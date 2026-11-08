import Foundation

// Android core/model `Stroke.kt` karşılığı. Alan adları ve enum adları kayıt biçimiyle (JSON) birebir aynıdır.

/// Fırça tipleri. Sıra Kotlin enum'uyla aynıdır (kayıtta sabit kodlar kullanılır: `ContentCodec`).
enum BrushType: String, KotlinEnum {
    // Sanat kalemleri
    case PENCIL, INK_PEN, FINELINER, CALLIGRAPHY, CHARCOAL
    // Geniş uçlu
    case MARKER, FLAT_BRUSH, HIGHLIGHTER
    // Sert uçlu
    case HARD_ROUND, PASTEL
    // Yumuşak uçlu
    case SOFT_ROUND, AIRBRUSH
    // Şekil izi
    case STAMP_STAR, STAMP_HEART, STAMP_LEAF
    // Desen
    case PATTERN_DOTS, PATTERN_ZIGZAG, PATTERN_HATCH
    // Doku
    case TEXTURE_SPONGE, TEXTURE_CANVAS, TEXTURE_SPLATTER
    // Yarım ton
    case HALFTONE_DOTS, HALFTONE_LINES
    // Çalışma / not kalemleri
    case NOTE_PENCIL, NOTE_BALLPOINT, NOTE_FOUNTAIN, NOTE_GEL, NOTE_FINELINER, NOTE_FELT, NOTE_HIGHLIGHTER
    // v15
    case DOT_PEN, BRUSH_3D
    // v21 izler
    case INK_TRAIL, DRIP_TRAIL, PAINT_TRAIL
    // v21 desenler
    case PATTERN_CARPET, PATTERN_WALLPAPER, PATTERN_WOODPAINT
    // v21 dokular
    case TEXTURE_SKIN, TEXTURE_FUR, TEXTURE_PLANT, TEXTURE_WOOD, TEXTURE_IRON, TEXTURE_METAL
    // Araçlar
    case ERASER
    /// Vektör şekil: noktalar alt yolların uç uca eklenmiş noktalarıdır; yapısı `Stroke.shape`.
    case SHAPE
    /// Diyagram: noktalar 4 köşedir.
    case DIAGRAM
    /// Yazı elemanı: noktalar yazı kutusunun 4 köşesidir (sol-üst, sağ-üst, sağ-alt, sol-alt).
    case TEXT
    /// Dışarıdan eklenen resim: noktalar resmin 4 köşesidir.
    case IMAGE
    /// Formül: noktalar kutunun 4 köşesidir.
    case FORMULA

    static var fallback: BrushType { .PENCIL }

    /// Noktaları 4 köşeli bir kutu olan elemanlar.
    var isQuad: Bool { self == .IMAGE || self == .TEXT || self == .DIAGRAM || self == .FORMULA }

    /// Kendi başına nesne olan elemanlar (silgiyle silinmez, değince tamamen silinir).
    var isObject: Bool { self == .IMAGE || self == .TEXT || self == .SHAPE || self == .DIAGRAM || self == .FORMULA }

    /// Silginin etkilediği çizimler.
    var isErasable: Bool { !isObject && self != .ERASER }
}

enum FontGroup: String, CaseIterable { case SANS, SERIF, HAND, DISPLAY, MONO, COMIC }

/// Yazı tipi: `asset` uygulamayla gelen dosya (`fonts/…`), yoksa sistem ailesi.
enum TextFont: String, KotlinEnum {
    case SANS, SERIF, MONO, CONDENSED, HANDWRITING, CASUAL
    case LIGHT, MEDIUM, BLACK, SMALLCAPS, SERIF_MONO
    case MONTSERRAT, POPPINS, NUNITO, RALEWAY, QUICKSAND, COMFORTAA, OSWALD, MERRIWEATHER, LORA, PLAYFAIR, ROBOTO_SLAB
    case CAVEAT, KALAM, PATRICK_HAND, DANCING_SCRIPT, PACIFICO, GREAT_VIBES, AMATIC, BEBAS, LOBSTER, ABRIL, RIGHTEOUS
    case PIXEL, JETBRAINS_MONO, SOURCE_CODE, TYPEWRITER
    case COMIC_RELIEF, COMIC_RELIEF_BOLD, BANGERS, LUCKIEST_GUY, SHANTELL, BOOGALOO, GRANDSTANDER, BUNGEE, BUNGEE_SHADE
    case RUBIK_BUBBLES, MARGARINE, MANSALVA

    static var fallback: TextFont { .SANS }

    private var info: (family: String, title: String, group: FontGroup, asset: String?) {
        switch self {
        case .SANS: return ("sans-serif", "Roboto", .SANS, nil)
        case .SERIF: return ("serif", "Noto Serif", .SERIF, nil)
        case .MONO: return ("monospace", "Droid Sans Mono", .MONO, nil)
        case .CONDENSED: return ("sans-serif-condensed", "Roboto Condensed", .SANS, nil)
        case .HANDWRITING: return ("cursive", "El Yazısı", .HAND, nil)
        case .CASUAL: return ("casual", "Gündelik", .HAND, nil)
        case .LIGHT: return ("sans-serif-light", "Roboto İnce", .SANS, nil)
        case .MEDIUM: return ("sans-serif-medium", "Roboto Orta", .SANS, nil)
        case .BLACK: return ("sans-serif-black", "Roboto Kalın", .SANS, nil)
        case .SMALLCAPS: return ("sans-serif-smallcaps", "Küçük Büyük Harf", .DISPLAY, nil)
        case .SERIF_MONO: return ("serif-monospace", "Daktilo", .MONO, nil)
        case .MONTSERRAT: return ("sans-serif", "Montserrat", .SANS, "fonts/montserrat.ttf")
        case .POPPINS: return ("sans-serif", "Poppins", .SANS, "fonts/poppins.ttf")
        case .NUNITO: return ("sans-serif", "Nunito", .SANS, "fonts/nunito.ttf")
        case .RALEWAY: return ("sans-serif", "Raleway", .SANS, "fonts/raleway.ttf")
        case .QUICKSAND: return ("sans-serif", "Quicksand", .SANS, "fonts/quicksand.ttf")
        case .COMFORTAA: return ("sans-serif", "Comfortaa", .SANS, "fonts/comfortaa.ttf")
        case .OSWALD: return ("sans-serif-condensed", "Oswald", .SANS, "fonts/oswald.ttf")
        case .MERRIWEATHER: return ("serif", "Merriweather", .SERIF, "fonts/merriweather.ttf")
        case .LORA: return ("serif", "Lora", .SERIF, "fonts/lora.ttf")
        case .PLAYFAIR: return ("serif", "Playfair Display", .SERIF, "fonts/playfair_display.ttf")
        case .ROBOTO_SLAB: return ("serif", "Roboto Slab", .SERIF, "fonts/roboto_slab.ttf")
        case .CAVEAT: return ("cursive", "Caveat", .HAND, "fonts/caveat.ttf")
        case .KALAM: return ("cursive", "Kalam", .HAND, "fonts/kalam.ttf")
        case .PATRICK_HAND: return ("casual", "Patrick Hand", .HAND, "fonts/patrick_hand.ttf")
        case .DANCING_SCRIPT: return ("cursive", "Dancing Script", .HAND, "fonts/dancing_script.ttf")
        case .PACIFICO: return ("cursive", "Pacifico", .HAND, "fonts/pacifico.ttf")
        case .GREAT_VIBES: return ("cursive", "Great Vibes", .HAND, "fonts/great_vibes.ttf")
        case .AMATIC: return ("casual", "Amatic SC", .HAND, "fonts/amatic_sc.ttf")
        case .BEBAS: return ("sans-serif-condensed", "Bebas Neue", .DISPLAY, "fonts/bebas_neue.ttf")
        case .LOBSTER: return ("cursive", "Lobster", .DISPLAY, "fonts/lobster.ttf")
        case .ABRIL: return ("serif", "Abril Fatface", .DISPLAY, "fonts/abril_fatface.ttf")
        case .RIGHTEOUS: return ("sans-serif", "Righteous", .DISPLAY, "fonts/righteous.ttf")
        case .PIXEL: return ("monospace", "Press Start 2P", .DISPLAY, "fonts/press_start_2p.ttf")
        case .JETBRAINS_MONO: return ("monospace", "JetBrains Mono", .MONO, "fonts/jetbrains_mono.ttf")
        case .SOURCE_CODE: return ("monospace", "Source Code Pro", .MONO, "fonts/source_code_pro.ttf")
        case .TYPEWRITER: return ("serif-monospace", "Special Elite", .MONO, "fonts/special_elite.ttf")
        case .COMIC_RELIEF: return ("casual", "Comic Relief", .COMIC, "fonts/comic_relief.ttf")
        case .COMIC_RELIEF_BOLD: return ("casual", "Comic Relief Kalın", .COMIC, "fonts/comic_relief_bold.ttf")
        case .BANGERS: return ("sans-serif-condensed", "Bangers", .COMIC, "fonts/bangers.ttf")
        case .LUCKIEST_GUY: return ("sans-serif", "Luckiest Guy", .COMIC, "fonts/luckiest_guy.ttf")
        case .SHANTELL: return ("casual", "Shantell Sans", .COMIC, "fonts/shantell_sans.ttf")
        case .BOOGALOO: return ("casual", "Boogaloo", .COMIC, "fonts/boogaloo.ttf")
        case .GRANDSTANDER: return ("casual", "Grandstander", .COMIC, "fonts/grandstander.ttf")
        case .BUNGEE: return ("sans-serif", "Bungee", .COMIC, "fonts/bungee.ttf")
        case .BUNGEE_SHADE: return ("sans-serif", "Bungee Shade", .COMIC, "fonts/bungee_shade.ttf")
        case .RUBIK_BUBBLES: return ("sans-serif", "Rubik Bubbles", .COMIC, "fonts/rubik_bubbles.ttf")
        case .MARGARINE: return ("casual", "Margarine", .COMIC, "fonts/margarine.ttf")
        case .MANSALVA: return ("cursive", "Mansalva", .COMIC, "fonts/mansalva.ttf")
        }
    }

