package com.karapp.drawguidance.content.math

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/** v45 kesin kesirli sayı (sadeleşmiş; payda pozitif). */
class Rational private constructor(val num: BigInteger, val den: BigInteger) : Comparable<Rational> {

    companion object {
        val ZERO = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rational(BigInteger.ONE, BigInteger.ONE)

        fun of(n: BigInteger, d: BigInteger = BigInteger.ONE): Rational {
            if (d.signum() == 0) throw MathException(MathError(MathError.Code.DIVISION_BY_ZERO))
            var a = n
            var b = d
            if (b.signum() < 0) { a = a.negate(); b = b.negate() }
            val g = a.gcd(b)
            if (g > BigInteger.ONE) { a /= g; b /= g }
            return Rational(a, b)
        }

        fun of(n: Long, d: Long = 1L): Rational = of(BigInteger.valueOf(n), BigInteger.valueOf(d))

        /** Ondalık yazımdan kesin değer ("2.5" → 5/2). */
        fun parse(text: String): Rational {
            val bd = BigDecimal(text)
            return if (bd.scale() <= 0) of(bd.toBigIntegerExact()) else of(bd.unscaledValue(), BigInteger.TEN.pow(bd.scale()))
        }
    }

    val isInteger: Boolean get() = den == BigInteger.ONE
    val signum: Int get() = num.signum()

    operator fun plus(o: Rational) = of(num * o.den + o.num * den, den * o.den)
    operator fun minus(o: Rational) = of(num * o.den - o.num * den, den * o.den)
    operator fun times(o: Rational) = of(num * o.num, den * o.den)
    operator fun div(o: Rational): Rational {
        if (o.num.signum() == 0) throw MathException(MathError(MathError.Code.DIVISION_BY_ZERO))
        return of(num * o.den, den * o.num)
    }
    operator fun unaryMinus() = of(num.negate(), den)

    /** Tamsayı üs (büyük sonuçlarda null: yaklaşık hesaba geçilir). */
    fun pow(e: Int): Rational? {
        if (e == 0) return ONE
        if (abs(e) > 4096) return null
        val bits = maxOf(num.abs().bitLength(), den.bitLength()).toLong() * abs(e)
        if (bits > 20000) return null
        if (e < 0) {
            if (num.signum() == 0) throw MathException(MathError(MathError.Code.DIVISION_BY_ZERO))
            return of(den.pow(-e), num.pow(-e))
        }
        return of(num.pow(e), den.pow(e))
    }

    /** Tam kök (karekök vb.): pay ve payda tam kuvvetse kesin, değilse null. */
    fun root(n: Int): Rational? {
        if (num.signum() < 0 && n % 2 == 0) return null
        val neg = num.signum() < 0
        val a = intRoot(num.abs(), n) ?: return null
        val b = intRoot(den, n) ?: return null
        return of(if (neg) a.negate() else a, b)
    }

    private fun intRoot(x: BigInteger, n: Int): BigInteger? {
        if (x.signum() == 0) return BigInteger.ZERO
        if (x.bitLength() > 4000) return null
        val guess = BigDecimal(x).toDouble().let { Math.pow(it, 1.0 / n) }
        if (!guess.isFinite()) return null
        val g = BigDecimal(guess).setScale(0, RoundingMode.HALF_UP).toBigInteger()
        for (d in -2L..2L) {
            val c = g + BigInteger.valueOf(d)
            if (c.signum() >= 0 && c.pow(n) == x) return c
        }
        return null
    }

    fun toDouble(): Double = BigDecimal(num).divide(BigDecimal(den), MathContext.DECIMAL64).toDouble()

    /** Sonlu ondalık açılımı var mı (payda yalnız 2 ve 5 çarpanlı) ve en çok [maxDigits] basamak mı. */
    fun terminatingDecimal(maxDigits: Int = 6): String? {
        var d = den
        var twos = 0; var fives = 0
        val two = BigInteger.valueOf(2); val five = BigInteger.valueOf(5)
        while (d.mod(two).signum() == 0) { d /= two; twos++ }
        while (d.mod(five).signum() == 0) { d /= five; fives++ }
        if (d != BigInteger.ONE) return null
        val digits = maxOf(twos, fives)
        if (digits > maxDigits) return null
        return BigDecimal(num).divide(BigDecimal(den)).setScale(digits, RoundingMode.UNNECESSARY).stripTrailingZeros().toPlainString()
    }

    override fun compareTo(other: Rational): Int = (num * other.den).compareTo(other.num * den)
    override fun equals(other: Any?) = other is Rational && num == other.num && den == other.den
    override fun hashCode() = num.hashCode() * 31 + den.hashCode()
    override fun toString() = if (isInteger) num.toString() else "$num/$den"
}

/** Hesap sonucu: kesin kesir ya da yaklaşık ondalık. */
sealed class Value {
    data class Exact(val q: Rational) : Value()
    /** [degrees]: RAD modunda derece işaretiyle yazılmış kesin açı (sin 30° tablodan kesin hesaplansın diye). */
    data class Approx(val d: Double, val degrees: Rational? = null) : Value()

