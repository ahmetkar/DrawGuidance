package com.karapp.drawguidance.ui.screens.canvasedit.ruler

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
// v40 eğitimde cetvel çubuğu ve düğmeleri tanıtılır
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.tutorialAnchor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** v40 cetvel ve kalıbı açan işler (menüler bunları çağırır). */
class RulerActions(
    val openRuler: () -> Unit,
    val openTemplate: () -> Unit,
    /** v40 basit menüdeki Cetvel düğmesi: açık değilse açar, açıksa kapatır. */
    val toggleRuler: () -> Unit,
    /** v40 basit menüdeki Şekil kalıbı düğmesi: açık değilse açar, açıksa kapatır. */
    val toggleTemplate: () -> Unit,
)

/**
 * v40 cetvel/kalıp açma işleri: çizim moduna geçilir (silgi açıksa son fırçaya dönülür; çizgi son seçili fırçayla
 * çizilir), cetvel görünen alanın ortasında açılır.
 */
@Composable
fun rememberRulerActions(viewModel: CanvasEditViewModel, viewport: ViewportState): RulerActions {
    val controller = viewModel.ruler
    val context = LocalContext.current
    val density = LocalDensity.current.density
    LaunchedEffect(controller) { controller.attach(RulerPrefs(context)) }
    return remember(controller, viewport, density) {
        fun toDraw() {
            if (viewModel.mode.value != EditMode.DRAW) viewModel.chooseMode(EditMode.DRAW)
            if (viewModel.brushSettings.value.brushType == BrushType.ERASER) viewModel.toggleEraser()
        }
        val openRuler = {
                toDraw()
                val s = viewport.scale.coerceAtLeast(0.01f)
                val view = viewport.viewSize
                val c = viewModel.canvas.value
                val center = if (view.width > 0) viewport.toCanvas(Offset(view.width / 2f, view.height / 2f))
                else Offset((c?.width ?: 1000) / 2f, (c?.height ?: 1000) / 2f)
                val visible = if (view.width > 0) view.width / s else (c?.width ?: 1000).toFloat()
                controller.openRuler(center, visible, GuideMetrics.THICKNESS_DP * density / s)
            }
        val openTemplate = {
                toDraw()
                controller.openTemplate()
            }
        RulerActions(
            openRuler = openRuler,
            openTemplate = openTemplate,
            toggleRuler = { if (controller.session.value == GuideSession.RULER) controller.finish() else openRuler() },
            toggleTemplate = {
                val sess = controller.session.value
                if (sess == GuideSession.TEMPLATE || sess == GuideSession.TEMPLATE_PICK) controller.finish() else openTemplate()
            },
        )
    }
}

@Composable
private fun typeLabel(t: RulerType): String = stringResource(
    when (t) {
        RulerType.STRAIGHT -> R.string.v40_ruler_straight
        RulerType.PROTRACTOR -> R.string.v40_ruler_protractor
        RulerType.SET_SQUARE_45 -> R.string.v40_ruler_set45
        RulerType.SET_SQUARE_30 -> R.string.v40_ruler_set30
        RulerType.COMPASS -> R.string.v40_ruler_compass
        RulerType.ELLIPSE -> R.string.v40_ruler_ellipse
        RulerType.FRENCH_CURVE -> R.string.v40_ruler_french
    },
)

/** v40 cetvelin kenarlarındaki bilgiler: boy (birimle, ölçekli), açı, ölçek ve birim. */
data class RulerLabels(val length: String, val angle: String, val scale: String) {
    val summary: String get() = "$length · $angle · $scale"

    companion object {
        fun of(shape: RulerShape, settings: RulerSettings): RulerLabels {
            val prefix = if (shape.pose.type == RulerType.PROTRACTOR || shape.pose.type == RulerType.COMPASS) "r " else ""
            return RulerLabels(
                prefix + RulerUnits.format(shape.measuredLength, settings.unit, settings.scale),
                "${AngleSnap.display(shape.pose.angle)}°",
                "${RulerUnits.scaleLabel(settings.scale)} · ${settings.unit.label}",
            )
        }
    }
}

/**
 * v40 cetvel/kalıp katmanı (çizim alanının üstünde; kendisi dokunuş almaz, hareketler çizim alanında): cetvel ya da
 * kalıbın hayaleti, tutamaklar ve yanlarındaki ayar düğmeleri, kenar bilgileri; altta bilgi çubuğu ve Bitir.
 * Çizim modundan çıkılınca kapanır.
 */
