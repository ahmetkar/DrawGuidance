package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.West
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.DiagramBox
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.theme.NeuCardShape
import com.karapp.drawguidance.ui.theme.NeuFieldShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic
import com.karapp.drawguidance.ui.components.NeuSheet

/** Diyagram türü seçimi: seçilince Diyagram moduna geçilir, tuvalde sürüklenerek yerleştirilir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagramPickerModal(
    selected: DiagramKind?,
    onDismiss: () -> Unit,
    onSelect: (DiagramKind) -> Unit,
    /** Yeni diyagramların renk teması. */
    theme: String = "",
    onTheme: (String) -> Unit = {},
    /** v33 basit not almada yalnız bu diyagramlar (tek grup); null: bütün türler gruplu. */
    only: List<DiagramKind>? = null,
) {
    val colors = NeuTheme.colors
    NeuSheet(onDismiss) {
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding()) {
            val columns = (maxWidth / 130.dp).toInt().coerceIn(2, 4)
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).graphicsLayer()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.diagram_picker_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.diagram_picker_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                Text(stringResource(R.string.diagram_theme), style = MaterialTheme.typography.titleSmall, color = colors.contentMuted)
                DiagramThemeRow(theme, onTheme)
                // v40 zeminli / zeminsiz (saydam) seçimi
                DiagramBackgroundRow(theme, onTheme)
                // v28: türler gruplu (yapı ve akış, yazılım ve veri, nicel grafikler, ders ve sunum)
                val groups = if (only != null) listOf(R.string.diagram_cat_simple to only) else DiagramGroups
                groups.forEach { (title, kinds) ->
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = colors.contentMuted, modifier = Modifier.padding(top = 4.dp))
                    if (title == R.string.diagram_group_data) {
                        Text(stringResource(R.string.diagram_data_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                    }
                    kinds.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { kind ->
                                DiagramTile(kind, kind == selected, { onSelect(kind) }, Modifier.weight(1f), theme)
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** Diyagram seçicisinin grupları (her tür bir kez). */
internal val DiagramGroups: List<Pair<Int, List<DiagramKind>>> = listOf(
    R.string.diagram_group_structure to listOf(
        DiagramKind.FLOW, DiagramKind.PROCESS, DiagramKind.STATE, DiagramKind.SWIMLANE, DiagramKind.SEQUENCE, DiagramKind.TIMELINE,
        DiagramKind.ORG, DiagramKind.TREE, DiagramKind.HUB, DiagramKind.LOOP, DiagramKind.CYCLE, DiagramKind.NESTED, DiagramKind.LAYERS,
        DiagramKind.QUADRANT, DiagramKind.WARDLEY, DiagramKind.JOURNEY, DiagramKind.KANBAN, DiagramKind.STORY_MAP,
    ),
    R.string.diagram_group_software to listOf(
        DiagramKind.ARCHITECTURE, DiagramKind.ER, DiagramKind.UML_CLASS, DiagramKind.DB_SCHEMA, DiagramKind.DEPLOYMENT,
        DiagramKind.DEPENDENCY, DiagramKind.IT_STATE, DiagramKind.HIGH_LEVEL, DiagramKind.MEDALLION, DiagramKind.DATA_FLOW,
        DiagramKind.DP_INTEGRATION, DiagramKind.DP_SECURITY, DiagramKind.ARCH_DELTA, DiagramKind.EXPLODED, DiagramKind.AXONOMETRIC,
    ),
    R.string.diagram_group_data to listOf(
        DiagramKind.BAR, DiagramKind.WATERFALL, DiagramKind.LINE, DiagramKind.SCATTER, DiagramKind.RADAR, DiagramKind.POLAR,
        DiagramKind.TREEMAP, DiagramKind.HEATMAP, DiagramKind.SANKEY, DiagramKind.GANTT,
    ),
    R.string.diagram_group_teaching to listOf(
        DiagramKind.RELATION, DiagramKind.COMPARISON, DiagramKind.MIND_MAP, DiagramKind.CONCEPT_MAP, DiagramKind.PYRAMID,
        DiagramKind.VENN, DiagramKind.MATRIX, DiagramKind.FUNNEL, DiagramKind.FISHBONE, DiagramKind.KWL, DiagramKind.STEPS,
    ),
).let { groups ->
    // Listede unutulan tür olursa son gruba eklenir
    val listed = groups.flatMap { it.second }.toSet()
    val rest = DiagramKind.entries.filter { it !in listed }
    if (rest.isEmpty()) groups else groups.dropLast(1) + (groups.last().first to groups.last().second + rest)
}

@Composable
private fun DiagramTile(kind: DiagramKind, selected: Boolean, onClick: () -> Unit, modifier: Modifier, theme: String = "") {
    // v40 şekil ekleme ve diyagramlar Premium değil: v39 kilidi kaldırıldı
    androidx.compose.foundation.layout.Box(modifier) {
        val colors = NeuTheme.colors
        Column(
            modifier = Modifier.fillMaxWidth()
                .neumorphic(colors, NeuCardShape, elevation = if (selected) 3.dp else 5.dp, selected = selected)
                .clip(NeuCardShape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Gerçek diyagram çizimi (seçili temayla) küçük resim olarak
            Thumb("diagram:${kind.name}:$theme", if (selected) colors.accent else colors.content, Modifier.fillMaxWidth().height(64.dp)) { c, w, h, ink ->
                drawDiagramThumb(c, kind, theme, w, h, ink)
            }
            Text(
                diagramLabel(kind),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) colors.accent else colors.content,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Diyagram türünün küçük şeması (gerçek yerleşimiyle çizilir). */
@Composable
fun DiagramIcon(kind: DiagramKind, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val nodes = com.karapp.drawguidance.ui.drawing.DiagramRenderer.layout(
            kind, com.karapp.drawguidance.ui.drawing.DiagramRenderer.defaultNodes(kind),
        )
        val edges = com.karapp.drawguidance.ui.drawing.DiagramRenderer.defaultEdges(kind, nodes)
        val w = size.width
        val h = size.height
        val line = 1.3.dp.toPx()
        // Düğümlerin x, y değeri sol-üst köşedir
        edges.forEach { edge ->
            val a = nodes.firstOrNull { it.id == edge.from } ?: return@forEach
            val b = nodes.firstOrNull { it.id == edge.to } ?: return@forEach
            drawLine(
                color.copy(alpha = 0.55f),
                androidx.compose.ui.geometry.Offset((a.x + a.w / 2f) * w, (a.y + a.h / 2f) * h),
                androidx.compose.ui.geometry.Offset((b.x + b.w / 2f) * w, (b.y + b.h / 2f) * h),
                line,
            )
        }
        nodes.forEach { node ->
            val bw = (node.w * w).coerceAtLeast(6f)
            val bh = (node.h * h).coerceAtLeast(5f)
            drawRoundRect(
                color,
                topLeft = androidx.compose.ui.geometry.Offset(node.x * w, node.y * h),
                size = androidx.compose.ui.geometry.Size(bw, bh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bh * 0.35f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = line),
            )
        }
    }
}

/**
 * Tuval üstü diyagram düzenleme paneli: odaktaki adımın (ya da okun) hemen yanında çizim alanının üstünde
 * durur. Adımda yazı, sonrasına adım ekleme, ok ekleme (hedef adıma dokunulur), kutu biçimi ve silme;
 * okta yazı ve silme.
 */
@Composable
fun DiagramFocusPanel(
    anchor: androidx.compose.ui.geometry.Rect,
    areaWidthPx: Float,
    areaHeightPx: Float,
    focusKey: Any,
    text: String,
    isNode: Boolean,
    box: DiagramBox?,
    linking: Boolean,
    onText: (String) -> Unit,
    onAddStep: () -> Unit,
    onLink: () -> Unit,
    onBox: (DiagramBox) -> Unit,
    onDelete: () -> Unit,
    /** Okta: bağlandığı taraflar ve değiştirme, yönü çevirme. */
    fromSide: com.karapp.drawguidance.data.model.DiagramSide = com.karapp.drawguidance.data.model.DiagramSide.AUTO,
    toSide: com.karapp.drawguidance.data.model.DiagramSide = com.karapp.drawguidance.data.model.DiagramSide.AUTO,
    onSides: (from: com.karapp.drawguidance.data.model.DiagramSide?, to: com.karapp.drawguidance.data.model.DiagramSide?) -> Unit = { _, _ -> },
    onFlip: () -> Unit = {},
    /** Alttaki mod çubuğu dahil tüm alanın yüksekliği: panel hiçbir yere sığmazsa çubuğun üstüne taşabilir. */
    fullHeightPx: Float = 0f,
    /** Adımın/okun rengi (bölgeye ayrı renk). */
    onColor: () -> Unit = {},
    /** Adımın vurgusu (null: vurgulanamaz) ve değiştirme. */
    focal: Boolean? = null,
    onFocal: () -> Unit = {},
    /** Nicel diyagramda değerlerin yazıdan okunduğu ipucu. */
    dataHint: Boolean = false,
) {
    val colors = NeuTheme.colors
    val density = androidx.compose.ui.platform.LocalDensity.current
    var value by remember(focusKey) { mutableStateOf(text) }
    // Yazı dışarıdan değişirse (geri alma) alan da güncellenir
    androidx.compose.runtime.LaunchedEffect(text) { if (text != value) value = text }
    var panelSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var boxMenu by remember { mutableStateOf(false) }
    // Adımın altındaki ok tutamağı (+) panelin altında kalmasın: adımda boşluk tutamağın dokunma payı kadar
    val gap = with(density) { (if (isNode) 26.dp else 12.dp).toPx() }
    val margin = with(density) { 8.dp.toPx() }
    // Alçak ama geniş alanda (yatay telefon) ok tarafları tek satırda: panel mod çubuğunun üstüne sığar
    val sidesInOneRow = !isNode && areaHeightPx < with(density) { 240.dp.toPx() } && areaWidthPx > with(density) { 600.dp.toPx() }
    val position = focusPanelPosition(
        anchor, panelSize.width.toFloat(), panelSize.height.toFloat(), areaWidthPx, areaHeightPx,
        fullHeightPx.takeIf { it > 0f } ?: areaHeightPx, gap, margin,
    )
    val x = position.x
    val y = position.y
    NeuSurface(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.coerceAtLeast(margin).toInt()) }
            .widthIn(max = if (sidesInOneRow) 560.dp else 340.dp)
            .onSizeChanged { panelSize = it }
            .testTag("diagram_focus_panel"),
        elevation = 10.dp,
    ) {
        val textField: @Composable (Modifier) -> Unit = { fieldModifier ->
            Box(
                fieldModifier
                    .neumorphic(colors, NeuFieldShape, elevation = 3.dp, inset = true)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = { value = it; onText(it) },
                    // v28: adımda çok satır (başlık, alt satırlar; ilk satır "[ETİKET]" üst etiket)
                    singleLine = !isNode,
                    maxLines = if (isNode) 4 else 1,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.content),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth().testTag("diagram_focus_text"),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                stringResource(if (isNode) R.string.diagram_step_hint else R.string.diagram_arrow_label),
                                style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted,
                            )
                        }
                        inner()
                    },
                )
            }
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isNode) {
                textField(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    NeuIconButton(Icons.Filled.Add, stringResource(R.string.diagram_add_step), onAddStep, size = 38.dp, tint = colors.accent)
                    NeuIconButton(
                        Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.diagram_add_arrow), onLink,
                        size = 38.dp, selected = linking, tint = colors.accent,
                    )
                    Box {
                        NeuIconButton(Icons.Filled.Tune, stringResource(R.string.diagram_box_shape), { boxMenu = true }, size = 38.dp, selected = boxMenu)
                        androidx.compose.material3.DropdownMenu(
                            expanded = boxMenu,
                            onDismissRequest = { boxMenu = false },
                            modifier = Modifier.background(colors.background),
                        ) {
                            DiagramBox.entries.forEach { option ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(boxLabel(option), color = if (option == box) colors.accent else colors.content) },
                                    onClick = { boxMenu = false; onBox(option) },
                                )
                            }
                        }
                    }
                    NeuIconButton(Icons.Filled.Palette, stringResource(R.string.diagram_color), onColor, size = 38.dp, tint = colors.accent)
                    if (focal != null) {
                        NeuIconButton(
                            if (focal) Icons.Filled.Star else Icons.Filled.StarBorder, stringResource(R.string.diagram_focal), onFocal,
                            size = 38.dp, selected = focal, tint = colors.accent,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    NeuIconButton(Icons.Filled.Delete, stringResource(R.string.diagram_delete_step), onDelete, size = 38.dp, tint = colors.danger)
                }
                if (dataHint) {
                    Text(
                        stringResource(R.string.diagram_data_hint), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted,
                        modifier = Modifier.widthIn(max = 320.dp),
                    )
                }
            } else {
                // Okta yazı, yön çevirme ve silme tek satırda: panel oku daha az kapatır
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    textField(Modifier.weight(1f))
                    NeuIconButton(Icons.Filled.SwapHoriz, stringResource(R.string.diagram_flip_edge), onFlip, size = 38.dp, tint = colors.accent)
                    NeuIconButton(Icons.Filled.Palette, stringResource(R.string.diagram_color), onColor, size = 38.dp, tint = colors.accent)
                    NeuIconButton(Icons.Filled.Delete, stringResource(R.string.diagram_delete_arrow), onDelete, size = 38.dp, tint = colors.danger)
                }
            }
            if (!isNode) {
                // Okun adımlara bağlandığı taraflar: otomatik ya da üst/sağ/alt/sol
                if (sidesInOneRow) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SideRow(stringResource(R.string.diagram_edge_from), fromSide) { onSides(it, null) }
                        SideRow(stringResource(R.string.diagram_edge_to), toSide) { onSides(null, it) }
                    }
                } else {
                    SideRow(stringResource(R.string.diagram_edge_from), fromSide) { onSides(it, null) }
                    SideRow(stringResource(R.string.diagram_edge_to), toSide) { onSides(null, it) }
                }
            }
        }
    }
}

