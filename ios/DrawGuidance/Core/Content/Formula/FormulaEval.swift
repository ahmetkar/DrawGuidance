import Foundation

/// v45 formülde bilinmeyenlerin yerine sayı yazılınca sonucun hesaplanması (Android `FormulaEval`).
///
/// Bağıntısız ifadede sonuç sona eklenir ("2·4+3 = 11"); bağıntıda iki yan sayıysa yanlış bağıntının yerine doğrusu
/// yazılır (kullanıcınınki kaynakta saklanır); bir yanı tek bilinmeyense öteki yan hesaplanır. Hesap kesirle yapılır;
/// sıfıra bölme ve tanım dışında "= tanımsız" yazılır ve hata döner.
enum FormulaEval {
    /// Sonuç: yeni kaynak, eklenen sonuç (TeX, yoksa nil) ve hata.
    struct Outcome: Equatable { let source: String; let result: String?; let error: MathError? }

    static let RESULT_MARK = "\\color{dgres}"
    static let REL_MARK = "\\color{dgrel:"
    static let DOT_MARK = "{\\cdot}"
    static let OPEN_MARK = "{(}"
    static let CLOSE_MARK = "{)}"

    /// Otomatik eklenenleri kaldırır (kullanıcının yazdığı kaynak).
    static func strip(_ source: String) -> String {
        var s = source
        if let r = s.range(of: RESULT_MARK) { s = trimEnd(String(s[..<r.lowerBound])) }
        while true {
            let a = Array(s)
            let m = indexOf(a, REL_MARK, 0)
            if m < 0 { break }
            let e = a.index(of: "}", from: m)
            if e < 0 { break }
            let original = a.str(m + REL_MARK.count, e)
            let after = e + 1
            let tok = relationTokenAt(a, after)
            let tokEnd = tok.map { after + $0.count } ?? after
            let rest = a.str(tokEnd, a.count)
            let t = texOf(original)
            s = a.str(0, m) + (rest.hasPrefix(" ") || rest.hasPrefix("\\") || rest.hasPrefix("{") ? trimEnd(t) : t) + rest
        }
        return s.replacingOccurrences(of: DOT_MARK, with: "").replacingOccurrences(of: OPEN_MARK, with: "")
            .replacingOccurrences(of: CLOSE_MARK, with: "")
    }

    /// Elle düzenlemeye açılan kaynak: sonuç ve bağıntı işaretleri atılır, eklenen çarpı/parantez görünür kalır.
    static func stripResult(_ source: String) -> String {
        if !source.contains("\\color{dg") && !source.contains(DOT_MARK) && !source.contains(OPEN_MARK) { return source }
        let keep = source.replacingOccurrences(of: DOT_MARK, with: "\\cdot ").replacingOccurrences(of: OPEN_MARK, with: "(")
            .replacingOccurrences(of: CLOSE_MARK, with: ")")
        return strip(keep)
    }

    static let REL_TOKENS = ["\\leqslant", "\\geqslant", "\\leq", "\\geq", "\\neq", "\\le", "\\ge", "\\ne", "\\lt", "\\gt", "≤", "≥", "≠", "<", ">", "="]

    static func relationTokenAt(_ s: [Character], _ i: Int) -> String? {
        var k = i
        while k < s.count && s[k] == " " { k += 1 }
        guard let tok = REL_TOKENS.first(where: { t in
            s.starts(with: t, at: k) && !(t.hasPrefix("\\") && k + t.count < s.count && s[k + t.count].isLetter)
        }) else { return nil }
        return s.str(i, k) + tok
    }

    static func texOf(_ code: String) -> String {
        switch code {
        case "eq": return "="
        case "lt": return "<"
        case "gt": return ">"
        case "le": return "\\le "
        case "ge": return "\\ge "
        case "ne": return "\\neq "
        default: return "="
        }
    }

    static func trimEnd(_ s: String) -> String {
        var c = Array(s)
        while let l = c.last, l.isWhitespace { c.removeLast() }
        return String(c)
    }

    static func indexOf(_ a: [Character], _ p: String, _ from: Int) -> Int {
        var k = max(from, 0)
        let n = p.count
        while k + n <= a.count { if a.starts(with: p, at: k) { return k }; k += 1 }
        return -1
    }

