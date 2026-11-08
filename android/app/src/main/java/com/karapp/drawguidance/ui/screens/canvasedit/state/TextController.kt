package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.TextRenderer
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.TextDraft
import com.karapp.drawguidance.ui.screens.canvasedit.TextSettings
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Yazı elemanı: tuvalin içinde yazma (yeni yazı ya da mevcut yazıyı düzenleme) ve yazı tipi ayarları.
 * Yazılan her değişiklik saklanır: başka yere dokunulunca, mod ya da sayfa değişince yazı kaybolmaz.
 */
class TextController(
    private val store: EditorStore,
    private val brush: () -> BrushController,
    /** v37 eni verilmemiş yazı tuvalin sağ kenarına varınca alt satıra geçsin mi (not tuvali). */
    private val autoWrap: () -> Boolean = { false },
    /** v23 yeni yazı eklendi (not tuvalinde Taşı moduna geçilir). */
    private val onAdded: (String) -> Unit = {},
) {
    /**
     * v29 kullanıcının son seçtiği yazı biçimi (yeni yazıların öntanımlısı, tuvalde saklanır). Yalnız kullanıcının
     * değiştirdiği alanlarla güncellenir ([updateSettings]); düzenlenen ya da eklenen özel biçimli yazıların (balon,
     * efekt yazısı) biçimi buraya geçmez.
     */
    private val _defaults = MutableStateFlow(TextSettings())
    val defaults: StateFlow<TextSettings> = _defaults.asStateFlow()

    /** Gösterilen biçim: düzenlenen yazınınki, yoksa [defaults]. */
    private val _settings = MutableStateFlow(TextSettings())
    val settings: StateFlow<TextSettings> = _settings.asStateFlow()

    private val _draft = MutableStateFlow<TextDraft?>(null)
    val draft: StateFlow<TextDraft?> = _draft.asStateFlow()

    /** Açık yazının anlık içeriği (yazdıkça güncellenir). */
    private var draftText = ""

    private fun setDefaults(value: TextSettings) {
        _defaults.value = value
        if (_draft.value?.editingId == null) _settings.value = value
    }

    /** Tuval açılınca yazı boyutu tuvalin boyutuna göre ayarlanır. */
    fun adaptTo(width: Int, height: Int) {
        setDefaults(_defaults.value.copy(sizePx = (minOf(width, height) / 22f).coerceIn(24f, 120f).let { kotlin.math.round(it) }))
    }

    /** Bu tuvalde son seçilen yazı biçimi (yazı tipi, kalın/eğik, hizalama…) ve boyutu geri yüklenir. */
    fun restore(spec: com.karapp.drawguidance.data.model.TextSpec, sizePx: Float? = null) {
        setDefaults(
            TextSettings(
                normalize(spec.copy(content = "")),
                sizePx?.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE) ?: _defaults.value.sizePx,
            ),
        )
    }

    fun updateDraftText(text: String) {
        draftText = text
    }

    /** Açık yazı varsa kaydeder (boşsa yeni yazı eklenmez, düzenlenen yazı silinir). */
    fun commitOpenDraft() {
        if (_draft.value != null) confirm(draftText)
    }

    fun onModeChange(to: EditMode) {
        // Yazı modundan çıkarken açık yazı kaydedilir
        if (to != EditMode.TEXT && _draft.value != null) commitOpenDraft()
    }

    /** Geri alma sonrası: artık olmayan yazının düzenlemesi kapanır. */
    fun onContentReplaced() {
        val id = _draft.value?.editingId ?: return
        if (store.strokes.none { it.id == id }) _draft.value = null
    }

    /** Yazı modunda dokunulan yer: bir yazıya dokunulduysa onu düzenler, yoksa oraya yeni yazı açar. */
    fun beginAt(x: Float, y: Float, tolerance: Float) {
        // Yazarken tuvalde başka yere dokunmak yazıyı bitirir (yazılan kaybolmaz)
        if (_draft.value != null) {
            commitOpenDraft()
            return
        }
        val current = store.canvas.value ?: return
        val hit = current.strokes.lastOrNull {
            it.brushType == BrushType.TEXT && ElementOps.distanceToShape(Offset(x, y), it) <= tolerance
        }
        if (hit != null) return edit(hit)
        draftText = ""
        _draft.value = TextDraft(Offset(x, y), null, "")
    }

    /**
     * v37 Yazı modunda sürüklenerek çizilen kutu: yeni yazı kutunun sol üstünden, kutunun eninde (paragraf; satırlar
     * kutuda alt satıra geçer) yazılır. Çok kısa sürükleme dokunma sayılır.
     */
    fun beginBox(from: Offset, to: Offset, tolerance: Float) {
        if (_draft.value != null) {
            commitOpenDraft()
            return
        }
        val size = _settings.value.sizePx
        val width = kotlin.math.abs(to.x - from.x)
        if (width < size * 2f && kotlin.math.abs(to.y - from.y) < size) return beginAt(from.x, from.y, tolerance)
        draftText = ""
        _draft.value = TextDraft(
            Offset(minOf(from.x, to.x), minOf(from.y, to.y) + size * 0.7f), null, "",
            boxWidth = width.coerceIn(size * 3f, MAX_BOX),
        )
    }

    /** v37 açık yazının kutusu taşındı: yeni yazının yeri ya da düzenlenen yazı (yazılan korunur). */
    fun moveDraft(dx: Float, dy: Float) {
        val draft = _draft.value ?: return
        if (dx == 0f && dy == 0f) return
        draft.editingId?.let { id ->
            val current = store.canvas.value ?: return
            if (current.strokes.none { it.id == id }) return
            store.commit(ElementOps.translate(current.strokes, setOf(id), dx, dy))
        }
        _draft.value = draft.copy(anchor = draft.anchor + Offset(dx, dy), content = draftText)
    }

    /**
     * v37 açık yazının kutu türü: [paragraph] ise eni sabit kutu (öntanımlı en: yazının yerinden sağ kenara), değilse
     * kutu yazıya göre (kısa yazı, başlık).
     */
    fun setDraftParagraph(paragraph: Boolean) {
        val draft = _draft.value ?: return
        val id = draft.editingId
        if (id == null) {
            val width = if (paragraph) draft.boxWidth ?: paragraphWidth(draft.anchor.x) else null
            if (width != draft.boxWidth) _draft.value = draft.copy(boxWidth = width, content = draftText)
            return
        }
        val current = store.canvas.value ?: return
        val old = current.strokes.firstOrNull { it.id == id } ?: return
        val text = old.text ?: return
        val width = if (paragraph) text.box?.width ?: paragraphWidth(old.points[0].x) else null
        if (width == text.box?.width) return
        val spec = text.copy(box = (text.box ?: com.karapp.drawguidance.data.model.TextBox()).copy(width = width).orNull())
        val q = ContentCodec::quantize
        val updated = old.copy(text = spec, points = TextRenderer.refit(old, spec, old.sizePx).map { StrokePoint(q(it.x), q(it.y)) })
        store.commit(current.strokes.map { if (it.id == id) updated else it }, coalesce = "text-box-$id")
        _draft.value = draft.copy(boxWidth = width, content = draftText)
    }

    private fun edgeMargin(canvasWidth: Float) = canvasWidth * 0.04f

    /** Yazının yerinden ([left]) tuvalin sağ kenarına (kenar boşluğu kadar içeride) kalan en. */
    private fun paragraphWidth(left: Float): Float {
        val w = store.canvas.value?.width?.toFloat() ?: return MIN_BOX * 20f
        val size = _settings.value.sizePx
        return maxOf(size * 4f, minOf(w - edgeMargin(w) - left, w * 0.95f))
    }

    /**
     * v37 not tuvalinde eni verilmemiş yazı (kısa yazı, başlık) tuvalin sağ kenarını geçecekse kenarda alt satıra
     * geçer: kutuya o kadar en verilir. [stretch] yazının en ölçeği (düzenlenen, ölçeklenmiş yazı).
     */
    private fun wrapped(spec: com.karapp.drawguidance.data.model.TextSpec, sizePx: Float, left: Float, stretch: Float = 1f): com.karapp.drawguidance.data.model.TextSpec {
        if (!autoWrap() || spec.box?.width != null || spec.box?.balloon != null || spec.content.isEmpty()) return spec
        val canvasWidth = store.canvas.value?.width?.toFloat() ?: return spec
        val room = (canvasWidth - edgeMargin(canvasWidth) - left) / stretch.coerceAtLeast(1e-3f)
        if (room < sizePx * 3f) return spec
        if (TextRenderer.measure(spec, sizePx).first <= room) return spec
        return spec.copy(box = (spec.box ?: com.karapp.drawguidance.data.model.TextBox()).copy(width = room))
    }

    /** Düzenlenen yazıda [wrapped]: yalnız döndürülmemiş yazıda (sol kenarı ve en ölçeğiyle). */
    private fun wrappedEdit(old: Stroke, spec: com.karapp.drawguidance.data.model.TextSpec, sizePx: Float): com.karapp.drawguidance.data.model.TextSpec {
        val q = old.points
        val oldSpec = old.text ?: return spec
        if (q.size < 4 || kotlin.math.abs(q[1].y - q[0].y) > 0.5f || q[1].x <= q[0].x) return spec
        val stretch = (q[1].x - q[0].x) / TextRenderer.measure(oldSpec, old.sizePx).first.coerceAtLeast(1e-3f)
        return wrapped(spec, sizePx, q[0].x, stretch)
    }

    /** Yeni yazının biçimi: ortak biçim + açık kutunun eni; not tuvalinde kenarda alt satıra geçer. */
    private fun newSpec(draft: TextDraft, settings: TextSettings, content: String): com.karapp.drawguidance.data.model.TextSpec {
        val base = settings.spec.copy(content = content)
        val spec = draft.boxWidth?.let { w -> base.copy(box = (base.box ?: com.karapp.drawguidance.data.model.TextBox()).copy(width = w)) } ?: base
        return wrapped(spec, settings.sizePx, draft.anchor.x)
    }

    /** Seçili (tek) yazıyı düzenler. */
    fun editSelected() {
        val current = store.canvas.value ?: return
        val text = current.strokes.firstOrNull { it.id in store.selection && it.brushType == BrushType.TEXT } ?: return
        edit(text)
    }

    private fun edit(stroke: Stroke) {
        val spec = stroke.text ?: return
        // v27 balon her yazının kendisinde. v29 düzenlenen yazının biçimi yalnız gösterilir; son ayar ([defaults]) olmaz
        _settings.value = TextSettings(normalize(spec), stroke.sizePx)
        draftText = spec.content
        _draft.value = TextDraft(Offset(stroke.points[0].x, stroke.points[0].y), stroke.id, spec.content, boxWidth = spec.box?.width)
    }

    /** v29 düzenlenen yazının harf rengi (açık düzenleme yoksa false). */
    fun setColor(colorHex: String): Boolean {
        val id = _draft.value?.editingId ?: return false
        val current = store.canvas.value ?: return false
        val hex = colorHex.uppercase()
        var changed = false
        val updated = current.strokes.map {
            if (it.id != id || it.colorHex.equals(hex, ignoreCase = true)) it else { changed = true; it.copy(colorHex = hex) }
        }
        if (changed) store.commit(updated, coalesce = "text-fill-$id")
        return true
    }

    fun cancel() {
        _draft.value = null
        _settings.value = _defaults.value
    }

    /**
     * v28 yazarken önizleme: onaylanınca tuvale konacak yazının şimdiki hali (biçimi, kutusu, balonu, efektleri,
     * ölçek ve yönü ile, tuvaldeki yerinde). Düzenleme alanı bunu çizer; yazı yerinden oynamaz, biçimi kaybolmaz.
     */
    fun previewStroke(content: String): Stroke? {
        val draft = _draft.value ?: return null
        val current = store.canvas.value ?: return null
        val settings = _settings.value
        if (draft.editingId != null) {
            val old = current.strokes.firstOrNull { it.id == draft.editingId } ?: return null
            val spec = wrappedEdit(old, settings.spec.copy(content = content, box = mergeBox(settings.spec.box, old.text?.box ?: settings.spec.box)), settings.sizePx)
            return old.copy(text = spec, sizePx = settings.sizePx, points = TextRenderer.refit(old, spec, settings.sizePx))
        }
        val spec = newSpec(draft, settings, content)
        return Stroke(
            id = DRAFT_ID, points = TextRenderer.quadAt(spec, settings.sizePx, draft.anchor.x, draft.anchor.y - settings.sizePx * 0.7f),
            colorHex = brush().inkColor(), brushType = BrushType.TEXT, sizePx = settings.sizePx, opacity = 1f, order = 0, text = spec,
        )
    }

    /** Yazıyı ekler/günceller (düzenlenen yazı boşaltılırsa silinir); eklenen yazı seçili olur. */
    fun confirm(content: String) {
        val draft = _draft.value ?: return
        _draft.value = null
        val settings = _settings.value
        _settings.value = _defaults.value
        val current = store.canvas.value ?: return
        val q = ContentCodec::quantize
        if (draft.editingId != null) {
            val old = current.strokes.firstOrNull { it.id == draft.editingId } ?: return
            if (content.isBlank()) {
                store.commit(ElementOps.delete(current.strokes, setOf(old.id)))
                return
            }
            // v24 ayarlar yazarken değiştiyse (ortak görünüm) yazının kendi kutu eni/boyu korunur
            val spec = wrappedEdit(old, settings.spec.copy(content = content, box = mergeBox(settings.spec.box, old.text?.box ?: settings.spec.box)), settings.sizePx)
            val updated = old.copy(text = spec, sizePx = settings.sizePx, points = TextRenderer.refit(old, spec, settings.sizePx).map { StrokePoint(q(it.x), q(it.y)) })
            store.commit(current.strokes.map { if (it.id == old.id) updated else it })
            return
        }
        if (content.isBlank()) return
        val spec = newSpec(draft, settings, content)
        val points = TextRenderer.quadAt(spec, settings.sizePx, draft.anchor.x, draft.anchor.y - settings.sizePx * 0.7f)
            .map { StrokePoint(q(it.x), q(it.y)) }
        val stroke = Stroke(
            id = UUID.randomUUID().toString(),
            points = points,
            colorHex = brush().inkColor(),
            brushType = BrushType.TEXT,
            sizePx = settings.sizePx,
            opacity = 1f,
            order = current.strokes.size,
            text = spec,
        )
        store.commit(current.strokes + stroke)
        // Eklenen yazı seçili olur (şekil ve diyagram gibi): biçimi hemen verilebilir
        store.select(setOf(stroke.id))
        onAdded(stroke.id)
    }

    /**
     * Yazı ayarları: kullanıcı [before] gösterilen biçimi [value] yaptı. v29 yalnız değişen alanlar uygulanır
     * ([TextStyleMerge]): yeni yazıların öntanımlısına ([defaults]), düzenlenen yazıya ve seçili yazılara. Kutunun eni,
     * boyu ve balonu her yazının kendisinde kalır ([setBoxSize]).
     */
    fun updateSettings(value: TextSettings, before: TextSettings = _settings.value) {
        val after = TextSettings(normalize(value.spec), value.sizePx.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE))
        val base = TextSettings(normalize(before.spec), before.sizePx.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE))
        if (after == base) return
        _defaults.value = TextStyleMerge.merge(_defaults.value, base, after)
        val draft = _draft.value
        _settings.value = if (draft?.editingId != null) TextStyleMerge.merge(_settings.value, base, after) else _defaults.value
        if (draft != null) return
        val current = store.canvas.value ?: return
        val ids = store.selection
        if (ids.isEmpty()) return
        val q = ContentCodec::quantize
        var changed = false
        val updated = current.strokes.map { s ->
            val text = s.text
            if (s.id !in ids || s.brushType != BrushType.TEXT || text == null) return@map s
            val merged = TextStyleMerge.merge(TextSettings(text, s.sizePx), base, after)
            // Paragraf biçimi değişince liste işaretleri de yazıya uygulanır
            val content = if (merged.spec.paragraph != text.paragraph) {
                com.karapp.drawguidance.data.text.TextLists.apply(text.content, merged.spec.paragraph)
            } else text.content
            val spec = merged.spec.copy(content = content)
            if (spec == text && s.sizePx == merged.sizePx) return@map s
            changed = true
            s.copy(text = spec, sizePx = merged.sizePx, points = TextRenderer.refit(s, spec, merged.sizePx).map { StrokePoint(q(it.x), q(it.y)) })
        }
        if (changed) store.commit(updated, coalesce = "text-style-" + store.selectionKey())
    }

    /**
     * v23 seçili yazıların kutu eni/boyu: [width]/[height] null ise o yön otomatik (yazıya göre); [setWidth]/[setHeight]
     * false olan yön değişmez. Kutunun sol üst köşesi ve yönü korunur.
     */
    fun setBoxSize(width: Float?, height: Float?, setWidth: Boolean, setHeight: Boolean, coalesce: Boolean = true) {
        val current = store.canvas.value ?: return
        val ids = store.selection
        if (ids.isEmpty()) return
        val q = ContentCodec::quantize
        var changed = false
        val updated = current.strokes.map { s ->
            val text = s.text
            if (s.id !in ids || s.brushType != BrushType.TEXT || text == null) return@map s
            val old = text.box ?: com.karapp.drawguidance.data.model.TextBox()
            val box = old.copy(
                width = if (setWidth) width?.coerceIn(MIN_BOX, MAX_BOX) else old.width,
                height = if (setHeight) height?.coerceIn(MIN_BOX, MAX_BOX) else old.height,
            ).orNull()
            if (box == text.box) return@map s
            changed = true
            val spec = text.copy(box = box)
            s.copy(text = spec, points = TextRenderer.refit(s, spec, s.sizePx).map { StrokePoint(q(it.x), q(it.y)) })
        }
        if (changed) store.commit(updated, coalesce = if (coalesce) "text-box-" + store.selectionKey() else null)
    }

    companion object {
        /** Ortak biçim: içeriksiz; kutunun eni, boyu ve balonu yazının kendisinde. */
        fun normalize(spec: com.karapp.drawguidance.data.model.TextSpec): com.karapp.drawguidance.data.model.TextSpec =
            spec.copy(content = "", box = spec.box?.copy(width = null, height = null, balloon = null)?.orNull())

        /** Ortak kutu görünümü + yazının kendi eni/boyu. */
        fun mergeBox(style: com.karapp.drawguidance.data.model.TextBox?, own: com.karapp.drawguidance.data.model.TextBox?): com.karapp.drawguidance.data.model.TextBox? =
            (style ?: com.karapp.drawguidance.data.model.TextBox()).copy(width = own?.width, height = own?.height, balloon = own?.balloon).orNull()

        /** Yazarken önizlemenin geçici kimliği. */
        const val DRAFT_ID = "__text_draft__"
        const val MIN_BOX = 24f
        const val MAX_BOX = 20000f
        const val MIN_TEXT_SIZE = 8f
        const val MAX_TEXT_SIZE = 400f
    }
}
