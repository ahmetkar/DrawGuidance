package com.karapp.drawguidance.ui.screens.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.settings.StorageArea
import com.karapp.drawguidance.data.settings.StorageManager
import com.karapp.drawguidance.data.sync.SyncHold
import com.karapp.drawguidance.data.sync.SyncNetwork
import com.karapp.drawguidance.data.sync.SyncPolicy
import com.karapp.drawguidance.diagnostics.AppErrors
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * v37 Ayarlar → Kaydetme → Depolama: bölüm bölüm kullanılan yer (çubuklarla), önbellek sınırı, önbelleği temizle ve
 * yer aç (veritabanını sıkıştırma). Tuvaller ve dışa aktarılanlar buradan silinmez.
 */
@Composable
fun StorageCard(storage: StorageManager = koinInject()) {
    val context = LocalContext.current
    val colors = NeuTheme.colors
    val scope = rememberCoroutineScope()
    var sizes by remember { mutableStateOf<Map<StorageArea, Long>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val limit by storage.cacheLimitMb.collectAsState()
    val measure: suspend () -> Unit = { sizes = withContext(Dispatchers.IO) { AppErrors.guard("storage.measure", null) { storage.measure() } } }
    LaunchedEffect(Unit) { measure() }
    fun run(action: () -> Long) {
        if (busy) return
        busy = true
        scope.launch {
            val freed = withContext(Dispatchers.IO) { AppErrors.guard("storage.clean", 0L, userVisible = true) { action() } }
            message = context.getString(R.string.storage_freed, Formatter.formatShortFileSize(context, freed))
            measure()
            busy = false
        }
    }
    SettingsCard(
        Icons.Filled.Storage, stringResource(R.string.storage_title), Modifier.testTag("storage_card"),
        subtitle = stringResource(R.string.storage_hint),
    ) {
        val total = sizes?.values?.sum()
        Text(
            if (total == null) stringResource(R.string.storage_measuring)
            else stringResource(R.string.storage_total, Formatter.formatShortFileSize(context, total)),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("storage_total"),
        )
        StorageArea.entries.forEach { area ->
            val bytes = sizes?.get(area) ?: 0L
            UsageBar(
                label = stringResource(
                    when (area) {
                        StorageArea.DRAWINGS -> R.string.storage_drawings
                        StorageArea.CATALOG -> R.string.storage_catalog
                        StorageArea.EXPORTS -> R.string.storage_exports
                        StorageArea.CACHE -> R.string.storage_cache
                    },
                ),
                value = if (sizes == null) "…" else Formatter.formatShortFileSize(context, bytes),
                fraction = if (total == null || total == 0L) 0f else bytes.toFloat() / total,
                modifier = Modifier.testTag("storage_${area.name.lowercase()}"),
            )
        }
        LimitChips(stringResource(R.string.storage_limit), StorageManager.LIMITS, limit, "storage_limit") { mb ->
            storage.setCacheLimit(mb)
            run(storage::trimCache)
        }
        Text(stringResource(R.string.storage_limit_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        // Düğmeler alt alta: uzun dillerde adları kesilmez
        NeuButton(
            stringResource(R.string.storage_clear_cache), { run(storage::clearCache) }, Modifier.fillMaxWidth().testTag("storage_clear_cache"),
            enabled = !busy, icon = Icons.Filled.CleaningServices,
        )
        NeuButton(
            stringResource(R.string.storage_compact), { run(storage::compact) }, Modifier.fillMaxWidth().testTag("storage_compact"),
            enabled = !busy, icon = Icons.Filled.Compress,
        )
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.accent, modifier = Modifier.testTag("storage_message")) }
    }
}

/**
 * v39 Ayarlar → Bağlantı → Eşitleme: ağ (Wi-Fi ve mobil veri / yalnız Wi-Fi), günlük indirme/yükleme sınırları,
 * bugünkü trafik ve son eşitleme. v43 eşitleme her zaman kendiliğinden: "Otomatik eşitleme" anahtarı ve "Şimdi eşitle"
 * düğmesi kaldırıldı. Sınıra ulaşılınca gelen içerik yarım indirilmez (yarım kalan silinir).
 */
@Composable
fun SyncControlCard(
    policy: SyncPolicy = koinInject(),
    lastSync: Long = 0L,
) {
    val context = LocalContext.current
    val colors = NeuTheme.colors
    val settings by policy.settings.collectAsState()
    val usage by policy.usage.collectAsState()
    val hold by policy.hold.collectAsState()
    LaunchedEffect(Unit) { policy.flush() }
    SettingsCard(
        Icons.Filled.CloudSync, stringResource(R.string.sync_merged_title), Modifier.testTag("sync_control_card"),
    ) {
        Text(stringResource(R.string.v43_sync_always), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, modifier = Modifier.testTag("sync_always_hint"))
        // Bekleme nedeni yalnız varsa (bağlantı, ağ ya da günlük sınır)
        hold?.let { h ->
            Text(
                stringResource(
                    when (h) {
                        SyncHold.OFFLINE -> R.string.sync_state_offline
                        SyncHold.METERED -> R.string.sync_state_metered
                        SyncHold.DOWNLOAD_LIMIT -> R.string.sync_state_down_limit
                        SyncHold.UPLOAD_LIMIT -> R.string.sync_state_up_limit
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (h == SyncHold.OFFLINE || h == SyncHold.METERED) colors.contentMuted else colors.danger,
                modifier = Modifier.testTag("sync_hold"),
            )
        }
        Text(stringResource(R.string.sync_network), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(SyncNetwork.ANY to R.string.sync_network_any, SyncNetwork.WIFI_ONLY to R.string.sync_network_wifi).forEach { (n, label) ->
                NeuChip(stringResource(label), settings.network == n, onClick = { policy.setNetwork(n) }, modifier = Modifier.testTag("sync_network_${n.name.lowercase()}"))
            }
        }
        LimitChips(stringResource(R.string.sync_down_limit), SyncPolicy.LIMITS, settings.downloadLimitMb, "sync_down", policy::setDownloadLimit)
        LimitChips(stringResource(R.string.sync_up_limit), SyncPolicy.LIMITS, settings.uploadLimitMb, "sync_up", policy::setUploadLimit)
        Text(
            stringResource(R.string.sync_usage_today, Formatter.formatShortFileSize(context, usage.downloaded), Formatter.formatShortFileSize(context, usage.uploaded)),
            style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, modifier = Modifier.testTag("sync_usage"),
        )
        Text(
            stringResource(
                R.string.sync_last,
                if (lastSync > 0) java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(lastSync))
                else stringResource(R.string.sync_never),
            ),
            style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, modifier = Modifier.testTag("sync_last"),
        )
    }
}

/** Bölümün adı, boyutu ve toplamdaki payını gösteren ince çubuk. */
@Composable
private fun UsageBar(label: String, value: String, fraction: Float, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.darkShadow.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.accent))
        }
    }
}

/** Sınır seçenekleri (MB; 0 sınırsız); etiketleri `<tag>_<mb>`. */
@Composable
private fun LimitChips(title: String, options: List<Int>, selected: Int, tag: String, onPick: (Int) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = NeuTheme.colors.contentMuted)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { mb ->
            val label = when {
                mb == 0 -> stringResource(R.string.limit_unlimited)
                mb >= 1000 && mb % 1000 == 0 -> stringResource(R.string.limit_gb, mb / 1000)
                else -> stringResource(R.string.limit_mb, mb)
            }
            NeuChip(label, selected == mb, onClick = { onPick(mb) }, modifier = Modifier.testTag("${tag}_$mb"))
        }
    }
}
