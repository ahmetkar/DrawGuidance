package com.karapp.drawguidance.ui.screens.share

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.share.ShareEvent
import com.karapp.drawguidance.data.share.ShareManager
import com.karapp.drawguidance.data.share.ShareWire
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuTopBar
import com.karapp.drawguidance.ui.components.formatFileSize
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasThumbnail
import com.karapp.drawguidance.ui.theme.NeuTheme
import org.koin.compose.koinInject

/**
 * v51 uygulamanın her ekranında paylaşımın pencereleri: bağlantı isteği, gelen tuval/resim/yayın teklifi ve alınan
 * tuval bildirimi. Yayın kabul edilince alıcıda yayın tuvali, yayını başlatanda tuval düzenleyicide açılır.
 */
@Composable
fun ShareHost(onOpenCanvas: (String) -> Unit, onOpenLive: (String) -> Unit, share: ShareManager = koinInject()) {
    val context = LocalContext.current
    val request by share.request.collectAsState()
    val offer by share.offer.collectAsState()
    var received by remember { mutableStateOf<ShareEvent.Received?>(null) }
    val colors = NeuTheme.colors

    LaunchedEffect(Unit) {
        share.events.collect { e ->
            when (e) {
                is ShareEvent.Received -> received = e
                is ShareEvent.OpenLive -> onOpenLive(e.canvasId)
                is ShareEvent.OpenBroadcast -> onOpenCanvas(e.canvasId)
                is ShareEvent.Sent -> toast(context, context.getString(R.string.v51_share_sent, e.title, e.to))
                is ShareEvent.Rejected -> toast(context, context.getString(R.string.v51_share_rejected, e.by))
                is ShareEvent.Failed -> toast(context, context.getString(R.string.v51_share_failed, e.message))
                is ShareEvent.Disconnected -> toast(context, context.getString(R.string.v51_share_disconnected, e.name))
            }
        }
    }

    request?.let { r ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = colors.background,
            modifier = Modifier.testTag("share_request_dialog"),
            icon = { Icon(Icons.Filled.Link, null, tint = colors.accent) },
            title = { Text(stringResource(R.string.v51_share_request_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.v51_share_request_text, r.name), translate = false)
                    r.code?.let { Text(stringResource(R.string.v51_share_request_code, it), fontWeight = FontWeight.SemiBold, translate = false) }
                }
            },
            confirmButton = { TextButton(onClick = { share.answerRequest(true) }, modifier = Modifier.testTag("share_request_accept")) { Text(stringResource(R.string.v51_share_accept)) } },
            dismissButton = { TextButton(onClick = { share.answerRequest(false) }) { Text(stringResource(R.string.v51_share_reject)) } },
        )
    }

    offer?.let { o ->
        val text = when (o.offer.type) {
            ShareWire.TYPE_IMAGE -> stringResource(R.string.v51_share_offer_image, o.peerName, o.offer.title)
            ShareWire.TYPE_LIVE -> stringResource(R.string.v51_share_offer_live, o.peerName, o.offer.title)
            else -> stringResource(R.string.v51_share_offer_canvas, o.peerName, o.offer.title)
        }
        AlertDialog(
            onDismissRequest = {},
            containerColor = colors.background,
            modifier = Modifier.testTag("share_offer_dialog"),
            icon = { Icon(if (o.offer.type == ShareWire.TYPE_LIVE) Icons.Filled.Cast else Icons.Filled.Inbox, null, tint = colors.accent) },
            title = { Text(stringResource(R.string.v51_share_offer_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text, translate = false)
                    if (o.offer.bytes > 0) Text(stringResource(R.string.v51_share_offer_size, formatFileSize(o.offer.bytes)), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                }
            },
            confirmButton = { TextButton(onClick = { share.answerOffer(true) }, modifier = Modifier.testTag("share_offer_accept")) { Text(stringResource(R.string.v51_share_accept)) } },
            dismissButton = { TextButton(onClick = { share.answerOffer(false) }) { Text(stringResource(R.string.v51_share_reject)) } },
        )
    }

    received?.let { r ->
        AlertDialog(
            onDismissRequest = { received = null },
            containerColor = colors.background,
            modifier = Modifier.testTag("share_received_dialog"),
            icon = { Icon(Icons.Filled.Inbox, null, tint = colors.accent) },
            title = { Text(stringResource(R.string.v51_share_offer_title)) },
            text = {
                Text(
                    if (r.type == ShareWire.TYPE_IMAGE) stringResource(R.string.v51_share_received_image, r.title, r.from)
                    else stringResource(R.string.v51_share_received_canvas, r.title, r.from),
                    translate = false,
                )
            },
            confirmButton = {
                val id = r.canvasId
                if (id != null) TextButton(onClick = { received = null; onOpenCanvas(id) }) { Text(stringResource(R.string.v51_share_open_canvas)) }
                else TextButton(onClick = { received = null }) { Text(stringResource(R.string.v51_share_close)) }
            },
            dismissButton = if (r.canvasId != null) { { TextButton(onClick = { received = null }) { Text(stringResource(R.string.v51_share_close)) } } } else null,
        )
    }
}

