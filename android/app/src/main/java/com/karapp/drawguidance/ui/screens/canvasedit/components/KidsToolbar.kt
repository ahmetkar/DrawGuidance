package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.tutorialAnchor
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.components.toolHelpClickable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * v45 düğme renginin okunur yazı rengi (WCAG 2.x). Göreli parlaklık sRGB bileşenleri doğrusallaştırılarak
 * (c ≤ 0,03928 ise c/12,92, değilse ((c+0,055)/1,055)^2,4) 0,2126 R + 0,7152 G + 0,0722 B ile hesaplanır; karşıtlık
 * (L1 + 0,05) / (L2 + 0,05). Yazı rengi koyu mürekkep ile beyazdan karşıtlığı yüksek olanıdır.
 */
object KidsContrast {
    /** Koyu yazı (çocuk kartlarının mürekkebi). */
    val DarkInk = Color(0xFF1F1A33)
    val LightInk = Color.White

    /** Normal yazı için WCAG AA (4,5:1) ve yüksek karşıtlıkta AAA (7:1). */
    const val AA = 4.5f
    const val AAA = 7f

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG göreli parlaklık (0 siyah, 1 beyaz). */
    fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    /** İki rengin WCAG karşıtlık oranı (1..21). */
    fun ratio(a: Color, b: Color): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** [bg] zemini üstünde okunur yazı rengi: koyu mürekkep ya da beyaz (karşıtlığı yüksek olan). */
    fun ink(bg: Color): Color = if (ratio(DarkInk, bg) >= ratio(LightInk, bg)) DarkInk else LightInk

    /**
     * Zemini en az [min] karşıtlığa getirir: önce yazı rengi seçilir; yetmiyorsa zemin, yazının tersine (koyu yazıda
     * beyaza, beyaz yazıda siyaha) doğru adım adım karıştırılır. Zemin ve yazı rengi döner.
     */
    fun readable(bg: Color, min: Float): Pair<Color, Color> {
        val ink = ink(bg)
        if (ratio(ink, bg) >= min) return bg to ink
        val toward = if (ink == DarkInk) Color.White else Color.Black
        var t = 0f
        var out = bg
        while (ratio(ink, out) < min && t < 1f) {
            t = (t + 0.05f).coerceAtMost(1f)
            out = lerp(bg, toward, t)
        }
        return out to ink
    }
}

/** v45 çocuk menüsü düğmesinin renkleri: zemin, yazı/simge rengi ve kenar. */
data class KidsTileColors(val background: Color, val content: Color, val border: Color)

/**
 * v45 çocuk menüsünün renkleri: her araç kendi renginde (açık temada canlı ama göz yormayan pastel tonlar, koyu temada
 * aynı renklerin koyulaştırılmış hâli). Yazı rengi zemine göre WCAG ile seçilir: en az 4,5:1, yüksek karşıtlıkta 7:1
 * (gerekirse zemin açılıp koyulaştırılır) ve kenar opak yazı rengindedir.
 */
object KidsToolColors {
    /** Araç kimliği → açık temadaki zemin rengi. */
    val palette: Map<String, Color> = linkedMapOf(
        "undo" to Color(0xFFFFE08A),
        "redo" to Color(0xFFFFE08A),
        "brush" to Color(0xFF9AD0FF),
        "eraser" to Color(0xFFFFB3C7),
        "paint_brush" to Color(0xFFFFB870),
        "paint" to Color(0xFFFFE066),
        "elements" to Color(0xFFC9B6FF),
        "brush_settings" to Color(0xFF9BE8C8),
        "shape" to Color(0xFFA8E6FF),
        "challenge" to Color(0xFFFFD36E),
        "background" to Color(0xFFB8F0E0),
        "image" to Color(0xFFFFC4A8),
        "text" to Color(0xFFD6E4FF),
        "model3d" to Color(0xFFC8F0A0),
        "select" to Color(0xFFE2D1FF),
        "eyedropper" to Color(0xFFFFC2E2),
        "ruler" to Color(0xFFFFD8A8),
        "template" to Color(0xFFB5EAD7),
        "expand" to Color(0xFFCDE7B0),
    )

    /** Listede olmayan araç. */
    val fallback = Color(0xFFE0E7FF)

