import Foundation

/// Fırçanın izi nasıl ürettiği (Android `BrushEngine`).
enum BrushEngine { case PATH, TAPERED_PATH, RIBBON, BRISTLE, DABS, SOFT_DABS, STAMP_SHAPE, PATTERN, SCATTER, HALFTONE, TUBE, PAINT, SURFACE, TRAIL, IMAGE }
enum TextureKind: String { case PASTEL_GRAIN, CHARCOAL_GRAIN, SPONGE, CANVAS_WEAVE }
enum TrailKind { case INK, DRIP }
enum SurfaceKind: String { case CARPET, WALLPAPER, WOOD_PAINT, SKIN, FUR, PLANT, WOOD, IRON, METAL }
enum StampShape { case STAR, HEART, LEAF }
enum PatternKind { case DOTS, ZIGZAG, HATCH, DOT_TRAIL }
enum HalftoneKind: String { case DOTS, LINES }
enum BrushCap { case round, square, butt }
enum BrushJoin { case round, bevel, miter }
enum BrushBlend { case srcOver, multiply, clear }

/// Bir fırçanın görünümünü belirleyen parametreler (Android `BrushStyle`).
struct BrushStyle {
    var widthFactor: Float
    var alphaFactor: Float
    var cap: BrushCap = .round
    var join: BrushJoin = .round
    var blendMode: BrushBlend = .srcOver
    var textured = false
    var smooth = true
    var engine: BrushEngine = .PATH
    var spacing: Float = 0.25
    var texture: TextureKind? = nil
    var shape: StampShape? = nil
    var pattern: PatternKind? = nil
    var halftone: HalftoneKind? = nil
    var jitter: Float = 0
    var sizeJitter: Float = 0
    var hardness: Float = 1
    var trail: TrailKind? = nil
    var surface: SurfaceKind? = nil

    var reach: Float { 1.05 + jitter + sizeJitter * 0.5 + (textured ? 0.15 : 0) }
}

private func surfaceStyle(_ kind: SurfaceKind) -> BrushStyle {
    BrushStyle(widthFactor: 2.2, alphaFactor: 1, engine: .SURFACE, spacing: 0.1, surface: kind)
}

/// v21 döküm izindeki damlaların iz genişliğine göre en uzun boyu.
let DRIP_MAX_LENGTH: Float = 3.2
let TUBE_SHADOW_MAX: Float = 0.6
let TUBE_BLUR_MAX: Float = 0.35

