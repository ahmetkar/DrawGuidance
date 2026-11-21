package com.karapp.drawguidance.ui.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.karapp.drawguidance.data.model.ImageWarp
import com.karapp.drawguidance.data.model.Reflection
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.WarpMesh
import com.karapp.drawguidance.data.model.WaveSpec
import kotlin.math.roundToInt

/**
 * v56 aşama 6 resim çarpıtması ve yansıma çizimi: Çarpıtma ağı ([ImageWarp.mesh]) `drawBitmapMesh` ile (ağ yüzeyi 16×16
 * alt bölmeyle eğri), yansıma dalgası noktaları (vektör) ya da ağı (resim) kaydırır, alfa gradyanı `DstIn` ile uygulanır.
 */
object WarpRenderer {
    const val SUB = 16

    fun hasWave(s: Stroke): Boolean = s.warp?.wave?.let { it.amplitude != 0f || it.fadeFrom < 1f || it.fadeTo < 1f } == true

    /** Vektör çizgide dalga kayması uygulanmış kopya. */
    fun displaced(s: Stroke): Stroke {
        val w = s.warp?.wave ?: return s
        if (w.amplitude == 0f) return s
        return s.copy(points = s.points.map { p -> Reflection.offset(w, p.x, p.y).let { (dx, dy) -> p.at(p.x + dx, p.y + dy) } })
    }

    /**
     * Resmin ağ köşe noktaları (SUB+1)² × 2: ağ varsa ağ yüzeyinden, yoksa 4 köşeden; dalga varsa kaydırılmış.
     */
    fun verts(corners: FloatArray, warp: ImageWarp?, offsetX: Float, offsetY: Float): FloatArray? {
        val mesh = warp?.mesh
        val n = mesh?.let { WarpMesh.sizeOf(it) } ?: 0
        val wave = warp?.wave?.takeIf { it.amplitude != 0f }
        if (n == 0 && wave == null) return null
        val out = FloatArray((SUB + 1) * (SUB + 1) * 2)
        var k = 0
        for (j in 0..SUB) for (i in 0..SUB) {
            val u = i.toFloat() / SUB; val v = j.toFloat() / SUB
            var x: Float; var y: Float
            if (n > 0) {
                val p = WarpMesh.eval(mesh!!, n, u, v); x = p.first; y = p.second
            } else {
                val tx = corners[0] + (corners[2] - corners[0]) * u; val ty = corners[1] + (corners[3] - corners[1]) * u
                val bx = corners[6] + (corners[4] - corners[6]) * u; val by = corners[7] + (corners[5] - corners[7]) * u
                x = tx + (bx - tx) * v; y = ty + (by - ty) * v
            }
            if (wave != null) Reflection.offset(wave, x, y).let { (dx, dy) -> x += dx; y += dy }
            out[k++] = x + offsetX; out[k++] = y + offsetY
        }
        return out
    }

    fun drawMesh(canvas: Canvas, bitmap: Bitmap, verts: FloatArray, alpha: Int, filter: Int) {
        val paint = Paint(if (filter == 0) Paint.ANTI_ALIAS_FLAG else Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha }
        canvas.drawBitmapMesh(bitmap, SUB, SUB, verts, 0, null, 0, paint)
    }

    /** Yansımanın alfa gradyanı: [block] ara katmanda çizilir, gradyan DstIn ile çarpılır. */
    inline fun withFade(canvas: Canvas, w: WaveSpec, top: Float, bottom: Float, block: () -> Unit) {
        val y0 = if (w.bottom > w.top) w.top else top
        val y1 = if (w.bottom > w.top) w.bottom else bottom
        if (w.fadeFrom >= 0.999f && w.fadeTo >= 0.999f) { block(); return }
        val save = canvas.saveLayer(RectF(-1e6f, -1e6f, 1e6f, 1e6f), null)
        block()
        val a0 = (w.fadeFrom.coerceIn(0f, 1f) * 255).roundToInt(); val a1 = (w.fadeTo.coerceIn(0f, 1f) * 255).roundToInt()
        val paint = Paint().apply {
            shader = LinearGradient(0f, y0, 0f, if (y1 > y0) y1 else y0 + 1f, (a0 shl 24) or 0xFFFFFF, (a1 shl 24) or 0xFFFFFF, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawPaint(paint)
        canvas.restoreToCount(save)
    }
}
