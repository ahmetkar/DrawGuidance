import Foundation

/*
 * v45 genel matematik ifadesi ayrıştırıcısı (Android `MathExpr`): grafikler, parametreli fen şekilleri, formül hesabı.
 * İşlem önceliği, örtük çarpım, parantezsiz fonksiyonlar, sin²x, log_b(x), sabitler, derece işareti, faktöriyel,
 * Unicode yazımlar ve Türkçe ondalık virgül; bağıntı zincirleri (-1 ≤ x < 4).
 */

/// Ayrıştırma ya da hesaplama hatası; [pos] kaynak metindeki konum (bilinmiyorsa -1).
struct MathError: Equatable {
    enum Code: String { case EMPTY, UNEXPECTED, PARENTHESIS, MISSING_OPERAND, UNKNOWN_NAME, ARGUMENTS, DIVISION_BY_ZERO, DOMAIN, RELATION, OVERFLOW }
    let code: Code
    var pos: Int = -1
    var detail: String = ""
}

struct MathException: Error, CustomStringConvertible {
    let error: MathError
    init(_ e: MathError) { error = e }
    init(_ code: MathError.Code, _ pos: Int = -1, _ detail: String = "") { error = MathError(code: code, pos: pos, detail: detail) }
    var description: String { "\(error.code.rawValue) @\(error.pos) \(error.detail)" }
}

/// Bağıntı işaretleri.
enum Rel: CaseIterable {
    case EQ, NE, LT, GT, LE, GE

    var symbol: String {
        switch self { case .EQ: return "="; case .NE: return "≠"; case .LT: return "<"; case .GT: return ">"; case .LE: return "≤"; case .GE: return "≥" }
    }
    var tex: String {
        switch self { case .EQ: return "="; case .NE: return "\\neq"; case .LT: return "<"; case .GT: return ">"; case .LE: return "\\le"; case .GE: return "\\ge" }
    }

    func holds(_ a: Double, _ b: Double, eps: Double = 1e-12) -> Bool {
        let eq = abs(a - b) <= eps * max(1.0, abs(a), abs(b))
        switch self {
        case .EQ: return eq
        case .NE: return !eq
        case .LT: return a < b && !eq
        case .GT: return a > b && !eq
        case .LE: return a < b || eq
        case .GE: return a > b || eq
        }
    }

    func holdsCmp(_ c: Int) -> Bool {
        switch self {
        case .EQ: return c == 0
        case .NE: return c != 0
        case .LT: return c < 0
        case .GT: return c > 0
        case .LE: return c <= 0
        case .GE: return c >= 0
        }
    }

    func flipped() -> Rel {
        switch self { case .LT: return .GT; case .GT: return .LT; case .LE: return .GE; case .GE: return .LE; default: return self }
    }
}

/// İfade ağacı.
indirect enum MNode: Equatable {
    /// Ondalık sayı (kaynaktaki yazımıyla; kesin hesap için).
    case num(String)
    case const(String)
    case variable(String)
    case neg(MNode)
    case bin(Character, MNode, MNode, implicit: Bool)
    case pow(MNode, MNode)
    case call(String, [MNode])
    case fact(MNode)
    /// Derece işareti: a° (radyana çevrilir).
    case deg(MNode)

    static func numValue(_ text: String) -> Double { Double(text) ?? .nan }

    /// Ağaçtaki değişken adları (ilk görünüş sırasıyla).
    func variables() -> [String] {
        var out: [String] = []
        func walk(_ n: MNode) {
            switch n {
            case let .variable(v): if !out.contains(v) { out.append(v) }
            case let .neg(a), let .fact(a), let .deg(a): walk(a)
            case let .bin(_, a, b, _), let .pow(a, b): walk(a); walk(b)
            case let .call(_, args): args.forEach(walk)
            case .num, .const: break
            }
        }
        walk(self)
        return out
    }

    var isVar: Bool { if case .variable = self { return true }; return false }
}

/// Bağıntı zinciri: sides[0] ops[0] sides[1] … (bağıntısız ifadede ops boş).
struct MRelation: Equatable {
    let sides: [MNode]
    let ops: [Rel]
    var isRelation: Bool { !ops.isEmpty }
}

/// Açı birimi.
enum AngleMode { case RAD, DEG }

