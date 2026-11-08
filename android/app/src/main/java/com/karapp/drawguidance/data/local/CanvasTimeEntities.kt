package com.karapp.drawguidance.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * v40 tuvalde geçirilen etkin düzenleme süresi (`canvas_time`, tuval başına bir satır). Süre `canvases` tablosunda
 * değil ayrı tabloda tutulur: tuval kaydı (çizim kuyruğu, eşitleme, aktarma) bütün satırı yeniden yazdığından aynı
 * satırdaki bir sayaç eski değerle ezilebilirdi; ayrı tabloda süre yalnız artırılır ([CanvasTimeDao.add]) ve tuval
 * içeriğine dokunulmaz. Silinen tuvallerin satırları gösterimde tuvallerle birleştirilirken düşer ([prune] ile temizlenir).
 */
@Entity(tableName = "canvas_time")
data class CanvasTimeEntity(
    @PrimaryKey val canvasLocalId: String,
    /** Toplam etkin düzenleme süresi (ms). */
    val timeSpentMs: Long,
    /** Süre en son eklendiğinde (ms, duvar saati). */
    val updatedAt: Long,
) {
    companion object {
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `canvas_time` (`canvasLocalId` TEXT NOT NULL, `timeSpentMs` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`canvasLocalId`))"
    }
}

@Dao
interface CanvasTimeDao {
    @Query("SELECT * FROM canvas_time")
    fun observeAll(): Flow<List<CanvasTimeEntity>>

    @Query("SELECT * FROM canvas_time WHERE canvasLocalId = :id")
    suspend fun get(id: String): CanvasTimeEntity?

    @Query("INSERT OR IGNORE INTO canvas_time (canvasLocalId, timeSpentMs, updatedAt) VALUES (:id, 0, :now)")
    suspend fun ensure(id: String, now: Long)

    @Query("UPDATE canvas_time SET timeSpentMs = timeSpentMs + :ms, updatedAt = :now WHERE canvasLocalId = :id")
    suspend fun increment(id: String, ms: Long, now: Long)

    /** Süreyi atomik olarak ekler (satır yoksa oluşturulur). */
    @Transaction
    suspend fun add(id: String, ms: Long, now: Long) {
        ensure(id, now)
        increment(id, ms, now)
    }

    /** Artık olmayan tuvallerin satırlarını siler. */
    @Query("DELETE FROM canvas_time WHERE canvasLocalId NOT IN (SELECT localId FROM canvases)")
    suspend fun prune()
}
