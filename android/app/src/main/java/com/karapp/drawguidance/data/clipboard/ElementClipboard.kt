package com.karapp.drawguidance.data.clipboard

import com.karapp.drawguidance.data.local.CanvasAssetEntity
import com.karapp.drawguidance.data.model.Stroke
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v56 2C-3 uygulama içi eleman panosu: tuval kapansa da uygulama açık kaldıkça durur. Elemanlar V11 modeliyle
 * (kimlikleri kaynaktaki gibi) tutulur; resim varlıklarının baytları kopyalama anında alınır ki kaynak tuvalde silinse
 * de başka tuvale yapıştırılabilsin.
 */
object ElementClipboard {
    class Content(
        val strokes: List<Stroke>,
        val sourceCanvas: String,
        val sourcePage: Int,
        /** Kesildi mi (kaynakta artık yok: ilk yapıştırma aynı yere). */
        val cut: Boolean,
        val assets: List<CanvasAssetEntity> = emptyList(),
        val serial: Long = 0,
    )

    private val _content = MutableStateFlow<Content?>(null)
    val content: StateFlow<Content?> = _content.asStateFlow()
    private var serial = 0L
    private var lastKey: String? = null
    private var repeat = 0

    fun set(strokes: List<Stroke>, sourceCanvas: String, sourcePage: Int, cut: Boolean): Content {
        serial += 1
        lastKey = null
        return Content(strokes, sourceCanvas, sourcePage, cut, serial = serial).also { _content.value = it }
    }

    /** Kopyalama anında alınan varlıklar (yalnız pano değişmediyse). */
    fun attachAssets(serial: Long, assets: List<CanvasAssetEntity>) {
        val c = _content.value ?: return
        if (c.serial == serial) _content.value = Content(c.strokes, c.sourceCanvas, c.sourcePage, c.cut, assets, c.serial)
    }

    /**
     * Aynı yere kaçıncı yapıştırma: kaynağın kendi sayfasına kesilmeden yapıştırmada ilki de kaydırılır (aslının üstüne
     * binmesin); başka sayfada ya da kesilmişse ilki aynı yere.
     */
    fun nextRepeat(canvas: String, page: Int, anchorKey: String): Int {
        val c = _content.value ?: return 0
        val key = "${c.serial}|$canvas|$page|$anchorKey"
        repeat = if (key == lastKey) repeat + 1 else if (!c.cut && c.sourceCanvas == canvas && c.sourcePage == page) 1 else 0
        lastKey = key
        return repeat
    }

    fun clear() { _content.value = null; lastKey = null }
}
