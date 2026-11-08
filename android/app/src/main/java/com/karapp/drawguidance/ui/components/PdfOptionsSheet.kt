package com.karapp.drawguidance.ui.components

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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.PdfLayout
import com.karapp.drawguidance.data.images.PdfMargin
import com.karapp.drawguidance.data.images.PdfOptions
import com.karapp.drawguidance.data.images.PdfOrientation
import com.karapp.drawguidance.data.images.PdfPages
import com.karapp.drawguidance.data.images.PdfPaper
import com.karapp.drawguidance.data.images.PdfQuality
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v19 "PDF olarak kaydet" penceresi (alttan açılan): dosya adı; sayfalar (tümü, bu sayfa, aralık); kağıt boyutu
 * (tuval oranı, A4, A5, Letter), yön ve kenar boşluğu; kalite (keskin vektör ya da resim) ve resim çözünürlüğü;
 * arka plan rengi (kapalıyken beyaz kağıt), kılavuz çizgileri, sayfa numarası; özet ve kaydedileceği yer.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PdfOptionsSheet(
    initial: PdfOptions,
    canvasWidth: Int,
    canvasHeight: Int,
    pageCount: Int,
    currentPage: Int,
    hasGuides: Boolean,
    locationLabel: String,
    onSave: (PdfOptions) -> Unit,
    onDismiss: () -> Unit,
    /** v42 birleşik Kaydet penceresinin içinde (kendi alt penceresi yok; başlık yerine [header]). */
    inline: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    val colors = NeuTheme.colors
    val count = pageCount.coerceAtLeast(1)
    var fileName by remember { mutableStateOf(initial.fileName) }
    var pages by remember { mutableStateOf(if (count > 1) initial.pages else PdfPages.ALL) }
    var fromText by remember { mutableStateOf(initial.fromPage.coerceIn(1, count).toString()) }
    var toText by remember { mutableStateOf(initial.toPage.coerceIn(1, count).toString()) }
    var paper by remember { mutableStateOf(initial.paper) }
    var orientation by remember { mutableStateOf(initial.orientation) }
    var margin by remember { mutableStateOf(initial.margin) }
    var quality by remember { mutableStateOf(initial.quality) }
    var imageScale by remember { mutableStateOf(initial.imageScale) }
    var background by remember { mutableStateOf(initial.background) }
    var guides by remember { mutableStateOf(initial.includeGuides && hasGuides) }
    var pageNumbers by remember { mutableStateOf(initial.pageNumbers) }
    // v40 baskıya hazır (CMYK) PDF ve kaydetme yeri
    var cmyk by remember { mutableStateOf(initial.cmyk) }
    var target by remember { mutableStateOf(initial.target) }

    val from = fromText.toIntOrNull()
    val to = toText.toIntOrNull()
    val rangeValid = pages != PdfPages.RANGE || (from != null && to != null && from in 1..count && to in from..count)
    val options = initial.copy(
        fileName = fileName.trim().ifEmpty { initial.fileName },
        pages = pages,
        fromPage = from ?: 1,
        toPage = to ?: count,
        paper = paper,
        orientation = orientation,
        margin = margin,
        quality = quality,
        imageScale = imageScale,
        background = background,
        includeGuides = guides && hasGuides,
        pageNumbers = pageNumbers,
        cmyk = cmyk,
        target = target,
    )
    val layout = PdfLayout.of(canvasWidth, canvasHeight, options)
    val selectedPages = if (rangeValid) options.pageIndices(count, currentPage).size else 0
    val paperLabel = when (paper) {
        PdfPaper.CANVAS -> stringResource(R.string.pdf_paper_canvas)
        else -> paper.label + " " + stringResource(
            if (layout.pageWidth > layout.pageHeight) R.string.pdf_orientation_landscape else R.string.pdf_orientation_portrait,
        ).lowercase()
    }

    com.karapp.drawguidance.ui.components.NeuSheetOrInline(inline, onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag("pdf_options_sheet")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (header != null) header() else Text(stringResource(R.string.pdf_options_title), style = MaterialTheme.typography.headlineSmall)
            NeuTextField(
                value = fileName,
                onValueChange = { fileName = it.take(80) },
                label = stringResource(R.string.save_file_name),
                supportingText = "${ImageExporter.safeName(fileName.ifBlank { initial.fileName })}.pdf",
                modifier = Modifier.fillMaxWidth(),
                fieldModifier = Modifier.testTag("pdf_file_name"),
            )

            if (count > 1) {
                Label(stringResource(R.string.pdf_pages))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuChip(stringResource(R.string.pdf_pages_all, count), pages == PdfPages.ALL, { pages = PdfPages.ALL })
                    NeuChip(stringResource(R.string.pdf_pages_current, currentPage + 1), pages == PdfPages.CURRENT, { pages = PdfPages.CURRENT })
                    NeuChip(stringResource(R.string.pdf_pages_range), pages == PdfPages.RANGE, { pages = PdfPages.RANGE })
                }
                if (pages == PdfPages.RANGE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        NeuTextField(
                            value = fromText,
                            onValueChange = { fromText = it.filter(Char::isDigit).take(4) },
                            label = stringResource(R.string.pdf_range_from),
                            supportingText = stringResource(R.string.pdf_range_hint, count),
                            isError = !rangeValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            fieldModifier = Modifier.testTag("pdf_range_from"),
                        )
                        NeuTextField(
                            value = toText,
                            onValueChange = { toText = it.filter(Char::isDigit).take(4) },
                            label = stringResource(R.string.pdf_range_to),
                            isError = !rangeValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            fieldModifier = Modifier.testTag("pdf_range_to"),
                        )
                    }
                }
            }

            Label(stringResource(R.string.pdf_paper))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfPaper.entries.forEach { p ->
                    NeuChip(if (p == PdfPaper.CANVAS) stringResource(R.string.pdf_paper_canvas) else p.label, paper == p, { paper = p })
                }
            }
            // Yön yalnız sabit boyutlu kağıtta (tuval oranında kağıt tuvalin yönündedir)
            if (paper != PdfPaper.CANVAS) {
                Label(stringResource(R.string.pdf_orientation))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuChip(stringResource(R.string.pdf_orientation_auto), orientation == PdfOrientation.AUTO, { orientation = PdfOrientation.AUTO })
                    NeuChip(stringResource(R.string.pdf_orientation_portrait), orientation == PdfOrientation.PORTRAIT, { orientation = PdfOrientation.PORTRAIT })
                    NeuChip(stringResource(R.string.pdf_orientation_landscape), orientation == PdfOrientation.LANDSCAPE, { orientation = PdfOrientation.LANDSCAPE })
                }
            }
            Label(stringResource(R.string.pdf_margin))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuChip(stringResource(R.string.pdf_margin_none), margin == PdfMargin.NONE, { margin = PdfMargin.NONE })
                NeuChip(stringResource(R.string.pdf_margin_narrow), margin == PdfMargin.NARROW, { margin = PdfMargin.NARROW })
                NeuChip(stringResource(R.string.pdf_margin_normal), margin == PdfMargin.NORMAL, { margin = PdfMargin.NORMAL })
            }

            Label(stringResource(R.string.pdf_quality))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuChip(stringResource(R.string.pdf_quality_sharp), quality == PdfQuality.SHARP, { quality = PdfQuality.SHARP })
                NeuChip(stringResource(R.string.pdf_quality_image), quality == PdfQuality.IMAGE, { quality = PdfQuality.IMAGE })
            }
            Text(
                stringResource(if (quality == PdfQuality.SHARP) R.string.pdf_quality_sharp_hint else R.string.pdf_quality_image_hint),
                style = MaterialTheme.typography.bodySmall, color = colors.contentMuted,
            )
            Label(stringResource(R.string.pdf_resolution))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfOptions.ImageScales.forEachIndexed { i, s ->
                    val label = stringResource(
                        when (i) { 0 -> R.string.pdf_resolution_standard; 1 -> R.string.pdf_resolution_high; else -> R.string.pdf_resolution_max },
                    )
                    NeuChip("$label · ${scaleLabel(s)}", imageScale == s, { imageScale = s })
                }
            }

            SwitchLine(stringResource(R.string.pdf_background), stringResource(R.string.pdf_background_hint), background, "pdf_background") { background = it }
            if (hasGuides) SwitchLine(stringResource(R.string.pdf_guides), null, guides, "pdf_guides") { guides = it }
            SwitchLine(stringResource(R.string.pdf_page_numbers), stringResource(R.string.pdf_page_numbers_hint), pageNumbers, "pdf_page_numbers") { pageNumbers = it }
            // v40 renk: RGB ya da baskıya hazır CMYK (sayfalar DeviceCMYK resim)
            CmykSection(cmyk, com.karapp.drawguidance.data.images.CmykFormat.PDF, { c, _ -> cmyk = c }, showFormat = false)
            if (cmyk != null) Text(stringResource(R.string.v40_pdf_cmyk_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)

            Text(
                stringResource(R.string.pdf_summary, selectedPages, paperLabel, layout.pageWidth, layout.pageHeight),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.accent,
                modifier = Modifier.testTag("pdf_summary"),
            )
            // v40 "Konum seç": klasör ya da farklı kaydet
            SaveLocationSection(
                defaultLabel = locationLabel,
                target = target,
                onTarget = { target = it },
                saveAsName = "${ImageExporter.safeName(options.fileName)}.pdf",
                saveAsMime = "application/pdf",
                onSaveAs = { if (rangeValid) onSave(options.copy(target = it)) },
                labelTag = "pdf_location",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                NeuButton(stringResource(R.string.cancel_button), onDismiss, Modifier.weight(1f))
                NeuButton(
                    stringResource(R.string.pdf_save),
                    onClick = { if (rangeValid) onSave(options) },
                    modifier = Modifier.weight(1f).testTag("pdf_save"),
                    primary = true,
                    enabled = rangeValid,
                    icon = Icons.Filled.PictureAsPdf,
                )
            }
        }
    }
}

/** Kağıt adı: A4, A5, Letter. */
private val PdfPaper.label: String
    get() = when (this) {
        PdfPaper.CANVAS -> ""
        PdfPaper.A4 -> "A4"
        PdfPaper.A5 -> "A5"
        PdfPaper.LETTER -> "Letter"
    }

@Composable
private fun SwitchLine(title: String, hint: String?, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val colors = NeuTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.content)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        }
        NeuSwitch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = NeuTheme.colors.contentMuted)
}
