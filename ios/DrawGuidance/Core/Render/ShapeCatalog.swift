import Foundation

/// Yerleşik şekiller (Android `ShapeKind`): kimlik kayıtta adıyla saklanır.
enum ShapeKind: String, CaseIterable {
    // Temel
    case LINE, ARROW, DOUBLE_ARROW
    case CIRCLE, ELLIPSE, SQUARE, RECTANGLE, ROUNDED_RECT
    case TRIANGLE, RIGHT_TRIANGLE, EQUILATERAL_TRIANGLE
    case DIAMOND, PARALLELOGRAM, TRAPEZOID
    case PENTAGON, HEXAGON, OCTAGON
    case STAR, HEART, SPIRAL
    case SEMICIRCLE, CIRCLE_RADIUS
    case SECTOR, ARC, ANNULUS, KITE
    case RIGHT_TRAPEZOID, TRIANGLE_HEIGHT, CROSS, ARROW_BLOCK
    // Manzara
    case SUN, MOON, CLOUD, MOUNTAINS, HILLS
    case WAVES, TREE, PINE, FLOWER, LEAF
    case BIRD, HOUSE, DROP
    case BUTTERFLY, FISH, PALM, GRASS, ROCK
    case BOAT, APPLE
    // Portre
    case FACE_GUIDE, EYE, LIPS, NOSE
    // Geometri
    case ANGLE, RIGHT_ANGLE, PARALLEL_LINES
    case CUBE, PRISM, CYLINDER, CONE, SPHERE, PYRAMID
    case TRIANGULAR_PRISM, HEXAGONAL_PRISM, TETRAHEDRON
    case TRUNCATED_CONE, HEMISPHERE, CUBE_NET
    // Grafik
    case AXES, NUMBER_LINE, GRID, PARABOLA, SINE
    case AXES_3D, FUNCTION_PLOT, COORD_GRID, UNIT_CIRCLE
    case BAR_CHART, LINE_CHART, PIE_CHART, SCATTER
    // v21 gölgeli 3B cisimler
    case SOLID_CUBE, SOLID_BOX, SOLID_CYLINDER, SOLID_CONE
    case SOLID_SPHERE, SOLID_DOME, SOLID_PYRAMID, SOLID_PRISM
    case SOLID_HEX_PRISM

    var fit: ShapeFit {
        switch self {
        case .LINE, .ARROW, .DOUBLE_ARROW, .NUMBER_LINE: return .LINE
        case .CIRCLE, .SQUARE, .EQUILATERAL_TRIANGLE, .PENTAGON, .HEXAGON, .OCTAGON, .STAR, .HEART, .SPIRAL,
             .CIRCLE_RADIUS, .SECTOR, .ARC, .ANNULUS, .CROSS, .SUN, .MOON, .FLOWER, .APPLE, .CUBE, .SPHERE,
             .TETRAHEDRON, .AXES_3D, .UNIT_CIRCLE, .PIE_CHART, .SOLID_CUBE, .SOLID_SPHERE:
            return .SQUARE
        default: return .BOX
        }
    }

    static let solids: [ShapeKind] = [.SOLID_CUBE, .SOLID_BOX, .SOLID_CYLINDER, .SOLID_CONE, .SOLID_SPHERE,
                                      .SOLID_DOME, .SOLID_PYRAMID, .SOLID_PRISM, .SOLID_HEX_PRISM]
    static let legacyGraphs: Set<ShapeKind> = [.AXES_3D, .FUNCTION_PLOT, .COORD_GRID, .UNIT_CIRCLE, .BAR_CHART,
                                               .LINE_CHART, .PIE_CHART, .SCATTER]
}

/// Şekil seçicideki yerleşik kategoriler (Android `ShapeCategory`).
enum ShapeCategory: String, CaseIterable {
    case ART_BASIC, ART_LANDSCAPE, ART_PORTRAIT, NOTE_LINES, NOTE_PLANE, NOTE_SOLIDS, NOTE_GRAPHS, ART_GENERAL
    case SIMPLE_ART_BASIC, SIMPLE_ART_NATURE, SIMPLE_ART_THINGS, SIMPLE_NOTE_BASIC, SIMPLE_NOTE_GEOMETRY, SIMPLE_NOTE_SOLIDS

    var kind: CanvasKind {
        switch self {
        case .NOTE_LINES, .NOTE_PLANE, .NOTE_SOLIDS, .NOTE_GRAPHS, .SIMPLE_NOTE_BASIC, .SIMPLE_NOTE_GEOMETRY, .SIMPLE_NOTE_SOLIDS: return .NOTES
        default: return .ART
        }
    }

    var listed: Bool {
        switch self {
        case .ART_BASIC, .NOTE_LINES, .NOTE_PLANE, .NOTE_SOLIDS, .NOTE_GRAPHS: return true
        default: return false
        }
    }

    /// Android metin kaynağının adı (`shape_cat_*`).
    var titleKey: String {
        switch self {
        case .ART_BASIC, .ART_GENERAL: return "shape_cat_basic"
        case .ART_LANDSCAPE: return "shape_cat_landscape"
        case .ART_PORTRAIT: return "shape_cat_portrait"
        case .NOTE_LINES: return "shape_cat_lines"
        case .NOTE_PLANE: return "shape_cat_plane"
        case .NOTE_SOLIDS: return "shape_cat_solids"
        case .NOTE_GRAPHS: return "shape_cat_graphs"
        case .SIMPLE_ART_BASIC, .SIMPLE_NOTE_BASIC: return "shape_cat_simple"
        case .SIMPLE_ART_NATURE: return "shape_cat_simple_nature"
        case .SIMPLE_ART_THINGS: return "shape_cat_simple_things"
        case .SIMPLE_NOTE_GEOMETRY: return "shape_cat_simple_geometry"
        case .SIMPLE_NOTE_SOLIDS: return "shape_cat_simple_solids"
        }
    }

