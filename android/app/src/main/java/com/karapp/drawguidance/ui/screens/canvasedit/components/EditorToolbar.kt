package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.Palette
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.tutorialAnchor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.ui.components.toolHelpClickable
import androidx.compose.ui.semantics.selected
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.BrushIcons
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.PanelTab
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic

@Composable
fun modeLabel(mode: EditMode): String = stringResource(
    when (mode) {
        EditMode.DRAW -> R.string.mode_draw
        EditMode.SELECT -> R.string.mode_select
        EditMode.MOVE -> R.string.mode_move
        EditMode.ROTATE -> R.string.mode_rotate
        EditMode.SCALE -> R.string.mode_scale
        EditMode.EXPAND -> R.string.mode_expand
        EditMode.CROP -> R.string.mode_crop
        EditMode.SHAPE -> R.string.mode_shape
        EditMode.TEXT -> R.string.mode_text
        EditMode.DIAGRAM -> R.string.mode_diagram
        EditMode.SHAPE_EDIT -> R.string.mode_shape_edit
        EditMode.GUIDE -> R.string.add_guide
        EditMode.PERSPECTIVE -> R.string.add_perspective
        EditMode.DIAGRAM_EDIT -> R.string.diagram_edit
        EditMode.ADD -> R.string.mode_add
        EditMode.EDIT_PICK -> R.string.mode_edit_pick
        EditMode.FORMULA -> R.string.mode_formula
        EditMode.IMAGE_EDIT -> R.string.mode_image_edit
        EditMode.PEN -> R.string.mode_pen
        EditMode.PAINT -> R.string.mode_paint
    },
)

private fun modeIcon(mode: EditMode): ImageVector = when (mode) {
    EditMode.DRAW -> Icons.Filled.Draw
    EditMode.SELECT -> Icons.Filled.HighlightAlt
    EditMode.MOVE -> Icons.Filled.OpenWith
    EditMode.ROTATE -> Icons.AutoMirrored.Filled.RotateRight
    EditMode.SCALE -> Icons.Filled.OpenInFull
    EditMode.EXPAND -> Icons.Filled.AspectRatio
    EditMode.CROP -> Icons.Filled.Crop
    EditMode.SHAPE -> Icons.Filled.Category
    EditMode.TEXT -> Icons.Filled.TextFields
    EditMode.DIAGRAM -> Icons.Filled.AccountTree
    EditMode.SHAPE_EDIT -> Icons.Filled.ContentCut
    EditMode.GUIDE -> Icons.Filled.GridOn
    EditMode.PERSPECTIVE -> Icons.Filled.ViewInAr
    EditMode.DIAGRAM_EDIT -> Icons.Filled.AccountTree
    EditMode.ADD -> Icons.Filled.AddCircleOutline
    EditMode.EDIT_PICK -> Icons.Filled.Edit
    EditMode.FORMULA -> Icons.Filled.Functions
    EditMode.IMAGE_EDIT -> Icons.Filled.Image
    EditMode.PEN -> Icons.Filled.Gesture
    EditMode.PAINT -> Icons.Filled.FormatPaint
}

/** v17 üst menüde moda özel bir düğme (simge; ad erişilebilirlik açıklaması ve uzun basınca ipucu). */
data class ToolItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    /** v32 eğitim modunda tanıtılan aracın yeri (açıklama kutusu bu aracın altında açılır). */
    val tag: String? = null,
)

/**
 * Araç düğmeleri (üst menü). Çiz modunda: geri al / yinele · fırça, silgi, boya kutusu · elemanlar ve fırça ayarları
 * panelleri (v18: eklenecekler yalnız Ekle modunda, kırpma Düzenle modunda). [items] verilirse (Seç, Taşı, Ekle,
 * Düzenle, Genişlet) geri al / yinele'nin yanında moda özel düğmeler durur; [leading] ve [trailing] düğmelerin önüne
 * ve arkasına bilgi çipleri koyar (ör. Taşı'da konum, Genişlet'te boyut). Sığmazsa satır kaydırılır.
 */
