import Foundation

/// v56 hazır doku paketi (aşama 5, saf ve deterministik; Android `V56Textures.kt` ile aynı tamsayı karması ve aynı
/// formüller, aynı karolar). Karolar `SIZE`×`SIZE`, döşenebilir; değer 0..1 "boya tutma" oranıdır (1: tam boya,
/// 0: kağıt dişindeki boşluk). Gren, kement ve katman doku dolgusu bu değerleri kullanır.
enum V56Textures {
    static let PREFIX = "builtin:"
    static let SIZE = 128
    static let NAMES = ["paper", "canvas", "charcoal", "noise", "dots", "hatch"]
    static let BUILTIN: [String] = NAMES.map { PREFIX + $0 }

    static func isBuiltin(_ id: String?) -> Bool { id.map { BUILTIN.contains($0) } ?? false }

    static let MIN_SCALE: Float = 0.25
    static let MAX_SCALE: Float = 8

    /// Kotlin `Int` taşmasıyla aynı (32 bit, sarmalı) karma.
    private static func hash(_ x: Int, _ y: Int, _ seed: Int) -> Float {
        var h = Int32(truncatingIfNeeded: x) &* 374761393 &+ Int32(truncatingIfNeeded: y) &* 668265263 &+ Int32(truncatingIfNeeded: seed) &* 1442695041
        h = (h ^ Int32(bitPattern: UInt32(bitPattern: h) >> 13)) &* 1274126177
        h = h ^ Int32(bitPattern: UInt32(bitPattern: h) >> 16)
        return Float(h & 0xFFFF) / 65535
    }

    /// Döşenebilir değer gürültüsü: hücre `cx`×`cy` piksel.
    private static func noise(_ x: Int, _ y: Int, _ cx: Int, _ cy: Int, _ seed: Int) -> Float {
        let nx = SIZE / cx, ny = SIZE / cy
        let ix = x / cx, iy = y / cy
        let fx = Float(x % cx) / Float(cx)
        let fy = Float(y % cy) / Float(cy)
        let sx = fx * fx * (3 - 2 * fx)
        let sy = fy * fy * (3 - 2 * fy)
        let a = hash(ix % nx, iy % ny, seed)
        let b = hash((ix + 1) % nx, iy % ny, seed)
        let c = hash(ix % nx, (iy + 1) % ny, seed)
        let d = hash((ix + 1) % nx, (iy + 1) % ny, seed)
        let top = a + (b - a) * sx
        let bottom = c + (d - c) * sx
        return top + (bottom - top) * sy
    }

    private static func ramp(_ v: Float, _ lo: Float, _ hi: Float) -> Float { min(max((v - lo) / (hi - lo), 0), 1) }

    private static func tri(_ t: Int, _ p: Int) -> Float {
        let f = Float(t % p) / Float(p) * 2 - 1
        return f < 0 ? -f : f
    }

    /// Karodaki (x, y) noktasının boya tutma değeri (0..1); bilinmeyen dokuda 1.
    static func value(_ id: String, _ x: Int, _ y: Int) -> Float {
        let px = Bitmaps.floorMod(x, SIZE), py = Bitmaps.floorMod(y, SIZE)
        let name = id.hasPrefix(PREFIX) ? String(id.dropFirst(PREFIX.count)) : id
        switch name {
        case "paper":
            let v1: Float = 0.5 * noise(px, py, 16, 16, 1)
            let v2: Float = 0.3 * noise(px, py, 8, 8, 2)
            let v3: Float = 0.2 * noise(px, py, 4, 4, 3)
            return ramp(v1 + v2 + v3, 0.3, 0.62)
        case "canvas":
            let thread: Float = (px / 8 + py / 8) % 2 == 0 ? 1 - tri(py, 8) : 1 - tri(px, 8)
            let v: Float = 0.3 + 0.6 * thread + 0.1 * noise(px, py, 4, 4, 5)
            return min(max(v, 0), 1)
        case "charcoal":
            let v1: Float = 0.6 * noise(px, py, 32, 4, 7)
            let v2: Float = 0.4 * noise(px, py, 8, 8, 8)
            return ramp(v1 + v2, 0.35, 0.6)
        case "noise":
            let v1: Float = 0.6 * noise(px, py, 2, 2, 11)
            let v2: Float = 0.4 * noise(px, py, 4, 4, 12)
            return ramp(v1 + v2, 0.25, 0.75)
        case "dots":
            let dx = Float(px % 16) - 7.5, dy = Float(py % 16) - 7.5
            return 1 - 0.85 * ramp(dx * dx + dy * dy, 16, 36)
        case "hatch":
            return (px + py) % 12 < 3 ? 1 : 0.15
        default:
            return 1
        }
    }

    static func tile(_ id: String) -> [Float] { (0..<(SIZE * SIZE)).map { value(id, $0 % SIZE, $0 / SIZE) } }

    static func alpha(_ value: Float, _ strength: Float) -> Float { 1 - min(max(strength, 0), 1) * (1 - value) }

    /// "Kayar" (moving) ise doku tuvale sabit (0, 0); değilse çizginin ilk noktasına bağlı.
    static func anchor(_ spec: GrainSpec, _ firstX: Float, _ firstY: Float) -> (Float, Float) {
        spec.moving ? (0, 0) : (firstX, firstY)
    }

    static func scaleOf(_ spec: GrainSpec) -> Float { min(max(spec.scale, MIN_SCALE), MAX_SCALE) }

    static func sample(_ spec: GrainSpec, _ x: Float, _ y: Float, _ firstX: Float = 0, _ firstY: Float = 0) -> Float {
        let (ax, ay) = anchor(spec, firstX, firstY)
        let s = scaleOf(spec)
        let u = Int(((x - ax) / s).rounded(.down)), v = Int(((y - ay) / s).rounded(.down))
        return alpha(value(spec.textureId, u, v), spec.strength)
    }

    static func pageRect(_ width: Float, _ height: Float) -> [StrokePoint] {
        [StrokePoint(0, 0), StrokePoint(width, 0), StrokePoint(width, height), StrokePoint(0, height)]
    }
}
