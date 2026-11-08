package com.karapp.drawguidance.content.model3d

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Üçgen ağ (içe aktarma için): [pos] köşeler (x, y, z…), [tris] üçgenlerin köşe sıraları, [uv] her üçgen köşesinin
 * doku koordinatı (üçgen × 3 × 2; yoksa null).
 */
class TriMesh(val pos: FloatArray, val tris: IntArray, val uv: FloatArray?) {
    val vertexCount: Int get() = pos.size / 3
    val triangleCount: Int get() = tris.size / 3
}

/**
 * v24 içe aktarılan modellerin ayrıntısını düşürme: aynı yerdeki köşeler birleştirilir, çok büyük ağlar önce ızgarada
 * kümelenir ([cluster]), ardından ikinci dereceden hata ölçüsüyle (Garland–Heckbert QEM) en az şekil bozan kenarlar
 * birer birer büzülür: yüzey düzlemlerinden uzaklık en az artacak noktaya taşınır; açık kenarlar (sınırlar) ek
 * düzlemlerle korunur, yüzü ters çeviren büzülmeler yapılmaz. Üçgen köşelerinin doku koordinatları üçgenle kalır.
 */
object MeshSimplifier {

    /** [mesh]'i en çok [target] üçgene indirir (zaten azsa yalnız köşeleri birleştirir). */
    fun simplify(mesh: TriMesh, target: Int): TriMesh {
        var m = weld(mesh)
        if (m.triangleCount <= target) return m
        // Çok büyük ağ: önce hızlı kümeleme (QEM'e ~6 kat hedef kadar üçgen kalsın)
        if (m.triangleCount > max(60_000, target * 8)) m = cluster(m, max(target * 6, 20_000))
        if (m.triangleCount <= target) return m
        return Qem(m).run(target)
    }

    /** Aynı (çok yakın) konumdaki köşeleri birleştirir; bozulan (sıfır alanlı) üçgenleri atar. */
    fun weld(mesh: TriMesh): TriMesh {
        val n = mesh.vertexCount
        if (n == 0) return mesh
        var ax = Float.POSITIVE_INFINITY; var ay = Float.POSITIVE_INFINITY; var az = Float.POSITIVE_INFINITY
        var bx = Float.NEGATIVE_INFINITY; var by = Float.NEGATIVE_INFINITY; var bz = Float.NEGATIVE_INFINITY
        val p = mesh.pos
        for (i in 0 until n) { ax = min(ax, p[3 * i]); ay = min(ay, p[3 * i + 1]); az = min(az, p[3 * i + 2]); bx = max(bx, p[3 * i]); by = max(by, p[3 * i + 1]); bz = max(bz, p[3 * i + 2]) }
        val ext = max(bx - ax, max(by - ay, bz - az)).coerceAtLeast(1e-9f)
        val q = ext * 1e-5f
        val map = IntArray(n)
        val keys = HashMap<Long, Int>(n * 2)
        val outPos = FloatArray(n * 3)
        var k = 0
        for (i in 0 until n) {
            val qx = ((p[3 * i] - ax) / q).toLong(); val qy = ((p[3 * i + 1] - ay) / q).toLong(); val qz = ((p[3 * i + 2] - az) / q).toLong()
            val key = (qx * 73856093L) xor (qy * 19349663L) xor (qz * 83492791L) xor (qx shl 42) xor (qy shl 21) xor qz
            val existing = keys[key]
            if (existing != null && abs(outPos[3 * existing] - p[3 * i]) <= q && abs(outPos[3 * existing + 1] - p[3 * i + 1]) <= q && abs(outPos[3 * existing + 2] - p[3 * i + 2]) <= q) {
                map[i] = existing
            } else {
                keys[key] = k
                outPos[3 * k] = p[3 * i]; outPos[3 * k + 1] = p[3 * i + 1]; outPos[3 * k + 2] = p[3 * i + 2]
                map[i] = k++
            }
        }
        return rebuild(outPos.copyOf(k * 3), mesh.tris.map { map[it] }.toIntArray(), mesh.uv)
    }

