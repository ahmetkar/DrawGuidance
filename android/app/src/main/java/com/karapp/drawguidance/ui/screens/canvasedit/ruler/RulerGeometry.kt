package com.karapp.drawguidance.ui.screens.canvasedit.ruler

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/**
 * v40 cetvel türleri: düz cetvel, iletki (yarım daire), gönyeler (45-45-90 ve 30-60-90), pergel (merkez çevresinde
 * sabit yarıçapla çember), elips/daire şablonu (delikli levha) ve pistole (eğri cetveli).
 */
enum class RulerType {
    STRAIGHT, PROTRACTOR, SET_SQUARE_45, SET_SQUARE_30, COMPASS, ELLIPSE, FRENCH_CURVE;

    companion object {
        fun parse(value: String?): RulerType = entries.firstOrNull { it.name == value } ?: STRAIGHT
    }
}

/** v40 cetvelde dokunulan yer: gövde (taşı), döndürme, boy ve (elipste) kısa eksen tutamağı. */
enum class RulerHandle { NONE, BODY, ROTATE, SIZE, MINOR }

/**
 * v40 cetvelin tuvaldeki yeri (tuval birimi): merkez, açı (derece), boy ([length]: düz cetvelde uzunluk, iletkide ve
 * pergelde çap, gönyede uzun dik kenar, elipste uzun eksen, pistolede genişlik), düz cetvelin eni ([thickness]) ve
 * elipsin kısa/uzun eksen oranı ([minor]).
 */
data class RulerPose(
    val type: RulerType,
    val cx: Float,
    val cy: Float,
    val angle: Float,
    val length: Float,
    val thickness: Float,
    val minor: Float = 0.6f,
) {
    companion object {
        const val MIN_LENGTH = 24f
        const val MAX_LENGTH = 40000f
    }
}

/**
 * v40 bir cetvel duruşunun hesaplanmış geometrisi (tuval koordinatı): gövde çokgeni (ve varsa deliği), yapışma
 * kenarları, tutamakların yeri. Duruş değişince bir kez kurulur; dokunuş başına yalnız sorgulanır.
 */
class RulerShape(val pose: RulerPose) {
    private val rad = Math.toRadians(pose.angle.toDouble()).toFloat()
    private val cosA = cos(rad)
    private val sinA = sin(rad)

    /** Yerel koordinat (cetvelin ekseni x, aşağısı y) → tuval. */
    fun toWorld(lx: Float, ly: Float): Offset = Offset(pose.cx + lx * cosA - ly * sinA, pose.cy + lx * sinA + ly * cosA)

    /** Tuval → yerel. */
    fun toLocal(x: Float, y: Float): Offset {
        val dx = x - pose.cx; val dy = y - pose.cy
        return Offset(dx * cosA + dy * sinA, -dx * sinA + dy * cosA)
    }

    private fun world(local: FloatArray): FloatArray {
        val out = FloatArray(local.size)
        for (i in 0 until local.size / 2) {
            val p = toWorld(local[2 * i], local[2 * i + 1])
            out[2 * i] = p.x; out[2 * i + 1] = p.y
        }
        return out
    }

    private val l = pose.length
    private val half = l / 2f

    /** Gövdenin dış çokgeni (yerel). */
    val localBody: FloatArray
    /** Gövdedeki delik (elips şablonu, pistole), yerel; yoksa null. */
    val localHole: FloatArray?
    /** Ekranda çizilecek ve yapışılacak konturlar (yerel), kapalı mı. */
    val localContours: List<Pair<FloatArray, Boolean>>
    val edges: SnapEdges

