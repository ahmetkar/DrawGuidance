package com.karapp.drawguidance.ui.screens.canvasedit.components

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

/**
 * v45 tabletlerde (en kısa kenar ≥ 600 dp) düzenleyicinin pencereleri sürüklenebilir: açılan pencereler (alt
 * pencereler), yan panel (Fırça / Elemanlar / Katmanlar) ve üstteki iki menü (araç satırı ve mod seçici). Konumlar
 * uygulama ayarlarında ([WindowPositionStore]) saklanır; ekranın dışına çıkılamaz; her pencerenin tutamağında
 * "Konumu sıfırla" düğmesi vardır. Telefonlarda yerleşim değişmez.
 */
object TabletWindows {
    fun isTablet(configuration: Configuration): Boolean = configuration.smallestScreenWidthDp >= 600

    /** Pencere kimlikleri. */
    const val TOP_MENU = "top_menu"
    const val MODE_MENU = "mode_menu"
    const val SIDE_PANEL = "side_panel"
    const val SHEET = "sheet"

    /**
     * Kaydırma ([offset]) pencerenin asıl yeri [home] ekranın ([container]) içinde kalacak biçimde sınırlanır.
     * Pencere ekrandan büyükse o eksende kıpırdamaz.
     */
    fun clamp(offset: Offset, home: Rect, container: Size): Offset {
        val minX = -home.left
        val maxX = container.width - home.right
        val minY = -home.top
        val maxY = container.height - home.bottom
        return Offset(
            if (maxX < minX) 0f else offset.x.coerceIn(minX, maxX),
            if (maxY < minY) 0f else offset.y.coerceIn(minY, maxY),
        )
    }
}

/** v45 pencere konumları (dp cinsinden kaydırma) uygulama ayarlarında: oturumlar arasında korunur. */
class WindowPositionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Kayıtlı kaydırma (dp); yoksa sıfır. */
    fun load(id: String): Offset {
        val raw = prefs.getString(id, null) ?: return Offset.Zero
        val parts = raw.split(',')
        val x = parts.getOrNull(0)?.toFloatOrNull() ?: return Offset.Zero
        val y = parts.getOrNull(1)?.toFloatOrNull() ?: return Offset.Zero
        return Offset(x, y)
    }

    fun save(id: String, dp: Offset) {
        if (dp == Offset.Zero) prefs.edit().remove(id).apply() else prefs.edit().putString(id, "${dp.x},${dp.y}").apply()
    }

    fun clear(id: String) = prefs.edit().remove(id).apply()

    companion object {
        const val PREFS = "dg_v45_windows"
    }
}

/** v45 sürüklenebilir pencerenin durumu: kaydırma (piksel), asıl yeri ve ekran boyu (sınır için). */
@Stable
class DraggableWindowState(val id: String, private val store: WindowPositionStore, private val density: Density) {
    /** Pencerenin asıl yerinden kaydırması (piksel). */
    var offset by mutableStateOf(with(density) { store.load(id).let { Offset(it.x.dp.toPx(), it.y.dp.toPx()) } })
        private set

    /** Pencerenin kaydırmasız yeri ve ekranın boyu (ilk yerleşimde öğrenilir). */
    var home: Rect? = null
        private set
    var container: Size? = null
        private set

    internal fun placed(home: Rect, container: Size) {
        this.home = home
        this.container = container
        // Ekran döndü ya da küçüldüyse pencere yine ekranın içinde kalır
        val clamped = TabletWindows.clamp(offset, home, container)
        if (clamped != offset) offset = clamped
    }

    /** Sürükleme: ekranın dışına çıkılmaz. */
    fun dragBy(delta: Offset) {
        val next = offset + delta
        val h = home
        val c = container
        offset = if (h != null && c != null) TabletWindows.clamp(next, h, c) else next
    }

    /** Sürükleme bitti: konum kaydedilir. */
    fun settle() = store.save(id, with(density) { Offset(offset.x.toDp().value, offset.y.toDp().value) })

    /** Konumu sıfırla: pencere asıl yerine döner (kayıt silinir). */
    fun reset() {
        offset = Offset.Zero
        store.clear(id)
    }

    val moved: Boolean get() = offset != Offset.Zero
}

