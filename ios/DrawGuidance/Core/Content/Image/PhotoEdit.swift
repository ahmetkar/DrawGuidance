import Foundation

/*
 * v52 Foto düzenleme modu (Android `PhotoEdit.kt` ile aynı sıra ve hesap): bütün raster düzenlemeleri tek, yıkıcı
 * olmayan bir durumda ([PhotoEdit]) toplanır ve özgün piksellerden her seferinde baştan uygulanır:
 *  1. rötuş fırçaları (kaynak koordinatında), 2. arka plan kaldırma, 3. geometri (90° döndürme, çevirme, eğim, kırpma),
 *  4. ışık, 5. çizgi roman filtresi, hazır görünüm ve genel ayarlar (ImageEffects).
 */

enum PhotoBrush: String, CaseIterable { case PAINT, RESTORE, BLUR, LIGHTEN, DARKEN, SMOOTH }

/// Fırça izi: [points] kaynak resimde 0..1 x,y çiftleri; [size] kısa kenarın oranı.
struct PhotoStroke: Equatable {
    var brush: PhotoBrush
    var points: [Float]
    var size: Float
    var strength: Float = 0.6
    var color: UInt32 = 0xFFE53935
}

struct PhotoCrop: Equatable {
    var left: Float = 0, top: Float = 0, right: Float = 1, bottom: Float = 1
    var isFull: Bool { left <= 0.0005 && top <= 0.0005 && right >= 0.9995 && bottom >= 0.9995 }
}

struct PhotoLight: Equatable {
    var exposure: Float = 0, highlights: Float = 0, shadows: Float = 0, whites: Float = 0, blacks: Float = 0
    var clarity: Float = 0, vibrance: Float = 0
    var isIdentity: Bool { self == PhotoLight() }
}

/// v53 HSL: 8 renk aralığı için ton (±30°), doygunluk ve açıklık (−1..1) (Android `PhotoHsl`).
struct PhotoHsl: Equatable {
    var hue = [Float](repeating: 0, count: 8)
    var saturation = [Float](repeating: 0, count: 8)
    var lightness = [Float](repeating: 0, count: 8)
    var isIdentity: Bool { hue.allSatisfy { $0 == 0 } && saturation.allSatisfy { $0 == 0 } && lightness.allSatisfy { $0 == 0 } }

    static let CENTERS: [Float] = [0, 30, 60, 120, 180, 240, 275, 315]
    static let SWATCHES: [UInt32] = [0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA, 0xFFD81B60]
}

/// v53 renk dengesi: gölgeler/orta tonlar/vurgular × (camgöbeği–kırmızı, macenta–yeşil, sarı–mavi).
struct PhotoBalance: Equatable {
    var shadows: [Float] = [0, 0, 0]
    var midtones: [Float] = [0, 0, 0]
    var highlights: [Float] = [0, 0, 0]
    var preserve = true
    var isIdentity: Bool { (shadows + midtones + highlights).allSatisfy { $0 == 0 } }

    func range(_ i: Int) -> [Float] { i == 0 ? shadows : i == 1 ? midtones : highlights }
    mutating func set(_ i: Int, _ channel: Int, _ v: Float) {
        switch i {
        case 0: shadows[channel] = v
        case 1: midtones[channel] = v
        default: highlights[channel] = v
        }
    }
}

enum ClipFill: String, CaseIterable { case COLOR, GRADIENT }
enum ClipBlend: String, CaseIterable { case NORMAL, MULTIPLY, SCREEN, OVERLAY, SOFT_LIGHT, COLOR }

/// v53 kırpma maskesi: dolgu yalnız resmin görünen piksellerine karışır.
struct PhotoClip: Equatable {
    var fill: ClipFill = .COLOR
    var color1: UInt32 = 0xFFE53935
    var color2: UInt32 = 0xFF1E88E5
    var angle: Float = 90
    var blend: ClipBlend = .OVERLAY
    var opacity: Float = 0.7
}

