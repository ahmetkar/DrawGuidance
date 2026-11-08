package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextBox
import kotlin.math.hypot
import kotlin.math.max

/**
 * v23 yazı kutusunun tutamaklarla boyutlandırılması: sağ kenar eni (satırlar yeni ene göre kaydırılır), alt kenar en az
 * boyu, sağ alt köşe ikisini birden değiştirir. Kutunun sol üst köşesi, yönü ve önceki ölçeklemesi korunur.
 */
object TextBoxResize {
    const val WIDTH = 1
    const val HEIGHT = 2
    const val BOTH = 3

    /** Tutamakların tuvaldeki yerleri: (konum, tür). */
    fun handles(stroke: Stroke): List<Pair<Offset, Int>> {
        val q = stroke.points
        if (stroke.brushType != BrushType.TEXT || stroke.text == null || q.size < 4) return emptyList()
        fun mid(a: StrokePoint, b: StrokePoint) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        return listOf(mid(q[1], q[2]) to WIDTH, mid(q[2], q[3]) to HEIGHT, Offset(q[2].x, q[2].y) to BOTH)
    }

    /** [handle] tutamağı tuvalde [point]'e sürüklenince yazının yeni hali (değişmediyse null). */
    fun resize(stroke: Stroke, handle: Int, point: Offset): Stroke? {
        val spec = stroke.text ?: return null
        val q = stroke.points
        if (q.size < 4) return null
        val (ow, oh) = TextRenderer.measure(spec, stroke.sizePx)
        val ux = q[1].x - q[0].x; val uy = q[1].y - q[0].y
        val vx = q[3].x - q[0].x; val vy = q[3].y - q[0].y
        val ul = hypot(ux, uy); val vl = hypot(vx, vy)
        if (ul < 1e-3f || vl < 1e-3f || ow <= 0f || oh <= 0f) return null
        val su = ul / ow; val sv = vl / oh
        val dx = point.x - q[0].x; val dy = point.y - q[0].y
        val alongU = (dx * ux + dy * uy) / ul
        val alongV = (dx * vx + dy * vy) / vl
        val size = TextRenderer.effectiveSize(spec, stroke.sizePx)
        val pads = TextRenderer.pads(spec, stroke.sizePx)
        val minW = pads[0] + pads[2] + size * 1.2f
        val old = spec.box ?: TextBox()
        val box = old.copy(
            width = if (handle and WIDTH != 0) max(minW, alongU / su).coerceAtMost(MAX) else old.width,
            height = if (handle and HEIGHT != 0) max(size, alongV / sv).coerceAtMost(MAX) else old.height,
        )
        if (box == old) return null
        val newSpec = spec.copy(box = box)
        val qz = ContentCodec::quantize
        return stroke.copy(text = newSpec, points = TextRenderer.refit(stroke, newSpec, stroke.sizePx).map { StrokePoint(qz(it.x), qz(it.y)) })
    }

    private const val MAX = 20000f
}
