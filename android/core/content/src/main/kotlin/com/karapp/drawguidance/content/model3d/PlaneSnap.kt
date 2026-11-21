package com.karapp.drawguidance.content.model3d

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v56 3B Çizim düzlemi: hazır yönler (Ön, Yan, Üst, Kameraya dönük), ekrandan düzleme izdüşüm, yapıştırma (yüze,
 * çizgi ucuna, iki çizginin kesişimine), 15° mıknatıslı döndürme ve yatıklık uyarısı. Saf Kotlin; iOS'ta
 * `PlaneSnap.swift` aynı sonuçları verir.
 */
object PlaneSnap {

    /** Yaslama adımı (derece). */
    const val STEP_DEG = 15f

    /** Bu açıdan (düzlem normali ile bakış yönü arasında) fazlası "yatık" sayılır. */
    const val TILT_WARN_DEG = 75f

    enum class Preset { FRONT, SIDE, TOP, CAMERA }

    /** Hazır yönde, [through] noktasından geçen düzlem. Kameraya dönük düzlem [view]'ın sağ ve yukarı eksenleridir. */
    fun preset(kind: Preset, through: V3 = V3.ZERO, view: View3? = null): Plane3 = when (kind) {
        Preset.FRONT -> Plane3(through, V3(1f, 0f, 0f), V3(0f, 1f, 0f), "front")
        Preset.SIDE -> Plane3(through, V3(0f, 0f, -1f), V3(0f, 1f, 0f), "side")
        Preset.TOP -> Plane3(through, V3(1f, 0f, 0f), V3(0f, 0f, -1f), "top")
        Preset.CAMERA -> {
            val v = view
            if (v == null) Plane3(through, V3(1f, 0f, 0f), V3(0f, 1f, 0f), "camera")
            else Plane3(through, v.right.normalized(), (-v.down).normalized(), "camera")
        }
    }

    /** Ekran noktasının düzlemdeki karşılığı (ışın düzleme paralelse ya da arkadaysa null). Düzlemin kenarı yoktur. */
    fun project(plane: Plane3, view: View3, sx: Float, sy: Float): V3? {
        val (o, d) = view.ray(sx, sy)
        return intersect(plane, o, d)
    }

    fun intersect(plane: Plane3, o: V3, d: V3): V3? {
        val n = plane.normal
        val den = d dot n
        if (abs(den) < 1e-5f) return null
        val t = ((plane.origin - o) dot n) / den
        if (t.isNaN() || t.isInfinite()) return null
        // Paralel izdüşümde ışın uzaktan başlar; arkada kalan kesişim de kullanılabilir
        return o + d * t
    }

    /** Düzlem normali ile bakış yönü arasındaki açı (0: tam karşıdan, 90: yandan). */
    fun tilt(plane: Plane3, view: View3, at: V3 = plane.origin): Float {
        val toEye = if (view.perspective) (view.eye - at).normalized() else view.toViewer
        val c = abs(plane.normal dot toEye).coerceIn(0f, 1f)
        return Math.toDegrees(acos(c).toDouble()).toFloat()
    }

    fun tooTilted(plane: Plane3, view: View3): Boolean = tilt(plane, view) > TILT_WARN_DEG

    /**
     * Yüze yapıştırma: [hit] yüzünün dünya normaline dik, değilen noktadan geçen düzlem. u ekseni dünya yukarısına
     * en dik yatay yön (yatay yüzlerde X) seçilir.
     */
    fun onFace(model: Model3D, hit: Hit3): Plane3 {
        val part = model.parts[hit.part]
        val fn = part.mesh.faceNormals
        val out = FloatArray(3)
        Transform3.of(part).normal(fn[3 * hit.face], fn[3 * hit.face + 1], fn[3 * hit.face + 2], out, 0)
        val n = V3(out[0], out[1], out[2]).normalized()
        return fromNormal(hit.point, n, "face:${part.id}:${hit.face}")
    }

    /** [normal]'e dik, [origin]'den geçen düzlem (u yatay, v yukarıya en yakın). */
    fun fromNormal(origin: V3, normal: V3, id: String = "custom"): Plane3 {
        val n = normal.normalized()
        val ref = if (abs(n.y) > 0.95f) V3(0f, 0f, -1f) else V3.UP
        val v0 = (ref - n * (ref dot n)).normalized()
        val u = (v0 cross n).normalized()
        return Plane3(origin, u, (n cross u).normalized(), id)
    }

    /**
     * Çizgi ucu ya da iki çizginin (ekranda) kesişimi [radius] piksel içindeyse o dünya noktası; yoksa null. Uçlar
     * kesişimlerden önce gelir.
     */
    fun snapPoint(model: Model3D, view: View3, sx: Float, sy: Float, radius: Float): V3? {
        val s = FloatArray(4)
        var best: V3? = null; var bestD = radius
        for (st in model.strokes) {
            if (st.count == 0 || model.layerHidden(st.layer)) continue
            for (i in intArrayOf(0, st.count - 1)) {
                view.project(st.points[3 * i], st.points[3 * i + 1], st.points[3 * i + 2], s)
                if (s[3] <= 0f) continue
                val d = sqrt((s[0] - sx) * (s[0] - sx) + (s[1] - sy) * (s[1] - sy))
                if (d < bestD) { bestD = d; best = V3(st.points[3 * i], st.points[3 * i + 1], st.points[3 * i + 2]) }
            }
        }
        if (best != null) return best
        return intersection(model, view, sx, sy, radius)
    }