    /** Üçgen listesini yeniden kurar: bozulanları atar, kullanılmayan köşeleri sıkıştırır. */
    private fun rebuild(pos: FloatArray, tris: IntArray, uv: FloatArray?): TriMesh {
        val keepTri = ArrayList<Int>(tris.size / 3)
        for (t in 0 until tris.size / 3) {
            val a = tris[3 * t]; val b = tris[3 * t + 1]; val c = tris[3 * t + 2]
            if (a == b || b == c || a == c) continue
            keepTri += t
        }
        val n = pos.size / 3
        val used = IntArray(n) { -1 }
        var k = 0
        val outTris = IntArray(keepTri.size * 3)
        val outUv = uv?.let { FloatArray(keepTri.size * 6) }
        keepTri.forEachIndexed { i, t ->
            for (c in 0 until 3) {
                val v = tris[3 * t + c]
                if (used[v] < 0) used[v] = k++
                outTris[3 * i + c] = used[v]
                if (outUv != null) { outUv[6 * i + 2 * c] = uv[6 * t + 2 * c]; outUv[6 * i + 2 * c + 1] = uv[6 * t + 2 * c + 1] }
            }
        }
        val outPos = FloatArray(k * 3)
        for (v in 0 until n) if (used[v] >= 0) { val j = used[v]; outPos[3 * j] = pos[3 * v]; outPos[3 * j + 1] = pos[3 * v + 1]; outPos[3 * j + 2] = pos[3 * v + 2] }
        return TriMesh(outPos, outTris, outUv)
    }

    /** Izgarada kümeleme: aynı hücredeki köşeler ortalamalarında birleşir (çok büyük ağlar için hızlı ön adım). */
    fun cluster(mesh: TriMesh, target: Int): TriMesh {
        val p = mesh.pos
        val n = mesh.vertexCount
        var ax = Float.POSITIVE_INFINITY; var ay = Float.POSITIVE_INFINITY; var az = Float.POSITIVE_INFINITY
        var bx = Float.NEGATIVE_INFINITY; var by = Float.NEGATIVE_INFINITY; var bz = Float.NEGATIVE_INFINITY
        for (i in 0 until n) { ax = min(ax, p[3 * i]); ay = min(ay, p[3 * i + 1]); az = min(az, p[3 * i + 2]); bx = max(bx, p[3 * i]); by = max(by, p[3 * i + 1]); bz = max(bz, p[3 * i + 2]) }
        // Hücre sayısı: hedef üçgen ≈ 2 × hücre yüzeyi; birkaç denemeyle ayarlanır
        var cells = sqrt(target / 2.0).toInt().coerceIn(8, 1024)
        var result = mesh
        repeat(4) {
            val ext = max(bx - ax, max(by - ay, bz - az)).coerceAtLeast(1e-9f)
            val size = ext / cells
            val map = IntArray(n)
            val ids = HashMap<Long, Int>()
            val sum = ArrayList<FloatArray>()
            for (i in 0 until n) {
                val cx = ((p[3 * i] - ax) / size).toLong().coerceAtMost(cells.toLong()); val cy = ((p[3 * i + 1] - ay) / size).toLong().coerceAtMost(cells.toLong()); val cz = ((p[3 * i + 2] - az) / size).toLong().coerceAtMost(cells.toLong())
                val key = (cx * 1_000_003L + cy) * 1_000_033L + cz
                val id = ids.getOrPut(key) { sum.add(FloatArray(4)); sum.size - 1 }
                val s = sum[id]; s[0] += p[3 * i]; s[1] += p[3 * i + 1]; s[2] += p[3 * i + 2]; s[3] += 1f
                map[i] = id
            }
            val pos = FloatArray(sum.size * 3)
            sum.forEachIndexed { i, s -> pos[3 * i] = s[0] / s[3]; pos[3 * i + 1] = s[1] / s[3]; pos[3 * i + 2] = s[2] / s[3] }
            result = rebuild(pos, mesh.tris.map { map[it] }.toIntArray(), mesh.uv)
            if (result.triangleCount <= target * 1.3 || cells <= 8) return result
            cells = (cells * 0.7).toInt().coerceAtLeast(8)
        }
        return result
    }

