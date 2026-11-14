package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.TubeMaterial
import com.karapp.drawguidance.data.settings.BrushTuning
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.drawing.defaultSpacing
import com.karapp.drawguidance.ui.drawing.defaultTiltLight
import com.karapp.drawguidance.ui.drawing.defaultTiltWidth
import com.karapp.drawguidance.ui.drawing.hasFlatNib
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import com.karapp.drawguidance.ui.components.NeuSheet

/** v20 fırça boyutu kaydırıcısı üstel: küçük boyutlarda hassas, en çok [BrushTuning.MAX_SIZE] px. */
internal object SizeScale {
    private val range = BrushTuning.MAX_SIZE / BrushTuning.MIN_SIZE
    fun toPosition(size: Float): Float = (ln(size.coerceIn(BrushTuning.MIN_SIZE, BrushTuning.MAX_SIZE) / BrushTuning.MIN_SIZE) / ln(range)).coerceIn(0f, 1f)
    fun toSize(position: Float): Float {
        val raw = BrushTuning.MIN_SIZE * range.toDouble().pow(position.coerceIn(0f, 1f).toDouble()).toFloat()
        // 10 px'in altında yarım piksel, üstünde tam piksel adım
        return if (raw < 10f) (raw * 2f).roundToInt() / 2f else raw.roundToInt().toFloat()
    }
}

/** v20 fırça boyutu: üstel kaydırıcı; değere dokununca boyut yazılabilir. */
@Composable
internal fun BrushSizeControl(sizePx: Float, onChange: (Float) -> Unit) {
    val colors = NeuTheme.colors
    var editing by remember { mutableStateOf(false) }
    val sizeText = if (sizePx < 10f && sizePx != sizePx.roundToInt().toFloat()) "${"%.1f".format(sizePx).replace('.', ',')} px" else "${sizePx.roundToInt()} px"
    val editText = stringResource(R.string.brush_size_edit)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.brush_size_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                sizeText,
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent,
                modifier = Modifier
                    .testTag("brush_size_value")
                    .semantics { contentDescription = editText }
                    .clickable { editing = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        NeuSlider(
            value = SizeScale.toPosition(sizePx),
            onValueChange = { onChange(SizeScale.toSize(it)) },
            valueRange = 0f..1f,
            modifier = Modifier.testTag("brush_size_slider"),
        )
        Text(
            stringResource(R.string.brush_size_range, BrushTuning.MIN_SIZE.toInt(), BrushTuning.MAX_SIZE.toInt()),
            style = MaterialTheme.typography.bodySmall, color = colors.contentMuted,
        )
    }
    if (editing) BrushSizeSheet(sizePx, editText, onApply = { onChange(it); editing = false }, onDismiss = { editing = false })
}

/** Boyutu yazma penceresi (alttan açılır; klavyenin üstünde kalır). Aralık dışı değer uygulanamaz. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrushSizeSheet(sizePx: Float, title: String, onApply: (Float) -> Unit, onDismiss: () -> Unit) {
    val colors = NeuTheme.colors
    var text by remember { mutableStateOf(sizePx.roundToInt().toString()) }
    val value = text.replace(',', '.').toFloatOrNull()
    val valid = value != null && value in BrushTuning.MIN_SIZE..BrushTuning.MAX_SIZE
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp).testTag("brush_size_sheet").verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            NeuTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                label = stringResource(R.string.brush_size_label),
                supportingText = stringResource(R.string.brush_size_range, BrushTuning.MIN_SIZE.toInt(), BrushTuning.MAX_SIZE.toInt()),
                isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                fieldModifier = Modifier.testTag("brush_size_field"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuButton(stringResource(R.string.cancel_button), onDismiss, Modifier.weight(1f))
                NeuButton(
                    stringResource(R.string.apply_button), { if (valid) onApply(value!!) }, Modifier.weight(1f).testTag("brush_size_apply"),
                    primary = true, enabled = valid,
                )
            }
        }
    }
}

/** Etiket, değer ve kaydırıcı. */
@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    tag: String,
    hint: String? = null,
) {
    val colors = NeuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = colors.contentMuted, modifier = Modifier.testTag("${tag}_value"))
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        NeuSlider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val colors = NeuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        }
        NeuSwitch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag).semantics { contentDescription = title })
    }
}

