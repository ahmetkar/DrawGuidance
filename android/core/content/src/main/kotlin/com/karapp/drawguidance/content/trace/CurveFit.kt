package com.karapp.drawguidance.content.trace

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Eğri uydurma ayarları (piksel biriminde).
 * [eps]: izin verilen en büyük sapma; [cornerCos]: köşe sayılan sapma açısının kosinüsü; [cornerScale]: köşe açısının
 * ölçüldüğü uzaklık; [flatTol]: Bézier eğrilerini noktalara açarken izin verilen sapma.
 */
internal class FitParams(
    val eps: Float,
    val smooth: Boolean,
    val cornerCos: Float,
    val cornerScale: Float,
    val flatTol: Float,
)

/**
 * Piksel kenarı ortalarından oluşan ham sınır çizgilerini sadeleştirir: [FitParams.smooth] kapalıysa Ramer–Douglas–Peucker,
 * açıksa köşeler bulunup köşe aralarına Schneider yöntemiyle kübik Bézier uydurulur ve eğriler yeniden noktalara açılır.
 */
internal object CurveFit {

    /** Köşeli (RDP) modda tolerans çarpanı: eğri uydurma olmadığından biraz daha sıkı. */
    private const val POLY_EPS = 0.75f

    /** Açık çizgi (uçlar sabit, dahil). Noktalar [out]'a eklenir. */
    fun open(src: FloatArray, n: Int, p: FitParams, out: FloatList) {
        if (n <= 2) { for (i in 0 until n * 2) out.add(src[i]); return }
        val d = src.copyOf(n * 2)
        val corners = corners(d, n, false, p)
        val keep = if (p.smooth) null else BooleanArray(n)
        out.add(d[0], d[1])
        var a = 0
        for (c in corners) { piece(d, a, c, p, keep, out); a = c }
        piece(d, a, n - 1, p, keep, out)
    }

    /** Kapalı çizgi (ilk nokta tekrarlanmaz). Noktalar [out]'a eklenir. */
    fun loop(src: FloatArray, n: Int, p: FitParams, out: FloatList) {
        if (n <= 3) { for (i in 0 until n * 2) out.add(src[i]); return }
        val d0 = src.copyOf(n * 2)
        val corners = corners(d0, n, true, p)
        if (corners.isEmpty() && !p.smooth) {
            // Köşesiz çokgen: en uzak iki noktadan bölünüp RDP
            var far = 0; var fd = -1f
            for (i in 1 until n) {
                val dd = sq(d0[i * 2] - d0[0]) + sq(d0[i * 2 + 1] - d0[1])
                if (dd > fd) { fd = dd; far = i }
            }
            val ext = d0.copyOf(n * 2 + 2)
            ext[n * 2] = d0[0]; ext[n * 2 + 1] = d0[1]
            val keep = BooleanArray(n + 1)
            val e2 = sq(p.eps * POLY_EPS)
            rdp(ext, 0, far, e2, keep)
            rdp(ext, far, n, e2, keep)
            for (i in 0 until n) if (keep[i]) out.add(ext[i * 2], ext[i * 2 + 1])
            return
        }
        if (corners.isEmpty()) {
            // Köşesiz: döngüsel yumuşatma ([1,2,1]/4, iki kez)
            val tmp = FloatArray(n * 2)
            repeat(2) {
                for (i in 0 until n) {
                    val a = (i - 1 + n) % n; val b = (i + 1) % n
                    tmp[i * 2] = (d0[a * 2] + 2 * d0[i * 2] + d0[b * 2]) * 0.25f
                    tmp[i * 2 + 1] = (d0[a * 2 + 1] + 2 * d0[i * 2 + 1] + d0[b * 2 + 1]) * 0.25f
                }
                tmp.copyInto(d0)
            }
        }
        // İlk köşe (yoksa 0) başa gelecek şekilde döndür, sona başlangıç noktası eklenir
        val start = if (corners.isNotEmpty()) corners[0] else 0
        val ext = FloatArray(n * 2 + 2)
        for (i in 0..n) { val j = (start + i) % n; ext[i * 2] = d0[j * 2]; ext[i * 2 + 1] = d0[j * 2 + 1] }
        val tmp = FloatList(n * 2)
        tmp.add(ext[0], ext[1])
        if (corners.isEmpty()) {
            // Köşesiz kapalı eğri: iki parçaya bölünür, bölme noktalarında ortak teğet (pürüzsüz birleşim)
            val mid = n / 2
            val t0 = centreTangentCyclic(d0, n, start, p.cornerScale)
            val tm = centreTangentCyclic(d0, n, (start + mid) % n, p.cornerScale)
            schneider(ext, 0, mid, -t0[0], -t0[1], tm[0], tm[1], p, tmp)
            schneider(ext, mid, n, -tm[0], -tm[1], t0[0], t0[1], p, tmp)
        } else {
            val keep = if (p.smooth) null else BooleanArray(n + 1)
            var a = 0
            for (k in 1 until corners.size) {
                val c = (corners[k] - start + n) % n
                piece(ext, a, c, p, keep, tmp)
                a = c
            }
            piece(ext, a, n, p, keep, tmp)
        }
        // Son nokta ilk noktanın tekrarıdır
        for (i in 0 until tmp.size - 2) out.add(tmp[i])
    }

