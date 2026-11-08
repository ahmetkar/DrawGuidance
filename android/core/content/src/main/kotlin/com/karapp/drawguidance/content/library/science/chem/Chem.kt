package com.karapp.drawguidance.content.library.science.chem

/*
 * v45 (8) formül okuma. Girdi sırasıyla:
 *  1. bilinen ad ("su", "etanol", "glucose"),
 *  2. kapalı formül (her element bir kez: "C2H6O", "H2SO4", "C6H12O6"): bilinen tablodan (Hill anahtarı); yoksa
 *     değerlik kurallarıyla denetlenip örnek bir izomer kurulur ("örnek yapı"),
 *  3. H içeren yarı açık formül ("CH3-CH2-OH", "CH3COOH", "CH2=CH2", "(CH3)3COH", "C6H5OH"),
 *  4. SMILES benzeri ("CCO", "C1=CC=CC=C1", "c1ccccc1", "OC(=O)C", "[Na+].[Cl-]").
 * Sonuçta yapı bilinen tabloyla imzadan eşlenir (ad gösterilir).
 */

/** Okuma sonucu: [mol] H'leri atomlara bağlı sayı olarak taşır; [known] eşleşen bilinen molekül; [example] örnek yapı. */
data class ChemResult(
    val mol: Molecule,
    val input: String,
    val known: KnownMolecule?,
    val example: Boolean,
    val kind: Kind,
) {
    enum class Kind { NAME, FORMULA, CONDENSED, SMILES }
}

object Chem {
    /** Çizim için en çok ağır atom. */
    const val MAX_ATOMS = 40

    private val SUB = "₀₁₂₃₄₅₆₇₈₉"

    /** Hill sırası: C, H, sonra alfabetik (karbon yoksa hepsi alfabetik). */
    fun hill(counts: Map<String, Int>): String {
        val keys = counts.keys.filter { (counts[it] ?: 0) > 0 }
        val order = if ("C" in keys) listOf("C") + (if ("H" in keys) listOf("H") else emptyList()) + keys.filter { it != "C" && it != "H" }.sorted() else keys.sorted()
        return order.joinToString("") { el -> val n = counts[el]!!; if (n == 1) el else "$el$n" }
    }

