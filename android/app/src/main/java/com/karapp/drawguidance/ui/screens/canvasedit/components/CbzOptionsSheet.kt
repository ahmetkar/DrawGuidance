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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.images.CbzOptions
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.PdfPages
import com.karapp.drawguidance.data.settings.ImageFormat
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSheet
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.components.SaveLocationSection
import com.karapp.drawguidance.ui.components.scaleLabel
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme

/** v40 "CBZ olarak kaydet" penceresi (çizgi roman): dosya adı, sayfalar, sayfa resmi biçimi, çözünürlük ve yer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CbzOptionsSheet(
    initial: CbzOptions,
    pageCount: Int,
    currentPage: Int,
    locationLabel: String,
    onSave: (CbzOptions) -> Unit,
    onDismiss: () -> Unit,
    /** v42 birleşik Kaydet penceresinin içinde (kendi alt penceresi yok; başlık yerine [header]). */
    inline: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    val colors = NeuTheme.colors
    val count = pageCount.coerceAtLeast(1)
    var fileName by remember { mutableStateOf(initial.fileName) }
    var pages by remember { mutableStateOf(if (count > 1) initial.pages else PdfPages.ALL) }
    var fromText by remember { mutableStateOf("1") }
    var toText by remember { mutableStateOf(count.toString()) }
    var format by remember { mutableStateOf(initial.format) }
    var scale by remember { mutableStateOf(initial.scale) }
    var target by remember { mutableStateOf(initial.target) }
    val from = fromText.toIntOrNull()
    val to = toText.toIntOrNull()
    val rangeValid = pages != PdfPages.RANGE || (from != null && to != null && from in 1..count && to in from..count)
    val options = initial.copy(
        fileName = fileName.trim().ifEmpty { initial.fileName }, pages = pages, fromPage = from ?: 1, toPage = to ?: count,
        format = format, scale = scale, target = target,
    )
    com.karapp.drawguidance.ui.components.NeuSheetOrInline(inline, onDismiss) {
        Column(
            Modifier.fillMaxWidth().testTag("cbz_options_sheet").verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (header != null) header() else Text(stringResource(R.string.v40_cbz_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.v40_cbz_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            NeuTextField(
                value = fileName, onValueChange = { fileName = it.take(80) }, label = stringResource(R.string.save_file_name),
                supportingText = "${ImageExporter.safeName(options.fileName)}.cbz", modifier = Modifier.fillMaxWidth(),
            )
            if (count > 1) {
                Text(stringResource(R.string.pdf_pages), style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuChip(stringResource(R.string.pdf_pages_all, count), pages == PdfPages.ALL, { pages = PdfPages.ALL })
                    NeuChip(stringResource(R.string.pdf_pages_current, currentPage + 1), pages == PdfPages.CURRENT, { pages = PdfPages.CURRENT })
                    NeuChip(stringResource(R.string.pdf_pages_range), pages == PdfPages.RANGE, { pages = PdfPages.RANGE })
                }
                if (pages == PdfPages.RANGE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        NeuTextField(
                            value = fromText, onValueChange = { fromText = it.filter(Char::isDigit).take(4) }, label = stringResource(R.string.pdf_range_from),
                            isError = !rangeValid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                        )
                        NeuTextField(
                            value = toText, onValueChange = { toText = it.filter(Char::isDigit).take(4) }, label = stringResource(R.string.pdf_range_to),
                            isError = !rangeValid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Text(stringResource(R.string.v40_cbz_format), style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageFormat.entries.forEach { f -> NeuChip(f.label, format == f, { format = f }) }
            }
            Text(stringResource(R.string.save_resolution), style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 1f, 2f).forEach { s -> NeuChip(scaleLabel(s), scale == s, { scale = s }) }
            }
            SaveLocationSection(
                defaultLabel = locationLabel, target = target, onTarget = { target = it },
                saveAsName = "${ImageExporter.safeName(options.fileName)}.cbz", saveAsMime = "application/vnd.comicbook+zip",
                onSaveAs = { if (rangeValid) onSave(options.copy(target = it)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                NeuButton(stringResource(R.string.cancel_button), onDismiss, Modifier.weight(1f))
                NeuButton(
                    stringResource(R.string.v40_cbz_save), { if (rangeValid) onSave(options) },
                    Modifier.weight(1f).testTag("cbz_save"), primary = true, enabled = rangeValid, icon = Icons.Filled.SaveAlt,
                )
            }
        }
    }
}
