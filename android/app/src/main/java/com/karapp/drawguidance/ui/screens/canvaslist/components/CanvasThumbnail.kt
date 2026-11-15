package com.karapp.drawguidance.ui.screens.canvaslist.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.karapp.drawguidance.data.images.CanvasImageCache
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.drawCanvasContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tuval kartındaki önizleme: çizimler vektör olduğu için kart boyutuna ölçeklenerek düzenleme ekranıyla aynı çizim
 * motoruyla çizilir.
 *
 * v52 önizleme arka planda bir kez resme çizilir ve önbellekte tutulur. Eskiden her kart, her karede bütün tuvali ana
 * iş parçacığında yeniden çiziyordu: çok ve ağır tuvalde (dokulu fırçalar, v51 piksel fırçaları) ana sayfa ve
 * Tuvallerim açılışta donuyordu.
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
    var box by remember { mutableStateOf(IntSize.Zero) }
    val key = ThumbnailCache.key(canvasWidth, canvasHeight, backgroundColorHex, strokes, background, box, imagesVersion)
    // Robolectric testlerinde (mağaza görüntüleri) eşzamanlı: ekran görüntüsünde önizleme hazır olsun
    val initial = ThumbnailCache.get(key) ?: if (ThumbnailCache.synchronous && box.width > 0 && box.height > 0 && canvasWidth > 0 && canvasHeight > 0)
        runCatching { ThumbnailCache.render(key, canvasWidth, canvasHeight, backgroundColorHex, strokes, background, box) }.getOrNull() else null
    val image by produceState(initial, key) {
        if (value != null || box.width <= 0 || box.height <= 0 || canvasWidth <= 0 || canvasHeight <= 0) return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching { ThumbnailCache.render(key, canvasWidth, canvasHeight, backgroundColorHex, strokes, background, box) }.getOrNull()
        }
    }
    val paper = remember(backgroundColorHex) { hexToColor(backgroundColorHex) }
    Canvas(modifier = modifier.fillMaxSize().onSizeChanged { box = it }) {
        if (canvasWidth <= 0 || canvasHeight <= 0) return@Canvas
        val s = min(size.width / canvasWidth, size.height / canvasHeight)
        val ox = (size.width - canvasWidth * s) / 2f
        val oy = (size.height - canvasHeight * s) / 2f
        val img = image
        if (img != null) {
            drawImage(
                img, dstOffset = androidx.compose.ui.unit.IntOffset(ox.roundToInt(), oy.roundToInt()),
                dstSize = IntSize(max(1, (canvasWidth * s).roundToInt()), max(1, (canvasHeight * s).roundToInt())),
            )
        } else {
            // Hazırlanırken kağıt rengi
            drawRect(paper, topLeft = Offset(ox, oy), size = Size(canvasWidth * s, canvasHeight * s))
        }
    }
}

/** v52 tuval önizlemelerinin bellek önbelleği (cihaz belleğine göre sınırlı; bellek azalınca boşaltılır). */
internal object ThumbnailCache {
    private const val MAX_SIDE = 640
    val synchronous: Boolean = android.os.Build.FINGERPRINT == "robolectric"

    private val cache = object : android.util.LruCache<String, ImageBitmap>(
        com.karapp.drawguidance.platform.DeviceCaps.secondaryCacheBytes(24 * 1024 * 1024),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }.also { c -> com.karapp.drawguidance.platform.DeviceCaps.onTrim { c.evictAll() } }

    fun key(w: Int, h: Int, paper: String, strokes: List<Stroke>, bg: Any?, box: IntSize, images: Int): String =
        "$w x $h|$paper|${System.identityHashCode(strokes)}:${strokes.size}|${bg?.hashCode()}|${box.width}x${box.height}|$images"

    fun get(key: String): ImageBitmap? = synchronized(cache) { cache.get(key) }

    fun render(key: String, w: Int, h: Int, paperHex: String, strokes: List<Stroke>, bg: com.karapp.drawguidance.data.model.BackgroundSpec?, box: IntSize): ImageBitmap {
        val s = min(box.width.toFloat() / w, box.height.toFloat() / h)
        val bw = max(1, min(MAX_SIDE, (w * s).roundToInt()))
        val bh = max(1, min(MAX_SIDE, (h * s).roundToInt()))
        val sx = bw.toFloat() / w
        val sy = bh.toFloat() / h
        val bitmap = ImageBitmap(bw, bh)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bitmap), Size(bw.toFloat(), bh.toFloat())) {
            scale(sx, sy, pivot = Offset.Zero) {
                translate(0f, 0f) {
                    drawCanvasContent(hexToColor(paperHex), strokes, w.toFloat(), h.toFloat(), decor = bg)
                }
            }
        }
        synchronized(cache) { cache.put(key, bitmap) }
        return bitmap
    }
}
