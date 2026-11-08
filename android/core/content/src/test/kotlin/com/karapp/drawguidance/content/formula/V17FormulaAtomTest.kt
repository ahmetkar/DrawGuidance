package com.karapp.drawguidance.content.formula

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v17: formül elemanlarına ayrılır — her sayı ve harf ayrı yuva, aynı harfin geçişleri birbirinden bağımsız. */
class V17FormulaAtomTest {

    private val m = Java2DGlyphMetrics()

    @Test fun everyLetterAndNumberBecomesItsOwnSlot() {
        val a = Formula.atomize("x_{1,2} = \\frac{-\\slot{b}{b} \\pm \\sqrt{\\slot{b}{b}^2 - 4\\slot{a}{a}\\slot{c}{c}}}{2\\slot{a}{a}}")
        val slots = Formula.slots(a)
        assertEquals(listOf("x", "1", "2", "b", "b", "2", "4", "a", "c", "2", "a"), slots.map { it.value })
        // Adlar benzersiz ve sıralı
        assertEquals((1..slots.size).map { it.toString() }, slots.map { it.name })
        assertTrue(Formula.isAtomized(a))
        assertFalse(Formula.isAtomized("a+b"))
        // Yuva işaretleri atılınca kaynak aynı ifadeye döner
        assertEquals("x_{1,2} = \\frac{-b \\pm \\sqrt{b^{2} - 4ac}}{2a}", Formula.plain(a))
    }

    @Test fun sameLetterIsEditedIndependently() {
        val a = Formula.atomize("(a + b)^2 = a^2 + 2ab + b^2")
        val slots = Formula.slots(a)
        val firstA = slots.first { it.value == "a" }.name
        val edited = Formula.setSlot(a, firstA, "3")
        val after = Formula.slots(edited)
        assertEquals("3", after.first { it.name == firstA }.value)
        // Diğer a'lar değişmedi
        assertEquals(2, after.count { it.value == "a" })
        // Dizgide her geçiş ayrı bir bölge
        val l = Formula.layout(edited, 40f, m)
        assertEquals(slots.size, l.slots.size)
    }

    @Test fun textEnvironmentsDelimitersAndGreekAreHandled() {
        val src = "|x| = \\begin{cases} x, & x \\ge 0 \\\\ -x, & x < 0 \\end{cases} \\quad \\text{mutlak} \\left(\\alpha x\\right)^{n}"
        val a = Formula.atomize(src)
        assertTrue(a.contains("\\begin{cases}") && a.contains("\\end{cases}"))
        assertTrue(a.contains("\\text{mutlak}"))
        assertTrue(a.contains("\\left(") && a.contains("\\right)"))
        assertTrue(Formula.slots(a).any { it.value == "\\alpha" })
        // Dizilir ve düz metne döner
        val l = Formula.layout(a, 40f, m)
        assertTrue(l.width > 0f && l.slots.isNotEmpty())
        assertEquals(Formula.atomize(Formula.plain(a)), a)
    }

    @Test fun decimalsScriptsAndRootsStayCorrect() {
        val a = Formula.atomize("\\sqrt[3]{x^2} + 3.14r^2 + x_1")
        val values = Formula.slots(a).map { it.value }
        assertEquals(listOf("3", "x", "2", "3.14", "r", "2", "x", "1"), values)
        assertEquals("\\sqrt[3]{x^{2}} + 3.14r^{2} + x_{1}", Formula.plain(a))
        // Katalogdaki her formül ayrılır ve hatasız dizilir
        FormulaCatalog.categories.flatMap { it.templates }.forEach { t ->
            val s = Formula.atomize(t.source)
            val l = Formula.layout(s, 40f, m)
            assertTrue(t.id, l.width.isFinite() && l.width > 0f)
            assertEquals(t.id, Formula.plainText(Formula.plain(t.source)).replace(" ", ""), Formula.plainText(Formula.plain(s)).replace(" ", ""))
        }
    }
}
