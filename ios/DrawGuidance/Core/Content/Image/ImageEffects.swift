import Foundation

/// v30 resmin genel ayarları (Android `ImageAdjust`). Renk ayarları −1..1 (0: değişmez), diğerleri 0..1 (0: yok).
struct ImageAdjust: Equatable, Codable {
    var brightness: Float = 0
    var contrast: Float = 0
    var saturation: Float = 0
    /// Sıcaklık: + sarı/turuncu, − mavi.
    var warmth: Float = 0
    /// Ton: + macenta, − yeşil.
    var tint: Float = 0
    var blur: Float = 0
    var sharpen: Float = 0
    var vignette: Float = 0
    var grain: Float = 0
    /// Solukluk: siyahlar griye açılır (film görünümü).
    var fade: Float = 0

    var isIdentity: Bool {
        brightness == 0 && contrast == 0 && saturation == 0 && warmth == 0 && tint == 0 &&
            blur == 0 && sharpen == 0 && vignette == 0 && grain == 0 && fade == 0
    }
}

/// v30 hazır görünümler (efektler).
enum ImageLook: String, KotlinEnum {
    case NONE, GRAYSCALE, SEPIA, INVERT, VINTAGE, COOL, WARM, NOIR, VIVID, DUOTONE,
         POSTERIZE, PIXELATE, SKETCH, EMBOSS, OIL, GLOW
    static var fallback: ImageLook { .NONE }
}

/// v30 resim efektleri (Android `ImageEffects` ile birebir; ARGB piksel dizileri 0xAARRGGBB, saydamlık korunur).
/// Önce hazır görünüm, sonra renk ayarları (tek geçişli renk matrisi), sonra bulanıklık/keskinlik, en son vinyet,
/// gren ve solukluk uygulanır. Arka planda çalıştırılır.
enum ImageEffects {

    static func apply(_ src: [UInt32], _ w: Int, _ h: Int, look: ImageLook = .NONE, adjust: ImageAdjust = ImageAdjust(),
                      cancelled: () -> Bool = { false }) -> [UInt32] {
        precondition(src.count >= w * h && w > 0 && h > 0)
        var px = applyLook(src, w, h, look, cancelled)
        if cancelled() { return src }
        if let matrix = adjustMatrix(adjust) { px = colorMatrix(px, matrix) }
        if adjust.blur > 0 {
            let r = max(1, roundK(adjust.blur * Float(min(w, h)) * 0.025))
            px = blur(px, w, h, r)
        }
        if adjust.sharpen > 0 { px = sharpen(px, w, h, adjust.sharpen) }
        if adjust.vignette > 0 || adjust.grain > 0 || adjust.fade > 0 { px = finish(px, w, h, adjust) }
        return px
    }

    /// Kotlin `Float.roundToInt()` (Math.round: floor(x + 0.5)).
    @inline(__always) static func roundK(_ v: Float) -> Int { Int((v + 0.5).rounded(.down)) }

    // MARK: Renk matrisi

    /// 3×4 matris (r, g, b satırları; son sütun 0..255 öteleme). Değişiklik yoksa nil.
    static func adjustMatrix(_ a: ImageAdjust) -> [Float]? {
        if a.brightness == 0 && a.contrast == 0 && a.saturation == 0 && a.warmth == 0 && a.tint == 0 { return nil }
        var m = identity()
        if a.saturation != 0 { m = mul(saturationMatrix(1 + min(max(a.saturation, -1), 1)), m) }
        if a.contrast != 0 {
            // −1: düz gri, +1: 3 kat karşıtlık
            let k: Float = a.contrast >= 0 ? 1 + a.contrast * 2 : 1 + a.contrast
            let t: Float = 128 * (1 - k)
            m = mul([k, 0, 0, t, 0, k, 0, t, 0, 0, k, t], m)
        }
        if a.brightness != 0 {
            let b = a.brightness * 90
            m = mul([1, 0, 0, b, 0, 1, 0, b, 0, 0, 1, b], m)
        }
        if a.warmth != 0 || a.tint != 0 {
            let wv = a.warmth * 28, tv = a.tint * 22
            m = mul([1, 0, 0, wv + tv * 0.5, 0, 1, 0, -tv + wv * 0.15, 0, 0, 1, -wv + tv * 0.5], m)
        }
        return m
    }

