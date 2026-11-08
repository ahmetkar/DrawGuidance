package com.karapp.drawguidance.content.vector

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v36 vektör çizimleri ARGB piksel dizisine çizen tarama satırı doldurucu (platformdan bağımsız; arka plan sahneleri
 * cihazda ve testte aynı çıkar). Kenar yumuşatma piksel başına 4×4 alt örnekle: her piksel satırında 4 alt satır
 * taranır, kesişimler çeyrek piksel çözünürlükte kapsama sayacına (0..16) eklenir. Dolgu kuralı sıfırdan farklı
 * sarım (Android ve Java2D yollarıyla aynı). Kenar çizgisi yuvarlak uçlu/birleşimli çokgenlere çevrilip aynı
 * doldurucuyla çizilir. Bellek: satır başına bir kapsama dizisi ve kenar tablosu; ara bitmap yok.
 */
class VectorRaster(val w: Int, val h: Int, val px: IntArray = IntArray(w * h)) {
    private val cov = IntArray(w + 1)

    // Kenar tablosu (yeniden kullanılır): x0, y0, x1, y1 (y0 < y1), yön
    private var ex0 = FloatArray(256); private var ey0 = FloatArray(256)
    private var ex1 = FloatArray(256); private var ey1 = FloatArray(256)
    private var edir = IntArray(256)
    private var count = 0
    private var order = IntArray(256)
    private var active = IntArray(64)
    private var xs = FloatArray(64); private var ds = IntArray(64)

    /** Bütün resmi tek renge boyar. */
    fun clear(argb: Int) = px.fill(argb)

    // ------------------------------------------------------------------ çokgen doldurma

    private fun ensure(n: Int) {
        if (n <= ex0.size) return
        val c = max(n, ex0.size * 2)
        ex0 = ex0.copyOf(c); ey0 = ey0.copyOf(c); ex1 = ex1.copyOf(c); ey1 = ey1.copyOf(c); edir = edir.copyOf(c); order = order.copyOf(c)
    }

    private fun addEdge(x0: Float, y0: Float, x1: Float, y1: Float) {
        if (y0 == y1 || !x0.isFinite() || !y0.isFinite() || !x1.isFinite() || !y1.isFinite()) return
        ensure(count + 1)
        if (y0 < y1) { ex0[count] = x0; ey0[count] = y0; ex1[count] = x1; ey1[count] = y1; edir[count] = 1 }
        else { ex0[count] = x1; ey0[count] = y1; ex1[count] = x0; ey1[count] = y0; edir[count] = -1 }
        count++
    }

    /** Kapalı çokgenlerin (x0,y0,x1,y1,…) birleşimini [argb] renkte, [alpha] ek opaklıkla doldurur. */
    fun fill(polys: List<FloatArray>, argb: Int, alpha: Float = 1f) {
        count = 0
        for (p in polys) {
            val n = p.size / 2
            if (n < 3) continue
            for (i in 0 until n) {
                val j = if (i + 1 == n) 0 else i + 1
                addEdge(p[i * 2], p[i * 2 + 1], p[j * 2], p[j * 2 + 1])
            }
        }
        if (count == 0) return
        val a = ((argb ushr 24) / 255f) * alpha.coerceIn(0f, 1f)
        if (a <= 0.002f) return
        rasterize(argb, a)
    }

