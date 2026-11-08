import Foundation

/// v45 kesin kesirli sayı (sadeleşmiş; payda pozitif) (Android `Rational`).
struct Rational: Equatable, Hashable, Comparable, CustomStringConvertible {
    let num: BigInt
    let den: BigInt

    private init(raw n: BigInt, _ d: BigInt) { num = n; den = d }

    static let ZERO = Rational(raw: .zero, .one)
    static let ONE = Rational(raw: .one, .one)

    static func of(_ n: BigInt, _ d: BigInt = .one) throws -> Rational {
        if d.isZero { throw MathException(.DIVISION_BY_ZERO) }
        var a = n, b = d
        if b.signum < 0 { a = -a; b = -b }
        let g = a.gcd(b)
        if g > .one { a = a / g; b = b / g }
        return Rational(raw: a, b)
    }

    /// Sıfır olmayan paydalı kesir (sabitler için).
    static func of(_ n: Int, _ d: Int = 1) -> Rational { try! of(BigInt(n), BigInt(d)) }

    /// Ondalık yazımdan kesin değer ("2.5" → 5/2).
    static func parse(_ text: String) throws -> Rational {
        var t = text.trimmingCharacters(in: .whitespaces)
        var exp = 0
        if let e = t.firstIndex(where: { $0 == "e" || $0 == "E" }) {
            exp = Int(t[t.index(after: e)...].replacingOccurrences(of: "+", with: "")) ?? 0
            t = String(t[..<e])
        }
        var negative = false
        if t.hasPrefix("-") { negative = true; t.removeFirst() } else if t.hasPrefix("+") { t.removeFirst() }
        let parts = t.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count <= 2 else { throw MathException(.UNEXPECTED, -1, text) }
        let digits = String(parts[0]) + (parts.count > 1 ? String(parts[1]) : "")
        let scale = (parts.count > 1 ? parts[1].count : 0) - exp
        guard !digits.isEmpty, var n = BigInt(digits) else { throw MathException(.UNEXPECTED, -1, text) }
        if negative { n = -n }
        if scale <= 0 { return try of(n * BigInt.ten.pow(-scale)) }
        return try of(n, BigInt.ten.pow(scale))
    }

    var isInteger: Bool { den == .one }
    var signum: Int { num.signum }

    static func + (a: Rational, b: Rational) -> Rational { try! of(a.num * b.den + b.num * a.den, a.den * b.den) }
    static func - (a: Rational, b: Rational) -> Rational { try! of(a.num * b.den - b.num * a.den, a.den * b.den) }
    static func * (a: Rational, b: Rational) -> Rational { try! of(a.num * b.num, a.den * b.den) }
    static prefix func - (a: Rational) -> Rational { Rational(raw: -a.num, a.den) }

    func divided(by o: Rational) throws -> Rational {
        if o.num.isZero { throw MathException(.DIVISION_BY_ZERO) }
        return try Rational.of(num * o.den, den * o.num)
    }

    /// Tamsayı üs (büyük sonuçlarda nil: yaklaşık hesaba geçilir).
    func pow(_ e: Int) throws -> Rational? {
        if e == 0 { return .ONE }
        if abs(e) > 4096 { return nil }
        let bits = max(num.magnitude.bitLength, den.bitLength) * abs(e)
        if bits > 20000 { return nil }
        if e < 0 {
            if num.isZero { throw MathException(.DIVISION_BY_ZERO) }
            return try Rational.of(den.pow(-e), num.pow(-e))
        }
        return try Rational.of(num.pow(e), den.pow(e))
    }

    /// Tam kök: pay ve payda tam kuvvetse kesin, değilse nil.
    func root(_ n: Int) -> Rational? {
        if num.signum < 0 && n % 2 == 0 { return nil }
        let negative = num.signum < 0
        guard let a = Rational.intRoot(num.magnitude, n), let b = Rational.intRoot(den, n) else { return nil }
        return try? Rational.of(negative ? -a : a, b)
    }

