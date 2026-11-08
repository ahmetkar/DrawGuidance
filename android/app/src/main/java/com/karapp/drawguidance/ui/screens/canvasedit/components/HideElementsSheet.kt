package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.BrushIcons
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.screens.canvasedit.editor.Element
import com.karapp.drawguidance.ui.theme.NeuFieldShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic
import com.karapp.drawguidance.ui.components.NeuSheet

/**
 * v30 "Gizle": seçili elemanlardan hangilerinin gizleneceği seçilir (hepsi işaretli açılır). Altta gizlenmiş
 * elemanlar listelenir; tek tek ya da hepsi yeniden gösterilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HideElementsSheet(
    selected: List<Element>,
    hidden: List<Element>,
    onHide: (List<String>) -> Unit,
    onShow: (List<String>?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NeuTheme.colors
    var chosen by remember(selected) { mutableStateOf(selected.map { it.id }.toSet()) }
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.hide_sheet_title), style = MaterialTheme.typography.headlineSmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selected.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.hide_sheet_selected, selected.size),
                                style = MaterialTheme.typography.titleSmall, color = colors.accent, modifier = Modifier.weight(1f),
                            )
                            val all = chosen.size == selected.size
                            Text(
                                stringResource(if (all) R.string.hide_sheet_none else R.string.hide_sheet_all),
                                style = MaterialTheme.typography.labelLarge, color = colors.accent,
                                modifier = Modifier.clip(NeuFieldShape)
                                    .clickable(role = Role.Button) { chosen = if (all) emptySet() else selected.map { it.id }.toSet() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(selected, key = { "s" + it.id }) { e ->
                        val on = e.id in chosen
                        ElementLine(
                            element = e,
                            modifier = Modifier.testTag("hide_pick_" + e.id),
                            onClick = { chosen = if (on) chosen - e.id else chosen + e.id },
                            trailing = {
                                Box(
                                    Modifier.size(26.dp).neumorphic(colors, CircleShape, elevation = 2.dp, inset = true, fill = if (on) colors.accent else colors.background),
                                    contentAlignment = Alignment.Center,
                                ) { if (on) Icon(Icons.Filled.Check, null, tint = colors.onAccent, modifier = Modifier.size(16.dp)) }
                            },
                        )
                    }
                }
                if (hidden.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.hide_sheet_hidden, hidden.size),
                            style = MaterialTheme.typography.titleSmall, color = colors.accent, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(hidden, key = { "h" + it.id }) { e ->
                        ElementLine(
                            element = e,
                            modifier = Modifier.testTag("hidden_row_" + e.id),
                            dim = true,
                            onClick = { onShow(listOf(e.id)) },
                            trailing = { Icon(Icons.Filled.Visibility, stringResource(R.string.element_show, elementName(e)), tint = colors.accent) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hidden.isNotEmpty()) {
                    NeuButton(
                        stringResource(R.string.show_hidden_count, hidden.size), { onShow(null) }, Modifier.weight(1f).testTag("hide_sheet_show_all"),
                        icon = Icons.Filled.Visibility,
                    )
                }
                if (selected.isNotEmpty()) {
                    NeuButton(
                        stringResource(R.string.hide_sheet_apply, chosen.size), { onHide(selected.filter { it.id in chosen }.map { it.id }) },
                        Modifier.weight(1f).testTag("hide_sheet_apply"), enabled = chosen.isNotEmpty(), icon = Icons.Filled.VisibilityOff,
                    )
                } else if (hidden.isEmpty()) {
                    NeuButton(stringResource(R.string.cancel_button), onDismiss, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ElementLine(
    element: Element,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    val colors = NeuTheme.colors
    NeuSurface(modifier = modifier.fillMaxWidth(), shape = NeuFieldShape, elevation = 3.dp, onClick = onClick) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                BrushIcons.of(element.brushType), null,
                tint = if (element.brushType == BrushType.IMAGE) colors.accent else hexToColor(element.colorHex).copy(alpha = if (dim) 0.5f else 1f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(10.dp).background(hexToColor(element.colorHex).copy(alpha = if (dim) 0.5f else 1f), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                elementName(element), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                color = if (dim) colors.contentMuted else colors.content,
            )
            trailing()
        }
    }
}