struct PhotoEdit: Equatable {
    var rotation: Int = 0
    var flipH = false
    var flipV = false
    /// Eğim düzeltme (derece, −45..45).
    var straighten: Float = 0
    var crop = PhotoCrop()
    var light = PhotoLight()
    var adjust = ImageAdjust()
    var look: ImageLook = .NONE
    var comic: ComicFilter? = nil
    var background: BackgroundOptions? = nil
    var strokes: [PhotoStroke] = []
    /// v53 renk aralıklarına göre ton, doygunluk, açıklık.
    var hsl = PhotoHsl()
    /// v53 renk dengesi.
    var balance = PhotoBalance()
    /// v53 kırpma maskesi.
    var clip: PhotoClip? = nil

    var isIdentity: Bool { self == PhotoEdit() }
    var quarter: Int { ((rotation % 4) + 4) % 4 }
}

enum PhotoOps {

    /// [edit] uygulanmış resim ve boyutu. [skipCrop]: kırpma ekranının önizlemesi (çerçeve üstte çizilir).
    static func render(_ src: [UInt32], _ w: Int, _ h: Int, _ edit: PhotoEdit, skipCrop: Bool = false,
                       cancelled: () -> Bool = { false }) -> (px: [UInt32], w: Int, h: Int) {
        var px = src
        if !edit.strokes.isEmpty { px = applyStrokes(px, src, w, h, edit.strokes) }
        if cancelled() { return (px, w, h) }
        if let opt = edit.background {
            let mask = BackgroundRemover.mask(px, w, h, opt, cancelled: cancelled)
            px = BackgroundRemover.apply(px, w, h, mask)
        }
        if cancelled() { return (px, w, h) }
        var cw = w, ch = h
        if edit.quarter != 0 || edit.flipH || edit.flipV {
            let r = orient(px, cw, ch, edit.quarter, edit.flipH, edit.flipV); px = r.px; cw = r.w; ch = r.h
        }
        if edit.straighten != 0 { px = straighten(px, cw, ch, edit.straighten) }
        if !skipCrop && !edit.crop.isFull {
            let r = crop(px, cw, ch, edit.crop); px = r.px; cw = r.w; ch = r.h
        }
        if cancelled() { return (px, cw, ch) }
        if !edit.light.isIdentity { px = light(px, cw, ch, edit.light) }
        if !edit.hsl.isIdentity { px = hsl(px, edit.hsl) }
        if !edit.balance.isIdentity { px = balance(px, edit.balance) }
        if cancelled() { return (px, cw, ch) }
        if let c = edit.comic { px = ComicFilters.apply(c, px, cw, ch, cancelled: cancelled) }
        if edit.look != .NONE || !edit.adjust.isIdentity { px = ImageEffects.apply(px, cw, ch, look: edit.look, adjust: edit.adjust, cancelled: cancelled) }
        if let c = edit.clip { px = clip(px, cw, ch, c) }
        return (px, cw, ch)
    }

    // MARK: v53 HSL, renk dengesi, kırpma maskesi (Android ile aynı hesap)

    private static func rgbToHsl(_ r: Float, _ g: Float, _ b: Float) -> (Float, Float, Float) {
        let mx = max(r, g, b), mn = min(r, g, b)
        let l = (mx + mn) / 2
        if mx == mn { return (0, 0, l) }
        let d = mx - mn
        let s = l > 0.5 ? d / (2 - mx - mn) : d / (mx + mn)
        var h: Float
        if mx == r { h = (g - b) / d + (g < b ? 6 : 0) } else if mx == g { h = (b - r) / d + 2 } else { h = (r - g) / d + 4 }
        h *= 60
        return (h, s, l)
    }

    private static func hue2rgb(_ p: Float, _ q: Float, _ t0: Float) -> Float {
        var t = t0
        if t < 0 { t += 1 }
        if t > 1 { t -= 1 }
        if t < 1.0 / 6 { return p + (q - p) * 6 * t }
        if t < 0.5 { return q }
        if t < 2.0 / 3 { return p + (q - p) * (2.0 / 3 - t) * 6 }
        return p
    }

