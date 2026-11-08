package com.karapp.drawguidance.content.library.science.chem

/*
 * v45 (8) küçük kimya motoru: element verisi, molekül çizgesi, değerlik denetimi, kapalı formül ve imza (eşyapı
 * tanıma). Çizim ve yerleşim Layout2D / Embed3D'de.
 */

/** Element bilgisi: [valences] olağan değerlikler (küçükten büyüğe), [ve] değerlik elektronu, [radius] kovalent yarıçap (Å). */
class Element(
    val symbol: String,
    val valences: List<Int>,
    val ve: Int,
    val mass: Double,
    val radius: Double,
    val color: String,
    val metal: Boolean = false,
)

object Elements {
    private val list = listOf(
        Element("H", listOf(1), 1, 1.008, 0.31, "#F4F6F8"),
        Element("B", listOf(3), 3, 10.81, 0.84, "#FFAB91"),
        Element("C", listOf(4), 4, 12.011, 0.76, "#455A64"),
        Element("N", listOf(3), 5, 14.007, 0.71, "#1E88E5"),
        Element("O", listOf(2), 6, 15.999, 0.66, "#E53935"),
        Element("F", listOf(1), 7, 18.998, 0.57, "#7CB342"),
        Element("Si", listOf(4), 4, 28.085, 1.11, "#BCAAA4"),
        Element("P", listOf(3, 5), 5, 30.974, 1.07, "#FB8C00"),
        Element("S", listOf(2, 4, 6), 6, 32.06, 1.05, "#FDD835"),
        Element("Cl", listOf(1), 7, 35.45, 1.02, "#43A047"),
        Element("Br", listOf(1), 7, 79.904, 1.20, "#8D6E63"),
        Element("I", listOf(1), 7, 126.90, 1.39, "#7E57C2"),
        Element("Se", listOf(2), 6, 78.97, 1.20, "#FFA726"),
        Element("Li", listOf(1), 1, 6.94, 1.28, "#B39DDB", metal = true),
        Element("Na", listOf(1), 1, 22.990, 1.66, "#9575CD", metal = true),
        Element("K", listOf(1), 1, 39.098, 2.03, "#7E57C2", metal = true),
        Element("Mg", listOf(2), 2, 24.305, 1.41, "#81C784", metal = true),
        Element("Ca", listOf(2), 2, 40.078, 1.76, "#66BB6A", metal = true),
        Element("Al", listOf(3), 3, 26.982, 1.21, "#B0BEC5", metal = true),
        Element("Fe", listOf(2, 3), 2, 55.845, 1.32, "#E57373", metal = true),
        Element("Cu", listOf(1, 2), 1, 63.546, 1.32, "#FF8A65", metal = true),
        Element("Zn", listOf(2), 2, 65.38, 1.22, "#90A4AE", metal = true),
        Element("Ag", listOf(1), 1, 107.87, 1.45, "#CFD8DC", metal = true),
        Element("Ba", listOf(2), 2, 137.33, 2.15, "#66BB6A", metal = true),
        Element("He", listOf(0), 2, 4.0026, 0.28, "#80DEEA"),
        Element("Ne", listOf(0), 8, 20.180, 0.58, "#80DEEA"),
        Element("Ar", listOf(0), 8, 39.948, 1.06, "#80DEEA"),
    )
    private val bySymbol = list.associateBy { it.symbol }

    operator fun get(symbol: String): Element? = bySymbol[symbol]
    fun known(symbol: String) = symbol in bySymbol
    val symbols: Set<String> get() = bySymbol.keys

    /**
     * Yüklü atomun değerlikleri: N⁺ 4, O⁺ 3, O⁻ 1, N⁻ 2, C⁻/C⁺ 3, B⁻ 4. Metal iyonları bağ yapmaz (0).
     */
    fun valences(symbol: String, charge: Int): List<Int> {
        val e = bySymbol[symbol] ?: return emptyList()
        if (e.metal) return if (charge == 0) e.valences else listOf(0)
        if (charge == 0) return e.valences
        return when (symbol) {
            "C", "Si" -> listOf(4 - kotlin.math.abs(charge))
            "B" -> listOf(3 - charge)
            "H" -> listOf(0)
            else -> e.valences.map { it + charge }.filter { it >= 0 }.ifEmpty { listOf(0) }
        }
    }
}

/** Atom: [h] bağlı (gösterilmeyen) hidrojen sayısı. */
data class Atom(val el: String, var h: Int = 0, var charge: Int = 0)

/** Bağ: [order] 1, 2, 3. */
class Bond(val a: Int, val b: Int, var order: Int = 1) {
    fun copy(order: Int = this.order) = Bond(a, b, order)
    fun other(i: Int) = if (a == i) b else a
    fun has(i: Int) = a == i || b == i
}

