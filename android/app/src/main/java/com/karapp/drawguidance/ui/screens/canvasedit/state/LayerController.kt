package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.Layers
import java.util.UUID

/**
 * Katmanlar: ekleme (yeni katman en üste eklenir ve etkin olur; eski katmanlar etkilenmez), silme (içindekilerle),
 * yeniden adlandırma, sıra değiştirme, gizleme ve kilitleme, seçili elemanları başka katmana taşıma. Yeni elemanlar
 * etkin katmana eklenir ([EditorStore.commit]). Her işlem geri alınabilir.
 */
class LayerController(private val store: EditorStore) {

    val active get() = store.activeLayer

    private fun layers(): List<Layer> = store.canvas.value?.layers ?: listOf(Layer.Default)

    fun setActive(id: String) {
        val layer = layers().firstOrNull { it.id == id } ?: return
        store.activeLayer.value = layer.id
    }

    /** Yeni katman en üste eklenir ve etkin olur. */
    fun add(): String {
        val current = layers()
        val names = current.map { it.name }.toSet()
        var n = current.size + 1
        while (com.karapp.drawguidance.i18n.I18n.trf("Katman {0}", n) in names) n++
        val layer = Layer(UUID.randomUUID().toString(), com.karapp.drawguidance.i18n.I18n.trf("Katman {0}", n))
        store.commitLayers(current + layer)
        store.activeLayer.value = layer.id
        return layer.id
    }

    /** Katmanı içindeki elemanlarla siler (en az bir katman kalır). */
    fun remove(id: String) {
        val current = layers()
        if (current.size <= 1) return
        val canvas = store.canvas.value ?: return
        val pages = canvas.pages.map { page -> page.filterNot { Layers.of(it, current) == id } }
        val remaining = current.filterNot { it.id == id }
        // İlk katman silinirse yeni ilk katmanın elemanları kimliksiz (ilk katman) sayılır: kimlikler korunur
        store.commitLayers(remaining, pages)
        if (store.activeLayer.value == id) store.activeLayer.value = remaining.last().id
        store.select(emptySet())
    }

    fun rename(id: String, name: String) {
        if (name.isBlank()) return
        store.commitLayers(layers().map { if (it.id == id) it.copy(name = name.trim()) else it }, coalesce = "layer-name-$id")
    }

    fun toggleVisible(id: String) {
        store.commitLayers(layers().map { if (it.id == id) it.copy(visible = !it.visible) else it })
        store.select(store.selection)
    }

    fun toggleLocked(id: String) {
        store.commitLayers(layers().map { if (it.id == id) it.copy(locked = !it.locked) else it })
        store.select(store.selection)
    }

    /** v56 katman opaklığı (0..1); kaydırırken tek geri alma adımı. */
    fun setOpacity(id: String, opacity: Float) {
        val v = opacity.coerceIn(0f, 1f)
        store.commitLayers(layers().map { if (it.id == id) it.copy(opacity = v) else it }, coalesce = "layer-opacity-$id")
    }

    /** v56 katman karışım modu. */
    fun setBlend(id: String, blend: com.karapp.drawguidance.data.model.LayerBlend) {
        store.commitLayers(layers().map { if (it.id == id) it.copy(blend = blend) else it })
    }

    /** v56 alfa kilidi: açıkken bu katmana çizilen fırça izleri yalnız katmanın boyalı yerlerinde görünür. */
    fun toggleAlphaLock(id: String) {
        store.commitLayers(layers().map { if (it.id == id) it.copy(alphaLock = !it.alphaLock) else it })
    }

    /** v56 alttakine kırp (en alttaki katmanda etkisiz). */
    fun toggleClip(id: String) {
        store.commitLayers(layers().map { if (it.id == id) it.copy(clipToBelow = !it.clipToBelow) else it })
    }

