package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.vector.VPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v45 (6, 9) parametreli şekiller: formül alanı (atama, türetilmiş büyüklük, eğri uydurma, hatalar), tutamak
 * sürükleme, değer–çizim–formül tutarlılığı ve köşedeki formül yazısı.
 */
class V45ParamShapesTest {

    private val models: List<ParamModel> = listOf(
        ProjectileModel, WaveModel, SpringModel, PendulumModel, ConvexLensModel, ConcaveLensModel, InclineModel, OhmModel,
        TriangleModel, CircleModel, PolygonModel, ParabolaModel, PopulationModel,
    ) + Molecules.all

    private fun ok(r: ParamApply): Map<String, String> {
        assertTrue("uygulanmalıydı: $r", r is ParamApply.Ok)
        return (r as ParamApply.Ok).params
    }

    private fun fail(r: ParamApply, code: ParamError.Code): ParamError {
        assertTrue("hata beklenirdi: $r", r is ParamApply.Failed)
        val e = (r as ParamApply.Failed).error
        assertEquals(code, e.code)
        return e
    }

    private fun num(raw: Map<String, String>, key: String) = raw[key]!!.toDouble()

    private fun geometry(paths: List<VPath>) = paths.filter { it.label == null }.flatMap { p -> p.points.map { listOf(it.x, it.y) } }

    private fun formulaPaths(paths: List<VPath>) = paths.filter { it.tag == "formula" || it.tag?.startsWith("formula:") == true }

    // ------------------------------------------------------------------ ortak

    @Test
    fun everyModelDrawsWithCornerFormulaLabel() {
        for (m in models) {
            val paths = m.paths(null)
            assertTrue("${m.id} çizim boş", geometry(paths).size > 4)
            val labels = formulaPaths(paths)
            assertTrue("${m.id}: formül yazısı yok", labels.isNotEmpty())
            val main = labels.first { it.tag == "formula" }
            assertEquals("${m.id}: yazı modelin ilk satırı", m.label(m.values(null))[0], main.label)
            // köşede: üst kenar şeridinde, yatayda sol ya da sağ kenara yaslı
            val y = main.points.minOf { it.y }
            assertTrue("${m.id}: formül üstte değil ($y)", y < 0.12f)
            val x0 = main.points.minOf { it.x }
            assertTrue("${m.id}: formül köşede değil ($x0)", x0 < 0.1f || m.labelRight)
            // her şey kutuda
            paths.flatMap { it.points }.forEach { p -> assertTrue("${m.id}: kutu dışı $p", p.x > -0.05f && p.x < 1.05f && p.y > -0.05f && p.y < 1.05f) }
            // kütüphanede parametreli şekil olarak bulunur (uzak katalog bunu ezmez)
            assertEquals(m, ShapeLibrary.find("lib." + m.id)?.param ?: ShapeLibrary.find(m.id)?.param)
        }
    }

    @Test
    fun labelUpdatesWithValuesAndBadStoredValuesFallBack() {
        val raw = ok(ProjectileModel.apply(null, "v = 30; θ = 40"))
        val label = formulaPaths(ProjectileModel.paths(raw)).map { it.label }
        assertTrue(label.toString(), label.any { it!!.contains("v = 30 m/s") && it.contains("θ = 40°") })
        // bozuk kayıt: varsayılana döner, çizim çökmez
        val bad = mapOf("v" to "abc", "theta" to "NaN", "g" to "1e999")
        assertEquals(ProjectileModel.paths(null).size, ProjectileModel.paths(bad).size)
        assertEquals(20.0, ProjectileModel.values(bad)["v"], 1e-9)
        // aralık dışı kayıt sıkıştırılır
        assertEquals(85.0, ProjectileModel.values(mapOf("theta" to "120"))["theta"], 1e-9)
    }

    @Test
    fun storeAndDisplayFormatting() {
        assertEquals("20", ParamModel.store(20.0))
        assertEquals("0.333333", ParamModel.store(1.0 / 3))
        assertEquals("-2.5", ParamModel.store(-2.5))
        assertEquals("2,5", ParamModel.num(2.5))
        assertEquals("−1,33", ParamModel.num(-4.0 / 3))
        assertEquals("∞", ParamModel.num(Double.POSITIVE_INFINITY))
    }

