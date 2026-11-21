package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.ArrowKind
import com.karapp.drawguidance.data.model.CapStyle
import com.karapp.drawguidance.data.model.GradientPoint
import com.karapp.drawguidance.data.model.JoinStyle
import com.karapp.drawguidance.data.model.LayerBlend
import com.karapp.drawguidance.data.model.PaintLayer
import com.karapp.drawguidance.data.model.RepeatKind
import com.karapp.drawguidance.data.model.RepeatSpec
import com.karapp.drawguidance.data.model.StrokeAlign
import com.karapp.drawguidance.data.model.V56Flags
import com.karapp.drawguidance.data.model.V56Textures
import com.karapp.drawguidance.data.model.VectorStyle
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

/** v56 aşama 8A seçim satırı araçları: bağlı kopya, bağı kopar, opaklık maskesi, maskeyi kaldır. */
@Composable
fun v56ObjectToolItems(viewModel: CanvasEditViewModel, hasSelection: Boolean, selectedCount: Int): List<ToolItem> {
    val out = ArrayList<ToolItem>()
    if (viewModel.v56(V56Flags.Feature.ADV_REPEAT)) {
        out += ToolItem(Icons.Filled.FilterNone, stringResource(R.string.v56_clone), { viewModel.cloneSelection() }, enabled = hasSelection, tag = "v56_clone")
        if (hasSelection && viewModel.selectionHasClone()) out += ToolItem(Icons.Filled.LinkOff, stringResource(R.string.v56_unlink), { viewModel.unlinkClones() }, tag = "v56_unlink")
    }
    if (viewModel.v56(V56Flags.Feature.ADV_APPEARANCE)) {
        if (hasSelection && viewModel.selectionHasOpacityMask()) out += ToolItem(Icons.Filled.Gradient, stringResource(R.string.v56_release_mask), { viewModel.releaseOpacityMask() }, tag = "v56_release_mask")
        else out += ToolItem(Icons.Filled.Gradient, stringResource(R.string.v56_opacity_mask), { viewModel.makeOpacityMask() }, enabled = selectedCount >= 2, tag = "v56_opacity_mask")
    }
    return out
}

@Composable
private fun chipRow(content: @Composable () -> Unit) =
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { content() }

@Composable
private fun label(text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = NeuTheme.colors.contentMuted)

@Composable
private fun swatch(hex: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).background(hexToColor(hex), CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) NeuTheme.colors.accent else NeuTheme.colors.contentMuted, CircleShape)
            .clickable(onClick = onClick).testTag(tag),
    )
}

/** Nesne menüsünde Tekrarla grubu (yalnız Resim): tür seçilince ayar penceresi açılır. */
@Composable
fun V56RepeatSection(viewModel: CanvasEditViewModel) {
    if (!viewModel.v56(V56Flags.Feature.ADV_REPEAT)) return
    var open by remember { mutableStateOf<RepeatKind?>(null) }
    val current = viewModel.selectedRepeat()
    chipRow {
        RepeatKind.entries.forEach { k ->
            NeuChip(repeatLabel(k), current?.kind == k, { open = k }, Modifier.testTag("v56_repeat_${k.name.lowercase()}"))
        }
        if (current != null) NeuChip(stringResource(R.string.v56_repeat_remove), false, { viewModel.removeRepeat() }, Modifier.testTag("v56_repeat_remove"))
    }
    open?.let { k -> V56RepeatDialog(viewModel, current?.takeIf { it.kind == k } ?: RepeatSpec(k, count = if (k == RepeatKind.RADIAL) 6 else 4)) { open = null } }
}

@Composable
fun repeatLabel(k: RepeatKind): String = stringResource(
    when (k) {
        RepeatKind.LINEAR -> R.string.v56_repeat_linear
        RepeatKind.GRID -> R.string.v56_repeat_grid
        RepeatKind.RADIAL -> R.string.v56_repeat_radial
        RepeatKind.MIRROR -> R.string.v56_repeat_mirror
        RepeatKind.STEP -> R.string.v56_repeat_step
    },
)

