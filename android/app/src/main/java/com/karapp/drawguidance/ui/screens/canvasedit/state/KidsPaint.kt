package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.content.fill.RegionFill
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.diagnostics.AppErrors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * v37 çocuk modunda kova imleci: parmağın (tuvaldeki) yeri [point], altındaki şekil ve boyanmaya hazır alan [target]
 * (henüz bulunmadıysa null), basılı tutma sayacı [hold] (her yeniden başlayışta artar; -1: sayılmıyor, ör. bütün şekil
 * boyandı). [color] eğitimde maskotun seçtiği renk (null: seçili boya rengi).
 */
data class PaintCursor(val point: Offset, val target: PaintTarget?, val hold: Int, val color: String? = null)

/**
 * v37 kova imlecinin durumu: imleç parmakla hemen yer değiştirir; altındaki alan arka planda bulunur (alan bulma
 * ızgarada taşma doldurma olduğundan parmak aynı alanda kaldıkça yeniden hesaplanmaz; sırada en son istenen yer
 * hesaplanır, aradakiler atlanır).
 */
class KidsPaintController(
    private val scope: CoroutineScope,
    private val target: (Offset) -> PaintTarget?,
) {
    private val _cursor = MutableStateFlow<PaintCursor?>(null)
    val cursor: StateFlow<PaintCursor?> = _cursor.asStateFlow()

    /** Alanı bulunacak son yer (sıra numarasıyla: aynı yer yeniden istenebilsin). */
    private class Request(val point: Offset, val seq: Long)
    private val requests = MutableStateFlow<Request?>(null)
    private var seq = 0L

    init {
        scope.launch(Dispatchers.Default) {
            // StateFlow birleştirir: hesap sürerken gelen istekler beklemez, yalnız sonuncusu hesaplanır
            requests.filterNotNull().collect { req ->
                val t = AppErrors.guard("kids.paint", null) { target(req.point) }
                _cursor.update { c ->
                    when {
                        c == null -> null
                        c.point == req.point || t?.region?.let { inside(it, c.point) } == true -> c.copy(target = t)
                        else -> c
                    }
                }
            }
        }
    }

    /** İmleç [point]'e (null: parmak kalktı, imleç gizlenir); [hold] basılı tutma sayacı. */
    fun hover(point: Offset?, hold: Int, color: String? = null) {
        if (point == null) {
            _cursor.value = null
            return
        }
        val prev = _cursor.value
        // Parmak aynı alanın içindeyse alan yeniden aranmaz
        val keep = prev?.target?.takeIf { t -> t.region != null && inside(t.region, point) }
        _cursor.value = PaintCursor(point, keep ?: prev?.target, hold, color)
        if (keep == null) requests.value = Request(point, ++seq)
    }

    /** Boyama tuvali değiştirdi: imlecin altındaki alan yeniden bulunur. */
    fun refresh() {
        val c = _cursor.value ?: return
        requests.value = Request(c.point, ++seq)
    }

    private fun inside(region: List<Offset>, p: Offset) = RegionFill.contains(region.map { P(it.x, it.y) }, P(p.x, p.y))
}
