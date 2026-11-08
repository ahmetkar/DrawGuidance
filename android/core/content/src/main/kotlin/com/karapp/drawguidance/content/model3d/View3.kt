package com.karapp.drawguidance.content.model3d

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Parçanın dönüşümü: M = Öteleme · Rz · Ry · Rx · Ölçek (3×4, satır düzeninde). Normaller R · Ölçek⁻¹ ile dönüştürülür
 * (eşit olmayan ölçekte de yüzeye dik kalır).
 */
class Transform3(val m: FloatArray, val n: FloatArray) {

    fun apply(x: Float, y: Float, z: Float, out: FloatArray, o: Int) {
        out[o] = m[0] * x + m[1] * y + m[2] * z + m[3]
        out[o + 1] = m[4] * x + m[5] * y + m[6] * z + m[7]
        out[o + 2] = m[8] * x + m[9] * y + m[10] * z + m[11]
    }

    fun apply(v: V3): V3 = V3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z + m[3],
        m[4] * v.x + m[5] * v.y + m[6] * v.z + m[7],
        m[8] * v.x + m[9] * v.y + m[10] * v.z + m[11],
    )

    /** Yön (öteleme olmadan). */
    fun applyDir(v: V3): V3 = V3(m[0] * v.x + m[1] * v.y + m[2] * v.z, m[4] * v.x + m[5] * v.y + m[6] * v.z, m[8] * v.x + m[9] * v.y + m[10] * v.z)

    /** Normal (birim). */
    fun normal(x: Float, y: Float, z: Float, out: FloatArray, o: Int) {
        val a = n[0] * x + n[1] * y + n[2] * z
        val b = n[3] * x + n[4] * y + n[5] * z
        val c = n[6] * x + n[7] * y + n[8] * z
        val l = sqrt(a * a + b * b + c * c).coerceAtLeast(1e-12f)
        out[o] = a / l; out[o + 1] = b / l; out[o + 2] = c / l
    }

    companion object {
        fun of(part: Part): Transform3 = of(part.position, part.rotation, part.scale)

        fun of(position: V3, rotation: V3, scale: V3): Transform3 {
            val r = rotation(rotation)
            val sx = scale.x; val sy = scale.y; val sz = scale.z
            val m = floatArrayOf(
                r[0] * sx, r[1] * sy, r[2] * sz, position.x,
                r[3] * sx, r[4] * sy, r[5] * sz, position.y,
                r[6] * sx, r[7] * sy, r[8] * sz, position.z,
            )
            fun inv(v: Float) = if (abs(v) < 1e-6f) 1e6f else 1f / v
            val n = floatArrayOf(
                r[0] * inv(sx), r[1] * inv(sy), r[2] * inv(sz),
                r[3] * inv(sx), r[4] * inv(sy), r[5] * inv(sz),
                r[6] * inv(sx), r[7] * inv(sy), r[8] * inv(sz),
            )
            return Transform3(m, n)
        }

        /** Döndürme matrisi (3×3, satır düzeni): önce X, sonra Y, sonra Z ekseni etrafında (derece). */
        fun rotation(deg: V3): FloatArray {
            val ax = Math.toRadians(deg.x.toDouble()); val ay = Math.toRadians(deg.y.toDouble()); val az = Math.toRadians(deg.z.toDouble())
            val cx = cos(ax).toFloat(); val sx = sin(ax).toFloat()
            val cy = cos(ay).toFloat(); val sy = sin(ay).toFloat()
            val cz = cos(az).toFloat(); val sz = sin(az).toFloat()
            // Rz · Ry · Rx
            return floatArrayOf(
                cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
                sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
                -sy, cy * sx, cy * cx,
            )
        }

        /** Döndürmenin tersi (devriği) ile yön: dünya yönünü parçanın döndürülmemiş eksenlerine çevirir. */
        fun inverseRotate(deg: V3, v: V3): V3 {
            val r = rotation(deg)
            return V3(r[0] * v.x + r[3] * v.y + r[6] * v.z, r[1] * v.x + r[4] * v.y + r[7] * v.z, r[2] * v.x + r[5] * v.y + r[8] * v.z)
        }
    }
}

/**
 * Kameranın ekrana izdüşümü. Kamera eksenleri: sağ (r), aşağı (d) ve bakana doğru (t). Bir dünya noktasının kamera
 * koordinatı (xc, yc, zc) hedefe göredir; perspektifte ölçek s = f / (f − zc), ekran = merkez + (xc, yc)·s·[scale].
 * Paralel izdüşümde s = 1. [scale] hedef derinliğinde dünya biriminin piksel karşılığı.
 */
