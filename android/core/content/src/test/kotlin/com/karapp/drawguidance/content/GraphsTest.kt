package com.karapp.drawguidance.content

import com.karapp.drawguidance.content.graph.CurvePresets
import com.karapp.drawguidance.content.graph.Expression
import com.karapp.drawguidance.content.graph.GraphKind
import com.karapp.drawguidance.content.graph.GraphParams
import com.karapp.drawguidance.content.graph.Graphs
import com.karapp.drawguidance.content.graph.Metrics
import com.karapp.drawguidance.content.vector.LineStyle
import com.karapp.drawguidance.content.vector.VPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Grafik üreticisi: her tür / her ön ayar her en-boy oranında geçerli, kutu içinde ve beklenen etiketlerle çizilir;
 * düzenleme yardımcıları çıktıyı değiştirir. -PpreviewDir verilirse önizleme sayfaları PNG olarak yazılır.
 */
class GraphsTest {

    init { System.setProperty("graphs.strict", "true") }

    private val aspects = listOf(0.6f, 1f, 1.7f)
    private val hex = Regex("^#[0-9A-Fa-f]{6}$")

    private fun tags(paths: List<VPath>) = paths.mapNotNull { it.tag }

    /** Geçerlilik: sonlu noktalar, geçerli renkler, geometri ve yazılar kutuda (≤ 0.05 taşma). */
    private fun validate(name: String, paths: List<VPath>, aspect: Float) {
        assertTrue("$name boş", paths.any { it.label == null && it.points.size >= 2 })
        val bw = max(aspect, 1f)
        val bh = max(1f, 1f / aspect)
        val lim = 0.05f
        paths.forEach { p ->
            p.points.forEach { pt -> if (!pt.x.isFinite() || !pt.y.isFinite()) fail("$name: sonlu olmayan nokta ${p.tag}") }
            listOfNotNull(p.fill, p.stroke).forEach { c -> assertTrue("$name: renk $c", c == "@ink" || hex.matches(c)) }
            if (p.label == null) {
                p.points.forEach { pt ->
                    if (pt.x < -lim || pt.x > 1 + lim || pt.y < -lim || pt.y > 1 + lim) fail("$name (aspect $aspect): nokta kutu dışında $pt ${p.tag}")
                }
            } else {
                assertTrue("$name: yazı boyu", p.labelSize > 0f && p.labelSize.isFinite())
                val pt = p.points.single()
                val w = Metrics.width(p.label!!, p.labelSize) / bw
                val h = p.labelSize / bh
                if (pt.x < -lim || pt.y < -lim || pt.x + w > 1 + lim || pt.y + h > 1 + lim) {
                    fail("$name (aspect $aspect): yazı kutu dışında '${p.label}' $pt w=$w h=$h")
                }
            }
        }
    }

    private fun assertTags(name: String, paths: List<VPath>, vararg prefixes: String) {
        val t = tags(paths)
        prefixes.forEach { pre -> assertTrue("$name: '$pre' etiketi yok (${t.distinct().take(30)})", t.any { it.startsWith(pre) }) }
    }

    private val kindSamples: List<Pair<GraphParams, Array<String>>> = listOf(
        GraphParams(GraphKind.AXES) to arrayOf("tick:x:", "tick:y:", "axis:x", "axis:y"),
        GraphParams(GraphKind.COORD_PLANE) to arrayOf("tick:x:", "tick:y:", "axis:x", "axis:y", "tick:x:0"),
        GraphParams(GraphKind.COORD_PLANE, formula = "y = x^2 - 2", values = listOf(2f, 3f, -1f, 2f)) to arrayOf("curve", "value:0", "vlabel:1", "formula"),
        GraphParams(GraphKind.NUMBER_LINE) to arrayOf("tick:x:1", "tick:x:-2"),
        GraphParams(GraphKind.UNIT_CIRCLE) to arrayOf("curve", "tick:x:1", "tick:y:-1", "angle:30", "axis:x"),
        GraphParams(GraphKind.AXES_3D) to arrayOf("tick:x:1", "tick:y:1", "tick:z:1", "axis:z"),
        GraphParams(GraphKind.AXES_3D, formula = "z = sin(x)*cos(y)") to arrayOf("curve", "formula", "axis:x"),
        GraphParams(GraphKind.FUNCTION, preset = "sinus") to arrayOf("curve", "tick:x:1", "tick:y:", "axis:x", "axis:y", "formula"),
        GraphParams(GraphKind.FUNCTION, formula = "x^3 - 3x") to arrayOf("curve", "formula", "key:"),
        GraphParams(GraphKind.BAR) to arrayOf("value:0", "vlabel:0", "tick:y:1", "axis:x", "axis:y"),
        GraphParams(GraphKind.LINE) to arrayOf("curve", "value:0", "vlabel:0", "tick:y:1"),
        GraphParams(GraphKind.PIE) to arrayOf("value:0", "vlabel:0"),
        GraphParams(GraphKind.SCATTER) to arrayOf("value:0", "tick:x:", "tick:y:"),
        GraphParams(GraphKind.HISTOGRAM) to arrayOf("value:0", "tick:x:1", "tick:y:1"),
    )