    private static func hslToRgb(_ h0: Float, _ s: Float, _ l: Float) -> (Float, Float, Float) {
        if s <= 0 { return (l, l, l) }
        let h = ((h0.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)) / 360
        let q = l < 0.5 ? l * (1 + s) : l + s - l * s
        let p = 2 * l - q
        return (hue2rgb(p, q, h + 1.0 / 3), hue2rgb(p, q, h), hue2rgb(p, q, h - 1.0 / 3))
    }

    static func rangeWeight(_ h: Float, _ range: Int) -> Float {
        let c = PhotoHsl.CENTERS
        let center = c[range], prev = c[(range + 7) % 8], next = c[(range + 1) % 8]
        var d = h - center
        if d > 180 { d -= 360 }
        if d < -180 { d += 360 }
        let span = d >= 0 ? (next - center + 360).truncatingRemainder(dividingBy: 360) : (center - prev + 360).truncatingRemainder(dividingBy: 360)
        return (1 - abs(d) / max(span, 1)).coerceIn(0, 1)
    }

    @inline(__always) private static func pack(_ a: UInt32, _ r: Float, _ g: Float, _ b: Float) -> UInt32 {
        let ri = UInt32(Int((r * 255).rounded()).coerceIn(0, 255)), gi = UInt32(Int((g * 255).rounded()).coerceIn(0, 255))
        let bi = UInt32(Int((b * 255).rounded()).coerceIn(0, 255))
        return a << 24 | ri << 16 | gi << 8 | bi
    }

    static func hsl(_ src: [UInt32], _ adj: PhotoHsl) -> [UInt32] {
        var out = src
        for i in 0..<src.count {
            let c = src[i]
            let a = c >> 24
            if a == 0 { continue }
            let (h0, s0, l0) = rgbToHsl(Float(c >> 16 & 0xFF) / 255, Float(c >> 8 & 0xFF) / 255, Float(c & 0xFF) / 255)
            let gray = (s0 * 2).coerceIn(0, 1)
            var dh: Float = 0, ds: Float = 0, dl: Float = 0
            for r in 0..<8 {
                let w = rangeWeight(h0, r) * gray
                if w == 0 { continue }
                dh += w * adj.hue[r]; ds += w * adj.saturation[r]; dl += w * adj.lightness[r]
            }
            if dh == 0 && ds == 0 && dl == 0 { continue }
            let h = h0 + dh * 30
            let s = (ds >= 0 ? s0 + (1 - s0) * ds : s0 * (1 + ds)).coerceIn(0, 1)
            let l = (dl >= 0 ? l0 + (1 - l0) * dl * 0.6 : l0 * (1 + dl * 0.6)).coerceIn(0, 1)
            let (r, g, b) = hslToRgb(h, s, l)
            out[i] = pack(a, r, g, b)
        }
        return out
    }

    static func balance(_ src: [UInt32], _ b: PhotoBalance) -> [UInt32] {
        var out = src
        let k: Float = 0.32
        for i in 0..<src.count {
            let c = src[i]
            let a = c >> 24
            if a == 0 { continue }
            let r0 = Float(c >> 16 & 0xFF) / 255, g0 = Float(c >> 8 & 0xFF) / 255, b0 = Float(c & 0xFF) / 255
            let lum = 0.299 * r0 + 0.587 * g0 + 0.114 * b0
            let ws = ((0.5 - lum) * 2).coerceIn(0, 1), wh = ((lum - 0.5) * 2).coerceIn(0, 1), wm = (1 - abs(lum - 0.5) * 2).coerceIn(0, 1)
            var r = r0 + (b.shadows[0] * ws + b.midtones[0] * wm + b.highlights[0] * wh) * k
            var g = g0 + (b.shadows[1] * ws + b.midtones[1] * wm + b.highlights[1] * wh) * k
            var bl = b0 + (b.shadows[2] * ws + b.midtones[2] * wm + b.highlights[2] * wh) * k
            if b.preserve {
                let d = lum - (0.299 * r + 0.587 * g + 0.114 * bl)
                r += d; g += d; bl += d
            }
            out[i] = pack(a, r, g, bl)
        }
        return out
    }

