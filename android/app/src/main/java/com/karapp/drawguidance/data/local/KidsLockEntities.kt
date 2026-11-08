package com.karapp.drawguidance.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * v44 çocuk kilidi (`kids_lock`, tek satır): kilit açıkken uygulama yalnız çocuk çizim modunu gösterir.
 *
 * - [enabled]: kilit etkin mi. [source]: `AGE` (12 yaşından küçük girişli kullanıcı için kendiliğinden) ya da `MANUAL`
 *   (Ayarlar'dan).
 * - [pinHash]/[pinSalt]: ebeveynin 4 haneli şifresi; şifrenin kendisi saklanmaz (PBKDF2, rastgele tuz). Şifre yoksa
 *   (kilit yaşla açıldı, ebeveyn henüz belirlemedi) kilit yine etkindir ve ebeveynden şifre istenir.
 * - [ageAppliedUid]: yaş kuralının hangi hesap için uygulandığı (ebeveyn kilidi kapatınca aynı hesapta yeniden açılmaz).
 * - [failedAttempts]/[blockedUntil]: art arda yanlış şifrede bekleme (deneme yanılmayı yavaşlatır).
 */
@Entity(tableName = "kids_lock")
data class KidsLockEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(defaultValue = "0") val enabled: Boolean = false,
    val source: String? = null,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val ageAppliedUid: String? = null,
    @ColumnInfo(defaultValue = "0") val failedAttempts: Int = 0,
    @ColumnInfo(defaultValue = "0") val blockedUntil: Long = 0,
    val updatedAt: Long = 0,
) {
    companion object {
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `kids_lock` (`id` INTEGER NOT NULL, `enabled` INTEGER NOT NULL DEFAULT 0, " +
            "`source` TEXT, `pinHash` TEXT, `pinSalt` TEXT, `ageAppliedUid` TEXT, `failedAttempts` INTEGER NOT NULL DEFAULT 0, " +
            "`blockedUntil` INTEGER NOT NULL DEFAULT 0, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        const val SOURCE_AGE = "AGE"
        const val SOURCE_MANUAL = "MANUAL"
    }
}

@Dao
interface KidsLockDao {
    @Query("SELECT * FROM kids_lock WHERE id = 1")
    fun observe(): Flow<KidsLockEntity?>

    @Query("SELECT * FROM kids_lock WHERE id = 1")
    suspend fun get(): KidsLockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: KidsLockEntity)
}