    init {
        val b = SnapEdges.Builder()
        when (pose.type) {
            RulerType.STRAIGHT -> {
                val t = pose.thickness / 2f
                localBody = floatArrayOf(-half, -t, half, -t, half, t, -half, t)
                localHole = null
                val top = floatArrayOf(-half, -t, half, -t)
                val bottom = floatArrayOf(-half, t, half, t)
                localContours = listOf(top to false, bottom to false)
                world(top).let { w -> b.line(w[0], w[1], w[2], w[3]).endContour() }
                world(bottom).let { w -> b.line(w[0], w[1], w[2], w[3]).endContour() }
            }
            RulerType.PROTRACTOR -> {
                localBody = arcPolygon(half, PI.toFloat(), PI.toFloat(), 64, closeToCenter = false)
                localHole = null
                localContours = listOf(localBody to true)
                // Yay (yukarıdaki yarım daire) ve taban tek kontur: çizgi köşede tabana geçer
                b.arc(pose.cx, pose.cy, half, rad + PI.toFloat(), PI.toFloat())
                val a = toWorld(-half, 0f); val c = toWorld(half, 0f)
                b.line(a.x, a.y, c.x, c.y).endContour()
            }
            RulerType.SET_SQUARE_45, RulerType.SET_SQUARE_30 -> {
                val h = if (pose.type == RulerType.SET_SQUARE_45) l else l * tan(PI / 6).toFloat()
                // Dik köşe (0,0), uzun dik kenar +x, kısa dik kenar yukarı; ağırlık merkezi başlangıçta
                val gx = l / 3f; val gy = -h / 3f
                localBody = floatArrayOf(-gx, -gy, l - gx, -gy, -gx, -h - gy)
                localHole = null
                localContours = listOf(localBody to true)
                b.polyline(world(localBody), closed = true).endContour()
            }
            RulerType.COMPASS -> {
                localBody = FloatArray(0)
                localHole = null
                localContours = emptyList()
                b.arc(pose.cx, pose.cy, half, 0f, (2 * PI).toFloat()).endContour()
            }
            RulerType.ELLIPSE -> {
                val a = half
                val bb = half * pose.minor.coerceIn(MIN_MINOR, 1f)
                val m = plateMargin()
                localBody = floatArrayOf(-a - m, -bb - m, a + m, -bb - m, a + m, bb + m, -a - m, bb + m)
                val hole = FloatArray(ELLIPSE_STEPS * 2)
                for (i in 0 until ELLIPSE_STEPS) {
                    val th = (2 * PI * i / ELLIPSE_STEPS).toFloat()
                    hole[2 * i] = a * cos(th); hole[2 * i + 1] = bb * sin(th)
                }
                localHole = hole
                localContours = listOf(hole to true)
                // Daire ise tam yay (birebir), değilse sık kırık çizgi
                if (bb >= a * 0.999f) b.arc(pose.cx, pose.cy, a, 0f, (2 * PI).toFloat()).endContour()
                else b.polyline(world(hole), closed = true).endContour()
            }
            RulerType.FRENCH_CURVE -> {
                val outer = scaled(FrenchCurve.outer, l)
                val inner = scaled(FrenchCurve.inner, l)
                localBody = outer
                localHole = inner
                localContours = listOf(outer to true, inner to true)
                b.polyline(world(outer), closed = true).endContour()
                b.polyline(world(inner), closed = true).endContour()
            }
        }
        edges = b.build()
    }

    /** Elips levhasının deliğin çevresindeki payı. */
    private fun plateMargin(): Float = maxOf(half * 0.18f, pose.thickness * 0.35f)

    private val worldBody = world(localBody)
    private val worldHole = localHole?.let { world(it) }

    /** Gövdenin tuvaldeki çokgeni ve deliği (çizim için). */
    val bodyPolygon: FloatArray get() = worldBody
    val holePolygon: FloatArray? get() = worldHole
    val contours: List<Pair<FloatArray, Boolean>> by lazy { localContours.map { (pts, closed) -> world(pts) to closed } }

    /** Nokta gövdenin üstünde mi (delik hariç). Pergelin gövdesi merkezdeki iğne ([pinRadius]). */
    fun inBody(p: Offset, pinRadius: Float): Boolean {
        if (pose.type == RulerType.COMPASS) return hypot(p.x - pose.cx, p.y - pose.cy) <= pinRadius
        if (!PolygonMath.contains(worldBody, p.x, p.y)) return false
        return worldHole == null || !PolygonMath.contains(worldHole, p.x, p.y)
    }

