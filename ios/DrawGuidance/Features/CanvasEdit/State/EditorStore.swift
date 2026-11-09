import Combine
import Foundation

/// Denetleyicilerin çizim ekranıyla konuştuğu arayüz (Android `EditorHost`). Görünüm modeli bunu uygular.
@MainActor
protocol EditorHost: AnyObject {
    var mode: EditMode { get }
    func setMode(_ mode: EditMode)
    func startAction(_ mode: EditMode, target: Set<String>)
    func continueAction(_ mode: EditMode)
    func finishAction()
    func endAction()
    func inserted(_ ids: Set<String>)
    func onInk(_ stroke: Stroke)
    func emit(_ event: EditEvent)
}

extension EditorHost {
    func startAction(_ mode: EditMode) { startAction(mode, target: []) }
    func endAction() { setMode(.SELECT) }
    func onInk(_ stroke: Stroke) {}
}

/// Çizim ekranının tek doğruluk kaynağı (Android `EditorStore`): tuval (sayfalar), seçim, eleman dizini ve geri al /
/// yinele geçmişi. Tüm değişiklikler [commit] ile yapılır: geçmişe kaydedilir, kayıt kuyruğuna verilir, eleman dizini
/// arka planda güncellenir.
@MainActor
final class EditorStore: ObservableObject {
    static let MAX_HISTORY = 100
    static let COALESCE_WINDOW_MS: Int64 = 2_500

    @Published var canvas: CanvasUi?
    /// Çizimlerden türetilen elemanlar.
    @Published private(set) var elements = ElementIndex.empty
    /// Seçili çizim kimlikleri — her zaman elemanlarının ve gruplarının tüm üyelerini içerir.
    @Published var selectedIds: Set<String> = []
    /// Görüntüyü değiştiren her değişiklikte artar.
    @Published private(set) var visualVersion: Int64 = 0
    @Published private(set) var canUndo = false
    @Published private(set) var canRedo = false
    /// Yeni elemanların eklendiği katman.
    @Published var activeLayer = Layer.BASE
    /// Kaydedilmemiş değişiklik var mı (otomatik kayıt kapalıyken).
    @Published private(set) var dirty = false

    let canvasLocalId: String
    private let saveQueue: CanvasSaveQueue?
    private let clock: () -> Int64
    let indexer = ElementIndexer()
    private var indexGeneration = 0
    private var history = EditHistory<DocumentState>(maxSize: EditorStore.MAX_HISTORY, coalesceWindowMs: EditorStore.COALESCE_WINDOW_MS)

    /// Otomatik kayıt.
    var autosave = true {
        didSet { if autosave && dirty { saveNow() } }
    }

    /// v24 sayfaların kendi arka planları değişince (kaydetmek için).
    var onPageBackgrounds: ((CanvasUi) -> Void)?

    init(saveQueue: CanvasSaveQueue?, canvasLocalId: String, clock: @escaping () -> Int64 = { Int64(ProcessInfo.processInfo.systemUptime * 1000) }) {
        self.saveQueue = saveQueue
        self.canvasLocalId = canvasLocalId
        self.clock = clock
    }

    // MARK: Kum havuzu (v32 eğitim)

    private struct Sandbox { let canvas: CanvasUi; let history: EditHistory<DocumentState>; let selection: Set<String>; let dirty: Bool }
    private var sandbox: Sandbox?
    var inSandbox: Bool { sandbox != nil }

    func beginSandbox() {
        guard let current = canvas, sandbox == nil else { return }
        sandbox = Sandbox(canvas: current, history: history, selection: selectedIds, dirty: dirty)
        history = EditHistory(maxSize: Self.MAX_HISTORY, coalesceWindowMs: Self.COALESCE_WINDOW_MS)
        selectedIds = []
        var c = current
        c.pages = [[]]
        c.currentPage = 0
        c.pageBackgrounds = Array(current.pageBackgrounds.prefix(1)).map { $0?.withoutCover() }
        canvas = c
        visualVersion += 1
        requestIndex()
        refreshHistoryFlags()
    }

    func sandboxState() -> CanvasUi? { sandbox != nil ? canvas : nil }

    func restoreSandboxState(_ state: CanvasUi) {
        if sandbox == nil { return }
        selectedIds = []
        canvas = state
        visualVersion += 1
        requestIndex()
    }

