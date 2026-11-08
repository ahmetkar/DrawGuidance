package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.data.model.GraphSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps

/**
 * Seçili elemanların biçimi: boya kutusu ve Renk Ver (elemanın dolgu rengi), yazının dolgusu ve kenarlığı,
 * şekil/diyagram biçimi, grafik ayarları ve tuvale göre hizalama.
 */
class StyleController(
    private val store: EditorStore,
    private val frame: FrameController,
    /** v24 kağıt rengi yalnız açık sayfaya mı (not tuvalinde birden çok sayfa). */
    private val perPage: () -> Boolean = { false },
) {

    // --- Yazı dolgusu ve kenarlığı ---

    /** Seçili yazıların dolgusu (harflerin iç rengi). */
    fun setTextFill(colorHex: String) {
        val current = store.canvas.value ?: return
        val ids = store.selection
        val hex = colorHex.uppercase()
        var changed = false
        val updated = current.strokes.map {
            if (it.id !in ids || it.text == null || it.colorHex.equals(hex, ignoreCase = true)) it
            else { changed = true; it.copy(colorHex = hex) }
        }
        if (changed) store.commit(updated, coalesce = "text-fill-" + store.selectionKey())
    }

    /** Seçili yazıların kenarlığı (harflerin çevresindeki çizgi); renk null ya da kalınlık 0 ise kenarlık kalkar. */
    fun setTextOutline(colorHex: String?, width: Float) {
        val current = store.canvas.value ?: return
        val ids = store.selection
        val color = colorHex?.uppercase()?.takeIf { width > 0f }
        val w = if (color == null) 0f else width.coerceIn(0f, 64f)
        var changed = false
        val updated = current.strokes.map {
            val spec = it.text
            if (it.id !in ids || spec == null || (spec.outlineColorHex == color && spec.outlineWidth == w)) it
            else { changed = true; it.copy(text = spec.copy(outlineColorHex = color, outlineWidth = w)) }
        }
        if (changed) store.commit(updated, coalesce = "text-outline-" + store.selectionKey())
    }

    // --- Boya kutusu ve Renk Ver: elemanın dolgu rengi ---

    /** Boya kutusunun hedefi: dolgu alabilen seçili elemanlar; boşsa tuvalin arka planı boyanır. */
    fun fillTargetIds(): Set<String> {
        val current = store.canvas.value ?: return emptySet()
        val ids = store.selection
        if (ids.isEmpty()) return emptySet()
        return current.strokes.filter { it.id in ids && ElementOps.canFill(it) }.mapTo(HashSet()) { it.id }
    }

    /** Boya kutusu (ve Renk Ver) penceresinin başlangıç rengi: seçili elemanın dolgusu ya da tuvalin arka planı. */
    fun currentFillColor(): String {
        val current = store.canvas.value ?: return "#FFFFFF"
        val targets = fillTargetIds()
        if (targets.isEmpty()) return current.pagePaperHex
        val first = current.strokes.first { it.id in targets }
        return ElementOps.fillColorOf(first) ?: "#FFFFFF"
    }

    /**
     * Boya kutusu: seçili elemanların dolgu rengini (Elemanlar panelindeki "Dolgu rengi" ile aynı) ya da
     * seçim yoksa tuvalin arka planını boyar. Renk seçicide sürükleme tek geri alma adımıdır.
     */
    fun applyFill(colorHex: String) {
        val current = store.canvas.value ?: return
        val hex = colorHex.uppercase()
        val targets = fillTargetIds()
        if (targets.isEmpty()) {
            frame.setBackgroundColor(hex, coalesce = "fill-background", pageOnly = perPage())
            return
        }
        // v44 kalemle çizilmiş kapalı şekil: çizginin rengi değil, içi boyanır
        val ink = current.strokes.filter { it.id in targets && com.karapp.drawguidance.ui.screens.canvasedit.editor.InkFill.isClosed(it) }.mapTo(HashSet()) { it.id }
        val updated = com.karapp.drawguidance.ui.screens.canvasedit.editor.InkFill.apply(ElementOps.fill(current.strokes, targets - ink, hex), ink, hex)
        if (updated.size != current.strokes.size || updated.indices.any { updated[it] !== current.strokes[it] }) store.commit(updated, coalesce = "fill-" + targets.sorted().joinToString())
    }

    /** v27 boya kutusunun gradyanı: seçili elemanınki ya da (seçim yoksa) kağıdınki. */
    fun currentFillGradient(): com.karapp.drawguidance.data.model.GradientSpec? {
        val current = store.canvas.value ?: return null
        val targets = fillTargetIds()
        if (targets.isEmpty()) return current.pageBackground.gradient
        return current.strokes.firstOrNull { it.id in targets }?.let { ElementOps.fillGradientOf(it) }
    }

    /** v27 seçili elemanlara gradyan dolgu (seçim yoksa [onBackground] kağıda uygular). */
    fun applyFillGradient(gradient: com.karapp.drawguidance.data.model.GradientSpec?, onBackground: (com.karapp.drawguidance.data.model.GradientSpec?) -> Unit) {
        val current = store.canvas.value ?: return
        val targets = fillTargetIds()
        if (targets.isEmpty()) { onBackground(gradient); return }
        val updated = ElementOps.fillGradient(current.strokes, targets, gradient)
        if (updated.indices.any { updated[it] !== current.strokes[it] }) store.commit(updated, coalesce = "gradient-" + targets.sorted().joinToString())
    }

    /** Renk Ver (Elemanlar paneli): verilen renk seçili elemanların dolgu rengi olur. */
    fun fillSelected(colorHex: String) {
        val color = colorHex.uppercase()
        store.mutateSelection { strokes, ids ->
            val ink = strokes.filter { it.id in ids && com.karapp.drawguidance.ui.screens.canvasedit.editor.InkFill.isClosed(it) }.mapTo(HashSet()) { it.id }
            com.karapp.drawguidance.ui.screens.canvasedit.editor.InkFill.apply(ElementOps.fill(strokes, ids - ink, color), ink, color)
        }
        store.endCoalesce()
    }

    /** Seçili elemanlardaki tüm çizimlere [colorHex] rengini verir. */
    fun recolorSelected(colorHex: String) {
        val color = colorHex.uppercase()
        store.mutateSelection { strokes, ids -> ElementOps.recolor(strokes, ids, color) }
    }

    // --- Şekil/diyagram biçimi ve grafik ---

    /** Seçili şekil/diyagramların biçimini değiştirir (kenar, dolgu, radius). */
    fun applyStyleToSelection(style: ShapeStyle) {
        val current = store.canvas.value ?: return
        val ids = store.selection
        if (ids.isEmpty()) return
        var changed = false
        val updated = current.strokes.map { stroke ->
            if (stroke.id !in ids) return@map stroke
            val shape = stroke.shape
            val diagram = stroke.diagram
            when {
                shape != null -> {
                    if (shape.style == style) stroke else { changed = true; stroke.copy(shape = shape.copy(style = style), colorHex = style.strokeColorHex) }
                }
                diagram != null -> {
                    if (diagram.style == style) stroke else { changed = true; stroke.copy(diagram = diagram.copy(style = style), colorHex = style.strokeColorHex) }
                }
                else -> stroke
            }
        }
        if (changed) store.commit(updated, coalesce = "shape-style-" + store.selectionKey())
    }

    /** Seçili grafiğin eksen etiketlerini ve formülünü değiştirir (şekil yeniden kurulur). */
    fun applyGraphToSelection(graph: GraphSpec) {
        val current = store.canvas.value ?: return
        val ids = store.selection
        if (ids.isEmpty()) return
        var changed = false
        val updated = current.strokes.map { stroke ->
            val spec = stroke.shape
            if (stroke.id !in ids || spec?.graph == null) return@map stroke
            // Formül değiştiyse hazır eğri bırakılır: grafik yazılan formülle yeniden çizilir
            val next = if (graph.formula.trim() != spec.graph!!.formula.trim() && graph.formula.isNotBlank()) graph.copy(preset = "") else graph
            val rebuilt = ShapeFactory.rebuild(stroke, spec.style, next) ?: return@map stroke
            changed = true
            rebuilt
        }
        if (changed) store.commit(updated, coalesce = "graph-spec-" + store.selectionKey())
    }

    /** Seçili elemanları tuvalin soluna / ortasına / sağına hizalar. */
    fun alignSelection(align: TextAlignKind) {
        val current = store.canvas.value ?: return
        val ids = store.selection
        if (ids.isEmpty()) return
        val bounds = ElementOps.selectionBounds(current.strokes, ids) ?: return
        val target = when (align) {
            TextAlignKind.LEFT -> ALIGN_MARGIN
            TextAlignKind.CENTER -> (current.width - bounds.width) / 2f
            TextAlignKind.RIGHT -> current.width - bounds.width - ALIGN_MARGIN
        }
        val dx = target - bounds.left
        if (kotlin.math.abs(dx) < 0.01f) return
        store.commit(ElementOps.translate(current.strokes, ids, dx, 0f))
    }

    private companion object {
        const val ALIGN_MARGIN = 24f
    }
}
