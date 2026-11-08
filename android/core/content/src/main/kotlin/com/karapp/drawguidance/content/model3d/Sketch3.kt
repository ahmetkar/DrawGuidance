package com.karapp.drawguidance.content.model3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * v27 3B çizim önerisi (saf Kotlin, cihazı yormayan makine öğrenmesi):
 *
 * 1. Düzleme izdüşüm: 3B çizim düzlemindeki iz, düzlemin tabanında (sağ, yukarı) 2B noktalara çevrilir.
 * 2. Öznitelikler (17 boyut, dönme/ölçek/başlangıç noktasından bağımsız): kapanma, sehim, dairesellik, dışbükeylik,
 *    en/boy oranı (PCA), yarıçap değişimi, köşe sayısı, toplam dönüş ve yön tutarlılığı, en küçük dikdörtgeni doldurma,
 *    merkez uzaklığının Fourier genlikleri (1–6) ve sarmal eğilimi. 48 noktaya yeniden örnekleme sayesinde süre çizimin
 *    uzunluğundan bağımsızdır (birkaç yüz mikro saniye).
 * 3. Sınıflandırıcı: Gauss Naive Bayes. Sınıf istatistikleri ilk kullanımda arka planda, sabit tohumla üretilen
 *    gürültülü sentetik çizimlerden (11 sınıf × 90 örnek) çıkarılır; model dosyası ya da ağ gerekmez.
 * 4. Kişiselleştirme (cihazda): kabul edilen öneri, o çizimin özniteliklerini ilgili sınıfa çevrimiçi (Welford)
 *    ekler; kişisel örnekler sentetik ortalamayla harmanlanır, eski örnekler üstel olarak unutulur. Her (sınıf, öneri)
 *    çifti için kabul/ret sayıları Beta ortalamasıyla sıralamayı değiştirir.
 * 5. Uydurma: seçilen öneri izin konumuna, boyuna ve yönüne oturtulur (küre: merkez ve yarıçap; kutu: en küçük
 *    dikdörtgen; koni/piramit: üçgenin tabanı ve tepesi; çubuk: çizginin uçları; yay: sarmalın turları…).
 */

/** v27 çizilen izin sınıfı. */
enum class SketchClass { LINE, ARC, WAVE, SPIRAL, CIRCLE, ELLIPSE, TRIANGLE, QUAD, POLYGON, STAR, BLOB }

/** v27 önerilebilen cisimler ve fırça dönüşümleri. */
enum class Suggest3Kind(val primitive: PrimitiveKind? = null, val brush: Brush3Kind? = null) {
    SPHERE(primitive = PrimitiveKind.SPHERE),
    ELLIPSOID(primitive = PrimitiveKind.SPHERE),
    CYLINDER(primitive = PrimitiveKind.CYLINDER),
    TORUS(primitive = PrimitiveKind.TORUS),
    DOME(primitive = PrimitiveKind.DOME),
    CAPSULE(primitive = PrimitiveKind.CAPSULE),
    CUBE(primitive = PrimitiveKind.CUBE),
    BOX(primitive = PrimitiveKind.CUBE),
    SLAB(primitive = PrimitiveKind.CUBE),
    PYRAMID(primitive = PrimitiveKind.PYRAMID),
    CONE(primitive = PrimitiveKind.CONE),
    PRISM3(primitive = PrimitiveKind.PRISM3),
    PRISM6(primitive = PrimitiveKind.PRISM6),
    STAR(primitive = PrimitiveKind.STAR),
    ROD(primitive = PrimitiveKind.CYLINDER),
    TUBE(brush = Brush3Kind.TUBE),
    RIBBON(brush = Brush3Kind.RIBBON),
    WALL(brush = Brush3Kind.WALL),
    LATHE(brush = Brush3Kind.LATHE),
    INFLATE(brush = Brush3Kind.INFLATE),
    SPRING(brush = Brush3Kind.TUBE),
}

/** Düzlemdeki iz: 2B noktalar ([xs], [ys]) ve düzlemin dünya tabanı (merkez, sağ, yukarı, normal). */
class Sketch2(val xs: FloatArray, val ys: FloatArray, val origin: V3, val u: V3, val v: V3, val n: V3) {
    val size: Int get() = xs.size

    fun world(x: Float, y: Float): V3 = V3(
        origin.x + u.x * x + v.x * y, origin.y + u.y * x + v.y * y, origin.z + u.z * x + v.z * y,
    )

    fun dir(x: Float, y: Float): V3 = (u * x + v * y).normalized()

    companion object {
        /** Dünya noktalarını düzleme indirir ([up] düzlemin yukarısı; normale paralelse dünya yukarısı). */
        fun of(points: List<V3>, normal: V3, up: V3): Sketch2? {
            if (points.size < 2) return null
            val n = normal.normalized()
            if (n.length < 0.5f) return null
            var vv = up - n * (up dot n)
            if (vv.length < 1e-4f) {
                val alt = if (abs(n.y) < 0.9f) V3.UP else V3(0f, 0f, -1f)
                vv = alt - n * (alt dot n)
            }
            val v = vv.normalized()
            val u = (v cross n).normalized()
            var sx = 0f; var sy = 0f; var sz = 0f
            for (p in points) { sx += p.x; sy += p.y; sz += p.z }
            val o = V3(sx / points.size, sy / points.size, sz / points.size)
            val xs = FloatArray(points.size) { (points[it] - o) dot u }
            val ys = FloatArray(points.size) { (points[it] - o) dot v }
            return Sketch2(xs, ys, o, u, v, n)
        }
    }
}

/** 2B geometri yardımcıları (öznitelikler ve uydurma için). */
object SketchGeom {
    fun length(xs: FloatArray, ys: FloatArray): Float {
        var l = 0f
        for (i in 1 until xs.size) l += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        return l
    }

    /** Yay uzunluğuna göre eşit aralıklı [n] nokta; [closed] ise kapatan kenar da dahil (son nokta ilkin tekrarı değil). */
    fun resample(xs: FloatArray, ys: FloatArray, n: Int, closed: Boolean): Pair<FloatArray, FloatArray>? {
        val m = xs.size
        if (m < 2) return null
        val count = if (closed) m + 1 else m
        val px = FloatArray(count) { xs[it % m] }
        val py = FloatArray(count) { ys[it % m] }
        val total = length(px, py)
        if (total < 1e-9f) return null
        val step = if (closed) total / n else total / (n - 1)
        val ox = FloatArray(n); val oy = FloatArray(n)
        ox[0] = px[0]; oy[0] = py[0]
        var k = 1
        var acc = 0f
        var i = 1
        var ax = px[0]; var ay = py[0]
        while (i < count && k < n) {
            val bx = px[i]; val by = py[i]
            val d = hypot(bx - ax, by - ay)
            if (acc + d >= step && d > 0f) {
                val t = (step - acc) / d
                val nx = ax + (bx - ax) * t; val ny = ay + (by - ay) * t
                ox[k] = nx; oy[k] = ny; k++
                ax = nx; ay = ny; acc = 0f
            } else {
                acc += d
                ax = bx; ay = by; i++
            }
        }
        while (k < n) { ox[k] = px[count - 1]; oy[k] = py[count - 1]; k++ }
        return ox to oy
    }