    private static func blendCh(_ mode: ClipBlend, _ a: Float, _ b: Float) -> Float {
        switch mode {
        case .NORMAL, .COLOR: return b
        case .MULTIPLY: return a * b
        case .SCREEN: return 1 - (1 - a) * (1 - b)
        case .OVERLAY: return a < 0.5 ? 2 * a * b : 1 - 2 * (1 - a) * (1 - b)
        case .SOFT_LIGHT: return (1 - 2 * b) * a * a + 2 * b * a
        }
    }

    static func clip(_ src: [UInt32], _ w: Int, _ h: Int, _ clip: PhotoClip) -> [UInt32] {
        var out = src
        let rad = clip.angle * Float.pi / 180
        let dx = cos(rad), dy = sin(rad)
        let corners: [Float] = [0, Float(w) * dx, Float(h) * dy, Float(w) * dx + Float(h) * dy]
        let p0 = corners.min()!, p1 = corners.max()!
        let span = max(p1 - p0, 1)
        let c1 = clip.color1, c2 = clip.color2
        let op = clip.opacity.coerceIn(0, 1)
        for y in 0..<h {
            for x in 0..<w {
                let i = y * w + x
                let c = src[i]
                let a = c >> 24
                if a == 0 { continue }
                let t: Float = clip.fill == .GRADIENT ? ((Float(x) * dx + Float(y) * dy - p0) / span).coerceIn(0, 1) : 0
                let fr = (Float(c1 >> 16 & 0xFF) * (1 - t) + Float(c2 >> 16 & 0xFF) * t) / 255
                let fg = (Float(c1 >> 8 & 0xFF) * (1 - t) + Float(c2 >> 8 & 0xFF) * t) / 255
                let fb = (Float(c1 & 0xFF) * (1 - t) + Float(c2 & 0xFF) * t) / 255
                let r0 = Float(c >> 16 & 0xFF) / 255, g0 = Float(c >> 8 & 0xFF) / 255, b0 = Float(c & 0xFF) / 255
                var r = blendCh(clip.blend, r0, fr), g = blendCh(clip.blend, g0, fg), b = blendCh(clip.blend, b0, fb)
                if clip.blend == .COLOR {
                    let d = (0.299 * r0 + 0.587 * g0 + 0.114 * b0) - (0.299 * r + 0.587 * g + 0.114 * b)
                    r += d; g += d; b += d
                }
                r = r0 + (r - r0) * op; g = g0 + (g - g0) * op; b = b0 + (b - b0) * op
                out[i] = pack(a, r, g, b)
            }
        }
        return out
    }

    // MARK: geometri

    static func orientedSize(_ w: Int, _ h: Int, _ quarter: Int) -> (Int, Int) { quarter % 2 == 1 ? (h, w) : (w, h) }

    static func orient(_ src: [UInt32], _ w: Int, _ h: Int, _ quarter: Int, _ flipH: Bool, _ flipV: Bool) -> (px: [UInt32], w: Int, h: Int) {
        let (ow, oh) = orientedSize(w, h, quarter)
        var out = [UInt32](repeating: 0, count: ow * oh)
        for y in 0..<oh {
            for x in 0..<ow {
                let ax = flipH ? ow - 1 - x : x
                let ay = flipV ? oh - 1 - y : y
                let sx: Int, sy: Int
                switch quarter {
                case 1: sx = ay; sy = h - 1 - ax
                case 2: sx = w - 1 - ax; sy = h - 1 - ay
                case 3: sx = w - 1 - ay; sy = ax
                default: sx = ax; sy = ay
                }
                out[y * ow + x] = src[sy * w + sx]
            }
        }
        return (out, ow, oh)
    }

