package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.drawing.ShapeCatalog
import com.karapp.drawguidance.ui.drawing.ShapeCategory
import com.karapp.drawguidance.ui.drawing.ShapeFit
import com.karapp.drawguidance.ui.drawing.ShapeKind
import com.karapp.drawguidance.ui.drawing.ShapePickerFilter
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.ui.common.hexToColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import com.karapp.drawguidance.ui.theme.NeuCardShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic
import com.karapp.drawguidance.ui.components.NeuSheet

/**
 * Tuval türüne göre şekil kataloğu ([ShapeRegistry]): yerleşik şekiller, grafikler ve grafik eğrileri (ayrı
 * kategoriler), fen/geometri şekilleri ve hazır çizimler. Üstte bölüm sekmeleri, altta kategoriler; şekle dokununca
 * Şekil moduna geçilir. [showFill] ile "İçi dolu" seçeneği gösterilir (yeni şekiller dolgulu eklenir).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShapePickerModal(
    kind: CanvasKind,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    /** Başlık (şekil dönüştürmede farklı). */
    title: String? = null,
    /** Yalnızca dönüştürülebilir (yerleşik) şekiller. */
    convertOnly: Boolean = false,
    /** Yeni şekillerin dolgusu (null: dolgusuz); verilirse "İçi dolu" satırı görünür. */
    fillHex: String? = null,
    showFill: Boolean = false,
    onFillChange: (String?) -> Unit = {},
    /** v23 3B Şekillerim (ayrı bölüm; dokununca tuvale eklenir, kalemle 3B düzenleyicide açılır). */
    models3d: List<com.karapp.drawguidance.data.local.Model3DInfo>? = null,
    onModel3d: (String) -> Unit = {},
    onEditModel3d: (String) -> Unit = {},
    onNewModel3d: () -> Unit = {},
) {
    val colors = NeuTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val categories = remember(kind, convertOnly) { if (convertOnly) ShapeRegistry.convertible(kind) else ShapeRegistry.categories(kind) }
    val models3dSection = stringResource(R.string.model3d_my_models)
    val sections = remember(categories, models3d != null) { categories.map { it.section }.distinct() + listOfNotNull(models3dSection.takeIf { models3d != null }) }
    var section by rememberSaveable(kind, convertOnly) { mutableStateOf(sections.firstOrNull() ?: "") }
    var fillColorOpen by remember { mutableStateOf(false) }
    // v36 arama ve kategori süzgeci (v43 nesne çizimleri ve stil süzgeci kaldırıldı)
    var query by rememberSaveable(kind, convertOnly) { mutableStateOf("") }
    val sectionCats = remember(categories, section, sections) { categories.filter { it.section == section || sections.size <= 1 } }
    var categoryId by rememberSaveable(kind, convertOnly, section) { mutableStateOf<String?>(null) }
    val index = remember(categories) {
        lazy {
            ShapePickerFilter.Index(
                categories,
                label = { id -> ShapeRegistry.builtin(id)?.let { context.getString(shapeLabelRes(it)) } ?: I18n.tr(ShapeRegistry.title(id) ?: id) },
                categoryLabel = { c -> c.builtin?.let { context.getString(shapeCategoryLabelRes(it)) } ?: I18n.tr(c.title) },
            )
        }
    }
    val searching = query.isNotBlank()
    val in3d = !searching && section == models3dSection && models3d != null
    val base = remember(query, section, categoryId, sectionCats, in3d) {
        when {
            searching -> index.value.search(query)
            in3d -> emptyList()
            else -> sectionCats.filter { categoryId == null || it.id == categoryId }
        }
    }
    val shown = base
    NeuSheet(onDismiss) {
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().testTag("shape_picker")) {
            val columns = (maxWidth / 96.dp).toInt().coerceIn(3, 8)
            // Kaydırılan sütun (tembel ızgara değil): kategori başlıkları ve tüm kutular hemen erişilebilir
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).testTag("shape_grid")
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title ?: stringResource(R.string.shape_picker_title), style = MaterialTheme.typography.titleLarge)
                if (title == null) {
                    Text(stringResource(R.string.shape_picker_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                }
                if (showFill) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.shape_filled), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (fillHex != null) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(hexToColor(fillHex))
                                    .border(1.dp, colors.contentMuted, androidx.compose.foundation.shape.CircleShape)
                                    .clickable { fillColorOpen = true }
                                    .semantics { contentDescription = "Dolgu rengi" },
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = fillHex != null,
                            onCheckedChange = { onFillChange(if (it) (fillHex ?: DEFAULT_FILL) else null) },
                            modifier = Modifier.semantics { contentDescription = "İçi dolu" },
                        )
                    }
                }
                if (!convertOnly) {
                    com.karapp.drawguidance.ui.components.TitleSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.shape_search_hint),
                        clearDescription = stringResource(R.string.shape_search_clear),
                        tag = "shape_search",
                    )
                }
                if (sections.size > 1 && !searching) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sections.forEach { name ->
                            com.karapp.drawguidance.ui.components.NeuChip(text = name, selected = name == section, onClick = { section = name })
                        }
                    }
                }
                if (!searching && !in3d && sectionCats.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("shape_category_filter"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        com.karapp.drawguidance.ui.components.NeuChip(
                            text = stringResource(R.string.shape_filter_all), selected = categoryId == null, onClick = { categoryId = null },
                        )
                        sectionCats.forEach { c ->
                            com.karapp.drawguidance.ui.components.NeuChip(
                                text = c.builtin?.let { shapeCategoryLabel(it) } ?: c.title,
                                selected = categoryId == c.id,
                                onClick = { categoryId = c.id },
                            )
                        }
                    }
                }
                if (searching && shown.isEmpty()) {
                    Text(stringResource(R.string.shape_search_empty), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
                }
                if (in3d) {
                    Text(stringResource(R.string.model3d_picker_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                    com.karapp.drawguidance.ui.components.NeuButton(
                        stringResource(R.string.model3d_new), onNewModel3d, Modifier.fillMaxWidth(), primary = true,
                    )
                    if (models3d.isEmpty()) Text(stringResource(R.string.model3d_list_empty), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
                    val cols = (columns / 2).coerceAtLeast(2)
                    models3d.chunked(cols).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { info ->
                                com.karapp.drawguidance.ui.screens.model3d.Model3DCard(
                                    info, onClick = { onModel3d(info.id) }, modifier = Modifier.weight(1f), onEdit = { onEditModel3d(info.id) },
                                )
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                // v27 kutular karelere yayılarak oluşturulur (pencere hemen açılır, kalanlar ardından gelir).
                val slotted = remember(shown) { shown.map { it to it.items } }
                val totalTiles = slotted.sumOf { it.second.size }
                var budget = com.karapp.drawguidance.ui.components.rememberProgressiveCount(totalTiles, listOf(section, kind, query, categoryId), first = columns * 5, step = columns * 3)
                for ((category, slots) in slotted) {
                    if (budget <= 0) break
                    Text(
                        category.builtin?.let { shapeCategoryLabel(it) } ?: category.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.contentMuted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    val cols = columns
                    val items = slots.take(budget)
                    budget -= items.size
                    items.chunked(cols).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { id ->
                                ShapeTile(id, id == selected, { onSelect(id) }, Modifier.weight(1f))
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
    if (fillColorOpen) {
        PartColorSheet(
            title = stringResource(R.string.shape_fill_color),
            colorHex = fillHex ?: DEFAULT_FILL,
            onColorChange = { onFillChange(it) },
            onDismiss = { fillColorOpen = false },
        )
    }
}

private const val DEFAULT_FILL = "#BBDEFB"


/** Şeklin adı (yerleşiklerde string kaynağı, diğerlerinde kayıttaki ad). */
@Composable
fun shapeTitle(id: String): String {
    ShapeRegistry.builtin(id)?.let { return shapeLabel(it) }
    return ShapeRegistry.title(id) ?: id
}

@Composable
private fun ShapeTile(id: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, styleLabel: String? = null) {
    // v40 şekil ekleme ve diyagramlar Premium değil: v39 kilidi kaldırıldı
    androidx.compose.foundation.layout.Box(modifier) {
        val colors = NeuTheme.colors
        val label = shapeTitle(id)
        Column(
            modifier = Modifier.fillMaxWidth()
                .neumorphic(colors, NeuCardShape, elevation = if (selected) 3.dp else 5.dp, selected = selected)
                .clip(NeuCardShape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics(mergeDescendants = true) { }
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val builtin = ShapeRegistry.builtin(id)
            if (builtin != null) ShapeIcon(builtin, if (selected) colors.accent else colors.content, Modifier.fillMaxWidth().height(40.dp))
            else ShapeThumbnail(id, if (selected) colors.accent else colors.content, Modifier.fillMaxWidth().height(52.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) colors.accent else colors.content,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (styleLabel != null) {
                Text(styleLabel, style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Kütüphane ve grafik şekillerinin küçük resmi: gerçek çizimle arka planda bir kez üretilir (önbellekli). */
@Composable
fun ShapeThumbnail(id: String, color: Color, modifier: Modifier = Modifier, fillHex: String? = null) {
    // v27 boyut alt kompozisyon (BoxWithConstraints) yerine ölçüden alınır; çizim sınırlı iş parçacığında
    var px by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    androidx.compose.foundation.layout.Box(modifier.onSizeChanged { px = it }) {
        if (px.width < 8 || px.height < 8) return@Box
        val w = px.width; val h = px.height
        val ink = color.toArgb()
        val bitmap = com.karapp.drawguidance.ui.components.rememberKeyedThumb(
            listOf(id, w, h, ink, fillHex), { ShapeThumbnails.cached(id, w, h, ink, fillHex) },
        ) { ShapeThumbnails.render(id, w, h, ink, fillHex) }
        bitmap?.let { androidx.compose.foundation.Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/** Seçici küçük resimlerinin önbelleği (v27: bayt sınırlı; bütün katalog yeniden açılışta hazır kalsın). */
object ShapeThumbnails {
    private val cache = object : android.util.LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 16).coerceIn(6L shl 20, 24L shl 20).toInt(),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun key(id: String, w: Int, h: Int, ink: Int, fillHex: String?) = if (fillHex == null) "$id|$w|$h|$ink" else "$id|$w|$h|$ink|$fillHex"

    fun cached(id: String, w: Int, h: Int, ink: Int, fillHex: String? = null): ImageBitmap? = cache.get(key(id, w, h, ink, fillHex))

    /** [fillHex] verilirse şekil o dolguyla (v36 efektlerin renkli önizlemesi). */
    fun render(id: String, w: Int, h: Int, ink: Int, fillHex: String? = null): ImageBitmap? {
        cached(id, w, h, ink, fillHex)?.let { return it }
        val pad = 3f
        val aspect = if (ShapeRegistry.fit(id) == com.karapp.drawguidance.ui.drawing.ShapeFit.SQUARE) 1f else ShapeRegistry.aspect(id)
        val availW = w - pad * 2
        val availH = h - pad * 2
        val boxW: Float
        val boxH: Float
        if (availW / availH > aspect) { boxH = availH; boxW = availH * aspect } else { boxW = availW; boxH = availW / aspect }
        val left = (w - boxW) / 2f
        val top = (h - boxH) / 2f
        val line = ShapeRegistry.fit(id) == com.karapp.drawguidance.ui.drawing.ShapeFit.LINE
        val start = if (line) Offset(left, h / 2f) else Offset(left, top)
        val end = if (line) Offset(left + boxW, h / 2f) else Offset(left + boxW, top + boxH)
        val hex = String.format("#%06X", ink and 0xFFFFFF)
        val stroke = runCatching {
            com.karapp.drawguidance.ui.drawing.ShapeFactory.build(
                id, start, end, com.karapp.drawguidance.data.model.ShapeStyle(strokeColorHex = hex, strokeWidth = 1.3f, fillColorHex = fillHex),
                id = "thumb-$id-$w-$h", order = 0,
            )
        }.getOrNull() ?: return null
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        com.karapp.drawguidance.ui.drawing.ShapeRenderer.draw(android.graphics.Canvas(bitmap), stroke, cache = false)
        val image = bitmap.asImageBitmap()
        cache.put(key(id, w, h, ink, fillHex), image)
        return image
    }
}

/** Şeklin küçük çizimi (katalogdaki gerçek geometriyle). */
@Composable
fun ShapeIcon(shape: ShapeKind, color: Color, modifier: Modifier = Modifier) {
    // v21 gölgeli cisimler gerçek çizimle (tonlu yüzler) gösterilir
    if (shape in com.karapp.drawguidance.ui.drawing.ShapeSolids.kinds) {
        ShapeThumbnail(shape.name, color, modifier)
        return
    }
    Canvas(modifier) {
        val stroke = 1.6.dp.toPx()
        val side = minOf(size.height, size.width) - stroke * 2
        val boxW = if (shape.fit == ShapeFit.SQUARE) side else minOf(size.width - stroke * 2, side * 1.35f)
        val left = (size.width - boxW) / 2f
        val top = (size.height - side) / 2f
        val start: Offset
        val end: Offset
        if (shape.fit == ShapeFit.LINE) {
            start = Offset(left, top + side * 0.8f)
            end = Offset(left + boxW, top + side * 0.2f)
        } else {
            start = Offset(left, top)
            end = Offset(left + boxW, top + side)
        }
        ShapeCatalog.paths(shape, start, end, stroke * 0.6f).forEach { path ->
            if (path.points.size < 2) return@forEach
            val p = Path().apply {
                moveTo(path.points[0].x, path.points[0].y)
                for (i in 1 until path.points.size) lineTo(path.points[i].x, path.points[i].y)
            }
            drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun shapeCategoryLabel(category: ShapeCategory): String = stringResource(shapeCategoryLabelRes(category))

fun shapeCategoryLabelRes(category: ShapeCategory): Int = when (category) {
    ShapeCategory.ART_BASIC, ShapeCategory.ART_GENERAL -> R.string.shape_cat_basic
    ShapeCategory.ART_LANDSCAPE -> R.string.shape_cat_landscape
    ShapeCategory.ART_PORTRAIT -> R.string.shape_cat_portrait
    ShapeCategory.NOTE_LINES -> R.string.shape_cat_lines
    ShapeCategory.NOTE_PLANE -> R.string.shape_cat_plane
    ShapeCategory.NOTE_SOLIDS -> R.string.shape_cat_solids
    ShapeCategory.NOTE_GRAPHS -> R.string.shape_cat_graphs
    ShapeCategory.SIMPLE_ART_BASIC, ShapeCategory.SIMPLE_NOTE_BASIC -> R.string.shape_cat_simple
    ShapeCategory.SIMPLE_ART_NATURE -> R.string.shape_cat_simple_nature
    ShapeCategory.SIMPLE_ART_THINGS -> R.string.shape_cat_simple_things
    ShapeCategory.SIMPLE_NOTE_GEOMETRY -> R.string.shape_cat_simple_geometry
    ShapeCategory.SIMPLE_NOTE_SOLIDS -> R.string.shape_cat_simple_solids
}

@Composable
fun shapeLabel(shape: ShapeKind): String = stringResource(shapeLabelRes(shape))

fun shapeLabelRes(shape: ShapeKind): Int = when (shape) {
    ShapeKind.LINE -> R.string.shape_line
    ShapeKind.ARROW -> R.string.shape_arrow
    ShapeKind.DOUBLE_ARROW -> R.string.shape_double_arrow
    ShapeKind.CIRCLE -> R.string.shape_circle
    ShapeKind.ELLIPSE -> R.string.shape_ellipse
    ShapeKind.SQUARE -> R.string.shape_square
    ShapeKind.RECTANGLE -> R.string.shape_rectangle
    ShapeKind.ROUNDED_RECT -> R.string.shape_rounded_rect
    ShapeKind.TRIANGLE -> R.string.shape_triangle
    ShapeKind.RIGHT_TRIANGLE -> R.string.shape_right_triangle
    ShapeKind.EQUILATERAL_TRIANGLE -> R.string.shape_equilateral_triangle
    ShapeKind.DIAMOND -> R.string.shape_diamond
    ShapeKind.PARALLELOGRAM -> R.string.shape_parallelogram
    ShapeKind.TRAPEZOID -> R.string.shape_trapezoid
    ShapeKind.PENTAGON -> R.string.shape_pentagon
    ShapeKind.HEXAGON -> R.string.shape_hexagon
    ShapeKind.OCTAGON -> R.string.shape_octagon
    ShapeKind.STAR -> R.string.shape_star
    ShapeKind.HEART -> R.string.shape_heart
    ShapeKind.SPIRAL -> R.string.shape_spiral
    ShapeKind.SEMICIRCLE -> R.string.shape_semicircle
    ShapeKind.CIRCLE_RADIUS -> R.string.shape_circle_radius
    ShapeKind.SUN -> R.string.shape_sun
    ShapeKind.MOON -> R.string.shape_moon
    ShapeKind.CLOUD -> R.string.shape_cloud
    ShapeKind.MOUNTAINS -> R.string.shape_mountains
    ShapeKind.HILLS -> R.string.shape_hills
    ShapeKind.WAVES -> R.string.shape_waves
    ShapeKind.TREE -> R.string.shape_tree
    ShapeKind.PINE -> R.string.shape_pine
    ShapeKind.FLOWER -> R.string.shape_flower
    ShapeKind.LEAF -> R.string.shape_leaf
    ShapeKind.BIRD -> R.string.shape_bird
    ShapeKind.HOUSE -> R.string.shape_house
    ShapeKind.DROP -> R.string.shape_drop
    ShapeKind.FACE_GUIDE -> R.string.shape_face_guide
    ShapeKind.EYE -> R.string.shape_eye
    ShapeKind.LIPS -> R.string.shape_lips
    ShapeKind.NOSE -> R.string.shape_nose
    ShapeKind.ANGLE -> R.string.shape_angle
    ShapeKind.RIGHT_ANGLE -> R.string.shape_right_angle
    ShapeKind.PARALLEL_LINES -> R.string.shape_parallel_lines
    ShapeKind.CUBE -> R.string.shape_cube
    ShapeKind.SOLID_CUBE -> R.string.shape_solid_cube
    ShapeKind.SOLID_BOX -> R.string.shape_solid_box
    ShapeKind.SOLID_CYLINDER -> R.string.shape_solid_cylinder
    ShapeKind.SOLID_CONE -> R.string.shape_solid_cone
    ShapeKind.SOLID_SPHERE -> R.string.shape_solid_sphere
    ShapeKind.SOLID_DOME -> R.string.shape_solid_dome
    ShapeKind.SOLID_PYRAMID -> R.string.shape_solid_pyramid
    ShapeKind.SOLID_PRISM -> R.string.shape_solid_prism
    ShapeKind.SOLID_HEX_PRISM -> R.string.shape_solid_hex_prism
    ShapeKind.PRISM -> R.string.shape_prism
    ShapeKind.CYLINDER -> R.string.shape_cylinder
    ShapeKind.CONE -> R.string.shape_cone
    ShapeKind.SPHERE -> R.string.shape_sphere
    ShapeKind.PYRAMID -> R.string.shape_pyramid
    ShapeKind.AXES -> R.string.shape_axes
    ShapeKind.NUMBER_LINE -> R.string.shape_number_line
    ShapeKind.GRID -> R.string.shape_grid
    ShapeKind.PARABOLA -> R.string.shape_parabola
    ShapeKind.SINE -> R.string.shape_sine
    ShapeKind.SECTOR -> R.string.shape_sector
    ShapeKind.ARC -> R.string.shape_arc
    ShapeKind.ANNULUS -> R.string.shape_annulus
    ShapeKind.KITE -> R.string.shape_kite
    ShapeKind.RIGHT_TRAPEZOID -> R.string.shape_right_trapezoid
    ShapeKind.TRIANGLE_HEIGHT -> R.string.shape_triangle_height
    ShapeKind.CROSS -> R.string.shape_cross
    ShapeKind.ARROW_BLOCK -> R.string.shape_arrow_block
    ShapeKind.BUTTERFLY -> R.string.shape_butterfly
    ShapeKind.FISH -> R.string.shape_fish
    ShapeKind.PALM -> R.string.shape_palm
    ShapeKind.GRASS -> R.string.shape_grass
    ShapeKind.ROCK -> R.string.shape_rock
    ShapeKind.BOAT -> R.string.shape_boat
    ShapeKind.APPLE -> R.string.shape_apple
    ShapeKind.TRIANGULAR_PRISM -> R.string.shape_triangular_prism
    ShapeKind.HEXAGONAL_PRISM -> R.string.shape_hexagonal_prism
    ShapeKind.TETRAHEDRON -> R.string.shape_tetrahedron
    ShapeKind.TRUNCATED_CONE -> R.string.shape_truncated_cone
    ShapeKind.HEMISPHERE -> R.string.shape_hemisphere
    ShapeKind.CUBE_NET -> R.string.shape_cube_net
    ShapeKind.AXES_3D -> R.string.shape_axes_3d
    ShapeKind.FUNCTION_PLOT -> R.string.shape_function_plot
    ShapeKind.COORD_GRID -> R.string.shape_coord_grid
    ShapeKind.UNIT_CIRCLE -> R.string.shape_unit_circle
    ShapeKind.BAR_CHART -> R.string.shape_bar_chart
    ShapeKind.LINE_CHART -> R.string.shape_line_chart
    ShapeKind.PIE_CHART -> R.string.shape_pie_chart
    ShapeKind.SCATTER -> R.string.shape_scatter
}
