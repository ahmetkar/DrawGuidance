package com.karapp.drawguidance.ui.screens.canvasedit.components

import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.drawStroke
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.withStrokeLayer
import com.karapp.drawguidance.ui.drawing.defaultSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/**
 * Fırça örnek izlerinin önbelleği. Örnekler (gerçek fırça motoruyla çizilen dalga çizgisi) arka planda
 * bir kez bitmap'e çizilir; fırça seçici açılırken, kayarken ve animasyonda yalnızca hazır bitmap çizilir.
 */
object BrushPreviewCache {
    data class Key(
        val type: BrushType, val color: Int, val paper: Int, val heightPx: Int,
        val opacity: Float, val flow: Float, val strength: Float,
        /** v20 fırça ayarları (aralık, 3B). */
        val params: com.karapp.drawguidance.data.model.BrushParams? = null,
    )

    // Bellek sınırı bayt olarak: fırça seçicinin tüm örnekleri ve yan paneldeki son ayarlar sığar
    private val cache = object : LruCache<Key, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: Key, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val points = (0..40).map { i -> Offset(10f + i * 3f, 24f + sin(i / 40f * 2 * PI).toFloat() * 11f) }

    fun get(key: Key): ImageBitmap? = cache.get(key)

    /** Önizlemeyi çizer (48 birim yüksekliğinde 140 birimlik dalga, [Key.heightPx] yüksekliğe ölçekli). */
    fun render(key: Key): ImageBitmap {
        cache.get(key)?.let { return it }
        val h = key.heightPx.coerceAtLeast(1)
        val s = h / 48f
        val w = ceil(140f * s).toInt().coerceAtLeast(1)
        val bitmap = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, ComposeCanvas(bitmap), Size(w.toFloat(), h.toFloat())) {
            drawRect(Color(key.paper))
            withStrokeLayer(Rect(Offset.Zero, size)) {
                val ink = Color(key.color)
                scale(s, s, pivot = Offset.Zero) {
                    if (key.type == BrushType.ERASER) {
                        // Silginin etkisi görünsün diye önce dolu bir şerit, sonra silgi
                        drawStroke(listOf(Offset(8f, 24f), Offset(132f, 24f)), ink, BrushType.MARKER, 18f, 1f, seed = 1)
                        drawStroke(points, ink, BrushType.ERASER, 6f, key.opacity, seed = 2, flow = key.flow, strength = key.strength)
                    } else {
                        // Her fırça kendi karakterini gösterecek bir boyutta çizilir
                        val size = (key.type.defaultSize() * 0.5f).coerceIn(4f, 14f)
                        drawStroke(points, ink, key.type, size, key.opacity, seed = 3, flow = key.flow, strength = key.strength, params = key.params)
                    }
                }
            }
        }
        cache.put(key, bitmap)
        return bitmap
    }

    /** Fırça seçicideki örnekleri önceden (arka planda) hazırlar. */
    suspend fun prewarm(types: List<BrushType>, color: Color, paper: Color, heightPx: Int) = withContext(Dispatchers.Default) {
        for (type in types) render(Key(type, color.toArgb(), paper.toArgb(), heightPx, 1f, 1f, 1f))
    }
}

/** Fırçanın gerçek çizim motoruyla çizilmiş örnek dalga çizgisi (önbellekten; hazır değilse arka planda çizilir). */
@Composable
fun BrushPreview(
    brushType: BrushType,
    color: Color,
    modifier: Modifier = Modifier,
    paper: Color = Color.White,
    opacity: Float = 1f,
    flow: Float = 1f,
    strength: Float = 1f,
    params: com.karapp.drawguidance.data.model.BrushParams? = null,
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    val key = BrushPreviewCache.Key(brushType, color.toArgb(), paper.toArgb(), box.height, opacity, flow, strength, params)
    // Önbellekte hazırsa aynı karede çizilir; değilse arka planda üretilir (bu arada bir önceki örnek kalır)
    val cached = if (box.height > 0) BrushPreviewCache.get(key) else null
    val produced by produceState<ImageBitmap?>(initialValue = null, key) {
        if (key.heightPx <= 0 || BrushPreviewCache.get(key) != null) return@produceState
        value = withContext(Dispatchers.Default) { BrushPreviewCache.render(key) }
    }
    val image = cached ?: produced
    Canvas(modifier.onSizeChanged { box = it }) {
        drawRect(paper)
        image?.let {
            // Dar kartlarda örnek sığacak şekilde küçültülür (eski ölçekleme: min(genişlik/140, yükseklik/48))
            val f = (size.width / it.width).coerceAtMost(1f)
            if (f < 1f) scale(f, f, pivot = Offset.Zero) { drawImage(it) } else drawImage(it)
        }
    }
}
