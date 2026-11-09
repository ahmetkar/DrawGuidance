import Combine
import Foundation

/// Ok ekleme durumu (Android `DiagramLink`): [fromId] nil ise önce okun başlayacağı adım seçilir.
struct DiagramLink: Equatable {
    var fromId: String?
    var fromSide: DiagramSide = .AUTO
}

/// Diyagramlar: türü seçip yerleştirme ve tuvalin üstünde düzenleme (Android `DiagramController`).
@MainActor
final class DiagramController: ObservableObject {
    enum ColorPart { case FILL, STROKE, TEXT }
    private static let PORT_REACH: Float = 1.6

    private let store: EditorStore
    private unowned let host: EditorHost
    private let newShapeStyle: () -> ShapeStyle

    @Published private(set) var kind: DiagramKind = .FLOW
    @Published private(set) var isPickerOpen = false
    @Published private(set) var focus: DiagramFocus?
    @Published private(set) var link: DiagramLink?
    @Published private(set) var theme = DiagramThemes.EDITORIAL

    var linking: Bool { link != nil }

    init(_ store: EditorStore, _ host: EditorHost, newShapeStyle: @escaping () -> ShapeStyle) {
        self.store = store; self.host = host; self.newShapeStyle = newShapeStyle
    }

    var selected: Stroke? {
        let ids = store.selection
        guard ids.count == 1 else { return nil }
        return store.canvas?.strokes.first { ids.contains($0.id) && $0.diagram != nil }
    }

    func openPicker() { isPickerOpen = true }
    func closePicker() { isPickerOpen = false }

    func setTheme(_ value: String) {
        theme = value
        if let d = selected { update(d.id) { s in var n = s; n.theme = value; return n } }
    }

    func selectKind(_ value: DiagramKind) {
        kind = value
        isPickerOpen = false
        host.startAction(.DIAGRAM)
    }

    private static func newId(_ count: Int) -> String { "n\(count + 1)-\(UUID().uuidString.lowercased().prefix(4))" }

    func add(_ gestureId: String, _ start: P, _ end: P) {
        guard let current = store.canvas else { return }
        let minSide = Float(min(current.width, current.height))
        var left = min(start.x, end.x), top = min(start.y, end.y), right = max(start.x, end.x), bottom = max(start.y, end.y)
        if right - left < minSide * 0.2 || bottom - top < minSide * 0.2 {
            let aspect = DiagramCatalog.aspect(kind)
            let w = aspect.map { min(Float(current.width) * 0.86, minSide * 0.62 * sqrt($0)) } ?? minSide * 0.6
            let h = aspect.map { w / $0 } ?? minSide * 0.7
            let cx = (left + right) / 2, cy = (top + bottom) / 2
            left = cx - w / 2; right = cx + w / 2; top = cy - h / 2; bottom = cy + h / 2
        }
        let nodes = DiagramRenderer.layout(kind, DiagramRenderer.defaultNodes(kind))
        let spec = DiagramSpec(kind: kind, nodes: nodes, edges: DiagramRenderer.defaultEdges(kind, nodes), style: newShapeStyle(),
                               manual: DiagramCatalog.template(kind)?.manual ?? false, theme: theme)
        let id = StableId.from("\(gestureId)-diagram")
        let stroke = Stroke(id: id, points: [StrokePoint(left, top), StrokePoint(right, top), StrokePoint(right, bottom), StrokePoint(left, bottom)],
                            colorHex: spec.style.strokeColorHex, brushType: .DIAGRAM, sizePx: spec.style.strokeWidth, opacity: 1,
                            order: current.strokes.count, diagram: spec)
        store.commit(current.strokes + [stroke])
        host.inserted([id])
    }

    func addAt(_ gestureId: String, _ center: P) { add(gestureId, center, center) }

    func update(_ strokeId: String, coalesce: String? = nil, _ block: (DiagramSpec) -> DiagramSpec) {
        guard let current = store.canvas else { return }
        var changed = false
        let updated = current.strokes.map { s -> Stroke in
            guard s.id == strokeId, let spec = s.diagram else { return s }
            let next = block(spec)
            if next == spec { return s }
            changed = true
            var c = s; c.diagram = next; return c
        }
        if changed { store.commit(updated, coalesce: coalesce) }
    }

    func addNode(_ strokeId: String, _ text: String) {
        update(strokeId) { spec in
            let last = spec.nodes.last
            let blank = text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            let node = DiagramNode(id: Self.newId(spec.nodes.count),
                                   text: blank ? DiagramCatalog.newNodeText(spec.kind, spec.nodes.count + 1, last?.text) : text,
                                   x: 0.5, y: 0.5, w: 0.3, h: 0.12, box: last?.box ?? .ROUNDED, role: DiagramCatalog.inheritRole(last?.role))
            var n = spec; n.nodes = DiagramRenderer.layout(spec.kind, spec.nodes + [node]); return n
        }
    }

