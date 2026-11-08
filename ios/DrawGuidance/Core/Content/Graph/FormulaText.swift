import Foundation

/// Kullanıcının yazdığı formülü ders kitabı yazımına çevirir (Android `FormulaText`): `x^2-3x` → `x² − 3x`.
enum FormulaText {
    private enum Tok: Equatable { case num(String), name(String), op(Character), lp, rp, bar }

    private static func tokenize(_ src: [Character]) -> [Tok]? {
        var out: [Tok] = []
        var i = 0
        while i < src.count {
            let c = src[i]
            if c.isWhitespace { i += 1 }
            else if c.isDigitK || c == "." {
                let s = i
                while i < src.count && (src[i].isDigitK || src[i] == ".") { i += 1 }
                out.append(.num(String(src[s..<i])))
            } else if c.isLetter {
                guard let name = GExpression.matchName(src, i) else { return nil }
                out.append(.name(name))
                i += name.count
            } else if c == "(" { out.append(.lp); i += 1 }
            else if c == ")" { out.append(.rp); i += 1 }
            else if c == "|" { out.append(.bar); i += 1 }
            else if "+-*/^=".contains(c) { out.append(.op(c)); i += 1 }
            else { return nil }
        }
        return out
    }

    private static let SUP_FROM = Array("0123456789−+xyn()")
    static let SUP_TO = Array("⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺ˣʸⁿ⁽⁾")

    static func superscript(_ s: String) -> String? {
        let t = s.replacingOccurrences(of: " ", with: "")
        if t.isEmpty { return nil }
        var sb = ""
        for c in t {
            guard let k = SUP_FROM.firstIndex(of: c) else { return nil }
            sb.append(SUP_TO[k])
        }
        return sb
    }

    private static func isSimple(_ s: String) -> Bool { !s.isEmpty && s.allSatisfy { $0.isLetterOrDigitK || $0 == "," || $0 == "π" || SUP_TO.contains($0) } }

    private final class Printer {
        let t: [Tok]
        var i = 0
        init(_ t: [Tok]) { self.t = t }

        private func at(_ k: Int) -> Tok? { k < t.count ? t[k] : nil }

        func seq(stopAtBar: Bool = false) -> String {
            var sb = ""
            var prevOperand = false
            var prevNumber = false
            while i < t.count {
                let tok = t[i]
                if tok == .rp { break }
                if tok == .bar && stopAtBar { break }
                switch tok {
                case let .num(text):
                    sb += text.replacingOccurrences(of: ".", with: ",")
                    i += 1; prevOperand = true; prevNumber = true
                    continue
                case let .name(text):
                    i += 1
                    sb += nameOrFunction(text)
                    prevOperand = true
                case .lp:
                    i += 1
                    let inner = seq()
                    if at(i) == .rp { i += 1 }
                    sb += "(" + inner + ")"
                    prevOperand = true
                case .bar:
                    i += 1
                    let inner = seq(stopAtBar: true)
                    if at(i) == .bar { i += 1 }
                    sb += "|" + inner + "|"
                    prevOperand = true
                case let .op(c):
                    i += 1
                    switch c {
                    case "^": sb += exponent()
                    case "*":
                        let next = at(i)
                        var nameOrLp = next == .lp
                        if case .name = next { nameOrLp = true }
                        if !(prevNumber && nameOrLp) { sb += "·" }
                        prevOperand = false
                    case "/": sb += "/"; prevOperand = false
                    case "-": sb += prevOperand ? " − " : "−"; prevOperand = false
                    case "+": if prevOperand { sb += " + " }; prevOperand = false
                    case "=": sb += " = "; prevOperand = false
                    default: break
                    }
                case .rp: break
                }
                prevNumber = false
            }
            return sb
        }

        private func exponent() -> String {
            guard let tok = at(i) else { return "^" }
            let raw: String
            var grouped = false
            if tok == .lp {
                i += 1
                raw = seq()
                if at(i) == .rp { i += 1 }
                grouped = true
            } else if tok == .op("-") {
                i += 1
                raw = "−" + atomText()
            } else { raw = atomText() }
            if let s = superscript(raw) { return s }
            return grouped || !isSimple(raw) ? "^(\(raw))" : "^\(raw)"
        }

