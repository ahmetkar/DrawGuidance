package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.science.chem.PeriodicTable
import com.karapp.drawguidance.content.library.science.chem.PtElement
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.max
import kotlin.math.min

/*
 * v46 (4) yazılan elemente göre yeniden çizilen element şekilleri: periyodik tablo hücresi ve Bohr atom modeli.
 * Formül alanına sembol ("Na"), Türkçe ad ("sodyum"), atom numarası ("11") ya da "Z = 11" yazılır. Kimlikler eski
 * sabit çizimlerle aynıdır; eski kayıtlar varsayılan elementle (karbon hücresi, sodyum atomu) aynı görünür.
 */

internal abstract class ElementModel(id: String, title: String, w: Float, h: Float, fit: Fit, tags: List<String>, private val defaultZ: Int) :
    ParamModel(id, title, w, h, fit, tags + listOf("element", "formül", "kimya")) {
    override val defs = listOf(
        ParamDef("z", "Z", "Atom numarası", ParamKind.INT, default = defaultZ.toDouble(), min = 1.0, max = 118.0, step = 1.0),
    )

    fun element(v: ParamValues): PtElement = PeriodicTable[v.num("z").toInt()] ?: PeriodicTable[defaultZ]!!

    override fun editText(raw: Map<String, String>?): String = element(values(raw)).symbol

    override fun apply(raw: Map<String, String>?, text: String): ParamApply {
        // "Z = 11" ya da yalın "11", "Na", "sodyum" ("Zn" sembolü atama sayılmaz)
        val t = Regex("^[Zz]\\s*=\\s*(.+)$").find(text.trim())?.groupValues?.get(1) ?: text.trim()
        val e = PeriodicTable.find(t)
        if (e != null) return ParamApply.Ok(withValue(raw, "z", e.z.toDouble()))
        // "Z = 5 + 6" gibi hesaplı yazım: genel yola bırak
        return super.apply(raw, text)
    }
}

/** Periyodik tablo hücresi: atom numarası, sembol, ad, atom kütlesi ve grup rengi. */
internal object PeriodicCellModel : ElementModel(
    "sci.chem.periodic_cell", "Periyodik tablo hücresi", 0.9f, 1f, Fit.BOX,
    listOf("periyodik tablo", "atom numarası", "kütle numarası", "sembol"), defaultZ = 6,
) {
    /** Köşede elementin grubu (hücrenin sağ üstünde). */
    override fun label(v: ParamValues): List<String> = listOf(element(v).kind.title)
    override val labelRight = true
    override val labelSize = 0.045f

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val e = element(v)
        roundRect(0.05f, 0.1f, 0.8f, 0.85f, 0.04f).fill(e.kind.color).width(1.5f).tag("cell")
        lb.t(0.11f, 0.15f, "${e.z}", 0.12f).tag("z")
        // uzun semboller (Og) ve adlar sığsın
        val symSize = if (e.symbol.length > 1) 0.3f else 0.36f
        lb.c(0.45f, 0.49f, e.symbol, symSize).tag("symbol")
        val nameSize = min(0.1f, 0.62f / max(1, e.name.length) / 0.56f)
        lb.c(0.45f, 0.75f, e.name, nameSize).tag("name")
        val mass = if (e.stable) ParamModel.num(e.mass, 3) else "(${e.massNumber})"
        lb.c(0.45f, 0.87f, mass, 0.085f).tag("mass")
    }
}

/** Bohr atom modeli: çekirdekte proton/nötron sayısı, katmanlarda elektronlar. */
internal object BohrAtomModel : ElementModel(
    "sci.chem.bohr_atom", "Bohr atom modeli", 1f, 1f, Fit.SQUARE,
    listOf("atom", "elektron", "çekirdek", "katman", "yörünge", "proton", "nötron"), defaultZ = 11,
) {
    override fun label(v: ParamValues): List<String> {
        val e = element(v)
        return listOf("${e.symbol}: ${e.shells.joinToString(", ")}")
    }
    override val labelSize = 0.06f

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val e = element(v)
        val shells = e.shells
        val c = 0.5f
        val nucR = 0.085f
        val outer = 0.41f
        val gap = (outer - nucR - 0.03f) / shells.size
        val radii = shells.indices.map { nucR + 0.03f + gap * (it + 1) }
        for (r in radii) circle(c, c, r).width(0.9f).tag("shell")
        // çekirdek
        circle(c, c, nucR).fill("#FFCDD2").width(1f).tag("nucleus")
        lb.c(c, c - 0.025f, "${e.z}p", 0.042f, "#C62828")
        lb.c(c, c + 0.03f, "${e.neutrons}n", 0.042f, "#455A64")
        // elektronlar: katman başına eşit aralıkla (kalabalık katmanda küçülür)
        for (k in shells.indices) {
            val n = shells[k]
            val r = radii[k]
            val er = min(0.022f, (2 * Math.PI.toFloat() * r / n) * 0.32f)
            for (i in 0 until n) {
                val p = onCircle(c, c, r, -90f + 360f * i / n + k * 17f)
                circle(p.x, p.y, er).fill(Sc.BLUE).width(0.6f).tag("electron")
            }
        }
        lb.c(c, 0.96f, e.name, 0.05f)
    }
}