    private fun rasterize(argb: Int, alpha: Float) {
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        for (i in 0 until count) {
            order[i] = i
            minY = min(minY, ey0[i]); maxY = max(maxY, ey1[i])
            minX = min(minX, min(ex0[i], ex1[i])); maxX = max(maxX, max(ex0[i], ex1[i]))
        }
        val row0 = kotlin.math.floor(minY).toInt().coerceAtLeast(0)
        val row1 = ceil(maxY).toInt().coerceAtMost(h)
        if (row0 >= row1) return
        val col0 = kotlin.math.floor(minX).toInt().coerceIn(0, w)
        val col1 = ceil(maxX).toInt().coerceIn(0, w)
        if (col0 >= col1) return
        // Kenarlar üst uçlarına göre sıralı (yerleştirme sıralaması yerine sıralı dizi: büyük şekillerde hızlı)
        sortOrder()
        var next = 0
        var na = 0
        val a256 = (alpha * 256f).toInt().coerceIn(0, 256)
        for (y in row0 until row1) {
            java.util.Arrays.fill(cov, col0, col1 + 1, 0)
            var touched = false
            for (s in 0 until 4) {
                val sy = y + (s + 0.5f) * 0.25f
                while (next < count && ey0[order[next]] <= sy) {
                    if (na == active.size) active = active.copyOf(na * 2)
                    active[na++] = order[next++]
                }
                // Biten kenarlar çıkar, kesişimler toplanır
                var k = 0; var nx = 0
                for (i in 0 until na) {
                    val e = active[i]
                    if (ey1[e] <= sy) continue
                    active[k++] = e
                    if (ey0[e] > sy) continue
                    if (nx == xs.size) { xs = xs.copyOf(nx * 2); ds = ds.copyOf(nx * 2) }
                    xs[nx] = ex0[e] + (sy - ey0[e]) * (ex1[e] - ex0[e]) / (ey1[e] - ey0[e])
                    ds[nx] = edir[e]
                    nx++
                }
                na = k
                if (nx < 2) continue
                // Kesişimler x'e göre (az sayıda: araya sokma sıralaması)
                for (i in 1 until nx) {
                    val x = xs[i]; val d = ds[i]
                    var j = i - 1
                    while (j >= 0 && xs[j] > x) { xs[j + 1] = xs[j]; ds[j + 1] = ds[j]; j-- }
                    xs[j + 1] = x; ds[j + 1] = d
                }
                var wind = 0; var start = 0f
                for (i in 0 until nx) {
                    val before = wind
                    wind += ds[i]
                    if (before == 0 && wind != 0) start = xs[i]
                    else if (before != 0 && wind == 0) { span(start, xs[i]); touched = true }
                }
            }
            if (!touched) continue
            val base = y * w
            for (x in col0 until col1) {
                val c = cov[x]
                if (c == 0) continue
                blend(base + x, argb, (a256 * c) shr 4)
            }
        }
    }

    private fun sortOrder() {
        // Sıralama: üst uç y'ye göre (kutu sıralaması değil, kararlı birleştirme sıralaması Integer dizisi gerektirir;
        // küçük dizide araya sokma, büyükte java.util sıralaması)
        if (count < 24) {
            for (i in 1 until count) {
                val e = order[i]; val y = ey0[e]
                var j = i - 1
                while (j >= 0 && ey0[order[j]] > y) { order[j + 1] = order[j]; j-- }
                order[j + 1] = e
            }
            return
        }
        val keys = LongArray(count) { i -> (java.lang.Float.floatToIntBits(ey0[i] + 1e6f).toLong() shl 32) or i.toLong() }
        keys.sort()
        for (i in 0 until count) order[i] = (keys[i] and 0xFFFFFFFFL).toInt()
    }

    /** Alt satırdaki [xa, xb) aralığının kapsaması (çeyrek piksel). */
    private fun span(xa: Float, xb: Float) {
        val lim = w * 4
        val a = (xa * 4f + 0.5f).toInt().coerceIn(0, lim)
        val b = (xb * 4f + 0.5f).toInt().coerceIn(0, lim)
        if (b <= a) return
        var i = a
        // Baştaki yarım piksel
        while (i < b && (i and 3) != 0) { cov[i shr 2]++; i++ }
        // Tam pikseller
        while (i + 4 <= b) { cov[i shr 2] += 4; i += 4 }
        while (i < b) { cov[i shr 2]++; i++ }
    }

    /** Kaynak-üstü karıştırma ([a256] 0..256). */
    private fun blend(i: Int, argb: Int, a256: Int) {
        if (a256 <= 0) return
        if (a256 >= 256) { px[i] = argb or (0xFF shl 24); return }
        val d = px[i]
        val ia = 256 - a256
        val r = (((argb shr 16) and 255) * a256 + ((d shr 16) and 255) * ia) shr 8
        val g = (((argb shr 8) and 255) * a256 + ((d shr 8) and 255) * ia) shr 8
        val b = ((argb and 255) * a256 + (d and 255) * ia) shr 8
        val da = (d ushr 24)
        val oa = (a256 * 255 + da * ia) shr 8
        px[i] = (oa shl 24) or (r shl 16) or (g shl 8) or b
    }

    // ------------------------------------------------------------------ kenar çizgisi

