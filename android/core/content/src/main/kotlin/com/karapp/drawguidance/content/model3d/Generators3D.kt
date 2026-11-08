package com.karapp.drawguidance.content.model3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v24 hazır şekiller için ağ üreticileri (dünya koordinatında, az ayrıntılı): düzgün çokyüzlüler, parametrik yüzeyler,
 * eğri boyunca boru, iki nokta arası silindir/koni, döner profil ve kutu.
 */
object Generators3D {

    private val GOLD = ((1 + sqrt(5.0)) / 2).toFloat()

    /** Ağın her köşesini ölçekleyip taşır. */
    fun place(mesh: Mesh, center: V3, scale: Float): Mesh = mesh.mapPositions { center + it * scale }

    fun place(mesh: Mesh, center: V3, scale: V3, rotationDeg: V3 = V3.ZERO): Mesh {
        val t = Transform3.of(center, rotationDeg, scale)
        return mesh.mapPositions { t.apply(it) }
    }

    /** Kutu: [min]–[max] köşeleri. */
    fun box(min: V3, max: V3): Mesh = place(Primitives.cube(), (min + max) * 0.5f, max - min)

    /** Küre (az ayrıntılı). */
    fun sphere(center: V3, r: Float, segments: Int = 14, rings: Int = 7): Mesh = place(Primitives.sphere(segments, rings), center, r * 2f)

    /** [a]'dan [b]'ye silindir ([r] yarıçap); uçları kapalı. */
    fun cylinder(a: V3, b: V3, r: Float, segments: Int = 10, rTop: Float = r): Mesh {
        val d = b - a
        val len = d.length
        if (len < 1e-6f) return Mesh(FloatArray(0), emptyArray())
        val base = Primitives.revolveFrustum(segments, r, rTop, 0f) // yükseklik 1, y −0,5..0,5
        val n = d * (1f / len)
        val x = frame(n).first
        // Sağ elli taban (x, n, x × n): yüzler dışa bakmaya devam eder
        val z = (x cross n).normalized()
        return base.mapPositions { p ->
            val t = p.y + 0.5f
            a + x * p.x + d * t + z * p.z
        }
    }

    /** [a]'dan [b]'ye koni (ok ucu). */
    fun cone(a: V3, b: V3, r: Float, segments: Int = 12): Mesh = cylinder(a, b, r, segments, 0f)

    /** [n] birim yönüne dik iki birim yön. */
    private fun frame(n: V3): Pair<V3, V3> {
        val helper = if (abs(n.y) < 0.9f) V3.UP else V3(1f, 0f, 0f)
        val x = (helper cross n).normalized()
        val z = (n cross x).normalized()
        return x to z
    }

    /** Ok: gövde silindir + uç koni. */
    fun arrow(a: V3, b: V3, r: Float): Mesh {
        val d = b - a
        val len = d.length
        val head = (len * 0.22f).coerceAtMost(r * 7f)
        val neck = a + d * ((len - head) / len)
        return Mesh.concat(listOf(cylinder(a, neck, r, 10), cone(neck, b, r * 2.4f, 12)))
    }

