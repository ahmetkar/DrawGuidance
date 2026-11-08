package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.tutorialAnchor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.formula.Formula
import com.karapp.drawguidance.content.graph.GraphKind
import com.karapp.drawguidance.content.graph.GraphParams
import com.karapp.drawguidance.content.graph.Graphs
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.screens.canvasedit.PartStyle
import com.karapp.drawguidance.ui.screens.canvasedit.TextSettings
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt
import com.karapp.drawguidance.ui.components.NeuSheet

/** Mod çubuğunda ya da seçim satırında bir düğme (ekran listeyi seçime/moda göre kurar). */
data class ModeAction(
    val label: String,
    val icon: ImageVector?,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val enabled: Boolean = true,
    /** Silme gibi geri dönüşü dikkat isteyen işlem (kırmızı). */
    val danger: Boolean = false,
    /** Erişilebilirlik açıklaması (kısa adın uzun hali; yoksa ad). */
    val description: String? = null,
    /** v45 tuval menülerinde öğenin test etiketi (yoksa etiketsiz). */
    val tag: String? = null,
)

// ---------------------------------------------------------------------------------------------------------------
// Formül düzenleme
// ---------------------------------------------------------------------------------------------------------------

/**
 * v16: formülün üstünde düzenleme (pencere açılmaz). Katsayı/bilinmeyen (yuva) düzenlenirken küçük yazı alanı o yuvanın
 * tam üstünde durur; formülün tamamı yazılırken alan formülün altında (yer yoksa üstünde) açılır. Yazdıkça tuvaldeki
 * formül güncellenir; klavyedeki "ileri" sonraki yuvaya geçer.
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.FormulaInlineEditor(
    formula: Stroke,
    slot: String?,
    viewport: com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState,
    areaWidthPx: Float,
    areaHeightPx: Float,
    onSlot: (String, String) -> Unit,
    onSource: (String) -> Unit,
    onNext: () -> Unit,
    onWhole: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = NeuTheme.colors
    val density = androidx.compose.ui.platform.LocalDensity.current
    val source = formula.formula?.source ?: ""
    val slots = remember(source) { Formula.slots(source) }
    val index = slots.indexOfFirst { it.name == slot }
    val hasNext = slot != null && index >= 0 && index < slots.size - 1
    val focus = remember { FocusRequester() }
    var value by remember(formula.id, slot) {
        val initial = if (slot != null) slots.firstOrNull { it.name == slot }?.value ?: "" else Formula.plain(source)
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial, androidx.compose.ui.text.TextRange(0, initial.length)))
    }
    LaunchedEffect(formula.id, slot) { runCatching { focus.requestFocus() } }
    // Konum: yuvanın (ya da formülün) ekrandaki kutusu
    val quad = if (slot != null) com.karapp.drawguidance.ui.drawing.FormulaRenderer.slotQuad(formula, slot)
    else formula.points.map { androidx.compose.ui.geometry.Offset(it.x, it.y) }
    if (quad == null || quad.size < 4) return
    val screen = quad.map { viewport.toScreen(it) }
    val left0 = screen.minOf { it.x }; val right0 = screen.maxOf { it.x }
    val top0 = screen.minOf { it.y }; val bottom0 = screen.maxOf { it.y }
    fun dp(v: Float) = with(density) { v.dp.toPx() }
    val margin = dp(8f)
    val buttons = if (slot != null) dp(40f) * (if (hasNext) 3 else 2) + dp(4f) * 3 else dp(44f)
    val fieldH: Float
    val fieldW: Float
    val left: Float
    val top: Float
    if (slot != null) {
        fieldH = ((bottom0 - top0) * 1.15f).coerceIn(dp(44f), dp(72f))
        fieldW = ((right0 - left0) + dp(28f)).coerceIn(dp(72f), (areaWidthPx - buttons - margin * 2).coerceAtLeast(dp(72f)))
        left = ((left0 + right0) / 2f - fieldW / 2f).coerceIn(margin, (areaWidthPx - fieldW - buttons - margin).coerceAtLeast(margin))
        top = ((top0 + bottom0) / 2f - fieldH / 2f).coerceIn(margin, (areaHeightPx - fieldH - margin).coerceAtLeast(margin))
    } else {
        fieldH = dp(52f)
        fieldW = (right0 - left0).coerceIn(dp(240f), (areaWidthPx - buttons - margin * 2).coerceAtLeast(dp(120f)))
        left = left0.coerceIn(margin, (areaWidthPx - fieldW - buttons - margin).coerceAtLeast(margin))
        val below = bottom0 + margin
        top = (if (below + fieldH <= areaHeightPx - margin) below else top0 - margin - fieldH).coerceAtLeast(margin)
    }
    val fontPx = if (slot != null) (fieldH * 0.5f).coerceIn(dp(15f), dp(32f)) else dp(17f)
    val description = if (slot != null) "Yuva $slot" else stringResource(R.string.formula_source)
    Row(
        Modifier
            .align(Alignment.TopStart)
            .offset { androidx.compose.ui.unit.IntOffset(left.roundToInt(), top.roundToInt()) }
            .testTag("formula_inline_editor"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NeuSurface(
            shape = RoundedCornerShape(10.dp),
            elevation = 6.dp,
            modifier = Modifier
                .width(with(density) { fieldW.toDp() })
                .heightIn(min = with(density) { fieldH.toDp() }),
        ) {
            Box(
                Modifier.border(2.dp, colors.accent, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = { next ->
                        val changed = next.text != value.text
                        value = next
                        if (changed) { if (slot != null) onSlot(slot, next.text) else onSource(next.text) }
                    },
                    singleLine = slot != null,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = hexToColor(formula.colorHex),
                        fontSize = with(density) { fontPx.toSp() },
                        fontFamily = if (slot != null) androidx.compose.ui.text.font.FontFamily.Serif else androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontStyle = if (slot != null) FontStyle.Italic else FontStyle.Normal,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = if (hasNext) androidx.compose.ui.text.input.ImeAction.Next else androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = description },
                )
            }
        }
        if (slot != null) {
            NeuIconButton(Icons.Filled.Functions, stringResource(R.string.formula_source_show), onWhole, size = 40.dp)
            if (hasNext) NeuIconButton(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.formula_next_slot), onNext, size = 40.dp)
        }
        NeuIconButton(Icons.Filled.Check, stringResource(R.string.formula_done), onDone, size = 40.dp, tint = colors.accent)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Şekil parçası özellikleri
// ---------------------------------------------------------------------------------------------------------------

/**
 * Şekil düzenlemede seçili parçaların (seçim yoksa tüm şeklin) özellikleri: çizgi rengi, dolgu (yok/renk),
 * kalınlık, çizgi biçimi (düz, kesik, noktalı, yok) ve saydamlık. Değişiklik hemen uygulanır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartStyleSheet(
    initial: PartStyle,
    partsCount: Int,
    onChange: (PartStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NeuTheme.colors
    var style by remember { mutableStateOf(initial) }
    var colorTarget by remember { mutableStateOf<String?>(null) }
    fun update(s: PartStyle, applied: PartStyle) { style = s; onChange(applied) }
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp).testTag("part_style_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.part_style_title), style = MaterialTheme.typography.titleLarge)
            Text(
                if (partsCount > 0) stringResource(R.string.part_style_parts, partsCount) else stringResource(R.string.part_style_all),
                style = MaterialTheme.typography.bodySmall, color = colors.contentMuted,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.part_style_stroke), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                ColorDot(style.strokeColorHex ?: "#1E1E1E", stringResource(R.string.part_style_stroke), { colorTarget = "stroke" })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.part_style_fill), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                val fill = style.fillColorHex?.takeIf { it.isNotEmpty() }
                if (fill != null) ColorDot(fill, stringResource(R.string.shape_fill_color), { colorTarget = "fill" })
                NeuSwitch(
                    checked = fill != null,
                    onCheckedChange = { on ->
                        val hex = if (on) (initial.fillColorHex?.takeIf { it.isNotEmpty() } ?: "#BBDEFB") else ""
                        update(style.copy(fillColorHex = hex), PartStyle(fillColorHex = hex))
                    },
                    modifier = Modifier.semantics { contentDescription = "Parça dolgusu" },
                )
            }
            Text(stringResource(R.string.part_style_line), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "solid" to R.string.line_solid, "dashed" to R.string.line_dashed,
                    "dotted" to R.string.line_dotted, "none" to R.string.line_none,
                ).forEach { (key, label) ->
                    NeuChip(stringResource(label), selected = style.line == key, onClick = { update(style.copy(line = key), PartStyle(line = key)) })
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val width = style.widthScale ?: 1f
                Text(stringResource(R.string.part_style_width, (width * 100).roundToInt()), style = MaterialTheme.typography.bodyMedium)
                NeuSlider(width.coerceIn(0.25f, 4f), { v -> update(style.copy(widthScale = v), PartStyle(widthScale = v)) }, 0.25f..4f)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val alpha = style.alpha ?: 1f
                Text(stringResource(R.string.part_style_alpha, (alpha * 100).roundToInt()), style = MaterialTheme.typography.bodyMedium)
                NeuSlider(alpha.coerceIn(0.1f, 1f), { v -> update(style.copy(alpha = v), PartStyle(alpha = v)) }, 0.1f..1f)
            }
            NeuButton(stringResource(R.string.move_done), onDismiss, Modifier.fillMaxWidth(), primary = true, icon = Icons.Filled.Check)
        }
    }
    colorTarget?.let { target ->
        PartColorSheet(
            title = stringResource(if (target == "stroke") R.string.part_style_stroke else R.string.shape_fill_color),
            colorHex = (if (target == "stroke") style.strokeColorHex else style.fillColorHex?.takeIf { it.isNotEmpty() }) ?: "#1E1E1E",
            onColorChange = { hex ->
                if (target == "stroke") update(style.copy(strokeColorHex = hex), PartStyle(strokeColorHex = hex))
                else update(style.copy(fillColorHex = hex), PartStyle(fillColorHex = hex))
            },
            onDismiss = { colorTarget = null },
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Grafik düzenleme
// ---------------------------------------------------------------------------------------------------------------

/**
 * Grafik aracının ayarları (şekil üstünde sayılara dokunmanın yanında): desen (eğrinin tekrarı / öğe sayısı) ve
 * nokta (çentik) sayısı artır/azalt, sayılar, ızgara, eğrinin altını boyama.
 */