@Composable
fun RulerLayer(viewModel: CanvasEditViewModel, viewport: ViewportState, actions: RulerActions, kind: CanvasKind) {
    val controller = viewModel.ruler
    val mode by viewModel.mode.collectAsState()
    val session by controller.session.collectAsState()
    val pose by controller.pose.collectAsState()
    val settings by controller.settings.collectAsState()
    val ghost by controller.template.collectAsState()
    val locked by controller.locked.collectAsState()
    val libraryOpen by controller.libraryOpen.collectAsState()
    LaunchedEffect(mode) { if (mode != EditMode.DRAW) controller.finish() }
    val colors = NeuTheme.colors
    val accent = colors.accent
    val ink = colors.content
    val paper = colors.background
    Box(Modifier.fillMaxSize()) {
        if (session != GuideSession.NONE && mode == EditMode.DRAW) {
            // pose ve kalıp değişince yeniden çizilir
            val shape = remember(pose, session) { if (session == GuideSession.RULER) controller.shape() else null }
            Canvas(Modifier.fillMaxSize().testTag(if (session == GuideSession.RULER) "ruler_overlay" else "template_overlay")) {
                val gapPx = GuideMetrics.HANDLE_GAP_DP.dp.toPx()
                if (shape != null) drawRuler(shape, viewport, settings, accent, ink, paper, gapPx)
                ghost?.takeIf { session == GuideSession.TEMPLATE }?.let { drawGhost(it, viewport, accent, paper, !locked, gapPx) }
            }
            if (shape != null) RulerHandleChips(controller, shape, settings, viewport)
            GuideBar(controller, session, settings, shape, locked)
        }
    }
    if (libraryOpen) {
        com.karapp.drawguidance.ui.screens.canvasedit.components.ShapePickerModal(
            kind = kind, selected = null,
            onDismiss = { controller.libraryOpen.value = false },
            onSelect = { id ->
                controller.libraryOpen.value = false
                val s = viewport.scale.coerceAtLeast(0.01f)
                val view = viewport.viewSize
                val c = viewModel.canvas.value
                val center = if (view.width > 0) viewport.toCanvas(Offset(view.width / 2f, view.height / 2f))
                else Offset((c?.width ?: 1000) / 2f, (c?.height ?: 1000) / 2f)
                val size = if (view.width > 0) minOf(view.width, view.height) / s * 0.5f else 400f
                controller.placeLibrary(id, center, size)
            },
            title = stringResource(R.string.v40_template),
        )
    }
}

/**
 * v40 alttaki bilgi çubuğu (alt menünün ve sistem çubuğunun üstünde): cetvelde canlı bilgiler (boy, açı, ölçek ve
 * birim) ve Bitir; kalıpta kilit, kütüphane, Bitir ve kısa yönerge. Cetvelin ayarları cetvelin yanındaki
 * tutamak düğmelerinde ([RulerHandleChips]).
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.GuideBar(
    controller: RulerController,
    session: GuideSession,
    settings: RulerSettings,
    shape: RulerShape?,
    locked: Boolean,
) {
    val colors = NeuTheme.colors
    NeuSurface(
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(max = 560.dp).testTag("ruler_bar").tutorialAnchor(TutorialAnchor.RULER_BAR),
        elevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (session == GuideSession.RULER && shape != null) {
                    Text(
                        RulerLabels.of(shape, settings).summary, style = MaterialTheme.typography.labelLarge, color = colors.accent,
                        maxLines = 2, modifier = Modifier.weight(1f, fill = false).testTag("ruler_info"),
                    )
                } else {
                    Row(
                        Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (session == GuideSession.TEMPLATE) {
                            NeuChip(
                                stringResource(if (locked) R.string.v40_template_unlock else R.string.v40_template_lock), locked, controller::toggleLock,
                                icon = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen, modifier = Modifier.testTag("template_lock"),
                            )
                        }
                        NeuChip(stringResource(R.string.v40_template_library), false, { controller.libraryOpen.value = true }, icon = Icons.Filled.Category, modifier = Modifier.testTag("template_library").tutorialAnchor(TutorialAnchor.TEMPLATE_LIBRARY))
                    }
                }
                NeuButton(stringResource(R.string.v40_ruler_finish), controller::finish, primary = true, icon = Icons.Filled.Check, modifier = Modifier.testTag("ruler_finish").tutorialAnchor(TutorialAnchor.RULER_FINISH))
            }
            if (session != GuideSession.RULER) {
                val hint = stringResource(if (session == GuideSession.TEMPLATE_PICK) R.string.v40_template_pick_hint else R.string.v40_template_hint)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, modifier = Modifier.testTag(if (session == GuideSession.TEMPLATE_PICK) "template_pick_hint" else "ruler_hint"))
            }
        }
    }
}

/**
 * v40 cetvelin üstündeki ayar düğmeleri (tutamakların yanında, cetvelle birlikte gezer; ekran dışına taşmaz):
 * döndürme tutamağının yanında tür, boy tutamağının yanında birim/ölçek ve Bitir. Dokununca küçük seçiciler
 * cetvelin yanında açılır.
 */
