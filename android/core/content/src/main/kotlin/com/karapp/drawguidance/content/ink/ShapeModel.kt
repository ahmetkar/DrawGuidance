package com.karapp.drawguidance.content.ink

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Gövde şekli öznitelikleri: çizgiler en-boy oranı korunarak birim kutuya ölçeklenir; 8×8 ızgarada 4 yönsüz doğrultu
 * kanalı (0°, 45°, 90°, 135°) + uç noktası kanalı, çift doğrusal dağıtım ve hafif bulanıklaştırma, L2 normalize.
 * Çizgi yönüne ve sırasına duyarsızdır.
 */
internal object ShapeFeatures {
    const val G = 8
    const val CH = 5
    const val DIM = G * G * CH
    private const val STEP = 0.03f
    private const val END_W = 0.22f

    /** Kutunun köşegen açısı (0 = ince dikey, 0.5 = kare, 1 = yassı yatay). */
    fun aspect(strokes: List<Poly>): Float {
        val b = Box.of(strokes)
        return (atan2(b.w.toDouble(), b.h.toDouble()) / (PI / 2)).toFloat()
    }

    fun extract(strokes: List<Poly>): FloatArray {
        val b = Box.of(strokes)
        val s = max(max(b.w, b.h), 1e-4f)
        val cx = b.cx; val cy = b.cy
        val f = FloatArray(DIM)
        for (st in strokes) {
            val nx = FloatArray(st.n) { (st.x[it] - cx) / s + 0.5f }
            val ny = FloatArray(st.n) { (st.y[it] - cy) / s + 0.5f }
            val r = Geo.smooth(Geo.resample(Poly(nx, ny), STEP * 0.5f), 2).let { Geo.resample(it, STEP) }
            if (r.n < 2 || r.length < STEP * 0.5f) {
                splat(f, 4, r.x[0], r.y[0], END_W * 2)
                continue
            }
            for (i in 0 until r.n - 1) {
                val dx = r.x[i + 1] - r.x[i]; val dy = r.y[i + 1] - r.y[i]
                val len = hypot(dx, dy)
                if (len < 1e-6f) continue
                var a = atan2(dy.toDouble(), dx.toDouble())
                if (a < 0) a += PI
                val bin = a / (PI / 4)
                val b0 = floor(bin).toInt()
                val fr = (bin - b0).toFloat()
                val mx = (r.x[i] + r.x[i + 1]) * 0.5f; val my = (r.y[i] + r.y[i + 1]) * 0.5f
                splat(f, b0 % 4, mx, my, len * (1 - fr))
                splat(f, (b0 + 1) % 4, mx, my, len * fr)
            }
            // kapalı döngülerin (o, 0, 8 ...) başlangıç noktası keyfidir: uç noktası sayılmaz
            val gap = hypot(r.x[r.n - 1] - r.x[0], r.y[r.n - 1] - r.y[0])
            val ext = max(max(r.maxX - r.minX, r.maxY - r.minY), 1e-4f)
            if (gap > 0.15f * ext || r.length < 1.5f * ext) {
                splat(f, 4, r.x[0], r.y[0], END_W)
                splat(f, 4, r.x[r.n - 1], r.y[r.n - 1], END_W)
            }
        }
        blur(f)
        var norm = 0f
        for (v in f) norm += v * v
        norm = sqrt(norm)
        if (norm > 0f) for (i in f.indices) f[i] /= norm
        return f
    }

    const val CLOUD = 32

    /** Aynı normalleştirmeyle (kutu merkezi, en uzun kenar = 1) yaklaşık [CLOUD] noktalık nokta bulutu (x0,y0,x1,y1...). */
    fun cloud(strokes: List<Poly>): FloatArray {
        val b = Box.of(strokes)
        val s = max(max(b.w, b.h), 1e-4f)
        var total = 0f
        for (st in strokes) total += st.length
        val out = ArrayList<Float>(CLOUD * 2 + 8)
        for (st in strokes) {
            val k = if (total > 0f) max(1, Math.round(CLOUD * st.length / total)) else 1
            val r = if (k <= 1 || st.length <= 0f) Poly(floatArrayOf(st.x[0]), floatArrayOf(st.y[0])) else Geo.resample(st, st.length / (k - 1))
            for (i in 0 until r.n) { out += (r.x[i] - b.cx) / s; out += (r.y[i] - b.cy) / s }
        }
        return out.toFloatArray()
    }

    /** Karesel ortalama en yakın komşu uzaklığı (iki yönlü): eksik/fazla parçaları cezalandırır. */
    fun chamfer(a: FloatArray, b: FloatArray): Float {
        fun oneWay(p: FloatArray, q: FloatArray): Float {
            var sum = 0f
            var i = 0
            while (i < p.size) {
                var best = Float.POSITIVE_INFINITY
                var j = 0
                while (j < q.size) {
                    val dx = p[i] - q[j]; val dy = p[i + 1] - q[j + 1]
                    val d = dx * dx + dy * dy
                    if (d < best) best = d
                    j += 2
                }
                sum += best
                i += 2
            }
            return sum / (p.size / 2)
        }
        return sqrt(0.5f * (oneWay(a, b) + oneWay(b, a)))
    }

