package com.karapp.drawguidance.ui.screens.images

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.images.SavedImageEntity
import com.karapp.drawguidance.data.local.FolderEntity
import com.karapp.drawguidance.data.local.FolderKind
import com.karapp.drawguidance.data.local.FolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Resimlerim: kaydedilen resimler ve klasörleri (not tuvalinin tüm sayfaları tuvalin adını taşıyan tek klasöre
 * kaydedilir). Resimler ve klasörler seçilip toplu silinebilir ya da klasöre taşınabilir.
 */
class ImagesViewModel(
    private val repository: ImagesRepository,
    private val folderRepository: FolderRepository? = null,
) : ViewModel() {

    val images: StateFlow<List<SavedImageEntity>?> = repository.observeImages()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val folders: StateFlow<List<FolderEntity>> = (folderRepository?.observe(FolderKind.IMAGE) ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentFolder = MutableStateFlow<String?>(null)
    val currentFolder: StateFlow<String?> = _currentFolder.asStateFlow()

    fun openFolder(id: String?) { _currentFolder.value = id }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    /** Seçilen resimleri ve klasörleri (içindeki resimlerle) siler. */
    fun deleteSelection(imageIds: List<Long>, folderIds: List<String>) {
        viewModelScope.launch {
            imageIds.forEach { repository.delete(it) }
            folderRepository?.deleteImageFolders(folderIds)
            if (_currentFolder.value in folderIds) _currentFolder.value = null
        }
    }

    fun moveImages(ids: List<Long>, folderId: String?) {
        viewModelScope.launch { repository.moveToFolder(ids, folderId) }
    }

    fun createFolder(name: String, moveImages: List<Long> = emptyList()) {
        val repo = folderRepository ?: return
        viewModelScope.launch {
            val folder = repo.create(name, FolderKind.IMAGE)
            if (moveImages.isNotEmpty()) repository.moveToFolder(moveImages, folder.id)
        }
    }

    fun renameFolder(id: String, name: String) {
        viewModelScope.launch { folderRepository?.rename(id, name) }
    }
}
