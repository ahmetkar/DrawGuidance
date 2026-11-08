package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.content.ink.InkLines
import com.karapp.drawguidance.content.ink.InkPoint
import com.karapp.drawguidance.content.ink.InkShapeKind
import com.karapp.drawguidance.content.ink.InkShapes
import com.karapp.drawguidance.content.shapes.SuggestRules
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import com.karapp.drawguidance.ui.screens.canvasedit.editor.InkSmoothing
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * v38 çizgi önerisi: dikkate alınan çizgiler ([strokeIds], en az iki; v39 çizim miktarı fazlaysa tek) ve her birinin düzeltmesi ([fixes]). Tek düğmeyle
 * hepsi birden düzelir (tek geri alma adımı). [joints]: ayrı çizgilerin birleştirilen uç kümeleri (köşeler).
 */
data class LineSuggestion(
    val fixes: List<ShapeFix>,
    val strokeIds: Set<String>,
    val bounds: Rect,
    val joints: Int = 0,
) {
    /** Tuvalde kesik çizgiyle önizlenen düzeltme. */
    val preview: List<List<StrokePoint>> get() = fixes.map { it.points }
}

/**
 * v38 çizgi önerisinin algoritması (yazı ve şekil önerisinden ayrı; sinir ağı yok, önceden kullanılan klasik makine
 * öğrenmesi ve geometri algoritmaları):
 * 1. Gruplama: son çizgiyle birbirine yakın ([SuggestRules.LINE_PAD]) son çizilen çizgiler; en az [SuggestRules.LINE_MIN]
 *    (v39 tek çizgi yalnız çizim miktarı fazlaysa: [SuggestRules.bigLine]).
 * 2. Her çizgi: [InkShapes] (doğru, kırık çizgi, çember, elips, üçgen, dikdörtgen/kare, çokgen; en küçük kareler,
 *    kovaryans ve RDP köşe bulma) — tanınmazsa Gauss yumuşatma ([InkSmoothing]).
 * 3. Yaylar (v38): çember yayına oturan açık çizgiler yay olur; birlikte tek çembere oturanlar ortak çemberin yaylarına.
 * 4. Çizgiler birlikte: açık uçların tek bağlantılı kümelenmesiyle köşeler birleşir ([InkLines.snapEndpoints]).
 * Öneri, değişen en az iki çizgi varsa verilir (v39 tek büyük çizgide o çizgi değişiyorsa).
 */
class LineSuggester {

    /** Çizginin düzgün geometrisi (tanınmazsa null). */
    fun fit(s: Stroke): ShapeFix? = s.takeIf { it.points.size >= 2 }?.let {
        InkShapes.recognize(it.points.map { p -> InkPoint(p.x, p.y) })?.takeIf { r -> r.score >= MIN_SHAPE_SCORE }?.let { r ->
            val q = ContentCodec::quantize
            ShapeFix(r.kind, it.id, r.points.map { p -> StrokePoint(q(p.x), q(p.y)) })
        }
    }

    /** Çizimin yumuşatılmış hali (gözle görülür bir değişiklik yoksa null). */
    fun smoothFix(s: Stroke): ShapeFix? {
        if (s.points.size < 4) return null
        val sm = InkSmoothing.smooth(s.points)
        return if (moved(s.points, sm) < 0.75f) null else ShapeFix(null, s.id, sm)
    }

    /**
     * [candidates] (çizilme sırasıyla) içinde [seed] ile birbirine yakın çizgiler (aynı katmanda; uzak duran çizgiler
     * girmez). En çok [SuggestRules.LINE_MAX].
     */
    fun group(candidates: List<Stroke>, seed: Stroke): List<Stroke> {
        val layer = seed.layerId.orEmpty()
        val pool = candidates.filter { it.id == seed.id || it.layerId.orEmpty() == layer }.takeLast(SuggestRules.LINE_MAX)
        val si = pool.indexOfFirst { it.id == seed.id }
        if (si < 0) return emptyList()
        val boxes = pool.map { s -> box(s) }
        return SuggestRules.near(boxes, si, SuggestRules.LINE_PAD, SuggestRules.LINE_MIN_PAD).map { pool[it] }
    }

