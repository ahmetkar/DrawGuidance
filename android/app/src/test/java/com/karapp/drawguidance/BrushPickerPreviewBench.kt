package com.karapp.drawguidance

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.ui.drawing.PickerBrushTypes
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.drawStroke
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.withStrokeLayer
import com.karapp.drawguidance.ui.drawing.defaultSize
import com.karapp.drawguidance.ui.screens.canvasedit.components.BrushPreviewCache
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Fırça seçicinin bir karesinde örnek izlerini çizme maliyeti: eski (her karede motorla) ve yeni (önbellek). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class BrushPickerPreviewBench {
    @After fun tearDown() = stopKoin()

    private val w = 419
    private val h = 138
    private val ink = Color(0xFF1E1E1E)

    private fun measure(frame: () -> Unit): Pair<Double, Double> {
        repeat(3) { frame() }
        val times = (0 until 10).map { val t = System.nanoTime(); frame(); (System.nanoTime() - t) / 1e6 }
        return times.average() to times.max()
    }

    @Test fun previewDrawCostOldVsCached() {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val points = (0..40).map { i -> Offset(10f + i * 3f, 24f + sin(i / 40f * 2 * PI).toFloat() * 11f) }
        val old = measure {
            for (type in PickerBrushTypes) {
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
                    val s = min(size.width / 140f, size.height / 48f)
                    drawRect(Color.White)
                    withStrokeLayer(Rect(Offset.Zero, size)) {
                        val sz = (type.defaultSize() * 0.5f).coerceIn(4f, 14f)
                        scale(s, s, pivot = Offset.Zero) { drawStroke(points, ink, type, sz, 1f, seed = 3) }
                    }
                }
            }
        }

        val t0 = System.nanoTime()
        runBlocking { BrushPreviewCache.prewarm(PickerBrushTypes, ink, Color.White, h) }
        val prewarmMs = (System.nanoTime() - t0) / 1e6
        val images = PickerBrushTypes.map {
            BrushPreviewCache.get(BrushPreviewCache.Key(it, ink.toArgb(), Color.White.toArgb(), h, 1f, 1f, 1f))!!
        }
        val cached = measure {
            for (image in images) {
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
                    drawRect(Color.White)
                    drawImage(image)
                }
            }
        }
        println("PICKER eski: ${PickerBrushTypes.size} önizleme bir karede ort=${"%.1f".format(old.first)} ms, en kötü=${"%.1f".format(old.second)} ms")
        println("PICKER yeni: önbellekten ort=${"%.2f".format(cached.first)} ms, en kötü=${"%.2f".format(cached.second)} ms; ön hazırlık (bir kez, arka planda)=${"%.0f".format(prewarmMs)} ms")
        assertTrue(cached.first * 5 < old.first)
    }
}
