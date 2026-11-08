package com.karapp.drawguidance.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Güncellemeler satırın tamamını değil yalnızca ilgili kolonları yazar: çizim kaydı ve
 * ayar güncellemesi eşzamanlı gerçekleşebildiği için "oku-değiştir-yaz" birbirini ezebilir.
 */
@Dao
interface CanvasDao {

    @Query("SELECT * FROM canvases ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CanvasEntity>>

    @Query("SELECT * FROM canvases WHERE localId = :localId")
    suspend fun getByLocalId(localId: String): CanvasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CanvasEntity)

    @Query("UPDATE canvases SET content = :content, updatedAt = :updatedAt WHERE localId = :localId")
    suspend fun updateContent(localId: String, content: ByteArray, updatedAt: Long)

    @Query(
        """
        UPDATE canvases
        SET name = :name,
            width = :width,
            height = :height,
            backgroundColorHex = :backgroundColorHex,
            aiEnabled = :aiEnabled,
            updatedAt = :updatedAt
        WHERE localId = :localId
        """,
    )
    suspend fun updateSettings(
        localId: String,
        name: String,
        width: Int,
        height: Int,
        backgroundColorHex: String,
        aiEnabled: Boolean,
        updatedAt: Long,
    )

    /** v30 yalnız yapay zekâ ayarı (içerik okunmadan). */
    @Query("SELECT aiEnabled FROM canvases WHERE localId = :localId")
    suspend fun aiEnabled(localId: String): Boolean?

    /** v30 tuvalin yapay zekâ ayarı (düzenleyicinin ⋮ menüsünden). */
    @Query("UPDATE canvases SET aiEnabled = :aiEnabled WHERE localId = :localId")
    suspend fun updateAiEnabled(localId: String, aiEnabled: Boolean)

    /** Tuval genişletme/daraltma: boyut ve (kaydırılmış) çizimler birlikte yazılır. */
    @Query(
        """
        UPDATE canvases
        SET width = :width,
            height = :height,
            content = :content,
            updatedAt = :updatedAt
        WHERE localId = :localId
        """,
    )
    suspend fun updateSizeAndContent(localId: String, width: Int, height: Int, content: ByteArray, updatedAt: Long)

    @Query("UPDATE canvases SET currentPage = :currentPage WHERE localId = :localId")
    suspend fun updateCurrentPage(localId: String, currentPage: Int)

    @Query("UPDATE canvases SET guide = :guide, updatedAt = :updatedAt WHERE localId = :localId")
    suspend fun updateGuide(localId: String, guide: String, updatedAt: Long)

    @Query("UPDATE canvases SET backgroundColorHex = :backgroundColorHex, updatedAt = :updatedAt WHERE localId = :localId")
    suspend fun updateBackground(localId: String, backgroundColorHex: String, updatedAt: Long)

    @Query("UPDATE canvases SET background = :background, updatedAt = :updatedAt WHERE localId = :localId")
    suspend fun updateBackgroundSpec(localId: String, background: String, updatedAt: Long)

    @Query("DELETE FROM canvases WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("UPDATE canvases SET folderId = :folderId WHERE localId IN (:ids)")
    suspend fun moveTo(ids: List<String>, folderId: String?)

    @Query("SELECT localId FROM canvases WHERE folderId IN (:folderIds)")
    suspend fun idsInFolders(folderIds: List<String>): List<String>
}