    var shapes: [ShapeKind] {
        switch self {
        case .ART_BASIC:
            return [.LINE, .ARROW, .CIRCLE, .ELLIPSE, .SQUARE, .RECTANGLE, .ROUNDED_RECT, .TRIANGLE, .DIAMOND, .PENTAGON,
                    .HEXAGON, .STAR, .HEART, .SPIRAL, .CROSS, .ARROW_BLOCK] + ShapeKind.solids
        case .ART_LANDSCAPE:
            return [.SUN, .MOON, .CLOUD, .MOUNTAINS, .HILLS, .WAVES, .TREE, .PINE, .PALM, .GRASS, .FLOWER, .LEAF, .APPLE,
                    .BIRD, .BUTTERFLY, .FISH, .HOUSE, .BOAT, .ROCK, .DROP]
        case .ART_PORTRAIT: return [.FACE_GUIDE, .EYE, .LIPS, .NOSE]
        case .NOTE_LINES: return [.LINE, .ARROW, .DOUBLE_ARROW, .ANGLE, .RIGHT_ANGLE, .PARALLEL_LINES]
        case .NOTE_PLANE:
            return [.TRIANGLE, .RIGHT_TRIANGLE, .EQUILATERAL_TRIANGLE, .SQUARE, .RECTANGLE, .PARALLELOGRAM, .TRAPEZOID,
                    .DIAMOND, .PENTAGON, .HEXAGON, .OCTAGON, .CIRCLE, .CIRCLE_RADIUS, .SEMICIRCLE, .ELLIPSE, .SECTOR, .ARC,
                    .ANNULUS, .KITE, .RIGHT_TRAPEZOID, .TRIANGLE_HEIGHT, .CROSS]
        case .NOTE_SOLIDS:
            return [.CUBE, .PRISM, .TRIANGULAR_PRISM, .HEXAGONAL_PRISM, .CYLINDER, .CONE, .TRUNCATED_CONE, .SPHERE,
                    .HEMISPHERE, .PYRAMID, .TETRAHEDRON, .CUBE_NET] + ShapeKind.solids
        case .NOTE_GRAPHS:
            return [.AXES, .FUNCTION_PLOT, .COORD_GRID, .AXES_3D, .UNIT_CIRCLE, .NUMBER_LINE, .GRID, .PARABOLA, .SINE,
                    .BAR_CHART, .LINE_CHART, .PIE_CHART, .SCATTER]
        case .ART_GENERAL:
            return [.LINE, .ARROW, .DOUBLE_ARROW, .CIRCLE, .ELLIPSE, .SQUARE, .RECTANGLE, .ROUNDED_RECT, .TRIANGLE,
                    .RIGHT_TRIANGLE, .DIAMOND, .PENTAGON, .HEXAGON, .HEART]
        case .SIMPLE_ART_BASIC:
            return [.LINE, .ARROW, .DOUBLE_ARROW, .CIRCLE, .ELLIPSE, .SQUARE, .RECTANGLE, .ROUNDED_RECT, .TRIANGLE, .DIAMOND,
                    .PENTAGON, .HEXAGON, .STAR, .HEART, .SPIRAL, .CROSS, .ARROW_BLOCK, .SEMICIRCLE]
        case .SIMPLE_ART_NATURE:
            return [.SUN, .MOON, .CLOUD, .DROP, .MOUNTAINS, .HILLS, .WAVES, .TREE, .PINE, .PALM, .GRASS, .FLOWER, .LEAF, .ROCK]
        case .SIMPLE_ART_THINGS:
            return [.BIRD, .BUTTERFLY, .FISH, .APPLE, .HOUSE, .BOAT, .SOLID_CUBE, .SOLID_SPHERE, .SOLID_CYLINDER, .SOLID_CONE]
        case .SIMPLE_NOTE_BASIC:
            return [.LINE, .ARROW, .DOUBLE_ARROW, .CIRCLE, .ELLIPSE, .SQUARE, .RECTANGLE, .ROUNDED_RECT, .TRIANGLE, .DIAMOND,
                    .STAR, .CROSS, .ARROW_BLOCK]
        case .SIMPLE_NOTE_GEOMETRY:
            return [.RIGHT_TRIANGLE, .EQUILATERAL_TRIANGLE, .TRIANGLE_HEIGHT, .PARALLELOGRAM, .TRAPEZOID, .RIGHT_TRAPEZOID,
                    .KITE, .PENTAGON, .HEXAGON, .OCTAGON, .SEMICIRCLE, .CIRCLE_RADIUS, .SECTOR, .ARC, .ANGLE, .RIGHT_ANGLE,
                    .PARALLEL_LINES]
        case .SIMPLE_NOTE_SOLIDS:
            return [.CUBE, .PRISM, .TRIANGULAR_PRISM, .CYLINDER, .CONE, .SPHERE, .HEMISPHERE, .PYRAMID, .CUBE_NET, .NUMBER_LINE, .GRID]
        }
    }

    static func forKind(_ kind: CanvasKind) -> [ShapeCategory] {
        switch kind {
        case .SIMPLE_ART, .ART: return [.ART_GENERAL]
        case .SIMPLE_NOTES: return [.SIMPLE_NOTE_BASIC, .SIMPLE_NOTE_GEOMETRY, .SIMPLE_NOTE_SOLIDS]
        case .KIDS: return []
        default: return allCases.filter { $0.kind == kind.base && $0.listed }
        }
    }
}

/// Yerleşik şekillerin geometrisi (Android `ShapeCatalog` ve `ShapeSolids`).
enum ShapeCatalog {
    static func frame(_ shape: ShapeKind, _ start: P, _ end: P) -> FRect {
        if shape.fit == .SQUARE {
            let side = max(abs(end.x - start.x), abs(end.y - start.y))
            let sx: Float = end.x < start.x ? -1 : 1
            let sy: Float = end.y < start.y ? -1 : 1
            let ex = start.x + side * sx, ey = start.y + side * sy
            return FRect(left: min(start.x, ex), top: min(start.y, ey), right: max(start.x, ex), bottom: max(start.y, ey))
        }
        return FRect(left: min(start.x, end.x), top: min(start.y, end.y), right: max(start.x, end.x), bottom: max(start.y, end.y))
    }

    /// Şeklin çizgileri; [ink] çizgi kalınlığı. Kesik çizgiler istenirse parçalara ayrılır.
    static func paths(_ shape: ShapeKind, _ start: P, _ end: P, ink: Float, expandDashes: Bool = true) -> [ShapePath] {
        let raw: [ShapePath]
        if shape.fit == .LINE {
            raw = linePaths(shape, start, end, ink)
        } else {
            raw = boxPaths(shape, frame(shape, start, end), ink)
        }
        return expandDashes ? raw.flatMap { $0.dashed ? dashes($0, ink) : [$0] } : raw
    }

    /// Çizgiyi [spacing] aralıklı noktalara böler.
    static func densify(_ points: [P], spacing: Float = 4) -> [P] {
        guard points.count >= 2 else { return points }
        var out = [points[0]]
        for i in 1..<points.count {
            let a = points[i - 1], b = points[i]
            let length = hypotf(b.x - a.x, b.y - a.y)
            if length < 0.5 { continue }
            let steps = max(1, Int((length / spacing).rounded(.up)))
            for k in 1...steps { out.append(a + (b - a) * (Float(k) / Float(steps))) }
        }
        return out
    }

    // MARK: - Çizgi şekilleri

    private static func linePaths(_ shape: ShapeKind, _ s: P, _ e: P, _ ink: Float) -> [ShapePath] {
        let length = hypotf(e.x - s.x, e.y - s.y)
        if length < 1e-3 { return [ShapePath(points: [s, e])] }
        let d = P((e.x - s.x) / length, (e.y - s.y) / length)
        let head = headSize(length, ink)
        let neg = P(-d.x, -d.y)
        switch shape {
        case .ARROW: return [ShapePath(points: [s, e]), arrowHead(e, d, head)]
        case .DOUBLE_ARROW: return [ShapePath(points: [s, e]), arrowHead(e, d, head), arrowHead(s, neg, head)]
        case .NUMBER_LINE:
            let n = P(-d.y, d.x)
            let ticks = (1...9).map { i -> ShapePath in
                let c = s + (e - s) * (Float(i) / 10)
                return ShapePath(points: [c - n * (head * 0.45), c + n * (head * 0.45)])
            }
            return [ShapePath(points: [s, e]), arrowHead(e, d, head), arrowHead(s, neg, head)] + ticks
        default: return [ShapePath(points: [s, e])]
        }
    }

    static func headSize(_ length: Float, _ ink: Float) -> Float { min(length * 0.3, max(ink * 4, min(length * 0.08, 42))) }

    static func arrowHead(_ tip: P, _ dir: P, _ size: Float) -> ShapePath {
        let n = P(-dir.y, dir.x)
        let back = tip - dir * size
        return ShapePath(points: [back + n * (size * 0.55), tip, back - n * (size * 0.55)])
    }

    // MARK: - Kutu şekilleri

    /// Birim kareden kutuya eşleme.
    struct Box {
        let r: FRect
        let w, h, m: Float
        init(_ r: FRect) { self.r = r; w = max(r.width, 1e-3); h = max(r.height, 1e-3); m = min(w, h) }
        func p(_ u: Float, _ v: Float) -> P { P(r.left + u * w, r.top + v * h) }

        func poly(_ uv: Float..., closed: Bool = false, dashed: Bool = false) -> ShapePath {
            var pts = (0..<(uv.count / 2)).map { p(uv[$0 * 2], uv[$0 * 2 + 1]) }
            if closed { pts.append(pts[0]) }
            return ShapePath(points: pts, closed: closed, dashed: dashed)
        }

