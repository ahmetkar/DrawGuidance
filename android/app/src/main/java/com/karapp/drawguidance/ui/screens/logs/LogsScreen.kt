package com.karapp.drawguidance.ui.screens.logs

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.diagnostics.Diagnostics
import com.karapp.drawguidance.diagnostics.LogEntry
import com.karapp.drawguidance.diagnostics.LogLevel
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.components.NeuTopBar
import com.karapp.drawguidance.ui.theme.NeuTheme
import org.koin.androidx.compose.koinViewModel

/**
 * Geliştirici modu: uygulamanın çalışırken yazdığı günlükler (hata, uyarı, bilgi, ayrıntı). Önem süzgeci,
 * arama, yenile, kopyala/paylaş ve temizle; son çökme kaydı en üstte.
 */
@Composable
fun LogsScreen(onBack: () -> Unit, viewModel: LogsViewModel = koinViewModel()) {
    val colors = NeuTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val entries by viewModel.visible.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val query by viewModel.query.collectAsState()
    val counts by viewModel.counts.collectAsState()
    val crash by viewModel.crash.collectAsState()
    val shareTitle = stringResource(R.string.logs_title)

    Scaffold(
        containerColor = colors.background,
        topBar = {
            NeuTopBar(
                title = stringResource(R.string.logs_title),
                subtitle = stringResource(R.string.logs_count, entries.size),
                navigation = { NeuIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack) },
                actions = {
                    NeuIconButton(Icons.Filled.Refresh, stringResource(R.string.logs_refresh), viewModel::refresh)
                    NeuIconButton(Icons.Filled.ContentCopy, stringResource(R.string.logs_copy), {
                        clipboard.setText(AnnotatedString(viewModel.exportText()))
                    })
                    NeuIconButton(Icons.Filled.Share, stringResource(R.string.logs_share), {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                            putExtra(Intent.EXTRA_TEXT, viewModel.exportText().takeLast(200_000))
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, shareTitle)) }
                    })
                    NeuIconButton(Icons.Filled.DeleteSweep, stringResource(R.string.logs_clear), viewModel::clear, tint = colors.danger)
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogFilter.entries.forEach { f ->
                    NeuChip(
                        text = "${filterLabel(f)} · ${counts[f] ?: 0}",
                        selected = f == filter,
                        onClick = { viewModel.setFilter(f) },
                    )
                }
            }
            NeuTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = stringResource(R.string.logs_search),
                placeholder = stringResource(R.string.logs_search_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("log_list"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    crash?.let { text ->
                        item(key = "crash") {
                            NeuSurface(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.logs_last_crash), style = MaterialTheme.typography.titleSmall, color = colors.danger)
                                    Text(
                                        text.take(3000), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                                        color = colors.content, maxLines = 12, overflow = TextOverflow.Ellipsis,
                                    )
                                    NeuButton(stringResource(R.string.logs_last_crash_clear), viewModel::clearCrash, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                    if (!loading && entries.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.logs_empty), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(entries.size, key = { i -> i }) { i -> LogRow(entries[i]) }
                }
                if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp), color = colors.accent)
            }
        }
    }
}

@Composable
private fun filterLabel(f: LogFilter): String = stringResource(
    when (f) {
        LogFilter.ALL -> R.string.logs_filter_all
        LogFilter.ERRORS -> R.string.logs_filter_errors
        LogFilter.WARNINGS -> R.string.logs_filter_warnings
        LogFilter.INFO -> R.string.logs_filter_info
    },
)

private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.ERROR, LogLevel.ASSERT -> Color(0xFFE53935)
    LogLevel.WARN -> Color(0xFFFB8C00)
    LogLevel.INFO -> Color(0xFF1E88E5)
    LogLevel.DEBUG -> Color(0xFF43A047)
    LogLevel.VERBOSE -> Color(0xFF8D8D8D)
}

/** Bir günlük satırı: önem rozeti, saat, etiket ve mesaj (dokununca tamamı açılır). */
@Composable
private fun LogRow(entry: LogEntry) {
    val colors = NeuTheme.colors
    var expanded by remember(entry) { mutableStateOf(false) }
    NeuSurface(modifier = Modifier.fillMaxWidth(), elevation = 3.dp) {
        Column(Modifier.clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(levelColor(entry.level)).padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(entry.level.letter.toString(), style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
                Text(entry.time, style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, fontFamily = FontFamily.Monospace)
                Text(
                    entry.tag, style = MaterialTheme.typography.labelMedium, color = colors.accent, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp),
                )
            }
            Text(
                entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = colors.content,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
