package com.karapp.drawguidance.ui.screens.canvasedit.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.image.BackgroundOptions
import com.karapp.drawguidance.content.image.BackgroundRemover
import com.karapp.drawguidance.content.image.ImageAdjust
import com.karapp.drawguidance.content.image.ImageEffects
import com.karapp.drawguidance.content.image.ImageLook
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.karapp.drawguidance.ui.components.NeuSheet

/** Önizleme için küçük resmin pikselleri. */
private class Pixels(val px: IntArray, val w: Int, val h: Int)

private fun Bitmap.pixels(): Pixels {
    val px = IntArray(width * height)
    getPixels(px, 0, width, 0, 0, width, height)
    return Pixels(px, width, height)
}

private fun toImage(px: IntArray, w: Int, h: Int): ImageBitmap = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()

/** Önizleme boyutu (tüm cihazlarda akıcı: ayar değişince ~10–60 ms). */
private const val PREVIEW_SIDE = 420

/**
 * Damalı (saydamlık gösteren) zeminde resim önizlemesi. [onTap] verilirse dokunulan yer resme göre oranlı (0..1)
 * bildirilir.
 */
@Composable
private fun CheckerPreview(image: ImageBitmap?, ratio: Float, busy: Boolean, modifier: Modifier = Modifier, onTap: ((Float, Float) -> Unit)? = null) {
    val colors = NeuTheme.colors
    Box(modifier.fillMaxWidth().heightIn(max = 300.dp), contentAlignment = Alignment.Center) {
        val r = ratio.coerceIn(0.3f, 3f)
        Box(
            Modifier
                .aspectRatio(r, matchHeightConstraintsFirst = r < 1f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, colors.contentMuted.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .then(
                    if (onTap != null) Modifier.pointerInput(Unit) {
                        detectTapGestures { p -> onTap((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f)) }
                    } else Modifier,
                )
                .testTag("image_tool_preview"),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                val cell = 10.dp.toPx()
                val light = Color(0xFFF2F2F2); val dark = Color(0xFFD9D9D9)
                var y = 0f; var row = 0
                while (y < size.height) {
                    var x = 0f; var col = 0
                    while (x < size.width) {
                        drawRect(if ((row + col) % 2 == 0) light else dark, Offset(x, y), Size(cell, cell))
                        x += cell; col++
                    }
                    y += cell; row++
                }
            }
            if (image != null) Image(image, null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
            if (busy || image == null) CircularProgressIndicator(Modifier.size(32.dp), color = colors.accent, strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, tag: String,
    /** v39 Premium kilidi: sürgü yerine dokununca "Premium gerekli" açan satır. */
    locked: Boolean = false, onLocked: () -> Unit = {},
    onChange: (Float) -> Unit,
) {
    val colors = NeuTheme.colors
    if (locked) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onLocked).padding(vertical = 8.dp).testTag("${tag}_locked"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted, modifier = Modifier.weight(1f))
            com.karapp.drawguidance.ui.premium.PremiumLockBadge()
        }
        return
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (range.start < 0f) "%+d".format((value * 100).toInt()) else "%d".format((value * 100).toInt()),
                style = MaterialTheme.typography.labelMedium, color = colors.contentMuted,
            )
        }
        NeuSlider(value, onChange, range, Modifier.fillMaxWidth().testTag(tag))
    }
}

