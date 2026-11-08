package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.Palette
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.components.NeuSheet

/**
 * Boya kutusu penceresi: yalnızca "Renk seç" başlığı ve renk seçici. Seçilen renk anında uygulanır
 * (tek geri alma adımı); pencere kaydırılarak ya da dışına dokunularak kapanır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickSheet(
    initialColorHex: String,
    onColorChange: (String) -> Unit,
    onDismiss: () -> Unit,
    /** v27 gradyan dolgu (null verilirse yalnız renk seçici). */
    initialGradient: com.karapp.drawguidance.data.model.GradientSpec? = null,
    onGradientChange: ((com.karapp.drawguidance.data.model.GradientSpec?) -> Unit)? = null,
) {
    val colors = NeuTheme.colors
    var colorHex by remember { mutableStateOf(initialColorHex.uppercase()) }
    var gradient by remember { mutableStateOf(initialGradient) }
    var gradientTab by remember { mutableStateOf(initialGradient != null) }
    NeuSheet(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag("color_pick_sheet")
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.pick_color_title), style = MaterialTheme.typography.headlineSmall)
            if (onGradientChange != null) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.karapp.drawguidance.ui.components.NeuChip(stringResource(R.string.fill_tab_color), !gradientTab, onClick = { gradientTab = false }, modifier = Modifier.testTag("fill_tab_color"))
                    com.karapp.drawguidance.ui.components.NeuChip(stringResource(R.string.fill_tab_gradient), gradientTab, onClick = { gradientTab = true }, modifier = Modifier.testTag("fill_tab_gradient"))
                }
            }
            if (gradientTab && onGradientChange != null) {
                com.karapp.drawguidance.ui.components.GradientPicker(gradient, onChange = { gradient = it; onGradientChange(it) })
            } else {
                HsvColorPicker(colorHex = colorHex, onColorChange = {
                    colorHex = it.uppercase()
                    onColorChange(colorHex)
                })
            }
        }
    }
}

/** Hızlı renkler: bölge ve parça boyamada tek dokunuşla. */
internal val QuickColors = listOf(
    "#1E1E1E", "#FFFFFF", "#E53935", "#FB8C00", "#FDD835", "#43A047", "#00897B", "#1E88E5", "#3949AB", "#8E24AA", "#D81B60", "#6D4C41",
)

/**
 * Bir elemanın bir parçasına/bölgesine renk verme penceresi: [parts] verilmişse hangi kısmın boyanacağı
 * (ör. dolgu/kenar/yazı) seçilir; hızlı renkler, renk seçici ve "varsayılana dön". Renk anında uygulanır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartColorSheet(
    title: String,
    colorHex: String,
    onColorChange: (String) -> Unit,
    onDismiss: () -> Unit,
    parts: List<Pair<String, String>> = emptyList(),
    selectedPart: String? = null,
    onPartChange: (String) -> Unit = {},
    onReset: (() -> Unit)? = null,
) {
    val colors = NeuTheme.colors
    NeuSheet(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag("part_color_sheet")
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (parts.isNotEmpty()) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    parts.forEach { (key, label) ->
                        com.karapp.drawguidance.ui.components.NeuChip(
                            text = label, selected = key == selectedPart, onClick = { onPartChange(key) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            androidx.compose.runtime.key(selectedPart) {
                var current by remember { mutableStateOf(colorHex.uppercase()) }
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    QuickColors.forEach { hex ->
                        val selected = hex.equals(current, ignoreCase = true)
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .size(24.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(com.karapp.drawguidance.ui.common.hexToColor(hex))
                                .border(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) colors.accent else colors.contentMuted.copy(alpha = 0.4f),
                                    androidx.compose.foundation.shape.CircleShape,
                                )
                                .clickable { current = hex; onColorChange(hex) }
                                .semantics { contentDescription = com.karapp.drawguidance.i18n.I18n.trf("Renk {0}", hex) },
                        )
                    }
                }
                HsvColorPicker(colorHex = current, onColorChange = {
                    current = it.uppercase()
                    onColorChange(current)
                })
            }
            if (onReset != null) {
                com.karapp.drawguidance.ui.components.NeuButton(
                    stringResource(R.string.color_reset_default), { onReset(); onDismiss() }, Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * v17 Alan boya aracının renk satırı: hızlı renkler, seçili kalem rengi, özel renk (renk seçici) ve "Temizle"
 * (dokunulan alanın rengini kaldırır). [colorHex] null: kalem rengi, "": temizle.
 */
