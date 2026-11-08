package com.karapp.drawguidance.ui.screens.canvasedit.components

/**
 * v40 basit resim çiziminde Seç'in hemen yanında Taşı düğmesi; ikisi menünün soldan [SIMPLE_SELECT_POSITION]. ve bir
 * sonraki düğmesi. Sıra, menüde görünen bütün düğmelerle sayılır: baştaki geri al ve yinele ([SIMPLE_HISTORY_BUTTONS])
 * dahil (geri al 1., yinele 2., fırça 3., silgi 4., Seç 5., Taşı 6.).
 */
object SimpleToolOrder {
    const val SELECT = "select"
    const val MOVE = "move"

    /** Seç düğmesinin menüdeki sırası (1'den; geri al ve yinele dahil). Taşı bir sonraki sırada. */
    const val SIMPLE_SELECT_POSITION = 5

    /** Araçlardan önce gelen geri al ve yinele düğmeleri. */
    const val SIMPLE_HISTORY_BUTTONS = 2

    /**
     * Seç ve Taşı'yı (hangisi varsa) araç listesinde [SIMPLE_SELECT_POSITION] sırasına alır; ötekilerin sırası değişmez.
     * Liste kısaysa sona eklenir.
     */
    fun <T> placeSelectAndMove(items: List<T>, id: (T) -> String): List<T> {
        val pair = listOf(SELECT, MOVE).mapNotNull { key -> items.firstOrNull { id(it) == key } }
        if (pair.isEmpty()) return items
        val rest = items.filter { id(it) != SELECT && id(it) != MOVE }
        val at = (SIMPLE_SELECT_POSITION - 1 - SIMPLE_HISTORY_BUTTONS).coerceIn(0, rest.size)
        return rest.take(at) + pair + rest.drop(at)
    }
}
