package com.karapp.drawguidance.data.model

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v56 aşama 6 Dönüştür paneli geometrisi (planın 2A bölümü; saf, iOS `V56Transform.swift` ile aynı):
 * homografi (Bozma), ağ enterpolasyonu (Çarpıtma), yaslama, çevirme/döndürme/sığdırma ve dönüşüm durumu.
 *
 * Dönüşüm durumu tek bir dörtgen ([TransformState.quad]) ya da ağdır ([TransformState.mesh]): Serbest, Tekdüze ve
 * Bozma'da seçimin sınır kutusu (başlangıç dörtgeni) → güncel dörtgen homografisi; Çarpıtma'da sınır kutusundaki
 * düz ağ → güncel ağ (Catmull-Rom yüzeyi, ağ çizgileri eğri olarak bükülür).
 */
/** [SHEAR] (2E 2.7–2.8, yalnız Resim): kenar paralel kayar, köşe paralelkenarı kendi eksenlerinde ölçekler. */
enum class TransformKind { FREE, UNIFORM, DISTORT, WARP, SHEAR }

/** Resim ara değerlemesi (planın 2A "Ara değerleme"): ImageWarp.filter kodları. */
enum class TransformFilter(val code: Int) { NEAREST(0), BILINEAR(1), BICUBIC(2);
    companion object { fun of(code: Int): TransformFilter = entries.firstOrNull { it.code == code } ?: BILINEAR }
}

object Homography {
    /**
     * [src] dört noktayı (x0,y0,…,x3,y3) [dst]'ye eşleyen 3×3 matris (satır satır, h[8] = 1); tekil durumda null.
     */
    fun solve(src: FloatArray, dst: FloatArray): FloatArray? {
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = src[i * 2].toDouble(); val y = src[i * 2 + 1].toDouble()
            val u = dst[i * 2].toDouble(); val v = dst[i * 2 + 1].toDouble()
            a[i * 2] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[i * 2 + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        // Kısmi pivotlu Gauss eleme
        for (c in 0 until 8) {
            var p = c
            for (r in c + 1 until 8) if (abs(a[r][c]) > abs(a[p][c])) p = r
            if (abs(a[p][c]) < 1e-12) return null
            val t = a[c]; a[c] = a[p]; a[p] = t
            for (r in 0 until 8) if (r != c) {
                val f = a[r][c] / a[c][c]
                if (f != 0.0) for (k in c until 9) a[r][k] -= f * a[c][k]
            }
        }
        return FloatArray(9) { if (it == 8) 1f else (a[it][8] / a[it][it]).toFloat() }
    }

    fun map(h: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        val w = h[6] * x + h[7] * y + h[8]
        val ww = if (abs(w) < 1e-9f) 1e-9f else w
        return (h[0] * x + h[1] * y + h[2]) / ww to (h[3] * x + h[4] * y + h[5]) / ww
    }

    /** Dörtgen dışbükey ve köşe sırası korunmuş mu (katlanmış dörtgen homografide tersler). */
    fun convex(q: List<StrokePoint>): Boolean {
        if (q.size != 4) return false
        var sign = 0
        for (i in 0 until 4) {
            val a = q[i]; val b = q[(i + 1) % 4]; val c = q[(i + 2) % 4]
            val z = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val s = if (z > 0f) 1 else if (z < 0f) -1 else 0
            if (s != 0) { if (sign == 0) sign = s else if (s != sign) return false }
        }
        return sign != 0
    }
}

object WarpMesh {
    /** Plan: 3×3 hücre (4×4 nokta); "Gelişmiş ağ" 5×5 hücre (6×6 nokta). Sıvılaştır ince ağ kullanır. */
    const val BASIC = 4
    const val ADVANCED = 6
    const val LIQUIFY = 17

    fun sizeOf(mesh: List<StrokePoint>): Int = sqrt(mesh.size.toDouble()).roundToInt().takeIf { it >= 2 && it * it == mesh.size } ?: 0

    /** Dikdörtgende düz ağ ([n]×[n] nokta, satır satır). */
    fun identity(l: Float, t: Float, r: Float, b: Float, n: Int): List<StrokePoint> =
        List(n * n) { i -> StrokePoint(l + (r - l) * (i % n) / (n - 1), t + (b - t) * (i / n) / (n - 1)) }

    /** Dörtgende (sol-üstten saat yönünde) düz ağ: iki doğrulu ara değerleme. */
    fun onQuad(q: List<StrokePoint>, n: Int): List<StrokePoint> = List(n * n) { i ->
        val u = (i % n).toFloat() / (n - 1); val v = (i / n).toFloat() / (n - 1)
        val tx = q[0].x + (q[1].x - q[0].x) * u; val ty = q[0].y + (q[1].y - q[0].y) * u
        val bx = q[3].x + (q[2].x - q[3].x) * u; val by = q[3].y + (q[2].y - q[3].y) * u
        StrokePoint(tx + (bx - tx) * v, ty + (by - ty) * v)
    }

