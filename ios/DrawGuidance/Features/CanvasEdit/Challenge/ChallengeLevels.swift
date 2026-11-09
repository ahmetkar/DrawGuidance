import Foundation

/*
 * v38 çocuk modunda çizim görevi (Android `ChallengeLevels`): 50 seviye, kolaydan zora. Hedef resimler uygulamanın
 * kendi özgün çocuk boyama şekillerinden türetilir: alanı en büyük kapalı parçaların dış hatları izlenecek çizgiler,
 * seçilen parçaların renkleri boyanacak yerlerdir.
 */

/// Görevin türü: TRACE kesikli çizgilerin üstünden çizilir; PAINT şekil örnekteki renklerle boyanır; BUILD şekil
/// Şekiller'den bulunup kesikli çizginin üstüne konur ve boyanır.
enum ChallengeKind: String {
    case TRACE, PAINT, BUILD
    var label: String {
        switch self { case .TRACE: return "challenge_kind_trace"; case .PAINT: return "challenge_kind_paint"; case .BUILD: return "challenge_kind_build" }
    }
    var intro: String {
        switch self { case .TRACE: return "challenge_intro_trace"; case .PAINT: return "challenge_intro_paint"; case .BUILD: return "challenge_intro_build" }
    }
}

/// Maskotun seviyede ipucu verdiği araçlar (ipucu cümlesi ve görünüş).
enum ChallengeTool {
    case BRUSH, ERASER, UNDO, PAINT, HOLD, SHAPE, MOVE, PAINT_BRUSH
    var tip: String {
        switch self {
        case .BRUSH: return "v45_challenge_tip_pen"
        case .ERASER: return "challenge_tip_eraser"
        case .UNDO: return "challenge_tip_undo"
        case .PAINT: return "challenge_tip_paint"
        case .HOLD: return "challenge_tip_hold"
        case .SHAPE: return "challenge_tip_shape"
        case .MOVE: return "challenge_tip_move"
        case .PAINT_BRUSH: return "v45_challenge_tip_paint_brush"
        }
    }
    var look: MascotLook {
        switch self {
        case .BRUSH: return MascotLook(face: .HAPPY, gesture: .BRUSH)
        case .ERASER: return MascotLook(face: .THINKING, gesture: .THINK)
        case .UNDO: return MascotLook(face: .WINKING, gesture: .POINT)
        case .PAINT: return MascotLook(face: .EXCITED, gesture: .THUMBS_UP)
        case .HOLD: return MascotLook(face: .SURPRISED, gesture: .CHEER)
        case .SHAPE: return MascotLook(face: .EXPLAINING, gesture: .POINT)
        case .MOVE: return MascotLook(face: .THINKING, gesture: .WAVE)
        case .PAINT_BRUSH: return MascotLook(face: .EXCITED, gesture: .BRUSH)
        }
    }
    /// v45 sırayla verilen ipuçları: kovanın ipucundan hemen sonra boya fırçasınınki.
    static func tips(_ level: ChallengeLevel) -> [ChallengeTool] { level.tools.flatMap { $0 == .PAINT ? [.PAINT, .PAINT_BRUSH] : [$0] } }
}

/// Bir seviye: çocuk şekli, izlenecek parçalar (alan sırası), boyanacak parçalar (sıra → renk), araçlar, boy.
struct ChallengeLevel {
    let number: Int
    let shape: String
    let kind: ChallengeKind
    let ranks: [Int]
    var paints: [(Int, String)] = []
    let tools: [ChallengeTool]
    var size: Float = 0.6

    var id: String { String(shape.dropFirst(shape.hasPrefix("lib.kid_") ? 8 : 0)) + "." + kind.rawValue.lowercased() }
    /// Şeklin Türkçe adı (arayüzde I18n.tr ile çevrilir).
    var title: String { ShapeFactory.title(shape) }
}

/// Boyanacak yer: şeklin çizgi kutusunda oran ve beklenen renk.
struct PaintProbe { let u: Float; let v: Float; let color: String }

