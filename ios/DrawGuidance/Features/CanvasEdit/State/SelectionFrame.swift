import Combine
import Foundation

/// Seçim ve seçili elemanlar üzerindeki işlemler (Android `SelectionController`).
@MainActor
final class SelectionController {
    private let store: EditorStore
    private unowned let host: EditorHost

    init(_ store: EditorStore, _ host: EditorHost) { self.store = store; self.host = host }

    @discardableResult
    func pickAt(_ x: Float, _ y: Float, tolerance: Float) -> Bool {
        let index = store.currentIndex()
        guard let element = ElementOps.hitTest(index, P(x, y), tolerance: tolerance, skip: store.lockedIds()) else { return false }
        store.select(index.expand(element.allIds))
        return true
    }

    func selectOnlyAt(_ x: Float, _ y: Float, tolerance: Float) -> Bool {
        if !pickAt(x, y, tolerance: tolerance) { return false }
        host.setMode(.SELECT)
        return true
    }

    func holdMoveAt(_ x: Float, _ y: Float, tolerance: Float) -> Bool {
        if !pickAt(x, y, tolerance: tolerance) { return false }
        if host.mode != .MOVE { host.startAction(.MOVE) }
        return true
    }

    func toggleElement(_ elementId: String) {
        let index = store.currentIndex()
        guard let element = index.element(elementId) else { return }
        let targets = index.expand(element.allIds)
        let selected = store.selection
        store.select(targets.isSubset(of: selected) ? selected.subtracting(targets) : index.expand(selected.union(targets)))
    }

    func toggleGroup(_ groupId: String) {
        guard let first = store.currentIndex().elements.first(where: { $0.groupId == groupId }) else { return }
        toggleElement(first.id)
    }

    /// v43 alanla seçim; seçilen eleman sayısı.
    @discardableResult
    func selectInArea(add: Bool, _ contains: (P) -> Bool) -> Int {
        let index = store.currentIndex()
        let locked = store.lockedIds()
        var found = Set<String>()
        var n = 0
        for e in index.elements {
            if e.hidden || e.strokeIds.contains(where: locked.contains) { continue }
            guard let b = ElementOps.selectionBounds(index.strokes, Set(e.strokeIds)) else { continue }
            if contains(b.center) { found.formUnion(e.allIds); n += 1 }
        }
        store.select(index.expand(add ? store.selection.union(found) : found))
        return n
    }

    @discardableResult
    func selectAt(_ x: Float, _ y: Float, tolerance: Float) -> Bool {
        let index = store.currentIndex()
        guard let element = ElementOps.hitTest(index, P(x, y), tolerance: tolerance, skip: store.lockedIds()) else { return false }
        store.select(index.expand(store.selection.union(element.allIds)))
        return true
    }

    @discardableResult
    func deselectAt(_ x: Float, _ y: Float, tolerance: Float) -> Bool {
        let index = store.currentIndex()
        guard let element = ElementOps.hitTest(index, P(x, y), tolerance: tolerance, skip: store.lockedIds()) else { return false }
        store.select(store.selection.subtracting(index.expand(element.allIds)))
        return true
    }

    func clear() { store.select([]) }

    func selectForContext(_ x: Float, _ y: Float, tolerance: Float) -> Bool {
        let index = store.currentIndex()
        guard let element = ElementOps.hitTest(index, P(x, y), tolerance: tolerance, skip: store.lockedIds()) else { return false }
        let ids = index.expand(element.allIds)
        if !ids.isSubset(of: store.selection) { store.select(ids) }
        return true
    }

    func reorder(_ direction: Int) {
        guard let current = store.canvas, !store.selection.isEmpty else { return }
        let updated = ZOrder.reorder(current.strokes, store.selection, store.currentIndex(), direction, current.layers)
        if updated != current.strokes { store.commit(updated) }
    }