    // --- Köşeler ---

    /** Köşe sırası (artan). Köşe noktaları [d] içinde gerçek köşe konumuna (iki kenar doğrusunun kesişimi) kaydırılır. */
    private fun corners(d: FloatArray, n: Int, closed: Boolean, p: FitParams): IntArray {
        if (n < 5) return IntArray(0)
        val L = p.cornerScale
        val candIdx = IntList(); val cand1 = FloatList(); val cand2 = FloatList()
        val lo = if (closed) 0 else 2
        val hi = if (closed) n else n - 2
        // İki ölçekte sapma: gerçek köşede iki ölçekte de büyük (kenarlar düz), eğride ölçekle büyür. Büyük ölçek,
        // kenar yumuşatmayla körelmiş (özellikle içbükey) köşeleri de yakalar.
        for (i in lo until hi) {
            val c1 = deflectionCos(d, n, i, L, closed)
            val c2 = deflectionCos(d, n, i, L * 2f, closed)
            if (c1 < p.cornerCos || c2 < p.cornerCos) { candIdx.add(i); cand1.add(c1); cand2.add(c2) }
        }
        if (candIdx.size == 0) return IntArray(0)
        val accepted = IntList()
        fun accept(order: List<Int>, sep: Int) {
            for (o in order) {
                val i = candIdx[o]
                var ok = true
                for (k in 0 until accepted.size) {
                    var dist = abs(accepted[k] - i)
                    if (closed) dist = min(dist, n - dist)
                    if (dist <= sep) { ok = false; break }
                }
                if (ok) accepted.add(i)
            }
        }
        // Önce küçük ölçekte keskin köşeler, sonra yalnız büyük ölçekte görülenler (daha geniş bastırma aralığıyla)
        accept((0 until candIdx.size).filter { cand1[it] < p.cornerCos }
            .sortedWith(compareBy<Int> { cand1[it] }.thenBy { candIdx[it] }), ceil(L * 2f).toInt())
        accept((0 until candIdx.size).filter { cand1[it] >= p.cornerCos }
            .sortedWith(compareBy<Int> { cand2[it] }.thenBy { candIdx[it] }), ceil(L * 4f).toInt())
        val res = IntArray(accepted.size) { accepted[it] }
        res.sort()
        for (i in res) refineCorner(d, n, i, L, closed)
        return res
    }

    /** [i]'deki sapma açısının kosinüsü ([len] uzaklıktaki önceki ve sonraki noktalarla); ölçülemezse 1. */
    private fun deflectionCos(d: FloatArray, n: Int, i: Int, len: Float, closed: Boolean): Float {
        val a = walk(d, n, i, -1, len, closed)
        val b = walk(d, n, i, 1, len, closed)
        if (a < 0 || b < 0) return 1f
        val ux = d[i * 2] - d[a * 2]; val uy = d[i * 2 + 1] - d[a * 2 + 1]
        val vx = d[b * 2] - d[i * 2]; val vy = d[b * 2 + 1] - d[i * 2 + 1]
        val lu = sqrt(ux * ux + uy * uy); val lv = sqrt(vx * vx + vy * vy)
        if (lu < 1e-4f || lv < 1e-4f) return 1f
        return (ux * vx + uy * vy) / (lu * lv)
    }

