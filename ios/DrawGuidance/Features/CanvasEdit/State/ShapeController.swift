import Combine
import Foundation

/// v37 kova imlecinin altındaki şekil ve boyanmaya hazır alanı (Android `PaintTarget`).
struct PaintTarget: Equatable { let strokeId: String; let region: [P]? }

/// Renk çok koyu mu (göreli parlaklık); okunamayan renk koyu sayılmaz.
func darkHex(_ hex: String) -> Bool {
    let h = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
    guard h.count >= 6, let v = UInt32(h.prefix(6), radix: 16) else { return false }
    let r = Float(v >> 16 & 0xFF) / 255, g = Float(v >> 8 & 0xFF) / 255, b = Float(v & 0xFF) / 255
    return 0.2126 * r + 0.7152 * g + 0.0722 * b < 0.2
}

/// Vektör şekiller (Android `ShapeController`): ekleme, şekil düzenleme (v16 vektör noktaları), alan boya (v17),
/// parça biçimi, grafik düzenleme ve çocuk modunda boya fırçası.
@MainActor
final class ShapeController: ObservableObject {
    private static let NODE_HIT: Float = 1.8
    private static let EDGE_HIT: Float = 1.3

    private let store: EditorStore
    private unowned let host: EditorHost
    private let brush: () -> BrushController
    private let style: StyleController

    @Published private(set) var shape = ShapeKind.CIRCLE.rawValue
    @Published private(set) var isPickerOpen = false
    @Published private(set) var shapeStyle = ShapeStyle(strokeWidth: BrushType.PENCIL.defaultSize)
    @Published private(set) var graphSpec = GraphSpec()
    @Published private(set) var arrowFrom: FRect?
    @Published private(set) var tool: ShapeTool = .NODES
    @Published private(set) var vector = VectorSelection()
    @Published private(set) var parts: Set<Int> = []
    @Published private(set) var regionColor: String?
    @Published private(set) var graphEdit: GraphEditRequest?

    struct GraphEditRequest: Equatable { let strokeId: String; let tag: String; let initial: String }

    private(set) lazy var paramShapes = ParamShapeController(store) { [unowned self] in self.selectedShape }

    private var lineStyle = PartStyle()

    init(_ store: EditorStore, _ host: EditorHost, brush: @escaping () -> BrushController, style: StyleController) {
        self.store = store; self.host = host; self.brush = brush; self.style = style
    }

    /// Seçili (ilk) vektör şekil.
    var selectedShape: Stroke? {
        let ids = store.selection
        if ids.isEmpty { return nil }
        return store.canvas?.strokes.first { ids.contains($0.id) && $0.shape != nil }
    }

    // MARK: Ekleme

    func startArrowFrom(_ bounds: FRect) {
        shape = ShapeKind.ARROW.rawValue
        isPickerOpen = false
        host.startAction(.SHAPE)
        arrowFrom = bounds
    }

    func clearArrowFrom() { arrowFrom = nil }

    private func edgePoint(_ box: FRect, _ target: P) -> P {
        let c = box.center
        let d = target - c
        let len = d.distance
        if len < 1e-3 || box.contains(target) { return c }
        let hw = box.width / 2, hh = box.height / 2
        let t = min(abs(d.x) > 1e-4 ? hw / abs(d.x) : .greatestFiniteMagnitude, abs(d.y) > 1e-4 ? hh / abs(d.y) : .greatestFiniteMagnitude)
        let margin = (min(box.width, box.height) * 0.06).coerceIn(4, 24)
        return c + d * t + d / len * margin
    }

    func openPicker() { isPickerOpen = true }
    func closePicker() { isPickerOpen = false }

    func selectShape(_ id: String) {
        shape = id
        isPickerOpen = false
        host.startAction(.SHAPE)
    }

    func setNewShapeFill(_ hex: String?) { shapeStyle.fillColorHex = hex }
    func setDrawingWidth(_ width: Float) { shapeStyle.strokeWidth = width }

    func updateShapeStyle(_ value: ShapeStyle) {
        shapeStyle = value
        style.applyStyleToSelection(value)
    }

    func updateGraphSpec(_ graph: GraphSpec) {
        graphSpec = graph
        style.applyGraphToSelection(graph)
    }

    func newShapeStyle() -> ShapeStyle {
        var s = shapeStyle
        s.strokeColorHex = brush().inkColor()
        return s
    }

    func shapeStrokes(_ gestureId: String, _ start0: P, _ end: P) -> [Stroke] {
        guard let current = store.canvas else { return [] }
        let id = shape
        let start = (id == ShapeKind.ARROW.rawValue ? arrowFrom.map { edgePoint($0, end) } : nil) ?? start0
        let sid = StableId.from("\(gestureId)-shape")
        if current.guide.draw3d && Scene3D.isSolidKind(id) {
            let cam = Scene3D.camera(current.guide, current.width, current.height)
            return Scene3D.solidStroke(id, start: start, end: end, style: newShapeStyle(), cam: cam, id: sid, order: current.strokes.count).map { [$0] } ?? []
        }
        var graph: GraphSpec?
        if GraphShapes.isGraph(id) {
            var g = GraphShapes.defaultGraph(id)
            g.paper = GraphPaper.normalize(current.pagePaperHex)
            graph = g
        }
        return ShapeFactory.build(id, start, end, style: newShapeStyle(), graph: graph, id: sid, order: current.strokes.count).map { [$0] } ?? []
    }