    /// v30 gizleme; gizlenen eleman sayısı.
    @discardableResult
    func hide(_ elementIds: [String]) -> Int {
        guard let current = store.canvas else { return 0 }
        let index = store.currentIndex()
        let targets = elementIds.compactMap { index.element($0) }.filter { !$0.hidden }
        if targets.isEmpty { return 0 }
        let ids = targets.reduce(into: Set<String>()) { $0.formUnion($1.allIds) }
        let updated = ElementOps.setHidden(current.strokes, ids, true)
        if updated == current.strokes { return 0 }
        store.commit(updated)
        store.select(store.selection.subtracting(ids))
        return targets.count
    }

    @discardableResult
    func hideSelected() -> Int { hide(store.currentIndex().selectedElements(store.selection).map(\.id)) }

    @discardableResult
    func show(_ elementIds: [String]? = nil) -> Int {
        guard let current = store.canvas else { return 0 }
        let index = store.currentIndex()
        let targets = index.elements.filter { $0.hidden && (elementIds == nil || elementIds!.contains($0.id)) }
        if targets.isEmpty { return 0 }
        let ids = targets.reduce(into: Set<String>()) { $0.formUnion($1.allIds) }
        let updated = ElementOps.setHidden(current.strokes, ids, false)
        if updated == current.strokes { return 0 }
        store.commit(updated)
        return targets.count
    }

    func group() {
        let idx = store.currentIndex()
        store.mutateSelection { ElementOps.group($0, $1, newGroupId: UUID().uuidString.lowercased(), current: idx) }
    }

    func ungroup() {
        let idx = store.currentIndex()
        store.mutateSelection { ElementOps.ungroup($0, $1, current: idx) }
    }

    func delete() {
        store.mutateSelection { ElementOps.delete($0, $1) }
        clear()
    }

    /// Seçili elemanların kopyası biraz kaydırılarak eklenir; kopyalar seçili olur.
    func duplicate() {
        guard let current = store.canvas else { return }
        let ids = store.selection
        if ids.isEmpty { return }
        let chosen = current.strokes.filter { ids.contains($0.id) }
        if chosen.isEmpty { return }
        let shift = max(16, Float(min(current.width, current.height)) * 0.03)
        var groups = [String: String]()
        let q = ContentCodec.quantize
        func moved(_ p: StrokePoint) -> StrokePoint { p.at(q(p.x + shift), q(p.y + shift)) }
        let copies = chosen.enumerated().map { i, s -> Stroke in
            var c = s
            c.layerId = Layers.of(s, current.layers)
            c.id = UUID().uuidString.lowercased()
            if let g = s.groupId {
                if groups[g] == nil { groups[g] = UUID().uuidString.lowercased() }
                c.groupId = groups[g]
            }
            c.order = current.strokes.count + i
            c.points = s.points.map(moved)
            c.clip = s.clip?.map(moved)
            return c
        }
        store.commit(current.strokes + copies)
        store.select(Set(copies.map(\.id)))
    }

    func move(_ dx: Float, _ dy: Float) {
        if dx == 0 && dy == 0 { return }
        store.mutateSelection { ElementOps.translate($0, $1, dx, dy) }
    }

    func rotate(_ degrees: Float, pivot: P? = nil) {
        if degrees == 0 { return }
        store.mutateSelection { strokes, ids in
            guard let center = pivot ?? ElementOps.selectionBounds(strokes, ids)?.center else { return strokes }
            return ElementOps.rotate(strokes, ids, degrees, pivot: center)
        }
    }

    func scale(_ sx: Float, _ sy: Float, pivot: P? = nil) {
        store.mutateSelection { strokes, ids in
            guard let center = pivot ?? ElementOps.selectionBounds(strokes, ids)?.center else { return strokes }
            return ElementOps.scale(strokes, ids, sx, sy, pivot: center)
        }
    }
}

/// Kağıt ve kırpma (Android `FrameController`).
@MainActor
final class FrameController: ObservableObject {
    static let MIN_CANVAS = 64
    static let MAX_CANVAS = 8192
    static let ELEMENT_CROP_MIN: Float = 8

    private let store: EditorStore
    private unowned let host: EditorHost

    /// Kırp modundaki çerçeve (tuval koordinatlarında).
    @Published private(set) var cropRect: FRect?
    /// Kırpılan elemanlar (boşsa tuval kırpılır).
    @Published private(set) var cropTarget: Set<String> = []
    /// Kırpılan elemanların kırpmasız tüm alanı.
    @Published private(set) var cropArea: FRect?

