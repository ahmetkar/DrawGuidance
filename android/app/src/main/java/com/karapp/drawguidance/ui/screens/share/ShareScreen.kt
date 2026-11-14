package com.karapp.drawguidance.ui.screens.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.share.PeerState
import com.karapp.drawguidance.data.share.ShareManager
import com.karapp.drawguidance.data.share.SharePeer
import com.karapp.drawguidance.data.share.ShareSettings
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.components.NeuTopBar
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasThumbnail
import com.karapp.drawguidance.ui.screens.settings.SettingSwitchRow
import com.karapp.drawguidance.ui.screens.settings.SettingsCard
import com.karapp.drawguidance.ui.theme.NeuTheme
import org.koin.compose.koinInject

private enum class Pick { CANVAS, IMAGE, LIVE }

/**
 * v51 Cihazlar arası paylaşım sayfası (Tuvallerim'in sağ üst köşesinden ve Ayarlar → Bağlantı'dan açılır):
 * bu cihazın görünen adı, yakındaki cihazlar (Nearby: Wi-Fi Direct/Bluetooth; aynı Wi-Fi: iPhone/iPad dahil),
 * bağlı cihaza tuval/resim gönderme ve canlı yayın, sürmekte olan yayın ve paylaşım ayarları. Sayfa açıkken cihaz
 * görünürdür ve arar; kapanınca arama durur, kurulu bağlantılar sürer.
 */
@Composable
fun ShareScreen(
    onBack: () -> Unit,
    onOpenCanvas: (String) -> Unit,
    onOpenLive: (String) -> Unit,
    share: ShareManager = koinInject(),
    settings: ShareSettings = koinInject(),
) {
    val colors = NeuTheme.colors
    // Sayfa açıkken (görünürlük ayarı kapalı olsa da) görünür ol ve ara
    DisposableEffect(Unit) {
        share.pageOpened()
        onDispose { share.pageClosed() }
    }
    val peers by share.peers.collectAsState()
    val active by share.active.collectAsState()
    val status by share.status.collectAsState()
    val liveOut by share.liveOut.collectAsState()
    val liveIn by share.liveIn.collectAsState()
    val error by share.errors.collectAsState()
    val name by settings.deviceName.collectAsState()
    var renaming by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<Pair<Pick, SharePeer>?>(null) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            NeuTopBar(
                title = stringResource(R.string.v51_share_title),
                navigation = { NeuIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack) },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp)
                .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).testTag("share_screen"),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Bu cihaz
            SettingsCard(
                Icons.Filled.PhoneAndroid, stringResource(R.string.v51_share_this_device),
                subtitle = stringResource(if (active) R.string.v51_share_visible else R.string.v51_share_off),
                trailing = { NeuIconButton(Icons.Filled.Edit, stringResource(R.string.v51_share_rename), { renaming = true }, size = 38.dp) },
            ) {
                Text(name, translate = false, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("share_device_name"))
                Text(stringResource(R.string.v51_share_how), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                error?.let { Text(stringResource(R.string.v51_share_error, it), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted) }
            }

            // Sürmekte olan aktarım
            status?.let { s ->
                NeuSurface(Modifier.fillMaxWidth(), elevation = 5.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (s.sending) stringResource(R.string.v51_share_sending, s.title, s.peerName) else stringResource(R.string.v51_share_receiving, s.peerName),
                            translate = false, style = MaterialTheme.typography.bodyMedium,
                        )
                        val p = s.progress
                        if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = colors.accent)
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.accent)
                    }
                }
            }

            // Canlı yayın
            liveOut?.let { out ->
                LiveCard(
                    icon = Icons.Filled.CastConnected,
                    text = stringResource(R.string.v51_share_live_out, canvasName(out.canvasId), out.peerName),
                    primary = stringResource(R.string.v51_share_live_open) to { onOpenCanvas(out.canvasId) },
                    secondary = stringResource(R.string.v51_share_live_stop) to { share.stopLive() },
                )
            }
            liveIn?.let { inn ->
                LiveCard(
                    icon = Icons.Filled.Cast,
                    text = stringResource(R.string.v51_share_live_in, inn.peerName, canvasName(inn.canvasId)) +
                        if (inn.ended) "\n" + stringResource(R.string.v51_share_live_ended) else "",
                    primary = stringResource(R.string.v51_share_live_open) to { onOpenLive(inn.canvasId) },
                    secondary = stringResource(R.string.v51_share_live_leave) to { share.leaveLive() },
                )
            }

            // Yakındaki cihazlar
            SettingsCard(Icons.Filled.Share, stringResource(R.string.v51_share_nearby_title), Modifier.testTag("share_peers"), trailing = {
                if (active) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.accent)
            }) {
                if (peers.isEmpty()) Text(stringResource(R.string.v51_share_nearby_empty), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                peers.sortedWith(compareBy({ it.state != PeerState.CONNECTED }, { it.name })).forEach { peer ->
                    PeerRow(
                        peer,
                        onDisconnect = { share.disconnect(peer) },
                        onPick = { pick = it to peer },
                    )
                }
            }

            // Ayarlar
            ShareSettingsCard(settings)
        }
    }

    if (renaming) RenameDialog(name, onDismiss = { renaming = false }) {
        settings.setDeviceName(it)
        renaming = false
        share.restartIfActive()
    }
    pick?.let { (kind, peer) ->
        when (kind) {
            Pick.CANVAS, Pick.LIVE -> CanvasPickDialog(
                title = stringResource(if (kind == Pick.LIVE) R.string.v51_share_pick_live else R.string.v51_share_pick_canvas),
                onDismiss = { pick = null },
            ) { canvas ->
                pick = null
                if (kind == Pick.LIVE) share.startLive(peer.key, canvas) else share.sendCanvas(peer.key, canvas)
            }
            Pick.IMAGE -> ImagePickDialog(onDismiss = { pick = null }) { image ->
                pick = null
                share.sendImage(peer.key, image)
            }
        }
    }
}

