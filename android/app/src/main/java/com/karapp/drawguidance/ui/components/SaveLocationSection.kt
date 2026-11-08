package com.karapp.drawguidance.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.images.SaveTarget
import com.karapp.drawguidance.data.settings.ExportSettingsStore
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v40 kaydetme pencerelerinin "Kaydedileceği yer" bölümü: varsayılan yer, hatırlanan klasör ve "Konum seç"
 * (klasör seç: ACTION_OPEN_DOCUMENT_TREE, kalıcı izinle hatırlanır; farklı kaydet: ACTION_CREATE_DOCUMENT, seçilince
 * hemen kaydedilir). [saveAsName] null ise (birden çok dosya) "Farklı kaydet" sunulmaz.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveLocationSection(
    defaultLabel: String,
    target: SaveTarget,
    onTarget: (SaveTarget) -> Unit,
    saveAsName: String?,
    saveAsMime: String,
    onSaveAs: (SaveTarget.Document) -> Unit,
    modifier: Modifier = Modifier,
    /** Gösterilen konum yazısının test etiketi. */
    labelTag: String = "save_location",
) {
    val colors = NeuTheme.colors
    val context = LocalContext.current
    val store = remember {
        runCatching { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<ExportSettingsStore>() }.getOrNull()
            ?: ExportSettingsStore(context.applicationContext)
    }
    var remembered by remember { mutableStateOf(store.pickedFolder()) }
    var menu by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val folder = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            SaveTarget.Folder(uri.toString(), ExportSettingsStore.folderLabelOf(DocumentsContract.getTreeDocumentId(uri)))
        }.getOrNull()
        failed = folder == null
        if (folder != null) {
            store.setPickedFolder(folder)
            remembered = folder
            onTarget(folder)
        }
    }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(saveAsMime)) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val label = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: saveAsName ?: uri.lastPathSegment ?: uri.toString()
        onSaveAs(SaveTarget.Document(uri.toString(), label))
    }

    Column(modifier.testTag("save_location_section"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.save_location), style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuChip(
                stringResource(R.string.v40_location_default, defaultLabel), target == SaveTarget.Default, { onTarget(SaveTarget.Default) },
                modifier = Modifier.testTag("save_location_default"),
            )
            val folder = (target as? SaveTarget.Folder) ?: remembered
            if (folder != null) {
                NeuChip(folder.label, target == folder, { onTarget(folder) }, icon = Icons.Filled.Folder, modifier = Modifier.testTag("save_location_folder"))
            }
            Box {
                NeuChip(stringResource(R.string.v40_location_pick), menu, { menu = true }, icon = Icons.Filled.FolderOpen, modifier = Modifier.testTag("save_location_pick"))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(colors.background)) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.v40_location_folder)) },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null, tint = colors.accent) },
                        onClick = { menu = false; runCatching { pickFolder.launch(null) } },
                        modifier = Modifier.testTag("save_location_pick_folder"),
                    )
                    if (saveAsName != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.v40_location_save_as)) },
                            leadingIcon = { Icon(Icons.Filled.SaveAs, null, tint = colors.accent) },
                            onClick = { menu = false; runCatching { createDocument.launch(saveAsName) } },
                            modifier = Modifier.testTag("save_location_save_as"),
                        )
                    }
                }
            }
        }
        if (target is SaveTarget.Folder) Text(target.label, style = MaterialTheme.typography.bodyMedium, color = colors.content, modifier = Modifier.testTag(labelTag))
        else Text(defaultLabel, style = MaterialTheme.typography.bodyMedium, color = colors.content, modifier = Modifier.testTag(labelTag))
        Text(
            stringResource(if (failed) R.string.v40_location_failed else R.string.v40_location_hint),
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) colors.danger else colors.contentMuted,
        )
    }
}
