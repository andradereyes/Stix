package cl.andradereyes.stix.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.andradereyes.stix.data.Pack
import cl.andradereyes.stix.data.StickerStore
import java.io.File

@Composable
fun HomeScreen(
    packs: List<Pack>,
    added: Set<String>,
    fileOf: (Pack, String) -> File,
    onLink: (String) -> Unit,
    onPick: () -> Unit,
    onAdd: (Pack) -> Unit,
    onDelete: (Pack, String) -> Unit,
) {
    val bars = WindowInsets.systemBars.asPaddingValues()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 84.dp),
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = bars.calculateTopPadding() + 24.dp,
            bottom = bars.calculateBottomPadding() + 32.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "top", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    "Stix",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(20.dp))
                InputBar(onLink = onLink, onPick = onPick)
            }
        }

        if (packs.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
        }

        packs.asReversed().forEach { pack ->
            item(key = pack.id, span = { GridItemSpan(maxLineSpan) }) {
                PackHeader(pack = pack, added = pack.id in added, onAdd = { onAdd(pack) })
            }
            items(pack.stickers.asReversed(), key = { "${pack.id}/$it" }) { name ->
                StickerCell(file = fileOf(pack, name), onDelete = { onDelete(pack, name) })
            }
        }
    }
}

@Composable
private fun InputBar(onLink: (String) -> Unit, onPick: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    fun submit() {
        if (text.isNotBlank()) {
            onLink(text)
            text = ""
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Pega un link") },
            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
            trailingIcon = {
                if (text.isBlank()) {
                    // Un toque: pega y abre de inmediato.
                    IconButton(onClick = { clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(onLink) }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Pegar")
                    }
                } else {
                    IconButton(onClick = ::submit) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Abrir")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        Spacer(Modifier.width(10.dp))
        FilledTonalIconButton(onClick = onPick, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Elegir de la galería")
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        )
        Text(
            "Pega un link o elige una foto",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            "También puedes compartir directo desde X, Instagram o la galería",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun PackHeader(pack: Pack, added: Boolean, onAdd: () -> Unit) {
    val n = pack.stickers.size
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (pack.animated) Icons.Rounded.Animation else Icons.Rounded.Image,
            contentDescription = if (pack.animated) "Animados" else "Fijos",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(pack.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "$n/${StickerStore.MAX_PER_PACK}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            // WhatsApp exige mínimo 3 stickers por pack: los puntos muestran cuánto falta.
            n < StickerStore.MIN_PER_PACK -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            added -> FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("En WhatsApp")
            }
            else -> Button(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("WhatsApp")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerCell(file: File, onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = {}, onLongClick = { confirm = true })
            .padding(6.dp),
    ) {
        StickerImage(file, Modifier.fillMaxSize())
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = { Text("¿Borrar sticker?") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDelete()
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}
