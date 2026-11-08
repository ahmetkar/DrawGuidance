import Foundation

/// Kotlin `kotlin.random.Random(seed)` (XorWow): Android ile aynı tohumdan aynı dizi (çizgi roman çizgileri, karakterler…).
struct KotlinRandom {
    private var x: Int32, y: Int32, z: Int32, w: Int32, v: Int32, addend: Int32

    init(_ seed: Int) {
        let s1 = Int32(truncatingIfNeeded: seed)
        let s2 = s1 >> 31
        x = s1; y = s2; z = 0; w = 0; v = ~s1
        addend = (s1 << 10) ^ Int32(bitPattern: UInt32(bitPattern: s2) >> 4)
        for _ in 0..<64 { _ = nextInt() }
    }

    mutating func nextInt() -> Int32 {
        var t = x
        t = t ^ Int32(bitPattern: UInt32(bitPattern: t) >> 2)
        x = y; y = z; z = w
        let v0 = v
        w = v0
        t = (t ^ (t << 1)) ^ v0 ^ (v0 << 4)
        v = t
        addend = addend &+ 362_437
        return t &+ addend
    }

    mutating func nextBits(_ bitCount: Int) -> Int32 {
        let n = nextInt()
        // takeUpperBits: (this ushr (32 - bitCount)) and (-bitCount).shr(31)
        let shifted = Int32(bitPattern: UInt32(bitPattern: n) >> UInt32(32 - bitCount))
        return shifted & (Int32(-bitCount) >> 31)
    }

    mutating func nextFloat() -> Float { Float(nextBits(24)) / Float(1 << 24) }

    mutating func nextDouble() -> Double {
        let hi = Int64(nextBits(26)), lo = Int64(nextBits(27))
        return Double((hi << 27) + lo) / Double(Int64(1) << 53)
    }

    mutating func nextBoolean() -> Bool { nextBits(1) != 0 }

    /// 0 ..< [until].
    mutating func nextInt(_ until: Int) -> Int { nextInt(0, until) }

    /// [from] ..< [until] (Kotlin `nextInt(from, until)`).
    mutating func nextInt(_ from: Int, _ until: Int) -> Int {
        let n = Int32(truncatingIfNeeded: until - from)
        if n > 0 || n == Int32.min {
            let r: Int32
            if n & -n == n {
                r = nextBits(31 - n.leadingZeroBitCount)
            } else {
                var bits: Int32, value: Int32
                repeat {
                    bits = Int32(bitPattern: UInt32(bitPattern: nextInt()) >> 1)
                    value = bits % n
                } while bits &- value &+ (n &- 1) < 0
                r = value
            }
            return from + Int(r)
        }
        while true {
            let r = Int(nextInt())
            if r >= from && r < until { return r }
        }
    }

    /// Kotlin `nextFloat` aralıklı biçimi yok; `nextDouble(from, until)`.
    mutating func nextDouble(_ from: Double, _ until: Double) -> Double { from + (until - from) * nextDouble() }
}

/// Kotlin `kotlin.math.sin(Float)` (Double'da hesaplayıp Float'a çevirir).
@inline(__always) func kSin(_ x: Float) -> Float { Float(sin(Double(x))) }
@inline(__always) func kCos(_ x: Float) -> Float { Float(cos(Double(x))) }
@inline(__always) func kHypot(_ a: Float, _ b: Float) -> Float { Float(hypot(Double(a), Double(b))) }
