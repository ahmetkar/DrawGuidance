package com.karapp.drawguidance.notify

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v40 bildirim ayarları ve durumu (ayar dosyası): türlere göre açık/kapalı (Ayarlar → Bildirimler), son açılış,
 * gönderilen hatırlatma sayısı, sunucunun son bildirdiği sürümler ve izin açıklamasının gösterilip gösterilmediği.
 */
class NotificationPrefs(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences("notifications", Context.MODE_PRIVATE)

    private val _inactivity = MutableStateFlow(prefs.getBoolean(KEY_INACTIVITY, true))
    val inactivityEnabled: StateFlow<Boolean> = _inactivity.asStateFlow()
    private val _updates = MutableStateFlow(prefs.getBoolean(KEY_UPDATES, true))
    val updatesEnabled: StateFlow<Boolean> = _updates.asStateFlow()
    private val _minSupported = MutableStateFlow(prefs.getLong(KEY_MIN, 0L))
    /** Sunucunun son bildirdiği desteklenen en eski sürüm (zorunlu güncelleme penceresi). */
    val minSupported: StateFlow<Long> = _minSupported.asStateFlow()

    fun setInactivity(on: Boolean) { _inactivity.value = on; prefs.edit().putBoolean(KEY_INACTIVITY, on).apply() }
    fun setUpdates(on: Boolean) { _updates.value = on; prefs.edit().putBoolean(KEY_UPDATES, on).apply() }

    val lastOpenedAt: Long get() = prefs.getLong(KEY_OPENED, 0L)
    val remindersSent: Int get() = prefs.getInt(KEY_SENT, 0)
    val opens: Int get() = prefs.getInt(KEY_OPENS, 0)

    /** Uygulama açıldı: hatırlatma sayacı sıfırlanır. */
    fun onAppOpened() {
        prefs.edit().putLong(KEY_OPENED, clock()).putInt(KEY_SENT, 0).putInt(KEY_OPENS, opens + 1).apply()
    }

    fun markReminderSent(stage: Int) { prefs.edit().putInt(KEY_SENT, stage).apply() }

    val latestVersion: Long get() = prefs.getLong(KEY_LATEST, 0L)
    val message: String? get() = prefs.getString(KEY_MESSAGE, null)
    val lastNotifiedVersion: Long get() = prefs.getLong(KEY_NOTIFIED, 0L)

    fun saveVersion(latest: Long, minSupported: Long, message: String?) {
        prefs.edit().putLong(KEY_LATEST, latest).putLong(KEY_MIN, minSupported).putString(KEY_MESSAGE, message).apply()
        _minSupported.value = minSupported
    }

    fun markUpdateNotified(version: Long) { prefs.edit().putLong(KEY_NOTIFIED, version).apply() }

    /** Android 13+ bildirim izni öncesi açıklama gösterildi mi (bir kez). */
    var permissionExplained: Boolean
        get() = prefs.getBoolean(KEY_EXPLAINED, false)
        set(v) { prefs.edit().putBoolean(KEY_EXPLAINED, v).apply() }

    private companion object {
        const val KEY_INACTIVITY = "inactivity"
        const val KEY_UPDATES = "updates"
        const val KEY_OPENED = "last_opened"
        const val KEY_SENT = "reminders_sent"
        const val KEY_OPENS = "opens"
        const val KEY_LATEST = "latest"
        const val KEY_MIN = "min_supported"
        const val KEY_MESSAGE = "message"
        const val KEY_NOTIFIED = "notified_version"
        const val KEY_EXPLAINED = "permission_explained"
    }
}
