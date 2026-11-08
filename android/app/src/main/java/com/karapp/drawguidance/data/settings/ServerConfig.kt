package com.karapp.drawguidance.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Sunucu adresi. v44: kullanıcının değiştirebileceği sunucu ayarı ve yerel ağ sunucusu yok; yayın derlemesinde adres
 * her zaman derlemenin adresidir (`https://drawapi.ahmetkar.com/`), kayıtlı hiçbir değer okunmaz.
 *
 * Yalnız hata ayıklama derlemesinde ([allowOverride]) testler ve geliştirici `server_url` intent parametresiyle adresi
 * değiştirebilir; değer bellekte tutulur (diske yazılmaz).
 */
class ServerConfig(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val defaultUrl: String,
    private val allowOverride: Boolean = false,
) {
    private val _baseUrl = MutableStateFlow(defaultUrl)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    val currentHttpUrl: HttpUrl
        get() = _baseUrl.value.toHttpUrlOrNull() ?: defaultUrl.toHttpUrlOrNull()!!

    /** Hata ayıklama derlemesinde adresi değiştirir; yayında hiçbir şey yapmaz (false). */
    fun update(raw: String): Boolean {
        if (!allowOverride) return false
        val normalized = normalize(raw) ?: return false
        _baseUrl.value = normalized
        return true
    }

    companion object {
        /** Yayın sunucusu. */
        const val REMOTE_URL = "https://drawapi.ahmetkar.com/"
        /** Hata ayıklama sunucusunun kapısı (adreste kapı yoksa). */
        const val DEFAULT_PORT = 4747

        /** "10.0.2.2", "10.0.2.2:4747", "http://10.0.2.2:4747/api" → "http://10.0.2.2:4747/". */
        fun normalize(raw: String): String? {
            var text = raw.trim()
            if (text.isEmpty()) return null
            if (!text.startsWith("http://", ignoreCase = true) && !text.startsWith("https://", ignoreCase = true)) text = "http://$text"
            val hasExplicitPort = Regex("^https?://[^/]+:\\d+", RegexOption.IGNORE_CASE).containsMatchIn(text)
            val url = text.toHttpUrlOrNull() ?: return null
            if (url.host.isBlank()) return null
            val builder = url.newBuilder().encodedPath("/").query(null).fragment(null)
            if (!hasExplicitPort && url.scheme == "http") builder.port(DEFAULT_PORT)
            return builder.build().toString()
        }
    }
}
