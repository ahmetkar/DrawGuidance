package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.TubeMaterial
import com.karapp.drawguidance.data.model.isHelper
import com.karapp.drawguidance.data.model.isRaster
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
    /** v51 klon/onarma kaynağı seçimi. */
    cloneSourcePicking: Boolean = false,
    cloneSourcePending: Boolean = false,
    onPickCloneSource: () -> Unit = {},
    /** v56 gren ve resimden fırça; null: gösterilmez (mod kuralı). */
    grain: GrainActions? = null,
) {
    val colors = NeuTheme.colors
    Column(Modifier.testTag("brush_tuning_section"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // v51 yardımcı fırça, boyut/şekil, boya ve kalem ayarları
        BrushAdvancedSections(type, tuning, onTuning, cloneSourcePicking, cloneSourcePending, onPickCloneSource)

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

        if (grain != null && BrushTuning.supportsGrain(type)) GrainSection(type, tuning, grain)

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

// ---------------------------------------------------------------------------------------------
// v51 gelişmiş fırça ayarları: boyut ve şekil, boyama ve yoğunluk, kalem, yardımcı fırça
// ---------------------------------------------------------------------------------------------

/** Başlığına dokununca açılıp kapanan bölüm. */
@Composable
private fun ExpandableSection(title: String, tag: String, content: @Composable () -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(tag) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().testTag(tag).clickable { open = !open }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = NeuTheme.colors.accent, modifier = Modifier.weight(1f))
            androidx.compose.material3.Icon(
                if (open) androidx.compose.material.icons.Icons.Filled.ExpandLess else androidx.compose.material.icons.Icons.Filled.ExpandMore,
                contentDescription = null, tint = NeuTheme.colors.accent,
            )
        }
        if (open) content()
    }
}

private fun r2(v: Float) = (v * 100).roundToInt() / 100f
private fun signedPct(v: Float) = if (v > 0f) "+%${(v * 100).roundToInt()}" else if (v < 0f) "−%${(-v * 100).roundToInt()}" else "%0"

