import CoreGraphics
import Foundation

/// Android `Bitmap` karşılığı yardımcılar: ARGB (0xAARRGGBB, önçarpımsız) piksellerden CGImage ve Android gibi
/// y ekseni aşağı bakan çizim bağlamı. Görüntünün 0. satırı üst satırdır.
enum Bitmaps {
    static let space = CGColorSpace(name: CGColorSpace.sRGB)!

    /// [w]×[h] ARGB piksellerden görüntü.
    static func image(argb px: [UInt32], _ w: Int, _ h: Int) -> CGImage {
        var data = [UInt8](repeating: 0, count: w * h * 4)
        for i in 0..<(w * h) {
            let c = px[i]
            let a = Int(c >> 24 & 0xFF)
            // Önçarpımlı RGBA
            data[i * 4] = UInt8((Int(c >> 16 & 0xFF) * a + 127) / 255)
            data[i * 4 + 1] = UInt8((Int(c >> 8 & 0xFF) * a + 127) / 255)
            data[i * 4 + 2] = UInt8((Int(c & 0xFF) * a + 127) / 255)
            data[i * 4 + 3] = UInt8(a)
        }
        let provider = CGDataProvider(data: Data(data) as CFData)!
        return CGImage(width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4, space: space,
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                       provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
    }

    /// Y ekseni aşağı bakan (Android tuvali gibi) boş bağlamda çizip görüntü üretir.
    static func draw(_ w: Int, _ h: Int, _ body: (CGContext) -> Void) -> CGImage {
        let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: space,
                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        ctx.translateBy(x: 0, y: CGFloat(h))
        ctx.scaleBy(x: 1, y: -1)
        body(ctx)
        return ctx.makeImage()!
    }

    /// Görüntüyü y ekseni aşağı bakan bağlamda [rect]'e dik (ters dönmeden) çizer.
    static func drawUpright(_ ctx: CGContext, _ image: CGImage, in rect: CGRect) {
        ctx.saveGState()
        ctx.translateBy(x: rect.minX, y: rect.maxY)
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: rect.width, height: rect.height))
        ctx.restoreGState()
    }

    /// Kâğıda sabit döşeme: karo [tile]×[tile] tuval biriminde, tuvalin (0,0) noktasından başlar. Geçerli kırpma
    /// alanını doldurur (y ekseni aşağı bakan bağlamda karo dik kalır).
    static func tile(_ ctx: CGContext, _ image: CGImage, scale: CGFloat, interpolate: Bool = true) {
        let w = CGFloat(image.width) * scale, h = CGFloat(image.height) * scale
        let clip = ctx.boundingBoxOfClipPath
        guard !clip.isNull, w > 0, h > 0 else { return }
        ctx.saveGState()
        ctx.interpolationQuality = interpolate ? .default : .none
        let x0 = (clip.minX / w).rounded(.down), y0 = (clip.minY / h).rounded(.down)
        let x1 = (clip.maxX / w).rounded(.up), y1 = (clip.maxY / h).rounded(.up)
        // Çok geniş alanlarda (uzaklaştırılmış görünüm) karo sayısı sınırlı: CG döşemesine bırakılır
        if (x1 - x0) * (y1 - y0) > 4096 {
            ctx.translateBy(x: 0, y: 0)
            ctx.scaleBy(x: 1, y: -1)
            ctx.draw(image, in: CGRect(x: 0, y: -h, width: w, height: h), byTiling: true)
        } else {
            var y = y0
            while y < y1 {
                var x = x0
                while x < x1 {
                    drawUpright(ctx, image, in: CGRect(x: x * w, y: y * h, width: w, height: h))
                    x += 1
                }
                y += 1
            }
        }
        ctx.restoreGState()
    }

    /// Java `Math.floorMod`.
    @inline(__always) static func floorMod(_ a: Int, _ b: Int) -> Int { ((a % b) + b) % b }

    /// Kotlin `Float.roundToInt()`.
    @inline(__always) static func roundToInt(_ v: Float) -> Int { Int((Double(v) + 0.5).rounded(.down)) }
}

