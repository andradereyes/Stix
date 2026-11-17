package cl.andradereyes.stix.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.andradereyes.stix.MainViewModel

@Composable
fun StixApp(vm: MainViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    // Al volver a la app (por ejemplo desde WhatsApp) se revisa qué packs ya están agregados.
    LifecycleResumeEffect(Unit) {
        vm.refreshAdded()
        onPauseOrDispose { }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::openUri)
    }
    var pendingPack by remember { mutableStateOf<String?>(null) }
    val whatsapp = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val error = r.data?.getStringExtra("validation_error")
        if (r.resultCode == Activity.RESULT_CANCELED && error != null) {
            vm.showMessage("WhatsApp: $error")
        } else {
            pendingPack?.let(vm::markSent)
        }
        pendingPack = null
        vm.refreshAdded()
    }

    BackHandler(enabled = s.editor != null) { vm.closeEditor() }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = s.editor,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it != null },
                label = "screen",
            ) { editor ->
                if (editor != null) {
                    EditorScreen(
                        ed = editor,
                        onAnimated = vm::setAnimated,
                        onRange = vm::setRange,
                        onFrame = vm::previewAt,
                        onPreparePlay = vm::preparePlayback,
                        onClose = vm::closeEditor,
                        onSave = vm::save,
                    )
                } else {
                    HomeScreen(
                        packs = s.packs,
                        added = s.added,
                        fileOf = vm::stickerFile,
                        onLink = vm::openLink,
                        onPick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                        onAdd = { pack ->
                            val intent = vm.addIntent(pack)
                            if (intent == null) {
                                vm.showMessage("No encontré WhatsApp en este teléfono")
                            } else {
                                try {
                                    pendingPack = pack.id
                                    whatsapp.launch(intent)
                                } catch (e: ActivityNotFoundException) {
                                    vm.showMessage("Tu WhatsApp no acepta packs externos")
                                }
                            }
                        },
                        onEmptyClipboard = { vm.showMessage("No hay nada copiado") },
                        onDelete = vm::delete,
                    )
                }
            }

            s.busy?.let { BusyOverlay(it, onCancel = vm::cancelWork) }

            // Los avisos salen arriba para no tapar la barra de abajo.
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp),
            )
        }
    }
}