    @Test
    fun everyKindBuilds() {
        GraphKind.values().forEach { k -> assertTrue("$k örneği yok", kindSamples.any { it.first.kind == k }) }
        for ((p, expected) in kindSamples) for (a in aspects) {
            val paths = Graphs.build(p, a, 512f)
            validate("${p.kind} ${p.formula}", paths, a)
            assertTags("${p.kind} ${p.formula} @$a", paths, *expected)
        }
    }

    @Test
    fun everyPresetBuilds() {
        assertTrue(CurvePresets.all.size >= 14)
        assertEquals(CurvePresets.all.size, CurvePresets.all.map { it.id }.toSet().size)
        for (preset in CurvePresets.all) {
            assertNotNull("${preset.id} formülü", if (Expression.isImplicit(preset.formula)) Expression.parseImplicit(preset.formula) else Expression.parse(preset.formula))
            for (a in aspects) {
                val p = GraphParams(GraphKind.FUNCTION, preset = preset.id, repeat = preset.defaultRepeat)
                val paths = Graphs.build(p, a, 512f)
                validate(preset.id, paths, a)
                assertTags("${preset.id} @$a", paths, "curve", "formula", "tick:x:", "axis:x")
            }
        }
    }

    @Test
    fun circleIsRound() {
        for (a in aspects) {
            val paths = Graphs.build(GraphParams(GraphKind.FUNCTION, preset = "cember", repeat = 4), a, 512f)
            val pts = paths.filter { it.tag == "curve" }.flatMap { it.points }
            // birim kutudan fiziksele: x·aspect, y
            val xs = pts.map { it.x * a }
            val ys = pts.map { it.y }
            val w = xs.max() - xs.min()
            val h = ys.max() - ys.min()
            assertEquals("çember en/boy @$a", 1f, w / h, 0.03f)
        }
    }

    @Test
    fun tickLabelsRespectOverrides() {
        val base = GraphParams(GraphKind.COORD_PLANE)
        val p = Graphs.withTickLabel(base, "x:2", "A")
        val lab = Graphs.build(p, 1f).firstOrNull { it.tag == "tick:x:2" && it.label != null }
        assertEquals("A", lab?.label)
        assertEquals("A", Graphs.tickValue(p, "x:2"))
        assertEquals("2", Graphs.tickValue(base, "x:2"))
        val hidden = Graphs.withTickLabel(base, "y:1", "")
        assertNull(Graphs.build(hidden, 1f).firstOrNull { it.tag == "tick:y:1" && it.label != null })
        assertNotNull(Graphs.build(base, 1f).firstOrNull { it.tag == "tick:y:1" && it.label != null })

        val sine = GraphParams(GraphKind.FUNCTION, preset = "sinus")
        assertEquals("π/2", Graphs.tickValue(sine, "x:1"))
        assertEquals("π", Graphs.tickValue(sine, "x:2"))
        assertEquals("−3π/2", Graphs.tickValue(sine, "x:-3"))
        assertEquals("y = sin(x)", Graphs.tickValue(sine, "formula"))
        val renamed = Graphs.withTickLabel(sine, "formula", "f(x) = sin x")
        assertEquals("f(x) = sin x", Graphs.build(renamed, 1.5f).first { it.tag == "formula" && it.label != null }.label)

        val nl = Graphs.withTickLabel(GraphParams(GraphKind.NUMBER_LINE, ticks = 8), "x:1/4", "¼")
        assertEquals("¼", Graphs.build(nl, 1.6f).first { it.tag == "tick:x:1/4" && it.label != null }.label)
        assertEquals("−2", Graphs.tickValue(GraphParams(GraphKind.NUMBER_LINE), "x:-2"))
    }