    /** QEM büzülmesi. */
    private class Qem(m: TriMesh) {
        val pos = m.pos.copyOf()
        val tris = m.tris.copyOf()
        val uv = m.uv?.copyOf()
        val nv = m.vertexCount
        val nt = m.triangleCount
        val q = DoubleArray(nv * 10)
        val alive = BooleanArray(nt) { true }
        val vAlive = BooleanArray(nv) { true }
        val version = IntArray(nv)
        val vtris: Array<MutableList<Int>> = Array(nv) { ArrayList(6) }
        var liveTris = nt
        private val tmpN = DoubleArray(3)
        private val qq = DoubleArray(10)

        init {
            for (t in 0 until nt) for (c in 0 until 3) vtris[tris[3 * t + c]].add(t)
            for (t in 0 until nt) addPlane(t)
            addBoundaryPlanes()
        }

        fun normal(t: Int, out: DoubleArray): Double {
            val a = tris[3 * t]; val b = tris[3 * t + 1]; val c = tris[3 * t + 2]
            val e1x = (pos[3 * b] - pos[3 * a]).toDouble(); val e1y = (pos[3 * b + 1] - pos[3 * a + 1]).toDouble(); val e1z = (pos[3 * b + 2] - pos[3 * a + 2]).toDouble()
            val e2x = (pos[3 * c] - pos[3 * a]).toDouble(); val e2y = (pos[3 * c + 1] - pos[3 * a + 1]).toDouble(); val e2z = (pos[3 * c + 2] - pos[3 * a + 2]).toDouble()
            val nx = e1y * e2z - e1z * e2y; val ny = e1z * e2x - e1x * e2z; val nz = e1x * e2y - e1y * e2x
            val l = sqrt(nx * nx + ny * ny + nz * nz)
            if (l < 1e-20) { out[0] = 0.0; out[1] = 0.0; out[2] = 0.0; return 0.0 }
            out[0] = nx / l; out[1] = ny / l; out[2] = nz / l
            return l * 0.5
        }

        fun addPlane(t: Int) {
            val area = normal(t, tmpN)
            if (area <= 0.0) return
            val a = tris[3 * t]
            val d = -(tmpN[0] * pos[3 * a] + tmpN[1] * pos[3 * a + 1] + tmpN[2] * pos[3 * a + 2])
            for (c in 0 until 3) addQuadric(tris[3 * t + c], tmpN[0], tmpN[1], tmpN[2], d, area)
        }

        fun addQuadric(v: Int, a: Double, b: Double, c: Double, d: Double, w: Double) {
            val o = v * 10
            q[o] += w * a * a; q[o + 1] += w * a * b; q[o + 2] += w * a * c; q[o + 3] += w * a * d
            q[o + 4] += w * b * b; q[o + 5] += w * b * c; q[o + 6] += w * b * d
            q[o + 7] += w * c * c; q[o + 8] += w * c * d
            q[o + 9] += w * d * d
        }

        fun addBoundaryPlanes() {
            val count = HashMap<Long, Int>()
            val owner = HashMap<Long, Int>()
            for (t in 0 until nt) for (c in 0 until 3) {
                val a = tris[3 * t + c]; val b = tris[3 * t + (c + 1) % 3]
                val key = if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()
                count[key] = (count[key] ?: 0) + 1
                owner[key] = t
            }
            for ((key, n) in count) {
                if (n != 1) continue
                val a = (key ushr 32).toInt(); val b = (key and 0xFFFFFFFFL).toInt()
                val t = owner[key] ?: continue
                normal(t, tmpN)
                val ex = (pos[3 * b] - pos[3 * a]).toDouble(); val ey = (pos[3 * b + 1] - pos[3 * a + 1]).toDouble(); val ez = (pos[3 * b + 2] - pos[3 * a + 2]).toDouble()
                // Kenardan geçen, yüze dik düzlem
                var px = ey * tmpN[2] - ez * tmpN[1]; var py = ez * tmpN[0] - ex * tmpN[2]; var pz = ex * tmpN[1] - ey * tmpN[0]
                val l = sqrt(px * px + py * py + pz * pz)
                if (l < 1e-20) continue
                px /= l; py /= l; pz /= l
                val d = -(px * pos[3 * a] + py * pos[3 * a + 1] + pz * pos[3 * a + 2])
                val w = (ex * ex + ey * ey + ez * ez) * 10.0
                addQuadric(a, px, py, pz, d, w); addQuadric(b, px, py, pz, d, w)
            }
        }

