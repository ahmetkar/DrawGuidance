package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

/**
 * v40 her tuval türünde (resim, not, çizgi roman, basit resim, basit not, çocuklar için) bir elemana parmak 5 sn
 * kıpırdamadan basılı kalınca o eleman seçilir ve Taşı moduna geçilir; parmak kalkmadan sürüklenirse eleman parmağı
 * izler (ya da sonra sürüklenerek taşınır). Basılı tutarken parmağın çevresinde ince bir ilerleme halkası dolar.
 *
 * 2 sn basılı tutma (elemanı seçip Seç'e geçme; çocuk boyamasında bütün şekli boyama) olduğu gibi kalır: 5 sn onun
 * devamıdır. Çocuk boyamasında halka kovanın kendi 2 sn halkası bittikten sonra görünür.
 *
 * Bu hareket ayrı ve hiç yeniden başlamayan bir dokunuş dinleyicisindedir (mod değişince çizim alanının dinleyicisi
 * yeniden başlar; 2 sn'deki Seç'e geçiş 5 sn'lik sayımı bozmasın diye). Olayları ilk geçişte (Initial) izler; Taşı'ya
 * geçince parmak kalkana kadar olayları yutar.
 */
object HoldToMove {
    /** Taşı'ya geçmek için basılı tutma süresi. */
    const val HOLD_MOVE_MS = 5000L

    /** Halka bu kadar basılı tutulduktan sonra görünür (dokunmalarda titremesin). */
    const val RING_DELAY_MS = 400L

    /** Halkanın ilerleme adımı (ms). */
    const val RING_TICK_MS = 50L

    /** Bu modda 5 sn basılı tutma Taşı'ya geçirir mi (ekleme ve düzenleme araçlarında kendi hareketleri var). */
    fun eligible(mode: EditMode): Boolean = when (mode) {
        EditMode.DRAW, EditMode.SELECT, EditMode.MOVE, EditMode.ROTATE, EditMode.SCALE,
        EditMode.PAINT, EditMode.ADD, EditMode.EDIT_PICK -> true
        else -> false
    }

    /** Halkanın görünmeye başladığı süre: çocuk boyamasında kovanın 2 sn halkası bitince. */
    fun ringDelay(mode: EditMode, kind: CanvasKind): Long =
        if (mode == EditMode.PAINT && kind == CanvasKind.KIDS) LONG_PRESS_MS else RING_DELAY_MS

    /** [elapsed] ms basılı tutmada halkanın dolulugu (0..1); henüz görünmüyorsa null. */
    fun ringProgress(elapsed: Long, delay: Long): Float? =
        if (elapsed < delay) null else (elapsed.toFloat() / HOLD_MOVE_MS).coerceIn(0f, 1f)
}

/** v40 basılı tutmanın halkası (ekran konumu ve doluluğu). */
data class HoldRing(val center: Offset, val progress: Float)

/** v40 basılı tutup taşımanın durumu (çizim alanının iki dinleyicisi arasında ortak). */
@Stable
class HoldMoveState {
    /** Gösterilen halka (null: yok). */
    var ring by mutableStateOf<HoldRing?>(null)

    /**
     * Taşı'ya geçildi ve parmak bu hareketi sürdürüyor: çizim alanının asıl dinleyicisi bu hareketi yok sayar (parmak
     * kalkınca dokunma ya da çizim sayılmaz). Bir sonraki parmak inişinde sıfırlanır.
     */
    var engaged = false
}

/**
 * v40 5 sn basılı tutunca Taşı (bkz. [HoldToMove]). [onHold] tuval noktasında elemanı seçip Taşı'ya geçer (eleman
 * yoksa false); [setPreview] taşıma önizlemesi, [onMove] parmak kalkınca taşıma.
 */