@Composable
fun GraphControls(params: GraphParams, onChange: ((GraphParams) -> GraphParams) -> Unit, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    val chart = params.kind in setOf(GraphKind.BAR, GraphKind.LINE, GraphKind.PIE, GraphKind.SCATTER, GraphKind.HISTOGRAM)
    // Tek kaydırılabilir satır: mod çubuğu tuvali fazla örtmesin (yönerge mod çubuğunun başlığında)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Stepper(
                label = if (chart) stringResource(R.string.graph_items, Graphs.itemCount(params)) else stringResource(R.string.graph_pattern, Graphs.patternValue(params)),
                onMinus = { onChange(Graphs::lessPattern) }, onPlus = { onChange(Graphs::morePattern) },
                minusDescription = stringResource(R.string.graph_pattern_less), plusDescription = stringResource(R.string.graph_pattern_more),
            )
            if (!chart || params.kind == GraphKind.LINE || params.kind == GraphKind.SCATTER || params.kind == GraphKind.BAR) {
                Stepper(
                    label = stringResource(R.string.graph_points, params.ticks),
                    onMinus = { onChange(Graphs::lessPoints) }, onPlus = { onChange(Graphs::morePoints) },
                    minusDescription = stringResource(R.string.graph_points_less), plusDescription = stringResource(R.string.graph_points_more),
                )
            }
            NeuChip(stringResource(R.string.graph_numbers), params.showNumbers, { onChange { it.copy(showNumbers = !it.showNumbers) } })
            if (params.kind != GraphKind.PIE) NeuChip(stringResource(R.string.graph_grid), params.grid, { onChange { it.copy(grid = !it.grid) } })
            if (params.kind == GraphKind.FUNCTION) {
                NeuChip(stringResource(R.string.graph_fill_under), params.fillUnder, { onChange { it.copy(fillUnder = !it.fillUnder) } })
                NeuChip(stringResource(R.string.graph_key_points), params.keyPoints, { onChange { it.copy(keyPoints = !it.keyPoints) } })
            }
        }
    }
}

