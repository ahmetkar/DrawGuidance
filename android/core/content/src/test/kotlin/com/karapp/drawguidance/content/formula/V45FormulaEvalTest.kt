package com.karapp.drawguidance.content.formula

import com.karapp.drawguidance.content.math.ExactEval
import com.karapp.drawguidance.content.math.MathError
import com.karapp.drawguidance.content.math.MathException
import com.karapp.drawguidance.content.math.MathExpr
import com.karapp.drawguidance.content.math.Rational
import com.karapp.drawguidance.content.math.Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v45 (7): formülde bilinmeyenlerin yerine sayı yazılınca sonuç hesaplanır ve doğru bağıntıyla eklenir; kesin kesir
 * hesabı, ondalık/yaklaşık gösterim, sıfıra bölme ve tanım dışı iletileri, işaretlerin geri alınabilirliği.
 */
class V45FormulaEvalTest {

    // ------------------------------------------------------------------ kesin hesap

    private fun exact(text: String): Value = ExactEval.eval(MathExpr.parse(text))

    private fun q(text: String): String = (exact(text) as Value.Exact).q.toString()

    @Test fun rationalArithmeticTable() {
        val cases = listOf(
            "1/2 + 1/3" to "5/6", "2/4" to "1/2", "1 - 1/3" to "2/3", "(2/3)*(9/4)" to "3/2", "(1/2)/(1/4)" to "2",
            "2^10" to "1024", "2^-2" to "1/4", "(-3)^2" to "9", "-3^2" to "-9", "(2/3)^3" to "8/27", "0.1 + 0.2" to "3/10",
            "1.5 * 4" to "6", "sqrt(9/4)" to "3/2", "sqrt(16)" to "4", "∛(-27)" to "-3", "8^(1/3)" to "2", "4^(3/2)" to "8",
            "|-7/2|" to "7/2", "5!" to "120", "floor(7/2)" to "3", "ceil(-7/2)" to "-3", "2*3+4*5" to "26", "1/3+1/3+1/3" to "1",
            "sin(30°)" to "1/2", "cos(60°)" to "1/2", "tan(45°)" to "1", "sin(90°)" to "1", "cos(180°)" to "-1", "sin(210°)" to "-1/2",
            "12345678901234567890 + 1" to "12345678901234567891",
        )
        for ((t, e) in cases) assertEquals(t, e, q(t))
    }

    @Test fun approximateWhenIrrational() {
        val v = exact("sqrt(2)")
        assertTrue(v is Value.Approx)
        assertEquals(1.41421356, v.toDouble(), 1e-8)
        assertTrue(exact("pi*2^2") is Value.Approx)
        assertEquals(12.566370614, exact("pi*2^2").toDouble(), 1e-8)
        // yaklaşık ama aslında kesin olanlar düzeltilir
        assertEquals(Value.Exact(Rational.of(1, 2)), ExactEval.tidy(Value.Approx(0.49999999999999994)))
        assertEquals(Value.Exact(Rational.of(3)), ExactEval.tidy(Value.Approx(2.9999999999999996)))
        assertTrue(ExactEval.tidy(Value.Approx(Math.PI)) is Value.Approx)
    }

    @Test fun exactErrors() {
        fun err(t: String): MathError.Code = try { exact(t); error("hata bekleniyordu: $t") } catch (e: MathException) { e.error.code }
        assertEquals(MathError.Code.DIVISION_BY_ZERO, err("1/0"))
        assertEquals(MathError.Code.DIVISION_BY_ZERO, err("5/(3-3)"))
        assertEquals(MathError.Code.DIVISION_BY_ZERO, err("0^(-2)"))
        assertEquals(MathError.Code.DOMAIN, err("sqrt(-4)"))
        assertEquals(MathError.Code.DOMAIN, err("ln(0)"))
        assertEquals(MathError.Code.DOMAIN, err("(-4)^(1/2)"))
        assertEquals(MathError.Code.DOMAIN, err("tan(90°)"))
        assertEquals(MathError.Code.DOMAIN, err("csc(180°)"))
        assertEquals(MathError.Code.OVERFLOW, err("e^1000"))
        // kesin ama çok büyük sonuç bilimsel yazılır
        assertEquals("\\approx 1\\cdot 10^{999}", FormulaEval.resultTex(exact("10^999"), false, true))
        assertEquals("\\approx 1{,}41421\\cdot 10^{-8}", FormulaEval.resultTex(exact("sqrt(2)/10^8"), false, true))
        assertEquals("\\approx 3{,}14159\\cdot 10^{20}", FormulaEval.resultTex(exact("pi*10^20"), false, true))
    }

