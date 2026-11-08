package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** v40 sayfa kaydırıcısının hesabı (birim testli). */
object PageScrub {
    /** Bir sayfalık sürükleme adımı: şerit genişliği / sayfa sayısı, [minPx]..[maxPx] aralığında. */
    fun step(widthPx: Float, pageCount: Int, minPx: Float, maxPx: Float): Float =
        (widthPx / pageCount.coerceAtLeast(1)).coerceIn(minPx, maxPx)

    /** [start] sayfasından [dx] piksel sürüklenince gelinen sayfa (0..[pageCount]−1). */
    fun pageFor(start: Int, dx: Float, step: Float, pageCount: Int): Int =
        (start + (dx / step.coerceAtLeast(1f)).roundToInt()).coerceIn(0, (pageCount - 1).coerceAtLeast(0))

    /** Büyütülmüş şeritte gösterilen sayfalar ([page] ortada, iki yanda [radius] sayfa). */
    fun window(page: Int, pageCount: Int, radius: Int = 3): IntRange =
        (page - radius).coerceAtLeast(0)..(page + radius).coerceAtMost(pageCount - 1)
}

/** Kaydırıcının durumu: açık mı, gösterilen sayfa; uzun basılan sayfayı bulmak için şerit ve sayfa düğmelerinin yeri. */
class PageScrubState {
    var active by mutableStateOf(false)
    var page by mutableIntStateOf(0)
    var root: LayoutCoordinates? = null
    val chips = HashMap<Int, LayoutCoordinates>()

    /** Şeritteki [at] noktasının altındaki sayfa düğmesi. */
    fun chipAt(at: Offset): Int? {
        val r = root?.takeIf { it.isAttached } ?: return null
        return chips.entries.firstOrNull { (_, c) ->
            if (!c.isAttached) return@firstOrNull false
            val p = r.localPositionOf(c, Offset.Zero)
            at.x >= p.x && at.x <= p.x + c.size.width && at.y >= p.y && at.y <= p.y + c.size.height
        }?.key
    }
}

/**
 * Uzun basınca kaydırıcı açılır (önce dokunuşlar şeridin düğmelerine ve kaydırmasına gider; uzun basmadan sonra
 * kaydırıcı hepsini alır). Sürükledikçe [onPage], sürüklemeden bırakınca [onLongPress] (basılan nokta).
 */
fun Modifier.pageScrubGestures(
    state: PageScrubState,
    pageCount: Int,
    currentPage: () -> Int,
    onPage: (Int) -> Unit,
    onLongPress: (Offset) -> Unit,
): Modifier = pointerInput(pageCount) {
    val minStep = 4.dp.toPx()
    val maxStep = 48.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        // Uzun basma beklenir: önce bırakılır ya da kaydırılırsa normal dokunuş/kaydırma
        val aborted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var result: Boolean? = null
            while (result == null) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val ch = e.changes.firstOrNull { it.id == down.id }
                if (ch == null || !ch.pressed || (ch.position - down.position).getDistance() > slop || e.changes.size > 1) result = true
            }
            result
        }
        if (aborted != null || pageCount < 1) return@awaitEachGesture
        val start = currentPage().coerceIn(0, pageCount - 1)
        val step = PageScrub.step(size.width.toFloat(), pageCount, minStep, maxStep)
        state.page = start
        state.active = true
        var moved = false
        try {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                // Kaydırıcı açıkken şeridin düğmeleri ve kaydırması dokunuşu almaz
                e.changes.forEach { it.consume() }
                if (!ch.pressed) break
                val dx = ch.position.x - down.position.x
                if (abs(dx) > slop) moved = true
                val p = PageScrub.pageFor(start, dx, step, pageCount)
                if (p != state.page) {
                    state.page = p
                    onPage(p)
                }
            }
        } finally {
            state.active = false
        }
        if (!moved) onLongPress(down.position)
    }
}

/** Şeridin üstünde açılan büyütülmüş sayfa şeridi: ortada büyük sayfa numarası, iki yanında komşu sayfalar. */
@Composable
fun PageScrubberPopup(page: Int, pageCount: Int, cover: Boolean = false) {
    val colors = NeuTheme.colors
    val lift = with(LocalDensity.current) { 84.dp.roundToPx() }
    val description = stringResource(R.string.v40_scrubber)
    // v45 kapaklı çizgi romanda kapak "Kapak", sonraki sayfa 1
    val position = pagePosition(page, pageCount, cover)
    val coverShort = stringResource(R.string.v45_cover)
    Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -lift), properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        NeuSurface(
            modifier = Modifier.widthIn(min = 220.dp).testTag("page_scrubber").semantics {
                contentDescription = description
                stateDescription = position
            },
            shape = NeuPillShape,
            elevation = 10.dp,
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PageScrub.window(page, pageCount).forEach { i ->
                        val d = abs(i - page)
                        Box(Modifier.width(if (d == 0) 56.dp else 30.dp), contentAlignment = Alignment.Center) {
                            Text(
                                com.karapp.drawguidance.data.model.PageNumbering.number(i, cover)?.toString() ?: coverShort.take(if (d == 0) 5 else 1),
                                translate = false,
                                style = if (d == 0) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleSmall,
                                fontWeight = if (d == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (d == 0) colors.accent else colors.content,
                                modifier = Modifier.alpha(if (d == 0) 1f else (1f - d * 0.22f).coerceAtLeast(0.3f)),
                            )
                        }
                    }
                }
                Text(position, style = MaterialTheme.typography.labelMedium, color = colors.contentMuted, modifier = Modifier.testTag("page_scrubber_position"))
            }
        }
    }
}
