package com.karapp.drawguidance.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * v34 dil ayarı (tek satır, `id = 1`): seçili dil, otomatik mi (ilk açılışta sistemden algılandı) yoksa kullanıcı mı
 * seçti, algılamada kullanılan sistem dili ve ülkesi.
 */
@Entity(tableName = "language_settings")
data class LanguageSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val languageCode: String,
    val auto: Boolean,
    val systemLanguage: String? = null,
    val systemCountry: String? = null,
    val updatedAt: Long,
)

@Dao
interface LanguageSettingsDao {
    @Query("SELECT * FROM language_settings WHERE id = 1")
    suspend fun get(): LanguageSettingsEntity?

    @Query("SELECT * FROM language_settings WHERE id = 1")
    fun observe(): Flow<LanguageSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LanguageSettingsEntity)
}
