package com.karapp.drawguidance.content.model3d

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Çokgen ağ (nesne uzayında): [pos] köşe konumları (x, y, z…), [faces] her yüzün köşe sıraları (dışarıdan bakınca saat
 * yönünün tersine). Köşeler yüzler arasında paylaşılır: bir köşe taşınınca ona değen tüm yüzler birlikte değişir.
 * Değişmezdir; düzenleme işlemleri yeni ağ döndürür. Komşuluk, yüz normalleri ve kenarlar ilk istendiğinde hesaplanıp
 * saklanır. v24 [uvs]: yüz köşelerinin kendi doku koordinatları (içe aktarılan glTF modellerinde; yüz başına köşe
 * sayısı × 2); yüzleri değiştiren işlemlerden sonra kalkar (doku eşlemesi kutuya döner).
 */
class Mesh(val pos: FloatArray, val faces: Array<IntArray>, val uvs: Array<FloatArray>? = null) {

    /**
     * v24 yüzlerin üçgenleri: her yüz için köşe sıraları (yüzün içindeki, 0..n−1) üçlüler halinde. Dışbükey yüzler
     * yelpaze, içbükey yüzler kulak kesme yöntemiyle bölünür (kenarı eğilmiş yüzler de doğru çizilsin).
     */
    val triangles: Array<IntArray> by lazy { Array(faces.size) { f -> MeshOps.triangulate(pos, faces[f]) } }

    /** Toplam üçgen sayısı. */
    val triangleCount: Int get() = triangles.sumOf { it.size / 3 }

    val vertexCount: Int get() = pos.size / 3
    val faceCount: Int get() = faces.size

    fun vertex(i: Int) = V3(pos[3 * i], pos[3 * i + 1], pos[3 * i + 2])