enum MathExpr {
    static let FUNCTIONS: Set<String> = [
        "sin", "cos", "tan", "tg", "cot", "cotg", "ctg", "sec", "csc", "asin", "acos", "atan", "arcsin", "arccos", "arctan",
        "sinh", "cosh", "tanh", "sqrt", "cbrt", "abs", "exp", "ln", "log", "lg", "log2", "floor", "ceil", "round", "sign", "sgn",
        "min", "max",
    ]
    static let CONSTANTS: Set<String> = ["pi", "e"]
    private static let TRIG: Set<String> = ["sin", "cos", "tan", "tg", "cot", "cotg", "ctg", "sec", "csc"]
    private static let INV_TRIG: Set<String> = ["asin", "acos", "atan", "arcsin", "arccos", "arctan"]
    private static let TWO_ARGS: Set<String> = ["min", "max"]

    static func parse(_ text: String, names: Set<String> = []) throws -> MNode {
        let r = try parseRelation(text, names: names)
        if r.isRelation { throw MathException(.RELATION) }
        return r.sides[0]
    }

    static func tryParse(_ text: String, names: Set<String> = []) -> (MNode?, MathError?) {
        do { return (try parse(text, names: names), nil) } catch let e as MathException { return (nil, e.error) } catch { return (nil, MathError(code: .UNEXPECTED)) }
    }

    static func parseRelation(_ text: String, names: Set<String> = []) throws -> MRelation {
        let norm = Normalized.of(text)
        if norm.text.allSatisfy(\.isWhitespace) { throw MathException(.EMPTY, 0) }
        let p = Parser(norm, names)
        let r = try p.relation()
        try p.expectEnd()
        return r
    }

    static func tryParseRelation(_ text: String, names: Set<String> = []) -> (MRelation?, MathError?) {
        do { return (try parseRelation(text, names: names), nil) } catch let e as MathException { return (nil, e.error) } catch { return (nil, MathError(code: .UNEXPECTED)) }
    }

    // MARK: Değerlendirme

    /// Hesaplar; tanımsız noktada NaN. Bilinmeyen değişken hata fırlatır.
    static func eval(_ n: MNode, _ vars: [String: Double] = [:], _ mode: AngleMode = .RAD) throws -> Double {
        try Evaluator(vars: vars, mode: mode, strict: false).eval(n)
    }

    /// Denetimli hesap: sıfıra bölme, tanım dışı ve sonsuz sonuç hata fırlatır.
    static func evalStrict(_ n: MNode, _ vars: [String: Double] = [:], _ mode: AngleMode = .RAD) throws -> Double {
        try Evaluator(vars: vars, mode: mode, strict: true).eval(n)
    }

    /// Hızlı tekrarlı hesap için derlenmiş fonksiyon (değişkenler [order] sırasıyla dizide).
    static func compile(_ n: MNode, _ order: [String], _ mode: AngleMode = .RAD) throws -> ([Double]) -> Double {
        var idx: [String: Int] = [:]
        for (k, v) in order.enumerated() where idx[v] == nil { idx[v] = k }
        return try Compiler(idx: idx, mode: mode).c(n)
    }

    /// Negatif tabanda tek paydalı kesirli üsleri de hesaplayan kuvvet.
    static func powReal(_ b: Double, _ e: Double) -> Double {
        if b >= 0 || e == e.rounded(.down) || !e.isFinite { return Foundation.pow(b, e) }
        var q = 3
        while q <= 15 {
            let n = e * Double(q)
            let rn = n.rounded(.toNearestOrEven)
            if abs(n - rn) < 1e-9 {
                let m = Foundation.pow(-b, e)
                return Int64(rn) % 2 != 0 ? -m : m
            }
            q += 2
        }
        return .nan
    }

    static func factorial(_ v: Double) -> Double {
        if v < 0 || v != v.rounded(.down) { return .nan }
        if v > 170 { return .infinity }
        var r = 1.0, k = 2.0
        while k <= v { r *= k; k += 1 }
        return r
    }

    static func isTrig(_ fn: String) -> Bool { TRIG.contains(fn) }
    static func isInvTrig(_ fn: String) -> Bool { INV_TRIG.contains(fn) }

    /// Kotlin `round` (yarım çifte).
    private static func roundEven(_ x: Double) -> Double { x.rounded(.toNearestOrEven) }

    private static func nearMultiple(_ r: Double, _ of: Double) -> Bool {
        let k = r / of
        return abs(k - roundEven(k)) < 1e-12 && abs(r) < 1e6
    }

    private static func sinExact(_ r: Double) -> Double { nearMultiple(r, .pi) ? 0 : Foundation.sin(r) }
    private static func cosExact(_ r: Double) -> Double { nearMultiple(r - .pi / 2, .pi) ? 0 : Foundation.cos(r) }
    private static func tanExact(_ r: Double) -> Double {
        if nearMultiple(r, .pi) { return 0 }
        if nearMultiple(r - .pi / 2, .pi) { return .nan }
        return Foundation.tan(r)
    }

