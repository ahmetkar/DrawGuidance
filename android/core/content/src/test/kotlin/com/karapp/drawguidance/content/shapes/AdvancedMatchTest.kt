package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.vector.P
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * v27 gelişmiş tanıma ($Q nokta bulutu, parça ilişki grafiği, IDM, int8 sinir ağı): v26 aramasıyla karşılaştırma,
 * kısmi eşleme (eksik parça önerisi) ve birimler. ADV_TUNE=1 ile ağırlık taraması yapılır.
 */
class AdvancedMatchTest {
    private val index by lazy { ShapeIndex.fromCatalog() }

    @After
    fun restore() { AdvancedWeights.enabled = true }

    private class Eval(val top1: Float, val top3: Float, val far: Float, val farTop1: Float, val scribbleSingle: Float, val scribbleGroup: Float, val micros: Long) {
        override fun toString() = "ilk-1 %.1f%% ilk-3 %.1f%% uzak %.1f%% (uzak ilk-1 %.1f%%) karalama tek/grup p90 %.2f/%.2f, %d µs".format(top1, top3, far, farTop1, scribbleSingle, scribbleGroup, micros)
    }

    private fun scribble(rnd: java.util.Random, strokes: Int): List<List<P>> = List(strokes) {
        var x = rnd.nextFloat() * 400f; var y = rnd.nextFloat() * 400f; var a = rnd.nextFloat() * 6.28f
        val n = 8 + rnd.nextInt(30)
        List(n) { x += cos(a) * 6f; y += sin(a) * 6f; a += (rnd.nextFloat() - 0.5f) * 1.2f; P(x, y) }
    }

    private fun evaluate(perTemplate: Int = 2, seed: Long = 101): Eval {
        val rnd = java.util.Random(seed)
        var top1 = 0; var top3 = 0; var total = 0; var time = 0L
        for (t in ShapeTemplates.all) repeat(perTemplate) {
            val q = ShapeAugment.handDrawn(t.strokes, rnd)
            val mode = if (t.strokeCount == 1) MatchMode.SINGLE else MatchMode.GROUP
            val t0 = System.nanoTime()
            val res = index.search(q, mode, 3)
            time += System.nanoTime() - t0
            total++
            val rank = res.indexOfFirst { it.key == t.key }
            if (rank == 0) top1++
            if (rank in 0..2) top3++
        }
        var far = 0; var farTotal = 0; var farTop1 = 0
        for (t in ShapeTemplates.all) {
            var parts = ShapeSignatures.normalize(t.strokes)
            if (parts.size >= 3 && rnd.nextBoolean()) {
                val longest = parts.indices.maxBy { i -> parts[i].size }
                val drop = (parts.indices - longest).random(kotlin.random.Random(rnd.nextLong()))
                parts = parts.filterIndexed { i, _ -> i != drop }
            }
            parts = parts.map { st -> val dx = (rnd.nextFloat() - 0.5f) * 0.1f; val dy = (rnd.nextFloat() - 0.5f) * 0.1f; st.map { P(it.x + dx, it.y + dy) } }
            val q = ShapeAugment.handDrawn(parts, rnd, 2.2f)
            val res = index.search(q, if (q.size == 1) MatchMode.SINGLE else MatchMode.GROUP, 3)
            farTotal++
            if (res.any { it.key == t.key && it.confidence >= TemplateSuggest.MIN_SIMILARITY }) far++
            if (res.firstOrNull()?.key == t.key) farTop1++
        }
        val single = ArrayList<Float>(); val group = ArrayList<Float>()
        val sr = java.util.Random(11)
        repeat(120) {
            index.search(scribble(sr, 1), MatchMode.SINGLE, 1).firstOrNull()?.let { single += it.confidence }
            index.search(scribble(sr, 2 + sr.nextInt(4)), MatchMode.GROUP, 1).firstOrNull()?.let { group += it.confidence }
        }
        single.sort(); group.sort()
        fun p90(l: List<Float>) = if (l.isEmpty()) 0f else l[((l.size - 1) * 0.9f).toInt()]
        return Eval(top1 * 100f / total, top3 * 100f / total, far * 100f / farTotal, farTop1 * 100f / farTotal, p90(single), p90(group), time / total / 1000)
    }

