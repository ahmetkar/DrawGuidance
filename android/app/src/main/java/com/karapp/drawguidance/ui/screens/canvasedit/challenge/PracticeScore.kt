package com.karapp.drawguidance.ui.screens.canvasedit.challenge

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * v51 yetişkin alıştırmalarının gelişmiş puanı. Eskiden yalnız çizgi puanı (F1) vardı; şimdi dört parça ayrı ölçülür ve
 * sonuç penceresinde gösterilir:
 *  - **İsabet** ([accuracy]): çizimin hedef çizgilere yakın kısmı.
 *  - **Kapsama** ([coverage]): hedefin çizilen kısmı.
 *  - **Akıcılık** ([smoothness]): çizgilerin titrememesi. Çizgi eşit aralıkla örneklenir, art arda yön değişimlerinin
 *    farkının (eğriliğin değişimi) ortancası ölçülür; köşeler ortancayı bozmaz. [SMOOTH_FULL] radyanda tam,
 *    [SMOOTH_ZERO] radyanda sıfır puan.
 *  - **Hız** ([speed], yalnız süreli çizimde): sürenin yarısında bitirmek tam puan, sürenin sonunda 50.
 *
 * Toplam: süresizde %80 çizgi puanı (F1) + %20 akıcılık; süreli çizimde %70 + %15 + %15 hız. Toplam çizgi puanını en çok
 * [SMOOTH_BONUS_MAX] puan geçebilir (düzgün ama yanlış yere çizilen çizgi yüksek puan almasın). Her ipucu
 * [PracticeLevels.HINT_PENALTY] düşer.
 */
data class PracticeScore(
    val total: Int,
    val outline: Int,
    val accuracy: Int,
    val coverage: Int,
    val smoothness: Int,
    /** Süreli çizimde hız (değilse null). */
    val speed: Int?,
    val hints: Int,
) {
    companion object {
        val ZERO = PracticeScore(0, 0, 0, 0, 0, null, 0)

        const val SMOOTH_FULL = 0.05f
        const val SMOOTH_ZERO = 0.40f
        const val SMOOTH_BONUS_MAX = 12

        /** Örnek aralığı: hedef kutusunun köşegeninin 1/90'ı. */
        private const val SMOOTH_SAMPLES = 90f

        fun of(parts: ChallengeScore.OutlineParts, smoothness: Int, speed: Int?, hints: Int): PracticeScore {
            if (parts.score <= 0) return ZERO.copy(hints = hints, speed = speed)
            val raw = if (speed == null) 0.8f * parts.score + 0.2f * smoothness else 0.7f * parts.score + 0.15f * smoothness + 0.15f * speed
            val capped = minOf(raw.roundToInt(), parts.score + SMOOTH_BONUS_MAX)
            val total = (capped - hints * PracticeLevels.HINT_PENALTY).coerceIn(0, 100)
            return PracticeScore(total, parts.score, parts.precision, parts.recall, smoothness, speed, hints)
        }

        /** Süreli çizimde hız puanı: [elapsedMs] / [limitMs] ≤ 0,5 ise 100, 1'de 50 (arası doğrusal). */
        fun speed(elapsedMs: Long, limitMs: Long): Int {
            if (limitMs <= 0) return 100
            val r = (elapsedMs.toFloat() / limitMs).coerceIn(0f, 1f)
            return if (r <= 0.5f) 100 else (100f - (r - 0.5f) * 100f).roundToInt().coerceIn(50, 100)
        }

        /** Çizgilerin akıcılığı (0–100); [diag] hedef kutusunun köşegeni. Ölçülecek kadar çizgi yoksa 0. */
        fun smoothness(lines: List<List<Offset>>, diag: Float): Int {
            val step = (diag / SMOOTH_SAMPLES).coerceAtLeast(1f)
            val changes = ArrayList<Float>()
            for (line in lines) {
                val pts = ChallengeScore.resample(listOf(line), step)
                if (pts.size < 4) continue
                var prevDir = Float.NaN
                var prevTurn = Float.NaN
                for (i in 1 until pts.size) {
                    val dx = pts[i].x - pts[i - 1].x
                    val dy = pts[i].y - pts[i - 1].y
                    if (hypot(dx, dy) < step * 0.25f) continue
                    val dir = atan2(dy, dx)
                    if (!prevDir.isNaN()) {
                        val turn = wrap(dir - prevDir)
                        if (!prevTurn.isNaN()) changes += abs(turn - prevTurn)
                        prevTurn = turn
                    }
                    prevDir = dir
                }
            }
            if (changes.size < 3) return 0
            changes.sort()
            val median = changes[changes.size / 2]
            val k = ((SMOOTH_ZERO - median) / (SMOOTH_ZERO - SMOOTH_FULL)).coerceIn(0f, 1f)
            return (k * 100f).roundToInt()
        }

        private fun wrap(a: Float): Float {
            var x = a
            while (x > PI) x -= (2 * PI).toFloat()
            while (x < -PI) x += (2 * PI).toFloat()
            return x
        }
    }
}