    fun toDouble(): Double = when (this) { is Exact -> q.toDouble(); is Approx -> d }
}

/**
 * v45 kesin hesap: toplama, çıkarma, çarpma, bölme ve tamsayı üsler kesirle; tam kökler (√(9/4) = 3/2), mutlak değer,
 * tam kısım ve derece cinsinden özel açıların trigonometrik değerleri (sin 30° = 1/2) kesin; geri kalan yaklaşık.
 * Sıfıra bölme ve tanım dışı [MathException] fırlatır.
 */
object ExactEval {

    fun eval(n: MNode, vars: Map<String, Value> = emptyMap(), mode: AngleMode = AngleMode.RAD): Value {
        val v = ev(n, vars, mode)
        if (v is Value.Approx && !v.d.isFinite()) {
            if (v.d.isNaN()) throw MathException(MathError(MathError.Code.DOMAIN))
            throw MathException(MathError(MathError.Code.OVERFLOW))
        }
        return v
    }

    private fun approx(n: MNode, vars: Map<String, Value>, mode: AngleMode): Double =
        MathExpr.evalStrict(n, vars.mapValues { it.value.toDouble() }, mode)

    private fun ev(n: MNode, vars: Map<String, Value>, mode: AngleMode): Value = when (n) {
        is MNode.Num -> Value.Exact(Rational.parse(n.text))
        is MNode.Const -> Value.Approx(if (n.name == "pi") PI else Math.E)
        is MNode.Var -> vars[n.name] ?: throw MathException(MathError(MathError.Code.UNKNOWN_NAME, -1, n.name))
        is MNode.Neg -> when (val a = ev(n.a, vars, mode)) {
            is Value.Exact -> Value.Exact(-a.q)
            is Value.Approx -> Value.Approx(-a.d)
        }
        is MNode.Bin -> {
            val a = ev(n.a, vars, mode)
            val b = ev(n.b, vars, mode)
            if (n.op == '/' && b.toDouble() == 0.0 && (b !is Value.Approx || b.d == 0.0)) {
                throw MathException(MathError(MathError.Code.DIVISION_BY_ZERO))
            }
            if (a is Value.Exact && b is Value.Exact) {
                Value.Exact(
                    when (n.op) {
                        '+' -> a.q + b.q
                        '-' -> a.q - b.q
                        '*' -> a.q * b.q
                        else -> a.q / b.q
                    },
                )
            } else {
                val x = a.toDouble(); val y = b.toDouble()
                Value.Approx(when (n.op) { '+' -> x + y; '-' -> x - y; '*' -> x * y; else -> x / y })
            }
        }
        is MNode.Pow -> pow(ev(n.a, vars, mode), ev(n.b, vars, mode))
        is MNode.Fact -> {
            val a = ev(n.a, vars, mode)
            if (a is Value.Exact && a.q.isInteger && a.q.signum >= 0 && a.q.num.bitLength() < 12) {
                var r = BigInteger.ONE
                var k = BigInteger.TWO
                while (k <= a.q.num) { r *= k; k += BigInteger.ONE }
                Value.Exact(Rational.of(r))
            } else {
                val v = MathExpr.factorial(a.toDouble())
                if (v.isNaN()) throw MathException(MathError(MathError.Code.DOMAIN, -1, "!"))
                Value.Approx(v)
            }
        }
        is MNode.Deg -> {
            // derece değeri: açı biriminde tutulur (sin bunu derece olarak bilir)
            val a = ev(n.a, vars, mode)
            if (mode == AngleMode.DEG) a else when (a) {
                is Value.Exact -> Value.Approx(a.q.toDouble() * PI / 180.0, a.q)
                is Value.Approx -> Value.Approx(a.d * PI / 180.0)
            }
        }
        is MNode.Call -> call(n, vars, mode)
    }

    private fun pow(a: Value, b: Value): Value {
        if (a is Value.Exact && b is Value.Exact) {
            if (a.q.signum == 0 && b.q.signum < 0) throw MathException(MathError(MathError.Code.DIVISION_BY_ZERO))
            if (b.q.isInteger && b.q.num.bitLength() < 31) {
                a.q.pow(b.q.num.toInt())?.let { return Value.Exact(it) }
            } else if (!b.q.isInteger && b.q.den.bitLength() < 8 && b.q.num.bitLength() < 16) {
                // a^(p/q) = (q√a)^p kesinse
                val r = a.q.root(b.q.den.toInt())
                if (r != null) r.pow(b.q.num.toInt())?.let { return Value.Exact(it) }
                if (a.q.signum < 0 && b.q.den.toInt() % 2 == 0) throw MathException(MathError(MathError.Code.DOMAIN, -1, "^"))
            }
        }
        val v = MathExpr.powReal(a.toDouble(), b.toDouble())
        if (v.isNaN()) throw MathException(MathError(MathError.Code.DOMAIN, -1, "^"))
        return Value.Approx(v)
    }

