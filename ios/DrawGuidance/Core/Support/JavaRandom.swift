import Foundation

/// Java `java.util.Random` (Android ile aynı rastgele diziler: pürüzlü balon, desenler…).
struct JavaRandom {
    private var seed: Int64
    init(_ s: Int64) { seed = (s ^ 0x5DEECE66D) & ((1 << 48) - 1) }
    mutating func next(_ bits: Int) -> Int32 {
        seed = (seed &* 0x5DEECE66D &+ 0xB) & ((1 << 48) - 1)
        return Int32(truncatingIfNeeded: seed >> (48 - Int64(bits)))
    }
    mutating func nextFloat() -> Float { Float(next(24)) / Float(1 << 24) }
    mutating func nextDouble() -> Double { Double((Int64(next(26)) << 27) + Int64(next(27))) * 0x1.0p-53 }
    mutating func nextInt() -> Int32 { next(32) }
    mutating func nextInt(_ bound: Int32) -> Int32 {
        var r = next(31)
        let m = bound - 1
        if bound & m == 0 { return Int32(truncatingIfNeeded: (Int64(bound) * Int64(r)) >> 31) }
        var u = r
        while true {
            r = u % bound
            if u &- r &+ m >= 0 { break }
            u = next(31)
        }
        return r
    }
}
