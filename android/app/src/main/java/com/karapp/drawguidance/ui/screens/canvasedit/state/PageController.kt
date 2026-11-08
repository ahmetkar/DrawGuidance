package com.karapp.drawguidance.ui.screens.canvasedit.state

import java.util.UUID

/**
 * Çalışma/not tuvalinin sayfaları: ekle, çoğalt, sil ve sayfaya git. Sayfa değişmeden önce [beforeChange]
 * çağrılır (açık yazı kaydedilir, seçime bağlı modlardan çıkılır). v45 çizgi roman kapağı ilk sayfadır (bayrağı
 * sayfanın arka plan kaydında); kapak silinince bayrak da gider, eklenen/çoğaltılan sayfa kapak olmaz.
 */
class PageController(
    private val store: EditorStore,
    private val beforeChange: () -> Unit,
    /** v33 sayfa sınırına ulaşıldı (en çok sayfa sayısı). */
    private val onLimit: (Int) -> Unit = {},
) {

    /** v33 tuval türünün sayfa sınırına ulaşıldıysa bildirir ve true döner. */
    private fun atLimit(): Boolean {
        val current = store.canvas.value ?: return true
        val max = current.kind.maxPages
        if (current.pages.size < max) return false
        onLimit(max)
        return true
    }

    /** Sayfa ekler ve yeni sayfaya geçer (v33: tuval türünün sayfa sınırına kadar). */
    fun addPage() {
        if (atLimit()) return
        beforeChange()
        val current = store.canvas.value ?: return
        val at = (current.currentPage + 1).coerceIn(0, current.pages.size)
        val pages = current.pages.toMutableList().apply { add(at, emptyList()) }
        // v24 yeni sayfa açık sayfanın arka planını alır (v45 kapak bayrağı kopyalanmaz)
        store.commit(pages, current.width, current.height, current.backgroundColorHex, page = at,
            pageBackgrounds = insertBackground(current, at, current.pageBackgrounds.getOrNull(current.currentPage)?.withoutCover()))
    }

    /** Sayfayı içeriğiyle birlikte çoğaltır. */
    fun duplicatePage(index: Int) {
        if (atLimit()) return
        beforeChange()
        val current = store.canvas.value ?: return
        val page = current.pages.getOrNull(index) ?: return
        val copy = page.map { it.copy(id = UUID.randomUUID().toString()) }
        val pages = current.pages.toMutableList().apply { add(index + 1, copy) }
        store.commit(pages, current.width, current.height, current.backgroundColorHex, page = index + 1,
            pageBackgrounds = insertBackground(current, index + 1, current.pageBackgrounds.getOrNull(index)?.withoutCover()))
    }

    /**
     * v45 çizgi roman kapağı ekler: [cover] elemanlarıyla yeni bir ilk sayfa (kapak) eklenir ve açılır. Eski sayfaların
     * numaraları değişmez (kapak numaralanmaz). Kapak zaten varsa ya da sayfa sınırına ulaşıldıysa bir şey yapmaz;
     * eklendiyse true. Geri alınınca kapak sayfası da bayrağı da birlikte kalkar.
     */
    fun addCover(cover: List<com.karapp.drawguidance.data.model.Stroke>): Boolean {
        val current0 = store.canvas.value ?: return false
        if (current0.hasCover || atLimit()) return false
        beforeChange()
        val current = store.canvas.value ?: return false
        val pages = current.pages.toMutableList().apply { add(0, cover.mapIndexed { i, s -> if (s.order == i) s else s.copy(order = i) }) }
        val backgrounds = current.pageBackgrounds.toMutableList()
        while (backgrounds.size < current.pages.size) backgrounds.add(null)
        backgrounds.add(0, com.karapp.drawguidance.data.model.PageBackground(cover = true))
        store.commit(pages, current.width, current.height, current.backgroundColorHex, page = 0, pageBackgrounds = backgrounds)
        return true
    }

    fun deletePage(index: Int) {
        val current0 = store.canvas.value ?: return
        if (current0.pages.size <= 1 || index !in current0.pages.indices) return
        beforeChange()
        val current = store.canvas.value ?: return
        val pages = current.pages.toMutableList().apply { removeAt(index) }
        val backgrounds = if (index < current.pageBackgrounds.size) current.pageBackgrounds.toMutableList().apply { removeAt(index) } else current.pageBackgrounds
        store.commit(pages, current.width, current.height, current.backgroundColorHex, page = index.coerceAtMost(pages.size - 1),
            pageBackgrounds = backgrounds)
    }

    /** v24 sayfa arka planları listesine [at] sırasında [value] eklenir (liste sayfa sayısına kadar null'la uzatılır). */
    private fun insertBackground(
        current: com.karapp.drawguidance.data.model.CanvasUi,
        at: Int,
        value: com.karapp.drawguidance.data.model.PageBackground?,
    ): List<com.karapp.drawguidance.data.model.PageBackground?> {
        if (current.pageBackgrounds.isEmpty() && value == null) return current.pageBackgrounds
        val list = current.pageBackgrounds.toMutableList()
        while (list.size < current.pages.size) list.add(null)
        list.add(at.coerceIn(0, list.size), value)
        return list
    }

    fun goToPage(index: Int) {
        val current = store.canvas.value ?: return
        if (index !in current.pages.indices || index == current.currentPage) return
        beforeChange()
        store.showPage(index)
    }
}
