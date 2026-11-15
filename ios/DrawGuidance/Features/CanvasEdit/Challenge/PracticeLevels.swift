import Foundation

/*
 * v50 yetişkinler için çizim alıştırmaları (Android `PracticeLevels`): basit resim ve gelişmiş resim tuvallerinde,
 * çocuk görevinden ayrı. Şekiller kütüphanededir ("lib.practice.*", catalog-vectors.bin). Üç görev türü: üzerinden
 * çiz, kaybolan kılavuz (kılavuz birkaç saniye görünür, sonra ezberden çizilir) ve simetri tamamla (sol yarı ve eksen
 * görünür). Basit resimde yalnız temel alıştırmalar ve kolay desenler, gelişmiş resimde hepsi.
 */

enum PracticeKind: String {
    /// v51 COPY: bakarak çiz (örnek köşede, yer ve boy serbest); TIMED: süreli çizim (hız da puanlanır).
    case TRACE, FADE, MIRROR, COPY, TIMED
    var label: String {
        switch self {
        case .TRACE: return "v50_practice_kind_trace"; case .FADE: return "v50_practice_kind_fade"; case .MIRROR: return "v50_practice_kind_mirror"
        case .COPY: return "v51_practice_kind_copy"; case .TIMED: return "v51_practice_kind_timed"
        }
    }
    var intro: String {
        switch self {
        case .TRACE: return "v50_practice_intro_trace"; case .FADE: return "v50_practice_intro_fade"; case .MIRROR: return "v50_practice_intro_mirror"
        case .COPY: return "v51_practice_intro_copy"; case .TIMED: return "v51_practice_intro_timed"
        }
    }
    /// Tam puan ve sıfır puan uzaklığı (hedef köşegenine oran).
    /// v52 daha sıkı (Android ile aynı).
    var near: Float { switch self { case .TRACE: return 0.010; case .FADE: return 0.016; case .MIRROR: return 0.014; case .COPY: return 0.020; case .TIMED: return 0.012 } }
    var far: Float { switch self { case .TRACE: return 0.036; case .FADE: return 0.052; case .MIRROR: return 0.048; case .COPY: return 0.060; case .TIMED: return 0.042 } }
}

