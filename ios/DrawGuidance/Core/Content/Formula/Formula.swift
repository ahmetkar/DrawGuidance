import Foundation

/// Formül dizgi motoru (Android `Formula`). Kaynak bir LaTeX alt kümesidir; düzenlenebilir kısımlar
/// `\slot{ad}{varsayılan}` ile işaretlenir.
enum Formula {
    /// Formülü dizer; bozuk girdi en iyi çabayla (gerekirse düz metin) dizilir.
    static func layout(_ source: String, _ size: Float, _ metrics: GlyphMetrics) -> FormulaLayout {
        let sz: Float = size.isFinite && size > 0 ? size : 16
        let root = FormulaParser(source).parse()
        let l = Typesetter(metrics, sz).layoutRoot(root)
        return valid(l) ? l : fallback(source, sz, metrics)
    }

    /// Kaynaktaki yuvalar, ilk görünüş sırasıyla ve ad başına bir kez.
    static func slots(_ source: String) -> [Slot] {
        let a = Array(source)
        var seen = Set<String>()
        var out: [Slot] = []
        for o in SlotSource.scan(a) where !o.name.isEmpty && !seen.contains(o.name) {
            seen.insert(o.name)
            out.append(Slot(name: o.name, value: a.str(o.valueStart, o.valueEnd)))
        }
        return out
    }

    static func setSlot(_ source: String, _ name: String, _ value: String) -> String { SlotSource.set(source, name, value) }
    static func atomize(_ source: String) -> String { Atomizer.atomize(source) }
    static func isAtomized(_ source: String) -> Bool { Atomizer.atomize(source) == source }
    static func plain(_ source: String) -> String { Atomizer.plain(source) }
    static func plainText(_ source: String) -> String { PlainText.of(FormulaParser(source).parse()) }

    private static func valid(_ l: FormulaLayout) -> Bool {
        l.width.isFinite && l.ascent.isFinite && l.descent.isFinite && l.ops.allSatisfy(\.isFinite)
    }

    private static func fallback(_ source: String, _ size: Float, _ metrics: GlyphMetrics) -> FormulaLayout {
        let text = source.trimmingCharacters(in: .whitespacesAndNewlines)
        let w0 = metrics.advance(text, size, .ROMAN), a0 = metrics.ascent(size), d0 = metrics.descent(size)
        return FormulaLayout(width: w0.isFinite ? w0 : 0, ascent: a0.isFinite ? a0 : size, descent: d0.isFinite ? d0 : size * 0.25,
                             ops: text.isEmpty ? [] : [.glyph(text: text, x: 0, y: 0, size: size, style: .ROMAN, slot: nil)], slots: [])
    }
}

// MARK: - v17: elemanlarına ayırma

enum Atomizer {
    private static let greek: Set<String> = [
        "alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "zeta", "eta", "theta", "vartheta", "iota", "kappa",
        "lambda", "mu", "nu", "xi", "pi", "varpi", "rho", "varrho", "sigma", "varsigma", "tau", "upsilon", "phi", "varphi",
        "chi", "psi", "omega", "Gamma", "Delta", "Theta", "Lambda", "Xi", "Pi", "Sigma", "Upsilon", "Phi", "Psi", "Omega",
    ]
    private static let verbatim: Set<String> = [
        "text", "textrm", "mbox", "textnormal", "hbox", "textup", "textit", "textbf", "operatorname",
        "begin", "end", "mathrm", "mathup", "mathsf", "mathtt", "mathbb", "mathcal", "mathscr", "label", "color",
    ]
    private static let delimiters: Set<String> = [
        "left", "right", "middle", "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr", "biggl", "biggr",
    ]