    /** İşaretli alan (kapalı çokgen kabul edilir). */
    fun signedArea(xs: FloatArray, ys: FloatArray): Float {
        var a = 0f
        val n = xs.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            a += xs[i] * ys[j] - xs[j] * ys[i]
        }
        return a / 2f
    }

    /** Dışbükey zarf (monoton zincir), saat yönünün tersine indisler. */
    fun hull(xs: FloatArray, ys: FloatArray): IntArray {
        val n = xs.size
        if (n < 3) return IntArray(n) { it }
        val idx = (0 until n).sortedWith(compareBy({ xs[it] }, { ys[it] }))
        fun cross(o: Int, a: Int, b: Int) = (xs[a] - xs[o]) * (ys[b] - ys[o]) - (ys[a] - ys[o]) * (xs[b] - xs[o])
        val out = IntArray(2 * n)
        var k = 0
        for (i in idx) {
            while (k >= 2 && cross(out[k - 2], out[k - 1], i) <= 0f) k--
            out[k++] = i
        }
        val lower = k + 1
        for (j in idx.indices.reversed().drop(1)) {
            val i = idx[j]
            while (k >= lower && cross(out[k - 2], out[k - 1], i) <= 0f) k--
            out[k++] = i
        }
        return out.copyOf(max(k - 1, 1))
    }

    /** En küçük alanlı dikdörtgen: merkez, ana yön (birim) ve yarı boyutlar (ana yönde, ona dik). */
    class Rect2(val cx: Float, val cy: Float, val ax: Float, val ay: Float, val hw: Float, val hh: Float) {
        val area: Float get() = 4f * hw * hh
    }

    fun minRect(xs: FloatArray, ys: FloatArray): Rect2 {
        val h = hull(xs, ys)
        var best: Rect2? = null
        val dirs = if (h.size >= 2) h.indices.map { i ->
            val a = h[i]; val b = h[(i + 1) % h.size]
            val dx = xs[b] - xs[a]; val dy = ys[b] - ys[a]
            val l = hypot(dx, dy)
            if (l < 1e-9f) 1f to 0f else dx / l to dy / l
        } else listOf(1f to 0f)
        for ((ax, ay) in dirs) {
            var minU = Float.MAX_VALUE; var maxU = -Float.MAX_VALUE; var minV = Float.MAX_VALUE; var maxV = -Float.MAX_VALUE
            for (i in h) {
                val pu = xs[i] * ax + ys[i] * ay
                val pv = -xs[i] * ay + ys[i] * ax
                minU = min(minU, pu); maxU = max(maxU, pu); minV = min(minV, pv); maxV = max(maxV, pv)
            }
            val hw = (maxU - minU) / 2f; val hh = (maxV - minV) / 2f
            if (best == null || 4f * hw * hh < best.area) {
                val cu = (maxU + minU) / 2f; val cv = (maxV + minV) / 2f
                best = Rect2(cu * ax - cv * ay, cu * ay + cv * ax, ax, ay, hw, hh)
            }
        }
        return best!!
    }

    /** Ana bileşenler: merkez, ana yön (birim) ve özdeğerler (l1 ≥ l2). */
    class Pca(val cx: Float, val cy: Float, val ax: Float, val ay: Float, val l1: Float, val l2: Float)

    fun pca(xs: FloatArray, ys: FloatArray): Pca {
        val n = xs.size
        var mx = 0f; var my = 0f
        for (i in 0 until n) { mx += xs[i]; my += ys[i] }
        mx /= n; my /= n
        var sxx = 0f; var syy = 0f; var sxy = 0f
        for (i in 0 until n) {
            val dx = xs[i] - mx; val dy = ys[i] - my
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy
        }
        sxx /= n; syy /= n; sxy /= n
        val tr = sxx + syy
        val det = sxx * syy - sxy * sxy
        val disc = sqrt(max(tr * tr / 4f - det, 0f))
        val l1 = tr / 2f + disc
        val l2 = max(tr / 2f - disc, 0f)
        val (ax, ay) = if (abs(sxy) > 1e-12f) {
            val vx = l1 - syy; val vy = sxy
            val l = hypot(vx, vy)
            vx / l to vy / l
        } else if (sxx >= syy) 1f to 0f else 0f to 1f
        return Pca(mx, my, ax, ay, l1, l2)
    }

    /** Kapalı çokgenin alan merkezi (alan ~0 ise ortalama). */
    fun centroid(xs: FloatArray, ys: FloatArray): Pair<Float, Float> {
        var a = 0f; var cx = 0f; var cy = 0f
        val n = xs.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            val c = xs[i] * ys[j] - xs[j] * ys[i]
            a += c; cx += (xs[i] + xs[j]) * c; cy += (ys[i] + ys[j]) * c
        }
        if (abs(a) < 1e-9f) return xs.average().toFloat() to ys.average().toFloat()
        return cx / (3f * a) to cy / (3f * a)
    }
}

/** v27 iz öznitelikleri (bkz. dosya başı). */
object SketchFeatures {
    /** Yeniden örnekleme noktası. */
    const val N = 48
    const val DIM = 17
    /** Uçlar arası boşluk / uzunluk bu orandan küçükse iz kapalı sayılır. */
    const val CLOSED_GAP = 0.16f

    fun closed(xs: FloatArray, ys: FloatArray): Boolean {
        val l = SketchGeom.length(xs, ys)
        if (l < 1e-9f) return false
        return hypot(xs.last() - xs[0], ys.last() - ys[0]) / l < CLOSED_GAP
    }