@Composable
fun EditorToolRow(
    panel: PanelTab?,
    brushType: BrushType,
    drawingBrush: BrushType,
    brushColorHex: String,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onOpenBrushPicker: () -> Unit,
    onToggleEraser: () -> Unit,
    onTogglePanel: (PanelTab) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    onFill: () -> Unit = {},
    /** Ekleme/düzenleme modlarında kalem ve silgi pasif. */
    drawToolsEnabled: Boolean = true,
    canRedo: Boolean = false,
    onRedo: () -> Unit = {},
    /** v17: moda özel düğmeler (null: çizim araçları). */
    items: List<ToolItem>? = null,
    /** v18: moda özel düğmelerin önündeki / arkasındaki bilgi çipleri. */
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** v24 Çiz satırında arka plan (kağıt rengi, desen, resim) düğmesi. */
    onBackground: (() -> Unit)? = null,
    /** v27 Kalem aracı (yol): yalnız Çiz satırında. */
    onPen: (() -> Unit)? = null,
    penActive: Boolean = false,
    /** v42 Çiz satırında Cetvel ve Şekil kalıbı (⋮ menüsünden taşındı; açıkken basınca kapanır). */
    onRuler: (() -> Unit)? = null,
    rulerActive: Boolean = false,
    onTemplate: (() -> Unit)? = null,
    templateActive: Boolean = false,
    /** v43 damlalık (tuvalden renk al) Çiz satırında. */
    onEyedropper: (() -> Unit)? = null,
    eyedropperActive: Boolean = false,
) {
    // Tam genişlikte (telefon) düğmeler biraz küçülür: 9 araç 360 dp'ye sığar; v27 Çiz satırında Kalem de varken
    // (10 düğme) bir kademe daha küçülür ki Katmanlar ekrana sığsın
    // v34: moda özel satırda 10 ve daha çok düğme varsa (Düzenle: nokta/çizgi/alan boya da satırda) aynı küçülme; uzun satırlarda (Resmi düzenle) daha çok düğme görünür
    val crowded = (items == null && onPen != null && onBackground != null) || (items != null && items.size >= 7)
    // v42 Cetvel ve Şekil kalıbı da varken (12 düğme) bir kademe daha küçülür; sığmazsa satır kayar
    val packed = items == null && onRuler != null && onTemplate != null
    // v45 adı içinde yazan düğmeler (gelişmiş modların dikey ekranı) hep aynı boyda; satır sığmazsa kayar
    val inline = com.karapp.drawguidance.ui.components.LocalInlineLabels.current
    val button = if (inline) 36.dp else if (fillWidth) (if (packed) 32.dp else if (crowded) 34.dp else 36.dp) else 38.dp
    val colors = NeuTheme.colors
    val undoText = stringResource(R.string.undo)
    val redoText = stringResource(R.string.redo)
    // v45 düğmeler yer listesi olarak verilir: dörderli gruplar ve ayraçlar listeden çıkar (yeni düğme = yeni yer)
    val history = listOf(
        ToolSlot("undo") { NeuIconButton(Icons.AutoMirrored.Filled.Undo, undoText, onUndo, enabled = canUndo, size = button, modifier = Modifier.tutorialAnchor(TutorialAnchor.UNDO)) },
        ToolSlot("redo") { NeuIconButton(Icons.AutoMirrored.Filled.Redo, redoText, onRedo, enabled = canRedo, size = button, modifier = Modifier.tutorialAnchor(TutorialAnchor.REDO)) },
    )
    // v47 Elemanlar, Katmanlar ve Özellikler üst satırda değil: dikey ekranda mod menüsünün son grubunda, yatayda
    // açılır alanın solundaki rayda (PanelMenu)
    if (items != null) {
        // Moda özel satır: sığarsa satıra yayılır, sığmazsa kaydırılır
        val slots = buildList {
            addAll(history)
            if (leading != null) add(ToolSlot("leading") { leading() })
            items.forEachIndexed { i, item ->
                add(ToolSlot("item$i:${item.label}", selected = item.selected) {
                    NeuIconButton(
                        item.icon, item.label, item.onClick, size = button, selected = item.selected, enabled = item.enabled,
                        modifier = Modifier.tutorialAnchor(item.tag),
                        tint = when {
                            !item.enabled -> null
                            item.danger -> colors.danger
                            item.selected -> colors.accent
                            else -> null
                        },
                    )
                })
            }
            if (trailing != null) add(ToolSlot("trailing") { trailing() })
        }
        ToolRowFrame(modifier, "tool_row_context", fillWidth, slots)
        return
    }
    val brushText = stringResource(R.string.brush_select)
    val eraserText = stringResource(R.string.brush_eraser)
    val eyedropperText = stringResource(R.string.v43_eyedropper)
    val penText = stringResource(R.string.add_pen)
    val rulerText = stringResource(R.string.v40_ruler)
    val templateText = stringResource(R.string.v40_template)
    val fillText = stringResource(R.string.fill_color)
    val backgroundText = stringResource(R.string.add_background)
    val eraserOn = drawToolsEnabled && brushType == BrushType.ERASER
    // Tam genişlikte düğmeler satıra eşit aralıkla yayılır; dar ekranda satır kaydırılır
    val slots = buildList {
        addAll(history)
        add(ToolSlot("brush") {
            NeuIconButton(
                BrushIcons.of(drawingBrush), brushText, onOpenBrushPicker, size = button, enabled = drawToolsEnabled,
                tint = if (brushType == BrushType.ERASER) null else hexToColor(brushColorHex),
                modifier = Modifier.tutorialAnchor(TutorialAnchor.BRUSH),
            )
        })
        add(ToolSlot("eraser", selected = eraserOn) {
            NeuIconButton(
                BrushIcons.Eraser, eraserText, onToggleEraser, size = button, enabled = drawToolsEnabled, selected = eraserOn,
                modifier = Modifier.tutorialAnchor(TutorialAnchor.ERASER),
            )
        })
        // v43 damlalık: tuvalde dokunulan yerin rengi fırça rengi olur
        if (onEyedropper != null) add(ToolSlot("eyedropper", selected = eyedropperActive) {
            NeuIconButton(
                Icons.Filled.Colorize, eyedropperText, onEyedropper, size = button, selected = eyedropperActive,
                enabled = drawToolsEnabled, modifier = Modifier.testTag("tool_eyedropper"),
            )
        })
        // v27 Kalem aracı (dokunarak köşe, sürükleyerek eğri): Ekle menüsünden buraya taşındı
        if (onPen != null) add(ToolSlot("pen", selected = penActive) {
            NeuIconButton(
                Icons.Filled.Gesture, penText, onPen, size = button, selected = penActive,
                modifier = Modifier.testTag("tool_pen").tutorialAnchor(TutorialAnchor.PEN),
            )
        })
        // v42 Cetvel ve Şekil kalıbı (başlıktaki ⋮ menüsünden taşındı)
        if (onRuler != null) add(ToolSlot("ruler", selected = rulerActive) {
            NeuIconButton(
                Icons.Filled.Straighten, rulerText, onRuler, size = button, selected = rulerActive,
                modifier = Modifier.testTag("tool_ruler").tutorialAnchor(TutorialAnchor.RULER),
            )
        })
        if (onTemplate != null) add(ToolSlot("template", selected = templateActive) {
            NeuIconButton(
                V44Icons.Template, templateText, onTemplate, size = button, selected = templateActive,
                modifier = Modifier.testTag("tool_template").tutorialAnchor(TutorialAnchor.TEMPLATE),
            )
        })
        // Boya kutusu: seçim yoksa arka plan, arka planı olan eleman seçiliyse onun dolgusu
        add(ToolSlot("fill") { NeuIconButton(Icons.Filled.FormatColorFill, fillText, onFill, size = button, modifier = Modifier.tutorialAnchor(TutorialAnchor.FILL)) })
        if (onBackground != null) add(ToolSlot("background") {
            NeuIconButton(Icons.Filled.Wallpaper, backgroundText, onBackground, size = button, modifier = Modifier.tutorialAnchor(TutorialAnchor.BACKGROUND))
        })
    }
    ToolRowFrame(modifier, "tool_row_draw", fillWidth, slots)
}

/**
 * Üst menü satırı: [fillWidth] iken düğmeler sığıyorsa satıra eşit aralıkla yayılır, sığmıyorsa en az
 * [ToolRowMinGap] aralıkla dizilip satır yana kaydırılır (son düğmelere kaydırarak ulaşılır; kaydırılabilen kenarda
 * hafif bir solma görünür). Kaydırma alanı düğme gölgeleri kesilmesin diye satırın dışına [ToolRowBleed] kadar taşar;
 * satırın yerleşimdeki boyu değişmez.
 */
@Composable
private fun ToolRowFrame(modifier: Modifier, tag: String, fillWidth: Boolean, slots: List<ToolSlot>) {
    // v46 üst menü iki parça: yatay ekranda yan yana, dikey ekranda sığmazsa alt alta (bkz. LocalToolRowSplit)
    val split = LocalToolRowSplit.current
    val at = ToolRowSplit.index(slots.size)
    if (split == ToolRowSplitMode.NONE || at <= 0 || at >= slots.size) {
        ToolRowPart(modifier.testTag(tag), "${tag}_1", fillWidth, slots)
        return
    }
    val first = slots.subList(0, at)
    val second = slots.subList(at, slots.size)
    if (split == ToolRowSplitMode.STACK_ALWAYS) {
        // v47 basit modların dikey ekranı: her zaman iki satır, alt alta (üstte ilk yarı)
        Column(modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(ToolRowStackGap)) {
            ToolRowPart(Modifier, "${tag}_1", fillWidth, first)
            ToolRowPart(Modifier, "${tag}_2", fillWidth, second)
        }
        return
    }
    if (split == ToolRowSplitMode.SIDE) {
        Row(modifier.testTag(tag), verticalAlignment = Alignment.CenterVertically) {
            ToolRowPart(Modifier.weight(first.size.toFloat(), fill = false), "${tag}_1", false, first)
            ToolRowPartDivider()
            ToolRowPart(Modifier.weight(second.size.toFloat(), fill = false), "${tag}_2", false, second)
        }
        return
    }
    // STACK: tek satıra sığıyorsa tek satır, sığmıyorsa (satır kaydırılabiliyorsa) iki satır alt alta. Sığma, satırın
    // kendisinden okunur (ayrı bir ölçüm kopyası yok: kopyanın düğmeleri erişilebilirlik ağacında ikinci kez görünüyordu)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.testTag(tag)) {
        var stacked by androidx.compose.runtime.remember(slots.size, maxWidth) { androidx.compose.runtime.mutableStateOf(false) }
        if (!stacked) {
            ToolRowPart(Modifier, "${tag}_1", fillWidth, slots, onOverflow = { if (it) stacked = true })
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ToolRowStackGap)) {
                ToolRowPart(Modifier, "${tag}_1", fillWidth, first)
                ToolRowPart(Modifier, "${tag}_2", fillWidth, second)
            }
        }
    }
}

