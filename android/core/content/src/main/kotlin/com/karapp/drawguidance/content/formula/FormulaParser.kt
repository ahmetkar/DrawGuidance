package com.karapp.drawguidance.content.formula

import com.karapp.drawguidance.content.formula.AtomType.BIN
import com.karapp.drawguidance.content.formula.AtomType.CLOSE
import com.karapp.drawguidance.content.formula.AtomType.OPEN
import com.karapp.drawguidance.content.formula.AtomType.ORD
import com.karapp.drawguidance.content.formula.AtomType.PUNCT
import com.karapp.drawguidance.content.formula.AtomType.REL

/** TeX atom sınıfları (aralarındaki boşluk TeX tablosuna göre). */
internal enum class AtomType { ORD, OP, BIN, REL, OPEN, CLOSE, PUNCT, INNER }

// ---------------------------------------------------------------- sözdizim ağacı

internal sealed class Node

/** pad: iki yana eklenen boşluk (em; \iff gibi). */
internal class SymNode(
    val text: String, val type: AtomType, val style: GlyphStyle, val kind: Int = K_OTHER, val pad: Float = 0f,
) : Node() {
    companion object {
        const val K_OTHER = 0
        const val K_LETTER = 1
        const val K_DIGITS = 2
        /** Yuva kurallarının yan yana iki sayı arasına eklediği çarpım noktası. */
        const val K_AUTO_DOT = 3
    }
}

internal class RowNode(val items: List<Node>) : Node()
internal class ScriptsNode(val base: Node, var sup: Node?, var sub: Node?, var primes: Int = 0) : Node()

/** forced: -1 bağlama göre, 0 = \dfrac (display), 1 = \tfrac (text). bar=false → \binom içi. */
internal class FracNode(val num: Node, val den: Node, val forced: Int = -1, val bar: Boolean = true) : Node()
internal class SqrtNode(val body: Node, val index: Node?) : Node()

internal enum class AccentKind { VEC, LEFT_VEC, OVERLINE, UNDERLINE, BAR, HAT, WIDEHAT, DOT, DDOT, TILDE }

internal class AccentNode(val kind: AccentKind, val body: Node) : Node()

/** auto = düz ( ) / [ ] çifti (içerik uzunsa ölçülü büyür); değilse \left … \right. binom = \binom sarmalayıcısı. */
internal class FenceNode(
    val left: String, val body: RowNode, val right: String, val auto: Boolean, val binom: Boolean = false,
) : Node()

internal class MatrixNode(val env: String, val rows: List<List<RowNode>>, val colSpec: String? = null) : Node()
internal class TextNode(val text: String, val style: GlyphStyle) : Node()

/** em = geçerli biçem boyutunun em'i cinsinden (mu/18). */
internal class SpaceNode(val em: Float) : Node()

/** type != null → atom sınıfı zorlanır (işaret birleştirmede kullanılan yuva içi işleç). */
internal class SlotNode(val name: String, val body: RowNode, val type: AtomType? = null) : Node()
internal class StyleNode(val level: Int) : Node()
internal class BigOpNode(val kind: String, var limits: Boolean? = null) : Node()
internal class FuncNode(val name: String, val limitsDefault: Boolean, var limits: Boolean? = null) : Node()

/** 'c' ⋯, 'l' …, 'v' ⋮, 'd' ⋱ */
internal class DotsNode(val kind: Char) : Node()

// ---------------------------------------------------------------- simgeler

internal object MathSymbols {
    class Sym(val text: String, val type: AtomType)