    @Test fun rationalBasics() {
        assertEquals(Rational.of(5, 2), Rational.parse("2.5"))
        assertEquals("1/4", Rational.parse("0.25").toString())
        assertEquals("-1/2", Rational.of(1, -2).toString())
        assertEquals("2.5", Rational.of(5, 2).terminatingDecimal())
        assertNull(Rational.of(1, 3).terminatingDecimal())
        assertEquals(Rational.of(3, 2), Rational.of(9, 4).root(2))
        assertNull(Rational.of(2).root(2))
        assertTrue(Rational.of(1, 3) < Rational.of(1, 2))
    }

    // ------------------------------------------------------------------ formüle sonuç ekleme

    /** Şablonu elemanlarına ayırıp [values] yuvalarına yazar (uygulamadaki gibi) ve hesaplar. */
    private fun run(template: String, values: Map<String, String>, decimalComma: Boolean = true): FormulaEval.Outcome {
        var src = Formula.atomize(template)
        for (s in Formula.slots(src)) values[s.value]?.let { src = Formula.setSlot(src, s.name, it) }
        return FormulaEval.annotate(src, decimalComma = decimalComma)
    }

    /** Okunur metin (yerleşik PlainText) — kullanıcının gördüğü dizgi. */
    private fun shown(o: FormulaEval.Outcome) = Formula.plainText(o.source)

    @Test fun ownerExampleLinearExpression() {
        val o = run("2x+3", mapOf("x" to "4"))
        assertEquals("=11", o.result)
        assertNull(o.error)
        assertTrue(o.source.endsWith(FormulaEval.RESULT_MARK + "=11"))
        val text = shown(o).replace(" ", "")
        assertTrue(text, text.startsWith("2·4+3"))
        assertTrue(text, text.endsWith("=11"))
    }

    @Test fun ownerExampleFalseInequalityShowsTrueRelation() {
        val o = run("x^2 \\ge 10", mapOf("x" to "3"))
        assertEquals("<", o.result)
        val text = shown(o).replace(" ", "")
        assertTrue(text, text.contains("3²<10") || text.contains("3^2<10"))
        assertFalse(text.contains("≥"))
        // x = 4 olunca kullanıcının ≥ bağıntısı geri gelir (doğru)
        val back = FormulaEval.annotate(Formula.setSlot(o.source, Formula.slots(o.source).first().name, "4"))
        assertEquals("≥", back.result)
        assertTrue(Formula.plainText(back.source).contains("≥"))
        // değiştirilen bağıntı tekrar tekrar hesaplanınca kaynak büyümez
        var s = o.source
        repeat(5) { s = FormulaEval.annotate(s).source }
        assertEquals(o.source, s)
    }

    @Test fun relationSelectionTable() {
        data class C(val tpl: String, val x: String, val rel: String)
        val cases = listOf(
            C("x+1 = 5", "4", "="), C("x+1 = 5", "3", "<"), C("x+1 = 5", "6", ">"),
            C("2x < 7", "3", "<"), C("2x < 7", "4", ">"), C("2x < 8", "4", "="),
            C("x \\le 2", "2", "≤"), C("x \\le 2", "3", ">"), C("x \\ge 2", "1", "<"), C("x \\ge 2", "2", "≥"),
            C("x \\neq 3", "3", "="), C("x \\neq 3", "5", "≠"), C("x > 0", "0", "="), C("x > 0", "-1", "<"),
            C("\\frac{x}{2} = 1.5", "3", "="), C("x^2 = 2", "1.4142", "<"),
        )
        for (c in cases) assertEquals("${c.tpl} x=${c.x}", c.rel, run(c.tpl, mapOf("x" to c.x)).result)
    }

    @Test fun loneUnknownSideGetsValue() {
        val o = run("A = \\pi r^2", mapOf("r" to "2"))
        assertNotNull(o.result)
        assertTrue(o.result!!, o.result!!.startsWith("\\approx 12{,}5664"))
        val f = run("F = m a", mapOf("m" to "2", "a" to "3"))
        assertEquals("=6", f.result)
        assertTrue(shown(f).replace(" ", ""), shown(f).replace(" ", "").endsWith("=6"))
        // iki bilinmeyen kalırsa sonuç yok
        assertNull(run("F = m a", mapOf("m" to "2")).result)
    }

    @Test fun fractionsAndDecimalsDisplay() {
        assertEquals("=\\frac{5}{6}", run("\\frac{1}{2} + \\frac{x}{3}", mapOf("x" to "1")).result)
        assertEquals("=-\\frac{1}{6}", run("\\frac{1}{3} - \\frac{x}{2}", mapOf("x" to "1")).result)
        assertEquals("=7{,}5", run("2.5 x", mapOf("x" to "3")).result)
        assertEquals("=7.5", run("2.5 x", mapOf("x" to "3"), decimalComma = false).result)
        assertEquals("=3", run("\\sqrt{x}", mapOf("x" to "9")).result)
        assertEquals("\\approx 1{,}41421", run("\\sqrt{x}", mapOf("x" to "2")).result)
        assertEquals("=8", run("x^3", mapOf("x" to "2")).result)
        assertEquals("=1024", run("2^{x}", mapOf("x" to "10")).result)
    }

