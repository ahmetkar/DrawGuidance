import CoreGraphics
import Foundation

/// v21 doku ve desen fırçalarının karoları (Android `SurfaceTextures`): fırçanın renginden tonlanan, döşenebilir,
/// opak 128×128 resimler. Karo kâğıda sabittir; fırça geçtiği yerde açığa çıkar.
enum SurfaceTextures {
    static let SIZE = 128
    private static var cache: [String: CGImage] = [:]
    private static var order: [String] = []
    private static let lock = NSLock()

    static func tile(_ kind: SurfaceKind, _ color: UInt32) -> CGImage {
        let base = color | 0xFF00_0000
        let key = "\(kind.rawValue)-\(base)"
        lock.lock()
        if let img = cache[key] { lock.unlock(); return img }
        lock.unlock()
        let img = build(kind, base)
        lock.lock()
        cache[key] = img
        order.append(key)
        if order.count > 24 { cache.removeValue(forKey: order.removeFirst()) }
        lock.unlock()
        return img
    }

    static func scaleFor(_ kind: SurfaceKind, _ width: Float) -> Float {
        let raw: Float
        switch kind {
        case .CARPET, .WALLPAPER: raw = width / 60
        case .WOOD_PAINT, .WOOD: raw = width / 48
        default: raw = width / 40
        }
        return Float(Bitmaps.roundToInt(min(max(raw, 0.5), 4) * 20)) / 20
    }

    private static func build(_ kind: SurfaceKind, _ base: UInt32) -> CGImage {
        switch kind {
        case .SKIN: return fromLight(base, skin)
        case .FUR: return fur(base)
        case .PLANT: return fromLight(base, plant)
        case .WOOD: return fromLight(base, wood)
        case .IRON: return iron(base)
        case .METAL: return fromLight(base, metal)
        case .CARPET: return carpet(base)
        case .WALLPAPER: return wallpaper(base)
        case .WOOD_PAINT: return woodPaint(base)
        }
    }

    // MARK: - Renk

    static func tone(_ base: UInt32, _ l: Float) -> UInt32 { ArgbColor.tone(base, l) }
    static func mix(_ a: UInt32, _ b: UInt32, _ t: Float) -> UInt32 { ArgbColor.mix(a, b, t) }

    static func hueShift(_ base: UInt32, _ degrees: Float, minSat: Float = 0.35) -> UInt32 {
        var (h, s, v) = HSV.from(base)
        h = ((h + degrees).truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)
        s = max(s, minSat)
        v = min(max(v, 0.3), 0.9)
        return HSV.color(h, s, v)
    }

    private static func fromLight(_ base: UInt32, _ light: (Int, Int) -> Float) -> CGImage {
        var px = [UInt32](repeating: 0, count: SIZE * SIZE)
        for y in 0..<SIZE { for x in 0..<SIZE { px[y * SIZE + x] = tone(base, light(x, y)) } }
        return Bitmaps.image(argb: px, SIZE, SIZE)
    }

    // MARK: - Döşenebilir gürültü

    private static func hash(_ x: Int, _ y: Int, _ seed: Int) -> Float {
        var h = Int32(truncatingIfNeeded: x) &* 374761393 &+ Int32(truncatingIfNeeded: y) &* 668265263 &+ Int32(truncatingIfNeeded: seed) &* 1442695041
        h = (h ^ Int32(bitPattern: UInt32(bitPattern: h) >> 13)) &* 1274126177
        h = h ^ Int32(bitPattern: UInt32(bitPattern: h) >> 16)
        return Float(h & 0x7FFF_FFFF) / 2147483647
    }

    private static func smooth(_ t: Float) -> Float { t * t * (3 - 2 * t) }

    private static func noise(_ x: Float, _ y: Float, _ cx: Int, _ cy: Int, _ seed: Int) -> Float {
        let fx = x * Float(cx) / Float(SIZE), fy = y * Float(cy) / Float(SIZE)
        let x0 = Int(fx.rounded(.down)), y0 = Int(fy.rounded(.down))
        let tx = smooth(fx - Float(x0)), ty = smooth(fy - Float(y0))
        func h(_ ix: Int, _ iy: Int) -> Float { hash(Bitmaps.floorMod(ix, cx), Bitmaps.floorMod(iy, cy), seed) }
        let top = h(x0, y0) + (h(x0 + 1, y0) - h(x0, y0)) * tx
        let bottom = h(x0, y0 + 1) + (h(x0 + 1, y0 + 1) - h(x0, y0 + 1)) * tx
        return top + (bottom - top) * ty
    }