class Molecule(val atoms: MutableList<Atom> = ArrayList(), val bonds: MutableList<Bond> = ArrayList()) {
    fun add(a: Atom): Int { atoms += a; return atoms.size - 1 }
    fun bond(a: Int, b: Int, order: Int = 1): Bond { val x = Bond(a, b, order); bonds += x; return x }
    fun bondBetween(a: Int, b: Int) = bonds.firstOrNull { (it.a == a && it.b == b) || (it.a == b && it.b == a) }
    fun neighbors(i: Int): List<Int> = bonds.filter { it.has(i) }.map { it.other(i) }
    fun bondsOf(i: Int): List<Bond> = bonds.filter { it.has(i) }
    fun bondSum(i: Int) = bonds.filter { it.has(i) }.sumOf { it.order }
    fun degree(i: Int) = bonds.count { it.has(i) }

    fun copy(): Molecule = Molecule(atoms.map { it.copy() }.toMutableList(), bonds.map { it.copy() }.toMutableList())

    /** Element sayıları (bağlı H'ler dahil). */
    fun counts(): Map<String, Int> {
        val m = LinkedHashMap<String, Int>()
        for (a in atoms) {
            m[a.el] = (m[a.el] ?: 0) + 1
            if (a.h > 0) m["H"] = (m["H"] ?: 0) + a.h
        }
        return m
    }

    val charge: Int get() = atoms.sumOf { it.charge }

    /** Hill sırası kapalı formül anahtarı ("C2H6O"); toplam yük sonda (+/−). */
    fun hill(): String = Chem.hill(counts())

    /** Mol kütlesi (g/mol). */
    fun mass(): Double = counts().entries.sumOf { (Elements[it.key]?.mass ?: 0.0) * it.value }

    /** Atomun yalın elektron çifti sayısı (VSEPR). */
    fun lonePairs(i: Int): Int {
        val a = atoms[i]
        val ve = Elements[a.el]?.ve ?: return 0
        val e = Elements[a.el]!!
        if (e.metal) return 0
        return ((ve - a.charge - bondSum(i) - a.h) / 2).coerceAtLeast(0)
    }

    /** Değerlik denetimi: hatalı atomun indeksi ya da -1. */
    fun valenceError(): Int {
        for (i in atoms.indices) {
            val a = atoms[i]
            val used = bondSum(i) + a.h
            val allowed = Elements.valences(a.el, a.charge)
            if (allowed.isEmpty() || used !in allowed) return i
        }
        return -1
    }

    /** Bağlı bileşenler (atom indeksleri). */
    fun components(): List<List<Int>> {
        val seen = BooleanArray(atoms.size)
        val out = ArrayList<List<Int>>()
        for (s in atoms.indices) {
            if (seen[s]) continue
            val comp = ArrayList<Int>()
            val stack = ArrayDeque<Int>().apply { add(s) }
            seen[s] = true
            while (stack.isNotEmpty()) {
                val x = stack.removeLast(); comp += x
                for (y in neighbors(x)) if (!seen[y]) { seen[y] = true; stack.add(y) }
            }
            out += comp.sorted()
        }
        return out
    }

    /** Hidrojenleri ayrı atom yapan kopya (açık formül çizimi ve 3B için). Yeni H'ler sonda. */
    fun explicitH(): Molecule {
        val m = Molecule(atoms.map { it.copy(h = 0) }.toMutableList(), bonds.map { it.copy() }.toMutableList())
        for (i in atoms.indices) repeat(atoms[i].h) { val h = m.add(Atom("H")); m.bond(i, h, 1) }
        return m
    }

    /**
     * Eşyapı imzası (Weisfeiler–Lehman tarzı): atom türü, H sayısı, yük ve bağ düzenleri. Aynı yapı → aynı imza
     * (atom sırasından bağımsız). Bağlı H atomları önce bağlı oldukları atoma katılır.
     */
    fun signature(): String {
        val m = collapsedH()
        var lab = m.atoms.map { "${it.el}${it.h}${if (it.charge != 0) "c${it.charge}" else ""}" }
        repeat(6) {
            lab = m.atoms.indices.map { i ->
                val ns = m.bondsOf(i).map { b -> "${b.order}${lab[b.other(i)]}" }.sorted()
                (lab[i] + "(" + ns.joinToString(",") + ")").hashCode().toString(36)
            }
        }
        return lab.sorted().joinToString(".")
    }