    /** [i]'den [dir] yönünde, [i]'ye uzaklığı ≥ [len] olan ilk nokta; açık çizgide uca varılırsa uç ya da -1. */
    private fun walk(d: FloatArray, n: Int, i: Int, dir: Int, len: Float, closed: Boolean): Int {
        val x = d[i * 2]; val y = d[i * 2 + 1]
        val l2 = len * len
        var j = i
        for (s in 1 until n) {
            var nj = j + dir
            if (nj < 0 || nj >= n) {
                if (!closed) return if (sq(d[j * 2] - x) + sq(d[j * 2 + 1] - y) >= l2 * 0.25f) j else -1
                nj = (nj + n) % n
            }
            j = nj
            if (sq(d[j * 2] - x) + sq(d[j * 2 + 1] - y) >= l2) return j
        }
        return -1
    }

    private fun refineCorner(d: FloatArray, n: Int, i: Int, L: Float, closed: Boolean) {
        val a1 = walk(d, n, i, -1, L, closed); val a2 = walk(d, n, i, -1, L * 2.5f, closed)
        val b1 = walk(d, n, i, 1, L, closed); val b2 = walk(d, n, i, 1, L * 2.5f, closed)
        if (a1 < 0 || a2 < 0 || b1 < 0 || b2 < 0 || a1 == a2 || b1 == b2) return
        val px = d[a2 * 2]; val py = d[a2 * 2 + 1]
        val rx = d[a1 * 2] - px; val ry = d[a1 * 2 + 1] - py
        val qx = d[b1 * 2]; val qy = d[b1 * 2 + 1]
        val sx = d[b2 * 2] - qx; val sy = d[b2 * 2 + 1] - qy
        val den = rx * sy - ry * sx
        if (abs(den) < 1e-6f * sqrt((rx * rx + ry * ry) * (sx * sx + sy * sy))) return
        val t = ((qx - px) * sy - (qy - py) * sx) / den
        val ix = px + t * rx; val iy = py + t * ry
        if (sq(ix - d[i * 2]) + sq(iy - d[i * 2 + 1]) <= 1.2f * 1.2f) { d[i * 2] = ix; d[i * 2 + 1] = iy }
    }

    // --- Parça uydurma ---

    /** [a]..[b] arası (a zaten yazılmış) noktaları [out]'a ekler; [keep] verilmişse (köşeli mod) RDP uygulanır. */
    private fun piece(d: FloatArray, a: Int, b: Int, p: FitParams, keep: BooleanArray?, out: FloatList) {
        if (b - a <= 1) { out.add(d[b * 2], d[b * 2 + 1]); return }
        if (keep != null) {
            rdp(d, a, b, sq(p.eps * POLY_EPS), keep)
            for (i in a + 1..b) if (keep[i]) out.add(d[i * 2], d[i * 2 + 1])
            return
        }
        presmooth(d, a, b)
        // Doğru parçası: tüm noktalar kirişe yeterince yakınsa
        if (maxChordDev2(d, a, b) <= p.eps * p.eps * 0.36f) { out.add(d[b * 2], d[b * 2 + 1]); return }
        val t1 = sideTangent(d, a, b, 1, p.cornerScale)
        val t2 = sideTangent(d, b, a, -1, p.cornerScale)
        schneider(d, a, b, t1[0], t1[1], t2[0], t2[1], p, out)
    }

    /**
     * Piksel basamaklarını yumuşatır: uçlar sabit, iç noktalara iki kez [1,2,1]/4 süzgeci (köşeler parça uçlarında
     * olduğundan korunur). Eğrilikteki küçülme ihmal edilebilir (σ≈1 px).
     */
    private fun presmooth(d: FloatArray, a: Int, b: Int) {
        if (b - a < 3) return
        val n = b - a + 1
        val tmp = FloatArray(n * 2)
        repeat(2) {
            for (k in 1 until n - 1) {
                val i = a + k
                tmp[k * 2] = (d[i * 2 - 2] + 2 * d[i * 2] + d[i * 2 + 2]) * 0.25f
                tmp[k * 2 + 1] = (d[i * 2 - 1] + 2 * d[i * 2 + 1] + d[i * 2 + 3]) * 0.25f
            }
            for (k in 1 until n - 1) { d[(a + k) * 2] = tmp[k * 2]; d[(a + k) * 2 + 1] = tmp[k * 2 + 1] }
        }
    }