/** v46 iki menü parçası arasındaki ayraç (yan yana iken). */
@Composable
private fun ToolRowPartDivider() {
    Box(
        Modifier.padding(horizontal = 8.dp).width(2.dp).height(30.dp)
            .background(NeuTheme.colors.accent.copy(alpha = 0.35f))
            .testTag(TOOL_PART_DIVIDER),
    )
}

/** v46 alt alta iki menü satırı arasındaki boşluk. */
private val ToolRowStackGap = 8.dp

/** Tek menü satırı (parça): kaydırma, seçili düğmeye hizalama, kenar solması. */
@Composable
private fun ToolRowPart(
    modifier: Modifier, tag: String, fillWidth: Boolean, slots: List<ToolSlot>,
    /** v46 satır sığmıyor (kaydırılabiliyor) mu: dikeyde iki satıra bölmek için. */
    onOverflow: ((Boolean) -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    if (onOverflow != null) androidx.compose.runtime.LaunchedEffect(scroll.maxValue) { onOverflow(scroll.maxValue > 0) }
    // v45 seçili düğmeye hizalama (kullanıcı kaydırınca bir sonraki seçime kadar durur)
    val aligner = rememberToolRowAligner(scroll, slots.firstOrNull { it.selected }?.key)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val row = Modifier
            .testTag(tag)
            .bleed(ToolRowBleed)
            .onGloballyPositioned { aligner.viewport = it; aligner.requestAlign() }
            .horizontalScroll(scroll)
        if (fillWidth) {
            Row(
                row.widthIn(min = maxWidth + ToolRowBleed * 2).padding(ToolRowBleed),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = SpreadOrScroll,
            ) { GroupedToolSlots(slots, aligner) }
        } else {
            Row(
                row.padding(ToolRowBleed),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { GroupedToolSlots(slots, aligner) }
        }
        // Kaydırılabilen kenarda solma: satırın devamı olduğu anlaşılsın
        val fade = NeuTheme.colors.background
        if (scroll.canScrollForward) EdgeFade(fade, end = true, Modifier.align(Alignment.CenterEnd))
        if (scroll.canScrollBackward) EdgeFade(fade, end = false, Modifier.align(Alignment.CenterStart))
    }
}

@Composable
private fun EdgeFade(color: androidx.compose.ui.graphics.Color, end: Boolean, modifier: Modifier) {
    val colors = if (end) listOf(color.copy(alpha = 0f), color) else listOf(color, color.copy(alpha = 0f))
    Box(
        modifier
            .width(28.dp)
            // Düğme boyu kadar (v38 yatayda adıyla birlikte): satırın yerleşimdeki boyunu değiştirmez
            .height(if (com.karapp.drawguidance.ui.components.LocalButtonLabels.current) 56.dp else 36.dp)
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(colors)),
    )
}

/** Düğme gölgeleri için kaydırma alanının satır dışına taşma payı. */
private val ToolRowBleed = 10.dp

/** İçeriği her yönde [amount] kadar büyük ölçüp taşırır; yerleşimde asıl boyu bildirir. */
private fun Modifier.bleed(amount: androidx.compose.ui.unit.Dp) = this.then(
    Modifier.layout { measurable, constraints ->
        val px = amount.roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = 2 * px, vertical = 2 * px))
        layout((placeable.width - 2 * px).coerceAtLeast(0), (placeable.height - 2 * px).coerceAtLeast(0)) {
            placeable.place(-px, -px)
        }
    },
)

// v24: Çiz satırına Arka plan eklendi; dar ekranda 9 düğme daha az kaydırılsın (v27: 10 düğme için 3 dp)
private val ToolRowMinGap = 3.dp

/** Yer varsa aralığı eşit büyütür (SpaceBetween gibi), yoksa en az [ToolRowMinGap] bırakır. */
private object SpreadOrScroll : Arrangement.Horizontal {
    override val spacing = ToolRowMinGap
    override fun androidx.compose.ui.unit.Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        outPositions: IntArray,
    ) {
        if (sizes.isEmpty()) return
        val minGap = ToolRowMinGap.roundToPx()
        val n = sizes.size
        val free = totalSize - sizes.sum()
        // v24 yayılırken kalan pikseller aralıklara paylaştırılır: son düğme tam sağ kenarda
        val spread = n > 1 && free >= minGap * (n - 1)
        var used = 0
        sizes.forEachIndexed { i, size ->
            val x = used + if (spread) Math.round(free.toFloat() * i / (n - 1)) else minGap * i
            outPositions[i] = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) totalSize - x - size else x
            used += size
        }
    }
}

/** v18 üst menüde bilgi çipi (Taşı'da konum, Genişlet'te boyut); dokununca değer girilir. */
@Composable
fun ToolInfoChip(
    text: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null,
    /** v33 basılı tutunca açıklaması gösterilen ad (değişken metinli çiplerde sabit ad). */
    helpLabel: String? = null,
) {
    val colors = NeuTheme.colors
    NeuSurface(
        shape = NeuPillShape,
        elevation = 3.dp,
        onClick = onClick,
        helpLabel = helpLabel ?: description,
        // Düğmelerle aynı yükseklik: mod değişince satır (ve tuval) boyu değişmesin
        modifier = modifier.height(36.dp).semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(Modifier.padding(horizontal = 12.dp).height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = colors.content, maxLines = 1, softWrap = false)
        }
    }
}