    private static func fbm(_ x: Float, _ y: Float, _ seed: Int, _ cells: Int...) -> Float {
        var v: Float = 0, w: Float = 0
        for (o, c) in cells.enumerated() {
            let weight = 1 / Float(o + 1)
            v += noise(x, y, c, c, seed + o * 17) * weight
            w += weight
        }
        return v / w
    }

    private static func worley(_ x: Float, _ y: Float, _ cells: Int, _ seed: Int) -> (Float, Float) {
        let cs = Float(SIZE) / Float(cells)
        let gx = Int((x / cs).rounded(.down)), gy = Int((y / cs).rounded(.down))
        var f1 = Float.greatestFiniteMagnitude, f2 = Float.greatestFiniteMagnitude
        for dy in -1...1 {
            for dx in -1...1 {
                let cx = gx + dx, cy = gy + dy
                let mx = Bitmaps.floorMod(cx, cells), my = Bitmaps.floorMod(cy, cells)
                let px = (Float(cx) + hash(mx, my, seed)) * cs
                let py = (Float(cy) + hash(mx, my, seed + 1)) * cs
                let d = ((px - x) * (px - x) + (py - y) * (py - y)).squareRoot()
                if d < f1 { f2 = f1; f1 = d } else if d < f2 { f2 = d }
            }
        }
        return (f1, f2)
    }

    private static func wave(_ x: Int, _ y: Int, _ a: Int, _ b: Int, _ phase: Float = 0) -> Float {
        Float(sin(Double(x * a + y * b) * 2.0 * Double.pi / Double(SIZE) + Double(phase)))
    }

    // MARK: - Dokular

    private static func skin(_ x: Int, _ y: Int) -> Float {
        let fx = Float(x), fy = Float(y)
        var l: Float = 0.5 + (fbm(fx, fy, 3, 4, 8, 16) - 0.5) * 0.18
        let w = worley(fx, fy, 22, 5)
        let cell = hash(Int(fx / (Float(SIZE) / 22)), Int(fy / (Float(SIZE) / 22)), 6)
        if cell > 0.35 { l -= 0.13 * min(max(1 - w.0 / 1.3, 0), 1) }
        let a = abs(wave(x, y, 5, 3, noise(fx, fy, 8, 8, 7) * 4))
        let b = abs(wave(x, y, -3, 5, noise(fx, fy, 8, 8, 8) * 4))
        let show = noise(fx, fy, 6, 6, 9)
        if a > 0.993 && show > 0.45 { l -= 0.035 }
        if b > 0.995 && show < 0.55 { l -= 0.03 }
        return l
    }

    private static func plant(_ x: Int, _ y: Int) -> Float {
        let fx = Float(x), fy = Float(y)
        var l: Float = 0.47 + (fbm(fx, fy, 11, 4, 8) - 0.5) * 0.12
        let w = worley(fx, fy, 9, 12)
        let edge = w.1 - w.0
        if edge < 1.4 { l += 0.16 * (1 - edge / 1.4) }
        let main = abs(wave(x, y, 2, 2, noise(fx, fy, 4, 4, 13) * 1.5))
        if main > 0.965 { l += 0.2 * (main - 0.965) / 0.035 }
        let side = abs(wave(x, y, 4, -4, noise(fx, fy, 4, 4, 14)))
        if side > 0.985 { l += 0.08 }
        return l
    }

    private static func wood(_ x: Int, _ y: Int) -> Float {
        let fx = Float(x), fy = Float(y)
        let t = fy / Float(SIZE) * 10 + (fbm(fx, fy, 21, 2, 4) - 0.5) * 2.6 + 0.15 * Float(sin(Double(fx) / Double(SIZE) * 2 * Double.pi))
        let r = t - t.rounded(.down)
        var l: Float = r > 0.84 ? 0.4 - 0.06 * Float(sin(Double((r - 0.84) / 0.16) * Double.pi)) : 0.5 + 0.08 * r
        l += (noise(fx, fy, 3, 48, 22) - 0.5) * 0.12
        if hash(x / 6, y, 23) > 0.985 { l -= 0.12 }
        return l
    }

    private static func metal(_ x: Int, _ y: Int) -> Float {
        let fx = Float(x), fy = Float(y)
        var l: Float = 0.56 + (noise(fx, fy, 2, 96, 31) - 0.5) * 0.14 + (noise(fx, fy, 4, 64, 32) - 0.5) * 0.08
        l += 0.16 * Float(sin(Double(fy) / Double(SIZE) * 2 * Double.pi + 0.6))
        if hash(x / 10, (y + x / 3) / 1, 33) > 0.9975 { l += 0.16 }
        return l
    }

