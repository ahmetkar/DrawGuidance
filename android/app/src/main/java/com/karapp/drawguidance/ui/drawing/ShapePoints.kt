package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.model.Stroke
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Şekil düzenlemede nokta düzenleme (kalem aracı mantığı): her parçanın belirgin noktaları (köşeler, eğrinin dönüm
 * noktaları) tutamak olarak gösterilir. Tutamak sürüklenince yanındaki tutamaklara kadar olan noktalar yumuşak
 * bir geçişle birlikte gelir (eğriler kırılmaz); parçanın üstüne dokunmak oraya nokta ekler, noktaya çift dokunmak
 * onu siler.
 */
object ShapePoints {

    /** Bir tutamak: [path] parçasının [index] noktası. */
    data class Key(val path: Int, val index: Int, val point: Offset)

    /** Parçanın belirgin noktaları (Ramer–Douglas–Peucker; az noktalı parçalarda hepsi). */
    fun keys(stroke: Stroke, tolerance: Float): List<Key> {
        val out = ArrayList<Key>()
        ShapeFactory.subPaths(stroke).forEachIndexed { p, path ->
            if (path.label != null || path.points.size < 2 || path.tag == "frame") return@forEachIndexed
            keyIndices(path.points, path.closed, tolerance).forEach { i -> out += Key(p, i, path.points[i]) }
        }
        return out
    }

    fun keyIndices(points: List<Offset>, closed: Boolean, tolerance: Float): List<Int> {
        if (points.size <= 24) return points.indices.toList()
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        simplify(points, 0, points.size - 1, max(tolerance, 0.5f), keep)
        // Uzun düz kısımlarda da tutulacak ara noktalar (çok seyrek kalmasın)
        val result = keep.indices.filter { keep[it] }.toMutableList()
        if (closed && result.size > 1 && hypot(points.first().x - points.last().x, points.first().y - points.last().y) < 0.5f) {
            result.removeAt(result.size - 1)
        }
        return result
    }

    private fun simplify(points: List<Offset>, a: Int, b: Int, eps: Float, keep: BooleanArray) {
        if (b <= a + 1) return
        var maxD = -1f
        var idx = -1
        val pa = points[a]; val pb = points[b]
        for (i in a + 1 until b) {
            val d = segmentDistance(points[i], pa, pb)
            if (d > maxD) { maxD = d; idx = i }
        }
        if (maxD > eps && idx >= 0) {
            keep[idx] = true
            simplify(points, a, idx, eps, keep)
            simplify(points, idx, b, eps, keep)
        }
    }

    /** [at] noktasına en yakın tutamak ([tolerance] içinde). */
    fun keyAt(stroke: Stroke, at: Offset, tolerance: Float, keyTolerance: Float): Key? =
        keys(stroke, keyTolerance).filter { (it.point - at).getDistance() <= tolerance }.minByOrNull { (it.point - at).getDistance() }

    /**
     * Tutamağı [delta] kadar taşır; önceki ve sonraki tutamağa kadar olan noktalar uzaklıkla azalan oranda gelir.
     */
    fun move(stroke: Stroke, key: Key, delta: Offset, keyTolerance: Float): Stroke? {
        val paths = ShapeFactory.subPaths(stroke).toMutableList()
        val path = paths.getOrNull(key.path) ?: return null
        val pts = path.points
        val keys = keyIndices(pts, path.closed, keyTolerance)
        val k = keys.indexOf(key.index).takeIf { it >= 0 } ?: return null
        val n = pts.size
        val out = pts.toMutableList()
        out[key.index] = pts[key.index] + delta
        if (keys.size < n) {
            // Önceki tutamağa doğru
            val prev = if (k > 0) keys[k - 1] else if (path.closed) keys.last() - n else 0
            val next = if (k < keys.size - 1) keys[k + 1] else if (path.closed) keys.first() + n else n - 1
            var j = key.index - 1
            while (j > prev) {
                val w = (j - prev).toFloat() / (key.index - prev)
                val idx = ((j % n) + n) % n
                out[idx] = pts[idx] + delta * w
                j--
            }
            j = key.index + 1
            while (j < next) {
                val w = (next - j).toFloat() / (next - key.index)
                val idx = j % n
                out[idx] = pts[idx] + delta * w
                j++
            }
        }
        paths[key.path] = path.copy(points = out)
        return ShapeFactory.fromEditable(stroke, paths)
    }

    /** Parçanın üstündeki [at] noktasına yeni nokta ekler (en yakın kenara). */
    fun insert(stroke: Stroke, at: Offset, tolerance: Float): Stroke? {
        val paths = ShapeFactory.subPaths(stroke).toMutableList()
        var best: Triple<Int, Int, Offset>? = null
        var bestD = tolerance
        paths.forEachIndexed { p, path ->
            if (path.label != null) return@forEachIndexed
            val pts = if (path.closed) path.points + path.points.first() else path.points
            for (i in 1 until pts.size) {
                val proj = project(at, pts[i - 1], pts[i])
                val d = (proj - at).getDistance()
                if (d < bestD) { bestD = d; best = Triple(p, i, proj) }
            }
        }
        val (p, i, proj) = best ?: return null
        val path = paths[p]
        val list = path.points.toMutableList()
        list.add(i.coerceAtMost(list.size), proj)
        paths[p] = path.copy(points = list, nodes = null)
        return ShapeFactory.fromEditable(stroke, paths)
    }

    /** Tutamağı siler (parçada en az 2 nokta, kapalıysa 3 nokta kalır). */
    fun delete(stroke: Stroke, key: Key): Stroke? {
        val paths = ShapeFactory.subPaths(stroke).toMutableList()
        val path = paths.getOrNull(key.path) ?: return null
        val min = if (path.closed) 3 else 2
        if (path.points.size <= min) return null
        paths[key.path] = path.copy(points = path.points.filterIndexed { i, _ -> i != key.index }, nodes = null)
        return ShapeFactory.fromEditable(stroke, paths)
    }

    private fun project(p: Offset, a: Offset, b: Offset): Offset {
        val vx = b.x - a.x; val vy = b.y - a.y
        val len2 = vx * vx + vy * vy
        val t = if (len2 < 1e-6f) 0f else (((p.x - a.x) * vx + (p.y - a.y) * vy) / len2).coerceIn(0f, 1f)
        return Offset(a.x + vx * t, a.y + vy * t)
    }

    private fun segmentDistance(p: Offset, a: Offset, b: Offset): Float = (project(p, a, b) - p).getDistance()

    /** Yardımcı: iki nokta çok yakın mı. */
    fun near(a: Offset, b: Offset, tolerance: Float) = abs(a.x - b.x) <= tolerance && abs(a.y - b.y) <= tolerance
}
