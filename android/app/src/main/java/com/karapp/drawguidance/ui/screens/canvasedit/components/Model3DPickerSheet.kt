package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.karapp.drawguidance.content.model3d.Catalog3D
import com.karapp.drawguidance.data.local.Model3DInfo
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.screens.model3d.Model3DCard
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.components.NeuSheet

/**
 * v25 tuvalde "3B şekil ekle" penceresi (Ekle menüsünde ayrı seçenek): 3B Şekillerim ve hazır şekiller; dokunmak şekli
 * seçer (vurgulanır), alttaki "3B şekil ekle" düğmesi seçili şekli tuvale ekler. Kalem 3B düzenleyicide açar, "Yeni 3B
 * şekil" boş düzenleyiciyi açar. Tuvalde 3B şekil yalnızca genel işlemlerle (taşı, ölçekle, döndür, sil) düzenlenir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Model3DPickerSheet(
    models: List<Model3DInfo>,
    onInsert: (String) -> Unit,
    onInsertCatalog: ((String) -> Unit)? = null,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
    /** v27 GLB/glTF içe aktarma (dosya seçici açılır; kaydetme sorusu sonra). */
    onImportGlb: (() -> Unit)? = null,
    /** v33 basit çizimde yalnız bu 10 hazır şekil. */
    catalogOnly: List<String>? = null,
) {
    val colors = NeuTheme.colors
    // Seçili şekil: "my:<kimlik>" (3B Şekillerim) ya da "cat:<kimlik>" (hazır şekil)
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val chosenName = chosen?.let { key ->
        when {
            key.startsWith(MY) -> models.firstOrNull { it.id == key.removePrefix(MY) }?.name
            key.startsWith(CAT) -> Catalog3D.all.firstOrNull { it.id == key.removePrefix(CAT) }?.name
            else -> null
        }
    }
    val insert = {
        val key = chosen
        if (key != null && chosenName != null) {
            if (key.startsWith(MY)) onInsert(key.removePrefix(MY)) else onInsertCatalog?.invoke(key.removePrefix(CAT))
        }
    }
    NeuSheet(onDismiss) {
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().testTag("model3d_picker")) {
            val columns = (maxWidth / 130.dp).toInt().coerceIn(2, 6)
            Column(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.model3d_add_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.model3d_add_pick_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        NeuButton(stringResource(R.string.model3d_new), onNew, Modifier.weight(1f), icon = Icons.Filled.ViewInAr)
                        if (onImportGlb != null) {
                            NeuButton(
                                stringResource(R.string.glb_import_button), onImportGlb, Modifier.weight(1f).testTag("model3d_import_glb"),
                                icon = Icons.Filled.FileUpload,
                            )
                        }
                    }
                    Text(stringResource(R.string.model3d_my_models), style = MaterialTheme.typography.titleMedium)
                    if (models.isEmpty()) Text(stringResource(R.string.model3d_list_empty), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
                    models.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { info ->
                                val key = MY + info.id
                                Model3DCard(
                                    info, onClick = { chosen = if (chosen == key) null else key }, modifier = Modifier.weight(1f),
                                    onEdit = { onEdit(info.id) }, selected = chosen == key,
                                )
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    // v24 hazır şekiller (ders ve çizim için)
                    if (onInsertCatalog != null) {
                        Text(stringResource(R.string.model3d_ready_shapes), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.model3d_add_ready_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                        com.karapp.drawguidance.ui.screens.model3d.CatalogGrid(
                            onPick = { item -> val key = CAT + item.id; chosen = if (chosen == key) null else key },
                            columns = (columns + 1).coerceAtMost(7), tile = 72.dp,
                            selected = chosen?.takeIf { it.startsWith(CAT) }?.removePrefix(CAT),
                            only = catalogOnly,
                        )
                    }
                }
                // Alt çubuk: seçili şekil ve "3B şekil ekle" düğmesi (her zaman görünür)
                HorizontalDivider(color = colors.contentMuted.copy(alpha = 0.18f))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        chosenName?.let { stringResource(R.string.model3d_add_selected, it) } ?: stringResource(R.string.model3d_add_none),
                        style = MaterialTheme.typography.bodyMedium, color = if (chosenName != null) colors.accent else colors.contentMuted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    NeuButton(
                        stringResource(R.string.model3d_add_button), insert, Modifier.testTag("model3d_add_button"),
                        primary = true, enabled = chosenName != null, icon = Icons.Filled.Add,
                    )
                }
            }
        }
    }
}

private const val MY = "my:"
private const val CAT = "cat:"