@Composable
private fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit, minusDescription: String, plusDescription: String) {
    val colors = NeuTheme.colors
    NeuSurface(shape = NeuPillShape, elevation = 3.dp) {
        Row(Modifier.padding(horizontal = 4.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuIconButton(Icons.Filled.Remove, minusDescription, onMinus, size = 32.dp)
            Text(label, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge, color = colors.content, maxLines = 1)
            NeuIconButton(Icons.Filled.Add, plusDescription, onPlus, size = 32.dp)
        }
    }
}

/** Grafikte dokunulan sayı/değer/eksen adı/formül için giriş penceresi. */
@Composable
fun GraphValueDialog(tag: String, initial: String, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    val numeric = tag.startsWith("value:")
    val title = when {
        tag.startsWith("tick:") -> stringResource(R.string.graph_edit_tick)
        tag.startsWith("value:") -> stringResource(R.string.graph_edit_value)
        tag.startsWith("vlabel:") -> stringResource(R.string.graph_edit_label)
        tag == "formula" -> stringResource(R.string.graph_edit_formula)
        else -> stringResource(R.string.graph_edit_axis)
    }
    com.karapp.drawguidance.ui.components.TextInputSheet(
        title = title, initial = initial, label = title, onConfirm = onApply, onDismiss = onDismiss,
        hint = if (tag.startsWith("tick:")) stringResource(R.string.graph_edit_tick_hint) else null,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        allowBlank = !numeric, tag = "graph_value_sheet",
    )
}

// ---------------------------------------------------------------------------------------------------------------
// Vektör çizgisi (şekil düzenlemede Çizgiler aracı)
// ---------------------------------------------------------------------------------------------------------------

/**
 * Seçili çizginin eğimi ve kavisi: eğim derece olarak gösterilir; ±15°, yatay, dikey ve (kavisliyse) düzleştir.
 * Tutamaklarla da değiştirilebilir (ortası: kavis, ucunun ötesi: eğim).
 */
@Composable
fun EdgeControls(
    angle: Float,
    curved: Boolean,
    onTurn: (Float) -> Unit,
    onAngle: (Float) -> Unit,
    onStraighten: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    // Ekranda aşağı doğru y büyür: kullanıcıya matematikteki gibi (yukarı pozitif) eğim gösterilir
    var shown = -angle
    while (shown > 90f) shown -= 180f
    while (shown <= -90f) shown += 180f
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("edge_controls"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stepper(
            label = stringResource(R.string.edge_angle, shown.roundToInt()),
            onMinus = { onTurn(15f) }, onPlus = { onTurn(-15f) },
            minusDescription = stringResource(R.string.edge_turn_less), plusDescription = stringResource(R.string.edge_turn_more),
        )
        NeuChip(stringResource(R.string.edge_horizontal), kotlin.math.abs(shown) < 0.5f, { onAngle(0f) })
        NeuChip(stringResource(R.string.edge_vertical), kotlin.math.abs(kotlin.math.abs(shown) - 90f) < 0.5f, { onAngle(90f) })
        if (curved) NeuChip(stringResource(R.string.edge_straighten), false, onStraighten)
        Text(stringResource(R.string.edge_handles_hint), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, maxLines = 1)
    }
}

/**
 * v33 şekil düzenlemede nokta paneli: Noktalar aracının alt araçları (noktaları taşı / seç, nokta ekle, noktalar arası
 * çizgi çiz). Üst menüde yalnız Noktalar düğmesi durur; alt araç burada seçilir.
 */
@Composable
fun NodeToolControls(
    tool: com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool,
    onTool: (com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool) -> Unit,
    icon: (com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool) -> androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    val tools = listOf(
        com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.NODES to R.string.tool_node_move,
        com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.ADD_NODE to R.string.tool_add_node,
        com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.CONNECT to R.string.tool_connect,
    )
    // Üç alt araç kaydırmadan yan yana sığar (dar telefonda simgesiz, eşit genişlikte)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().testTag("node_tool_controls")) {
        val withIcons = maxWidth >= 400.dp
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tools.forEach { (t, res) ->
                val label = stringResource(res)
                NeuChip(
                    label, tool == t, { onTool(t) },
                    Modifier.weight(1f).semantics { contentDescription = label },
                    icon = if (withIcons) icon(t) else null,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Resim vektörleştirme
// ---------------------------------------------------------------------------------------------------------------

/**
 * v17 kenar vektörleştirme ayarları: çizgi bulma türü, ayrıntı, yumuşatma, kısa çizgileri atma, çizgi kalınlığı,
 * çizgi rengini resimden alma, resmi koru. Sonuç dolgusuz, düzenlenebilir çizgilerdir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VectorizeSheet(
    tracing: Boolean,
    /** v34 üst menüden seçilen tür: türler pencerede gösterilmez (null: türler seçilebilir). */
    mode: com.karapp.drawguidance.content.trace.EdgeMode? = null,
    onApply: (options: com.karapp.drawguidance.content.trace.EdgeOptions, lineWidth: Float, keepImage: Boolean) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    empty: Boolean = false,
) {
    val colors = NeuTheme.colors
    val fixedMode = mode
    var mode by remember(fixedMode) { mutableStateOf(fixedMode ?: com.karapp.drawguidance.content.trace.EdgeMode.AUTO) }
    var detail by remember { mutableStateOf(0.5f) }
    var smooth by remember { mutableStateOf(0.5f) }
    var minLength by remember { mutableStateOf(0.4f) }
    var width by remember { mutableStateOf(2f) }
    var colored by remember { mutableStateOf(false) }
    var keep by remember { mutableStateOf(true) }
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp).testTag("vectorize_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(
                    when (fixedMode) {
                        null -> R.string.vectorize_title
                        com.karapp.drawguidance.content.trace.EdgeMode.AUTO -> R.string.vectorize_direct_auto
                        com.karapp.drawguidance.content.trace.EdgeMode.EDGES -> R.string.vectorize_direct_edges
                        com.karapp.drawguidance.content.trace.EdgeMode.CENTERLINE -> R.string.vectorize_direct_lines
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(stringResource(R.string.vectorize_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            // v34 tür üst menüden seçildiyse burada yalnız ayarlar
            if (fixedMode == null) Text(stringResource(R.string.vectorize_mode), style = MaterialTheme.typography.bodyMedium)
            if (fixedMode == null) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    com.karapp.drawguidance.content.trace.EdgeMode.AUTO to R.string.vectorize_mode_auto,
                    com.karapp.drawguidance.content.trace.EdgeMode.EDGES to R.string.vectorize_mode_edges,
                    com.karapp.drawguidance.content.trace.EdgeMode.CENTERLINE to R.string.vectorize_mode_lines,
                ).forEach { (m, label) ->
                    NeuChip(stringResource(label), mode == m, { mode = m }, Modifier.testTag("vectorize_mode_${m.name.lowercase()}"))
                }
            }
            Text(
                stringResource(
                    when (mode) {
                        com.karapp.drawguidance.content.trace.EdgeMode.AUTO -> R.string.vectorize_mode_auto_hint
                        com.karapp.drawguidance.content.trace.EdgeMode.EDGES -> R.string.vectorize_mode_edges_hint
                        com.karapp.drawguidance.content.trace.EdgeMode.CENTERLINE -> R.string.vectorize_mode_lines_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall, color = colors.contentMuted,
            )
            Text(stringResource(R.string.vectorize_detail, (detail * 100).roundToInt()), style = MaterialTheme.typography.bodyMedium)
            NeuSlider(detail, { detail = it }, 0f..1f)
            Text(stringResource(R.string.vectorize_smooth, (smooth * 100).roundToInt()), style = MaterialTheme.typography.bodyMedium)
            NeuSlider(smooth, { smooth = it }, 0f..1f)
            Text(stringResource(R.string.vectorize_min_length, (minLength * 100).roundToInt()), style = MaterialTheme.typography.bodyMedium)
            NeuSlider(minLength, { minLength = it }, 0f..1f)
            Text(stringResource(R.string.vectorize_width, String.format(java.util.Locale.ROOT, "%.1f", width)), style = MaterialTheme.typography.bodyMedium)
            NeuSlider(width, { width = it }, 0.5f..8f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.vectorize_colored), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                NeuSwitch(colored, { colored = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.vectorize_keep), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                NeuSwitch(keep, { keep = it })
            }
            if (empty && !tracing) {
                Text(stringResource(R.string.vectorize_empty), style = MaterialTheme.typography.bodySmall, color = colors.danger)
            }
            if (tracing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.accent)
                    Text(stringResource(R.string.vectorize_running), Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel_button)) }
                }
            } else {
                NeuButton(
                    stringResource(R.string.vectorize_apply),
                    {
                        onApply(
                            com.karapp.drawguidance.content.trace.EdgeOptions(detail = detail, smooth = smooth, minLength = minLength, colored = colored, mode = mode),
                            width, keep,
                        )
                    },
                    Modifier.fillMaxWidth().testTag("vectorize_apply"), primary = true,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Katmanlar
// ---------------------------------------------------------------------------------------------------------------

/**
 * Katmanlar paneli: en üstteki katman listede en üstte. Katmana dokunmak onu etkin yapar (yeni elemanlar oraya
 * eklenir); göz gizler/gösterir, kilit kilitler; ⋮ menüsünde yeniden adlandır, yukarı/aşağı, seçimi buraya taşı, sil.
 */
@Composable
fun LayersPanelContent(
    layers: List<Layer>,
    active: String,
    selectionCount: Int,
    counts: Map<String, Int>,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onToggleVisible: (String) -> Unit,
    onToggleLocked: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onMove: (String, Int) -> Unit,
    onMoveSelection: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = NeuTheme.colors
    var renaming by remember { mutableStateOf<Layer?>(null) }
    var deleting by remember { mutableStateOf<Layer?>(null) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(4.dp).testTag("layers_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.layers_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        NeuButton(stringResource(R.string.layer_add), onAdd, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
        layers.asReversed().forEachIndexed { reversedIndex, layer ->
            val index = layers.size - 1 - reversedIndex
            val isActive = layer.id == active
            var menu by remember(layer.id) { mutableStateOf(false) }
            NeuSurface(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = false) { contentDescription = com.karapp.drawguidance.i18n.I18n.trf("Katman {0}", com.karapp.drawguidance.i18n.I18n.tr(layer.name)) },
                elevation = if (isActive) 2.dp else 5.dp,
                inset = isActive,
                onClick = { onSelect(layer.id) },
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            layer.name, style = MaterialTheme.typography.titleSmall, color = if (isActive) colors.accent else colors.content,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.layer_count, counts[layer.id] ?: 0) + if (isActive) " · " + stringResource(R.string.layer_active) else "",
                            style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, maxLines = 1,
                        )
                    }
                    // v23 katman sırası doğrudan düğmelerle (üstteki katmanın çizimleri tuvalde üstte)
                    NeuIconButton(
                        Icons.Filled.KeyboardArrowUp, stringResource(R.string.layer_up), { onMove(layer.id, 1) },
                        size = 30.dp, enabled = index < layers.size - 1,
                    )
                    Spacer(Modifier.width(4.dp))
                    NeuIconButton(
                        Icons.Filled.KeyboardArrowDown, stringResource(R.string.layer_down), { onMove(layer.id, -1) },
                        size = 30.dp, enabled = index > 0,
                    )
                    Spacer(Modifier.width(6.dp))
                    NeuIconButton(
                        if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        stringResource(if (layer.visible) R.string.layer_hide else R.string.layer_show),
                        { onToggleVisible(layer.id) }, size = 34.dp, selected = !layer.visible,
                    )
                    Spacer(Modifier.width(6.dp))
                    NeuIconButton(
                        if (layer.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        stringResource(if (layer.locked) R.string.layer_unlock else R.string.layer_lock),
                        { onToggleLocked(layer.id) }, size = 34.dp, selected = layer.locked,
                    )
                    Spacer(Modifier.width(2.dp))
                    Box {
                        NeuIconButton(Icons.Filled.MoreVert, stringResource(R.string.layer_menu, layer.name), { menu = true }, size = 34.dp)
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(colors.background)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.layer_rename)) }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                onClick = { menu = false; renaming = layer },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.layer_up)) }, leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, null) },
                                enabled = index < layers.size - 1, onClick = { menu = false; onMove(layer.id, 1) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.layer_down)) }, leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) },
                                enabled = index > 0, onClick = { menu = false; onMove(layer.id, -1) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.layer_move_selection, selectionCount)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                                enabled = selectionCount > 0 && !layer.locked && layer.visible,
                                onClick = { menu = false; onMoveSelection(layer.id) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.layer_delete), color = colors.danger) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = colors.danger) },
                                enabled = layers.size > 1, onClick = { menu = false; deleting = layer },
                            )
                        }
                    }
                }
            }
        }
    }
    renaming?.let { layer ->
        com.karapp.drawguidance.ui.components.TextInputSheet(
            title = stringResource(R.string.layer_rename), initial = layer.name, label = stringResource(R.string.layer_name),
            onConfirm = { onRename(layer.id, it); renaming = null }, onDismiss = { renaming = null },
        )
    }
    deleting?.let { layer ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = colors.background,
            title = { Text(stringResource(R.string.layer_delete_title, layer.name)) },
            text = { Text(stringResource(R.string.layer_delete_text, counts[layer.id] ?: 0)) },
            confirmButton = { TextButton(onClick = { onDelete(layer.id); deleting = null }) { Text(stringResource(R.string.delete_button), color = colors.danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel_button)) } },
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------
// El yazısı önerisi, stil satırı, kaydetme
// ---------------------------------------------------------------------------------------------------------------

