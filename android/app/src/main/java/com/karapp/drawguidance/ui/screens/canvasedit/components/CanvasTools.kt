package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.drawing.GuideRenderer
import com.karapp.drawguidance.ui.drawing.LiveStrokeLayer
import com.karapp.drawguidance.ui.screens.canvasedit.BrushSettings
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Hareket sürerken gösterilen, henüz kaydedilmemiş dönüşüm ya da yardımcı çizim (tuval koordinatlarında). */
internal sealed interface Preview {
    data class Move(val delta: Offset) : Preview
    data class Rotate(val degrees: Float, val pivot: Offset) : Preview
    data class Scale(val sx: Float, val sy: Float, val pivot: Offset) : Preview
    data class Expand(val rect: Rect) : Preview
    data class Shape(val gestureId: String, val start: Offset, val end: Offset) : Preview
    /** Diyagram yerleştirme kutusu (kesik çerçeve). */
    data class Box(val start: Offset, val end: Offset) : Preview
    /** Yardımcı doğru: şekil düzenlemede nokta taşıma/bağlama, diyagramda çekilen ok. */
    data class Guideline(val from: Offset, val to: Offset) : Preview
    /** Şekil düzenlemede sürüklemenin sonucu (parçası taşınmış/döndürülmüş ya da çizgi eklenmiş şekil). */
    data class ShapeEdit(val stroke: com.karapp.drawguidance.data.model.Stroke, val path: List<Offset> = emptyList()) : Preview
    /** v43 kalemle alan seçiminde çizilen çizgi (kesik, kendiliğinden kapanır). */
    data class Lasso(val points: List<Offset>) : Preview
}

internal enum class CropHandle { LEFT, TOP, RIGHT, BOTTOM, MOVE }

internal enum class ScaleHandle { CENTER, CORNER, HORIZONTAL, VERTICAL }

/** Çift dokunmayı tanımak için son dokunuş (hareketler arasında saklanır). */
internal class TapMemory {
    var time = 0L
    var position = Offset.Zero

    /** Bu dokunuş öncekiyle çift dokunma mı; değilse bir sonrakine hatırlanır. */
    fun isDouble(upTime: Long, at: Offset, maxDistance: Float): Boolean {
        val double = upTime - time <= DOUBLE_TAP_MS && (at - position).getDistance() <= maxDistance
        if (double) time = 0L else { time = upTime; position = at }
        return double
    }
}

/** Bir dokunma hareketinin (parmak inişinden kalkışına) araçlara verdiği bağlam. */
internal class GestureContext(
    val viewport: ViewportState,
    val base: CanvasUi,
    val selected: Set<String>,
    val actions: CanvasActions,
    val brush: BrushSettings,
    val crop: Rect?,
    val shapeTool: ShapeTool,
    val live: LiveStrokeLayer,
    val viewW: Int,
    val viewH: Int,
    /** Parmağın indiği yer (ekran) ve tuvaldeki karşılığı. */
    val start: Offset,
    val startCanvas: Offset,
    val handleTolerance: Float,
    val edgeTolerance: Float,
    /** Dokunma payı (ekran pikseli); tuval biriminde [hitTolerance]. */
    val hitTolerancePx: Float,
    val taps: TapMemory,
    val setPreview: (Preview?) -> Unit,
    val setFinger: (Offset?) -> Unit,
    val refreshLive: () -> Unit,
) {
    val hitTolerance: Float get() = hitTolerancePx / viewport.scale

    /** v40 cetvel/kalıp kenarına yapışma uzaklığı (tuval birimi; ekranda sabit 28 dp). */
    val guideCapture: Float get() = hitTolerancePx * 2f / viewport.scale.coerceAtLeast(0.01f)

    /** v18 kalem: sıradaki noktanın basıncı (0..1) ve eğimi (0..1); parmakla 1 ve 0. v20: yönü (0..1; 0 bilinmiyor). */
    var pressure: Float = 1f
    var tilt: Float = 0f
    var orient: Float = 0f

    /** v20 sıradaki noktanın zamanı (ms; olay zamanı) — çizim hızı süzgeci için. */
    var eventTime: Long = 0L

    /** v18: canlı çizginin kesinleşen parçaları ayrıca ön tampona (düşük gecikme) gönderilsin mi. */
    var frontBuffer: ((com.karapp.drawguidance.ui.drawing.StrokeChunk) -> Unit)? = null
}

/**
 * Çizim alanındaki bir modun dokunuş davranışı (Strategy). Her hareket için yeni bir araç oluşturulur;
 * hareket boyunca tuttuğu durum (döndürme açısı, kırpma tutamağı…) araçta kalır.
 */
internal abstract class CanvasTool {
    /** Parmak 2 sn hareketsiz basılı kalınca altındaki eleman seçilsin mi. */
    open val longPressSelects: Boolean = false

    /** v20 parmak hareketsizken de düzenli [tick] ister mi (çizim hızı: mürekkep kaleme yetişir). */
    open val needsTicks: Boolean = false

    /** v20 zaman ilerledi (parmak hareketsiz). */
    open fun tick(ctx: GestureContext, time: Long) = Unit

    /** Sürükleme başlarken; false dönerse sürükleme görünümü kaydırır. */
    open fun begin(ctx: GestureContext): Boolean = false

    open fun move(ctx: GestureContext, position: Offset, point: Offset) = Unit

    open fun end(ctx: GestureContext, preview: Preview?) = Unit

    /** İkinci parmak gelince yarım kalan hareket bırakılır. */
    open fun cancel(ctx: GestureContext) {
        ctx.setPreview(null)
    }

    /** Parmak kaymadan kalktı (dokunma). */
    open fun tap(ctx: GestureContext, point: Offset, upTime: Long) = Unit

    /** v37 parmak indi (hareketin başı; [GestureContext.eventTime] iniş zamanı). */
    open fun down(ctx: GestureContext) = Unit

