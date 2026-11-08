package com.karapp.drawguidance.data.diagram

import com.karapp.drawguidance.data.model.DiagramFrame
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.data.model.StrokePoint
import kotlin.math.max
import kotlin.math.min

/**
 * Diyagramın tasarım alanı ile elemanın dörtgeni arasındaki dönüşüm. Dörtgen yalnızca taşıma/döndürme/
 * ölçekleme gördüğü için paralelkenardır: tasarım noktası önce çerçeveye göre 0..1'e, sonra dörtgene eşlenir.
 */
object DiagramFrames {

    /** Tasarım alanındaki noktanın tuval koordinatı (4 köşe: sol-üst, sağ-üst, sağ-alt, sol-alt). */
    fun toCanvas(corners: List<StrokePoint>, frame: DiagramFrame, x: Float, y: Float): StrokePoint? {
        if (corners.size < 4 || frame.width <= 0f || frame.height <= 0f) return null
        val u = (x - frame.left) / frame.width
        val v = (y - frame.top) / frame.height
        val p0 = corners[0]; val p1 = corners[1]; val p3 = corners[3]
        return StrokePoint(p0.x + u * (p1.x - p0.x) + v * (p3.x - p0.x), p0.y + u * (p1.y - p0.y) + v * (p3.y - p0.y))
    }

    /** Tuval noktasının tasarım alanındaki karşılığı. */
    fun toDesign(corners: List<StrokePoint>, frame: DiagramFrame, x: Float, y: Float): StrokePoint? {
        if (corners.size < 4) return null
        val p0 = corners[0]; val p1 = corners[1]; val p3 = corners[3]
        val ax = p1.x - p0.x; val ay = p1.y - p0.y
        val bx = p3.x - p0.x; val by = p3.y - p0.y
        val det = ax * by - ay * bx
        if (kotlin.math.abs(det) < 1e-6f) return null
        val dx = x - p0.x; val dy = y - p0.y
        val u = (dx * by - dy * bx) / det
        val v = (ax * dy - ay * dx) / det
        return StrokePoint(frame.left + u * frame.width, frame.top + v * frame.height)
    }

    /**
     * Çerçeveyi [left]..[bottom] (tasarım alanı) bölgesini de kapsayacak şekilde büyütür. Dönüşüm aynı kalır:
     * adımların tuvaldeki yeri ve boyutu değişmez, yalnızca elemanın dörtgeni büyür. Değişiklik yoksa null.
     */
    fun grow(
        corners: List<StrokePoint>, spec: DiagramSpec, left: Float, top: Float, right: Float, bottom: Float, margin: Float = 0.02f,
    ): Pair<List<StrokePoint>, DiagramSpec>? {
        val f = spec.frame
        val grown = DiagramFrame(
            min(f.left, left - margin), min(f.top, top - margin), max(f.right, right + margin), max(f.bottom, bottom + margin),
        )
        if (grown == f) return null
        val tl = toCanvas(corners, f, grown.left, grown.top) ?: return null
        val tr = toCanvas(corners, f, grown.right, grown.top) ?: return null
        val br = toCanvas(corners, f, grown.right, grown.bottom) ?: return null
        val bl = toCanvas(corners, f, grown.left, grown.bottom) ?: return null
        return listOf(tl, tr, br, bl) to spec.copy(frame = grown)
    }
}
