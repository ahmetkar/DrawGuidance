import Foundation

/// TeX atom sınıfları.
enum AtomType: Int { case ORD, OP, BIN, REL, OPEN, CLOSE, PUNCT, INNER }

// MARK: - Sözdizim ağacı (Android `Node` alt sınıfları)

class FNode {}

final class SymNode: FNode {
    static let K_OTHER = 0, K_LETTER = 1, K_DIGITS = 2, K_AUTO_DOT = 3
    let text: String, type: AtomType, style: GlyphStyle, kind: Int, pad: Float
    init(_ text: String, _ type: AtomType, _ style: GlyphStyle, kind: Int = 0, pad: Float = 0) {
        self.text = text; self.type = type; self.style = style; self.kind = kind; self.pad = pad
    }
}
final class RowNode: FNode { let items: [FNode]; init(_ items: [FNode]) { self.items = items } }
final class ScriptsNode: FNode {
    let base: FNode; var sup: FNode?; var sub: FNode?; var primes: Int
    init(_ base: FNode, _ sup: FNode?, _ sub: FNode?, _ primes: Int = 0) { self.base = base; self.sup = sup; self.sub = sub; self.primes = primes }
}
final class FracNode: FNode {
    let num: FNode, den: FNode, forced: Int, bar: Bool
    init(_ num: FNode, _ den: FNode, forced: Int = -1, bar: Bool = true) { self.num = num; self.den = den; self.forced = forced; self.bar = bar }
}
final class SqrtNode: FNode { let body: FNode, index: FNode?; init(_ b: FNode, _ i: FNode?) { body = b; index = i } }
enum AccentKind { case VEC, LEFT_VEC, OVERLINE, UNDERLINE, BAR, HAT, WIDEHAT, DOT, DDOT, TILDE }
final class AccentNode: FNode { let kind: AccentKind, body: FNode; init(_ k: AccentKind, _ b: FNode) { kind = k; body = b } }
final class FenceNode: FNode {
    let left: String, body: RowNode, right: String, auto: Bool, binom: Bool
    init(_ l: String, _ b: RowNode, _ r: String, auto: Bool, binom: Bool = false) { left = l; body = b; right = r; self.auto = auto; self.binom = binom }
}
final class MatrixNode: FNode {
    let env: String, rows: [[RowNode]], colSpec: String?
    init(_ env: String, _ rows: [[RowNode]], _ spec: String? = nil) { self.env = env; self.rows = rows; colSpec = spec }
}
final class TextNode: FNode { let text: String, style: GlyphStyle; init(_ t: String, _ s: GlyphStyle) { text = t; style = s } }
final class SpaceNode: FNode { let em: Float; init(_ em: Float) { self.em = em } }
final class SlotNode: FNode {
    let name: String, body: RowNode, type: AtomType?
    init(_ name: String, _ body: RowNode, _ type: AtomType? = nil) { self.name = name; self.body = body; self.type = type }
}
final class StyleNode: FNode { let level: Int; init(_ l: Int) { level = l } }
final class BigOpNode: FNode { let kind: String; var limits: Bool?; init(_ k: String) { kind = k } }
final class FuncNode: FNode {
    let name: String, limitsDefault: Bool; var limits: Bool?
    init(_ n: String, _ d: Bool) { name = n; limitsDefault = d }
}
final class DotsNode: FNode { let kind: Character; init(_ k: Character) { kind = k } }

// MARK: - Simgeler

enum MathSymbols {
    struct Sym { let text: String; let type: AtomType }

    static let greek: [String: String] = [
        "alpha": "α", "beta": "β", "gamma": "γ", "delta": "δ", "epsilon": "ε", "varepsilon": "ε",
        "zeta": "ζ", "eta": "η", "theta": "θ", "vartheta": "ϑ", "iota": "ι", "kappa": "κ",
        "lambda": "λ", "mu": "μ", "nu": "ν", "xi": "ξ", "omicron": "ο", "pi": "π", "varpi": "ϖ",
        "rho": "ρ", "varrho": "ϱ", "sigma": "σ", "varsigma": "ς", "tau": "τ", "upsilon": "υ",
        "phi": "φ", "varphi": "φ", "chi": "χ", "psi": "ψ", "omega": "ω",
        "Gamma": "Γ", "Delta": "Δ", "Theta": "Θ", "Lambda": "Λ", "Xi": "Ξ", "Pi": "Π",
        "Sigma": "Σ", "Upsilon": "Υ", "Phi": "Φ", "Psi": "Ψ", "Omega": "Ω",
    ]

