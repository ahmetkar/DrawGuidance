package com.karapp.drawguidance.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Kaydedilen resmin biçimi. */
enum class ImageFormat(val extension: String, val mime: String, val label: String) {
    PNG("png", "image/png", "PNG"),
    JPEG("jpg", "image/jpeg", "JPEG"),
}

/**
 * Resim kaydetme ayarları (Ayarlar ekranından değiştirilir):
 * - [albumName]: Galeri'de `Pictures/<albumName>` klasörü (varsayılan konum)
 * - [folderUri]: kullanıcının seçtiği klasör (Android klasör seçici, kalıcı izinle); verilirse oraya kaydedilir
 * - [defaultScale] ve [defaultFormat]: kaydetme penceresinin başlangıç değerleri
 */
data class ExportSettings(
    val albumName: String = DEFAULT_ALBUM,
    val folderUri: String? = null,
    val folderLabel: String? = null,
    val defaultScale: Float = 1f,
    val defaultFormat: ImageFormat = ImageFormat.PNG,
) {
    /** Kullanıcıya gösterilen konum. */
    val locationLabel: String get() = folderLabel ?: "Pictures/$albumName"

    companion object {
        const val DEFAULT_ALBUM = "DrawGuidance"
        /** Kaydetme penceresinde ve Ayarlar'da sunulan çözünürlük katları. */
        val Scales = listOf(0.5f, 1f, 2f, 4f)
    }
}

class ExportSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("export_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<ExportSettings> = _settings.asStateFlow()

    fun setAlbumName(name: String) = update { it.copy(albumName = sanitizeAlbum(name)) }

    fun setFolder(uri: String?, label: String?) = update { it.copy(folderUri = uri, folderLabel = if (uri == null) null else label) }

    fun setDefaultScale(scale: Float) = update { it.copy(defaultScale = scale.coerceIn(0.25f, 8f)) }

    fun setDefaultFormat(format: ImageFormat) = update { it.copy(defaultFormat = format) }

    /**
     * v40 kaydetme pencerelerinde "Konum seç" ile seçilen son klasör (kalıcı izinli; sonraki kayıtlarda hazır sunulur).
     * Ayarlar'daki varsayılan klasörden ayrıdır: seçmek varsayılan yeri değiştirmez.
     */
    fun pickedFolder(): com.karapp.drawguidance.data.images.SaveTarget.Folder? {
        val uri = prefs.getString(KEY_PICKED_URI, null) ?: return null
        return com.karapp.drawguidance.data.images.SaveTarget.Folder(uri, prefs.getString(KEY_PICKED_LABEL, null) ?: uri)
    }

    fun setPickedFolder(folder: com.karapp.drawguidance.data.images.SaveTarget.Folder?) {
        prefs.edit().putString(KEY_PICKED_URI, folder?.treeUri).putString(KEY_PICKED_LABEL, folder?.label).apply()
    }

    private fun update(block: (ExportSettings) -> ExportSettings) {
        val next = block(_settings.value)
        prefs.edit()
            .putString(KEY_ALBUM, next.albumName)
            .putString(KEY_FOLDER, next.folderUri)
            .putString(KEY_FOLDER_LABEL, next.folderLabel)
            .putFloat(KEY_SCALE, next.defaultScale)
            .putString(KEY_FORMAT, next.defaultFormat.name)
            .apply()
        _settings.value = next
    }

    private fun load(): ExportSettings = ExportSettings(
        albumName = prefs.getString(KEY_ALBUM, null)?.let { sanitizeAlbum(it) } ?: ExportSettings.DEFAULT_ALBUM,
        folderUri = prefs.getString(KEY_FOLDER, null),
        folderLabel = prefs.getString(KEY_FOLDER_LABEL, null),
        defaultScale = prefs.getFloat(KEY_SCALE, 1f),
        defaultFormat = ImageFormat.entries.firstOrNull { it.name == prefs.getString(KEY_FORMAT, null) } ?: ImageFormat.PNG,
    )

    companion object {
        private const val KEY_ALBUM = "album"
        private const val KEY_FOLDER = "folder_uri"
        private const val KEY_FOLDER_LABEL = "folder_label"
        private const val KEY_SCALE = "scale"
        private const val KEY_FORMAT = "format"
        private const val KEY_PICKED_URI = "v40_picked_folder_uri"
        private const val KEY_PICKED_LABEL = "v40_picked_folder_label"

        /**
         * Klasör seçiciden gelen ağaç adresinin okunur adı:
         * "primary:Pictures/Çizimler" → "Pictures/Çizimler", "1A2B-3C4D:Resimler" → "SD kart/Resimler".
         */
        fun folderLabelOf(treeDocumentId: String): String {
            val volume = treeDocumentId.substringBefore(':', "")
            val path = treeDocumentId.substringAfter(':', treeDocumentId).trim('/')
            val prefix = when {
                volume.isEmpty() || volume == "primary" -> ""
                volume == "home" -> "Documents/"
                else -> "SD kart/"
            }
            return (prefix + path).trim('/').ifEmpty { if (prefix.isEmpty()) "Dahili depolama" else prefix.trim('/') }
        }

        /** Albüm adı: dosya sisteminde sorun çıkaracak karakterler temizlenir, boşsa varsayılan. */
        fun sanitizeAlbum(name: String): String =
            name.trim().replace(Regex("[\\\\/:*?\"<>|]+"), "_").take(40).ifBlank { ExportSettings.DEFAULT_ALBUM }
    }
}
