package com.karapp.drawguidance.ui.screens.canvasedit.editor

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.content.fill.RegionClip
import com.karapp.drawguidance.content.fill.RegionFill
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapePath
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.drawing.VectorEdit
import java.util.UUID

/**
 * v45 çocuk modunda boya fırçası: boyama kitabındaki gibi parmağın geçtiği yer boyanır. Fırça bir şeklin (ya da kalemle
 * çizilmiş kapalı bir şeklin) alanında başlarsa boya o alanın içinde kalır (kovanın bulduğu alanın maskesiyle kırpılır,
 * çizgilerin dışına taşmaz); boş kâğıtta serbestçe boyar.
 *
 * Boya şeklin içinde [TAG] etiketli, kenarsız dolgulu parçalar olarak saklanır (şekille birlikte taşınır, döndürülür;
 * görevde boyanan yer sayılır). Kalemle çizilmiş kapalı şekilde boya ayrı bir dolgu şeklidir ([PREFIX]): çizginin hemen
 * altında, onunla aynı grupta. Boş kâğıtta keçeli kalem gibi düz bir fırça izi olur. Her fırça darbesi tek geri alma
 * adımıdır.
 */
object KidsBrush {
    /** Şeklin içindeki boya parçalarının etiketi. */
    const val TAG = VectorEdit.BRUSH_PAINT

    /** Kalemle çizilmiş kapalı şekildeki boyanın kimlik öneki. */
    const val PREFIX = "kbrush-"

    /** Boş kâğıtta fırça izinin türü: çocukların keçeli kalemi (yuvarlak, opak). */
    val FREE_BRUSH: BrushType = BrushType.MARKER

    /** Boya katmanı mı: kova dolgusu ya da fırça boyası (çizgi sayılmaz, alan sınırı değildir). */
    fun isPaintLayer(tag: String?): Boolean = tag == VectorEdit.REGION || tag == TAG

    /**
     * Fırçanın başladığı yerin alanı: hangi elemanın ([strokeId]), kalem şekli mi ([ink]), alanın sınır çizgileri
     * ([walls]), kovadaki gibi kapatılan boşluk ([gap]), başlangıç noktası ([seed]) ve önizlemede boyanın kırpıldığı
     * alan sınırı ([outline], tek kapalı yol).
     */
    class Region(
        val strokeId: String,
        val ink: Boolean,
        val walls: List<RegionFill.Line>,
        val gap: Float,
        val seed: Offset,
        val outline: List<Offset>,
    ) {
        /** Alan maskesi (bir kez hesaplanır; darbe bitince kırpmada kullanılır). */
        val mask: RegionFill.Mask? by lazy { RegionFill.fillMask(walls, P(seed.x, seed.y), gap = gap) }
    }

    /** Darbeyi alanın içinde kalacak biçimde kırpar (tuval birimi çokgenler). */
    fun clip(region: Region, points: List<Offset>, radius: Float): List<List<Offset>> {
        val mask = region.mask ?: return emptyList()
        return RegionClip.clip(mask, points.map { P(it.x, it.y) }, radius).map { poly -> poly.map { Offset(it.x, it.y) } }
    }

    /** Kırpılmış boyanın şekil parçaları (kenarsız, dolgulu, kapalı). */
    fun parts(polys: List<List<Offset>>, colorHex: String): List<ShapeFactory.EditablePath> = polys.filter { it.size >= 3 }.map { poly ->
        ShapeFactory.EditablePath(poly, closed = true, fillColorHex = colorHex, widthScale = 0f, tag = TAG)
    }

    /** Kalem şeklinin içindeki boya: ayrı, kenarsız dolgu şekli (kimliği [PREFIX] ile). */
    fun inkPaint(ink: Stroke, polys: List<List<Offset>>, colorHex: String): Stroke? {
        val paths = polys.filter { it.size >= 3 }.map { ShapePath(it + it.first(), closed = true, fillColorHex = colorHex, widthScale = 0f, tag = TAG) }
        if (paths.isEmpty()) return null
        val style = ShapeStyle(strokeColorHex = colorHex, strokeWidth = 0.5f, fillColorHex = colorHex)
        return ShapeFactory.fromPaths(ShapeRegistry.FREE_PATH, paths, style, null, PREFIX + UUID.randomUUID(), ink.order)
            ?.copy(layerId = ink.layerId)
    }

    /**
     * Boyayı kalem şeklinin hemen altına (varsa kova dolgusunun üstüne) ekler; ikisi aynı grupta olur (birlikte
     * taşınır). Kalem şekli yoksa liste değişmez.
     */
    fun insertInkPaint(strokes: List<Stroke>, inkId: String, paint: Stroke): List<Stroke> {
        val at = strokes.indexOfFirst { it.id == inkId }
        if (at < 0) return strokes
        val ink = strokes[at]
        val group = ink.groupId ?: UUID.randomUUID().toString()
        val out = ArrayList<Stroke>(strokes.size + 1)
        strokes.forEachIndexed { i, s ->
            when {
                i == at -> { out += paint.copy(groupId = group); out += s.copy(groupId = group) }
                // Kalemin kova dolgusu da aynı grupta kalır
                s.id == InkFill.PREFIX + inkId -> out += s.copy(groupId = group)
                else -> out += s
            }
        }
        return out
    }

    /** Boş kâğıtta fırça izi: çocukların keçeli kalemi gibi düz, opak iz. */
    fun freeStroke(points: List<Offset>, colorHex: String, sizePx: Float, order: Int, layerId: String?): Stroke =
        Stroke(
            id = UUID.randomUUID().toString(),
            points = points.map { StrokePoint(it.x, it.y) },
            colorHex = colorHex,
            brushType = FREE_BRUSH,
            sizePx = sizePx,
            opacity = 1f,
            order = order,
            layerId = layerId,
        )

    /**
     * Önizlemede ve kırpmada kullanılan fırça yarıçapı (tuval birimi): çocuk fırçasının boyunun yarısı; çok ince
     * fırçada da boya görünsün diye en az 3.
     */
    fun radius(sizePx: Float): Float = (sizePx / 2f).coerceAtLeast(3f)
}
