package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.toolHelpClickable
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.PanelTab
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.tutorialAnchor
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic

/**
 * v47 sağdaki açılır alanın üç düğmesi: Elemanlar, Katmanlar ve Özellikler (eski "Fırça ayarları"; seçili aracın
 * ayarları). Düğmeler üst menüde değildir:
 * - dikey ekranda gelişmiş modlarda mod menüsünün son grubu, basit modlarda basit menünün düğmeleri;
 * - yatay ekranda her modda sağda, açılır alanın soluna yapışık dikey ray ([PanelRail]); seçildikçe alanın içeriği değişir.
 */
object PanelMenu {
    /** Düğmelerin sırası. */
    val TABS = listOf(PanelTab.ELEMENTS, PanelTab.LAYERS, PanelTab.BRUSH)

    /** Basit menüde düğmelerin kimlikleri (eğitim ve test etiketleri: simple_tool_<kimlik>). */
    fun simpleId(tab: PanelTab): String = when (tab) {
        PanelTab.ELEMENTS -> "elements"
        PanelTab.LAYERS -> "layers"
        PanelTab.BRUSH -> "brush_settings"
    }

    /** Eğitimdeki yerleri. */
    fun anchor(tab: PanelTab): String = when (tab) {
        PanelTab.ELEMENTS -> TutorialAnchor.ELEMENTS
        PanelTab.LAYERS -> TutorialAnchor.LAYERS
        PanelTab.BRUSH -> TutorialAnchor.BRUSH_SETTINGS
    }

    fun icon(tab: PanelTab): ImageVector = when (tab) {
        PanelTab.ELEMENTS -> Icons.Filled.Widgets
        PanelTab.LAYERS -> Icons.Filled.Layers
        PanelTab.BRUSH -> Icons.Filled.Tune
    }

    fun labelRes(tab: PanelTab): Int = when (tab) {
        PanelTab.ELEMENTS -> R.string.tab_elements
        PanelTab.LAYERS -> R.string.tab_layers
        PanelTab.BRUSH -> R.string.tab_brush
    }

    /** Düğmeler yatay ekranda sağdaki rayda (dikeyde menülerde). */
    fun railShown(landscape: Boolean): Boolean = landscape

    /** Basit modların üst menüsünün bölünmesi: dikeyde her zaman iki satır alt alta, yatayda tek satır (dörtlü gruplar). */
    fun simpleSplit(landscape: Boolean): ToolRowSplitMode = if (landscape) ToolRowSplitMode.NONE else ToolRowSplitMode.STACK_ALWAYS
}

/** v47 Özellikler alanının içeriği: yalnız ayarı olan araçlarda dolu. */
enum class PropertiesContent { BRUSH, TEXT, EMPTY }

/** v47 seçili araca göre Özellikler'in içeriği (saf; testlerde de kullanılır). */
object Properties {
    /**
     * Fırça ve silgi (Çiz modu, kalem aracı) → fırça ayarları; yazı aracı → yazı rengi ve (yeni yazıda) biçimi (düzenlenen
     * yazının biçimi Elemanlar'da); çocuk modunda boya fırçası → fırça boyu ve renkleri. Öteki araçların (seç, ekle,
     * düzenle, kova…) ayarı yok: boş.
     */
    @Suppress("UNUSED_PARAMETER")
    fun content(mode: EditMode, kids: Boolean, paintBrush: Boolean, textSelected: Boolean): PropertiesContent = when {
        mode == EditMode.TEXT -> PropertiesContent.TEXT
        mode == EditMode.DRAW || mode == EditMode.PEN -> PropertiesContent.BRUSH
        kids && mode == EditMode.PAINT && paintBrush -> PropertiesContent.BRUSH
        else -> PropertiesContent.EMPTY
    }
}

/** v47 basit menüdeki üç düğme (dikey ekranda menünün içinde). */
@Composable
fun panelSimpleTools(panel: PanelTab?, onToggle: (PanelTab) -> Unit): List<SimpleTool> = PanelMenu.TABS.map { tab ->
    SimpleTool(PanelMenu.simpleId(tab), PanelMenu.icon(tab), stringResource(PanelMenu.labelRes(tab)), panel == tab, { onToggle(tab) })
}

