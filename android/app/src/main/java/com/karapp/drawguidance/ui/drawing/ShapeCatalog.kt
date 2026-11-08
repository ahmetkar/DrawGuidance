package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.data.model.CanvasKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Şeklin parmakla nasıl yerleştirildiği. */
enum class ShapeFit {
    /** Sürüklenen dikdörtgene serbestçe yerleşir */
    BOX,
    /** En/boy oranı korunur (sürüklenen yönde kare kutu) */
    SQUARE,
    /** Başlangıç noktasından bitiş noktasına uzanır (çizgi, ok, sayı doğrusu) */
    LINE,
}

/** Şeklin bir çizgisi (tuval koordinatlarında); [dashed] kesik çizgi (ör. katı cisimlerin görünmeyen ayrıtları). */
class ShapePath(
    val points: List<Offset>,
    val closed: Boolean = false,
    val dashed: Boolean = false,
    /** Yazı çapası (eksen etiketi/formül): tek noktalık yol. */
    val label: String? = null,
    val labelSize: Float = 0f,
    /** Parçanın kendi çizgi / dolgu rengi (yoksa şeklinki). */
    val strokeColorHex: String? = null,
    val fillColorHex: String? = null,
    /** v15: parçanın kenar kalınlığı çarpanı (0 = kenar yok), noktalı çizgi, saydamlık ve tanım etiketi. */
    val widthScale: Float? = null,
    val dotted: Boolean = false,
    val alpha: Float? = null,
    val tag: String? = null,
    /** v16: vektör noktaları (nokta sıraları); null: belirgin noktalar kullanılır. */
    val nodes: List<Int>? = null,
)

enum class ShapeKind(val fit: ShapeFit) {
    // Temel
    LINE(ShapeFit.LINE), ARROW(ShapeFit.LINE), DOUBLE_ARROW(ShapeFit.LINE),
    CIRCLE(ShapeFit.SQUARE), ELLIPSE(ShapeFit.BOX), SQUARE(ShapeFit.SQUARE), RECTANGLE(ShapeFit.BOX), ROUNDED_RECT(ShapeFit.BOX),
    TRIANGLE(ShapeFit.BOX), RIGHT_TRIANGLE(ShapeFit.BOX), EQUILATERAL_TRIANGLE(ShapeFit.SQUARE),
    DIAMOND(ShapeFit.BOX), PARALLELOGRAM(ShapeFit.BOX), TRAPEZOID(ShapeFit.BOX),
    PENTAGON(ShapeFit.SQUARE), HEXAGON(ShapeFit.SQUARE), OCTAGON(ShapeFit.SQUARE),
    STAR(ShapeFit.SQUARE), HEART(ShapeFit.SQUARE), SPIRAL(ShapeFit.SQUARE),
    SEMICIRCLE(ShapeFit.BOX), CIRCLE_RADIUS(ShapeFit.SQUARE),
    SECTOR(ShapeFit.SQUARE), ARC(ShapeFit.SQUARE), ANNULUS(ShapeFit.SQUARE), KITE(ShapeFit.BOX),
    RIGHT_TRAPEZOID(ShapeFit.BOX), TRIANGLE_HEIGHT(ShapeFit.BOX), CROSS(ShapeFit.SQUARE), ARROW_BLOCK(ShapeFit.BOX),
    // Manzara
    SUN(ShapeFit.SQUARE), MOON(ShapeFit.SQUARE), CLOUD(ShapeFit.BOX), MOUNTAINS(ShapeFit.BOX), HILLS(ShapeFit.BOX),
    WAVES(ShapeFit.BOX), TREE(ShapeFit.BOX), PINE(ShapeFit.BOX), FLOWER(ShapeFit.SQUARE), LEAF(ShapeFit.BOX),
    BIRD(ShapeFit.BOX), HOUSE(ShapeFit.BOX), DROP(ShapeFit.BOX),
    BUTTERFLY(ShapeFit.BOX), FISH(ShapeFit.BOX), PALM(ShapeFit.BOX), GRASS(ShapeFit.BOX), ROCK(ShapeFit.BOX),
    BOAT(ShapeFit.BOX), APPLE(ShapeFit.SQUARE),
    // Portre
    FACE_GUIDE(ShapeFit.BOX), EYE(ShapeFit.BOX), LIPS(ShapeFit.BOX), NOSE(ShapeFit.BOX),
    // Geometri
    ANGLE(ShapeFit.BOX), RIGHT_ANGLE(ShapeFit.BOX), PARALLEL_LINES(ShapeFit.BOX),
    CUBE(ShapeFit.SQUARE), PRISM(ShapeFit.BOX), CYLINDER(ShapeFit.BOX), CONE(ShapeFit.BOX), SPHERE(ShapeFit.SQUARE), PYRAMID(ShapeFit.BOX),
    TRIANGULAR_PRISM(ShapeFit.BOX), HEXAGONAL_PRISM(ShapeFit.BOX), TETRAHEDRON(ShapeFit.SQUARE),
    TRUNCATED_CONE(ShapeFit.BOX), HEMISPHERE(ShapeFit.BOX), CUBE_NET(ShapeFit.BOX),
    // Grafik
    AXES(ShapeFit.BOX), NUMBER_LINE(ShapeFit.LINE), GRID(ShapeFit.BOX), PARABOLA(ShapeFit.BOX), SINE(ShapeFit.BOX),
    AXES_3D(ShapeFit.SQUARE), FUNCTION_PLOT(ShapeFit.BOX), COORD_GRID(ShapeFit.BOX), UNIT_CIRCLE(ShapeFit.SQUARE),
    BAR_CHART(ShapeFit.BOX), LINE_CHART(ShapeFit.BOX), PIE_CHART(ShapeFit.SQUARE), SCATTER(ShapeFit.BOX),
    // v21 gölgeli 3B cisimler
    SOLID_CUBE(ShapeFit.SQUARE), SOLID_BOX(ShapeFit.BOX), SOLID_CYLINDER(ShapeFit.BOX), SOLID_CONE(ShapeFit.BOX),
    SOLID_SPHERE(ShapeFit.SQUARE), SOLID_DOME(ShapeFit.BOX), SOLID_PYRAMID(ShapeFit.BOX), SOLID_PRISM(ShapeFit.BOX),
    SOLID_HEX_PRISM(ShapeFit.BOX),
}

/** v21 gölgeli 3B cisimler (temel şekillerde ve not tuvalinin cisimlerinde). */
val SolidShapes: List<ShapeKind> = listOf(
    ShapeKind.SOLID_CUBE, ShapeKind.SOLID_BOX, ShapeKind.SOLID_CYLINDER, ShapeKind.SOLID_CONE, ShapeKind.SOLID_SPHERE,
    ShapeKind.SOLID_DOME, ShapeKind.SOLID_PYRAMID, ShapeKind.SOLID_PRISM, ShapeKind.SOLID_HEX_PRISM,
)

