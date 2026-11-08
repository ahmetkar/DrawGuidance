package com.karapp.drawguidance.content.trace

import com.karapp.drawguidance.content.Preview
import com.karapp.drawguidance.content.trace.TraceFixtures.agreement
import com.karapp.drawguidance.content.trace.TraceFixtures.pixels
import com.karapp.drawguidance.content.trace.TraceFixtures.pointCount
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import org.junit.Assert.assertTrue
import org.junit.Test

/** Önizleme sayfaları (kaynak | vektör; ayrıntı 0.2/0.5/0.9 × renk 4/8/16) ve süre ölçümü. */
class TracePreviewTest {

    private val scenes = listOf(
        "flat" to TraceFixtures.flatShapes(),
        "gradient" to TraceFixtures.gradientScene(),
        "photo" to TraceFixtures.photoLike(),
        "ring-alpha" to TraceFixtures.ringWithCore(360, 270, null),
    )

    @Test
    fun previewSheets() {
        if (Preview.dir == null) return
        val details = listOf(0.2f, 0.5f, 0.9f)
        val colors = listOf(4, 8, 16)
        for ((name, img) in scenes) {
            val cw = 360; val ch = img.height * cw / img.width
            val sheet = BufferedImage(cw * 4 + 50, (ch + 22) * 3 + 10, BufferedImage.TYPE_INT_ARGB)
            val g = sheet.createGraphics()
            g.color = Color(0xE9ECEF); g.fillRect(0, 0, sheet.width, sheet.height)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            details.forEachIndexed { row, detail ->
                val y = 5 + row * (ch + 22)
                drawChecker(g, 5, y, cw, ch)
                g.drawImage(img, 5, y, cw, ch, null)
                g.color = Color.DARK_GRAY; g.drawString("kaynak · ayrıntı $detail", 8, y + ch + 15)
                colors.forEachIndexed { col, c ->
                    val x = 5 + (col + 1) * (cw + 12)
                    val t0 = System.nanoTime()
                    val r = Vectorizer.trace(pixels(img), img.width, img.height, TraceOptions(colors = c, detail = detail))
                    val ms = (System.nanoTime() - t0) / 1_000_000
                    drawChecker(g, x, y, cw, ch)
                    Preview.draw(g, r.paths, x.toFloat(), y.toFloat(), cw.toFloat(), ch.toFloat())
                    g.color = Color.DARK_GRAY
                    g.drawString("d=$detail c=$c · ${r.paths.size} parça · ${pointCount(r)} nokta · %.1f%% · ${ms}ms"
                        .format(agreement(img, r) * 100), x + 3, y + ch + 15)
                }
            }
            g.dispose()
            Preview.write(sheet, "trace-$name")
        }
        // Yakın plan: kaynak ve vektör 2x büyütülmüş (eğri kalitesi)
        for ((name, img) in scenes.take(3)) {
            val r = Vectorizer.trace(pixels(img), img.width, img.height)
            val z = 2
            val out = BufferedImage(img.width * z * 2 + 10, img.height * z, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.color = Color.WHITE; g.fillRect(0, 0, out.width, out.height)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g.drawImage(img, 0, 0, img.width * z, img.height * z, null)
            Preview.draw(g, r.paths, img.width * z + 10f, 0f, img.width * z.toFloat(), img.height * z.toFloat())
            g.dispose()
            Preview.write(out, "trace-zoom-$name")
            val o = Vectorizer.trace(pixels(img), img.width, img.height, TraceOptions(outline = true, smooth = false))
            val out2 = BufferedImage(img.width * z, img.height * z, BufferedImage.TYPE_INT_ARGB)
            val g2 = out2.createGraphics()
            g2.color = Color.WHITE; g2.fillRect(0, 0, out2.width, out2.height)
            Preview.draw(g2, o.paths, 0f, 0f, img.width * z.toFloat(), img.height * z.toFloat())
            g2.dispose()
            Preview.write(out2, "trace-outline-poly-$name")
        }
    }

    private fun drawChecker(g: java.awt.Graphics2D, x: Int, y: Int, w: Int, h: Int) {
        val s = 10
        for (yy in 0 until h step s) for (xx in 0 until w step s) {
            g.color = if (((xx + yy) / s) % 2 == 0) Color(0xFFFFFF) else Color(0xDDDDDD)
            g.fillRect(x + xx, y + yy, minOf(s, w - xx), minOf(s, h - yy))
        }
    }

    @Test
    fun timings() {
        val photo = TraceFixtures.photoLike()
        val flat = TraceFixtures.flatShapes()
        fun median(img: BufferedImage, o: TraceOptions): Long {
            val px = pixels(img)
            repeat(4) { Vectorizer.trace(px, img.width, img.height, o) }
            val times = List(7) {
                val t0 = System.nanoTime()
                Vectorizer.trace(px, img.width, img.height, o)
                (System.nanoTime() - t0) / 1_000_000
            }.sorted()
            return times[times.size / 2]
        }
        val cold = run {
            val t0 = System.nanoTime(); Vectorizer.trace(pixels(photo), 480, 360); (System.nanoTime() - t0) / 1_000_000
        }
        val photoMs = median(photo, TraceOptions(colors = 8))
        val flatMs = median(flat, TraceOptions(colors = 8))
        val photo16 = median(photo, TraceOptions(colors = 16, detail = 0.9f))
        val big = TraceFixtures.photoLike(1920, 1440)
        val bigMs = median(big, TraceOptions())
        println("süre: foto 480x360 8 renk ${photoMs}ms (ilk çağrı ${cold}ms), düz ${flatMs}ms, " +
            "foto 16 renk d0.9 ${photo16}ms, 1920x1440 kaynak ${bigMs}ms")
        assertTrue("foto $photoMs ms", photoMs < 700)
        assertTrue("düz $flatMs ms", flatMs < 150)
    }
}
