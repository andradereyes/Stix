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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

    LaunchedEffect(s.message) {
        s.message?.let {
            vm.consumeMessage()
            snackbar.showSnackbar(it)
        }
    }

    // Al volver a la app (por ejemplo desde WhatsApp) se revisa qué packs ya están agregados.
    LifecycleResumeEffect(Unit) {
        vm.refreshAdded()
        onPauseOrDispose { }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::openUri)
    }
    val whatsapp = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_CANCELED) {
            r.data?.getStringExtra("validation_error")?.let { vm.showMessage("WhatsApp: $it") }
        }
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
                                    whatsapp.launch(intent)
                                } catch (e: ActivityNotFoundException) {
                                    vm.showMessage("Tu WhatsApp no acepta packs externos")
                                }
                            }
                        },
                        onDelete = vm::delete,
                    )
                }
            }

            s.busy?.let { BusyOverlay(it, onCancel = vm::cancelWork) }

            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            )
        }
    }
}
