import Foundation

/// Tohum + damga sırası + tuzdan türetilen deterministik rastgelelik (Android `DabRandom`; bit düzeyinde aynı).
enum DabRandom {
    @inline(__always) private static func ushr(_ v: Int64, _ n: Int64) -> Int64 { Int64(bitPattern: UInt64(bitPattern: v) >> UInt64(n)) }

    private static func mix(_ input: Int64) -> Int64 {
        var z = input
        z = (z ^ ushr(z, 30)) &* -0x40a7b892e31b1a47
        z = (z ^ ushr(z, 27)) &* -0x6b2fb644ecceee15
        return z ^ ushr(z, 31)
    }

    /// [0, 1) aralığında sayı.
    static func unit(_ seed: Int32, _ index: Int, _ salt: Int) -> Float {
        let h = mix(Int64(seed) &* -0x61c8864680b583eb &+ Int64(index) &* -0x40a7b892e31b1a47 &+ Int64(salt) &* -0x6b2fb644ecceee15)
        return Float(Int32(truncatingIfNeeded: ushr(h, 40))) / 16777216
    }

    /// Standart normal dağılımlı, ±2.5 ile sınırlı sayı.
    static func gaussian(_ seed: Int32, _ index: Int, _ salt: Int) -> Float {
        let u1 = max(1e-7, unit(seed, index, salt))
        let u2 = unit(seed, index, salt + 7919)
        return min(max((-2 * logf(u1)).squareRoot() * cos(2 * Float.pi * u2), -2.5), 2.5)
    }
}

extension String {
    /// Java `String.hashCode()` (UTF-16 birimleri üzerinden): fırça rastgeleliğinin tohumu Android ile aynı olsun.
    var javaHashCode: Int32 {
        var h: Int32 = 0
        for u in utf16 { h = h &* 31 &+ Int32(u) }
        return h
    }
}

/// Örnek (damga konumu) listesi (Android `SampleBuffer`).
struct SampleBuffer {
    var x: [Float] = [], y: [Float] = [], dx: [Float] = [], dy: [Float] = []
    var along: [Float] = [], raw: [Int] = [], index: [Int] = []
    var size: Int { x.count }

    mutating func clear() { x.removeAll(); y.removeAll(); dx.removeAll(); dy.removeAll(); along.removeAll(); raw.removeAll(); index.removeAll() }

    mutating func add(_ sx: Float, _ sy: Float, _ sdx: Float, _ sdy: Float, _ a: Float, _ r: Int, _ k: Int) {
        x.append(sx); y.append(sy); dx.append(sdx); dy.append(sdy); along.append(a); raw.append(r); index.append(k)
    }

    mutating func truncate(_ n: Int) {
        guard n < size else { return }
        x.removeLast(size - n); y.removeLast(y.count - n); dx.removeLast(dx.count - n); dy.removeLast(dy.count - n)
        along.removeLast(along.count - n); raw.removeLast(raw.count - n); index.removeLast(index.count - n)
    }
}

/// Ham noktalardan, karesel orta nokta eğrileriyle yumuşatılmış çizgi üzerinde eşit aralıklı örnekler
/// (Android `CurveSampler`; çizim bitmiş hâliyle).
final class CurveSampler {
    private let step: Float
    var samples = SampleBuffer()

    private var started = false
    private var vx: Float = 0, vy: Float = 0
    private var acc: Float = 0
    private var k = 0
    private var lastDx: Float = 1, lastDy: Float = 0
    private var nextSegment = 1
    private(set) var finished = false

    init(_ step: Float) { self.step = step }

    var finalLength: Float { acc }

    private func segment(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float, _ raw: Int) {
        let segLen = hypotf(bx - ax, by - ay)
        let end = acc + segLen
        while true {
            let target = Float(k) * step
            if end < target { break }
            let u = segLen == 0 ? 0 : min(max((target - acc) / segLen, 0), 1)
            let sx = ax + (bx - ax) * u, sy = ay + (by - ay) * u
            if segLen > 0 { lastDx = (bx - ax) / segLen; lastDy = (by - ay) / segLen }
            else if k == 0 { lastDx = 1; lastDy = 0 }
            samples.add(sx, sy, lastDx, lastDy, target, raw, k)
            k += 1
        }
        acc = end
        vx = bx; vy = by
    }

