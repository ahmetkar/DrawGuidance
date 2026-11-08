package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.ui.layout.onSizeChanged

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.formula.FormulaCatalog
import com.karapp.drawguidance.content.formula.FormulaTemplate
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.FormulaSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.drawing.DiagramThemes
import com.karapp.drawguidance.ui.drawing.FormulaRenderer
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.theme.NeuCardShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic
import kotlinx.coroutines.Dispatchers
import kotlin.math.max
import kotlin.math.min
import com.karapp.drawguidance.ui.components.NeuSheet

/**
 * Seçicilerdeki küçük resimler (formül, doku/desen, diyagram teması…): gerçek çizim kodu arka planda bir kez
 * çalıştırılır, sonuç önbellekte tutulur. Her seçici yalnızca "nasıl çizilir" kısmını verir.
 */
object Thumbs {
    private val cache = object : android.util.LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 24).coerceIn(4L shl 20, 16L shl 20).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun cached(key: String): ImageBitmap? = cache.get(key)

    fun render(key: String, w: Int, h: Int, draw: (android.graphics.Canvas, Int, Int) -> Unit): ImageBitmap? {
        cache.get(key)?.let { return it }
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        runCatching { draw(android.graphics.Canvas(bitmap), w, h) }.onFailure { return null }
        val image = bitmap.asImageBitmap()
        cache.put(key, image)
        return image
    }
}

/** Arka planda üretilen küçük resim ([key] çizimin kimliği; boyut ve renk anahtara eklenir). */
@Composable
fun Thumb(key: String, color: Color, modifier: Modifier = Modifier, draw: (android.graphics.Canvas, Int, Int, Int) -> Unit) {
    // v27 boyut ölçüden (alt kompozisyon yok); çizim sınırlı iş parçacığında
    var px by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    androidx.compose.foundation.layout.Box(modifier.onSizeChanged { px = it }) {
        if (px.width < 8 || px.height < 8) return@Box
        val w = px.width; val h = px.height
        val ink = color.toArgb()
        val fullKey = "$key|$w|$h|$ink"
        val bitmap = com.karapp.drawguidance.ui.components.rememberKeyedThumb(fullKey, Thumbs::cached) { k ->
            Thumbs.render(k, w, h) { c, cw, ch -> draw(c, cw, ch, ink) }
        }
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/** Formülü verilen kutuya sığdırıp çizer (seçici ve düzenleme penceresi önizlemesi). */
fun drawFormulaFit(canvas: android.graphics.Canvas, source: String, w: Int, h: Int, ink: Int, pad: Float = 4f) {
    val q = FormulaRenderer.quadAt(source, 100f, 0f, 0f)
    val w100 = q[1].x - q[0].x
    val h100 = q[3].y - q[0].y
    if (w100 <= 0f || h100 <= 0f) return
    val size = min((w - pad * 2) / w100, (h - pad * 2) / h100) * 100f
    val left = (w - w100 * size / 100f) / 2f
    val top = (h - h100 * size / 100f) / 2f
    val quad = FormulaRenderer.quadAt(source, size, left, top).map { StrokePoint(it.x, it.y) }
    val stroke = Stroke("thumb", quad, "#000000", BrushType.FORMULA, size, 1f, 0, formula = FormulaSpec(source))
    FormulaRenderer.draw(canvas, stroke, ink)
}

/** Formül kataloğu: kategori sekmeleri, formüller gerçek dizgisiyle gösterilir; dokununca Formül moduna geçilir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormulaPickerModal(
    selected: String?, onDismiss: () -> Unit, onSelect: (FormulaTemplate) -> Unit,
    /** v33 basit not almada yalnız bu formüller (tek kategori). */
    only: List<String>? = null,
) {
    val colors = NeuTheme.colors
    val categories = if (only == null) FormulaCatalog.categories else {
        val all = FormulaCatalog.categories.flatMap { it.templates }.associateBy { it.id }
        listOf(FormulaCatalog.categories.first().copy(id = "simple", title = stringResource(R.string.formula_cat_simple), templates = only.mapNotNull { all[it] }))
    }
    var category by rememberSaveable { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    NeuSheet(onDismiss) {
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().testTag("formula_picker")) {
            val columns = (maxWidth / 170.dp).toInt().coerceIn(2, 5)
            val shown = categories.firstOrNull { it.id == category } ?: categories.first()
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.formula_picker_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.formula_picker_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            categories.forEach { c -> NeuChip(text = c.title, selected = c.id == shown.id, onClick = { category = c.id }) }
                        }
                    }
                }
                items(shown.templates, key = { it.id }) { t ->
                    val isSelected = t.id == selected
                    Column(
                        Modifier
                            .neumorphic(colors, NeuCardShape, elevation = if (isSelected) 3.dp else 5.dp, selected = isSelected)
                            .clip(NeuCardShape)
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(t) })
                            .semantics(mergeDescendants = true) { }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Thumb("formula:" + t.source, if (isSelected) colors.accent else colors.content, Modifier.fillMaxWidth().height(56.dp)) { c, w, h, ink ->
                            drawFormulaFit(c, t.source, w, h, ink)
                        }
                        Text(
                            t.title, style = MaterialTheme.typography.labelSmall, color = if (isSelected) colors.accent else colors.content,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Diyagram renk temaları: küçük renk şeridiyle seçilir (yeni diyagramlarda ve seçili diyagramda). */
@Composable
fun DiagramThemeRow(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DiagramThemes.all.forEach { theme ->
            // v40 zeminsiz kimlik ("pastel_clear") temel temanın kutusunu seçili gösterir; seçim zemin ayarını korur
            val isSelected = theme.id == selected || (selected != DiagramThemes.EDITORIAL_CLEAR && theme.id == DiagramThemes.baseOf(selected))
            val pick = if (theme.id == DiagramThemes.EDITORIAL_CLEAR) theme.id else DiagramThemes.withBackground(theme.id, DiagramThemes.hasBackground(selected))
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.accent else colors.contentMuted.copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(pick) })
                    .semantics(mergeDescendants = true) { contentDescription = theme.title }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val fills = theme.fills.ifEmpty { listOf("#FFFFFF") }
                    fills.take(4).forEach { hex ->
                        Box(
                            Modifier.size(14.dp).clip(CircleShape).background(hexToColor(hex))
                                .border(1.dp, hexToColor(theme.stroke ?: theme.edge ?: "#455A64").copy(alpha = 0.6f), CircleShape),
                        )
                    }
                }
                Text(
                    theme.title, style = MaterialTheme.typography.labelSmall, color = if (isSelected) colors.accent else colors.content,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Renk yuvarlağı (dokununca renk penceresi); [hex] null ise çapraz çizgili "yok". */
@Composable
fun ColorDot(hex: String?, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(hex?.let { hexToColor(it) } ?: colors.background)
            .border(1.dp, colors.contentMuted, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (hex == null) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                drawLine(colors.danger, Offset(0f, size.height), Offset(size.width, 0f), 2.dp.toPx())
            }
        }
    }
}

/** Seçili diyagramın renk teması (alttan açılan küçük pencere; seçim hemen uygulanır). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagramThemeSheet(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = NeuTheme.colors
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp).testTag("diagram_theme_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.diagram_theme), style = MaterialTheme.typography.titleLarge)
            DiagramThemeRow(selected, onSelect)
            // v40 seçili diyagramın zemini de değiştirilebilir
            DiagramBackgroundRow(selected, onSelect)
        }
    }
}