/** v51 piksel motorlu ya da tüm fırçalar için gelişmiş ayarlar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BrushAdvancedSections(
    type: BrushType,
    tuning: BrushTuning,
    onTuning: ((BrushTuning) -> BrushTuning) -> Unit,
    cloneSourcePicking: Boolean,
    cloneSourcePending: Boolean,
    onPickCloneSource: () -> Unit,
) {
    if (type == BrushType.ERASER) return
    val raster = type.isRaster
    val eff = com.karapp.drawguidance.ui.drawing.RasterBrush.effectiveTip(type, com.karapp.drawguidance.data.model.BrushParams(tip = tuning.tip))
    val tip = tuning.tip
    fun setTip(f: (com.karapp.drawguidance.data.model.TipParams) -> com.karapp.drawguidance.data.model.TipParams) = onTuning { it.copy(tip = f(it.tip)) }
    fun setPen(f: (com.karapp.drawguidance.data.model.PenDynamics) -> com.karapp.drawguidance.data.model.PenDynamics) = onTuning { it.copy(pen = f(it.pen)) }

    // Yardımcı fırça: açıklama, kip ve kaynak
    if (type.isHelper) {
        ExpandableSection(stringResource(R.string.v51_sec_helper), "brush_v51_helper") {
            val hint = when (type) {
                BrushType.FX_BLUR -> R.string.v51_fx_blur_hint
                BrushType.FX_SHARPEN -> R.string.v51_fx_sharpen_hint
                BrushType.FX_DODGE -> R.string.v51_fx_dodge_hint
                BrushType.FX_BURN -> R.string.v51_fx_burn_hint
                BrushType.FX_SPONGE -> R.string.v51_fx_sponge_hint
                BrushType.FX_CLONE -> R.string.v51_fx_clone_hint
                BrushType.FX_HEAL -> R.string.v51_fx_heal_hint
                else -> R.string.v51_fx_mixer_hint
            }
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall)
            if (type != BrushType.FX_MIXER) Text(stringResource(R.string.v51_fx_hint), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
            if (type == BrushType.FX_DODGE || type == BrushType.FX_BURN) {
                Text(stringResource(R.string.v51_fx_range), style = MaterialTheme.typography.labelLarge)
                val mode = tuning.fx.mode.takeIf { it in 0..2 } ?: 1
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to R.string.v51_fx_shadows, 1 to R.string.v51_fx_midtones, 2 to R.string.v51_fx_highlights).forEach { (m, label) ->
                        NeuChip(stringResource(label), mode == m, { onTuning { it.copy(fx = it.fx.copy(mode = m)) } }, Modifier.testTag("brush_v51_range_$m"))
                    }
                }
            }
            if (type == BrushType.FX_SPONGE) {
                Text(stringResource(R.string.v51_fx_mode), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to R.string.v51_fx_desaturate, 1 to R.string.v51_fx_saturate).forEach { (m, label) ->
                        NeuChip(stringResource(label), tuning.fx.mode == m, { onTuning { it.copy(fx = it.fx.copy(mode = m)) } }, Modifier.testTag("brush_v51_sponge_$m"))
                    }
                }
            }
            if (type == BrushType.FX_CLONE || type == BrushType.FX_HEAL) {
                Text(stringResource(R.string.v51_fx_source), style = MaterialTheme.typography.labelLarge)
                val dx = tuning.fx.srcDx; val dy = tuning.fx.srcDy
                Text(
                    when {
                        cloneSourcePicking -> stringResource(R.string.v51_fx_picking)
                        cloneSourcePending -> stringResource(R.string.v51_fx_source_wait)
                        dx != null && dy != null -> stringResource(R.string.v51_fx_source_set, dx.roundToInt(), dy.roundToInt())
                        else -> stringResource(R.string.v51_fx_source_auto)
                    },
                    style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted, modifier = Modifier.testTag("brush_v51_source_state"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuChip(stringResource(R.string.v51_fx_pick_source), cloneSourcePicking, onPickCloneSource, Modifier.testTag("brush_v51_pick_source"))
                    if (dx != null) NeuChip(stringResource(R.string.v51_fx_source_reset), false, { onTuning { it.copy(fx = it.fx.copy(srcDx = null, srcDy = null)) } })
                }
            }
        }
    }

    // Boyut ve şekil
    ExpandableSection(stringResource(R.string.v51_sec_shape), "brush_v51_shape") {
        val minS = tip.minSize ?: if (raster) eff.minSize ?: 0f else 0f
        val maxS = tip.maxSize ?: 1f
        LabeledSlider(stringResource(R.string.v51_min_size), pct(minS), minS, 0f..1f, { v -> setTip { it.copy(minSize = r2(v)) } }, "brush_v51_min_size")
        LabeledSlider(stringResource(R.string.v51_max_size), pct(maxS), maxS, 0.2f..3f, { v -> setTip { it.copy(maxSize = r2(v)) } }, "brush_v51_max_size")
        if (raster) {
            val h = eff.hardness ?: 0.5f
            LabeledSlider(stringResource(R.string.v51_hardness), pct(h), h, 0f..1f, { v -> setTip { it.copy(hardness = r2(v)) } }, "brush_v51_hardness")
            val so = eff.softness ?: 0f
            LabeledSlider(stringResource(R.string.v51_softness), pct(so), so, 0f..1f, { v -> setTip { it.copy(softness = r2(v)) } }, "brush_v51_softness")
            val asp = eff.aspect ?: 1f
            LabeledSlider(stringResource(R.string.v51_aspect), pct(asp), asp, 0.05f..1f, { v -> setTip { it.copy(aspect = r2(v)) } }, "brush_v51_aspect")
            val ang = eff.angle ?: 0f
            LabeledSlider(stringResource(R.string.v51_angle), stringResource(R.string.degree_value, ang.roundToInt()), ang, 0f..359f, { v -> setTip { it.copy(angle = v.roundToInt().toFloat()) } }, "brush_v51_angle")
            val rot = eff.rotation ?: 0f
            LabeledSlider(stringResource(R.string.v51_rotation), pct(rot), rot, 0f..1f, { v -> setTip { it.copy(rotation = r2(v)) } }, "brush_v51_rotation")
            val rnd = eff.roundness ?: 1f
            LabeledSlider(stringResource(R.string.v51_roundness), pct(rnd), rnd, 0f..1f, { v -> setTip { it.copy(roundness = r2(v)) } }, "brush_v51_roundness")
            val edge = eff.edge ?: 0.5f
            LabeledSlider(stringResource(R.string.v51_edge), pct(edge), edge, 0f..1f, { v -> setTip { it.copy(edge = r2(v)) } }, "brush_v51_edge")
            Text(stringResource(R.string.v51_tip_shape), style = MaterialTheme.typography.labelLarge)
            val shape = eff.shape ?: com.karapp.drawguidance.data.model.TipShape.ROUND
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    com.karapp.drawguidance.data.model.TipShape.ROUND to R.string.v51_tip_round,
                    com.karapp.drawguidance.data.model.TipShape.SQUARE to R.string.v51_tip_square,
                    com.karapp.drawguidance.data.model.TipShape.FLAT to R.string.v51_tip_flat,
                    com.karapp.drawguidance.data.model.TipShape.CHISEL to R.string.v51_tip_chisel,
                    com.karapp.drawguidance.data.model.TipShape.BRISTLE to R.string.v51_tip_bristle,
                    com.karapp.drawguidance.data.model.TipShape.SPONGE to R.string.v51_tip_sponge,
                    com.karapp.drawguidance.data.model.TipShape.SPLATTER to R.string.v51_tip_splatter,
                    com.karapp.drawguidance.data.model.TipShape.CLOUD to R.string.v51_tip_cloud,
                ).forEach { (sh, label) ->
                    NeuChip(stringResource(label), shape == sh, { setTip { it.copy(shape = sh) } }, Modifier.testTag("brush_v51_tip_${sh.name.lowercase()}"))
                }
            }
        }
    }

    // Boyama ve yoğunluk (piksel motorlu fırçalar; opaklık ve akışkanlık panelin üstünde)
    if (raster) {
        ExpandableSection(stringResource(R.string.v51_sec_paint), "brush_v51_paint") {
            Text(stringResource(R.string.v51_paint_hint), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
            val den = eff.density ?: 1f
            LabeledSlider(stringResource(R.string.v51_density), pct(den), den, 0f..1f, { v -> setTip { it.copy(density = r2(v)) } }, "brush_v51_density")
            val bu = eff.buildUp ?: 0f
            LabeledSlider(stringResource(R.string.v51_buildup), pct(bu), bu, 0f..1f, { v -> setTip { it.copy(buildUp = r2(v)) } }, "brush_v51_buildup")
            if (com.karapp.drawguidance.ui.drawing.RasterBrush.paints(type)) {
                val wet = eff.wetness ?: 0f
                LabeledSlider(stringResource(R.string.v51_wetness), pct(wet), wet, 0f..1f, { v -> setTip { it.copy(wetness = r2(v)) } }, "brush_v51_wetness")
                val pa = eff.paintAmount ?: 1f
                LabeledSlider(stringResource(R.string.v51_paint_amount), pct(pa), pa, 0f..1f, { v -> setTip { it.copy(paintAmount = r2(v)) } }, "brush_v51_paint_amount")
                val th = eff.thickness ?: 0f
                LabeledSlider(stringResource(R.string.v51_thickness), pct(th), th, 0f..1f, { v -> setTip { it.copy(thickness = r2(v)) } }, "brush_v51_thickness")
                val ca = eff.colorAmount ?: 1f
                LabeledSlider(stringResource(R.string.v51_color_amount), pct(ca), ca, 0f..1f, { v -> setTip { it.copy(colorAmount = r2(v)) } }, "brush_v51_color_amount")
                val sm = eff.smudge ?: 0f
                LabeledSlider(stringResource(R.string.v51_smudge), pct(sm), sm, 0f..1f, { v -> setTip { it.copy(smudge = r2(v)) } }, "brush_v51_smudge")
                val bl = eff.blend ?: 0f
                LabeledSlider(stringResource(R.string.v51_blend), pct(bl), bl, 0f..1f, { v -> setTip { it.copy(blend = r2(v)) } }, "brush_v51_blend")
            }
        }
    }

    // Kalem ve hareket dinamikleri
    ExpandableSection(stringResource(R.string.v51_sec_pen), "brush_v51_pen") {
        val pen = tuning.pen
        Text(stringResource(R.string.v51_pen_hint), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
        val p = pen.pressure ?: 0f
        LabeledSlider(stringResource(R.string.v51_pen_pressure), if (pen.pressure == null) stringResource(R.string.v51_default) else pct(p), p, 0f..1f, { v -> setPen { it.copy(pressure = r2(v)) } }, "brush_v51_pen_pressure")
        val po = pen.pressureOpacity ?: 0f
        LabeledSlider(stringResource(R.string.v51_pen_pressure_opacity), pct(po), po, 0f..1f, { v -> setPen { it.copy(pressureOpacity = r2(v).takeIf { x -> x > 0f }) } }, "brush_v51_pen_pressure_opacity")
        val tx = pen.tiltX ?: 0f
        LabeledSlider(stringResource(R.string.v51_pen_tilt_x), signedPct(tx), tx, -1f..1f, { v -> setPen { it.copy(tiltX = r2(v).takeIf { x -> x != 0f }) } }, "brush_v51_pen_tilt_x")
        val ty = pen.tiltY ?: 0f
        LabeledSlider(stringResource(R.string.v51_pen_tilt_y), signedPct(ty), ty, -1f..1f, { v -> setPen { it.copy(tiltY = r2(v).takeIf { x -> x != 0f }) } }, "brush_v51_pen_tilt_y")
        if (raster) {
            val td = pen.tiltDirection ?: 0f
            LabeledSlider(stringResource(R.string.v51_pen_tilt_dir), pct(td), td, 0f..1f, { v -> setPen { it.copy(tiltDirection = r2(v).takeIf { x -> x > 0f }) } }, "brush_v51_pen_tilt_dir")
            val ro = pen.rotation ?: 0f
            LabeledSlider(stringResource(R.string.v51_pen_rotation), pct(ro), ro, 0f..1f, { v -> setPen { it.copy(rotation = r2(v).takeIf { x -> x > 0f }) } }, "brush_v51_pen_rotation",
                hint = stringResource(R.string.v51_pen_rotation_hint))
        }
        val ve = pen.velocity ?: 0f
        LabeledSlider(stringResource(R.string.v51_pen_velocity), signedPct(ve), ve, -1f..1f, { v -> setPen { it.copy(velocity = r2(v).takeIf { x -> x != 0f }) } }, "brush_v51_pen_velocity")
        if (raster) {
            val di = pen.direction ?: 0f
            LabeledSlider(stringResource(R.string.v51_pen_direction), pct(di), di, 0f..1f, { v -> setPen { it.copy(direction = r2(v).takeIf { x -> x > 0f }) } }, "brush_v51_pen_direction")
        }
        val ds = pen.distance ?: 0f
        LabeledSlider(stringResource(R.string.v51_pen_distance), pct(ds), ds, 0f..1f, { v -> setPen { it.copy(distance = r2(v).takeIf { x -> x > 0f }) } }, "brush_v51_pen_distance")
        val ti = pen.time ?: 0f
        LabeledSlider(stringResource(R.string.v51_pen_time), pct(ti), ti, 0f..1f, { v -> setPen { it.copy(time = r2(v).takeIf { x -> x > 0f }) } }, "brush_v51_pen_time")
        val ba = pen.barrel ?: 0f
        LabeledSlider(stringResource(R.string.v51_pen_barrel), signedPct(ba), ba, -1f..1f, { v -> setPen { it.copy(barrel = r2(v).takeIf { x -> x != 0f }) } }, "brush_v51_pen_barrel",
            hint = stringResource(R.string.v51_pen_barrel_hint))
    }
}

// ---------------------------------------------------------------------------------------------
// v56 aşama 5: gren ve resimden fırça
// ---------------------------------------------------------------------------------------------

/** v56 Fırça Özellikleri › Gren bölümünün eylemleri. */
class GrainActions(
    val onTexture: (String?) -> Unit,
    val onGrain: ((com.karapp.drawguidance.data.model.GrainSpec) -> com.karapp.drawguidance.data.model.GrainSpec) -> Unit,
    val onPickImage: () -> Unit,
    val onRemoveImage: () -> Unit,
)

