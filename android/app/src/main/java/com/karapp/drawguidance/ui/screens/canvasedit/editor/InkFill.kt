package com.karapp.drawguidance.ui.screens.canvasedit.editor

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapePath
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * v44 kalemle (fırçayla) çizilmiş kapalı şeklin içini boyama. Çizgi kapalıysa (uçları birbirine yakın, çizgi uzun) içi
 * çizginin altına yerleşen dolgu şekliyle boyanır: dolgu çizgiyle aynı grupta durur (birlikte taşınır, döndürülür),
 * çizginin kendisi değişmez; yeniden boyamada aynı dolgunun rengi değişir. Kimliği `inkfill-<çizgi>`.
 */
object InkFill {
    const val PREFIX = "inkfill-"

    /** Dolguya uygun fırça izi mi (şekil, yazı, resim, silgi vb. değil). */
    fun isInk(s: Stroke): Boolean = s.shape == null && s.text == null && s.diagram == null && s.formula == null &&
        s.brushType != BrushType.ERASER && s.brushType != BrushType.IMAGE && s.brushType != BrushType.TEXT &&
        s.brushType != BrushType.SHAPE && s.brushType != BrushType.DIAGRAM && s.brushType != BrushType.FORMULA && s.points.size >= 8

    /** Çizgi kapalı mı: uçları arası boşluk çizginin kalınlığına ve boyuna göre küçük, içi alan kaplıyor. */
    fun isClosed(s: Stroke): Boolean {
        if (!isInk(s)) return false
        val p = s.points
        val gap = hypot(p.first().x - p.last().x, p.first().y - p.last().y)
        var length = 0f
        for (i in 1 until p.size) length += hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y)
        if (length < 40f) return false
        if (gap > max(24f, s.sizePx * 3f) && gap > length * 0.08f) return false
        return abs(area(p.map { Offset(it.x, it.y) })) > length * max(2f, s.sizePx) * 0.5f
    }

    /** [point] noktasını içine alan en üstteki kapalı çizgi. */
    fun closedAt(strokes: List<Stroke>, point: Offset, locked: Set<String>): Stroke? =
        strokes.asReversed().firstOrNull { s ->
            !s.hidden && s.id !in locked && isClosed(s) && AreaSelect.inside(s.points.map { Offset(it.x, it.y) }, point)
        }

    /** Kapalı çizgilerin ([ids]) içini [colorHex] ile boyar (dolgu yoksa eklenir, varsa rengi değişir). */
    fun apply(strokes: List<Stroke>, ids: Set<String>, colorHex: String): List<Stroke> {
        if (ids.isEmpty()) return strokes
        val out = ArrayList<Stroke>(strokes.size + ids.size)
        val existing = strokes.filter { it.id.startsWith(PREFIX) }.associateBy { it.id.removePrefix(PREFIX) }
        for (s in strokes) {
            if (s.id.startsWith(PREFIX) && s.id.removePrefix(PREFIX) in ids) {
                val shape = s.shape ?: continue
                out += s.copy(colorHex = colorHex, shape = shape.copy(style = shape.style.copy(fillColorHex = colorHex, strokeColorHex = colorHex)))
                continue
            }
            if (s.id in ids && existing[s.id] == null && isClosed(s)) {
                val group = s.groupId ?: UUID.randomUUID().toString()
                fill(s, colorHex)?.let { out += it.copy(groupId = group, layerId = s.layerId) }
                out += s.copy(groupId = group)
                continue
            }
            out += s
        }
        return out
    }

    /** Çizginin içini kaplayan dolgu şekli (çizginin altında, kenarı görünmez). */
    fun fill(s: Stroke, colorHex: String): Stroke? {
        val pts = s.points.map { Offset(it.x, it.y) }
        val path = ShapePath(pts + pts.first(), closed = true)
        val style = ShapeStyle(strokeColorHex = colorHex, strokeWidth = 0.5f, fillColorHex = colorHex)
        return ShapeFactory.fromPaths(ShapeRegistry.FREE_PATH, listOf(path), style, null, PREFIX + s.id, s.order)
    }

    private fun area(p: List<Offset>): Float {
        var a = 0f
        for (i in p.indices) { val j = (i + 1) % p.size; a += p[i].x * p[j].y - p[j].x * p[i].y }
        return a / 2f
    }
}
