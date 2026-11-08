package com.karapp.drawguidance.content.formula

import com.karapp.drawguidance.content.formula.AtomType.BIN
import com.karapp.drawguidance.content.formula.AtomType.CLOSE
import com.karapp.drawguidance.content.formula.AtomType.INNER
import com.karapp.drawguidance.content.formula.AtomType.OP
import com.karapp.drawguidance.content.formula.AtomType.OPEN
import com.karapp.drawguidance.content.formula.AtomType.ORD
import com.karapp.drawguidance.content.formula.AtomType.PUNCT
import com.karapp.drawguidance.content.formula.AtomType.REL

/**
 * Formül dizgi motoru. Kaynak dil bir LaTeX alt kümesidir; düzenlenebilir kısımlar `\slot{ad}{varsayılan}` ile
 * işaretlenir. Tüm fonksiyonlar saftır ve iş parçacığı güvenlidir (paylaşılan değişebilir durum yoktur).
 */
object Formula {

    /** Formülü dizer. Hiçbir girdide istisna atmaz; bozuk girdi en iyi çabayla (gerekirse düz metin) dizilir. */
    fun layout(source: String, size: Float, metrics: GlyphMetrics): FormulaLayout {
        val sz = if (size.isFinite() && size > 0f) size else 16f
        return try {
            val root = FormulaParser(source).parse()
            val l = Typesetter(metrics, sz).layoutRoot(root)
            if (valid(l)) l else fallback(source, sz, metrics)
        } catch (e: Exception) {
            fallback(source, sz, metrics)
        } catch (e: StackOverflowError) {
            fallback(source, sz, metrics)
        }
    }

    /** Kaynaktaki yuvalar, ilk görünüş sırasıyla ve ad başına bir kez (değer = ilk geçişin içeriği). */
    fun slots(source: String): List<Slot> {
        val seen = LinkedHashMap<String, Slot>()
        for (o in SlotSource.scan(source)) {
            if (o.name.isEmpty() || seen.containsKey(o.name)) continue
            seen[o.name] = Slot(o.name, source.substring(o.valueStart, o.valueEnd))
        }
        return seen.values.toList()
    }

    /** [name] adlı yuvanın TÜM geçişlerinin içeriğini [value] (basit TeX olabilir: `-3`, `\pi`, `x^2`) yapar. */
    fun setSlot(source: String, name: String, value: String): String = SlotSource.set(source, name, value)

    /**
     * v17: formülü elemanlarına ayırır — her sayı ve her harf (Yunan harfleri dahil) ayrı, benzersiz adlı (1, 2, 3…)
     * bir yuva olur; var olan yuvalar da sırayla yeniden adlandırılır (değerleri korunur). Böylece formülde birden çok
     * geçen `a`'nın her biri ayrı düzenlenir. Yazı (`\text`, `\mathrm`…), ortam adları ve ayraçlar dokunulmadan kalır.
     */
    fun atomize(source: String): String = Atomizer.atomize(source)

    /** Formül zaten elemanlarına ayrılmış mı (her sayı/harf sırayla numaralı bir yuvada). */
    fun isAtomized(source: String): Boolean = Atomizer.atomize(source) == source

    /** Yuva işaretleri atılmış, elle yazmaya uygun kaynak (ör. `\slot{1}{a}\slot{2}{c}` → `ac`). */
    fun plain(source: String): String = Atomizer.plain(source)

    /** Arama/erişilebilirlik için tek satırlık okunur metin, ör. "x = (−b ± √(b² − 4ac)) / 2a". */
    fun plainText(source: String): String = try {
        PlainText.of(FormulaParser(source).parse())
    } catch (e: Exception) {
        source
    } catch (e: StackOverflowError) {
        source
    }

    private fun valid(l: FormulaLayout): Boolean {
        if (!l.width.isFinite() || !l.ascent.isFinite() || !l.descent.isFinite()) return false
        for (o in l.ops) {
            val ok = when (o) {
                is DrawOp.Glyph -> o.x.isFinite() && o.y.isFinite() && o.size.isFinite()
                is DrawOp.Rule -> o.x1.isFinite() && o.y1.isFinite() && o.x2.isFinite() && o.y2.isFinite()
                is DrawOp.Stroke -> o.points.all { it.isFinite() }
            }
            if (!ok) return false
        }
        return true
    }

