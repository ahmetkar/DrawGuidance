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

    /** CBZ'nin varsayılan yeri. */
    val documentLocation: String get() = imagesRepository.pdfLocationLabel
}
