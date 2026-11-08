package com.karapp.drawguidance.ui.screens.canvasedit.ruler

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v40 cetvel ve şekil kalıbının yapışma kenarları (tuval koordinatı): doğru parçaları ve yaylar, konturlara (bir
 * cetvel kenarı, bir üçgenin çevresi, şeklin bir alt yolu ya da deliği) bölünmüş. Kenarlar bir kez hesaplanır
 * ([Builder]); dokunuş başına iş yalnız en yakın parçayı bulmak: ayırma yapılmaz, sonuç verilen diziye yazılır.
 *
 * Çizgi parmağın indiği yere en yakın kontura kilitlenir ([lock]); sonraki noktalar o konturun en yakın noktasına
 * düşürülür ([project]). Böylece çizgi gerçek bir cetvel gibi kenarı izler, köşelerde (kapalı konturda) bir sonraki
 * kenara geçer.
 */
class SnapEdges private constructor(
    /** Parça türü: [LINE] ya da [ARC]. */
    private val kinds: IntArray,
    /**
     * Parça başına [STRIDE] sayı. Doğru: x1, y1, dx, dy, 1/(dx²+dy²). Yay: merkez x, y, yarıçap, başlangıç açısı
     * (radyan), tarama (radyan, pozitif; 2π tam çember).
     */
    private val data: FloatArray,
    /** Konturların ilk parçası; son eleman parça sayısı (kontur i: [contourStart][i] ..< [contourStart][i+1]). */
    private val contourStart: IntArray,
) {
    val contourCount: Int get() = contourStart.size - 1
    val segmentCount: Int get() = kinds.size
    val isEmpty: Boolean get() = kinds.isEmpty()

    /**
     * ([x], [y]) noktasına [maxDistance] içinde en yakın konturun sırası (yoksa -1). [out] varsa en yakın nokta
     * out[0], out[1] ve uzaklık out[2]'ye yazılır.
     */
    fun lock(x: Float, y: Float, maxDistance: Float, out: FloatArray? = null): Int {
        var best = -1
        var bestD = maxDistance
        var bx = 0f; var by = 0f
        val o = scratch
        for (c in 0 until contourCount) {
            if (!projectContour(c, x, y, o)) continue
            if (o[2] <= bestD) { bestD = o[2]; best = c; bx = o[0]; by = o[1] }
        }
        if (best >= 0 && out != null) { out[0] = bx; out[1] = by; out[2] = bestD }
        return best
    }

    /** ([x], [y]) noktasını [contour] konturunun en yakın noktasına düşürür: out[0], out[1] nokta, out[2] uzaklık. */
    fun project(contour: Int, x: Float, y: Float, out: FloatArray) {
        if (contour !in 0 until contourCount || !projectContour(contour, x, y, out)) {
            out[0] = x; out[1] = y; out[2] = 0f
        }
    }

    private val scratch = FloatArray(3)

    private fun projectContour(c: Int, x: Float, y: Float, out: FloatArray): Boolean {
        val from = contourStart[c]
        val to = contourStart[c + 1]
        if (from >= to) return false
        var bestD2 = Float.MAX_VALUE
        var bx = x; var by = y
        for (i in from until to) {
            val b = i * STRIDE
            var px: Float; var py: Float
            if (kinds[i] == LINE) {
                val x1 = data[b]; val y1 = data[b + 1]; val dx = data[b + 2]; val dy = data[b + 3]
                var t = ((x - x1) * dx + (y - y1) * dy) * data[b + 4]
                if (t < 0f) t = 0f else if (t > 1f) t = 1f
                px = x1 + dx * t; py = y1 + dy * t
            } else {
                val cx = data[b]; val cy = data[b + 1]; val r = data[b + 2]; val start = data[b + 3]; val sweep = data[b + 4]
                var a = atan2(y - cy, x - cx) - start
                // Açı yayın başından ileri doğru 0..2π
                while (a < 0f) a += TWO_PI
                while (a >= TWO_PI) a -= TWO_PI
                val angle = if (a <= sweep) start + a else {
                    // Yayın dışında: yakın uca
                    val pastEnd = a - sweep
                    val beforeStart = TWO_PI - a
                    if (pastEnd < beforeStart) start + sweep else start
                }
                px = cx + r * cos(angle); py = cy + r * sin(angle)
            }
            val ddx = px - x; val ddy = py - y
            val d2 = ddx * ddx + ddy * ddy
            if (d2 < bestD2) { bestD2 = d2; bx = px; by = py }
        }
        out[0] = bx; out[1] = by; out[2] = sqrt(bestD2)
        return true
    }

    /** Kenarları kuran yardımcı: parçalar eklenir, [endContour] ile kontur kapanır. */
    class Builder {
        private val kinds = ArrayList<Int>()
        private val data = ArrayList<Float>()
        private val starts = arrayListOf(0)

        fun line(x1: Float, y1: Float, x2: Float, y2: Float): Builder {
            val dx = x2 - x1; val dy = y2 - y1
            val len2 = dx * dx + dy * dy
            if (len2 < 1e-8f) return this
            kinds += LINE
            data += x1; data += y1; data += dx; data += dy; data += 1f / len2
            return this
        }

        /** Merkez, yarıçap, başlangıç açısı ve tarama (radyan; negatif tarama ters yönde aynı yaydır). */
        fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float): Builder {
            if (r <= 0f || sweep == 0f) return this
            var s = start; var w = sweep
            if (w < 0f) { s += w; w = -w }
            if (w > TWO_PI) w = TWO_PI
            kinds += ARC
            data += cx; data += cy; data += r; data += s; data += w
            return this
        }

        /** Nokta dizisi (x0, y0, x1, y1…) kırık çizgi; [closed] ise son nokta ilkine bağlanır. Kontur kapanmaz. */
        fun polyline(points: FloatArray, closed: Boolean): Builder {
            val n = points.size / 2
            for (i in 0 until n - 1) line(points[2 * i], points[2 * i + 1], points[2 * i + 2], points[2 * i + 3])
            if (closed && n > 2) line(points[2 * n - 2], points[2 * n - 1], points[0], points[1])
            return this
        }

        fun endContour(): Builder {
            if (starts.last() != kinds.size) starts += kinds.size
            return this
        }

        fun build(): SnapEdges {
            endContour()
            return SnapEdges(kinds.toIntArray(), data.toFloatArray(), starts.toIntArray())
        }
    }

    companion object {
        const val LINE = 0
        const val ARC = 1
        private const val STRIDE = 5
        private const val TWO_PI = (2 * PI).toFloat()
        val Empty: SnapEdges = Builder().build()
    }
}

/**
 * v40 çizgiyi kenara yapıştıran kaynak (çizim aracına verilir): parmağın indiği yer kenara yakınsa [lock] konturu
 * döner, sonraki noktalar [project] ile o kontura düşer.
 */
interface StrokeGuide {
    /** [captureDistance] tuval birimi; kenar yoksa -1. */
    fun lock(x: Float, y: Float, captureDistance: Float): Int
    fun project(lock: Int, x: Float, y: Float, out: FloatArray)
}

/** Sabit kenarlarla yapışma; [captureAll] (pergel) her yerden yapışır. */
class EdgesGuide(val edges: SnapEdges, private val captureAll: Boolean = false) : StrokeGuide {
    override fun lock(x: Float, y: Float, captureDistance: Float): Int =
        edges.lock(x, y, if (captureAll) Float.MAX_VALUE else captureDistance)

    override fun project(lock: Int, x: Float, y: Float, out: FloatArray) = edges.project(lock, x, y, out)
}