    /** Koyu temada zeminin karıştırıldığı koyu ton (çocuk kartlarının koyu zemini). */
    private val DarkBase = Color(0xFF1E2233)

    fun colors(id: String, dark: Boolean, highContrast: Boolean): KidsTileColors {
        val base = palette[id] ?: fallback
        val bg = if (dark) lerp(base, DarkBase, 0.62f) else base
        val (background, ink) = KidsContrast.readable(bg, if (highContrast) KidsContrast.AAA else KidsContrast.AA)
        val border = if (highContrast) ink else if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.9f)
        return KidsTileColors(background, ink, border)
    }
}

/** v45 çocuk menüsündeki düğme boyutları (dokunma alanı her ekranda en az 48 dp). */
object KidsToolSizes {
    /** Alçak ekran (yatay telefon), telefon ve tablet için düğmenin en küçük kenarı. */
    val COMPACT: Dp = 56.dp
    val PHONE: Dp = 64.dp
    val TABLET: Dp = 72.dp
    /** Dokunma alanının en küçük kenarı (Android erişilebilirlik). */
    val MIN_TOUCH: Dp = 48.dp

    fun tile(widthDp: Int, heightDp: Int): Dp = when {
        heightDp < 480 -> COMPACT
        min(widthDp, heightDp) >= 600 -> TABLET
        else -> PHONE
    }
}

/** v45 çocuk menüsündeki araçların çocuklara göre kısa adları. */
data class KidsLabels(
    val undo: String,
    val redo: String,
    val pen: String,
    val eraser: String,
    val paintBrush: String,
    val bucket: String,
    val parts: String,
    val colors: String,
    val shapes: String,
    val challenge: String,
    val background: String,
    val image: String,
    val text: String,
    val model3d: String,
    val select: String,
    val eyedropper: String,
    val ruler: String,
    val template: String,
    val expand: String,
) {
    /** Araç kimliği → ad. */
    fun of(id: String): String? = when (id) {
        "undo" -> undo; "redo" -> redo; "brush" -> pen; "eraser" -> eraser; "paint_brush" -> paintBrush; "paint" -> bucket
        "elements" -> parts; "brush_settings" -> colors; "shape" -> shapes; "challenge" -> challenge
        "background" -> background; "image" -> image; "text" -> text; "model3d" -> model3d; "select" -> select
        "eyedropper" -> eyedropper; "ruler" -> ruler; "template" -> template; "expand" -> expand
        else -> null
    }
}

@Composable
fun rememberKidsLabels(): KidsLabels = KidsLabels(
    undo = stringResource(R.string.v45_kids_undo),
    redo = stringResource(R.string.v45_kids_redo),
    pen = stringResource(R.string.v45_kids_pen),
    eraser = stringResource(R.string.v45_kids_eraser),
    paintBrush = stringResource(R.string.v45_kids_paint_brush),
    bucket = stringResource(R.string.v45_kids_bucket),
    parts = stringResource(R.string.v45_kids_parts),
    colors = stringResource(R.string.v45_kids_colors),
    shapes = stringResource(R.string.v45_kids_shapes),
    challenge = stringResource(R.string.v45_kids_challenge),
    background = stringResource(R.string.v45_kids_background),
    image = stringResource(R.string.v45_kids_image),
    text = stringResource(R.string.v45_kids_text),
    model3d = stringResource(R.string.v45_kids_model3d),
    select = stringResource(R.string.v45_kids_select),
    eyedropper = stringResource(R.string.v45_kids_eyedropper),
    ruler = stringResource(R.string.v45_kids_ruler),
    template = stringResource(R.string.v45_kids_template),
    expand = stringResource(R.string.v45_kids_expand),
)

/**
 * v45 çocuk menüsü: basit menünün araçlarına çocuk adları ve daha anlaşılır simgeler verilir, boya fırçası ("Boya")
 * kovanın hemen önüne eklenir (Kalem, Silgi, Boya, Kova, Parçalar, Renkler…; v44 sırası korunur: Elemanlar ve Fırça
 * ayarları kovanın hemen ardında). Boyama modunda yalnız biri seçili görünür: boya fırçası ya da kova.
 */