    static func atomize(_ source: String) -> String {
        let s = Array(source)
        var out = ""
        var n = 0
        func slot(_ value: String) { n += 1; out += "\\slot{\(n)}{\(value)}" }
        func groupEnd(_ from: Int) -> Int {
            var k = from
            while k < s.count && s[k].isWhitespace { k += 1 }
            if k >= s.count || s[k] != "{" { return -1 }
            var d = 0
            while k < s.count {
                let c = s[k]
                if c == "\\" && k + 1 < s.count { k += 2; continue }
                if c == "{" { d += 1 }
                if c == "}" { d -= 1; if d == 0 { return k + 1 } }
                k += 1
            }
            return s.count
        }
        func oneArg(_ from: Int) -> (String, Int) {
            var k = from
            while k < s.count && s[k].isWhitespace { k += 1 }
            if k >= s.count { return ("", k) }
            if s[k] == "{" {
                let e = groupEnd(k)
                return (s.str(k + 1, max(e - 1, k + 1)), e)
            }
            if s[k] == "\\" {
                var e = k + 1
                if e < s.count && s[e].isAsciiLetterK { while e < s.count && s[e].isAsciiLetterK { e += 1 } } else { e = min(e + 1, s.count) }
                return (s.str(k, e), e)
            }
            return (String(s[k]), k + 1)
        }
        let mark = Array(FormulaEval.RESULT_MARK)
        var i = 0
        while i < s.count {
            let c = s[i]
            if c == "\\" && s.starts(with: String(mark), at: i) { out += s.str(i, s.count); break }
            if c == "\\" {
                let st = i
                i += 1
                if i >= s.count { out.append(c); break }
                if !s[i].isAsciiLetterK { out += s.str(st, i + 1); i += 1; continue }
                while i < s.count && s[i].isAsciiLetterK { i += 1 }
                let name = s.str(st + 1, i)
                if name == "slot" {
                    let (_, afterName) = oneArg(i)
                    let (value, afterValue) = oneArg(afterName)
                    slot(value)
                    i = afterValue
                } else if greek.contains(name) {
                    slot("\\" + name)
                } else if verbatim.contains(name) {
                    out += s.str(st, i)
                    if name == "operatorname" && i < s.count && s[i] == "*" { out += "*"; i += 1 }
                    let e = groupEnd(i)
                    if e > 0 { out += s.str(i, e); i = e }
                } else if delimiters.contains(name) {
                    out += s.str(st, i)
                    var k = i
                    while k < s.count && s[k].isWhitespace { k += 1 }
                    if k < s.count {
                        let (d, e) = oneArg(k)
                        out += s.str(i, k) + (s[k] == "{" ? "{\(d)}" : d)
                        if d.hasPrefix("\\") && d.dropFirst().allSatisfy(\.isAsciiLetterK) && e < s.count && s[e].isAsciiLetterK { out += " " }
                        i = e
                    }
                } else {
                    out += s.str(st, i)
                    if i < s.count && s[i].isAsciiLetterK { out += " " }
                }
            } else if c == "^" || c == "_" {
                out.append(c)
                i += 1
                var k = i
                while k < s.count && s[k].isWhitespace { k += 1 }
                if k < s.count && s[k] != "{" && s[k] != "\\" {
                    let single = s[k]
                    out += "{"
                    if single.isAsciiLetterK || single.isDigitK { slot(String(single)) } else { out.append(single) }
                    out += "}"
                    i = k + 1
                }
            } else if c.isDigitK {
                var e = i
                while e < s.count && s[e].isDigitK { e += 1 }
                if e + 1 < s.count && s[e] == "." && s[e + 1].isDigitK {
                    e += 1
                    while e < s.count && s[e].isDigitK { e += 1 }
                }
                slot(s.str(i, e))
                i = e
            } else if c.isAsciiLetterK {
                slot(String(c)); i += 1
            } else {
                out.append(c); i += 1
            }
        }
        return out
    }

    private static func isCommandWord(_ v: [Character]) -> Bool { v.first == "\\" && v.dropFirst().allSatisfy(\.isAsciiLetterK) }

    static func plain(_ source0: String) -> String {
        let source = Array(FormulaEval.stripResult(source0))
        let occ = SlotSource.scan(source)
        if occ.isEmpty { return String(source) }
        var sb = ""
        var last = 0
        var outer: [SlotSource.Occ] = []
        for o in occ.sorted(by: { $0.start < $1.start }) where !outer.contains(where: { o.start >= $0.start && o.start < $0.valueEnd + 1 }) {
            outer.append(o)
        }
        for o in outer {
            sb += source.str(last, o.start)
            let value = Array(source[o.valueStart..<o.valueEnd])
            let end = o.braced && o.closed ? o.valueEnd + 1 : o.valueEnd
            let simple = value.count == 1 || value.allSatisfy { $0.isDigitK || $0 == "." } || isCommandWord(value)
            sb += simple ? String(value) : "{\(String(value))}"
            if isCommandWord(value) && end < source.count {
                let next = source[end]
                if next.isAsciiLetterK || next == "\\" { sb += " " }
            }
            last = end
        }
        sb += source.str(last, source.count)
        return sb
    }
}

