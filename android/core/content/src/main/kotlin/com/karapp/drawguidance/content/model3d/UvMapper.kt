package com.karapp.drawguidance.content.model3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * v24 yüz köşelerinin doku koordinatları (yazılım çizici, ekran kartı ve glTF dışa aktarma ortak kullanır): nesne
 * uzayı × ölçek (bir dünya birimi = bir tekrar; malzemenin doku ölçeği çizimde uygulanır). Kutu eşlemesi yüzün baktığı
 * eksene göre, silindir/küre açıya göre (dikiş düzeltilir); [UvMode.MESH] ağın kendi koordinatları.
 */
object UvMapper {

    fun compute(part: Part): Array<FloatArray> {
        val m = part.mesh
        if (part.uv == UvMode.MESH) m.uvs?.let { return it }
        val sx = part.scale.x; val sy = part.scale.y; val sz = part.scale.z
        val fn = m.faceNormals
        val mode0 = if (part.uv == UvMode.MESH) UvMode.BOX else part.uv
        val per = PI.toFloat() * (abs(sx) + abs(sz)) * 0.5f
        return Array(m.faceCount) { f ->
            val face = m.faces[f]
            val out = FloatArray(face.size * 2)
            val nx = abs(fn[3 * f] * sx); val ny = abs(fn[3 * f + 1] * sy); val nz = abs(fn[3 * f + 2] * sz)
            val mode = if (mode0 == UvMode.CYLINDER && ny > 0.7f * max(nx, nz).coerceAtLeast(1e-6f) * 1.4f) UvMode.PLANAR else mode0
            for (k in face.indices) {
                val v = face[k]
                val x = m.pos[3 * v] * sx; val y = m.pos[3 * v + 1] * sy; val z = m.pos[3 * v + 2] * sz
                when (mode) {
                    UvMode.BOX, UvMode.MESH -> when {
                        nx >= ny && nx >= nz -> { out[2 * k] = z; out[2 * k + 1] = -y }
                        ny >= nx && ny >= nz -> { out[2 * k] = x; out[2 * k + 1] = z }
                        else -> { out[2 * k] = x; out[2 * k + 1] = -y }
                    }
                    UvMode.PLANAR -> { out[2 * k] = x; out[2 * k + 1] = z }
                    UvMode.CYLINDER -> {
                        out[2 * k] = (atan2(x, z) / (2f * PI.toFloat()) + 0.5f) * per
                        out[2 * k + 1] = -y
                    }
                    UvMode.SPHERE -> {
                        val len = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
                        out[2 * k] = (atan2(x, z) / (2f * PI.toFloat()) + 0.5f) * per
                        out[2 * k + 1] = (kotlin.math.acos((y / len).coerceIn(-1f, 1f)) / PI.toFloat()) * per * 0.5f
                    }
                }
            }
            // Dikiş: açıya bağlı u değeri yüz içinde sarmışsa düzeltilir
            if (mode == UvMode.CYLINDER || mode == UvMode.SPHERE) {
                var lo = Float.POSITIVE_INFINITY; var hi = Float.NEGATIVE_INFINITY
                for (k in face.indices) { lo = min(lo, out[2 * k]); hi = max(hi, out[2 * k]) }
                if (hi - lo > per * 0.5f) for (k in face.indices) if (out[2 * k] < per * 0.5f) out[2 * k] += per
            }
            out
        }
    }

    private class Key(val mesh: Mesh, val uv: UvMode, val sx: Float, val sy: Float, val sz: Float) {
        override fun equals(other: Any?): Boolean =
            other is Key && other.mesh === mesh && other.uv == uv && other.sx == sx && other.sy == sy && other.sz == sz

        override fun hashCode(): Int = ((System.identityHashCode(mesh) * 31 + uv.ordinal) * 31 + sx.toBits()) * 31 + (sy.toBits() xor sz.toBits())
    }

    private val cache = object : LinkedHashMap<Key, Array<FloatArray>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Array<FloatArray>>?): Boolean = size > 96
    }

    /** Aynı ağ, eşleme ve ölçek için önbellekten (çizim her karede yeniden hesaplamasın). */
    fun cached(part: Part): Array<FloatArray> {
        val key = Key(part.mesh, part.uv, part.scale.x, part.scale.y, part.scale.z)
        synchronized(cache) { cache[key]?.let { return it } }
        val uv = compute(part)
        synchronized(cache) { cache[key] = uv }
        return uv
    }
}
