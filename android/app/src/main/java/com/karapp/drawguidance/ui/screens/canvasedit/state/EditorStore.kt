package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.data.history.DocumentState
import com.karapp.drawguidance.data.history.EditHistory
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.Layers
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.repository.CanvasSaveQueue
import com.karapp.drawguidance.ui.screens.canvasedit.EditEvent
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementIndex
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementIndexer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Denetleyicilerin çizim ekranıyla konuştuğu arayüz: mod, olaylar ve iş kapsamı. ViewModel bunu uygular.
 */
interface EditorHost {
    val mode: StateFlow<EditMode>
    /** Modu doğrudan değiştirir (politikasız; temel modlar arası geçiş). */
    fun setMode(mode: EditMode)
    /**
     * Bir eylem başlatır ([EditMode.kind] politikasıyla): eylemden önceki temel mod hatırlanır; ekleme modlarında eski
     * seçim kalkar, düzenleme modlarında yalnız [target] seçili kalır.
     */
    fun startAction(mode: EditMode, target: Set<String> = emptySet())
    /** Aynı eylemin bir sonraki adımı (ör. eklenen şekil seçili olarak Taşı'ya geçer); dönülecek mod değişmez. */
    fun continueAction(mode: EditMode)
    /**
     * Eylemi bitirir (v17): iş bitince seçim kalkar ve Seç moduna geçilir; yalnızca düzenlemenin içinden açılan araç
     * bitince o düzenlemeye dönülür.
     */
    fun finishAction()
    /** Eylem artık sürdürülemez (ör. düzenlenen eleman silindi): Seç moduna geçilir. */
    fun endAction() = setMode(EditMode.SELECT)
    /** Ekleme bitti: eklenen elemanlar seçili olarak Taşı moduna geçilir. */
    fun inserted(ids: Set<String>)
    /** Fırçayla yeni bir iz çizildi (el yazısı önerisi için). */
    fun onInk(stroke: com.karapp.drawguidance.data.model.Stroke) {}
    val scope: CoroutineScope
    fun emit(event: EditEvent)
}

/**
 * Çizim ekranının tek doğruluk kaynağı: tuval (sayfalar), seçim, eleman dizini ve geri al / yinele geçmişi.
 * Tüm değişiklikler [commit] ile yapılır: geçmişe kaydedilir, kayıt kuyruğuna verilir ve eleman dizini
 * arka planda güncellenir. Denetleyiciler (kırpma, diyagram, yazı…) yalnızca bu sınıf üzerinden değişiklik yapar.
 */
