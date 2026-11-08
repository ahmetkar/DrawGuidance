package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import java.util.UUID

/** Seçim (dokunarak, panelden, 2 sn basılı tutarak) ve seçili elemanlar üzerindeki işlemler. */
class SelectionController(private val store: EditorStore, private val host: EditorHost) {

    /** Dokunulan elemanı (grubuyla) seçer, modu değiştirmez; eleman yoksa false. */
    fun pickAt(x: Float, y: Float, tolerance: Float): Boolean {
        val index = store.currentIndex()
        val element = ElementOps.hitTest(index, Offset(x, y), tolerance, store.lockedIds()) ?: return false
        store.select(index.expand(element.allIds))
        return true
    }

    /** Elemana uzun (2 sn) basılınca: Seç moduna geçer ve yalnızca o elemanı (grubuyla) seçer. */
    fun selectOnlyAt(x: Float, y: Float, tolerance: Float): Boolean {
        if (!pickAt(x, y, tolerance)) return false
        host.setMode(EditMode.SELECT)
        return true
    }

    /**
     * v40 elemana 5 sn basılı tutulunca: yalnızca o eleman (grubuyla) seçilir ve Taşı moduna geçilir (eylem olarak;
     * Bitti ile önceki moda dönülür). Eleman yoksa false.
     */
    fun holdMoveAt(x: Float, y: Float, tolerance: Float): Boolean {
        if (!pickAt(x, y, tolerance)) return false
        if (host.mode.value != EditMode.MOVE) host.startAction(EditMode.MOVE)
        return true
    }

    fun toggleElement(elementId: String) {
        val index = store.currentIndex()
        val element = index.element(elementId) ?: return
        val targets = index.expand(element.allIds)
        val selected = store.selection
        store.select(if (selected.containsAll(targets)) selected - targets else index.expand(selected + targets))
    }

    fun toggleGroup(groupId: String) {
        val first = store.currentIndex().elements.firstOrNull { it.groupId == groupId } ?: return
        toggleElement(first.id)
    }

    /**
     * v43 alanla seçim (dikdörtgen ya da kalemle çizilen kapalı çizgi; çizim yapılmaz): kutusunun ortası alanın içinde
     * kalan görünür ve kilitsiz elemanlar (gruplarıyla) seçilir. [add] ise seçime eklenir. Seçilen eleman sayısı.
     */
    fun selectInArea(add: Boolean, contains: (Offset) -> Boolean): Int {
        val index = store.currentIndex()
        val locked = store.lockedIds()
        val found = LinkedHashSet<String>()
        var n = 0
        for (e in index.elements) {
            if (e.hidden || e.strokeIds.any { it in locked }) continue
            val b = ElementOps.selectionBounds(index.strokes, e.strokeIds.toSet()) ?: continue
            if (contains(b.center)) { found += e.allIds; n++ }
        }
        store.select(index.expand(if (add) store.selection + found else found))
        return n
    }

    /** Seçim modunda dokunulan noktadaki elemanı (grubuyla) seçime ekler. */
    fun selectAt(x: Float, y: Float, tolerance: Float): Boolean {
        val index = store.currentIndex()
        val element = ElementOps.hitTest(index, Offset(x, y), tolerance, store.lockedIds()) ?: return false
        store.select(index.expand(store.selection + element.allIds))
        return true
    }

    /** Seçim modunda çift dokunulan elemanın (grubuyla) seçimini kaldırır. */
    fun deselectAt(x: Float, y: Float, tolerance: Float): Boolean {
        val index = store.currentIndex()
        val element = ElementOps.hitTest(index, Offset(x, y), tolerance, store.lockedIds()) ?: return false
        store.select(store.selection - index.expand(element.allIds))
        return true
    }

    fun clear() = store.select(emptySet())

    /**
     * v23 iki parmakla uzun basma: altındaki eleman seçili değilse yalnız o (grubuyla) seçilir, seçiliyse seçim
     * korunur; boş yerde seçim değişmez (normal bağlam menüsü: seçim varsa seçimin, yoksa Ekle menüsü). Altında
     * eleman varsa true.
     */
    fun selectForContext(x: Float, y: Float, tolerance: Float): Boolean {
        val index = store.currentIndex()
        val element = ElementOps.hitTest(index, Offset(x, y), tolerance, store.lockedIds()) ?: return false
        val ids = index.expand(element.allIds)
        if (!store.selection.containsAll(ids)) store.select(ids)
        return true
    }