    func endSandbox() {
        guard let saved = sandbox else { return }
        sandbox = nil
        history = saved.history
        canvas = saved.canvas
        let ids = Set(saved.canvas.strokes.map(\.id))
        selectedIds = saved.selection.filter(ids.contains)
        dirty = saved.dirty
        visualVersion += 1
        requestIndex()
        refreshHistoryFlags()
    }

    // MARK: Okuma

    var strokes: [Stroke] { canvas?.strokes ?? [] }
    var selection: Set<String> { selectedIds }

    func selectedStroke(_ predicate: (Stroke) -> Bool) -> Stroke? {
        let ids = selection
        if ids.isEmpty { return nil }
        return strokes.first { ids.contains($0.id) && predicate($0) }
    }

    var layers: [Layer] { canvas?.layers ?? [Layer.Default] }

    func lockedIds() -> Set<String> {
        guard let c = canvas else { return [] }
        return Layers.lockedIds(c.strokes, c.layers)
    }

    func visibleStrokes() -> [Stroke] {
        guard let c = canvas else { return [] }
        return Layers.visible(c.strokes, c.layers)
    }

    /// Seçim; gizli ya da kilitli katmandaki elemanlar seçilemez.
    func select(_ ids: Set<String>) {
        let locked = ids.isEmpty ? [] : lockedIds()
        selectedIds = locked.isEmpty ? ids : ids.subtracting(locked)
    }

    func selectionKey() -> String { selectedIds.sorted().joined(separator: ",") }

    // MARK: Yükleme ve değişiklik

    func load(_ loaded: CanvasUi?) {
        canvas = loaded.map { c in
            var c2 = c
            if let pages = Scene3D.syncPages(c.pages, c.guide, c.width, c.height) { c2.pages = pages }
            return c2
        }
        history.clear()
        dirty = false
        if let l = loaded?.layers.last(where: { $0.visible && !$0.locked }) { activeLayer = l.id }
        else { activeLayer = loaded?.layers.last?.id ?? Layer.BASE }
        refreshHistoryFlags()
    }

    /// Açık sayfanın çizimlerini değiştirir (geri alınabilir).
    func commit(_ updated: [Stroke], coalesce: String? = nil) {
        guard let current = canvas else { return }
        let placed = placeNewInActiveLayer(current.strokes, updated, current.layers)
        var pages = current.pages
        if pages.indices.contains(current.currentPage) { pages[current.currentPage] = placed } else { pages.append(placed) }
        commit(pages: pages, width: current.width, height: current.height, background: current.backgroundColorHex, coalesce: coalesce)
    }

    private func placeNewInActiveLayer(_ before: [Stroke], _ after: [Stroke], _ layers: [Layer]) -> [Stroke] {
        if layers.count <= 1 { return after }
        guard layers.contains(where: { $0.id == activeLayer }) else { return Layers.sort(after, layers) }
        let active = activeLayer
        let known = Set(before.map(\.id))
        let first = layers[0].id
        let assigned = after.map { s -> Stroke in
            var c = s
            if s.layerId == first || s.layerId == "" { c.layerId = nil }
            else if s.layerId == nil && !known.contains(s.id) && active != first { c.layerId = active }
            return c
        }
        return Layers.sort(assigned, layers)
    }

    /// Katmanları değiştirir; geri alınabilir.
    func commitLayers(_ layers: [Layer], pages: [[Stroke]]? = nil, coalesce: String? = nil) {
        guard let current = canvas else { return }
        let normalized = Layers.normalize(layers)
        let first = normalized[0].id
        let sortedPages = (pages ?? current.pages).map { page in
            Layers.sort(page.map { s in
                var c = s
                if s.layerId == first || s.layerId == "" { c.layerId = nil }
                return c
            }, normalized)
        }
        let before = stateOf(current)
        apply(current, DocumentState(pages: sortedPages, width: current.width, height: current.height, background: current.backgroundColorHex,
                                     page: current.currentPage, layers: normalized, pageBackgrounds: current.pageBackgrounds))
        guard let now = canvas else { return }
        history.record(before: before, after: stateOf(now), key: coalesce, now: clock())
        refreshHistoryFlags()
        if !normalized.contains(where: { $0.id == activeLayer }) { activeLayer = normalized.last!.id }
    }

