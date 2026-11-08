package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.Model3DStore
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.PreparedImage
import com.karapp.drawguidance.data.library.BitmapCodec
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.library.Model3DResults
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.local.Model3DInfo
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Model3DLink
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.hypot

/**
 * v23 tuvaldeki 3B şekiller: tuvalde yalnızca genel işlemler (ekle, seç, taşı, ölçekle, döndür, sil) yapılır; şekil
 * 3B modelin o anki görünümünün resmidir ([Stroke.assetId]), modelin kendisi tuvalin verisinde ([Model3DLink]) saklanır.
 * Düzenleme ayrı pencerede (3B düzenleyici) yapılır; kaydedilince sonuç [Model3DResults] ile buraya döner ve resim
 * yenilenir.
 */
class Model3DController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val assetStore: CanvasAssetStore,
    private val repository: Model3DRepository?,
    private val results: Model3DResults?,
) {
    /** 3B Şekillerim (küçük resimleriyle). */
    val models: StateFlow<List<Model3DInfo>> = (repository?.observe() ?: flowOf(emptyList()))
        .stateIn(host.scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    /** Şekil resmi hazırlanıyor. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        // Düzenleyiciden dönen sonuçlar (tuval yüklendikten sonra) uygulanır
        val res = results
        if (res != null) host.scope.launch {
            store.canvas.filterNotNull().first()
            res.pending.filter { list -> list.any { it.canvasId == store.canvasLocalId } }.collect {
                res.take(store.canvasLocalId).forEach { r -> runCatching { apply(r) }.onFailure { e -> com.karapp.drawguidance.diagnostics.AppLog.e("Model3D", "3B şekil tuvale uygulanamadı", e) } }
            }
        }
    }

    /** Seçili tek 3B şekil (yoksa null). */
    fun selected(): Stroke? = store.selectedStroke { it.model != null }?.takeIf { store.selection.size == 1 }

    /** "3B Şekillerim"deki modeli tuvalin ortasına (ya da [center]'a) ekler. */
    fun insert(modelId: String, center: Offset? = null) {
        val repo = repository ?: return
        host.scope.launch {
            _busy.value = true
            try {
                val model = repo.load(modelId) ?: return@launch
                place(model, null, center)
            } finally {
                _busy.value = false
            }
        }
    }

    /** v24 hazır şekli ([com.karapp.drawguidance.content.model3d.Catalog3D]) yeni kimlikle tuvale ekler. */
    fun insertCatalog(catalogId: String, center: Offset? = null) {
        val repo = repository ?: return
        host.scope.launch {
            _busy.value = true
            try {
                val model = repo.fromCatalog(catalogId) ?: return@launch
                place(model, null, center)
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * v27 GLB/glTF dosyasını içe aktarıp tuvale ekler (ayrıntı [maxTriangles]'a düşürülür). [save] ise model 3B
     * Şekillerim'e ayrı şekil olarak da kaydedilir; değilse yalnız tuvalin verisinde durur (kalemle düzenlenince yine
     * kaydedilebilir).
     */
    fun importGlb(bytes: ByteArray, name: String, maxTriangles: Int, save: Boolean, center: Offset? = null) {
        val repo = repository ?: return
        host.scope.launch {
            _busy.value = true
            try {
                val r = runCatching { repo.importGlb(bytes, name, maxTriangles) }
                val imported = r.getOrNull()
                if (imported == null) {
                    host.emit(com.karapp.drawguidance.ui.screens.canvasedit.EditEvent.Message(MSG_IMPORT_FAIL.format(r.exceptionOrNull()?.message ?: "?")))
                    return@launch
                }
                if (save) repo.save(imported.model)
                place(imported.model, null, center)
                host.emit(
                    com.karapp.drawguidance.ui.screens.canvasedit.EditEvent.Message(
                        (if (save) MSG_IMPORT_SAVED else MSG_IMPORT_CANVAS).format(imported.model.name, imported.triangles),
                    ),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // v27 bellek yetmezse (büyük GLB, giriş düzeyi cihaz) uygulama kapanmaz, bildirilir
                com.karapp.drawguidance.diagnostics.AppLog.e("GlbImport", "İçe aktarılamadı", e, userVisible = true)
                host.emit(com.karapp.drawguidance.ui.screens.canvasedit.EditEvent.Message(MSG_IMPORT_FAIL.format(e.message ?: e.javaClass.simpleName)))
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * Düzenleyicide açmadan önce: model "3B Şekillerim"de yoksa (silinmişse) tuvaldeki veriden geri yüklenir. Açılacak
     * modelin kimliğini döner.
     */
    suspend fun prepareEdit(stroke: Stroke): String? {
        val link = stroke.model ?: return null
        val repo = repository ?: return null
        if (repo.load(link.modelId) != null) return link.modelId
        val bytes = assetStore.bytes(link.dataAssetId) ?: return null
        val model = runCatching { Model3DStore.decode(bytes) }.getOrNull() ?: return null
        repo.save(model.copy(id = link.modelId))
        return link.modelId
    }

    private suspend fun apply(r: Model3DResults.Result) {
        val existing = r.strokeId?.let { id -> store.strokes.firstOrNull { it.id == id } }
        _busy.value = true
        try {
            place(r.model, existing, null)
        } finally {
            _busy.value = false
        }
    }

    /** Modelin resmini ve verisini tuvale yazar; [existing] varsa onu (yerini ve boyunu koruyarak) günceller. */
    private suspend fun place(model: Model3D, existing: Stroke?, center: Offset?) {
        val repo = repository ?: return
        val bmp = repo.render(model, IMAGE_SIZE, IMAGE_SIZE, ground = false, margin = 0.04f)
        val trimmed = trim(bmp)
        val png = BitmapCodec.encode(trimmed, opaque = false)
        val image = PreparedImage(png, "image/png", trimmed.width, trimmed.height, trimmed, png.size, 0f)
        val assetId = assetStore.save(store.canvasLocalId, image)
        val dataId = assetStore.saveData(store.canvasLocalId, Model3DStore.encode(model), MIME)
        val link = Model3DLink(model.id, dataId)
        val current = store.canvas.value ?: return
        val q = ContentCodec::quantize
        val aspect = trimmed.width / trimmed.height.toFloat().coerceAtLeast(1f)
        if (existing != null && current.strokes.any { it.id == existing.id }) {
            val p = existing.points
            val points = if (p.size >= 4) {
                // Merkez ve yükseklik korunur, genişlik yeni resmin oranına göre
                val cx = (p[0].x + p[2].x) / 2f; val cy = (p[0].y + p[2].y) / 2f
                val ux = p[1].x - p[0].x; val uy = p[1].y - p[0].y
                val vx = p[3].x - p[0].x; val vy = p[3].y - p[0].y
                val ul = hypot(ux, uy).coerceAtLeast(1e-3f); val vl = hypot(vx, vy).coerceAtLeast(1e-3f)
                val nul = vl * aspect
                val ax = ux / ul * nul / 2f; val ay = uy / ul * nul / 2f
                val bx = vx / 2f; val by = vy / 2f
                listOf(
                    StrokePoint(q(cx - ax - bx), q(cy - ay - by)), StrokePoint(q(cx + ax - bx), q(cy + ay - by)),
                    StrokePoint(q(cx + ax + bx), q(cy + ay + by)), StrokePoint(q(cx - ax + bx), q(cy - ay + by)),
                )
            } else p
            store.commit(current.strokes.map { if (it.id == existing.id) it.copy(assetId = assetId, model = link, points = points) else it })
            return
        }
        val c = center ?: Offset(current.width / 2f, current.height / 2f)
        val fit = minOf(current.width * 0.45f / trimmed.width, current.height * 0.45f / trimmed.height)
        val hw = trimmed.width * fit / 2f; val hh = trimmed.height * fit / 2f
        val stroke = Stroke(
            id = UUID.randomUUID().toString(),
            points = listOf(
                StrokePoint(q(c.x - hw), q(c.y - hh)), StrokePoint(q(c.x + hw), q(c.y - hh)),
                StrokePoint(q(c.x + hw), q(c.y + hh)), StrokePoint(q(c.x - hw), q(c.y + hh)),
            ),
            colorHex = "#000000",
            brushType = BrushType.IMAGE,
            sizePx = 1f,
            opacity = 1f,
            order = current.strokes.size,
            assetId = assetId,
            model = link,
        )
        store.commit(current.strokes + stroke)
        host.inserted(setOf(stroke.id))
    }

    /** Saydam kenarları kırpar (şeklin kutusu resme tam otursun). */
    private fun trim(bmp: android.graphics.Bitmap): android.graphics.Bitmap {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 8) {
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
        }
        if (r < l || b < t) return bmp
        val pad = 4
        l = (l - pad).coerceAtLeast(0); t = (t - pad).coerceAtLeast(0); r = (r + pad).coerceAtMost(w - 1); b = (b + pad).coerceAtMost(h - 1)
        if (l == 0 && t == 0 && r == w - 1 && b == h - 1) return bmp
        return android.graphics.Bitmap.createBitmap(bmp, l, t, r - l + 1, b - t + 1)
    }

    companion object {
        private const val MSG_IMPORT_SAVED = "\"%s\" tuvale eklendi ve 3B Şekillerim'e kaydedildi (%d üçgen)"
        private const val MSG_IMPORT_CANVAS = "\"%s\" tuvale eklendi (%d üçgen)"
        private const val MSG_IMPORT_FAIL = "İçe aktarılamadı: %s"
        const val IMAGE_SIZE = 720
        /** v24 tuvaldeki 3B şeklin verisi GLB (eski "application/x-dg-model3d" kayıtları da okunur). */
        const val MIME = Model3DStore.MIME
    }
}
