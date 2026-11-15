package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeScore
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.PracticeKind
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.PracticeLevels
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.PracticeScore
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** v52 alıştırma puanı daha sıkı: yalnız doğruluk; özensiz çizim geçmez, akıcılık puanı toplamı değiştirmez. */
class V52PracticeScoreTest {
    private fun circle(r: Float, wobble: Float = 0f, dx: Float = 0f, seed: Int = 1): List<List<Offset>> {
        val rnd = java.util.Random(seed.toLong())
        return listOf((0..120).map {
            val a = 2 * PI * it / 120
            val rr = r + (rnd.nextFloat() * 2 - 1) * wobble
            Offset(200f + dx + rr * cos(a).toFloat(), 200f + rr * sin(a).toFloat())
        })
    }

    private fun score(drawn: List<List<Offset>>, smooth: Int = 50): Int {
        val k = PracticeKind.TRACE
        val parts = ChallengeScore.outlineParts(drawn, circle(100f), k.near, k.far, PracticeScore.ALIGNED)
        return PracticeScore.of(parts, smooth, null, 0).total
    }

    @Test fun exactTracePasses() {
        val s = score(circle(100f, wobble = 1f))
        println("tam: $s"); assertTrue("tam üstünden çizim yüksek ($s)", s >= 90)
    }

    @Test fun sloppyFails() {
        val s = score(circle(100f, wobble = 16f, seed = 3))
        println("özensiz: $s"); assertTrue("titrek/özensiz çizim geçmez ($s)", s < PracticeLevels.PASS)
    }

    @Test fun offsetLowersScore() {
        val near = score(circle(100f, wobble = 1f))
        val shifted = score(circle(100f, wobble = 1f, dx = 9f))
        println("kaymış: $shifted / $near"); assertTrue("kayma puanı belirgin düşürür", shifted <= near - 15)
    }

    @Test fun halfDrawnFails() {
        val half = listOf(circle(100f).first().take(61))
        val s = score(half)
        println("yarım: $s"); assertTrue("yarım çizim geçmez ($s)", s < 50)
    }

    @Test fun smoothnessDoesNotChangeTotal() {
        val d = circle(100f, wobble = 3f)
        assertTrue(score(d, smooth = 0) == score(d, smooth = 100))
    }
}
