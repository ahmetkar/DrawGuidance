package com.karapp.drawguidance.content.library.science.chem

/*
 * v46 periyodik tablonun 118 elementi: sembol, Türkçe ad, standart atom kütlesi (kararsız elementlerde en kararlı
 * izotopun kütle numarası), grup türü ve katman (Bohr) dağılımı. Element hücresi ve Bohr atom modeli şekilleri
 * yazılan elemente göre bunlardan yeniden çizilir.
 */

/** Element grubu (hücre rengi ve açıklaması). */
enum class ElementKind(val title: String, val color: String) {
    ALKALI("Alkali metal", "#FFCDD2"),
    ALKALINE("Toprak alkali metal", "#FFE0B2"),
    TRANSITION("Geçiş metali", "#FFF9C4"),
    POST_TRANSITION("Zayıf metal", "#DCEDC8"),
    METALLOID("Yarı metal", "#C8E6C9"),
    NONMETAL("Ametal", "#E3F2E1"),
    HALOGEN("Halojen", "#B2EBF2"),
    NOBLE("Soy gaz", "#D1C4E9"),
    LANTHANIDE("Lantanit", "#F8BBD0"),
    ACTINIDE("Aktinit", "#E1BEE7"),
}

/** Periyodik tablo elementi: [z] atom numarası, [mass] atom kütlesi, [stable] kararlı izotopu var mı. */
class PtElement(val z: Int, val symbol: String, val name: String, val mass: Double, val kind: ElementKind, val stable: Boolean) {
    /** Katmanlardaki elektron sayısı (K, L, M, …). */
    val shells: List<Int> get() = PeriodicTable.shells(z)
    /** Kütle numarası (en yaygın izotop yaklaşık: kütlenin yuvarlağı). */
    val massNumber: Int get() = Math.round(mass).toInt()
    /** Nötron sayısı. */
    val neutrons: Int get() = massNumber - z
    /** Periyot (katman sayısı). */
    val period: Int get() = shells.size
}

object PeriodicTable {
    private val raw = listOf(
        "H|Hidrojen|1.008", "He|Helyum|4.0026", "Li|Lityum|6.94", "Be|Berilyum|9.0122", "B|Bor|10.81",
        "C|Karbon|12.011", "N|Azot|14.007", "O|Oksijen|15.999", "F|Flor|18.998", "Ne|Neon|20.180",
        "Na|Sodyum|22.990", "Mg|Magnezyum|24.305", "Al|Alüminyum|26.982", "Si|Silisyum|28.085", "P|Fosfor|30.974",
        "S|Kükürt|32.06", "Cl|Klor|35.45", "Ar|Argon|39.948", "K|Potasyum|39.098", "Ca|Kalsiyum|40.078",
        "Sc|Skandiyum|44.956", "Ti|Titanyum|47.867", "V|Vanadyum|50.942", "Cr|Krom|51.996", "Mn|Mangan|54.938",
        "Fe|Demir|55.845", "Co|Kobalt|58.933", "Ni|Nikel|58.693", "Cu|Bakır|63.546", "Zn|Çinko|65.38",
        "Ga|Galyum|69.723", "Ge|Germanyum|72.630", "As|Arsenik|74.922", "Se|Selenyum|78.971", "Br|Brom|79.904",
        "Kr|Kripton|83.798", "Rb|Rubidyum|85.468", "Sr|Stronsiyum|87.62", "Y|İtriyum|88.906", "Zr|Zirkonyum|91.224",
        "Nb|Niyobyum|92.906", "Mo|Molibden|95.95", "Tc|Teknesyum|98", "Ru|Rutenyum|101.07", "Rh|Rodyum|102.91",
        "Pd|Paladyum|106.42", "Ag|Gümüş|107.87", "Cd|Kadmiyum|112.41", "In|İndiyum|114.82", "Sn|Kalay|118.71",
        "Sb|Antimon|121.76", "Te|Tellür|127.60", "I|İyot|126.90", "Xe|Ksenon|131.29", "Cs|Sezyum|132.91",
        "Ba|Baryum|137.33", "La|Lantan|138.91", "Ce|Seryum|140.12", "Pr|Praseodim|140.91", "Nd|Neodim|144.24",
        "Pm|Prometyum|145", "Sm|Samaryum|150.36", "Eu|Evropiyum|151.96", "Gd|Gadolinyum|157.25", "Tb|Terbiyum|158.93",
        "Dy|Disprosyum|162.50", "Ho|Holmiyum|164.93", "Er|Erbiyum|167.26", "Tm|Tulyum|168.93", "Yb|İterbiyum|173.05",
        "Lu|Lutesyum|174.97", "Hf|Hafniyum|178.49", "Ta|Tantal|180.95", "W|Volfram|183.84", "Re|Renyum|186.21",
        "Os|Osmiyum|190.23", "Ir|İridyum|192.22", "Pt|Platin|195.08", "Au|Altın|196.97", "Hg|Cıva|200.59",
        "Tl|Talyum|204.38", "Pb|Kurşun|207.2", "Bi|Bizmut|208.98", "Po|Polonyum|209", "At|Astatin|210",
        "Rn|Radon|222", "Fr|Fransiyum|223", "Ra|Radyum|226", "Ac|Aktinyum|227", "Th|Toryum|232.04",
        "Pa|Protaktinyum|231.04", "U|Uranyum|238.03", "Np|Neptünyum|237", "Pu|Plütonyum|244", "Am|Amerikyum|243",
        "Cm|Küriyum|247", "Bk|Berkelyum|247", "Cf|Kaliforniyum|251", "Es|Aynştaynyum|252", "Fm|Fermiyum|257",
        "Md|Mendelevyum|258", "No|Nobelyum|259", "Lr|Lavrensiyum|262", "Rf|Rutherfordiyum|267", "Db|Dubniyum|268",
        "Sg|Seaborgiyum|269", "Bh|Bohriyum|270", "Hs|Hassiyum|277", "Mt|Meitneriyum|278", "Ds|Darmstadtiyum|281",
        "Rg|Röntgenyum|282", "Cn|Kopernikyum|285", "Nh|Nihonyum|286", "Fl|Flerovyum|289", "Mc|Moskovyum|290",
        "Lv|Livermoryum|293", "Ts|Tenesin|294", "Og|Oganesson|294",
    )