    func addEdge(_ strokeId: String, _ from: String, _ to: String, label: String = "", fromSide: DiagramSide = .AUTO, toSide: DiagramSide = .AUTO) {
        update(strokeId) { spec in
            if from == to || !spec.nodes.contains(where: { $0.id == from }) || !spec.nodes.contains(where: { $0.id == to }) { return spec }
            var n = spec
            if let existing = spec.edges.firstIndex(where: { $0.from == from && $0.to == to }) {
                n.edges[existing].fromSide = fromSide; n.edges[existing].toSide = toSide
            } else {
                n.edges.append(DiagramEdge(from: from, to: to, label: label, fromSide: fromSide, toSide: toSide))
            }
            return n
        }
    }

    func removeNode(_ strokeId: String, _ nodeId: String) {
        update(strokeId) { spec in
            if spec.nodes.count <= 1 { return spec }
            let remaining = spec.nodes.filter { $0.id != nodeId }
            let incoming = spec.edges.filter { $0.to == nodeId && $0.from != nodeId }
            let outgoing = spec.edges.filter { $0.from == nodeId && $0.to != nodeId }
            var edges = spec.edges.filter { $0.from != nodeId && $0.to != nodeId }
            if incoming.count == 1 && outgoing.count == 1 {
                let a = incoming[0], b = outgoing[0]
                if a.from != b.to && !edges.contains(where: { $0.from == a.from && $0.to == b.to }) {
                    var e = a; e.to = b.to; e.toSide = b.toSide
                    edges.append(e)
                }
            }
            var n = spec
            n.nodes = spec.manual ? remaining : DiagramRenderer.layout(spec.kind, remaining)
            n.edges = edges
            return n
        }
    }

    func removeEdge(_ strokeId: String, _ index: Int) {
        update(strokeId) { spec in
            guard spec.edges.indices.contains(index) else { return spec }
            var n = spec; n.edges.remove(at: index); return n
        }
    }

    func renameNode(_ strokeId: String, _ nodeId: String, _ text: String) {
        update(strokeId, coalesce: "diagram-text-\(strokeId)-\(nodeId)") { spec in
            let nodes = spec.nodes.map { n -> DiagramNode in var c = n; if n.id == nodeId { c.text = text }; return c }
            var n = spec
            n.nodes = DiagramCatalog.isData(spec.kind) && !spec.manual ? DiagramRenderer.layout(spec.kind, nodes) : nodes
            return n
        }
    }

    private var focusedNodeId: String? { if case .node(let id) = focus { return id }; return nil }
    private var focusedEdgeIndex: Int? { if case .edge(let i) = focus { return i }; return nil }

    func toggleFocusedFocal() {
        guard let stroke = selected, let fid = focusedNodeId else { return }
        update(stroke.id) { spec in
            var n = spec
            n.nodes = spec.nodes.map { node in
                if node.id != fid || DiagramCatalog.isStructural(node.role) { return node }
                var c = node; c.role = node.role == "focal" ? nil : "focal"; return c
            }
            return n
        }
    }

    func focusedFocal() -> Bool? {
        guard let spec = selected?.diagram, let fid = focusedNodeId, let node = spec.nodes.first(where: { $0.id == fid }) else { return nil }
        return DiagramCatalog.isStructural(node.role) ? nil : node.role == "focal"
    }

    func setNodeBox(_ strokeId: String, _ nodeId: String, _ box: DiagramBox) {
        update(strokeId) { spec in
            var n = spec
            n.nodes = spec.nodes.map { node in var c = node; if node.id == nodeId { c.box = box }; return c }
            return n
        }
    }

    // MARK: Tuval üstünde düzenleme

    func editSelected() {
        guard let diagram = store.selectedStroke({ $0.brushType == .DIAGRAM }) else { return }
        reset()
        host.startAction(.DIAGRAM_EDIT, target: [diagram.id])
    }

    func onModeChange(_ to: EditMode) { if to != .DIAGRAM_EDIT { reset() } }

    func reset() { focus = nil; link = nil }

    func onContentReplaced() {
        reset()
        if host.mode == .DIAGRAM_EDIT, let current = store.canvas {
            if !current.strokes.contains(where: { store.selection.contains($0.id) && $0.diagram != nil }) { host.endAction() }
        }
    }