object KidsMenu {
    /**
     * v45 araç kimliği → çocuklara özgü açıklaması. Basılı tutunca adla değil kimlikle bulunur: bazı dillerde çocuk adı
     * başka bir düğmenin adıyla aynıdır (ör. "Boya" = Boyama modu), adla aransa o düğmenin açıklaması çıkardı.
     */
    val help: Map<String, Int> = mapOf(
        "brush" to R.string.v45_help_kids_pen,
        "paint_brush" to R.string.v45_help_kids_paint_brush,
        "paint" to R.string.v45_help_kids_bucket,
        "elements" to R.string.v45_help_kids_parts,
        "brush_settings" to R.string.v45_help_kids_colors,
        "shape" to R.string.v45_help_kids_shapes,
        "background" to R.string.v45_help_kids_background,
    )

    /** Araç kimliği → çocuk menüsündeki simgesi (listede olmayanlar kendi simgesini korur). */
    val icons: Map<String, ImageVector>
        get() = mapOf(
            "brush" to Icons.Filled.Create,
            "paint_brush" to KidsIcons.PaintBrush,
            "paint" to KidsPaintIcon,
            "brush_settings" to Icons.Filled.Palette,
            "select" to Icons.Filled.TouchApp,
            "expand" to Icons.Filled.OpenInFull,
        )

    /**
     * v47 menünün başı (bu sırayla): Kalem, Silgi, Boya, Kova, sonra Şekiller, Görev, Kalıp, Yazı. Ötekiler (Parçalar,
     * Katmanlar, Renkler, Zemin, Resim, 3B, Seç, Renk al, Cetvel, Büyüt) bunların ardında, kendi sıralarıyla.
     */
    val FRONT = listOf("brush", "eraser", "paint_brush", "paint", "shape", "challenge", "template", "text")

    fun <T> order(items: List<T>, id: (T) -> String): List<T> =
        items.sortedBy { FRONT.indexOf(id(it)).let { i -> if (i < 0) FRONT.size else i } }

    fun decorate(raw: List<SimpleTool>, labels: KidsLabels, brushOn: Boolean, paintMode: Boolean, onPaintBrush: () -> Unit): List<SimpleTool> {
        val out = raw.map { t ->
            val named = t.copy(label = labels.of(t.id) ?: t.label, icon = icons[t.id] ?: t.icon)
            if (t.id == "paint") named.copy(selected = paintMode && !brushOn) else named
        }.toMutableList()
        val brush = SimpleTool("paint_brush", KidsIcons.PaintBrush, labels.paintBrush, paintMode && brushOn, onPaintBrush)
        val at = out.indexOfFirst { it.id == "paint" }
        if (at >= 0) out.add(at, brush) else out.add(brush)
        return out
    }
}

/**
 * v45 çocuk modunun üst menüsü: renkli, yuvarlak köşeli büyük düğmeler (her aracın kendi rengi; yazı rengi zemine göre
 * okunur), adları her zaman simgenin altında. Düğmeler oyuncak rafı gibi yumuşak gradyanlı, yuvarlak bir çerçevede;
 * sığmazsa satır yana kaydırılır (kenarda solma görünür). Seçili düğme kalın kenarlı, adı kalın yazılı; geri al ve
 * yinele menünün başında sabit (kaydırılan bölümün dışında, hep görünür). 2 sn basılı tutunca düğmenin ne işe yaradığı köşedeki kutuda yazar.
 */