    /**
     * Tutamağın tuvaldeki yeri; [gap] gövdeden uzaklık (tuval birimi; ekranda sabit kalsın diye ölçeğe bölünmüş).
     * Bu cetvelde o tutamak yoksa null.
     */
    fun handle(kind: RulerHandle, gap: Float): Offset? = when (pose.type) {
        RulerType.STRAIGHT -> when (kind) {
            RulerHandle.ROTATE -> toWorld(-half - gap, 0f)
            RulerHandle.SIZE -> toWorld(half + gap, 0f)
            else -> null
        }
        RulerType.PROTRACTOR -> when (kind) {
            RulerHandle.ROTATE -> toWorld(-half - gap, 0f)
            RulerHandle.SIZE -> toWorld(half + gap, 0f)
            else -> null
        }
        RulerType.SET_SQUARE_45, RulerType.SET_SQUARE_30 -> {
            val h = if (pose.type == RulerType.SET_SQUARE_45) l else l * tan(PI / 6).toFloat()
            when (kind) {
                RulerHandle.SIZE -> toWorld(l * 2f / 3f + gap, h / 3f)
                RulerHandle.ROTATE -> toWorld(-l / 3f, -h * 2f / 3f - gap)
                else -> null
            }
        }
        RulerType.COMPASS -> when (kind) {
            RulerHandle.SIZE -> toWorld(half, 0f)
            else -> null
        }
        RulerType.ELLIPSE -> {
            val m = plateMargin()
            val bb = half * pose.minor.coerceIn(MIN_MINOR, 1f)
            when (kind) {
                RulerHandle.ROTATE -> toWorld(-half - m - gap, 0f)
                RulerHandle.SIZE -> toWorld(half + m + gap, 0f)
                RulerHandle.MINOR -> toWorld(0f, -bb - m - gap)
                else -> null
            }
        }
        RulerType.FRENCH_CURVE -> when (kind) {
            RulerHandle.ROTATE -> toWorld(-half - gap, 0f)
            RulerHandle.SIZE -> toWorld(half + gap, 0f)
            else -> null
        }
    }

    /** Döndürme tutamağının yerel açısı (radyan): tutamağı sürüklerken cetvelin açısı buna göre bulunur. */
    fun rotateHandleLocalAngle(gap: Float): Float {
        val h = handle(RulerHandle.ROTATE, gap) ?: return 0f
        val local = toLocal(h.x, h.y)
        return atan2(local.y, local.x)
    }

    /** [p]'deki tutamak ya da gövde ([handleRadius] ve [gap] tuval birimi). */
    fun hit(p: Offset, handleRadius: Float, gap: Float): RulerHandle {
        for (kind in HANDLE_ORDER) {
            val h = handle(kind, gap) ?: continue
            if (hypot(p.x - h.x, p.y - h.y) <= handleRadius) return kind
        }
        return if (inBody(p, handleRadius)) RulerHandle.BODY else RulerHandle.NONE
    }

    /**
     * Boy tutamağı [p]'ye sürüklenince yeni duruş. Düz cetvelde karşı uç yerinde kalır; ötekiler merkez çevresinde
     * büyür. Pergelde tutamak yarıçapla birlikte açıyı da belirler.
     */
    fun resized(handle: RulerHandle, p: Offset, gap: Float): RulerPose {
        val local = toLocal(p.x, p.y)
        fun clampLen(v: Float) = v.coerceIn(RulerPose.MIN_LENGTH, RulerPose.MAX_LENGTH)
        return when (pose.type) {
            RulerType.STRAIGHT -> {
                val newL = clampLen(local.x - gap + half)
                val shift = (newL - l) / 2f
                val c = toWorld(shift, 0f)
                pose.copy(cx = c.x, cy = c.y, length = newL)
            }
            RulerType.PROTRACTOR, RulerType.FRENCH_CURVE -> pose.copy(length = clampLen(2f * (local.x - gap)))
            RulerType.SET_SQUARE_45, RulerType.SET_SQUARE_30 -> pose.copy(length = clampLen((local.x - gap) * 1.5f))
            RulerType.COMPASS -> {
                val r = hypot(p.x - pose.cx, p.y - pose.cy)
                val angle = Math.toDegrees(atan2(p.y - pose.cy, p.x - pose.cx).toDouble()).toFloat()
                pose.copy(length = clampLen(2f * r), angle = angle)
            }
            RulerType.ELLIPSE -> if (handle == RulerHandle.MINOR) {
                val bb = (-local.y - gap) / 1.18f
                pose.copy(minor = (bb / half).coerceIn(MIN_MINOR, 1f))
            } else pose.copy(length = clampLen(2f * (local.x - gap) / 1.18f))
        }
    }