    private static func identity() -> [Float] { [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0] }

    /// a·b (3×4 afin matrisler).
    private static func mul(_ a: [Float], _ b: [Float]) -> [Float] {
        var o = [Float](repeating: 0, count: 12)
        for r in 0..<3 {
            for c in 0..<3 { o[r * 4 + c] = a[r * 4] * b[c] + a[r * 4 + 1] * b[4 + c] + a[r * 4 + 2] * b[8 + c] }
            o[r * 4 + 3] = a[r * 4] * b[3] + a[r * 4 + 1] * b[7] + a[r * 4 + 2] * b[11] + a[r * 4 + 3]
        }
        return o
    }

    private static func saturationMatrix(_ s: Float) -> [Float] {
        let lr = 0.2126 * (1 - s), lg = 0.7152 * (1 - s), lb = 0.0722 * (1 - s)
        return [lr + s, lg, lb, 0, lr, lg + s, lb, 0, lr, lg, lb + s, 0]
    }

    private static func colorMatrix(_ src: [UInt32], _ m: [Float]) -> [UInt32] {
        var px = src
        for i in px.indices {
            let c = px[i]
            let a = c >> 24
            if a == 0 { continue }
            let r = Float(c >> 16 & 0xFF), g = Float(c >> 8 & 0xFF), b = Float(c & 0xFF)
            let nr = clamp(m[0] * r + m[1] * g + m[2] * b + m[3])
            let ng = clamp(m[4] * r + m[5] * g + m[6] * b + m[7])
            let nb = clamp(m[8] * r + m[9] * g + m[10] * b + m[11])
            px[i] = a << 24 | nr << 16 | ng << 8 | nb
        }
        return px
    }

    @inline(__always) private static func clamp(_ v: Float) -> UInt32 { v <= 0 ? 0 : v >= 255 ? 255 : UInt32(v + 0.5) }

    // MARK: Hazır görünümler

    private static func applyLook(_ src: [UInt32], _ w: Int, _ h: Int, _ look: ImageLook, _ cancelled: () -> Bool) -> [UInt32] {
        switch look {
        case .NONE: return src
        case .GRAYSCALE: return colorMatrix(src, saturationMatrix(0))
        case .SEPIA: return colorMatrix(src, [0.393, 0.769, 0.189, 0, 0.349, 0.686, 0.168, 0, 0.272, 0.534, 0.131, 0])
        case .INVERT: return colorMatrix(src, [-1, 0, 0, 255, 0, -1, 0, 255, 0, 0, -1, 255])
        case .VINTAGE:
            return finish(colorMatrix(src, mul([0.9, 0.1, 0.05, 18, 0.07, 0.85, 0.08, 10, 0.05, 0.1, 0.7, 6], saturationMatrix(0.7))),
                          w, h, ImageAdjust(vignette: 0.45, fade: 0.25))
        case .COOL: return colorMatrix(src, [0.92, 0, 0, -6, 0, 1, 0, 4, 0, 0, 1.08, 18])
        case .WARM: return colorMatrix(src, [1.08, 0, 0, 16, 0, 1.02, 0, 6, 0, 0, 0.9, -10])
        case .NOIR: return colorMatrix(src, mul([1.6, 0, 0, -60, 0, 1.6, 0, -60, 0, 0, 1.6, -60], saturationMatrix(0)))
        case .VIVID: return colorMatrix(src, mul([1.12, 0, 0, -8, 0, 1.12, 0, -8, 0, 0, 1.12, -8], saturationMatrix(1.55)))
        case .DUOTONE: return duotone(src, 0x1B2A6B, 0xFFB86B)
        case .POSTERIZE: return posterize(src, 4)
        case .PIXELATE: return pixelate(src, w, h, max(2, min(w, h) / 56))
        case .SKETCH: return sketch(src, w, h)
        case .EMBOSS: return emboss(src, w, h)
        case .OIL: return kuwahara(src, w, h, w * h > 1_500_000 ? 2 : 3, cancelled)
        case .GLOW: return glow(src, w, h)
        }
    }

    @inline(__always) private static func lum(_ c: UInt32) -> Float {
        0.2126 * Float(c >> 16 & 0xFF) + 0.7152 * Float(c >> 8 & 0xFF) + 0.0722 * Float(c & 0xFF)
    }