@Composable
private fun canvasName(id: String, repo: CanvasRepository = koinInject()): String {
    val list by repo.observeCanvases().collectAsState(initial = emptyList())
    return list.firstOrNull { it.localId == id }?.name ?: ""
}

@Composable
private fun LiveCard(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, primary: Pair<String, () -> Unit>, secondary: Pair<String, () -> Unit>) {
    val colors = NeuTheme.colors
    NeuSurface(Modifier.fillMaxWidth().testTag("share_live_card"), elevation = 6.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colors.danger))
                Icon(icon, null, tint = colors.accent)
                Text(text, translate = false, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeuButton(primary.first, primary.second, Modifier.weight(1f), primary = true)
                NeuButton(secondary.first, secondary.second, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PeerRow(peer: SharePeer, onDisconnect: () -> Unit, onPick: (Pick) -> Unit) {
    val colors = NeuTheme.colors
    val ios = peer.platform == "ios"
    NeuSurface(Modifier.fillMaxWidth().testTag("share_peer"), elevation = 4.dp, selected = peer.state == PeerState.CONNECTED) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(colors.accent.copy(alpha = if (colors.isDark) 0.22f else 0.13f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (ios) Icons.Filled.PhoneIphone else Icons.Filled.PhoneAndroid, null, tint = colors.accent) }
                Column(Modifier.weight(1f)) {
                    Text(peer.name, translate = false, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val route = stringResource(R.string.v51_share_route_lan)
                    val state = when (peer.state) {
                        PeerState.CONNECTED -> stringResource(R.string.v51_share_connected)
                        PeerState.CONNECTING -> stringResource(R.string.v51_share_connecting)
                        PeerState.FOUND -> stringResource(if (ios) R.string.v51_share_platform_ios else R.string.v51_share_platform_android)
                    }
                    Text("$state · $route", style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Bağlantı istek üzerine kendiliğinden kurulur: düğmeler her zaman kullanılabilir
                when (peer.state) {
                    PeerState.FOUND -> Unit
                    PeerState.CONNECTING -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.accent)
                    PeerState.CONNECTED -> NeuIconButton(Icons.Filled.LinkOff, stringResource(R.string.v51_share_disconnect), onDisconnect, size = 38.dp)
                }
            }
            run {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuButton(stringResource(R.string.v51_share_send_canvas), { onPick(Pick.CANVAS) }, Modifier.weight(1f).testTag("share_send_canvas"), icon = Icons.AutoMirrored.Filled.Send)
                    NeuButton(stringResource(R.string.v51_share_send_image), { onPick(Pick.IMAGE) }, Modifier.weight(1f).testTag("share_send_image"), icon = Icons.Filled.Image)
                }
                NeuButton(stringResource(R.string.v51_share_start_live), { onPick(Pick.LIVE) }, Modifier.fillMaxWidth().testTag("share_start_live"), primary = true, icon = Icons.Filled.Cast)
            }
        }
    }
}

