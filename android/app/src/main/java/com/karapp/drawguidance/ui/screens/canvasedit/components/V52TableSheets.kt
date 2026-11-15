package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSheet
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.drawing.TableShape
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.theme.NeuTheme

/*
 * v52 not almada tablo pencereleri: "Tablo ekle" (satır/sütun sayısı, başlık satırı/sütunu, dönüşümlü satır, çizgiler,
 * görünüm) ve seçili tablonun düzenleme penceresi (hücre yazıları; her satır, her sütun ve tablonun tamamı için ayarlar).
 */

@Composable
private fun presetName(id: String): String = stringResource(
    when (id) {
        "blue" -> R.string.v52_table_preset_blue
        "green" -> R.string.v52_table_preset_green
        "orange" -> R.string.v52_table_preset_orange
        "purple" -> R.string.v52_table_preset_purple
        "minimal" -> R.string.v52_table_preset_minimal
        else -> R.string.v52_table_preset_classic
    },
)

@Composable
private fun borderName(id: String): String = stringResource(
    when (id) {
        "outer" -> R.string.v52_table_border_outer
        "rows" -> R.string.v52_table_border_rows
        "header" -> R.string.v52_table_border_header
        "none" -> R.string.v52_table_border_none
        else -> R.string.v52_table_border_all
    },
)

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, tag: String, onChange: (Int) -> Unit) {
    val colors = NeuTheme.colors
    Row(Modifier.fillMaxWidth().testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = colors.content)
        NeuIconButton(Icons.Filled.Remove, "−", { onChange((value - 1).coerceAtLeast(min)) }, size = 36.dp, enabled = value > min)
        Text("$value", Modifier.width(44.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = colors.content)
        NeuIconButton(Icons.Filled.Add, "+", { onChange((value + 1).coerceAtMost(max)) }, size = 36.dp, enabled = value < max)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = NeuTheme.colors.content)
        NeuSwitch(checked, onChange)
    }
}

/** Renk satırı; [allowDefault] ise başta "varsayılan" (ayarı kaldırır, null). */
@Composable
private fun Swatches(label: String, selected: String?, allowDefault: Boolean, tag: String, onPick: (String?) -> Unit) {
    val colors = NeuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(tag), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowDefault) NeuChip(stringResource(R.string.v52_table_default), selected == null, { onPick(null) })
            TableShape.palette.forEach { hex ->
                val on = selected.equals(hex, ignoreCase = true)
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(hexToColor(hex))
                        .border(if (on) 3.dp else 1.dp, if (on) colors.accent else colors.contentMuted.copy(alpha = 0.5f), CircleShape)
                        .clickable { onPick(hex) },
                )
            }
        }
    }
}

@Composable
private fun AlignRow(selected: String?, allowDefault: Boolean, onPick: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.v52_table_align), style = MaterialTheme.typography.labelLarge, color = NeuTheme.colors.contentMuted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowDefault) NeuChip(stringResource(R.string.v52_table_default), selected == null, { onPick(null) })
            NeuChip(stringResource(R.string.v52_table_align_l), selected == "l", { onPick("l") }, Modifier.testTag("table_align_l"))
            NeuChip(stringResource(R.string.v52_table_align_c), selected == "c", { onPick("c") }, Modifier.testTag("table_align_c"))
            NeuChip(stringResource(R.string.v52_table_align_r), selected == "r", { onPick("r") }, Modifier.testTag("table_align_r"))
        }
    }
}

