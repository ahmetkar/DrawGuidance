package com.karapp.drawguidance.ui.screens.canvaslist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.canvasKindIcon
import com.karapp.drawguidance.ui.components.canvasKindLabel
import com.karapp.drawguidance.ui.components.canvasKindTint
import com.karapp.drawguidance.ui.components.timeSpentText
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme

/** v40 Tuvallerim özeti: toplam, tür başına sayılar, son 7 günde düzenlenen ve toplam süre; v42 resim / not / çizgi roman grupları. */
data class CanvasStats(
    val total: Int,
    val perKind: List<Pair<CanvasKind, Int>>,
    val editedThisWeek: Int,
    val totalTimeMs: Long,
    /** v42 Resim (resim, basit resim, çocuklar için), Not (not, basit not) ve Çizgi roman sayıları. */
    val drawings: Int = 0,
    val notes: Int = 0,
    val comics: Int = 0,
) {
    companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

        fun of(canvases: List<CanvasUi>, times: Map<String, Long>, now: Long): CanvasStats {
            val counts = canvases.groupingBy { it.kind }.eachCount()
            return CanvasStats(
                total = canvases.size,
                perKind = CanvasKind.entries.mapNotNull { k -> counts[k]?.let { k to it } },
                editedThisWeek = canvases.count { now - it.updatedAt in 0..WEEK_MS },
                totalTimeMs = canvases.sumOf { times[it.localId] ?: 0L },
                drawings = canvases.count { it.kind == CanvasKind.ART || it.kind == CanvasKind.SIMPLE_ART || it.kind == CanvasKind.KIDS },
                notes = canvases.count { it.kind == CanvasKind.NOTES || it.kind == CanvasKind.SIMPLE_NOTES },
                comics = canvases.count { it.kind == CanvasKind.COMIC },
            )
        }
    }
}

/**
 * v40 Tuvallerim'in en üstündeki küçük özet kutuları (eskiden Ana sayfadaki sayılar): kaydırılabilir tek satır,
 * her kutuda simge, büyük değer ve kısa ad.
 */
@Composable
fun CanvasStatsRow(stats: CanvasStats, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp).testTag("canvas_stats"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatTile(Icons.Filled.Collections, colors.accent, stats.total.toString(), stringResource(R.string.v40_stats_total), "stat_total")
        // v42 türler üç grupta (Ana sayfada sayı yok): resim, not, çizgi roman
        StatTile(canvasKindIcon(CanvasKind.ART), canvasKindTint(CanvasKind.ART), stats.drawings.toString(), stringResource(R.string.v42_stats_drawings), "stat_drawings")
        StatTile(canvasKindIcon(CanvasKind.NOTES), canvasKindTint(CanvasKind.NOTES), stats.notes.toString(), stringResource(R.string.v42_stats_notes), "stat_notes")
        StatTile(canvasKindIcon(CanvasKind.COMIC), canvasKindTint(CanvasKind.COMIC), stats.comics.toString(), stringResource(R.string.v42_stats_comics), "stat_comics")
        StatTile(Icons.Filled.Update, Color(0xFF0EA5E9), stats.editedThisWeek.toString(), stringResource(R.string.v40_stats_week), "stat_week")
        if (stats.totalTimeMs > 0) {
            StatTile(Icons.Filled.Schedule, Color(0xFF10B981), timeSpentText(stats.totalTimeMs), stringResource(R.string.v40_stats_time), "stat_time")
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, tint: Color, value: String, label: String, tag: String) {
    val colors = NeuTheme.colors
    val cd = stringResource(R.string.v40_stats_cd, label, value)
    NeuSurface(
        Modifier.widthIn(min = 92.dp, max = 150.dp).clearAndSetSemantics { contentDescription = cd; testTag = tag },
        shape = RoundedCornerShape(18.dp), elevation = 4.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Box(Modifier.size(26.dp).background(tint.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.content, maxLines = 1, translate = false)
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
