package com.karapp.drawguidance.content.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** v23 arka plan desenleri: sayılar, kayıt biçimi, çizim ve karonun dikişsizliği. */
class PaperTest {

    /** Java2D yüzeyi (önizleme ve sınama için). */
    class J2D(val g: Graphics2D) : PatternSurface {
        private val stack = ArrayList<java.awt.geom.AffineTransform>()
        private val clips = ArrayList<java.awt.Shape?>()
        override fun save() { stack += g.transform; clips += g.clip }
        override fun restore() { g.transform = stack.removeAt(stack.size - 1); g.clip = clips.removeAt(clips.size - 1) }
        override fun translate(dx: Float, dy: Float) = g.translate(dx.toDouble(), dy.toDouble())
        override fun scale(s: Float) = g.scale(s.toDouble(), s.toDouble())
        override fun clipRect(l: Float, t: Float, r: Float, b: Float) = g.clip(Rectangle2D.Float(l, t, r - l, b - t))
        private fun stroke(w: Float, dash: Float) = if (dash > 0f) BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, floatArrayOf(dash, dash * 0.7f), 0f)
        else BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        private fun color(c: Int) = Color(c, true)
        override fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int, dash: Float) {
            g.color = color(color); g.stroke = stroke(width, dash); g.draw(Line2D.Float(x1, y1, x2, y2))
        }
        override fun rect(x: Float, y: Float, w: Float, h: Float, fill: Boolean, width: Float, color: Int, dash: Float) {
            g.color = color(color); val r = Rectangle2D.Float(x, y, w, h)
            if (fill) g.fill(r) else { g.stroke = stroke(width, dash); g.draw(r) }
        }
        override fun circle(cx: Float, cy: Float, r: Float, fill: Boolean, width: Float, color: Int, dash: Float) {
            g.color = color(color); val e = Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r)
            if (fill) g.fill(e) else { g.stroke = stroke(width, dash); g.draw(e) }
        }
        override fun poly(xy: FloatArray, closed: Boolean, fill: Boolean, width: Float, color: Int, dash: Float) {
            if (xy.size < 4) return
            val p = Path2D.Float(); p.moveTo(xy[0], xy[1])
            for (i in 1 until xy.size / 2) p.lineTo(xy[2 * i], xy[2 * i + 1])
            if (closed) p.closePath()
            g.color = color(color)
            if (fill) g.fill(p) else { g.stroke = stroke(width, dash); g.draw(p) }
        }
    }

    @Test
    fun catalogCountsAndUniqueIds() {
        assertEquals(30, PaperCatalog.note.size)
        assertEquals(30, PaperCatalog.art.size)
        assertEquals(10, PaperCatalog.box.size)
        assertEquals(PaperCatalog.all.size, PaperCatalog.all.map { it.id }.toSet().size)
        PaperCatalog.all.forEach { assertTrue(it.id, it.hasTile || it.page.isNotEmpty()) }
    }

    @Test
    fun codecRoundTripIsCompact() {
        var total = 0
        PaperCatalog.all.forEach { p ->
            val bytes = PaperCodec.encode(p)
            total += bytes.size
            val back = PaperCodec.decode(bytes)
            assertEquals(p.id, back.id); assertEquals(p.name, back.name); assertEquals(p.usage, back.usage)
            assertEquals(p.tileW, back.tileW, 0.07f); assertEquals(p.tileH, back.tileH, 0.07f)
            assertEquals(p.tile.size, back.tile.size); assertEquals(p.page.size, back.page.size)
            (p.tile + p.page).zip(back.tile + back.page).forEach { (a, b) ->
                assertEquals(a.type, b.type); assertEquals(a.fill, b.fill); assertEquals(a.slot, b.slot); assertEquals(a.centered, b.centered)
                a.pts.indices.forEach { assertEquals(a.pts[it], b.pts[it], 0.07f) }
            }
        }
        println("70 desen toplam $total bayt")
        assertTrue("$total", total < 40_000)
    }

    private fun render(p: PaperPattern, w: Int, h: Int, scale: Float): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE; g.fillRect(0, 0, w, h)
        PatternPainter.paint(p, J2D(g), 0f, 0f, w.toFloat(), h.toFloat(), PatternColors(p.color, 0xFFFFFFFF.toInt(), p.accent), scale)
        g.dispose()
        return img
    }

    @Test
    fun everyPatternDrawsSomething() {
        PaperCatalog.all.forEach { p ->
            val img = render(p, 240, 340, 0.5f)
            var ink = 0
            for (y in 0 until img.height) for (x in 0 until img.width) if (img.getRGB(x, y) != -1) ink++
            assertTrue("${p.id} boş ($ink)", ink > 150)
        }
    }

    @Test
    fun tileRepeatsSeamlessly() {
        // Karo resmi tekrarlanınca doğrudan çizimle aynı görünür
        PaperCatalog.all.filter { it.hasTile && it.tileW < 500f }.forEach { p ->
            val s = 1f
            val tw = (p.tileW * s).toInt(); val th = (p.tileH * s).toInt()
            if (tw < 8 || th < 8 || kotlin.math.abs(p.tileW * s - tw) > 0.01f || kotlin.math.abs(p.tileH * s - th) > 0.01f) return@forEach
            val tile = BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB)
            val g = tile.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color.WHITE; g.fillRect(0, 0, tw, th)
            PatternPainter.paintTile(p, J2D(g), PatternColors(p.color, -1, p.accent), s)
            g.dispose()
            val direct = BufferedImage(tw * 3, th * 3, BufferedImage.TYPE_INT_ARGB)
            val g2 = direct.createGraphics()
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = Color.WHITE; g2.fillRect(0, 0, tw * 3, th * 3)
            val noInset = PaperPattern(p.id, p.name, p.usage, p.tileW, p.tileH, p.tile, emptyList(), floatArrayOf(0f, 0f, 0f, 0f), p.color, p.accent)
            PatternPainter.paint(noInset, J2D(g2), 0f, 0f, tw * 3f, th * 3f, PatternColors(p.color, -1, p.accent), s)
            g2.dispose()
            var diff = 0L; var n = 0
            for (y in 0 until th) for (x in 0 until tw) {
                val a = tile.getRGB(x, y); val b = direct.getRGB(tw + x, th + y)
                diff += kotlin.math.abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + kotlin.math.abs((a and 0xFF) - (b and 0xFF)); n++
            }
            assertTrue("${p.id} fark ${diff / n.toFloat()}", diff / n.toFloat() < 6f)
        }
    }

    @Test
    fun imagesAreSeamlessAndSized() {
        assertEquals(8, PaperImages.of(PaperUsage.NOTE).size)
        assertEquals(8, PaperImages.of(PaperUsage.ART).size)
        assertEquals(10, PaperImages.of(PaperUsage.BOX).size)
        // Desen ve resim kimlikleri aynı tabloda: çakışmamalı
        val ids = PaperCatalog.all.map { it.id } + PaperImages.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val t0 = System.nanoTime()
        PaperImages.all.forEach { d ->
            val px = d.generate()
            assertEquals(d.id, d.w * d.h, px.size)
            if (d.opaque) assertTrue(d.id, px.all { it ushr 24 == 0xFF })
            fun diff(a: Int, b: Int) = kotlin.math.abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + kotlin.math.abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) +
                kotlin.math.abs((a and 0xFF) - (b and 0xFF)) + kotlin.math.abs((a ushr 24) - (b ushr 24))
            fun colDiff(x1: Int, x2: Int, y0: Int = 0, y1: Int = d.h) = (y0 until y1).sumOf { diff(px[it * d.w + x1], px[it * d.w + x2]) } / (y1 - y0).toFloat()
            fun rowDiff(y1: Int, y2: Int, x0: Int = 0, x1: Int = d.w) = (x0 until x1).sumOf { diff(px[y1 * d.w + it], px[y2 * d.w + it]) } / (x1 - x0).toFloat()
            val layout = d.layout
            when {
                layout.kind == ImageLayout.TILE -> {
                    // Kenardan kenara geçiş, içerideki komşu sütunlar kadar yumuşak
                    val wrap = colDiff(d.w - 1, 0); val inner = colDiff(d.w / 2, d.w / 2 + 1)
                    val wrapY = rowDiff(d.h - 1, 0); val innerY = rowDiff(d.h / 2, d.h / 2 + 1)
                    assertTrue("${d.id} yatay dikiş $wrap / $inner", wrap < inner * 2.5f + 3f)
                    assertTrue("${d.id} dikey dikiş $wrapY / $innerY", wrapY < innerY * 2.5f + 3f)
                }
                layout.kind == ImageLayout.NINE && layout.repeat -> {
                    // Tekrarlanan orta parça: başı ve sonu (bir sonraki kopyanın başı) aynı
                    val l = layout.insets[0]; val t = layout.insets[1]; val r = layout.insets[2]; val b = layout.insets[3]
                    val cols = colDiff(l, d.w - r, t, d.h - b); val rows = rowDiff(t, d.h - b, l, d.w - r)
                    assertTrue("${d.id} orta sütun $cols", cols < 6f)
                    assertTrue("${d.id} orta satır $rows", rows < 6f)
                    // Üst ve alt kenar parçaları da yatayda periyodik
                    assertTrue("${d.id} üst kenar", colDiff(l, d.w - r, 0, t) < 8f)
                }
            }
            assertEquals(layout.encode(), ImageLayout.decode(layout.encode()).encode())
        }
        println("26 resim ${(System.nanoTime() - t0) / 1_000_000} ms")
    }

    @Test
    fun imagePreviewSheet() {
        val dir = System.getProperty("previewDir")?.let { File(it).apply { mkdirs() } } ?: return
        for (usage in PaperUsage.entries) {
            val list = PaperImages.of(usage)
            val cell = 260
            val cols = 5
            val rows = (list.size + cols - 1) / cols
            val sheet = BufferedImage(cols * (cell + 8), rows * (cell + 24), BufferedImage.TYPE_INT_RGB)
            val g = sheet.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.color = Color(0xB8C2CC); g.fillRect(0, 0, sheet.width, sheet.height)
            list.forEachIndexed { i, d ->
                val img = BufferedImage(d.w, d.h, BufferedImage.TYPE_INT_ARGB)
                img.setRGB(0, 0, d.w, d.h, d.generate(), 0, d.w)
                val x = (i % cols) * (cell + 8); val y = (i / cols) * (cell + 24) + 20
                val k = minOf(cell / d.w.toFloat(), cell / d.h.toFloat())
                g.drawImage(img, x, y, (d.w * k).toInt(), (d.h * k).toInt(), null)
                g.color = Color.BLACK; g.drawString(d.name, x + 2, y - 5)
            }
            g.dispose()
            ImageIO.write(sheet, "png", File(dir, "v23-images-${usage.name.lowercase()}.png"))
        }
    }

    @Test
    fun previewSheet() {
        val dir = System.getProperty("previewDir")?.let { File(it).apply { mkdirs() } } ?: return
        for ((name, list) in listOf("note" to PaperCatalog.note, "art" to PaperCatalog.art, "box" to PaperCatalog.box)) {
            val cols = 6; val cw = 200; val ch = if (name == "box") 140 else 280
            val rows = (list.size + cols - 1) / cols
            val sheet = BufferedImage(cols * (cw + 8), rows * (ch + 24), BufferedImage.TYPE_INT_RGB)
            val g = sheet.createGraphics()
            g.color = Color(0xDDDDDD); g.fillRect(0, 0, sheet.width, sheet.height)
            list.forEachIndexed { i, p ->
                val img = render(p, cw, ch, if (name == "note") 0.32f else 0.6f)
                val x = (i % cols) * (cw + 8); val y = (i / cols) * (ch + 24)
                g.drawImage(img, x, y + 20, null)
                g.color = Color.BLACK; g.drawString(p.name, x + 2, y + 15)
            }
            g.dispose()
            ImageIO.write(sheet, "png", File(dir, "v23-paper-$name.png"))
        }
    }
}
