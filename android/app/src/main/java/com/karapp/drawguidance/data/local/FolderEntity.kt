package com.karapp.drawguidance.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Klasörün içerdiği öğeler. */
enum class FolderKind { CANVAS, IMAGE }

/**
 * Tuval ya da resim klasörü (tek seviye). Tuvaller ve resimler `folderId` ile klasöre bağlanır; klasör
 * silinince içindekiler de silinir (kullanıcıya önceden sorulur).
 */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** [FolderKind] adı. */
    val kind: String,
    val createdAt: Long,
    /** Klasörü oluşturan tuval (not sayfalarının kaydedildiği klasör; aynı tuvalin sonraki kayıtları da buraya). */
    @androidx.room.ColumnInfo(defaultValue = "") val sourceCanvasId: String = "",
)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE kind = :kind ORDER BY createdAt DESC")
    fun observe(kind: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE kind = :kind")
    suspend fun all(kind: String): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE kind = :kind AND sourceCanvasId = :canvasId LIMIT 1")
    suspend fun findForCanvas(kind: String, canvasId: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM folders WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}
