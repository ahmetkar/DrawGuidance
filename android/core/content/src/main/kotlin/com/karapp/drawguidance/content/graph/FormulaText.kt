package com.karapp.drawguidance.content.graph

/**
 * Kullanıcının yazdığı formülü ders kitabı yazımına çevirir: `x^2-3x` → `x² − 3x`, `sqrt(x)` → `√x`,
 * `abs(x)` → `|x|`, `pi` → `π`, `2*x` → `2x`, `exp(-x)` → `e⁻ˣ`, `floor(x)` → `⌊x⌋`, ondalık virgül.
 */
internal object FormulaText {

    private sealed class Tok {
        data class Num(val text: String) : Tok()
        data class Name(val text: String) : Tok()
        data class Op(val c: Char) : Tok()
        object LP : Tok()
        object RP : Tok()
        object Bar : Tok()
    }

    private fun tokenize(src: String): List<Tok>? {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val s = i
                    while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                    out += Tok.Num(src.substring(s, i))
                }
                c.isLetter() -> {
                    val name = Expression.matchName(src, i) ?: return null
                    out += Tok.Name(name)
                    i += name.length
                }
                c == '(' -> { out += Tok.LP; i++ }
                c == ')' -> { out += Tok.RP; i++ }
                c == '|' -> { out += Tok.Bar; i++ }
                c in "+-*/^=" -> { out += Tok.Op(c); i++ }
                else -> return null
            }
        }
        return out
    }

    private const val SUP_FROM = "0123456789−+xyn()"
    private const val SUP_TO = "⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺ˣʸⁿ⁽⁾"

    private fun superscript(s: String): String? {
        val t = s.replace(" ", "")
        if (t.isEmpty()) return null
        val sb = StringBuilder()
        for (c in t) {
            val k = SUP_FROM.indexOf(c)
            if (k < 0) return null
            sb.append(SUP_TO[k])
        }
        return sb.toString()
    }

    private fun isSimple(s: String) = s.isNotEmpty() && s.all { it.isLetterOrDigit() || it == ',' || it == 'π' || it in SUP_TO }

    private class Printer(val t: List<Tok>) {
        var i = 0

        fun seq(stopAtBar: Boolean = false): String {
            val sb = StringBuilder()
            var prevOperand = false
            var prevNumber = false
            while (i < t.size) {
                val tok = t[i]
                if (tok is Tok.RP) break
                if (tok is Tok.Bar && stopAtBar) break
                when (tok) {
                    is Tok.Num -> { sb.append(tok.text.replace('.', ',')); i++; prevOperand = true; prevNumber = true; continue }
                    is Tok.Name -> {
                        i++
                        sb.append(nameOrFunction(tok.text))
                        prevOperand = true
                    }
                    Tok.LP -> {
                        i++
                        val inner = seq()
                        if (i < t.size && t[i] is Tok.RP) i++
                        sb.append('(').append(inner).append(')')
                        prevOperand = true
                    }
                    Tok.Bar -> {
                        i++
                        val inner = seq(stopAtBar = true)
                        if (i < t.size && t[i] is Tok.Bar) i++
                        sb.append('|').append(inner).append('|')
                        prevOperand = true
                    }
                    is Tok.Op -> {
                        i++
                        when (tok.c) {
                            '^' -> sb.append(exponent())
                            '*' -> {
                                val next = t.getOrNull(i)
                                if (!(prevNumber && (next is Tok.Name || next is Tok.LP))) sb.append('·')
                                prevOperand = false
                            }
                            '/' -> { sb.append('/'); prevOperand = false }
                            '-' -> { sb.append(if (prevOperand) " − " else "−"); prevOperand = false }
                            '+' -> { if (prevOperand) sb.append(" + "); prevOperand = false }
                            '=' -> { sb.append(" = "); prevOperand = false }
                        }
                    }
                    Tok.RP -> {}
                }
                prevNumber = false
            }
            return sb.toString()
        }

        /** '^' sonrası üs: basitse üst simge, değilse ^(…). */
        private fun exponent(): String {
            val tok = t.getOrNull(i) ?: return "^"
            val raw: String
            var grouped = false
            when {
                tok is Tok.LP -> {
                    i++
                    raw = seq()
                    if (i < t.size && t[i] is Tok.RP) i++
                    grouped = true
                }
                tok is Tok.Op && tok.c == '-' -> {
                    i++
                    raw = "−" + atomText()
                }
                else -> raw = atomText()
            }
            superscript(raw)?.let { return it }
            return if (grouped || !isSimple(raw)) "^($raw)" else "^$raw"
        }

        private fun atomText(): String {
            val tok = t.getOrNull(i) ?: return ""
            return when (tok) {
                is Tok.Num -> { i++; tok.text.replace('.', ',') }
                is Tok.Name -> { i++; nameOrFunction(tok.text) }
                Tok.LP -> {
                    i++
                    val inner = seq()
                    if (i < t.size && t[i] is Tok.RP) i++
                    "($inner)"
                }
                else -> ""
            }
        }

        private fun nameOrFunction(name: String): String {
            if (name !in Expression.FUNCTIONS) return if (name == "pi") "π" else name
            val parens = t.getOrNull(i) is Tok.LP
            val arg: String = if (parens) {
                i++
                val a = seq()
                if (i < t.size && t[i] is Tok.RP) i++
                a
            } else atomText()
            return when (name) {
                "sqrt" -> if (isSimple(arg)) "√$arg" else "√($arg)"
                "cbrt" -> if (isSimple(arg)) "∛$arg" else "∛($arg)"
                "abs" -> "|$arg|"
                "floor" -> "⟦$arg⟧"
                "ceil" -> "⌈$arg⌉"
                "exp" -> superscript(arg)?.let { "e$it" } ?: "e^($arg)"
                else -> {
                    val shown = when (name) {
                        "asin" -> "arcsin"; "acos" -> "arccos"; "atan" -> "arctan"
                        "tg" -> "tan"; "cotg" -> "cot"; "sgn" -> "sign"
                        else -> name
                    }
                    if (parens || !isSimple(arg)) "$shown($arg)" else "$shown $arg"
                }
            }
        }
    }

    /** Formül gövdesinin güzel yazımı; ayrıştırılamazsa girilen metin (ASCII işlemler düzeltilerek). */
    fun pretty(body: String): String {
        val norm = Expression.normalize(body)
        val toks = tokenize(norm) ?: return body.trim().replace("*", "·").replace("-", "−")
        return try {
            val pr = Printer(toks)
            val sb = StringBuilder(pr.seq())
            while (pr.i < toks.size) { pr.i++; sb.append(')').append(pr.seq()) }
            sb.toString().trim()
        } catch (_: Exception) {
            body.trim()
        }
    }

    /** Formül kutusu metni: "y = …", "z = …", kullanıcı ön eki ("f(x) = …") ya da kapalı denklem. */
    fun label(text: String, lhs: String = "y"): String {
        if (text.isBlank()) return ""
        val body = Expression.body(text)
        if (body.contains('=')) return pretty(body)
        val prefix = Expression.prefix(text)?.let { pretty(it) }?.takeIf { it.isNotBlank() } ?: lhs
        return "$prefix = ${pretty(body)}"
    }

    /**
     * v18: güzel yazımdaki formülde x yerine k·x yazar (desen). Katsayılı terimde katsayı çarpılır ("3x" → "6x"),
     * üslü, kök içi ya da bölen x parantezlenir ("x²" → "(2x)²", "√x" → "√(2x)", "1/x" → "1/(2x)"), üsteki ˣ
     * üst simge katsayı alır ("2ˣ" → "2²ˣ"); diğerleri "2x" olur ("sin(x)" → "sin(2x)"). Adların içindeki x'e
     * dokunulmaz.
     */
    fun scaleX(label: String, k: Int): String {
        if (k == 1) return label
        val supK = superscript(k.toString()) ?: return label
        val sb = StringBuilder()
        var i = 0
        while (i < label.length) {
            val c = label[i]
            if (c == 'ˣ') {
                sb.append(supK).append('ˣ')
                i++
                continue
            }
            val prevLetter = i > 0 && label[i - 1].isLetter() && label[i - 1] != 'π'
            val nextLetter = i + 1 < label.length && label[i + 1].isLetter()
            if (c != 'x' || prevLetter || nextLetter) {
                sb.append(c)
                i++
                continue
            }
            // Hemen önündeki sayı katsayıdır ("3x", "0,5x")
            var j = sb.length
            while (j > 0 && (sb[j - 1].isDigit() || sb[j - 1] == ',')) j--
            val coef = sb.substring(j)
            val next = label.getOrNull(i + 1)
            val prev = sb.lastOrNull()
            when {
                coef.isNotEmpty() && coef.first().isDigit() -> {
                    val v = coef.replace(',', '.').toDoubleOrNull()
                    sb.setLength(j)
                    val product = if (v == null) null else v * k
                    val text = product?.let { if (it == kotlin.math.round(it)) it.toLong().toString() else it.toString().replace('.', ',') }
                    sb.append(if (text == null) coef + k.toString() else if (text == "1") "" else text).append('x')
                }
                (next != null && (next in SUP_TO || next == '^')) || prev == '√' || prev == '∛' || prev == '/' -> sb.append('(').append(k).append("x)")
                else -> sb.append(k).append('x')
            }
            i++
        }
        return sb.toString()
    }
}
