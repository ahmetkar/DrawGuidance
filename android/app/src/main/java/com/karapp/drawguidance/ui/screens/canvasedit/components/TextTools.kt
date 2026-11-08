package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.ui.semantics.selected

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import com.karapp.drawguidance.ui.components.NeuButton
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextParagraph
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSectionTitle
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.TextSettings
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import com.karapp.drawguidance.ui.components.NeuSheet

/** Yazı tipinin listede görünen adı. */
fun fontLabel(font: TextFont): String = font.title

/** Ekranda yazı tipinin kendisiyle gösterilmesi için Compose karşılığı. */
fun composeFamily(font: TextFont): FontFamily = com.karapp.drawguidance.ui.drawing.FontRegistry.family(font)

@Composable
private fun fontGroupLabel(group: com.karapp.drawguidance.data.model.FontGroup): String = stringResource(
    when (group) {
        com.karapp.drawguidance.data.model.FontGroup.SANS -> R.string.font_group_sans
        com.karapp.drawguidance.data.model.FontGroup.SERIF -> R.string.font_group_serif
        com.karapp.drawguidance.data.model.FontGroup.HAND -> R.string.font_group_hand
        com.karapp.drawguidance.data.model.FontGroup.DISPLAY -> R.string.font_group_display
        com.karapp.drawguidance.data.model.FontGroup.COMIC -> R.string.font_group_comic
        com.karapp.drawguidance.data.model.FontGroup.MONO -> R.string.font_group_mono
    },
)

/** Seçili yazı tipini kendi harfleriyle gösteren düğme; dokununca yazı tipi listesi açılır. */
@Composable
fun FontPickerButton(font: TextFont, onPick: (TextFont) -> Unit, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.text_font_pick_cd, font.title)
    com.karapp.drawguidance.ui.components.NeuSurface(
        shape = com.karapp.drawguidance.ui.theme.NeuPillShape,
        elevation = 4.dp,
        onClick = { open = true },
        modifier = modifier
            .heightIn(min = 40.dp)
            .testTag("font_picker_button")
            .androidxSemantics(description),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Aa", fontFamily = composeFamily(font), style = MaterialTheme.typography.titleMedium, color = colors.accent)
            Text(
                font.title, fontFamily = composeFamily(font), style = MaterialTheme.typography.bodyLarge, color = colors.content,
                maxLines = 1, modifier = Modifier.weight(1f, fill = false),
            )
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Filled.ArrowDropDown, null, tint = colors.contentMuted,
            )
        }
    }
    if (open) FontPickerSheet(selected = font, onPick = { open = false; onPick(it) }, onDismiss = { open = false })
}

private fun Modifier.androidxSemantics(description: String) =
    this.then(Modifier.semantics(mergeDescendants = true) { contentDescription = description })

/** Yazı tipi listesi: gruplara ayrılmış, her yazı tipi kendi harfleriyle ve Türkçe bir örnekle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontPickerSheet(selected: TextFont, onPick: (TextFont) -> Unit, onDismiss: () -> Unit) {
    val colors = NeuTheme.colors
    NeuSheet(onDismiss) {
        Text(
            stringResource(R.string.text_font_label), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
        )
        val groups = remember { TextFont.entries.groupBy { it.group } }
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxWidth().testTag("font_list").navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            com.karapp.drawguidance.data.model.FontGroup.entries.forEach { group ->
                val fonts = groups[group].orEmpty()
                if (fonts.isEmpty()) return@forEach
                item(key = "g-${group.name}") {
                    Text(
                        fontGroupLabel(group), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted,
                        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
                    )
                }
                fonts.forEach { font ->
                    item(key = font.name) {
                        val isSelected = font == selected
                        com.karapp.drawguidance.ui.components.NeuSurface(
                            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = font.title },
                            elevation = if (isSelected) 2.dp else 4.dp,
                            selected = isSelected,
                            onClick = { onPick(font) },
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    font.title, fontFamily = composeFamily(font), style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) colors.accent else colors.content, maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    stringResource(R.string.font_sample), fontFamily = composeFamily(font),
                                    style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted, maxLines = 1,
                                )
                                if (isSelected) {
                                    androidx.compose.material3.Icon(
                                        androidx.compose.material.icons.Icons.Filled.Check, null, tint = colors.accent,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Not tuvalinde paragraf biçimi: düz metin, üç başlık seviyesi, madde işaretli / numaralı / yapılacaklar listesi
 * ve listede girintiyi artırma/azaltma.
 */
