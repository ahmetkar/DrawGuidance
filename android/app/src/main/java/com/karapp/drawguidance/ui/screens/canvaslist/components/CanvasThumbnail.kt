package com.karapp.drawguidance.ui.screens.canvaslist.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.karapp.drawguidance.data.images.CanvasImageCache
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.drawCanvasContent
import kotlin.math.min

/**
 * Tuval kartındaki canlı önizleme: çizimler vektör olduğu için kart boyutuna
 * ölçeklenerek düzenleme ekranıyla aynı çizim motoruyla yeniden çizilir.
 */
@Composable
fun CanvasThumbnail(
    canvasWidth: Int,
    canvasHeight: Int,
    backgroundColorHex: String,
    strokes: List<Stroke>,
    modifier: Modifier = Modifier,
    background: com.karapp.drawguidance.data.model.BackgroundSpec? = null,
) {
    val imagesVersion by CanvasImageCache.version.collectAsState()
    Canvas(modifier = modifier.fillMaxSize()) {
        if (canvasWidth <= 0 || canvasHeight <= 0 || imagesVersion < 0) return@Canvas
        val s = min(size.width / canvasWidth, size.height / canvasHeight)
        val ox = (size.width - canvasWidth * s) / 2f
        val oy = (size.height - canvasHeight * s) / 2f
        translate(ox, oy) {
            scale(s, s, pivot = Offset.Zero) {
                drawCanvasContent(hexToColor(backgroundColorHex), strokes, canvasWidth.toFloat(), canvasHeight.toFloat(), decor = background)
            }
        }
    }
}
