package com.karapp.drawguidance.content.formula

import com.karapp.drawguidance.content.math.AngleMode
import com.karapp.drawguidance.content.math.ExactEval
import com.karapp.drawguidance.content.math.MNode
import com.karapp.drawguidance.content.math.MathError
import com.karapp.drawguidance.content.math.MathException
import com.karapp.drawguidance.content.math.MathExpr
import com.karapp.drawguidance.content.math.Rel
import com.karapp.drawguidance.content.math.Value
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs

/**
 * v45 formülde bilinmeyenlerin yerine sayı yazılınca sonucun hesaplanması.
 *
 * - Bağıntısız ifade: "2x+3", x = 4 → "2·4+3 = 11" (sonuç sona eklenir).
 * - Bağıntı (=, <, >, ≤, ≥, ≠), iki taraf da sayı: doğruysa bağıntı kalır; yanlışsa yerine doğru olan yazılır
 *   ("x² ≥ 10", x = 3 → "3² < 10"); eşitken "=", değilse "<" ya da ">" (≠ yanlışsa "="). Kullanıcının bağıntısı
 *   kaynakta saklanır: bilinmeyen geri yazılınca ya da sayı değişince kendi bağıntısı döner.
 * - Bir yanı tek bilinmeyen ("A = πr²", r = 2): öteki yan hesaplanıp sona eklenir ("A = π·2² ≈ 12,57").
 * - Bilinmeyen kalırsa hiçbir şey eklenmez (önceki sonuç silinir).
 * - Hesap kesirle yapılır ("1/2 + 1/3 = 5/6"); ondalık girişte ondalık; köklü/π'li sonuçlar yaklaşık (≈).
 * - Sıfıra bölme ve tanım dışı: "= tanımsız" yazılır ve [Outcome.error] dolu döner (uygulama ileti gösterir).
 *
 * Kaynak işaretleri (eski sürümler bunları görünmez çizer): `\color{dgres}` sonucun başı (sonrası otomatik),
 * `\color{dgrel:<bağıntı>}` değiştirilen bağıntının özgün hâli. Yan yana iki sayı yuvasının arasındaki çarpı
 * noktası (2·4) ve üslü negatif sayının parantezi ((−3)²) dizgi motorunun yuva kurallarından gelir (SlotRules).
 */
object FormulaEval {

    const val RESULT_MARK = "\\color{dgres}"
    private const val REL_MARK = "\\color{dgrel:"
    private const val DOT_MARK = "{\\cdot}"
    private const val OPEN_MARK = "{(}"
    private const val CLOSE_MARK = "{)}"

    /** Sonuç: yeni kaynak, eklenen sonuç (TeX, yoksa null) ve hata (sıfıra bölme / tanım dışı). */
    data class Outcome(val source: String, val result: String?, val error: MathError?)

    /** Otomatik eklenenleri kaldırır (kullanıcının yazdığı kaynak). */
    fun strip(source: String): String {
        var s = source
        val k = s.indexOf(RESULT_MARK)
        if (k >= 0) s = s.substring(0, k).trimEnd()
        // değiştirilmiş bağıntıyı özgününe döndür
        while (true) {
            val m = s.indexOf(REL_MARK)
            if (m < 0) break
            val e = s.indexOf('}', m)
            if (e < 0) break
            val original = s.substring(m + REL_MARK.length, e)
            val after = e + 1
            val tok = relationTokenAt(s, after)
            val tokEnd = if (tok != null) after + tok.length else after
            val rest = s.substring(tokEnd)
            val t = texOf(original)
            s = s.substring(0, m) + (if (rest.startsWith(" ") || rest.startsWith("\\") || rest.startsWith("{")) t.trimEnd() else t) + rest
        }
        return s.replace(DOT_MARK, "").replace(OPEN_MARK, "").replace(CLOSE_MARK, "")
    }

    /** Elle düzenlemeye açılan kaynak: sonuç ve bağıntı işaretleri atılır, eklenen çarpı/parantez görünür kalır. */
    fun stripResult(source: String): String {
        if (!source.contains("\\color{dg") && !source.contains(DOT_MARK) && !source.contains(OPEN_MARK)) return source
        val keep = source.replace(DOT_MARK, "\\cdot ").replace(OPEN_MARK, "(").replace(CLOSE_MARK, ")")
        return strip(keep)
    }

    private val REL_TOKENS = listOf("\\leqslant", "\\geqslant", "\\leq", "\\geq", "\\neq", "\\le", "\\ge", "\\ne", "\\lt", "\\gt", "≤", "≥", "≠", "<", ">", "=")

