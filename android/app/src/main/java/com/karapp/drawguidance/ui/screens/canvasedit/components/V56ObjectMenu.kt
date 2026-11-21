package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.V56Arrange
import com.karapp.drawguidance.data.model.V56Flags
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

/**
 * v56 aşama 6A Nesne menüsü (2E-1, yalnız Resim): Hizala ve dağıt (referans: kâğıt / seçim / ana nesne), Efektler (girdap,
 * büzüştür, şişir, yansıma oluştur), Tekrarla (dönüşümü tekrarla / çoğalt).
 */
@Composable
fun V56ObjectMenu(viewModel: CanvasEditViewModel, onDismiss: () -> Unit) {
    var ref by remember { mutableStateOf(V56Arrange.Ref.SELECTION) }
    var effect by remember { mutableStateOf<V56Arrange.Effect?>(null) }
    val muted = NeuTheme.colors.contentMuted
    @Composable
    fun row(content: @Composable () -> Unit) =
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NeuTheme.colors.background,
        title = { Text(stringResource(R.string.v56_object_menu)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("v56_object_menu"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (viewModel.v56(V56Flags.Feature.ADV_ALIGN)) {
                    Text(stringResource(R.string.v56_align_title), style = MaterialTheme.typography.titleSmall)
                    row {
                        listOf(V56Arrange.Ref.PAPER to R.string.v56_align_ref_paper, V56Arrange.Ref.SELECTION to R.string.v56_align_ref_selection, V56Arrange.Ref.KEY to R.string.v56_align_ref_key)
                            .forEach { (r, label) -> NeuChip(stringResource(label), ref == r, { ref = r }, Modifier.testTag("v56_align_ref_${r.name.lowercase()}")) }
                    }
                    if (ref == V56Arrange.Ref.KEY) Text(stringResource(R.string.v56_align_key_hint), style = MaterialTheme.typography.bodySmall, color = muted)
                    row {
                        listOf(
                            V56Arrange.Align.LEFT to R.string.v56_align_left, V56Arrange.Align.HCENTER to R.string.v56_align_hcenter,
                            V56Arrange.Align.RIGHT to R.string.v56_align_right, V56Arrange.Align.TOP to R.string.v56_align_top,
                            V56Arrange.Align.VCENTER to R.string.v56_align_vcenter, V56Arrange.Align.BOTTOM to R.string.v56_align_bottom,
                        ).forEach { (a, label) -> NeuChip(stringResource(label), false, { viewModel.alignObjects(a, ref) }, Modifier.testTag("v56_align_${a.name.lowercase()}")) }
                    }
                    row {
                        listOf(
                            V56Arrange.Distribute.HORIZONTAL to R.string.v56_distribute_h, V56Arrange.Distribute.VERTICAL to R.string.v56_distribute_v,
                            V56Arrange.Distribute.SPACE_H to R.string.v56_space_h, V56Arrange.Distribute.SPACE_V to R.string.v56_space_v,
                        ).forEach { (d, label) -> NeuChip(stringResource(label), false, { viewModel.distributeObjects(d) }, Modifier.testTag("v56_distribute_${d.name.lowercase()}")) }
                    }
                }
                if (viewModel.v56(V56Flags.Feature.ADV_EFFECTS)) {
                    Text(stringResource(R.string.v56_effects_title), style = MaterialTheme.typography.titleSmall)
                    row {
                        listOf(V56Arrange.Effect.TWIRL to R.string.v56_fx_twirl, V56Arrange.Effect.PUCKER to R.string.v56_fx_pucker, V56Arrange.Effect.BLOAT to R.string.v56_fx_bloat)
                            .forEach { (e, label) -> NeuChip(stringResource(label), false, { if (viewModel.canEffect()) effect = e }, Modifier.testTag("v56_fx_${e.name.lowercase()}")) }
                        if (viewModel.v56(V56Flags.Feature.REFLECTION_LIQUIFY)) {
                            NeuChip(stringResource(R.string.v56_reflection), false, { viewModel.reflectSelection(); onDismiss() }, Modifier.testTag("v56_fx_reflection"))
                        }
                    }
                }
                if (viewModel.v56(V56Flags.Feature.ADV_TRANSFORM) || viewModel.v56(V56Flags.Feature.ADV_REPEAT)) {
                    Text(stringResource(R.string.v56_repeat_title), style = MaterialTheme.typography.titleSmall)
                    if (viewModel.v56(V56Flags.Feature.ADV_TRANSFORM)) row {
                        NeuChip(stringResource(R.string.v56_tf_repeat), false, { viewModel.repeatTransform(false) }, Modifier.testTag("v56_obj_repeat"))
                        NeuChip(stringResource(R.string.v56_tf_duplicate), false, { viewModel.repeatTransform(true) }, Modifier.testTag("v56_obj_duplicate"))
                    }
                    // v56 aşama 8A canlı tekrar: doğrusal, ızgara, dairesel, aynalı, adımla çoğalt
                    V56RepeatSection(viewModel)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
    effect?.let { e -> V56EffectDialog(viewModel, e) { effect = null } }
}

/** Efekt miktarı (önizlemeli): Uygula tek geri alma adımı, İptal belgeyi değiştirmez. */
@Composable
private fun V56EffectDialog(viewModel: CanvasEditViewModel, effect: V56Arrange.Effect, onClose: () -> Unit) {
    var amount by remember(effect) { mutableFloatStateOf(V56Arrange.default(effect)) }
    LaunchedEffect(effect, amount) { viewModel.previewEffect(effect, amount) }
    val label = stringResource(when (effect) { V56Arrange.Effect.TWIRL -> R.string.v56_fx_twirl; V56Arrange.Effect.PUCKER -> R.string.v56_fx_pucker; V56Arrange.Effect.BLOAT -> R.string.v56_fx_bloat })
    AlertDialog(
        onDismissRequest = { viewModel.cancelEffect(); onClose() }, containerColor = NeuTheme.colors.background,
        title = { Text(label) },
        text = {
            Column(Modifier.testTag("v56_fx_dialog"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.v56_fx_amount) + " · " + if (effect == V56Arrange.Effect.TWIRL) "${amount.roundToInt()}°" else "${(amount * 100).roundToInt()}%")
                NeuSlider(amount, { amount = it }, V56Arrange.range(effect), Modifier.widthIn(min = 220.dp).testTag("v56_fx_amount"))
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.applyEffect(effect, amount); onClose() }, modifier = Modifier.testTag("v56_fx_apply")) { Text(stringResource(R.string.ok_button)) } },
        dismissButton = { TextButton(onClick = { viewModel.cancelEffect(); onClose() }) { Text(stringResource(R.string.cancel_button)) } },
    )
}
