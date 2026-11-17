package cl.andradereyes.stix

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.andradereyes.stix.data.Pack
import cl.andradereyes.stix.data.StickerStore
import cl.andradereyes.stix.data.WhatsApp
import cl.andradereyes.stix.media.Downloader
import cl.andradereyes.stix.media.FrameSource
import cl.andradereyes.stix.media.Sources
import cl.andradereyes.stix.media.StickerRenderer
import cl.andradereyes.stix.media.Transform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.min

sealed interface Busy {
    data object Loading : Busy
    data class Rendering(val progress: Float) : Busy
}

data class EditorState(
    val source: FrameSource,
    val preview: Bitmap,
    val previewMs: Long = 0,
    val animated: Boolean,
    val startMs: Long = 0,
    val endMs: Long,
) {
    val canAnimate get() = source.durationMs > 0
}

data class UiState(
    val packs: List<Pack> = emptyList(),
    val added: Set<String> = emptySet(),
    val busy: Busy? = null,
    val editor: EditorState? = null,
)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    val store = StickerStore(app)
    private val _state = MutableStateFlow(UiState(packs = store.packs()))
    val state = _state.asStateFlow()

    // Los mensajes van por un canal: así no se pierden aunque la pantalla se redibuje.
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val sourceLock = Mutex()
    private var previewJob: Job? = null
    private var workJob: Job? = null

    // ---------- Entrada ----------

    fun openLink(text: String) {
        val url = Downloader.extractUrl(text)
        if (url == null) {
            showMessage("Eso no parece un link")
            return
        }
        load { Downloader.fetch(url, app.cacheDir) { Sources.open(it) } }
    }

    fun openUri(uri: Uri) = load {
        withContext(Dispatchers.IO) {
            val file = File(app.cacheDir, "in_${System.currentTimeMillis()}")
            val input = app.contentResolver.openInputStream(uri) ?: throw UserError("No pude abrir ese archivo")
            input.use { i -> file.outputStream().use { i.copyTo(it) } }
            Sources.open(file)
        }
    }

    private fun load(block: suspend () -> FrameSource) {
        if (_state.value.busy != null) return
        closeEditor()
        _state.update { it.copy(busy = Busy.Loading) }
        workJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    app.cacheDir.listFiles { f -> f.name.startsWith("in_") }?.forEach { it.delete() }
                }
                val source = block()
                val preview = withContext(Dispatchers.Default) {
                    source.frameAt(0).copy(Bitmap.Config.ARGB_8888, false)
                }
                _state.update {
                    it.copy(
                        editor = EditorState(
                            source = source,
                            preview = preview,
                            animated = source.durationMs > 0,
                            endMs = min(source.durationMs, DEFAULT_CLIP_MS),
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(friendly(e))
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    // ---------- Editor ----------

    fun setAnimated(animated: Boolean) {
        val ed = _state.value.editor ?: return
        _state.update { it.copy(editor = ed.copy(animated = animated)) }
        if (animated) previewAt(ed.startMs)
    }

    fun setRange(startMs: Long, endMs: Long) {
        val ed = _state.value.editor ?: return
        _state.update { it.copy(editor = ed.copy(startMs = startMs, endMs = endMs)) }
        previewAt(startMs)
    }

    fun previewAt(ms: Long) {
        val ed = _state.value.editor ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                sourceLock.withLock { ed.source.frameAt(ms).copy(Bitmap.Config.ARGB_8888, false) }
            }
            _state.update { st ->
                val cur = st.editor
                if (cur != null && cur.source === ed.source) st.copy(editor = cur.copy(preview = bmp, previewMs = ms)) else st
            }
        }
    }

    fun closeEditor() {
        val ed = _state.value.editor ?: return
        previewJob?.cancel()
        _state.update { it.copy(editor = null) }
        viewModelScope.launch(Dispatchers.Default) { sourceLock.withLock { ed.source.close() } }
    }

    fun save(t: Transform) {
        val ed = _state.value.editor ?: return
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = Busy.Rendering(0f)) }
        workJob = viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) {
                    sourceLock.withLock {
                        if (ed.animated && ed.canAnimate) {
                            StickerRenderer.encodeAnimated(ed.source, t, ed.startMs, ed.endMs) { p ->
                                _state.update { it.copy(busy = Busy.Rendering(p)) }
                            }
                        } else {
                            StickerRenderer.encodeStatic(StickerRenderer.render(ed.preview, t))
                        }
                    }
                }
                withContext(Dispatchers.IO) { store.add(bytes, ed.animated && ed.canAnimate) }
                closeEditor()
                _state.update { it.copy(packs = store.packs()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(friendly(e))
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    fun cancelWork() {
        workJob?.cancel()
        _state.update { it.copy(busy = null) }
    }

    // ---------- Packs ----------

    fun stickerFile(pack: Pack, name: String) = store.stickerFile(pack.id, name)

    fun delete(pack: Pack, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            store.delete(pack.id, name)
            _state.update { it.copy(packs = store.packs()) }
        }
    }

    fun addIntent(pack: Pack): Intent? = WhatsApp.addIntent(app, pack)

    fun refreshAdded() {
        viewModelScope.launch(Dispatchers.IO) {
            val added = store.packs().filter { WhatsApp.isAdded(app, it) }.map { it.id }.toSet()
            _state.update { it.copy(added = added) }
        }
    }

    // ---------- Mensajes ----------

    fun showMessage(text: String) {
        _messages.trySend(text)
    }

    private fun friendly(e: Exception): String = when (e) {
        is UserError -> e.message ?: "Algo salió mal"
        is UnknownHostException -> "Sin conexión a internet"
        is SocketTimeoutException -> "El sitio tardó demasiado en responder"
        is IOException -> "No se pudo descargar (${e.message ?: e.javaClass.simpleName})"
        else -> "Algo salió mal (${e.javaClass.simpleName}: ${e.message ?: "sin detalle"})"
    }

    override fun onCleared() {
        _state.value.editor?.source?.close()
    }

    private companion object {
        const val DEFAULT_CLIP_MS = 5_000L
    }
}
