package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.QuickBrushLists
import com.karapp.drawguidance.data.model.QuickCorner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v56 hızlı fırça seçicinin durumu (planın 2. bölümü): mod başına son kullanılanlar ve favoriler, şeridin açıklığı,
 * düğmenin köşesi. Kayıt `brush_quick` tercihlerinde ([load] ve [save] dışarıdan verilir; testlerde bellekte).
 */
class QuickBrushStore(
    private val load: (kind: String) -> String? = { null },
    private val save: (kind: String, text: String) -> Unit = { _, _ -> },
    private val loadCorner: (kind: String) -> String? = { null },
    private val saveCorner: (kind: String, corner: String) -> Unit = { _, _ -> },
) {
    private var kind: CanvasKind? = null
    private val _lists = MutableStateFlow(QuickBrushLists())
    val lists: StateFlow<QuickBrushLists> = _lists.asStateFlow()
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()
    private val _corner = MutableStateFlow(QuickCorner.TOP_START)
    val corner: StateFlow<QuickCorner> = _corner.asStateFlow()

    /** Tuval açılınca o modun listeleri yüklenir. */
    fun bind(kind: CanvasKind) {
        if (this.kind == kind) return
        this.kind = kind
        _lists.value = QuickBrushLists.decode(runCatching { load(kind.name) }.getOrNull())
        _corner.value = runCatching { loadCorner(kind.name) }.getOrNull()?.let { n -> QuickCorner.entries.firstOrNull { it.name == n } } ?: QuickCorner.TOP_START
    }

    private fun store(next: QuickBrushLists) {
        _lists.value = next
        kind?.let { k -> runCatching { save(k.name, next.encode()) } }
    }

    fun used(type: BrushType) {
        val next = _lists.value.used(type)
        if (next != _lists.value) store(next)
    }

    /** Sabitle / kaldır; favoriler doluysa false. */
    fun toggleFavorite(type: BrushType): Boolean {
        val (next, ok) = _lists.value.toggleFavorite(type)
        if (ok) store(next)
        return ok
    }

    fun setOpen(open: Boolean) { _open.value = open }
    fun toggle() { _open.value = !_open.value }

    fun setCorner(corner: QuickCorner) {
        _corner.value = corner
        kind?.let { k -> runCatching { saveCorner(k.name, corner.name) } }
    }
}
