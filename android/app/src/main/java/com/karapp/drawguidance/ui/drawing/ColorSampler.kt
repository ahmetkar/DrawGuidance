package com.karapp.drawguidance.ui.drawing

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.Layers
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.drawCanvasContent

/**
 * v43 damlalık: tuvalin (kağıt, arka plan, görünür çizimler ve resimler) bir noktadaki rengi. Yalnız o noktanın 3×3
 * piksellik çevresi çizilir (bütün sayfa değil); ortalaması alınır. Saydam yerlerde kağıt rengi.
 */
object ColorSampler {
    fun sample(canvas: CanvasUi, x: Float, y: Float, page: Int = canvas.currentPage): String? {
        if (x < 0f || y < 0f || x > canvas.width || y > canvas.height) return null
        val strokes = Layers.visible(canvas.pages.getOrElse(page) { canvas.strokes }, canvas.layers)
        val bmp = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp.asImageBitmap()), Size(3f, 3f)) {
            translate(1f - x, 1f - y) {
                drawCanvasContent(
                    hexToColor(canvas.paperOf(page)), strokes, canvas.width.toFloat(), canvas.height.toFloat(),
                    decor = canvas.backgroundOf(page),
                ) {}
            }
        }
        var r = 0; var g = 0; var b = 0
        for (yy in 0 until 3) for (xx in 0 until 3) {
            val c = bmp.getPixel(xx, yy)
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
        }
        bmp.recycle()
        return "#%02X%02X%02X".format(r / 9, g / 9, b / 9)
    }
}