extension BrushType {
    var style: BrushStyle {
        switch self {
        case .PENCIL: return BrushStyle(widthFactor: 0.5, alphaFactor: 1)
        case .INK_PEN: return BrushStyle(widthFactor: 0.8, alphaFactor: 1, engine: .TAPERED_PATH)
        case .FINELINER: return BrushStyle(widthFactor: 0.35, alphaFactor: 1, smooth: false)
        case .CALLIGRAPHY: return BrushStyle(widthFactor: 1.4, alphaFactor: 1, cap: .butt, engine: .RIBBON)
        case .CHARCOAL:
            return BrushStyle(widthFactor: 1.2, alphaFactor: 0.95, textured: true, engine: .DABS, spacing: 0.08,
                              texture: .CHARCOAL_GRAIN, jitter: 0.03, sizeJitter: 0.12)
        case .MARKER: return BrushStyle(widthFactor: 1.6, alphaFactor: 0.92, cap: .square, join: .bevel, smooth: false)
        case .FLAT_BRUSH: return BrushStyle(widthFactor: 2.2, alphaFactor: 0.95, cap: .butt, textured: true, engine: .BRISTLE)
        case .HIGHLIGHTER: return BrushStyle(widthFactor: 3, alphaFactor: 0.38, cap: .butt, join: .bevel, blendMode: .multiply, smooth: false)
        case .HARD_ROUND: return BrushStyle(widthFactor: 1, alphaFactor: 1, engine: .DABS, spacing: 0.2)
        case .PASTEL:
            return BrushStyle(widthFactor: 1.3, alphaFactor: 1, textured: true, engine: .DABS, spacing: 0.1,
                              texture: .PASTEL_GRAIN, jitter: 0.04)
        case .SOFT_ROUND: return BrushStyle(widthFactor: 1.4, alphaFactor: 1, engine: .SOFT_DABS, spacing: 0.15, hardness: 0.35)
        case .AIRBRUSH: return BrushStyle(widthFactor: 2.4, alphaFactor: 0.8, textured: true, engine: .SOFT_DABS, spacing: 0.18, hardness: 0)
        case .STAMP_STAR:
            return BrushStyle(widthFactor: 1.6, alphaFactor: 1, textured: true, engine: .STAMP_SHAPE, spacing: 1.25, shape: .STAR, jitter: 0.15, sizeJitter: 0.3)
        case .STAMP_HEART:
            return BrushStyle(widthFactor: 1.6, alphaFactor: 1, textured: true, engine: .STAMP_SHAPE, spacing: 1.3, shape: .HEART, jitter: 0.1, sizeJitter: 0.25)
        case .STAMP_LEAF:
            return BrushStyle(widthFactor: 1.8, alphaFactor: 1, textured: true, engine: .STAMP_SHAPE, spacing: 0.9, shape: .LEAF, jitter: 0.2, sizeJitter: 0.3)
        case .PATTERN_DOTS: return BrushStyle(widthFactor: 1.2, alphaFactor: 1, engine: .PATTERN, pattern: .DOTS)
        case .PATTERN_ZIGZAG: return BrushStyle(widthFactor: 1.6, alphaFactor: 1, join: .miter, engine: .PATTERN, pattern: .ZIGZAG)
        case .PATTERN_HATCH: return BrushStyle(widthFactor: 2, alphaFactor: 1, cap: .butt, engine: .PATTERN, pattern: .HATCH)
        case .TEXTURE_SPONGE:
            return BrushStyle(widthFactor: 2, alphaFactor: 0.9, textured: true, engine: .DABS, spacing: 0.3, texture: .SPONGE, jitter: 0.06, sizeJitter: 0.2)
        case .TEXTURE_CANVAS:
            return BrushStyle(widthFactor: 1.8, alphaFactor: 1, textured: true, engine: .DABS, spacing: 0.15, texture: .CANVAS_WEAVE)
        case .TEXTURE_SPLATTER: return BrushStyle(widthFactor: 2, alphaFactor: 1, textured: true, engine: .SCATTER, jitter: 0.9, sizeJitter: 1)
        case .HALFTONE_DOTS: return BrushStyle(widthFactor: 2.4, alphaFactor: 1, engine: .HALFTONE, spacing: 0.15, halftone: .DOTS)
        case .HALFTONE_LINES: return BrushStyle(widthFactor: 2.4, alphaFactor: 1, engine: .HALFTONE, spacing: 0.15, halftone: .LINES)
        case .NOTE_PENCIL: return BrushStyle(widthFactor: 0.6, alphaFactor: 0.78)
        case .NOTE_BALLPOINT: return BrushStyle(widthFactor: 0.42, alphaFactor: 0.96)
        case .NOTE_FOUNTAIN: return BrushStyle(widthFactor: 0.62, alphaFactor: 1, engine: .TAPERED_PATH)
        case .NOTE_GEL: return BrushStyle(widthFactor: 0.62, alphaFactor: 1)
        case .NOTE_FINELINER: return BrushStyle(widthFactor: 0.3, alphaFactor: 1, smooth: false)
        case .NOTE_FELT: return BrushStyle(widthFactor: 1, alphaFactor: 0.9)
        case .NOTE_HIGHLIGHTER: return BrushStyle(widthFactor: 2.6, alphaFactor: 0.4, cap: .butt, join: .bevel, blendMode: .multiply, smooth: false)
        case .DOT_PEN: return BrushStyle(widthFactor: 1, alphaFactor: 1, engine: .PATTERN, pattern: .DOT_TRAIL)
        case .BRUSH_3D: return BrushStyle(widthFactor: 1.3, alphaFactor: 1, engine: .TUBE, spacing: 0.08)
        case .INK_TRAIL: return BrushStyle(widthFactor: 1.1, alphaFactor: 1, textured: true, engine: .TRAIL, spacing: 0.12, jitter: 0.35, trail: .INK)
        case .DRIP_TRAIL: return BrushStyle(widthFactor: 1.2, alphaFactor: 1, textured: true, engine: .TRAIL, spacing: 0.12, trail: .DRIP)
        case .PAINT_TRAIL: return BrushStyle(widthFactor: 1.6, alphaFactor: 1, textured: true, engine: .PAINT, spacing: 0.08)
        case .PATTERN_CARPET: return surfaceStyle(.CARPET)
        case .PATTERN_WALLPAPER: return surfaceStyle(.WALLPAPER)
        case .PATTERN_WOODPAINT: return surfaceStyle(.WOOD_PAINT)
        case .TEXTURE_SKIN: return surfaceStyle(.SKIN)
        case .TEXTURE_FUR: return surfaceStyle(.FUR)
        case .TEXTURE_PLANT: return surfaceStyle(.PLANT)
        case .TEXTURE_WOOD: return surfaceStyle(.WOOD)
        case .TEXTURE_IRON: return surfaceStyle(.IRON)
        case .TEXTURE_METAL: return surfaceStyle(.METAL)
        case .ERASER: return BrushStyle(widthFactor: 2, alphaFactor: 1, blendMode: .clear)
        case .IMAGE, .TEXT, .SHAPE, .DIAGRAM, .FORMULA:
            return BrushStyle(widthFactor: 0, alphaFactor: 1, cap: .butt, join: .miter, smooth: false, engine: .IMAGE)
        }
    }

