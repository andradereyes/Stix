package cl.andradereyes.stix.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cl.andradereyes.stix.Busy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

/** Muestra un sticker WebP; si es animado, se mueve. */
@Composable
fun StickerImage(file: File, modifier: Modifier = Modifier) {
    var drawable by remember(file.path) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(file.path) {
        drawable = withContext(Dispatchers.IO) {
            runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) }.getOrNull()
        }
    }
    AndroidView(
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { view ->
            if (view.drawable !== drawable) {
                view.setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.start()
            }
        },
        modifier = modifier,
    )
}

@Composable
fun BusyOverlay(busy: Busy, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(32.dp), tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(28.dp).width(160.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when (busy) {
                    Busy.Loading -> CircularProgressIndicator(modifier = Modifier.size(56.dp), strokeWidth = 5.dp)
                    is Busy.Rendering -> Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { busy.progress },
                            modifier = Modifier.size(56.dp),
                            strokeWidth = 5.dp,
                        )
                        Text(
                            "${(busy.progress * 100).toInt()}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                FilledTonalIconButton(onClick = onCancel) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancelar")
                }
            }
        }
    }
}