    static func coverScale(_ w: Int, _ h: Int, _ degrees: Float) -> Float {
        let a = Double(abs(degrees)) * .pi / 180
        let ratio = Double(max(w, h)) / Double(max(1, min(w, h)))
        return Float(cos(a) + sin(a) * ratio)
    }

    static func straighten(_ src: [UInt32], _ w: Int, _ h: Int, _ degrees: Float) -> [UInt32] {
        let a = Double(degrees) * .pi / 180
        let ca = Float(cos(a)), sa = Float(sin(a))
        let s = coverScale(w, h, degrees)
        let cx = Float(w) / 2, cy = Float(h) / 2
        var out = [UInt32](repeating: 0, count: w * h)
        for y in 0..<h {
            for x in 0..<w {
                let dx = (Float(x) + 0.5 - cx) / s, dy = (Float(y) + 0.5 - cy) / s
                let sx = cx + dx * ca + dy * sa - 0.5
                let sy = cy - dx * sa + dy * ca - 0.5
                out[y * w + x] = bilinear(src, w, h, sx, sy)
            }
        }
        return out
    }

    static func crop(_ src: [UInt32], _ w: Int, _ h: Int, _ c: PhotoCrop) -> (px: [UInt32], w: Int, h: Int) {
        func cl(_ v: Float) -> Float { min(1, max(0, v)) }
        let l = min(w - 1, max(0, Int((cl(c.left) * Float(w)).rounded())))
        let t = min(h - 1, max(0, Int((cl(c.top) * Float(h)).rounded())))
        let r = min(w, max(l + 1, Int((cl(c.right) * Float(w)).rounded())))
        let b = min(h, max(t + 1, Int((cl(c.bottom) * Float(h)).rounded())))
        let cw = r - l, ch = b - t
        var out = [UInt32](repeating: 0, count: cw * ch)
        for y in 0..<ch {
            let so = (t + y) * w + l
            for x in 0..<cw { out[y * cw + x] = src[so + x] }
        }
        return (out, cw, ch)
    }

    /// Son resmin 0..1 noktasının kaynak resimdeki 0..1 karşılığı ([w]×[h] kaynak boyutu).
    static func toSource(_ u: Float, _ v: Float, _ w: Int, _ h: Int, _ e: PhotoEdit, skipCrop: Bool = false) -> (Float, Float) {
        let (ow, oh) = orientedSize(w, h, e.quarter)
        let bx = skipCrop ? u : e.crop.left + u * (e.crop.right - e.crop.left)
        let by = skipCrop ? v : e.crop.top + v * (e.crop.bottom - e.crop.top)
        var px = bx * Float(ow), py = by * Float(oh)
        if e.straighten != 0 {
            let a = Double(e.straighten) * .pi / 180
            let ca = Float(cos(a)), sa = Float(sin(a))
            let s = coverScale(ow, oh, e.straighten)
            let cx = Float(ow) / 2, cy = Float(oh) / 2
            let dx = (px - cx) / s, dy = (py - cy) / s
            px = cx + dx * ca + dy * sa; py = cy - dx * sa + dy * ca
        }
        if e.flipH { px = Float(ow) - px }
        if e.flipV { py = Float(oh) - py }
        let sx: Float, sy: Float
        switch e.quarter {
        case 1: sx = py; sy = Float(h) - px
        case 2: sx = Float(w) - px; sy = Float(h) - py
        case 3: sx = Float(w) - py; sy = px
        default: sx = px; sy = py
        }
        return (sx / Float(w), sy / Float(h))
    }

