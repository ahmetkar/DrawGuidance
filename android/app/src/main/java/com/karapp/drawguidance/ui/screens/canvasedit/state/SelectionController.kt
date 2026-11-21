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
        lastPicked = element.allIds.firstOrNull()
        return true
    }

    /** v56 2E hizalamada ana nesne: seçimde en son dokunulan eleman. */
    var lastPicked: String? = null
        private set

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
        lastPicked = element.allIds.firstOrNull()
        return true
    }

    /** Seçim modunda çift dokunulan elemanın (grubuyla) seçimini kaldırır. */
    fun deselectAt(x: Float, y: Float, tolerance: Float): Boolean {
        val index = store.currentIndex()
        val element = ElementOps.hitTest(index, Offset(x, y), tolerance, store.lockedIds()) ?: return false
        store.select(store.selection - index.expand(element.allIds))
        return true
    }

    fun clear() { enteredGroup = null; store.select(emptySet()) }

    // ------------------------------------------------------------------ v56 seçim işlemleri

    /** v56 2E grup içi seçim: çift dokunulan grubun içine girilir; içerideyken dokunuş yalnız o elemanı seçer. */
    var enteredGroup: String? = null
        private set

    /** Çift dokunuş gruplu elemandaysa grubun içine girer ve yalnız o elemanı seçer. */
    fun enterGroupAt(x: Float, y: Float, tolerance: Float): Boolean {
        val index = store.currentIndex()
        val element = ElementOps.hitTest(index, Offset(x, y), tolerance, store.lockedIds()) ?: return false
        val group = element.groupId ?: return false
        enteredGroup = group
        store.select(element.allIds)
        return true
    }

    /** Grup içindeyken dokunulan eleman: grubun üyesiyse yalnız o eleman seçime eklenir; değilse gruptan çıkılır. */
    fun selectInGroupAt(x: Float, y: Float, tolerance: Float): Boolean? {
        val group = enteredGroup ?: return null
        val index = store.currentIndex()
        val element = ElementOps.hitTest(index, Offset(x, y), tolerance, store.lockedIds())
        if (element == null || element.groupId != group) { enteredGroup = null; return null }
        store.select(store.selection + element.allIds)
        return true
    }

    /** Sayfadaki bütün görünür ve kilitsiz elemanları seçer. */
    fun selectAll(): Int {
        val index = store.currentIndex()
        val locked = store.lockedIds()
        val ids = index.elements.filter { e -> !e.hidden && e.strokeIds.none { it in locked } }.flatMap { it.allIds }.toSet()
        enteredGroup = null
        store.select(ids)
        return ids.size
    }

    /** Seçimi ters çevirir: seçili olmayan görünür ve kilitsiz elemanlar seçilir. */
    fun invert(): Int {
        val index = store.currentIndex()
        val locked = store.lockedIds()
        val selected = store.selection
        val ids = index.elements.filter { e -> !e.hidden && e.strokeIds.none { it in locked } && e.allIds.none { it in selected } }
            .flatMap { it.allIds }.toSet()
        enteredGroup = null
        store.select(index.expand(ids))
        return ids.size
    }

    /** v56 sihirli değnek: dokunulan elemana [criterion] ölçütüyle benzeyenler seçilir. Seçilen eleman sayısı. */
    fun magicWand(x: Float, y: Float, tolerance: Float, criterion: com.karapp.drawguidance.data.model.V56Selection.WandCriterion, similarity: Float): Int {
        val index = store.currentIndex()
        val locked = store.lockedIds()
        val element = ElementOps.hitTest(index, Offset(x, y), tolerance, locked) ?: return 0
        val target = index.strokes.firstOrNull { it.id in element.strokeIds } ?: return 0
        val candidates = index.strokes.filter { it.id !in locked }
        val found = com.karapp.drawguidance.data.model.V56Selection.similar(target, candidates, criterion, similarity).map { it.id }.toSet()
        enteredGroup = null
        store.select(index.expand(found))
        return found.size
    }

    /** Seçim alanı: son alan seçimi, yoksa seçili elemanların kutusu. */
    fun areaFromSelection(): Boolean {
        val pts = store.lastArea ?: ElementOps.selectionBounds(store.currentIndex().strokes, store.selection)?.let {
            com.karapp.drawguidance.data.model.V56Selection.rect(it.left, it.top, it.right, it.bottom)
        } ?: return false
        if (pts.size < 3) return false
        store.area.value = com.karapp.drawguidance.data.model.ClipMask(pts)
        store.select(emptySet())
        return true
    }

    fun clearArea() { store.area.value = null }

    fun invertArea() { store.area.value = store.area.value?.let { it.copy(inverted = !it.inverted) } }

    /** Kenar yumuşatma (px) sırayla: 0 → 6 → 16 → 32 → 0. */
    fun cycleFeather(): Float {
        val a = store.area.value ?: return 0f
        val next = when { a.feather < 3f -> 6f; a.feather < 11f -> 16f; a.feather < 24f -> 32f; else -> 0f }
        store.area.value = a.copy(feather = next)
        return next
    }

    /** Alanı [d] px genişletir (eksi: daraltır); çok küçülürse değişmez. */
    fun growArea(d: Float): Boolean {
        val a = store.area.value ?: return false
        val grown = com.karapp.drawguidance.data.model.V56Selection.offset(a.points, d) ?: return false
        store.area.value = a.copy(points = grown)
        return true
    }

    /** v56 kement dolgu: [points] (yoksa seçim alanı, o da yoksa son alan seçimi) fırça rengiyle doldurulur. */
    fun lassoFill(points: List<com.karapp.drawguidance.data.model.StrokePoint>?, colorHex: String, opacity: Float): Boolean {
        val area = store.area.value
        val pts = points ?: area?.takeIf { !it.inverted }?.points ?: store.lastArea ?: return false
        if (pts.size < 3) return false
        val current = store.canvas.value ?: return false
        val fill = com.karapp.drawguidance.data.model.V56Selection.lassoFill(UUID.randomUUID().toString(), pts, colorHex, opacity, current.strokes.size)
        store.commit(current.strokes + fill)
        return true
    }

    /** v56 2E kırpma maskesi oluşturulabilir mi (en üstteki seçili nesne sınır olabilir ve altında seçili eleman var). */
    fun canClipMask(): Boolean {
        val ids = store.selection
        return ids.isNotEmpty() && com.karapp.drawguidance.data.model.V56Selection.canClip(store.currentIndex().strokes.filter { it.id in ids && !it.hidden })
    }

    fun hasClipMask(): Boolean {
        val ids = store.selection
        return store.currentIndex().strokes.any { it.id in ids && it.mask?.source != null }
    }

    fun clipMask() {
        val current = store.canvas.value ?: return
        val ids = store.selection
        val changed = com.karapp.drawguidance.data.model.V56Selection.clip(current.strokes.filter { it.id in ids && !it.hidden }, UUID.randomUUID().toString())
        if (changed.isEmpty()) return
        val byId = changed.associateBy { it.id }
        store.commit(current.strokes.map { byId[it.id] ?: it })
        store.select(changed.filter { !it.hidden }.map { it.id }.toSet())
    }

    fun releaseClipMask() {
        val current = store.canvas.value ?: return
        val ids = store.selection
        val updated = com.karapp.drawguidance.data.model.V56Selection.release(current.strokes, ids)
        if (updated !== current.strokes) store.commit(updated)
    }

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
                style2 = com.karapp.drawguidance.data.model.V56Vector.mapStyle(s.style2, ::moved),
                repeat = null,
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
