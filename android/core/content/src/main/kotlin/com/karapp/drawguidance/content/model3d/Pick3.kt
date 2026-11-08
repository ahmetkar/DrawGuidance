package com.karapp.drawguidance.content.model3d

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Işının değdiği yer: parça sırası, yüz, ışın parametresi ve dünya noktası. */
data class Hit3(val part: Int, val face: Int, val t: Float, val point: V3)

/** Ekranda seçilen köşe/kenar/çizgi. */
data class ScreenPick(val part: Int, val index: Int, val distance: Float, val visible: Boolean)

/**
 * v24 3B seçim (çiziciden bağımsız): ekran noktasından geçen ışın parçaların üçgenleriyle kesiştirilir (ekran kartıyla
 * çizilen sahnede de, yazılım çizicide de aynı sonuç). Köşe ve kenar seçiminde en yakın ekran mesafesi alınır;
 * önünde başka yüzey olanlar "görünmez" sayılır (görünür olan tercih edilir).
 */
object Pick3 {

    /** [view] ekranındaki ([sx], [sy]) noktadan geçen ışının en yakın parçası. */
    fun pick(model: Model3D, view: View3, sx: Float, sy: Float): Hit3? {
        val (o, d) = view.ray(sx, sy)
        return ray(model, o, d)
    }

    /** Işının (o + t·d) değdiği en yakın görünür parça. */
    fun ray(model: Model3D, o: V3, d: V3): Hit3? {
        var best: Hit3? = null
        model.parts.forEachIndexed { pi, part ->
            if (!part.visible || part.mesh.faceCount == 0) return@forEachIndexed
            val hit = rayPart(part, o, d) ?: return@forEachIndexed
            if (best == null || hit.second < best!!.t) best = Hit3(pi, hit.first, hit.second, o + d * hit.second)
        }
        return best
    }

    /** Parçanın üçgenleriyle kesişim: (yüz, t). */
    fun rayPart(part: Part, o: V3, d: V3): Pair<Int, Float>? {
        val inv = Inverse3.of(Transform3.of(part)) ?: return null
        val lo = inv.point(o); val ld = inv.dir(d)
        val box = part.mesh.bounds
        if (!slab(lo, ld, box)) return null
        val m = part.mesh
        val p = m.pos
        var bestT = Float.POSITIVE_INFINITY; var bestF = -1
        for (f in 0 until m.faceCount) {
            val face = m.faces[f]
            val tris = m.triangles[f]
            var k = 0
            while (k + 2 < tris.size) {
                val a = face[tris[k]] * 3; val b = face[tris[k + 1]] * 3; val c = face[tris[k + 2]] * 3
                val t = triangle(lo, ld, p[a], p[a + 1], p[a + 2], p[b], p[b + 1], p[b + 2], p[c], p[c + 1], p[c + 2])
                if (t > 1e-5f && t < bestT) { bestT = t; bestF = f }
                k += 3
            }
        }
        return if (bestF >= 0) bestF to bestT else null
    }

    /** Möller–Trumbore (iki yüzlü): değmiyorsa −1. */
    private fun triangle(
        o: V3, d: V3,
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float,
    ): Float {
        val e1x = bx - ax; val e1y = by - ay; val e1z = bz - az
        val e2x = cx - ax; val e2y = cy - ay; val e2z = cz - az
        val px = d.y * e2z - d.z * e2y; val py = d.z * e2x - d.x * e2z; val pz = d.x * e2y - d.y * e2x
        val det = e1x * px + e1y * py + e1z * pz
        if (abs(det) < 1e-12f) return -1f
        val inv = 1f / det
        val tx = o.x - ax; val ty = o.y - ay; val tz = o.z - az
        val u = (tx * px + ty * py + tz * pz) * inv
        if (u < -1e-6f || u > 1f + 1e-6f) return -1f
        val qx = ty * e1z - tz * e1y; val qy = tz * e1x - tx * e1z; val qz = tx * e1y - ty * e1x
        val v = (d.x * qx + d.y * qy + d.z * qz) * inv
        if (v < -1e-6f || u + v > 1f + 1e-6f) return -1f
        return (e2x * qx + e2y * qy + e2z * qz) * inv
    }