/** Paylaşım ayarları kartı (paylaşım sayfasında ve Ayarlar → Bağlantı'da). */
@Composable
fun ShareSettingsCard(settings: ShareSettings = koinInject(), onOpenShare: (() -> Unit)? = null) {
    val ask by settings.askBeforeReceive.collectAsState()
    val folder by settings.receivedFolder.collectAsState()
    val smooth by settings.liveSmooth.collectAsState()
    val visible by settings.discoverable.collectAsState()
    SettingsCard(
        if (onOpenShare != null) Icons.Filled.Share else Icons.Filled.Settings,
        stringResource(if (onOpenShare != null) R.string.v51_share_title else R.string.v51_share_settings_title),
        Modifier.testTag("share_settings_card"),
        subtitle = if (onOpenShare != null) stringResource(R.string.v51_share_settings_hint) else null,
    ) {
        if (onOpenShare != null) NeuButton(stringResource(R.string.v51_share_open_page), onOpenShare, Modifier.fillMaxWidth().testTag("settings_open_share"), primary = true, icon = Icons.Filled.Share)
        SettingSwitchRow(stringResource(R.string.v51_share_discoverable), stringResource(R.string.v51_share_discoverable_hint), visible, settings::setDiscoverable, divider = onOpenShare != null)
        SettingSwitchRow(stringResource(R.string.v51_share_ask), stringResource(R.string.v51_share_ask_hint), ask, settings::setAskBeforeReceive)
        SettingSwitchRow(stringResource(R.string.v51_share_folder), stringResource(R.string.v51_share_folder_hint), folder, settings::setReceivedFolder)
        SettingSwitchRow(stringResource(R.string.v51_share_smooth), stringResource(R.string.v51_share_smooth_hint), smooth, settings::setLiveSmooth)
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeuTheme.colors.background,
        title = { Text(stringResource(R.string.v51_share_device_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuTextField(text, { text = it.take(40) }, stringResource(R.string.v51_share_device_name), Modifier.fillMaxWidth().testTag("share_name_field"))
                Text(stringResource(R.string.v51_share_device_name_hint), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.v51_share_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.v51_share_close)) } },
    )
}

@Composable
private fun CanvasPickDialog(title: String, onDismiss: () -> Unit, repo: CanvasRepository = koinInject(), onPick: (CanvasUi) -> Unit) {
    val list by repo.observeCanvases().collectAsState(initial = null)
    val colors = NeuTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(title) },
        text = {
            val items = list?.sortedByDescending { it.updatedAt }
            if (items != null && items.isEmpty()) Text(stringResource(R.string.v51_share_no_canvases), color = colors.contentMuted)
            else LazyColumn(Modifier.heightIn(max = 420.dp).testTag("share_canvas_pick"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items.orEmpty(), key = { it.localId }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(c) }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Color.White)) {
                            CanvasThumbnail(c.width, c.height, c.pagePaperHex, c.strokes, Modifier.fillMaxSize(), c.pageBackground)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(c.name, translate = false, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(com.karapp.drawguidance.ui.screens.canvaslist.components.canvasSubtitle(c), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.v51_share_close)) } },
    )
}

@Composable
private fun ImagePickDialog(onDismiss: () -> Unit, repo: ImagesRepository = koinInject(), onPick: (com.karapp.drawguidance.data.images.SavedImageEntity) -> Unit) {
    val list by repo.observeImages().collectAsState(initial = null)
    val colors = NeuTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(stringResource(R.string.v51_share_pick_image)) },
        text = {
            val items = list?.filter { it.isImage }
            if (items != null && items.isEmpty()) Text(stringResource(R.string.v51_share_no_images), color = colors.contentMuted)
            else LazyColumn(Modifier.heightIn(max = 420.dp).testTag("share_image_pick"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items.orEmpty(), key = { it.id }) { img ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(img) }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        coil.compose.AsyncImage(
                            model = img.uri, contentDescription = null,
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(img.title, translate = false, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${img.typeLabel} · ${img.width}×${img.height}", style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.v51_share_close)) } },
    )
}