/** v47 gelişmiş modların mod menüsündeki son grup (dikey ekranda). */
@Composable
fun panelModeSlots(panel: PanelTab?, onToggle: (PanelTab) -> Unit, vertical: Boolean): List<ToolSlot> = PanelMenu.TABS.map { tab ->
    val label = stringResource(PanelMenu.labelRes(tab))
    ToolSlot("panel_${tab.name}", panel == tab) {
        PanelButton(PanelMenu.icon(tab), label, panel == tab, { onToggle(tab) }, vertical = vertical, anchor = PanelMenu.anchor(tab), tag = "panel_btn_${tab.name}")
    }
}

/**
 * v47 yatay ekranda sağdaki ray: Elemanlar, Katmanlar, Özellikler; adları simgelerinin altında yazar. Açılır alan
 * açıkken onun soluna yapışık durur; seçili düğmenin alanı açıktır (yeniden dokununca kapanır).
 */
@Composable
fun PanelRail(panel: PanelTab?, onToggle: (PanelTab) -> Unit, modifier: Modifier = Modifier) {
    NeuSurface(shape = NeuPillShape, elevation = 4.dp, inset = true, modifier = modifier.testTag("panel_rail")) {
        Column(
            Modifier.padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PanelMenu.TABS.forEach { tab ->
                PanelButton(
                    PanelMenu.icon(tab), stringResource(PanelMenu.labelRes(tab)), panel == tab, { onToggle(tab) },
                    vertical = true, anchor = PanelMenu.anchor(tab), tag = "panel_btn_${tab.name}", wide = true,
                )
            }
        }
    }
}

/** Mod düğmesi görünümünde, adı her zaman yazan düğme (dikeyde ad simgenin altında). */
@Composable
private fun PanelButton(
    icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit,
    vertical: Boolean, anchor: String, tag: String, wide: Boolean = false,
) {
    val colors = NeuTheme.colors
    val help = com.karapp.drawguidance.ui.components.LocalToolHelp.current
    val click = if (help != null) {
        Modifier.toolHelpClickable(help, label, true, Role.Tab, null, showName = false, onClick = onClick).semantics { this.selected = selected }
    } else Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick)
    val tint = if (selected) colors.accent else colors.contentMuted
    com.karapp.drawguidance.ui.components.ToolHold {
        Box(
            Modifier
                .then(if (vertical) Modifier.width(if (wide) 72.dp else 52.dp).height(52.dp) else Modifier.height(40.dp))
                .then(if (selected) Modifier.neumorphic(colors, NeuPillShape, elevation = 4.dp) else Modifier)
                .clip(NeuPillShape)
                .then(click)
                .semantics { contentDescription = label }
                .testTag(tag)
                .tutorialAnchor(anchor)
                .then(if (!vertical) Modifier.padding(horizontal = 10.dp) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val text: @Composable () -> Unit = {
                Text(
                    com.karapp.drawguidance.ui.components.shortLabel(label),
                    style = if (vertical) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    color = tint, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = if (vertical) (if (wide) 68.dp else 50.dp) else 120.dp)
                        .clearAndSetSemantics { testTag = com.karapp.drawguidance.ui.components.INLINE_LABEL_PREFIX + label },
                )
            }
            if (vertical) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)); text()
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)); text()
            }
        }
    }
}

/** v47 Özellikler alanı boşken (seçili aracın ayarı yok). */
@Composable
fun PropertiesEmpty() {
    val colors = NeuTheme.colors
    Column(
        Modifier.padding(horizontal = 8.dp, vertical = 24.dp).testTag("properties_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Tune, null, tint = colors.contentMuted.copy(alpha = 0.6f), modifier = Modifier.size(36.dp))
        Text(
            stringResource(R.string.v47_properties_empty), style = MaterialTheme.typography.bodyMedium,
            color = colors.contentMuted, textAlign = TextAlign.Center,
        )
    }
}