/**
 * v30 "Efektler": hazır görünümler (küçük resimli) ve genel ayarlar; önizleme canlı güncellenir. Uygula ile seçili
 * resme işlenir (tek geri alma adımı).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEffectsSheet(
    loadPreview: suspend (Int) -> Bitmap?,
    busy: Boolean,
    onApply: (ImageLook, ImageAdjust) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NeuTheme.colors
    var source by remember { mutableStateOf<Pixels?>(null) }
    var thumbs by remember { mutableStateOf<Map<ImageLook, ImageBitmap>>(emptyMap()) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var computing by remember { mutableStateOf(false) }
    var look by remember { mutableStateOf(ImageLook.NONE) }
    var adjust by remember { mutableStateOf(ImageAdjust()) }
    // v39 gelişmiş görünüm ve sürgüler Premium (gösterilir, dokununca "Premium gerekli")
    val premium = com.karapp.drawguidance.ui.premium.LocalPremium.current
    val advLocked = premium.locked(com.karapp.drawguidance.data.billing.PremiumFeature.IMAGE_EFFECTS_ADVANCED)
    LaunchedEffect(Unit) {
        val bmp = loadPreview(PREVIEW_SIDE) ?: return@LaunchedEffect
        val p = withContext(Dispatchers.Default) { bmp.pixels() }
        source = p
        preview = withContext(Dispatchers.Default) { toImage(p.px, p.w, p.h) }
        // Görünümlerin küçük resimleri (küçük kopya üstünde, bir kez)
        thumbs = withContext(Dispatchers.Default) {
            val s = 72f / maxOf(p.w, p.h)
            val tw = maxOf(1, (p.w * s).toInt()); val th = maxOf(1, (p.h * s).toInt())
            val small = BackgroundRemover.resample(p.px, p.w, p.h, tw, th)
            ImageLook.entries.associateWith { l -> toImage(ImageEffects.apply(small, tw, th, l), tw, th) }
        }
    }
    LaunchedEffect(source, look, adjust) {
        val p = source ?: return@LaunchedEffect
        delay(60)
        computing = true
        preview = withContext(Dispatchers.Default) { toImage(ImageEffects.apply(p.px, p.w, p.h, look, adjust), p.w, p.h) }
        computing = false
    }
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp)
                .testTag("image_effects_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.image_effects_title), style = MaterialTheme.typography.headlineSmall)
            CheckerPreview(preview, source?.let { it.w / it.h.toFloat() } ?: 1f, computing || busy)
            Text(stringResource(R.string.image_effects_looks), style = MaterialTheme.typography.titleSmall, color = colors.accent)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ImageLook.entries.forEach { l ->
                    val selected = l == look
                    val lLocked = advLocked && com.karapp.drawguidance.data.billing.PremiumRules.isAdvancedLook(l)
                    Column(
                        Modifier.width(76.dp).clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { if (!lLocked || premium.request(com.karapp.drawguidance.data.billing.PremiumFeature.IMAGE_EFFECTS_ADVANCED)) look = l }
                            .testTag("look_${l.name}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                                .border(if (selected) 3.dp else 1.dp, if (selected) colors.accent else colors.contentMuted.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            thumbs[l]?.let { Image(it, null, Modifier.size(64.dp), contentScale = ContentScale.Crop) }
                            if (lLocked) com.karapp.drawguidance.ui.premium.PremiumLockBadge(Modifier.align(Alignment.TopEnd).padding(2.dp))
                        }
                        Text(
                            lookLabel(l), style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center,
                            color = if (selected) colors.accent else colors.content, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Text(stringResource(R.string.image_effects_adjust), style = MaterialTheme.typography.titleSmall, color = colors.accent)
            LabeledSlider(stringResource(R.string.adj_brightness), adjust.brightness, -1f..1f, "adj_brightness") { adjust = adjust.copy(brightness = it) }
            LabeledSlider(stringResource(R.string.adj_contrast), adjust.contrast, -1f..1f, "adj_contrast") { adjust = adjust.copy(contrast = it) }
            LabeledSlider(stringResource(R.string.adj_saturation), adjust.saturation, -1f..1f, "adj_saturation") { adjust = adjust.copy(saturation = it) }
            LabeledSlider(stringResource(R.string.adj_warmth), adjust.warmth, -1f..1f, "adj_warmth") { adjust = adjust.copy(warmth = it) }
            LabeledSlider(stringResource(R.string.adj_tint), adjust.tint, -1f..1f, "adj_tint") { adjust = adjust.copy(tint = it) }
            LabeledSlider(stringResource(R.string.adj_blur), adjust.blur, 0f..1f, "adj_blur", locked = advLocked && "blur" in com.karapp.drawguidance.data.billing.PremiumRules.ADVANCED_ADJUSTMENTS, onLocked = { premium.request(com.karapp.drawguidance.data.billing.PremiumFeature.IMAGE_EFFECTS_ADVANCED) }) { adjust = adjust.copy(blur = it) }
            LabeledSlider(stringResource(R.string.adj_sharpen), adjust.sharpen, 0f..1f, "adj_sharpen", locked = advLocked && "sharpen" in com.karapp.drawguidance.data.billing.PremiumRules.ADVANCED_ADJUSTMENTS, onLocked = { premium.request(com.karapp.drawguidance.data.billing.PremiumFeature.IMAGE_EFFECTS_ADVANCED) }) { adjust = adjust.copy(sharpen = it) }
            LabeledSlider(stringResource(R.string.adj_vignette), adjust.vignette, 0f..1f, "adj_vignette", locked = advLocked && "vignette" in com.karapp.drawguidance.data.billing.PremiumRules.ADVANCED_ADJUSTMENTS, onLocked = { premium.request(com.karapp.drawguidance.data.billing.PremiumFeature.IMAGE_EFFECTS_ADVANCED) }) { adjust = adjust.copy(vignette = it) }
            LabeledSlider(stringResource(R.string.adj_grain), adjust.grain, 0f..1f, "adj_grain", locked = advLocked && "grain" in com.karapp.drawguidance.data.billing.PremiumRules.ADVANCED_ADJUSTMENTS, onLocked = { premium.request(com.karapp.drawguidance.data.billing.PremiumFeature.IMAGE_EFFECTS_ADVANCED) }) { adjust = adjust.copy(grain = it) }
            LabeledSlider(stringResource(R.string.adj_fade), adjust.fade, 0f..1f, "adj_fade", locked = advLocked && "fade" in com.karapp.drawguidance.data.billing.PremiumRules.ADVANCED_ADJUSTMENTS, onLocked = { premium.request(com.karapp.drawguidance.data.billing.PremiumFeature.IMAGE_EFFECTS_ADVANCED) }) { adjust = adjust.copy(fade = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuButton(stringResource(R.string.image_tool_reset), { look = ImageLook.NONE; adjust = ImageAdjust() }, Modifier.weight(1f), icon = Icons.Filled.RestartAlt)
                NeuButton(
                    stringResource(R.string.apply_button), { onApply(look, adjust) }, Modifier.weight(1f).testTag("image_effects_apply"),
                    primary = true, enabled = !busy && source != null && (look != ImageLook.NONE || !adjust.isIdentity), icon = Icons.Filled.Check,
                )
            }
        }
    }
}

@Composable
fun lookLabel(look: ImageLook): String = stringResource(
    when (look) {
        ImageLook.NONE -> R.string.look_none
        ImageLook.GRAYSCALE -> R.string.look_grayscale
        ImageLook.SEPIA -> R.string.look_sepia
        ImageLook.INVERT -> R.string.look_invert
        ImageLook.VINTAGE -> R.string.look_vintage
        ImageLook.COOL -> R.string.look_cool
        ImageLook.WARM -> R.string.look_warm
        ImageLook.NOIR -> R.string.look_noir
        ImageLook.VIVID -> R.string.look_vivid
        ImageLook.DUOTONE -> R.string.look_duotone
        ImageLook.POSTERIZE -> R.string.look_posterize
        ImageLook.PIXELATE -> R.string.look_pixelate
        ImageLook.SKETCH -> R.string.look_sketch
        ImageLook.EMBOSS -> R.string.look_emboss
        ImageLook.OIL -> R.string.look_oil
        ImageLook.GLOW -> R.string.look_glow
    },
)

/** Dokunma aracı: kaldır / geri getir. */
private enum class TapTool { REMOVE, KEEP }