// MARK: - Kaynak düzeyinde yuvalar

enum SlotSource {
    struct Occ { let name: String; let start: Int; let valueStart: Int; let valueEnd: Int; let braced: Bool; let closed: Bool }
    private struct Arg { let innerStart: Int; let innerEnd: Int; let after: Int; let braced: Bool; let closed: Bool }

    private static func arg(_ s: [Character], _ from: Int) -> Arg? {
        var i = from
        while i < s.count && s[i].isWhitespace { i += 1 }
        if i >= s.count { return nil }
        let c = s[i]
        if c == "{" {
            let st = i + 1
            var k = st, d = 1
            while k < s.count {
                let ch = s[k]
                if ch == "\\" && k + 1 < s.count { k += 2; continue }
                if ch == "{" { d += 1 }
                if ch == "}" { d -= 1; if d == 0 { return Arg(innerStart: st, innerEnd: k, after: k + 1, braced: true, closed: true) } }
                k += 1
            }
            return Arg(innerStart: st, innerEnd: s.count, after: s.count, braced: true, closed: false)
        }
        if c == "}" { return nil }
        if c == "\\" {
            var k = i + 1
            if k < s.count && s[k].isAsciiLetterK { while k < s.count && s[k].isAsciiLetterK { k += 1 } } else { k = min(k + 1, s.count) }
            return Arg(innerStart: i, innerEnd: k, after: k, braced: false, closed: true)
        }
        return Arg(innerStart: i, innerEnd: i + 1, after: i + 1, braced: false, closed: true)
    }

    static func scan(_ s: [Character]) -> [Occ] {
        var out: [Occ] = []
        var i = 0
        while i < s.count {
            if s[i] != "\\" { i += 1; continue }
            let st = i
            i += 1
            if i >= s.count { break }
            if !s[i].isAsciiLetterK { i += 1; continue }
            let ns = i
            while i < s.count && s[i].isAsciiLetterK { i += 1 }
            if i - ns == 4 && s.starts(with: "slot", at: ns) {
                guard let n = arg(s, i), let v = arg(s, n.after) else { continue }
                out.append(Occ(name: s.str(n.innerStart, n.innerEnd).trimmingCharacters(in: .whitespacesAndNewlines), start: st,
                               valueStart: v.innerStart, valueEnd: v.innerEnd, braced: v.braced, closed: v.closed))
                i = v.braced ? v.innerStart : v.after
            }
        }
        return out
    }

    static func set(_ source: String, _ name: String, _ value: String) -> String {
        var a = Array(source)
        let occ = scan(a).filter { $0.name == name }
        if occ.isEmpty { return source }
        var chosen: [Occ] = []
        for o in occ.sorted(by: { $0.start < $1.start }) where !chosen.contains(where: { o.start >= $0.valueStart && o.start < $0.valueEnd }) {
            chosen.append(o)
        }
        let v = sanitize(value)
        for o in chosen.sorted(by: { $0.valueStart > $1.valueStart }) {
            let rep = !o.braced ? "{\(v)}" : !o.closed ? "\(v)}" : v
            a.replaceSubrange(o.valueStart..<o.valueEnd, with: Array(rep))
        }
        return String(a)
    }

    /// Değerin kaynağı bozmaması için: dengesiz parantezler dengelenir, sondaki tek `\` atılır.
    static func sanitize(_ value: String) -> String {
        var v = Array(value)
        var trailing = 0
        while trailing < v.count && v[v.count - 1 - trailing] == "\\" { trailing += 1 }
        if trailing % 2 == 1 { v.removeLast() }
        var sb = ""
        var depth = 0, k = 0
        while k < v.count {
            let c = v[k]
            if c == "\\" && k + 1 < v.count { sb.append(c); sb.append(v[k + 1]); k += 2; continue }
            if c == "{" { depth += 1; sb.append(c) } else if c == "}" { if depth > 0 { depth -= 1; sb.append(c) } } else { sb.append(c) }
            k += 1
        }
        sb += String(repeating: "}", count: depth)
        return sb
    }
}

// MARK: - Düz metin

enum PlainText {
    private final class Tok {
        let s: String; var left: AtomType; var right: AtomType; let space: Bool
        init(_ s: String, _ l: AtomType, _ r: AtomType, _ space: Bool = false) { self.s = s; left = l; right = r; self.space = space }
    }

    static func of(_ root: RowNode) -> String {
        row(root.items).components(separatedBy: .whitespacesAndNewlines).filter { !$0.isEmpty }.joined(separator: " ")
    }

