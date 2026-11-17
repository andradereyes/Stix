package cl.andradereyes.stix.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.andradereyes.stix.EditorState
import cl.andradereyes.stix.media.StickerRenderer
import cl.andradereyes.stix.media.Transform
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    ed: EditorState,
    onAnimated: (Boolean) -> Unit,
    onRange: (Long, Long) -> Unit,
    onFrame: (Long) -> Unit,
    onClose: () -> Unit,
    onSave: (Transform) -> Unit,
) {
    var t by remember(ed.source) { mutableStateOf(Transform()) }
    val w = ed.source.width
    val h = ed.source.height

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // Barra superior: cerrar y listo.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Cerrar") }
                Spacer(Modifier.weight(1f))
                if (ed.canAnimate) {
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = ed.animated,
                            onClick = { onAnimated(true) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = {},
                        ) { Icon(Icons.Rounded.Movie, contentDescription = "Animado") }
                        SegmentedButton(
                            selected = !ed.animated,
                            onClick = { onAnimated(false) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = {},
                        ) { Icon(Icons.Rounded.Image, contentDescription = "Fijo") }
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(t) }) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Listo")
                }
            }

            // Lienzo: arrastra para mover, pellizca para zoom, gira con dos dedos.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val light = MaterialTheme.colorScheme.surfaceContainerHigh
                val dark = MaterialTheme.colorScheme.surfaceContainerHighest
                Box(
                    Modifier
                        .padding(24.dp)
                        .widthIn(max = 520.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .checkerboard(light, dark)
                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
                ) {
                    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(ed.source) {
                                detectTransformGestures { _, pan, zoom, rotation ->
                                    val side = size.width.toFloat()
                                    t = t.copy(
                                        scale = (t.scale * zoom).coerceIn(0.1f, 12f),
                                        rotation = t.rotation + rotation,
                                        offsetX = t.offsetX + pan.x / side,
                                        offsetY = t.offsetY + pan.y / side,
                                    )
                                }
                            }
                    ) {
                        val m = StickerRenderer.matrix(ed.preview.width, ed.preview.height, t, size.width)
                        drawIntoCanvas { it.nativeCanvas.drawBitmap(ed.preview, m, paint) }
                    }
                }
            }

            // Herramientas.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                FilledTonalIconButton(onClick = { t = t.copy(scale = 1f, offsetX = 0f, offsetY = 0f) }) {
                    Icon(Icons.Rounded.FitScreen, contentDescription = "Ajustar")
                }
                FilledTonalIconButton(onClick = {
                    t = t.copy(scale = max(w, h).toFloat() / max(1, min(w, h)), offsetX = 0f, offsetY = 0f)
                }) {
                    Icon(Icons.Rounded.Crop, contentDescription = "Llenar")
                }
                FilledTonalIconButton(onClick = {
                    t = t.copy(rotation = (((t.rotation / 90f).roundToInt() + 1) * 90 % 360).toFloat())
                }) {
                    @Suppress("DEPRECATION")
                    Icon(Icons.Rounded.RotateRight, contentDescription = "Girar")
                }
                FilledTonalIconButton(onClick = { t = t.copy(flip = !t.flip) }) {
                    Icon(Icons.Rounded.Flip, contentDescription = "Voltear")
                }
                FilledTonalIconButton(onClick = { t = Transform() }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Reiniciar")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Tiempo: rango del clip (animado) o fotograma elegido (fijo).
            if (ed.canAnimate) {
                val totalS = ed.source.durationMs / 1000f
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                    if (ed.animated) {
                        var range by remember(ed.source) {
                            mutableStateOf(ed.startMs / 1000f..ed.endMs / 1000f)
                        }
                        RangeSlider(
                            value = range,
                            onValueChange = { r ->
                                val maxLen = StickerRenderer.MAX_ANIM_MS / 1000f
                                range = if (r.endInclusive - r.start <= maxLen) r
                                else if (r.start != range.start) r.start..(r.start + maxLen)
                                else (r.endInclusive - maxLen)..r.endInclusive
                            },
                            onValueChangeFinished = {
                                onRange((range.start * 1000).toLong(), (range.endInclusive * 1000).toLong())
                            },
                            valueRange = 0f..totalS,
                        )
                        TimeLabels(
                            left = "${fmt(range.start)} – ${fmt(range.endInclusive)}",
                            right = fmt(range.endInclusive - range.start),
                        )
                    } else {
                        var pos by remember(ed.source) { mutableFloatStateOf(ed.previewMs / 1000f) }
                        Slider(
                            value = pos,
                            onValueChange = { pos = it },
                            onValueChangeFinished = { onFrame((pos * 1000).toLong()) },
                            valueRange = 0f..totalS,
                        )
                        TimeLabels(left = fmt(pos), right = fmt(totalS))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TimeLabels(left: String, right: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(left, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(right, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun fmt(seconds: Float) = String.format(Locale.ROOT, "%.1f s", seconds)