    private fun fallback(source: String, size: Float, metrics: GlyphMetrics): FormulaLayout = try {
        val text = source.trim()
        val w = metrics.advance(text, size, GlyphStyle.ROMAN).takeIf { it.isFinite() } ?: 0f
        val a = metrics.ascent(size).takeIf { it.isFinite() } ?: size
        val d = metrics.descent(size).takeIf { it.isFinite() } ?: (size * 0.25f)
        FormulaLayout(w, a, d, if (text.isEmpty()) emptyList() else listOf(DrawOp.Glyph(text, 0f, 0f, size, GlyphStyle.ROMAN)), emptyList())
    } catch (e: Exception) {
        FormulaLayout(0f, size * 0.8f, size * 0.2f, emptyList(), emptyList())
    }
}

// ---------------------------------------------------------------- v17: elemanlarına ayırma

internal object Atomizer {
    private val greek = setOf(
        "alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "zeta", "eta", "theta", "vartheta", "iota", "kappa",
        "lambda", "mu", "nu", "xi", "pi", "varpi", "rho", "varrho", "sigma", "varsigma", "tau", "upsilon", "phi", "varphi",
        "chi", "psi", "omega", "Gamma", "Delta", "Theta", "Lambda", "Xi", "Pi", "Sigma", "Upsilon", "Phi", "Psi", "Omega",
    )
    /** Tek süslü parantez argümanı olduğu gibi kopyalanan komutlar (yazı, yazı tipi, ortam adı). */
    private val verbatim = setOf(
        "text", "textrm", "mbox", "textnormal", "hbox", "textup", "textit", "textbf", "operatorname",
        "begin", "end", "mathrm", "mathup", "mathsf", "mathtt", "mathbb", "mathcal", "mathscr", "label",
        // v45: renk adı (ve FormulaEval işaretleri) yuvaya bölünmez
        "color",
    )
    /** Ardından gelen ayraç olduğu gibi kopyalanan komutlar. */
    private val delimiters = setOf(
        "left", "right", "middle", "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr", "biggl", "biggr",
    )

    private fun Char.isLetter() = this in 'a'..'z' || this in 'A'..'Z'

