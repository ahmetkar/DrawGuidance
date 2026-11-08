package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextPathSpec
import kotlin.math.hypot

/**
 * v42 yol üzerinde yazı: kalem aracıyla çizilen yol (ya da başka bir vektör çizgi) seçilir, yazı o yolu izler.
 * Yolun bir kopyası yazının içinde saklanır (yol silinse de yazı bozulmaz); yazı kutusu yolun kutusudur.
 */
object TextOnPath {
    /** Yazı yerleştirilebilen eleman: kalem yolu ya da en az iki noktalı çizgisi olan vektör şekil (grafik değil). */
    fun canHost(s: Stroke): Boolean {
        val shape = s.shape ?: return false
        if (s.brushType != BrushType.SHAPE || shape.graph != null || s.hidden) return false
        val first = shape.subpaths.firstOrNull() ?: return false
        return first.label == null && first.count >= 2 && length(points(s)) > 1f
    }

    /** Kalem yolu mu (seçicide önce bunlar önerilir). */
    fun isPenPath(s: Stroke): Boolean = s.shape?.kind == ShapeRegistry.FREE_PATH

    /** Şeklin ilk çizgisi (tuval koordinatı). */
    fun points(s: Stroke): List<Offset> {
        val n = s.shape?.subpaths?.firstOrNull()?.count ?: 0
        return s.points.take(n).map { Offset(it.x, it.y) }
    }

    private fun length(pts: List<Offset>): Float {
        var l = 0f
        for (i in 1 until pts.size) l += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        return l
    }

    /** [p] noktasına en yakın, [tolerance] içindeki yol elemanı (üstteki önce). */
    fun hostAt(strokes: List<Stroke>, p: Offset, tolerance: Float): Stroke? =
        strokes.asReversed().filter(::canHost).minByOrNull { distance(points(it), p) }?.takeIf { distance(points(it), p) <= tolerance }

    private fun distance(pts: List<Offset>, p: Offset): Float {
        var best = Float.MAX_VALUE
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            val ab = b - a
            val len2 = ab.x * ab.x + ab.y * ab.y
            val t = if (len2 < 1e-6f) 0f else (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
            val q = Offset(a.x + ab.x * t, a.y + ab.y * t)
            best = minOf(best, (q - p).getDistance())
        }
        return best
    }

    /**
     * [text] yazısını [host] yolunun üstüne yerleştirir: yol yazının yerel düzlemine kopyalanır, kutu yolun kutusu
     * (yazı boyu kadar pay ile) olur; yazı tek satır. Önceden yoldaysa yeni yola taşınır (başlangıç ve yön korunur).
     */
    fun attach(text: Stroke, host: Stroke): Stroke? {
        val spec = text.text ?: return null
        if (text.brushType != BrushType.TEXT || !canHost(host)) return null
        val pts = points(host)
        val size = TextRenderer.effectiveSize(spec, text.sizePx)
        val m = size * 1.4f
        val minX = pts.minOf { it.x } - m; val minY = pts.minOf { it.y } - m
        val maxX = pts.maxOf { it.x } + m; val maxY = pts.maxOf { it.y } + m
        val local = pts.map { StrokePoint(it.x - minX, it.y - minY) }
        val old = spec.path
        val path = TextPathSpec(
            local, maxX - minX, maxY - minY,
            start = old?.start ?: 0f, below = old?.below ?: false, reversed = old?.reversed ?: false,
            closed = host.shape?.subpaths?.firstOrNull()?.closed == true,
        )
        // Yol yazısında kutu boyu ve arka planı yok; efektler (gölge, büyük harf, aralık) kalır
        val box = spec.box?.let { com.karapp.drawguidance.data.model.TextBox(effects = it.effects).orNull() }
        val newSpec = spec.copy(path = path, box = box)
        val quad = listOf(StrokePoint(minX, minY), StrokePoint(maxX, minY), StrokePoint(maxX, maxY), StrokePoint(minX, maxY))
        return text.copy(text = newSpec, points = quad)
    }

    /** Yazıyı yoldan çıkarır: düz yazı kutusu, yolun başladığı köşede. */
    fun detach(text: Stroke): Stroke? {
        val spec = text.text ?: return null
        if (spec.path == null) return null
        val newSpec = spec.copy(path = null)
        val tl = text.points.firstOrNull() ?: StrokePoint(0f, 0f)
        return text.copy(text = newSpec, points = TextRenderer.quadAt(newSpec, text.sizePx, tl.x, tl.y))
    }

    /** Yoldaki ayarlar: başlangıç (0..1), yolun altında, ters yön. */
    fun adjust(text: Stroke, start: Float? = null, below: Boolean? = null, reversed: Boolean? = null): Stroke? {
        val spec = text.text ?: return null
        val path = spec.path ?: return null
        val p = path.copy(
            start = start?.coerceIn(0f, 1f) ?: path.start, below = below ?: path.below, reversed = reversed ?: path.reversed,
        )
        return text.copy(text = spec.copy(path = p))
    }
}
