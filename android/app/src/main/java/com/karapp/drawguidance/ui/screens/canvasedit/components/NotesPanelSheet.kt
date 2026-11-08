package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.state.NotesPanels
import com.karapp.drawguidance.ui.theme.NeuTheme

/** v45 çizim ekranının pencereleri (not alma panelleri). CanvasEditScreen JVM yöntem sınırına yakın olduğundan ayrı. */
@Composable
fun V45EditorSheets(viewModel: CanvasEditViewModel) {
    val open by viewModel.v45.panelsOpen.collectAsState()
    val canvas by viewModel.canvas.collectAsState()
    val current = canvas ?: return
    if (open) NotesPanelSheet(current.width, current.height, onPick = { viewModel.v45.insertPanels(it) }, onDismiss = viewModel.v45::closePanels)
}

/**
 * v45 "Panel ekle" penceresi: not alma panel yerleşimleri sayfanın oranında önizlemeyle (ince yuvarlak çerçeveler,
 * hafif ton). Seçilen yerleşim sayfaya eklenir.
 */
@Composable
fun NotesPanelSheet(pageWidth: Int, pageHeight: Int, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val aspect = (pageWidth.toFloat() / pageHeight.coerceAtLeast(1)).coerceIn(0.3f, 3f)
    val colors = NeuTheme.colors
    ComicSheetFrame(stringResource(R.string.v45_panels_title), stringResource(R.string.v45_panels_hint), "notes_panel_sheet", onDismiss) { columns ->
        Grid(NotesPanels.layouts, (columns - 1).coerceAtLeast(2)) { layout, m ->
            ComicTile(stringResource(layout.title), false, { onPick(layout.id) }, m, "notes_panel_layout_${layout.id}") {
                NotesPanelPreview(layout, aspect)
                Text(
                    stringResource(R.string.v45_panel_count, layout.areaCount), style = MaterialTheme.typography.labelSmall,
                    color = colors.contentMuted, translate = false,
                )
            }
        }
    }
}

/** Yerleşimin küçük önizlemesi: beyaz sayfada yuvarlak köşeli, hafif tonlu alanlar. */
@Composable
private fun NotesPanelPreview(layout: NotesPanels.Layout, aspect: Float) {
    val rects = remember(layout.id, aspect) { layout.rects(aspect * 1000f, 1000f) }
    val frame = Color(0xFF90A4AE)
    val tint = Color(0xFFE3F2FD)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.height(96.dp).aspectRatio(aspect)) {
            val k = size.height / 1000f
            drawRect(Color.White, size = size)
            val r = 18f * k
            for (b in rects) {
                val tl = Offset(b[0] * k, b[1] * k)
                val sz = Size((b[2] - b[0]) * k, (b[3] - b[1]) * k)
                drawRoundRect(tint, tl, sz, CornerRadius(r))
                drawRoundRect(frame, tl, sz, CornerRadius(r), style = Stroke(width = 1.2.dp.toPx()))
            }
        }
    }
}