@Composable
fun ParagraphRow(
    paragraph: com.karapp.drawguidance.data.model.TextParagraph,
    onChange: (com.karapp.drawguidance.data.model.TextParagraph) -> Unit,
    modifier: Modifier = Modifier,
    onIndent: ((Int) -> Unit)? = null,
    buttonSize: androidx.compose.ui.unit.Dp = 38.dp,
) {
    Column(modifier.testTag("paragraph_row"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(TextParagraph.NORMAL to R.string.text_paragraph_normal_short, TextParagraph.H1 to R.string.text_paragraph_h1_short,
                TextParagraph.H2 to R.string.text_paragraph_h2_short, TextParagraph.H3 to R.string.text_paragraph_h3_short).forEach { (p, label) ->
                val description = paragraphLabelRes(p)
                NeuChip(
                    text = stringResource(label),
                    selected = paragraph == p,
                    onClick = { onChange(p) },
                    modifier = Modifier.weight(1f).semantics { contentDescription = description },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuIconButton(
                Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.text_paragraph_bullet),
                { onChange(if (paragraph == TextParagraph.BULLET) TextParagraph.NORMAL else TextParagraph.BULLET) }, size = buttonSize, selected = paragraph == TextParagraph.BULLET,
            )
            NeuIconButton(
                Icons.Filled.FormatListNumbered, stringResource(R.string.text_paragraph_numbered),
                { onChange(if (paragraph == TextParagraph.NUMBERED) TextParagraph.NORMAL else TextParagraph.NUMBERED) }, size = buttonSize, selected = paragraph == TextParagraph.NUMBERED,
            )
            NeuIconButton(
                Icons.Filled.Checklist, stringResource(R.string.text_paragraph_checklist),
                { onChange(if (paragraph == TextParagraph.CHECKLIST) TextParagraph.NORMAL else TextParagraph.CHECKLIST) }, size = buttonSize, selected = paragraph == TextParagraph.CHECKLIST,
            )
            if (onIndent != null && paragraph.isList) {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                NeuIconButton(Icons.AutoMirrored.Filled.FormatIndentDecrease, stringResource(R.string.text_outdent), { onIndent(-1) }, size = buttonSize)
                NeuIconButton(Icons.AutoMirrored.Filled.FormatIndentIncrease, stringResource(R.string.text_indent), { onIndent(1) }, size = buttonSize)
            }
        }
    }
}

/**
 * v16: paragraf biçimi tek satırda (satır içi yazı çubuğu için; kaydırılabilir bir satırın içinde kullanılır):
 * Normal/Başlık 1–3, madde, numara, yapılacaklar listesi ve listede girinti.
 */
@Composable
fun ParagraphInlineControls(
    paragraph: com.karapp.drawguidance.data.model.TextParagraph,
    onChange: (com.karapp.drawguidance.data.model.TextParagraph) -> Unit,
    onIndent: ((Int) -> Unit)?,
    buttonSize: androidx.compose.ui.unit.Dp = 36.dp,
) {
    listOf(TextParagraph.NORMAL to R.string.text_paragraph_normal_short, TextParagraph.H1 to R.string.text_paragraph_h1_short,
        TextParagraph.H2 to R.string.text_paragraph_h2_short, TextParagraph.H3 to R.string.text_paragraph_h3_short).forEach { (p, label) ->
        val description = paragraphLabelRes(p)
        NeuChip(
            text = stringResource(label),
            selected = paragraph == p,
            onClick = { onChange(p) },
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
    NeuIconButton(
        Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.text_paragraph_bullet),
        { onChange(if (paragraph == TextParagraph.BULLET) TextParagraph.NORMAL else TextParagraph.BULLET) }, size = buttonSize, selected = paragraph == TextParagraph.BULLET,
    )
    NeuIconButton(
        Icons.Filled.FormatListNumbered, stringResource(R.string.text_paragraph_numbered),
        { onChange(if (paragraph == TextParagraph.NUMBERED) TextParagraph.NORMAL else TextParagraph.NUMBERED) }, size = buttonSize, selected = paragraph == TextParagraph.NUMBERED,
    )
    NeuIconButton(
        Icons.Filled.Checklist, stringResource(R.string.text_paragraph_checklist),
        { onChange(if (paragraph == TextParagraph.CHECKLIST) TextParagraph.NORMAL else TextParagraph.CHECKLIST) }, size = buttonSize, selected = paragraph == TextParagraph.CHECKLIST,
    )
    if (onIndent != null && paragraph.isList) {
        NeuIconButton(Icons.AutoMirrored.Filled.FormatIndentDecrease, stringResource(R.string.text_outdent), { onIndent(-1) }, size = buttonSize)
        NeuIconButton(Icons.AutoMirrored.Filled.FormatIndentIncrease, stringResource(R.string.text_indent), { onIndent(1) }, size = buttonSize)
    }
}

@Composable
private fun paragraphLabelRes(p: com.karapp.drawguidance.data.model.TextParagraph): String = stringResource(
    when (p) {
        com.karapp.drawguidance.data.model.TextParagraph.NORMAL -> R.string.text_paragraph_normal
        com.karapp.drawguidance.data.model.TextParagraph.H1 -> R.string.text_paragraph_h1
        com.karapp.drawguidance.data.model.TextParagraph.H2 -> R.string.text_paragraph_h2
        com.karapp.drawguidance.data.model.TextParagraph.H3 -> R.string.text_paragraph_h3
        com.karapp.drawguidance.data.model.TextParagraph.BULLET -> R.string.text_paragraph_bullet
        com.karapp.drawguidance.data.model.TextParagraph.NUMBERED -> R.string.text_paragraph_numbered
        com.karapp.drawguidance.data.model.TextParagraph.CHECKLIST -> R.string.text_paragraph_checklist
    },
)

/**
 * Yazı tipi ayarları: yazı tipi (liste), boyut, kalın/eğik/altı çizili ve hizalama; not tuvalinde paragraf
 * biçimi (başlık/liste). Hem yazı penceresinde hem de ayarlar panelinde (seçili yazılara uygulanır) kullanılır.
 */
@Composable
fun TextStyleControls(settings: TextSettings, onChange: (TextSettings) -> Unit, modifier: Modifier = Modifier, notes: Boolean = false, effects: Boolean = true) {
    val colors = NeuTheme.colors
    val spec = settings.spec
    Column(modifier.testTag("text_style_controls"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NeuSectionTitle(stringResource(R.string.text_font_label))
        FontPickerButton(spec.font, { onChange(settings.copy(spec = spec.copy(font = it))) }, Modifier.fillMaxWidth())
        if (notes) {
            ParagraphRow(spec.paragraph, { onChange(settings.copy(spec = spec.copy(paragraph = it))) }, buttonSize = 40.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.text_size_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("${settings.sizePx.roundToInt()} px", style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
            }
            NeuSlider(
                value = settings.sizePx,
                onValueChange = { onChange(settings.copy(sizePx = it.roundToInt().toFloat())) },
                valueRange = CanvasEditViewModel.MIN_TEXT_SIZE..240f,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuIconButton(Icons.Filled.FormatBold, stringResource(R.string.text_bold), { onChange(settings.copy(spec = spec.copy(bold = !spec.bold))) }, size = 40.dp, selected = spec.bold)
            NeuIconButton(Icons.Filled.FormatItalic, stringResource(R.string.text_italic), { onChange(settings.copy(spec = spec.copy(italic = !spec.italic))) }, size = 40.dp, selected = spec.italic)
            NeuIconButton(Icons.Filled.FormatUnderlined, stringResource(R.string.text_underline), { onChange(settings.copy(spec = spec.copy(underline = !spec.underline))) }, size = 40.dp, selected = spec.underline)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            NeuIconButton(Icons.AutoMirrored.Filled.FormatAlignLeft, stringResource(R.string.text_align_left), { onChange(settings.copy(spec = spec.copy(align = TextAlignKind.LEFT))) }, size = 40.dp, selected = spec.align == TextAlignKind.LEFT)
            NeuIconButton(Icons.Filled.FormatAlignCenter, stringResource(R.string.text_align_center), { onChange(settings.copy(spec = spec.copy(align = TextAlignKind.CENTER))) }, size = 40.dp, selected = spec.align == TextAlignKind.CENTER)
            NeuIconButton(Icons.AutoMirrored.Filled.FormatAlignRight, stringResource(R.string.text_align_right), { onChange(settings.copy(spec = spec.copy(align = TextAlignKind.RIGHT))) }, size = 40.dp, selected = spec.align == TextAlignKind.RIGHT)
        }
        if (effects) TextEffectsControls(settings, onChange)
    }
}

/**
 * v27 yazı efektleri (çizgi roman yazılarında yaygın): gölge, gradyan dolgu, BÜYÜK HARF ve harf aralığı. Kenarlık
 * ayrıca yazı ayarlarında (harf kenarlığı).
 */
@Composable
fun TextEffectsControls(settings: TextSettings, onChange: (TextSettings) -> Unit) {
    val colors = NeuTheme.colors
    val spec = settings.spec
    val fx = spec.box?.effects ?: com.karapp.drawguidance.data.model.TextEffects()
    fun set(e: com.karapp.drawguidance.data.model.TextEffects) {
        val box = (spec.box ?: com.karapp.drawguidance.data.model.TextBox()).copy(effects = e.takeUnless { it.isEmpty })
        onChange(settings.copy(spec = spec.copy(box = box.orNull())))
    }
    Column(Modifier.testTag("text_effects"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NeuSectionTitle(stringResource(R.string.text_effects))
        Text(stringResource(R.string.text_shadow), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuChip(stringResource(R.string.text_shadow_none), fx.shadowHex == null, onClick = { set(fx.copy(shadowHex = null)) })
            listOf("#000000", "#666666", "#E53935", "#1E88E5", "#FFFFFF").forEach { hex ->
                val chosen = fx.shadowHex.equals(hex, ignoreCase = true)
                androidx.compose.foundation.layout.Box(
                    Modifier.size(30.dp).clip(CircleShape).background(com.karapp.drawguidance.ui.common.hexToColor(hex))
                        .border(if (chosen) 3.dp else 1.dp, if (chosen) colors.accent else colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                        .clickable { set(fx.copy(shadowHex = hex)) }
                        .semantics { contentDescription = hex; if (chosen) selected = true },
                )
            }
            NeuChip(stringResource(R.string.text_shadow_soft), fx.shadowBlur > 0f, onClick = { set(fx.copy(shadowBlur = if (fx.shadowBlur > 0f) 0f else 0.12f)) })
        }
        Text(stringResource(R.string.text_gradient), style = MaterialTheme.typography.labelLarge)
        com.karapp.drawguidance.ui.components.GradientPicker(fx.gradient, onChange = { set(fx.copy(gradient = it)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuChip(stringResource(R.string.text_caps), fx.caps, onClick = { set(fx.copy(caps = !fx.caps)) }, modifier = Modifier.testTag("text_caps"))
            Text(stringResource(R.string.text_letter_spacing), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        }
        NeuSlider(value = fx.letterSpacing, onValueChange = { set(fx.copy(letterSpacing = (it * 100).roundToInt() / 100f)) }, valueRange = -0.05f..0.4f)
    }
}

/** v23 yazı kutusu arka plan renkleri (açık, yazıyı bozmayan tonlar). */
private val BoxFillSwatches = listOf("#FFFFFF", "#FFF7D6", "#FFE9EE", "#E8F4FF", "#E9F7EC", "#F3ECFF", "#F6F1E7", "#FFF0DE", "#EDEFF2", "#2E3440")

/**
 * v23 not tuvalinde yazı kutusu: eni ve en az boyu (otomatik ya da sabit; kutunun kenarındaki tutamaklarla da
 * değişir), arka planı (renk, not defteri desenleri, hazır resimler), köşe yuvarlaklığı ve kenar çizgisi.
 * [onStyle] görünümü (seçili tüm yazılara ve yeni yazılara), [onSize] seçili yazıların boyutunu değiştirir.
 */
@Composable
fun TextBoxControls(
    box: com.karapp.drawguidance.data.model.TextBox?,
    sizePx: Float,
    onStyle: (com.karapp.drawguidance.data.model.TextBox?) -> Unit,
    onSize: ((width: Float?, height: Float?, setWidth: Boolean, setHeight: Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    val b = box ?: com.karapp.drawguidance.data.model.TextBox()
    fun style(next: com.karapp.drawguidance.data.model.TextBox) = onStyle(next.orNull())
    val items = com.karapp.drawguidance.ui.components.rememberPaperItems()
    val boxPatterns = androidx.compose.runtime.remember(items) {
        items.filter { !it.isImage && it.usage == com.karapp.drawguidance.content.paper.PaperUsage.BOX } +
            items.filter { !it.isImage && it.usage == com.karapp.drawguidance.content.paper.PaperUsage.NOTE }
    }
    val boxImages = androidx.compose.runtime.remember(items) { items.filter { it.isImage && it.usage == com.karapp.drawguidance.content.paper.PaperUsage.BOX } }
    val imagesVersion by com.karapp.drawguidance.data.images.CanvasImageCache.version.collectAsState()
    var tab by androidx.compose.runtime.saveable.rememberSaveable(b.imageId != null, b.patternId != null, b.fillHex != null) {
        androidx.compose.runtime.mutableIntStateOf(
            when {
                b.imageId != null -> 3
                b.patternId != null -> 2
                b.fillHex != null -> 1
                else -> 0
            },
        )
    }
    Column(modifier.testTag("text_box_controls"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NeuSectionTitle(stringResource(R.string.text_box_title))
        if (onSize != null) {
            val autoW = b.width == null
            val autoH = b.height == null
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.text_box_width), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                NeuChip(stringResource(R.string.text_box_auto), autoW, onClick = { onSize(null, null, true, false) })
                NeuChip(stringResource(R.string.text_box_fixed), !autoW, onClick = { if (autoW) onSize(sizePx * 12f, null, true, false) })
            }
            if (!autoW) NeuSlider(b.width ?: 0f, { onSize(it.roundToInt().toFloat(), null, true, false) }, (sizePx * 2f)..(sizePx * 80f).coerceAtLeast(sizePx * 2f + 1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.text_box_height), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                NeuChip(stringResource(R.string.text_box_auto), autoH, onClick = { onSize(null, null, false, true) })
                NeuChip(stringResource(R.string.text_box_fixed), !autoH, onClick = { if (autoH) onSize(null, sizePx * 6f, false, true) })
            }
            if (!autoH) NeuSlider(b.height ?: 0f, { onSize(null, it.roundToInt().toFloat(), false, true) }, sizePx..(sizePx * 120f))
            Text(stringResource(R.string.text_box_handles_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf(R.string.bg_none, R.string.text_box_color, R.string.bg_pattern, R.string.bg_image).forEachIndexed { i, label ->
                NeuChip(stringResource(label), tab == i, onClick = {
                    tab = i
                    if (i == 0) style(b.copy(fillHex = null, patternId = null, imageId = null, borderHex = null))
                    if (i == 1 && b.fillHex == null) style(b.copy(fillHex = BoxFillSwatches[1], imageId = null))
                    if (i == 2 && b.patternId == null) style(b.copy(patternId = boxPatterns.firstOrNull()?.id, imageId = null, fillHex = b.fillHex ?: "#FFFFFF"))
                    if (i == 3 && b.imageId == null) style(b.copy(imageId = boxImages.firstOrNull()?.id, patternId = null, fillHex = null, imageFit = com.karapp.drawguidance.data.model.BoxFit.NINE_SLICE))
                })
            }
        }
        if (tab == 1 || tab == 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                BoxFillSwatches.forEach { hex ->
                    val chosen = hex.equals(b.fillHex, ignoreCase = true)
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .border(if (chosen) 3.dp else 1.dp, if (chosen) colors.accent else colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                            .padding(4.dp).background(com.karapp.drawguidance.ui.common.hexToColor(hex), CircleShape)
                            .clickable { style(b.copy(fillHex = hex)) }
                            .semantics { contentDescription = hex },
                    )
                }
            }
        }
        if (tab == 2) {
            val paper = com.karapp.drawguidance.ui.drawing.BackgroundRenderer.parseColor(b.fillHex, 0xFFFFFFFF.toInt())
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(boxPatterns.size) { i ->
                    val item = boxPatterns[i]
                    com.karapp.drawguidance.ui.components.Tile(item.name, b.patternId == item.id, onClick = { style(b.copy(patternId = item.id)) }, size = 60.dp) { m ->
                        com.karapp.drawguidance.ui.components.PatternThumb(item.id, b.patternColorHex, paper, m, span = 240f)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                com.karapp.drawguidance.ui.components.PatternColorSwatches.forEach { hex ->
                    val chosen = hex.equals(b.patternColorHex, ignoreCase = true)
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .border(if (chosen) 3.dp else 1.dp, if (chosen) colors.accent else colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                            .padding(4.dp).background(com.karapp.drawguidance.ui.common.hexToColor(hex), CircleShape)
                            .clickable { style(b.copy(patternColorHex = hex)) }
                            .semantics { contentDescription = hex },
                    )
                }
            }
        }
        if (tab == 3) {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(boxImages.size) { i ->
                    val item = boxImages[i]
                    com.karapp.drawguidance.ui.components.Tile(item.name, b.imageId == item.id, onClick = {
                        style(b.copy(imageId = item.id, imageFit = com.karapp.drawguidance.data.model.BoxFit.NINE_SLICE))
                    }, size = 60.dp) { m -> com.karapp.drawguidance.ui.components.ImageThumb(item.id, m, imagesVersion) }
                }
            }
        }
        if (tab >= 2) {
            com.karapp.drawguidance.ui.components.LabeledValueSlider(
                stringResource(if (tab == 3) R.string.text_box_image_scale else R.string.bg_scale), "${(b.patternScale * 100).roundToInt()}%",
                b.patternScale, 0.3f..3f,
            ) { style(b.copy(patternScale = (it * 20f).roundToInt() / 20f)) }
        }
        if (tab != 0 && tab != 3) {
            com.karapp.drawguidance.ui.components.LabeledValueSlider(stringResource(R.string.text_box_corner), "${b.corner.roundToInt()}", b.corner, 0f..60f) {
                style(b.copy(corner = it.roundToInt().toFloat()))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.text_box_border), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                com.karapp.drawguidance.ui.components.NeuSwitch(b.borderHex != null, { on -> style(b.copy(borderHex = if (on) "#5B6573" else null)) })
            }
        }
    }
}
