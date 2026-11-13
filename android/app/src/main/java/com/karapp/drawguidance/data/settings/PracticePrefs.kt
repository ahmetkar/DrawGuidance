package com.karapp.drawguidance.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v50 yetişkin çizim alıştırmalarının en iyi puanları (alıştırma kimliği → 0–100). Çocuk görevinin ilerlemesinden
 * ayrıdır (o tablo seviye sırasıyla sunucuya eşitlenir); bu puanlar yalnız cihazda tutulur.
 */
class PracticePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("adult_practice", Context.MODE_PRIVATE)

    private val _best = MutableStateFlow(parse(prefs.getString(KEY_BEST, null)))
    val best: StateFlow<Map<String, Int>> = _best.asStateFlow()

    /** Puan yazılır (en iyisi kalır); en iyi puan döner. */
    fun record(id: String, score: Int): Int {
        val now = _best.value
        val best = maxOf(now[id] ?: 0, score.coerceIn(0, 100))
        val next = now + (id to best)
        _best.value = next
        prefs.edit().putString(KEY_BEST, next.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
        return best
    }

    companion object {
        private const val KEY_BEST = "best"

        fun parse(raw: String?): Map<String, Int> = raw.orEmpty().split(',').mapNotNull { part ->
            val i = part.lastIndexOf(':')
            if (i <= 0) null else part.substring(i + 1).toIntOrNull()?.let { part.substring(0, i) to it }
        }.toMap()
    }
}