    /** Gelişmiş yeniden sıralama v26'dan geri kalmamalı; karalamalar eşiğin altında kalmalı. */
    @Test
    fun advancedIsNotWorseThanBaseline() {
        evaluate(1, 5) // ısınma (önbellekler, JIT)
        AdvancedWeights.enabled = false
        val base = evaluate()
        AdvancedWeights.enabled = true
        val adv = evaluate()
        println("v26:      $base")
        println("gelişmiş: $adv")
        assertTrue("ilk-1 ${adv.top1} < ${base.top1}", adv.top1 >= base.top1 - 0.5f)
        assertTrue("uzak ${adv.far} < ${base.far}", adv.far >= base.far - 1.5f)
        assertTrue(adv.scribbleSingle < TemplateSuggest.MIN_SINGLE)
        assertTrue(adv.scribbleGroup < TemplateSuggest.MIN_GROUP)
    }

    /** Ağırlık taraması (yalnız ADV_TUNE=1 ile). */
    @Test
    fun tuneWeights() {
        if (System.getenv("ADV_TUNE") != "1") return
        index.search(ShapeAugment.handDrawn(ShapeTemplates.all.first().strokes, java.util.Random(1)), MatchMode.GROUP, 3)
        AdvancedWeights.enabled = false
        println("v26: ${evaluate(1)}")
        AdvancedWeights.enabled = true
        // Doğru eşleşmelerde ham ölçütlerin dağılımı
        val rnd = java.util.Random(5)
        val cols = Array(3) { ArrayList<Float>() }
        for (t in ShapeTemplates.all.shuffled(rnd).take(150)) {
            val terms = index.advancedTermsFor(ShapeAugment.handDrawn(t.strokes, rnd), t.key) ?: continue
            terms.forEachIndexed { i, v -> if (!v.isNaN()) cols[i] += v }
        }
        cols.forEachIndexed { i, l -> l.sort(); if (l.isNotEmpty()) println("ölçüt $i p10/p50/p90: ${l[l.size / 10]} ${l[l.size / 2]} ${l[l.size * 9 / 10]}") }
        val grid = listOf(
            floatArrayOf(1.2f, 0.6f, 0.8f, 0.35f, 0.12f, 0.5f),
            floatArrayOf(0.6f, 0.3f, 0.4f, 0.2f, 0.12f, 0.5f),
            floatArrayOf(0.3f, 0.2f, 0.2f, 0.1f, 0.08f, 0.4f),
            floatArrayOf(0.6f, 0.3f, 0.4f, 0.2f, 0.25f, 0.3f),
            floatArrayOf(0.8f, 0.4f, 0.2f, 0.3f, 0.2f, 0.3f),
            floatArrayOf(0.4f, 0.2f, 0.6f, 0.15f, 0.15f, 0.3f),
            floatArrayOf(0.4f, 0.3f, 0.3f, 0.3f, 0.3f, 0.25f),
        )
        for (g in grid) {
            AdvancedWeights.q = g[0]; AdvancedWeights.q1 = g[1]; AdvancedWeights.idm = g[2]; AdvancedWeights.graph = g[3]; AdvancedWeights.net = g[4]; AdvancedWeights.cap = g[5]
            println("${g.joinToString("/")}: ${evaluate(1)}")
        }
    }