        /// Kutu biriminde elips yayı; örnek sayısı gerçek boyuta göre.
        func arc(_ cx: Float, _ cy: Float, _ rx: Float, _ ry: Float, _ from: Double, _ to: Double, closed: Bool = false, dashed: Bool = false) -> ShapePath {
            let perimeter = Float(abs(to - from) * Double(rx * w + ry * h) / 2)
            let n = min(max(Int(perimeter / 5), 12), 720)
            let pts = (0...n).map { i -> P in
                let a = from + (to - from) * Double(i) / Double(n)
                return p(cx + rx * Float(cos(a)), cy + ry * Float(sin(a)))
            }
            return ShapePath(points: pts, closed: closed, dashed: dashed)
        }

        /// Parametrik eğri (t ∈ [0,1]) örnekleri.
        func curve(closed: Bool = false, samples: Int = 0, _ f: (Float) -> (Float, Float)) -> ShapePath {
            let n = samples > 0 ? samples : min(max(Int((w + h) / 4), 48), 900)
            let pts = (0...n).map { i -> P in let (u, v) = f(Float(i) / Float(n)); return p(u, v) }
            return ShapePath(points: pts, closed: closed)
        }
    }

    private static let PI = Double.pi

    private static func boxPaths(_ shape: ShapeKind, _ rect: FRect, _ ink: Float) -> [ShapePath] {
        if ShapeKind.solids.contains(shape) { return ShapeSolids.paths(shape, rect) }
        let b = Box(rect)
        let full = 2 * PI
        switch shape {
        case .CIRCLE, .ELLIPSE: return [b.arc(0.5, 0.5, 0.5, 0.5, 0, full, closed: true)]
        case .SQUARE, .RECTANGLE: return [b.poly(0, 0, 1, 0, 1, 1, 0, 1, closed: true)]
        case .ROUNDED_RECT: return [roundedRect(rect, b.m * 0.18)]
        case .TRIANGLE: return [b.poly(0.5, 0, 1, 1, 0, 1, closed: true)]
        case .RIGHT_TRIANGLE: return [b.poly(0, 0, 1, 1, 0, 1, closed: true), rightAngleMark(b.p(0, 1), 1, -1, b.m * 0.12)]
        case .EQUILATERAL_TRIANGLE:
            let top = 1 - Float(3).squareRoot() / 2
            return [b.poly(0.5, top, 1, 1, 0, 1, closed: true)]
        case .DIAMOND: return [b.poly(0.5, 0, 1, 0.5, 0.5, 1, 0, 0.5, closed: true)]
        case .PARALLELOGRAM: return [b.poly(0.25, 0, 1, 0, 0.75, 1, 0, 1, closed: true)]
        case .TRAPEZOID: return [b.poly(0.25, 0, 0.75, 0, 1, 1, 0, 1, closed: true)]
        case .PENTAGON: return [regular(b, 5)]
        case .HEXAGON: return [regular(b, 6)]
        case .OCTAGON: return [regular(b, 8, rotation: PI / 8)]
        case .STAR: return [star(b)]
        case .HEART:
            return [normalized(b, closed: true) { t in
                let a = Double(t) * 2 * PI
                let x = 16 * pow(sin(a), 3)
                let y = 13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)
                return (Float(x), Float(-y))
            }]
        case .SPIRAL:
            return [b.curve { t in
                let a = Double(t) * 3.0 * 2 * PI
                let r = 0.5 * t
                return (0.5 + r * Float(cos(a)), 0.5 + r * Float(sin(a)))
            }]
        case .SEMICIRCLE:
            let pts = b.arc(0.5, 1, 0.5, 1, PI, full).points
            return [ShapePath(points: pts + [pts[0]], closed: true)]
        case .CIRCLE_RADIUS:
            return [b.arc(0.5, 0.5, 0.5, 0.5, 0, full, closed: true),
                    ShapePath(points: [b.p(0.5, 0.5), b.p(1, 0.5)]),
                    circleIn(b.p(0.5, 0.5), max(ink * 0.9, b.m * 0.012))]
        case .SUN:
            var out = [b.arc(0.5, 0.5, 0.27, 0.27, 0, full, closed: true)]
            for i in 0..<12 {
                let a = Double(i) * PI / 6
                let c = Float(cos(a)), s = Float(sin(a))
                out.append(ShapePath(points: [b.p(0.5 + 0.35 * c, 0.5 + 0.35 * s), b.p(0.5 + 0.5 * c, 0.5 + 0.5 * s)]))
            }
            return out
        case .MOON:
            return [crescent(b),
                    b.arc(0.30, 0.34, 0.055, 0.055, 0, full, closed: true),
                    b.arc(0.22, 0.58, 0.038, 0.038, 0, full, closed: true),
                    b.arc(0.35, 0.72, 0.028, 0.028, 0, full, closed: true)]
        case .CLOUD:
            return [normalized(b, closed: true, samples: 360) { t in
                let a = Double(t) * 2 * PI
                let g = 0.78 + 0.22 * abs(sin(3.5 * a))
                let x = cos(a) * g * 1.5
                var y = sin(a) * g
                if y > 0.45 { y = 0.45 + (y - 0.45) * 0.15 }
                return (Float(x), Float(y))
            }]
        case .MOUNTAINS:
            return [b.poly(0, 1, 0.22, 0.35, 0.38, 0.6, 0.58, 0.1, 0.78, 0.55, 0.88, 0.4, 1, 1),
                    b.poly(0.5, 0.3, 0.54, 0.37, 0.58, 0.31, 0.62, 0.38, 0.669, 0.3)]
        case .HILLS:
            return [b.curve { t in (t * 0.65, 1 - 0.6 * Float(sin(PI * Double(t)))) },
                    b.curve { t in (0.35 + t * 0.65, 1 - 0.42 * Float(sin(PI * Double(t)))) },
                    b.poly(0, 1, 1, 1)]
        case .WAVES:
            return [Float(0.2), 0.5, 0.8].map { v0 in b.curve { t in (t, v0 + 0.1 * Float(sin(2 * PI * 2.5 * Double(t)))) } }
        case .TREE:
            return [b.poly(0.36, 1, 0.43, 0.76, 0.455, 0.52, 0.545, 0.52, 0.57, 0.76, 0.64, 1, closed: true),
                    b.poly(0.455, 0.68, 0.33, 0.56),
                    b.poly(0.545, 0.72, 0.67, 0.62),
                    normalizedInto(b, FRect(left: 0.04, top: 0.02, right: 0.96, bottom: 0.60), closed: true, samples: 240) { t in
                        let a = Double(t) * 2 * PI
                        let g = 0.86 + 0.14 * abs(sin(3.5 * a))
                        return (Float(cos(a) * g), Float(sin(a) * g * 0.92))
                    },
                    b.curve { quad(0.16, 0.30, 0.30, 0.40, 0.40, 0.26, $0) },
                    b.curve { quad(0.44, 0.42, 0.58, 0.52, 0.74, 0.38, $0) },
                    b.curve { quad(0.60, 0.18, 0.72, 0.30, 0.86, 0.24, $0) }]
        case .PINE:
            return [b.poly(0.5, 0, 0.78, 0.34, 0.22, 0.34, closed: true),
                    b.poly(0.5, 0.2, 0.88, 0.6, 0.12, 0.6, closed: true),
                    b.poly(0.5, 0.45, 1, 0.88, 0, 0.88, closed: true),
                    b.poly(0.44, 0.88, 0.44, 1, 0.56, 1, 0.56, 0.88)]
        case .FLOWER:
            var out = [b.arc(0.5, 0.5, 0.12, 0.12, 0, full, closed: true)]
            for i in 0..<6 { out.append(petal(b, Double(i) * PI / 3)) }
            for i in 0..<6 {
                let a = Double(i) * PI / 3 + 0.5
                out.append(b.arc(Float(0.5 + 0.06 * cos(a)), Float(0.5 + 0.06 * sin(a)), 0.015, 0.015, 0, full, closed: true))
            }
            return out
        case .LEAF:
            let a = P(0.05, 0.95), c = P(0.95, 0.05), n = P(0.7071, 0.7071)
            func side(_ sign: Float) -> ShapePath {
                b.curve { t in
                    let bulge = 0.22 * Float(sin(PI * Double(t))) * sign
                    return (a.x + (c.x - a.x) * t + n.x * bulge, a.y + (c.y - a.y) * t + n.y * bulge)
                }
            }
            let veins = (1...5).flatMap { i -> [ShapePath] in
                let t = Float(i) / 6
                let reach = 0.17 * Float(sin(PI * Double(t)))
                let mx = a.x + (c.x - a.x) * t, my = a.y + (c.y - a.y) * t
                let fwd: Float = 0.06
                return [b.poly(mx, my, mx + n.x * reach + (c.x - a.x) * 0.01 + fwd * 0.3, my + n.y * reach - fwd * 0.3),
                        b.poly(mx, my, mx - n.x * reach + fwd * 0.3, my - n.y * reach + (c.y - a.y) * 0.01 - fwd * 0.3)]
            }
            return [side(1), side(-1), b.poly(0.02, 0.98, 0.95, 0.05)] + veins
        case .BIRD:
            return [bezierPath(b, true, [0.30, 0.62, 0.33, 0.44, 0.54, 0.42], [0.54, 0.42, 0.72, 0.44, 0.75, 0.58],
                               [0.75, 0.58, 0.76, 0.80, 0.52, 0.84], [0.52, 0.84, 0.33, 0.84, 0.30, 0.62]),
                    b.arc(0.79, 0.33, 0.105, 0.105, 0, full, closed: true),
                    b.curve { quad(0.70, 0.42, 0.71, 0.38, 0.70, 0.34, $0) },
                    b.poly(0.885, 0.30, 1, 0.36, 0.885, 0.41, closed: true),
                    b.arc(0.815, 0.30, 0.022, 0.022, 0, full, closed: true),
                    bezierPath(b, true, [0.40, 0.56, 0.52, 0.48, 0.66, 0.56], [0.66, 0.56, 0.58, 0.73, 0.42, 0.69],
                               [0.42, 0.69, 0.36, 0.63, 0.40, 0.56]),
                    b.poly(0.31, 0.58, 0.04, 0.50, 0.08, 0.70, 0.31, 0.73, closed: true),
                    b.poly(0.50, 0.84, 0.48, 0.95, 0.42, 0.98),
                    b.poly(0.61, 0.82, 0.63, 0.94, 0.57, 0.97)]
        case .HOUSE:
            return [b.poly(0.12, 0.45, 0.88, 0.45, 0.88, 1, 0.12, 1, closed: true),
                    b.poly(0, 0.5, 0.5, 0.06, 1, 0.5, closed: true),
                    b.poly(0.5, 0.06, 0.5, 0.45, dashed: true),
                    b.poly(0.72, 0.28, 0.72, 0.10, 0.80, 0.10, 0.80, 0.345),
                    b.poly(0.70, 0.10, 0.82, 0.10, 0.82, 0.05, 0.70, 0.05, closed: true),
                    b.poly(0.42, 1, 0.42, 0.68, 0.58, 0.68, 0.58, 1),
                    b.arc(0.55, 0.85, 0.015, 0.015, 0, full, closed: true),
                    b.poly(0.18, 0.56, 0.34, 0.56, 0.34, 0.74, 0.18, 0.74, closed: true),
                    b.poly(0.26, 0.56, 0.26, 0.74),
                    b.poly(0.18, 0.65, 0.34, 0.65),
                    b.poly(0.66, 0.56, 0.82, 0.56, 0.82, 0.74, 0.66, 0.74, closed: true),
                    b.poly(0.74, 0.56, 0.74, 0.74),
                    b.poly(0.66, 0.65, 0.82, 0.65)]
        case .DROP:
            return [normalized(b, closed: true) { t in
                let a = Double(t) * 2 * PI
                return (Float(sin(a) * sin(a / 2)), Float(-cos(a)))
            }]
        case .FACE_GUIDE:
            return [b.curve(closed: true) { t in
                        let a = Double(t) * 2 * PI
                        let lower = max(0.0, sin(a))
                        let rx = 0.5 * (1 - 0.22 * lower * lower)
                        return (0.5 + Float(rx * cos(a)), 0.5 + 0.5 * Float(sin(a)))
                    },
                    ShapePath(points: [b.p(0.5, 0), b.p(0.5, 1)], dashed: true),
                    ShapePath(points: [b.p(0.02, 0.47), b.p(0.98, 0.47)], dashed: true),
                    ShapePath(points: [b.p(0.33, 0.68), b.p(0.67, 0.68)], dashed: true),
                    ShapePath(points: [b.p(0.36, 0.8), b.p(0.64, 0.8)], dashed: true)]
        case .EYE:
            let center = P((rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2)
            let pupil = min(b.h * 0.11, b.w * 0.07)
            var out = [b.curve { t in (t, 0.5 - 0.42 * Float(sin(PI * Double(t)))) },
                       b.curve { t in (t, 0.5 + 0.30 * Float(sin(PI * Double(t)))) },
                       circleIn(center + P(0, b.h * 0.04), min(b.h * 0.30, b.w * 0.19)),
                       circleIn(center + P(0, b.h * 0.04), pupil),
                       circleIn(center + P(-pupil * 0.38, b.h * 0.04 - pupil * 0.38), pupil * 0.38),
                       b.curve { t in (0.08 + 0.84 * t, 0.22 - 0.16 * Float(sin(PI * Double(t)))) }]
            for i in 1...4 {
                let t = 0.16 + Float(i) * 0.17
                let y = 0.5 - 0.42 * Float(sin(PI * Double(t)))
                out.append(b.poly(t, y, max(0.02, t - 0.045), max(0.02, y - 0.09)))
            }
            return out
        case .LIPS:
            return [b.curve { t in (t, Float(0.5 - 0.38 * sin(PI * Double(t)) + 0.22 * exp1(t))) },
                    b.curve { t in (t, 0.5 + 0.5 * Float(sin(PI * Double(t)))) },
                    b.curve { t in (t, 0.5 + 0.06 * Float(sin(PI * Double(t)))) }]
        case .NOSE:
            return [b.curve { quad(0.46, 0.02, 0.40, 0.34, 0.40, 0.62, $0) },
                    b.curve { quad(0.40, 0.62, 0.42, 0.80, 0.56, 0.80, $0) },
                    b.curve { quad(0.22, 0.70, 0.30, 0.98, 0.50, 0.88, $0) },
                    b.curve { quad(0.50, 0.88, 0.70, 0.98, 0.78, 0.70, $0) },
                    b.arc(0.31, 0.80, 0.045, 0.028, 0, full, closed: true),
                    b.arc(0.69, 0.80, 0.045, 0.028, 0, full, closed: true),
                    b.poly(0.60, 0.06, 0.62, 0.30, dashed: true)]
        case .ANGLE:
            let v = b.p(0, 1), a = b.p(1, 1), c = b.p(0.72, 0)
            return [ShapePath(points: [a, v, c]), angleArc(v, a, c, b.m * 0.28)]
        case .RIGHT_ANGLE:
            return [b.poly(1, 1, 0, 1, 0, 0), rightAngleMark(b.p(0, 1), 1, -1, b.m * 0.14)]
        case .PARALLEL_LINES:
            let head = max(ink * 3, b.m * 0.08)
            return [Float(0.3), 0.7].flatMap { v -> [ShapePath] in
                let mid = b.p(0.5, v)
                return [ShapePath(points: [b.p(0, v), b.p(1, v)]),
                        ShapePath(points: [mid + P(-head * 0.5, -head * 0.5), mid + P(head * 0.2, 0), mid + P(-head * 0.5, head * 0.5)])]
            }
        case .CUBE: return box3d(b, 0.3)
        case .PRISM: return box3d(b, 0.25)
        case .CYLINDER:
            return [b.arc(0.5, 0.12, 0.5, 0.12, 0, full, closed: true),
                    b.poly(0, 0.12, 0, 0.88),
                    b.poly(1, 0.12, 1, 0.88),
                    b.arc(0.5, 0.88, 0.5, 0.12, 0, PI),
                    b.arc(0.5, 0.88, 0.5, 0.12, PI, full, dashed: true)]
        case .CONE:
            return [b.poly(0, 0.88, 0.5, 0, 1, 0.88),
                    b.arc(0.5, 0.88, 0.5, 0.12, 0, PI),
                    b.arc(0.5, 0.88, 0.5, 0.12, PI, full, dashed: true)]
        case .SPHERE:
            return [b.arc(0.5, 0.5, 0.5, 0.5, 0, full, closed: true),
                    b.arc(0.5, 0.5, 0.5, 0.15, 0, PI),
                    b.arc(0.5, 0.5, 0.5, 0.15, PI, full, dashed: true)]
        case .PYRAMID:
            return [b.poly(0, 0.85, 0.68, 1, 1, 0.8),
                    b.poly(0, 0.85, 0.5, 0, 0.68, 1),
                    b.poly(0.5, 0, 1, 0.8),
                    b.poly(0, 0.85, 0.32, 0.66, 1, 0.8, dashed: true),
                    b.poly(0.5, 0, 0.32, 0.66, dashed: true)]
        case .AXES:
            let head = headSize(min(b.w, b.h), ink)
            let cx = b.p(0.5, 0.5)
            var out = [ShapePath(points: [b.p(0, 0.5), b.p(1, 0.5)]),
                       ShapePath(points: [b.p(0.5, 1), b.p(0.5, 0)]),
                       arrowHead(b.p(1, 0.5), P(1, 0), head),
                       arrowHead(b.p(0.5, 0), P(0, -1), head)]
            let tick = head * 0.4
            for i in 1...4 {
                for sign: Float in [-1, 1] {
                    let x = cx.x + sign * Float(i) * b.w * 0.1
                    let y = cx.y + sign * Float(i) * b.h * 0.1
                    out.append(ShapePath(points: [P(x, cx.y - tick), P(x, cx.y + tick)]))
                    out.append(ShapePath(points: [P(cx.x - tick, y), P(cx.x + tick, y)]))
                }
            }
            return out
        case .GRID:
            let cells = 8
            let cell = b.w / Float(cells)
            let rows = max(1, Int(b.h / cell))
            let height = cell * Float(rows)
            var out: [ShapePath] = []
            for i in 0...cells { out.append(ShapePath(points: [P(rect.left + Float(i) * cell, rect.top), P(rect.left + Float(i) * cell, rect.top + height)])) }
            for j in 0...rows { out.append(ShapePath(points: [P(rect.left, rect.top + Float(j) * cell), P(rect.right, rect.top + Float(j) * cell)])) }
            return out
        case .PARABOLA: return [b.curve { t in let x = t * 2 - 1; return (t, 1 - x * x) }]
        case .SINE: return [b.curve { t in (t, 0.5 - 0.5 * Float(sin(2 * PI * 2 * Double(t)))) }]
        case .SECTOR:
            let arc = b.arc(0.5, 0.5, 0.5, 0.5, -PI / 2, PI / 6).points
            return [ShapePath(points: [b.p(0.5, 0.5)] + arc + [b.p(0.5, 0.5)], closed: true)]
        case .ARC: return [b.arc(0.5, 0.5, 0.5, 0.5, PI, 2 * PI)]
        case .ANNULUS:
            return [b.arc(0.5, 0.5, 0.5, 0.5, 0, full, closed: true), b.arc(0.5, 0.5, 0.28, 0.28, 0, full, closed: true)]
        case .KITE: return [b.poly(0.5, 0, 1, 0.38, 0.5, 1, 0, 0.38, closed: true)]
        case .RIGHT_TRAPEZOID: return [b.poly(0, 0, 0.62, 0, 1, 1, 0, 1, closed: true)]
        case .TRIANGLE_HEIGHT:
            return [b.poly(0.35, 0, 1, 1, 0, 1, closed: true),
                    b.poly(0.35, 0, 0.35, 1, dashed: true),
                    rightAngleMark(b.p(0.35, 1), 1, -1, b.m * 0.1)]
        case .CROSS:
            return [b.poly(0.35, 0, 0.65, 0, 0.65, 0.35, 1, 0.35, 1, 0.65, 0.65, 0.65, 0.65, 1, 0.35, 1, 0.35, 0.65, 0, 0.65, 0, 0.35, 0.35, 0.35, closed: true)]
        case .ARROW_BLOCK:
            return [b.poly(0, 0.3, 0.6, 0.3, 0.6, 0.05, 1, 0.5, 0.6, 0.95, 0.6, 0.7, 0, 0.7, closed: true)]
        case .TRIANGULAR_PRISM:
            return [b.poly(0.3, 0.12, 0.6, 0.72, 0, 0.72, closed: true),
                    b.poly(0.3, 0.12, 0.7, 0.28),
                    b.poly(0.6, 0.72, 1, 0.88),
                    b.poly(0.7, 0.28, 1, 0.88),
                    b.poly(0, 0.72, 0.4, 0.88, dashed: true),
                    b.poly(0.4, 0.88, 1, 0.88, dashed: true),
                    b.poly(0.4, 0.88, 0.7, 0.28, dashed: true)]
        case .HEXAGONAL_PRISM:
            let top = (0...5).map { i -> (Float, Float) in
                let a = -PI / 2 + Double(i) * PI / 3
                return (0.5 + 0.32 * Float(cos(a)), 0.22 + 0.16 * Float(sin(a)))
            }
            let bottom = top.map { ($0.0, $0.1 + 0.52) }
            let tp = top.map { b.p($0.0, $0.1) }, bp = bottom.map { b.p($0.0, $0.1) }
            var out = [ShapePath(points: tp + [tp[0]], closed: true),
                       ShapePath(points: Array(bp[0..<4])),
                       ShapePath(points: [bp[3]] + Array(bp[4..<6]) + [bp[0]], dashed: true)]
            for i in [0, 2, 3, 5] { out.append(ShapePath(points: [tp[i], bp[i]], dashed: i == 5)) }
            return out
        case .TETRAHEDRON:
            return [b.poly(0.5, 0.05, 0.95, 0.85, 0.05, 0.85, closed: true),
                    b.poly(0.5, 0.05, 0.62, 0.62),
                    b.poly(0.05, 0.85, 0.62, 0.62, dashed: true),
                    b.poly(0.95, 0.85, 0.62, 0.62, dashed: true)]
        case .TRUNCATED_CONE:
            return [b.arc(0.5, 0.15, 0.26, 0.08, 0, full, closed: true),
                    b.poly(0.24, 0.15, 0.06, 0.85),
                    b.poly(0.76, 0.15, 0.94, 0.85),
                    b.arc(0.5, 0.85, 0.44, 0.12, 0, PI),
                    b.arc(0.5, 0.85, 0.44, 0.12, PI, full, dashed: true)]
        case .HEMISPHERE:
            return [b.arc(0.5, 0.78, 0.5, 0.62, PI, full),
                    b.arc(0.5, 0.78, 0.5, 0.14, 0, PI),
                    b.arc(0.5, 0.78, 0.5, 0.14, PI, full, dashed: true)]
        case .CUBE_NET:
            let c: Float = 0.25
            return [b.poly(c, 0, 2 * c, 0, 2 * c, c, c, c, closed: true),
                    b.poly(0, c, c, c, c, 2 * c, 0, 2 * c, closed: true),
                    b.poly(c, c, 2 * c, c, 2 * c, 2 * c, c, 2 * c, closed: true),
                    b.poly(2 * c, c, 3 * c, c, 3 * c, 2 * c, 2 * c, 2 * c, closed: true),
                    b.poly(3 * c, c, 4 * c, c, 4 * c, 2 * c, 3 * c, 2 * c, closed: true),
                    b.poly(c, 2 * c, 2 * c, 2 * c, 2 * c, 3 * c, c, 3 * c, closed: true)]
        case .BUTTERFLY:
            let upper: [[Float]] = [[0.475, 0.20, 0.30, 0.03, 0.10, 0.14], [0.10, 0.14, 0.00, 0.33, 0.20, 0.44],
                                    [0.20, 0.44, 0.38, 0.50, 0.475, 0.40], [0.475, 0.40, 0.475, 0.30, 0.475, 0.20]]
            let lower: [[Float]] = [[0.475, 0.46, 0.28, 0.50, 0.19, 0.66], [0.19, 0.66, 0.10, 0.86, 0.30, 0.90],
                                    [0.30, 0.90, 0.44, 0.86, 0.475, 0.68], [0.475, 0.68, 0.475, 0.57, 0.475, 0.46]]
            return [bezierPath(b, true, upper), mirrored(b, true, upper), bezierPath(b, true, lower), mirrored(b, true, lower),
                    normalizedInto(b, FRect(left: 0.462, top: 0.13, right: 0.538, bottom: 0.93), closed: true, samples: 72) { t in
                        let a = Double(t) * 2 * PI
                        return (Float(sin(a) * (0.35 + 0.65 * abs(sin(a / 2)))), Float(-cos(a)))
                    },
                    b.arc(0.5, 0.115, 0.042, 0.042, 0, full, closed: true),
                    b.curve { quad(0.482, 0.09, 0.40, 0.04, 0.31, 0.015, $0) },
                    b.curve { quad(0.518, 0.09, 0.60, 0.04, 0.69, 0.015, $0) },
                    b.arc(0.235, 0.25, 0.05, 0.055, 0, full, closed: true),
                    b.arc(0.765, 0.25, 0.05, 0.055, 0, full, closed: true),
                    b.arc(0.30, 0.735, 0.032, 0.036, 0, full, closed: true),
                    b.arc(0.70, 0.735, 0.032, 0.036, 0, full, closed: true)]
        case .FISH:
            return [b.curve(closed: true) { t in
                        let a = Double(t) * 2 * PI
                        return (0.45 + 0.42 * Float(cos(a)), 0.5 + 0.27 * Float(sin(a)) * (0.6 + 0.4 * Float(cos(a / 2))))
                    },
                    b.poly(0.86, 0.5, 1, 0.28, 1, 0.72, closed: true),
                    b.arc(0.25, 0.44, 0.05, 0.05, 0, full, closed: true),
                    b.curve { quad(0.45, 0.24, 0.6, 0.08, 0.72, 0.33, $0) },
                    b.curve { quad(0.45, 0.76, 0.6, 0.92, 0.72, 0.67, $0) }]
        case .PALM:
            return [b.curve { quad(0.45, 1, 0.52, 0.62, 0.5, 0.32, $0) },
                    b.curve { quad(0.55, 1, 0.62, 0.62, 0.56, 0.32, $0) }] + (0...5).map { i in
                let a = PI * (0.15 + Double(i) * 0.14)
                return b.curve { quad(0.52, 0.3, Float(0.52 + 0.3 * cos(a)), Float(0.3 - 0.22 * sin(a)), Float(0.52 + 0.46 * cos(a)), Float(0.34 - 0.1 * sin(a)), $0) }
            }
        case .GRASS:
            return (0...8).map { i -> ShapePath in
                let x = 0.05 + Float(i) * 0.1125
                let lean = 0.035 + Float(i % 3) * 0.012
                let top = 0.14 + Float(i % 3) * 0.13
                let tipX = min(x + lean * 2.2, 0.99)
                return b.curve { quad(x, 1, x + lean * 0.9, (1 + top) * 0.5, tipX, top, $0) }
            } + [b.poly(0.02, 1, 0.98, 1)]
        case .ROCK:
            return [b.poly(0.05, 1, 0.2, 0.45, 0.45, 0.2, 0.72, 0.3, 0.95, 0.7, 0.98, 1, closed: true),
                    b.poly(0.45, 0.2, 0.52, 0.62, 0.95, 0.7),
                    b.poly(0.52, 0.62, 0.3, 1)]
        case .BOAT:
            return [b.poly(0.05, 0.7, 0.95, 0.7, 0.8, 0.95, 0.2, 0.95, closed: true),
                    b.poly(0.5, 0.7, 0.5, 0.12),
                    b.poly(0.5, 0.16, 0.9, 0.62, 0.5, 0.62, closed: true),
                    b.poly(0.46, 0.62, 0.14, 0.62, 0.46, 0.2, closed: true)]
        case .APPLE:
            return [b.curve(closed: true) { t in
                        let a = Double(t) * 2 * PI
                        let r = 0.42 + 0.05 * cos(2 * a)
                        return (Float(0.5 + r * cos(a)), Float(0.55 + r * 0.95 * sin(a) - 0.06 * cos(a) * cos(a)))
                    },
                    b.curve { quad(0.5, 0.16, 0.53, 0.05, 0.62, 0.02, $0) },
                    b.curve(closed: true) { leafAt($0) }]
        case .LINE, .ARROW, .DOUBLE_ARROW, .NUMBER_LINE:
            return linePaths(shape, P(rect.left, rect.top), P(rect.right, rect.bottom), ink)
        case .AXES_3D, .FUNCTION_PLOT, .COORD_GRID, .UNIT_CIRCLE, .BAR_CHART, .LINE_CHART, .PIE_CHART, .SCATTER:
            // Eski grafik kayıtları: grafik motoru aktarılınca onunla çizilir
            return []
        case .SOLID_CUBE, .SOLID_BOX, .SOLID_CYLINDER, .SOLID_CONE, .SOLID_SPHERE, .SOLID_DOME, .SOLID_PYRAMID, .SOLID_PRISM, .SOLID_HEX_PRISM:
            return ShapeSolids.paths(shape, rect)
        }
    }

