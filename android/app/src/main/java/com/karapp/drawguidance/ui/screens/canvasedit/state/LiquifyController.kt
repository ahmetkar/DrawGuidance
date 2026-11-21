package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.ImageWarp
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.WarpMesh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v56 aşama 6 Sıvılaştır (planın 4. bölümü): resme çevrilmiş katman ya da seçim üzerinde ağ deformasyonu (17×17 ağ,
 * [ImageWarp.mesh]); it, döndür, büzüştür, şişir. Fırça izleri önizlenir; "Bitti" tek geri alma adımı, "İptal" belgeyi
 * değiştirmez. Asıl resim değişmez (ağ sonradan yeniden düzenlenebilir).
 */
class LiquifyController(private val store: EditorStore) {
    private val _target = MutableStateFlow<String?>(null)
    /** Sıvılaştırılan resim elemanının kimliği (null: kapalı). */
    val target: StateFlow<String?> = _target.asStateFlow()

    private val _mode = MutableStateFlow(WarpMesh.Liquify.PUSH)
    val mode: StateFlow<WarpMesh.Liquify> = _mode.asStateFlow()
    private val _radius = MutableStateFlow(80f)
    val radius: StateFlow<Float> = _radius.asStateFlow()
    private val _strength = MutableStateFlow(0.6f)
    val strength: StateFlow<Float> = _strength.asStateFlow()

    private var original: List<Stroke> = emptyList()
    private var mesh: List<StrokePoint> = emptyList()

    val isOpen: Boolean get() = _target.value != null

    fun setMode(m: WarpMesh.Liquify) { _mode.value = m }
    fun setRadius(r: Float) { _radius.value = r.coerceIn(10f, 400f) }
    fun setStrength(s: Float) { _strength.value = s.coerceIn(0.05f, 1f) }

    /** [imageId] resim elemanında başlar (ağ yoksa resmin dörtgeninde düz ağ). */
    fun begin(imageId: String): Boolean {
        val c = store.canvas.value ?: return false
        val img = c.strokes.firstOrNull { it.id == imageId && it.brushType == BrushType.IMAGE && it.points.size >= 4 } ?: return false
        original = c.strokes
        val old = img.warp?.mesh
        val n = old?.let { WarpMesh.sizeOf(it) } ?: 0
        mesh = when {
            n == WarpMesh.LIQUIFY -> old!!
            n > 0 -> WarpMesh.resample(old!!, n, WarpMesh.LIQUIFY)
            else -> WarpMesh.onQuad(img.points.take(4), WarpMesh.LIQUIFY)
        }
        _target.value = imageId
        return true
    }

    private fun applied(): List<Stroke> {
        val id = _target.value ?: return original
        val n = WarpMesh.LIQUIFY
        val corners = listOf(mesh[0], mesh[n - 1], mesh[n * n - 1], mesh[n * (n - 1)])
        return original.map { s ->
            if (s.id != id) s else s.copy(points = corners + s.points.drop(4), warp = (s.warp ?: ImageWarp()).copy(mesh = mesh))
        }
    }

    /** Fırça hareketi: ([cx], [cy]) noktasında ([dx], [dy]) kadar (tuval). */
    fun brush(cx: Float, cy: Float, dx: Float, dy: Float) {
        if (_target.value == null) return
        mesh = WarpMesh.liquify(mesh, _mode.value, cx, cy, dx, dy, _radius.value, _strength.value)
        store.previewStrokes(applied())
    }

    fun done() {
        if (_target.value == null) return
        val result = applied()
        store.endPreview()
        if (result != original) store.commit(result, useArea = false)
        close()
    }

    fun cancel() {
        if (_target.value == null) return
        store.endPreview()
        close()
    }

    private fun close() { _target.value = null; original = emptyList(); mesh = emptyList() }
}