    /** Rakamları alt simge, yükleri üst simge yazar ("C2H5OH" → "C₂H₅OH"). */
    fun pretty(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val prev = sb.lastOrNull()
            if (c.isDigit() && prev != null && (prev.isLetter() || prev == ')' || SUB.indexOf(prev) >= 0)) sb.append(SUB[c - '0'])
            else sb.append(c)
            i++
        }
        return sb.toString().replace("#", "≡")
    }

    private fun normalize(text: String): String {
        val sb = StringBuilder()
        for (c in text.trim()) {
            val sub = SUB.indexOf(c)
            when {
                sub >= 0 -> sb.append('0' + sub)
                c == '−' || c == '–' || c == '—' -> sb.append('-')
                c == '≡' -> sb.append('#')
                c == ' ' || c == '\t' -> {}
                c == '⁺' -> sb.append('+')
                c == '⁻' -> sb.append('-')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Metni okur; okunamazsa [ChemException]. */
    fun read(text: String): ChemResult {
        val raw = text.trim()
        if (raw.isEmpty()) throw ChemException(ChemError(ChemError.Code.EMPTY))
        KnownMolecules.byName(raw)?.let { k -> return ChemResult(k.molecule(), raw, k, false, ChemResult.Kind.NAME) }
        val s = normalize(raw)
        if (s.isEmpty()) throw ChemException(ChemError(ChemError.Code.EMPTY))
        val formula = pureFormula(s)
        if (formula != null) {
            KnownMolecules.byHill(hill(formula))?.let { k -> return ChemResult(k.molecule(), raw, k, false, ChemResult.Kind.FORMULA) }
            val mol = Isomer.example(formula)
            return finish(mol, raw, ChemResult.Kind.FORMULA, example = true)
        }
        val hasH = hasPlainH(s)
        if (hasH) {
            val cond = try { Condensed.parse(s) } catch (e: ChemException) {
                // SMILES'ta açık H yalnız köşeli parantezde olur; yine de dene
                val sm = try { Smiles.parse(s) } catch (_: ChemException) { null }
                if (sm != null) return finish(sm, raw, ChemResult.Kind.SMILES, false)
                throw e
            }
            return finish(cond, raw, ChemResult.Kind.CONDENSED, false)
        }
        return finish(Smiles.parse(s), raw, ChemResult.Kind.SMILES, false)
    }

    /** Okumayı dener: sonuç ya da hata. */
    fun tryRead(text: String): Pair<ChemResult?, ChemError?> = try { read(text) to null } catch (e: ChemException) { null to e.error }

    private fun finish(mol: Molecule, raw: String, kind: ChemResult.Kind, example: Boolean): ChemResult {
        val heavy = mol.atoms.count { it.el != "H" }
        if (heavy > MAX_ATOMS) throw ChemException(ChemError(ChemError.Code.TOO_LARGE, MAX_ATOMS.toString()))
        val bad = mol.valenceError()
        if (bad >= 0) throw ChemException(ChemError(ChemError.Code.VALENCE, mol.atoms[bad].el))
        val known = KnownMolecules.bySignature(mol)
        return ChemResult(mol, raw, known, example, kind)
    }

    /** Köşeli parantez dışında 'H' (element olarak) var mı? */
    private fun hasPlainH(s: String): Boolean {
        var depth = 0
        for (i in s.indices) {
            when (s[i]) {
                '[' -> depth++
                ']' -> depth--
                'H' -> if (depth == 0 && (i + 1 >= s.length || !s[i + 1].isLowerCase())) return true
            }
        }
        return false
    }

    /** Her element bir kez geçen kapalı formül mü? Öyleyse sayılar. */
    private fun pureFormula(s: String): Map<String, Int>? {
        if (s.any { !(it.isLetterOrDigit() || it == '(' || it == ')') }) return null
        val tokens = elementTokens(s) ?: return null
        val els = tokens.filter { it.first != "(" && it.first != ")" }.map { it.first }
        if (els.size != els.toSet().size) return null
        return formulaCounts(s)
    }

    private fun elementTokens(s: String): List<Pair<String, Int>>? {
        val out = ArrayList<Pair<String, Int>>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '(' -> { out += "(" to 0; i++ }
                c == ')' -> { i++; var n = 0; var any = false; while (i < s.length && s[i].isDigit()) { n = n * 10 + (s[i] - '0'); i++; any = true }; out += ")" to (if (any) n else 1) }
                c.isUpperCase() -> {
                    var el = c.toString()
                    if (i + 1 < s.length && s[i + 1].isLowerCase() && Elements.known(el + s[i + 1])) { el += s[i + 1]; i++ }
                    if (!Elements.known(el)) return null
                    i++
                    var n = 0; var any = false
                    while (i < s.length && s[i].isDigit()) { n = n * 10 + (s[i] - '0'); i++; any = true }
                    out += el to (if (any) n else 1)
                }
                else -> return null
            }
        }
        return out
    }

    /** Kapalı formülün element sayıları ("Ca(OH)2" → Ca 1, O 2, H 2); okunamazsa null. */
    fun formulaCounts(s: String): Map<String, Int>? {
        val tokens = elementTokens(s) ?: return null
        val stack = ArrayDeque<LinkedHashMap<String, Int>>().apply { add(LinkedHashMap()) }
        for ((t, n) in tokens) {
            when (t) {
                "(" -> stack.add(LinkedHashMap())
                ")" -> {
                    if (stack.size < 2) return null
                    val top = stack.removeLast()
                    val m = stack.last()
                    for ((k, v) in top) m[k] = (m[k] ?: 0) + v * n
                }
                else -> { if (n <= 0) return null; val m = stack.last(); m[t] = (m[t] ?: 0) + n }
            }
        }
        if (stack.size != 1 || stack.last().isEmpty()) return null
        return stack.last()
    }
}

// ------------------------------------------------------------------ SMILES

internal object Smiles {
    private val ORGANIC = setOf("B", "C", "N", "O", "P", "S", "F", "Cl", "Br", "I")
    private val AROMATIC = setOf('b', 'c', 'n', 'o', 'p', 's')