    private static let UNARY_BEFORE: [AtomType] = [.BIN, .OP, .REL, .OPEN, .PUNCT]

    private static func row(_ items: [FNode]) -> String {
        let nodes = SlotRules.apply(items)
        var toks: [Tok] = []
        for n in nodes {
            if let sp = n as? SpaceNode { if sp.em >= 0.15 { toks.append(Tok(" ", .ORD, .ORD, true)) } }
            else if n is StyleNode {}
            else { toks.append(tok(n)) }
        }
        var prev: Tok? = nil
        for t in toks where !t.space {
            if t.left == .BIN, prev == nil || UNARY_BEFORE.contains(prev!.right) { t.left = .ORD; t.right = .ORD }
            if [.REL, .CLOSE, .PUNCT].contains(t.left), let p = prev, p.right == .BIN { p.left = .ORD; p.right = .ORD }
            prev = t
        }
        if let p = prev, p.right == .BIN { p.left = .ORD; p.right = .ORD }
        var sb = ""
        prev = nil
        for t in toks {
            if t.space { sb += " "; continue }
            if t.s.isEmpty { continue }
            if let p = prev {
                let spaced = t.left == .BIN || t.left == .REL || p.right == .BIN || p.right == .REL || p.right == .PUNCT ||
                    (p.right == .OP && t.left != .OPEN && t.left != .PUNCT && t.left != .CLOSE) ||
                    (t.left == .OP && p.right != .OPEN) ||
                    (t.left == .INNER && p.right != .OPEN) || (p.right == .INNER && t.left != .CLOSE && t.left != .PUNCT)
                if spaced { sb += " " }
            }
            sb += t.s
            prev = t
        }
        return sb
    }

    private static func str(_ n: FNode) -> String { tok(n).s }

    private static func tok(_ n: FNode) -> Tok {
        switch n {
        case let s as SymNode: return s.kind == SymNode.K_AUTO_DOT ? Tok(s.text, .ORD, .ORD) : Tok(s.text, s.type, s.type)
        case let r as RowNode: return Tok(row(r.items), .ORD, .ORD)
        case let s as ScriptsNode:
            let base = tok(s.base)
            return Tok(scripts(s, base.s), base.left, base.right)
        case let f as FracNode: return Tok(frac(f), .INNER, .INNER)
        case let q as SqrtNode: return Tok(sqrt(q), .ORD, .ORD)
        case let a as AccentNode: return Tok(accent(a), .ORD, .ORD)
        case let f as FenceNode: return Tok(fence(f), .OPEN, .CLOSE)
        case let m as MatrixNode: return Tok(matrix(m), .ORD, .ORD)
        case let t as TextNode: return Tok(t.text.replacingOccurrences(of: "\u{00A0}", with: " "), .ORD, .ORD)
        case let s as SlotNode:
            let inner = s.body.items.count == 1 ? tok(s.body.items[0]) : nil
            return Tok(row(s.body.items), s.type ?? inner?.left ?? .ORD, s.type ?? inner?.right ?? .ORD)
        case let b as BigOpNode: return Tok(BIG_OPS[b.kind] ?? "∑", .OP, .OP)
        case let f as FuncNode: return Tok(f.name, .OP, .OP)
        case let d as DotsNode:
            let t: String
            switch d.kind { case "c": t = "⋯"; case "v": t = "⋮"; case "d": t = "⋱"; default: t = "…" }
            return Tok(t, .INNER, .INNER)
        case is SpaceNode: return Tok(" ", .ORD, .ORD, true)
        default: return Tok("", .ORD, .ORD)
        }
    }

    private static func isDegree(_ n: FNode?) -> Bool {
        let s = (n as? RowNode).map { $0.items.count == 1 ? $0.items[0] : nil } ?? n
        guard let sym = s as? SymNode else { return false }
        return sym.text == "∘" || sym.text == "°"
    }

    private static func scripts(_ n: ScriptsNode, _ baseStr: String) -> String {
        var sb = n.base is FracNode ? "(\(baseStr))" : baseStr
        sb += String(repeating: "′", count: n.primes)
        if let sub = n.sub { sb += convert(str(sub), SUB, "_") }
        if let sup = n.sup { sb += isDegree(sup) ? "°" : convert(str(sup), SUP, "^") }
        return sb
    }

