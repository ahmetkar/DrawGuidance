package com.karapp.drawguidance.data.model

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * v56 aşama 8: simetri kılavuzu (dikey, yatay, dörtlü, radyal) ve "Desen yap". Canlı çizgi n kopyaya aynalanır;
 * kaydedilen kopyalar ayrı elemanlardır. iOS `V56Symmetry` ile aynı hesap.
 */
object V56Symmetry {
    /** Merkez etrafında 2×2 doğrusal dönüşüm (x' = cx + a·dx + b·dy, y' = cy + c·dx + d·dy). */
    data class Affine(val a: Float, val b: Float, val c: Float, val d: Float) {
        fun map(cx: Float, cy: Float, x: Float, y: Float): Pair<Float, Float> {
            val dx = x - cx; val dy = y - cy
            return (cx + a * dx + b * dy) to (cy + c * dx + d * dy)
        }
    }

    private fun rad(deg: Float) = deg * PI.toFloat() / 180f

    /** Yönü [deg] olan, merkezden geçen eksene göre yansıma. */
    fun reflect(deg: Float): Affine {
        val t = 2f * rad(deg)
        return Affine(cos(t), sin(t), sin(t), -cos(t))
    }

    fun rotate(deg: Float): Affine {
        val t = rad(deg)
        return Affine(cos(t), -sin(t), sin(t), cos(t))
    }

    /** Özdeşlik dışındaki kopya dönüşümleri. */
    fun transforms(spec: SymmetrySpec): List<Affine> = when (spec.kind) {
        SymmetryKind.VERTICAL -> listOf(reflect(90f + spec.angle))
        SymmetryKind.HORIZONTAL -> listOf(reflect(spec.angle))
        SymmetryKind.QUAD -> listOf(reflect(90f + spec.angle), reflect(spec.angle), rotate(180f))
        SymmetryKind.RADIAL -> {
            val n = spec.arms.coerceIn(2, 16)
            (1 until n).map { rotate(360f * it / n) }
        }
    }

    /** Eksen çizgileri (görünür kılavuz): her biri x1, y1, x2, y2 ([len] merkezden uzunluk). */
    fun axes(spec: SymmetrySpec, len: Float): List<FloatArray> {
        val dirs = when (spec.kind) {
            SymmetryKind.VERTICAL -> listOf(90f + spec.angle)
            SymmetryKind.HORIZONTAL -> listOf(spec.angle)
            SymmetryKind.QUAD -> listOf(90f + spec.angle, spec.angle)
            SymmetryKind.RADIAL -> {
                val n = spec.arms.coerceIn(2, 16)
                (0 until n).map { -90f + spec.angle + 360f * it / n }
            }
        }
        return dirs.map { deg ->
            val ux = cos(rad(deg)); val uy = sin(rad(deg))
            if (spec.kind == SymmetryKind.RADIAL) floatArrayOf(spec.cx, spec.cy, spec.cx + ux * len, spec.cy + uy * len)
            else floatArrayOf(spec.cx - ux * len, spec.cy - uy * len, spec.cx + ux * len, spec.cy + uy * len)
        }
    }

    fun mapPoints(points: List<StrokePoint>, cx: Float, cy: Float, t: Affine): List<StrokePoint> = points.map { p ->
        val (x, y) = t.map(cx, cy, p.x, p.y)
        p.copy(x = x, y = y)
    }

    /** [s] çiziminin simetri kopyaları (yeni kimliklerle, aynı katmanda). */
    fun copies(s: Stroke, spec: SymmetrySpec, newId: () -> String): List<Stroke> =
        transforms(spec).mapIndexed { i, t ->
            s.copy(id = newId(), points = mapPoints(s.points, spec.cx, spec.cy, t), order = s.order + i + 1)
        }

    /** Tuvalin ortasında yeni simetri ayarı. */
    fun start(kind: SymmetryKind, width: Int, height: Int, arms: Int = 6): SymmetrySpec =
        SymmetrySpec(kind, if (kind == SymmetryKind.RADIAL) arms else 2, width / 2f, height / 2f)

    // ------------------------------------------------------------------ Desen yap

    /**
     * Seçili elemanları ([bounds] kutusu: l, t, r, b) [gap] aralıkla tuval boyunca ızgara halinde döşer; kopyalar
     * kaynağın grubuna (yoksa yeni gruba) girer. Kopya sayısı [maxCopies] ile sınırlıdır. Dönen: yeni elemanlar.
     */
    fun pattern(
        selected: List<Stroke>, bounds: FloatArray, width: Int, height: Int, gap: Float, maxCopies: Int = 200,
        newId: () -> String,
    ): List<Stroke> {
        if (selected.isEmpty()) return emptyList()
        val w = bounds[2] - bounds[0] + gap
        val h = bounds[3] - bounds[1] + gap
        if (w <= 1f || h <= 1f) return emptyList()
        val i0 = -ceil(bounds[0] / w).toInt(); val i1 = ceil((width - bounds[2]) / w).toInt()
        val j0 = -ceil(bounds[1] / h).toInt(); val j1 = ceil((height - bounds[3]) / h).toInt()
        val out = ArrayList<Stroke>()
        val group = selected.firstNotNullOfOrNull { it.groupId } ?: newId()
        var copies = 0
        loop@ for (j in j0..j1) for (i in i0..i1) {
            if (i == 0 && j == 0) continue
            if (copies >= maxCopies) break@loop
            val dx = i * w; val dy = j * h
            selected.forEach { s -> out.add(V56Arrange.shift(s, dx, dy).copy(id = newId(), groupId = group)) }
            copies++
        }
        return out
    }

    /** Desende kaç kopya olur (pencerede gösterilir). */
    fun patternCount(bounds: FloatArray, width: Int, height: Int, gap: Float): Int {
        val w = bounds[2] - bounds[0] + gap
        val h = bounds[3] - bounds[1] + gap
        if (w <= 1f || h <= 1f) return 0
        val cols = ceil(bounds[0] / w).toInt() + ceil((width - bounds[2]) / w).toInt() + 1
        val rows = ceil(bounds[1] / h).toInt() + ceil((height - bounds[3]) / h).toInt() + 1
        return (cols * rows - 1).coerceAtLeast(0)
    }

}