    private fun cr(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t; val t3 = t2 * t
        return 0.5f * (2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3)
    }

    /** Ağ yüzeyinde (u, v) ∈ [0,1]² noktası (Catmull-Rom; kenarda doğrusal uzatma, düz ağda kimlik). */
    fun eval(mesh: List<StrokePoint>, n: Int, u: Float, v: Float): Pair<Float, Float> {
        fun at(ix: Int, iy: Int): StrokePoint {
            // Kenar dışı: doğrusal uzatma (düz ağda doğrusal kalır)
            val cx = ix.coerceIn(0, n - 1); val cy = iy.coerceIn(0, n - 1)
            val p = mesh[cy * n + cx]
            if (cx == ix && cy == iy) return p
            val qx = (2 * cx - ix).coerceIn(0, n - 1); val qy = (2 * cy - iy).coerceIn(0, n - 1)
            val q = mesh[qy * n + qx]
            return StrokePoint(2 * p.x - q.x, 2 * p.y - q.y)
        }
        val fu = (u.coerceIn(0f, 1f) * (n - 1)); val fv = (v.coerceIn(0f, 1f) * (n - 1))
        val iu = min(fu.toInt(), n - 2); val iv = min(fv.toInt(), n - 2)
        val tu = fu - iu; val tv = fv - iv
        var x = 0f; var y = 0f
        val rx = FloatArray(4); val ry = FloatArray(4)
        for (k in 0 until 4) {
            val row = iv - 1 + k
            val a = at(iu - 1, row); val b = at(iu, row); val c = at(iu + 1, row); val d = at(iu + 2, row)
            rx[k] = cr(a.x, b.x, c.x, d.x, tu); ry[k] = cr(a.y, b.y, c.y, d.y, tu)
        }
        x = cr(rx[0], rx[1], rx[2], rx[3], tv); y = cr(ry[0], ry[1], ry[2], ry[3], tv)
        return x to y
    }

    /** Sınır kutusundaki noktanın ağdaki yeri. */
    fun map(mesh: List<StrokePoint>, n: Int, l: Float, t: Float, r: Float, b: Float, x: Float, y: Float): Pair<Float, Float> =
        eval(mesh, n, if (r > l) (x - l) / (r - l) else 0f, if (b > t) (y - t) / (b - t) else 0f)

    /** Ağı [m]×[m] noktaya yeniden örnekler (yüzey aynı kalır). */
    fun resample(mesh: List<StrokePoint>, n: Int, m: Int): List<StrokePoint> = List(m * m) { i ->
        eval(mesh, n, (i % m).toFloat() / (m - 1), (i / m).toFloat() / (m - 1)).let { (x, y) -> StrokePoint(x, y) }
    }

    fun movePoint(mesh: List<StrokePoint>, index: Int, x: Float, y: Float): List<StrokePoint> =
        mesh.mapIndexed { i, p -> if (i == index) StrokePoint(x, y) else p }

    /** Ağın içinden sürükleme: yakın noktalar yumuşak ağırlıkla ([radius]) kayar. */
    fun smoothDrag(mesh: List<StrokePoint>, px: Float, py: Float, dx: Float, dy: Float, radius: Float): List<StrokePoint> {
        val r2 = max(1f, radius * radius)
        return mesh.map { p ->
            val d2 = (p.x - px) * (p.x - px) + (p.y - py) * (p.y - py)
            val w = exp(-d2 / r2 * 2f)
            if (w < 1e-3f) p else StrokePoint(p.x + dx * w, p.y + dy * w)
        }
    }

    /** Sıvılaştır fırçaları: it, döndür, büzüştür, şişir; [strength] 0..1, [radius] tuval px. */
    enum class Liquify { PUSH, TWIRL, PINCH, BLOAT }

    fun liquify(
        mesh: List<StrokePoint>, mode: Liquify, cx: Float, cy: Float, dx: Float, dy: Float, radius: Float, strength: Float,
    ): List<StrokePoint> {
        val r = max(1f, radius)
        val s = strength.coerceIn(0f, 1f)
        return mesh.map { p ->
            val ox = p.x - cx; val oy = p.y - cy
            val d = hypot(ox, oy)
            if (d >= r) return@map p
            val f = (1f - d / r).let { it * it } * s
            when (mode) {
                Liquify.PUSH -> StrokePoint(p.x + dx * f, p.y + dy * f)
                Liquify.TWIRL -> {
                    val a = 0.35f * f
                    StrokePoint(cx + ox * cos(a) - oy * sin(a), cy + ox * sin(a) + oy * cos(a))
                }
                Liquify.PINCH -> StrokePoint(p.x - ox * 0.2f * f, p.y - oy * 0.2f * f)
                Liquify.BLOAT -> StrokePoint(p.x + ox * 0.2f * f, p.y + oy * 0.2f * f)
            }
        }
    }
}

object Snapping {
    const val ANGLE_STEP = 15f