    var family: String { info.family }
    var title: String { info.title }
    var group: FontGroup { info.group }
    var asset: String? { info.asset }
}

enum TextParagraph: String, KotlinEnum {
    case NORMAL, H1, H2, H3, BULLET, NUMBERED, CHECKLIST
    static var fallback: TextParagraph { .NORMAL }

    var sizeFactor: Float {
        switch self {
        case .H1: return 2
        case .H2: return 1.6
        case .H3: return 1.3
        default: return 1
        }
    }
    var heading: Bool { self == .H1 || self == .H2 || self == .H3 }
    var isList: Bool { self == .BULLET || self == .NUMBERED || self == .CHECKLIST }
}

enum TextAlignKind: String, KotlinEnum {
    case LEFT, CENTER, RIGHT
    static var fallback: TextAlignKind { .LEFT }
}

/// Yazı elemanının içeriği ve yazı tipi ayarları.
struct TextSpec: Codable, Equatable {
    var content: String
    var font: TextFont = .SANS
    var bold = false
    var italic = false
    var underline = false
    var align: TextAlignKind = .LEFT
    var paragraph: TextParagraph = .NORMAL
    var outlineColorHex: String? = nil
    var outlineWidth: Float = 0
    var box: TextBox? = nil
    /// v42 yol üzerinde yazı.
    var path: TextPathSpec? = nil

    init(content: String, font: TextFont = .SANS, bold: Bool = false, italic: Bool = false, underline: Bool = false,
         align: TextAlignKind = .LEFT, paragraph: TextParagraph = .NORMAL, outlineColorHex: String? = nil,
         outlineWidth: Float = 0, box: TextBox? = nil, path: TextPathSpec? = nil) {
        self.content = content; self.font = font; self.bold = bold; self.italic = italic; self.underline = underline
        self.align = align; self.paragraph = paragraph; self.outlineColorHex = outlineColorHex
        self.outlineWidth = outlineWidth; self.box = box; self.path = path
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        content = try c.req("content")
        font = c.v("font", .SANS); bold = c.v("bold", false); italic = c.v("italic", false)
        underline = c.v("underline", false); align = c.v("align", .LEFT); paragraph = c.v("paragraph", .NORMAL)
        outlineColorHex = c.o("outlineColorHex"); outlineWidth = c.v("outlineWidth", 0)
        box = c.o("box"); path = c.o("path")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(content, "content")
        try c.e(font, "font", .SANS); try c.e(bold, "bold", false); try c.e(italic, "italic", false)
        try c.e(underline, "underline", false); try c.e(align, "align", .LEFT); try c.e(paragraph, "paragraph", .NORMAL)
        try c.o(outlineColorHex, "outlineColorHex"); try c.e(outlineWidth, "outlineWidth", 0)
        try c.o(box, "box"); try c.o(path, "path")
    }
}

/// v42 yol üzerinde yazı.
struct TextPathSpec: Codable, Equatable {
    var points: [StrokePoint]
    var w: Float
    var h: Float
    var start: Float = 0
    var below = false
    var reversed = false
    var closed = false

    init(points: [StrokePoint], w: Float, h: Float, start: Float = 0, below: Bool = false, reversed: Bool = false, closed: Bool = false) {
        self.points = points; self.w = w; self.h = h; self.start = start; self.below = below; self.reversed = reversed; self.closed = closed
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        points = try c.req("points"); w = try c.req("w"); h = try c.req("h")
        start = c.v("start", 0); below = c.v("below", false); reversed = c.v("reversed", false); closed = c.v("closed", false)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(points, "points"); try c.r(w, "w"); try c.r(h, "h")
        try c.e(start, "start", 0); try c.e(below, "below", false); try c.e(reversed, "reversed", false); try c.e(closed, "closed", false)
    }
}

enum BoxFit: String, KotlinEnum {
    case COVER, STRETCH, NINE_SLICE, TILE
    static var fallback: BoxFit { .COVER }
}

/// v23 yazı kutusu.
struct TextBox: Codable, Equatable {
    var width: Float? = nil
    var height: Float? = nil
    var fillHex: String? = nil
    var patternId: String? = nil
    var patternColorHex: String? = nil
    var patternScale: Float = 1
    var imageId: String? = nil
    var imageFit: BoxFit = .NINE_SLICE
    var corner: Float = 0
    var padding: Float? = nil
    var borderHex: String? = nil
    var fillGradient: GradientSpec? = nil
    var balloon: BalloonSpec? = nil
    var effects: TextEffects? = nil

    init(width: Float? = nil, height: Float? = nil, fillHex: String? = nil, patternId: String? = nil,
         patternColorHex: String? = nil, patternScale: Float = 1, imageId: String? = nil, imageFit: BoxFit = .NINE_SLICE,
         corner: Float = 0, padding: Float? = nil, borderHex: String? = nil, fillGradient: GradientSpec? = nil,
         balloon: BalloonSpec? = nil, effects: TextEffects? = nil) {
        self.width = width; self.height = height; self.fillHex = fillHex; self.patternId = patternId
        self.patternColorHex = patternColorHex; self.patternScale = patternScale; self.imageId = imageId
        self.imageFit = imageFit; self.corner = corner; self.padding = padding; self.borderHex = borderHex
        self.fillGradient = fillGradient; self.balloon = balloon; self.effects = effects
    }

