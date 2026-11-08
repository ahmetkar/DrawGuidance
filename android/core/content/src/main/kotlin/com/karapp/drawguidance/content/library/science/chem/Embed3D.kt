package com.karapp.drawguidance.content.library.science.chem

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v45 basit 3B yerleşim (top-çubuk model): VSEPR. Her atomun sterik sayısı = komşular + yalın çiftler; 2 doğrusal,
 * 3 düzlem üçgen, 4 dörtyüzlü (yalın çift varsa açı daralır: NH₃ 107°, H₂O 104,5°), 5 üçgen çift piramit, 6
 * sekizyüzlü. Zincirler çapraz (anti) dizilir. Halkalı moleküllerde halka 2B yerleşimden düzlemsel alınır, yan
 * gruplar VSEPR ile halka düzleminin dışına (sp³) ya da içine (sp²) yerleşir. H'ler ayrı atomdur ([Molecule.explicitH]).
 * Sonuç ana eksenlere döndürülür (en geniş yayılım x, sonra y; düzlemsel moleküller önden görünür).
 */
object Embed3D {
    data class V(val x: Double, val y: Double, val z: Double) {
        operator fun plus(o: V) = V(x + o.x, y + o.y, z + o.z)
        operator fun minus(o: V) = V(x - o.x, y - o.y, z - o.z)
        operator fun times(k: Double) = V(x * k, y * k, z * k)
        infix fun dot(o: V) = x * o.x + y * o.y + z * o.z
        infix fun cross(o: V) = V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
        fun len() = sqrt(this dot this)
        fun norm(): V { val l = len(); return if (l < 1e-12) V(1.0, 0.0, 0.0) else this * (1 / l) }
    }

    /** Bağ boyu (Å): kovalent yarıçaplar toplamı, çift ×0,87, üçlü ×0,78. */
    fun bondLength(m: Molecule, a: Int, b: Int, order: Int): Double {
        val ra = Elements[m.atoms[a].el]?.radius ?: 0.75
        val rb = Elements[m.atoms[b].el]?.radius ?: 0.75
        val k = when (order) { 2 -> 0.87; 3 -> 0.78; else -> 1.0 }
        return (ra + rb) * k
    }

    private fun perp(v: V): V {
        val t = if (abs(v.x) < 0.9) V(1.0, 0.0, 0.0) else V(0.0, 1.0, 0.0)
        return (v cross t).norm()
    }

    private fun rotate(v: V, axis: V, ang: Double): V {
        val k = axis.norm()
        return v * cos(ang) + (k cross v) * sin(ang) + k * ((k dot v) * (1 - cos(ang)))
    }

    /** Sterik sayı [sn], yalın çift [lp] için ideal komşu açısı (derece). */
    private fun angleFor(sn: Int, lp: Int): Double = when (sn) {
        2 -> 180.0
        3 -> if (lp > 0) 117.0 else 120.0
        4 -> 109.47 - 2.5 * lp
        else -> 90.0
    }

    /**
     * Yerleşik komşu yönleri [e] (birim) verildiğinde [k] yeni yön. [ref] ilk yeni yönün dönmeye çalışacağı
     * referans (zincirde anti dizilim).
     */
    private fun newDirs(e: List<V>, sn: Int, lp: Int, k: Int, ref: V?): List<V> {
        if (k == 0) return emptyList()
        val ang = angleFor(sn, lp) * PI / 180
        if (e.isEmpty()) {
            val dirs: List<V> = when (sn) {
                1 -> listOf(V(1.0, 0.0, 0.0))
                2 -> listOf(V(1.0, 0.0, 0.0), V(-1.0, 0.0, 0.0))
                3 -> if (k == 2 && lp == 1) bent(ang) else List(3) { V(cos(PI / 2 + 2 * PI * it / 3), sin(PI / 2 + 2 * PI * it / 3), 0.0) }
                4 -> when (k) {
                    2 -> bent(ang)
                    3 -> pyramid(ang)
                    else -> tetra()
                }
                5 -> listOf(V(0.0, 1.0, 0.0), V(0.0, -1.0, 0.0)) + List(3) { V(cos(2 * PI * it / 3), 0.0, sin(2 * PI * it / 3)) }
                else -> listOf(V(1.0, 0.0, 0.0), V(-1.0, 0.0, 0.0), V(0.0, 1.0, 0.0), V(0.0, -1.0, 0.0), V(0.0, 0.0, 1.0), V(0.0, 0.0, -1.0))
            }
            return dirs.take(k)
        }
        if (e.size == 1) {
            val d0 = e[0]
            if (sn <= 2) return listOf(d0 * -1.0).take(k)
            val u = ref?.let { (it - d0 * (it dot d0)).takeIf { p -> p.len() > 1e-6 }?.norm() } ?: perp(d0)
            val w = d0 cross u
            val count = (sn - 1).coerceAtLeast(k)
            return List(count) { j ->
                val phi = PI + 2 * PI * j / count // ilk yön ref'in tersi: anti
                val radial = u * cos(phi) + w * sin(phi)
                (d0 * cos(ang) + radial * sin(ang)).norm()
            }.take(k)
        }
        val s = e.fold(V(0.0, 0.0, 0.0)) { a, b -> a + b }
        val out = (s * -1.0).let { if (it.len() < 1e-6) perp(e[0]) else it.norm() }
        if (k == 1) return listOf(out)
        val n = (e[0] cross e[1]).let { if (it.len() < 1e-6) perp(e[0]) else it.norm() }
        return if (sn >= 4) {
            val half = 54.7 * PI / 180
            List(k) { j -> val sg = if (j % 2 == 0) 1.0 else -1.0; (out * cos(half) + n * (sg * sin(half))).norm() }
        } else List(k) { j -> rotate(out, n, (j - (k - 1) / 2.0) * 0.6) }
    }

