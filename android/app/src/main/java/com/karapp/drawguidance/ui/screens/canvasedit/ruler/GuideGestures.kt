package com.karapp.drawguidance.ui.screens.canvasedit.ruler

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import kotlin.math.atan2

/**
 * v40 cetvel/kalıp hareketini çizim alanının asıl dinleyicisinden önce alan bayrak: [active] iken o hareket çizim
 * sayılmaz (bir sonraki parmak inişinde sıfırlanır).
 */
class GuideGrab {
    var active = false
}

/** v40 tutamakların ekrandaki boyutları (tuval birimine ölçeğe bölünerek çevrilir). */
object GuideMetrics {
    /** Tutamağa dokunma yarıçapı (dp). */
    const val HANDLE_RADIUS_DP = 24f
    /** Tutamağın gövdeden uzaklığı (dp). */
    const val HANDLE_GAP_DP = 30f
    /** Kenara yapışma uzaklığı (dp; ekranda sabit). */
    const val CAPTURE_DP = 28f
    /** Düz cetvelin eni (dp; açılışta). */
    const val THICKNESS_DP = 72f
}

/**
 * v40 cetvel ve kalıp hareketleri (çizim alanında, asıl dinleyiciden önce, Initial geçişinde):
 * - Cetvel: gövdede tek parmak taşır, iki parmak döndürür (ve taşır); döndürme tutamağı açıyı (15°'ye yapışarak;
 *   tutamakta [AngleSnap.RELEASE_MS] beklenirse serbest), boy tutamağı uzunluğu/boyu değiştirir.
 * - Kalıp seçimi: dokunulan eleman kalıp olur; sürükleme ve iki parmak görünümü kaydırır/yakınlaştırır.
 * - Kalıp (kilitli değilken): sol üst tutamak taşır, sağ alt tutamak ölçekler. Kalıbın kendisi dokunuşa kapalıdır.
 * Cetvelin/kalıbın tutmadığı dokunuşlar çizim alanına kalır (kenara yakın çizgiler yapışır).
 */
