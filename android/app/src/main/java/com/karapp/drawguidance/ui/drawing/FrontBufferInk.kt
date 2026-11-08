package com.karapp.drawguidance.ui.drawing

import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.RectF
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import androidx.graphics.lowlatency.CanvasFrontBufferedRenderer
import com.karapp.drawguidance.diagnostics.AppLog

/**
 * v18 düşük gecikmeli kalem çizimi (Android 10+): çizilmekte olan çizginin kesinleşen parçaları, ekranın normal çizim
 * döngüsünü (1–3 kare) beklemeden doğrudan ön tampona çizilir. Çizim bitince ön tampon temizlenir; çizgi zaten
 * tuvalin kendi katmanında görünmektedir. Yalnızca opak, normal karışımlı fırçalarla kalemle çizerken kullanılır
 * (yarı saydam fırçalar ve silgi normal yoldan çizilir). Bir hata olursa kendini kapatır; çizim normal yoldan sürer.
 */
@RequiresApi(29)
internal class FrontBufferInk(surfaceView: SurfaceView) {

    /** Ön tampona çizilecek parça ve görünüm dönüşümü (tuval → ekran), kağıt kırpması (ekran). */
    class Item(val chunk: StrokeChunk, val scale: Float, val offsetX: Float, val offsetY: Float, val clip: RectF?)

    @Volatile var failed = false
        private set

    private val renderer: CanvasFrontBufferedRenderer<Item>? = runCatching {
        CanvasFrontBufferedRenderer(surfaceView, object : CanvasFrontBufferedRenderer.Callback<Item> {
            override fun onDrawFrontBufferedLayer(canvas: Canvas, bufferWidth: Int, bufferHeight: Int, param: Item) {
                val save = canvas.save()
                param.clip?.let { canvas.clipRect(it) }
                canvas.translate(param.offsetX, param.offsetY)
                canvas.scale(param.scale, param.scale)
                param.chunk.draw(canvas)
                canvas.restoreToCount(save)
            }

            override fun onDrawMultiBufferedLayer(canvas: Canvas, bufferWidth: Int, bufferHeight: Int, params: Collection<Item>) {
                // Çok tamponlu katman hiç kullanılmaz: her zaman saydam (tuval kendi katmanında çizer)
                canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            }
        })
    }.onFailure { fail("create", it) }.getOrNull()

    fun draw(item: Item) {
        val r = renderer ?: return
        if (failed) return
        runCatching { r.renderFrontBufferedLayer(item) }.onFailure { fail("render", it) }
    }

    fun clear() {
        val r = renderer ?: return
        runCatching { r.clear() }.onFailure { fail("clear", it) }
    }

    fun release() {
        runCatching { renderer?.release(true) }
    }

    private fun fail(where: String, e: Throwable) {
        failed = true
        runCatching { AppLog.w("FrontBufferInk", "düşük gecikmeli çizim kapatıldı ($where): ${e.message}") }
    }
}