/** Şekil seçicideki kategoriler; resim ve çalışma/not tuvallerinin kataloğu farklıdır. */
enum class ShapeCategory(val kind: CanvasKind, val shapes: List<ShapeKind>, val listed: Boolean = true) {
    ART_BASIC(
        CanvasKind.ART,
        listOf(
            ShapeKind.LINE, ShapeKind.ARROW, ShapeKind.CIRCLE, ShapeKind.ELLIPSE, ShapeKind.SQUARE, ShapeKind.RECTANGLE,
            ShapeKind.ROUNDED_RECT, ShapeKind.TRIANGLE, ShapeKind.DIAMOND, ShapeKind.PENTAGON, ShapeKind.HEXAGON,
            ShapeKind.STAR, ShapeKind.HEART, ShapeKind.SPIRAL, ShapeKind.CROSS, ShapeKind.ARROW_BLOCK,
        ) + SolidShapes,
    ),
    ART_LANDSCAPE(
        CanvasKind.ART,
        listOf(
            ShapeKind.SUN, ShapeKind.MOON, ShapeKind.CLOUD, ShapeKind.MOUNTAINS, ShapeKind.HILLS, ShapeKind.WAVES,
            ShapeKind.TREE, ShapeKind.PINE, ShapeKind.PALM, ShapeKind.GRASS, ShapeKind.FLOWER, ShapeKind.LEAF,
            ShapeKind.APPLE, ShapeKind.BIRD, ShapeKind.BUTTERFLY, ShapeKind.FISH, ShapeKind.HOUSE, ShapeKind.BOAT,
            ShapeKind.ROCK, ShapeKind.DROP,
        ),
        // v17: temel çizimler seçiciden kalktı (yerini çizim yardımcıları aldı); eski kayıtlar aynen açılır
        listed = false,
    ),
    ART_PORTRAIT(CanvasKind.ART, listOf(ShapeKind.FACE_GUIDE, ShapeKind.EYE, ShapeKind.LIPS, ShapeKind.NOSE), listed = false),
    NOTE_LINES(
        CanvasKind.NOTES,
        listOf(ShapeKind.LINE, ShapeKind.ARROW, ShapeKind.DOUBLE_ARROW, ShapeKind.ANGLE, ShapeKind.RIGHT_ANGLE, ShapeKind.PARALLEL_LINES),
    ),
    NOTE_PLANE(
        CanvasKind.NOTES,
        listOf(
            ShapeKind.TRIANGLE, ShapeKind.RIGHT_TRIANGLE, ShapeKind.EQUILATERAL_TRIANGLE, ShapeKind.SQUARE, ShapeKind.RECTANGLE,
            ShapeKind.PARALLELOGRAM, ShapeKind.TRAPEZOID, ShapeKind.DIAMOND, ShapeKind.PENTAGON, ShapeKind.HEXAGON,
            ShapeKind.OCTAGON, ShapeKind.CIRCLE, ShapeKind.CIRCLE_RADIUS, ShapeKind.SEMICIRCLE, ShapeKind.ELLIPSE,
            ShapeKind.SECTOR, ShapeKind.ARC, ShapeKind.ANNULUS, ShapeKind.KITE, ShapeKind.RIGHT_TRAPEZOID,
            ShapeKind.TRIANGLE_HEIGHT, ShapeKind.CROSS,
        ),
    ),
    NOTE_SOLIDS(
        CanvasKind.NOTES,
        listOf(
            ShapeKind.CUBE, ShapeKind.PRISM, ShapeKind.TRIANGULAR_PRISM, ShapeKind.HEXAGONAL_PRISM, ShapeKind.CYLINDER,
            ShapeKind.CONE, ShapeKind.TRUNCATED_CONE, ShapeKind.SPHERE, ShapeKind.HEMISPHERE, ShapeKind.PYRAMID,
            ShapeKind.TETRAHEDRON, ShapeKind.CUBE_NET,
        ) + SolidShapes,
    ),
    NOTE_GRAPHS(
        CanvasKind.NOTES,
        listOf(
            ShapeKind.AXES, ShapeKind.FUNCTION_PLOT, ShapeKind.COORD_GRID, ShapeKind.AXES_3D, ShapeKind.UNIT_CIRCLE,
            ShapeKind.NUMBER_LINE, ShapeKind.GRID, ShapeKind.PARABOLA, ShapeKind.SINE,
            ShapeKind.BAR_CHART, ShapeKind.LINE_CHART, ShapeKind.PIE_CHART, ShapeKind.SCATTER,
        ),
    ),
    /**
     * v42 gelişmiş ve basit resim tuvallerinin temel şekilleri (azaltıldı: 14). Öteki genel şekiller (oklar,
     * yıldızlar, afişler, akış şeması, konuşma kutuları, semboller) kütüphanede ([com.karapp.drawguidance.content.library.general.GeneralShapes]);
     * nesne çizimleri resim tuvallerinin seçicisinde yok.
     */
    ART_GENERAL(
        CanvasKind.ART,
        listOf(
            ShapeKind.LINE, ShapeKind.ARROW, ShapeKind.DOUBLE_ARROW, ShapeKind.CIRCLE, ShapeKind.ELLIPSE, ShapeKind.SQUARE,
            ShapeKind.RECTANGLE, ShapeKind.ROUNDED_RECT, ShapeKind.TRIANGLE, ShapeKind.RIGHT_TRIANGLE, ShapeKind.DIAMOND,
            ShapeKind.PENTAGON, ShapeKind.HEXAGON, ShapeKind.HEART,
        ),
        listed = false,
    ),
    /** v33 basit resim: temel şekiller; v34 genişledi (18). v42: seçicide yok (yerine [ART_GENERAL]). */
    SIMPLE_ART_BASIC(
        CanvasKind.ART,
        listOf(
            ShapeKind.LINE, ShapeKind.ARROW, ShapeKind.DOUBLE_ARROW, ShapeKind.CIRCLE, ShapeKind.ELLIPSE, ShapeKind.SQUARE,
            ShapeKind.RECTANGLE, ShapeKind.ROUNDED_RECT, ShapeKind.TRIANGLE, ShapeKind.DIAMOND, ShapeKind.PENTAGON,
            ShapeKind.HEXAGON, ShapeKind.STAR, ShapeKind.HEART, ShapeKind.SPIRAL, ShapeKind.CROSS, ShapeKind.ARROW_BLOCK,
            ShapeKind.SEMICIRCLE,
        ),
        listed = false,
    ),
    /** v34 basit resim: doğa ve gökyüzü (14). */
    SIMPLE_ART_NATURE(
        CanvasKind.ART,
        listOf(
            ShapeKind.SUN, ShapeKind.MOON, ShapeKind.CLOUD, ShapeKind.DROP, ShapeKind.MOUNTAINS, ShapeKind.HILLS,
            ShapeKind.WAVES, ShapeKind.TREE, ShapeKind.PINE, ShapeKind.PALM, ShapeKind.GRASS, ShapeKind.FLOWER,
            ShapeKind.LEAF, ShapeKind.ROCK,
        ),
        listed = false,
    ),
    /** v34 basit resim: canlılar, eşyalar ve gölgeli cisimler (10). */
    SIMPLE_ART_THINGS(
        CanvasKind.ART,
        listOf(
            ShapeKind.BIRD, ShapeKind.BUTTERFLY, ShapeKind.FISH, ShapeKind.APPLE, ShapeKind.HOUSE, ShapeKind.BOAT,
            ShapeKind.SOLID_CUBE, ShapeKind.SOLID_SPHERE, ShapeKind.SOLID_CYLINDER, ShapeKind.SOLID_CONE,
        ),
        listed = false,
    ),
    /** v33 basit not alma: temel şekiller (+ koordinat ekseni); v34 genişledi (13 + 1). */
    SIMPLE_NOTE_BASIC(
        CanvasKind.NOTES,
        listOf(
            ShapeKind.LINE, ShapeKind.ARROW, ShapeKind.DOUBLE_ARROW, ShapeKind.CIRCLE, ShapeKind.ELLIPSE, ShapeKind.SQUARE,
            ShapeKind.RECTANGLE, ShapeKind.ROUNDED_RECT, ShapeKind.TRIANGLE, ShapeKind.DIAMOND, ShapeKind.STAR,
            ShapeKind.CROSS, ShapeKind.ARROW_BLOCK,
        ),
        listed = false,
    ),
    /** v34 basit not alma: düzlem geometri (17). */
    SIMPLE_NOTE_GEOMETRY(
        CanvasKind.NOTES,
        listOf(
            ShapeKind.RIGHT_TRIANGLE, ShapeKind.EQUILATERAL_TRIANGLE, ShapeKind.TRIANGLE_HEIGHT, ShapeKind.PARALLELOGRAM,
            ShapeKind.TRAPEZOID, ShapeKind.RIGHT_TRAPEZOID, ShapeKind.KITE, ShapeKind.PENTAGON, ShapeKind.HEXAGON,
            ShapeKind.OCTAGON, ShapeKind.SEMICIRCLE, ShapeKind.CIRCLE_RADIUS, ShapeKind.SECTOR, ShapeKind.ARC,
            ShapeKind.ANGLE, ShapeKind.RIGHT_ANGLE, ShapeKind.PARALLEL_LINES,
        ),
        listed = false,
    ),
    /** v34 basit not alma: cisimler ve ölçme (11). */
    SIMPLE_NOTE_SOLIDS(
        CanvasKind.NOTES,
        listOf(
            ShapeKind.CUBE, ShapeKind.PRISM, ShapeKind.TRIANGULAR_PRISM, ShapeKind.CYLINDER, ShapeKind.CONE,
            ShapeKind.SPHERE, ShapeKind.HEMISPHERE, ShapeKind.PYRAMID, ShapeKind.CUBE_NET, ShapeKind.NUMBER_LINE,
            ShapeKind.GRID,
        ),
        listed = false,
    ),
    ;

