package com.karapp.drawguidance.content.formula

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/** Ayrıştırıcı + dizgi birim testleri (Java2D ölçüleriyle), yuva düzenleme ve katalog doğrulaması. */
class FormulaTest {

    private val m = Java2DGlyphMetrics()
    private val size = 40f

    private fun lay(src: String) = Formula.layout(src, size, m)
    private fun glyphs(l: FormulaLayout) = l.ops.filterIsInstance<DrawOp.Glyph>()
    private fun glyph(l: FormulaLayout, text: String) =
        glyphs(l).firstOrNull { it.text == text } ?: error("'$text' glifi yok: ${glyphs(l).map { it.text }}")
    private fun center(g: DrawOp.Glyph) = g.x + m.advance(g.text, g.size, g.style) / 2

    @Test
    fun fractionIsStackedAndCentered() {
        val l = lay("\\frac{a}{bb}")
        val a = glyph(l, "a")
        val bs = glyphs(l).filter { it.text == "b" }
        val bar = l.ops.filterIsInstance<DrawOp.Rule>().single()
        assertTrue("pay çizginin üstünde", a.y < bar.y1)
        assertTrue("payda çizginin altında", bs.all { it.y > bar.y1 })
        assertTrue("çizgi eksende (taban çizgisi üstünde)", bar.y1 < 0f && bar.y1 > -0.5f * size)
        val denCenter = (bs.minOf { it.x } + bs.maxOf { it.x + m.advance("b", it.size, it.style) }) / 2
        assertEquals("pay ve payda ortalı", center(a), denCenter, 1.5f)
        assertTrue("çizgi paydayı kapsar", bar.x1 <= bs.minOf { it.x } && bar.x2 >= bs.maxOf { it.x + m.advance("b", it.size, it.style) })
        assertTrue(bar.thickness in 0.045f * size..0.065f * size)
    }

    @Test
    fun scriptsAreRaisedLoweredAndScaled() {
        val sup = glyph(lay("x^2"), "2")
        assertTrue("üst indis yukarıda", sup.y < -0.25f * size)
        assertEquals("script boyutu ~0.7", 0.7f * size, sup.size, 0.01f)
        val sub = glyph(lay("x_2"), "2")
        assertTrue("alt indis aşağıda", sub.y > 0.05f * size)
        val both = lay("x_1^2")
        val s1 = glyph(both, "1")
        val s2 = glyph(both, "2")
        assertTrue("üst indis alt indisin üstünde", s2.y < s1.y - 0.4f * size)
        assertTrue("x italik", glyph(both, "x").style == GlyphStyle.ITALIC)
        assertTrue("rakam düz", s1.style == GlyphStyle.ROMAN)
        val ss = glyph(lay("e^{x^2}"), "2")
        assertEquals("scriptscript ~0.5", 0.5f * size, ss.size, 0.01f)
    }

    @Test
    fun functionsRomanAndSpacing() {
        val l = lay("\\sin x + 1 = y")
        assertEquals(GlyphStyle.ROMAN, glyph(l, "sin").style)
        val plus = glyph(l, "+")
        val x = glyph(l, "x")
        assertTrue("ikili işleç çevresinde boşluk", plus.x - (x.x + m.advance("x", size, GlyphStyle.ITALIC)) > 0.15f * size)
        val minus = glyph(lay("-b"), "−")
        assertEquals("tekli eksi boşluksuz", 0f, minus.x, 0.01f)
    }

    @Test
    fun bigOperatorsAndIntegrals() {
        val sum = lay("\\sum_{i=1}^{n} i")
        val sigma = glyph(sum, "Σ")
        assertTrue("display Σ büyük", sigma.size > 1.4f * size)
        assertTrue("alt sınır altta", glyph(sum, "1").y > 0.5f * size)
        assertTrue("üst sınır üstte", glyph(sum, "n").y < -0.8f * size)
        val int = lay("\\int_a^b f(x)\\,dx")
        val stroke = int.ops.filterIsInstance<DrawOp.Stroke>()
        assertTrue("integral çizim olarak", stroke.isNotEmpty())
        val ys = stroke.flatMap { s -> s.points.filterIndexed { k, _ -> k % 2 == 1 } }
        assertTrue("integral ~2 em yüksek", ys.max() - ys.min() > 1.8f * size)
        assertTrue("üst sınır sağda, yukarıda", glyph(int, "b").y < -0.6f * size)
        val lim = lay("\\lim_{x \\to 0} f(x)")
        assertTrue("lim altında sınır", glyph(lim, "0").y > 0.4f * size)
    }

