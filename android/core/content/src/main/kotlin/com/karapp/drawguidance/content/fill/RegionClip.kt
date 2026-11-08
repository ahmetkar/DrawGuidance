package com.karapp.drawguidance.content.fill

import com.karapp.drawguidance.content.vector.P
import kotlin.math.abs
import kotlin.math.max

/**
 * v45 boya fırçası (boyama kitabı gibi): parmağın geçtiği yer, fırçanın başladığı alanın içinde kalacak biçimde kırpılır.
 * Kova ile aynı alan bulma kullanılır ([RegionFill.fillMask]: çizgiler ızgaraya kalınlıklarıyla çizilir, dokunulan
 * yerden taşma doldurma); fırça izi aynı ızgaraya fırçanın yarıçapıyla işlenir, iki maskenin kesişimi kenarlarından
 * izlenip çokgenlere çevrilir. Böylece çocuk çizginin dışına taşsa da boya alanın dışına çıkmaz; çizgiyi örtmez (kova
 * dolgusu gibi yalnız bir hücre çizginin altına girer). Saf ve durumsuzdur.
 */
object RegionClip {

    /**
     * [seed] noktasının alanında kalan fırça izi: [stroke] noktalarından [radius] yarıçapla geçen iz alanın maskesiyle
     * kesişir. Sonuç birbirinden ayrı parçalar (her biri tek kapalı yol; delikler köprüyle bağlı). Alan kapalı değilse
     * null; iz alana hiç değmiyorsa boş liste. [gap] kovadaki gibi kapatılacak küçük boşluk.
     */
    fun clip(lines: List<RegionFill.Line>, seed: P, stroke: List<P>, radius: Float, resolution: Int = 0, gap: Float = 0f): List<List<P>>? {
        val mask = RegionFill.fillMask(lines, seed, resolution, gap) ?: return null
        return clip(mask, stroke, radius)
    }

    /** Bulunmuş alan maskesiyle kırpma (bkz. [clip]). */
    fun clip(mask: RegionFill.Mask, stroke: List<P>, radius: Float): List<List<P>> {
        if (stroke.isEmpty()) return emptyList()
        val w = mask.w; val h = mask.h
        val paint = BooleanArray(w * h)
        // Fırça yarıçapı hücre biriminde; en az bir hücre (ince fırça da görünür iz bırakır)
        val rad = max(0.75f, radius * mask.scale)
        if (stroke.size == 1) {
            val x = mask.gx(stroke[0].x); val y = mask.gy(stroke[0].y)
            RegionFill.stamp(paint, w, h, x, y, x, y, rad)
        }
        for (i in 1 until stroke.size) {
            RegionFill.stamp(
                paint, w, h, mask.gx(stroke[i - 1].x), mask.gy(stroke[i - 1].y), mask.gx(stroke[i].x), mask.gy(stroke[i].y), rad,
            )
        }
        var any = false
        for (i in paint.indices) {
            val on = paint[i] && mask.cells[i]
            paint[i] = on
            if (on) any = true
        }
        if (!any) return emptyList()
        return polygons(paint, mask)
    }

    /**
     * Maskenin parçaları tuval biriminde: dış sınırlar ve delikler ayrılır, her delik onu içeren en küçük dış sınıra
     * köprüyle bağlanır.
     */
    internal fun polygons(cells: BooleanArray, mask: RegionFill.Mask): List<List<P>> {
        val loops = RegionFill.boundaries(cells, mask.w, mask.h)
        if (loops.isEmpty()) return emptyList()
        // Izgarada (y aşağı) dış sınırlar negatif, delikler pozitif işaretli alanlıdır (maske solda kalır)
        val eps = 0.7f / mask.scale
        val outers = ArrayList<List<P>>()
        val holes = ArrayList<List<P>>()
        for (loop in loops) {
            val signed = RegionFill.area(loop.map { (x, y) -> P(x.toFloat(), y.toFloat()) })
            val poly = RegionFill.simplify(loop.map { (x, y) -> mask.toCanvas(x, y) }, eps)
            if (poly.size < 3) continue
            if (signed < 0f) outers += poly else holes += poly
        }
        if (outers.isEmpty()) return emptyList()
        val owned = outers.associateWith { ArrayList<List<P>>() }
        for (hole in holes) {
            val probe = hole.first()
            val owner = outers.filter { RegionFill.contains(it, probe) }.minByOrNull { abs(RegionFill.area(it)) } ?: continue
            owned.getValue(owner) += hole
        }
        return outers.map { outer ->
            RegionFill.keyhole(RegionFill.orient(outer, positive = true), owned.getValue(outer).map { RegionFill.orient(it, positive = false) })
        }
    }
}
