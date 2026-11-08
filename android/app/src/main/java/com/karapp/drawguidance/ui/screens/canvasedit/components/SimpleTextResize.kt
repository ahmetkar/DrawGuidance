package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode

/**
 * v40 basit çizim türlerinde (basit resim, basit not, çocuklar için) yazı kutusunun boyutu: tek yazı seçiliyken Seç ve
 * Taşı'da da kutunun tutamakları görünür (sağ kenar en, alt kenar boy, köşe ikisi); tutamaktan sürüklemek kutuyu
 * boyutlandırır (gelişmiş türlerde yalnız Düzenle → Ölçekle'de). Taşı'da kutunun içinden sürüklemek yine taşır.
 */
internal object SimpleTextResize {

    /** Yazı kutusu tutamakları bu modda ve tuval türünde gösterilir mi (tek yazı seçiliyken). */
    fun handlesShown(mode: EditMode, kind: CanvasKind): Boolean =
        mode == EditMode.SCALE || (kind.simple && (mode == EditMode.SELECT || mode == EditMode.MOVE))

    /** Tutamak sürüklenirken önizleme (yazının yeni hali). */
    fun preview(ctx: GestureContext, box: BoxResizeGesture, point: Offset) {
        com.karapp.drawguidance.ui.drawing.TextBoxResize.resize(box.stroke, box.handle, point)?.let { ctx.setPreview(Preview.ShapeEdit(it)) }
    }

    /** Ekrandaki [p] dörtgenin ([quad], ekran) içinde mi. */
    fun inside(quad: List<Offset>, p: Offset): Boolean {
        if (quad.size < 4) return false
        var sign = 0
        for (i in 0 until 4) {
            val a = quad[i]; val b = quad[(i + 1) % 4]
            val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
            val s = if (cross > 0f) 1 else if (cross < 0f) -1 else 0
            if (s == 0) continue
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }
}

/**
 * v40 basit çizim türünde Seç ya da Taşı'da parmak seçili tek yazının kutu tutamağına indi mi. Taşı'da kutunun içinden
 * başlayan sürükleme taşımaya kalır (küçük yazılar da taşınabilsin); tutamaklar kutunun hemen dışında çizilir.
 */
internal fun simpleTextBoxHandleAt(ctx: GestureContext): BoxResizeGesture? {
    if (!ctx.base.kind.simple) return null
    val gesture = textBoxHandleAt(ctx) ?: return null
    val quad = gesture.stroke.points.take(4).map { ctx.viewport.toScreen(Offset(it.x, it.y)) }
    // Kutunun içi (Seç'te sürükleme görünümü kaydırır, Taşı'da yazıyı taşır): tutamak değil
    if (SimpleTextResize.inside(quad, ctx.start)) {
        val near = com.karapp.drawguidance.ui.drawing.TextBoxResize.handles(gesture.stroke)
            .any { (ctx.viewport.toScreen(it.first) - ctx.start).getDistance() <= ctx.hitTolerancePx * 0.6f }
        if (!near) return null
    }
    return gesture
}

/** v43 basit türlerde köşe tutamağından başlayan boyutlandırma (şekil ya da resim; karşı köşe sabit, oran korunur). */
internal class CornerResizeGesture(val stroke: com.karapp.drawguidance.data.model.Stroke, val pivot: Offset, val corner: Offset)

/**
 * v43 basit çizim türlerinde (basit resim, basit not, çocuklar için) seçili tek şekil ya da resmin köşe tutamakları:
 * Seç ve Taşı'da kutunun dört köşesinde görünür; köşeden sürüklemek karşı köşe sabit kalarak oranı koruyup
 * boyutlandırır (gelişmiş türlerde Düzenle → Ölçekle). Taşı'da elemanın içinden sürüklemek yine taşır.
 */
internal object SimpleElementResize {
    /** Köşe tutamakları bu modda ve tuval türünde gösterilir mi. */
    fun handlesShown(mode: EditMode, kind: CanvasKind): Boolean = kind.simple && (mode == EditMode.SELECT || mode == EditMode.MOVE)

    /** Köşe tutamaklarıyla boyutlanan eleman: şekil ya da resim. */
    fun eligible(s: com.karapp.drawguidance.data.model.Stroke): Boolean =
        s.brushType == com.karapp.drawguidance.data.model.BrushType.IMAGE || (s.shape != null && s.brushType != com.karapp.drawguidance.data.model.BrushType.TEXT)

    /** Elemanın kutusu (tuval). */
    fun bounds(s: com.karapp.drawguidance.data.model.Stroke): androidx.compose.ui.geometry.Rect? =
        com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps.selectionBounds(listOf(s), setOf(s.id))

    /** Köşeler ve karşı köşeleri (tuval): sol üst, sağ üst, sağ alt, sol alt. */
    fun corners(b: androidx.compose.ui.geometry.Rect): List<Pair<Offset, Offset>> = listOf(
        b.topLeft to b.bottomRight, b.topRight to b.bottomLeft, b.bottomRight to b.topLeft, b.bottomLeft to b.topRight,
    )

    /** Oranı koruyan ölçek: köşenin karşı köşeye uzaklığının değişimi (çok küçülmez). */
    fun factor(g: CornerResizeGesture, point: Offset): Float {
        val d0 = (g.corner - g.pivot).getDistance()
        if (d0 < 1e-3f) return 1f
        // Köşegen üzerine izdüşüm: parmak yana kaysa da boyut köşegen boyunca değişir
        val dir = (g.corner - g.pivot) / d0
        val along = (point.x - g.pivot.x) * dir.x + (point.y - g.pivot.y) * dir.y
        return (along / d0).coerceIn(MIN_FACTOR, com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps.MAX_SCALE)
    }

    /** Boyutlanmış eleman. */
    fun resized(g: CornerResizeGesture, point: Offset): com.karapp.drawguidance.data.model.Stroke {
        val f = factor(g, point)
        return com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps.scale(listOf(g.stroke), setOf(g.stroke.id), f, f, g.pivot).first()
    }

    fun preview(ctx: GestureContext, g: CornerResizeGesture, point: Offset) = ctx.setPreview(Preview.ShapeEdit(resized(g, point)))

    const val MIN_FACTOR = 0.08f
}

/** v43 basit türde seçili tek şekil/resmin köşe tutamağına mı inildi. */
internal fun simpleCornerAt(ctx: GestureContext): CornerResizeGesture? {
    if (!ctx.base.kind.simple || ctx.selected.size != 1) return null
    val s = ctx.base.strokes.firstOrNull { it.id in ctx.selected } ?: return null
    if (!SimpleElementResize.eligible(s)) return null
    val b = SimpleElementResize.bounds(s) ?: return null
    val (corner, pivot) = SimpleElementResize.corners(b).minByOrNull { (ctx.viewport.toScreen(it.first) - ctx.start).getDistance() } ?: return null
    if ((ctx.viewport.toScreen(corner) - ctx.start).getDistance() > ctx.handleTolerance) return null
    return CornerResizeGesture(s, pivot, corner)
}
