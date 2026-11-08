package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.content.formula.Formula
import com.karapp.drawguidance.content.formula.FormulaTemplate
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.FormulaSpec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.FormulaRenderer
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/**
 * Formüller: katalogdan şablon seçip tuvale yerleştirme (dokunarak ya da sürükleyerek; eklenince Taşı modu) ve
 * düzenleme — bilinmeyenler/katsayılar (yuvalar) ya da kaynak formülün üstünde yazılır (v16: pencere açılmaz),
 * formül kutusu yeniden ölçülür.
 * Formül yazıdır (resim değil): renk, boyut ve dönüşümler yazı gibi çalışır.
 */
class FormulaController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val brush: () -> BrushController,
) {
    private val _isPickerOpen = MutableStateFlow(false)
    val isPickerOpen: StateFlow<Boolean> = _isPickerOpen.asStateFlow()

    private val _template = MutableStateFlow<FormulaTemplate?>(null)
    val template: StateFlow<FormulaTemplate?> = _template.asStateFlow()

    /** Düzenleme penceresi açık olan formül (null: kapalı) ve odaktaki yuva. */
    private val _editing = MutableStateFlow<String?>(null)
    val editing: StateFlow<String?> = _editing.asStateFlow()
    private val _activeSlot = MutableStateFlow<String?>(null)
    val activeSlot: StateFlow<String?> = _activeSlot.asStateFlow()

    /** Seçili tek formül. */
    val selected: StateFlow<Stroke?> = combine(store.canvas, store.selectedIds) { canvas, ids ->
        if (ids.size != 1) null else canvas?.strokes?.firstOrNull { it.id in ids && it.brushType == BrushType.FORMULA }
    }.stateIn(host.scope, SharingStarted.Eagerly, null)

    /** v45 formül sayılarla hesaplanamadıysa (sıfıra bölme, tanım dışı) hata; yoksa null. */
    private val _evalError = MutableStateFlow<com.karapp.drawguidance.content.math.MathError?>(null)
    val evalError: StateFlow<com.karapp.drawguidance.content.math.MathError?> = _evalError.asStateFlow()

    /** Yeni formüllerin yazı boyutu (tuval boyutuna göre). */
    private var defaultSize = 56f

    fun adaptTo(width: Int, height: Int) {
        defaultSize = (minOf(width, height) / 20f).coerceIn(28f, 140f)
    }

    fun openPicker() { _isPickerOpen.value = true }
    fun closePicker() { _isPickerOpen.value = false }

    /** Şablon seçilince Formül moduna geçilir: tuvale dokunulan yere (ya da sürüklenen kutuya) eklenir. */
    fun selectTemplate(value: FormulaTemplate) {
        _template.value = value
        _isPickerOpen.value = false
        host.startAction(EditMode.FORMULA)
    }

    /** Sürüklenen kutuya sığan boyutta ekler; kısa sürüklemede dokunulan yere varsayılan boyutta. */
    fun add(gestureId: String, start: Offset, end: Offset) {
        val t = _template.value ?: return
        val layout = FormulaRenderer.quadAt(t.source, 100f, 0f, 0f)
        val w100 = layout[1].x - layout[0].x
        val h100 = layout[3].y - layout[0].y
        val dx = abs(end.x - start.x)
        val dy = abs(end.y - start.y)
        val size = if (dx < 24f && dy < 24f) defaultSize else max(12f, minOf(dx / w100, if (dy < 24f) Float.MAX_VALUE else dy / h100) * 100f)
        val left = if (dx < 24f && dy < 24f) start.x - w100 * size / 200f else minOf(start.x, end.x)
        val top = if (dx < 24f && dy < 24f) start.y - h100 * size / 200f else minOf(start.y, end.y)
        insert(gestureId, t, size, left, top)
    }

    fun addAt(gestureId: String, center: Offset) = add(gestureId, center, center)

    private fun insert(gestureId: String, t: FormulaTemplate, size: Float, left: Float, top: Float) {
        val current = store.canvas.value ?: return
        val q = ContentCodec::quantize
        val quad = FormulaRenderer.quadAt(t.source, size, left, top).map { StrokePoint(q(it.x), q(it.y)) }
        val id = UUID.nameUUIDFromBytes("$gestureId-formula".toByteArray()).toString()
        val stroke = Stroke(
            id = id, points = quad, colorHex = brush().inkColor(), brushType = BrushType.FORMULA, sizePx = size,
            // v17: formül elemanlarına ayrılır — her sayı ve harf ayrı düzenlenir
            opacity = 1f, order = current.strokes.size, formula = FormulaSpec(Formula.atomize(t.source), t.id),
        )
        store.commit(current.strokes + stroke)
        host.inserted(setOf(id))
    }

    /**
     * v16: seçili formülü formülün üstünde düzenlemeye açar: [slot] verilirse o yuva (katsayı/bilinmeyen), verilmezse
     * ilk yuva; yuvası olmayan formülde formülün tamamı. Pencere açılmaz, yazı alanı formülün üstünde durur.
     */
    fun edit(slot: String? = null) {
        val f = ensureAtomized() ?: return
        _editing.value = f.id
        _activeSlot.value = slot ?: Formula.slots(f.formula?.source ?: "").firstOrNull()?.name
    }

    /**
     * Eski (v17 öncesi) formül elemanlarına ayrılmamışsa ayrılır: her sayı/harf ayrı yuva olur. Güncel çizim döner
     * (seçim akışı gecikmeli güncellendiği için tuvalden okunur).
     */
    private fun ensureAtomized(): Stroke? {
        val f = store.selectedStroke { it.brushType == BrushType.FORMULA }?.takeIf { store.selection.size == 1 } ?: return null
        val src = f.formula?.source ?: return f
        if (Formula.isAtomized(src)) return f
        update(f.id) { Formula.atomize(it) }
        return store.canvas.value?.strokes?.firstOrNull { it.id == f.id } ?: f
    }

    /** Formülün tamamını (kaynağını) formülün altında açılan alanda yazmaya geçer. */
    fun editSource() {
        val f = store.selectedStroke { it.brushType == BrushType.FORMULA }?.takeIf { store.selection.size == 1 } ?: return
        _editing.value = f.id
        _activeSlot.value = null
    }

    /** Sonraki yuvaya geçer (sondaysa düzenleme biter). */
    fun nextSlot() {
        val f = selected.value ?: return closeEditor()
        val slots = Formula.slots(f.formula?.source ?: "").map { it.name }
        val i = slots.indexOf(_activeSlot.value)
        if (i < 0 || i + 1 >= slots.size) closeEditor() else _activeSlot.value = slots[i + 1]
    }

    fun closeEditor() {
        if (_editing.value == null) return
        _editing.value = null
        _activeSlot.value = null
        store.endCoalesce()
    }

    fun focusSlot(name: String?) { _activeSlot.value = name }

    /**
     * Dokunulan noktada seçili formülün bir yuvası varsa o yuva formülün üstünde düzenlenir; değilse açık düzenleme
     * kapanır (dokunuş normal işine devam eder).
     */
    fun tapSlot(point: Offset): Boolean {
        val f = if (selected.value?.let { FormulaRenderer.slotAt(it, point) } != null) ensureAtomized() else selected.value
        val slot = f?.let { FormulaRenderer.slotAt(it, point) }
        if (slot == null) {
            closeEditor()
            return false
        }
        edit(slot)
        return true
    }

    /** Formül yalnızca Seç ve Taşı modlarında düzenlenir; başka moda geçilince düzenleme kapanır. */
    fun onModeChange(to: EditMode) {
        if (to != EditMode.SELECT && to != EditMode.MOVE) closeEditor()
    }

    /** Yuvanın değerini değiştirir (yazdıkça; tek geri alma adımında birleşir). */
    fun setSlot(name: String, value: String) {
        val id = _editing.value ?: return
        update(id) { Formula.setSlot(it, name, value) }
    }

    /** Kaynağı doğrudan değiştirir: yazılan formül elemanlarına ayrılarak kaydedilir. */
    fun setSource(source: String) {
        val id = _editing.value ?: return
        update(id) { Formula.atomize(source) }
    }

    private fun update(id: String, block: (String) -> String) {
        val current = store.canvas.value ?: return
        val q = ContentCodec::quantize
        var changed = false
        val updated = current.strokes.map { s ->
            val spec = s.formula
            if (s.id != id || spec == null) return@map s
            // v45 bilinmeyenlerin yerine sayı yazılınca sonuç ve doğru bağıntı eklenir ("2·4+3 = 11", "3² < 10")
            val eval = com.karapp.drawguidance.content.formula.FormulaEval.annotate(
                block(spec.source), com.karapp.drawguidance.i18n.I18n.tr("tanımsız"),
                decimalComma = com.karapp.drawguidance.i18n.I18n.language in DECIMAL_COMMA,
            )
            _evalError.value = eval.error
            val next = eval.source
            if (next == spec.source) return@map s
            changed = true
            val points = FormulaRenderer.refit(s, next).map { StrokePoint(q(it.x), q(it.y)) }
            s.copy(formula = spec.copy(source = next), points = points)
        }
        if (changed) store.commit(updated, coalesce = "formula-$id")
    }

    private companion object {
        /** Ondalık ayracı virgül olan diller. */
        val DECIMAL_COMMA = setOf("tr", "ru")
    }

    fun onContentReplaced() {
        val id = _editing.value ?: return
        if (store.strokes.none { it.id == id }) closeEditor()
    }

    /** Seçim değişti: düzenlenen formül artık seçili değilse düzenleme kapanır. */
    fun onSelectionChanged(ids: Set<String>) {
        val id = _editing.value ?: return
        if (id !in ids) closeEditor()
    }
}