/** Yakınlaştırma oranı (dokununca kağıt sığdırılır); tuvalin köşesinde durur. */
@Composable
fun ZoomChip(zoomPercent: Int, onFit: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    val zoomDescription = stringResource(R.string.zoom_fit_cd, zoomPercent)
    NeuSurface(
        shape = NeuPillShape,
        elevation = 4.dp,
        onClick = onFit,
        helpLabel = stringResource(R.string.zoom_fit),
        modifier = modifier.height(34.dp).semantics(mergeDescendants = true) { contentDescription = zoomDescription }.testTag("zoom_chip"),
    ) {
        Row(Modifier.padding(horizontal = 10.dp).height(34.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ZoomIn, null, tint = colors.contentMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text("%$zoomPercent", style = MaterialTheme.typography.labelMedium, color = colors.content, maxLines = 1, softWrap = false)
        }
    }
}

/** Ekle modunda (üst menü ve "buraya ekle" menüsü) bir öğe; [id] son seçileni hatırlamak için. */
data class AddMenuItem(val id: String, val label: String, val icon: ImageVector, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * Çizim alanı modları (v17: Çiz, Seç, Ekle, Düzenle, Taşı, Genişlet): seçili modun adı simgesinin yanında (dikey rayda
 * altında) yazar; alt modlarda (ör. şekil ekleme, şekil düzenleme) ailesinin düğmesi seçili görünür. Dikey rayda
 * (yatay ekran/tablet) sütun, telefonda satır olarak dizilir.
 */
@Composable
fun ModeSelector(
    mode: EditMode, onModeChange: (EditMode) -> Unit, modifier: Modifier = Modifier, vertical: Boolean = false,
    /** v45 modların sırası (bkz. [modeToolbar]). */
    modes: List<EditMode> = EditMode.Toolbar,
    /** v47 modların ardında ayrı grup: Elemanlar, Katmanlar, Özellikler (dikey ekranda; yatayda sağdaki rayda). */
    extra: List<ToolSlot> = emptyList(),
) {
    // v45 gelişmiş modların dikey ekranında her modun adı düğmenin içinde yazar
    val inline = com.karapp.drawguidance.ui.components.LocalInlineLabels.current
    NeuSurface(shape = NeuPillShape, elevation = 4.dp, inset = true, modifier = modifier.testTag("mode_selector")) {
        if (vertical) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GroupedToolSlots(
                    modes.map { item ->
                        val on = item == mode.menuFamily
                        ToolSlot(item.name, on) {
                            ModeButton(item, on, { onModeChange(item) }, Modifier.width(52.dp), height = if (on || inline) 52.dp else 42.dp, vertical = true, allLabels = inline)
                        }
                    },
                    aligner = null, vertical = true,
                )
                // v48 açılır alanın düğmeleri modların ardında (ayraç yok)
                if (extra.isNotEmpty()) GroupedToolSlots(extra, aligner = null, vertical = true)
            }
        } else if (inline) {
            // v45 adlar sığmazsa satır kayar; seçili moda hizalanır, dörderli gruplar
            val scroll = rememberScrollState()
            val aligner = rememberToolRowAligner(scroll, mode.menuFamily.name)
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .onGloballyPositioned { aligner.viewport = it; aligner.requestAlign() }
                        .horizontalScroll(scroll)
                        .widthIn(min = maxWidth)
                        .padding(5.dp),
                    horizontalArrangement = SpreadOrScroll,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GroupedToolSlots(
                        modes.map { item ->
                            val on = item == mode.menuFamily
                            ToolSlot(item.name, on) { ModeButton(item, on, { onModeChange(item) }, Modifier, allLabels = true) }
                        },
                        aligner,
                    )
                    if (extra.isNotEmpty()) GroupedToolSlots(extra, aligner)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                val dividers = ToolGroups.dividerBefore(modes.size).toSet()
                modes.forEachIndexed { i, item ->
                    if (i in dividers) ToolGroupDivider()
                    val on = item == mode.menuFamily
                    ModeButton(item, on, { onModeChange(item) }, Modifier.weight(if (on) 2.2f else 1f))
                }
                if (extra.isNotEmpty()) extra.forEach { slot -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { slot.content() } }
            }
        }
    }
}

/** v47 bütün gelişmiş türlerde dört mod: Seç, Çiz, Ekle, Düzenle (Taşı ve Genişlet Düzenle satırında tek düğme). */
@Suppress("UNUSED_PARAMETER")
fun modeToolbar(kind: com.karapp.drawguidance.data.model.CanvasKind): List<EditMode> = EditMode.Toolbar

