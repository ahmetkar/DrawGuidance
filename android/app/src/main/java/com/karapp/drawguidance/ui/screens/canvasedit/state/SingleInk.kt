package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.content.ink.InkPoint
import com.karapp.drawguidance.content.ink.InkShapeKind
import com.karapp.drawguidance.content.ink.InkShapes
import com.karapp.drawguidance.content.shapes.SuggestRules
import com.karapp.drawguidance.data.model.Stroke

/**
 * v39 tek çizginin çizim miktarı: yazı ve çizgi önerisi tek elemanda yalnız çizim miktarı fazlaysa yapılır
 * ([SuggestRules.bigLine], [SuggestRules.bigText]); [canvasW]×[canvasH] tuval (0: bilinmiyor, olağan boyut sayılır).
 */
object SingleInk {
    private class Amount(val length: Float, val points: Int, val w: Float, val h: Float)

    private fun amount(s: Stroke): Amount? {
        if (s.points.size < 2) return null
        val xs = FloatArray(s.points.size) { s.points[it].x }
        val ys = FloatArray(s.points.size) { s.points[it].y }
        return Amount(SuggestRules.pathLength(xs, ys), s.points.size, xs.max() - xs.min(), ys.max() - ys.min())
    }

    /** Tek çizgi çizgi önerisi alacak kadar çok çizim mi. */
    fun bigLine(s: Stroke, canvasW: Float = 0f, canvasH: Float = 0f): Boolean =
        amount(s)?.let { SuggestRules.bigLine(it.length, it.points, it.w, it.h, canvasW, canvasH) } == true

    /** Tek çizgi yazı önerisine aday olacak kadar çok çizim mi (tanıma güveni ayrıca denetlenir). */
    fun bigText(s: Stroke, canvasW: Float = 0f, canvasH: Float = 0f): Boolean =
        amount(s)?.let { SuggestRules.bigText(it.length, it.points, it.w, it.h, canvasW, canvasH) } == true

    /**
     * Tek çizgi düzgün bir geometrik şekil mi (doğru, çember, elips, üçgen, dörtgen, çokgen): böyle bir çizgi yazıya
     * önerilmez (ör. büyük çember "0"/"O" değil çizimdir).
     */
    fun geometric(s: Stroke): Boolean =
        InkShapes.recognize(s.points.map { InkPoint(it.x, it.y) })?.let { r ->
            r.kind != InkShapeKind.POLYLINE && r.score >= GEOMETRIC_SCORE
        } == true

    /** Tek çizginin geometrik şekil sayılması için en az tanıma puanı. */
    const val GEOMETRIC_SCORE = 0.5f
}
