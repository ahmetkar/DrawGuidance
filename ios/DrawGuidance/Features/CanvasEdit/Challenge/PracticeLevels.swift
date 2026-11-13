import Foundation

/*
 * v50 yetişkinler için çizim alıştırmaları (Android `PracticeLevels`): basit resim ve gelişmiş resim tuvallerinde,
 * çocuk görevinden ayrı. Şekiller kütüphanededir ("lib.practice.*", catalog-vectors.bin). Üç görev türü: üzerinden
 * çiz, kaybolan kılavuz (kılavuz birkaç saniye görünür, sonra ezberden çizilir) ve simetri tamamla (sol yarı ve eksen
 * görünür). Basit resimde yalnız temel alıştırmalar ve kolay desenler, gelişmiş resimde hepsi.
 */

enum PracticeKind: String {
    case TRACE, FADE, MIRROR
    var label: String {
        switch self { case .TRACE: return "v50_practice_kind_trace"; case .FADE: return "v50_practice_kind_fade"; case .MIRROR: return "v50_practice_kind_mirror" }
    }
    var intro: String {
        switch self { case .TRACE: return "v50_practice_intro_trace"; case .FADE: return "v50_practice_intro_fade"; case .MIRROR: return "v50_practice_intro_mirror" }
    }
    /// Tam puan ve sıfır puan uzaklığı (hedef köşegenine oran).
    var near: Float { switch self { case .TRACE: return 0.022; case .FADE: return 0.035; case .MIRROR: return 0.03 } }
    var far: Float { switch self { case .TRACE: return 0.07; case .FADE: return 0.10; case .MIRROR: return 0.09 } }
}

enum PracticeGroup: CaseIterable {
    case BASICS, PATTERNS, VOLUME, MOTIFS, SUBJECTS
    var title: String {
        switch self {
        case .BASICS: return "v50_practice_group_basics"
        case .PATTERNS: return "v50_practice_group_patterns"
        case .VOLUME: return "v50_practice_group_volume"
        case .MOTIFS: return "v50_practice_group_motifs"
        case .SUBJECTS: return "v50_practice_group_subjects"
        }
    }
}

/// Bir alıştırma: kütüphane şeklinin kayıt türü ("lib.practice.<ad>"); [id] en iyi puanın kayıt anahtarı (Android ile aynı).
struct PracticeLevel {
    static let PREFIX = "lib.practice."
    let shape: String
    let kind: PracticeKind
    let group: PracticeGroup
    var size: Float = 0.72

    var name: String { String(shape.dropFirst(Self.PREFIX.count)) }
    var id: String { "adult." + name + "." + kind.rawValue.lowercased() }
    var simple: Bool { PracticeLevels.simpleNames.contains(name) }
    var title: String { ShapeFactory.title(shape) }
}

/// Alıştırmanın kâğıttaki hedefi: puanlanan çizgiler, görünen kılavuz (simetride sol yarı) ve simetri ekseni.
struct PracticeTarget {
    let level: PracticeLevel
    let box: FRect
    let lines: [[P]]
    let guide: [[P]]
    let axis: Float?
}

enum PracticeLevels {
    /// Basit resimde de sunulan şekiller (Android `PracticeShapes.simpleIds`).
    static let simpleNames: Set<String> = ["lines", "radial", "ellipses", "spiral", "waves", "value_scale", "mandala", "honeycomb", "scales"]

    private static func l(_ name: String, _ kind: PracticeKind, _ group: PracticeGroup, _ size: Float = 0.72) -> PracticeLevel {
        PracticeLevel(shape: PracticeLevel.PREFIX + name, kind: kind, group: group, size: size)
    }

