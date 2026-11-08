package com.karapp.drawguidance

import com.karapp.drawguidance.data.diagram.DiagramRouting
import com.karapp.drawguidance.data.diagram.RoutePoint
import com.karapp.drawguidance.data.diagram.RouteRect
import com.karapp.drawguidance.data.model.DiagramSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Diyagram oklarının yolu: seçilen taraftan dik çıkar/girer, kutuların içinden geçmez, düz parçalardan oluşur. */
class DiagramRoutingTest {

    private val top = RouteRect(400f, 100f, 600f, 200f)
    private val bottom = RouteRect(400f, 600f, 600f, 700f)
    private val right = RouteRect(800f, 350f, 950f, 450f)

    private fun assertOrthogonal(path: List<RoutePoint>) {
        for (i in 1 until path.size) {
            val a = path[i - 1]; val b = path[i]
            assertTrue("dik parça: $a → $b", abs(a.x - b.x) < 1e-3f || abs(a.y - b.y) < 1e-3f)
        }
    }

    private fun insideInterior(p: RoutePoint, r: RouteRect) = p.x > r.left + 1f && p.x < r.right - 1f && p.y > r.top + 1f && p.y < r.bottom - 1f

    @Test fun autoSidesGiveAStraightArrowBetweenBorders() {
        val path = DiagramRouting.route(top, bottom, DiagramSide.AUTO, DiagramSide.AUTO, 30f)
        assertEquals(2, path.size)
        assertEquals(RoutePoint(500f, 200f), path.first())
        assertEquals(RoutePoint(500f, 600f), path.last())
    }

    @Test fun chosenSidesAreUsedAndThePathIsOrthogonal() {
        // Üstteki kutunun sağından, alttakinin sağına: dışarıdan dolaşan "⊐" biçimi
        val path = DiagramRouting.route(top, bottom, DiagramSide.RIGHT, DiagramSide.RIGHT, 30f)
        assertEquals(RoutePoint(600f, 150f), path.first())
        assertEquals(RoutePoint(600f, 650f), path.last())
        assertOrthogonal(path)
        assertTrue("sağa çıkar", path[1].x > 600f)
        assertTrue("sağdan girer", path[path.size - 2].x > 600f)
        // Alttan çıkıp soldan giren ok: L biçimi
        val l = DiagramRouting.route(top, right, DiagramSide.BOTTOM, DiagramSide.LEFT, 30f)
        assertEquals(RoutePoint(500f, 200f), l.first())
        assertEquals(RoutePoint(800f, 400f), l.last())
        assertOrthogonal(l)
        assertEquals("tek kırılım", 3, l.size)
    }

    @Test fun pathsNeverCrossTheBoxes() {
        val sides = DiagramSide.entries.filter { it != DiagramSide.AUTO }
        for (a in sides) for (b in sides) {
            val path = DiagramRouting.route(top, bottom, a, b, 30f)
            assertOrthogonal(path)
            // Ara noktalar kutuların içinde değil
            path.drop(1).dropLast(1).forEach { p ->
                assertTrue("$a→$b: $p üstteki kutunun içinde", !insideInterior(p, top))
                assertTrue("$a→$b: $p alttaki kutunun içinde", !insideInterior(p, bottom))
            }
        }
    }

    @Test fun nearestSideAndMidpoint() {
        assertEquals(DiagramSide.TOP, DiagramRouting.nearestSide(top, RoutePoint(500f, 105f)))
        assertEquals(DiagramSide.LEFT, DiagramRouting.nearestSide(top, RoutePoint(405f, 150f)))
        assertEquals(DiagramSide.RIGHT, DiagramRouting.nearestSide(top, RoutePoint(700f, 150f)))
        val mid = DiagramRouting.midpoint(listOf(RoutePoint(0f, 0f), RoutePoint(100f, 0f), RoutePoint(100f, 100f)))
        assertEquals(RoutePoint(100f, 0f), mid)
        assertEquals(0f, DiagramRouting.distance(listOf(RoutePoint(0f, 0f), RoutePoint(100f, 0f)), RoutePoint(50f, 0f)), 1e-4f)
    }
}
