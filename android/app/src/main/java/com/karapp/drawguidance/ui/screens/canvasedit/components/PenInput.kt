package com.karapp.drawguidance.ui.screens.canvasedit.components

import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.view.SurfaceView
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.viewinterop.AndroidView
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.platform.StrokePredictor
import com.karapp.drawguidance.platform.Stylus
import com.karapp.drawguidance.ui.drawing.FrontBufferInk
import com.karapp.drawguidance.ui.drawing.LiveStrokeLayer
import com.karapp.drawguidance.ui.screens.canvasedit.BrushSettings
import com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState

/** Kalem ya da kalemin silgi ucu. */
internal val PointerInputChange.isPen: Boolean
    get() = type == PointerType.Stylus || type == PointerType.Eraser

/** v18: bir çizim ekranındaki kalem durumu (hareketler arasında). */
internal class PenSession {
    /** Kalemin en son havada görüldüğü an (ms). */
    var lastHover: Long = Long.MIN_VALUE / 2
    /** Ön tamponun gecikmeli temizliği (yeni çizgi başlarsa hemen yapılır). */
    var pendingClear: Runnable? = null
    var predictor: StrokePredictor? = null
}

/**
 * v18 düşük gecikmeli çizimin yüzeyi (Android 10+): tuvalin altında durur (dokunuşları almaz), yüzeyi pencerenin
 * üstünde çizilir; yalnızca çizilmekte olan çizgi görünür, geri kalanı saydamdır.
 */
@Composable
internal fun FrontBufferSurface(inkState: MutableState<Any?>) {
    if (Build.VERSION.SDK_INT < 29) return
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                setZOrderOnTop(true)
                holder.setFormat(PixelFormat.TRANSLUCENT)
                isClickable = false
                isFocusable = false
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            if (inkState.value == null && Build.VERSION.SDK_INT >= 29) {
                inkState.value = runCatching { FrontBufferInk(view) }.getOrNull()
            }
        },
    )
    DisposableEffect(Unit) {
        onDispose {
            if (Build.VERSION.SDK_INT >= 29) (inkState.value as? FrontBufferInk)?.release()
            inkState.value = null
        }
    }
}

/**
 * Tek bir kalem çizgisi: kalem kalkana (ya da sistem dokunuşu avuç sayıp iptal edene) kadar. Silgi ucu ya da yan tuş
 * (ayar açıksa) silgidir. Diğer parmaklar yok sayılır.
 */