    /** v37 parmak 2 sn hareketsiz basılı kaldı ([longPressSelects]): bir iş yapıldıysa true (hareketin kalanı yutulur). */
    open fun longPress(ctx: GestureContext): Boolean = ctx.actions.onLongPressSelect(ctx.startCanvas, ctx.hitTolerance)

    /** v37 hareket bitti (dokunma, sürükleme ya da iptal; her durumda en son). */
    open fun up(ctx: GestureContext) = Unit
}

/** v43 tuval üstü yardımcı araçlar: damlalık (Çiz ve Seç'te), dikdörtgenle ve kalemle alan seçimi (Seç'te). */
enum class CanvasPick { NONE, EYEDROPPER, RECT_SELECT, LASSO_SELECT }

internal object CanvasTools {
    fun create(mode: EditMode, pick: CanvasPick): CanvasTool = when {
        // v45 damlalık boyamada da (çocuk menüsünde kova ve boya fırçasıyla birlikte)
        pick == CanvasPick.EYEDROPPER && (mode == EditMode.DRAW || mode == EditMode.SELECT || mode == EditMode.PAINT) -> EyedropperTool()
        pick == CanvasPick.RECT_SELECT && mode == EditMode.SELECT -> RectSelectTool()
        pick == CanvasPick.LASSO_SELECT && mode == EditMode.SELECT -> LassoSelectTool()
        else -> create(mode)
    }

    /** v45 boyama modunda boya fırçası seçiliyse ([paintBrush]) fırça aracı (damlalık açıksa damlalık). */
    fun create(mode: EditMode, pick: CanvasPick, paintBrush: Boolean): CanvasTool =
        if (paintBrush && mode == EditMode.PAINT && pick != CanvasPick.EYEDROPPER) PaintBrushTool() else create(mode, pick)

    fun create(mode: EditMode): CanvasTool = when (mode) {
        EditMode.DRAW -> DrawTool()
        EditMode.SELECT -> SelectTool()
        EditMode.ADD -> AddTool()
        EditMode.EDIT_PICK -> EditPickTool()
        EditMode.MOVE -> MoveTool()
        EditMode.ROTATE -> RotateTool()
        EditMode.SCALE -> ScaleTool()
        EditMode.EXPAND -> ExpandTool()
        EditMode.CROP -> CropTool()
        EditMode.SHAPE -> ShapePlaceTool()
        EditMode.DIAGRAM -> DiagramPlaceTool()
        EditMode.TEXT -> TextTool()
        EditMode.SHAPE_EDIT -> ShapeEditTool()
        EditMode.DIAGRAM_EDIT -> DiagramEditTool()
        EditMode.GUIDE -> PanTool()
        EditMode.PERSPECTIVE -> PerspectiveTool()
        EditMode.FORMULA -> BoxPlaceTool(CanvasActions::onFormulaFinished, CanvasActions::onFormulaTap)
        // Resim düzenlemede tuval yalnızca kaydırılır; işlemler alt çubuktan (Kırp, Vektörleştir, Kalem)
        EditMode.IMAGE_EDIT -> PanTool()
        EditMode.PEN -> PenTool()
        EditMode.PAINT -> PaintTool()
    }
}

/**
 * v33 Şekil boyama: dokunma şeklin alanını boyar, sürükleme görünümü kaydırır.
 * v37 çocuk tuvalinde parmağın üstünde boya kovası gezer (sürükleme kovayı taşır; iki parmak görünümü kaydırır):
 * altındaki şekil vurgulanır, parmak kalkınca kovanın altındaki alan boyanır; parmak bir yerde 2 sn basılı kalırsa
 * bütün şekil boyanır.
 */
internal class PaintTool : CanvasTool() {
    private var kids = false
    /** Basılı tutmanın başladığı yer ve zaman (parmak dokunma payından çok kayınca yeniden başlar). */
    private var anchor = Offset.Zero
    private var holdStart = 0L
    private var hold = -1
    private var last = Offset.Zero
    /** Bu basılı tutmada bütün şekil boyandı (parmak kalkınca yeniden boyanmaz). */
    private var done = false

    override val longPressSelects: Boolean get() = true
    override val needsTicks: Boolean get() = kids

    override fun down(ctx: GestureContext) {
        kids = ctx.base.kind == com.karapp.drawguidance.data.model.CanvasKind.KIDS
        if (kids) startHold(ctx, ctx.startCanvas)
    }

    private fun startHold(ctx: GestureContext, point: Offset) {
        anchor = point; last = point; holdStart = ctx.eventTime; done = false
        hold = ++holds
        ctx.actions.onPaintHover(point, hold)
    }

    override fun begin(ctx: GestureContext): Boolean = kids

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        if (!kids) return
        last = point
        if ((point - anchor).getDistance() > ctx.hitTolerance) startHold(ctx, point)
        else if (!done) { ctx.actions.onPaintHover(point, hold); checkHold(ctx, ctx.eventTime) }
    }

    override fun tick(ctx: GestureContext, time: Long) = checkHold(ctx, time)

    private fun checkHold(ctx: GestureContext, time: Long) {
        if (!kids || done || time - holdStart < LONG_PRESS_MS) return
        whole(ctx, last)
    }

    private fun whole(ctx: GestureContext, point: Offset): Boolean {
        done = true
        ctx.actions.onPaintWhole(point)
        ctx.actions.onPaintHover(point, -1)
        return true
    }

    override fun longPress(ctx: GestureContext): Boolean = kids && !done && whole(ctx, ctx.startCanvas)

    override fun end(ctx: GestureContext, preview: Preview?) {
        if (kids && !done) ctx.actions.onPaintTap(last)
    }

    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) {
        if (!done) ctx.actions.onPaintTap(point)
    }

    override fun cancel(ctx: GestureContext) {
        super.cancel(ctx)
        if (kids) ctx.actions.onPaintHover(null, -1)
    }

    override fun up(ctx: GestureContext) {
        if (kids) ctx.actions.onPaintHover(null, -1)
    }

    private companion object {
        /** Basılı tutmaların sırası (imleçteki 2 sn halkası her yeni tutmada baştan dolar). */
        var holds = 0
    }
}