@Composable
fun KidsToolRow(
    items: List<SimpleTool>,
    labels: KidsLabels,
    canUndo: Boolean,
    onUndo: () -> Unit,
    canRedo: Boolean,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val tile = KidsToolSizes.tile(configuration.screenWidthDp, configuration.screenHeightDp)
    val colors = NeuTheme.colors
    val history = listOf(
        SimpleTool("undo", Icons.AutoMirrored.Filled.Undo, labels.undo, false, onUndo, canUndo),
        SimpleTool("redo", Icons.AutoMirrored.Filled.Redo, labels.redo, false, onRedo, canRedo),
    )
    val shelf = RoundedCornerShape(26.dp)
    val shelfBrush = if (colors.isDark) Brush.linearGradient(listOf(Color(0xFF3B2E5A), Color(0xFF2B2F45)))
    else Brush.linearGradient(listOf(Color(0xFFFFF7DD), Color(0xFFFFE6F1), Color(0xFFEDE7FF)))
    val shelfBorder = if (colors.highContrast) Brush.linearGradient(listOf(colors.content, colors.content))
    else Brush.linearGradient(listOf(Color(0xFFFFB938), Color(0xFFA78BFA)))
    val fade = if (colors.isDark) Color(0xFF2B2F45) else Color(0xFFFFF0F4)
    // v47 basit modların dikey ekranında menü iki satır, alt alta (geri al ve yinele üst satırın başında sabit)
    val stacked = LocalToolRowSplit.current == ToolRowSplitMode.STACK_ALWAYS
    val at = if (stacked) ToolRowSplit.index(items.size) else items.size
    val parts = if (stacked && at in 1 until items.size) listOf(items.subList(0, at), items.subList(at, items.size)) else listOf(items)
    Box(
        modifier
            .clip(shelf)
            .background(shelfBrush)
            .border(2.dp, shelfBorder, shelf)
            .semantics { stateDescription = "tile:${tile.value.toInt()}" }
            .testTag("kids_tool_shelf"),
    ) {
        Column(Modifier.testTag("simple_tool_row").fillMaxWidth()) {
            parts.forEachIndexed { i, part ->
                Row(
                    Modifier
                        .testTag("simple_tool_row_${i + 1}")
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // v45 geri al ve yinele hep görünür (kaydırılan satırın dışında): çocuk menüyü kaydırınca kaybolmasın
                    if (i == 0) {
                        history.forEach { item ->
                            val anchor = if (item.id == "undo") TutorialAnchor.UNDO else TutorialAnchor.REDO
                            KidsToolTile(item, tile, Modifier.testTag("simple_tool_${item.id}").tutorialAnchor(anchor))
                        }
                    }
                    Box(Modifier.weight(1f)) { KidsScrollPart(part, tile, fade, if (i == 0) "kids_tool_scroll" else "kids_tool_scroll_2") }
                }
            }
        }
    }
}

/** v45 kaydırılan araç bölümü (dörderli gruplar; seçili düğmeye hizalanır; kenarda solma). */
@Composable
private fun KidsScrollPart(items: List<SimpleTool>, tile: Dp, fade: Color, tag: String) {
    val scroll = rememberScrollState()
    // v45 (12) kaydırılan satır seçili düğmeye hizalanır (çocuk kaydırınca bir sonraki seçime kadar durur)
    val aligner = rememberToolRowAligner(scroll, items.firstOrNull { it.selected }?.id)
    Box {
        Row(
            Modifier
                .testTag(tag)
                .onGloballyPositioned { aligner.viewport = it; aligner.requestAlign() }
                .horizontalScroll(scroll)
                // Seçili düğme %4 büyür: kaydırma kırpmasında kenarları kesilmesin diye içte pay
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // v48 gruplama yok (geri al/yinele satırın dışında, sabit)
            GroupedToolSlots(
                items.map { item ->
                    ToolSlot(item.id, item.selected) {
                        KidsToolTile(item, tile, Modifier.testTag("simple_tool_${item.id}").tutorialAnchor(TutorialAnchor.simple(item.id)))
                    }
                },
                aligner,
            )
        }
        // Kaydırılabilen kenarda solma: menünün devamı olduğu anlaşılsın
        if (scroll.canScrollForward) Box(
            Modifier.align(Alignment.CenterEnd).width(22.dp).height(tile)
                .background(Brush.horizontalGradient(listOf(fade.copy(alpha = 0f), fade))),
        )
        if (scroll.canScrollBackward) Box(
            Modifier.align(Alignment.CenterStart).width(22.dp).height(tile)
                .background(Brush.horizontalGradient(listOf(fade, fade.copy(alpha = 0f)))),
        )
    }
}