    var hasBackground: Bool {
        fillHex != nil || patternId != nil || imageId != nil || borderHex != nil || fillGradient != nil || balloon != nil
    }

    func orNull() -> TextBox? {
        (width == nil && height == nil && !hasBackground && corner == 0 && padding == nil && effects == nil) ? nil : self
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        width = c.o("width"); height = c.o("height"); fillHex = c.o("fillHex"); patternId = c.o("patternId")
        patternColorHex = c.o("patternColorHex"); patternScale = c.v("patternScale", 1); imageId = c.o("imageId")
        imageFit = c.v("imageFit", .NINE_SLICE); corner = c.v("corner", 0); padding = c.o("padding")
        borderHex = c.o("borderHex"); fillGradient = c.o("fillGradient"); balloon = c.o("balloon"); effects = c.o("effects")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.o(width, "width"); try c.o(height, "height"); try c.o(fillHex, "fillHex"); try c.o(patternId, "patternId")
        try c.o(patternColorHex, "patternColorHex"); try c.e(patternScale, "patternScale", 1); try c.o(imageId, "imageId")
        try c.e(imageFit, "imageFit", .NINE_SLICE); try c.e(corner, "corner", 0); try c.o(padding, "padding")
        try c.o(borderHex, "borderHex"); try c.o(fillGradient, "fillGradient"); try c.o(balloon, "balloon"); try c.o(effects, "effects")
    }
}

/// v27 gradyan: 2–4 renk, doğrusal (`angle` derece) ya da `radial`.
struct GradientSpec: Codable, Equatable {
    var colors: [String]
    var angle: Float = 90
    var radial = false

    init(colors: [String], angle: Float = 90, radial: Bool = false) { self.colors = colors; self.angle = angle; self.radial = radial }

    var valid: Bool { colors.count >= 2 }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        colors = try c.req("colors"); angle = c.v("angle", 90); radial = c.v("radial", false)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(colors, "colors"); try c.e(angle, "angle", 90); try c.e(radial, "radial", false)
    }
}

/// v27 konuşma balonu biçimleri.
enum BalloonStyle: String, KotlinEnum {
    case SPEECH, ROUNDED, THOUGHT, SHOUT, WHISPER, NARRATION, ELECTRIC, WAVY
    case DOUBLE, ICICLE, DRIP, SCALLOP, FLASH, HEART, CUT, ROUGH
    case PHONE, FREE
    static var fallback: BalloonStyle { .SPEECH }
}

struct BalloonTail: Codable, Equatable {
    var x: Float
    var y: Float
    var curve: Float = 0.3

    init(x: Float, y: Float, curve: Float = 0.3) { self.x = x; self.y = y; self.curve = curve }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        x = try c.req("x"); y = try c.req("y"); curve = c.v("curve", 0.3)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(x, "x"); try c.r(y, "y"); try c.e(curve, "curve", 0.3)
    }
}

struct BalloonSpec: Codable, Equatable {
    static let defaultTails = [BalloonTail(x: 0.25, y: 1.6)]
    var style: BalloonStyle = .SPEECH
    var tails: [BalloonTail] = BalloonSpec.defaultTails
    var fillHex = "#FFFFFF"
    var strokeHex = "#111111"
    var strokeWidth: Float = 0.09

    init(style: BalloonStyle = .SPEECH, tails: [BalloonTail] = BalloonSpec.defaultTails, fillHex: String = "#FFFFFF",
         strokeHex: String = "#111111", strokeWidth: Float = 0.09) {
        self.style = style; self.tails = tails; self.fillHex = fillHex; self.strokeHex = strokeHex; self.strokeWidth = strokeWidth
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        style = c.v("style", .SPEECH); tails = c.v("tails", BalloonSpec.defaultTails); fillHex = c.v("fillHex", "#FFFFFF")
        strokeHex = c.v("strokeHex", "#111111"); strokeWidth = c.v("strokeWidth", 0.09)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        // `style` Kotlin'de EncodeDefault işaretsiz: kotlinx encodeDefaults=false iken varsayılanda yazılmaz.
        try c.e(style, "style", .SPEECH); try c.e(tails, "tails", BalloonSpec.defaultTails)
        try c.e(fillHex, "fillHex", "#FFFFFF"); try c.e(strokeHex, "strokeHex", "#111111"); try c.e(strokeWidth, "strokeWidth", 0.09)
    }
}

struct TextEffects: Codable, Equatable {
    var shadowHex: String? = nil
    var shadowDx: Float = 0.06
    var shadowDy: Float = 0.06
    var shadowBlur: Float = 0
    var gradient: GradientSpec? = nil
    var caps = false
    var letterSpacing: Float = 0

    init(shadowHex: String? = nil, shadowDx: Float = 0.06, shadowDy: Float = 0.06, shadowBlur: Float = 0,
         gradient: GradientSpec? = nil, caps: Bool = false, letterSpacing: Float = 0) {
        self.shadowHex = shadowHex; self.shadowDx = shadowDx; self.shadowDy = shadowDy; self.shadowBlur = shadowBlur
        self.gradient = gradient; self.caps = caps; self.letterSpacing = letterSpacing
    }

    var isEmpty: Bool { shadowHex == nil && gradient?.valid != true && !caps && letterSpacing == 0 }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        shadowHex = c.o("shadowHex"); shadowDx = c.v("shadowDx", 0.06); shadowDy = c.v("shadowDy", 0.06)
        shadowBlur = c.v("shadowBlur", 0); gradient = c.o("gradient"); caps = c.v("caps", false); letterSpacing = c.v("letterSpacing", 0)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.o(shadowHex, "shadowHex"); try c.e(shadowDx, "shadowDx", 0.06); try c.e(shadowDy, "shadowDy", 0.06)
        try c.e(shadowBlur, "shadowBlur", 0); try c.o(gradient, "gradient"); try c.e(caps, "caps", false)
        try c.e(letterSpacing, "letterSpacing", 0)
    }
}

/// v23 tuvalin arka planı (resim + desen + gradyan).
struct BackgroundSpec: Codable, Equatable {
    static let ASSET = "asset:"
    static let PAPER = "paper:"

    var patternId: String? = nil
    var patternColorHex: String? = nil
    var patternScale: Float = 1
    var patternOpacity: Float = 1
    var imageId: String? = nil
    var imageFit: BoxFit = .COVER
    var imageOpacity: Float = 1
    var gradient: GradientSpec? = nil
    var imageOffsetX: Float = 0
    var imageOffsetY: Float = 0
    var imageScale: Float = 1

    init(patternId: String? = nil, patternColorHex: String? = nil, patternScale: Float = 1, patternOpacity: Float = 1,
         imageId: String? = nil, imageFit: BoxFit = .COVER, imageOpacity: Float = 1, gradient: GradientSpec? = nil,
         imageOffsetX: Float = 0, imageOffsetY: Float = 0, imageScale: Float = 1) {
        self.patternId = patternId; self.patternColorHex = patternColorHex; self.patternScale = patternScale
        self.patternOpacity = patternOpacity; self.imageId = imageId; self.imageFit = imageFit; self.imageOpacity = imageOpacity
        self.gradient = gradient; self.imageOffsetX = imageOffsetX; self.imageOffsetY = imageOffsetY; self.imageScale = imageScale
    }

    var isEmpty: Bool { patternId == nil && imageId == nil && gradient == nil }
    var hasImageTransform: Bool { imageOffsetX != 0 || imageOffsetY != 0 || imageScale != 1 }

    func withImageTransformOf(_ from: BackgroundSpec) -> BackgroundSpec {
        var c = self
        c.imageOffsetX = from.imageOffsetX; c.imageOffsetY = from.imageOffsetY; c.imageScale = from.imageScale
        return c
    }

    var assetId: String? {
        guard let id = imageId, id.hasPrefix(Self.ASSET) else { return nil }
        return String(id.dropFirst(Self.ASSET.count))
    }