    private func handleNodeId() -> String? { link?.fromId ?? focusedNodeId }

    func hasHandleAt(_ point: P, tolerance: Float) -> Bool {
        guard let stroke = selected, let id = handleNodeId() else { return false }
        return DiagramRenderer.portAt(stroke, point, tolerance * Self.PORT_REACH, only: id) != nil
    }

    func tap(_ point: P, tolerance: Float) {
        guard let stroke = selected else { return }
        let reach = tolerance * Self.PORT_REACH
        if let l = link {
            guard let sourceId = l.fromId else {
                guard let (node, side) = attachAt(stroke, point, reach) else { link = nil; return }
                focus = .node(node.id)
                link = DiagramLink(fromId: node.id, fromSide: side)
                return
            }
            let target = attachAt(stroke, point, reach)
            if let t = target {
                if t.0.id == sourceId { link?.fromSide = t.1 } else { connect(stroke, sourceId, l.fromSide, t.0, t.1) }
            } else if insideDiagram(stroke, point) {
                connectToNewNode(stroke, sourceId, l.fromSide, point)
            } else {
                link = nil
            }
            return
        }
        if let fid = focusedNodeId, let port = DiagramRenderer.portAt(stroke, point, reach, only: fid) {
            link = DiagramLink(fromId: fid, fromSide: port.1)
            return
        }
        if let node = DiagramRenderer.nodeAt(stroke, point) { focus = .node(node.id); return }
        focus = DiagramRenderer.edgeAt(stroke, point, tolerance * 1.6).map { .edge($0) }
    }

    private func attachAt(_ stroke: Stroke, _ point: P, _ reach: Float, exclude: String? = nil) -> (DiagramNode, DiagramSide)? {
        if let node = DiagramRenderer.nodeAt(stroke, point), node.id != exclude {
            return (node, DiagramRenderer.sideNear(stroke, node, point))
        }
        guard let port = DiagramRenderer.portAt(stroke, point, reach), port.0.id != exclude else { return nil }
        return port
    }

    func dragLink(_ from: P, _ to: P, tolerance: Float) {
        guard let stroke = selected, let sourceId = handleNodeId(),
              let port = DiagramRenderer.portAt(stroke, from, tolerance * Self.PORT_REACH, only: sourceId) else { return }
        guard let target = attachAt(stroke, to, tolerance * Self.PORT_REACH, exclude: sourceId) else {
            if DiagramRenderer.nodeAt(stroke, to) == nil { connectToNewNode(stroke, sourceId, port.1, to) } else { link = nil }
            return
        }
        connect(stroke, sourceId, port.1, target.0, target.1)
    }

    private func insideDiagram(_ stroke: Stroke, _ point: P) -> Bool {
        guard let f = stroke.diagram?.frame, let u = DiagramRenderer.toUnit(stroke, point) else { return false }
        return u.x >= f.left && u.x <= f.right && u.y >= f.top && u.y <= f.bottom
    }

    private func connectToNewNode(_ stroke: Stroke, _ sourceId: String, _ fromSide: DiagramSide, _ point: P) {
        link = nil
        guard let current = store.canvas, let spec = stroke.diagram, let source = spec.nodes.first(where: { $0.id == sourceId }) else { return }
        let clamped = P(point.x.coerceIn(0, Float(current.width)), point.y.coerceIn(0, Float(current.height)))
        guard let center = DiagramRenderer.toUnit(stroke, clamped) else { return }
        let id = Self.newId(spec.nodes.count)
        let node = DiagramNode(id: id, text: I18n.trf("Adım {0}", spec.nodes.count + 1), x: center.x - source.w / 2, y: center.y - source.h / 2,
                               w: source.w, h: source.h, box: source.box == .PILL || source.box == .DIAMOND ? .ROUNDED : source.box)
        var next = spec
        next.nodes.append(node)
        next.edges.append(DiagramEdge(from: sourceId, to: id, fromSide: fromSide))
        next.manual = true
        commitGrown(stroke, next, node)
        focus = .node(id)
    }

    private func commitGrown(_ stroke: Stroke, _ next: DiagramSpec, _ node: DiagramNode) {
        guard let current = store.canvas else { return }
        var updated = stroke
        if let grown = DiagramFrames.grow(stroke.points, next, left: node.x, top: node.y, right: node.x + node.w, bottom: node.y + node.h) {
            updated.points = grown.0.map { StrokePoint(ContentCodec.quantize($0.x), ContentCodec.quantize($0.y)) }
            updated.diagram = grown.1
        } else {
            updated.diagram = next
        }
        store.commit(current.strokes.map { $0.id == stroke.id ? updated : $0 })
    }

