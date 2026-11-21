package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.data.images.CbzOptions
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.model.PageSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * v40 sayfa ayarları (sayfa numarası, üst ve alt bilgi; tuval başına kaydedilir) ve çizgi roman arşivi (.cbz)
 * dışa aktarma.
 */
class V40ExportController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val imagesRepository: ImagesRepository,
    private val savePageSettings: suspend (String, PageSettings) -> Unit,
) {
    // --- Sayfa ayarları ---

    private val _pageSettingsOpen = MutableStateFlow(false)
    val pageSettingsOpen: StateFlow<Boolean> = _pageSettingsOpen.asStateFlow()

    fun openPageSettings() { _pageSettingsOpen.value = true }
    fun closePageSettings() { _pageSettingsOpen.value = false }

    /** Ayarları tuvale uygular (hemen görünür) ve kaydeder. */
    fun setPageSettings(settings: PageSettings) {
        val current = store.canvas.value ?: return
        if (current.pageSettings == settings) return
        store.updateCanvas { it.copy(pageSettings = settings) }
        host.scope.launch {
            runCatching { savePageSettings(store.canvasLocalId, settings) }
                .onFailure { com.karapp.drawguidance.diagnostics.AppLog.e("PageSettings", "Sayfa ayarları kaydedilemedi", it) }
        }
    }

    // --- CBZ ---

    private val _cbzOptions = MutableStateFlow<CbzOptions?>(null)
    /** Açık CBZ penceresinin başlangıç ayarları (null: kapalı). */
    val cbzOptions: StateFlow<CbzOptions?> = _cbzOptions.asStateFlow()

    private val _cbz = MutableStateFlow<PdfExportState>(PdfExportState.Idle)
    val cbz: StateFlow<PdfExportState> = _cbz.asStateFlow()

    fun openCbz() {
        val current = store.canvas.value ?: return
        if (_cbz.value == PdfExportState.Exporting) return
        _cbzOptions.value = CbzOptions(ImageExporter.defaultFileName(current.name), toPage = current.pageCount.coerceAtLeast(1))
    }

    fun closeCbz() { _cbzOptions.value = null }

    fun exportCbz(options: CbzOptions) {
        val current = store.canvas.value ?: return
        _cbzOptions.value = null
        if (_cbz.value == PdfExportState.Exporting) return
        _cbz.value = PdfExportState.Exporting
        host.scope.launch {
            _cbz.value = try {
                PdfExportState.Done(imagesRepository.exportCbz(current, options))
            } catch (e: Exception) {
                com.karapp.drawguidance.diagnostics.AppLog.e("CbzExport", "CBZ kaydedilemedi", e, userVisible = true)
                PdfExportState.Failed
            }
        }
    }

    fun dismissCbz() { _cbz.value = PdfExportState.Idle }

    // --- v56 aşama 9: SVG / PSD / DXF ---

    sealed interface DocState {
        data object Idle : DocState
        data object Exporting : DocState
        data class Done(val file: com.karapp.drawguidance.data.images.ExportedFile, val skipped: Int) : DocState
        data object Failed : DocState
    }

    private val _doc = MutableStateFlow<DocState>(DocState.Idle)
    val doc: StateFlow<DocState> = _doc.asStateFlow()

    fun exportDoc(format: com.karapp.drawguidance.data.export.DocFormat, fileName: String) {
        val current = store.canvas.value ?: return
        if (_doc.value == DocState.Exporting) return
        _doc.value = DocState.Exporting
        host.scope.launch {
            _doc.value = try {
                val r = imagesRepository.exportDoc(current, format, fileName)
                DocState.Done(r.file, r.skipped)
            } catch (e: Exception) {
                com.karapp.drawguidance.diagnostics.AppLog.e("DocExport", "${format.name} kaydedilemedi", e, userVisible = true)
                DocState.Failed
            }
        }
    }

    fun dismissDoc() { _doc.value = DocState.Idle }

    /** Varsayılan dosya adı. */
    fun defaultDocName(): String = store.canvas.value?.let { ImageExporter.defaultFileName(it.name) } ?: ""

    /** CBZ'nin varsayılan yeri. */
    val documentLocation: String get() = imagesRepository.pdfLocationLabel
}
