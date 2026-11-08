package com.karapp.drawguidance.content.graph

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
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/**
 * Grafik formüllerinin ayrıştırıcısı ve hesaplayıcısı: `y = x^2 - 3`, `sin(x)/x`, `z = x*y`, `x² + y² = 9`.
 *
 * Desteklenenler: + - * / ^ ( ) |…|, örtük çarpım (`2x`, `3sin(x)`, `x(x+1)`, `2πx`, `xy`), parantezsiz fonksiyon
 * (`sin x`, `sin 2x`, `√x`), tek değişkenli fonksiyonlar (sin, cos, tan/tg, cot, sec, csc, asin/arcsin, acos, atan,
 * sinh, cosh, tanh, sqrt, cbrt, abs, exp, ln, log, floor, ceil, round, sign), sabitler (pi/π, e), x ve y
 * değişkenleri, Unicode yazımlar (x², x⁻¹, 2ˣ, √, ∛, π, ·, ×, ÷, −, ⌊x⌋) ve Türkçe ondalık virgül. `-x^2` = −(x²),
 * `2^-x` = 2^(−x); negatif tabanın tek paydalı kesirli kuvveti tanımlıdır (`x^(1/3)`).
 * Geçersiz ifadede [parse] null döner (kullanıcıya uyarı gösterilebilir).
 */
object Expression {

    fun interface Expr {
        /** Tanımsız noktalarda NaN (ya da sonsuz) döner (grafik orada kesilir). */
        fun eval(x: Double, y: Double): Double
    }

    internal val FUNCTIONS = setOf(
        "sin", "cos", "tan", "tg", "cot", "cotg", "sec", "csc", "asin", "acos", "atan", "arcsin", "arccos", "arctan",
        "sinh", "cosh", "tanh", "sqrt", "cbrt", "abs", "exp", "ln", "log", "floor", "ceil", "round", "sign", "sgn",
    )
    internal val CONSTANTS = setOf("pi", "e")
    internal val VARIABLES = setOf("x", "y")
    private val NAMES = (FUNCTIONS + CONSTANTS + VARIABLES).sortedByDescending { it.length }

    /** [s] içinde [i] konumundan başlayan en uzun bilinen ad (fonksiyon, sabit, değişken). */
    internal fun matchName(s: String, i: Int): String? = NAMES.firstOrNull { s.startsWith(it, i) }

    private val PREFIXES = setOf("y", "z", "f", "f(x)", "g(x)", "h(x)", "y(x)", "f(x,y)", "z(x,y)", "g(x,y)")

    /** `y = ...` / `z = ...` / `f(x) = ...` gibi ön ekleri atar. */
    fun body(text: String): String {
        val t = text.trim()
        val i = t.indexOf('=')
        if (i >= 1 && t.take(i).trim().lowercase().replace(" ", "") in PREFIXES) return t.substring(i + 1)
        return t
    }

    /** [body] tarafından atılan ön ek ("f(x)" gibi); yoksa null. */
    internal fun prefix(text: String): String? {
        val t = text.trim()
        val i = t.indexOf('=')
        if (i >= 1 && t.take(i).trim().lowercase().replace(" ", "") in PREFIXES) return t.take(i).trim()
        return null
    }

    /** Kapalı denklem mi (`x^2 + y^2 = 9` gibi, ön ek atıldıktan sonra da '=' içeren)? */
    fun isImplicit(text: String): Boolean = body(text).contains('=')

    fun parse(text: String): Expr? = parse(text, com.karapp.drawguidance.content.math.AngleMode.RAD)

    /** v45: açı birimiyle (derece modunda sin(30) = 1/2). */
    fun parse(text: String, mode: com.karapp.drawguidance.content.math.AngleMode): Expr? {
        val source = body(text)
        if (source.isBlank() || source.contains('=') || source.contains('<') || source.contains('>') ||
            source.contains('≤') || source.contains('≥') || source.contains('≠')) return null
        return parseNormalized(source, mode)
    }

