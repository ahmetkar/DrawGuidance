package com.karapp.drawguidance.data.history

import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.Stroke

/**
 * Belgenin geri alınabilir durumu: sayfalar, kağıt boyutu, arka plan ve açık sayfa.
 * Çizimler değişmez nesneler olduğu için durumlar çizimleri paylaşır (kopyalanmaz): bir kayıt yalnızca
 * değişen sayfanın liste kopyası kadar yer tutar.
 */
data class DocumentState(
    val pages: List<List<Stroke>>,
    val width: Int,
    val height: Int,
    val background: String,
    val page: Int,
    val layers: List<Layer> = listOf(Layer.Default),
    /** v24 sayfaların kendi arka planları (sayfa eklenip silinince sayfalarla birlikte geri alınır). */
    val pageBackgrounds: List<com.karapp.drawguidance.data.model.PageBackground?> = emptyList(),
    /**
     * v40 ortak arka plan resminin kaydırma/ölçeği (yalnız arka planı taşıma adımlarında; null: bu adım değiştirmez).
     * Geri alınınca yalnız resmin yeri ve boyu döner (resim, desen ve renk o anki haliyle kalır).
     */
    val sharedBackground: com.karapp.drawguidance.data.model.BackgroundSpec? = null,
    /**
     * v41 [sharedBackground] bütünüyle mi döner (arka plan penceresinde Uygula, arka planı silme): true iken resim, desen
     * ve gradyan da bu adımın haline döner; false iken yalnız resmin yeri ve boyu.
     */
    val sharedBackgroundFull: Boolean = false,
)

/**
 * Geri al / yinele geçmişi. Her kayıt bir değişikliğin önceki ve sonraki durumudur (komut); geri almak
 * öncekine, yinelemek sonrakine döner. Aynı anahtarla kısa aralıklarla gelen değişiklikler (kaydırıcı ya da
 * renk sürükleme, yazma) tek kayıtta birleşir. Yeni bir değişiklik yineleme listesini temizler.
 */
class EditHistory<S : Any>(
    private val maxSize: Int = 100,
    private val coalesceWindowMs: Long = 2_500L,
) {
    private class Entry<S>(val before: S, var after: S, val key: String?, var at: Long)

    private val undoStack = ArrayDeque<Entry<S>>()
    private val redoStack = ArrayDeque<Entry<S>>()
    private var coalescing = true

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoSize: Int get() = undoStack.size
    val redoSize: Int get() = redoStack.size

    /** [before] → [after] değişikliğini kaydeder; [key] aynıysa ve süre içindeyse son kayıtla birleştirir. */
    fun record(before: S, after: S, key: String? = null, now: Long = 0L) {
        redoStack.clear()
        val top = undoStack.lastOrNull()
        if (coalescing && key != null && top != null && top.key == key && now - top.at <= coalesceWindowMs) {
            top.after = after
            top.at = now
        } else {
            undoStack.addLast(Entry(before, after, key, now))
            while (undoStack.size > maxSize) undoStack.removeFirst()
        }
        coalescing = true
    }

    /** Son değişikliği geri alır: dönülecek durum (yoksa null). */
    fun undo(): S? {
        val entry = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(entry)
        coalescing = false
        return entry.before
    }

    /** Geri alınan değişikliği yineler: dönülecek durum (yoksa null). */
    fun redo(): S? {
        val entry = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(entry)
        coalescing = false
        return entry.after
    }

    /** Sonraki değişiklik öncekiyle birleşmesin (ör. renk penceresi kapandı). */
    fun breakCoalescing() {
        coalescing = false
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