    fun of(xs: FloatArray, ys: FloatArray): FloatArray? {
        val total = SketchGeom.length(xs, ys)
        if (total < 1e-9f || xs.size < 3) return null
        val gap = hypot(xs.last() - xs[0], ys.last() - ys[0])
        val gapRatio = (gap / total).coerceIn(0f, 1f)
        val closed = gapRatio < CLOSED_GAP
        val (rx, ry) = SketchGeom.resample(xs, ys, N, closed) ?: return null
        val f = FloatArray(DIM)
        f[0] = gapRatio
        // Sehim: kirişe en uzak noktanın uzaklığı / uzunluk (doğru ~0, yay büyük)
        run {
            val dx = xs.last() - xs[0]; val dy = ys.last() - ys[0]
            val cl = hypot(dx, dy)
            var sag = 0f
            for (i in 0 until N) {
                val d = if (cl > 1e-6f * total) abs((rx[i] - xs[0]) * dy - (ry[i] - ys[0]) * dx) / cl else hypot(rx[i] - xs[0], ry[i] - ys[0])
                sag = max(sag, d)
            }
            f[1] = (sag / total).coerceIn(0f, 1f)
        }
        val area = abs(SketchGeom.signedArea(rx, ry))
        val per = total + gap
        f[2] = (4f * PI.toFloat() * area / (per * per)).coerceIn(0f, 1f)
        val hull = SketchGeom.hull(rx, ry)
        val hx = FloatArray(hull.size) { rx[hull[it]] }; val hy = FloatArray(hull.size) { ry[hull[it]] }
        val hullArea = abs(SketchGeom.signedArea(hx, hy))
        f[3] = if (hullArea > 1e-12f) (area / hullArea).coerceIn(0f, 1f) else 0f
        val pca = SketchGeom.pca(rx, ry)
        f[4] = if (pca.l1 > 1e-12f) sqrt(pca.l2 / pca.l1).coerceIn(0f, 1f) else 0f
        // Merkez uzaklığı (yarıçap) ve değişimi
        val r = FloatArray(N)
        var mean = 0f
        for (i in 0 until N) { r[i] = hypot(rx[i] - pca.cx, ry[i] - pca.cy); mean += r[i] }
        mean /= N
        var sd = 0f
        for (i in 0 until N) sd += (r[i] - mean) * (r[i] - mean)
        sd = sqrt(sd / N)
        f[5] = if (mean > 1e-9f) (sd / mean).coerceIn(0f, 1.5f) else 0f
        f[6] = corners(rx, ry, closed) / 8f
        // Dönüş: toplam mutlak dönüş (tur) ve işaretli/mutlak oranı (dışbükey ve sarmal 1, zikzak düşük)
        run {
            var sumAbs = 0f; var sumSigned = 0f
            val last = if (closed) N else N - 1
            for (i in 1 until last) {
                val a = (i - 1 + N) % N; val b = i; val c = (i + 1) % N
                val t = turn(rx[b] - rx[a], ry[b] - ry[a], rx[c] - rx[b], ry[c] - ry[b])
                sumAbs += abs(t); sumSigned += t
            }
            f[7] = (sumAbs / (2f * PI.toFloat())).coerceIn(0f, 6f) / 2f
            f[8] = if (sumAbs > 1e-6f) abs(sumSigned) / sumAbs else 0f
        }
        val rect = SketchGeom.minRect(rx, ry)
        f[9] = if (rect.area > 1e-12f) (area / rect.area).coerceIn(0f, 1f) else 0f
        // Yarıçap imzasının Fourier genlikleri (dönme ve başlangıç noktasından bağımsız)
        if (mean > 1e-9f) for (k in 1..6) {
            var re = 0f; var im = 0f
            for (i in 0 until N) {
                val a = 2f * PI.toFloat() * k * i / N
                re += r[i] * cos(a); im -= r[i] * sin(a)
            }
            f[9 + k] = (hypot(re, im) / (N * mean)).coerceIn(0f, 1f) * 2f
        }
        // Sarmal eğilimi: yarıçapın iz boyunca tekdüze artması/azalması
        run {
            val mi = (N - 1) / 2f
            var sxy = 0f; var sxx = 0f; var syy = 0f
            for (i in 0 until N) {
                val dx = i - mi; val dy = r[i] - mean
                sxy += dx * dy; sxx += dx * dx; syy += dy * dy
            }
            f[16] = if (sxx > 0f && syy > 1e-12f) (abs(sxy) / sqrt(sxx * syy)).coerceIn(0f, 1f) else 0f
        }
        return f
    }

    private fun turn(ax: Float, ay: Float, bx: Float, by: Float): Float = atan2(ax * by - ay * bx, ax * bx + ay * by)

    /** Köşe sayısı: pencereli dönüş açısı 42°'yi aşan yerel en büyükler. */
    fun corners(rx: FloatArray, ry: FloatArray, closed: Boolean, window: Int = 3, minDeg: Float = 42f): Int {
        val n = rx.size
        val ang = FloatArray(n)
        for (i in 0 until n) {
            val a = i - window; val c = i + window
            if (!closed && (a < 0 || c >= n)) continue
            val ia = (a + n) % n; val ic = c % n
            ang[i] = abs(turn(rx[i] - rx[ia], ry[i] - ry[ia], rx[ic] - rx[i], ry[ic] - ry[i]))
        }
        val min = Math.toRadians(minDeg.toDouble()).toFloat()
        var count = 0
        var i = 0
        while (i < n) {
            if (ang[i] > min) {
                var best = true
                for (d in -window..window) {
                    if (d == 0) continue
                    val j = i + d
                    if (!closed && (j < 0 || j >= n)) continue
                    val jj = (j + n) % n
                    if (ang[jj] > ang[i] || (ang[jj] == ang[i] && d < 0)) { best = false; break }
                }
                if (best) count++
            }
            i++
        }
        return count
    }
}

/** v27 sentetik eğitim çizimleri (sabit tohum): gürültülü, döndürülmüş, ölçeklenmiş, başlangıcı kaydırılmış. */
object SketchSynth {
    fun sample(c: SketchClass, rnd: Random): Pair<FloatArray, FloatArray> {
        val pts = when (c) {
            SketchClass.LINE -> {
                val bend = rnd.range(-0.035f, 0.035f)
                List(24) { i -> val t = i / 23f; t to bend * 4f * t * (1 - t) }
            }
            SketchClass.ARC -> {
                val sweep = Math.toRadians(rnd.range(70f, 290f).toDouble()).toFloat()
                List(36) { i -> val a = sweep * i / 35f; cos(a) to sin(a) }
            }
            SketchClass.WAVE -> if (rnd.nextBoolean()) {
                val k = rnd.range(1.6f, 4f); val amp = rnd.range(0.07f, 0.24f)
                List(60) { i -> val t = i / 59f; t to amp * sin(2f * PI.toFloat() * k * t) }
            } else {
                val teeth = rnd.nextInt(3, 7); val amp = rnd.range(0.08f, 0.25f)
                polyline(List(teeth * 2 + 1) { i -> i / (teeth * 2f) to if (i % 2 == 0) 0f else amp }, 6)
            }
            SketchClass.SPIRAL -> {
                val turns = rnd.range(1.6f, 4f)
                val a0 = rnd.range(0f, 0.12f)
                val total = 2f * PI.toFloat() * turns
                val list = List(110) { i ->
                    val th = total * i / 109f
                    val rr = a0 + (1f - a0) * th / total
                    rr * cos(th) to rr * sin(th)
                }
                if (rnd.nextBoolean()) list.reversed() else list
            }
            SketchClass.CIRCLE -> curve(rnd)
            SketchClass.ELLIPSE -> curve(rnd, by = rnd.range(0.32f, 0.78f))
            SketchClass.TRIANGLE -> {
                var v: List<Pair<Float, Float>>
                do {
                    v = List(3) { k -> val a = 2f * PI.toFloat() * k / 3f + rnd.range(-0.45f, 0.45f); cos(a) * rnd.range(0.7f, 1.2f) to sin(a) * rnd.range(0.7f, 1.2f) }
                } while (minAngle(v) < 24f)
                closedTrace(v, rnd)
            }
            SketchClass.QUAD -> {
                val h = if (rnd.nextFloat() < 0.4f) rnd.range(0.85f, 1f) else rnd.range(0.25f, 0.85f)
                val j = 0.06f
                closedTrace(listOf(-1f to -h, 1f to -h, 1f to h, -1f to h).map { (x, y) -> x + rnd.range(-j, j) to y + rnd.range(-j, j) }, rnd)
            }
            SketchClass.POLYGON -> {
                val k = rnd.nextInt(5, 8)
                closedTrace(List(k) { i -> val a = 2f * PI.toFloat() * i / k; val r = rnd.range(0.93f, 1.07f); r * cos(a) to r * sin(a) }, rnd)
            }
            SketchClass.STAR -> {
                val k = rnd.nextInt(5, 7); val inner = rnd.range(0.35f, 0.55f)
                closedTrace(List(2 * k) { i -> val a = PI.toFloat() * i / k; val r = if (i % 2 == 0) 1f else inner; r * cos(a) to r * sin(a) }, rnd)
            }
            SketchClass.BLOB -> {
                val amps = List(4) { rnd.range(0.06f, 0.26f) }
                val phases = List(4) { rnd.range(0f, 6.28f) }
                curve(rnd, radial = { th -> 1f + amps.indices.sumOf { k -> (amps[k] * cos((k + 2) * th + phases[k])).toDouble() }.toFloat() }, wob = 0f)
            }
        }
        // Döndür, ölçekle, el titremesi
        val rot = rnd.range(0f, 2f * PI.toFloat())
        val s = rnd.range(0.3f, 3f)
        val cr = cos(rot); val sr = sin(rot)
        val jitter = 0.009f * s
        val xs = FloatArray(pts.size); val ys = FloatArray(pts.size)
        for (i in pts.indices) {
            val (x, y) = pts[i]
            xs[i] = s * (x * cr - y * sr) + rnd.range(-jitter, jitter)
            ys[i] = s * (x * sr + y * cr) + rnd.range(-jitter, jitter)
        }
        return xs to ys
    }

