package com.karapp.drawguidance.content.model3d

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * v25 katı cisim işlemleri (CSG): birleştirme ([union]), çıkarma ([subtract]), kesişim ([intersect]); delik açma ve
 * parçalama bunların üstüne kuruludur. Yöntem ikili alan bölme ağacı (BSP; Evan Wallace'ın csg.js algoritması): her
 * cismin çokgenleri diğerinin ağacıyla kırpılır. Ağaç işlemleri özyinelemesiz (yığın taşmaz). Sonuçta noktalar
 * kaynaştırılır ve T-kavşakları giderilir (kenar ortasında kalan noktalar komşu yüze eklenir: çizimde çatlak olmaz).
 * Girdi ve çıktı dünya uzayında; her çokgen malzeme sırasını ([CsgPoly.tag]) taşır.
 */
object Csg {

    private const val EPS = 1e-5

    /** Çokgen (dışbükey, saat yönünün tersine), malzeme etiketiyle. */
    class CsgPoly internal constructor(internal val v: List<DoubleArray>, val tag: Int, internal val plane: Plane) {
        internal fun flipped() = CsgPoly(v.asReversed().toList(), tag, plane.flipped())
    }

    internal class Plane(val nx: Double, val ny: Double, val nz: Double, val w: Double) {
        fun flipped() = Plane(-nx, -ny, -nz, -w)
        fun dist(p: DoubleArray) = nx * p[0] + ny * p[1] + nz * p[2] - w

        companion object {
            fun of(a: DoubleArray, b: DoubleArray, c: DoubleArray): Plane? {
                val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
                val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
                var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
                val l = sqrt(nx * nx + ny * ny + nz * nz)
                if (l < 1e-12) return null
                nx /= l; ny /= l; nz /= l
                return Plane(nx, ny, nz, nx * a[0] + ny * a[1] + nz * a[2])
            }
        }
    }

    private const val COPLANAR = 0
    private const val FRONT = 1
    private const val BACK = 2
    private const val SPANNING = 3

    /** [poly]'yi düzleme göre ayırır (csg.js splitPolygon). */
    private fun split(
        plane: Plane, poly: CsgPoly,
        coFront: MutableList<CsgPoly>, coBack: MutableList<CsgPoly>, front: MutableList<CsgPoly>, back: MutableList<CsgPoly>,
    ) {
        var type = 0
        val n = poly.v.size
        val types = IntArray(n)
        for (i in 0 until n) {
            val t = plane.dist(poly.v[i])
            val ty = if (t < -EPS) BACK else if (t > EPS) FRONT else COPLANAR
            type = type or ty
            types[i] = ty
        }
        when (type) {
            COPLANAR -> {
                val same = plane.nx * poly.plane.nx + plane.ny * poly.plane.ny + plane.nz * poly.plane.nz > 0
                (if (same) coFront else coBack) += poly
            }
            FRONT -> front += poly
            BACK -> back += poly
            else -> {
                val f = ArrayList<DoubleArray>(n + 2); val b = ArrayList<DoubleArray>(n + 2)
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    val ti = types[i]; val tj = types[j]
                    val vi = poly.v[i]; val vj = poly.v[j]
                    if (ti != BACK) f += vi
                    if (ti != FRONT) b += vi
                    if ((ti or tj) == SPANNING) {
                        val dx = vj[0] - vi[0]; val dy = vj[1] - vi[1]; val dz = vj[2] - vi[2]
                        val t = (plane.w - (plane.nx * vi[0] + plane.ny * vi[1] + plane.nz * vi[2])) / (plane.nx * dx + plane.ny * dy + plane.nz * dz)
                        val p = doubleArrayOf(vi[0] + dx * t, vi[1] + dy * t, vi[2] + dz * t)
                        f += p; b += p
                    }
                }
                if (f.size >= 3) front += CsgPoly(f, poly.tag, poly.plane)
                if (b.size >= 3) back += CsgPoly(b, poly.tag, poly.plane)
            }
        }
    }

    /** BSP düğümü (özyinelemesiz işlemler). */
    private class Node {
        var plane: Plane? = null
        var front: Node? = null
        var back: Node? = null
        var polys: MutableList<CsgPoly> = ArrayList()

        fun nodes(): List<Node> {
            val out = ArrayList<Node>()
            val stack = ArrayDeque<Node>()
            stack += this
            while (stack.isNotEmpty()) {
                val n = stack.removeLast()
                out += n
                n.front?.let { stack += it }
                n.back?.let { stack += it }
            }
            return out
        }

        fun invert() {
            for (n in nodes()) {
                n.polys = n.polys.mapTo(ArrayList(n.polys.size)) { it.flipped() }
                n.plane = n.plane?.flipped()
                val t = n.front; n.front = n.back; n.back = t
            }
        }

        fun clip(input: List<CsgPoly>): MutableList<CsgPoly> {
            val out = ArrayList<CsgPoly>()
            if (plane == null) { out.addAll(input); return out }
            val stack = ArrayDeque<Pair<Node, List<CsgPoly>>>()
            stack += this to input
            while (stack.isNotEmpty()) {
                val (node, list) = stack.removeLast()
                val pl = node.plane
                if (pl == null) { out.addAll(list); continue }
                val f = ArrayList<CsgPoly>(); val b = ArrayList<CsgPoly>()
                for (p in list) split(pl, p, f, b, f, b)
                val fn = node.front
                if (fn != null) { if (f.isNotEmpty()) stack += fn to f } else out.addAll(f)
                val bn = node.back
                if (bn != null && b.isNotEmpty()) stack += bn to b
            }
            return out
        }

        fun clipTo(bsp: Node) { for (n in nodes()) n.polys = bsp.clip(n.polys) }

        fun all(): MutableList<CsgPoly> { val out = ArrayList<CsgPoly>(); for (n in nodes()) out.addAll(n.polys); return out }

        fun build(input: List<CsgPoly>) {
            if (input.isEmpty()) return
            val stack = ArrayDeque<Pair<Node, List<CsgPoly>>>()
            stack += this to input
            while (stack.isNotEmpty()) {
                val (node, list) = stack.removeLast()
                if (list.isEmpty()) continue
                val pl = node.plane ?: list[0].plane.also { node.plane = it }
                val f = ArrayList<CsgPoly>(); val b = ArrayList<CsgPoly>()
                for (p in list) split(pl, p, node.polys, node.polys, f, b)
                if (f.isNotEmpty()) stack += (node.front ?: Node().also { node.front = it }) to f
                if (b.isNotEmpty()) stack += (node.back ?: Node().also { node.back = it }) to b
            }
        }

        companion object {
            fun of(list: List<CsgPoly>) = Node().also { it.build(list) }
        }
    }

    // ------------------------------------------------------------------------------------------ işlemler

    fun union(a: List<CsgPoly>, b: List<CsgPoly>): List<CsgPoly> {
        val na = Node.of(a); val nb = Node.of(b)
        na.clipTo(nb); nb.clipTo(na); nb.invert(); nb.clipTo(na); nb.invert()
        na.build(nb.all())
        return na.all()
    }

    fun subtract(a: List<CsgPoly>, b: List<CsgPoly>): List<CsgPoly> {
        val na = Node.of(a); val nb = Node.of(b)
        na.invert(); na.clipTo(nb); nb.clipTo(na); nb.invert(); nb.clipTo(na); nb.invert()
        na.build(nb.all()); na.invert()
        return na.all()
    }

    /**
     * v26 yerel çıkarma: [b] (küçük kesici) [a]'dan çıkarılır; yalnız [b]'nin kutusuna değen yüzler kırpılır, uzaktaki
     * yüzler olduğu gibi kalır (kıvrımlı büyük cisimde gereksiz bölünme olmaz). [b]'nin [a] içinde kalan yüzleri ters
     * çevrilip eklenir (oyuğun duvarları).
     */
    fun subtractLocal(a: List<CsgPoly>, b: List<CsgPoly>): List<CsgPoly> {
        if (a.isEmpty() || b.isEmpty()) return a
        var x0 = Double.MAX_VALUE; var y0 = Double.MAX_VALUE; var z0 = Double.MAX_VALUE
        var x1 = -Double.MAX_VALUE; var y1 = -Double.MAX_VALUE; var z1 = -Double.MAX_VALUE
        for (p in b) for (v in p.v) {
            x0 = min(x0, v[0]); y0 = min(y0, v[1]); z0 = min(z0, v[2]); x1 = max(x1, v[0]); y1 = max(y1, v[1]); z1 = max(z1, v[2])
        }
        val pad = EPS * 10
        val near = ArrayList<CsgPoly>(); val far = ArrayList<CsgPoly>()
        for (p in a) {
            var px0 = Double.MAX_VALUE; var py0 = Double.MAX_VALUE; var pz0 = Double.MAX_VALUE
            var px1 = -Double.MAX_VALUE; var py1 = -Double.MAX_VALUE; var pz1 = -Double.MAX_VALUE
            for (v in p.v) {
                px0 = min(px0, v[0]); py0 = min(py0, v[1]); pz0 = min(pz0, v[2]); px1 = max(px1, v[0]); py1 = max(py1, v[1]); pz1 = max(pz1, v[2])
            }
            val hit = px1 >= x0 - pad && px0 <= x1 + pad && py1 >= y0 - pad && py0 <= y1 + pad && pz1 >= z0 - pad && pz0 <= z1 + pad
            (if (hit) near else far) += p
        }
        if (near.isEmpty()) return a
        // A'nın yakın yüzlerinden B'nin içinde kalanlar atılır
        val nb = Node.of(b)
        val aOut = nb.clip(near)
        // B'nin A içinde kalan kısmı (A'nın tüm yüzleriyle sınanır), ters yüzle
        val na = Node.of(a)
        na.invert()
        val bIn = na.clip(b).map { it.flipped() }
        return far + aOut + bIn
    }

    fun intersect(a: List<CsgPoly>, b: List<CsgPoly>): List<CsgPoly> {
        val na = Node.of(a); val nb = Node.of(b)
        na.invert(); nb.clipTo(na); nb.invert(); na.clipTo(nb); nb.clipTo(na)
        na.build(nb.all()); na.invert()
        return na.all()
    }

    // ------------------------------------------------------------------------------------------ dönüşümler

    /** Ağın (dünya uzayındaki) üçgenleri; [tagOf] yüzün malzeme etiketi. */
    fun polygons(mesh: Mesh, transform: Transform3?, tagOf: (Int) -> Int): List<CsgPoly> {
        val world = FloatArray(mesh.pos.size)
        if (transform != null) for (i in 0 until mesh.vertexCount) transform.apply(mesh.pos[3 * i], mesh.pos[3 * i + 1], mesh.pos[3 * i + 2], world, 3 * i)
        else mesh.pos.copyInto(world)
        val mirrored = transform?.let { det(it.m) < 0f } ?: false
        val out = ArrayList<CsgPoly>(mesh.triangleCount)
        for (f in 0 until mesh.faceCount) {
            val face = mesh.faces[f]; val tris = mesh.triangles[f]
            val tag = tagOf(f)
            var k = 0
            while (k + 2 < tris.size) {
                val ids = if (mirrored) intArrayOf(face[tris[k]], face[tris[k + 2]], face[tris[k + 1]]) else intArrayOf(face[tris[k]], face[tris[k + 1]], face[tris[k + 2]])
                val pts = ids.map { doubleArrayOf(world[3 * it].toDouble(), world[3 * it + 1].toDouble(), world[3 * it + 2].toDouble()) }
                Plane.of(pts[0], pts[1], pts[2])?.let { out += CsgPoly(pts, tag, it) }
                k += 3
            }
        }
        return out
    }

    private fun det(m: FloatArray) = m[0] * (m[5] * m[10] - m[6] * m[9]) - m[1] * (m[4] * m[10] - m[6] * m[8]) + m[2] * (m[4] * m[9] - m[5] * m[8])

    /** Çokgenler ağa: noktalar kaynaştırılır, T-kavşakları giderilir. Yüzlerin etiketleri [tags]'a yazılır. */
    fun toMesh(polys: List<CsgPoly>, tags: MutableList<Int>): Mesh {
        if (polys.isEmpty()) return Mesh(FloatArray(0), emptyArray())
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        var minZ = Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
        for (p in polys) for (v in p.v) {
            minX = min(minX, v[0]); maxX = max(maxX, v[0]); minY = min(minY, v[1]); maxY = max(maxY, v[1]); minZ = min(minZ, v[2]); maxZ = max(maxZ, v[2])
        }
        val size = max(maxX - minX, max(maxY - minY, maxZ - minZ)).coerceAtLeast(1e-6)
        val weld = size * 1e-6
        val grid = HashMap<Long, MutableList<Int>>()
        val pos = ArrayList<Double>()
        fun cellKey(x: Long, y: Long, z: Long) = (x * 73856093L) xor (y * 19349663L) xor (z * 83492791L)
        fun indexOf(v: DoubleArray): Int {
            val cx = floor(v[0] / (weld * 4)).toLong(); val cy = floor(v[1] / (weld * 4)).toLong(); val cz = floor(v[2] / (weld * 4)).toLong()
            for (dx in -1L..1L) for (dy in -1L..1L) for (dz in -1L..1L) {
                val list = grid[cellKey(cx + dx, cy + dy, cz + dz)] ?: continue
                for (i in list) if (abs(pos[3 * i] - v[0]) <= weld && abs(pos[3 * i + 1] - v[1]) <= weld && abs(pos[3 * i + 2] - v[2]) <= weld) return i
            }
            val i = pos.size / 3
            pos += v[0]; pos += v[1]; pos += v[2]
            grid.getOrPut(cellKey(cx, cy, cz)) { ArrayList(2) } += i
            return i
        }
        val faces = ArrayList<IntArray>(polys.size)
        for (p in polys) {
            val ids = ArrayList<Int>(p.v.size)
            for (v in p.v) { val i = indexOf(v); if (ids.isEmpty() || ids.last() != i) ids += i }
            while (ids.size > 1 && ids.first() == ids.last()) ids.removeAt(ids.size - 1)
            if (ids.toSet().size >= 3) { faces += ids.toIntArray(); tags += p.tag }
        }
        val posF = FloatArray(pos.size) { pos[it].toFloat() }
        return stitch(fixTJunctions(Mesh(posF, faces.toTypedArray()), size * 2e-5), size * 2e-5)
    }

    /**
     * v26 açık kenarlarda kalan, birbirine çok yakın noktaları birleştirir (ince cisimlerde bölmenin bıraktığı kıl
     * payı çatlaklar); bozulan yüzler atılır.
     */
    fun stitch(mesh: Mesh, eps: Double): Mesh {
        val e = mesh.edges
        val open = LinkedHashSet<Int>()
        var i = 0
        while (i < e.size) { if (e[i + 3] < 0) { open += e[i]; open += e[i + 1] }; i += 4 }
        if (open.isEmpty() || open.size > 4000) return mesh
        val list = open.toIntArray()
        val remap = IntArray(mesh.vertexCount) { it }
        val p = mesh.pos
        var merged = false
        for (a in list.indices) {
            if (remap[list[a]] != list[a]) continue
            for (b in a + 1 until list.size) {
                val u = list[a]; val v = list[b]
                if (remap[v] != v) continue
                val dx = (p[3 * u] - p[3 * v]).toDouble(); val dy = (p[3 * u + 1] - p[3 * v + 1]).toDouble(); val dz = (p[3 * u + 2] - p[3 * v + 2]).toDouble()
                if (dx * dx + dy * dy + dz * dz <= eps * eps) { remap[v] = u; merged = true }
            }
        }
        if (!merged) return mesh
        val faces = ArrayList<IntArray>(mesh.faceCount)
        for (f in mesh.faces) {
            val ids = ArrayList<Int>(f.size)
            for (x in f) { val r = remap[x]; if (ids.isEmpty() || ids.last() != r) ids += r }
            while (ids.size > 1 && ids.first() == ids.last()) ids.removeAt(ids.size - 1)
            if (ids.toSet().size >= 3) faces += ids.toIntArray()
        }
        return Mesh(p, faces.toTypedArray()).compact()
    }

    /** Bir yüzün kenarının ortasında duran noktaları o kenara ekler (çatlaksız ve kenarları paylaşılan ağ). */
    fun fixTJunctions(mesh: Mesh, eps: Double): Mesh {
        val n = mesh.vertexCount
        if (n == 0) return mesh
        val p = mesh.pos
        var len = 0.0; var cnt = 0
        for (face in mesh.faces) for (i in face.indices) {
            val a = face[i]; val b = face[(i + 1) % face.size]
            len += dist(p, a, b); cnt++
        }
        val cell = (len / cnt.coerceAtLeast(1)).coerceAtLeast(eps * 10)
        val grid = HashMap<Long, MutableList<Int>>()
        fun c(v: Float) = floor(v / cell).toLong()
        fun k(x: Long, y: Long, z: Long) = (x * 73856093L) xor (y * 19349663L) xor (z * 83492791L)
        for (i in 0 until n) grid.getOrPut(k(c(p[3 * i]), c(p[3 * i + 1]), c(p[3 * i + 2]))) { ArrayList(4) } += i
        var changed = false
        val faces = Array(mesh.faceCount) { f ->
            val face = mesh.faces[f]
            val out = ArrayList<Int>(face.size + 2)
            for (i in face.indices) {
                val a = face[i]; val b = face[(i + 1) % face.size]
                out += a
                val ax = p[3 * a]; val ay = p[3 * a + 1]; val az = p[3 * a + 2]
                val dx = p[3 * b] - ax; val dy = p[3 * b + 1] - ay; val dz = p[3 * b + 2] - az
                val l2 = (dx * dx + dy * dy + dz * dz).toDouble()
                if (l2 < eps * eps) continue
                val x0 = c(minOf(ax, p[3 * b])); val x1 = c(maxOf(ax, p[3 * b]))
                val y0 = c(minOf(ay, p[3 * b + 1])); val y1 = c(maxOf(ay, p[3 * b + 1]))
                val z0 = c(minOf(az, p[3 * b + 2])); val z1 = c(maxOf(az, p[3 * b + 2]))
                if ((x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 4096) continue
                val hits = ArrayList<Pair<Double, Int>>()
                for (x in x0..x1) for (y in y0..y1) for (z in z0..z1) {
                    for (v in grid[k(x, y, z)] ?: continue) {
                        if (v == a || v == b) continue
                        val px = p[3 * v] - ax; val py = p[3 * v + 1] - ay; val pz = p[3 * v + 2] - az
                        val t = (px * dx + py * dy + pz * dz) / l2
                        if (t <= 1e-6 || t >= 1 - 1e-6) continue
                        val qx = px - dx * t; val qy = py - dy * t; val qz = pz - dz * t
                        if (qx * qx + qy * qy + qz * qz <= eps * eps) hits += t to v
                    }
                }
                if (hits.isNotEmpty()) {
                    hits.sortBy { it.first }
                    hits.forEach { if (out.last() != it.second) out += it.second }
                    changed = true
                }
            }
            out.toIntArray()
        }
        return if (changed) Mesh(p, faces) else mesh
    }

    private fun dist(p: FloatArray, a: Int, b: Int): Double {
        val dx = p[3 * a] - p[3 * b]; val dy = p[3 * a + 1] - p[3 * b + 1]; val dz = p[3 * a + 2] - p[3 * b + 2]
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble())
    }

    /** Eksene hizalı kutunun (dünya) çokgenleri. */
    fun box(min: V3, max: V3, tag: Int): List<CsgPoly> {
        val m = Primitives.cube().mapPositions { V3(min.x + (it.x + 0.5f) * (max.x - min.x), min.y + (it.y + 0.5f) * (max.y - min.y), min.z + (it.z + 0.5f) * (max.z - min.z)) }
        return polygons(m, null) { tag }
    }
}