    /** Eğri boyunca boru ([points] dünya noktaları x, y, z…): paralel taşımalı çerçeve, [closed] ise halka. */
    fun tube(points: List<V3>, r: Float, sides: Int = 8, closed: Boolean = false): Mesh {
        val n = points.size
        if (n < 2) return Mesh(FloatArray(0), emptyArray())
        val tangents = List(n) { i ->
            val prev = if (i > 0) points[i - 1] else if (closed) points[n - 1] else points[i]
            val next = if (i < n - 1) points[i + 1] else if (closed) points[0] else points[i]
            (next - prev).normalized()
        }
        var (nx, _) = frame(tangents[0])
        val pos = FloatArray(n * sides * 3)
        for (i in 0 until n) {
            val t = tangents[i]
            // Paralel taşıma: önceki normal yeni teğete dik hale getirilir
            nx = (nx - t * (nx dot t)).normalized().let { if (it.length < 0.5f) frame(t).first else it }
            val bz = (t cross nx).normalized()
            for (s in 0 until sides) {
                val a = 2.0 * PI * s / sides
                val p = points[i] + nx * (r * cos(a).toFloat()) + bz * (r * sin(a).toFloat())
                val k = (i * sides + s) * 3
                pos[k] = p.x; pos[k + 1] = p.y; pos[k + 2] = p.z
            }
        }
        val faces = ArrayList<IntArray>()
        val segs = if (closed) n else n - 1
        for (i in 0 until segs) {
            val j = (i + 1) % n
            for (s in 0 until sides) {
                val s1 = (s + 1) % sides
                faces += intArrayOf(i * sides + s, i * sides + s1, j * sides + s1, j * sides + s)
            }
        }
        if (!closed) {
            faces += IntArray(sides) { sides - 1 - it }
            faces += IntArray(sides) { (n - 1) * sides + it }
        }
        return fixOrientation(Mesh(pos, faces.toTypedArray()))
    }

    /**
     * Parametrik yüzey: u, v ∈ [0, 1] için [f]; [uSeg] × [vSeg] dörtgen. [closeU]/[closeV] uçlar birleşik (dikişsiz).
     * İki yüzlü görünsün diye çizici açık ağların arka yüzünü de çizer.
     */
    fun surface(uSeg: Int, vSeg: Int, closeU: Boolean = false, closeV: Boolean = false, f: (Float, Float) -> V3): Mesh {
        val nu = if (closeU) uSeg else uSeg + 1
        val nv = if (closeV) vSeg else vSeg + 1
        val pos = FloatArray(nu * nv * 3)
        for (i in 0 until nu) for (j in 0 until nv) {
            val p = f(i / uSeg.toFloat(), j / vSeg.toFloat())
            val k = (i * nv + j) * 3
            pos[k] = p.x; pos[k + 1] = p.y; pos[k + 2] = p.z
        }
        val faces = ArrayList<IntArray>()
        for (i in 0 until uSeg) for (j in 0 until vSeg) {
            val i1 = if (closeU) (i + 1) % nu else i + 1
            val j1 = if (closeV) (j + 1) % nv else j + 1
            faces += intArrayOf(i * nv + j, i1 * nv + j, i1 * nv + j1, i * nv + j1)
        }
        return Mesh(pos, faces.toTypedArray())
    }

    /** Döner profil: (yarıçap, y) çiftleri aşağıdan yukarı (Primitives.revolve). */
    fun revolve(profile: List<Pair<Float, Float>>, segments: Int = 20, capBottom: Boolean = true, capTop: Boolean = true): Mesh =
        Primitives.revolve(profile, segments, capBottom, capTop)

    /** Kapalı ağda yüzler dışa bakmıyorsa (hacim eksi) çevirir. */
    fun fixOrientation(mesh: Mesh): Mesh {
        if (!mesh.closed) return mesh
        var vol = 0.0
        val p = mesh.pos
        for (f in 0 until mesh.faceCount) {
            val face = mesh.faces[f]; val tris = mesh.triangles[f]
            var k = 0
            while (k + 2 < tris.size) {
                val a = face[tris[k]] * 3; val b = face[tris[k + 1]] * 3; val c = face[tris[k + 2]] * 3
                vol += (p[a] * (p[b + 1] * p[c + 2] - p[b + 2] * p[c + 1]) - p[a + 1] * (p[b] * p[c + 2] - p[b + 2] * p[c]) + p[a + 2] * (p[b] * p[c + 1] - p[b + 1] * p[c])).toDouble()
                k += 3
            }
        }
        return if (vol >= 0) mesh else Mesh(mesh.pos, Array(mesh.faceCount) { mesh.faces[it].reversedArray() })
    }

    // ------------------------------------------------------------------------------------------ düzgün çokyüzlüler

    private fun polyhedron(verts: List<V3>, faces: List<IntArray>, radius: Float): Mesh {
        val pos = FloatArray(verts.size * 3)
        verts.forEachIndexed { i, v -> val u = v.normalized() * radius; pos[3 * i] = u.x; pos[3 * i + 1] = u.y; pos[3 * i + 2] = u.z }
        return fixOrientation(Mesh(pos, faces.toTypedArray()).let { m -> orientFacesOutward(m) })
    }

