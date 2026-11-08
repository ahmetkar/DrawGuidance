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
 * v43 uygulamanın Android izinleri (`app_permissions`): ilk açılışta istenen izinlerin sonucu burada tutulur ve
 * uygulama izne bağlı işlerde (bildirim gösterme gibi) önce buraya, sonra sistemin güncel durumuna bakar.
 *
 * - [name]: izin adı (ör. `android.permission.POST_NOTIFICATIONS`).
 * - [granted]: son bilinen durum (her açılışta sistemden yenilenir: kullanıcı ayarlardan değiştirmiş olabilir).
 * - [requestedAt]: kullanıcıya en son ne zaman soruldu; [deniedCount]: kaç kez reddedildi.
 * - [permanentlyDenied]: "bir daha sorma" (sistem penceresi artık açılmaz; ayarlara yönlendirilir).
 */
@Entity(tableName = "app_permissions")
data class AppPermissionEntity(
    @PrimaryKey val name: String,
    @ColumnInfo(defaultValue = "0") val granted: Boolean = false,
    val requestedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val deniedCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val permanentlyDenied: Boolean = false,
    val updatedAt: Long,
) {
    companion object {
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `app_permissions` (`name` TEXT NOT NULL, `granted` INTEGER NOT NULL DEFAULT 0, " +
            "`requestedAt` INTEGER, `deniedCount` INTEGER NOT NULL DEFAULT 0, `permanentlyDenied` INTEGER NOT NULL DEFAULT 0, " +
            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`name`))"
    }
}

@Dao
interface AppPermissionDao {
    @Query("SELECT * FROM app_permissions")
    fun observeAll(): Flow<List<AppPermissionEntity>>

    @Query("SELECT * FROM app_permissions")
    suspend fun all(): List<AppPermissionEntity>

    @Query("SELECT * FROM app_permissions WHERE name = :name")
    suspend fun get(name: String): AppPermissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AppPermissionEntity)
}