    private fun Random.range(a: Float, b: Float) = a + nextFloat() * (b - a)

    /**
     * Kapalı eğri: başlangıç açısı rastgele, tur 0,93–1,06 (biraz açık ya da üst üste); [by] ikinci yarı eksen, [radial]
     * açıya göre yarıçap, [wob] elle çizimin hafif dalgalanması.
     */
    private fun curve(rnd: Random, by: Float = 1f, radial: (Float) -> Float = { 1f }, wob: Float = 0.035f): List<Pair<Float, Float>> {
        val start = rnd.range(0f, 6.28f)
        val sweep = 2f * PI.toFloat() * rnd.range(0.93f, 1.06f)
        val w1 = rnd.range(0f, wob); val w2 = rnd.range(0f, 6.28f); val w3 = rnd.range(0f, wob * 0.6f)
        return List(64) { i ->
            val th = start + sweep * i / 63f
            val rr = radial(th) * (1f + w1 * sin(2 * th + w2) + w3 * sin(3 * th))
            rr * cos(th) to rr * by * sin(th)
        }
    }

    private fun polyline(v: List<Pair<Float, Float>>, per: Int): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        for (i in 0 until v.size - 1) for (k in 0 until per) {
            val t = k / per.toFloat()
            out += (v[i].first + (v[i + 1].first - v[i].first) * t) to (v[i].second + (v[i + 1].second - v[i].second) * t)
        }
        out += v.last()
        return out
    }

    /** Çokgen kenarları boyunca kapalı iz: rastgele noktadan başlar, %94–105 tur. */
    private fun closedTrace(v: List<Pair<Float, Float>>, rnd: Random): List<Pair<Float, Float>> {
        val dense = polyline(v + v.first(), 14).dropLast(1)
        val n = dense.size
        val start = rnd.nextInt(n)
        val count = (n * rnd.range(0.94f, 1.05f)).toInt().coerceAtLeast(3)
        return List(count + 1) { dense[(start + it) % n] }
    }

    private fun minAngle(v: List<Pair<Float, Float>>): Float {
        var m = 180f
        for (i in 0 until 3) {
            val a = v[i]; val b = v[(i + 1) % 3]; val c = v[(i + 2) % 3]
            val ux = b.first - a.first; val uy = b.second - a.second
            val wx = c.first - a.first; val wy = c.second - a.second
            val ang = Math.toDegrees(abs(atan2(ux * wy - uy * wx, ux * wx + uy * wy)).toDouble()).toFloat()
            m = min(m, ang)
        }
        return m
    }
}

/**
 * v27 Gauss Naive Bayes sınıflandırıcı: sentetik istatistikler + cihazda kişisel örnekler (çevrimiçi Welford) ve
 * öneri tercihleri (kabul/ret). [encode]/[decode] ile tercihlerde saklanır.
 */
