package com.karapp.drawguidance.ui.components

import com.karapp.drawguidance.ui.theme.neuOutline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.theme.NeuTheme

/** Galeri küçük resminin köşe yuvarlaklığı. */
val GalleryShape = RoundedCornerShape(16.dp)

/**
 * Sade galeri kutusu (Tuvallerim ve Resimlerim): kutu yok; yalnızca yumuşak gölgeli, yuvarlak köşeli küçük resim
 * ve altında ad ile tek satır bilgi. Seçiliyken küçük resmin çevresi vurgulanır.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryTile(
    title: String,
    subtitle: String,
    ratio: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    thumbnail: @Composable BoxScope.() -> Unit,
) {
    val colors = NeuTheme.colors
    Column(
        modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio.coerceIn(0.62f, 1.7f))
                .clip(GalleryShape)
                .background(Color.White)
                .then(if (selected) Modifier.border(3.dp, colors.accent, GalleryShape) else Modifier.neuOutline(colors, GalleryShape)),
            content = thumbnail,
        )
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (selected) colors.accent else colors.content,
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Galeride tarih bölümünün başlığı. */
@Composable
fun GallerySectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = NeuTheme.colors.contentMuted,
        modifier = modifier.padding(start = 4.dp, top = 6.dp),
    )
}

/** Tarihe göre galeri bölümü: bugün, dün, bu hafta, bu ay, daha eski (dize kaynağı). */
fun gallerySection(time: Long, now: Long = System.currentTimeMillis()): Int {
    val day = 24L * 60 * 60 * 1000
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = now
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    val today = cal.timeInMillis
    return when {
        time >= today -> R.string.images_section_today
        time >= today - day -> R.string.images_section_yesterday
        time >= today - 6 * day -> R.string.images_section_week
        time >= today - 30 * day -> R.string.images_section_month
        else -> R.string.images_section_older
    }
}