/** Hazır dokunun adı (7 dilde). */
@Composable
fun textureLabel(id: String): String = stringResource(
    when (id.removePrefix(com.karapp.drawguidance.data.model.V56Textures.PREFIX)) {
        "paper" -> R.string.v56_tex_paper
        "canvas" -> R.string.v56_tex_canvas
        "charcoal" -> R.string.v56_tex_charcoal
        "noise" -> R.string.v56_tex_noise
        "dots" -> R.string.v56_tex_dots
        else -> R.string.v56_tex_hatch
    },
)

/** Doku örneği (karo, koyu = boya tutan yer). */
@Composable
fun TextureSwatch(id: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    val bitmap = remember(id) { com.karapp.drawguidance.ui.drawing.GrainRenderer.preview(id).asImageBitmap() }
    val label = textureLabel(id)
    Column(
        modifier.clickable(onClick = onClick).padding(2.dp).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Canvas(
            Modifier.size(48.dp)
                .background(colors.background, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .border(if (selected) 3.dp else 1.dp, if (selected) colors.accent else colors.contentMuted.copy(alpha = 0.4f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
        ) {
            val inset = 3.dp.toPx()
            clipRect(inset, inset, size.width - inset, size.height - inset) {
                drawImage(
                    bitmap, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.content, androidx.compose.ui.graphics.BlendMode.SrcIn),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GrainSection(type: BrushType, tuning: BrushTuning, actions: GrainActions) {
    val colors = NeuTheme.colors
    Column(Modifier.testTag("v56_grain_section"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.v56_grain_title), stringResource(R.string.v56_grain_hint))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NeuChip(stringResource(R.string.v56_grain_none), tuning.grain == null, { actions.onTexture(null) }, Modifier.testTag("v56_grain_none"))
            com.karapp.drawguidance.data.model.V56Textures.BUILTIN.forEach { id ->
                TextureSwatch(id, tuning.grain?.textureId == id, { actions.onTexture(id) }, Modifier.testTag("v56_grain_${id.removePrefix("builtin:")}"))
            }
        }
        tuning.grain?.let { g ->
            LabeledSlider(
                stringResource(R.string.v56_grain_scale), pct(g.scale), g.scale,
                com.karapp.drawguidance.data.model.V56Textures.MIN_SCALE..com.karapp.drawguidance.data.model.V56Textures.MAX_SCALE,
                { v -> actions.onGrain { it.copy(scale = r2(v)) } }, "v56_grain_scale",
            )
            LabeledSlider(stringResource(R.string.v56_grain_strength), pct(g.strength), g.strength, 0f..1f, { v -> actions.onGrain { it.copy(strength = r2(v)) } }, "v56_grain_strength")
            SwitchRow(stringResource(R.string.v56_grain_moving), stringResource(R.string.v56_grain_moving_hint), g.moving, "v56_grain_moving") { on ->
                actions.onGrain { it.copy(moving = on) }
            }
        }
        if (BrushTuning.supportsImageTip(type)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuButton(stringResource(R.string.v56_image_brush), actions.onPickImage, Modifier.weight(1f).testTag("v56_image_brush"))
                if (tuning.stampAssetId != null) {
                    TextButton(onClick = actions.onRemoveImage, modifier = Modifier.testTag("v56_image_brush_remove")) {
                        Text(stringResource(R.string.v56_image_brush_remove), color = colors.accent)
                    }
                }
            }
            if (tuning.stampAssetId != null) {
                Text(stringResource(R.string.v56_image_brush_active), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, modifier = Modifier.testTag("v56_image_brush_active"))
            }
        }
    }
}

/** v56 doku seçici (Dokuyla doldur, katmanı dokuyla doldur, Ekle › Doku). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TexturePickerDialog(title: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeuTheme.colors.background,
        title = { Text(title) },
        text = {
            FlowRow(Modifier.testTag("v56_texture_picker"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.karapp.drawguidance.data.model.V56Textures.BUILTIN.forEach { id ->
                    TextureSwatch(id, false, { onPick(id) }, Modifier.testTag("v56_texture_${id.removePrefix("builtin:")}"))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) } },
    )
}
