package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.Preview
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** v21 şekil veritabanı: katalog, imzalar, arama doğruluğu (el çizimi benzetimiyle) ve hız. */
class ShapeTemplatesTest {

    private val index by lazy { ShapeIndex.fromCatalog() }

    @Test
    fun catalogIsLargeAndConsistent() {
        val all = ShapeTemplates.all
        println("şablon sayısı: ${all.size}, kategoriler: ${ShapeTemplates.categories}")
        ShapeTemplates.categories.forEach { c -> println("  $c: ${all.count { it.category == c }}") }
        assertTrue(all.size >= 200)
        assertEquals(all.size, all.map { it.key }.toSet().size)
        all.forEach { t ->
            assertTrue(t.key, t.strokes.isNotEmpty() && t.strokes.all { it.size >= 2 })
            t.strokes.flatten().forEach { p -> assertTrue("${t.key} $p", p.x in -0.4f..1.4f && p.y in -0.4f..1.4f) }
        }
        assertTrue(all.count { it.solid != null } >= 9)
        listOf("Yüz", "Saç", "Çatı", "Kapı", "Sandalye", "Meyve", "Sebze", "Hayvan uzvu").forEach { c -> assertTrue(c, all.any { it.category == c }) }
    }

    @Test
    fun encodingRoundTrips() {
        val t = ShapeTemplates.byKey("yapi.ev.besik")!!
        val sig = ShapeSignatures.of(t.strokes)
        assertEquals(ShapeSignatures.VARIANTS * ShapeSignature.DIM, sig.descriptors.size)
        val geo = ShapeSignatures.decodeGeometry(ShapeSignatures.encodeGeometry(sig.geometry))
        assertEquals(sig.geometry.size, geo.size)
        sig.geometry.zip(geo).forEach { (a, b) -> a.zip(b).forEach { (p, q) -> assertTrue(hypot(p.x - q.x, p.y - q.y) < 1e-3f) } }
        val desc = ShapeSignatures.decodeDescriptors(ShapeSignatures.encodeDescriptors(sig.descriptors))
        var err = 0f
        var norm = 0f
        for (i in 0 until ShapeSignature.DIM) { err += (desc[i] - sig.descriptors[i]).let { it * it }; norm += sig.descriptors[i] * sig.descriptors[i] }
        assertTrue("nicemleme hatası ${kotlin.math.sqrt(err / norm)}", kotlin.math.sqrt(err / norm) < 0.03f)
    }

