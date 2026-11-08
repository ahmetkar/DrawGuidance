package com.karapp.drawguidance.ui.screens.canvaslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface CanvasListEvent {
    data class ImageSaved(val images: List<com.karapp.drawguidance.data.images.SavedImageEntity>) : CanvasListEvent {
        val image get() = images.first()
    }
    data object ImageSaveFailed : CanvasListEvent
    /** v40 baskıya hazır belge (CMYK TIFF/PDF) kaydedildi. */
    data class DocumentSaved(val file: com.karapp.drawguidance.data.images.ExportedFile) : CanvasListEvent
}

class CanvasListViewModel(
    private val repository: CanvasRepository,
    private val imagesRepository: ImagesRepository,
    private val assetStore: CanvasAssetStore,
    private val folderRepository: com.karapp.drawguidance.data.local.FolderRepository? = null,
    /** v23 galeriden arka plan resmi (formda seçilir, tuval oluşturulunca eklenir). */
    private val imageImporter: com.karapp.drawguidance.data.images.ImageImporter? = null,
    /** v29 silinen tuvalin kendi son ayarları da silinir. */
    private val editorPrefs: com.karapp.drawguidance.data.settings.EditorPrefsStore? = null,
    /** v40 tuvalde geçirilen süre (Tuvallerim'in üstündeki özet). */
    canvasTime: com.karapp.drawguidance.data.repository.CanvasTimeRepository? = null,
) : ViewModel() {

    /** v40 tuval kimliği → etkin düzenleme süresi (ms). */
    val times: StateFlow<Map<String, Long>> = (canvasTime?.observe() ?: kotlinx.coroutines.flow.flowOf(emptyMap()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Liste yalnızca ekranda görünürken güncellenir (çizim ekranındayken arka planda her
     * kayıtta tüm tuvaller yeniden çözülmez). Boyut: çizim verisi + eklenen resimler.
     */
    val canvases: StateFlow<List<CanvasUi>> = repository.observeCanvases()
        // Kart önizlemelerindeki resimler arka planda yüklenir
        .onEach { list -> assetStore.ensureLoaded(list.flatMap { c -> c.pages.flatten() }) }
        .map { list ->
            val assets = assetStore.bytesByCanvas()
            if (assets.isEmpty()) list else list.map { c -> assets[c.localId]?.let { c.copy(sizeBytes = c.sizeBytes + it) } ?: c }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Klasörler ---

    val folders: StateFlow<List<com.karapp.drawguidance.data.local.FolderEntity>> =
        (folderRepository?.observe(com.karapp.drawguidance.data.local.FolderKind.CANVAS) ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Açık klasör (null: ana liste). */
    private val _currentFolder = MutableStateFlow<String?>(null)
    val currentFolder: StateFlow<String?> = _currentFolder.asStateFlow()

    fun openFolder(id: String?) { _currentFolder.value = id }

    fun createFolder(name: String, moveCanvases: List<String> = emptyList()) {
        val repo = folderRepository ?: return
        viewModelScope.launch {
            val folder = repo.create(name, com.karapp.drawguidance.data.local.FolderKind.CANVAS)
            if (moveCanvases.isNotEmpty()) repository.moveToFolder(moveCanvases, folder.id)
        }
    }

    fun renameFolder(id: String, name: String) {
        viewModelScope.launch { folderRepository?.rename(id, name) }
    }

    fun moveCanvases(ids: List<String>, folderId: String?) {
        viewModelScope.launch { repository.moveToFolder(ids, folderId) }
    }

    /** Seçilen tuvalleri ve klasörleri (içindeki tuvallerle) siler. */
    fun deleteSelection(canvasIds: List<String>, folderIds: List<String>) {
        viewModelScope.launch {
            canvasIds.forEach { repository.deleteCanvas(it); editorPrefs?.clearCanvas(it) }
            folderRepository?.deleteCanvasFolders(folderIds)
            if (_currentFolder.value in folderIds) _currentFolder.value = null
        }
    }

    private val _events = MutableSharedFlow<CanvasListEvent>()
    val events: SharedFlow<CanvasListEvent> = _events

    private val _savingImageIds = MutableStateFlow<Set<String>>(emptySet())
    val savingImageIds: StateFlow<Set<String>> = _savingImageIds.asStateFlow()


    fun createCanvas(
        name: String,
        width: Int,
        height: Int,
        backgroundColorHex: String,
        aiEnabled: Boolean,
        onCreated: (localId: String) -> Unit,
        kind: com.karapp.drawguidance.data.model.CanvasKind = com.karapp.drawguidance.data.model.CanvasKind.ART,
        pageCount: Int = 1,
        background: com.karapp.drawguidance.data.model.BackgroundSpec = com.karapp.drawguidance.data.model.BackgroundSpec(),
        galleryUri: android.net.Uri? = null,
        /** v46 hazır şablon (çizgi roman ve not alma): boyut, kâğıt, sayfalar ve resimler şablondan gelir. */
        templateId: String? = null,
    ) {
        viewModelScope.launch {
            if (templateId != null) {
                val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.karapp.drawguidance.data.templates.CanvasTemplates.prepare(templateId, name)
                }
                if (prepared != null) {
                    val made = repository.createFromTemplate(name, kind, aiEnabled, prepared, folderId = _currentFolder.value)
                    onCreated(made.localId)
                    return@launch
                }
            }
            val created = repository.createCanvas(
                name = name,
                width = width,
                height = height,
                backgroundColorHex = backgroundColorHex,
                aiEnabled = aiEnabled,
                kind = kind,
                pageCount = pageCount,
                // Açık klasörde oluşturulan tuval o klasöre girer
                folderId = _currentFolder.value,
                background = background.withoutPending(),
                // v45 yeni çizgi roman kapak sayfası ve bir boş sayfayla başlar (başlık tuvalin adı)
                cover = if (kind == com.karapp.drawguidance.data.model.CanvasKind.COMIC) {
                    com.karapp.drawguidance.ui.screens.canvasedit.state.ComicCover.build(width, height, name, com.karapp.drawguidance.ui.screens.canvasedit.state.ComicCover.subtitlePlaceholder())
                } else null,
            )
            if (galleryUri != null) applyGallery(created.localId, background, galleryUri)
            onCreated(created.localId)
        }
    }

    private fun com.karapp.drawguidance.data.model.BackgroundSpec.withoutPending() =
        if (imageId == com.karapp.drawguidance.ui.components.GALLERY_PENDING) copy(imageId = null) else this

    /** Galeriden seçilen resmi tuvale ekleyip arka plan yapar (okunamazsa resimsiz kalır). */
    private suspend fun applyGallery(localId: String, spec: com.karapp.drawguidance.data.model.BackgroundSpec, uri: android.net.Uri) {
        val importer = imageImporter ?: return
        val image = runCatching { importer.check(uri) }.getOrNull()?.takeIf { it.accepted }?.image ?: return
        val assetId = runCatching { assetStore.save(localId, image) }.getOrNull() ?: return
        repository.saveBackgroundSpec(localId, spec.copy(imageId = com.karapp.drawguidance.data.model.BackgroundSpec.ASSET + assetId))
    }

    fun updateSettings(
        localId: String,
        name: String,
        width: Int,
        height: Int,
        backgroundColorHex: String,
        aiEnabled: Boolean,
        background: com.karapp.drawguidance.data.model.BackgroundSpec? = null,
        galleryUri: android.net.Uri? = null,
    ) {
        viewModelScope.launch {
            repository.updateSettings(localId, name, width, height, backgroundColorHex, aiEnabled, background?.withoutPending())
            if (galleryUri != null && background != null) applyGallery(localId, background, galleryUri)
        }
    }

    fun deleteCanvas(localId: String) {
        viewModelScope.launch {
            repository.deleteCanvas(localId)
            editorPrefs?.clearCanvas(localId)
        }
    }

    /** Kaydetme penceresi: hangi tuval ve başlangıç ayarları (null: kapalı). */
    data class SaveDialog(val canvas: CanvasUi, val options: com.karapp.drawguidance.data.images.ExportOptions)

    private val _saveDialog = MutableStateFlow<SaveDialog?>(null)
    val saveDialog: StateFlow<SaveDialog?> = _saveDialog.asStateFlow()

    /** Resimlerin kaydedileceği yer. */
    val imageSaveLocation: String get() = imagesRepository.locationLabel
    /** v40 CMYK PDF'in varsayılan yeri. */
    val pdfLocationLabel: String get() = imagesRepository.pdfLocationLabel

    /** Seçili tuval için kaydetme ayarlarını soran pencereyi açar. */
    fun openSaveDialog(localId: String) {
        if (localId in _savingImageIds.value) return
        viewModelScope.launch {
            val canvas = repository.getCanvas(localId)
            if (canvas == null) {
                _events.emit(CanvasListEvent.ImageSaveFailed)
                return@launch
            }
            _saveDialog.value = SaveDialog(canvas, imagesRepository.defaultOptions(canvas))
        }
    }

    fun closeSaveDialog() {
        _saveDialog.value = null
    }

    fun imageOutputSize(canvas: CanvasUi, scale: Float): Pair<Int, Int> = imagesRepository.outputSize(canvas, scale)

    /** Tuvali varsayılan ayarlarla kaydeder; Resimlerim'de listelenir. */
    fun saveAsImage(localId: String) = saveAsImage(localId, null)

    /** Tuvali penceredeki ayarlarla (null: varsayılan) kaydeder. */
    fun saveAsImage(localId: String, options: com.karapp.drawguidance.data.images.ExportOptions?) {
        _saveDialog.value = null
        if (localId in _savingImageIds.value) return
        viewModelScope.launch {
            _savingImageIds.value = _savingImageIds.value + localId
            try {
                val canvas = repository.getCanvas(localId) ?: throw IllegalStateException("Tuval bulunamadı")
                // v40 CMYK TIFF/PDF belge olarak kaydedilir (Resimlerim'e eklenmez)
                if (options?.isDocument == true) {
                    _events.emit(CanvasListEvent.DocumentSaved(imagesRepository.exportCmykDocument(canvas, options)))
                    return@launch
                }
                val saved = imagesRepository.saveCanvasAsImage(canvas, options ?: imagesRepository.defaultOptions(canvas))
                if (saved.isEmpty()) throw IllegalStateException("Resim kaydedilemedi")
                _events.emit(CanvasListEvent.ImageSaved(saved))
            } catch (e: Exception) {
                _events.emit(CanvasListEvent.ImageSaveFailed)
            } finally {
                _savingImageIds.value = _savingImageIds.value - localId
            }
        }
    }
}