    val greek: Map<String, String> = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ε",
        "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "omicron" to "ο", "pi" to "π", "varpi" to "ϖ",
        "rho" to "ρ", "varrho" to "ϱ", "sigma" to "σ", "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ",
        "phi" to "φ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π",
        "Sigma" to "Σ", "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
    )

    val commands: Map<String, Sym> = HashMap<String, Sym>().apply {
        fun put(type: AtomType, text: String, vararg names: String) = names.forEach { this[it] = Sym(text, type) }
        put(BIN, "±", "pm"); put(BIN, "∓", "mp"); put(BIN, "×", "times"); put(BIN, "÷", "div")
        put(BIN, "·", "cdot"); put(BIN, "∗", "ast"); put(BIN, "⋆", "star"); put(BIN, "∘", "circ")
        put(BIN, "•", "bullet"); put(BIN, "∪", "cup"); put(BIN, "∩", "cap"); put(BIN, "∖", "setminus")
        put(BIN, "∧", "wedge", "land"); put(BIN, "∨", "vee", "lor"); put(BIN, "⊕", "oplus"); put(BIN, "⊖", "ominus")
        put(BIN, "⊗", "otimes"); put(BIN, "⊙", "odot")
        put(REL, "≤", "le", "leq", "leqslant"); put(REL, "≥", "ge", "geq", "geqslant"); put(REL, "≠", "ne", "neq")
        put(REL, "≈", "approx"); put(REL, "≡", "equiv"); put(REL, "∼", "sim"); put(REL, "≃", "simeq")
        put(REL, "≅", "cong"); put(REL, "∝", "propto"); put(REL, "→", "to", "rightarrow", "longrightarrow")
        put(REL, "←", "leftarrow", "gets", "longleftarrow"); put(REL, "↔", "leftrightarrow", "longleftrightarrow")
        put(REL, "⇒", "Rightarrow", "implies", "Longrightarrow"); put(REL, "⇐", "Leftarrow", "Longleftarrow")
        put(REL, "⇔", "Leftrightarrow", "iff", "Longleftrightarrow"); put(REL, "↦", "mapsto")
        put(REL, "∈", "in"); put(REL, "∉", "notin"); put(REL, "∋", "ni"); put(REL, "⊂", "subset")
        put(REL, "⊆", "subseteq"); put(REL, "⊃", "supset"); put(REL, "⊇", "supseteq"); put(REL, "⊄", "not\\subset")
        put(REL, "⊥", "perp"); put(REL, "∥", "parallel"); put(REL, "|", "mid"); put(REL, "∤", "nmid")
        put(REL, "≪", "ll"); put(REL, "≫", "gg"); put(REL, "↑", "uparrow"); put(REL, "↓", "downarrow")
        put(REL, "≐", "doteq"); put(REL, "<", "lt"); put(REL, ">", "gt"); put(REL, "≔", "coloneqq")
        put(ORD, "∞", "infty"); put(ORD, "∂", "partial"); put(ORD, "∇", "nabla"); put(ORD, "∀", "forall")
        put(ORD, "∃", "exists"); put(ORD, "∄", "nexists"); put(ORD, "∅", "emptyset", "varnothing")
        put(ORD, "∠", "angle"); put(ORD, "∡", "measuredangle"); put(ORD, "△", "triangle"); put(ORD, "□", "square")
        put(ORD, "°", "degree"); put(ORD, "′", "prime"); put(ORD, "ℏ", "hbar"); put(ORD, "ℓ", "ell")
        put(ORD, "¬", "neg", "lnot"); put(ORD, "ℵ", "aleph"); put(ORD, "ℜ", "Re"); put(ORD, "ℑ", "Im")
        put(ORD, "∴", "therefore"); put(ORD, "∵", "because"); put(ORD, "⊤", "top"); put(ORD, "⊥", "bot")
        put(ORD, "\\", "backslash"); put(ORD, "†", "dagger"); put(ORD, "‖", "|", "Vert"); put(ORD, "|", "vert")
        put(ORD, "%", "%"); put(ORD, "#", "#"); put(ORD, "&", "&"); put(ORD, "_", "_"); put(ORD, "$", "$")
        put(ORD, "✓", "checkmark"); put(ORD, "√", "surd")
        put(OPEN, "{", "{", "lbrace"); put(OPEN, "⟨", "langle"); put(OPEN, "⌊", "lfloor"); put(OPEN, "⌈", "lceil")
        put(OPEN, "[", "lbrack"); put(OPEN, "|", "lvert"); put(OPEN, "‖", "lVert")
        put(CLOSE, "}", "}", "rbrace"); put(CLOSE, "⟩", "rangle"); put(CLOSE, "⌋", "rfloor"); put(CLOSE, "⌉", "rceil")
        put(CLOSE, "]", "rbrack"); put(CLOSE, "|", "rvert"); put(CLOSE, "‖", "rVert")
        put(PUNCT, ":", "colon"); put(PUNCT, ".", "ldotp"); put(PUNCT, "·", "cdotp")
    }

    /** Doğrudan yazılan Unicode/ASCII karakterlerin sınıfı (harf ve rakam dışındakiler). */
    val charTypes: Map<Char, AtomType> = HashMap<Char, AtomType>().apply {
        "+−±∓×÷·∘∙•∪∩∧∨⊕⊗∖".forEach { this[it] = BIN }
        "=<>:≤≥≠≈≡∼≃≅∝→←↔⇒⇐⇔↦∈∉∋⊂⊆⊃⊇⊥∥≪≫⩽⩾".forEach { this[it] = REL }
        "([⟨⌊⌈".forEach { this[it] = OPEN }
        ")]⟩⌋⌉!?".forEach { this[it] = CLOSE }
        ",;".forEach { this[it] = PUNCT }
    }

    /** Fonksiyon adları (düz yazılır) → display biçeminde alt/üst sınırlar altına/üstüne mi konur. */
    val functions: Map<String, Boolean> = HashMap<String, Boolean>().apply {
        listOf(
            "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan", "arccot", "sinh", "cosh", "tanh",
            "coth", "log", "ln", "lg", "exp", "deg", "dim", "ker", "hom", "arg", "tg", "ctg", "sgn", "lcm", "cosec",
        ).forEach { this[it] = false }
        listOf("lim", "liminf", "limsup", "max", "min", "sup", "inf", "det", "gcd", "Pr").forEach { this[it] = true }
    }

    val functionText: Map<String, String> = mapOf("liminf" to "lim inf", "limsup" to "lim sup")

    val bigOps: Map<String, String> = mapOf(
        "sum" to "sum", "prod" to "prod", "coprod" to "coprod", "int" to "int", "iint" to "iint", "iiint" to "iiint",
        "oint" to "oint", "oiint" to "oiint", "bigcup" to "bigcup", "bigcap" to "bigcap",
    )
    val bigOpChars: Map<Char, String> = mapOf(
        '∑' to "sum", '∏' to "prod", '∐' to "coprod", '∫' to "int", '∬' to "iint", '∭' to "iiint", '∮' to "oint",
        '∯' to "oiint", '⋃' to "bigcup", '⋂' to "bigcap",
    )

    val superscripts: Map<Char, Char> = pairs("⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱ", "0123456789+-=()ni")
    val subscripts: Map<Char, Char> = pairs("₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎ₐₑₒₓᵢⱼₙₘₖ", "0123456789+-=()aeoxijnmk")

    private fun pairs(a: String, b: String) = a.indices.associate { a[it] to b[it] }

    val doubleStruck: Map<Char, String> =
        mapOf('R' to "ℝ", 'N' to "ℕ", 'Z' to "ℤ", 'Q' to "ℚ", 'C' to "ℂ", 'P' to "ℙ", 'H' to "ℍ")

    val accents: Map<String, AccentKind> = mapOf(
        "vec" to AccentKind.VEC, "overrightarrow" to AccentKind.VEC, "overleftarrow" to AccentKind.LEFT_VEC,
        "overline" to AccentKind.OVERLINE, "underline" to AccentKind.UNDERLINE, "bar" to AccentKind.BAR,
        "hat" to AccentKind.HAT, "widehat" to AccentKind.WIDEHAT, "dot" to AccentKind.DOT, "ddot" to AccentKind.DDOT,
        "tilde" to AccentKind.TILDE, "widetilde" to AccentKind.TILDE,
    )
}

