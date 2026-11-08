package com.karapp.drawguidance.content.model3d

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Yüz sırası değişen işlemin sonucu: yeni ağ ve her yeni yüzün malzemesini alacağı eski yüz ([origin]; −1: parçanın
 * kendi malzemesi).
 */
class TopoResult(val mesh: Mesh, val origin: IntArray)

/** Halkadan kesmenin sonucu: kalan şekil, kesilen parça ve parçanın kalandan uzaklaşan yönü (nesne uzayı, birim). */
class CutResult(val rest: TopoResult, val piece: TopoResult, val away: V3)

/**
 * v25 ağın yapısını değiştiren düzenlemeler (Nokta aracı ve yüzey işlemleri): nokta silme ([deleteVertex]), iki noktayı
 * kenarla birleştirme ([connect]), noktalardan oluşan kapalı halkadan kesip iki tarafı kapatma ([cutLoop]), yüzey bölgesi
 * seçimi ([surfaceRegion]), bölgeyi birlikte yükseltme ([extrudeRegion]) ve kopuk parçaları ayırma ([components]).
 */
object MeshTopo {

    // ------------------------------------------------------------------------------------------ nokta silme

    /**
     * Noktayı siler: noktaya değen yüzlerden çıkarılır; kenar üstündeki (iki yüzlü) nokta iz bırakmaz, köşe noktası
     * silinince açılan boşluk komşu noktalardan geçen yeni bir yüzle kapatılır (köşe kesilmiş gibi). Üç köşeden aza
     * inen yüzler kalkar. Ağın açık kenarındaki noktada kapak yüzü eklenmez.
     */
    fun deleteVertex(mesh: Mesh, v: Int): TopoResult? {
        if (v !in 0 until mesh.vertexCount) return null
        if (mesh.vertexFaces[v].isEmpty()) return null
        val faces = ArrayList<IntArray>(mesh.faceCount + 1)
        val origin = ArrayList<Int>(mesh.faceCount + 1)
        val next = HashMap<Int, Int>()
        var capOrigin = -1
        for (f in mesh.faces.indices) {
            val face = mesh.faces[f]
            val i = face.indexOf(v)
            if (i < 0) { faces += face; origin += f; continue }
            val n = face.size
            val a = face[(i - 1 + n) % n]; val b = face[(i + 1) % n]
            if (a != b) { next[a] = b; if (capOrigin < 0) capOrigin = f }
            val reduced = face.filter { it != v }.toIntArray()
            if (reduced.size >= 3) { faces += reduced; origin += f }
        }
        // Boşluğun sınırı: kirişlerin (a → b) zinciri; kapak ters yönde
        val visited = HashSet<Int>()
        for (start in next.keys.toList()) {
            if (start in visited) continue
            val loop = ArrayList<Int>()
            var cur = start
            var closed = false
            while (true) {
                if (!visited.add(cur)) { closed = cur == start; break }
                loop += cur
                cur = next[cur] ?: break
                if (cur == start) { closed = true; break }
            }
            if (closed && loop.size >= 3) {
                faces += loop.reversed().toIntArray()
                origin += capOrigin
            }
        }
        if (faces.isEmpty()) return null
        return compacted(Mesh(mesh.pos, faces.toTypedArray()), origin.toIntArray())
    }

    // ------------------------------------------------------------------------------------------ iki noktayı birleştirme

    /** İki noktanın ortak yüzü (yan yana değil); yoksa −1. */
    fun commonFace(mesh: Mesh, a: Int, b: Int): Int {
        if (a !in 0 until mesh.vertexCount || b !in 0 until mesh.vertexCount || a == b) return -1
        val bf = mesh.vertexFaces[b].toSet()
        var best = -1
        for (f in mesh.vertexFaces[a]) {
            if (f !in bf) continue
            val face = mesh.faces[f]
            val ia = face.indexOf(a); val ib = face.indexOf(b)
            val d = abs(ia - ib)
            if (d == 1 || d == face.size - 1) continue
            best = f
            break
        }
        return best
    }

