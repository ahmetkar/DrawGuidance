package com.karapp.drawguidance.content.library.science.chem

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v45 2B yerleşim (bağ boyu 1, y yukarı). Halkalar düzgün çokgen (kaynaşık halkalar ortak kenarın öbür yanına), zincirler
 * 120° zikzak; açık H'li çizimde dört bağlı karbon artı biçiminde (zincir düz, H'ler üst/alt: ders kitabı açık formülü);
 * sp atomları (üçlü bağ, O=C=O) doğrusal. Bileşenler (iyonlar) yan yana. Çakışma kalırsa itme ile gevşetilir.
 */
object Layout2D {
    class Pt(var x: Double, var y: Double)

    fun layout(m: Molecule, crossH: Boolean = false): List<Pt> {
        val n = m.atoms.size
        val pos = arrayOfNulls<Pt>(n)
        val comps = m.components()
        var offset = 0.0
        val rings = m.rings()
        for (comp in comps.sortedBy { c -> c.minOrNull() ?: 0 }) {
            placeComponent(m, comp, pos, rings, crossH)
            // bileşeni sola hizala, öncekinin sağına koy
            val minX = comp.minOf { pos[it]!!.x }
            val maxX = comp.maxOf { pos[it]!!.x }
            val cy = comp.map { pos[it]!!.y }.let { (it.min() + it.max()) / 2 }
            for (i in comp) { pos[i]!!.x += offset - minX; pos[i]!!.y -= cy }
            offset += (maxX - minX) + 1.6
        }
        val out = pos.map { it!! }
        relax(m, out)
        return out
    }

    private fun dir(a: Double) = Pt(cos(a), sin(a))

    /** Atom sp mi (iki komşu, en az bir üçlü ya da iki çift bağ)? */
    private fun linear(m: Molecule, i: Int): Boolean {
        val bs = m.bondsOf(i)
        val nb = bs.size + m.atoms[i].h
        if (nb != 2) return false
        return bs.any { it.order == 3 } || bs.count { it.order == 2 } == 2
    }

