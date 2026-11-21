import Foundation

/// v56 katman karışım modları (Android `LayerBlend`). Kodlar içerik biçiminde sabittir ([code]).
enum LayerBlend: String, KotlinEnum {
    case NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN,
         HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION, HUE, SATURATION, COLOR, LUMINOSITY, ADD

    static var fallback: LayerBlend { .NORMAL }

    var code: Int {
        switch self {
        case .NORMAL: return 0
        case .MULTIPLY: return 1
        case .SCREEN: return 2
        case .OVERLAY: return 3
        case .DARKEN: return 4
        case .LIGHTEN: return 5
        case .COLOR_DODGE: return 6
        case .COLOR_BURN: return 7
        case .HARD_LIGHT: return 8
        case .SOFT_LIGHT: return 9
        case .DIFFERENCE: return 10
        case .EXCLUSION: return 11
        case .HUE: return 12
        case .SATURATION: return 13
        case .COLOR: return 14
        case .LUMINOSITY: return 15
        case .ADD: return 16
        }
    }

    static func of(_ code: Int) -> LayerBlend { allCases.first { $0.code == code } ?? .NORMAL }
}

/// v56 seçim maskesi: kapalı çokgen (tuval koordinatı), kenar yumuşatma (px) ve ters seçim.
struct ClipMask: Codable, Equatable {
    var points: [StrokePoint]
    var feather: Float = 0
    var inverted = false
    /// v56 kırpma maskesi (2E 7.15): sınırı veren (gizlenen) nesnenin kimliği; nil: seçim alanından boyama maskesi.
    var source: String? = nil

    init(points: [StrokePoint], feather: Float = 0, inverted: Bool = false, source: String? = nil) {
        self.points = points; self.feather = feather; self.inverted = inverted; self.source = source
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        points = try c.req("points"); feather = c.v("feather", 0); inverted = c.v("inverted", false); source = c.o("source")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(points, "points"); try c.e(feather, "feather", 0); try c.e(inverted, "inverted", false); try c.o(source, "source")
    }
}

/// v56 gren: doku kimliği, ölçek, güç (0..1); [moving] true: doku tuvale sabit (kayar).
struct GrainSpec: Codable, Equatable {
    var textureId: String
    var scale: Float = 1
    var strength: Float = 1
    var moving = true

    init(textureId: String, scale: Float = 1, strength: Float = 1, moving: Bool = true) {
        self.textureId = textureId; self.scale = scale; self.strength = strength; self.moving = moving
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        textureId = try c.req("textureId"); scale = c.v("scale", 1); strength = c.v("strength", 1); moving = c.v("moving", true)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(textureId, "textureId"); try c.e(scale, "scale", 1); try c.e(strength, "strength", 1); try c.e(moving, "moving", true)
    }
}

/// v56 yansıma dalgası: genlik ve dalga boyu (px), evre (0..1), dalgacık, yukarıdan aşağı alfa gradyanı.
struct WaveSpec: Codable, Equatable {
    var amplitude: Float
    var wavelength: Float
    var phase: Float = 0
    var ripple = false
    var fadeFrom: Float = 1
    var fadeTo: Float = 1
    /// v56 aşama 6: dalga ve gradyanın başladığı ve bittiği y (tuval); eşitse elemanın kendi kutusu. Dalgacık merkezi x.
    var top: Float = 0
    var bottom: Float = 0
    var cx: Float = 0

    init(amplitude: Float, wavelength: Float, phase: Float = 0, ripple: Bool = false, fadeFrom: Float = 1, fadeTo: Float = 1,
         top: Float = 0, bottom: Float = 0, cx: Float = 0) {
        self.amplitude = amplitude; self.wavelength = wavelength; self.phase = phase; self.ripple = ripple
        self.fadeFrom = fadeFrom; self.fadeTo = fadeTo; self.top = top; self.bottom = bottom; self.cx = cx
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        amplitude = try c.req("amplitude"); wavelength = try c.req("wavelength"); phase = c.v("phase", 0)
        ripple = c.v("ripple", false); fadeFrom = c.v("fadeFrom", 1); fadeTo = c.v("fadeTo", 1)
        top = c.v("top", 0); bottom = c.v("bottom", 0); cx = c.v("cx", 0)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(amplitude, "amplitude"); try c.r(wavelength, "wavelength"); try c.e(phase, "phase", 0)
        try c.e(ripple, "ripple", false); try c.e(fadeFrom, "fadeFrom", 1); try c.e(fadeTo, "fadeTo", 1)
        try c.e(top, "top", 0); try c.e(bottom, "bottom", 0); try c.e(cx, "cx", 0)
    }
}

/// v56 resim dönüşümleri: bozma (4 köşe), çarpıtma (n×n ağ, satır satır: 4×4, gelişmiş 6×6, sıvılaştır 17×17), yansıma
/// dalgası, ara değerleme (0 en yakın, 1 çift doğrusal, 2 çift kübik).
struct ImageWarp: Codable, Equatable {
    var quad: [StrokePoint]? = nil
    var mesh: [StrokePoint]? = nil
    var wave: WaveSpec? = nil
    var filter: Int = 1

    var isEmpty: Bool { quad == nil && mesh == nil && wave == nil && filter == 1 }

    init(quad: [StrokePoint]? = nil, mesh: [StrokePoint]? = nil, wave: WaveSpec? = nil, filter: Int = 1) {
        self.quad = quad; self.mesh = mesh; self.wave = wave; self.filter = filter
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        quad = c.o("quad"); mesh = c.o("mesh"); wave = c.o("wave"); filter = c.v("filter", 1)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.o(quad, "quad"); try c.o(mesh, "mesh"); try c.o(wave, "wave"); try c.e(filter, "filter", 1)
    }
}

/// v56 simetri türleri.
enum SymmetryKind: String, KotlinEnum {
    case VERTICAL, HORIZONTAL, QUAD, RADIAL
    static var fallback: SymmetryKind { .VERTICAL }
}

/// v56 belge simetrisi: tür, kol sayısı (radyalde 2..16), eksen merkezi ve açısı (derece).
struct SymmetrySpec: Codable, Equatable {
    var kind: SymmetryKind
    var arms = 2
    var cx: Float
    var cy: Float
    var angle: Float = 0

    init(kind: SymmetryKind, arms: Int = 2, cx: Float, cy: Float, angle: Float = 0) {
        self.kind = kind; self.arms = arms; self.cx = cx; self.cy = cy; self.angle = angle
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        kind = try c.req("kind"); arms = c.v("arms", 2); cx = try c.req("cx"); cy = try c.req("cy"); angle = c.v("angle", 0)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(kind, "kind"); try c.e(arms, "arms", 2); try c.r(cx, "cx"); try c.r(cy, "cy"); try c.e(angle, "angle", 0)
    }
}

/// v56 belge düzeyindeki ek bilgiler (V11 içerikte JSON olarak).
struct DocExtras: Codable, Equatable {
    var symmetry: SymmetrySpec? = nil

    var isEmpty: Bool { symmetry == nil }

    init(symmetry: SymmetrySpec? = nil) { self.symmetry = symmetry }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        symmetry = c.o("symmetry")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.o(symmetry, "symmetry")
    }
}
