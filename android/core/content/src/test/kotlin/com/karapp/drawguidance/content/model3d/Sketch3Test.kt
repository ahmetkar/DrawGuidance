package com.karapp.drawguidance.content.model3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** v27 3B çizim önerisi: öznitelikler, sınıflandırıcı doğruluğu, öğrenme, uydurma ve hız. */
class Sketch3Test {

    private fun front(xy: List<Pair<Float, Float>>, z: Float = 0f): List<V3> = xy.map { (x, y) -> V3(x, y, z) }

    private fun circle(r: Float = 1f, cx: Float = 0f, cy: Float = 0f, n: Int = 60): List<Pair<Float, Float>> =
        List(n + 1) { i -> val a = 2 * PI.toFloat() * i / n; cx + r * cos(a) to cy + r * sin(a) }

    private fun poly(v: List<Pair<Float, Float>>, per: Int = 12): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        val loop = v + v.first()
        for (i in 0 until loop.size - 1) for (k in 0 until per) {
            val t = k / per.toFloat()
            out += (loop[i].first + (loop[i + 1].first - loop[i].first) * t) to (loop[i].second + (loop[i + 1].second - loop[i].second) * t)
        }
        out += loop.last()
        return out
    }

    private fun top(points: List<V3>, model: SketchModel = SketchModel.create(), drawn: Brush3Kind = Brush3Kind.LINE): Pair<Sketch2, Sketch3Result> =
        Sketch3Suggest.analyze(points, V3(0f, 0f, 1f), V3.UP, drawn, model, 0.01f)!!

    @Test
    fun heldOutSyntheticAccuracy() {
        val model = SketchModel.create()
        val rnd = Random(424242)
        val classes = SketchClass.entries
        var ok = 0; var total = 0
        val confusion = Array(classes.size) { IntArray(classes.size) }
        for (c in classes) repeat(40) {
            val (xs, ys) = SketchSynth.sample(c, rnd)
            val f = SketchFeatures.of(xs, ys) ?: return@repeat
            val post = model.posterior(f)
            val best = post.indices.maxBy { post[it] }
            confusion[c.ordinal][best]++
            if (best == c.ordinal) ok++
            total++
        }
        val acc = ok.toFloat() / total
        println("Sketch3 doğruluk: %.3f (%d/%d)".format(acc, ok, total))
        classes.forEach { c -> println("  %-9s %s".format(c.name, confusion[c.ordinal].joinToString(" ") { "%2d".format(it) })) }
        assertTrue("doğruluk $acc", acc >= 0.85f)
    }

    @Test
    fun handDrawnShapesGetSensibleFirstSuggestion() {
        assertEquals(Suggest3Kind.SPHERE, top(front(circle())).second.suggestions.first().kind)
        assertEquals(Suggest3Kind.CUBE, top(front(poly(listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)))).second.suggestions.first().kind)
        assertEquals(Suggest3Kind.BOX, top(front(poly(listOf(-2f to -0.6f, 2f to -0.6f, 2f to 0.6f, -2f to 0.6f)))).second.suggestions.first().kind)
        assertEquals(Suggest3Kind.CONE, top(front(poly(listOf(-1f to -1f, 1f to -1f, 0f to 1.4f)))).second.suggestions.first().kind)
        assertEquals(Suggest3Kind.ROD, top(front(List(20) { i -> i / 10f to 0.02f * sin(i.toFloat()) })).second.suggestions.first().kind)
        val spiral = List(120) { i -> val th = 6f * PI.toFloat() * i / 119f; val r = 0.1f + th / (6f * PI.toFloat()); r * cos(th) to r * sin(th) }
        assertEquals(Suggest3Kind.SPRING, top(front(spiral)).second.suggestions.first().kind)
        val star = List(10) { i -> val a = PI.toFloat() * i / 5 + PI.toFloat() / 2; val r = if (i % 2 == 0) 1f else 0.42f; r * cos(a) to r * sin(a) }
        assertEquals(Suggest3Kind.STAR, top(front(poly(star, 8))).second.suggestions.first().kind)
        val ellipse = List(61) { i -> val a = 2 * PI.toFloat() * i / 60; 1.6f * cos(a) to 0.7f * sin(a) }
        assertEquals(Suggest3Kind.ELLIPSOID, top(front(ellipse)).second.suggestions.first().kind)
    }

    @Test
    fun drawnBrushKindIsNotSuggestedAgain() {
        val arc = List(30) { i -> val a = PI.toFloat() * i / 29f; cos(a) to sin(a) }
        val r = top(front(arc), drawn = Brush3Kind.TUBE).second
        assertTrue(r.suggestions.none { it.kind == Suggest3Kind.TUBE })
        assertTrue(r.suggestions.size <= 3)
    }

    @Test
    fun acceptingTeachesPreferenceAndSurvivesEncoding() {
        val tri = front(poly(listOf(-1f to -1f, 1f to -1f, 0f to 1.4f)))
        val model = SketchModel.create()
        assertEquals(Suggest3Kind.CONE, top(tri, model).second.suggestions.first().kind)
        repeat(4) {
            val r = top(tri, model).second
            model.learnAccept(r.features, Suggest3Kind.PYRAMID)
        }
        assertEquals(Suggest3Kind.PYRAMID, top(tri, model).second.suggestions.first().kind)
        // Kişisel durum saklanıp geri yüklenince aynı sıralama
        val copy = SketchModel.create().apply { decode(model.encode()) }
        assertEquals(Suggest3Kind.PYRAMID, top(tri, copy).second.suggestions.first().kind)
        // Bozuk kayıt yok sayılır
        SketchModel.create().decode("v1;p,XYZ,1,2")
    }

    /**
     * v29 3B modunun kendi sinir ağı: kabul edilen tercih öğrenilir, kayıtla birlikte saklanır. v30: ağ yalnız çok
     * parçalı (3+ köşeli) izlerde çalışır; daire gibi basit izlerde tercih yalnız Naive Bayes sayaçlarına yazılır.
     */
    @Test
    fun modeNetLearnsPreference() {
        // Basit iz (daire): ağ yok, tercih sayaçla öğrenilir
        val circlePts = front(circle(n = 120))
        val simple = SketchModel.create()
        val c0 = top(circlePts, simple).second
        assertTrue(!simple.usesNet(c0.features))
        repeat(5) { simple.learnAccept(top(circlePts, simple).second.features, Suggest3Kind.TORUS) }
        assertEquals(0, simple.netSamples)
        val cAfter = top(circlePts, simple).second.suggestions.map { it.kind }
        println("3B daire (ağsız): önce ${c0.suggestions.map { it.kind }} → sonra $cAfter")
        assertTrue(Suggest3Kind.TORUS in cAfter)

        // Çok parçalı iz (yıldız): ağ öğrenir
        val starPts = front(poly(List(10) { i ->
            val a = i * Math.PI / 5 - Math.PI / 2; val r = if (i % 2 == 0) 1.0 else 0.45
            (r * kotlin.math.cos(a)).toFloat() to (r * kotlin.math.sin(a)).toFloat()
        } + listOf(0f to -1f)))
        val model = SketchModel.create()
        val r0 = top(starPts, model).second
        assertTrue("yıldız çok parçalı", model.usesNet(r0.features))
        val first = r0.suggestions.map { it.kind }
        val target = first.lastOrNull() ?: Suggest3Kind.TORUS
        assertEquals(0, model.netSamples)
        repeat(5) { model.learnAccept(top(starPts, model).second.features, target) }
        assertEquals(5, model.netSamples)
        val after = top(starPts, model).second.suggestions.map { it.kind }
        println("3B yıldız (ağlı): önce $first → sonra $after")
        assertEquals(target, after.first())
        val copy = SketchModel.create().apply { decode(model.encode()) }
        assertEquals(5, copy.netSamples)
        assertEquals(after, top(starPts, copy).second.suggestions.map { it.kind })
    }

    @Test
    fun fitsSolidsToTheStroke() {
        val mat = Material()
        // Merkezi (2, 1, 0.5), yarıçapı 0,8 olan daire → küre
        val (sk, _) = top(front(circle(0.8f, 2f, 1f), z = 0.5f))
        val sphere = Sketch3Fit.solid(Suggest3Kind.SPHERE, sk, "s", 0, mat)!!
        assertEquals(2f, sphere.position.x, 0.03f); assertEquals(1f, sphere.position.y, 0.03f); assertEquals(0.5f, sphere.position.z, 1e-3f)
        assertEquals(1.6f, sphere.scale.x, 0.05f)
        // Dikdörtgen → kutu: genişlik 4, yükseklik 1,2, dik duruyor (Y dünya yukarısı)
        val (rk, _) = top(front(poly(listOf(-2f to -0.6f, 2f to -0.6f, 2f to 0.6f, -2f to 0.6f))))
        val box = Sketch3Fit.solid(Suggest3Kind.BOX, rk, "b", 0, mat)!!
        val wb = ModelEdit.worldBounds(box)
        assertEquals(4f, wb.size.x, 0.08f); assertEquals(1.2f, wb.size.y, 0.08f); assertEquals(1.2f, wb.size.z, 0.08f)
        // Üçgen → koni: tepe yukarıda, taban aşağıda
        val (tk, _) = top(front(poly(listOf(-1f to -1f, 1f to -1f, 0f to 1.4f))))
        val cone = Sketch3Fit.solid(Suggest3Kind.CONE, tk, "c", 0, mat)!!
        val cb = ModelEdit.worldBounds(cone)
        assertEquals(-1f, cb.min.y, 0.08f); assertEquals(1.4f, cb.max.y, 0.08f); assertEquals(2f, cb.size.x, 0.1f)
        // Yatay çizgi → çubuk boyunca X
        val (lk, _) = top(front(List(20) { i -> i / 10f to 0f }))
        val rod = Sketch3Fit.solid(Suggest3Kind.ROD, lk, "r", 0, mat)!!
        assertEquals(1.9f, ModelEdit.worldBounds(rod).size.x, 0.05f)
        // Yay: fırça girdisi (tüp), eksen boyunca yükselir
        val spiral = List(120) { i -> val th = 6f * PI.toFloat() * i / 119f; val r = 0.1f + th / (6f * PI.toFloat()); r * cos(th) to r * sin(th) }
        val (spk, _) = top(front(spiral))
        val fit = Sketch3Fit.brush(Suggest3Kind.SPRING, spk, front(spiral))!!
        assertEquals(Brush3Kind.TUBE, fit.kind)
        val ys = fit.input.points.map { it.y }
        assertTrue(ys.max() - ys.min() > 1.5f)
        val mesh = BrushShapes.build(fit.input, BrushShapeSpec(Brush3Kind.TUBE, fit.width!!, smoothing = 0f))
        assertNotNull(mesh)
    }

    @Test
    fun rotationRoundTrip() {
        val rnd = Random(7)
        repeat(50) {
            val deg = V3(rnd.nextFloat() * 160 - 80, rnd.nextFloat() * 160 - 80, rnd.nextFloat() * 160 - 80)
            val r = Transform3.rotation(deg)
            val ex = V3(r[0], r[3], r[6]); val ey = V3(r[1], r[4], r[7]); val ez = V3(r[2], r[5], r[8])
            val back = Transform3.rotation(Rot3.euler(ex, ey, ez))
            for (k in 0 until 9) assertEquals(r[k], back[k], 1e-3f)
        }
    }

    @Test
    fun analysisIsFast() {
        val model = SketchModel.create()
        val pts = front(circle(n = 400))
        repeat(50) { top(pts, model) }
        val t0 = System.nanoTime()
        repeat(500) { top(pts, model) }
        val ms = (System.nanoTime() - t0) / 1e6 / 500
        println("Sketch3 çözümleme: %.3f ms/iz".format(ms))
        assertTrue("$ms ms", ms < 2.0)
        val t1 = System.nanoTime()
        SketchModel.train(Random(1), SketchModel.SYNTH_PER_CLASS)
        println("Sketch3 eğitim: %.1f ms".format((System.nanoTime() - t1) / 1e6))
    }

    @Test
    fun degenerateInputsAreSafe() {
        val model = SketchModel.create()
        assertEquals(null, Sketch3Suggest.analyze(listOf(V3.ZERO, V3.ZERO, V3.ZERO), V3(0f, 0f, 1f), V3.UP, Brush3Kind.LINE, model, 0.01f))
        assertEquals(null, Sketch3Suggest.analyze(listOf(V3.ZERO), V3(0f, 0f, 1f), V3.UP, Brush3Kind.LINE, model, 0.01f))
        // Çok kısa iz önerilmez
        assertEquals(null, Sketch3Suggest.analyze(front(circle(0.001f)), V3(0f, 0f, 1f), V3.UP, Brush3Kind.LINE, model, 0.05f))
        // Normal yukarıya paralel (üst düzlem) de çalışır
        val flat = circle().map { (x, z) -> V3(x, 0f, z) }
        assertNotNull(Sketch3Suggest.analyze(flat, V3.UP, V3.UP, Brush3Kind.LINE, model, 0.01f))
        assertTrue(abs(0f) < 1f)
    }
}
