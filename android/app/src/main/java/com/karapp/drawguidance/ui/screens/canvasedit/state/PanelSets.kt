package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.content.comic.PanelPage
import com.karapp.drawguidance.content.comic.PanelShape
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.PanelRef
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapePath
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import java.util.UUID
import kotlin.math.min

/**
 * v36 sayfadaki çizgi roman şablonu: panel elemanları ([BrushType.SHAPE], tür [KIND]) aynı set kimliğini ve düzeni
 * ([PanelRef]) taşır. Düzen değişince (boyut, sıra, aralık, ekleme, silme) panellerin köşeleri yeniden hesaplanır;
 * her panel kendi kimliğini, biçimini (renk, kalınlık, dolgu), katmanını ve yerini (çizim sırası) korur.
 */
object PanelSets {
    const val KIND = "comic.panel"

    /** Sayfadaki şablon: [page] düzen, [strokes] panel kimliği → panel elemanı. */
    class Found(val set: String, val page: PanelPage, val strokes: Map<Int, Stroke>) {
        fun strokeIds(): Set<String> = strokes.values.mapTo(HashSet()) { it.id }
    }

    fun isPanel(s: Stroke): Boolean = s.shape?.panel != null

    /**
     * Sayfadaki (en son eklenen) şablon. Silinmiş panellerin kimlikleri düzenden de çıkar; kopyalanan paneller
     * (aynı kimlik ikinci kez) şablona sayılmaz.
     */
    fun find(strokes: List<Stroke>): Found? {
        val last = strokes.lastOrNull { isPanel(it) } ?: return null
        val ref = last.shape!!.panel!!
        val members = LinkedHashMap<Int, Stroke>()
        var layout: PanelPage? = null
        for (s in strokes) {
            val r = s.shape?.panel ?: continue
            if (r.set != ref.set || r.leaf in members) continue
            members[r.leaf] = s
            if (layout == null) layout = PanelPage.decode(r.layout)
        }
        var page = layout ?: return null
        // Elle silinen paneller düzenden de çıkar (komşuları yer kaplamaz; düzen yeniden kurulunca dolar)
        for (id in page.leafIds()) if (id !in members) page = page.remove(id)
        return Found(ref.set, page, members)
    }

    /** Panel elemanları (yeni şablon): her panel ayrı eleman; iç paneller beyaz dolgulu. */
    fun build(page: PanelPage, set: String, w: Float, h: Float, style: ShapeStyle, order: Int): List<Stroke> {
        val layout = page.encode()
        return page.shapes(w, h).mapIndexedNotNull { i, s ->
            stroke(s, PanelRef(set, s.id, layout), if (s.inset) style.copy(fillColorHex = INSET_FILL) else style, UUID.randomUUID().toString(), order + i)
        }
    }

    private fun stroke(s: PanelShape, ref: PanelRef, style: ShapeStyle, id: String, order: Int): Stroke? {
        val pts = ArrayList<Offset>(s.size + 1)
        for (i in 0 until s.size) pts += Offset(s.x(i), s.y(i))
        pts += pts.first()
        val built = ShapeFactory.fromPaths(KIND, listOf(ShapePath(pts, closed = true)), style, null, id, order) ?: return null
        return built.copy(shape = built.shape!!.copy(panel = ref))
    }

