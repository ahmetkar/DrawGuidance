package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.QuickBrushLayout
import com.karapp.drawguidance.data.model.QuickBrushLists
import com.karapp.drawguidance.data.model.QuickCorner
import com.karapp.drawguidance.data.settings.BrushTuning
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.BrushIcons
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.brushLabel
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

/**
 * v56 hızlı fırça seçici (planın 2. bölümü ve 2D-5): görünür tuval alanının köşesinde 44 dp'lik yuvarlak düğme (etkin fırça
 * simgesi, kenarında seçili rengin halkası, altında boyut noktası). Dokununca düğmenin yanından yatay şerit açılır:
 * sabitlenmiş favoriler ve son kullanılan fırçalar (uzun basınca sabitle / kaldır), renk düğmesi, boyut ve opaklık
 * kaydırıcıları, silgi kısayolu, "Tüm fırçalar". Çizerken şerit kapanır ve düğme %40 saydamlaşır. Tablette düğme
 * sürüklenip dört köşeden birine bırakılır.
 */
@Composable
fun QuickBrushOverlay(
    brushType: BrushType,
    colorHex: String,
    sizePx: Float,
    opacity: Float,
    lists: QuickBrushLists,
    open: Boolean,
    corner: QuickCorner,
    drawing: Boolean,
    /** Çizilmekte olan çizginin son noktası (alan koordinatı, px); düğmeye yaklaşınca düğme kenar boyunca kayar. */
    pen: Offset?,
    /** Seçimin içine boyama etkin (şeritte "Seçim içinde" rozeti). */
    insideSelection: Boolean,
    /** Renk düğmesi (2D-5) gösterilsin mi (Resim ve Basit resim). */
    showColor: Boolean,
    /** Tablette sürüklenip köşe değiştirilebilir. */
    movable: Boolean,
    onToggle: () -> Unit,
    onSelect: (BrushType) -> Unit,
    onToggleFavorite: (BrushType) -> Unit,
    onColor: () -> Unit,
    onSize: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onAllBrushes: () -> Unit,
    onEraser: () -> Unit,
    onCorner: (QuickCorner) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier.testTag("quick_brush_area")) {
        val (right, bottom) = QuickBrushLayout.physical(corner, rtl)
        val align = when {
            !right && !bottom -> Alignment.TopStart
            right && !bottom -> Alignment.TopEnd
            !right -> Alignment.BottomStart
            else -> Alignment.BottomEnd
        }.let { a ->
            // Hizalama mantıksal (Start/End RTL'de aynalanır); fiziksel köşeyi istediğimiz için RTL'de geri çevrilir
            if (!rtl) a else when (a) {
                Alignment.TopStart -> Alignment.TopEnd
                Alignment.TopEnd -> Alignment.TopStart
                Alignment.BottomStart -> Alignment.BottomEnd
                else -> Alignment.BottomStart
            }
        }
        val density = LocalDensity.current
        val areaW = with(density) { maxWidth.toPx() }
        val areaH = with(density) { maxHeight.toPx() }
        var drag by remember { mutableStateOf(Offset.Zero) }
        // Kalem düğmenin üstünden geçerse düğme kenar boyunca (köşeden uzağa) kayar; çizgiyi engellemez
        val pad = with(density) { 8.dp.toPx() }
        val half = with(density) { (QuickBrushLayout.BUTTON / 2f).dp.toPx() }
        val bx = if (right) areaW - pad - half else pad + half
        val by = if (bottom) areaH - pad - half else pad + half
        val dodge = pen?.let { QuickBrushLayout.dodge(it.x, it.y, bx, by, half, with(density) { 12.dp.toPx() }) } ?: 0f
        val dodgeY by androidx.compose.animation.core.animateFloatAsState(if (bottom) -dodge else dodge, label = "quick_dodge")
        Row(
            Modifier.align(align).padding(8.dp).offset { IntOffset(drag.x.roundToInt(), (drag.y + dodgeY).roundToInt()) },
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val button: @Composable () -> Unit = {
                QuickBrushButton(
                    brushType, colorHex, sizePx, drawing,
                    onToggle = onToggle, onLongColor = onColor.takeIf { showColor },
                    modifier = if (!movable) Modifier else Modifier.pointerInput(corner, rtl) {
                        var start = Offset.Zero
                        detectDragGestures(
                            onDragStart = { start = it; drag = Offset.Zero },
                            onDragEnd = {
                                // Bırakılan yer: düğmenin alan içindeki yaklaşık konumu
                                val baseX = if (right) areaW - size.width / 2f else size.width / 2f
                                val baseY = if (bottom) areaH - size.height / 2f else size.height / 2f
                                onCorner(QuickBrushLayout.nearest(baseX + drag.x, baseY + drag.y, areaW, areaH, rtl))
                                drag = Offset.Zero
                            },
                            onDragCancel = { drag = Offset.Zero },
                        ) { change, amount -> change.consume(); drag += amount }
                    },
                )
            }
            // Şerit düğmenin iç tarafına açılır (sol köşede sağına, sağ köşede soluna)
            val stripFirst = right != rtl
            if (!stripFirst) button()
            if (open && !drawing) QuickBrushStrip(
                brushType, colorHex, sizePx, opacity, lists, insideSelection, showColor,
                onSelect, onToggleFavorite, onColor, onSize, onOpacity, onAllBrushes, onEraser,
                modifier = Modifier.width(this@BoxWithConstraints.maxWidth - 72.dp),
            )
            if (stripFirst) button()
        }
    }
}