@Composable
private fun RulerHandleChips(controller: RulerController, shape: RulerShape, settings: RulerSettings, viewport: ViewportState) {
    val density = LocalDensity.current
    val gapPx = with(density) { GuideMetrics.HANDLE_GAP_DP.dp.toPx() }
    val s = viewport.scale.coerceAtLeast(0.01f)
    val pose = shape.pose
    val center = viewport.toScreen(Offset(pose.cx, pose.cy))
    val gap = gapPx / s
    fun dirOf(p: Offset, fallback: Offset): Offset {
        val d = p - center
        val len = d.getDistance()
        return if (len < 1f) fallback else d / len
    }
    val axis = run {
        val r = Math.toRadians(pose.angle.toDouble())
        Offset(cos(r).toFloat(), sin(r).toFloat())
    }
    val sizeAt = shape.handle(RulerHandle.SIZE, gap)?.let { viewport.toScreen(it) } ?: center
    val sizeDir = dirOf(sizeAt, axis)
    val rotateAt = shape.handle(RulerHandle.ROTATE, gap)?.let { viewport.toScreen(it) } ?: center
    val rotateDir = dirOf(rotateAt, -axis)
    var typeOpen by remember { mutableStateOf(false) }
    var unitOpen by remember { mutableStateOf(false) }
    // Pergelde döndürme tutamağı yok: tür düğmesi iğnenin öbür yanında
    val typeOffset = if (pose.type == RulerType.COMPASS) 22.dp else 24.dp
    HandleAnchor(rotateAt, rotateDir, typeOffset, viewport) {
        Box {
            HandleChip(typeShort(settings.type), Icons.Filled.Straighten, { typeOpen = true }, "ruler_handle_type", modifier = Modifier.tutorialAnchor(TutorialAnchor.RULER_TYPE))
            DropdownMenu(typeOpen, { typeOpen = false }) {
                RulerType.entries.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(typeLabel(t)) }, onClick = { typeOpen = false; controller.setType(t) },
                        leadingIcon = if (t == settings.type) ({ Icon(Icons.Filled.Check, null) }) else null,
                        modifier = Modifier.testTag("ruler_type_${t.name}"),
                    )
                }
            }
        }
    }
    HandleAnchor(sizeAt, sizeDir, 24.dp, viewport) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                HandleChip(
                    "${settings.unit.label} · ${RulerUnits.scaleLabel(settings.scale)}", null, { unitOpen = true }, "ruler_handle_units",
                    modifier = Modifier.tutorialAnchor(TutorialAnchor.RULER_UNIT).tutorialAnchor(TutorialAnchor.RULER_SCALE),
                )
                DropdownMenu(unitOpen, { unitOpen = false }) {
                    Text(stringResource(R.string.v40_ruler_unit), style = MaterialTheme.typography.labelMedium, color = NeuTheme.colors.contentMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    RulerUnit.entries.forEach { u ->
                        DropdownMenuItem(
                            text = { Text(u.label) }, onClick = { unitOpen = false; controller.setUnit(u) },
                            leadingIcon = if (u == settings.unit) ({ Icon(Icons.Filled.Check, null) }) else null,
                            modifier = Modifier.testTag("ruler_unit_${u.name}"),
                        )
                    }
                    Text(stringResource(R.string.v40_ruler_scale), style = MaterialTheme.typography.labelMedium, color = NeuTheme.colors.contentMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    RulerUnits.Scales.forEach { sc ->
                        DropdownMenuItem(
                            text = { Text(RulerUnits.scaleLabel(sc)) }, onClick = { unitOpen = false; controller.setScale(sc) },
                            leadingIcon = if (sc == settings.scale) ({ Icon(Icons.Filled.Check, null) }) else null,
                            modifier = Modifier.testTag("ruler_scale_$sc"),
                        )
                    }
                }
            }
            HandleChip(stringResource(R.string.v40_ruler_finish), Icons.Filled.Check, controller::finish, "ruler_handle_finish", primary = true)
        }
    }
}