/** v45 tablette sürüklenebilir pencere durumu; telefonda null (yerleşim değişmez). */
@Composable
fun rememberTabletWindow(id: String): DraggableWindowState? {
    val configuration = LocalConfiguration.current
    if (!TabletWindows.isTablet(configuration)) return null
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(id, density) { DraggableWindowState(id, WindowPositionStore(context), density) }
}

/**
 * v45 pencereyi kaydırmasıyla gösterir; asıl yerini (kaydırmasız; bu ölçüm kaydırmanın dışında) ve ekran boyunu
 * duruma bildirir.
 */
fun Modifier.tabletWindow(state: DraggableWindowState?): Modifier = if (state == null) this else this
    .zIndex(2f)
    .onGloballyPositioned { c -> state.placed(c.boundsInRoot(), c.findRootCoordinates().size.let { Size(it.width.toFloat(), it.height.toFloat()) }) }
    .offset { IntOffset(state.offset.x.roundToInt(), state.offset.y.roundToInt()) }

/** v45 pencerenin tutamağındaki sürükleme hareketi (yan panelin başlığı gibi var olan tutamaklar için). */
fun Modifier.windowDrag(state: DraggableWindowState): Modifier = this.pointerInput(state) {
    detectDragGestures(onDragEnd = { state.settle() }, onDragCancel = { state.settle() }) { change, amount ->
        change.consume()
        state.dragBy(amount)
    }
}

/** v45 tutamak: sürükleme simgesi ve (pencere yerinden oynadıysa) "Konumu sıfırla". */
@Composable
fun WindowDragHandle(state: DraggableWindowState, modifier: Modifier = Modifier, vertical: Boolean = false) {
    val colors = NeuTheme.colors
    val dragLabel = stringResource(R.string.v45_window_drag)
    val resetLabel = stringResource(R.string.v45_window_reset)
    val content: @Composable () -> Unit = {
        Icon(
            Icons.Filled.DragIndicator, contentDescription = null, tint = colors.contentMuted,
            modifier = Modifier.size(28.dp).windowDrag(state).semantics { contentDescription = dragLabel }.testTag("window_drag:${state.id}"),
        )
        // Sıfırla düğmesinin yeri hep ayrılır: düğme çıkınca pencerenin boyu ve asıl yeri değişmez (sıçrama olmaz).
        // Düğme her zaman yalnız simge (menülerin düğme içi/altı ad ayarından etkilenmez)
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (state.moved) CompositionLocalProvider(
                com.karapp.drawguidance.ui.components.LocalInlineLabels provides false,
                com.karapp.drawguidance.ui.components.LocalButtonLabels provides false,
            ) {
                NeuIconButton(Icons.Filled.RestartAlt, resetLabel, state::reset, size = 28.dp, modifier = Modifier.testTag("window_reset:${state.id}"))
            }
        }
    }
    if (vertical) Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    else Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

/**
 * v45 düzenleyicide tabletteyken verilir: alt pencereler ([com.karapp.drawguidance.ui.components.NeuSheet]) ekranın
 * altına yapışık sayfa yerine sürüklenebilir pencere olarak açılır.
 */
val LocalFloatingSheets = staticCompositionLocalOf<DraggableWindowState?> { null }

/** v45 [content] içindeki alt pencereler tablette sürüklenebilir pencere olur. */
@Composable
fun FloatingSheetsScope(state: DraggableWindowState?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFloatingSheets provides state, content = content)
}

/**
 * v45 tablette alt pencerenin yerine sürüklenebilir pencere: varsayılan yeri ekranın altının ortası (sayfanın yeri);
 * üstteki tutamaktan sürüklenir, dışına dokununca kapanır.
 */
@Composable
fun FloatingSheet(state: DraggableWindowState, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = NeuTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Pencerenin dışına dokunma: kapanır
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val up = waitForUpOrCancellation()
                        if (up != null && !down.isConsumed) onDismiss()
                    }
                },
        ) {
            NeuSurface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.85f)
                    .tabletWindow(state)
                    .testTag("floating_sheet")
                    // Pencerenin içindeki dokunuşlar dışarıya (kapatmaya) geçmez
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                        }
                    },
                elevation = 14.dp,
                fill = colors.background,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    WindowDragHandle(state, Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
                    content()
                }
            }
        }
    }
}