    private static func signD(_ a: Double) -> Double { a.isNaN ? .nan : a > 0 ? 1 : a < 0 ? -1 : a }

    /// Tek bağımsız değişkenli fonksiyon (açı biriminde); tanımsızsa NaN.
    static func apply(_ fn: String, _ a: Double, _ mode: AngleMode = .RAD) throws -> Double {
        let r = mode == .DEG && TRIG.contains(fn) ? a * .pi / 180 : a
        let v: Double
        switch fn {
        case "sin": v = sinExact(r)
        case "cos": v = cosExact(r)
        case "tan", "tg": v = tanExact(r)
        case "cot", "cotg", "ctg": let t = tanExact(r); v = t == 0 ? .nan : 1 / t
        case "sec": let c = cosExact(r); v = c == 0 ? .nan : 1 / c
        case "csc": let s = sinExact(r); v = s == 0 ? .nan : 1 / s
        case "asin", "arcsin": v = Foundation.asin(a)
        case "acos", "arccos": v = Foundation.acos(a)
        case "atan", "arctan": v = Foundation.atan(a)
        case "sinh": v = Foundation.sinh(a)
        case "cosh": v = Foundation.cosh(a)
        case "tanh": v = Foundation.tanh(a)
        case "sqrt": v = a.squareRoot()
        case "cbrt": v = Foundation.cbrt(a)
        case "abs": v = abs(a)
        case "exp": v = Foundation.exp(a)
        case "ln": v = a == 0 ? .nan : Foundation.log(a)
        case "log", "lg": v = a == 0 ? .nan : Foundation.log10(a)
        case "log2": v = a == 0 ? .nan : Foundation.log2(a)
        case "floor": v = a.rounded(.down)
        case "ceil": v = a.rounded(.up)
        case "round": v = (a + 0.5).rounded(.down)
        case "sign", "sgn": v = signD(a)
        default: throw MathException(.UNKNOWN_NAME, -1, fn)
        }
        return mode == .DEG && INV_TRIG.contains(fn) ? v * 180 / .pi : v
    }

    /// `apply`'ın hata fırlatmayan biçimi (bilinen adlar için).
    static func applyOrNaN(_ fn: String, _ a: Double, _ mode: AngleMode) -> Double { (try? apply(fn, a, mode)) ?? .nan }

    private struct Evaluator {
        let vars: [String: Double]
        let mode: AngleMode
        let strict: Bool

        func eval(_ n: MNode) throws -> Double {
            let v = try raw(n)
            if strict && v.isInfinite { throw MathException(.OVERFLOW) }
            return v
        }

        private func raw(_ n: MNode) throws -> Double {
            switch n {
            case let .num(t): return MNode.numValue(t)
            case let .const(name): return name == "pi" ? .pi : M_E
            case let .variable(name):
                guard let v = vars[name] else { throw MathException(.UNKNOWN_NAME, -1, name) }
                return v
            case let .neg(a): return -(try eval(a))
            case let .bin(op, a0, b0, _):
                let a = try eval(a0), b = try eval(b0)
                switch op {
                case "+": return a + b
                case "-": return a - b
                case "*": return a * b
                default:
                    if strict && b == 0 { throw MathException(.DIVISION_BY_ZERO) }
                    return a / b
                }
            case let .pow(a0, b0):
                let a = try eval(a0), b = try eval(b0)
                if strict && a == 0 && b < 0 { throw MathException(.DIVISION_BY_ZERO) }
                let v = powReal(a, b)
                if strict && v.isNaN { throw MathException(.DOMAIN, -1, "^") }
                return v
            case let .fact(a0):
                let v = factorial(try eval(a0))
                if strict && v.isNaN { throw MathException(.DOMAIN, -1, "!") }
                return v
            case let .deg(a0):
                return try eval(a0) * .pi / 180 * (mode == .DEG ? 180 / .pi : 1)
            case let .call(fn, args):
                if TWO_ARGS.contains(fn) {
                    let xs = try args.map { try eval($0) }
                    return fn == "min" ? xs.min()! : xs.max()!
                }
                if fn == "logb" {
                    let base = try eval(args[0]), x = try eval(args[1])
                    if strict && (base <= 0 || base == 1 || x <= 0) { throw MathException(.DOMAIN, -1, "log") }
                    return Foundation.log(x) / Foundation.log(base)
                }
                let a = try eval(args[0])
                if strict { try checkDomain(fn, a) }
                let v = try apply(fn, a, mode)
                if strict && v.isNaN { throw MathException(.DOMAIN, -1, fn) }
                return v
            }
        }