    companion object {
        fun forKind(kind: CanvasKind): List<ShapeCategory> = when (kind) {
            // v42 resim tuvalleri: azaltılmış temel şekiller (+ kütüphanedeki genel şekiller; nesneler yok)
            CanvasKind.SIMPLE_ART, CanvasKind.ART -> listOf(ART_GENERAL)
            CanvasKind.SIMPLE_NOTES -> listOf(SIMPLE_NOTE_BASIC, SIMPLE_NOTE_GEOMETRY, SIMPLE_NOTE_SOLIDS)
            // Çocuklar için yalnız çocuk şekilleri (kütüphanede)
            CanvasKind.KIDS -> emptyList()
            else -> entries.filter { it.kind == kind.base && it.listed }
        }
    }
}

/**
 * Şekillerin geometrisi. Şekiller, seçili fırçayla çizilen çizgilere (tuval koordinatlarında nokta listeleri)
 * dönüştürülür; böylece her fırça, silgi, seçim ve dönüşüm şekillerle de aynen çalışır.
 */
object ShapeCatalog {

    /** Sürüklenen iki noktadan şeklin yerleşeceği kutu ([ShapeFit.SQUARE] için kare). */
    fun frame(shape: ShapeKind, start: Offset, end: Offset): Rect = when (shape.fit) {
        ShapeFit.SQUARE -> {
            val side = max(abs(end.x - start.x), abs(end.y - start.y))
            val sx = if (end.x < start.x) -1f else 1f
            val sy = if (end.y < start.y) -1f else 1f
            val ex = start.x + side * sx
            val ey = start.y + side * sy
            Rect(min(start.x, ex), min(start.y, ey), max(start.x, ex), max(start.y, ey))
        }
        else -> Rect(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
    }

    /**
     * Şeklin çizgileri. [ink] çizgi kalınlığıdır (ok ucu, çentik ve kesik çizgi boyları buna göre ölçeklenir).
     * Kesik çizgiler parçalara ayrılmış olarak döner.
     */
    fun paths(
        shape: ShapeKind,
        start: Offset,
        end: Offset,
        ink: Float,
        expandDashes: Boolean = true,
        graph: com.karapp.drawguidance.data.model.GraphSpec? = null,
    ): List<ShapePath> {
        val raw = when {
            shape in GraphShapes.kinds -> GraphShapes.build(shape, frame(shape, start, end), ink, graph ?: com.karapp.drawguidance.data.model.GraphSpec())
            shape.fit == ShapeFit.LINE -> linePaths(shape, start, end, ink)
            else -> boxPaths(shape, frame(shape, start, end), ink)
        }
        return if (expandDashes) raw.flatMap { if (it.dashed) dashes(it, ink) else listOf(it) } else raw
    }

    /**
     * Fırça motoru noktaları eğrilerle yumuşattığı için köşeler ancak noktalar sık olursa keskin kalır:
     * çizgi [spacing] aralıklı noktalara bölünür (hız/basınç etkili fırçalarda da kalınlık düzgün olur).
     */
    fun densify(points: List<Offset>, spacing: Float = 4f): List<Offset> {
        if (points.size < 2) return points
        val out = ArrayList<Offset>(points.size * 2)
        out += points.first()
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val length = (b - a).getDistance()
            if (length < 0.5f) continue
            val steps = kotlin.math.ceil(length / spacing).toInt().coerceAtLeast(1)
            for (k in 1..steps) out += a + (b - a) * (k / steps.toFloat())
        }
        return out
    }

    // --- Çizgi şekilleri ---

    private fun linePaths(shape: ShapeKind, s: Offset, e: Offset, ink: Float): List<ShapePath> {
        val length = (e - s).getDistance()
        if (length < 1e-3f) return listOf(ShapePath(listOf(s, e)))
        val d = (e - s) / length
        val head = headSize(length, ink)
        return when (shape) {
            ShapeKind.ARROW -> listOf(ShapePath(listOf(s, e)), arrowHead(e, d, head))
            ShapeKind.DOUBLE_ARROW -> listOf(ShapePath(listOf(s, e)), arrowHead(e, d, head), arrowHead(s, -d, head))
            ShapeKind.NUMBER_LINE -> {
                val n = Offset(-d.y, d.x)
                val ticks = (1..9).map { i ->
                    val c = s + (e - s) * (i / 10f)
                    ShapePath(listOf(c - n * head * 0.45f, c + n * head * 0.45f))
                }
                listOf(ShapePath(listOf(s, e)), arrowHead(e, d, head), arrowHead(s, -d, head)) + ticks
            }
            else -> listOf(ShapePath(listOf(s, e)))
        }
    }

    private fun headSize(length: Float, ink: Float) = min(length * 0.3f, max(ink * 4f, min(length * 0.08f, 42f)))

    private fun arrowHead(tip: Offset, dir: Offset, size: Float): ShapePath {
        val n = Offset(-dir.y, dir.x)
        val back = tip - dir * size
        return ShapePath(listOf(back + n * size * 0.55f, tip, back - n * size * 0.55f))
    }

    // --- Kutu şekilleri ---

    /** Birim kareden ([0,1]²) kutuya eşleme. */
    private class Box(val r: Rect) {
        val w = max(r.width, 1e-3f)
        val h = max(r.height, 1e-3f)
        val m = min(w, h)
        fun p(u: Float, v: Float) = Offset(r.left + u * w, r.top + v * h)
        fun poly(vararg uv: Float, closed: Boolean = false, dashed: Boolean = false): ShapePath =
            ShapePath(List(uv.size / 2) { p(uv[it * 2], uv[it * 2 + 1]) }.let { if (closed) it + it.first() else it }, closed, dashed)

        /** Kutu biriminde elips yayı; örnek sayısı gerçek boyuta göre. */
        fun arc(cx: Float, cy: Float, rx: Float, ry: Float, from: Double, to: Double, closed: Boolean = false, dashed: Boolean = false): ShapePath {
            val perimeter = (abs(to - from) * (rx * w + ry * h) / 2.0).toFloat()
            val n = (perimeter / 5f).toInt().coerceIn(12, 720)
            val pts = List(n + 1) { i ->
                val a = from + (to - from) * i / n
                p(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
            }
            return ShapePath(pts, closed, dashed)
        }

        /** Parametrik eğri (t ∈ [0,1]) örnekleri. */
        fun curve(closed: Boolean = false, samples: Int = 0, f: (Float) -> Pair<Float, Float>): ShapePath {
            val n = if (samples > 0) samples else ((w + h) / 4f).toInt().coerceIn(48, 900)
            val pts = List(n + 1) { i -> f(i / n.toFloat()).let { (u, v) -> p(u, v) } }
            return ShapePath(pts, closed)
        }
    }

