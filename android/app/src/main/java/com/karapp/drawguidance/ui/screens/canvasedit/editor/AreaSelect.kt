package com.karapp.drawguidance.ui.screens.canvasedit.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** v43 alanla seçimin geometrisi: dikdörtgen ve kapalı çizgi (çokgen) içinde mi. */
object AreaSelect {
    fun rect(a: Offset, b: Offset): Rect = Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))

    /** Çift-tek kuralıyla nokta çokgenin içinde mi (çokgen kendiliğinden kapanır). */
    fun inside(poly: List<Offset>, p: Offset): Boolean {
        if (poly.size < 3) return false
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    /** Kalemle çizilen çizgi alan sayılabilecek kadar büyük mü (en az 3 nokta, kutusu [minSize]'dan büyük). */
    fun usable(poly: List<Offset>, minSize: Float): Boolean {
        if (poly.size < 3) return false
        val r = Rect(poly.minOf { it.x }, poly.minOf { it.y }, poly.maxOf { it.x }, poly.maxOf { it.y })
        return r.width >= minSize && r.height >= minSize
    }
}
