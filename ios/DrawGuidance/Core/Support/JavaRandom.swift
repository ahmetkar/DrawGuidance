import Foundation

/// Java `java.util.Random` (Android ile aynı rastgele diziler: pürüzlü balon, desenler…).
struct JavaRandom {
    private var seed: Int64
    private var nextNextGaussian = 0.0
    private var haveNextNextGaussian = false
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
    mutating func nextInt(_ bound: Int) -> Int { Int(nextInt(Int32(bound))) }
    mutating func nextBoolean() -> Bool { next(1) != 0 }
    mutating func nextGaussian() -> Double {
        if haveNextNextGaussian { haveNextNextGaussian = false; return nextNextGaussian }
        var v1 = 0.0, v2 = 0.0, s = 0.0
        repeat {
            v1 = 2 * nextDouble() - 1
            v2 = 2 * nextDouble() - 1
            s = v1 * v1 + v2 * v2
        } while s >= 1 || s == 0
        let multiplier = (-2 * log(s) / s).squareRoot()
        nextNextGaussian = v2 * multiplier
        haveNextNextGaussian = true
        return v1 * multiplier
    }
    /// `Collections.shuffle(list, rnd)`.
    mutating func shuffle<T>(_ a: inout [T]) {
        if a.count < 2 { return }
        for i in stride(from: a.count - 1, through: 1, by: -1) { a.swapAt(i, nextInt(i + 1)) }
    }
}

/// Paylaşılan `java.util.Random` (aynı ifadede birden çok çağrı için sınıf).
final class JRandom {
    var r: JavaRandom
    init(_ seed: Int64) { r = JavaRandom(seed) }
    func nextFloat() -> Float { r.nextFloat() }
    func nextDouble() -> Double { r.nextDouble() }
    func nextInt(_ n: Int) -> Int { r.nextInt(n) }
    func nextBoolean() -> Bool { r.nextBoolean() }
    func nextGaussian() -> Double { r.nextGaussian() }
    func shuffle<T>(_ a: inout [T]) { r.shuffle(&a) }
    func shuffled<T>(_ a: [T]) -> [T] { var b = a; r.shuffle(&b); return b }
}