/** Yalnızca kaydırma (kılavuz ayarında). */
internal class PanTool : CanvasTool()

/**
 * Çiz: sürükleme yeni çizim, dokunma nokta; canlı katmanda çizilir. v18: kalemle çizerken noktalar basınç ve eğimle
 * eklenir; [penEraser] kalemin silgi ucu ya da yan tuşu: seçili fırçadan bağımsız silgi.
 */
internal class DrawTool(private val penEraser: Boolean = false) : CanvasTool() {
    override val longPressSelects = true

    /** v20 sabitleme ve çizim hızı süzgeci (fırçada kapalıysa null; silgide yok). */
    private var filter: com.karapp.drawguidance.ui.drawing.InkFilter? = null
    private var lastRaw: com.karapp.drawguidance.ui.drawing.InkFilter.Point? = null

    override val needsTicks: Boolean get() = filter?.needsTicks == true

    /** Süzgeç açık mı (hareket tahmini süzgeçli çizimde gösterilmez). */
    val filtering: Boolean get() = filter != null

    /** v21 3B çizim modunda düzlem (çizgi ilk noktası etrafında düzleme yatırılır). */
    private var plane = com.karapp.drawguidance.data.model.Plane3D.FREE
    private var anchor = Offset.Zero
    /** v22 döndürülebilir sahnede düzleme yatırma (kameraya göre). */
    private var mapping: com.karapp.drawguidance.ui.drawing.Scene3D.DrawMapping? = null

    /** v40 cetvel/kalıp: çizgi parmağın indiği yere yakın kenara kilitlenir (kilit yoksa -1). */
    private var guide: com.karapp.drawguidance.ui.screens.canvasedit.ruler.StrokeGuide? = null
    private var guideLock = -1
    private val snapOut = FloatArray(3)

    private fun add(ctx: GestureContext, x0: Float, y0: Float, pressure: Float, tilt: Float, orient: Float) {
        var x = x0; var y = y0
        val g = guide
        if (g != null && guideLock >= 0) { g.project(guideLock, x0, y0, snapOut); x = snapOut[0]; y = snapOut[1] }
        val q = mapping?.map(x, y) ?: com.karapp.drawguidance.ui.drawing.Iso3D.project(plane, anchor, x, y)
        ctx.live.add(q.x, q.y, pressure, tilt, orient)
    }

    private fun emit(ctx: GestureContext, points: List<com.karapp.drawguidance.ui.drawing.InkFilter.Point>) {
        for (p in points) add(ctx, p.x, p.y, p.pressure, p.tilt, p.orient)
    }

    override fun begin(ctx: GestureContext): Boolean {
        val brush = ctx.brush
        val eraser = penEraser && brush.brushType != com.karapp.drawguidance.data.model.BrushType.ERASER
        val type = if (eraser) com.karapp.drawguidance.data.model.BrushType.ERASER else brush.brushType
        var depth = if (type == com.karapp.drawguidance.data.model.BrushType.ERASER) null else ctx.actions.draw3d()
        plane = depth?.plane ?: com.karapp.drawguidance.data.model.Plane3D.FREE
        anchor = ctx.startCanvas
        val cam = if (depth != null) ctx.actions.scene3d() else null
        mapping = if (depth != null && cam != null) {
            com.karapp.drawguidance.ui.drawing.Scene3D.DrawMapping(
                com.karapp.drawguidance.ui.drawing.Scene3D.effectivePlane(depth.plane, cam), cam, anchor.x, anchor.y,
            ).also { m -> depth = depth!!.copy(plane = m.plane, ex = m.extrude.x, ey = m.extrude.y) }
        } else null
        // v40 cetvel/kalıp kenarına yakın başlayan çizgi kenarı izler (silgi ve 3B çizimde yok)
        guide = if (type == com.karapp.drawguidance.data.model.BrushType.ERASER || depth != null) null else ctx.actions.ruler()?.guide()
        guideLock = guide?.lock(ctx.startCanvas.x, ctx.startCanvas.y, ctx.guideCapture) ?: -1
        val params = if (type == com.karapp.drawguidance.data.model.BrushType.ERASER) null else
            if (depth == null) brush.params else (brush.params ?: com.karapp.drawguidance.data.model.BrushParams()).copy(depth3d = depth)
        ctx.live.start(
            UUID.randomUUID().toString(),
            type,
            hexToColor(brush.colorHex).toArgb(),
            if (eraser) ctx.actions.eraserSize() else brush.sizePx,
            if (eraser) 1f else brush.opacity, if (eraser) 1f else brush.flow, if (eraser) 1f else brush.strength,
            ctx.viewport.scale, ctx.viewport.offset.x, ctx.viewport.offset.y, ctx.viewW, ctx.viewH,
            front = ctx.frontBuffer,
            params = params,
        )
        val tuning = brush.tuning
        filter = if (type == com.karapp.drawguidance.data.model.BrushType.ERASER) null else
            com.karapp.drawguidance.ui.drawing.InkFilter(tuning.stabilizeAmount, tuning.speed, 1f / ctx.viewport.scale).takeIf { it.active }
        val first = com.karapp.drawguidance.ui.drawing.InkFilter.Point(ctx.startCanvas.x, ctx.startCanvas.y, ctx.pressure, ctx.tilt, ctx.orient)
        lastRaw = first
        val f = filter
        if (f != null) emit(ctx, f.start(first, ctx.eventTime))
        else add(ctx, first.x, first.y, first.pressure, first.tilt, first.orient)
        ctx.setFinger(ctx.start)
        ctx.refreshLive()
        return true
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        val p = com.karapp.drawguidance.ui.drawing.InkFilter.Point(point.x, point.y, ctx.pressure, ctx.tilt, ctx.orient)
        lastRaw = p
        val f = filter
        if (f != null) emit(ctx, f.move(p, ctx.eventTime)) else add(ctx, p.x, p.y, p.pressure, p.tilt, p.orient)
        ctx.setFinger(position)
        ctx.refreshLive()
    }