    private static func convert(_ s: String, _ map: [Character: Character], _ mark: String) -> String {
        if s.isEmpty { return "" }
        if s.allSatisfy({ map[$0] != nil }) { return String(s.map { map[$0]! }) }
        return s.unicodeScalars.count == 1 ? mark + s : "\(mark)(\(s))"
    }

    private static func simple(_ s: String) -> Bool { !s.contains(" ") }

    private static func frac(_ n: FracNode) -> String {
        let a = str(n.num), b = str(n.den)
        if !n.bar { return "\(a), \(b)" }
        if simple(a) && simple(b) { return "\(a)/\(b)" }
        return "\(simple(a) ? a : "(\(a))") / \(simple(b) ? b : "(\(b))")"
    }

    private static func sqrt(_ n: SqrtNode) -> String {
        let b = str(n.body)
        let body = b.unicodeScalars.count <= 1 || b.allSatisfy(\.isDigitK) ? b : "(\(b))"
        let idx = n.index.map { str($0) }
        let sign: String
        switch idx {
        case nil, "", "2": sign = "√"
        case "3": sign = "∛"
        case "4": sign = "∜"
        default: sign = convert(idx!, SUP, "^") + "√"
        }
        return sign + body
    }

    private static func accent(_ n: AccentNode) -> String {
        let b = str(n.body)
        let mark: String
        switch n.kind {
        case .VEC: mark = "\u{20D7}"
        case .LEFT_VEC: mark = "\u{20D6}"
        case .OVERLINE: mark = "\u{0305}"
        case .UNDERLINE: mark = "\u{0332}"
        case .BAR: mark = "\u{0304}"
        case .HAT, .WIDEHAT: mark = "\u{0302}"
        case .DOT: mark = "\u{0307}"
        case .DDOT: mark = "\u{0308}"
        case .TILDE: mark = "\u{0303}"
        }
        if b.isEmpty { return "" }
        let perChar = n.kind == .OVERLINE || n.kind == .UNDERLINE || n.kind == .BAR
        return perChar ? b.unicodeScalars.map { String($0) + mark }.joined() : b + mark
    }

    private static func fence(_ n: FenceNode) -> String {
        if n.binom, n.body.items.count == 1, let f = n.body.items[0] as? FracNode { return "C(\(str(f.num)), \(str(f.den)))" }
        let l = n.left == "." ? "" : n.left, r = n.right == "." ? "" : n.right
        return l + row(n.body.items) + r
    }

    private static let MATRIX_ENVS: Set<String> = ["matrix", "pmatrix", "bmatrix", "Bmatrix", "vmatrix", "Vmatrix", "smallmatrix", "array"]

    private static func matrix(_ n: MatrixNode) -> String {
        let cases = n.env == "cases" || n.env == "dcases" || n.env == "rcases"
        let aligned = !MATRIX_ENVS.contains(n.env)
        let sep = cases || aligned ? " " : ", "
        let body = n.rows.map { r in
            r.map { row($0.items).trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty || !aligned }.joined(separator: sep)
        }.joined(separator: "; ")
        switch n.env {
        case "pmatrix": return "(\(body))"
        case "bmatrix": return "[\(body)]"
        case "Bmatrix": return "{\(body)}"
        case "vmatrix": return "|\(body)|"
        case "Vmatrix": return "‖\(body)‖"
        case "cases", "dcases": return "{ \(body) }"
        default: return body
        }
    }

    private static let BIG_OPS: [String: String] = [
        "sum": "∑", "prod": "∏", "coprod": "∐", "int": "∫", "iint": "∬", "iiint": "∭", "oint": "∮",
        "oiint": "∯", "bigcup": "⋃", "bigcap": "⋂",
    ]

    private static let SUP = zip("0123456789+−-=()niabcdefghjklmoprstuvwxyz", "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁻⁼⁽⁾ⁿⁱᵃᵇᶜᵈᵉᶠᵍʰʲᵏˡᵐᵒᵖʳˢᵗᵘᵛʷˣʸᶻ")
    private static let SUB = zip("0123456789+−-=()aeoxhklmnpstijruv,", "₀₁₂₃₄₅₆₇₈₉₊₋₋₌₍₎ₐₑₒₓₕₖₗₘₙₚₛₜᵢⱼᵣᵤᵥ,")

    private static func zip(_ a: String, _ b: String) -> [Character: Character] {
        var m: [Character: Character] = [:]
        for (x, y) in Swift.zip(a, b) { m[x] = y }
        return m
    }
}
