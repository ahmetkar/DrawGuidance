package com.karapp.drawguidance.ui.screens.canvasedit.tutorial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.content.ink.InkSynth
import com.karapp.drawguidance.content.shapes.ShapeSignatures
import com.karapp.drawguidance.content.shapes.ShapeTemplates
import com.karapp.drawguidance.content.shapes.TemplateFit
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.data.model.FormulaSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import com.karapp.drawguidance.ui.drawing.FormulaRenderer
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.TextRenderer
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * v32 eğitim çizimlerinin geometrisi: yumuşak eğriler (Catmull-Rom), elle çizilmiş gibi titrek çizgiler, şekil
 * veritabanındaki şablonların el çizimi halleri, yapay el yazısı ve hazır elemanlar (yazı, formül, diyagram, şekil).
 */
internal object TutorialArt {

    /** Noktalardan geçen yumuşak eğri (Catmull-Rom; uçlar sabit). */
    fun smooth(points: List<Offset>, samples: Int = 10): List<Offset> {
        if (points.size < 3) return points
        val out = ArrayList<Offset>()
        for (i in 0 until points.size - 1) {
            val p0 = points[(i - 1).coerceAtLeast(0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
            for (k in 0 until samples) {
                val t = k / samples.toFloat()
                val t2 = t * t; val t3 = t2 * t
                fun c(a: Float, b: Float, cc: Float, d: Float) =
                    0.5f * ((2 * b) + (-a + cc) * t + (2 * a - 5 * b + 4 * cc - d) * t2 + (-a + 3 * b - 3 * cc + d) * t3)
                out += Offset(c(p0.x, p1.x, p2.x, p3.x), c(p0.y, p1.y, p2.y, p3.y))
            }
        }
        out += points.last()
        return out
    }

    /** El titremesi: düşük frekanslı bükülme ve küçük gürültü ([amp] tuval birimi). */
    fun wobble(points: List<Offset>, amp: Float, seed: Long): List<Offset> {
        val rnd = java.util.Random(seed)
        val ph = rnd.nextFloat() * 6.28f; val f = 1.5f + rnd.nextFloat() * 2f
        var len = 0f
        return points.mapIndexed { i, p ->
            if (i > 0) len += (p - points[i - 1]).getDistance()
            val wv = amp * sin(f * len / (amp * 60f).coerceAtLeast(1f) + ph)
            Offset(p.x + wv + (rnd.nextFloat() - 0.5f) * amp * 0.35f, p.y + amp * cos(f * len / (amp * 55f).coerceAtLeast(1f) + ph) * 0.8f + (rnd.nextFloat() - 0.5f) * amp * 0.35f)
        }
    }

    /** Elle çizilmiş çember (biraz taşan uçla). */
    fun roughCircle(c: Offset, r: Float, seed: Long, overshoot: Float = 1.05f): List<Offset> {
        val rnd = java.util.Random(seed)
        val a0 = rnd.nextFloat() * 6.28f
        return (0..80).map { i ->
            val a = a0 + i / 80.0 * 2 * PI * overshoot
            val rr = r * (1f + (rnd.nextFloat() - 0.5f) * 0.05f + 0.03f * sin(i / 9f))
            Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat())
        }
    }

    /** Köşelerden geçen elle çizilmiş kırık çizgi. */
    fun roughPoly(corners: List<Offset>, seed: Long, closed: Boolean = true, amp: Float = 2.5f): List<Offset> {
        val rnd = java.util.Random(seed)
        val pts = if (closed) corners + corners.first() else corners
        val out = ArrayList<Offset>()
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            val n = 16
            for (k in 0 until n) {
                val t = k / n.toFloat()
                out += Offset(a.x + (b.x - a.x) * t + (rnd.nextFloat() - 0.5f) * amp, a.y + (b.y - a.y) * t + (rnd.nextFloat() - 0.5f) * amp)
            }
        }
        out += pts.last()
        return out
    }

    /** Şekil veritabanındaki şablonun ([key]) [center] etrafında [size] boyunda elle çizilmiş hali (çizgi sırası korunur). */
    fun handDrawnTemplate(key: String, center: Offset, size: Float, seed: Long): List<List<Offset>> {
        val t = ShapeTemplates.byKey(key) ?: return emptyList()
        val rnd = java.util.Random(seed)
        // v38 şekil önerisi birbirine yakın 4+ elemanda: az çizgili şablon parçalara bölünerek çizilir
        val base = ShapeSignatures.normalize(t.strokes).let {
            if (it.size >= com.karapp.drawguidance.content.shapes.SuggestRules.SHAPE_MIN) it
            else com.karapp.drawguidance.content.shapes.ShapeAugment.atLeast(it, com.karapp.drawguidance.content.shapes.SuggestRules.SHAPE_MIN, java.util.Random(seed))
        }
        return base.map { s ->
            val pts = TemplateFit.densify(s.map { P(center.x + (it.x - 0.5f) * size, center.y + (it.y - 0.5f) * size) }, (size / 90f).coerceAtLeast(2f))
            val ph = rnd.nextFloat() * 6.28f; val f = 1f + rnd.nextFloat() * 2f
            var len = 0f
            pts.mapIndexed { i, p ->
                if (i > 0) len += kotlin.math.hypot(p.x - pts[i - 1].x, p.y - pts[i - 1].y)
                val wv = size * 0.008f
                Offset(p.x + wv * sin(f * len / size * 6.28f + ph) + (rnd.nextFloat() - 0.5f), p.y + wv * cos(f * len / size * 6.28f + ph) + (rnd.nextFloat() - 0.5f))
            }
        }
    }

