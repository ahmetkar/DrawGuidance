package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.Model3DStore
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.PreparedImage
import com.karapp.drawguidance.data.library.BitmapCodec
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Model3DLink
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.hypot

/**
 * v23 tuvaldeki 3B şekiller: tuvalde yalnızca genel işlemler (ekle, seç, taşı, ölçekle, döndür, sil) yapılır; şekil
 * 3B modelin o anki görünümünün resmidir ([Stroke.assetId]), modelin kendisi tuvalin verisinde ([Model3DLink]) saklanır.
 * v56 aşama 10: kaynaklar yalnız kütüphane ([com.karapp.drawguidance.content.model3d.Catalog3D]) ve GLB dosyası;
 * eklemeden önce açı seçilir, sonra "Açıyı değiştir" ile değişir. Tuvalden 3B düzenleyiciye yol yoktur.
 */
class Model3DController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val assetStore: CanvasAssetStore,
    private val repository: Model3DRepository?,
) {
    private val _busy = MutableStateFlow(false)
    /** Şekil resmi hazırlanıyor. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Seçili tek 3B şekil (yoksa null). */
    fun selected(): Stroke? = store.selectedStroke { it.model != null }?.takeIf { store.selection.size == 1 }

    /** Kütüphanedeki şeklin modeli (açı seçimi için). */
    fun catalogModel(catalogId: String): Model3D? = repository?.fromCatalog(catalogId)

    /** GLB/glTF dosyasının modeli (ayrıntı [maxTriangles]'a düşürülür); okunamazsa bildirilir, null döner. */
    suspend fun glbModel(bytes: ByteArray, name: String, maxTriangles: Int): Model3D? {
        val repo = repository ?: return null
        _busy.value = true
        return try {
            repo.importGlb(bytes, name, maxTriangles).model
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            // v27 bellek yetmezse (büyük GLB, giriş düzeyi cihaz) uygulama kapanmaz, bildirilir
            com.karapp.drawguidance.diagnostics.AppLog.e("GlbImport", "İçe aktarılamadı", e, userVisible = true)
            host.emit(com.karapp.drawguidance.ui.screens.canvasedit.EditEvent.Message(MSG_IMPORT_FAIL.format(e.message ?: e.javaClass.simpleName)))
            null
        } finally {
            _busy.value = false
        }
    }

    /** Seçili 3B şeklin tuvalde saklanan modeli (Açıyı değiştir için). */
    suspend fun selectedModel(): Model3D? {
        val link = selected()?.model ?: return null
        val bytes = assetStore.bytes(link.dataAssetId) ?: return null
        return runCatching { Model3DStore.decode(bytes) }.getOrNull()
    }

    /** Önizleme: modeli [yaw]/[pitch] açısından [size] px kare resim olarak çizer. */
    suspend fun preview(model: Model3D, yaw: Float, pitch: Float, size: Int): android.graphics.Bitmap? =
        repository?.render(withAngle(model, yaw, pitch), size, size, ground = false, margin = 0.06f)

    /** Modeli seçilen açıyla tuvalin ortasına (ya da [center]'a) ekler. */
    fun insert(model: Model3D, yaw: Float, pitch: Float, center: Offset? = null) {
        host.scope.launch {
            _busy.value = true
            try {
                place(withAngle(model, yaw, pitch), null, center)
            } finally {
                _busy.value = false
            }
        }
    }

    /** Seçili 3B şeklin bakış açısını değiştirir (model aynı kalır; tek geri alma adımı). */
    fun changeAngle(yaw: Float, pitch: Float) {
        val stroke = selected() ?: return
        host.scope.launch {
            val model = selectedModel() ?: return@launch
            _busy.value = true
            try {
                place(withAngle(model, yaw, pitch), stroke, null)
            } finally {
                _busy.value = false
            }
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
        val dataId = assetStore.saveData(store.canvasLocalId, Model3DStore.encodeShape(model), MIME)
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
        private const val MSG_IMPORT_FAIL = "İçe aktarılamadı: %s"
        const val IMAGE_SIZE = 720
        /** v24 tuvaldeki 3B şeklin verisi GLB (eski "application/x-dg-model3d" kayıtları da okunur). */
        const val MIME = Model3DStore.MIME

        /** Açı sınırları: dikey eğim −80..80, yatay dönüş 0..360. */
        fun withAngle(model: Model3D, yaw: Float, pitch: Float): Model3D =
            model.copy(camera = model.camera.copy(yaw = ((yaw % 360f) + 360f) % 360f, pitch = pitch.coerceIn(-80f, 80f)))
    }
}