    private fun relationTokenAt(s: String, i: Int): String? {
        var k = i
        while (k < s.length && s[k] == ' ') k++
        val tok = REL_TOKENS.firstOrNull { s.startsWith(it, k) && !(it.startsWith("\\") && k + it.length < s.length && s[k + it.length].isLetter()) }
            ?: return null
        return s.substring(i, k) + tok
    }

    private fun relOfToken(t: String): Rel? = when (t.trim()) {
        "=" -> Rel.EQ
        "<", "\\lt" -> Rel.LT
        ">", "\\gt" -> Rel.GT
        "\\le", "\\leq", "\\leqslant", "≤" -> Rel.LE
        "\\ge", "\\geq", "\\geqslant", "≥" -> Rel.GE
        "\\ne", "\\neq", "≠" -> Rel.NE
        else -> null
    }

    private fun texOf(code: String): String = when (code) {
        "eq" -> "="; "lt" -> "<"; "gt" -> ">"; "le" -> "\\le "; "ge" -> "\\ge "; "ne" -> "\\neq "
        else -> "="
    }

    private fun codeOf(r: Rel): String = when (r) {
        Rel.EQ -> "eq"; Rel.LT -> "lt"; Rel.GT -> "gt"; Rel.LE -> "le"; Rel.GE -> "ge"; Rel.NE -> "ne"
    }

    // ------------------------------------------------------------------ TeX → doğrusal ifade

    /** Dönüştürülen ifade ve kaynaktaki bağıntı işaretlerinin yerleri. */
    private class Linear(val text: String, val relations: List<IntRange>, val hasDecimal: Boolean)

    private class Unsupported : RuntimeException()