    private func connect(_ stroke: Stroke, _ sourceId: String, _ fromSide: DiagramSide, _ target: DiagramNode, _ toSide: DiagramSide) {
        link = nil
        addEdge(stroke.id, sourceId, target.id, fromSide: fromSide, toSide: toSide)
        let index = selected?.diagram?.edges.firstIndex { $0.from == sourceId && $0.to == target.id }
        focus = index.map { .edge($0) } ?? .node(target.id)
    }

    func addStep() {
        guard let stroke = selected, let current = stroke.diagram else { return }
        let focusedEdge = focusedEdgeIndex.flatMap { current.edges.indices.contains($0) ? current.edges[$0] : nil }
        let focusId = focusedEdge?.from ?? focusedNodeId ?? current.nodes.last?.id
        let id = Self.newId(current.nodes.count)
        link = nil
        let at = current.nodes.firstIndex { $0.id == focusId }.map { $0 + 1 } ?? current.nodes.count
        let focused = current.nodes.first { $0.id == focusId }
        let box = focused?.box ?? .ROUNDED
        let newBox: DiagramBox
        switch box {
        case .PILL, .DIAMOND: newBox = .ROUNDED
        case .GROUP, .LANE, .COLUMN: newBox = current.kind == .KANBAN || current.kind == .STORY_MAP ? .CARD : .ROUNDED
        default: newBox = box
        }
        let node = DiagramNode(id: id, text: DiagramCatalog.newNodeText(current.kind, current.nodes.count + 1, focused?.text),
                               x: 0.5, y: 0.5, w: 0.3, h: 0.12, box: newBox, role: DiagramCatalog.inheritRole(focused?.role))
        let placed = current.manual ? placeNear(current, node, focused) : node
        var nodes = current.nodes
        nodes.insert(placed, at: at)
        var edges = current.edges
        if let fid = focusId, !DiagramCatalog.noEdgeKinds.contains(current.kind), !(node.box == .CARD && current.kind == .KANBAN) {
            let outgoing: [Int] = focusedEdge != nil ? [focusedEdgeIndex!] : current.edges.indices.filter { current.edges[$0].from == fid }
            if outgoing.count == 1 {
                let k = outgoing[0]
                let next = current.edges[k]
                edges[k].to = id; edges[k].toSide = .AUTO
                var tail = next; tail.from = id; tail.label = ""; tail.fromSide = .AUTO
                edges.append(tail)
            } else {
                edges.append(DiagramEdge(from: fid, to: id))
            }
        }
        if current.manual {
            var n = current; n.nodes = nodes; n.edges = edges
            commitGrown(stroke, n, placed)
        } else {
            update(stroke.id) { s in var n = s; n.nodes = DiagramRenderer.layout(s.kind, nodes); n.edges = edges; return n }
        }
        focus = .node(id)
    }

    private func placeNear(_ spec: DiagramSpec, _ node: DiagramNode, _ near: DiagramNode?) -> DiagramNode {
        guard let ref = near ?? spec.nodes.last else { return node }
        if ref.box == .GROUP || ref.box == .LANE || ref.box == .COLUMN, let inside = placeInside(spec, node, ref) { return inside }
        let w = ref.w, h = ref.h
        let candidates: [(Float, Float)] = [(ref.x, ref.y + h * 1.8), (ref.x + w * 1.4, ref.y), (ref.x - w * 1.4, ref.y),
                                            (ref.x, ref.y - h * 1.8), (ref.x + w * 1.4, ref.y + h * 1.8)]
        func overlaps(_ x: Float, _ y: Float) -> Bool {
            spec.nodes.contains { o in x < o.x + o.w + 0.01 && x + w > o.x - 0.01 && y < o.y + o.h + 0.01 && y + h > o.y - 0.01 }
        }
        let (x, y) = candidates.first { !overlaps($0.0, $0.1) } ?? candidates[0]
        var c = node; c.x = x; c.y = y; c.w = w; c.h = h
        return c
    }