    private fun boxPaths(shape: ShapeKind, rect: Rect, ink: Float): List<ShapePath> {
        if (shape in ShapeSolids.kinds) return ShapeSolids.paths(shape, rect)
        val b = Box(rect)
        val full = 2 * PI
        return when (shape) {
            ShapeKind.CIRCLE, ShapeKind.ELLIPSE -> listOf(b.arc(0.5f, 0.5f, 0.5f, 0.5f, 0.0, full, closed = true))
            ShapeKind.SQUARE, ShapeKind.RECTANGLE -> listOf(b.poly(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, closed = true))
            ShapeKind.ROUNDED_RECT -> listOf(roundedRect(rect, b.m * 0.18f))
            ShapeKind.TRIANGLE -> listOf(b.poly(0.5f, 0f, 1f, 1f, 0f, 1f, closed = true))
            ShapeKind.RIGHT_TRIANGLE -> listOf(b.poly(0f, 0f, 1f, 1f, 0f, 1f, closed = true), rightAngleMark(b.p(0f, 1f), 1f, -1f, b.m * 0.12f))
            ShapeKind.EQUILATERAL_TRIANGLE -> {
                val top = 1f - sqrt(3f) / 2f
                listOf(b.poly(0.5f, top, 1f, 1f, 0f, 1f, closed = true))
            }
            ShapeKind.DIAMOND -> listOf(b.poly(0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, 0.5f, closed = true))
            ShapeKind.PARALLELOGRAM -> listOf(b.poly(0.25f, 0f, 1f, 0f, 0.75f, 1f, 0f, 1f, closed = true))
            ShapeKind.TRAPEZOID -> listOf(b.poly(0.25f, 0f, 0.75f, 0f, 1f, 1f, 0f, 1f, closed = true))
            ShapeKind.PENTAGON -> listOf(regular(b, 5))
            ShapeKind.HEXAGON -> listOf(regular(b, 6))
            ShapeKind.OCTAGON -> listOf(regular(b, 8, PI / 8))
            ShapeKind.STAR -> listOf(star(b))
            ShapeKind.HEART -> listOf(normalized(b, closed = true) { t ->
                val a = t * 2 * PI
                val x = 16 * sin(a).pow(3)
                val y = 13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)
                x.toFloat() to (-y).toFloat()
            })
            ShapeKind.SPIRAL -> listOf(b.curve { t ->
                val turns = 3.0
                val a = t * turns * 2 * PI
                val r = 0.5f * t
                (0.5f + r * cos(a).toFloat()) to (0.5f + r * sin(a).toFloat())
            })
            ShapeKind.SEMICIRCLE -> listOf(
                ShapePath(b.arc(0.5f, 1f, 0.5f, 1f, PI, full).points.let { it + it.first() }, closed = true),
            )
            ShapeKind.CIRCLE_RADIUS -> listOf(
                b.arc(0.5f, 0.5f, 0.5f, 0.5f, 0.0, full, closed = true),
                ShapePath(listOf(b.p(0.5f, 0.5f), b.p(1f, 0.5f))),
                dot(b.p(0.5f, 0.5f), max(ink * 0.9f, b.m * 0.012f)),
            )

            ShapeKind.SUN -> buildList {
                add(b.arc(0.5f, 0.5f, 0.27f, 0.27f, 0.0, full, closed = true))
                for (i in 0 until 12) {
                    val a = i * PI / 6
                    val c = cos(a).toFloat(); val s = sin(a).toFloat()
                    add(ShapePath(listOf(b.p(0.5f + 0.35f * c, 0.5f + 0.35f * s), b.p(0.5f + 0.5f * c, 0.5f + 0.5f * s))))
                }
            }
            ShapeKind.MOON -> listOf(
                crescent(b),
                b.arc(0.30f, 0.34f, 0.055f, 0.055f, 0.0, full, closed = true),
                b.arc(0.22f, 0.58f, 0.038f, 0.038f, 0.0, full, closed = true),
                b.arc(0.35f, 0.72f, 0.028f, 0.028f, 0.0, full, closed = true),
            )
            ShapeKind.CLOUD -> listOf(normalized(b, closed = true, samples = 360) { t ->
                val a = t * 2 * PI
                val g = 0.78 + 0.22 * abs(sin(3.5 * a))
                val x = cos(a) * g * 1.5
                var y = sin(a) * g
                if (y > 0.45) y = 0.45 + (y - 0.45) * 0.15 // düz taban
                x.toFloat() to y.toFloat()
            })
            ShapeKind.MOUNTAINS -> listOf(
                b.poly(0f, 1f, 0.22f, 0.35f, 0.38f, 0.6f, 0.58f, 0.1f, 0.78f, 0.55f, 0.88f, 0.4f, 1f, 1f),
                b.poly(0.5f, 0.3f, 0.54f, 0.37f, 0.58f, 0.31f, 0.62f, 0.38f, 0.669f, 0.3f),
            )
            ShapeKind.HILLS -> listOf(
                b.curve { t -> val u = t * 0.65f; u to (1f - 0.6f * sin(PI * t).toFloat()) },
                b.curve { t -> val u = 0.35f + t * 0.65f; u to (1f - 0.42f * sin(PI * t).toFloat()) },
                b.poly(0f, 1f, 1f, 1f),
            )
            ShapeKind.WAVES -> listOf(0.2f, 0.5f, 0.8f).map { v0 ->
                b.curve { t -> t to (v0 + 0.1f * sin(2 * PI * 2.5 * t).toFloat()) }
            }
            ShapeKind.TREE -> listOf(
                // Tabana doğru genişleyen kapalı gövde, iki dal ve dalgalı taç
                b.poly(0.36f, 1f, 0.43f, 0.76f, 0.455f, 0.52f, 0.545f, 0.52f, 0.57f, 0.76f, 0.64f, 1f, closed = true),
                b.poly(0.455f, 0.68f, 0.33f, 0.56f),
                b.poly(0.545f, 0.72f, 0.67f, 0.62f),
                normalizedInto(b, Rect(0.04f, 0.02f, 0.96f, 0.60f), closed = true, samples = 240) { t ->
                    val a = t * 2 * PI
                    val g = 0.86 + 0.14 * abs(sin(3.5 * a))
                    (cos(a) * g).toFloat() to (sin(a) * g * 0.92).toFloat()
                },
                // Taç içindeki yaprak kümesi çizgileri (hacim hissi, asimetrik)
                b.curve { t -> quad(0.16f, 0.30f, 0.30f, 0.40f, 0.40f, 0.26f, t) },
                b.curve { t -> quad(0.44f, 0.42f, 0.58f, 0.52f, 0.74f, 0.38f, t) },
                b.curve { t -> quad(0.60f, 0.18f, 0.72f, 0.30f, 0.86f, 0.24f, t) },
            )
            ShapeKind.PINE -> listOf(
                b.poly(0.5f, 0f, 0.78f, 0.34f, 0.22f, 0.34f, closed = true),
                b.poly(0.5f, 0.2f, 0.88f, 0.6f, 0.12f, 0.6f, closed = true),
                b.poly(0.5f, 0.45f, 1f, 0.88f, 0f, 0.88f, closed = true),
                b.poly(0.44f, 0.88f, 0.44f, 1f, 0.56f, 1f, 0.56f, 0.88f),
            )
            ShapeKind.FLOWER -> buildList {
                add(b.arc(0.5f, 0.5f, 0.12f, 0.12f, 0.0, full, closed = true))
                for (i in 0 until 6) add(petal(b, i * PI / 3))
                // Göbekteki tohum noktaları
                for (i in 0 until 6) {
                    val a = i * PI / 3 + 0.5
                    add(b.arc((0.5 + 0.06 * cos(a)).toFloat(), (0.5 + 0.06 * sin(a)).toFloat(), 0.015f, 0.015f, 0.0, full, closed = true))
                }
            }
            ShapeKind.LEAF -> {
                val a = Offset(0.05f, 0.95f); val c = Offset(0.95f, 0.05f)
                val n = Offset(0.7071f, 0.7071f)
                fun side(sign: Float) = b.curve { t ->
                    val bulge = 0.22f * sin(PI * t).toFloat() * sign
                    (a.x + (c.x - a.x) * t + n.x * bulge) to (a.y + (c.y - a.y) * t + n.y * bulge)
                }
                // Orta damar, sap ve yan damarlar
                // Yan damarlar orta damardan kenara doğru, hafif eğimli
                val veins = (1..5).flatMap { i ->
                    val t = i / 6f
                    val reach = 0.17f * sin(PI * t).toFloat()
                    val mx = a.x + (c.x - a.x) * t
                    val my = a.y + (c.y - a.y) * t
                    val fwd = 0.06f
                    listOf(
                        b.poly(mx, my, mx + n.x * reach + (c.x - a.x) * 0.01f + fwd * 0.3f, my + n.y * reach - fwd * 0.3f),
                        b.poly(mx, my, mx - n.x * reach + fwd * 0.3f, my - n.y * reach + (c.y - a.y) * 0.01f - fwd * 0.3f),
                    )
                }
                listOf(side(1f), side(-1f), b.poly(0.02f, 0.98f, 0.95f, 0.05f)) + veins
            }
            ShapeKind.BIRD -> listOf(
                // Yandan kuş: gövde, baş, gaga, kanat, kuyruk ve bacaklar
                bezierPath(
                    b, true,
                    floatArrayOf(0.30f, 0.62f, 0.33f, 0.44f, 0.54f, 0.42f),
                    floatArrayOf(0.54f, 0.42f, 0.72f, 0.44f, 0.75f, 0.58f),
                    floatArrayOf(0.75f, 0.58f, 0.76f, 0.80f, 0.52f, 0.84f),
                    floatArrayOf(0.52f, 0.84f, 0.33f, 0.84f, 0.30f, 0.62f),
                ),
                b.arc(0.79f, 0.33f, 0.105f, 0.105f, 0.0, full, closed = true),
                b.curve { t -> quad(0.70f, 0.42f, 0.71f, 0.38f, 0.70f, 0.34f, t) },
                b.poly(0.885f, 0.30f, 1f, 0.36f, 0.885f, 0.41f, closed = true),
                b.arc(0.815f, 0.30f, 0.022f, 0.022f, 0.0, full, closed = true),
                bezierPath(
                    b, true,
                    floatArrayOf(0.40f, 0.56f, 0.52f, 0.48f, 0.66f, 0.56f),
                    floatArrayOf(0.66f, 0.56f, 0.58f, 0.73f, 0.42f, 0.69f),
                    floatArrayOf(0.42f, 0.69f, 0.36f, 0.63f, 0.40f, 0.56f),
                ),
                b.poly(0.31f, 0.58f, 0.04f, 0.50f, 0.08f, 0.70f, 0.31f, 0.73f, closed = true),
                b.poly(0.50f, 0.84f, 0.48f, 0.95f, 0.42f, 0.98f),
                b.poly(0.61f, 0.82f, 0.63f, 0.94f, 0.57f, 0.97f),
            )
            ShapeKind.HOUSE -> listOf(
                b.poly(0.12f, 0.45f, 0.88f, 0.45f, 0.88f, 1f, 0.12f, 1f, closed = true),
                b.poly(0f, 0.5f, 0.5f, 0.06f, 1f, 0.5f, closed = true),
                b.poly(0.5f, 0.06f, 0.5f, 0.45f, dashed = true),
                // Baca
                b.poly(0.72f, 0.28f, 0.72f, 0.10f, 0.80f, 0.10f, 0.80f, 0.345f),
                b.poly(0.70f, 0.10f, 0.82f, 0.10f, 0.82f, 0.05f, 0.70f, 0.05f, closed = true),
                // Kapı ve tokmağı
                b.poly(0.42f, 1f, 0.42f, 0.68f, 0.58f, 0.68f, 0.58f, 1f),
                b.arc(0.55f, 0.85f, 0.015f, 0.015f, 0.0, full, closed = true),
                // Bölmeli pencere
                b.poly(0.18f, 0.56f, 0.34f, 0.56f, 0.34f, 0.74f, 0.18f, 0.74f, closed = true),
                b.poly(0.26f, 0.56f, 0.26f, 0.74f),
                b.poly(0.18f, 0.65f, 0.34f, 0.65f),
                b.poly(0.66f, 0.56f, 0.82f, 0.56f, 0.82f, 0.74f, 0.66f, 0.74f, closed = true),
                b.poly(0.74f, 0.56f, 0.74f, 0.74f),
                b.poly(0.66f, 0.65f, 0.82f, 0.65f),
            )
            ShapeKind.DROP -> listOf(normalized(b, closed = true) { t ->
                val a = t * 2 * PI
                (sin(a) * sin(a / 2)).toFloat() to (-cos(a)).toFloat()
            })

            ShapeKind.FACE_GUIDE -> listOf(
                b.curve(closed = true) { t ->
                    val a = t * 2 * PI
                    val lower = max(0.0, sin(a))
                    val rx = 0.5 * (1 - 0.22 * lower * lower)
                    (0.5f + (rx * cos(a)).toFloat()) to (0.5f + 0.5f * sin(a).toFloat())
                },
                ShapePath(listOf(b.p(0.5f, 0f), b.p(0.5f, 1f)), dashed = true),
                ShapePath(listOf(b.p(0.02f, 0.47f), b.p(0.98f, 0.47f)), dashed = true),
                ShapePath(listOf(b.p(0.33f, 0.68f), b.p(0.67f, 0.68f)), dashed = true),
                ShapePath(listOf(b.p(0.36f, 0.8f), b.p(0.64f, 0.8f)), dashed = true),
            )
            ShapeKind.EYE -> listOf(
                b.curve { t -> t to (0.5f - 0.42f * sin(PI * t).toFloat()) },
                b.curve { t -> t to (0.5f + 0.30f * sin(PI * t).toFloat()) },
                circleIn(rect.center + Offset(0f, b.h * 0.04f), min(b.h * 0.30f, b.w * 0.19f)),
                circleIn(rect.center + Offset(0f, b.h * 0.04f), min(b.h * 0.11f, b.w * 0.07f)),
                // Işık noktası göz bebeğinin içinde kalır
                circleIn(
                    rect.center + Offset(-min(b.h * 0.11f, b.w * 0.07f) * 0.38f, b.h * 0.04f - min(b.h * 0.11f, b.w * 0.07f) * 0.38f),
                    min(b.h * 0.11f, b.w * 0.07f) * 0.38f,
                ),
                b.curve { t -> (0.08f + 0.84f * t) to (0.22f - 0.16f * sin(PI * t).toFloat()) },
            ) + (1..4).map { i ->
                // Üst kapak kirpikleri (kutunun dışına taşmaz)
                val t = 0.16f + i * 0.17f
                val y = 0.5f - 0.42f * sin(PI * t).toFloat()
                b.poly(t, y, max(0.02f, t - 0.045f), max(0.02f, y - 0.09f))
            }
            ShapeKind.LIPS -> listOf(
                b.curve { t ->
                    // Üst dudak: iki tepe ve ortada çukur
                    val y = 0.5 - 0.38 * sin(PI * t) + 0.22 * exp1(t)
                    t to y.toFloat()
                },
                b.curve { t -> t to (0.5f + 0.5f * sin(PI * t).toFloat()) },
                b.curve { t -> t to (0.5f + 0.06f * sin(PI * t).toFloat()) },
            )
            ShapeKind.NOSE -> listOf(
                b.curve { t -> quad(0.46f, 0.02f, 0.40f, 0.34f, 0.40f, 0.62f, t) },
                b.curve { t -> quad(0.40f, 0.62f, 0.42f, 0.80f, 0.56f, 0.80f, t) },
                b.curve { t -> quad(0.22f, 0.70f, 0.30f, 0.98f, 0.50f, 0.88f, t) },
                b.curve { t -> quad(0.50f, 0.88f, 0.70f, 0.98f, 0.78f, 0.70f, t) },
                // Burun delikleri
                b.arc(0.31f, 0.80f, 0.045f, 0.028f, 0.0, full, closed = true),
                b.arc(0.69f, 0.80f, 0.045f, 0.028f, 0.0, full, closed = true),
                b.poly(0.60f, 0.06f, 0.62f, 0.30f, dashed = true),
            )

            ShapeKind.ANGLE -> {
                val v = b.p(0f, 1f)
                val a = b.p(1f, 1f)
                val c = b.p(0.72f, 0f)
                listOf(ShapePath(listOf(a, v, c)), angleArc(v, a, c, b.m * 0.28f))
            }
            ShapeKind.RIGHT_ANGLE -> listOf(b.poly(1f, 1f, 0f, 1f, 0f, 0f), rightAngleMark(b.p(0f, 1f), 1f, -1f, b.m * 0.14f))
            ShapeKind.PARALLEL_LINES -> {
                val head = max(ink * 3f, b.m * 0.08f)
                listOf(0.3f, 0.7f).flatMap { v ->
                    val mid = b.p(0.5f, v)
                    listOf(
                        ShapePath(listOf(b.p(0f, v), b.p(1f, v))),
                        ShapePath(listOf(mid + Offset(-head * 0.5f, -head * 0.5f), mid + Offset(head * 0.2f, 0f), mid + Offset(-head * 0.5f, head * 0.5f))),
                    )
                }
            }
            ShapeKind.CUBE -> box3d(b, 0.3f)
            ShapeKind.PRISM -> box3d(b, 0.25f)
            ShapeKind.CYLINDER -> listOf(
                b.arc(0.5f, 0.12f, 0.5f, 0.12f, 0.0, full, closed = true),
                b.poly(0f, 0.12f, 0f, 0.88f),
                b.poly(1f, 0.12f, 1f, 0.88f),
                b.arc(0.5f, 0.88f, 0.5f, 0.12f, 0.0, PI),
                b.arc(0.5f, 0.88f, 0.5f, 0.12f, PI, full, dashed = true),
            )
            ShapeKind.CONE -> listOf(
                b.poly(0f, 0.88f, 0.5f, 0f, 1f, 0.88f),
                b.arc(0.5f, 0.88f, 0.5f, 0.12f, 0.0, PI),
                b.arc(0.5f, 0.88f, 0.5f, 0.12f, PI, full, dashed = true),
            )
            ShapeKind.SPHERE -> listOf(
                b.arc(0.5f, 0.5f, 0.5f, 0.5f, 0.0, full, closed = true),
                b.arc(0.5f, 0.5f, 0.5f, 0.15f, 0.0, PI),
                b.arc(0.5f, 0.5f, 0.5f, 0.15f, PI, full, dashed = true),
            )
            ShapeKind.PYRAMID -> listOf(
                b.poly(0f, 0.85f, 0.68f, 1f, 1f, 0.8f),
                b.poly(0f, 0.85f, 0.5f, 0f, 0.68f, 1f),
                b.poly(0.5f, 0f, 1f, 0.8f),
                b.poly(0f, 0.85f, 0.32f, 0.66f, 1f, 0.8f, dashed = true),
                b.poly(0.5f, 0f, 0.32f, 0.66f, dashed = true),
            )
            ShapeKind.AXES -> {
                val head = headSize(min(b.w, b.h), ink)
                val cx = b.p(0.5f, 0.5f)
                buildList {
                    add(ShapePath(listOf(b.p(0f, 0.5f), b.p(1f, 0.5f))))
                    add(ShapePath(listOf(b.p(0.5f, 1f), b.p(0.5f, 0f))))
                    add(arrowHead(b.p(1f, 0.5f), Offset(1f, 0f), head))
                    add(arrowHead(b.p(0.5f, 0f), Offset(0f, -1f), head))
                    val tick = head * 0.4f
                    for (i in 1..4) {
                        for (sign in listOf(-1f, 1f)) {
                            val x = cx.x + sign * i * b.w * 0.1f
                            val y = cx.y + sign * i * b.h * 0.1f
                            add(ShapePath(listOf(Offset(x, cx.y - tick), Offset(x, cx.y + tick))))
                            add(ShapePath(listOf(Offset(cx.x - tick, y), Offset(cx.x + tick, y))))
                        }
                    }
                }
            }
            ShapeKind.GRID -> {
                val cells = 8
                val cell = b.w / cells
                val rows = (b.h / cell).toInt().coerceAtLeast(1)
                val height = cell * rows
                buildList {
                    for (i in 0..cells) add(ShapePath(listOf(Offset(rect.left + i * cell, rect.top), Offset(rect.left + i * cell, rect.top + height))))
                    for (j in 0..rows) add(ShapePath(listOf(Offset(rect.left, rect.top + j * cell), Offset(rect.right, rect.top + j * cell))))
                }
            }
            // Tepe noktası altta, kollar yukarıda (y = x²)
            ShapeKind.PARABOLA -> listOf(b.curve { t -> val x = t * 2 - 1; t to (1f - x * x) })
            ShapeKind.SINE -> listOf(b.curve { t -> t to (0.5f - 0.5f * sin(2 * PI * 2 * t).toFloat()) })

            ShapeKind.SECTOR -> listOf(
                ShapePath(b.arc(0.5f, 0.5f, 0.5f, 0.5f, -PI / 2, PI / 6).points.let { listOf(b.p(0.5f, 0.5f)) + it + b.p(0.5f, 0.5f) }, closed = true),
            )
            ShapeKind.ARC -> listOf(b.arc(0.5f, 0.5f, 0.5f, 0.5f, PI, 2 * PI))
            ShapeKind.ANNULUS -> listOf(
                b.arc(0.5f, 0.5f, 0.5f, 0.5f, 0.0, full, closed = true),
                b.arc(0.5f, 0.5f, 0.28f, 0.28f, 0.0, full, closed = true),
            )
            ShapeKind.KITE -> listOf(b.poly(0.5f, 0f, 1f, 0.38f, 0.5f, 1f, 0f, 0.38f, closed = true))
            ShapeKind.RIGHT_TRAPEZOID -> listOf(b.poly(0f, 0f, 0.62f, 0f, 1f, 1f, 0f, 1f, closed = true))
            ShapeKind.TRIANGLE_HEIGHT -> listOf(
                b.poly(0.35f, 0f, 1f, 1f, 0f, 1f, closed = true),
                b.poly(0.35f, 0f, 0.35f, 1f, dashed = true),
                rightAngleMark(b.p(0.35f, 1f), 1f, -1f, b.m * 0.1f),
            )
            ShapeKind.CROSS -> listOf(b.poly(0.35f, 0f, 0.65f, 0f, 0.65f, 0.35f, 1f, 0.35f, 1f, 0.65f, 0.65f, 0.65f, 0.65f, 1f, 0.35f, 1f, 0.35f, 0.65f, 0f, 0.65f, 0f, 0.35f, 0.35f, 0.35f, closed = true))
            ShapeKind.ARROW_BLOCK -> listOf(b.poly(0f, 0.3f, 0.6f, 0.3f, 0.6f, 0.05f, 1f, 0.5f, 0.6f, 0.95f, 0.6f, 0.7f, 0f, 0.7f, closed = true))

            ShapeKind.TRIANGULAR_PRISM -> listOf(
                b.poly(0.3f, 0.12f, 0.6f, 0.72f, 0f, 0.72f, closed = true),
                b.poly(0.3f, 0.12f, 0.7f, 0.28f),
                b.poly(0.6f, 0.72f, 1f, 0.88f),
                b.poly(0.7f, 0.28f, 1f, 0.88f),
                b.poly(0f, 0.72f, 0.4f, 0.88f, dashed = true),
                b.poly(0.4f, 0.88f, 1f, 0.88f, dashed = true),
                b.poly(0.4f, 0.88f, 0.7f, 0.28f, dashed = true),
            )
            ShapeKind.HEXAGONAL_PRISM -> buildList {
                val top = (0..5).map { i ->
                    val a = -PI / 2 + i * PI / 3
                    0.5f + 0.32f * cos(a).toFloat() to 0.22f + 0.16f * sin(a).toFloat()
                }
                val bottom = top.map { it.first to it.second + 0.52f }
                add(ShapePath(top.map { b.p(it.first, it.second) } + b.p(top[0].first, top[0].second), closed = true))
                add(ShapePath(bottom.map { b.p(it.first, it.second) }.let { it.subList(0, 4) }))
                add(ShapePath(bottom.map { b.p(it.first, it.second) }.let { listOf(it[3]) + it.subList(4, 6) + it[0] }, dashed = true))
                for (i in listOf(0, 2, 3, 5)) add(ShapePath(listOf(b.p(top[i].first, top[i].second), b.p(bottom[i].first, bottom[i].second)), dashed = i == 5))
            }
            ShapeKind.TETRAHEDRON -> listOf(
                b.poly(0.5f, 0.05f, 0.95f, 0.85f, 0.05f, 0.85f, closed = true),
                b.poly(0.5f, 0.05f, 0.62f, 0.62f),
                b.poly(0.05f, 0.85f, 0.62f, 0.62f, dashed = true),
                b.poly(0.95f, 0.85f, 0.62f, 0.62f, dashed = true),
            )
            ShapeKind.TRUNCATED_CONE -> listOf(
                b.arc(0.5f, 0.15f, 0.26f, 0.08f, 0.0, full, closed = true),
                b.poly(0.24f, 0.15f, 0.06f, 0.85f),
                b.poly(0.76f, 0.15f, 0.94f, 0.85f),
                b.arc(0.5f, 0.85f, 0.44f, 0.12f, 0.0, PI),
                b.arc(0.5f, 0.85f, 0.44f, 0.12f, PI, full, dashed = true),
            )
            ShapeKind.HEMISPHERE -> listOf(
                b.arc(0.5f, 0.78f, 0.5f, 0.62f, PI, full),
                b.arc(0.5f, 0.78f, 0.5f, 0.14f, 0.0, PI),
                b.arc(0.5f, 0.78f, 0.5f, 0.14f, PI, full, dashed = true),
            )
            ShapeKind.CUBE_NET -> buildList {
                val c = 0.25f
                // Haç biçimli küp açınımı
                add(b.poly(c, 0f, 2 * c, 0f, 2 * c, c, c, c, closed = true))
                add(b.poly(0f, c, c, c, c, 2 * c, 0f, 2 * c, closed = true))
                add(b.poly(c, c, 2 * c, c, 2 * c, 2 * c, c, 2 * c, closed = true))
                add(b.poly(2 * c, c, 3 * c, c, 3 * c, 2 * c, 2 * c, 2 * c, closed = true))
                add(b.poly(3 * c, c, 4 * c, c, 4 * c, 2 * c, 3 * c, 2 * c, closed = true))
                add(b.poly(c, 2 * c, 2 * c, 2 * c, 2 * c, 3 * c, c, 3 * c, closed = true))
            }

            ShapeKind.BUTTERFLY -> {
                // Üst kanat: gövdeden çıkıp dışa açılan, ucu sivri; alt kanat daha küçük ve yuvarlak
                val upper = arrayOf(
                    floatArrayOf(0.475f, 0.20f, 0.30f, 0.03f, 0.10f, 0.14f),
                    floatArrayOf(0.10f, 0.14f, 0.00f, 0.33f, 0.20f, 0.44f),
                    floatArrayOf(0.20f, 0.44f, 0.38f, 0.50f, 0.475f, 0.40f),
                    floatArrayOf(0.475f, 0.40f, 0.475f, 0.30f, 0.475f, 0.20f),
                )
                val lower = arrayOf(
                    floatArrayOf(0.475f, 0.46f, 0.28f, 0.50f, 0.19f, 0.66f),
                    floatArrayOf(0.19f, 0.66f, 0.10f, 0.86f, 0.30f, 0.90f),
                    floatArrayOf(0.30f, 0.90f, 0.44f, 0.86f, 0.475f, 0.68f),
                    floatArrayOf(0.475f, 0.68f, 0.475f, 0.57f, 0.475f, 0.46f),
                )
                listOf(
                    bezierPath(b, true, *upper),
                    mirrored(b, true, *upper),
                    bezierPath(b, true, *lower),
                    mirrored(b, true, *lower),
                    // Gövde (incelen mekik), baş ve antenler
                    normalizedInto(b, Rect(0.462f, 0.13f, 0.538f, 0.93f), closed = true, samples = 72) { t ->
                        val a = t * 2 * PI
                        (sin(a) * (0.35 + 0.65 * abs(sin(a / 2)))).toFloat() to (-cos(a)).toFloat()
                    },
                    b.arc(0.5f, 0.115f, 0.042f, 0.042f, 0.0, full, closed = true),
                    b.curve { t -> quad(0.482f, 0.09f, 0.40f, 0.04f, 0.31f, 0.015f, t) },
                    b.curve { t -> quad(0.518f, 0.09f, 0.60f, 0.04f, 0.69f, 0.015f, t) },
                    // Kanat benekleri
                    b.arc(0.235f, 0.25f, 0.05f, 0.055f, 0.0, full, closed = true),
                    b.arc(0.765f, 0.25f, 0.05f, 0.055f, 0.0, full, closed = true),
                    b.arc(0.30f, 0.735f, 0.032f, 0.036f, 0.0, full, closed = true),
                    b.arc(0.70f, 0.735f, 0.032f, 0.036f, 0.0, full, closed = true),
                )
            }
            ShapeKind.FISH -> listOf(
                b.curve(closed = true) { t ->
                    val a = t * 2 * PI
                    (0.45f + 0.42f * cos(a).toFloat()) to (0.5f + 0.27f * sin(a).toFloat() * (0.6f + 0.4f * cos(a / 2).toFloat()))
                },
                b.poly(0.86f, 0.5f, 1f, 0.28f, 1f, 0.72f, closed = true),
                b.arc(0.25f, 0.44f, 0.05f, 0.05f, 0.0, full, closed = true),
                b.curve { t -> quad(0.45f, 0.24f, 0.6f, 0.08f, 0.72f, 0.33f, t) },
                b.curve { t -> quad(0.45f, 0.76f, 0.6f, 0.92f, 0.72f, 0.67f, t) },
            )
            ShapeKind.PALM -> listOf(
                b.curve { t -> quad(0.45f, 1f, 0.52f, 0.62f, 0.5f, 0.32f, t) },
                b.curve { t -> quad(0.55f, 1f, 0.62f, 0.62f, 0.56f, 0.32f, t) },
            ) + (0..5).map { i ->
                val a = PI * (0.15 + i * 0.14)
                b.curve { t -> quad(0.52f, 0.3f, (0.52 + 0.3 * cos(a)).toFloat(), (0.3 - 0.22 * sin(a)).toFloat(), (0.52 + 0.46 * cos(a)).toFloat(), (0.34 - 0.1 * sin(a)).toFloat(), t) }
            }
            ShapeKind.GRASS -> (0..8).map { i ->
                // Hepsi aynı yöne hafifçe yatan, boyları değişen çim bıçakları (birbirini kesmez)
                val x = 0.05f + i * 0.1125f
                val lean = 0.035f + (i % 3) * 0.012f
                val top = 0.14f + (i % 3) * 0.13f
                val tipX = (x + lean * 2.2f).coerceAtMost(0.99f)
                b.curve { t -> quad(x, 1f, x + lean * 0.9f, (1f + top) * 0.5f, tipX, top, t) }
            } + listOf(b.poly(0.02f, 1f, 0.98f, 1f))
            ShapeKind.ROCK -> listOf(
                b.poly(0.05f, 1f, 0.2f, 0.45f, 0.45f, 0.2f, 0.72f, 0.3f, 0.95f, 0.7f, 0.98f, 1f, closed = true),
                b.poly(0.45f, 0.2f, 0.52f, 0.62f, 0.95f, 0.7f),
                b.poly(0.52f, 0.62f, 0.3f, 1f),
            )
            ShapeKind.BOAT -> listOf(
                b.poly(0.05f, 0.7f, 0.95f, 0.7f, 0.8f, 0.95f, 0.2f, 0.95f, closed = true),
                b.poly(0.5f, 0.7f, 0.5f, 0.12f),
                b.poly(0.5f, 0.16f, 0.9f, 0.62f, 0.5f, 0.62f, closed = true),
                b.poly(0.46f, 0.62f, 0.14f, 0.62f, 0.46f, 0.2f, closed = true),
            )
            ShapeKind.APPLE -> listOf(
                b.curve(closed = true) { t ->
                    val a = t * 2 * PI
                    val r = 0.42 + 0.05 * cos(2 * a)
                    (0.5 + r * cos(a)).toFloat() to (0.55 + r * 0.95 * sin(a) - 0.06 * cos(a) * cos(a)).toFloat()
                },
                b.curve { t -> quad(0.5f, 0.16f, 0.53f, 0.05f, 0.62f, 0.02f, t) },
                b.curve(closed = true) { t -> leafAt(t) },
            )

            ShapeKind.LINE, ShapeKind.ARROW, ShapeKind.DOUBLE_ARROW, ShapeKind.NUMBER_LINE ->
                linePaths(shape, rect.topLeft, rect.bottomRight, ink)
            ShapeKind.AXES_3D, ShapeKind.FUNCTION_PLOT, ShapeKind.COORD_GRID, ShapeKind.UNIT_CIRCLE,
            ShapeKind.BAR_CHART, ShapeKind.LINE_CHART, ShapeKind.PIE_CHART, ShapeKind.SCATTER ->
                GraphShapes.build(shape, rect, ink, com.karapp.drawguidance.data.model.GraphSpec())
            ShapeKind.SOLID_CUBE, ShapeKind.SOLID_BOX, ShapeKind.SOLID_CYLINDER, ShapeKind.SOLID_CONE, ShapeKind.SOLID_SPHERE,
            ShapeKind.SOLID_DOME, ShapeKind.SOLID_PYRAMID, ShapeKind.SOLID_PRISM, ShapeKind.SOLID_HEX_PRISM -> ShapeSolids.paths(shape, rect)
        }
    }

