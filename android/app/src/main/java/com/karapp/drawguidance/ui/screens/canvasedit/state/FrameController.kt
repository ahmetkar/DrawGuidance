package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Kağıt ve kırpma: tuvali kırpma/genişletme, seçili elemanı kırpma (dışarıda kalan kısım gizlenir, silinmez)
 * ve kağıdın arka plan rengi.
 */
class FrameController(private val store: EditorStore, private val host: EditorHost) {

    private val _cropRect = MutableStateFlow<Rect?>(null)
    /** Kırp modundaki çerçeve (tuval koordinatlarında); kırpılınca dışarıda kalan çizimler silinmez, gizli kalır. */
    val cropRect: StateFlow<Rect?> = _cropRect.asStateFlow()

    /** Kırpılan elemanlar (boşsa tuval kırpılır). */
    private val _cropTarget = MutableStateFlow<Set<String>>(emptySet())
    val cropTarget: StateFlow<Set<String>> = _cropTarget.asStateFlow()

    /** Kırpılan elemanların kırpmasız tüm alanı (çerçeve bu alanın içinde kalır). */
    private val _cropArea = MutableStateFlow<Rect?>(null)
    val cropArea: StateFlow<Rect?> = _cropArea.asStateFlow()

    /** Mod değişiminde: kırpmaya girerken çerçeve hazırlanır, çıkarken bırakılır. */
    fun onModeChange(from: EditMode, to: EditMode) {
        val current = store.canvas.value
        if (to == EditMode.CROP && current != null) {
            if (from != EditMode.CROP) initCrop(current)
        } else if (to != EditMode.CROP) {
            _cropRect.value = null
            _cropTarget.value = emptySet()
            _cropArea.value = null
        }
    }

    /** Geri alma sonrası: kırpma çerçevesi güncel içeriğe göre yeniden hesaplanır. */
    fun onContentReplaced() {
        val current = store.canvas.value ?: return
        if (host.mode.value == EditMode.CROP) initCrop(current)
    }

    private fun fullBounds(strokes: List<Stroke>, ids: Set<String>): Rect? =
        ElementOps.bounds(strokes.filter { it.id in ids && it.brushType != BrushType.ERASER }.map { it.copy(clip = null) })

    /** Kırpma çerçevesi: seçili eleman varsa onun görünen alanı (kırpma ona uygulanır), yoksa tüm tuval. */
    private fun initCrop(current: CanvasUi) {
        val targets = store.selection
        val visible = if (targets.isEmpty()) null else ElementOps.selectionBounds(current.strokes, targets)
        val area = if (visible == null) null else fullBounds(current.strokes, targets)
        if (visible != null && area != null) {
            _cropTarget.value = targets
            _cropArea.value = area
            _cropRect.value = visible
        } else {
            _cropTarget.value = emptySet()
            _cropArea.value = null
            _cropRect.value = Rect(0f, 0f, current.width.toFloat(), current.height.toFloat())
        }
    }

    fun updateCrop(rect: Rect) {
        val area = _cropArea.value
        if (_cropTarget.value.isNotEmpty() && area != null) {
            // Eleman kırpması: çerçeve elemanın tüm alanının içinde, en az birkaç birim
            // (ince bir çizgide alan bu sınırdan dar olabilir: o eksende tüm alan kalır)
            val minW = min(ELEMENT_CROP_MIN, area.width)
            val minH = min(ELEMENT_CROP_MIN, area.height)
            val l = rect.left.coerceIn(area.left, area.right - minW)
            val t = rect.top.coerceIn(area.top, area.bottom - minH)
            val r = rect.right.coerceIn(min(l + minW, area.right), area.right)
            val b = rect.bottom.coerceIn(min(t + minH, area.bottom), area.bottom)
            _cropRect.value = Rect(l, t, r, b)
            return
        }
        val current = store.canvas.value ?: return
        val w = current.width.toFloat()
        val h = current.height.toFloat()
        // Küçük tuvalde (en küçük kırpma boyutundan dar) o eksende tüm genişlik kalır
        val minW = min(MIN_CANVAS.toFloat(), w)
        val minH = min(MIN_CANVAS.toFloat(), h)
        val l = rect.left.coerceIn(0f, w - minW)
        val t = rect.top.coerceIn(0f, h - minH)
        val r = rect.right.coerceIn(min(l + minW, w), w)
        val b = rect.bottom.coerceIn(min(t + minH, h), h)
        _cropRect.value = Rect(l.roundToInt().toFloat(), t.roundToInt().toFloat(), r.roundToInt().toFloat(), b.roundToInt().toFloat())
    }