    fun parse(s: String): Molecule {
        val m = Molecule()
        val aromatic = HashSet<Int>()
        val bracket = HashSet<Int>()
        val aroBonds = HashSet<Bond>()
        val branch = ArrayDeque<Int?>()
        val rings = HashMap<Int, Pair<Int, Int?>>()
        var prev: Int? = null
        var pending: Int? = null // 1,2,3; 0 = aromatik ':'
        var i = 0
        fun err(code: ChemError.Code, arg: String = ""): Nothing = throw ChemException(ChemError(code, arg, i))
        fun connect(a: Int, b: Int, explicit: Int?) {
            if (m.bondBetween(a, b) != null || a == b) err(ChemError.Code.SYNTAX)
            val order = explicit?.takeIf { it > 0 } ?: 1
            val bd = m.bond(a, b, order)
            if ((explicit == null || explicit == 0) && a in aromatic && b in aromatic) aroBonds += bd
        }
        fun addAtom(atom: Atom, aro: Boolean, br: Boolean) {
            val idx = m.add(atom)
            if (aro) aromatic += idx
            if (br) bracket += idx
            prev?.let { connect(it, idx, pending) }
            pending = null
            prev = idx
        }
        while (i < s.length) {
            val c = s[i]
            when {
                c == '(' -> { if (prev == null) err(ChemError.Code.SYNTAX); branch.add(prev); i++ }
                c == ')' -> { if (branch.isEmpty()) err(ChemError.Code.PARENTHESIS); prev = branch.removeLast(); i++ }
                c == '-' -> { pending = 1; i++ }
                c == '=' -> { pending = 2; i++ }
                c == '#' -> { pending = 3; i++ }
                c == ':' -> { pending = 0; i++ }
                c == '/' || c == '\\' -> { i++ }
                c == '.' -> { prev = null; pending = null; i++ }
                c.isDigit() || c == '%' -> {
                    val num: Int
                    if (c == '%') {
                        if (i + 2 >= s.length || !s[i + 1].isDigit() || !s[i + 2].isDigit()) err(ChemError.Code.SYNTAX)
                        num = (s[i + 1] - '0') * 10 + (s[i + 2] - '0'); i += 3
                    } else { num = c - '0'; i++ }
                    val p = prev ?: err(ChemError.Code.SYNTAX)
                    val open = rings.remove(num)
                    if (open == null) { rings[num] = p to pending } else {
                        val order = pending ?: open.second
                        connect(open.first, p, order)
                    }
                    pending = null
                }
                c == '[' -> {
                    val end = s.indexOf(']', i)
                    if (end < 0) err(ChemError.Code.PARENTHESIS)
                    val body = s.substring(i + 1, end)
                    var k = 0
                    while (k < body.length && body[k].isDigit()) k++
                    if (k >= body.length) err(ChemError.Code.SYNTAX)
                    var el: String
                    var aro = false
                    if (body[k].isLowerCase() && body[k] in AROMATIC) { el = body[k].uppercaseChar().toString(); aro = true; k++ } else {
                        el = body[k].toString(); k++
                        if (k < body.length && body[k].isLowerCase() && Elements.known(el + body[k])) { el += body[k]; k++ }
                    }
                    if (!Elements.known(el)) err(ChemError.Code.UNKNOWN_ELEMENT, el)
                    var h = 0
                    if (k < body.length && body[k] == 'H' && el != "H") {
                        k++; h = 1
                        if (k < body.length && body[k].isDigit()) { h = body[k] - '0'; k++ }
                    }
                    var charge = 0
                    while (k < body.length && (body[k] == '+' || body[k] == '-')) {
                        val sign = if (body[k] == '+') 1 else -1
                        k++
                        if (k < body.length && body[k].isDigit()) { charge += sign * (body[k] - '0'); k++ } else charge += sign
                    }
                    if (k != body.length) err(ChemError.Code.SYNTAX)
                    i = end + 1
                    addAtom(Atom(el, h, charge), aro, true)
                }
                c.isLetter() -> {
                    var el: String
                    var aro = false
                    if (c.isUpperCase()) {
                        el = c.toString()
                        if (i + 1 < s.length && ((c == 'C' && s[i + 1] == 'l') || (c == 'B' && s[i + 1] == 'r'))) { el += s[i + 1]; i++ }
                        if (el !in ORGANIC) err(ChemError.Code.UNKNOWN_ELEMENT, el)
                    } else {
                        if (c !in AROMATIC) err(ChemError.Code.SYNTAX)
                        el = c.uppercaseChar().toString(); aro = true
                    }
                    i++
                    addAtom(Atom(el), aro, false)
                }
                else -> err(ChemError.Code.SYNTAX)
            }
        }
        if (rings.isNotEmpty()) throw ChemException(ChemError(ChemError.Code.RING, rings.keys.first().toString(), s.length))
        if (branch.isNotEmpty()) throw ChemException(ChemError(ChemError.Code.PARENTHESIS, pos = s.length))
        if (m.atoms.isEmpty()) throw ChemException(ChemError(ChemError.Code.EMPTY))
        kekulize(m, aromatic, bracket, aroBonds)
        // örtük hidrojenler
        for (idx in m.atoms.indices) {
            if (idx in bracket) continue
            val a = m.atoms[idx]
            val used = m.bondSum(idx)
            val v = Elements.valences(a.el, a.charge).firstOrNull { it >= used } ?: throw ChemException(ChemError(ChemError.Code.VALENCE, a.el))
            a.h = v - used
        }
        return m
    }

