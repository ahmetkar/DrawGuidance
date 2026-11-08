package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.ink.ShapeFeatures
import com.karapp.drawguidance.content.vector.P
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * v27 seçim önerileri (tüm modlarda, seçim değişince kendiliğinden):
 * - çok eleman seçiliyken şekil önerisi (şablon araması + [VectorShapeMatcher] ile hazır vektör şekiller),
 * - az (1–3) eleman seçiliyken çizimi tamamlayacak parçaların önerisi ([ShapeIndex.partialMatches] + [PartFit]).
 */
object SelectionSuggest {
    /** Bu kadar ve daha az eleman seçiliyken parça önerilir (şekil bulunamazsa). */
    const val PART_MAX = 3
    /** Şekil önerisi bu kadar elemandan başlar. */
    const val SHAPE_MIN = 2
    /** Parça önerisinin en düşük güveni. */
    const val PART_MIN_CONFIDENCE = 0.42f
    /** Bu kadar elemandan fazlası tek şekil sayılmaz (yalnız vektör şekil ve "tümünü düzelt"). */
    const val MAX_ELEMENTS = 24
}

/** Kısmi eşleşmenin eksik parçalarını seçili çizimin yanına yerleştirir. */
object PartFit {
    /**
     * [r] eşleşmesinin eksik (eşlenmemiş) parçaları, [query] çizgilerinin (tuval koordinatı) kutusuna göre: eşlenen
     * şablon parçalarının kutusu sorgunun kutusuna oturtulur. İnce parçalarda (ör. tek doğru) ölçek iki eksende aynı
     * alınır; en-boy oranı en çok [maxStretch] kat bozulur.
     */
    fun missing(r: PartialResult, query: List<List<P>>, maxStretch: Float = 1.6f): List<List<P>> {
        val geo = r.geometry
        val mapped = r.mapping.filter { it in geo.indices }.toHashSet()
        if (mapped.isEmpty() || mapped.size >= geo.size) return emptyList()
        val tb = box(mapped.flatMap { geo[it] }) ?: return emptyList()
        val qb = box(query.flatten()) ?: return emptyList()
        val tw = tb[2] - tb[0]; val th = tb[3] - tb[1]
        val qw = qb[2] - qb[0]; val qh = qb[3] - qb[1]
        val te = max(max(tw, th), 1e-4f)
        var sx = if (tw > te * 0.15f) qw / tw else Float.NaN
        var sy = if (th > te * 0.15f) qh / th else Float.NaN
        if (sx.isNaN() && sy.isNaN()) { sx = max(qw, qh) / te; sy = sx }
        if (sx.isNaN()) sx = sy
        if (sy.isNaN()) sy = sx
        if (!sx.isFinite() || !sy.isFinite() || sx <= 0f || sy <= 0f) return emptyList()
        val g = sqrt(sx * sy); val k = sqrt(maxStretch)
        sx = sx.coerceIn(g / k, g * k); sy = sy.coerceIn(g / k, g * k)
        val tcx = (tb[0] + tb[2]) / 2f; val tcy = (tb[1] + tb[3]) / 2f
        val qcx = (qb[0] + qb[2]) / 2f; val qcy = (qb[1] + qb[3]) / 2f
        return geo.indices.filter { it !in mapped }.map { i -> geo[i].map { P(qcx + (it.x - tcx) * sx, qcy + (it.y - tcy) * sy) } }
    }

    /** Eksik parçalar dahil bütün şeklin kutusu (önizleme ve balonun yeri için). */
    fun box(points: List<P>): FloatArray? {
        if (points.isEmpty()) return null
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (p in points) { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        return floatArrayOf(l, t, r, b)
    }
}

/**
 * v27 hazır vektör şekillere benzerlik (çok eleman seçiliyken "varolan şekillere yakınsa o şekil" önerisi): şekillerin
 * dış hatları ile seçili çizgiler $Q nokta bulutuyla karşılaştırılır (çizgi sayısından, sırasından ve yönünden
 * bağımsız: dört ayrı doğruyla çizilen dikdörtgen de dikdörtgendir). Bulutlar bir kez hesaplanır; arama şekil başına
 * mikro saniyeler.
 */
class VectorShapeMatcher(items: List<Item>) {
    /**
     * [strokes] şeklin çizgileri (herhangi bir kutuda). [stretch]: şekil kutuya esneyerek oturur (dikdörtgen, elips…):
     * karşılaştırma iki eksen ayrı ayrı birim kutuya getirilerek yapılır (en-boy oranından bağımsız); değilse (kare,
     * çember, yıldız…) oran korunur ve en-boy farkı cezalanır.
     */
    class Item(val id: String, val strokes: List<List<P>>, val stretch: Boolean = false)