    /** Her yüzü merkezden dışa baksın diye düzeltir (dışbükey çokyüzlülerde). */
    private fun orientFacesOutward(m: Mesh): Mesh = Mesh(m.pos, Array(m.faceCount) { f ->
        val c = m.faceCenter(f)
        if ((m.faceNormal(f) dot c) < 0f) m.faces[f].reversedArray() else m.faces[f]
    })

    fun tetrahedron(radius: Float = 0.5f): Mesh = polyhedron(
        listOf(V3(1f, 1f, 1f), V3(1f, -1f, -1f), V3(-1f, 1f, -1f), V3(-1f, -1f, 1f)),
        listOf(intArrayOf(0, 1, 2), intArrayOf(0, 3, 1), intArrayOf(0, 2, 3), intArrayOf(1, 3, 2)), radius,
    )

    fun octahedron(radius: Float = 0.5f): Mesh = polyhedron(
        listOf(V3(1f, 0f, 0f), V3(-1f, 0f, 0f), V3(0f, 1f, 0f), V3(0f, -1f, 0f), V3(0f, 0f, 1f), V3(0f, 0f, -1f)),
        listOf(
            intArrayOf(0, 2, 4), intArrayOf(4, 2, 1), intArrayOf(1, 2, 5), intArrayOf(5, 2, 0),
            intArrayOf(0, 4, 3), intArrayOf(4, 1, 3), intArrayOf(1, 5, 3), intArrayOf(5, 0, 3),
        ), radius,
    )

    fun icosahedron(radius: Float = 0.5f): Mesh {
        val g = GOLD
        val v = listOf(
            V3(-1f, g, 0f), V3(1f, g, 0f), V3(-1f, -g, 0f), V3(1f, -g, 0f),
            V3(0f, -1f, g), V3(0f, 1f, g), V3(0f, -1f, -g), V3(0f, 1f, -g),
            V3(g, 0f, -1f), V3(g, 0f, 1f), V3(-g, 0f, -1f), V3(-g, 0f, 1f),
        )
        val f = listOf(
            intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7), intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
            intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
            intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
            intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10), intArrayOf(8, 6, 7), intArrayOf(9, 8, 1),
        )
        return polyhedron(v, f, radius)
    }

    /** Onikiyüzlü: yirmiyüzlünün ikilisi (yüz merkezleri köşe, beşgen yüzler). */
    fun dodecahedron(radius: Float = 0.5f): Mesh {
        val ico = icosahedron(1f)
        val centers = List(ico.faceCount) { ico.faceCenter(it).normalized() }
        val faces = ArrayList<IntArray>()
        for (v in 0 until ico.vertexCount) {
            val around = ico.vertexFaces[v].toMutableList()
            // Köşe çevresindeki yüzleri açıya göre sırala
            val axis = ico.vertex(v).normalized()
            val (x, z) = frame(axis)
            around.sortBy { f -> val c = centers[f]; kotlin.math.atan2(c dot z, c dot x) }
            faces += around.toIntArray()
        }
        return polyhedron(centers, faces, radius)
    }

    /** Tek bir ağ (birden çok ağın birleşimi). */
    fun merge(vararg meshes: Mesh): Mesh = Mesh.concat(meshes.filter { it.faceCount > 0 })

    /** Çember üzerindeki noktalar (X–Z düzleminde, [y] yükseklikte). */
    fun circle(center: V3, r: Float, n: Int = 32, tiltX: Float = 0f): List<V3> = List(n) { i ->
        val a = 2.0 * PI * i / n
        val x = (r * cos(a)).toFloat(); val z = (r * sin(a)).toFloat()
        val ct = cos(Math.toRadians(tiltX.toDouble())).toFloat(); val st = sin(Math.toRadians(tiltX.toDouble())).toFloat()
        center + V3(x, -z * st, z * ct)
    }
}
