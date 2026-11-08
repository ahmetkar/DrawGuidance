package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.library.science.chem.ElementKind
import com.karapp.drawguidance.content.library.science.chem.PeriodicTable
import com.karapp.drawguidance.content.vector.VPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v46 (4) not almada formülle yeniden çizilen şekiller: element (periyodik tablo hücresi, Bohr atomu), serbest cisim
 * diyagramı, sabit ve hareketli makara, kaldıraç. Kimlikler eskiyle aynı; değer–formül–çizim tutarlı.
 */
class V46ParamMechanicsTest {
    private val models = listOf(FreeBodyModel, FixedPulleyModel, MovablePulleyModel, LeverModel, PeriodicCellModel, BohrAtomModel)

    private fun ok(r: ParamApply): Map<String, String> {
        assertTrue("uygulanmalıydı: $r", r is ParamApply.Ok)
        return (r as ParamApply.Ok).params
    }

    private fun labels(paths: List<VPath>) = paths.filter { it.tag == "formula" || it.tag?.startsWith("formula:") == true }.map { it.label!! }
    private fun tagged(paths: List<VPath>, tag: String) = paths.filter { it.tag == tag }
    private fun geometry(paths: List<VPath>) = paths.filter { it.label == null }.flatMap { p -> p.points.map { it.x to it.y } }

    @Test
    fun everyModelIsInLibraryInsideBoxWithFormula() {
        for (m in models) {
            val lib = ShapeLibrary.find("lib." + m.id) ?: ShapeLibrary.find(m.id)
            assertNotNull(m.id, lib)
            assertEquals("${m.id} parametreli", m, lib!!.param)
            val paths = m.paths(null)
            assertTrue(m.id, geometry(paths).size > 4)
            assertTrue("${m.id} formül yazısı", labels(paths).isNotEmpty())
            paths.flatMap { it.points }.forEach { p -> assertTrue("${m.id}: kutu dışı $p", p.x > -0.05f && p.x < 1.05f && p.y > -0.05f && p.y < 1.05f) }
            // eski kayıt (değersiz) çizilir; bozuk kayıt varsayılana döner
            assertEquals(m.paths(null).size, m.paths(m.defs.associate { it.key to "abc" }).size)
        }
        // eski sabit çizimlerin oranları korundu (eski kayıtlar bozulmadan yeniden çizilir)
        assertEquals(1.3f, FreeBodyModel.w / FreeBodyModel.h, 1e-4f)
        assertEquals(1f / 1.15f, FixedPulleyModel.w / FixedPulleyModel.h, 1e-4f)
        assertEquals(1.8f, LeverModel.w / LeverModel.h, 1e-4f)
        assertEquals(0.9f, PeriodicCellModel.w / PeriodicCellModel.h, 1e-4f)
    }

    // ------------------------------------------------------------------ element

    @Test
    fun periodicTableData() {
        assertEquals(118, PeriodicTable.all.size)
        assertEquals("Og", PeriodicTable[118]!!.symbol)
        assertEquals(PeriodicTable.all.size, PeriodicTable.all.map { it.symbol }.toSet().size)
        // her elementin elektronları katmanlarda tam
        for (e in PeriodicTable.all) assertEquals(e.symbol, e.z, e.shells.sum())
        assertEquals(listOf(2, 8, 1), PeriodicTable.find("Na")!!.shells)
        assertEquals(listOf(2, 8, 8, 2), PeriodicTable.find("kalsiyum")!!.shells)
        assertEquals(listOf(2, 8, 14, 2), PeriodicTable.find("Fe")!!.shells)
        assertEquals(listOf(2, 8, 13, 1), PeriodicTable.find("Cr")!!.shells)
        assertEquals(listOf(2, 8, 18, 1), PeriodicTable.find("Cu")!!.shells)
        assertEquals(listOf(2, 8, 18, 32, 18, 1), PeriodicTable.find("Au")!!.shells)
        assertEquals(listOf(2, 8, 18, 32, 32, 18, 8), PeriodicTable.find("Og")!!.shells)
        assertEquals(ElementKind.NOBLE, PeriodicTable.find("Ar")!!.kind)
        assertEquals(ElementKind.HALOGEN, PeriodicTable.find("Cl")!!.kind)
        assertEquals(12, PeriodicTable.find("Na")!!.neutrons)
        assertNull(PeriodicTable.find("Xx"))
        assertEquals(26, PeriodicTable.find("26")!!.z)
        assertEquals("Zn", PeriodicTable.find("Zn")!!.symbol)
        assertEquals("İyot", PeriodicTable.find("iyot")!!.name)
    }

