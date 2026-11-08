import CoreGraphics
import Foundation

/// Şeklin bir parçası (tuval koordinatlarında; Android `ShapePath`).
struct ShapePath: Equatable {
    var points: [P]
    var closed = false
    var dashed = false
    /// Yazı çapası (eksen etiketi/formül): tek noktalık yol.
    var label: String? = nil
    var labelSize: Float = 0
    var strokeColorHex: String? = nil
    var fillColorHex: String? = nil
    var widthScale: Float? = nil
    var dotted = false
    var alpha: Float? = nil
    var tag: String? = nil
    var nodes: [Int]? = nil
}

/// Şeklin parmakla yerleştiriliş biçimi (Android `ShapeFit`).
enum ShapeFit { case BOX, SQUARE, LINE }

/// Bir kutu (Android `Rect`).
struct FRect: Equatable {
    var left, top, right, bottom: Float
    var width: Float { right - left }
    var height: Float { bottom - top }
}

/// Katalogdaki şekilleri tek bir vektör elemana ([BrushType.SHAPE]) çevirir (Android `ShapeFactory` ve
/// `ShapeRegistry`'nin kütüphane bölümü).
enum ShapeFactory {
    static let VNODE = "vnode"
    static let LIB_PREFIX = "lib."
    static let GENERAL_PREFIX = "gen."

    static func isLibrary(_ id: String) -> Bool { id.hasPrefix(LIB_PREFIX) }

    static func isGeneral(_ id: String) -> Bool {
        (id.hasPrefix(LIB_PREFIX) ? String(id.dropFirst(LIB_PREFIX.count)) : id).hasPrefix(GENERAL_PREFIX)
    }

    static func fit(_ id: String) -> ShapeFit {
        guard let lib = ShapeLibrary.shared.find(id) else { return .BOX }
        switch lib.fit {
        case .LINE: return .LINE
        case .SQUARE: return .SQUARE
        case .BOX: return .BOX
        }
    }

    static func aspect(_ id: String) -> Float {
        guard let a = ShapeLibrary.shared.find(id)?.aspect else { return 1 }
        return min(max(a, 0.2), 6)
    }

    /// Kütüphane şekilleri sürüklenirken oranını korur (genel şekiller hariç).
    private static func keepsAspect(_ id: String) -> Bool { isLibrary(id) && fit(id) != .LINE && !isGeneral(id) }

    /// Sürüklenen iki noktadan şeklin kutusu.
    static func frame(_ id: String, _ start: P, _ end: P) -> FRect {
        if !keepsAspect(id) && fit(id) != .SQUARE {
            return FRect(left: min(start.x, end.x), top: min(start.y, end.y), right: max(start.x, end.x), bottom: max(start.y, end.y))
        }
        let aspect: Float = fit(id) == .SQUARE ? 1 : self.aspect(id)
        let dx = end.x - start.x, dy = end.y - start.y
        let w = max(abs(dx), abs(dy) * aspect)
        let h = w / aspect
        let ex = start.x + (dx < 0 ? -w : w)
        let ey = start.y + (dy < 0 ? -h : h)
        return FRect(left: min(start.x, ex), top: min(start.y, ey), right: max(start.x, ex), bottom: max(start.y, ey))
    }

    /// Kütüphane şeklinin parçaları (tuval koordinatlarında).
    static func paths(_ id: String, _ start: P, _ end: P) -> [ShapePath] {
        guard let lib = ShapeLibrary.shared.find(id) else { return [] }
        if lib.fit == .LINE { return lineMapped(start, end, aspect: lib.aspect, paths: lib.paths) }
        return mapped(lib.paths, frame(id, start, end))
    }

    /// Birim kutudaki parçaları dikdörtgene eşler.
    static func mapped(_ paths: [VPath], _ rect: FRect) -> [ShapePath] {
        let w = max(rect.width, 1e-3), h = max(rect.height, 1e-3)
        let m = min(w, h)
        return paths.map { toShapePath($0, minSide: m) { P(rect.left + $0.x * w, rect.top + $0.y * h) } }
    }

