package com.karapp.drawguidance.content.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** v45 ortak ifade ayrıştırıcısı: öncelik, örtük çarpım, fonksiyonlar, derece/radyan, bağıntılar, hatalar. */
class V45MathExprTest {

    private fun v(text: String, vars: Map<String, Double> = emptyMap(), mode: AngleMode = AngleMode.RAD): Double =
        MathExpr.eval(MathExpr.parse(text), vars, mode)

    private fun near(expected: Double, actual: Double, eps: Double = 1e-9) =
        assertTrue("beklenen $expected, bulunan $actual", abs(expected - actual) <= eps * maxOf(1.0, abs(expected)))

    @Test fun precedenceTable() {
        val cases = listOf(
            "1+2*3" to 7.0, "(1+2)*3" to 9.0, "2^3^2" to 512.0, "-2^2" to -4.0, "(-2)^2" to 4.0, "2^-1" to 0.5,
            "2^-x" to 0.25, "10-4-3" to 3.0, "100/10/5" to 2.0, "2*3/4" to 1.5, "-3*-2" to 6.0, "--3" to 3.0,
            "+5" to 5.0, "2·3" to 6.0, "6÷4" to 1.5, "6:4" to 1.5, "3×4" to 12.0, "5 − 7" to -2.0, "2²" to 4.0,
            "2³+1" to 9.0, "x²" to 4.0, "x⁻¹" to 0.5, "-x^2+1" to -3.0, "1-x^2" to -3.0, "2^(1+1)" to 4.0,
            "[1+1]*{2+1}" to 6.0, "3!" to 6.0, "0!" to 1.0, "2*3!" to 12.0, "(1+2)!" to 6.0,
            "1e3" to 1000.0, "1.5e-2" to 0.015, "2,5*2" to 5.0, ".5+.5" to 1.0, "1.25" to 1.25,
        )
        for ((t, e) in cases) near(e, v(t, mapOf("x" to 2.0)))
    }

    @Test fun implicitMultiplication() {
        val vars = mapOf("x" to 3.0, "y" to 2.0, "a" to 5.0)
        val cases = listOf(
            "2x" to 6.0, "2x^2" to 18.0, "xy" to 6.0, "2(x+1)" to 8.0, "(x+1)(x-1)" to 8.0, "x(x+1)" to 12.0,
            "2πx" to 6 * PI, "3sin(0)" to 0.0, "2 x y" to 12.0, "(2)(3)" to 6.0, "2a x" to 30.0, "-2x" to -6.0,
            "x/2y" to 3.0, // x/(2)·y: soldan sağa → (x/2)·y
            "2|x-5|" to 4.0, "|x-5||y-5|" to 6.0, "|-x|" to 3.0,
        )
        for ((t, e) in cases) near(e, v(t, vars))
    }

    @Test fun functionsAndConstants() {
        val x = 0.7
        val vars = mapOf("x" to x)
        val cases = listOf(
            "sin(x)" to sin(x), "sin x" to sin(x), "sin 2x" to sin(2 * x), "sin^2 x + cos^2 x" to 1.0, "sin(x)^2" to sin(x) * sin(x),
            "sqrt(16)" to 4.0, "√16" to 4.0, "√(9+16)" to 5.0, "∛27" to 3.0, "cbrt(-8)" to -2.0, "abs(-3)" to 3.0,
            "ln(e)" to 1.0, "ln e^2" to 2.0, "log(1000)" to 3.0, "lg 100" to 2.0, "log2(8)" to 3.0, "log_2(8)" to 3.0, "log_3 81" to 4.0,
            "exp(1)" to Math.E, "e^x" to exp(x), "pi" to PI, "π/2" to PI / 2, "PI" to PI, "tan(pi/4)" to 1.0, "cot(pi/4)" to 1.0,
            "floor(2.7)" to 2.0, "ceil(2.1)" to 3.0, "round(2.5)" to 3.0, "sign(-4)" to -1.0, "min(3; 5)" to 3.0, "max(3, 5)" to 5.0,
            "arcsin(1)" to PI / 2, "atan(1)" to PI / 4, "tg(0)" to 0.0, "sinh(0)" to 0.0, "cosh(0)" to 1.0, "ln(x)" to ln(x),
            "log(x)" to log10(x), "sqrt x + 1" to sqrt(x) + 1, "2sqrt(x)" to 2 * sqrt(x), "x^(1/3)" to Math.cbrt(x),
            "(-8)^(1/3)" to -2.0, "(-8)^(2/3)" to 4.0,
        )
        for ((t, e) in cases) near(e, v(t, vars))
    }

