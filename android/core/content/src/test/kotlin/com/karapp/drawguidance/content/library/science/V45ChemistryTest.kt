package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.library.science.chem.Chem
import com.karapp.drawguidance.content.library.science.chem.ChemError
import com.karapp.drawguidance.content.library.science.chem.ChemResult
import com.karapp.drawguidance.content.library.science.chem.Embed3D
import com.karapp.drawguidance.content.library.science.chem.KnownMolecules
import com.karapp.drawguidance.content.library.science.chem.Layout2D
import com.karapp.drawguidance.content.library.science.chem.Molecule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

/**
 * v45 (8) kimya: formül okuma (kapalı, yarı açık, SMILES, ad), bağ düzenleri, değerlik, örnek izomer, bilinen tablo,
 * 2B yerleşim (bağ boyu, çakışma, halka), 3B VSEPR açıları ve molekül şekli (görünümler, köşe yazısı, hatalar).
 */
class V45ChemistryTest {

    private fun read(t: String): ChemResult = Chem.read(t)

    private fun err(t: String): ChemError {
        val (r, e) = Chem.tryRead(t)
        assertNull("hata beklenirdi: $t → ${r?.mol?.hill()}", r)
        return e!!
    }

    private fun orders(m: Molecule) = m.bonds.map { it.order }.sorted()

    // ------------------------------------------------------------------ okuma

    @Test
    fun parsesFormulasNamesCondensedAndSmiles() {
        // girdi → (Hill formülü, bilinen ad ya da null, okuma yolu)
        val table = listOf(
            Triple("H2O", "H2O", "su"), Triple("CO2", "CO2", "karbon dioksit"), Triple("CH4", "CH4", "metan"),
            Triple("NH3", "H3N", "amonyak"), Triple("C2H5OH", "C2H6O", "etanol"), Triple("CH3CH2OH", "C2H6O", "etanol"),
            Triple("CH3-CH2-OH", "C2H6O", "etanol"), Triple("CH3COOH", "C2H4O2", "asetik asit"), Triple("HCOOH", "CH2O2", "formik asit"),
            Triple("CH2=CH2", "C2H4", "eten"), Triple("HC≡CH", "C2H2", "etin"), Triple("CH3CH(CH3)CH3", "C4H10", "izobütan"),
            Triple("C6H6", "C6H6", "benzen"), Triple("C6H5OH", "C6H6O", "fenol"), Triple("C6H5COOH", "C7H6O2", "benzoik asit"),
            Triple("C6H5CH3", "C7H8", "toluen"), Triple("C6H12O6", "C6H12O6", "glikoz"), Triple("C2H6O", "C2H6O", "etanol"),
            Triple("CH3OCH3", "C2H6O", "dimetil eter"), Triple("CH3CHO", "C2H4O", "asetaldehit"), Triple("CH3COCH3", "C3H6O", "aseton"),
            Triple("CH3CN", "C2H3N", "asetonitril"), Triple("NH2CH2COOH", "C2H5NO2", "glisin"), Triple("CH3CH(NH2)COOH", "C3H7NO2", "alanin"),
            Triple("NH2CONH2", "CH4N2O", "üre"), Triple("CH3COOCH2CH3", "C4H8O2", "etil asetat"), Triple("HCHO", "CH2O", "formaldehit"),
            Triple("CH3-CH=CH2", "C3H6", "propen"), Triple("H2SO4", "H2O4S", "sülfürik asit"), Triple("HNO3", "HNO3", "nitrik asit"),
            Triple("NaCl", "ClNa", "sodyum klorür"), Triple("CCO", "C2H6O", "etanol"), Triple("c1ccccc1", "C6H6", "benzen"),
            Triple("OC(=O)C", "C2H4O2", "asetik asit"), Triple("[Na+].[Cl-]", "ClNa", "sodyum klorür"), Triple("CO", "CO", "karbon monoksit"),
            Triple("glikoz", "C6H12O6", "glikoz"), Triple("Ethanol", "C2H6O", "etanol"), Triple("asetik asit", "C2H4O2", "asetik asit"),
            Triple("C₂H₅OH", "C2H6O", "etanol"), Triple("C10H8", "C10H8", "naftalin"), Triple("PCl5", "Cl5P", "fosfor pentaklorür"),
        )
        for ((input, hill, name) in table) {
            val r = read(input)
            assertEquals(input, hill, r.mol.hill())
            assertEquals(input, name, r.known?.name)
            assertFalse(input, r.example)
            assertEquals(input, -1, r.mol.valenceError())
        }
        assertEquals(ChemResult.Kind.CONDENSED, read("CH3COOH").kind)
        assertEquals(ChemResult.Kind.SMILES, read("CCO").kind)
        assertEquals(ChemResult.Kind.FORMULA, read("C2H6O").kind)
        assertEquals(ChemResult.Kind.NAME, read("su").kind)
        // tablo dışı ama yazılabilen yapı: tert-bütanol (adsız, örnek değil)
        val tb = read("(CH3)3COH")
        assertEquals("C4H10O", tb.mol.hill()); assertNull(tb.known); assertFalse(tb.example)
        assertEquals(tb.mol.signature(), read("CC(C)(C)O").mol.signature())
    }

