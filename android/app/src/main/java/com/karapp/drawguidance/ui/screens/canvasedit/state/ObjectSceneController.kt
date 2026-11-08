package com.karapp.drawguidance.ui.screens.canvasedit.state

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.karapp.drawguidance.data.images.CanvasImageCache
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.Layers
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.drawCanvasContent
import com.karapp.drawguidance.ui.drawing.scene.SceneAnalyzer
import com.karapp.drawguidance.ui.drawing.scene.ScenePlacement
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * v43 gelişmiş resim ve çizgi romanda Düzenle → "Arkaplan ile Nesneyi Uyumla" ve "Nesneyi Mekâna Yerleştir".
 *
 * Nesne: seçili tek resim elemanı. Arka plan: nesnenin altındaki her şey (kağıt, arka plan resmi/deseni ve sırada
 * nesneden önce gelen görünür elemanlar). Bütün çözümleme cihazda, küçültülmüş piksellerle ([SceneAnalyzer]).
 *
 * Değişiklikler önce önizlenir ([EditorStore.previewStrokes]: geçmişe ve kayda girmez); Uygula tek bir geri alınabilir
 * adım olarak kaydeder, İptal özgün hâle döndürür. Uyum yıkıcı değildir (resmin dosyası değişmez; ayarlar elemanın
 * [Stroke.harmony] alanında), yerleştirme yalnız dönüşümdür (taşıma ve oranı koruyan ölçek).
 */
class ObjectSceneController(private val store: EditorStore, private val host: EditorHost) {

    data class HarmonyDraft(
        val target: Stroke,
        val options: SceneAnalyzer.Options,
        val analysis: SceneAnalyzer.Analysis?,
        val busy: Boolean = false,
        /** Nesnede zaten uyum var (kaldırılabilir). */
        val hadHarmony: Boolean = false,
    )

    data class PlaceDraft(
        val target: Stroke,
        val perspective: SceneAnalyzer.Perspective?,
        val horizonY: Float,
        val footY: Float?,
        val scaleMul: Float = 1f,
        val result: ScenePlacement.Result? = null,
        val references: Int = 0,
        val busy: Boolean = false,
        val canvasHeight: Float = 1f,
    )

    private val _harmony = MutableStateFlow<HarmonyDraft?>(null)
    val harmony: StateFlow<HarmonyDraft?> = _harmony.asStateFlow()
    private val _place = MutableStateFlow<PlaceDraft?>(null)
    val place: StateFlow<PlaceDraft?> = _place.asStateFlow()
    private var job: Job? = null

    /** Bu tuval türünde araçlar var mı (gelişmiş resim ve çizgi roman). */
    fun available(kind: CanvasKind?): Boolean = kind == CanvasKind.ART || kind == CanvasKind.COMIC

    /** Seçili tek resim (nesne). */
    fun target(): Stroke? {
        val c = store.canvas.value ?: return null
        val ids = store.selection
        val picked = c.strokes.filter { it.id in ids && it.brushType != BrushType.ERASER }
        return picked.singleOrNull()?.takeIf { it.brushType == BrushType.IMAGE && it.points.size >= 4 }
    }

    // ------------------------------------------------------------------ uyum

    fun openHarmony(): Boolean {
        if (!available(store.canvas.value?.kind)) return false
        val t = target() ?: return false
        endAll()
        _harmony.value = HarmonyDraft(t, SceneAnalyzer.Options(), null, busy = true, hadHarmony = t.harmony != null)
        job = host.scope.launch {
            val a = withContext(Dispatchers.Default) { runCatching { analyzeHarmony(t) }.getOrNull() }
            val d = _harmony.value ?: return@launch
            _harmony.value = d.copy(analysis = a, busy = false)
            previewHarmony()
        }
        return true
    }

    fun setHarmonyOptions(o: SceneAnalyzer.Options) {
        val d = _harmony.value ?: return
        _harmony.value = d.copy(options = o)
        previewHarmony()
    }

