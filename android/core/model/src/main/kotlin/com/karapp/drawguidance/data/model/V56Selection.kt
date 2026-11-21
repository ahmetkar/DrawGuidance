package com.karapp.drawguidance.data.model

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * v56 seçim işlemleri (saf; Android ve iOS'ta aynı kural): seçim alanı (boyama maskesi), genişlet/daralt, kırpma maskesi
 * sınırı, sihirli değnek benzerliği, kement dolgu.
 */
object V56Selection {
    /** Sihirli değnek ölçütü. */
    enum class WandCriterion { COLOR, STROKE, APPEARANCE }

    /** Dikdörtgen alan (sol-üstten saat yönünde 4 köşe). */
    fun rect(left: Float, top: Float, right: Float, bottom: Float): List<StrokePoint> =
        listOf(StrokePoint(left, top), StrokePoint(right, top), StrokePoint(right, bottom), StrokePoint(left, bottom))

    /** Çokgenin işaretli alanı (pozitif: y aşağı eksende saat yönü). */
    fun signedArea(p: List<StrokePoint>): Float {
        var a = 0f
        for (i in p.indices) { val q = p[i]; val r = p[(i + 1) % p.size]; a += q.x * r.y - r.x * q.y }
        return a / 2f
    }

    /**
     * Alanı [d] kadar genişletir (eksi: daraltır). Her köşe komşu kenarların dış normallerinin ortalaması boyunca
     * kaydırılır (sivri köşede en çok 3d). Çok küçülen alan (3 köşeden az ya da alan ~0) için null.
     */
    fun offset(points: List<StrokePoint>, d: Float): List<StrokePoint>? {
        val p = dedupe(points)
        if (p.size < 3) return null
        val sign = if (signedArea(p) >= 0f) 1f else -1f
        val n = p.size
        val out = ArrayList<StrokePoint>(n)
        for (i in 0 until n) {
            val a = p[(i - 1 + n) % n]; val b = p[i]; val c = p[(i + 1) % n]
            val (n1x, n1y) = normal(a, b, sign)
            val (n2x, n2y) = normal(b, c, sign)
            var mx = n1x + n2x; var my = n1y + n2y
            val ml = hypot(mx, my)
            if (ml < 1e-4f) { mx = n1x; my = n1y } else { mx /= ml; my /= ml }
            // Köşe açısına göre uzunluk (miter), sınırlı
            val cos = (mx * n1x + my * n1y).coerceAtLeast(1f / 3f)
            val k = d / cos
            out += StrokePoint(b.x + mx * k, b.y + my * k)
        }
        if (abs(signedArea(out)) < 1f || (signedArea(out) >= 0f) != (sign > 0f)) return null
        // Daralırken kenarlar yön değiştirdiyse (alan kendi üstüne katlandı) geçersiz
        for (i in 0 until n) {
            val a = p[i]; val b = p[(i + 1) % n]; val c = out[i]; val e = out[(i + 1) % n]
            if ((b.x - a.x) * (e.x - c.x) + (b.y - a.y) * (e.y - c.y) <= 0f) return null
        }
        return out
    }

    private fun normal(a: StrokePoint, b: StrokePoint, sign: Float): Pair<Float, Float> {
        val dx = b.x - a.x; val dy = b.y - a.y
        val l = hypot(dx, dy).takeIf { it > 1e-6f } ?: return 0f to 0f
        // y aşağı eksende saat yönündeki çokgenin dış normali (dy, -dx)
        return sign * dy / l to sign * -dx / l
    }

    private fun dedupe(points: List<StrokePoint>): List<StrokePoint> {
        val out = ArrayList<StrokePoint>(points.size)
        for (q in points) if (out.isEmpty() || hypot(out.last().x - q.x, out.last().y - q.y) > 0.5f) out += q
        if (out.size > 1 && hypot(out.first().x - out.last().x, out.first().y - out.last().y) <= 0.5f) out.removeAt(out.size - 1)
        return out
    }

    /** Nokta çokgenin içinde mi (çift-tek kuralı). */
    fun inside(p: List<StrokePoint>, x: Float, y: Float): Boolean {
        var inside = false
        var j = p.size - 1
        for (i in p.indices) {
            val a = p[i]; val b = p[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    /**
     * Kırpma maskesinin sınırı olabilecek nesnenin dış çizgisi: şekilde ilk kapalı alt yol, kutu türlerinde (resim, yazı,
     * formül, diyagram) 4 köşe, kement dolguda noktalar. Uygun değilse null.
     */
    fun outline(s: Stroke): List<StrokePoint>? = when {
        s.brushType == BrushType.SHAPE -> s.shape?.let { spec ->
            var start = 0
            var found: List<StrokePoint>? = null
            for (sub in spec.subpaths) {
                if (sub.closed && sub.count >= 3 && start + sub.count <= s.points.size) { found = s.points.subList(start, start + sub.count); break }
                start += sub.count
            }
            found ?: s.points.takeIf { it.size >= 3 }
        }
        s.brushType == BrushType.LASSO_FILL -> s.points.takeIf { it.size >= 3 }
        s.brushType.isQuad -> s.points.takeIf { it.size == 4 }
        else -> null
    }

    /** Kırpma maskesi uygulanabilir mi: en üstteki seçili nesnenin dış çizgisi var ve altında başka seçili eleman var. */
    fun canClip(selected: List<Stroke>): Boolean = selected.size >= 2 && outline(selected.last()) != null

    /**
     * Kırpma maskesi oluşturur: seçili elemanların en üstteki ([selected] sırası çizim sırası) sınır olur ve gizlenir;
     * ötekiler onun içinde kırpılır ve hepsi [groupId] ile gruplanır. Sonuç: değişen elemanlar (kimlikle).
     */
    fun clip(selected: List<Stroke>, groupId: String): List<Stroke> {
        if (!canClip(selected)) return emptyList()
        val top = selected.last()
        val mask = ClipMask(outline(top)!!, source = top.id)
        return selected.dropLast(1).map { it.copy(mask = mask, groupId = groupId) } + top.copy(hidden = true, groupId = groupId)
    }

    /** Kırpma maskesini kaldırır: kaynak nesne yeniden görünür, maskeler ve grup kalkar. */
    fun release(page: List<Stroke>, selectedIds: Set<String>): List<Stroke> {
        val sources = page.filter { it.id in selectedIds }.mapNotNull { it.mask?.source }.toSet()
        if (sources.isEmpty()) return page
        val masked = page.filter { it.mask?.source in sources }.map { it.id }.toSet()
        return page.map { s ->
            when {
                s.id in sources -> s.copy(hidden = false, groupId = null)
                s.id in masked -> s.copy(mask = null, groupId = null)
                else -> s
            }
        }
    }

    /** "#RRGGBB" ya da "#AARRGGBB" iki renk arasındaki uzaklık (0..1). */
    fun colorDistance(a: String, b: String): Float {
        val x = rgb(a) ?: return 1f
        val y = rgb(b) ?: return 1f
        val dr = (x shr 16 and 0xFF) - (y shr 16 and 0xFF)
        val dg = (x shr 8 and 0xFF) - (y shr 8 and 0xFF)
        val db = (x and 0xFF) - (y and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toFloat()) / 441.673f
    }

    private fun rgb(hex: String): Int? {
        val h = hex.removePrefix("#")
        val v = h.toLongOrNull(16) ?: return null
        return when (h.length) { 6 -> v.toInt(); 8 -> (v and 0xFFFFFF).toInt(); else -> null }
    }

    /** Elemanın rengi (şekilde dolgu yoksa çizgi rengi). */
    fun colorOf(s: Stroke): String = s.shape?.style?.let { it.fillColorHex ?: it.strokeColorHex } ?: s.colorHex

    /**
     * Sihirli değnek: [target]'a benzeyen elemanlar ([tolerance] 0..1). Renk: renk uzaklığı; kontur: aynı tür ve yakın
     * kalınlık; görünüm: renk, tür, kalınlık ve opaklık birlikte.
     */
    fun similar(target: Stroke, candidates: List<Stroke>, criterion: WandCriterion, tolerance: Float): List<Stroke> {
        val tol = tolerance.coerceIn(0f, 1f)
        val color = colorOf(target)
        fun sizeClose(s: Stroke): Boolean = abs(s.sizePx - target.sizePx) / max(max(s.sizePx, target.sizePx), 1f) <= tol + 1e-4f
        return candidates.filter { s ->
            if (s.hidden || s.brushType == BrushType.ERASER) return@filter false
            when (criterion) {
                WandCriterion.COLOR -> colorDistance(colorOf(s), color) <= tol + 1e-4f
                WandCriterion.STROKE -> s.brushType == target.brushType && sizeClose(s)
                WandCriterion.APPEARANCE -> s.brushType == target.brushType && sizeClose(s) &&
                    colorDistance(colorOf(s), color) <= tol + 1e-4f && abs(s.opacity - target.opacity) <= tol + 1e-4f
            }
        }
    }

    /** Kement dolgu elemanı: [points] kapalı yol, renk ve opaklık. */
    fun lassoFill(
        id: String, points: List<StrokePoint>, colorHex: String, opacity: Float, order: Int, layerId: String? = null,
        /** v56 aşama 5 doku dolgusu: dolgu dokuyla çarpılır (hazır doku paketi). */
        grain: GrainSpec? = null,
    ): Stroke =
        Stroke(id, points, colorHex, BrushType.LASSO_FILL, 1f, opacity.coerceIn(0f, 1f), order, layerId = layerId, params = grain?.let { BrushParams(grain = it) })

    /** Seçim alanı varken eklenen elemanlar: fırça izleri ve silgiler alanla kırpılır (nesneler etkilenmez). */
    fun applyArea(before: Set<String>, after: List<Stroke>, area: ClipMask?): List<Stroke> {
        if (area == null) return after
        return after.map { s ->
            if (s.id in before || s.mask != null || s.brushType.isObject || s.brushType == BrushType.LASSO_FILL) s else s.copy(mask = area)
        }
    }
}
