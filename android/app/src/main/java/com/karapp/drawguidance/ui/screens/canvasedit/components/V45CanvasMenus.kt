package com.karapp.drawguidance.ui.screens.canvasedit.components

import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.tutorialAnchor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

/**
 * v45 tuval menüleri (eski bağlam menülerinin yerine):
 * - Seçili eleman (ya da v41 eleman gibi seçilen arka plan): köşesinde saydam, yatay menü; küçük saydam simgeler ve
 *   yanlarında küçük adlar: Taşı, Ölçekle, Kopyala, Sil, Düzenle. Düzenle seçime özel işlemlerin dikey listesini açar
 *   (yazıyı/şekli/paneli düzenle, renk, sıra, gizle…).
 * - (v47 boş yere dokunmanın dikey ekleme menüsü kaldırıldı.)
 * Menüler ekranın içinde kalır, olabildiğince seçili elemanın üstüne binmez, sağdan sola dillerde aynalanır.
 */
data class SelectionMenuAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val selected: Boolean = false,
)

/** v45 seçim menüsünün beş eyleminin kimlikleri (bu sırayla). */
object SelectionMenuIds {
    const val MOVE = "move"
    const val SCALE = "scale"
    const val COPY = "copy"
    const val DELETE = "delete"
    const val EDIT = "edit"
    val ORDER = listOf(MOVE, SCALE, COPY, DELETE, EDIT)
}

/** v45 menülerin yerleşimi (saf hesap; testlerde de kullanılır). */
object CanvasMenuPlacement {
    /**
     * Seçim menüsü: seçimin üst köşesinde (soldan sağa dillerde sağ üst, sağdan sola dillerde sol üst) seçimin
     * üstünde; üstte yer yoksa altında; ikisinde de yer yoksa ekranın içinde kalacak biçimde (kaçınılmazsa üstüne
     * biner). Yatayda hep alanın içinde.
     */
    fun aboveOrBelow(anchor: Rect, menu: IntSize, area: IntSize, gap: Float, margin: Float, rtl: Boolean): IntOffset {
        val w = menu.width.toFloat()
        val h = menu.height.toFloat()
        val x0 = if (rtl) anchor.left else anchor.right - w
        val x = clampSpan(x0, w, area.width.toFloat(), margin)
        val above = anchor.top - gap - h
        val below = anchor.bottom + gap
        val y = when {
            above >= margin -> above
            below + h <= area.height - margin -> below
            else -> clampSpan(above, h, area.height.toFloat(), margin)
        }
        return IntOffset(x.roundToInt(), y.roundToInt())
    }

    /**
     * Ekleme menüsü: dokunulan noktadan açılır (soldan sağa dillerde sağa ve aşağı); sığmazsa noktanın öbür yanına
     * döner; hep alanın içinde.
     */
    fun atPoint(point: Offset, menu: IntSize, area: IntSize, margin: Float, rtl: Boolean): IntOffset {
        val w = menu.width.toFloat()
        val h = menu.height.toFloat()
        val forward = if (rtl) point.x - w else point.x
        val back = if (rtl) point.x else point.x - w
        val x = if (forward >= margin && forward + w <= area.width - margin) forward else back
        val y = if (point.y + h <= area.height - margin) point.y else point.y - h
        return IntOffset(clampSpan(x, w, area.width.toFloat(), margin).roundToInt(), clampSpan(y, h, area.height.toFloat(), margin).roundToInt())
    }

    /** v45 çift dokunmanın iki dokunuşu arasındaki en uzun süre (tuvalin çift dokunmasıyla aynı) ve en uzak mesafe. */
    const val DOUBLE_TAP_MS = 320L
    val DOUBLE_TAP_SLOP = 24.dp

    /**
     * Menü [openedAt] anında (ilk dokunuşun hemen ardındaki kare) açıldıysa [downAt] anındaki ve [down] noktasındaki
     * dokunuş çift dokunmanın ikincisi mi: süre ve mesafe içinde.
     */
    fun isSecondTap(openedAt: Long, downAt: Long, at: Offset, down: Offset, slop: Float): Boolean =
        openedAt >= 0 && downAt - openedAt in 0..DOUBLE_TAP_MS && (down - at).getDistance() <= slop

    private fun clampSpan(start: Float, size: Float, total: Float, margin: Float): Float {
        val max = total - size - margin
        return if (max < margin) ((total - size) / 2f).coerceAtLeast(0f) else start.coerceIn(margin, max)
    }
}

/**
 * v45 seçili elemanın yatay menüsü. [anchor] seçimin ekrandaki kutusunu verir (yerleşim sırasında okunur: görünüm
 * kayınca ya da yakınlaşınca menü yeniden yerleşir). [bottomInset] alttaki mod çubuğunun yüksekliği (menü onun altına
 * girmez). [editItems] Düzenle'nin açtığı dikey liste.
 */