    /**
     * Kırık çizgi kenarı ([pts] x,y çiftleri) [width] kalınlığında, yuvarlak uç ve birleşimlerle. [dash] verilirse
     * (çizgi, boşluk, …) kesik çizgi.
     */
    fun stroke(pts: FloatArray, closed: Boolean, width: Float, argb: Int, alpha: Float = 1f, dash: FloatArray? = null) {
        if (pts.size < 4 || width <= 0f) return
        val polys = ArrayList<FloatArray>()
        val pieces = if (dash != null) dashes(pts, closed, dash) else listOf(if (closed) pts + floatArrayOf(pts[0], pts[1]) else pts)
        val r = width / 2f
        for (p in pieces) outline(p, r, polys)
        fill(polys, argb, alpha)
    }

    private fun outline(p: FloatArray, r: Float, out: MutableList<FloatArray>) {
        val n = p.size / 2
        val round = r >= 0.75f
        for (i in 0 until n - 1) {
            val x0 = p[i * 2]; val y0 = p[i * 2 + 1]; val x1 = p[i * 2 + 2]; val y1 = p[i * 2 + 3]
            val len = hypot(x1 - x0, y1 - y0)
            if (len < 1e-4f) continue
            val nx = -(y1 - y0) / len * r; val ny = (x1 - x0) / len * r
            out += positive(floatArrayOf(x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny))
        }
        if (round) for (i in 0 until n) {
            // Birleşim ve uç: daire (çok kısa parçalarda da boşluk kalmaz)
            out += circle(p[i * 2], p[i * 2 + 1], r)
        }
    }

    private fun dashes(p: FloatArray, closed: Boolean, pattern: FloatArray): List<FloatArray> {
        val src = if (closed) p + floatArrayOf(p[0], p[1]) else p
        val out = ArrayList<FloatArray>()
        var k = 0; var left = pattern[0]; var on = true
        val cur = ArrayList<Float>()
        cur += src[0]; cur += src[1]
        for (i in 0 until src.size / 2 - 1) {
            var x0 = src[i * 2]; var y0 = src[i * 2 + 1]
            val x1 = src[i * 2 + 2]; val y1 = src[i * 2 + 3]
            var seg = hypot(x1 - x0, y1 - y0)
            while (seg > 0f) {
                val step = min(seg, left)
                val t = step / seg
                val nx = x0 + (x1 - x0) * t; val ny = y0 + (y1 - y0) * t
                if (on) { cur += nx; cur += ny }
                seg -= step; left -= step; x0 = nx; y0 = ny
                if (left <= 1e-4f) {
                    if (on && cur.size >= 4) out += cur.toFloatArray()
                    cur.clear()
                    on = !on; k = (k + 1) % pattern.size; left = pattern[k]
                    if (on) { cur += x0; cur += y0 }
                }
            }
        }
        if (on && cur.size >= 4) out += cur.toFloatArray()
        return out
    }

    // ------------------------------------------------------------------ vektör parçaları

    /**
     * Vektör parçalarını sırayla çizer (önce dolgu, sonra kenar): kapalı ya da kendi dolgusu olan parça dolar ("@none"
     * dolgusuz), kenar kalınlığı [inkWidth] × parça çarpanı. Renksiz kenar [ink], renksiz dolgu [fill] (yoksa dolmaz).
     */
    fun draw(paths: List<VPath>, inkWidth: Float, ink: Int, fill: Int? = null) {
        for (p in paths) {
            if (p.label != null || p.points.size < 2) continue
            val a = p.alpha.coerceIn(0f, 1f)
            val flat = FloatArray(p.points.size * 2)
            p.points.forEachIndexed { i, pt -> flat[i * 2] = pt.x; flat[i * 2 + 1] = pt.y }
            val f = when {
                p.fill == "@none" -> null
                p.fill != null -> parse(p.fill)
                p.closed -> fill
                else -> null
            }
            if (f != null && (p.closed || p.fill != null) && p.points.size >= 3) fill(listOf(flat), f, a)
            if (p.width > 0f) {
                val c = p.stroke?.let { parse(it) } ?: ink
                val sw = inkWidth * p.width
                val dash = when (p.line) {
                    LineStyle.SOLID -> null
                    LineStyle.DASHED -> floatArrayOf(sw * 3f + 4f, sw * 2f + 3f)
                    LineStyle.DOTTED -> floatArrayOf(max(0.5f, sw * 0.2f), sw * 2.2f)
                }
                stroke(flat, p.closed, sw, c, a, dash)
            }
        }
    }

