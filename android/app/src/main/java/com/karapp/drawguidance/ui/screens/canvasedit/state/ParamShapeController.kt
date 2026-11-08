package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.content.library.science.ParamApply
import com.karapp.drawguidance.content.library.science.ParamError
import com.karapp.drawguidance.content.library.science.ParamModel
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v45 parametreli şekillerin (fizik, geometri, molekül…) düzenlenmesi: Düzenle'de açılan değer/formül penceresinin
 * işleri ve şekil üstündeki tutamaklar. Değerler elemanın kendi kaydında (ShapeSpec.params) saklanır; her değişiklik
 * şekli bulunduğu kutuda yeniden kurar (köşedeki formül yazısı da güncellenir). Hatalı formül uygulanmaz, son geçerli
 * çizim kalır, hata [error] ile gösterilir.
 */
class ParamShapeController(
    private val store: EditorStore,
    private val selected: () -> Stroke?,
) {
    private val _error = MutableStateFlow<ParamError?>(null)
    /** Son formül uygulamasının hatası (yoksa null). */
    val error: StateFlow<ParamError?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    /** Seçili şeklin modeli (parametreli değilse null). */
    fun model(stroke: Stroke? = selected()): ParamModel? = stroke?.shape?.kind?.let { ShapeRegistry.paramModel(it) }

    /** Seçili şeklin kayıttaki değerleri. */
    fun raw(stroke: Stroke? = selected()): Map<String, String> = stroke?.shape?.params.orEmpty()

    /** Formül alanına yazılanı uygular; başarılıysa true. */
    fun applyFormula(text: String): Boolean {
        val stroke = selected() ?: return false
        val model = model(stroke) ?: return false
        return when (val r = model.apply(raw(stroke), text)) {
            is ParamApply.Ok -> { _error.value = null; commit(stroke, r.params); true }
            is ParamApply.Failed -> { _error.value = r.error; false }
        }
    }

    /** Tek sayısal değer (alan ya da adım düğmesi); birlikte geçersizse uygulanmaz. */
    fun setValue(key: String, value: Double): Boolean {
        val stroke = selected() ?: return false
        val model = model(stroke) ?: return false
        val next = model.setValue(raw(stroke), key, value) ?: return false
        _error.value = null
        commit(stroke, next)
        return true
    }

    /** Metin değeri (ör. molekül görünümü). */
    fun setText(key: String, text: String) {
        val stroke = selected() ?: return
        val model = model(stroke) ?: return
        commit(stroke, model.withText(raw(stroke), key, text))
    }

    private fun rebuilt(stroke: Stroke, params: Map<String, String>): Stroke? {
        val spec = stroke.shape ?: return null
        return ShapeFactory.rebuild(stroke, spec.style, spec.graph, params)
    }

    private fun commit(stroke: Stroke, params: Map<String, String>) {
        val current = store.canvas.value ?: return
        if (params == stroke.shape?.params.orEmpty()) return
        val next = rebuilt(stroke, params) ?: return
        store.commit(current.strokes.map { if (it.id == stroke.id) next.copy(order = it.order) else it }, coalesce = "params-${stroke.id}")
        store.select(setOf(stroke.id))
    }

    // --- Tutamaklar ---

    /** Tutamakların tuvaldeki yerleri (anahtar → nokta). */
    fun handles(stroke: Stroke? = selected()): List<Pair<String, Offset>> {
        val s = stroke ?: return emptyList()
        val model = model(s) ?: return emptyList()
        val box = ShapeFactory.box(s) ?: return emptyList()
        return model.unitHandles(raw(s)).map { h -> h.key to Offset(box.left + h.x * box.width, box.top + h.y * box.height) }
    }

    private var dragKey: String? = null
    private var dragBox: Rect? = null
    private var dragRaw: Map<String, String> = emptyMap()

    /** Sürükleme bir tutamaktan mı başlıyor? */
    fun beginDrag(point: Offset, tolerance: Float): Boolean {
        dragKey = null
        val stroke = selected() ?: return false
        val hit = handles(stroke).minByOrNull { (it.second - point).getDistance() } ?: return false
        if ((hit.second - point).getDistance() > tolerance * 2.2f) return false
        dragKey = hit.first
        dragBox = ShapeFactory.box(stroke)
        dragRaw = raw(stroke)
        return true
    }

    /** Sürüklemenin vardığı noktadaki değerler (değişmezse null); hep başlangıç değerlerine ve kutusuna göre. */
    private fun dragged(to: Offset): Map<String, String>? {
        val key = dragKey ?: return null
        val box = dragBox ?: return null
        val model = model() ?: return null
        val ux = (to.x - box.left) / box.width.coerceAtLeast(1e-3f)
        val uy = (to.y - box.top) / box.height.coerceAtLeast(1e-3f)
        return model.dragUnit(dragRaw, key, ux, uy)
    }

    fun dragPreview(to: Offset): Stroke? {
        val stroke = selected() ?: return null
        val next = dragged(to) ?: return null
        return rebuilt(stroke, next)
    }

    fun endDrag(to: Offset) {
        val stroke = selected()
        val next = dragged(to)
        dragKey = null
        if (stroke != null && next != null) { _error.value = null; commit(stroke, next) }
    }
}