    /** Aromatik bağlara dönüşümlü çift bağ yerleştirir (geri izlemeli eşleme). */
    private fun kekulize(m: Molecule, aromatic: Set<Int>, bracket: Set<Int>, aroBonds: Set<Bond>) {
        if (aroBonds.isEmpty()) return
        val need = aromatic.filter { i ->
            val a = m.atoms[i]
            when (a.el) {
                "C", "B" -> true
                "N", "P" -> if (i in bracket) a.h == 0 && a.charge == 0 && m.degree(i) == 2 else m.degree(i) == 2
                else -> false
            }
        }.sorted()
        val taken = HashSet<Int>()
        fun solve(k: Int): Boolean {
            if (k == need.size) return true
            val a = need[k]
            if (a in taken) return solve(k + 1)
            for (b in aroBonds.filter { it.has(a) }) {
                val o = b.other(a)
                if (o in taken || o !in need) continue
                taken += a; taken += o; b.order = 2
                if (solve(k + 1)) return true
                taken -= a; taken -= o; b.order = 1
            }
            return false
        }
        if (!solve(0)) throw ChemException(ChemError(ChemError.Code.VALENCE, "aromatic"))
    }
}

// ------------------------------------------------------------------ Yarı açık formül

/**
 * "CH3-CH2-OH", "CH3COOH", "CH3CH(CH3)CH3", "(CH3)3COH", "HC≡CH", "C2H5OH", "C6H5COOH". Ağır atomlar zincir kurar;
 * H'siz O/S önceki karbonda yer varsa karbonildir (=O), sondaki H'siz N nitrildir (≡N), sayılı H'siz atom ("CO2",
 * "CCl3") önceki atoma dal olarak bağlanır. Kalan doymamışlık komşular arasında çoklu bağla doldurulur.
 */
internal object Condensed {
    private sealed class Unit(val bond: Int?) {
        class A(val el: String, val count: Int, val h: Int, bond: Int?) : Unit(bond)
        class G(val units: List<Unit>, val count: Int, bond: Int?) : Unit(bond)
    }