    /** İki nokta zaten kenarla bağlı mı. */
    fun isEdge(mesh: Mesh, a: Int, b: Int): Boolean =
        a != b && a in 0 until mesh.vertexCount && b in 0 until mesh.vertexCount && MeshOps.facesOfEdge(mesh, EdgeRef.of(a, b)).isNotEmpty()

    /**
     * İki noktayı yeni bir kenarla birleştirir: ortak yüz ikiye bölünür (yüz yerinde kalır, ikinci yarısı ağın sonuna
     * eklenir). Noktalar aynı yüzde değilse ya da zaten kenarsa null.
     */
    fun connect(mesh: Mesh, a: Int, b: Int): TopoResult? {
        val f = commonFace(mesh, a, b)
        if (f < 0) return null
        val face = mesh.faces[f]
        val n = face.size
        val ia = face.indexOf(a); val ib = face.indexOf(b)
        fun arc(from: Int, to: Int): IntArray {
            val out = ArrayList<Int>()
            var i = from
            while (true) { out += i; if (i == to) break; i = (i + 1) % n }
            return out.toIntArray()
        }
        val k1 = arc(ia, ib); val k2 = arc(ib, ia)
        val faces = Array(mesh.faceCount + 1) { i -> if (i < mesh.faceCount) mesh.faces[i] else IntArray(0) }
        faces[f] = IntArray(k1.size) { face[k1[it]] }
        faces[mesh.faceCount] = IntArray(k2.size) { face[k2[it]] }
        val uvs = mesh.uvs?.let { u ->
            Array(faces.size) { i ->
                when (i) {
                    f -> FloatArray(k1.size * 2) { j -> u[f][2 * k1[j / 2] + j % 2] }
                    mesh.faceCount -> FloatArray(k2.size * 2) { j -> u[f][2 * k2[j / 2] + j % 2] }
                    else -> u[i]
                }
            }
        }
        val origin = IntArray(faces.size) { if (it < mesh.faceCount) it else f }
        return TopoResult(Mesh(mesh.pos, faces, uvs), origin)
    }

    // ------------------------------------------------------------------------------------------ halkadan kesme