class SketchModel private constructor(
    private val baseMean: Array<FloatArray>,
    private val baseVar: Array<FloatArray>,
) {
    private val classes = SketchClass.entries
    private val kinds = Suggest3Kind.entries
    private val pn = FloatArray(classes.size)
    private val pMean = Array(classes.size) { FloatArray(SketchFeatures.DIM) }
    private val pM2 = Array(classes.size) { FloatArray(SketchFeatures.DIM) }
    private val accept = Array(classes.size) { FloatArray(kinds.size) }
    private val reject = Array(classes.size) { FloatArray(kinds.size) }

    /**
     * v29 3B modunun kendi küçük sinir ağı (17 öznitelik → 24 gizli → öneri türleri): yalnız kullanıcının kabul ve
     * retleriyle cihazda eğitilir; yeterince örnek görünce Naive Bayes puanlarını kişisel tercihe göre yeniden tartar.
     */
    private val net = Sketch3Net(kinds.size)

    /** Ağın eğitildiği geri bildirim sayısı. */
    val netSamples: Int get() = net.samples

    /**
     * v30 karmaşıklığa göre algoritma: tek parçalı basit izler (doğru, yay, daire) Naive Bayes ile; ağ yalnız çok
     * parçalı (3+ köşeli: üçgen, dörtgen, yıldız, zikzak…) izlerde çalışır ve yalnız onlardan öğrenir.
     */
    fun usesNet(f: FloatArray): Boolean = Math.round(f[6] * 8f) >= SKETCH3_NET_MIN_CORNERS

    /** Özniteliklerin ölçeklenmesi (sentetik istatistiklerden). */
    private val featMean = FloatArray(SketchFeatures.DIM) { d -> baseMean.map { it[d] }.average().toFloat() }
    private val featScale = FloatArray(SketchFeatures.DIM) { d ->
        val m = featMean[d]
        val v = baseMean.indices.map { c -> baseVar[c][d] + (baseMean[c][d] - m) * (baseMean[c][d] - m) }.average().toFloat()
        1f / kotlin.math.sqrt(max(v, VAR_FLOOR))
    }

    private fun scaled(f: FloatArray): FloatArray = FloatArray(SketchFeatures.DIM) { d -> ((f[d] - featMean[d]) * featScale[d]).coerceIn(-6f, 6f) }

    /** Sınıf olasılıkları (sınıf sırasıyla). */
    fun posterior(f: FloatArray): FloatArray {
        val logp = FloatArray(classes.size)
        for (c in classes.indices) {
            val (mu, va) = stats(c)
            var s = 0.0
            for (d in 0 until SketchFeatures.DIM) {
                val diff = f[d] - mu[d]
                s += -0.5 * (diff * diff / va[d] + ln(va[d].toDouble()))
            }
            logp[c] = (s / TEMPERATURE).toFloat()
        }
        val mx = logp.max()
        var sum = 0f
        for (c in logp.indices) { logp[c] = exp(logp[c] - mx); sum += logp[c] }
        for (c in logp.indices) logp[c] /= sum
        return logp
    }

    /** Sentetik ve kişisel istatistiklerin harmanı (kişisel örnek [PERSONAL_WEIGHT] sentetik örnek sayılır). */
    private fun stats(c: Int): Pair<FloatArray, FloatArray> {
        val n = pn[c]
        if (n < 0.5f) return baseMean[c] to baseVar[c]
        val ws = SYNTH_PER_CLASS.toFloat(); val wp = n * PERSONAL_WEIGHT
        val mu = FloatArray(SketchFeatures.DIM)
        val va = FloatArray(SketchFeatures.DIM)
        for (d in 0 until SketchFeatures.DIM) {
            val pv = if (n > 1f) pM2[c][d] / n else baseVar[c][d]
            val m = (ws * baseMean[c][d] + wp * pMean[c][d]) / (ws + wp)
            val e2 = (ws * (baseVar[c][d] + baseMean[c][d] * baseMean[c][d]) + wp * (pv + pMean[c][d] * pMean[c][d])) / (ws + wp)
            mu[d] = m
            va[d] = max(e2 - m * m, VAR_FLOOR)
        }
        return mu to va
    }

    /** Sınıf için öneri ağırlığı × tercih çarpanı (Beta ortalaması: veri yokken 1). */
    private fun weight(c: Int, k: Suggest3Kind): Float {
        val w = CANDIDATES[classes[c]]?.firstOrNull { it.first == k }?.second ?: return 0f
        val a = accept[c][k.ordinal]; val r = reject[c][k.ordinal]
        return w * (0.5f + (a + 1f) / (a + r + 2f))
    }

    /**
     * Öneriler: her öneri için Σ P(sınıf) × ağırlık; çizilen fırçayla aynı olan ve eşiğin altındakiler çıkarılır. Kare
     * benzeri dörtgende küp, değilse kutu öne çıkar.
     */
    fun suggest(f: FloatArray, drawn: Brush3Kind, max: Int = 3): Sketch3Result {
        val post = posterior(f)
        val square = f[4] > 0.8f
        val scores = HashMap<Suggest3Kind, Float>()
        for (c in classes.indices) {
            if (post[c] < 0.02f) continue
            CANDIDATES[classes[c]]?.forEach { (k, _) ->
                var w = weight(c, k)
                if (classes[c] == SketchClass.QUAD) {
                    if (k == Suggest3Kind.CUBE && !square) w *= 0.45f
                    if (k == Suggest3Kind.BOX && square) w *= 0.6f
                }
                scores[k] = (scores[k] ?: 0f) + post[c] * w
            }
        }
        // v29 modun ağı: kullanıcının tercihleri öğrenildikçe puanlar yeniden tartılır
        if (net.samples > 0 && usesNet(f)) {
            val p = net.probs(scaled(f))
            val maxP = p.max().coerceAtLeast(1e-6f)
            val blend = (net.samples / 6f).coerceAtMost(1f)
            for (k in scores.keys.toList()) {
                val rel = p[k.ordinal] / maxP
                scores[k] = scores.getValue(k) * (1f - blend * (1f - (0.5f + rel) / 1.5f))
            }
        }
        val list = scores.entries
            .filter { (k, s) -> s >= MIN_SCORE && (k.brush == null || k.brush != drawn || k == Suggest3Kind.SPRING) }
            .sortedByDescending { it.value }
            .take(max)
            .map { Suggestion3(it.key, it.value) }
        val best = post.indices.maxBy { post[it] }
        return Sketch3Result(classes[best], post[best], list, f)
    }

    /** Kabul edilen öneri: izin öznitelikleri öneriyi en çok destekleyen sınıfa eklenir; tercih sayısı artar. */
    fun learnAccept(f: FloatArray, k: Suggest3Kind) {
        val post = posterior(f)
        val c = classes.indices.maxBy { post[it] * (CANDIDATES[classes[it]]?.firstOrNull { p -> p.first == k }?.second ?: 0f) }
        accept[c][k.ordinal] += 1f
        if (usesNet(f)) net.accept(scaled(f), k.ordinal)
        // Welford (üstel unutma: en fazla [PERSONAL_CAP] örnek)
        if (pn[c] >= PERSONAL_CAP) {
            val keep = (PERSONAL_CAP - 1f) / pn[c]
            pn[c] *= keep
            for (d in 0 until SketchFeatures.DIM) pM2[c][d] *= keep
        }
        pn[c] += 1f
        for (d in 0 until SketchFeatures.DIM) {
            val delta = f[d] - pMean[c][d]
            pMean[c][d] += delta / pn[c]
            pM2[c][d] += delta * (f[d] - pMean[c][d])
        }
    }

    /** Kapatılan öneriler: en olası sınıfta hafif ret. */
    fun learnDismiss(f: FloatArray, shown: List<Suggest3Kind>) {
        val post = posterior(f)
        val c = post.indices.maxBy { post[it] }
        shown.forEach { reject[c][it.ordinal] += 0.5f }
        if (usesNet(f)) net.reject(scaled(f), shown.map { it.ordinal })
    }

    /** Kişisel durum (tercihlerde saklanır). */
    fun encode(): String = buildString {
        append("v1")
        for (c in classes.indices) {
            if (pn[c] > 0f) {
                append(";p,").append(classes[c].name).append(',').append(pn[c])
                pMean[c].forEach { append(',').append(it) }
                pM2[c].forEach { append(',').append(it) }
            }
            for (k in kinds.indices) if (accept[c][k] > 0f || reject[c][k] > 0f) {
                append(";k,").append(classes[c].name).append(',').append(kinds[k].name).append(',').append(accept[c][k]).append(',').append(reject[c][k])
            }
        }
        if (net.samples > 0) append(";n,").append(net.encode())
    }

    fun decode(s: String?) {
        if (s.isNullOrBlank() || !s.startsWith("v1")) return
        runCatching {
            for (rec in s.split(';').drop(1)) {
                val t = rec.split(',')
                when (t[0]) {
                    "p" -> {
                        val c = SketchClass.valueOf(t[1]).ordinal
                        if (t.size != 3 + 2 * SketchFeatures.DIM) continue
                        pn[c] = t[2].toFloat()
                        for (d in 0 until SketchFeatures.DIM) { pMean[c][d] = t[3 + d].toFloat(); pM2[c][d] = t[3 + SketchFeatures.DIM + d].toFloat() }
                    }
                    "k" -> {
                        val c = SketchClass.valueOf(t[1]).ordinal
                        val k = Suggest3Kind.valueOf(t[2]).ordinal
                        accept[c][k] = t[3].toFloat(); reject[c][k] = t[4].toFloat()
                    }
                    "n" -> net.decode(t.drop(1))
                }
            }
        }
    }

    companion object {
        const val SYNTH_PER_CLASS = 90
        private const val PERSONAL_WEIGHT = 4f
        private const val PERSONAL_CAP = 40f
        private const val VAR_FLOOR = 0.0025f
        /** Bağımsızlık varsayımının aşırı güvenini yumuşatır. */
        private const val TEMPERATURE = 3.0
        private const val MIN_SCORE = 0.12f

        /** Sınıf → (öneri, ağırlık), sırayla. */
        val CANDIDATES: Map<SketchClass, List<Pair<Suggest3Kind, Float>>> = mapOf(
            SketchClass.LINE to listOf(Suggest3Kind.ROD to 1f, Suggest3Kind.TUBE to 0.8f, Suggest3Kind.WALL to 0.6f),
            SketchClass.ARC to listOf(Suggest3Kind.TUBE to 1f, Suggest3Kind.RIBBON to 0.7f, Suggest3Kind.LATHE to 0.6f, Suggest3Kind.WALL to 0.5f),
            SketchClass.WAVE to listOf(Suggest3Kind.TUBE to 1f, Suggest3Kind.RIBBON to 0.8f, Suggest3Kind.WALL to 0.6f),
            SketchClass.SPIRAL to listOf(Suggest3Kind.SPRING to 1f, Suggest3Kind.TUBE to 0.7f),
            SketchClass.CIRCLE to listOf(Suggest3Kind.SPHERE to 1f, Suggest3Kind.CYLINDER to 0.85f, Suggest3Kind.TORUS to 0.7f, Suggest3Kind.DOME to 0.55f),
            SketchClass.ELLIPSE to listOf(Suggest3Kind.ELLIPSOID to 1f, Suggest3Kind.CAPSULE to 0.8f, Suggest3Kind.CYLINDER to 0.55f, Suggest3Kind.INFLATE to 0.5f),
            SketchClass.TRIANGLE to listOf(Suggest3Kind.CONE to 1f, Suggest3Kind.PYRAMID to 0.9f, Suggest3Kind.PRISM3 to 0.75f),
            SketchClass.QUAD to listOf(Suggest3Kind.CUBE to 1f, Suggest3Kind.BOX to 1f, Suggest3Kind.SLAB to 0.6f, Suggest3Kind.WALL to 0.45f),
            SketchClass.POLYGON to listOf(Suggest3Kind.PRISM6 to 1f, Suggest3Kind.INFLATE to 0.6f, Suggest3Kind.WALL to 0.5f),
            SketchClass.STAR to listOf(Suggest3Kind.STAR to 1f, Suggest3Kind.WALL to 0.6f, Suggest3Kind.INFLATE to 0.5f),
            SketchClass.BLOB to listOf(Suggest3Kind.INFLATE to 1f, Suggest3Kind.WALL to 0.8f, Suggest3Kind.LATHE to 0.35f),
        )

        /** Sentetik istatistikler (süreç başına bir kez; birkaç on milisaniye). */
        private val base: Pair<Array<FloatArray>, Array<FloatArray>> by lazy { train(Random(2701), SYNTH_PER_CLASS) }

        /** Yeni model (sentetik istatistiklerle; kişisel durum [decode] ile yüklenir). */
        fun create(): SketchModel = SketchModel(base.first, base.second)

        internal fun train(rnd: Random, perClass: Int): Pair<Array<FloatArray>, Array<FloatArray>> {
            val classes = SketchClass.entries
            val mean = Array(classes.size) { FloatArray(SketchFeatures.DIM) }
            val varr = Array(classes.size) { FloatArray(SketchFeatures.DIM) }
            for (c in classes.indices) {
                val m2 = FloatArray(SketchFeatures.DIM)
                var n = 0
                var guard = 0
                while (n < perClass && guard++ < perClass * 4) {
                    val (xs, ys) = SketchSynth.sample(classes[c], rnd)
                    val f = SketchFeatures.of(xs, ys) ?: continue
                    n++
                    for (d in 0 until SketchFeatures.DIM) {
                        val delta = f[d] - mean[c][d]
                        mean[c][d] += delta / n
                        m2[d] += delta * (f[d] - mean[c][d])
                    }
                }
                for (d in 0 until SketchFeatures.DIM) varr[c][d] = max(m2[d] / max(n - 1, 1), VAR_FLOOR)
            }
            return mean to varr
        }
    }
}