/** Tek düğme: renkli yuvarlak kare, ortada simge, altında adı. */
@Composable
private fun KidsToolTile(item: SimpleTool, tile: Dp, modifier: Modifier) {
    val theme = NeuTheme.colors
    val c = remember(item.id, theme.isDark, theme.highContrast) { KidsToolColors.colors(item.id, theme.isDark, theme.highContrast) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val help = com.karapp.drawguidance.ui.components.LocalToolHelp.current
    val own = KidsMenu.help[item.id]?.let { stringResource(it) }
    val click = if (help != null) {
        Modifier.toolHelpClickable(
            help, item.label, item.enabled, Role.Button, interaction, showName = false,
            description = { own ?: help.describe(item.label) }, onClick = item.onClick,
        )
    } else Modifier.clickable(interactionSource = interaction, indication = null, enabled = item.enabled, role = Role.Button, onClick = item.onClick)
    val shape = RoundedCornerShape(tile * 0.3f)
    val alpha = if (item.enabled) 1f else 0.45f
    val icon = when {
        tile >= KidsToolSizes.TABLET -> 30.dp
        tile >= KidsToolSizes.PHONE -> 27.dp
        else -> 23.dp
    }
    val fontSize = if (tile >= KidsToolSizes.TABLET) 13.sp else if (tile >= KidsToolSizes.PHONE) 12.sp else 11.sp
    com.karapp.drawguidance.ui.components.ToolHold {
        Column(
            modifier
                .heightIn(min = tile)
                .widthIn(min = tile)
                // v45 seçili düğme büyütülmez (kaydırılan satırda kenarı kesilirdi); kalın kenar ve kalın yazıyla belli olur
                .scale(if (pressed) 0.94f else 1f)
                .clip(shape)
                .background(c.background.copy(alpha = c.background.alpha * alpha))
                .border(if (item.selected) 3.dp else if (theme.highContrast) 2.dp else 1.5.dp, if (item.selected) c.content else c.border, shape)
                .then(click)
                .semantics { contentDescription = item.label; if (item.selected) selected = true }
                .padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Icon(item.icon, null, tint = c.content.copy(alpha = alpha), modifier = Modifier.size(icon))
            Text(
                item.label, color = c.content.copy(alpha = alpha), fontSize = fontSize, lineHeight = fontSize * 1.15f,
                fontWeight = if (item.selected) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1, softWrap = false, translate = false,
                modifier = Modifier.clearAndSetSemantics { testTag = "button_label:${item.label}" },
            )
        }
    }
}

/** v45 fırça boyu seçenekleri (tuval birimi): İnce, Orta, Kalın. */
object KidsBrushSizes {
    val sizes: List<Float> = listOf(10f, 24f, 48f)

    /** [sizePx] boyuna en yakın seçenek. */
    fun nearest(sizePx: Float): Float = sizes.minBy { kotlin.math.abs(it - sizePx) }
}

/**
 * v45 boyama satırı: boya fırçası seçiliyse üstte "Fırça boyu" (İnce, Orta, Kalın; çocuk fırçasının boyunu değiştirir),
 * altında çocuk paleti.
 */
@Composable
fun KidsPaintBar(selectedHex: String?, onColor: (String) -> Unit, brushOn: Boolean, sizePx: Float, onSize: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (brushOn) KidsBrushSizeRow(sizePx, onSize)
        KidsPalette(selectedHex = selectedHex, onColor = onColor)
    }
}

@Composable
private fun KidsBrushSizeRow(sizePx: Float, onSize: (Float) -> Unit) {
    val names = listOf(stringResource(R.string.v45_kids_size_thin), stringResource(R.string.v45_kids_size_medium), stringResource(R.string.v45_kids_size_thick))
    val title = stringResource(R.string.v45_kids_brush_size)
    val current = KidsBrushSizes.nearest(sizePx)
    val theme = NeuTheme.colors
    Row(
        Modifier.fillMaxWidth().testTag("kids_brush_size"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = theme.content, fontWeight = FontWeight.Bold, fontSize = 14.sp, translate = false, modifier = Modifier.weight(1f))
        KidsBrushSizes.sizes.forEachIndexed { i, size ->
            val on = size == current
            val c = KidsToolColors.colors("paint_brush", theme.isDark, theme.highContrast)
            Box(
                Modifier
                    .size(KidsToolSizes.MIN_TOUCH)
                    .clip(CircleShape)
                    .background(if (on) c.background else c.background.copy(alpha = 0.35f))
                    .border(if (on) 3.dp else 1.dp, if (on) c.content else c.content.copy(alpha = 0.4f), CircleShape)
                    .clickable(role = Role.Button) { onSize(size) }
                    .semantics { contentDescription = names[i]; if (on) selected = true }
                    .testTag("kids_brush_size_$i"),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(8.dp + 8.dp * i).clip(CircleShape).background(c.content))
            }
        }
    }
}
