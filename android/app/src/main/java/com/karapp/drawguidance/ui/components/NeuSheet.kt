package com.karapp.drawguidance.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v37 uygulamanın alt sayfası (bütün sayfaların ortak kabı): yarım açılmadan tam açılır, tema renkleriyle. Eskiden
 * her sayfa aynı dört satırı yineliyordu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuSheet(onDismiss: () -> Unit, full: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    // v51 ekleme ve seçim pencereleri (karakterler, efektler, nesneler, şekiller, alıştırmalar, eğitimler...) dikey
    // ekranda da ekranın tamamını kaplar; içlerinde açılan pencereler yerleşik değil, normal alt pencere olur.
    // v52 dikey ekranda bütün pencereler (düzenleyicide yerleşik olanlar da) ekranı kaplar; tablette yatayda kayan pencere
    val portraitFull = isPortraitScreen() && com.karapp.drawguidance.ui.screens.canvasedit.components.LocalFloatingSheets.current == null
    if (portraitFull || (full && com.karapp.drawguidance.ui.screens.canvasedit.components.dockedSheetHostOrNull() != null)) {
        val colors = NeuTheme.colors
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            contentColor = colors.content,
        ) {
            CompositionLocalProvider(
                com.karapp.drawguidance.ui.screens.canvasedit.components.LocalDockedSheets provides null,
                LocalFullSheet provides true,
            ) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(), content = content)
            }
        }
        return
    }
    // v50 dikey ekranda düzenleyicinin pencereleri çizim alanının altında küçük, kalıcı olmayan pencere (seçim menüsü
    // de kullanılabilir; son dokunulan üstte)
    val docked = com.karapp.drawguidance.ui.screens.canvasedit.components.dockedSheetHostOrNull()
    if (docked != null) {
        com.karapp.drawguidance.ui.screens.canvasedit.components.DockedSheet(docked, onDismiss, content)
        return
    }
    // v45 tablette düzenleyicinin pencereleri sürüklenebilir pencere olarak açılır (telefonda alt sayfa)
    val floating = com.karapp.drawguidance.ui.screens.canvasedit.components.LocalFloatingSheets.current
    if (floating != null) {
        com.karapp.drawguidance.ui.screens.canvasedit.components.FloatingSheet(floating, onDismiss, content)
        return
    }
    val colors = NeuTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        contentColor = colors.content,
        content = content,
    )
}

/**
 * v42 pencere içeriği tek başına alt pencere olarak ya da ([inline]) başka bir pencerenin içinde (birleşik Kaydet
 * penceresinde sekme) gösterilir.
 */
@Composable
fun NeuSheetOrInline(inline: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (inline) content() else NeuSheet(onDismiss) { content() }
}

/** v51 pencere tam ekran açıldı mı (içerik yüksekliğini sınırlamaz, ekranı doldurur). */
val LocalFullSheet = staticCompositionLocalOf { false }

/**
 * v51 pencere gövdesinin yüksekliği: tam ekran pencerede kalan alanı doldurur, ötekilerde en çok [max] (eskiden
 * `heightIn(max = 620.dp)`).
 */
@Composable
fun Modifier.sheetBodyHeight(max: Dp = 620.dp): Modifier =
    if (LocalFullSheet.current) this.fillMaxHeight() else this.heightIn(max = max)

/** v52 ekran dikey mi (pencereler dikeyde ekranı kaplar). */
@Composable
fun isPortraitScreen(): Boolean {
    val c = LocalConfiguration.current
    return c.screenHeightDp >= c.screenWidthDp
}

/** v52 doğrudan ModalBottomSheet kullanan pencerelerin gövdesi: dikey ekranda ekranı doldurur. */
@Composable
fun Modifier.portraitFill(): Modifier = if (isPortraitScreen()) this.fillMaxHeight() else this