    /// Fırçanın tuval birimindeki gerçek iz genişliği.
    func inkWidth(_ sizePx: Float, strength: Float = 1) -> Float { max(1, sizePx * style.widthFactor * pressureWidth(strength)) }

    /// Her fırçanın varsayılan boyutu.
    var defaultSize: Float {
        switch self {
        case .PENCIL: return 6
        case .INK_PEN: return 8
        case .FINELINER: return 6
        case .CALLIGRAPHY: return 14
        case .CHARCOAL: return 16
        case .MARKER: return 14
        case .FLAT_BRUSH: return 18
        case .HIGHLIGHTER: return 24
        case .HARD_ROUND: return 14
        case .PASTEL: return 18
        case .SOFT_ROUND: return 28
        case .AIRBRUSH: return 36
        case .STAMP_STAR, .STAMP_HEART: return 18
        case .STAMP_LEAF: return 16
        case .PATTERN_DOTS: return 12
        case .PATTERN_ZIGZAG: return 14
        case .PATTERN_HATCH: return 16
        case .TEXTURE_SPONGE, .TEXTURE_CANVAS: return 22
        case .TEXTURE_SPLATTER: return 16
        case .HALFTONE_DOTS, .HALFTONE_LINES: return 20
        case .NOTE_PENCIL, .NOTE_BALLPOINT: return 5
        case .NOTE_FOUNTAIN, .NOTE_GEL: return 6
        case .NOTE_FINELINER: return 5
        case .NOTE_FELT: return 8
        case .NOTE_HIGHLIGHTER: return 16
        case .DOT_PEN: return 6
        case .BRUSH_3D: return 18
        case .INK_TRAIL: return 14
        case .DRIP_TRAIL: return 16
        case .PAINT_TRAIL: return 18
        case .PATTERN_CARPET, .PATTERN_WALLPAPER, .PATTERN_WOODPAINT: return 36
        case .TEXTURE_SKIN, .TEXTURE_FUR, .TEXTURE_PLANT, .TEXTURE_WOOD, .TEXTURE_IRON, .TEXTURE_METAL: return 36
        case .ERASER: return 30
        case .IMAGE: return 1
        case .SHAPE, .DIAGRAM: return 4
        case .TEXT: return 48
        case .FORMULA: return 56
        }
    }