    /** Katmanı bir üste (+1) ya da alta (-1) taşır; çizimler yeni sıraya göre dizilir. */
    fun move(id: String, direction: Int) {
        val current = layers().toMutableList()
        val i = current.indexOfFirst { it.id == id }
        val j = i + direction
        if (i < 0 || j !in current.indices) return
        val canvas = store.canvas.value ?: return
        val firstBefore = current.first().id
        val layer = current.removeAt(i)
        current.add(j, layer)
        // İlk katman değişirse "kimliksiz" (ilk katman) elemanlar eski ilk katmanın kimliğini açıkça alır
        val pages = if (current.first().id != firstBefore) {
            canvas.pages.map { page -> page.map { s -> if (s.layerId == null || s.layerId == "") s.copy(layerId = firstBefore) else s } }
        } else canvas.pages
        store.commitLayers(current, pages)
    }

    /** Seçili elemanları [id] katmanına taşır. */
    fun moveSelectionTo(id: String) {
        val ids = store.selection
        if (ids.isEmpty()) return
        val canvas = store.canvas.value ?: return
        val target = layers().firstOrNull { it.id == id } ?: return
        if (target.locked || !target.visible) return
        val first = layers().first().id
        val pages = canvas.pages.mapIndexed { index, page ->
            if (index != canvas.currentPage) page
            else page.map { s -> if (s.id in ids) s.copy(layerId = if (id == first) null else id) else s }
        }
        store.commitLayers(layers(), pages)
        store.select(ids)
    }

    /** v56 aşama 7: katmanı çoğalt (hemen üstte "<ad> (kopya)", bütün sayfalardaki elemanlar yeni kimlikle); yeni katman etkin olur. */
    fun duplicate(id: String): String? {
        val canvas = store.canvas.value ?: return null
        val layer = layers().firstOrNull { it.id == id } ?: return null
        val copyId = UUID.randomUUID().toString()
        val (outLayers, pages) = com.karapp.drawguidance.data.model.LayerOps.duplicate(
            layers(), canvas.pages, id, copyId, layer.name + com.karapp.drawguidance.i18n.I18n.tr(" (kopya)"),
        ) { UUID.randomUUID().toString() }
        store.commitLayers(outLayers, pages)
        store.activeLayer.value = copyId
        store.select(emptySet())
        return copyId
    }

    /** v56 aşama 7: katmanları vektör olarak birleştirir (en üstteki seçili katmanın yerinde; tek geri alma adımı). */
    fun mergeVector(ids: Set<String>): Boolean {
        val canvas = store.canvas.value ?: return false
        val current = layers()
        if (current.count { it.id in ids } < 2) return false
        val target = current.last { it.id in ids }
        val (outLayers, pages) = com.karapp.drawguidance.data.model.LayerOps.mergeVector(current, canvas.pages, ids)
        store.commitLayers(outLayers, pages)
        store.activeLayer.value = target.id
        store.select(emptySet())
        return true
    }

    /**
     * v56 aşama 7: birleştirmenin resimli hali. [images] sayfa başına seçili katmanların resme çevrilmiş hali (yoksa
     * null); seçili katmanların elemanları kalkar, resim hedef katmana girer, hedef katman Normal ve opaklık 1 olur.
     */
    fun mergeRaster(ids: Set<String>, images: List<com.karapp.drawguidance.data.model.Stroke?>): Boolean {
        val canvas = store.canvas.value ?: return false
        val current = layers()
        if (current.count { it.id in ids } < 2) return false
        val target = current.last { it.id in ids }
        val outLayers = current.filter { it.id !in ids || it.id == target.id }
            .map { if (it.id == target.id) it.copy(opacity = 1f, blend = com.karapp.drawguidance.data.model.LayerBlend.NORMAL, alphaLock = false, clipToBelow = false) else it }
        val pages = canvas.pages.mapIndexed { i, page ->
            val kept = page.filterNot { Layers.of(it, current) in ids }
            val img = images.getOrNull(i)
            if (img == null) kept else kept + img.copy(layerId = target.id, order = kept.size)
        }
        store.commitLayers(outLayers, pages)
        store.activeLayer.value = target.id
        store.select(emptySet())
        return true
    }
}
