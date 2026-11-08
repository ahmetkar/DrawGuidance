package com.karapp.drawguidance.content.model3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Hazır cisimlerin ağları. Hepsi birim kutuya sığar (genişlik, yükseklik ve derinlik en çok 1), merkezleri başlangıçtadır.
 * Yüzler dışarıdan bakınca saat yönünün tersine dizilir (normal dışarı bakar).
 */
object Primitives {

    /**
     * Cismin varsayılan ayarları (dilim ve halka sayısı). v24 düzenleme her cihazda hızlı olsun diye ayrıntı az tutulur
     * (küre 20×10 ≈ 360 üçgen); eğri yüzeyler yumuşak gölgelenir, kenarları yine düzgün görünür.
     */
    fun defaultSpec(kind: PrimitiveKind): PrimitiveSpec = when (kind) {
        PrimitiveKind.CUBE -> PrimitiveSpec(kind, 4, 1)
        PrimitiveKind.SPHERE -> PrimitiveSpec(kind, 20, 10)
        PrimitiveKind.CYLINDER -> PrimitiveSpec(kind, 24, 1)
        PrimitiveKind.CONE -> PrimitiveSpec(kind, 24, 1, top = 0f)
        PrimitiveKind.PYRAMID -> PrimitiveSpec(kind, 4, 1, top = 0f)
        PrimitiveKind.PRISM3 -> PrimitiveSpec(kind, 3, 1)
        PrimitiveKind.PRISM6 -> PrimitiveSpec(kind, 6, 1)
        PrimitiveKind.TORUS -> PrimitiveSpec(kind, 24, 10, thickness = 0.3f)
        PrimitiveKind.PLANE -> PrimitiveSpec(kind, 4, 4)
        PrimitiveKind.DOME -> PrimitiveSpec(kind, 20, 6)
        PrimitiveKind.CAPSULE -> PrimitiveSpec(kind, 16, 4)
        PrimitiveKind.TUBE -> PrimitiveSpec(kind, 24, 1, thickness = 0.3f)
        PrimitiveKind.WEDGE -> PrimitiveSpec(kind, 4, 1)
        PrimitiveKind.STAR -> PrimitiveSpec(kind, 5, 1, thickness = 0.4f)
    }

    /** v24 düzenleyicide dilim/halka ayarının üst sınırı (ayrıntı sınırı). */
    const val MAX_SEGMENTS = 48
    const val MAX_RINGS = 24

    /** Ayarlara göre ağ. */
    fun build(spec: PrimitiveSpec): Mesh = when (spec.kind) {
        PrimitiveKind.CUBE -> cube()
        PrimitiveKind.SPHERE -> sphere(spec.segments.coerceIn(6, 96), spec.rings.coerceIn(3, 64))
        PrimitiveKind.CYLINDER -> revolveFrustum(spec.segments.coerceIn(3, 128), 0.5f, 0.5f * spec.top.coerceIn(0f, 2f), 0f)
        PrimitiveKind.CONE -> revolveFrustum(spec.segments.coerceIn(3, 128), 0.5f, 0.5f * spec.top.coerceIn(0f, 2f), 0f)
        PrimitiveKind.PYRAMID -> revolveFrustum(spec.segments.coerceIn(3, 16), 0.5f * SQRT2, 0.5f * SQRT2 * spec.top.coerceIn(0f, 2f), 45f)
        PrimitiveKind.PRISM3 -> revolveFrustum(3, 0.5f, 0.5f * spec.top.coerceIn(0f, 2f), 0f)
        PrimitiveKind.PRISM6 -> revolveFrustum(6, 0.5f, 0.5f * spec.top.coerceIn(0f, 2f), 0f)
        PrimitiveKind.TORUS -> torus(spec.segments.coerceIn(6, 96), spec.rings.coerceIn(3, 48), spec.thickness.coerceIn(0.05f, 0.95f))
        PrimitiveKind.PLANE -> plane(spec.segments.coerceIn(1, 32))
        PrimitiveKind.DOME -> dome(spec.segments.coerceIn(6, 96), spec.rings.coerceIn(2, 32))
        PrimitiveKind.CAPSULE -> capsule(spec.segments.coerceIn(6, 96), spec.rings.coerceIn(2, 24))
        PrimitiveKind.TUBE -> tube(spec.segments.coerceIn(3, 128), spec.thickness.coerceIn(0.05f, 0.95f))
        PrimitiveKind.WEDGE -> wedge()
        PrimitiveKind.STAR -> star(spec.segments.coerceIn(3, 12), spec.thickness.coerceIn(0.1f, 0.9f))
    }

    private const val SQRT2 = 1.41421356f