/**
 * v29 3B modunun sinir ağı: [SketchFeatures.DIM] → [HIDDEN] (ReLU) → [out] öneri türü (softmax). Gizli katman sabit
 * tohumla başlar, çıkış katmanı sıfırdan (eşit olasılık); kabul (çapraz entropi) ve ret (−log(1 − p)) adımlarıyla
 * cihazda öğrenir.
 */
internal class Sketch3Net(private val out: Int) {
    private val inDim = SketchFeatures.DIM
    private val w1: FloatArray
    private val b1 = FloatArray(HIDDEN) { 0.05f }
    private val w2 = FloatArray(out * HIDDEN)
    private val b2 = FloatArray(out)
    var samples = 0
        private set

    init {
        val rnd = Random(3301)
        w1 = FloatArray(HIDDEN * inDim) { (rnd.nextFloat() - 0.5f) * 2f * kotlin.math.sqrt(2f / inDim) }
    }

    private fun hidden(x: FloatArray): FloatArray = FloatArray(HIDDEN) { j ->
        var a = b1[j]
        for (i in 0 until inDim) a += w1[j * inDim + i] * x[i]
        if (a > 0f) a else 0f
    }

    private fun softmax(h: FloatArray): FloatArray {
        val z = FloatArray(out) { k -> var a = b2[k]; for (j in 0 until HIDDEN) a += w2[k * HIDDEN + j] * h[j]; a }
        val m = z.max()
        var sum = 0f
        for (k in 0 until out) { z[k] = exp(z[k] - m); sum += z[k] }
        for (k in 0 until out) z[k] /= sum
        return z
    }

    fun probs(x: FloatArray): FloatArray = softmax(hidden(x))

    private fun step(x: FloatArray, h: FloatArray, g: FloatArray, lr: Float) {
        val dh = FloatArray(HIDDEN)
        for (k in 0 until out) {
            if (g[k] == 0f) continue
            for (j in 0 until HIDDEN) { dh[j] += g[k] * w2[k * HIDDEN + j]; w2[k * HIDDEN + j] -= lr * g[k] * h[j] }
            b2[k] -= lr * g[k]
        }
        for (j in 0 until HIDDEN) {
            if (h[j] <= 0f) continue
            val gj = dh[j].coerceIn(-1f, 1f) * 0.2f
            for (i in 0 until inDim) w1[j * inDim + i] -= lr * gj * x[i]
            b1[j] -= lr * gj
        }
    }

    fun accept(x: FloatArray, k: Int) {
        repeat(3) {
            val h = hidden(x); val p = softmax(h)
            val g = FloatArray(out) { p[it] }; g[k] -= 1f
            step(x, h, g, LR)
        }
        samples++
    }

    fun reject(x: FloatArray, shown: List<Int>) {
        if (shown.isEmpty()) return
        for (k in shown) {
            val h = hidden(x); val p = softmax(h)
            val f = (p[k] / (1f - p[k]).coerceAtLeast(1e-4f)).coerceAtMost(2f)
            val g = FloatArray(out) { -f * p[it] }; g[k] += f
            step(x, h, g, LR * 0.4f)
        }
        samples++
    }

    fun encode(): String = buildString {
        append(samples)
        for (a in arrayOf(w1, b1, w2, b2)) a.forEach { append(',').append(it) }
    }

    fun decode(t: List<String>) {
        val n = 1 + w1.size + b1.size + w2.size + b2.size
        if (t.size != n) return
        samples = t[0].toInt()
        var i = 1
        for (a in arrayOf(w1, b1, w2, b2)) for (j in a.indices) a[j] = t[i++].toFloat()
    }

    companion object {
        const val HIDDEN = 24
        const val LR = 0.15f
    }
}

/** Tek öneri ve puanı. */
data class Suggestion3(val kind: Suggest3Kind, val score: Float)