    /** Elle çizilmiş gibi bozulma: döndürme, esneme, dalgalanma, titreme, kapalı çizgide başlangıç kayması ve boşluk, sıra ve yön. */
    private fun handDrawn(strokes: List<List<P>>, rnd: java.util.Random, amount: Float = 1f): List<List<P>> {
        val size = 200f + rnd.nextFloat() * 200f
        val ox = 100f + rnd.nextFloat() * 300f; val oy = 100f + rnd.nextFloat() * 300f
        val rot = ((rnd.nextFloat() - 0.5f) * 10f * amount) * PI.toFloat() / 180f
        val sx = 1f + (rnd.nextFloat() - 0.5f) * 0.24f * amount
        val c = cos(rot); val s = sin(rot)
        fun map(p: P): P {
            val x = (p.x - 0.5f) * size * sx; val y = (p.y - 0.5f) * size
            return P(ox + c * x - s * y, oy + s * x + c * y)
        }
        val out = strokes.map { st ->
            // Küçük parçalar (göz, nokta) elle çizimde çoğu zaman nokta ya da minik karalama olur
            val ext = maxOf(st.maxOf { it.x } - st.minOf { it.x }, st.maxOf { it.y } - st.minOf { it.y })
            if (strokes.size > 1 && ext < 0.12f && rnd.nextBoolean()) {
                val c = map(P(st.map { it.x }.average().toFloat(), st.map { it.y }.average().toFloat()))
                return@map listOf(c, P(c.x + 1.5f, c.y + 1f), P(c.x + 0.5f, c.y + 2f))
            }
            var pts = TemplateFit.densify(st.map(::map), 3f)
            val closed = st.size > 3 && hypot(st.first().x - st.last().x, st.first().y - st.last().y) < 0.02f
            if (closed && pts.size > 8) {
                val k = rnd.nextInt(pts.size - 1)
                pts = pts.subList(k, pts.size - 1) + pts.subList(0, k + 1)
                // küçük boşluk ya da taşma
                val cut = (rnd.nextFloat() * 0.03f * pts.size).toInt()
                if (cut > 0 && rnd.nextBoolean()) pts = pts.dropLast(cut)
            }
            val f1 = rnd.nextFloat() * 3f + 1f; val f2 = rnd.nextFloat() * 7f + 3f
            val ph1 = rnd.nextFloat() * 6.28f; val ph2 = rnd.nextFloat() * 6.28f
            val amp = size * 0.012f * amount
            var total = 0f
            val lens = FloatArray(pts.size)
            for (i in 1 until pts.size) { total += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y); lens[i] = total }
            val wob = pts.mapIndexed { i, p ->
                val t = if (total > 0f) lens[i] / total else 0f
                val nx = amp * sin(f1 * 6.28f * t + ph1) + amp * 0.5f * sin(f2 * 6.28f * t + ph2) + (rnd.nextFloat() - 0.5f) * 0.8f
                val ny = amp * cos(f1 * 6.28f * t + ph2) + amp * 0.5f * cos(f2 * 6.28f * t + ph1) + (rnd.nextFloat() - 0.5f) * 0.8f
                P(p.x + nx, p.y + ny)
            }
            if (rnd.nextBoolean()) wob.reversed() else wob
        }
        return out.shuffled(rnd)
    }

    private fun scribble(rnd: java.util.Random, strokes: Int): List<List<P>> = List(strokes) {
        var x = 200f + rnd.nextFloat() * 200f; var y = 200f + rnd.nextFloat() * 200f
        var a = rnd.nextFloat() * 6.28f
        val n = 20 + rnd.nextInt(60)
        List(n) { x += cos(a) * 6f; y += sin(a) * 6f; a += (rnd.nextFloat() - 0.5f) * 1.2f; P(x, y) }
    }

    @Test
    fun recognizesHandDrawnTemplates() {
        val rnd = java.util.Random(7)
        var top1 = 0; var top3 = 0; var total = 0
        val correctConf = ArrayList<Float>()
        val wrongTopConf = ArrayList<Float>()
        val fails = ArrayList<String>()
        var time = 0L
        for (t in ShapeTemplates.all) {
            val mode = if (t.strokeCount == 1) MatchMode.SINGLE else MatchMode.GROUP
            repeat(3) {
                val q = handDrawn(t.strokes, rnd)
                val t0 = System.nanoTime()
                val res = index.search(q, mode, limit = 3)
                time += System.nanoTime() - t0
                total++
                val rank = res.indexOfFirst { it.key == t.key }
                if (rank == 0) { top1++; correctConf += res[0].confidence } else {
                    res.firstOrNull()?.let { wrongTopConf += it.confidence }
                    if (fails.size < 60) fails += "${t.key} → ${res.joinToString { "${it.key}(${"%.2f".format(it.confidence)})" }}"
                }
                if (rank in 0..2) top3++
            }
        }
        correctConf.sort(); wrongTopConf.sort()
        fun pct(l: List<Float>, p: Float) = if (l.isEmpty()) Float.NaN else l[((l.size - 1) * p).toInt()]
        println("ilk-1: ${top1 * 100f / total}%, ilk-3: ${top3 * 100f / total}% ($total çizim), ortalama arama: ${time / total / 1000} µs")
        println("doğru güven p10/p50/p90: ${pct(correctConf, 0.1f)} ${pct(correctConf, 0.5f)} ${pct(correctConf, 0.9f)}")
        println("yanlış ilk güven p50/p90: ${pct(wrongTopConf, 0.5f)} ${pct(wrongTopConf, 0.9f)}")
        fails.forEach { println("  ✗ $it") }
        assertTrue("ilk-1 doğruluk ${top1 * 100f / total}", top1 >= total * 0.8f)
        assertTrue("ilk-3 doğruluk ${top3 * 100f / total}", top3 >= total * 0.92f)
    }

    /**
     * v22 uzak çizimler: güçlü bozulma (±11° döndürme, ±%26 esneme, 2 kat dalgalanma), parçalar yerinden kaymış ve
     * çok parçalı şablonların yarısında bir parça eksik. Doğru şablon ilk 3 içinde %50 benzerliği geçmeli.
     */
    @Test
    fun farDrawingsStillGetPredictions() {
        val rnd = java.util.Random(19)
        var predicted = 0; var anyPrediction = 0; var total = 0
        val sims = ArrayList<Float>()
        for (t in ShapeTemplates.all) {
            repeat(2) {
                var parts = ShapeSignatures.normalize(t.strokes)
                if (parts.size >= 3 && rnd.nextBoolean()) {
                    val longest = parts.indices.maxBy { i -> parts[i].size }
                    val drop = (parts.indices - longest).random(kotlin.random.Random(rnd.nextLong()))
                    parts = parts.filterIndexed { i, _ -> i != drop }
                }
                parts = parts.map { st -> val dx = (rnd.nextFloat() - 0.5f) * 0.1f; val dy = (rnd.nextFloat() - 0.5f) * 0.1f; st.map { P(it.x + dx, it.y + dy) } }
                val q = handDrawn(parts, rnd, 2.2f)
                val mode = if (q.size == 1) MatchMode.SINGLE else MatchMode.GROUP
                val res = index.search(q, mode, limit = 3)
                total++
                val hit = res.firstOrNull { it.key == t.key }
                hit?.let { sims += it.confidence }
                if (hit != null && hit.confidence >= TemplateSuggest.MIN_SIMILARITY) predicted++
                if ((res.firstOrNull()?.confidence ?: 0f) >= TemplateSuggest.MIN_SIMILARITY) anyPrediction++
            }
        }
        sims.sort()
        fun pct(l: List<Float>, p: Float) = if (l.isEmpty()) Float.NaN else l[((l.size - 1) * p).toInt()]
        println("uzak çizim: doğru tahmin (ilk 3, ≥%50) ${predicted * 100f / total}%, herhangi bir tahmin ${anyPrediction * 100f / total}% ($total çizim)")
        println("uzak çizim doğru şablon benzerliği p10/p50/p90: ${pct(sims, 0.1f)} ${pct(sims, 0.5f)} ${pct(sims, 0.9f)}")
        assertTrue(predicted >= total * 0.7f)
    }

    /**
     * v23 karışık çizimler: sık çizilen bir şeklin (ev, güneş, kalp…) yanına/üstüne fazladan 1–2 parça (karalama, çizgi,
     * küçük daire) eklenmiş. İç içe arama (sık şekiller için parça çıkararak alt küme araması + popülerlik önseli) doğru
     * şekli ilk 3'te %50'nin üstünde bulmalı.
     */
    @Test
    fun mixedDrawingsFindFrequentShapes() {
        val rnd = java.util.Random(23)
        var hit = 0; var total = 0; var time = 0L
        val frequent = ShapeTemplates.all.filter { TemplatePopularity.of(it.key) >= 4 }
        for (t in frequent) {
            repeat(3) {
                val base = ShapeSignatures.normalize(t.strokes)
                val extras = List(1 + rnd.nextInt(2)) {
                    val cx = 0.1f + rnd.nextFloat() * 0.9f; val cy = 0.1f + rnd.nextFloat() * 0.9f; val r = 0.08f + rnd.nextFloat() * 0.12f
                    if (rnd.nextBoolean()) List(14) { k -> val a = 6.283f * k / 13; P(cx + r * cos(a), cy + r * sin(a)) }
                    else listOf(P(cx - r, cy - r * rnd.nextFloat()), P(cx + r, cy + r * rnd.nextFloat()))
                }
                val q = handDrawn(base + extras, rnd)
                val t0 = System.nanoTime()
                val res = index.search(q, MatchMode.GROUP, limit = 3)
                time += System.nanoTime() - t0
                total++
                if (res.any { it.key == t.key && it.confidence >= TemplateSuggest.MIN_SIMILARITY }) hit++
            }
        }
        println("karışık çizim: sık şekil bulundu ${hit * 100f / total}% ($total çizim, ortalama ${time / total / 1000} µs)")
        assertTrue("${hit * 100f / total}", hit >= total * 0.6f)
    }

    @Test
    fun scribblesStayBelowThreshold() {
        val rnd = java.util.Random(11)
        val single = ArrayList<Float>(); val group = ArrayList<Float>()
        repeat(150) {
            index.search(scribble(rnd, 1), MatchMode.SINGLE, 1).firstOrNull()?.let { single += it.confidence }
            index.search(scribble(rnd, 2 + rnd.nextInt(4)), MatchMode.GROUP, 1).firstOrNull()?.let { group += it.confidence }
        }
        single.sort(); group.sort()
        fun pct(l: List<Float>, p: Float) = if (l.isEmpty()) 0f else l[((l.size - 1) * p).toInt()]
        println("karalama tek p50/p90/p98: ${pct(single, 0.5f)} ${pct(single, 0.9f)} ${pct(single, 0.98f)}")
        println("karalama grup p50/p90/p98: ${pct(group, 0.5f)} ${pct(group, 0.9f)} ${pct(group, 0.98f)}")
        assertTrue(pct(single, 0.9f) < TemplateSuggest.MIN_SINGLE)
        assertTrue(pct(group, 0.9f) < TemplateSuggest.MIN_GROUP)
    }

    @Test
    fun lshAgreesWithFullScan() {
        val rnd = java.util.Random(3)
        var same = 0; var n = 0
        for (t in ShapeTemplates.all.filter { it.strokeCount > 1 }.take(80)) {
            val q = handDrawn(t.strokes, rnd)
            val a = index.search(q, MatchMode.GROUP, 1, useLsh = false).firstOrNull()?.key
            val b = index.search(q, MatchMode.GROUP, 1, useLsh = true).firstOrNull()?.key
            n++; if (a == b) same++
        }
        println("LSH uyumu: $same / $n")
        assertTrue(same >= n * 0.9f)
    }

    @Test
    fun learningAddsUserExemplarAndPrior() {
        val t = ShapeTemplates.byKey("meyve.armut")!!
        val rnd = java.util.Random(5)
        val drawn = handDrawn(t.strokes, rnd, 1.6f)
        val learned = index.plus(IndexEntry(9999, t.key, ShapeSignatures.of(drawn, augment = false), user = true))
        assertEquals(1, learned.userExemplars(t.key))
        val res = learned.search(drawn, MatchMode.GROUP, 1)
        assertEquals(t.key, res.first().key)
        // Öğrenilen örnekle aynı çizim neredeyse tam güvenle bulunur
        assertTrue(res.first().confidence > 0.8f)
        learned.recordFeedback(t.key, accepted = true)
        assertEquals(1, learned.usageOf(t.key))
    }

    @Test
    fun fitKeepsBoxAndDensifies() {
        val g = ShapeTemplates.byKey("yapi.kapi.duz")!!.strokes
        val fitted = TemplateFit.fit(ShapeSignatures.normalize(g), 100f, 50f, 160f, 170f)
        val xs = fitted.flatten().map { it.x }; val ys = fitted.flatten().map { it.y }
        assertEquals(130f, (xs.min() + xs.max()) / 2f, 1f)
        assertEquals(110f, (ys.min() + ys.max()) / 2f, 1f)
        val dense = TemplateFit.densify(fitted[0], 4f)
        for (i in 1 until dense.size) assertTrue(hypot(dense[i].x - dense[i - 1].x, dense[i].y - dense[i - 1].y) <= 4.01f)
    }

    @Test
    fun groupingCollectsNearbyStrokes() {
        val boxes = listOf(
            floatArrayOf(0f, 0f, 100f, 100f), floatArrayOf(30f, 30f, 40f, 40f), floatArrayOf(60f, 30f, 70f, 40f),
            floatArrayOf(500f, 500f, 560f, 560f), floatArrayOf(40f, 70f, 70f, 80f),
        )
        assertEquals(listOf(0, 1, 2, 4), TemplateGrouping.cluster(boxes, 4))
        assertEquals(listOf(3), TemplateGrouping.cluster(boxes, 3))
        // Yan yana iki ayrı çizim (ev ve yüz) birleşmez; yüzün içindeki gözler ve ağız katılır
        val scene = listOf(
            floatArrayOf(97f, 90f, 486f, 480f), floatArrayOf(622f, 76f, 951f, 402f),
            floatArrayOf(747f, 197f, 750f, 200f), floatArrayOf(711f, 268f, 867f, 318f),
        )
        assertEquals(listOf(1, 2, 3), TemplateGrouping.cluster(scene, 1))
        assertEquals(listOf(0), TemplateGrouping.cluster(scene, 0))
    }

    /** Kayıt listesi (belge için; -PpreviewDir ile markdown yazılır). */
    @Test
    fun dumpCatalog() {
        val dir = Preview.dir ?: return
        val sb = StringBuilder()
        ShapeTemplates.categories.forEach { c ->
            val list = ShapeTemplates.all.filter { it.category == c }
            sb.append("\n#### ").append(c).append(" (").append(list.size).append(" kayıt)\n\n")
            sb.append("| Anahtar | Ad | Grup | Çizgi | Açıklama |\n|---|---|---|---|---|\n")
            list.forEach { t ->
                val solid = t.solid?.let { " → 3B: $it" }.orEmpty()
                sb.append("| `${t.key}` | ${t.name} | ${t.group} | ${t.strokeCount} | ${t.description}$solid |\n")
            }
        }
        java.io.File(dir, "shape_catalog.md").writeText(sb.toString())
    }

    /** Katalogun önizleme sayfaları (-PpreviewDir ile). */
    @Test
    fun previewSheets() {
        Preview.dir ?: return
        val cell = 150; val cols = 10
        ShapeTemplates.all.chunked(60).forEachIndexed { page, list ->
            val rows = (list.size + cols - 1) / cols
            val img = BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = Color.WHITE; g.fillRect(0, 0, img.width, img.height)
            list.forEachIndexed { i, t ->
                val x = (i % cols) * cell; val y = (i / cols) * cell
                val paths = ShapeSignatures.normalize(t.strokes).map { VPath(it) }
                Preview.draw(g, paths, x + 20f, y + 12f, cell - 40f, cell - 40f, inkWidth = 2f)
                g.color = Color.DARK_GRAY; g.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
                g.drawString(t.name.take(24), x + 4, y + cell - 6)
            }
            g.dispose()
            Preview.write(img, "shape_templates_$page")
        }
        // El çizimi benzetimi örnekleri
        val rnd = java.util.Random(1)
        val sample = ShapeTemplates.all.shuffled(rnd).take(20)
        val img = BufferedImage(5 * 220, 4 * 220, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE; g.fillRect(0, 0, img.width, img.height)
        sample.forEachIndexed { i, t ->
            val q = handDrawn(t.strokes, rnd)
            val paths = ShapeSignatures.normalize(q).map { VPath(it) }
            val x = (i % 5) * 220; val y = (i / 5) * 220
            Preview.draw(g, paths, x + 20f, y + 10f, 180f, 180f, inkWidth = 2f)
            val best = index.search(q, if (t.strokeCount == 1) MatchMode.SINGLE else MatchMode.GROUP, 1).firstOrNull()
            g.color = if (best?.key == t.key) Color(0, 120, 0) else Color.RED
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
            g.drawString("${t.name.take(18)} → ${best?.name?.take(14)} ${"%.2f".format(best?.confidence ?: 0f)}", x + 4, y + 214)
        }
        g.dispose()
        Preview.write(img, "shape_templates_handdrawn")
    }
}
