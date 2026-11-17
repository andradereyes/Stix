package cl.andradereyes.stix.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.media.MediaMetadataRetriever
import cl.andradereyes.stix.UserError
import java.io.File
import kotlin.math.max

/** Algo de donde sacar fotogramas: foto, GIF o video. */
interface FrameSource : AutoCloseable {
    val width: Int
    val height: Int

    /** 0 si es una imagen fija. */
    val durationMs: Long

    /** El bitmap devuelto puede reutilizarse en la siguiente llamada. */
    fun frameAt(ms: Long): Bitmap

    override fun close() {}
}

class StillSource(private val bitmap: Bitmap) : FrameSource {
    override val width = bitmap.width
    override val height = bitmap.height
    override val durationMs = 0L
    override fun frameAt(ms: Long) = bitmap
}

class VideoSource(file: File) : FrameSource {
    private val mmr = MediaMetadataRetriever().apply { setDataSource(file.absolutePath) }
    override val durationMs: Long =
        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    private val first: Bitmap = grab(0)
        ?: grab(100)
        ?: runCatching { mmr.getFrameAtIndex(0) }.getOrNull()?.let(::shrink)
        ?: throw UserError("No pude leer ese video (${describe(file)})")
    override val width = first.width
    override val height = first.height

    /** Prueba varios métodos: algunos teléfonos fallan con uno y no con otro. */
    private fun grab(ms: Long): Bitmap? {
        val us = ms * 1000
        return runCatching {
            mmr.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST, MAX_SIDE, MAX_SIDE)
        }.getOrNull()
            ?: runCatching { mmr.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST) }.getOrNull()?.let(::shrink)
            ?: runCatching { mmr.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull()?.let(::shrink)
    }

    private fun shrink(b: Bitmap): Bitmap {
        val m = max(b.width, b.height)
        if (m <= MAX_SIDE) return b
        val f = MAX_SIDE.toFloat() / m
        return Bitmap.createScaledBitmap(b, max(1, (b.width * f).toInt()), max(1, (b.height * f).toInt()), true)
    }

    private fun describe(file: File): String {
        val mime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "tipo desconocido"
        val hasVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) ?: "?"
        return "$mime, video=$hasVideo, ${file.length() / 1024} KB"
    }

    override fun frameAt(ms: Long): Bitmap =
        grab(ms.coerceIn(0L, max(0L, durationMs - 1))) ?: first

    override fun close() {
        runCatching { mmr.release() }
    }

    private companion object {
        const val MAX_SIDE = 720
    }
}

@Suppress("DEPRECATION")
class GifSource(bytes: ByteArray) : FrameSource {
    private val movie: Movie = Movie.decodeByteArray(bytes, 0, bytes.size) ?: throw UserError("No pude leer ese GIF")
    override val width = max(1, movie.width())
    override val height = max(1, movie.height())
    override val durationMs = movie.duration().toLong()
    private val buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(buffer)

    override fun frameAt(ms: Long): Bitmap {
        buffer.eraseColor(0)
        movie.setTime(if (durationMs > 0) (ms % durationMs).toInt() else 0)
        movie.draw(canvas, 0f, 0f)
        return buffer
    }
}

object Sources {
    private val IMAGE_BRANDS = setOf("heic", "heix", "hevc", "mif1", "msf1", "avif", "avis")

    fun open(file: File): FrameSource {
        val head = ByteArray(16)
        val read = file.inputStream().use { it.read(head) }
        if (read < 12) throw UserError("El archivo está vacío o dañado")
        fun ascii(offset: Int, len: Int) = String(head, offset, len, Charsets.US_ASCII)

        val isGif = ascii(0, 4) == "GIF8"
        val isMp4Family = ascii(4, 4) == "ftyp"
        val isImageBrand = isMp4Family && ascii(8, 4).lowercase() in IMAGE_BRANDS
        val isWebm = head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte()

        return when {
            isGif -> {
                val gif = GifSource(file.readBytes())
                if (gif.durationMs > 0) gif else StillSource(gif.frameAt(0).copy(Bitmap.Config.ARGB_8888, false))
            }
            (isMp4Family && !isImageBrand) || isWebm -> VideoSource(file)
            else -> runCatching { StillSource(decodeStill(file)) }
                .getOrElse { runCatching { VideoSource(file) }.getOrElse { throw UserError("No reconozco ese archivo") } }
        }
    }

    private fun decodeStill(file: File): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val m = max(w, h)
            if (m > 1600) {
                val f = 1600f / m
                d.setTargetSize(max(1, (w * f).toInt()), max(1, (h * f).toInt()))
            }
        }
}