    func addShape(_ gestureId: String, _ start: P, _ end: P) {
        if arrowFrom != nil && shape == ShapeKind.ARROW.rawValue {
            guard let current = store.canvas else { return }
            let strokes = shapeStrokes(gestureId, start, end)
            arrowFrom = nil
            if !strokes.isEmpty {
                store.commit(current.strokes + strokes)
                host.inserted(Set(strokes.map(\.id)))
            }
            return
        }
        if (end - start).distance < 2 { addShapeAt(gestureId, start); return }
        guard let current = store.canvas else { return }
        let strokes = shapeStrokes(gestureId, start, end)
        if !strokes.isEmpty {
            store.commit(current.strokes + strokes)
            host.inserted(Set(strokes.map(\.id)))
        }
    }

    func addShapeAt(_ gestureId: String, _ center: P) {
        if arrowFrom != nil && shape == ShapeKind.ARROW.rawValue { addShape(gestureId, center, center); return }
        guard let current = store.canvas else { return }
        let id = shape
        let line = ShapeFactory.fit(id) == .LINE
        let big = ShapeFactory.isLibrary(id) || GraphShapes.isModernGraph(id)
        let aspect: Float = ShapeFactory.fit(id) == .SQUARE ? 1 : ShapeFactory.aspect(id)
        let half = Float(min(current.width, current.height)) * (big ? 0.22 : 0.15)
        let hw = line ? half : (aspect >= 1 ? half : half * aspect)
        let hh = line ? half : (aspect >= 1 ? half / aspect : half)
        let start = line ? P(center.x - half, center.y) : center - P(hw, hh)
        let end = line ? P(center.x + half, center.y) : center + P(hw, hh)
        let strokes = shapeStrokes(gestureId, start, end)
        if !strokes.isEmpty {
            store.commit(current.strokes + strokes)
            host.inserted(Set(strokes.map(\.id)))
        }
    }

    // MARK: Şekil düzenleme modu

    func selectTool(_ value: ShapeTool) {
        tool = value
        let v = vector
        setSelection(VectorSelection(nodes: value == .NODES || value == .ADD_NODE ? v.nodes : [], edge: value == .LINES ? v.edge : nil))
    }

    func editSelected() {
        guard let s = store.selectedStroke({ $0.shape != nil }) else { return }
        paramShapes.clearError()
        if let spec = s.shape, GraphShapes.isGraph(spec.kind) && spec.graph != nil { tool = .GRAPH }
        else if paramShapes.model(s) != nil { tool = .PARAMS }
        else { tool = .NODES }
        setSelection(VectorSelection(), s)
        host.startAction(.SHAPE_EDIT, target: [s.id])
    }

    func onModeChange(_ to: EditMode) {
        if to != .SHAPE { arrowFrom = nil }
        if to != .SHAPE_EDIT { reset() }
    }

    func reset() { vector = VectorSelection(); parts = [] }

    func onContentReplaced() {
        reset()
        if host.mode == .SHAPE_EDIT, let current = store.canvas {
            if !current.strokes.contains(where: { store.selection.contains($0.id) && $0.shape != nil }) { host.endAction() }
        }
    }

    var coarse: Bool { store.canvas?.kind.simple == true }

    private func graphOf(_ stroke: Stroke) -> VectorEdit.Graph { VectorEdit.graph(stroke, coarse: coarse) }
    private func graphOf(_ paths: [EditablePath]) -> VectorEdit.Graph {
        coarse ? VectorEdit.graph(paths, diagonal: VectorEdit.diagonal(paths), coarse: true) : VectorEdit.graph(paths)
    }

    func canSimplify() -> Bool { selectedShape.map { VectorEdit.canSimplify(graphOf($0)) } ?? false }

    func simplifySelected() {
        guard let stroke = selectedShape else { return }
        let g = graphOf(stroke)
        commitPaths(stroke, g, VectorEdit.simplifyNodes(g, VectorEdit.diagonal(stroke)), VectorSelection())
    }

    private func setSelection(_ sel: VectorSelection, _ stroke: Stroke? = nil) {
        let s = stroke ?? selectedShape
        vector = sel
        parts = (s == nil || (sel.nodes.isEmpty && sel.edge == nil)) ? [] : partsOf(graphOf(s!), sel)
    }

    private func partsOf(_ g: VectorEdit.Graph, _ sel: VectorSelection) -> Set<Int> {
        if let (a, b) = sel.edge, let e = g.edgeBetween(a, b) { return [e.path] }
        let ids = selectedNodeIds(g, sel)
        return Set(g.edges.filter { ids.contains($0.a) || ids.contains($0.b) }.map(\.path))
    }

    private func selectedNodeIds(_ g: VectorEdit.Graph, _ sel: VectorSelection? = nil) -> Set<Int> {
        Set((sel ?? vector).nodes.compactMap { g.nodeNear($0)?.id })
    }

    private func selectedEdge(_ g: VectorEdit.Graph, _ sel: VectorSelection? = nil) -> VectorEdit.Edge? {
        (sel ?? vector).edge.flatMap { g.edgeBetween($0.0, $0.1) }
    }

    private func toggle(_ list: [P], _ p: P) -> [P] {
        list.contains { VectorEdit.near($0, p) } ? list.filter { !VectorEdit.near($0, p) } : list + [p]
    }

