package com.karapp.drawguidance.content.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/*
 * v45 genel matematik ifadesi ayrıştırıcısı (grafikler, parametreli fen şekilleri, formül hesaplama).
 *
 * Desteklenenler: + − · × ÷ / ^ ( ) [ ] |…|, işlem önceliği (üs sağdan birleşir; -x^2 = −(x²), 2^-x = 2^(−x)),
 * örtük çarpım (2x, 3sin x, x(x+1), (a)(b), 2π, xy), parantezsiz fonksiyon (sin x, sin 2x, √x), sin²x biçimi,
 * fonksiyonlar (sin cos tan/tg cot sec csc asin/arcsin acos atan sinh cosh tanh sqrt cbrt abs exp ln log log2
 * floor ceil round sign min max), log_b(x) tabanlı logaritma, sabitler (pi/π, e), derece işareti (30°), faktöriyel
 * (5!), Unicode yazımlar (x², x⁻¹, √, ∛, π, ·, ×, ÷, −, ≤, ≥, ≠) ve Türkçe ondalık virgül (2,5). Bağıntılar
 * (=, <, >, ≤, ≥, ≠ ve <=, >=, !=) zincir olabilir: -1 ≤ x < 4.
 *
 * Hatalar [MathError] ile döner: kod + metindeki konum (uygulama kodu kendi diline çevirir).
 */

/** Ayrıştırma ya da hesaplama hatası; [pos] kaynak metindeki konum (bilinmiyorsa -1). */
data class MathError(val code: Code, val pos: Int = -1, val detail: String = "") {
    enum class Code {
        /** Boş ifade. */
        EMPTY,
        /** Beklenmeyen karakter ([detail] = karakter). */
        UNEXPECTED,
        /** Kapanmayan ya da fazla parantez. */
        PARENTHESIS,
        /** Eksik işlenen (ör. "2+", "*3"). */
        MISSING_OPERAND,
        /** Bilinmeyen ad ya da tanımsız değişken ([detail] = ad). */
        UNKNOWN_NAME,
        /** Fonksiyonun bağımsız değişken sayısı yanlış. */
        ARGUMENTS,
        /** Sıfıra bölme. */
        DIVISION_BY_ZERO,
        /** Tanım dışı (negatif sayının karekökü, ln(0)…). [detail] = fonksiyon adı. */
        DOMAIN,
        /** Beklenen yerde bağıntı (=, <…) yok ya da fazla. */
        RELATION,
        /** Sonuç çok büyük / sonsuz. */
        OVERFLOW,
    }
}

class MathException(val error: MathError) : RuntimeException(error.code.name + " @" + error.pos + " " + error.detail)

/** Bağıntı işaretleri. */
enum class Rel(val symbol: String, val tex: String) {
    EQ("=", "="), NE("≠", "\\neq"), LT("<", "<"), GT(">", ">"), LE("≤", "\\le"), GE("≥", "\\ge");

    /** a [this] b doğru mu ([eps] bağıl eşitlik payı). */
    fun holds(a: Double, b: Double, eps: Double = 1e-12): Boolean {
        val eq = abs(a - b) <= eps * maxOf(1.0, abs(a), abs(b))
        return when (this) {
            EQ -> eq
            NE -> !eq
            LT -> a < b && !eq
            GT -> a > b && !eq
            LE -> a < b || eq
            GE -> a > b || eq
        }
    }

    /** Sıralama sonucundan (-1, 0, 1) doğru bağıntı. */
    fun holdsCmp(c: Int): Boolean = when (this) {
        EQ -> c == 0
        NE -> c != 0
        LT -> c < 0
        GT -> c > 0
        LE -> c <= 0
        GE -> c >= 0
    }

    /** Taraflar yer değiştirince (a < b ⇔ b > a). */
    fun flipped(): Rel = when (this) { LT -> GT; GT -> LT; LE -> GE; GE -> LE; else -> this }
}

/** İfade ağacı. */
sealed class MNode {
    /** Ondalık sayı (kaynaktaki yazımıyla; kesin hesap için). */
    data class Num(val text: String) : MNode() { val value: Double = text.toDouble() }
    data class Const(val name: String) : MNode()
    data class Var(val name: String) : MNode()
    data class Neg(val a: MNode) : MNode()
    data class Bin(val op: Char, val a: MNode, val b: MNode, val implicit: Boolean = false) : MNode()
    data class Pow(val a: MNode, val b: MNode) : MNode()
    data class Call(val fn: String, val args: List<MNode>) : MNode()
    data class Fact(val a: MNode) : MNode()
    /** Derece işareti: a° (radyana çevrilir). */
    data class Deg(val a: MNode) : MNode()

