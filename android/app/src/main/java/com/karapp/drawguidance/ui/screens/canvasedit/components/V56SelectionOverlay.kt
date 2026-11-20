package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.V56Selection
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

/**
 * v56 tuval üstü seçim çubukları: seçim alanı varken (üstte) ters çevir, yumuşat, genişlet/daralt, doldur, kaldır;
 * sihirli değnek açıkken (altta) ölçüt ve tolerans.
 */
@Composable
fun V56SelectionOverlay(viewModel: CanvasEditViewModel, mode: EditMode, pick: CanvasPick) {
    val area by viewModel.area.collectAsState()
    val criterion by viewModel.wandCriterion.collectAsState()
    val tolerance by viewModel.wandTolerance.collectAsState()
    val wand = pick == CanvasPick.MAGIC_WAND && mode == EditMode.SELECT
    if (area == null && !wand) return
    Box(Modifier.fillMaxSize()) {
        area?.let { a ->
            Bar(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).testTag("v56_area_bar")) {
                Text(stringResource(R.string.v56_area_title), style = MaterialTheme.typography.labelMedium, color = NeuTheme.colors.contentMuted)
                NeuChip(stringResource(R.string.v56_area_invert), a.inverted, { viewModel.invertArea() }, Modifier.testTag("v56_area_invert"))
                NeuChip(stringResource(R.string.v56_area_feather, a.feather.roundToInt()), a.feather > 0f, { viewModel.cycleAreaFeather() }, Modifier.testTag("v56_area_feather"))
                NeuChip(stringResource(R.string.v56_area_grow), false, { viewModel.growArea(8f) }, Modifier.testTag("v56_area_grow"))
                NeuChip(stringResource(R.string.v56_area_shrink), false, { viewModel.growArea(-8f) }, Modifier.testTag("v56_area_shrink"))
                if (!a.inverted && viewModel.v56(com.karapp.drawguidance.data.model.V56Flags.Feature.LASSO_FILL)) {
                    NeuChip(stringResource(R.string.v56_area_fill), false, { viewModel.lassoFill() }, Modifier.testTag("v56_area_fill"))
                }
                NeuChip(stringResource(R.string.v56_area_clear), false, { viewModel.clearArea() }, Modifier.testTag("v56_area_clear"))
            }
        }
        if (wand) {
            Bar(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).testTag("v56_wand_bar")) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        WandChip(R.string.v56_wand_color, V56Selection.WandCriterion.COLOR, criterion, viewModel)
                        WandChip(R.string.v56_wand_stroke, V56Selection.WandCriterion.STROKE, criterion, viewModel)
                        WandChip(R.string.v56_wand_appearance, V56Selection.WandCriterion.APPEARANCE, criterion, viewModel)
                    }
                    Text(stringResource(R.string.v56_wand_tolerance, (tolerance * 100).roundToInt()), style = MaterialTheme.typography.labelMedium, color = NeuTheme.colors.contentMuted)
                    NeuSlider(tolerance, { viewModel.setWandTolerance(it) }, 0f..1f, Modifier.widthIn(min = 200.dp, max = 320.dp).testTag("v56_wand_tolerance"))
                }
            }
        }
    }
}

@Composable
private fun WandChip(label: Int, value: V56Selection.WandCriterion, current: V56Selection.WandCriterion, viewModel: CanvasEditViewModel) {
    NeuChip(stringResource(label), value == current, { viewModel.setWandCriterion(value) }, Modifier.testTag("v56_wand_${value.name.lowercase()}"))
}

@Composable
private fun Bar(modifier: Modifier, content: @Composable () -> Unit) {
    val colors = NeuTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .padding(horizontal = 8.dp)
            .clip(shape)
            .background(colors.background.copy(alpha = 0.92f))
            .border(1.dp, colors.contentMuted.copy(alpha = 0.22f), shape)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