    static let commands: [String: Sym] = {
        var m: [String: Sym] = [:]
        func put(_ t: AtomType, _ text: String, _ names: String...) { for n in names { m[n] = Sym(text: text, type: t) } }
        put(.BIN, "±", "pm"); put(.BIN, "∓", "mp"); put(.BIN, "×", "times"); put(.BIN, "÷", "div")
        put(.BIN, "·", "cdot"); put(.BIN, "∗", "ast"); put(.BIN, "⋆", "star"); put(.BIN, "∘", "circ")
        put(.BIN, "•", "bullet"); put(.BIN, "∪", "cup"); put(.BIN, "∩", "cap"); put(.BIN, "∖", "setminus")
        put(.BIN, "∧", "wedge", "land"); put(.BIN, "∨", "vee", "lor"); put(.BIN, "⊕", "oplus"); put(.BIN, "⊖", "ominus")
        put(.BIN, "⊗", "otimes"); put(.BIN, "⊙", "odot")
        put(.REL, "≤", "le", "leq", "leqslant"); put(.REL, "≥", "ge", "geq", "geqslant"); put(.REL, "≠", "ne", "neq")
        put(.REL, "≈", "approx"); put(.REL, "≡", "equiv"); put(.REL, "∼", "sim"); put(.REL, "≃", "simeq")
        put(.REL, "≅", "cong"); put(.REL, "∝", "propto"); put(.REL, "→", "to", "rightarrow", "longrightarrow")
        put(.REL, "←", "leftarrow", "gets", "longleftarrow"); put(.REL, "↔", "leftrightarrow", "longleftrightarrow")
        put(.REL, "⇒", "Rightarrow", "implies", "Longrightarrow"); put(.REL, "⇐", "Leftarrow", "Longleftarrow")
        put(.REL, "⇔", "Leftrightarrow", "iff", "Longleftrightarrow"); put(.REL, "↦", "mapsto")
        put(.REL, "∈", "in"); put(.REL, "∉", "notin"); put(.REL, "∋", "ni"); put(.REL, "⊂", "subset")
        put(.REL, "⊆", "subseteq"); put(.REL, "⊃", "supset"); put(.REL, "⊇", "supseteq"); put(.REL, "⊄", "not\\subset")
        put(.REL, "⊥", "perp"); put(.REL, "∥", "parallel"); put(.REL, "|", "mid"); put(.REL, "∤", "nmid")
        put(.REL, "≪", "ll"); put(.REL, "≫", "gg"); put(.REL, "↑", "uparrow"); put(.REL, "↓", "downarrow")
        put(.REL, "≐", "doteq"); put(.REL, "<", "lt"); put(.REL, ">", "gt"); put(.REL, "≔", "coloneqq")
        put(.ORD, "∞", "infty"); put(.ORD, "∂", "partial"); put(.ORD, "∇", "nabla"); put(.ORD, "∀", "forall")
        put(.ORD, "∃", "exists"); put(.ORD, "∄", "nexists"); put(.ORD, "∅", "emptyset", "varnothing")
        put(.ORD, "∠", "angle"); put(.ORD, "∡", "measuredangle"); put(.ORD, "△", "triangle"); put(.ORD, "□", "square")
        put(.ORD, "°", "degree"); put(.ORD, "′", "prime"); put(.ORD, "ℏ", "hbar"); put(.ORD, "ℓ", "ell")
        put(.ORD, "¬", "neg", "lnot"); put(.ORD, "ℵ", "aleph"); put(.ORD, "ℜ", "Re"); put(.ORD, "ℑ", "Im")
        put(.ORD, "∴", "therefore"); put(.ORD, "∵", "because"); put(.ORD, "⊤", "top"); put(.ORD, "⊥", "bot")
        put(.ORD, "\\", "backslash"); put(.ORD, "†", "dagger"); put(.ORD, "‖", "|", "Vert"); put(.ORD, "|", "vert")
        put(.ORD, "%", "%"); put(.ORD, "#", "#"); put(.ORD, "&", "&"); put(.ORD, "_", "_"); put(.ORD, "$", "$")
        put(.ORD, "✓", "checkmark"); put(.ORD, "√", "surd")
        put(.OPEN, "{", "{", "lbrace"); put(.OPEN, "⟨", "langle"); put(.OPEN, "⌊", "lfloor"); put(.OPEN, "⌈", "lceil")
        put(.OPEN, "[", "lbrack"); put(.OPEN, "|", "lvert"); put(.OPEN, "‖", "lVert")
        put(.CLOSE, "}", "}", "rbrace"); put(.CLOSE, "⟩", "rangle"); put(.CLOSE, "⌋", "rfloor"); put(.CLOSE, "⌉", "rceil")
        put(.CLOSE, "]", "rbrack"); put(.CLOSE, "|", "rvert"); put(.CLOSE, "‖", "rVert")
        put(.PUNCT, ":", "colon"); put(.PUNCT, ".", "ldotp"); put(.PUNCT, "·", "cdotp")
        return m
    }()