        private func checkDomain(_ fn: String, _ a: Double) throws {
            let bad: Bool
            switch fn {
            case "sqrt": bad = a < 0
            case "ln", "log", "lg", "log2": bad = a <= 0
            case "asin", "arcsin", "acos", "arccos": bad = a < -1 || a > 1
            default: bad = false
            }
            if bad { throw MathException(.DOMAIN, -1, fn) }
            if ["tan", "tg", "sec", "cot", "cotg", "ctg", "csc"].contains(fn) {
                let r = mode == .DEG ? a * .pi / 180 : a
                let zero = ["tan", "tg", "sec"].contains(fn) ? nearMultiple(r - .pi / 2, .pi) : nearMultiple(r, .pi)
                if zero { throw MathException(.DOMAIN, -1, fn) }
            }
        }
    }

    private struct Compiler {
        let idx: [String: Int]
        let mode: AngleMode

        func c(_ n: MNode) throws -> ([Double]) -> Double {
            switch n {
            case let .num(t): let v = MNode.numValue(t); return { _ in v }
            case let .const(name): let v = name == "pi" ? Double.pi : M_E; return { _ in v }
            case let .variable(name):
                guard let k = idx[name] else { throw MathException(.UNKNOWN_NAME, -1, name) }
                return { a in a[k] }
            case let .neg(a): let f = try c(a); return { x in -f(x) }
            case let .bin(op, a, b, _):
                let f = try c(a), g = try c(b)
                switch op {
                case "+": return { x in f(x) + g(x) }
                case "-": return { x in f(x) - g(x) }
                case "*": return { x in f(x) * g(x) }
                default: return { x in f(x) / g(x) }
                }
            case let .pow(a, b): let f = try c(a), g = try c(b); return { x in powReal(f(x), g(x)) }
            case let .fact(a): let f = try c(a); return { x in factorial(f(x)) }
            case let .deg(a): let f = try c(a); let k = mode == .DEG ? 1.0 : Double.pi / 180; return { x in f(x) * k }
            case let .call(fn, args):
                let fs = try args.map { try c($0) }
                switch fn {
                case "min": return { x in fs.map { $0(x) }.min()! }
                case "max": return { x in fs.map { $0(x) }.max()! }
                case "logb": return { x in Foundation.log(fs[1](x)) / Foundation.log(fs[0](x)) }
                default:
                    let f = fs[0], m = mode
                    _ = try apply(fn, 0, m) // bilinmeyen ad derlemede hata versin
                    return { x in applyOrNaN(fn, f(x), m) }
                }
            }
        }
    }

    // MARK: Ayrıştırma

    /// Normalleştirilmiş metin ve her karakterin kaynaktaki konumu.
    struct Normalized {
        let text: [Character]
        let map: [Int]
        func srcPos(_ i: Int) -> Int { i < map.count ? map[i] : (map.last.map { $0 + 1 } ?? 0) }

        private static let SUP = Array("⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺ˣʸⁿ⁽⁾")
        private static let SUP_PLAIN = Array("0123456789-+xyn()")
        private static let SUBS = Array("₀₁₂₃₄₅₆₇₈₉")

        static func of(_ source: String) -> Normalized {
            let src = Array(source)
            var sb: [Character] = []
            var map: [Int] = []
            func put(_ s: String, _ at: Int) { for c in s { sb.append(c); map.append(at) } }
            var i = 0
            while i < src.count {
                let ch = src[i]
                if SUP.contains(ch) {
                    put("^(", i)
                    while i < src.count, let k = SUP.firstIndex(of: src[i]) { put(String(SUP_PLAIN[k]), i); i += 1 }
                    put(")", i - 1)
                    continue
                }
                if let sub = SUBS.firstIndex(of: ch) {
                    let prevSub = i > 0 && SUBS.contains(src[i - 1])
                    put(prevSub ? "\(sub)" : "_\(sub)", i)
                    i += 1
                    continue
                }
                let rep: String
                switch ch {
                case "−", "–", "—": rep = "-"
                case "×", "·", "⋅", "∙", "∗": rep = "*"
                case "÷", ":": rep = "/"
                case "π": rep = "pi"
                case "√": rep = "sqrt"
                case "∛": rep = "cbrt"
                case "⌊", "⟦": rep = "floor("
                case "⌈": rep = "ceil("
                case "⌋", "⌉", "⟧": rep = ")"
                case "[", "{": rep = "("
                case "]", "}": rep = ")"
                case "⩽": rep = "≤"
                case "⩾": rep = "≥"
                case " ", "\t", "\n", "\r", "\r\n": rep = " "
                default: rep = String(ch)
                }
                put(rep, i)
                i += 1
            }
            return Normalized(text: sb, map: map)
        }
    }