/// Seviyenin kâğıttaki hedefi: yeri, izlenecek çizgiler, boyanacak yerler ve hedef şeklin çizgi kutusu.
struct ChallengeTarget {
    let level: ChallengeLevel
    let box: FRect
    let lines: [[P]]
    let probes: [PaintProbe]
    var outline: FRect? = nil

    func spots() -> [(P, String)] {
        guard let b = outline else { return [] }
        return probes.map { (P(b.left + $0.u * b.width, b.top + $0.v * b.height), $0.color) }
    }
}

enum ChallengeLevels {
    private static let RED = "#E53935", ORANGE = "#FB8C00", YELLOW = "#FDD835", LIGHT_GREEN = "#7CB342", TURQUOISE = "#00ACC1"
    private static let BLUE = "#1E88E5", PURPLE = "#5E35B1", PINK = "#EC407A", BROWN = "#8D6E63", GREEN = "#2E7D32", BLACK = "#212121"
    private static let BUILD_SIZE: Float = 0.44

    static let all: [ChallengeLevel] = {
        let T = ChallengeKind.TRACE, Pk = ChallengeKind.PAINT, B = ChallengeKind.BUILD
        let brush = ChallengeTool.BRUSH, eraser = ChallengeTool.ERASER, undo = ChallengeTool.UNDO, paint = ChallengeTool.PAINT
        let hold = ChallengeTool.HOLD, shapes = ChallengeTool.SHAPE, move = ChallengeTool.MOVE
        func L(_ n: Int, _ s: String, _ k: ChallengeKind, _ ranks: [Int], _ paints: [(Int, String)] = [], _ tools: [ChallengeTool], _ size: Float = 0.6) -> ChallengeLevel {
            ChallengeLevel(number: n, shape: "lib.kid_" + s, kind: k, ranks: ranks, paints: paints, tools: tools, size: size)
        }
        func r(_ n: Int) -> [Int] { Array(0..<n) }
        return [
            L(1, "sun", T, [0, 1, 2], [], [brush, undo], 0.5),
            L(2, "apple", T, [0, 1, 3, 2], [], [brush, eraser]),
            L(3, "house", Pk, [], [(0, YELLOW), (1, RED), (2, BROWN)], [paint, undo]),
            L(4, "kite", T, r(6), [], [brush, undo]),
            L(5, "balloons", Pk, [], [(0, RED), (1, BLUE), (2, YELLOW)], [paint, undo]),
            L(6, "ice_cream", T, r(4), [], [brush, eraser, undo]),
            L(7, "flower", B, r(9), [(0, YELLOW), (1, LIGHT_GREEN), (2, LIGHT_GREEN), (3, PINK), (4, PINK), (5, PINK), (6, PINK), (7, PINK)],
              [shapes, move, hold, paint], BUILD_SIZE),
            L(8, "fish", T, r(6), [], [brush, eraser, undo]),
            L(9, "mushroom", Pk, [], [(0, RED), (1, LIGHT_GREEN), (3, ORANGE)], [paint, undo]),
            L(10, "umbrella", T, r(7), [], [brush, eraser, undo]),
            L(11, "car", B, r(5), [(0, RED), (1, TURQUOISE)], [shapes, move, paint], BUILD_SIZE),
            L(12, "cat", T, r(8), [], [brush, eraser, undo], 0.64),
            L(13, "rocket", Pk, [], [(0, RED), (1, BLUE), (2, ORANGE), (3, ORANGE)], [paint, hold, undo]),
            L(14, "butterfly", T, r(7), [], [brush, eraser, undo], 0.64),
            L(15, "castle", B, r(7), [(0, YELLOW), (1, PURPLE), (2, PURPLE), (4, RED), (5, RED)], [shapes, move, hold, paint], BUILD_SIZE),
            L(16, "banana", T, r(3), [], [brush, eraser, undo]),
            L(17, "strawberry", Pk, [], [(0, RED), (1, LIGHT_GREEN), (2, BROWN)], [paint, undo]),
            L(18, "igloo", T, r(5), [], [brush, eraser, undo]),
            L(19, "watermelon", Pk, [], [(0, RED), (1, GREEN), (2, LIGHT_GREEN)], [paint, hold, undo]),
            L(20, "ball", B, r(5), [(0, RED), (1, BLUE), (2, YELLOW), (3, LIGHT_GREEN)], [shapes, move, paint], BUILD_SIZE),
            L(21, "whale", T, r(6), [], [brush, eraser, undo], 0.64),
            L(22, "cupcake", Pk, [], [(0, TURQUOISE), (1, PINK), (2, YELLOW), (3, PINK), (4, RED)], [paint, hold, undo]),
            L(23, "ufo", T, r(7), [], [brush, eraser, undo], 0.64),
            L(24, "ladybug", Pk, [], [(0, RED), (1, RED), (2, BLACK), (3, BLACK), (4, BLACK)], [paint, undo]),
            L(25, "earth", B, r(5), [(0, BLUE), (1, GREEN), (2, GREEN), (3, LIGHT_GREEN)], [shapes, move, hold, paint], BUILD_SIZE),
            L(26, "jellyfish", T, r(8), [], [brush, eraser, undo], 0.64),
            L(27, "crown", Pk, [], [(0, YELLOW), (1, ORANGE), (2, RED), (3, BLUE), (4, BLUE)], [paint, hold, undo]),
            L(28, "owl", T, r(8), [], [brush, eraser, undo], 0.64),
            L(29, "sailboat", B, r(6), [(0, BLUE), (1, BROWN), (2, YELLOW), (3, RED)], [shapes, move, hold, paint], BUILD_SIZE),
            L(30, "frog_prince", T, r(8), [], [brush, eraser, undo], 0.66),
            L(31, "lion", Pk, [], [(0, ORANGE), (1, YELLOW), (2, BROWN), (3, ORANGE), (4, ORANGE)], [paint, hold, undo]),
            L(32, "tree", Pk, [], [(0, GREEN), (1, LIGHT_GREEN), (2, BROWN), (3, RED), (4, RED), (5, RED), (6, RED)], [paint, hold, undo]),
            L(33, "teddy", T, r(10), [], [brush, eraser, undo], 0.66),
            L(34, "planet", Pk, [], [(0, ORANGE), (1, PURPLE), (2, ORANGE), (3, TURQUOISE), (4, PURPLE), (5, YELLOW)], [paint, hold, undo]),
            L(35, "rainbow", B, r(7), [(0, RED), (1, ORANGE), (4, YELLOW), (5, LIGHT_GREEN), (6, BLUE), (2, TURQUOISE), (3, TURQUOISE)],
              [shapes, move, hold, paint], BUILD_SIZE),
            L(36, "elephant", T, r(10), [], [brush, eraser, undo], 0.66),
            L(37, "octopus", Pk, [], [(0, PURPLE), (1, PINK), (2, PINK), (3, ORANGE), (4, ORANGE), (5, TURQUOISE), (6, TURQUOISE)], [paint, hold, undo]),
            L(38, "train", T, r(10), [], [brush, eraser, undo], 0.68),
            L(39, "treasure_chest", B, r(8), [(0, BROWN), (1, ORANGE), (2, YELLOW), (3, YELLOW), (4, RED)], [shapes, move, hold, paint], BUILD_SIZE),
            L(40, "rabbit", Pk, [], [(0, BROWN), (1, LIGHT_GREEN), (2, BROWN), (3, BROWN), (4, PINK), (5, PINK), (8, RED)], [paint, hold, undo]),
            L(41, "robot", T, r(11), [], [brush, eraser, undo], 0.68),
            L(42, "helicopter", Pk, [], [(0, RED), (1, TURQUOISE), (2, BLACK), (3, RED), (4, BLACK), (5, ORANGE), (6, ORANGE)], [paint, hold, undo]),
            L(43, "astronaut", T, r(11), [], [brush, eraser, undo], 0.68),
            L(44, "pirate_ship", B, r(7), [(0, BROWN), (1, YELLOW), (2, BLUE), (3, BLACK), (4, RED), (5, BROWN)], [shapes, move, hold, paint], BUILD_SIZE),
            L(45, "bus", T, r(12), [], [brush, eraser, undo], 0.7),
            L(46, "unicorn", Pk, [], [(0, PINK), (1, PINK), (2, PURPLE), (3, BLUE), (4, YELLOW), (5, PINK), (6, PINK), (7, PINK)], [paint, hold, undo]),
            L(47, "dino", T, r(11), [], [brush, eraser, undo], 0.7),
            L(48, "candy_house", B, r(8), [(0, BROWN), (1, PINK), (2, RED), (3, YELLOW), (4, TURQUOISE), (5, TURQUOISE), (6, ORANGE)],
              [shapes, move, hold, paint], BUILD_SIZE),
            L(49, "knight", T, r(11), [], [brush, eraser, undo], 0.7),
            L(50, "castle", Pk, [], [(0, YELLOW), (1, PURPLE), (2, PURPLE), (3, BROWN), (4, RED), (5, RED), (6, BLUE), (7, BLUE), (8, TURQUOISE),
                                     (9, PINK), (10, PINK)], [paint, hold, undo]),
        ]
    }()

