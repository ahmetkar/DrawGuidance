package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.AutoAwesome
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.SymmetryKind
import com.karapp.drawguidance.data.model.SymmetrySpec
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

@Composable
fun symmetryLabel(kind: SymmetryKind): String = stringResource(
    when (kind) {
        SymmetryKind.VERTICAL -> R.string.v56_sym_vertical
        SymmetryKind.HORIZONTAL -> R.string.v56_sym_horizontal
        SymmetryKind.QUAD -> R.string.v56_sym_quad
        SymmetryKind.RADIAL -> R.string.v56_sym_radial
    },
)

/** Kılavuzlar penceresindeki "Simetri" seçimi (kapalı + dört tür). */
@Composable
fun V56SymmetryPicker(current: SymmetryKind?, onPick: (SymmetryKind?) -> Unit) {
    Column(Modifier.testTag("v56_sym_picker"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.v56_symmetry), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuChip(stringResource(R.string.v56_sym_off), current == null, { onPick(null) }, Modifier.testTag("v56_sym_off"))
            SymmetryKind.entries.forEach { k ->
                NeuChip(symmetryLabel(k), current == k, { onPick(k) }, Modifier.testTag("v56_sym_${k.name.lowercase()}"))
            }
        }
    }
}

/**
 * v56 aşama 8 alt panel (simetri açıkken, Çiz modunda): tür, kol sayısı (radyalde 2–16), eksen açısı, merkez, canlı
 * ayna ve kapat. Ayar tuvalle kaydedilir.
 */
@Composable
fun V56SymmetryBar(viewModel: CanvasEditViewModel, spec: SymmetrySpec, width: Int, height: Int, modifier: Modifier = Modifier) {
    val muted = NeuTheme.colors.contentMuted
    NeuSurface(modifier = modifier.fillMaxWidth().testTag("v56_sym_bar"), elevation = 8.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SymmetryKind.entries.forEach { k ->
                    NeuChip(symmetryLabel(k), spec.kind == k, { viewModel.setSymmetry(k) }, Modifier.testTag("v56_symbar_${k.name.lowercase()}"))
                }
                NeuChip(stringResource(R.string.v56_sym_live), spec.live, { viewModel.updateSymmetry(spec.copy(live = !spec.live)) }, Modifier.testTag("v56_sym_live"))
                NeuChip(stringResource(R.string.v56_sym_center), false, { viewModel.updateSymmetry(spec.copy(cx = width / 2f, cy = height / 2f, angle = 0f)) }, Modifier.testTag("v56_sym_center"))
                NeuChip(stringResource(R.string.v56_sym_close), false, { viewModel.setSymmetry(null) }, Modifier.testTag("v56_sym_close"))
            }
            @Composable
            fun slider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, tag: String, set: (Float, Boolean) -> Unit) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.widthIn(min = 72.dp))
                    NeuSlider(value, { set(it, false) }, range, Modifier.weight(1f).testTag(tag), onValueChangeFinished = { set(value, true) })
                }
            }
            if (spec.kind == SymmetryKind.RADIAL) {
                slider(stringResource(R.string.v56_sym_arms, spec.arms), spec.arms.toFloat(), 2f..16f, "v56_sym_arms") { v, p -> viewModel.updateSymmetry(spec.copy(arms = v.roundToInt()), p) }
            }
            slider(stringResource(R.string.v56_sym_angle, spec.angle.roundToInt()), spec.angle, -90f..90f, "v56_sym_angle") { v, p -> viewModel.updateSymmetry(spec.copy(angle = v.roundToInt().toFloat()), p) }
            slider(stringResource(R.string.v56_sym_cx), spec.cx, 0f..width.toFloat(), "v56_sym_cx") { v, p -> viewModel.updateSymmetry(spec.copy(cx = v), p) }
            slider(stringResource(R.string.v56_sym_cy), spec.cy, 0f..height.toFloat(), "v56_sym_cy") { v, p -> viewModel.updateSymmetry(spec.copy(cy = v), p) }
        }
    }
}

