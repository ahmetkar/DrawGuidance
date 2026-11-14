package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v50 dikey ekranda düzenleyicinin alt pencereleri tuvalin üstünü kapatan kalıcı (modal) sayfa değil, çizim alanının
 * altına yerleşen küçük pencerelerdir (en çok alanın [MAX_HEIGHT_FRACTION] kadarı): pencere açıkken seçim menüsü de
 * kullanılabilir. İkisi çakışırsa son dokunulan üstte durur ([sheetOnTop]); seçim menüsü olabildiğince pencerenin
 * üstüne yerleşir ([coveredBottomPx]).
 */
@Stable
class DockedSheetHost {
    internal val entries = mutableStateListOf<DockedSheetEntry>()

    /** Son dokunulan alt pencere mi (true) yoksa seçim menüsü mü (false). Yeni açılan pencere üste gelir. */
    var sheetOnTop by mutableStateOf(true)

    /** Alt pencerelerin çizim alanının altından kapladığı yükseklik (piksel; pencere yoksa 0). */
    var coveredBottomPx by mutableIntStateOf(0)
        internal set

    val open: Boolean get() = entries.isNotEmpty()

    /** Seçim menüsüne dokunuldu: çakışırlarsa menü pencerenin üstünde. */
    fun menuTouched() { sheetOnTop = false }

    /** Seçim menüsünün katman sırası (zIndex). */
    fun menuZ(): Float = if (sheetOnTop) MENU_BELOW_Z else MENU_ABOVE_Z

    companion object {
        /** Alt pencerenin en büyük yüksekliği: çizim alanının oranı. */
        const val MAX_HEIGHT_FRACTION = 0.46f
        const val SHEET_LOW_Z = 4f
        const val SHEET_HIGH_Z = 8f
        const val MENU_BELOW_Z = 5f
        const val MENU_ABOVE_Z = 9f

        /** Dikey ekran mı (yerleşik pencereler yalnız dikeyde). */
        fun portrait(widthDp: Int, heightDp: Int): Boolean = heightDp > widthDp
    }
}

internal class DockedSheetEntry(content: @Composable ColumnScope.() -> Unit, onDismiss: () -> Unit) {
    var content by mutableStateOf(content)
    var onDismiss by mutableStateOf(onDismiss)
}

/** v50 düzenleyicide verilir (dikey ekranda alt pencereler buraya yerleşir); başka yerde null. */
val LocalDockedSheets = staticCompositionLocalOf<DockedSheetHost?> { null }

/** v50 dikey ekranda düzenleyicide mi: alt pencere yerleşik pencere olarak açılır. */
@Composable
fun dockedSheetHostOrNull(): DockedSheetHost? {
    val host = LocalDockedSheets.current ?: return null
    val c = LocalConfiguration.current
    return host.takeIf { DockedSheetHost.portrait(c.screenWidthDp, c.screenHeightDp) }
}

/**
 * v50 alt pencereyi [host]a kaydeder (çağrıldığı yerde bir şey çizmez; içerik [DockedSheetsLayer]de çizilir). İçerik ve
 * kapatma her yeniden çizimde güncellenir; çağıran çıkınca pencere kapanır.
 */
@Composable
fun DockedSheet(host: DockedSheetHost, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val entry = remember { DockedSheetEntry(content, onDismiss) }
    SideEffect {
        entry.content = content
        entry.onDismiss = onDismiss
    }
    DisposableEffect(entry) {
        host.entries.add(entry)
        host.sheetOnTop = true
        onDispose { host.entries.remove(entry) }
    }
}

/**
 * v50 yerleşik alt pencerelerin katmanı: çizim alanının (bu kutunun) altına, [bottomPadding] (alttaki mod çubuğu) kadar
 * yukarıda. Pencere boş alanlarda dokunmayı tutmaz: tuval ve seçim menüsü kullanılabilir. Geri tuşu en üstteki
 * pencereyi kapatır.
 */
@Composable
fun DockedSheetsLayer(host: DockedSheetHost, bottomPadding: () -> Int = { 0 }) {
    if (host.entries.isEmpty()) {
        SideEffect { if (host.coveredBottomPx != 0) host.coveredBottomPx = 0 }
        return
    }
    BackHandler { host.entries.lastOrNull()?.onDismiss?.invoke() }
    val colors = NeuTheme.colors
    val closeLabel = stringResource(R.string.close_panel)
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(if (host.sheetOnTop) DockedSheetHost.SHEET_HIGH_Z else DockedSheetHost.SHEET_LOW_Z)) {
        val maxH = maxHeight * DockedSheetHost.MAX_HEIGHT_FRACTION
        val last = host.entries.lastIndex
        host.entries.forEachIndexed { i, e ->
            key(e) {
                NeuSurface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = with(androidx.compose.ui.platform.LocalDensity.current) { bottomPadding().toDp() })
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxH)
                        .then(if (i == last) Modifier.onSizeChanged { host.coveredBottomPx = it.height + bottomPadding() } else Modifier)
                        .pointerInput(Unit) {
                            // Pencereye dokunuldu: çakışırsa seçim menüsünün üstüne gelir (dokunuş içeriğe geçer)
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                host.sheetOnTop = true
                            }
                        }
                        .testTag("docked_sheet"),
                    elevation = 14.dp,
                    fill = colors.background,
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp)) {
                            Box(
                                Modifier.align(Alignment.Center).padding(top = 6.dp).size(width = 36.dp, height = 4.dp)
                                    .clip(RoundedCornerShape(2.dp)).background(colors.contentMuted.copy(alpha = 0.4f)),
                            )
                            NeuIconButton(
                                Icons.Filled.Close, closeLabel, { e.onDismiss() }, size = 32.dp,
                                modifier = Modifier.align(Alignment.CenterEnd).testTag("docked_sheet_close"),
                            )
                        }
                        e.content(this)
                    }
                }
            }
        }
    }
}