    /** Taslağın sonucu (önizleme ve uygulama aynı). */
    fun harmonized(d: HarmonyDraft): Stroke {
        val a = d.analysis ?: return d.target
        val o = d.options
        val spec = SceneAnalyzer.suggest(a, o)
        val any = o.shadow || o.color || o.edge
        var s = d.target.copy(harmony = if (any && o.intensity > 0f) spec else null)
        // Konum: zemin çizgisi nesnenin altına yakınsa ayaklar ona oturtulur (yalnız taşıma)
        if (o.position) {
            val b = ElementOps.selectionBounds(listOf(s), setOf(s.id))
            val g = a.groundY
            if (b != null && g != null && kotlin.math.abs(g - b.bottom) < b.height * 0.25f) {
                s = ElementOps.translate(listOf(s), setOf(s.id), 0f, g - b.bottom).first()
            }
        }
        return s
    }

    private fun previewHarmony() {
        val d = _harmony.value ?: return
        if (d.analysis == null) return
        val next = harmonized(d)
        val base = store.canvas.value?.strokes ?: return
        store.previewStrokes(base.map { if (it.id == next.id) next else it })
    }

    fun applyHarmony() {
        val d = _harmony.value ?: return
        _harmony.value = null
        job?.cancel()
        store.endPreview()
        if (d.analysis == null) return
        val next = harmonized(d)
        val current = store.canvas.value ?: return
        store.commit(current.strokes.map { if (it.id == next.id) next else it })
        store.select(setOf(next.id))
    }

    /** Nesnedeki uyumu kaldırır (özgün görünüm; geri alınabilir). */
    fun removeHarmony() {
        val d = _harmony.value ?: return
        _harmony.value = null
        job?.cancel()
        store.endPreview()
        val current = store.canvas.value ?: return
        store.commit(current.strokes.map { if (it.id == d.target.id) it.copy(harmony = null) else it })
    }

    fun cancelHarmony() {
        _harmony.value = null
        job?.cancel()
        store.endPreview()
    }

    private fun analyzeHarmony(t: Stroke): SceneAnalyzer.Analysis? {
        val c = store.canvas.value ?: return null
        val obj = CanvasImageCache.get(t.assetId)?.asAndroidBitmap() ?: return null
        val b = ElementOps.selectionBounds(listOf(t), setOf(t.id)) ?: return null
        val pad = max(b.width, b.height) * 0.6f
        val region = Rect(b.left - pad, b.top - pad, b.right + pad, b.bottom + pad)
            .intersect(Rect(0f, 0f, c.width.toFloat(), c.height.toFloat()))
        if (region.width < 2f || region.height < 2f) return null
        val bg = renderBelow(c, t.id, region, 256)
        return try { SceneAnalyzer.analyze(bg, region, b, obj) } finally { bg.recycle() }
    }

    // ------------------------------------------------------------------ mekâna yerleştir

    fun openPlace(): Boolean {
        val c = store.canvas.value ?: return false
        if (!available(c.kind)) return false
        val t = target() ?: return false
        endAll()
        _place.value = PlaceDraft(t, null, c.height * 0.4f, null, busy = true, canvasHeight = c.height.toFloat())
        job = host.scope.launch {
            val p = withContext(Dispatchers.Default) {
                runCatching {
                    val bg = renderBelow(c, t.id, Rect(0f, 0f, c.width.toFloat(), c.height.toFloat()), 320)
                    try { SceneAnalyzer.perspective(bg, c.width.toFloat(), c.height.toFloat()) } finally { bg.recycle() }
                }.getOrNull()
            }
            val d = _place.value ?: return@launch
            _place.value = d.copy(perspective = p, horizonY = p?.horizonY ?: d.horizonY, busy = false)
            previewPlace()
        }
        return true
    }

