package com.karapp.drawguidance.data.model

import kotlin.math.max
import kotlin.math.min

/**
 * v56 2C-3 sayfalar arası kes, kopyala, yapıştır (saf; Android ve iOS'ta aynı kural): seçili elemanların toplanması,
 * yapıştırılacak konum, yeni kimlikler (gruplar ve kırpma maskesi kaynağı kendi aralarında korunur) ve resim
 * varlıklarının kimlik eşlemesi.
 */
object ElementClip {
    /** Yapıştırmada aynı yere art arda kaydırma. */
    const val REPEAT_SHIFT = 16f

    /**
     * Sayfadan seçili elemanlar (çizim sırasıyla). Seçili elemanların kırpma maskesi kaynağı seçilmemişse o da alınır
     * (maskeli eleman kaynağı olmadan yapıştırılmaz).
     */
    fun collect(page: List<Stroke>, ids: Set<String>): List<Stroke> {
        if (ids.isEmpty()) return emptyList()
        val sources = page.filter { it.id in ids }.mapNotNull { it.mask?.source }.toSet()
        return page.filter { it.id in ids || it.id in sources }
    }

    /** Elemanların kapsayan kutusu [sol, üst, sağ, alt] (nokta ve kırpma köşeleri; boşsa null). */
    fun bounds(strokes: List<Stroke>): FloatArray? {
        var l = Float.POSITIVE_INFINITY; var t = Float.POSITIVE_INFINITY
        var r = Float.NEGATIVE_INFINITY; var b = Float.NEGATIVE_INFINITY
        for (s in strokes) for (p in s.points) { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        return if (l > r || t > b) null else floatArrayOf(l, t, r, b)
    }

    /**
     * Yapıştırma kayması: [anchor] verilirse kutunun merkezi oraya; yoksa aynı konum, ama kutu hedef sayfaya
     * ([pageW] x [pageH]) sığmıyorsa ya da görünür alanın ([visible], [sol, üst, sağ, alt]) dışındaysa görünür alanın
     * (yoksa sayfanın) ortasına. [repeat]: aynı yere kaçıncı yapıştırma (0: ilk), her biri [REPEAT_SHIFT] kaydırır.
     */
    fun offset(
        bounds: FloatArray, pageW: Float, pageH: Float, visible: FloatArray? = null, anchor: Pair<Float, Float>? = null, repeat: Int = 0,
    ): Pair<Float, Float> {
        val cx = (bounds[0] + bounds[2]) / 2f; val cy = (bounds[1] + bounds[3]) / 2f
        val shift = REPEAT_SHIFT * repeat
        if (anchor != null) return anchor.first - cx + shift to anchor.second - cy + shift
        val outside = bounds[0] < -0.5f || bounds[1] < -0.5f || bounds[2] > pageW + 0.5f || bounds[3] > pageH + 0.5f
        val hidden = visible != null && (bounds[2] < visible[0] || bounds[0] > visible[2] || bounds[3] < visible[1] || bounds[1] > visible[3])
        if (outside || hidden) {
            val tx = visible?.let { (it[0] + it[2]) / 2f } ?: (pageW / 2f)
            val ty = visible?.let { (it[1] + it[3]) / 2f } ?: (pageH / 2f)
            return tx - cx + shift to ty - cy + shift
        }
        return shift to shift
    }

    /** Elemanların kullandığı tuval varlıkları (resim, 3B model verisi, yazı kutusu resmi, damga resmi). */
    fun assetIds(strokes: List<Stroke>): Set<String> {
        val out = LinkedHashSet<String>()
        for (s in strokes) {
            s.assetId?.let(out::add)
            s.model?.dataAssetId?.let(out::add)
            s.text?.box?.imageId?.takeIf { it.startsWith(BackgroundSpec.ASSET) }?.let { out += it.removePrefix(BackgroundSpec.ASSET) }
            s.params?.stampAssetId?.let(out::add)
        }
        return out
    }

    /**
     * Yapıştırılacak kopyalar: yeni kimlikler ([newId]), gruplar ve kırpma maskesi kaynağı kendi aralarında yeniden
     * eşlenir, noktalar ([dx], [dy]) kaydırılır ([q] nicemleme), katman boşaltılır (hedefte etkin katmana girer), sıra
     * [startOrder]'dan sürer, varlık kimlikleri [assets] ile değiştirilir.
     */
    fun paste(
        clip: List<Stroke>, newId: () -> String, startOrder: Int, dx: Float = 0f, dy: Float = 0f,
        assets: Map<String, String> = emptyMap(), q: (Float) -> Float = { it },
    ): List<Stroke> {
        val ids = clip.associate { it.id to newId() }
        val groups = HashMap<String, String>()
        fun moved(p: StrokePoint) = p.at(q(p.x + dx), q(p.y + dy))
        return clip.mapIndexed { i, s ->
            val mask = s.mask?.let { m -> m.copy(points = m.points.map(::moved), source = m.source?.let { ids[it] ?: it }) }
            var c = s.copy(
                id = ids.getValue(s.id),
                groupId = s.groupId?.let { g -> groups.getOrPut(g) { newId() } },
                order = startOrder + i,
                layerId = null,
                points = s.points.map(::moved),
                clip = s.clip?.map(::moved),
                mask = mask,
            )
            if (assets.isNotEmpty()) {
                s.assetId?.let { a -> assets[a]?.let { c = c.copy(assetId = it) } }
                s.model?.let { link -> assets[link.dataAssetId]?.let { c = c.copy(model = link.copy(dataAssetId = it)) } }
                s.params?.stampAssetId?.let { a -> assets[a]?.let { c = c.copy(params = c.params?.copy(stampAssetId = it)) } }
                s.text?.box?.imageId?.takeIf { it.startsWith(BackgroundSpec.ASSET) }?.let { img ->
                    assets[img.removePrefix(BackgroundSpec.ASSET)]?.let { n ->
                        c = c.copy(text = c.text?.copy(box = c.text?.box?.copy(imageId = BackgroundSpec.ASSET + n)))
                    }
                }
            }
            c
        }
    }
}