    /** Ağaçtaki değişken adları. */
    fun variables(): Set<String> {
        val out = LinkedHashSet<String>()
        fun walk(n: MNode) {
            when (n) {
                is Var -> out += n.name
                is Neg -> walk(n.a)
                is Bin -> { walk(n.a); walk(n.b) }
                is Pow -> { walk(n.a); walk(n.b) }
                is Call -> n.args.forEach(::walk)
                is Fact -> walk(n.a)
                is Deg -> walk(n.a)
                is Num, is Const -> Unit
            }
        }
        walk(this)
        return out
    }
}

/** Bağıntı zinciri: sides[0] ops[0] sides[1] ops[1] sides[2] … (bağıntısız ifadede ops boş). */
data class MRelation(val sides: List<MNode>, val ops: List<Rel>) {
    val isRelation: Boolean get() = ops.isNotEmpty()
}

/** Açı birimi: trigonometrik fonksiyonların bağımsız değişkeni (ve ters fonksiyonların sonucu). */
enum class AngleMode { RAD, DEG }

object MathExpr {

    val FUNCTIONS: Set<String> = setOf(
        "sin", "cos", "tan", "tg", "cot", "cotg", "ctg", "sec", "csc", "asin", "acos", "atan", "arcsin", "arccos", "arctan",
        "sinh", "cosh", "tanh", "sqrt", "cbrt", "abs", "exp", "ln", "log", "lg", "log2", "floor", "ceil", "round", "sign", "sgn",
        "min", "max",
    )
    val CONSTANTS: Set<String> = setOf("pi", "e")
    private val TRIG = setOf("sin", "cos", "tan", "tg", "cot", "cotg", "ctg", "sec", "csc")
    private val INV_TRIG = setOf("asin", "acos", "atan", "arcsin", "arccos", "arctan")
    private val TWO_ARGS = setOf("min", "max")

    /** Yalnız bir ifade (bağıntısız). */
    fun parse(text: String, names: Set<String> = emptySet()): MNode {
        val r = parseRelation(text, names)
        if (r.isRelation) throw MathException(MathError(MathError.Code.RELATION, -1))
        return r.sides[0]
    }

    /** Hata fırlatmadan: ağaç ya da hata. */
    fun tryParse(text: String, names: Set<String> = emptySet()): Pair<MNode?, MathError?> = try {
        parse(text, names) to null
    } catch (e: MathException) {
        null to e.error
    }

    /** Bağıntı zinciri ya da tek ifade. [names]: çok harfli değişken adları (ör. "do", "di", "v0"). */
    fun parseRelation(text: String, names: Set<String> = emptySet()): MRelation {
        val norm = Normalized.of(text)
        if (norm.text.isBlank()) throw MathException(MathError(MathError.Code.EMPTY, 0))
        val p = Parser(norm, names)
        val r = p.relation()
        p.expectEnd()
        return r
    }

    fun tryParseRelation(text: String, names: Set<String> = emptySet()): Pair<MRelation?, MathError?> = try {
        parseRelation(text, names) to null
    } catch (e: MathException) {
        null to e.error
    }

    // ------------------------------------------------------------------ değerlendirme (Double)

    /**
     * Hesaplar; tanımsız noktada NaN döner (grafik orada kesilir). Bilinmeyen değişken [MathException] fırlatır.
     */
    fun eval(n: MNode, vars: Map<String, Double> = emptyMap(), mode: AngleMode = AngleMode.RAD): Double =
        Evaluator(vars, mode, strict = false).eval(n)

    /** Denetimli hesap: sıfıra bölme, tanım dışı ve sonsuz sonuç [MathException] fırlatır. */
    fun evalStrict(n: MNode, vars: Map<String, Double> = emptyMap(), mode: AngleMode = AngleMode.RAD): Double =
        Evaluator(vars, mode, strict = true).eval(n)

    /** Hızlı tekrarlı hesap için derlenmiş fonksiyon (değişkenler [order] sırasıyla dizide). */
    fun compile(n: MNode, order: List<String>, mode: AngleMode = AngleMode.RAD): (DoubleArray) -> Double {
        val idx = order.withIndex().associate { it.value to it.index }
        return Compiler(idx, mode).c(n)
    }

