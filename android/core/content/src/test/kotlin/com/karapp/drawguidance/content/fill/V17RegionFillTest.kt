package com.karapp.drawguidance.content.fill

import com.karapp.drawguidance.content.vector.P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** v17: alan alan boyama — çizgilerle çevrili alan bulunur, içteki adacıklar delik kalır, açık şekil boyanmaz. */
class V17RegionFillTest {

    private fun square(l: Float, t: Float, r: Float, b: Float) = RegionFill.Line(listOf(P(l, t), P(r, t), P(r, b), P(l, b)), closed = true, width = 2f)
    private fun circle(cx: Float, cy: Float, rad: Float) =
        RegionFill.Line((0 until 48).map { val a = it * 2 * PI / 48; P(cx + rad * cos(a).toFloat(), cy + rad * sin(a).toFloat()) }, closed = true, width = 2f)

    @Test fun ringBetweenSquareAndCircleKeepsTheHole() {
        val lines = listOf(square(0f, 0f, 100f, 100f), circle(50f, 50f, 20f))
        val ring = RegionFill.region(lines, P(10f, 10f))
        assertNotNull(ring)
        ring!!
        assertTrue(RegionFill.contains(ring, P(10f, 10f)))
        assertTrue(RegionFill.contains(ring, P(90f, 50f)))
        // Dairenin içi boyanmaz
        assertFalse(RegionFill.contains(ring, P(50f, 50f)))
        // Sınır çizginin iç kenarında (çizgiyi örtmez, arada boşluk da kalmaz)
        assertTrue(RegionFill.contains(ring, P(1.3f, 50f)))
        assertFalse(RegionFill.contains(ring, P(0.3f, 50f)))
        assertNotNull(RegionFill.interiorPoint(ring)?.takeIf { RegionFill.contains(ring, it) && !RegionFill.contains(RegionFill.region(lines, P(50f, 50f))!!, it) })
        // Alan ≈ iç kare - daire (çizgi kalınlığı düşülür)
        val expected = 98.4f * 98.4f - PI.toFloat() * 20.8f * 20.8f
        assertEquals(expected, RegionFill.areaOf(ring), expected * 0.02f)

        val inner = RegionFill.region(lines, P(50f, 50f))!!
        assertTrue(RegionFill.contains(inner, P(50f, 50f)))
        assertFalse(RegionFill.contains(inner, P(10f, 10f)))
        assertEquals(PI.toFloat() * 19.2f * 19.2f, RegionFill.areaOf(inner), 40f)
    }

    @Test fun openShapeOrOutsideIsNotFilled() {
        val open = RegionFill.Line(listOf(P(0f, 0f), P(100f, 0f), P(100f, 100f), P(0f, 100f)), closed = false, width = 2f)
        assertNull(RegionFill.region(listOf(open), P(50f, 50f)))
        // Küçük boşluk izinle kapanır
        val gapped = RegionFill.Line(listOf(P(3f, 0f), P(100f, 0f), P(100f, 100f), P(0f, 100f), P(0f, 3f)), closed = false, width = 2f)
        assertNull(RegionFill.region(listOf(gapped), P(50f, 50f)))
        assertNotNull(RegionFill.region(listOf(gapped), P(50f, 50f), gap = 3f))
        // Kapalı şeklin dışı
        assertNull(RegionFill.region(listOf(square(0f, 0f, 100f, 100f)), P(150f, 50f)))
    }

    @Test fun crossingLinesMakeSeparateCells() {
        // Kare + ortadan artı: dört ayrı hücre
        val lines = listOf(
            square(0f, 0f, 100f, 100f),
            RegionFill.Line(listOf(P(50f, 0f), P(50f, 100f)), closed = false, width = 2f),
            RegionFill.Line(listOf(P(0f, 50f), P(100f, 50f)), closed = false, width = 2f),
        )
        val cell = RegionFill.region(lines, P(25f, 25f))!!
        assertTrue(RegionFill.contains(cell, P(25f, 25f)))
        assertFalse(RegionFill.contains(cell, P(75f, 25f)))
        assertFalse(RegionFill.contains(cell, P(25f, 75f)))
        assertEquals(48.4f * 48.4f, RegionFill.areaOf(cell), 60f)
    }
}