    /** Mıknatıs: açı [step]'in katına (yalnız [tolerance] derece yakınsa). */
    fun angle(degrees: Float, step: Float = ANGLE_STEP, tolerance: Float = step / 2f): Float {
        val k = (degrees / step).roundToInt() * step
        return if (abs(degrees - k) <= tolerance) k else degrees
    }

    /** Değeri hedeflerden en yakınına (en çok [distance]) yaslar; yoksa null. */
    fun nearest(value: Float, targets: List<Float>, distance: Float): Float? =
        targets.minByOrNull { abs(it - value) }?.takeIf { abs(it - value) <= distance }

    /** Hızlı sürüklemede yaslama devre dışı ([speed], [threshold]: px/sn). */
    fun active(speed: Float, threshold: Float): Boolean = threshold <= 0f || speed <= threshold

    /** Sınır kutusunun kenar ve merkezleri (x: sol, orta, sağ; y: üst, orta, alt). */
    fun edges(l: Float, t: Float, r: Float, b: Float): Pair<List<Float>, List<Float>> =
        listOf(l, (l + r) / 2f, r) to listOf(t, (t + b) / 2f, b)

    /**
     * Taşınan kutunun kenarlarını hedeflere yaslar: (dx düzeltmesi, dy düzeltmesi, kılavuz x, kılavuz y).
     */
    fun snapBox(l: Float, t: Float, r: Float, b: Float, xs: List<Float>, ys: List<Float>, distance: Float): SnapResult {
        val (bx, by) = edges(l, t, r, b)
        var bestX: Pair<Float, Float>? = null
        for (e in bx) nearest(e, xs, distance)?.let { g -> if (bestX == null || abs(g - e) < abs(bestX!!.first)) bestX = (g - e) to g }
        var bestY: Pair<Float, Float>? = null
        for (e in by) nearest(e, ys, distance)?.let { g -> if (bestY == null || abs(g - e) < abs(bestY!!.first)) bestY = (g - e) to g }
        return SnapResult(bestX?.first ?: 0f, bestY?.first ?: 0f, bestX?.second, bestY?.second)
    }

    data class SnapResult(val dx: Float, val dy: Float, val guideX: Float?, val guideY: Float?)
}

/** Dönüşüm durumu: başlangıç sınır kutusu ([l], [t], [r], [b]), güncel dörtgen ve (Çarpıtma'da) ağ. */
data class TransformState(
    val kind: TransformKind,
    val l: Float, val t: Float, val r: Float, val b: Float,
    val quad: List<StrokePoint>,
    val mesh: List<StrokePoint>? = null,
    val filter: TransformFilter = TransformFilter.BILINEAR,
) {
    val box: List<StrokePoint> get() = listOf(StrokePoint(l, t), StrokePoint(r, t), StrokePoint(r, b), StrokePoint(l, b))
    val isIdentity: Boolean get() = mesh?.let { m -> m == WarpMesh.identity(l, t, r, b, WarpMesh.sizeOf(m)) } ?: (quad == box)

    companion object {
        fun start(l: Float, t: Float, r: Float, b: Float, kind: TransformKind = TransformKind.UNIFORM): TransformState {
            // Çok ince seçim (tek çizgi) için en az 1 px
            val rr = if (r - l < 1f) l + 1f else r
            val bb = if (b - t < 1f) t + 1f else b
            val box = listOf(StrokePoint(l, t), StrokePoint(rr, t), StrokePoint(rr, bb), StrokePoint(l, bb))
            return TransformState(kind, l, t, rr, bb, box, if (kind == TransformKind.WARP) WarpMesh.identity(l, t, rr, bb, WarpMesh.BASIC) else null)
        }
    }
}

/** Dönüşüm işlemleri (saf): durum → yeni durum; ve noktaları eşleme. */
object TransformOps {
    private fun center(q: List<StrokePoint>): Pair<Float, Float> = q.sumOf { it.x.toDouble() }.toFloat() / q.size to q.sumOf { it.y.toDouble() }.toFloat() / q.size

    /** Dörtgenin açısı (derece, üst kenarın yönü). */
    fun angleOf(q: List<StrokePoint>): Float = Math.toDegrees(atan2((q[1].y - q[0].y).toDouble(), (q[1].x - q[0].x).toDouble())).toFloat()

    fun widthOf(q: List<StrokePoint>): Float = hypot(q[1].x - q[0].x, q[1].y - q[0].y)
    fun heightOf(q: List<StrokePoint>): Float = hypot(q[3].x - q[0].x, q[3].y - q[0].y)

