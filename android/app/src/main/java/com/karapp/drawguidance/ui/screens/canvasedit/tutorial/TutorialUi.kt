package com.karapp.drawguidance.ui.screens.canvasedit.tutorial

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.drawStroke
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic
import kotlin.math.roundToInt
import com.karapp.drawguidance.ui.components.NeuSheet

/** v32 eğitimde tanıtılan araçların ekrandaki yerleri (kök koordinatı). */
@Stable
class TutorialAnchors {
    val rects = mutableStateMapOf<String, Rect>()
    /** Tanıtılan araç: kaydırılan bir satırdaysa görünür alana getirilir. */
    var focus by mutableStateOf<String?>(null)
}

val LocalTutorialAnchors = staticCompositionLocalOf<TutorialAnchors?> { null }

/** v40 eğitim adımının ekranda bulunan hedefinin kimliği (testler için; ekran okuyucuya söylenmez). */
val TutorialTargetKey = androidx.compose.ui.semantics.SemanticsPropertyKey<String>("TutorialTarget")
var androidx.compose.ui.semantics.SemanticsPropertyReceiver.tutorialTarget by TutorialTargetKey

/**
 * Aracın yerini eğitim için bildirir (açıklama kutusu bu aracın altında açılır). Tanıtılan araç yana kayan bir satırda
 * ekran dışındaysa görünür alana getirilir.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.tutorialAnchor(id: String?): Modifier = if (id == null) this else composed {
    val registry = LocalTutorialAnchors.current
    if (registry == null) Modifier else {
        val requester = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
        val focused = registry.focus == id
        androidx.compose.runtime.LaunchedEffect(focused) { if (focused) runCatching { requester.bringIntoView() } }
        DisposableEffect(registry, id) { onDispose { registry.rects.remove(id) } }
        Modifier
            .bringIntoViewRequester(requester)
            .onGloballyPositioned { c ->
                val r = c.boundsInRoot()
                if (registry.rects[id] != r && r.width > 0f) registry.rects[id] = r
            }
    }
}


/** Eğitimde çizilmekte olan (henüz eklenmemiş) çizgiler; tuvalin üstünde, görünümle birlikte. */
@Composable
fun TutorialLiveLayer(live: TutorialLive, viewport: ViewportState, modifier: Modifier = Modifier) {
    if (live.strokes.isEmpty()) return
    Canvas(modifier.fillMaxSize().testTag("tutorial_live")) {
        withTransform({
            translate(viewport.offset.x, viewport.offset.y)
            scale(viewport.scale, viewport.scale, Offset.Zero)
        }) {
            live.strokes.forEach { drawStroke(it) }
        }
    }
}

/**
 * v32 eğitim katmanı: tanıtılan aracın çevresinde nabız gibi atan vurgu halkası, aracın hemen altında (yer yoksa
 * üstünde; çizim alanı adımlarında alanın altında) açıklama kutusu ve kutudan araca uzanan ok, çizim yapan "parmak".
 * Eğitim sürerken ekrana dokunmalar engellenir; yalnız yapay zekâ önerisi vurgulanınca öneri balonuna dokunulabilir.
 */
