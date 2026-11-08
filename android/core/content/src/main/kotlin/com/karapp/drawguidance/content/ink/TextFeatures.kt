package com.karapp.drawguidance.content.ink

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * v37 yazı ağının girdisi (tek karakterin çizgileri): oran korunarak birim kutuya ölçeklenir; 12×12 ızgarada 4 yönsüz
 * doğrultu kanalı (0°, 45°, 90°, 135°; çift doğrusal dağıtım, hafif bulanık, L2), 6×6 ızgarada uç noktası ve nokta
 * (tek dokunuş) kanalı ve 4 genel değer (en-boy, çizgi sayısı, toplam mürekkep, nokta oranı). Çizgi yönüne ve sırasına
 * duyarsızdır; 616 boyut, karakter başına ~0,05 ms.
 */
internal object TextFeatures {
    const val G = 12
    const val E = 6
    const val DIR = 4 * G * G
    const val DIM = DIR + E * E + 4
    private const val STEP = 0.02f
    private const val END_W = 0.3f
    private const val DOT_W = 1f

    fun extract(strokes: List<Poly>): FloatArray {
        val f = FloatArray(DIM)
        if (strokes.isEmpty()) return f
        val b = Box.of(strokes)
        val s = max(max(b.w, b.h), 1e-4f)
        val cx = b.cx; val cy = b.cy
        var total = 0f
        var dots = 0
        for (st in strokes) {
            val nx = FloatArray(st.n) { (st.x[it] - cx) / s + 0.5f }
            val ny = FloatArray(st.n) { (st.y[it] - cy) / s + 0.5f }
            val p = Poly(nx, ny)
            if (p.n < 2 || p.length < STEP) {
                dots++
                end(f, p.cx, p.cy, DOT_W)
                continue
            }
            val r = Geo.resample(Geo.smooth(Geo.resample(p, STEP * 0.5f), 1), STEP)
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
                dir(f, b0 % 4, mx, my, len * (1 - fr))
                dir(f, (b0 + 1) % 4, mx, my, len * fr)
            }
            total += r.length
            // kapalı döngülerin başlangıç noktası keyfidir: uç sayılmaz
            val gap = hypot(r.x[r.n - 1] - r.x[0], r.y[r.n - 1] - r.y[0])
            val ext = max(max(r.maxX - r.minX, r.maxY - r.minY), 1e-4f)
            if (gap > 0.15f * ext || r.length < 1.5f * ext) {
                end(f, r.x[0], r.y[0], END_W)
                end(f, r.x[r.n - 1], r.y[r.n - 1], END_W)
            }
        }
        for (ch in 0 until 4) blur(f, ch * G * G, G)
        blur(f, DIR, E)
        normalize(f, 0, DIR, 1f)
        normalize(f, DIR, E * E, 0.5f)
        val o = DIR + E * E
        f[o] = (atan2(b.w.toDouble(), b.h.toDouble()) / (PI / 2)).toFloat()
        f[o + 1] = (ln(strokes.size.toFloat()) / ln(16f)).coerceIn(0f, 1.5f)
        f[o + 2] = min(total / 8f, 1.5f)
        f[o + 3] = dots / strokes.size.toFloat()
        return f
    }

    private fun dir(f: FloatArray, ch: Int, u: Float, v: Float, w: Float) = splat(f, ch * G * G, G, u, v, w)

    private fun end(f: FloatArray, u: Float, v: Float, w: Float) = splat(f, DIR, E, u, v, w)

    private fun splat(f: FloatArray, base: Int, g: Int, u: Float, v: Float, w: Float) {
        val gx = (u * g - 0.5f).coerceIn(0f, g - 1f)
        val gy = (v * g - 0.5f).coerceIn(0f, g - 1f)
        val x0 = min(floor(gx).toInt(), g - 2); val y0 = min(floor(gy).toInt(), g - 2)
        val fx = gx - x0; val fy = gy - y0
        f[base + y0 * g + x0] += w * (1 - fx) * (1 - fy)
        f[base + y0 * g + x0 + 1] += w * fx * (1 - fy)
        f[base + (y0 + 1) * g + x0] += w * (1 - fx) * fy
        f[base + (y0 + 1) * g + x0 + 1] += w * fx * fy
    }

    private fun blur(f: FloatArray, base: Int, g: Int) {
        val tmp = FloatArray(g * g)
        for (y in 0 until g) for (x in 0 until g) {
            var s = f[base + y * g + x] * 0.5f
            if (x > 0) s += f[base + y * g + x - 1] * 0.25f
            if (x < g - 1) s += f[base + y * g + x + 1] * 0.25f
            tmp[y * g + x] = s
        }
        for (y in 0 until g) for (x in 0 until g) {
            var s = tmp[y * g + x] * 0.5f
            if (y > 0) s += tmp[(y - 1) * g + x] * 0.25f
            if (y < g - 1) s += tmp[(y + 1) * g + x] * 0.25f
            f[base + y * g + x] = s
        }
    }

    private fun normalize(f: FloatArray, from: Int, len: Int, scale: Float) {
        var n = 0f
        for (i in from until from + len) n += f[i] * f[i]
        n = sqrt(n)
        if (n > 0f) for (i in from until from + len) f[i] = f[i] / n * scale
    }
}
