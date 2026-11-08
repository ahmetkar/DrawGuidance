package com.karapp.drawguidance.ui.screens.canvasedit.editor

import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.StrokePoint
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fırça çiziminin titreşimini yumuşatır ("Yumuşat"). v21: etki gözle görülür olsun diye nokta aralığına değil çizginin
 * boyuna göre çalışır:
 * 1. Çizgi, nokta sayısı değişmeden eşit aralıklı noktalarla yeniden örneklenir (basınç, eğim, yön de aradan alınır);
 *    böylece sık ve seyrek çizilmiş yerler aynı ölçüde yumuşar.
 * 2. Gauss ağırlıklı ortalama: yarıçap çizgi boyunun %4'ü (4..40 birim). Açık çizgide uçlar yerinde kalır (uçtan
 *    yansıtma: çizginin yönü korunur), kapalı çizgide çevre boyunca dolanır.
 * Her basışta çizgi belirgin biçimde düzleşir; birkaç basış kıvrımları iyice yuvarlar.
 */
object InkSmoothing {

    fun smooth(points: List<StrokePoint>, strength: Float = 1f): List<StrokePoint> {
        if (points.size < 4) return points
        val n = points.size
        val closed = points.first().x == points.last().x && points.first().y == points.last().y
        // Birikimli uzunluk
        val acc = FloatArray(n)
        for (i in 1 until n) acc[i] = acc[i - 1] + hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        val length = acc[n - 1]
        if (length < 1f) return points
        // 1) Eşit aralıklı yeniden örnekleme (nokta sayısı aynı)
        val xs = FloatArray(n); val ys = FloatArray(n)
        val ps = FloatArray(n); val ts = FloatArray(n); val os = FloatArray(n)
        var j = 0
        for (i in 0 until n) {
            val d = length * i / (n - 1)
            while (j < n - 2 && acc[j + 1] < d) j++
            val seg = (acc[j + 1] - acc[j]).coerceAtLeast(1e-6f)
            val u = ((d - acc[j]) / seg).coerceIn(0f, 1f)
            val a = points[j]; val b = points[j + 1]
            xs[i] = a.x + (b.x - a.x) * u; ys[i] = a.y + (b.y - a.y) * u
            ps[i] = a.pressure + (b.pressure - a.pressure) * u
            ts[i] = a.tilt + (b.tilt - a.tilt) * u
            os[i] = if (u < 0.5f) a.orient else b.orient
        }
        // 2) Gauss ortalaması (yarıçap: boyun %4'ü, nokta cinsinden)
        val spacing = length / (n - 1)
        val sigmaPx = (length * 0.04f * strength).coerceIn(4f, 40f)
        val sigma = max(0.8f, sigmaPx / spacing)
        val radius = (sigma * 2.5f).roundToInt().coerceAtLeast(1)
        val weights = FloatArray(radius + 1) { k -> exp(-(k * k) / (2f * sigma * sigma)) }
        val ox = FloatArray(n); val oy = FloatArray(n)
        fun sampleX(k: Int): Float = when {
            closed -> xs[Math.floorMod(k, n - 1)]
            k < 0 -> 2f * xs[0] - xs[-k]
            k >= n -> 2f * xs[n - 1] - xs[2 * (n - 1) - k]
            else -> xs[k]
        }
        fun sampleY(k: Int): Float = when {
            closed -> ys[Math.floorMod(k, n - 1)]
            k < 0 -> 2f * ys[0] - ys[-k]
            k >= n -> 2f * ys[n - 1] - ys[2 * (n - 1) - k]
            else -> ys[k]
        }
        for (i in 0 until n) {
            var sx = 0f; var sy = 0f; var sw = 0f
            for (k in -radius..radius) {
                val idx = (i + k).coerceIn(-(n - 1), 2 * (n - 1))
                val w = weights[kotlin.math.abs(k)]
                sx += sampleX(idx) * w; sy += sampleY(idx) * w; sw += w
            }
            ox[i] = sx / sw; oy[i] = sy / sw
        }
        if (closed) { ox[n - 1] = ox[0]; oy[n - 1] = oy[0] }
        val q = ContentCodec::quantize
        return List(n) { i ->
            when {
                !closed && i == 0 -> points[0]
                !closed && i == n - 1 -> points[n - 1]
                else -> StrokePoint(q(ox[i]), q(oy[i]), ps[i], ts[i], os[i])
            }
        }
    }
}
