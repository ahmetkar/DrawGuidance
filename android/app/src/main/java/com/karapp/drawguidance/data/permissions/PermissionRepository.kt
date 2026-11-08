package com.karapp.drawguidance.data.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.karapp.drawguidance.data.local.AppPermissionDao
import com.karapp.drawguidance.data.local.AppPermissionEntity
import kotlinx.coroutines.flow.Flow

/**
 * v43 uygulamanın Android çalışma zamanı izinleri: ilk girişte sistem penceresiyle istenir, sonuç
 * `app_permissions` tablosuna yazılır. Her açılışta sistemin güncel durumu tabloya yansıtılır ([refresh]: kullanıcı
 * izni ayarlardan değiştirmiş olabilir). İzne bağlı işler (bildirim gösterme) [granted] ile bakar.
 *
 * Uygulamanın gerçekten istediği izin yalnız Android 13+ **bildirim** iznidir: resim seçme sistemin seçicisiyle,
 * kaydetme MediaStore ile yapılır (depolama izni gerekmez); ağ izinleri kurulumda verilir.
 */
class PermissionRepository(
    private val context: Context,
    private val dao: AppPermissionDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    /** Bu cihazda çalışma zamanında istenmesi gereken izinler. */
    fun required(): List<String> = buildList {
        if (sdkInt >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun systemGranted(name: String): Boolean =
        ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED

    val all: Flow<List<AppPermissionEntity>> = dao.observeAll()

    /** Sistemdeki durumu tabloya yazar. */
    suspend fun refresh() {
        val now = clock()
        for (name in required()) {
            val granted = systemGranted(name)
            val row = dao.get(name)
            if (row == null) dao.upsert(AppPermissionEntity(name, granted = granted, updatedAt = now))
            else if (row.granted != granted) dao.upsert(row.copy(granted = granted, permanentlyDenied = row.permanentlyDenied && !granted, updatedAt = now))
        }
    }

    /** İlk girişte istenecekler: gerekli, verilmemiş ve daha önce hiç sorulmamış izinler. */
    suspend fun toAskOnFirstLaunch(): List<String> {
        refresh()
        return required().filter { name -> !systemGranted(name) && dao.get(name)?.requestedAt == null }
    }

    /** Sistem penceresinin sonucu. [canAskAgain]: false ise kullanıcı "bir daha sorma" dedi. */
    suspend fun record(name: String, granted: Boolean, canAskAgain: Boolean) {
        val now = clock()
        val row = dao.get(name) ?: AppPermissionEntity(name, updatedAt = now)
        dao.upsert(
            row.copy(
                granted = granted, requestedAt = now, updatedAt = now,
                deniedCount = if (granted) row.deniedCount else row.deniedCount + 1,
                permanentlyDenied = !granted && !canAskAgain,
            ),
        )
    }

    /** İzin var mı (önce sistem: kayıt eskiyse güncellenir). */
    suspend fun granted(name: String): Boolean {
        if (sdkInt < 33 && name == Manifest.permission.POST_NOTIFICATIONS) return true
        val now = systemGranted(name)
        val row = dao.get(name)
        if (row == null || row.granted != now) dao.upsert((row ?: AppPermissionEntity(name, updatedAt = clock())).copy(granted = now, permanentlyDenied = (row?.permanentlyDenied ?: false) && !now, updatedAt = clock()))
        return now
    }

    suspend fun get(name: String): AppPermissionEntity? = dao.get(name)
}