@Composable
fun SelectionMenu(
    anchor: () -> Rect?,
    actions: List<SelectionMenuAction>,
    editItems: List<ModeAction>,
    modifier: Modifier = Modifier,
    /** Bu değer değişince (yeni seçim) Düzenle listesi kapanır. */
    resetKey: Any? = null,
    bottomInset: () -> Int = { 0 },
    tag: String = "selection_menu",
    /** Düzenle listesinin altında ek içerik (ör. arka planın saydamlığı). */
    editExtra: (@Composable () -> Unit)? = null,
    /** v45 true iken Düzenle listesi açılır (iki parmakla basma: eski bağlam menüsünün işlemleri); açılınca [onEditOpened]. */
    openEdit: Boolean = false,
    onEditOpened: () -> Unit = {},
) {
    var editOpen by remember(resetKey) { mutableStateOf(false) }
    LaunchedEffect(openEdit, resetKey) {
        if (openEdit) { editOpen = true; onEditOpened() }
    }
    // Geri tuşu önce açık Düzenle listesini kapatır
    BackHandler(enabled = editOpen) { editOpen = false }
    // Düzenle: türüne özel işlemlerin listesi açılır/kapanır
    val shown = actions.map { a -> if (a.id == SelectionMenuIds.EDIT) a.copy(selected = editOpen, onClick = { editOpen = !editOpen; a.onClick() }) else a }
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val gap = with(density) { 8.dp.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            Column(horizontalAlignment = if (rtl) Alignment.Start else Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectionMenuRow(shown, tag)
                if (editOpen && (editItems.isNotEmpty() || editExtra != null)) {
                    CanvasVerticalMenu(editItems, "${tag}_edit_list", stringResource(R.string.v45_selection_edit_menu), onPicked = { editOpen = false }, extra = editExtra)
                }
            }
        },
    ) { measurables, constraints ->
        val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val a = anchor()
            if (a != null) {
                val area = IntSize(constraints.maxWidth, (constraints.maxHeight - bottomInset()).coerceAtLeast(p.height))
                p.place(CanvasMenuPlacement.aboveOrBelow(a, IntSize(p.width, p.height), area, gap, margin, rtl))
            }
        }
    }
}

/** v45 saydam yatay menü satırı: küçük saydam simgeler ve yanlarında küçük adlar. */
@Composable
private fun SelectionMenuRow(actions: List<SelectionMenuAction>, tag: String) {
    val colors = NeuTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val menuLabel = stringResource(R.string.v45_selection_menu)
    Row(
        Modifier
            .semantics { contentDescription = menuLabel }
            .testTag(tag)
            .clip(shape)
            .background(colors.background.copy(alpha = 0.55f))
            .border(1.dp, colors.contentMuted.copy(alpha = 0.22f), shape)
            .padding(horizontal = 3.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { a ->
            val color = when {
                !a.enabled -> colors.contentMuted.copy(alpha = 0.35f)
                a.danger -> colors.danger.copy(alpha = 0.85f)
                a.selected -> colors.accent
                else -> colors.content.copy(alpha = 0.8f)
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = a.enabled, role = Role.Button, onClick = a.onClick)
                    .padding(horizontal = 6.dp, vertical = 5.dp)
                    .testTag("${tag}_${a.id}")
                    // v46 eğitimde seçim menüsünün düğmeleri tanıtılır
                    .then(if (tag == "selection_menu") Modifier.tutorialAnchor(com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor.selection(a.id)) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(a.icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(3.dp))
                Text(a.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** v45 saydam dikey menü (ekleme seçenekleri ve Düzenle listesi): simge ve adı. Uzunsa kayar. */
@Composable
fun CanvasVerticalMenu(
    items: List<ModeAction>, tag: String, title: String?, onPicked: () -> Unit, modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    val colors = NeuTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .widthIn(min = 160.dp, max = 280.dp)
            .heightIn(max = 420.dp)
            .testTag(tag)
            .clip(shape)
            .background(colors.background.copy(alpha = 0.82f))
            .border(1.dp, colors.contentMuted.copy(alpha = 0.22f), shape)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        if (title != null) {
            Text(
                title, style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, maxLines = 1,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
        items.forEach { item ->
            val color = when {
                !item.enabled -> colors.contentMuted.copy(alpha = 0.4f)
                item.danger -> colors.danger
                else -> colors.content
            }
            Row(
                Modifier
                    .clickable(enabled = item.enabled, role = Role.Button) { onPicked(); item.onClick() }
                    .then(if (item.tag != null) Modifier.testTag(item.tag) else Modifier)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item.icon?.let { Icon(it, contentDescription = null, tint = if (item.danger) colors.danger else colors.accent.copy(alpha = if (item.enabled) 0.9f else 0.4f), modifier = Modifier.size(18.dp)) }
                Text(item.label, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        extra?.invoke()
    }
}

/** v45 seçim menüsünün beş eylemi (Taşı, Ölçekle, Kopyala, Sil, Düzenle) bu sırayla. */
@Composable
fun selectionMenuActions(
    onMove: () -> Unit,
    onScale: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    moveEnabled: Boolean = true,
    scaleEnabled: Boolean = true,
    copyEnabled: Boolean = true,
    moveSelected: Boolean = false,
    scaleSelected: Boolean = false,
): List<SelectionMenuAction> = listOf(
    SelectionMenuAction(SelectionMenuIds.MOVE, stringResource(R.string.mode_move), Icons.Filled.OpenWith, onMove, enabled = moveEnabled, selected = moveSelected),
    SelectionMenuAction(SelectionMenuIds.SCALE, stringResource(R.string.mode_scale), Icons.Filled.OpenInFull, onScale, enabled = scaleEnabled, selected = scaleSelected),
    SelectionMenuAction(SelectionMenuIds.COPY, stringResource(R.string.duplicate_selection), Icons.Filled.ContentCopy, onCopy, enabled = copyEnabled),
    SelectionMenuAction(SelectionMenuIds.DELETE, stringResource(R.string.delete_selection), Icons.Filled.Delete, onDelete, danger = true),
    SelectionMenuAction(SelectionMenuIds.EDIT, stringResource(R.string.mode_edit_pick), Icons.Filled.Edit, onEdit),
)

// v47 boş yere dokununca açılan dikey ekleme menüsü (CanvasAddMenu) kaldırıldı: tuvalde yalnız saydam seçim menüsü var.
