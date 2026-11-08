package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import com.karapp.drawguidance.content.trace.EdgeOptions
import com.karapp.drawguidance.content.trace.EdgeTracer
import com.karapp.drawguidance.data.images.CanvasImageCache
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapePath
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max

/**
 * Eklenen resmin düzenlenmesi: kırpma (eleman kırpması), vektörleştirme (v17: kenar çizgileri — dolgu değil,
 * düzenlenebilir çizgiler ve vektör noktaları) ve kalem aracı (resmin üstünden yol çizme). Resim düzenlenirken yalnızca
 * o resim seçilidir.
 */
class ImageEditController(
    private val store: EditorStore,
    private val host: EditorHost,
) {
    /** Seçili tek resim. */
    val selected: StateFlow<Stroke?> = combine(store.canvas, store.selectedIds) { canvas, ids ->
        if (ids.size != 1) null else canvas?.strokes?.firstOrNull { it.id in ids && it.brushType == BrushType.IMAGE }
    }.stateIn(host.scope, SharingStarted.Eagerly, null)

    private val _tracing = MutableStateFlow(false)
    val tracing: StateFlow<Boolean> = _tracing.asStateFlow()
    /** Son vektörleştirmede hiç çizgi bulunamadı. */
    private val _empty = MutableStateFlow(false)
    val empty: StateFlow<Boolean> = _empty.asStateFlow()
    private var job: Job? = null

    fun editSelected() {
        val image = store.selectedStroke { it.brushType == BrushType.IMAGE }?.takeIf { store.selection.size == 1 } ?: return
        host.startAction(EditMode.IMAGE_EDIT, setOf(image.id))
    }

    /** Resmi kırpar (eleman kırpma çerçevesi). */
    fun crop() {
        if (selected.value == null) return
        host.startAction(EditMode.CROP)
    }

    fun cancelTrace() {
        job?.cancel()
        job = null
        _tracing.value = false
    }

    /**
     * Resmin kenarlarını düzenlenebilir çizgilere çevirir (dolgusuz; her çizginin vektör noktaları köşeler ve eğrinin
     * dönüm yerleri). [lineWidth] tuval birimi çizgi kalınlığı. [keepImage] false ise resmin yerine geçer (geri
     * alınabilir), true ise resmin üstüne eklenir. Sonuç seçili olarak Taşı'ya geçer.
     */
    fun vectorize(options: EdgeOptions, lineWidth: Float, keepImage: Boolean) {
        val image = selected.value ?: return
        val bitmap = CanvasImageCache.get(image.assetId)?.asAndroidBitmap() ?: return
        if (image.points.size < 4) return
        cancelTrace()
        _tracing.value = true
        _empty.value = false
        job = host.scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val source = com.karapp.drawguidance.platform.Compat.readable(bitmap)
                    val scale = minOf(1f, 1280f / max(source.width, source.height))
                    val small = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true) else source
                    val pixels = IntArray(small.width * small.height)
                    small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
                    EdgeTracer.trace(pixels, small.width, small.height, options) { !isActive }
                }
                val current = store.canvas.value ?: return@launch
                val p0 = image.points[0]; val p1 = image.points[1]; val p3 = image.points[3]
                fun map(u: Float, v: Float) = Offset(p0.x + (p1.x - p0.x) * u + (p3.x - p0.x) * v, p0.y + (p1.y - p0.y) * u + (p3.y - p0.y) * v)
                val paths = result.paths.map { ep ->
                    val pts = ep.points.map { map(it.x, it.y) }
                    ShapePath(
                        if (ep.closed && pts.size > 2) pts + pts.first() else pts, closed = ep.closed,
                        strokeColorHex = ep.color.takeIf { options.colored },
                        nodes = ep.nodes,
                    )
                }
                if (paths.isEmpty()) { _empty.value = true; return@launch }
                val style = ShapeStyle(strokeColorHex = "#1E1E1E", strokeWidth = lineWidth.coerceIn(0.3f, 20f))
                val shape = ShapeFactory.fromPaths(ShapeRegistry.TRACED, paths, style, null, UUID.randomUUID().toString(), image.order)
                    ?.copy(layerId = image.layerId ?: "", groupId = null, clip = image.clip) ?: return@launch
                val updated = if (keepImage) {
                    val list = current.strokes.toMutableList()
                    val i = list.indexOfFirst { it.id == image.id }
                    list.add(i + 1, shape)
                    list
                } else current.strokes.map { if (it.id == image.id) shape else it }
                store.commit(updated.mapIndexed { i, s -> if (s.order == i) s else s.copy(order = i) })
                // Vektörleştirilen şekil yeni eleman gibi seçili olarak Taşı'ya geçer
                host.inserted(setOf(shape.id))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.karapp.drawguidance.diagnostics.AppLog.e("Vectorize", "Resim vektörleştirilemedi", e, userVisible = true)
            } finally {
                _tracing.value = false
            }
        }
    }
}
