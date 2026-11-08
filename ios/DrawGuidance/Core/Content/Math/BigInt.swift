import Foundation

/// Küçük, işaretli, keyfi duyarlıklı tamsayı (Java `BigInteger`'ın kesin kesir hesabı için gereken kısmı).
/// Büyüklük 32 bitlik parçalarla, küçükten büyüğe tutulur (sonda sıfır parça yok; sıfırın işareti yok).
struct BigInt: Equatable, Hashable, Comparable, CustomStringConvertible {
    private(set) var neg: Bool
    private(set) var mag: [UInt32]

    static let zero = BigInt(0), one = BigInt(1), ten = BigInt(10)

    init(_ v: Int) {
        neg = v < 0
        var m = v.magnitude
        var out: [UInt32] = []
        while m > 0 { out.append(UInt32(truncatingIfNeeded: m)); m >>= 32 }
        mag = out
    }

    private init(neg: Bool, mag: [UInt32]) {
        var m = mag
        while let l = m.last, l == 0 { m.removeLast() }
        self.mag = m
        self.neg = m.isEmpty ? false : neg
    }

    /// Ondalık yazım ("-123"); geçersizse nil.
    init?(_ text: String) {
        var chars = Substring(text)
        var negative = false
        if chars.first == "-" { negative = true; chars = chars.dropFirst() } else if chars.first == "+" { chars = chars.dropFirst() }
        if chars.isEmpty { return nil }
        var r: [UInt32] = []
        for c in chars {
            guard let d = c.wholeNumberValue, c.isASCII else { return nil }
            BigInt.mulSmallAdd(&r, 10, UInt32(d))
        }
        self.init(neg: negative, mag: r)
    }

    var signum: Int { mag.isEmpty ? 0 : (neg ? -1 : 1) }
    var isZero: Bool { mag.isEmpty }
    var magnitude: BigInt { BigInt(neg: false, mag: mag) }

    /// Java `bitLength` (negatifte ikiye tümleyen gösterimdeki işaretsiz bit sayısı).
    var bitLength: Int {
        if !neg { return BigInt.bits(mag) }
        return BigInt.bits(BigInt.subMag(mag, [1]))
    }

    private static func bits(_ m: [UInt32]) -> Int {
        guard let top = m.last else { return 0 }
        return (m.count - 1) * 32 + (32 - top.leadingZeroBitCount)
    }

    /// 64 bite sığıyorsa değeri.
    var int64: Int64? {
        if mag.count > 2 { return nil }
        var u: UInt64 = 0
        for (k, l) in mag.enumerated() { u |= UInt64(l) << (32 * UInt64(k)) }
        if neg { return u <= UInt64(Int64.max) + 1 ? Int64(truncatingIfNeeded: 0 &- u) : nil }
        return u <= UInt64(Int64.max) ? Int64(u) : nil
    }

    /// Java `intValue` gibi düşük 32 bit (işaretli).
    var intValue: Int {
        let low = Int64(mag.first ?? 0)
        return Int(Int32(truncatingIfNeeded: neg ? -low : low))
    }

    var toDouble: Double {
        if mag.isEmpty { return 0 }
        let b = BigInt.bits(mag)
        if b > 1100 { return neg ? -.infinity : .infinity }
        // üstteki 64 biti al, geri kalanı üs olarak
        var d = 0.0
        for l in mag.reversed() { d = d * 4_294_967_296.0 + Double(l) }
        return neg ? -d : d
    }

    var description: String {
        if mag.isEmpty { return "0" }
        var parts: [String] = []
        var m = mag
        while !m.isEmpty {
            let r = BigInt.divSmall(&m, 1_000_000_000)
            parts.append(String(r))
        }
        var s = neg ? "-" : ""
        s += parts.last!
        for p in parts.dropLast().reversed() { s += String(repeating: "0", count: 9 - p.count) + p }
        return s
    }

    // MARK: Büyüklük işlemleri

    private static func cmpMag(_ a: [UInt32], _ b: [UInt32]) -> Int {
        if a.count != b.count { return a.count < b.count ? -1 : 1 }
        var k = a.count - 1
        while k >= 0 {
            if a[k] != b[k] { return a[k] < b[k] ? -1 : 1 }
            k -= 1
        }
        return 0
    }

