package com.karapp.drawguidance.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karapp.drawguidance.BuildConfig
import com.karapp.drawguidance.data.remote.v1.RemoteStore
import com.karapp.drawguidance.diagnostics.AppLog
import java.util.concurrent.TimeUnit

/**
 * v40 günlük bildirim işi: uzun süre kullanılmama hatırlatması (yerel, ağ gerekmez) ve güncelleme denetimi
 * (`GET /api/v1/app/version`; ağ yoksa atlanır, ertesi gün yeniden). Kararlar [EngagementRules]'ta.
 */
class EngagementWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val koin = org.koin.core.context.GlobalContext.getOrNull()
        val prefs = koin?.getOrNull<NotificationPrefs>() ?: NotificationPrefs(applicationContext)
        EngagementChecks(applicationContext, prefs, koin?.getOrNull<RemoteStore>()).run()
        return Result.success()
    }
}

/** v40 bildirim denetimlerinin kendisi (işçi ve testler kullanır). */
class EngagementChecks(
    private val context: Context,
    private val prefs: NotificationPrefs,
    private val remote: RemoteStore?,
    private val installedVersion: Long = BuildConfig.VERSION_CODE.toLong(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val postReminder: (Int) -> Boolean = { AppNotifications.postReminder(context, it) },
    private val postUpdate: (String?) -> Boolean = { AppNotifications.postUpdate(context, it) },
) {
    suspend fun run() {
        checkInactivity()
        checkUpdate()
    }

    /** Hatırlatma gönderildiyse sırası (1–3). */
    fun checkInactivity(): Int? {
        if (!prefs.inactivityEnabled.value) return null
        val stage = EngagementRules.inactivityReminderDue(prefs.lastOpenedAt, clock(), prefs.remindersSent) ?: return null
        // Bildirim gönderilemese de (izin yok) sayaç ilerler: sonraki eşikte yeniden denenir
        postReminder(stage)
        prefs.markReminderSent(stage)
        return stage
    }

    /** Sunucudan sürüm bilgisi alınır, saklanır; karar döner (ağ hatasında null). */
    suspend fun checkUpdate(): UpdateDecision? {
        val r = remote ?: return null
        val v = runCatching { r.appVersion() }.getOrElse { AppLog.w(TAG, "sürüm bilgisi alınamadı: ${it.message}"); return null }
        prefs.saveVersion(v.latestVersionCode, v.minSupportedVersionCode, v.message)
        val d = EngagementRules.update(installedVersion, v.latestVersionCode, v.minSupportedVersionCode, prefs.lastNotifiedVersion)
        if (d != UpdateDecision.NONE && prefs.updatesEnabled.value && v.latestVersionCode > prefs.lastNotifiedVersion) {
            if (postUpdate(v.message)) prefs.markUpdateNotified(v.latestVersionCode)
        }
        return d
    }

    private companion object { const val TAG = "Engagement" }
}

/** v40 günlük bildirim işinin planlanması (uygulama açılışında; zaten planlıysa dokunulmaz). */
object EngagementScheduler {
    private const val WORK = "dg-engagement-daily"

    fun schedule(context: Context) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        wm.enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<EngagementWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()).build(),
        )
    }
}