    static let charTypes: [Character: AtomType] = {
        var m: [Character: AtomType] = [:]
        for c in "+−±∓×÷·∘∙•∪∩∧∨⊕⊗∖" { m[c] = .BIN }
        for c in "=<>:≤≥≠≈≡∼≃≅∝→←↔⇒⇐⇔↦∈∉∋⊂⊆⊃⊇⊥∥≪≫⩽⩾" { m[c] = .REL }
        for c in "([⟨⌊⌈" { m[c] = .OPEN }
        for c in ")]⟩⌋⌉!?" { m[c] = .CLOSE }
        for c in ",;" { m[c] = .PUNCT }
        return m
    }()

    static let functions: [String: Bool] = {
        var m: [String: Bool] = [:]
        for f in ["sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan", "arccot", "sinh", "cosh", "tanh",
                  "coth", "log", "ln", "lg", "exp", "deg", "dim", "ker", "hom", "arg", "tg", "ctg", "sgn", "lcm", "cosec"] { m[f] = false }
        for f in ["lim", "liminf", "limsup", "max", "min", "sup", "inf", "det", "gcd", "Pr"] { m[f] = true }
        return m
    }()

    static let functionText: [String: String] = ["liminf": "lim inf", "limsup": "lim sup"]

    static let bigOps: [String: String] = [
        "sum": "sum", "prod": "prod", "coprod": "coprod", "int": "int", "iint": "iint", "iiint": "iiint",
        "oint": "oint", "oiint": "oiint", "bigcup": "bigcup", "bigcap": "bigcap",
    ]
    static let bigOpChars: [Character: String] = [
        "∑": "sum", "∏": "prod", "∐": "coprod", "∫": "int", "∬": "iint", "∭": "iiint", "∮": "oint",
        "∯": "oiint", "⋃": "bigcup", "⋂": "bigcap",
    ]

    static let superscripts = pairs("⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱ", "0123456789+-=()ni")
    static let subscripts = pairs("₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎ₐₑₒₓᵢⱼₙₘₖ", "0123456789+-=()aeoxijnmk")

    static func pairs(_ a: String, _ b: String) -> [Character: Character] {
        var m: [Character: Character] = [:]
        for (x, y) in zip(a, b) { m[x] = y }
        return m
    }

    static let doubleStruck: [Character: String] = ["R": "ℝ", "N": "ℕ", "Z": "ℤ", "Q": "ℚ", "C": "ℂ", "P": "ℙ", "H": "ℍ"]

    static let accents: [String: AccentKind] = [
        "vec": .VEC, "overrightarrow": .VEC, "overleftarrow": .LEFT_VEC, "overline": .OVERLINE, "underline": .UNDERLINE,
        "bar": .BAR, "hat": .HAT, "widehat": .WIDEHAT, "dot": .DOT, "ddot": .DDOT, "tilde": .TILDE, "widetilde": .TILDE,
    ]
}

// MARK: - Ayrıştırıcı

/// TeX alt kümesinin hataya dayanıklı ayrıştırıcısı (Android `FormulaParser`).
final class FormulaParser {
    private let s: [Character]
    fileprivate(set) var i = 0
    private var font = F_NONE
    private var depth = 0
    private var stopStart = 0

    private enum Stop { case EOF, BRACE, RIGHT, AMP, NEWLINE, END, BRACKET }