    private static func addMag(_ a: [UInt32], _ b: [UInt32]) -> [UInt32] {
        var out = [UInt32](repeating: 0, count: max(a.count, b.count) + 1)
        var carry: UInt64 = 0
        for k in 0..<(out.count - 1) {
            let s = UInt64(k < a.count ? a[k] : 0) + UInt64(k < b.count ? b[k] : 0) + carry
            out[k] = UInt32(truncatingIfNeeded: s)
            carry = s >> 32
        }
        out[out.count - 1] = UInt32(carry)
        return out
    }

    /// a − b (|a| ≥ |b|).
    private static func subMag(_ a: [UInt32], _ b: [UInt32]) -> [UInt32] {
        var out = a
        var borrow: Int64 = 0
        for k in 0..<a.count {
            var d = Int64(a[k]) - Int64(k < b.count ? b[k] : 0) - borrow
            if d < 0 { d += 4_294_967_296; borrow = 1 } else { borrow = 0 }
            out[k] = UInt32(d)
        }
        while let l = out.last, l == 0 { out.removeLast() }
        return out
    }

    private static func mulMag(_ a: [UInt32], _ b: [UInt32]) -> [UInt32] {
        if a.isEmpty || b.isEmpty { return [] }
        var out = [UInt32](repeating: 0, count: a.count + b.count)
        for i in 0..<a.count {
            var carry: UInt64 = 0
            let ai = UInt64(a[i])
            if ai == 0 { continue }
            for j in 0..<b.count {
                let t = ai * UInt64(b[j]) + UInt64(out[i + j]) + carry
                out[i + j] = UInt32(truncatingIfNeeded: t)
                carry = t >> 32
            }
            var k = i + b.count
            while carry > 0 {
                let t = UInt64(out[k]) + carry
                out[k] = UInt32(truncatingIfNeeded: t)
                carry = t >> 32
                k += 1
            }
        }
        while let l = out.last, l == 0 { out.removeLast() }
        return out
    }

    private static func mulSmallAdd(_ m: inout [UInt32], _ mul: UInt32, _ add: UInt32) {
        var carry = UInt64(add)
        for k in 0..<m.count {
            let t = UInt64(m[k]) * UInt64(mul) + carry
            m[k] = UInt32(truncatingIfNeeded: t)
            carry = t >> 32
        }
        if carry > 0 { m.append(UInt32(carry)) }
    }

    /// m ← m / d; kalanı döner.
    private static func divSmall(_ m: inout [UInt32], _ d: UInt32) -> UInt32 {
        var rem: UInt64 = 0
        var k = m.count - 1
        while k >= 0 {
            let cur = (rem << 32) | UInt64(m[k])
            m[k] = UInt32(cur / UInt64(d))
            rem = cur % UInt64(d)
            k -= 1
        }
        while let l = m.last, l == 0 { m.removeLast() }
        return UInt32(rem)
    }

    private static func shiftLeft(_ m: [UInt32], _ n: Int) -> [UInt32] {
        if m.isEmpty || n == 0 { return m }
        let words = n / 32, bitsN = n % 32
        var out = [UInt32](repeating: 0, count: words) + m
        if bitsN > 0 {
            var carry: UInt32 = 0
            for k in words..<out.count {
                let v = out[k]
                out[k] = (v << UInt32(bitsN)) | carry
                carry = v >> UInt32(32 - bitsN)
            }
            if carry > 0 { out.append(carry) }
        }
        return out
    }

    private static func shiftRight(_ m: [UInt32], _ n: Int) -> [UInt32] {
        let words = n / 32, bitsN = n % 32
        if words >= m.count { return [] }
        var out = Array(m[words...])
        if bitsN > 0 {
            for k in 0..<out.count {
                let hi: UInt32 = k + 1 < out.count ? out[k + 1] << UInt32(32 - bitsN) : 0
                out[k] = (out[k] >> UInt32(bitsN)) | hi
            }
        }
        while let l = out.last, l == 0 { out.removeLast() }
        return out
    }