    @Test fun negativeValuesAreParenthesizedForPowers() {
        val o = run("x^2", mapOf("x" to "-3"))
        assertEquals("=9", o.result)
        val text = shown(o).replace(" ", "")
        assertTrue(text, text.startsWith("(−3)²") || text.startsWith("(-3)²") || text.startsWith("(−3)^2"))
        val m = run("2x", mapOf("x" to "-5"))
        assertEquals("=-10", m.result)
    }

    @Test fun divisionByZeroAndDomainErrors() {
        val d = run("\\frac{1}{x}", mapOf("x" to "0"))
        assertEquals(MathError.Code.DIVISION_BY_ZERO, d.error?.code)
        assertTrue(d.source.endsWith("=\\text{tanımsız}"))
        val s = run("\\sqrt{x}", mapOf("x" to "-4"))
        assertEquals(MathError.Code.DOMAIN, s.error?.code)
        val l = FormulaEval.annotate(Formula.atomize("\\ln 0"), undefined = "undefined")
        assertEquals(MathError.Code.DOMAIN, l.error?.code)
        assertTrue(l.source.endsWith("\\text{undefined}"))
        // hata düzelince ileti ve "tanımsız" kalkar
        val ok = FormulaEval.annotate(Formula.setSlot(d.source, Formula.slots(d.source)[1].name, "4"))
        assertNull(ok.error)
        assertEquals("=\\frac{1}{4}", ok.result)
        assertFalse(ok.source.contains("tanımsız"))
    }

    @Test fun nothingAddedWhileUnknownsRemainOrForPlainNumbers() {
        val o = FormulaEval.annotate(Formula.atomize("2x+3"))
        assertNull(o.result)
        assertEquals(Formula.atomize("2x+3"), o.source)
        assertNull(FormulaEval.annotate(Formula.atomize("5")).result)
        assertNull(FormulaEval.annotate(Formula.atomize("x = 5")).result)
        // değerlendirilemeyen yapılar (integral, toplam, ±) olduğu gibi kalır
        for (t in listOf("\\int_0^1 x\\,dx", "\\sum_{k=1}^{n} k", "1 \\pm 2", "\\vec{v}")) {
            val r = FormulaEval.annotate(Formula.atomize(t))
            assertNull(t, r.result)
        }
    }

    @Test fun resultUpdatesAndStripsWhenUnknownComesBack() {
        val a = run("2x+3", mapOf("x" to "4"))
        val slots = Formula.slots(a.source)
        val xSlot = slots.first { it.value == "4" }.name
        val b = FormulaEval.annotate(Formula.setSlot(a.source, xSlot, "5"))
        assertEquals("=13", b.result)
        assertEquals(1, Regex(Regex.escape(FormulaEval.RESULT_MARK)).findAll(b.source).count())
        val c = FormulaEval.annotate(Formula.setSlot(b.source, xSlot, "x"))
        assertNull(c.result)
        assertFalse(c.source.contains("dgres"))
        assertEquals(Formula.atomize("2x+3"), c.source)
    }

    @Test fun markersSurviveAtomizeAndPlain() {
        val a = run("2x+3", mapOf("x" to "4"))
        // elemanlarına ayırma sonucu yuvaya bölmez (yeniden ayırmada aynı kalır)
        assertEquals(a.source, Formula.atomize(a.source))
        assertTrue(Formula.isAtomized(a.source))
        // elle düzenleme metninde sonuç yok
        val plain = Formula.plain(a.source)
        assertFalse(plain, plain.contains("dgres"))
        assertFalse(plain, plain.contains("11"))
        // değiştirilmiş bağıntı elle düzenlemede kullanıcının özgün bağıntısıdır
        val r = run("x^2 \\ge 10", mapOf("x" to "3"))
        assertTrue(Formula.plain(r.source).contains("\\ge"))
        // dizgi motoru işaretli kaynağı sorunsuz dizer (görünmez işaretler)
        assertFalse(Formula.plainText(r.source).contains("dgrel"))
    }

    @Test fun catalogTemplatesDoNotBreak() {
        // Katalogdaki bütün şablonlar: hesap ya hiçbir şey eklemez ya da geçerli bir sonuç ekler; kaynak bozulmaz
        for (cat in FormulaCatalog.categories) for (t in cat.templates) {
            val src = Formula.atomize(t.source)
            val o = FormulaEval.annotate(src)
            if (o.result == null) assertEquals(t.id, src, o.source)
            assertEquals(t.id, src, FormulaEval.strip(o.source))
        }
    }
}