    /** Negatif tabanda tek paydalı kesirli üsleri de hesaplayan kuvvet (x^(1/3), x^(2/3)). */
    fun powReal(b: Double, e: Double): Double {
        if (b >= 0.0 || e == floor(e) || !e.isFinite()) return b.pow(e)
        for (q in 3..15 step 2) {
            val n = e * q
            val rn = round(n)
            if (abs(n - rn) < 1e-9) {
                val m = (-b).pow(e)
                return if (rn.toLong() % 2L != 0L) -m else m
            }
        }
        return Double.NaN
    }

    fun factorial(v: Double): Double {
        if (v < 0 || v != floor(v)) return Double.NaN
        if (v > 170) return Double.POSITIVE_INFINITY
        var r = 1.0
        var k = 2.0
        while (k <= v) { r *= k; k += 1.0 }
        return r
    }

    internal fun isTrig(fn: String) = fn in TRIG
    internal fun isInvTrig(fn: String) = fn in INV_TRIG

    /** Tek bağımsız değişkenli fonksiyonun değeri (açı biriminde); tanımsızsa NaN. */
    fun apply(fn: String, a: Double, mode: AngleMode = AngleMode.RAD): Double {
        val r = if (mode == AngleMode.DEG && fn in TRIG) a * PI / 180.0 else a
        val v = when (fn) {
            "sin" -> sinExact(r)
            "cos" -> cosExact(r)
            "tan", "tg" -> tanExact(r)
            "cot", "cotg", "ctg" -> tanExact(r).let { if (it == 0.0) Double.NaN else 1.0 / it }
            "sec" -> cosExact(r).let { if (it == 0.0) Double.NaN else 1.0 / it }
            "csc" -> sinExact(r).let { if (it == 0.0) Double.NaN else 1.0 / it }
            "asin", "arcsin" -> asin(a)
            "acos", "arccos" -> acos(a)
            "atan", "arctan" -> atan(a)
            "sinh" -> sinh(a)
            "cosh" -> cosh(a)
            "tanh" -> tanh(a)
            "sqrt" -> sqrt(a)
            "cbrt" -> cbrt(a)
            "abs" -> abs(a)
            "exp" -> exp(a)
            "ln" -> if (a == 0.0) Double.NaN else ln(a)
            "log", "lg" -> if (a == 0.0) Double.NaN else log10(a)
            "log2" -> if (a == 0.0) Double.NaN else log2(a)
            "floor" -> floor(a)
            "ceil" -> ceil(a)
            "round" -> floor(a + 0.5)
            "sign", "sgn" -> sign(a)
            else -> throw MathException(MathError(MathError.Code.UNKNOWN_NAME, -1, fn))
        }
        return if (mode == AngleMode.DEG && fn in INV_TRIG) v * 180.0 / PI else v
    }

    // sin(π) = 1.2e-16 yerine tam 0; tan(π/2) gibi yerlerde tanımsız (asimptot kesilsin diye NaN değil sonsuz)
    private fun nearMultiple(r: Double, of: Double): Boolean {
        val k = r / of
        return abs(k - round(k)) < 1e-12 && abs(r) < 1e6
    }

    private fun sinExact(r: Double): Double = if (nearMultiple(r, PI)) 0.0 else sin(r)
    private fun cosExact(r: Double): Double = if (nearMultiple(r - PI / 2, PI)) 0.0 else cos(r)
    private fun tanExact(r: Double): Double = when {
        nearMultiple(r, PI) -> 0.0
        nearMultiple(r - PI / 2, PI) -> Double.NaN
        else -> tan(r)
    }

    private class Evaluator(val vars: Map<String, Double>, val mode: AngleMode, val strict: Boolean) {
        fun fail(code: MathError.Code, detail: String = ""): Nothing = throw MathException(MathError(code, -1, detail))

        fun eval(n: MNode): Double {
            val v = raw(n)
            if (strict && v.isInfinite()) fail(MathError.Code.OVERFLOW)
            return v
        }

