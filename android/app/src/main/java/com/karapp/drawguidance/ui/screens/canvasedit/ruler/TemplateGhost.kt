package com.karapp.drawguidance.ui.screens.canvasedit.ruler

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.isQuad
import kotlin.math.hypot

/**
 * v40 şekil kalıbı: seçilen şeklin (ya da kütüphaneden seçilenin) belge dışındaki hayalet kopyası. Konturları
 * (şeklin alt yolları: dış çevre, iç çizgiler ve delikler) merkeze göre yerel koordinatta saklanır; kalıp taşınıp
 * ölçeklenebilir ([cx], [cy], [scale]). Belgeye girmez, dışa aktarılmaz, geri alma geçmişinde yer almaz.
 */
class TemplateGhost(
    /** Yerel konturlar (merkez başlangıçta) ve kapalı mı. */
    val local: List<Pair<FloatArray, Boolean>>,
    val cx: Float,
    val cy: Float,
    val scale: Float = 1f,
    /** Kalıbın çizgi rengi (hayalet olarak soluk çizilir). */
    val colorHex: String = "#3B82F6",
) {
    /** Yerel sınır kutusu (ölçeksiz). */
    val localBounds: Rect = run {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        local.forEach { (pts, _) ->
            for (i in 0 until pts.size / 2) {
                val x = pts[2 * i]; val y = pts[2 * i + 1]
                if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
            }
        }
        if (l > r) Rect.Zero else Rect(l, t, r, b)
    }

    val contours: List<Pair<FloatArray, Boolean>> by lazy {
        local.map { (pts, closed) -> FloatArray(pts.size) { i -> if (i % 2 == 0) cx + pts[i] * scale else cy + pts[i] * scale } to closed }
    }

    val edges: SnapEdges by lazy {
        val b = SnapEdges.Builder()
        contours.forEach { (pts, closed) -> b.polyline(pts, closed).endContour() }
        b.build()
    }

    val bounds: Rect get() = Rect(cx + localBounds.left * scale, cy + localBounds.top * scale, cx + localBounds.right * scale, cy + localBounds.bottom * scale)

    fun moved(dx: Float, dy: Float) = TemplateGhost(local, cx + dx, cy + dy, scale, colorHex)
    fun scaled(s: Float) = TemplateGhost(local, cx, cy, s.coerceIn(MIN_SCALE, MAX_SCALE), colorHex)

    /** Taşıma tutamağı (sol üst köşenin dışında) ve ölçek tutamağı (sağ alt köşenin dışında); [gap] tuval birimi. */
    fun moveHandle(gap: Float): Offset = bounds.let { Offset(it.left - gap, it.top - gap) }
    fun scaleHandle(gap: Float): Offset = bounds.let { Offset(it.right + gap, it.bottom + gap) }

    /** Ölçek tutamağı [p]'ye sürüklenince yeni ölçek (merkez yerinde; tutamağın merkeze uzaklığı oranında). */
    fun scaleFor(p: Offset, gap: Float, startScale: Float, startHandle: Offset): Float {
        val d0 = hypot(startHandle.x - cx, startHandle.y - cy)
        if (d0 < 1e-3f) return startScale
        return (startScale * hypot(p.x - cx, p.y - cy) / d0).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    companion object {
        const val MIN_SCALE = 0.05f
        const val MAX_SCALE = 40f

        /**
         * Çizimlerin konturları (tuval koordinatı): şekillerde alt yollar (dış çevre, iç çizgiler, delikler), yazı/resim
         * gibi dörtgen elemanlarda çerçeve, serbest çizimde noktaların çizgisi. Silgiler alınmaz.
         */
        fun contoursOf(strokes: List<Stroke>): List<Pair<FloatArray, Boolean>> {
            val out = ArrayList<Pair<FloatArray, Boolean>>()
            for (s in strokes) {
                if (s.brushType == BrushType.ERASER || s.points.size < 2) continue
                val shape = s.shape
                if (shape != null && shape.subpaths.isNotEmpty()) {
                    var at = 0
                    for (sub in shape.subpaths) {
                        val end = (at + sub.count).coerceAtMost(s.points.size)
                        if (end - at >= 2) {
                            val arr = FloatArray((end - at) * 2)
                            for (i in at until end) { arr[2 * (i - at)] = s.points[i].x; arr[2 * (i - at) + 1] = s.points[i].y }
                            out += arr to sub.closed
                        }
                        at = end
                    }
                } else {
                    val arr = FloatArray(s.points.size * 2)
                    s.points.forEachIndexed { i, p -> arr[2 * i] = p.x; arr[2 * i + 1] = p.y }
                    out += arr to s.brushType.isQuad
                }
            }
            return out
        }

        /** Tuval koordinatındaki konturlardan kalıp: merkez konturların kutusunun ortası, ölçek 1. */
        fun of(contours: List<Pair<FloatArray, Boolean>>, colorHex: String = "#3B82F6"): TemplateGhost? {
            if (contours.isEmpty()) return null
            val probe = TemplateGhost(contours, 0f, 0f)
            val b = probe.localBounds
            if (b.width <= 0f && b.height <= 0f) return null
            val c = b.center
            val local = contours.map { (pts, closed) -> FloatArray(pts.size) { i -> pts[i] - if (i % 2 == 0) c.x else c.y } to closed }
            return TemplateGhost(local, c.x, c.y, 1f, colorHex)
        }
    }
}