    /** Kararlı izotopu olmayan elementler (Tc, Pm ve Bi'den sonrakiler; Th ve U doğada bol olsa da kararsız). */
    private fun stable(z: Int) = z != 43 && z != 61 && z <= 83

    private fun kind(z: Int, s: String): ElementKind = when {
        z in 57..71 -> ElementKind.LANTHANIDE
        z in 89..103 -> ElementKind.ACTINIDE
        s in setOf("He", "Ne", "Ar", "Kr", "Xe", "Rn", "Og") -> ElementKind.NOBLE
        s in setOf("F", "Cl", "Br", "I", "At", "Ts") -> ElementKind.HALOGEN
        s in setOf("Li", "Na", "K", "Rb", "Cs", "Fr") -> ElementKind.ALKALI
        s in setOf("Be", "Mg", "Ca", "Sr", "Ba", "Ra") -> ElementKind.ALKALINE
        s in setOf("B", "Si", "Ge", "As", "Sb", "Te") -> ElementKind.METALLOID
        s in setOf("H", "C", "N", "O", "P", "S", "Se") -> ElementKind.NONMETAL
        s in setOf("Al", "Ga", "In", "Sn", "Tl", "Pb", "Bi", "Po", "Nh", "Fl", "Mc", "Lv") -> ElementKind.POST_TRANSITION
        else -> ElementKind.TRANSITION
    }

    val all: List<PtElement> = raw.mapIndexed { i, r ->
        val (s, n, m) = r.split('|')
        PtElement(i + 1, s, n, m.toDouble(), kind(i + 1, s), stable(i + 1))
    }
    private val bySymbol = all.associateBy { it.symbol.lowercase() }
    private val byName = all.associateBy { it.name.lowercase(java.util.Locale("tr")) }

    operator fun get(z: Int): PtElement? = all.getOrNull(z - 1)

    /** Sembol ("na", "Na"), Türkçe ad ("sodyum") ya da atom numarası ("11") → element. */
    fun find(text: String): PtElement? {
        val t = text.trim()
        if (t.isEmpty()) return null
        t.toIntOrNull()?.let { return get(it) }
        return bySymbol[t.lowercase()] ?: byName[t.lowercase(java.util.Locale("tr"))]
    }

    /** Madelung (Aufbau) sırası: (n, l). */
    private val order = listOf(
        1 to 0, 2 to 0, 2 to 1, 3 to 0, 3 to 1, 4 to 0, 3 to 2, 4 to 1, 5 to 0, 4 to 2, 5 to 1, 6 to 0, 4 to 3, 5 to 2,
        6 to 1, 7 to 0, 5 to 3, 6 to 2, 7 to 1,
    )

    /** Aufbau kuralına uymayan bilinen dağılımlar: elektron [count] tanesi [from] katmanından [to] katmanına geçer. */
    private data class Shift(val from: Int, val to: Int, val count: Int)
    private val exceptions = mapOf(
        24 to Shift(4, 3, 1), 29 to Shift(4, 3, 1),
        41 to Shift(5, 4, 1), 42 to Shift(5, 4, 1), 44 to Shift(5, 4, 1), 45 to Shift(5, 4, 1), 46 to Shift(5, 4, 2), 47 to Shift(5, 4, 1),
        78 to Shift(6, 5, 1), 79 to Shift(6, 5, 1),
        // 4f → 5d / 5f → 6d (katman toplamı değişir: f n−2, d n−1)
        57 to Shift(4, 5, 1), 58 to Shift(4, 5, 1), 64 to Shift(4, 5, 1),
        89 to Shift(5, 6, 1), 90 to Shift(5, 6, 2), 91 to Shift(5, 6, 1), 92 to Shift(5, 6, 1), 93 to Shift(5, 6, 1), 96 to Shift(5, 6, 1),
    )

    /** Katmanlardaki elektron sayıları (K'dan dışa). */
    fun shells(z: Int): List<Int> {
        val count = IntArray(8)
        var left = z
        for ((n, l) in order) {
            if (left <= 0) break
            val take = minOf(left, 2 * (2 * l + 1))
            count[n] += take
            left -= take
        }
        exceptions[z]?.let { count[it.from] -= it.count; count[it.to] += it.count }
        val last = (7 downTo 1).firstOrNull { count[it] > 0 } ?: 1
        return (1..last).map { count[it] }
    }
}
