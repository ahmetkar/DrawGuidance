package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuSheet
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.drawing.scene.SceneAnalyzer
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.state.ObjectSceneController
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

/**
 * v43 "Arkaplan ile Nesneyi Uyumla" ayar/onay penceresi: gölge, renk/ışık, kenar yumuşatma, konum/ölçek ve yoğunluk.
 * Değişiklikler tuvalde önizlenir; Uygula tek geri alınabilir adımdır, İptal özgün hâle döndürür.
 */
@Composable
fun HarmonizeSheet(
    draft: ObjectSceneController.HarmonyDraft,
    onOptions: (SceneAnalyzer.Options) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = NeuTheme.colors
    val o = draft.options
    NeuSheet(onCancel) {
        Column(
            Modifier.fillMaxWidth().testTag("harmonize_sheet").verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.v43_harmonize_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.v43_harmonize_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            when {
                draft.busy -> Text(stringResource(R.string.v43_analyzing), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("harmonize_busy"))
                draft.analysis == null -> Text(stringResource(R.string.v43_analyze_failed), style = MaterialTheme.typography.bodyMedium, color = colors.danger)
            }
            Option(R.string.v43_opt_shadow, o.shadow, "harmonize_shadow") { onOptions(o.copy(shadow = it)) }
            Option(R.string.v43_opt_color, o.color, "harmonize_color") { onOptions(o.copy(color = it)) }
            Option(R.string.v43_opt_edge, o.edge, "harmonize_edge") { onOptions(o.copy(edge = it)) }
            Option(R.string.v43_opt_position, o.position, "harmonize_position") { onOptions(o.copy(position = it)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.v43_intensity), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                NeuSlider(o.intensity, { onOptions(o.copy(intensity = it)) }, 0f..1f, Modifier.weight(1f).testTag("harmonize_intensity"))
                Text("%${(o.intensity * 100).roundToInt()}", style = MaterialTheme.typography.labelMedium, color = colors.contentMuted, modifier = Modifier.width(44.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeuButton(stringResource(R.string.v43_cancel), onCancel, Modifier.weight(1f).testTag("harmonize_cancel"))
                NeuButton(stringResource(R.string.v43_apply), onApply, Modifier.weight(1f).testTag("harmonize_apply"), primary = true, enabled = !draft.busy && draft.analysis != null)
            }
            if (draft.hadHarmony) {
                NeuButton(stringResource(R.string.v43_harmony_remove), onRemove, Modifier.fillMaxWidth().testTag("harmonize_remove"))
            }
        }
    }
}

@Composable
private fun Option(label: Int, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        NeuSwitch(checked, onChange, Modifier.testTag(tag))
    }
}

/**
 * v43 "Nesneyi Mekâna Yerleştir": bulunan ufuk/kaçış noktasına göre ayak zemine oturur ve boy perspektife göre
 * ayarlanır. Bulunamazsa ya da yanlışsa ufuk, ayak hizası ve ölçek elle verilir. Önizleme tuvalde.
 */
@Composable
fun PlaceSheet(
    draft: ObjectSceneController.PlaceDraft,
    onManual: (horizonY: Float?, footY: Float?, scaleMul: Float?) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = NeuTheme.colors
    val h = draft.canvasHeight.coerceAtLeast(1f)
    NeuSheet(onCancel) {
        Column(
            Modifier.fillMaxWidth().testTag("place_sheet").verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.v43_place_title), style = MaterialTheme.typography.headlineSmall)
            val status = when {
                draft.busy -> stringResource(R.string.v43_analyzing)
                draft.perspective?.vanishing != null -> stringResource(R.string.v43_place_found_vp)
                (draft.perspective?.confidence ?: 0f) >= 0.5f -> stringResource(R.string.v43_place_found_line)
                else -> stringResource(R.string.v43_place_default)
            }
            Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("place_status"))
            if (draft.references > 0) {
                Text(stringResource(R.string.v43_place_refs, draft.references), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            }
            Text(stringResource(R.string.v43_place_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            Labeled(R.string.v43_horizon, "%${(draft.horizonY / h * 100).roundToInt()}") {
                NeuSlider(draft.horizonY / h, { onManual(it * h, draft.footY, null) }, 0f..1f, Modifier.fillMaxWidth().testTag("place_horizon"))
            }
            val foot = draft.footY ?: draft.result?.footY ?: (h * 0.8f)
            Labeled(R.string.v43_ground, "%${(foot / h * 100).roundToInt()}") {
                NeuSlider(foot / h, { onManual(null, it * h, null) }, 0f..1f, Modifier.fillMaxWidth().testTag("place_foot"))
            }
            Labeled(R.string.v43_scale, "×${"%.2f".format(draft.scaleMul)}") {
                NeuSlider(draft.scaleMul, { onManual(null, draft.footY, it) }, 0.5f..2f, Modifier.fillMaxWidth().testTag("place_scale"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeuButton(stringResource(R.string.v43_cancel), onCancel, Modifier.weight(1f).testTag("place_cancel"))
                NeuButton(stringResource(R.string.v43_apply), onApply, Modifier.weight(1f).testTag("place_apply"), primary = true, enabled = !draft.busy)
            }
        }
    }
}

@Composable
private fun Labeled(label: Int, value: String, content: @Composable () -> Unit) {
    val colors = NeuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
        }
        content()
    }
}