    /** Kip değiştir: Çarpıtma'ya geçince güncel dörtgende düz ağ; ağdan çıkınca ağın dış köşeleri dörtgen olur. */
    fun setKind(s: TransformState, kind: TransformKind, meshSize: Int = WarpMesh.BASIC): TransformState = when {
        kind == TransformKind.WARP -> s.copy(kind = kind, mesh = s.mesh?.takeIf { WarpMesh.sizeOf(it) == meshSize } ?: WarpMesh.onQuad(s.quad, meshSize))
        s.mesh != null -> {
            val m = s.mesh; val n = WarpMesh.sizeOf(m)
            s.copy(kind = kind, mesh = null, quad = listOf(m[0], m[n - 1], m[n * n - 1], m[n * (n - 1)]))
        }
        else -> s.copy(kind = kind)
    }

    private fun mapAll(s: TransformState, f: (Float, Float) -> Pair<Float, Float>): TransformState =
        s.copy(quad = s.quad.map { f(it.x, it.y).let { (x, y) -> StrokePoint(x, y) } }, mesh = s.mesh?.map { f(it.x, it.y).let { (x, y) -> StrokePoint(x, y) } })

    fun translate(s: TransformState, dx: Float, dy: Float): TransformState = mapAll(s) { x, y -> (x + dx) to (y + dy) }

    /** Merkez etrafında döndür (derece, saat yönünde). */
    fun rotate(s: TransformState, degrees: Float, pivot: Pair<Float, Float>? = null): TransformState {
        val (cx, cy) = pivot ?: center(s.quad)
        val a = Math.toRadians(degrees.toDouble()); val c = cos(a).toFloat(); val sn = sin(a).toFloat()
        return mapAll(s) { x, y -> (cx + (x - cx) * c - (y - cy) * sn) to (cy + (x - cx) * sn + (y - cy) * c) }
    }

    /** Döndürme tutamacı: dörtgenin açısını [degrees] yapar (mıknatısla 15° katları). */
    fun rotateTo(s: TransformState, degrees: Float, magnet: Boolean, pivot: Pair<Float, Float>? = null): TransformState {
        val target = if (magnet) Snapping.angle(degrees) else degrees
        return rotate(s, target - angleOf(s.quad), pivot)
    }

    /** Yatay ya da dikey çevir (dörtgenin kendi ekseninde, merkezine göre). */
    fun flip(s: TransformState, horizontal: Boolean, pivot: Pair<Float, Float>? = null): TransformState {
        val (cx, cy) = pivot ?: center(s.quad)
        val a = Math.toRadians(angleOf(s.quad).toDouble()); val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
        // Yerel eksen: u (üst kenar yönü), v (dik)
        return mapAll(s) { x, y ->
            val ox = x - cx; val oy = y - cy
            var pu = ox * ux + oy * uy; var pv = -ox * uy + oy * ux
            if (horizontal) pu = -pu else pv = -pv
            (cx + pu * ux - pv * uy) to (cy + pu * uy + pv * ux)
        }
    }

    /**
     * Köşe tutamacı (Serbest / Tekdüze): karşı köşe sabit, dörtgen kendi açısında dikdörtgen kalır; Tekdüze'de oran korunur.
     * Bozma'da köşe serbestçe taşınır (dörtgen dışbükey kalmıyorsa değişmez).
     */
    fun dragCorner(s: TransformState, i: Int, x: Float, y: Float): TransformState {
        if (s.kind == TransformKind.SHEAR) {
            // Paralelkenar kendi kenar yönlerinde ölçeklenir (karşı köşe sabit)
            val q = s.quad
            val o = q[(i + 2) % 4]; val na = q[(i + 1) % 4]; val nb = q[(i + 3) % 4]
            val ax = na.x - o.x; val ay = na.y - o.y; val bx = nb.x - o.x; val by = nb.y - o.y
            val det = ax * by - ay * bx
            if (abs(det) < 1e-6f) return s
            val px = x - o.x; val py = y - o.y
            val a = (px * by - py * bx) / det; val b = (ax * py - ay * px) / det
            if (a * hypot(ax, ay) < 1f || b * hypot(bx, by) < 1f) return s
            val nq = q.toMutableList()
            nq[(i + 1) % 4] = StrokePoint(o.x + a * ax, o.y + a * ay)
            nq[(i + 3) % 4] = StrokePoint(o.x + b * bx, o.y + b * by)
            nq[i] = StrokePoint(o.x + a * ax + b * bx, o.y + a * ay + b * by)
            return s.copy(quad = nq)
        }
        if (s.kind == TransformKind.DISTORT) {
            val q = s.quad.mapIndexed { k, p -> if (k == i) StrokePoint(x, y) else p }
            return if (Homography.convex(q)) s.copy(quad = q) else s
        }
        val q = s.quad
        val o = q[(i + 2) % 4]
        val a = Math.toRadians(angleOf(q).toDouble()); val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
        val oldW = widthOf(q); val oldH = heightOf(q)
        // Yerel koordinatta karşı köşeye göre yeni köşe
        var pu = (x - o.x) * ux + (y - o.y) * uy
        var pv = -(x - o.x) * uy + (y - o.y) * ux
        val su = if (i == 1 || i == 2) 1f else -1f
        val sv = if (i == 2 || i == 3) 1f else -1f
        var w = max(1f, pu * su); var h = max(1f, pv * sv)
        if (s.kind == TransformKind.UNIFORM && oldW > 0f && oldH > 0f) {
            val k = max(w / oldW, h / oldH)
            w = oldW * k; h = oldH * k
        }
        pu = w * su; pv = h * sv
        fun at(du: Float, dv: Float) = StrokePoint(o.x + du * ux - dv * uy, o.y + du * uy + dv * ux)
        // Yerel çerçevede (u sağa, v aşağı) karşı köşeye göre dikdörtgen: sol-üst, sağ-üst, sağ-alt, sol-alt
        val u0 = min(0f, pu); val u1 = max(0f, pu); val v0 = min(0f, pv); val v1 = max(0f, pv)
        return s.copy(quad = listOf(at(u0, v0), at(u1, v0), at(u1, v1), at(u0, v1)))
    }