    private val FUNS = setOf("sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh", "ln", "log", "lg", "exp", "tg", "cotg")
    private val GREEK = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ",
        "eta" to "η", "theta" to "θ", "vartheta" to "θ", "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν",
        "xi" to "ξ", "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "upsilon" to "υ", "phi" to "φ", "varphi" to "φ", "chi" to "χ",
        "psi" to "ψ", "omega" to "ω", "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Sigma" to "Σ",
        "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
    )
    private val SPACES = setOf(",", ";", ":", "!", "quad", "qquad", " ", "thinspace", "medspace", "thickspace", "enspace")
    private val SKIP_ARG = setOf("text", "textrm", "mbox", "mathrm", "textit", "textbf", "operatorname", "label", "color", "hbox")
    private val IGNORE = setOf("displaystyle", "textstyle", "left", "right", "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr", "biggl", "biggr", "limits", "nolimits")

    private class Conv(val s: String) {
        var i = 0
        val out = StringBuilder()
        val rels = ArrayList<IntRange>()
        var decimal = false

        fun groupEnd(from: Int): Int {
            var k = from
            var d = 0
            while (k < s.length) {
                val c = s[k]
                if (c == '\\' && k + 1 < s.length) { k += 2; continue }
                if (c == '{') d++
                if (c == '}') { d--; if (d == 0) return k + 1 }
                k++
            }
            return s.length
        }

        /** Sonraki tek bağımsız değişkenin kaynak aralığı (grup ya da tek simge). */
        fun argRange(from: Int): IntRange {
            var k = from
            while (k < s.length && s[k] == ' ') k++
            if (k >= s.length) throw Unsupported()
            if (s[k] == '{') return (k + 1) until (groupEnd(k) - 1)
            if (s[k] == '\\') {
                var e = k + 1
                if (e < s.length && s[e].isLetter()) { while (e < s.length && s[e].isLetter()) e++ } else e++
                return k until e
            }
            return k until k + 1
        }

        fun sub(r: IntRange): String {
            val c = Conv(s.substring(r.first, r.last + 1))
            c.run()
            if (c.rels.isNotEmpty()) throw Unsupported()
            if (c.decimal) decimal = true
            return c.out.toString()
        }

        fun plain(r: IntRange): String = Formula.plain(s.substring(r.first, r.last + 1)).filter { it.isLetterOrDigit() }

        fun run() {
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '\\' -> command()
                    c == '{' -> { out.append('('); i++ }
                    c == '}' -> { out.append(')'); i++ }
                    c == '_' -> subscript()
                    c == '^' -> {
                        i++
                        val r = argRange(i)
                        out.append("^(").append(sub(r)).append(')')
                        i = if (s.getOrNull(r.first - 1) == '{') r.last + 2 else r.last + 1
                    }
                    c == '=' || c == '<' || c == '>' || c == '≤' || c == '≥' || c == '≠' -> {
                        rels += i..i
                        out.append(' ').append(c).append(' ')
                        i++
                    }
                    c == '~' -> { out.append(' '); i++ }
                    c == '&' -> i++
                    c == '.' && i + 1 < s.length && s[i + 1].isDigit() -> { decimal = true; out.append(c); i++ }
                    c.isDigit() -> {
                        out.append(c)
                        if (i + 2 < s.length && (s[i + 1] == '.' || s[i + 1] == ',') && s[i + 2].isDigit()) decimal = true
                        i++
                    }
                    else -> { out.append(c); i++ }
                }
            }
        }

        private fun subscript() {
            i++
            val r = argRange(i)
            val name = plain(r)
            i = if (s.getOrNull(r.first - 1) == '{') r.last + 2 else r.last + 1
            // önceki değişkene alt indis: "(x)" → "(x_1)"
            val m = Regex("\\(([\\p{L}])\\)$").find(out)
            if (m != null && name.isNotEmpty()) {
                out.setLength(out.length - m.value.length)
                out.append('(').append(m.groupValues[1]).append('_').append(name).append(')')
            } else if (out.endsWith("log") || out.endsWith("log ")) {
                // log_b: MathExpr "log_(b)" biçimi
                while (out.endsWith(" ")) out.setLength(out.length - 1)
                out.append("_(").append(name.ifEmpty { throw Unsupported() }).append(')')
            } else if (Regex("[\\p{L}]$").containsMatchIn(out) && name.isNotEmpty()) {
                out.append('_').append(name)
            } else throw Unsupported()
        }

        private fun command() {
            val st = i
            i++
            if (i >= s.length) return
            if (!s[i].isLetter()) {
                val ch = s[i]
                i++
                when (ch.toString()) {
                    in SPACES -> out.append(' ')
                    "{" -> out.append('(')
                    "}" -> out.append(')')
                    "|" -> out.append('|')
                    else -> out.append(' ')
                }
                return
            }
            while (i < s.length && s[i].isLetter()) i++
            val name = s.substring(st + 1, i)
            when {
                name == "slot" -> {
                    val nr = argRange(i)
                    i = nr.last + 2
                    val vr = argRange(i)
                    val v = sub(vr)
                    i = vr.last + 2
                    out.append('(').append(v.trim()).append(')')
                }
                name == "frac" || name == "dfrac" || name == "tfrac" || name == "cfrac" -> {
                    val a = argRange(i); val ae = if (s.getOrNull(a.first - 1) == '{') a.last + 2 else a.last + 1
                    val b = argRange(ae); val be = if (s.getOrNull(b.first - 1) == '{') b.last + 2 else b.last + 1
                    out.append("((").append(sub(a)).append(")/(").append(sub(b)).append("))")
                    i = be
                }
                name == "sqrt" -> {
                    var k = i
                    while (k < s.length && s[k] == ' ') k++
                    var index: String? = null
                    if (k < s.length && s[k] == '[') {
                        val e = s.indexOf(']', k)
                        if (e < 0) throw Unsupported()
                        index = sub((k + 1) until e)
                        k = e + 1
                    }
                    val a = argRange(k)
                    val ae = if (s.getOrNull(a.first - 1) == '{') a.last + 2 else a.last + 1
                    if (index == null) out.append("sqrt(").append(sub(a)).append(')')
                    else out.append("((").append(sub(a)).append(")^(1/(").append(index).append(")))")
                    i = ae
                }
                name == "cdot" || name == "times" || name == "ast" -> out.append('*')
                name == "div" -> out.append('/')
                name == "pi" -> out.append("(pi)")
                name in GREEK -> out.append('(').append(GREEK.getValue(name)).append(')')
                name in FUNS -> out.append(' ').append(name).append(' ')
                name == "le" || name == "leq" || name == "leqslant" -> rel(st, "≤")
                name == "ge" || name == "geq" || name == "geqslant" -> rel(st, "≥")
                name == "ne" || name == "neq" -> rel(st, "≠")
                name == "lt" -> rel(st, "<")
                name == "gt" -> rel(st, ">")
                name == "circ" -> out.append('°')
                name == "degree" -> out.append('°')
                name in SPACES -> out.append(' ')
                name == "textcolor" -> { val r = argRange(i); i = r.last + 2 }
                name in SKIP_ARG -> {
                    if (name == "color" && s.startsWith("{dgrel:", i)) { i = groupEnd(i); return }
                    val r = argRange(i)
                    i = if (s.getOrNull(r.first - 1) == '{') r.last + 2 else r.last + 1
                }
                name in IGNORE -> {
                    // \left( → "(", \right. → yok
                    var k = i
                    while (k < s.length && s[k] == ' ') k++
                    if (name == "left" || name == "right" || name.startsWith("big") || name.startsWith("Big")) {
                        if (k < s.length) {
                            when {
                                s[k] == '.' -> i = k + 1
                                s[k] == '(' || s[k] == '[' -> { out.append('('); i = k + 1 }
                                s[k] == ')' || s[k] == ']' -> { out.append(')'); i = k + 1 }
                                s[k] == '|' -> { out.append('|'); i = k + 1 }
                                s.startsWith("\\{", k) -> { out.append('('); i = k + 2 }
                                s.startsWith("\\}", k) -> { out.append(')'); i = k + 2 }
                                s.startsWith("\\lvert", k) || s.startsWith("\\rvert", k) || s.startsWith("\\vert", k) -> {
                                    out.append('|'); i = s.indexOf("vert", k) + 4
                                }
                                else -> throw Unsupported()
                            }
                        }
                    }
                }
                name == "lvert" || name == "rvert" || name == "vert" -> out.append('|')
                name == "lfloor" -> out.append("floor(")
                name == "rfloor" || name == "rceil" -> out.append(')')
                name == "lceil" -> out.append("ceil(")
                else -> throw Unsupported()
            }
        }

        private fun rel(st: Int, sym: String) {
            rels += st until i
            out.append(' ').append(sym).append(' ')
        }
    }

    private fun linear(source: String): Linear? = try {
        val c = Conv(source)
        c.run()
        Linear(c.out.toString(), c.rels, c.decimal)
    } catch (_: Unsupported) {
        null
    } catch (_: RuntimeException) {
        null
    }

    // ------------------------------------------------------------------ hesap

    /**
     * Kaynağı (kullanıcının yazdığı, işaretli olabilir) hesaplar ve sonucu ekler. [undefined] tanımsız sonucun
     * yazısı (uygulama diline göre); [decimalComma] ondalık ayırıcı virgül mü.
     */
    fun annotate(source: String, undefined: String = "tanımsız", decimalComma: Boolean = true): Outcome {
        val base = strip(source)
        val lin = linear(base) ?: return Outcome(base, null, null)
        val rel = try { MathExpr.parseRelation(lin.text) } catch (_: MathException) { return Outcome(base, null, null) }
        val unknown = rel.sides.map { it.variables() }
        try {
            when {
                // bağıntısız: tamamen sayıysa sonucu ekle
                !rel.isRelation -> {
                    if (unknown[0].isNotEmpty() || isLiteral(rel.sides[0])) return Outcome(base, null, null)
                    val v = ExactEval.eval(rel.sides[0], emptyMap(), AngleMode.RAD)
                    val tex = resultTex(v, lin.hasDecimal, decimalComma)
                    return Outcome(base + RESULT_MARK + tex, tex, null)
                }
                // tek bağıntı, bir yanı tek bilinmeyen, öteki sayı: "A = πr²"
                rel.ops.size == 1 && rel.ops[0] == Rel.EQ && unknown[0].isNotEmpty() != unknown[1].isNotEmpty() -> {
                    val (lone, other) = if (unknown[0].isNotEmpty()) rel.sides[0] to rel.sides[1] else rel.sides[1] to rel.sides[0]
                    if (lone !is MNode.Var || isLiteral(other) || unknown[0].size + unknown[1].size != 1) return Outcome(base, null, null)
                    val v = ExactEval.eval(other, emptyMap(), AngleMode.RAD)
                    val tex = resultTex(v, lin.hasDecimal, decimalComma)
                    // sonuç sağ yanın arkasına (sol yan bilinmeyense formülün sonu)
                    return Outcome(base + RESULT_MARK + tex, tex, null)
                }
                // bütün yanlar sayı: bağıntıyı denetle
                rel.isRelation && unknown.all { it.isEmpty() } -> {
                    val values = rel.sides.map { ExactEval.tidy(ExactEval.eval(it, emptyMap(), AngleMode.RAD)) }
                    var out = base
                    // bağıntı işaretlerinin yerleri dönüştürülmüş kaynakta değişmesin diye sondan başa değiştirilir
                    val positions = relationSpans(out)
                    if (positions.size != rel.ops.size) return Outcome(base, null, null)
                    val chosen = ArrayList<Rel>()
                    for (k in rel.ops.indices.reversed()) {
                        val cmp = ExactEval.compare(values[k], values[k + 1])
                        val original = rel.ops[k]
                        val shown = if (original.holdsCmp(cmp)) original else when {
                            original == Rel.NE -> Rel.EQ
                            cmp < 0 -> Rel.LT
                            cmp > 0 -> Rel.GT
                            else -> Rel.EQ
                        }
                        chosen += shown
                        if (shown != original) {
                            val r = positions[k]
                            val rest = out.substring(r.last + 1)
                            val t = texOf(codeOf(shown))
                            val tt = if (rest.startsWith(" ") || rest.startsWith("\\") || rest.startsWith("{")) t.trimEnd() else t
                            out = out.substring(0, r.first) + REL_MARK + codeOf(original) + "}" + tt + rest
                        }
                    }
                    return Outcome(out, chosen.reversed().joinToString(" ") { it.symbol }, null)
                }
                else -> return Outcome(base, null, null)
            }
        } catch (e: MathException) {
            if (e.error.code == MathError.Code.UNKNOWN_NAME) return Outcome(base, null, null)
            val tex = "=\\text{$undefined}"
            return Outcome(base + RESULT_MARK + tex, tex, e.error)
        }
    }

    /** Kaynaktaki bağıntı işaretlerinin yerleri (yuva içi değil, en dış düzey). */
    private fun relationSpans(s: String): List<IntRange> {
        val out = ArrayList<IntRange>()
        var i = 0
        var depth = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '{') { depth++; i++; continue }
            if (c == '}') { depth--; i++; continue }
            if (c == '\\') {
                var e = i + 1
                while (e < s.length && s[e].isLetter()) e++
                val name = s.substring(i, e)
                if (name == "\\color") { i = s.indexOf('}', e).let { if (it < 0) s.length else it + 1 }; continue }
                if (name in setOf("\\le", "\\leq", "\\leqslant", "\\ge", "\\geq", "\\geqslant", "\\ne", "\\neq", "\\lt", "\\gt")) out += i until e
                i = if (e == i + 1) i + 2 else e
                continue
            }
            if (c in "=<>≤≥≠") out += i..i
            i++
        }
        return out
    }

    /** Yalın sayı mı (sonuç eklemeye değmez). */
    private fun isLiteral(n: MNode): Boolean = n is MNode.Num || (n is MNode.Neg && n.a is MNode.Num)

    /** Sonuç TeX'i: "=11", "=\frac{5}{6}", "=-\frac{1}{2}", "=2{,}5", "\approx 12{,}57". */
    fun resultTex(v0: Value, preferDecimal: Boolean, decimalComma: Boolean): String {
        val v = ExactEval.tidy(v0)
        val sep = if (decimalComma) "{,}" else "."
        return when (v) {
            is Value.Exact -> {
                val q = v.q
                when {
                    // çok büyük/küçük kesin sonuç bilimsel yazımla (binlerce basamak yazılmaz)
                    q.num.abs().bitLength() > 50 || q.den.bitLength() > 50 -> "\\approx " + scientific(
                        java.math.BigDecimal(q.num).divide(java.math.BigDecimal(q.den), MathContext(6)), sep,
                    )
                    q.isInteger -> "=" + q.num.toString()
                    preferDecimal && q.terminatingDecimal() != null -> "=" + q.terminatingDecimal()!!.replace(".", sep)
                    else -> {
                        val neg = q.signum < 0
                        "=" + (if (neg) "-" else "") + "\\frac{" + q.num.abs() + "}{" + q.den + "}"
                    }
                }
            }
            is Value.Approx -> "\\approx " + if (abs(v.d) >= 1e12 || (abs(v.d) < 1e-4 && v.d != 0.0)) scientific(BigDecimal(v.d).round(MathContext(6)), sep)
            else decimal(v.d).replace(".", sep)
        }
    }

    /** a·10ⁿ yazımı. */
    private fun scientific(bd0: BigDecimal, sep: String): String {
        val bd = bd0.stripTrailingZeros()
        val exp = bd.precision() - bd.scale() - 1
        val mant = bd.movePointLeft(exp).stripTrailingZeros().toPlainString().replace(".", sep)
        return "$mant\\cdot 10^{$exp}"
    }

    /** 6 anlamlı basamağa yuvarlanmış ondalık (sondaki sıfırlar atılır). */
    fun decimal(d: Double): String {
        if (d == 0.0) return "0"
        val bd = BigDecimal(d).round(MathContext(6)).stripTrailingZeros()
        val plain = if (abs(d) >= 1e-4 && abs(d) < 1e12) bd.toPlainString() else bd.toString()
        return plain
    }

}