/** v23 öneri balonundaki seçenek. */
internal class SuggestOption(
    val text: String, val icon: androidx.compose.ui.graphics.vector.ImageVector?, val cd: String, val tag: String, val action: () -> Unit,
    /** v22 benzerlik yüzdesi (şablon tahminlerinde). */
    val badge: String? = null,
    /** v39 Premium kilidi: öneri gösterilir, uygulamak kilitliyse "Premium gerekli" açılır. */
    val premium: com.karapp.drawguidance.data.billing.PremiumFeature? = null,
)

/**
 * v28 öneri balonunun sürüklenmesi: balonun solundaki tutamaktan ekranda istenen yere taşınır (yeri hatırlanır);
 * tutamağa çift dokununca balon yeniden kendiliğinden (önerilen çizimin üstünde) yerleşir.
 */
class SuggestDrag(val onDrag: (androidx.compose.ui.geometry.Offset) -> Unit, val onEnd: () -> Unit, val onReset: () -> Unit)

/** v28 öneri kutusunu taşıma tutamağı (sürükle; çift dokun: otomatik yere dön). */
@Composable
fun SuggestDragHandle(drag: SuggestDrag, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    val current by androidx.compose.runtime.rememberUpdatedState(drag)
    val cd = stringResource(R.string.suggest_move)
    Box(
        modifier.size(width = 30.dp, height = 40.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { current.onEnd() }, onDragCancel = { current.onEnd() },
                ) { change, delta -> change.consume(); current.onDrag(delta) }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { current.onReset() }) }
            .semantics { contentDescription = cd }
            .testTag("suggest_drag_handle"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.DragIndicator, null, tint = colors.contentMuted, modifier = Modifier.size(20.dp))
    }
}