    init(_ store: EditorStore, _ host: EditorHost) { self.store = store; self.host = host }

    func onModeChange(_ from: EditMode, _ to: EditMode) {
        if to == .CROP, let current = store.canvas {
            if from != .CROP { initCrop(current) }
        } else if to != .CROP {
            cropRect = nil; cropTarget = []; cropArea = nil
        }
    }

    func onContentReplaced() {
        guard let current = store.canvas else { return }
        if host.mode == .CROP { initCrop(current) }
    }

    private func fullBounds(_ strokes: [Stroke], _ ids: Set<String>) -> FRect? {
        ElementOps.bounds(strokes.filter { ids.contains($0.id) && $0.brushType != .ERASER }.map { s in var c = s; c.clip = nil; return c })
    }

    private func initCrop(_ current: CanvasUi) {
        let targets = store.selection
        let visible = targets.isEmpty ? nil : ElementOps.selectionBounds(current.strokes, targets)
        let area = visible == nil ? nil : fullBounds(current.strokes, targets)
        if let visible, let area {
            cropTarget = targets; cropArea = area; cropRect = visible
        } else {
            cropTarget = []; cropArea = nil
            cropRect = FRect(0, 0, Float(current.width), Float(current.height))
        }
    }

    func updateCrop(_ rect: FRect) {
        if !cropTarget.isEmpty, let area = cropArea {
            let minW = min(Self.ELEMENT_CROP_MIN, area.width), minH = min(Self.ELEMENT_CROP_MIN, area.height)
            let l = rect.left.coerceIn(area.left, area.right - minW)
            let t = rect.top.coerceIn(area.top, area.bottom - minH)
            let r = rect.right.coerceIn(min(l + minW, area.right), area.right)
            let b = rect.bottom.coerceIn(min(t + minH, area.bottom), area.bottom)
            cropRect = FRect(l, t, r, b)
            return
        }
        guard let current = store.canvas else { return }
        let w = Float(current.width), h = Float(current.height)
        let minW = min(Float(Self.MIN_CANVAS), w), minH = min(Float(Self.MIN_CANVAS), h)
        let l = rect.left.coerceIn(0, w - minW)
        let t = rect.top.coerceIn(0, h - minH)
        let r = rect.right.coerceIn(min(l + minW, w), w)
        let b = rect.bottom.coerceIn(min(t + minH, h), h)
        cropRect = FRect(l.rounded(), t.rounded(), r.rounded(), b.rounded())
    }

    func resetCrop() {
        guard let current = store.canvas else { return }
        if !cropTarget.isEmpty, let area = cropArea { cropRect = area; return }
        cropRect = FRect(0, 0, Float(current.width), Float(current.height))
    }

    /// Çerçeveyi uygular; dönen değer tuvalin sol-üst köşesinin kayması.
    @discardableResult
    func applyCrop() -> P? {
        guard let rect = cropRect, let current = store.canvas else { return nil }
        let targets = cropTarget
        if !targets.isEmpty, let area = cropArea {
            let covers = rect.left <= area.left + 0.5 && rect.top <= area.top + 0.5 && rect.right >= area.right - 0.5 && rect.bottom >= area.bottom - 0.5
            let q = ContentCodec.quantize
            let quad = [StrokePoint(q(rect.left), q(rect.top)), StrokePoint(q(rect.right), q(rect.top)),
                        StrokePoint(q(rect.right), q(rect.bottom)), StrokePoint(q(rect.left), q(rect.bottom))]
            let updated = current.strokes.map { s -> Stroke in
                guard targets.contains(s.id), s.brushType != .ERASER else { return s }
                var c = s; c.clip = covers ? nil : quad; return c
            }
            host.finishAction()
            if updated != current.strokes { store.commit(updated) }
            return nil
        }
        let l = Int(rect.left.rounded()), t = Int(rect.top.rounded()), r = Int(rect.right.rounded()), b = Int(rect.bottom.rounded())
        host.finishAction()
        if l == 0 && t == 0 && r == current.width && b == current.height { return nil }
        resizeCanvas(l, t, r, b)
        return P(Float(l), Float(t))
    }