    func applyTool(_ point: P, tolerance: Float = 12) {
        guard let stroke = selectedShape else { return }
        switch tool {
        case .GRAPH: graphTap(stroke, point, tolerance); return
        case .PARAMS: return
        case .FILL: _ = fillRegion(stroke, point, select: true, reportOpen: true); return
        default: break
        }
        let g = graphOf(stroke)
        let sel = vector
        let node = g.nodeAt(point, tolerance * Self.NODE_HIT)
        switch tool {
        case .NODES:
            setSelection(node.map { VectorSelection(nodes: toggle(sel.nodes, $0.point)) } ?? VectorSelection(), stroke)
        case .ADD_NODE:
            if let node { setSelection(VectorSelection(nodes: toggle(sel.nodes, node.point)), stroke); return }
            if let hit = g.edgeAt(point, tolerance * Self.EDGE_HIT) {
                let (paths, at) = VectorEdit.insertNode(g, hit)
                commitPaths(stroke, g, paths, VectorSelection(nodes: [at]))
            } else {
                commitPaths(stroke, g, VectorEdit.addFreeNode(g, point), VectorSelection(nodes: [point]))
            }
        case .CONNECT: connectTap(stroke, g, point, tolerance)
        case .LINES:
            let hit = g.edgeAt(point, tolerance * Self.EDGE_HIT)
            let current = selectedEdge(g, sel)
            if let hit, hit.edge.id != current?.id { setSelection(VectorSelection(edge: (hit.edge.start, hit.edge.end)), stroke) }
            else { setSelection(VectorSelection(), stroke) }
        default: break
        }
    }

    private func connectTap(_ stroke: Stroke, _ g: VectorEdit.Graph, _ point: P, _ tolerance: Float) {
        let from = vector.linkFrom
        let (paths, at, free) = anchor(g, point, tolerance, exclude: from)
        if from == nil {
            let withNode = free ? VectorEdit.addFreeNode(VectorEdit.graph(paths), at) : paths
            commitPaths(stroke, g, withNode, VectorSelection(nodes: [at], linkFrom: at), same: !free && paths == g.paths)
        } else if VectorEdit.near(from!, at) {
            setSelection(VectorSelection(), stroke)
        } else {
            let connected = VectorEdit.connect(VectorEdit.graph(paths), from!, at, style: withLineStyle) ?? paths
            commitPaths(stroke, g, connected, VectorSelection(nodes: [at], linkFrom: at))
        }
    }

    private func anchor(_ g: VectorEdit.Graph, _ point: P, _ tolerance: Float, exclude: P? = nil) -> ([EditablePath], P, Bool) {
        let excludeId = exclude.flatMap { g.nodeNear($0)?.id }
        if let n = g.nodeAt(point, tolerance * Self.NODE_HIT, exclude: excludeId) { return (g.paths, n.point, false) }
        if let hit = g.edgeAt(point, tolerance * Self.EDGE_HIT) {
            let (paths, at) = VectorEdit.insertNode(g, hit)
            return (paths, at, false)
        }
        return (g.paths, point, true)
    }

    /// Düzenlenen parçaları kaydeder; parçalar değişmediyse ([same]) yalnızca seçim değişir.
    private func commitPaths(_ stroke: Stroke, _ g: VectorEdit.Graph, _ paths: [EditablePath], _ sel: VectorSelection, same: Bool? = nil) {
        if same ?? (paths == g.paths) { setSelection(sel, stroke); return }
        guard let current = store.canvas else { return }
        if !VectorEdit.drawable(paths) { removeShape(current, stroke); return }
        guard let edited = ShapeFactory.fromEditable(stroke, refreshRegions(stroke, paths)) else { return }
        store.commit(current.strokes.map { s in s.id == stroke.id ? reordered(edited, s.order) : s })
        store.select([stroke.id])
        setSelection(sel, edited)
    }

    private func reordered(_ s: Stroke, _ order: Int) -> Stroke { var c = s; c.order = order; return c }

    private func removeShape(_ current: CanvasUi, _ stroke: Stroke) {
        store.commit(current.strokes.filter { $0.id != stroke.id }.enumerated().map { i, s in s.order == i ? s : reordered(s, i) })
        store.select([])
        reset()
        host.endAction()
    }

    // MARK: v17 Alan boya

    func setRegionColor(_ hex: String?) { regionColor = hex }
    func regionInk() -> String { brush().inkColor() }

    private func rank(_ s: Stroke?, _ strokes: [Stroke]) -> Int64 {
        guard let s else { return -1 }
        return Int64(s.order) * 1_000_000 + Int64(strokes.firstIndex { $0.id == s.id } ?? -1)
    }

    @discardableResult
    func paintAt(_ point: P, locked: Set<String>) -> Bool {
        let strokes = store.canvas?.strokes ?? []
        let ink = InkFill.closedAt(strokes, point, locked: locked)
        let candidates = paintCandidates(point, locked).filter { !$0.id.hasPrefix(InkFill.PREFIX) }
        if let ink, rank(ink, strokes) > rank(candidates.first, strokes), paintInk(ink) { return true }
        for stroke in candidates where fillRegion(stroke, point, select: true, reportOpen: false) { return true }
        if let ink, paintInk(ink) { return true }
        store.select([])
        return false
    }

    private func paintInk(_ ink: Stroke) -> Bool {
        guard let current = store.canvas else { return false }
        let color = regionColor ?? brush().inkColor()
        let next = color.isEmpty ? current.strokes.filter { $0.id != InkFill.PREFIX + ink.id } : InkFill.apply(current.strokes, [ink.id], color)
        if next == current.strokes { return false }
        store.commit(next)
        store.select([ink.id])
        return true
    }

    func paintSelectedAt(_ point: P) -> Bool {
        guard let stroke = store.selectedStroke({ $0.shape != nil }) else { return false }
        return fillRegion(stroke, point, select: true, reportOpen: false)
    }