    /// Bütün alıştırmalar (Android ile aynı sıra ve kimlikler).
    static let all: [PracticeLevel] = [
        l("lines", .TRACE, .BASICS), l("radial", .TRACE, .BASICS), l("ellipses", .TRACE, .BASICS), l("spiral", .TRACE, .BASICS),
        l("waves", .TRACE, .BASICS), l("value_scale", .TRACE, .BASICS, 0.86), l("lines", .FADE, .BASICS), l("ellipses", .FADE, .BASICS),
        l("spiral", .FADE, .BASICS),
        l("honeycomb", .TRACE, .PATTERNS), l("scales", .TRACE, .PATTERNS), l("mandala", .TRACE, .PATTERNS), l("mandala", .MIRROR, .PATTERNS),
        l("scales", .FADE, .PATTERNS),
        l("cube_1p", .TRACE, .VOLUME, 0.8), l("box_2p", .TRACE, .VOLUME, 0.8), l("sphere", .TRACE, .VOLUME), l("cylinder", .TRACE, .VOLUME),
        l("cube_1p", .FADE, .VOLUME, 0.8),
        l("star8", .TRACE, .MOTIFS), l("star8", .MIRROR, .MOTIFS), l("star12", .TRACE, .MOTIFS), l("tulip", .TRACE, .MOTIFS),
        l("tulip", .MIRROR, .MOTIFS), l("rumi", .TRACE, .MOTIFS),
        l("eye", .TRACE, .SUBJECTS, 0.8), l("face", .TRACE, .SUBJECTS), l("face", .MIRROR, .SUBJECTS), l("leaf", .TRACE, .SUBJECTS, 0.8),
        l("rose", .TRACE, .SUBJECTS), l("dome", .TRACE, .SUBJECTS, 0.8), l("dome", .MIRROR, .SUBJECTS, 0.8), l("cup", .TRACE, .SUBJECTS),
        l("eye", .FADE, .SUBJECTS, 0.8), l("rose", .FADE, .SUBJECTS), l("cup", .FADE, .SUBJECTS),
    ]

    static func forCanvas(_ kind: CanvasKind?) -> [PracticeLevel] {
        switch kind {
        case .SIMPLE_ART: return all.filter(\.simple)
        case .ART: return all
        default: return []
        }
    }

    static func available(_ kind: CanvasKind?) -> Bool { kind == .SIMPLE_ART || kind == .ART }

    static let PASS = 70
    static let STARS = [70, 82, 92]
    static func stars(_ score: Int) -> Int { STARS.filter { score >= $0 }.count }
    static let HINT_PENALTY = 5

    private static let style: ShapeStyle = { var s = ShapeStyle(); s.strokeColorHex = "#2A2A33"; s.strokeWidth = 3; return s }()

    static func box(_ level: PracticeLevel, _ paperW: Int, _ paperH: Int) -> FRect {
        let aspect = ShapeFactory.aspect(level.shape)
        let side = Float(min(paperW, paperH)) * level.size
        let w = aspect >= 1 ? side : side * aspect
        let h = aspect >= 1 ? side / aspect : side
        let cx = Float(paperW) / 2, cy = Float(paperH) / 2
        return FRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    static func target(_ level: PracticeLevel, _ paperW: Int, _ paperH: Int) -> PracticeTarget {
        let box = box(level, paperW, paperH)
        let stroke = ShapeFactory.build(level.shape, box.topLeft, box.bottomRight, style: style, id: "practice-target", order: 0)
        let lines: [[P]] = (stroke.map { ShapeFactory.subPaths($0) } ?? [])
            .filter { $0.label == nil && $0.points.count >= 2 && ($0.widthScale ?? 1) > 0 }
            .map { $0.closed ? $0.points + [$0.points[0]] : $0.points }
        guard level.kind == .MIRROR else { return PracticeTarget(level: level, box: box, lines: lines, guide: lines, axis: nil) }
        let axis = box.center.x
        return PracticeTarget(level: level, box: box, lines: lines, guide: leftHalf(lines, axis), axis: axis)
    }

    /// Çizgilerin eksenin solunda kalan parçaları; eksenden geçen kenar kesişim noktasında bölünür.
    static func leftHalf(_ lines: [[P]], _ axis: Float) -> [[P]] {
        let eps: Float = 0.5
        var out: [[P]] = []
        for line in lines {
            var piece: [P] = []
            for (i, p) in line.enumerated() {
                let inside = p.x <= axis + eps
                if i > 0 {
                    let q = line[i - 1]
                    let wasInside = q.x <= axis + eps
                    if inside != wasInside && p.x != q.x {
                        let t = (axis - q.x) / (p.x - q.x)
                        piece.append(P(axis, q.y + (p.y - q.y) * t))
                        if !inside {
                            if piece.count >= 2 { out.append(piece) }
                            piece = []
                        }
                    }
                }
                if inside { piece.append(p) }
            }
            if piece.count >= 2 { out.append(piece) }
        }
        return out
    }
}
