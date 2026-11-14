package com.karapp.drawguidance.ui.components

import androidx.compose.foundation.layout.ColumnScope
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
fun NeuSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
