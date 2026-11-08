package com.karapp.drawguidance.content.trace

import java.util.Random
import java.util.concurrent.CancellationException
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal fun checkCancel(cancel: () -> Boolean) {
    if (cancel()) throw CancellationException("Vektörleştirme iptal edildi")
}

/** Büyüyebilen Int dizisi (kutulama olmadan). */
internal class IntList(capacity: Int = 16) {
    var data = IntArray(max(4, capacity)); private set
    var size = 0; private set
    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }
    operator fun get(i: Int) = data[i]
    fun pop(): Int = data[--size]
    fun clear() { size = 0 }
}

/** Büyüyebilen Float dizisi; noktalar x,y çiftleri halinde tutulur. */
internal class FloatList(capacity: Int = 64) {
    var data = FloatArray(max(4, capacity)); private set
    var size = 0; private set
    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }
    fun add(x: Float, y: Float) { add(x); add(y) }
    operator fun get(i: Int) = data[i]
    fun pop(): Float = data[--size]
    fun clear() { size = 0 }
    fun toArray(): FloatArray = data.copyOf(size)
}

/** Çalışma çözünürlüğündeki görüntü: 0xRRGGBB renkler; saydam (arka plan) pikseller için [opaque] false. */
internal class Raster(val w: Int, val h: Int, val rgb: IntArray, val opaque: BooleanArray)

/** Alan ortalamalı küçültme (uzun kenar ≤ maxSide). Renkler alfa ağırlıklı (önçarpımlı) ortalanır. */
internal object Downscale {

    fun run(argb: IntArray, width: Int, height: Int, maxSide: Int, keepAlpha: Boolean, cancel: () -> Boolean): Raster {
        val longer = max(width, height)
        val scale = if (longer > maxSide) maxSide.toDouble() / longer else 1.0
        val w = (width * scale).roundToInt().coerceIn(1, width)
        val h = (height * scale).roundToInt().coerceIn(1, height)
        val rgb = IntArray(w * h)
        val opaque = BooleanArray(w * h)
        if (w == width && h == height) {
            for (i in 0 until w * h) {
                val c = argb[i]
                val a = c ushr 24
                store(rgb, opaque, i, a.toLong(), ((c shr 16) and 255).toLong() * a, ((c shr 8) and 255).toLong() * a,
                    (c and 255).toLong() * a, 1, keepAlpha)
            }
            return Raster(w, h, rgb, opaque)
        }
        val xMap = IntArray(width) { ((it.toLong() * w) / width).toInt() }
        val sa = LongArray(w); val sr = LongArray(w); val sg = LongArray(w); val sb = LongArray(w)
        val cnt = IntArray(w)
        var y = 0
        for (dy in 0 until h) {
            sa.fill(0); sr.fill(0); sg.fill(0); sb.fill(0); cnt.fill(0)
            while (y < height && ((y.toLong() * h) / height).toInt() == dy) {
                val row = y * width
                for (x in 0 until width) {
                    val c = argb[row + x]
                    val a = c ushr 24
                    val dx = xMap[x]
                    sa[dx] += a.toLong()
                    sr[dx] += ((c shr 16) and 255).toLong() * a
                    sg[dx] += ((c shr 8) and 255).toLong() * a
                    sb[dx] += (c and 255).toLong() * a
                    cnt[dx]++
                }
                y++
            }
            val base = dy * w
            for (dx in 0 until w) store(rgb, opaque, base + dx, sa[dx], sr[dx], sg[dx], sb[dx], max(1, cnt[dx]), keepAlpha)
            if ((dy and 31) == 0) checkCancel(cancel)
        }
        return Raster(w, h, rgb, opaque)
    }

    /** a: alfa toplamı, r/g/b: önçarpımlı toplamlar, n: piksel sayısı. */
    private fun store(rgb: IntArray, opaque: BooleanArray, i: Int, a: Long, r: Long, g: Long, b: Long, n: Int, keepAlpha: Boolean) {
        if (keepAlpha) {
            if (a >= 128L * n && a > 0) {
                opaque[i] = true
                rgb[i] = (ch(r, a) shl 16) or (ch(g, a) shl 8) or ch(b, a)
            }
        } else {
            // Beyaz zemin üzerine bindirilir: saydam alanlar beyaz renk olarak izlenir
            val den = 255L * n
            val white = 255L * (den - a)
            opaque[i] = true
            rgb[i] = (ch(r + white, den) shl 16) or (ch(g + white, den) shl 8) or ch(b + white, den)
        }
    }