        private fun raw(n: MNode): Double = when (n) {
            is MNode.Num -> n.value
            is MNode.Const -> if (n.name == "pi") PI else Math.E
            is MNode.Var -> vars[n.name] ?: fail(MathError.Code.UNKNOWN_NAME, n.name)
            is MNode.Neg -> -eval(n.a)
            is MNode.Bin -> {
                val a = eval(n.a)
                val b = eval(n.b)
                when (n.op) {
                    '+' -> a + b
                    '-' -> a - b
                    '*' -> a * b
                    else -> {
                        if (strict && b == 0.0) fail(MathError.Code.DIVISION_BY_ZERO)
                        a / b
                    }
                }
            }
            is MNode.Pow -> {
                val a = eval(n.a)
                val b = eval(n.b)
                if (strict && a == 0.0 && b < 0) fail(MathError.Code.DIVISION_BY_ZERO)
                val v = powReal(a, b)
                if (strict && v.isNaN()) fail(MathError.Code.DOMAIN, "^")
                v
            }
            is MNode.Fact -> {
                val a = eval(n.a)
                val v = factorial(a)
                if (strict && v.isNaN()) fail(MathError.Code.DOMAIN, "!")
                v
            }
            is MNode.Deg -> eval(n.a) * PI / 180.0 * (if (mode == AngleMode.DEG) 180.0 / PI else 1.0)
            is MNode.Call -> {
                if (n.fn in TWO_ARGS) {
                    val xs = n.args.map { eval(it) }
                    if (n.fn == "min") xs.min() else xs.max()
                } else if (n.fn == "logb") {
                    val base = eval(n.args[0])
                    val x = eval(n.args[1])
                    if (strict && (base <= 0 || base == 1.0 || x <= 0)) fail(MathError.Code.DOMAIN, "log")
                    ln(x) / ln(base)
                } else {
                    val a = eval(n.args[0])
                    if (strict) checkDomain(n.fn, a)
                    val v = apply(n.fn, a, mode)
                    if (strict && v.isNaN()) fail(MathError.Code.DOMAIN, n.fn)
                    v
                }
            }
        }

        private fun checkDomain(fn: String, a: Double) {
            val bad = when (fn) {
                "sqrt" -> a < 0
                "ln", "log", "lg", "log2" -> a <= 0
                "asin", "arcsin", "acos", "arccos" -> a < -1 || a > 1
                else -> false
            }
            if (bad) fail(MathError.Code.DOMAIN, fn)
            if (fn in setOf("tan", "tg", "sec", "cot", "cotg", "ctg", "csc")) {
                val r = if (mode == AngleMode.DEG) a * PI / 180.0 else a
                val zero = when (fn) {
                    "tan", "tg", "sec" -> nearMultiple(r - PI / 2, PI)
                    else -> nearMultiple(r, PI)
                }
                if (zero) fail(MathError.Code.DOMAIN, fn)
            }
        }
    }

    private class Compiler(val idx: Map<String, Int>, val mode: AngleMode) {
        fun c(n: MNode): (DoubleArray) -> Double = when (n) {
            is MNode.Num -> { val v = n.value; { _ -> v } }
            is MNode.Const -> { val v = if (n.name == "pi") PI else Math.E; { _ -> v } }
            is MNode.Var -> {
                val k: Int = idx[n.name] ?: throw MathException(MathError(MathError.Code.UNKNOWN_NAME, -1, n.name))
                val f: (DoubleArray) -> Double = { a -> a[k] }
                f
            }
            is MNode.Neg -> { val f = c(n.a); { a -> -f(a) } }
            is MNode.Bin -> {
                val f = c(n.a)
                val g = c(n.b)
                when (n.op) {
                    '+' -> { a -> f(a) + g(a) }
                    '-' -> { a -> f(a) - g(a) }
                    '*' -> { a -> f(a) * g(a) }
                    else -> { a -> f(a) / g(a) }
                }
            }
            is MNode.Pow -> { val f = c(n.a); val g = c(n.b); { a -> powReal(f(a), g(a)) } }
            is MNode.Fact -> { val f = c(n.a); { a -> factorial(f(a)) } }
            is MNode.Deg -> { val f = c(n.a); val k = if (mode == AngleMode.DEG) 1.0 else PI / 180.0; { a -> f(a) * k } }
            is MNode.Call -> {
                val fs = n.args.map { c(it) }
                when (n.fn) {
                    "min" -> { a -> fs.minOf { it(a) } }
                    "max" -> { a -> fs.maxOf { it(a) } }
                    "logb" -> { a -> ln(fs[1](a)) / ln(fs[0](a)) }
                    else -> { val f = fs[0]; val fn = n.fn; val m = mode; { a -> apply(fn, f(a), m) } }
                }
            }
        }
    }

