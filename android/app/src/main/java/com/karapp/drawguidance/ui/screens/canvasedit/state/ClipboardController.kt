package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.clipboard.ElementClipboard
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.ElementClip
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * v56 2C-3 seçili elemanları sayfalar (ve tuvaller) arasında kes, kopyala, yapıştır. Pano uygulama düzeyindedir
 * ([ElementClipboard]); her işlem tek geri alma adımıdır.
 */
class ClipboardController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val assetStore: CanvasAssetStore?,
) {
    val content get() = ElementClipboard.content

    /** Seçili elemanlar panoya alınır; seçili eleman yoksa false. */
    fun copy(): Boolean = take(cut = false)

    /** Seçili elemanlar panoya alınır ve sayfadan çıkar (geri al geri getirir). */
    fun cut(): Boolean {
        if (!take(cut = true)) return false
        val current = store.canvas.value ?: return false
        val taken = ElementClipboard.content.value?.strokes?.mapTo(HashSet()) { it.id } ?: return false
        store.commit(current.strokes.filter { it.id !in taken })
        store.select(emptySet())
        return true
    }

    private fun take(cut: Boolean): Boolean {
        val current = store.canvas.value ?: return false
        val strokes = ElementClip.collect(current.strokes, store.selection)
        if (strokes.isEmpty()) return false
        val c = ElementClipboard.set(strokes, store.canvasLocalId, current.currentPage, cut)
        val ids = ElementClip.assetIds(strokes)
        if (ids.isNotEmpty() && assetStore != null) {
            host.scope.launch { ElementClipboard.attachAssets(c.serial, runCatching { assetStore.entities(ids) }.getOrDefault(emptyList())) }
        }
        return true
    }

    /**
     * Panodakiler açık sayfaya yapıştırılır: [anchor] ("buraya yapıştır") verilirse oraya, yoksa aynı konuma (sayfaya
     * sığmıyor ya da [visible] görünür alanın dışındaysa görünür alanın ortasına). Başka tuvalden gelen resimler bu
     * tuvale kopyalanır. Yapıştırılanlar seçili gelir.
     */
    fun paste(anchor: Offset? = null, visible: FloatArray? = null) {
        val content = ElementClipboard.content.value ?: return
        host.scope.launch {
            val map = HashMap<String, String>()
            val needed = ElementClip.assetIds(content.strokes)
            if (needed.isNotEmpty() && assetStore != null) {
                val here = runCatching { assetStore.entities(needed) }.getOrDefault(emptyList())
                    .filter { it.canvasLocalId == store.canvasLocalId }.mapTo(HashSet()) { it.id }
                for (a in content.assets) if (a.id in needed && a.id !in here) map[a.id] = assetStore.insertCopy(a, store.canvasLocalId)
            }
            val now = store.canvas.value ?: return@launch
            val b = ElementClip.bounds(content.strokes) ?: return@launch
            val repeat = ElementClipboard.nextRepeat(store.canvasLocalId, now.currentPage, anchor?.toString() ?: "")
            val (dx, dy) = ElementClip.offset(b, now.width.toFloat(), now.height.toFloat(), visible, anchor?.let { it.x to it.y }, repeat)
            val pasted = ElementClip.paste(content.strokes, { UUID.randomUUID().toString() }, now.strokes.size, dx, dy, map, ContentCodec::quantize)
            store.commit(now.strokes + pasted, useArea = false)
            assetStore?.runCatching { ensureLoaded(pasted) }
            if (host.mode.value != EditMode.SELECT) host.setMode(EditMode.SELECT)
            store.select(store.currentIndex().expand(pasted.filter { !it.hidden }.mapTo(HashSet()) { it.id }))
        }
    }
}
