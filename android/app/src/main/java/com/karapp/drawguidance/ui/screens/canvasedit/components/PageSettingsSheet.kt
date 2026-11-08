package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.PageAlign
import com.karapp.drawguidance.data.model.PageNumberFormat
import com.karapp.drawguidance.data.model.PageSettings
import com.karapp.drawguidance.data.model.PageTextSize
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSheet
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v40 sayfa ayarları penceresi (not alma ve çizgi roman): sayfa numarası (açık/kapalı, üstte/altta, sol/orta/sağ,
 * "3" ya da "3 / 12"), üst bilgi ve alt bilgi (yazı, hiza, boy). Değişiklikler tuvalde hemen görünür ve kaydedilir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageSettingsSheet(
    settings: PageSettings,
    pageCount: Int,
    onChange: (PageSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NeuTheme.colors
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().testTag("page_settings_sheet").verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.v40_page_settings), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.v40_page_settings_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.v40_page_numbers), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                NeuSwitch(settings.pageNumbers, { onChange(settings.copy(pageNumbers = it)) }, Modifier.testTag("page_numbers_switch"))
            }
            if (settings.pageNumbers) {
                Label(stringResource(R.string.v40_number_position))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuChip(stringResource(R.string.v40_bottom), !settings.numberAtTop, { onChange(settings.copy(numberAtTop = false)) })
                    NeuChip(stringResource(R.string.v40_top), settings.numberAtTop, { onChange(settings.copy(numberAtTop = true)) })
                }
                AlignRow(settings.numberAlign, "number") { onChange(settings.copy(numberAlign = it)) }
                Label(stringResource(R.string.v40_number_format))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val n = pageCount.coerceAtLeast(1)
                    NeuChip("1", settings.numberFormat == PageNumberFormat.NUMBER, { onChange(settings.copy(numberFormat = PageNumberFormat.NUMBER)) }, modifier = Modifier.testTag("number_format_n"))
                    NeuChip("1 / $n", settings.numberFormat == PageNumberFormat.NUMBER_OF_TOTAL, { onChange(settings.copy(numberFormat = PageNumberFormat.NUMBER_OF_TOTAL)) }, modifier = Modifier.testTag("number_format_total"))
                }
            }

            TextBlock(
                title = stringResource(R.string.v40_header), hint = stringResource(R.string.v40_header_hint), tag = "header",
                text = settings.header, align = settings.headerAlign, size = settings.headerSize,
                onText = { onChange(settings.copy(header = it)) }, onAlign = { onChange(settings.copy(headerAlign = it)) },
                onSize = { onChange(settings.copy(headerSize = it)) },
            )
            TextBlock(
                title = stringResource(R.string.v40_footer), hint = stringResource(R.string.v40_footer_hint), tag = "footer",
                text = settings.footer, align = settings.footerAlign, size = settings.footerSize,
                onText = { onChange(settings.copy(footer = it)) }, onAlign = { onChange(settings.copy(footerAlign = it)) },
                onSize = { onChange(settings.copy(footerSize = it)) },
            )
            NeuButton(stringResource(R.string.v40_done), onDismiss, Modifier.fillMaxWidth().testTag("page_settings_done"), primary = true, icon = Icons.Filled.Check)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextBlock(
    title: String, hint: String, tag: String,
    text: String, align: PageAlign, size: PageTextSize,
    onText: (String) -> Unit, onAlign: (PageAlign) -> Unit, onSize: (PageTextSize) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
    NeuTextField(
        value = text, onValueChange = { onText(it.take(120)) }, label = title, placeholder = hint,
        modifier = Modifier.fillMaxWidth(), fieldModifier = Modifier.testTag("${tag}_text"),
    )
    if (text.isNotBlank()) {
        AlignRow(align, tag, onAlign)
        Label(stringResource(R.string.v40_text_size))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PageTextSize.entries.forEach { s ->
                val label = stringResource(
                    when (s) { PageTextSize.SMALL -> R.string.v40_size_small; PageTextSize.MEDIUM -> R.string.v40_size_medium; PageTextSize.LARGE -> R.string.v40_size_large },
                )
                NeuChip(label, size == s, { onSize(s) }, modifier = Modifier.testTag("${tag}_size_${s.name}"))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlignRow(align: PageAlign, tag: String, onAlign: (PageAlign) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageAlign.entries.forEach { a ->
            val label = stringResource(
                when (a) { PageAlign.LEFT -> R.string.v40_align_left; PageAlign.CENTER -> R.string.v40_align_center; PageAlign.RIGHT -> R.string.v40_align_right },
            )
            NeuChip(label, align == a, { onAlign(a) }, modifier = Modifier.testTag("${tag}_align_${a.name}"))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = NeuTheme.colors.contentMuted)
}
