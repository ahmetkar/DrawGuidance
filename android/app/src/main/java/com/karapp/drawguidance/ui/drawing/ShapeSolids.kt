package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v21 gölgeli (3B) cisimler: temel şekillere eklenen, ışığı sol üstten alan katı cisimler. Düz yüzlü cisimlerde (küp,
 * kutu, piramit, prizmalar) her görünen yüz ayrı tonda; eğri yüzlülerde (silindir, koni, küre, kubbe) ışığa doğru kayan
 * iç içe bantlarla yumuşak geçiş. Tonlar şeklin dolgu renginden (yoksa kenar renginden) türetilir ([ShapeRenderer.SHADE]):
 * renk değişince cisim yeni renge göre gölgelenir; kenarlar ince, dış hat tam kalınlıkta.
 */
internal object ShapeSolids {

    val kinds = setOf(
        ShapeKind.SOLID_CUBE, ShapeKind.SOLID_BOX, ShapeKind.SOLID_CYLINDER, ShapeKind.SOLID_CONE, ShapeKind.SOLID_SPHERE,
        ShapeKind.SOLID_DOME, ShapeKind.SOLID_PYRAMID, ShapeKind.SOLID_PRISM, ShapeKind.SOLID_HEX_PRISM,
    )

    /** Kenar kalınlığı çarpanı: yüz kenarları ince, bantlarda kenar yok. */
    private const val EDGE = 0.55f
    private const val BANDS = 12

    private class Unit(val r: Rect) {
        val w = max(r.width, 1e-3f)
        val h = max(r.height, 1e-3f)
        fun p(u: Float, v: Float) = Offset(r.left + u * w, r.top + v * h)
        fun sampleCount(rx: Float, ry: Float) = ((rx * w + ry * h) * PI / 5f).toInt().coerceIn(16, 360)
    }

    private fun face(u: Unit, level: Float, vararg uv: Float, edge: Float = EDGE): ShapePath {
        val pts = List(uv.size / 2) { u.p(uv[it * 2], uv[it * 2 + 1]) }
        return ShapePath(pts + pts.first(), closed = true, fillColorHex = ShapeRenderer.shade(level), widthScale = edge)
    }

    private fun region(pts: List<Offset>, level: Float, edge: Float = 0f): ShapePath =
        ShapePath(pts + pts.first(), closed = true, fillColorHex = ShapeRenderer.shade(level), widthScale = edge)

    private fun outline(pts: List<Offset>, closed: Boolean = true): ShapePath =
        ShapePath(if (closed) pts + pts.first() else pts, closed = closed, fillColorHex = if (closed) ShapeRenderer.NO_FILL else null)

    /** Bant tonu: 0 (en dış, gölgede) → 1 (ışığa bakan tepe). */
    private fun bandLevel(k: Int): Float {
        val t = k / (BANDS - 1f)
        return 0.28f + 0.58f * t
    }