    private static let S_BRACE = 1, S_RIGHT = 2, S_CELL = 4, S_BRACKET = 8, S_END = 16, MAX_DEPTH = 48
    private static let F_NONE = 0, F_RM = 1, F_BF = 2, F_IT = 3, F_BI = 4, F_BB = 5
    static var EMPTY: RowNode { RowNode([]) }
    private static let STRUCTURAL: Set<String> = ["right", "\\", "end", "cr", "limits", "nolimits"]

    init(_ source: String) { s = Array(source) }

    func parse() -> RowNode {
        var rows: [[RowNode]] = []
        var cur: [RowNode] = []
        var multi = false
        while true {
            let (items, stop) = list(Self.S_CELL)
            cur.append(RowNode(items))
            if stop == .AMP { multi = true } else if stop == .NEWLINE { multi = true; rows.append(cur); cur = [] } else { break }
        }
        rows.append(cur)
        if !multi { return cur[0] }
        while rows.count > 1 && rows.last!.allSatisfy({ $0.items.isEmpty }) { rows.removeLast() }
        if rows.count == 1 && rows[0].count == 1 { return rows[0][0] }
        let aligned = rows.contains { $0.count > 1 }
        return RowNode([MatrixNode(aligned ? "aligned" : "gathered", rows)])
    }

    private func list(_ stops: Int) -> ([FNode], Stop) {
        var items: [FNode] = []
        let savedFont = font
        depth += 1
        defer { font = savedFont; depth -= 1 }
        while i < s.count {
            let c = s[i]
            if c.isWhitespace { i += 1 }
            else if c == "{" { i += 1; items.append(group()) }
            else if c == "}" {
                stopStart = i; i += 1
                if stops & Self.S_BRACE != 0 { return done(items, .BRACE) }
            } else if c == "]" && stops & Self.S_BRACKET != 0 { stopStart = i; i += 1; return done(items, .BRACKET) }
            else if c == "&" {
                stopStart = i; i += 1
                if stops & Self.S_CELL != 0 { return done(items, .AMP) }
            } else if c == "^" || c == "_" { i += 1; let a = arg(); script(&items, c == "^", a) }
            else if c == "'" {
                var n = 0
                while i < s.count && s[i] == "'" { n += 1; i += 1 }
                primes(&items, n)
            } else if c == "\\" {
                stopStart = i
                i += 1
                let name = self.name()
                switch name {
                case "right":
                    if stops & Self.S_RIGHT != 0 { return done(items, .RIGHT) }
                    _ = delimiter()
                case "\\", "cr", "newline":
                    optBracket()
                    if stops & Self.S_CELL != 0 { return done(items, .NEWLINE) }
                case "end":
                    _ = rawGroup()
                    if stops & Self.S_END != 0 { return done(items, .END) }
                case "limits", "nolimits": setLimits(items, name == "limits")
                case "rm": font = Self.F_RM
                case "bf": font = Self.F_BF
                case "it": font = Self.F_IT
                case "hline", "left.", "nonumber", "notag", "label": if name == "label" { _ = rawGroup() }
                default: if let n = command(name) { items.append(n) }
                }
            } else { chars(&items, false) }
        }
        return done(items, .EOF)
    }

    private func done(_ items: [FNode], _ stop: Stop) -> ([FNode], Stop) { (pairFences(items), stop) }

    private func group() -> FNode {
        if depth > Self.MAX_DEPTH {
            let st = i
            var d = 1
            while i < s.count && d > 0 {
                if s[i] == "{" { d += 1 } else if s[i] == "}" { d -= 1 }
                i += 1
            }
            return TextNode(s.str(st, max(i - 1, st)), .ROMAN)
        }
        return RowNode(list(Self.S_BRACE).0)
    }

    private func skipWs() { while i < s.count && s[i].isWhitespace { i += 1 } }

    private func name() -> String {
        if i >= s.count { return "" }
        if s[i].isAsciiLetterK {
            let st = i
            while i < s.count && s[i].isAsciiLetterK { i += 1 }
            return s.str(st, i)
        }
        i += 1
        return String(s[i - 1])
    }

    private func optBracket() {
        let save = i
        skipWs()
        if i < s.count && s[i] == "[" {
            let close = s.index(of: "]", from: i)
            if close > 0 && close - i < 16 { i = close + 1; return }
        }
        i = save
    }

