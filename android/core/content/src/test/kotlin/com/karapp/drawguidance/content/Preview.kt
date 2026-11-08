package com.karapp.drawguidance.content

import com.karapp.drawguidance.content.library.LibCategory
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.LineStyle
import com.karapp.drawguidance.content.vector.VPath
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

/**
 * Testlerde kütüphane çizimlerinin önizlemesi (Java2D): uygulamadaki çizimle aynı kurallar — parçalar sırayla,
 * önce dolgu sonra kenar; kenar kalınlığı [inkWidth] × parça çarpanı; kesik/noktalı çizgi; yazı çapaları.
 */
object Preview {

    /** -PpreviewDir ile verilen klasör (yoksa önizleme yazılmaz). */
    val dir: File? get() = System.getProperty("previewDir")?.let { File(it).apply { mkdirs() } }

    fun color(hex: String?, fallback: Color): Color {
        if (hex == null) return fallback
        if (hex == "@ink") return Color(0x1E, 0x1E, 0x1E)
        val v = hex.removePrefix("#").toLong(16)
        return if (hex.length > 7) Color(v.toInt(), true) else Color(v.toInt())
    }

    /** [paths] birim kutudadır; ([x],[y],[w],[h]) piksel dikdörtgenine çizilir. */
    fun draw(
        g: java.awt.Graphics2D, paths: List<VPath>, x: Float, y: Float, w: Float, h: Float,
        inkWidth: Float = 2.2f, ink: Color = Color(0x1E, 0x1E, 0x1E), elementFill: String? = null,
    ) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val m = min(w, h)
        for (p in paths) {
            val alpha = p.alpha.coerceIn(0f, 1f)
            if (p.label != null) {
                if (p.points.isEmpty()) continue
                val size = max(6f, p.labelSize * m)
                g.font = Font(Font.SANS_SERIF, Font.PLAIN, size.toInt())
                g.color = withAlpha(color(p.stroke, ink), alpha)
                val pt = p.points[0]
                g.drawString(p.label, x + pt.x * w, y + pt.y * h + size * 0.82f)
                continue
            }
            if (p.points.size < 2) continue
            val shape = Path2D.Float()
            p.points.forEachIndexed { i, pt ->
                val px = x + pt.x * w; val py = y + pt.y * h
                if (i == 0) shape.moveTo(px, py) else shape.lineTo(px, py)
            }
            if (p.closed) shape.closePath()
            val fillHex = (p.fill ?: elementFill)?.takeIf { it != "@none" }
            if (fillHex != null && (p.closed || p.fill != null)) {
                g.color = withAlpha(shade(fillHex, elementFill, ink) ?: color(fillHex, ink), alpha)
                g.fill(shape)
            }
            if (p.width > 0f) {
                val sw = max(0.6f, inkWidth * p.width)
                val dash = when (p.line) {
                    LineStyle.SOLID -> null
                    LineStyle.DASHED -> floatArrayOf(sw * 3f + 4f, sw * 2f + 3f)
                    LineStyle.DOTTED -> floatArrayOf(0.1f, sw * 2.2f)
                }
                g.stroke = BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f)
                g.color = withAlpha(p.stroke?.let { shade(it, elementFill, ink) } ?: color(p.stroke, ink), alpha)
                g.draw(shape)
            }
        }
    }

    /** "@shade:0.72" tonu (uygulamadaki SurfaceTextures.tone ile aynı): dolgunun (yoksa kalemin) koyu/açık tonu. */
    private fun shade(hex: String, elementFill: String?, ink: Color): Color? {
        if (!hex.startsWith("@shade:")) return null
        val v = (hex.removePrefix("@shade:").toFloatOrNull() ?: 0.5f).coerceIn(0f, 1f)
        val base = elementFill?.takeIf { it.startsWith("#") }?.let { color(it, ink) } ?: ink
        fun ch(c: Int) = if (v <= 0.5f) (c * (0.25f + 1.5f * v)).toInt().coerceIn(0, 255)
        else (c + (255 - c) * ((v - 0.5f) * 1.5f)).toInt().coerceIn(0, 255)
        return Color(ch(base.red), ch(base.green), ch(base.blue))
    }

    private fun withAlpha(c: Color, a: Float) = if (a >= 0.999f) c else Color(c.red, c.green, c.blue, (c.alpha * a).toInt())

    /** Tek şeklin PNG'si. */
    fun shapeImage(shape: LibShape, size: Int = 360): BufferedImage {
        val w = if (shape.aspect >= 1f) size else (size * shape.aspect).toInt()
        val h = if (shape.aspect >= 1f) (size / shape.aspect).toInt() else size
        val img = BufferedImage(w + 24, h + 24, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, img.width, img.height)
        draw(g, shape.paths(size.toFloat()), 12f, 12f, w.toFloat(), h.toFloat())
        g.dispose()
        return img
    }

    /** Kategorinin tüm şekilleri tek sayfada, adlarıyla. */
    fun sheet(category: LibCategory, cell: Int = 220, columns: Int = 5): BufferedImage {
        val rows = (category.shapes.size + columns - 1) / columns
        val img = BufferedImage(columns * cell, rows * (cell + 26) + 40, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color(0xF4, 0xF5, 0xF8)
        g.fillRect(0, 0, img.width, img.height)
        g.color = Color(0x33, 0x33, 0x33)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 20)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.drawString("${category.title} (${category.shapes.size})", 12, 28)
        category.shapes.forEachIndexed { i, s ->
            val cx = (i % columns) * cell
            val cy = 40 + (i / columns) * (cell + 26)
            g.color = Color.WHITE
            g.fillRoundRect(cx + 6, cy + 6, cell - 12, cell - 12, 16, 16)
            val inner = cell - 36f
            val w = if (s.aspect >= 1f) inner else inner * s.aspect
            val h = if (s.aspect >= 1f) inner / s.aspect else inner
            draw(g, s.paths(inner), cx + (cell - w) / 2f, cy + (cell - h) / 2f, w, h, inkWidth = 1.6f)
            g.color = Color(0x44, 0x44, 0x44)
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
            g.drawString(s.title, cx + 10, cy + cell + 14)
        }
        g.dispose()
        return img
    }

    fun write(img: BufferedImage, name: String): File? {
        val d = dir ?: return null
        val f = File(d, "$name.png")
        ImageIO.write(img, "png", f)
        return f
    }
}