    // ------------------------------------------------------------------ ayrıştırma

    /** Normalleştirilmiş metin ve her karakterin kaynaktaki konumu (hata konumu için). */
    internal class Normalized(val text: String, val map: IntArray) {
        fun srcPos(i: Int): Int = if (i < map.size) map[i] else (map.lastOrNull()?.plus(1) ?: 0)

        companion object {
            private const val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺ˣʸⁿ⁽⁾"
            private const val SUP_PLAIN = "0123456789-+xyn()"

            fun of(src: String): Normalized {
                val sb = StringBuilder()
                val map = ArrayList<Int>()
                fun put(s: String, at: Int) { s.forEach { sb.append(it); map += at } }
                var i = 0
                while (i < src.length) {
                    val ch = src[i]
                    if (SUP.indexOf(ch) >= 0) {
                        put("^(", i)
                        while (i < src.length && SUP.indexOf(src[i]) >= 0) { put(SUP_PLAIN[SUP.indexOf(src[i])].toString(), i); i++ }
                        put(")", i - 1)
                        continue
                    }
                    // v45: alt simge rakamları ("N₀", "v₁₂") → "N_0", "v_12"
                    val sub = "₀₁₂₃₄₅₆₇₈₉".indexOf(ch)
                    if (sub >= 0) {
                        val prevSub = i > 0 && "₀₁₂₃₄₅₆₇₈₉".indexOf(src[i - 1]) >= 0
                        put(if (prevSub) "$sub" else "_$sub", i)
                        i++
                        continue
                    }
                    val rep = when (ch) {
                        '−', '–', '—' -> "-"
                        '×', '·', '⋅', '∙', '∗' -> "*"
                        '÷', ':' -> "/"
                        'π' -> "pi"
                        '√' -> "sqrt"
                        '∛' -> "cbrt"
                        '⌊', '⟦' -> "floor("
                        '⌈' -> "ceil("
                        '⌋', '⌉', '⟧' -> ")"
                        '[', '{' -> "("
                        ']', '}' -> ")"
                        '≤', '⩽' -> "≤"
                        '≥', '⩾' -> "≥"
                        '≠' -> "≠"
                        ' ', '\t', '\n', '\r' -> " "
                        else -> ch.toString()
                    }
                    put(rep, i)
                    i++
                }
                return Normalized(sb.toString(), map.toIntArray())
            }
        }
    }

    private class Parser(val norm: Normalized, extraNames: Set<String>) {
        private val s = norm.text
        private var i = 0
        private var absDepth = 0
        private val names: List<String> = (FUNCTIONS + CONSTANTS + extraNames).sortedByDescending { it.length }
        private val extra = extraNames

        fun err(code: MathError.Code, at: Int = i, detail: String = ""): Nothing =
            throw MathException(MathError(code, norm.srcPos(at), detail))

        private fun skip() { while (i < s.length && s[i] == ' ') i++ }
        private fun peek(): Char? { skip(); return if (i < s.length) s[i] else null }
        private fun eat(c: Char): Boolean { skip(); if (i < s.length && s[i] == c) { i++; return true }; return false }

        fun expectEnd() {
            skip()
            if (i < s.length) {
                if (s[i] == ')') err(MathError.Code.PARENTHESIS)
                err(MathError.Code.UNEXPECTED, i, s[i].toString())
            }
        }

        private fun relOp(): Rel? {
            skip()
            if (i >= s.length) return null
            val two = if (i + 1 < s.length) s.substring(i, i + 2) else ""
            val r = when {
                two == "<=" || two == "=<" -> { i += 2; Rel.LE }
                two == ">=" || two == "=>" -> { i += 2; Rel.GE }
                two == "!=" || two == "<>" || two == "=/" -> { i += 2; Rel.NE }
                two == "==" -> { i += 2; Rel.EQ }
                s[i] == '=' -> { i++; Rel.EQ }
                s[i] == '<' -> { i++; Rel.LT }
                s[i] == '>' -> { i++; Rel.GT }
                s[i] == '≤' -> { i++; Rel.LE }
                s[i] == '≥' -> { i++; Rel.GE }
                s[i] == '≠' -> { i++; Rel.NE }
                else -> null
            }
            return r
        }

        fun relation(): MRelation {
            val sides = arrayListOf(expression())
            val ops = ArrayList<Rel>()
            while (true) {
                val r = relOp() ?: break
                ops += r
                if (peek() == null) err(MathError.Code.MISSING_OPERAND)
                sides += expression()
            }
            return MRelation(sides, ops)
        }