    private static func intRoot(_ x: BigInt, _ n: Int) -> BigInt? {
        if x.isZero { return .zero }
        if x.bitLength > 4000 { return nil }
        let guess = Foundation.pow(x.toDouble, 1.0 / Double(n))
        if !guess.isFinite || guess > 9e15 { return nil }
        let g = BigInt(Int((guess + 0.5).rounded(.down)))
        for d in -2...2 {
            let c = g + BigInt(d)
            if c.signum >= 0 && c.pow(n) == x { return c }
        }
        return nil
    }

    func toDouble() -> Double {
        if num.isZero { return 0 }
        let nb = num.magnitude.bitLength, db = den.bitLength
        if nb < 53 && db < 53 { return num.toDouble / den.toDouble }
        // payı kaydırıp 64 bitlik bölüm al
        let shift = max(0, 64 - (nb - db))
        let q = (num.magnitude * BigInt(2).pow(shift)) / den
        let v = q.toDouble * Foundation.pow(2.0, Double(-shift))
        return num.signum < 0 ? -v : v
    }

    /// Sonlu ondalık açılımı var mı (payda yalnız 2 ve 5 çarpanlı) ve en çok [maxDigits] basamak mı.
    func terminatingDecimal(_ maxDigits: Int = 6) -> String? {
        var d = den
        var twos = 0, fives = 0
        let two = BigInt(2), five = BigInt(5)
        while d.mod(two).isZero { d = d / two; twos += 1 }
        while d.mod(five).isZero { d = d / five; fives += 1 }
        if d != .one { return nil }
        let digits = max(twos, fives)
        if digits > maxDigits { return nil }
        let scaled = (num * BigInt.ten.pow(digits)) / den
        var s = scaled.magnitude.description
        if digits > 0 {
            if s.count <= digits { s = String(repeating: "0", count: digits - s.count + 1) + s }
            s.insert(".", at: s.index(s.endIndex, offsetBy: -digits))
            while s.hasSuffix("0") { s.removeLast() }
            if s.hasSuffix(".") { s.removeLast() }
        }
        return (num.signum < 0 ? "-" : "") + s
    }

    static func < (a: Rational, b: Rational) -> Bool { a.num * b.den < b.num * a.den }
    func compare(_ o: Rational) -> Int { self < o ? -1 : (o < self ? 1 : 0) }
    var description: String { isInteger ? num.description : "\(num)/\(den)" }
}

/// Hesap sonucu: kesin kesir ya da yaklaşık ondalık.
enum Value: Equatable {
    case exact(Rational)
    /// [degrees]: RAD modunda derece işaretiyle yazılmış kesin açı.
    case approx(Double, degrees: Rational? = nil)

    func toDouble() -> Double {
        switch self { case let .exact(q): return q.toDouble(); case let .approx(d, _): return d }
    }
}

/// v45 kesin hesap (Android `ExactEval`).
enum ExactEval {
    static func eval(_ n: MNode, _ vars: [String: Value] = [:], _ mode: AngleMode = .RAD) throws -> Value {
        let v = try ev(n, vars, mode)
        if case let .approx(d, _) = v, !d.isFinite {
            if d.isNaN { throw MathException(.DOMAIN) }
            throw MathException(.OVERFLOW)
        }
        return v
    }

    private static func approx(_ n: MNode, _ vars: [String: Value], _ mode: AngleMode) throws -> Double {
        try MathExpr.evalStrict(n, vars.mapValues { $0.toDouble() }, mode)
    }