    // --- Yardımcılar ---

    /** Kelebek kanadı (üst ve alt lob), [side] -1 sol / +1 sağ. */
    /**
     * Ardışık quadratic Bezier parçalarından yol; her parça (x0, y0, cx, cy, x1, y1) olarak verilir.
     * Elle çizilmiş organik şekiller (kanat, gövde, yaprak) bununla kurulur.
     */
    private fun bezierPath(b: Box, closed: Boolean, vararg segments: FloatArray): ShapePath {
        val pts = ArrayList<Offset>(segments.size * 19)
        segments.forEach { g ->
            val n = 18
            for (i in 0..n) {
                if (i == 0 && pts.isNotEmpty()) continue
                val (x, y) = quad(g[0], g[1], g[2], g[3], g[4], g[5], i / n.toFloat())
                pts += b.p(x, y)
            }
        }
        return ShapePath(if (closed) pts + pts.first() else pts, closed)
    }

    /** Aynı yolun x ekseninde aynalanmışı (sağ/sol kanat gibi). */
    private fun mirrored(b: Box, closed: Boolean, vararg segments: FloatArray): ShapePath =
        bezierPath(b, closed, *segments.map { floatArrayOf(1f - it[0], it[1], 1f - it[2], it[3], 1f - it[4], it[5]) }.toTypedArray())

