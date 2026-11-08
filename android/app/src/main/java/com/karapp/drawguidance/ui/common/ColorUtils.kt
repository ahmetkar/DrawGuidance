package com.karapp.drawguidance.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** "#RRGGBB" / "#AARRGGBB" hex string'i Compose Color'a çevirir. Geçersizse siyah döner. */
fun hexToColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color.Black
}

fun Color.toHex(): String = String.format("#%06X", 0xFFFFFF and this.toArgb())

/** Yeni Tuval / Tuval Ayarları modallarında sunulan hazır kağıt renkleri. */
val CanvasBackgroundSwatches = listOf(
    "#FFFFFF", "#F6F1E7", "#E8EEF6", "#1E1E24", "#FDE7C8", "#D9F0E3",
)
