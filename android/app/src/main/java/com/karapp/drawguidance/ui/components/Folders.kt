package com.karapp.drawguidance.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.local.FolderEntity
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * Listelerde (tuvaller, resimler) çoklu seçim: anahtarlar "f:<klasör>" ve "i:<öğe>" biçimindedir. Uzun basmak seçime
 * ekler/çıkarır; seçim varken dokunmak da seçimi değiştirir.
 */
object ListSelection {
    fun folder(id: String) = "f:$id"
    fun item(id: String) = "i:$id"
    fun folders(selection: Set<String>): List<String> = selection.filter { it.startsWith("f:") }.map { it.removePrefix("f:") }
    fun items(selection: Set<String>): List<String> = selection.filter { it.startsWith("i:") }.map { it.removePrefix("i:") }
    fun toggle(selection: Set<String>, key: String): Set<String> = if (key in selection) selection - key else selection + key
}

/** Galeri görünümünde klasör: büyük klasör simgesi, adı ve içindeki öğe sayısı. */
@Composable
fun FolderTile(
    folder: FolderEntity,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    GalleryTile(
        title = folder.name,
        subtitle = stringResource(R.string.folder_count, count),
        ratio = 1.3f,
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = com.karapp.drawguidance.i18n.I18n.trf("Klasör {0}", folder.name) }.testTag("folder_tile"),
    ) {
        Box(Modifier.fillMaxSize().background(colors.accent.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Folder, null, tint = colors.accent.copy(alpha = 0.85f), modifier = Modifier.size(56.dp))
        }
        if (selected) SelectedCheck(Modifier.align(Alignment.TopStart).padding(8.dp))
    }
}

/** Liste görünümünde klasör satırı. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderRow(
    folder: FolderEntity,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.background(colors.accent.copy(alpha = 0.10f)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) { contentDescription = com.karapp.drawguidance.i18n.I18n.trf("Klasör {0}", folder.name) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(colors.accent.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Folder, null, tint = colors.accent, modifier = Modifier.size(34.dp))
            if (selected) SelectedCheck(Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, translate = false, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (selected) colors.accent else colors.content)
            Text(stringResource(R.string.folder_count, count), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
        }
    }
}

@Composable
fun SelectedCheck(modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    Box(modifier.size(24.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Check, null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
    }
}

/** Yeni klasör / yeniden adlandırma penceresi. */
@Composable
fun FolderNameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) =
    TextInputSheet(title, initial, stringResource(R.string.folder_name), onConfirm, onDismiss, tag = "folder_name_sheet")

/** Seçilenleri klasöre taşı: ana liste, mevcut klasörler ya da yeni klasör. */
@Composable
fun MoveToFolderDialog(
    folders: List<FolderEntity>,
    current: String?,
    onMove: (String?) -> Unit,
    onNewFolder: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NeuTheme.colors
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(stringResource(R.string.move_to_folder)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FolderOption(Icons.Filled.Home, stringResource(R.string.folder_root), current == null) { onMove(null) }
                folders.forEach { f -> FolderOption(Icons.Filled.Folder, f.name, current == f.id) { onMove(f.id) } }
                FolderOption(Icons.Filled.CreateNewFolder, stringResource(R.string.folder_new), false) { creating = true }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) } },
    )
    if (creating) {
        FolderNameDialog(stringResource(R.string.folder_new), "", onConfirm = { creating = false; onNewFolder(it) }, onDismiss = { creating = false })
    }
}

@Composable
private fun FolderOption(icon: ImageVector, label: String, current: Boolean, onClick: () -> Unit) {
    val colors = NeuTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .folderOptionClick(onClick)
            .testTag("folder_option")
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = if (current) colors.contentMuted else colors.accent)
        Text(label, color = if (current) colors.contentMuted else colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (current) Text(stringResource(R.string.folder_here), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.folderOptionClick(onClick: () -> Unit) = this.combinedClickable(onClick = onClick)

/**
 * Tek alanlı yazı girişi (klasör adı, katman adı, grafikteki sayı…): alttan açılan pencere. Uygulamadaki tüm kısa
 * yazı girişleri bunu kullanır.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TextInputSheet(
    title: String,
    initial: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    allowBlank: Boolean = false,
    tag: String = "text_input_sheet",
) {
    val colors = NeuTheme.colors
    var value by remember { mutableStateOf(initial) }
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp).testTag(tag),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            NeuTextField(value, { value = it }, label = label, keyboardOptions = keyboardOptions, modifier = Modifier.fillMaxWidth())
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuButton(stringResource(R.string.cancel_button), onDismiss, Modifier.weight(1f))
                NeuButton(
                    stringResource(R.string.ok_button), { onConfirm(value) }, Modifier.weight(1f),
                    primary = true, enabled = allowBlank || value.isNotBlank(),
                )
            }
        }
    }
}
