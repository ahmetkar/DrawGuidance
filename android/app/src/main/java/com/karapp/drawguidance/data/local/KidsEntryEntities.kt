package com.karapp.drawguidance.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * v40 çocuk tuvaline girişlerin sayacı (`kids_entries`): kullanıcı başına bir satır ([userKey]: giriş yapılmışsa
 * "uid:<Firebase uid>", yapılmamışsa cihazın "device" satırı). İlk girişlerde maskot Boyacık çizim görevini önerir
 * (bkz. KidsEntryRepository); önerinin kaç kez gösterildiği ve Başlat / İptal sayıları da burada tutulur.
 */
@Entity(tableName = "kids_entries")
data class KidsEntryEntity(
    @PrimaryKey val userKey: String,
    /** Çocuk tuvaline kaç kez girildi. */
    val entries: Int,
    /** Görev önerisi kaç kez gösterildi. */
    val suggested: Int,
    /** Öneride kaç kez Başlat'a dokunuldu. */
    val started: Int,
    /** Öneride kaç kez İptal'e dokunuldu. */
    val cancelled: Int,
    /** Son giriş zamanı (ms). */
    val lastEntryAt: Long,
) {
    companion object {
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `kids_entries` (`userKey` TEXT NOT NULL, `entries` INTEGER NOT NULL, " +
            "`suggested` INTEGER NOT NULL, `started` INTEGER NOT NULL, `cancelled` INTEGER NOT NULL, `lastEntryAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`userKey`))"
    }
}

@Dao
interface KidsEntryDao {
    @Query("SELECT * FROM kids_entries WHERE userKey = :userKey")
    suspend fun get(userKey: String): KidsEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: KidsEntryEntity)

    @Query("SELECT COUNT(*) FROM kids_entries")
    suspend fun count(): Int
}