    override fun tick(ctx: GestureContext, time: Long) {
        val f = filter ?: return
        val out = f.tick(time)
        if (out.isEmpty()) return
        emit(ctx, out)
        ctx.refreshLive()
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        val id = ctx.live.strokeId
        // Süzgeçte kalan kısım (uç parmağın bıraktığı yere yetişir)
        filter?.let { emit(ctx, it.finish(lastRaw, ctx.eventTime)) }
        ctx.live.finish()
        if (id != null) {
            if (penEraser) ctx.actions.onPenEraserFinished(ctx.live.points(), id)
            else ctx.actions.onStrokeFinished(ctx.live.points(), id)
        }
        ctx.setFinger(null)
        ctx.refreshLive()
    }

    override fun cancel(ctx: GestureContext) {
        ctx.live.release()
        ctx.setFinger(null)
        ctx.setPreview(null)
        ctx.refreshLive()
    }

    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) {
        var p = point
        val g = if (ctx.brush.brushType == com.karapp.drawguidance.data.model.BrushType.ERASER) null else ctx.actions.ruler()?.guide()
        val lock = g?.lock(point.x, point.y, ctx.guideCapture) ?: -1
        if (g != null && lock >= 0) { g.project(lock, point.x, point.y, snapOut); p = Offset(snapOut[0], snapOut[1]) }
        ctx.actions.onStrokeFinished(listOf(StrokePoint(p.x, p.y)), UUID.randomUUID().toString())
    }
}

/**
 * v17 Seç (eski İncele ile birleşik): elemana dokunmak seçer (seçime ekler), boş yere dokunmak seçimi kaldırır; seçili
 * elemana çift dokunmak onu seçimden çıkarır, boş yere çift dokunmak kağıdı sığdırır. Sürükleme kaydırır.
 */
internal class SelectTool : CanvasTool() {
    override val longPressSelects = true

    /** v27 sürüklenen balon kuyruğu (balon ve kuyruk sırası). */
    private var tail: Pair<com.karapp.drawguidance.data.model.Stroke, Int>? = null
    /** v40 basit çizimde yazı kutusu tutamağı (kutu boyutu). */
    private var box: BoxResizeGesture? = null
    /** v43 basit çizimde şekil/resim köşe tutamağı. */
    private var corner: CornerResizeGesture? = null

    override fun begin(ctx: GestureContext): Boolean {
        tail = balloonTailAt(ctx)
        box = if (tail == null) simpleTextBoxHandleAt(ctx) else null
        corner = if (tail == null && box == null) simpleCornerAt(ctx) else null
        return tail != null || box != null || corner != null
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        box?.let { b -> SimpleTextResize.preview(ctx, b, point); return }
        corner?.let { c -> SimpleElementResize.preview(ctx, c, point); return }
        val (s, i) = tail ?: return
        com.karapp.drawguidance.ui.drawing.BalloonTails.moved(s, i, point)?.let { ctx.setPreview(Preview.ShapeEdit(it)) }
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        if (tail == null && box == null && corner == null) return
        tail = null; box = null; corner = null
        (preview as? Preview.ShapeEdit)?.let { ctx.actions.onReplaceStroke(it.stroke) }
        ctx.setPreview(null)
    }

    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) {
        val double = ctx.taps.isDouble(upTime, ctx.start, ctx.handleTolerance)
        if (ctx.actions.onSlotTap(point)) return
        if (double) {
            if (!ctx.actions.onDeselectAt(point, ctx.hitTolerance)) ctx.actions.onFit()
        } else if (!ctx.actions.onSelectAt(point, ctx.hitTolerance)) {
            // v41 boş yere dokunma: seçim yoksa arka plan seçilir, varsa seçim kalkar
            if (!ctx.actions.onEmptyTap(point)) ctx.actions.onClearSelection()
        }
    }
}

/** v17 Ekle: dokunulan yerde "buraya ekle" menüsü açılır (yazı, şekil, formül…); sürükleme kaydırır. */
internal class AddTool : CanvasTool() {
    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) = ctx.actions.onAddTap(ctx.start, point)
}

/** v17 Düzenle: dokunulan elemanın düzenleyicisi açılır; sürükleme kaydırır. */
internal class EditPickTool : CanvasTool() {
    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) {
        ctx.actions.onEditAt(point, ctx.hitTolerance)
    }
}

/** v43 damlalık: dokunulan (ya da parmağın kalktığı) yerin rengi fırça rengi olur; çizim yapılmaz. */
internal class EyedropperTool : CanvasTool() {
    private var last = Offset.Zero
    override fun begin(ctx: GestureContext): Boolean { last = ctx.startCanvas; ctx.setFinger(ctx.start); return true }
    override fun move(ctx: GestureContext, position: Offset, point: Offset) { last = point; ctx.setFinger(position) }
    override fun end(ctx: GestureContext, preview: Preview?) { ctx.setFinger(null); ctx.actions.onEyedrop(last) }
    override fun cancel(ctx: GestureContext) = ctx.setFinger(null)
    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) = ctx.actions.onEyedrop(point)
}

/** v43 dikdörtgenle seçim: sürüklenen kutunun içindeki elemanlar seçilir (çizim yapılmaz); dokunma her zamanki seçim. */
internal class RectSelectTool : CanvasTool() {
    private var last = Offset.Zero
    override fun begin(ctx: GestureContext): Boolean { last = ctx.startCanvas; return true }
    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        last = point
        ctx.setPreview(Preview.Box(ctx.startCanvas, point))
    }
    override fun end(ctx: GestureContext, preview: Preview?) {
        ctx.setPreview(null)
        ctx.actions.onRectSelect(ctx.startCanvas, last)
    }
    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) {
        if (!ctx.actions.onSelectAt(point, ctx.hitTolerance)) ctx.actions.onClearSelection()
    }
}