    @Test
    fun formulaErrorsLeaveValuesUntouched() {
        val raw = mapOf("v" to "25")
        fail(ProjectileModel.apply(raw, "v = (3"), ParamError.Code.SYNTAX).also { assertNotNull(it.math) }
        assertEquals("q", fail(ProjectileModel.apply(raw, "q = 3"), ParamError.Code.UNKNOWN_SYMBOL).detail)
        assertEquals("θ", fail(ProjectileModel.apply(raw, "θ = 95"), ParamError.Code.OUT_OF_RANGE).detail)
        fail(ProjectileModel.apply(raw, "y = x^3"), ParamError.Code.NO_MATCH)
        fail(ProjectileModel.apply(raw, "v < 3"), ParamError.Code.NO_MATCH)
        fail(ProjectileModel.apply(raw, "v = 1/0"), ParamError.Code.NOT_NUMBER)
        fail(ProjectileModel.apply(raw, ""), ParamError.Code.SYNTAX)
        // ikinci parça hatalıysa ilk parça da uygulanmaz
        fail(ProjectileModel.apply(raw, "v = 30; θ = 200"), ParamError.Code.OUT_OF_RANGE)
        assertEquals("25", raw["v"])
    }

    // ------------------------------------------------------------------ fizik

    @Test
    fun projectileFormulaDerivedCurveAndHandle() {
        val raw = ok(ProjectileModel.apply(null, "v = 30; θ = 45°"))
        assertEquals(30.0, num(raw, "v"), 1e-9)
        assertEquals(45.0, num(raw, "theta"), 1e-9)
        assertEquals(900 / 9.8, ProjectileModel.derivedValues(raw)["R"]!!, 1e-6)
        // menzil yazılınca hız çözülür (θ sabit)
        val r2 = ok(ProjectileModel.apply(raw, "R = 50"))
        assertEquals(sqrt(50 * 9.8), num(r2, "v"), 1e-3)
        assertEquals(45.0, num(r2, "theta"), 1e-9)
        // eğri yazılınca θ ve v uydurulur: y = x − 0,02x² → θ = 45°, v = √(g / (2·0,02·cos²45°))
        val r3 = ok(ProjectileModel.apply(raw, "y = x - 0,02x^2"))
        assertEquals(45.0, num(r3, "theta"), 1e-4)
        assertEquals(sqrt(9.8 / (2 * 0.02 * 0.5)), num(r3, "v"), 1e-3)
        // formül alanının metni geri yazılabilir (aynı değerler)
        val again = ok(ProjectileModel.apply(r3, ProjectileModel.editText(r3)))
        assertEquals(num(r3, "theta"), num(again, "theta"), 0.01)
        // genel bağıntı doğruysa değer değişmez
        assertEquals(raw, ok(ProjectileModel.apply(raw, "h = v^2·sin(θ)^2/(2g)")).let { raw })
        // açı geometriyi değiştirir
        assertNotEquals(geometry(ProjectileModel.paths(raw)), geometry(ProjectileModel.paths(ok(ProjectileModel.apply(raw, "θ = 30")))))
        // tutamak: hız vektörünün ucu; sürükleyince θ ve v, yeni tutamak sürüklenen yerde
        val h = ProjectileModel.unitHandles(raw).single()
        assertEquals("v0", h.key)
        val target = Pair(h.x + 0.03f, h.y - 0.05f)
        val dragged = ProjectileModel.dragUnit(raw, "v0", target.first, target.second)!!
        val h2 = ProjectileModel.unitHandles(dragged).single()
        assertEquals(target.first, h2.x, 2e-3f)
        assertEquals(target.second, h2.y, 2e-3f)
        assertNull(ProjectileModel.dragUnit(raw, "yok", 0.5f, 0.5f))
    }