    /// Çizgi gibi yerleşen şekiller: birim kutunun x ekseni başlangıçtan bitişe, y ekseni ona dik.
    static func lineMapped(_ start: P, _ end: P, aspect: Float, paths: [VPath]) -> [ShapePath] {
        let length = max(1e-3, hypotf(end.x - start.x, end.y - start.y))
        let ux = (end.x - start.x) / length, uy = (end.y - start.y) / length
        let nx = -uy, ny = ux
        let height = length / max(aspect, 0.2)
        return paths.map { v in
            toShapePath(v, minSide: height) { p in
                let a = p.x * length, b = (p.y - 0.5) * height
                return P(start.x + ux * a + nx * b, start.y + uy * a + ny * b)
            }
        }
    }

    private static func toShapePath(_ v: VPath, minSide: Float, map: (P) -> P) -> ShapePath {
        let pts = v.points.map(map)
        return ShapePath(
            points: (v.closed && v.label == nil && pts.count > 2) ? pts + [pts[0]] : pts,
            closed: v.closed,
            dashed: v.line == .DASHED,
            label: v.label,
            labelSize: v.labelSize * minSide,
            strokeColorHex: v.stroke,
            fillColorHex: v.fill,
            widthScale: v.width != 1 ? v.width : nil,
            dotted: v.line == .DOTTED,
            alpha: v.alpha < 0.999 ? v.alpha : nil,
            tag: v.tag
        )
    }

    /// Kayıttaki şekil kimliğiyle kurar (şimdilik kütüphane şekilleri).
    static func build(_ kind: String, _ start: P, _ end: P, style: ShapeStyle, graph: GraphSpec? = nil, id: String,
                      order: Int, opacity: Float = 1, params: [String: String]? = nil) -> Stroke? {
        fromPaths(kind, paths(kind, start, end), style: style, graph: graph, id: id, order: order, opacity: opacity, params: params)
    }

    static func fromPaths(_ kind: String, _ paths: [ShapePath], style: ShapeStyle, graph: GraphSpec?, id: String,
                          order: Int, opacity: Float = 1, params: [String: String]? = nil) -> Stroke? {
        var points: [StrokePoint] = []
        var subpaths: [SubPath] = []
        let q = ContentCodec.quantize
        for path in paths {
            let pts = path.points
            if let label = path.label {
                guard let f = pts.first else { continue }
                points.append(StrokePoint(q(f.x), q(f.y)))
                subpaths.append(SubPath(count: 1, label: label, labelSize: path.labelSize, strokeColorHex: path.strokeColorHex,
                                        alpha: path.alpha, tag: path.tag))
                continue
            }
            // v16: serbest vektör noktası
            if pts.count == 1 && path.tag == VNODE {
                points.append(StrokePoint(q(pts[0].x), q(pts[0].y)))
                subpaths.append(SubPath(count: 1, tag: VNODE, nodes: [0]))
                continue
            }
            if pts.count < 2 { continue }
            let closed = path.closed && pts[0].x == pts[pts.count - 1].x && pts[0].y == pts[pts.count - 1].y
            let body = closed ? Array(pts.dropLast()) : pts
            for p in body { points.append(StrokePoint(q(p.x), q(p.y))) }
            let nodes = path.nodes.map { Array(Set($0.filter { body.indices.contains($0) })).sorted() }
            subpaths.append(SubPath(count: body.count, closed: path.closed, dashed: path.dashed,
                                    strokeColorHex: path.strokeColorHex, fillColorHex: path.fillColorHex,
                                    widthScale: path.widthScale, dotted: path.dotted, alpha: path.alpha, tag: path.tag,
                                    nodes: (nodes?.isEmpty == false) ? nodes : nil))
        }
        if points.count < 2 { return nil }
        return Stroke(id: id, points: points, colorHex: style.strokeColorHex, brushType: .SHAPE, sizePx: style.strokeWidth,
                      opacity: opacity, order: order,
                      shape: ShapeSpec(kind: kind, subpaths: subpaths, style: style, graph: graph,
                                       params: (params?.isEmpty == false) ? params : nil))
    }

    /// Şeklin noktalarının sınırları.
    static func bounds(_ stroke: Stroke) -> FRect? {
        guard !stroke.points.isEmpty else { return nil }
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude
        var r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for p in stroke.points { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        return FRect(left: l, top: t, right: r, bottom: b)
    }
}
