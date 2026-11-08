package com.karapp.drawguidance.data.images

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tuvalden dışa aktarılmış bir dosyanın kaydı (Kaydedilenler ekranı). v42: resimlerin (PNG, JPEG, WebP, CMYK) yanında
 * PDF ve CBZ belgeleri de ([mime]); [defaultLocation] dosya varsayılan kayıt yerinde mi (yoksa "Konum seç" ile
 * seçilen klasörde ya da "Farklı kaydet" ile seçilen yerde).
 */
@Entity(tableName = "saved_images")
data class SavedImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val canvasLocalId: String,
    val title: String,
    /** content:// (Galeri, Android 10+) ya da file:// (uygulama klasörü) adresi */
    val uri: String,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    /** Kullanıcıya gösterilen konum, ör. "Pictures/DrawGuidance/Deneme_20260915_1830.png" */
    val location: String,
    /** PNG dosya boyutu (bayt). Eski kayıtlarda 0 gelir ve ilk gösterimde hesaplanır. */
    @androidx.room.ColumnInfo(defaultValue = "0") val sizeBytes: Long = 0,
    /** Klasör ([com.karapp.drawguidance.data.local.FolderEntity.id]); null: kök. Not sayfaları tuvalin klasörüne. */
    val folderId: String? = null,
    /** v42 dosya türü (image/png, image/jpeg, application/pdf, application/vnd.comicbook+zip...). */
    @androidx.room.ColumnInfo(defaultValue = "image/png") val mime: String = "image/png",
    /** v42 varsayılan kayıt yerinde mi (Kaydedilenler'de iki sekme). */
    @androidx.room.ColumnInfo(defaultValue = "1") val defaultLocation: Boolean = true,
) {
    /** Resim mi (önizlenir); değilse belge (PDF, CBZ, TIFF). */
    val isImage: Boolean get() = mime.startsWith("image/") && mime != "image/tiff"

    /** Kısa tür adı (rozet). */
    val typeLabel: String get() = when {
        mime == "application/pdf" -> "PDF"
        mime.contains("comicbook") || location.endsWith(".cbz", ignoreCase = true) -> "CBZ"
        mime == "image/jpeg" -> "JPEG"
        mime == "image/webp" -> "WEBP"
        mime == "image/tiff" -> "TIFF"
        else -> "PNG"
    }

    companion object {
        /** CBZ'nin türü (Android'in tür tablosunda yoksa). */
        const val CBZ_MIME = "application/vnd.comicbook+zip"

        /**
         * Adres varsayılan kayıt yerinde mi: Galeri/İndirilenler (MediaStore) ve uygulama klasörü varsayılan; klasör
         * seçiciyle ya da "Farklı kaydet" ile seçilen belgeler (Storage Access Framework) değil.
         */
        fun isDefaultUri(uri: String): Boolean = !(uri.contains(".documents/") || uri.contains("/tree/") || uri.contains("/document/"))
    }
}