@Composable
private fun SideRow(
    label: String,
    selected: com.karapp.drawguidance.data.model.DiagramSide,
    onPick: (com.karapp.drawguidance.data.model.DiagramSide) -> Unit,
) {
    val colors = NeuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.contentMuted, modifier = Modifier.widthIn(min = 44.dp))
        com.karapp.drawguidance.data.model.DiagramSide.entries.forEach { side ->
            NeuIconButton(sideIcon(side), "$label: ${sideLabel(side)}", { onPick(side) }, size = 34.dp, selected = side == selected)
        }
    }
}

private fun sideIcon(side: com.karapp.drawguidance.data.model.DiagramSide) = when (side) {
    com.karapp.drawguidance.data.model.DiagramSide.AUTO -> Icons.Filled.AutoAwesome
    com.karapp.drawguidance.data.model.DiagramSide.TOP -> Icons.Filled.ArrowUpward
    com.karapp.drawguidance.data.model.DiagramSide.RIGHT -> Icons.Filled.East
    com.karapp.drawguidance.data.model.DiagramSide.BOTTOM -> Icons.Filled.ArrowDownward
    com.karapp.drawguidance.data.model.DiagramSide.LEFT -> Icons.Filled.West
}

@Composable
fun sideLabel(side: com.karapp.drawguidance.data.model.DiagramSide): String = stringResource(
    when (side) {
        com.karapp.drawguidance.data.model.DiagramSide.AUTO -> R.string.diagram_side_auto
        com.karapp.drawguidance.data.model.DiagramSide.TOP -> R.string.diagram_side_top
        com.karapp.drawguidance.data.model.DiagramSide.RIGHT -> R.string.diagram_side_right
        com.karapp.drawguidance.data.model.DiagramSide.BOTTOM -> R.string.diagram_side_bottom
        com.karapp.drawguidance.data.model.DiagramSide.LEFT -> R.string.diagram_side_left
    },
)