internal suspend fun AwaitPointerEventScope.penStroke(
    first: PointerInputChange,
    firstEvent: PointerEvent,
    hostView: View,
    base: CanvasUi,
    selected: Set<String>,
    actions: CanvasActions,
    brush: BrushSettings,
    crop: Rect?,
    shapeTool: ShapeTool,
    live: LiveStrokeLayer,
    viewW: Int,
    viewH: Int,
    hitTolerancePx: Float,
    taps: TapMemory,
    pen: PenSession,
    frontInk: Any?,
    setPreview: (Preview?) -> Unit,
    refreshLive: () -> Unit,
    predicted: MutableState<List<Offset>>,
    viewport: ViewportState,
) {
    val raw0 = firstEvent.motionEvent
    val idx0 = raw0?.let { Stylus.stylusIndex(it) } ?: -1
    val eraserTip = first.type == PointerType.Eraser
    // v21: kalem tanıma kapalıyken kalem parmak gibi çizer (basınç, eğim, yön ve yan tuş kullanılmaz)
    val penOn = Stylus.penFeatures
    val button = penOn && Stylus.sideButtonErases.value && raw0 != null && Stylus.sideButton(raw0)
    val usePressure = penOn && Stylus.pressure.value
    val useTilt = penOn && Stylus.tilt.value
    val tool = DrawTool(penEraser = eraserTip || button)
    val ctx = GestureContext(
        viewport = viewport, base = base, selected = selected, actions = actions, brush = brush, crop = crop,
        shapeTool = shapeTool, live = live, viewW = viewW, viewH = viewH, start = first.position,
        startCanvas = viewport.toCanvas(first.position), handleTolerance = 0f, edgeTolerance = 0f,
        hitTolerancePx = hitTolerancePx, taps = taps, setPreview = setPreview, setFinger = {}, refreshLive = refreshLive,
    )

    fun dynamics(raw: android.view.MotionEvent?, idx: Int, history: Int, fallbackPressure: Float) {
        ctx.pressure = when {
            !usePressure -> 1f
            raw != null && idx >= 0 -> Stylus.pressureOf(raw, idx, history)
            else -> fallbackPressure.coerceIn(0f, 1f)
        }
        ctx.tilt = if (useTilt && raw != null && idx >= 0) Stylus.tiltOf(raw, idx, history) else 0f
        ctx.orient = if (useTilt && raw != null && idx >= 0) Stylus.orientOf(raw, idx, history) else 0f
    }

    // Düşük gecikme: kesinleşen parçalar ön tampona da (önceki çizginin bekleyen temizliği hemen yapılır)
    val ink = if (Build.VERSION.SDK_INT >= 29) (frontInk as? FrontBufferInk)?.takeIf { !it.failed } else null
    if (Build.VERSION.SDK_INT >= 29 && ink != null) {
        pen.pendingClear?.let { hostView.removeCallbacks(it); ink.clear() }
        pen.pendingClear = null
        val paper = viewport.toScreen(Rect(0f, 0f, base.width.toFloat(), base.height.toFloat()))
        val clip = RectF(paper.left, paper.top, paper.right, paper.bottom)
        val s = viewport.scale
        val ox = viewport.offset.x
        val oy = viewport.offset.y
        ctx.frontBuffer = { chunk -> ink.draw(FrontBufferInk.Item(chunk, s, ox, oy, clip)) }
    }
    fun scheduleClear() {
        if (Build.VERSION.SDK_INT < 29 || ink == null) return
        // Tuvalin kendi katmanı çizgiyi gösterene kadar (birkaç kare) ön tampon durur
        val r = Runnable { ink.clear(); pen.pendingClear = null }
        pen.pendingClear = r
        hostView.postDelayed(r, 90L)
    }

    // Kalem olaylarının tamponlanmadan (her örnekte) gelmesi: daha az gecikme
    if (raw0 != null) runCatching { hostView.requestUnbufferedDispatch(raw0) }

    val predictor = if (Stylus.prediction.value) {
        (pen.predictor ?: StrokePredictor(hostView.context).also { pen.predictor = it }).also { it.reset() }
    } else null
    predictor?.record(raw0)
    predictor?.add(first.position.x, first.position.y, first.uptimeMillis)

    dynamics(raw0, idx0, -1, first.pressure)
    ctx.eventTime = first.uptimeMillis
    tool.begin(ctx)
    first.consume()
    // v20 çizim hızı: kalem dururken de mürekkep yetişsin diye kısa aralıklarla zaman ilerletilir
    var clock = first.uptimeMillis
    try {
        while (true) {
            val event = if (tool.needsTicks) {
                withTimeoutOrNull(TICK_MS) { awaitPointerEvent() } ?: run {
                    clock += TICK_MS
                    tool.tick(ctx, clock)
                    null
                }
            } else awaitPointerEvent()
            if (event == null) continue
            event.changes.firstOrNull()?.let { clock = maxOf(clock, it.uptimeMillis) }
            val raw = event.motionEvent
            val change = event.changes.firstOrNull { it.id == first.id }
            // Tüketmeden önce: tüketilen değişiklikte positionChanged() hep false döner
            val moved = change != null && change.pressed && change.position != change.previousPosition
            // Avuç ve diğer parmaklar yok sayılır
            event.changes.forEach { it.consume() }
            val lifted = change == null || !change.pressed
            // ACTION_CANCEL'ı Compose, ham MotionEvent'i olmayan yapay bir "kalkış" olayı olarak iletir;
            // API 33+ avuç iptali ise FLAG_CANCELED ile gelir
            if (lifted && (raw == null || Stylus.isCanceled(raw))) {
                // Sistem dokunuşu avuç sayıp iptal etti: çizgi kaydedilmez
                tool.cancel(ctx)
                predicted.value = emptyList()
                if (Build.VERSION.SDK_INT >= 29) ink?.clear()
                return
            }
            if (lifted) {
                ctx.eventTime = change?.uptimeMillis ?: clock
                tool.end(ctx, null)
                predicted.value = emptyList()
                scheduleClear()
                return
            }
            if (!moved) continue
            change!!
            val idx = raw?.let { Stylus.stylusIndex(it) } ?: -1
            val history = change.historical
            val sameHistory = raw != null && idx >= 0 && raw.historySize == history.size
            history.forEachIndexed { h, sample ->
                if (sameHistory) dynamics(raw, idx, h, change.pressure) else dynamics(raw, idx, -1, change.pressure)
                ctx.eventTime = sample.uptimeMillis
                tool.move(ctx, sample.position, viewport.toCanvas(sample.position))
                predictor?.add(sample.position.x, sample.position.y, sample.uptimeMillis)
            }
            dynamics(raw, idx, -1, change.pressure)
            ctx.eventTime = change.uptimeMillis
            tool.move(ctx, change.position, viewport.toCanvas(change.position))
            // Sabitleme/çizim hızında mürekkep kalemin gerisinde: tahmin gösterilmez
            if (predictor != null && !tool.filtering) {
                predictor.record(raw)
                predictor.add(change.position.x, change.position.y, change.uptimeMillis)
                val rx = if (raw != null && idx >= 0) change.position.x - raw.getX(idx) else 0f
                val ry = if (raw != null && idx >= 0) change.position.y - raw.getY(idx) else 0f
                val maxDistance = (live.inkWidth * viewport.scale * 3f).coerceIn(12f, 60f)
                predicted.value = predictor.predict(rx, ry, maxDistance).map { (x, y) -> viewport.toCanvas(Offset(x, y)) }
            }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        tool.cancel(ctx)
        predicted.value = emptyList()
        throw e
    }
}

/** v20 çizim hızı süzgecinin zaman adımı (ms). */
internal const val TICK_MS = 16L