// ---------------------------------------------------------------- ayrıştırıcı

/**
 * TeX alt kümesinin hataya dayanıklı ayrıştırıcısı: hiçbir girdide istisna atmaz (kapanmayan gruplar dosya sonunda
 * kapanır, fazla `}` yok sayılır, bilinmeyen komut adı düz yazı olur). Düz ( ) ve [ ] çiftleri [FenceNode]'a
 * dönüştürülür ki içerik uzunsa ayraçlar büyüyebilsin.
 */
internal class FormulaParser(private val s: String) {
    private var i = 0
    private var font = F_NONE
    private var depth = 0
    private var stopStart = 0

    private enum class Stop { EOF, BRACE, RIGHT, AMP, NEWLINE, END, BRACKET }

    private companion object {
        const val S_BRACE = 1
        const val S_RIGHT = 2
        const val S_CELL = 4
        const val S_BRACKET = 8
        const val S_END = 16
        const val MAX_DEPTH = 48

        const val F_NONE = 0
        const val F_RM = 1
        const val F_BF = 2
        const val F_IT = 3
        const val F_BI = 4
        const val F_BB = 5
        val EMPTY = RowNode(emptyList())
        val STRUCTURAL = setOf("right", "\\", "end", "cr", "limits", "nolimits")
    }