    private fun tickTags(p: GraphParams, a: Float = 1f) = tags(Graphs.build(p, a)).filter { it.startsWith("tick:") }.toSet().size

    /** Eğrinin x eksenini kaç kez kestiği (eksen: "tick:x:1" çentiğinin orta noktası). */
    private fun zeroCrossings(paths: List<VPath>): Int {
        val tick = paths.first { it.tag == "tick:x:1" && it.label == null }
        val axisY = (tick.points[0].y + tick.points[1].y) / 2
        var n = 0
        paths.filter { it.tag == "curve" }.forEach { c ->
            var prev = 0
            c.points.forEach { pt ->
                val s = if (pt.y < axisY - 1e-4f) 1 else if (pt.y > axisY + 1e-4f) -1 else 0
                if (s != 0) { if (prev != 0 && s != prev) n++; prev = s }
            }
        }
        return n
    }

    @Test
    fun patternAndPointsChangeOutput() {
        val plane = GraphParams(GraphKind.COORD_PLANE)
        assertTrue(tickTags(Graphs.morePoints(plane)) > tickTags(plane))
        assertTrue(tickTags(Graphs.lessPoints(plane)) < tickTags(plane))
        assertEquals(12, Graphs.morePoints(plane.copy(ticks = 12)).ticks)
        assertEquals(1, Graphs.lessPoints(plane.copy(ticks = 1)).ticks)

        // v18: fonksiyonda desen eğrinin sıklığı (eksenler aynı kalır)
        val sine = GraphParams(GraphKind.FUNCTION, preset = "sinus", repeat = 2, freq = 2)
        val z2 = zeroCrossings(Graphs.build(sine, 1.7f))
        val z3 = zeroCrossings(Graphs.build(Graphs.morePattern(sine), 1.7f))
        val z1 = zeroCrossings(Graphs.build(Graphs.lessPattern(sine), 1.7f))
        assertTrue("sinüs kesişimleri $z1 < $z2 < $z3", z1 < z2 && z2 < z3)

        val nl = GraphParams(GraphKind.NUMBER_LINE)
        assertTrue(tickTags(Graphs.morePattern(nl), 1.7f) > tickTags(nl, 1.7f))
        assertTrue(tickTags(Graphs.morePoints(nl), 1.7f) >= tickTags(nl, 1.7f))

        val bar = GraphParams(GraphKind.BAR)
        fun bars(p: GraphParams) = tags(Graphs.build(p, 1.4f)).filter { it.startsWith("value:") }.toSet().size
        assertTrue(bars(Graphs.morePattern(bar)) > bars(bar))
        assertTrue(bars(Graphs.lessPattern(bar)) < bars(bar))
        val edited = Graphs.withValue(bar, 0, 3f)
        assertTrue(bars(Graphs.morePattern(edited)) > bars(edited))
        assertTrue(bars(Graphs.lessPattern(edited)) < bars(edited))

        val uc = GraphParams(GraphKind.UNIT_CIRCLE)
        fun angles(p: GraphParams) = tags(Graphs.build(p, 1f)).count { it.startsWith("angle:") }
        assertTrue(angles(Graphs.morePattern(uc)) > angles(uc))
        assertTrue(angles(Graphs.lessPoints(uc)) < angles(uc))

        val para = GraphParams(GraphKind.FUNCTION, preset = "parabol", repeat = 3)
        assertTrue(Graphs.build(Graphs.morePattern(para), 1.4f) != Graphs.build(para, 1.4f))
        assertTrue(tickTags(Graphs.morePoints(para), 1.4f) > tickTags(para, 1.4f))
        assertTrue(tickTags(Graphs.lessPoints(Graphs.lessPoints(para)), 1.4f) < tickTags(para, 1.4f))
    }