    @Test fun degreeAndRadian() {
        near(0.5, v("sin(30°)"))
        near(0.5, v("sin 30°"))
        near(0.5, v("cos(60)", mode = AngleMode.DEG))
        near(0.5, v("sin(30)", mode = AngleMode.DEG))
        near(1.0, v("tan(45)", mode = AngleMode.DEG))
        near(90.0, v("asin(1)", mode = AngleMode.DEG))
        near(45.0, v("atan(1)", mode = AngleMode.DEG))
        // derece modunda ° işareti ikinci kez çevirmez
        near(0.5, v("sin(30°)", mode = AngleMode.DEG))
        near(PI / 6, v("30°"))
        // radyanda sin(π) tam sıfır, tan(π/2) tanımsız (NaN, asimptot)
        assertEquals(0.0, v("sin(pi)"), 0.0)
        assertTrue(v("tan(pi/2)").isNaN())
        assertTrue(v("tan(90)", mode = AngleMode.DEG).isNaN())
    }

    @Test fun relationsAndChains() {
        val r = MathExpr.parseRelation("-1 ≤ x < 4")
        assertEquals(listOf(Rel.LE, Rel.LT), r.ops)
        assertEquals(3, r.sides.size)
        assertEquals(listOf(Rel.GE), MathExpr.parseRelation("x >= 2").ops)
        assertEquals(listOf(Rel.LE), MathExpr.parseRelation("x <= 2").ops)
        assertEquals(listOf(Rel.NE), MathExpr.parseRelation("x != 2").ops)
        assertEquals(listOf(Rel.NE), MathExpr.parseRelation("x ≠ 2").ops)
        assertEquals(listOf(Rel.EQ), MathExpr.parseRelation("x^2 + y^2 = 9").ops)
        assertFalse(MathExpr.parseRelation("x^2 + 1").isRelation)
        assertTrue(Rel.LE.holds(2.0, 2.0)); assertFalse(Rel.LT.holds(2.0, 2.0)); assertTrue(Rel.NE.holds(1.0, 2.0))
        assertTrue(Rel.EQ.holds(0.1 + 0.2, 0.3))
        assertEquals(Rel.GT, Rel.LT.flipped())
    }

    @Test fun variablesAndNames() {
        assertEquals(setOf("x", "y"), MathExpr.parse("x^2 + 2xy").variables())
        assertEquals(setOf("v_0", "t", "g"), MathExpr.parse("v_0 t - g t^2/2").variables())
        // çok harfli adlar (mercek: d_o, d_i yerine do, di)
        val n = MathExpr.parse("1/do + 1/di", setOf("do", "di"))
        assertEquals(setOf("do", "di"), n.variables())
        near(0.75, MathExpr.eval(n, mapOf("do" to 2.0, "di" to 4.0)))
        // Yunan harfleri
        near(1.0, v("tan θ", mapOf("θ" to PI / 4)))
        // büyük/küçük harf ayrı değişken
        assertEquals(setOf("F", "m", "a"), MathExpr.parseRelation("F = m a").sides.flatMap { it.variables() }.toSet())
    }

    private fun code(text: String): MathError.Code? = MathExpr.tryParseRelation(text).second?.code

    @Test fun parseErrorsAreReportedWithCodeAndPosition() {
        assertEquals(MathError.Code.EMPTY, code(""))
        assertEquals(MathError.Code.EMPTY, code("   "))
        assertEquals(MathError.Code.MISSING_OPERAND, code("2+"))
        assertEquals(MathError.Code.MISSING_OPERAND, code("*3"))
        assertEquals(MathError.Code.MISSING_OPERAND, code("2*/3"))
        assertEquals(MathError.Code.MISSING_OPERAND, code("x ="))
        assertEquals(MathError.Code.PARENTHESIS, code("(1+2"))
        assertEquals(MathError.Code.PARENTHESIS, code("1+2)"))
        assertEquals(MathError.Code.MISSING_OPERAND, code("()"))
        assertEquals(MathError.Code.PARENTHESIS, code("|x"))
        assertEquals(MathError.Code.UNEXPECTED, code("2 # 3"))
        assertEquals(MathError.Code.UNEXPECTED, code("2 \$ 3"))
        assertEquals(MathError.Code.ARGUMENTS, code("sin()"))
        assertEquals(MathError.Code.ARGUMENTS, code("min(3)"))
        assertEquals(MathError.Code.MISSING_OPERAND, code("sin"))
        val e = MathExpr.tryParseRelation("1 + 2 ) * 3").second!!
        assertEquals(6, e.pos)
        val u = MathExpr.tryParseRelation("2 + @").second!!
        assertEquals(4, u.pos)
        assertEquals("@", u.detail)
        // tek ifade beklenirken bağıntı
        assertEquals(MathError.Code.RELATION, MathExpr.tryParse("x = 2").second?.code)
    }

