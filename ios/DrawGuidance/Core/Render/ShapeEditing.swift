import Foundation

/// Düzenleme sırasında bir alt yol (Android `ShapeFactory.EditablePath`).
struct EditablePath: Equatable {
    var points: [P]
    var closed = false
    var dashed = false
    var label: String? = nil
    var labelSize: Float = 0
    var strokeColorHex: String? = nil
    var fillColorHex: String? = nil
    var widthScale: Float? = nil
    var dotted = false
    var alpha: Float? = nil
    var tag: String? = nil
    /// v16 vektör noktaları (nokta sıraları).
    var nodes: [Int]? = nil
}

/// Android `ShapeFactory`'nin düzenleme işlevleri: yeniden kurma, kutu, alt yolları açma ve geri kurma.
extension ShapeFactory {
    /// v45 parametreli şeklin modeli (bilim kütüphanesi); bilim kütüphanesi taşınana kadar nil.
    static func paramModel(_ id: String) -> AnyObject? { nil }

    /// Grafiğin etiketi/formülü ya da biçimi değişince şekli, bulunduğu kutuda yeniden kurar.
    static func rebuild(_ stroke: Stroke, style: ShapeStyle, graph: GraphSpec?, params: [String: String]?? = nil) -> Stroke? {
        guard let spec = stroke.shape else { return nil }
        let kind = GraphShapes.upgradeGraphKind(spec.kind)
        if !canBuild(kind) { return nil }
        guard let box = frameOf(stroke, spec) else { return nil }
        let p: [String: String]? = params ?? spec.params
        guard var rebuilt = build(kind, box.topLeft, box.bottomRight, style: style, graph: graph, id: stroke.id, order: stroke.order,
                                  opacity: stroke.opacity, params: p) else { return nil }
        rebuilt.groupId = stroke.groupId
        rebuilt.clip = stroke.clip
        rebuilt.layerId = stroke.layerId ?? ""
        return rebuilt
    }

    private static func frameOf(_ stroke: Stroke, _ spec: ShapeSpec) -> FRect? {
        guard let sb = pointBounds(stroke) else { return nil }
        if sb.width < 2 || sb.height < 2 { return sb }
        guard let canon = build(spec.kind, P(0, 0), P(sb.width, sb.height), style: spec.style, graph: spec.graph, id: "frame", order: 0, params: spec.params),
              let cb = pointBounds(canon) else { return sb }
        let fx0 = cb.left / sb.width, fx1 = cb.right / sb.width
        let fy0 = cb.top / sb.height, fy1 = cb.bottom / sb.height
        let minPart: Float = paramModel(spec.kind) != nil ? 0.02 : 0.3
        if fx1 - fx0 < minPart || fy1 - fy0 < minPart { return sb }
        let bw = sb.width / (fx1 - fx0), bh = sb.height / (fy1 - fy0)
        let left = sb.left - fx0 * bw, top = sb.top - fy0 * bh
        return FRect(left, top, left + bw, top + bh)
    }

    /// v45 şeklin kurulduğu kutu.
    static func box(_ stroke: Stroke) -> FRect? { stroke.shape.flatMap { frameOf(stroke, $0) } }

    private static func pointBounds(_ stroke: Stroke) -> FRect? {
        if stroke.points.isEmpty { return nil }
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for p in stroke.points { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        return FRect(l, t, r, b)
    }

    /// Elemanın alt yollarını düzenlenebilir listeye açar.
    static func subPaths(_ stroke: Stroke) -> [EditablePath] {
        guard let spec = stroke.shape else { return [] }
        var out: [EditablePath] = []
        var start = 0
        for sub in spec.subpaths {
            let end = min(stroke.points.count, start + sub.count)
            if end > start {
                out.append(EditablePath(points: stroke.points[start..<end].map { P($0.x, $0.y) }, closed: sub.closed, dashed: sub.dashed,
                                        label: sub.label, labelSize: sub.labelSize, strokeColorHex: sub.strokeColorHex,
                                        fillColorHex: sub.fillColorHex, widthScale: sub.widthScale, dotted: sub.dotted, alpha: sub.alpha,
                                        tag: sub.tag, nodes: sub.nodes))
            }
            start = end
        }
        return out
    }

    /// Düzenlenen alt yollardan yeni bir şekil elemanı kurar (boş kalırsa nil).
    static func fromEditable(_ stroke: Stroke, _ paths: [EditablePath], id: String? = nil) -> Stroke? {
        guard let spec = stroke.shape else { return nil }
        let shapePaths: [ShapePath] = paths.compactMap { p in
            if let label = p.label {
                return ShapePath(points: Array(p.points.prefix(1)), label: label, labelSize: p.labelSize, strokeColorHex: p.strokeColorHex, alpha: p.alpha, tag: p.tag)
            }
            if p.tag == VNODE && p.points.count == 1 { return ShapePath(points: p.points, tag: VNODE) }
            if p.points.count >= 2 {
                return ShapePath(points: p.closed ? p.points + [p.points[0]] : p.points, closed: p.closed, dashed: p.dashed,
                                 strokeColorHex: p.strokeColorHex, fillColorHex: p.fillColorHex, widthScale: p.widthScale,
                                 dotted: p.dotted, alpha: p.alpha, tag: p.tag, nodes: p.nodes)
            }
            return nil
        }
        guard var s = fromPaths(spec.kind, shapePaths, style: spec.style, graph: spec.graph, id: id ?? stroke.id, order: stroke.order,
                                opacity: stroke.opacity, params: spec.params) else { return nil }
        s.groupId = stroke.groupId
        s.clip = stroke.clip
        s.layerId = stroke.layerId ?? ""
        return s
    }
}

/// v18 grafikler kağıt rengine uyar (Android `GraphPaper`).
enum GraphPaper {
    static func normalize(_ hex: String?) -> String {
        let h = (hex ?? "").trimmingCharacters(in: .whitespaces).uppercased()
        let valid = h.count == 7 && h.hasPrefix("#") && h.dropFirst().allSatisfy { $0.isHexDigit }
        return (h.isEmpty || h == "#FFFFFF" || !valid) ? "" : h
    }

    static func needsUpdate(_ stroke: Stroke, _ paper: String) -> Bool {
        guard let spec = stroke.shape, let graph = spec.graph else { return false }
        if !GraphShapes.isGraph(GraphShapes.upgradeGraphKind(spec.kind)) { return false }
        return normalize(graph.paper) != normalize(paper)
    }

    static func recolor(_ stroke: Stroke, _ paper: String) -> Stroke {
        guard needsUpdate(stroke, paper), let spec = stroke.shape, var graph = spec.graph else { return stroke }
        graph.paper = normalize(paper)
        guard var rebuilt = ShapeFactory.rebuild(stroke, style: spec.style, graph: graph) else { return stroke }
        rebuilt.order = stroke.order
        return rebuilt
    }

    static func recolor(_ pages: [[Stroke]], _ paper: String) -> [[Stroke]] {
        if !pages.contains(where: { $0.contains { needsUpdate($0, paper) } }) { return pages }
        return pages.map { page in page.contains { needsUpdate($0, paper) } ? page.map { recolor($0, paper) } : page }
    }
}