    @Test
    fun waveSpringPendulumOhm() {
        val w = ok(WaveModel.apply(null, "y = 2sin(3x)"))
        assertEquals(2.0, num(w, "A"), 1e-6); assertEquals(3.0, num(w, "k"), 1e-6)
        assertEquals(2 * PI / 3, WaveModel.derivedValues(w)["λ"]!!, 1e-6)
        val w2 = ok(WaveModel.apply(w, "λ = 4"))
        assertEquals(PI / 2, num(w2, "k"), 1e-5)
        fail(WaveModel.apply(w, "y = 2cos(3x)"), ParamError.Code.NO_MATCH)
        assertNotEquals(geometry(WaveModel.paths(w)), geometry(WaveModel.paths(w2)))
        val crest = WaveModel.unitHandles(w).single()
        val wd = WaveModel.dragUnit(w, "crest", crest.x + 0.05f, crest.y - 0.02f)!!
        assertTrue(num(wd, "k") < 3.0 && num(wd, "A") > 2.0)

        val s = ok(SpringModel.apply(null, "F = 20"))
        assertEquals(0.4, num(s, "x"), 1e-6)
        assertEquals("F", fail(SpringModel.apply(null, "F = 30"), ParamError.Code.OUT_OF_RANGE).detail)
        assertEquals(80.0, num(ok(SpringModel.apply(null, "F = 80x")), "k"), 1e-6)
        fail(SpringModel.apply(null, "F = 80x + 1"), ParamError.Code.NO_MATCH)
        val mass = SpringModel.unitHandles(s).single()
        val sd = SpringModel.dragUnit(s, "mass", mass.x - 0.1f, mass.y)!!
        assertTrue(num(sd, "x") < 0.4)
        assertNotEquals(geometry(SpringModel.paths(s)), geometry(SpringModel.paths(sd)))

        val p = ok(PendulumModel.apply(null, "T = 2"))
        assertEquals(9.8 / (PI * PI), num(p, "L"), 1e-4)
        assertNotEquals(geometry(PendulumModel.paths(null)), geometry(PendulumModel.paths(ok(PendulumModel.apply(null, "θ = 15")))))

        val o = ok(OhmModel.apply(null, "V = 5I"))
        assertEquals(5.0, num(o, "R"), 1e-9)
        assertTrue(formulaPaths(OhmModel.paths(o)).any { it.label!!.contains("R = 5") })
    }

    @Test
    fun lensEquationBothWays() {
        val raw = ok(ConvexLensModel.apply(null, "f = 10; di = 15"))
        assertEquals(30.0, num(raw, "do"), 1e-4)
        val dv = ConvexLensModel.derivedValues(raw)
        assertEquals(15.0, dv["di"]!!, 1e-4)
        assertEquals(-0.5, dv["m"]!!, 1e-4)
        ok(ConvexLensModel.apply(raw, "1/f = 1/do + 1/di"))
        fail(ConvexLensModel.apply(raw, "1/f = 1/do - 1/di"), ParamError.Code.NO_MATCH)
        // cisim odak içinde: sanal görüntü (di < 0)
        val virt = ok(ConvexLensModel.apply(null, "f = 10; do = 5"))
        assertEquals(-10.0, ConvexLensModel.derivedValues(virt)["di"]!!, 1e-6)
        // ıraksak mercek: görüntü hep sanal
        assertTrue(ConcaveLensModel.derivedValues(null)["di"]!! < 0)
        assertNotEquals(geometry(ConvexLensModel.paths(raw)), geometry(ConvexLensModel.paths(virt)))
        val obj = ConvexLensModel.unitHandles(raw).first { it.key == "object" }
        val moved = ConvexLensModel.dragUnit(raw, "object", obj.x - 0.05f, obj.y)!!
        assertTrue(num(moved, "do") > 30.0)
    }

    @Test
    fun inclineAngleAndAcceleration() {
        val raw = ok(InclineModel.apply(null, "θ = 45"))
        assertEquals(9.8 * sin(PI / 4), InclineModel.derivedValues(raw)["a"]!!, 1e-6)
        val r2 = ok(InclineModel.apply(null, "a = 4,9"))
        assertEquals(30.0, num(r2, "theta"), 1e-3)
        assertNotEquals(geometry(InclineModel.paths(raw)), geometry(InclineModel.paths(r2)))
        val top = InclineModel.unitHandles(raw).single()
        val d = InclineModel.dragUnit(raw, top.key, top.x, top.y + 0.1f)!!
        assertTrue(num(d, "theta") < 45.0)
    }

    // ------------------------------------------------------------------ geometri

