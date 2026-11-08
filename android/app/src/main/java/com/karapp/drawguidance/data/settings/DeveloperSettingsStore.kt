package com.karapp.drawguidance.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Geliştirici modu (Ayarlar'da açılınca uygulama günlükleri görüntülenebilir). */
class DeveloperSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("developer_settings", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    private companion object {
        const val KEY_ENABLED = "developer_mode"
    }
}