    private static let NAMES_BASE: [String] = Array(FUNCTIONS.union(CONSTANTS))

    private final class Parser {
        let norm: Normalized
        let s: [Character]
        var i = 0
        var absDepth = 0
        let names: [String]
        let lowerNames: [String]
        let extra: Set<String>

        init(_ norm: Normalized, _ extraNames: Set<String>) {
            self.norm = norm
            s = norm.text
            // Kotlin sortedByDescending kararlıdır; Set sırası belirsiz olduğundan eşit uzunlukta alfabetik sıra
            names = Array(Set(NAMES_BASE).union(extraNames)).sorted { $0.count != $1.count ? $0.count > $1.count : $0 < $1 }
            lowerNames = names.map { $0.lowercased() }
            extra = extraNames
        }

        func err(_ code: MathError.Code, _ at: Int? = nil, _ detail: String = "") -> MathException {
            MathException(code, norm.srcPos(at ?? i), detail)
        }

        private func skip() { while i < s.count && s[i] == " " { i += 1 } }
        private func peek() -> Character? { skip(); return i < s.count ? s[i] : nil }
        private func eat(_ c: Character) -> Bool { skip(); if i < s.count && s[i] == c { i += 1; return true }; return false }

        func expectEnd() throws {
            skip()
            if i < s.count {
                if s[i] == ")" { throw err(.PARENTHESIS) }
                throw err(.UNEXPECTED, i, String(s[i]))
            }
        }

        private func relOp() -> Rel? {
            skip()
            if i >= s.count { return nil }
            let two = i + 1 < s.count ? String(s[i...(i + 1)]) : ""
            switch two {
            case "<=", "=<": i += 2; return .LE
            case ">=", "=>": i += 2; return .GE
            case "!=", "<>", "=/": i += 2; return .NE
            case "==": i += 2; return .EQ
            default: break
            }
            let r: Rel?
            switch s[i] {
            case "=": r = .EQ
            case "<": r = .LT
            case ">": r = .GT
            case "≤": r = .LE
            case "≥": r = .GE
            case "≠": r = .NE
            default: r = nil
            }
            if r != nil { i += 1 }
            return r
        }

        func relation() throws -> MRelation {
            var sides = [try expression()]
            var ops: [Rel] = []
            while let r = relOp() {
                ops.append(r)
                if peek() == nil { throw err(.MISSING_OPERAND) }
                sides.append(try expression())
            }
            return MRelation(sides: sides, ops: ops)
        }

        func expression() throws -> MNode {
            var left = try term()
            while true {
                if eat("+") { left = .bin("+", left, try operand { try self.term() }, implicit: false) }
                else if eat("-") { left = .bin("-", left, try operand { try self.term() }, implicit: false) }
                else { return left }
            }
        }

        private func operand(_ block: () throws -> MNode) throws -> MNode {
            let c = peek()
            if c == nil || c == ")" || "+*/^=<>≤≥≠!".contains(c!) || (c == "|" && absDepth > 0) { throw err(.MISSING_OPERAND) }
            return try block()
        }

        private func implicitNext() -> Bool {
            guard let c = peek() else { return false }
            if c == "|" { return absDepth == 0 }
            return c.isLetter || c == "(" || c.isDigitK || c == "."
        }

        private func term() throws -> MNode {
            var left = try unary()
            while true {
                if eat("*") { left = .bin("*", left, try operand { try self.unary() }, implicit: false) }
                else if eat("/") { left = .bin("/", left, try operand { try self.unary() }, implicit: false) }
                else if implicitNext() { left = .bin("*", left, try power(), implicit: true) }
                else { return left }
            }
        }

        private func unary() throws -> MNode {
            if eat("-") { return .neg(try operand { try self.unary() }) }
            if eat("+") { return try operand { try self.unary() } }
            return try power()
        }

        private func power() throws -> MNode {
            let base = try postfix()
            if eat("^") { return .pow(base, try operand { try self.unary() }) }
            return base
        }

        private func postfix() throws -> MNode {
            var a = try atom()
            while true {
                skip()
                if i < s.count && s[i] == "!" && !(i + 1 < s.count && s[i + 1] == "=") { i += 1; a = .fact(a); continue }
                if i < s.count && (s[i] == "°" || s[i] == "º") { i += 1; a = .deg(a); continue }
                return a
            }
        }