    private static func duotone(_ src: [UInt32], _ dark: Int, _ light: Int) -> [UInt32] {
        let dr = dark >> 16 & 0xFF, dg = dark >> 8 & 0xFF, db = dark & 0xFF
        let lr = light >> 16 & 0xFF, lg = light >> 8 & 0xFF, lb = light & 0xFF
        return src.map { c in
            let t = lum(c) / 255
            return (c & 0xFF00_0000) | clamp(Float(dr) + Float(lr - dr) * t) << 16 | clamp(Float(dg) + Float(lg - dg) * t) << 8 |
                clamp(Float(db) + Float(lb - db) * t)
        }
    }

    private static func posterize(_ src: [UInt32], _ levels: Int) -> [UInt32] {
        let step = 255 / Float(levels - 1)
        let lut: [UInt32] = (0..<256).map { UInt32(min(max(roundK(Float(roundK(Float($0) / step)) * step), 0), 255)) }
        return src.map { c in (c & 0xFF00_0000) | lut[Int(c >> 16 & 0xFF)] << 16 | lut[Int(c >> 8 & 0xFF)] << 8 | lut[Int(c & 0xFF)] }
    }

    private static func pixelate(_ src: [UInt32], _ w: Int, _ h: Int, _ cell: Int) -> [UInt32] {
        var out = [UInt32](repeating: 0, count: src.count)
        var y0 = 0
        while y0 < h {
            let y1 = min(h, y0 + cell)
            var x0 = 0
            while x0 < w {
                let x1 = min(w, x0 + cell)
                var a = 0, r = 0, g = 0, b = 0, n = 0
                for y in y0..<y1 {
                    for x in x0..<x1 {
                        let c = src[y * w + x], ca = Int(c >> 24)
                        a += ca; r += Int(c >> 16 & 0xFF) * ca; g += Int(c >> 8 & 0xFF) * ca; b += Int(c & 0xFF) * ca; n += 1
                    }
                }
                let avg: UInt32 = a == 0 ? 0 : UInt32(a / n) << 24 | UInt32(r / a) << 16 | UInt32(g / a) << 8 | UInt32(b / a)
                for y in y0..<y1 { for x in x0..<x1 { out[y * w + x] = avg } }
                x0 = x1
            }
            y0 = y1
        }
        return out
    }

    /// Kurşun kalem çizimi: gri → ters → bulanık → renk soldurma (color dodge).
    private static func sketch(_ src: [UInt32], _ w: Int, _ h: Int) -> [UInt32] {
        let n = w * h
        let gray: [UInt32] = (0..<n).map { i in
            let l = UInt32(min(max(roundK(lum(src[i])), 0), 255))
            return (src[i] & 0xFF00_0000) | l << 16 | l << 8 | l
        }
        let inv: [UInt32] = gray.map { ($0 & 0xFF00_0000) | (0xFFFFFF - ($0 & 0xFFFFFF)) }
        let blurred = blur(inv, w, h, max(2, min(w, h) / 90))
        return (0..<n).map { i in
            let base = Int(gray[i] & 0xFF), top = Int(blurred[i] & 0xFF)
            let v = UInt32(top >= 255 ? 255 : min(255, base * 255 / (255 - top)))
            return (src[i] & 0xFF00_0000) | v << 16 | v << 8 | v
        }
    }

    private static func emboss(_ src: [UInt32], _ w: Int, _ h: Int) -> [UInt32] {
        let l = src.prefix(w * h).map(lum)
        return (0..<(w * h)).map { i in
            let x = i % w, y = i / w
            func L(_ dx: Int, _ dy: Int) -> Float { l[min(max(y + dy, 0), h - 1) * w + min(max(x + dx, 0), w - 1)] }
            let e1: Float = -2 * L(-1, -1) - L(0, -1) - L(-1, 0)
            let e: Float = e1 + L(1, 0) + L(0, 1) + 2 * L(1, 1)
            let v = clamp(128 + 1.5 * e / 2)
            return (src[i] & 0xFF00_0000) | v << 16 | v << 8 | v
        }
    }