    private static func relOfToken(_ t: String) -> Rel? {
        switch t.trimmingCharacters(in: .whitespaces) {
        case "=": return .EQ
        case "<", "\\lt": return .LT
        case ">", "\\gt": return .GT
        case "\\le", "\\leq", "\\leqslant", "≤": return .LE
        case "\\ge", "\\geq", "\\geqslant", "≥": return .GE
        case "\\ne", "\\neq", "≠": return .NE
        default: return nil
        }
    }

    private static func codeOf(_ r: Rel) -> String {
        switch r { case .EQ: return "eq"; case .LT: return "lt"; case .GT: return "gt"; case .LE: return "le"; case .GE: return "ge"; case .NE: return "ne" }
    }

    // MARK: TeX → doğrusal ifade

    private struct Linear { let text: String; let hasDecimal: Bool }
    private struct Unsupported: Error {}

    private static let FUNS: Set<String> = ["sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh", "ln", "log", "lg", "exp", "tg", "cotg"]
    private static let GREEK: [String: String] = [
        "alpha": "α", "beta": "β", "gamma": "γ", "delta": "δ", "epsilon": "ε", "varepsilon": "ε", "zeta": "ζ",
        "eta": "η", "theta": "θ", "vartheta": "θ", "iota": "ι", "kappa": "κ", "lambda": "λ", "mu": "μ", "nu": "ν",
        "xi": "ξ", "rho": "ρ", "sigma": "σ", "tau": "τ", "upsilon": "υ", "phi": "φ", "varphi": "φ", "chi": "χ",
        "psi": "ψ", "omega": "ω", "Gamma": "Γ", "Delta": "Δ", "Theta": "Θ", "Lambda": "Λ", "Xi": "Ξ", "Sigma": "Σ",
        "Phi": "Φ", "Psi": "Ψ", "Omega": "Ω",
    ]
    private static let SPACES: Set<String> = [",", ";", ":", "!", "quad", "qquad", " ", "thinspace", "medspace", "thickspace", "enspace"]
    private static let SKIP_ARG: Set<String> = ["text", "textrm", "mbox", "mathrm", "textit", "textbf", "operatorname", "label", "color", "hbox"]
    private static let IGNORE: Set<String> = ["displaystyle", "textstyle", "left", "right", "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr", "biggl", "biggr", "limits", "nolimits"]

    /// Kaynak aralığı [start, end) (Kotlin `IntRange` first…last+1).
    private struct Span { let start: Int; let end: Int }

    private final class Conv {
        let s: [Character]
        var i = 0
        var out: [Character] = []
        var relCount = 0
        var decimal = false

        init(_ s: [Character]) { self.s = s }

        func groupEnd(_ from: Int) -> Int {
            var k = from, d = 0
            while k < s.count {
                let c = s[k]
                if c == "\\" && k + 1 < s.count { k += 2; continue }
                if c == "{" { d += 1 }
                if c == "}" { d -= 1; if d == 0 { return k + 1 } }
                k += 1
            }
            return s.count
        }

        func argRange(_ from: Int) throws -> Span {
            var k = from
            while k < s.count && s[k] == " " { k += 1 }
            if k >= s.count { throw Unsupported() }
            if s[k] == "{" { return Span(start: k + 1, end: max(k + 1, groupEnd(k) - 1)) }
            if s[k] == "\\" {
                var e = k + 1
                if e < s.count && s[e].isLetter { while e < s.count && s[e].isLetter { e += 1 } } else { e += 1 }
                return Span(start: k, end: min(e, s.count))
            }
            return Span(start: k, end: k + 1)
        }

        /// Aralıktan sonraki konum (süslü gruptaysa kapanıştan sonrası).
        func after(_ r: Span) -> Int { r.start - 1 >= 0 && r.start - 1 < s.count && s[r.start - 1] == "{" ? r.end + 1 : r.end }

        func sub(_ r: Span) throws -> String {
            let c = Conv(Array(s[r.start..<max(r.start, min(r.end, s.count))]))
            try c.run()
            if c.relCount > 0 { throw Unsupported() }
            if c.decimal { decimal = true }
            return String(c.out)
        }

        func plain(_ r: Span) -> String {
            Formula.plain(String(s[r.start..<max(r.start, min(r.end, s.count))])).filter(\.isLetterOrDigitK)
        }