    func rawGroup() -> String {
        skipWs()
        if i >= s.count { return "" }
        if s[i] != "{" {
            if s[i] == "\\" { i += 1; return "\\" + name() }
            i += 1
            return String(s[i - 1])
        }
        i += 1
        let st = i
        var d = 1
        while i < s.count {
            let c = s[i]
            if c == "\\" && i + 1 < s.count { i += 2; continue }
            if c == "{" { d += 1 }
            if c == "}" { d -= 1; if d == 0 { break } }
            i += 1
        }
        let out = s.str(st, min(i, s.count))
        if i < s.count { i += 1 }
        return out
    }

    private func arg() -> FNode {
        skipWs()
        if i >= s.count { return Self.EMPTY }
        switch s[i] {
        case "{": i += 1; return group()
        case "\\":
            let save = i
            i += 1
            let n = name()
            if Self.STRUCTURAL.contains(n) { i = save; return Self.EMPTY }
            return command(n) ?? Self.EMPTY
        case "}", "&", "^", "_": return Self.EMPTY
        default:
            var tmp: [FNode] = []
            chars(&tmp, true)
            return tmp.count == 1 ? tmp[0] : RowNode(tmp)
        }
    }

    private func script(_ items: inout [FNode], _ sup: Bool, _ arg: FNode) {
        if let last = items.last as? ScriptsNode {
            if sup ? last.sup == nil : last.sub == nil {
                if sup { last.sup = arg } else { last.sub = arg }
                return
            }
            if sup { last.sup = RowNode([last.sup!, arg]) } else { last.sub = RowNode([last.sub!, arg]) }
            return
        }
        let last = items.last
        let base: FNode = (last == nil || last is SpaceNode || last is StyleNode) ? Self.EMPTY : items.removeLast()
        items.append(sup ? ScriptsNode(base, arg, nil) : ScriptsNode(base, nil, arg))
    }

    private func primes(_ items: inout [FNode], _ n: Int) {
        let last = items.last
        if let l = last as? ScriptsNode, l.sup == nil { l.primes += n }
        else if last == nil || last is SpaceNode || last is StyleNode { items.append(SymNode(String(repeating: "′", count: n), .ORD, .ROMAN)) }
        else { items.append(ScriptsNode(items.removeLast(), nil, nil, n)) }
    }

    private func setLimits(_ items: [FNode], _ on: Bool) {
        let last = items.last
        if let b = last as? BigOpNode { b.limits = on }
        else if let f = last as? FuncNode { f.limits = on }
        else if let sc = last as? ScriptsNode {
            if let b = sc.base as? BigOpNode { b.limits = on } else if let f = sc.base as? FuncNode { f.limits = on }
        }
    }

    private func letterStyle(_ upperGreek: Bool) -> GlyphStyle {
        switch font {
        case Self.F_RM: return .ROMAN
        case Self.F_BF: return .BOLD
        case Self.F_BI: return upperGreek ? .BOLD : .BOLD_ITALIC
        case Self.F_IT: return .ITALIC
        default: return upperGreek ? .ROMAN : .ITALIC
        }
    }

    private func letter(_ t: String) -> SymNode {
        let cp = t.unicodeScalars.first?.value ?? 0
        if font == Self.F_BB {
            if let ds = t.first.flatMap({ MathSymbols.doubleStruck[$0] }) { return SymNode(ds, .ORD, .ROMAN, kind: SymNode.K_LETTER) }
            return SymNode(t, .ORD, .BOLD, kind: SymNode.K_LETTER)
        }
        return SymNode(t, .ORD, letterStyle((0x391...0x3A9).contains(cp)), kind: SymNode.K_LETTER)
    }

    private func digits(_ t: String) -> SymNode {
        SymNode(t, .ORD, (font == Self.F_BF || font == Self.F_BI) ? .BOLD : .ROMAN, kind: SymNode.K_DIGITS)
    }

