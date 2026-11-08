package com.karapp.drawguidance.content.model3d

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Ağdaki bir kenar: iki köşenin sırası (küçük önce). */
data class EdgeRef(val a: Int, val b: Int) {
    companion object {
        fun of(a: Int, b: Int) = if (a < b) EdgeRef(a, b) else EdgeRef(b, a)
    }
}

/**
 * v24 ağ yardımcıları ve yüzeysel düzenleme işlemleri (saf işlevler; her biri yeni ağ döndürür):
 * - üçgenleme (dışbükey yüzde yelpaze, içbükey yüzde kulak kesme),
 * - kenara nokta ekleme ([splitEdge]),
 * - seçili kenarlarda eğme ([bend]: kenar yay olur), germe ([stretch]: kenar uzar/kısalır), itme ([push]: komşu
 *   yüzlerin normali yönünde içe/dışa) ve yuvarlatma ([round]: kenar pahlanıp kavisli şerit olur).
 */
object MeshOps {

    // ------------------------------------------------------------------------------------------ üçgenleme

    /** Yüzün üçgenleri: yüz içindeki köşe sıraları (0..n−1) üçlüler halinde. */
    fun triangulate(pos: FloatArray, face: IntArray): IntArray {
        val n = face.size
        if (n < 3) return IntArray(0)
        if (n == 3) return intArrayOf(0, 1, 2)
        // Newell normali ve en büyük bileşenine göre 2B izdüşüm
        var nx = 0f; var ny = 0f; var nz = 0f
        for (i in 0 until n) {
            val a = face[i] * 3; val b = face[(i + 1) % n] * 3
            nx += (pos[a + 1] - pos[b + 1]) * (pos[a + 2] + pos[b + 2])
            ny += (pos[a + 2] - pos[b + 2]) * (pos[a] + pos[b])
            nz += (pos[a] - pos[b]) * (pos[a + 1] + pos[b + 1])
        }
        val ax = abs(nx); val ay = abs(ny); val az = abs(nz)
        val (u, v, sign) = when {
            ax >= ay && ax >= az -> Triple(1, 2, if (nx >= 0f) 1f else -1f)
            ay >= ax && ay >= az -> Triple(2, 0, if (ny >= 0f) 1f else -1f)
            else -> Triple(0, 1, if (nz >= 0f) 1f else -1f)
        }
        val xs = FloatArray(n) { pos[face[it] * 3 + u] }
        val ys = FloatArray(n) { pos[face[it] * 3 + v] * sign }
        if (convex(xs, ys)) return IntArray((n - 2) * 3).also { t -> for (k in 0 until n - 2) { t[3 * k] = 0; t[3 * k + 1] = k + 1; t[3 * k + 2] = k + 2 } }
        return earClip(xs, ys)
    }

    private fun cross(xs: FloatArray, ys: FloatArray, a: Int, b: Int, c: Int): Float =
        (xs[b] - xs[a]) * (ys[c] - ys[a]) - (ys[b] - ys[a]) * (xs[c] - xs[a])

    private fun convex(xs: FloatArray, ys: FloatArray): Boolean {
        val n = xs.size
        for (i in 0 until n) if (cross(xs, ys, i, (i + 1) % n, (i + 2) % n) < -1e-9f) return false
        return true
    }

    private fun earClip(xs: FloatArray, ys: FloatArray): IntArray {
        val n = xs.size
        val idx = ArrayList<Int>(n).apply { for (i in 0 until n) add(i) }
        val out = ArrayList<Int>((n - 2) * 3)
        var guard = 0
        while (idx.size > 3 && guard++ < n * n) {
            var clipped = false
            for (k in idx.indices) {
                val a = idx[(k + idx.size - 1) % idx.size]; val b = idx[k]; val c = idx[(k + 1) % idx.size]
                if (cross(xs, ys, a, b, c) <= 1e-12f) continue
                var inside = false
                for (p in idx) {
                    if (p == a || p == b || p == c) continue
                    if (inTri(xs[p], ys[p], xs[a], ys[a], xs[b], ys[b], xs[c], ys[c])) { inside = true; break }
                }
                if (inside) continue
                out += a; out += b; out += c
                idx.removeAt(k)
                clipped = true
                break
            }
            if (!clipped) break
        }
        if (idx.size == 3) { out += idx[0]; out += idx[1]; out += idx[2] }
        else if (idx.size > 3) for (k in 1 until idx.size - 1) { out += idx[0]; out += idx[k]; out += idx[k + 1] }
        return out.toIntArray()
    }

