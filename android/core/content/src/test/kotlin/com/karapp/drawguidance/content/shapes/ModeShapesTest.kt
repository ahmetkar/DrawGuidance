package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.vector.P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * v29 moda özel tahmin: her modun kapsamı (resimde not/çizgi roman şablonları yok, notta eksen ve Venn var), moda özel
 * şablonların modun ağıyla tanınması, cihazda eğitimin (kabul ve ret) olasılıkları değiştirmesi ve modelin dosyaya
 * yazılıp okunması; modlar birbirini etkilemez.
 */
class ModeShapesTest {
    private val index by lazy { ShapeIndex.fromCatalog(ShapeTemplates.catalog) }

    private fun drawn(key: String, seed: Long): List<List<P>> =
        ShapeAugment.handDrawn(ShapeTemplates.byKey(key)!!.strokes, java.util.Random(seed))

    /** v38 4+ elemanlı el çizimi (şekil önerisinin ve ağın kuralı; az çizgili şablon parçalara bölünür). */
    private fun many(key: String, seed: Long): List<List<P>> =
        ShapeAugment.manyElements(ShapeTemplates.byKey(key)!!.strokes, java.util.Random(seed))

    private fun modeOf(t: ShapeTemplate) = if (t.strokeCount == 1) MatchMode.SINGLE else MatchMode.GROUP

    @Test fun catalogHasModeOnlyTemplatesOutsideGeneralList() {
        assertTrue(ShapeTemplates.modeOnly.size >= 30)
        assertTrue(ShapeTemplates.modeOnly.all { ModeCatalog.isModeOnly(it.key) })
        assertTrue(ShapeTemplates.all.none { ModeCatalog.isModeOnly(it.key) })
        assertEquals(ShapeTemplates.all.size + ShapeTemplates.modeOnly.size, ShapeTemplates.catalog.size)
        println("moda özel şablonlar: not ${ShapeTemplates.modeOnly.count { it.key.startsWith("ders.") }}, çizgi roman ${ShapeTemplates.modeOnly.count { it.key.startsWith("roman.") }}")
    }

    @Test fun scopesDifferPerMode() {
        val models = ShapeModeModels(null)
        val art = models.keys(ShapeMode.ART, index)
        val notes = models.keys(ShapeMode.NOTES, index)
        val comic = models.keys(ShapeMode.COMIC, index)
        assertTrue("resimde ders şablonu yok", art.none { it.startsWith("ders.") || it.startsWith("roman.") })
        assertTrue("ders.eksen" in notes && "ders.venn" in notes && "roman.konusma" !in notes)
        assertTrue("roman.konusma" in comic && "ders.eksen" !in comic)
        assertTrue("notta yüz şablonu yok", notes.none { it.startsWith("yuz.") })
        assertTrue(notes.size < art.size)
        println("kapsam: resim ${art.size}, not ${notes.size}, çizgi roman ${comic.size}")
    }

    @Test fun generalSearchNeverReturnsModeOnly() {
        val q = drawn("ders.venn", 3)
        val res = index.search(q, MatchMode.GROUP, 5)
        assertTrue(res.none { ModeCatalog.isModeOnly(it.key) })
    }

    @Test fun modeTemplatesAreRecognizedInTheirMode() {
        val models = ShapeModeModels(null)
        val t0 = System.currentTimeMillis()
        val notes = models.scope(ShapeMode.NOTES, index)
        val comic = models.scope(ShapeMode.COMIC, index)
        val kids = models.scope(ShapeMode.KIDS, index)
        println("model kurulumu (üç mod): ${System.currentTimeMillis() - t0} ms")
        assertNotNull(notes.net); assertNotNull(comic.net); assertNotNull(kids.net)
        var top1 = 0; var top3 = 0; var total = 0
        var kidTop3 = 0; var kidTotal = 0
        val rnd = java.util.Random(77)
        for (t in ShapeTemplates.modeOnly) repeat(3) {
            val scope = when {
                t.key.startsWith("ders.") -> notes
                t.key.startsWith(ModeCatalog.KIDS_PREFIX) -> kids
                else -> comic
            }
            val q = ShapeAugment.handDrawn(t.strokes, rnd)
            val res = index.search(q, modeOf(t), 3, scope = scope)
            assertTrue("kapsam dışı sonuç yok", res.all { it.key in scope.keys })
            total++
            val rank = res.indexOfFirst { it.key == t.key }
            if (rank == 0) top1++
            if (rank in 0..2) top3++
            if (scope === kids) { kidTotal++; if (rank in 0..2) kidTop3++ }
        }
        println("v34 çocuk şablonları: ilk-3 %.1f%% (%d çizim)".format(100f * kidTop3 / kidTotal, kidTotal))
        println("moda özel şablonlar: ilk-1 %.1f%%, ilk-3 %.1f%% (%d çizim)".format(100f * top1 / total, 100f * top3 / total, total))
        assertTrue("ilk-3 ≥ %75", top3 >= total * 0.75f)
    }