    /// v20 varsayılan damga aralığı.
    var defaultSpacing: Float? {
        let st = style
        switch st.engine {
        case .DABS, .SOFT_DABS, .STAMP_SHAPE, .HALFTONE, .SURFACE, .TRAIL: return st.spacing
        case .SCATTER: return 0.45
        case .PATTERN:
            switch st.pattern {
            case .DOTS: return 1.6
            case .DOT_TRAIL: return 2.2
            case .HATCH: return 0.15
            case .ZIGZAG: return 0.55
            case nil: return nil
            }
        default: return nil
        }
    }

    var hasFlatNib: Bool { style.engine == .RIBBON }
    var defaultTiltWidth: Float { BrushDynamics.of(self).tiltWiden }
    var defaultTiltLight: Float { BrushDynamics.of(self).tiltLighten }

    /// İzin fırça boyutuna göre en fazla ne kadar uzağa ulaşabileceği.
    func reachFactor(_ params: BrushParams?) -> Float {
        let st = style
        let widen = BrushDynamics.of(self).with(params).maxWidth
        var r = st.widthFactor * 0.5 * st.reach * widen * 1.15
        if self == .DRIP_TRAIL { r += st.widthFactor * DRIP_MAX_LENGTH * widen }
        if self == .BRUSH_3D {
            let tube = params?.tube ?? TubeParams()
            r += st.widthFactor * (min(max(tube.shadow, 0), 1) * TUBE_SHADOW_MAX + min(max(tube.shadowSoftness, 0), 1) * TUBE_BLUR_MAX + 0.1) * widen
        }
        return max(1.6, r)
    }
}

/// Çizim gücünün kalınlığa etkisi.
func pressureWidth(_ strength: Float) -> Float { 0.45 + 0.55 * min(max(strength, 0), 1) }
/// Çizim gücünün koyuluğa etkisi.
func pressureAlpha(_ strength: Float) -> Float { 0.35 + 0.65 * min(max(strength, 0), 1) }

/// Fırça seçicideki kategoriler (Android `BrushCategory`).
enum BrushCategory: String, CaseIterable {
    case FINE_ART, BROAD, HARD, SOFT, SHAPE, TRAIL, PATTERN, TEXTURE, LIVING, MATERIAL, HALFTONE, EFFECTS
    case NOTE_WRITING, NOTE_MARKING, NOTE_EFFECTS

    var brushes: [BrushType] {
        switch self {
        case .FINE_ART: return [.PENCIL, .INK_PEN, .FINELINER, .CALLIGRAPHY, .CHARCOAL]
        case .BROAD: return [.MARKER, .FLAT_BRUSH, .HIGHLIGHTER]
        case .HARD: return [.HARD_ROUND, .PASTEL]
        case .SOFT: return [.SOFT_ROUND, .AIRBRUSH]
        case .SHAPE: return [.STAMP_STAR, .STAMP_HEART, .STAMP_LEAF]
        case .TRAIL: return [.INK_TRAIL, .DRIP_TRAIL, .PAINT_TRAIL]
        case .PATTERN: return [.PATTERN_DOTS, .PATTERN_ZIGZAG, .PATTERN_HATCH, .PATTERN_CARPET, .PATTERN_WALLPAPER, .PATTERN_WOODPAINT]
        case .TEXTURE: return [.TEXTURE_SPONGE, .TEXTURE_CANVAS, .TEXTURE_SPLATTER]
        case .LIVING: return [.TEXTURE_SKIN, .TEXTURE_FUR, .TEXTURE_PLANT]
        case .MATERIAL: return [.TEXTURE_WOOD, .TEXTURE_IRON, .TEXTURE_METAL]
        case .HALFTONE: return [.HALFTONE_DOTS, .HALFTONE_LINES]
        case .EFFECTS: return [.DOT_PEN, .BRUSH_3D]
        case .NOTE_WRITING: return [.NOTE_PENCIL, .NOTE_BALLPOINT, .NOTE_FOUNTAIN, .NOTE_GEL, .NOTE_FINELINER]
        case .NOTE_MARKING: return [.NOTE_FELT, .NOTE_HIGHLIGHTER]
        case .NOTE_EFFECTS: return [.DOT_PEN, .BRUSH_3D]
        }
    }

