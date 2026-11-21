package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSheet
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel.SaveTab
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v42 birleşik Kaydet penceresi (başlıktaki tek Kaydet düğmesi): üstte biçim sekmeleri — resim (PNG/JPEG/WebP ya da
 * CMYK JPEG/TIFF), PDF (RGB ya da baskıya hazır CMYK) ve çizgi romanda CBZ — altında seçilen biçimin ayarları.
 * Çizgi roman ve not alma varsayılan olarak CMYK PDF'tir; öteki birleşimler seçilebilir. ⋮ menüsündeki ayrı PDF ve
 * CBZ kaydetme kaldırıldı.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedSaveSheet(viewModel: CanvasEditViewModel, canvas: CanvasUi) {
    val tab by viewModel.saveTab.collectAsState()
    val current = tab ?: return
    val tabs = viewModel.saveTabs()
    val image by viewModel.saveOptions.collectAsState()
    val pdf by viewModel.pdfOptions.collectAsState()
    val cbz by viewModel.v40.cbzOptions.collectAsState()
    val close = viewModel::closeUnifiedSave
    val colors = NeuTheme.colors
    val header: @Composable () -> Unit = {
        Column(Modifier.testTag("unified_save_header"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.v42_save_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.v42_save_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            if (tabs.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEach { t ->
                    NeuChip(
                        stringResource(
                            when (t) {
                                SaveTab.IMAGE -> R.string.v42_save_tab_image
                                SaveTab.PDF -> R.string.v42_save_tab_pdf
                                SaveTab.CBZ -> R.string.v42_save_tab_cbz
                                SaveTab.SVG -> R.string.v56_save_tab_svg
                                SaveTab.PSD -> R.string.v56_save_tab_psd
                                SaveTab.DXF -> R.string.v56_save_tab_dxf
                            },
                        ),
                        t == current, { viewModel.selectSaveTab(t) },
                        icon = when (t) {
                            SaveTab.IMAGE -> Icons.Filled.Image
                            SaveTab.PDF -> Icons.Filled.PictureAsPdf
                            SaveTab.CBZ -> Icons.Filled.Archive
                            SaveTab.SVG, SaveTab.DXF -> Icons.Filled.Polyline
                            SaveTab.PSD -> Icons.Filled.Layers
                        },
                        modifier = Modifier.testTag("save_tab_${t.name.lowercase()}"),
                    )
                }
            }
        }
    }
    NeuSheet(close) {
        when (current) {
            SaveTab.IMAGE -> image?.let { options ->
                com.karapp.drawguidance.ui.components.ImageSaveOptionsDialog(
                    initial = options,
                    pageCount = canvas.pageCount,
                    hasGuides = !canvas.guide.isEmpty,
                    locationLabel = viewModel.imageSaveLocation,
                    outputSize = viewModel::imageOutputSize,
                    onSave = { o -> viewModel.saveAsImage(o); close() },
                    onDismiss = close,
                    preview = viewModel::cmykPreview,
                    documentLocationLabel = viewModel.pdfLocation,
                    inline = true,
                    header = header,
                )
            }
            SaveTab.PDF -> pdf?.let { options ->
                com.karapp.drawguidance.ui.components.PdfOptionsSheet(
                    initial = options,
                    canvasWidth = canvas.width,
                    canvasHeight = canvas.height,
                    pageCount = canvas.pageCount,
                    currentPage = canvas.currentPage,
                    hasGuides = !canvas.guide.isEmpty,
                    locationLabel = viewModel.pdfLocation,
                    onSave = { o -> viewModel.exportPdf(o); close() },
                    onDismiss = close,
                    inline = true,
                    header = header,
                )
            }
            SaveTab.CBZ -> cbz?.let { options ->
                CbzOptionsSheet(
                    options, canvas.pageCount, canvas.currentPage, viewModel.v40.documentLocation,
                    onSave = { o -> viewModel.v40.exportCbz(o); close() },
                    onDismiss = close,
                    inline = true,
                    header = header,
                )
            }
            SaveTab.SVG, SaveTab.PSD, SaveTab.DXF -> V56DocSavePanel(viewModel, current, header, close)
        }
    }
}

/** v56 aşama 9 SVG / PSD / DXF ayarları: dosya adı, biçimin açıklaması ve Kaydet. */
@Composable
private fun V56DocSavePanel(viewModel: CanvasEditViewModel, tab: SaveTab, header: @Composable () -> Unit, close: () -> Unit) {
    val format = when (tab) {
        SaveTab.SVG -> com.karapp.drawguidance.data.export.DocFormat.SVG
        SaveTab.PSD -> com.karapp.drawguidance.data.export.DocFormat.PSD
        else -> com.karapp.drawguidance.data.export.DocFormat.DXF
    }
    var name by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(viewModel.v40.defaultDocName()) }
    Column(Modifier.testTag("v56_doc_save"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        header()
        Text(
            stringResource(when (format) {
                com.karapp.drawguidance.data.export.DocFormat.SVG -> R.string.v56_save_svg_hint
                com.karapp.drawguidance.data.export.DocFormat.PSD -> R.string.v56_save_psd_hint
                com.karapp.drawguidance.data.export.DocFormat.DXF -> R.string.v56_save_dxf_hint
            }),
            style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted,
        )
        com.karapp.drawguidance.ui.components.NeuTextField(name, { name = it }, stringResource(R.string.v56_save_file_name), Modifier.testTag("v56_doc_name"))
        Text(stringResource(R.string.v56_save_location, viewModel.v40.documentLocation), style = MaterialTheme.typography.labelSmall, color = NeuTheme.colors.contentMuted)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.karapp.drawguidance.ui.components.NeuButton(stringResource(R.string.cancel_button), close)
            com.karapp.drawguidance.ui.components.NeuButton(
                stringResource(R.string.v56_save_as, format.extension.uppercase()),
                { viewModel.v40.exportDoc(format, name); close() }, Modifier.testTag("v56_doc_save_button"), primary = true,
            )
        }
    }
}

/** v56 aşama 9 resim kaydetme penceresinin başlığı: başlık + SVG / PSD / DXF'e geçiş (özellik kapalıysa null). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun v56ImageSaveHeader(viewModel: CanvasEditViewModel): (@Composable () -> Unit)? {
    val docs = viewModel.saveTabs().filter { it == SaveTab.SVG || it == SaveTab.PSD || it == SaveTab.DXF }
    if (docs.isEmpty()) return null
    return {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.save_options_title), style = MaterialTheme.typography.headlineSmall)
            FlowRow(Modifier.testTag("v56_doc_formats"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.v56_save_other_formats), style = MaterialTheme.typography.labelMedium, color = NeuTheme.colors.contentMuted)
                docs.forEach { t ->
                    NeuChip(
                        stringResource(when (t) { SaveTab.SVG -> R.string.v56_save_tab_svg; SaveTab.PSD -> R.string.v56_save_tab_psd; else -> R.string.v56_save_tab_dxf }),
                        false, { viewModel.openDocTab(t) }, modifier = Modifier.testTag("save_doc_${t.name.lowercase()}"),
                    )
                }
            }
        }
    }
}