    // MARK: - Yardımcılar

    /// Ardışık quadratic Bezier parçalarından yol; her parça (x0, y0, cx, cy, x1, y1).
    private static func bezierPath(_ b: Box, _ closed: Bool, _ segments: [Float]...) -> ShapePath { bezierPath(b, closed, segments) }

    private static func bezierPath(_ b: Box, _ closed: Bool, _ segments: [[Float]]) -> ShapePath {
        var pts: [P] = []
        for g in segments {
            let n = 18
            for i in 0...n {
                if i == 0 && !pts.isEmpty { continue }
                let (x, y) = quad(g[0], g[1], g[2], g[3], g[4], g[5], Float(i) / Float(n))
                pts.append(b.p(x, y))
            }
        }
        return ShapePath(points: closed ? pts + [pts[0]] : pts, closed: closed)
    }

    private static func mirrored(_ b: Box, _ closed: Bool, _ segments: [[Float]]) -> ShapePath {
        bezierPath(b, closed, segments.map { [1 - $0[0], $0[1], 1 - $0[2], $0[3], 1 - $0[4], $0[5]] })
    }

    private static func leafAt(_ t: Float) -> (Float, Float) {
        let a = Double(t) * 2 * PI
        return (Float(0.66 + 0.12 * cos(a) - 0.05 * sin(a)), Float(0.06 + 0.05 * sin(a) + 0.03 * cos(a)))
    }