/** v43 kalemle alan seçimi: çizilen kapalı çizginin içindeki elemanlar seçilir (çizgi tuvale eklenmez). */
internal class LassoSelectTool : CanvasTool() {
    private val points = ArrayList<Offset>()
    override fun begin(ctx: GestureContext): Boolean { points.clear(); points += ctx.startCanvas; return true }
    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        if ((points.last() - point).getDistance() * ctx.viewport.scale >= 3f) points += point
        ctx.setPreview(Preview.Lasso(points.toList()))
    }
    override fun end(ctx: GestureContext, preview: Preview?) {
        ctx.setPreview(null)
        ctx.actions.onLassoSelect(points.toList())
        points.clear()
    }
    override fun cancel(ctx: GestureContext) { points.clear(); ctx.setPreview(null) }
    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) {
        if (!ctx.actions.onSelectAt(point, ctx.hitTolerance)) ctx.actions.onClearSelection()
    }
}

/** Yazı: dokunulan yere yazı ekler (yazıya dokunulduysa düzenler). */
/**
 * Yazı: dokunulan yere yazılır (yazıya dokunulursa düzenlenir). v37 sürüklenerek kutu çizilir: yazı kutunun yerinde ve
 * eninde (paragraf) yazılır. Yazı açıkken sürükleme görünümü kaydırır.
 */
internal class TextTool : CanvasTool() {
    private var last = Offset.Zero

    override fun begin(ctx: GestureContext): Boolean {
        if (ctx.actions.textDrafting()) return false
        last = ctx.startCanvas
        return true
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        last = point
        ctx.setPreview(Preview.Box(ctx.startCanvas, point))
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        ctx.setPreview(null)
        ctx.actions.onTextBox(ctx.startCanvas, last, ctx.hitTolerance)
    }

    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) = ctx.actions.onTextTap(point, ctx.hitTolerance)
}

/** Taşı/Döndür/Ölçekle ortak: seçim yokken dokunulan (ya da üstünden sürüklenen) eleman seçilir. */
internal abstract class TransformTool : CanvasTool() {
    override val longPressSelects = true

    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) {
        // Seçiliyken dokunulan yer formül yuvasıysa (ör. eklenen formülün katsayısı) formül üstünde düzenlenir
        if (ctx.selected.isEmpty()) ctx.actions.onPickAt(point, ctx.hitTolerance) else ctx.actions.onSlotTap(point)
    }

    protected fun preview(ctx: GestureContext, p: Preview) = ctx.setPreview(p)
}

/** v27 tek balon seçiliyken parmak bir kuyruk ucuna indi mi. */
internal fun balloonTailAt(ctx: GestureContext): Pair<com.karapp.drawguidance.data.model.Stroke, Int>? {
    if (ctx.selected.size != 1) return null
    val s = ctx.base.strokes.firstOrNull { it.id in ctx.selected } ?: return null
    if (s.text?.box?.balloon == null) return null
    val pos = com.karapp.drawguidance.ui.drawing.BalloonTails.positions(s)
    val i = pos.indices.minByOrNull { (ctx.viewport.toScreen(pos[it]) - ctx.start).getDistance() } ?: return null
    if ((ctx.viewport.toScreen(pos[i]) - ctx.start).getDistance() > ctx.handleTolerance * 1.3f) return null
    return s to i
}

/** v23 yazı kutusu tutamağından başlayan sürükleme. */
internal class BoxResizeGesture(val stroke: com.karapp.drawguidance.data.model.Stroke, val handle: Int)

/** Tek yazı seçiliyken parmak kutu tutamaklarından birine indi mi. */
internal fun textBoxHandleAt(ctx: GestureContext): BoxResizeGesture? {
    if (ctx.selected.size != 1) return null
    val s = ctx.base.strokes.firstOrNull { it.id in ctx.selected } ?: return null
    if (s.brushType != com.karapp.drawguidance.data.model.BrushType.TEXT) return null
    val h = com.karapp.drawguidance.ui.drawing.TextBoxResize.handles(s)
        .minByOrNull { (ctx.viewport.toScreen(it.first) - ctx.start).getDistance() } ?: return null
    if ((ctx.viewport.toScreen(h.first) - ctx.start).getDistance() > ctx.handleTolerance) return null
    return BoxResizeGesture(s, h.second)
}

/** v27 Taşı yalnız taşır: yazı kutusu tutamakları burada yok (kutu boyutu Düzenle → Ölçekle'de). */
internal class MoveTool : TransformTool() {
    /** v40 basit çizimde yazı kutusu tutamağı (Taşı'da da kutu boyutlanır). */
    private var box: BoxResizeGesture? = null
    /** v43 basit çizimde şekil/resim köşe tutamağı (Taşı'da da boyutlanır). */
    private var corner: CornerResizeGesture? = null

    override fun begin(ctx: GestureContext): Boolean {
        box = simpleTextBoxHandleAt(ctx)
        corner = if (box == null) simpleCornerAt(ctx) else null
        if (box != null || corner != null) return true
        val selection = ElementOps.selectionBounds(ctx.base.strokes, ctx.selected)
        // Seçim yokken bir elemanın üstünden sürüklenirse önce o eleman seçilir ve taşınır
        return selection != null || ctx.actions.onPickAt(ctx.startCanvas, ctx.hitTolerance)
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        box?.let { b -> SimpleTextResize.preview(ctx, b, point); return }
        corner?.let { c -> SimpleElementResize.preview(ctx, c, point); return }
        preview(ctx, Preview.Move(point - ctx.startCanvas))
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        if (box != null || corner != null) {
            box = null; corner = null
            (preview as? Preview.ShapeEdit)?.let { ctx.actions.onReplaceStroke(it.stroke) }
            ctx.setPreview(null)
            return
        }
        (preview as? Preview.Move)?.let { ctx.actions.onMove(it.delta.x, it.delta.y) }
    }
}