    var paperImageId: String? {
        guard let id = imageId, id.hasPrefix(Self.PAPER) else { return nil }
        return String(id.dropFirst(Self.PAPER.count))
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        patternId = c.o("patternId"); patternColorHex = c.o("patternColorHex"); patternScale = c.v("patternScale", 1)
        patternOpacity = c.v("patternOpacity", 1); imageId = c.o("imageId"); imageFit = c.v("imageFit", .COVER)
        imageOpacity = c.v("imageOpacity", 1); gradient = c.o("gradient"); imageOffsetX = c.v("imageOffsetX", 0)
        imageOffsetY = c.v("imageOffsetY", 0); imageScale = c.v("imageScale", 1)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.o(patternId, "patternId"); try c.o(patternColorHex, "patternColorHex"); try c.e(patternScale, "patternScale", 1)
        try c.e(patternOpacity, "patternOpacity", 1); try c.o(imageId, "imageId"); try c.e(imageFit, "imageFit", .COVER)
        try c.e(imageOpacity, "imageOpacity", 1); try c.o(gradient, "gradient"); try c.e(imageOffsetX, "imageOffsetX", 0)
        try c.e(imageOffsetY, "imageOffsetY", 0); try c.e(imageScale, "imageScale", 1)
    }
}

/// v24 bir sayfanın kendi arka planı; v45 `cover` çizgi roman kapağı.
struct PageBackground: Codable, Equatable {
    var paperHex: String? = nil
    var spec: BackgroundSpec? = nil
    var cover = false

    init(paperHex: String? = nil, spec: BackgroundSpec? = nil, cover: Bool = false) {
        self.paperHex = paperHex; self.spec = spec; self.cover = cover
    }

    var isEmpty: Bool { paperHex == nil && spec == nil && !cover }
    var hasLook: Bool { paperHex != nil || spec != nil }

    func withoutCover() -> PageBackground? {
        var c = self
        c.cover = false
        return c.isEmpty ? nil : c
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        paperHex = c.o("paperHex"); spec = c.o("spec"); cover = c.v("cover", false)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.o(paperHex, "paperHex"); try c.o(spec, "spec"); try c.e(cover, "cover", false)
    }
}

/// v23 tuvaldeki 3B şekil.
struct Model3DLink: Codable, Equatable {
    var modelId: String
    var dataAssetId: String
}

/// Tuval koordinatlarında bir nokta (v18 basınç/eğim, v20 yön).
struct StrokePoint: Codable, Equatable {
    var x: Float
    var y: Float
    var pressure: Float = 1
    var tilt: Float = 0
    var orient: Float = 0

    init(_ x: Float, _ y: Float, pressure: Float = 1, tilt: Float = 0, orient: Float = 0) {
        self.x = x; self.y = y; self.pressure = pressure; self.tilt = tilt; self.orient = orient
    }

    var hasDynamics: Bool { pressure != 1 || tilt != 0 || orient != 0 }

    func at(_ x: Float, _ y: Float) -> StrokePoint {
        var p = self
        p.x = x; p.y = y
        return p
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        x = try c.req("x"); y = try c.req("y")
        pressure = c.v("pressure", 1); tilt = c.v("tilt", 0); orient = c.v("orient", 0)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(x, "x"); try c.r(y, "y")
        try c.e(pressure, "pressure", 1); try c.e(tilt, "tilt", 0); try c.e(orient, "orient", 0)
    }
}

/// Tek bir çizim elemanı.
struct Stroke: Codable, Equatable, Identifiable {
    var id: String
    var points: [StrokePoint]
    var colorHex: String
    var brushType: BrushType
    var sizePx: Float
    var opacity: Float
    var order: Int
    var groupId: String? = nil
    var assetId: String? = nil
    var flow: Float = 1
    var strength: Float = 1
    var text: TextSpec? = nil
    var shape: ShapeSpec? = nil
    var diagram: DiagramSpec? = nil
    var clip: [StrokePoint]? = nil
    var layerId: String? = nil
    var formula: FormulaSpec? = nil
    var params: BrushParams? = nil
    /// v22 3B sahnedeki yer (JSON'a yazılmaz; içerik biçiminde ayrı).
    var space: Space3D? = nil
    /// v23 3B şekil (JSON'a yazılmaz).
    var model: Model3DLink? = nil
    var hidden = false
    var harmony: HarmonySpec? = nil

    init(id: String, points: [StrokePoint], colorHex: String, brushType: BrushType, sizePx: Float, opacity: Float,
         order: Int, groupId: String? = nil, assetId: String? = nil, flow: Float = 1, strength: Float = 1,
         text: TextSpec? = nil, shape: ShapeSpec? = nil, diagram: DiagramSpec? = nil, clip: [StrokePoint]? = nil,
         layerId: String? = nil, formula: FormulaSpec? = nil, params: BrushParams? = nil, space: Space3D? = nil,
         model: Model3DLink? = nil, hidden: Bool = false, harmony: HarmonySpec? = nil) {
        self.id = id; self.points = points; self.colorHex = colorHex; self.brushType = brushType; self.sizePx = sizePx
        self.opacity = opacity; self.order = order; self.groupId = groupId; self.assetId = assetId; self.flow = flow
        self.strength = strength; self.text = text; self.shape = shape; self.diagram = diagram; self.clip = clip
        self.layerId = layerId; self.formula = formula; self.params = params; self.space = space; self.model = model
        self.hidden = hidden; self.harmony = harmony
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        id = try c.req("id"); points = try c.req("points"); colorHex = try c.req("colorHex")
        brushType = try c.req("brushType"); sizePx = try c.req("sizePx"); opacity = try c.req("opacity"); order = try c.req("order")
        groupId = c.o("groupId"); assetId = c.o("assetId"); flow = c.v("flow", 1); strength = c.v("strength", 1)
        text = c.o("text"); shape = c.o("shape"); diagram = c.o("diagram"); clip = c.o("clip"); layerId = c.o("layerId")
        formula = c.o("formula"); params = c.o("params"); hidden = c.v("hidden", false); harmony = c.o("harmony")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(id, "id"); try c.r(points, "points"); try c.r(colorHex, "colorHex"); try c.r(brushType, "brushType")
        try c.r(sizePx, "sizePx"); try c.r(opacity, "opacity"); try c.r(order, "order")
        try c.o(groupId, "groupId"); try c.o(assetId, "assetId"); try c.e(flow, "flow", 1); try c.e(strength, "strength", 1)
        try c.o(text, "text"); try c.o(shape, "shape"); try c.o(diagram, "diagram"); try c.o(clip, "clip")
        try c.o(layerId, "layerId"); try c.o(formula, "formula"); try c.o(params, "params"); try c.e(hidden, "hidden", false)
        try c.o(harmony, "harmony")
    }
}