@Composable
fun boxLabel(box: DiagramBox): String = stringResource(
    when (box) {
        DiagramBox.ROUNDED -> R.string.box_rounded
        DiagramBox.RECT -> R.string.box_rect
        DiagramBox.DIAMOND -> R.string.box_diamond
        DiagramBox.CIRCLE -> R.string.box_circle
        DiagramBox.CYLINDER -> R.string.box_cylinder
        DiagramBox.PILL -> R.string.box_pill
        DiagramBox.NOTE -> R.string.box_note
        DiagramBox.HEXAGON -> R.string.box_hexagon
        DiagramBox.PARALLELOGRAM -> R.string.box_parallelogram
        DiagramBox.DOCUMENT -> R.string.box_document
        DiagramBox.CLOUD -> R.string.box_cloud
        DiagramBox.TRAPEZOID -> R.string.box_trapezoid
        DiagramBox.ELLIPSE -> R.string.box_ellipse
        DiagramBox.TRIANGLE -> R.string.box_triangle
        DiagramBox.CHEVRON -> R.string.box_chevron
        DiagramBox.TABLE -> R.string.box_table
        DiagramBox.GROUP -> R.string.box_group
        DiagramBox.LANE -> R.string.box_lane
        DiagramBox.COLUMN -> R.string.box_column
        DiagramBox.CARD -> R.string.box_card
        DiagramBox.DOT -> R.string.box_dot
        DiagramBox.LABEL -> R.string.box_label
        DiagramBox.BAR -> R.string.box_bar
        DiagramBox.CELL -> R.string.box_cell
        DiagramBox.SLAB -> R.string.box_slab
    },
)