internal class RotateTool : TransformTool() {
    private var pivot = Offset.Zero
    private var lastAngle = 0f
    private var total = 0f

    override fun begin(ctx: GestureContext): Boolean {
        val selection = ElementOps.selectionBounds(ctx.base.strokes, ctx.selected) ?: return false
        pivot = selection.center
        val p = ctx.viewport.toScreen(pivot)
        lastAngle = atan2(ctx.start.y - p.y, ctx.start.x - p.x)
        total = 0f
        return true
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        val pivotScreen = ctx.viewport.toScreen(pivot)
        val angle = atan2(position.y - pivotScreen.y, position.x - pivotScreen.x)
        var step = angle - lastAngle
        if (step > PI) step -= (2 * PI).toFloat()
        if (step < -PI) step += (2 * PI).toFloat()
        total += step
        lastAngle = angle
        preview(ctx, Preview.Rotate(snapAngle(Math.toDegrees(total.toDouble()).toFloat()), pivot))
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        (preview as? Preview.Rotate)?.let { ctx.actions.onRotate(it.degrees, it.pivot) }
    }
}

internal class ScaleTool : TransformTool() {
    private var pivot = Offset.Zero
    private var handle = ScaleHandle.CENTER
    /** v27 tek yazı seçiliyken kutu tutamağı (sağ kenar en, alt kenar boy, köşe ikisi): yazı kutusu boyutlanır. */
    private var box: BoxResizeGesture? = null

    override fun begin(ctx: GestureContext): Boolean {
        box = null
        tail = balloonTailAt(ctx)
        if (tail != null) return true
        textBoxHandleAt(ctx)?.let { box = it; return true }
        val selection = ElementOps.selectionBounds(ctx.base.strokes, ctx.selected) ?: return false
        if (isSingleText(ctx)) {
            // Yazı kutusunda kare tutamaklar yok: kutu dışında sürükleme yazıyı orantılı büyütür
            handle = ScaleHandle.CENTER
            pivot = selection.center
            return (ctx.startCanvas - pivot).getDistance() * ctx.viewport.scale >= 8f
        }
        val box = ctx.viewport.toScreen(selection)
        val handles = listOf(
            Triple(box.topLeft, ScaleHandle.CORNER, selection.bottomRight),
            Triple(box.topRight, ScaleHandle.CORNER, selection.bottomLeft),
            Triple(box.bottomLeft, ScaleHandle.CORNER, selection.topRight),
            Triple(box.bottomRight, ScaleHandle.CORNER, selection.topLeft),
            Triple(box.centerLeft, ScaleHandle.HORIZONTAL, selection.centerRight),
            Triple(box.centerRight, ScaleHandle.HORIZONTAL, selection.centerLeft),
            Triple(box.topCenter, ScaleHandle.VERTICAL, selection.bottomCenter),
            Triple(box.bottomCenter, ScaleHandle.VERTICAL, selection.topCenter),
        )
        val nearest = handles.minByOrNull { (it.first - ctx.start).getDistance() }
        if (nearest != null && (nearest.first - ctx.start).getDistance() <= ctx.handleTolerance) {
            handle = nearest.second
            pivot = nearest.third
        } else {
            handle = ScaleHandle.CENTER
            pivot = selection.center
        }
        return (ctx.startCanvas - pivot).getDistance() * ctx.viewport.scale >= 8f
    }

    private var tail: Pair<com.karapp.drawguidance.data.model.Stroke, Int>? = null

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        tail?.let { (s, i) -> com.karapp.drawguidance.ui.drawing.BalloonTails.moved(s, i, point)?.let { preview(ctx, Preview.ShapeEdit(it)) }; return }
        val b = box
        if (b != null) {
            com.karapp.drawguidance.ui.drawing.TextBoxResize.resize(b.stroke, b.handle, point)?.let { preview(ctx, Preview.ShapeEdit(it)) }
            return
        }
        val (sx, sy) = scaleFactors(handle, pivot, ctx.startCanvas, point)
        preview(ctx, Preview.Scale(sx, sy, pivot))
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        if (box != null || tail != null) {
            box = null; tail = null
            (preview as? Preview.ShapeEdit)?.let { ctx.actions.onReplaceStroke(it.stroke) }
            ctx.setPreview(null)
            return
        }
        (preview as? Preview.Scale)?.let { ctx.actions.onScale(it.sx, it.sy, it.pivot) }
    }

    private fun isSingleText(ctx: GestureContext): Boolean =
        ctx.selected.size == 1 && ctx.base.strokes.any { it.id in ctx.selected && it.brushType == com.karapp.drawguidance.data.model.BrushType.TEXT }
}

/** Genişlet: kağıdın kenarları/köşeleri sürüklenir. */
/** Ekrandaki [point] kağıdın hangi kenarlarına [tolerance] kadar yakın (sol, üst, sağ, alt). */
internal fun paperEdgesAt(viewport: ViewportState, width: Int, height: Int, point: Offset, tolerance: Float): BooleanArray {
    val paper = viewport.toScreen(Rect(0f, 0f, width.toFloat(), height.toFloat()))
    val inY = point.y in (paper.top - tolerance)..(paper.bottom + tolerance)
    val inX = point.x in (paper.left - tolerance)..(paper.right + tolerance)
    return booleanArrayOf(
        inY && abs(point.x - paper.left) <= tolerance,
        inX && abs(point.y - paper.top) <= tolerance,
        inY && abs(point.x - paper.right) <= tolerance,
        inX && abs(point.y - paper.bottom) <= tolerance,
    )
}

internal class ExpandTool : CanvasTool() {
    private var edges = BooleanArray(4) // sol, üst, sağ, alt