    fun parse(s: String): Molecule {
        var i = 0
        fun err(code: ChemError.Code, arg: String = ""): Nothing = throw ChemException(ChemError(code, arg, i))
        fun digits(): Int? { var n = 0; var any = false; while (i < s.length && s[i].isDigit()) { n = n * 10 + (s[i] - '0'); i++; any = true }; return if (any) n else null }
        fun seq(depth: Int): List<Unit> {
            val out = ArrayList<Unit>()
            while (i < s.length) {
                var bond: Int? = null
                when (s[i]) {
                    '-' -> { bond = 1; i++ }
                    '=' -> { bond = 2; i++ }
                    '#' -> { bond = 3; i++ }
                }
                if (i >= s.length) err(ChemError.Code.SYNTAX)
                val c = s[i]
                when {
                    c == ')' -> { if (bond != null || depth == 0) err(if (depth == 0) ChemError.Code.PARENTHESIS else ChemError.Code.SYNTAX); return out }
                    c == '(' -> {
                        i++
                        val inner = seq(depth + 1)
                        if (i >= s.length || s[i] != ')') err(ChemError.Code.PARENTHESIS)
                        i++
                        if (inner.isEmpty()) err(ChemError.Code.SYNTAX)
                        out += Unit.G(inner, digits() ?: 1, bond)
                    }
                    c.isUpperCase() -> {
                        var el = c.toString()
                        if (i + 1 < s.length && s[i + 1].isLowerCase() && Elements.known(el + s[i + 1])) { el += s[i + 1]; i++ }
                        if (!Elements.known(el)) err(ChemError.Code.UNKNOWN_ELEMENT, el)
                        i++
                        val count = digits() ?: 1
                        var h = 0
                        if (el != "H" && i < s.length && s[i] == 'H' && (i + 1 >= s.length || !s[i + 1].isLowerCase())) { i++; h = digits() ?: 1 }
                        if (count == 0) err(ChemError.Code.SYNTAX)
                        out += Unit.A(el, count, h, bond)
                    }
                    else -> err(ChemError.Code.SYNTAX)
                }
            }
            if (depth > 0) err(ChemError.Code.PARENTHESIS)
            return out
        }
        val units = seq(0)
        if (units.isEmpty()) err(ChemError.Code.EMPTY)
        val m = Molecule()
        val locked = HashSet<Bond>()
        fun maxVal(a: Int) = Elements.valences(m.atoms[a].el, m.atoms[a].charge).maxOrNull() ?: 0
        fun rem(a: Int) = maxVal(a) - m.bondSum(a) - m.atoms[a].h
        fun link(a: Int, b: Int, order: Int?, lock: Boolean): Bond { val bd = m.bond(a, b, order ?: 1); if (lock || order != null) locked += bd; return bd }

        fun build(list: List<Unit>, attach: Int?): Int? {
            var cur: Int? = attach
            var first: Int? = null
            var pendingH = 0
            val pendingGroups = ArrayList<Unit.G>()
            fun placed(idx: Int, bond: Int?) {
                val c = cur
                if (c != null) link(c, idx, bond, bond != null)
                if (first == null) first = idx
                cur = idx
                if (pendingGroups.isNotEmpty()) { val gs = pendingGroups.toList(); pendingGroups.clear(); for (g in gs) repeat(g.count) { build(g.units, idx) } }
            }
            for ((k, u) in list.withIndex()) {
                val more = list.subList(k + 1, list.size).any { it is Unit.G || (it is Unit.A && it.el != "H") }
                when (u) {
                    is Unit.G -> {
                        val c = cur
                        if (c == null) { pendingGroups += u; continue }
                        repeat(u.count) {
                            // grubun bağ işareti ("(=O)") ilk atoma geçer
                            val inner = if (u.bond != null && u.units.first() is Unit.A) {
                                val f = u.units.first() as Unit.A
                                listOf(Unit.A(f.el, f.count, f.h, u.bond)) + u.units.drop(1)
                            } else u.units
                            build(inner, c)
                        }
                    }
                    is Unit.A -> {
                        if (u.el == "H") {
                            val c = cur
                            if (c == null || (attach != null && c == attach && first == null)) pendingH += u.count else m.atoms[c].h += u.count
                            continue
                        }
                        // alkil (C2H5, C3H7…) ve fenil (C6H5)
                        if (u.el == "C" && u.count > 1 && u.h == 2 * u.count + 1) {
                            val ids = (0 until u.count).map { m.add(Atom("C", 2)) }
                            for (j in 1 until ids.size) link(ids[j - 1], ids[j], 1, true)
                            if (cur == null && attach == null) {
                                m.atoms[ids.first()].h = 3
                                cur = ids.last(); first = ids.first()
                                for (g in pendingGroups) repeat(g.count) { build(g.units, ids.last()) }
                                pendingGroups.clear()
                            } else {
                                if (more) err(ChemError.Code.SYNTAX, "C${u.count}H${u.h}")
                                m.atoms[ids.last()].h = 3
                                link(cur!!, ids.first(), u.bond ?: 1, true)
                                if (first == null) first = ids.first()
                            }
                            continue
                        }
                        if (u.el == "C" && u.count == 6 && u.h == 5) {
                            val ids = (0 until 6).map { m.add(Atom("C", if (it == 0) 0 else 1)) }
                            for (j in 0 until 6) link(ids[j], ids[(j + 1) % 6], if (j % 2 == 0) 2 else 1, true)
                            placed(ids[0], u.bond)
                            continue
                        }
                        if (u.count > 1) {
                            val c = cur
                            if (u.h > 0 || c == null) err(ChemError.Code.SYNTAX, "${u.el}${u.count}")
                            repeat(u.count) { val idx = m.add(Atom(u.el, 0)); link(c, idx, u.bond, u.bond != null) }
                            continue
                        }
                        val atom = Atom(u.el, u.h + pendingH)
                        pendingH = 0
                        val c = cur
                        if (c != null && u.bond == null && atom.h == 0 && (u.el == "O" || u.el == "S") && rem(c) >= 2 + (if (more) 1 else 0) && m.atoms[c].el == "C") {
                            val idx = m.add(atom); link(c, idx, 2, true); if (first == null) first = idx
                            continue
                        }
                        if (c != null && u.bond == null && atom.h == 0 && u.el == "N" && !more && rem(c) >= 3 && m.atoms[c].el == "C") {
                            val idx = m.add(atom); link(c, idx, 3, true); if (first == null) first = idx
                            continue
                        }
                        placed(m.add(atom), u.bond)
                    }
                }
            }
            if (pendingGroups.isNotEmpty() || pendingH > 0) {
                val c = cur ?: err(ChemError.Code.SYNTAX)
                m.atoms[c].h += pendingH
                for (g in pendingGroups) repeat(g.count) { build(g.units, c) }
            }
            return first
        }
        build(units, null)
        if (m.components().size > 1) err(ChemError.Code.SYNTAX)
        if (!m.fillUnsaturation(locked)) {
            val bad = m.atoms.indices.firstOrNull { rem(it) != 0 } ?: 0
            throw ChemException(ChemError(ChemError.Code.VALENCE, m.atoms[bad].el))
        }
        return m
    }
}

