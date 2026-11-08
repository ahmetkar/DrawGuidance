package com.karapp.drawguidance.ui.screens.canvasedit.editor

import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.Layers
import com.karapp.drawguidance.data.model.Stroke

/**
 * v23 elemanların üst/alt sırası (aynı katmanda): seçili elemanlar (silgileriyle birlikte) bir üstteki/alttaki elemanın
 * üstüne/altına ya da en üste/en alta taşınır. Katmanlar arası sıra katman sırasıdır ([Layers.sort]).
 */
object ZOrder {
    const val FORWARD = 1
    const val BACKWARD = -1
    const val FRONT = 2
    const val BACK = -2

    fun reorder(strokes: List<Stroke>, selected: Set<String>, index: ElementIndex, direction: Int, layers: List<Layer>): List<Stroke> {
        val chosen = index.selectedElements(selected)
        if (chosen.isEmpty()) return strokes
        val moving = HashSet<String>()
        chosen.forEach { moving += it.allIds }
        val pos = HashMap<String, Int>(strokes.size * 2)
        strokes.forEachIndexed { i, s -> pos[s.id] = i }
        val movingList = strokes.filter { it.id in moving }
        if (movingList.isEmpty()) return strokes
        val rest = strokes.filter { it.id !in moving }
        val layer = Layers.of(movingList.last(), layers)
        val result = when (direction) {
            FRONT -> rest + movingList
            BACK -> movingList + rest
            FORWARD, BACKWARD -> {
                // Aynı katmandaki diğer elemanlar (sıra: en üstteki çizgisinin yeri)
                val others = index.elements.filter { e ->
                    e.strokeIds.isNotEmpty() && e.allIds.none { it in moving } &&
                        e.strokeIds.firstOrNull()?.let { id -> strokes.getOrNull(pos[id] ?: -1)?.let { Layers.of(it, layers) == layer } } == true
                }
                if (direction == FORWARD) {
                    val top = movingList.maxOf { pos[it.id] ?: 0 }
                    val next = others.filter { e -> e.allIds.maxOf { pos[it] ?: -1 } > top }.minByOrNull { e -> e.allIds.maxOf { pos[it] ?: -1 } }
                        ?: return strokes
                    val lastId = next.allIds.maxByOrNull { pos[it] ?: -1 } ?: return strokes
                    val at = rest.indexOfFirst { it.id == lastId } + 1
                    rest.subList(0, at) + movingList + rest.subList(at, rest.size)
                } else {
                    val bottom = movingList.minOf { pos[it.id] ?: 0 }
                    val prev = others.filter { e -> e.allIds.minOf { pos[it] ?: Int.MAX_VALUE } < bottom }.maxByOrNull { e -> e.allIds.minOf { pos[it] ?: Int.MAX_VALUE } }
                        ?: return strokes
                    val firstId = prev.allIds.minByOrNull { pos[it] ?: Int.MAX_VALUE } ?: return strokes
                    val at = rest.indexOfFirst { it.id == firstId }.coerceAtLeast(0)
                    rest.subList(0, at) + movingList + rest.subList(at, rest.size)
                }
            }
            else -> return strokes
        }
        val sorted = Layers.sort(result, layers)
        return if (sorted.indices.all { sorted[it] === strokes[it] }) strokes else sorted.mapIndexed { i, s -> if (s.order == i) s else s.copy(order = i) }
    }
}