    /**
     * Şablonu [page] düzenine göre yeniden kurar: var olan paneller yerinde güncellenir (kimlik, biçim, katman, sıra
     * korunur), silinenler çıkar, yeniler setin son panelinin hemen arkasına eklenir. [follow] açıkken (v37 varsayılan)
     * panellerin içindeki çizimler panelle birlikte yeni yerine taşınır ve yeni boyuna ölçeklenir (sıra, boyut,
     * aralık, kenar boşluğu). Yeni eklenen panellerin kimlikleri [added] ile döner.
     */
    fun apply(
        strokes: List<Stroke>, found: Found, page: PanelPage, w: Float, h: Float, defaultStyle: ShapeStyle,
        follow: Boolean = true, added: MutableMap<Int, String>? = null,
    ): List<Stroke> {
        val layout = page.encode()
        val shapes = page.shapes(w, h).associateBy { it.id }
        val old = if (follow) found.page.shapes(w, h).associateBy { it.id } else emptyMap()
        val keep = HashSet<String>()
        val out = ArrayList<Stroke>(strokes.size + 4)
        var lastPanel = -1
        for (s in strokes) {
            val r = s.shape?.panel
            if (r != null && r.set == found.set && found.strokes[r.leaf] === s) {
                val shape = shapes[r.leaf] ?: continue // silindi
                val base = s.shape!!.style.let { if (shape.inset && it.fillColorHex == null) it.copy(fillColorHex = INSET_FILL) else it }
                val rebuilt = stroke(shape, PanelRef(found.set, r.leaf, layout), base, s.id, s.order) ?: continue
                out += rebuilt.copy(groupId = s.groupId, layerId = s.layerId, opacity = s.opacity, hidden = s.hidden, clip = null)
                keep += s.id
                lastPanel = out.size - 1
            } else out += s
        }
        // Yeni paneller setin arkasına
        val style = found.strokes.values.firstOrNull()?.shape?.style?.copy(fillColorHex = null) ?: defaultStyle
        val fresh = page.leafIds().filter { it !in found.strokes }.mapNotNull { leaf ->
            val shape = shapes[leaf] ?: return@mapNotNull null
            val id = UUID.randomUUID().toString()
            added?.put(leaf, id)
            stroke(shape, PanelRef(found.set, leaf, layout), if (shape.inset) style.copy(fillColorHex = INSET_FILL) else style, id, 0)
        }
        val at = if (lastPanel >= 0) lastPanel + 1 else out.size
        out.addAll(at, fresh)
        val ordered = out.mapIndexed { i, s -> if (s.order == i) s else s.copy(order = i) }
        if (!follow || old.isEmpty()) return ordered
        return followContent(ordered, found, old, shapes)
    }

    /**
     * Panellerin içindeki çizimler (merkezi panelin içinde olanlar) panelle birlikte taşınır: çizim grubunun panel
     * içindeki göreli yeri korunur, boyu panelin küçülen/büyüyen kenarına göre bozulmadan (eşit oranla) ölçeklenir;
     * böylece panelin içinde kalır.
     */
    private fun followContent(strokes: List<Stroke>, found: Found, old: Map<Int, PanelShape>, now: Map<Int, PanelShape>): List<Stroke> {
        val panelIds = found.strokeIds()
        val index = ElementOps.index(strokes)
        val byId = strokes.associateBy { it.id }
        // Her eleman bir kez ve yalnız tek panele sayılır (iç paneldekiler iç panele)
        val owner = HashMap<Int, HashSet<String>>()
        for (e in index.elements) {
            // v46 yeni eklenen paneller de (henüz setin eski listesinde yok) içerik sayılmaz: sayfanın sonuna eklenen
            // panel eski son panelin alanına düşünce onunla birlikte küçülüp içine taşınıyordu
            if (e.strokeIds.any { it in panelIds || byId[it]?.shape?.panel?.set == found.set }) continue
            val box = ElementOps.bounds(e.strokeIds.mapNotNull { byId[it] }) ?: continue
            val cx = box.center.x; val cy = box.center.y
            val panel = old.values.firstOrNull { it.inset && it.contains(cx, cy) } ?: old.values.firstOrNull { !it.inset && it.contains(cx, cy) } ?: continue
            owner.getOrPut(panel.id) { HashSet() } += e.allIds
        }
        var out = strokes
        for ((leaf, inside) in owner) {
            val a = old.getValue(leaf).bounds()
            val b = now[leaf]?.bounds() ?: continue // panel silindi: çizimler yerinde kalır
            if (a.contentEquals(b)) continue
            val ids = index.expand(inside)
            val box = ElementOps.bounds(out.filter { it.id in ids }) ?: continue
            val ow = a[2] - a[0]; val oh = a[3] - a[1]
            if (ow <= 0f || oh <= 0f) continue
            val sx = (b[2] - b[0]) / ow; val sy = (b[3] - b[1]) / oh
            val k = min(sx, sy)
            // Grubun merkezi paneldeki göreli yerine taşınır, sonra o merkez etrafında eşit oranla ölçeklenir
            val nc = Offset(b[0] + (box.center.x - a[0]) * sx, b[1] + (box.center.y - a[1]) * sy)
            out = ElementOps.translate(out, ids, nc.x - box.center.x, nc.y - box.center.y)
            if (k < 0.995f || k > 1.005f) out = ElementOps.scale(out, ids, k, k, nc)
        }
        return out
    }

    /** İç panellerin dolgusu (altındaki büyük panelin çizimini örter). */
    const val INSET_FILL = "#FFFFFF"
}