    private func chars(_ items: inout [FNode], _ single: Bool) {
        let c = s[i]
        if c.isAsciiDigitK {
            if single { i += 1; items.append(digits(String(c))); return }
            let st = i
            while i < s.count {
                let d = s[i]
                if d.isAsciiDigitK { i += 1 }
                else if (d == "." || d == ",") && i > st && i + 1 < s.count && s[i + 1].isAsciiDigitK { i += 1 }
                else { break }
            }
            items.append(digits(s.str(st, i)))
        } else if MathSymbols.superscripts[c] != nil || MathSymbols.subscripts[c] != nil {
            let sup = MathSymbols.superscripts[c] != nil
            let map = sup ? MathSymbols.superscripts : MathSymbols.subscripts
            var sb = ""
            while i < s.count, let m = map[s[i]] { sb.append(m); i += 1 }
            let inner = FormulaParser(sb).parse()
            if single { items.append(inner) } else { script(&items, sup, inner) }
        } else if c == "√" || c == "∛" || c == "∜" {
            i += 1
            let idx: FNode? = c == "∛" ? digits("3") : c == "∜" ? digits("4") : nil
            items.append(SqrtNode(arg(), idx))
        } else if let op = MathSymbols.bigOpChars[c] { i += 1; items.append(BigOpNode(op)) }
        else if c == "-" { i += 1; items.append(SymNode("−", .BIN, .ROMAN)) }
        else if c == "*" { i += 1; items.append(SymNode("·", .BIN, .ROMAN)) }
        else if c == "~" { i += 1; items.append(SpaceNode(0.33)) }
        else if c == "'" { i += 1; items.append(SymNode("′", .ORD, .ROMAN)) }
        else if c == "{" { i += 1; items.append(group()) }
        else if let t = MathSymbols.charTypes[c] { i += 1; items.append(SymNode(String(c), t, .ROMAN)) }
        else {
            // Swift karakteri birleşen işaretleri (x̄) zaten içerir
            i += 1
            let t = String(c)
            items.append(c.isLetter ? letter(t) : SymNode(t, .ORD, .ROMAN))
        }
    }

    private func withFont(_ f: Int, _ block: () -> FNode) -> FNode {
        let saved = font
        font = f
        defer { font = saved }
        return block()
    }