    /**
     * Kenar tutamacı ([e]: 0 üst, 1 sağ, 2 alt, 3 sol). Serbest/Tekdüze: o kenar dik yönde taşınır (tek eksen ölçek;
     * Tekdüze'de iki eksen birlikte). Bozma: kenar paralel kayar (eğme).
     */
    fun dragEdge(s: TransformState, e: Int, dx: Float, dy: Float): TransformState {
        val q = s.quad
        val i0 = e; val i1 = (e + 1) % 4
        if (s.kind == TransformKind.DISTORT || s.kind == TransformKind.SHEAR) {
            // Eğme: kenar kendi doğrultusunda kayar
            val ex = q[i1].x - q[i0].x; val ey = q[i1].y - q[i0].y; val len = max(1e-3f, hypot(ex, ey))
            val along = (dx * ex + dy * ey) / len
            val mx = ex / len * along; val my = ey / len * along
            val nq = q.mapIndexed { k, p -> if (k == i0 || k == i1) StrokePoint(p.x + mx, p.y + my) else p }
            return if (Homography.convex(nq)) s.copy(quad = nq) else s
        }
        val a = Math.toRadians(angleOf(q).toDouble()); val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
        val du = dx * ux + dy * uy; val dv = -dx * uy + dy * ux
        val w = widthOf(q); val h = heightOf(q)
        // Yeni en ve boy (karşı kenar sabit)
        var nw = w; var nh = h
        when (e) { 0 -> nh = max(1f, h - dv); 2 -> nh = max(1f, h + dv); 1 -> nw = max(1f, w + du); 3 -> nw = max(1f, w - du) }
        if (s.kind == TransformKind.UNIFORM) {
            val k = if (e == 0 || e == 2) nh / max(1e-3f, h) else nw / max(1e-3f, w)
            nw = w * k; nh = h * k
        }
        // Sabit kenar: karşı kenar; yerel çerçevede yeniden kur
        val fix = when (e) { 0 -> q[3]; 1 -> q[0]; 2 -> q[0]; else -> q[1] }
        fun at(du0: Float, dv0: Float) = StrokePoint(fix.x + du0 * ux - dv0 * uy, fix.y + du0 * uy + dv0 * ux)
        val nq = when (e) {
            0 -> { val o = q[3]; listOf(at(0f, -nh), at(nw, -nh), at(nw, 0f), o) }
            2 -> listOf(q[0], at(nw, 0f), at(nw, nh), at(0f, nh))
            1 -> listOf(q[0], at(nw, 0f), at(nw, nh), at(0f, nh))
            else -> { listOf(at(-nw, 0f), q[1], at(0f, nh), at(-nw, nh)) }
        }
        return s.copy(quad = nq)
    }

    /** Çarpıtma: ağ noktası taşınır. */
    fun dragMesh(s: TransformState, index: Int, x: Float, y: Float): TransformState =
        s.mesh?.let { s.copy(mesh = WarpMesh.movePoint(it, index, x, y)) } ?: s

    /** Çarpıtma: ağın içinden sürükleme (yumuşak bölge). */
    fun dragMeshInside(s: TransformState, px: Float, py: Float, dx: Float, dy: Float): TransformState =
        s.mesh?.let { m -> s.copy(mesh = WarpMesh.smoothDrag(m, px, py, dx, dy, max(widthOf(s.quad), heightOf(s.quad)) / 3f)) } ?: s