    /** Ayrı H atomlarını (tek bağlı, yüksüz) bağlı oldukları atomun H sayısına katar. */
    fun collapsedH(): Molecule {
        val drop = BooleanArray(atoms.size)
        val out = atoms.map { it.copy() }.toMutableList()
        for (i in atoms.indices) {
            val a = atoms[i]
            if (a.el != "H" || a.charge != 0) continue
            val bs = bondsOf(i)
            if (bs.size != 1 || bs[0].order != 1) continue
            val o = bs[0].other(i)
            if (atoms[o].el == "H" && o < i) continue // H₂: birini tut
            if (atoms[o].el == "H") continue
            drop[i] = true
            out[o] = out[o].copy(h = out[o].h + 1 + a.h)
        }
        val map = IntArray(atoms.size) { -1 }
        val res = Molecule()
        for (i in atoms.indices) if (!drop[i]) map[i] = res.add(out[i])
        for (b in bonds) if (!drop[b.a] && !drop[b.b]) res.bond(map[b.a], map[b.b], b.order)
        return res
    }

    /** Halkalar (en küçük halkalar kümesi; küçük moleküller için). Her halka atom sırasıyla. */
    fun rings(): List<List<Int>> {
        val n = atoms.size
        val rank = bonds.size - n + components().size
        if (rank <= 0) return emptyList()
        // her bağdan geçen en küçük halka; sonra küçükten büyüğe, yeni bağ katanlar seçilir (bağımsız halkalar)
        val cand = ArrayList<List<Int>>()
        for (bd in bonds) {
            val u = bd.a; val v = bd.b
            val prev = IntArray(n) { -1 }
            val q = ArrayDeque<Int>().apply { add(u) }
            prev[u] = u
            while (q.isNotEmpty()) {
                val x = q.removeFirst()
                if (x == v) break
                for (y in neighbors(x)) {
                    if (prev[y] != -1) continue
                    if ((x == u && y == v) || (x == v && y == u)) continue
                    prev[y] = x; q.add(y)
                }
            }
            if (prev[v] == -1) continue
            val path = ArrayList<Int>()
            var c = v
            while (c != u) { path += c; c = prev[c] }
            path += u
            val ring = path.reversed()
            if (cand.none { it.size == ring.size && it.toSet() == ring.toSet() }) cand += ring
        }
        cand.sortBy { it.size }
        val out = ArrayList<List<Int>>()
        val covered = HashSet<Pair<Int, Int>>()
        fun edges(r: List<Int>) = r.indices.map { val a = r[it]; val b = r[(it + 1) % r.size]; if (a < b) a to b else b to a }
        for (r in cand) {
            if (out.size >= rank) break
            val e = edges(r)
            if (out.isEmpty() || e.any { it !in covered }) { out += r; covered += e }
        }
        return out
    }

    /**
     * Doymamışlığı doldurur: değerliği eksik komşu atomlar arasındaki bağ düzenlerini artırır (önce tek seçenekli
     * atomlar: yaprak öncelikli eşleme). [locked] bağları değişmez. Başarılıysa true.
     */
    fun fillUnsaturation(locked: Set<Bond> = emptySet()): Boolean {
        fun rem(i: Int): Int {
            val a = atoms[i]
            val used = bondSum(i) + a.h
            val v = Elements.valences(a.el, a.charge).firstOrNull { it >= used } ?: return -1
            return v - used
        }
        var guard = 0
        while (guard++ < 200) {
            val needy = atoms.indices.filter { rem(it) > 0 }
            if (needy.isEmpty()) return true
            fun options(i: Int) = bondsOf(i).filter { it !in locked && it.order < 3 && rem(it.other(i)) > 0 }
            val pick = needy.minByOrNull { i -> options(i).size.let { if (it == 0) 99 else it } } ?: return true
            val opts = options(pick)
            if (opts.isEmpty()) return false
            opts.minByOrNull { options(it.other(pick)).size }!!.order++
        }
        return false
    }
}

/** Kimya okuma hatası; [code] uygulamada çevrilir, [arg] atom/simge, [pos] metindeki konum. */
data class ChemError(val code: Code, val arg: String = "", val pos: Int = -1) {
    enum class Code {
        EMPTY,
        /** Okunamayan karakter / yazım ([pos]). */
        SYNTAX,
        /** Bilinmeyen element ([arg]). */
        UNKNOWN_ELEMENT,
        /** Değerlik kuralına uymayan atom ([arg] element). */
        VALENCE,
        /** Hidrojen sayısı yapıyla uyuşmuyor (doymamışlık negatif ya da kesirli). */
        HYDROGEN_COUNT,
        /** Çok büyük (çizim için en çok [Chem.MAX_ATOMS] ağır atom). */
        TOO_LARGE,
        /** Tanınmayan iyonik/metal bileşik. */
        IONIC,
        /** Parantez eşleşmiyor. */
        PARENTHESIS,
        /** Halka numarası kapanmadı. */
        RING,
    }

    override fun toString() = if (arg.isEmpty()) code.name else "${code.name}:$arg"
}

class ChemException(val error: ChemError) : RuntimeException(error.toString())
