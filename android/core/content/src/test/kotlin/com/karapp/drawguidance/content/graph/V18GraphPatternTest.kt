package com.karapp.drawguidance.content.graph

import com.karapp.drawguidance.content.vector.VPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v18 grafik: fonksiyonda "desen" eğrinin yatay sıklığıdır (eksenler aynı kalır, formül k·x ile yazılır); grafiğin
 * formül kutusu, nokta içleri ve ızgarası kağıt rengine uyar.
 */
class V18GraphPatternTest {

    init { System.setProperty("graphs.strict", "true") }

    private fun label(preset: String, k: Int) = Graphs.formulaLabel(GraphParams(GraphKind.FUNCTION, preset = preset, freq = k))

    @Test
    fun patternChangesFormulaText() {
        assertEquals("y = sin(x)", label("sinus", 1))
        assertEquals("y = sin(2x)", label("sinus", 2))
        assertEquals("y = cos(3x)", label("kosinus", 3))
        assertEquals("y = (2x)²", label("parabol", 2))
        assertEquals("y = 4x + 1", label("dogrusal", 2))
        assertEquals("y = (2x)³ − 6x", label("kubik", 2))
        assertEquals("y = ln(2x)", label("logaritma", 2))
        assertEquals("y = √(2x)", label("karekok", 2))
        assertEquals("y = 1/(2x)", label("hiperbol", 2))
        assertEquals("y = 2²ˣ", label("ustel", 2))
        assertEquals("y = |3x|", label("mutlak", 3))
        assertEquals("x² + y² = 9", label("cember", 1))
        // Kullanıcının formülü
        val own = GraphParams(GraphKind.FUNCTION, formula = "x^2 - 3x", freq = 2)
        assertEquals("y = (2x)² − 6x", Graphs.formulaLabel(own))
        // "exp", "max" gibi adların içindeki x'e dokunulmaz
        assertEquals("y = exp(2x)", FormulaText.scaleX("y = exp(x)", 2))
    }

    @Test
    fun patternStepsFrequencyNotRange() {
        val sine = GraphParams(GraphKind.FUNCTION, preset = "sinus", repeat = 2)
        val more = Graphs.morePattern(sine)
        assertEquals(2, more.freq)
        assertEquals("x aralığı değişmez", sine.repeat, more.repeat)
        assertEquals(2, Graphs.patternValue(more))
        assertEquals(1, Graphs.lessPattern(sine).freq)
        var p = sine
        repeat(20) { p = Graphs.morePattern(p) }
        assertEquals(Graphs.MAX_FREQ, p.freq)
        // Eksen çentikleri aynı, eğri farklı
        val a = Graphs.build(sine, 1.6f)
        val b = Graphs.build(more, 1.6f)
        fun ticks(paths: List<VPath>) = paths.filter { it.tag?.startsWith("tick:x") == true }.map { it.label }
        assertEquals(ticks(a), ticks(b))
        assertNotEquals(a.filter { it.tag == "curve" }, b.filter { it.tag == "curve" })
        // Formül kutusundaki yazı sıklığı gösterir
        assertTrue(b.any { it.tag == "formula" && it.label == "y = sin(2x)" })
        // Diğer grafiklerde desen eskisi gibi
        val plane = GraphParams(GraphKind.COORD_PLANE, repeat = 2)
        assertEquals(3, Graphs.morePattern(plane).repeat)
        assertEquals(1, Graphs.morePattern(plane).freq)
    }

    @Test
    fun editableFormulaCarriesFrequency() {
        assertEquals("sin(3*x)", Graphs.editableFormula(GraphParams(GraphKind.FUNCTION, preset = "sinus", freq = 3)))
        assertEquals("(2*x)^2", Graphs.editableFormula(GraphParams(GraphKind.FUNCTION, preset = "parabol", freq = 2)))
        assertEquals("1/(2*x)", Graphs.editableFormula(GraphParams(GraphKind.FUNCTION, preset = "hiperbol", freq = 2)))
        assertEquals("exp(2*x) + 1", Graphs.editableFormula(GraphParams(GraphKind.FUNCTION, formula = "exp(x) + 1", freq = 2)))
        assertEquals("sin(x)", Graphs.editableFormula(GraphParams(GraphKind.FUNCTION, preset = "sinus", freq = 1)))
        // Yazılabilir biçim yeniden okunabilir ve aynı eğriyi verir
        val k2 = GraphParams(GraphKind.FUNCTION, preset = "sinus", freq = 2)
        val typed = GraphParams(GraphKind.FUNCTION, formula = Graphs.editableFormula(k2))
        val e = Expression.parse(typed.formula)!!
        assertEquals(kotlin.math.sin(2 * 0.7), e.eval(0.7, 0.0), 1e-9)
    }

    @Test
    fun graphFollowsPaperColor() {
        val white = Graphs.build(GraphParams(GraphKind.FUNCTION, preset = "sinus"), 1.5f)
        val box = white.first { it.tag == "formula" && it.closed }
        assertEquals("#FFFFFF", box.fill)
        assertTrue(white.any { it.stroke == "#DDE3EA" })

        val dark = Graphs.build(GraphParams(GraphKind.FUNCTION, preset = "sinus", paper = "#1B1F24"), 1.5f)
        val darkBox = dark.first { it.tag == "formula" && it.closed }
        assertEquals("formül kutusu kağıt renginde", "#1B1F24", darkBox.fill)
        assertTrue("beyaz ızgara kalmaz", dark.none { it.stroke == "#DDE3EA" || it.fill == "#FFFFFF" })
        // Koyu kağıtta ızgara kağıttan açık
        val grid = dark.first { it.stroke != null && it.stroke!!.startsWith("#") && it.tag == null && it.width < 0.5f }.stroke!!
        assertTrue("ızgara $grid", Colors.luminance(grid) > Colors.luminance("#1B1F24"))

        // Pasta grafiğinde dilim ayraçları kağıt renginde
        val pie = Graphs.build(GraphParams(GraphKind.PIE, paper = "#FFF3E0"), 1f)
        val slices = pie.filter { it.tag?.startsWith("value:") == true && it.closed && it.fill != null }
        assertTrue(slices.any { it.stroke == "#FFF3E0" })
        assertTrue("beyaz ayraç kalmaz: ${slices.map { it.stroke }}", slices.none { it.stroke == "#FFFFFF" })
    }
}