    static var count: Int { all.count }
    /// v40 Free planda açık seviyeler (1–35); sonrakiler Premium.
    static let FREE_LEVELS = 35
    static func isPremium(_ index: Int) -> Bool { index >= FREE_LEVELS }

    static let inkStyle = ShapeStyle(strokeColorHex: "#2A2A33", strokeWidth: 4)

    static func box(_ level: ChallengeLevel, _ paperW: Int, _ paperH: Int) -> FRect {
        let aspect = ShapeFactory.aspect(level.shape)
        let side = Float(min(paperW, paperH)) * level.size
        let w = aspect >= 1 ? side : side * aspect
        let h = aspect >= 1 ? side / aspect : side
        let cx = Float(paperW) / 2, cy = Float(paperH) / 2
        return FRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    static func build(_ level: ChallengeLevel, _ box: FRect, id: String, order: Int = 0, style: ShapeStyle = inkStyle, fills: [Int: String] = [:]) -> Stroke? {
        guard let stroke = ShapeFactory.build(level.shape, box.topLeft, box.bottomRight, style: style, id: id, order: order) else { return nil }
        if fills.isEmpty { return stroke }
        let paths = ShapeFactory.subPaths(stroke).enumerated().map { i, p -> EditablePath in
            var q = p
            if let f = fills[i] { q.fillColorHex = f }
            return q
        }
        guard var out = ShapeFactory.fromEditable(stroke, paths) else { return nil }
        out.layerId = nil
        return out
    }

    static func target(_ level: ChallengeLevel, _ paperW: Int, _ paperH: Int) -> ChallengeTarget {
        let box = box(level, paperW, paperH)
        let info = info(level)
        guard let stroke = build(level, box, id: "challenge-target") else { return ChallengeTarget(level: level, box: box, lines: [], probes: info.probes) }
        let paths = ShapeFactory.subPaths(stroke)
        let lines: [[P]] = info.lineIndices.compactMap { $0 < paths.count ? paths[$0] : nil }.map { $0.closed ? $0.points + [$0.points[0]] : $0.points }
        return ChallengeTarget(level: level, box: box, lines: lines, probes: info.probes, outline: outlineBounds(paths))
    }

    static func fills(_ level: ChallengeLevel) -> [Int: String] { info(level).fills }

    private struct Info { let lineIndices: [Int]; let fills: [Int: String]; let probes: [PaintProbe] }
    private static var infos: [String: Info] = [:]
    private static let lock = NSLock()

    private static func info(_ level: ChallengeLevel) -> Info {
        lock.lock(); defer { lock.unlock() }
        if let i = infos[level.id] { return i }
        let box = box(level, CANON, CANON)
        let paths = build(level, box, id: "challenge-canon").map { ShapeFactory.subPaths($0) } ?? []
        let regions = regions(paths)
        let bounds = outlineBounds(paths)
        let lineIndices = (level.kind == .PAINT ? [] : level.ranks).compactMap { $0 < regions.count ? regions[$0] : nil }
        var fills: [Int: String] = [:]
        var probes: [PaintProbe] = []
        for (rank, color) in level.paints {
            guard rank < regions.count else { continue }
            let index = regions[rank]
            fills[index] = color
            guard let at = probe(paths, index) else { continue }
            if let b = bounds { probes.append(PaintProbe(u: (at.x - b.left) / b.width, v: (at.y - b.top) / b.height, color: color)) }
        }
        let i = Info(lineIndices: lineIndices, fills: fills, probes: probes)
        infos[level.id] = i
        return i
    }

    static let KID_DETAIL_TAG = "kid_detail"
    private static let CANON = 1000
    private static let GRID = 40

    /// Boyanabilir kapalı parçaların alt yol sıraları, alanı en büyükten küçüğe (eşitlikte ilk gelen önce).
    static func regions(_ paths: [EditablePath]) -> [Int] {
        paths.indices.filter { i in
            let p = paths[i]
            return p.closed && p.points.count >= 3 && p.label == nil && !KidsBrush.isPaintLayer(p.tag) && p.tag != KID_DETAIL_TAG && (p.widthScale ?? 1) > 0
        }
        .map { ($0, area(paths[$0].points)) }
        .enumerated().sorted { $0.element.1 != $1.element.1 ? $0.element.1 > $1.element.1 : $0.offset < $1.offset }
        .map { $0.element.0 }
    }

    static func outlineBounds(_ paths: [EditablePath]) -> FRect? {
        let pts = paths.filter { $0.label == nil && !KidsBrush.isPaintLayer($0.tag) && $0.points.count >= 2 }.flatMap(\.points)
        guard !pts.isEmpty else { return nil }
        let r = FRect(pts.map(\.x).min()!, pts.map(\.y).min()!, pts.map(\.x).max()!, pts.map(\.y).max()!)
        return r.width < 1e-3 || r.height < 1e-3 ? nil : r
    }

    /// Parçanın içinde, üstündeki parçaların dışında ve bütün çizgilerden en uzak nokta (ızgara taranır).
    private static func probe(_ paths: [EditablePath], _ index: Int) -> P? {
        let region = paths[index].points
        let above = paths[(index + 1)...].filter { $0.closed && $0.points.count >= 3 && $0.label == nil }.map(\.points)
        let edges = paths.filter { $0.label == nil && $0.points.count >= 2 }
        let minX = region.map(\.x).min()!, maxX = region.map(\.x).max()!
        let minY = region.map(\.y).min()!, maxY = region.map(\.y).max()!
        var best: P?
        var bestD: Float = 0
        for i in 1..<GRID { for j in 1..<GRID {
            let q = P(minX + (maxX - minX) * Float(i) / Float(GRID), minY + (maxY - minY) * Float(j) / Float(GRID))
            if !RegionFill.contains(region, q) || above.contains(where: { RegionFill.contains($0, q) }) { continue }
            var d = Float.greatestFiniteMagnitude
            for e in edges {
                d = min(d, ChallengeScore.distanceToPolyline(q, e.points, e.closed))
                if d <= bestD { break }
            }
            if d > bestD { bestD = d; best = q }
        } }
        return best
    }

    private static func area(_ p: [P]) -> Float {
        var a: Float = 0
        for i in p.indices { let q = p[(i + 1) % p.count]; a += p[i].x * q.y - q.x * p[i].y }
        return abs(a) / 2
    }
}