    private fun inTri(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Boolean {
        val d1 = (bx - ax) * (py - ay) - (by - ay) * (px - ax)
        val d2 = (cx - bx) * (py - by) - (cy - by) * (px - bx)
        val d3 = (ax - cx) * (py - cy) - (ay - cy) * (px - cx)
        return d1 >= 0f && d2 >= 0f && d3 >= 0f
    }

    // ------------------------------------------------------------------------------------------ kenar yardımcıları

    /** Kenara değen yüzler. */
    fun facesOfEdge(mesh: Mesh, e: EdgeRef): List<Int> {
        val out = ArrayList<Int>(2)
        for (f in mesh.vertexFaces.getOrNull(e.a) ?: return out) {
            val face = mesh.faces[f]
            for (i in face.indices) {
                val p = face[i]; val q = face[(i + 1) % face.size]
                if ((p == e.a && q == e.b) || (p == e.b && q == e.a)) { out += f; break }
            }
        }
        return out
    }

    /** Ağın tüm kenarları. */
    fun edgeList(mesh: Mesh): List<EdgeRef> {
        val e = mesh.edges
        return List(e.size / 4) { EdgeRef(e[4 * it], e[4 * it + 1]) }
    }

    private fun v(mesh: Mesh, i: Int) = mesh.vertex(i)

    /**
     * Kenarın dışa yönü (birim): iki yüzlü kenarda komşu yüzlerin ortalama normali; tek yüzlü (açık) kenarda yüzün
     * düzleminde, yüzün merkezinden dışarı (düzlemin kenarı eğilince dış çizgisi kıvrılır).
     */
    fun edgeNormal(mesh: Mesh, e: EdgeRef): V3 {
        val adj = facesOfEdge(mesh, e)
        if (adj.size == 1) {
            val f = adj[0]
            val fn = mesh.faceNormal(f)
            val pa = v(mesh, e.a); val pb = v(mesh, e.b)
            var out = ((pb - pa) cross fn).normalized()
            val c = mesh.faceCenter(f)
            if ((((pa + pb) * 0.5f) - c) dot out < 0f) out = -out
            return out
        }
        var n = V3.ZERO
        adj.forEach { n = n + mesh.faceNormal(it) }
        return if (n.length < 1e-6f) mesh.faceNormal(mesh.vertexFaces[e.a].firstOrNull() ?: 0) else n.normalized()
    }

    // ------------------------------------------------------------------------------------------ nokta ekleme

    /**
     * Kenara [t] (0..1, a'dan b'ye) oranında yeni köşe ekler: kenara değen her yüzün köşe sırasına girer (yüz şekli
     * değişmez). Yeni ağ ve yeni köşenin sırası.
     */
    fun splitEdge(mesh: Mesh, e: EdgeRef, t: Float): Pair<Mesh, Int> {
        val tt = t.coerceIn(0.02f, 0.98f)
        val pa = v(mesh, e.a); val pb = v(mesh, e.b)
        val np = pa + (pb - pa) * tt
        val ni = mesh.vertexCount
        val pos = mesh.pos.copyOf(mesh.pos.size + 3)
        pos[3 * ni] = np.x; pos[3 * ni + 1] = np.y; pos[3 * ni + 2] = np.z
        val faces = Array(mesh.faceCount) { mesh.faces[it] }
        val uvs = mesh.uvs?.let { u -> Array(u.size) { u[it] } }
        for (f in facesOfEdge(mesh, e)) {
            val face = faces[f]
            val n = face.size
            for (i in 0 until n) {
                val p = face[i]; val q = face[(i + 1) % n]
                if (!((p == e.a && q == e.b) || (p == e.b && q == e.a))) continue
                // p ile q arasına yeni köşe
                val local = if (p == e.a) tt else 1f - tt
                faces[f] = IntArray(n + 1) { k -> if (k <= i) face[k] else if (k == i + 1) ni else face[k - 1] }
                if (uvs != null) {
                    val u = uvs[f]
                    val j = (i + 1) % n
                    val nu = u[2 * i] + (u[2 * j] - u[2 * i]) * local
                    val nv = u[2 * i + 1] + (u[2 * j + 1] - u[2 * i + 1]) * local
                    uvs[f] = FloatArray((n + 1) * 2) { k ->
                        val c = k / 2
                        when {
                            c <= i -> u[k]
                            c == i + 1 -> if (k % 2 == 0) nu else nv
                            else -> u[k - 2]
                        }
                    }
                }
                break
            }
        }
        return Mesh(pos, faces, uvs) to ni
    }

    // ------------------------------------------------------------------------------------------ eğme

    /**
     * Seçili kenarları yay yapar: her kenar [segments] parçaya bölünür, ara noktalar komşu yüzlerin ortalama normali
     * yönünde, kenar boyunun [amount] katı (ortada en çok yarısı) kadar dışarı (eksi: içeri) itilir.
     */
    fun bend(mesh: Mesh, edges: Collection<EdgeRef>, amount: Float, segments: Int = 4): Mesh {
        if (edges.isEmpty() || abs(amount) < 1e-4f) return mesh
        var m = mesh
        val seg = segments.coerceIn(2, 12)
        for (e0 in edges.distinct()) {
            if (e0.a >= m.vertexCount || e0.b >= m.vertexCount || facesOfEdge(m, e0).isEmpty()) continue
            val pa = v(m, e0.a); val pb = v(m, e0.b)
            val len = (pb - pa).length
            val n = edgeNormal(m, e0)
            // a'dan b'ye sırayla bölünür: kalan kenar her seferinde (yeni köşe, b)
            var a = e0.a
            val added = ArrayList<Pair<Int, Float>>()
            for (k in 1 until seg) {
                val t = k / seg.toFloat()
                // Kalan kenar (a, b) üzerinde, a'dan b'ye göre yerel oran
                val remainingStart = (k - 1) / seg.toFloat()
                val local = (t - remainingStart) / (1f - remainingStart)
                val split = splitEdge(m, EdgeRef.of(a, e0.b), if (a < e0.b) local else 1f - local)
                m = split.first
                added += split.second to t
                a = split.second
            }
            val pos = m.pos.copyOf()
            for ((vi, t) in added) {
                val h = amount * len * 2f * t * (1f - t)
                pos[3 * vi] += n.x * h; pos[3 * vi + 1] += n.y * h; pos[3 * vi + 2] += n.z * h
            }
            m = Mesh(pos, m.faces, m.uvs)
        }
        return m
    }

    // ------------------------------------------------------------------------------------------ germe ve itme

    /** Seçili kenarları kendi ortalarına göre [factor] katına uzatır/kısaltır (ortak köşelerde ortalama). */
    fun stretch(mesh: Mesh, edges: Collection<EdgeRef>, factor: Float): Mesh {
        if (edges.isEmpty()) return mesh
        val acc = HashMap<Int, V3>(); val cnt = HashMap<Int, Int>()
        for (e in edges.distinct()) {
            if (e.a >= mesh.vertexCount || e.b >= mesh.vertexCount) continue
            val pa = v(mesh, e.a); val pb = v(mesh, e.b)
            val mid = (pa + pb) * 0.5f
            acc[e.a] = (acc[e.a] ?: V3.ZERO) + (pa - mid) * (factor - 1f); cnt[e.a] = (cnt[e.a] ?: 0) + 1
            acc[e.b] = (acc[e.b] ?: V3.ZERO) + (pb - mid) * (factor - 1f); cnt[e.b] = (cnt[e.b] ?: 0) + 1
        }
        return displace(mesh, acc, cnt)
    }

    /** Seçili kenarları komşu yüzlerinin ortalama normali yönünde [distance] (nesne birimi) kadar iter. */
    fun push(mesh: Mesh, edges: Collection<EdgeRef>, distance: Float): Mesh {
        if (edges.isEmpty()) return mesh
        val acc = HashMap<Int, V3>(); val cnt = HashMap<Int, Int>()
        for (e in edges.distinct()) {
            if (e.a >= mesh.vertexCount || e.b >= mesh.vertexCount) continue
            val d = edgeNormal(mesh, e) * distance
            acc[e.a] = (acc[e.a] ?: V3.ZERO) + d; cnt[e.a] = (cnt[e.a] ?: 0) + 1
            acc[e.b] = (acc[e.b] ?: V3.ZERO) + d; cnt[e.b] = (cnt[e.b] ?: 0) + 1
        }
        return displace(mesh, acc, cnt)
    }

    /** Köşeleri biriken ötelemelerin ortalaması kadar taşır. */
    private fun displace(mesh: Mesh, acc: Map<Int, V3>, cnt: Map<Int, Int>): Mesh {
        if (acc.isEmpty()) return mesh
        val pos = mesh.pos.copyOf()
        for ((i, d) in acc) {
            val k = (cnt[i] ?: 1).toFloat()
            pos[3 * i] += d.x / k; pos[3 * i + 1] += d.y / k; pos[3 * i + 2] += d.z / k
        }
        return Mesh(pos, mesh.faces, mesh.uvs)
    }

    // ------------------------------------------------------------------------------------------ yuvarlatma

    /**
     * Kenarı yuvarlatır (pah + kavis): kenarın iki yüzü [radius] kadar geri çekilir, araya [segments] dilimli kavisli
     * şerit girer, uçlardaki kapak yüzlerine kavis noktaları eklenir. Yalnız iki yüzlü ve uçlarında üç yüz buluşan
     * kenarlarda (küp, prizma kenarları gibi) yapılır; olmuyorsa null.
     */
    fun round(mesh: Mesh, e: EdgeRef, radius: Float, segments: Int = 4): Mesh? {
        if (e.a >= mesh.vertexCount || e.b >= mesh.vertexCount) return null
        val adj = facesOfEdge(mesh, e)
        if (adj.size != 2) return null
        // F1'de a → b sırası
        fun has(f: Int, p: Int, q: Int): Boolean {
            val face = mesh.faces[f]
            for (i in face.indices) if (face[i] == p && face[(i + 1) % face.size] == q) return true
            return false
        }
        val f1 = if (has(adj[0], e.a, e.b)) adj[0] else adj[1]
        val f2 = if (f1 == adj[0]) adj[1] else adj[0]
        if (!has(f2, e.b, e.a)) return null
        val a = e.a; val b = e.b
        if (mesh.vertexFaces[a].size != 3 || mesh.vertexFaces[b].size != 3) return null
        fun prev(f: Int, x: Int): Int { val face = mesh.faces[f]; val i = face.indexOf(x); return face[(i - 1 + face.size) % face.size] }
        fun next(f: Int, x: Int): Int { val face = mesh.faces[f]; val i = face.indexOf(x); return face[(i + 1) % face.size] }
        val x1 = prev(f1, a); val x2 = next(f2, a)
        val y1 = next(f1, b); val y2 = prev(f2, b)
        val capA = mesh.vertexFaces[a].firstOrNull { it != f1 && it != f2 } ?: return null
        val capB = mesh.vertexFaces[b].firstOrNull { it != f1 && it != f2 } ?: return null
        if (capA == capB) return null
        val pa = v(mesh, a); val pb = v(mesh, b)
        val limit = 0.45f * minOf((v(mesh, x1) - pa).length, (v(mesh, x2) - pa).length, (v(mesh, y1) - pb).length, (v(mesh, y2) - pb).length)
        val r = radius.coerceIn(1e-4f, max(1e-4f, limit))
        val k = segments.coerceIn(1, 12)
        fun arc(p: V3, s: V3, t: V3): List<V3> {
            val p1 = p + (s - p).normalized() * r
            val p2 = p + (t - p).normalized() * r
            return List(k + 1) { i ->
                val u = i / k.toFloat()
                p1 * ((1 - u) * (1 - u)) + p * (2 * u * (1 - u)) + p2 * (u * u)
            }
        }
        val arcA = arc(pa, v(mesh, x1), v(mesh, x2))
        val arcB = arc(pb, v(mesh, y1), v(mesh, y2))
        val base = mesh.vertexCount
        val pos = mesh.pos.copyOf(mesh.pos.size + (k + 1) * 6)
        fun put(i: Int, p: V3) { pos[3 * i] = p.x; pos[3 * i + 1] = p.y; pos[3 * i + 2] = p.z }
        val ia = IntArray(k + 1) { base + it }
        val ib = IntArray(k + 1) { base + k + 1 + it }
        for (i in 0..k) { put(ia[i], arcA[i]); put(ib[i], arcB[i]) }
        val faces = ArrayList<IntArray>(mesh.faceCount + k)
        for (f in 0 until mesh.faceCount) {
            val face = mesh.faces[f]
            faces += when (f) {
                f1 -> IntArray(face.size) { face[it].let { x -> if (x == a) ia[0] else if (x == b) ib[0] else x } }
                f2 -> IntArray(face.size) { face[it].let { x -> if (x == a) ia[k] else if (x == b) ib[k] else x } }
                capA -> replaceInCap(face, a, x1, ia)
                capB -> replaceInCap(face, b, y1, ib)
                else -> face
            }
        }
        for (i in 0 until k) faces += intArrayOf(ib[i], ia[i], ia[i + 1], ib[i + 1])
        return Mesh(pos, faces.toTypedArray()).compact()
    }

    /** Kapak yüzünde [corner] köşesinin yerine kavis noktaları: [side1] tarafındaki uç ([arc][0]) ona komşu olur. */
    private fun replaceInCap(face: IntArray, corner: Int, side1: Int, arc: IntArray): IntArray {
        val i = face.indexOf(corner)
        if (i < 0) return face
        val before = face[(i - 1 + face.size) % face.size]
        val seq = if (before == side1) arc.toList() else arc.reversed()
        val out = ArrayList<Int>(face.size + arc.size)
        for (j in face.indices) if (j == i) out.addAll(seq) else out += face[j]
        return out.toIntArray()
    }

    /** Ağı kaplayan kutunun en uzun kenarı (işlem miktarlarını ölçeklemek için). */
    fun extent(mesh: Mesh): Float {
        val b = mesh.bounds
        val s = b.size
        return max(1e-4f, max(s.x, max(s.y, s.z)))
    }

    /** İki nokta arası (yardımcı). */
    fun dist(a: V3, b: V3): Float = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z))

    @Suppress("unused")
    private fun clamp01(x: Float) = min(1f, max(0f, x))
}
