package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.data.model.GraphSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Grafik şekilleri: eksenler (2B/3B), kareli alan, birim çember, sütun/çizgi/pasta/dağılım grafikleri.
 * Eksen etiketleri ve formül [GraphSpec] ile verilir; formül verilirse eğri (3B'de tel kafes yüzey) çizilir.
 */
object GraphShapes {

    /** Grafik olan şekiller (eksen etiketi ve formül ayarları bunlarda gösterilir). */
    val kinds = setOf(
        ShapeKind.AXES, ShapeKind.AXES_3D, ShapeKind.FUNCTION_PLOT, ShapeKind.COORD_GRID,
        ShapeKind.UNIT_CIRCLE, ShapeKind.BAR_CHART, ShapeKind.LINE_CHART, ShapeKind.PIE_CHART, ShapeKind.SCATTER,
    )

    /** Ucu açık (hizalama ayarı sunulmayan) grafikler. */
    val openEnded = setOf(ShapeKind.AXES, ShapeKind.AXES_3D, ShapeKind.FUNCTION_PLOT, ShapeKind.COORD_GRID, ShapeKind.NUMBER_LINE)

    fun build(kind: ShapeKind, rect: Rect, ink: Float, graph: GraphSpec): List<ShapePath> = when (kind) {
        ShapeKind.AXES -> axes(rect, ink, graph, grid = false)
        ShapeKind.FUNCTION_PLOT -> axes(rect, ink, graph, grid = true)
        ShapeKind.COORD_GRID -> axes(rect, ink, graph, grid = true, ticks = false)
        ShapeKind.UNIT_CIRCLE -> unitCircle(rect, ink, graph)
        ShapeKind.AXES_3D -> axes3d(rect, ink, graph)
        ShapeKind.BAR_CHART -> bars(rect, ink, graph)
        ShapeKind.LINE_CHART -> lineChart(rect, ink, graph)
        ShapeKind.PIE_CHART -> pie(rect, ink)
        ShapeKind.SCATTER -> scatter(rect, ink, graph)
        else -> emptyList()
    }

    private fun labelSize(rect: Rect, ink: Float) = max(ink * 6f, min(rect.width, rect.height) * 0.075f)

    private fun arrow(tip: Offset, dir: Offset, size: Float): ShapePath {
        val n = Offset(-dir.y, dir.x)
        val back = tip - dir * size
        return ShapePath(listOf(back + n * size * 0.5f, tip, back - n * size * 0.5f))
    }

    /** 2B eksen takımı; [grid] kareli zemin, [ticks] çentikler. */
    private fun axes(rect: Rect, ink: Float, graph: GraphSpec, grid: Boolean, ticks: Boolean = true): List<ShapePath> {
        val out = ArrayList<ShapePath>()
        val cx = rect.center.x
        val cy = rect.center.y
        val head = max(ink * 4f, min(rect.width, rect.height) * 0.05f)
        val step = min(rect.width, rect.height) / 10f
        if (grid) {
            var x = cx
            while (x <= rect.right + 0.1f) { out += ShapePath(listOf(Offset(x, rect.top), Offset(x, rect.bottom))); x += step }
            x = cx - step
            while (x >= rect.left - 0.1f) { out += ShapePath(listOf(Offset(x, rect.top), Offset(x, rect.bottom))); x -= step }
            var y = cy
            while (y <= rect.bottom + 0.1f) { out += ShapePath(listOf(Offset(rect.left, y), Offset(rect.right, y))); y += step }
            y = cy - step
            while (y >= rect.top - 0.1f) { out += ShapePath(listOf(Offset(rect.left, y), Offset(rect.right, y))); y -= step }
        }
        out += ShapePath(listOf(Offset(rect.left, cy), Offset(rect.right, cy)))
        out += ShapePath(listOf(Offset(cx, rect.bottom), Offset(cx, rect.top)))
        out += arrow(Offset(rect.right, cy), Offset(1f, 0f), head)
        out += arrow(Offset(cx, rect.top), Offset(0f, -1f), head)
        if (ticks && !grid) {
            val tick = head * 0.4f
            for (i in 1..4) for (sign in listOf(-1f, 1f)) {
                val x = cx + sign * i * rect.width * 0.1f
                val y = cy + sign * i * rect.height * 0.1f
                out += ShapePath(listOf(Offset(x, cy - tick), Offset(x, cy + tick)))
                out += ShapePath(listOf(Offset(cx - tick, y), Offset(cx + tick, y)))
            }
        }
        val size = labelSize(rect, ink)
        out += label(graph.xLabel, Offset(rect.right - size * 1.2f, cy + size * 0.4f), size, rect)
        out += label(graph.yLabel, Offset(cx + size * 0.5f, rect.top), size, rect)
        curve(rect, graph)?.let { out += it }
        if (graph.formula.isNotBlank()) out += label(graph.formula, Offset(rect.left, rect.top), size * 0.9f, rect)
        return out
    }

    /** y = f(x) eğrisi: x aralığı kutuya, y ekseni veriye göre ölçeklenir. */
    private fun curve(rect: Rect, graph: GraphSpec): ShapePath? {
        val expr = Formula.parse(graph.formula) ?: return null
        val n = 160
        val xMin = graph.xMin.toDouble()
        val xMax = graph.xMax.toDouble().coerceAtLeast(xMin + 1e-3)
        val values = DoubleArray(n + 1) { i -> expr.eval(xMin + (xMax - xMin) * i / n, 0.0) }
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return null
        val extent = max(1e-3, max(abs(finite.max()), abs(finite.min())))
        val points = ArrayList<Offset>(n + 1)
        for (i in 0..n) {
            val v = values[i]
            if (!v.isFinite()) continue
            val x = rect.left + rect.width * i / n
            val y = rect.center.y - (v / extent).toFloat() * rect.height * 0.45f
            if (y < rect.top - rect.height || y > rect.bottom + rect.height) continue
            points += Offset(x, y.coerceIn(rect.top, rect.bottom))
        }
        return if (points.size < 2) null else ShapePath(points)
    }

    private fun label(text: String, at: Offset, size: Float): ShapePath =
        ShapePath(listOf(at), label = text, labelSize = size)

    /** Etiket çapasını grafiğin kutusunun içinde tutar (yazı dışarı taşmaz). */
    private fun label(text: String, at: Offset, size: Float, rect: Rect): ShapePath =
        label(
            text,
            Offset(
                at.x.coerceIn(rect.left, (rect.right - size * 0.6f).coerceAtLeast(rect.left)),
                at.y.coerceIn(rect.top, (rect.bottom - size).coerceAtLeast(rect.top)),
            ),
            size,
        )

    private fun unitCircle(rect: Rect, ink: Float, graph: GraphSpec): List<ShapePath> {
        val out = ArrayList<ShapePath>(axes(rect, ink, graph, grid = false, ticks = false))
        val r = min(rect.width, rect.height) * 0.38f
        val c = rect.center
        val n = 96
        out += ShapePath(List(n + 1) { i -> Offset(c.x + r * cos(i * 2 * PI / n).toFloat(), c.y + r * sin(i * 2 * PI / n).toFloat()) }, closed = true)
        for (deg in listOf(30, 45, 60)) {
            val a = -deg * PI / 180
            out += ShapePath(listOf(c, Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())), dashed = true)
        }
        return out
    }

    /** İzometrik 3B eksenler; formül verilirse z = f(x, y) tel kafes yüzeyi. */
    private fun axes3d(rect: Rect, ink: Float, graph: GraphSpec): List<ShapePath> {
        val out = ArrayList<ShapePath>()
        val origin = Offset(rect.left + rect.width * 0.42f, rect.top + rect.height * 0.72f)
        val unit = min(rect.width, rect.height)
        val ex = Offset(-0.62f, 0.36f) * (unit * 0.42f)
        val ey = Offset(0.92f, 0.3f) * (unit * 0.42f)
        val ez = Offset(0f, -1f) * (unit * 0.58f)
        val head = max(ink * 4f, unit * 0.045f)
        listOf(ex to graph.xLabel, ey to graph.yLabel, ez to graph.zLabel).forEach { (axis, name) ->
            val tip = origin + axis
            out += ShapePath(listOf(origin, tip))
            out += arrow(tip, axis / axis.getDistance(), head)
            val size = labelSize(rect, ink)
            out += label(name, tip + axis / axis.getDistance() * size * 0.7f - Offset(size * 0.3f, size * 0.3f), size, rect)
        }
        val expr = Formula.parse(graph.formula)
        if (expr != null) {
            val steps = 10
            val xMin = graph.xMin.toDouble()
            val xMax = graph.xMax.toDouble().coerceAtLeast(xMin + 1e-3)
            val z = Array(steps + 1) { i -> DoubleArray(steps + 1) { j -> expr.eval(xMin + (xMax - xMin) * i / steps, xMin + (xMax - xMin) * j / steps) } }
            val finite = z.flatMap { it.toList() }.filter { it.isFinite() }
            val extent = max(1e-3, finite.maxOfOrNull { abs(it) } ?: 1.0)
            fun project(i: Int, j: Int): Offset? {
                val v = z[i][j]
                if (!v.isFinite()) return null
                val u = i / steps.toFloat()
                val w = j / steps.toFloat()
                return origin + ex * u + ey * w + ez * ((v / extent).toFloat() * 0.85f)
            }
            for (i in 0..steps) {
                val row = (0..steps).mapNotNull { j -> project(i, j) }
                if (row.size > 1) out += ShapePath(row)
            }
            for (j in 0..steps) {
                val col = (0..steps).mapNotNull { i -> project(i, j) }
                if (col.size > 1) out += ShapePath(col)
            }
            out += label(graph.formula, Offset(rect.left, rect.top), labelSize(rect, ink) * 0.9f, rect)
        }
        return out
    }

    private fun frameAxes(rect: Rect): List<ShapePath> = listOf(
        ShapePath(listOf(Offset(rect.left, rect.top), Offset(rect.left, rect.bottom), Offset(rect.right, rect.bottom))),
    )

    private fun bars(rect: Rect, ink: Float, graph: GraphSpec): List<ShapePath> {
        val out = ArrayList<ShapePath>(frameAxes(rect))
        val values = listOf(0.45f, 0.8f, 0.6f, 0.95f, 0.35f)
        val gap = rect.width / (values.size * 1.6f)
        val w = gap * 0.75f
        values.forEachIndexed { i, v ->
            val left = rect.left + gap * 0.5f + i * gap * 1.5f
            val top = rect.bottom - rect.height * 0.85f * v
            out += ShapePath(
                listOf(Offset(left, rect.bottom), Offset(left, top), Offset(left + w, top), Offset(left + w, rect.bottom)),
                closed = true,
            )
        }
        val size = labelSize(rect, ink)
        out += label(graph.xLabel, Offset(rect.right - size, rect.bottom + size * 0.9f), size, rect)
        out += label(graph.yLabel, Offset(rect.left - size * 0.2f, rect.top), size, rect)
        return out
    }

    private fun lineChart(rect: Rect, ink: Float, graph: GraphSpec): List<ShapePath> {
        val out = ArrayList<ShapePath>(frameAxes(rect))
        val values = listOf(0.25f, 0.55f, 0.4f, 0.75f, 0.6f, 0.9f)
        val r = max(ink, min(rect.width, rect.height) * 0.018f)
        // İlk ve son noktanın işaretçisi kutunun dışına taşmasın
        val span = (rect.width - 2 * r).coerceAtLeast(1f)
        val points = values.mapIndexed { i, v ->
            Offset(rect.left + r + span * (i / (values.size - 1f)), rect.bottom - rect.height * 0.85f * v)
        }
        out += ShapePath(points)
        points.forEach { p -> out += ShapePath(List(17) { k -> Offset(p.x + r * cos(k * 2 * PI / 16).toFloat(), p.y + r * sin(k * 2 * PI / 16).toFloat()) }, closed = true) }
        val size = labelSize(rect, ink)
        out += label(graph.xLabel, Offset(rect.right - size, rect.bottom + size * 0.9f), size, rect)
        out += label(graph.yLabel, Offset(rect.left - size * 0.2f, rect.top), size, rect)
        return out
    }

    private fun pie(rect: Rect, ink: Float): List<ShapePath> {
        val c = rect.center
        val r = min(rect.width, rect.height) / 2f
        val out = ArrayList<ShapePath>()
        val n = 96
        out += ShapePath(List(n + 1) { i -> Offset(c.x + r * cos(i * 2 * PI / n).toFloat(), c.y + r * sin(i * 2 * PI / n).toFloat()) }, closed = true)
        var angle = -PI / 2
        listOf(0.35f, 0.25f, 0.22f, 0.18f).forEach { share ->
            out += ShapePath(listOf(c, Offset(c.x + r * cos(angle).toFloat(), c.y + r * sin(angle).toFloat())))
            angle += share * 2 * PI
        }
        return out
    }

    private fun scatter(rect: Rect, ink: Float, graph: GraphSpec): List<ShapePath> {
        val out = ArrayList<ShapePath>(frameAxes(rect))
        val r = max(ink, min(rect.width, rect.height) * 0.016f)
        val points = listOf(
            0.12f to 0.3f, 0.2f to 0.55f, 0.3f to 0.45f, 0.38f to 0.7f, 0.5f to 0.6f,
            0.58f to 0.85f, 0.66f to 0.72f, 0.75f to 0.9f, 0.85f to 0.8f, 0.92f to 0.95f,
        )
        points.forEach { (u, v) ->
            val p = Offset(rect.left + rect.width * u, rect.bottom - rect.height * 0.85f * v)
            out += ShapePath(List(13) { k -> Offset(p.x + r * cos(k * 2 * PI / 12).toFloat(), p.y + r * sin(k * 2 * PI / 12).toFloat()) }, closed = true)
        }
        val size = labelSize(rect, ink)
        out += label(graph.xLabel, Offset(rect.right - size, rect.bottom + size * 0.9f), size, rect)
        out += label(graph.yLabel, Offset(rect.left - size * 0.2f, rect.top), size, rect)
        return out
    }
}