    // ------------------------------------------------------------------ piksel işlemleri

    /** Dikey gradyan ([top] → [bottom]) [y0]..[y1] satırlarında. */
    fun verticalGradient(top: Int, bottom: Int, y0: Int = 0, y1: Int = h, x0: Int = 0, x1: Int = w) {
        val a = y0.coerceIn(0, h); val b = y1.coerceIn(0, h)
        val l = x0.coerceIn(0, w); val r = x1.coerceIn(0, w)
        for (y in a until b) {
            val t = if (b - a <= 1) 0f else (y - a) / (b - a - 1f)
            val c = mix(top, bottom, t)
            java.util.Arrays.fill(px, y * w + l, y * w + r, c)
        }
    }

    /** Yumuşak ışık halesi: merkezde [strength], [radius] uzakta sönük (gauss). */
    fun glow(cx: Float, cy: Float, radius: Float, argb: Int, strength: Float) {
        val x0 = (cx - radius * 1.6f).toInt().coerceIn(0, w); val x1 = (cx + radius * 1.6f).toInt().coerceIn(0, w)
        val y0 = (cy - radius * 1.6f).toInt().coerceIn(0, h); val y1 = (cy + radius * 1.6f).toInt().coerceIn(0, h)
        val k = 1f / (radius * radius * 0.5f)
        for (y in y0 until y1) {
            val dy = y + 0.5f - cy
            for (x in x0 until x1) {
                val dx = x + 0.5f - cx
                val g = exp(-(dx * dx + dy * dy) * k) * strength
                if (g > 0.004f) blend(y * w + x, argb, (g.coerceAtMost(1f) * 256f).toInt())
            }
        }
    }

    /** Işık (ekran karışımı): karanlıkta parlar, açık renkleri soldurmaz; merkezde [strength], [radius] uzakta sönük. */
    fun light(cx: Float, cy: Float, radius: Float, argb: Int, strength: Float) {
        val x0 = (cx - radius * 1.6f).toInt().coerceIn(0, w); val x1 = (cx + radius * 1.6f).toInt().coerceIn(0, w)
        val y0 = (cy - radius * 1.6f).toInt().coerceIn(0, h); val y1 = (cy + radius * 1.6f).toInt().coerceIn(0, h)
        val k = 1f / (radius * radius * 0.5f)
        val lr = (argb shr 16) and 255; val lg = (argb shr 8) and 255; val lb = argb and 255
        for (y in y0 until y1) {
            val dy = y + 0.5f - cy
            for (x in x0 until x1) {
                val dx = x + 0.5f - cx
                val g = exp(-(dx * dx + dy * dy) * k) * strength
                if (g <= 0.004f) continue
                val t = (g.coerceAtMost(1f) * 256f).toInt()
                val i = y * w + x
                val d = px[i]
                val r = (d shr 16) and 255; val gg = (d shr 8) and 255; val b = d and 255
                // ekran: 1 - (1-d)(1-l·t)
                val nr = r + (((255 - r) * (lr * t shr 8)) shr 8)
                val ng = gg + (((255 - gg) * (lg * t shr 8)) shr 8)
                val nb = b + (((255 - b) * (lb * t shr 8)) shr 8)
                px[i] = (d and (0xFF shl 24)) or (nr shl 16) or (ng shl 8) or nb
            }
        }
    }

    /** Kenarlara doğru karartma (köşelerde [strength] oranında). */
    fun vignette(strength: Float) {
        val cx = w / 2f; val cy = h / 2f
        val k = 1f / (cx * cx + cy * cy)
        for (y in 0 until h) {
            val dy = y + 0.5f - cy
            for (x in 0 until w) {
                val dx = x + 0.5f - cx
                val m = (256f * (1f - strength * (dx * dx + dy * dy) * k)).toInt().coerceIn(0, 256)
                val i = y * w + x
                val d = px[i]
                px[i] = (d and (0xFF shl 24)) or ((((d shr 16) and 255) * m shr 8) shl 16) or ((((d shr 8) and 255) * m shr 8) shl 8) or ((d and 255) * m shr 8)
            }
        }
    }

