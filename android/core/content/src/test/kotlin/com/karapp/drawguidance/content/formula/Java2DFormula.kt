package com.karapp.drawguidance.content.formula

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.ceil

/**
 * Testler için Java2D [GlyphMetrics]: serif yazı tipi (varsayılan DejaVu Serif — Android'in Noto Serif'ine oran
 * olarak yakın), ITALIC için italik yüz; eksik glifler (⋯, ∅, ∮…) yedek yazı tipinden (DejaVu Sans) alınır —
 * Android'in otomatik yazı tipi geri dönüşünü taklit eder. [withInk] = false ise yalnız zorunlu üç ölçü verilir
 * (motorun tahmin tablolarını sınamak için).
 */
class Java2DGlyphMetrics(
    family: String = "DejaVu Serif",
    fallbackFamily: String = "DejaVu Sans",
    private val withInk: Boolean = true,
) : GlyphMetrics {
    private val frc = FontRenderContext(null, true, true)
    private val primary = GlyphStyle.values().associateWith { Font(family, awt(it), REF.toInt()) }
    private val fallback = GlyphStyle.values().associateWith { Font(fallbackFamily, awt(it), REF.toInt()) }
    private val advCache = HashMap<String, Float>()
    private val inkCache = HashMap<String, FloatArray>()
    private val runCache = HashMap<String, List<Pair<String, Font>>>()

    val familyName: String = primary.getValue(GlyphStyle.ROMAN).family

    private fun awt(s: GlyphStyle) = when (s) {
        GlyphStyle.ROMAN -> Font.PLAIN
        GlyphStyle.ITALIC -> Font.ITALIC
        GlyphStyle.BOLD -> Font.BOLD
        GlyphStyle.BOLD_ITALIC -> Font.BOLD or Font.ITALIC
    }

    /** Metni, her parçası tek yazı tipiyle çizilebilen parçalara böler. */
    fun runs(text: String, style: GlyphStyle): List<Pair<String, Font>> = runCache.getOrPut("${style.ordinal}$text") {
        val p = primary.getValue(style)
        val f = fallback.getValue(style)
        val out = ArrayList<Pair<String, Font>>()
        var i = 0
        val sb = StringBuilder()
        var cur: Font? = null
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val font = if (p.canDisplay(cp) || Character.getType(cp) == Character.SPACE_SEPARATOR.toInt()) p else f
            if (cur != null && font != cur) { out.add(sb.toString() to cur); sb.clear() }
            cur = font
            sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        if (cur != null) out.add(sb.toString() to cur)
        out
    }

    private fun refAdvance(text: String, style: GlyphStyle): Float = advCache.getOrPut("${style.ordinal}$text") {
        runs(text, style).sumOf { (s, f) -> f.getStringBounds(s, frc).width }.toFloat()
    }

    override fun advance(text: String, size: Float, style: GlyphStyle): Float = refAdvance(text, style) * size / REF

    override fun ascent(size: Float): Float = primary.getValue(GlyphStyle.ROMAN).getLineMetrics("Hg", frc).ascent * size / REF

    override fun descent(size: Float): Float = primary.getValue(GlyphStyle.ROMAN).getLineMetrics("Hg", frc).descent * size / REF

    override fun inkBounds(text: String, size: Float, style: GlyphStyle): FloatArray? {
        if (!withInk) return null
        val r = inkCache.getOrPut("${style.ordinal}$text") {
            var x = 0.0
            var u: Rectangle2D? = null
            for ((s, f) in runs(text, style)) {
                val b = f.createGlyphVector(frc, s).visualBounds
                if (b.width > 0 || b.height > 0) {
                    val moved = Rectangle2D.Double(b.x + x, b.y, b.width, b.height)
                    u = u?.createUnion(moved) ?: moved
                }
                x += f.getStringBounds(s, frc).width
            }
            val v = u ?: Rectangle2D.Double(0.0, 0.0, 0.0, 0.0)
            floatArrayOf(v.minX.toFloat(), v.minY.toFloat(), v.maxX.toFloat(), v.maxY.toFloat())
        }
        val k = size / REF
        return floatArrayOf(r[0] * k, r[1] * k, r[2] * k, r[3] * k)
    }

    fun drawText(g: Graphics2D, text: String, x: Float, y: Float, size: Float, style: GlyphStyle) {
        var cx = x
        for ((s, f) in runs(text, style)) {
            g.font = f.deriveFont(size)
            g.drawString(s, cx, y)
            cx += (f.getStringBounds(s, frc).width * size / REF).toFloat()
        }
    }

    companion object {
        const val REF = 100f
    }
}

/** [FormulaLayout] çizicisi: uygulamadaki Canvas çizimiyle aynı kurallar. */
object FormulaRenderer {
    val INK = Color(0x1E, 0x1E, 0x1E)
    val SLOT_INK = Color(0x1F, 0x4F, 0xB0)
    val SLOT_FILL = Color(0x1F, 0x4F, 0xB0, 28)

    fun draw(g: Graphics2D, l: FormulaLayout, x: Float, y: Float, m: Java2DGlyphMetrics, showSlots: Boolean = true) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        if (showSlots) {
            g.color = SLOT_FILL
            for (r in l.slots) {
                g.fill(RoundRectangle2D.Float(x + r.left, y + r.top, r.right - r.left, r.bottom - r.top, 6f, 6f))
            }
        }
        for (op in l.ops) {
            when (op) {
                is DrawOp.Glyph -> {
                    g.color = if (showSlots && op.slot != null) SLOT_INK else INK
                    m.drawText(g, op.text, x + op.x, y + op.y, op.size, op.style)
                }
                is DrawOp.Rule -> {
                    g.color = INK
                    g.stroke = BasicStroke(op.thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
                    g.draw(Line2D.Float(x + op.x1, y + op.y1, x + op.x2, y + op.y2))
                }
                is DrawOp.Stroke -> {
                    g.color = INK
                    val p = Path2D.Float()
                    for (k in 0 until op.points.size / 2) {
                        val px = x + op.points[k * 2]
                        val py = y + op.points[k * 2 + 1]
                        if (k == 0) p.moveTo(px, py) else p.lineTo(px, py)
                    }
                    if (op.closed) p.closePath()
                    if (op.filled) g.fill(p)
                    if (op.thickness > 0f) {
                        g.stroke = BasicStroke(op.thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                        g.draw(p)
                    }
                }
            }
        }
    }

    fun image(l: FormulaLayout, m: Java2DGlyphMetrics, pad: Int = 12, showSlots: Boolean = true): BufferedImage {
        val img = BufferedImage(ceil(l.width).toInt() + 2 * pad, ceil(l.height).toInt() + 2 * pad, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, img.width, img.height)
        draw(g, l, pad.toFloat(), pad + l.ascent, m, showSlots)
        g.dispose()
        return img
    }
}
