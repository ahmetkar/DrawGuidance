package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.EffectKind
import com.karapp.drawguidance.data.model.V56Flags
import com.karapp.drawguidance.data.model.V56PathOps.PathOp
import com.karapp.drawguidance.data.model.VectorEffect
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool
import com.karapp.drawguidance.ui.screens.canvasedit.VectorSelection
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

/*
 * v56 aşama 9C (2E, yalnız Resim): Nesne menüsünün Yol işlemleri / Efektler / Yol düzenle bölümleri, şekil oluşturucu
 * ve bıçak alt panelleri, düğüm türü ve yolu uzat.
 */

@Composable
private fun row(content: @Composable () -> Unit) =
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }

/** Nesne menüsünde: Yol işlemleri (Pathfinder) ve şekil oluşturucu. */
@Composable
fun V56PathfinderSection(viewModel: CanvasEditViewModel, onDismiss: () -> Unit) {
    if (!viewModel.v56(V56Flags.Feature.ADV_PATHFINDER)) return
    val count = viewModel.pathOperableCount()
    Text(stringResource(R.string.v56_pathfinder), style = MaterialTheme.typography.titleSmall)
    if (count < 2) Text(stringResource(R.string.v56_pathfinder_hint), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
    val ops = listOf(
        PathOp.UNITE to R.string.v56_pf_unite, PathOp.MINUS_FRONT to R.string.v56_pf_minus_front, PathOp.INTERSECT to R.string.v56_pf_intersect,
        PathOp.EXCLUDE to R.string.v56_pf_exclude, PathOp.DIVIDE to R.string.v56_pf_divide, PathOp.TRIM to R.string.v56_pf_trim,
        PathOp.MERGE to R.string.v56_pf_merge, PathOp.CROP to R.string.v56_pf_crop, PathOp.OUTLINE to R.string.v56_pf_outline,
    )
    row {
        ops.forEach { (op, label) ->
            val enabled = count >= 2 || ((op == PathOp.OUTLINE || op == PathOp.DIVIDE) && count >= 1)
            NeuChip(stringResource(label), false, { if (enabled && viewModel.pathOp(op)) onDismiss() }, Modifier.testTag("v56_pf_${op.name.lowercase()}"))
        }
        NeuChip(stringResource(R.string.v56_shape_builder), false, { if (viewModel.startShapeBuilder()) onDismiss() }, Modifier.testTag("v56_shape_builder"))
    }
}

/** Nesne menüsünde: Yol düzenle (konturu yola çevir, bıçak, yolu ayır, kapat, ters çevir). */
@Composable
fun V56PathEditSection(viewModel: CanvasEditViewModel, onDismiss: () -> Unit) {
    if (!viewModel.v56(V56Flags.Feature.ADV_NODES)) return
    Text(stringResource(R.string.v56_path_edit), style = MaterialTheme.typography.titleSmall)
    row {
        NeuChip(stringResource(R.string.v56_outline_stroke), false, { if (viewModel.outlineStroke()) onDismiss() }, Modifier.testTag("v56_outline_stroke"))
        NeuChip(stringResource(R.string.v56_knife), false, { viewModel.startKnife(); onDismiss() }, Modifier.testTag("v56_knife"))
        NeuChip(stringResource(R.string.v56_path_separate), false, { if (viewModel.separatePaths()) onDismiss() }, Modifier.testTag("v56_path_separate"))
        NeuChip(stringResource(R.string.v56_path_close), false, { if (viewModel.closeSelectedPaths()) onDismiss() }, Modifier.testTag("v56_path_close"))
        NeuChip(stringResource(R.string.v56_path_reverse), false, { if (viewModel.reverseSelectedPaths()) onDismiss() }, Modifier.testTag("v56_path_reverse"))
    }
}

/** Nesne › Efektler'e eklenenler: öteleme, kıvırma, pürüz, zikzak, dalga (değiştirilebilir liste) ve zarf bozma. */
@Composable
fun V56EffectChips(viewModel: CanvasEditViewModel, onDismiss: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    NeuChip(stringResource(R.string.v56_effects_list), false, { if (viewModel.canApplyEffects()) open = true }, Modifier.testTag("v56_effects_list"))
    NeuChip(stringResource(R.string.v56_envelope), false, { if (viewModel.envelopeDistort()) onDismiss() }, Modifier.testTag("v56_envelope"))
    if (open) V56EffectsDialog(viewModel) { open = false }
}

fun effectLabel(kind: EffectKind): Int = when (kind) {
    EffectKind.OFFSET -> R.string.v56_fx_offset
    EffectKind.BEND -> R.string.v56_fx_bend
    EffectKind.ROUGHEN -> R.string.v56_fx_roughen
    EffectKind.ZIGZAG -> R.string.v56_fx_zigzag
    EffectKind.WAVE -> R.string.v56_fx_wave
}

/** Efekt listesi: ekle, ayarla, kaldır; önizlemeli, Uygula tek geri alma adımı. */
@Composable
fun V56EffectsDialog(viewModel: CanvasEditViewModel, onClose: () -> Unit) {
    var list by remember { mutableStateOf(viewModel.selectedEffects()) }
    LaunchedEffect(list) { viewModel.previewEffects(list) }
    val muted = NeuTheme.colors.contentMuted
    AlertDialog(
        onDismissRequest = { viewModel.cancelEffectsPreview(); onClose() }, containerColor = NeuTheme.colors.background,
        title = { Text(stringResource(R.string.v56_effects_list)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("v56_effects_dialog"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                row {
                    EffectKind.entries.forEach { k ->
                        NeuChip("+ " + stringResource(effectLabel(k)), false, {
                            list = list + when (k) {
                                EffectKind.OFFSET -> VectorEffect(k, amount = 8f)
                                EffectKind.BEND -> VectorEffect(k, amount = 0.3f)
                                else -> VectorEffect(k, size = 6f, detail = 6f, seed = list.size + 1)
                            }
                        }, Modifier.testTag("v56_fx_add_${k.name.lowercase()}"))
                    }
                }
                if (list.isEmpty()) Text(stringResource(R.string.v56_effects_empty), style = MaterialTheme.typography.bodySmall, color = muted)
                list.forEachIndexed { i, e ->
                    fun set(n: VectorEffect) { list = list.toMutableList().also { it[i] = n } }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(effectLabel(e.kind)), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { list = list.filterIndexed { j, _ -> j != i } }, modifier = Modifier.testTag("v56_fx_remove_$i")) { Text(stringResource(R.string.v56_remove)) }
                    }
                    when (e.kind) {
                        EffectKind.OFFSET -> {
                            Text(stringResource(R.string.v56_fx_distance, e.amount.roundToInt()), style = MaterialTheme.typography.labelSmall, color = muted)
                            NeuSlider(e.amount, { set(e.copy(amount = it.roundToInt().toFloat())) }, -60f..60f, Modifier.widthIn(min = 220.dp))
                        }
                        EffectKind.BEND -> {
                            Text(stringResource(R.string.v56_fx_amount) + " · ${(e.amount * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = muted)
                            NeuSlider(e.amount, { set(e.copy(amount = it)) }, -1f..1f, Modifier.widthIn(min = 220.dp))
                        }
                        else -> {
                            Text(stringResource(R.string.v56_fx_size, e.size.roundToInt()), style = MaterialTheme.typography.labelSmall, color = muted)
                            NeuSlider(e.size, { set(e.copy(size = it.roundToInt().toFloat())) }, 1f..60f, Modifier.widthIn(min = 220.dp))
                            Text(stringResource(R.string.v56_fx_detail, e.detail.roundToInt()), style = MaterialTheme.typography.labelSmall, color = muted)
                            NeuSlider(e.detail, { set(e.copy(detail = it.roundToInt().toFloat())) }, 1f..40f, Modifier.widthIn(min = 220.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.applyEffects(list); onClose() }, modifier = Modifier.testTag("v56_fx_list_apply")) { Text(stringResource(R.string.ok_button)) } },
        dismissButton = { TextButton(onClick = { viewModel.cancelEffectsPreview(); onClose() }) { Text(stringResource(R.string.cancel_button)) } },
    )
}

/** Bıçak ve şekil oluşturucu alt panelleri (açıkken). */
@Composable
fun V56PathBars(viewModel: CanvasEditViewModel) {
    val knife by viewModel.knife.collectAsState()
    val builder by viewModel.shapeBuilder.collectAsState()
    if (!knife && builder == null) return
    val muted = NeuTheme.colors.contentMuted
    NeuSurface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag(if (knife) "v56_knife_bar" else "v56_builder_bar"), elevation = 8.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (knife) {
                Text(stringResource(R.string.v56_knife_hint), style = MaterialTheme.typography.bodySmall, color = muted)
                row { NeuChip(stringResource(R.string.v56_done), false, viewModel::stopKnife, Modifier.testTag("v56_knife_done")) }
            } else builder?.let { b ->
                Text(stringResource(R.string.v56_builder_hint), style = MaterialTheme.typography.bodySmall, color = muted)
                row {
                    NeuChip(stringResource(R.string.v56_builder_apply, b.merge.size, b.remove.size), false, { viewModel.applyShapeBuilder() }, Modifier.testTag("v56_builder_apply"))
                    NeuChip(stringResource(R.string.cancel_button), false, viewModel::cancelShapeBuilder, Modifier.testTag("v56_builder_cancel"))
                }
            }
        }
    }
}

/** Şekil düzenlemenin nokta paneli: alt araçlar ve (Resim modunda) düğüm türü, yolu uzat. */
@Composable
fun V56NodePanel(viewModel: CanvasEditViewModel, tool: ShapeTool, tools: @Composable () -> Unit) {
    if (!viewModel.bezierOn() || tool != ShapeTool.NODES) return tools()
    val smooth by viewModel.smoothNodes.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tools()
        row {
            NeuChip(stringResource(R.string.v56_node_corner), !smooth, { viewModel.setNodeSmooth(false) }, Modifier.testTag("v56_node_corner"))
            NeuChip(stringResource(R.string.v56_node_smooth), smooth, { viewModel.setNodeSmooth(true) }, Modifier.testTag("v56_node_smooth"))
            NeuChip(stringResource(R.string.v56_path_extend), false, { viewModel.extendFromSelectedNode() }, Modifier.testTag("v56_path_extend"))
        }
        Text(stringResource(R.string.v56_node_hint), style = MaterialTheme.typography.labelSmall, color = NeuTheme.colors.contentMuted, maxLines = 1)
    }
}

/** Tuval katmanı için seçili düğümün tutamakları. */
@Composable
fun v56Handles(viewModel: CanvasEditViewModel, selection: VectorSelection, mode: EditMode): List<Pair<Offset, Offset>> {
    if (mode != EditMode.SHAPE_EDIT) return emptyList()
    val strokes = viewModel.canvas.collectAsState().value?.strokes
    return remember(selection, strokes) { viewModel.selectedHandles() }
}

@Composable
fun v56Builder(viewModel: CanvasEditViewModel): CanvasEditViewModel.ShapeBuilderUi? = viewModel.shapeBuilder.collectAsState().value