/// v43 nesnenin arka planla uyumu (yıkıcı olmayan).
struct HarmonySpec: Codable, Equatable {
    var intensity: Float = 1
    var color = true
    var gainR: Float = 1
    var gainG: Float = 1
    var gainB: Float = 1
    var lift: Float = 0
    var saturation: Float = 1
    var shadow = true
    var shadowColorHex = "#000000"
    var shadowOpacity: Float = 0.35
    var shadowDx: Float = 0
    var shadowDy: Float = 0
    var shadowBlur: Float = 0.03
    var castSkew: Float = 0
    var castScaleY: Float = 0.35
    var contact = true
    var edge = true
    var feather: Float = 0.006
    var wrap: Float = 0.3
    var wrapColorHex = "#FFFFFF"
    var lightAngle: Float = -135

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        let d = HarmonySpec()
        intensity = c.v("intensity", d.intensity); color = c.v("color", d.color); gainR = c.v("gainR", d.gainR)
        gainG = c.v("gainG", d.gainG); gainB = c.v("gainB", d.gainB); lift = c.v("lift", d.lift)
        saturation = c.v("saturation", d.saturation); shadow = c.v("shadow", d.shadow)
        shadowColorHex = c.v("shadowColorHex", d.shadowColorHex); shadowOpacity = c.v("shadowOpacity", d.shadowOpacity)
        shadowDx = c.v("shadowDx", d.shadowDx); shadowDy = c.v("shadowDy", d.shadowDy); shadowBlur = c.v("shadowBlur", d.shadowBlur)
        castSkew = c.v("castSkew", d.castSkew); castScaleY = c.v("castScaleY", d.castScaleY); contact = c.v("contact", d.contact)
        edge = c.v("edge", d.edge); feather = c.v("feather", d.feather); wrap = c.v("wrap", d.wrap)
        wrapColorHex = c.v("wrapColorHex", d.wrapColorHex); lightAngle = c.v("lightAngle", d.lightAngle)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        let d = HarmonySpec()
        try c.e(intensity, "intensity", d.intensity); try c.e(color, "color", d.color); try c.e(gainR, "gainR", d.gainR)
        try c.e(gainG, "gainG", d.gainG); try c.e(gainB, "gainB", d.gainB); try c.e(lift, "lift", d.lift)
        try c.e(saturation, "saturation", d.saturation); try c.e(shadow, "shadow", d.shadow)
        try c.e(shadowColorHex, "shadowColorHex", d.shadowColorHex); try c.e(shadowOpacity, "shadowOpacity", d.shadowOpacity)
        try c.e(shadowDx, "shadowDx", d.shadowDx); try c.e(shadowDy, "shadowDy", d.shadowDy); try c.e(shadowBlur, "shadowBlur", d.shadowBlur)
        try c.e(castSkew, "castSkew", d.castSkew); try c.e(castScaleY, "castScaleY", d.castScaleY); try c.e(contact, "contact", d.contact)
        try c.e(edge, "edge", d.edge); try c.e(feather, "feather", d.feather); try c.e(wrap, "wrap", d.wrap)
        try c.e(wrapColorHex, "wrapColorHex", d.wrapColorHex); try c.e(lightAngle, "lightAngle", d.lightAngle)
    }
}

/// v22 3B sahnede bir elemanın yeri.
struct Space3D: Equatable {
    var frame: [Float]
    var local: [Float]
    var stamp: Int64
    var view: Int32

    init(frame: [Float], local: [Float], stamp: Int64, view: Int32) {
        precondition(frame.count == 12)
        self.frame = frame; self.local = local; self.stamp = stamp; self.view = view
    }

    var isSolid: Bool { local.isEmpty }
    var pointCount: Int { local.count / 2 }

    func with(stamp: Int64, view: Int32) -> Space3D { Space3D(frame: frame, local: local, stamp: stamp, view: view) }

    static func == (a: Space3D, b: Space3D) -> Bool {
        a.stamp == b.stamp && a.view == b.view && a.frame.map(\.bitPattern) == b.frame.map(\.bitPattern)
            && a.local.map(\.bitPattern) == b.local.map(\.bitPattern)
    }
}

/// v20 fırça izinin görünüm ayarları.
struct BrushParams: Codable, Equatable {
    var spacing: Float? = nil
    var tiltWidth: Float? = nil
    var tiltLight: Float? = nil
    var tiltThreshold: Float = 0
    var nibFollow = false
    var tube: TubeParams? = nil
    var depth3d: Depth3D? = nil
    var comicLines: ComicLinesParams? = nil

    init(spacing: Float? = nil, tiltWidth: Float? = nil, tiltLight: Float? = nil, tiltThreshold: Float = 0,
         nibFollow: Bool = false, tube: TubeParams? = nil, depth3d: Depth3D? = nil, comicLines: ComicLinesParams? = nil) {
        self.spacing = spacing; self.tiltWidth = tiltWidth; self.tiltLight = tiltLight; self.tiltThreshold = tiltThreshold
        self.nibFollow = nibFollow; self.tube = tube; self.depth3d = depth3d; self.comicLines = comicLines
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        spacing = c.o("spacing"); tiltWidth = c.o("tiltWidth"); tiltLight = c.o("tiltLight")
        tiltThreshold = c.v("tiltThreshold", 0); nibFollow = c.v("nibFollow", false); tube = c.o("tube")
        depth3d = c.o("depth3d"); comicLines = c.o("comicLines")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.o(spacing, "spacing"); try c.o(tiltWidth, "tiltWidth"); try c.o(tiltLight, "tiltLight")
        try c.e(tiltThreshold, "tiltThreshold", 0); try c.e(nibFollow, "nibFollow", false); try c.o(tube, "tube")
        try c.o(depth3d, "depth3d"); try c.o(comicLines, "comicLines")
    }
}

/// v29 çizgi roman hız/odak çizgisi grubunun üretim bilgisi.
struct ComicLinesParams: Codable, Equatable {
    var kind: String
    var density: Float
    var seed: Int32
    var area: [Float]
    var bounds: [Float]
    var focusX: Float = 0.5
    var focusY: Float = 0.5
    var angle: Float = 0
    var length: Float = 1
    var palette = false

    init(kind: String, density: Float, seed: Int32, area: [Float], bounds: [Float], focusX: Float = 0.5,
         focusY: Float = 0.5, angle: Float = 0, length: Float = 1, palette: Bool = false) {
        self.kind = kind; self.density = density; self.seed = seed; self.area = area; self.bounds = bounds
        self.focusX = focusX; self.focusY = focusY; self.angle = angle; self.length = length; self.palette = palette
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        kind = try c.req("kind"); density = try c.req("density"); seed = try c.req("seed")
        area = try c.req("area"); bounds = try c.req("bounds")
        focusX = c.v("focusX", 0.5); focusY = c.v("focusY", 0.5); angle = c.v("angle", 0); length = c.v("length", 1)
        palette = c.v("palette", false)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(kind, "kind"); try c.r(density, "density"); try c.r(seed, "seed"); try c.r(area, "area"); try c.r(bounds, "bounds")
        try c.e(focusX, "focusX", 0.5); try c.e(focusY, "focusY", 0.5); try c.e(angle, "angle", 0); try c.e(length, "length", 1)
        try c.e(palette, "palette", false)
    }
}

enum Plane3D: String, KotlinEnum {
    case FREE, FRONT, SIDE, TOP
    static var fallback: Plane3D { .FREE }
}

struct Depth3D: Codable, Equatable {
    var plane: Plane3D = .FREE
    var depth: Float = 0.4
    var ex: Float? = nil
    var ey: Float? = nil

    init(plane: Plane3D = .FREE, depth: Float = 0.4, ex: Float? = nil, ey: Float? = nil) {
        self.plane = plane; self.depth = depth; self.ex = ex; self.ey = ey
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        plane = c.v("plane", .FREE); depth = c.v("depth", 0.4); ex = c.o("ex"); ey = c.o("ey")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.e(plane, "plane", .FREE); try c.e(depth, "depth", 0.4); try c.o(ex, "ex"); try c.o(ey, "ey")
    }
}

enum TubeMaterial: String, KotlinEnum {
    case MATTE, PLASTIC, METAL, GLASS
    static var fallback: TubeMaterial { .PLASTIC }
}

struct TubeParams: Codable, Equatable {
    var lightAngle: Float = 225
    var relief: Float = 0.75
    var gloss: Float = 0.6
    var material: TubeMaterial = .PLASTIC
    var shadow: Float = 0.3
    var shadowSoftness: Float = 0.5