@Composable
private fun ModeButton(
    mode: EditMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 40.dp, vertical: Boolean = false,
    /** v45 seçili olmayan modların da adı yazar. */
    allLabels: Boolean = false,
) {
    val colors = NeuTheme.colors
    val label = modeLabel(mode)
    // v33: seçili modun adı düğmede yazar; 2 sn basılı tutunca modun açıklaması köşedeki kutuda
    val help = com.karapp.drawguidance.ui.components.LocalToolHelp.current
    val click = if (help != null) {
        Modifier
            .toolHelpClickable(help, label, true, Role.Tab, null, showName = false, onClick = onClick)
            .semantics { this.selected = selected }
    } else Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick)
    com.karapp.drawguidance.ui.components.ToolHold {
    Box(
        modifier = modifier
            .height(height)
            .then(if (selected) Modifier.neumorphic(colors, NeuPillShape, elevation = 4.dp) else Modifier)
            .clip(NeuPillShape)
            .then(click)
            .semantics { contentDescription = label }
            .tutorialAnchor(TutorialAnchor.mode(mode))
            .then(if (allLabels && !vertical) Modifier.padding(horizontal = 10.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val icon: @Composable () -> Unit = {
            Icon(
                modeIcon(mode),
                contentDescription = null,
                tint = if (selected) colors.accent else colors.contentMuted,
                modifier = Modifier.size(if (allLabels && !selected) 18.dp else 20.dp),
            )
        }
        val text: @Composable () -> Unit = {
            Text(
                label,
                style = if (vertical) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = if (selected) colors.accent else colors.contentMuted,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                // Ad ekranda yazar; erişilebilirlikte düğmenin açıklaması zaten aynı ad (v45 testlerde etiketle bulunur)
                modifier = Modifier.clearAndSetSemantics { testTag = com.karapp.drawguidance.ui.components.INLINE_LABEL_PREFIX + label },
            )
        }
        when {
            !selected && !allLabels -> icon()
            vertical -> Column(horizontalAlignment = Alignment.CenterHorizontally) { icon(); text() }
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { icon(); text() }
        }
    }
    }
}

/**
 * Çizim alanının altında seçili moda ait kısa açıklama ve hızlı işlemler.
 * Çiz modunda gösterilmez.
 */
@Composable
fun ModeBar(
    mode: EditMode,
    selectedCount: Int,
    canvasWidth: Int,
    canvasHeight: Int,
    zoomPercent: Int,
    onModeChange: (EditMode) -> Unit,
    onClearSelection: () -> Unit,
    onRotateBy: (Float) -> Unit,
    onScaleBy: (Float) -> Unit,
    onZoomStep: (Float) -> Unit,
    onFit: () -> Unit,
    modifier: Modifier = Modifier,
    cropWidth: Int = canvasWidth,
    cropHeight: Int = canvasHeight,
    shapeName: String = "",
    selectedTextCount: Int = 0,
    onApplyCrop: () -> Unit = {},
    onResetCrop: () -> Unit = {},
    onChangeShape: () -> Unit = {},
    onTextSettings: () -> Unit = {},
    onEditText: () -> Unit = {},
    onBackground: () -> Unit = {},
    shapeTool: com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool = com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.NODES,
    onShapeTool: (com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool) -> Unit = {},
    onSubdivide: () -> Unit = {},
    diagramName: String = "",
    onChangeDiagram: () -> Unit = {},
    canEditShape: Boolean = false,
    onEditShape: () -> Unit = {},
    canEditDiagram: Boolean = false,
    onEditDiagram: () -> Unit = {},
    cropElement: Boolean = false,
    diagramLinking: Boolean = false,
    onDiagramAddStep: () -> Unit = {},
    onDiagramLink: () -> Unit = {},
    /** Ok eklemede önce başlangıç adımı seçilecek. */
    diagramLinkNeedsSource: Boolean = false,
    /** Seçili elemanları sil / kopyala. */
    onDeleteSelection: () -> Unit = {},
    onDuplicateSelection: () -> Unit = {},
    /** Alçak ekran (yatay telefon): açıklama tek satır, araç seçimi düğmelerle aynı satırda. */
    compact: Boolean = false,
    /** "Bitti": modun politikasına göre biter (v16: iş bitince İncele). */
    onDone: () -> Unit = { onModeChange(EditMode.DRAW) },
    /** Şekil düzenlemede gösterilen araçlar (Grafik aracı yalnızca grafiklerde). */
    shapeTools: List<com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool> =
        com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.entries - com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.GRAPH - com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.PARAMS,
    /** Şekil düzenlemede Çizgi çiz aracında çizim sürüyor (bir noktadan devam ediliyor). */
    vectorLinking: Boolean = false,
    /** Şekil düzenlemede seçili parçalara (seçim yoksa tüm şekle) özellik verme. */
    onPartStyle: () -> Unit = {},
    /** Seç modunda seçili elemana özel ek düğmeler (resmi düzenle, formülü düzenle…). */
    selectionActions: List<ModeAction> = emptyList(),
    /** Başlığın ([title]) yerine geçen ad (ör. "Formül: İkinci derece denklem"). */
    title: String? = null,
    /** Mod çubuğunun üstünde moda özel ek ayarlar (grafik, desen boyu…). */
    extra: (@Composable () -> Unit)? = null,
    /** Varsayılan düğme satırının yerine moda özel düğmeler (yeni modlar kendi düğmelerini verir). */
    actions: List<ModeAction>? = null,
    /** Diyagram düzenlemede renk teması seçimi (null: gösterilmez). */
    onDiagramTheme: (() -> Unit)? = null,
) {
    val colors = NeuTheme.colors
    NeuSurface(modifier = modifier.fillMaxWidth().testTag("mode_bar"), elevation = 10.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val needsSelection = mode.transformsSelection && selectedCount == 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            // v23 özel başlık (ör. "Ok çıkar" yönergesi) önce
                            title != null -> title
                            mode == EditMode.SHAPE && shapeName.isNotEmpty() -> "${modeLabel(mode)}: $shapeName"
                            mode == EditMode.DIAGRAM && diagramName.isNotEmpty() -> "${modeLabel(mode)}: $diagramName"
                            mode == EditMode.SHAPE_EDIT -> "${modeLabel(mode)}: ${shapeToolLabel(shapeTool)}"
                            mode == EditMode.CROP && cropElement -> stringResource(R.string.crop_element_title)
                            else -> modeLabel(mode)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.accent,
                    )
                    Text(
                        stringResource(
                            when {
                                needsSelection -> R.string.mode_hint_need_selection
                                mode == EditMode.SELECT -> R.string.mode_hint_select
                                mode == EditMode.CROP && cropElement -> R.string.mode_hint_crop_element
                                mode == EditMode.CROP -> R.string.mode_hint_crop
                                mode == EditMode.SHAPE -> R.string.mode_hint_shape
                                mode == EditMode.TEXT -> R.string.mode_hint_text
                                mode == EditMode.DIAGRAM -> R.string.mode_hint_diagram
                                mode == EditMode.SHAPE_EDIT -> when (shapeTool) {
                                    com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.NODES -> R.string.mode_hint_vector_nodes
                                    com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.ADD_NODE -> R.string.mode_hint_vector_add
                                    com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.CONNECT -> if (vectorLinking) R.string.mode_hint_vector_connect_next else R.string.mode_hint_vector_connect
                                    com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.LINES -> R.string.mode_hint_vector_lines
                                    com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.FILL -> R.string.mode_hint_region_fill
                                    com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.GRAPH -> R.string.mode_hint_shape_graph
                                    com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.PARAMS -> R.string.v45_mode_hint_params
                                }
                                mode == EditMode.FORMULA -> R.string.mode_hint_formula
                                mode == EditMode.IMAGE_EDIT -> R.string.mode_hint_image_edit
                                mode == EditMode.PEN -> R.string.mode_hint_pen
                                mode == EditMode.PAINT -> R.string.mode_hint_paint
                                mode == EditMode.DIAGRAM_EDIT && diagramLinking && diagramLinkNeedsSource -> R.string.mode_hint_diagram_link_source
                                mode == EditMode.DIAGRAM_EDIT && diagramLinking -> R.string.mode_hint_diagram_link
                                mode == EditMode.DIAGRAM_EDIT -> R.string.mode_hint_diagram_edit
                                mode == EditMode.MOVE -> R.string.mode_hint_move
                                mode == EditMode.ROTATE -> R.string.mode_hint_rotate
                                mode == EditMode.SCALE -> R.string.mode_hint_scale
                                else -> R.string.mode_hint_expand
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.contentMuted,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    mode == EditMode.EXPAND -> Text("$canvasWidth × $canvasHeight", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelLarge)
                    mode == EditMode.CROP -> Text("$cropWidth × $cropHeight", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelLarge)
                    // Şekil düzenlerken seçili eleman hep düzenlenen şekildir; sayı yerine yönerge tam genişlikte görünür
                    mode == EditMode.SHAPE_EDIT -> Unit
                    selectedCount > 0 -> Text(stringResource(R.string.selection_count, selectedCount), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
                }
            }

            // Seçili elemana özel düzenleme düğmeleri kendi satırında durur: etiketler kırpılmaz
            // v18: yalnız Seç'te (Taşı modunda yalnızca taşımayla ilgili seçenekler bulunur)
            if (mode == EditMode.SELECT && selectedCount > 0 &&
                (selectedTextCount == 1 || canEditDiagram || canEditShape || selectionActions.isNotEmpty())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    selectionActions.forEach { a ->
                        NeuButton(a.label, a.onClick, Modifier.weight(1f), primary = a.primary, enabled = a.enabled, icon = a.icon)
                    }
                    if (selectedTextCount == 1) {
                        NeuButton(stringResource(R.string.edit_text), onEditText, Modifier.weight(1f), icon = Icons.Filled.TextFields)
                    }
                    if (canEditDiagram) {
                        NeuButton(stringResource(R.string.diagram_edit), onEditDiagram, Modifier.weight(1f), icon = Icons.Filled.AccountTree)
                    }
                    if (canEditShape) {
                        NeuButton(stringResource(R.string.shape_edit_open), onEditShape, Modifier.weight(1f), icon = Icons.Filled.ContentCut)
                    }
                }
            }

            // v17: şekil düzenleme araçları (Noktalar, Nokta ekle, Çizgi çiz, Çizgiler, Alan boya) üst menüde

            extra?.invoke()

            // Çok düğmeli satırlarda (şekil düzenleme, moda özel) aralık daralır: dar ekranda etiketler sığar
            val tight = mode == EditMode.SHAPE_EDIT || (actions?.size ?: 0) >= 4
            Row(horizontalArrangement = Arrangement.spacedBy(if (tight) 4.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    actions != null -> {
                        // Moda özel düğmeler: son düğme (Bitti) vurgulu
                        actions.forEach { a ->
                            if (a.icon != null && a.label.isEmpty()) NeuIconButton(a.icon, a.label, a.onClick, size = 48.dp, enabled = a.enabled, tint = colors.accent)
                            else ActionItem(a.icon ?: Icons.Filled.Check, a.label, a.onClick, Modifier.weight(1f), enabled = a.enabled, tint = if (a.primary) colors.accent else if (a.danger) colors.danger else null, description = a.description ?: a.label)
                        }
                    }
                    needsSelection -> {
                        NeuButton(stringResource(R.string.mode_select), { onModeChange(EditMode.SELECT) }, Modifier.weight(1f), primary = true, icon = Icons.Filled.HighlightAlt)
                        // Seçim yokken de eylem bitirilebilir (ör. eklenen eleman geri alındıysa)
                        NeuButton(stringResource(R.string.move_done), onDone, Modifier.weight(1f), icon = Icons.Filled.Check)
                    }
                    mode == EditMode.SELECT -> {
                        // v17: sil/kopyala/döndür/ölçekle üst menüde; burada seçimi kaldır ve bitti
                        NeuButton(stringResource(R.string.clear_selection), onClearSelection, Modifier.weight(1f), enabled = selectedCount > 0, icon = Icons.Filled.Close)
                        NeuButton(stringResource(R.string.move_done), onDone, Modifier.weight(1f), primary = true, icon = Icons.Filled.Check)
                    }
                    mode == EditMode.CROP -> {
                        NeuButton(stringResource(R.string.crop_reset), onResetCrop, Modifier.weight(1f))
                        NeuButton(stringResource(R.string.crop_apply), onApplyCrop, Modifier.weight(1f), primary = true, icon = Icons.Filled.Crop)
                    }
                    mode == EditMode.SHAPE -> {
                        NeuButton(stringResource(R.string.change_shape), onChangeShape, Modifier.weight(1f), icon = Icons.Filled.Category)
                        NeuButton(stringResource(R.string.move_done), onDone, Modifier.weight(1f), primary = true, icon = Icons.Filled.Check)
                    }
                    mode == EditMode.DIAGRAM -> {
                        NeuButton(stringResource(R.string.add_diagram), onChangeDiagram, Modifier.weight(1f), icon = Icons.Filled.AccountTree)
                        NeuButton(stringResource(R.string.move_done), onDone, Modifier.weight(1f), primary = true, icon = Icons.Filled.Check)
                    }
                    mode == EditMode.DIAGRAM_EDIT -> {
                        // Adım ve ok eklemek aynı satırda; "Bitti" ile düzenleme biter
                        NeuButton(stringResource(R.string.diagram_add_step), onDiagramAddStep, Modifier.weight(1f))
                        NeuButton(
                            stringResource(if (diagramLinking) R.string.diagram_link_cancel else R.string.diagram_add_arrow),
                            onDiagramLink,
                            Modifier.weight(1f),
                            primary = diagramLinking,
                        )
                        if (onDiagramTheme != null) {
                            NeuIconButton(Icons.Filled.Palette, stringResource(R.string.diagram_theme), onDiagramTheme, size = 48.dp, tint = colors.accent)
                        }
                        NeuIconButton(Icons.Filled.Check, stringResource(R.string.move_done), onDone, size = 48.dp, tint = colors.accent)
                    }
                    mode == EditMode.SHAPE_EDIT -> {
                        ActionItem(Icons.Filled.Palette, stringResource(R.string.part_style), onPartStyle, Modifier.weight(1f), description = stringResource(R.string.part_style_title))
                        ActionItem(Icons.Filled.Check, stringResource(R.string.move_done), onDone, Modifier.weight(1f), tint = colors.accent)
                    }
                    mode == EditMode.TEXT -> {
                        NeuButton(stringResource(R.string.text_settings), onTextSettings, Modifier.weight(1f), icon = Icons.Filled.Tune)
                        NeuButton(stringResource(R.string.move_done), onDone, Modifier.weight(1f), primary = true, icon = Icons.Filled.Check)
                    }
                    mode == EditMode.ROTATE -> {
                        NeuButton("−15°", { onRotateBy(-15f) }, Modifier.weight(1f))
                        NeuButton("+15°", { onRotateBy(15f) }, Modifier.weight(1f))
                        NeuButton("+90°", { onRotateBy(90f) }, Modifier.weight(1f))
                        NeuIconButton(Icons.Filled.Check, stringResource(R.string.move_done), onDone, size = 48.dp, tint = colors.accent)
                    }
                    mode == EditMode.SCALE -> {
                        NeuButton("−10%", { onScaleBy(0.9f) }, Modifier.weight(1f))
                        NeuButton("+10%", { onScaleBy(1.1f) }, Modifier.weight(1f))
                        NeuIconButton(Icons.Filled.Check, stringResource(R.string.move_done), onDone, size = 48.dp, tint = colors.accent)
                    }
                    mode == EditMode.MOVE -> {
                        // v17: sil / kopyala üst menüde
                        NeuButton(stringResource(R.string.move_done), onDone, Modifier.weight(1f), primary = true, icon = Icons.Filled.Check)
                    }
                    else -> {
                        NeuButton(stringResource(R.string.zoom_fit), onFit, Modifier.weight(1f))
                        NeuButton(stringResource(R.string.move_done), onDone, Modifier.weight(1f), primary = true, icon = Icons.Filled.Check)
                    }
                }
            }
        }
    }
}

