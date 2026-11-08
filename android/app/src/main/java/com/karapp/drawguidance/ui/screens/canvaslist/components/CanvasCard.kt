package com.karapp.drawguidance.ui.screens.canvaslist.components

import com.karapp.drawguidance.ui.theme.neuOutline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.ui.components.GalleryTile
import com.karapp.drawguidance.ui.theme.NeuTheme

/** Tuvalin tek satırlık bilgisi: tür (not), boyut ve sayfa sayısı. */
@Composable
fun canvasSubtitle(canvas: CanvasUi): String =
    (com.karapp.drawguidance.ui.components.canvasKindBadge(canvas.kind)?.let { "$it · " } ?: "") +
        "${canvas.width}×${canvas.height}" + (if (canvas.pageCount > 1) " · " + com.karapp.drawguidance.i18n.I18n.trf("{0} sayfa", canvas.pageCount) else "") +
        // Kayıtlı boyut (çizim verisi + eklenen resimler)
        (if (canvas.sizeBytes > 0) " · " + com.karapp.drawguidance.ui.components.formatFileSize(canvas.sizeBytes) else "")

/**
 * Galeri görünümünde tuval: kendi oranındaki sade önizleme (kutu yok), altında adı ve bilgisi.
 * Uzun basınca seçilir (önizlemenin çevresi vurgulanır, köşede onay işareti).
 */
@Composable
fun CanvasCard(
    canvas: CanvasUi,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GalleryTile(
        title = canvas.name,
        subtitle = canvasSubtitle(canvas),
        ratio = canvas.width.toFloat() / canvas.height.coerceAtLeast(1),
        onClick = onClick,
        onLongClick = onLongClick,
        selected = isSelected,
        modifier = modifier,
    ) {
        CanvasThumbnail(
            canvasWidth = canvas.width,
            canvasHeight = canvas.height,
            backgroundColorHex = canvas.pagePaperHex,
            // Gizli katmanlar önizlemede de görünmez
            strokes = androidx.compose.runtime.remember(canvas.strokes, canvas.layers) { com.karapp.drawguidance.data.model.Layers.visible(canvas.strokes, canvas.layers) },
            modifier = Modifier.fillMaxSize(),
            background = canvas.pageBackground,
        )
        if (isSelected) SelectedMark(Modifier.align(Alignment.TopStart).padding(8.dp))
    }
}


@Composable
private fun SelectedMark(modifier: Modifier) {
    val colors = NeuTheme.colors
    Box(
        modifier.size(24.dp).clip(CircleShape).background(colors.accent),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Check, null, tint = colors.onAccent, modifier = Modifier.size(16.dp)) }
}

/** Liste görünümünde tuval: sade satır — küçük önizleme, ad, bilgi ve son düzenleme tarihi. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CanvasRow(
    canvas: CanvasUi,
    isSelected: Boolean,
    dateText: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (isSelected) Modifier.background(colors.accent.copy(alpha = 0.10f)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).neuOutline(com.karapp.drawguidance.ui.theme.NeuTheme.colors, shape).clip(shape).background(Color.White)) {
            CanvasThumbnail(
                canvas.width, canvas.height, canvas.pagePaperHex,
                androidx.compose.runtime.remember(canvas.strokes, canvas.layers) { com.karapp.drawguidance.data.model.Layers.visible(canvas.strokes, canvas.layers) },
                Modifier.fillMaxSize(),
                canvas.pageBackground,
            )
            if (isSelected) SelectedMark(Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                canvas.name, translate = false, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (isSelected) colors.accent else colors.content,
            )
            Text(canvasSubtitle(canvas) + " · " + dateText, style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
