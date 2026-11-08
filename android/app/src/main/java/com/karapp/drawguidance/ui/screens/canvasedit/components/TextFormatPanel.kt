package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSectionTitle
import com.karapp.drawguidance.ui.screens.canvasedit.TextSettings
import com.karapp.drawguidance.ui.theme.NeuTheme

/** v29 yazı biçimi panelinin bölümleri. */
enum class TextFormatTab { FONT, COLOR, EFFECTS, BOX }

/**
 * v29 tek yazı biçimi alanı: bütün yazı ayarları bir yerde ve her ayar bir kez. Dört bölüm:
 * - **Yazı:** yazı tipi, boyut, kalın/eğik/altı çizili, yazının kutudaki hizası (not tuvalinde başlık/liste);
 * - **Renk:** harflerin rengi ve harf kenarlığı (renk, kalınlık);
 * - **Efekt:** gölge, gradyan, büyük harf, harf aralığı;
 * - **Kutu:** not tuvalinde yazı kutusu (en, boy, arka plan) ve seçili yazının sayfadaki yeri.
 *
 * Elemanlar panelinde seçili yazılar için, Fırça panelinde yeni yazıların biçimi için ve yazı düzenlenirken
 * ([tabs] ile yalnız düzenleme çubuğunda olmayan bölümler) aynı panel kullanılır. [onChange] yeni biçimi verir;
 * yalnız değişen alanlar uygulanır.
 */
@Composable
fun TextFormatPanel(
    settings: TextSettings,
    onChange: (TextSettings) -> Unit,
    modifier: Modifier = Modifier,
    notes: Boolean = false,
    /** Harflerin rengi; null ise Renk bölümünde gösterilmez (yeni yazılar kalem rengini alır). */
    fillColorHex: String? = null,
    onFill: ((String) -> Unit)? = null,
    /** Seçili yazıların kutu eni/boyu (null: kutu boyutu ayarı yok). */
    onBoxSize: ((width: Float?, height: Float?, setWidth: Boolean, setHeight: Boolean) -> Unit)? = null,
    /** Seçili yazıları sayfada sola/ortaya/sağa yerleştirir (null: yok). */
    onPlace: ((TextAlignKind) -> Unit)? = null,
    tabs: List<TextFormatTab> = TextFormatTab.entries,
    initialTab: TextFormatTab = tabs.first(),
) {
    val shown = tabs.filter { it != TextFormatTab.BOX || notes || onPlace != null }
    var tab by rememberSaveable { mutableStateOf(initialTab.takeIf { it in shown } ?: shown.first()) }
    Column(modifier.testTag("text_format_panel"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (shown.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("text_format_tabs"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shown.forEach { t ->
                    NeuChip(
                        stringResource(
                            when (t) {
                                TextFormatTab.FONT -> R.string.text_tab_font
                                TextFormatTab.COLOR -> R.string.text_tab_color
                                TextFormatTab.EFFECTS -> R.string.text_tab_effects
                                TextFormatTab.BOX -> if (notes) R.string.text_tab_box else R.string.text_tab_place
                            },
                        ),
                        tab == t,
                        onClick = { tab = t },
                        modifier = Modifier.testTag("text_tab_${t.name}"),
                    )
                }
            }
        }
        when (tab) {
            TextFormatTab.FONT -> TextStyleControls(settings, onChange, notes = notes, effects = false)
            TextFormatTab.COLOR -> TextColorControls(settings, onChange, fillColorHex, onFill)
            TextFormatTab.EFFECTS -> TextEffectsControls(settings, onChange)
            TextFormatTab.BOX -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onPlace != null) {
                    Column(Modifier.testTag("text_place"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeuSectionTitle(stringResource(R.string.text_place_title))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NeuButton(stringResource(R.string.align_left), { onPlace(TextAlignKind.LEFT) }, Modifier.weight(1f))
                            NeuButton(stringResource(R.string.align_center), { onPlace(TextAlignKind.CENTER) }, Modifier.weight(1f))
                            NeuButton(stringResource(R.string.align_right), { onPlace(TextAlignKind.RIGHT) }, Modifier.weight(1f))
                        }
                    }
                }
                if (notes) {
                    TextBoxControls(
                        settings.spec.box, settings.sizePx,
                        onStyle = { b -> onChange(settings.copy(spec = settings.spec.copy(box = b))) },
                        onSize = onBoxSize,
                    )
                }
            }
        }
    }
}

/**
 * v29 yazının renkleri: harflerin iç rengi ([fillColorHex], verilmişse) ve harflerin çevresindeki kenarlık (rengi,
 * kalınlığı). Kenarlık yazı biçiminin bir parçasıdır ([onChange]).
 */
@Composable
fun TextColorControls(
    settings: TextSettings,
    onChange: (TextSettings) -> Unit,
    fillColorHex: String?,
    onFill: ((String) -> Unit)?,
) {
    val spec = settings.spec
    val outlineColor = spec.outlineColorHex
    val outlineWidth = spec.outlineWidth
    var fillPicker by remember { mutableStateOf(false) }
    var outlinePicker by remember { mutableStateOf(false) }
    fun outline(color: String?, width: Float) {
        val c = color?.uppercase()?.takeIf { width > 0f }
        val w = if (c == null) 0f else width.coerceIn(0f, 64f)
        onChange(settings.copy(spec = spec.copy(outlineColorHex = c, outlineWidth = w)))
    }
    Column(Modifier.fillMaxWidth().testTag("text_fill_border_section"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (fillColorHex != null && onFill != null) {
            ColorRow(
                label = stringResource(R.string.text_fill_color),
                colorHex = fillColorHex,
                expanded = fillPicker,
                onToggle = { fillPicker = !fillPicker; if (fillPicker) outlinePicker = false },
            )
            AnimatedVisibility(fillPicker) {
                HsvColorPicker(colorHex = fillColorHex, onColorChange = { onFill(it.uppercase()) })
            }
        }
        ColorRow(
            label = stringResource(R.string.text_outline_color),
            colorHex = outlineColor,
            expanded = outlinePicker,
            onToggle = { outlinePicker = !outlinePicker; if (outlinePicker) fillPicker = false },
        )
        AnimatedVisibility(outlinePicker) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuChip(
                        stringResource(R.string.text_outline_none),
                        selected = outlineColor == null || outlineWidth <= 0f,
                        onClick = { outline(null, 0f) },
                        modifier = Modifier.weight(1f),
                    )
                    NeuChip(
                        stringResource(R.string.text_outline_on),
                        selected = outlineColor != null && outlineWidth > 0f,
                        onClick = { outline(outlineColor ?: DEFAULT_TEXT_OUTLINE, outlineWidth.takeIf { it > 0f } ?: 3f) },
                        modifier = Modifier.weight(1f),
                    )
                }
                HsvColorPicker(
                    colorHex = outlineColor ?: DEFAULT_TEXT_OUTLINE,
                    onColorChange = { outline(it, outlineWidth.takeIf { w -> w > 0f } ?: 3f) },
                )
            }
        }
        SliderRow(
            stringResource(R.string.text_outline_width), "${outlineWidth.toInt()} px",
            outlineWidth.coerceIn(0f, 16f), 0f..16f,
        ) { outline(if (it <= 0f) null else (outlineColor ?: DEFAULT_TEXT_OUTLINE), it) }
        if (fillColorHex == null) {
            Text(stringResource(R.string.text_color_from_pen), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
        }
    }
}
