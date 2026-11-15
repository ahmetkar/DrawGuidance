package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.comic.ComicTones
import com.karapp.drawguidance.content.structure.StructureGroup
import com.karapp.drawguidance.content.structure.StructureGuide
import com.karapp.drawguidance.content.structure.StructureGuides
import com.karapp.drawguidance.content.structure.StructurePrim
import com.karapp.drawguidance.data.model.ToneFill
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.drawing.ToneRenderer
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

/** Adı çalışma anında çözülen metin (ton/kılavuz anahtarları veriden gelir; res/raw/v53_keep.xml korur). */
@Composable
internal fun dynString(name: String): String {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    @Suppress("DiscouragedApi")
    val id = remember(name) { ctx.resources.getIdentifier(name, "string", ctx.packageName) }
    return if (id != 0) stringResource(id) else name
}

// MARK: Ekran tonları ve desenler -----------------------------------------------------------------------------

/** Tonun örnek görüntüsü (yuvarlak köşeli kutuda; gerçek çiziciyle). */
@Composable
fun TonePreview(tone: ToneFill, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(10.dp))) {
        drawIntoCanvas { c ->
            val clip = android.graphics.Path().apply { addRect(0f, 0f, size.width, size.height, android.graphics.Path.Direction.CW) }
            ToneRenderer.draw(c.nativeCanvas, clip, tone, 0f, 0f, size.width, size.height, 1f)
        }
    }
}

/**
 * v53 Desenler ve Ekran tonları (yalnız çizgi roman; ayrı düğme ve pencereler): her tür örneği ve çizgi romandaki
 * kullanımıyla; koyuluk, sıklık, açı ve renk ayarlanır. Seçili şekle/panele uygulanır ya da yeni tonlu alan eklenir.
 */
@Composable
fun V53ToneSheet(
    group: ComicTones.Group,
    canApply: Boolean,
    hasTone: Boolean,
    onInsert: (ToneFill) -> Unit,
    onApply: (ToneFill?) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = if (group == ComicTones.Group.TONE) ComicTones.TONES else ComicTones.PATTERNS
    var preset by remember { mutableStateOf<ComicTones.Preset?>(null) }
    var density by remember { mutableFloatStateOf(0.3f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var angle by remember { mutableFloatStateOf(45f) }
    var color by remember { mutableStateOf("#111111") }
    val colors = NeuTheme.colors
    val title = stringResource(if (group == ComicTones.Group.TONE) R.string.v53_tones else R.string.v53_patterns)
    val hint = stringResource(if (group == ComicTones.Group.TONE) R.string.v53_tones_hint else R.string.v53_patterns_hint)
    ComicSheetFrame(title, hint, if (group == ComicTones.Group.TONE) "tones_sheet" else "patterns_sheet", onDismiss) { columns ->
        Grid(presets, columns.coerceAtMost(4)) { p, m ->
            ComicTile(dynString(p.nameKey), preset?.key == p.key, {
                preset = p; density = p.density; scale = p.scale; angle = p.angle
            }, m, "tone_${p.key}") {
                TonePreview(ToneFill(p.id, p.density, p.scale * 0.6f, p.angle), Modifier.fillMaxWidth().height(64.dp))
                Text(dynString(p.usageKey), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, maxLines = 3)
            }
        }
        val current = preset?.let { ToneFill(it.id, density, scale, angle, color) }
        if (current != null) {
            Text(stringResource(R.string.v53_tone_settings), style = MaterialTheme.typography.titleSmall)
            TonePreview(current, Modifier.fillMaxWidth().height(90.dp))
            LabeledSlider(stringResource(R.string.v53_tone_density), density, 0.03f..0.9f, "${(density * 100).roundToInt()}%", "tone_density") { density = it }
            LabeledSlider(stringResource(R.string.v53_tone_scale), scale, 0.4f..3f, String.format("%.1f×", scale), "tone_scale") { scale = it }
            LabeledSlider(stringResource(R.string.v53_tone_angle), angle, 0f..180f, "${angle.roundToInt()}°", "tone_angle") { angle = it }
            SwatchPicker(color) { color = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Button({ onInsert(current) }, Modifier.weight(1f).testTag("tone_insert")) { Text(stringResource(R.string.v53_tone_insert)) }
                androidx.compose.material3.OutlinedButton({ onApply(current) }, Modifier.weight(1f).testTag("tone_apply"), enabled = canApply) {
                    Text(stringResource(R.string.v53_tone_apply))
                }
            }
            Text(stringResource(R.string.v53_tone_apply_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        }
        if (hasTone) {
            androidx.compose.material3.TextButton({ onApply(null) }, Modifier.testTag("tone_remove")) {
                Text(stringResource(R.string.v53_tone_remove), color = colors.danger)
            }
        }
    }
}

@Composable
internal fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, tag: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
        }
        Slider(value, onChange, valueRange = range, modifier = Modifier.testTag(tag))
    }
}

private val QUICK_COLORS = listOf("#111111", "#FFFFFF", "#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5", "#8E24AA", "#6D4C41", "#9E9E9E")

@Composable
internal fun SwatchPicker(selected: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QUICK_COLORS.forEach { c ->
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(hexToColor(c))
                    .border(if (c.equals(selected, true)) 2.5.dp else 1.dp, Color.Black.copy(alpha = if (c.equals(selected, true)) 0.85f else 0.15f), CircleShape)
                    .androidxClickable { onPick(c) },
            )
        }
    }
}

