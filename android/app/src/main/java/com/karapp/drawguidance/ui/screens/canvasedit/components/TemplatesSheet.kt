package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.HorizontalSplit
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.comic.ComicTemplate
import com.karapp.drawguidance.content.comic.ComicTemplates
import com.karapp.drawguidance.content.comic.PanelPage
import com.karapp.drawguidance.content.comic.PanelShape
import com.karapp.drawguidance.content.comic.TemplateGroup
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.ui.components.LabeledValueSlider
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.state.PanelSets
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

/** v36 Şablonlar penceresinin sekmeleri: şablon seçme ve sayfadaki panelleri düzenleme. */
enum class TemplatesTab { PICK, EDIT }

/**
 * v36 Şablonlar penceresi. Şablonlar sekmesi: tek, iki, üç, dört, altı panel; dikey webtoon; manga; türlere göre
 * düzenler (sayfanın oranında önizleme). Seçilen şablonun panelleri sayfada kurulur ve Paneller sekmesine geçilir:
 * sayfa haritası (numaralı paneller, dokununca seçilir), seçili panelin genişliği/yüksekliği, sırası (öne/arkaya),
 * yanına/altına panel ekleme ve silme; bütün sayfa için panel aralığı, kenar boşluğu ve sona panel ekleme.
 */
@Composable
fun TemplatesSheet(
    pageWidth: Int,
    pageHeight: Int,
    panels: PanelSets.Found?,
    initialLeaf: Int?,
    onPick: (String) -> Unit,
    onEdit: (coalesce: String?, edit: (PanelPage) -> PanelPage?) -> Unit,
    onAdd: (leaf: Int?, rows: Boolean) -> Int?,
    onEndCoalesce: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NeuTheme.colors
    var tab by rememberSaveable { mutableStateOf(if (panels != null && initialLeaf != null) TemplatesTab.EDIT else TemplatesTab.PICK) }
    var group by rememberSaveable { mutableStateOf<TemplateGroup?>(null) }
    var leaf by rememberSaveable { mutableStateOf(initialLeaf) }
    val aspect = (pageWidth.toFloat() / pageHeight.coerceAtLeast(1)).coerceIn(0.3f, 3f)
    ComicSheetFrame(stringResource(R.string.templates_title), stringResource(R.string.templates_hint), "templates_sheet", onDismiss) { columns ->
        PillTabs(
            listOf(
                Triple(TemplatesTab.PICK, stringResource(R.string.templates_tab_pick), Icons.Filled.AutoAwesomeMosaic),
                Triple(TemplatesTab.EDIT, stringResource(R.string.templates_tab_edit), Icons.Filled.Dashboard),
            ),
            tab, tagPrefix = "templates_tab_",
        ) { tab = it; onEndCoalesce() }
        when (tab) {
            TemplatesTab.PICK -> {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("tpl_groups"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuChip(stringResource(R.string.shape_filter_all), group == null, onClick = { group = null }, modifier = Modifier.testTag("tpl_group_all"))
                    TemplateGroup.entries.forEach { g ->
                        NeuChip(I18n.tr(g.title), group == g, onClick = { group = g }, modifier = Modifier.testTag("tpl_group_${g.id}"))
                    }
                }
                if (panels != null) {
                    Text(stringResource(R.string.templates_replace_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, modifier = Modifier.testTag("tpl_replace_hint"))
                }
                Grid(ComicTemplates.inGroup(group), columns) { t, m ->
                    ComicTile(I18n.tr(t.title), false, {
                        onPick(t.id)
                        leaf = t.page.leafIds().firstOrNull()
                        tab = TemplatesTab.EDIT
                    }, m, "tpl_${t.id}", premium = com.karapp.drawguidance.data.billing.PremiumFeature.COMIC_TEMPLATES.takeIf { com.karapp.drawguidance.data.billing.PremiumRules.isAdvancedTemplate(t) }) {
                        TemplatePreview(t, aspect, Modifier.height(96.dp))
                    }
                }
            }
            TemplatesTab.EDIT -> PanelEditor(panels, aspect, pageWidth, pageHeight, leaf, { leaf = it }, onEdit, onAdd)
        }
    }
}

/** Şablonun küçük önizlemesi: sayfa oranında, panel numaralarıyla. */
@Composable
private fun TemplatePreview(t: ComicTemplate, aspect: Float, modifier: Modifier) {
    val ink = NeuTheme.colors.content
    val shapes = remember(t.id, aspect) { t.page.shapes(aspect * 1000f, 1000f) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.height(96.dp).aspectRatio(aspect).semantics { contentDescription = t.id }) {
            drawPage(shapes, size.height / 1000f, ink, null, 1.3.dp.toPx() * t.line)
        }
    }
}