/**
 * Odak panelinin yeri: önce adımın/okun altı, sonra üstü, sonra sağı ya da solu (alttaki mod çubuğuyla
 * çakışmadan, [areaHeightPx] içinde). Hiçbiri sığmazsa tüm alanda ([fullHeightPx]) alta ya da üste konur.
 */
internal fun focusPanelPosition(
    anchor: androidx.compose.ui.geometry.Rect,
    panelWidth: Float,
    panelHeight: Float,
    areaWidthPx: Float,
    areaHeightPx: Float,
    fullHeightPx: Float,
    gap: Float,
    margin: Float,
): androidx.compose.ui.geometry.Offset {
    val maxX = (areaWidthPx - panelWidth - margin).coerceAtLeast(margin)
    val centeredX = (anchor.center.x - panelWidth / 2f).coerceIn(margin, maxX)
    fun fitsVertically(y: Float, height: Float) = y >= margin && y + panelHeight <= height - margin
    val below = anchor.bottom + gap
    if (fitsVertically(below, areaHeightPx)) return androidx.compose.ui.geometry.Offset(centeredX, below)
    val above = anchor.top - gap - panelHeight
    if (fitsVertically(above, areaHeightPx)) return androidx.compose.ui.geometry.Offset(centeredX, above)
    if (panelHeight + 2 * margin <= areaHeightPx) {
        val sideY = (anchor.center.y - panelHeight / 2f).coerceIn(margin, areaHeightPx - margin - panelHeight)
        val right = anchor.right + gap
        val left = anchor.left - gap - panelWidth
        val rightFits = right + panelWidth <= areaWidthPx - margin
        val leftFits = left >= margin
        // Daha geniş boşluğu olan yana; sığmıyorsa o yana yaslanır (mod çubuğunun düğmeleri kapanmaz)
        val useRight = if (rightFits != leftFits) rightFits else areaWidthPx - anchor.right >= anchor.left
        val sideX = (if (useRight) right else left).coerceIn(margin, maxX)
        return androidx.compose.ui.geometry.Offset(sideX, sideY)
    }
    val y = if (below + panelHeight <= fullHeightPx - margin || anchor.top - gap - panelHeight < margin) below else above
    return androidx.compose.ui.geometry.Offset(centeredX, y.coerceAtLeast(margin))
}