    /** Şablonun düzgün (kusursuz) hali — öneri gelmezse yedek. */
    fun cleanTemplate(key: String, center: Offset, size: Float): List<List<Offset>> {
        val t = ShapeTemplates.byKey(key) ?: return emptyList()
        return ShapeSignatures.normalize(t.strokes).map { s ->
            TemplateFit.densify(s.map { P(center.x + (it.x - 0.5f) * size, center.y + (it.y - 0.5f) * size) }, (size / 90f).coerceAtLeast(2f))
                .map { Offset(it.x, it.y) }
        }
    }

    /** [text] için düzgün yapay el yazısı; satır [box] içine (yüksekliğine göre) oturtulur. */
    fun handwriting(text: String, seed: Int, box: Rect): List<List<Offset>> {
        val opt = InkSynth.Options(slantDeg = 6f, rotDeg = 2f, minH = 60f, maxH = 61f, warp = 2.5f, delayMarks = 0f)
        val ink = InkSynth.sample(text, kotlin.random.Random(seed), opt)
        val all = ink.flatMap { it.points }
        if (all.isEmpty()) return emptyList()
        val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
        val k = minOf(box.height / (maxY - minY).coerceAtLeast(1f), box.width / (maxX - minX).coerceAtLeast(1f))
        return ink.map { s -> s.points.map { Offset(box.left + (it.x - minX) * k, box.top + (it.y - minY) * k) } }
    }

    // --- Hazır elemanlar ---

    private val q = ContentCodec::quantize

    fun text(
        content: String, left: Float, top: Float, size: Float, color: String, font: TextFont = TextFont.SANS,
        bold: Boolean = false, italic: Boolean = false, align: TextAlignKind = TextAlignKind.LEFT,
    ): Stroke {
        val spec = TextSpec(content, font = font, bold = bold, italic = italic, align = align)
        val points = TextRenderer.quadAt(spec, size, left, top).map { StrokePoint(q(it.x), q(it.y)) }
        return Stroke(
            id = UUID.randomUUID().toString(), points = points, colorHex = color, brushType = BrushType.TEXT,
            sizePx = size, opacity = 1f, order = 0, text = spec,
        )
    }

    /** Yazının ölçüsü (genişlik, yükseklik). */
    fun measure(content: String, size: Float, font: TextFont = TextFont.SANS, bold: Boolean = false): Pair<Float, Float> =
        TextRenderer.measure(TextSpec(content, font = font, bold = bold), size)

    fun formula(templateId: String, left: Float, top: Float, size: Float, color: String): Stroke? {
        val t = com.karapp.drawguidance.content.formula.FormulaCatalog.find(templateId) ?: return null
        val quad = FormulaRenderer.quadAt(t.source, size, left, top).map { StrokePoint(q(it.x), q(it.y)) }
        return Stroke(
            id = UUID.randomUUID().toString(), points = quad, colorHex = color, brushType = BrushType.FORMULA, sizePx = size,
            opacity = 1f, order = 0, formula = FormulaSpec(com.karapp.drawguidance.content.formula.Formula.atomize(t.source), t.id),
        )
    }

    /** Diyagram ([texts] verilirse adımların yazıları sırayla onlar). */
    fun diagram(kind: DiagramKind, rect: Rect, style: ShapeStyle, theme: String, texts: List<String> = emptyList()): Stroke {
        val defaults = DiagramRenderer.defaultNodes(kind).mapIndexed { i, n -> texts.getOrNull(i)?.let { n.copy(text = it) } ?: n }
        val nodes = DiagramRenderer.layout(kind, defaults)
        val spec = DiagramSpec(kind = kind, nodes = nodes, edges = DiagramRenderer.defaultEdges(kind, nodes), style = style, theme = theme)
        return Stroke(
            id = UUID.randomUUID().toString(),
            points = listOf(StrokePoint(rect.left, rect.top), StrokePoint(rect.right, rect.top), StrokePoint(rect.right, rect.bottom), StrokePoint(rect.left, rect.bottom)),
            colorHex = style.strokeColorHex, brushType = BrushType.DIAGRAM, sizePx = style.strokeWidth, opacity = 1f, order = 0, diagram = spec,
        )
    }

    fun shape(kind: String, start: Offset, end: Offset, style: ShapeStyle, opacity: Float = 1f): Stroke? =
        ShapeFactory.build(kind, start, end, style, id = UUID.randomUUID().toString(), order = 0, opacity = opacity)
}