    /** v23 seçili elemanları bir üste/alta ya da en üste/en alta taşır ([ZOrder]). */
    fun reorder(direction: Int) {
        val current = store.canvas.value ?: return
        if (store.selection.isEmpty()) return
        val updated = com.karapp.drawguidance.ui.screens.canvasedit.editor.ZOrder.reorder(
            current.strokes, store.selection, store.currentIndex(), direction, current.layers,
        )
        if (updated !== current.strokes) store.commit(updated)
    }

    /**
     * v30 gizleme. [elementIds] verilen elemanları (yalnız onları; grubun diğer üyeleri değil) gizler; gizlenenler
     * seçimden çıkar. Gizlenen eleman sayısı döner. Tek geri alma adımıdır.
     */
    fun hide(elementIds: Collection<String>): Int {
        val current = store.canvas.value ?: return 0
        val index = store.currentIndex()
        val targets = elementIds.mapNotNull { index.element(it) }.filter { !it.hidden }
        if (targets.isEmpty()) return 0
        val ids = targets.flatMapTo(HashSet()) { it.allIds }
        val updated = ElementOps.setHidden(current.strokes, ids, true)
        if (updated === current.strokes) return 0
        store.commit(updated)
        store.select(store.selection - ids)
        return targets.size
    }

    /** v30 seçili elemanların hepsini gizler. */
    fun hideSelected(): Int = hide(store.currentIndex().selectedElements(store.selection).map { it.id })

    /** v30 gizlenmiş elemanları yeniden gösterir ([elementIds] null: hepsi). Gösterilen eleman sayısı döner. */
    fun show(elementIds: Collection<String>? = null): Int {
        val current = store.canvas.value ?: return 0
        val index = store.currentIndex()
        val targets = index.elements.filter { it.hidden && (elementIds == null || it.id in elementIds) }
        if (targets.isEmpty()) return 0
        val ids = targets.flatMapTo(HashSet()) { it.allIds }
        val updated = ElementOps.setHidden(current.strokes, ids, false)
        if (updated === current.strokes) return 0
        store.commit(updated)
        return targets.size
    }

    fun group() = store.mutateSelection { strokes, ids -> ElementOps.group(strokes, ids, UUID.randomUUID().toString(), store.currentIndex()) }

    fun ungroup() = store.mutateSelection { strokes, ids -> ElementOps.ungroup(strokes, ids, store.currentIndex()) }

    fun delete() {
        store.mutateSelection { strokes, ids -> ElementOps.delete(strokes, ids) }
        clear()
    }

    /**
     * Seçili elemanların kopyası biraz kaydırılarak eklenir (gruplar kendi aralarında korunur); kopyalar seçili
     * olur.
     */
    fun duplicate() {
        val current = store.canvas.value ?: return
        val ids = store.selection
        if (ids.isEmpty()) return
        val chosen = current.strokes.filter { it.id in ids }
        if (chosen.isEmpty()) return
        val shift = maxOf(16f, minOf(current.width, current.height) * 0.03f)
        val groups = HashMap<String, String>()
        val q = com.karapp.drawguidance.data.local.ContentCodec::quantize
        fun moved(p: com.karapp.drawguidance.data.model.StrokePoint) = p.at(q(p.x + shift), q(p.y + shift))
        val copies = chosen.mapIndexed { i, s ->
            s.copy(
                // Kopya aslının katmanında kalır (etkin katmana taşınmaz)
                layerId = com.karapp.drawguidance.data.model.Layers.of(s, current.layers),
                id = UUID.randomUUID().toString(),
                groupId = s.groupId?.let { g -> groups.getOrPut(g) { UUID.randomUUID().toString() } },
                order = current.strokes.size + i,
                points = s.points.map(::moved),
                clip = s.clip?.map(::moved),
            )
        }
        store.commit(current.strokes + copies)
        store.select(copies.mapTo(HashSet()) { it.id })
    }

    fun move(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        store.mutateSelection { strokes, ids -> ElementOps.translate(strokes, ids, dx, dy) }
    }

    /** [pivot] verilmezse seçimin merkezi etrafında döndürür. */
    fun rotate(degrees: Float, pivot: Offset? = null) {
        if (degrees == 0f) return
        store.mutateSelection { strokes, ids ->
            val center = pivot ?: ElementOps.selectionBounds(strokes, ids)?.center ?: return@mutateSelection strokes
            ElementOps.rotate(strokes, ids, degrees, center)
        }
    }

    /** [pivot] verilmezse seçimin merkezi sabit kalacak şekilde ölçekler. */
    fun scale(sx: Float, sy: Float, pivot: Offset? = null) {
        store.mutateSelection { strokes, ids ->
            val center = pivot ?: ElementOps.selectionBounds(strokes, ids)?.center ?: return@mutateSelection strokes
            ElementOps.scale(strokes, ids, sx, sy, center)
        }
    }
}
