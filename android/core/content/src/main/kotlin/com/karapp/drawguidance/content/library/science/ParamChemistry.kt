package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.science.chem.Chem
import com.karapp.drawguidance.content.library.science.chem.ChemResult
import com.karapp.drawguidance.content.library.science.chem.Elements
import com.karapp.drawguidance.content.library.science.chem.Embed3D
import com.karapp.drawguidance.content.library.science.chem.Layout2D
import com.karapp.drawguidance.content.library.science.chem.Molecule
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 * v45 (8) formülden çizilen molekül şekilleri. Formül alanına element/molekül formülü, yarı açık formül ya da
 * SMILES yazılır; molekül yeniden çizilir, köşede formül (ve ad ya da "örnek yapı") yazar. Görünümler:
 * - structural: açık formül (bütün atomlar ve H'ler, ders kitabı biçimi),
 * - condensed: ağır atomlar H'leriyle (CH₃, OH),
 * - skeletal: iskelet (karbonlar köşe, yalnız heteroatomlar yazılı),
 * - 3d: top-çubuk model (VSEPR).
 */

/** Molekül görünümleri (kayıtta saklanan anahtarlar). */
object MolView {
    const val STRUCTURAL = "structural"
    const val CONDENSED = "condensed"
    const val SKELETAL = "skeletal"
    const val BALL = "3d"
    val all = listOf(STRUCTURAL, CONDENSED, SKELETAL, BALL)
}

internal class MoleculeModel(
    id: String,
    title: String,
    private val defaultFormula: String,
    private val defaultView: String,
    w: Float,
    h: Float,
    fit: Fit = Fit.BOX,
    tags: List<String>,
) : ParamModel(id, title, w, h, fit, tags + listOf("molekül", "formül", "kimya")) {
    override val defs = listOf(
        ParamDef("formula", "", "Formül", ParamKind.TEXT, defaultText = defaultFormula),
        ParamDef("view", "", "Görünüm", ParamKind.TEXT, defaultText = defaultView, choices = MolView.all),
    )

    override fun editText(raw: Map<String, String>?): String = values(raw).text("formula")

    /** Formül → okuma sonucu (son okunanlar önbellekte). */
    fun result(formula: String): ChemResult? = cache.getOrPut(formula) { Chem.tryRead(formula).first }

    override fun apply(raw: Map<String, String>?, text: String): ParamApply {
        val t = text.trim()
        val (res, err) = Chem.tryRead(t)
        if (res == null) return ParamApply.Failed(ParamError(ParamError.Code.CHEMISTRY, err?.toString() ?: ""))
        cache[t] = res
        return ParamApply.Ok(raw.orEmpty() + ("formula" to t))
    }

    override fun label(v: ParamValues): List<String> {
        val f = v.text("formula")
        val r = result(f) ?: return listOf(Chem.pretty(f))
        val hill = Chem.pretty(r.mol.hill())
        val first = Chem.pretty(f).let { if (r.kind == ChemResult.Kind.NAME) hill else it }
        val name = when {
            r.example -> "örnek yapı"
            r.known != null -> r.known.name
            else -> ""
        }
        val second = listOfNotNull(name.ifEmpty { null }, if (hill != first) hill else null, "M = ${num(r.mol.mass(), 2)} g/mol").joinToString(" · ")
        return listOf(first, second)
    }

    override val labelSize: Float get() = 0.07f * min(w, h)

    private val top: Float get() = labelSize * 2.6f + 0.02f

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues) {
        val r = result(v.text("formula")) ?: result(defaultFormula) ?: return
        when (v.text("view").takeIf { it in MolView.all } ?: defaultView) {
            MolView.BALL -> drawBall(b, lb, r.mol)
            MolView.CONDENSED -> draw2D(b, lb, r.mol, MolView.CONDENSED)
            MolView.SKELETAL -> draw2D(b, lb, r.mol, MolView.SKELETAL)
            else -> draw2D(b, lb, r.mol.explicitH(), MolView.STRUCTURAL)
        }
    }

    // ------------------------------------------------------------------ 2B

    private fun sup(charge: Int): String = when {
        charge == 0 -> ""
        abs(charge) == 1 -> if (charge > 0) "⁺" else "⁻"
        else -> "²³⁴⁵⁶".getOrNull(abs(charge) - 2)?.toString().orEmpty() + (if (charge > 0) "⁺" else "⁻")
    }

    private fun hs(n: Int) = when (n) { 0 -> ""; 1 -> "H"; else -> "H" + "₀₁₂₃₄₅₆₇₈₉"[n] }

    /** Atom yazısı (boşsa yazısız köşe). [left]: H'ler sola yazılır ("H₃C", "HO"). */
    private fun atomText(m: Molecule, i: Int, view: String, left: Boolean): String {
        val a = m.atoms[i]
        val c = sup(a.charge)
        return when (view) {
            MolView.STRUCTURAL -> a.el + c
            MolView.SKELETAL -> if (a.el == "C" && a.charge == 0 && m.degree(i) > 0) "" else if (left) hs(a.h) + a.el + c else a.el + hs(a.h) + c
            else -> if (left) hs(a.h) + a.el + c else a.el + hs(a.h) + c
        }
    }

    private fun textWidth(s: String, size: Float): Float = s.sumOf { ch -> if (ch in "₀₁₂₃₄₅₆₇₈₉⁺⁻²³") 0.4 else if (ch.isUpperCase()) 0.68 else 0.56 }.toFloat() * size

    private fun draw2D(b: VectorBuilder, lb: Lb, m: Molecule, view: String) {
        val pts = Layout2D.layout(m, crossH = view == MolView.STRUCTURAL)
        if (pts.isEmpty()) return
        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
        val availW = w - 0.08f; val availH = h - top - 0.04f
        val pad = 1.1
        val s = min(min(availW / (maxX - minX + pad), availH / (maxY - minY + pad)), 0.3 * min(w, h).toDouble()).toFloat()
        val ox = 0.04f + (availW - (maxX - minX).toFloat() * s) / 2f
        val oy = top + (availH - (maxY - minY).toFloat() * s) / 2f
        fun at(i: Int) = P(ox + ((pts[i].x - minX) * s).toFloat(), oy + ((maxY - pts[i].y) * s).toFloat())
        val fs = s * (if (view == MolView.STRUCTURAL) 0.42f else 0.38f)
        val texts = m.atoms.indices.map { i ->
            val p = at(i)
            val nbs = m.neighbors(i)
            val left = nbs.isNotEmpty() && nbs.all { at(it).x > p.x + 0.01f }
            atomText(m, i, view, left)
        }
        fun gap(i: Int, dx: Float, dy: Float): Float {
            val t = texts[i]
            if (t.isEmpty()) return 0f
            val hw = textWidth(t, fs) / 2f + fs * 0.12f
            val hh = fs * 0.55f
            val ax = abs(dx); val ay = abs(dy)
            return min(if (ax < 1e-6f) 9f else hw / ax, if (ay < 1e-6f) 9f else hh / ay).coerceAtMost(0.45f * s)
        }
        val rings = m.rings()
        for (bd in m.bonds) {
            val pa = at(bd.a); val pb = at(bd.b)
            val len = hypot(pb.x - pa.x, pb.y - pa.y)
            if (len < 1e-6f) continue
            val ux = (pb.x - pa.x) / len; val uy = (pb.y - pa.y) / len
            val ga = gap(bd.a, ux, uy); val gb = gap(bd.b, ux, uy)
            val a = P(pa.x + ux * ga, pa.y + uy * ga); val c = P(pb.x - ux * gb, pb.y - uy * gb)
            val nx = -uy; val ny = ux
            val off = s * 0.1f
            val ring = rings.firstOrNull { r -> r.contains(bd.a) && r.contains(bd.b) }
            when (bd.order) {
                1 -> b.line(a.x, a.y, c.x, c.y).width(1.3f).tag("bond")
                2 -> if (ring != null) {
                    // halkada: dış çizgi tam, iç çizgi halka merkezine doğru kısaltılmış
                    val cx = ring.map { at(it).x }.average().toFloat(); val cy = ring.map { at(it).y }.average().toFloat()
                    val side = if ((cx - (pa.x + pb.x) / 2f) * nx + (cy - (pa.y + pb.y) / 2f) * ny > 0) 1f else -1f
                    b.line(a.x, a.y, c.x, c.y).width(1.3f).tag("bond")
                    val sx = side * nx * off * 1.4f; val sy = side * ny * off * 1.4f
                    val k = 0.16f * len
                    b.line(a.x + sx + ux * k, a.y + sy + uy * k, c.x + sx - ux * k, c.y + sy - uy * k).width(1.3f).tag("bond")
                } else {
                    b.line(a.x + nx * off, a.y + ny * off, c.x + nx * off, c.y + ny * off).width(1.3f).tag("bond")
                    b.line(a.x - nx * off, a.y - ny * off, c.x - nx * off, c.y - ny * off).width(1.3f).tag("bond")
                }
                else -> {
                    b.line(a.x, a.y, c.x, c.y).width(1.2f).tag("bond")
                    val o3 = off * 1.3f
                    b.line(a.x + nx * o3, a.y + ny * o3, c.x + nx * o3, c.y + ny * o3).width(1.2f).tag("bond")
                    b.line(a.x - nx * o3, a.y - ny * o3, c.x - nx * o3, c.y - ny * o3).width(1.2f).tag("bond")
                }
            }
        }
        for (i in m.atoms.indices) {
            val t = texts[i]
            if (t.isEmpty()) continue
            val p = at(i)
            val col = when (m.atoms[i].el) { "O" -> "#C62828"; "N" -> "#1565C0"; "S" -> "#9E7C00"; "Cl", "F" -> "#2E7D32"; "Br" -> "#6D4C41"; "P" -> "#E65100"; else -> null }
            lb.c(p.x, p.y, t, fs, col).tag("atom:$i")
        }
    }

    // ------------------------------------------------------------------ 3B top-çubuk

    private fun light(hex: String): Boolean {
        val c = hex.removePrefix("#").toInt(16)
        val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255
        return 0.299 * r + 0.587 * g + 0.114 * bl > 150
    }

    private fun ballR(el: String): Double = 0.2 + 0.3 * (Elements[el]?.radius ?: 0.7)

    private fun drawBall(b: VectorBuilder, lb: Lb, mol: Molecule) {
        val m = mol.explicitH()
        val raw = Embed3D.embed(m)
        val p = if (Embed3D.planar(raw)) raw else Embed3D.view(raw, 22.0, 16.0)
        val rad = m.atoms.map { ballR(it.el) }
        val minX = p.indices.minOf { p[it].x - rad[it] }; val maxX = p.indices.maxOf { p[it].x + rad[it] }
        val minY = p.indices.minOf { p[it].y - rad[it] }; val maxY = p.indices.maxOf { p[it].y + rad[it] }
        val availW = w - 0.08f; val availH = h - top - 0.04f
        val s = min(min(availW / (maxX - minX), availH / (maxY - minY)), 0.34 * min(w, h).toDouble()).toFloat()
        val ox = 0.04f + (availW - (maxX - minX).toFloat() * s) / 2f
        val oy = top + (availH - (maxY - minY).toFloat() * s) / 2f
        val zMax = max(1.0, p.maxOf { abs(it.z) })
        fun at(i: Int) = P(ox + ((p[i].x - minX) * s).toFloat(), oy + ((maxY - p[i].y) * s).toFloat())
        fun persp(i: Int) = (1 + 0.08 * p[i].z / zMax).toFloat()
        // önce bağlar (uçlar kürelerin içinde kalır), sonra atomlar uzaktan yakına
        for (bd in m.bonds.sortedBy { (p[it.a].z + p[it.b].z) / 2 }) {
            val a = at(bd.a); val c = at(bd.b)
            val hw = s * 0.07f
            if (bd.order == 1) b.bar(a.x, a.y, c.x, c.y, hw).fill("#B8BEC6").width(0.8f).tag("bond")
            else {
                val u = unit(c.x - a.x, c.y - a.y)
                val k = bd.order
                for (j in 0 until k) {
                    val o = (j - (k - 1) / 2f) * hw * 1.9f
                    b.bar(a.x - u.y * o, a.y + u.x * o, c.x - u.y * o, c.y + u.x * o, hw * 0.6f).fill("#B8BEC6").width(0.7f).tag("bond")
                }
            }
        }
        for (i in m.atoms.indices.sortedBy { p[it].z }) {
            val e = Elements[m.atoms[i].el]
            val col = e?.color ?: "#9E9E9E"
            val c = at(i)
            val r = (rad[i] * s * persp(i)).toFloat()
            b.circle(c.x, c.y, r).fill(col).width(1f).tag("atom:$i")
            b.shine(c.x, c.y, r)
            lb.c(c.x, c.y + r * 0.04f, m.atoms[i].el + sup(m.atoms[i].charge), r * 0.95f, if (light(col)) null else "#FFFFFF")
        }
    }

    private companion object {
        private val cache: MutableMap<String, ChemResult?> = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, ChemResult?>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ChemResult?>?) = size > 64
        })
    }
}