    init(lightAngle: Float = 225, relief: Float = 0.75, gloss: Float = 0.6, material: TubeMaterial = .PLASTIC,
         shadow: Float = 0.3, shadowSoftness: Float = 0.5) {
        self.lightAngle = lightAngle; self.relief = relief; self.gloss = gloss; self.material = material
        self.shadow = shadow; self.shadowSoftness = shadowSoftness
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        lightAngle = c.v("lightAngle", 225); relief = c.v("relief", 0.75); gloss = c.v("gloss", 0.6)
        material = c.v("material", .PLASTIC); shadow = c.v("shadow", 0.3); shadowSoftness = c.v("shadowSoftness", 0.5)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.e(lightAngle, "lightAngle", 225); try c.e(relief, "relief", 0.75); try c.e(gloss, "gloss", 0.6)
        try c.e(material, "material", .PLASTIC); try c.e(shadow, "shadow", 0.3); try c.e(shadowSoftness, "shadowSoftness", 0.5)
    }
}

/// Tuvalin katmanı (aşağıdan yukarı).
struct Layer: Codable, Equatable, Identifiable {
    static let BASE = "base"
    static let Default = Layer(id: BASE, name: "Katman 1")

    var id: String
    var name: String
    var visible = true
    var locked = false

    init(id: String, name: String, visible: Bool = true, locked: Bool = false) {
        self.id = id; self.name = name; self.visible = visible; self.locked = locked
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        id = try c.req("id"); name = try c.req("name"); visible = c.v("visible", true); locked = c.v("locked", false)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(id, "id"); try c.r(name, "name"); try c.e(visible, "visible", true); try c.e(locked, "locked", false)
    }
}

/// Formül elemanı: LaTeX alt kümesi kaynak.
struct FormulaSpec: Codable, Equatable {
    var source: String
    var templateId = ""

    init(source: String, templateId: String = "") { self.source = source; self.templateId = templateId }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        source = try c.req("source"); templateId = c.v("templateId", "")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(source, "source"); try c.e(templateId, "templateId", "")
    }
}

/// Katman işlemleri.
enum Layers {
    static func of(_ stroke: Stroke, _ layers: [Layer]) -> String {
        if let id = stroke.layerId, layers.contains(where: { $0.id == id }) { return id }
        return layers.first?.id ?? Layer.BASE
    }

    static func indexOf(_ stroke: Stroke, _ layers: [Layer]) -> Int {
        if layers.count <= 1 { return 0 }
        guard let id = stroke.layerId else { return 0 }
        return layers.firstIndex { $0.id == id } ?? 0
    }

    /// Katman sırasına göre dizer (katman içinde sıra korunur) ve `order` alanlarını yeniler.
    static func sort(_ strokes: [Stroke], _ layers: [Layer]) -> [Stroke] {
        if layers.count <= 1 { return strokes }
        var sorted = true
        var last = -1
        for s in strokes {
            let i = indexOf(s, layers)
            if i < last { sorted = false; break }
            last = i
        }
        if sorted { return strokes }
        let ordered = strokes.enumerated().sorted { a, b in
            let ia = indexOf(a.element, layers), ib = indexOf(b.element, layers)
            return ia != ib ? ia < ib : a.offset < b.offset
        }
        return ordered.enumerated().map { i, v in
            if v.element.order == i { return v.element }
            var s = v.element
            s.order = i
            return s
        }
    }

    static func visible(_ strokes: [Stroke], _ layers: [Layer]) -> [Stroke] {
        let allLayers = layers.allSatisfy { $0.visible }
        if allLayers && !strokes.contains(where: { $0.hidden }) { return strokes }
        let hiddenLayers: Set<String> = allLayers ? [] : Set(layers.filter { !$0.visible }.map(\.id))
        return strokes.filter { !$0.hidden && (allLayers || !hiddenLayers.contains(of($0, layers))) }
    }

    static func editable(_ stroke: Stroke, _ layers: [Layer]) -> Bool {
        if stroke.hidden { return false }
        if layers.count <= 1 && layers.allSatisfy({ $0.visible && !$0.locked }) { return true }
        let id = of(stroke, layers)
        guard let layer = layers.first(where: { $0.id == id }) else { return true }
        return layer.visible && !layer.locked
    }

    static func lockedIds(_ strokes: [Stroke], _ layers: [Layer]) -> Set<String> {
        if layers.allSatisfy({ $0.visible && !$0.locked }) && !strokes.contains(where: { $0.hidden }) { return [] }
        return Set(strokes.filter { !editable($0, layers) }.map(\.id))
    }

    static func normalize(_ layers: [Layer]) -> [Layer] { layers.isEmpty ? [Layer.Default] : layers }
}

/// Vektör şeklin bir alt yolu.
struct SubPath: Codable, Equatable {
    var count: Int
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
    var nodes: [Int]? = nil

    init(count: Int, closed: Bool = false, dashed: Bool = false, label: String? = nil, labelSize: Float = 0,
         strokeColorHex: String? = nil, fillColorHex: String? = nil, widthScale: Float? = nil, dotted: Bool = false,
         alpha: Float? = nil, tag: String? = nil, nodes: [Int]? = nil) {
        self.count = count; self.closed = closed; self.dashed = dashed; self.label = label; self.labelSize = labelSize
        self.strokeColorHex = strokeColorHex; self.fillColorHex = fillColorHex; self.widthScale = widthScale
        self.dotted = dotted; self.alpha = alpha; self.tag = tag; self.nodes = nodes
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        count = try c.req("count"); closed = c.v("closed", false); dashed = c.v("dashed", false); label = c.o("label")
        labelSize = c.v("labelSize", 0); strokeColorHex = c.o("strokeColorHex"); fillColorHex = c.o("fillColorHex")
        widthScale = c.o("widthScale"); dotted = c.v("dotted", false); alpha = c.o("alpha"); tag = c.o("tag"); nodes = c.o("nodes")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(count, "count"); try c.e(closed, "closed", false); try c.e(dashed, "dashed", false); try c.o(label, "label")
        try c.e(labelSize, "labelSize", 0); try c.o(strokeColorHex, "strokeColorHex"); try c.o(fillColorHex, "fillColorHex")
        try c.o(widthScale, "widthScale"); try c.e(dotted, "dotted", false); try c.o(alpha, "alpha"); try c.o(tag, "tag")
        try c.o(nodes, "nodes")
    }
}

/// Şeklin görünümü.
struct ShapeStyle: Codable, Equatable {
    var strokeColorHex = "#1E1E1E"
    var strokeWidth: Float = 4
    var fillColorHex: String? = nil
    var fillOpacity: Float = 1
    var cornerRadius: Float = 0
    var fillGradient: GradientSpec? = nil

    init(strokeColorHex: String = "#1E1E1E", strokeWidth: Float = 4, fillColorHex: String? = nil, fillOpacity: Float = 1,
         cornerRadius: Float = 0, fillGradient: GradientSpec? = nil) {
        self.strokeColorHex = strokeColorHex; self.strokeWidth = strokeWidth; self.fillColorHex = fillColorHex
        self.fillOpacity = fillOpacity; self.cornerRadius = cornerRadius; self.fillGradient = fillGradient
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        strokeColorHex = c.v("strokeColorHex", "#1E1E1E"); strokeWidth = c.v("strokeWidth", 4); fillColorHex = c.o("fillColorHex")
        fillOpacity = c.v("fillOpacity", 1); cornerRadius = c.v("cornerRadius", 0); fillGradient = c.o("fillGradient")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.e(strokeColorHex, "strokeColorHex", "#1E1E1E"); try c.e(strokeWidth, "strokeWidth", 4)
        try c.o(fillColorHex, "fillColorHex"); try c.e(fillOpacity, "fillOpacity", 1); try c.e(cornerRadius, "cornerRadius", 0)
        try c.o(fillGradient, "fillGradient")
    }
}