    /// Belgeyi değiştirir (sayfalar, boyut, arka plan); [page] verilirse o sayfa açılır.
    func commit(pages: [[Stroke]], width: Int, height: Int, background: String, coalesce: String? = nil, page: Int? = nil,
                pageBackgrounds: [PageBackground?]? = nil, shared: BackgroundSpec? = nil) {
        guard let current = canvas else { return }
        func withShared(_ st: DocumentState, _ c: CanvasUi) -> DocumentState {
            guard shared != nil else { return st }
            var s = st; s.sharedBackground = c.background; s.sharedBackgroundFull = true; return s
        }
        let before = withShared(stateOf(current), current)
        apply(current, DocumentState(pages: pages, width: width, height: height, background: background, page: page ?? current.currentPage,
                                     layers: current.layers, pageBackgrounds: pageBackgrounds ?? current.pageBackgrounds,
                                     sharedBackground: shared, sharedBackgroundFull: shared != nil))
        guard let now = canvas else { return }
        history.record(before: before, after: withShared(stateOf(now), now), key: coalesce, now: clock())
        refreshHistoryFlags()
    }

    /// v40 ortak arka plan resminin yeri ve boyu (geri alınabilir).
    func commitSharedBackground(_ spec: BackgroundSpec, coalesce: String? = nil) {
        guard let current = canvas, current.background != spec else { return }
        let full = spec.withImageTransformOf(current.background) != current.background
        var before = stateOf(current); before.sharedBackground = current.background; before.sharedBackgroundFull = full
        var c = current; c.background = spec
        canvas = c
        visualVersion += 1
        if sandbox == nil { onPageBackgrounds?(c) }
        var after = stateOf(c); after.sharedBackground = spec; after.sharedBackgroundFull = full
        history.record(before: before, after: after, key: coalesce, now: clock())
        refreshHistoryFlags()
    }

    // MARK: v43 geçici önizleme

    private var previewBase: CanvasUi?
    var inPreview: Bool { previewBase != nil }

    func previewStrokes(_ strokes: [Stroke]) {
        guard let current = canvas else { return }
        let base = previewBase ?? current
        previewBase = base
        var pages = base.pages
        guard pages.indices.contains(base.currentPage) else { return }
        pages[base.currentPage] = strokes
        var c = base; c.pages = pages
        canvas = c
        visualVersion += 1
        requestIndex()
    }

    @discardableResult
    func endPreview() -> CanvasUi? {
        guard let base = previewBase else { return nil }
        previewBase = nil
        canvas = base
        visualVersion += 1
        requestIndex()
        return base
    }

    /// Görüntüyü değiştirmeyen, geçmişe girmeyen veri güncellemesi.
    func applySilently(_ strokes: [Stroke]) {
        guard let current = canvas else { return }
        var pages = current.pages
        if pages.indices.contains(current.currentPage) { pages[current.currentPage] = strokes }
        apply(current, DocumentState(pages: pages, width: current.width, height: current.height, background: current.backgroundColorHex,
                                     page: current.currentPage, layers: current.layers, pageBackgrounds: current.pageBackgrounds), visual: false)
    }

    @discardableResult
    func undo() -> Bool {
        guard let current = canvas, let state = history.undo() else { return false }
        apply(current, state)
        refreshHistoryFlags()
        return true
    }

    @discardableResult
    func redo() -> Bool {
        guard let current = canvas, let state = history.redo() else { return false }
        apply(current, state)
        refreshHistoryFlags()
        return true
    }

    func endCoalesce() { history.breakCoalescing() }

    /// Açık sayfayı değiştirir (geçmişe girmez).
    func showPage(_ index: Int) {
        guard let current = canvas, current.pages.indices.contains(index), index != current.currentPage else { return }
        selectedIds = []
        var c = current; c.currentPage = index
        canvas = c
        visualVersion += 1
        requestIndex()
        if autosave && sandbox == nil {
            saveQueue?.enqueue(canvasLocalId, current.pages, current.width, current.height, false, nil, index, current.layers)
        }
    }

    func saveNow() {
        if sandbox != nil { return }
        guard let c = canvas else { return }
        saveQueue?.enqueue(canvasLocalId, c.pages, c.width, c.height, true, c.backgroundColorHex, c.currentPage, c.layers)
        saveQueue?.flushNow()
        dirty = false
    }

    /// Kılavuz gibi geçmişe girmeyen görünüm ayarları.
    func updateCanvas(_ block: (CanvasUi) -> CanvasUi) {
        guard let current = canvas else { return }
        canvas = block(current)
        visualVersion += 1
    }

