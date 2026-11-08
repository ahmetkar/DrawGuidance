package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.library.kids.KidsShapes
import com.karapp.drawguidance.content.vector.P
import kotlin.math.abs
import kotlin.math.max

/**
 * v34 çocuklar için çizimin şablonları ([ModeCatalog.KIDS_PREFIX]): 86 çocuk boyama şeklinin bölge dış hatlarından
 * (en büyük 8 bölge; göz, gülümseme gibi ayrıntılar hariç) üretilir. Çocuk bir şeklin ana hatlarını çizince çocuk
 * modunun kapsamında bu şekil önerilir; öneri kabul edilince boyanabilir şeklin kendisi konur.
 */
internal fun TemplateScope.kidsTemplates() {
    for (cat in KidsShapes.categories) {
        for (shape in cat.shapes) {
            val paths = shape.paths(256f)
            val aspect = shape.aspect
            // Birim kutuda en/boy oranı korunur (uzun kenar 0..1, kısa kenar ortalanır)
            fun fit(p: P): P = if (aspect >= 1f) P(p.x, 0.5f + (p.y - 0.5f) / aspect) else P(0.5f + (p.x - 0.5f) * aspect, p.y)
            val regions = paths.filter { it.closed && it.width > 0f && it.points.size >= 3 && it.label == null }
                .sortedByDescending { area(it.points) }
                .take(8)
            val strokes = regions.map { r -> thin(r.points.map(::fit)).let { it + it.first() } }
            if (strokes.isEmpty()) continue
            out += ShapeTemplate(
                key = ModeCatalog.KIDS_PREFIX + shape.id.removePrefix("kid_"),
                name = shape.title,
                category = "Çocuk",
                group = cat.title,
                strokes = strokes,
                description = "Çocuk boyama şekli: ${shape.title}",
            )
        }
    }
}

private fun area(p: List<P>): Float {
    var a = 0f
    for (i in p.indices) { val q = p[(i + 1) % p.size]; a += p[i].x * q.y - q.x * p[i].y }
    return abs(a) / 2f
}

/** Sık örneklenmiş dış hattı seyreltir (şablon imzası yeniden örnekler; ~64 nokta yeter). */
private fun thin(p: List<P>): List<P> {
    val step = max(1, p.size / 64)
    return p.filterIndexed { i, _ -> i % step == 0 }
}