    private static func iron(_ base: UInt32) -> CGImage {
        let rust = ArgbColor.rgb(142, 72, 30), rustLight = ArgbColor.rgb(186, 104, 46)
        var px = [UInt32](repeating: 0, count: SIZE * SIZE)
        for y in 0..<SIZE {
            for x in 0..<SIZE {
                let fx = Float(x), fy = Float(y)
                var l: Float = 0.42 + (fbm(fx, fy, 41, 16, 32, 64) - 0.5) * 0.22
                let w = worley(fx, fy, 14, 42)
                if w.0 < 1.6 && hash(Int(fx / (Float(SIZE) / 14)), Int(fy / (Float(SIZE) / 14)), 43) > 0.55 { l -= 0.16 }
                var c = tone(base, l)
                let patch = fbm(fx, fy, 44, 3, 6, 12)
                let m = min(max((patch - 0.56) / 0.12, 0), 1)
                if m > 0 {
                    let speck = hash(x, y, 45)
                    c = mix(c, speck > 0.6 ? rustLight : rust, m * (0.7 + 0.3 * speck))
                }
                px[y * SIZE + x] = c
            }
        }
        return Bitmaps.image(argb: px, SIZE, SIZE)
    }

    private static func fur(_ base: UInt32) -> CGImage {
        Bitmaps.draw(SIZE, SIZE) { c in
            c.setFillColor(ArgbColor.cgColor(tone(base, 0.36)))
            c.fill(CGRect(x: 0, y: 0, width: SIZE, height: SIZE))
            c.setLineCap(.round)
            let s = Float(SIZE)
            for i in 0..<1100 {
                let x = hash(i, 0, 51) * s, y = hash(i, 1, 51) * s
                let ang = (72 + (hash(i, 2, 51) - 0.5) * 30 + (noise(x, y, 4, 4, 52) - 0.5) * 40) * Float.pi / 180
                let len = 6 + 9 * hash(i, 3, 51)
                let ex = x + cos(ang) * len, ey = y + sin(ang) * len
                let l = 0.3 + 0.55 * hash(i, 4, 51) * (0.6 + 0.4 * noise(x, y, 6, 6, 53))
                c.setStrokeColor(ArgbColor.cgColor(tone(base, l)))
                c.setLineWidth(CGFloat(0.8 + 0.7 * hash(i, 5, 51)))
                for ox in -1...1 {
                    for oy in -1...1 {
                        let dx = CGFloat(ox * SIZE), dy = CGFloat(oy * SIZE)
                        c.move(to: CGPoint(x: CGFloat(x) + dx, y: CGFloat(y) + dy))
                        c.addLine(to: CGPoint(x: CGFloat(ex) + dx, y: CGFloat(ey) + dy))
                        c.strokePath()
                    }
                }
            }
        }
    }

    // MARK: - Desenler

    private static func carpet(_ base: UInt32) -> CGImage {
        let bg = tone(base, 0.5), dark = tone(base, 0.16), light = tone(base, 0.86)
        let accent = hueShift(base, 160)
        let accent2 = mix(hueShift(base, -38), ArgbColor.white, 0.2)
        var px = [UInt32](repeating: 0, count: SIZE * SIZE)
        let c = SIZE / 2
        for y in 0..<SIZE {
            for x in 0..<SIZE {
                let d = ((abs(x - c) / 4) + (abs(y - c) / 4)) * 4
                let cx = min(x, SIZE - 1 - x), cy = min(y, SIZE - 1 - y)
                let corner = ((cx / 4) + (cy / 4)) * 4
                let color: UInt32
                if y < 4 || y >= SIZE - 4 { color = dark }
                else if y < 8 || y >= SIZE - 8 { color = ((x / 4) + (y / 4)) % 2 == 0 ? accent2 : light }
                else if d < 12 { color = accent2 }
                else if d < 16 { color = dark }
                else if d < 28 { color = accent }
                else if d < 32 { color = dark }
                else if d < 40 { color = light }
                else if d < 44 { color = dark }
                else if corner < 12 { color = accent }
                else if corner < 16 { color = dark }
                else if (x % 32 == 16 && abs(y % 32 - 16) < 3) || (y % 32 == 16 && abs(x % 32 - 16) < 3) { color = d > 50 ? light : bg }
                else { color = bg }
                px[y * SIZE + x] = color
            }
        }
        return Bitmaps.image(argb: px, SIZE, SIZE)
    }

