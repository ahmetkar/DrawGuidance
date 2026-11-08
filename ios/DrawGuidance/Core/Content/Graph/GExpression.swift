import Foundation

/// Grafik formüllerinin ayrıştırıcısı ve hesaplayıcısı (Android graph `Expression`): `y = x^2 - 3`, `x² + y² = 9`.
enum GExpression {
    /// Tanımsız noktalarda NaN (ya da sonsuz) döner.
    typealias Expr = (Double, Double) -> Double

    static let FUNCTIONS: Set<String> = [
        "sin", "cos", "tan", "tg", "cot", "cotg", "sec", "csc", "asin", "acos", "atan", "arcsin", "arccos", "arctan",
        "sinh", "cosh", "tanh", "sqrt", "cbrt", "abs", "exp", "ln", "log", "floor", "ceil", "round", "sign", "sgn",
    ]
    static let CONSTANTS: Set<String> = ["pi", "e"]
    static let VARIABLES: Set<String> = ["x", "y"]
    private static let NAMES: [String] = Array(FUNCTIONS.union(CONSTANTS).union(VARIABLES)).sorted { $0.count != $1.count ? $0.count > $1.count : $0 < $1 }

    /// [s] içinde [i] konumundan başlayan en uzun bilinen ad.
    static func matchName(_ s: [Character], _ i: Int) -> String? { NAMES.first { s.starts(with: $0, at: i) } }

    private static let PREFIXES: Set<String> = ["y", "z", "f", "f(x)", "g(x)", "h(x)", "y(x)", "f(x,y)", "z(x,y)", "g(x,y)"]

    private static func split(_ text: String) -> (String, String)? {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let i = t.firstIndex(of: "="), i > t.startIndex else { return nil }
        let head = String(t[..<i])
        if PREFIXES.contains(head.trimmingCharacters(in: .whitespaces).lowercased().replacingOccurrences(of: " ", with: "")) {
            return (head.trimmingCharacters(in: .whitespaces), String(t[t.index(after: i)...]))
        }
        return nil
    }

    /// `y = ...` / `z = ...` / `f(x) = ...` gibi ön ekleri atar.
    static func body(_ text: String) -> String { split(text)?.1 ?? text.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// [body] tarafından atılan ön ek; yoksa nil.
    static func prefix(_ text: String) -> String? { split(text)?.0 }

    /// Kapalı denklem mi?
    static func isImplicit(_ text: String) -> Bool { body(text).contains("=") }

    static func parse(_ text: String, _ mode: AngleMode = .RAD) -> Expr? {
        let source = body(text)
        if source.allSatisfy(\.isWhitespace) || source.contains(where: { "=<>≤≥≠".contains($0) }) { return nil }
        return parseNormalized(source, mode)
    }

    /// Kapalı denklem `sol = sağ` için F(x, y) = sol − sağ.
    static func parseImplicit(_ text: String, _ mode: AngleMode = .RAD) -> Expr? {
        let b = body(text)
        let parts = b.split(separator: "=", omittingEmptySubsequences: false)
        if parts.count != 2 { return nil }
        guard let l = parseNormalized(String(parts[0]), mode), let r = parseNormalized(String(parts[1]), mode) else { return nil }
        return { x, y in l(x, y) - r(x, y) }
    }

    /// v45: formülün hatası (nil: geçerli).
    static func error(_ text: String, allowY: Bool = true) -> MathError? {
        let b = body(text)
        let parts = isImplicit(text) ? b.split(separator: "=", omittingEmptySubsequences: false).map(String.init) : [b]
        if parts.count > 2 { return MathError(code: .RELATION) }
        for part in parts {
            let (node, err) = MathExpr.tryParse(part.lowercased())
            if let err { return err }
            let allowed = allowY || parts.count == 2 ? VARIABLES : ["x"]
            if let bad = node!.variables().first(where: { !allowed.contains($0) }) { return MathError(code: .UNKNOWN_NAME, pos: -1, detail: bad) }
        }
        return nil
    }

    private static func parseNormalized(_ source: String, _ mode: AngleMode) -> Expr? {
        if source.allSatisfy(\.isWhitespace) { return nil }
        guard let node = try? MathExpr.parse(source.lowercased()) else { return nil }
        if node.variables().contains(where: { !VARIABLES.contains($0) }) { return nil }
        guard let f = try? MathExpr.compile(node, ["x", "y"], mode) else { return nil }
        return { x, y in f([x, y]) }
    }

    private static let SUPERSCRIPTS = Array("⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺ˣʸⁿ⁽⁾")
    private static let SUPER_PLAIN = Array("0123456789-+xyn()")

    /// Unicode yazımları ve Türkçe ondalık virgülü ayrıştırıcının ASCII diline çevirir.
    static func normalize(_ text: String) -> String {
        let t = Array(text.lowercased())
        var sb = ""
        var i = 0
        while i < t.count {
            let ch = t[i]
            if SUPERSCRIPTS.contains(ch) {
                sb += "^("
                while i < t.count, let k = SUPERSCRIPTS.firstIndex(of: t[i]) { sb.append(SUPER_PLAIN[k]); i += 1 }
                sb += ")"
                continue
            }
            switch ch {
            case ",": sb += "."
            case "−", "–", "—": sb += "-"
            case "×", "·", "⋅", "∙": sb += "*"
            case "÷", ":": sb += "/"
            case "π": sb += "pi"
            case "√": sb += "sqrt"
            case "∛": sb += "cbrt"
            case "⌊", "⟦": sb += "floor("
            case "⌈": sb += "ceil("
            case "⌋", "⌉", "⟧": sb += ")"
            case "[", "{": sb += "("
            case "]", "}": sb += ")"
            default: sb.append(ch)
            }
            i += 1
        }
        return sb
    }

    static func powReal(_ b: Double, _ e: Double) -> Double { MathExpr.powReal(b, e) }
}
