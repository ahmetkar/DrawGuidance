package com.karapp.drawguidance.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Kullanıcının kaydettiği renkler (en yeni başta). Cihazda saklanır. */
class ColorPaletteStore(context: Context) {

    private val prefs = context.getSharedPreferences("color_palette", Context.MODE_PRIVATE)
    private val _colors = MutableStateFlow(load())
    val colors: StateFlow<List<String>> = _colors.asStateFlow()

    fun add(colorHex: String) {
        val hex = colorHex.uppercase()
        val updated = (listOf(hex) + _colors.value.filterNot { it.equals(hex, ignoreCase = true) }).take(MAX_COLORS)
        persist(updated)
    }

    fun remove(colorHex: String) {
        persist(_colors.value.filterNot { it.equals(colorHex, ignoreCase = true) })
    }

    private fun load(): List<String> =
        prefs.getString(KEY, null)?.split(',')?.filter { HEX.matches(it) } ?: emptyList()

    private fun persist(colors: List<String>) {
        prefs.edit().putString(KEY, colors.joinToString(",")).apply()
        _colors.value = colors
    }

    companion object {
        const val MAX_COLORS = 24
        private const val KEY = "saved_colors"
        private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