    private static func bilinear(_ src: [UInt32], _ w: Int, _ h: Int, _ x: Float, _ y: Float) -> UInt32 {
        let xf = min(Float(w - 1), max(0, x)), yf = min(Float(h - 1), max(0, y))
        let x0 = Int(xf.rounded(.down)), y0 = Int(yf.rounded(.down))
        let x1 = min(w - 1, x0 + 1), y1 = min(h - 1, y0 + 1)
        let fx = xf - Float(x0), fy = yf - Float(y0)
        let c00 = src[y0 * w + x0], c10 = src[y0 * w + x1], c01 = src[y1 * w + x0], c11 = src[y1 * w + x1]
        var out: UInt32 = 0
        for sh: UInt32 in [24, 16, 8, 0] {
            let a = Float(c00 >> sh & 0xFF) * (1 - fx) + Float(c10 >> sh & 0xFF) * fx
            let b = Float(c01 >> sh & 0xFF) * (1 - fx) + Float(c11 >> sh & 0xFF) * fx
            let v = Int(a * (1 - fy) + b * fy + 0.5)
            out |= UInt32(min(255, max(0, v))) << sh
        }
        return out
    }

    // MARK: ışık

    static func light(_ px: [UInt32], _ w: Int, _ h: Int, _ l: PhotoLight) -> [UInt32] {
        var out = px
        let ev = Float(pow(2.0, Double(l.exposure) * 1.5))
        let base: [UInt32]? = l.clarity != 0 ? ImageEffects.blur(px, w, h, max(2, min(w, h) / 40)) : nil
        for i in 0..<out.count {
            let c = out[i]
            let a = c >> 24
            if a == 0 { continue }
            var r = Float(c >> 16 & 0xFF) / 255, g = Float(c >> 8 & 0xFF) / 255, b = Float(c & 0xFF) / 255
            if let base {
                let bc = base[i]
                let k = l.clarity * 0.8
                r += (r - Float(bc >> 16 & 0xFF) / 255) * k
                g += (g - Float(bc >> 8 & 0xFF) / 255) * k
                b += (b - Float(bc & 0xFF) / 255) * k
            }
            r *= ev; g *= ev; b *= ev
            let lum = min(1, max(0, 0.2126 * r + 0.7152 * g + 0.0722 * b))
            let sw = (1 - lum) * (1 - lum)
            let hw = lum * lum
            let delta = l.shadows * 0.45 * sw + l.highlights * 0.45 * hw +
                l.whites * 0.25 * smooth(0.6, 1, lum) + l.blacks * 0.25 * (1 - smooth(0, 0.4, lum))
            r += delta; g += delta; b += delta
            if l.vibrance != 0 {
                let mx = max(r, max(g, b)), mn = min(r, min(g, b))
                let k = l.vibrance * (1 - (mx - mn)) * 1.2
                let avg = (r + g + b) / 3
                r += (r - avg) * k; g += (g - avg) * k; b += (b - avg) * k
            }
            out[i] = (a << 24) | (to8(r) << 16) | (to8(g) << 8) | to8(b)
        }
        return out
    }

