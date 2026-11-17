package cl.andradereyes.stix.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import cl.andradereyes.stix.UserError
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Posición de la imagen dentro del sticker.
 * scale = 1 significa "cabe entera"; offset es una fracción del lado del sticker.
 */
data class Transform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f,
    val flip: Boolean = false,
)

object StickerRenderer {
    const val SIZE = 512
    private const val MAX_STATIC_BYTES = 100 * 1024
    private const val MAX_ANIM_BYTES = 500 * 1024
    const val MAX_ANIM_MS = 8_000L
    private const val MIN_FRAME_MS = 20

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** La misma matriz se usa para la vista previa (out = tamaño en pantalla) y para el archivo (out = 512). */
    fun matrix(srcW: Int, srcH: Int, t: Transform, out: Float): Matrix {
        val s = out / max(srcW, srcH) * t.scale
        return Matrix().apply {
            postTranslate(-srcW / 2f, -srcH / 2f)
            postScale(if (t.flip) -s else s, s)
            postRotate(t.rotation)
            postTranslate(out / 2f + t.offsetX * out, out / 2f + t.offsetY * out)
        }
    }

    fun render(src: Bitmap, t: Transform, into: Bitmap? = null): Bitmap {
        val out = into ?: Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        out.eraseColor(0)
        Canvas(out).drawBitmap(src, matrix(src.width, src.height, t, SIZE.toFloat()), paint)
        return out
    }

    private fun compress(bmp: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, it) }.toByteArray()

    fun encodeStatic(bmp: Bitmap): ByteArray {
        var last = ByteArray(0)
        for (q in listOf(95, 85, 75, 60, 45, 30, 15)) {
            last = compress(bmp, q)
            if (last.size <= MAX_STATIC_BYTES) return last
        }
        return last
    }

    /**
     * WhatsApp pide WebP animado de 512x512, máximo 500 KB y 10 s.
     * Baja calidad y fps hasta que quepa. Si un intento se pasa por mucho, salta varios niveles.
     * onProgress recibe (número de intento, avance 0..1).
     */
    suspend fun encodeAnimated(
        source: FrameSource,
        t: Transform,
        startMs: Long,
        endMs: Long,
        onProgress: (Int, Float) -> Unit,
    ): ByteArray {
        val duration = (endMs - startMs).coerceIn(200L, MAX_ANIM_MS)
        val canvas = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val plans = listOf(15 to 75, 12 to 60, 12 to 45, 10 to 35, 10 to 25, 8 to 18, 6 to 12)

        var pi = 0
        var attempt = 0
        while (pi < plans.size) {
            val (fps, quality) = plans[pi]
            val n = max(1, (duration * fps / 1000.0).roundToInt())
            val frameMs = max(MIN_FRAME_MS, (duration / n).toInt())
            val frames = ArrayList<ByteArray>(n)
            var total = 0L
            var projected = 0.0
            for (i in 0 until n) {
                currentCoroutineContext().ensureActive()
                render(source.frameAt(startMs + i.toLong() * frameMs), t, canvas)
                val bytes = compress(canvas, quality)
                frames += bytes
                total += bytes.size
                onProgress(attempt, (i + 1f) / n)
                if (total > MAX_ANIM_BYTES) {
                    projected = total.toDouble() * n / (i + 1)
                    break
                }
            }
            attempt++
            if (projected > 0) {
                pi += when {
                    projected > 3.0 * MAX_ANIM_BYTES -> 3
                    projected > 1.7 * MAX_ANIM_BYTES -> 2
                    else -> 1
                }
                continue
            }
            val out = AnimatedWebp.assemble(frames, SIZE, SIZE, frameMs)
            if (out.size <= MAX_ANIM_BYTES) return out
            pi++
        }
        throw UserError("Quedó muy pesado. Prueba con un clip más corto.")
    }

    /** Copia reducida de un fotograma (para miniaturas y la vista previa animada). */
    fun thumb(b: Bitmap, maxSide: Int): Bitmap {
        val m = max(b.width, b.height)
        if (m <= maxSide) return b.copy(Bitmap.Config.ARGB_8888, false)
        val f = maxSide.toFloat() / m
        return Bitmap.createScaledBitmap(b, max(1, (b.width * f).toInt()), max(1, (b.height * f).toInt()), true)
    }
}