    var kind: CanvasKind {
        switch self {
        case .NOTE_WRITING, .NOTE_MARKING, .NOTE_EFFECTS: return .NOTES
        default: return .ART
        }
    }

    static func of(_ type: BrushType) -> BrushCategory? { allCases.first { $0.brushes.contains(type) } }

    static func forKind(_ kind: CanvasKind) -> [BrushCategory] {
        switch kind {
        case .SIMPLE_ART: return [.FINE_ART, .BROAD, .SOFT]
        case .KIDS: return [.FINE_ART, .BROAD, .SHAPE]
        case .SIMPLE_NOTES: return [.NOTE_WRITING, .NOTE_MARKING, .NOTE_EFFECTS]
        default: return allCases.filter { $0.kind == kind.base }
        }
    }

    /// Android metin kaynağının adı.
    var titleKey: String { self == .NOTE_EFFECTS ? "brush_cat_effects" : "brush_cat_" + rawValue.lowercased() }
}

/// Tuval türünün fırça seçicisindeki fırçalar.
func pickerBrushTypes(_ kind: CanvasKind) -> [BrushType] {
    if kind.simple { return BrushCategory.forKind(kind).flatMap(\.brushes) }
    return BrushCategory.forKind(kind.isNotes ? .NOTES : .ART).flatMap(\.brushes)
}

/// Tuval türünün varsayılan fırçası.
func defaultBrush(_ kind: CanvasKind) -> BrushType {
    if kind == .KIDS { return .MARKER }
    return kind.isNotes ? .NOTE_BALLPOINT : .PENCIL
}

/// v18 kalem dinamikleri (Android `BrushDynamics`).
struct BrushDynamics {
    let minWidth: Float
    let pressureAlpha: Bool
    let tiltWiden: Float
    let tiltLighten: Float
    var threshold: Float = 0

    private func effectiveTilt(_ tilt: Float) -> Float {
        let t = min(max(tilt, 0), 1)
        return threshold <= 0 ? t : min(max((t - threshold) / (1 - threshold), 0), 1)
    }

    func width(_ pressure: Float, _ tilt: Float) -> Float {
        let p = min(max(pressure, 0), 1)
        let curve = Float(pow(Double(p), 0.7))
        return (minWidth + (1 - minWidth) * curve) * (1 + tiltWiden * effectiveTilt(tilt))
    }

    func alpha(_ pressure: Float, _ tilt: Float) -> Float {
        let p = min(max(pressure, 0), 1)
        let a: Float = pressureAlpha ? 0.3 + 0.7 * p : 1
        return min(max(a * (1 - tiltLighten * effectiveTilt(tilt)), 0.05), 1)
    }

    var maxWidth: Float { 1 + tiltWiden }

    func with(_ params: BrushParams?) -> BrushDynamics {
        guard let params, params.tiltWidth != nil || params.tiltLight != nil || params.tiltThreshold > 0 else { return self }
        return BrushDynamics(minWidth: minWidth, pressureAlpha: pressureAlpha,
                             tiltWiden: params.tiltWidth.map { min(max($0, 0), 2) } ?? tiltWiden,
                             tiltLighten: params.tiltLight.map { min(max($0, 0), 1) } ?? tiltLighten,
                             threshold: min(max(params.tiltThreshold / 90, 0), 0.95))
    }

    static let PENCIL = BrushDynamics(minWidth: 0.35, pressureAlpha: false, tiltWiden: 1.6, tiltLighten: 0)
    static let GRAPHITE = BrushDynamics(minWidth: 0.3, pressureAlpha: true, tiltWiden: 1.8, tiltLighten: 0.45)
    static let SOFT = BrushDynamics(minWidth: 0.35, pressureAlpha: true, tiltWiden: 0, tiltLighten: 0)
    static let PEN = BrushDynamics(minWidth: 0.22, pressureAlpha: false, tiltWiden: 0, tiltLighten: 0)
    static let MARKER = BrushDynamics(minWidth: 0.6, pressureAlpha: false, tiltWiden: 0, tiltLighten: 0)
    static let HIGHLIGHT = BrushDynamics(minWidth: 0.8, pressureAlpha: false, tiltWiden: 0, tiltLighten: 0)