    class Match(val id: String, val confidence: Float, val distance: Float)

    private class Entry(val id: String, val cloud: QCloud, val aspect: Float, val stretch: Boolean)

    private val entries: List<Entry> = items.mapNotNull { item ->
        val clean = item.strokes.filter { it.size >= 2 }
        if (clean.isEmpty()) return@mapNotNull null
        val polys = ShapeSignatures.polys(if (item.stretch) stretched(clean) else ShapeSignatures.normalize(clean))
        QDollar.cloud(polys)?.let { Entry(item.id, it, ShapeFeatures.aspect(polys), item.stretch) }
    }

    val size: Int get() = entries.size

    /** [query] çizgilerine en benzer şekiller (güveni [minConfidence] üstündekiler, en çok [limit]). */
    fun match(query: List<List<P>>, limit: Int = 2, minConfidence: Float = MIN_CONFIDENCE): List<Match> {
        val clean = query.filter { it.size >= 2 }
        if (clean.isEmpty() || entries.isEmpty()) return emptyList()
        val polys = ShapeSignatures.polys(ShapeSignatures.normalize(clean))
        val qc = QDollar.cloud(polys) ?: return emptyList()
        val qa = ShapeFeatures.aspect(polys)
        // Esneyen şekiller için ayrı bulut: çok ince çizimler (çizgi gibi) kutu sayılmaz
        val box = PartFit.box(clean.flatten())
        val w = box?.let { it[2] - it[0] } ?: 0f; val h = box?.let { it[3] - it[1] } ?: 0f
        val boxy = min(w, h) >= max(w, h) * STRETCH_MIN_SIDE
        val qs = if (boxy && entries.any { it.stretch }) QDollar.cloud(ShapeSignatures.polys(stretched(clean))) else null
        return entries.asSequence()
            .mapNotNull { e ->
                val d = if (e.stretch) {
                    // Esneme serbestlik verir: eşitlikte oranı koruyan şekil (kare, çember) öne geçsin
                    QDollar.distance(qs ?: return@mapNotNull null, e.cloud) + STRETCH_COST
                } else {
                    // En-boy oranı çok farklıysa bulut karşılaştırılmaz (hız)
                    if (abs(e.aspect - qa) >= 0.3f) return@mapNotNull null
                    QDollar.distance(qc, e.cloud) + ASPECT_W * abs(e.aspect - qa)
                }
                Match(e.id, exp(-d / TAU), d)
            }
            .filter { it.confidence >= minConfidence }
            .sortedBy { it.distance }
            .take(limit)
            .toList()
    }

    /** İki eksen ayrı ayrı birim kutuya (en-boy oranı 1). */
    private fun stretched(strokes: List<List<P>>): List<List<P>> {
        val b = PartFit.box(strokes.flatten()) ?: return strokes
        val w = max(b[2] - b[0], 1e-4f); val h = max(b[3] - b[1], 1e-4f)
        return strokes.map { s -> s.map { P((it.x - b[0]) / w, (it.y - b[1]) / h) } }
    }

    companion object {
        /** $Q uzaklığından güvene: el çiziminde doğru şekil ~0.03–0.08 (güven ~%60–%80), farklı şekil 0.2+. */
        const val TAU = 0.16f
        const val ASPECT_W = 0.25f
        const val MIN_CONFIDENCE = 0.6f
        /** Esneyen şekillerle karşılaştırılacak çizimin kısa kenarı en az uzun kenarın bu kadarı. */
        const val STRETCH_MIN_SIDE = 0.18f
        const val STRETCH_COST = 0.015f
    }
}