/** Şekil düzenleme aracının adı. */
@Composable
fun shapeToolLabel(tool: com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool): String = stringResource(
    when (tool) {
        com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.NODES -> R.string.tool_nodes
        com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.ADD_NODE -> R.string.tool_add_node
        com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.CONNECT -> R.string.tool_connect
        com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.LINES -> R.string.tool_lines
        com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.FILL -> R.string.tool_region_fill
        com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.GRAPH -> R.string.tool_graph
        com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.PARAMS -> R.string.v45_tool_params
    },
)

/** Diyagram türünün adı. */
@Composable
fun diagramLabel(kind: com.karapp.drawguidance.data.model.DiagramKind): String = stringResource(diagramLabelRes(kind))

/** v43 diyagram türünün adının kaynağı. */
fun diagramLabelRes(kind: com.karapp.drawguidance.data.model.DiagramKind): Int = (
    when (kind) {
        com.karapp.drawguidance.data.model.DiagramKind.FLOW -> R.string.diagram_flow
        com.karapp.drawguidance.data.model.DiagramKind.PROCESS -> R.string.diagram_process
        com.karapp.drawguidance.data.model.DiagramKind.ARCHITECTURE -> R.string.diagram_architecture
        com.karapp.drawguidance.data.model.DiagramKind.ORG -> R.string.diagram_org
        com.karapp.drawguidance.data.model.DiagramKind.TIMELINE -> R.string.diagram_timeline
        com.karapp.drawguidance.data.model.DiagramKind.STATE -> R.string.diagram_state
        com.karapp.drawguidance.data.model.DiagramKind.RELATION -> R.string.diagram_relation
        com.karapp.drawguidance.data.model.DiagramKind.COMPARISON -> R.string.diagram_comparison
        com.karapp.drawguidance.data.model.DiagramKind.CYCLE -> R.string.diagram_cycle
        com.karapp.drawguidance.data.model.DiagramKind.MIND_MAP -> R.string.diagram_mind_map
        com.karapp.drawguidance.data.model.DiagramKind.PYRAMID -> R.string.diagram_pyramid
        com.karapp.drawguidance.data.model.DiagramKind.VENN -> R.string.diagram_venn
        com.karapp.drawguidance.data.model.DiagramKind.MATRIX -> R.string.diagram_matrix
        com.karapp.drawguidance.data.model.DiagramKind.FUNNEL -> R.string.diagram_funnel
        com.karapp.drawguidance.data.model.DiagramKind.FISHBONE -> R.string.diagram_fishbone
        com.karapp.drawguidance.data.model.DiagramKind.CONCEPT_MAP -> R.string.diagram_concept_map
        com.karapp.drawguidance.data.model.DiagramKind.TREE -> R.string.diagram_tree
        com.karapp.drawguidance.data.model.DiagramKind.KWL -> R.string.diagram_kwl
        com.karapp.drawguidance.data.model.DiagramKind.STEPS -> R.string.diagram_steps
        com.karapp.drawguidance.data.model.DiagramKind.HUB -> R.string.diagram_hub
        com.karapp.drawguidance.data.model.DiagramKind.SEQUENCE -> R.string.diagram_sequence
        com.karapp.drawguidance.data.model.DiagramKind.ER -> R.string.diagram_er
        com.karapp.drawguidance.data.model.DiagramKind.UML_CLASS -> R.string.diagram_uml_class
        com.karapp.drawguidance.data.model.DiagramKind.DB_SCHEMA -> R.string.diagram_db_schema
        com.karapp.drawguidance.data.model.DiagramKind.SWIMLANE -> R.string.diagram_swimlane
        com.karapp.drawguidance.data.model.DiagramKind.KANBAN -> R.string.diagram_kanban
        com.karapp.drawguidance.data.model.DiagramKind.STORY_MAP -> R.string.diagram_story_map
        com.karapp.drawguidance.data.model.DiagramKind.DEPLOYMENT -> R.string.diagram_deployment
        com.karapp.drawguidance.data.model.DiagramKind.NESTED -> R.string.diagram_nested
        com.karapp.drawguidance.data.model.DiagramKind.LAYERS -> R.string.diagram_layers
        com.karapp.drawguidance.data.model.DiagramKind.LOOP -> R.string.diagram_loop
        com.karapp.drawguidance.data.model.DiagramKind.QUADRANT -> R.string.diagram_quadrant
        com.karapp.drawguidance.data.model.DiagramKind.WARDLEY -> R.string.diagram_wardley
        com.karapp.drawguidance.data.model.DiagramKind.JOURNEY -> R.string.diagram_journey
        com.karapp.drawguidance.data.model.DiagramKind.DEPENDENCY -> R.string.diagram_dependency
        com.karapp.drawguidance.data.model.DiagramKind.IT_STATE -> R.string.diagram_it_state
        com.karapp.drawguidance.data.model.DiagramKind.HIGH_LEVEL -> R.string.diagram_high_level
        com.karapp.drawguidance.data.model.DiagramKind.MEDALLION -> R.string.diagram_medallion
        com.karapp.drawguidance.data.model.DiagramKind.DATA_FLOW -> R.string.diagram_data_flow
        com.karapp.drawguidance.data.model.DiagramKind.DP_INTEGRATION -> R.string.diagram_dp_integration
        com.karapp.drawguidance.data.model.DiagramKind.ARCH_DELTA -> R.string.diagram_arch_delta
        com.karapp.drawguidance.data.model.DiagramKind.BAR -> R.string.diagram_bar
        com.karapp.drawguidance.data.model.DiagramKind.WATERFALL -> R.string.diagram_waterfall
        com.karapp.drawguidance.data.model.DiagramKind.LINE -> R.string.diagram_line
        com.karapp.drawguidance.data.model.DiagramKind.SCATTER -> R.string.diagram_scatter
        com.karapp.drawguidance.data.model.DiagramKind.RADAR -> R.string.diagram_radar
        com.karapp.drawguidance.data.model.DiagramKind.POLAR -> R.string.diagram_polar
        com.karapp.drawguidance.data.model.DiagramKind.TREEMAP -> R.string.diagram_treemap
        com.karapp.drawguidance.data.model.DiagramKind.HEATMAP -> R.string.diagram_heatmap
        com.karapp.drawguidance.data.model.DiagramKind.DP_SECURITY -> R.string.diagram_dp_security
        com.karapp.drawguidance.data.model.DiagramKind.SANKEY -> R.string.diagram_sankey
        com.karapp.drawguidance.data.model.DiagramKind.GANTT -> R.string.diagram_gantt
        com.karapp.drawguidance.data.model.DiagramKind.EXPLODED -> R.string.diagram_exploded
        com.karapp.drawguidance.data.model.DiagramKind.AXONOMETRIC -> R.string.diagram_axonometric
    }
)