/** Diyagram türünün küçük resmi: gerçek yerleşim ve temayla (yazılar küçük olduğundan boş bırakılır). */
fun drawDiagramThumb(canvas: android.graphics.Canvas, kind: DiagramKind, theme: String, w: Int, h: Int, ink: Int) {
    val renderer = com.karapp.drawguidance.ui.drawing.DiagramRenderer
    // Yazılar çizilmez ama değerler (grafikler) yazıda olduğundan yazılar korunur
    val nodes = renderer.layout(kind, renderer.defaultNodes(kind))
    val edges = renderer.defaultEdges(kind, nodes)
    val hex = String.format("#%06X", ink and 0xFFFFFF)
    val style = com.karapp.drawguidance.data.model.ShapeStyle(strokeColorHex = hex, strokeWidth = 1.2f)
    val spec = com.karapp.drawguidance.data.model.DiagramSpec(kind = kind, nodes = nodes, edges = edges, style = style, theme = theme)
    val pad = 3f
    val pts = listOf(
        com.karapp.drawguidance.data.model.StrokePoint(pad, pad), com.karapp.drawguidance.data.model.StrokePoint(w - pad, pad),
        com.karapp.drawguidance.data.model.StrokePoint(w - pad, h - pad), com.karapp.drawguidance.data.model.StrokePoint(pad, h - pad),
    )
    val stroke = com.karapp.drawguidance.data.model.Stroke(
        "thumb", pts, hex, com.karapp.drawguidance.data.model.BrushType.DIAGRAM, 1.2f, 1f, 0, diagram = spec,
    )
    renderer.draw(canvas, stroke, labels = false)
}
