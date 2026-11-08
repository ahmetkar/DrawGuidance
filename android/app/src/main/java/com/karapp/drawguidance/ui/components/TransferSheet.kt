package com.karapp.drawguidance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.transfer.TransferTarget
import com.karapp.drawguidance.ui.theme.NeuTheme

@Composable
private fun kindLabel(kind: CanvasKind): String = canvasKindLabel(kind)

/**
 * v29 modlar arası aktarma penceresi: nasıl (vektör: düzenlenebilir elemanlar / resim: tek resim), ne ([selectionCount]
 * > 0 ise seçili elemanlar ya da tüm sayfa) ve nereye (yeni tuval ya da kayıtlı tuvallerden biri). Hedefe dokununca
 * aktarılır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSheet(
    title: String,
    hint: String,
    targets: List<CanvasUi>,
    newKinds: List<CanvasKind>,
    working: Boolean,
    onTransfer: (target: TransferTarget, vector: Boolean, selectionOnly: Boolean) -> Unit,
    onDismiss: () -> Unit,
    selectionCount: Int = 0,
) {
    val colors = NeuTheme.colors
    var vector by rememberSaveable { mutableStateOf(true) }
    var selectionOnly by rememberSaveable { mutableStateOf(selectionCount > 0) }
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().heightIn(max = 640.dp).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp).testTag("transfer_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)

            NeuSectionTitle(stringResource(R.string.transfer_how))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuChip(stringResource(R.string.transfer_vector), vector, onClick = { vector = true }, modifier = Modifier.weight(1f).testTag("transfer_vector"))
                NeuChip(stringResource(R.string.transfer_image), !vector, onClick = { vector = false }, modifier = Modifier.weight(1f).testTag("transfer_image"))
            }
            Text(
                stringResource(if (vector) R.string.transfer_vector_hint else R.string.transfer_image_hint),
                style = MaterialTheme.typography.bodySmall, color = colors.contentMuted,
            )
            if (selectionCount > 0) {
                NeuSectionTitle(stringResource(R.string.transfer_what))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuChip(stringResource(R.string.transfer_selection, selectionCount), selectionOnly, onClick = { selectionOnly = true }, modifier = Modifier.weight(1f))
                    NeuChip(stringResource(R.string.transfer_page), !selectionOnly, onClick = { selectionOnly = false }, modifier = Modifier.weight(1f))
                }
            }

            NeuSectionTitle(stringResource(R.string.transfer_where))
            if (working) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.accent)
                    Text(stringResource(R.string.transfer_working), style = MaterialTheme.typography.bodyMedium)
                }
            }
            newKinds.forEach { kind ->
                NeuButton(
                    stringResource(R.string.transfer_new, kindLabel(kind)),
                    { if (!working) onTransfer(TransferTarget.New(kind), vector, selectionOnly) },
                    Modifier.fillMaxWidth().testTag("transfer_new_${kind.name}"),
                    icon = Icons.Filled.Add,
                )
            }
            if (targets.isEmpty()) {
                Text(stringResource(R.string.transfer_no_targets), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            }
            targets.forEach { c ->
                NeuSurface(
                    modifier = Modifier.fillMaxWidth().testTag("transfer_target_${c.localId}"),
                    elevation = 4.dp,
                    onClick = { if (!working) onTransfer(TransferTarget.Existing(c.localId), vector, selectionOnly) },
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                kindLabel(c.kind) + if (c.pageCount > 1) " · " + stringResource(R.string.transfer_page_of, c.currentPage + 1, c.pageCount) else "",
                                style = MaterialTheme.typography.bodySmall, color = colors.contentMuted,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.accent)
                    }
                }
            }
        }
    }
}