    fun resetCrop() {
        val current = store.canvas.value ?: return
        _cropArea.value?.takeIf { _cropTarget.value.isNotEmpty() }?.let { _cropRect.value = it; return }
        _cropRect.value = Rect(0f, 0f, current.width.toFloat(), current.height.toFloat())
    }

    /** Çerçeveyi uygular; dönen değer tuvalin sol-üst köşesinin kayması (görünüm sabit kalsın diye). */
    fun applyCrop(): Offset? {
        val rect = _cropRect.value ?: return null
        val current = store.canvas.value ?: return null
        val targets = _cropTarget.value
        val area = _cropArea.value
        if (targets.isNotEmpty() && area != null) {
            // Elemana kırpma: dörtgen elemanlarla birlikte saklanır; tüm alanı kapsıyorsa kırpma kalkar
            val covers = rect.left <= area.left + 0.5f && rect.top <= area.top + 0.5f &&
                rect.right >= area.right - 0.5f && rect.bottom >= area.bottom - 0.5f
            val q = ContentCodec::quantize
            val quad = listOf(
                StrokePoint(q(rect.left), q(rect.top)), StrokePoint(q(rect.right), q(rect.top)),
                StrokePoint(q(rect.right), q(rect.bottom)), StrokePoint(q(rect.left), q(rect.bottom)),
            )
            // Silgiler kırpılmaz: başka elemanlara da değen silginin etkisi kırpma dışında kaybolmasın
            val updated = current.strokes.map {
                if (it.id in targets && it.brushType != BrushType.ERASER) it.copy(clip = if (covers) null else quad) else it
            }
            host.finishAction()
            if (updated != current.strokes) store.commit(updated)
            return null
        }
        val l = rect.left.roundToInt(); val t = rect.top.roundToInt()
        val r = rect.right.roundToInt(); val b = rect.bottom.roundToInt()
        host.finishAction()
        if (l == 0 && t == 0 && r == current.width && b == current.height) return null
        resizeCanvas(l, t, r, b)
        return Offset(l.toFloat(), t.toFloat())
    }

    /**
     * Genişletme modu: yeni kağıt, eski tuval koordinatlarında [left],[top],[right],[bottom]
     * dikdörtgenidir. Sol/üst kenar dışarı çekildiyse çizimler yeni orijine göre kaydırılır.
     */
    fun resizeCanvas(left: Int, top: Int, right: Int, bottom: Int) {
        val current = store.canvas.value ?: return
        val width = (right - left).coerceIn(MIN_CANVAS, MAX_CANVAS)
        val height = (bottom - top).coerceIn(MIN_CANVAS, MAX_CANVAS)
        if (left == 0 && top == 0 && width == current.width && height == current.height) return
        // Tüm sayfalar aynı boyutta: kaydırma hepsine uygulanır
        val pages = if (left == 0 && top == 0) current.pages else current.pages.map { page ->
            ElementOps.translate(page, page.mapTo(HashSet()) { it.id }, -left.toFloat(), -top.toFloat())
        }
        store.commit(pages, width, height, current.backgroundColorHex)
    }

