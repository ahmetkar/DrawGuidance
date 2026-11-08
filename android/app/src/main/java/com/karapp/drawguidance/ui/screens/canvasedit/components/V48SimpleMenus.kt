package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.tutorialAnchor

/**
 * v48 basit resim ve basit not alma, dikey ekran: üç menü alt alta. Her düğmede simge ve adı yan yana (gelişmiş
 * modların dikey ekranındaki gibi). v49: her menü tek satır; satır ekrana sığmazsa yatay kaydırılır (alt satıra
 * sarmaz, gruplama yok). Çiz menüsünde geri al / yinele satırın başında sabit kalır (kaydırılmaz).
 * 1. Ekle: şekil, yazı, formül, diyagram, paket, panel, arka plan, resim, 3B.
 * 2. Çiz: geri al, yinele, kalem, silgi, boya, damlalık, cetvel, kalıp.
 * 3. Seç ve düzenle: seç, taşı, şekil düzenle, dikdörtgen seçim, genişlet; Elemanlar, Katmanlar, Özellikler.
 */
object SimpleMenus {
    enum class Menu(val tag: String) { ADD("simple_menu_add"), DRAW("simple_menu_draw"), EDIT("simple_menu_edit") }

    private val ADD_IDS = setOf("shape", "text", "formula", "diagram", "packs", "panels", "background", "image", "model3d")
    private val DRAW_IDS = setOf("brush", "eraser", "paint", "paint_brush", "eyedropper", "ruler", "template")

    /** Aracın menüsü (bilinmeyen araçlar düzen menüsüne). */
    fun menuOf(id: String): Menu = when (id) {
        in ADD_IDS -> Menu.ADD
        in DRAW_IDS -> Menu.DRAW
        else -> Menu.EDIT
    }

    /** Açılır alanın düğmeleri düzen menüsünün sonunda. */
    private val PANEL_IDS = listOf("elements", "layers", "brush_settings")

    /** Araçları menülere ayırır (sıraları korunur; Elemanlar, Katmanlar, Özellikler düzen menüsünün sonunda). */
    fun <T> split(items: List<T>, id: (T) -> String): Map<Menu, List<T>> =
        Menu.entries.associateWith { m -> items.filter { menuOf(id(it)) == m }.sortedBy { if (id(it) in PANEL_IDS) 1 else 0 } }
}

@Composable
fun SimpleMenuColumn(
    items: List<SimpleTool>,
    canUndo: Boolean,
    onUndo: () -> Unit,
    canRedo: Boolean,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val history = listOf(
        SimpleTool("undo", Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo), false, onUndo, canUndo),
        SimpleTool("redo", Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo), false, onRedo, canRedo),
    )
    val menus = SimpleMenus.split(items) { it.id }
    Column(modifier.fillMaxWidth().testTag("simple_tool_row"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SimpleMenus.Menu.entries.forEach { menu ->
            // Çiz menüsünde geri al / yinele satırın başında sabit; ötekiler kaydırılan bölümde
            val fixed = if (menu == SimpleMenus.Menu.DRAW) history else emptyList()
            val scrollable = menus.getValue(menu)
            if (fixed.isEmpty() && scrollable.isEmpty()) return@forEach
            @Composable
            fun pill(item: SimpleTool) {
                val anchor = when (item.id) { "undo" -> TutorialAnchor.UNDO; "redo" -> TutorialAnchor.REDO; else -> TutorialAnchor.simple(item.id) }
                SimpleToolPill(item, Modifier.testTag("simple_tool_${item.id}").tutorialAnchor(anchor))
            }
            NeuSurface(modifier = Modifier.fillMaxWidth(), elevation = 4.dp, inset = true) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).testTag(menu.tag),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    fixed.forEach { pill(it) }
                    // Tek satır; sığmazsa yatay kaydırılır (alt satıra sarmaz)
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        scrollable.forEach { pill(it) }
                    }
                }
            }
        }
    }
}

/**
 * v48 Taşı ve Genişlet ayrı düğmeler: Taşı bir eleman seçiliyken, Genişlet tuval seçiliyken (arka plan seçili ya da
 * hiçbir eleman seçili değil) etkin. Etkin mod kendi düğmesini kapatmaz (seçim değişse de moddan çıkılabilsin).
 */
object MoveExpandRules {
    fun canvasTarget(hasSelection: Boolean, backgroundSelected: Boolean): Boolean = backgroundSelected || !hasSelection
    fun moveEnabled(hasSelection: Boolean, active: Boolean): Boolean = hasSelection || active
    fun expandEnabled(canvasTarget: Boolean, active: Boolean): Boolean = canvasTarget || active
}
