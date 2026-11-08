package com.karapp.drawguidance.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v30 son açılan tuvaller (Ana sayfa: "Kaldığın yerden devam et"): tuvalin en son açıldığı ve kapatıldığı (bırakıldığı)
 * zaman. En çok [MAX] kayıt; silinen tuvaller Ana sayfada süzülür.
 */
class RecentCanvasStore(context: Context) {
    data class Entry(val canvasId: String, val openedAt: Long, val closedAt: Long)

    private val prefs = context.getSharedPreferences("recent_canvases", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private fun load(): List<Entry> = prefs.getString(KEY, "").orEmpty().lines().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 3) null else runCatching { Entry(parts[0], parts[1].toLong(), parts[2].toLong()) }.getOrNull()
    }

    @Synchronized
    private fun update(id: String, block: (Entry?) -> Entry) {
        val current = _entries.value
        val next = (listOf(block(current.firstOrNull { it.canvasId == id })) + current.filter { it.canvasId != id }).take(MAX)
        _entries.value = next
        prefs.edit().putString(KEY, next.joinToString("\n") { "${it.canvasId}|${it.openedAt}|${it.closedAt}" }).apply()
    }

    /** Düzenleyicide açıldı. */
    fun opened(id: String, now: Long = System.currentTimeMillis()) = update(id) { Entry(id, now, it?.closedAt ?: 0L) }

    /** Düzenleyiciden çıkıldı (bırakılıp kapatıldı). */
    fun closed(id: String, now: Long = System.currentTimeMillis()) = update(id) { Entry(id, it?.openedAt ?: now, now) }

    @Synchronized
    fun remove(id: String) {
        val next = _entries.value.filter { it.canvasId != id }
        _entries.value = next
        prefs.edit().putString(KEY, next.joinToString("\n") { "${it.canvasId}|${it.openedAt}|${it.closedAt}" }).apply()
    }

    private companion object {
        const val KEY = "entries"
        const val MAX = 20
    }
}