    private fun maxChordDev2(d: FloatArray, a: Int, b: Int): Float {
        val ax = d[a * 2]; val ay = d[a * 2 + 1]
        val bx = d[b * 2]; val by = d[b * 2 + 1]
        var m = 0f
        for (i in a + 1 until b) {
            val dd = segDist2(d[i * 2], d[i * 2 + 1], ax, ay, bx, by)
            if (dd > m) m = dd
        }
        return m
    }

    /** [from]'dan [dir] yönünde ([to]'yu aşmadan) tek yanlı birim teğet (dışa doğru). */
    private fun sideTangent(d: FloatArray, from: Int, to: Int, dir: Int, len: Float): FloatArray {
        val x = d[from * 2]; val y = d[from * 2 + 1]
        var j = from + dir
        while (j != to && sq(d[j * 2] - x) + sq(d[j * 2 + 1] - y) < len * len) j += dir
        return unit(d[j * 2] - x, d[j * 2 + 1] - y)
    }

    /** Kapalı çizgide [i]'de iki yanlı teğet (geriye doğru bakan: önceki − sonraki). */
    private fun centreTangentCyclic(d: FloatArray, n: Int, i: Int, len: Float): FloatArray {
        val a = walk(d, n, i, -1, len, true).let { if (it < 0) (i - 1 + n) % n else it }
        val b = walk(d, n, i, 1, len, true).let { if (it < 0) (i + 1) % n else it }
        return unit(d[a * 2] - d[b * 2], d[a * 2 + 1] - d[b * 2 + 1])
    }

    private fun unit(x: Float, y: Float): FloatArray {
        val l = sqrt(x * x + y * y)
        return if (l < 1e-9f) floatArrayOf(1f, 0f) else floatArrayOf(x / l, y / l)
    }

    /**
     * Schneider (Graphics Gems I) kübik Bézier uydurma, yığınla (özyinelemesiz). [first] noktası yazılmış kabul edilir;
     * eğriler açılarak [first]+1..[last] kısmı [out]'a eklenir. t1: başlangıçta ileri, t2: sonda geri bakan teğet.
     */
    private fun schneider(
        d: FloatArray, first0: Int, last0: Int, t1x0: Float, t1y0: Float, t2x0: Float, t2y0: Float,
        p: FitParams, out: FloatList,
    ) {
        val e2 = p.eps * p.eps
        val stackI = IntList(); val stackF = FloatList()
        stackI.add(first0); stackI.add(last0); stackF.add(t1x0); stackF.add(t1y0); stackF.add(t2x0); stackF.add(t2y0)
        val u = FloatArray(last0 - first0 + 1)
        val bez = FloatArray(8)
        val errOut = FloatArray(1)
        while (stackI.size > 0) {
            val last = stackI[stackI.size - 1]; val first = stackI[stackI.size - 2]
            val t2y = stackF[stackF.size - 1]; val t2x = stackF[stackF.size - 2]
            val t1y = stackF[stackF.size - 3]; val t1x = stackF[stackF.size - 4]
            pop(stackI, 2); popF(stackF, 4)
            if (last - first <= 1) { out.add(d[last * 2], d[last * 2 + 1]); continue }
            chordParams(d, first, last, u)
            generate(d, first, last, u, t1x, t1y, t2x, t2y, bez)
            var split = maxError(d, first, last, u, bez, errOut)
            var err = errOut[0]
            if (err > e2 && err < e2 * 16f) {
                for (it in 0 until 4) {
                    reparam(d, first, last, u, bez)
                    generate(d, first, last, u, t1x, t1y, t2x, t2y, bez)
                    split = maxError(d, first, last, u, bez, errOut)
                    err = errOut[0]
                    if (err <= e2) break
                }
            }
            if (err <= e2) { flatten(bez, p.flatTol, out); continue }
            if (split <= first || split >= last) split = (first + last) / 2
            // Bölme noktasında ortak teğet
            val len = p.cornerScale
            val sx = d[split * 2]; val sy = d[split * 2 + 1]
            var a = split - 1
            while (a > first && sq(d[a * 2] - sx) + sq(d[a * 2 + 1] - sy) < len * len) a--
            var b = split + 1
            while (b < last && sq(d[b * 2] - sx) + sq(d[b * 2 + 1] - sy) < len * len) b++
            val tc = unit(d[a * 2] - d[b * 2], d[a * 2 + 1] - d[b * 2 + 1])
            // Önce sol yarı işlensin diye sağ yarı önce yığına
            stackI.add(split); stackI.add(last); stackF.add(-tc[0]); stackF.add(-tc[1]); stackF.add(t2x); stackF.add(t2y)
            stackI.add(first); stackI.add(split); stackF.add(t1x); stackF.add(t1y); stackF.add(tc[0]); stackF.add(tc[1])
        }
    }

