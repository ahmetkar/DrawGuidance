package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.karapp.drawguidance.ui.screens.canvasedit.editor.Element
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementIndex
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.BrushIcons
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSectionTitle
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.brushLabel
import com.karapp.drawguidance.ui.screens.canvasedit.BrushSettings
import com.karapp.drawguidance.ui.screens.canvasedit.PanelTab
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementRow
import com.karapp.drawguidance.ui.theme.NeuFieldShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic
import com.karapp.drawguidance.ui.components.NeuSheet

/** Sağdan açılan panel: Elemanlar, Katmanlar ve v47 Özellikler (seçili aracın ayarları; eski "Fırça"). */
@Composable
fun EditorSidePanel(
    tab: PanelTab,
    onTabChange: (PanelTab) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** v43 geniş ekranda paneli sürükleme hareketi (başlıktaki tutamakta); null ise sürüklenmez. */
    dragHandle: Modifier? = null,
    /** v45 tablette panel yerinden oynadıysa "Konumu sıfırla" (null: düğme yok). */
    onResetPosition: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    NeuSurface(modifier = modifier.fillMaxHeight(), elevation = 12.dp) {
        Column(Modifier.fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dragHandle != null) {
                    val label = stringResource(R.string.v43_panel_drag)
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.DragIndicator, contentDescription = label,
                        tint = NeuTheme.colors.contentMuted,
                        modifier = Modifier.size(32.dp).then(dragHandle).testTag("side_panel_drag"),
                    )
                }
                if (onResetPosition != null) {
                    com.karapp.drawguidance.ui.components.NeuIconButton(
                        androidx.compose.material.icons.Icons.Filled.RestartAlt, stringResource(R.string.v45_window_reset), onResetPosition,
                        size = 30.dp, modifier = Modifier.testTag("window_reset:side_panel"),
                    )
                }
                // v47 sekme çipleri yok: alanın içeriği mod menüsündeki (dikey ekran) ya da alanın solundaki raydaki
                // (yatay ekran) Elemanlar, Katmanlar, Özellikler düğmeleriyle değişir; başlıkta açık olanın adı
                Text(
                    stringResource(PanelMenu.labelRes(tab)), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("side_panel_title"),
                )
                NeuIconButton(Icons.Filled.Close, stringResource(R.string.close_panel), onClose, size = 38.dp)
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

/** v43 geniş ekranda sürüklenen yan panelin kenara yerleşmesi. */
object SidePanelDock {
    /** Bırakılan panelin ortası alanın sol yarısındaysa sola. */
    fun dockLeft(centerX: Float, areaWidth: Float): Boolean = centerX < areaWidth / 2f

    /** Sürükleme alanın dışına taşmaz. */
    fun clampDrag(drag: Float, left: Boolean, areaWidth: Float, panelWidth: Float): Float {
        val room = (areaWidth - panelWidth).coerceAtLeast(0f)
        return if (left) drag.coerceIn(0f, room) else drag.coerceIn(-room, 0f)
    }
}

@Composable
fun BrushPanelContent(
    settings: BrushSettings,
    savedColors: List<String>,
    onOpenBrushPicker: () -> Unit,
    onSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onFlowChange: (Float) -> Unit,
    onStrengthChange: (Float) -> Unit,
    onColorChange: (String) -> Unit,
    onSaveColor: () -> Unit,
    onRemoveColor: (String) -> Unit,
    textSection: (@Composable () -> Unit)? = null,
    /** v20 fırça başına ayarlar (aralık, çizim hızı, sabitleme, eğim, 3B). */
    onTuning: ((com.karapp.drawguidance.data.settings.BrushTuning) -> com.karapp.drawguidance.data.settings.BrushTuning) -> Unit = {},
    onResetTuning: () -> Unit = {},
    /** v43 damlalık: tuvalden renk al (panel kapanır, dokunulan yerin rengi fırça rengi olur). */
    onEyedropper: (() -> Unit)? = null,
    /** v33 çocuklar için sade panel: fırça, kalınlık ve büyük renk paleti. */
    kids: Boolean = false,
) {
    val colors = NeuTheme.colors
    if (kids) {
        Column(
            modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(4.dp).testTag("kids_brush_panel"),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            textSection?.invoke()
            NeuSurface(onClick = onOpenBrushPicker, modifier = Modifier.fillMaxWidth(), elevation = 5.dp) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(BrushIcons.of(settings.brushType), null, tint = colors.accent, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(brushLabel(settings.brushType), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
            }
            BrushSizeControl(settings.sizePx, onSizeChange)
            Text(stringResource(R.string.brush_color_label), style = MaterialTheme.typography.titleSmall)
            KidsPalette(settings.colorHex, onColorChange)
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Yazı modunda ya da yazı seçiliyken en üstte yazı tipi ayarları
        textSection?.invoke()

        NeuSurface(onClick = onOpenBrushPicker, modifier = Modifier.fillMaxWidth(), elevation = 5.dp) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(BrushIcons.of(settings.brushType), null, tint = colors.accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(brushLabel(settings.brushType), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.change_brush), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                }
            }
        }

        // Ayarların etkisini gösteren canlı örnek iz
        BrushPreview(
            brushType = settings.brushType,
            color = hexToColor(settings.colorHex),
            paper = if (colors.isDark) androidx.compose.ui.graphics.Color(0xFFF4F1EA) else androidx.compose.ui.graphics.Color.White,
            opacity = settings.opacity,
            flow = settings.flow,
            strength = settings.strength,
            params = settings.params,
            modifier = Modifier.fillMaxWidth().height(52.dp).neumorphic(colors, NeuFieldShape, elevation = 3.dp, inset = true).padding(4.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
        )

        // v20: 1–380 px, üstel kaydırıcı; değere dokununca yazılabilir
        BrushSizeControl(settings.sizePx, onSizeChange)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.brush_opacity_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("%${(settings.opacity * 100).toInt()}", style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
            }
            NeuSlider(value = settings.opacity, onValueChange = onOpacityChange, valueRange = 0.05f..1f)
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.brush_flow_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("%${(settings.flow * 100).toInt()}", style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
            }
            Text(stringResource(R.string.brush_flow_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            NeuSlider(value = settings.flow, onValueChange = onFlowChange, valueRange = 0.05f..1f)
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.brush_strength_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("%${(settings.strength * 100).toInt()}", style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
            }
            Text(stringResource(R.string.brush_strength_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            NeuSlider(value = settings.strength, onValueChange = onStrengthChange, valueRange = 0.1f..1f)
        }

        BrushTuningSection(settings.brushType, settings.tuning, onTuning, onResetTuning)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.brush_color_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Box(Modifier.size(22.dp).background(hexToColor(settings.colorHex), CircleShape).border(2.dp, colors.lightShadow, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(settings.colorHex, style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
                if (onEyedropper != null) {
                    Spacer(Modifier.width(8.dp))
                    NeuIconButton(
                        androidx.compose.material.icons.Icons.Filled.Colorize, stringResource(R.string.v43_eyedropper), onEyedropper,
                        size = 34.dp, modifier = Modifier.testTag("brush_eyedropper"),
                    )
                }
            }
            HsvColorPicker(colorHex = settings.colorHex, onColorChange = onColorChange)
        }

        SavedColors(
            savedColors = savedColors,
            currentHex = settings.colorHex,
            onSelect = onColorChange,
            onSave = onSaveColor,
            onRemove = onRemoveColor,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SavedColors(
    savedColors: List<String>,
    currentHex: String,
    onSelect: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = NeuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeuSectionTitle(stringResource(R.string.saved_colors_title), Modifier.weight(1f))
            NeuIconButton(Icons.Filled.Add, stringResource(R.string.save_color), onSave, size = 36.dp, tint = colors.accent)
        }
        if (savedColors.isEmpty()) {
            Text(stringResource(R.string.saved_colors_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        } else {
            savedColors.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { hex ->
                        val selected = hex.equals(currentHex, ignoreCase = true)
                        val description = stringResource(R.string.saved_color_cd, hex)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .neumorphic(colors, CircleShape, elevation = if (selected) 2.dp else 4.dp, selected = selected)
                                .clip(CircleShape)
                                .combinedClickable(role = Role.Button, onClick = { onSelect(hex) }, onLongClick = { onRemove(hex) })
                                .semantics { contentDescription = description }
                                .padding(5.dp)
                                .background(hexToColor(hex), CircleShape),
                        )
                    }
                }
            }
            Text(stringResource(R.string.saved_colors_remove_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        }
    }
}

@Composable
fun ElementsPanelContent(
    rows: List<ElementRow>,
    elements: ElementIndex,
    selectedIds: Set<String>,
    onToggleElement: (String) -> Unit,
    onToggleGroup: (String) -> Unit,
    onGroup: () -> Unit,
    onUngroup: () -> Unit,
    onRecolor: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onClearSelection: () -> Unit,
    styleSection: (@Composable () -> Unit)? = null,
    /** v23 seçili elemanların sırası (ZOrder yönü). */
    onOrder: (Int) -> Unit = {},
    /** v30 seçili elemanlardan istenenleri gizle (seçim penceresi ya da doğrudan). */
    onHide: () -> Unit = {},
    /** v30 eleman kimliklerini gizle (true) ya da göster (false); göz düğmeleri. */
    onSetHidden: (List<String>, Boolean) -> Unit = { _, _ -> },
    /** v30 gizlenenlerin hepsini göster. */
    onShowAll: () -> Unit = {},
    /** v41 açık sayfada arka plan (gradyan, desen ya da resim) var: listenin en altında eleman gibi seçilir. */
    hasBackground: Boolean = false,
    backgroundSelected: Boolean = false,
    onSelectBackground: () -> Unit = {},
) {
    val colors = NeuTheme.colors
    val selectedElements = elements.selectedElements(selectedIds)
    val selectedGroups = selectedElements.mapNotNull { it.groupId }.toSet()
    val canGroup = selectedElements.size >= 2 && !(selectedGroups.size == 1 && selectedElements.all { it.groupId != null })
    val canUngroup = selectedGroups.isNotEmpty()
    val hasSelection = selectedElements.isNotEmpty()
    val hiddenCount = elements.elements.count { it.hidden }

    LazyColumn(
        modifier = Modifier.fillMaxHeight().testTag("elements_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(4.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (hasSelection) stringResource(R.string.selection_count, selectedElements.size) else stringResource(R.string.elements_select_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.contentMuted,
                        modifier = Modifier.weight(1f),
                    )
                    if (hasSelection) {
                        NeuIconButton(Icons.Filled.Close, stringResource(R.string.clear_selection), onClearSelection, size = 32.dp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeuButton(stringResource(R.string.action_group), onGroup, Modifier.weight(1f), enabled = canGroup, icon = Icons.Filled.Link)
                    NeuButton(stringResource(R.string.action_ungroup), onUngroup, Modifier.weight(1f), enabled = canUngroup, icon = Icons.Filled.LinkOff)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeuButton(
                        stringResource(R.string.action_recolor), onRecolor, Modifier.weight(1f),
                        enabled = hasSelection, icon = Icons.Filled.FormatColorFill,
                    )
                    NeuButton(stringResource(R.string.action_move), onMove, Modifier.weight(1f), enabled = hasSelection, icon = Icons.Filled.OpenWith)
                }
                // v23 üst/alt sıra: en alta, bir alta, bir üste, en üste
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.order_title), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, modifier = Modifier.weight(1f))
                    NeuIconButton(Icons.Filled.VerticalAlignBottom, stringResource(R.string.order_back), { onOrder(com.karapp.drawguidance.ui.screens.canvasedit.editor.ZOrder.BACK) }, size = 36.dp, enabled = hasSelection)
                    NeuIconButton(Icons.Filled.KeyboardArrowDown, stringResource(R.string.order_backward), { onOrder(com.karapp.drawguidance.ui.screens.canvasedit.editor.ZOrder.BACKWARD) }, size = 36.dp, enabled = hasSelection)
                    NeuIconButton(Icons.Filled.KeyboardArrowUp, stringResource(R.string.order_forward), { onOrder(com.karapp.drawguidance.ui.screens.canvasedit.editor.ZOrder.FORWARD) }, size = 36.dp, enabled = hasSelection)
                    NeuIconButton(Icons.Filled.VerticalAlignTop, stringResource(R.string.order_front), { onOrder(com.karapp.drawguidance.ui.screens.canvasedit.editor.ZOrder.FRONT) }, size = 36.dp, enabled = hasSelection)
                }
                // v30 gizle / gizlenenleri göster
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeuButton(
                        stringResource(R.string.hide_elements), onHide, Modifier.weight(1f).testTag("panel_hide"),
                        enabled = hasSelection, icon = Icons.Filled.VisibilityOff,
                    )
                    NeuButton(
                        if (hiddenCount > 0) stringResource(R.string.show_hidden_count, hiddenCount) else stringResource(R.string.show_hidden),
                        onShowAll, Modifier.weight(1f).testTag("panel_show_all"),
                        enabled = hiddenCount > 0, icon = Icons.Filled.Visibility,
                    )
                }
                NeuButton(
                    stringResource(R.string.action_delete_elements), onDelete, Modifier.fillMaxWidth(),
                    enabled = hasSelection, icon = Icons.Filled.Delete, contentColor = if (hasSelection) colors.danger else null,
                )
            }
        }

        if (styleSection != null) {
            item { styleSection() }
        }

        if (rows.isEmpty()) {
            item {
                Text(stringResource(R.string.elements_empty), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
            }
        }

        items(rows, key = { row ->
            when (row) {
                is ElementRow.Single -> row.element.id
                is ElementRow.Group -> "group-" + row.groupId
            }
        }) { row ->
            when (row) {
                is ElementRow.Single -> ElementItem(
                    element = row.element,
                    selected = !row.element.hidden && selectedIds.containsAll(row.element.strokeIds),
                    onClick = { onToggleElement(row.element.id) },
                    onToggleHidden = { onSetHidden(listOf(row.element.id), !row.element.hidden) },
                )
                is ElementRow.Group -> GroupItem(
                    row = row,
                    selected = row.members.any { !it.hidden } && row.members.filterNot { it.hidden }.all { selectedIds.containsAll(it.strokeIds) },
                    onClick = { onToggleGroup(row.groupId) },
                    onSetHidden = onSetHidden,
                )
            }
        }

        // v41 arka plan en altta (bütün elemanların arkasında)
        if (hasBackground) item(key = "page-background") {
            NeuSurface(
                modifier = Modifier.fillMaxWidth().testTag("element_background"),
                shape = NeuFieldShape, elevation = 4.dp, selected = backgroundSelected, onClick = onSelectBackground,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SelectionMark(backgroundSelected)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Filled.Wallpaper, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.v41_bg_element), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    val colors = NeuTheme.colors
    Box(
        modifier = Modifier
            .size(24.dp)
            .neumorphic(colors, CircleShape, elevation = 2.dp, inset = true, fill = if (selected) colors.accent else colors.background),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun elementName(element: Element): String = "${brushLabel(element.brushType)} ${element.number}"

/** v30 elemanın görünürlük düğmesi (göz): gizliyse üstü çizili göz. */
@Composable
private fun HiddenToggle(element: Element, onToggle: () -> Unit, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val label = stringResource(if (element.hidden) R.string.element_show else R.string.element_hide, elementName(element))
    NeuIconButton(
        if (element.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, label, onToggle,
        size = size, modifier = Modifier.testTag("eye_" + element.id),
    )
}

@Composable
private fun ElementItem(element: Element, selected: Boolean, onClick: () -> Unit, onToggleHidden: () -> Unit) {
    val colors = NeuTheme.colors
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = NeuFieldShape,
        elevation = if (element.hidden) 2.dp else 4.dp,
        selected = selected,
        onClick = onClick,
    ) {
        Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).alpha(if (element.hidden) 0.45f else 1f), verticalAlignment = Alignment.CenterVertically) {
                SelectionMark(selected)
                Spacer(Modifier.width(12.dp))
                Icon(
                    BrushIcons.of(element.brushType), null,
                    tint = if (element.brushType == BrushType.IMAGE) colors.accent else hexToColor(element.colorHex),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    elementName(element),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (element.hidden) {
                    Text(stringResource(R.string.element_hidden_tag), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
                } else if (element.strokeIds.size > 1) {
                    Text(stringResource(R.string.stroke_count, element.strokeIds.size), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
                }
            }
            Spacer(Modifier.width(6.dp))
            HiddenToggle(element, onToggleHidden)
        }
    }
}

@Composable
private fun GroupItem(row: ElementRow.Group, selected: Boolean, onClick: () -> Unit, onSetHidden: (List<String>, Boolean) -> Unit) {
    val colors = NeuTheme.colors
    val allHidden = row.members.all { it.hidden }
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = NeuFieldShape,
        elevation = 5.dp,
        selected = selected,
        onClick = onClick,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionMark(selected)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Filled.Link, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.group_label, row.number),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.group_count, row.members.size), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
                Spacer(Modifier.width(6.dp))
                // Grubun gözü: hepsi gizliyse hepsini gösterir, değilse görünenlerin hepsini gizler
                NeuIconButton(
                    if (allHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    stringResource(if (allHidden) R.string.element_show else R.string.element_hide, stringResource(R.string.group_label, row.number)),
                    { onSetHidden(row.members.filter { it.hidden == allHidden }.map { it.id }, !allHidden) },
                    size = 32.dp, modifier = Modifier.testTag("eye_group_" + row.groupId),
                )
            }
            row.members.forEach { element ->
                Row(Modifier.padding(start = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).alpha(if (element.hidden) 0.45f else 1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(hexToColor(element.colorHex), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(elementName(element), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, modifier = Modifier.weight(1f))
                    }
                    HiddenToggle(element, { onSetHidden(listOf(element.id), !element.hidden) }, size = 28.dp)
                }
            }
        }
    }
}

/**
 * "Renk Ver": renk paletinden (ve kayıtlı renklerden) seçilen renk, "Uygula" ile seçili
 * elemanlardaki tüm çizimlere verilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecolorSheet(
    initialColorHex: String,
    selectedCount: Int,
    savedColors: List<String>,
    onSaveColor: (String) -> Unit,
    onRemoveColor: (String) -> Unit,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    title: String? = null,
    subtitle: String? = null,
    swatches: List<String> = emptyList(),
) {
    val colors = NeuTheme.colors
    var colorHex by remember { mutableStateOf(initialColorHex.uppercase()) }
    NeuSheet(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title ?: stringResource(R.string.recolor_title), style = MaterialTheme.typography.headlineSmall)
                    Text(subtitle ?: stringResource(R.string.selection_count, selectedCount), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .neumorphic(colors, CircleShape, elevation = 3.dp, inset = true)
                        .padding(6.dp)
                        .background(hexToColor(colorHex), CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Text(colorHex, style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
            }
            if (swatches.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    swatches.forEach { hex ->
                        val isSelected = hex.equals(colorHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .neumorphic(colors, CircleShape, elevation = if (isSelected) 2.dp else 5.dp, selected = isSelected)
                                .clip(CircleShape)
                                .clickable(role = Role.Button, onClick = { colorHex = hex.uppercase() })
                                .semantics { contentDescription = hex }
                                .padding(if (isSelected) 8.dp else 5.dp)
                                .background(hexToColor(hex), CircleShape),
                        )
                    }
                }
            }
            HsvColorPicker(colorHex = colorHex, onColorChange = { colorHex = it.uppercase() })
            SavedColors(
                savedColors = savedColors,
                currentHex = colorHex,
                onSelect = { colorHex = it.uppercase() },
                onSave = { onSaveColor(colorHex) },
                onRemove = onRemoveColor,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NeuButton(stringResource(R.string.cancel_button), onDismiss, Modifier.weight(1f))
                NeuButton(
                    stringResource(R.string.apply_button),
                    { onApply(colorHex) },
                    Modifier.weight(1f),
                    primary = true,
                    icon = Icons.Filled.FormatColorFill,
                )
            }
        }
    }
}