    /** Elmanın yaprağı. */
    private fun leafAt(t: Float): Pair<Float, Float> {
        val a = t * 2 * PI
        val x = 0.66 + 0.12 * cos(a) - 0.05 * sin(a)
        val y = 0.06 + 0.05 * sin(a) + 0.03 * cos(a)
        return x.toFloat() to y.toFloat()
    }

    private fun exp1(t: Float): Double {
        // Ortada (t=0.5) keskin çukur
        val d = abs(t - 0.5) / 0.12
        return kotlin.math.exp(-d * d)
    }

    private fun quad(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, t: Float): Pair<Float, Float> {
        val m = 1 - t
        return (m * m * x0 + 2 * m * t * cx + t * t * x1) to (m * m * y0 + 2 * m * t * cy + t * t * y1)
    }

    private fun regular(b: Box, sides: Int, rotation: Double = 0.0): ShapePath {
        val pts = (0..sides).map { i ->
            val a = -PI / 2 + rotation + i * 2 * PI / sides
            b.p(0.5f + 0.5f * cos(a).toFloat(), 0.5f + 0.5f * sin(a).toFloat())
        }
        return ShapePath(pts, closed = true)
    }

    private fun star(b: Box): ShapePath {
        val pts = (0..10).map { i ->
            val a = -PI / 2 + i * PI / 5
            val r = if (i % 2 == 0) 0.5f else 0.2f
            b.p(0.5f + r * cos(a).toFloat(), 0.53f + r * sin(a).toFloat())
        }
        return ShapePath(pts, closed = true)
    }

