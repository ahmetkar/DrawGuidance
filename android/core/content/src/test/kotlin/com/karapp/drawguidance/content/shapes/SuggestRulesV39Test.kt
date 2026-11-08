package com.karapp.drawguidance.content.shapes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v39 öneri kuralları: şekil önerisi hâlâ 4+ eleman; yazı ve çizgi önerisi tek çizgide yalnız çizim miktarı fazlaysa
 * ([SuggestRules.bigLine], [SuggestRules.bigText]); eşikler tuvalin kısa kenarına göre (küçük tuvalde px tabanları).
 */
class SuggestRulesV39Test {
    @Test fun groupMinimumsUnchanged() {
        assertEquals(2, SuggestRules.LINE_MIN)
        assertEquals(2, SuggestRules.TEXT_MIN)
        assertEquals(4, SuggestRules.SHAPE_MIN)
    }

    @Test fun thresholdsAreSane() {
        assertEquals(0.75f, SuggestRules.LINE_SINGLE_LENGTH)
        assertEquals(400f, SuggestRules.LINE_SINGLE_MIN_LENGTH)
        assertEquals(40, SuggestRules.LINE_SINGLE_POINTS)
        assertEquals(0.3f, SuggestRules.TEXT_SINGLE_LENGTH)
        assertEquals(0.06f, SuggestRules.TEXT_SINGLE_HEIGHT)
        assertEquals(0.9f, SuggestRules.TEXT_SINGLE_CONFIDENCE)
        // Tek çizginin yazı güven eşiği çok çizgili yazınınkinden sıkı
        assertTrue(SuggestRules.TEXT_SINGLE_CONFIDENCE > 0.5f)
    }

    @Test fun canvasSide() {
        assertEquals(1000f, SuggestRules.side(1000f, 1400f))
        assertEquals(1080f, SuggestRules.side(1080f, 1920f))
        assertEquals(SuggestRules.DEFAULT_CANVAS_SIDE, SuggestRules.side(0f, 0f))
    }

    @Test fun bigLineNeedsLengthSpanAndPoints() {
        // 1000 px tuval: 750 px yol, 250 px kutu, 40 nokta
        assertTrue(SuggestRules.bigLine(800f, 80, 300f, 300f, 1000f, 1000f))
        assertFalse("kısa", SuggestRules.bigLine(700f, 80, 300f, 300f, 1000f, 1000f))
        assertFalse("az nokta", SuggestRules.bigLine(800f, 30, 300f, 300f, 1000f, 1000f))
        assertFalse("yerinde karalama (küçük kutu)", SuggestRules.bigLine(2000f, 300, 120f, 120f, 1000f, 1000f))
        // Büyük tuvalde aynı çizgi küçük kalır
        assertFalse(SuggestRules.bigLine(800f, 80, 300f, 300f, 4000f, 4000f))
        // Küçük tuvalde px tabanı
        assertFalse(SuggestRules.bigLine(300f, 80, 200f, 200f, 200f, 200f))
        assertTrue(SuggestRules.bigLine(420f, 80, 200f, 200f, 200f, 200f))
    }

    @Test fun bigTextNeedsLengthHeightAndPoints() {
        assertTrue(SuggestRules.bigText(400f, 50, 110f, 160f, 1000f, 1000f))
        assertFalse("olağan not harfi", SuggestRules.bigText(150f, 50, 40f, 45f, 1000f, 1000f))
        assertFalse("alçak", SuggestRules.bigText(400f, 50, 300f, 50f, 1000f, 1000f))
        assertFalse("az nokta", SuggestRules.bigText(400f, 20, 110f, 160f, 1000f, 1000f))
        assertFalse("büyük tuval", SuggestRules.bigText(400f, 50, 110f, 160f, 3000f, 3000f))
    }

    @Test fun pathLength() {
        assertEquals(7f, SuggestRules.pathLength(floatArrayOf(0f, 3f, 3f), floatArrayOf(0f, 4f, 6f)), 1e-4f)
        assertEquals(0f, SuggestRules.pathLength(floatArrayOf(1f), floatArrayOf(1f)), 0f)
    }
}