    private val HALF = Rational.of(1, 2)

    private fun call(n: MNode.Call, vars: Map<String, Value>, mode: AngleMode): Value {
        if (n.fn == "min" || n.fn == "max" || n.fn == "logb") return Value.Approx(approx(n, vars, mode))
        val a = ev(n.args[0], vars, mode)
        // derece işaretli değerin derece cinsi (sin(30°) → 30)
        val degrees: Rational? = when {
            mode == AngleMode.DEG && a is Value.Exact -> a.q
            a is Value.Approx -> a.degrees
            else -> null
        }
        when (n.fn) {
            "sqrt" -> {
                if (a.toDouble() < 0) throw MathException(MathError(MathError.Code.DOMAIN, -1, "sqrt"))
                if (a is Value.Exact) a.q.root(2)?.let { return Value.Exact(it) }
            }
            "cbrt" -> if (a is Value.Exact) a.q.root(3)?.let { return Value.Exact(it) }
            "abs" -> if (a is Value.Exact) return Value.Exact(if (a.q.signum < 0) -a.q else a.q)
            "sign" -> return Value.Exact(Rational.of(a.toDouble().let { if (it > 0) 1L else if (it < 0) -1L else 0L }))
            "floor", "ceil", "round" -> {
                val d = a.toDouble()
                val v = when (n.fn) { "floor" -> floor(d); "ceil" -> kotlin.math.ceil(d); else -> floor(d + 0.5) }
                return Value.Exact(Rational.of(v.toLong()))
            }
            "sin", "cos", "tan", "cot", "sec", "csc" -> if (degrees != null) special(n.fn, degrees)?.let { return it }
        }
        return Value.Approx(MathExpr.evalStrict(MNode.Call(n.fn, listOf(MNode.Var("_a"))), mapOf("_a" to a.toDouble()), mode))
    }

    /** Özel açılarda (30°'nin ve 45°'nin katları) rasyonel çıkan değerler: 0, ±1/2, ±1. Tanımsızda hata. */
    private fun special(fn: String, deg: Rational): Value? {
        if (!deg.isInteger) return null
        val d = deg.num.mod(BigInteger.valueOf(360)).toInt()
        val sinQ: Rational? = when (d) {
            0, 180 -> Rational.ZERO; 30, 150 -> HALF; 210, 330 -> -HALF; 90 -> Rational.ONE; 270 -> -Rational.ONE
            else -> null
        }
        val cosQ: Rational? = when (d) {
            90, 270 -> Rational.ZERO; 60, 300 -> HALF; 120, 240 -> -HALF; 0 -> Rational.ONE; 180 -> -Rational.ONE
            else -> null
        }
        fun inv(q: Rational?, f: String): Value? {
            q ?: return null
            if (q.signum == 0) throw MathException(MathError(MathError.Code.DOMAIN, -1, f))
            return Value.Exact(Rational.ONE / q)
        }
        return when (fn) {
            "sin" -> sinQ?.let { Value.Exact(it) }
            "cos" -> cosQ?.let { Value.Exact(it) }
            "tan" -> when (d) {
                0, 180 -> Value.Exact(Rational.ZERO); 45, 225 -> Value.Exact(Rational.ONE); 135, 315 -> Value.Exact(-Rational.ONE)
                90, 270 -> throw MathException(MathError(MathError.Code.DOMAIN, -1, "tan"))
                else -> null
            }
            "cot" -> when (d) {
                90, 270 -> Value.Exact(Rational.ZERO); 45, 225 -> Value.Exact(Rational.ONE); 135, 315 -> Value.Exact(-Rational.ONE)
                0, 180 -> throw MathException(MathError(MathError.Code.DOMAIN, -1, "cot"))
                else -> null
            }
            "sec" -> inv(cosQ, "sec")
            "csc" -> inv(sinQ, "csc")
            else -> null
        }
    }

    /** Değerin karşılaştırması (kesinse kesin). */
    fun compare(a: Value, b: Value): Int {
        if (a is Value.Exact && b is Value.Exact) return a.q.compareTo(b.q)
        val x = a.toDouble(); val y = b.toDouble()
        if (abs(x - y) <= 1e-12 * maxOf(1.0, abs(x), abs(y))) return 0
        return x.compareTo(y)
    }

    /** Yaklaşık değer tam sayıya / kısa ondalığa çok yakınsa ("0,49999999999" → 1/2) kesine çevirir. */
    fun tidy(v: Value): Value {
        if (v !is Value.Approx) return v
        val d = v.d
        for (den in longArrayOf(1, 2, 4, 5, 8, 10, 100, 1000)) {
            val k = round(d * den)
            if (abs(k - d * den) < 1e-9 * maxOf(1.0, abs(d * den)) && abs(k) < 1e15) return Value.Exact(Rational.of(k.toLong(), den))
        }
        return v
    }
}