/**
 * v23 öneri balonu iskeleti: başlık, kayan seçenekler ve kaymayan kapatma düğmeleri. v38 [anchor]: eğitimin bu balonu
 * bulduğu yer (yazı, şekil ve çizgi balonlarının ayrı yerleri).
 */
@Composable
internal fun SuggestBalloon(
    title: String,
    options: List<SuggestOption>,
    onDismiss: () -> Unit,
    onTurnOff: (() -> Unit)?,
    turnOffLabel: String,
    tag: String,
    modifier: Modifier = Modifier,
    drag: SuggestDrag? = null,
    anchor: String? = null,
) {
    val colors = NeuTheme.colors
    val access = com.karapp.drawguidance.ui.premium.LocalPremium.current
    // Ortak etiket (bütün öneri balonları): "handwriting_chip"; kendi etiketi içte
    Box(modifier.testTag("handwriting_chip").tutorialAnchor(anchor)) {
    NeuSurface(Modifier.testTag(tag), shape = RoundedCornerShape(18.dp), elevation = 8.dp) {
        Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (drag != null) SuggestDragHandle(drag, Modifier.padding(start = 2.dp))
            Row(
                Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()).padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
                options.forEachIndexed { i, o ->
                    val locked = access.locked(o.premium)
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (i == 0) colors.accent.copy(alpha = 0.16f) else colors.background)
                            .border(1.dp, colors.accent.copy(alpha = if (i == 0) 0.8f else 0.35f), RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { if (access.request(o.premium)) o.action() }
                            .semantics { contentDescription = o.cd }
                            .testTag(if (locked) o.tag + "_locked" else o.tag)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (o.icon != null) Icon(o.icon, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            Text(o.text, style = MaterialTheme.typography.titleSmall, color = if (i == 0) colors.accent else colors.content, maxLines = 1)
                            if (o.badge != null) Text(o.badge, style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, maxLines = 1)
                            if (locked) com.karapp.drawguidance.ui.premium.PremiumLockBadge(size = 14.dp)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onTurnOff != null) NeuIconButton(Icons.Filled.VisibilityOff, turnOffLabel, onTurnOff, size = 28.dp)
                NeuIconButton(Icons.Filled.Close, stringResource(R.string.handwriting_dismiss), onDismiss, size = 28.dp)
            }
        }
    }
    }
}