@Composable
private fun SectionTitle(text: String, hint: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = NeuTheme.colors.accent)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
    }
}

private fun pct(v: Float) = "%${(v * 100).roundToInt()}"

/**
 * v20 fırça başına ayarlar: damga aralığı (aralıklı fırçalarda), çizim hızı, sabitleme, kalem eğimi (kalınlaşma,
 * açılma, eşik; yassı uçta uç yönü) ve 3B fırçada ışık yönü, kabartma, parlaklık, malzeme ve zemin gölgesi.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BrushTuningSection(
    type: BrushType,
    tuning: BrushTuning,
    onTuning: ((BrushTuning) -> BrushTuning) -> Unit,
    onReset: () -> Unit,
) {
    val colors = NeuTheme.colors
    Column(Modifier.testTag("brush_tuning_section"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Damga aralığı: yalnız damgalarla çizen fırçalarda
        type.defaultSpacing()?.let { default ->
            val spacing = tuning.spacing ?: default
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LabeledSlider(
                    stringResource(R.string.brush_spacing_label), pct(spacing), spacing, 0.02f..3f,
                    { v -> onTuning { it.copy(spacing = (v * 100).roundToInt() / 100f) } }, "brush_spacing",
                    hint = stringResource(R.string.brush_spacing_hint),
                )
                if (tuning.spacing != null && kotlin.math.abs(tuning.spacing - default) > 1e-3f) {
                    NeuChip(stringResource(R.string.brush_default) + " · " + pct(default), false, { onTuning { it.copy(spacing = null) } }, Modifier.testTag("brush_spacing_default"))
                }
            }
        }

        // Çizim hızı (mürekkebin kaleme yetişme hızı)
        LabeledSlider(
            stringResource(R.string.brush_speed_label),
            if (tuning.speed >= 0.999f) stringResource(R.string.brush_speed_instant) else pct(tuning.speed),
            tuning.speed, 0.1f..1f,
            { v -> onTuning { it.copy(speed = if (v > 0.98f) 1f else (v * 100).roundToInt() / 100f) } }, "brush_speed",
            hint = stringResource(R.string.brush_speed_hint),
        )

        // Sabitleme (bu fırça için)
        SwitchRow(
            stringResource(R.string.brush_stabilizer_label), stringResource(R.string.brush_stabilizer_hint), tuning.stabilizer, "brush_stabilizer",
        ) { on -> onTuning { it.copy(stabilizer = on) } }
        if (tuning.stabilizer) {
            LabeledSlider(
                stringResource(R.string.brush_stabilizer_strength), pct(tuning.stabilizerStrength), tuning.stabilizerStrength, 0.05f..1f,
                { v -> onTuning { it.copy(stabilizerStrength = (v * 100).roundToInt() / 100f) } }, "brush_stabilizer_strength",
            )
        }

        // Kalem eğimi: v21 yalnız uygulama tablet kalemini tanıdıysa (Ayarlar → Tablet kalemi)
        val stylusState by com.karapp.drawguidance.platform.Stylus.state.collectAsState()
        val recognition by com.karapp.drawguidance.platform.Stylus.recognition.collectAsState()
        val penRecognized = com.karapp.drawguidance.platform.Stylus.isRecognized(stylusState, recognition)
        if (type != BrushType.ERASER && penRecognized) {
            SectionTitle(stringResource(R.string.brush_tilt_title), stringResource(R.string.brush_tilt_hint))
            val tw = tuning.tiltWidth ?: type.defaultTiltWidth()
            val tl = tuning.tiltLight ?: type.defaultTiltLight()
            LabeledSlider(stringResource(R.string.brush_tilt_width), pct(tw), tw, 0f..2f, { v -> onTuning { it.copy(tiltWidth = (v * 100).roundToInt() / 100f) } }, "brush_tilt_width")
            LabeledSlider(stringResource(R.string.brush_tilt_light), pct(tl), tl, 0f..1f, { v -> onTuning { it.copy(tiltLight = (v * 100).roundToInt() / 100f) } }, "brush_tilt_light")
            LabeledSlider(
                stringResource(R.string.brush_tilt_threshold), stringResource(R.string.degree_value, tuning.tiltThreshold.roundToInt()),
                tuning.tiltThreshold, 0f..60f, { v -> onTuning { it.copy(tiltThreshold = v.roundToInt().toFloat()) } }, "brush_tilt_threshold",
                hint = stringResource(R.string.brush_tilt_threshold_hint),
            )
            if (type.hasFlatNib) {
                SwitchRow(stringResource(R.string.brush_nib_follow), stringResource(R.string.brush_nib_follow_hint), tuning.nibFollow, "brush_nib_follow") { on ->
                    onTuning { it.copy(nibFollow = on) }
                }
            }
        }

        // 3B fırça
        if (type == BrushType.BRUSH_3D) {
            val tube = tuning.tube
            SectionTitle(stringResource(R.string.brush_3d_title))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LightDial(tube.lightAngle, Modifier.size(44.dp))
                Column(Modifier.weight(1f)) {
                    LabeledSlider(
                        stringResource(R.string.brush_3d_light), stringResource(R.string.degree_value, tube.lightAngle.roundToInt()),
                        tube.lightAngle, 0f..359f, { v -> onTuning { it.copy(tube = it.tube.copy(lightAngle = v.roundToInt().toFloat())) } }, "brush_3d_light",
                    )
                }
            }
            LabeledSlider(stringResource(R.string.brush_3d_relief), pct(tube.relief), tube.relief, 0f..1f, { v -> onTuning { it.copy(tube = it.tube.copy(relief = (v * 100).roundToInt() / 100f)) } }, "brush_3d_relief")
            LabeledSlider(stringResource(R.string.brush_3d_gloss), pct(tube.gloss), tube.gloss, 0f..1f, { v -> onTuning { it.copy(tube = it.tube.copy(gloss = (v * 100).roundToInt() / 100f)) } }, "brush_3d_gloss")
            Text(stringResource(R.string.brush_3d_material), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    TubeMaterial.MATTE to R.string.brush_3d_matte, TubeMaterial.PLASTIC to R.string.brush_3d_plastic,
                    TubeMaterial.METAL to R.string.brush_3d_metal, TubeMaterial.GLASS to R.string.brush_3d_glass,
                ).forEach { (m, label) ->
                    NeuChip(stringResource(label), tube.material == m, { onTuning { it.copy(tube = it.tube.copy(material = m)) } })
                }
            }
            LabeledSlider(stringResource(R.string.brush_3d_shadow), pct(tube.shadow), tube.shadow, 0f..1f, { v -> onTuning { it.copy(tube = it.tube.copy(shadow = (v * 100).roundToInt() / 100f)) } }, "brush_3d_shadow")
            if (tube.shadow > 0f) {
                LabeledSlider(
                    stringResource(R.string.brush_3d_shadow_soft), pct(tube.shadowSoftness), tube.shadowSoftness, 0f..1f,
                    { v -> onTuning { it.copy(tube = it.tube.copy(shadowSoftness = (v * 100).roundToInt() / 100f)) } }, "brush_3d_shadow_soft",
                )
            }
        }

        if (tuning != BrushTuning()) {
            TextButton(onClick = onReset, modifier = Modifier.testTag("brush_tuning_reset")) {
                Text(stringResource(R.string.brush_tuning_reset), color = colors.accent)
            }
        }
    }
}

/** Işığın geldiği yönü gösteren küçük kadran (güneş simgesi yönünde). */
@Composable
private fun LightDial(angle: Float, modifier: Modifier) {
    val colors = NeuTheme.colors
    val cd = stringResource(R.string.brush_3d_light_cd, angle.roundToInt())
    Canvas(modifier.semantics { contentDescription = cd }) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(colors.contentMuted.copy(alpha = 0.35f), r - 1f, c, style = Stroke(2f))
        val rad = Math.toRadians(angle.toDouble())
        val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
        drawLine(colors.accent, c, c + dir * (r - 6f), strokeWidth = 4f, cap = StrokeCap.Round)
        drawCircle(colors.accent, 5f, c + dir * (r - 6f))
        drawCircle(colors.content.copy(alpha = 0.6f), 3f, c)
    }
}
