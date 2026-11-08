package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.PageBackground

/**
 * v40 arka plan resmini taşıma/boyutlandırma (not alma dışındaki türlerde; bkz. ui/drawing/BackgroundTransform).
 * Sürüklerken tuval geçmişe girmeden güncellenir ([preview]); parmak kalkınca tek geri alma adımı olarak kaydedilir
 * ([commit]). Sayfaların kendi arka planı varsa (çok sayfalı çizgi roman) açık sayfanınki, yoksa ortak arka plan değişir.
 */
class BackgroundTransformController(
    private val store: EditorStore,
    private val frame: FrameController,
    /** Arka plan sayfa sayfa mı değişir (CanvasEditViewModel.backgroundPerPage). */
    private val perPage: () -> Boolean,
) {
    /** Önizleme başlamadan önceki arka planlar (önizleme geri alınıp kalıcı adım olarak yazılır). */
    private var origin: Pair<BackgroundSpec, List<PageBackground?>>? = null

    /** Bu tuvalde arka plan taşınabilir mi: not alma dışında ve açık sayfada arka plan resmi varken. */
    fun available(): Boolean {
        val c = store.canvas.value ?: return false
        return !c.kind.isNotes && c.pageBackground.imageId != null
    }

    private fun onPage(c: CanvasUi): Boolean = perPage() || c.pageBackgrounds.getOrNull(c.currentPage)?.spec != null

    /** v41 açık sayfanın kendi arka planı mı değişir (değilse ortak arka plan). */
    fun onCurrentPage(): Boolean = store.canvas.value?.let { onPage(it) } ?: false

    private fun withSpec(c: CanvasUi, spec: BackgroundSpec): CanvasUi {
        if (!onPage(c)) return c.copy(background = spec)
        val list = c.pageBackgrounds.toMutableList()
        while (list.size < c.pages.size) list.add(null)
        list[c.currentPage] = (list[c.currentPage] ?: PageBackground()).copy(spec = spec)
        return c.copy(pageBackgrounds = list)
    }

    /** Sürükleme sürerken (kaydedilmez, geçmişe girmez). */
    fun preview(spec: BackgroundSpec) {
        val c = store.canvas.value ?: return
        if (origin == null) origin = c.background to c.pageBackgrounds
        if (c.pageBackground == spec) return
        store.updateCanvas { withSpec(it, spec) }
    }

    /** Sürükleme bitti: tek geri alma adımı, hemen kaydedilir ([coalesce]: aynı anahtarlı önceki adımla birleşir). */
    fun commit(spec: BackgroundSpec) = commit(spec, null)

    fun commit(spec: BackgroundSpec, coalesce: String?) {
        restore()
        val c = store.canvas.value ?: return
        if (c.pageBackground == spec) return
        if (onPage(c)) frame.setPageSpec(spec, coalesce) else store.commitSharedBackground(spec, coalesce)
    }

    /** Önizleme bırakıldı (ör. ikinci parmak, ekrandan çıkış): arka plan önceki haline döner. */
    fun restore() {
        val (background, pages) = origin ?: return
        origin = null
        store.updateCanvas { it.copy(background = background, pageBackgrounds = pages) }
    }
}