    /** [outer] çokgeninden [holes] çıkarılmış alanı doldurur (pencere, kapı açıklığı olan duvar). */
    fun fillWithHoles(outer: FloatArray, holes: List<FloatArray>, argb: Int, alpha: Float = 1f) {
        val polys = ArrayList<FloatArray>(holes.size + 1)
        polys += positive(outer)
        for (hole in holes) polys += reversed(positive(hole))
        fill(polys, argb, alpha)
    }

    /** Bütün resmi [argb] rengine [amount] oranında yaklaştırır (gece, sis). */
    fun tint(argb: Int, amount: Float) {
        val a = (amount.coerceIn(0f, 1f) * 256f).toInt()
        for (i in px.indices) blend(i, argb, a)
    }

    /** Çarpma (gece karartması): her kanal [argb] kanalıyla çarpılır. */
    fun multiply(argb: Int) {
        val mr = (argb shr 16) and 255; val mg = (argb shr 8) and 255; val mb = argb and 255
        for (i in px.indices) {
            val d = px[i]
            px[i] = (d and (0xFF shl 24)) or ((((d shr 16) and 255) * mr / 255) shl 16) or ((((d shr 8) and 255) * mg / 255) shl 8) or ((d and 255) * mb / 255)
        }
    }

    /** Yatay sis bandı: [yc] çevresinde [height] kalınlıkta, kenarlara doğru sönen. */
    fun haze(yc: Float, height: Float, argb: Int, strength: Float) {
        val y0 = (yc - height).toInt().coerceIn(0, h); val y1 = (yc + height).toInt().coerceIn(0, h)
        for (y in y0 until y1) {
            val t = (y + 0.5f - yc) / height
            val g = (1f - t * t).coerceAtLeast(0f) * strength
            val a = (g * 256f).toInt()
            if (a <= 0) continue
            for (x in 0 until w) blend(y * w + x, argb, a)
        }
    }

    /** İnce tane (baskı dokusu): tohumla aynı. */
    fun grain(amount: Float, seed: Int) {
        val k = (amount * 255f).toInt()
        if (k <= 0) return
        var s = seed * 1103515245 + 12345
        for (i in px.indices) {
            s = s * 1664525 + 1013904223
            val n = ((s ushr 24) - 128) * k shr 8
            val d = px[i]
            val r = (((d shr 16) and 255) + n).coerceIn(0, 255)
            val g = (((d shr 8) and 255) + n).coerceIn(0, 255)
            val b = ((d and 255) + n).coerceIn(0, 255)
            px[i] = (d and (0xFF shl 24)) or (r shl 16) or (g shl 8) or b
        }
    }

    companion object {
        /** "#RRGGBB" ya da "#AARRGGBB" → ARGB (opak varsayılır). */
        fun parse(hex: String): Int {
            val s = hex.removePrefix("#")
            val v = s.toLongOrNull(16) ?: return 0xFF000000.toInt()
            return if (s.length > 6) v.toInt() else (v.toInt() or (0xFF shl 24))
        }

        fun mix(a: Int, b: Int, t: Float): Int {
            val k = t.coerceIn(0f, 1f)
            fun ch(s: Int) = (((a shr s) and 255) + (((b shr s) and 255) - ((a shr s) and 255)) * k).toInt()
            return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        /** Saat yönünün tersi (pozitif alanlı) çokgen: sıfırdan farklı sarımda parçalar birbirini silmez. */
        internal fun positive(p: FloatArray): FloatArray {
            var area = 0f
            val n = p.size / 2
            for (i in 0 until n) {
                val j = (i + 1) % n
                area += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1]
            }
            if (area >= 0f) return p
            val r = FloatArray(p.size)
            for (i in 0 until n) { r[i * 2] = p[(n - 1 - i) * 2]; r[i * 2 + 1] = p[(n - 1 - i) * 2 + 1] }
            return r
        }

        internal fun reversed(p: FloatArray): FloatArray {
            val n = p.size / 2
            return FloatArray(p.size) { i -> p[(n - 1 - i / 2) * 2 + (i and 1)] }
        }

        internal fun circle(cx: Float, cy: Float, r: Float): FloatArray {
            val n = ceil(sqrt(r) * 4f).toInt().coerceIn(6, 40)
            val out = FloatArray(n * 2)
            for (i in 0 until n) {
                val a = 2.0 * Math.PI * i / n
                out[i * 2] = cx + (r * cos(a)).toFloat(); out[i * 2 + 1] = cy + (r * sin(a)).toFloat()
            }
            return out
        }
    }
}
