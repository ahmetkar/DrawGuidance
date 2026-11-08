package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.content.ink.InkShapeKind
import com.karapp.drawguidance.content.shapes.SuggestRules
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.data.model.isErasable
import com.karapp.drawguidance.ui.drawing.SuggestPolicy
import com.karapp.drawguidance.ui.drawing.TextRenderer
import com.karapp.drawguidance.ui.drawing.suggestPolicy
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * v17: çizimin şekil düzeltmesi — izin yerini alacak düzgün geometri (aynı fırça, renk ve kalınlıkla). v31 geometrisi
 * tanınmayan çizim yumuşatılır ([kind] null). v38 çizgi önerisinin her çizgisinin düzeltmesi.
 */
data class ShapeFix(val kind: InkShapeKind?, val strokeId: String, val points: List<StrokePoint>)

/**
 * v38 kalemle çizilenin yapay zekâ önerileri (bütün modlarda: resim, not alma, çizgi roman, çocuk, basit resim, basit
 * not). Öneriler üç ayrı türdür; her türün kendi algoritması, kendi balonu ([text], [shape], [line]), kendi ayarı ve
 * kendi tek düğmeli düzeltmesi vardır (bkz. [SuggestRules]):
 * - **Yazı** ([TextSuggester]): birbirine yakın 2+ eleman; yazı sinir ağının "yazı mı" kapısı ve karakter başları.
 *   v39 tek eleman: çizim miktarı fazlaysa ([SuggestRules.bigText]) ve güvenle okunuyorsa.
 * - **Şekil** ([ShapeSuggester]): birbirine yakın 4+ eleman; modun şekil sinir ağı ve şablon veritabanı.
 * - **Çizgi** ([LineSuggester]): 2+ çizgi; klasik makine öğrenmesi ve geometri algoritmaları (sinir ağı yok).
 *   v39 tek eleman: çizim miktarı fazlaysa ([SuggestRules.bigLine]).
 *
 * v39 küçük tek çizgiye hiçbir türde öneri yapılmaz; tek çizgiye şekil önerisi hiç yapılmaz. Kalem kısa bir süre durunca üç tür birbirinden bağımsız denenir; hepsi aynı
 * anda gösterilebilir. Her önerinin düğmesi dikkate alınan bütün çizgileri tek geri alma adımıyla düzeltir. Kapatılan
 * önerinin aynı çizgi kümesi için yeniden önerilmez; çizgi önerisinde kapatılan ya da düzeltilen çizgiler bir daha
 * dikkate alınmaz. Yazı gibi duran (yazı kalemiyle yazılmış ve yazı ağının kapısından geçen) çizgilere çizgi ve şekil
 * önerisi yapılmaz.
 */