        private func atomText() -> String {
            guard let tok = at(i) else { return "" }
            switch tok {
            case let .num(text): i += 1; return text.replacingOccurrences(of: ".", with: ",")
            case let .name(text): i += 1; return nameOrFunction(text)
            case .lp:
                i += 1
                let inner = seq()
                if at(i) == .rp { i += 1 }
                return "(\(inner))"
            default: return ""
            }
        }

        private func nameOrFunction(_ name: String) -> String {
            if !GExpression.FUNCTIONS.contains(name) { return name == "pi" ? "π" : name }
            let parens = at(i) == .lp
            let arg: String
            if parens {
                i += 1
                arg = seq()
                if at(i) == .rp { i += 1 }
            } else { arg = atomText() }
            switch name {
            case "sqrt": return isSimple(arg) ? "√\(arg)" : "√(\(arg))"
            case "cbrt": return isSimple(arg) ? "∛\(arg)" : "∛(\(arg))"
            case "abs": return "|\(arg)|"
            case "floor": return "⟦\(arg)⟧"
            case "ceil": return "⌈\(arg)⌉"
            case "exp": return superscript(arg).map { "e" + $0 } ?? "e^(\(arg))"
            default:
                let shown: String
                switch name {
                case "asin": shown = "arcsin"
                case "acos": shown = "arccos"
                case "atan": shown = "arctan"
                case "tg": shown = "tan"
                case "cotg": shown = "cot"
                case "sgn": shown = "sign"
                default: shown = name
                }
                return parens || !isSimple(arg) ? "\(shown)(\(arg))" : "\(shown) \(arg)"
            }
        }
    }

    /// Formül gövdesinin güzel yazımı; ayrıştırılamazsa girilen metin (ASCII işlemler düzeltilerek).
    static func pretty(_ body: String) -> String {
        let norm = GExpression.normalize(body)
        guard let toks = tokenize(Array(norm)) else {
            return body.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "*", with: "·").replacingOccurrences(of: "-", with: "−")
        }
        let pr = Printer(toks)
        var sb = pr.seq()
        while pr.i < toks.count { pr.i += 1; sb += ")" + pr.seq() }
        return sb.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Formül kutusu metni: "y = …", "z = …", kullanıcı ön eki ya da kapalı denklem.
    static func label(_ text: String, _ lhs: String = "y") -> String {
        if text.allSatisfy(\.isWhitespace) { return "" }
        let body = GExpression.body(text)
        if body.contains("=") { return pretty(body) }
        let prefix = GExpression.prefix(text).map { pretty($0) }.flatMap { $0.allSatisfy(\.isWhitespace) ? nil : $0 } ?? lhs
        return "\(prefix) = \(pretty(body))"
    }

    /// v18: güzel yazımdaki formülde x yerine k·x yazar (desen).
    static func scaleX(_ label: String, _ k: Int) -> String {
        if k == 1 { return label }
        guard let supK = superscript(String(k)) else { return label }
        let lb = Array(label)
        var sb: [Character] = []
        var i = 0
        while i < lb.count {
            let c = lb[i]
            if c == "ˣ" { sb += Array(supK); sb.append("ˣ"); i += 1; continue }
            let prevLetter = i > 0 && lb[i - 1].isLetter && lb[i - 1] != "π"
            let nextLetter = i + 1 < lb.count && lb[i + 1].isLetter
            if c != "x" || prevLetter || nextLetter { sb.append(c); i += 1; continue }
            var j = sb.count
            while j > 0 && (sb[j - 1].isDigitK || sb[j - 1] == ",") { j -= 1 }
            let coef = String(sb[j...])
            let next: Character? = i + 1 < lb.count ? lb[i + 1] : nil
            let prev = sb.last
            if let f = coef.first, f.isDigitK {
                let v = Double(coef.replacingOccurrences(of: ",", with: "."))
                sb.removeSubrange(j...)
                let text: String? = v.map { pr in
                    let product = pr * Double(k)
                    return product == product.rounded(.toNearestOrEven) ? String(Int64(product)) : "\(product)".replacingOccurrences(of: ".", with: ",")
                }
                sb += Array(text == nil ? coef + String(k) : text == "1" ? "" : text!)
                sb.append("x")
            } else if (next != nil && (SUP_TO.contains(next!) || next == "^")) || prev == "√" || prev == "∛" || prev == "/" {
                sb += Array("(\(k)x)")
            } else {
                sb += Array("\(k)x")
            }
            i += 1
        }
        return String(sb)
    }
}