    @Test
    fun elementShapesFollowTypedElement() {
        // varsayılanlar eski çizimlerle aynı: karbon hücresi, sodyum atomu (2, 8, 1)
        assertEquals("C", PeriodicCellModel.editText(null))
        assertEquals(11, tagged(BohrAtomModel.paths(null), "electron").size)
        assertEquals("30", ok(PeriodicCellModel.apply(null, "Zn"))["z"])
        for (input in listOf("Fe", "demir", "26", "Z = 26")) {
            val raw = ok(PeriodicCellModel.apply(null, input))
            assertEquals(input, "26", raw["z"])
            val texts = PeriodicCellModel.paths(raw).mapNotNull { it.label }
            assertTrue(texts.toString(), "Fe" in texts && "Demir" in texts && "26" in texts && "55,845" in texts)
            assertTrue(labels(PeriodicCellModel.paths(raw)).contains("Geçiş metali"))
        }
        // kararsız element: kütle numarası parantezle
        val tc = ok(PeriodicCellModel.apply(null, "Tc"))
        assertTrue(PeriodicCellModel.paths(tc).mapNotNull { it.label }.contains("(98)"))
        // Bohr: elektron ve katman sayısı elemente göre
        val k = ok(BohrAtomModel.apply(null, "potasyum"))
        val paths = BohrAtomModel.paths(k)
        assertEquals(19, tagged(paths, "electron").size)
        assertEquals(4, tagged(paths, "shell").size)
        assertEquals("K: 2, 8, 8, 1", labels(paths).first())
        assertTrue(paths.mapNotNull { it.label }.containsAll(listOf("19p", "20n")))
        // bilinmeyen element uygulanmaz
        assertTrue(BohrAtomModel.apply(k, "Qq") is ParamApply.Failed)
        assertTrue(BohrAtomModel.apply(k, "Z = 200") is ParamApply.Failed)
    }

    // ------------------------------------------------------------------ serbest cisim

    @Test
    fun freeBodyForcesAndRedraw() {
        // varsayılan: m = 2, F = 15 N yatay, μ = 0,3 → G = 19,6; N = 19,6; Fs = 5,88; a = 4,56
        val f = FreeBodyModel.forces(FreeBodyModel.values(null))
        assertEquals(19.6, f.g, 1e-9)
        assertEquals(19.6, f.n, 1e-9)
        assertEquals(5.88, f.fs, 1e-9)
        assertEquals((15 - 5.88) / 2, f.a, 1e-9)
        // yukarı açılı kuvvet normal kuvveti azaltır
        val raw = ok(FreeBodyModel.apply(null, "F = 20; α = 30°"))
        val g = FreeBodyModel.forces(FreeBodyModel.values(raw))
        assertEquals(19.6 - 10.0, g.n, 1e-6)
        // kuvvet sürtünmeyi yenemezse durur (statik sürtünme = yatay kuvvet)
        val still = ok(FreeBodyModel.apply(null, "F = 3"))
        val s = FreeBodyModel.forces(FreeBodyModel.values(still))
        assertEquals(0.0, s.a, 1e-12)
        assertEquals(3.0, s.fs, 1e-9)
        // türetilmiş: a = 2 yazılınca F çözülür (2·2 + 5,88 = 9,88)
        val solved = ok(FreeBodyModel.apply(null, "a = 2"))
        assertEquals(9.88, solved["F"]!!.toDouble(), 1e-3)
        // yazı ve çizim değişir
        assertNotEquals(labels(FreeBodyModel.paths(null)), labels(FreeBodyModel.paths(raw)))
        assertNotEquals(FreeBodyModel.paths(null).map { it.points }, FreeBodyModel.paths(raw).map { it.points })
        // tutamak: kuvvetin ucu sürüklenince F ve α değişir
        val h = FreeBodyModel.unitHandles(null).single()
        val dragged = FreeBodyModel.dragUnit(null, h.key, h.x + 0.05f, h.y - 0.08f)!!
        assertTrue(dragged["alpha"]!!.toDouble() > 5)
    }

    // ------------------------------------------------------------------ makaralar