        fun expression(): MNode {
            var left = term()
            while (true) {
                left = when {
                    eat('+') -> MNode.Bin('+', left, operand { term() })
                    eat('-') -> MNode.Bin('-', left, operand { term() })
                    else -> return left
                }
            }
        }

        private inline fun operand(block: () -> MNode): MNode {
            val c = peek()
            if (c == null || c == ')' || c in "+*/^=<>≤≥≠!" || (c == '|' && absDepth > 0)) err(MathError.Code.MISSING_OPERAND)
            return block()
        }

        /** Sonraki karakter örtük çarpım başlatır mı (2x, 3sin x, x(x+1), )( )? */
        private fun implicitNext(): Boolean {
            val c = peek() ?: return false
            if (c == '|') return absDepth == 0
            return c.isLetter() || c == '(' || c.isDigit() || c == '.'
        }

        private fun term(): MNode {
            var left = unary()
            while (true) {
                left = when {
                    eat('*') -> MNode.Bin('*', left, operand { unary() })
                    eat('/') -> MNode.Bin('/', left, operand { unary() })
                    implicitNext() -> MNode.Bin('*', left, power(), implicit = true)
                    else -> return left
                }
            }
        }

        private fun unary(): MNode {
            if (eat('-')) return MNode.Neg(operand { unary() })
            if (eat('+')) return operand { unary() }
            return power()
        }

        private fun power(): MNode {
            val base = postfix()
            if (eat('^')) return MNode.Pow(base, operand { unary() })
            return base
        }

        private fun postfix(): MNode {
            var a = atom()
            while (true) {
                skip()
                if (i < s.length && s[i] == '!' && !(i + 1 < s.length && s[i + 1] == '=')) { i++; a = MNode.Fact(a); continue }
                if (i < s.length && (s[i] == '°' || s[i] == 'º')) { i++; a = MNode.Deg(a); continue }
                return a
            }
        }

        private fun atom(): MNode {
            skip()
            val start = i
            val c = peek() ?: err(MathError.Code.MISSING_OPERAND)
            if (c == '(') {
                i++
                if (peek() == ')') err(MathError.Code.MISSING_OPERAND)
                val saved = absDepth
                absDepth = 0
                val e = expression()
                absDepth = saved
                if (!eat(')')) err(MathError.Code.PARENTHESIS, start)
                return e
            }
            if (c == '|') {
                i++
                absDepth++
                val e = operand { expression() }
                absDepth--
                if (!eat('|')) err(MathError.Code.PARENTHESIS, start)
                return MNode.Call("abs", listOf(e))
            }
            if (c.isDigit() || c == '.') return number()
            if (c.isLetter()) return name()
            if (c == ')') err(MathError.Code.PARENTHESIS)
            if (c in "*/^=<>≤≥≠!") err(MathError.Code.MISSING_OPERAND)
            err(MathError.Code.UNEXPECTED, i, c.toString())
        }

        private fun number(): MNode {
            val start = i
            val sb = StringBuilder()
            while (i < s.length && s[i].isDigit()) sb.append(s[i++])
            // ondalık nokta ya da (iki rakam arasında, boşluksuz) Türkçe virgül
            if (i < s.length && (s[i] == '.' || s[i] == ',') && i + 1 < s.length && s[i + 1].isDigit()) {
                sb.append('.'); i++
                while (i < s.length && s[i].isDigit()) sb.append(s[i++])
            } else if (i < s.length && s[i] == '.' && sb.isNotEmpty()) {
                i++
            }
            if (sb.isEmpty() || sb.toString() == ".") err(MathError.Code.UNEXPECTED, start, ".")
            // bilimsel yazım: 3e8, 1.6e-19 (e sabitiyle karışmasın diye ardından rakam ya da işaret+rakam gerekir)
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                var k = i + 1
                if (k < s.length && (s[k] == '-' || s[k] == '+')) k++
                if (k < s.length && s[k].isDigit() && (k + 1 >= s.length || !s[k + 1].isLetter() || s[k + 1] == 'x')) {
                    val e0 = i
                    i = k
                    while (i < s.length && s[i].isDigit()) i++
                    val text = sb.toString() + "e" + s.substring(e0 + 1, i)
                    return MNode.Num(java.math.BigDecimal(text).toPlainString())
                }
            }
            return MNode.Num(sb.toString())
        }