        fun error(qq: DoubleArray, x: Double, y: Double, z: Double): Double =
            qq[0] * x * x + 2 * qq[1] * x * y + 2 * qq[2] * x * z + 2 * qq[3] * x +
                qq[4] * y * y + 2 * qq[5] * y * z + 2 * qq[6] * y +
                qq[7] * z * z + 2 * qq[8] * z + qq[9]

        /** Kenarın büzülme maliyeti ve hedef nokta. */
        fun cost(a: Int, b: Int, out: DoubleArray): Double {
            for (i in 0 until 10) qq[i] = q[a * 10 + i] + q[b * 10 + i]
            // 3×3 sistem: A x = −b
            val a00 = qq[0]; val a01 = qq[1]; val a02 = qq[2]; val a11 = qq[4]; val a12 = qq[5]; val a22 = qq[7]
            val det = a00 * (a11 * a22 - a12 * a12) - a01 * (a01 * a22 - a12 * a02) + a02 * (a01 * a12 - a11 * a02)
            val scale = abs(a00) + abs(a11) + abs(a22)
            if (abs(det) > 1e-12 * scale * scale * scale && scale > 0) {
                val b0 = -qq[3]; val b1 = -qq[6]; val b2 = -qq[8]
                val x = (b0 * (a11 * a22 - a12 * a12) - a01 * (b1 * a22 - a12 * b2) + a02 * (b1 * a12 - a11 * b2)) / det
                val y = (a00 * (b1 * a22 - a12 * b2) - b0 * (a01 * a22 - a12 * a02) + a02 * (a01 * b2 - b1 * a02)) / det
                val z = (a00 * (a11 * b2 - b1 * a12) - a01 * (a01 * b2 - b1 * a02) + b0 * (a01 * a12 - a11 * a02)) / det
                // Çözüm kenardan çok uzaksa (kötü koşullu) uçlardan seçilir
                val mx = (pos[3 * a] + pos[3 * b]) * 0.5; val my = (pos[3 * a + 1] + pos[3 * b + 1]) * 0.5; val mz = (pos[3 * a + 2] + pos[3 * b + 2]) * 0.5
                val ex = (pos[3 * a] - pos[3 * b]).toDouble(); val ey = (pos[3 * a + 1] - pos[3 * b + 1]).toDouble(); val ez = (pos[3 * a + 2] - pos[3 * b + 2]).toDouble()
                val len2 = ex * ex + ey * ey + ez * ez
                val dx = x - mx; val dy = y - my; val dz = z - mz
                if (dx * dx + dy * dy + dz * dz <= len2 * 4.0) {
                    out[0] = x; out[1] = y; out[2] = z
                    return error(qq, x, y, z)
                }
            }
            var best = Double.MAX_VALUE
            for (k in 0..2) {
                val t = k / 2.0
                val x = pos[3 * a] + (pos[3 * b] - pos[3 * a]) * t; val y = pos[3 * a + 1] + (pos[3 * b + 1] - pos[3 * a + 1]) * t; val z = pos[3 * a + 2] + (pos[3 * b + 2] - pos[3 * a + 2]) * t
                val e = error(qq, x, y, z)
                if (e < best) { best = e; out[0] = x; out[1] = y; out[2] = z }
            }
            return best
        }

        /** [v]'yi [p]'ye taşımak çevresindeki (ve [other]'ı içermeyen) üçgenleri ters çeviriyor mu. */
        fun flips(v: Int, other: Int, px: Double, py: Double, pz: Double): Boolean {
            for (t in vtris[v]) {
                if (!alive[t]) continue
                val a = tris[3 * t]; val b = tris[3 * t + 1]; val c = tris[3 * t + 2]
                if (a == other || b == other || c == other) continue
                normal(t, tmpN)
                val n0x = tmpN[0]; val n0y = tmpN[1]; val n0z = tmpN[2]
                fun cx(i: Int) = if (i == v) px else pos[3 * i].toDouble()
                fun cy(i: Int) = if (i == v) py else pos[3 * i + 1].toDouble()
                fun cz(i: Int) = if (i == v) pz else pos[3 * i + 2].toDouble()
                val e1x = cx(b) - cx(a); val e1y = cy(b) - cy(a); val e1z = cz(b) - cz(a)
                val e2x = cx(c) - cx(a); val e2y = cy(c) - cy(a); val e2z = cz(c) - cz(a)
                val nx = e1y * e2z - e1z * e2y; val ny = e1z * e2x - e1x * e2z; val nz = e1x * e2y - e1y * e2x
                val l = sqrt(nx * nx + ny * ny + nz * nz)
                if (l < 1e-20) return true
                if ((nx * n0x + ny * n0y + nz * n0z) / l < 0.2) return true
            }
            return false
        }