/// Grafik bilgisi.
struct GraphSpec: Codable, Equatable {
    var xLabel = "x"
    var yLabel = "y"
    var zLabel = "z"
    var formula = ""
    var xMin: Float = -5
    var xMax: Float = 5
    var kind = ""
    var preset = ""
    var `repeat`: Int = 2
    var ticks = 5
    var showNumbers = true
    var grid = true
    var tickLabels: [String: String] = [:]
    var values: [Float] = []
    var valueLabels = IntKeyedStrings()
    var curveColor = "#1E88E5"
    var fillUnder = false
    var keyPoints = true
    var rangeSet = false
    var freq = 1
    var paper = ""
    var angleDeg = false

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        let d = GraphSpec()
        xLabel = c.v("xLabel", d.xLabel); yLabel = c.v("yLabel", d.yLabel); zLabel = c.v("zLabel", d.zLabel)
        formula = c.v("formula", d.formula); xMin = c.v("xMin", d.xMin); xMax = c.v("xMax", d.xMax); kind = c.v("kind", d.kind)
        preset = c.v("preset", d.preset); `repeat` = c.v("repeat", d.repeat); ticks = c.v("ticks", d.ticks)
        showNumbers = c.v("showNumbers", d.showNumbers); grid = c.v("grid", d.grid); tickLabels = c.v("tickLabels", d.tickLabels)
        values = c.v("values", d.values); valueLabels = c.v("valueLabels", d.valueLabels); curveColor = c.v("curveColor", d.curveColor)
        fillUnder = c.v("fillUnder", d.fillUnder); keyPoints = c.v("keyPoints", d.keyPoints); rangeSet = c.v("rangeSet", d.rangeSet)
        freq = c.v("freq", d.freq); paper = c.v("paper", d.paper); angleDeg = c.v("angleDeg", d.angleDeg)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        let d = GraphSpec()
        try c.e(xLabel, "xLabel", d.xLabel); try c.e(yLabel, "yLabel", d.yLabel); try c.e(zLabel, "zLabel", d.zLabel)
        try c.e(formula, "formula", d.formula); try c.e(xMin, "xMin", d.xMin); try c.e(xMax, "xMax", d.xMax)
        try c.e(kind, "kind", d.kind); try c.e(preset, "preset", d.preset); try c.e(`repeat`, "repeat", d.repeat)
        try c.e(ticks, "ticks", d.ticks); try c.e(showNumbers, "showNumbers", d.showNumbers); try c.e(grid, "grid", d.grid)
        try c.e(tickLabels, "tickLabels", d.tickLabels); try c.e(values, "values", d.values)
        try c.e(valueLabels, "valueLabels", d.valueLabels); try c.e(curveColor, "curveColor", d.curveColor)
        try c.e(fillUnder, "fillUnder", d.fillUnder); try c.e(keyPoints, "keyPoints", d.keyPoints)
        try c.e(rangeSet, "rangeSet", d.rangeSet); try c.e(freq, "freq", d.freq); try c.e(paper, "paper", d.paper)
        try c.e(angleDeg, "angleDeg", d.angleDeg)
    }
}

/// Vektör şekil.
struct ShapeSpec: Codable, Equatable {
    var kind: String
    var subpaths: [SubPath]
    var style = ShapeStyle()
    var graph: GraphSpec? = nil
    var panel: PanelRef? = nil
    var params: [String: String]? = nil

    init(kind: String, subpaths: [SubPath], style: ShapeStyle = ShapeStyle(), graph: GraphSpec? = nil,
         panel: PanelRef? = nil, params: [String: String]? = nil) {
        self.kind = kind; self.subpaths = subpaths; self.style = style; self.graph = graph; self.panel = panel; self.params = params
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        kind = try c.req("kind"); subpaths = try c.req("subpaths"); style = c.v("style", ShapeStyle())
        graph = c.o("graph"); panel = c.o("panel"); params = c.o("params")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(kind, "kind"); try c.r(subpaths, "subpaths"); try c.e(style, "style", ShapeStyle())
        try c.o(graph, "graph"); try c.o(panel, "panel"); try c.o(params, "params")
    }
}

/// v36 çizgi roman şablonu paneli.
struct PanelRef: Codable, Equatable {
    var set: String
    var leaf: Int
    var layout: String
}

enum DiagramKind: String, KotlinEnum {
    case FLOW, PROCESS, ARCHITECTURE, ORG, TIMELINE, STATE, RELATION, COMPARISON
    case CYCLE, MIND_MAP, PYRAMID, VENN, MATRIX, FUNNEL, FISHBONE, CONCEPT_MAP, TREE, KWL, STEPS, HUB
    case SEQUENCE, ER, UML_CLASS, DB_SCHEMA, SWIMLANE, KANBAN, STORY_MAP, DEPLOYMENT, NESTED, LAYERS, LOOP, QUADRANT
    case WARDLEY, JOURNEY, DEPENDENCY, IT_STATE, HIGH_LEVEL, MEDALLION, DATA_FLOW, DP_INTEGRATION, ARCH_DELTA
    case BAR, WATERFALL, LINE, SCATTER, RADAR, POLAR, TREEMAP, HEATMAP, DP_SECURITY, SANKEY, GANTT
    case EXPLODED, AXONOMETRIC
    static var fallback: DiagramKind { .FLOW }
}

enum DiagramBox: String, KotlinEnum {
    case ROUNDED, RECT, DIAMOND, CIRCLE, CYLINDER, PILL, NOTE
    case HEXAGON, PARALLELOGRAM, DOCUMENT, CLOUD, TRAPEZOID, ELLIPSE, TRIANGLE, CHEVRON
    case TABLE, GROUP, LANE, COLUMN, CARD, DOT, LABEL, BAR, CELL, SLAB
    static var fallback: DiagramBox { .ROUNDED }
}

enum DiagramSide: String, KotlinEnum {
    case AUTO, TOP, RIGHT, BOTTOM, LEFT
    static var fallback: DiagramSide { .AUTO }
}

struct DiagramNode: Codable, Equatable, Identifiable {
    var id: String
    var text: String
    var x: Float
    var y: Float
    var w: Float
    var h: Float
    var box: DiagramBox = .ROUNDED
    var fillColorHex: String? = nil
    var strokeColorHex: String? = nil
    var textColorHex: String? = nil
    var role: String? = nil

    init(id: String, text: String, x: Float, y: Float, w: Float, h: Float, box: DiagramBox = .ROUNDED,
         fillColorHex: String? = nil, strokeColorHex: String? = nil, textColorHex: String? = nil, role: String? = nil) {
        self.id = id; self.text = text; self.x = x; self.y = y; self.w = w; self.h = h; self.box = box
        self.fillColorHex = fillColorHex; self.strokeColorHex = strokeColorHex; self.textColorHex = textColorHex; self.role = role
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        id = try c.req("id"); text = try c.req("text"); x = try c.req("x"); y = try c.req("y"); w = try c.req("w"); h = try c.req("h")
        box = c.v("box", .ROUNDED); fillColorHex = c.o("fillColorHex"); strokeColorHex = c.o("strokeColorHex")
        textColorHex = c.o("textColorHex"); role = c.o("role")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(id, "id"); try c.r(text, "text"); try c.r(x, "x"); try c.r(y, "y"); try c.r(w, "w"); try c.r(h, "h")
        try c.e(box, "box", .ROUNDED); try c.o(fillColorHex, "fillColorHex"); try c.o(strokeColorHex, "strokeColorHex")
        try c.o(textColorHex, "textColorHex"); try c.o(role, "role")
    }
}

struct DiagramEdge: Codable, Equatable {
    var from: String
    var to: String
    var label = ""
    var dashed = false
    var arrow = true
    var fromSide: DiagramSide = .AUTO
    var toSide: DiagramSide = .AUTO
    var colorHex: String? = nil
    var role: String? = nil
    var fromLabel: String? = nil
    var toLabel: String? = nil