@Composable
fun RegionFillControls(
    colorHex: String?,
    inkHex: String,
    onColor: (String?) -> Unit,
    onCustom: () -> Unit,
) {
    val colors = NeuTheme.colors
    val current = colorHex ?: inkHex
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).testTag("region_fill_controls"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        com.karapp.drawguidance.ui.components.NeuChip(
            stringResource(R.string.region_fill_clear), colorHex == "", { onColor("") },
            Modifier.testTag("region_fill_clear"), icon = androidx.compose.material.icons.Icons.Filled.FormatColorReset,
        )
        (listOf(inkHex.uppercase()) + QuickColors.filterNot { it.equals(inkHex, ignoreCase = true) }).forEach { hex ->
            val selected = colorHex != "" && hex.equals(current, ignoreCase = true)
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(30.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(com.karapp.drawguidance.ui.common.hexToColor(hex))
                    .border(
                        if (selected) 3.dp else 1.dp,
                        if (selected) colors.accent else colors.contentMuted.copy(alpha = 0.4f),
                        androidx.compose.foundation.shape.CircleShape,
                    )
                    .clickable { onColor(hex) }
                    .semantics { contentDescription = com.karapp.drawguidance.i18n.I18n.trf("Renk {0}", hex) },
            )
        }
        com.karapp.drawguidance.ui.components.NeuIconButton(
            androidx.compose.material.icons.Icons.Filled.Palette, stringResource(R.string.region_fill_custom), onCustom, size = 38.dp,
            tint = colors.accent,
        )
    }
}

/** v33 çocuklar için 12 canlı renk (adlarıyla: erişilebilirlik ve araç yardımı). */
val KidsColors: List<Pair<String, String>> = listOf(
    "#E53935" to "Kırmızı", "#FB8C00" to "Turuncu", "#FDD835" to "Sarı", "#7CB342" to "Açık yeşil",
    "#2E7D32" to "Yeşil", "#00ACC1" to "Turkuaz", "#1E88E5" to "Mavi", "#5E35B1" to "Mor",
    "#EC407A" to "Pembe", "#8D6E63" to "Kahverengi", "#212121" to "Siyah", "#FFFFFF" to "Beyaz",
)

/**
 * v33 çocuklar için büyük renk paleti: iki sıra halinde 12 büyük renk dairesi (seçili olan kalın çerçeveli). Fırça
 * rengi, şekil boyama ve arka plan boyamada aynı palet kullanılır.
 */
@Composable
fun KidsPalette(selectedHex: String?, onColor: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth().testTag("kids_palette"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KidsColors.chunked(6).forEach { row ->
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (hex, name) ->
                    val selected = hex.equals(selectedHex, ignoreCase = true)
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(44.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(com.karapp.drawguidance.ui.common.hexToColor(hex))
                            .border(
                                if (selected) 4.dp else 1.dp,
                                if (selected) colors.accent else colors.contentMuted.copy(alpha = 0.45f),
                                androidx.compose.foundation.shape.CircleShape,
                            )
                            .clickable { onColor(hex) }
                            .semantics { contentDescription = name; if (selected) this.selected = true },
                    )
                }
            }
        }
    }
}

/** v33 çocuklar için arka plan boyama: büyük paletten dokunulan renk kağıda hemen uygulanır. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun KidsBackgroundSheet(currentHex: String, onColor: (String) -> Unit, onDismiss: () -> Unit, onTransform: (() -> Unit)? = null) {
    val colors = NeuTheme.colors
    var chosen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(currentHex) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        contentColor = colors.content,
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp).testTag("kids_background_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            androidx.compose.material3.Text(stringResource(R.string.simple_paint_background), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            KidsPalette(chosen, { chosen = it; onColor(it) })
            // v40 kağıtta resim varsa taşıma/boyutlandırma
            if (onTransform != null) com.karapp.drawguidance.ui.components.NeuButton(
                stringResource(R.string.bg_transform), onTransform, modifier = Modifier.testTag("bg_transform_open"),
                icon = androidx.compose.material.icons.Icons.Filled.OpenWith,
            )
        }
    }
}
