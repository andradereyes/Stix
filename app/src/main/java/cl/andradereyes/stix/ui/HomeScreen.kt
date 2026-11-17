package cl.andradereyes.stix.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.andradereyes.stix.data.Pack
import cl.andradereyes.stix.data.StickerStore
import java.io.File

private const val COLUMNS = 4

@Composable
fun HomeScreen(
    packs: List<Pack>,
    added: Set<String>,
    fileOf: (Pack, String) -> File,
    onLink: (String) -> Unit,
    onPick: () -> Unit,
    onEmptyClipboard: () -> Unit,
    onAdd: (Pack) -> Unit,
    onDelete: (Pack, String) -> Unit,
) {
    var preview by remember { mutableStateOf<Pair<Pack, String>?>(null) }
    val bars = WindowInsets.systemBars.asPaddingValues()

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = bars.calculateTopPadding() + 16.dp,
                bottom = bars.calculateBottomPadding() + 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (packs.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }
            items(packs.asReversed(), key = { it.id }) { pack ->
                PackCard(
                    pack = pack,
                    added = pack.id in added,
                    fileOf = fileOf,
                    onAdd = { onAdd(pack) },
                    onOpen = { name -> preview = pack to name },
                )
            }
        }

        InputDock(
            onLink = onLink,
            onPick = onPick,
            onEmptyClipboard = onEmptyClipboard,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }

    preview?.let { (pack, name) ->
        StickerSheet(
            file = fileOf(pack, name),
            onDismiss = { preview = null },
            onDelete = {
                preview = null
                onDelete(pack, name)
            },
        )
    }
}

/** Barra flotante abajo, al alcance del pulgar: link + pegar + galería. */
@Composable
private fun InputDock(
    onLink: (String) -> Unit,
    onPick: () -> Unit,
    onEmptyClipboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    fun submit() {
        if (text.isNotBlank()) {
            onLink(text)
            text = ""
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        shadowElevation = 16.dp,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text("Pega un link", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (text.isBlank()) {
                    // Un toque: pega y abre de inmediato.
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val clip = clipboard.getText()?.text
                        if (clip.isNullOrBlank()) onEmptyClipboard() else onLink(clip)
                    }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Pegar y abrir")
                    }
                } else {
                    IconButton(
                        onClick = { submit() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Abrir")
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onPick, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Elegir de la galería")
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Tres stickers de mentira, inclinados.
        Box(Modifier.size(width = 220.dp, height = 140.dp), contentAlignment = Alignment.Center) {
            val c = MaterialTheme.colorScheme
            listOf(
                Triple(-14f, -56, c.tertiaryContainer),
                Triple(10f, 56, c.secondaryContainer),
                Triple(-3f, 0, c.primaryContainer),
            ).forEach { (angle, dx, color) ->
                Box(
                    Modifier
                        .offset(x = dx.dp)
                        .size(96.dp)
                        .rotate(angle)
                        .clip(RoundedCornerShape(26.dp))
                        .background(color)
                )
            }
            Text("✨", style = MaterialTheme.typography.displaySmall)
        }
        Spacer(Modifier.height(28.dp))
        Text("Tu primer sticker", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pega un link abajo, elige una foto o comparte directo desde X o Instagram",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
    }
}

@Composable
private fun PackCard(
    pack: Pack,
    added: Boolean,
    fileOf: (Pack, String) -> File,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(14.dp)) {
            PackHeader(pack, added, onAdd)
            Spacer(Modifier.height(12.dp))
            pack.stickers.asReversed().chunked(COLUMNS).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { name ->
                        StickerCell(file = fileOf(pack, name), onClick = { onOpen(name) }, modifier = Modifier.weight(1f))
                    }
                    repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PackHeader(pack: Pack, added: Boolean, onAdd: () -> Unit) {
    val n = pack.stickers.size
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (pack.animated) Icons.Rounded.Movie else Icons.Rounded.Image,
                contentDescription = if (pack.animated) "Animados" else "Fijos",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(pack.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "$n de ${StickerStore.MAX_PER_PACK}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            // WhatsApp exige mínimo 3 stickers por pack: los puntos muestran cuánto falta.
            n < StickerStore.MIN_PER_PACK -> Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                repeat(StickerStore.MIN_PER_PACK) { i ->
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (i < n) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
            added && pack.needsUpdate -> {
                // Pulso suave para que se note que hay cambios por mandar.
                val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                    initialValue = 1f,
                    targetValue = 1.06f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "s",
                )
                Button(
                    onClick = onAdd,
                    modifier = Modifier.graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                ) {
                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Actualizar")
                }
            }
            added -> FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("En WhatsApp")
            }
            else -> Button(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Agregar")
            }
        }
    }
}

@Composable
private fun StickerCell(file: File, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        StickerImage(file, Modifier.fillMaxSize())
    }
}

/** Vista grande de un sticker, con la opción de borrarlo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerSheet(file: File, onDismiss: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val c = MaterialTheme.colorScheme
            Box(
                Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .checkerboard(c.surfaceContainerHigh, c.surfaceContainerHighest)
                    .padding(12.dp)
            ) {
                StickerImage(file, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (confirm) onDelete() else confirm = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (confirm) c.error else c.errorContainer,
                    contentColor = if (confirm) c.onError else c.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (confirm) "Toca otra vez para borrar" else "Borrar")
            }
        }
    }
}