    @Test
    fun bondOrdersAndAtomCounts() {
        assertEquals(listOf(2, 2), orders(read("CO2").mol))
        assertEquals(listOf(3), orders(read("N2").mol))
        assertEquals(listOf(2), orders(read("CH2=CH2").mol))
        assertEquals(listOf(3), orders(read("HC≡CH").mol))
        assertEquals(listOf(1, 1, 2), orders(read("CH3COOH").mol))
        assertEquals(listOf(1, 1, 1, 2, 2, 2), orders(read("C6H6").mol))
        assertEquals(listOf(1, 3), orders(read("CH3CN").mol))
        val naph = read("C10H8").mol
        assertEquals(2, naph.rings().size)
        assertEquals(5, naph.bonds.count { it.order == 2 })
        val gl = read("C6H12O6").mol
        assertEquals(mapOf("C" to 6, "H" to 12, "O" to 6), gl.counts().toSortedMap().toMap())
        assertEquals(listOf(6), gl.rings().map { it.size })
        // SMILES aromatik → Kekulé; piridin N'si H'siz
        val py = read("c1ccncc1").mol
        assertEquals(3, py.bonds.count { it.order == 2 })
        assertEquals(0, py.atoms.first { it.el == "N" }.h)
        // yükler
        val hno3 = read("HNO3").mol
        assertEquals(setOf(1, -1, 0), hno3.atoms.map { it.charge }.toSet())
        assertEquals(0, hno3.charge)
        assertEquals(2, read("NaCl").mol.components().size)
        assertEquals(46.07, read("etanol").mol.mass(), 0.01)
        assertEquals(18.02, read("H2O").mol.mass(), 0.01)
    }

    @Test
    fun errorsAreSpecific() {
        assertEquals(ChemError.Code.EMPTY, err("  ").code)
        assertEquals(ChemError.Code.UNKNOWN_ELEMENT, err("XyZ").code)
        assertEquals(ChemError.Code.HYDROGEN_COUNT, err("CH5").code)
        assertEquals(ChemError.Code.HYDROGEN_COUNT, err("C2H7").code)
        assertEquals(ChemError.Code.IONIC, err("Fe2O3").code)
        assertEquals(ChemError.Code.TOO_LARGE, err("C50H102").code)
        assertEquals(ChemError.Code.PARENTHESIS, err("C(").code)
        assertEquals(ChemError.Code.RING, err("C1CC").code)
        assertEquals("C", err("CH3CH2").arg) // CH2'nin bir bağı eksik
        assertEquals(ChemError.Code.VALENCE, err("CH3CH2").code)
        assertEquals(ChemError.Code.VALENCE, err("C(C)(C)(C)(C)C").code)
        assertEquals(ChemError.Code.SYNTAX, err("CH3?").code)
        assertEquals("VALENCE:C", ChemError(ChemError.Code.VALENCE, "C").toString())
    }

