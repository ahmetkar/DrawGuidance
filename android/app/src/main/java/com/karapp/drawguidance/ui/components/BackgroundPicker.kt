package com.karapp.drawguidance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.paper.ImageLayout
import com.karapp.drawguidance.content.paper.PaperCatalog
import com.karapp.drawguidance.content.paper.PaperImages
import com.karapp.drawguidance.content.paper.PaperUsage
import com.karapp.drawguidance.content.paper.PatternColors
import com.karapp.drawguidance.data.images.CanvasImageCache
import com.karapp.drawguidance.data.library.PaperItem
import com.karapp.drawguidance.data.library.PaperLibrary
import com.karapp.drawguidance.data.library.PaperRepository
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.BoxFit
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.drawing.BackgroundRenderer
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

/** Desen renkleri (ana renk; açık ve koyu tonları desende ondan türer). */
val PatternColorSwatches = listOf("#7C89A6", "#7C9CC9", "#6FA287", "#D9935B", "#E57373", "#9C7CC9", "#8C95A3", "#3D4A5C", "#C9A227", "#FFFFFF")

/** Arka plan kütüphanesi (veritabanından; uygulama dışı testlerde katalogdan). */
@Composable
fun rememberPaperItems(): List<PaperItem> {
    val repo = remember { runCatching { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<PaperRepository>() }.getOrNull() }
    if (repo != null) {
        val items by repo.items.collectAsState()
        return items
    }
    return remember {
        PaperCatalog.all.map { PaperItem(it.id, it.name, it.usage, false, null) } +
            PaperImages.all.map { PaperItem(it.id, it.name, it.usage, true, it.layout) }
    }
}

/**
 * v23 arka plan seçimi: desen (tuval türüne göre 30 hazır desen; rengi, ölçeği ve örtücülüğü) ve resim (hazır resimler
 * ve galeriden; yerleşimi ve örtücülüğü). Resmin üstüne desen de verilebilir. Tuval formunda ve çizim ekranında aynı.
 */
@Composable
fun BackgroundPicker(
    usage: PaperUsage,
    paperHex: String,
    spec: BackgroundSpec,
    onChange: (BackgroundSpec) -> Unit,
    modifier: Modifier = Modifier,
    onGallery: (() -> Unit)? = null,
    /** Galeriden seçilmiş (henüz eklenmemiş) resmin önizlemesi. */
    galleryPreview: (@Composable (Modifier) -> Unit)? = null,
    /**
     * v42 resim bölümü gösterilir mi: çizgi romanda hayır (çizgi roman sayfalarının resimleri yalnız "Mekân arka planı"
     * penceresinde; öteki modların kağıt resimleri çizgi romanda sunulmaz). Var olan resmin sığdırma ve saydamlık
     * ayarları yine görünür.
     */
    showImages: Boolean = true,
) {
    val colors = NeuTheme.colors
    val items = rememberPaperItems()
    val patterns = remember(items, usage) { items.filter { it.usage == usage && !it.isImage } }
    val images = remember(items, usage) { items.filter { it.usage == usage && it.isImage && !it.id.startsWith(LEGACY_SCENE_PREFIX) } }
    val paper = remember(paperHex) { hexToColor(paperHex).toArgb() }
    val imagesVersion by CanvasImageCache.version.collectAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // v27 gradyan kağıt (kağıt renginin yerine; resim ve desen üstüne)
        NeuSectionTitle(stringResource(R.string.gradient_title))
        GradientPicker(spec.gradient, onChange = { onChange(spec.copy(gradient = it)) })
        NeuSectionTitle(stringResource(R.string.bg_pattern))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().testTag("bg_patterns")) {
            item { NoneTile(selected = spec.patternId == null) { onChange(spec.copy(patternId = null)) } }
            items(patterns, key = { it.id }) { item ->
                Tile(item.name, spec.patternId == item.id, onClick = {
                    onChange(spec.copy(patternId = item.id))
                }) { m -> PatternThumb(item.id, spec.patternColorHex, paper, m) }
            }
        }
        if (spec.patternId != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                val current = spec.patternColorHex
                PatternColorSwatches.forEach { hex ->
                    val chosen = current?.equals(hex, ignoreCase = true) ?: (hex == PatternColorSwatches.first())
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .border(if (chosen) 3.dp else 1.dp, if (chosen) colors.accent else colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                            .padding(4.dp).background(hexToColor(hex), CircleShape)
                            .clickable { onChange(spec.copy(patternColorHex = hex)) }
                            .semantics { contentDescription = hex; if (chosen) selected = true },
                    )
                }
            }
            LabeledValueSlider(stringResource(R.string.bg_scale), "${(spec.patternScale * 100).roundToInt()}%", spec.patternScale, 0.4f..3f) {
                onChange(spec.copy(patternScale = it))
            }
            LabeledValueSlider(stringResource(R.string.bg_opacity), "${(spec.patternOpacity * 100).roundToInt()}%", spec.patternOpacity, 0.1f..1f) {
                onChange(spec.copy(patternOpacity = it))
            }
        }
        if (showImages) NeuSectionTitle(stringResource(R.string.bg_image))
        if (showImages) LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().testTag("bg_images")) {
            item { NoneTile(selected = spec.imageId == null) { onChange(spec.copy(imageId = null)) } }
            if (onGallery != null) item {
                val chosen = spec.assetId != null || (galleryPreview != null && spec.imageId == GALLERY_PENDING)
                Tile(stringResource(R.string.bg_gallery), chosen, onClick = onGallery) { m ->
                    if (galleryPreview != null && spec.imageId == GALLERY_PENDING) galleryPreview(m)
                    else {
                        val bmp = spec.assetId?.let { CanvasImageCache.get(it) }
                        if (bmp != null && imagesVersion >= 0) androidx.compose.foundation.Image(bmp, null, m, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        else Box(m.background(colors.background), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PhotoLibrary, null, tint = colors.accent, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
            items(images, key = { it.id }) { item ->
                Tile(item.name, spec.paperImageId == item.id, onClick = {
                    val fit = if (item.layout?.kind == ImageLayout.TILE) BoxFit.TILE else BoxFit.COVER
                    onChange(spec.copy(imageId = BackgroundSpec.PAPER + item.id, imageFit = fit))
                }) { m -> ImageThumb(item.id, m, imagesVersion) }
            }
        }
        // v41 hazır manga / çizgi roman arka planları burada değil: çizgi roman düzenleyicisinde "Mekân arka planı ekle"
        if (spec.imageId != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(BoxFit.COVER to R.string.fit_cover, BoxFit.TILE to R.string.fit_tile, BoxFit.STRETCH to R.string.fit_stretch).forEach { (fit, label) ->
                    NeuChip(stringResource(label), spec.imageFit == fit, onClick = { onChange(spec.copy(imageFit = fit)) })
                }
            }
            LabeledValueSlider(stringResource(R.string.bg_image_opacity), "${(spec.imageOpacity * 100).roundToInt()}%", spec.imageOpacity, 0.1f..1f) {
                onChange(spec.copy(imageOpacity = it))
            }
        }
    }
}

/** v36 çizilen sahnelerin kimlik öneki: v40'ta kaldırıldı; daha önce kullanılanlar veritabanındaki kopyalarından açılır. */
const val LEGACY_SCENE_PREFIX = "scene."

/** Galeriden seçilip tuval oluşturulunca eklenecek resmin geçici kimliği (formda). */
const val GALLERY_PENDING = "pending:gallery"

@Composable
fun LabeledValueSlider(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val colors = NeuTheme.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = colors.accent)
        }
        NeuSlider(current, onChange, range)
    }
}