    private fun pop(l: IntList, k: Int) { repeat(k) { l.pop() } }
    private fun popF(l: FloatList, k: Int) { repeat(k) { l.pop() } }

    private fun chordParams(d: FloatArray, first: Int, last: Int, u: FloatArray) {
        u[0] = 0f
        for (i in first + 1..last) {
            u[i - first] = u[i - first - 1] + sqrt(sq(d[i * 2] - d[i * 2 - 2]) + sq(d[i * 2 + 1] - d[i * 2 - 1]))
        }
        val total = u[last - first]
        if (total <= 0f) { for (i in 0..last - first) u[i] = i.toFloat() / (last - first); return }
        for (i in 1..last - first) u[i] /= total
    }

    private fun generate(
        d: FloatArray, first: Int, last: Int, u: FloatArray,
        t1x: Float, t1y: Float, t2x: Float, t2y: Float, bez: FloatArray,
    ) {
        val p0x = d[first * 2]; val p0y = d[first * 2 + 1]
        val p3x = d[last * 2]; val p3y = d[last * 2 + 1]
        var c00 = 0.0; var c01 = 0.0; var c11 = 0.0; var x0 = 0.0; var x1 = 0.0
        for (i in 0..last - first) {
            val t = u[i].toDouble(); val m = 1 - t
            val b0 = m * m * m; val b1 = 3 * t * m * m; val b2 = 3 * t * t * m; val b3 = t * t * t
            val a1x = t1x * b1; val a1y = t1y * b1
            val a2x = t2x * b2; val a2y = t2y * b2
            c00 += a1x * a1x + a1y * a1y
            c01 += a1x * a2x + a1y * a2y
            c11 += a2x * a2x + a2y * a2y
            val px = d[(first + i) * 2] - (p0x * (b0 + b1) + p3x * (b2 + b3))
            val py = d[(first + i) * 2 + 1] - (p0y * (b0 + b1) + p3y * (b2 + b3))
            x0 += a1x * px + a1y * py
            x1 += a2x * px + a2y * py
        }
        val det = c00 * c11 - c01 * c01
        var al = if (abs(det) < 1e-12) 0.0 else (x0 * c11 - x1 * c01) / det
        var ar = if (abs(det) < 1e-12) 0.0 else (c00 * x1 - c01 * x0) / det
        val seg = sqrt(sq(p3x - p0x) + sq(p3y - p0y)).toDouble()
        val eps = 1e-6 * seg
        if (al < eps || ar < eps || al > seg * 1.5 || ar > seg * 1.5) { al = seg / 3; ar = seg / 3 }
        bez[0] = p0x; bez[1] = p0y
        bez[2] = (p0x + t1x * al).toFloat(); bez[3] = (p0y + t1y * al).toFloat()
        bez[4] = (p3x + t2x * ar).toFloat(); bez[5] = (p3y + t2y * ar).toFloat()
        bez[6] = p3x; bez[7] = p3y
    }

    /** En büyük kare sapma [errOut]'a yazılır; sapmanın en büyük olduğu nokta döner. */
    private fun maxError(d: FloatArray, first: Int, last: Int, u: FloatArray, bez: FloatArray, errOut: FloatArray): Int {
        var maxD = 0f; var split = (first + last) / 2
        for (i in first + 1 until last) {
            val t = u[i - first]; val m = 1 - t
            val b0 = m * m * m; val b1 = 3 * t * m * m; val b2 = 3 * t * t * m; val b3 = t * t * t
            val x = b0 * bez[0] + b1 * bez[2] + b2 * bez[4] + b3 * bez[6]
            val y = b0 * bez[1] + b1 * bez[3] + b2 * bez[5] + b3 * bez[7]
            val dd = sq(x - d[i * 2]) + sq(y - d[i * 2 + 1])
            if (dd >= maxD) { maxD = dd; split = i }
        }
        errOut[0] = maxD
        return split
    }