    func advance(_ px: [Float], _ py: [Float], _ count: Int) {
        if finished || count < 3 { return }
        if !started { vx = px[0]; vy = py[0]; started = true }
        while nextSegment <= count - 1 {
            let i = nextSegment
            let sx = vx, sy = vy
            let cx = px[i - 1], cy = py[i - 1]
            let ex = (px[i - 1] + px[i]) / 2, ey = (py[i - 1] + py[i]) / 2
            let len = hypotf(cx - sx, cy - sy) + hypotf(ex - cx, ey - cy)
            let n = min(max(1, Int((len / max(0.5, step * 0.5)).rounded(.up))), 64)
            for q in 1...n {
                let u = Float(q) / Float(n)
                let a = (1 - u) * (1 - u), b = 2 * (1 - u) * u, c = u * u
                segment(vx, vy, a * sx + b * cx + c * ex, a * sy + b * cy + c * ey, i)
            }
            nextSegment += 1
        }
    }

    func finish(_ px: [Float], _ py: [Float], _ count: Int) {
        if finished { return }
        if count >= 3 { advance(px, py, count) }
        finished = true
        if count == 0 { return }
        if count == 1 { samples.add(px[0], py[0], 1, 0, 0, 0, 0); return }
        if count == 2 {
            started = true; vx = px[0]; vy = py[0]
            segment(px[0], py[0], px[1], py[1], 1)
        } else {
            segment(vx, vy, px[count - 1], py[count - 1], count - 1)
        }
        let total = acc
        if total <= 0 {
            samples.clear()
            samples.add(px[0], py[0], 1, 0, 0, 0, 0)
            return
        }
        let n = max(1, Int(total / step) + 1)
        if samples.size > n { samples.truncate(n) }
        while samples.size < n {
            samples.add(px[count - 1], py[count - 1], lastDx, lastDy, total, count - 1, samples.size)
        }
    }
}

/// Aynı boyuttaki noktaları birlikte çizmek için boyut (ve koyuluk) kovaları (Android `DabBuckets`). Her nokta ayrı
/// çizilir (üst üste binen damgalarda boya birikir).
struct DabBuckets {
    static let BUCKETS = 9
    private static let MIN_F: Float = 0.15
    private static let MAX_F: Float = 1.25

    let base: Float
    private let alphaLevels: Int
    private var points: [[Float]]
    private(set) var isEmpty = true

    init(_ base: Float, alphaLevels: Int = 1) {
        self.base = base
        self.alphaLevels = alphaLevels
        points = Array(repeating: [], count: Self.BUCKETS * alphaLevels)
    }

    mutating func add(_ x: Float, _ y: Float, _ diameter: Float, alpha: Float = 1) {
        let f = (diameter / base - Self.MIN_F) / (Self.MAX_F - Self.MIN_F)
        let i = min(max(Bitmaps.roundToInt(f * Float(Self.BUCKETS - 1)), 0), Self.BUCKETS - 1)
        let a = alphaLevels == 1 ? 0 : min(max(Bitmaps.roundToInt(min(max(alpha, 0), 1) * Float(alphaLevels) - 0.5), 0), alphaLevels - 1)
        points[a * Self.BUCKETS + i].append(x)
        points[a * Self.BUCKETS + i].append(y)
        isEmpty = false
    }

    /// Kovaları gezer: (nokta dizisi, çap, 0–255 koyuluk).
    func forEach(baseAlpha: Int, _ body: ([Float], Float, Int) -> Void) {
        for a in 0..<alphaLevels {
            let alpha = alphaLevels > 1 ? min(max(Bitmaps.roundToInt(Float(baseAlpha) * Float(a + 1) / Float(alphaLevels)), 1), 255) : baseAlpha
            for i in 0..<Self.BUCKETS {
                let bucket = points[a * Self.BUCKETS + i]
                if bucket.isEmpty { continue }
                let size = base * (Self.MIN_F + (Self.MAX_F - Self.MIN_F) * Float(i) / Float(Self.BUCKETS - 1))
                body(bucket, max(0.6, size), alpha)
            }
        }
    }
}

/// Sınır kutusu biriktirici.
struct BoundsBuilder {
    var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude
    var r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
    var isEmpty: Bool { l > r }
    mutating func add(_ x: Float, _ y: Float) { if x < l { l = x }; if x > r { r = x }; if y < t { t = y }; if y > b { b = y } }
    func rect(_ pad: Float) -> CGRect {
        isEmpty ? .null : CGRect(x: CGFloat(l - pad), y: CGFloat(t - pad), width: CGFloat(r - l + 2 * pad), height: CGFloat(b - t + 2 * pad))
    }
}