    /** Kısmi eşleme: çok parçalı bir şablonun parçalarının bir kısmı verilince o şablon (ilk 4'te) önerilir. */
    @Test
    fun partialMatchesSuggestWholeShapes() {
        val rnd = java.util.Random(31)
        val pool = ShapeTemplates.all.filter { it.strokeCount in 3..8 && TemplatePopularity.of(it.key) >= 4 }
        var hit = 0; var total = 0; var time = 0L
        index.partialMatches(ShapeAugment.handDrawn(ShapeSignatures.normalize(pool[0].strokes).take(2), rnd), 4) // ısınma (grafik önbelleği)
        for (t in pool) {
            val parts = ShapeSignatures.normalize(t.strokes)
            // En uzun iki parça (çizimin ana gövdesi) seçilmiş gibi
            val keep = parts.indices.sortedByDescending { i -> parts[i].size }.take(2).sorted()
            val q = ShapeAugment.handDrawn(keep.map { parts[it] }, rnd, 0.6f)
            val t0 = System.nanoTime()
            val res = index.partialMatches(q, limit = 4, minConfidence = 0f)
            time += System.nanoTime() - t0
            total++
            if (res.any { it.key == t.key }) hit++
            res.firstOrNull()?.let { r -> assertEquals(q.size, r.mapping.count { it >= 0 }) }
        }
        println("kısmi eşleme: ${hit * 100f / total}% ($total şablon, ortalama ${time / total.coerceAtLeast(1) / 1000} µs)")
        assertTrue("${hit * 100f / total}", hit >= total * 0.35f)
    }

    @Test
    fun hungarianFindsOptimalAssignment() {
        val cost = arrayOf(floatArrayOf(4f, 1f, 3f), floatArrayOf(2f, 0f, 5f), floatArrayOf(3f, 2f, 2f))
        val a = Hungarian.solve(cost)
        val total = a.indices.sumOf { cost[it][a[it]].toDouble() }
        assertEquals(5.0, total, 1e-4)
        assertEquals(3, a.toSet().size)
    }

    @Test
    fun qDollarIsSmallForSameShapeAndLargeForDifferent() {
        val rnd = java.util.Random(2)
        val circle = ShapeTemplates.all.first { it.strokeCount == 1 && ShapeSignatures.isClosed(ShapeSignatures.polys(ShapeSignatures.normalize(it.strokes))[0]) }
        fun cloud(s: List<List<P>>) = QDollar.cloud(ShapeSignatures.polys(ShapeSignatures.normalize(s)))
        val a = cloud(circle.strokes)
        val b = cloud(ShapeAugment.handDrawn(circle.strokes, rnd))
        val line = cloud(listOf(listOf(P(0f, 0f), P(100f, 100f))))
        assertNotNull(a); assertNotNull(b); assertNotNull(line)
        val same = QDollar.distance(a!!, b!!)
        val diff = QDollar.distance(a, line!!)
        println("\$Q aynı ${"%.3f".format(same)} farklı ${"%.3f".format(diff)}")
        assertTrue(same < diff)
    }

    @Test
    fun shapeNetIsBundledAndFast() {
        val net = ShapeNet.bundled
        assertNotNull("shape_net.bin uygulamayla gelmeli", net)
        val rnd = java.util.Random(4)
        var hit = 0; var n = 0; var time = 0L
        for (t in ShapeTemplates.all.shuffled(rnd).take(200)) {
            val x = ShapeNet.inputOf(ShapeAugment.handDrawn(t.strokes, rnd)) ?: continue
            val t0 = System.nanoTime()
            val p = net!!.probs(x)
            time += System.nanoTime() - t0
            n++
            if (net.keys[p.indices.maxBy { p[it] }] == t.key) hit++
        }
        println("ağ ilk-1: ${hit * 100f / n}%, ortalama ${time / n / 1000} µs")
        assertTrue(hit >= n * 0.85f)
    }

    private fun poly(vararg xy: Float): List<P> = (xy.indices step 2).map { P(xy[it], xy[it + 1]) }.let { it + it.first() }
    private fun ring(n: Int, r0: Float, r1: Float = r0): List<P> = List(n * 2 + 1) { k ->
        val a = Math.PI * k / n - Math.PI / 2; val r = if (k % 2 == 0) r0 else r1
        P(0.5f + r * cos(a).toFloat(), 0.5f + r * sin(a).toFloat())
    }

