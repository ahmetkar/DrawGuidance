package com.karapp.drawguidance.content.graph

import com.karapp.drawguidance.content.math.MathError
import com.karapp.drawguidance.content.vector.VPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v45 (5): her grafik türünde formül — anlamı, doğrulama, çoklu eğri, sayı doğrusunda çözüm kümesi, örneklenen
 * grafikler, asimptot ve tanımsız noktalarda eğrinin kesilmesi, derece modu.
 */
class V45GraphFormulaTest {

    init { System.setProperty("graphs.strict", "true") }

    private fun curves(paths: List<VPath>, tag: String = "curve") = paths.filter { it.tag == tag }

    @Test fun meaningPerKind() {
        assertEquals(GraphFormula.Meaning.CURVE, GraphFormula.meaning(GraphKind.FUNCTION))
        assertEquals(GraphFormula.Meaning.CURVE, GraphFormula.meaning(GraphKind.AXES))
        assertEquals(GraphFormula.Meaning.CURVE, GraphFormula.meaning(GraphKind.COORD_PLANE))
        assertEquals(GraphFormula.Meaning.SURFACE, GraphFormula.meaning(GraphKind.AXES_3D))
        assertEquals(GraphFormula.Meaning.SET, GraphFormula.meaning(GraphKind.NUMBER_LINE))
        for (k in listOf(GraphKind.BAR, GraphKind.LINE, GraphKind.PIE, GraphKind.SCATTER, GraphKind.HISTOGRAM)) {
            assertEquals(GraphFormula.Meaning.SAMPLED, GraphFormula.meaning(k))
        }
        assertFalse(GraphFormula.accepts(GraphKind.UNIT_CIRCLE))
        for (k in GraphKind.entries) if (k != GraphKind.UNIT_CIRCLE) {
            assertTrue(k.name, GraphFormula.accepts(k))
            assertNull("örnek formül geçerli: $k", GraphFormula.error(k, GraphFormula.example(k)))
        }
    }

    @Test fun validationPerKind() {
        fun code(k: GraphKind, f: String) = GraphFormula.error(k, f)?.code
        assertNull(code(GraphKind.FUNCTION, "y = x^2 - 2"))
        assertNull(code(GraphKind.FUNCTION, "x^2 + y^2 = 9"))
        assertNull(code(GraphKind.AXES, "sin(x); cos(x)"))
        assertEquals(MathError.Code.UNKNOWN_NAME, code(GraphKind.FUNCTION, "y = 2a x"))
        assertEquals(MathError.Code.PARENTHESIS, code(GraphKind.COORD_PLANE, "sin(x"))
        assertEquals(MathError.Code.MISSING_OPERAND, code(GraphKind.FUNCTION, "x^"))
        assertNull(code(GraphKind.AXES_3D, "z = x^2 - y^2"))
        assertEquals(MathError.Code.RELATION, code(GraphKind.AXES_3D, "x + y = 1"))
        assertEquals(MathError.Code.UNKNOWN_NAME, code(GraphKind.AXES_3D, "z = x t"))
        assertNull(code(GraphKind.NUMBER_LINE, "-1 ≤ x < 3"))
        assertNull(code(GraphKind.NUMBER_LINE, "x = 2; x = -1"))
        assertNull(code(GraphKind.NUMBER_LINE, "2.5"))
        assertEquals(MathError.Code.UNKNOWN_NAME, code(GraphKind.NUMBER_LINE, "y > 2"))
        assertNull(code(GraphKind.BAR, "y = 2x + 1"))
        assertEquals(MathError.Code.UNKNOWN_NAME, code(GraphKind.LINE, "y = x + y"))
        assertEquals(MathError.Code.RELATION, code(GraphKind.LINE, "x^2 + y^2 = 4"))
        assertEquals(MathError.Code.RELATION, code(GraphKind.SCATTER, "x; 2x"))
        assertNotNull(code(GraphKind.UNIT_CIRCLE, "x"))
        assertNull(code(GraphKind.FUNCTION, ""))
    }

    @Test fun partsSplitOnlyAtTopLevel() {
        assertEquals(listOf("x^2", "2x+1"), GraphFormula.parts("x^2; 2x+1"))
        assertEquals(listOf("min(x; 2)", "x"), GraphFormula.parts("min(x; 2) ; x"))
        assertEquals(listOf("a", "b"), GraphFormula.parts("a\nb\n"))
        assertEquals(emptyList<String>(), GraphFormula.parts(" ; "))
    }