    private fun ch(sum: Long, den: Long): Int = ((sum + den / 2) / den).toInt().coerceIn(0, 255)
}

/** sRGB → CIE L*a*b* (D65). */
internal object Lab {
    private fun lin(c: Double): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
    private fun f(t: Double): Double = if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116.0

    fun from(r: Double, g: Double, b: Double, out: FloatArray, o: Int) {
        val rl = lin(r); val gl = lin(g); val bl = lin(b)
        val x = (0.4124564 * rl + 0.3575761 * gl + 0.1804375 * bl) / 0.95047
        val y = 0.2126729 * rl + 0.7151522 * gl + 0.0721750 * bl
        val z = (0.0193339 * rl + 0.1191920 * gl + 0.9503041 * bl) / 1.08883
        val fx = f(x); val fy = f(y); val fz = f(z)
        out[o] = (116.0 * fy - 16.0).toFloat()
        out[o + 1] = (500.0 * (fx - fy)).toFloat()
        out[o + 2] = (200.0 * (fy - fz)).toFloat()
    }
}

/** Nicemleme sonucu: piksel başına palet sırası (saydam: -1) ve palet (çoktan aza piksel sayısına göre sıralı). */
internal class Quantized(val labels: IntArray, val paletteRgb: IntArray)

/**
 * Renk nicemleme: 15 bitlik renk histogramı (kova ortalamaları) üzerinde Lab uzayında ağırlıklı k-means
 * (k-means++ başlangıç, sabit tohum). Birbirine çok yakın merkezler birleştirilir.
 */
internal object Quantizer {

    private const val BINS = 1 shl 15
    /** Bu kadar (ΔE) uzaktaki renk "temsil edilmiyor" sayılır. */
    private const val FAR_DE = 14f
    private const val GROUP_DE = 14f