@Composable
fun TutorialOverlay(
    ui: TutorialUi,
    live: TutorialLive,
    anchors: TutorialAnchors,
    viewport: ViewportState,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    onReplay: () -> Unit,
    onCallout: (Rect) -> Unit,
    /** v37 çocuk tuvalinde açıklamalar maskotun balonunda (çizim alanının sağ alt köşesinde). */
    mascot: Boolean = false,
) {
    val colors = NeuTheme.colors
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var callout by remember { mutableStateOf<Rect?>(null) }
    val canvasRect = anchors.rects[TutorialAnchor.CANVAS]
    fun local(r: Rect) = r.translate(-origin)
    val stepTarget = (anchors.rects[ui.step.anchor] ?: canvasRect)?.let(::local)
    val focusTarget = (anchors.rects[ui.focus] ?: anchors.rects[ui.step.anchor] ?: canvasRect)?.let(::local)
    val onCanvas = ui.step.anchor == TutorialAnchor.CANVAS
    val hole = focusTarget?.takeIf { ui.focus in TutorialAnchor.SUGGESTS }?.inflate(with(density) { 6.dp.toPx() })
    val pulse by rememberInfiniteTransition(label = "tutorial").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse",
    )
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .testTag("tutorial_overlay"),
    ) {
        // Dokunma engeli: vurgulanan öneri balonu dışında (balona kullanıcı da dokunabilsin)
        TouchBlocker(hole)
        // v40 testler için: adımın hedefi ekranda bulundu mu (bulunduysa yeri ve kimliği)
        anchors.rects[ui.step.anchor]?.let(::local)?.let { r ->
            Box(
                Modifier
                    .layout { m, _ ->
                        val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)))
                        layout(0, 0) { p.place(r.left.roundToInt(), r.top.roundToInt()) }
                    }
                    .semantics { tutorialTarget = ui.step.anchor }
                    .testTag("tutorial_target"),
            )
        }
        // Vurgu halkası, kutudan araca ok ve parmak
        Canvas(Modifier.fillMaxSize().testTag("tutorial_ring")) {
            val ring = focusTarget
            if (ring != null && !(onCanvas && ui.focus == TutorialAnchor.CANVAS)) {
                val pad = 6.dp.toPx() + 3.dp.toPx() * pulse
                val r = ring.inflate(pad)
                val radius = CornerRadius(minOf(r.height, r.width) / 2f, minOf(r.height, r.width) / 2f).let {
                    if (ring.height > 70.dp.toPx()) CornerRadius(20.dp.toPx(), 20.dp.toPx()) else it
                }
                drawRoundRect(colors.accent.copy(alpha = 0.10f + 0.10f * pulse), r.topLeft, r.size, radius)
                drawRoundRect(colors.accent.copy(alpha = 0.55f + 0.35f * pulse), r.topLeft, r.size, radius, style = DrawStroke(2.5.dp.toPx()))
            }
            // Kutudan araca uzanan nöromorfik ok
            val box = callout?.translate(-origin)
            val target = stepTarget
            if (box != null && target != null && !onCanvas) {
                val below = box.top >= target.bottom
                val x = target.center.x.coerceIn(box.left + 22.dp.toPx(), box.right - 22.dp.toPx())
                val baseY = if (below) box.top + 1f else box.bottom - 1f
                val tipY = if (below) baseY - 11.dp.toPx() else baseY + 11.dp.toPx()
                val half = 11.dp.toPx()
                val path = Path().apply { moveTo(x - half, baseY); lineTo(x, tipY); lineTo(x + half, baseY); close() }
                val sh = 1.5.dp.toPx()
                withTransform({ translate(sh, sh) }) { drawPath(path, colors.darkShadow.copy(alpha = 0.45f)) }
                drawPath(path, colors.background)
            }
            // Parmak (tuvalde ya da bir aracın üstünde)
            val p: Offset? = when {
                live.pointerAnchor != null -> anchors.rects[live.pointerAnchor]?.let { local(it).center }
                live.pointer != null && canvasRect != null -> local(canvasRect).topLeft + viewport.toScreen(live.pointer)
                else -> null
            }
            if (p != null) {
                val outer = (if (live.pressed) 15.dp else 21.dp).toPx()
                drawCircle(colors.darkShadow.copy(alpha = 0.35f), outer, p + Offset(2.dp.toPx(), 3.dp.toPx()))
                drawCircle(colors.background.copy(alpha = 0.92f), outer, p)
                drawCircle(colors.accent.copy(alpha = if (live.pressed) 0.55f else 0.35f), outer, p, style = DrawStroke(2.dp.toPx()))
                drawCircle(colors.accent, (if (live.pressed) 6.dp else 4.5.dp).toPx(), p)
                // v40 basılı tutma halkası (dolunca Taşı)
                live.hold?.let { f ->
                    val r = outer + 7.dp.toPx()
                    drawCircle(colors.contentMuted.copy(alpha = 0.25f), r, p, style = DrawStroke(3.dp.toPx()))
                    drawArc(
                        colors.accent, -90f, 360f * f.coerceIn(0f, 1f), false, p - Offset(r, r),
                        androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = DrawStroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }
        }
        // Açıklama kutusu: aracın hemen altında (yer yoksa üstünde); çizim alanı adımlarında alanın altında
        val margin = with(density) { 12.dp.roundToPx() }
        val gap = with(density) { 16.dp.roundToPx() }
        if (mascot) {
            KidsTutorialMascot(
                ui, onNext = onNext, onCancel = onCancel,
                modifier = Modifier
                    .layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0, minHeight = 0, maxWidth = (c.maxWidth - 2 * margin).coerceAtLeast(0)))
                        layout(c.maxWidth, c.maxHeight) {
                            val cr = canvasRect?.let(::local)
                            val right = cr?.right?.roundToInt() ?: c.maxWidth
                            // Mod çubuğu görünüyorsa (ör. boyamada renk paleti) maskot onun üstünde
                            val bar = anchors.rects[TutorialAnchor.MODE_BAR]?.let(::local)?.top?.plus(margin)
                            val bottom = listOfNotNull(cr?.bottom, bar).minOrNull()?.roundToInt() ?: c.maxHeight
                            p.place((right - margin - p.width).coerceAtLeast(margin), (bottom - margin - p.height).coerceAtLeast(margin))
                        }
                    }
                    .onGloballyPositioned { c -> onCallout(c.boundsInRoot()) },
            )
        } else Box(
            Modifier
                .layout { m, c ->
                    val maxW = (c.maxWidth - 2 * margin).coerceAtLeast(0)
                    val p = m.measure(c.copy(minWidth = 0, minHeight = 0, maxWidth = minOf(maxW, with(density) { 400.dp.roundToPx() })))
                    layout(c.maxWidth, c.maxHeight) {
                        val t = stepTarget
                        val cr = canvasRect?.let(::local)
                        val (x, y) = when {
                            t == null -> ((c.maxWidth - p.width) / 2) to (c.maxHeight - p.height - margin)
                            onCanvas && cr != null -> ((c.maxWidth - p.width) / 2) to (cr.bottom.roundToInt() - p.height - margin).coerceAtLeast(margin)
                            else -> {
                                val below = t.bottom.roundToInt() + gap
                                val above = t.top.roundToInt() - gap - p.height
                                val y = when {
                                    below + p.height <= c.maxHeight - margin -> below
                                    above >= margin -> above
                                    else -> (c.maxHeight - p.height - margin).coerceAtLeast(margin)
                                }
                                (t.center.x.roundToInt() - p.width / 2).coerceIn(margin, (c.maxWidth - p.width - margin).coerceAtLeast(margin)) to y
                            }
                        }
                        p.place(x, y)
                    }
                },
        ) {
            TutorialCallout(
                ui = ui, onNext = onNext, onCancel = onCancel, onReplay = onReplay,
                modifier = Modifier.onGloballyPositioned { c ->
                    val r = c.boundsInRoot()
                    if (callout != r) { callout = r; onCallout(r) }
                },
            )
        }
    }
}