/** Tekrar ayarları (önizlemeli): Uygula tek geri alma adımı; İptal belgeyi değiştirmez. Kaynak değişince kopyalar güncellenir. */
@Composable
fun V56RepeatDialog(viewModel: CanvasEditViewModel, initial: RepeatSpec, onClose: () -> Unit) {
    var spec by remember(initial) { mutableStateOf(initial) }
    LaunchedEffect(spec) { viewModel.previewRepeat(spec) }
    @Composable
    fun slider(text: String, value: Float, range: ClosedFloatingPointRange<Float>, tag: String, set: (Float) -> Unit) {
        Text(text, style = MaterialTheme.typography.bodySmall)
        NeuSlider(value, { set(it) }, range, Modifier.widthIn(min = 220.dp).testTag(tag))
    }
    AlertDialog(
        onDismissRequest = { viewModel.cancelRepeatPreview(); onClose() }, containerColor = NeuTheme.colors.background,
        title = { Text(repeatLabel(spec.kind)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("v56_repeat_dialog"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (spec.kind) {
                    RepeatKind.LINEAR -> {
                        slider(stringResource(R.string.v56_repeat_count, spec.count), spec.count.toFloat(), 1f..40f, "v56_repeat_count") { spec = spec.copy(count = it.roundToInt()) }
                        slider(stringResource(R.string.v56_repeat_dx, spec.dx.roundToInt()), spec.dx, -400f..400f, "v56_repeat_dx") { spec = spec.copy(dx = it.roundToInt().toFloat()) }
                        slider(stringResource(R.string.v56_repeat_dy, spec.dy.roundToInt()), spec.dy, -400f..400f, "v56_repeat_dy") { spec = spec.copy(dy = it.roundToInt().toFloat()) }
                    }
                    RepeatKind.GRID -> {
                        slider(stringResource(R.string.v56_repeat_cols, spec.cols), spec.cols.toFloat(), 1f..20f, "v56_repeat_cols") { spec = spec.copy(cols = it.roundToInt()) }
                        slider(stringResource(R.string.v56_repeat_rows, spec.rows), spec.rows.toFloat(), 1f..20f, "v56_repeat_rows") { spec = spec.copy(rows = it.roundToInt()) }
                        slider(stringResource(R.string.v56_repeat_dx, spec.gapX.roundToInt()), spec.gapX, 0f..200f, "v56_repeat_gapx") { spec = spec.copy(gapX = it.roundToInt().toFloat()) }
                        slider(stringResource(R.string.v56_repeat_dy, spec.gapY.roundToInt()), spec.gapY, 0f..200f, "v56_repeat_gapy") { spec = spec.copy(gapY = it.roundToInt().toFloat()) }
                    }
                    RepeatKind.RADIAL -> {
                        slider(stringResource(R.string.v56_repeat_count, spec.count), spec.count.toFloat(), 2f..36f, "v56_repeat_count") { spec = spec.copy(count = it.roundToInt()) }
                        slider(stringResource(R.string.v56_repeat_radius, spec.radius.roundToInt()), spec.radius, 0f..800f, "v56_repeat_radius") { spec = spec.copy(radius = it.roundToInt().toFloat()) }
                    }
                    RepeatKind.MIRROR -> {
                        slider(stringResource(R.string.v56_repeat_angle, spec.angle.roundToInt()), spec.angle, -90f..90f, "v56_repeat_angle") { spec = spec.copy(angle = it.roundToInt().toFloat()) }
                        slider(stringResource(R.string.v56_repeat_dx, spec.gapX.roundToInt()), spec.gapX, 0f..300f, "v56_repeat_gapx") { spec = spec.copy(gapX = it.roundToInt().toFloat()) }
                    }
                    RepeatKind.STEP -> {
                        slider(stringResource(R.string.v56_repeat_count, spec.count), spec.count.toFloat(), 1f..40f, "v56_repeat_count") { spec = spec.copy(count = it.roundToInt()) }
                        slider(stringResource(R.string.v56_repeat_dx, spec.dx.roundToInt()), spec.dx, -400f..400f, "v56_repeat_dx") { spec = spec.copy(dx = it.roundToInt().toFloat()) }
                        slider(stringResource(R.string.v56_repeat_dy, spec.dy.roundToInt()), spec.dy, -400f..400f, "v56_repeat_dy") { spec = spec.copy(dy = it.roundToInt().toFloat()) }
                        slider(stringResource(R.string.v56_repeat_angle, spec.angle.roundToInt()), spec.angle, -180f..180f, "v56_repeat_angle") { spec = spec.copy(angle = it.roundToInt().toFloat()) }
                        slider(stringResource(R.string.v56_repeat_scale, (spec.scale * 100).roundToInt()), spec.scale, 0.5f..1.5f, "v56_repeat_scale") { spec = spec.copy(scale = (it * 100).roundToInt() / 100f) }
                    }
                }
                Text(stringResource(R.string.v56_repeat_live_hint), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.applyRepeat(spec); onClose() }, modifier = Modifier.testTag("v56_repeat_apply")) { Text(stringResource(R.string.ok_button)) } },
        dismissButton = { TextButton(onClick = { viewModel.cancelRepeatPreview(); onClose() }) { Text(stringResource(R.string.cancel_button)) } },
    )
}

/**
 * Elemanlar panelindeki gelişmiş görünüm (yalnız Resim): kontur uç/köşe/sınır/hiza/tire/ok uçları, desen ve serbest
 * gradyan dolgu, nesne karışım modu, ek dolgu ve konturlar. [shape] false: yalnız karışım modu.
 */
@Composable
fun V56AppearanceSection(viewModel: CanvasEditViewModel, shape: Boolean, selectionKey: Any) {
    if (!viewModel.v56(V56Flags.Feature.ADV_APPEARANCE)) return
    // Seçim ya da belge değişince yeniden okunur
    val v = remember(selectionKey) { mutableStateOf(viewModel.selectedVectorStyle()) }
    fun update(coalesce: String? = null, f: (VectorStyle) -> VectorStyle) {
        v.value = f(v.value)
        viewModel.updateVectorStyle(coalesce, f)
    }
    val st = v.value
    NeuSurface(modifier = Modifier.fillMaxWidth().testTag("v56_appearance"), elevation = 5.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.v56_appearance), style = MaterialTheme.typography.titleSmall, color = NeuTheme.colors.accent)
            var blendMenu by remember { mutableStateOf(false) }
            Box {
                NeuChip(stringResource(R.string.v56_object_blend) + ": " + layerBlendLabel(st.blend), st.blend != LayerBlend.NORMAL, { blendMenu = true }, Modifier.testTag("v56_obj_blend"))
                DropdownMenu(expanded = blendMenu, onDismissRequest = { blendMenu = false }, modifier = Modifier.background(NeuTheme.colors.background)) {
                    LayerBlend.entries.forEach { b ->
                        DropdownMenuItem(text = { Text(layerBlendLabel(b)) }, onClick = { blendMenu = false; update { it.copy(blend = b) } }, modifier = Modifier.testTag("v56_obj_blend_${b.name.lowercase()}"))
                    }
                }
            }
            if (!shape) return@Column
            label(stringResource(R.string.v56_cap))
            chipRow {
                listOf(CapStyle.ROUND to R.string.v56_cap_round, CapStyle.BUTT to R.string.v56_cap_butt, CapStyle.SQUARE to R.string.v56_cap_square).forEach { (c, l) ->
                    NeuChip(stringResource(l), st.cap == c, { update { it.copy(cap = c) } }, Modifier.testTag("v56_cap_${c.name.lowercase()}"))
                }
            }
            label(stringResource(R.string.v56_join))
            chipRow {
                listOf(JoinStyle.ROUND to R.string.v56_join_round, JoinStyle.MITER to R.string.v56_join_miter, JoinStyle.BEVEL to R.string.v56_join_bevel).forEach { (j, l) ->
                    NeuChip(stringResource(l), st.join == j, { update { it.copy(join = j) } }, Modifier.testTag("v56_join_${j.name.lowercase()}"))
                }
            }
            if (st.join == JoinStyle.MITER) {
                SliderRow(stringResource(R.string.v56_miter), "${st.miterLimit.roundToInt()}", st.miterLimit, 1f..20f) { x -> update("miter") { it.copy(miterLimit = x.roundToInt().toFloat()) } }
            }
            label(stringResource(R.string.v56_stroke_align))
            chipRow {
                listOf(StrokeAlign.CENTER to R.string.v56_align_center, StrokeAlign.INSIDE to R.string.v56_align_inside, StrokeAlign.OUTSIDE to R.string.v56_align_outside).forEach { (a, l) ->
                    NeuChip(stringResource(l), st.align == a, { update { it.copy(align = a) } }, Modifier.testTag("v56_stroke_align_${a.name.lowercase()}"))
                }
            }
            val dash = st.dash ?: emptyList()
            chipRow {
                NeuChip(stringResource(R.string.v56_dash_on), dash.isNotEmpty(), { update { it.copy(dash = if (dash.isEmpty()) listOf(12f, 8f) else null) } }, Modifier.testTag("v56_dash_toggle"))
            }
            if (dash.size >= 2) {
                SliderRow(stringResource(R.string.v56_dash), "${dash[0].roundToInt()}", dash[0], 1f..80f) { x -> update("dash") { it.copy(dash = listOf(x.roundToInt().toFloat(), dash[1])) } }
                SliderRow(stringResource(R.string.v56_gap), "${dash[1].roundToInt()}", dash[1], 1f..80f) { x -> update("gap") { it.copy(dash = listOf(dash[0], x.roundToInt().toFloat())) } }
            }
            listOf(true, false).forEach { start ->
                label(stringResource(if (start) R.string.v56_arrow_start else R.string.v56_arrow_end))
                chipRow {
                    ArrowKind.entries.forEach { k ->
                        NeuChip(arrowLabel(k), (if (start) st.arrowStart else st.arrowEnd) == k, { update { if (start) it.copy(arrowStart = k) else it.copy(arrowEnd = k) } },
                            Modifier.testTag("v56_arrow_${if (start) "s" else "e"}_${k.name.lowercase()}"))
                    }
                }
            }
            label(stringResource(R.string.v56_pattern_fill))
            chipRow {
                NeuChip(stringResource(R.string.v56_none), st.patternId == null, { update { it.copy(patternId = null) } }, Modifier.testTag("v56_pattern_none"))
                V56Textures.BUILTIN.forEach { id ->
                    NeuChip(textureLabel(id), st.patternId == id, { update { it.copy(patternId = id) } }, Modifier.testTag("v56_pattern_${id.removePrefix(V56Textures.PREFIX)}"))
                }
            }
            if (st.patternId != null) {
                SliderRow(stringResource(R.string.v56_pattern_scale), String.format(java.util.Locale.US, "%.1f×", st.patternScale), st.patternScale, V56Textures.MIN_SCALE..V56Textures.MAX_SCALE) { x ->
                    update("pscale") { it.copy(patternScale = (x * 10).roundToInt() / 10f) }
                }
            }
            FreeGradientRow(st) { f -> update(null, f) }
            PaintLayerList(stringResource(R.string.v56_extra_fills), st.fills, stroke = false, tag = "v56_fills") { list -> update { it.copy(fills = list) } }
            PaintLayerList(stringResource(R.string.v56_extra_strokes), st.strokes, stroke = true, tag = "v56_strokes") { list -> update { it.copy(strokes = list) } }
        }
    }
}