    private func placeInside(_ spec: DiagramSpec, _ node: DiagramNode, _ box: DiagramNode) -> DiagramNode? {
        let containers: Set<DiagramBox> = [.GROUP, .LANE, .COLUMN]
        let others = spec.nodes.filter { !containers.contains($0.box) }
        let ws = others.map(\.w).sorted(), hs = others.map(\.h).sorted()
        let w = max(min(ws.isEmpty ? 0.2 : ws[others.count / 2], box.w - 0.02), 0.08)
        let h = max(min(hs.isEmpty ? 0.12 : hs[others.count / 2], box.h - 0.02), 0.05)
        let pad: Float = 0.01
        let startX = box.box == .LANE ? box.x + box.w * 0.16 : box.x + pad
        let startY = box.y + (box.box == .LANE ? (box.h - h) / 2 : 0.11 * min(box.h, 1))
        func free(_ x: Float, _ y: Float) -> Bool {
            !others.contains { o in x < o.x + o.w + 0.008 && x + w > o.x - 0.008 && y < o.y + o.h + 0.008 && y + h > o.y - 0.008 }
        }
        var y = startY
        while y + h <= box.y + box.h + 1e-4 {
            var x = startX
            while x + w <= box.x + box.w + 1e-4 {
                if free(x, y) { var c = node; c.x = x; c.y = y; c.w = w; c.h = h; return c }
                x += w + 0.015
            }
            y += h + 0.02
        }
        return nil
    }

    func setFocusedColor(_ part: ColorPart, _ hex: String?) {
        guard let stroke = selected else { return }
        switch focus {
        case .node(let fid):
            update(stroke.id, coalesce: "diagram-color-\(stroke.id)-\(fid)-\(part)") { spec in
                var n = spec
                n.nodes = spec.nodes.map { node in
                    guard node.id == fid else { return node }
                    var c = node
                    switch part {
                    case .FILL: c.fillColorHex = hex
                    case .STROKE: c.strokeColorHex = hex
                    case .TEXT: c.textColorHex = hex
                    }
                    return c
                }
                return n
            }
        case .edge(let index):
            update(stroke.id, coalesce: "diagram-color-\(stroke.id)-e\(index)") { spec in
                guard spec.edges.indices.contains(index) else { return spec }
                var n = spec; n.edges[index].colorHex = hex; return n
            }
        case nil: break
        }
    }

    func focusedColor(_ part: ColorPart) -> String? {
        guard let spec = selected?.diagram else { return nil }
        switch focus {
        case .node(let fid):
            guard let n = spec.nodes.first(where: { $0.id == fid }) else { return nil }
            switch part {
            case .FILL: return n.fillColorHex ?? spec.style.fillColorHex ?? "#FFFFFF"
            case .STROKE: return n.strokeColorHex ?? spec.style.strokeColorHex
            case .TEXT: return n.textColorHex ?? n.strokeColorHex ?? spec.style.strokeColorHex
            }
        case .edge(let i):
            return spec.edges.indices.contains(i) ? spec.edges[i].colorHex ?? spec.style.strokeColorHex : nil
        case nil: return nil
        }
    }

    func startLink() {
        if link != nil { link = nil; return }
        let fid = focusedNodeId
        if fid == nil { focus = nil }
        link = DiagramLink(fromId: fid)
    }

    func renameFocused(_ text: String) {
        guard let stroke = selected else { return }
        switch focus {
        case .node(let fid): renameNode(stroke.id, fid, text)
        case .edge(let index):
            update(stroke.id, coalesce: "diagram-edge-\(stroke.id)-\(index)") { spec in
                guard spec.edges.indices.contains(index) else { return spec }
                var n = spec; n.edges[index].label = text; return n
            }
        case nil: break
        }
    }

    func setFocusedBox(_ box: DiagramBox) {
        guard let stroke = selected, let fid = focusedNodeId else { return }
        setNodeBox(stroke.id, fid, box)
    }

    func setFocusedEdgeSides(fromSide: DiagramSide? = nil, toSide: DiagramSide? = nil) {
        guard let stroke = selected, let index = focusedEdgeIndex else { return }
        update(stroke.id) { spec in
            guard spec.edges.indices.contains(index) else { return spec }
            var n = spec
            if let fromSide { n.edges[index].fromSide = fromSide }
            if let toSide { n.edges[index].toSide = toSide }
            return n
        }
    }

    func flipFocusedEdge() {
        guard let stroke = selected, let index = focusedEdgeIndex else { return }
        update(stroke.id) { spec in
            guard spec.edges.indices.contains(index) else { return spec }
            let e = spec.edges[index]
            if spec.edges.contains(where: { $0.from == e.to && $0.to == e.from }) { return spec }
            var n = spec
            n.edges[index].from = e.to; n.edges[index].to = e.from
            n.edges[index].fromSide = e.toSide; n.edges[index].toSide = e.fromSide
            return n
        }
    }

    func deleteFocused() {
        guard let stroke = selected else { return }
        switch focus {
        case .node(let fid): removeNode(stroke.id, fid)
        case .edge(let index): removeEdge(stroke.id, index)
        case nil: return
        }
        reset()
    }
}