enum PracticeGroup: CaseIterable {
    case BASICS, PATTERNS, VOLUME, MOTIFS, SUBJECTS, OBSERVE, FIGURES
    var title: String {
        switch self {
        case .OBSERVE: return "v51_practice_group_observe"
        case .FIGURES: return "v51_practice_group_figures"
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
    static let SKETCH = "lib.sketch."
    let shape: String
    let kind: PracticeKind
    let group: PracticeGroup
    var size: Float = 0.72
    /// v51 ödüllü reklam izlenerek açılır (Premium'da açık).
    var reward = false

    var name: String {
        if shape.hasPrefix(Self.PREFIX) { return String(shape.dropFirst(Self.PREFIX.count)) }
        if shape.hasPrefix(Self.SKETCH) { return String(shape.dropFirst(Self.SKETCH.count)) }
        return shape
    }
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
    /// v51 Bakarak çiz: örneğin durduğu küçük çerçeve.
    var reference: FRect? = nil
    /// v51 Süreli çizim: süre (saniye).
    var seconds = 0
}

enum PracticeLevels {
    /// Basit resimde de sunulan şekiller (Android `PracticeShapes.simpleIds`).
    static let simpleNames: Set<String> = ["lines", "radial", "ellipses", "spiral", "waves", "value_scale", "mandala", "honeycomb", "scales"]

    private static func l(_ name: String, _ kind: PracticeKind, _ group: PracticeGroup, _ size: Float = 0.72, reward: Bool = false) -> PracticeLevel {
        PracticeLevel(shape: PracticeLevel.PREFIX + name, kind: kind, group: group, size: size, reward: reward)
    }

    /// v51 eskiz kütüphanesinden alıştırma (yalnız gelişmiş resim).
    private static func k(_ name: String, _ kind: PracticeKind, _ group: PracticeGroup, _ size: Float = 0.74, reward: Bool = false) -> PracticeLevel {
        PracticeLevel(shape: PracticeLevel.SKETCH + name, kind: kind, group: group, size: size, reward: reward)
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
        // v51 yeni alıştırmalar (Android ile aynı sıra; reward: ödüllü reklamla açılır)
        l("lines", .TIMED, .BASICS), l("ellipses", .TIMED, .BASICS), l("radial", .FADE, .BASICS), l("waves", .FADE, .BASICS),
        l("spiral", .TIMED, .BASICS), l("waves", .TIMED, .BASICS),
        l("honeycomb", .FADE, .PATTERNS), l("honeycomb", .MIRROR, .PATTERNS), l("mandala", .FADE, .PATTERNS, reward: true),
        l("scales", .TIMED, .PATTERNS),
        l("box_2p", .FADE, .VOLUME, 0.8), l("sphere", .FADE, .VOLUME), l("cylinder", .FADE, .VOLUME),
        l("box_2p", .COPY, .VOLUME, 0.8, reward: true), l("cylinder", .COPY, .VOLUME),
        l("star12", .MIRROR, .MOTIFS), l("rumi", .MIRROR, .MOTIFS), l("star8", .FADE, .MOTIFS), l("rumi", .FADE, .MOTIFS, reward: true),
        l("star12", .TIMED, .MOTIFS),
        l("leaf", .MIRROR, .SUBJECTS, 0.8), l("cup", .MIRROR, .SUBJECTS), l("face", .FADE, .SUBJECTS, reward: true), l("tulip", .COPY, .SUBJECTS),
        k("cat", .TRACE, .OBSERVE), k("bird", .TRACE, .OBSERVE), k("apple", .COPY, .OBSERVE), k("pear", .COPY, .OBSERVE),
        k("butterfly", .MIRROR, .OBSERVE, reward: true), k("mug", .COPY, .OBSERVE), k("teapot", .TRACE, .OBSERVE),
        k("vase", .MIRROR, .OBSERVE), k("tree", .TRACE, .OBSERVE, reward: true), k("mushroom", .MIRROR, .OBSERVE),
        k("fish", .COPY, .OBSERVE), k("chair", .COPY, .OBSERVE, reward: true), k("lighthouse", .MIRROR, .OBSERVE),
        k("car_side", .COPY, .OBSERVE), k("bicycle", .TRACE, .OBSERVE, reward: true), k("sailboat", .COPY, .OBSERVE),
        k("owl", .TRACE, .OBSERVE), k("house_perspective", .TRACE, .OBSERVE, reward: true),
        k("figure_standing", .TRACE, .FIGURES), k("head_construction", .TRACE, .FIGURES), k("face_front", .MIRROR, .FIGURES),
        k("eye_detail", .COPY, .FIGURES), k("nose_detail", .TRACE, .FIGURES, reward: true), k("lips_detail", .MIRROR, .FIGURES),
        k("ear_detail", .COPY, .FIGURES), k("face_three_quarter", .TRACE, .FIGURES, reward: true), k("face_profile", .COPY, .FIGURES),
        k("hand_open", .TRACE, .FIGURES, reward: true), k("figure_walking", .TRACE, .FIGURES), k("figure_sitting", .COPY, .FIGURES, reward: true),
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

    /// v51 Bakarak çiz: örnek ve çizim çerçevesinin boyu (kâğıdın kısa kenarına oran).
    static let COPY_REF: Float = 0.30
    static let COPY_SIZE: Float = 0.58
    private static let COPY_PAD: Float = 0.04

    /// v51 Süreli çizim: süre, kılavuzun boyuna göre (köşegen başına 2,5 sn), 15–90 sn.
    static func seconds(_ lines: [[P]], _ box: FRect) -> Int {
        let diag = max(hypot(box.width, box.height), 1)
        return min(max(Int(10 + ChallengeScore.length(lines) / diag * 2.5), 15), 90)
    }

    private static func fit(_ aspect: Float, _ side: Float, _ cx: Float, _ cy: Float) -> FRect {
        let w = aspect >= 1 ? side : side * aspect
        let h = aspect >= 1 ? side / aspect : side
        return FRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    static func box(_ level: PracticeLevel, _ paperW: Int, _ paperH: Int) -> FRect {
        let aspect = ShapeFactory.aspect(level.shape)
        if level.kind == .COPY {
            let m = Float(min(paperW, paperH)), side = m * COPY_SIZE
            return fit(aspect, side, Float(paperW) - m * COPY_PAD - side / 2, Float(paperH) - m * COPY_PAD - side / 2)
        }
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
        if level.kind == .COPY {
            let m = Float(min(paperW, paperH)), side = m * COPY_REF
            let ref = fit(ShapeFactory.aspect(level.shape), side, m * COPY_PAD + side / 2, m * COPY_PAD + side / 2)
            let k = ref.width / max(box.width, 1)
            let guide = lines.map { line in line.map { P(ref.left + ($0.x - box.left) * k, ref.top + ($0.y - box.top) * k) } }
            return PracticeTarget(level: level, box: box, lines: lines, guide: guide, axis: nil, reference: ref)
        }
        if level.kind == .TIMED {
            return PracticeTarget(level: level, box: box, lines: lines, guide: lines, axis: nil, seconds: seconds(lines, box))
        }
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