    private fun slab(o: V3, d: V3, b: Box3): Boolean {
        var t0 = Float.NEGATIVE_INFINITY; var t1 = Float.POSITIVE_INFINITY
        val pad = max(1e-4f, b.size.length * 0.01f)
        for (axis in 0..2) {
            val oo = o.get(axis); val dd = d.get(axis)
            val lo = b.min.get(axis) - pad; val hi = b.max.get(axis) + pad
            if (abs(dd) < 1e-12f) {
                if (oo < lo || oo > hi) return false
            } else {
                var a = (lo - oo) / dd; var c = (hi - oo) / dd
                if (a > c) { val s = a; a = c; c = s }
                t0 = max(t0, a); t1 = min(t1, c)
                if (t0 > t1) return false
            }
        }
        return t1 > 0f
    }

    /** Dünya noktası ekranda görünüyor mu (göz ile nokta arasında başka yüzey yok). */
    fun visible(model: Model3D, view: View3, p: V3, ignorePart: Int = -1): Boolean {
        val s = view.project(p)
        if (s[3] <= 0f) return false
        val (o, d) = view.ray(s[0], s[1])
        val tp = (p - o) dot d
        if (tp <= 0f) return false
        val eps = max(1e-3f, tp * 0.004f)
        model.parts.forEachIndexed { pi, part ->
            if (!part.visible || pi == ignorePart) return@forEachIndexed
            val hit = rayPart(part, o, d) ?: return@forEachIndexed
            if (hit.second < tp - eps) return false
        }
        // Kendi parçasının önündeki yüzleri de say (köşe arkada kalmış olabilir)
        if (ignorePart >= 0) {
            val hit = rayPart(model.parts[ignorePart], o, d)
            if (hit != null && hit.second < tp - eps) return false
        }
        return true
    }

    /** Parçanın köşelerinin dünya konumları. */
    fun worldVertices(part: Part): FloatArray {
        val t = Transform3.of(part)
        val n = part.mesh.vertexCount
        val out = FloatArray(n * 3)
        val p = part.mesh.pos
        for (i in 0 until n) t.apply(p[3 * i], p[3 * i + 1], p[3 * i + 2], out, 3 * i)
        return out
    }

    /** Ekrana en yakın köşe ([radius] piksel içinde; görünür olan önce). */
    fun vertex(model: Model3D, view: View3, partIndex: Int, sx: Float, sy: Float, radius: Float): ScreenPick? {
        val part = model.parts.getOrNull(partIndex) ?: return null
        val w = worldVertices(part)
        val s = FloatArray(4)
        val cands = ArrayList<Pair<Int, Float>>()
        for (i in 0 until part.mesh.vertexCount) {
            view.project(w[3 * i], w[3 * i + 1], w[3 * i + 2], s)
            if (s[3] <= 0f) continue
            val dd = sqrt((s[0] - sx) * (s[0] - sx) + (s[1] - sy) * (s[1] - sy))
            if (dd <= radius) cands += i to dd
        }
        // Ekranda üst üste düşenlerde (1,5 piksel içinde) bakana yakın olan önce
        cands.sortWith(compareBy<Pair<Int, Float>> { (it.second / 1.5f).toInt() }.thenByDescending { view.depth(w[3 * it.first], w[3 * it.first + 1], w[3 * it.first + 2]) })
        var hidden: ScreenPick? = null
        for ((i, dd) in cands.take(8)) {
            val p = V3(w[3 * i], w[3 * i + 1], w[3 * i + 2])
            if (visible(model, view, p, partIndex)) return ScreenPick(partIndex, i, dd, true)
            if (hidden == null) hidden = ScreenPick(partIndex, i, dd, false)
        }
        return hidden
    }