@Composable
private fun arrowLabel(k: ArrowKind): String = stringResource(
    when (k) {
        ArrowKind.NONE -> R.string.v56_none
        ArrowKind.TRIANGLE -> R.string.v56_arrow_triangle
        ArrowKind.OPEN -> R.string.v56_arrow_open
        ArrowKind.CIRCLE -> R.string.v56_arrow_circle
        ArrowKind.BAR -> R.string.v56_arrow_bar
    },
)

private val presetColors = listOf("#1E1E1E", "#FFFFFF", "#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5", "#8E24AA")

@Composable
private fun FreeGradientRow(st: VectorStyle, update: ((VectorStyle) -> VectorStyle) -> Unit) {
    val pts = st.freeGradient
    chipRow {
        NeuChip(stringResource(R.string.v56_free_gradient), pts != null, {
            update { it.copy(freeGradient = if (pts != null) null else listOf(GradientPoint(0.1f, 0.1f, "#E53935"), GradientPoint(0.9f, 0.2f, "#FDD835"), GradientPoint(0.5f, 0.9f, "#1E88E5"))) }
        }, Modifier.testTag("v56_free_gradient"))
    }
    if (pts == null) return
    var picked by remember { mutableIntStateOf(0) }
    chipRow {
        pts.forEachIndexed { i, p -> swatch(p.colorHex, picked == i, "v56_fg_point_$i") { picked = i } }
        if (pts.size < 6) NeuChip(stringResource(R.string.v56_add), false, { update { it.copy(freeGradient = pts + GradientPoint(0.5f, 0.5f, "#FFFFFF")) } }, Modifier.testTag("v56_fg_add"))
        if (pts.size > 2) NeuChip(stringResource(R.string.v56_remove), false, { val i = picked.coerceIn(pts.indices); picked = 0; update { it.copy(freeGradient = pts.filterIndexed { j, _ -> j != i }) } }, Modifier.testTag("v56_fg_remove"))
    }
    val i = picked.coerceIn(pts.indices)
    val p = pts[i]
    fun set(np: GradientPoint) = update { it.copy(freeGradient = pts.mapIndexed { j, q -> if (j == i) np else q }) }
    chipRow { presetColors.forEach { c -> swatch(c, c.equals(p.colorHex, true), "v56_fg_color_${c.drop(1)}") { set(p.copy(colorHex = c)) } } }
    SliderRow("X", "${(p.x * 100).roundToInt()}", p.x, 0f..1f) { set(p.copy(x = it)) }
    SliderRow("Y", "${(p.y * 100).roundToInt()}", p.y, 0f..1f) { set(p.copy(y = it)) }
}