private fun DrawScope.drawPage(shapes: List<PanelShape>, k: Float, ink: Color, selected: Int?, line: Float, accent: Color = ink) {
    drawRect(Color.White, size = size)
    for (s in shapes) {
        val path = Path()
        path.moveTo(s.x(0) * k, s.y(0) * k)
        for (i in 1 until s.size) path.lineTo(s.x(i) * k, s.y(i) * k)
        path.close()
        if (s.id == selected) drawPath(path, accent.copy(alpha = 0.18f), style = Fill)
        else if (s.inset) drawPath(path, Color.White, style = Fill)
        drawPath(path, if (s.id == selected) accent else ink, style = Stroke(width = if (s.id == selected) line * 1.8f else line))
    }
}

/** Sayfadaki şablonun panellerini düzenleme. */
@Composable
private fun PanelEditor(
    panels: PanelSets.Found?,
    aspect: Float,
    pageWidth: Int,
    pageHeight: Int,
    leaf0: Int?,
    onLeaf: (Int) -> Unit,
    onEdit: (coalesce: String?, edit: (PanelPage) -> PanelPage?) -> Unit,
    onAdd: (leaf: Int?, rows: Boolean) -> Int?,
) {
    val colors = NeuTheme.colors
    if (panels == null) {
        Text(stringResource(R.string.templates_edit_empty), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted, modifier = Modifier.testTag("tpl_edit_empty"))
        return
    }
    val page = panels.page
    val order = page.leafIds()
    val leaf = leaf0?.takeIf { it in order } ?: order.firstOrNull() ?: return
    val w = pageWidth.toFloat(); val h = pageHeight.toFloat()
    val shapes = remember(page, w, h) { page.shapes(w, h) }
    val measurer = rememberTextMeasurer()
    val ink = colors.content
    val accent = colors.accent
    // Sayfa haritası: dokunulan panel seçilir
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.height(220.dp).aspectRatio(aspect).testTag("tpl_map")
                .pointerInput(shapes) {
                    detectTapGestures { p ->
                        val k = size.height / h
                        val hit = shapes.lastOrNull { it.contains(p.x / k, p.y / k) }
                        if (hit != null) onLeaf(hit.id)
                    }
                },
        ) {
            val k = size.height / h
            drawPage(shapes, k, ink, leaf, 1.5.dp.toPx(), accent)
            for (s in shapes) {
                val b = s.bounds()
                val label = measurer.measure(s.number.toString(), TextStyle(fontSize = 13.sp, color = if (s.id == leaf) accent else ink))
                drawText(label, topLeft = Offset((b[0] + b[2]) / 2f * k - label.size.width / 2f, (b[1] + b[3]) / 2f * k - label.size.height / 2f))
            }
        }
    }
    // Panel seçimi (numaralar)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("tpl_panels"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shapes.forEach { s ->
            NeuChip(s.number.toString(), s.id == leaf, onClick = { onLeaf(s.id) }, modifier = Modifier.width(48.dp).testTag("tpl_panel_${s.number}"))
        }
    }
    val number = shapes.firstOrNull { it.id == leaf }?.number ?: 1
    Section(stringResource(R.string.templates_panel, number), "tpl_selected") {
        page.widthOf(leaf)?.let { part ->
            Tagged("tpl_width") {
                LabeledValueSlider(stringResource(R.string.templates_width), "%${(part * 100).roundToInt()}", part, PanelPage.MIN_PART..PanelPage.MAX_PART) { v ->
                    onEdit("panel-width-${panels.set}-$leaf") { it.setWidth(leaf, (v * 100f).roundToInt() / 100f) }
                }
            }
        }
        page.heightOf(leaf)?.let { part ->
            Tagged("tpl_height") {
                LabeledValueSlider(stringResource(R.string.templates_height), "%${(part * 100).roundToInt()}", part, PanelPage.MIN_PART..PanelPage.MAX_PART) { v ->
                    onEdit("panel-height-${panels.set}-$leaf") { it.setHeight(leaf, (v * 100f).roundToInt() / 100f) }
                }
            }
        }
        // Sıra: okuma sırasında öne / arkaya (panelin içindeki çizimler de taşınır)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NeuButton(
                stringResource(R.string.templates_move_prev), onClick = { onEdit(null) { it.move(leaf, forward = false) } },
                modifier = Modifier.weight(1f).testTag("tpl_move_prev"), enabled = page.move(leaf, false) != null, icon = Icons.AutoMirrored.Filled.ArrowBack,
            )
            NeuButton(
                stringResource(R.string.templates_move_next), onClick = { onEdit(null) { it.move(leaf, forward = true) } },
                modifier = Modifier.weight(1f).testTag("tpl_move_next"), enabled = page.move(leaf, true) != null, icon = Icons.AutoMirrored.Filled.ArrowForward,
            )
        }
        val canSplit = !page.isInset(leaf)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NeuButton(
                stringResource(R.string.templates_add_side), onClick = { onAdd(leaf, false)?.let(onLeaf) },
                modifier = Modifier.weight(1f).testTag("tpl_add_side"), enabled = canSplit, icon = Icons.Filled.VerticalSplit,
            )
            NeuButton(
                stringResource(R.string.templates_add_below), onClick = { onAdd(leaf, true)?.let(onLeaf) },
                modifier = Modifier.weight(1f).testTag("tpl_add_below"), enabled = canSplit, icon = Icons.Filled.HorizontalSplit,
            )
        }
        NeuButton(
            stringResource(R.string.templates_delete), onClick = {
                val next = order.getOrNull(order.indexOf(leaf) + 1) ?: order.getOrNull(order.indexOf(leaf) - 1)
                onEdit(null) { it.remove(leaf) }
                next?.let(onLeaf)
            },
            modifier = Modifier.fillMaxWidth().testTag("tpl_delete"), icon = Icons.Filled.Delete, contentColor = colors.danger,
        )
    }
    Section(stringResource(R.string.templates_page), "tpl_page") {
        Tagged("tpl_gutter") {
            LabeledValueSlider(stringResource(R.string.templates_gutter), "%${(page.gutter * 1000).roundToInt() / 10f}", page.gutter, PanelPage.MIN_GUTTER..PanelPage.MAX_GUTTER) { v ->
                onEdit("panel-gutter-${panels.set}") { it.withGutter((v * 1000f).roundToInt() / 1000f) }
            }
        }
        Tagged("tpl_margin") {
            LabeledValueSlider(stringResource(R.string.templates_margin), "%${(page.margin * 1000).roundToInt() / 10f}", page.margin, PanelPage.MIN_MARGIN..PanelPage.MAX_MARGIN) { v ->
                onEdit("panel-margin-${panels.set}") { it.withMargin((v * 1000f).roundToInt() / 1000f) }
            }
        }
        NeuButton(
            stringResource(R.string.templates_add_end), onClick = { onAdd(null, true)?.let(onLeaf) },
            modifier = Modifier.fillMaxWidth().testTag("tpl_add_end"), icon = Icons.Filled.AddBox,
        )
    }
}