        func run() throws {
            while i < s.count {
                let c = s[i]
                if c == "\\" { try command() }
                else if c == "{" { out.append("("); i += 1 }
                else if c == "}" { out.append(")"); i += 1 }
                else if c == "_" { try subscriptArg() }
                else if c == "^" {
                    i += 1
                    let r = try argRange(i)
                    out += "^(" + (try sub(r)) + ")"
                    i = after(r)
                } else if "=<>≤≥≠".contains(c) {
                    relCount += 1
                    out += [" ", c, " "]
                    i += 1
                } else if c == "~" { out.append(" "); i += 1 }
                else if c == "&" { i += 1 }
                else if c == "." && i + 1 < s.count && s[i + 1].isDigitK { decimal = true; out.append(c); i += 1 }
                else if c.isDigitK {
                    out.append(c)
                    if i + 2 < s.count && (s[i + 1] == "." || s[i + 1] == ",") && s[i + 2].isDigitK { decimal = true }
                    i += 1
                } else { out.append(c); i += 1 }
            }
        }

        private func endsWith(_ t: String) -> Bool {
            let a = Array(t)
            return out.count >= a.count && Array(out.suffix(a.count)) == a
        }

        private func subscriptArg() throws {
            i += 1
            let r = try argRange(i)
            let name = plain(r)
            i = after(r)
            let n = out.count
            if n >= 3 && out[n - 3] == "(" && out[n - 2].isLetter && out[n - 1] == ")" && !name.isEmpty {
                let v = out[n - 2]
                out.removeLast(3)
                out += "(" + String(v) + "_" + name + ")"
            } else if endsWith("log") || endsWith("log ") {
                while out.last == " " { out.removeLast() }
                if name.isEmpty { throw Unsupported() }
                out += "_(" + name + ")"
            } else if let l = out.last, l.isLetter, !name.isEmpty {
                out += "_" + name
            } else { throw Unsupported() }
        }

        private func command() throws {
            let st = i
            i += 1
            if i >= s.count { return }
            if !s[i].isLetter {
                let ch = String(s[i])
                i += 1
                if SPACES.contains(ch) { out.append(" ") }
                else if ch == "{" { out.append("(") }
                else if ch == "}" { out.append(")") }
                else if ch == "|" { out.append("|") }
                else { out.append(" ") }
                return
            }
            while i < s.count && s[i].isLetter { i += 1 }
            let name = String(s[(st + 1)..<i])
            switch name {
            case "slot":
                let nr = try argRange(i)
                i = nr.end + 1
                let vr = try argRange(i)
                let v = try sub(vr)
                i = vr.end + 1
                out += "(" + v.trimmingCharacters(in: .whitespaces) + ")"
            case "frac", "dfrac", "tfrac", "cfrac":
                let a = try argRange(i)
                let b = try argRange(after(a))
                let sa = try sub(a), sb = try sub(b)
                out += "((" + sa + ")/(" + sb + "))"
                i = after(b)
            case "sqrt":
                var k = i
                while k < s.count && s[k] == " " { k += 1 }
                var index: String? = nil
                if k < s.count && s[k] == "[" {
                    let e = s.index(of: "]", from: k)
                    if e < 0 { throw Unsupported() }
                    index = try sub(Span(start: k + 1, end: e))
                    k = e + 1
                }
                let a = try argRange(k)
                let sa = try sub(a)
                if let index { out += "((" + sa + ")^(1/(" + index + ")))" } else { out += "sqrt(" + sa + ")" }
                i = after(a)
            case "cdot", "times", "ast": out.append("*")
            case "div": out.append("/")
            case "pi": out += "(pi)"
            case _ where GREEK[name] != nil: out += "(" + GREEK[name]! + ")"
            case _ where FUNS.contains(name): out += " " + name + " "
            case "le", "leq", "leqslant": rel("≤")
            case "ge", "geq", "geqslant": rel("≥")
            case "ne", "neq": rel("≠")
            case "lt": rel("<")
            case "gt": rel(">")
            case "circ", "degree": out.append("°")
            case _ where SPACES.contains(name): out.append(" ")
            case "textcolor": let r = try argRange(i); i = r.end + 1
            case _ where SKIP_ARG.contains(name):
                if name == "color" && s.starts(with: "{dgrel:", at: i) { i = groupEnd(i); return }
                let r = try argRange(i)
                i = after(r)
            case _ where IGNORE.contains(name):
                var k = i
                while k < s.count && s[k] == " " { k += 1 }
                if name == "left" || name == "right" || name.hasPrefix("big") || name.hasPrefix("Big") {
                    if k < s.count {
                        if s[k] == "." { i = k + 1 }
                        else if s[k] == "(" || s[k] == "[" { out.append("("); i = k + 1 }
                        else if s[k] == ")" || s[k] == "]" { out.append(")"); i = k + 1 }
                        else if s[k] == "|" { out.append("|"); i = k + 1 }
                        else if s.starts(with: "\\{", at: k) { out.append("("); i = k + 2 }
                        else if s.starts(with: "\\}", at: k) { out.append(")"); i = k + 2 }
                        else if s.starts(with: "\\lvert", at: k) || s.starts(with: "\\rvert", at: k) || s.starts(with: "\\vert", at: k) {
                            out.append("|")
                            i = FormulaEval.indexOf(s, "vert", k) + 4
                        } else { throw Unsupported() }
                    }
                }
            case "lvert", "rvert", "vert": out.append("|")
            case "lfloor": out += "floor("
            case "rfloor", "rceil": out.append(")")
            case "lceil": out += "ceil("
            default: throw Unsupported()
            }
        }