/**
 * v23 yazı önerisi balonu (şekil önerisinden ayrı): kalemle yazılanın tanınan hali ve seçenekleri; dokununca izler aynı
 * yerde ve renkte yazıya dönüşür.
 */
@Composable
fun TextSuggestionChip(
    suggestion: com.karapp.drawguidance.ui.screens.canvasedit.state.TextSuggestion,
    onAccept: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onTurnOff: (() -> Unit)? = null,
    drag: SuggestDrag? = null,
) {
    val texts = listOf(suggestion.text) + suggestion.alternatives
    SuggestBalloon(
        title = stringResource(R.string.text_suggest_title),
        options = texts.map { t -> SuggestOption(t, Icons.Filled.TextFields, "Öneri $t", "text_option", { onAccept(t) }) },
        onDismiss = onDismiss, onTurnOff = onTurnOff, turnOffLabel = stringResource(R.string.text_suggest_turn_off),
        tag = "text_suggest_chip", modifier = modifier, drag = drag,
        anchor = com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor.SUGGEST_TEXT,
    )
}

/**
 * v23 şekil önerisi balonu (yazı ve çizgi önerisinden ayrı). v38: birbirine yakın 4+ elemanın yerine konabilecek
 * şekiller (şablon veritabanı ve modun sinir ağı; benzerlik yüzdesiyle) ve hazır vektör şekiller; her seçenek dikkate
 * alınan bütün çizgileri tek düğmeyle değiştirir. İlk seçeneğin önizlemesi tuvalde kesik çizgiyle görünür.
 */