    override fun begin(ctx: GestureContext): Boolean {
        edges = paperEdgesAt(ctx.viewport, ctx.base.width, ctx.base.height, ctx.start, ctx.edgeTolerance)
        return edges.any { it }
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        val d = point - ctx.startCanvas
        val w = ctx.base.width.toFloat()
        val h = ctx.base.height.toFloat()
        val minSize = CanvasEditViewModel.MIN_CANVAS.toFloat()
        val maxSize = CanvasEditViewModel.MAX_CANVAS.toFloat()
        var left = if (edges[0]) d.x else 0f
        var top = if (edges[1]) d.y else 0f
        var right = if (edges[2]) w + d.x else w
        var bottom = if (edges[3]) h + d.y else h
        if (edges[0]) left = left.coerceIn(right - maxSize, right - minSize)
        if (edges[2]) right = right.coerceIn(left + minSize, left + maxSize)
        if (edges[1]) top = top.coerceIn(bottom - maxSize, bottom - minSize)
        if (edges[3]) bottom = bottom.coerceIn(top + minSize, top + maxSize)
        ctx.setPreview(Preview.Expand(Rect(left.roundToInt().toFloat(), top.roundToInt().toFloat(), right.roundToInt().toFloat(), bottom.roundToInt().toFloat())))
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        (preview as? Preview.Expand)?.let { e ->
            val left = e.rect.left.roundToInt()
            val top = e.rect.top.roundToInt()
            ctx.viewport.shiftForResize(left.toFloat(), top.toFloat())
            ctx.actions.onResize(left, top, e.rect.right.roundToInt(), e.rect.bottom.roundToInt())
        }
    }
}

/** Kırp: çerçevenin kenarları sürüklenir ya da içinden tutularak taşınır. */
internal class CropTool : CanvasTool() {
    private val handles = HashSet<CropHandle>()
    private var startRect = Rect.Zero

    override fun begin(ctx: GestureContext): Boolean {
        startRect = ctx.crop ?: Rect(0f, 0f, ctx.base.width.toFloat(), ctx.base.height.toFloat())
        val box = ctx.viewport.toScreen(startRect)
        val start = ctx.start
        val t = ctx.edgeTolerance
        val inY = start.y in (box.top - t)..(box.bottom + t)
        val inX = start.x in (box.left - t)..(box.right + t)
        if (inY && abs(start.x - box.left) <= t) handles += CropHandle.LEFT
        if (inX && abs(start.y - box.top) <= t) handles += CropHandle.TOP
        if (inY && abs(start.x - box.right) <= t) handles += CropHandle.RIGHT
        if (inX && abs(start.y - box.bottom) <= t) handles += CropHandle.BOTTOM
        if (handles.isEmpty() && box.contains(start)) handles += CropHandle.MOVE
        return handles.isNotEmpty()
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        val d = point - ctx.startCanvas
        val w = ctx.base.width.toFloat()
        val h = ctx.base.height.toFloat()
        val r = startRect
        val next = if (CropHandle.MOVE in handles) {
            r.translate(d.x.coerceIn(-r.left, w - r.right), d.y.coerceIn(-r.top, h - r.bottom))
        } else {
            Rect(
                if (CropHandle.LEFT in handles) r.left + d.x else r.left,
                if (CropHandle.TOP in handles) r.top + d.y else r.top,
                if (CropHandle.RIGHT in handles) r.right + d.x else r.right,
                if (CropHandle.BOTTOM in handles) r.bottom + d.y else r.bottom,
            )
        }
        ctx.actions.onCropChange(next)
    }
}

/** Şekil ekleme: sürükleyerek kutuya, dokunarak varsayılan boyutta. */
internal class ShapePlaceTool : CanvasTool() {
    private val gestureId = UUID.randomUUID().toString()
    private var last = Offset.Zero

    override fun begin(ctx: GestureContext): Boolean {
        last = ctx.startCanvas
        return true
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        last = point
        ctx.setPreview(Preview.Shape(gestureId, ctx.startCanvas, point))
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        ctx.setPreview(null)
        ctx.actions.onShapeFinished(gestureId, ctx.startCanvas, last)
    }

    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) = ctx.actions.onShapeTap(UUID.randomUUID().toString(), point)
}

/**
 * Kutuya yerleştirme (formül): sürüklenen kutuya ya da dokunulan yere; sürüklerken kesik çerçeve.
 * Yerleştirilen eleman seçili olarak Taşı moduna geçilir (mod politikası).
 */
internal class BoxPlaceTool(
    private val finished: (CanvasActions) -> (String, Offset, Offset) -> Unit,
    private val tapped: (CanvasActions) -> (String, Offset) -> Unit,
) : CanvasTool() {
    private val gestureId = UUID.randomUUID().toString()
    private var last = Offset.Zero

    override fun begin(ctx: GestureContext): Boolean {
        last = ctx.startCanvas
        return true
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        last = point
        ctx.setPreview(Preview.Box(ctx.startCanvas, point))
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        ctx.setPreview(null)
        finished(ctx.actions)(gestureId, ctx.startCanvas, last)
    }

    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) = tapped(ctx.actions)(UUID.randomUUID().toString(), point)
}

/**
 * Kalem aracı: dokunma köşe noktası ekler (ilk noktaya dokunmak yolu kapatır), sürükleme eğri noktası ekler
 * (sürükleme yönü eğrinin teğeti).
 */
internal class PenTool : CanvasTool() {
    private var last = Offset.Zero
    /** v43 var olan nokta ya da tutamak sürükleniyor (yeni nokta eklenmez). */
    private var editing = false

    override fun begin(ctx: GestureContext): Boolean {
        last = ctx.startCanvas
        editing = ctx.actions.onPenGrab(ctx.startCanvas, ctx.hitTolerance)
        return true
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        last = point
        if (editing) ctx.actions.onPenEditDrag(point, false) else ctx.actions.onPenDrag(ctx.startCanvas, point, false)
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        if (editing) { editing = false; ctx.actions.onPenEditDrag(last, true) } else ctx.actions.onPenDrag(ctx.startCanvas, last, true)
    }