    fun run(img: Raster, k: Int, mergeDE: Float, cancel: () -> Boolean): Quantized {
        val n = img.w * img.h
        val cnt = IntArray(BINS)
        val sr = LongArray(BINS); val sg = LongArray(BINS); val sb = LongArray(BINS)
        for (i in 0 until n) {
            if (!img.opaque[i]) continue
            val c = img.rgb[i]
            val key = ((c shr 9) and 0x7C00) or ((c shr 6) and 0x3E0) or ((c shr 3) and 0x1F)
            cnt[key]++
            sr[key] += ((c shr 16) and 255).toLong(); sg[key] += ((c shr 8) and 255).toLong(); sb[key] += (c and 255).toLong()
        }
        var m = 0
        for (b in 0 until BINS) if (cnt[b] > 0) m++
        val labels = IntArray(n) { -1 }
        if (m == 0) return Quantized(labels, IntArray(0))

        val binKey = IntArray(m)
        val weight = DoubleArray(m)
        val rgb = DoubleArray(m * 3)
        val lab = FloatArray(m * 3)
        run {
            var j = 0
            for (b in 0 until BINS) {
                val c = cnt[b]
                if (c == 0) continue
                binKey[j] = b; weight[j] = c.toDouble()
                rgb[j * 3] = sr[b].toDouble() / c; rgb[j * 3 + 1] = sg[b].toDouble() / c; rgb[j * 3 + 2] = sb[b].toDouble() / c
                Lab.from(rgb[j * 3], rgb[j * 3 + 1], rgb[j * 3 + 2], lab, j * 3)
                j++
            }
        }
        val totalWeight = weight.sum()
        checkCancel(cancel)

        // --- k-means++ başlangıcı ---
        val kk0 = minOf(k, m)
        val centre = FloatArray(kk0 * 3)
        val rnd = Random(0x7EACE5L)
        val d2 = DoubleArray(m) { Double.MAX_VALUE }
        fun pickWeighted(score: (Int) -> Double, total: Double): Int {
            var r = rnd.nextDouble() * total
            for (i in 0 until m) { r -= score(i); if (r <= 0) return i }
            return m - 1
        }
        var kk = 0
        fun addCentre(i: Int) {
            centre[kk * 3] = lab[i * 3]; centre[kk * 3 + 1] = lab[i * 3 + 1]; centre[kk * 3 + 2] = lab[i * 3 + 2]
            val o = kk * 3
            for (j in 0 until m) {
                val d = dist2(lab, j * 3, centre, o).toDouble()
                if (d < d2[j]) d2[j] = d
            }
            kk++
        }
        addCentre(pickWeighted({ weight[it] }, totalWeight))
        while (kk < kk0) {
            var total = 0.0
            for (j in 0 until m) total += weight[j] * d2[j]
            if (total <= 1e-6) break
            addCentre(pickWeighted({ weight[it] * d2[it] }, total))
        }

        // --- Lloyd yinelemeleri ---
        val assign = IntArray(m) { -1 }
        val acc = DoubleArray(kk * 4)
        val cw = DoubleArray(kk)
        fun lloyd(maxIter: Int) {
            for (iter in 0 until maxIter) {
                var changed = 0
                for (j in 0 until m) {
                    val a = nearest(lab, j * 3, centre, kk)
                    if (a != assign[j]) { assign[j] = a; changed++ }
                }
                if (changed == 0 && iter > 0) break
                acc.fill(0.0)
                for (j in 0 until m) {
                    val o = assign[j] * 4; val w = weight[j]
                    acc[o] += w; acc[o + 1] += w * lab[j * 3]; acc[o + 2] += w * lab[j * 3 + 1]; acc[o + 3] += w * lab[j * 3 + 2]
                }
                for (c in 0 until kk) {
                    val w = acc[c * 4]
                    cw[c] = w
                    if (w <= 0) continue
                    centre[c * 3] = (acc[c * 4 + 1] / w).toFloat(); centre[c * 3 + 1] = (acc[c * 4 + 2] / w).toFloat()
                    centre[c * 3 + 2] = (acc[c * 4 + 3] / w).toFloat()
                }
                checkCancel(cancel)
            }
        }
        lloyd(16)

        // --- Takas: geniş alanlar merkezleri kendine çeker; küçük ama belirgin bir renk grubu (çatı, güneş) hiçbir
        // merkeze yakın değilse ve onun için yeni merkez kazancı, en ucuz iki merkezi birleştirmenin maliyetini (Ward)
        // aşıyorsa o iki merkez birleştirilip boşalan merkez bu gruba verilir. ---
        val minGroup = max(16.0, totalWeight * 0.002)
        val gc = FloatArray(3)
        for (round in 0 until 4) {
            if (kk < 2) break
            var worst = -1; var worstScore = 0.0
            for (j in 0 until m) {
                val dd = dist2(lab, j * 3, centre, assign[j] * 3)
                if (dd > FAR_DE * FAR_DE && weight[j] * dd > worstScore) { worstScore = weight[j] * dd; worst = j }
            }
            if (worst < 0) break
            // Grup: en kötü kovanın çevresindeki, kendi merkezinden daha uzak kovalar
            var gw = 0.0; var gl = 0.0; var ga = 0.0; var gb = 0.0
            for (j in 0 until m) {
                val dw = dist2(lab, j * 3, lab, worst * 3)
                if (dw < GROUP_DE * GROUP_DE && dw < dist2(lab, j * 3, centre, assign[j] * 3)) {
                    val w = weight[j]
                    gw += w; gl += w * lab[j * 3]; ga += w * lab[j * 3 + 1]; gb += w * lab[j * 3 + 2]
                }
            }
            if (gw < minGroup) break
            gc[0] = (gl / gw).toFloat(); gc[1] = (ga / gw).toFloat(); gc[2] = (gb / gw).toFloat()
            var gain = 0.0
            for (j in 0 until m) {
                val dNew = dist2(lab, j * 3, gc, 0); val dOld = dist2(lab, j * 3, centre, assign[j] * 3)
                if (dNew < dOld) gain += weight[j] * (dOld - dNew)
            }
            var bi = -1; var bj = -1; var cost = Double.MAX_VALUE
            for (i in 0 until kk) for (j in i + 1 until kk) {
                val wi = cw[i]; val wj = cw[j]
                val c = if (wi <= 0 || wj <= 0) 0.0 else wi * wj / (wi + wj) * dist2(centre, i * 3, centre, j * 3)
                if (c < cost) { cost = c; bi = i; bj = j }
            }
            if (bi < 0 || gain <= cost * 0.5) break
            val wi = cw[bi]; val wj = cw[bj]; val t = wi + wj
            if (t > 0) for (c in 0 until 3) centre[bi * 3 + c] = ((centre[bi * 3 + c] * wi + centre[bj * 3 + c] * wj) / t).toFloat()
            centre[bj * 3] = gc[0]; centre[bj * 3 + 1] = gc[1]; centre[bj * 3 + 2] = gc[2]
            lloyd(10)
        }

        // --- Yakın merkezleri birleştir ---
        cw.fill(0.0)
        for (j in 0 until m) cw[assign[j]] += weight[j]
        val alive = BooleanArray(kk) { cw[it] > 0 }
        val thr2 = mergeDE * mergeDE
        while (true) {
            var bi = -1; var bj = -1; var best = thr2
            for (i in 0 until kk) {
                if (!alive[i]) continue
                for (j in i + 1 until kk) {
                    if (!alive[j]) continue
                    val d = dist2(centre, i * 3, centre, j * 3)
                    if (d < best) { best = d; bi = i; bj = j }
                }
            }
            if (bi < 0) break
            val wi = cw[bi]; val wj = cw[bj]; val t = wi + wj
            for (c in 0 until 3) centre[bi * 3 + c] = ((centre[bi * 3 + c] * wi + centre[bj * 3 + c] * wj) / t).toFloat()
            cw[bi] = t; cw[bj] = 0.0; alive[bj] = false
        }
        // Son atama yalnız yaşayan merkezlere
        val liveIdx = (0 until kk).filter { alive[it] }.toIntArray()
        val liveCentre = FloatArray(liveIdx.size * 3) { centre[liveIdx[it / 3] * 3 + it % 3] }
        val sums = DoubleArray(liveIdx.size * 4)
        for (j in 0 until m) {
            val a = nearest(lab, j * 3, liveCentre, liveIdx.size)
            assign[j] = a
            val w = weight[j]
            sums[a * 4] += w; sums[a * 4 + 1] += w * rgb[j * 3]; sums[a * 4 + 2] += w * rgb[j * 3 + 1]; sums[a * 4 + 3] += w * rgb[j * 3 + 2]
        }
        // Piksel sayısına göre sırala (eşitlikte sabit sıra)
        val order = (0 until liveIdx.size).filter { sums[it * 4] > 0 }
            .sortedWith(compareByDescending<Int> { sums[it * 4] }.thenBy { it })
        val remap = IntArray(liveIdx.size) { -1 }
        order.forEachIndexed { i, c -> remap[c] = i }
        val palette = IntArray(order.size) { i ->
            val c = order[i]; val w = sums[c * 4]
            val r = (sums[c * 4 + 1] / w).roundToInt().coerceIn(0, 255)
            val g = (sums[c * 4 + 2] / w).roundToInt().coerceIn(0, 255)
            val b = (sums[c * 4 + 3] / w).roundToInt().coerceIn(0, 255)
            (r shl 16) or (g shl 8) or b
        }
        val binLabel = IntArray(BINS) { -1 }
        for (j in 0 until m) binLabel[binKey[j]] = remap[assign[j]]
        for (i in 0 until n) {
            if (!img.opaque[i]) continue
            val c = img.rgb[i]
            labels[i] = binLabel[((c shr 9) and 0x7C00) or ((c shr 6) and 0x3E0) or ((c shr 3) and 0x1F)]
        }
        return Quantized(labels, palette)
    }