    /** Görünen boy (etiket): düz cetvelde uzunluk, iletki/pergel/elipste yarıçap ya da eksen, gönyede uzun kenar. */
    val measuredLength: Float get() = when (pose.type) {
        RulerType.PROTRACTOR, RulerType.COMPASS -> half
        else -> l
    }

    companion object {
        const val ELLIPSE_STEPS = 128
        const val MIN_MINOR = 0.15f
        private val HANDLE_ORDER = listOf(RulerHandle.ROTATE, RulerHandle.SIZE, RulerHandle.MINOR)

        /** Merkezi başlangıçta, [r] yarıçaplı, [start] açısından [sweep] kadar yay çokgeni (taban kapanır). */
        fun arcPolygon(r: Float, start: Float, sweep: Float, steps: Int, closeToCenter: Boolean): FloatArray {
            val n = steps + 1 + if (closeToCenter) 1 else 0
            val out = FloatArray(n * 2)
            for (i in 0..steps) {
                val a = start + sweep * i / steps
                out[2 * i] = r * cos(a); out[2 * i + 1] = r * sin(a)
            }
            return out
        }

        private fun scaled(unit: FloatArray, s: Float) = FloatArray(unit.size) { unit[it] * s }
    }
}

/** v40 çokgen yardımcıları. */
object PolygonMath {
    /** Çift-tek kuralıyla nokta çokgenin içinde mi (x0, y0, x1, y1…). */
    fun contains(poly: FloatArray, x: Float, y: Float): Boolean {
        val n = poly.size / 2
        if (n < 3) return false
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly[2 * i]; val yi = poly[2 * i + 1]
            val xj = poly[2 * j]; val yj = poly[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}

/**
 * v40 pistole (eğri cetveli) biçimi: genişliği 1 olan, merkezi başlangıçta dış kontur ve içindeki eğri yarık.
 * Kontrol noktaları Catmull-Rom eğrisiyle sıklaştırılır.
 */
internal object FrenchCurve {
    private val outerControl = floatArrayOf(
        -0.50f, 0.02f, -0.44f, -0.10f, -0.32f, -0.17f, -0.16f, -0.18f, -0.02f, -0.12f, 0.10f, -0.15f,
        0.24f, -0.24f, 0.38f, -0.25f, 0.48f, -0.16f, 0.49f, -0.04f, 0.40f, 0.05f, 0.26f, 0.08f,
        0.12f, 0.12f, 0.00f, 0.20f, -0.14f, 0.22f, -0.30f, 0.17f, -0.44f, 0.12f,
    )
    private val innerControl = floatArrayOf(
        -0.30f, 0.02f, -0.22f, -0.06f, -0.10f, -0.05f, 0.02f, 0.01f, 0.14f, -0.05f, 0.26f, -0.12f,
        0.34f, -0.10f, 0.30f, -0.04f, 0.18f, 0.02f, 0.06f, 0.08f, -0.08f, 0.09f, -0.22f, 0.07f,
    )
    val outer: FloatArray = catmullRom(outerControl, 10)
    val inner: FloatArray = catmullRom(innerControl, 10)

    /** Kapalı Catmull-Rom eğrisi: her aralığa [steps] nokta. */
    fun catmullRom(ctrl: FloatArray, steps: Int): FloatArray {
        val n = ctrl.size / 2
        val out = FloatArray(n * steps * 2)
        var k = 0
        for (i in 0 until n) {
            val p0 = (i - 1 + n) % n; val p1 = i; val p2 = (i + 1) % n; val p3 = (i + 2) % n
            for (s in 0 until steps) {
                val t = s.toFloat() / steps
                val t2 = t * t; val t3 = t2 * t
                for (d in 0..1) {
                    val a = ctrl[2 * p0 + d]; val b = ctrl[2 * p1 + d]; val c = ctrl[2 * p2 + d]; val e = ctrl[2 * p3 + d]
                    out[k + d] = 0.5f * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - e) * t2 + (-a + 3 * b - 3 * c + e) * t3)
                }
                k += 2
            }
        }
        return out
    }
}
