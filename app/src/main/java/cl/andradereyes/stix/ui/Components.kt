package cl.andradereyes.stix.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cl.andradereyes.stix.Busy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** Fondo de cuadritos para que se note qué parte del sticker es transparente. */
fun Modifier.checkerboard(light: Color, dark: Color, cell: Dp = 12.dp): Modifier = drawBehind {
    val c = cell.toPx()
    drawRect(light)
    val cols = (size.width / c).toInt() + 1
    val rows = (size.height / c).toInt() + 1
    for (y in 0 until rows) for (x in 0 until cols) {
        if ((x + y) % 2 == 1) drawRect(dark, Offset(x * c, y * c), Size(c, c))
    }
}

/** Brillo que recorre un bloque mientras carga (skeleton). */
fun Modifier.shimmer(): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceBright
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "x",
    )
    drawBehind {
        val w = size.width
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(w * x - w, 0f),
                end = Offset(w * x, size.height),
            )
        )
    }
}

/** Fondo con manchas de color suaves que se mueven lento, con los colores del tema. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val blobs = listOf(c.primaryContainer, c.tertiaryContainer, c.secondaryContainer)
    val phase by rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(modifier.fillMaxSize().background(c.surface)) {
        val w = size.width
        val h = size.height
        val anchors = listOf(Offset(0.15f, 0.08f), Offset(0.9f, 0.3f), Offset(0.3f, 0.75f))
        anchors.forEachIndexed { i, a ->
            val p = phase + i * 2.1f
            val center = Offset(w * (a.x + 0.08f * cos(p)), h * (a.y + 0.05f * sin(p)))
            val r = w * 0.75f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blobs[i].copy(alpha = 0.55f), Color.Transparent),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }
    }
}

/** Tres "stickers" que saltan y giran: el indicador de carga de la app. */
@Composable
fun StickerLoader(modifier: Modifier = Modifier) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
    )
    val transition = rememberInfiniteTransition(label = "loader")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEachIndexed { i, color ->
            val t by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(620, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 140),
                ),
                label = "b$i",
            )
            Box(
                Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        translationY = -14.dp.toPx() * t
                        rotationZ = -12f + 24f * t
                        scaleX = 0.9f + 0.15f * t
                        scaleY = 0.9f + 0.15f * t
                    }
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
        }
    }
}

/** Muestra un sticker WebP; si es animado, se mueve. Mientras carga, muestra un skeleton. */
@Composable
fun StickerImage(file: File, modifier: Modifier = Modifier) {
    var drawable by remember(file.path) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(file.path) {
        drawable = withContext(Dispatchers.IO) {
            runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) }.getOrNull()
        }
    }
    val alpha by animateFloatAsState(if (drawable != null) 1f else 0f, tween(250), label = "fade")
    Box(modifier) {
        if (drawable == null) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).shimmer())
        AndroidView(
            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
            update = { view ->
                if (view.drawable !== drawable) {
                    view.setImageDrawable(drawable)
                    (drawable as? AnimatedImageDrawable)?.start()
                }
            },
            modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
        )
    }
}

/** Tarjeta de "trabajando": dice en qué etapa va y, si se sabe, cuánto falta. */
@Composable
fun BusyOverlay(busy: Busy, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 26.dp).width(240.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StickerLoader(Modifier.height(44.dp).padding(top = 14.dp))
                AnimatedContent(
                    targetState = busy.label,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label = "label",
                ) { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    )
                }
                val p = busy.progress
                val shown by animateFloatAsState(p ?: 0f, tween(180), label = "p")
                Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                    if (p != null) {
                        LinearProgressIndicator(
                            progress = { shown },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                }
                TextButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
    }
}