    @Test
    fun tangentIsSplitAtAsymptotes() {
        for (a in aspects) {
            val paths = Graphs.build(GraphParams(GraphKind.FUNCTION, preset = "tanjant", repeat = 3), a)
            val curves = paths.filter { it.tag == "curve" }
            val asym = paths.filter { it.tag == "asymptote" }
            assertTrue("tanjant parçaları ${curves.size}", curves.size >= 3)
            assertTrue("asimptotlar ${asym.size}", asym.size >= 2)
            asym.forEach { assertEquals(LineStyle.DASHED, it.line) }
            val xs = asym.map { it.points[0].x }
            for (c in curves) for (x in xs) {
                val left = c.points.any { it.x < x - 1e-3f }
                val right = c.points.any { it.x > x + 1e-3f }
                assertTrue("eğri asimptotu aşıyor (x=$x)", !(left && right))
            }
        }
        // Formülle girilen 1/(x-1) de kesilir
        val hyp = Graphs.build(GraphParams(GraphKind.FUNCTION, formula = "1/(x-1)"), 1.3f)
        assertTrue(hyp.count { it.tag == "curve" } >= 2)
        assertTrue(hyp.any { it.tag == "asymptote" })
    }

    @Test
    fun floorHasOpenAndClosedDots() {
        val paths = Graphs.build(GraphParams(GraphKind.FUNCTION, preset = "basamak", repeat = 3), 1.3f)
        val white = paths.count { it.tag == "key" && it.fill == "#FFFFFF" }
        val full = paths.count { it.tag == "key" && it.fill != "#FFFFFF" }
        assertTrue("boş $white dolu $full", white >= 4 && full >= 4)
        assertTrue(paths.count { it.tag == "curve" } >= 5)
    }

    private fun barHeight(paths: List<VPath>, i: Int): Float {
        val b = paths.first { it.tag == "value:$i" && it.label == null && it.closed }
        return b.points.maxOf { it.y } - b.points.minOf { it.y }
    }

    @Test
    fun withValueEditsBarHeight() {
        val bar = GraphParams(GraphKind.BAR)
        val before = Graphs.build(bar, 1.3f)
        val edited = Graphs.withValue(bar, 1, 30f)
        assertEquals(Graphs.values(bar).size, edited.values.size)
        assertEquals(30f, edited.values[1])
        val after = Graphs.build(edited, 1.3f)
        val r0 = barHeight(before, 1) / barHeight(before, 0)
        val r1 = barHeight(after, 1) / barHeight(after, 0)
        assertTrue("oran $r0 → $r1", r1 > r0 * 2)
        assertEquals("30", after.first { it.tag == "value:1" && it.label != null }.label)
        // Dizin taşarsa uzatılır; SCATTER'da y değeri
        assertEquals(8, Graphs.withValue(bar, 7, 2f).values.size)
        val sc = Graphs.withValue(GraphParams(GraphKind.SCATTER), 2, 9f)
        assertEquals(9f, sc.values[5])
        assertTrue(Graphs.itemCount(GraphParams(GraphKind.PIE)) >= 3)
    }

