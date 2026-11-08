package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.drawing.DiagramThemes
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.state.PdfExportState
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** v40 ⋮ menüsünün ek seçenekleri: sayfa ayarları (sayfalı tuvaller). v42 CBZ birleşik Kaydet penceresinde. */
@Composable
fun V40MoreMenuItems(kind: CanvasKind, viewModel: CanvasEditViewModel, close: () -> Unit) {
    val colors = NeuTheme.colors
    if (kind.paged) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.v40_page_settings)) },
            leadingIcon = { Icon(Icons.Filled.FormatListNumbered, null, tint = colors.accent) },
            onClick = { close(); viewModel.v40.openPageSettings() },
            modifier = Modifier.testTag("page_settings_menu_item"),
        )
    }
    // v42 CBZ olarak kaydet ⋮ menüsünde değil, birleşik Kaydet penceresinde (CBZ sekmesi)
}

/**
 * v40 çizim ekranının pencereleri (sayfa ayarları, CBZ) ve bildirimleri (CBZ ve CMYK belge kaydedildi). CanvasEditScreen
 * JVM yöntem sınırına yakın olduğundan ayrı işlevde.
 */
@Composable
fun V40EditorSheets(viewModel: CanvasEditViewModel, snackbar: SnackbarHostState, scope: CoroutineScope) {
    val canvas by viewModel.canvas.collectAsState()
    val current = canvas ?: return
    val pageSettingsOpen by viewModel.v40.pageSettingsOpen.collectAsState()
    if (pageSettingsOpen) {
        // v45 kapaklı çizgi romanda toplam sayfa kapaksız sayılır ("1 / n")
        PageSettingsSheet(
            current.pageSettings, com.karapp.drawguidance.data.model.PageNumbering.count(current.pageCount, current.hasCover),
            viewModel.v40::setPageSettings, viewModel.v40::closePageSettings,
        )
    }
    // v45 not alma panelleri penceresi
    V45EditorSheets(viewModel)
    val cbzOptions by viewModel.v40.cbzOptions.collectAsState()
    val unifiedTab by viewModel.saveTab.collectAsState()
    if (unifiedTab == null) cbzOptions?.let { options ->
        CbzOptionsSheet(options, current.pageCount, current.currentPage, viewModel.v40.documentLocation, viewModel.v40::exportCbz, viewModel.v40::closeCbz)
    }
    val cbz by viewModel.v40.cbz.collectAsState()
    val cbzSaved = stringResource(R.string.v40_cbz_saved)
    val cbzFailed = stringResource(R.string.v40_cbz_failed)
    LaunchedEffect(cbz) {
        val message = when (val s = cbz) {
            is PdfExportState.Done -> cbzSaved.format(s.file.location, com.karapp.drawguidance.ui.screens.canvasedit.formatBytes(s.file.sizeBytes))
            PdfExportState.Failed -> cbzFailed
            else -> null
        } ?: return@LaunchedEffect
        viewModel.v40.dismissCbz()
        snackbar.currentSnackbarData?.dismiss()
        scope.launch { snackbar.showSnackbar(message) }
    }
    // CMYK TIFF/PDF (resim kaydetme penceresinden) kaydedildi
    val pdf by viewModel.pdfExport.collectAsState()
    val printSaved = stringResource(R.string.v40_cmyk_saved)
    LaunchedEffect(pdf) {
        val s = pdf as? PdfExportState.PrintDone ?: return@LaunchedEffect
        viewModel.dismissPdf()
        snackbar.currentSnackbarData?.dismiss()
        scope.launch { snackbar.showSnackbar(printSaved.format(s.file.location, com.karapp.drawguidance.ui.screens.canvasedit.formatBytes(s.file.sizeBytes))) }
    }
}

/** v40 diyagram ekleme: zeminli ya da zeminsiz (saydam) seçimi; tema kimliğinde saklanır. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagramBackgroundRow(theme: String, onTheme: (String) -> Unit) {
    val colors = NeuTheme.colors
    val filled = DiagramThemes.hasBackground(theme)
    Text(stringResource(R.string.v40_diagram_bg), style = MaterialTheme.typography.titleSmall, color = colors.contentMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NeuChip(stringResource(R.string.v40_diagram_bg_filled), filled, { onTheme(DiagramThemes.withBackground(theme, true)) }, modifier = Modifier.testTag("diagram_bg_filled"))
        NeuChip(stringResource(R.string.v40_diagram_bg_clear), !filled, { onTheme(DiagramThemes.withBackground(theme, false)) }, modifier = Modifier.testTag("diagram_bg_clear"))
    }
}