class View3(
    val camera: Camera3,
    val width: Int,
    val height: Int,
    val scale: Float,
    val centerX: Float,
    val centerY: Float,
) {
    val rx: Float; val ry: Float; val rz: Float
    val dx: Float; val dy: Float; val dz: Float
    val tx: Float; val ty: Float; val tz: Float
    val focal: Float = focalOf(camera.persp)
    val perspective: Boolean get() = focal.isFinite()
    private val ox = camera.target.x; private val oy = camera.target.y; private val oz = camera.target.z

    init {
        val y = Math.toRadians(camera.yaw.toDouble()); val p = Math.toRadians(camera.pitch.coerceIn(-89.5f, 89.5f).toDouble())
        val cp = cos(p).toFloat(); val sp = sin(p).toFloat(); val cyw = cos(y).toFloat(); val syw = sin(y).toFloat()
        tx = cp * syw; ty = sp; tz = cp * cyw
        rx = cyw; ry = 0f; rz = -syw
        dx = ry * tz - rz * ty; dy = rz * tx - rx * tz; dz = rx * ty - ry * tx
    }

    /** Kameranın dünya konumu (perspektifte). */
    val eye: V3 get() = V3(ox + tx * focal, oy + ty * focal, oz + tz * focal)

    /** Bakış yönü (göze doğru) birim vektör. */
    val toViewer: V3 get() = V3(tx, ty, tz)
    val right: V3 get() = V3(rx, ry, rz)
    val down: V3 get() = V3(dx, dy, dz)

    /** Kamera derinliği (bakana doğru artı). */
    fun depth(x: Float, y: Float, z: Float): Float = (x - ox) * tx + (y - oy) * ty + (z - oz) * tz

    /** Perspektif ölçeği; kameranın arkasındaysa 0'dan küçük ya da çok büyük olur. */
    fun persp(zc: Float): Float = if (!perspective) 1f else focal / (focal - zc)

    /** Dünya noktasının ekran konumu ve derinlik bilgisi: out = (x, y, zc, s). Görünmüyorsa s ≤ 0. */
    fun project(x: Float, y: Float, z: Float, out: FloatArray, o: Int = 0) {
        val ax = x - ox; val ay = y - oy; val az = z - oz
        val xc = ax * rx + ay * ry + az * rz
        val yc = ax * dx + ay * dy + az * dz
        val zc = ax * tx + ay * ty + az * tz
        val s = if (!perspective) 1f else {
            val den = focal - zc
            if (den < focal * 0.02f) -1f else focal / den
        }
        out[o] = centerX + xc * s * scale
        out[o + 1] = centerY + yc * s * scale
        out[o + 2] = zc
        out[o + 3] = s
    }

    fun project(v: V3): FloatArray = FloatArray(4).also { project(v.x, v.y, v.z, it) }

    /** Ekran noktasından geçen görüş ışını: başlangıç ve (birim) yön. */
    fun ray(sx: Float, sy: Float): Pair<V3, V3> {
        val a = (sx - centerX) / scale; val b = (sy - centerY) / scale
        val q = V3(ox + rx * a + dx * b, oy + ry * a + dy * b, oz + rz * a + dz * b)
        return if (!perspective) (q + V3(tx, ty, tz) * 1000f) to V3(-tx, -ty, -tz)
        else eye to (q - eye).normalized()
    }

    /** Ekrandaki öteleme (piksel), hedef derinliğinde dünya ötelemesine. */
    fun screenDelta(dxPx: Float, dyPx: Float, atDepth: Float = 0f): V3 {
        val s = persp(atDepth).takeIf { it > 0f } ?: 1f
        val k = 1f / (scale * s)
        return V3((rx * dxPx + dx * dyPx) * k, (ry * dxPx + dy * dyPx) * k, (rz * dxPx + dz * dyPx) * k)
    }

    companion object {
        /** Düzenleyicide zoom = 1'de görünen dünya genişliği (birim küp ekranın ~yarısı). */
        const val REFERENCE_EXTENT = 1.6f

        fun focalOf(persp: Float): Float = if (persp < 0.02f) Float.POSITIVE_INFINITY else 0.8f + 3.2f / persp.coerceIn(0.02f, 1f)

        /** Düzenleyici görünümü: hedef ekranın ortasında, yakınlık kameradan. */
        fun editor(camera: Camera3, width: Int, height: Int): View3 =
            View3(camera, width, height, min(width, height) * 0.5f / REFERENCE_EXTENT * camera.zoom.coerceIn(0.05f, 40f), width / 2f, height / 2f)

        /**
         * Modeli (görünür parçalar ve çizgiler) [margin] payla ekrana sığdıran görünüm: kameranın açıları ve perspektifi
         * korunur, ölçek ve merkez çizime göre seçilir. Boş modelde düzenleyici görünümü.
         */
        fun fit(model: Model3D, camera: Camera3, width: Int, height: Int, margin: Float = 0.06f): View3 {
            val probe = View3(camera, width, height, 1f, 0f, 0f)
            var ax = Float.POSITIVE_INFINITY; var ay = Float.POSITIVE_INFINITY
            var bx = Float.NEGATIVE_INFINITY; var by = Float.NEGATIVE_INFINITY
            val tmp = FloatArray(3); val pr = FloatArray(4)
            fun acc(x: Float, y: Float, z: Float) {
                probe.project(x, y, z, pr)
                if (pr[3] <= 0f) return
                ax = min(ax, pr[0]); ay = min(ay, pr[1]); bx = max(bx, pr[0]); by = max(by, pr[1])
            }
            model.parts.filter { it.visible }.forEach { part ->
                val t = Transform3.of(part)
                val p = part.mesh.pos
                for (i in 0 until part.mesh.vertexCount) { t.apply(p[3 * i], p[3 * i + 1], p[3 * i + 2], tmp, 0); acc(tmp[0], tmp[1], tmp[2]) }
            }
            model.strokes.forEach { s -> for (i in 0 until s.count) acc(s.points[3 * i], s.points[3 * i + 1], s.points[3 * i + 2]) }
            if (!ax.isFinite()) return editor(camera, width, height)
            val bw = max(bx - ax, 1e-4f); val bh = max(by - ay, 1e-4f)
            val mw = width * (1f - 2f * margin); val mh = height * (1f - 2f * margin)
            val k = min(mw / bw, mh / bh)
            val cx = width / 2f - (ax + bx) / 2f * k
            val cy = height / 2f - (ay + by) / 2f * k
            return View3(camera, width, height, k, cx, cy)
        }
    }
}
