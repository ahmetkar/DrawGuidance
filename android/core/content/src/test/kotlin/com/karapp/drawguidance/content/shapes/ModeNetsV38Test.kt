package com.karapp.drawguidance.content.shapes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * v38 öneri kuralları ([SuggestRules]) ve 4+ elemanla yeniden eğitilen mod ağları: sürümler, ağın yalnız 4+ elemanda
 * çalışması, ayrılmış (eğitimde kullanılmayan tohumlu) 4+ elemanlı çizimlerde doğruluk, uzak duran elemanların
 * gruplanmaması.
 */
class ModeNetsV38Test {
    private val index by lazy { ShapeIndex.fromCatalog(ShapeTemplates.catalog) }

    @Test fun rulesAndVersions() {
        assertEquals(2, SuggestRules.LINE_MIN)
        assertEquals(2, SuggestRules.TEXT_MIN)
        assertEquals(4, SuggestRules.SHAPE_MIN)
        assertEquals(SuggestRules.SHAPE_MIN, ShapeComplexity.NET_MIN_ELEMENTS)
        assertTrue(!ShapeComplexity.usesNet(3) && ShapeComplexity.usesNet(4))
        for (mode in ShapeMode.entries) {
            assertEquals(ShapeNet.V38, ShapeNet.modeNetVersion(mode))
            assertEquals("$mode ağı gizli katmanı 320", 320, ShapeNet.bundledFor(mode)!!.hidden)
        }
    }

    @Test fun nearGroupsOnlyCloseBoxes() {
        // Üç yakın kutu ve uzakta bir kutu
        val boxes = listOf(
            floatArrayOf(0f, 0f, 100f, 100f), floatArrayOf(110f, 0f, 200f, 100f), floatArrayOf(0f, 110f, 100f, 200f),
            floatArrayOf(900f, 900f, 1000f, 1000f),
        )
        val g = SuggestRules.near(boxes, 0, SuggestRules.SHAPE_PAD, SuggestRules.SHAPE_MIN_PAD)
        assertEquals(listOf(0, 1, 2), g)
        assertEquals(listOf(3), SuggestRules.near(boxes, 3, SuggestRules.SHAPE_PAD, SuggestRules.SHAPE_MIN_PAD))
        val c = SuggestRules.clusters(boxes, SuggestRules.SHAPE_PAD, SuggestRules.SHAPE_MIN_PAD)
        assertEquals(listOf(listOf(0, 1, 2), listOf(3)), c)
    }

    @Test fun manyElementDrawingsAlwaysHaveFourOrMore() {
        val rnd = Random(38)
        for (t in ShapeTemplates.catalog.take(120)) {
            val s = ShapeAugment.manyElements(t.strokes, rnd)
            assertTrue("${t.key}: ${s.size}", s.count { it.isNotEmpty() } >= SuggestRules.SHAPE_MIN)
        }
    }

    /** Ayrılmış tohumla 4+ elemanlı çizimler: modun ağı doğru şablonu ilk 3'te bulur (eğitim raporu: %96–98). */
    @Test fun netsRecognizeFourPlusElementDrawings() {
        val models = ShapeModeModels(null)
        for (mode in ShapeMode.entries) {
            val net = ShapeNet.bundledFor(mode)!!
            val keys = models.keys(mode, index).toList()
            val byKey = ShapeTemplates.catalog.associateBy { it.key }
            val step = maxOf(1, keys.size / 80)
            var n = 0; var top3 = 0
            for (k in keys.indices step step) {
                val t = byKey[keys[k]] ?: continue
                val s = ShapeAugment.manyElements(t.strokes, Random(990_000L + k))
                val x = ShapeNet.inputOf(s) ?: continue
                val p = net.probs(x)
                val rank = p.indices.sortedByDescending { p[it] }.take(3)
                n++
                if (net.indexOf(keys[k]) in rank) top3++
            }
            val acc = top3.toFloat() / n
            println("$mode: ilk-3 " + "%.1f%%".format(acc * 100) + " ($n)")
            assertTrue("$mode ağı 4+ elemanlı çizimlerde ilk-3 ≥ %85 (${acc * 100})", acc >= 0.85f)
        }
    }
}