    private fun dist2(a: FloatArray, ao: Int, b: FloatArray, bo: Int): Float {
        val d0 = a[ao] - b[bo]; val d1 = a[ao + 1] - b[bo + 1]; val d2 = a[ao + 2] - b[bo + 2]
        return d0 * d0 + d1 * d1 + d2 * d2
    }

    private fun nearest(lab: FloatArray, o: Int, centre: FloatArray, k: Int): Int {
        var best = 0; var bd = Float.MAX_VALUE
        for (c in 0 until k) {
            val d = dist2(lab, o, centre, c * 3)
            if (d < bd) { bd = d; best = c }
        }
        return best
    }
}

/**
 * Kenar koruyan gürültü süzgeci (sigma süzgeci): her piksel, 3×3 penceredeki kendisine renkçe [threshold]'dan yakın
 * komşularının ortalaması olur. Fotoğraf gürültüsünü bastırır (bölge sınırları düzgünleşir); düz renkleri ve
 * belirgin kenarları değiştirmez.
 */
internal fun denoise(img: Raster, threshold: Int, passes: Int, cancel: () -> Boolean): Raster {
    val w = img.w; val h = img.h
    val t2 = threshold * threshold
    var src = img.rgb
    repeat(passes) {
        val dst = IntArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - 1); val y1 = minOf(h - 1, y + 1)
            for (x in 0 until w) {
                val p = y * w + x
                val c = src[p]
                if (!img.opaque[p]) { dst[p] = c; continue }
                val cr = (c shr 16) and 255; val cg = (c shr 8) and 255; val cb = c and 255
                var sr = 0; var sg = 0; var sb = 0; var n = 0
                for (yy in y0..y1) {
                    val row = yy * w
                    for (xx in max(0, x - 1)..minOf(w - 1, x + 1)) {
                        val q = row + xx
                        if (!img.opaque[q]) continue
                        val o = src[q]
                        val r = (o shr 16) and 255; val g = (o shr 8) and 255; val b = o and 255
                        val dr = r - cr; val dg = g - cg; val db = b - cb
                        if (dr * dr + dg * dg + db * db >= t2) continue
                        sr += r; sg += g; sb += b; n++
                    }
                }
                dst[p] = (((sr + n / 2) / n) shl 16) or (((sg + n / 2) / n) shl 8) or ((sb + n / 2) / n)
            }
            if ((y and 63) == 0) checkCancel(cancel)
        }
        src = dst
    }
    return Raster(w, h, src, img.opaque)
}