    /** Tuvale (ya da panel kutusuna) sığdır: oran korunur, ortalanır, döndürme sıfırlanır. */
    fun fit(s: TransformState, l: Float, t: Float, r: Float, b: Float): TransformState {
        val bw = s.r - s.l; val bh = s.b - s.t
        val k = min((r - l) / max(1e-3f, bw), (b - t) / max(1e-3f, bh))
        val w = bw * k; val h = bh * k
        val x0 = l + (r - l - w) / 2f; val y0 = t + (b - t - h) / 2f
        val q = listOf(StrokePoint(x0, y0), StrokePoint(x0 + w, y0), StrokePoint(x0 + w, y0 + h), StrokePoint(x0, y0 + h))
        return s.copy(quad = q, mesh = s.mesh?.let { WarpMesh.onQuad(q, WarpMesh.sizeOf(it)) })
    }

    fun reset(s: TransformState): TransformState =
        s.copy(quad = s.box, mesh = s.mesh?.let { WarpMesh.identity(s.l, s.t, s.r, s.b, WarpMesh.sizeOf(it)) })

    /** Sayısal giriş: konum (sol üst), en, boy, açı. */
    fun numeric(s: TransformState, x: Float, y: Float, w: Float, h: Float, degrees: Float): TransformState {
        val a = Math.toRadians(degrees.toDouble()); val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
        val ww = max(1f, w); val hh = max(1f, h)
        val q = listOf(StrokePoint(x, y), StrokePoint(x + ww * ux, y + ww * uy), StrokePoint(x + ww * ux - hh * uy, y + ww * uy + hh * ux), StrokePoint(x - hh * uy, y + hh * ux))
        return s.copy(quad = q, mesh = s.mesh?.let { WarpMesh.onQuad(q, WarpMesh.sizeOf(it)) })
    }

    /** Başlangıç kutusundaki noktayı güncel duruma eşleyen işlev. */
    fun mapper(s: TransformState): (Float, Float) -> Pair<Float, Float> {
        val m = s.mesh
        if (m != null) { val n = WarpMesh.sizeOf(m); return { x, y -> WarpMesh.map(m, n, s.l, s.t, s.r, s.b, x, y) } }
        val src = floatArrayOf(s.l, s.t, s.r, s.t, s.r, s.b, s.l, s.b)
        val dst = FloatArray(8) { if (it % 2 == 0) s.quad[it / 2].x else s.quad[it / 2].y }
        val h = Homography.solve(src, dst) ?: return { x, y -> x to y }
        return { x, y -> Homography.map(h, x, y) }
    }

    /** Fırça kalınlığı çarpanı: alan oranının karekökü. */
    fun widthFactor(s: TransformState): Float {
        val q = s.mesh?.let { m -> val n = WarpMesh.sizeOf(m); listOf(m[0], m[n - 1], m[n * n - 1], m[n * (n - 1)]) } ?: s.quad
        fun area(p: List<StrokePoint>): Float {
            var a = 0f
            for (i in p.indices) { val c = p[i]; val d = p[(i + 1) % p.size]; a += c.x * d.y - d.x * c.y }
            return abs(a) / 2f
        }
        val a0 = (s.r - s.l) * (s.b - s.t)
        return if (a0 <= 0f) 1f else sqrt(area(q) / a0).coerceIn(0.05f, 20f)
    }

