package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.BrushIcons
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.brushCategoryLabel
import com.karapp.drawguidance.ui.components.brushLabel
import com.karapp.drawguidance.ui.drawing.BrushCategory
import com.karapp.drawguidance.ui.theme.NeuCardShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic
import kotlinx.coroutines.launch

/**
 * Kategorili fırça seçici: üstte kategori çipleri (dokununca o bölüme kayar), altında her
 * kategori için fırçanın gerçek çizim motoruyla çizilmiş örnek izleri. Sütun sayısı ekran
 * genişliğine göre (telefon 2, yatay/tablet 3–4) ayarlanır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrushPickerModal(
    selected: BrushType,
    colorHex: String,
    onDismiss: () -> Unit,
    onSelect: (BrushType) -> Unit,
    kind: com.karapp.drawguidance.data.model.CanvasKind = com.karapp.drawguidance.data.model.CanvasKind.ART,
) {
    val categories = BrushCategory.forKind(kind)
    val colors = NeuTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val sectionOffsets = remember { mutableStateMapOf<BrushCategory, Int>() }
    // v22: açılınca seçili (son kullanılan) fırçanın bölümüne kayılır
    val selectedCategory = BrushCategory.of(selected)
    val autoScrolled = remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(selectedCategory?.let { sectionOffsets[it] }) {
        val y = selectedCategory?.let { sectionOffsets[it] } ?: return@LaunchedEffect
        if (!autoScrolled.value) {
            autoScrolled.value = true
            if (y > 0) scroll.scrollTo(y)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        contentColor = colors.content,
    ) {
        // İçerik tek GPU katmanında: sayfa açılış animasyonu ve kaydırma sırasında kartlar yeniden çizilmez
        Column(Modifier.fillMaxWidth().navigationBarsPadding().graphicsLayer()) {
            Text(
                stringResource(R.string.brush_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                categories.forEach { category ->
                    NeuChip(
                        text = brushCategoryLabel(category),
                        selected = BrushCategory.of(selected) == category,
                        onClick = { scope.launch { scroll.animateScrollTo(sectionOffsets[category] ?: 0) } },
                    )
                }
            }
            val ink = hexToColor(colorHex)
            val paper = if (colors.isDark) Color(0xFFF4F1EA) else Color.White
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = (maxWidth / 170.dp).toInt().coerceIn(2, 4)
                Column(
                    Modifier.fillMaxWidth().verticalScroll(scroll).graphicsLayer().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    categories.forEach { category ->
                        Text(
                            brushCategoryLabel(category),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.contentMuted,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .onGloballyPositioned { sectionOffsets[category] = it.positionInParent().y.toInt() },
                        )
                        category.brushes.chunked(columns).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { type ->
                                    BrushCard(type, type == selected, ink, paper, { onSelect(type) }, Modifier.weight(1f))
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrushCard(type: BrushType, isSelected: Boolean, ink: Color, paper: Color, onClick: () -> Unit, modifier: Modifier) {
    val colors = NeuTheme.colors
    Column(
        modifier = modifier
            .neumorphic(colors, NeuCardShape, elevation = if (isSelected) 3.dp else 6.dp, selected = isSelected)
            .clip(NeuCardShape)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrushPreview(
            brushType = type,
            color = ink,
            paper = paper,
            modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                BrushIcons.of(type),
                contentDescription = null,
                tint = if (isSelected) colors.accent else colors.content,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                brushLabel(type),
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) colors.accent else colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