    private static func wallpaper(_ base: UInt32) -> CGImage {
        Bitmaps.draw(SIZE, SIZE) { c in
            c.setFillColor(ArgbColor.cgColor(tone(base, 0.62)))
            c.fill(CGRect(x: 0, y: 0, width: SIZE, height: SIZE))
            let motif = damask(), inner = damaskInner()
            for (ox, oy) in [(64, 64), (0, 0), (128, 0), (0, 128), (128, 128)] as [(CGFloat, CGFloat)] {
                c.saveGState()
                c.translateBy(x: ox, y: oy)
                c.scaleBy(x: 0.62, y: 0.62)
                c.setFillColor(ArgbColor.cgColor(tone(base, 0.4)))
                c.addPath(motif)
                c.fillPath()
                c.setStrokeColor(ArgbColor.cgColor(tone(base, 0.8)))
                c.setLineWidth(1.2)
                c.addPath(inner)
                c.strokePath()
                c.restoreGState()
            }
        }
    }

    /// Yatayda simetrik süsleme (iki yarının birleşimi; dolgu sıfırdan farklı kuralıyla).
    private static func damask() -> CGPath {
        let half = CGMutablePath()
        half.move(to: CGPoint(x: 0, y: -52))
        half.addCurve(to: CGPoint(x: 12, y: -16), control1: CGPoint(x: 8, y: -40), control2: CGPoint(x: 18, y: -30))
        half.addCurve(to: CGPoint(x: 40, y: -2), control1: CGPoint(x: 30, y: -30), control2: CGPoint(x: 46, y: -20))
        half.addCurve(to: CGPoint(x: 20, y: 0), control1: CGPoint(x: 36, y: 10), control2: CGPoint(x: 22, y: 8))
        half.addCurve(to: CGPoint(x: 8, y: 36), control1: CGPoint(x: 28, y: 16), control2: CGPoint(x: 22, y: 32))
        half.addCurve(to: CGPoint(x: 0, y: 56), control1: CGPoint(x: 12, y: 44), control2: CGPoint(x: 8, y: 50))
        half.addLine(to: CGPoint(x: 0, y: -52))
        half.closeSubpath()
        var mirror = CGAffineTransform(scaleX: -1, y: 1)
        let full = CGMutablePath()
        full.addPath(half)
        if let m = half.copy(using: &mirror) { full.addPath(m) }
        return full
    }

    private static func damaskInner() -> CGPath {
        let p = CGMutablePath()
        p.move(to: CGPoint(x: 0, y: -34)); p.addCurve(to: CGPoint(x: 0, y: 30), control1: CGPoint(x: 6, y: -20), control2: CGPoint(x: 6, y: 10))
        p.move(to: CGPoint(x: 0, y: -34)); p.addCurve(to: CGPoint(x: 0, y: 30), control1: CGPoint(x: -6, y: -20), control2: CGPoint(x: -6, y: 10))
        p.addEllipse(in: CGRect(x: -4, y: -12, width: 8, height: 8))
        return p
    }

    private static func woodPaint(_ base: UInt32) -> CGImage {
        let woodBase = ArgbColor.rgb(156, 107, 63)
        var px = [UInt32](repeating: 0, count: SIZE * SIZE)
        let plank = SIZE / 4
        for y in 0..<SIZE {
            for x in 0..<SIZE {
                let row = y / plank, inRow = y % plank
                let joint = Int(hash(row, 0, 61) * Float(SIZE))
                let fx = Float(x), fy = Float(y)
                let gap = inRow < 2 || abs(x - joint) < 1
                let color: UInt32
                if gap { color = tone(base, 0.12) } else {
                    let wear = fbm(fx, fy, 62, 4, 8, 16)
                    if wear > 0.66 {
                        let t = fy / Float(SIZE) * 12 + (noise(fx, fy, 2, 4, 63) - 0.5) * 3
                        let r = t - t.rounded(.down)
                        color = tone(woodBase, r > 0.8 ? 0.35 : 0.5 + 0.05 * r)
                    } else {
                        var l: Float = 0.5 + (noise(fx, fy, 3, 40, 64) - 0.5) * 0.16
                        if inRow < 5 { l -= 0.06 }
                        let nail = abs(x - (joint + 5) % SIZE) < 2 && abs(inRow - plank / 2) < 2
                        color = tone(base, nail ? 0.2 : l)
                    }
                }
                px[y * SIZE + x] = color
            }
        }
        return Bitmaps.image(argb: px, SIZE, SIZE)
    }
}