    fun parse(): RowNode {
        val rows = ArrayList<List<RowNode>>()
        var cur = ArrayList<RowNode>()
        var multi = false
        while (true) {
            val (items, stop) = list(S_CELL)
            cur.add(RowNode(items))
            when (stop) {
                Stop.AMP -> multi = true
                Stop.NEWLINE -> { multi = true; rows.add(cur); cur = ArrayList() }
                else -> break
            }
        }
        rows.add(cur)
        if (!multi) return cur[0]
        while (rows.size > 1 && rows.last().all { it.items.isEmpty() }) rows.removeAt(rows.size - 1)
        if (rows.size == 1 && rows[0].size == 1) return rows[0][0]
        val aligned = rows.any { it.size > 1 }
        return RowNode(listOf(MatrixNode(if (aligned) "aligned" else "gathered", rows)))
    }

    private fun list(stops: Int): Pair<List<Node>, Stop> {
        val items = ArrayList<Node>()
        val savedFont = font
        depth++
        try {
            while (i < s.length) {
                val c = s[i]
                when {
                    c.isWhitespace() -> i++
                    c == '{' -> { i++; add(items, group()) }
                    c == '}' -> {
                        stopStart = i; i++
                        if (stops and S_BRACE != 0) return done(items, Stop.BRACE)
                    }
                    c == ']' && stops and S_BRACKET != 0 -> { stopStart = i; i++; return done(items, Stop.BRACKET) }
                    c == '&' -> {
                        stopStart = i; i++
                        if (stops and S_CELL != 0) return done(items, Stop.AMP)
                    }
                    c == '^' || c == '_' -> { i++; script(items, c == '^', arg()) }
                    c == '\'' -> {
                        var n = 0
                        while (i < s.length && s[i] == '\'') { n++; i++ }
                        primes(items, n)
                    }
                    c == '\\' -> {
                        stopStart = i
                        i++
                        when (val name = name()) {
                            "right" -> {
                                if (stops and S_RIGHT != 0) return done(items, Stop.RIGHT)
                                delimiter()
                            }
                            "\\", "cr", "newline" -> {
                                optBracket()
                                if (stops and S_CELL != 0) return done(items, Stop.NEWLINE)
                            }
                            "end" -> {
                                rawGroup()
                                if (stops and S_END != 0) return done(items, Stop.END)
                            }
                            "limits", "nolimits" -> setLimits(items, name == "limits")
                            "rm" -> font = F_RM
                            "bf" -> font = F_BF
                            "it" -> font = F_IT
                            "hline", "left.", "nonumber", "notag", "label" -> if (name == "label") rawGroup()
                            else -> command(name)?.let { add(items, it) }
                        }
                    }
                    else -> chars(items, false)
                }
            }
            return done(items, Stop.EOF)
        } finally {
            font = savedFont
            depth--
        }
    }

    private fun done(items: List<Node>, stop: Stop) = pairFences(items) to stop

    private fun group(): Node {
        if (depth > MAX_DEPTH) {
            // Aşırı iç içe grup: içeriği düz yazı olarak al (yığın taşmasını önler).
            val st = i
            var d = 1
            while (i < s.length && d > 0) {
                when (s[i]) { '{' -> d++; '}' -> d-- }
                i++
            }
            return TextNode(s.substring(st, (i - 1).coerceAtLeast(st)), GlyphStyle.ROMAN)
        }
        return RowNode(list(S_BRACE).first)
    }

    private fun add(items: MutableList<Node>, n: Node) {
        items.add(n)
    }

    private fun skipWs() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun name(): String {
        if (i >= s.length) return ""
        if (s[i].isAsciiLetter()) {
            val st = i
            while (i < s.length && s[i].isAsciiLetter()) i++
            return s.substring(st, i)
        }
        val n = Character.charCount(s.codePointAt(i))
        i += n
        return s.substring(i - n, i)
    }

    private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

    private fun optBracket() {
        val save = i
        skipWs()
        if (i < s.length && s[i] == '[') {
            val close = s.indexOf(']', i)
            if (close > 0 && close - i < 16) { i = close + 1; return }
        }
        i = save
    }