// ------------------------------------------------------------------ Örnek izomer

/**
 * Kapalı formülden değerlik kurallarına uyan örnek bir yapı: karbon zinciri (doymamışlık ≥ 4 ve ≥ 6 C ise benzen
 * halkası), N başa, O sona (iki O ve doymamışlık varsa karboksil), halojenler karbonlara; karbonsuzsa merkez atom.
 * Kalan doymamışlık önce C=O, sonra C=C ile; olmazsa halka kapanır.
 */
internal object Isomer {
    private val MULTI_ORDER = listOf("Si", "B", "N", "P", "O", "S", "Se")
    private val MONO = setOf("F", "Cl", "Br", "I")

    fun example(counts: Map<String, Int>): Molecule {
        val h = counts["H"] ?: 0
        for (el in counts.keys) {
            val e = Elements[el] ?: throw ChemException(ChemError(ChemError.Code.UNKNOWN_ELEMENT, el))
            if (e.metal) throw ChemException(ChemError(ChemError.Code.IONIC, el))
            if (e.valences == listOf(0) && (counts.size > 1 || counts[el] != 1)) throw ChemException(ChemError(ChemError.Code.VALENCE, el))
        }
        val heavy = counts.filterKeys { it != "H" }.values.sum()
        if (heavy > Chem.MAX_ATOMS) throw ChemException(ChemError(ChemError.Code.TOO_LARGE, Chem.MAX_ATOMS.toString()))
        if (heavy == 0) {
            if (h == 2) return Molecule().apply { val a = add(Atom("H")); val b = add(Atom("H")); bond(a, b) }
            throw ChemException(ChemError(ChemError.Code.HYDROGEN_COUNT))
        }
        val m = Molecule()
        val carbons = counts["C"] ?: 0
        val nX = MONO.sumOf { counts[it] ?: 0 }
        val nN = (counts["N"] ?: 0) + (counts["P"] ?: 0)
        val dbe = carbons - h / 2.0 - nX / 2.0 + nN / 2.0 + 1
        val cs = (0 until carbons).map { m.add(Atom("C")) }
        val fixed = HashSet<Bond>()
        if (carbons >= 6 && dbe >= 4) {
            for (j in 0 until 6) fixed += m.bond(cs[j], cs[(j + 1) % 6], if (j % 2 == 0) 2 else 1)
            for (j in 6 until carbons) m.bond(cs[j - 1], cs[j])
        } else for (j in 1 until carbons) m.bond(cs[j - 1], cs[j])
        fun cap(i: Int) = (Elements.valences(m.atoms[i].el, 0).maxOrNull() ?: 0) - m.bondSum(i)
        val others = ArrayList<Int>()
        val multi = MULTI_ORDER.flatMap { el -> List(counts[el] ?: 0) { el } }
        val mono = MONO.flatMap { el -> List(counts[el] ?: 0) { el } }
        if (carbons > 0) {
            val oxy = multi.filter { it == "O" || it == "S" || it == "Se" }.toMutableList()
            val nit = multi.filter { it !in setOf("O", "S", "Se") }
            for (el in nit) {
                val host = cs.firstOrNull { cap(it) >= 2 } ?: cs.firstOrNull { cap(it) >= 1 } ?: throw ChemException(ChemError(ChemError.Code.HYDROGEN_COUNT))
                val idx = m.add(Atom(el)); m.bond(host, idx); others += idx
            }
            // karboksil: iki O + doymamışlık → son karbona iki O
            val order = cs.reversed().filter { it !in cs.take(if (carbons >= 6 && dbe >= 4) 6 else 0) || carbons <= 6 }
            if (oxy.size >= 2 && dbe >= 1 && carbons >= 1) {
                val last = cs.last()
                if (cap(last) >= 3) repeat(2) { val idx = m.add(Atom(oxy.removeAt(0))); m.bond(last, idx); others += idx }
            }
            for (pass in 0..1) {
                for (c in order) {
                    if (oxy.isEmpty()) break
                    val cnt = m.neighbors(c).count { m.atoms[it].el in setOf("O", "S", "Se") }
                    if (cnt > pass || cap(c) < 2) continue
                    val idx = m.add(Atom(oxy.removeAt(0))); m.bond(c, idx); others += idx
                }
            }
            // kalan O'lar: zincire (eter/peroksit) — O'ya ya da son atoma bağla
            for (el in oxy) {
                val host = (others + cs).firstOrNull { cap(it) >= 2 } ?: throw ChemException(ChemError(ChemError.Code.HYDROGEN_COUNT))
                val idx = m.add(Atom(el)); m.bond(host, idx); others += idx
            }
            for (el in mono) {
                val host = cs.firstOrNull { cap(it) >= 1 && m.neighbors(it).none { n -> m.atoms[n].el in MONO } } ?: cs.firstOrNull { cap(it) >= 1 }
                    ?: others.firstOrNull { cap(it) >= 1 } ?: throw ChemException(ChemError(ChemError.Code.HYDROGEN_COUNT))
                m.bond(host, m.add(Atom(el)))
            }
        } else {
            // merkez atom: en yüksek değerlikli
            val sorted = multi.sortedByDescending { Elements[it]!!.valences.max() }
            if (sorted.isEmpty()) {
                // yalnız tek değerlikliler ve H: iki atomlu (HCl, Cl2, BrCl)
                if (mono.size + h != 2) throw ChemException(ChemError(ChemError.Code.HYDROGEN_COUNT))
                if (mono.size == 2) { val a = m.add(Atom(mono[0])); m.bond(a, m.add(Atom(mono[1]))) } else m.add(Atom(mono[0], h))
                return m
            }
            val center = m.add(Atom(sorted[0]))
            val rest = sorted.drop(1)
            for (el in rest) {
                val host = (listOf(center) + others).firstOrNull { cap(it) >= 2 } ?: (listOf(center) + others).firstOrNull { cap(it) >= 1 }
                    ?: throw ChemException(ChemError(ChemError.Code.HYDROGEN_COUNT))
                val idx = m.add(Atom(el)); m.bond(host, idx); others += idx
            }
            for (el in mono) {
                val host = (listOf(center) + others).firstOrNull { cap(it) >= 1 } ?: throw ChemException(ChemError(ChemError.Code.HYDROGEN_COUNT))
                m.bond(host, m.add(Atom(el)))
            }
        }
        if (m.components().size > 1) throw ChemException(ChemError(ChemError.Code.HYDROGEN_COUNT))
        // değerlik seçimi + doymamışlık + H dağıtımı (değişken değerlikli atomlar için seçenekler denenir)
        val variable = m.atoms.indices.filter { Elements.valences(m.atoms[it].el, 0).size > 1 }
        val choices = variable.map { Elements.valences(m.atoms[it].el, 0) }
        val total = choices.fold(1) { acc, l -> acc * l.size }
        for (pick in 0 until minOf(total, 256)) {
            val v = IntArray(m.atoms.size) { i -> Elements.valences(m.atoms[i].el, 0).first() }
            var r = pick
            for ((k, i) in variable.withIndex()) { val l = choices[k]; v[i] = l[r % l.size]; r /= l.size }
            val trial = m.copy()
            if (saturate(trial, v, h, fixed.map { fb -> trial.bonds[m.bonds.indexOf(fb)] }.toSet())) return trial
        }
        throw ChemException(ChemError(ChemError.Code.HYDROGEN_COUNT))
    }