internal suspend fun PointerInputScope.guideGestures(
    controller: () -> RulerController?,
    enabled: () -> Boolean,
    viewport: ViewportState,
    grab: GuideGrab,
) {
    val slop = viewConfiguration.touchSlop
    val handlePx = GuideMetrics.HANDLE_RADIUS_DP.dp.toPx()
    val gapPx = GuideMetrics.HANDLE_GAP_DP.dp.toPx()
    val tolPx = 14.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        grab.active = false
        val c = controller() ?: return@awaitEachGesture
        if (!enabled()) return@awaitEachGesture
        val s = viewport.scale.coerceAtLeast(0.01f)
        val p0 = viewport.toCanvas(down.position)
        when (c.session.value) {
            GuideSession.NONE -> return@awaitEachGesture
            GuideSession.TEMPLATE_PICK -> {
                grab.active = true
                down.consume()
                var moved = false
                var multi = false
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = e.changes.filter { it.pressed }
                    e.changes.forEach { it.consume() }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2) {
                        multi = true
                        viewport.panBy(e.calculatePan())
                        val centroid = e.calculateCentroid(useCurrent = true)
                        if (centroid != Offset.Unspecified) viewport.zoomBy(e.calculateZoom(), centroid)
                    } else if (!multi) {
                        val ch = pressed[0]
                        if (!moved && (ch.position - down.position).getDistance() > slop) moved = true
                        if (moved) viewport.panBy(ch.position - ch.previousPosition)
                    }
                }
                if (moved || multi) viewport.settle() else c.pickAt(p0, tolPx / s)
            }
            GuideSession.RULER -> {
                val shape = c.shape() ?: return@awaitEachGesture
                val gap = gapPx / s
                val hit = shape.hit(p0, handlePx / s, gap)
                if (hit == RulerHandle.NONE) return@awaitEachGesture
                grab.active = true
                down.consume()
                rulerDrag(c, shape, hit, p0, gap, viewport)
            }
            GuideSession.TEMPLATE -> {
                if (c.locked.value) return@awaitEachGesture
                val ghost = c.template.value ?: return@awaitEachGesture
                val gap = gapPx / s
                val r = handlePx / s
                val moveH = ghost.moveHandle(gap)
                val scaleH = ghost.scaleHandle(gap)
                val moving = (p0 - moveH).getDistance() <= r
                val scaling = !moving && (p0 - scaleH).getDistance() <= r
                if (!moving && !scaling) return@awaitEachGesture
                grab.active = true
                down.consume()
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = e.changes.filter { it.pressed }
                    e.changes.forEach { it.consume() }
                    if (pressed.isEmpty()) break
                    val p = viewport.toCanvas(pressed[0].position)
                    c.setTemplate(if (moving) ghost.moved(p.x - p0.x, p.y - p0.y) else ghost.scaled(ghost.scaleFor(p, gap, ghost.scale, scaleH)))
                }
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.rulerDrag(
    c: RulerController,
    start: RulerShape,
    hit: RulerHandle,
    p0: Offset,
    gap: Float,
    viewport: ViewportState,
) {
    val startPose = start.pose
    val handleAngle = Math.toDegrees(start.rotateHandleLocalAngle(gap).toDouble()).toFloat()
    var snapOn = true
    // İki parmakla döndürme: başlangıçtaki iki parmak doğrusu, açı ve merkez
    var twoStartAngle = Float.NaN
    var twoStartPose = startPose
    var twoStartCentroid = Offset.Zero
    while (true) {
        val e: PointerEvent? = if (hit == RulerHandle.ROTATE && snapOn) {
            withTimeoutOrNull(AngleSnap.RELEASE_MS) { awaitPointerEvent(PointerEventPass.Initial) }
        } else awaitPointerEvent(PointerEventPass.Initial)
        if (e == null) { snapOn = false; continue }
        val pressed = e.changes.filter { it.pressed }
        e.changes.forEach { it.consume() }
        if (pressed.isEmpty()) break
        val current = c.pose.value ?: break
        if (pressed.size >= 2 && hit == RulerHandle.BODY) {
            val a = viewport.toCanvas(pressed[0].position)
            val b = viewport.toCanvas(pressed[1].position)
            val angle = Math.toDegrees(atan2(b.y - a.y, b.x - a.x).toDouble()).toFloat()
            val centroid = (a + b) / 2f
            if (twoStartAngle.isNaN()) {
                twoStartAngle = angle; twoStartPose = current; twoStartCentroid = centroid
                continue
            }
            val target = AngleSnap.snap(twoStartPose.angle + (angle - twoStartAngle))
            val delta = Math.toRadians((target - twoStartPose.angle).toDouble())
            val rx = twoStartPose.cx - twoStartCentroid.x
            val ry = twoStartPose.cy - twoStartCentroid.y
            val cos = kotlin.math.cos(delta).toFloat(); val sin = kotlin.math.sin(delta).toFloat()
            c.setPose(twoStartPose.copy(
                cx = centroid.x + rx * cos - ry * sin, cy = centroid.y + rx * sin + ry * cos, angle = target,
            ))
            continue
        }
        // İkinci parmak kalktı: bir sonraki iki parmak baştan sayılır
        twoStartAngle = Float.NaN
        if (pressed.size >= 2) continue
        val p = viewport.toCanvas(pressed[0].position)
        when (hit) {
            RulerHandle.BODY -> {
                // Adım adım (iki parmaktan tek parmağa geçişte sıçramaz)
                val d = p - viewport.toCanvas(pressed[0].previousPosition)
                c.setPose(current.copy(cx = current.cx + d.x, cy = current.cy + d.y))
            }
            RulerHandle.ROTATE -> {
                val raw = Math.toDegrees(atan2(p.y - startPose.cy, p.x - startPose.cx).toDouble()).toFloat() - handleAngle
                c.setPose(current.copy(angle = AngleSnap.snap(raw, snapOn)))
            }
            RulerHandle.SIZE, RulerHandle.MINOR -> c.setPose(start.resized(hit, p, gap))
            RulerHandle.NONE -> Unit
        }
    }
}
