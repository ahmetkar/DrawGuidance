package com.karapp.drawguidance.ui.drawing

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.karapp.drawguidance.data.model.DiagramBox
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramNode
import com.karapp.drawguidance.ui.drawing.DiagramCatalog.GANTT_L
import com.karapp.drawguidance.ui.drawing.DiagramCatalog.PLOT_B
import com.karapp.drawguidance.ui.drawing.DiagramCatalog.PLOT_L
import com.karapp.drawguidance.ui.drawing.DiagramCatalog.PLOT_R
import com.karapp.drawguidance.ui.drawing.DiagramCatalog.PLOT_T
import com.karapp.drawguidance.ui.drawing.DiagramPainter.Align
import com.karapp.drawguidance.ui.drawing.DiagramPainter.Ctx
import com.karapp.drawguidance.ui.drawing.DiagramPainter.Look
import com.karapp.drawguidance.ui.drawing.DiagramPainter.U
import com.karapp.drawguidance.ui.drawing.DiagramPainter.alpha
import com.karapp.drawguidance.ui.drawing.DiagramPainter.blend
import com.karapp.drawguidance.ui.drawing.DiagramPainter.drawBlock
import com.karapp.drawguidance.ui.drawing.DiagramPainter.drawText
import com.karapp.drawguidance.ui.drawing.DiagramPainter.fitSize
import com.karapp.drawguidance.ui.drawing.DiagramPainter.fmt
import com.karapp.drawguidance.ui.drawing.DiagramPainter.measure
import com.karapp.drawguidance.ui.drawing.DiagramPainter.medium
import com.karapp.drawguidance.ui.drawing.DiagramPainter.mono
import com.karapp.drawguidance.ui.drawing.DiagramPainter.p
import com.karapp.drawguidance.ui.drawing.DiagramPainter.rectOf
import com.karapp.drawguidance.ui.drawing.DiagramPainter.sans
import com.karapp.drawguidance.ui.drawing.DiagramPainter.strokePaint
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Türe özgü süsler: grafik eksenleri ve ızgaraları, sıra diyagramının yaşam çizgileri, radar halkaları, Gantt hafta
 * ızgarası, yolculuk duygu eğrisi, dörtlü konum ve Wardley eksenleri, zaman çizelgesi ekseni, balık kılçığı omurgası,
 * patlatılmış / aksonometrik levhalar. [before] oklardan ve kutulardan önce, [after] sonra çizilir.
 */
internal object DiagramDecor {

    private val TR = Locale("tr")

    fun before(x: Ctx) {
        when (x.kind) {
            DiagramKind.BAR -> chartAxes(x, DiagramCatalog.barScale(x.nodes))
            DiagramKind.WATERFALL -> { chartAxes(x, DiagramCatalog.waterfallScale(x.nodes)); waterfallConnectors(x) }
            DiagramKind.LINE -> { chartAxes(x, DiagramCatalog.lineScale(x.nodes)); lineSeries(x) }
            DiagramKind.SCATTER -> DiagramCatalog.scatterScale(x.nodes).let { (mx, my) -> chartAxes(x, my, mx); trend(x) }
            DiagramKind.RADAR -> radar(x)
            DiagramKind.POLAR -> polar(x)
            DiagramKind.GANTT -> ganttGrid(x)
            DiagramKind.SEQUENCE -> lifelines(x)
            DiagramKind.JOURNEY -> journey(x)
            DiagramKind.QUADRANT -> quadrant(x)
            DiagramKind.WARDLEY -> wardley(x)
            DiagramKind.EXPLODED -> explodedAxis(x)
            DiagramKind.AXONOMETRIC -> isoGround(x)
            DiagramKind.TIMELINE -> timeline(x)
            DiagramKind.FISHBONE -> fishbone(x)
            DiagramKind.STORY_MAP -> backbone(x)
            else -> Unit
        }
    }

    fun after(x: Ctx) {
        when (x.kind) {
            DiagramKind.BAR, DiagramKind.WATERFALL -> barLabels(x)
            DiagramKind.LINE -> lineLabels(x)
            DiagramKind.SCATTER -> scatterLabels(x)
            DiagramKind.GANTT -> ganttLabels(x)
            DiagramKind.SANKEY -> sankeyLabels(x)
            DiagramKind.LAYERS -> layerNumbers(x)
            DiagramKind.EXPLODED -> explodedLabels(x)
            DiagramKind.WARDLEY -> wardleyMoves(x)
            else -> Unit
        }
    }

    private fun paper(x: Ctx) = if (x.pal.editorial) x.pal.paper else Color.WHITE

    private fun fill(x: Ctx, color: Int) {
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = if (x.pal.coverage) Color.BLACK else color
    }