/**
 * 3×3 çoğunluk süzgeci. Gürültü kuralı: pikselin kendi etiketi pencerede en çok [maxOwn] kez geçiyor ve başka bir
 * etiket en az [minBest] kez (ve kendisinden çok) geçiyorsa o etikete çevrilir — tek piksellik lekeleri temizler,
 * köşeleri korur. Yumuşak kural: iki etiket birbirine renkçe yakınsa ([soft] matrisi; ör. geçişli gökyüzünün bantları)
 * basit çoğunluk (kendi ≤ 4, diğeri ≥ 5) uygulanır; bant sınırlarındaki tırtıklar düzleşir, gerçek kenarlar etkilenmez.
 */
internal fun modeFilter(labels: IntArray, w: Int, h: Int, k: Int, maxOwn: Int, minBest: Int, soft: BooleanArray?): IntArray {
    val out = labels.copyOf()
    val counts = IntArray(k + 1)
    for (y in 0 until h) {
        val y0 = max(0, y - 1); val y1 = minOf(h - 1, y + 1)
        for (x in 0 until w) {
            val x0 = max(0, x - 1); val x1 = minOf(w - 1, x + 1)
            val own = labels[y * w + x]
            var best = -2; var bestC = 0
            for (yy in y0..y1) {
                val row = yy * w
                for (xx in x0..x1) {
                    val l = labels[row + xx]
                    val c = ++counts[l + 1]
                    if (l != own && (c > bestC || (c == bestC && l < best))) { bestC = c; best = l }
                }
            }
            val ownC = counts[own + 1]
            for (yy in y0..y1) { val row = yy * w; for (xx in x0..x1) counts[labels[row + xx] + 1] = 0 }
            if (best < -1) continue
            if (ownC <= maxOwn && bestC >= minBest && bestC > ownC) out[y * w + x] = best
            else if (soft != null && own >= 0 && best >= 0 && ownC <= 4 && bestC >= 5 && soft[own * k + best]) out[y * w + x] = best
        }
    }
    return out
}

/** Palet renk çiftlerinden birbirine yakın (ΔE < [de]) olanlar: k×k matris. */
internal fun softPairs(palette: IntArray, de: Float): BooleanArray {
    val k = palette.size
    val lab = FloatArray(k * 3)
    for (i in 0 until k) {
        val c = palette[i]
        Lab.from(((c shr 16) and 255).toDouble(), ((c shr 8) and 255).toDouble(), (c and 255).toDouble(), lab, i * 3)
    }
    return BooleanArray(k * k) { idx ->
        val i = idx / k; val j = idx % k
        if (i == j) false else {
            val d0 = lab[i * 3] - lab[j * 3]; val d1 = lab[i * 3 + 1] - lab[j * 3 + 1]; val d2 = lab[i * 3 + 2] - lab[j * 3 + 2]
            d0 * d0 + d1 * d1 + d2 * d2 < de * de
        }
    }
}

/** RGB uzaklığı (0..441). */
internal fun rgbDist(a: Int, b: Int): Float {
    val dr = ((a shr 16) and 255) - ((b shr 16) and 255)
    val dg = ((a shr 8) and 255) - ((b shr 8) and 255)
    val db = (a and 255) - (b and 255)
    return sqrt((dr * dr + dg * dg + db * db).toFloat())
}
