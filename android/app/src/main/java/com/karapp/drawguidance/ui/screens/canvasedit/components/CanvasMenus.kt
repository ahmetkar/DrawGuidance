package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme

// v45 iki parmakla uzun basınca açılan bağlam menüsü ve Ekle modundaki "buraya ekle" açılır menüsü (CanvasPopupMenu)
// kaldırıldı; yerlerine V45CanvasMenus.kt'deki seçim menüsü (Taşı, Ölçekle, Kopyala, Sil, Düzenle) ve dikey ekleme
// menüsü geldi.

/** v17: temel modlarda (Seç, Ekle, Düzenle) çizim alanının altında duran kısa yönerge. */
@Composable
fun ModeHintChip(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    NeuSurface(shape = NeuPillShape, elevation = 6.dp, modifier = modifier.testTag("mode_hint")) {
        Row(Modifier.padding(horizontal = 16.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