    /** `{…}` içeriğini ham metin olarak (iç içe parantez ve kaçışlar korunarak) okur; yoksa tek karakter. */
    private fun rawGroup(): String {
        skipWs()
        if (i >= s.length) return ""
        if (s[i] != '{') {
            if (s[i] == '\\') { i++; return "\\" + name() }
            val n = Character.charCount(s.codePointAt(i))
            i += n
            return s.substring(i - n, i)
        }
        i++
        val st = i
        var d = 1
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) { i += 2; continue }
            if (c == '{') d++
            if (c == '}') { d--; if (d == 0) break }
            i++
        }
        val out = s.substring(st, i.coerceAtMost(s.length))
        if (i < s.length) i++
        return out
    }

    /** Komut veya ^/_ argümanı: grup, tek komut ya da TEK karakter (x^23 = x²3, TeX gibi). */
    private fun arg(): Node {
        skipWs()
        if (i >= s.length) return EMPTY
        return when (s[i]) {
            '{' -> { i++; group() }
            '\\' -> {
                val save = i
                i++
                val n = name()
                if (n in STRUCTURAL) { i = save; EMPTY } else command(n) ?: EMPTY
            }
            '}', '&', '^', '_' -> EMPTY
            else -> {
                val tmp = ArrayList<Node>(1)
                chars(tmp, true)
                tmp.singleOrNull() ?: RowNode(tmp)
            }
        }
    }

    private fun script(items: MutableList<Node>, sup: Boolean, arg: Node) {
        val last = items.lastOrNull()
        if (last is ScriptsNode && (if (sup) last.sup == null else last.sub == null)) {
            if (sup) last.sup = arg else last.sub = arg
            return
        }
        if (last is ScriptsNode) { // x^a^b: en iyi çaba — ikinciyi birincinin sonuna ekle
            if (sup) last.sup = RowNode(listOf(last.sup!!, arg)) else last.sub = RowNode(listOf(last.sub!!, arg))
            return
        }
        val base = if (last == null || last is SpaceNode || last is StyleNode) EMPTY else items.removeAt(items.size - 1)
        items.add(if (sup) ScriptsNode(base, arg, null) else ScriptsNode(base, null, arg))
    }

    private fun primes(items: MutableList<Node>, n: Int) {
        val last = items.lastOrNull()
        when {
            last is ScriptsNode && last.sup == null -> last.primes += n
            last == null || last is SpaceNode || last is StyleNode -> items.add(SymNode("′".repeat(n), ORD, GlyphStyle.ROMAN))
            else -> items.add(ScriptsNode(items.removeAt(items.size - 1), null, null, n))
        }
    }

    private fun setLimits(items: List<Node>, on: Boolean) {
        when (val last = items.lastOrNull()) {
            is BigOpNode -> last.limits = on
            is FuncNode -> last.limits = on
            is ScriptsNode -> when (val b = last.base) {
                is BigOpNode -> b.limits = on
                is FuncNode -> b.limits = on
                else -> {}
            }
            else -> {}
        }
    }

    private fun letterStyle(upperGreek: Boolean): GlyphStyle = when (font) {
        F_RM -> GlyphStyle.ROMAN
        F_BF -> GlyphStyle.BOLD
        F_BI -> if (upperGreek) GlyphStyle.BOLD else GlyphStyle.BOLD_ITALIC
        F_IT -> GlyphStyle.ITALIC
        else -> if (upperGreek) GlyphStyle.ROMAN else GlyphStyle.ITALIC
    }

    private fun letter(t: String): SymNode {
        val cp = t.codePointAt(0)
        if (font == F_BB) {
            MathSymbols.doubleStruck[t[0]]?.let { return SymNode(it, ORD, GlyphStyle.ROMAN, SymNode.K_LETTER) }
            return SymNode(t, ORD, GlyphStyle.BOLD, SymNode.K_LETTER)
        }
        return SymNode(t, ORD, letterStyle(cp in 0x391..0x3A9), SymNode.K_LETTER)
    }

    private fun digits(t: String) = SymNode(
        t, ORD, if (font == F_BF || font == F_BI) GlyphStyle.BOLD else GlyphStyle.ROMAN, SymNode.K_DIGITS,
    )

    private fun chars(items: MutableList<Node>, single: Boolean) {
        val c = s[i]
        when {
            c in '0'..'9' -> {
                if (single) { i++; items.add(digits(c.toString())); return }
                val st = i
                while (i < s.length) {
                    val d = s[i]
                    if (d in '0'..'9') i++
                    else if ((d == '.' || d == ',') && i > st && i + 1 < s.length && s[i + 1] in '0'..'9') i++
                    else break
                }
                items.add(digits(s.substring(st, i)))
            }
            MathSymbols.superscripts.containsKey(c) || MathSymbols.subscripts.containsKey(c) -> {
                val sup = MathSymbols.superscripts.containsKey(c)
                val map = if (sup) MathSymbols.superscripts else MathSymbols.subscripts
                val sb = StringBuilder()
                while (i < s.length && map.containsKey(s[i])) { sb.append(map[s[i]]); i++ }
                val inner = FormulaParser(sb.toString()).parse()
                if (single) items.add(inner) else script(items, sup, inner)
            }
            c == '√' || c == '∛' || c == '∜' -> {
                i++
                val idx = when (c) { '∛' -> digits("3"); '∜' -> digits("4"); else -> null }
                items.add(SqrtNode(arg(), idx))
            }
            MathSymbols.bigOpChars.containsKey(c) -> { i++; items.add(BigOpNode(MathSymbols.bigOpChars.getValue(c))) }
            c == '-' -> { i++; items.add(SymNode("−", BIN, GlyphStyle.ROMAN)) }
            c == '*' -> { i++; items.add(SymNode("·", BIN, GlyphStyle.ROMAN)) }
            c == '~' -> { i++; items.add(SpaceNode(0.33f)) }
            c == '\'' -> { i++; items.add(SymNode("′", ORD, GlyphStyle.ROMAN)) }
            c == '{' -> { i++; items.add(group()) }
            MathSymbols.charTypes.containsKey(c) -> {
                i++
                items.add(SymNode(c.toString(), MathSymbols.charTypes.getValue(c), GlyphStyle.ROMAN))
            }
            else -> {
                val cp = s.codePointAt(i)
                var end = i + Character.charCount(cp)
                // Birleşen işaretler (x̄ gibi) harfle tek glif kalır.
                while (end < s.length && Character.getType(s[end]) == Character.NON_SPACING_MARK.toInt()) end++
                val t = s.substring(i, end)
                i = end
                items.add(if (Character.isLetter(cp)) letter(t) else SymNode(t, ORD, GlyphStyle.ROMAN))
            }
        }
    }

    private fun withFont(f: Int, block: () -> Node): Node {
        val saved = font
        font = f
        try {
            return block()
        } finally {
            font = saved
        }
    }

    private fun command(n: String): Node? {
        MathSymbols.greek[n]?.let { return letter(it) }
        MathSymbols.functions[n]?.let { return FuncNode(MathSymbols.functionText[n] ?: n, it) }
        MathSymbols.bigOps[n]?.let { return BigOpNode(it) }
        MathSymbols.accents[n]?.let { return AccentNode(it, arg()) }
        if (n == "iff" || n == "implies" || n == "impliedby") {
            return SymNode(if (n == "impliedby") "⇐" else if (n == "iff") "⇔" else "⇒", REL, GlyphStyle.ROMAN, pad = 5f / 18f)
        }
        MathSymbols.commands[n]?.let {
            val style = if ((font == F_BF || font == F_BI) && it.type == ORD) GlyphStyle.BOLD else GlyphStyle.ROMAN
            return SymNode(it.text, it.type, style)
        }
        return when (n) {
            "" -> null
            "frac", "dfrac", "tfrac", "cfrac" -> {
                val a = arg()
                val b = arg()
                FracNode(a, b, when (n) { "dfrac", "cfrac" -> 0; "tfrac" -> 1; else -> -1 })
            }
            "binom", "dbinom", "tbinom" -> {
                val a = arg()
                val b = arg()
                val f = FracNode(a, b, when (n) { "dbinom" -> 0; "tbinom" -> 1; else -> -1 }, bar = false)
                FenceNode("(", RowNode(listOf(f)), ")", auto = false, binom = true)
            }
            "sqrt" -> {
                skipWs()
                val idx = if (i < s.length && s[i] == '[') { i++; RowNode(list(S_BRACKET).first) } else null
                SqrtNode(arg(), idx)
            }
            "left" -> left()
            "middle" -> SymNode(delimiter().let { if (it == ".") "" else it }, REL, GlyphStyle.ROMAN)
            "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr", "biggl", "biggr" -> {
                val d = delimiter()
                if (d == ".") null else SymNode(d, if (n.endsWith("r")) CLOSE else if (n.endsWith("l")) OPEN else ORD, GlyphStyle.ROMAN)
            }
            "begin" -> env(rawGroup().trim())
            "text", "textrm", "mbox", "textnormal", "hbox", "textup" -> text(GlyphStyle.ROMAN)
            "textit" -> text(GlyphStyle.ITALIC)
            "textbf" -> text(GlyphStyle.BOLD)
            "operatorname" -> {
                val star = i < s.length && s[i] == '*'
                if (star) i++
                FuncNode(rawGroup().trim(), star)
            }
            "mathrm", "mathup", "mathsf", "mathtt" -> withFont(F_RM) { arg() }
            "mathbf" -> withFont(F_BF) { arg() }
            "mathit", "mathcal", "mathscr" -> withFont(F_IT) { arg() }
            "boldsymbol", "bm" -> withFont(F_BI) { arg() }
            "mathbb" -> withFont(F_BB) { arg() }
            "displaystyle" -> StyleNode(0)
            "textstyle" -> StyleNode(1)
            "scriptstyle" -> StyleNode(2)
            "scriptscriptstyle" -> StyleNode(3)
            ",", "thinspace" -> SpaceNode(3f / 18f)
            ":", ">", "medspace" -> SpaceNode(4f / 18f)
            ";", "thickspace" -> SpaceNode(5f / 18f)
            "!", "negthinspace" -> SpaceNode(-3f / 18f)
            "quad" -> SpaceNode(1f)
            "qquad" -> SpaceNode(2f)
            " " -> SpaceNode(0.33f)
            "enspace" -> SpaceNode(0.5f)
            "slot" -> {
                val name = rawGroup().trim()
                val body = arg()
                if (name.isEmpty()) body else SlotNode(name, body as? RowNode ?: RowNode(listOf(body)))
            }
            "not" -> {
                val next = arg()
                if (next is SymNode) {
                    when (next.text) {
                        "=" -> SymNode("≠", REL, GlyphStyle.ROMAN)
                        "∈" -> SymNode("∉", REL, GlyphStyle.ROMAN)
                        "⊂" -> SymNode("⊄", REL, GlyphStyle.ROMAN)
                        "⊆" -> SymNode("⊈", REL, GlyphStyle.ROMAN)
                        "≡" -> SymNode("≢", REL, GlyphStyle.ROMAN)
                        "∼" -> SymNode("≁", REL, GlyphStyle.ROMAN)
                        "|" -> SymNode("∤", REL, GlyphStyle.ROMAN)
                        else -> next
                    }
                } else next
            }
            "cdots", "dotsb", "dotsm", "dotsi" -> DotsNode('c')
            "ldots", "dots", "dotsc" -> DotsNode('l')
            "vdots" -> DotsNode('v')
            "ddots" -> DotsNode('d')
            "bmod", "mod" -> SymNode("mod", BIN, GlyphStyle.ROMAN)
            "pmod" -> {
                val a = arg()
                RowNode(
                    listOf(
                        SpaceNode(0.5f),
                        FenceNode("(", RowNode(listOf(FuncNode("mod", false), SpaceNode(1f / 3f), a)), ")", auto = true),
                    ),
                )
            }
            "color" -> { rawGroup(); null }
            "textcolor" -> { rawGroup(); arg() }
            "phantom", "hphantom", "vphantom" -> { arg(); null }
            "mathstrut", "strut", "displaylimits", "allowbreak", "relax" -> null
            "circledot" -> SymNode("⊙", BIN, GlyphStyle.ROMAN)
            else -> {
                if (n.length == 1 && !n[0].isLetterOrDigit()) SymNode(n, ORD, GlyphStyle.ROMAN)
                else SymNode(n, ORD, GlyphStyle.ROMAN) // bilinmeyen komut: adı düz yazı olarak
            }
        }
    }

    private fun text(style: GlyphStyle): Node {
        val raw = rawGroup()
        val parts = ArrayList<Node>()
        val sb = StringBuilder()
        var k = 0
        fun flush() {
            if (sb.isNotEmpty()) { parts.add(TextNode(sb.toString(), style)); sb.clear() }
        }
        while (k < raw.length) {
            val c = raw[k]
            if (c == '\\' && k + 1 < raw.length) {
                if (raw.startsWith("\\slot", k) && (k + 5 >= raw.length || !raw[k + 5].isAsciiLetter())) {
                    // \text içinde yuva: \slot{ad}{metin}
                    val sub = FormulaParser(raw.substring(k + 5))
                    val name = sub.rawGroup().trim()
                    val value = sub.rawGroup()
                    k += 5 + sub.i
                    flush()
                    parts.add(SlotNode(name, RowNode(listOf(TextNode(unescape(value), style)))))
                    continue
                }
                val nx = raw[k + 1]
                when {
                    nx.isAsciiLetter() -> {
                        var e = k + 1
                        while (e < raw.length && raw[e].isAsciiLetter()) e++
                        val cmd = raw.substring(k + 1, e)
                        sb.append(MathSymbols.greek[cmd] ?: MathSymbols.commands[cmd]?.text ?: "")
                        k = e
                        if (k < raw.length && raw[k] == ' ' && cmd.isNotEmpty()) k++
                    }
                    else -> {
                        sb.append(if (nx == ',' || nx == ';' || nx == ' ' || nx == ':') ' ' else nx)
                        k += 2
                    }
                }
                continue
            }
            when (c) {
                '~' -> sb.append(' ')
                '{', '}' -> {}
                else -> sb.append(c)
            }
            k++
        }
        flush()
        return if (parts.size == 1) parts[0] else RowNode(parts)
    }

    private fun unescape(v: String): String {
        val sb = StringBuilder()
        var k = 0
        while (k < v.length) {
            val c = v[k]
            if (c == '\\' && k + 1 < v.length) { sb.append(v[k + 1]); k += 2; continue }
            if (c != '{' && c != '}') sb.append(if (c == '~') ' ' else c)
            k++
        }
        return sb.toString()
    }

    private fun delimiter(): String {
        skipWs()
        if (i >= s.length) return "."
        val c = s[i]
        if (c == '\\') {
            i++
            return when (name()) {
                "{", "lbrace" -> "{"
                "}", "rbrace" -> "}"
                "|", "Vert", "lVert", "rVert" -> "‖"
                "vert", "lvert", "rvert", "mid" -> "|"
                "langle" -> "⟨"
                "rangle" -> "⟩"
                "lfloor" -> "⌊"
                "rfloor" -> "⌋"
                "lceil" -> "⌈"
                "rceil" -> "⌉"
                "lbrack" -> "["
                "rbrack" -> "]"
                else -> "."
            }
        }
        i++
        return when (c) {
            '(', ')', '[', ']', '|', '.', '⟨', '⟩', '⌊', '⌋', '⌈', '⌉', '‖', '{', '}' -> c.toString()
            '<' -> "⟨"
            '>' -> "⟩"
            else -> "."
        }
    }

    private fun left(): Node {
        val l = delimiter()
        val (body, stop) = list(S_RIGHT or S_BRACE or S_CELL or S_END)
        val r = when (stop) {
            Stop.RIGHT -> delimiter()
            Stop.EOF -> "."
            else -> { i = stopStart; "." } // eksik \right: kapsayan yapı kendi sonlandırıcısını yeniden görsün
        }
        return FenceNode(l, RowNode(body), r, auto = false)
    }

    private fun env(name: String): Node {
        val spec = if (name == "array" || name == "alignedat") rawGroup() else null
        val rows = ArrayList<List<RowNode>>()
        var cur = ArrayList<RowNode>()
        while (true) {
            val (items, stop) = list(S_CELL or S_END)
            cur.add(RowNode(items))
            when (stop) {
                Stop.AMP -> {}
                Stop.NEWLINE -> { rows.add(cur); cur = ArrayList() }
                else -> { rows.add(cur); break }
            }
        }
        while (rows.size > 1 && rows.last().all { it.items.isEmpty() }) rows.removeAt(rows.size - 1)
        return MatrixNode(name.removeSuffix("*"), rows, spec)
    }

    /** Düz ( … ) ve [ … ] çiftlerini FenceNode yapar; kapanışa bağlı üst/alt indisler çitin kendisine geçer. */
    private fun pairFences(items: List<Node>): List<Node> {
        if (items.none { it is SymNode && (it.text == "(" || it.text == "[") && it.type == OPEN }) return items
        val out = ArrayList<Node>(items.size)
        val stack = ArrayList<Int>()
        for (n in items) {
            val closeSym = when {
                n is SymNode && n.type == CLOSE -> n
                n is ScriptsNode && n.base is SymNode && (n.base as SymNode).type == CLOSE -> n.base as SymNode
                else -> null
            }
            val want = when (closeSym?.text) { ")" -> "("; "]" -> "["; else -> null }
            if (want != null && stack.isNotEmpty() && (out[stack.last()] as SymNode).text == want) {
                val openIdx = stack.removeAt(stack.size - 1)
                val body = ArrayList(out.subList(openIdx + 1, out.size))
                while (out.size > openIdx) out.removeAt(out.size - 1)
                val fence = FenceNode(want, RowNode(body), closeSym!!.text, auto = true)
                out.add(if (n is ScriptsNode) ScriptsNode(fence, n.sup, n.sub, n.primes) else fence)
            } else {
                if (n is SymNode && n.type == OPEN && (n.text == "(" || n.text == "[")) stack.add(out.size)
                out.add(n)
            }
        }
        return out
    }
}