    private fun splat(f: FloatArray, ch: Int, u: Float, v: Float, w: Float) {
        val gx = (u * G - 0.5f).coerceIn(0f, G - 1f)
        val gy = (v * G - 0.5f).coerceIn(0f, G - 1f)
        val x0 = min(floor(gx).toInt(), G - 2); val y0 = min(floor(gy).toInt(), G - 2)
        val fx = gx - x0; val fy = gy - y0
        val base = ch * G * G
        f[base + y0 * G + x0] += w * (1 - fx) * (1 - fy)
        f[base + y0 * G + x0 + 1] += w * fx * (1 - fy)
        f[base + (y0 + 1) * G + x0] += w * (1 - fx) * fy
        f[base + (y0 + 1) * G + x0 + 1] += w * fx * fy
    }

    private fun blur(f: FloatArray) {
        val tmp = FloatArray(G * G)
        for (ch in 0 until CH) {
            val base = ch * G * G
            for (y in 0 until G) for (x in 0 until G) {
                var s = f[base + y * G + x] * 0.5f
                if (x > 0) s += f[base + y * G + x - 1] * 0.25f
                if (x < G - 1) s += f[base + y * G + x + 1] * 0.25f
                tmp[y * G + x] = s
            }
            for (y in 0 until G) for (x in 0 until G) {
                var s = tmp[y * G + x] * 0.5f
                if (y > 0) s += tmp[(y - 1) * G + x] * 0.25f
                if (y < G - 1) s += tmp[(y + 1) * G + x] * 0.25f
                f[base + y * G + x] = s
            }
        }
    }
}

/**
 * Şablon kümesi: her taslak küçük bozulmalarla (eğim ±, döndürme ±, yatay ölçek ±) çoğaltılır; sorgu en yakın
 * komşu ile etiket başına en küçük uzaklığı alır. İlk kullanımda bir kez kurulur.
 */
internal object ShapeModel {

    private class Distort(val slantDeg: Float, val rotDeg: Float, val sx: Float)

    private val distortions = listOf(
        Distort(0f, 0f, 1f),
        Distort(12f, 0f, 1f), Distort(-10f, 0f, 1f),
        Distort(0f, 8f, 1f), Distort(0f, -8f, 1f),
        Distort(0f, 0f, 0.8f), Distort(0f, 0f, 1.22f),
        Distort(7f, -4f, 0.9f), Distort(-5f, 5f, 1.1f),
    )

    const val ASPECT_W = 1.2f

    val labels: List<String> = Glyphs.sketches.map { it.first }.distinct()
    private val labelIndex: Map<String, Int> = labels.withIndex().associate { it.value to it.index }
    fun indexOf(label: String): Int = labelIndex[label] ?: -1

    private class Bank(
        val feats: FloatArray, val coarse: FloatArray, val rowLabel: IntArray, val rowAspect: FloatArray, val rows: Int,
        val labelRows: Array<IntArray>, val clouds: Array<FloatArray>,
    )

    /** Nokta bulutu uzaklığının ağırlığı ve kaç etikette hesaplanacağı. */
    const val CHAMFER_W = 2.0f
    const val CHAMFER_LABELS = 8

    /** 8×8 ızgarayı 4×4'e indirger (2×2 blok toplamı), L2 normalize. */
    private fun pool(f: FloatArray): FloatArray {
        val g = ShapeFeatures.G; val h = g / 2
        val out = FloatArray(h * h * ShapeFeatures.CH)
        for (ch in 0 until ShapeFeatures.CH) for (y in 0 until g) for (x in 0 until g)
            out[ch * h * h + (y / 2) * h + x / 2] += f[ch * g * g + y * g + x]
        var n = 0f
        for (v in out) n += v * v
        n = sqrt(n)
        if (n > 0f) for (i in out.indices) out[i] /= n
        return out
    }

    const val PREFILTER_LABELS = 12

    private val bank: Bank by lazy { build() }

    /** Şablonları önceden hazırlar (isteğe bağlı; ilk tanıma da yapar). */
    fun warmUp() { bank.rows }

