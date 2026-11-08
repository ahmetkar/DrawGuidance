package com.karapp.drawguidance.content.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** v17: resim vektörleştirme kenar çizgisi odaklı — fotoğrafta kenarlar, kalem çiziminde çizginin ortası. */
class V17EdgeTracerTest {

    private fun image(w: Int, h: Int, paint: (x: Int, y: Int) -> Int): IntArray = IntArray(w * h) { paint(it % w, it / w) }

    private val white = 0xFFFFFFFF.toInt()

    @Test fun filledDiscGivesOneClosedEdgeOnItsBorder() {
        val w = 200; val h = 200
        val px = image(w, h) { x, y -> if (hypot(x - 100f + 0.5f, y - 100f + 0.5f) < 50f) 0xFF203040.toInt() else white }
        val r = EdgeTracer.trace(px, w, h, EdgeOptions(detail = 0.5f))
        assertEquals(EdgeMode.EDGES, r.mode)
        val main = r.paths.maxBy { it.points.size }
        assertTrue("kapalı kenar", main.closed)
        main.points.forEach { p ->
            val d = hypot(p.x * w - 100f, p.y * h - 100f)
            assertTrue("kenar dairenin sınırında: $d", abs(d - 50f) < 3.5f)
        }
        // Vektör noktaları az, eğri yumuşak (her pikselde nokta yok)
        assertTrue("nokta sayısı: ${main.nodes.size}", main.nodes.size in 4..40)
        assertTrue("parçalar: ${r.paths.map { it.closed to it.points.size }}", r.paths.size <= 3)
    }

    @Test fun inkDrawingIsTracedAlongTheMiddleOfItsLines() {
        val w = 240; val h = 180
        // 3 piksel kalın dikdörtgen çerçeve ve bir çapraz çizgi
        val px = image(w, h) { x, y ->
            val onRect = (x in 40..200 && (abs(y - 40) <= 1 || abs(y - 140) <= 1)) || (y in 40..140 && (abs(x - 40) <= 1 || abs(x - 200) <= 1))
            val onDiag = x in 60..180 && abs((y - 60) - (x - 60) * 0.5f) <= 1.2f
            if (onRect || onDiag) 0xFF111111.toInt() else white
        }
        val r = EdgeTracer.trace(px, w, h, EdgeOptions(detail = 0.5f, minLength = 0.2f))
        assertEquals(EdgeMode.CENTERLINE, r.mode)
        // Çizgiler çift değil: toplam uzunluk çizimin uzunluğuna yakın (kenar izleme iki katı verirdi)
        val total = r.paths.sumOf { p -> (1 until p.points.size).sumOf { i -> hypot((p.points[i].x - p.points[i - 1].x) * w, (p.points[i].y - p.points[i - 1].y) * h).toDouble() } + if (p.closed) hypot((p.points.first().x - p.points.last().x) * w, (p.points.first().y - p.points.last().y) * h).toDouble() else 0.0 }
        val drawn = 2 * 160 + 2 * 100 + hypot(120f, 60f)
        assertTrue("toplam uzunluk $total / $drawn", total in drawn * 0.8..drawn * 1.25)
        // Noktalar çizginin ortasında
        r.paths.flatMap { it.points }.forEach { p ->
            val x = p.x * w; val y = p.y * h
            val dRect = minOf(
                if (x in 38f..202f) minOf(abs(y - 40.5f), abs(y - 140.5f)) else Float.MAX_VALUE,
                if (y in 38f..142f) minOf(abs(x - 40.5f), abs(x - 200.5f)) else Float.MAX_VALUE,
            )
            val dDiag = abs((y - 60.5f) - (x - 60.5f) * 0.5f) / 1.118f
            assertTrue("($x,$y) çizgide", minOf(dRect, dDiag) < 3.5f)
        }
    }

    @Test fun crossingLineDoesNotBreakTheCircle() {
        // Kalem çizimi: çember ve içinden geçen çapraz çizgi — çember kapalı kalır, çizgi tek parça
        val w = 240; val h = 240
        val px = image(w, h) { x, y ->
            val onCircle = abs(hypot(x - 120f + 0.5f, y - 120f + 0.5f) - 70f) <= 1.5f
            val onLine = x in 20..220 && abs((y - 220) + (x - 20)) <= 1
            if (onCircle || onLine) 0xFF111111.toInt() else white
        }
        val r = EdgeTracer.trace(px, w, h, EdgeOptions(detail = 0.5f, minLength = 0.2f, mode = EdgeMode.CENTERLINE))
        val closed = r.paths.filter { it.closed }
        assertTrue("kapalı çember: ${r.paths.map { it.closed to it.points.size }}", closed.any { p ->
            val xs = p.points.map { it.x * w }; val ys = p.points.map { it.y * h }
            xs.max() - xs.min() > 120f && ys.max() - ys.min() > 120f
        })
        fun len(p: EdgePath) = (1 until p.points.size).sumOf { i -> hypot((p.points[i].x - p.points[i - 1].x) * w, (p.points[i].y - p.points[i - 1].y) * h).toDouble() }
        assertTrue("çapraz çizgi tek parça", r.paths.filter { !it.closed }.any { len(it) > 200.0 * 1.41 * 0.85 })
    }

    @Test fun colouredLinesTakeTheImageColour() {
        val w = 160; val h = 160
        val px = image(w, h) { x, y -> if (x in 40..120 && y in 40..120) 0xFFD32F2F.toInt() else white }
        val r = EdgeTracer.trace(px, w, h, EdgeOptions(colored = true, mode = EdgeMode.EDGES))
        val c = r.paths.maxBy { it.points.size }.color
        val red = c.substring(1, 3).toInt(16); val green = c.substring(3, 5).toInt(16)
        assertTrue("kırmızımsı: $c", red > green * 2)
        // Kare: köşeler vektör noktası
        assertTrue(r.paths.maxBy { it.points.size }.closed)
    }
}
