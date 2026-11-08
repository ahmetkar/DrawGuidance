package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v45 boya fırçasının süren darbesi: parmağın geçtiği noktalar ([points], tuval birimi), boyanın kaldığı alan
 * ([region]; null: boş kâğıt, serbest), renk ve fırça boyu. Önizleme bunu alanın sınırıyla kırparak çizer.
 */
data class BrushLive(
    val points: List<Offset>,
    val region: KidsBrush.Region?,
    val colorHex: String,
    val sizePx: Float,
)

/**
 * v45 çocuk modunda boya fırçası: parmak inince başladığı yerin alanı bulunur (kova ile aynı alan), parmak gezdikçe
 * önizleme güncellenir, kalkınca darbe tek geri alma adımı olarak kaydedilir ([commit]). İki parmak gelirse darbe
 * bırakılır.
 */
class KidsBrushController(
    private val regionAt: (Offset) -> KidsBrush.Region?,
    private val color: () -> String,
    private val size: () -> Float,
    private val commit: (region: KidsBrush.Region?, points: List<Offset>, sizePx: Float, colorHex: String) -> Boolean,
) {
    private val _live = MutableStateFlow<BrushLive?>(null)
    /** Süren darbe (yoksa null). */
    val live: StateFlow<BrushLive?> = _live.asStateFlow()

    /** Parmak indi: alan bulunur, darbe başlar. */
    fun begin(point: Offset) {
        val region = runCatching { regionAt(point) }.getOrNull()
        _live.value = BrushLive(listOf(point), region, color(), size())
    }

    /** Parmak gezdi (aynı noktalar tekrar eklenmez). */
    fun move(point: Offset) {
        val c = _live.value ?: return
        if (c.points.lastOrNull() == point) return
        _live.value = c.copy(points = c.points + point)
    }

    /** Parmak kalktı: darbe kaydedilir. Bir şey boyandıysa true. */
    fun end(point: Offset? = null): Boolean {
        val c = _live.value ?: return false
        _live.value = null
        val points = if (point != null && c.points.lastOrNull() != point) c.points + point else c.points
        return commit(c.region, points, c.sizePx, c.colorHex)
    }

    /** Darbe bırakıldı (iki parmak, mod değişti): hiçbir şey kaydedilmez. */
    fun cancel() {
        _live.value = null
    }
}