/**
 * v30 "Arka planı kaldır": kenardan otomatik bulma, tolerans ve kenar yumuşaklığı, iç boşluklar; önizlemeye
 * dokunarak bölge kaldırma ya da geri getirme. Önizleme damalı zeminde canlı güncellenir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundRemoveSheet(
    loadPreview: suspend (Int) -> Bitmap?,
    busy: Boolean,
    onApply: (BackgroundOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NeuTheme.colors
    var source by remember { mutableStateOf<Pixels?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var removed by remember { mutableStateOf(0f) }
    var computing by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(BackgroundOptions()) }
    var tool by remember { mutableStateOf(TapTool.REMOVE) }
    // Dokunuş sırası (son dokunuşu geri almak için)
    var taps by remember { mutableStateOf(listOf<Pair<TapTool, Pair<Float, Float>>>()) }
    LaunchedEffect(Unit) {
        val bmp = loadPreview(PREVIEW_SIDE) ?: return@LaunchedEffect
        source = withContext(Dispatchers.Default) { bmp.pixels() }
    }
    LaunchedEffect(source, options) {
        val p = source ?: return@LaunchedEffect
        delay(80)
        computing = true
        val (img, ratio) = withContext(Dispatchers.Default) {
            val mask = BackgroundRemover.mask(p.px, p.w, p.h, options)
            toImage(BackgroundRemover.apply(p.px, p.w, p.h, mask), p.w, p.h) to mask.removedRatio
        }
        preview = img; removed = ratio
        computing = false
    }
    fun setTaps(list: List<Pair<TapTool, Pair<Float, Float>>>) {
        taps = list
        options = options.copy(removeAt = list.filter { it.first == TapTool.REMOVE }.map { it.second }, keepAt = list.filter { it.first == TapTool.KEEP }.map { it.second })
    }
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp)
                .testTag("bg_remove_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.bg_remove_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.bg_remove_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            CheckerPreview(preview, source?.let { it.w / it.h.toFloat() } ?: 1f, computing || busy) { u, v -> setTaps(taps + (tool to (u to v))) }
            Text(
                stringResource(R.string.bg_remove_ratio, (removed * 100).toInt()),
                style = MaterialTheme.typography.labelMedium, color = colors.contentMuted, modifier = Modifier.testTag("bg_remove_ratio"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NeuChip(stringResource(R.string.bg_tap_remove), tool == TapTool.REMOVE, { tool = TapTool.REMOVE }, Modifier.weight(1f).testTag("bg_tap_remove"))
                NeuChip(stringResource(R.string.bg_tap_keep), tool == TapTool.KEEP, { tool = TapTool.KEEP }, Modifier.weight(1f).testTag("bg_tap_keep"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.bg_auto), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.bg_auto_hint), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
                }
                NeuSwitch(options.auto, { options = options.copy(auto = it) }, Modifier.testTag("bg_auto"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.bg_holes), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.bg_holes_hint), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
                }
                NeuSwitch(options.removeHoles, { options = options.copy(removeHoles = it) }, Modifier.testTag("bg_holes"))
            }
            LabeledSlider(stringResource(R.string.bg_tolerance), options.tolerance, 0f..1f, "bg_tolerance") { options = options.copy(tolerance = it) }
            LabeledSlider(stringResource(R.string.bg_feather), options.feather, 0f..1f, "bg_feather") { options = options.copy(feather = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuButton(
                    stringResource(R.string.bg_undo_tap), { setTaps(taps.dropLast(1)) }, Modifier.weight(1f),
                    enabled = taps.isNotEmpty(), icon = Icons.AutoMirrored.Filled.Undo,
                )
                // v39 arka plan kaldırma Premium: önizleme açık, uygulama kilitli
                val premium = com.karapp.drawguidance.ui.premium.LocalPremium.current
                val bgLocked = premium.locked(com.karapp.drawguidance.data.billing.PremiumFeature.IMAGE_BG_REMOVAL)
                NeuButton(
                    stringResource(R.string.apply_button), { if (premium.request(com.karapp.drawguidance.data.billing.PremiumFeature.IMAGE_BG_REMOVAL)) onApply(options) },
                    Modifier.weight(1f).testTag(if (bgLocked) "bg_remove_apply_locked" else "bg_remove_apply"),
                    primary = true, enabled = !busy && source != null, icon = if (bgLocked) Icons.Filled.WorkspacePremium else Icons.Filled.AutoFixHigh,
                )
            }
        }
    }
}

/** Araç simgeleri (üst menü ve bağlam menüsü). */
val ImageEffectsIcon = Icons.Filled.AutoAwesome
val BackgroundRemoveIcon = Icons.Filled.AutoFixHigh