    @Test
    fun unknownFormulaBuildsValidExampleIsomer() {
        val cases = mapOf(
            "C7H16" to 0, "C8H18" to 0, "C5H10O2" to 1, "C4H6" to 2, "C3H9N" to 0, "C3H7Cl" to 0, "C9H12" to 4,
            "C4H8O" to 1, "C5H12O" to 0, "C2H2Cl2" to 1, "SiCl4" to 0, "SF6" to 0,
        )
        for ((f, dbe) in cases) {
            val r = read(f)
            assertTrue("$f örnek yapı", r.example || r.known != null)
            val m = r.mol
            assertEquals(f, -1, m.valenceError())
            assertEquals(f, Chem.formulaCounts(f), m.counts().filterValues { it > 0 }.toMap())
            assertEquals("$f tek parça", 1, m.components().size)
            // çoklu bağlar + halkalar = doymamışlık derecesi
            val unsat = m.bonds.sumOf { it.order - 1 } + m.rings().size
            assertEquals("$f doymamışlık", dbe, unsat)
        }
        assertTrue(read("C7H16").example)
        assertTrue(read("C9H12").mol.rings().isNotEmpty())
        // C2H6O bilinen (etanol), örnek değil
        assertFalse(read("C2H6O").example)
    }

    @Test
    fun knownTableIsLargeConsistentAndSearchable() {
        val all = KnownMolecules.all
        assertTrue("tablo ${all.size}", all.size >= 60)
        assertEquals(all.size, all.map { it.name }.toSet().size)
        for (k in all) {
            val m = k.molecule()
            assertEquals(k.name, -1, m.valenceError())
            assertEquals(k.name, 0, m.charge)
            // yapı imzası tekil: tablo girdisi kendi yapısından tanınır
            assertEquals(k.name, k, KnownMolecules.bySignature(m))
            assertEquals(k.name, k, KnownMolecules.byName(k.name))
            assertEquals(k.en, k, KnownMolecules.byName(k.en))
        }
        // istenen yapılar tabloda
        for (name in listOf("su", "karbon dioksit", "metan", "amonyak", "etanol", "benzen", "glikoz", "asetik asit", "aseton", "eten", "etin",
            "propan", "bütan", "izobütan", "metanol", "formaldehit", "üre", "sodyum klorür", "sülfürik asit", "nitrik asit", "glisin", "alanin")) {
            assertNotNull(name, KnownMolecules.byName(name))
        }
        assertEquals("izobütan", KnownMolecules.byName("2-metilpropan")?.name)
        assertEquals("etanol", KnownMolecules.byHill("C2H6O")?.name)
    }

    // ------------------------------------------------------------------ 2B yerleşim

    @Test
    fun layoutHasUniformBondsAndNoOverlap() {
        val inputs = KnownMolecules.all.map { it.name to it.molecule() } +
            listOf("(CH3)3COH", "C7H16", "C9H12", "CH3CH(NH2)COOH", "C5H10O2").map { it to read(it).mol }
        for ((t, base) in inputs) {
            for (cross in listOf(false, true)) {
                val m = if (cross) base.explicitH() else base
                val p = Layout2D.layout(m, cross)
                assertEquals(m.atoms.size, p.size)
                p.forEach { assertTrue("$t sonlu", it.x.isFinite() && it.y.isFinite()) }
                val (lo, hi) = Layout2D.bondRange(m, p)
                assertTrue("$t (H=$cross) bağ boyu [$lo, $hi]", lo > 0.85 && hi < 1.15)
                if (m.atoms.size > 1) assertTrue("$t (H=$cross) çakışma ${Layout2D.minNonBonded(m, p)}", Layout2D.minNonBonded(m, p) > 0.55)
            }
        }
    }