@Composable
private fun PaintLayerList(title: String, list: List<PaintLayer>, stroke: Boolean, tag: String, onChange: (List<PaintLayer>) -> Unit) {
    var picked by remember { mutableIntStateOf(-1) }
    label(title)
    chipRow {
        list.forEachIndexed { i, l -> swatch(l.colorHex, picked == i, "${tag}_$i") { picked = if (picked == i) -1 else i } }
        if (list.size < 6) NeuChip(stringResource(R.string.v56_add), false, {
            onChange(list + PaintLayer(if (stroke) "#FFFFFF" else "#1E88E5", width = if (stroke) 12f else 4f, opacity = if (stroke) 1f else 0.5f)); picked = list.size
        }, Modifier.testTag("${tag}_add"))
    }
    val i = picked
    if (i !in list.indices) return
    val l = list[i]
    fun set(n: PaintLayer) = onChange(list.mapIndexed { j, q -> if (j == i) n else q })
    chipRow {
        presetColors.forEach { c -> swatch(c, c.equals(l.colorHex, true), "${tag}_color_${c.drop(1)}") { set(l.copy(colorHex = c)) } }
    }
    if (stroke) SliderRow(stringResource(R.string.style_stroke_width), "${l.width.roundToInt()} px", l.width, 1f..60f) { set(l.copy(width = it.roundToInt().toFloat())) }
    SliderRow(stringResource(R.string.style_fill_opacity), "%${(l.opacity * 100).roundToInt()}", l.opacity, 0.05f..1f) { set(l.copy(opacity = it)) }
    chipRow {
        if (i > 0) NeuChip("↑", false, { onChange(list.toMutableList().also { it.add(i - 1, it.removeAt(i)) }); picked = i - 1 }, Modifier.testTag("${tag}_up"))
        if (i < list.size - 1) NeuChip("↓", false, { onChange(list.toMutableList().also { it.add(i + 1, it.removeAt(i)) }); picked = i + 1 }, Modifier.testTag("${tag}_down"))
        NeuChip(stringResource(R.string.v56_remove), false, { onChange(list.filterIndexed { j, _ -> j != i }); picked = -1 }, Modifier.testTag("${tag}_remove"))
    }
}