    @Test
    fun radicalsDelimitersMatrices() {
        val r = lay("\\sqrt{x^2+1}")
        assertTrue(r.ops.any { it is DrawOp.Rule })
        assertTrue(r.ops.any { it is DrawOp.Stroke })
        val idx = lay("\\sqrt[3]{8}")
        assertEquals(0.5f * size, glyph(idx, "3").size, 0.01f)
        val big = lay("\\left(\\frac{a}{b}\\right)")
        assertFalse("büyük ayraç glif değil", glyphs(big).any { it.text == "(" })
        val small = lay("(a+b)")
        assertTrue("küçük içerikte glif ayraç", glyphs(small).any { it.text == "(" })
        val auto = lay("(\\frac{a}{b})")
        assertFalse("kesirli düz parantez büyür", glyphs(auto).any { it.text == "(" })
        val mat = lay("\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}")
        val a = glyph(mat, "a"); val b = glyph(mat, "b"); val c = glyph(mat, "c")
        assertEquals(a.y, b.y, 0.01f)
        assertEquals(a.x, c.x, 0.5f * size)
        assertTrue(c.y > a.y + 0.8f * size)
        val det = lay("\\begin{vmatrix} a & b \\\\ c & d \\end{vmatrix}")
        assertTrue("determinant çizgileri", det.ops.filterIsInstance<DrawOp.Stroke>().size >= 2)
        val cases = lay("f(x) = \\begin{cases} 1, & x > 0 \\\\ 0, & x \\le 0 \\end{cases}")
        assertTrue(cases.height > 1.8f * size)
    }

    @Test
    fun slotsProduceRegionsAndTags() {
        val src = "\\slot{a}{a}x^2 + \\slot{b}{b}x + \\slot{c}{c} = 0"
        assertEquals(listOf(Slot("a", "a"), Slot("b", "b"), Slot("c", "c")), Formula.slots(src))
        val l = lay(src)
        assertEquals(setOf("a", "b", "c"), l.slots.map { it.name }.toSet())
        val ga = glyphs(l).first { it.text == "a" }
        assertEquals("a", ga.slot)
        assertEquals(null, glyph(l, "x").slot)
        val ra = l.slots.first { it.name == "a" }
        assertTrue(ra.left <= ga.x && ra.right >= ga.x + m.advance("a", size, GlyphStyle.ITALIC) - 0.01f)
        assertTrue(ra.top < 0 && ra.bottom > 0)
        // aynı ad birden çok yerde → hepsi bölge, hepsi etiketli
        val twice = lay("(\\slot{a}{a}+\\slot{b}{b})^2 = \\slot{a}{a}^2 + 2\\slot{a}{a}\\slot{b}{b} + \\slot{b}{b}^2")
        assertEquals(3, twice.slots.count { it.name == "a" })
        assertEquals(3, glyphs(twice).count { it.slot == "a" })
        // boş yuva da dokunulabilir alan bırakır
        val empty = lay("x = \\slot{v}{}")
        assertTrue(empty.slots.single().let { it.right - it.left > 0.3f * size })
    }

    @Test
    fun setSlotRoundTrip() {
        val src = FormulaCatalog.find("algebra.quadratic")!!.source
        var s = Formula.setSlot(src, "a", "2")
        s = Formula.setSlot(s, "b", "-3")
        s = Formula.setSlot(s, "c", "\\frac{1}{2}")
        assertEquals(listOf(Slot("a", "2"), Slot("b", "-3"), Slot("c", "\\frac{1}{2}")), Formula.slots(s))
        assertEquals("2x² − 3x + 1/2 = 0", Formula.plainText(s))
        val l = lay(s)
        assertTrue(glyphs(l).any { it.text == "3" && it.slot == "b" })
        assertTrue("birleşen işaret de yuvaya ait", glyphs(l).any { it.text == "−" && it.slot == "b" })
        assertEquals(1, l.slots.count { it.name == "b" })
        // tüm geçişler birlikte değişir
        val sq = Formula.setSlot(FormulaCatalog.find("algebra.square_of_sum")!!.source, "a", "x")
        assertEquals("(x + b)² = x² + 2xb + b²", Formula.plainText(sq))
        // dengesiz parantezler kaynağı bozmaz
        val broken = Formula.setSlot("\\slot{n}{n}+1", "n", "{x}}{")
        assertEquals(listOf(Slot("n", "{x}{}")), Formula.slots(broken))
        assertEquals("\\slot{n}{\\}}", Formula.setSlot("\\slot{n}{n}", "n", "\\}\\"))
        assertEquals("olmayan yuva: değişmez", "x", Formula.setSlot("x", "q", "1"))
        assertEquals("\\slot{k}{5} y", Formula.setSlot("\\slot{k}q y", "k", "5"))
    }