    /// O anki çizimlerin eleman dizini (arka plan hesabı bitmediyse hemen hesaplanır).
    func currentIndex() -> ElementIndex {
        guard let s = canvas?.strokes else { return .empty }
        let latest = elements
        return latest.strokes == s ? latest : indexer.update(s)
    }

    /// Seçili çizimlere [block] uygular; bir şey değiştiyse kaydeder.
    func mutateSelection(_ block: ([Stroke], Set<String>) -> [Stroke]) {
        guard let current = canvas else { return }
        let ids = selectedIds
        if ids.isEmpty { return }
        let updated = block(current.strokes, ids)
        if updated != current.strokes { commit(updated) }
    }

    /// Eleman dizinini arka planda yeniden hesaplar (son istek kazanır).
    func requestIndex() {
        guard let s = canvas?.strokes else { return }
        indexGeneration += 1
        let gen = indexGeneration
        let indexer = self.indexer
        Task { [weak self] in
            let index = await Task.detached(priority: .userInitiated) { indexer.update(s) }.value
            guard let self, gen == self.indexGeneration else { return }
            self.elements = index
            self.selectedIds = index.expand(self.selectedIds)
        }
    }

    /// Testler ve eşzamanlı kullanım: dizini hemen hesaplar.
    func indexNow() {
        indexGeneration += 1
        let idx = indexer.update(strokes)
        elements = idx
        selectedIds = idx.expand(selectedIds)
    }

    private func stateOf(_ c: CanvasUi) -> DocumentState {
        DocumentState(pages: c.pages, width: c.width, height: c.height, background: c.backgroundColorHex, page: c.currentPage,
                      layers: c.layers, pageBackgrounds: c.pageBackgrounds)
    }

    private func refreshHistoryFlags() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    private func synced(_ current: CanvasUi, _ state: DocumentState) -> DocumentState {
        guard let pages = Scene3D.syncPages(state.pages, current.guide, state.width, state.height) else { return state }
        var s = state; s.pages = pages; return s
    }

    /// v22 sahnenin kamerası değişti: 3B elemanlar yeni görünümden çizilir.
    func applyView(persist: Bool) {
        guard let current = canvas else { return }
        let pages = Scene3D.syncPages(current.pages, current.guide, current.width, current.height)
        if let pages {
            var c = current; c.pages = pages
            canvas = c
            visualVersion += 1
            requestIndex()
        }
        if !persist || sandbox != nil { return }
        guard let c = canvas else { return }
        if autosave { saveQueue?.enqueue(canvasLocalId, c.pages, c.width, c.height, false, nil, nil, c.layers) }
        else if pages != nil { dirty = true }
    }

    private func apply(_ current: CanvasUi, _ state0: DocumentState, visual: Bool = true) {
        let state = synced(current, state0)
        let sizeChanged = state.width != current.width || state.height != current.height
        let backgroundChanged = state.background.caseInsensitiveCompare(current.backgroundColorHex) != .orderedSame
        let pageIndex = state.page.coerceIn(0, state.pages.count - 1)
        let pageChanged = pageIndex != current.currentPage
        let layersChanged = state.layers != current.layers
        let pageBackgroundsChanged = state.pageBackgrounds != current.pageBackgrounds
        let shared = state.sharedBackground.map { state.sharedBackgroundFull ? $0 : current.background.withImageTransformOf($0) } ?? current.background
        let sharedChanged = shared != current.background
        var c = current
        c.pages = state.pages; c.width = state.width; c.height = state.height
        c.backgroundColorHex = state.background; c.currentPage = pageIndex; c.layers = state.layers
        c.pageBackgrounds = state.pageBackgrounds; c.background = shared
        canvas = c
        if (pageBackgroundsChanged || sharedChanged) && sandbox == nil { onPageBackgrounds?(c) }
        if visual { visualVersion += 1 }
        if !selectedIds.isEmpty {
            let existing = Set(c.strokes.map(\.id))
            selectedIds = selectedIds.filter(existing.contains)
        }
        if sandbox != nil {
            // v32 eğitim: kaydedilmez
        } else if autosave {
            saveQueue?.enqueue(canvasLocalId, state.pages, state.width, state.height, sizeChanged,
                               backgroundChanged ? state.background : nil, pageChanged ? pageIndex : nil, state.layers)
        } else if visual || layersChanged {
            dirty = true
        }
        requestIndex()
    }
}