    @Test fun withFormulaAppliesOrKeepsLastValid() {
        val p = GraphParams(GraphKind.FUNCTION, preset = "sinus", freq = 3)
        val q = Graphs.withFormula(p, "y = x^3")
        assertEquals("y = x^3", q.formula)
        assertEquals("", q.preset)
        assertEquals(1, q.freq)
        // geçersiz formül: son geçerli grafik kalır
        assertEquals(q, Graphs.withFormula(q, "y = x^"))
        // boş formül kaldırır
        assertEquals("", Graphs.withFormula(q, "  ").formula)
        // örneklenen grafik: elle girilen değerler silinir
        val bar = GraphParams(GraphKind.BAR, values = listOf(1f, 2f, 3f))
        val bar2 = Graphs.withFormula(bar, "y = x^2")
        assertTrue(bar2.values.isEmpty())
        // birim çember formül almaz
        val uc = GraphParams(GraphKind.UNIT_CIRCLE)
        assertEquals(uc, Graphs.withFormula(uc, "x^2"))
        assertEquals("sin(x)", Graphs.formulaText(GraphParams(GraphKind.FUNCTION, preset = "sinus")))
    }

    @Test fun axesAndPlaneDrawFormulaCurves() {
        for (k in listOf(GraphKind.AXES, GraphKind.COORD_PLANE, GraphKind.FUNCTION)) {
            val none = Graphs.build(GraphParams(k, formula = if (k == GraphKind.FUNCTION) "x" else ""), 1.3f)
            val with = Graphs.build(GraphParams(k, formula = "y = x^2 - 2"), 1.3f)
            if (k != GraphKind.FUNCTION) assertTrue(k.name, curves(none).isEmpty())
            assertTrue(k.name, curves(with).isNotEmpty())
            assertTrue(k.name, with.any { it.tag == "formula" && it.label?.contains("x²") == true })
            // eğri kutunun içinde
            for (c in curves(with)) for (pt in c.points) assertTrue(pt.x in -0.001f..1.001f && pt.y in -0.001f..1.001f)
        }
    }

    @Test fun multipleCurvesHaveOwnTagsColorsAndLabel() {
        val p = GraphParams(GraphKind.COORD_PLANE, formula = "y = x^2 - 2; y = 2x + 1; x^2 + y^2 = 4")
        val paths = Graphs.build(p, 1.3f)
        assertTrue(curves(paths).isNotEmpty())
        val c1 = curves(paths, "curve:1")
        val c2 = curves(paths, "curve:2")
        assertTrue(c1.isNotEmpty()); assertTrue(c2.isNotEmpty())
        assertTrue(c1.all { it.stroke == Cartesian.EXTRA_COLORS[0] })
        assertTrue(c2.all { it.stroke == Cartesian.EXTRA_COLORS[1] })
        val label = Graphs.formulaLabel(p)
        assertTrue(label, label.contains("x² − 2") && label.contains("2x + 1") && label.contains("x² + y² = 4"))
        // en çok 4 eğri
        val many = GraphParams(GraphKind.AXES, formula = (1..7).joinToString(";") { "x + $it" })
        val tags = Graphs.build(many, 1.3f).mapNotNull { it.tag }.filter { it.startsWith("curve:") }.toSet()
        assertEquals(setOf("curve:1", "curve:2", "curve:3"), tags)
    }

    /** Eğri parçalarında x'e göre en büyük ardışık dikey sıçrama (kutu birimi). */
    private fun maxJump(paths: List<VPath>): Float = curves(paths).maxOf { c ->
        c.points.zipWithNext { a, b -> abs(b.y - a.y) }.maxOrNull() ?: 0f
    }