/** Çözümleme sonucu: en olası sınıf ve olasılığı, öneriler ve öznitelikler (öğrenme için). */
class Sketch3Result(val sketchClass: SketchClass, val confidence: Float, val suggestions: List<Suggestion3>, val features: FloatArray)

/** v27 önerinin ize oturtulması: cisim parçası ya da fırça girdisi. */
object Sketch3Fit {
    /** Fırça dönüşümü: dünya noktaları, normal, yukarı, kapalılık ve (tornada) eksen; yayda kalınlık önerisi. */
    class BrushFit(val kind: Brush3Kind, val input: BrushInput, val width: Float?, val smoothing: Float?)

    /**
     * Cisim önerisini ize oturtur: parça [id] ile, [index] sırasıyla, [material] ile. Uygun değilse null (iz çok küçük
     * ya da tür cisim değil).
     */
    fun solid(kind: Suggest3Kind, sk: Sketch2, id: String, index: Int, material: Material, minSize: Float = 1e-3f): Part? {
        val prim = kind.primitive ?: return null
        val xs = sk.xs; val ys = sk.ys
        val closed = SketchFeatures.closed(xs, ys)
        val (rx, ry) = SketchGeom.resample(xs, ys, 96, closed) ?: return null
        val (cx, cy) = if (closed) SketchGeom.centroid(rx, ry) else (rx.average().toFloat() to ry.average().toFloat())
        var meanR = 0f; var maxR = 0f; var minR = Float.MAX_VALUE
        for (i in rx.indices) { val r = hypot(rx[i] - cx, ry[i] - cy); meanR += r; maxR = max(maxR, r); minR = min(minR, r) }
        meanR /= rx.size
        val center = sk.world(cx, cy)
        val n = sk.n
        val base = ModelEdit.newPart(prim, id, index).copy(material = material)
        // Parçanın yerel Y'si [ey] (tam korunur), X'i [ex]'in ona dik bileşeni, Z = X × Y (sağ elli)
        fun place(ex: V3, ey: V3, size: V3, at: V3 = center, part: Part = base): Part? {
            if (size.x < minSize || size.y < minSize || size.z < minSize) return null
            val y = ey.normalized(); val x = (ex - y * (ex dot y)).normalized()
            if (x.length < 0.5f || y.length < 0.5f) return null
            val z = x cross y
            return part.copy(position = at, rotation = Rot3.euler(x, y, z), scale = size)
        }
        return when (kind) {
            Suggest3Kind.SPHERE -> place(sk.u, sk.v, V3(2 * meanR, 2 * meanR, 2 * meanR))
            Suggest3Kind.TORUS -> place(sk.u, n, V3(2 * meanR, 2 * meanR, 2 * meanR))
            Suggest3Kind.CYLINDER -> {
                val p = SketchGeom.pca(rx, ry)
                val a = extent(rx, ry, cx, cy, p.ax, p.ay); val b = extent(rx, ry, cx, cy, -p.ay, p.ax)
                place(sk.dir(p.ax, p.ay), n, V3(2 * a, (a + b), 2 * b))
            }
            Suggest3Kind.DOME -> {
                // Taban düzlemde, kubbe düzlemin önüne doğru
                place(sk.u, n, V3(2 * meanR, 2 * meanR, 2 * meanR), at = center + n * (0.5f * meanR))
            }
            Suggest3Kind.ELLIPSOID, Suggest3Kind.CAPSULE -> {
                val p = SketchGeom.pca(rx, ry)
                val a = extent(rx, ry, cx, cy, p.ax, p.ay); val b = extent(rx, ry, cx, cy, -p.ay, p.ax)
                val major = sk.dir(p.ax, p.ay); val minor = sk.dir(-p.ay, p.ax)
                if (kind == Suggest3Kind.ELLIPSOID) place(major, minor, V3(2 * a, 2 * b, 2 * b))
                else place(minor, major, V3(2 * b, 2 * a, 2 * b))
            }
            Suggest3Kind.CUBE, Suggest3Kind.BOX, Suggest3Kind.SLAB -> {
                val r = SketchGeom.minRect(rx, ry)
                // Dikdörtgenin düzlemin yukarısına en yakın kenarı parçanın Y'si olur (dik dursun)
                val upFirst = abs(r.ay) > abs(r.ax)
                val (w, h) = if (upFirst) r.hh * 2 to r.hw * 2 else r.hw * 2 to r.hh * 2
                val ex = if (upFirst) sk.dir(r.ay, -r.ax) else sk.dir(r.ax, r.ay)
                val ey = if (upFirst) sk.dir(r.ax, r.ay) else sk.dir(-r.ay, r.ax)
                val eyUp = if ((ey dot sk.v) < 0f) -ey else ey
                val exR = if ((ex dot sk.u) < 0f) -ex else ex
                val at = sk.world(r.cx, r.cy)
                val depth = when (kind) {
                    Suggest3Kind.CUBE -> (w + h) / 2f
                    Suggest3Kind.SLAB -> max(w, h) * 0.08f
                    else -> min(w, h)
                }
                val size = if (kind == Suggest3Kind.CUBE) { val s = (w + h) / 2f; V3(s, s, s) } else V3(w, h, depth)
                place(exR, eyUp, size, at)
            }
            Suggest3Kind.CONE, Suggest3Kind.PYRAMID -> {
                val t = triangle(rx, ry) ?: return null
                val (apex, b1, b2) = t
                val mx = (b1.first + b2.first) / 2f; val my = (b1.second + b2.second) / 2f
                val axis = sk.dir(apex.first - mx, apex.second - my)
                val height = hypot(apex.first - mx, apex.second - my)
                val baseLen = hypot(b2.first - b1.first, b2.second - b1.second)
                val side = sk.dir(b2.first - b1.first, b2.second - b1.second)
                place(side, axis, V3(baseLen, height, baseLen), sk.world((apex.first + mx) / 2f, (apex.second + my) / 2f))
            }
            Suggest3Kind.PRISM3 -> {
                val t = triangle(rx, ry) ?: return null
                val (apex, b1, b2) = t
                val gx = (apex.first + b1.first + b2.first) / 3f; val gy = (apex.second + b1.second + b2.second) / 3f
                val r = (hypot(apex.first - gx, apex.second - gy) + hypot(b1.first - gx, b1.second - gy) + hypot(b2.first - gx, b2.second - gy)) / 3f
                // Üçgen yüz düzleme bakar: parçanın Y'si normal, ilk köşe (+Z) tepeye
                val ez = sk.dir(apex.first - gx, apex.second - gy)
                val ex = n cross ez
                place(ex, n, V3(2 * r, r * 1.2f, 2 * r), sk.world(gx, gy))
            }
            Suggest3Kind.PRISM6, Suggest3Kind.STAR -> {
                // İlk köşe (+Z) en uzak noktaya (yıldızda bir uç)
                var far = 0
                for (i in rx.indices) if (hypot(rx[i] - cx, ry[i] - cy) > hypot(rx[far] - cx, ry[far] - cy)) far = i
                val ez = sk.dir(rx[far] - cx, ry[far] - cy)
                val ex = n cross ez
                if (kind == Suggest3Kind.PRISM6) {
                    place(ex, n, V3(2 * meanR * 1.05f, meanR, 2 * meanR * 1.05f))
                } else {
                    val tips = starTips(rx, ry, cx, cy).coerceIn(4, 8)
                    val inner = (minR / maxR).coerceIn(0.2f, 0.8f)
                    val spec = (base.primitive ?: return null).copy(segments = tips, thickness = inner)
                    place(ex, n, V3(2 * maxR, 2 * maxR, 2 * maxR), part = ModelEdit.setPrimitive(base, spec))
                }
            }
            Suggest3Kind.ROD -> {
                val a = sk.world(xs[0], ys[0])
                val b = sk.world(xs.last(), ys.last())
                val len = (b - a).length
                if (len < minSize) return null
                val axis = (b - a).normalized()
                val d = max(len * 0.07f, minSize * 2f)
                place(axis cross n, axis, V3(d, len, d), (a + b) * 0.5f)
            }
            else -> null
        }
    }