    /// |a| / |b| ve kalanı (bit bit uzun bölme; tek parçalı bölende hızlı yol).
    private static func divModMag(_ a: [UInt32], _ b: [UInt32]) -> ([UInt32], [UInt32]) {
        if cmpMag(a, b) < 0 { return ([], a) }
        if b.count == 1 {
            var q = a
            let r = divSmall(&q, b[0])
            return (q, r == 0 ? [] : [r])
        }
        let shift = bits(a) - bits(b)
        var rem = a
        var q = [UInt32](repeating: 0, count: shift / 32 + 1)
        var d = shiftLeft(b, shift)
        var s = shift
        while s >= 0 {
            if cmpMag(rem, d) >= 0 {
                rem = subMag(rem, d)
                q[s / 32] |= UInt32(1) << UInt32(s % 32)
            }
            d = shiftRight(d, 1)
            s -= 1
        }
        while let l = q.last, l == 0 { q.removeLast() }
        return (q, rem)
    }

    // MARK: İşleçler

    static func + (a: BigInt, b: BigInt) -> BigInt {
        if a.neg == b.neg { return BigInt(neg: a.neg, mag: addMag(a.mag, b.mag)) }
        let c = cmpMag(a.mag, b.mag)
        if c == 0 { return .zero }
        return c > 0 ? BigInt(neg: a.neg, mag: subMag(a.mag, b.mag)) : BigInt(neg: b.neg, mag: subMag(b.mag, a.mag))
    }

    static prefix func - (a: BigInt) -> BigInt { BigInt(neg: !a.neg, mag: a.mag) }
    static func - (a: BigInt, b: BigInt) -> BigInt { a + (-b) }
    static func * (a: BigInt, b: BigInt) -> BigInt { BigInt(neg: a.neg != b.neg, mag: mulMag(a.mag, b.mag)) }

    /// Java `divideAndRemainder`: bölüm sıfıra doğru kesilir, kalan bölünenin işaretini taşır.
    func quotientAndRemainder(_ d: BigInt) -> (BigInt, BigInt) {
        precondition(!d.isZero, "sıfıra bölme")
        let (q, r) = BigInt.divModMag(mag, d.mag)
        return (BigInt(neg: neg != d.neg, mag: q), BigInt(neg: neg, mag: r))
    }

    static func / (a: BigInt, b: BigInt) -> BigInt { a.quotientAndRemainder(b).0 }

    /// Java `mod`: sonuç her zaman ≥ 0 (m > 0).
    func mod(_ m: BigInt) -> BigInt {
        let r = quotientAndRemainder(m).1
        return r.neg ? r + m.magnitude : r
    }

    func pow(_ e: Int) -> BigInt {
        var result = BigInt.one, base = self, k = e
        while k > 0 {
            if k & 1 == 1 { result = result * base }
            k >>= 1
            if k > 0 { base = base * base }
        }
        return result
    }

    /// İkili (Stein) en büyük ortak bölen; her zaman ≥ 0.
    func gcd(_ o: BigInt) -> BigInt {
        var a = mag, b = o.mag
        if a.isEmpty { return BigInt(neg: false, mag: b) }
        if b.isEmpty { return BigInt(neg: false, mag: a) }
        func tz(_ m: [UInt32]) -> Int {
            var k = 0
            while m[k] == 0 { k += 1 }
            return k * 32 + m[k].trailingZeroBitCount
        }
        let za = tz(a), zb = tz(b)
        let shift = min(za, zb)
        a = BigInt.shiftRight(a, za)
        b = BigInt.shiftRight(b, zb)
        while true {
            let c = BigInt.cmpMag(a, b)
            if c == 0 { break }
            if c < 0 { swap(&a, &b) }
            a = BigInt.subMag(a, b)
            a = BigInt.shiftRight(a, tz(a))
        }
        return BigInt(neg: false, mag: BigInt.shiftLeft(a, shift))
    }

    static func < (a: BigInt, b: BigInt) -> Bool {
        if a.neg != b.neg { return a.neg }
        let c = cmpMag(a.mag, b.mag)
        return a.neg ? c > 0 : c < 0
    }
}