    override fun cancel(ctx: GestureContext) {
        ctx.setPreview(null)
        if (editing) { editing = false; ctx.actions.onPenEditDrag(ctx.startCanvas, true); return }
        ctx.actions.onPenDrag(ctx.startCanvas, ctx.startCanvas, false)
    }

    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) = ctx.actions.onPenTap(point, ctx.hitTolerance)
}

/** Diyagram yerleştirme: sürüklenen kutuya ya da dokunulan yere. */
internal class DiagramPlaceTool : CanvasTool() {
    private val gestureId = UUID.randomUUID().toString()
    private var last = Offset.Zero

    override fun begin(ctx: GestureContext): Boolean {
        last = ctx.startCanvas
        return true
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        last = point
        ctx.setPreview(Preview.Box(ctx.startCanvas, point))
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        ctx.setPreview(null)
        ctx.actions.onDiagramFinished(gestureId, ctx.startCanvas, last)
    }

    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) = ctx.actions.onDiagramTap(UUID.randomUUID().toString(), point)
}

/**
 * Şekil düzenleme (v16 vektör): dokunma nokta/çizgi seçer, nokta ekler ya da noktalar arasına çizgi çizer; sürükleme
 * noktaları taşır, noktadan çizgi çeker, seçili çizgiyi taşır, ortasındaki tutamaktan büker, ucundaki tutamaktan
 * döndürür. Noktaya/çizgiye değmeyen sürükleme görünümü kaydırır.
 */
internal class ShapeEditTool : CanvasTool() {
    private var kind = com.karapp.drawguidance.ui.screens.canvasedit.ShapeDrag.NONE
    private var last = Offset.Zero
    private val path = ArrayList<Offset>()

    override fun begin(ctx: GestureContext): Boolean {
        last = ctx.startCanvas
        path.clear()
        path += ctx.startCanvas
        kind = ctx.actions.onShapeDragBegin(ctx.startCanvas, ctx.hitTolerance, ctx.handleTolerance * 1.3f / ctx.viewport.scale)
        return kind != com.karapp.drawguidance.ui.screens.canvasedit.ShapeDrag.NONE
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        last = point
        if ((path.last() - point).getDistance() > ctx.hitTolerance * 0.15f) path += point
        val preview = ctx.actions.onShapeDragPreview(kind, ctx.startCanvas, point, path, ctx.hitTolerance)
        ctx.setPreview(preview?.let { Preview.ShapeEdit(it) })
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        ctx.setPreview(null)
        if (path.last() != last) path += last
        ctx.actions.onShapeDragEnd(kind, ctx.startCanvas, last, path, ctx.hitTolerance)
    }

    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) = ctx.actions.onShapeEditTap(point, ctx.hitTolerance)
}

/**
 * Tuval üstünde diyagram düzenleme: dokunma adım/ok seçer (ve ok ekler); odaktaki adımın tutamağından
 * sürüklemek ok çeker (bırakılan adımın en yakın tarafına bağlanır); başka yerden sürüklemek kaydırır.
 */
internal class DiagramEditTool : CanvasTool() {
    private var last = Offset.Zero

    override fun begin(ctx: GestureContext): Boolean {
        last = ctx.startCanvas
        return ctx.actions.onDiagramHasHandle(ctx.startCanvas, ctx.hitTolerance)
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        last = point
        ctx.setPreview(Preview.Guideline(ctx.startCanvas, point))
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        ctx.setPreview(null)
        ctx.actions.onDiagramLinkDrag(ctx.startCanvas, last, ctx.hitTolerance)
    }

    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) = ctx.actions.onDiagramEditTap(point, ctx.hitTolerance)
}

/** Perspektif: kaçış noktasının yakınından başlayan sürükleme onu taşır; değilse kaydırılır. */
internal class PerspectiveTool : CanvasTool() {
    private var index = -1
    private var last = Offset.Zero

    override fun begin(ctx: GestureContext): Boolean {
        val base = ctx.base
        val paperScreen = ctx.viewport.toScreen(Rect(0f, 0f, base.width.toFloat(), base.height.toFloat()))
        val vps = GuideRenderer.vanishingPoints(base.guide, paperScreen)
        val nearest = vps.indices.minByOrNull { (vps[it] - ctx.start).getDistance() }
        index = if (nearest != null && (vps[nearest] - ctx.start).getDistance() <= ctx.handleTolerance * 1.6f) nearest else -1
        last = ctx.startCanvas
        return index >= 0
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        last = point
        ctx.actions.onVanishMove(index, point.x / ctx.base.width, point.y / ctx.base.height, false)
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        ctx.actions.onVanishMove(index, last.x / ctx.base.width, last.y / ctx.base.height, true)
    }
}

private const val DOUBLE_TAP_MS = 320L

/** 15°'nin katlarına yakın açıları o kata yapıştırır. */
internal fun snapAngle(degrees: Float): Float {
    val snapped = (degrees / 15f).roundToInt() * 15f
    return if (abs(degrees - snapped) <= 2.5f) snapped else degrees
}

internal fun scaleFactors(handle: ScaleHandle, pivot: Offset, start: Offset, current: Offset): Pair<Float, Float> {
    fun ratio(now: Float, begin: Float): Float = if (abs(begin) < 1e-3f) 1f else (now / begin)
    return when (handle) {
        ScaleHandle.CENTER, ScaleHandle.CORNER -> {
            val begin = (start - pivot).getDistance()
            val f = if (begin < 1e-3f) 1f else (current - pivot).getDistance() / begin
            val c = f.coerceIn(ElementOps.MIN_SCALE, ElementOps.MAX_SCALE)
            c to c
        }
        ScaleHandle.HORIZONTAL -> ratio(current.x - pivot.x, start.x - pivot.x).coerceIn(ElementOps.MIN_SCALE, ElementOps.MAX_SCALE) to 1f
        ScaleHandle.VERTICAL -> 1f to ratio(current.y - pivot.y, start.y - pivot.y).coerceIn(ElementOps.MIN_SCALE, ElementOps.MAX_SCALE)
    }
}