/** Dokunmaları yutar; [hole] (katmanın kendi koordinatı) içine dokunulabilir (altındaki öneri balonuna geçer). */
@Composable
private fun TouchBlocker(hole: Rect?) {
    val block = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent()
                e.changes.forEach { it.consume() }
            }
        }
    }
    if (hole == null) {
        Box(Modifier.fillMaxSize().then(block))
        return
    }
    // Deliğin çevresindeki dört dikdörtgen (delikte dokunmayı alan bir düğüm yok)
    Box(Modifier.fillMaxSize()) {
        val l = hole.left.coerceAtLeast(0f); val t = hole.top.coerceAtLeast(0f)
        Box(Modifier.fillMaxWidth().heightInPx(t).then(block))
        Box(Modifier.offsetPx(0f, hole.bottom).fillMaxSize().then(block))
        Box(Modifier.offsetPx(0f, t).sizePx(l, hole.height).then(block))
        Box(Modifier.offsetPx(hole.right, t).fillMaxWidth().heightInPx(hole.height).then(block))
    }
}

private fun Modifier.heightInPx(px: Float) = layout { m, c ->
    val h = px.roundToInt().coerceIn(0, c.maxHeight)
    val p = m.measure(c.copy(minHeight = h, maxHeight = h))
    layout(p.width, p.height) { p.place(0, 0) }
}

private fun Modifier.sizePx(w: Float, h: Float) = layout { m, c ->
    val ww = w.roundToInt().coerceIn(0, c.maxWidth); val hh = h.roundToInt().coerceIn(0, c.maxHeight)
    val p = m.measure(c.copy(minWidth = ww, maxWidth = ww, minHeight = hh, maxHeight = hh))
    layout(p.width, p.height) { p.place(0, 0) }
}

private fun Modifier.offsetPx(x: Float, y: Float) = layout { m, c ->
    val p = m.measure(c)
    layout(p.width, p.height) { p.place(x.roundToInt(), y.roundToInt()) }
}