class HandwritingController(
    private val store: EditorStore,
    private val host: EditorHost,
    /** Çizim önerileri açık mı (Çiz modunda ve ana ayar açıkken). */
    private val enabled: () -> Boolean,
    /** v21 şekil veritabanı (null: yalnız hazır vektör şekiller önerilir). */
    private val templates: com.karapp.drawguidance.data.repository.TemplateSource? = null,
    /** v23 yazı ve şekil önerisi, v38 çizgi önerisi ayrı ayrı açılıp kapatılır. */
    private val textEnabled: () -> Boolean = { true },
    private val shapeEnabled: () -> Boolean = { true },
    /** v27 seçimde kendiliğinden öneri (tüm modlarda) açık mı. */
    private val selectionEnabled: () -> Boolean = { false },
    private val lineEnabled: () -> Boolean = { true },
) {
    private val textEngine = TextSuggester()
    private val shapeEngine = ShapeSuggester(templates)
    private val lineEngine = LineSuggester()

    private val _text = MutableStateFlow<TextSuggestion?>(null)
    /** Yazı önerisi (balonu ayrı). */
    val text: StateFlow<TextSuggestion?> = _text.asStateFlow()

    private val _shape = MutableStateFlow<ShapeSuggestion?>(null)
    /** Şekil önerisi (balonu ayrı; ilk seçeneğin önizlemesi tuvalde). */
    val shape: StateFlow<ShapeSuggestion?> = _shape.asStateFlow()

    private val _line = MutableStateFlow<LineSuggestion?>(null)
    /** v38 çizgi önerisi (balonu ayrı; düzeltmenin önizlemesi tuvalde). */
    val line: StateFlow<LineSuggestion?> = _line.asStateFlow()

    private var job: Job? = null
    private var selectionJob: Job? = null
    /** Son çizilen (önerilerin tohumu). */
    private var lastInk: String? = null
    /** Seçimden kendiliğinden gösterilen önerinin seçim anahtarı (seçim değişince kalkar). */
    private var autoKey: String? = null
    /** Kapatılan önerilerin çizgi kümeleri (tür başına): aynı küme için yeniden önerilmez. */
    private val quietKeys = LinkedHashSet<String>()
    /** Çizgi önerisinde düzeltilen ya da kapatılan çizgiler: bir daha dikkate alınmaz. */
    private val lineSettled = LinkedHashSet<String>()
    /** Kabul edilen önerinin koyduğu düzgün çizgiler (şablon): hiçbir türde yeniden dikkate alınmaz. */
    private val settled = LinkedHashSet<String>()

    /**
     * Düzeltilebilecek iz: fırçayla çizilmiş (silgi ve nesneler değil). v21: fırçanın görünümünü bozacak öneri verilmez;
     * alan dolduran doku, desen ve yumuşak fırçalarda hiç öneri yok ([SuggestPolicy.NONE]).
     */
    private fun isWritingInk(stroke: Stroke): Boolean =
        stroke.brushType.isErasable && stroke.points.isNotEmpty() && stroke.brushType.suggestPolicy() != SuggestPolicy.NONE

    /** Tuvalin boyutu (v39 tek çizginin çizim miktarı tuvale göre ölçülür; 0: bilinmiyor). */
    private fun canvasW(): Float = store.canvas.value?.width?.toFloat() ?: 0f
    private fun canvasH(): Float = store.canvas.value?.height?.toFloat() ?: 0f

    /**
     * Seçimde öneri verilebilir mi: v38 en az iki fırça izi; v39 ya da çizim miktarı fazla tek iz (çizgi önerisi için
     * [SingleInk.bigLine], yazı kalemiyle yazı önerisi için [SingleInk.bigText] ve geometrik şekil değil).
     */
    fun canSuggest(strokes: List<Stroke>): Boolean {
        val ink = strokes.filter { isWritingInk(it) }
        if (ink.size >= SuggestRules.LINE_MIN) return true
        val one = ink.singleOrNull() ?: return false
        val w = canvasW(); val h = canvasH()
        // Yazı adayı: düzgün geometrik şekil değil (tanıma güveni öneri hesaplanırken denetlenir)
        return SingleInk.bigLine(one, w, h) || (textEngine.isWritingPen(one) && SingleInk.bigText(one, w, h) && !SingleInk.geometric(one))
    }

    /** Yeni iz çizildi: kısa bir beklemeden sonra (yeni iz gelmezse) üç tür ayrı ayrı denenir. */
    /**
     * v43 öneri dışı çizgi (ör. şekil kalıbının üzerinde çizilen): bir daha hiçbir öneride dikkate alınmaz, gösterilen
     * öneriler kalkar.
     */
    fun exclude(stroke: Stroke) {
        settle(settled, listOf(stroke.id))
        settle(lineSettled, listOf(stroke.id))
        if (lastInk == stroke.id) lastInk = null
        job?.cancel()
        clearAll()
    }

    fun onInk(stroke: Stroke) {
        if (!enabled()) return
        if (!isWritingInk(stroke)) { reset(); return }
        lastInk = stroke.id
        // Yeni çizgi grupları değiştirir: gösterilen öneriler kalkar (yeniden hesaplanır)
        clearAll()
        job?.cancel()
        job = host.scope.launch {
            delay(PAUSE_MS)
            runCatching { recognize() }
        }
    }

    private class Found(val text: TextSuggestion?, val shape: ShapeSuggestion?, val line: LineSuggestion?)

    private fun key(kind: String, ids: Collection<String>) = kind + ":" + ids.sorted().joinToString(",")

    private suspend fun recognize() {
        val current = store.canvas.value ?: return
        val all = current.strokes
        val last = all.lastOrNull { it.id == lastInk } ?: return
        val wantText = textEnabled(); val wantShape = shapeEnabled(); val wantLine = lineEnabled()
        val cw = current.width.toFloat(); val ch = current.height.toFloat()
        val quiet = quietKeys.toSet(); val lineDone = lineSettled.toSet(); val done = settled.toSet()
        val found = withContext(Dispatchers.Default) {
            val pool = all.filter { it.id == last.id || it.id !in done }
            // Yazı: son yazılanın satırında birbirine yakın 2+ yazı kalemi izi (v39 ya da çizim miktarı fazla tek iz)
            val textGroup = if (textEngine.isWritingPen(last)) textEngine.group(pool, last) else emptyList()
            val writing = textGroup.isNotEmpty() && textEngine.looksLikeText(textGroup, cw, ch)
            val t = if (wantText && writing && key("text", textGroup.map { it.id }) !in quiet) {
                runCatching { textEngine.suggest(textGroup, canvasW = cw, canvasH = ch) }.getOrNull()
            } else null
            // Şekil: son çizilenle birbirine yakın 4+ eleman (yazı gibi duruyorsa yok)
            val sh = if (wantShape) {
                val g = shapeEngine.group(pool, last, ::isWritingInk)
                if (g.size >= SuggestRules.SHAPE_MIN && key("shape", g.map { it.id }) !in quiet && !(writing && looksLikeWriting(g, cw, ch))) {
                    runCatching { shapeEngine.suggest(g, all, ::isWritingInk) }.getOrNull()
                } else null
            } else null
            // Çizgi: son çizgiyle birbirine yakın 2+ çizgi (daha önce düzeltilen ya da kapatılanlar hariç); v39 ya da
            // çizim miktarı fazla tek çizgi
            val ln = if (wantLine && last.id !in lineDone) {
                val g = lineEngine.group(pool.filter { it.id == last.id || (isWritingInk(it) && it.id !in lineDone) }, last)
                if (g.isNotEmpty() && key("line", g.map { it.id }) !in quiet && !(writing && looksLikeWriting(g, cw, ch))) {
                    runCatching { lineEngine.suggest(g, cw, ch) }.getOrNull()
                } else null
            } else null
            Found(t, sh, ln)
        }
        // Tanıma sürerken çizim değiştiyse öneri gösterilmez
        if (store.canvas.value?.strokes !== current.strokes) return
        autoKey = null
        _text.value = found.text
        _shape.value = found.shape
        _line.value = found.line
    }

    /** Grup yazı gibi mi duruyor (hepsi yazı kalemi ve yazı ağının kapısından geçiyor). */
    private fun looksLikeWriting(group: List<Stroke>, cw: Float, ch: Float): Boolean =
        group.all(textEngine::isWritingPen) && textEngine.looksLikeText(group, cw, ch)

    /**
     * v17 seçili fırça çizimleri için öneri (bağlam menüsündeki "Düzeltme öner"): üç tür ayrı ayrı (yazı: birbirine
     * yakın 2+ yazı izi, şekil: birbirine yakın 4+ eleman, çizgi: seçili 2+ çizgi; v39 yazı ve çizgi çizim miktarı
     * fazla tek izde de). Bulunamazsa false.
     */
    fun suggestForSelection(onResult: (Boolean) -> Unit = {}) {
        val current = store.canvas.value ?: return onResult(false)
        val ids = store.selection
        val ink = current.strokes.filter { it.id in ids && isWritingInk(it) }
        if (!canSuggest(ink)) return onResult(false)
        job?.cancel()
        selectionJob?.cancel()
        job = host.scope.launch {
            val found = withContext(Dispatchers.Default) { runCatching { forSelection(ink, current.strokes, ignoreQuiet = true) }.getOrNull() }
            if (store.canvas.value?.strokes !== current.strokes || store.selection != ids) return@launch
            if (found == null || (found.text == null && found.shape == null && found.line == null)) { onResult(false); return@launch }
            autoKey = null
            _text.value = found.text
            _shape.value = found.shape
            _line.value = found.line
            onResult(true)
        }
    }

    /** Seçimdeki fırça çizgileri için üç tür (her tür kendi kuralıyla; ayarı kapalı türler hesaplanmaz). */
    private fun forSelection(ink: List<Stroke>, all: List<Stroke>, ignoreQuiet: Boolean): Found {
        val quiet = if (ignoreQuiet) emptySet() else quietKeys.toSet()
        val last = ink.last()
        val cw = canvasW(); val ch = canvasH()
        val writers = ink.filter(textEngine::isWritingPen)
        // v39 tek yazı izi seçiliyse kendisi (çizim miktarı fazlaysa öneri alır)
        val textGroup = when {
            writers.size >= SuggestRules.TEXT_MIN -> textEngine.group(writers, writers.last())
            writers.size == 1 && ink.size == 1 -> writers
            else -> emptyList()
        }
        val writing = textGroup.isNotEmpty() && textEngine.looksLikeText(textGroup, cw, ch)
        val t = if (textEnabled() && writing && key("text", textGroup.map { it.id }) !in quiet) textEngine.suggest(textGroup, canvasW = cw, canvasH = ch) else null
        val sh = if (shapeEnabled() && ink.size >= SuggestRules.SHAPE_MIN) {
            // Seçimin birbirine yakın en büyük kümesi
            val g = shapeEngine.group(ink, last) { true }.takeIf { it.size >= SuggestRules.SHAPE_MIN }
                ?: largestCluster(ink, SuggestRules.SHAPE_PAD, SuggestRules.SHAPE_MIN_PAD).take(SuggestRules.SHAPE_MAX)
            if (g.size >= SuggestRules.SHAPE_MIN && key("shape", g.map { it.id }) !in quiet && !(writing && looksLikeWriting(g, cw, ch))) {
                shapeEngine.suggest(g, all, ::isWritingInk)
            } else null
        } else null
        // Çizgi: seçilen bütün çizgiler (kullanıcı kendisi seçti; en çok LINE_MAX; v39 tek büyük çizgi de)
        val lineGroup = ink.takeLast(SuggestRules.LINE_MAX)
        val ln = if (lineEnabled() && lineGroup.isNotEmpty() && key("line", lineGroup.map { it.id }) !in quiet && !(writing && looksLikeWriting(lineGroup, cw, ch))) {
            lineEngine.suggest(lineGroup, cw, ch)
        } else null
        return Found(t, sh, ln)
    }

    private fun largestCluster(strokes: List<Stroke>, pad: Float, minPad: Float): List<Stroke> {
        val boxes = strokes.map { s -> TemplateSuggester.box(TemplateSuggester.flatten(s)) }
        return SuggestRules.clusters(boxes, pad, minPad).firstOrNull()?.map { strokes[it] }.orEmpty()
    }

    /**
     * v27 seçim değişti (tüm modlarda): seçim kısa bir süre aynı kalırsa düğmeye basılmadan öneri gösterilir (v38 üç
     * tür, en az iki fırça izi seçiliyse; v39 ya da çizim miktarı fazla tek iz). Hesap arka planda yapılır; seçim
     * değişirse iptal edilir.
     */
    fun onSelectionChanged(ids: Set<String>) {
        selectionJob?.cancel()
        val key = ids.sorted().joinToString(",")
        // Kendiliğinden gösterilen öneri seçim değişince kalkar
        if (autoKey != null && autoKey != key) {
            clearAll()
            autoKey = null
        }
        if (ids.isEmpty() || !selectionEnabled() || key in quietKeys) return
        selectionJob = host.scope.launch {
            delay(SELECTION_PAUSE_MS)
            runCatching { autoSuggest(ids, key) }
        }
    }

    private suspend fun autoSuggest(ids: Set<String>, key: String) {
        val current = store.canvas.value ?: return
        val ink = current.strokes.filter { it.id in ids && isWritingInk(it) }
        if (!canSuggest(ink) || ink.size > SuggestRules.LINE_MAX) return
        val found = withContext(Dispatchers.Default) { forSelection(ink, current.strokes, ignoreQuiet = false) }
        // Hesap sürerken çizim ya da seçim değiştiyse gösterilmez
        if (store.canvas.value?.strokes !== current.strokes || store.selection != ids) return
        if (found.text == null && found.shape == null && found.line == null) return
        // Kalemle çizilenin önerisi açıksa ona dokunulmaz
        if (autoKey == null && (_text.value != null || _shape.value != null || _line.value != null)) return
        autoKey = key
        _text.value = found.text
        _shape.value = found.shape
        _line.value = found.line
    }

    private fun quiet(key: String) {
        quietKeys += key
        while (quietKeys.size > 64) quietKeys.remove(quietKeys.first())
    }

    private fun settle(set: LinkedHashSet<String>, ids: Collection<String>) {
        set += ids
        while (set.size > 2000) set.remove(set.first())
    }

    /**
     * Bir öneri uygulandı: o tür kalkar; diğer türlerin önerileri çizgileri hâlâ tuvaldeyse kalır (türler bağımsız
     * uygulanabilir), değilse kalkar. Seçimden gelen önerinin seçimi bir daha önerilmez.
     */
    private fun afterAccept() {
        val ids = store.canvas.value?.strokes?.mapTo(HashSet()) { it.id } ?: emptySet()
        _text.value = _text.value?.takeIf { s -> s.strokeIds.all(ids::contains) }
        _shape.value = _shape.value?.takeIf { s -> s.strokeIds.all(ids::contains) }
        _line.value = _line.value?.takeIf { s -> s.strokeIds.all(ids::contains) }
        autoKey?.let(::quiet)
        if (_text.value == null && _shape.value == null && _line.value == null) autoKey = null
    }

    /** v38 çizgi önerisi kabul edildi: dikkate alınan bütün çizgiler düzgün haline (tek geri alma adımı). */
    fun acceptLines() {
        val s = _line.value ?: return
        _line.value = null
        val current = store.canvas.value ?: return
        val byId = s.fixes.associateBy { it.strokeId }
        if (current.strokes.none { it.id in byId }) return
        store.commit(current.strokes.map { st -> byId[st.id]?.let { st.copy(points = it.points) } ?: st })
        settle(lineSettled, s.strokeIds)
        afterAccept()
    }

    /** Yazı önerisi kabul edildi: dikkate alınan bütün izler tek yazıya dönüşür (tek geri alma adımı). */
    fun accept(text: String) {
        val s = _text.value ?: return
        if (text.isBlank()) return
        _text.value = null
        val current = store.canvas.value ?: return
        val ink = current.strokes.filter { it.id in s.strokeIds }
        if (ink.isEmpty()) return
        val color = ink.groupingBy { it.colorHex }.eachCount().maxByOrNull { it.value }?.key ?: "#1E1E1E"
        // Yazı boyu: izlerin yüksekliği büyük harf yüksekliği sayılır
        val size = (s.bounds.height / 0.72f).coerceIn(10f, 400f)
        val spec = TextSpec(text, font = TextFont.SANS)
        val q = ContentCodec::quantize
        val points = TextRenderer.quadAt(spec, size, s.bounds.left, s.bounds.top - size * 0.12f).map { StrokePoint(q(it.x), q(it.y)) }
        val first = current.strokes.indexOfFirst { it.id in s.strokeIds }
        val text0 = Stroke(
            id = UUID.randomUUID().toString(), points = points, colorHex = color, brushType = BrushType.TEXT, sizePx = size,
            opacity = 1f, order = first, text = spec, layerId = ink.first().layerId ?: "",
        )
        val updated = ArrayList<Stroke>(current.strokes.size)
        current.strokes.forEach { st ->
            if (st.id in s.strokeIds) { if (st.id == ink.first().id) updated += text0 } else updated += st
        }
        store.commit(updated.mapIndexed { i, st -> if (st.order == i) st else st.copy(order = i) })
        afterAccept()
    }

    /**
     * v21 şablon önerisi kabul edildi: dikkate alınan bütün izler şablonun çizgileriyle (aynı fırça, renk, kalınlık ve
     * 3B ayarlarıyla; çok parçalıysa gruplanmış) ya da gölgeli cisim/vektör şekliyle değiştirilir; tek geri alma adımı.
     * Kullanıcının çizimi o şablonun örneği olarak öğrenilir.
     */
    fun acceptTemplate(position: Int) {
        val fix = _shape.value?.templates?.getOrNull(position) ?: return
        _shape.value = null
        val current = store.canvas.value ?: return
        val group = current.strokes.filter { it.id in fix.strokeIds }
        if (group.isEmpty()) return
        val base = group.maxByOrNull { s -> ElementOps.bounds(s)?.let { it.width + it.height } ?: 0f } ?: return
        val color = group.groupingBy { it.colorHex }.eachCount().maxByOrNull { it.value }?.key ?: base.colorHex
        val firstId = current.strokes.first { it.id in fix.strokeIds }.id
        val replacement: List<Stroke> = if (fix.solid != null && !fix.vector && group.any { it.space != null }) {
            // v22: 3B sahnedeki çizim sahnenin gerçek 3B cismiyle değiştirilir
            val width = (ElementOps.halfWidth(base) * 2f).coerceIn(1f, 14f)
            val cam = com.karapp.drawguidance.ui.drawing.Scene3D.camera(current.guide, current.width, current.height)
            listOfNotNull(
                com.karapp.drawguidance.ui.drawing.Scene3D.solidStroke(
                    fix.solid, androidx.compose.ui.geometry.Offset(fix.bounds.left, fix.bounds.top),
                    androidx.compose.ui.geometry.Offset(fix.bounds.right, fix.bounds.bottom),
                    com.karapp.drawguidance.data.model.ShapeStyle(strokeColorHex = color, strokeWidth = width), cam,
                    UUID.randomUUID().toString(), 0, base.opacity,
                )?.copy(layerId = base.layerId),
            )
        } else if (fix.solid != null) {
            val width = (ElementOps.halfWidth(base) * 2f).coerceIn(1f, 14f)
            listOfNotNull(
                com.karapp.drawguidance.ui.drawing.ShapeFactory.build(
                    kind = fix.solid,
                    start = androidx.compose.ui.geometry.Offset(fix.bounds.left, fix.bounds.top),
                    end = androidx.compose.ui.geometry.Offset(fix.bounds.right, fix.bounds.bottom),
                    style = com.karapp.drawguidance.data.model.ShapeStyle(strokeColorHex = color, strokeWidth = width),
                    id = UUID.randomUUID().toString(),
                    order = 0,
                    opacity = base.opacity,
                )?.copy(layerId = base.layerId),
            )
        } else {
            val groupId = if (fix.strokes.size > 1) UUID.randomUUID().toString() else base.groupId
            fix.strokes.mapIndexedNotNull { i, pts ->
                if (pts.size < 2) null
                else base.copy(
                    id = UUID.randomUUID().toString(), points = pts, colorHex = color, order = 0, groupId = groupId, clip = null,
                    space = fix.spaces.getOrNull(i),
                )
            }
        }
        if (replacement.isEmpty()) return
        val updated = ArrayList<Stroke>(current.strokes.size + replacement.size)
        current.strokes.forEach { st ->
            if (st.id in fix.strokeIds) { if (st.id == firstId) updated += replacement } else updated += st
        }
        store.commit(updated.mapIndexed { i, st -> if (st.order == i) st else st.copy(order = i) })
        // Şablonun düzgün çizgileri yeniden dikkate alınmaz
        settle(settled, replacement.map { it.id })
        if (!fix.vector) templates?.accepted(fix.key, fix.drawn)
        afterAccept()
    }

    /** Yazı önerisi kapatıldı: aynı çizgiler için yeniden önerilmez. */
    fun dismissText() {
        _text.value?.let { quiet(key("text", it.strokeIds)) }
        _text.value = null
        afterDismiss()
    }

    /** Şekil önerisi kapatıldı: v21 kapatılan şablonlar öğrenilir (bir dahaki sefere biraz geride kalır). */
    fun dismissShape() {
        _shape.value?.let { s ->
            quiet(key("shape", s.strokeIds))
            val keys = s.templates.map { it.key }.filterNot { it.startsWith(ShapeSuggester.VECTOR_PREFIX) }.distinct()
            // v29 gösterildiği çizimle: modun ağı bu çizimde bu şablonları daha az olası bulur
            templates?.rejected(keys, s.templates.firstOrNull { it.drawn.isNotEmpty() }?.drawn.orEmpty())
        }
        _shape.value = null
        afterDismiss()
    }

    /** v38 çizgi önerisi kapatıldı: dikkate alınan çizgiler bir daha çizgi önerisine girmez. */
    fun dismissLine() {
        _line.value?.let { s -> quiet(key("line", s.strokeIds)); settle(lineSettled, s.strokeIds) }
        _line.value = null
        afterDismiss()
    }

    private fun afterDismiss() {
        if (_text.value == null && _shape.value == null && _line.value == null) {
            autoKey?.let(::quiet)
            autoKey = null
        }
    }

    fun dismiss() {
        dismissShape()
        dismissText()
        dismissLine()
    }

    private fun clearAll() {
        _text.value = null
        _shape.value = null
        _line.value = null
    }

    /** Başka bir işlem yapıldı (mod, sayfa, geri alma): öneriler kalkar. */
    fun reset() {
        job?.cancel()
        selectionJob?.cancel()
        clearAll()
        autoKey = null
    }

    companion object {
        const val PAUSE_MS = 750L
        /** v27 seçim bu kadar aynı kalınca kendiliğinden öneri. */
        const val SELECTION_PAUSE_MS = 450L
    }
}
