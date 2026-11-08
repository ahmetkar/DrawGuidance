package com.karapp.drawguidance.content.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * v38 yazı önerisinin sinir ağı kapısı ([Handwriting.writingScore]): yazı satırları geçer; çizimler, şekiller ve çizgi
 * grupları geçmez. Eğitimden ayrı üreteçle ([InkSynth]) sınanır.
 */
class TextGateTest {
    private fun box(ox: Float, oy: Float, s: Float): List<InkStroke> {
        fun seg(ax: Float, ay: Float, bx: Float, by: Float) = InkStroke((0..20).map { i -> val t = i / 20f; InkPoint(ax + (bx - ax) * t, ay + (by - ay) * t + (if (i % 3 == 0) 0.6f else 0f)) })
        return listOf(seg(ox, oy, ox + s, oy), seg(ox + s, oy, ox + s, oy + s), seg(ox + s, oy + s, ox, oy + s), seg(ox, oy + s, ox, oy))
    }

    @Test fun bundledNetHasTheGate() {
        val net = HandwritingNet.bundled
        assertNotNull(net)
        assertTrue("yazı ağında v38 kapı başları var", net!!.hasGate)
        assertEquals(2, HandwritingNet.VERSION)
        // Kapı başları eklendi, karakter başları aynı
        assertEquals(2, net.head(HandwritingNet.WRITING)!!.size)
        assertNotNull(net.head(HandwritingNet.SCRIPT))
        // Kodlama gidiş-dönüş: başlar korunur
        val back = HandwritingNet.decode(net.encode())!!
        assertTrue(back.hasGate)
    }

    @Test fun writingPassesDrawingsDoNot() {
        val words = listOf("merhaba", "kuvvet", "ders notu", "ağaç", "çizgi", "yazı", "kitap", "denklem", "örnek", "sınav")
        var passed = 0
        words.forEachIndexed { i, w ->
            val ink = InkSynth.sample(w, Random(3800 + i))
            val p = Handwriting.writingScore(ink)!!
            if (p >= Handwriting.GATE_MIN) passed++
        }
        println("kapı: yazı $passed/${words.size}")
        assertTrue("yazıların çoğu kapıdan geçer ($passed/${words.size})", passed >= words.size - 1)

        var rejected = 0; var total = 0
        for ((ki, kind) in listOf("rectangle", "house", "smiley", "arrow").withIndex()) for (s in 0 until 3) {
            val ink = InkSynth.drawing(kind, Random(500 + ki * 10 + s))
            if (ink.size < 2) continue
            total++
            if (Handwriting.writingScore(ink)!! < Handwriting.GATE_MIN) rejected++
        }
        val boxP = Handwriting.writingScore(box(100f, 100f, 160f))!!
        println("kapı: çizim reddi $rejected/$total, kutu ${"%.3f".format(boxP)}")
        assertTrue("çizimlerin çoğu kapıdan geçmez ($rejected/$total)", rejected >= total - 1)
        assertTrue("dört kenarla çizilen kutu yazı değil", boxP < Handwriting.GATE_MIN)
    }
}