private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

// MARK: Yapı kılavuzları ---------------------------------------------------------------------------------------

/** v53 yapı kılavuzu durumu (yalnız görünüm; kaydedilmez, düzenleyici kapanınca biter). */
class StructureGuideState {
    var sheet by mutableStateOf(false)
    var guideId by mutableStateOf<String?>(null)
    var center by mutableStateOf(Offset.Zero)
    var height by mutableFloatStateOf(600f)
    var opacity by mutableFloatStateOf(0.75f)
    var flipped by mutableStateOf(false)
    val active: Boolean get() = guideId != null
    val guide: StructureGuide? get() = guideId?.let { StructureGuides.find(it) }

    fun place(id: String, canvasW: Int, canvasH: Int) {
        guideId = id
        val a = StructureGuides.find(id)?.aspect ?: 1f
        height = minOf(canvasH * 0.7f, canvasW * 0.7f / a.coerceAtLeast(0.1f))
        center = Offset(canvasW / 2f, canvasH / 2f)
        flipped = false
    }

    fun clear() { guideId = null }
}

@Composable
fun rememberStructureGuide(): StructureGuideState = remember { StructureGuideState() }

/** Kılavuzun çizimi (tuval üstü ve küçük resimlerde ortak). */
fun DrawScope.drawStructure(g: StructureGuide, rect: Rect, flipped: Boolean, color: Color, lineWidth: Float) {
    val s = rect.height
    fun pt(x: Float, y: Float) = Offset(rect.left + (if (flipped) g.aspect - x else x) * s, rect.top + y * s)
    fun poly(n: FloatArray, closed: Boolean): Path = Path().apply {
        var i = 0
        while (i + 1 < n.size) { val q = pt(n[i], n[i + 1]); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y); i += 2 }
        if (closed) close()
    }
    val solid = Stroke(lineWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    val halo = Stroke(lineWidth + 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    val dashed = Stroke((lineWidth * 0.7f).coerceAtLeast(1f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(lineWidth * 4, lineWidth * 3)))
    fun strokePath(p: Path, faint: Boolean) {
        if (!faint) drawPath(p, Color.White.copy(alpha = 0.65f), style = halo)
        drawPath(p, if (faint) color.copy(alpha = 0.45f) else color, style = if (faint) dashed else solid)
    }
    for (prim in g.prims) when (prim) {
        is StructurePrim.Measure -> {
            val p = Path()
            for (k in 1 until prim.n) { val y = k.toFloat() / prim.n; val a = pt(0f, y); val b = pt(g.aspect, y); p.moveTo(a.x, a.y); p.lineTo(b.x, b.y) }
            strokePath(p, true)
        }
        is StructurePrim.Axis -> strokePath(poly(prim.pts, false), true)
        is StructurePrim.Line -> strokePath(poly(prim.pts, false), false)
        is StructurePrim.Poly -> strokePath(poly(prim.pts, false), false)
        is StructurePrim.Polygon -> strokePath(poly(prim.pts, true), false)
        is StructurePrim.Circle -> {
            val c = pt(prim.x, prim.y); val r = prim.r * s
            drawCircle(Color.White.copy(alpha = 0.65f), r, c, style = halo); drawCircle(color, r, c, style = solid)
        }
        is StructurePrim.Ellipse -> {
            val c = pt(prim.x, prim.y)
            val deg = if (flipped) -prim.deg else prim.deg
            rotate(deg, c) {
                val tl = Offset(c.x - prim.rx * s, c.y - prim.ry * s)
                val sz = Size(prim.rx * s * 2, prim.ry * s * 2)
                drawOval(Color.White.copy(alpha = 0.65f), tl, sz, style = halo)
                drawOval(color, tl, sz, style = solid)
            }
        }
        is StructurePrim.Joint -> drawCircle(color, (lineWidth * 1.8f).coerceAtLeast(2.5f), pt(prim.x, prim.y))
        is StructurePrim.Vanish -> {
            val c = pt(prim.x, prim.y); val r = (lineWidth * 3).coerceAtLeast(4f)
            drawLine(Color(0xFFFF9800), Offset(c.x - r, c.y), Offset(c.x + r, c.y), lineWidth)
            drawLine(Color(0xFFFF9800), Offset(c.x, c.y - r), Offset(c.x, c.y + r), lineWidth)
        }
    }
}

/** Tuval üstünde kılavuz: dokunuşu tutmaz; köşedeki tutamaklarla taşınır ve boyutlanır. */
@Composable
fun StructureOverlay(state: StructureGuideState, viewport: ViewportState) {
    val g = state.guide ?: return
    val accent = NeuTheme.colors.accent
    val density = LocalDensity.current
    val h = state.height * viewport.scale
    val w = h * g.aspect
    val c = Offset(viewport.offset.x + state.center.x * viewport.scale, viewport.offset.y + state.center.y * viewport.scale)
    val rect = Rect(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2)
    Box(Modifier.fillMaxSize().testTag("structure_overlay")) {
        Canvas(Modifier.fillMaxSize()) {
            drawStructure(g, rect, state.flipped, accent.copy(alpha = state.opacity), with(density) { 1.6.dp.toPx() })
        }
        val handle = 30.dp
        val hp = with(density) { handle.toPx() }
        Handle(Icons.Filled.OpenWith, "structure_move", Modifier.offset { IntOffset((rect.left - hp / 2).roundToInt(), (rect.top - hp / 2).roundToInt()) }) { d ->
            state.center = Offset(state.center.x + d.x / viewport.scale, state.center.y + d.y / viewport.scale)
        }
        Handle(Icons.Filled.OpenInFull, "structure_resize", Modifier.offset { IntOffset((rect.right - hp / 2).roundToInt(), (rect.bottom - hp / 2).roundToInt()) }) { d ->
            state.height = (state.height + d.y / viewport.scale).coerceAtLeast(60f)
        }
    }
}

@Composable
private fun Handle(icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, modifier: Modifier, onDrag: (Offset) -> Unit) {
    Box(
        modifier.size(30.dp).clip(CircleShape).background(NeuTheme.colors.accent)
            .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDrag(drag) } }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
}