    fun atomize(source: String): String {
        val out = StringBuilder(source.length * 3)
        var n = 0
        fun slot(value: String) { n++; out.append("\\slot{").append(n).append("}{").append(value).append('}') }
        var i = 0
        val s = source
        /** [from] konumundaki süslü grubun bittiği yer (kapanıştan sonrası); grup değilse -1. */
        fun groupEnd(from: Int): Int {
            var k = from
            while (k < s.length && s[k].isWhitespace()) k++
            if (k >= s.length || s[k] != '{') return -1
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
        /** Tek argüman (grup ya da tek simge/komut) ve bittiği yer. */
        fun oneArg(from: Int): Pair<String, Int> {
            var k = from
            while (k < s.length && s[k].isWhitespace()) k++
            if (k >= s.length) return "" to k
            if (s[k] == '{') {
                val e = groupEnd(k)
                return s.substring(k + 1, (e - 1).coerceAtLeast(k + 1)) to e
            }
            if (s[k] == '\\') {
                var e = k + 1
                if (e < s.length && s[e].isLetter()) { while (e < s.length && s[e].isLetter()) e++ } else e = minOf(e + 1, s.length)
                return s.substring(k, e) to e
            }
            val e = k + Character.charCount(s.codePointAt(k))
            return s.substring(k, e) to e
        }
        while (i < s.length) {
            val c = s[i]
            // v45: otomatik hesap sonucu (FormulaEval) yuvaya bölünmez, olduğu gibi kalır
            if (c == '\\' && s.startsWith(FormulaEval.RESULT_MARK, i)) { out.append(s, i, s.length); break }
            when {
                c == '\\' -> {
                    val st = i
                    i++
                    if (i >= s.length) { out.append(c); break }
                    if (!s[i].isLetter()) { out.append(s, st, i + 1); i++; continue }
                    while (i < s.length && s[i].isLetter()) i++
                    val name = s.substring(st + 1, i)
                    when {
                        name == "slot" -> {
                            val (_, afterName) = oneArg(i)
                            val (value, afterValue) = oneArg(afterName)
                            slot(value)
                            i = afterValue
                        }
                        name in greek -> slot("\\$name")
                        name in verbatim -> {
                            out.append(s, st, i)
                            if (name == "operatorname" && i < s.length && s[i] == '*') { out.append('*'); i++ }
                            val e = groupEnd(i)
                            if (e > 0) { out.append(s, i, e); i = e }
                        }
                        name in delimiters -> {
                            out.append(s, st, i)
                            var k = i
                            while (k < s.length && s[k].isWhitespace()) k++
                            if (k < s.length) {
                                val (d, e) = oneArg(k)
                                out.append(s, i, k).append(if (s[k] == '{') "{$d}" else d)
                                // Komut adı sonraki harfe yapışmasın
                                if (d.startsWith("\\") && d.drop(1).all { it.isLetter() } && e < s.length && s[e].isLetter()) out.append(' ')
                                i = e
                            }
                        }
                        else -> {
                            out.append(s, st, i)
                            if (i < s.length && s[i].isLetter()) out.append(' ')
                        }
                    }
                }
                c == '^' || c == '_' -> {
                    out.append(c)
                    i++
                    var k = i
                    while (k < s.length && s[k].isWhitespace()) k++
                    if (k < s.length && s[k] != '{' && s[k] != '\\') {
                        // Tek karakterlik üs/alt indis gruplanır: x^2 → x^{…}
                        val single = s[k]
                        out.append('{')
                        if (single.isLetter() || single.isDigit()) slot(single.toString()) else out.append(single)
                        out.append('}')
                        i = k + 1
                    }
                }
                c.isDigit() -> {
                    var e = i
                    while (e < s.length && s[e].isDigit()) e++
                    if (e + 1 < s.length && s[e] == '.' && s[e + 1].isDigit()) {
                        e++
                        while (e < s.length && s[e].isDigit()) e++
                    }
                    slot(s.substring(i, e))
                    i = e
                }
                c.isLetter() -> { slot(c.toString()); i++ }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    fun plain(source0: String): String {
        // v45: otomatik sonuç atılır, değiştirilen bağıntı özgününe döner; eklenen çarpı ve parantezler kalır
        val source = FormulaEval.stripResult(source0)
        val occ = SlotSource.scan(source)
        if (occ.isEmpty()) return source
        val sb = StringBuilder()
        var last = 0
        var i = 0
        // En dıştaki yuvalar (iç içe olanlar dıştakinin değerinde kalır)
        val outer = ArrayList<SlotSource.Occ>()
        for (o in occ.sortedBy { it.start }) if (outer.none { o.start >= it.start && o.start < it.valueEnd + 1 }) outer += o
        for (o in outer) {
            sb.append(source, last, o.start)
            val value = source.substring(o.valueStart, o.valueEnd)
            val end = if (o.braced && o.closed) o.valueEnd + 1 else o.valueEnd
            val simple = value.length == 1 || value.all { it.isDigit() || it == '.' } || (value.startsWith("\\") && value.drop(1).all { it.isLetter() })
            sb.append(if (simple) value else "{$value}")
            if (value.startsWith("\\") && value.drop(1).all { it.isLetter() } && end < source.length) {
                // Komut adının ardından harf ya da yeni yuva geliyorsa boşluk
                val next = source[end]
                if (next.isLetter() || next == '\\') sb.append(' ')
            }
            last = end
            i++
        }
        sb.append(source, last, source.length)
        return sb.toString()
    }
}

// ---------------------------------------------------------------- kaynak düzeyinde yuvalar

internal object SlotSource {
    class Occ(val name: String, val start: Int, val valueStart: Int, val valueEnd: Int, val braced: Boolean, val closed: Boolean)

    private class Arg(val innerStart: Int, val innerEnd: Int, val after: Int, val braced: Boolean, val closed: Boolean)

    private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

    private fun arg(s: String, from: Int): Arg? {
        var i = from
        while (i < s.length && s[i].isWhitespace()) i++
        if (i >= s.length) return null
        val c = s[i]
        if (c == '{') {
            val st = i + 1
            var k = st
            var d = 1
            while (k < s.length) {
                val ch = s[k]
                if (ch == '\\' && k + 1 < s.length) { k += 2; continue }
                if (ch == '{') d++
                if (ch == '}') { d--; if (d == 0) return Arg(st, k, k + 1, braced = true, closed = true) }
                k++
            }
            return Arg(st, s.length, s.length, braced = true, closed = false)
        }
        if (c == '}') return null
        if (c == '\\') {
            var k = i + 1
            if (k < s.length && s[k].isAsciiLetter()) { while (k < s.length && s[k].isAsciiLetter()) k++ } else k = minOf(k + 1, s.length)
            return Arg(i, k, k, braced = false, closed = true)
        }
        val e = i + Character.charCount(s.codePointAt(i))
        return Arg(i, e, e, braced = false, closed = true)
    }

    fun scan(s: String): List<Occ> {
        val out = ArrayList<Occ>()
        var i = 0
        while (i < s.length) {
            if (s[i] != '\\') { i++; continue }
            val st = i
            i++
            if (i >= s.length) break
            if (!s[i].isAsciiLetter()) { i++; continue }
            val ns = i
            while (i < s.length && s[i].isAsciiLetter()) i++
            if (s.regionMatches(ns, "slot", 0, 4) && i - ns == 4) {
                val n = arg(s, i) ?: continue
                val v = arg(s, n.after) ?: continue
                out.add(Occ(s.substring(n.innerStart, n.innerEnd).trim(), st, v.innerStart, v.innerEnd, v.braced, v.closed))
                i = if (v.braced) v.innerStart else v.after // iç içe yuvalar da bulunsun
            }
        }
        return out
    }

    fun set(source: String, name: String, value: String): String {
        val occ = scan(source).filter { it.name == name }
        if (occ.isEmpty()) return source
        val chosen = ArrayList<Occ>()
        for (o in occ.sortedBy { it.start }) {
            if (chosen.any { o.start >= it.valueStart && o.start < it.valueEnd }) continue
            chosen.add(o)
        }
        val v = sanitize(value)
        val sb = StringBuilder(source)
        for (o in chosen.sortedByDescending { it.valueStart }) {
            val rep = when {
                !o.braced -> "{$v}"
                !o.closed -> "$v}"
                else -> v
            }
            sb.replace(o.valueStart, o.valueEnd, rep)
        }
        return sb.toString()
    }

    /** Değerin kaynağı bozmaması için: dengesiz parantezler dengelenir, sondaki tek `\` atılır. */
    fun sanitize(value: String): String {
        var v = value
        var trailing = 0
        while (trailing < v.length && v[v.length - 1 - trailing] == '\\') trailing++
        if (trailing % 2 == 1) v = v.dropLast(1)
        val sb = StringBuilder()
        var depth = 0
        var k = 0
        while (k < v.length) {
            val c = v[k]
            if (c == '\\' && k + 1 < v.length) { sb.append(c).append(v[k + 1]); k += 2; continue }
            when (c) {
                '{' -> { depth++; sb.append(c) }
                '}' -> if (depth > 0) { depth--; sb.append(c) }
                else -> sb.append(c)
            }
            k++
        }
        repeat(depth) { sb.append('}') }
        return sb.toString()
    }
}

// ---------------------------------------------------------------- düz metin

internal object PlainText {
    private class Tok(val s: String, var left: AtomType, var right: AtomType, val space: Boolean = false)

    fun of(root: RowNode): String = row(root.items).replace(Regex("\\s+"), " ").trim()

    private fun row(items: List<Node>): String {
        val nodes = SlotRules.apply(items)
        val toks = ArrayList<Tok>()
        for (n in nodes) {
            when (n) {
                is SpaceNode -> if (n.em >= 0.15f) toks.add(Tok(" ", ORD, ORD, true))
                is StyleNode -> {}
                else -> toks.add(tok(n))
            }
        }
        var prev: Tok? = null
        for (t in toks) {
            if (t.space) continue
            if (t.left == BIN && (prev == null || prev.right in UNARY_BEFORE)) { t.left = ORD; t.right = ORD }
            if ((t.left == REL || t.left == CLOSE || t.left == PUNCT) && prev?.right == BIN) { prev.left = ORD; prev.right = ORD }
            prev = t
        }
        if (prev?.right == BIN) { prev.left = ORD; prev.right = ORD }
        val sb = StringBuilder()
        prev = null
        for (t in toks) {
            if (t.space) { sb.append(' '); continue }
            if (t.s.isEmpty()) continue
            val p = prev
            if (p != null) {
                val spaced = t.left == BIN || t.left == REL || p.right == BIN || p.right == REL || p.right == PUNCT ||
                    (p.right == OP && t.left != OPEN && t.left != PUNCT && t.left != CLOSE) ||
                    (t.left == OP && p.right != OPEN) ||
                    (t.left == INNER && p.right != OPEN) || (p.right == INNER && t.left != CLOSE && t.left != PUNCT)
                if (spaced) sb.append(' ')
            }
            sb.append(t.s)
            prev = t
        }
        return sb.toString()
    }

    private val UNARY_BEFORE = setOf(BIN, OP, REL, OPEN, PUNCT)

    private fun str(n: Node): String = tok(n).s

    private fun tok(n: Node): Tok = when (n) {
        is SymNode -> if (n.kind == SymNode.K_AUTO_DOT) Tok(n.text, ORD, ORD) else Tok(n.text, n.type, n.type)
        is RowNode -> Tok(row(n.items), ORD, ORD)
        is ScriptsNode -> {
            val base = tok(n.base)
            Tok(scripts(n, base.s), base.left, base.right)
        }
        is FracNode -> Tok(frac(n), INNER, INNER)
        is SqrtNode -> Tok(sqrt(n), ORD, ORD)
        is AccentNode -> Tok(accent(n), ORD, ORD)
        is FenceNode -> Tok(fence(n), OPEN, CLOSE)
        is MatrixNode -> Tok(matrix(n), ORD, ORD)
        is TextNode -> Tok(n.text.replace(' ', ' '), ORD, ORD)
        is SlotNode -> {
            val single = n.body.items.singleOrNull()
            val inner = single?.let { tok(it) }
            Tok(row(n.body.items), n.type ?: inner?.left ?: ORD, n.type ?: inner?.right ?: ORD)
        }
        is BigOpNode -> Tok(BIG_OPS[n.kind] ?: "∑", OP, OP)
        is FuncNode -> Tok(n.name, OP, OP)
        is DotsNode -> Tok(when (n.kind) { 'c' -> "⋯"; 'v' -> "⋮"; 'd' -> "⋱"; else -> "…" }, INNER, INNER)
        is SpaceNode -> Tok(" ", ORD, ORD, true)
        is StyleNode -> Tok("", ORD, ORD)
    }

    private fun isDegree(n: Node?): Boolean {
        val s = if (n is RowNode) n.items.singleOrNull() else n
        return s is SymNode && (s.text == "∘" || s.text == "°")
    }

    private fun scripts(n: ScriptsNode, baseStr: String): String {
        val sb = StringBuilder(if (n.base is FracNode) "($baseStr)" else baseStr)
        repeat(n.primes) { sb.append('′') }
        n.sub?.let { sb.append(convert(str(it), SUB, "_")) }
        n.sup?.let { if (isDegree(it)) sb.append('°') else sb.append(convert(str(it), SUP, "^")) }
        return sb.toString()
    }

    private fun convert(s: String, map: Map<Char, Char>, mark: String): String {
        if (s.isEmpty()) return ""
        if (s.all { map.containsKey(it) }) return s.map { map.getValue(it) }.joinToString("")
        return if (s.codePointCount(0, s.length) == 1) "$mark$s" else "$mark($s)"
    }

    private fun simple(s: String) = !s.contains(' ')

    private fun frac(n: FracNode): String {
        val a = str(n.num)
        val b = str(n.den)
        if (!n.bar) return "$a, $b"
        if (simple(a) && simple(b)) return "$a/$b"
        return "${if (simple(a)) a else "($a)"} / ${if (simple(b)) b else "($b)"}"
    }

    private fun sqrt(n: SqrtNode): String {
        val b = str(n.body)
        val body = if (b.codePointCount(0, b.length) <= 1 || b.all { it.isDigit() }) b else "($b)"
        val sign = when (val idx = n.index?.let { str(it) }) {
            null, "", "2" -> "√"
            "3" -> "∛"
            "4" -> "∜"
            else -> convert(idx, SUP, "^") + "√"
        }
        return sign + body
    }

    private fun accent(n: AccentNode): String {
        val b = str(n.body)
        val mark = when (n.kind) {
            AccentKind.VEC -> '⃗'
            AccentKind.LEFT_VEC -> '⃖'
            AccentKind.OVERLINE -> '̅'
            AccentKind.UNDERLINE -> '̲'
            AccentKind.BAR -> '̄'
            AccentKind.HAT, AccentKind.WIDEHAT -> '̂'
            AccentKind.DOT -> '̇'
            AccentKind.DDOT -> '̈'
            AccentKind.TILDE -> '̃'
        }
        if (b.isEmpty()) return ""
        val perChar = n.kind == AccentKind.OVERLINE || n.kind == AccentKind.UNDERLINE || n.kind == AccentKind.BAR
        return if (perChar) b.map { "$it$mark" }.joinToString("") else "$b$mark"
    }

    private fun fence(n: FenceNode): String {
        val inner = n.body.items.singleOrNull()
        if (n.binom && inner is FracNode) return "C(${str(inner.num)}, ${str(inner.den)})"
        val l = if (n.left == ".") "" else n.left
        val r = if (n.right == ".") "" else n.right
        return l + row(n.body.items) + r
    }

    private fun matrix(n: MatrixNode): String {
        val cases = n.env == "cases" || n.env == "dcases" || n.env == "rcases"
        val aligned = n.env !in setOf("matrix", "pmatrix", "bmatrix", "Bmatrix", "vmatrix", "Vmatrix", "smallmatrix", "array")
        val sep = if (cases || aligned) " " else ", "
        val body = n.rows.joinToString("; ") { r -> r.map { row(it.items).trim() }.filter { it.isNotEmpty() || !aligned }.joinToString(sep) }
        return when (n.env) {
            "pmatrix" -> "($body)"
            "bmatrix" -> "[$body]"
            "Bmatrix" -> "{$body}"
            "vmatrix" -> "|$body|"
            "Vmatrix" -> "‖$body‖"
            "cases", "dcases" -> "{ $body }"
            else -> body
        }
    }

    private val BIG_OPS = mapOf(
        "sum" to "∑", "prod" to "∏", "coprod" to "∐", "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮",
        "oiint" to "∯", "bigcup" to "⋃", "bigcap" to "⋂",
    )

    private val SUP: Map<Char, Char> = zip(
        "0123456789+−-=()niabcdefghjklmoprstuvwxyz",
        "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁻⁼⁽⁾ⁿⁱᵃᵇᶜᵈᵉᶠᵍʰʲᵏˡᵐᵒᵖʳˢᵗᵘᵛʷˣʸᶻ",
    )
    private val SUB: Map<Char, Char> = zip(
        "0123456789+−-=()aeoxhklmnpstijruv,",
        "₀₁₂₃₄₅₆₇₈₉₊₋₋₌₍₎ₐₑₒₓₕₖₗₘₙₚₛₜᵢⱼᵣᵤᵥ,",
    )

    private fun zip(a: String, b: String): Map<Char, Char> = a.indices.associate { a[it] to b[it] }
}