        private fun matchName(): String? {
            val low = s.substring(i).lowercase()
            for (n in names) {
                if (!low.startsWith(n.lowercase())) continue
                // çok harfli değişkenler yazıldığı gibi; fonksiyonlar küçük/büyük harf duyarsız
                if (n in extra && !s.startsWith(n, i)) continue
                // e sabiti yalnız küçük harfle (E enerji değişkeni olabilir)
                if (n == "e" && s[i] != 'e') continue
                return n
            }
            return null
        }

        private fun name(): MNode {
            val start = i
            val n = matchName()
            if (n == null) {
                // tek harfli değişken (Latin ya da Yunan), isteğe bağlı alt indis: v_0, x_1
                var name = s[i].toString()
                i++
                if (i + 1 < s.length && s[i] == '_' && s[i + 1].isLetterOrDigit()) {
                    val k = i + 1
                    var e = k
                    while (e < s.length && s[e].isLetterOrDigit() && (e == k || s[e].isDigit())) e++
                    name += "_" + s.substring(k, e)
                    i = e
                }
                return MNode.Var(name)
            }
            i += n.length
            if (n in extra) return MNode.Var(n)
            if (n in CONSTANTS) return MNode.Const(n.lowercase())
            return call(n.lowercase(), start)
        }

        private fun call(fn0: String, start: Int): MNode {
            var fn = fn0
            // log_b(x) ya da log_2 x
            var base: MNode? = null
            if ((fn == "log" || fn == "ln") && peekRaw() == '_') {
                i++
                base = atom()
                fn = "logb"
            }
            // sin^2(x) = (sin x)^2
            var outerPow: MNode? = null
            if (peekRaw() == '^') {
                i++
                outerPow = operand { postfix() }
            }
            val args: List<MNode> = if (peek() == '(') {
                i++
                val saved = absDepth
                absDepth = 0
                if (peek() == ')') err(MathError.Code.ARGUMENTS, start, fn)
                val list = arrayListOf(expression())
                while (eat(';') || (peekComma() && eat(','))) list += expression()
                absDepth = saved
                if (!eat(')')) err(MathError.Code.PARENTHESIS, start)
                list
            } else {
                val c = peek()
                if (c == null || c in ")+*/^=<>≤≥≠") err(MathError.Code.MISSING_OPERAND)
                listOf(functionArgument())
            }
            val node = when {
                fn == "logb" -> {
                    if (args.size != 1) err(MathError.Code.ARGUMENTS, start, "log")
                    MNode.Call("logb", listOf(base!!, args[0]))
                }
                fn in TWO_ARGS -> {
                    if (args.size < 2) err(MathError.Code.ARGUMENTS, start, fn)
                    MNode.Call(fn, args)
                }
                else -> {
                    if (args.size != 1) err(MathError.Code.ARGUMENTS, start, fn)
                    MNode.Call(canonical(fn), args)
                }
            }
            return if (outerPow != null) MNode.Pow(node, outerPow) else node
        }

        private fun peekRaw(): Char? = if (i < s.length) s[i] else null

        /** Virgül bağımsız değişken ayırıcı mı (ondalık virgül iki rakam arasında boşluksuz olur). */
        private fun peekComma(): Boolean {
            skip()
            return i < s.length && s[i] == ','
        }

        /** Parantezsiz fonksiyon bağımsız değişkeni: `sin 2x` → sin(2x); bir sonraki fonksiyon adında durur. */
        private fun functionArgument(): MNode {
            var e = if (peek() == '-') { i++; MNode.Neg(power()) } else power()
            while (true) {
                val c = peek() ?: break
                val continues = c.isDigit() || c == '(' ||
                    (c.isLetter() && matchName().let { it == null || it !in FUNCTIONS })
                if (!continues) break
                e = MNode.Bin('*', e, power(), implicit = true)
            }
            return e
        }