/** Kimya kategorisindeki formülden çizilen moleküller (eski su/metan/benzen kimlikleri korunur). */
internal object Molecules {
    val water = MoleculeModel("sci.chem.water_molecule", "Su molekülü (H₂O)", "H2O", MolView.BALL, 1.2f, 1f,
        tags = listOf("su", "molekül", "top çubuk", "bağ açısı", "kovalent bağ"))
    val methane = MoleculeModel("sci.chem.methane", "Metan molekülü (CH₄)", "CH4", MolView.BALL, 1f, 1f, Fit.SQUARE,
        tags = listOf("metan", "molekül", "top çubuk", "tetrahedral", "organik", "karbon"))
    val benzene = MoleculeModel("sci.chem.benzene", "Benzen halkası (C₆H₆)", "C6H6", MolView.STRUCTURAL, 1f, 1f, Fit.SQUARE,
        tags = listOf("benzen", "aromatik", "halka", "organik", "kekule"))
    val any2d = MoleculeModel("sci.chem.molecule", "Molekül (formülden)", "C2H5OH", MolView.STRUCTURAL, 1.4f, 1f,
        tags = listOf("molekül", "yapı formülü", "açık formül", "organik", "smiles"))
    val any3d = MoleculeModel("sci.chem.molecule_3d", "Molekül modeli (3B)", "NH3", MolView.BALL, 1.2f, 1f,
        tags = listOf("molekül", "top çubuk", "vsepr", "molekül geometrisi", "3b"))
    val propane = MoleculeModel("sci.chem.org.propane", "Propan (alkan)", "CH3CH2CH3", MolView.STRUCTURAL, 1.4f, 1f,
        tags = listOf("alkan", "propan", "doymuş hidrokarbon", "organik"))
    val ethylene = MoleculeModel("sci.chem.org.ethylene", "Eten (alken)", "CH2=CH2", MolView.STRUCTURAL, 1.4f, 1f,
        tags = listOf("alken", "eten", "etilen", "çift bağ", "organik"))
    val ethanol = MoleculeModel("sci.chem.org.ethanol", "Etanol (alkol)", "CH3CH2OH", MolView.STRUCTURAL, 1.4f, 1f,
        tags = listOf("alkol", "etanol", "hidroksil", "organik"))
    val aceticAcid = MoleculeModel("sci.chem.org.acetic_acid", "Asetik asit (karboksilli asit)", "CH3COOH", MolView.STRUCTURAL, 1.4f, 1f,
        tags = listOf("karboksilli asit", "asetik asit", "sirke", "karboksil", "organik"))
    val phenol = MoleculeModel("sci.chem.org.phenol", "Fenol (benzen türevi)", "C6H5OH", MolView.STRUCTURAL, 1.4f, 1f,
        tags = listOf("benzen türevi", "fenol", "aromatik", "organik"))
    val toluene = MoleculeModel("sci.chem.org.toluene", "Toluen (benzen türevi)", "C6H5CH3", MolView.SKELETAL, 1.2f, 1f,
        tags = listOf("benzen türevi", "toluen", "aromatik", "iskelet formül", "organik"))
    val glycine = MoleculeModel("sci.chem.org.glycine", "Glisin (amino asit)", "NH2CH2COOH", MolView.STRUCTURAL, 1.4f, 1f,
        tags = listOf("amino asit", "glisin", "protein", "biyokimya", "organik"))
    val alanine = MoleculeModel("sci.chem.org.alanine", "Alanin (amino asit)", "CH3CH(NH2)COOH", MolView.STRUCTURAL, 1.4f, 1f,
        tags = listOf("amino asit", "alanin", "protein", "biyokimya", "organik"))
    val glucose = MoleculeModel("sci.chem.org.glucose", "Glikoz (C₆H₁₂O₆)", "C6H12O6", MolView.SKELETAL, 1.2f, 1f,
        tags = listOf("glikoz", "şeker", "karbonhidrat", "monosakkarit", "biyokimya", "organik"))

    val all: List<MoleculeModel> = listOf(water, methane, benzene, any2d, any3d, propane, ethylene, ethanol, aceticAcid, phenol, toluene, glycine, alanine, glucose)
}