    @Test
    fun smartSlotValues() {
        fun p(src: String, vararg kv: Pair<String, String>): String {
            var s = src
            kv.forEach { (k, v) -> s = Formula.setSlot(s, k, v) }
            return Formula.plainText(s)
        }
        val q = FormulaCatalog.find("algebra.quadratic_formula")!!.source
        assertEquals("x₁,₂ = (3 ± √((−3)² − 4·2·1)) / 2·2", p(q, "a" to "2", "b" to "-3", "c" to "1"))
        assertEquals("F = 2·3", p("F = \\slot{m}{m}\\slot{a}{a}", "m" to "2", "a" to "3"))
        assertEquals("2(−1)", p("2\\slot{a}{a}", "a" to "-1"))
        assertEquals("F = 5x", p("F = -\\slot{k}{k}x", "k" to "-5"))
        assertEquals("a − (−3)²", p("a - \\slot{b}{b}^2", "b" to "-3"))
        assertEquals("sin(x + 1)", p("\\sin \\slot{x}{x}", "x" to "x+1"))
    }

    @Test
    fun plainText() {
        assertEquals("x = (−b ± √(b² − 4ac)) / 2a", Formula.plainText("x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}"))
        assertEquals("E = mc²", Formula.plainText("E = mc^2"))
        assertEquals("sin² x + cos² x = 1", Formula.plainText("\\sin^2 x + \\cos^2 x = 1"))
        assertEquals("v⃗ = (a, b)", Formula.plainText("\\vec{v} = (a, b)"))
        assertEquals("30° + x₁", Formula.plainText("30^\\circ + x_1"))
        assertEquals("∫ₐᵇ f(x) dx", Formula.plainText("\\int_a^b f(x)\\,dx"))
        assertEquals("(a, b; c, d)", Formula.plainText("\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}"))
        assertEquals("C(n, k)", Formula.plainText("\\binom{n}{k}"))
    }

    @Test
    fun brokenInputNeverThrows() {
        val inputs = listOf(
            "", " ", "{", "}", "}}}{{{", "\\", "\\frac", "\\frac{a}", "x^", "_", "^^^", "\\sqrt[", "\\sqrt[3",
            "\\left(", "\\right)", "\\left( x", "\\begin{pmatrix} a & b", "\\end{cases}", "&&\\\\&", "a \\\\ b",
            "\\unknowncommand{x}", "\\slot", "\\slot{", "\\slot{a}", "\\slot{}{}", "x''''^2_3", "\\text{", "\\text{a\\}",
            "{".repeat(500) + "x", "\\frac{".repeat(200), "\\sqrt{".repeat(100) + "2", "(((((((", "))))]]]", "[a, b)",
            "\\left\\langle x \\right\\rangle", "🙂 + ∑∫√π≤²₃", "\\mathbb{R} \\mathbf{v} \\boldsymbol{\\alpha}",
            "\\begin{array}{cc} 1 & 2 \\end{array}", "\\overline{\\overline{x}}", "\\not= \\not\\in \\not",
            "\\int\\limits_0^1 \\sum\\nolimits_i \\lim\\limits_{x}", "a &= b \\\\ &= c", "\\color{red}{x}",
        )
        for (src in inputs) {
            val l = try {
                Formula.layout(src, size, m)
            } catch (e: Throwable) {
                fail("'$src' istisna attı: $e"); return
            }
            assertTrue("'$src' genişlik", l.width.isFinite() && l.width >= 0f)
            assertTrue("'$src' yükseklik", l.ascent.isFinite() && l.descent.isFinite())
            Formula.plainText(src)
            Formula.slots(src)
            Formula.setSlot(src, "a", "{")
        }
        // ölçüler de hata verse/NaN dönse bile
        val evil = object : GlyphMetrics {
            override fun advance(text: String, size: Float, style: GlyphStyle): Float = if (text == "x") Float.NaN else 1f
            override fun ascent(size: Float) = size
            override fun descent(size: Float) = size / 4
            override fun inkBounds(text: String, size: Float, style: GlyphStyle): FloatArray? = throw IllegalStateException()
        }
        val l = Formula.layout("\\frac{x}{2}", 20f, evil)
        assertTrue(l.width.isFinite())
        assertTrue(Formula.layout("x", Float.NaN, m).width > 0f)
    }