class EditorStore(
    private val scope: CoroutineScope,
    private val saveQueue: CanvasSaveQueue,
    val canvasLocalId: String,
    private val clock: () -> Long = { android.os.SystemClock.uptimeMillis() },
) {
    val canvas = MutableStateFlow<CanvasUi?>(null)

    /** Çizimlerden türetilen elemanlar (değen çizimler tek eleman; silgiler görünmez). */
    val elements = MutableStateFlow(ElementIndex.Empty)

    /** Seçili çizim kimlikleri — her zaman elemanlarının ve gruplarının tüm üyelerini içerir. */
    val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Görüntüyü değiştiren her değişiklikte artar. Görüntüyü değiştirmeyen veri değişiklikleri
     * (ör. tamamen silinmiş çizimlerin temizlenmesi) artırmaz; çizim önbelleği yeniden çizilmez.
     */
    val visualVersion = MutableStateFlow(0L)

    val canUndo = MutableStateFlow(false)
    val canRedo = MutableStateFlow(false)

    /** Yeni elemanların eklendiği katman ([Layer.id]). */
    val activeLayer = MutableStateFlow(Layer.BASE)

    /**
     * Otomatik kayıt: açıkken her değişiklik kayıt kuyruğuna verilir; kapalıyken değişiklikler bellekte kalır,
     * [dirty] işaretlenir ve [saveNow] (üst menüdeki Kaydet) ile yazılır.
     */
    var autosave: Boolean = true
        set(value) {
            field = value
            if (value && dirty.value) saveNow()
        }

    /** Kaydedilmemiş değişiklik var mı (otomatik kayıt kapalıyken). */
    val dirty = MutableStateFlow(false)

    // Ağır işler (eleman dizini, silgi temizliği) tek bir arka plan iş parçacığında sırayla
    @OptIn(ExperimentalCoroutinesApi::class)
    val worker = Dispatchers.Default.limitedParallelism(1)
    val indexer = ElementIndexer()
    private val indexRequests = MutableStateFlow<List<Stroke>?>(null)

    private var history = EditHistory<DocumentState>(MAX_HISTORY, COALESCE_WINDOW_MS)

    /**
     * v32 eğitim modu ("kum havuzu"): eğitimin çizimleri kaydedilmez ve kullanıcının geri alma geçmişine girmez; eğitim
     * bitince ya da iptal edilince tuval eğitimden önceki haline döner.
     */
    private var sandbox: Sandbox? = null
    private class Sandbox(val canvas: CanvasUi, val history: EditHistory<DocumentState>, val selection: Set<String>, val dirty: Boolean)
    val inSandbox: Boolean get() = sandbox != null

    /** Eğitim başlar: tuvalin o anki hali saklanır; eğitim boş bir sayfada (aynı kağıt ve katmanlarla) gösterilir. */
    fun beginSandbox() {
        val current = canvas.value ?: return
        if (sandbox != null) return
        sandbox = Sandbox(current, history, selectedIds.value, dirty.value)
        history = EditHistory(MAX_HISTORY, COALESCE_WINDOW_MS)
        selectedIds.value = emptySet()
        // v45 eğitim sayfası çizgi roman kapağı sayılmaz (kapak bayrağı kopyalanmaz)
        canvas.value = current.copy(pages = listOf(emptyList()), currentPage = 0, pageBackgrounds = current.pageBackgrounds.take(1).map { it?.withoutCover() })
        visualVersion.value++
        indexRequests.value = canvas.value?.strokes
        refreshHistoryFlags()
    }

    /** O anki eğitim çiziminin anlık hali (adımı yeniden göstermek için). */
    fun sandboxState(): CanvasUi? = canvas.value.takeIf { sandbox != null }

    /** Eğitim adımı yeniden gösterilirken tuval adımın başındaki haline döner (geçmişe girmez). */
    fun restoreSandboxState(state: CanvasUi) {
        if (sandbox == null) return
        selectedIds.value = emptySet()
        canvas.value = state
        visualVersion.value++
        indexRequests.value = state.strokes
    }

    /** Eğitim bitti: tuval, seçim ve geri alma geçmişi eğitimden önceki haline döner (hiçbir şey kaydedilmez). */
    fun endSandbox() {
        val saved = sandbox ?: return
        sandbox = null
        history = saved.history
        // Görünüm ayarları (kılavuz, 3B kamera) eğitimde değişmez; belge eğitimden önceki haline döner
        canvas.value = saved.canvas
        selectedIds.value = saved.selection.filterTo(HashSet()) { id -> saved.canvas.strokes.any { it.id == id } }
        dirty.value = saved.dirty
        visualVersion.value++
        indexRequests.value = saved.canvas.strokes
        refreshHistoryFlags()
    }

    init {
        scope.launch {
            indexRequests.filterNotNull().conflate().collect { strokes ->
                val index = withContext(worker) { indexer.update(strokes) }
                if (canvas.value?.strokes === strokes) {
                    elements.value = index
                    // Seçim, eleman bütünlüğünü korur (ör. seçili elemana değen yeni çizim)
                    selectedIds.value = index.expand(selectedIds.value)
                }
            }
        }
    }

    val strokes: List<Stroke> get() = canvas.value?.strokes.orEmpty()
    val selection: Set<String> get() = selectedIds.value

    /** Seçimdeki ilk uyan çizim (seçim akışlarını beklemeden, doğrudan tuvalden). */
    fun selectedStroke(predicate: (Stroke) -> Boolean): Stroke? {
        val ids = selection
        if (ids.isEmpty()) return null
        return strokes.firstOrNull { it.id in ids && predicate(it) }
    }
    val layers: List<Layer> get() = canvas.value?.layers ?: listOf(Layer.Default)

    /** Gizli ya da kilitli katmandaki (seçilemeyen, silinemeyen) çizimlerin kimlikleri. */
    fun lockedIds(): Set<String> {
        val c = canvas.value ?: return emptySet()
        return Layers.lockedIds(c.strokes, c.layers)
    }

    /** Görünür katmanlardaki çizimler (çizim, dışa aktarma). */
    fun visibleStrokes(): List<Stroke> {
        val c = canvas.value ?: return emptyList()
        return Layers.visible(c.strokes, c.layers)
    }

    /** Seçim; gizli ya da kilitli katmandaki elemanlar seçilemez. */
    fun select(ids: Set<String>) {
        val locked = if (ids.isEmpty()) emptySet() else lockedIds()
        selectedIds.value = if (locked.isEmpty()) ids else ids - locked
    }

    /** Birleşen geri alma adımlarının anahtarında kullanılan seçim kimliği (farklı elemanlar ayrı adım olur). */
    fun selectionKey(): String = selectedIds.value.sorted().joinToString(",")

    /** Tuval yüklendi: geçmiş boş başlar. */
    fun load(loaded: CanvasUi?) {
        // v22: kayıttaki 3B elemanlar sahnenin kaydedilmiş görünümüyle uyumlu değilse (ör. döndürme yarıda kaldıysa)
        canvas.value = loaded?.let { c ->
            com.karapp.drawguidance.ui.drawing.Scene3D.syncPages(c.pages, c.guide, c.width, c.height)?.let { c.copy(pages = it) } ?: c
        }
        history.clear()
        dirty.value = false
        // Etkin katman: en üstteki görünür ve kilitsiz katman
        loaded?.layers?.lastOrNull { it.visible && !it.locked }?.let { activeLayer.value = it.id }
            ?: run { activeLayer.value = loaded?.layers?.lastOrNull()?.id ?: Layer.BASE }
        refreshHistoryFlags()
    }

    /**
     * Açık sayfanın çizimlerini değiştirir (geri alınabilir). Yeni eklenen ve katmanı belirtilmemiş elemanlar etkin
     * katmana konur; çizimler katman sırasına dizilir (her denetleyici bunu ayrıca düşünmez).
     */
    fun commit(updated: List<Stroke>, coalesce: String? = null) {
        val current = canvas.value ?: return
        val placed = placeNewInActiveLayer(current.strokes, updated, current.layers)
        val pages = current.pages.toMutableList()
        if (current.currentPage in pages.indices) pages[current.currentPage] = placed else pages.add(placed)
        commit(pages, current.width, current.height, current.backgroundColorHex, coalesce)
    }

    private fun placeNewInActiveLayer(before: List<Stroke>, after: List<Stroke>, layers: List<Layer>): List<Stroke> {
        if (layers.size <= 1) return after
        val active = activeLayer.value.takeIf { id -> layers.any { it.id == id } } ?: return Layers.sort(after, layers)
        val known = HashSet<String>(before.size * 2)
        before.forEach { known += it.id }
        val first = layers.first().id
        val assigned = after.map { s ->
            when {
                // İlk katman kimliği kayıtta null ile gösterilir (karşılaştırmalar tutarlı olsun)
                s.layerId == first || s.layerId == "" -> s.copy(layerId = null)
                s.layerId == null && s.id !in known && active != first -> s.copy(layerId = active)
                else -> s
            }
        }
        return Layers.sort(assigned, layers)
    }

    /** Katmanları değiştirir (ekleme, silme, sıra, ad, görünürlük, kilit); geri alınabilir. */
    fun commitLayers(layers: List<Layer>, pages: List<List<Stroke>>? = null, coalesce: String? = null) {
        val current = canvas.value ?: return
        val normalized = Layers.normalize(layers)
        val first = normalized.first().id
        // İlk katmanın elemanları kimliksiz (null) tutulur: karşılaştırmalar ve kayıt tutarlı olsun
        val sortedPages = (pages ?: current.pages).map { page ->
            Layers.sort(page.map { s -> if (s.layerId == first || s.layerId == "") s.copy(layerId = null) else s }, normalized)
        }
        val before = stateOf(current)
        apply(current, DocumentState(sortedPages, current.width, current.height, current.backgroundColorHex, current.currentPage, normalized, current.pageBackgrounds))
        val after = canvas.value?.let { stateOf(it) } ?: return
        history.record(before, after, coalesce, clock())
        refreshHistoryFlags()
        if (normalized.none { it.id == activeLayer.value }) activeLayer.value = normalized.last().id
    }

    /** Belgeyi değiştirir (sayfalar, boyut, arka plan); [page] verilirse o sayfa açılır. Geri alınabilir. */
    fun commit(
        pages: List<List<Stroke>>,
        width: Int,
        height: Int,
        background: String,
        coalesce: String? = null,
        page: Int? = null,
        /** v24 sayfaların kendi arka planları (null: değişmez). */
        pageBackgrounds: List<com.karapp.drawguidance.data.model.PageBackground?>? = null,
        /** v41 ortak arka plan da bu adımda değişir (null: değişmez); geri alınınca bütünüyle döner. */
        shared: com.karapp.drawguidance.data.model.BackgroundSpec? = null,
    ) {
        val current = canvas.value ?: return
        val withShared: (DocumentState, CanvasUi) -> DocumentState = { st, c ->
            if (shared != null) st.copy(sharedBackground = c.background, sharedBackgroundFull = true) else st
        }
        val before = withShared(stateOf(current), current)
        apply(current, DocumentState(
            pages, width, height, background, page ?: current.currentPage, current.layers, pageBackgrounds ?: current.pageBackgrounds,
            sharedBackground = shared, sharedBackgroundFull = shared != null,
        ))
        val after = canvas.value?.let { withShared(stateOf(it), it) } ?: return
        history.record(before, after, coalesce, clock())
        refreshHistoryFlags()
    }

    /**
     * v40 ortak arka plan resminin yeri ve boyu (geri alınabilir; ortak arka planla hemen kaydedilir). [spec] tuvalin
     * yeni ortak arka planı.
     */
    fun commitSharedBackground(spec: com.karapp.drawguidance.data.model.BackgroundSpec, coalesce: String? = null) {
        val current = canvas.value ?: return
        if (current.background == spec) return
        // v41 resim, desen ya da gradyan da değiştiyse adım geri alınınca arka plan bütünüyle döner
        val full = spec.withImageTransformOf(current.background) != current.background
        val before = stateOf(current).copy(sharedBackground = current.background, sharedBackgroundFull = full)
        canvas.value = current.copy(background = spec)
        visualVersion.value++
        if (sandbox == null) canvas.value?.let { c -> onPageBackgrounds?.invoke(c) }
        val after = stateOf(canvas.value ?: return).copy(sharedBackground = spec, sharedBackgroundFull = full)
        history.record(before, after, coalesce, clock())
        refreshHistoryFlags()
    }

    /**
     * v43 geçici önizleme (Arka planla uyumla, Mekâna yerleştir): açık sayfanın çizimleri geçmişe ve kayda girmeden
     * gösterilir. [endPreview] özgün hâle döndürür (sonra istenirse [commit] edilir).
     */
    private var previewBase: CanvasUi? = null
    val inPreview: Boolean get() = previewBase != null

    fun previewStrokes(strokes: List<Stroke>) {
        val current = canvas.value ?: return
        val base = previewBase ?: current.also { previewBase = it }
        val pages = base.pages.toMutableList()
        if (base.currentPage in pages.indices) pages[base.currentPage] = strokes else return
        canvas.value = base.copy(pages = pages)
        visualVersion.value++
        indexRequests.value = strokes
    }

    fun endPreview(): CanvasUi? {
        val base = previewBase ?: return null
        previewBase = null
        canvas.value = base
        visualVersion.value++
        indexRequests.value = base.strokes
        return base
    }

    /** Görüntüyü değiştirmeyen, geri alma geçmişine girmeyen veri güncellemesi (silinmiş çizimlerin temizliği). */
    fun applySilently(strokes: List<Stroke>) {
        val current = canvas.value ?: return
        val pages = current.pages.toMutableList()
        if (current.currentPage in pages.indices) pages[current.currentPage] = strokes
        apply(current, DocumentState(pages, current.width, current.height, current.backgroundColorHex, current.currentPage, current.layers, current.pageBackgrounds), visual = false)
    }

    /** Son değişikliği geri alır (değişikliğin yapıldığı sayfa açılır). */
    fun undo(): Boolean {
        val current = canvas.value ?: return false
        val state = history.undo() ?: return false
        apply(current, state)
        refreshHistoryFlags()
        return true
    }

    /** Geri alınan değişikliği yineler. */
    fun redo(): Boolean {
        val current = canvas.value ?: return false
        val state = history.redo() ?: return false
        apply(current, state)
        refreshHistoryFlags()
        return true
    }

    /** Sürüklemeyle birleşen geri alma adımını bitirir (renk penceresi kapanınca). */
    fun endCoalesce() = history.breakCoalescing()

    /** Açık sayfayı değiştirir (geçmişe girmez; sayfa numarası kayıt kuyruğuyla yazılır). */
    fun showPage(index: Int) {
        val current = canvas.value ?: return
        if (index !in current.pages.indices || index == current.currentPage) return
        selectedIds.value = emptySet()
        canvas.value = current.copy(currentPage = index)
        visualVersion.value++
        indexRequests.value = canvas.value?.strokes
        if (autosave && sandbox == null) saveQueue.enqueue(canvasLocalId, current.pages, current.width, current.height, false, null, index, current.layers)
    }

    /** Belgeyi hemen kaydeder (otomatik kayıt kapalıyken Kaydet düğmesi). */
    fun saveNow() {
        // v32 eğitimde kaydedilmez (eğitimden önceki belge zaten kayıtlı)
        if (sandbox != null) return
        val c = canvas.value ?: return
        saveQueue.enqueue(canvasLocalId, c.pages, c.width, c.height, true, c.backgroundColorHex, c.currentPage, c.layers)
        saveQueue.flushNow()
        dirty.value = false
    }

    /** v24 sayfaların kendi arka planları değişince (kaydetmek için). */
    var onPageBackgrounds: ((CanvasUi) -> Unit)? = null

    /** Kılavuz gibi geçmişe girmeyen görünüm ayarları. */
    fun updateCanvas(block: (CanvasUi) -> CanvasUi) {
        val current = canvas.value ?: return
        canvas.value = block(current)
        visualVersion.value++
    }

    /** O anki çizimlerin eleman dizini (arka plan hesabı henüz bitmediyse hemen hesaplanır). */
    fun currentIndex(): ElementIndex {
        val strokes = canvas.value?.strokes ?: return ElementIndex.Empty
        val latest = elements.value
        return if (latest.strokes === strokes) latest else indexer.update(strokes)
    }

    /** Seçili çizimlere [block] uygular; bir şey değiştiyse kaydeder. */
    fun mutateSelection(block: (List<Stroke>, Set<String>) -> List<Stroke>) {
        val current = canvas.value ?: return
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        val updated = block(current.strokes, ids)
        // Kimlik karşılaştırması: tüm noktaları tek tek karşılaştırmaktan çok daha hızlı
        val changed = updated !== current.strokes && (updated.size != current.strokes.size || updated.indices.any { updated[it] !== current.strokes[it] })
        if (changed) commit(updated)
    }

    /** İlk yüklemeden sonra eleman dizini (arka planda). */
    fun requestIndex() {
        indexRequests.value = canvas.value?.strokes
    }

    private fun stateOf(c: CanvasUi) = DocumentState(c.pages, c.width, c.height, c.backgroundColorHex, c.currentPage, c.layers, c.pageBackgrounds)

    private fun refreshHistoryFlags() {
        canUndo.value = history.canUndo
        canRedo.value = history.canRedo
    }

    /**
     * v22 3B sahne: yeni 3B izler sahnedeki yerini alır, 2B'de düzenlenen 3B elemanların yeri güncellenir, başka bir
     * kamerayla kaydedilmiş (ör. geri alınan) elemanlar o anki görünümden yeniden çizilir.
     */
    private fun synced(current: CanvasUi, state: DocumentState): DocumentState {
        val pages = com.karapp.drawguidance.ui.drawing.Scene3D.syncPages(state.pages, current.guide, state.width, state.height) ?: return state
        return state.copy(pages = pages)
    }

    /**
     * v22 sahnenin kamerası değişti: 3B elemanlar yeni görünümden çizilir (geri alma geçmişine girmez; görünüm ayarı
     * gibi). [persist] false iken (döndürme sürerken) kaydedilmez; son adımda kaydedilir.
     */
    fun applyView(persist: Boolean) {
        val current = canvas.value ?: return
        val pages = com.karapp.drawguidance.ui.drawing.Scene3D.syncPages(current.pages, current.guide, current.width, current.height)
        if (pages != null) {
            canvas.value = current.copy(pages = pages)
            visualVersion.value++
            indexRequests.value = canvas.value?.strokes
        }
        if (!persist || sandbox != null) return
        val c = canvas.value ?: return
        if (autosave) saveQueue.enqueue(canvasLocalId, c.pages, c.width, c.height, false, null, null, c.layers)
        else if (pages != null) dirty.value = true
    }

    private fun apply(current: CanvasUi, state0: DocumentState, visual: Boolean = true) {
        val state = synced(current, state0)
        val sizeChanged = state.width != current.width || state.height != current.height
        val backgroundChanged = !state.background.equals(current.backgroundColorHex, ignoreCase = true)
        val pageIndex = state.page.coerceIn(0, state.pages.size - 1)
        val pageChanged = pageIndex != current.currentPage
        val layersChanged = state.layers != current.layers
        val pageBackgroundsChanged = state.pageBackgrounds != current.pageBackgrounds
        // v40 arka plan resminin yeri ve boyu (geri al / yinele); resim ve desen o anki haliyle kalır
        val shared = state.sharedBackground?.let { if (state.sharedBackgroundFull) it else current.background.withImageTransformOf(it) }
            ?: current.background
        val sharedChanged = shared != current.background
        canvas.value = current.copy(
            pages = state.pages, width = state.width, height = state.height,
            backgroundColorHex = state.background, currentPage = pageIndex, layers = state.layers,
            pageBackgrounds = state.pageBackgrounds, background = shared,
        )
        // v24 sayfa arka planları ayrı kolonda (ortak arka planla) hemen yazılır
        if ((pageBackgroundsChanged || sharedChanged) && sandbox == null) canvas.value?.let { c -> onPageBackgrounds?.invoke(c) }
        if (visual) visualVersion.value++
        if (selectedIds.value.isNotEmpty()) {
            val existing = (canvas.value?.strokes ?: emptyList()).mapTo(HashSet()) { it.id }
            selectedIds.value = selectedIds.value.filterTo(HashSet()) { it in existing }
        }
        if (sandbox != null) {
            // v32 eğitim: kaydedilmez, kaydedilmemiş değişiklik de sayılmaz
        } else if (autosave) {
            saveQueue.enqueue(
                canvasLocalId, state.pages, state.width, state.height, sizeChanged,
                state.background.takeIf { backgroundChanged }, pageIndex.takeIf { pageChanged }, state.layers,
            )
        } else if (visual || layersChanged) {
            dirty.value = true
        }
        indexRequests.value = canvas.value?.strokes
    }

    companion object {
        private const val MAX_HISTORY = 100
        /** Birleşen geri alma adımında iki değişiklik arasındaki en uzun süre (kaydırıcı/renk sürükleme, yazma). */
        private const val COALESCE_WINDOW_MS = 2_500L
    }
}