    @Test
    fun expressionParsing() {
        fun v(s: String, x: Double, y: Double = 0.0) = Expression.parse(s)!!.eval(x, y)
        assertEquals(7.0, v("2x + 1", 3.0), 1e-9)
        assertEquals(3 * sin(1.0), v("3sin(x)", 1.0), 1e-9)
        assertEquals(12.0, v("x(x+1)", 3.0), 1e-9)
        assertEquals(-9.0, v("-x^2", 3.0), 1e-9)
        assertEquals(0.25, v("2^-x", 2.0), 1e-9)
        assertEquals(9.0, v("x²", 3.0), 1e-9)
        assertEquals(2.0, v("√x", 4.0), 1e-9)
        assertEquals(3.0, v("|x|", -3.0), 1e-9)
        assertEquals(16.0, v("f(x) = x^2", 4.0), 1e-9)
        assertEquals(2 * PI * 2, v("2πx", 2.0), 1e-9)
        assertEquals(6.0, v("xy", 2.0, 3.0), 1e-9)
        assertEquals(sin(2.0), v("sin 2x", 1.0), 1e-9)
        assertEquals(sin(1.0) * kotlin.math.cos(1.0), v("sin x cos x", 1.0), 1e-9)
        assertEquals(-2.0, v("x^(1/3)", -8.0), 1e-9)
        assertEquals(1.5, v("1,5", 0.0), 1e-9)
        assertEquals(2.0, v("⌊x⌋", 2.7), 1e-9)
        assertEquals(-3.0, v("⟦x⟧", -2.5), 1e-9)
        assertEquals(sqrt(2.0) * 3, v("3·√2", 0.0), 1e-9)
        assertNull(Expression.parse("2 +"))
        assertNull(Expression.parse("foo(x)"))
        assertTrue(Expression.isImplicit("x^2 + y^2 = 9"))
        assertEquals(0.0, Expression.parseImplicit("x^2 + y^2 = 25")!!.eval(3.0, 4.0), 1e-9)
    }

    @Test
    fun formulaLabels() {
        fun fn(f: String) = Graphs.formulaLabel(GraphParams(GraphKind.FUNCTION, formula = f))
        assertEquals("y = sin(x)", Graphs.formulaLabel(GraphParams(GraphKind.FUNCTION, preset = "sinus")))
        assertEquals("y = x² − 3x", fn("x^2-3x"))
        assertEquals("y = 2x + 1", fn("2*x+1"))
        assertEquals("y = √x", fn("sqrt(x)"))
        assertEquals("y = |x − 1|", fn("abs(x-1)"))
        assertEquals("y = e^(−x/4)·sin(2x)", fn("e^(-x/4)*sin(2x)"))
        assertEquals("f(x) = 2ˣ", fn("f(x) = 2^x"))
        assertEquals("x² + y² = 9", fn("x^2+y^2=9"))
        assertEquals("y = 1,5x", fn("1,5x"))
        assertEquals("y = ⟦x⟧", fn("floor(x)"))
        assertEquals("z = x² + y²", Graphs.formulaLabel(GraphParams(GraphKind.AXES_3D, formula = "z = x^2+y^2")))
    }

    @Test
    fun userPeriodicFormulaUsesPiTicks() {
        val p = GraphParams(GraphKind.FUNCTION, formula = "y = 2cos(x)")
        assertEquals("π/2", Graphs.tickValue(p, "x:1", 1.5f))
    }

    // ---------------------------------------------------------------- önizlemeler

