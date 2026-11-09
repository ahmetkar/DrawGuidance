import Foundation

/// Düzenleyicinin tuval deposundan kullandığı işlemler (Android `CanvasRepository`'nin düzenleyiciye bakan yüzü).
@MainActor
protocol CanvasPersistence: AnyObject {
    func getCanvas(_ id: String) -> CanvasUi?
    func saveGuide(_ id: String, _ guide: GuideSpec)
    func saveBackground(_ id: String, _ hex: String)
    func saveBackgroundSpec(_ id: String, _ spec: BackgroundSpec, _ pages: [PageBackground?])
    func savePageSettings(_ id: String, _ settings: PageSettings)
    func setAiEnabled(_ id: String, _ enabled: Bool)
    func rename(_ id: String, _ name: String)
}

/// Sayfalar: ekle, çoğalt, sil, sayfaya git; v45 çizgi roman kapağı (Android `PageController`).
@MainActor
final class PageController {
    private let store: EditorStore
    private let beforeChange: () -> Void
    private let onLimit: (Int) -> Void

    init(_ store: EditorStore, beforeChange: @escaping () -> Void, onLimit: @escaping (Int) -> Void = { _ in }) {
        self.store = store; self.beforeChange = beforeChange; self.onLimit = onLimit
    }

    private func atLimit() -> Bool {
        guard let current = store.canvas else { return true }
        let max = current.kind.maxPages
        if current.pages.count < max { return false }
        onLimit(max)
        return true
    }

    func addPage() {
        if atLimit() { return }
        beforeChange()
        guard let current = store.canvas else { return }
        let at = (current.currentPage + 1).coerceIn(0, current.pages.count)
        var pages = current.pages
        pages.insert([], at: at)
        let bg = current.pageBackgrounds.indices.contains(current.currentPage) ? current.pageBackgrounds[current.currentPage]?.withoutCover() : nil
        store.commit(pages: pages, width: current.width, height: current.height, background: current.backgroundColorHex, page: at,
                     pageBackgrounds: insertBackground(current, at, bg))
    }

    func duplicatePage(_ index: Int) {
        if atLimit() { return }
        beforeChange()
        guard let current = store.canvas, current.pages.indices.contains(index) else { return }
        let copy = current.pages[index].map { s -> Stroke in var c = s; c.id = UUID().uuidString.lowercased(); return c }
        var pages = current.pages
        pages.insert(copy, at: index + 1)
        let bg = current.pageBackgrounds.indices.contains(index) ? current.pageBackgrounds[index]?.withoutCover() : nil
        store.commit(pages: pages, width: current.width, height: current.height, background: current.backgroundColorHex, page: index + 1,
                     pageBackgrounds: insertBackground(current, index + 1, bg))
    }

    /// v45 çizgi roman kapağı: yeni bir ilk sayfa (kapak) eklenir ve açılır.
    @discardableResult
    func addCover(_ cover: [Stroke]) -> Bool {
        guard let current0 = store.canvas, !current0.hasCover, !atLimit() else { return false }
        beforeChange()
        guard let current = store.canvas else { return false }
        var pages = current.pages
        pages.insert(ElementOps.reorder(cover), at: 0)
        var backgrounds = current.pageBackgrounds
        while backgrounds.count < current.pages.count { backgrounds.append(nil) }
        backgrounds.insert(PageBackground(cover: true), at: 0)
        store.commit(pages: pages, width: current.width, height: current.height, background: current.backgroundColorHex, page: 0, pageBackgrounds: backgrounds)
        return true
    }

    func deletePage(_ index: Int) {
        guard let current0 = store.canvas, current0.pages.count > 1, current0.pages.indices.contains(index) else { return }
        beforeChange()
        guard let current = store.canvas else { return }
        var pages = current.pages
        pages.remove(at: index)
        var backgrounds = current.pageBackgrounds
        if index < backgrounds.count { backgrounds.remove(at: index) }
        store.commit(pages: pages, width: current.width, height: current.height, background: current.backgroundColorHex,
                     page: min(index, pages.count - 1), pageBackgrounds: backgrounds)
    }

    /// v40 sayfaları yeniden sıralar (sayfa şeridinde sürükleme).
    func movePage(_ from: Int, _ to: Int) {
        guard let current = store.canvas, current.pages.indices.contains(from), current.pages.indices.contains(to), from != to else { return }
        if current.isCover(from) || current.isCover(to) { return }
        beforeChange()
        var pages = current.pages
        let p = pages.remove(at: from)
        pages.insert(p, at: to)
        var backgrounds = current.pageBackgrounds
        if !backgrounds.isEmpty {
            while backgrounds.count < current.pages.count { backgrounds.append(nil) }
            let b = backgrounds.remove(at: from)
            backgrounds.insert(b, at: to)
        }
        store.commit(pages: pages, width: current.width, height: current.height, background: current.backgroundColorHex, page: to, pageBackgrounds: backgrounds)
    }

    private func insertBackground(_ current: CanvasUi, _ at: Int, _ value: PageBackground?) -> [PageBackground?] {
        if current.pageBackgrounds.isEmpty && value == nil { return current.pageBackgrounds }
        var list = current.pageBackgrounds
        while list.count < current.pages.count { list.append(nil) }
        list.insert(value, at: at.coerceIn(0, list.count))
        return list
    }

    func goToPage(_ index: Int) {
        guard let current = store.canvas, current.pages.indices.contains(index), index != current.currentPage else { return }
        beforeChange()
        store.showPage(index)
    }
}