    /**
     * Noktalardan oluşan kapalı halka boyunca keser: halkanın iki yanındaki yüzler ayrılır, ikisinin de kesik yeri
     * halkadan geçen bir yüzle kapatılır. Küçük (alanı az) taraf kesilen parça olur. Ardışık noktalar kenarla bağlı
     * olmalı; halka yüzeyi ikiye ayırmıyorsa (ör. simidin kolunu dolaşan halka değilse) null.
     */
    fun cutLoop(mesh: Mesh, loop: List<Int>): CutResult? {
        if (loop.size < 3 || loop.toSet().size != loop.size) return null
        val n = loop.size
        val loopEdges = HashSet<EdgeRef>()
        for (i in 0 until n) {
            val a = loop[i]; val b = loop[(i + 1) % n]
            if (!isEdge(mesh, a, b)) return null
            loopEdges += EdgeRef.of(a, b)
        }
        // Yüzleri halka dışındaki kenarlardan birleştir
        val parent = IntArray(mesh.faceCount) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        fun union(a: Int, b: Int) { val ra = find(a); val rb = find(b); if (ra != rb) parent[ra] = rb }
        val e = mesh.edges
        var i = 0
        while (i < e.size) {
            val f1 = e[i + 2]; val f2 = e[i + 3]
            if (f2 >= 0 && EdgeRef.of(e[i], e[i + 1]) !in loopEdges) union(f1, f2)
            i += 4
        }
        // Halkanın sol (a → b yönlü kenarı içeren) ve sağ yüzleri
        var left = -1; var right = -1
        for (k in 0 until n) {
            val a = loop[k]; val b = loop[(k + 1) % n]
            for (f in MeshOps.facesOfEdge(mesh, EdgeRef.of(a, b))) {
                val face = mesh.faces[f]
                val ia = face.indexOf(a)
                val dir = face[(ia + 1) % face.size] == b
                val c = find(f)
                if (dir) { if (left < 0) left = c else if (left != c) return null }
                else { if (right < 0) right = c else if (right != c) return null }
            }
        }
        if (left < 0 || right < 0 || left == right) return null
        val leftFaces = ArrayList<Int>(); val restFaces = ArrayList<Int>()
        for (f in 0 until mesh.faceCount) if (find(f) == left) leftFaces += f
        val rightSet = (0 until mesh.faceCount).filter { find(it) == right }
        fun area(fs: List<Int>): Double = fs.sumOf { faceArea(mesh, it) }
        // Küçük taraf kesilen parça; diğer tüm yüzler (ağın kopuk parçaları dahil) kalanda
        val pieceIsLeft = area(leftFaces) <= area(rightSet)
        val pieceFaces = if (pieceIsLeft) leftFaces else rightSet
        val pieceSet = pieceFaces.toHashSet()
        for (f in 0 until mesh.faceCount) if (f !in pieceSet) restFaces += f
        val forward = loop.toIntArray()
        val backward = loop.reversed().toIntArray()
        // Sol taraf halkayı a → b yönünde kullanır: kapağı b → a (ters); sağ tarafın kapağı ileri yönde
        val pieceCap = if (pieceIsLeft) backward else forward
        val restCap = if (pieceIsLeft) forward else backward
        val capOriginPiece = pieceFaces.firstOrNull() ?: -1
        val capOriginRest = restFaces.firstOrNull { find(it) == (if (pieceIsLeft) right else left) } ?: -1
        fun build(fs: List<Int>, cap: IntArray, capOrigin: Int): TopoResult {
            val faces = ArrayList<IntArray>(fs.size + 1); val origin = ArrayList<Int>(fs.size + 1)
            fs.forEach { faces += mesh.faces[it]; origin += it }
            faces += cap; origin += capOrigin
            return compacted(Mesh(mesh.pos, faces.toTypedArray()), origin.toIntArray())
        }
        val rest = build(restFaces, restCap, capOriginRest)
        val piece = build(pieceFaces, pieceCap, capOriginPiece)
        val away = (centroid(piece.mesh) - centroid(rest.mesh)).let { if (it.length < 1e-6f) loopNormal(mesh, loop) else it.normalized() }
        return CutResult(rest, piece, away)
    }

    private fun loopNormal(mesh: Mesh, loop: List<Int>): V3 {
        var nx = 0f; var ny = 0f; var nz = 0f
        for (i in loop.indices) {
            val a = mesh.vertex(loop[i]); val b = mesh.vertex(loop[(i + 1) % loop.size])
            nx += (a.y - b.y) * (a.z + b.z); ny += (a.z - b.z) * (a.x + b.x); nz += (a.x - b.x) * (a.y + b.y)
        }
        return V3(nx, ny, nz).normalized()
    }

    fun faceArea(mesh: Mesh, f: Int): Double {
        val face = mesh.faces[f]
        var nx = 0.0; var ny = 0.0; var nz = 0.0
        val p = mesh.pos
        for (i in face.indices) {
            val a = face[i] * 3; val b = face[(i + 1) % face.size] * 3
            nx += (p[a + 1] - p[b + 1]).toDouble() * (p[a + 2] + p[b + 2])
            ny += (p[a + 2] - p[b + 2]).toDouble() * (p[a] + p[b])
            nz += (p[a] - p[b]).toDouble() * (p[a + 1] + p[b + 1])
        }
        return sqrt(nx * nx + ny * ny + nz * nz) * 0.5
    }

    fun centroid(mesh: Mesh): V3 {
        if (mesh.vertexCount == 0) return V3.ZERO
        var x = 0.0; var y = 0.0; var z = 0.0
        for (i in 0 until mesh.vertexCount) { x += mesh.pos[3 * i]; y += mesh.pos[3 * i + 1]; z += mesh.pos[3 * i + 2] }
        val k = 1.0 / mesh.vertexCount
        return V3((x * k).toFloat(), (y * k).toFloat(), (z * k).toFloat())
    }