/** Alt çubukta simgenin altında kısa adı olan düğme (dar ekranda çok işlem sığar). */
@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color? = null,
    description: String = label,
) {
    val colors = NeuTheme.colors
    val color = (tint ?: colors.content).copy(alpha = if (enabled) 1f else 0.35f)
    // v33: 2 sn basılı tutunca açıklama (ad simgenin altında yazılı)
    val help = com.karapp.drawguidance.ui.components.LocalToolHelp.current
    val click = if (help != null) {
        Modifier.toolHelpClickable(help, label, enabled, Role.Button, null, showName = false, description = { help.describe(description, label) }, onClick = onClick)
    } else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    com.karapp.drawguidance.ui.components.ToolHold {
    Column(
        modifier = modifier
            .height(56.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.neumorphic(colors, androidx.compose.foundation.shape.RoundedCornerShape(14.dp), elevation = 3.dp) else Modifier)
            .then(click)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, softWrap = false,
            overflow = TextOverflow.Clip, modifier = Modifier.clearAndSetSemantics { },
        )
    }
    }
}

/** v33 basit çizimin tek üst menüsünde bir araç; [selected] iken adı simgesinin yanında yazar. */
data class SimpleTool(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/** v37 not tuvallerinde menünün başındakiler (bu sırayla). */
val NOTES_FIRST = listOf("text", "diagram", "formula")

/** v37 not tuvallerinde yazı, diyagram ve formül menünün başına alınır; ötekilerin sırası değişmez. */
fun <T> notesFirst(items: List<T>, id: (T) -> String): List<T> =
    items.sortedBy { NOTES_FIRST.indexOf(id(it)).let { i -> if (i < 0) NOTES_FIRST.size else i } }

/**
 * v35 basit menüde aynı anda tek araç seçili: en son dokunulan araç (hâlâ etkinse) seçili sayılır; değilse çizim
 * modunun aracı (fırça, silgi, seç, boya…), o da yoksa açık pencerenin aracı. Bir araca dokununca [onPick] öteki
 * açık pencereleri kapatır.
 */
fun exclusiveSimpleTools(
    raw: List<SimpleTool>, last: String?, overlays: Set<String>,
    /** v47 bağımsız düğmeler (Elemanlar, Katmanlar, Özellikler: açılır alanın sekmeleri) kendi seçimini korur. */
    independent: Set<String> = emptySet(),
    onPick: (String) -> Unit,
): List<SimpleTool> {
    val active = raw.filter { it.selected && it.id !in independent }.map { it.id }
    val chosen = last?.takeIf { it in active } ?: active.firstOrNull { it !in overlays } ?: active.firstOrNull()
    return raw.map { t ->
        t.copy(selected = if (t.id in independent) t.selected else t.id == chosen, onClick = { if (t.id !in independent) onPick(t.id); t.onClick() })
    }
}

/**
 * v33 basit çizimin tek üst menüsü: geri al / yinele ve modun bütün araçları tek satırda. Seçili aracın adı yazar,
 * ötekilerin yalnız simgesi görünür; v37 dokunulan her düğmenin (geri al / yinele dahil) adı simgesinin yanında
 * açılır ([SIMPLE_NAME_MS] sonra ya da başka düğmeye dokununca kapanır), 2 sn basılı tutunca açıklaması köşedeki
 * kutuda. Sığmazsa satır yana kaydırılır (kenarda solma görünür). Araçların alt pencereleri korunur. v38 yatay
 * ekranda bütün düğmelerin adı simgelerinin altında sürekli yazar (seçili araç çökük ve vurgu renginde).
 */
@Composable
fun SimpleToolRow(
    items: List<SimpleTool>,
    canUndo: Boolean,
    onUndo: () -> Unit,
    canRedo: Boolean,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    // v37 dokunulan düğmenin adı yanında açılır, kısa süre sonra kapanır
    var named by remember { mutableStateOf<String?>(null) }
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(taps) {
        if (named != null) {
            kotlinx.coroutines.delay(SIMPLE_NAME_MS)
            named = null
        }
    }
    val history = listOf(
        SimpleTool("undo", Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo), false, onUndo, canUndo),
        SimpleTool("redo", Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo), false, onRedo, canRedo),
    )
    val labels = com.karapp.drawguidance.ui.components.LocalButtonLabels.current
    // v45 dörderli gruplar (çocuk modunun menüsü de bu bileşeni kullanır; API aynı) ve seçili araca hizalama
    val slots = (history + items).map { item ->
        ToolSlot(item.id, item.selected) {
            val tool = item.copy(onClick = { named = item.id; taps++; item.onClick() })
            // v37 çocuk eğitimi araçları bu menüde gösterir (geri al ve yinele eğitimdeki ortak adlarıyla)
            val anchor = when (item.id) { "undo" -> TutorialAnchor.UNDO; "redo" -> TutorialAnchor.REDO; else -> TutorialAnchor.simple(item.id) }
            val tag = Modifier.testTag("simple_tool_${item.id}").tutorialAnchor(anchor)
            if (!labels && (item.selected || named == item.id)) SimpleToolPill(tool, tag) else SimpleToolIcon(tool, tag, labels)
        }
    } + listOfNotNull(trailing?.let { t -> ToolSlot("trailing") { t() } })
    ToolRowFrame(modifier, "simple_tool_row", fillWidth, slots)
}