    private fun crescent(b: Box): ShapePath {
        val theta = Math.toRadians(55.0)
        val p1x = 0.5 + 0.5 * cos(theta)
        val s = 0.5 * sin(theta)
        val left = 0.3
        val c = (left * left - p1x * p1x - s * s) / (2 * (left - p1x))
        val r2 = c - left
        val phi = atan2(s, p1x - c)
        val outer = b.arc(0.5f, 0.5f, 0.5f, 0.5f, theta, 2 * PI - theta).points
        val inner = b.arc(c.toFloat(), 0.5f, r2.toFloat(), r2.toFloat(), 2 * PI - phi, phi).points
        return ShapePath(outer + inner.drop(1), closed = true)
    }

    private fun petal(b: Box, angle: Double): ShapePath {
        val cx = 0.5 + 0.29 * cos(angle)
        val cy = 0.5 + 0.29 * sin(angle)
        val pts = (0..72).map { i ->
            val a = i * 2 * PI / 72
            val x = 0.17 * cos(a)
            val y = 0.09 * sin(a)
            b.p((cx + x * cos(angle) - y * sin(angle)).toFloat(), (cy + x * sin(angle) + y * cos(angle)).toFloat())
        }
        return ShapePath(pts, closed = true)
    }

    private fun box3d(b: Box, depth: Float): List<ShapePath> {
        val f = 1f - depth
        return listOf(
            b.poly(0f, depth, f, depth, f, 1f, 0f, 1f, closed = true),
            b.poly(0f, depth, depth, 0f, 1f, 0f, f, depth),
            b.poly(1f, 0f, 1f, f, f, 1f),
            b.poly(depth, 0f, depth, f, dashed = true),
            b.poly(depth, f, 1f, f, dashed = true),
            b.poly(0f, 1f, depth, f, dashed = true),
        )
    }

