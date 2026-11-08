package com.karapp.drawguidance.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.karapp.drawguidance.R

/** v40 geçirilen sürenin kısa yazımı: "1 dk'dan az", "12 dk", "2 sa", "1 sa 5 dk". */
object TimeSpent {
    /** (saat, dakika); 1 dakikadan azsa null. */
    fun parts(ms: Long): Pair<Int, Int>? {
        val minutes = ms / 60_000L
        if (minutes < 1) return null
        return (minutes / 60).toInt() to (minutes % 60).toInt()
    }
}

@Composable
fun timeSpentText(ms: Long): String {
    val p = TimeSpent.parts(ms) ?: return stringResource(R.string.v40_time_lt_minute)
    val (h, m) = p
    return when {
        h == 0 -> stringResource(R.string.v40_time_minutes, m)
        m == 0 -> stringResource(R.string.v40_time_hours, h)
        else -> stringResource(R.string.v40_time_hours_minutes, h, m)
    }
}