    private static func exp1(_ t: Float) -> Double {
        let d = abs(Double(t) - 0.5) / 0.12
        return Foundation.exp(-d * d)
    }

    static func quad(_ x0: Float, _ y0: Float, _ cx: Float, _ cy: Float, _ x1: Float, _ y1: Float, _ t: Float) -> (Float, Float) {
        let m = 1 - t
        return (m * m * x0 + 2 * m * t * cx + t * t * x1, m * m * y0 + 2 * m * t * cy + t * t * y1)
    }

    private static func regular(_ b: Box, _ sides: Int, rotation: Double = 0) -> ShapePath {
        let pts = (0...sides).map { i -> P in
            let a = -PI / 2 + rotation + Double(i) * 2 * PI / Double(sides)
            return b.p(0.5 + 0.5 * Float(cos(a)), 0.5 + 0.5 * Float(sin(a)))
        }
        return ShapePath(points: pts, closed: true)
    }

    private static func star(_ b: Box) -> ShapePath {
        let pts = (0...10).map { i -> P in
            let a = -PI / 2 + Double(i) * PI / 5
            let r: Float = i % 2 == 0 ? 0.5 : 0.2
            return b.p(0.5 + r * Float(cos(a)), 0.53 + r * Float(sin(a)))
        }
        return ShapePath(points: pts, closed: true)
    }