        private func rel(_ sym: String) {
            relCount += 1
            out += " " + sym + " "
        }
    }

    private static func linear(_ source: String) -> Linear? {
        let c = Conv(Array(source))
        do { try c.run() } catch { return nil }
        return Linear(text: String(c.out), hasDecimal: c.decimal)
    }

    // MARK: Hesap

    /// Kaynağı hesaplar ve sonucu ekler. [undefined] tanımsız sonucun yazısı; [decimalComma] ondalık ayırıcı virgül mü.
    static func annotate(_ source: String, undefined: String = "tanımsız", decimalComma: Bool = true) -> Outcome {
        let base = strip(source)
        guard let lin = linear(base), let rel = try? MathExpr.parseRelation(lin.text) else { return Outcome(source: base, result: nil, error: nil) }
        let unknown = rel.sides.map { $0.variables() }
        do {
            if !rel.isRelation {
                if !unknown[0].isEmpty || isLiteral(rel.sides[0]) { return Outcome(source: base, result: nil, error: nil) }
                let v = try ExactEval.eval(rel.sides[0], [:], .RAD)
                let tex = resultTex(v, lin.hasDecimal, decimalComma)
                return Outcome(source: base + RESULT_MARK + tex, result: tex, error: nil)
            }
            if rel.ops.count == 1 && rel.ops[0] == .EQ && unknown[0].isEmpty != unknown[1].isEmpty {
                let (lone, other) = !unknown[0].isEmpty ? (rel.sides[0], rel.sides[1]) : (rel.sides[1], rel.sides[0])
                if !lone.isVar || isLiteral(other) || unknown[0].count + unknown[1].count != 1 { return Outcome(source: base, result: nil, error: nil) }
                let v = try ExactEval.eval(other, [:], .RAD)
                let tex = resultTex(v, lin.hasDecimal, decimalComma)
                return Outcome(source: base + RESULT_MARK + tex, result: tex, error: nil)
            }
            if unknown.allSatisfy(\.isEmpty) {
                let values = try rel.sides.map { ExactEval.tidy(try ExactEval.eval($0, [:], .RAD)) }
                var out = base
                let positions = relationSpans(Array(out))
                if positions.count != rel.ops.count { return Outcome(source: base, result: nil, error: nil) }
                var chosen: [Rel] = []
                for k in rel.ops.indices.reversed() {
                    let cmp = ExactEval.compare(values[k], values[k + 1])
                    let original = rel.ops[k]
                    let shown: Rel = original.holdsCmp(cmp) ? original : original == .NE ? .EQ : cmp < 0 ? .LT : cmp > 0 ? .GT : .EQ
                    chosen.append(shown)
                    if shown != original {
                        let a = Array(out)
                        let r = positions[k]
                        let rest = a.str(r.end, a.count)
                        let t = texOf(codeOf(shown))
                        let tt = rest.hasPrefix(" ") || rest.hasPrefix("\\") || rest.hasPrefix("{") ? trimEnd(t) : t
                        out = a.str(0, r.start) + REL_MARK + codeOf(original) + "}" + tt + rest
                    }
                }
                return Outcome(source: out, result: chosen.reversed().map(\.symbol).joined(separator: " "), error: nil)
            }
            return Outcome(source: base, result: nil, error: nil)
        } catch let e as MathException {
            if e.error.code == .UNKNOWN_NAME { return Outcome(source: base, result: nil, error: nil) }
            let tex = "=\\text{\(undefined)}"
            return Outcome(source: base + RESULT_MARK + tex, result: tex, error: e.error)
        } catch {
            return Outcome(source: base, result: nil, error: nil)
        }
    }