    private val vectorItems = listOf(
        VectorShapeMatcher.Item("SQUARE", listOf(poly(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))),
        VectorShapeMatcher.Item("TRIANGLE", listOf(poly(0.5f, 0f, 1f, 1f, 0f, 1f))),
        VectorShapeMatcher.Item("CIRCLE", listOf(ring(24, 0.5f))),
        VectorShapeMatcher.Item("STAR", listOf(ring(5, 0.5f, 0.2f))),
        VectorShapeMatcher.Item("DIAMOND", listOf(poly(0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, 0.5f))),
        // Kutuya esneyenler (en-boydan bağımsız)
        VectorShapeMatcher.Item("RECT", listOf(poly(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)), stretch = true),
        VectorShapeMatcher.Item("ELLIPSE", listOf(ring(24, 0.5f)), stretch = true),
    )

    /** Dört ayrı doğruyla çizilen kare ve beş köşeli yıldız hazır vektör şekillerle eşleşir; karalamalar eşleşmez. */
    @Test
    fun vectorShapesMatchMultiStrokeDrawings() {
        val m = VectorShapeMatcher(vectorItems)
        val rnd = java.util.Random(8)
        val square = listOf(listOf(P(0f, 0f), P(1f, 0f)), listOf(P(1f, 0f), P(1f, 1f)), listOf(P(1f, 1f), P(0f, 1f)), listOf(P(0f, 1f), P(0f, 0f)))
        var ok = 0
        repeat(20) {
            val res = m.match(ShapeAugment.handDrawn(square, rnd, 0.8f))
            if (res.firstOrNull()?.id in setOf("SQUARE", "RECT")) ok++
        }
        val star = ring(5, 0.5f, 0.2f)
        val starParts = listOf(star.subList(0, 6), star.subList(5, 11))
        var okStar = 0
        repeat(20) { if (m.match(ShapeAugment.handDrawn(starParts, rnd, 0.8f)).firstOrNull()?.id == "STAR") okStar++ }
        // 2:1 dikdörtgen dört ayrı doğruyla: kare değil, esneyen dikdörtgen
        val wide = listOf(listOf(P(0f, 0f), P(2f, 0f)), listOf(P(2f, 0f), P(2f, 1f)), listOf(P(2f, 1f), P(0f, 1f)), listOf(P(0f, 1f), P(0f, 0f)))
        var okRect = 0
        repeat(20) { if (m.match(ShapeAugment.handDrawn(wide, rnd, 0.8f)).firstOrNull()?.id == "RECT") okRect++ }
        var false0 = 0
        val sr = java.util.Random(12)
        repeat(60) { if (m.match(scribble(sr, 2 + sr.nextInt(4))).isNotEmpty()) false0++ }
        println("vektör şekil: kare $ok/20, yıldız $okStar/20, 2:1 dikdörtgen $okRect/20, karalamada yanlış $false0/60")
        assertTrue(ok >= 14); assertTrue(okStar >= 14); assertTrue(okRect >= 14); assertTrue(false0 <= 6)
    }

    /** Eksik parçalar seçili parçanın kutusuna göre yerleşir (ör. yüzün dış çemberine göre gözler içeride). */
    @Test
    fun partFitPlacesMissingPartsInsideMatchedBox() {
        val face = listOf(ring(20, 0.5f), ring(8, 0.06f).map { P(it.x - 0.18f, it.y - 0.12f) }, ring(8, 0.06f).map { P(it.x + 0.18f, it.y - 0.12f) })
        val r = PartialResult("yuz", "Yüz", "yuz", 0.8f, intArrayOf(0), face)
        // Kullanıcının çemberi: 200..400 kutusunda
        val query = listOf(ring(20, 0.5f).map { P(200f + it.x * 200f, 200f + it.y * 200f) })
        val parts = PartFit.missing(r, query)
        assertEquals(2, parts.size)
        parts.flatten().forEach { p -> assertTrue("$p", p.x in 200f..400f && p.y in 200f..400f) }
        val left = parts[0].map { it.x }.average(); val right = parts[1].map { it.x }.average()
        assertTrue(left < 300.0 && right > 300.0)
    }
}
