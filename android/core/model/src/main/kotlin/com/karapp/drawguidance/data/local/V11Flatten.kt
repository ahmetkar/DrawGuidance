package com.karapp.drawguidance.data.local

import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.DocExtras
import com.karapp.drawguidance.data.model.Layers
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.SubPath

/**
 * v56 V11 belgeyi V10 bilen (eski) uygulamaya uygun hale getirir (saf; Android ve iOS'ta aynı kural):
 * - katman opaklığı katmandaki elemanların opaklığına çarpılır; karışım modu, alfa kilidi ve kırpma kalkar;
 * - belge ek bilgileri (simetri) kalkar;
 * - seçim maskesi ve yansıma dalgası kalkar; bozma/çarpıtmada resim dörtgenin (ağın dış köşelerinin) köşelerine yerleşir;
 * - gren ve resimden fırça kalkar (fırçanın kendi ucu);
 * - kement dolgu, aynı renkte dolu kapalı şekle ([BrushType.SHAPE]) çevrilir.
 * Sonuç [ContentCodec] ile V10 (ya da daha eski) olarak yazılır. Kaybolan görünüm için kullanıcıya bilgi verilir.
 */
object V11Flatten {
    fun downgrade(doc: ContentCodec.Document): ContentCodec.Document {
        val layers = doc.layers.map { it.copy(opacity = 1f, blend = com.karapp.drawguidance.data.model.LayerBlend.NORMAL, alphaLock = false, clipToBelow = false) }
        val alpha = doc.layers.associate { it.id to it.opacity.coerceIn(0f, 1f) }
        val pages = doc.pages.map { page -> page.map { s -> stroke(s, alpha[Layers.of(s, doc.layers)] ?: 1f) } }
        return ContentCodec.Document(pages, layers, DocExtras())
    }

    private fun stroke(s0: Stroke, layerAlpha: Float): Stroke {
        var s = s0
        if (layerAlpha < 1f) s = s.copy(opacity = s.opacity * layerAlpha)
        if (s.mask != null) s = s.copy(mask = null)
        s.warp?.let { w ->
            val n = w.mesh?.let { com.karapp.drawguidance.data.model.WarpMesh.sizeOf(it) } ?: 0
            val corners = w.quad?.takeIf { it.size == 4 } ?: w.mesh?.takeIf { n > 1 }?.let { m -> listOf(m[0], m[n - 1], m[n * n - 1], m[n * (n - 1)]) }
            s = s.copy(warp = null, points = if (s.brushType == BrushType.IMAGE && corners != null) corners else s.points)
        }
        s.params?.let { p -> if (p.grain != null || p.stampAssetId != null) s = s.copy(params = p.copy(grain = null, stampAssetId = null)) }
        if (s.brushType == BrushType.LASSO_FILL) {
            val pts = s.points
            s = s.copy(
                brushType = BrushType.SHAPE,
                shape = ShapeSpec(
                    kind = "lasso",
                    subpaths = listOf(SubPath(count = pts.size, closed = true)),
                    style = ShapeStyle(strokeColorHex = s.colorHex, strokeWidth = 0f, fillColorHex = s.colorHex, fillOpacity = 1f),
                ),
            )
        }
        return s
    }

    /** Eski uygulamaya gidecek içerik: gerekiyorsa düzleştirilip yeniden kodlanır. İkinci değer: düzleştirildi mi. */
    fun forPeer(content: ByteArray, peerCodec: Int): Pair<ByteArray, Boolean> {
        if (peerCodec >= ContentCodec.LATEST_VERSION) return content to false
        val doc = runCatching { ContentCodec.decodeDocument(content) }.getOrNull() ?: return content to false
        if (!ContentCodec.requiresV11(doc)) return content to false
        val flat = downgrade(doc)
        return ContentCodec.encodePages(flat.pages, layers = flat.layers) to true
    }

}