    /** Elle düzeltme: ufuk, ayak yüksekliği (null: otomatik) ve ek ölçek. */
    fun setPlaceManual(horizonY: Float? = null, footY: Float? = _place.value?.footY, scaleMul: Float? = null) {
        val d = _place.value ?: return
        _place.value = d.copy(
            horizonY = (horizonY ?: d.horizonY).coerceIn(0f, d.canvasHeight),
            footY = footY?.coerceIn(0f, d.canvasHeight),
            scaleMul = (scaleMul ?: d.scaleMul).coerceIn(0.5f, 2f),
        )
        previewPlace()
    }

    /** Taslağın sonucu: yerleştirilmiş nesne. */
    fun placed(d: PlaceDraft): Pair<Stroke, ScenePlacement.Result>? {
        val c = store.canvas.value ?: return null
        val t = d.target
        val b = ElementOps.selectionBounds(listOf(t), setOf(t.id)) ?: return null
        // Referanslar: sahnede (sırada nesneden önce ya da sonra) zemindeki öteki resimler
        val refs = c.strokes.filter { it.id != t.id && it.brushType == BrushType.IMAGE && !it.hidden }.mapNotNull { s ->
            ElementOps.selectionBounds(listOf(s), setOf(s.id))?.let { r -> ScenePlacement.Reference(r.bottom, r.height) }
        }.filter { it.height < c.height * 0.9f } // tam sayfa arka plan resimleri referans sayılmaz
        val r = ScenePlacement.place(b, d.horizonY, c.height.toFloat(), refs, d.footY, d.scaleMul)
        // Ayak noktası (kutunun alt ortası) sabit kalacak şekilde ölçek, sonra hedefe taşıma
        val pivot = Offset(b.center.x, b.bottom)
        var s = ElementOps.scale(listOf(t), setOf(t.id), r.scale, r.scale, pivot).first()
        s = ElementOps.translate(listOf(s), setOf(s.id), r.footX - pivot.x, r.footY - pivot.y).first()
        return s to r
    }

    private fun previewPlace() {
        val d = _place.value ?: return
        if (d.busy) return
        val (s, r) = placed(d) ?: return
        _place.value = d.copy(result = r, references = r.references)
        val base = store.canvas.value?.strokes ?: return
        store.previewStrokes(base.map { if (it.id == s.id) s else it })
    }

    fun applyPlace() {
        val d = _place.value ?: return
        _place.value = null
        job?.cancel()
        store.endPreview()
        if (d.busy) return
        val (s, _) = placed(d) ?: return
        val current = store.canvas.value ?: return
        store.commit(current.strokes.map { if (it.id == s.id) s else it })
        store.select(setOf(s.id))
    }

    fun cancelPlace() {
        _place.value = null
        job?.cancel()
        store.endPreview()
    }

    /** Açık taslaklar kapanır (mod/sayfa değişince, geri almada). */
    fun endAll() {
        if (_harmony.value != null) cancelHarmony()
        if (_place.value != null) cancelPlace()
    }

    // ------------------------------------------------------------------ çizim

    /** Nesnenin altındakilerin ([region] alanı, en uzun kenarı [maxSide] piksel) çizimi. */
    private fun renderBelow(c: CanvasUi, objectId: String, region: Rect, maxSide: Int): Bitmap {
        val strokes = Layers.visible(c.strokes, c.layers)
        val index = strokes.indexOfFirst { it.id == objectId }
        val below = if (index < 0) strokes else strokes.subList(0, index)
        val k = maxSide / max(region.width, region.height)
        val w = max(1, (region.width * k).toInt()); val h = max(1, (region.height * k).toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            scale(k, k, pivot = Offset.Zero) {
                translate(-region.left, -region.top) {
                    drawCanvasContent(hexToColor(c.paperOf(c.currentPage)), below, c.width.toFloat(), c.height.toFloat(), decor = c.backgroundOf(c.currentPage))
                }
            }
        }
        return bmp
    }
}