    func resizeCanvas(_ left: Int, _ top: Int, _ right: Int, _ bottom: Int) {
        guard let current = store.canvas else { return }
        let width = (right - left).coerceIn(Self.MIN_CANVAS, Self.MAX_CANVAS)
        let height = (bottom - top).coerceIn(Self.MIN_CANVAS, Self.MAX_CANVAS)
        if left == 0 && top == 0 && width == current.width && height == current.height { return }
        let pages = (left == 0 && top == 0) ? current.pages : current.pages.map { page in
            ElementOps.translate(page, Set(page.map(\.id)), -Float(left), -Float(top))
        }
        store.commit(pages: pages, width: width, height: height, background: current.backgroundColorHex)
    }

    /// Kağıdın arka plan rengi (geri alınabilir).
    @discardableResult
    func setBackgroundColor(_ colorHex: String, coalesce: String? = nil, pageOnly: Bool = false, shared: BackgroundSpec? = nil) -> Bool {
        guard let current = store.canvas else { return false }
        let hex = colorHex.uppercased()
        if pageOnly {
            let page = current.currentPage
            if hex.caseInsensitiveCompare(current.paperOf(page)) == .orderedSame { return false }
            var list = padded(current)
            var pb = list[page] ?? PageBackground()
            pb.paperHex = hex
            list[page] = pb
            var pages = current.pages
            pages[page] = GraphPaper.recolor([pages[page]], hex)[0]
            store.commit(pages: pages, width: current.width, height: current.height, background: current.backgroundColorHex, coalesce: coalesce, pageBackgrounds: list)
            return true
        }
        let overrides = current.pageBackgrounds.contains { $0?.paperHex != nil }
        if hex.caseInsensitiveCompare(current.backgroundColorHex) == .orderedSame && !overrides { return false }
        let cleared = current.pageBackgrounds.map { b -> PageBackground? in
            guard var p = b else { return nil }
            p.paperHex = nil
            return p.isEmpty ? nil : p
        }
        store.commit(pages: GraphPaper.recolor(current.pages, hex), width: current.width, height: current.height, background: hex, coalesce: coalesce,
                     pageBackgrounds: overrides ? cleared : nil, shared: shared)
        return true
    }

    @discardableResult
    func setPageSpec(_ spec: BackgroundSpec?, coalesce: String? = nil) -> Bool {
        guard let current = store.canvas else { return false }
        let page = current.currentPage
        var list = padded(current)
        var pb = list[page] ?? PageBackground()
        pb.spec = spec
        let updated: PageBackground? = pb.isEmpty ? nil : pb
        if updated == list[page] { return false }
        list[page] = updated
        store.commit(pages: current.pages, width: current.width, height: current.height, background: current.backgroundColorHex, coalesce: coalesce, pageBackgrounds: list)
        return true
    }

    @discardableResult
    func clearPageSpecs() -> Bool {
        guard let current = store.canvas, current.pageBackgrounds.contains(where: { $0?.spec != nil }) else { return false }
        let cleared = current.pageBackgrounds.map { b -> PageBackground? in
            guard var p = b else { return nil }
            p.spec = nil
            return p.isEmpty ? nil : p
        }
        store.commit(pages: current.pages, width: current.width, height: current.height, background: current.backgroundColorHex, pageBackgrounds: cleared)
        return true
    }

    /// v24 "Tüm sayfalara uygula".
    @discardableResult
    func applyPageToAll() -> BackgroundSpec? {
        guard let current = store.canvas else { return nil }
        let hex = current.pagePaperHex.uppercased()
        let spec = current.pageBackground
        store.commit(pages: GraphPaper.recolor(current.pages, hex), width: current.width, height: current.height, background: hex,
                     pageBackgrounds: current.hasCover ? [PageBackground(cover: true)] : [], shared: spec)
        return spec
    }

    private func padded(_ current: CanvasUi) -> [PageBackground?] {
        var list = current.pageBackgrounds
        while list.count < current.pages.count { list.append(nil) }
        return list
    }
}