    private static func crescent(_ b: Box) -> ShapePath {
        let theta = 55.0 * PI / 180
        let p1x = 0.5 + 0.5 * cos(theta)
        let s = 0.5 * sin(theta)
        let left = 0.3
        let c = (left * left - p1x * p1x - s * s) / (2 * (left - p1x))
        let r2 = c - left
        let phi = atan2(s, p1x - c)
        let outer = b.arc(0.5, 0.5, 0.5, 0.5, theta, 2 * PI - theta).points
        let inner = b.arc(Float(c), 0.5, Float(r2), Float(r2), 2 * PI - phi, phi).points
        return ShapePath(points: outer + inner.dropFirst(), closed: true)
    }

    private static func petal(_ b: Box, _ angle: Double) -> ShapePath {
        let cx = 0.5 + 0.29 * cos(angle), cy = 0.5 + 0.29 * sin(angle)
        let pts = (0...72).map { i -> P in
            let a = Double(i) * 2 * PI / 72
            let x = 0.17 * cos(a), y = 0.09 * sin(a)
            return b.p(Float(cx + x * cos(angle) - y * sin(angle)), Float(cy + x * sin(angle) + y * cos(angle)))
        }
        return ShapePath(points: pts, closed: true)
    }

    private static func box3d(_ b: Box, _ depth: Float) -> [ShapePath] {
        let f = 1 - depth
        return [b.poly(0, depth, f, depth, f, 1, 0, 1, closed: true),
                b.poly(0, depth, depth, 0, 1, 0, f, depth),
                b.poly(1, 0, 1, f, f, 1),
                b.poly(depth, 0, depth, f, dashed: true),
                b.poly(depth, f, 1, f, dashed: true),
                b.poly(0, 1, depth, f, dashed: true)]
    }