    private fun stroke(x: Ctx, color: Int, width: Float, dashed: Boolean = false) =
        strokePaint(x, if (x.pal.coverage) Color.BLACK else color, width, dashed)

    private fun tick(x: Ctx): Float = (x.base * 0.5f).coerceIn(10f, 26f)

    // ------------------------------------------------------------------------------------------ grafikler

    /** Y ekseni ızgarası (0, ¼, ½, ¾, tam) ve değer yazıları; [maxX] verilirse X ekseni de. */
    private fun chartAxes(x: Ctx, maxY: Float, maxX: Float? = null) {
        val l = PLOT_L * U; val r = PLOT_R * U; val t = PLOT_T * U; val b = PLOT_B * U
        val s = tick(x)
        for (i in 0..4) {
            val y = b - (b - t) * i / 4f
            if (i == 0) stroke(x, x.pal.ink, x.line * 0.9f) else stroke(x, x.pal.rule, x.line * 0.55f)
            x.c.drawLine(l, y, r, y, p)
            val label = fmt(maxY * i / 4f)
            drawText(x, label, l - 8f * x.k - measure(label, mono, s, 0f) * x.k, y + s * 0.36f, mono, s, x.pal.muted)
        }
        if (maxX != null) {
            for (i in 0..4) {
                val xx = l + (r - l) * i / 4f
                if (i > 0) { stroke(x, x.pal.rule, x.line * 0.55f); x.c.drawLine(xx, t, xx, b, p) }
                val label = fmt(maxX * i / 4f)
                drawText(x, label, xx - measure(label, mono, s, 0f) * x.k / 2f, b + s * 1.5f, mono, s, x.pal.muted)
            }
            stroke(x, x.pal.ink, x.line * 0.9f); x.c.drawLine(l, t, l, b, p)
        }
    }

    private fun barLabels(x: Ctx) {
        val s = tick(x)
        val running = if (x.kind == DiagramKind.WATERFALL) DiagramCatalog.waterfallLevels(x.nodes) else null
        x.nodes.forEachIndexed { i, n ->
            if (n.box != DiagramBox.BAR) return@forEachIndexed
            val r = rectOf(n)
            val item = DiagramCatalog.parse(n.text)
            val v = item.values.lastOrNull()
            val value = when {
                x.kind == DiagramKind.WATERFALL && (item.computed || v == null) -> fmt(running?.getOrNull(i)?.second ?: 0f)
                v == null -> ""
                item.signed && v > 0f -> "+" + fmt(v)
                item.signed && v < 0f -> "−" + fmt(-v)
                else -> fmt(v)
            }
            val focal = n.role == "focal"
            val vs = s * 1.05f
            val face = if (focal || item.computed) medium else mono
            val color = if (focal) x.pal.accent else x.pal.ink
            drawText(x, value, r.centerX() - measure(value, face, vs, 0f) * x.k / 2f, r.top - vs * 0.45f, face, vs, color)
            val name = DiagramCatalog.label(x.kind, n.text)
            val pitch = (PLOT_R - PLOT_L) * U / max(1, x.nodes.size)
            val ns = fitSize(name, sans, s * 1.05f, (pitch - 6f) / x.k, 0f)
            drawText(x, name, r.centerX() - measure(name, sans, ns, 0f) * x.k / 2f, PLOT_B * U + ns * 1.45f, sans, ns, if (focal) x.pal.accent else x.pal.muted)
        }
    }

    private fun waterfallConnectors(x: Ctx) {
        val levels = DiagramCatalog.waterfallLevels(x.nodes)
        val max = DiagramCatalog.waterfallScale(x.nodes)
        fun y(v: Float) = (PLOT_B - (PLOT_B - PLOT_T) * (v.coerceAtLeast(0f) / max)) * U
        stroke(x, x.pal.muted, x.line * 0.7f, dashed = true)
        for (i in 0 until x.nodes.size - 1) {
            val a = rectOf(x.nodes[i]); val b = rectOf(x.nodes[i + 1])
            val yy = y(levels[i].second)
            x.c.drawLine(a.right, yy, b.left, yy, p)
        }
    }

    private fun centers(x: Ctx): List<Pair<Float, Float>> = x.nodes.filter { it.box == DiagramBox.DOT }.map { DiagramPainter.dotCenter(it) }

    private fun lineSeries(x: Ctx) {
        val pts = centers(x)
        if (pts.size < 2) return
        val path = Path()
        pts.forEachIndexed { i, (px, py) -> if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }
        // Altı hafif tonlu alan
        val area = Path(path).apply { lineTo(pts.last().first, PLOT_B * U); lineTo(pts.first().first, PLOT_B * U); close() }
        fill(x, alpha(x.pal.ink, 0.06f)); x.c.drawPath(area, p)
        stroke(x, x.pal.ink, x.line * 1.35f); x.c.drawPath(path, p)
    }