    private static func ev(_ n: MNode, _ vars: [String: Value], _ mode: AngleMode) throws -> Value {
        switch n {
        case let .num(t): return .exact(try Rational.parse(t))
        case let .const(name): return .approx(name == "pi" ? .pi : M_E)
        case let .variable(name):
            guard let v = vars[name] else { throw MathException(.UNKNOWN_NAME, -1, name) }
            return v
        case let .neg(a):
            switch try ev(a, vars, mode) {
            case let .exact(q): return .exact(-q)
            case let .approx(d, _): return .approx(-d)
            }
        case let .bin(op, an, bn, _):
            let a = try ev(an, vars, mode), b = try ev(bn, vars, mode)
            if op == "/" && b.toDouble() == 0 {
                var approxNonZero = false
                if case let .approx(d, _) = b, d != 0 { approxNonZero = true }
                if !approxNonZero { throw MathException(.DIVISION_BY_ZERO) }
            }
            if case let .exact(x) = a, case let .exact(y) = b {
                switch op {
                case "+": return .exact(x + y)
                case "-": return .exact(x - y)
                case "*": return .exact(x * y)
                default: return .exact(try x.divided(by: y))
                }
            }
            let x = a.toDouble(), y = b.toDouble()
            switch op {
            case "+": return .approx(x + y)
            case "-": return .approx(x - y)
            case "*": return .approx(x * y)
            default: return .approx(x / y)
            }
        case let .pow(a, b): return try pow(try ev(a, vars, mode), try ev(b, vars, mode))
        case let .fact(an):
            let a = try ev(an, vars, mode)
            if case let .exact(q) = a, q.isInteger, q.signum >= 0, q.num.bitLength < 12 {
                var r = BigInt.one, k = BigInt(2)
                while k <= q.num { r = r * k; k = k + .one }
                return .exact(try Rational.of(r))
            }
            let v = MathExpr.factorial(a.toDouble())
            if v.isNaN { throw MathException(.DOMAIN, -1, "!") }
            return .approx(v)
        case let .deg(an):
            let a = try ev(an, vars, mode)
            if mode == .DEG { return a }
            switch a {
            case let .exact(q): return .approx(q.toDouble() * .pi / 180, degrees: q)
            case let .approx(d, _): return .approx(d * .pi / 180)
            }
        case let .call(fn, args): return try call(fn, args, vars, mode)
        }
    }

    private static func pow(_ a: Value, _ b: Value) throws -> Value {
        if case let .exact(x) = a, case let .exact(y) = b {
            if x.signum == 0 && y.signum < 0 { throw MathException(.DIVISION_BY_ZERO) }
            if y.isInteger && y.num.bitLength < 31 {
                if let r = try x.pow(y.num.intValue) { return .exact(r) }
            } else if !y.isInteger && y.den.bitLength < 8 && y.num.bitLength < 16 {
                if let r = x.root(y.den.intValue), let p = try r.pow(y.num.intValue) { return .exact(p) }
                if x.signum < 0 && y.den.intValue % 2 == 0 { throw MathException(.DOMAIN, -1, "^") }
            }
        }
        let v = MathExpr.powReal(a.toDouble(), b.toDouble())
        if v.isNaN { throw MathException(.DOMAIN, -1, "^") }
        return .approx(v)
    }

    private static let HALF = Rational.of(1, 2)