    /** Ekranda kesişen iki çizgi parçasının, dokunuşa en yakın kesişim noktası (iki parçanın 3B orta noktası). */
    fun intersection(model: Model3D, view: View3, sx: Float, sy: Float, radius: Float): V3? {
        data class Seg(val ax: Float, val ay: Float, val bx: Float, val by: Float, val a: V3, val b: V3, val stroke: Int)
        val segs = ArrayList<Seg>()
        val pa = FloatArray(4); val pb = FloatArray(4)
        val r2 = radius * 3f
        model.strokes.forEachIndexed { si, st ->
            if (model.layerHidden(st.layer)) return@forEachIndexed
            for (i in 0 until st.count - 1) {
                view.project(st.points[3 * i], st.points[3 * i + 1], st.points[3 * i + 2], pa)
                view.project(st.points[3 * i + 3], st.points[3 * i + 4], st.points[3 * i + 5], pb)
                if (pa[3] <= 0f || pb[3] <= 0f) continue
                // Yalnız dokunuşun yakınındaki parçalar
                if (minOf(pa[0], pb[0]) > sx + r2 || maxOf(pa[0], pb[0]) < sx - r2 || minOf(pa[1], pb[1]) > sy + r2 || maxOf(pa[1], pb[1]) < sy - r2) continue
                segs += Seg(pa[0], pa[1], pb[0], pb[1],
                    V3(st.points[3 * i], st.points[3 * i + 1], st.points[3 * i + 2]),
                    V3(st.points[3 * i + 3], st.points[3 * i + 4], st.points[3 * i + 5]), si)
            }
        }
        var best: V3? = null; var bestD = radius
        for (i in segs.indices) for (j in i + 1 until segs.size) {
            val p = segs[i]; val q = segs[j]
            if (p.stroke == q.stroke) continue
            val rx = p.bx - p.ax; val ry = p.by - p.ay; val qx = q.bx - q.ax; val qy = q.by - q.ay
            val den = rx * qy - ry * qx
            if (abs(den) < 1e-6f) continue
            val t = ((q.ax - p.ax) * qy - (q.ay - p.ay) * qx) / den
            val u = ((q.ax - p.ax) * ry - (q.ay - p.ay) * rx) / den
            if (t < 0f || t > 1f || u < 0f || u > 1f) continue
            val ix = p.ax + rx * t; val iy = p.ay + ry * t
            val d = sqrt((ix - sx) * (ix - sx) + (iy - sy) * (iy - sy))
            if (d < bestD) {
                bestD = d
                val a = p.a + (p.b - p.a) * t; val b = q.a + (q.b - q.a) * u
                best = (a + b) * 0.5f
            }
        }
        return best
    }

    /** Düzlemi kendi normali boyunca [d] dünya birimi kaydırır. */
    fun offset(plane: Plane3, d: Float): Plane3 = plane.copy(origin = plane.origin + plane.normal * d)

    /**
     * Düzlemi [axis] çevresinde (0: u ekseni, 1: v ekseni, 2: normal) [deg] derece döndürür; [snap] açıkken sonuç
     * açı 15°'nin katına yuvarlanır (dünya eksenlerine göre değil, döndürme miktarı).
     */
    fun rotate(plane: Plane3, axis: Int, deg: Float, snap: Boolean = true): Plane3 {
        val a = if (snap) snapAngle(deg) else deg
        if (a == 0f) return plane
        val k = when (axis) { 0 -> plane.u; 1 -> plane.v; else -> plane.normal }.normalized()
        return plane.copy(u = rotateAround(plane.u, k, a).normalized(), v = rotateAround(plane.v, k, a).normalized())
    }

    /** Açıyı 15°'nin en yakın katına yuvarlar. */
    fun snapAngle(deg: Float): Float = (deg / STEP_DEG).roundToInt() * STEP_DEG

    /** Rodrigues döndürmesi. */
    fun rotateAround(v: V3, k: V3, deg: Float): V3 {
        val r = Math.toRadians(deg.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        return v * c + (k cross v) * s + k * ((k dot v) * (1f - c))
    }

    /** Kameraya çevir: düzlemin merkezi korunur, eksenler kameranın sağı ve yukarısı olur. */
    fun faceCamera(plane: Plane3, view: View3): Plane3 = preset(Preset.CAMERA, plane.origin, view)

    /** Düzlemin ızgara çizgileri ([half] yarı kenar, [step] aralık): (başlangıç, bitiş) dünya noktaları. */
    fun grid(plane: Plane3, half: Float, step: Float): List<Pair<V3, V3>> {
        val n = (half / step).toInt().coerceIn(1, 60)
        val out = ArrayList<Pair<V3, V3>>(4 * n + 2)
        for (i in -n..n) {
            val t = i * step
            out += plane.world(t, -half) to plane.world(t, half)
            out += plane.world(-half, t) to plane.world(half, t)
        }
        return out
    }
}