/** v56 aşama 8 "Desen yap": seçim aralıkla tuval boyunca döşenir. */
@Composable
fun V56PatternDialog(viewModel: CanvasEditViewModel, onDismiss: () -> Unit) {
    var gap by remember { mutableFloatStateOf(20f) }
    val count = viewModel.patternCount(gap)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NeuTheme.colors.background,
        title = { Text(stringResource(R.string.v56_pattern)) },
        text = {
            Column(Modifier.testTag("v56_pattern_dialog"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.v56_pattern_gap, gap.roundToInt()))
                NeuSlider(gap, { gap = it.roundToInt().toFloat() }, 0f..200f, Modifier.widthIn(min = 220.dp).testTag("v56_pattern_gap"))
                Text(stringResource(R.string.v56_pattern_count, minOf(count, 200)), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.makePattern(gap); onDismiss() }, modifier = Modifier.testTag("v56_pattern_apply")) { Text(stringResource(R.string.ok_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) } },
    )
}

/** Basit resim ve çocuk modunda üst menüdeki simetri düğmesi (aç/kapa; ayarlar alt panelde). */
@Composable
fun v56SymmetrySimpleTool(viewModel: CanvasEditViewModel, canvas: com.karapp.drawguidance.data.model.CanvasUi?): SimpleTool? {
    if (!viewModel.v56(com.karapp.drawguidance.data.model.V56Flags.Feature.SYMMETRY) || canvas == null || canvas.kind.isNotes) return null
    val on = canvas.guide.symmetry != null
    return SimpleTool("symmetry", androidx.compose.material.icons.Icons.Filled.Flip, stringResource(R.string.v56_symmetry), on, {
        viewModel.setSymmetry(if (on) null else SymmetryKind.VERTICAL)
    })
}

/** Simetri alt paneli: simetri açıkken çizim modlarında. */
@Composable
fun V56SymmetryBottom(viewModel: CanvasEditViewModel, canvas: com.karapp.drawguidance.data.model.CanvasUi, mode: com.karapp.drawguidance.ui.screens.canvasedit.EditMode) {
    val sym = canvas.guide.symmetry ?: return
    if (!viewModel.v56(com.karapp.drawguidance.data.model.V56Flags.Feature.SYMMETRY)) return
    if (mode != com.karapp.drawguidance.ui.screens.canvasedit.EditMode.DRAW && mode != com.karapp.drawguidance.ui.screens.canvasedit.EditMode.PEN &&
        mode != com.karapp.drawguidance.ui.screens.canvasedit.EditMode.PAINT) return
    V56SymmetryBar(viewModel, sym, canvas.width, canvas.height, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
}

/** v56 aşama 7 Ekle menüsünün hazır ayarları: ışık katmanı, gölge katmanı, parıltı fırçası. */
@Composable
fun v56PresetAddItems(viewModel: CanvasEditViewModel): List<AddMenuItem> {
    if (!viewModel.v56(com.karapp.drawguidance.data.model.V56Flags.Feature.PRESETS)) return emptyList()
    val icons = androidx.compose.material.icons.Icons.Filled
    return listOf(
        AddMenuItem("light_layer", stringResource(R.string.v56_light_layer), icons.WbSunny) { viewModel.addPresetLayer(com.karapp.drawguidance.data.model.V56Presets.Kind.LIGHT) },
        AddMenuItem("shadow_layer", stringResource(R.string.v56_shadow_layer), icons.DarkMode) { viewModel.addPresetLayer(com.karapp.drawguidance.data.model.V56Presets.Kind.SHADOW) },
        AddMenuItem("glitter_brush", stringResource(R.string.v56_glitter_brush), icons.AutoAwesome) { viewModel.useGlitterBrush() },
    )
}
