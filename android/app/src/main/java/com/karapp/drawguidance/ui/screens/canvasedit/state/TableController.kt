package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.TableShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * v52 not almada tablo: "Tablo ekle" penceresi (satır/sütun sayısı, başlık, görünüm) ve seçili tablonun düzenleme
 * penceresi (hücre yazıları; her satır, her sütun ve hepsi için ayarlar). Tablo tek şekil elemanıdır, bütün ayarları
 * kendi değerlerinde ([TableShape]).
 */
class TableController(private val store: EditorStore, private val host: EditorHost) {
    private val _createOpen = MutableStateFlow(false)
    val createOpen: StateFlow<Boolean> = _createOpen.asStateFlow()
    private val _editing = MutableStateFlow<String?>(null)
    /** Düzenleme penceresi açık tablo (null: kapalı). */
    val editing: StateFlow<String?> = _editing.asStateFlow()

    fun openCreate() { _createOpen.value = true }
    fun closeCreate() { _createOpen.value = false }

    /** Yeni tablo sayfanın ortasına eklenir ve seçilir. */
    fun create(params: Map<String, String>, ink: String = "#263238"): String? {
        val current = store.canvas.value ?: return null
        val w = current.width.toFloat(); val h = current.height.toFloat()
        val nr = TableShape.rows(params); val nc = TableShape.cols(params)
        val colW = min(w * 0.86f / nc, w * 0.24f)
        var rowH = (w * 0.06f).coerceIn(36f, 120f)
        if (rowH * nr > h * 0.8f) rowH = h * 0.8f / nr
        val tw = colW * nc; val th = rowH * nr
        val left = (w - tw) / 2f; val top = max(h * 0.08f, (h - th) / 2f - h * 0.1f)
        val id = UUID.randomUUID().toString()
        val style = ShapeStyle(strokeColorHex = ink, strokeWidth = max(2f, w / 600f))
        val s = ShapeFactory.build(TableShape.KIND, Offset(left, top), Offset(left + tw, top + th), style, null, id, current.strokes.size, params = params) ?: return null
        store.commit(current.strokes + s)
        _createOpen.value = false
        host.inserted(setOf(id))
        return id
    }

    fun edit(id: String) { _editing.value = id }
    fun closeEdit() { _editing.value = null }

    /** Düzenlenen tablo. */
    fun table(): Stroke? = _editing.value?.let { id -> store.canvas.value?.strokes?.firstOrNull { it.id == id && TableShape.isTable(it.shape?.kind) } }

    fun params(): Map<String, String> = table()?.shape?.params.orEmpty()

    /**
     * Değerleri değiştirip tabloyu yeniden kurar. Satır/sütun eklenince ya da payı değişince kutu da büyür/küçülür
     * (hücreler aynı boyda kalır). [coalesce] aynı alana yazarken geri alma adımlarını birleştirir.
     */
    fun update(coalesce: String? = null, block: (Map<String, String>) -> Map<String, String>) {
        val current = store.canvas.value ?: return
        val s = table() ?: return
        val spec = s.shape ?: return
        val old = spec.params.orEmpty()
        val next = block(old)
        if (next == old) return
        val box = ShapeFactory.box(s) ?: return
        val sx = TableShape.weightSum(next, false) / TableShape.weightSum(old, false).coerceAtLeast(0.01f)
        val sy = TableShape.weightSum(next, true) / TableShape.weightSum(old, true).coerceAtLeast(0.01f)
        val nb = Rect(box.left, box.top, box.left + box.width * sx, box.top + box.height * sy)
        val built = ShapeFactory.build(TableShape.KIND, nb.topLeft, nb.bottomRight, spec.style, null, s.id, s.order, s.opacity, next) ?: return
        val rebuilt = built.copy(groupId = s.groupId, clip = s.clip, layerId = s.layerId)
        store.commit(current.strokes.map { if (it.id == s.id) rebuilt else it }, coalesce = coalesce ?: "table-${s.id}-${System.nanoTime()}")
    }

    /** Silinen ya da geri alınan tablonun penceresi kapanır. */
    fun onContentReplaced() { if (_editing.value != null && table() == null) _editing.value = null }
}