    fun cube(): Mesh {
        val h = 0.5f
        val p = floatArrayOf(
            -h, -h, -h, h, -h, -h, h, h, -h, -h, h, -h,
            -h, -h, h, h, -h, h, h, h, h, -h, h, h,
        )
        val f = arrayOf(
            intArrayOf(4, 5, 6, 7), intArrayOf(1, 0, 3, 2), intArrayOf(5, 1, 2, 6),
            intArrayOf(0, 4, 7, 3), intArrayOf(7, 6, 2, 3), intArrayOf(0, 1, 5, 4),
        )
        return Mesh(p, f)
    }

    /**
     * Dönel yüzey: [profile] (yarıçap, y) çiftleri aşağıdan yukarı; yarıçapı 0 olan uçlar tek köşedir. [capBottom] ve
     * [capTop] açık uçları kapatır. [offsetDeg] ilk dilimin açısı.
     */
    fun revolve(profile: List<Pair<Float, Float>>, segments: Int, capBottom: Boolean, capTop: Boolean, offsetDeg: Float = 0f): Mesh {
        val pos = ArrayList<Float>()
        val rings = ArrayList<IntArray>()
        fun add(x: Float, y: Float, z: Float): Int { pos += x; pos += y; pos += z; return pos.size / 3 - 1 }
        val off = Math.toRadians(offsetDeg.toDouble())
        for ((r, y) in profile) {
            if (r <= 1e-6f) {
                val v = add(0f, y, 0f)
                rings += IntArray(segments) { v }
            } else {
                rings += IntArray(segments) { s ->
                    val a = off + 2.0 * PI * s / segments
                    add((r * sin(a)).toFloat(), y, (r * cos(a)).toFloat())
                }
            }
        }
        val faces = ArrayList<IntArray>()
        for (k in 0 until rings.size - 1) {
            val a = rings[k]; val b = rings[k + 1]
            for (s in 0 until segments) {
                val t = (s + 1) % segments
                when {
                    a[s] == a[t] && b[s] == b[t] -> Unit
                    a[s] == a[t] -> faces += intArrayOf(a[s], b[t], b[s])
                    b[s] == b[t] -> faces += intArrayOf(a[s], a[t], b[s])
                    else -> faces += intArrayOf(a[s], a[t], b[t], b[s])
                }
            }
        }
        val first = rings.first(); val last = rings.last()
        if (capBottom && first[0] != first[1 % segments]) faces += IntArray(segments) { first[segments - 1 - it] }
        if (capTop && last[0] != last[1 % segments]) faces += IntArray(segments) { last[it] }
        return Mesh(pos.toFloatArray(), faces.toTypedArray())
    }

    /** Kesik koni / silindir / koni / prizma / piramit (yükseklik 1). */
    fun revolveFrustum(segments: Int, bottom: Float, top: Float, offsetDeg: Float): Mesh =
        revolve(listOf(bottom to -0.5f, top to 0.5f), segments, capBottom = true, capTop = true, offsetDeg = offsetDeg)

    fun sphere(segments: Int, rings: Int): Mesh {
        val profile = (0..rings).map { k ->
            val phi = PI * k / rings
            val r = if (k == 0 || k == rings) 0f else (0.5 * sin(phi)).toFloat()
            r to (-0.5 * cos(phi)).toFloat()
        }
        return revolve(profile, segments, capBottom = false, capTop = false)
    }

    /** Yarım küre (kubbe), tabanı kapalı; yükseklik 0,5, kutunun ortasına göre ortalı. */
    fun dome(segments: Int, rings: Int): Mesh {
        val profile = (0..rings).map { k ->
            val phi = PI / 2 * k / rings
            val r = if (k == rings) 0f else (0.5 * cos(phi)).toFloat()
            r to (0.5 * sin(phi) - 0.25).toFloat()
        }
        return revolve(profile, segments, capBottom = true, capTop = false)
    }

    /** Hap: yarıçap 0,25, toplam yükseklik 1. */
    fun capsule(segments: Int, rings: Int): Mesh {
        val r = 0.25f
        val profile = ArrayList<Pair<Float, Float>>()
        for (k in 0..rings) {
            val phi = PI / 2 * k / rings
            val rr = if (k == 0) 0f else (r * sin(phi)).toFloat()
            profile += rr to (-0.5f + r - (r * cos(phi)).toFloat())
        }
        for (k in 0..rings) {
            val phi = PI / 2 * k / rings
            val rr = if (k == rings) 0f else (r * cos(phi)).toFloat()
            profile += rr to (0.5f - r + (r * sin(phi)).toFloat())
        }
        return revolve(profile, segments, capBottom = false, capTop = false)
    }