    /**
     * Elemanlara uygula: vektör çizgi ve şekiller noktadan eşlenir (kalınlık alan oranıyla); resimde Bozma köşelerle
     * (perspektif), Çarpıtma ağla ([ImageWarp.mesh]) uygulanır; yazı, şema ve formül yalnız dörtgen köşeleriyle eşlenir
     * (Bozma ve Çarpıtma'da değişmez, [skipped] olarak döner).
     */
    fun apply(strokes: List<Stroke>, ids: Set<String>, s: TransformState): Applied {
        val f = mapper(s)
        val wf = widthFactor(s)
        val nonAffine = s.kind == TransformKind.DISTORT || s.kind == TransformKind.WARP
        val skipped = HashSet<String>()
        fun mp(p: StrokePoint): StrokePoint = f(p.x, p.y).let { (x, y) -> p.at(x, y) }
        val out = strokes.map { st ->
            if (st.id !in ids) return@map st
            when {
                st.brushType == BrushType.IMAGE && st.points.size >= 4 -> {
                    val corners = st.points.take(4)
                    val oldMesh = st.warp?.mesh
                    val oldN = oldMesh?.let { WarpMesh.sizeOf(it) } ?: 0
                    val n = max(oldN, s.mesh?.let { WarpMesh.sizeOf(it) } ?: 0)
                    val base = when {
                        n == 0 -> null
                        oldN == n -> oldMesh
                        oldN > 0 -> WarpMesh.resample(oldMesh!!, oldN, n)
                        else -> WarpMesh.onQuad(corners, n)
                    }
                    val grid = base?.map { mp(it) }
                    val newCorners = grid?.let { g -> listOf(g[0], g[n - 1], g[n * n - 1], g[n * (n - 1)]) } ?: corners.map { mp(it) }
                    val warp = (st.warp ?: ImageWarp()).copy(mesh = grid, filter = s.filter.code)
                    st.copy(points = newCorners + st.points.drop(4).map { mp(it) }, warp = warp.takeIf { !it.isEmpty })
                }
                nonAffine && (st.brushType.isQuad) -> { skipped += st.id; st }
                else -> st.copy(
                    points = st.points.map { mp(it) },
                    clip = st.clip?.map { mp(it) },
                    sizePx = if (st.brushType.isQuad) st.sizePx else (st.sizePx * wf).coerceIn(0.5f, 2000f),
                    shape = st.shape?.let { spec -> spec.copy(style = spec.style.copy(strokeWidth = (spec.style.strokeWidth * wf).coerceIn(0.2f, 400f), cornerRadius = spec.style.cornerRadius * wf)) },
                    mask = st.mask?.let { m -> m.copy(points = m.points.map { mp(it) }) },
                )
            }
        }
        return Applied(out, skipped)
    }

    data class Applied(val strokes: List<Stroke>, val skipped: Set<String>)

    /**
     * 2E göreli afin dönüşüm (her birini dönüştür, dönüşümü tekrarla): p' = c + (dx, dy) + L·(p − c); c kutunun merkezi.
     * L = [[a, b], [c, d]] (satır satır).
     */
    data class Relative(val a: Float, val b: Float, val c: Float, val d: Float, val dx: Float, val dy: Float) {
        val isIdentity: Boolean get() = abs(a - 1f) < 1e-5f && abs(b) < 1e-5f && abs(c) < 1e-5f && abs(d - 1f) < 1e-5f && abs(dx) < 1e-3f && abs(dy) < 1e-3f
        fun map(cx: Float, cy: Float, x: Float, y: Float): Pair<Float, Float> =
            (cx + dx + a * (x - cx) + b * (y - cy)) to (cy + dy + c * (x - cx) + d * (y - cy))
    }

    /** Durumun göreli afin karşılığı; ağ ya da perspektif (paralelkenar olmayan dörtgen) ise null. */
    fun relative(s: TransformState): Relative? {
        if (s.mesh != null) return null
        val q = s.quad
        val w = s.r - s.l; val h = s.b - s.t
        if (w <= 0f || h <= 0f) return null
        // Paralelkenar mı: q2 = q1 + q3 − q0
        val ex = q[1].x + q[3].x - q[0].x; val ey = q[1].y + q[3].y - q[0].y
        if (hypot(q[2].x - ex, q[2].y - ey) > max(0.5f, max(widthOf(q), heightOf(q)) * 0.002f)) return null
        val a = (q[1].x - q[0].x) / w; val c = (q[1].y - q[0].y) / w
        val b = (q[3].x - q[0].x) / h; val d = (q[3].y - q[0].y) / h
        val cx = (s.l + s.r) / 2f; val cy = (s.t + s.b) / 2f
        val mx = q[0].x + a * (cx - s.l) + b * (cy - s.t); val my = q[0].y + c * (cx - s.l) + d * (cy - s.t)
        return Relative(a, b, c, d, mx - cx, my - cy)
    }

