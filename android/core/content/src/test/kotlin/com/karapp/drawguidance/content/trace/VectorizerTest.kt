package com.karapp.drawguidance.content.trace

import com.karapp.drawguidance.content.trace.TraceFixtures.agreement
import com.karapp.drawguidance.content.trace.TraceFixtures.parseHex
import com.karapp.drawguidance.content.trace.TraceFixtures.pixels
import com.karapp.drawguidance.content.trace.TraceFixtures.pointCount
import com.karapp.drawguidance.content.trace.TraceFixtures.render
import com.karapp.drawguidance.content.trace.TraceFixtures.rgbDistance
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VectorizerTest {

    private fun trace(img: BufferedImage, o: TraceOptions = TraceOptions(), cancel: () -> Boolean = { false }) =
        Vectorizer.trace(pixels(img), img.width, img.height, o, cancel)

    @Test
    fun flatShapesAreTracedFaithfully() {
        val img = TraceFixtures.flatShapes()
        for (detail in listOf(0.2f, 0.5f, 0.9f)) {
            val r = trace(img, TraceOptions(detail = detail))
            val a = agreement(img, r)
            println("flat detail=$detail: ${r.paths.size} parça, ${pointCount(r)} nokta, palet ${r.palette.size}, uyum %.4f".format(a))
            assertTrue("detail $detail uyum $a", a >= if (detail == 0.5f) 0.92 else 0.90)
        }
        // Düz görüntüde yalnız gerçek renkler kalır (kenar yumuşatma ara tonları katılır)
        val r = trace(img, TraceOptions(colors = 16))
        assertTrue("palet ${r.palette}", r.palette.size <= 9)
        assertTrue(agreement(img, r) >= 0.92)
    }

    @Test
    fun ringHoleShowsInnerColours() {
        val bg = Color(0xFFFFFF)
        val img = TraceFixtures.ringWithCore(300, 300, bg)
        val r = trace(img)
        val out = render(r, 300, 300)
        val cx = 150; val cy = 150; val rad = 300 * 0.42f
        fun at(x: Float, y: Float) = out.getRGB(x.toInt(), y.toInt())
        // Merkez: çekirdek rengi; halka ile çekirdek arası: arka plan; halkanın üstü: halka rengi
        assertTrue(rgbDistance(at(cx.toFloat(), cy.toFloat()), 0xF72585) < 30)
        assertTrue(rgbDistance(at(cx + rad * 0.4f, cy.toFloat()), 0xFFFFFF) < 30)
        assertTrue(rgbDistance(at(cx + rad * 0.8f, cy.toFloat()), 0x3A0CA3) < 30)
        assertTrue(rgbDistance(at(cx - rad * 0.8f, cy - 10f), 0x3A0CA3) < 30)
        assertTrue(agreement(img, r) >= 0.96)
        // Ressam sırası: parçalar alanı büyükten küçüğe
        assertEquals("#FFFFFF", r.paths.first().fill)
    }

    @Test
    fun transparentBackgroundAndHolesStayEmpty() {
        val img = TraceFixtures.ringWithCore(300, 300, null)
        val r = trace(img)
        val out = render(r, 300, 300)
        val rad = 300 * 0.42f
        fun alpha(x: Float, y: Float) = out.getRGB(x.toInt(), y.toInt()) ushr 24
        assertEquals(0, alpha(5f, 5f))                       // dış arka plan çizilmez
        assertEquals(0, alpha(150 + rad * 0.4f, 150f))       // halkanın deliği oyulmuş
        assertEquals(0, alpha(150f, 150 - rad * 0.45f))
        assertEquals(255, alpha(150f, 150f))                 // çekirdek
        assertTrue(rgbDistance(out.getRGB(150, 150), 0xF72585) < 30)
        assertEquals(255, alpha(150 + rad * 0.8f, 150f))     // halka
        assertEquals(2, r.palette.size)
        assertTrue(agreement(img, r) >= 0.97)

        // Saydamlık yok sayılırsa beyaz zemin olarak izlenir
        val opaque = trace(img, TraceOptions(ignoreTransparent = false))
        val o2 = render(opaque, 300, 300)
        assertEquals(255, o2.getRGB(5, 5) ushr 24)
        assertTrue(rgbDistance(o2.getRGB((150 + rad * 0.4f).toInt(), 150), 0xFFFFFF) < 20)
        assertTrue(opaque.palette.contains("#FFFFFF"))
    }

    @Test
    fun neighboursShareBordersWithoutGaps() {
        // Kenarsız, kenar yumuşatmasız çizimde magenta zemin hiçbir yerde görünmemeli: komşular ortak yay kullanır
        for ((img, smooth) in listOf(TraceFixtures.photoLike() to true, TraceFixtures.photoLike() to false,
            TraceFixtures.flatShapes() to true)) {
            val r = trace(img, TraceOptions(smooth = smooth))
            val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.color = Color.MAGENTA; g.fillRect(0, 0, img.width, img.height)
            for (p in r.paths) {
                val shape = java.awt.geom.Path2D.Float()
                p.points.forEachIndexed { i, pt ->
                    if (i == 0) shape.moveTo(pt.x * img.width, pt.y * img.height) else shape.lineTo(pt.x * img.width, pt.y * img.height)
                }
                shape.closePath()
                g.color = Color(parseHex(p.fill!!)); g.fill(shape)
            }
            g.dispose()
            var holes = 0
            for (y in 0 until img.height) for (x in 0 until img.width) if (out.getRGB(x, y) and 0xFFFFFF == 0xFF00FF) holes++
            println("boşluk (smooth=$smooth): $holes piksel")
            assertTrue("boşluk $holes", holes <= img.width * img.height / 2000)
        }
    }

    @Test
    fun outputIsWellFormed() {
        val img = TraceFixtures.gradientScene()
        val r = trace(img, TraceOptions(colors = 6))
        assertTrue(r.paths.isNotEmpty())
        assertTrue(r.palette.size in 2..6)
        assertEquals(480, r.width); assertEquals(360, r.height)
        for (p in r.paths) {
            assertTrue(p.closed)
            assertTrue(p.points.size >= 3)
            assertTrue(p.fill in r.palette)
            assertEquals(p.fill, p.stroke)
            assertEquals(0.25f, p.width)
            for (pt in p.points) assertTrue(pt.x in 0f..1f && pt.y in 0f..1f)
        }
        // Kenar çizgili seçenek: dolgunun koyu tonu, 0.35 kalınlık
        val o = trace(img, TraceOptions(colors = 6, outline = true))
        for (p in o.paths) {
            assertEquals(0.35f, p.width)
            assertNotEquals(p.fill, p.stroke)
            val f = parseHex(p.fill!!); val s = parseHex(p.stroke!!)
            assertTrue(((s shr 8) and 255) <= ((f shr 8) and 255))
        }
        // Köşeli (smooth=false) mod da doğru çalışır
        val poly = trace(TraceFixtures.flatShapes(), TraceOptions(smooth = false))
        assertTrue(agreement(TraceFixtures.flatShapes(), poly) >= 0.92)
    }

    @Test
    fun largeImagesAreDownscaled() {
        val img = TraceFixtures.flatShapes(1200, 900)
        val r = trace(img)
        assertEquals(480, r.width); assertEquals(360, r.height)
        val small = trace(img, TraceOptions(maxSide = 200))
        assertEquals(200, small.width); assertEquals(150, small.height)
        assertTrue(small.paths.isNotEmpty())
        // Küçültülmüş sonuç, aynı boyda çizilmiş kaynakla uyumlu
        assertTrue(agreement(TraceFixtures.flatShapes(480, 360), r) >= 0.92)
    }

    @Test
    fun limitsAreRespected() {
        val worst = listOf(
            TraceFixtures.noise(480, 480) to TraceOptions(colors = 24, detail = 1f, minRegion = 0f),
            TraceFixtures.photoLike() to TraceOptions(colors = 24, detail = 1f, minRegion = 0f),
            TraceFixtures.photoLike(960, 720) to TraceOptions(colors = 16, detail = 1f, maxSide = 960, minRegion = 0f),
        )
        for ((img, o) in worst) {
            val r = trace(img, o)
            println("sınır: ${img.width}x${img.height} ${o.colors} renk → ${r.paths.size} parça, ${pointCount(r)} nokta")
            assertTrue(r.paths.size <= Vectorizer.MAX_PARTS)
            assertTrue(pointCount(r) <= Vectorizer.MAX_POINTS)
            assertTrue(r.paths.isNotEmpty())
        }
    }

    @Test
    fun cancellationStopsEarly() {
        val img = TraceFixtures.photoLike()
        try {
            trace(img) { true }
            fail("iptal edilmedi")
        } catch (_: CancellationException) {
        }
        // İptal isteği işin ortasında da fark edilir
        var calls = 0
        trace(img) { calls++; false }
        assertTrue("iptal denetimi seyrek: $calls", calls >= 8)
        for (at in listOf(3, calls / 2, calls - 1)) {
            var k = 0
            try {
                trace(img) { ++k >= at }
                fail("iptal edilmedi ($at)")
            } catch (_: CancellationException) {
                assertEquals(at, k)
            }
        }
    }

    @Test
    fun outputIsDeterministic() {
        val img = TraceFixtures.photoLike()
        val a = trace(img, TraceOptions(colors = 10))
        val b = trace(img, TraceOptions(colors = 10))
        assertEquals(a, b)
        val c = trace(TraceFixtures.flatShapes(), TraceOptions(detail = 0.8f))
        val d = trace(TraceFixtures.flatShapes(), TraceOptions(detail = 0.8f))
        assertEquals(c, d)
    }

    @Test
    fun edgeCases() {
        // Tamamen saydam
        val empty = BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB)
        val e = trace(empty)
        assertTrue(e.paths.isEmpty()); assertTrue(e.palette.isEmpty())
        // Tek renk: tek dikdörtgen
        val flat = BufferedImage(50, 20, BufferedImage.TYPE_INT_ARGB).apply {
            val g = createGraphics(); g.color = Color(0x336699); g.fillRect(0, 0, 50, 20); g.dispose()
        }
        val f = trace(flat)
        assertEquals(1, f.paths.size)
        assertEquals(listOf("#336699"), f.palette)
        val xs = f.paths[0].points.map { it.x }; val ys = f.paths[0].points.map { it.y }
        assertEquals(0f, xs.min(), 1e-3f); assertEquals(1f, xs.max(), 1e-3f)
        assertEquals(0f, ys.min(), 1e-3f); assertEquals(1f, ys.max(), 1e-3f)
        // 1×1 ve ince görüntüler
        assertEquals(1, trace(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)).paths.size)
        assertFalse(trace(TraceFixtures.flatShapes(480, 4)).paths.isEmpty())
        // Aşırı ayarlar sınırlanır
        val r = trace(TraceFixtures.flatShapes(), TraceOptions(colors = 99, detail = 7f, maxSide = 2, minRegion = -1f))
        assertTrue(r.width <= 16 && r.paths.isNotEmpty())
    }
}