/// Android `Color.colorToHSV` / `HSVToColor`.
enum HSV {
    static func from(_ c: UInt32) -> (Float, Float, Float) {
        let r = Float(ArgbColor.red(c)) / 255, g = Float(ArgbColor.green(c)) / 255, b = Float(ArgbColor.blue(c)) / 255
        let mx = max(r, g, b), mn = min(r, g, b)
        let d = mx - mn
        var h: Float = 0
        if d > 0 {
            if mx == r { h = (g - b) / d } else if mx == g { h = 2 + (b - r) / d } else { h = 4 + (r - g) / d }
            h *= 60
            if h < 0 { h += 360 }
        }
        return (h, mx == 0 ? 0 : d / mx, mx)
    }

    static func color(_ h: Float, _ s: Float, _ v: Float) -> UInt32 {
        let hh = (h.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360) / 60
        let i = Int(hh)
        let f = hh - Float(i)
        let p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f))
        let (r, g, b): (Float, Float, Float)
        switch i {
        case 0: (r, g, b) = (v, t, p)
        case 1: (r, g, b) = (q, v, p)
        case 2: (r, g, b) = (p, v, t)
        case 3: (r, g, b) = (p, q, v)
        case 4: (r, g, b) = (t, p, v)
        default: (r, g, b) = (v, p, q)
        }
        return ArgbColor.rgb(Bitmaps.roundToInt(r * 255), Bitmaps.roundToInt(g * 255), Bitmaps.roundToInt(b * 255))
    }
}

/// Program içinde üretilen fırça dokuları, yarım ton, yumuşak damga ve şekil yolları (Android `BrushTextures`).
/// Döşenebilir dokular beyaz + alfa; renk çizimde verilir. Doku tuval biriminde, kâğıda sabittir.
enum BrushTextures {
    private static var images: [String: CGImage] = [:]
    private static let lock = NSLock()

    private static func cached(_ key: String, _ make: () -> CGImage) -> CGImage {
        lock.lock(); defer { lock.unlock() }
        if let img = images[key] { return img }
        let img = make()
        images[key] = img
        return img
    }

    static func grain(_ kind: TextureKind, _ level: Int) -> CGImage {
        cached("grain-\(kind.rawValue)-\(level)") {
            let l = Float(level)
            switch kind {
            case .PASTEL_GRAIN: return noiseTile(128, octaves: [64, 32], gap: 0.34 - 0.05 * l, softness: 0.03, seed: 11)
            case .CHARCOAL_GRAIN: return noiseTile(128, octaves: [32, 16, 64], gap: 0.58 - 0.06 * l, softness: 0.12, seed: 23, streak: true)
            case .SPONGE: return noiseTile(96, octaves: [12, 24], gap: 0.46 - 0.05 * l, softness: 0.04, seed: 37)
            case .CANVAS_WEAVE: return weaveTile(level)
            }
        }
    }

    /// v21 akışkanlık: az boyada izin kâğıt dişine takılması (ikili gürültü).
    static func flowGrain(_ flow: Float) -> CGImage {
        let level = min(max(Bitmaps.roundToInt(min(max(flow, 0), 1) * 10), 0), 9)
        return cached("flow-\(level)") {
            let coverage = 0.38 + 0.6 * Float(level) / 10
            return noiseTile(128, octaves: [64, 32, 128], gap: 1 - coverage, softness: 0, seed: 71)
        }
    }

