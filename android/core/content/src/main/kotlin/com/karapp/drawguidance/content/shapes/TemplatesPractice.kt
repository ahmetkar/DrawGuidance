package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.library.practice.PRACTICE_ID
import com.karapp.drawguidance.content.library.practice.PracticeShapes
import com.karapp.drawguidance.content.vector.P
import kotlin.math.hypot
import kotlin.math.max

/**
 * v50 yetişkin alıştırma şekillerinin şablonları ([ModeCatalog.PRACTICE_PREFIX]): şeklin en uzun çizgileri (en çok
 * [MAX_STROKES]) birim kutuda. Kullanıcı bir deseni ya da konuyu (mandala, yıldız, lale, göz, fincan…) çizince resim
 * modlarında bu şekil önerilir; öneri kabul edilince kütüphanedeki düzgün çizim konur ([ShapeTemplate.solid]).
 * Basit resimde yalnız temel şekiller ([PracticeShapes.simpleIds]) önerilir.
 */
internal fun TemplateScope.practiceTemplates() {
    for (shape in PracticeShapes.all) {
        if (shape.id in SUGGEST_EXCLUDED) continue
        val paths = shape.paths(256f)
        val aspect = shape.aspect
        fun fit(p: P): P = if (aspect >= 1f) P(p.x, 0.5f + (p.y - 0.5f) / aspect) else P(0.5f + (p.x - 0.5f) * aspect, p.y)
        val strokes = paths.filter { it.points.size >= 2 && it.label == null }
            .sortedByDescending { length(it.points, it.closed) }
            .take(MAX_STROKES)
            .map { p -> seyrek(p.points.map(::fit)).let { if (p.closed) it + it.first() else it } }
        if (strokes.isEmpty()) continue
        val name = shape.id.removePrefix(PRACTICE_ID)
        val simple = shape.id in PracticeShapes.simpleIds
        out += ShapeTemplate(
            key = ModeCatalog.PRACTICE_PREFIX + name,
            name = shape.title,
            category = if (simple) ModeCatalog.PRACTICE_CATEGORY else ModeCatalog.PRACTICE_ADVANCED_CATEGORY,
            group = "Alıştırma",
            strokes = strokes,
            description = "Çizim alıştırması: ${shape.title}",
            solid = "lib." + shape.id,
        )
    }
}

/**
 * Öneride kullanılmayan temel alıştırmalar: düz çizgi, ışın, elips, spiral, dalga, ton skalası ve küre birkaç basit
 * çizgiden oluşur; şablon olunca sıradan daire ve çizgilerin düzeltme önerisini (şekil, "hepsini düzelt") ele
 * geçiriyordu. Alıştırma olarak yine sunulurlar.
 */
private val SUGGEST_EXCLUDED = setOf(
    "practice.lines", "practice.radial", "practice.ellipses", "practice.spiral", "practice.waves", "practice.value_scale",
    "practice.sphere",
)

/** Şablonda tutulan en çok çizgi (tarama gibi çok çizgili şekillerde en uzunları). */
private const val MAX_STROKES = 12

private fun length(p: List<P>, closed: Boolean): Float {
    var l = 0f
    for (i in 1 until p.size) l += hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y)
    if (closed && p.size > 2) l += hypot(p.first().x - p.last().x, p.first().y - p.last().y)
    return l
}

/** Sık örneklenmiş çizgiyi seyreltir (~64 nokta; uçlar korunur). */
private fun seyrek(p: List<P>): List<P> {
    val step = max(1, p.size / 64)
    val out = p.filterIndexed { i, _ -> i % step == 0 }
    return if (out.last() != p.last()) out + p.last() else out
}
