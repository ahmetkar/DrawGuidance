package com.karapp.drawguidance.ui.screens.canvasedit.editor

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.isQuad
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * GEÇİCİ "boşlukları doldur" fonksiyonu (yapay zeka komutları gelene kadar kural tabanlı).
 *
 * Seçili çizimlerin serbest uçlarını (başka bir seçili çizime değmeyen uçlar) bulur, birbirine
 * yakın ve yönleri uyumlu uç çiftlerini eşleştirir ve her çifti, iki çizginin yönünü sürdüren
 * yumuşak bir kübik eğriyle birleştirir. Eğri, bağlandığı ilk çizginin fırçasıyla çizilir;
 * uçlara değdiği için bağlanan çizgiler tek eleman olur.
 */
object GapFiller {

    class Result(val strokes: List<Stroke>, val bridges: List<Stroke>)

    private class End(val stroke: Stroke, val strokeIndex: Int, val isStart: Boolean, val x: Float, val y: Float, val dx: Float, val dy: Float)

    fun fill(strokes: List<Stroke>, selectedIds: Set<String>): Result {
        val candidates = strokes.withIndex().filter { (_, s) ->
            s.id in selectedIds && s.brushType != BrushType.ERASER && !s.brushType.isQuad && s.points.size >= 2 && length(s) >= 2f
        }
        if (candidates.isEmpty()) return Result(strokes, emptyList())

        val bounds = ElementOps.bounds(candidates.map { it.value }) ?: return Result(strokes, emptyList())
        val diagonal = hypot(bounds.width, bounds.height)
        val avgWidth = candidates.map { ElementOps.halfWidth(it.value) * 2f }.average().toFloat()
        val maxGap = max(max(60f, avgWidth * 10f), diagonal * 0.35f)

        val ends = ArrayList<End>()
        for ((index, stroke) in candidates) {
            val half = ElementOps.halfWidth(stroke)
            val first = stroke.points.first()
            val last = stroke.points.last()
            if (hypot(first.x - last.x, first.y - last.y) <= half * 2f) continue // kapalı şekil
            listOf(true, false).forEach { isStart ->
                val end = if (isStart) first else last
                val (dx, dy) = outwardDirection(stroke, isStart, max(8f, half * 3f))
                val touchesOther = candidates.any { (otherIndex, other) ->
                    otherIndex != index && ElementOps.distanceToPolyline(Offset(end.x, end.y), other.points) <= half + ElementOps.halfWidth(other) + 1f
                }
                if (!touchesOther) ends += End(stroke, index, isStart, end.x, end.y, dx, dy)
            }
        }

        data class Pair(val a: End, val b: End, val score: Float)
        val pairs = ArrayList<Pair>()
        for (i in ends.indices) for (j in i + 1 until ends.size) {
            val a = ends[i]
            val b = ends[j]
            val d = hypot(b.x - a.x, b.y - a.y)
            if (d < 0.5f || d > maxGap) continue
            if (a.strokeIndex == b.strokeIndex && d > length(a.stroke) * 0.3f) continue
            val ux = (b.x - a.x) / d
            val uy = (b.y - a.y) / d
            val cosA = a.dx * ux + a.dy * uy
            val cosB = -(b.dx * ux + b.dy * uy)
            if (cosA < -0.3f && cosB < -0.3f) continue // iki uç da ters yöne bakıyor
            pairs += Pair(a, b, d * (1f + (1f - cosA) * 0.35f + (1f - cosB) * 0.35f))
        }
        pairs.sortBy { it.score }

        val used = HashSet<End>()
        val bridges = ArrayList<Stroke>()
        for (pair in pairs) {
            if (pair.a in used || pair.b in used) continue
            used += pair.a
            used += pair.b
            bridges += bridge(pair.a, pair.b, strokes.size + bridges.size)
        }
        return Result(strokes + bridges, bridges)
    }

    private fun length(stroke: Stroke): Float {
        var total = 0f
        for (i in 1 until stroke.points.size) total += hypot(stroke.points[i].x - stroke.points[i - 1].x, stroke.points[i].y - stroke.points[i - 1].y)
        return total
    }

    /** Uçtan dışarı doğru birim yön: uçla, çizgi üzerinde [reach] kadar içerideki nokta arasındaki yön. */
    private fun outwardDirection(stroke: Stroke, isStart: Boolean, reach: Float): kotlin.Pair<Float, Float> {
        val pts = if (isStart) stroke.points else stroke.points.asReversed()
        val end = pts[0]
        var walked = 0f
        var inner = pts[1]
        for (i in 1 until pts.size) {
            walked += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
            inner = pts[i]
            if (walked >= reach) break
        }
        val dx = end.x - inner.x
        val dy = end.y - inner.y
        val l = sqrt(dx * dx + dy * dy)
        return if (l == 0f) 1f to 0f else (dx / l) to (dy / l)
    }

    private fun bridge(a: End, b: End, order: Int): Stroke {
        val d = hypot(b.x - a.x, b.y - a.y)
        val k = min(d * 0.38f, d)
        val c1x = a.x + a.dx * k
        val c1y = a.y + a.dy * k
        val c2x = b.x + b.dx * k
        val c2y = b.y + b.dy * k
        val half = ElementOps.halfWidth(a.stroke)
        val n = max(6, ceil(d / max(2f, half)).toInt() + 1).coerceAtMost(400)
        val points = List(n) { i ->
            val t = i / (n - 1f)
            val u = 1f - t
            val x = u * u * u * a.x + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * b.x
            val y = u * u * u * a.y + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * b.y
            StrokePoint(ContentCodec.quantize(x), ContentCodec.quantize(y))
        }
        val style = a.stroke
        return Stroke(
            id = UUID.randomUUID().toString(),
            points = points,
            colorHex = style.colorHex,
            brushType = style.brushType,
            sizePx = style.sizePx,
            opacity = style.opacity,
            order = order,
            groupId = style.groupId,
            flow = style.flow,
            strength = style.strength,
        )
    }
}