    /** Fırça önerisi (tüp, şerit, duvar, torna, şişir, yay): izden fırça girdisi. */
    fun brush(kind: Suggest3Kind, sk: Sketch2, points: List<V3>): BrushFit? {
        val b = kind.brush ?: return null
        if (kind != Suggest3Kind.SPRING) {
            return BrushFit(b, BrushInput(points, null, sk.n, sk.v), null, null)
        }
        // Yay: sarmalın merkezinde, düzlemin yukarısı ekseninde, tur sayısı izin dönüşünden
        val xs = sk.xs; val ys = sk.ys
        val cx = xs.average().toFloat(); val cy = ys.average().toFloat()
        var maxR = 0f
        for (i in xs.indices) maxR = max(maxR, hypot(xs[i] - cx, ys[i] - cy))
        if (maxR < 1e-4f) return null
        val f = SketchFeatures.of(xs, ys) ?: return null
        val turns = (f[7] * 2f).coerceIn(2f, 8f)
        val radius = maxR * 0.85f
        val height = radius * 2.2f
        val wire = radius * 0.16f
        val center = sk.world(cx, cy)
        val steps = (turns * 20).toInt().coerceIn(40, 150)
        val pts = List(steps + 1) { i ->
            val t = i / steps.toFloat()
            val a = 2f * PI.toFloat() * turns * t
            center + sk.u * (radius * cos(a)) + sk.n * (radius * sin(a)) + sk.v * (height * (t - 0.5f))
        }
        return BrushFit(Brush3Kind.TUBE, BrushInput(pts, null, sk.u, sk.v, closed = false), wire, 0f)
    }

    /** Merkezden [ax],[ay] yönündeki en büyük uzaklık (yarı boy). */
    private fun extent(xs: FloatArray, ys: FloatArray, cx: Float, cy: Float, ax: Float, ay: Float): Float {
        var m = 0f
        for (i in xs.indices) m = max(m, abs((xs[i] - cx) * ax + (ys[i] - cy) * ay))
        return m
    }

    /** Zarftaki en büyük alanlı üçgen: tepe (en yüksek köşe) ve taban uçları. */
    private fun triangle(xs: FloatArray, ys: FloatArray): Triple<Pair<Float, Float>, Pair<Float, Float>, Pair<Float, Float>>? {
        val h = SketchGeom.hull(xs, ys)
        if (h.size < 3) return null
        var best = -1f; var bi = 0; var bj = 1; var bk = 2
        for (i in h.indices) for (j in i + 1 until h.size) for (k in j + 1 until h.size) {
            val a = abs((xs[h[j]] - xs[h[i]]) * (ys[h[k]] - ys[h[i]]) - (ys[h[j]] - ys[h[i]]) * (xs[h[k]] - xs[h[i]]))
            if (a > best) { best = a; bi = h[i]; bj = h[j]; bk = h[k] }
        }
        val p = listOf(xs[bi] to ys[bi], xs[bj] to ys[bj], xs[bk] to ys[bk])
        // Tepe düzlemin yukarısında en yüksek köşe (dik duran koni/piramit silüeti), taban karşı kenar
        val apex = p.indices.maxBy { p[it].second }
        val base = (0..2).filter { it != apex }
        return Triple(p[apex], p[base[0]], p[base[1]])
    }

    /** Yıldızın uç sayısı: merkez uzaklığının yerel en büyükleri (yumuşatılmış). */
    private fun starTips(xs: FloatArray, ys: FloatArray, cx: Float, cy: Float): Int {
        val n = xs.size
        val r = FloatArray(n) { hypot(xs[it] - cx, ys[it] - cy) }
        val s = FloatArray(n) { i -> (r[(i - 1 + n) % n] + 2 * r[i] + r[(i + 1) % n]) / 4f }
        val mean = s.average().toFloat()
        var count = 0
        for (i in 0 until n) {
            if (s[i] <= mean) continue
            var peak = true
            for (d in -4..4) if (d != 0 && s[(i + d + n) % n] > s[i]) { peak = false; break }
            if (peak) count++
        }
        return count
    }
}

/** Döndürme yardımcıları. */
object Rot3 {
    /**
     * Parçanın yerel eksenlerinin dünya yönlerinden (sağ elli, birim) derece cinsinden açılar (X, sonra Y, sonra Z:
     * [Transform3.rotation]'ın tersi).
     */
    fun euler(ex: V3, ey: V3, ez: V3): V3 {
        // Satır düzeninde R = [ex ey ez] (sütunlar)
        val r = floatArrayOf(ex.x, ey.x, ez.x, ex.y, ey.y, ez.y, ex.z, ey.z, ez.z)
        val sy = (-r[6]).coerceIn(-1f, 1f)
        val y = Math.toDegrees(kotlin.math.asin(sy.toDouble())).toFloat()
        return if (abs(sy) < 0.9999f) {
            V3(Math.toDegrees(atan2(r[7].toDouble(), r[8].toDouble())).toFloat(), y, Math.toDegrees(atan2(r[3].toDouble(), r[0].toDouble())).toFloat())
        } else {
            V3(Math.toDegrees(atan2(-r[5].toDouble(), r[4].toDouble())).toFloat(), y, 0f)
        }
    }
}

/** v27 öneri giriş noktası: dünya izinden öneriler (arka planda çağrılır). */
object Sketch3Suggest {
    /**
     * [points] dünya noktaları, [normal]/[up] çizim düzlemi, [drawn] çizilen fırça türü, [minLength] dünya biriminde en
     * kısa iz (çok kısa ve nokta gibi izlere öneri yok).
     */
    fun analyze(points: List<V3>, normal: V3, up: V3, drawn: Brush3Kind, model: SketchModel, minLength: Float): Pair<Sketch2, Sketch3Result>? {
        val sk = Sketch2.of(points, normal, up) ?: return null
        if (SketchGeom.length(sk.xs, sk.ys) < minLength) return null
        val f = SketchFeatures.of(sk.xs, sk.ys) ?: return null
        val r = model.suggest(f, drawn)
        if (r.suggestions.isEmpty()) return null
        return sk to r
    }
}

/**
 * v38 3B çizim ağının köşe eşiği (v30'da 2B şekil ağlarıyla ortak sabitti; v38'de 2B şekil ağları 4+ elemana geçti, 3B
 * düzenleyicinin önerisi aynı kalır).
 */
private const val SKETCH3_NET_MIN_CORNERS = 3
