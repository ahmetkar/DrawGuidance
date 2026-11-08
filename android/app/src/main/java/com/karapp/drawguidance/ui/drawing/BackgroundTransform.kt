package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.data.model.BackgroundSpec
import kotlin.math.hypot

/**
 * v40 arka plan resminin taşınması ve boyutlandırılması (tuval biriminde). Resmin çerçevesi kağıt boyutunda bir
 * dikdörtgendir: merkezi kağıdın merkezinden [BackgroundSpec.imageOffsetX]/[BackgroundSpec.imageOffsetY] (kağıt
 * eni/boyu oranında) uzakta, boyu kağıdın [BackgroundSpec.imageScale] katı. Resim çerçeveye kendi yerleşimiyle
 * ([BackgroundSpec.imageFit]) çizilir ve kağıtla kırpılır.
 */
object BackgroundTransform {
    const val MIN_SCALE = 0.1f
    const val MAX_SCALE = 8f

    /** Köşeler: sol üst, sağ üst, sağ alt, sol alt. */
    const val TOP_LEFT = 0
    const val TOP_RIGHT = 1
    const val BOTTOM_RIGHT = 2
    const val BOTTOM_LEFT = 3

    /** Resmin çerçevesi ([w]×[h] kağıtta). */
    fun frame(spec: BackgroundSpec, w: Float, h: Float): Rect {
        val cx = w / 2f + spec.imageOffsetX * w
        val cy = h / 2f + spec.imageOffsetY * h
        val hw = w * spec.imageScale / 2f
        val hh = h * spec.imageScale / 2f
        return Rect(cx - hw, cy - hh, cx + hw, cy + hh)
    }

    /** Çerçevenin köşeleri ([TOP_LEFT] sırasıyla). */
    fun corners(spec: BackgroundSpec, w: Float, h: Float): List<Offset> =
        frame(spec, w, h).let { listOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) }

    private fun with(spec: BackgroundSpec, w: Float, h: Float, center: Offset, scale: Float): BackgroundSpec {
        if (w <= 0f || h <= 0f) return spec
        // Çerçeve kağıttan tamamen kaçmasın: merkez kağıdın (ölçek kadar genişletilmiş) içinde kalır
        val limitX = 0.5f + scale / 2f
        val limitY = 0.5f + scale / 2f
        val ox = ((center.x - w / 2f) / w).coerceIn(-limitX + 0.05f, limitX - 0.05f)
        val oy = ((center.y - h / 2f) / h).coerceIn(-limitY + 0.05f, limitY - 0.05f)
        return spec.copy(imageOffsetX = ox, imageOffsetY = oy, imageScale = scale)
    }

    /** Sürükleme ([pan]) ve iki parmakla yakınlaştırma ([zoom], [focus] çevresinde). */
    fun pinch(spec: BackgroundSpec, w: Float, h: Float, pan: Offset, zoom: Float, focus: Offset): BackgroundSpec {
        val f = frame(spec, w, h)
        val scale = (spec.imageScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val k = scale / spec.imageScale
        val center = focus + (f.center - focus) * k + pan
        return with(spec, w, h, center, scale)
    }

    /** Yalnız taşıma. */
    fun moved(spec: BackgroundSpec, w: Float, h: Float, delta: Offset): BackgroundSpec =
        pinch(spec, w, h, delta, 1f, Offset.Zero)

    /**
     * Köşe tutamağı [corner] tuvalde [point]'e sürüklendi: karşı köşe yerinde kalır, en-boy oranı korunur (parmağın
     * köşegen üstündeki izdüşümü kadar).
     */
    fun corner(spec: BackgroundSpec, w: Float, h: Float, corner: Int, point: Offset): BackgroundSpec {
        val pts = corners(spec, w, h)
        val dragged = pts[corner.coerceIn(0, 3)]
        val opposite = pts[(corner.coerceIn(0, 3) + 2) % 4]
        val diag = dragged - opposite
        val len = hypot(diag.x, diag.y)
        if (len < 1e-3f) return spec
        val along = ((point.x - opposite.x) * diag.x + (point.y - opposite.y) * diag.y) / len
        val scale = (spec.imageScale * along / len).coerceIn(MIN_SCALE, MAX_SCALE)
        val k = scale / spec.imageScale
        val center = opposite + (frame(spec, w, h).center - opposite) * k
        return with(spec, w, h, center, scale)
    }

    /** Resim yeniden kağıdı kaplar (kaydırma ve ölçek kalkar). */
    fun reset(spec: BackgroundSpec): BackgroundSpec = spec.copy(imageOffsetX = 0f, imageOffsetY = 0f, imageScale = 1f)
}