    @Test
    fun ringsAreRegularAndChainsZigzag() {
        val bz = read("C6H6").mol
        val p = Layout2D.layout(bz)
        val ring = bz.rings().single()
        val cx = ring.map { p[it].x }.average(); val cy = ring.map { p[it].y }.average()
        val rs = ring.map { hypot(p[it].x - cx, p[it].y - cy) }
        rs.forEach { assertEquals(1.0, it, 1e-6) } // altıgenin çevrel yarıçapı = kenar
        // zincir: bütan iskeleti 120° açılı zikzak
        val bu = read("CCCC").mol
        val q = Layout2D.layout(bu)
        fun angle(a: Int, b: Int, c: Int): Double {
            val ux = q[a].x - q[b].x; val uy = q[a].y - q[b].y; val vx = q[c].x - q[b].x; val vy = q[c].y - q[b].y
            return acos((ux * vx + uy * vy) / (hypot(ux, uy) * hypot(vx, vy))) * 180 / PI
        }
        assertEquals(120.0, angle(0, 1, 2), 1e-6)
        assertEquals(120.0, angle(1, 2, 3), 1e-6)
        // açık formülde (H'ler ayrı) karbon zinciri düz, H'ler dik
        val eth = read("CC").mol.explicitH()
        val r = Layout2D.layout(eth, crossH = true)
        assertEquals(r[0].y, r[1].y, 1e-9)
        // CO2 doğrusal
        val co2 = read("CO2").mol
        val c = Layout2D.layout(co2)
        assertEquals(0.0, abs(c[0].y - c[2].y), 1e-9)
        assertEquals(2.0, hypot(c[0].x - c[2].x, c[0].y - c[2].y), 1e-9)
    }

    // ------------------------------------------------------------------ 3B

    private fun angle3(p: List<Embed3D.V>, a: Int, b: Int, c: Int): Double {
        val u = (p[a] - p[b]).norm(); val v = (p[c] - p[b]).norm()
        return acos((u dot v).coerceIn(-1.0, 1.0)) * 180 / PI
    }

    @Test
    fun vseprGeometry() {
        val ch4 = read("CH4").mol.explicitH()
        val p = Embed3D.embed(ch4)
        for (i in 1..4) for (j in i + 1..4) assertEquals(109.47, angle3(p, i, 0, j), 0.1)
        for (i in 1..4) assertEquals(0.76 + 0.31, (p[i] - p[0]).len(), 1e-6)
        assertFalse(Embed3D.planar(p))
        val h2o = read("H2O").mol.explicitH()
        val w = Embed3D.embed(h2o)
        assertEquals(104.47, angle3(w, 1, 0, 2), 0.1)
        assertTrue(Embed3D.planar(w))
        assertTrue("O üstte", w[0].y > w[1].y)
        val nh3 = read("NH3").mol.explicitH()
        val n = Embed3D.embed(nh3)
        for (i in 1..3) for (j in i + 1..3) assertEquals(106.97, angle3(n, i, 0, j), 0.2)
        val co2 = Embed3D.embed(read("CO2").mol.explicitH())
        assertEquals(180.0, angle3(co2, 0, 1, 2), 1e-6)
        assertEquals((0.76 + 0.66) * 0.87, (co2[0] - co2[1]).len(), 1e-6)
        val eth = Embed3D.embed(read("C=C").mol.explicitH())
        assertTrue("eten düzlemsel", Embed3D.planar(eth))
        val bz = Embed3D.embed(read("C6H6").mol.explicitH())
        assertTrue("benzen düzlemsel", Embed3D.planar(bz))
        // etan: çapraz (anti) dizilim, H–C–C açıları dörtyüzlü
        val ethane = read("CC").mol.explicitH()
        val e = Embed3D.embed(ethane)
        for (h in ethane.neighbors(0).filter { it != 1 }) assertEquals(109.47, angle3(e, h, 0, 1), 0.1)
        // bütün bilinen moleküllerde atomlar üst üste binmez
        for (k in KnownMolecules.all) {
            val m = k.molecule().explicitH()
            val q = Embed3D.embed(m)
            for (i in q.indices) for (j in i + 1 until q.size) assertTrue("${k.name}: $i-$j çok yakın", (q[i] - q[j]).len() > 0.6) // en kısa bağ H–H 0,62 Å
        }
    }

