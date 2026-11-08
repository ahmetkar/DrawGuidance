package com.karapp.drawguidance.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karapp.drawguidance.data.settings.ExportSettings
import com.karapp.drawguidance.data.settings.ExportSettingsStore
import com.karapp.drawguidance.data.settings.ImageFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val exportSettings: ExportSettingsStore,
    private val developer: com.karapp.drawguidance.data.settings.DeveloperSettingsStore,
    private val editorPrefs: com.karapp.drawguidance.data.settings.EditorPrefsStore? = null,
    /** v32 eğitim önerileri. */
    private val tutorials: com.karapp.drawguidance.data.settings.TutorialPrefs? = null,
) : ViewModel() {

    /** v32 eğitim önerileri ilk girişteki gibi yeniden gösterilir. */
    fun resetTutorials(): Boolean {
        val t = tutorials ?: return false
        t.reset()
        return true
    }

    /** Çizim ekranı: otomatik kayıt. */
    val autosave: StateFlow<Boolean> = editorPrefs?.autosave ?: MutableStateFlow(true)
    fun setAutosave(value: Boolean) { editorPrefs?.setAutosave(value) }

    /** v20 çizim düzeltme önerileri. */
    val suggestions: StateFlow<Boolean> = editorPrefs?.handwriting ?: MutableStateFlow(true)
    fun setSuggestions(value: Boolean) { editorPrefs?.setHandwriting(value) }
    /** v23 yazı önerisi ve şekil önerisi ayrı ayrı. */
    val textSuggest: StateFlow<Boolean> = editorPrefs?.textSuggest ?: MutableStateFlow(true)
    fun setTextSuggest(value: Boolean) { editorPrefs?.setTextSuggest(value) }
    val shapeSuggest: StateFlow<Boolean> = editorPrefs?.shapeSuggest ?: MutableStateFlow(true)
    fun setShapeSuggest(value: Boolean) { editorPrefs?.setShapeSuggest(value) }
    /** v38 çizgi önerisi (yazı ve şekil önerisinden ayrı). */
    val lineSuggest: StateFlow<Boolean> = editorPrefs?.lineSuggest ?: MutableStateFlow(true)
    fun setLineSuggest(value: Boolean) { editorPrefs?.setLineSuggest(value) }
    /** v27 seçimde kendiliğinden öneri. */
    val selectionSuggest: StateFlow<Boolean> = editorPrefs?.selectionSuggest ?: MutableStateFlow(true)
    fun setSelectionSuggest(value: Boolean) { editorPrefs?.setSelectionSuggest(value) }

    /** Geliştirici modu: açıkken uygulama günlükleri görüntülenebilir. */
    val developerMode: StateFlow<Boolean> = developer.enabled

    fun setDeveloperMode(value: Boolean) = developer.setEnabled(value)

    /** Resim kaydetme ayarları: dizin, varsayılan çözünürlük ve biçim. */
    val export: StateFlow<ExportSettings> = exportSettings.settings

    fun setAlbumName(name: String) = exportSettings.setAlbumName(name)

    /** Android klasör seçiciden gelen klasör (kalıcı izin alındıktan sonra). */
    fun setExportFolder(uri: String, label: String) = exportSettings.setFolder(uri, label)

    /** Seçilen klasör bırakılır; resimler yeniden Galeri albümüne kaydedilir. */
    fun useGallery() = exportSettings.setFolder(null, null)

    fun setDefaultScale(scale: Float) = exportSettings.setDefaultScale(scale)

    fun setDefaultFormat(format: ImageFormat) = exportSettings.setDefaultFormat(format)
}