    private func paintCandidates(_ point: P, _ locked: Set<String>) -> [Stroke] {
        guard let current = store.canvas else { return [] }
        return current.strokes.enumerated()
            .filter { _, s in s.shape != nil && !s.hidden && !locked.contains(s.id) && !s.id.hasPrefix(KidsBrush.PREFIX) }
            .sorted { a, b in a.element.order != b.element.order ? a.element.order > b.element.order : a.offset > b.offset }
            .map(\.element)
            .filter { stroke in
                let pts = ShapeFactory.subPaths(stroke).flatMap(\.points)
                guard !pts.isEmpty else { return false }
                let margin: Float = 2
                let xs = pts.map(\.x), ys = pts.map(\.y)
                return point.x >= xs.min()! - margin && point.x <= xs.max()! + margin && point.y >= ys.min()! - margin && point.y <= ys.max()! + margin
            }
    }

    func paintTarget(_ point: P, locked: Set<String>) -> PaintTarget? {
        let candidates = paintCandidates(point, locked)
        for stroke in candidates {
            guard let spec = stroke.shape, let region = regionAt(spec.style, ShapeFactory.subPaths(stroke), point) else { continue }
            return PaintTarget(strokeId: stroke.id, region: region)
        }
        return candidates.first.map { PaintTarget(strokeId: $0.id, region: nil) }
    }

    @discardableResult
    func paintWhole(_ point: P, locked: Set<String>) -> Bool {
        guard let t = paintTarget(point, locked: locked), let stroke = store.canvas?.strokes.first(where: { $0.id == t.strokeId }) else {
            store.select([]); return false
        }
        guard let current = store.canvas else { return false }
        store.select([stroke.id])
        guard let spec = stroke.shape else { return false }
        let color = regionColor ?? brush().inkColor()
        let paths = ShapeFactory.subPaths(stroke)
        let ink = spec.style.strokeColorHex
        let all: [P] = paths.flatMap(\.points)
        let xs: [Float] = all.map(\.x), ys: [Float] = all.map(\.y)
        let bw: Float = (xs.max() ?? 0) - (xs.min() ?? 0)
        let bh: Float = (ys.max() ?? 0) - (ys.min() ?? 0)
        let boxArea: Float = bw * bh
        func fillable(_ p: EditablePath) -> Bool {
            if !p.closed || p.points.count < 3 || p.label != nil || p.tag == VectorEdit.VNODE || p.tag == "guide" { return false }
            guard let fill = p.fillColorHex ?? spec.style.fillColorHex else { return true }
            if fill.caseInsensitiveCompare(p.strokeColorHex ?? ink) == .orderedSame { return false }
            let small = RegionFill.areaOf(p.points) < boxArea * 0.03
            return !(small && darkHex(fill))
        }
        let targets = Set(paths.indices.filter { KidsBrush.isPaintLayer(paths[$0].tag) || fillable(paths[$0]) })
        if targets.isEmpty { return fillRegion(stroke, point, select: false, reportOpen: false) }
        let next = color.isEmpty
            ? paths.filter { !KidsBrush.isPaintLayer($0.tag) }
            : paths.enumerated().map { i, p in targets.contains(i) ? { var c = p; c.fillColorHex = color; return c }() : p }
        guard let edited = ShapeFactory.fromEditable(stroke, next) else { return false }
        store.commit(current.strokes.map { $0.id == stroke.id ? reordered(edited, $0.order) : $0 })
        return true
    }

    private func fillRegion(_ stroke: Stroke, _ point: P, select: Bool, reportOpen: Bool) -> Bool {
        guard let current = store.canvas, let spec = stroke.shape else { return false }
        let paths = ShapeFactory.subPaths(stroke)
        let color = regionColor ?? brush().inkColor()
        let found = regionAt(spec.style, paths, point)
        let regions = paths.indices.filter { paths[$0].tag == VectorEdit.REGION }
        let containing = regions.filter { RegionFill.contains(paths[$0].points, point) }
        let same: Int?
        if let found {
            let a = RegionFill.areaOf(found)
            same = containing.last { abs(RegionFill.areaOf(paths[$0].points) - a) <= max(a * 0.08, 4) }
        } else {
            same = containing.last
        }
        var next: [EditablePath]
        if color.isEmpty {
            guard let hit = same ?? containing.last else { return false }
            next = paths
            next.remove(at: hit)
        } else if let same, paths.contains(where: { $0.tag == KidsBrush.TAG }) {
            var top = paths[same]; top.fillColorHex = color
            next = paths.enumerated().filter { $0.offset != same }.map(\.element) + [top]
        } else if let same {
            next = paths
            next[same].fillColorHex = color
        } else if let found {
            next = paths + [EditablePath(points: found, closed: true, fillColorHex: color, widthScale: 0, tag: VectorEdit.REGION)]
        } else {
            if reportOpen { host.emit(.regionOpen) }
            return false
        }
        guard let edited = ShapeFactory.fromEditable(stroke, next) else { return false }
        if select { store.select([stroke.id]) }
        store.commit(current.strokes.map { $0.id == stroke.id ? reordered(edited, $0.order) : $0 })
        return true
    }

    // MARK: v45 çocuk boya fırçası