        private fun canonical(fn: String) = when (fn) {
            "tg" -> "tan"
            "cotg", "ctg" -> "cot"
            "arcsin" -> "asin"
            "arccos" -> "acos"
            "arctan" -> "atan"
            "sgn" -> "sign"
            "lg" -> "log"
            else -> fn
        }
    }

    // ------------------------------------------------------------------ yazım (okunur metin)

    private val SUP_MAP = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '-' to '⁻', 'n' to 'ⁿ', 'x' to 'ˣ',
    )

    /**
     * Ağacın güzel yazımı: "2x² − 3", "A·sin(kx)", "9,8·t²/2". [decimalComma] Türkçe ondalık virgül.
     */
    fun pretty(n: MNode, decimalComma: Boolean = true): String = Pretty(decimalComma).p(n, 0)

    fun pretty(r: MRelation, decimalComma: Boolean = true): String {
        val sb = StringBuilder(pretty(r.sides[0], decimalComma))
        r.ops.forEachIndexed { k, op -> sb.append(' ').append(op.symbol).append(' ').append(pretty(r.sides[k + 1], decimalComma)) }
        return sb.toString()
    }

    private class Pretty(val comma: Boolean) {
        // öncelik: 1 toplama, 2 çarpma, 3 tekli eksi, 4 üs, 5 atom
        fun p(n: MNode, ctx: Int): String {
            val (s, prec) = when (n) {
                is MNode.Num -> num(n.text) to 5
                is MNode.Const -> (if (n.name == "pi") "π" else "e") to 5
                is MNode.Var -> varName(n.name) to 5
                is MNode.Neg -> ("−" + p(n.a, 3)) to 3
                is MNode.Bin -> when (n.op) {
                    '+' -> (if (n.b is MNode.Neg) p(n.a, 1) + " − " + p(n.b.a, 2) else p(n.a, 1) + " + " + p(n.b, 2)) to 1
                    '-' -> (p(n.a, 1) + " − " + p(n.b, 2)) to 1
                    '*' -> mul(n) to 2
                    else -> (p(n.a, 2) + "/" + p(n.b, 3)) to 2
                }
                is MNode.Pow -> pow(n) to 4
                is MNode.Fact -> (p(n.a, 5) + "!") to 5
                is MNode.Deg -> (p(n.a, 5) + "°") to 5
                is MNode.Call -> call(n) to 5
            }
            return if (prec < ctx) "($s)" else s
        }

        private fun num(t: String): String {
            val clean = if (t.contains('.')) t.trimEnd('0').trimEnd('.') else t
            return if (comma) clean.replace('.', ',') else clean
        }

        private fun varName(n: String): String {
            val k = n.indexOf('_')
            if (k < 0) return n
            val sub = n.substring(k + 1).map { c -> if (c.isDigit()) "₀₁₂₃₄₅₆₇₈₉"[c - '0'] else c }.joinToString("")
            return n.substring(0, k) + sub
        }

        private fun mul(n: MNode.Bin): String {
            val a = p(n.a, 2)
            val b = p(n.b, 3)
            // 2x, 3sin(x), xy: sayıyı harf izliyorsa nokta yazılmaz
            val tight = n.implicit && (b.firstOrNull()?.let { it.isLetter() || it == '(' || it == '√' } == true) &&
                (a.lastOrNull()?.let { it.isLetterOrDigit() || it == ')' || it in "²³¹⁰⁴⁵⁶⁷⁸⁹" } == true) &&
                !(a.last().isDigit() && b.first().isDigit())
            return if (tight) a + b else "$a·$b"
        }

        private fun pow(n: MNode.Pow): String {
            val base = p(n.a, 5)
            val e = n.b
            val sup = when (e) {
                is MNode.Num -> e.text.takeIf { !it.contains('.') }?.let { t -> t.map { SUP_MAP[it] }.takeIf { l -> l.all { it != null } }?.joinToString("") }
                is MNode.Neg -> (e.a as? MNode.Num)?.text?.takeIf { !it.contains('.') }?.let { t -> val m = t.map { SUP_MAP[it] }; if (m.any { it == null }) null else "⁻" + m.joinToString("") }
                is MNode.Var -> if (e.name.length == 1) SUP_MAP[e.name[0]]?.toString() else null
                else -> null
            }
            if (sup != null) return base + sup
            return base + "^" + p(e, 5)
        }

        private fun call(n: MNode.Call): String = when (n.fn) {
            "sqrt" -> "√" + p(n.args[0], 5).let { if (n.args[0] is MNode.Num || n.args[0] is MNode.Var) it else it }
            "abs" -> "|" + p(n.args[0], 0) + "|"
            "logb" -> "log_" + p(n.args[0], 5) + "(" + p(n.args[1], 0) + ")"
            else -> n.fn + "(" + n.args.joinToString("; ") { p(it, 0) } + ")"
        }
    }
}