        private func atom() throws -> MNode {
            skip()
            let start = i
            guard let c = peek() else { throw err(.MISSING_OPERAND) }
            if c == "(" {
                i += 1
                if peek() == ")" { throw err(.MISSING_OPERAND) }
                let saved = absDepth
                absDepth = 0
                let e = try expression()
                absDepth = saved
                if !eat(")") { throw err(.PARENTHESIS, start) }
                return e
            }
            if c == "|" {
                i += 1
                absDepth += 1
                let e = try operand { try self.expression() }
                absDepth -= 1
                if !eat("|") { throw err(.PARENTHESIS, start) }
                return .call("abs", [e])
            }
            if c.isDigitK || c == "." { return try number() }
            if c.isLetter { return try name() }
            if c == ")" { throw err(.PARENTHESIS) }
            if "*/^=<>≤≥≠!".contains(c) { throw err(.MISSING_OPERAND) }
            throw err(.UNEXPECTED, i, String(c))
        }

        private func number() throws -> MNode {
            let start = i
            var sb = ""
            while i < s.count && s[i].isDigitK { sb.append(s[i]); i += 1 }
            if i < s.count && (s[i] == "." || s[i] == ",") && i + 1 < s.count && s[i + 1].isDigitK {
                sb.append("."); i += 1
                while i < s.count && s[i].isDigitK { sb.append(s[i]); i += 1 }
            } else if i < s.count && s[i] == "." && !sb.isEmpty {
                i += 1
            }
            if sb.isEmpty || sb == "." { throw err(.UNEXPECTED, start, ".") }
            if i < s.count && (s[i] == "e" || s[i] == "E") {
                var k = i + 1
                if k < s.count && (s[k] == "-" || s[k] == "+") { k += 1 }
                if k < s.count && s[k].isDigitK && (k + 1 >= s.count || !s[k + 1].isLetter || s[k + 1] == "x") {
                    let e0 = i
                    i = k
                    while i < s.count && s[i].isDigitK { i += 1 }
                    let exp = Int(String(s[(e0 + 1)..<i]).replacingOccurrences(of: "+", with: "")) ?? 0
                    return .num(Decimals.plain(sb, exp))
                }
            }
            return .num(sb)
        }

        private func matchName() -> String? {
            let low = String(s[i...]).lowercased()
            for (k, n) in names.enumerated() {
                if !low.hasPrefix(lowerNames[k]) { continue }
                if extra.contains(n) && !s.starts(with: n, at: i) { continue }
                if n == "e" && s[i] != "e" { continue }
                return n
            }
            return nil
        }

        private func name() throws -> MNode {
            let start = i
            guard let n = matchName() else {
                var name = String(s[i])
                i += 1
                if i + 1 < s.count && s[i] == "_" && s[i + 1].isLetterOrDigitK {
                    let k = i + 1
                    var e = k
                    while e < s.count && s[e].isLetterOrDigitK && (e == k || s[e].isDigitK) { e += 1 }
                    name += "_" + String(s[k..<e])
                    i = e
                }
                return .variable(name)
            }
            i += n.count
            if extra.contains(n) { return .variable(n) }
            if CONSTANTS.contains(n) { return .const(n.lowercased()) }
            return try call(n.lowercased(), start)
        }

        private func peekRaw() -> Character? { i < s.count ? s[i] : nil }

        private func call(_ fn0: String, _ start: Int) throws -> MNode {
            var fn = fn0
            var base: MNode? = nil
            if (fn == "log" || fn == "ln") && peekRaw() == "_" {
                i += 1
                base = try atom()
                fn = "logb"
            }
            var outerPow: MNode? = nil
            if peekRaw() == "^" {
                i += 1
                outerPow = try operand { try self.postfix() }
            }
            let args: [MNode]
            if peek() == "(" {
                i += 1
                let saved = absDepth
                absDepth = 0
                if peek() == ")" { throw err(.ARGUMENTS, start, fn) }
                var list = [try expression()]
                while eat(";") || (peekComma() && eat(",")) { list.append(try expression()) }
                absDepth = saved
                if !eat(")") { throw err(.PARENTHESIS, start) }
                args = list
            } else {
                let c = peek()
                if c == nil || ")+*/^=<>≤≥≠".contains(c!) { throw err(.MISSING_OPERAND) }
                args = [try functionArgument()]
            }
            let node: MNode
            if fn == "logb" {
                if args.count != 1 { throw err(.ARGUMENTS, start, "log") }
                node = .call("logb", [base!, args[0]])
            } else if TWO_ARGS.contains(fn) {
                if args.count < 2 { throw err(.ARGUMENTS, start, fn) }
                node = .call(fn, args)
            } else {
                if args.count != 1 { throw err(.ARGUMENTS, start, fn) }
                node = .call(canonical(fn), args)
            }
            return outerPow.map { .pow(node, $0) } ?? node
        }

        private func peekComma() -> Bool { skip(); return i < s.count && s[i] == "," }

