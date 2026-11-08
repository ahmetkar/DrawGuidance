package com.karapp.drawguidance.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.inbox.InboxRepository
import com.karapp.drawguidance.data.local.SyncEventDao
import com.karapp.drawguidance.data.local.SyncEventEntity
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File

/**
 * v43 Ana sayfadaki duyurular: sunucudan gelen (verisi ve küçük resmi inmiş) duyurular en üstte kart olarak; ilk
 * gösterildiğinde olay tablosunda "ana sayfada gösterildi" işaretlenir, kapatılınca bir daha gösterilmez.
 */
@Composable
fun HomeAnnouncements(dao: SyncEventDao = koinInject(), inbox: InboxRepository = koinInject()) {
    val list by dao.observeAnnouncements().collectAsState(initial = emptyList())
    if (list.isEmpty()) return
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().testTag("home_announcements"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        list.forEach { a ->
            LaunchedEffect(a.id) { if (!a.handled) inbox.announcementShown(a.id) }
            AnnouncementCard(a, onClose = { scope.launch { inbox.dismiss(a.id) } })
        }
    }
}

@Composable
private fun AnnouncementCard(a: SyncEventEntity, onClose: () -> Unit) {
    val colors = NeuTheme.colors
    NeuSurface(modifier = Modifier.fillMaxWidth().testTag("announcement_${a.refId}"), elevation = 6.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val image = a.localPath?.let(::File)?.takeIf { it.isFile }
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                if (image != null) {
                    coil.compose.AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp))
                } else {
                    Icon(Icons.Filled.Campaign, null, tint = colors.accent, modifier = Modifier.size(36.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(a.title.orEmpty(), translate = false, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(a.body.orEmpty(), translate = false, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp).testTag("announcement_close_${a.refId}")) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.v43_announcement_close), tint = colors.contentMuted)
            }
        }
    }
}