/** Kısa tür adı (tutamak düğmesinde). */
@Composable
private fun typeShort(t: RulerType): String = typeLabel(t)

/** v40 cetvelin üstünde küçük düğme (yarı saydam zemin, ince kenar). */
@Composable
private fun HandleChip(
    text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit, tag: String, primary: Boolean = false,
    /** v40 eğitimin işaret ettiği yer (tutorialAnchor). */
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    val bg = if (primary) colors.accent else colors.background.copy(alpha = 0.94f)
    val fg = if (primary) colors.background else colors.content
    Row(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, colors.accent.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (primary) fg else colors.accent, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

/**
 * [anchor] tutamağının [dir] yönünde [distance] uzağına içeriği yerleştirir (içeriğin yakın kenarı oraya gelir) ve
 * çizim alanının içinde tutar.
 */
@Composable
private fun HandleAnchor(anchor: Offset, dir: Offset, distance: androidx.compose.ui.unit.Dp, viewport: ViewportState, content: @Composable () -> Unit) {
    Box(
        Modifier.layout { measurable, constraints ->
            val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            val view = viewport.viewSize
            val w = if (view.width > 0) view.width else constraints.maxWidth
            val h = if (view.height > 0) view.height else constraints.maxHeight
            layout(w, h) {
                val d = distance.toPx()
                val margin = 4.dp.toPx()
                fun at(u: Offset): Offset {
                    val cx = anchor.x + u.x * (d + p.width / 2f)
                    val cy = anchor.y + u.y * (d + p.height / 2f)
                    return Offset(
                        (cx - p.width / 2f).coerceIn(margin, (w - p.width - margin).coerceAtLeast(margin)),
                        (cy - p.height / 2f).coerceIn(margin, (h - p.height - margin).coerceAtLeast(margin)),
                    )
                }
                // v41 ekran kenarına itilen düğme tutamağın üstüne binmesin (tutamak sürüklenemez olur): yana geçer
                fun covers(o: Offset): Boolean {
                    val nx = anchor.x.coerceIn(o.x, o.x + p.width)
                    val ny = anchor.y.coerceIn(o.y, o.y + p.height)
                    return (Offset(nx, ny) - anchor).getDistance() < d
                }
                val pos = listOf(dir, Offset(-dir.y, dir.x), Offset(dir.y, -dir.x), -dir).map(::at).firstOrNull { !covers(it) } ?: at(dir)
                p.place(pos.x.toInt(), pos.y.toInt())
            }
        },
    ) { content() }
}

// --- Çizim ---

private fun polyPath(path: Path, pts: FloatArray, viewport: ViewportState, close: Boolean) {
    val n = pts.size / 2
    if (n < 2) return
    for (i in 0 until n) {
        val p = viewport.toScreen(Offset(pts[2 * i], pts[2 * i + 1]))
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    if (close) path.close()
}

private fun textPaint(color: Color, sizePx: Float, bold: Boolean = false) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    textSize = sizePx
    textAlign = android.graphics.Paint.Align.CENTER
    if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
}

/** Ekranda [at] noktasına, [angleDeg] açısıyla (baş aşağı olmasın diye çevrilerek) yazı; arkasında soluk zemin. */
private fun DrawScope.label(text: String, at: Offset, angleDeg: Float, paint: android.graphics.Paint, back: Color) {
    drawIntoCanvas { c ->
        val n = c.nativeCanvas
        var a = AngleSnap.normalize(angleDeg)
        if (a > 90f) a -= 180f else if (a < -90f) a += 180f
        n.save()
        n.rotate(a, at.x, at.y)
        val w = paint.measureText(text) / 2f + 4.dp.toPx()
        val h = paint.textSize
        val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = back.copy(alpha = 0.8f).toArgb() }
        n.drawRoundRect(at.x - w, at.y - h * 0.8f, at.x + w, at.y + h * 0.35f, h * 0.3f, h * 0.3f, bg)
        n.drawText(text, at.x, at.y, paint)
        n.restore()
    }
}

private fun DrawScope.handleDot(at: Offset, glyph: String, accent: Color, paper: Color) {
    val r = 15.dp.toPx()
    drawCircle(paper, r + 2.dp.toPx(), at)
    drawCircle(accent, r, at)
    drawIntoCanvas { c ->
        val p = textPaint(paper, 16.dp.toPx(), bold = true)
        c.nativeCanvas.drawText(glyph, at.x, at.y + p.textSize * 0.35f, p)
    }
}

private fun DrawScope.drawRuler(
    shape: RulerShape, viewport: ViewportState, settings: RulerSettings, accent: Color, ink: Color, paper: Color, gapPx: Float,
) {
    val s = viewport.scale.coerceAtLeast(0.01f)
    val pose = shape.pose
    val gap = gapPx / s
    val body = Path().apply { fillType = PathFillType.EvenOdd }
    polyPath(body, shape.bodyPolygon, viewport, true)
    shape.holePolygon?.let { polyPath(body, it, viewport, true) }
    if (pose.type != RulerType.COMPASS) {
        drawPath(body, accent.copy(alpha = 0.14f))
        drawPath(body, accent.copy(alpha = 0.75f), style = Stroke(1.5.dp.toPx()))
    }
    val tickColor = ink.copy(alpha = 0.8f)
    val small = textPaint(ink, 10.dp.toPx())
    val half = pose.length / 2f
    val unitPx = RulerUnits.toPx(1f, settings.unit)
    fun line(a: Offset, b: Offset, w: Float = 1.dp.toPx(), color: Color = tickColor) = drawLine(color, viewport.toScreen(a), viewport.toScreen(b), w)

    /** Kenar boyunca çentikler: yerel (x0,y) → (x0+len,y), çentikler [dir] yönünde (yerel y). */
    fun ticksAlong(x0: Float, length: Float, y: Float, dir: Float) {
        val (step, major) = RulerUnits.tickStep(unitPx * s, 7.dp.toPx(), settings.unit)
        val stepPx = step * unitPx
        if (stepPx <= 0f) return
        val count = floor(length / stepPx).toInt().coerceAtMost(4000)
        val labelEvery = if (major * stepPx * s >= 36.dp.toPx()) major else major * 2
        for (i in 0..count) {
            val lx = x0 + i * stepPx
            val isMajor = i % major == 0
            val len = (if (isMajor) 14.dp.toPx() else if (major % 2 == 0 && i % (major / 2) == 0) 10.dp.toPx() else 6.dp.toPx()) / s
            line(shape.toWorld(lx, y), shape.toWorld(lx, y + dir * len))
            if (i % labelEvery == 0 && i > 0) {
                val at = viewport.toScreen(shape.toWorld(lx, y + dir * (len + 9.dp.toPx() / s)))
                label(RulerUnits.number(i * step * settings.scale), at + Offset(0f, small.textSize * 0.35f), pose.angle, small, paper)
            }
        }
    }

    val labels = RulerLabels.of(shape, settings)
    val big = textPaint(accent, 12.dp.toPx(), bold = true)
    when (pose.type) {
        RulerType.STRAIGHT -> {
            val t = pose.thickness / 2f
            ticksAlong(-half, pose.length, -t, 1f)
            label(labels.length, viewport.toScreen(shape.toWorld(0f, t * 0.55f)), pose.angle, big, paper)
        }
        RulerType.PROTRACTOR -> {
            val pxPerDeg = half * s * (PI / 180).toFloat()
            val step = when { pxPerDeg >= 5.dp.toPx() -> 1; pxPerDeg * 5 >= 5.dp.toPx() -> 5; else -> 10 }
            val labelStep = if (pxPerDeg * 10 >= 26.dp.toPx()) 10 else 30
            var d = 0
            while (d <= 180) {
                val th = Math.toRadians(d.toDouble())
                val ux = cos(th).toFloat(); val uy = -sin(th).toFloat()
                val len = (if (d % 10 == 0) 14.dp.toPx() else if (d % 5 == 0) 10.dp.toPx() else 6.dp.toPx()) / s
                line(shape.toWorld(ux * half, uy * half), shape.toWorld(ux * (half - len), uy * (half - len)))
                if (d % labelStep == 0) {
                    val rr = half - len - 10.dp.toPx() / s
                    label("$d", viewport.toScreen(shape.toWorld(ux * rr, uy * rr)) + Offset(0f, small.textSize * 0.35f), pose.angle + 90f - d, small, paper)
                }
                d += step
            }
            line(shape.toWorld(0f, 0f), shape.toWorld(0f, -10.dp.toPx() / s), 1.5.dp.toPx(), accent)
            label(labels.length, viewport.toScreen(shape.toWorld(0f, -half * 0.3f)), pose.angle, big, paper)
        }
        RulerType.SET_SQUARE_45, RulerType.SET_SQUARE_30 -> {
            val h = if (pose.type == RulerType.SET_SQUARE_45) pose.length else pose.length * tan(PI / 6).toFloat()
            ticksAlong(-pose.length / 3f, pose.length, h / 3f, -1f)
            label(labels.length, viewport.toScreen(shape.toWorld(-pose.length / 9f, 0f)), pose.angle, big, paper)
        }
        RulerType.COMPASS -> {
            val center = viewport.toScreen(Offset(pose.cx, pose.cy))
            drawCircle(accent.copy(alpha = 0.7f), half * s, center, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
            line(Offset(pose.cx, pose.cy), shape.toWorld(half, 0f), 1.5.dp.toPx(), accent)
            drawCircle(paper, 9.dp.toPx(), center)
            drawCircle(accent, 7.dp.toPx(), center)
            label(labels.length, viewport.toScreen(shape.toWorld(half / 2f, -12.dp.toPx() / s)), pose.angle, big, paper)
        }
        RulerType.ELLIPSE -> {
            val b = half * pose.minor.coerceIn(RulerShape.MIN_MINOR, 1f)
            line(shape.toWorld(-half, 0f), shape.toWorld(half, 0f), 0.75.dp.toPx(), accent.copy(alpha = 0.5f))
            line(shape.toWorld(0f, -b), shape.toWorld(0f, b), 0.75.dp.toPx(), accent.copy(alpha = 0.5f))
            label(labels.length, viewport.toScreen(shape.toWorld(0f, b + 14.dp.toPx() / s)), pose.angle, big, paper)
        }
        RulerType.FRENCH_CURVE -> {
            label(labels.length, viewport.toScreen(shape.toWorld(0f, pose.length * 0.27f)), pose.angle, big, paper)
        }
    }
    // Tutamaklar ve yanlarında açı, ölçek ve birim
    shape.handle(RulerHandle.ROTATE, gap)?.let { h ->
        val p = viewport.toScreen(h)
        handleDot(p, "⟳", accent, paper)
        label(labels.angle, p + Offset(0f, 30.dp.toPx()), 0f, big, paper)
    }
    shape.handle(RulerHandle.SIZE, gap)?.let { h ->
        val p = viewport.toScreen(h)
        handleDot(p, "↔", accent, paper)
        label(labels.scale, p + Offset(0f, 30.dp.toPx()), 0f, small, paper)
        if (pose.type == RulerType.COMPASS) label(labels.angle, p + Offset(0f, 46.dp.toPx()), 0f, big, paper)
    }
    shape.handle(RulerHandle.MINOR, gap)?.let { h -> handleDot(viewport.toScreen(h), "↕", accent, paper) }
}

private fun DrawScope.drawGhost(ghost: TemplateGhost, viewport: ViewportState, accent: Color, paper: Color, handles: Boolean, gapPx: Float) {
    val color = runCatching { hexToColor(ghost.colorHex) }.getOrDefault(accent)
    ghost.contours.forEach { (pts, closed) ->
        val path = Path()
        polyPath(path, pts, viewport, closed)
        drawPath(path, paper.copy(alpha = 0.5f), style = Stroke(5.dp.toPx()))
        drawPath(path, color.copy(alpha = 0.55f), style = Stroke(2.dp.toPx()))
    }
    if (!handles) return
    val s = viewport.scale.coerceAtLeast(0.01f)
    val gap = gapPx / s
    val b = viewport.toScreen(ghost.bounds)
    drawRect(
        accent.copy(alpha = 0.6f), topLeft = b.topLeft, size = b.size,
        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))),
    )
    handleDot(viewport.toScreen(ghost.moveHandle(gap)), "✥", accent, paper)
    handleDot(viewport.toScreen(ghost.scaleHandle(gap)), "⤡", accent, paper)
}