        private func functionArgument() throws -> MNode {
            var e: MNode
            if peek() == "-" { i += 1; e = .neg(try power()) } else { e = try power() }
            while let c = peek() {
                let continues = c.isDigitK || c == "(" || (c.isLetter && { let m = matchName(); return m == nil || !FUNCTIONS.contains(m!) }())
                if !continues { break }
                e = .bin("*", e, try power(), implicit: true)
            }
            return e
        }

        private func canonical(_ fn: String) -> String {
            switch fn {
            case "tg": return "tan"
            case "cotg", "ctg": return "cot"
            case "arcsin": return "asin"
            case "arccos": return "acos"
            case "arctan": return "atan"
            case "sgn": return "sign"
            case "lg": return "log"
            default: return fn
            }
        }
    }

    // MARK: Yazım

    private static let SUP_MAP: [Character: Character] = [
        "0": "⁰", "1": "¹", "2": "²", "3": "³", "4": "⁴", "5": "⁵", "6": "⁶", "7": "⁷", "8": "⁸", "9": "⁹", "-": "⁻", "n": "ⁿ", "x": "ˣ",
    ]

    /// Ağacın güzel yazımı: "2x² − 3", "A·sin(kx)", "9,8·t²/2".
    static func pretty(_ n: MNode, decimalComma: Bool = true) -> String { Pretty(comma: decimalComma).p(n, 0) }

    static func pretty(_ r: MRelation, decimalComma: Bool = true) -> String {
        var sb = pretty(r.sides[0], decimalComma: decimalComma)
        for (k, op) in r.ops.enumerated() { sb += " " + op.symbol + " " + pretty(r.sides[k + 1], decimalComma: decimalComma) }
        return sb
    }

    private struct Pretty {
        let comma: Bool

        func p(_ n: MNode, _ ctx: Int) -> String {
            let s: String, prec: Int
            switch n {
            case let .num(t): (s, prec) = (num(t), 5)
            case let .const(name): (s, prec) = (name == "pi" ? "π" : "e", 5)
            case let .variable(name): (s, prec) = (varName(name), 5)
            case let .neg(a): (s, prec) = ("−" + p(a, 3), 3)
            case let .bin(op, a, b, implicit):
                switch op {
                case "+":
                    if case let .neg(bb) = b { s = p(a, 1) + " − " + p(bb, 2) } else { s = p(a, 1) + " + " + p(b, 2) }
                    prec = 1
                case "-": (s, prec) = (p(a, 1) + " − " + p(b, 2), 1)
                case "*": (s, prec) = (mul(a, b, implicit), 2)
                default: (s, prec) = (p(a, 2) + "/" + p(b, 3), 2)
                }
            case let .pow(a, b): (s, prec) = (pow(a, b), 4)
            case let .fact(a): (s, prec) = (p(a, 5) + "!", 5)
            case let .deg(a): (s, prec) = (p(a, 5) + "°", 5)
            case let .call(fn, args): (s, prec) = (call(fn, args), 5)
            }
            return prec < ctx ? "(\(s))" : s
        }

        private func num(_ t: String) -> String {
            var clean = t
            if t.contains(".") {
                while clean.hasSuffix("0") { clean.removeLast() }
                if clean.hasSuffix(".") { clean.removeLast() }
            }
            return comma ? clean.replacingOccurrences(of: ".", with: ",") : clean
        }

        private func varName(_ n: String) -> String {
            guard let k = n.firstIndex(of: "_") else { return n }
            let subs = Array("₀₁₂₃₄₅₆₇₈₉")
            let sub = n[n.index(after: k)...].map { c -> Character in c.isAsciiDigitK ? subs[c.wholeNumberValue!] : c }
            return String(n[..<k]) + String(sub)
        }

        private func mul(_ an: MNode, _ bn: MNode, _ implicit: Bool) -> String {
            let a = p(an, 2), b = p(bn, 3)
            let bFirst = b.first, aLast = a.last
            let tight = implicit && (bFirst.map { $0.isLetter || $0 == "(" || $0 == "√" } ?? false) &&
                (aLast.map { $0.isLetterOrDigitK || $0 == ")" || "²³¹⁰⁴⁵⁶⁷⁸⁹".contains($0) } ?? false) &&
                !(aLast!.isDigitK && bFirst!.isDigitK)
            return tight ? a + b : "\(a)·\(b)"
        }

        private func supDigits(_ t: String) -> String? {
            if t.contains(".") { return nil }
            let m = t.map { SUP_MAP[$0] }
            if m.contains(where: { $0 == nil }) { return nil }
            return String(m.map { $0! })
        }