    private static func smooth(_ e0: Float, _ e1: Float, _ x: Float) -> Float { let t = min(1, max(0, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t) }
    private static func to8(_ v: Float) -> UInt32 { UInt32(min(255, max(0, Int(v * 255 + 0.5)))) }

    // MARK: rötuş fırçaları

    static func applyStrokes(_ px: [UInt32], _ orig: [UInt32], _ w: Int, _ h: Int, _ strokes: [PhotoStroke]) -> [UInt32] {
        var out = px
        let side = Float(min(w, h))
        for s in strokes {
            let radius = max(1, s.size * side / 2)
            var mask = [Float](repeating: 0, count: w * h)
            var minX = w, minY = h, maxX = -1, maxY = -1
            func stamp(_ cx: Float, _ cy: Float) {
                let x0 = max(0, Int(cx - radius)), x1 = min(w - 1, Int(cx + radius) + 1)
                let y0 = max(0, Int(cy - radius)), y1 = min(h - 1, Int(cy + radius) + 1)
                if x0 > x1 || y0 > y1 { return }
                minX = min(minX, x0); minY = min(minY, y0); maxX = max(maxX, x1); maxY = max(maxY, y1)
                for y in y0...y1 {
                    for x in x0...x1 {
                        let ddx = Float(x) + 0.5 - cx, ddy = Float(y) + 0.5 - cy
                        let d = (ddx * ddx + ddy * ddy).squareRoot() / radius
                        if d >= 1 { continue }
                        let v: Float = d < 0.6 ? 1 : 1 - (d - 0.6) / 0.4
                        let i = y * w + x
                        if v > mask[i] { mask[i] = v }
                    }
                }
            }
            let n = s.points.count / 2
            for k in 0..<n {
                let x = s.points[2 * k] * Float(w), y = s.points[2 * k + 1] * Float(h)
                if k == 0 { stamp(x, y); continue }
                let px0 = s.points[2 * k - 2] * Float(w), py0 = s.points[2 * k - 1] * Float(h)
                let dist = ((x - px0) * (x - px0) + (y - py0) * (y - py0)).squareRoot()
                let steps = max(1, Int(dist / max(1, radius * 0.3)))
                for j in 1...steps { stamp(px0 + (x - px0) * Float(j) / Float(steps), py0 + (y - py0) * Float(j) / Float(steps)) }
            }
            if maxX < 0 { continue }
            let box = (minX, minY, maxX, maxY)
            switch s.brush {
            case .BLUR, .SMOOTH:
                let r = max(1, Int(radius * (s.brush == .BLUR ? 0.5 : 0.25)))
                let blurred = ImageEffects.blur(out, w, h, r)
                out = blend(out, w, box, mask, s.strength) { i, _ in blurred[i] }
            case .RESTORE:
                out = blend(out, w, box, mask, s.strength) { i, _ in orig[i] }
            case .PAINT:
                out = blend(out, w, box, mask, s.strength) { _, c in (c & 0xFF000000) | (s.color & 0xFFFFFF) | (c >> 24 == 0 ? 0xFF000000 : 0) }
            case .LIGHTEN:
                out = blend(out, w, box, mask, s.strength) { _, c in scale(c, 1.45, 18) }
            case .DARKEN:
                out = blend(out, w, box, mask, s.strength) { _, c in scale(c, 0.6, 0) }
            }
        }
        return out
    }

    private static func scale(_ c: UInt32, _ k: Float, _ add: Float) -> UInt32 {
        func ch(_ v: UInt32) -> UInt32 { UInt32(min(255, max(0, Int((Float(v) * k + add).rounded())))) }
        return (c & 0xFF000000) | (ch(c >> 16 & 0xFF) << 16) | (ch(c >> 8 & 0xFF) << 8) | ch(c & 0xFF)
    }

    private static func blend(_ px: [UInt32], _ w: Int, _ box: (Int, Int, Int, Int), _ mask: [Float], _ strength: Float,
                              _ target: (Int, UInt32) -> UInt32) -> [UInt32] {
        var out = px
        let st = min(1, max(0, strength))
        for y in box.1...box.3 {
            for x in box.0...box.2 {
                let i = y * w + x
                let m = mask[i] * st
                if m <= 0 { continue }
                let c = px[i]
                let t = target(i, c)
                var o: UInt32 = 0
                for sh: UInt32 in [24, 16, 8, 0] {
                    let a = Float(c >> sh & 0xFF), b = Float(t >> sh & 0xFF)
                    o |= UInt32(min(255, max(0, Int(a + (b - a) * m + 0.5)))) << sh
                }
                out[i] = o
            }
        }
        return out
    }

    /// Kırpmayı yeni orana (en/boy) göre ortalar; nil: kırpma yok.
    static func cropForAspect(_ w: Int, _ h: Int, _ e: PhotoEdit, _ aspect: Float?) -> PhotoCrop {
        guard let aspect else { return PhotoCrop() }
        let (ow, oh) = orientedSize(w, h, e.quarter)
        let imgAspect = Float(ow) / Float(oh)
        if aspect > imgAspect {
            let hh = imgAspect / aspect
            return PhotoCrop(left: 0, top: (1 - hh) / 2, right: 1, bottom: (1 + hh) / 2)
        } else {
            let ww = aspect / imgAspect
            return PhotoCrop(left: (1 - ww) / 2, top: 0, right: (1 + ww) / 2, bottom: 1)
        }
    }
}
