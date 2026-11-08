import Foundation

/// v45 formül hesabının kaynak işaretleri (Android `FormulaEval`). Hesaplama (`annotate`) matematik çekirdeği
/// (MathExpr/ExactEval) taşındığında eklenir; işaretleri temizleme burada, çünkü yuva düzenleme bunlara dayanır.
enum FormulaEval {
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
}