/** Kılavuz seçimi: Canlılar, Nesneler, Eşyalar; küçük resim ve ad. Seçiliyken görünürlük ve aynalama. */
@Composable
fun StructureGuideSheet(state: StructureGuideState, canvasW: Int, canvasH: Int) {
    var group by remember { mutableStateOf(StructureGroup.LIVING) }
    val accent = NeuTheme.colors.accent
    ComicSheetFrame(stringResource(R.string.v53_struct_title), stringResource(R.string.v53_struct_hint), "structure_sheet", { state.sheet = false }) { columns ->
        PillTabs(
            StructureGroup.entries.map { Triple(it, dynString("v53_struct_group_" + it.name.lowercase()), Icons.Filled.DragIndicator) },
            group, "structure_group_",
        ) { group = it }
        Grid(StructureGuides.byGroup(group), columns.coerceAtMost(4)) { g, m ->
            ComicTile(dynString(g.nameKey), state.guideId == g.id, { state.place(g.id, canvasW, canvasH); state.sheet = false }, m, "structure_${g.id}") {
                Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                    val hh = minOf(size.height, size.width / g.aspect.coerceAtLeast(0.1f)) * 0.9f
                    val ww = hh * g.aspect
                    drawStructure(g, Rect(Offset((size.width - ww) / 2, (size.height - hh) / 2), Size(ww, hh)), false, accent, 2f)
                }
            }
        }
        if (state.active) {
            Text(stringResource(R.string.v53_struct_settings), style = MaterialTheme.typography.titleSmall)
            LabeledSlider(stringResource(R.string.v53_struct_opacity), state.opacity, 0.15f..1f, "${(state.opacity * 100).roundToInt()}%", "structure_opacity") { state.opacity = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.v53_struct_flip), Modifier.weight(1f))
                Switch(state.flipped, { state.flipped = it }, Modifier.testTag("structure_flip"))
            }
            androidx.compose.material3.TextButton({ state.clear(); state.sheet = false }, Modifier.testTag("structure_remove")) {
                Text(stringResource(R.string.v53_struct_remove), color = NeuTheme.colors.danger)
            }
        }
    }
}