/// Katmanlar (Android `LayerController`). Her işlem geri alınabilir.
@MainActor
final class LayerController {
    private let store: EditorStore
    init(_ store: EditorStore) { self.store = store }

    var active: String { store.activeLayer }
    private func layers() -> [Layer] { store.canvas?.layers ?? [Layer.Default] }

    func setActive(_ id: String) {
        guard let layer = layers().first(where: { $0.id == id }) else { return }
        store.activeLayer = layer.id
    }

    @discardableResult
    func add() -> String {
        let current = layers()
        let names = Set(current.map(\.name))
        var n = current.count + 1
        while names.contains(I18n.trf("Katman {0}", n)) { n += 1 }
        let layer = Layer(id: UUID().uuidString.lowercased(), name: I18n.trf("Katman {0}", n))
        store.commitLayers(current + [layer])
        store.activeLayer = layer.id
        return layer.id
    }

    func remove(_ id: String) {
        let current = layers()
        guard current.count > 1, let canvas = store.canvas else { return }
        let pages = canvas.pages.map { page in page.filter { Layers.of($0, current) != id } }
        let remaining = current.filter { $0.id != id }
        store.commitLayers(remaining, pages: pages)
        if store.activeLayer == id { store.activeLayer = remaining.last!.id }
        store.select([])
    }

    func rename(_ id: String, _ name: String) {
        let t = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty { return }
        store.commitLayers(layers().map { l in var c = l; if l.id == id { c.name = t }; return c }, coalesce: "layer-name-\(id)")
    }

    func toggleVisible(_ id: String) {
        store.commitLayers(layers().map { l in var c = l; if l.id == id { c.visible.toggle() }; return c })
        store.select(store.selection)
    }

    func toggleLocked(_ id: String) {
        store.commitLayers(layers().map { l in var c = l; if l.id == id { c.locked.toggle() }; return c })
        store.select(store.selection)
    }

    func move(_ id: String, _ direction: Int) {
        var current = layers()
        guard let i = current.firstIndex(where: { $0.id == id }) else { return }
        let j = i + direction
        guard current.indices.contains(j), let canvas = store.canvas else { return }
        let firstBefore = current[0].id
        let layer = current.remove(at: i)
        current.insert(layer, at: j)
        let pages = current[0].id != firstBefore
            ? canvas.pages.map { page in page.map { s in
                var c = s
                if s.layerId == nil || s.layerId == "" { c.layerId = firstBefore }
                return c
            } }
            : canvas.pages
        store.commitLayers(current, pages: pages)
    }

    func moveSelectionTo(_ id: String) {
        let ids = store.selection
        guard !ids.isEmpty, let canvas = store.canvas, let target = layers().first(where: { $0.id == id }),
              !target.locked, target.visible else { return }
        let first = layers()[0].id
        let pages = canvas.pages.enumerated().map { index, page -> [Stroke] in
            if index != canvas.currentPage { return page }
            return page.map { s in
                guard ids.contains(s.id) else { return s }
                var c = s; c.layerId = id == first ? nil : id; return c
            }
        }
        store.commitLayers(layers(), pages: pages)
        store.select(ids)
    }
}

/// Kılavuz ve perspektif çizgileri (Android `GuideController`); geçmişe girmez, hemen kaydedilir.
@MainActor
final class GuideController {
    private let store: EditorStore
    private unowned let host: EditorHost
    private weak var repository: CanvasPersistence?

    init(_ store: EditorStore, _ host: EditorHost, _ repository: CanvasPersistence?) {
        self.store = store; self.host = host; self.repository = repository
    }

    func setGuide(_ spec: GuideSpec, persist: Bool = true) {
        guard let current = store.canvas else { return }
        if current.guide == spec && !persist { return }
        store.updateCanvas { c in var n = c; n.guide = spec; return n }
        if persist { repository?.saveGuide(store.canvasLocalId, spec) }
    }

    func addGuide(_ kind: GuideKind) {
        guard var g = store.canvas?.guide else { return }
        g.kind = kind
        setGuide(g)
        host.startAction(.GUIDE)
    }

    func addPerspective(_ points: Int) {
        guard let current = store.canvas, !current.kind.isNotes else { return }
        var g = current.guide
        g.perspective = points.coerceIn(1, 3)
        setGuide(g)
        host.startAction(.PERSPECTIVE)
    }

    func clearGuides() {
        guard let current = store.canvas else { return }
        if current.guide.kind == .NONE && current.guide.perspective == 0 { return }
        var g = current.guide
        g.kind = .NONE; g.perspective = 0
        setGuide(g)
        if host.mode == .GUIDE || host.mode == .PERSPECTIVE { host.finishAction() }
    }

    func removeGuide() {
        guard var g = store.canvas?.guide else { return }
        g.kind = .NONE
        setGuide(g)
        host.finishAction()
    }

    func removePerspective() {
        guard var g = store.canvas?.guide else { return }
        g.perspective = 0
        setGuide(g)
        host.finishAction()
    }

    func moveVanishingPoint(_ index: Int, _ fx: Float, _ fy: Float, persist: Bool) {
        guard var g = store.canvas?.guide else { return }
        let x = fx.coerceIn(-0.5, 1.5), y = fy.coerceIn(-0.5, 1.5)
        switch index {
        case 0: g.vanishA = x; g.horizon = y.coerceIn(0, 1)
        case 1: g.vanishB = x; g.horizon = y.coerceIn(0, 1)
        default: g.vanishC = y
        }
        setGuide(g, persist: persist)
    }
}