    /// Yumuşak parıltı: bulanık kopya "screen" karışımıyla eklenir.
    private static func glow(_ src: [UInt32], _ w: Int, _ h: Int) -> [UInt32] {
        let b = blur(src, w, h, max(2, min(w, h) / 40))
        func screen(_ x: UInt32, _ y: UInt32) -> UInt32 { UInt32(255 - (255 - Int(x)) * (255 - Int(Float(y) * 0.6)) / 255) }
        return src.indices.map { i in
            let c = src[i], d = b[i]
            return (c & 0xFF00_0000) | screen(c >> 16 & 0xFF, d >> 16 & 0xFF) << 16 | screen(c >> 8 & 0xFF, d >> 8 & 0xFF) << 8 | screen(c & 0xFF, d & 0xFF)
        }
    }

    /// Yağlı boya (Kuwahara): her piksel dört çeyrekten parlaklık varyansı en küçük olanın ortalama rengini alır.
    private static func kuwahara(_ src: [UInt32], _ w: Int, _ h: Int, _ r: Int, _ cancelled: () -> Bool) -> [UInt32] {
        var out = [UInt32](repeating: 0, count: w * h)
        let l = src.prefix(w * h).map(lum)
        let qx = [-r, 0, -r, 0], qy = [-r, -r, 0, 0]
        for y in 0..<h {
            if y % 32 == 0 && cancelled() { return src }
            for x in 0..<w {
                var best = Float.greatestFiniteMagnitude
                var br: Float = 0, bg: Float = 0, bb: Float = 0
                for q in 0..<4 {
                    var sum: Float = 0, sq: Float = 0, rr: Float = 0, gg: Float = 0, bbq: Float = 0
                    var n = 0
                    for dy in 0...r {
                        let yy = min(max(y + qy[q] + dy, 0), h - 1)
                        for dx in 0...r {
                            let i = yy * w + min(max(x + qx[q] + dx, 0), w - 1)
                            let v = l[i]; sum += v; sq += v * v
                            let c = src[i]
                            rr += Float(c >> 16 & 0xFF); gg += Float(c >> 8 & 0xFF); bbq += Float(c & 0xFF); n += 1
                        }
                    }
                    let fn = Float(n)
                    let mean = sum / fn
                    let variance = sq / fn - mean * mean
                    if variance < best { best = variance; br = rr / fn; bg = gg / fn; bb = bbq / fn }
                }
                out[y * w + x] = (src[y * w + x] & 0xFF00_0000) | clamp(br) << 16 | clamp(bg) << 8 | clamp(bb)
            }
        }
        return out
    }

    // MARK: Bulanıklık ve keskinlik

    /// Saydamlığa duyarlı (ön-çarpımlı) bulanıklık: üç geçişli ayrılabilir kutu bulanıklığı, yarıçaptan bağımsız O(n).
    static func blur(_ src: [UInt32], _ w: Int, _ h: Int, _ radius: Int) -> [UInt32] {
        if radius <= 0 { return src }
        let n = w * h
        var a = [Int](repeating: 0, count: n), r = a, g = a, b = a
        for i in 0..<n {
            let c = src[i], ca = Int(c >> 24)
            a[i] = ca; r[i] = Int(c >> 16 & 0xFF) * ca / 255; g[i] = Int(c >> 8 & 0xFF) * ca / 255; b[i] = Int(c & 0xFF) * ca / 255
        }
        var tmp = [Int](repeating: 0, count: n)
        let k = max(1, roundK(Float(radius) / 1.7))
        for _ in 0..<3 { boxH(a, &tmp, w, h, k); boxV(tmp, &a, w, h, k) }
        for _ in 0..<3 { boxH(r, &tmp, w, h, k); boxV(tmp, &r, w, h, k) }
        for _ in 0..<3 { boxH(g, &tmp, w, h, k); boxV(tmp, &g, w, h, k) }
        for _ in 0..<3 { boxH(b, &tmp, w, h, k); boxV(tmp, &b, w, h, k) }
        return (0..<n).map { i in
            let ca = a[i]
            if ca == 0 { return 0 }
            return UInt32(ca) << 24 | UInt32(min(255, r[i] * 255 / ca)) << 16 | UInt32(min(255, g[i] * 255 / ca)) << 8 | UInt32(min(255, b[i] * 255 / ca))
        }
    }

