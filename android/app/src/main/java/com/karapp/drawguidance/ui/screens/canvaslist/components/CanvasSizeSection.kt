package com.karapp.drawguidance.ui.screens.canvaslist.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSectionTitle
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v40 yeni tuval formunun boyut bölümü: türe uygun hazır boyutlar ve "Özel". Özelde genişlik / yükseklik px, in ya
 * da cm olarak yazılır; inç ve santimetrede DPI alanı görünür, sonuç piksel olarak altta yazar. Boyut sınırları ve
 * bellek ([CanvasSizing.validate]) aşılırsa uyarı çıkar ve [onSize] sorunu bildirir (form Oluştur'u kapatır).
 */
@Composable
fun CanvasSizeSection(
    kind: CanvasKind,
    initialWidth: Int,
    initialHeight: Int,
    onSize: (width: Int, height: Int, issue: SizeIssue?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    val presets = CanvasSizing.presets(kind)
    var key by rememberSaveable { mutableStateOf(CanvasSizing.match(kind, initialWidth, initialHeight)) }
    var unit by rememberSaveable { mutableStateOf(SizeUnit.PX) }
    var dpiText by rememberSaveable { mutableStateOf(CanvasSizing.DEFAULT_DPI.toString()) }
    var wText by rememberSaveable { mutableStateOf(initialWidth.toString()) }
    var hText by rememberSaveable { mutableStateOf(initialHeight.toString()) }
    // Tür değişince (özel boyut seçili değilse) türün öntanımlı boyutuna geçilir
    var lastKind by rememberSaveable { mutableStateOf(kind) }
    if (lastKind != kind) {
        lastKind = kind
        if (key != CanvasSizing.CUSTOM) key = presets.first().key
    }
    val dpi = dpiText.toIntOrNull()?.coerceIn(CanvasSizing.MIN_DPI, CanvasSizing.MAX_DPI) ?: CanvasSizing.DEFAULT_DPI
    val preset = presets.firstOrNull { it.key == key }
    val width = preset?.width ?: CanvasSizing.parse(wText)?.let { CanvasSizing.toPx(it, unit, dpi) } ?: 0
    val height = preset?.height ?: CanvasSizing.parse(hText)?.let { CanvasSizing.toPx(it, unit, dpi) } ?: 0
    val maxPixels = remember { CanvasSizing.maxPixels() }
    val issue = CanvasSizing.validate(width, height, maxPixels)
    LaunchedEffect(width, height, issue) { onSize(width, height, issue) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NeuSectionTitle(stringResource(R.string.canvas_size_label))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp).testTag("size_presets"),
        ) {
            presets.forEach { p ->
                NeuChip(
                    text = stringResource(p.label), selected = key == p.key,
                    onClick = { key = p.key }, modifier = Modifier.testTag("size_preset_${p.key}"),
                )
            }
            NeuChip(
                text = stringResource(R.string.v40_size_custom), selected = key == CanvasSizing.CUSTOM,
                onClick = {
                    if (key != CanvasSizing.CUSTOM) {
                        // Özel boyut seçili boyuttan başlar (seçili birimde)
                        wText = CanvasSizing.format(CanvasSizing.fromPx(width.coerceAtLeast(1), unit, dpi), unit)
                        hText = CanvasSizing.format(CanvasSizing.fromPx(height.coerceAtLeast(1), unit, dpi), unit)
                    }
                    key = CanvasSizing.CUSTOM
                },
                modifier = Modifier.testTag("size_preset_custom"),
            )
        }
        if (preset != null) {
            Text(
                stringResource(R.string.v40_size_result, preset.width, preset.height),
                style = MaterialTheme.typography.labelMedium, color = colors.contentMuted, modifier = Modifier.testTag("size_result"),
            )
        } else {
            // Birim: değiştirilince yazılı değerler yeni birime çevrilir
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("size_units")) {
                SizeUnit.entries.forEach { u ->
                    NeuChip(
                        text = u.symbol, selected = unit == u,
                        onClick = {
                            if (u != unit) {
                                if (width > 0) wText = CanvasSizing.format(CanvasSizing.fromPx(width, u, dpi), u)
                                if (height > 0) hText = CanvasSizing.format(CanvasSizing.fromPx(height, u, dpi), u)
                                unit = u
                            }
                        },
                        modifier = Modifier.testTag("size_unit_${u.name.lowercase()}"),
                    )
                }
            }
            val numberKeyboard = KeyboardOptions(keyboardType = if (unit == SizeUnit.PX) KeyboardType.Number else KeyboardType.Decimal)
            val filter: (String) -> String = { s ->
                if (unit == SizeUnit.PX) s.filter(Char::isDigit).take(5) else s.filter { it.isDigit() || it == '.' || it == ',' }.take(7)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuTextField(
                    value = wText, onValueChange = { wText = filter(it) },
                    label = stringResource(R.string.v40_size_width, unit.symbol), keyboardOptions = numberKeyboard,
                    modifier = Modifier.weight(1f), fieldModifier = Modifier.testTag("size_width"),
                )
                NeuTextField(
                    value = hText, onValueChange = { hText = filter(it) },
                    label = stringResource(R.string.v40_size_height, unit.symbol), keyboardOptions = numberKeyboard,
                    modifier = Modifier.weight(1f), fieldModifier = Modifier.testTag("size_height"),
                )
                if (unit != SizeUnit.PX) {
                    NeuTextField(
                        value = dpiText, onValueChange = { dpiText = it.filter(Char::isDigit).take(4) },
                        label = stringResource(R.string.v40_size_dpi), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.8f), fieldModifier = Modifier.testTag("size_dpi"),
                    )
                }
            }
            val message = when (issue) {
                null -> stringResource(R.string.v40_size_result, width, height)
                SizeIssue.TOO_SMALL -> stringResource(R.string.v40_size_error_min, CanvasSizing.MIN_SIDE)
                SizeIssue.TOO_LARGE -> stringResource(R.string.v40_size_error_max, CanvasSizing.MAX_SIDE)
                SizeIssue.MEMORY -> stringResource(R.string.v40_size_error_memory)
            }
            Text(
                message, style = MaterialTheme.typography.labelMedium,
                color = if (issue != null) colors.danger else colors.contentMuted,
                modifier = Modifier.testTag(if (issue != null) "size_error" else "size_result"),
            )
        }
    }
}