    @Test fun asymptotesAndNaNBreakTheCurve() {
        // 1/x: x = 0'da iki ayrı parça, aradaki dikey çizgi yok
        val hyper = Graphs.build(GraphParams(GraphKind.FUNCTION, formula = "1/x"), 1.3f)
        assertTrue(curves(hyper).size >= 2)
        assertTrue("dikey bağlantı: ${maxJump(hyper)}", maxJump(hyper) < 0.5f)
        // tan(x): her asimptotta kesilir
        val tan = Graphs.build(GraphParams(GraphKind.FUNCTION, formula = "tan(x)"), 1.3f)
        assertTrue(curves(tan).size >= 3)
        assertTrue(maxJump(tan) < 0.5f)
        // sqrt(x) ve ln(x): x < 0 tanımsız, eğri yalnız sağda
        for (f in listOf("sqrt(x)", "ln(x)")) {
            val paths = Graphs.build(GraphParams(GraphKind.AXES, formula = f), 1.3f)
            val xs = curves(paths).flatMap { it.points.map { p -> p.x } }
            assertTrue(f, xs.isNotEmpty())
            val axis = paths.first { it.tag == null && it.points.size == 2 && abs(it.points[0].x - it.points[1].x) < 1e-4f && abs(it.points[0].y - it.points[1].y) > 0.5f }.points[0].x
            assertTrue("$f eksenin solunda çizilmemeli", xs.min() >= axis - 0.01f)
        }
        // ek eğrilerde de kesilme: 1/(x-1) ikinci eğri
        val two = Graphs.build(GraphParams(GraphKind.COORD_PLANE, formula = "x; 1/(x-1)"), 1.3f)
        val extra = curves(two, "curve:1")
        assertTrue(extra.size >= 2)
        assertTrue(extra.maxOf { c -> c.points.zipWithNext { a, b -> abs(b.y - a.y) }.maxOrNull() ?: 0f } < 0.5f)
    }

    @Test fun degreeModeChangesPeriod() {
        val rad = Graphs.build(GraphParams(GraphKind.FUNCTION, formula = "sin(x)", xMin = 0f, xMax = 360f), 1.3f)
        val deg = Graphs.build(GraphParams(GraphKind.FUNCTION, formula = "sin(x)", xMin = 0f, xMax = 360f, angleDeg = true), 1.3f)
        fun crossings(paths: List<VPath>): Int {
            val pts = curves(paths).flatMap { it.points }.sortedBy { it.x }
            val mid = (pts.minOf { it.y } + pts.maxOf { it.y }) / 2
            return pts.zipWithNext().count { (a, b) -> (a.y - mid) * (b.y - mid) < 0 }
        }
        // radyanda 360 birimde ~57 periyot, derecede 1 periyot
        assertTrue(crossings(rad) > 50)
        assertTrue("derece: ${crossings(deg)}", crossings(deg) in 1..4)
    }

    @Test fun axes3dDrawsSurfaceFromFormula() {
        val plain = Graphs.build(GraphParams(GraphKind.AXES_3D), 1f)
        val surf = Graphs.build(GraphParams(GraphKind.AXES_3D, formula = "z = x^2 - y^2"), 1f)
        assertTrue(curves(plain).isEmpty())
        assertTrue(curves(surf).size > 10)
        assertTrue(Graphs.formulaLabel(GraphParams(GraphKind.AXES_3D, formula = "x^2 - y^2")).startsWith("z ="))
    }

    @Test fun numberLineSolutionSets() {
        fun solve(f: String) = GraphFormula.solve(f, -5.0, 5.0)!!
        // tek nokta
        assertEquals(listOf(3.0), solve("x = 3").points)
        assertEquals(listOf(2.5), solve("2.5").points)
        assertEquals(listOf(-2.0, 2.0), solve("x^2 = 4").points)
        assertEquals(listOf(0.0), solve("x^2 = 0").points)
        // açık/kapalı aralık
        val a = solve("-1 ≤ x < 3")
        assertEquals(1, a.intervals.size)
        val iv = a.intervals[0]
        assertEquals(-1.0, iv.a, 1e-6); assertEquals(3.0, iv.b, 1e-6)
        assertTrue(iv.aClosed); assertFalse(iv.bClosed)
        assertFalse(iv.aInf); assertFalse(iv.bInf)
        // sonsuza giden ışın
        val r = solve("x > 2").intervals.single()
        assertEquals(2.0, r.a, 1e-6); assertFalse(r.aClosed); assertTrue(r.bInf)
        val l = solve("x ≤ -1").intervals.single()
        assertTrue(l.aInf); assertEquals(-1.0, l.b, 1e-6); assertTrue(l.bClosed)
        // iki parçalı: x² > 4
        val two = solve("x^2 > 4").intervals
        assertEquals(2, two.size)
        assertEquals(-2.0, two[0].b, 1e-6); assertEquals(2.0, two[1].a, 1e-6)
        // |x| ≤ 1
        val abs1 = solve("|x| <= 1").intervals.single()
        assertEquals(-1.0, abs1.a, 1e-6); assertEquals(1.0, abs1.b, 1e-6); assertTrue(abs1.aClosed && abs1.bClosed)
        // x ≠ 0: iki ışın, 0 açık
        val ne = solve("x != 0").intervals
        assertEquals(2, ne.size)
        assertFalse(ne[0].bClosed); assertFalse(ne[1].aClosed)
        // boş küme
        assertTrue(solve("x^2 < -1").isEmpty)
        // 1/x > 0: asimptotta kapanmaz
        val inv = solve("1/x > 0").intervals.single()
        assertEquals(0.0, inv.a, 1e-6); assertFalse(inv.aClosed)
        // eşitlikli uç tek nokta: x ≤ 0 ve x ≥ 0 zinciri (0 ≤ x ≤ 0)
        assertEquals(listOf(0.0), solve("0 <= x <= 0").points)
        assertNull(GraphFormula.solve("y > 1", -5.0, 5.0))
    }

