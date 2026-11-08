package com.karapp.drawguidance.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Tuvale eklenen resimlerin verisi. Çizim içeriğinde yalnızca kimliği tutulur;
 * resim baytları (en küçük kayıpsız biçimde) burada bir kez saklanır.
 */
@Entity(tableName = "canvas_assets", indices = [Index("canvasLocalId")])
class CanvasAssetEntity(
    @PrimaryKey val id: String,
    val canvasLocalId: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val data: ByteArray,
    val createdAt: Long,
)

/** Tuvalin resim verilerinin toplam boyutu. */
data class CanvasAssetBytes(val canvasLocalId: String, val bytes: Long)

@Dao
interface CanvasAssetDao {
    @Query("SELECT canvasLocalId, COALESCE(SUM(LENGTH(data)), 0) AS bytes FROM canvas_assets GROUP BY canvasLocalId")
    suspend fun bytesByCanvas(): List<CanvasAssetBytes>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: CanvasAssetEntity)

    @Query("SELECT * FROM canvas_assets WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<CanvasAssetEntity>

    @Query("SELECT id FROM canvas_assets WHERE canvasLocalId = :canvasLocalId")
    suspend fun idsForCanvas(canvasLocalId: String): List<String>

    @Query("DELETE FROM canvas_assets WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM canvas_assets WHERE canvasLocalId = :canvasLocalId")
    suspend fun deleteForCanvas(canvasLocalId: String)

    @Query("SELECT COALESCE(SUM(LENGTH(data)), 0) FROM canvas_assets WHERE canvasLocalId = :canvasLocalId")
    suspend fun totalBytes(canvasLocalId: String): Long
}
