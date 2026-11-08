package com.karapp.drawguidance.content.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** v38 çizgi önerisinin çoklu çizgi adımı: açık uçların kümelenip birleştirilmesi ([InkLines.snapEndpoints]). */
class InkLinesTest {
    private fun line(ax: Float, ay: Float, bx: Float, by: Float) =
        InkLines.Item(InkShapeKind.LINE, InkShapes.densify(listOf(InkPoint(ax, ay), InkPoint(bx, by)), false))

    private fun near(a: InkPoint, b: InkPoint, tol: Float = 0.6f) = hypot(a.x - b.x, a.y - b.y) <= tol

    @Test fun boxSidesDrawnSeparatelyJoinAtCorners() {
        // Dört ayrı kenar, köşelerde 6–10 px boşluk ve taşma
        val items = listOf(
            line(106f, 100f, 296f, 103f),
            line(300f, 108f, 302f, 292f),
            line(295f, 300f, 108f, 297f),
            line(100f, 290f, 103f, 109f),
        )
        val r = InkLines.snapEndpoints(items)
        assertEquals(4, r.joints)
        val out = r.items.map { it.points }
        // Her kenarın sonu bir sonrakinin başıyla aynı noktada (kenarların kesişimi)
        for (i in out.indices) assertTrue("köşe $i", near(out[i].last(), out[(i + 1) % out.size].first()))
        // Kenarlar düz kalır (doğru yeniden kurulur)
        out.forEach { assertTrue(it.size >= 2) }
        // Köşe, iki doğrunun kesişiminde (uçların ağırlık merkezi (104,5; 104,5) değil): sol üst ≈ (103,15; 99,96)
        val tl = out[0].first()
        assertTrue("sol üst köşe $tl", hypot(tl.x - 103.15f, tl.y - 99.96f) < 1f)
    }

    @Test fun farApartEndsAreNotJoined() {
        val items = listOf(line(0f, 0f, 200f, 0f), line(0f, 300f, 200f, 300f))
        val r = InkLines.snapEndpoints(items)
        assertEquals(0, r.joints)
        assertSame(items, r.items)
    }

    @Test fun singleStrokeIsUnchanged() {
        val items = listOf(line(0f, 0f, 200f, 0f))
        val r = InkLines.snapEndpoints(items)
        assertEquals(0, r.joints)
        assertSame(items, r.items)
    }

    @Test fun closedShapesKeepTheirOutline() {
        val circle = InkLines.Item(InkShapeKind.CIRCLE, (0..64).map { i -> val a = i / 64f * 6.2832f; InkPoint(100f + 50f * cos(a), 100f + 50f * sin(a)) })
        val tail = line(152f, 100f, 260f, 100f)
        val r = InkLines.snapEndpoints(listOf(circle, tail))
        // Kapalı çizginin ucu yok: kuyruğun ucu tek başına kalır (birleştirilecek küme yok)
        assertEquals(0, r.joints)
        assertEquals(circle.points, r.items[0].points)
    }

    @Test fun curvedEndBlendsToTheJoint() {
        // Yay (serbest çizgi, tür yok) ve ucuna yakın başlayan doğru
        val arc = InkLines.Item(null, (0..40).map { i -> val a = 3.1416f * i / 40f; InkPoint(200f - 100f * cos(a), 200f - 100f * sin(a)) })
        val l = line(306f, 204f, 420f, 200f)
        val r = InkLines.snapEndpoints(listOf(arc, l))
        assertEquals(1, r.joints)
        val end = r.items[0].points.last(); val start = r.items[1].points.first()
        assertTrue("uçlar birleşti: $end / $start", near(end, start))
        // Yayın başı yerinde kalır (yalnız uç kısmı kayar)
        assertTrue(near(r.items[0].points.first(), arc.points.first()))
    }

    /** v38 Kåsa çember uydurması: iki yayla çizilen çember ortak çembere oturur; düz çizgi yay sayılmaz. */
    @Test fun twoArcsShareOneCircle() {
        val rnd = java.util.Random(3)
        val ring = (0..80).map { i ->
            val a = i / 80.0 * 2 * Math.PI
            val rr = 50f * (1f + (rnd.nextFloat() - 0.5f) * 0.06f)
            InkPoint(200f + rr * cos(a).toFloat(), 150f + rr * sin(a).toFloat())
        }
        val c = InkLines.fitCircle(ring)!!
        assertEquals(200f, c.cx, 1.5f); assertEquals(150f, c.cy, 1.5f); assertEquals(50f, c.r, 1.5f)
        val arcs = InkLines.arcs(listOf(ring.subList(0, 41), ring.subList(42, 81)))
        assertTrue(arcs.all { it != null })
        arcs.flatMap { it!! }.forEach { p -> assertEquals(c.r, hypot(p.x - c.cx, p.y - c.cy), 1.5f) }
        val straight = (0..30).map { InkPoint(10f + it * 10f, 20f + (it % 2) * 0.8f) }
        assertEquals(listOf(null), InkLines.arcs(listOf(straight)))
    }
}