    /** Ekrana en yakın kenar ([radius] piksel içinde; görünür olan önce): [ScreenPick.index] kenar listesindeki sıra. */
    fun edge(model: Model3D, view: View3, partIndex: Int, sx: Float, sy: Float, radius: Float): Pair<EdgeRef, ScreenPick>? {
        val part = model.parts.getOrNull(partIndex) ?: return null
        val w = worldVertices(part)
        val e = part.mesh.edges
        val sa = FloatArray(4); val sb = FloatArray(4)
        val cands = ArrayList<Triple<Int, Float, Float>>()
        var i = 0
        while (i < e.size) {
            val a = e[i]; val b = e[i + 1]
            view.project(w[3 * a], w[3 * a + 1], w[3 * a + 2], sa)
            view.project(w[3 * b], w[3 * b + 1], w[3 * b + 2], sb)
            if (sa[3] > 0f && sb[3] > 0f) {
                val dx = sb[0] - sa[0]; val dy = sb[1] - sa[1]
                val len2 = dx * dx + dy * dy
                val t = if (len2 < 1e-6f) 0f else (((sx - sa[0]) * dx + (sy - sa[1]) * dy) / len2).coerceIn(0f, 1f)
                val px = sa[0] + dx * t; val py = sa[1] + dy * t
                val dd = sqrt((px - sx) * (px - sx) + (py - sy) * (py - sy))
                if (dd <= radius) cands += Triple(i / 4, dd, t)
            }
            i += 4
        }
        fun depthOf(k: Int): Float {
            val a = e[4 * k]; val b = e[4 * k + 1]
            return view.depth((w[3 * a] + w[3 * b]) / 2f, (w[3 * a + 1] + w[3 * b + 1]) / 2f, (w[3 * a + 2] + w[3 * b + 2]) / 2f)
        }
        cands.sortWith(compareBy<Triple<Int, Float, Float>> { (it.second / 1.5f).toInt() }.thenByDescending { depthOf(it.first) })
        var hidden: Pair<EdgeRef, ScreenPick>? = null
        for ((k, dd, t) in cands.take(8)) {
            val a = e[4 * k]; val b = e[4 * k + 1]
            val p = V3(w[3 * a] + (w[3 * b] - w[3 * a]) * t, w[3 * a + 1] + (w[3 * b + 1] - w[3 * a + 1]) * t, w[3 * a + 2] + (w[3 * b + 2] - w[3 * a + 2]) * t)
            val ref = EdgeRef(a, b)
            if (visible(model, view, p, partIndex)) return ref to ScreenPick(partIndex, k, dd, true)
            if (hidden == null) hidden = ref to ScreenPick(partIndex, k, dd, false)
        }
        return hidden
    }

    /** Ekrandaki noktanın kenar üzerindeki oranı (a'dan b'ye; nokta eklerken). */
    fun edgeParam(model: Model3D, view: View3, partIndex: Int, edge: EdgeRef, sx: Float, sy: Float): Float {
        val part = model.parts.getOrNull(partIndex) ?: return 0.5f
        val t = Transform3.of(part)
        val pa = t.apply(part.mesh.vertex(edge.a)); val pb = t.apply(part.mesh.vertex(edge.b))
        val sa = view.project(pa); val sb = view.project(pb)
        val dx = sb[0] - sa[0]; val dy = sb[1] - sa[1]
        val len2 = dx * dx + dy * dy
        return if (len2 < 1e-6f) 0.5f else (((sx - sa[0]) * dx + (sy - sa[1]) * dy) / len2).coerceIn(0.05f, 0.95f)
    }

    /** Ekrana en yakın 3B çizgi ([radius] piksel içinde): çizginin sırası. */
    fun stroke(model: Model3D, view: View3, sx: Float, sy: Float, radius: Float): Int? {
        val s = FloatArray(4)
        var best = -1; var bestD = radius
        model.strokes.forEachIndexed { si, st ->
            for (i in 0 until st.count) {
                view.project(st.points[3 * i], st.points[3 * i + 1], st.points[3 * i + 2], s)
                if (s[3] <= 0f) continue
                val dd = sqrt((s[0] - sx) * (s[0] - sx) + (s[1] - sy) * (s[1] - sy))
                if (dd < bestD) { bestD = dd; best = si }
            }
        }
        return best.takeIf { it >= 0 }
    }
}

/** Parça dönüşümünün tersi (dünyadan nesne uzayına). */
class Inverse3 private constructor(private val a: FloatArray, private val t: FloatArray) {
    fun point(p: V3): V3 {
        val x = p.x - t[0]; val y = p.y - t[1]; val z = p.z - t[2]
        return V3(a[0] * x + a[1] * y + a[2] * z, a[3] * x + a[4] * y + a[5] * z, a[6] * x + a[7] * y + a[8] * z)
    }

    fun dir(d: V3): V3 = V3(a[0] * d.x + a[1] * d.y + a[2] * d.z, a[3] * d.x + a[4] * d.y + a[5] * d.z, a[6] * d.x + a[7] * d.y + a[8] * d.z)

    companion object {
        fun of(tr: Transform3): Inverse3? {
            val m = tr.m
            val a = m[0]; val b = m[1]; val c = m[2]
            val d = m[4]; val e = m[5]; val f = m[6]
            val g = m[8]; val h = m[9]; val i = m[10]
            val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
            if (abs(det) < 1e-12f) return null
            val k = 1f / det
            val inv = floatArrayOf(
                (e * i - f * h) * k, (c * h - b * i) * k, (b * f - c * e) * k,
                (f * g - d * i) * k, (a * i - c * g) * k, (c * d - a * f) * k,
                (d * h - e * g) * k, (b * g - a * h) * k, (a * e - b * d) * k,
            )
            return Inverse3(inv, floatArrayOf(m[3], m[7], m[11]))
        }
    }
}