    /** Simit: dış yarıçap 0,5; boru yarıçapı 0,05 + 0,2·[thickness]. */
    fun torus(segments: Int, rings: Int, thickness: Float): Mesh {
        val tube = max(0.02f, 0.05f + 0.2f * thickness)
        val big = 0.5f - tube
        val pos = FloatArray(segments * rings * 3)
        for (u in 0 until segments) {
            val a = 2.0 * PI * u / segments
            for (v in 0 until rings) {
                val b = 2.0 * PI * v / rings
                val i = (u * rings + v) * 3
                val rr = big + tube * cos(b)
                pos[i] = (rr * sin(a)).toFloat()
                pos[i + 1] = (tube * sin(b)).toFloat()
                pos[i + 2] = (rr * cos(a)).toFloat()
            }
        }
        val faces = ArrayList<IntArray>(segments * rings)
        for (u in 0 until segments) for (v in 0 until rings) {
            val u1 = (u + 1) % segments; val v1 = (v + 1) % rings
            faces += intArrayOf(u * rings + v, u1 * rings + v, u1 * rings + v1, u * rings + v1)
        }
        return Mesh(pos, faces.toTypedArray())
    }

    /** X–Z düzleminde 1×1 ızgara (y = 0), [n]×[n] dörtgen. */
    fun plane(n: Int): Mesh {
        val pos = FloatArray((n + 1) * (n + 1) * 3)
        for (i in 0..n) for (j in 0..n) {
            val k = (i * (n + 1) + j) * 3
            pos[k] = -0.5f + i / n.toFloat(); pos[k + 1] = 0f; pos[k + 2] = -0.5f + j / n.toFloat()
        }
        val faces = ArrayList<IntArray>()
        for (i in 0 until n) for (j in 0 until n) {
            val a = i * (n + 1) + j
            faces += intArrayOf(a, a + 1, a + n + 2, a + n + 1)
        }
        return Mesh(pos, faces.toTypedArray())
    }

    /** Boru: dış yarıçap 0,5, iç yarıçap 0,5·(1 − kalınlık), yükseklik 1. */
    fun tube(segments: Int, thickness: Float): Mesh {
        val ro = 0.5f; val ri = 0.5f * (1f - thickness)
        val pos = FloatArray(segments * 4 * 3)
        fun put(i: Int, r: Float, y: Float, s: Int) {
            val a = 2.0 * PI * s / segments
            pos[3 * i] = (r * sin(a)).toFloat(); pos[3 * i + 1] = y; pos[3 * i + 2] = (r * cos(a)).toFloat()
        }
        for (s in 0 until segments) {
            put(s, ro, -0.5f, s); put(segments + s, ro, 0.5f, s)
            put(2 * segments + s, ri, -0.5f, s); put(3 * segments + s, ri, 0.5f, s)
        }
        val ob = 0; val ot = segments; val ib = 2 * segments; val it = 3 * segments
        val faces = ArrayList<IntArray>()
        for (s in 0 until segments) {
            val t = (s + 1) % segments
            faces += intArrayOf(ob + s, ob + t, ot + t, ot + s)
            faces += intArrayOf(ib + t, ib + s, it + s, it + t)
            faces += intArrayOf(ot + s, ot + t, it + t, it + s)
            faces += intArrayOf(ob + t, ob + s, ib + s, ib + t)
        }
        return Mesh(pos, faces.toTypedArray())
    }

    /** Rampa (üçgen prizma): taban 1×1, arka yüz dik, eğim öne doğru iner. */
    fun wedge(): Mesh {
        val h = 0.5f
        val p = floatArrayOf(-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h, -h, h, -h, h, h, -h)
        val f = arrayOf(intArrayOf(0, 1, 2, 3), intArrayOf(1, 0, 4, 5), intArrayOf(3, 2, 5, 4), intArrayOf(0, 3, 4), intArrayOf(1, 5, 2))
        return Mesh(p, f)
    }

    /** Yıldız prizma: [points] uçlu, iç yarıçap oranı [inner]; kalınlık 0,25. */
    fun star(points: Int, inner: Float): Mesh {
        val n = points * 2
        val h = 0.125f
        val pos = FloatArray((2 * n + 2) * 3)
        for (k in 0 until n) {
            val r = if (k % 2 == 0) 0.5f else 0.5f * inner
            val a = 2.0 * PI * k / n
            val x = (r * sin(a)).toFloat(); val z = (r * cos(a)).toFloat()
            pos[3 * k] = x; pos[3 * k + 1] = -h; pos[3 * k + 2] = z
            pos[3 * (n + k)] = x; pos[3 * (n + k) + 1] = h; pos[3 * (n + k) + 2] = z
        }
        val cb = 2 * n; val ct = 2 * n + 1
        pos[3 * cb + 1] = -h; pos[3 * ct + 1] = h
        val faces = ArrayList<IntArray>()
        for (k in 0 until n) {
            val j = (k + 1) % n
            faces += intArrayOf(k, j, n + j, n + k)
            faces += intArrayOf(ct, n + k, n + j)
            faces += intArrayOf(cb, j, k)
        }
        return Mesh(pos, faces.toTypedArray())
    }
}