    private func command(_ n: String) -> FNode? {
        if let g = MathSymbols.greek[n] { return letter(g) }
        if let f = MathSymbols.functions[n] { return FuncNode(MathSymbols.functionText[n] ?? n, f) }
        if let b = MathSymbols.bigOps[n] { return BigOpNode(b) }
        if let a = MathSymbols.accents[n] { return AccentNode(a, arg()) }
        if n == "iff" || n == "implies" || n == "impliedby" {
            return SymNode(n == "impliedby" ? "⇐" : n == "iff" ? "⇔" : "⇒", .REL, .ROMAN, pad: 5 / 18)
        }
        if let sym = MathSymbols.commands[n] {
            let style: GlyphStyle = (font == Self.F_BF || font == Self.F_BI) && sym.type == .ORD ? .BOLD : .ROMAN
            return SymNode(sym.text, sym.type, style)
        }
        switch n {
        case "": return nil
        case "frac", "dfrac", "tfrac", "cfrac":
            let a = arg(), b = arg()
            return FracNode(a, b, forced: (n == "dfrac" || n == "cfrac") ? 0 : n == "tfrac" ? 1 : -1)
        case "binom", "dbinom", "tbinom":
            let a = arg(), b = arg()
            let f = FracNode(a, b, forced: n == "dbinom" ? 0 : n == "tbinom" ? 1 : -1, bar: false)
            return FenceNode("(", RowNode([f]), ")", auto: false, binom: true)
        case "sqrt":
            skipWs()
            var idx: FNode? = nil
            if i < s.count && s[i] == "[" { i += 1; idx = RowNode(list(Self.S_BRACKET).0) }
            return SqrtNode(arg(), idx)
        case "left": return left()
        case "middle": let d = delimiter(); return SymNode(d == "." ? "" : d, .REL, .ROMAN)
        case "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr", "biggl", "biggr":
            let d = delimiter()
            return d == "." ? nil : SymNode(d, n.hasSuffix("r") ? .CLOSE : n.hasSuffix("l") ? .OPEN : .ORD, .ROMAN)
        case "begin": return env(rawGroup().trimmingCharacters(in: .whitespaces))
        case "text", "textrm", "mbox", "textnormal", "hbox", "textup": return text(.ROMAN)
        case "textit": return text(.ITALIC)
        case "textbf": return text(.BOLD)
        case "operatorname":
            let star = i < s.count && s[i] == "*"
            if star { i += 1 }
            return FuncNode(rawGroup().trimmingCharacters(in: .whitespaces), star)
        case "mathrm", "mathup", "mathsf", "mathtt": return withFont(Self.F_RM) { arg() }
        case "mathbf": return withFont(Self.F_BF) { arg() }
        case "mathit", "mathcal", "mathscr": return withFont(Self.F_IT) { arg() }
        case "boldsymbol", "bm": return withFont(Self.F_BI) { arg() }
        case "mathbb": return withFont(Self.F_BB) { arg() }
        case "displaystyle": return StyleNode(0)
        case "textstyle": return StyleNode(1)
        case "scriptstyle": return StyleNode(2)
        case "scriptscriptstyle": return StyleNode(3)
        case ",", "thinspace": return SpaceNode(3 / 18)
        case ":", ">", "medspace": return SpaceNode(4 / 18)
        case ";", "thickspace": return SpaceNode(5 / 18)
        case "!", "negthinspace": return SpaceNode(-3 / 18)
        case "quad": return SpaceNode(1)
        case "qquad": return SpaceNode(2)
        case " ": return SpaceNode(0.33)
        case "enspace": return SpaceNode(0.5)
        case "slot":
            let name = rawGroup().trimmingCharacters(in: .whitespaces)
            let body = arg()
            if name.isEmpty { return body }
            return SlotNode(name, (body as? RowNode) ?? RowNode([body]))
        case "not":
            let next = arg()
            guard let sym = next as? SymNode else { return next }
            switch sym.text {
            case "=": return SymNode("≠", .REL, .ROMAN)
            case "∈": return SymNode("∉", .REL, .ROMAN)
            case "⊂": return SymNode("⊄", .REL, .ROMAN)
            case "⊆": return SymNode("⊈", .REL, .ROMAN)
            case "≡": return SymNode("≢", .REL, .ROMAN)
            case "∼": return SymNode("≁", .REL, .ROMAN)
            case "|": return SymNode("∤", .REL, .ROMAN)
            default: return next
            }
        case "cdots", "dotsb", "dotsm", "dotsi": return DotsNode("c")
        case "ldots", "dots", "dotsc": return DotsNode("l")
        case "vdots": return DotsNode("v")
        case "ddots": return DotsNode("d")
        case "bmod", "mod": return SymNode("mod", .BIN, .ROMAN)
        case "pmod":
            let a = arg()
            return RowNode([SpaceNode(0.5), FenceNode("(", RowNode([FuncNode("mod", false), SpaceNode(1 / 3), a]), ")", auto: true)])
        case "color": _ = rawGroup(); return nil
        case "textcolor": _ = rawGroup(); return arg()
        case "phantom", "hphantom", "vphantom": _ = arg(); return nil
        case "mathstrut", "strut", "displaylimits", "allowbreak", "relax": return nil
        case "circledot": return SymNode("⊙", .BIN, .ROMAN)
        default: return SymNode(n, .ORD, .ROMAN)
        }
    }

    private func text(_ style: GlyphStyle) -> FNode {
        let raw = Array(rawGroup())
        var parts: [FNode] = []
        var sb = ""
        var k = 0
        func flush() { if !sb.isEmpty { parts.append(TextNode(sb, style)); sb = "" } }
        while k < raw.count {
            let c = raw[k]
            if c == "\\" && k + 1 < raw.count {
                if raw.starts(with: "\\slot", at: k) && (k + 5 >= raw.count || !raw[k + 5].isAsciiLetterK) {
                    let sub = FormulaParser(raw.str(k + 5, raw.count))
                    let name = sub.rawGroup().trimmingCharacters(in: .whitespaces)
                    let value = sub.rawGroup()
                    k += 5 + sub.i
                    flush()
                    parts.append(SlotNode(name, RowNode([TextNode(Self.unescape(value), style)])))
                    continue
                }
                let nx = raw[k + 1]
                if nx.isAsciiLetterK {
                    var e = k + 1
                    while e < raw.count && raw[e].isAsciiLetterK { e += 1 }
                    let cmd = raw.str(k + 1, e)
                    sb += MathSymbols.greek[cmd] ?? MathSymbols.commands[cmd]?.text ?? ""
                    k = e
                    if k < raw.count && raw[k] == " " && !cmd.isEmpty { k += 1 }
                } else {
                    sb.append((nx == "," || nx == ";" || nx == " " || nx == ":") ? " " : nx)
                    k += 2
                }
                continue
            }
            if c == "~" { sb.append(" ") } else if c != "{" && c != "}" { sb.append(c) }
            k += 1
        }
        flush()
        return parts.count == 1 ? parts[0] : RowNode(parts)
    }