    /** Noktalardan sınır kutusu (sol, üst, sağ, alt). */
    fun pointsBounds(strokes: List<Stroke>): FloatArray? {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (s in strokes) for (p in s.points) { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        return if (l > r) null else floatArrayOf(l, t, r, b)
    }

    /** Seçimin birimleri: aynı gruptakiler birlikte, öteki elemanlar tek başına. */
    fun units(strokes: List<Stroke>, ids: Set<String>): List<Set<String>> =
        strokes.filter { it.id in ids }.groupBy { it.groupId ?: ("#" + it.id) }.values.map { g -> g.mapTo(LinkedHashSet()) { it.id } }

    /** Göreli dönüşümü [ids] elemanlarına uygular: [each] ise her birim kendi merkezinde, değilse seçimin merkezinde. */
    fun applyRelative(strokes: List<Stroke>, ids: Set<String>, rel: Relative, each: Boolean): List<Stroke> {
        val groups = if (each) units(strokes, ids) else listOf(ids)
        var out = strokes
        for (g in groups) {
            val bb = pointsBounds(out.filter { it.id in g }) ?: continue
            val st = TransformState.start(bb[0], bb[1], bb[2], bb[3], TransformKind.FREE)
            val cx = (st.l + st.r) / 2f; val cy = (st.t + st.b) / 2f
            val q = st.box.map { p -> rel.map(cx, cy, p.x, p.y).let { (x, y) -> StrokePoint(x, y) } }
            out = apply(out, g, st.copy(quad = q)).strokes
        }
        return out
    }

    /** Her birini dönüştür: durum afin ise her birim kendi merkezinde aynı dönüşümü alır (değilse bütün seçim). */
    fun applyEach(strokes: List<Stroke>, ids: Set<String>, s: TransformState): Applied {
        val rel = relative(s) ?: return apply(strokes, ids, s)
        return Applied(applyRelative(strokes, ids, rel, each = true), emptySet())
    }

    /** Referans noktası: 9 noktadan biri (0 sol-üst … 4 merkez … 8 sağ-alt), dörtgen üzerinde. */
    fun anchorPoint(q: List<StrokePoint>, index: Int): Pair<Float, Float> {
        val u = (index % 3) / 2f; val v = (index / 3) / 2f
        val tx = q[0].x + (q[1].x - q[0].x) * u; val ty = q[0].y + (q[1].y - q[0].y) * u
        val bx = q[3].x + (q[2].x - q[3].x) * u; val by = q[3].y + (q[2].y - q[3].y) * u
        return (tx + (bx - tx) * v) to (ty + (by - ty) * v)
    }
}

/**
 * v56 Yansıma (planın 4. bölümü "Yansıma: çoğalt, dikey çevir, alfa gradyanı, dalga ya da dalgacık"): seçimin
 * altına dikey çevrilmiş kopya; kopyalar kalıcı ve ayarlanabilir [WaveSpec] taşır ([ImageWarp.wave]).
 */
object Reflection {
    /** Dalga kayması (dx, dy): dalgada yatay sinüs, dalgacıkta merkezden halkalar; derinlikle (top → bottom) artar. */
    fun offset(w: WaveSpec, x: Float, y: Float): Pair<Float, Float> {
        if (w.amplitude == 0f || w.wavelength <= 0f) return 0f to 0f
        val depth = if (w.bottom > w.top) ((y - w.top) / (w.bottom - w.top)).coerceIn(0f, 1f) else 1f
        val twoPi = (2.0 * Math.PI).toFloat()
        return if (w.ripple) {
            val dx = x - w.cx; val dy = y - w.top
            val d = hypot(dx, dy)
            if (d < 1e-3f) return 0f to 0f
            val k = w.amplitude * depth * sin(twoPi * (d / w.wavelength + w.phase))
            (dx / d * k) to (dy / d * k * 0.5f)
        } else {
            (w.amplitude * depth * sin(twoPi * ((y - w.top) / w.wavelength + w.phase))) to 0f
        }
    }

    /** Gradyan opaklığı (y'de). */
    fun fade(w: WaveSpec, y: Float): Float {
        if (w.bottom <= w.top) return w.fadeFrom
        val t = ((y - w.top) / (w.bottom - w.top)).coerceIn(0f, 1f)
        return w.fadeFrom + (w.fadeTo - w.fadeFrom) * t
    }

    /**
     * Seçili elemanların yansıması: [gap] px aşağıda, dikey çevrilmiş kopyalar (yeni kimlikler [newId]); dalga ve
     * gradyan yansımanın kutusuna göre. Seçim boşsa liste aynı döner.
     */
    fun create(
        strokes: List<Stroke>, ids: Set<String>, l: Float, t: Float, r: Float, b: Float, gap: Float, newId: (Stroke) -> String,
        wave: WaveSpec = WaveSpec(amplitude = 4f, wavelength = 36f, fadeFrom = 0.55f, fadeTo = 0f),
    ): List<Stroke> {
        val sel = strokes.filter { it.id in ids && !it.hidden }
        if (sel.isEmpty()) return strokes
        val axis = b + gap / 2f
        val spec = wave.copy(top = b + gap, bottom = b + gap + (b - t), cx = (l + r) / 2f)
        val copies = sel.mapIndexed { i, s ->
            fun m(p: StrokePoint) = p.at(p.x, 2f * axis - p.y)
            s.copy(
                id = newId(s), order = strokes.size + i, groupId = s.groupId?.let { "$it-r" },
                points = s.points.map { m(it) }, clip = s.clip?.map { m(it) },
                mask = s.mask?.let { mk -> mk.copy(points = mk.points.map { m(it) }) },
                warp = (s.warp ?: ImageWarp()).copy(wave = spec, mesh = s.warp?.mesh?.map { m(it) }, quad = s.warp?.quad?.map { m(it) }),
            )
        }
        return strokes + copies
    }
}