    // ------------------------------------------------------------------------------------------ yüzey bölgesi

    /**
     * Dokunulan yüzün yüzeyi: komşu yüzlerden normali [maxAngleDeg]'den az dönenler (silindirin yanı, kürenin tamamı,
     * küpün bir yüzü). Geniş yüzeyde [limit] yüzde durur.
     */
    fun surfaceRegion(mesh: Mesh, seed: Int, maxAngleDeg: Float = 32f, limit: Int = 20_000): Set<Int> {
        if (seed !in 0 until mesh.faceCount) return emptySet()
        val cosMax = kotlin.math.cos(Math.toRadians(maxAngleDeg.toDouble())).toFloat()
        val adj = adjacency(mesh)
        val fn = mesh.faceNormals
        val out = LinkedHashSet<Int>()
        val queue = ArrayDeque<Int>()
        out += seed; queue += seed
        while (queue.isNotEmpty() && out.size < limit) {
            val f = queue.removeFirst()
            for (g in adj[f]) {
                if (g in out) continue
                val dot = fn[3 * f] * fn[3 * g] + fn[3 * f + 1] * fn[3 * g + 1] + fn[3 * f + 2] * fn[3 * g + 2]
                if (dot >= cosMax) { out += g; queue += g }
            }
        }
        return out
    }

    /** Yüz komşulukları (kenar paylaşanlar). */
    fun adjacency(mesh: Mesh): Array<IntArray> {
        val lists = Array(mesh.faceCount) { ArrayList<Int>(4) }
        val e = mesh.edges
        var i = 0
        while (i < e.size) {
            val f1 = e[i + 2]; val f2 = e[i + 3]
            if (f2 >= 0 && f1 != f2) { lists[f1] += f2; lists[f2] += f1 }
            i += 4
        }
        return Array(mesh.faceCount) { lists[it].toIntArray() }
    }

    /** Kopuk parçalar: kenarla bağlı yüz kümeleri (tek küme varsa bir eleman). */
    fun components(mesh: Mesh): List<List<Int>> {
        val adj = adjacency(mesh)
        // Köşe paylaşan ama kenar paylaşmayanlar da aynı parçadır
        val parent = IntArray(mesh.faceCount) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        for (f in adj.indices) for (g in adj[f]) { val a = find(f); val b = find(g); if (a != b) parent[a] = b }
        for (vf in mesh.vertexFaces) for (k in 1 until vf.size) { val a = find(vf[0]); val b = find(vf[k]); if (a != b) parent[a] = b }
        return (0 until mesh.faceCount).groupBy { find(it) }.values.toList()
    }

    /** Yüzlerin alt ağı (köşeler yeniden numaralanır). */
    fun subMesh(mesh: Mesh, faces: List<Int>): TopoResult =
        compacted(Mesh(mesh.pos, Array(faces.size) { mesh.faces[faces[it]] }), faces.toIntArray())

    // ------------------------------------------------------------------------------------------ bölge yükseltme