        fun run(target: Int): TriMesh {
            val heap = EdgeHeap()
            val out = DoubleArray(3)
            val seen = HashSet<Long>()
            for (t in 0 until nt) for (c in 0 until 3) {
                val a = tris[3 * t + c]; val b = tris[3 * t + (c + 1) % 3]
                val key = if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()
                if (!seen.add(key)) continue
                heap.push(cost(a, b, out), a, b, version[a], version[b])
            }
            while (liveTris > target && heap.size > 0) {
                val e = heap.pop()
                val a = e.a; val b = e.b
                if (!vAlive[a] || !vAlive[b] || version[a] != e.va || version[b] != e.vb) continue
                val c = cost(a, b, out)
                if (flips(a, b, out[0], out[1], out[2]) || flips(b, a, out[0], out[1], out[2])) continue
                // b → a
                pos[3 * a] = out[0].toFloat(); pos[3 * a + 1] = out[1].toFloat(); pos[3 * a + 2] = out[2].toFloat()
                for (i in 0 until 10) q[a * 10 + i] += q[b * 10 + i]
                for (t in vtris[b]) {
                    if (!alive[t]) continue
                    val hasA = tris[3 * t] == a || tris[3 * t + 1] == a || tris[3 * t + 2] == a
                    if (hasA) { alive[t] = false; liveTris--; continue }
                    for (k in 0 until 3) if (tris[3 * t + k] == b) tris[3 * t + k] = a
                    vtris[a].add(t)
                }
                vAlive[b] = false
                vtris[b].clear()
                vtris[a].removeAll { !alive[it] }
                version[a]++
                // Komşu kenarların maliyeti yenilenir
                val neighbors = HashSet<Int>()
                for (t in vtris[a]) for (k in 0 until 3) { val v = tris[3 * t + k]; if (v != a) neighbors += v }
                for (nb in neighbors) heap.push(cost(a, nb, out), a, nb, version[a], version[nb])
                if (c.isNaN()) break
            }
            val keep = ArrayList<Int>()
            for (t in 0 until nt) if (alive[t]) keep += t
            val outTris = IntArray(keep.size * 3)
            val outUv = uv?.let { FloatArray(keep.size * 6) }
            keep.forEachIndexed { i, t ->
                for (k in 0 until 3) {
                    outTris[3 * i + k] = tris[3 * t + k]
                    if (outUv != null) { outUv[6 * i + 2 * k] = uv[6 * t + 2 * k]; outUv[6 * i + 2 * k + 1] = uv[6 * t + 2 * k + 1] }
                }
            }
            return rebuild(pos, outTris, outUv)
        }
    }

    private class EdgeEntry(val cost: Double, val a: Int, val b: Int, val va: Int, val vb: Int)

    /** En küçük maliyet önde (ikili yığın). */
    private class EdgeHeap {
        private var items = arrayOfNulls<EdgeEntry>(1024)
        var size = 0
            private set

        fun push(cost: Double, a: Int, b: Int, va: Int, vb: Int) {
            if (size == items.size) items = items.copyOf(size * 2)
            var i = size++
            val e = EdgeEntry(cost, a, b, va, vb)
            while (i > 0) {
                val p = (i - 1) / 2
                val pe = items[p]!!
                if (pe.cost <= cost) break
                items[i] = pe; i = p
            }
            items[i] = e
        }

        fun pop(): EdgeEntry {
            val top = items[0]!!
            val last = items[--size]!!
            items[size] = null
            if (size > 0) {
                var i = 0
                while (true) {
                    val l = 2 * i + 1; val r = l + 1
                    if (l >= size) break
                    val c = if (r < size && items[r]!!.cost < items[l]!!.cost) r else l
                    if (items[c]!!.cost >= last.cost) break
                    items[i] = items[c]; i = c
                }
                items[i] = last
            }
            return top
        }
    }
}
