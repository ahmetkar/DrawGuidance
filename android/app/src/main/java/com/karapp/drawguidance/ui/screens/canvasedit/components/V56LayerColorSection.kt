package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.ColorAdjust
import com.karapp.drawguidance.data.model.ColorAdjustOps
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** Renk ayarı sekmeleri. */
enum class ColorAdjustTab { HSL, BALANCE, CURVES }

/**
 * v56 aşama 7 (2D-4): renk ayarı denetimleri. Özellikler penceresinde "Katman renkleri" ([layerId] etkin katman) ve
 * seçimin "Renk ayarları" penceresinde ([layerId] null: seçili elemanlar) aynı denetimler kullanılır. Kaydırırken canlı
 * önizleme; Uygula tek geri alma adımı, Sıfırla ayarları sıfırlar.
 */
@Composable
fun V56ColorAdjustControls(
    viewModel: CanvasEditViewModel,
    layerId: String?,
    adj: ColorAdjust,
    onChange: (ColorAdjust) -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = NeuTheme.colors.contentMuted
    var tab by remember { mutableStateOf(ColorAdjustTab.HSL) }
    var range by remember { mutableIntStateOf(1) }
    var channel by remember { mutableIntStateOf(0) }
    LaunchedEffect(adj, layerId) { viewModel.previewColorAdjust(adj, layerId) }
    @Composable
    fun row(content: @Composable () -> Unit) =
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    @Composable
    fun slider(label: String, value: Float, min: Float, max: Float, tag: String, set: (Float) -> Unit) {
        Text("$label · ${value.roundToInt()}", style = MaterialTheme.typography.bodySmall, color = muted)
        NeuSlider(value, { set(it.roundToInt().toFloat()) }, min..max, Modifier.fillMaxWidth().widthIn(min = 200.dp).testTag(tag))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        row {
            NeuChip(stringResource(R.string.v56_ca_hsl), tab == ColorAdjustTab.HSL, { tab = ColorAdjustTab.HSL }, Modifier.testTag("v56_ca_tab_hsl"))
            NeuChip(stringResource(R.string.v56_ca_balance), tab == ColorAdjustTab.BALANCE, { tab = ColorAdjustTab.BALANCE }, Modifier.testTag("v56_ca_tab_balance"))
            NeuChip(stringResource(R.string.v56_ca_curves), tab == ColorAdjustTab.CURVES, { tab = ColorAdjustTab.CURVES }, Modifier.testTag("v56_ca_tab_curves"))
        }
        when (tab) {
            ColorAdjustTab.HSL -> {
                slider(stringResource(R.string.v56_ca_hue), adj.hue, -180f, 180f, "v56_ca_hue") { onChange(adj.copy(hue = it)) }
                slider(stringResource(R.string.v56_ca_saturation), adj.saturation, -100f, 100f, "v56_ca_saturation") { onChange(adj.copy(saturation = it)) }
                slider(stringResource(R.string.v56_ca_lightness), adj.lightness, -100f, 100f, "v56_ca_lightness") { onChange(adj.copy(lightness = it)) }
                NeuChip(stringResource(R.string.v56_ca_colorize), adj.colorize, { onChange(adj.copy(colorize = !adj.colorize)) }, Modifier.testTag("v56_ca_colorize"))
            }
            ColorAdjustTab.BALANCE -> {
                row {
                    listOf(R.string.v56_ca_shadows, R.string.v56_ca_midtones, R.string.v56_ca_highlights).forEachIndexed { i, label ->
                        NeuChip(stringResource(label), range == i, { range = i }, Modifier.testTag("v56_ca_range_$i"))
                    }
                }
                listOf(R.string.v56_ca_cyan_red, R.string.v56_ca_magenta_green, R.string.v56_ca_yellow_blue).forEachIndexed { c, label ->
                    slider(stringResource(label), adj.balance[range][c], -100f, 100f, "v56_ca_balance_$c") { onChange(adj.withBalance(range, c, it)) }
                }
                NeuChip(stringResource(R.string.v56_ca_preserve), adj.preserveLuminosity, { onChange(adj.copy(preserveLuminosity = !adj.preserveLuminosity)) }, Modifier.testTag("v56_ca_preserve"))
            }
            ColorAdjustTab.CURVES -> {
                row {
                    listOf("RGB", "R", "G", "B").forEachIndexed { i, label ->
                        NeuChip(label, channel == i, { channel = i }, Modifier.testTag("v56_ca_channel_$i"))
                    }
                }
                CurveEditor(adj.curves[channel], channelColor(channel), { i, v -> onChange(adj.withCurve(channel, i, v)) }, Modifier.fillMaxWidth().height(160.dp).testTag("v56_ca_curve"))
                Text(stringResource(R.string.v56_ca_curve_hint), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}

private fun channelColor(c: Int): Color = when (c) { 1 -> Color(0xFFE53935); 2 -> Color(0xFF43A047); 3 -> Color(0xFF1E88E5); else -> Color(0xFF616161) }

/** 5 noktalı eğri: noktalar dikey sürüklenir (x sabit: 0, ¼, ½, ¾, 1). */
@Composable
private fun CurveEditor(ys: List<Float>, color: Color, onSet: (Int, Float) -> Unit, modifier: Modifier) {
    val grid = NeuTheme.colors.contentMuted.copy(alpha = 0.3f)
    var drag by remember { mutableIntStateOf(-1) }
    val current by androidx.compose.runtime.rememberUpdatedState(ys)
    Canvas(
        modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { p ->
                    val xs = ColorAdjust.CURVE_X
                    drag = xs.indices.minByOrNull { abs(xs[it] * size.width - p.x) } ?: -1
                    if (drag >= 0) onSet(drag, 1f - p.y / size.height)
                },
                onDragEnd = { drag = -1 }, onDragCancel = { drag = -1 },
            ) { change, _ ->
                change.consume()
                if (drag >= 0) onSet(drag, 1f - change.position.y / size.height)
            }
        },
    ) {
        for (i in 1..3) {
            drawLine(grid, Offset(size.width * i / 4f, 0f), Offset(size.width * i / 4f, size.height))
            drawLine(grid, Offset(0f, size.height * i / 4f), Offset(size.width, size.height * i / 4f))
        }
        drawLine(grid, Offset(0f, size.height), Offset(size.width, 0f))
        val lut = ColorAdjustOps.curveLut(current)
        val path = Path()
        for (i in 0..255) {
            val x = i / 255f * size.width; val y = (1f - lut[i] / 255f) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = DrawStroke(width = 3.dp.toPx()))
        ColorAdjust.CURVE_X.forEachIndexed { i, x ->
            drawCircle(color, 6.dp.toPx(), Offset(x * size.width, (1f - current[i]) * size.height))
        }
    }
}

/**
 * Özellikler penceresindeki "Katman renkleri" bölümü (eleman seçili değilken, etkin katmana). Kilitli katmanda pasif.
 */
@Composable
fun V56LayerColorSection(viewModel: CanvasEditViewModel, modifier: Modifier = Modifier) {
    val layers by viewModel.layers.collectAsState()
    val active by viewModel.activeLayer.collectAsState()
    val applying by viewModel.colorApplying.collectAsState()
    val layer = layers.firstOrNull { it.id == active } ?: layers.lastOrNull() ?: return
    var adj by remember(layer.id) { mutableStateOf(ColorAdjust()) }
    DisposableEffect(layer.id) { onDispose { viewModel.cancelColorAdjust() } }
    Column(modifier.verticalScroll(rememberScrollState()).testTag("v56_layer_colors"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.v56_layer_colors, layer.name), style = MaterialTheme.typography.titleSmall)
        if (layer.locked) {
            Text(stringResource(R.string.v56_layer_colors_locked), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
            return@Column
        }
        V56ColorAdjustControls(viewModel, layer.id, adj, { adj = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuButton(stringResource(R.string.v56_ca_reset), { adj = ColorAdjust(); viewModel.cancelColorAdjust() }, Modifier.weight(1f).testTag("v56_ca_reset"))
            NeuButton(
                stringResource(R.string.v56_ca_apply), { viewModel.applyColorAdjust(adj, layer.id); adj = ColorAdjust() },
                Modifier.weight(1f).testTag("v56_ca_apply"), primary = true, enabled = !adj.isIdentity && !applying,
            )
        }
    }
}

/** Seç satırındaki "Renk ayarları": seçili elemanlara (resimler piksel olarak). */
@Composable
fun V56ColorAdjustDialog(viewModel: CanvasEditViewModel, onDismiss: () -> Unit) {
    var adj by remember { mutableStateOf(ColorAdjust()) }
    AlertDialog(
        onDismissRequest = { viewModel.cancelColorAdjust(); onDismiss() }, containerColor = NeuTheme.colors.background,
        title = { Text(stringResource(R.string.v56_color_adjust)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("v56_color_adjust_dialog")) {
                V56ColorAdjustControls(viewModel, null, adj, { adj = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.applyColorAdjust(adj, null); onDismiss() }, modifier = Modifier.testTag("v56_ca_dialog_apply")) { Text(stringResource(R.string.v56_ca_apply)) }
        },
        dismissButton = { TextButton(onClick = { viewModel.cancelColorAdjust(); onDismiss() }) { Text(stringResource(R.string.cancel_button)) } },
    )
}