/** Küçük tablo önizlemesi (değerlerden). */
@Composable
private fun TablePreview(p: Map<String, String>, modifier: Modifier = Modifier) {
    val nr = TableShape.rows(p); val nc = TableShape.cols(p)
    Canvas(modifier) {
        val cw = size.width / nc; val rh = minOf(size.height / nr, 28.dp.toPx())
        val h = rh * nr
        for (r in 0 until nr) for (c in 0 until nc) drawRect(hexToColor(TableShape.fillOf(p, r, c)), Offset(c * cw, r * rh), Size(cw, rh))
        val style = p["border"] ?: "all"
        val bc = hexToColor(p["bc"] ?: "#455A64")
        if (style != "none") {
            if (style != "rows") drawRect(bc, Offset.Zero, Size(size.width, h), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            else { drawLine(bc, Offset(0f, 0f), Offset(size.width, 0f), 2f); drawLine(bc, Offset(0f, h), Offset(size.width, h), 2f) }
            for (r in 1 until nr) if (style == "all" || style == "rows" || (r == 1 && TableShape.flag(p, "hr", true) && style != "outer")) drawLine(bc, Offset(0f, r * rh), Offset(size.width, r * rh), if (r == 1 && TableShape.flag(p, "hr", true)) 3f else 1f)
            if (style == "all") for (c in 1 until nc) drawLine(bc, Offset(c * cw, 0f), Offset(c * cw, h), 1f)
        }
    }
}

/** "Tablo ekle" penceresi. */
@Composable
fun TableCreateSheet(onDismiss: () -> Unit, onCreate: (Map<String, String>) -> Unit) {
    val colors = NeuTheme.colors
    var rows by rememberSaveable { mutableStateOf(4) }
    var cols by rememberSaveable { mutableStateOf(3) }
    var header by rememberSaveable { mutableStateOf(true) }
    var headerCol by rememberSaveable { mutableStateOf(false) }
    var zebra by rememberSaveable { mutableStateOf(true) }
    var border by rememberSaveable { mutableStateOf("all") }
    var preset by rememberSaveable { mutableStateOf("classic") }
    val params = TableShape.create(rows, cols, header, headerCol, zebra, border, TableShape.presets.first { it.id == preset })
    NeuSheet(onDismiss, full = true) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp).testTag("table_create_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.v52_add_table), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.v52_table_create_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            TablePreview(params, Modifier.fillMaxWidth().height(140.dp))
            Stepper(stringResource(R.string.v52_table_rows), rows, 1, TableShape.MAX_ROWS, "table_rows") { rows = it }
            Stepper(stringResource(R.string.v52_table_cols), cols, 1, TableShape.MAX_COLS, "table_cols") { cols = it }
            SwitchRow(stringResource(R.string.v52_table_header_row), header, "table_header_row") { header = it }
            SwitchRow(stringResource(R.string.v52_table_header_col), headerCol, "table_header_col") { headerCol = it }
            SwitchRow(stringResource(R.string.v52_table_zebra), zebra, "table_zebra") { zebra = it }
            Text(stringResource(R.string.v52_table_borders), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TableShape.borderStyles.forEach { b -> NeuChip(borderName(b), border == b, { border = b }, Modifier.testTag("table_border_$b")) }
            }
            Text(stringResource(R.string.v52_table_style), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TableShape.presets.forEach { p ->
                    NeuChip(presetName(p.id), preset == p.id, { preset = p.id; if (p.id == "minimal") border = "rows" }, Modifier.testTag("table_preset_${p.id}"))
                }
            }
            NeuButton(stringResource(R.string.v52_table_insert), { onCreate(params) }, Modifier.fillMaxWidth().testTag("table_insert"), primary = true)
        }
    }
}

/** Seçili tablonun düzenleme penceresi. */
@Composable
fun TableEditSheet(viewModel: CanvasEditViewModel) {
    val ctl = viewModel.tables
    val editing by ctl.editing.collectAsState()
    val canvas by viewModel.canvas.collectAsState()
    val id = editing ?: return
    val table = canvas?.strokes?.firstOrNull { it.id == id } ?: return
    val p = table.shape?.params.orEmpty()
    val colors = NeuTheme.colors
    val nr = TableShape.rows(p); val nc = TableShape.cols(p)
    // Kapsam: -1 hepsi; satır r ya da sütun c
    var scopeRow by remember(id) { mutableStateOf(-1) }
    var scopeCol by remember(id) { mutableStateOf(-1) }
    if (scopeRow >= nr) scopeRow = -1
    if (scopeCol >= nc) scopeCol = -1
    NeuSheet(ctl::closeEdit, full = true) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp).testTag("table_edit_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.v52_table_edit), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.v52_table_edit_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            // Hücreler
            Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                Column {
                    Row {
                        HeaderCell("", false, Modifier.width(40.dp)) { scopeRow = -1; scopeCol = -1 }
                        for (c in 0 until nc) HeaderCell(TableShape.colName(c), scopeCol == c, Modifier.width(120.dp).testTag("table_col_$c")) { scopeCol = c; scopeRow = -1 }
                    }
                    for (r in 0 until nr) Row {
                        HeaderCell("${r + 1}", scopeRow == r, Modifier.width(40.dp).testTag("table_row_$r")) { scopeRow = r; scopeCol = -1 }
                        for (c in 0 until nc) {
                            val highlighted = scopeRow == r || scopeCol == c
                            BasicTextField(
                                value = TableShape.cell(p, r, c),
                                onValueChange = { v -> ctl.update("table-$id-cell-$r-$c") { TableShape.setCell(it, r, c, v.replace('\n', ' ')) } },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = hexToColor(TableShape.textOf(p, r, c)), fontSize = 14.sp,
                                    fontWeight = if (TableShape.boldOf(p, r, c)) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = when (TableShape.alignOf(p, r, c)) { "c" -> TextAlign.Center; "r" -> TextAlign.End; else -> TextAlign.Start },
                                ),
                                cursorBrush = SolidColor(colors.accent),
                                modifier = Modifier.width(120.dp).height(44.dp)
                                    .background(hexToColor(TableShape.fillOf(p, r, c)))
                                    .border(BorderStroke(if (highlighted) 2.dp else 0.5.dp, if (highlighted) colors.accent else Color(0x55607D8B)))
                                    .padding(horizontal = 8.dp, vertical = 12.dp)
                                    .testTag("table_cell_${r}_$c"),
                            )
                        }
                    }
                }
            }
            // Kapsam seçimi
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuChip(stringResource(R.string.v52_table_scope_all), scopeRow < 0 && scopeCol < 0, { scopeRow = -1; scopeCol = -1 }, Modifier.testTag("table_scope_all"))
                if (scopeRow >= 0) NeuChip(stringResource(R.string.v52_table_scope_row, scopeRow + 1), true, {})
                if (scopeCol >= 0) NeuChip(stringResource(R.string.v52_table_scope_col, TableShape.colName(scopeCol)), true, {})
            }
            when {
                scopeRow >= 0 -> LineSettings(viewModel, p, isRow = true, index = scopeRow, id = id) { scopeRow = it }
                scopeCol >= 0 -> LineSettings(viewModel, p, isRow = false, index = scopeCol, id = id) { scopeCol = it }
                else -> AllSettings(viewModel, p, id)
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = NeuTheme.colors
    Box(
        modifier.height(if (text.isEmpty() || text.first().isLetter()) 32.dp else 44.dp)
            .background(if (selected) colors.accent.copy(alpha = 0.18f) else colors.contentMuted.copy(alpha = 0.08f))
            .border(0.5.dp, colors.contentMuted.copy(alpha = 0.3f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) colors.accent else colors.content) }
}