@Composable
private fun QuickBrushButton(
    brushType: BrushType,
    colorHex: String,
    sizePx: Float,
    drawing: Boolean,
    onToggle: () -> Unit,
    onLongColor: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    val label = brushLabel(brushType)
    val description = stringResource(R.string.v56_quick_brush_a11y, label)
    val ring = if (brushType == BrushType.ERASER) colors.contentMuted else hexToColor(colorHex)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.alpha(if (drawing) 0.4f else 1f)) {
        NeuSurface(
            modifier = Modifier.size(QuickBrushLayout.BUTTON.dp).testTag("quick_brush_button")
                .semantics(mergeDescendants = true) { contentDescription = description },
            shape = CircleShape,
            elevation = 5.dp,
            onClick = onToggle,
            onLongClick = onLongColor,
        ) {
            Box(Modifier.size(QuickBrushLayout.BUTTON.dp).border(3.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
                Icon(BrushIcons.of(brushType), null, tint = colors.accent, modifier = Modifier.size(22.dp))
            }
        }
        // Boyut noktası: fırça boyutuyla büyür (3–12 dp)
        val dot = (3f + (sizePx / BrushTuning.MAX_SIZE).coerceIn(0f, 1f) * 9f).dp
        Box(Modifier.padding(top = 3.dp).size(dot).background(if (brushType == BrushType.ERASER) colors.contentMuted else ring, CircleShape))
    }
}

@Composable
private fun QuickBrushStrip(
    brushType: BrushType,
    colorHex: String,
    sizePx: Float,
    opacity: Float,
    lists: QuickBrushLists,
    insideSelection: Boolean,
    showColor: Boolean,
    onSelect: (BrushType) -> Unit,
    onToggleFavorite: (BrushType) -> Unit,
    onColor: () -> Unit,
    onSize: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onAllBrushes: () -> Unit,
    onEraser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    NeuSurface(modifier = modifier.testTag("quick_brush_strip"), shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), elevation = 6.dp) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (insideSelection) {
                Text(
                    stringResource(R.string.v56_quick_inside_selection), style = MaterialTheme.typography.labelSmall, color = colors.onAccent,
                    modifier = Modifier.background(colors.accent, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp).testTag("quick_brush_inside"),
                )
            }
            lists.strip().forEach { type ->
                val name = brushLabel(type)
                val fav = type in lists.favorites
                val tip = stringResource(if (fav) R.string.v56_quick_unpin else R.string.v56_quick_pin)
                Box {
                    NeuSurface(
                        modifier = Modifier.size(38.dp).testTag("quick_brush_item_${type.name}").semantics { contentDescription = name },
                        shape = CircleShape, elevation = 3.dp, selected = type == brushType,
                        onClick = { onSelect(type) }, onLongClick = { onToggleFavorite(type) }, helpLabel = tip,
                    ) {
                        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                            Icon(BrushIcons.of(type), null, tint = if (type == brushType) colors.accent else colors.content, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (fav) Icon(Icons.Filled.PushPin, null, tint = colors.accent, modifier = Modifier.size(12.dp).align(Alignment.TopEnd))
                }
            }
            // 2D-5 renk düğmesi ("Tüm fırçalar"dan önce): renk seçici penceresini açar
            if (showColor) {
                val colorName = stringResource(R.string.v56_quick_color)
                NeuSurface(
                    modifier = Modifier.size(38.dp).testTag("quick_brush_color").semantics { contentDescription = colorName },
                    shape = CircleShape, elevation = 3.dp, onClick = onColor, helpLabel = colorName,
                ) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(24.dp).background(hexToColor(colorHex), CircleShape).border(1.dp, Color.Black.copy(alpha = 0.25f), CircleShape))
                    }
                }
            }
            // Silgi kısayolu
            NeuSurface(
                modifier = Modifier.size(38.dp).testTag("quick_brush_eraser"), shape = CircleShape, elevation = 3.dp,
                selected = brushType == BrushType.ERASER, onClick = onEraser, helpLabel = brushLabel(BrushType.ERASER),
            ) { Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { Icon(BrushIcons.of(BrushType.ERASER), brushLabel(BrushType.ERASER), modifier = Modifier.size(20.dp)) } }
            // İki mini kaydırıcı: boyut ve opaklık
            MiniSlider(stringResource(R.string.v56_quick_size), sizePx, BrushTuning.MIN_SIZE..BrushTuning.MAX_SIZE, onSize, "quick_brush_size")
            MiniSlider(stringResource(R.string.v56_quick_opacity), opacity, 0.05f..1f, onOpacity, "quick_brush_opacity")
            val allName = stringResource(R.string.v56_quick_all_brushes)
            NeuSurface(
                modifier = Modifier.testTag("quick_brush_all"), shape = CircleShape, elevation = 3.dp, onClick = onAllBrushes, helpLabel = allName,
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Apps, null, modifier = Modifier.size(18.dp))
                    Text(allName, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun MiniSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, tag: String) {
    var local by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    val shown = if (dragging) local else value
    Column(Modifier.width(96.dp).testTag(tag).semantics { contentDescription = label }) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = NeuTheme.colors.contentMuted)
        NeuSlider(
            value = shown,
            onValueChange = { dragging = true; local = it; onChange(it) },
            valueRange = range,
            onValueChangeFinished = { dragging = false },
        )
    }
}