    /**
     * Kağıdın arka plan rengi (geri alınabilir). v24 [pageOnly] iken yalnız açık sayfanın rengi değişir (not tuvali);
     * değilse tüm sayfaların ortak rengi olur ve sayfaların kendi renkleri kalkar.
     */
    fun setBackgroundColor(
        colorHex: String, coalesce: String? = null, pageOnly: Boolean = false,
        /** v41 ortak arka plan da aynı adımda değişir (yalnız ortak kağıt renginde; Uygula). */
        shared: com.karapp.drawguidance.data.model.BackgroundSpec? = null,
    ): Boolean {
        val current = store.canvas.value ?: return false
        val hex = colorHex.uppercase()
        if (pageOnly) {
            val page = current.currentPage
            if (hex.equals(current.paperOf(page), ignoreCase = true)) return false
            val list = paddedBackgrounds(current)
            list[page] = (list[page] ?: com.karapp.drawguidance.data.model.PageBackground()).copy(paperHex = hex)
            val pages = current.pages.toMutableList()
            pages[page] = com.karapp.drawguidance.ui.drawing.GraphPaper.recolor(listOf(pages[page]), hex)[0]
            store.commit(pages, current.width, current.height, current.backgroundColorHex, coalesce, pageBackgrounds = list)
            return true
        }
        val overrides = current.pageBackgrounds.any { it?.paperHex != null }
        if (hex.equals(current.backgroundColorHex, ignoreCase = true) && !overrides) return false
        val cleared = current.pageBackgrounds.map { it?.copy(paperHex = null)?.takeUnless { p -> p.isEmpty } }
        // v18: grafikler yeni kağıt rengine uyar (aynı geri alma adımında)
        store.commit(com.karapp.drawguidance.ui.drawing.GraphPaper.recolor(current.pages, hex), current.width, current.height, hex, coalesce,
            pageBackgrounds = if (overrides) cleared else null, shared = shared)
        return true
    }

    /**
     * v24 açık sayfanın desen/resmi ([pageOnly]) ya da sayfaların kendi desen/resimlerinin kaldırılması (ortak arka plan
     * her sayfada görünsün). Geri alınabilir. Değişiklik olduysa true.
     */
    fun setPageSpec(spec: com.karapp.drawguidance.data.model.BackgroundSpec?, coalesce: String? = null): Boolean {
        val current = store.canvas.value ?: return false
        val page = current.currentPage
        val list = paddedBackgrounds(current)
        val updated = (list[page] ?: com.karapp.drawguidance.data.model.PageBackground()).copy(spec = spec).takeUnless { it.isEmpty }
        if (updated == list[page]) return false
        list[page] = updated
        store.commit(current.pages, current.width, current.height, current.backgroundColorHex, coalesce, pageBackgrounds = list)
        return true
    }

    /** v24 sayfaların kendi desen/resimlerini kaldırır (ortak desen/resim tüm sayfalarda). */
    fun clearPageSpecs(): Boolean {
        val current = store.canvas.value ?: return false
        if (current.pageBackgrounds.none { it?.spec != null }) return false
        val cleared = current.pageBackgrounds.map { it?.copy(spec = null)?.takeUnless { p -> p.isEmpty } }
        store.commit(current.pages, current.width, current.height, current.backgroundColorHex, pageBackgrounds = cleared)
        return true
    }

    /**
     * v24 "Tüm sayfalara uygula": açık sayfanın rengi ve deseni/resmi ortak arka plan olur, sayfaların kendi arka
     * planları kalkar. Yeni ortak desen/resmi döner (kaydetmek için).
     */
    fun applyPageToAll(): com.karapp.drawguidance.data.model.BackgroundSpec? {
        val current = store.canvas.value ?: return null
        val hex = current.pagePaperHex.uppercase()
        val spec = current.pageBackground
        store.commit(
            com.karapp.drawguidance.ui.drawing.GraphPaper.recolor(current.pages, hex), current.width, current.height, hex,
            // v45 çizgi roman kapağı kapak olarak kalır (yalnız görünümü ortak arka plana döner)
            pageBackgrounds = if (current.hasCover) listOf(com.karapp.drawguidance.data.model.PageBackground(cover = true)) else emptyList(), shared = spec,
        )
        return spec
    }

    private fun paddedBackgrounds(current: com.karapp.drawguidance.data.model.CanvasUi): MutableList<com.karapp.drawguidance.data.model.PageBackground?> {
        val list = current.pageBackgrounds.toMutableList()
        while (list.size < current.pages.size) list.add(null)
        return list
    }

    companion object {
        const val MIN_CANVAS = 64
        const val MAX_CANVAS = 8192
        /** Eleman kırpma çerçevesinin en küçük kenarı (tuval birimi). */
        const val ELEMENT_CROP_MIN = 8f
    }
}
