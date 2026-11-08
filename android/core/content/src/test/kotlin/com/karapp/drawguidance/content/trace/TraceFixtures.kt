package com.karapp.drawguidance.content.trace

import com.karapp.drawguidance.content.Preview
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Vektörleştirme testleri için Java2D ile çizilen yapay görüntüler ve karşılaştırma yardımcıları. */
object TraceFixtures {

    fun pixels(img: BufferedImage): IntArray = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)

    private fun canvas(w: Int, h: Int, bg: Color?, block: Graphics2D.() -> Unit): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        if (bg != null) { g.color = bg; g.fillRect(0, 0, w, h) }
        g.block()
        g.dispose()
        return img
    }

    /** Düz renkli şekiller: daire, delikli dikdörtgen (çerçeve), halka, yıldız ve harf benzeri kalın çizgiler. */
    fun flatShapes(w: Int = 480, h: Int = 360): BufferedImage = canvas(w, h, Color(0xFAF7F0)) {
        val s = w / 480f
        color = Color(0xE63946); fill(Ellipse2D.Float(40 * s, 40 * s, 130 * s, 130 * s))
        // Delikli dikdörtgen (içi arka plan)
        color = Color(0x1D3557)
        val frame = Path2D.Float(Path2D.WIND_EVEN_ODD)
        frame.append(java.awt.Rectangle((200 * s).toInt(), (40 * s).toInt(), (120 * s).toInt(), (100 * s).toInt()), false)
        frame.append(java.awt.Rectangle((225 * s).toInt(), (65 * s).toInt(), (70 * s).toInt(), (50 * s).toInt()), false)
        fill(frame)
        // Halka + ortasında başka renk disk
        color = Color(0x2A9D8F); fill(ring(400 * s, 100 * s, 60 * s, 36 * s))
        color = Color(0xF4A261); fill(Ellipse2D.Float((400 - 18) * s, (100 - 18) * s, 36 * s, 36 * s))
        // Yıldız
        color = Color(0xE9C46A); fill(star(110 * s, 270 * s, 70 * s, 30 * s, 5))
        // Harf benzeri kalın çizgiler: "T", "L", "O", "S"
        color = Color(0x222222)
        stroke = BasicStroke(12 * s, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
        drawLine((210 * s).toInt(), (200 * s).toInt(), (270 * s).toInt(), (200 * s).toInt())
        drawLine((240 * s).toInt(), (200 * s).toInt(), (240 * s).toInt(), (310 * s).toInt())
        drawLine((290 * s).toInt(), (200 * s).toInt(), (290 * s).toInt(), (310 * s).toInt())
        drawLine((290 * s).toInt(), (304 * s).toInt(), (340 * s).toInt(), (304 * s).toInt())
        draw(Ellipse2D.Float(355 * s, 200 * s, 50 * s, 110 * s))
        val sPath = Path2D.Float()
        sPath.moveTo(460 * s, 205 * s); sPath.curveTo(410 * s, 190 * s, 410 * s, 250 * s, 440 * s, 255 * s)
        sPath.curveTo(470 * s, 262 * s, 470 * s, 320 * s, 420 * s, 305 * s)
        draw(sPath)
    }

    fun ring(cx: Float, cy: Float, r: Float, inner: Float): Path2D.Float {
        val p = Path2D.Float(Path2D.WIND_EVEN_ODD)
        p.append(Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r), false)
        p.append(Ellipse2D.Float(cx - inner, cy - inner, 2 * inner, 2 * inner), false)
        return p
    }

    fun star(cx: Float, cy: Float, outer: Float, inner: Float, points: Int): Path2D.Float {
        val p = Path2D.Float()
        for (i in 0 until points * 2) {
            val a = -PI / 2 + PI * i / points
            val r = if (i % 2 == 0) outer else inner
            val x = cx + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.closePath()
        return p
    }

    /** Halka: [bg] arka plan (null = saydam), ortasında boşluk ve küçük bir disk. */
    fun ringWithCore(w: Int, h: Int, bg: Color?): BufferedImage = canvas(w, h, bg) {
        val cx = w / 2f; val cy = h / 2f; val r = minOf(w, h) * 0.42f
        color = Color(0x3A0CA3); fill(ring(cx, cy, r, r * 0.62f))
        color = Color(0xF72585); fill(Ellipse2D.Float(cx - r * 0.22f, cy - r * 0.22f, r * 0.44f, r * 0.44f))
    }

    /** Yumuşak geçişli gökyüzü + düz şekiller. */
    fun gradientScene(w: Int = 480, h: Int = 360): BufferedImage = canvas(w, h, null) {
        paint = GradientPaint(0f, 0f, Color(0x1B3A6B), 0f, h.toFloat(), Color(0xF6BD60))
        fillRect(0, 0, w, h)
        color = Color(0xFFF3B0); fill(Ellipse2D.Float(w * 0.65f, h * 0.15f, w * 0.18f, w * 0.18f))
        color = Color(0x2D6A4F)
        val hill = Path2D.Float()
        hill.moveTo(0f, h * 0.75f); hill.curveTo(w * 0.3f, h * 0.55f, w * 0.6f, h * 0.9f, w.toFloat(), h * 0.68f)
        hill.lineTo(w.toFloat(), h.toFloat()); hill.lineTo(0f, h.toFloat()); hill.closePath()
        fill(hill)
        color = Color(0x6F1D1B); fillRect((w * 0.2f).toInt(), (h * 0.55f).toInt(), (w * 0.14f).toInt(), (h * 0.22f).toInt())
    }

    /** Fotoğraf benzeri: gök geçişi, güneş parlaması, dokulu tepeler, ev, ağaçlar ve piksel gürültüsü. */
    fun photoLike(w: Int = 480, h: Int = 360, seed: Long = 7): BufferedImage {
        val rnd = Random(seed)
        val img = canvas(w, h, null) {
            paint = GradientPaint(0f, 0f, Color(0x4A7FB5), 0f, h * 0.7f, Color(0xDDE8F0))
            fillRect(0, 0, w, h)
            for (i in 12 downTo 1) {
                val r = w * 0.03f * i
                color = Color(255, 240, 200, 18)
                fill(Ellipse2D.Float(w * 0.75f - r, h * 0.2f - r, 2 * r, 2 * r))
            }
            color = Color(0xFFF6D8); fill(Ellipse2D.Float(w * 0.72f, h * 0.16f, w * 0.06f, w * 0.06f))
            for (layer in 0 until 3) {
                val base = h * (0.55f + layer * 0.12f)
                val p = Path2D.Float()
                p.moveTo(0f, base)
                var x = 0f
                while (x <= w) { p.lineTo(x, base - 30f * sin(x / w * 6 + layer * 2.0).toFloat() - rnd.nextFloat() * 8); x += 6f }
                p.lineTo(w.toFloat(), h.toFloat()); p.lineTo(0f, h.toFloat()); p.closePath()
                paint = GradientPaint(0f, base - 40, Color(0x6A994E).darker(layer), 0f, h.toFloat(), Color(0x386641).darker(layer))
                fill(p)
            }
            // Ev
            color = Color(0xBC4749); fillRect((w * 0.12f).toInt(), (h * 0.58f).toInt(), (w * 0.16f).toInt(), (h * 0.16f).toInt())
            color = Color(0x5E2B1F)
            fill(Path2D.Float().apply {
                moveTo(w * 0.1f, h * 0.59f); lineTo(w * 0.2f, h * 0.48f); lineTo(w * 0.3f, h * 0.59f); closePath()
            })
            color = Color(0xF2E8CF); fillRect((w * 0.17f).toInt(), (h * 0.63f).toInt(), (w * 0.04f).toInt(), (h * 0.05f).toInt())
            // Ağaçlar: çok sayıda küçük yaprak lekesi
            repeat(5) { t ->
                val tx = w * (0.45f + t * 0.1f); val ty = h * (0.5f + (t % 2) * 0.06f)
                color = Color(0x3E2723); fillRect((tx - 3).toInt(), ty.toInt(), 6, (h * 0.18f).toInt())
                repeat(60) {
                    val a = rnd.nextDouble() * 2 * PI; val d = rnd.nextFloat() * w * 0.05f
                    val g = 90 + rnd.nextInt(80)
                    color = Color(30 + rnd.nextInt(30), g, 40 + rnd.nextInt(30))
                    val s = 4 + rnd.nextFloat() * 8
                    fill(Ellipse2D.Float(tx + d * cos(a).toFloat() - s / 2, ty + d * sin(a).toFloat() - s / 2 - w * 0.03f, s, s))
                }
            }
        }
        // Gürültü
        for (y in 0 until h) for (x in 0 until w) {
            val c = img.getRGB(x, y)
            val n = (rnd.nextGaussian() * 7).toInt()
            val r = ((c shr 16) and 255) + n; val g = ((c shr 8) and 255) + n; val b = (c and 255) + n
            img.setRGB(x, y, (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255))
        }
        return img
    }

    private fun Color.darker(times: Int): Color { var c = this; repeat(times) { c = Color((c.red * 0.85).toInt(), (c.green * 0.85).toInt(), (c.blue * 0.85).toInt()) }; return c }

    /** Rastgele renkli pikseller (sınır testleri için en kötü durum). */
    fun noise(w: Int, h: Int, seed: Long = 3): BufferedImage {
        val rnd = Random(seed)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h) for (x in 0 until w) img.setRGB(x, y, (0xFF shl 24) or rnd.nextInt(0x1000000))
        return img
    }

    /** Sonucu [w]×[h] boyutunda (uygulamadaki gibi sırayla, kenar yumuşatmalı) çizer; [bg] null ise saydam zemin. */
    fun render(result: TraceResult, w: Int, h: Int, bg: Color? = null, inkWidth: Float = 2.2f): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        if (bg != null) { g.color = bg; g.fillRect(0, 0, w, h) }
        Preview.draw(g, result.paths, 0f, 0f, w.toFloat(), h.toFloat(), inkWidth = inkWidth)
        g.dispose()
        return img
    }

    fun parseHex(hex: String): Int = hex.removePrefix("#").toInt(16)

    private fun nearest(c: Int, palette: IntArray): Int {
        var best = 0; var bd = Int.MAX_VALUE
        for (i in palette.indices) {
            val p = palette[i]
            val dr = ((c shr 16) and 255) - ((p shr 16) and 255)
            val dg = ((c shr 8) and 255) - ((p shr 8) and 255)
            val db = (c and 255) - (p and 255)
            val d = dr * dr + dg * dg + db * db
            if (d < bd) { bd = d; best = i }
        }
        return best
    }

    /**
     * Piksel uyumu: kaynak her piksel en yakın palet rengine (nicemlenmiş kaynak), çizilen sonuç da en yakın palet
     * rengine eşlenir; aynı olanların oranı. Saydam (alfa < 128) pikseller yalnız saydamla eşleşir.
     */
    fun agreement(source: BufferedImage, result: TraceResult): Double {
        val w = source.width; val h = source.height
        val rendered = render(result, w, h)
        val palette = result.palette.map { parseHex(it) }.toIntArray()
        var same = 0
        for (y in 0 until h) for (x in 0 until w) {
            val s = source.getRGB(x, y); val r = rendered.getRGB(x, y)
            val st = (s ushr 24) < 128; val rt = (r ushr 24) < 128
            val ok = if (st || rt) st == rt else nearest(s, palette) == nearest(r, palette)
            if (ok) same++
        }
        return same.toDouble() / (w * h)
    }

    fun pointCount(result: TraceResult) = result.paths.sumOf { it.points.size }

    fun rgbDistance(a: Int, b: Int): Double {
        val dr = ((a shr 16) and 255) - ((b shr 16) and 255)
        val dg = ((a shr 8) and 255) - ((b shr 8) and 255)
        val db = (a and 255) - (b and 255)
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }
}