    static func of(_ type: BrushType) -> BrushDynamics {
        switch type {
        case .PENCIL, .NOTE_PENCIL: return PENCIL
        case .CHARCOAL, .PASTEL: return GRAPHITE
        case .SOFT_ROUND, .AIRBRUSH, .HARD_ROUND, .TEXTURE_SPONGE, .TEXTURE_CANVAS, .PATTERN_CARPET, .PATTERN_WALLPAPER,
             .PATTERN_WOODPAINT, .TEXTURE_SKIN, .TEXTURE_FUR, .TEXTURE_PLANT, .TEXTURE_WOOD, .TEXTURE_IRON, .TEXTURE_METAL:
            return SOFT
        case .PAINT_TRAIL, .MARKER, .NOTE_FELT, .FLAT_BRUSH: return MARKER
        case .HIGHLIGHTER, .NOTE_HIGHLIGHTER: return HIGHLIGHT
        default: return PEN
        }
    }
}

/// v21 3B çizim modunun geometrisi (Android `Iso3D`).
enum Iso3D {
    static let C: Float = 0.8660254
    static let S: Float = 0.5

    static func matrix(_ plane: Plane3D) -> [Float] {
        switch plane {
        case .FREE: return [1, 0, 0, 1]
        case .FRONT: return [C, S, 0, 1]
        case .SIDE: return [C, -S, 0, 1]
        case .TOP: return [C, S, -C, S]
        }
    }

    static func project(_ plane: Plane3D, _ anchor: P, _ x: Float, _ y: Float) -> P {
        if plane == .FREE { return P(x, y) }
        let m = matrix(plane)
        let dx = x - anchor.x, dy = y - anchor.y
        return P(anchor.x + m[0] * dx + m[2] * dy, anchor.y + m[1] * dx + m[3] * dy)
    }

    static func unproject(_ plane: Plane3D, _ anchor: P, _ x: Float, _ y: Float) -> P {
        if plane == .FREE { return P(x, y) }
        let m = matrix(plane)
        let det = m[0] * m[3] - m[2] * m[1]
        let dx = x - anchor.x, dy = y - anchor.y
        return P(anchor.x + (m[3] * dx - m[2] * dy) / det, anchor.y + (-m[1] * dx + m[0] * dy) / det)
    }

    static func extrudeDirection(_ plane: Plane3D) -> P {
        switch plane {
        case .FREE: return P(0.7071, 0.7071)
        case .FRONT: return P(-C, S)
        case .SIDE: return P(C, S)
        case .TOP: return P(0, 1)
        }
    }

    static func extrudeVector(_ depth: Depth3D) -> P {
        if let x = depth.ex, let y = depth.ey { return P(x, y) }
        return extrudeDirection(depth.plane)
    }

    static func extrudeReach(_ depth: Depth3D?, _ inkWidth: Float) -> Float {
        let len = extrudeLength(depth, inkWidth)
        guard len > 0, let depth else { return 0 }
        let v = extrudeVector(depth)
        return len * max(1, (v.x * v.x + v.y * v.y).squareRoot())
    }

    static func extrudeLength(_ depth: Depth3D?, _ inkWidth: Float) -> Float {
        guard let depth, depth.depth > 0.01 else { return 0 }
        return min(min(max(depth.depth, 0), 1) * (10 + 1.2 * inkWidth), 160)
    }

    static let gridDirections: [P] = [P(C, S), P(-C, S), P(0, 1)]
}

extension BrushType {
    /// Android metin kaynağının adı (`BrushIcons.labelRes`).
    var titleKey: String {
        switch self {
        case .FLAT_BRUSH: return "brush_flat"
        case .BRUSH_3D: return "brush_3d"
        default: return "brush_" + rawValue.lowercased()
        }
    }
}