    private fun sheet(title: String, rows: List<Pair<String, List<Pair<GraphParams, Float>>>>, h: Int = 300): BufferedImage {
        val gap = 18
        val labelH = 26
        val rowWidths = rows.map { (_, cells) -> cells.sumOf { (h * it.second).toInt() + gap } + gap }
        val width = max(900, rowWidths.maxOrNull() ?: 900)
        val height = 50 + rows.size * (h + labelH + gap)
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0xF1, 0xF3, 0xF6)
        g.fillRect(0, 0, width, height)
        g.color = Color(0x33, 0x33, 0x33)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 20)
        g.drawString(title, 14, 30)
        var y = 50
        for ((label, cells) in rows) {
            g.color = Color(0x44, 0x44, 0x44)
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
            g.drawString(label, gap, y + 17)
            var x = gap
            for ((p, a) in cells) {
                val w = (h * a).toInt()
                g.color = Color.WHITE
                g.fillRect(x, y + labelH, w, h)
                g.color = Color(0xE0, 0xE0, 0xE0)
                g.stroke = BasicStroke(1f)
                g.drawRect(x, y + labelH, w, h)
                Preview.draw(g, Graphs.build(p, a, max(w, h).toFloat()), x.toFloat(), (y + labelH).toFloat(), w.toFloat(), h.toFloat(), inkWidth = 0.0062f * min(w, h))
                x += w + gap
            }
            y += h + labelH + gap
        }
        g.dispose()
        return img
    }

    @Test
    fun previewSheets() {
        if (Preview.dir == null) return
        val kinds = kindSamples.map { (p, _) -> "${p.kind} ${p.formula}".trim() to aspects.map { p to it } }
        Preview.write(sheet("Grafik türleri (en/boy 0,6 · 1 · 1,7)", kinds.take(7)), "graph-kinds-1")
        Preview.write(sheet("Grafik türleri (en/boy 0,6 · 1 · 1,7)", kinds.drop(7)), "graph-kinds-2")
        val presets = CurvePresets.all.map { pr ->
            val p = GraphParams(GraphKind.FUNCTION, preset = pr.id, repeat = pr.defaultRepeat)
            "${pr.id} — ${pr.title}" to listOf(p to 1.6f, p to 1f, p.copy(fillUnder = true) to 0.7f)
        }
        Preview.write(sheet("Hazır eğriler (1)", presets.take(7), 260), "graph-presets-1")
        Preview.write(sheet("Hazır eğriler (2)", presets.drop(7).take(7), 260), "graph-presets-2")
        Preview.write(sheet("Hazır eğriler (3)", presets.drop(14), 260), "graph-presets-3")
        val variants = listOf(
            "sinüs desen 1 / 3, nokta 7" to listOf(
                GraphParams(GraphKind.FUNCTION, preset = "sinus", repeat = 1) to 1.6f,
                GraphParams(GraphKind.FUNCTION, preset = "sinus", repeat = 3, ticks = 7) to 1.6f,
            ),
            "formül: 3sin(2x), x^3/4 - x, dolgu" to listOf(
                GraphParams(GraphKind.FUNCTION, formula = "3sin(2x)") to 1.6f,
                GraphParams(GraphKind.FUNCTION, formula = "x^3/4 - x", fillUnder = true, curveColor = "#E53935") to 1.2f,
            ),
            "düzlem: noktalar, sayılar kapalı, etiket değişikliği" to listOf(
                GraphParams(GraphKind.COORD_PLANE, values = listOf(3f, 2f, -2f, 4f, -3f, -1f), valueLabels = mapOf(0 to "A", 1 to "B", 2 to "C")) to 1.4f,
                GraphParams(GraphKind.AXES, showNumbers = false, formula = "x/2 + 1") to 1f,
                GraphParams(GraphKind.COORD_PLANE, tickLabels = mapOf("x:1" to "a", "x:2" to "2a", "y:1" to "")) to 1f,
            ),
            "sayı doğrusu (çeyrekler), birim çember (hepsi), 3B noktalar" to listOf(
                GraphParams(GraphKind.NUMBER_LINE, repeat = 3, ticks = 8, values = listOf(-1.5f, 2.25f), valueLabels = mapOf(0 to "A", 1 to "B")) to 1.8f,
                GraphParams(GraphKind.UNIT_CIRCLE, repeat = 4, ticks = 6) to 1f,
                GraphParams(GraphKind.AXES_3D, ticks = 4, values = listOf(2f, 3f, 4f), valueLabels = mapOf(0 to "A")) to 1f,
            ),
            "3B yüzey: eyer, paraboloit" to listOf(
                GraphParams(GraphKind.AXES_3D, formula = "z = x^2 - y^2") to 1.3f,
                GraphParams(GraphKind.AXES_3D, formula = "(x^2 + y^2)/2", curveColor = "#8E24AA") to 1f,
            ),
            "grafik: adlar, negatif değer, çizgi dolgu" to listOf(
                GraphParams(GraphKind.BAR, values = listOf(12f, 18f, 7f, 15f), valueLabels = mapOf(0 to "Elma", 1 to "Armut", 2 to "Kiraz", 3 to "Muz"), xLabel = "Meyveler", yLabel = "Öğrenci sayısı") to 1.5f,
                GraphParams(GraphKind.LINE, values = listOf(3f, -2f, 5f, 1f, 4f), fillUnder = true) to 1.3f,
                GraphParams(GraphKind.PIE, values = listOf(40f, 30f, 20f, 10f, 3f), valueLabels = mapOf(0 to "Futbol", 1 to "Basketbol", 2 to "Voleybol", 3 to "Yüzme", 4 to "Satranç")) to 1.5f,
            ),
        )
        Preview.write(sheet("Çeşitlemeler", variants, 280), "graph-variants")
    }
}