    private fun lineLabels(x: Ctx) {
        val s = tick(x)
        val dots = x.nodes.filter { it.box == DiagramBox.DOT }
        val pitch = (PLOT_R - PLOT_L) * U / max(1, dots.size)
        dots.forEach { n ->
            val (cx, cy) = DiagramPainter.dotCenter(n)
            val name = DiagramCatalog.label(x.kind, n.text)
            val ns = fitSize(name, sans, s * 1.05f, (pitch - 4f) / x.k, 0f)
            drawText(x, name, cx - measure(name, sans, ns, 0f) * x.k / 2f, PLOT_B * U + ns * 1.45f, sans, ns, x.pal.muted)
            if (n.role == "focal") {
                val v = fmt(DiagramCatalog.parse(n.text).values.lastOrNull() ?: 0f)
                val vs = s * 1.15f
                drawText(x, v, cx - measure(v, medium, vs, 0f) * x.k / 2f, cy - vs * 0.9f, medium, vs, x.pal.accent)
            }
        }
    }

    private fun trend(x: Ctx) {
        val pts = centers(x)
        if (pts.size < 3) return
        val n = pts.size
        val mx = pts.sumOf { it.first.toDouble() } / n; val my = pts.sumOf { it.second.toDouble() } / n
        var sxy = 0.0; var sxx = 0.0
        pts.forEach { (px, py) -> sxy += (px - mx) * (py - my); sxx += (px - mx) * (px - mx) }
        if (sxx < 1e-6) return
        val slope = sxy / sxx
        val x0 = PLOT_L * U; val x1 = PLOT_R * U
        val y0 = (my + slope * (x0 - mx)).toFloat().coerceIn(PLOT_T * U, PLOT_B * U)
        val y1 = (my + slope * (x1 - mx)).toFloat().coerceIn(PLOT_T * U, PLOT_B * U)
        stroke(x, x.pal.muted, x.line * 0.8f, dashed = true)
        x.c.drawLine(x0, y0, x1, y1, p)
    }

    private fun scatterLabels(x: Ctx) {
        val s = tick(x)
        x.nodes.filter { it.box == DiagramBox.DOT }.forEach { n ->
            val (cx, cy) = DiagramPainter.dotCenter(n)
            val name = DiagramCatalog.label(x.kind, n.text)
            if (name.isEmpty()) return@forEach
            val focal = n.role == "focal"
            val face = if (focal) medium else mono
            drawText(x, name, cx + 12f * x.k, cy - 8f, face, if (focal) s * 1.15f else s, if (focal) x.pal.accent else x.pal.muted)
        }
    }

    // --- Radar ve kutupsal ---

    @Suppress("UNUSED_PARAMETER")
    private fun ringRadius(x: Ctx): Float = 0.34f * U