    /** Yüz normali (Newell yöntemi: düzlemsel olmayan çokgende de kararlı), birim. */
    val faceNormals: FloatArray by lazy {
        val out = FloatArray(faces.size * 3)
        for (f in faces.indices) {
            val face = faces[f]
            var nx = 0f; var ny = 0f; var nz = 0f
            for (i in face.indices) {
                val a = face[i] * 3; val b = face[(i + 1) % face.size] * 3
                nx += (pos[a + 1] - pos[b + 1]) * (pos[a + 2] + pos[b + 2])
                ny += (pos[a + 2] - pos[b + 2]) * (pos[a] + pos[b])
                nz += (pos[a] - pos[b]) * (pos[a + 1] + pos[b + 1])
            }
            val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-12f)
            out[3 * f] = nx / l; out[3 * f + 1] = ny / l; out[3 * f + 2] = nz / l
        }
        out
    }

    fun faceNormal(f: Int) = V3(faceNormals[3 * f], faceNormals[3 * f + 1], faceNormals[3 * f + 2])

    fun faceCenter(f: Int): V3 {
        val face = faces[f]
        var x = 0f; var y = 0f; var z = 0f
        for (v in face) { x += pos[3 * v]; y += pos[3 * v + 1]; z += pos[3 * v + 2] }
        val n = face.size.coerceAtLeast(1).toFloat()
        return V3(x / n, y / n, z / n)
    }

    /** Her köşeye değen yüzler. */
    val vertexFaces: Array<IntArray> by lazy {
        val counts = IntArray(vertexCount)
        faces.forEach { f -> f.forEach { counts[it]++ } }
        val out = Array(vertexCount) { IntArray(counts[it]) }
        val fill = IntArray(vertexCount)
        faces.forEachIndexed { fi, f -> f.forEach { v -> out[v][fill[v]++] = fi } }
        out
    }

    /**
     * Kenarlar: (a, b, yüz1, yüz2) dörtlüleri; tek yüzlü (açık) kenarda yüz2 = −1. Aynı kenarı ikiden çok yüz paylaşırsa
     * ilk ikisi alınır.
     */
    val edges: IntArray by lazy {
        val map = HashMap<Long, Int>(faces.size * 4)
        val list = ArrayList<Int>(faces.size * 8)
        for (f in faces.indices) {
            val face = faces[f]
            for (i in face.indices) {
                val a = face[i]; val b = face[(i + 1) % face.size]
                if (a == b) continue
                val key = if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()
                val at = map[key]
                if (at == null) {
                    map[key] = list.size
                    list += min(a, b); list += max(a, b); list += f; list += -1
                } else if (list[at + 3] == -1 && list[at + 2] != f) list[at + 3] = f
            }
        }
        list.toIntArray()
    }

    /** Ağ kapalı mı (her kenar tam iki yüzün): kapalı ağlarda arka yüzler çizilmez. */
    val closed: Boolean by lazy {
        val e = edges
        var i = 0
        var ok = e.isNotEmpty()
        while (i < e.size) { if (e[i + 3] < 0) { ok = false; break }; i += 4 }
        ok
    }

    /**
     * Yumuşak gölgelemede yüz köşelerinin normalleri: köşeye değen ve bu yüzle [creaseCos]'tan küçük açı yapan yüzlerin
     * normallerinin ortalaması (keskin kenarlar korunur). Yüz sırasıyla, her yüz için köşe başına 3 değer.
     */
    fun cornerNormals(creaseCos: Float = 0.7f): Array<FloatArray> {
        cache?.let { if (it.first == creaseCos) return it.second }
        val fn = faceNormals
        val vf = vertexFaces
        val out = Array(faces.size) { f ->
            val face = faces[f]
            val n = FloatArray(face.size * 3)
            val ax = fn[3 * f]; val ay = fn[3 * f + 1]; val az = fn[3 * f + 2]
            for (i in face.indices) {
                var sx = 0f; var sy = 0f; var sz = 0f
                for (g in vf[face[i]]) {
                    val bx = fn[3 * g]; val by = fn[3 * g + 1]; val bz = fn[3 * g + 2]
                    if (ax * bx + ay * by + az * bz >= creaseCos) { sx += bx; sy += by; sz += bz }
                }
                val l = sqrt(sx * sx + sy * sy + sz * sz)
                if (l < 1e-9f) { n[3 * i] = ax; n[3 * i + 1] = ay; n[3 * i + 2] = az }
                else { n[3 * i] = sx / l; n[3 * i + 1] = sy / l; n[3 * i + 2] = sz / l }
            }
            n
        }
        cache = creaseCos to out
        return out
    }

    @Volatile private var cache: Pair<Float, Array<FloatArray>>? = null

    /** Nesne uzayındaki kutu. */
    val bounds: Box3 by lazy {
        if (pos.isEmpty()) return@lazy Box3(V3.ZERO, V3.ZERO)
        var ax = Float.POSITIVE_INFINITY; var ay = Float.POSITIVE_INFINITY; var az = Float.POSITIVE_INFINITY
        var bx = Float.NEGATIVE_INFINITY; var by = Float.NEGATIVE_INFINITY; var bz = Float.NEGATIVE_INFINITY
        var i = 0
        while (i < pos.size) {
            ax = min(ax, pos[i]); ay = min(ay, pos[i + 1]); az = min(az, pos[i + 2])
            bx = max(bx, pos[i]); by = max(by, pos[i + 1]); bz = max(bz, pos[i + 2])
            i += 3
        }
        Box3(V3(ax, ay, az), V3(bx, by, bz))
    }

    // ---------------------------------------------------------------- düzenleme (yeni ağ döndürür)

    /** Verilen köşeleri [d] kadar taşır. */
    fun moveVertices(indices: Collection<Int>, d: V3): Mesh {
        val p = pos.copyOf()
        for (i in indices.toSet()) {
            if (i !in 0 until vertexCount) continue
            p[3 * i] += d.x; p[3 * i + 1] += d.y; p[3 * i + 2] += d.z
        }
        return Mesh(p, faces, uvs)
    }

    /** Yüzün köşelerini merkezine göre [s] katına getirir (paylaşılan köşeler: komşu yüzler de esner). */
    fun scaleFace(f: Int, s: Float): Mesh {
        if (f !in faces.indices) return this
        val c = faceCenter(f)
        val p = pos.copyOf()
        for (v in faces[f].toSet()) {
            p[3 * v] = c.x + (pos[3 * v] - c.x) * s
            p[3 * v + 1] = c.y + (pos[3 * v + 1] - c.y) * s
            p[3 * v + 2] = c.z + (pos[3 * v + 2] - c.z) * s
        }
        return Mesh(p, faces, uvs)
    }

    /**
     * Yüzü normali yönünde [distance] kadar dışarı çeker (uzatma): yüz yeni köşelerle yerinde kalır (aynı sıra numarası),
     * eski kenarları ile yeni kenarlar arasına yan yüzler eklenir (ağın sonuna). Eksi uzaklık içeri iter.
     */
    fun extrudeFace(f: Int, distance: Float): Mesh {
        if (f !in faces.indices) return this
        val face = faces[f]
        val n = faceNormal(f)
        val base = vertexCount
        val p = pos.copyOf(pos.size + face.size * 3)
        for (i in face.indices) {
            val v = face[i]
            p[3 * (base + i)] = pos[3 * v] + n.x * distance
            p[3 * (base + i) + 1] = pos[3 * v + 1] + n.y * distance
            p[3 * (base + i) + 2] = pos[3 * v + 2] + n.z * distance
        }
        val newFaces = ArrayList<IntArray>(faces.size + face.size)
        faces.forEachIndexed { i, fc -> newFaces += if (i == f) IntArray(face.size) { base + it } else fc }
        for (i in face.indices) {
            val j = (i + 1) % face.size
            newFaces += intArrayOf(face[i], face[j], base + j, base + i)
        }
        return Mesh(p, newFaces.toTypedArray())
    }

    /**
     * Yüzün içine küçük bir yüz açar (içe kaydırma): yeni köşeler merkeze doğru [amount] (0..1) oranında; iç yüz aynı sıra
     * numarasını alır, aradaki halka dörtgenleri ağın sonuna eklenir.
     */
    fun insetFace(f: Int, amount: Float): Mesh {
        if (f !in faces.indices) return this
        val t = amount.coerceIn(0.02f, 0.95f)
        val face = faces[f]
        val c = faceCenter(f)
        val base = vertexCount
        val p = pos.copyOf(pos.size + face.size * 3)
        for (i in face.indices) {
            val v = face[i]
            p[3 * (base + i)] = pos[3 * v] + (c.x - pos[3 * v]) * t
            p[3 * (base + i) + 1] = pos[3 * v + 1] + (c.y - pos[3 * v + 1]) * t
            p[3 * (base + i) + 2] = pos[3 * v + 2] + (c.z - pos[3 * v + 2]) * t
        }
        val newFaces = ArrayList<IntArray>(faces.size + face.size)
        faces.forEachIndexed { i, fc -> newFaces += if (i == f) IntArray(face.size) { base + it } else fc }
        for (i in face.indices) {
            val j = (i + 1) % face.size
            newFaces += intArrayOf(face[i], face[j], base + j, base + i)
        }
        return Mesh(p, newFaces.toTypedArray())
    }

    /** Yüzü siler (köşeler kalır; kullanılmayanlar [compact] ile atılır). */
    fun deleteFace(f: Int): Mesh {
        if (f !in faces.indices || faces.size <= 1) return this
        return Mesh(pos, faces.filterIndexed { i, _ -> i != f }.toTypedArray(), uvs?.filterIndexed { i, _ -> i != f }?.toTypedArray()).compact()
    }

    /** Hiçbir yüzün kullanmadığı köşeleri atar. */
    fun compact(): Mesh {
        val used = BooleanArray(vertexCount)
        faces.forEach { f -> f.forEach { used[it] = true } }
        if (used.all { it }) return this
        val map = IntArray(vertexCount) { -1 }
        var k = 0
        for (i in used.indices) if (used[i]) map[i] = k++
        val p = FloatArray(k * 3)
        for (i in used.indices) if (used[i]) { val j = map[i]; p[3 * j] = pos[3 * i]; p[3 * j + 1] = pos[3 * i + 1]; p[3 * j + 2] = pos[3 * i + 2] }
        return Mesh(p, Array(faces.size) { f -> IntArray(faces[f].size) { map[faces[f][it]] } }, uvs)
    }

    /** Ağın kopyası, her köşe [transform] ile dönüştürülmüş. */
    fun mapPositions(transform: (V3) -> V3): Mesh {
        val p = FloatArray(pos.size)
        for (i in 0 until vertexCount) {
            val v = transform(vertex(i))
            p[3 * i] = v.x; p[3 * i + 1] = v.y; p[3 * i + 2] = v.z
        }
        return Mesh(p, faces, uvs)
    }

    /** Ekranda seçilebilecek, düzlemsellikten en çok sapan yüzün sapması (testler ve tanılama için). */
    fun maxNonPlanarity(): Float {
        var worst = 0f
        for (f in faces.indices) {
            val n = faceNormal(f); val c = faceCenter(f)
            for (v in faces[f]) worst = max(worst, abs((vertex(v) - c) dot n))
        }
        return worst
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Mesh) return false
        if (!pos.contentEquals(other.pos) || faces.size != other.faces.size || !faces.indices.all { faces[it].contentEquals(other.faces[it]) }) return false
        val a = uvs; val b = other.uvs
        if (a == null || b == null) return a == null && b == null
        return a.size == b.size && a.indices.all { a[it].contentEquals(b[it]) }
    }

    override fun hashCode(): Int {
        var h = pos.contentHashCode()
        faces.forEach { h = h * 31 + it.contentHashCode() }
        return h
    }

    /** v24 aynı ağ, kendi doku koordinatları olmadan. */
    fun withoutUvs(): Mesh = if (uvs == null) this else Mesh(pos, faces)

    companion object {
        /** Birden çok ağın birleşimi (köşe numaraları kaydırılır). */
        fun concat(meshes: List<Mesh>): Mesh {
            val total = meshes.sumOf { it.pos.size }
            val p = FloatArray(total)
            val f = ArrayList<IntArray>(meshes.sumOf { it.faces.size })
            val keepUv = meshes.isNotEmpty() && meshes.all { it.uvs != null }
            val uv = ArrayList<FloatArray>()
            var off = 0
            meshes.forEach { m ->
                m.pos.copyInto(p, off * 3)
                m.faces.forEach { face -> f += IntArray(face.size) { face[it] + off } }
                if (keepUv) uv.addAll(m.uvs!!)
                off += m.vertexCount
            }
            return Mesh(p, f.toTypedArray(), if (keepUv) uv.toTypedArray() else null)
        }
    }
}