    init(from: String, to: String, label: String = "", dashed: Bool = false, arrow: Bool = true,
         fromSide: DiagramSide = .AUTO, toSide: DiagramSide = .AUTO, colorHex: String? = nil, role: String? = nil,
         fromLabel: String? = nil, toLabel: String? = nil) {
        self.from = from; self.to = to; self.label = label; self.dashed = dashed; self.arrow = arrow
        self.fromSide = fromSide; self.toSide = toSide; self.colorHex = colorHex; self.role = role
        self.fromLabel = fromLabel; self.toLabel = toLabel
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        from = try c.req("from"); to = try c.req("to"); label = c.v("label", ""); dashed = c.v("dashed", false)
        arrow = c.v("arrow", true); fromSide = c.v("fromSide", .AUTO); toSide = c.v("toSide", .AUTO)
        colorHex = c.o("colorHex"); role = c.o("role"); fromLabel = c.o("fromLabel"); toLabel = c.o("toLabel")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(from, "from"); try c.r(to, "to"); try c.e(label, "label", ""); try c.e(dashed, "dashed", false)
        try c.e(arrow, "arrow", true); try c.e(fromSide, "fromSide", .AUTO); try c.e(toSide, "toSide", .AUTO)
        try c.o(colorHex, "colorHex"); try c.o(role, "role"); try c.o(fromLabel, "fromLabel"); try c.o(toLabel, "toLabel")
    }
}

struct DiagramFrame: Codable, Equatable {
    var left: Float = 0
    var top: Float = 0
    var right: Float = 1
    var bottom: Float = 1

    init(left: Float = 0, top: Float = 0, right: Float = 1, bottom: Float = 1) {
        self.left = left; self.top = top; self.right = right; self.bottom = bottom
    }

    var width: Float { right - left }
    var height: Float { bottom - top }
    var isDefault: Bool { left == 0 && top == 0 && right == 1 && bottom == 1 }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        left = c.v("left", 0); top = c.v("top", 0); right = c.v("right", 1); bottom = c.v("bottom", 1)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.e(left, "left", 0); try c.e(top, "top", 0); try c.e(right, "right", 1); try c.e(bottom, "bottom", 1)
    }
}

struct DiagramSpec: Codable, Equatable {
    var kind: DiagramKind
    var nodes: [DiagramNode]
    var edges: [DiagramEdge] = []
    var style = ShapeStyle()
    var fontSize: Float = 0.045
    var frame = DiagramFrame()
    var manual = false
    var theme = ""

    init(kind: DiagramKind, nodes: [DiagramNode], edges: [DiagramEdge] = [], style: ShapeStyle = ShapeStyle(),
         fontSize: Float = 0.045, frame: DiagramFrame = DiagramFrame(), manual: Bool = false, theme: String = "") {
        self.kind = kind; self.nodes = nodes; self.edges = edges; self.style = style; self.fontSize = fontSize
        self.frame = frame; self.manual = manual; self.theme = theme
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        kind = try c.req("kind"); nodes = try c.req("nodes"); edges = c.v("edges", []); style = c.v("style", ShapeStyle())
        fontSize = c.v("fontSize", 0.045); frame = c.v("frame", DiagramFrame()); manual = c.v("manual", false); theme = c.v("theme", "")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(kind, "kind"); try c.r(nodes, "nodes"); try c.e(edges, "edges", []); try c.e(style, "style", ShapeStyle())
        try c.e(fontSize, "fontSize", 0.045); try c.e(frame, "frame", DiagramFrame()); try c.e(manual, "manual", false)
        try c.e(theme, "theme", "")
    }
}

enum GuideKind: String, KotlinEnum {
    case NONE, GRID, RULED, DOTS, PERSPECTIVE_1, PERSPECTIVE_2, PERSPECTIVE_3, ISOMETRIC
    static var fallback: GuideKind { .NONE }
}

/// Kılavuz ve perspektif ayarları.
struct GuideSpec: Codable, Equatable {
    static let DEFAULT_YAW: Float = 45
    static let DEFAULT_PITCH: Float = 35.26439
    static let GridKinds: [GuideKind] = [.GRID, .RULED, .DOTS, .ISOMETRIC]

    var kind: GuideKind = .NONE
    var spacing: Float = 80
    var colorHex = "#7C89A6"
    var opacity: Float = 0.35
    var horizon: Float = 0.5
    var vanishA: Float = 0.2
    var vanishB: Float = 0.8
    var vanishC: Float = 0.9
    var rays = 24
    var includeInExport = false
    var perspective = 0
    var perspectiveOpacity: Float = 0.35
    var draw3d = false
    var plane3d: Plane3D = .FREE
    var depth3d: Float = 0.4
    var yaw3d: Float = GuideSpec.DEFAULT_YAW
    var pitch3d: Float = GuideSpec.DEFAULT_PITCH
    var persp3d: Float = 0

    init() {}

    func normalized() -> GuideSpec {
        var c = self
        switch kind {
        case .PERSPECTIVE_1: c.kind = .NONE; c.perspective = 1; c.perspectiveOpacity = opacity
        case .PERSPECTIVE_2: c.kind = .NONE; c.perspective = 2; c.perspectiveOpacity = opacity
        case .PERSPECTIVE_3: c.kind = .NONE; c.perspective = 3; c.perspectiveOpacity = opacity
        default: break
        }
        return c
    }

    var isEmpty: Bool { kind == .NONE && perspective == 0 && !draw3d }

    var draw3dSpec: Depth3D? { draw3d ? Depth3D(plane: plane3d, depth: min(max(depth3d, 0), 1)) : nil }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        let d = GuideSpec()
        kind = c.v("kind", d.kind); spacing = c.v("spacing", d.spacing); colorHex = c.v("colorHex", d.colorHex)
        opacity = c.v("opacity", d.opacity); horizon = c.v("horizon", d.horizon); vanishA = c.v("vanishA", d.vanishA)
        vanishB = c.v("vanishB", d.vanishB); vanishC = c.v("vanishC", d.vanishC); rays = c.v("rays", d.rays)
        includeInExport = c.v("includeInExport", d.includeInExport); perspective = c.v("perspective", d.perspective)
        perspectiveOpacity = c.v("perspectiveOpacity", d.perspectiveOpacity); draw3d = c.v("draw3d", d.draw3d)
        plane3d = c.v("plane3d", d.plane3d); depth3d = c.v("depth3d", d.depth3d); yaw3d = c.v("yaw3d", d.yaw3d)
        pitch3d = c.v("pitch3d", d.pitch3d); persp3d = c.v("persp3d", d.persp3d)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        let d = GuideSpec()
        try c.e(kind, "kind", d.kind); try c.e(spacing, "spacing", d.spacing); try c.e(colorHex, "colorHex", d.colorHex)
        try c.e(opacity, "opacity", d.opacity); try c.e(horizon, "horizon", d.horizon); try c.e(vanishA, "vanishA", d.vanishA)
        try c.e(vanishB, "vanishB", d.vanishB); try c.e(vanishC, "vanishC", d.vanishC); try c.e(rays, "rays", d.rays)
        try c.e(includeInExport, "includeInExport", d.includeInExport); try c.e(perspective, "perspective", d.perspective)
        try c.e(perspectiveOpacity, "perspectiveOpacity", d.perspectiveOpacity); try c.e(draw3d, "draw3d", d.draw3d)
        try c.e(plane3d, "plane3d", d.plane3d); try c.e(depth3d, "depth3d", d.depth3d); try c.e(yaw3d, "yaw3d", d.yaw3d)
        try c.e(pitch3d, "pitch3d", d.pitch3d); try c.e(persp3d, "persp3d", d.persp3d)
    }
}