    func brushRegion(_ point: P, locked: Set<String>) -> KidsBrush.Region? {
        let strokes = store.canvas?.strokes ?? []
        let ink = InkFill.closedAt(strokes, point, locked: locked)
        let candidates = paintCandidates(point, locked).filter { !$0.id.hasPrefix(InkFill.PREFIX) }
        func inkRegion(_ s: Stroke) -> KidsBrush.Region? {
            let walls = [RegionFill.Line(s.points.map { P($0.x, $0.y) }, true, max(0.3, s.sizePx))]
            guard let outline = RegionFill.region(walls, point) else { return nil }
            return KidsBrush.Region(s.id, true, walls, 0, point, outline)
        }
        if let ink, rank(ink, strokes) > rank(candidates.first, strokes), let r = inkRegion(ink) { return r }
        for stroke in candidates {
            guard let spec = stroke.shape else { continue }
            let walls = wallsOf(spec.style, ShapeFactory.subPaths(stroke))
            if walls.isEmpty { continue }
            var gap: Float = 0
            var outline = RegionFill.region(walls, point)
            if outline == nil {
                gap = (spec.style.strokeWidth * 1.5).coerceIn(1.5, 8)
                outline = RegionFill.region(walls, point, gap: gap)
            }
            guard let o = outline else { continue }
            return KidsBrush.Region(stroke.id, false, walls, gap, point, o)
        }
        return ink.flatMap(inkRegion)
    }

    @discardableResult
    func commitBrush(_ region: KidsBrush.Region?, _ points: [P], sizePx: Float, colorHex: String) -> Bool {
        guard let current = store.canvas, !points.isEmpty, !colorHex.isEmpty else { return false }
        guard let region else {
            store.commit(current.strokes + [KidsBrush.freeStroke(points, colorHex, sizePx, current.strokes.count, nil)])
            return true
        }
        guard let target = current.strokes.first(where: { $0.id == region.strokeId }) else { return false }
        let polys = KidsBrush.clip(region, points, KidsBrush.radius(sizePx))
        if polys.isEmpty { return false }
        if region.ink {
            guard let paint = KidsBrush.inkPaint(target, polys, colorHex) else { return false }
            store.commit(KidsBrush.insertInkPaint(current.strokes, target.id, paint))
            return true
        }
        guard let edited = ShapeFactory.fromEditable(target, ShapeFactory.subPaths(target) + KidsBrush.parts(polys, colorHex)) else { return false }
        store.commit(current.strokes.map { $0.id == target.id ? reordered(edited, $0.order) : $0 })
        return true
    }

    private func wallsOf(_ style: ShapeStyle, _ paths: [EditablePath]) -> [RegionFill.Line] {
        paths.compactMap { p in
            if p.label != nil || p.tag == VectorEdit.VNODE || KidsBrush.isPaintLayer(p.tag) || p.tag == "guide" || p.points.count < 2 { return nil }
            let scale = p.widthScale ?? 1
            let width: Float
            if scale > 0 { width = max(0.3, style.strokeWidth * scale) }
            else if p.closed && (p.fillColorHex ?? style.fillColorHex) != nil { width = 0.3 }
            else { return nil }
            return RegionFill.Line(p.points, p.closed, width)
        }
    }

    private func regionAt(_ style: ShapeStyle, _ paths: [EditablePath], _ tap: P) -> [P]? {
        let walls = wallsOf(style, paths)
        if walls.isEmpty { return nil }
        return RegionFill.region(walls, tap) ?? RegionFill.region(walls, tap, gap: (style.strokeWidth * 1.5).coerceIn(1.5, 8))
    }

    private func refreshRegions(_ stroke: Stroke, _ paths: [EditablePath]) -> [EditablePath] {
        guard paths.contains(where: { $0.tag == VectorEdit.REGION }), let style = stroke.shape?.style else { return paths }
        return paths.map { p in
            guard p.tag == VectorEdit.REGION, let seed = RegionFill.interiorPoint(p.points), let poly = regionAt(style, paths, seed) else { return p }
            var c = p; c.points = poly; return c
        }
    }

    // MARK: Parça özellikleri

    private func withLineStyle(_ p: EditablePath) -> EditablePath {
        if lineStyle == PartStyle() { return p }
        var s = lineStyle; s.fillColorHex = nil
        return applyStyle(p, s)
    }

    private func applyStyle(_ p: EditablePath, _ style: PartStyle) -> EditablePath {
        var out = p
        if let c = style.strokeColorHex { out.strokeColorHex = c }
        if let f = style.fillColorHex { out.fillColorHex = f.isEmpty ? nil : f }
        if let w = style.widthScale { out.widthScale = w == 1 ? nil : w }
        if let a = style.alpha { out.alpha = a >= 0.999 ? nil : a }
        func unhide() { if out.widthScale == 0 { out.widthScale = nil } }
        switch style.line {
        case "solid": out.dashed = false; out.dotted = false; unhide()
        case "dashed": out.dashed = true; out.dotted = false; unhide()
        case "dotted": out.dashed = false; out.dotted = true; unhide()
        case "none": out.widthScale = 0
        default: break
        }
        return out
    }

    func applyPartStyle(_ style: PartStyle) {
        guard let stroke = selectedShape, let current = store.canvas else { return }
        let parts = self.parts
        lineStyle = PartStyle(strokeColorHex: style.strokeColorHex ?? lineStyle.strokeColorHex, fillColorHex: nil,
                              widthScale: style.widthScale ?? lineStyle.widthScale, line: style.line ?? lineStyle.line, alpha: style.alpha ?? lineStyle.alpha)
        let paths = ShapeFactory.subPaths(stroke)
        guard let edited = ShapeFactory.fromEditable(stroke, paths.enumerated().map { i, p in
            (parts.isEmpty && p.tag != VectorEdit.REGION) || parts.contains(i) ? applyStyle(p, style) : p
        }) else { return }
        let key = parts.sorted().map(String.init).joined(separator: ", ")
        store.commit(current.strokes.map { $0.id == stroke.id ? reordered(edited, $0.order) : $0 }, coalesce: "part-style-\(stroke.id)-[\(key)]")
        store.select([stroke.id])
    }