    /** Elips yayı (birim kutuda). */
    private fun arc(u: Unit, cx: Float, cy: Float, rx: Float, ry: Float, from: Double, to: Double): List<Offset> {
        val n = u.sampleCount(rx, ry)
        return List(n + 1) { i ->
            val a = from + (to - from) * i / n
            u.p(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
        }
    }

    fun paths(kind: ShapeKind, rect: Rect): List<ShapePath> {
        val u = Unit(rect)
        return when (kind) {
            ShapeKind.SOLID_CUBE -> box(u, 0.3f)
            ShapeKind.SOLID_BOX -> box(u, 0.24f)
            ShapeKind.SOLID_PYRAMID -> listOf(
                face(u, 0.66f, 0.5f, 0f, 0.04f, 0.84f, 0.66f, 1f),
                face(u, 0.32f, 0.5f, 0f, 0.66f, 1f, 0.96f, 0.78f),
            )
            ShapeKind.SOLID_PRISM -> listOf(
                // Yatan üçgen prizma: sol eğik yüz (ışıkta), sağ eğik yüz, ön üçgen
                face(u, 0.74f, 0f, 0.78f, 0.31f, 0.14f, 0.69f, 0f, 0.38f, 0.64f),
                face(u, 0.36f, 0.31f, 0.14f, 0.62f, 0.78f, 1f, 0.64f, 0.69f, 0f),
                face(u, 0.52f, 0f, 0.78f, 0.62f, 0.78f, 0.31f, 0.14f),
            )
            ShapeKind.SOLID_HEX_PRISM -> hexPrism(u)
            ShapeKind.SOLID_CYLINDER -> cylinder(u)
            ShapeKind.SOLID_CONE -> cone(u)
            ShapeKind.SOLID_SPHERE -> sphere(u)
            ShapeKind.SOLID_DOME -> dome(u)
            else -> emptyList()
        }
    }

    /** Eğik görünüşlü kutu: ön yüz, üst yüz (ışıkta), sağ yüz (gölgede). */
    private fun box(u: Unit, d: Float): List<ShapePath> {
        val f = 1f - d
        return listOf(
            face(u, 0.5f, 0f, d, f, d, f, 1f, 0f, 1f),
            face(u, 0.78f, 0f, d, d, 0f, 1f, 0f, f, d),
            face(u, 0.3f, f, d, 1f, 0f, 1f, f, f, 1f),
        )
    }

    private fun hexPrism(u: Unit): List<ShapePath> {
        val top = (0..5).map { i ->
            val a = -PI / 2 + i * PI / 3
            (0.5f + 0.48f * cos(a).toFloat()) to (0.2f + 0.18f * sin(a).toFloat())
        }
        val drop = 0.58f
        val out = ArrayList<ShapePath>()
        // Öndeki dört yan yüz (soldan sağa ışıktan gölgeye)
        val sides = listOf(5 to 4, 4 to 3, 3 to 2, 2 to 1)
        val levels = listOf(0.66f, 0.54f, 0.42f, 0.3f)
        sides.forEachIndexed { k, (a, b) ->
            val (ax, ay) = top[a]; val (bx, by) = top[b]
            out += face(u, levels[k], ax, ay, bx, by, bx, by + drop, ax, ay + drop)
        }
        out += face(u, 0.8f, *top.flatMap { listOf(it.first, it.second) }.toFloatArray())
        return out
    }

    /**
     * Silindir: gövde, ışığa (sol) doğru daralan dikey bantlar; üstte açık tonlu elips yüz; dış hat. Bantların altı ön
     * taban yayını izler.
     */
    private fun cylinder(u: Unit): List<ShapePath> {
        val top = 0.12f; val bottom = 0.88f; val ry = 0.12f
        val light = 0.34f
        val out = ArrayList<ShapePath>()
        fun bottomY(x: Float): Float {
            val t = ((x - 0.5f) / 0.5f).coerceIn(-1f, 1f)
            return bottom + ry * sqrt(1f - t * t)
        }
        for (k in 0 until BANDS) {
            val t = k / BANDS.toFloat()
            val l = light * t * 0.95f
            val r = 1f - (1f - light) * t * 0.95f
            val n = max(8, ((r - l) * 40).toInt())
            val arcPts = List(n + 1) { i -> val x = r - (r - l) * i / n; u.p(x, bottomY(x)) }
            out += region(listOf(u.p(l, top), u.p(r, top)) + arcPts, bandLevel(k))
        }
        out += outline(listOf(u.p(0f, top)) + List(41) { i -> val x = i / 40f; u.p(x, bottomY(x)) } + u.p(1f, top), closed = false)
        out += region(arc(u, 0.5f, top, 0.5f, ry, 0.0, 2 * PI).dropLast(1), 0.8f, EDGE)
        return out
    }

    /** Koni: tepe noktasından ön taban yayına uzanan, ışığa doğru daralan üçgen bantlar; dış hat. */
    private fun cone(u: Unit): List<ShapePath> {
        val apex = u.p(0.5f, 0.02f)
        val cy = 0.86f; val ry = 0.12f
        val light = 0.36f
        fun baseY(x: Float): Float {
            val t = ((x - 0.5f) / 0.5f).coerceIn(-1f, 1f)
            return cy + ry * sqrt(1f - t * t)
        }
        val out = ArrayList<ShapePath>()
        for (k in 0 until BANDS) {
            val t = k / BANDS.toFloat()
            val l = light * t * 0.95f
            val r = 1f - (1f - light) * t * 0.95f
            val n = max(8, ((r - l) * 40).toInt())
            val arcPts = List(n + 1) { i -> val x = r - (r - l) * i / n; u.p(x, baseY(x)) }
            out += region(listOf(apex) + arcPts, bandLevel(k))
        }
        out += outline(listOf(apex) + List(41) { i -> val x = i / 40f; u.p(x, baseY(x)) }, closed = true)
        return out
    }

    /** Küre: ışığa doğru kayan ve küçülen iç içe daireler, küçük parlama, dış hat. */
    private fun sphere(u: Unit): List<ShapePath> {
        val out = ArrayList<ShapePath>()
        for (k in 0 until BANDS) {
            val t = k / BANDS.toFloat()
            val r = 0.5f * (1f - 0.86f * t)
            val c = 0.5f - 0.5f * 0.36f * t
            out += region(arc(u, c, c, r, r, 0.0, 2 * PI).dropLast(1), bandLevel(k))
        }
        out += region(arc(u, 0.33f, 0.33f, 0.05f, 0.05f, 0.0, 2 * PI).dropLast(1), 0.97f)
        out += outline(arc(u, 0.5f, 0.5f, 0.5f, 0.5f, 0.0, 2 * PI).dropLast(1))
        return out
    }

    /** Kubbe (yarım küre): üst yarım elips ve ön taban yayından oluşan iç içe bantlar; dış hat. */
    private fun dome(u: Unit): List<ShapePath> {
        val out = ArrayList<ShapePath>()
        val cy = 0.78f
        fun silhouette(cx: Float, rx: Float, ryTop: Float, ryBase: Float, cyy: Float): List<Offset> =
            arc(u, cx, cyy, rx, ryTop, PI, 2 * PI) + arc(u, cx, cyy, rx, ryBase, 0.0, PI).drop(1).dropLast(1)
        for (k in 0 until BANDS) {
            val t = k / BANDS.toFloat()
            val s = 1f - 0.84f * t
            val cx = 0.5f - 0.2f * t
            val cyy = cy - 0.24f * t
            out += region(silhouette(cx, 0.5f * s, 0.74f * s, 0.14f * s, cyy), bandLevel(k))
        }
        out += outline(silhouette(0.5f, 0.5f, 0.74f, 0.14f, cy))
        return out
    }
}