    private fun placeComponent(m: Molecule, comp: List<Int>, pos: Array<Pt?>, allRings: List<List<Int>>, crossH: Boolean) {
        val inComp = comp.toSet()
        val rings = allRings.filter { r -> r.all { it in inComp } }
        val ringOf = HashMap<Int, MutableList<List<Int>>>()
        for (r in rings) for (a in r) ringOf.getOrPut(a) { ArrayList() } += r
        val heavy = comp.filter { m.atoms[it].el != "H" }.ifEmpty { comp }
        // kök: en uzun ağır-atom yolunun bir ucu (halka varsa halka atomu)
        val root = when {
            rings.isNotEmpty() && heavy.size <= rings.flatten().toSet().size + 1 -> rings[0][0]
            heavy.size == 1 -> heavy[0]
            else -> farthest(m, farthest(m, heavy[0], heavy.toSet()), heavy.toSet())
        }
        val mainPath = if (heavy.size > 1) pathTo(m, root, farthest(m, root, heavy.toSet()), heavy.toSet()) else listOf(root)
        val mainSet = mainPath.toSet()
        val turn = HashMap<Int, Int>()
        val placedRings = HashSet<List<Int>>()
        val ringCenter = HashMap<List<Int>, Pt>()

        fun placeRing(r: List<Int>, entry: Int, outward: Double?) {
            val k = r.size
            val rad = 1.0 / (2 * sin(PI / k))
            val placedIdx = r.indices.filter { pos[r[it]] != null }
            val ordered: List<Int>
            val center: Pt
            if (placedIdx.size >= 2) {
                // kaynaşık: iki ardışık yerleşik atomdan geçen kenarın öbür yanı
                var i0 = -1
                for (j in r.indices) if (pos[r[j]] != null && pos[r[(j + 1) % k]] != null) { i0 = j; break }
                if (i0 < 0) i0 = placedIdx[0]
                val a = pos[r[i0]]!!; val b = pos[r[(i0 + 1) % k]]!!
                val mx = (a.x + b.x) / 2; val my = (a.y + b.y) / 2
                val nx = -(b.y - a.y); val ny = b.x - a.x
                val nl = hypot(nx, ny)
                val apo = rad * cos(PI / k)
                val old = ringOf[r[i0]]?.firstOrNull { it != r && it in placedRings }?.let { ringCenter[it] }
                var sgn = 1.0
                if (old != null && ((old.x - mx) * nx + (old.y - my) * ny) > 0) sgn = -1.0
                center = Pt(mx + sgn * nx / nl * apo, my + sgn * ny / nl * apo)
                ordered = List(k) { r[(i0 + it) % k] }
            } else {
                val e = pos[entry]!!
                val ang = outward ?: (PI / 2)
                center = Pt(e.x + rad * cos(ang), e.y + rad * sin(ang))
                val ei = r.indexOf(entry)
                ordered = List(k) { r[(ei + it) % k] }
            }
            val a0 = atan2(pos[ordered[0]]!!.y - center.y, pos[ordered[0]]!!.x - center.x)
            // dönüş yönü: ikinci atom yerleşikse ona göre
            var sense = 1.0
            if (pos[ordered[1]] != null) {
                val a1 = atan2(pos[ordered[1]]!!.y - center.y, pos[ordered[1]]!!.x - center.x)
                var d = a1 - a0
                while (d > PI) d -= 2 * PI
                while (d < -PI) d += 2 * PI
                sense = if (d >= 0) 1.0 else -1.0
            }
            for (j in 1 until k) {
                if (pos[ordered[j]] != null) continue
                val a = a0 + sense * 2 * PI * j / k
                pos[ordered[j]] = Pt(center.x + rad * cos(a), center.y + rad * sin(a))
            }
            placedRings += r
            ringCenter[r] = center
        }

        fun placeRingsAt(i: Int, outward: Double?) {
            val rs = ringOf[i] ?: return
            for (r in rs) if (r !in placedRings) {
                placeRing(r, i, outward)
                // kaynaşık halkalar
                var changed = true
                while (changed) {
                    changed = false
                    for (r2 in rings) if (r2 !in placedRings && r2.count { pos[it] != null } >= 2) { placeRing(r2, r2.first { pos[it] != null }, null); changed = true }
                }
            }
        }

        pos[root] = Pt(0.0, 0.0)
        if (ringOf[root] != null) {
            val r = ringOf[root]!![0]
            val k = r.size
            val rad = 1.0 / (2 * sin(PI / k))
            // kök halka: merkez orijinin altında, kök tepe köşede
            pos[root] = Pt(0.0, rad)
            placeRing(r, root, -PI / 2)
            placeRingsAt(root, null)
        }
        val queue = ArrayDeque<Int>().apply { add(root) }
        val done = HashSet<Int>()
        // halka atomlarını da kuyruğa al
        for (r in placedRings) for (a in r) if (a != root) queue.add(a)
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            if (!done.add(i)) continue
            val p = pos[i]!!
            val nbs = m.neighbors(i).filter { it in inComp }
            val placed = nbs.filter { pos[it] != null }
            val todo = nbs.filter { pos[it] == null }
                .sortedWith(compareBy<Int>({ if (it in mainSet) 0 else 1 }, { if (m.atoms[it].el == "H") 1 else 0 }, { -subtreeSize(m, it, i) }))
            if (todo.isEmpty()) { continue }
            val dirs = placed.map { atan2(pos[it]!!.y - p.y, pos[it]!!.x - p.x) }
            val angles: List<Double> = when {
                dirs.isEmpty() -> rootAngles(m, i, todo.size)
                dirs.size == 1 -> {
                    val back = dirs[0]
                    val t = turn[i] ?: 1
                    when {
                        linear(m, i) -> listOf(back + PI)
                        todo.size == 1 -> listOf(back + PI - t * PI / 3)
                        todo.size == 2 -> if (crossH && todo.count { m.atoms[it].el == "H" } == 2 && m.bondsOf(i).all { it.order == 1 }) listOf(back + PI / 2, back - PI / 2)
                            else listOf(back + PI - t * PI / 3, back + PI + t * PI / 3)
                        todo.size == 3 && crossH -> listOf(back + PI, back + PI / 2, back - PI / 2)
                        else -> List(todo.size) { back + 2 * PI * (it + 1) / (todo.size + 1) }
                    }
                }
                else -> {
                    // dışa doğru: yerleşik yönlerin ortalamasının tersi
                    val sx = dirs.sumOf { cos(it) }; val sy = dirs.sumOf { sin(it) }
                    val out = if (hypot(sx, sy) < 1e-6) dirs[0] + PI / 2 else atan2(-sy, -sx)
                    when (todo.size) {
                        1 -> listOf(out)
                        2 -> listOf(out + PI / 5, out - PI / 5)
                        else -> List(todo.size) { out + (it - (todo.size - 1) / 2.0) * PI / 4 }
                    }
                }
            }
            for ((k, c) in todo.withIndex()) {
                if (pos[c] != null) continue
                val a = angles[k]
                pos[c] = Pt(p.x + cos(a), p.y + sin(a))
                turn[c] = -(turn[i] ?: 1)
                if (ringOf[c] != null) placeRingsAt(c, a)
                queue.add(c)
                for (r in placedRings) for (x in r) if (x !in done) queue.add(x)
            }
        }
        // ulaşılamayan (olmamalı) atomlar
        for (i in comp) if (pos[i] == null) pos[i] = Pt(0.0, 0.0)
    }

    /** Kökün komşu yönleri: ana zincir (ilk) sağa; yalnız H'li merkez atomlar (su, amonyak) bükük/üçgen. */
    private fun rootAngles(m: Molecule, i: Int, n: Int): List<Double> {
        val allH = m.neighbors(i).all { m.atoms[it].el == "H" }
        return when (n) {
            1 -> listOf(0.0)
            2 -> when {
                linear(m, i) -> listOf(0.0, PI)
                allH -> listOf(-PI / 6, -5 * PI / 6) // bükük (su: H'ler aşağıda)
                else -> listOf(0.0, 2 * PI / 3)
            }
            3 -> if (allH && m.lonePairs(i) > 0) listOf(-PI / 2, PI / 6, 5 * PI / 6) else listOf(0.0, 2 * PI / 3, 4 * PI / 3)
            4 -> listOf(0.0, PI / 2, PI, 3 * PI / 2)
            else -> List(n) { 2 * PI * it / n }
        }
    }

    private fun subtreeSize(m: Molecule, start: Int, from: Int): Int {
        val seen = hashSetOf(from, start)
        val st = ArrayDeque<Int>().apply { add(start) }
        while (st.isNotEmpty()) { val x = st.removeLast(); for (y in m.neighbors(x)) if (seen.add(y)) st.add(y) }
        return seen.size - 1
    }

    private fun farthest(m: Molecule, s: Int, allowed: Set<Int>): Int {
        val d = HashMap<Int, Int>().apply { put(s, 0) }
        val q = ArrayDeque<Int>().apply { add(s) }
        var best = s
        while (q.isNotEmpty()) {
            val x = q.removeFirst()
            if (d[x]!! > d[best]!!) best = x
            for (y in m.neighbors(x)) if (y in allowed && y !in d) { d[y] = d[x]!! + 1; q.add(y) }
        }
        return best
    }

    private fun pathTo(m: Molecule, s: Int, t: Int, allowed: Set<Int>): List<Int> {
        val prev = HashMap<Int, Int>().apply { put(s, s) }
        val q = ArrayDeque<Int>().apply { add(s) }
        while (q.isNotEmpty()) { val x = q.removeFirst(); if (x == t) break; for (y in m.neighbors(x)) if (y in allowed && y !in prev) { prev[y] = x; q.add(y) } }
        val out = ArrayList<Int>()
        var c = t
        while (c != s) { out += c; c = prev[c] ?: return listOf(s) }
        out += s
        return out.reversed()
    }

    /** Bağlı olmayan atom çiftlerinin en küçük uzaklığı (testler ve gevşetme için). */
    fun minNonBonded(m: Molecule, p: List<Pt>): Double {
        var best = Double.MAX_VALUE
        for (i in p.indices) for (j in i + 1 until p.size) {
            if (m.bondBetween(i, j) != null) continue
            best = minOf(best, hypot(p[i].x - p[j].x, p[i].y - p[j].y))
        }
        return best
    }

    /**
     * Çakışma (bağsız iki atom < 0,55 bağ boyu) varsa gevşetir: bağsız yakın çiftler itilir, ardından bağ boyları
     * yeniden 1'e çekilir (kısıt izdüşümü). Bağ boyları korunur, yalnız açılar değişir.
     */
    private fun relax(m: Molecule, p: List<Pt>) {
        if (p.size < 3 || minNonBonded(m, p) >= 0.55) return
        val n = p.size
        val bonded = Array(n) { BooleanArray(n) }
        for (b in m.bonds) { bonded[b.a][b.b] = true; bonded[b.b][b.a] = true }
        repeat(300) {
            for (i in 0 until n) for (j in i + 1 until n) {
                if (bonded[i][j]) continue
                val dx = p[j].x - p[i].x; val dy = p[j].y - p[i].y
                val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3)
                if (d >= 1.2) continue
                val f = (1.2 - d) * 0.15
                p[i].x -= f * dx / d; p[i].y -= f * dy / d
                p[j].x += f * dx / d; p[j].y += f * dy / d
            }
            repeat(4) {
                for (b in m.bonds) {
                    val a = p[b.a]; val c = p[b.b]
                    val dx = c.x - a.x; val dy = c.y - a.y
                    val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-6)
                    val k = (d - 1.0) / d * 0.5
                    a.x += dx * k; a.y += dy * k
                    c.x -= dx * k; c.y -= dy * k
                }
            }
        }
    }

    /** Bağ boylarının en küçük ve en büyük değeri (testler için). */
    fun bondRange(m: Molecule, p: List<Pt>): Pair<Double, Double> {
        if (m.bonds.isEmpty()) return 1.0 to 1.0
        val l = m.bonds.map { hypot(p[it.a].x - p[it.b].x, p[it.a].y - p[it.b].y) }
        return l.min() to l.max()
    }

}