    private static func boxH(_ src: [Int], _ dst: inout [Int], _ w: Int, _ h: Int, _ r: Int) {
        let div = 2 * r + 1
        src.withUnsafeBufferPointer { s in
            dst.withUnsafeMutableBufferPointer { d in
                for y in 0..<h {
                    let row = y * w
                    var sum = 0
                    for i in -r...r { sum += s[row + min(max(i, 0), w - 1)] }
                    for x in 0..<w {
                        d[row + x] = sum / div
                        sum += s[row + min(w - 1, x + r + 1)] - s[row + max(0, x - r)]
                    }
                }
            }
        }
    }

    private static func boxV(_ src: [Int], _ dst: inout [Int], _ w: Int, _ h: Int, _ r: Int) {
        let div = 2 * r + 1
        src.withUnsafeBufferPointer { s in
            dst.withUnsafeMutableBufferPointer { d in
                for x in 0..<w {
                    var sum = 0
                    for i in -r...r { sum += s[min(max(i, 0), h - 1) * w + x] }
                    for y in 0..<h {
                        d[y * w + x] = sum / div
                        sum += s[min(h - 1, y + r + 1) * w + x] - s[max(0, y - r) * w + x]
                    }
                }
            }
        }
    }

    /// Keskinleştirme maskesi: c + k·(c − bulanık c).
    private static func sharpen(_ src: [UInt32], _ w: Int, _ h: Int, _ amount: Float) -> [UInt32] {
        let soft = blur(src, w, h, max(1, min(w, h) / 500 + 1))
        let k = amount * 1.6
        return src.indices.map { i in
            let c = src[i], s = soft[i]
            func ch(_ shift: UInt32) -> UInt32 { let v = Int(c >> shift & 0xFF); return clamp(Float(v) + k * Float(v - Int(s >> shift & 0xFF))) }
            return (c & 0xFF00_0000) | ch(16) << 16 | ch(8) << 8 | ch(0)
        }
    }

    /// Vinyet, gren ve solukluk.
    private static func finish(_ src: [UInt32], _ w: Int, _ h: Int, _ a: ImageAdjust) -> [UInt32] {
        var px = src
        let cx = Float(w - 1) / 2, cy = Float(h - 1) / 2
        let maxD = max((cx * cx + cy * cy).squareRoot(), 1)
        let fadeLift = a.fade * 70
        let fadeK = 1 - a.fade * 0.28
        for y in 0..<h {
            for x in 0..<w {
                let i = y * w + x
                let c = px[i]
                if c >> 24 == 0 { continue }
                var m: Float = 1
                if a.vignette > 0 {
                    let fx = Float(x) - cx, fy = Float(y) - cy
                    let d = (fx * fx + fy * fy).squareRoot() / maxD
                    let t = min(max((d - 0.4) / 0.6, 0), 1)
                    m = 1 - a.vignette * 0.85 * t * t * (3 - 2 * t)
                }
                var noise: Float = 0
                if a.grain > 0 {
                    var hsh = Int32(truncatingIfNeeded: x) &* 374_761_393 &+ Int32(truncatingIfNeeded: y) &* 668_265_263
                    hsh = (hsh ^ Int32(bitPattern: UInt32(bitPattern: hsh) >> 13)) &* 1_274_126_177
                    noise = (Float(UInt32(bitPattern: hsh) >> 8 & 0xFFFF) / 65535 - 0.5) * a.grain * 70
                }
                func ch(_ v: UInt32) -> UInt32 { clamp((Float(v) * m + noise) * fadeK + fadeLift) }
                px[i] = (c & 0xFF00_0000) | ch(c >> 16 & 0xFF) << 16 | ch(c >> 8 & 0xFF) << 8 | ch(c & 0xFF)
            }
        }
        return px
    }

    /// İki ARGB dizisinin ortalama kanal farkı.
    static func meanDiff(_ a: [UInt32], _ b: [UInt32]) -> Float {
        var s = 0
        for i in a.indices {
            let x = a[i], y = b[i]
            s += abs(Int(x >> 16 & 0xFF) - Int(y >> 16 & 0xFF)) + abs(Int(x >> 8 & 0xFF) - Int(y >> 8 & 0xFF)) + abs(Int(x & 0xFF) - Int(y & 0xFF))
        }
        return Float(s) / (3 * Float(a.count))
    }
}