    /**
     * Dikkate alınan çizgilerin önerisi (en az iki çizgi değişmiyorsa null). v39 tek çizgi: yalnız çizim miktarı fazlaysa
     * ([SingleInk.bigLine]; [canvasW]×[canvasH] tuval, 0: bilinmiyor).
     */
    fun suggest(strokes: List<Stroke>, canvasW: Float = 0f, canvasH: Float = 0f): LineSuggestion? {
        val single = strokes.size == 1
        if (single && !SingleInk.bigLine(strokes[0], canvasW, canvasH)) return null
        if (!single && strokes.size < SuggestRules.LINE_MIN) return null
        val considered = strokes.take(SuggestRules.LINE_MAX)
        val own = considered.map { s -> fit(s) ?: smoothFix(s) }.toMutableList()
        // Yaylar: kırık çizgi sanılan ya da tanınmayan açık çizgiler çember yayına oturuyorsa yay olur; birlikte tek
        // çembere oturanlar (çemberi iki yayla çizmek) ortak çemberin yaylarına döner (Kåsa çember uydurması)
        val open = considered.indices.filter { i -> own[i]?.kind.let { it == null || it == InkShapeKind.POLYLINE } && considered[i].points.size >= 5 }
        if (open.isNotEmpty()) {
            val arcs = InkLines.arcs(open.map { i -> considered[i].points.map { InkPoint(it.x, it.y) } })
            val q = ContentCodec::quantize
            open.forEachIndexed { k, i ->
                arcs[k]?.let { a -> own[i] = ShapeFix(null, considered[i].id, a.map { StrokePoint(q(it.x), q(it.y)) }) }
            }
        }
        // Çoklu çizgi adımı: açık çizgilerin yakın uçları birleşir (düzeltilmeyen çizgiler de kendi haliyle katılır)
        val items = considered.mapIndexed { i, s ->
            val pts = (own[i]?.points ?: s.points).map { InkPoint(it.x, it.y) }
            InkLines.Item(own[i]?.kind, pts)
        }
        val snapped = InkLines.snapEndpoints(items)
        val q = ContentCodec::quantize
        val fixes = considered.mapIndexedNotNull { i, s ->
            val fix = own[i]
            val after = snapped.items[i].points
            val changedBySnap = after !== items[i].points && after != items[i].points
            when {
                changedBySnap -> {
                    // Yumuşatılan çizgide basınç/eğim korunur (nokta sayısı aynıysa)
                    val base = fix?.points ?: s.points
                    val pts = if (after.size == base.size) after.mapIndexed { k, p -> base[k].copy(x = q(p.x), y = q(p.y)) }
                    else after.map { StrokePoint(q(it.x), q(it.y)) }
                    ShapeFix(fix?.kind, s.id, pts)
                }
                fix != null && moved(s.points, fix.points) >= 0.75f -> fix
                else -> null
            }
        }
        if (fixes.size < (if (single) 1 else SuggestRules.LINE_MIN)) return null
        val ids = fixes.mapTo(LinkedHashSet()) { it.strokeId }
        val bounds = considered.filter { it.id in ids }.mapNotNull { ElementOps.bounds(it) }.reduceOrNull { a, b ->
            Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))
        } ?: return null
        return LineSuggestion(fixes, ids, bounds, snapped.joints)
    }

    private fun box(s: Stroke): FloatArray {
        val pts = TemplateSuggester.flatten(s)
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (p in pts) { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        return floatArrayOf(l, t, r, b)
    }

    companion object {
        const val MIN_SHAPE_SCORE = 0.2f

        /** İki nokta dizisi arasındaki en büyük kayma (farklı uzunlukta: kutuların farkı). */
        fun moved(a: List<StrokePoint>, b: List<StrokePoint>): Float {
            if (a.size != b.size) {
                if (a.isEmpty() || b.isEmpty()) return Float.MAX_VALUE
                val d = listOf(
                    abs(a.minOf { it.x } - b.minOf { it.x }), abs(a.maxOf { it.x } - b.maxOf { it.x }),
                    abs(a.minOf { it.y } - b.minOf { it.y }), abs(a.maxOf { it.y } - b.maxOf { it.y }),
                ).max()
                // Nokta sayısı değiştiyse (düzgün geometri) en az görünür bir değişiklik sayılır
                return max(d, 1f)
            }
            var m = 0f
            for (i in a.indices) m = max(m, abs(a[i].x - b[i].x) + abs(a[i].y - b[i].y))
            return m
        }
    }
}