    @Test
    fun pulleys() {
        val d = FixedPulleyModel.derivedValues(ok(FixedPulleyModel.apply(null, "P = 250; h = 2")))
        assertEquals(250.0, d["F"]!!, 1e-9)
        assertEquals(2.0, d["s"]!!, 1e-9)
        assertTrue(labels(FixedPulleyModel.paths(mapOf("P" to "250", "h" to "2"))).contains("F = 250 N; ip = 2 m"))
        // hareketli makara: F = (P + Pm)/2, ip 2h
        val raw = ok(MovablePulleyModel.apply(null, "P = 300; Pm = 20; h = 1,5"))
        val m = MovablePulleyModel.derivedValues(raw)
        assertEquals(160.0, m["F"]!!, 1e-9)
        assertEquals(3.0, m["s"]!!, 1e-9)
        assertEquals("F = (P + Pm)/2", labels(MovablePulleyModel.paths(raw)).first())
        // F yazılınca yük çözülür
        val p = ok(MovablePulleyModel.apply(null, "F = 75"))
        assertEquals(150.0, p["P"]!!.toDouble(), 1e-6)
        // makaranın ağırlığı F okunu uzatır
        assertNotEquals(MovablePulleyModel.paths(null).map { it.points }, MovablePulleyModel.paths(raw).map { it.points })
    }

    // ------------------------------------------------------------------ kaldıraç

    @Test
    fun leverMoments() {
        // F·b = P·a → 60·0,4/1 = 24
        assertEquals(24.0, LeverModel.derivedValues(null)["F"]!!, 1e-9)
        val raw = ok(LeverModel.apply(null, "P = 200; a = 0,5; b = 2"))
        assertEquals(50.0, LeverModel.derivedValues(raw)["F"]!!, 1e-9)
        val texts = LeverModel.paths(raw).mapNotNull { it.label }
        assertTrue(texts.toString(), "a = 0,5 m" in texts && "b = 2 m" in texts && "F = 50 N" in texts)
        // F yazılınca kuvvet kolu çözülür: 200·0,5/F = b → F = 25 → b = 4
        val b = ok(LeverModel.apply(raw, "F = 25"))
        assertEquals(4.0, b["b"]!!.toDouble(), 1e-6)
        // destek sürüklenince kollar değişir, toplam boy aynı
        val pivot = LeverModel.unitHandles(raw).first { it.key == "pivot" }
        val moved = LeverModel.dragUnit(raw, "pivot", pivot.x + 0.1f, pivot.y)!!
        assertEquals(2.5, moved["a"]!!.toDouble() + moved["b"]!!.toDouble(), 1e-6)
        assertTrue(moved["a"]!!.toDouble() > 0.5)
        // destek a/(a+b) oranında yer değiştirir: çizim farklı
        assertNotEquals(LeverModel.paths(null).map { it.points }, LeverModel.paths(raw).map { it.points })
    }
}

/** v46 önizleme (-PpreviewDir verilince): formülle çizilen şekiller farklı değerlerle. */
class V46ParamPreview {
    @Test
    fun sheet() {
        val dir = com.karapp.drawguidance.content.Preview.dir ?: return
        val cases = listOf(
            FreeBodyModel to null, FreeBodyModel to mapOf("F" to "40", "alpha" to "30", "m" to "3"),
            FixedPulleyModel to null, MovablePulleyModel to mapOf("P" to "300", "Pm" to "40"),
            LeverModel to null, LeverModel to mapOf("P" to "200", "a" to "1.5", "b" to "0.5"),
            PeriodicCellModel to mapOf("z" to "26"), PeriodicCellModel to mapOf("z" to "118"),
            BohrAtomModel to mapOf("z" to "19"), BohrAtomModel to mapOf("z" to "54"),
        )
        val cell = 340
        val img = java.awt.image.BufferedImage(5 * cell, 2 * cell, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = java.awt.Color.WHITE; g.fillRect(0, 0, img.width, img.height)
        cases.forEachIndexed { i, (m, raw) ->
            val a = m.w / m.h
            val inner = cell - 30f
            val w = if (a >= 1f) inner else inner * a
            val h = if (a >= 1f) inner / a else inner
            val x = (i % 5) * cell + (cell - w) / 2f; val y = (i / 5) * cell + (cell - h) / 2f
            g.color = java.awt.Color(0xEE, 0xEE, 0xEE); g.drawRect(x.toInt(), y.toInt(), w.toInt(), h.toInt())
            com.karapp.drawguidance.content.Preview.draw(g, m.paths(raw, inner), x, y, w, h, inkWidth = 1.6f)
        }
        g.dispose()
        com.karapp.drawguidance.content.Preview.write(img, "v46-param-mechanics")
        dir.exists()
    }
}
