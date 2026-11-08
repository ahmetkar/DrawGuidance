package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.content.ink.Handwriting
import com.karapp.drawguidance.content.ink.InkMode
import com.karapp.drawguidance.content.ink.InkPoint
import com.karapp.drawguidance.content.ink.InkStroke
import com.karapp.drawguidance.content.shapes.SuggestRules
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.SuggestPolicy
import com.karapp.drawguidance.ui.drawing.suggestPolicy
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps

/**
 * v23 yazı önerisi: tanınan yazı, seçenekleri ve yazıya dönüşecek izler (v38 en az iki; v39 tek iz yalnız çizim miktarı
 * fazlaysa; tek düğmeyle hepsi yazıya).
 */
data class TextSuggestion(
    val text: String,
    val alternatives: List<String>,
    val strokeIds: Set<String>,
    val bounds: Rect,
    val confidence: Float,
)

/**
 * v23 yazı önerisinin algoritması (şekil ve çizgi önerisinden ayrı). v38 kuralları:
 * 1. Gruplama: yalnızca yazı kalemleriyle yazılmış, son izin satırında birbirine yakın izler (dikeyde örtüşen ve yatayda
 *    harf aralığıyla zincirlenen); en az [SuggestRules.TEXT_MIN] eleman. v39 tek iz: yalnız çizim miktarı fazlaysa
 *    ([SuggestRules.bigText]: büyük tek çizgilik harf/rakam), düzgün geometrik şekil değilse ([SingleInk.geometric]) ve
 *    tanıma güveni ≥ [SuggestRules.TEXT_SINGLE_CONFIDENCE] ise (yazı ağının kapısı çok çizgili yazıyla eğitildiğinden tek
 *    izde kullanılmaz; yerine sıkı güven eşiği ve sezgi süzgeci).
 * 2. Yazı sinir ağının "yazı mı" kapısı ([Handwriting.writingScore], [HandwritingNet][com.karapp.drawguidance.content.ink.HandwritingNet]
 *    gövdesi + v38 kapı başları) ve hızlı sezgi süzgeci ([Handwriting.looksLikeWriting]): çizim, şekil ve çizgi
 *    grupları yazı sanılmaz.
 * 3. Tanıma: yazı ağının karakter başları + bölütleme ve ışın aramalı dinamik programlama; Türkçe harf ikilisi dil
 *    modeli ve sözlükle yeniden sıralama ([Handwriting.recognize]).
 * Aynı iz kümesi için sonuç önbellekten döner (kalem durduğunda yeniden hesaplanmaz).
 */