    func partStyleOf() -> PartStyle {
        guard let stroke = selectedShape, let spec = stroke.shape else { return PartStyle() }
        let paths = ShapeFactory.subPaths(stroke)
        let idx = parts.min() ?? -1
        guard let p = (paths.indices.contains(idx) ? paths[idx] : nil) ?? paths.first(where: { $0.label == nil }) else { return PartStyle() }
        let ws = p.widthScale ?? 1
        return PartStyle(strokeColorHex: p.strokeColorHex ?? spec.style.strokeColorHex,
                         fillColorHex: ShapeRenderer.resolveFill(p.fillColorHex, spec.style) ?? "",
                         widthScale: ws, line: ws <= 0 ? "none" : p.dotted ? "dotted" : p.dashed ? "dashed" : "solid", alpha: p.alpha ?? 1)
    }

    // MARK: Grafik düzenleme

    func dismissGraphEdit() { graphEdit = nil }

    private func graphTap(_ stroke: Stroke, _ point: P, _ tolerance: Float) {
        guard let spec = stroke.shape, let graph = spec.graph, let tag = graphTagAt(stroke, point, tolerance * 1.3) else { return }
        let params = GraphShapes.params(spec.kind, graph)
        let initial: String
        if tag.hasPrefix("tick:") {
            initial = Graphs.tickValue(params, String(tag.dropFirst(5)), aspect: aspectOf(stroke))
        } else if tag.hasPrefix("value:") {
            let vals = Graphs.values(params)
            initial = Int(tag.dropFirst(6)).flatMap { vals.indices.contains($0) ? formatNumber(vals[$0]) : nil } ?? ""
        } else if tag.hasPrefix("vlabel:") {
            initial = Int(tag.dropFirst(7)).flatMap { params.valueLabels[$0] } ?? ""
        } else if tag == "axis:x" { initial = params.xLabel }
        else if tag == "axis:y" { initial = params.yLabel }
        else if tag == "axis:z" { initial = params.zLabel }
        else if tag == "formula" {
            let blank = params.formula.trimmingCharacters(in: .whitespaces).isEmpty
            initial = params.freq > 1 ? Graphs.editableFormula(params) : (blank ? Graphs.formulaLabel(params) : params.formula)
        } else { return }
        graphEdit = GraphEditRequest(strokeId: stroke.id, tag: tag, initial: initial)
    }

    private func formatNumber(_ v: Float) -> String {
        if v == v.rounded() { return String(Int(v)) }
        var s = String(format: "%.2f", v).replacingOccurrences(of: ".", with: ",")
        while s.hasSuffix("0") { s.removeLast() }
        while s.hasSuffix(",") { s.removeLast() }
        return s
    }

    private func aspectOf(_ stroke: Stroke) -> Float {
        guard let b = ShapeEditor.partsBounds(stroke, []) ?? ElementOps.bounds(stroke) else { return 1 }
        return (b.width / max(b.height, 1e-3)).coerceIn(0.05, 20)
    }

    func graphTagAt(_ stroke: Stroke, _ point: P, _ tolerance: Float) -> String? {
        var best: String?
        var bestD = Float.greatestFiniteMagnitude
        for p in ShapeFactory.subPaths(stroke) {
            guard let tag = p.tag else { continue }
            let d: Float
            if let label = p.label {
                guard let a = p.points.first else { continue }
                let w = Float(label.count) * p.labelSize * 0.6
                d = FRect(a.x - tolerance, a.y - tolerance, a.x + w + tolerance, a.y + p.labelSize + tolerance).contains(point) ? 0 : .greatestFiniteMagnitude
            } else {
                let pts = p.closed && !p.points.isEmpty ? p.points + [p.points[0]] : p.points
                let poly = ElementOps.distanceToPolyline(point, pts.map { StrokePoint($0.x, $0.y) })
                d = p.closed && RegionFill.contains(p.points, point) ? 0 : poly
            }
            let weight = tag == "curve" || tag == "fill" || tag == "asymptote" ? d + tolerance * 0.8 : d
            if weight <= tolerance && weight < bestD { bestD = weight; best = tag }
        }
        guard let b = best, b != "curve", b != "fill", b != "asymptote", !b.hasPrefix("key"), !b.hasPrefix("angle") else { return nil }
        return b
    }

    func applyGraphEdit(_ text: String) {
        guard let req = graphEdit else { return }
        graphEdit = nil
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        updateGraph(req.strokeId) { p in
            var q = p
            if req.tag.hasPrefix("tick:") {
                return t.isEmpty ? Graphs.withoutTickLabel(p, String(req.tag.dropFirst(5))) : Graphs.withTickLabel(p, req.tag, t)
            } else if req.tag.hasPrefix("value:") {
                guard let v = Float(t.replacingOccurrences(of: ",", with: ".")), let i = Int(req.tag.dropFirst(6)) else { return p }
                return Graphs.withValue(p, i, v)
            } else if req.tag.hasPrefix("vlabel:"), let i = Int(req.tag.dropFirst(7)) {
                q.valueLabels[i] = t
            } else if req.tag == "axis:x" { q.xLabel = t }
            else if req.tag == "axis:y" { q.yLabel = t }
            else if req.tag == "axis:z" { q.zLabel = t }
            else if req.tag == "formula" {
                if t.isEmpty { q.formula = ""; q.freq = 1 } else { q.formula = t; q.preset = ""; q.freq = 1 }
            }
            return q
        }
    }