    // ------------------------------------------------------------------ şekil

    @Test
    fun moleculeShapeFormulaViewsAndCornerLabel() {
        val m = Molecules.any2d
        val raw = (m.apply(null, "CH3COOH") as ParamApply.Ok).params
        assertEquals("CH3COOH", raw["formula"])
        val label = m.paths(raw).first { it.tag == "formula" }.label
        assertEquals("CH₃COOH", label)
        assertTrue(m.paths(raw).first { it.tag == "formula:1" }.label!!.contains("asetik asit"))
        // bilinmeyen kapalı formül: örnek yapı
        val ex = (m.apply(null, "C7H16") as ParamApply.Ok).params
        assertTrue(m.paths(ex).first { it.tag == "formula:1" }.label!!.startsWith("örnek yapı"))
        // hatalı formül: kimya hatası, değerler değişmez
        val bad = m.apply(raw, "CH5")
        assertTrue(bad is ParamApply.Failed)
        assertEquals(ParamError.Code.CHEMISTRY, (bad as ParamApply.Failed).error.code)
        assertEquals("HYDROGEN_COUNT", bad.error.detail)
        // görünümler: açık formülde her H yazılı, iskelette karbon yazısız, 3B'de her atom bir küre
        fun texts(view: String) = m.paths(raw + ("view" to view)).filter { it.tag?.startsWith("atom:") == true }
        val structural = texts(MolView.STRUCTURAL).mapNotNull { it.label }
        assertEquals(4, structural.count { it == "H" })
        assertEquals(2, structural.count { it == "C" })
        val skeletal = texts(MolView.SKELETAL).mapNotNull { it.label }
        assertFalse(skeletal.contains("C"))
        assertTrue(skeletal.toString(), skeletal.contains("OH") && skeletal.contains("O"))
        val condensed = texts(MolView.CONDENSED).mapNotNull { it.label }
        assertTrue(condensed.toString(), condensed.any { it == "CH₃" || it == "H₃C" })
        val balls = texts(MolView.BALL).filter { it.label == null }
        assertEquals(8, balls.size) // C2H4O2: 8 atom
        assertNotEquals(m.paths(raw + ("view" to MolView.BALL)).size, m.paths(raw).size)
        // ad yazılınca kapalı formül köşede
        val named = (m.apply(null, "glikoz") as ParamApply.Ok).params
        assertEquals("C₆H₁₂O₆", m.paths(named).first { it.tag == "formula" }.label)
        // eski kimlikler korunur, Düzenle'de formül açılır
        assertEquals("H2O", Molecules.water.editText(null))
        assertNotNull(ShapeLibrary.find("sci.chem.water_molecule")?.param)
        assertNotNull(ShapeLibrary.find("sci.chem.org.glucose")?.param)
        // pretty
        assertEquals("C₆H₁₂O₆", Chem.pretty("C6H12O6"))
        assertEquals("HC≡CH", Chem.pretty("HC#CH"))
    }

    @Test
    fun organicPresetsDrawFromTheirFormula() {
        val ids = listOf("propane", "ethylene", "ethanol", "acetic_acid", "phenol", "toluene", "glycine", "alanine", "glucose").map { "sci.chem.org.$it" }
        val chem = ShapeLibrary.categories.first { it.id == "sci_chemistry" }.shapes.map { it.id }
        for (id in ids) {
            assertTrue(id, id in chem)
            val model = ShapeLibrary.find(id)!!.param as MoleculeModel
            val r = model.result(model.editText(null))
            assertNotNull(id, r?.known)
            // başka formül yazınca çizim değişir
            val other = (model.apply(null, "CH4") as ParamApply.Ok).params
            assertNotEquals(id, model.paths(null).size, model.paths(other).size)
        }
    }
}
