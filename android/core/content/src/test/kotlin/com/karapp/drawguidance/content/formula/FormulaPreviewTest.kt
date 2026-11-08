package com.karapp.drawguidance.content.formula

import com.karapp.drawguidance.content.Preview
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.max

/**
 * -PpreviewDir verilirse: her kategori için `formula-<id>.png` (formül + başlık), ayrıntılı inceleme için büyük
 * boyutlu `formula-zoom-<id>-<n>.png` sayfaları ve zorlayıcı örneklerden `formula-samples.png`.
 */
class FormulaPreviewTest {

    private val m = Java2DGlyphMetrics()

    private class Item(val title: String, val source: String)

    private fun sheet(heading: String, items: List<Item>, size: Float, columns: Int, metrics: Java2DGlyphMetrics = m): BufferedImage {
        val layouts = items.map { Formula.layout(it.source, size, metrics) }
        val pad = 14
        val titleH = 18
        val colW = max(360, ceil(layouts.maxOf { it.width }).toInt() + 2 * pad + 8)
        val rows = (items.size + columns - 1) / columns
        val rowH = IntArray(rows) { r ->
            (0 until columns).mapNotNull { c -> layouts.getOrNull(r * columns + c) }
                .maxOf { ceil(it.height).toInt() } + titleH + 2 * pad + 6
        }
        val img = BufferedImage(colW * columns, rowH.sum() + 44, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0xF4, 0xF5, 0xF8)
        g.fillRect(0, 0, img.width, img.height)
        g.color = Color(0x33, 0x33, 0x33)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 20)
        g.drawString(heading, 12, 29)
        var y = 44
        for (r in 0 until rows) {
            for (c in 0 until columns) {
                val k = r * columns + c
                val l = layouts.getOrNull(k) ?: continue
                val x = c * colW
                g.color = Color.WHITE
                g.fillRoundRect(x + 5, y + 3, colW - 10, rowH[r] - 6, 14, 14)
                g.color = Color(0x55, 0x55, 0x55)
                g.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
                g.drawString(items[k].title, x + pad, y + titleH + 2)
                FormulaRenderer.draw(g, l, (x + pad + 4).toFloat(), y + titleH + pad + l.ascent, metrics)
            }
            y += rowH[r]
        }
        g.dispose()
        return img
    }

    @Test
    fun catalogSheets() {
        if (Preview.dir == null) return
        for (c in FormulaCatalog.categories) {
            val items = c.templates.map { Item("${it.title}  ·  ${it.id}", it.source) }
            Preview.write(sheet("${c.title} (${c.templates.size})", items, 26f, 2), "formula-${c.id}")
            items.chunked(6).forEachIndexed { n, part ->
                Preview.write(sheet("${c.title} ${n + 1}", part, 44f, 1), "formula-zoom-${c.id}-${n + 1}")
            }
        }
    }

    @Test
    fun sampleSheet() {
        if (Preview.dir == null) return
        val samples = listOf(
            "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}",
            "\\frac{\\frac{1}{x} + \\frac{1}{y}}{x + y} = \\frac{1}{xy}",
            "e^{i\\pi} + 1 = 0 \\qquad a_{n+1} = a_n^2 + x_i^{2k} \\quad f''(x) \\quad 30^\\circ",
            "\\sqrt{2} \\quad \\sqrt{x} \\quad \\sqrt{b} \\quad \\sqrt{\\frac{a}{b}} \\quad \\sqrt[3]{8} \\quad \\sqrt[n]{x^{n+1}}",
            "\\left( \\frac{a}{b} \\right) \\left[ \\frac{a}{b} \\right] \\left\\{ \\frac{a}{b} \\right\\} \\left| \\frac{a}{b} \\right| \\left\\langle \\frac{a}{b} \\right\\rangle (x^2) [x] \\{x\\} |x|",
            "\\left( \\sum_{k=1}^{n} \\frac{1}{k^2} \\right)^{2} \\le \\left\\lfloor \\frac{\\pi^2}{6} \\right\\rfloor",
            "\\int_0^1 x^2\\,dx \\quad \\iint_D f\\,dA \\quad \\oint_C \\vec{F}\\cdot d\\vec{r} \\quad \\textstyle\\int_a^b f(x)\\,dx",
            "\\vec{v} \\; \\vec{AB} \\; \\overline{AB} \\; \\hat{x} \\; \\bar{x} \\; \\dot{x} \\; \\ddot{x} \\; \\tilde{n} \\; \\widehat{ABC} \\; \\vec{F}_{net}",
            "\\det \\begin{bmatrix} 1 & 2 & 3 \\\\ 4 & 5 & 6 \\\\ 7 & 8 & 9 \\end{bmatrix} = 0, \\quad \\begin{vmatrix} a & b \\\\ c & d \\end{vmatrix}",
            "\\alpha\\beta\\gamma\\delta\\varepsilon\\zeta\\eta\\theta\\lambda\\mu\\pi\\rho\\sigma\\tau\\varphi\\chi\\psi\\omega \\; \\Gamma\\Delta\\Theta\\Lambda\\Sigma\\Phi\\Psi\\Omega",
            "a \\cdot b \\times c \\div d \\pm e \\mp f \\le g \\ge h \\ne i \\approx j \\equiv k \\sim l \\propto m",
            "x \\to 0 \\Rightarrow y \\Leftrightarrow z \\iff w, \\; x \\in A \\notin B \\subset C \\subseteq D \\cup E \\cap \\emptyset",
            "\\forall \\epsilon > 0 \\; \\exists \\delta: \\angle ABC = 90\\degree, \\; AB \\perp CD, \\; k \\parallel l, \\; \\partial_x \\nabla f, \\infty",
            "1 + 2 + \\cdots + n, \\quad x_1, x_2, \\ldots, x_n \\quad \\text{ise } f(x) = \\mathrm{d}x \\; \\mathbf{v} \\; \\mathbb{R}",
            "\\lim_{x \\to \\infty} \\left(1 + \\frac{1}{x}\\right)^x = e \\qquad \\max_{i} a_i \\qquad \\prod_{i=1}^{n} i = n!",
            "\\begin{cases} 2x + 3y = 7 \\\\ x - y = 1 \\end{cases} \\qquad |x| = \\begin{cases} x, & x \\ge 0 \\\\ -x, & x < 0 \\end{cases}",
            "a &= (x+1)^2 \\\\ &= x^2 + 2x + 1",
            "π r² ≤ √2 · α + ∑ x₁",
        )
        Preview.write(sheet("Örnekler (DejaVu Serif)", samples.map { Item(it.take(80), it) }, 34f, 1), "formula-samples")
        val lib = Java2DGlyphMetrics("Liberation Serif", "DejaVu Sans")
        val few = samples.take(9).map { Item("Liberation Serif · " + it.take(60), it) }
        Preview.write(sheet("Liberation Serif", few, 34f, 1, lib), "formula-samples-liberation")
        val est = Java2DGlyphMetrics(withInk = false)
        Preview.write(sheet("Tahmin tablosu (inkBounds yok)", few, 34f, 1, est), "formula-samples-estimates")
        // Yuvalara değer yazılmış hâller
        var q = FormulaCatalog.find("algebra.quadratic_formula")!!.source
        q = Formula.setSlot(Formula.setSlot(Formula.setSlot(q, "a", "2"), "b", "-3"), "c", "1")
        var eq = FormulaCatalog.find("algebra.quadratic")!!.source
        eq = Formula.setSlot(Formula.setSlot(Formula.setSlot(eq, "a", "2"), "b", "-3"), "c", "\\frac{1}{2}")
        var mat = FormulaCatalog.find("matrix.inverse")!!.source
        mat = Formula.setSlot(Formula.setSlot(Formula.setSlot(Formula.setSlot(mat, "a", "1"), "b", "-2"), "c", "3"), "d", "4")
        var f = FormulaCatalog.find("physics.position")!!.source
        f = Formula.setSlot(Formula.setSlot(Formula.setSlot(f, "v0", "5"), "t", "2"), "a", "-9,8")
        val filled = listOf(q, eq, mat, f).map { Item(Formula.plainText(it), it) }
        Preview.write(sheet("Yuvalara değer yazılmış", filled, 34f, 1), "formula-filled")
    }
}