@Composable
private fun NoneTile(selected: Boolean, onClick: () -> Unit) {
    val colors = NeuTheme.colors
    Tile(stringResource(R.string.bg_none), selected, onClick) { m ->
        Box(m.background(colors.background), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Block, null, tint = colors.contentMuted, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun Tile(name: String, selected: Boolean, onClick: () -> Unit, size: Dp = 72.dp, modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    val colors = NeuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.width(size + 8.dp).clip(shape).clickable(onClick = onClick).semantics { contentDescription = name; if (selected) this.selected = true }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(size).clip(shape)
                .border(if (selected) 3.dp else 1.dp, if (selected) colors.accent else colors.contentMuted.copy(alpha = 0.3f), shape),
        ) { content(Modifier.fillMaxSize().clip(shape)) }
        Text(
            name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) colors.accent else colors.content,
        )
    }
}

/** Desenin küçük önizlemesi: kağıt renginde, sayfanın ~300 birimlik bir köşesi. */
@Composable
fun PatternThumb(id: String, colorHex: String?, paper: Int, modifier: Modifier, span: Float = 300f) {
    Canvas(modifier.background(androidx.compose.ui.graphics.Color(paper))) {
        val p = PaperLibrary.pattern(id) ?: return@Canvas
        val main = colorHex?.let { BackgroundRenderer.parseColor(it, p.color) } ?: p.color
        val k = size.width / span
        drawIntoCanvas {
            BackgroundRenderer.drawPattern(it.nativeCanvas, p, PatternColors(main, paper, p.accent), 0f, 0f, size.width, size.height, k, vector = true)
        }
    }
}

/** Hazır resmin küçük önizlemesi (yüklenince görünür). */
@Composable
fun ImageThumb(id: String, modifier: Modifier, version: Int) {
    val colors = NeuTheme.colors
    Canvas(modifier.background(colors.background)) {
        if (version < 0) return@Canvas
        val bmp = PaperLibrary.image(id)?.asAndroidBitmap() ?: return@Canvas
        val layout = PaperLibrary.layout(id)
        drawIntoCanvas {
            val fit = when (layout.kind) {
                ImageLayout.NINE -> BoxFit.NINE_SLICE
                else -> BoxFit.COVER
            }
            BackgroundRenderer.drawImage(it.nativeCanvas, bmp, fit, layout, 0f, 0f, size.width, size.height, size.width / 240f, 1f)
        }
    }
}