    /// Yarım ton karosu: [pitch] aralık, [level] 0..7 kaplama.
    static func halftone(_ kind: HalftoneKind, _ pitch: Int, _ level: Int) -> CGImage {
        cached("ht-\(kind.rawValue)-\(pitch)-\(level)") {
            let coverage = 0.12 + 0.1 * Float(level)
            let t = max(4, Bitmaps.roundToInt(Float(pitch) * 1.41421))
            let tf = CGFloat(t)
            return Bitmaps.draw(t, t) { c in
                c.setFillColor(gray: 1, alpha: 1)
                c.setStrokeColor(gray: 1, alpha: 1)
                switch kind {
                case .DOTS:
                    let r = CGFloat(Float(t) * (coverage / (2 * Float.pi)).squareRoot())
                    for (x, y) in [(0, 0), (tf, 0), (0, tf), (tf, tf), (tf / 2, tf / 2)] as [(CGFloat, CGFloat)] {
                        c.fillEllipse(in: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
                    }
                case .LINES:
                    c.setLineWidth(CGFloat(max(1, coverage * Float(pitch))))
                    diagonals(c, tf)
                }
            }
        }
    }

    /// Tarama deseni karosu: sabit aralıklı ince 45° çizgiler.
    static func hatch(_ pitch: Int) -> CGImage {
        cached("hatch-\(pitch)") {
            let t = max(4, Bitmaps.roundToInt(Float(pitch) * 1.41421))
            return Bitmaps.draw(t, t) { c in
                c.setStrokeColor(gray: 1, alpha: 1)
                c.setLineWidth(CGFloat(max(1, Float(pitch) * 0.22)))
                diagonals(c, CGFloat(t))
            }
        }
    }

    private static func diagonals(_ c: CGContext, _ tf: CGFloat) {
        c.setLineCap(.butt)
        for (a, b) in [((-tf, tf), (tf, -tf)), ((-tf, 2 * tf), (2 * tf, -tf)), ((0, 2 * tf), (2 * tf, 0))] as [((CGFloat, CGFloat), (CGFloat, CGFloat))] {
            c.move(to: CGPoint(x: a.0, y: a.1)); c.addLine(to: CGPoint(x: b.0, y: b.1))
        }
        c.strokePath()
    }

    /// Ortası [hardness] oranında dolu, kenara doğru yumuşakça sönen yuvarlak damga (beyaz + alfa).
    static func softStamp(_ hardness: Float) -> CGImage {
        let level = Bitmaps.roundToInt(min(max(hardness, 0), 1) * 10)
        return cached("soft-\(level)") {
            let size = 64
            let h = Float(level) / 10
            var px = [UInt32](repeating: 0, count: size * size)
            let c = Float(size - 1) / 2
            for y in 0..<size {
                for x in 0..<size {
                    let r = ((Float(x) - c) * (Float(x) - c) + (Float(y) - c) * (Float(y) - c)).squareRoot() / c
                    let a: Float
                    if r >= 1 { a = 0 } else if r <= h { a = 1 } else {
                        let t = (r - h) / (1 - h)
                        a = 0.5 + 0.5 * cos(t * Float.pi)
                    }
                    px[y * size + x] = UInt32(Bitmaps.roundToInt(a * a * 255)) << 24 | 0x00FF_FFFF
                }
            }
            return Bitmaps.image(argb: px, size, size)
        }
    }

    /// Merkezde, birim boyutta (≈1×1) şekil yolu.
    static func shapePath(_ shape: StampShape) -> CGPath {
        let p = CGMutablePath()
        switch shape {
        case .STAR:
            for i in 0..<10 {
                let r: Float = i % 2 == 0 ? 0.5 : 0.21
                let a = -Float.pi / 2 + Float(i) * Float.pi / 5
                let pt = CGPoint(x: CGFloat(cos(a) * r), y: CGFloat(sin(a) * r))
                if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
            }
            p.closeSubpath()
        case .HEART:
            p.move(to: CGPoint(x: 0, y: 0.45))
            p.addCurve(to: CGPoint(x: 0, y: -0.2), control1: CGPoint(x: -0.55, y: 0.05), control2: CGPoint(x: -0.45, y: -0.5))
            p.addCurve(to: CGPoint(x: 0, y: 0.45), control1: CGPoint(x: 0.45, y: -0.5), control2: CGPoint(x: 0.55, y: 0.05))
            p.closeSubpath()
        case .LEAF:
            p.move(to: CGPoint(x: -0.5, y: 0))
            p.addQuadCurve(to: CGPoint(x: 0.5, y: 0), control: CGPoint(x: 0, y: -0.32))
            p.addQuadCurve(to: CGPoint(x: -0.5, y: 0), control: CGPoint(x: 0, y: 0.32))
            p.closeSubpath()
        }
        return p
    }

    // MARK: - Üreticiler

    static func hash(_ x: Int, _ y: Int, _ seed: Int) -> Float {
        var h = Int32(truncatingIfNeeded: x) &* 374761393 &+ Int32(truncatingIfNeeded: y) &* 668265263 &+ Int32(truncatingIfNeeded: seed) &* 144665
        h = (h ^ Int32(bitPattern: UInt32(bitPattern: h) >> 13)) &* 1274126177
        h = h ^ Int32(bitPattern: UInt32(bitPattern: h) >> 16)
        return Float(h & 0x7FFF_FFFF) / 2147483647
    }

    private static func smooth(_ t: Float) -> Float { t * t * (3 - 2 * t) }

    private static func noiseTile(_ size: Int, octaves: [Int], gap: Float, softness: Float, seed: Int, streak: Bool = false) -> CGImage {
        var values = [Float](repeating: 0, count: size * size)
        for y in 0..<size {
            for x in 0..<size {
                var v: Float = 0, weight: Float = 0
                for (o, cells) in octaves.enumerated() {
                    let cellsY = streak ? max(2, cells / 3) : cells
                    let fx = Float(x * cells) / Float(size)
                    let fy = Float(y * cellsY) / Float(size)
                    let x0 = Int(fx.rounded(.down)), y0 = Int(fy.rounded(.down))
                    let tx = smooth(fx - Float(x0)), ty = smooth(fy - Float(y0))
                    func h(_ ix: Int, _ iy: Int) -> Float { hash(Bitmaps.floorMod(ix, cells), Bitmaps.floorMod(iy, cellsY), seed + o * 7) }
                    let top = h(x0, y0) + (h(x0 + 1, y0) - h(x0, y0)) * tx
                    let bottom = h(x0, y0 + 1) + (h(x0 + 1, y0 + 1) - h(x0, y0 + 1)) * tx
                    let w = 1 / Float(o + 1)
                    v += (top + (bottom - top) * ty) * w
                    weight += w
                }
                values[y * size + x] = v / weight
            }
        }
        let sorted = values.sorted()
        let threshold = sorted[Int(min(max(gap, 0), 0.95) * Float(sorted.count - 1))]
        let range = max(sorted[sorted.count - 1] - sorted[0], 1e-3)
        let px = values.map { value -> UInt32 in
            let d = (value - threshold) / range
            let a: Float = softness <= 0 ? (d >= 0 ? 1 : 0) : min(max((d + softness) / (2 * softness), 0), 1)
            return UInt32(Bitmaps.roundToInt(a * 255)) << 24 | 0x00FF_FFFF
        }
        return Bitmaps.image(argb: px, size, size)
    }

    private static func weaveTile(_ level: Int) -> CGImage {
        let size = 24
        let gapAlpha = 20 + 22 * level
        var px = [UInt32](repeating: 0, count: size * size)
        for y in 0..<size {
            for x in 0..<size {
                let threadX = (2...9).contains(x % 12), threadY = (2...9).contains(y % 12)
                let over = ((x / 12) + (y / 12)) % 2 == 0
                let a: Int
                if threadX && threadY { a = over ? 255 : 200 } else if threadX || threadY { a = 150 } else { a = gapAlpha }
                px[y * size + x] = UInt32(min(a, 255)) << 24 | 0x00FF_FFFF
            }
        }
        return Bitmaps.image(argb: px, size, size)
    }
}