internal suspend fun PointerInputScope.holdToMove(
    state: HoldMoveState,
    viewport: ViewportState,
    mode: () -> EditMode,
    kind: () -> CanvasKind,
    onHold: (point: Offset, tolerance: Float) -> Boolean,
    setPreview: (Preview?) -> Unit,
    onMove: (dx: Float, dy: Float) -> Unit,
    onFired: () -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    val hitTolerancePx = 14.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        state.engaged = false
        state.ring = null
        // Kalem çizerken (değdiği anda çizer) ve uygun olmayan modlarda yok
        if (down.type == PointerType.Stylus || down.type == PointerType.Eraser) return@awaitEachGesture
        val startMode = mode()
        if (!HoldToMove.eligible(startMode)) return@awaitEachGesture
        val delay = HoldToMove.ringDelay(startMode, kind())
        val downTime = down.uptimeMillis
        var time = downTime
        var position = down.position
        try {
            while (true) {
                val elapsed = time - downTime
                if (elapsed >= HoldToMove.HOLD_MOVE_MS) break
                val wait = min(HoldToMove.HOLD_MOVE_MS - elapsed, HoldToMove.RING_TICK_MS)
                val event = withTimeoutOrNull(wait.coerceAtLeast(1L)) { awaitPointerEvent(PointerEventPass.Initial) }
                if (event == null) {
                    time += wait
                } else {
                    val pressed = event.changes.filter { it.pressed }
                    // İkinci parmak, kalkış ya da kayma: basılı tutma biter
                    if (pressed.size != 1 || pressed[0].id != down.id) return@awaitEachGesture
                    if ((pressed[0].position - down.position).getDistance() > slop) return@awaitEachGesture
                    position = pressed[0].position
                    time = maxOf(time, pressed[0].uptimeMillis)
                }
                state.ring = HoldToMove.ringProgress(time - downTime, delay)?.let { HoldRing(position, it) }
            }
        } finally {
            state.ring = null
        }
        if (!HoldToMove.eligible(mode())) return@awaitEachGesture
        val anchor = viewport.toCanvas(position)
        if (!onHold(anchor, hitTolerancePx / viewport.scale.coerceAtLeast(0.01f))) return@awaitEachGesture
        state.engaged = true
        onFired()
        // Parmak kalkana kadar eleman parmağı izler (kayma eşiğinden sonra)
        var delta: Offset? = null
        var stillDown = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            event.changes.forEach { it.consume() }
            if (event.changes.count { it.pressed } > 1) {
                // İkinci parmak: taşıma bırakılır (eleman seçili, Taşı'da kalır)
                delta = null
                setPreview(null)
                stillDown = true
                break
            }
            if (change == null || !change.pressed) { stillDown = event.changes.any { it.pressed }; break }
            if (delta != null || (change.position - position).getDistance() > slop) {
                val d = viewport.toCanvas(change.position) - anchor
                delta = d
                setPreview(Preview.Move(d))
            }
        }
        delta?.let { d ->
            setPreview(null)
            onMove(d.x, d.y)
        }
        // Kalan olaylar da (öteki parmaklar kalkana kadar) yutulur
        while (stillDown) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) break
        }
    }
}

/** v40 basılı tutmanın ince ilerleme halkası. */
@Composable
internal fun HoldMoveRing(state: HoldMoveState) {
    val ring = state.ring ?: return
    val colors = NeuTheme.colors
    Canvas(Modifier.fillMaxSize().testTag("hold_move_ring")) {
        val r = 34.dp.toPx()
        val w = 3.dp.toPx()
        val topLeft = ring.center - Offset(r, r)
        drawCircle(colors.accent.copy(alpha = 0.16f), r, ring.center, style = Stroke(width = w))
        drawArc(
            colors.accent.copy(alpha = 0.7f), startAngle = -90f, sweepAngle = 360f * ring.progress, useCenter = false,
            topLeft = topLeft, size = Size(r * 2, r * 2), style = Stroke(width = w, cap = StrokeCap.Round),
        )
    }
}