    private static func unescape(_ v: String) -> String {
        let a = Array(v)
        var sb = ""
        var k = 0
        while k < a.count {
            let c = a[k]
            if c == "\\" && k + 1 < a.count { sb.append(a[k + 1]); k += 2; continue }
            if c != "{" && c != "}" { sb.append(c == "~" ? " " : c) }
            k += 1
        }
        return sb
    }

    private func delimiter() -> String {
        skipWs()
        if i >= s.count { return "." }
        let c = s[i]
        if c == "\\" {
            i += 1
            switch name() {
            case "{", "lbrace": return "{"
            case "}", "rbrace": return "}"
            case "|", "Vert", "lVert", "rVert": return "‖"
            case "vert", "lvert", "rvert", "mid": return "|"
            case "langle": return "⟨"
            case "rangle": return "⟩"
            case "lfloor": return "⌊"
            case "rfloor": return "⌋"
            case "lceil": return "⌈"
            case "rceil": return "⌉"
            case "lbrack": return "["
            case "rbrack": return "]"
            default: return "."
            }
        }
        i += 1
        switch c {
        case "(", ")", "[", "]", "|", ".", "⟨", "⟩", "⌊", "⌋", "⌈", "⌉", "‖", "{", "}": return String(c)
        case "<": return "⟨"
        case ">": return "⟩"
        default: return "."
        }
    }

    private func left() -> FNode {
        let l = delimiter()
        let (body, stop) = list(Self.S_RIGHT | Self.S_BRACE | Self.S_CELL | Self.S_END)
        let r: String
        switch stop {
        case .RIGHT: r = delimiter()
        case .EOF: r = "."
        default: i = stopStart; r = "."
        }
        return FenceNode(l, RowNode(body), r, auto: false)
    }

    private func env(_ name: String) -> FNode {
        let spec: String? = (name == "array" || name == "alignedat") ? rawGroup() : nil
        var rows: [[RowNode]] = []
        var cur: [RowNode] = []
        while true {
            let (items, stop) = list(Self.S_CELL | Self.S_END)
            cur.append(RowNode(items))
            if stop == .AMP { continue }
            if stop == .NEWLINE { rows.append(cur); cur = []; continue }
            rows.append(cur)
            break
        }
        while rows.count > 1 && rows.last!.allSatisfy({ $0.items.isEmpty }) { rows.removeLast() }
        return MatrixNode(name.hasSuffix("*") ? String(name.dropLast()) : name, rows, spec)
    }

    private func pairFences(_ items: [FNode]) -> [FNode] {
        if !items.contains(where: { ($0 as? SymNode).map { ($0.text == "(" || $0.text == "[") && $0.type == .OPEN } ?? false }) { return items }
        var out: [FNode] = []
        var stack: [Int] = []
        for n in items {
            var closeSym: SymNode? = nil
            if let sym = n as? SymNode, sym.type == .CLOSE { closeSym = sym }
            else if let sc = n as? ScriptsNode, let b = sc.base as? SymNode, b.type == .CLOSE { closeSym = b }
            let want: String? = closeSym?.text == ")" ? "(" : closeSym?.text == "]" ? "[" : nil
            if let want, let top = stack.last, (out[top] as? SymNode)?.text == want {
                let openIdx = stack.removeLast()
                let body = Array(out[(openIdx + 1)...])
                out.removeSubrange(openIdx...)
                let fence = FenceNode(want, RowNode(body), closeSym!.text, auto: true)
                if let sc = n as? ScriptsNode { out.append(ScriptsNode(fence, sc.sup, sc.sub, sc.primes)) } else { out.append(fence) }
            } else {
                if let sym = n as? SymNode, sym.type == .OPEN, sym.text == "(" || sym.text == "[" { stack.append(out.count) }
                out.append(n)
            }
        }
        return out
    }
}
