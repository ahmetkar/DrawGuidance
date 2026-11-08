package com.karapp.drawguidance.content.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** v17 çizim düzeltme: elle (titrek) çizilen doğru, kırık çizgi, çember, elips, üçgen, dikdörtgen, kare ve çokgen tanınır. */
class V17InkShapesTest {

    private val rnd = java.util.Random(7)

    /** Köşeler arasından titrek çizgi (el çizimi gibi). */
    private fun hand(corners: List<Pair<Float, Float>>, jitter: Float = 1.2f, step: Float = 2f): List<InkPoint> {
        val out = ArrayList<InkPoint>()
        for (i in 0 until corners.size - 1) {
            val (ax, ay) = corners[i]; val (bx, by) = corners[i + 1]
            val n = maxOf(2, (Math.hypot((bx - ax).toDouble(), (by - ay).toDouble()) / step).toInt())
            for (k in 0 until n) {
                val t = k / n.toFloat()
                out += InkPoint(ax + (bx - ax) * t + (rnd.nextFloat() - 0.5f) * jitter * 2, ay + (by - ay) * t + (rnd.nextFloat() - 0.5f) * jitter * 2)
            }
        }
        out += InkPoint(corners.last().first, corners.last().second)
        return out
    }

    private fun ellipse(cx: Float, cy: Float, a: Float, b: Float, jitter: Float = 1.5f, overlap: Float = 0.05f): List<InkPoint> =
        (0..140).map { i ->
            val t = i / 140.0 * 2 * PI * (1 + overlap)
            InkPoint(cx + a * cos(t).toFloat() + (rnd.nextFloat() - 0.5f) * jitter * 2, cy + b * sin(t).toFloat() + (rnd.nextFloat() - 0.5f) * jitter * 2)
        }

    @Test fun linesAndPolylines() {
        val line = InkShapes.recognize(hand(listOf(10f to 100f, 300f to 104f)))
        assertEquals(InkShapeKind.LINE, line?.kind)
        // Neredeyse yatay doğru tam yatay olur
        assertTrue(abs(line!!.points.first().y - line.points.last().y) < 0.01f)
        val angle = InkShapes.recognize(hand(listOf(10f to 10f, 150f to 160f, 300f to 12f)))
        assertEquals(InkShapeKind.POLYLINE, angle?.kind)
        // Serbest eğri (dalga) tanınmaz
        val wave = (0..200).map { InkPoint(it * 2f, 100f + 40f * sin(it / 12f)) }
        assertNull(InkShapes.recognize(wave))
    }

    @Test fun circlesAndEllipses() {
        val c = InkShapes.recognize(ellipse(200f, 200f, 100f, 96f))
        assertEquals(InkShapeKind.CIRCLE, c?.kind)
        assertTrue(c!!.closed)
        val e = InkShapes.recognize(ellipse(200f, 200f, 160f, 70f))
        assertEquals(InkShapeKind.ELLIPSE, e?.kind)
        // Ideal elips ana eksenleri yatay/dikey (neredeyse yatay çizildi)
        val xs = e!!.points.map { it.x }; val ys = e.points.map { it.y }
        assertEquals(320f, xs.max() - xs.min(), 16f)
        assertEquals(140f, ys.max() - ys.min(), 12f)
    }

    @Test fun polygons() {
        val rect = InkShapes.recognize(hand(listOf(20f to 20f, 320f to 24f, 318f to 180f, 22f to 178f, 21f to 26f)))
        assertEquals(InkShapeKind.RECTANGLE, rect?.kind)
        val sq = InkShapes.recognize(hand(listOf(20f to 20f, 220f to 22f, 219f to 221f, 21f to 219f, 20f to 26f)))
        assertEquals(InkShapeKind.SQUARE, sq?.kind)
        val tri = InkShapes.recognize(hand(listOf(150f to 20f, 280f to 240f, 20f to 238f, 148f to 26f)))
        assertEquals(InkShapeKind.TRIANGLE, tri?.kind)
        val pent = (0..5).map { i -> val a = -PI / 2 + i * 2 * PI / 5; (200 + 120 * cos(a)).toFloat() to (200 + 120 * sin(a)).toFloat() }
        val p = InkShapes.recognize(hand(pent))
        assertNotNull(p)
        assertEquals(InkShapeKind.POLYGON, p!!.kind)
        // Kapalı şeklin son noktası ilk noktası
        assertEquals(p.points.first(), p.points.last())
    }
}