    /** Kapalı denklem `sol = sağ` için F(x, y) = sol − sağ (eğri F = 0). '=' yoksa null. */
    fun parseImplicit(text: String): Expr? = parseImplicit(text, com.karapp.drawguidance.content.math.AngleMode.RAD)

    fun parseImplicit(text: String, mode: com.karapp.drawguidance.content.math.AngleMode): Expr? {
        val b = body(text)
        val k = b.indexOf('=')
        if (k < 0 || b.indexOf('=', k + 1) >= 0) return null
        val l = parseNormalized(b.substring(0, k), mode) ?: return null
        val r = parseNormalized(b.substring(k + 1), mode) ?: return null
        return Expr { x, y -> l.eval(x, y) - r.eval(x, y) }
    }

    /**
     * v45: formülün hatası (null: geçerli). Grafik formülünde yalnız x ve y değişkenleri bulunabilir; [allowY] false
     * ise yalnız x (y = f(x) biçimi).
     */
    fun error(text: String, allowY: Boolean = true): com.karapp.drawguidance.content.math.MathError? {
        val b = body(text)
        val parts = if (isImplicit(text)) b.split('=') else listOf(b)
        if (parts.size > 2) return com.karapp.drawguidance.content.math.MathError(com.karapp.drawguidance.content.math.MathError.Code.RELATION)
        for (part in parts) {
            val (node, err) = com.karapp.drawguidance.content.math.MathExpr.tryParse(part.lowercase())
            if (err != null) return err
            val allowed = if (allowY || parts.size == 2) VARIABLES else setOf("x")
            node!!.variables().firstOrNull { it !in allowed }?.let {
                return com.karapp.drawguidance.content.math.MathError(com.karapp.drawguidance.content.math.MathError.Code.UNKNOWN_NAME, -1, it)
            }
        }
        return null
    }

    private fun parseNormalized(source: String, mode: com.karapp.drawguidance.content.math.AngleMode): Expr? {
        if (source.isBlank()) return null
        return try {
            // v45: ortak ayrıştırıcı (MathExpr); grafikte yalnız x ve y değişkenleri vardır (büyük harf de olur)
            val node = com.karapp.drawguidance.content.math.MathExpr.parse(source.lowercase())
            if (node.variables().any { it !in VARIABLES }) return null
            val f = com.karapp.drawguidance.content.math.MathExpr.compile(node, listOf("x", "y"), mode)
            Expr { x, y -> f(doubleArrayOf(x, y)) }
        } catch (_: Exception) {
            null
        }
    }

    private const val SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺ˣʸⁿ⁽⁾"
    private const val SUPER_PLAIN = "0123456789-+xyn()"

    /** Unicode yazımları ve Türkçe ondalık virgülü ayrıştırıcının ASCII diline çevirir. */
    internal fun normalize(text: String): String {
        val t = text.lowercase()
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            val ch = t[i]
            val sup = SUPERSCRIPTS.indexOf(ch)
            if (sup >= 0) {
                sb.append("^(")
                while (i < t.length && SUPERSCRIPTS.indexOf(t[i]) >= 0) { sb.append(SUPER_PLAIN[SUPERSCRIPTS.indexOf(t[i])]); i++ }
                sb.append(')')
                continue
            }
            when (ch) {
                ',' -> sb.append('.')
                '−', '–', '—' -> sb.append('-')
                '×', '·', '⋅', '∙' -> sb.append('*')
                '÷', ':' -> sb.append('/')
                'π' -> sb.append("pi")
                '√' -> sb.append("sqrt")
                '∛' -> sb.append("cbrt")
                '⌊', '⟦' -> sb.append("floor(")
                '⌈' -> sb.append("ceil(")
                '⌋', '⌉', '⟧' -> sb.append(')')
                '[', '{' -> sb.append('(')
                ']', '}' -> sb.append(')')
                else -> sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }

    /** Negatif tabanda tek paydalı kesirli üsleri de hesaplayan kuvvet (x^(1/3), x^(2/3)). */
    internal fun powReal(b: Double, e: Double): Double {
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
}