    private fun build(): Bank {
        val feats = ArrayList<FloatArray>()
        val lab = ArrayList<Int>()
        val asp = ArrayList<Float>()
        val clouds = ArrayList<FloatArray>()
        for ((label, src) in Glyphs.sketches) {
            val polys = Sketch.renderAll(src, 8)
            for (d in distortions) {
                val t = distort(polys, d)
                feats += ShapeFeatures.extract(t)
                lab += labelIndex.getValue(label)
                asp += ShapeFeatures.aspect(t)
                clouds += ShapeFeatures.cloud(t)
            }
        }
        val all = FloatArray(feats.size * ShapeFeatures.DIM)
        feats.forEachIndexed { i, f -> f.copyInto(all, i * ShapeFeatures.DIM) }
        val cd = POOLED
        val coarse = FloatArray(feats.size * cd)
        feats.forEachIndexed { i, f -> pool(f).copyInto(coarse, i * cd) }
        val labelRows = Array(labels.size) { l -> lab.indices.filter { lab[it] == l }.toIntArray() }
        return Bank(all, coarse, lab.toIntArray(), asp.toFloatArray(), feats.size, labelRows, clouds.toTypedArray())
    }

    private fun distort(polys: List<Poly>, d: Distort): List<Poly> {
        val b = Box.of(polys)
        val sh = tan(Math.toRadians(d.slantDeg.toDouble())).toFloat()
        val r = Math.toRadians(d.rotDeg.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        // önce eğim + yatay ölçek (merkez etrafında), sonra döndürme
        return polys.map { p ->
            p.affine(d.sx, -sh, -d.sx * b.cx + sh * b.cy + b.cx, 0f, 1f, 0f)
                .affine(c, -s, b.cx - c * b.cx + s * b.cy, s, c, b.cy - s * b.cx - c * b.cy)
        }
    }

    private val POOLED = (ShapeFeatures.G / 2) * (ShapeFeatures.G / 2) * ShapeFeatures.CH

    /**
     * Etiket başına uzaklık (küçük = benzer): 2 − 2·kosinüs + en-boy terimi. Önce 4×4 kaba özniteliklerle tüm şablonlar
     * taranır, yalnız en yakın [PREFILTER_LABELS] etiketin şablonları tam çözünürlükte karşılaştırılır; diğer etiketlere
     * hesaplanan en büyük uzaklıktan biraz fazlası verilir.
     */
    fun distances(main: List<Poly>): FloatArray {
        val q = ShapeFeatures.extract(main)
        val qc = pool(q)
        val qa = ShapeFeatures.aspect(main)
        val bk = bank
        val nl = labels.size
        val coarseBest = FloatArray(nl) { Float.POSITIVE_INFINITY }
        val cd = POOLED
        for (r in 0 until bk.rows) {
            val dot = dot(qc, bk.coarse, r * cd, cd)
            val da = qa - bk.rowAspect[r]
            val d = 2f - 2f * dot + ASPECT_W * da * da
            val l = bk.rowLabel[r]
            if (d < coarseBest[l]) coarseBest[l] = d
        }
        val top = (0 until nl).sortedBy { coarseBest[it] }.take(PREFILTER_LABELS)
        val out = FloatArray(nl) { Float.NaN }
        val bestRow = IntArray(nl) { -1 }
        val dim = ShapeFeatures.DIM
        var worst = 0f
        for (l in top) {
            var best = Float.POSITIVE_INFINITY
            for (r in bk.labelRows[l]) {
                val dot = dot(q, bk.feats, r * dim, dim)
                val da = qa - bk.rowAspect[r]
                val d = 2f - 2f * dot + ASPECT_W * da * da
                if (d < best) { best = d; bestRow[l] = r }
            }
            out[l] = best
            worst = max(worst, best)
        }
        // en iyi etiketlerde nokta bulutu uzaklığı (eksik/fazla parça)
        val qc2 = ShapeFeatures.cloud(main)
        var maxCh = 0f
        val chTop = top.sortedBy { out[it] }.take(CHAMFER_LABELS)
        for (l in chTop) {
            val ch = ShapeFeatures.chamfer(qc2, bk.clouds[bestRow[l]])
            out[l] += CHAMFER_W * ch
            maxCh = max(maxCh, ch)
        }
        worst += CHAMFER_W * maxCh
        for (l in top) if (l !in chTop) out[l] = max(out[l] + CHAMFER_W * maxCh, 0f)
        for (l in 0 until nl) if (out[l].isNaN()) out[l] = max(worst + 0.05f, coarseBest[l] + CHAMFER_W * maxCh)
        return out
    }

    /** 4 birikimli iç çarpım ([len] 4'ün katı). */
    private fun dot(q: FloatArray, m: FloatArray, off: Int, len: Int): Float {
        var a0 = 0f; var a1 = 0f; var a2 = 0f; var a3 = 0f
        var k = 0
        while (k < len) {
            a0 += q[k] * m[off + k]
            a1 += q[k + 1] * m[off + k + 1]
            a2 += q[k + 2] * m[off + k + 2]
            a3 += q[k + 3] * m[off + k + 3]
            k += 4
        }
        return (a0 + a1) + (a2 + a3)
    }

    /** Önizleme için: etiketlerin ham taslakları. */
    fun sketchesOf(label: String): List<List<Poly>> =
        Glyphs.sketches.filter { it.first == label }.map { Sketch.renderAll(it.second, 8) }
}