@Composable
fun ShapeSuggestionChip(
    suggestion: com.karapp.drawguidance.ui.screens.canvasedit.state.ShapeSuggestion,
    onAcceptTemplate: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onTurnOff: (() -> Unit)? = null,
    drag: SuggestDrag? = null,
) {
    val templateCd = stringResource(R.string.template_apply_cd, "%s")
    val solidLabel = stringResource(R.string.template_solid, "%s")
    val vectorLabel = stringResource(R.string.template_vector, "%s")
    val options = suggestion.templates.mapIndexed { i, fix ->
        val label = when {
            // v27 hazır vektör şekil (çizim tek şekille değiştirilir)
            fix.vector -> vectorLabel.replace("%s", shapeTitle(fix.solid ?: ""))
            fix.solid != null -> solidLabel.replace("%s", fix.name)
            else -> fix.name
        }
        SuggestOption(
            label, if (fix.vector) Icons.Filled.Interests else if (fix.solid != null) Icons.Filled.ViewInAr else Icons.Filled.Category, templateCd.replace("%s", label),
            "template_option_$i", { onAcceptTemplate(i) }, badge = "%" + (fix.confidence * 100f).roundToInt().coerceIn(0, 100),
            // v39 gelişmiş (çok parçalı, kütüphane, 3B) öneri gösterilir, uygulaması Premium
            premium = com.karapp.drawguidance.data.billing.PremiumFeature.SHAPE_SUGGEST_ADVANCED
                .takeIf { com.karapp.drawguidance.data.billing.PremiumRules.isAdvancedSuggestion(fix.vector, fix.solid, fix.strokes.size) },
        )
    }
    SuggestBalloon(
        title = stringResource(R.string.shape_suggest_title),
        options = options, onDismiss = onDismiss, onTurnOff = onTurnOff, turnOffLabel = stringResource(R.string.shape_suggest_turn_off),
        tag = "shape_suggest_chip", modifier = modifier, drag = drag,
        anchor = com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor.SUGGEST,
    )
}

/** v17: düzeltilen şeklin adı. */
fun fixLabel(kind: com.karapp.drawguidance.content.ink.InkShapeKind): Int = when (kind) {
    com.karapp.drawguidance.content.ink.InkShapeKind.LINE -> R.string.fix_line
    com.karapp.drawguidance.content.ink.InkShapeKind.POLYLINE -> R.string.fix_polyline
    com.karapp.drawguidance.content.ink.InkShapeKind.CIRCLE -> R.string.fix_circle
    com.karapp.drawguidance.content.ink.InkShapeKind.ELLIPSE -> R.string.fix_ellipse
    com.karapp.drawguidance.content.ink.InkShapeKind.TRIANGLE -> R.string.fix_triangle
    com.karapp.drawguidance.content.ink.InkShapeKind.RECTANGLE -> R.string.fix_rectangle
    com.karapp.drawguidance.content.ink.InkShapeKind.SQUARE -> R.string.fix_square
    com.karapp.drawguidance.content.ink.InkShapeKind.POLYGON -> R.string.fix_polygon
}

/**
 * Başlığın altındaki bilgi satırı: tuval boyutu, son seçilen renk ve yazı ayarları (yazı tipi, boyut, kalın/eğik).
 * Dokununca Fırça ve yazı ayarları paneli açılır. Ayarlar tuvaller arasında hatırlanır.
 */
@Composable
fun EditorStyleLine(
    width: Int,
    height: Int,
    colorHex: String,
    text: TextSettings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeLayer: String? = null,
) {
    val colors = NeuTheme.colors
    val description = stringResource(R.string.style_line_cd)
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }.testTag("style_line"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("$width × $height", style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, maxLines = 1)
        Box(Modifier.size(10.dp).clip(CircleShape).background(hexToColor(colorHex)).border(0.5.dp, colors.contentMuted, CircleShape))
        Text(
            "${text.spec.font.title} ${text.sizePx.roundToInt()}",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (text.spec.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (text.spec.italic) FontStyle.Italic else FontStyle.Normal,
            ),
            color = colors.contentMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp),
        )
        if (activeLayer != null) {
            Text("· $activeLayer", style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 90.dp))
        }
    }
}