    private static func roundedRect(_ r: FRect, _ radius: Float) -> ShapePath {
        let rad = min(radius, min(r.width, r.height) / 2)
        var pts: [P] = []
        func corner(_ cx: Float, _ cy: Float, _ from: Double) {
            let n = min(max(Int(rad / 3), 6), 60)
            for i in 0...n {
                let a = from + (PI / 2) * Double(i) / Double(n)
                pts.append(P(cx + rad * Float(cos(a)), cy + rad * Float(sin(a))))
            }
        }
        corner(r.right - rad, r.top + rad, -PI / 2)
        corner(r.right - rad, r.bottom - rad, 0)
        corner(r.left + rad, r.bottom - rad, PI / 2)
        corner(r.left + rad, r.top + rad, PI)
        pts.append(pts[0])
        return ShapePath(points: pts, closed: true)
    }

    private static func rightAngleMark(_ corner: P, _ sx: Float, _ sy: Float, _ size: Float) -> ShapePath {
        ShapePath(points: [corner + P(size * sx, 0), corner + P(size * sx, size * sy), corner + P(0, size * sy)])
    }

    private static func angleArc(_ v: P, _ a: P, _ c: P, _ radius: Float) -> ShapePath {
        let a0 = atan2(Double(a.y - v.y), Double(a.x - v.x))
        var a1 = atan2(Double(c.y - v.y), Double(c.x - v.x))
        if a1 > a0 { a1 -= 2 * PI }
        let n = 32
        return ShapePath(points: (0...n).map { i in
            let t = a0 + (a1 - a0) * Double(i) / Double(n)
            return P(v.x + radius * Float(cos(t)), v.y + radius * Float(sin(t)))
        })
    }

    static func circleIn(_ center: P, _ radius: Float) -> ShapePath {
        let n = min(max(Int(2 * Float.pi * radius / 5), 16), 360)
        return ShapePath(points: (0...n).map { i in
            let a = Double(i) * 2 * PI / Double(n)
            return P(center.x + radius * Float(cos(a)), center.y + radius * Float(sin(a)))
        }, closed: true)
    }

    private static func normalized(_ b: Box, closed: Bool, samples: Int = 0, _ f: (Float) -> (Float, Float)) -> ShapePath {
        normalizedInto(b, FRect(left: 0, top: 0, right: 1, bottom: 1), closed: closed, samples: samples, f)
    }

    private static func normalizedInto(_ b: Box, _ target: FRect, closed: Bool, samples: Int = 0, _ f: (Float) -> (Float, Float)) -> ShapePath {
        let n = samples > 0 ? samples : min(max(Int((b.w + b.h) / 4), 96), 900)
        let raw = (0...n).map { f(Float($0) / Float(n)) }
        let minX = raw.map(\.0).min()!, maxX = raw.map(\.0).max()!
        let minY = raw.map(\.1).min()!, maxY = raw.map(\.1).max()!
        let sw = max(maxX - minX, 1e-6), sh = max(maxY - minY, 1e-6)
        return ShapePath(points: raw.map { b.p(target.left + ($0.0 - minX) / sw * target.width, target.top + ($0.1 - minY) / sh * target.height) },
                         closed: closed)
    }

    /// Kesik çizgi parçaları (tuval biriminde, çizgi kalınlığına göre).
    static func dashes(_ path: ShapePath, _ ink: Float) -> [ShapePath] {
        let dash = max(ink * 3, 10), gap = max(ink * 2.2, 7)
        var out: [ShapePath] = []
        var current: [P] = []
        var drawing = true
        var left = dash
        guard path.points.count >= 2 else { return [] }
        for i in 1..<path.points.count {
            var a = path.points[i - 1]
            let bpt = path.points[i]
            var seg = hypotf(bpt.x - a.x, bpt.y - a.y)
            if drawing && current.isEmpty { current.append(a) }
            while seg > 0 {
                let step = min(seg, left)
                let t = step / seg
                let p = P(a.x + (bpt.x - a.x) * t, a.y + (bpt.y - a.y) * t)
                if drawing { current.append(p) }
                seg -= step
                left -= step
                a = p
                if left <= 1e-3 {
                    if drawing { if current.count >= 2 { out.append(ShapePath(points: current)) }; current = [] }
                    drawing.toggle()
                    left = drawing ? dash : gap
                    if drawing { current.append(a) }
                }
            }
        }
        if drawing && current.count >= 2 { out.append(ShapePath(points: current)) }
        return out
    }
}

/// v21 gölgeli (3B) cisimler (Android `ShapeSolids`): yüzler ve bantlar şeklin renginin tonlarıyla dolar.
enum ShapeSolids {
    private static let EDGE: Float = 0.55
    private static let BANDS = 12
    private static let PI = Double.pi

    private struct Unit {
        let r: FRect
        let w, h: Float
        init(_ r: FRect) { self.r = r; w = max(r.width, 1e-3); h = max(r.height, 1e-3) }
        func p(_ u: Float, _ v: Float) -> P { P(r.left + u * w, r.top + v * h) }
        func sampleCount(_ rx: Float, _ ry: Float) -> Int { min(max(Int((rx * w + ry * h) * Float.pi / 5), 16), 360) }
    }