/** v37 basit menüde dokunulan düğmenin adı bu kadar süre (ms) yanında görünür. */
const val SIMPLE_NAME_MS = 2500L

/**
 * Seçili olmayan araç: yalnız simge (dokununca adı yanında açılır; köşedeki kutuda ad tekrarlanmaz). v38 [label]
 * iken (yatay ekran) adı simgenin altında yazar; seçili araç da böyle gösterilir.
 */
@Composable
private fun SimpleToolIcon(item: SimpleTool, modifier: Modifier, label: Boolean = false) {
    val colors = NeuTheme.colors
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val help = com.karapp.drawguidance.ui.components.LocalToolHelp.current
    val click = if (help != null) {
        Modifier.toolHelpClickable(help, item.label, item.enabled, Role.Button, interaction, showName = false, onClick = item.onClick)
    } else Modifier.clickable(interactionSource = interaction, indication = null, enabled = item.enabled, role = Role.Button, onClick = item.onClick)
    val tint = when {
        !item.enabled -> colors.contentMuted.copy(alpha = 0.45f)
        item.selected -> colors.accent
        else -> colors.content
    }
    val icon: @Composable () -> Unit = {
        com.karapp.drawguidance.ui.components.ToolHold {
            Box(
                modifier
                    .size(36.dp)
                    .neumorphic(colors, CircleShape, elevation = 5.dp, inset = pressed, selected = item.selected)
                    .clip(CircleShape)
                    .then(click)
                    .semantics { contentDescription = item.label; if (item.selected) selected = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (label) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            com.karapp.drawguidance.ui.components.ButtonLabel(com.karapp.drawguidance.ui.components.shortLabel(item.label), item.selected, item.enabled, tag = item.label)
        }
    } else icon()
}

/** Seçili (ya da v37 az önce dokunulan) araç: simge ve adı yan yana, hap biçiminde (seçiliyse çökük, vurgu renginde). */
@Composable
internal fun SimpleToolPill(item: SimpleTool, modifier: Modifier) {
    val colors = NeuTheme.colors
    val help = com.karapp.drawguidance.ui.components.LocalToolHelp.current
    val click = if (help != null) {
        Modifier.toolHelpClickable(help, item.label, item.enabled, Role.Button, null, showName = false, onClick = item.onClick)
    } else Modifier.clickable(enabled = item.enabled, role = Role.Button, onClick = item.onClick)
    com.karapp.drawguidance.ui.components.ToolHold {
        Row(
            modifier
                .height(36.dp)
                .neumorphic(colors, NeuPillShape, elevation = if (item.selected) 3.dp else 5.dp, selected = item.selected)
                .clip(NeuPillShape)
                .then(click)
                .semantics { contentDescription = item.label; if (item.selected) selected = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val tint = when {
                item.selected -> colors.accent
                item.enabled -> colors.content
                else -> colors.contentMuted
            }
            Icon(item.icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(
                item.label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1, softWrap = false,
                modifier = Modifier.clearAndSetSemantics { testTag = com.karapp.drawguidance.ui.components.INLINE_LABEL_PREFIX + item.label },
            )
        }
    }
}