    /**
     * Seçili yüzleri birlikte yükseltir (bölge): bölgenin iç kenarları duvar oluşturmaz, yalnız dış sınırına yan yüzler
     * eklenir. Noktalar bölgenin ortalama normali yönünde [distance] (nesne birimi) kadar taşınır; eksi değer içeri iter.
     * Yüzler sırasını korur, yan yüzler ağın sonuna eklenir (komşu bölge yüzünün malzemesiyle).
     */
    fun extrudeRegion(mesh: Mesh, region: Set<Int>, distance: Float): TopoResult {
        val faces = region.filter { it in 0 until mesh.faceCount }.toSet()
        if (faces.isEmpty() || abs(distance) < 1e-7f) return TopoResult(mesh, IntArray(mesh.faceCount) { it })
        val fn = mesh.faceNormals
        val normals = HashMap<Int, FloatArray>()
        for (f in faces) for (v in mesh.faces[f]) {
            val acc = normals.getOrPut(v) { FloatArray(3) }
            acc[0] += fn[3 * f]; acc[1] += fn[3 * f + 1]; acc[2] += fn[3 * f + 2]
        }
        val newIndex = HashMap<Int, Int>()
        var count = mesh.vertexCount
        for (v in normals.keys) newIndex[v] = count++
        val pos = mesh.pos.copyOf(count * 3)
        for ((v, acc) in normals) {
            val l = sqrt(acc[0] * acc[0] + acc[1] * acc[1] + acc[2] * acc[2]).coerceAtLeast(1e-9f)
            // Köşede ortalama normal kısalır: uzaklık açıyla düzeltilir (yan yüzler düz kalsın)
            val nx = acc[0] / l; val ny = acc[1] / l; val nz = acc[2] / l
            var minDot = 1f
            for (f in mesh.vertexFaces[v]) if (f in faces) minDot = minOf(minDot, nx * fn[3 * f] + ny * fn[3 * f + 1] + nz * fn[3 * f + 2])
            val d = distance / max(0.35f, minDot)
            val j = newIndex[v]!!
            pos[3 * j] = mesh.pos[3 * v] + nx * d; pos[3 * j + 1] = mesh.pos[3 * v + 1] + ny * d; pos[3 * j + 2] = mesh.pos[3 * v + 2] + nz * d
        }
        // Bölgedeki yönlü kenarlar: karşı yönü bölgede olmayanlar sınırdır
        val directed = HashSet<Long>()
        for (f in faces) { val fc = mesh.faces[f]; for (i in fc.indices) directed += key(fc[i], fc[(i + 1) % fc.size]) }
        val out = ArrayList<IntArray>(mesh.faceCount + 16)
        val origin = ArrayList<Int>(mesh.faceCount + 16)
        for (f in 0 until mesh.faceCount) {
            val fc = mesh.faces[f]
            out += if (f in faces) IntArray(fc.size) { newIndex[fc[it]]!! } else fc
            origin += f
        }
        for (f in faces) {
            val fc = mesh.faces[f]
            for (i in fc.indices) {
                val a = fc[i]; val b = fc[(i + 1) % fc.size]
                if (key(b, a) in directed) continue
                out += intArrayOf(a, b, newIndex[b]!!, newIndex[a]!!)
                origin += f
            }
        }
        return TopoResult(Mesh(pos, out.toTypedArray()), origin.toIntArray())
    }

    /** Seçili yüzlerin noktalarını bölgenin merkezine göre [s] katına getirir. */
    fun scaleRegion(mesh: Mesh, region: Set<Int>, s: Float): Mesh {
        val verts = region.filter { it in 0 until mesh.faceCount }.flatMap { mesh.faces[it].toList() }.toSet()
        if (verts.isEmpty()) return mesh
        var cx = 0f; var cy = 0f; var cz = 0f
        for (v in verts) { cx += mesh.pos[3 * v]; cy += mesh.pos[3 * v + 1]; cz += mesh.pos[3 * v + 2] }
        cx /= verts.size; cy /= verts.size; cz /= verts.size
        val p = mesh.pos.copyOf()
        for (v in verts) {
            p[3 * v] = cx + (p[3 * v] - cx) * s; p[3 * v + 1] = cy + (p[3 * v + 1] - cy) * s; p[3 * v + 2] = cz + (p[3 * v + 2] - cz) * s
        }
        return Mesh(p, mesh.faces, mesh.uvs)
    }

    /** Yüzleri siler (kullanılmayan noktalar atılır). */
    fun deleteFaces(mesh: Mesh, region: Set<Int>): TopoResult? {
        val keep = (0 until mesh.faceCount).filter { it !in region }
        if (keep.isEmpty() || keep.size == mesh.faceCount) return null
        return subMesh(mesh, keep)
    }

    // ------------------------------------------------------------------------------------------ yardımcılar

    private fun key(a: Int, b: Int): Long = (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL)

    /** Kullanılmayan noktaları atar, yüz kökenlerini korur. */
    private fun compacted(mesh: Mesh, origin: IntArray): TopoResult = TopoResult(mesh.compact().let { Mesh(it.pos, it.faces) }, origin)
}
