package com.karapp.drawguidance.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Yerel SQLite (Room) tablosu — çizimlerin tek kaydı (sunucuya gönderilmez).
 * `localId` istemcide üretilir ve birincil anahtardır. `content`, List<Stroke>'un
 * [ContentCodec] ile kompakt ikili biçime çevrilip DEFLATE ile (kayıpsız)
 * sıkıştırılmış halidir.
 */
@Entity(tableName = "canvases")
data class CanvasEntity(
    @PrimaryKey val localId: String,
    val name: String,
    val width: Int,
    val height: Int,
    val backgroundColorHex: String,
    val aiEnabled: Boolean,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val content: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
    /** Tuval türü ([com.karapp.drawguidance.data.model.CanvasKind] adı). */
    @ColumnInfo(defaultValue = "ART") val kind: String = "ART",
    /** Açık sayfanın sırası (çalışma/not tuvallerinde birden çok sayfa olabilir). */
    @ColumnInfo(defaultValue = "0") val currentPage: Int = 0,
    /** Kılavuz ayarları (JSON; boşsa kılavuz yok). */
    @ColumnInfo(defaultValue = "") val guide: String = "",
    /** Klasör ([FolderEntity.id]); null: Tuvallerim kökü. */
    val folderId: String? = null,
    /** v23 arka plan resmi/deseni (JSON, [com.karapp.drawguidance.data.model.BackgroundSpec]; boşsa yalnızca renk). */
    @ColumnInfo(defaultValue = "") val background: String = "",
)
