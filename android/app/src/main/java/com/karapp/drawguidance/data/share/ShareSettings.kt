package com.karapp.drawguidance.data.share

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * v51 paylaşım ayarları (Ayarlar → Bağlantı → Cihazlar arası paylaşım ve paylaşım sayfası):
 * diğer cihazlarda görünen ad, görünürlük (uygulama açıkken kendiliğinden keşif), gelenleri sormadan alma, alınanları "Paylaşılanlar" klasörüne koyma ve
 * yayının güncelleme sıklığı. [instanceId] bu kurulumun kalıcı kimliğidir (kendi cihazını listede görmemek için).
 */
class ShareSettings(context: Context) {
    private val prefs = context.getSharedPreferences("share", Context.MODE_PRIVATE)

    val instanceId: String = prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_ID, it).apply() }

    private val _name = MutableStateFlow(prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: defaultName())
    val deviceName: StateFlow<String> = _name.asStateFlow()

    private val _ask = MutableStateFlow(prefs.getBoolean(KEY_ASK, true))
    /** Gelen tuval/resim/yayın teklifleri için onay sorulsun (kapalıysa bağlı cihazdan gelenler doğrudan alınır). */
    val askBeforeReceive: StateFlow<Boolean> = _ask.asStateFlow()

    private val _folder = MutableStateFlow(prefs.getBoolean(KEY_FOLDER, true))
    /** Alınan tuvaller Tuvallerim'deki "Paylaşılanlar" klasörüne konsun. */
    val receivedFolder: StateFlow<Boolean> = _folder.asStateFlow()

    private val _visible = MutableStateFlow(prefs.getBoolean(KEY_VISIBLE, true))
    /** Uygulama açıkken aynı Wi-Fi'deki diğer cihazlarda görünür ol ve onları kendiliğinden bul. */
    val discoverable: StateFlow<Boolean> = _visible.asStateFlow()

    private val _smooth = MutableStateFlow(prefs.getBoolean(KEY_SMOOTH, true))
    /** Yayın sık güncellensin (kapalıysa daha seyrek; yavaş Bluetooth bağlantısında önerilir). */
    val liveSmooth: StateFlow<Boolean> = _smooth.asStateFlow()

    fun setDeviceName(name: String) {
        val n = name.trim().take(40)
        _name.value = n.ifBlank { defaultName() }
        prefs.edit().putString(KEY_NAME, n).apply()
    }

    fun setAskBeforeReceive(on: Boolean) { _ask.value = on; prefs.edit().putBoolean(KEY_ASK, on).apply() }
    fun setReceivedFolder(on: Boolean) { _folder.value = on; prefs.edit().putBoolean(KEY_FOLDER, on).apply() }
    fun setDiscoverable(on: Boolean) { _visible.value = on; prefs.edit().putBoolean(KEY_VISIBLE, on).apply() }
    fun setLiveSmooth(on: Boolean) { _smooth.value = on; prefs.edit().putBoolean(KEY_SMOOTH, on).apply() }

    /** "Paylaşılanlar" klasörünün kimliği (silinmişse yeniden oluşturulur). */
    var sharedFolderId: String?
        get() = prefs.getString(KEY_FOLDER_ID, null)
        set(v) { prefs.edit().putString(KEY_FOLDER_ID, v).apply() }

    /** Yayın güncellemeleri arasındaki en kısa süre (ms). */
    val liveIntervalMs: Long get() = if (_smooth.value) 250L else 900L

    private companion object {
        const val KEY_ID = "instance_id"
        const val KEY_NAME = "device_name"
        const val KEY_ASK = "ask_before_receive"
        const val KEY_FOLDER = "received_folder"
        const val KEY_FOLDER_ID = "received_folder_id"
        const val KEY_SMOOTH = "live_smooth"
        const val KEY_VISIBLE = "discoverable"

        fun defaultName(): String {
            val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
            val model = Build.MODEL.orEmpty()
            return (if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model").trim().ifBlank { "Android" }.take(40)
        }
    }
}

/** Başka cihazdan alınan tuvalin bilgisi: gönderen, platformu, zamanı ve canlı yayından mı geldiği. */
@Serializable
data class SharedCanvasInfo(val from: String, val platform: String = "", val at: Long = 0, val live: Boolean = false)

/**
 * v51 alınan tuvallerin kaydı (tuval kimliği → [SharedCanvasInfo]). Tuval listesinde ve tuval kartında "Paylaşılan"
 * rozeti bununla gösterilir. Veritabanı şemasına dokunmamak için ayrı ayar dosyasında JSON olarak tutulur.
 */
class SharedCanvasRegistry(context: Context) {
    private val prefs = context.getSharedPreferences("share_registry", Context.MODE_PRIVATE)
    private val json = ShareWire.json

    private val _all = MutableStateFlow(load())
    val all: StateFlow<Map<String, SharedCanvasInfo>> = _all.asStateFlow()

    private fun load(): Map<String, SharedCanvasInfo> = prefs.all.mapNotNull { (k, v) ->
        (v as? String)?.let { s -> runCatching { json.decodeFromString(SharedCanvasInfo.serializer(), s) }.getOrNull() }?.let { k to it }
    }.toMap()

    fun get(canvasId: String): SharedCanvasInfo? = _all.value[canvasId]

    fun mark(canvasId: String, info: SharedCanvasInfo) {
        prefs.edit().putString(canvasId, json.encodeToString(SharedCanvasInfo.serializer(), info)).apply()
        _all.value = _all.value + (canvasId to info)
    }

    fun forget(canvasId: String) {
        prefs.edit().remove(canvasId).apply()
        _all.value = _all.value - canvasId
    }
}