    @Test
    fun triangleAnglesSidesAndApexHandle() {
        val raw = ok(TriangleModel.apply(null, "A = 90°; B = 45°; c = 4"))
        val d = TriangleModel.derivedValues(raw)
        assertEquals(45.0, d["C"]!!, 1e-9)
        assertEquals(4 * sqrt(2.0), d["a"]!!, 1e-9)
        assertEquals(4.0, d["b"]!!, 1e-9)
        assertEquals(8.0, d["S"]!!, 1e-9)
        assertEquals("C", fail(TriangleModel.apply(null, "A = 120; B = 70"), ParamError.Code.OUT_OF_RANGE).detail)
        // C = 30 yazılınca B çözülür
        assertEquals(60.0, num(ok(TriangleModel.apply(raw, "C = 30")), "B"), 1e-4)
        assertNull("geçersiz alan değeri uygulanmaz", TriangleModel.setValue(raw, "B", 100.0))
        assertNotNull(TriangleModel.setValue(raw, "B", 60.0))
        // tutamak: tepe sürüklenince açılar, çizimdeki köşe ile tutarlı
        val apex = TriangleModel.unitHandles(raw).single()
        val drag = TriangleModel.dragUnit(raw, "C", apex.x + 0.08f, apex.y + 0.02f)!!
        val v = TriangleModel.values(drag)
        assertEquals(180.0, v["A"] + v["B"] + TriangleModel.derivedValues(drag)["C"]!!, 1e-9)
        assertTrue(v["A"] < 90.0)
        // köşelerin çizimdeki açıları değerlerle aynı
        val poly = TriangleModel.paths(drag).first { it.tag == "curve" }.points
        fun ang(p: Int, q: Int, r: Int): Double {
            val a = poly[p]; val b = poly[q]; val c = poly[r]
            val a1 = atan2((b.y - a.y).toDouble() * TriangleModel.h, (b.x - a.x).toDouble() * TriangleModel.w)
            val a2 = atan2((c.y - a.y).toDouble() * TriangleModel.h, (c.x - a.x).toDouble() * TriangleModel.w)
            var d0 = abs(a1 - a2) * 180 / PI
            if (d0 > 180) d0 = 360 - d0
            return d0
        }
        assertEquals(v["A"], ang(0, 1, 2), 0.3)
        assertEquals(v["B"], ang(1, 2, 0), 0.3)
    }

    @Test
    fun circleRadiusFromEquationAndRim() {
        assertEquals(4.0, num(ok(CircleModel.apply(null, "x² + y² = 16")), "r"), 1e-6)
        assertEquals(1.5, num(ok(CircleModel.apply(null, "x^2+y^2=2,25")), "r"), 1e-6)
        fail(CircleModel.apply(null, "(x−1)² + y² = 4"), ParamError.Code.NO_MATCH)
        fail(CircleModel.apply(null, "x² + y² = −4"), ParamError.Code.NO_MATCH)
        fail(CircleModel.apply(null, "x² + 2y² = 4"), ParamError.Code.NO_MATCH)
        assertEquals(5.0, num(ok(CircleModel.apply(null, "Ç = 10π")), "r"), 1e-5)
        assertEquals(2.0, num(ok(CircleModel.apply(null, "A = 4π")), "r"), 1e-5)
        assertEquals(7.0, num(ok(CircleModel.apply(null, "r = 7")), "r"), 1e-9)
        assertEquals("x² + y² = 9", CircleModel.editText(null))
        // kenar tutamağı: dışa sürükleyince r büyür (ızgara ölçeği başlangıç değerinden)
        val rim = CircleModel.unitHandles(null).single()
        val cx = 0.5f; val cy = 0.6f
        val out = CircleModel.dragUnit(null, "r", cx + (rim.x - cx) * 1.5f, cy + (rim.y - cy) * 1.5f)!!
        assertEquals(4.5, num(out, "r"), 0.06)
        // çizilen çember yarıçapı değerle orantılı (ızgara aynı kaldıkça)
        val r3 = CircleModel.paths(null).first { it.tag == "curve" }.points.let { p -> (p.maxOf { it.x } - p.minOf { it.x }) / 2 }
        val r4 = CircleModel.paths(mapOf("r" to "4")).first { it.tag == "curve" }.points.let { p -> (p.maxOf { it.x } - p.minOf { it.x }) / 2 }
        assertEquals(4.0 / 3.0, (r4 / r3).toDouble(), 0.01)
    }

    @Test
    fun polygonSidesAngleAndCornerHandle() {
        assertEquals(6.0, num(ok(PolygonModel.apply(null, "α = 120")), "n"), 1e-9)
        assertEquals(5.0, num(ok(PolygonModel.apply(null, "α = 108°")), "n"), 1e-9)
        assertEquals("α", fail(PolygonModel.apply(null, "α = 125"), ParamError.Code.OUT_OF_RANGE).detail)
        val p5 = ok(PolygonModel.apply(null, "n = 5; a = 2"))
        assertTrue(formulaPaths(PolygonModel.paths(p5)).any { it.label!!.contains("α = 108°") && it.label!!.contains("Ç = 10") })
        assertEquals(5, PolygonModel.paths(p5).first { it.tag == "curve" }.points.size)
        // köşe tutamağı ilk köşeye yaklaşınca kenar sayısı artar
        val h = PolygonModel.unitHandles(p5).single()
        val first = PolygonModel.paths(p5).first { it.tag == "curve" }.points[0]
        val mid = Pair((h.x + first.x) / 2, (h.y + first.y) / 2)
        val more = PolygonModel.dragUnit(p5, "corner", mid.first, mid.second)!!
        assertTrue(num(more, "n") > 5)
        // n tamsayı saklanır
        assertEquals("7", PolygonModel.withValue(null, "n", 6.6)["n"])
    }