    @Test fun modeNetLearnsFromFeedbackWithoutTouchingOtherModes() {
        val dir = Files.createTempDirectory("modes").toFile()
        val models = ShapeModeModels(dir)
        // v30 ağ yalnız çok elemanlı (v38: 4+ çizgili) çizimlerden öğrenir: kullanıcı "Zzz"ye benzeyen çizimlerini
        // "Şaşkınlık çizgileri" olarak kabul ediyor
        val drawnKey = "roman.uyku"
        val key = "roman.saskinlik"
        val q = many(drawnKey, 11)
        assertTrue(q.size >= ShapeComplexity.NET_MIN_ELEMENTS)
        val x = ShapeNet.inputOf(q)!!
        // Ret: ağın bu çizimdeki ilk tahmini kapatılınca olasılığı azalır
        val top = models.model(ShapeMode.COMIC, index).net.let { n -> val p = n.probs(x); n.keys[p.indices.maxBy { p[it] }] }
        val px = models.model(ShapeMode.COMIC, index).net.let { it.probs(x)[it.indexOf(top)] }
        models.rejected(ShapeMode.COMIC, index, listOf(top), q)
        val py = models.model(ShapeMode.COMIC, index).net.let { it.probs(x)[it.indexOf(top)] }
        println("ret: p($top) $px → $py")
        assertTrue("retten sonra azaldı: $px → $py", py < px)
        val before = models.model(ShapeMode.COMIC, index).net.let { it.probs(x)[it.indexOf(key)] }
        val artBefore = models.model(ShapeMode.ART, index).net.probs(x).copyOf()
        repeat(4) { i -> models.accepted(ShapeMode.COMIC, index, key, many(drawnKey, 20L + i)) }
        val after = models.model(ShapeMode.COMIC, index).net.let { it.probs(x)[it.indexOf(key)] }
        println("kabul: p($key) $before → $after")
        assertTrue("kabulden sonra olasılık arttı: $before → $after", after > before * 2f)
        assertTrue("resim modu değişmedi", artBefore.contentEquals(models.model(ShapeMode.ART, index).net.probs(x)))
        // Az elemanlı (tek çizgi) geri bildirim ağı değiştirmez, yalnız sayaçlara yazılır
        val one = drawn("roman.ter_damlasi", 5)
        val x1 = ShapeNet.inputOf(one)!!
        val p1 = models.model(ShapeMode.COMIC, index).net.probs(x1).copyOf()
        models.accepted(ShapeMode.COMIC, index, "roman.unlem", one)
        assertTrue("tek çizgide ağ değişmez", p1.contentEquals(models.model(ShapeMode.COMIC, index).net.probs(x1)))
        val info = models.info(ShapeMode.COMIC, index)
        assertEquals(5, info.accepted); assertEquals(1, info.rejected); assertEquals(5, info.trainedSamples)
        // Dosyadan yeniden okunur (öğrenilenler korunur)
        val reloaded = ShapeModeModels(dir)
        val again = reloaded.model(ShapeMode.COMIC, index)
        assertEquals(5, again.accepted)
        assertEquals(after.toDouble(), again.net.let { it.probs(x)[it.indexOf(key)] }.toDouble(), 0.02)
        // Sıfırlama
        reloaded.reset(ShapeMode.COMIC)
        assertFalse(java.io.File(dir, "shape_mode_comic.bin").exists())
        assertEquals(0, reloaded.info(ShapeMode.COMIC, index).accepted)
    }

    /** v30 karmaşıklığa göre algoritma: ağ yalnız 3+ çizgili sorgularda çağrılır. */
    @Test fun netRunsOnlyForManyElements() {
        val models = ShapeModeModels(null)
        val real = models.scope(ShapeMode.ART, index)
        var calls = 0
        val counting = object : ShapeClassifier {
            override val keys = real.net!!.keys
            override fun indexOf(key: String) = real.net!!.indexOf(key)
            override fun probs(x: FloatArray): FloatArray { calls++; return real.net!!.probs(x) }
        }
        val scope = ShapeScope(ShapeMode.ART, real.keys, counting, real.netWeight, emptyMap(), emptyMap())
        index.search(drawn("doga.bulut", 1), MatchMode.SINGLE, 3, scope = scope)
        index.search(drawn("meyve.armut", 2), MatchMode.GROUP, 3, scope = scope)
        // v38 3 çizgide de ağ yok (şekil önerisi ve ağ birbirine yakın 4+ elemanda)
        val three = ShapeAugment.atLeast(drawn("hayvan.kedi_yuz", 3), 3, java.util.Random(3)).take(3)
        assertEquals(3, three.size)
        index.search(three, MatchMode.GROUP, 3, scope = scope)
        assertEquals("1–3 çizgide ağ yok", 0, calls)
        val four = many("hayvan.kedi_yuz", 3)
        assertTrue(four.size >= 4)
        index.search(four, MatchMode.GROUP, 3, scope = scope)
        assertTrue("4+ çizgide ağ var", calls > 0)
        assertEquals(ShapeComplexity.Tier.FEW, ShapeComplexity.tier(3))
        assertEquals(ShapeComplexity.Tier.MANY, ShapeComplexity.tier(4))
        assertEquals(listOf("Geometrik uydurma"), ShapeComplexity.algorithms(ShapeComplexity.Tier.PRIMITIVE))
        // Uygulamayla gelen moda özel ağlar her modun bütün şablonlarını tanır
        for (mode in ShapeMode.entries) {
            val net = ShapeNet.bundledFor(mode)
            assertNotNull("$mode ağı", net)
            assertEquals(models.keys(mode, index).toList(), net!!.keys)
        }
    }

    @Test fun partialMatchesStayInScope() {
        val models = ShapeModeModels(null)
        val scope = models.scope(ShapeMode.NOTES, index)
        val circle = listOf(ShapeTemplates.byKey("ders.venn")!!.strokes[0].map { P(it.x * 300f, it.y * 300f) })
        val res = index.partialMatches(circle, limit = 5, minConfidence = 0.2f, scope = scope)
        assertTrue(res.all { it.key in scope.keys })
        println("not modunda tek daire → " + res.joinToString { "${it.key} %.2f".format(it.confidence) })
    }
}
