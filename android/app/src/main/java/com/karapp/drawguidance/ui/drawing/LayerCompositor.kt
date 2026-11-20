package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.LayerBlend
import com.karapp.drawguidance.data.model.Layers
import com.karapp.drawguidance.data.model.Stroke

/**
 * v56 katman birleştirme (plan 4. bölüm). Opaklığı 1 ve modu Normal olan, kırpmasız katmanlar bugünkü gibi doğrudan
 * çizilir; ötekiler ekran dışı ara katmanda (`saveLayer`, katmanın opaklığı ve karışım modu) toplanır.
 * - Alttakine kırpma: katman, alttaki ilk kırpmasız katmanın ("taban") alfa'sıyla `DstIn` maskelenir.
 * - Alfa kilidi: kilitliyken çizilen çizgi ([com.karapp.drawguidance.data.model.BrushParams.alphaLocked]) katmanın
 *   önceki boyasının üstüne `SrcAtop` ile çizilir ([StrokeRenderer.drawStrokeList]).
 * API 29 öncesinde Compose, yerel karşılığı olmayan modları en yakın PorterDuff moduna çevirir.
 */
object LayerCompositor {
    private val bounds = Rect(-1e6f, -1e6f, 1e6f, 1e6f)

    fun blendMode(b: LayerBlend): BlendMode = when (b) {
        LayerBlend.NORMAL -> BlendMode.SrcOver
        LayerBlend.MULTIPLY -> BlendMode.Multiply
        LayerBlend.SCREEN -> BlendMode.Screen
        LayerBlend.OVERLAY -> BlendMode.Overlay
        LayerBlend.DARKEN -> BlendMode.Darken
        LayerBlend.LIGHTEN -> BlendMode.Lighten
        LayerBlend.COLOR_DODGE -> BlendMode.ColorDodge
        LayerBlend.COLOR_BURN -> BlendMode.ColorBurn
        LayerBlend.HARD_LIGHT -> BlendMode.Hardlight
        LayerBlend.SOFT_LIGHT -> BlendMode.Softlight
        LayerBlend.DIFFERENCE -> BlendMode.Difference
        LayerBlend.EXCLUSION -> BlendMode.Exclusion
        LayerBlend.HUE -> BlendMode.Hue
        LayerBlend.SATURATION -> BlendMode.Saturation
        LayerBlend.COLOR -> BlendMode.Color
        LayerBlend.LUMINOSITY -> BlendMode.Luminosity
        LayerBlend.ADD -> BlendMode.Plus
    }

    /** Katman listesi birleştirme gerektiriyor mu (yoksa çizim bugünkü gibi düz liste). */
    fun needed(layers: List<Layer>): Boolean = layers.any { it.composited }

    /**
     * Önbelleğe yalnız eklenen çizimleri çizmek doğru mu: eklenenlerin katmanı birleştirilmiyor ve kırpılan bir
     * katmanın tabanı değil (yoksa görüntü baştan üretilir).
     */
    fun appendSafe(added: List<Stroke>, layers: List<Layer>): Boolean {
        if (!needed(layers) && added.none { it.params?.alphaLocked == true }) return true
        if (added.any { it.params?.alphaLocked == true }) return false
        val unsafe = HashSet<String>()
        var base: Layer? = null
        layers.forEachIndexed { i, l ->
            val clipped = l.clipToBelow && i > 0
            if (clipped) { unsafe += l.id; base?.let { unsafe += it.id } } else base = l
            if (l.composited) unsafe += l.id
        }
        return added.none { Layers.of(it, layers) in unsafe }
    }

    /** Canlı (henüz önbellekte olmayan) çizginin katman görünümü: opaklık ve mod (kırpma önizlemede yok). */
    inline fun DrawScope.withLayerLook(layer: Layer?, block: DrawScope.() -> Unit) {
        if (layer == null || (layer.opacity >= 1f && layer.blend == LayerBlend.NORMAL)) { block(); return }
        drawIntoCanvas { it.saveLayer(Rect(-1e6f, -1e6f, 1e6f, 1e6f), paintOf(layer)) }
        block()
        drawIntoCanvas { it.restore() }
    }

    fun paintOf(layer: Layer): Paint = Paint().apply {
        alpha = layer.opacity.coerceIn(0f, 1f)
        blendMode = blendMode(layer.blend)
    }

    /**
     * Çizimleri katman katman birleştirir. [plain] bir katmanın çizimlerini (silgi ve alfa kilidiyle) çizer.
     * Çizimler katman sırasına dizili olmasa da her çizim kendi katmanında, katman içi sırası korunarak çizilir.
     */
    fun DrawScope.drawLayered(strokes: List<Stroke>, layers: List<Layer>, plain: DrawScope.(List<Stroke>) -> Unit) {
        val index = HashMap<String, Int>(layers.size * 2)
        layers.forEachIndexed { i, l -> index[l.id] = i }
        val groups = List(layers.size) { ArrayList<Stroke>() }
        for (s in strokes) groups[index[Layers.of(s, layers)] ?: 0] += s
        var base: Int = -1
        for (i in layers.indices) {
            val l = layers[i]
            val clipped = l.clipToBelow && base >= 0
            if (!clipped) base = i
            val list = groups[i]
            if (list.isEmpty()) continue
            if (!clipped && l.opacity >= 1f && l.blend == LayerBlend.NORMAL) { plain(list); continue }
            drawIntoCanvas { it.saveLayer(bounds, paintOf(l)) }
            plain(list)
            if (clipped) {
                // Tabanın alfa'sı maske: taban boşsa (ya da gizliyse) kırpılan katman görünmez
                drawIntoCanvas { it.saveLayer(bounds, Paint().apply { blendMode = BlendMode.DstIn }) }
                plain(groups[base])
                drawIntoCanvas { it.restore() }
            }
            drawIntoCanvas { it.restore() }
        }
    }
}