/** Tablonun tamamı. */
@Composable
private fun AllSettings(viewModel: CanvasEditViewModel, p: Map<String, String>, id: String) {
    val ctl = viewModel.tables
    val colors = NeuTheme.colors
    Stepper(stringResource(R.string.v52_table_rows), TableShape.rows(p), 1, TableShape.MAX_ROWS, "table_all_rows") { n -> ctl.update { TableShape.resize(it, n, TableShape.cols(it)) } }
    Stepper(stringResource(R.string.v52_table_cols), TableShape.cols(p), 1, TableShape.MAX_COLS, "table_all_cols") { n -> ctl.update { TableShape.resize(it, TableShape.rows(it), n) } }
    SwitchRow(stringResource(R.string.v52_table_header_row), TableShape.flag(p, "hr", true), "table_all_hr") { v -> ctl.update { it + ("hr" to if (v) "1" else "0") } }
    SwitchRow(stringResource(R.string.v52_table_header_col), TableShape.flag(p, "hc"), "table_all_hc") { v -> ctl.update { it + ("hc" to if (v) "1" else "0") } }
    SwitchRow(stringResource(R.string.v52_table_zebra), TableShape.flag(p, "zebra"), "table_all_zebra") { v -> ctl.update { it + ("zebra" to if (v) "1" else "0") } }
    Text(stringResource(R.string.v52_table_borders), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TableShape.borderStyles.forEach { b -> NeuChip(borderName(b), (p["border"] ?: "all") == b, { ctl.update { it + ("border" to b) } }) }
    }
    Text(stringResource(R.string.v52_table_style), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TableShape.presets.forEach { pr ->
            NeuChip(presetName(pr.id), false, { ctl.update { m -> LinkedHashMap(m).also { TableShape.applyPreset(it, pr) } } })
        }
    }
    AlignRow(p["al"] ?: "l", false) { v -> ctl.update { TableShape.setGlobal(it, "al", v, clearProp = "a") } }
    Text(stringResource(R.string.v52_table_text_size), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
    NeuSlider((p["fs"]?.toFloatOrNull() ?: 0.42f), { v -> ctl.update("table-$id-fs") { it + ("fs" to "%.2f".format(java.util.Locale.ROOT, v)) } }, 0.2f..0.8f)
    Text(stringResource(R.string.v52_table_line_width), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
    NeuSlider((p["bw"]?.toFloatOrNull() ?: 1f), { v -> ctl.update("table-$id-bw") { it + ("bw" to "%.2f".format(java.util.Locale.ROOT, v)) } }, 0.25f..4f)
    Swatches(stringResource(R.string.v52_table_bg), p["bg"], false, "table_all_bg") { v -> ctl.update { TableShape.setGlobal(it, "bg", v, clearProp = "f") } }
    Swatches(stringResource(R.string.v52_table_text_color), p["tc"], false, "table_all_tc") { v -> ctl.update { TableShape.setGlobal(it, "tc", v, clearProp = "t") } }
    Swatches(stringResource(R.string.v52_table_header_fill), p["hf"], false, "table_all_hf") { v -> ctl.update { TableShape.setGlobal(it, "hf", v) } }
    Swatches(stringResource(R.string.v52_table_header_text), p["ht"], false, "table_all_ht") { v -> ctl.update { TableShape.setGlobal(it, "ht", v) } }
    Swatches(stringResource(R.string.v52_table_zebra_fill), p["zf"], false, "table_all_zf") { v -> ctl.update { TableShape.setGlobal(it, "zf", v) } }
    Swatches(stringResource(R.string.v52_table_border_color), p["bc"], false, "table_all_bc") { v -> ctl.update { TableShape.setGlobal(it, "bc", v) } }
    NeuButton(stringResource(R.string.v52_table_reset_lines), {
        ctl.update { m -> m.filterKeys { k -> !(k.startsWith("R.") || k.startsWith("C.")) } }
    }, Modifier.fillMaxWidth().testTag("table_reset_lines"))
}

/** Tek satır ([isRow]) ya da tek sütunun ayarları. */
@Composable
private fun LineSettings(viewModel: CanvasEditViewModel, p: Map<String, String>, isRow: Boolean, index: Int, id: String, onIndex: (Int) -> Unit) {
    val ctl = viewModel.tables
    val colors = NeuTheme.colors
    fun prop(name: String) = if (isRow) TableShape.rowProp(p, name, index) else TableShape.colProp(p, name, index)
    fun set(name: String, value: String?) = ctl.update { TableShape.setLineProp(it, isRow, name, index, value) }
    val sizeKey = if (isRow) "h" else "w"
    val weight = prop(sizeKey)?.toFloatOrNull() ?: 1f
    Text(stringResource(if (isRow) R.string.v52_table_height else R.string.v52_table_width), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
    NeuSlider(weight, { v -> ctl.update("table-$id-$sizeKey-$index") { TableShape.setLineProp(it, isRow, sizeKey, index, "%.2f".format(java.util.Locale.ROOT, v)) } }, 0.4f..4f)
    val bold = prop("b")
    SwitchRow(stringResource(R.string.v52_table_bold), bold?.let { it == "1" } ?: (isRow && index == 0 && TableShape.flag(p, "hr", true)) || (!isRow && index == 0 && TableShape.flag(p, "hc")), "table_line_bold") { v -> set("b", if (v) "1" else "0") }
    AlignRow(prop("a"), true) { set("a", it) }
    Swatches(stringResource(R.string.v52_table_fill), prop("f"), true, "table_line_fill") { set("f", it) }
    Swatches(stringResource(R.string.v52_table_text_color), prop("t"), true, "table_line_text") { set("t", it) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isRow) {
            NeuButton(stringResource(R.string.v52_table_add_above), { ctl.update { TableShape.insertRow(it, index) }; onIndex(index + 1) }, icon = Icons.Filled.Add)
            NeuButton(stringResource(R.string.v52_table_add_below), { ctl.update { TableShape.insertRow(it, index + 1) } }, icon = Icons.Filled.Add)
            NeuButton(stringResource(R.string.v52_table_delete_row), { ctl.update { TableShape.deleteRow(it, index) }; onIndex(-1) }, icon = Icons.Filled.Delete, enabled = TableShape.rows(p) > 1)
        } else {
            NeuButton(stringResource(R.string.v52_table_add_left), { ctl.update { TableShape.insertCol(it, index) }; onIndex(index + 1) }, icon = Icons.Filled.Add)
            NeuButton(stringResource(R.string.v52_table_add_right), { ctl.update { TableShape.insertCol(it, index + 1) } }, icon = Icons.Filled.Add)
            NeuButton(stringResource(R.string.v52_table_delete_col), { ctl.update { TableShape.deleteCol(it, index) }; onIndex(-1) }, icon = Icons.Filled.Delete, enabled = TableShape.cols(p) > 1)
        }
        NeuButton(stringResource(R.string.v52_table_clear), {
            ctl.update { m -> m.filterKeys { k -> val parts = k.split('.'); !(parts.size == 3 && parts[0] == "T" && (if (isRow) parts[1] else parts[2]) == index.toString()) } }
        })
    }
    Spacer(Modifier.height(4.dp))
}