    @Test fun numberLineDrawsSetWithDotsAndLabel() {
        val p = GraphParams(GraphKind.NUMBER_LINE, formula = "-1 ≤ x < 3", repeat = 5)
        val paths = Graphs.build(p, 5f)
        val seg = curves(paths)
        assertEquals(1, seg.size)
        assertTrue(seg[0].stroke == "#1E88E5" && seg[0].width > 2f)
        val keys = paths.filter { it.tag == "key" }
        // dolu uçta dolgu, açık uçta yalnız kenar (iki yol)
        assertTrue(keys.any { it.fill == "#1E88E5" })
        assertTrue(keys.any { it.fill != "#1E88E5" })
        val label = paths.first { it.tag == "formula" }.label!!
        assertEquals("−1 ≤ x < 3", label)
        // noktalar: x = -2; x = 4 ikinci formül farklı renkte
        val pts = Graphs.build(GraphParams(GraphKind.NUMBER_LINE, formula = "x = -2; x = 4", repeat = 5), 5f)
        val dots = pts.filter { it.tag == "key" }
        assertTrue(dots.size >= 2)
        assertTrue(dots.map { it.fill }.toSet().size >= 2)
        assertEquals("x = −2;  x = 4", Graphs.formulaLabel(GraphParams(GraphKind.NUMBER_LINE, formula = "x = -2; x = 4")))
    }

    @Test fun sampledChartsUseFormula() {
        val bar = GraphParams(GraphKind.BAR, formula = "y = x^2", repeat = 2)
        assertEquals(listOf(1f, 4f, 9f, 16f, 25f), Graphs.values(bar))
        val line = GraphParams(GraphKind.LINE, formula = "2x + 1", repeat = 1)
        assertEquals(listOf(3f, 5f, 7f, 9f), Graphs.values(line))
        val scatter = GraphParams(GraphKind.SCATTER, formula = "10/x", repeat = 1)
        assertEquals(listOf(1f, 10f, 2f, 5f, 3f, 3.333f, 4f, 2.5f), Graphs.values(scatter))
        assertEquals(4, Graphs.itemCount(scatter))
        // pasta: negatifler 0
        val pie = GraphParams(GraphKind.PIE, formula = "x - 2", repeat = 1)
        assertEquals(listOf(0f, 0f, 1f, 2f), Graphs.values(pie))
        // tanımsız noktada 0 (SCATTER'da atlanır)
        val hole = GraphParams(GraphKind.LINE, formula = "1/(x-2)", repeat = 1)
        assertEquals(0f, Graphs.values(hole)[1])
        assertEquals(3, Graphs.itemCount(GraphParams(GraphKind.SCATTER, formula = "1/(x-2)", repeat = 1)))
        // desen artınca daha çok örnek
        assertEquals(6, Graphs.values(Graphs.morePattern(bar)).size)
        // formül yazısı grafikte
        for (k in listOf(GraphKind.BAR, GraphKind.LINE, GraphKind.PIE, GraphKind.SCATTER, GraphKind.HISTOGRAM)) {
            val paths = Graphs.build(GraphParams(k, formula = "y = x + 1"), 1.3f)
            assertTrue(k.name, paths.any { it.tag == "formula" && it.label == "y = x + 1" })
        }
        // elle değer girilince formül yerine değerler
        val edited = Graphs.withValue(bar, 0, 7f)
        assertEquals(7f, Graphs.values(edited)[0])
    }

    @Test fun unitCircleIgnoresFormula() {
        val a = Graphs.build(GraphParams(GraphKind.UNIT_CIRCLE), 1f)
        val b = Graphs.build(GraphParams(GraphKind.UNIT_CIRCLE, formula = "x^2"), 1f)
        assertEquals(a, b)
    }
}
