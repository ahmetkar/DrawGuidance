package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.drawing.BackgroundTransform
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v40 arka planı taşıma/boyutlandırma katmanı (Arka plan aracı seçiliyken "Taşı / boyutlandır"; not alma dışında,
 * arka plan resmi varken): çizim alanının üstünde resmin çerçevesi ve köşe tutamakları. Tek parmak resmi sürükler,
 * köşeden sürüklemek (karşı köşe yerinde) boyutlandırır, iki parmak sıkıştırarak boyutlandırır ve kaydırır. Her hareket
 * tek geri alma adımıdır. Üstte ipucu, Sıfırla ve Bitti.
 */
@Composable
fun BackgroundTransformOverlay(
    canvas: CanvasUi,
    viewport: ViewportState,
    available: Boolean,
    onPreview: (BackgroundSpec) -> Unit,
    onCommit: (BackgroundSpec) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    /** v41 resim taşınıp boyutlandırılabilir mi (değilse çerçeve sayfanın kendisi; yalnız eleman işlemleri). */
    transformable: Boolean = available,
    /** v41 arka planı değiştir (pencere açılır). */
    onChange: (() -> Unit)? = null,
    /** v41 arka planı kaldır. */
    onRemove: (() -> Unit)? = null,
    /** v41 not tuvalinde bütün sayfalara uygula. */
    onApplyAll: (() -> Unit)? = null,
) {
    // Arka plan kalktıysa (geri alındı, sayfa değişti, silindi) katman kapanır
    LaunchedEffect(available) { if (!available) onDone() }
    if (!available) return
    val colors = NeuTheme.colors
    val w = canvas.width.toFloat()
    val h = canvas.height.toFloat()
    val spec = canvas.pageBackground
    val currentSpec by rememberUpdatedState(spec)
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val cancel by rememberUpdatedState(onCancel)
    val done by rememberUpdatedState(onDone)
    val movable = transformable
    // v45 seçim menüsünde Ölçekle seçiliyken tek parmak resmi çerçevenin ortasına göre büyütür/küçültür
    var scaleMode by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val scaling by rememberUpdatedState(scaleMode)
    Box(Modifier.fillMaxSize().testTag("bg_transform_overlay")) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(w, h, movable) {
                val slop = viewConfiguration.touchSlop
                val handleR = 30.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val start = currentSpec
                    if (!movable) {
                        // v41 resimsiz arka plan: sürüklenmez; kağıdın dışına dokunmak seçimi kaldırır
                        val p0 = viewport.toCanvas(down.position)
                        while (awaitPointerEvent().changes.any { it.pressed }) Unit
                        if (p0.x < 0f || p0.y < 0f || p0.x > w || p0.y > h) done()
                        return@awaitEachGesture
                    }
                    val startCanvas = viewport.toCanvas(down.position)
                    val screenCorners = BackgroundTransform.corners(start, w, h).map { viewport.toScreen(it) }
                    val nearest = screenCorners.indices.minByOrNull { (screenCorners[it] - down.position).getDistance() }
                    val corner = nearest?.takeIf { (screenCorners[it] - down.position).getDistance() <= handleR }
                    var result = start
                    var changed = false
                    var multi = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        event.changes.forEach { it.consume() }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            multi = true
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (centroid != Offset.Unspecified) {
                                val k = viewport.scale.coerceAtLeast(0.01f)
                                result = BackgroundTransform.pinch(result, w, h, event.calculatePan() / k, event.calculateZoom(), viewport.toCanvas(centroid))
                                changed = true
                                preview(result)
                            }
                            continue
                        }
                        // İki parmaktan sonra kalan tek parmak sürüklemez
                        if (multi) continue
                        val c = pressed[0]
                        if (!changed && (c.position - down.position).getDistance() <= slop) continue
                        val p = viewport.toCanvas(c.position)
                        result = when {
                            corner != null -> BackgroundTransform.corner(start, w, h, corner, p)
                            scaling -> {
                                val center = BackgroundTransform.frame(start, w, h).center
                                val zoom = (p - center).getDistance() / (startCanvas - center).getDistance().coerceAtLeast(1f)
                                BackgroundTransform.pinch(start, w, h, Offset.Zero, zoom.coerceIn(0.05f, 20f), center)
                            }
                            else -> BackgroundTransform.moved(start, w, h, p - startCanvas)
                        }
                        changed = true
                        preview(result)
                    }
                    if (changed) commit(result) else {
                        cancel()
                        // v41 çerçevenin dışına dokunmak (sürüklemeden) arka planın seçimini kaldırır
                        val f = BackgroundTransform.frame(start, w, h)
                        val p0 = viewport.toCanvas(down.position)
                        if (corner == null && !f.contains(p0)) done()
                    }
                }
            },
        ) {
            val f = if (movable) BackgroundTransform.frame(spec, w, h) else androidx.compose.ui.geometry.Rect(0f, 0f, w, h)
            val tl = viewport.toScreen(f.topLeft)
            val br = viewport.toScreen(f.bottomRight)
            drawRect(
                colors.accent, topLeft = tl, size = Size(br.x - tl.x, br.y - tl.y),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f))),
            )
            if (movable) BackgroundTransform.corners(spec, w, h).forEach { p ->
                val c = viewport.toScreen(p)
                drawCircle(colors.background, 11.dp.toPx(), c)
                drawCircle(colors.accent, 9.dp.toPx(), c)
            }
        }
        // v45 arka plan eleman gibi: seçili elemanın yatay menüsü (eski üst çubuğun yerine). Taşı / Ölçekle tek parmağın
        // ne yapacağını seçer; Kopyala bütün sayfalara uygular; Sil kaldırır; Düzenle: değiştir, sıfırla, saydamlık, bitti
        Text(
            stringResource(R.string.v41_bg_selected) + " · " + stringResource(if (movable) R.string.bg_transform_hint else R.string.v41_bg_selected_hint),
            style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, maxLines = 2,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp, start = 60.dp, end = 60.dp).testTag("bg_selected_title"),
        )
        val actions = selectionMenuActions(
            onMove = { scaleMode = false }, onScale = { scaleMode = true },
            onCopy = { onApplyAll?.invoke() },
            onDelete = { onRemove?.invoke(); done() },
            moveEnabled = movable, scaleEnabled = movable, copyEnabled = onApplyAll != null,
            moveSelected = movable && !scaleMode, scaleSelected = movable && scaleMode,
        )
        val changeText = stringResource(R.string.v41_bg_change)
        val resetText = stringResource(R.string.bg_transform_reset)
        val doneText = stringResource(R.string.move_done)
        val editItems = listOfNotNull(
            onChange?.let { ModeAction(changeText, Icons.Filled.Wallpaper, it, tag = "bg_change") },
            if (movable) ModeAction(resetText, Icons.Filled.RestartAlt, { commit(BackgroundTransform.reset(currentSpec)) }, enabled = spec.hasImageTransform, tag = "bg_transform_reset") else null,
            ModeAction(doneText, Icons.Filled.Check, onDone, primary = true, tag = "bg_transform_done"),
        )
        // v41 saydamlık: resim varsa resmin, yoksa desenin (Düzenle listesinin altında)
        val opacity = when {
            spec.imageId != null -> spec.imageOpacity
            spec.patternId != null -> spec.patternOpacity
            else -> null
        }
        val opacityText = stringResource(R.string.v41_bg_opacity)
        SelectionMenu(
            anchor = {
                val f = if (movable) BackgroundTransform.frame(currentSpec, w, h) else androidx.compose.ui.geometry.Rect(0f, 0f, w, h)
                androidx.compose.ui.geometry.Rect(viewport.toScreen(f.topLeft), viewport.toScreen(f.bottomRight))
            },
            actions = actions,
            editItems = editItems,
            tag = "bg_selection_menu",
            editExtra = if (opacity == null) null else {
                {
                    var drag by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<BackgroundSpec?>(null) }
                    Row(Modifier.padding(horizontal = 14.dp).widthIn(max = 260.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(opacityText, style = MaterialTheme.typography.bodySmall)
                        androidx.compose.material3.Slider(
                            value = opacity, valueRange = 0.1f..1f,
                            onValueChange = { v ->
                                val next = if (currentSpec.imageId != null) currentSpec.copy(imageOpacity = v) else currentSpec.copy(patternOpacity = v)
                                drag = next; preview(next)
                            },
                            onValueChangeFinished = { drag?.let { commit(it) }; drag = null },
                            modifier = Modifier.weight(1f).testTag("bg_selected_opacity"),
                        )
                    }
                }
            },
        )
    }
}
