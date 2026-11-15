package com.karapp.drawguidance.ui.screens.canvasedit.state

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.karapp.drawguidance.content.image.PhotoEdit
import com.karapp.drawguidance.content.image.PhotoOps
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.PreparedImage
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.notes.TempCanvases
import com.karapp.drawguidance.data.notes.TempImageGeometry
import com.karapp.drawguidance.ui.screens.canvasedit.EditEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v52 Foto düzenleme modu: seçili resmin bütün raster düzenlemeleri ([PhotoEdit]; kırp/döndür, ışık, renk, filtre,
 * efekt, arka plan, rötuş fırçaları). Düzenleme yıkıcı değildir: önizleme küçük kopyadan, kayıt tam çözünürlüklü
 * kopyadan baştan hesaplanır; geri al/yinele durum geçmişidir. Kaydedince yeni resim verisi tuvale yazılır ve resim
 * elemanı onu gösterir (oran değiştiyse çerçeve yeni orana uyar). Tek geri alma adımıdır.
 */
class PhotoEditController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val assetStore: CanvasAssetStore,
) {
    private class Session(
        val strokeId: String,
        val full: IntArray, val fw: Int, val fh: Int,
        val small: IntArray, val sw: Int, val sh: Int,
    )

    private var session: Session? = null

    private val _open = MutableStateFlow<String?>(null)
    /** Düzenlenen resim elemanının kimliği (pencere açık). */
    val open: StateFlow<String?> = _open.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _edit = MutableStateFlow(PhotoEdit())
    val edit: StateFlow<PhotoEdit> = _edit.asStateFlow()

    private val _preview = MutableStateFlow<ImageBitmap?>(null)
    /** Düzenlenmiş önizleme (kırpma sekmesinde kırpılmadan). */
    val preview: StateFlow<ImageBitmap?> = _preview.asStateFlow()

    private val _original = MutableStateFlow<ImageBitmap?>(null)
    /** Özgün resmin önizleme kopyası (Önce/sonra). */
    val original: StateFlow<ImageBitmap?> = _original.asStateFlow()

    private val _rendering = MutableStateFlow(false)
    val rendering: StateFlow<Boolean> = _rendering.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _cropMode = MutableStateFlow(false)
    /** Kırpma sekmesi: önizleme kırpılmadan gösterilir, çerçeve üstte çizilir. */
    val cropMode: StateFlow<Boolean> = _cropMode.asStateFlow()

    private val _history = MutableStateFlow(0 to 0)
    /** (geri alınabilir, yinelenebilir) adım sayısı. */
    val history: StateFlow<Pair<Int, Int>> = _history.asStateFlow()

    private val undoStack = ArrayDeque<PhotoEdit>()
    private val redoStack = ArrayDeque<PhotoEdit>()
    private var lastKey: String? = null
    private var renderJob: Job? = null

    /** Önizleme kaynağının boyutu (fırça ve dokunma noktaları kaynağa bununla çevrilir). */
    val sourceSize: Pair<Int, Int> get() = session?.let { it.sw to it.sh } ?: (1 to 1)

    /** Seçili (ya da [strokeId]) resmi foto düzenlemede açar. */
    fun open(strokeId: String? = null) {
        val c = store.canvas.value ?: return
        val image = (strokeId?.let { id -> c.strokes.firstOrNull { it.id == id } }
            ?: store.selectedStroke { it.brushType == BrushType.IMAGE })?.takeIf { it.brushType == BrushType.IMAGE } ?: return
        val assetId = image.assetId ?: return
        if (_open.value != null) return
        _open.value = image.id
        _edit.value = PhotoEdit()
        _preview.value = null; _original.value = null
        undoStack.clear(); redoStack.clear(); lastKey = null; publishHistory()
        _cropMode.value = false
        _loading.value = true
        host.scope.launch {
            try {
                val s = withContext(Dispatchers.Default) {
                    val bmp = com.karapp.drawguidance.data.images.CanvasImageCache.get(assetId)?.asAndroidBitmap()
                        ?: assetStore.bytes(assetId)?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                        ?: return@withContext null
                    val source = com.karapp.drawguidance.platform.Compat.readable(bmp)
                    val maxFull = if (com.karapp.drawguidance.platform.DeviceCaps.tier == com.karapp.drawguidance.platform.DeviceCaps.Tier.LOW) 1600f else 2400f
                    val full = scaled(source, maxFull)
                    val small = scaled(full, PREVIEW_SIDE.toFloat())
                    Session(image.id, full.pixels(), full.width, full.height, small.pixels(), small.width, small.height)
                }
                if (s == null || _open.value != image.id) { if (s == null) failAndClose(); return@launch }
                session = s
                _original.value = toImage(s.small, s.sw, s.sh)
                _preview.value = _original.value
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                com.karapp.drawguidance.diagnostics.AppLog.e("PhotoEdit", "open", e, userVisible = false)
                failAndClose()
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Düzenleme durumunu değiştirir. Aynı [key] ile art arda gelen değişiklikler (kaydırıcı sürükleme) tek geri alma
     * adımıdır; [key] null ise her değişiklik ayrı adımdır.
     */
    fun update(key: String? = null, block: (PhotoEdit) -> PhotoEdit) {
        val before = _edit.value
        val after = block(before)
        if (after == before) return
        if (key == null || key != lastKey) {
            undoStack.addLast(before)
            if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
            redoStack.clear()
        }
        lastKey = key
        _edit.value = after
        publishHistory()
        render()
    }

    /** Kaydırıcı bırakıldı: bir sonraki değişiklik yeni adım olur. */
    fun endGesture() { lastKey = null }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_edit.value)
        _edit.value = prev; lastKey = null
        publishHistory(); render()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_edit.value)
        _edit.value = next; lastKey = null
        publishHistory(); render()
    }

    fun reset() = update { PhotoEdit() }

    fun setCropMode(on: Boolean) {
        if (_cropMode.value == on) return
        _cropMode.value = on
        render()
    }

    /** Önizlemedeki (0..1) nokta kaynak resimde nereye düşer (fırça izi, arka plan dokunuşu). */
    fun toSource(u: Float, v: Float): Pair<Float, Float> {
        val (w, h) = sourceSize
        return PhotoOps.toSource(u, v, w, h, _edit.value, skipCrop = _cropMode.value)
    }

    private fun render() {
        val s = session ?: return
        val e = _edit.value
        val skip = _cropMode.value
        renderJob?.cancel()
        renderJob = host.scope.launch {
            // Kaydırıcı sürüklenirken hesaplar üst üste binmesin
            delay(16)
            _rendering.value = true
            try {
                val img = withContext(Dispatchers.Default) {
                    val (px, w, h) = PhotoOps.render(s.small, s.sw, s.sh, e, skipCrop = skip) { !isActive }
                    if (!isActive) null else toImage(px, w, h)
                }
                if (img != null && isActive) _preview.value = img
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                com.karapp.drawguidance.diagnostics.AppLog.e("PhotoEdit", "render", e, userVisible = false)
            } finally {
                if (renderJob == coroutineContext[Job]) _rendering.value = false
            }
        }
    }

    /** Tam çözünürlükte uygular; yeni resim verisi tuvaldeki resmin yerine geçer ve pencere kapanır. */
    fun save() {
        val s = session ?: return
        if (_saving.value) return
        val e = _edit.value
        if (e.isIdentity) { close(); return }
        _saving.value = true
        host.scope.launch {
            try {
                val prepared = withContext(Dispatchers.Default) {
                    val (px, w, h) = PhotoOps.render(s.full, s.fw, s.fh, e)
                    val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
                    val opaque = px.all { it ushr 24 == 0xFF }
                    val bytes = com.karapp.drawguidance.data.library.BitmapCodec.encode(bmp, opaque = opaque)
                    PreparedImage(bytes, if (opaque) "image/webp" else "image/png", w, h, bmp, bytes.size, 0f)
                }
                val newId = assetStore.save(store.canvasLocalId, prepared)
                val current = store.canvas.value
                if (current != null && current.strokes.any { it.id == s.strokeId }) {
                    val result = TempCanvases.Result(
                        tempId = "", sourceId = store.canvasLocalId, strokeId = s.strokeId, assetId = newId,
                        width = prepared.width, height = prepared.height,
                        reshaped = TempImageGeometry.reshaped(s.fw, s.fh, prepared.width, prepared.height),
                    )
                    store.commit(current.strokes.map { if (it.id == s.strokeId) TempImageGeometry.replace(it, result) else it })
                    host.emit(EditEvent.PhotoSaved)
                }
                close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                com.karapp.drawguidance.diagnostics.AppLog.e("PhotoEdit", "save", e, userVisible = true)
                host.emit(EditEvent.PhotoFailed)
            } finally {
                _saving.value = false
            }
        }
    }

    /** Kaydetmeden kapatır. */
    fun close() {
        renderJob?.cancel()
        session = null
        _open.value = null
        _preview.value = null; _original.value = null
        _edit.value = PhotoEdit()
        undoStack.clear(); redoStack.clear(); publishHistory()
    }

    private fun failAndClose() {
        host.emit(EditEvent.PhotoFailed)
        close()
    }

    private fun publishHistory() { _history.value = undoStack.size to redoStack.size }

    companion object {
        const val PREVIEW_SIDE = 1024
        private const val MAX_HISTORY = 80

        private fun scaled(src: Bitmap, maxSide: Float): Bitmap {
            val scale = minOf(1f, maxSide / maxOf(src.width, src.height))
            return if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true) else src
        }

        private fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

        private fun toImage(px: IntArray, w: Int, h: Int): ImageBitmap = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