    func updateSelectedGraph(_ block: (GraphParams) -> GraphParams) {
        guard let stroke = selectedShape else { return }
        updateGraph(stroke.id, block)
    }

    func selectedIsGraph() -> Bool { selectedShape?.shape.map { GraphShapes.isGraph($0.kind) && $0.graph != nil } ?? false }

    func selectedGraphParams() -> GraphParams? {
        guard let spec = selectedShape?.shape, let g = spec.graph else { return nil }
        let kind = GraphShapes.upgradeGraphKind(spec.kind)
        guard GraphShapes.isGraph(kind) else { return nil }
        return GraphShapes.params(kind, g)
    }

    private func updateGraph(_ strokeId: String, _ block: (GraphParams) -> GraphParams) {
        guard let current = store.canvas, let stroke = current.strokes.first(where: { $0.id == strokeId }),
              var spec = stroke.shape, let g = spec.graph else { return }
        let kind = GraphShapes.upgradeGraphKind(spec.kind)
        let stored = GraphShapes.params(kind, g)
        var params = stored
        let paper = GraphPaper.normalize(current.pagePaperHex)
        params.paper = paper.isEmpty ? "#FFFFFF" : paper
        let next = block(params)
        if next == stored { return }
        let newSpec = GraphShapes.spec(next, g)
        spec.kind = kind
        var base = stroke; base.shape = spec
        guard let rebuilt = ShapeFactory.rebuild(base, style: spec.style, graph: newSpec) else { return }
        store.commit(current.strokes.map { $0.id == strokeId ? reordered(rebuilt, $0.order) : $0 }, coalesce: "graph-\(strokeId)")
        store.select([strokeId])
    }

    // MARK: Sürükleme

    private var dragGraph: VectorEdit.Graph?
    private var dragNodes: Set<Int> = []
    private var dragEdge: VectorEdit.Edge?
    private var dragBase: [EditablePath]?
    private var dragFrom: P?

    func beginDrag(_ point: P, tolerance: Float, handleDistance: Float) -> ShapeDrag {
        dragGraph = nil; dragNodes = []; dragEdge = nil; dragBase = nil; dragFrom = nil
        guard let stroke = selectedShape else { return .NONE }
        if tool == .PARAMS { return paramShapes.beginDrag(point, tolerance: tolerance) ? .HANDLE : .NONE }
        if tool == .GRAPH || tool == .FILL { return .NONE }
        let g = graphOf(stroke)
        dragGraph = g
        let sel = vector
        switch tool {
        case .NODES, .ADD_NODE:
            guard let node = g.nodeAt(point, tolerance * Self.NODE_HIT) else { return .NONE }
            let selected = selectedNodeIds(g, sel)
            if selected.contains(node.id) { dragNodes = selected } else {
                dragNodes = [node.id]
                setSelection(VectorSelection(nodes: [node.point]), stroke)
            }
            return .NODES
        case .CONNECT:
            let from = sel.linkFrom
            if g.nodeAt(point, tolerance * Self.NODE_HIT) == nil && g.edgeAt(point, tolerance * Self.EDGE_HIT) == nil &&
                (from == nil || (from! - point).distance > tolerance * Self.NODE_HIT) { return .NONE }
            let (paths, at, _) = anchor(g, point, tolerance)
            dragBase = paths
            dragFrom = at
            return .CONNECT
        case .LINES:
            let edge = selectedEdge(g, sel)
            if let edge {
                if (VectorEdit.turnHandle(edge, handleDistance) - point).distance <= tolerance * 2.2 { dragEdge = edge; return .TURN }
                if (VectorEdit.bendHandle(edge) - point).distance <= tolerance * 2 { dragEdge = edge; return .BEND }
            }
            guard let hit = g.edgeAt(point, tolerance * Self.EDGE_HIT) else { return .NONE }
            dragEdge = hit.edge
            if hit.edge.id != edge?.id { setSelection(VectorSelection(edge: (hit.edge.start, hit.edge.end)), stroke) }
            return .EDGE
        default:
            return .NONE
        }
    }

    private func turnDegrees(_ edge: VectorEdit.Edge, _ from: P, _ to: P) -> Float {
        let pivot = (edge.start + edge.end) / 2
        let a0 = atan2(from.y - pivot.y, from.x - pivot.x), a1 = atan2(to.y - pivot.y, to.x - pivot.x)
        var degrees = (a1 - a0) * 180 / .pi
        if degrees > 180 { degrees -= 360 }
        if degrees < -180 { degrees += 360 }
        let target = edge.angle() + degrees
        let snapped = (target / 15).rounded() * 15
        return abs(target - snapped) < 4 ? snapped - edge.angle() : degrees
    }