// MARK: Hızlı yazı kutusu --------------------------------------------------------------------------------------

/** v53 not almada hızlı yazı kutusu durumu: kutunun tuvaldeki sol-üst köşesi ve genişliği (tuval birimi). */
class QuickTextState {
    var active by mutableStateOf(false)
    var anchor by mutableStateOf<Offset?>(null)
    var text by mutableStateOf("")
    var width by mutableFloatStateOf(600f)
    fun close() { active = false; text = ""; anchor = null }
}

@Composable
fun rememberQuickText(): QuickTextState = remember { QuickTextState() }

/**
 * Tuvaldeki hızlı yazı kutusu: temaya uygun saydam kutu; yazılar klavyeyle yazılır, Enter'a basınca kutu arka plansız
 * yazıya dönüşür ve hemen altında yeni kutu açılır. Sol tutamaktan sürüklenerek taşınır.
 */
@Composable
fun QuickTextOverlay(state: QuickTextState, viewport: ViewportState, textSizePx: Float, inkHex: String, onCommit: () -> Unit, onClose: () -> Unit) {
    val a = state.anchor ?: return
    if (!state.active) return
    val colors = NeuTheme.colors
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    val sx = viewport.offset.x + a.x * viewport.scale
    val sy = viewport.offset.y + a.y * viewport.scale
    val wPx = (state.width * viewport.scale).coerceAtLeast(with(density) { 160.dp.toPx() })
    val fontSp = with(density) { (textSizePx * viewport.scale).coerceAtLeast(12f).toSp() }
    Row(
        Modifier.offset { IntOffset((sx - with(density) { 26.dp.toPx() }).roundToInt(), sy.roundToInt()) }
            .widthIn(max = with(density) { (wPx + with(density) { 90.dp.toPx() }).toDp() })
            .clip(RoundedCornerShape(10.dp))
            .background(colors.accent.copy(alpha = 0.07f))
            .border(1.2.dp, colors.accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .testTag("quick_text_box"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(22.dp, 30.dp).pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    state.anchor = state.anchor?.let { Offset(it.x + drag.x / viewport.scale, it.y + drag.y / viewport.scale) }
                }
            },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.DragIndicator, stringResource(R.string.v53_quick_text_move), tint = colors.accent) }
        BasicTextField(
            state.text, { state.text = it },
            Modifier.width(with(density) { wPx.toDp() }).focusRequester(focus).testTag("quick_text_field"),
            textStyle = TextStyle(fontSize = fontSp, color = hexToColor(if (inkHex.equals("#FFFFFF", true)) "#1E1E1E" else inkHex)),
            singleLine = true,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onCommit() }, onDone = { onCommit() }),
            decorationBox = { inner ->
                Box {
                    if (state.text.isEmpty()) Text(stringResource(R.string.v53_quick_text_hint), color = colors.contentMuted, fontSize = 13.sp)
                    inner()
                }
            },
        )
        Box(Modifier.size(28.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.18f)).androidxClickable(onCommit).testTag("quick_text_commit"), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.KeyboardReturn, stringResource(R.string.v53_quick_text_next), tint = colors.accent, modifier = Modifier.size(16.dp))
        }
        Box(Modifier.size(28.dp).clip(CircleShape).background(colors.content.copy(alpha = 0.07f)).androidxClickable(onClose).testTag("quick_text_close"), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, stringResource(R.string.close), tint = colors.contentMuted, modifier = Modifier.size(14.dp))
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