    private fun bent(ang: Double) = listOf(V(sin(ang / 2), -cos(ang / 2), 0.0), V(-sin(ang / 2), -cos(ang / 2), 0.0))

    private fun pyramid(ang: Double): List<V> {
        // üç yön, eksenle β açısı: cos θ = cos²β − sin²β/2
        val c = cos(ang)
        val cb2 = (2 * c + 1) / 3
        val b = acos(sqrt(cb2.coerceIn(0.0, 1.0)))
        return List(3) { j -> val p = 2 * PI * j / 3; V(sin(b) * cos(p), -cos(b), sin(b) * sin(p)) }
    }

    private fun tetra(): List<V> {
        val k = 1 / sqrt(3.0)
        return listOf(V(k, k, k), V(-k, -k, k), V(-k, k, -k), V(k, -k, -k))
    }

    /** [m] açık H'li olmalı. */
    fun embed(m: Molecule): List<V> {
        val n = m.atoms.size
        val pos = arrayOfNulls<V>(n)
        val rings = m.rings()
        if (rings.isNotEmpty()) {
            // halka atomları: 2B yerleşimden düzlemsel (bağ boyu ~1,4 Å)
            val ringAtoms = rings.flatten().toSet()
            val heavy = m.collapsedHIndices()
            val sub = heavy.first
            val p2 = Layout2D.layout(sub)
            for ((si, orig) in heavy.second.withIndex()) if (orig in ringAtoms) pos[orig] = V(p2[si].x * 1.4, p2[si].y * 1.4, 0.0)
        }
        val comps = m.components()
        var shift = 0.0
        for (comp in comps) {
            val seed = comp.firstOrNull { pos[it] != null } ?: comp.maxByOrNull { m.degree(it) }!!
            if (pos[seed] == null) pos[seed] = V(shift, 0.0, 0.0)
            val queue = ArrayDeque<Int>()
            queue.addAll(comp.filter { pos[it] != null })
            val done = HashSet<Int>()
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst()
                if (!done.add(i)) continue
                val p = pos[i]!!
                val nbs = m.neighbors(i)
                val placed = nbs.filter { pos[it] != null }
                val todo = nbs.filter { pos[it] == null }.sortedBy { if (m.atoms[it].el == "H") 1 else 0 }
                if (todo.isEmpty()) continue
                val lp = m.lonePairs(i)
                val lin = m.bondsOf(i).any { it.order == 3 } || m.bondsOf(i).count { it.order == 2 } >= 2
                val sn = if (lin && nbs.size == 2) 2 else nbs.size + lp
                val e = placed.map { (pos[it]!! - p).norm() }
                // anti dizilim referansı: ebeveynin başka komşusu
                val ref = placed.firstOrNull()?.let { par -> m.neighbors(par).firstOrNull { it != i && pos[it] != null }?.let { g -> (pos[g]!! - pos[par]!!) } }
                val dirs = newDirs(e, sn, lp, todo.size, ref)
                for ((k, c) in todo.withIndex()) {
                    val d = dirs.getOrElse(k) { perp(dirs.lastOrNull() ?: V(1.0, 0.0, 0.0)) }
                    val order = m.bondBetween(i, c)?.order ?: 1
                    pos[c] = p + d * bondLength(m, i, c, order)
                    queue.add(c)
                }
            }
            for (i in comp) if (pos[i] == null) pos[i] = V(shift, 0.0, 0.0)
            val maxX = comp.maxOf { pos[it]!!.x }
            shift = maxX + 3.0
        }
        return orient(m, pos.map { it!! })
    }

    /** Ana eksenlere döndürme (Jacobi özdeğer); kök (en çok bağlı atom) yukarıda kalır. */
    private fun orient(m: Molecule, p: List<V>): List<V> {
        if (p.size < 2) return p
        val c = p.fold(V(0.0, 0.0, 0.0)) { a, b -> a + b } * (1.0 / p.size)
        val q = p.map { it - c }
        val a = Array(3) { DoubleArray(3) }
        for (v in q) {
            val r = doubleArrayOf(v.x, v.y, v.z)
            for (i in 0..2) for (j in 0..2) a[i][j] += r[i] * r[j]
        }
        val vec = Array(3) { i -> DoubleArray(3) { j -> if (i == j) 1.0 else 0.0 } }
        repeat(50) {
            for (pI in 0..1) for (qI in pI + 1..2) {
                if (abs(a[pI][qI]) < 1e-12) continue
                val th = 0.5 * kotlin.math.atan2(2 * a[pI][qI], a[qI][qI] - a[pI][pI])
                val cs = cos(th); val sn = sin(th)
                for (k in 0..2) {
                    val akp = a[k][pI]; val akq = a[k][qI]
                    a[k][pI] = cs * akp - sn * akq; a[k][qI] = sn * akp + cs * akq
                }
                for (k in 0..2) {
                    val apk = a[pI][k]; val aqk = a[qI][k]
                    a[pI][k] = cs * apk - sn * aqk; a[qI][k] = sn * apk + cs * aqk
                }
                for (k in 0..2) {
                    val vkp = vec[k][pI]; val vkq = vec[k][qI]
                    vec[k][pI] = cs * vkp - sn * vkq; vec[k][qI] = sn * vkp + cs * vkq
                }
            }
        }
        val order = (0..2).sortedByDescending { a[it][it] }
        val ax = order.map { j -> V(vec[0][j], vec[1][j], vec[2][j]).norm() }
        var e1 = ax[0]; var e2 = ax[1]
        var e3 = e1 cross e2
        var out = q.map { V(it dot e1, it dot e2, it dot e3) }
        // kök atom yukarıda (su: O üstte)
        val root = p.indices.maxByOrNull { m.degree(it) * 10 + (if (m.atoms[it].el == "H") 0 else 1) } ?: 0
        if (out[root].y < 0) { e2 = e2 * -1.0; e3 = e1 cross e2; out = q.map { V(it dot e1, it dot e2, it dot e3) } }
        return out
    }

    /** Görüntüleme: [yaw]/[pitch] (derece) döndürülmüş koordinatlar. Düzlemsel moleküller hafif, diğerleri belirgin döner. */
    fun view(p: List<V>, yaw: Double, pitch: Double): List<V> {
        val cy = cos(yaw * PI / 180); val sy = sin(yaw * PI / 180)
        val cp = cos(pitch * PI / 180); val sp = sin(pitch * PI / 180)
        return p.map { v ->
            val x1 = v.x * cy + v.z * sy; val z1 = -v.x * sy + v.z * cy
            val y2 = v.y * cp - z1 * sp; val z2 = v.y * sp + z1 * cp
            V(x1, y2, z2)
        }
    }

    /** Düzlemsel mi (üçüncü eksende yayılım çok küçük)? */
    fun planar(p: List<V>): Boolean = p.all { abs(it.z) < 0.15 }
}

/** H atomlarını katlayan alt molekül ve ağır atomların özgün indeksleri. */
internal fun Molecule.collapsedHIndices(): Pair<Molecule, List<Int>> {
    val keep = atoms.indices.filter { atoms[it].el != "H" || neighbors(it).all { n -> atoms[n].el == "H" } }
    val map = HashMap<Int, Int>()
    val sub = Molecule()
    for (i in keep) map[i] = sub.add(atoms[i].copy())
    for (b in bonds) {
        val a = map[b.a]; val c = map[b.b]
        if (a != null && c != null) sub.bond(a, c, b.order)
        else if (a != null) sub.atoms[a].h++ else if (c != null) sub.atoms[c].h++
    }
    return sub to keep
}