    @Test
    fun estimatesOnlyMetricsWork() {
        val plain = Java2DGlyphMetrics(withInk = false)
        for (t in FormulaCatalog.categories.flatMap { it.templates }) {
            val l = Formula.layout(t.source, size, plain)
            assertTrue(t.id, l.width > 0f && l.height > 0f)
        }
        val bar = Formula.layout("\\frac{1}{2}", size, plain).ops.filterIsInstance<DrawOp.Rule>().single()
        assertEquals("varsayılan eksen ~0.3 em", -0.3f * size, bar.y1, 0.02f * size)
    }

    @Test
    fun catalogIsCompleteAndEveryTemplateLaysOut() {
        val ids = HashSet<String>()
        val cats = FormulaCatalog.categories
        assertEquals(
            listOf("algebra", "functions", "trig", "matrix", "calculus", "probability", "physics", "geometry"),
            cats.map { it.id },
        )
        var total = 0
        for (c in cats) {
            assertTrue("boş kategori ${c.id}", c.templates.isNotEmpty())
            for (t in c.templates) {
                total++
                assertTrue("yinelenen kimlik ${t.id}", ids.add(t.id))
                assertTrue("kimlik biçimi ${t.id}", t.id.matches(Regex("^[a-z0-9_]+\\.[a-z0-9_]+$")) && t.id.startsWith(c.id + "."))
                assertNotNull(FormulaCatalog.find(t.id))
                assertFalse("${t.id}: açılmamış kısaltma", t.source.contains('@'))
                val l = Formula.layout(t.source, size, m)
                assertTrue("${t.id} boyut", l.width > size && l.ascent > 0f && l.descent > 0f)
                assertTrue("${t.id} düz metne düşmemeli", l.ops.size > 1)
                val slots = Formula.slots(t.source)
                assertTrue("${t.id}: yuva yok", slots.isNotEmpty())
                for (s in slots) {
                    assertTrue("${t.id}: '${s.name}' ad biçimi", s.name.matches(Regex("^[A-Za-z][A-Za-z0-9_]*$")))
                    val regions = l.slots.filter { it.name == s.name }
                    assertTrue("${t.id}: '${s.name}' bölgesi yok", regions.isNotEmpty())
                    assertTrue("${t.id}: '${s.name}' glifi yok", l.ops.any { it is DrawOp.Glyph && it.slot == s.name })
                    regions.forEach { r ->
                        assertTrue("${t.id}: '${s.name}' bölge kutu dışında $r", r.left >= -0.2f * size && r.right <= l.width + 0.2f * size &&
                            r.top >= -l.ascent - 0.2f * size && r.bottom <= l.descent + 0.2f * size)
                    }
                }
                // tüm yuvalara sayı yazılınca da dizilir
                var s = t.source
                slots.forEachIndexed { k, sl -> s = Formula.setSlot(s, sl.name, if (k % 2 == 0) "-${k + 2}" else "${k + 1},5") }
                assertTrue(t.id, Formula.layout(s, size, m).width > 0f)
                assertFalse(t.id, Formula.plainText(t.source).isBlank())
                for (op in l.ops) {
                    val ok = when (op) {
                        is DrawOp.Glyph -> op.x.isFinite() && op.y.isFinite()
                        is DrawOp.Rule -> op.x1.isFinite() && op.y2.isFinite()
                        is DrawOp.Stroke -> op.points.all { it.isFinite() } && op.points.size % 2 == 0
                    }
                    assertTrue(t.id, ok)
                }
            }
        }
        assertTrue("en az 70 şablon ($total)", total >= 70)
    }

    @Test
    fun layoutIsFast() {
        val sources = listOf(
            FormulaCatalog.find("algebra.quadratic_formula")!!.source,
            FormulaCatalog.find("probability.normal")!!.source,
            FormulaCatalog.find("matrix.m3")!!.source,
            "\\int_0^1 x^2\\,dx = \\frac{1}{3}",
        )
        repeat(300) { sources.forEach { Formula.layout(it, size, m) } }
        val n = 400
        val t0 = System.nanoTime()
        repeat(n) { sources.forEach { Formula.layout(it, size, m) } }
        val perMs = (System.nanoTime() - t0) / 1e6 / (n * sources.size)
        println("formül dizgisi: %.3f ms / formül".format(perMs))
        assertTrue("dizgi çok yavaş: $perMs ms", perMs < 2.0)
    }

    @Test
    fun layoutCoordinatesFollowContract() {
        val l = lay("\\frac{\\sum_{k=1}^{n} k^2}{\\sqrt{2}}")
        for (op in l.ops) {
            if (op is DrawOp.Glyph) {
                assertTrue(op.y > -l.ascent && op.y < l.descent + 1)
                assertTrue(op.x >= -0.1f * size && op.x < l.width)
            }
        }
        assertTrue(abs(l.height - (l.ascent + l.descent)) < 1e-3f)
    }
}
