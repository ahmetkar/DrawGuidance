package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.data.model.CanvasKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v45 çizim ekranının iki eki:
 * - Çizgi roman kapağı: kapaksız (eski) çizgi romana sayfa menüsündeki "Kapak ekle" ile kapak sayfası eklenir.
 * - Not alma panelleri: "Panel ekle" penceresi ve seçilen yerleşimin alanlarının sayfaya eklenmesi.
 */
class V45NotesComicController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val pages: PageController,
) {
    // --- Çizgi roman kapağı ---

    /** Kapak eklenebilir mi: çizgi roman ve henüz kapak yok. */
    fun canAddCover(): Boolean = store.canvas.value?.let { it.kind == CanvasKind.COMIC && !it.hasCover } == true

    /**
     * Kapaksız çizgi romanın başına kapak sayfası ekler (başlık tuvalin adı, alt başlık yazar alanı) ve kapağı açar.
     * Eski sayfaların numaraları değişmez. Eklendiyse true.
     */
    fun addCover(): Boolean {
        val current = store.canvas.value ?: return false
        if (!canAddCover()) return false
        val cover = ComicCover.build(current.width, current.height, current.name, ComicCover.subtitlePlaceholder())
        return pages.addCover(cover)
    }

    // --- Not alma panelleri ---

    private val _panelsOpen = MutableStateFlow(false)
    /** "Panel ekle" penceresi açık mı. */
    val panelsOpen: StateFlow<Boolean> = _panelsOpen.asStateFlow()

    fun openPanels() { _panelsOpen.value = true }
    fun closePanels() { _panelsOpen.value = false }

    /**
     * [layoutId] yerleşiminin alanlarını açık sayfaya ekler. Alanlar sayfanın en altına (var olan yazı ve çizimlerin
     * altına) konur, eklenince seçili olur (taşınabilir). Pencere kapanır. Eklenen eleman kimlikleri döner.
     */
    fun insertPanels(layoutId: String): Set<String> {
        val current = store.canvas.value ?: return emptySet()
        val layout = NotesPanels.byId(layoutId) ?: return emptySet()
        val added = NotesPanels.build(layout, current.width.toFloat(), current.height.toFloat())
        if (added.isEmpty()) return emptySet()
        val merged = (added + current.strokes).mapIndexed { i, s -> if (s.order == i) s else s.copy(order = i) }
        store.commit(merged)
        _panelsOpen.value = false
        val ids = added.mapTo(LinkedHashSet()) { it.id }
        host.inserted(ids)
        return ids
    }
}