    private static let REL_COMMANDS: Set<String> = ["\\le", "\\leq", "\\leqslant", "\\ge", "\\geq", "\\geqslant", "\\ne", "\\neq", "\\lt", "\\gt"]

    /// Kaynaktaki bağıntı işaretlerinin yerleri.
    private static func relationSpans(_ s: [Character]) -> [Span] {
        var out: [Span] = []
        var i = 0
        while i < s.count {
            let c = s[i]
            if c == "{" || c == "}" { i += 1; continue }
            if c == "\\" {
                var e = i + 1
                while e < s.count && s[e].isLetter { e += 1 }
                let name = s.str(i, e)
                if name == "\\color" { let k = s.index(of: "}", from: e); i = k < 0 ? s.count : k + 1; continue }
                if REL_COMMANDS.contains(name) { out.append(Span(start: i, end: e)) }
                i = e == i + 1 ? i + 2 : e
                continue
            }
            if "=<>≤≥≠".contains(c) { out.append(Span(start: i, end: i + 1)) }
            i += 1
        }
        return out
    }

    private static func isLiteral(_ n: MNode) -> Bool {
        if case .num = n { return true }
        if case let .neg(a) = n, case .num = a { return true }
        return false
    }

    /// Sonuç TeX'i: "=11", "=\frac{5}{6}", "=-\frac{1}{2}", "=2{,}5", "\approx 12{,}57".
    static func resultTex(_ v0: Value, _ preferDecimal: Bool, _ decimalComma: Bool) -> String {
        let v = ExactEval.tidy(v0)
        let sep = decimalComma ? "{,}" : "."
        switch v {
        case let .exact(q):
            if q.num.magnitude.bitLength > 50 || q.den.bitLength > 50 {
                return "\\approx " + scientific(Decimals.significant(q.num, q.den, 6), negative: q.signum < 0, sep)
            }
            if q.isInteger { return "=" + q.num.description }
            if preferDecimal, let t = q.terminatingDecimal() { return "=" + t.replacingOccurrences(of: ".", with: sep) }
            return "=" + (q.signum < 0 ? "-" : "") + "\\frac{\(q.num.magnitude)}{\(q.den)}"
        case let .approx(d, _):
            if abs(d) >= 1e12 || (abs(d) < 1e-4 && d != 0) {
                return "\\approx " + scientific(Decimals.significant(d, 6), negative: d < 0, sep)
            }
            return "\\approx " + decimal(d).replacingOccurrences(of: ".", with: sep)
        }
    }

    /// a·10ⁿ yazımı.
    private static func scientific(_ sig: (String, Int)?, negative: Bool, _ sep: String) -> String {
        guard let sig else { return "0" }
        var digits = sig.0
        let exp = sig.1
        while digits.count > 1 && digits.hasSuffix("0") { digits.removeLast() }
        var mant = String(digits.prefix(1))
        if digits.count > 1 { mant += "." + digits.dropFirst() }
        return (negative ? "-" : "") + mant.replacingOccurrences(of: ".", with: sep) + "\\cdot 10^{\(exp)}"
    }

    /// 6 anlamlı basamağa yuvarlanmış ondalık (sondaki sıfırlar atılır).
    static func decimal(_ d: Double) -> String {
        guard let sig = Decimals.significant(d, 6) else { return "0" }
        var digits = sig.0
        let exp = sig.1
        while digits.count > 1 && digits.hasSuffix("0") { digits.removeLast() }
        let mant = digits.count > 1 ? String(digits.prefix(1)) + "." + digits.dropFirst() : digits
        var s = Decimals.plain(mant, exp)
        if s.contains(".") {
            while s.hasSuffix("0") { s.removeLast() }
            if s.hasSuffix(".") { s.removeLast() }
        }
        return (d < 0 ? "-" : "") + s
    }
}