    @Test
    fun parabolaCoefficientsVertexAndPointHandles() {
        val raw = ok(ParabolaModel.apply(null, "y = 2x^2 - 4x + 1"))
        assertEquals(2.0, num(raw, "a"), 1e-9); assertEquals(-4.0, num(raw, "b"), 1e-9); assertEquals(1.0, num(raw, "c"), 1e-9)
        assertEquals("y = 2x² − 4x + 1", ParabolaModel.editText(raw))
        assertEquals(8.0, ParabolaModel.derivedValues(raw)["Δ"]!!, 1e-9)
        fail(ParabolaModel.apply(null, "y = sin(x)"), ParamError.Code.NO_MATCH)
        fail(ParabolaModel.apply(null, "y = 3x + 1"), ParamError.Code.NO_MATCH)
        assertEquals(-1.0, num(ok(ParabolaModel.apply(null, "y = -(x-1)^2 + 2")), "a"), 1e-9)
        // tepe tutamağı: tepe noktası sürüklenen yere (0,1 adımla), a değişmez
        val hs = ParabolaModel.unitHandles(raw)
        val vtx = hs.first { it.key == "vertex" }
        val moved = ParabolaModel.dragUnit(raw, "vertex", vtx.x + 0.104f / 1.2f, vtx.y)!! // 1 birim sağa (pencere 10 birim = 1,04 sanal, kutu eni 1,2)
        assertEquals(2.0, num(moved, "a"), 1e-9)
        assertEquals(2.0, -num(moved, "b") / (2 * num(moved, "a")), 1e-9)
        // nokta tutamağı: a değişir, tepe aynı
        val pt = hs.first { it.key == "point" }
        val steeper = ParabolaModel.dragUnit(raw, "point", pt.x, pt.y - 0.05f)!!
        assertTrue(num(steeper, "a") > 2.0)
        assertEquals(1.0, -num(steeper, "b") / (2 * num(steeper, "a")), 1e-9)
        assertNotEquals(geometry(ParabolaModel.paths(raw)), geometry(ParabolaModel.paths(steeper)))
    }

    @Test
    fun logisticPopulation() {
        val raw = ok(PopulationModel.apply(null, "N = 200/(1 + 9e^(−0,3t))"))
        assertEquals(200.0, num(raw, "K"), 1e-4)
        assertEquals(20.0, num(raw, "N0"), 1e-4)
        assertEquals(0.3, num(raw, "r"), 1e-6)
        assertEquals(5.0, num(ok(PopulationModel.apply(raw, "N₀ = 5")), "N0"), 1e-9)
        assertEquals(5.0, num(ok(PopulationModel.apply(raw, "N_0 = 5")), "N0"), 1e-9)
        assertEquals("N_0", fail(PopulationModel.apply(raw, "N_0 = 300"), ParamError.Code.OUT_OF_RANGE).detail)
        fail(PopulationModel.apply(raw, "N = 2t"), ParamError.Code.NO_MATCH)
        val k = PopulationModel.unitHandles(raw).first { it.key == "K" }
        assertTrue(num(PopulationModel.dragUnit(raw, "K", k.x, k.y - 0.05f)!!, "K") > 200)
        assertEquals(ln9over03(), PopulationModel.derivedValues(raw)["t_m"]!!, 1e-6)
    }

    private fun ln9over03() = kotlin.math.ln(9.0) / 0.3

    @Test
    fun angleUnitsInFormulas() {
        // atamalar derece: sin(30) = 0,5 → θ = asin(0,5) = 30°
        assertEquals(30.0, num(ok(ProjectileModel.apply(null, "θ = asin(0,5)")), "theta"), 1e-9)
        // eğride x sayıdır (radyan): y = 3sin(2x) → k = 2
        assertEquals(2.0, num(ok(WaveModel.apply(null, "y = 3sin(2x)")), "k"), 1e-6)
        assertFalse(cos(0.0).isNaN())
    }
}