    private fun roundedRect(r: Rect, radius: Float): ShapePath {
        val rad = min(radius, min(r.width, r.height) / 2f)
        val pts = ArrayList<Offset>()
        fun corner(cx: Float, cy: Float, from: Double) {
            val n = (rad / 3f).toInt().coerceIn(6, 60)
            for (i in 0..n) {
                val a = from + (PI / 2) * i / n
                pts += Offset(cx + rad * cos(a).toFloat(), cy + rad * sin(a).toFloat())
            }
        }
        corner(r.right - rad, r.top + rad, -PI / 2)
        corner(r.right - rad, r.bottom - rad, 0.0)
        corner(r.left + rad, r.bottom - rad, PI / 2)
        corner(r.left + rad, r.top + rad, PI)
        pts += pts.first()
        return ShapePath(pts, closed = true)
    }

    /** Dik açı işareti: köşe [corner], kollar +x·[sx] ve +y·[sy] yönünde. */
    private fun rightAngleMark(corner: Offset, sx: Float, sy: Float, size: Float): ShapePath =
        ShapePath(listOf(corner + Offset(size * sx, 0f), corner + Offset(size * sx, size * sy), corner + Offset(0f, size * sy)))

    private fun angleArc(v: Offset, a: Offset, c: Offset, radius: Float): ShapePath {
        val a0 = atan2((a.y - v.y).toDouble(), (a.x - v.x).toDouble())
        var a1 = atan2((c.y - v.y).toDouble(), (c.x - v.x).toDouble())
        if (a1 > a0) a1 -= 2 * PI
        val n = 32
        return ShapePath(List(n + 1) { i ->
            val t = a0 + (a1 - a0) * i / n
            Offset(v.x + radius * cos(t).toFloat(), v.y + radius * sin(t).toFloat())
        })
    }

    private fun circleIn(center: Offset, radius: Float): ShapePath {
        val n = (2 * PI * radius / 5).toInt().coerceIn(16, 360)
        return ShapePath(List(n + 1) { i ->
            val a = i * 2 * PI / n
            Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
        }, closed = true)
    }

    private fun dot(center: Offset, radius: Float) = circleIn(center, radius)

    /** Parametrik eğriyi kutuya sığdırır (eğrinin kendi sınırlarından). */
    private fun normalized(b: Box, closed: Boolean, samples: Int = 0, f: (Float) -> Pair<Float, Float>): ShapePath =
        normalizedInto(b, Rect(0f, 0f, 1f, 1f), closed, samples, f)

    private fun normalizedInto(b: Box, target: Rect, closed: Boolean, samples: Int = 0, f: (Float) -> Pair<Float, Float>): ShapePath {
        val n = if (samples > 0) samples else ((b.w + b.h) / 4f).toInt().coerceIn(96, 900)
        val raw = List(n + 1) { f(it / n.toFloat()) }
        val minX = raw.minOf { it.first }; val maxX = raw.maxOf { it.first }
        val minY = raw.minOf { it.second }; val maxY = raw.maxOf { it.second }
        val sw = max(maxX - minX, 1e-6f); val sh = max(maxY - minY, 1e-6f)
        return ShapePath(raw.map { (x, y) ->
            b.p(target.left + (x - minX) / sw * target.width, target.top + (y - minY) / sh * target.height)
        }, closed)
    }

    /** Kesik çizgi parçaları (tuval biriminde, çizgi kalınlığına göre). */
    private fun dashes(path: ShapePath, ink: Float): List<ShapePath> {
        val dash = max(ink * 3f, 10f)
        val gap = max(ink * 2.2f, 7f)
        val out = ArrayList<ShapePath>()
        var current = ArrayList<Offset>()
        var drawing = true
        var left = dash
        for (i in 1 until path.points.size) {
            var a = path.points[i - 1]
            val bpt = path.points[i]
            var seg = hypot(bpt.x - a.x, bpt.y - a.y)
            if (drawing && current.isEmpty()) current += a
            while (seg > 0f) {
                val step = min(seg, left)
                val t = step / seg
                val p = Offset(a.x + (bpt.x - a.x) * t, a.y + (bpt.y - a.y) * t)
                if (drawing) current += p
                seg -= step
                left -= step
                a = p
                if (left <= 1e-3f) {
                    if (drawing) { if (current.size >= 2) out += ShapePath(current); current = ArrayList() }
                    drawing = !drawing
                    left = if (drawing) dash else gap
                    if (drawing) current += a
                }
            }
        }
        if (drawing && current.size >= 2) out += ShapePath(current)
        return out
    }
}