class TextSuggester(
    /**
     * v37 arayüz dili (tr, en, ru, ar, ja, ko…): not alma modunun yazı ağı o dilin yazısını (Latin, Kiril, Arap,
     * Japon, Korece) öncelikli okur.
     */
    private val language: () -> String? = { com.karapp.drawguidance.i18n.I18n.language },
) {
    private var cacheKey: List<String> = emptyList()
    private var cacheValue: TextSuggestion? = null
    private var gateKey: List<String> = emptyList()
    private var gateValue = false

    /** Yazı adayı iz: yazı kalemi (fırça, iz ve desen fırçaları yazıya dönüşmez). */
    fun isWritingPen(s: Stroke): Boolean = s.points.isNotEmpty() && s.brushType.suggestPolicy() == SuggestPolicy.WRITING

    /**
     * v38 [pool] (tuvalin çizgileri, çizilme sırasıyla) içinde [seed] ile aynı katmanda, aynı satırda birbirine yakın yazı
     * kalemi izleri (en çok son [POOL] iz).
     */
    fun group(pool: List<Stroke>, seed: Stroke): List<Stroke> {
        if (!isWritingPen(seed)) return emptyList()
        val layer = seed.layerId.orEmpty()
        val candidates = pool.asReversed().asSequence()
            .filter { it.id == seed.id || (isWritingPen(it) && it.layerId.orEmpty() == layer) }
            .take(POOL).toList().asReversed()
        return line(candidates, seed)
    }

    /**
     * [recent] (eskiden yeniye) içinde [seed] izinin satırı: dikeyde örtüşen ve yatayda zincirleme yakın (yazı
     * yüksekliğinin ~2,5 katı aralık) izler.
     */
    fun line(recent: List<Stroke>, seed: Stroke? = recent.lastOrNull()): List<Stroke> {
        val last = seed ?: return emptyList()
        val lb = ElementOps.bounds(last) ?: return emptyList()
        val candidates = recent.mapNotNull { s -> ElementOps.bounds(s)?.let { s to it } }
            .filter { (_, b) -> b.bottom >= lb.top - lb.height * 0.6f && b.top <= lb.bottom + lb.height * 0.6f }
        if (candidates.isEmpty()) return listOf(last)
        val h = candidates.map { it.second.height }.sorted()[candidates.size / 2].coerceAtLeast(8f)
        val gap = h * 2.5f
        // Son izden başlayıp sağa ve sola yakın izler zincirlenir
        val chosen = LinkedHashSet<Stroke>()
        chosen += last
        var left = lb.left; var right = lb.right
        var changed = true
        while (changed) {
            changed = false
            for ((s, b) in candidates) {
                if (s in chosen) continue
                if (b.right >= left - gap && b.left <= right + gap) {
                    chosen += s
                    left = minOf(left, b.left); right = maxOf(right, b.right)
                    changed = true
                }
            }
        }
        return recent.filter { it in chosen }
    }

    private fun bounds(line: List<Stroke>): Rect? = line.mapNotNull { ElementOps.bounds(it) }
        .reduceOrNull { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }

    private fun ink(line: List<Stroke>) = line.map { s -> InkStroke(s.points.map { InkPoint(it.x, it.y) }) }

    /**
     * v38 [line] yazı gibi mi duruyor: en az [SuggestRules.TEXT_MIN] yazı kalemi izi, sezgi süzgeci ve yazı ağının
     * "yazı mı" kapısı ([Handwriting.GATE_MIN]; kapısı olmayan eski ağda yalnız sezgi). Şekil ve çizgi önerisi de
     * yazıya öneri yapmamak için bunu kullanır. v39 tek iz: [single] (büyük ve güvenle okunan tek iz).
     */
    fun looksLikeText(line: List<Stroke>, canvasW: Float = 0f, canvasH: Float = 0f): Boolean {
        if (line.isEmpty() || !line.all(::isWritingPen)) return false
        if (line.size == 1) return single(line[0], canvasW, canvasH) != null
        if (line.size < SuggestRules.TEXT_MIN) return false
        val lang = language()
        val key = line.map { it.id } + "lang:$lang"
        if (key == gateKey) return gateValue
        val b = bounds(line)
        val ink = ink(line)
        val value = b != null && b.height in 8f..600f && Handwriting.looksLikeWriting(ink, lang) &&
            (Handwriting.writingScore(ink)?.let { it >= Handwriting.GATE_MIN } ?: true)
        gateKey = key
        gateValue = value
        return value
    }

    private var singleKey: List<String> = emptyList()
    private var singleValue: TextSuggestion? = null

    /**
     * v39 tek izin yazı önerisi: yazı kalemi, çizim miktarı fazla ([SingleInk.bigText]), düzgün geometrik şekil değil,
     * sezgi süzgecinden geçen ve [SuggestRules.TEXT_SINGLE_CONFIDENCE] güvenle okunan iz (yoksa null; önbellekli).
     */
    fun single(s: Stroke, canvasW: Float = 0f, canvasH: Float = 0f, mode: InkMode = InkMode.MIXED): TextSuggestion? {
        if (!isWritingPen(s) || !SingleInk.bigText(s, canvasW, canvasH)) return null
        val lang = language()
        val key = listOf(s.id, "lang:$lang", "mode:$mode", "c:${SuggestRules.side(canvasW, canvasH)}", "n:${s.points.size}")
        if (key == singleKey) return singleValue
        val bounds = ElementOps.bounds(s)
        val value = if (bounds == null || bounds.height > 600f || SingleInk.geometric(s)) null else {
            Handwriting.recognize(ink(listOf(s)), mode, language = lang)
                ?.takeIf { it.confidence >= SuggestRules.TEXT_SINGLE_CONFIDENCE && it.text.isNotBlank() }
                ?.let { r -> TextSuggestion(r.text, r.alternatives.filter { it != r.text }.take(3), linkedSetOf(s.id), bounds, r.confidence) }
        }
        singleKey = key
        singleValue = value
        return value
    }

    /** Satırdaki izler yazıysa tanınan yazı (yoksa null; v39 tek iz yalnız [single] ise). */
    fun suggest(line: List<Stroke>, mode: InkMode = InkMode.MIXED, canvasW: Float = 0f, canvasH: Float = 0f): TextSuggestion? {
        if (line.size == 1) return single(line[0], canvasW, canvasH, mode)
        if (line.size < SuggestRules.TEXT_MIN || !line.all(::isWritingPen)) return null
        val lang = language()
        val key = line.map { it.id } + "lang:$lang"
        if (key == cacheKey) return cacheValue
        val bounds = bounds(line) ?: return null
        val result = if (!looksLikeText(line)) null else {
            Handwriting.recognize(ink(line), mode, language = lang)?.takeIf { it.confidence >= MIN_CONFIDENCE && it.text.isNotBlank() }?.let { r ->
                TextSuggestion(r.text, r.alternatives.filter { it != r.text }.take(3), line.mapTo(LinkedHashSet()) { it.id }, bounds, r.confidence)
            }
        }
        cacheKey = key
        cacheValue = result
        return result
    }

    companion object {
        const val MIN_CONFIDENCE = 0.5f
        /** Satır aramasında geriye bakılacak en çok iz. */
        const val POOL = 40
    }
}