/** Adımın açıklama kutusu (nöromorfik kart): adım, başlık, açıklama, ilerleme ve düğmeler. */
@Composable
private fun TutorialCallout(ui: TutorialUi, onNext: () -> Unit, onCancel: () -> Unit, onReplay: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    NeuSurface(modifier.testTag("tutorial_callout"), shape = RoundedCornerShape(22.dp), elevation = 10.dp) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.neumorphic(colors, NeuPillShape, elevation = 3.dp, inset = true).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(
                        stringResource(R.string.tut_step_of, ui.index + 1, ui.count), style = MaterialTheme.typography.labelMedium,
                        color = colors.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("tutorial_step"),
                    )
                }
                Text(
                    stringResource(ui.group.title), style = MaterialTheme.typography.labelMedium, color = colors.contentMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (ui.running) {
                    CircularProgressIndicator(Modifier.size(14.dp).semantics { contentDescription = "" }, strokeWidth = 2.dp, color = colors.accent)
                }
                if (ui.step.demo != null) {
                    NeuIconButton(Icons.Filled.Replay, stringResource(R.string.tut_replay), onReplay, size = 30.dp, modifier = Modifier.testTag("tutorial_replay"))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ui.step.ai) Icon(Icons.Filled.AutoAwesome, stringResource(R.string.tut_ai_badge), tint = colors.accent, modifier = Modifier.size(18.dp))
                Text(stringResource(ui.step.title), style = MaterialTheme.typography.titleMedium, color = colors.content, modifier = Modifier.testTag("tutorial_title"))
            }
            Text(stringResource(ui.step.body), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            ui.hint?.let { hint ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.accent.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("tutorial_hint"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = colors.content)
                }
            }
            // İlerleme noktaları
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(ui.count) { i ->
                    val current = i == ui.index
                    Box(
                        Modifier.size(width = if (current) 18.dp else 7.dp, height = 7.dp).clip(CircleShape)
                            .background(if (i <= ui.index) colors.accent.copy(alpha = if (current) 1f else 0.45f) else colors.contentMuted.copy(alpha = 0.25f)),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                NeuButton(
                    stringResource(R.string.tut_cancel), onCancel, Modifier.weight(1f).testTag("tutorial_cancel"),
                    icon = Icons.Filled.Close, contentColor = colors.contentMuted,
                )
                NeuButton(
                    stringResource(if (ui.last) R.string.tut_finish else R.string.tut_next), onNext, Modifier.weight(1f).testTag("tutorial_next"),
                    primary = true, icon = if (ui.last) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                )
            }
        }
    }
}

/**
 * v32 eğitim önerisi: tuval türüne göre araç grupları (çizim / not alma / çizgi roman). Kullanıcı hangi grubu seçerse
 * onun eğitimi başlar; tamamlanan gruplar işaretli.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialOfferSheet(
    kind: CanvasKind,
    groups: List<TutorialGroup>,
    completed: Set<String>,
    onStart: (TutorialGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NeuTheme.colors
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().heightIn(max = 680.dp).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp).testTag("tutorial_offer"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(46.dp).neumorphic(colors, CircleShape, elevation = 5.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.School, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                }
                Text(
                    stringResource(
                        when (kind) {
                            CanvasKind.NOTES -> R.string.tut_offer_title_notes
                            CanvasKind.COMIC -> R.string.tut_offer_title_comic
                            // v40 basit resim ve basit not
                            CanvasKind.SIMPLE_ART -> R.string.tut40_offer_title_simple_art
                            CanvasKind.SIMPLE_NOTES -> R.string.tut40_offer_title_simple_notes
                            else -> R.string.tut_offer_title_art
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Text(stringResource(R.string.tut_offer_text), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
            groups.forEach { g ->
                val title = stringResource(g.title)
                val cd = stringResource(R.string.tut_offer_start_cd, title)
                NeuSurface(
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = cd }.testTag("tutorial_group_${g.id}"),
                    elevation = 7.dp, onClick = { onStart(g) },
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier.size(44.dp).neumorphic(colors, CircleShape, elevation = 3.dp, inset = true),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(g.icon, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(g.summary), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.tut_steps, g.steps.size), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
                                if (g.ai) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(Icons.Filled.AutoAwesome, null, tint = colors.accent, modifier = Modifier.size(13.dp))
                                        Text(stringResource(R.string.tut_ai_badge), style = MaterialTheme.typography.labelSmall, color = colors.accent)
                                    }
                                }
                                if (g.id in completed) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(Icons.Filled.CheckCircle, null, tint = colors.accent.copy(alpha = 0.8f), modifier = Modifier.size(13.dp))
                                        Text(stringResource(R.string.tut_done_badge), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
                                    }
                                }
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Text(stringResource(R.string.tut_offer_note), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            NeuButton(stringResource(R.string.tut_offer_later), onDismiss, Modifier.fillMaxWidth().testTag("tutorial_later"))
        }
    }
}
