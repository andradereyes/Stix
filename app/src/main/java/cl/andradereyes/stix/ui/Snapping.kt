package cl.andradereyes.stix.ui

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import cl.andradereyes.stix.media.StickerRenderer
import cl.andradereyes.stix.media.Transform
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Resultado del imán: la posición ajustada y qué guías mostrar (en fracción del lienzo, 0..1). */
data class Snap(
    val t: Transform,
    val vGuides: List<Float> = emptyList(),
    val hGuides: List<Float> = emptyList(),
    val angleSnapped: Boolean = false,
    val scaleSnapped: Boolean = false,
) {
    val any get() = vGuides.isNotEmpty() || hGuides.isNotEmpty() || angleSnapped || scaleSnapped
    val key get() = listOf(vGuides, hGuides, angleSnapped, scaleSnapped)
}

object Snapping {
    private const val EDGE = 0.022f // ~2 % del lado
    private const val ANGLE = 4f // grados
    private const val SCALE = 0.035f // 3,5 %

    /**
     * Aplica un gesto (arrastre, pellizco y giro alrededor del punto entre los dedos).
     * pan y pivot vienen en fracción del lado del lienzo.
     */
    fun applyGesture(t: Transform, pan: Offset, zoom: Float, rotation: Float, pivot: Offset): Transform {
        val newScale = (t.scale * zoom).coerceIn(0.05f, 20f)
        val z = newScale / t.scale
        val center = Offset(0.5f + t.offsetX, 0.5f + t.offsetY)
        val v = center - pivot
        val rad = Math.toRadians(rotation.toDouble())
        val rx = (v.x * cos(rad) - v.y * sin(rad)).toFloat() * z
        val ry = (v.x * sin(rad) + v.y * cos(rad)).toFloat() * z
        val c = Offset(pivot.x + rx + pan.x, pivot.y + ry + pan.y)
        return t.copy(
            scale = newScale,
            rotation = t.rotation + rotation,
            offsetX = c.x - 0.5f,
            offsetY = c.y - 0.5f,
        )
    }

    /** Ángulo a 45°, tamaño a "cabe entera" / "llena el cuadrado", y bordes y centro al lienzo. */
    fun snap(raw: Transform, w: Int, h: Int): Snap {
        var t = raw
        val nearAngle = (t.rotation / 45f).roundToInt() * 45f
        val angleSnapped = abs(t.rotation - nearAngle) < ANGLE
        if (angleSnapped) t = t.copy(rotation = nearAngle)

        val fill = max(w, h).toFloat() / max(1, min(w, h))
        var scaleSnapped = false
        for (target in listOf(1f, fill)) {
            if (abs(t.scale / target - 1f) < SCALE) {
                // Se ajusta el tamaño manteniendo el centro en su lugar.
                t = t.copy(scale = target)
                scaleSnapped = true
                break
            }
        }

        val r = RectF(0f, 0f, w.toFloat(), h.toFloat())
        StickerRenderer.matrix(w, h, t, 1f).mapRect(r)

        val (dx, vGuides) = axis(listOf(r.left to 0f, r.right to 1f, r.centerX() to 0.5f))
        val (dy, hGuides) = axis(listOf(r.top to 0f, r.bottom to 1f, r.centerY() to 0.5f))
        t = t.copy(offsetX = t.offsetX + dx, offsetY = t.offsetY + dy)
        return Snap(t, vGuides, hGuides, angleSnapped, scaleSnapped)
    }

    /** Elige el ajuste más cercano en un eje y devuelve cuánto mover y dónde dibujar la guía. */
    private fun axis(pairs: List<Pair<Float, Float>>): Pair<Float, List<Float>> {
        val best = pairs.minByOrNull { abs(it.second - it.first) } ?: return 0f to emptyList()
        val d = best.second - best.first
        if (abs(d) > EDGE) return 0f to emptyList()
        // Puede haber más de una guía alineada a la vez (por ejemplo, ambos bordes).
        val guides = pairs.filter { abs((it.first + d) - it.second) < 0.002f }.map { it.second }
        return d to guides
    }
}