    private static func face(_ u: Unit, _ level: Float, _ uv: Float..., edge: Float = EDGE) -> ShapePath { face(u, level, uv, edge: edge) }

    private static func face(_ u: Unit, _ level: Float, _ uv: [Float], edge: Float = EDGE) -> ShapePath {
        let pts = (0..<(uv.count / 2)).map { u.p(uv[$0 * 2], uv[$0 * 2 + 1]) }
        return ShapePath(points: pts + [pts[0]], closed: true, fillColorHex: ShapeRenderer.shade(level), widthScale: edge)
    }

    private static func region(_ pts: [P], _ level: Float, edge: Float = 0) -> ShapePath {
        ShapePath(points: pts + [pts[0]], closed: true, fillColorHex: ShapeRenderer.shade(level), widthScale: edge)
    }

    private static func outline(_ pts: [P], closed: Bool = true) -> ShapePath {
        ShapePath(points: closed ? pts + [pts[0]] : pts, closed: closed, fillColorHex: closed ? ShapeRenderer.NO_FILL : nil)
    }

    private static func bandLevel(_ k: Int) -> Float { 0.28 + 0.58 * (Float(k) / Float(BANDS - 1)) }

    private static func arc(_ u: Unit, _ cx: Float, _ cy: Float, _ rx: Float, _ ry: Float, _ from: Double, _ to: Double) -> [P] {
        let n = u.sampleCount(rx, ry)
        return (0...n).map { i in
            let a = from + (to - from) * Double(i) / Double(n)
            return u.p(cx + rx * Float(cos(a)), cy + ry * Float(sin(a)))
        }
    }

    static func paths(_ kind: ShapeKind, _ rect: FRect) -> [ShapePath] {
        let u = Unit(rect)
        switch kind {
        case .SOLID_CUBE: return box(u, 0.3)
        case .SOLID_BOX: return box(u, 0.24)
        case .SOLID_PYRAMID:
            return [face(u, 0.66, 0.5, 0, 0.04, 0.84, 0.66, 1), face(u, 0.32, 0.5, 0, 0.66, 1, 0.96, 0.78)]
        case .SOLID_PRISM:
            return [face(u, 0.74, 0, 0.78, 0.31, 0.14, 0.69, 0, 0.38, 0.64),
                    face(u, 0.36, 0.31, 0.14, 0.62, 0.78, 1, 0.64, 0.69, 0),
                    face(u, 0.52, 0, 0.78, 0.62, 0.78, 0.31, 0.14)]
        case .SOLID_HEX_PRISM: return hexPrism(u)
        case .SOLID_CYLINDER: return cylinder(u)
        case .SOLID_CONE: return cone(u)
        case .SOLID_SPHERE: return sphere(u)
        case .SOLID_DOME: return dome(u)
        default: return []
        }
    }

    private static func box(_ u: Unit, _ d: Float) -> [ShapePath] {
        let f = 1 - d
        return [face(u, 0.5, 0, d, f, d, f, 1, 0, 1), face(u, 0.78, 0, d, d, 0, 1, 0, f, d), face(u, 0.3, f, d, 1, 0, 1, f, f, 1)]
    }

    private static func hexPrism(_ u: Unit) -> [ShapePath] {
        let top = (0...5).map { i -> (Float, Float) in
            let a = -PI / 2 + Double(i) * PI / 3
            return (0.5 + 0.48 * Float(cos(a)), 0.2 + 0.18 * Float(sin(a)))
        }
        let drop: Float = 0.58
        var out: [ShapePath] = []
        let sides = [(5, 4), (4, 3), (3, 2), (2, 1)]
        let levels: [Float] = [0.66, 0.54, 0.42, 0.3]
        for (k, (a, b)) in sides.enumerated() {
            let (ax, ay) = top[a], (bx, by) = top[b]
            out.append(face(u, levels[k], ax, ay, bx, by, bx, by + drop, ax, ay + drop))
        }
        out.append(face(u, 0.8, top.flatMap { [$0.0, $0.1] }))
        return out
    }

    private static func cylinder(_ u: Unit) -> [ShapePath] {
        let top: Float = 0.12, bottom: Float = 0.88, ry: Float = 0.12, light: Float = 0.34
        func bottomY(_ x: Float) -> Float { let t = min(max((x - 0.5) / 0.5, -1), 1); return bottom + ry * (1 - t * t).squareRoot() }
        var out: [ShapePath] = []
        for k in 0..<BANDS {
            let t = Float(k) / Float(BANDS)
            let l = light * t * 0.95, r = 1 - (1 - light) * t * 0.95
            let n = max(8, Int((r - l) * 40))
            let arcPts = (0...n).map { i -> P in let x = r - (r - l) * Float(i) / Float(n); return u.p(x, bottomY(x)) }
            out.append(region([u.p(l, top), u.p(r, top)] + arcPts, bandLevel(k)))
        }
        out.append(outline([u.p(0, top)] + (0...40).map { i in let x = Float(i) / 40; return u.p(x, bottomY(x)) } + [u.p(1, top)], closed: false))
        out.append(region(Array(arc(u, 0.5, top, 0.5, ry, 0, 2 * PI).dropLast()), 0.8, edge: EDGE))
        return out
    }

    private static func cone(_ u: Unit) -> [ShapePath] {
        let apex = u.p(0.5, 0.02)
        let cy: Float = 0.86, ry: Float = 0.12, light: Float = 0.36
        func baseY(_ x: Float) -> Float { let t = min(max((x - 0.5) / 0.5, -1), 1); return cy + ry * (1 - t * t).squareRoot() }
        var out: [ShapePath] = []
        for k in 0..<BANDS {
            let t = Float(k) / Float(BANDS)
            let l = light * t * 0.95, r = 1 - (1 - light) * t * 0.95
            let n = max(8, Int((r - l) * 40))
            let arcPts = (0...n).map { i -> P in let x = r - (r - l) * Float(i) / Float(n); return u.p(x, baseY(x)) }
            out.append(region([apex] + arcPts, bandLevel(k)))
        }
        out.append(outline([apex] + (0...40).map { i in let x = Float(i) / 40; return u.p(x, baseY(x)) }, closed: true))
        return out
    }

    private static func sphere(_ u: Unit) -> [ShapePath] {
        var out: [ShapePath] = []
        for k in 0..<BANDS {
            let t = Float(k) / Float(BANDS)
            let r = 0.5 * (1 - 0.86 * t)
            let c = 0.5 - 0.5 * 0.36 * t
            out.append(region(Array(arc(u, c, c, r, r, 0, 2 * PI).dropLast()), bandLevel(k)))
        }
        out.append(region(Array(arc(u, 0.33, 0.33, 0.05, 0.05, 0, 2 * PI).dropLast()), 0.97))
        out.append(outline(Array(arc(u, 0.5, 0.5, 0.5, 0.5, 0, 2 * PI).dropLast())))
        return out
    }

    private static func dome(_ u: Unit) -> [ShapePath] {
        var out: [ShapePath] = []
        let cy: Float = 0.78
        func silhouette(_ cx: Float, _ rx: Float, _ ryTop: Float, _ ryBase: Float, _ cyy: Float) -> [P] {
            arc(u, cx, cyy, rx, ryTop, PI, 2 * PI) + Array(arc(u, cx, cyy, rx, ryBase, 0, PI).dropFirst().dropLast())
        }
        for k in 0..<BANDS {
            let t = Float(k) / Float(BANDS)
            let s = 1 - 0.84 * t
            out.append(region(silhouette(0.5 - 0.2 * t, 0.5 * s, 0.74 * s, 0.14 * s, cy - 0.24 * t), bandLevel(k)))
        }
        out.append(outline(silhouette(0.5, 0.5, 0.74, 0.14, cy)))
        return out
    }
}
