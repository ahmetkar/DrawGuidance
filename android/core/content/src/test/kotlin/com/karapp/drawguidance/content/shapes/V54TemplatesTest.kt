package com.karapp.drawguidance.content.shapes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File

/** v54 çizim modları için eklenen şablonlar: sayı, birim kutu, kategori ve (isteğe bağlı) önizleme. */
class V54TemplatesTest {
    private val v54 get() = ShapeTemplates.all.filter { it.key in V54_KEYS }

    @Test
    fun manyNewDrawingTemplatesInsideUnitBox() {
        assertEquals(9, ShapeTemplates.VERSION)
        assertTrue("v54 şablon sayısı: ${v54.size}", v54.size >= 140)
        for (t in v54) {
            for (s in t.strokes) for (p in s) assertTrue("${t.key} kutu dışında: $p", p.x in -0.06f..1.06f && p.y in -0.06f..1.06f)
            assertTrue("${t.key} resim modunda", ModeCatalog.allows(ShapeMode.ART, t.key, t.category))
        }
        // çizgi roman modu teknoloji ve 3B cisim dışındakileri kapsar
        assertTrue(v54.count { ModeCatalog.allows(ShapeMode.COMIC, it.key, it.category) } >= 140)
    }

    @Test
    fun previewV54() {
        val out = System.getProperty("v54Preview") ?: return
        val list = v54
        val cols = 12; val cell = 130
        val rows = (list.size + cols - 1) / cols
        val img = BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE; g.fillRect(0, 0, img.width, img.height)
        list.forEachIndexed { i, t ->
            val ox = (i % cols) * cell + 12f; val oy = (i / cols) * cell + 6f
            g.color = Color(0x1E1E1E); g.stroke = BasicStroke(1.5f)
            for (s in t.strokes) {
                val path = Path2D.Float()
                s.forEachIndexed { k, p -> if (k == 0) path.moveTo(ox + p.x * 104f, oy + p.y * 104f) else path.lineTo(ox + p.x * 104f, oy + p.y * 104f) }
                g.draw(path)
            }
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 10); g.color = Color(0x2563EB)
            g.drawString(t.name.take(22), ox - 8f, oy + 120f)
        }
        javax.imageio.ImageIO.write(img, "png", File(out))
    }

    companion object {
        val V54_KEYS: Set<String> by lazy {
            val src = File("src/main/kotlin/com/karapp/drawguidance/content/shapes/TemplatesV54.kt").takeIf { it.exists() }?.readText()
            if (src != null) Regex("t\\(\"([a-z_.]+)\"").findAll(src).map { it.groupValues[1] }.toSet()
            else ShapeTemplates.all.map { it.key }.toSet()
        }
    }
}