        private func pow(_ a: MNode, _ e: MNode) -> String {
            let base = p(a, 5)
            var sup: String? = nil
            switch e {
            case let .num(t): sup = supDigits(t)
            case let .neg(inner): if case let .num(t) = inner { sup = supDigits(t).map { "⁻" + $0 } }
            case let .variable(name): if name.count == 1 { sup = SUP_MAP[name.first!].map { String($0) } }
            default: break
            }
            if let sup { return base + sup }
            return base + "^" + p(e, 5)
        }

        private func call(_ fn: String, _ args: [MNode]) -> String {
            switch fn {
            case "sqrt": return "√" + p(args[0], 5)
            case "abs": return "|" + p(args[0], 0) + "|"
            case "logb": return "log_" + p(args[0], 5) + "(" + p(args[1], 0) + ")"
            default: return fn + "(" + args.map { p($0, 0) }.joined(separator: "; ") + ")"
            }
        }
    }
}

// MARK: - Ondalık yardımcıları (Java BigDecimal'ın kullanılan kısmı)

enum Decimals {
    /// Bilimsel yazımı düz ondalığa çevirir: ("1.6", -19) → "0.00000000000000000016" (`BigDecimal.toPlainString`).
    static func plain(_ mantissa: String, _ exp: Int) -> String {
        var m = mantissa
        var negative = false
        if m.hasPrefix("-") { negative = true; m.removeFirst() }
        let parts = m.split(separator: ".", omittingEmptySubsequences: false)
        let intPart = String(parts[0]), frac = parts.count > 1 ? String(parts[1]) : ""
        var digits = intPart + frac
        var point = intPart.count + exp
        if point <= 0 { digits = String(repeating: "0", count: 1 - point) + digits; point = 1 }
        if point > digits.count { digits += String(repeating: "0", count: point - digits.count) }
        var ip = String(digits.prefix(point))
        let fp = String(digits.dropFirst(point))
        while ip.count > 1 && ip.hasPrefix("0") { ip.removeFirst() }
        // BigDecimal ölçeği korur: kesir kısmındaki sondaki sıfırlar kalır (yalnız tam kısımda taşma yok)
        let s = fp.isEmpty ? ip : ip + "." + fp
        return negative ? "-" + s : s
    }

    /// Ondalık rakamlar ve ilk rakamın onluk üssü: d = 0.d1d2… × 10^(exp+1). [digits] anlamlı basamağa
    /// yuvarlanmış (yarım yukarı), sondaki sıfırlar atılmamış.
    static func significant(_ d: Double, _ digits: Int) -> (String, Int)? {
        if d == 0 || !d.isFinite { return nil }
        let s = String(format: "%.30e", abs(d))
        guard let eIdx = s.firstIndex(of: "e"), let exp = Int(s[s.index(after: eIdx)...]) else { return nil }
        var ds = Array(s[..<eIdx].filter { $0 != "." })
        var e = exp
        let roundUp = ds.count > digits && ds[digits].wholeNumberValue! >= 5
        ds = Array(ds.prefix(digits))
        if roundUp {
            var k = digits - 1
            while k >= 0 {
                if ds[k] == "9" { ds[k] = "0"; k -= 1 } else { ds[k] = Character(String(ds[k].wholeNumberValue! + 1)); break }
            }
            if k < 0 { ds.insert("1", at: 0); ds.removeLast(); e += 1 }
        }
        return (String(ds), e)
    }

    /// Kesrin 6 (ya da [digits]) anlamlı basamağa yarım yukarı yuvarlanmışı (BigDecimal.divide(MathContext)).
    static func significant(_ num: BigInt, _ den: BigInt, _ digits: Int) -> (String, Int)? {
        if num.isZero { return nil }
        let n = num.magnitude, d = den.magnitude
        let lo = BigInt.ten.pow(digits - 1), hi = BigInt.ten.pow(digits)
        var k = digits - (n.description.count - d.description.count)
        func q(_ k: Int) -> (BigInt, BigInt, BigInt) {
            let nn = k >= 0 ? n * BigInt.ten.pow(k) : n
            let dd = k >= 0 ? d : d * BigInt.ten.pow(-k)
            let (qq, r) = nn.quotientAndRemainder(dd)
            return (qq, r, dd)
        }
        var (qq, r, dd) = q(k)
        while qq >= hi { k -= 1; (qq, r, dd) = q(k) }
        while qq < lo { k += 1; (qq, r, dd) = q(k) }
        if r * BigInt(2) >= dd { qq = qq + BigInt.one }
        if qq >= hi { qq = qq / BigInt.ten; k -= 1 }
        return (qq.description, digits - 1 - k)
    }
}