    private func dragOutcome(_ kind: ShapeDrag, _ from: P, _ to: P, _ tolerance: Float) -> ([EditablePath], VectorSelection)? {
        guard let g = dragGraph else { return nil }
        let d = to - from
        switch kind {
        case .NODES:
            if dragNodes.isEmpty { return nil }
            var deltas = [Int: P]()
            for n in dragNodes { deltas[n] = d }
            return (VectorEdit.moveNodes(g, deltas), VectorSelection(nodes: dragNodes.map { g.nodes[$0].point + d }))
        case .EDGE:
            guard let e = dragEdge else { return nil }
            var deltas = [Int: P]()
            deltas[e.a] = d; deltas[e.b] = d
            return (VectorEdit.moveNodes(g, deltas), VectorSelection(edge: (e.start + d, e.end + d)))
        case .BEND:
            guard let e = dragEdge else { return nil }
            return (VectorEdit.bendEdge(g, e, VectorEdit.bendHandle(e) + d), VectorSelection(edge: (e.start, e.end)))
        case .TURN:
            guard let e = dragEdge else { return nil }
            let degrees = turnDegrees(e, from, to)
            return (VectorEdit.rotateEdge(g, e, degrees), VectorSelection(edge: rotated(e, degrees)))
        case .CONNECT:
            guard let base = dragBase, let start = dragFrom, (to - start).distance > tolerance else { return nil }
            let (paths, at, _) = anchor(graphOf(base), to, tolerance, exclude: start)
            guard let connected = VectorEdit.connect(VectorEdit.graph(paths), start, at, style: withLineStyle) else { return nil }
            return (connected, VectorSelection(nodes: [at], linkFrom: at))
        case .NONE, .HANDLE:
            return nil
        }
    }

    private func rotated(_ e: VectorEdit.Edge, _ degrees: Float) -> (P, P) {
        let pivot = (e.start + e.end) / 2
        let rad = Double(degrees) * .pi / 180
        let c = Float(cos(rad)), s = Float(sin(rad))
        func rot(_ p: P) -> P { P(pivot.x + (p.x - pivot.x) * c - (p.y - pivot.y) * s, pivot.y + (p.x - pivot.x) * s + (p.y - pivot.y) * c) }
        return (rot(e.start), rot(e.end))
    }

    func dragPreview(_ kind: ShapeDrag, _ from: P, _ to: P, tolerance: Float) -> Stroke? {
        if kind == .HANDLE { return paramShapes.dragPreview(to) }
        guard let stroke = selectedShape, let outcome = dragOutcome(kind, from, to, tolerance) else { return nil }
        return ShapeFactory.fromEditable(stroke, outcome.0)
    }

    func endDrag(_ kind: ShapeDrag, _ from: P, _ to: P, tolerance: Float) {
        if kind == .HANDLE { paramShapes.endDrag(to); return }
        guard let stroke = selectedShape, let g = dragGraph else { return }
        let outcome = dragOutcome(kind, from, to, tolerance)
        dragGraph = nil
        guard let outcome else { return }
        commitPaths(stroke, g, outcome.0, outcome.1)
    }

    // MARK: Kes, sil, çizgi ayarları

    func canCut() -> Bool {
        guard let stroke = selectedShape else { return false }
        let g = graphOf(stroke)
        let ids = selectedNodeIds(g)
        if ids.isEmpty { return false }
        let (rest, piece) = VectorEdit.cutNodes(g, ids)
        return VectorEdit.drawable(rest) && VectorEdit.drawable(piece)
    }

    func cutSelected() {
        guard let stroke = selectedShape, var spec = stroke.shape, let current = store.canvas else { return }
        let g = graphOf(stroke)
        let ids = selectedNodeIds(g)
        if ids.isEmpty { return }
        let (rest, piece) = VectorEdit.cutNodes(g, ids)
        guard VectorEdit.drawable(rest), VectorEdit.drawable(piece), let kept = ShapeFactory.fromEditable(stroke, rest) else { return }
        spec.kind = InkFill.FREE_PATH; spec.graph = nil
        var free = stroke; free.shape = spec
        guard let cut = ShapeFactory.fromEditable(free, piece, id: UUID().uuidString.lowercased()) else { return }
        var updated: [Stroke] = []
        for s in current.strokes { if s.id == stroke.id { updated.append(kept); updated.append(cut) } else { updated.append(s) } }
        store.commit(updated.enumerated().map { i, s in s.order == i ? s : reordered(s, i) })
        reset()
        host.inserted([cut.id])
    }

    func canDelete() -> Bool { !vector.nodes.isEmpty || vector.edge != nil }

    func deleteSelected() {
        guard let stroke = selectedShape else { return }
        let g = graphOf(stroke)
        let paths: [EditablePath]
        if let edge = selectedEdge(g) { paths = VectorEdit.deleteEdge(g, edge) } else {
            let ids = selectedNodeIds(g)
            if ids.isEmpty { return }
            paths = VectorEdit.cutNodes(g, ids).0
        }
        commitPaths(stroke, g, paths, VectorSelection())
    }

    func selectedEdgeAngle() -> Float? { selectedShape.flatMap { selectedEdge(graphOf($0))?.angle() } }
    func selectedEdgeCurved() -> Bool { selectedShape.flatMap { selectedEdge(graphOf($0))?.curved } ?? false }

    func turnSelectedEdge(_ degrees: Float) { editSelectedEdge { g, e in (VectorEdit.rotateEdge(g, e, degrees), rotated(e, degrees)) } }

    func setSelectedEdgeAngle(_ target: Float) {
        editSelectedEdge { g, e in
            var diff = (target - e.angle()).truncatingRemainder(dividingBy: 180)
            if diff > 90 { diff -= 180 }
            if diff <= -90 { diff += 180 }
            return (VectorEdit.rotateEdge(g, e, diff), rotated(e, diff))
        }
    }

    func straightenSelectedEdge() { editSelectedEdge { g, e in (VectorEdit.straighten(g, e), (e.start, e.end)) } }

    private func editSelectedEdge(_ block: (VectorEdit.Graph, VectorEdit.Edge) -> ([EditablePath], (P, P))) {
        guard let stroke = selectedShape else { return }
        let g = graphOf(stroke)
        guard let edge = selectedEdge(g) else { return }
        let (paths, ends) = block(g, edge)
        commitPaths(stroke, g, paths, VectorSelection(edge: ends))
    }
}