    private static func call(_ fn: String, _ args: [MNode], _ vars: [String: Value], _ mode: AngleMode) throws -> Value {
        if fn == "min" || fn == "max" || fn == "logb" { return .approx(try approx(.call(fn, args), vars, mode)) }
        let a = try ev(args[0], vars, mode)
        var degrees: Rational? = nil
        if mode == .DEG, case let .exact(q) = a { degrees = q } else if case let .approx(_, dg) = a { degrees = dg }
        switch fn {
        case "sqrt":
            if a.toDouble() < 0 { throw MathException(.DOMAIN, -1, "sqrt") }
            if case let .exact(q) = a, let r = q.root(2) { return .exact(r) }
        case "cbrt":
            if case let .exact(q) = a, let r = q.root(3) { return .exact(r) }
        case "abs":
            if case let .exact(q) = a { return .exact(q.signum < 0 ? -q : q) }
        case "sign":
            let d = a.toDouble()
            return .exact(Rational.of(d > 0 ? 1 : d < 0 ? -1 : 0))
        case "floor", "ceil", "round":
            let d = a.toDouble()
            let v = fn == "floor" ? d.rounded(.down) : fn == "ceil" ? d.rounded(.up) : (d + 0.5).rounded(.down)
            guard v.isFinite, abs(v) < 9.2e18 else { throw MathException(.OVERFLOW) }
            return .exact(Rational.of(Int(v)))
        case "sin", "cos", "tan", "cot", "sec", "csc":
            if let degrees, let s = try special(fn, degrees) { return s }
        default: break
        }
        return .approx(try MathExpr.evalStrict(.call(fn, [.variable("_a")]), ["_a": a.toDouble()], mode))
    }

    /// Özel açılarda (30°'nin ve 45°'nin katları) rasyonel çıkan değerler.
    private static func special(_ fn: String, _ deg: Rational) throws -> Value? {
        if !deg.isInteger { return nil }
        let d = deg.num.mod(BigInt(360)).intValue
        let sinQ: Rational?
        switch d {
        case 0, 180: sinQ = .ZERO
        case 30, 150: sinQ = HALF
        case 210, 330: sinQ = -HALF
        case 90: sinQ = .ONE
        case 270: sinQ = -Rational.ONE
        default: sinQ = nil
        }
        let cosQ: Rational?
        switch d {
        case 90, 270: cosQ = .ZERO
        case 60, 300: cosQ = HALF
        case 120, 240: cosQ = -HALF
        case 0: cosQ = .ONE
        case 180: cosQ = -Rational.ONE
        default: cosQ = nil
        }
        func inv(_ q: Rational?, _ f: String) throws -> Value? {
            guard let q else { return nil }
            if q.signum == 0 { throw MathException(.DOMAIN, -1, f) }
            return .exact(try Rational.ONE.divided(by: q))
        }
        switch fn {
        case "sin": return sinQ.map { .exact($0) }
        case "cos": return cosQ.map { .exact($0) }
        case "tan":
            switch d {
            case 0, 180: return .exact(.ZERO)
            case 45, 225: return .exact(.ONE)
            case 135, 315: return .exact(-Rational.ONE)
            case 90, 270: throw MathException(.DOMAIN, -1, "tan")
            default: return nil
            }
        case "cot":
            switch d {
            case 90, 270: return .exact(.ZERO)
            case 45, 225: return .exact(.ONE)
            case 135, 315: return .exact(-Rational.ONE)
            case 0, 180: throw MathException(.DOMAIN, -1, "cot")
            default: return nil
            }
        case "sec": return try inv(cosQ, "sec")
        case "csc": return try inv(sinQ, "csc")
        default: return nil
        }
    }

    /// Değerin karşılaştırması (kesinse kesin).
    static func compare(_ a: Value, _ b: Value) -> Int {
        if case let .exact(x) = a, case let .exact(y) = b { return x.compare(y) }
        let x = a.toDouble(), y = b.toDouble()
        if abs(x - y) <= 1e-12 * max(1.0, abs(x), abs(y)) { return 0 }
        return x < y ? -1 : (x > y ? 1 : 0)
    }

    /// Yaklaşık değer tam sayıya / kısa ondalığa çok yakınsa kesine çevirir.
    static func tidy(_ v: Value) -> Value {
        guard case let .approx(d, _) = v else { return v }
        for den in [1, 2, 4, 5, 8, 10, 100, 1000] {
            let k = (d * Double(den)).rounded(.toNearestOrEven)
            if abs(k - d * Double(den)) < 1e-9 * max(1.0, abs(d * Double(den))) && abs(k) < 1e15 { return .exact(Rational.of(Int(k), den)) }
        }
        return v
    }
}