    @Test fun strictEvaluationReportsDomainAndDivision() {
        fun strictCode(t: String, vars: Map<String, Double> = emptyMap()): MathError.Code? = try {
            MathExpr.evalStrict(MathExpr.parse(t), vars); null
        } catch (e: MathException) { e.error.code }
        assertEquals(MathError.Code.DIVISION_BY_ZERO, strictCode("1/0"))
        assertEquals(MathError.Code.DIVISION_BY_ZERO, strictCode("1/(x-2)", mapOf("x" to 2.0)))
        assertEquals(MathError.Code.DIVISION_BY_ZERO, strictCode("0^-1"))
        assertEquals(MathError.Code.DOMAIN, strictCode("sqrt(-4)"))
        assertEquals(MathError.Code.DOMAIN, strictCode("ln(0)"))
        assertEquals(MathError.Code.DOMAIN, strictCode("log(-1)"))
        assertEquals(MathError.Code.DOMAIN, strictCode("asin(2)"))
        assertEquals(MathError.Code.DOMAIN, strictCode("tan(pi/2)"))
        assertEquals(MathError.Code.DOMAIN, strictCode("(-8)^(1/2)"))
        assertEquals(MathError.Code.DOMAIN, strictCode("(-1)!"))
        assertEquals(MathError.Code.UNKNOWN_NAME, strictCode("q + 1"))
        assertEquals(MathError.Code.OVERFLOW, strictCode("10^400"))
        assertNull(strictCode("sqrt(4)"))
        // gevşek hesapta NaN / sonsuz (grafik kesilir)
        assertTrue(v("1/0").isInfinite())
        assertTrue(v("sqrt(-1)").isNaN())
        assertTrue(v("ln(0)").isNaN())
    }

    @Test fun compiledMatchesInterpreted() {
        val exprs = listOf("x^2 - 3x + 2", "sin(x)/x", "e^(-x^2/2)", "|x| + floor(y)", "max(x, y) - min(x; y)", "x^(1/3)", "log_2(x+3)", "3!x")
        for (t in exprs) {
            val n = MathExpr.parse(t)
            val f = MathExpr.compile(n, listOf("x", "y"))
            for (x in listOf(-2.5, -1.0, 0.3, 1.7, 4.0)) {
                val a = MathExpr.eval(n, mapOf("x" to x, "y" to 0.5))
                val b = f(doubleArrayOf(x, 0.5))
                if (a.isNaN()) assertTrue(t, b.isNaN()) else near(a, b)
            }
        }
        try {
            MathExpr.compile(MathExpr.parse("x + q"), listOf("x"))
            fail("bilinmeyen değişken derlenmemeli")
        } catch (e: MathException) {
            assertEquals(MathError.Code.UNKNOWN_NAME, e.error.code)
            assertEquals("q", e.error.detail)
        }
    }

    @Test fun prettyPrinting() {
        assertEquals("2x² − 3", MathExpr.pretty(MathExpr.parse("2x^2-3")))
        assertEquals("A·sin(kx)", MathExpr.pretty(MathExpr.parse("A*sin(k x)")))
        assertEquals("x − 3", MathExpr.pretty(MathExpr.parse("x + -3")))
        assertEquals("9,8·t²/2", MathExpr.pretty(MathExpr.parse("9.8*t^2/2")))
        assertEquals("v₀·t", MathExpr.pretty(MathExpr.parse("v_0*t")))
        assertEquals("−1 ≤ x < 4", MathExpr.pretty(MathExpr.parseRelation("-1 <= x < 4")))
        assertEquals("(x + 1)²", MathExpr.pretty(MathExpr.parse("(x+1)^2")))
        assertEquals("√x", MathExpr.pretty(MathExpr.parse("sqrt(x)")))
        assertEquals("2·3", MathExpr.pretty(MathExpr.parse("2*3")))
        assertEquals("x⁻¹", MathExpr.pretty(MathExpr.parse("x^-1")))
        assertEquals("1.5", MathExpr.pretty(MathExpr.parse("1.50"), decimalComma = false))
    }

    @Test fun normalizedPositionsMapBackToSource() {
        // Unicode genişlemesi (π → pi) konumları kaydırmaz: hata konumu kaynaktaki karakteri gösterir
        val e = MathExpr.tryParseRelation("π + ) ").second
        assertNotNull(e)
        assertEquals(4, e!!.pos)
    }
}