/** Başlıktaki ⋮ menüsü: PDF olarak kaydet (ders notları) ve otomatik kayıt (v17: el yazısı ayarı kalktı). */
@Composable
fun EditorMoreMenu(
    showPdf: Boolean,
    exportingPdf: Boolean,
    autosave: Boolean,
    onPdf: () -> Unit,
    onAutosave: (Boolean) -> Unit,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    /** v20 çizim düzeltme önerileri açık mı. */
    suggestions: Boolean = true,
    onSuggestions: (Boolean) -> Unit = {},
    /** v23 yazı ve şekil önerisi ayrı ayrı. */
    textSuggest: Boolean = true,
    onTextSuggest: (Boolean) -> Unit = {},
    shapeSuggest: Boolean = true,
    onShapeSuggest: (Boolean) -> Unit = {},
    /** v27 seçimde kendiliğinden öneri. */
    selectionSuggest: Boolean = true,
    onSelectionSuggest: (Boolean) -> Unit = {},
    /** v29 resim tuvalinde: çizimi not alma / çizgi roman tuvaline aktar. */
    onTransfer: (() -> Unit)? = null,
    /** v30 tuvalin yapay zekâ ayarı (kapalıyken öneri ayarları pasif). */
    aiEnabled: Boolean = true,
    onAiEnabled: ((Boolean) -> Unit)? = null,
    /** v32 eğitim modu (çizim, not alma, çizgi roman). */
    onTutorial: (() -> Unit)? = null,
    /** v38 çizgi önerisi (yazı ve şekil önerisinden ayrı). */
    lineSuggest: Boolean = true,
    onLineSuggest: (Boolean) -> Unit = {},
    /** v40 ek seçenekler (sayfa ayarları, CBZ); [close] menüyü kapatır. */
    extraItems: @Composable (close: () -> Unit) -> Unit = {},
) {
    val colors = NeuTheme.colors
    var open by remember { mutableStateOf(false) }
    val ai = aiEnabled || onAiEnabled == null
    Box {
        NeuIconButton(
            Icons.Filled.MoreVert, stringResource(R.string.editor_more), { open = true }, size = size, selected = open,
            label = stringResource(R.string.label_more),
            modifier = Modifier.tutorialAnchor(com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor.MORE),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(colors.background)) {
            if (onTutorial != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tut_menu)) },
                    leadingIcon = { Icon(Icons.Filled.School, null, tint = colors.accent) },
                    onClick = { open = false; onTutorial() },
                    modifier = Modifier.testTag("tutorial_menu_item"),
                )
            }
            if (onTransfer != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.transfer_title_canvas)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null, tint = colors.accent) },
                    onClick = { open = false; onTransfer() },
                    modifier = Modifier.testTag("transfer_menu_item"),
                )
            }
            if (showPdf) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (exportingPdf) R.string.pdf_exporting else R.string.export_pdf)) },
                    leadingIcon = { Icon(Icons.Filled.PictureAsPdf, null, tint = colors.accent) },
                    enabled = !exportingPdf,
                    onClick = { open = false; onPdf() },
                )
            }
            extraItems { open = false }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.autosave)) },
                leadingIcon = { Checkbox(checked = autosave, onCheckedChange = null) },
                onClick = { onAutosave(!autosave) },
            )
            if (onAiEnabled != null) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.ai_canvas_setting))
                            Text(
                                stringResource(if (aiEnabled) R.string.ai_canvas_on_hint else R.string.ai_canvas_off_hint),
                                style = MaterialTheme.typography.labelSmall, color = colors.contentMuted,
                            )
                        }
                    },
                    leadingIcon = { Checkbox(checked = aiEnabled, onCheckedChange = null) },
                    onClick = { onAiEnabled(!aiEnabled) },
                    modifier = Modifier.testTag("ai_menu_item"),
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.suggestions_setting)) },
                leadingIcon = { Checkbox(checked = ai && suggestions, onCheckedChange = null, enabled = ai) },
                enabled = ai,
                onClick = { onSuggestions(!suggestions) },
                modifier = Modifier.testTag("suggestions_menu_item"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.text_suggest_setting)) },
                leadingIcon = { Checkbox(checked = ai && suggestions && textSuggest, onCheckedChange = null, enabled = ai && suggestions) },
                enabled = ai && suggestions,
                onClick = { onTextSuggest(!textSuggest) },
                modifier = Modifier.padding(start = 16.dp).testTag("text_suggest_menu_item"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shape_suggest_setting)) },
                leadingIcon = { Checkbox(checked = ai && suggestions && shapeSuggest, onCheckedChange = null, enabled = ai && suggestions) },
                enabled = ai && suggestions,
                onClick = { onShapeSuggest(!shapeSuggest) },
                modifier = Modifier.padding(start = 16.dp).testTag("shape_suggest_menu_item"),
            )
            // v38 çizgi önerisi (yazı ve şekil önerisinden ayrı)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.line_suggest_setting)) },
                leadingIcon = { Checkbox(checked = ai && suggestions && lineSuggest, onCheckedChange = null, enabled = ai && suggestions) },
                enabled = ai && suggestions,
                onClick = { onLineSuggest(!lineSuggest) },
                modifier = Modifier.padding(start = 16.dp).testTag("line_suggest_menu_item"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.selection_suggest_setting)) },
                leadingIcon = { Checkbox(checked = ai && suggestions && selectionSuggest, onCheckedChange = null, enabled = ai && suggestions) },
                enabled = ai && suggestions,
                onClick = { onSelectionSuggest(!selectionSuggest) },
                modifier = Modifier.padding(start = 16.dp).testTag("selection_suggest_menu_item"),
            )
        }
    }
}

/** Otomatik kayıt kapalıyken kaydedilmemiş değişiklikle çıkılıyor. */
@Composable
fun UnsavedChangesDialog(onSave: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    val colors = NeuTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(stringResource(R.string.unsaved_title)) },
        text = { Text(stringResource(R.string.unsaved_text)) },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.save_button)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.unsaved_discard), color = colors.danger) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
            }
        },
    )
}