private fun toast(context: android.content.Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

/**
 * v51 yayını alan tarafın ekranı: yayın yapılan tuval, gönderenin her değişikliğiyle güncellenir (salt görüntüleme).
 * Yayın bitince tuval Tuvallerim'de kalır ve düzenlenebilir.
 */
@Composable
fun LiveCanvasScreen(canvasId: String, onBack: () -> Unit, onEdit: (String) -> Unit, share: ShareManager = koinInject()) {
    val colors = NeuTheme.colors
    val liveIn by share.liveIn.collectAsState()
    val canvas by share.liveCanvas.collectAsState()
    val live = liveIn?.takeIf { it.canvasId == canvasId }
    val ended = live == null || live.ended
    Scaffold(
        containerColor = colors.background,
        topBar = {
            NeuTopBar(
                title = canvas?.name ?: stringResource(R.string.v51_share_live_title),
                subtitle = live?.let { stringResource(R.string.v51_share_live_in, it.peerName, canvas?.name ?: "") },
                navigation = { NeuIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack) },
                actions = {
                    if (ended) NeuIconButton(Icons.Filled.Edit, stringResource(R.string.v51_share_live_edit), { onEdit(canvasId) })
                    else NeuIconButton(Icons.Filled.Close, stringResource(R.string.v51_share_live_leave), { share.leaveLive(); onBack() })
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).testTag("share_live_screen"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (ended) colors.contentMuted else Color(0xFFE53935)))
                Text(
                    stringResource(if (ended) R.string.v51_share_live_ended else R.string.v51_share_live_badge),
                    style = MaterialTheme.typography.labelLarge, color = if (ended) colors.contentMuted else colors.content,
                )
                canvas?.takeIf { it.pageCount > 1 }?.let {
                    Text("· ${it.currentPage + 1}/${it.pageCount}", style = MaterialTheme.typography.labelLarge, color = colors.contentMuted, translate = false)
                }
            }
            val c = canvas
            if (c == null || c.localId != canvasId) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = colors.accent)
                        Text(stringResource(R.string.v51_share_live_waiting), color = colors.contentMuted)
                    }
                }
            } else {
                LiveCanvas(c, Modifier.fillMaxWidth().weight(1f))
            }
            if (ended) NeuButton(stringResource(R.string.v51_share_live_edit), { onEdit(canvasId) }, Modifier.fillMaxWidth(), primary = true, icon = Icons.Filled.Edit)
        }
    }
}

@Composable
private fun LiveCanvas(c: CanvasUi, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        NeuSurface(
            Modifier.fillMaxWidth().aspectRatio(c.width.toFloat() / c.height.coerceAtLeast(1), matchHeightConstraintsFirst = c.height > c.width),
            elevation = 6.dp, shape = RoundedCornerShape(8.dp),
        ) {
            CanvasThumbnail(
                c.width, c.height, c.pagePaperHex,
                remember(c.strokes, c.layers) { com.karapp.drawguidance.data.model.Layers.visible(c.strokes, c.layers) },
                Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), c.pageBackground,
            )
        }
    }
}

/** v51 yayın yapılan tuvalin düzenleyicisinde küçük "Yayında" etiketi (dokununca yayın durdurulabilir). */
@Composable
fun LiveBroadcastBadge(canvasName: String, peerName: String, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    var confirm by remember { mutableStateOf(false) }
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE53935)).clickable { confirm = true }
            .padding(horizontal = 10.dp, vertical = 4.dp).testTag("share_live_badge"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
        Text(stringResource(R.string.v51_share_live_badge) + " · " + peerName, color = Color.White, style = MaterialTheme.typography.labelMedium, translate = false, maxLines = 1)
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        containerColor = colors.background,
        icon = { Icon(Icons.Filled.Cast, null, tint = colors.accent) },
        title = { Text(stringResource(R.string.v51_share_live_title)) },
        text = { Text(stringResource(R.string.v51_share_live_out, canvasName, peerName), translate = false) },
        confirmButton = { TextButton(onClick = { confirm = false; onStop() }) { Text(stringResource(R.string.v51_share_live_stop)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.v51_share_close)) } },
    )
}