    /** Newton–Raphson ile parametrelerin iyileştirilmesi. */
    private fun reparam(d: FloatArray, first: Int, last: Int, u: FloatArray, bez: FloatArray) {
        for (i in first + 1 until last) {
            val t = u[i - first]; val m = 1 - t
            val qx = m * m * m * bez[0] + 3 * t * m * m * bez[2] + 3 * t * t * m * bez[4] + t * t * t * bez[6]
            val qy = m * m * m * bez[1] + 3 * t * m * m * bez[3] + 3 * t * t * m * bez[5] + t * t * t * bez[7]
            // Q'
            val q1x = 3 * (m * m * (bez[2] - bez[0]) + 2 * t * m * (bez[4] - bez[2]) + t * t * (bez[6] - bez[4]))
            val q1y = 3 * (m * m * (bez[3] - bez[1]) + 2 * t * m * (bez[5] - bez[3]) + t * t * (bez[7] - bez[5]))
            // Q''
            val q2x = 6 * (m * (bez[4] - 2 * bez[2] + bez[0]) + t * (bez[6] - 2 * bez[4] + bez[2]))
            val q2y = 6 * (m * (bez[5] - 2 * bez[3] + bez[1]) + t * (bez[7] - 2 * bez[5] + bez[3]))
            val dx = qx - d[i * 2]; val dy = qy - d[i * 2 + 1]
            val num = dx * q1x + dy * q1y
            val den = q1x * q1x + q1y * q1y + dx * q2x + dy * q2y
            if (abs(den) > 1e-9f) u[i - first] = (t - num / den).coerceIn(0f, 1f)
        }
        // Parametreler sıralı kalmalı
        for (i in 1 until last - first) if (u[i] < u[i - 1]) u[i] = u[i - 1]
    }

    /**
     * Kübik Bézier'i sapma ≤ tol olacak kadar doğru parçasına açar (başlangıç noktası yazılmaz).
     * Eşit adımlı bölmede hata ≤ max|B''| / (8 n²); max|B''| = 6·max(|P0−2P1+P2|, |P1−2P2+P3|).
     */
    private fun flatten(b: FloatArray, tol: Float, out: FloatList) {
        val ax = b[0] - 2 * b[2] + b[4]; val ay = b[1] - 2 * b[3] + b[5]
        val bx = b[2] - 2 * b[4] + b[6]; val by = b[3] - 2 * b[5] + b[7]
        val acc = 6f * sqrt(max(ax * ax + ay * ay, bx * bx + by * by))
        val steps = ceil(sqrt(acc / (8f * tol))).toInt().coerceIn(1, 32)
        for (s in 1..steps) {
            val t = s.toFloat() / steps; val m = 1 - t
            val b0 = m * m * m; val b1 = 3 * t * m * m; val b2 = 3 * t * t * m; val b3 = t * t * t
            out.add(b0 * b[0] + b1 * b[2] + b2 * b[4] + b3 * b[6], b0 * b[1] + b1 * b[3] + b2 * b[5] + b3 * b[7])
        }
    }

    // --- RDP ---

    private fun rdp(d: FloatArray, first: Int, last: Int, eps2: Float, keep: BooleanArray) {
        keep[first] = true; keep[last] = true
        val stack = IntList()
        stack.add(first); stack.add(last)
        while (stack.size > 0) {
            val b = stack[stack.size - 1]; val a = stack[stack.size - 2]
            stack.pop(); stack.pop()
            if (b - a < 2) continue
            var best = -1; var bd = eps2
            val ax = d[a * 2]; val ay = d[a * 2 + 1]; val bx = d[b * 2]; val by = d[b * 2 + 1]
            for (i in a + 1 until b) {
                val dd = segDist2(d[i * 2], d[i * 2 + 1], ax, ay, bx, by)
                if (dd > bd) { bd = dd; best = i }
            }
            if (best >= 0) {
                keep[best] = true
                stack.add(a); stack.add(best); stack.add(best); stack.add(b)
            }
        }
    }

    fun segDist2(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        if (l2 < 1e-12f) return sq(px - ax) + sq(py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
        return sq(px - (ax + t * dx)) + sq(py - (ay + t * dy))
    }

    private fun sq(v: Float) = v * v
}
