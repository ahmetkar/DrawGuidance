import Combine
import CoreGraphics
import Foundation

/// v40 arka plan resmini taşıma/boyutlandırma (Android `BackgroundTransformController`).
@MainActor
final class BackgroundTransformController {
    private let store: EditorStore
    private let frame: FrameController
    private let perPage: () -> Bool
    private var origin: (BackgroundSpec, [PageBackground?])?

    init(_ store: EditorStore, _ frame: FrameController, perPage: @escaping () -> Bool) {
        self.store = store; self.frame = frame; self.perPage = perPage
    }

    func available() -> Bool {
        guard let c = store.canvas else { return false }
        return !c.kind.isNotes && c.pageBackground.imageId != nil
    }

    private func onPage(_ c: CanvasUi) -> Bool {
        perPage() || (c.pageBackgrounds.indices.contains(c.currentPage) && c.pageBackgrounds[c.currentPage]?.spec != nil)
    }

    func onCurrentPage() -> Bool { store.canvas.map(onPage) ?? false }

    private func withSpec(_ c: CanvasUi, _ spec: BackgroundSpec) -> CanvasUi {
        var n = c
        if !onPage(c) { n.background = spec; return n }
        var list = c.pageBackgrounds
        while list.count < c.pages.count { list.append(nil) }
        var pb = list[c.currentPage] ?? PageBackground()
        pb.spec = spec
        list[c.currentPage] = pb
        n.pageBackgrounds = list
        return n
    }

    func preview(_ spec: BackgroundSpec) {
        guard let c = store.canvas else { return }
        if origin == nil { origin = (c.background, c.pageBackgrounds) }
        if c.pageBackground == spec { return }
        store.updateCanvas { self.withSpec($0, spec) }
    }

    func commit(_ spec: BackgroundSpec, coalesce: String? = nil) {
        restore()
        guard let c = store.canvas, c.pageBackground != spec else { return }
        if onPage(c) { frame.setPageSpec(spec, coalesce: coalesce) } else { store.commitSharedBackground(spec, coalesce: coalesce) }
    }

    func restore() {
        guard let (background, pages) = origin else { return }
        origin = nil
        store.updateCanvas { c in var n = c; n.background = background; n.pageBackgrounds = pages; return n }
    }
}

/// v45 çizgi roman kapağı ve not alma panelleri (Android `V45NotesComicController`).
@MainActor
final class V45NotesComicController: ObservableObject {
    private let store: EditorStore
    private unowned let host: EditorHost
    private let pages: PageController
    @Published private(set) var panelsOpen = false

    init(_ store: EditorStore, _ host: EditorHost, _ pages: PageController) {
        self.store = store; self.host = host; self.pages = pages
    }

    func canAddCover() -> Bool { store.canvas.map { $0.kind == .COMIC && !$0.hasCover } ?? false }

    @discardableResult
    func addCover() -> Bool {
        guard let current = store.canvas, canAddCover() else { return false }
        return pages.addCover(ComicCover.build(current.width, current.height, current.name, ComicCover.subtitlePlaceholder()))
    }

    func openPanels() { panelsOpen = true }
    func closePanels() { panelsOpen = false }

    @discardableResult
    func insertPanels(_ layoutId: String) -> Set<String> {
        guard let current = store.canvas, let layout = NotesPanels.byId(layoutId) else { return [] }
        let added = NotesPanels.build(layout, Float(current.width), Float(current.height))
        if added.isEmpty { return [] }
        store.commit((added + current.strokes).enumerated().map { i, s in var c = s; if c.order != i { c.order = i }; return c })
        panelsOpen = false
        let ids = Set(added.map(\.id))
        host.inserted(ids)
        return ids
    }
}

/// Eklenen resmin düzenlenmesi: kırpma ve vektörleştirme (Android `ImageEditController`).
@MainActor
final class ImageEditController: ObservableObject {
    nonisolated static let TRACED = "TRACED"
    private let store: EditorStore
    private unowned let host: EditorHost
    @Published private(set) var tracing = false
    @Published private(set) var empty = false
    private var job: Task<Void, Never>?

    init(_ store: EditorStore, _ host: EditorHost) { self.store = store; self.host = host }

    var selected: Stroke? {
        let ids = store.selection
        guard ids.count == 1 else { return nil }
        return store.canvas?.strokes.first { ids.contains($0.id) && $0.brushType == .IMAGE }
    }

    func editSelected() {
        guard store.selection.count == 1, let image = store.selectedStroke({ $0.brushType == .IMAGE }) else { return }
        host.startAction(.IMAGE_EDIT, target: [image.id])
    }

    func crop() { if selected != nil { host.startAction(.CROP) } }

    func cancelTrace() { job?.cancel(); job = nil; tracing = false }

    func vectorize(_ options: EdgeOptions, lineWidth: Float, keepImage: Bool) {
        guard let image = selected, image.points.count >= 4, let bitmap = CanvasImageCache.get(image.assetId) else { return }
        cancelTrace()
        tracing = true
        empty = false
        job = Task { [weak self] in
            let traced: EdgeResult? = await Task.detached(priority: .userInitiated) {
                let scale = min(1, 1280 / Float(max(bitmap.width, bitmap.height)))
                let w = max(1, Int(Float(bitmap.width) * scale)), h = max(1, Int(Float(bitmap.height) * scale))
                let pixels = HarmonyRenderer.argbPixels(bitmap, w, h)
                return try? EdgeTracer.trace(pixels, w, h, options: options) { Task.isCancelled }
            }.value
            guard let self else { return }
            defer { self.tracing = false }
            guard !Task.isCancelled, let result = traced, let current = self.store.canvas else { return }
            let p0 = image.points[0], p1 = image.points[1], p3 = image.points[3]
            func map(_ u: Float, _ v: Float) -> P { P(p0.x + (p1.x - p0.x) * u + (p3.x - p0.x) * v, p0.y + (p1.y - p0.y) * u + (p3.y - p0.y) * v) }
            let paths = result.paths.map { ep -> ShapePath in
                let pts = ep.points.map { map($0.x, $0.y) }
                return ShapePath(points: ep.closed && pts.count > 2 ? pts + [pts[0]] : pts, closed: ep.closed,
                                 strokeColorHex: options.colored ? ep.color : nil, nodes: ep.nodes)
            }
            if paths.isEmpty { self.empty = true; return }
            let style = ShapeStyle(strokeColorHex: "#1E1E1E", strokeWidth: lineWidth.coerceIn(0.3, 20))
            guard var shape = ShapeFactory.fromPaths(Self.TRACED, paths, style: style, graph: nil, id: UUID().uuidString.lowercased(), order: image.order) else { return }
            shape.layerId = image.layerId ?? ""; shape.groupId = nil; shape.clip = image.clip
            var updated = current.strokes
            if keepImage {
                let i = updated.firstIndex { $0.id == image.id } ?? (updated.count - 1)
                updated.insert(shape, at: i + 1)
            } else {
                updated = updated.map { $0.id == image.id ? shape : $0 }
            }
            self.store.commit(updated.enumerated().map { i, s in var c = s; if c.order != i { c.order = i }; return c })
            self.host.inserted([shape.id])
        }
    }
}
