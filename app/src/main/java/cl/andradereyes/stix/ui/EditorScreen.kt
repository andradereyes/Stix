package cl.andradereyes.stix.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.andradereyes.stix.EditorState
import cl.andradereyes.stix.media.LoopMode
import cl.andradereyes.stix.media.StickerRenderer
import cl.andradereyes.stix.media.Transform
import kotlinx.coroutines.delay
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
    onPreparePlay: () -> Unit,
    onLoop: (LoopMode) -> Unit,
    onClose: () -> Unit,
    onSave: (Transform) -> Unit,
) {
    val w = ed.source.width
    val h = ed.source.height
    val haptic = LocalHapticFeedback.current

    // Posición que se ve (con imán) y la "cruda" que siguen los dedos.
    var shown by remember(ed.source) { mutableStateOf(Transform()) }
    var raw by remember(ed.source) { mutableStateOf(Transform()) }
    var snap by remember { mutableStateOf<Snap?>(null) }

    // Historial para deshacer y rehacer.
    val history = remember(ed.source) { mutableStateListOf(Transform()) }
    var index by remember(ed.source) { mutableIntStateOf(0) }
    fun commit(t: Transform) {
        if (history[index] == t) return
        while (history.size > index + 1) history.removeAt(history.size - 1)
        history.add(t)
        index = history.size - 1
        shown = t
        raw = t
    }
    fun jump(to: Int) {
        index = to
        shown = history[to]
        raw = history[to]
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // Reproducción dentro del editor.
    var playing by remember(ed.source) { mutableStateOf(false) }
    var frame by remember(ed.source) { mutableIntStateOf(0) }
    val frames = ed.playFrames
    LaunchedEffect(playing, frames) {
        frame = 0
        if (playing && frames != null) {
            while (true) {
                delay(ed.playFrameMs)
                frame = (frame + 1) % frames.size
            }
        }
    }
    LaunchedEffect(playing, frames == null) {
        if (playing && frames == null) onPreparePlay()
    }
    val animatedMode = ed.animated && ed.canAnimate
    val playheadMs: Long? = if (playing && frames != null) ed.playTimes.getOrNull(frame % frames.size) else null
    if (!animatedMode && playing) playing = false
    val bitmap: Bitmap = if (playing && frames != null) frames[frame % frames.size] else ed.preview

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // Arriba: cerrar, tipo de sticker y listo.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Cerrar") }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (ed.canAnimate) {
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = ed.animated,
                                onClick = { onAnimated(true) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                icon = {},
                            ) { ModeLabel(Icons.Rounded.Movie, "Animado") }
                            SegmentedButton(
                                selected = !ed.animated,
                                onClick = { onAnimated(false) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                icon = {},
                            ) { ModeLabel(Icons.Rounded.Image, "Fijo") }
                        }
                    } else {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                ModeLabel(Icons.Rounded.Image, "Sticker fijo")
                            }
                        }
                    }
                }
                Button(onClick = { onSave(shown) }) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Listo")
                }
            }

            // Lienzo: un dedo mueve, dos dedos hacen zoom y giran, doble toque alterna ajustar/llenar.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val c = MaterialTheme.colorScheme
                val guideColor = c.primary
                Box(
                    Modifier
                        .padding(20.dp)
                        .widthIn(max = 520.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .checkerboard(c.surfaceContainerHigh, c.surfaceContainerHighest)
                        .border(1.5.dp, c.outlineVariant, RoundedCornerShape(28.dp))
                ) {
                    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(ed.source) {
                                detectTapGestures(onDoubleTap = {
                                    val fill = max(w, h).toFloat() / max(1, min(w, h))
                                    val toFill = shown.scale < (1f + fill) / 2f
                                    commit(shown.copy(scale = if (toFill) fill else 1f, offsetX = 0f, offsetY = 0f))
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                })
                            }
                            .pointerInput(ed.source) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var moved = false
                                    var lastKey: Any? = null
                                    do {
                                        val event = awaitPointerEvent()
                                        val zoom = event.calculateZoom()
                                        val rotation = event.calculateRotation()
                                        val pan = event.calculatePan()
                                        if (zoom != 1f || rotation != 0f || pan != Offset.Zero) {
                                            val side = size.width.toFloat()
                                            val centroid = event.calculateCentroid(useCurrent = false)
                                            val pivot = if (centroid == Offset.Unspecified) Offset(0.5f, 0.5f)
                                            else Offset(centroid.x / side, centroid.y / side)
                                            raw = Snapping.applyGesture(
                                                raw,
                                                Offset(pan.x / side, pan.y / side),
                                                zoom,
                                                rotation,
                                                pivot,
                                            )
                                            val s = Snapping.snap(raw, w, h)
                                            // Vibra un poquito cada vez que algo "engancha".
                                            if (s.any && s.key != lastKey) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            lastKey = if (s.any) s.key else null
                                            shown = s.t
                                            snap = s
                                            moved = true
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                    snap = null
                                    if (moved) commit(shown)
                                }
                            }
                    ) {
                        val m = StickerRenderer.matrix(bitmap.width, bitmap.height, shown, size.width)
                        drawIntoCanvas { it.nativeCanvas.drawBitmap(bitmap, m, paint) }

                        // Guías del imán.
                        snap?.let { s ->
                            val stroke = 1.5.dp.toPx()
                            s.vGuides.forEach { x ->
                                val px = (x * size.width).coerceIn(stroke, size.width - stroke)
                                drawLine(guideColor, Offset(px, 0f), Offset(px, size.height), stroke)
                            }
                            s.hGuides.forEach { y ->
                                val py = (y * size.height).coerceIn(stroke, size.height - stroke)
                                drawLine(guideColor, Offset(0f, py), Offset(size.width, py), stroke)
                            }
                        }
                    }

                    // Ángulo mientras giras (como en Canva).
                    val angle = (((shown.rotation % 360f) + 360f) % 360f).roundToInt() % 360
                    if (snap != null && angle != 0) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                        ) {
                            Text(
                                "$angle°",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // Play / pausa para ver cómo queda la animación.
                    if (animatedMode) {
                        val loadingAlpha by rememberInfiniteTransition(label = "load").animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                            label = "a",
                        )
                        FilledIconButton(
                            onClick = { playing = !playing },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(48.dp),
                        ) {
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Pausa" else "Ver animación",
                                modifier = Modifier.graphicsLayer {
                                    alpha = if (playing && frames == null) loadingAlpha else 1f
                                },
                            )
                        }
                    }
                }
            }

            // Herramientas.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Tool(Icons.Rounded.FitScreen, "Ajustar") { commit(Transform(rotation = shown.rotation, flip = shown.flip)) }
                Tool(Icons.Rounded.Crop, "Llenar") {
                    commit(shown.copy(scale = max(w, h).toFloat() / max(1, min(w, h)), offsetX = 0f, offsetY = 0f))
                }
                Tool(Icons.AutoMirrored.Rounded.RotateRight, "Girar") {
                    commit(shown.copy(rotation = (((shown.rotation / 90f).roundToInt() + 1) * 90 % 360).toFloat()))
                }
                Tool(Icons.Rounded.Flip, "Voltear") { commit(shown.copy(flip = !shown.flip)) }
                Tool(Icons.AutoMirrored.Rounded.Undo, "Deshacer", enabled = index > 0) { jump(index - 1) }
                Tool(Icons.AutoMirrored.Rounded.Redo, "Rehacer", enabled = index < history.size - 1) { jump(index + 1) }
            }

            Spacer(Modifier.height(14.dp))

            // Tiempo: modo de bucle + tira de fotogramas + rango (animado) o fotograma elegido (fijo).
            if (ed.canAnimate) {
                val totalS = ed.source.durationMs / 1000f
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    if (ed.animated) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        ) {
                            LoopChip(Icons.AutoMirrored.Rounded.ArrowForward, "Normal", ed.loop == LoopMode.NORMAL) {
                                onLoop(LoopMode.NORMAL)
                            }
                            LoopChip(Icons.Rounded.SwapHoriz, "Bumerán", ed.loop == LoopMode.BOOMERANG) {
                                onLoop(LoopMode.BOOMERANG)
                            }
                            LoopChip(Icons.Rounded.Gradient, "Fundido", ed.loop == LoopMode.CROSSFADE) {
                                onLoop(LoopMode.CROSSFADE)
                            }
                        }
                        val maxLen = StickerRenderer.maxClipMs(ed.loop) / 1000f
                        var range by remember(ed.source, ed.loop) {
                            mutableStateOf(ed.startMs / 1000f..ed.endMs / 1000f)
                        }
                        Filmstrip(
                            ed.filmstrip,
                            start = range.start / totalS,
                            end = range.endInclusive / totalS,
                            marker = playheadMs?.let { it / 1000f / totalS },
                        )
                        RangeSlider(
                            value = range,
                            onValueChange = { r ->
                                range = if (r.endInclusive - r.start <= maxLen) r
                                else if (r.start != range.start) r.start..(r.start + maxLen)
                                else (r.endInclusive - maxLen)..r.endInclusive
                                playing = false
                            },
                            onValueChangeFinished = {
                                onRange((range.start * 1000).toLong(), (range.endInclusive * 1000).toLong())
                            },
                            valueRange = 0f..totalS,
                        )
                        val clip = range.endInclusive - range.start
                        val finalLen = if (ed.loop == LoopMode.BOOMERANG) clip * 2 else clip
                        TimeLabels(
                            left = playheadMs?.let { "▶ ${fmt(it / 1000f)}" }
                                ?: "${fmt(range.start)} – ${fmt(range.endInclusive)}",
                            right = "${fmt(finalLen)} de ${fmt(StickerRenderer.MAX_ANIM_MS / 1000f)}",
                        )
                    } else {
                        var pos by remember(ed.source) { mutableFloatStateOf(ed.previewMs / 1000f) }
                        Filmstrip(ed.filmstrip, marker = pos / totalS)
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
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModeLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LoopChip(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = CircleShape,
    )
}

@Composable
private fun Tool(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = label)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Miniaturas del video; oscurece lo que queda fuera del tramo elegido o marca el fotograma. */
@Composable
private fun Filmstrip(thumbs: List<Bitmap>, start: Float = 0f, end: Float = 1f, marker: Float? = null) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .drawWithContent {
                drawContent()
                val dim = Color.Black.copy(alpha = 0.55f)
                drawRect(dim, Offset.Zero, Size(size.width * start, size.height))
                drawRect(dim, Offset(size.width * end, 0f), Size(size.width * (1f - end), size.height))
                if (marker != null) {
                    val x = (size.width * marker).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
                    drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 5.dp.toPx())
                    drawLine(accent, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx())
                }
            }
    ) {
        if (thumbs.isEmpty()) {
            Box(Modifier.fillMaxSize().shimmer())
        } else {
            Row(Modifier.fillMaxSize()) {
                thumbs.forEach { b ->
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeLabels(left: String, right: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Text(left, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(right, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun fmt(seconds: Float) = String.format(Locale.ROOT, "%.1f s", seconds)