    private fun radar(x: Ctx) {
        val n = x.nodes.size
        if (n < 3) return
        val cx = 0.5f * U; val cy = 0.5f * U; val r = ringRadius(x)
        val values = x.nodes.map { DiagramCatalog.parse(it.text).values }
        val max = niceMax(values.flatten().maxOrNull() ?: 1f)
        fun pt(i: Int, f: Float): Pair<Float, Float> {
            val a = -Math.PI / 2 + 2 * Math.PI * i / n
            return (cx + r * f * cos(a).toFloat()) to (cy + r * f * sin(a).toFloat())
        }
        // Halkalar ve kollar
        stroke(x, x.pal.rule, x.line * 0.6f)
        for (ring in 1..4) {
            val path = Path()
            for (i in 0..n) { val (px, py) = pt(i % n, ring / 4f); if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }
            x.c.drawPath(path, p)
        }
        for (i in 0 until n) { val (px, py) = pt(i, 1f); x.c.drawLine(cx, cy, px, py, p) }
        val series = values.maxOfOrNull { it.size } ?: 0
        val colors = listOf(x.pal.ink, x.pal.accent, x.pal.link)
        for (sIdx in 0 until series) {
            val path = Path()
            for (i in 0..n) {
                val v = values[i % n].getOrElse(sIdx) { 0f }
                val (px, py) = pt(i % n, (v / max).coerceIn(0f, 1f))
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            val c = colors[sIdx % colors.size]
            fill(x, alpha(c, if (sIdx == 0) 0.07f else 0.12f)); x.c.drawPath(path, p)
            stroke(x, c, x.line * 1.3f); x.c.drawPath(path, p)
            for (i in 0 until n) {
                val v = values[i].getOrElse(sIdx) { 0f }
                val (px, py) = pt(i, (v / max).coerceIn(0f, 1f))
                fill(x, c); x.c.drawOval(RectF(px - 4.5f * x.k, py - 4.5f, px + 4.5f * x.k, py + 4.5f), p)
            }
        }
        // Ölçek yazısı (dış halka)
        val s = tick(x) * 0.85f
        drawText(x, fmt(max), cx + 6f * x.k, cy - r - 4f, mono, s, x.pal.muted)
    }

    private fun polar(x: Ctx) {
        val n = x.nodes.size
        if (n < 1) return
        val cx = 0.5f * U; val cy = 0.5f * U; val r = ringRadius(x)
        val values = x.nodes.map { DiagramCatalog.parse(it.text).values.lastOrNull() ?: 0f }
        val max = niceMax(values.maxOrNull() ?: 1f)
        stroke(x, x.pal.rule, x.line * 0.6f)
        for (ring in 1..4) {
            val rr = r * ring / 4f
            x.c.drawOval(RectF(cx - rr, cy - rr, cx + rr, cy + rr), p)
        }
        val sweep = 360f / n
        x.nodes.forEachIndexed { i, node ->
            val f = (values[i] / max).coerceIn(0f, 1f)
            val rr = r * f
            // Dilim, adının yönünde ortalı
            val start = -90f + sweep * (i - 0.5f)
            val path = Path().apply {
                moveTo(cx, cy)
                arcTo(RectF(cx - rr, cy - rr, cx + rr, cy + rr), start + 1f, sweep - 2f)
                close()
            }
            val color = node.fillColorHex?.let { DiagramPainter.hex(it) }
                ?: if (node.role == "focal") x.pal.accent else blend(paper(x), x.pal.ink, 0.38f)
            fill(x, color); x.c.drawPath(path, p)
        }
        val s = tick(x) * 0.85f
        drawText(x, fmt(max), cx + 6f * x.k, cy - r - 4f, mono, s, x.pal.muted)
    }

    private fun niceMax(v: Float): Float {
        if (v <= 0f) return 1f
        val exp = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(v.toDouble()))).toFloat()
        val f = v / exp
        val nice = when { f <= 1f -> 1f; f <= 2f -> 2f; f <= 2.5f -> 2.5f; f <= 5f -> 5f; else -> 10f }
        return nice * exp
    }

    // --- Gantt ---

    private fun ganttGrid(x: Ctx) {
        val weeks = DiagramCatalog.ganttWeeks(x.nodes)
        val l = GANTT_L * U; val r = 0.98f * U
        val pitch = (r - l) / weeks
        val bottom = (x.nodes.maxOfOrNull { it.y + it.h } ?: 0.9f) * U + 0.03f * U
        val s = tick(x) * 0.95f
        val every = when { pitch / x.k > s * 2.6f -> 1; pitch / x.k > s * 1.4f -> 2; else -> 4 }
        for (w in 0..weeks) {
            val xx = l + pitch * w
            stroke(x, if (w % 4 == 0) blend(x.pal.rule, x.pal.ink, 0.15f) else x.pal.rule, x.line * 0.55f)
            x.c.drawLine(xx, 0.105f * U, xx, bottom, p)
            if (w < weeks && w % every == 0) {
                val label = "H${w + 1}"
                drawText(x, label, xx + pitch / 2f - measure(label, mono, s, 0f) * x.k / 2f, 0.08f * U, mono, s, x.pal.muted)
            }
        }
        stroke(x, x.pal.ink, x.line * 0.9f)
        x.c.drawLine(0.02f * U, 0.105f * U, r, 0.105f * U, p)
    }

    private fun ganttLabels(x: Ctx) {
        val s = (x.base * 0.62f).coerceIn(10f, 30f)
        x.nodes.forEach { n ->
            if (n.box != DiagramBox.BAR) return@forEach
            val r = rectOf(n)
            val name = DiagramCatalog.label(x.kind, n.text)
            val focal = n.role == "focal"
            val face = if (focal) medium else sans
            val ns = fitSize(name, face, min(s, r.height() * 1.1f), (GANTT_L * U - 0.04f * U) / x.k, 0f)
            drawText(x, name, 0.02f * U, r.centerY() + ns * 0.36f, face, ns, if (focal) x.pal.accent else x.pal.ink)
            val v = DiagramCatalog.parse(n.text).values
            if (v.size >= 2) {
                val range = "${fmt(v[v.size - 2])}–${fmt(v.last())}"
                val rs = min(s * 0.8f, r.height() * 0.8f)
                val w = measure(range, mono, rs, 0f) * x.k
                if (r.right + 8f * x.k + w < 0.995f * U) drawText(x, range, r.right + 8f * x.k, r.centerY() + rs * 0.36f, mono, rs, x.pal.muted)
                else if (r.width() > w + 12f * x.k) drawText(x, range, r.right - 6f * x.k - w, r.centerY() + rs * 0.36f, mono, rs, if (x.pal.editorial) x.pal.onInk else Color.WHITE)
            }
        }
    }

    // --- Sankey, katmanlar ---

    private fun sankeyLabels(x: Ctx) {
        val s = (x.base * 0.54f).coerceIn(10f, 26f)
        x.nodes.forEach { n ->
            if (n.box != DiagramBox.BAR) return@forEach
            val r = rectOf(n)
            val name = DiagramCatalog.label(x.kind, n.text)
            val v = DiagramCatalog.parse(n.text).values.lastOrNull()?.let { fmt(it) }.orEmpty()
            val first = (n.role ?: "c0") == "c0"
            val nw = measure(name, sans, s, 0f) * x.k
            val vw = measure(v, mono, s * 0.85f, 0f) * x.k
            // Şeritlerin üstünde okunabilsin: kağıt renginde hafif zemin
            if (x.pal.editorial && !first) {
                fill(x, alpha(x.pal.paper, 0.85f))
                x.c.drawRoundRect(RectF(r.right + 6f * x.k, r.centerY() - s * 1.0f, r.right + 14f * x.k + max(nw, vw), r.centerY() + s * 1.25f), 3f * x.k, 3f, p)
            }
            if (first) {
                drawText(x, name, r.left - 10f * x.k - nw, r.centerY() - s * 0.1f, sans, s, x.pal.ink)
                drawText(x, v, r.left - 10f * x.k - vw, r.centerY() + s * 0.95f, mono, s * 0.85f, x.pal.muted)
            } else {
                drawText(x, name, r.right + 10f * x.k, r.centerY() - s * 0.1f, sans, s, x.pal.ink)
                drawText(x, v, r.right + 10f * x.k, r.centerY() + s * 0.95f, mono, s * 0.85f, x.pal.muted)
            }
        }
    }

    private fun layerNumbers(x: Ctx) {
        val s = (x.base * 0.62f).coerceIn(10f, 30f)
        x.nodes.forEachIndexed { i, n ->
            val r = rectOf(n)
            val label = String.format(TR, "%02d", i + 1)
            val ls = fitSize(label, mono, min(s, r.height() * 0.5f), (r.left - 0.02f * U) / x.k, 0f)
            drawText(x, label, r.left - 14f * x.k - measure(label, mono, ls, 0f) * x.k, r.centerY() + ls * 0.36f, mono, ls, if (n.role == "focal") x.pal.accent else x.pal.muted)
        }
    }

    // --- Sıra diyagramı ---

    private fun lifelines(x: Ctx) {
        val bottom = 0.975f * U
        x.nodes.forEach { n ->
            val cx = (n.x + n.w / 2f) * U
            stroke(x, alpha(x.pal.muted, 0.7f), x.line * 0.7f, dashed = true)
            x.c.drawLine(cx, (n.y + n.h) * U, cx, bottom, p)
        }
        // Odaktaki katılımcının etkinlik çubuğu: ilk ve son mesajı arasında
        x.nodes.filter { it.role == "focal" }.forEach { n ->
            val ys = x.spec.edges.mapIndexedNotNull { i, e ->
                if (e.from != n.id && e.to != n.id) null
                else DiagramRenderer.sequenceY(x.spec, i)
            }
            if (ys.isEmpty()) return@forEach
            val cx = (n.x + n.w / 2f) * U
            val r = RectF(cx - DiagramRenderer.ACTIVATION, ys.min() - 14f, cx + DiagramRenderer.ACTIVATION, ys.max() + 14f)
            fill(x, if (x.pal.editorial) blend(x.pal.paper, x.pal.accent, 0.12f) else alpha(x.pal.accent, 0.2f)); x.c.drawRect(r, p)
            stroke(x, x.pal.accent, x.line); x.c.drawRect(r, p)
        }
    }

    // --- Yolculuk ---

    private fun journey(x: Ctx) {
        val stages = x.nodes.filter { it.role == "stage" }
        val moods = x.nodes.filter { it.role == "mood" }
        val actions = x.nodes.filter { it.role == "action" || it.role == null }
        val m = max(1, max(stages.size, max(moods.size, actions.size)))
        val left = 0.1f * U
        val colW = (0.99f * U - left) / m
        // Duygu bandı ve orta çizgi
        fill(x, alpha(if (x.pal.editorial) x.pal.soft else x.pal.rule, 0.7f))
        x.c.drawRect(left, 0.155f * U, 0.99f * U, 0.545f * U, p)
        stroke(x, x.pal.rule, x.line * 0.6f, dashed = true)
        x.c.drawLine(left, 0.35f * U, 0.99f * U, 0.35f * U, p)
        // Sütun ayırıcıları
        stroke(x, x.pal.rule, x.line * 0.6f)
        for (i in 1 until m) { val xx = left + colW * i; x.c.drawLine(xx, 0.0f, xx, 0.94f * U, p) }
        // Satır adları
        val s = tick(x)
        listOf("AŞAMA" to 0.065f, "DUYGU" to 0.35f, "EYLEM" to 0.68f, "SIKINTI" to 0.85f).forEach { (t0, y) ->
            val t = com.karapp.drawguidance.i18n.I18n.tr(t0)
            val ls = fitSize(t, mono, s, (left - 12f) / x.k, 0.06f)
            drawText(x, t, 0.01f * U, y * U + ls * 0.36f, mono, ls, x.pal.muted, spacing = 0.06f)
        }
        // Duygu eğrisi (noktalardan geçen yumuşak çizgi)
        val pts = moods.map { DiagramPainter.dotCenter(it) }.sortedBy { it.first }
        if (pts.size >= 2) {
            val path = Path().apply { moveTo(pts[0].first, pts[0].second) }
            for (i in 0 until pts.size - 1) {
                val p0 = pts[max(0, i - 1)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[min(pts.size - 1, i + 2)]
                path.cubicTo(
                    p1.first + (p2.first - p0.first) / 6f, p1.second + (p2.second - p0.second) / 6f,
                    p2.first - (p3.first - p1.first) / 6f, p2.second - (p3.second - p1.second) / 6f,
                    p2.first, p2.second,
                )
            }
            stroke(x, x.pal.ink, x.line * 1.3f); x.c.drawPath(path, p)
        }
    }

    // --- Dörtlü konum ve Wardley ---

    private fun arrowHead(x: Ctx, tx: Float, ty: Float, fx: Float, fy: Float, color: Int) {
        val d = DiagramPainter.visualDir(x, fx, fy, tx, ty) ?: return
        fill(x, color); x.c.drawPath(DiagramPainter.headPath(x, tx, ty, d, 13f, 5f), p)
    }

    private fun quadrant(x: Ctx) {
        val P = x.pal
        // Sol üst (yüksek etki, düşük çaba) hafif vurgulu
        fill(x, alpha(P.accent, 0.05f)); x.c.drawRect(0.03f * U, 0.08f * U, 0.5f * U, 0.5f * U, p)
        stroke(x, P.ink, x.line)
        x.c.drawLine(0.5f * U, 0.95f * U, 0.5f * U, 0.075f * U, p)
        arrowHead(x, 0.5f * U, 0.07f * U, 0.5f * U, 0.5f * U, P.ink)
        x.c.drawLine(0.03f * U, 0.5f * U, 0.85f * U, 0.5f * U, p)
        arrowHead(x, 0.855f * U, 0.5f * U, 0.5f * U, 0.5f * U, P.ink)
        val s = tick(x) * 0.9f
        val names = listOf("HIZLI KAZANÇ" to (0.05f to 0.12f), "BÜYÜK İŞ" to (0.52f to 0.12f), "DOLGU İŞLER" to (0.05f to 0.94f), "ZAMAN KAYBI" to (0.52f to 0.94f))
        names.forEachIndexed { i, (t0, pos) ->
            val t = com.karapp.drawguidance.i18n.I18n.tr(t0)
            val ls = fitSize(t, mono, s, (0.4f * U) / x.k, 0.08f)
            drawText(x, t, pos.first * U, pos.second * U, mono, ls, if (i == 0) P.accent else P.muted, spacing = 0.08f)
        }
    }

    private val wardleyStages = listOf("DOĞUŞ", "ÖZEL YAPIM", "ÜRÜN", "EMTİA")

    private fun wardley(x: Ctx) {
        val P = x.pal
        val l = 0.03f * U; val r = 0.985f * U; val b = 0.92f * U
        stroke(x, P.rule, x.line * 0.6f, dashed = true)
        for (i in 1..3) { val xx = l + (r - l) * i / 4f; x.c.drawLine(xx, 0.03f * U, xx, b, p) }
        stroke(x, P.ink, x.line)
        x.c.drawLine(l, b, l, 0.035f * U, p); arrowHead(x, l, 0.03f * U, l, b, P.ink)
        x.c.drawLine(l, b, r - 6f, b, p); arrowHead(x, r, b, l, b, P.ink)
        val s = tick(x) * 0.85f
        wardleyStages.forEachIndexed { i, t0 ->
            val t = com.karapp.drawguidance.i18n.I18n.tr(t0)
            val cx = l + (r - l) * (i + 0.5f) / 4f
            val ls = fitSize(t, mono, s, ((r - l) / 4f - 8f) / x.k, 0.06f)
            drawText(x, t, cx - measure(t, mono, ls, 0.06f) * x.k / 2f, b + ls * 1.6f, mono, ls, P.muted, spacing = 0.06f)
        }
        drawText(x, com.karapp.drawguidance.i18n.I18n.tr("GÖRÜNÜRLÜK"), l + 8f * x.k, 0.045f * U, mono, s, P.muted, spacing = 0.06f)
    }

    private fun wardleyMoves(x: Ctx) {
        x.nodes.filter { it.role == "moving" && it.box == DiagramBox.DOT }.forEach { n ->
            val (cx, cy) = DiagramPainter.dotCenter(n)
            val len = 0.15f * U
            val end = (cx + len).coerceAtMost(0.97f * U)
            stroke(x, x.pal.accent, x.line * 1.2f, dashed = true)
            x.c.drawLine(cx + 14f * x.k, cy + n.h * U * 0.45f, end - 10f * x.k, cy + n.h * U * 0.45f, p)
            arrowHead(x, end, cy + n.h * U * 0.45f, cx, cy + n.h * U * 0.45f, x.pal.accent)
        }
    }

    // --- Zaman çizelgesi, balık kılçığı, hikâye haritası ---

    private fun timeline(x: Ctx) {
        if (x.nodes.isEmpty()) return
        val axisY = 0.47f * U
        val P = x.pal
        stroke(x, P.ink, x.line * 1.1f)
        x.c.drawLine(0.02f * U, axisY, 0.975f * U, axisY, p)
        arrowHead(x, 0.985f * U, axisY, 0.02f * U, axisY, P.ink)
        x.nodes.forEach { n ->
            val cx = (n.x + n.w / 2f) * U
            val above = n.y + n.h / 2f < 0.47f
            stroke(x, P.rule, x.line * 0.8f)
            x.c.drawLine(cx, axisY, cx, if (above) (n.y + n.h) * U else n.y * U, p)
            val r = 7f
            fill(x, if (n.role == "focal") P.accent else paper(x)); x.c.drawOval(RectF(cx - r * x.k, axisY - r, cx + r * x.k, axisY + r), p)
            stroke(x, if (n.role == "focal") P.accent else P.ink, x.line); x.c.drawOval(RectF(cx - r * x.k, axisY - r, cx + r * x.k, axisY + r), p)
        }
    }

    private fun fishbone(x: Ctx) {
        val head = x.nodes.firstOrNull() ?: return
        val color = if (x.pal.coverage) Color.BLACK else x.pal.edge
        val spineY = (head.y + head.h / 2f) * U
        stroke(x, color, x.line * 1.6f)
        x.c.drawLine(0.02f * U, spineY, head.x * U, spineY, p)
        val tail = Path().apply {
            moveTo(0.02f * U, spineY); lineTo(0f, spineY - 0.06f * U)
            moveTo(0.02f * U, spineY); lineTo(0f, spineY + 0.06f * U)
        }
        x.c.drawPath(tail, p)
        stroke(x, if (x.pal.editorial) x.pal.muted else color, x.line)
        x.nodes.drop(1).forEach { cause ->
            val top = cause.y + cause.h / 2f < head.y + head.h / 2f
            val sx = (cause.x + cause.w / 2f) * U
            val sy = (if (top) cause.y + cause.h else cause.y) * U
            x.c.drawLine(sx, sy, sx + 0.1f * U, spineY, p)
        }
    }

    private fun backbone(x: Ctx) {
        val acts = x.nodes.filter { it.role == "activity" }
        if (acts.size < 2) return
        val y = (acts.maxOf { it.y + it.h } + 0.02f) * U
        val l = acts.minOf { it.x } * U; val r = acts.maxOf { it.x + it.w } * U
        stroke(x, x.pal.muted, x.line * 0.8f)
        x.c.drawLine(l, y, r - 8f, y, p)
        arrowHead(x, r, y, l, y, x.pal.muted)
    }

    // --- Patlatılmış ve aksonometrik ---

    /** Levhanın köşeleri: üst yüz (sol-üst, sağ-üst, sağ-alt, sol-alt) ve kalınlık. */
    private class Slab(val tl: Pair<Float, Float>, val tr: Pair<Float, Float>, val br: Pair<Float, Float>, val bl: Pair<Float, Float>, val t: Float)

    private fun slabOf(x: Ctx, n: DiagramNode): Slab {
        val r = rectOf(n)
        val sk = r.width() * 0.22f
        val t: Float
        val th: Float
        if (x.kind == DiagramKind.AXONOMETRIC) {
            val levels = Regex("(\\d+)\\s*kat").find(n.text.lowercase(TR))?.groupValues?.get(1)?.toIntOrNull() ?: 1
            t = r.height() * (0.12f + 0.1f * min(levels, 5)).coerceAtMost(0.6f)
            th = r.height() - t
        } else {
            th = r.height() * 0.55f
            t = r.height() * 0.16f
        }
        return Slab(r.left + sk to r.top, r.right to r.top, r.right - sk to r.top + th, r.left to r.top + th, t)
    }

    fun drawSlab(x: Ctx, n: DiagramNode, look: Look, index: Int) {
        val s = slabOf(x, n)
        val top = look.fill ?: paper(x)
        val front = blend(top, x.pal.ink, 0.08f)
        val side = blend(top, x.pal.ink, 0.17f)
        val stroke = look.stroke ?: x.pal.ink
        val topFace = Path().apply { moveTo(s.tl.first, s.tl.second); lineTo(s.tr.first, s.tr.second); lineTo(s.br.first, s.br.second); lineTo(s.bl.first, s.bl.second); close() }
        val frontFace = Path().apply { moveTo(s.bl.first, s.bl.second); lineTo(s.br.first, s.br.second); lineTo(s.br.first, s.br.second + s.t); lineTo(s.bl.first, s.bl.second + s.t); close() }
        val sideFace = Path().apply { moveTo(s.br.first, s.br.second); lineTo(s.tr.first, s.tr.second); lineTo(s.tr.first, s.tr.second + s.t); lineTo(s.br.first, s.br.second + s.t); close() }
        listOf(topFace to top, frontFace to front, sideFace to side).forEach { (path, color) ->
            fill(x, color); x.c.drawPath(path, p)
            strokePaint(x, if (x.pal.coverage) Color.BLACK else stroke, look.width, look.dashed); x.c.drawPath(path, p)
        }
        p.pathEffect = null
        if (x.kind == DiagramKind.AXONOMETRIC && x.labels) {
            val r = RectF(s.bl.first + (s.br.first - s.bl.first) * 0.12f, s.tl.second, s.br.first - (s.br.first - s.bl.first) * 0.02f, s.bl.second)
            drawBlock(x, n.text, r, look, Align.CENTER, maxSize = x.base * 0.6f)
        }
    }

    private fun explodedAxis(x: Ctx) {
        val slabs = x.nodes.filter { it.box == DiagramBox.SLAB }.sortedBy { it.y }.map { slabOf(x, it) }
        stroke(x, x.pal.muted, x.line * 0.7f, dashed = true)
        for (i in 0 until slabs.size - 1) {
            val a = slabs[i]; val b = slabs[i + 1]
            x.c.drawLine(a.bl.first, a.bl.second + a.t, b.bl.first, b.bl.second, p)
            x.c.drawLine(a.br.first, a.br.second + a.t, b.br.first, b.br.second, p)
            x.c.drawLine(a.tr.first, a.tr.second + a.t, b.tr.first, b.tr.second, p)
        }
    }

    private fun explodedLabels(x: Ctx) {
        val lx = 0.66f * U
        x.nodes.filter { it.box == DiagramBox.SLAB }.sortedBy { it.y }.forEachIndexed { i, n ->
            val s = slabOf(x, n)
            val ax = (s.tr.first + s.br.first) / 2f; val ay = (s.tr.second + s.br.second) / 2f
            stroke(x, x.pal.muted, x.line * 0.7f)
            x.c.drawLine(ax, ay, lx - 6f * x.k, ay, p)
            fill(x, if (n.role == "focal") x.pal.accent else x.pal.ink)
            x.c.drawOval(RectF(ax - 4f * x.k, ay - 4f, ax + 4f * x.k, ay + 4f), p)
            if (!x.labels) return@forEachIndexed
            val look = DiagramPainter.lookOf(x, x.nodes.indexOf(n), n)
            val text = "[${String.format(TR, "%02d", i + 1)}]\n" + n.text
            val h = rectOf(n).height()
            drawBlock(x, text, RectF(lx, ay - h * 0.5f, 0.99f * U, ay + h * 0.5f), look, Align.LEFT, maxSize = x.base * 0.7f)
        }
    }

    private fun isoGround(x: Ctx) {
        val f = x.spec.frame
        val l = f.left * U; val t = f.top * U; val r = f.right * U; val b = f.bottom * U
        val save = x.c.save()
        x.c.clipRect(l, t, r, b)
        stroke(x, alpha(x.pal.rule, 0.6f), x.line * 0.5f)
        val step = 0.08f * U
        val slope = 0.5f / x.k
        val span = (r - l) * slope
        var c = t - span
        while (c < b + span) {
            x.c.drawLine(l, c, r, c + (r - l) * slope, p)
            x.c.drawLine(l, c + (r - l) * slope, r, c, p)
            c += step
        }
        x.c.restoreToCount(save)
    }
}