    /** [v] değerlikleriyle [h] hidrojene uyacak biçimde çoklu bağ/halka ekler ve H'leri dağıtır. */
    private fun saturate(m: Molecule, v: IntArray, h: Int, fixed: Set<Bond>): Boolean {
        fun rem(i: Int) = v[i] - m.bondSum(i)
        if (m.atoms.indices.any { rem(it) < 0 }) return false
        val total = m.atoms.indices.sumOf { rem(it) }
        if (total < h || (total - h) % 2 != 0) return false
        var k = (total - h) / 2
        fun score(b: Bond): Int {
            val ea = m.atoms[b.a].el; val eb = m.atoms[b.b].el
            val pair = setOf(ea, eb)
            val term = m.degree(b.a) == 1 || m.degree(b.b) == 1
            return when {
                "C" in pair && (pair.contains("O") || pair.contains("S")) && term -> 0
                "C" in pair && pair.contains("N") && term -> 1
                pair == setOf("C") -> 2
                pair.contains("O") || pair.contains("S") -> 3
                else -> 4
            }
        }
        while (k > 0) {
            val cand = m.bonds.filter { it !in fixed && it.order < 3 && rem(it.a) >= 1 && rem(it.b) >= 1 }
            val best = cand.minWithOrNull(compareBy<Bond>({ score(it) }, { it.order }, { -maxOf(it.a, it.b) }))
            if (best != null) { best.order++; k--; continue }
            // halka kapat: aralarında en az 2 atom olan iki karbon (en büyük uzaklık 5: altılı halka tercih)
            val cs = m.atoms.indices.filter { m.atoms[it].el == "C" && rem(it) >= 1 }
            var done = false
            loop@ for (len in listOf(5, 4, 3, 2, 6, 7)) for (a in cs) for (b in cs) {
                if (a >= b || m.bondBetween(a, b) != null) continue
                if (pathLength(m, a, b) == len) { m.bond(a, b); k--; done = true; break@loop }
            }
            if (!done) return false
        }
        for (i in m.atoms.indices) m.atoms[i].h = rem(i)
        return m.valenceError() < 0
    }

    private fun pathLength(m: Molecule, a: Int, b: Int): Int {
        val d = IntArray(m.atoms.size) { -1 }
        val q = ArrayDeque<Int>().apply { add(a) }
        d[a] = 0
        while (q.isNotEmpty()) { val x = q.removeFirst(); for (y in m.neighbors(x)) if (d[y] < 0) { d[y] = d[x] + 1; q.add(y) } }
        return d[b]
    }
}
