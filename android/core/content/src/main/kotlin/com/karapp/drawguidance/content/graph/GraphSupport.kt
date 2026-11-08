package com.karapp.drawguidance.content.graph

import com.karapp.drawguidance.content.vector.INK_COLOR
import com.karapp.drawguidance.content.vector.LineStyle
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VPath
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

/** Grafiklerin ortak ölçüleri (kısa kenar = 1 birimlik "fiziksel" koordinatlarda). */
internal object Style {
    const val PAD = 0.035f
    const val AXIS_EXT = 0.05f
    const val ARROW_LEN = 0.032f
    const val ARROW_HALF = 0.012f
    const val TICK = 0.011f
    const val TICK_SIZE = 0.038f
    const val NAME_SIZE = 0.05f
    const val FORMULA_SIZE = 0.046f
    const val KEY_SIZE = 0.034f
    const val GAP = 0.009f
    const val DOT = 0.0125f

    const val GRID = "#DDE3EA"
    const val GRID_MINOR = "#EEF1F5"
    const val ASYMPTOTE = "#90A4AE"
    const val GUIDE = "#90A4AE"
    const val WHITE = "#FFFFFF"
    const val BOX_BORDER = "#C9D3DE"
    const val TEXT_DARK = "#263238"

    const val W_AXIS = 1f
    const val W_TICK = 0.9f
    const val W_GRID = 0.45f
    const val W_MINOR = 0.3f
    const val W_CURVE = 2f

    /** Yumuşak, birbirinden ayrışan grafik renkleri. */
    val PALETTE = listOf(
        "#5B9BD5", "#F4A259", "#6CC08B", "#E86F68", "#9B8AD1", "#F2C94C",
        "#4DB6AC", "#F48FB1", "#A1887F", "#90A4AE", "#AED581", "#7986CB",
    )
}

/**
 * v18 kağıda uyan renkler: formül kutusu ve nokta içleri kağıt renginde, ızgara/yardımcı çizgiler kağıttan biraz koyu
 * (koyu kağıtta biraz açık). Beyaz kağıtta eski renkler aynen kullanılır.
 */
internal class GraphPalette private constructor(
    val paper: String,
    val grid: String,
    val gridMinor: String,
    val guide: String,
    val boxBorder: String,
) {
    companion object {
        private val WHITE = GraphPalette(Style.WHITE, Style.GRID, Style.GRID_MINOR, Style.GUIDE, Style.BOX_BORDER)

        fun of(paperHex: String): GraphPalette {
            val paper = Colors.valid(paperHex, Style.WHITE)
            if (paper == Style.WHITE) return WHITE
            val dark = Colors.luminance(paper) < 0.45f
            val tone = if (dark) "#CFD8DC" else "#5B7083"
            return GraphPalette(
                paper = paper,
                grid = Colors.mix(paper, tone, if (dark) 0.24f else 0.2f),
                gridMinor = Colors.mix(paper, tone, if (dark) 0.12f else 0.1f),
                guide = Colors.mix(paper, if (dark) "#ECEFF1" else "#37474F", 0.55f),
                boxBorder = Colors.mix(paper, tone, if (dark) 0.4f else 0.3f),
            )
        }
    }
}

/** Fiziksel koordinatlarda dikdörtgen (yazı çarpışması için). */
internal data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    fun overlaps(o: Box, m: Float = 0f) = l < o.r + m && o.l < r + m && t < o.b + m && o.t < b + m
    fun contains(x: Float, y: Float) = x in l..r && y in t..b
    val w get() = r - l
    val h get() = b - t
}

/** Yazı genişliği tahmini (yazı boyuna oranla; uygulamadaki sans-serif yazı tipine yakın). */
internal object Metrics {
    private const val SUPER = "⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺ⁿˣʸ⁽⁾"
    fun char(c: Char): Float = when {
        c.isDigit() -> 0.6f
        c in ".,:;'|!" -> 0.28f
        c == ' ' -> 0.3f
        c in "−+=±<>" -> 0.7f
        c == '-' -> 0.4f
        c in "()[]{}⌊⌋⌈⌉⟦⟧" -> 0.4f
        c == '/' -> 0.36f
        c == 'π' -> 0.62f
        c == '√' || c == '∛' -> 0.62f
        c == '°' -> 0.4f
        c == '%' -> 0.86f
        c == '·' -> 0.3f
        c in SUPER -> 0.38f
        c in "ilIjtfr" -> 0.32f
        c in "mwMWŞ" -> 0.85f
        c.isUpperCase() -> 0.68f
        c.isLetter() -> 0.55f
        else -> 0.6f
    }

    fun width(s: String, size: Float): Float = s.sumOf { char(it).toDouble() }.toFloat() * size
}

/** Sayı biçimleri (Türkçe ondalık virgül, gerçek eksi işareti), π kesirleri ve "güzel" adımlar. */
internal object Fmt {
    const val MINUS = "−"

    fun decimals(step: Double): Int {
        for (d in 0..6) {
            val v = step * 10.0.pow(d)
            if (abs(v - round(v)) < 1e-6 * max(1.0, abs(v))) return d
        }
        return 6
    }

    fun num(v: Double, decimals: Int): String {
        var s = String.format(Locale.ROOT, "%.${decimals}f", v)
        if (s.startsWith("-")) {
            s = s.substring(1)
            if (s.any { it in '1'..'9' }) s = MINUS + s
        }
        return s.replace('.', ',')
    }

    /** En fazla [maxDec] ondalık, sondaki sıfırlar atılır. */
    fun auto(v: Double, maxDec: Int = 2): String {
        val s = num(v, maxDec)
        if (!s.contains(',')) return s
        return s.trimEnd('0').trimEnd(',')
    }

    fun gcd(a: Int, b: Int): Int = if (b == 0) abs(a) else gcd(b, a % b)

    /** v ≈ n·π/d ise (n, d) (sadeleşmiş), değilse null. */
    fun piFraction(v: Double, maxDen: Int = 12): Pair<Int, Int>? {
        if (!v.isFinite()) return null
        val r = v / PI
        for (d in 1..maxDen) {
            val n = r * d
            val rn = round(n)
            if (abs(n - rn) < 1e-7 * d && abs(rn) < 1e6) {
                val g = max(1, gcd(rn.toInt(), d))
                return Pair(rn.toInt() / g, d / g)
            }
        }
        return null
    }

    fun piLabel(n: Int, d: Int): String {
        if (n == 0) return "0"
        val g = max(1, gcd(n, d))
        val nn = n / g
        val dd = d / g
        val a = abs(nn)
        val head = if (a == 1) "π" else "${a}π"
        return (if (nn < 0) MINUS else "") + head + (if (dd != 1) "/$dd" else "")
    }

    /** Bir koordinat değeri: periyodikse π kesri (π/2, 3π/4), değilse ≤2 ondalık. */
    fun coord(v: Double, preferPi: Boolean): String {
        if (preferPi && abs(v) > 1e-9) {
            piFraction(v)?.let { (n, d) -> if (d <= 12) return piLabel(n, d) }
        }
        return auto(v, 2)
    }

    /** Nokta yazısı: tamsayılarda "(2, 3)", ondalıkta Türkçe usulü "(1,5; 2)". */
    fun point(x: String, y: String): String =
        if (x.contains(',') || y.contains(',')) "($x; $y)" else "($x, $y)"

    private fun split(raw: Double): Pair<Double, Double> {
        val e = floor(log10(raw))
        val b = 10.0.pow(e)
        return Pair(raw / b, b)
    }

    fun niceCeil(raw: Double): Double {
        if (!(raw > 0) || !raw.isFinite()) return 1.0
        val (m, b) = split(raw)
        val n = when {
            m <= 1.0 + 1e-9 -> 1.0
            m <= 2.0 + 1e-9 -> 2.0
            m <= 5.0 + 1e-9 -> 5.0
            else -> 10.0
        }
        return n * b
    }

    fun niceRound(raw: Double): Double {
        if (!(raw > 0) || !raw.isFinite()) return 1.0
        val (m, b) = split(raw)
        val n = when {
            m < 1.4 -> 1.0
            m < 2.9 -> 2.0
            m < 6.5 -> 5.0
            else -> 10.0
        }
        return n * b
    }

    fun smaller(step: Double): Double {
        val (m, b) = split(step)
        return when {
            m < 1.5 -> 0.5 * b
            m < 3.5 -> 1.0 * b
            else -> 2.0 * b
        }
    }

    fun larger(step: Double): Double {
        val (m, b) = split(step)
        return when {
            m < 1.5 -> 2.0 * b
            m < 3.5 -> 5.0 * b
            else -> 10.0 * b
        }
    }
}

/** Renk yardımcıları ("#RRGGBB"). */
internal object Colors {
    fun rgb(hex: String): IntArray {
        val h = hex.removePrefix("#")
        return try {
            val v = h.take(6).toLong(16).toInt()
            intArrayOf((v shr 16) and 255, (v shr 8) and 255, v and 255)
        } catch (_: Exception) {
            intArrayOf(30, 136, 229)
        }
    }

    fun hex(r: Int, g: Int, b: Int) = String.format(Locale.ROOT, "#%02X%02X%02X", r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

    fun mix(a: String, b: String, t: Float): String {
        val x = rgb(a); val y = rgb(b)
        return hex(
            (x[0] + (y[0] - x[0]) * t).roundToInt(),
            (x[1] + (y[1] - x[1]) * t).roundToInt(),
            (x[2] + (y[2] - x[2]) * t).roundToInt(),
        )
    }

    fun lighten(c: String, t: Float) = mix(c, "#FFFFFF", t)
    fun darken(c: String, t: Float) = mix(c, "#000000", t)
    fun scale(c: String, k: Float): String {
        val x = rgb(c)
        return hex((x[0] * k).roundToInt(), (x[1] * k).roundToInt(), (x[2] * k).roundToInt())
    }

    fun luminance(c: String): Float {
        val x = rgb(c)
        return (0.299f * x[0] + 0.587f * x[1] + 0.114f * x[2]) / 255f
    }

    /** Geçerli "#RRGGBB" değilse varsayılan. */
    fun valid(c: String, fallback: String = "#1E88E5"): String =
        if (Regex("^#[0-9A-Fa-f]{6}$").matches(c)) c.uppercase() else fallback
}

/**
 * Çizim yüzeyi: "fiziksel" koordinatlarda (kısa kenar = 1, y aşağı) çizer, birim kutuya dönüştürüp katmanlara
 * ekler. Yazı boyları doğrudan kısa kenara oranlıdır. Çentik yazılarının varsayılan metinleri [defaults]'ta
 * toplanır (düzenleme penceresinin ilk metni için).
 */
internal class Canvas(aspect: Float, resolution: Float, val p: GraphParams) {
    val W: Float
    val H: Float

    init {
        val a = if (aspect.isFinite() && aspect > 0f) aspect.coerceIn(0.1f, 10f) else 1f
        if (a >= 1f) { W = a; H = 1f } else { W = 1f; H = 1f / a }
    }

    val res: Float = (if (resolution.isFinite()) resolution else 512f).coerceIn(48f, 4096f)
    /** Bir pikselin fiziksel boyu (yaklaşık). */
    val px: Float = 1f / res
    private val layers = Array(8) { ArrayList<VPath>() }
    var layer = 0
    val boxes = ArrayList<Box>()
    val defaults = LinkedHashMap<String, String>()
    val color: String = Colors.valid(p.curveColor)
    /** v18: kağıda uyan renkler. */
    val pal: GraphPalette = GraphPalette.of(p.paper)

    fun paths(): List<VPath> = layers.flatMap { it }

    private fun u(x: Float, y: Float) = P(x / W, y / H)

    fun add(
        pts: List<P>, closed: Boolean = false, fill: String? = null, stroke: String? = null, width: Float = 1f,
        line: LineStyle = LineStyle.SOLID, alpha: Float = 1f, tag: String? = null,
    ) {
        if (pts.size < 2) return
        if (pts.any { !it.x.isFinite() || !it.y.isFinite() }) return
        layers[layer] += VPath(pts.map { u(it.x, it.y) }, closed, fill, stroke, width, line, alpha, tag = tag)
    }

    fun line(
        x1: Float, y1: Float, x2: Float, y2: Float, stroke: String? = null, width: Float = 1f,
        line: LineStyle = LineStyle.SOLID, alpha: Float = 1f, tag: String? = null,
    ) = add(listOf(P(x1, y1), P(x2, y2)), stroke = stroke, width = width, line = line, alpha = alpha, tag = tag)

    fun circlePoints(cx: Float, cy: Float, r: Float, rMin: Int = 12): List<P> {
        val n = (2 * PI * r * res / 4).toInt().coerceIn(rMin, 240)
        return List(n) { i ->
            val a = 2 * PI * i / n
            P(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        }
    }

    /** Dolu nokta (beyaz kenarlı). */
    fun dot(cx: Float, cy: Float, r: Float = Style.DOT, fill: String = color, stroke: String = pal.paper, width: Float = 0.7f, tag: String? = null) =
        add(circlePoints(cx, cy, r), closed = true, fill = fill, stroke = stroke, width = width, tag = tag)

    /** Boş nokta (beyaz içli, renkli kenarlı). */
    fun openDot(cx: Float, cy: Float, r: Float = Style.DOT, stroke: String = color, tag: String? = null) =
        add(circlePoints(cx, cy, r), closed = true, fill = pal.paper, stroke = stroke, width = 1.1f, tag = tag)

    /** Dolgusuz çember (açık çoklu çizgi olarak kapatılır; eleman dolgusu uygulanmaz). */
    fun ring(cx: Float, cy: Float, r: Float, stroke: String? = null, width: Float = 1f, line: LineStyle = LineStyle.SOLID, alpha: Float = 1f, tag: String? = null) {
        val pts = circlePoints(cx, cy, r, 24)
        add(pts + pts.first(), stroke = stroke, width = width, line = line, alpha = alpha, tag = tag)
    }

    fun roundRectPoints(x: Float, y: Float, w: Float, h: Float, r: Float): List<P> {
        val rr = min(r, min(w, h) / 2f)
        val pts = ArrayList<P>()
        fun corner(cx: Float, cy: Float, from: Double) {
            for (i in 0..5) {
                val a = from + (PI / 2) * i / 5
                pts += P(cx + rr * cos(a).toFloat(), cy + rr * sin(a).toFloat())
            }
        }
        corner(x + w - rr, y + rr, -PI / 2)
        corner(x + w - rr, y + h - rr, 0.0)
        corner(x + rr, y + h - rr, PI / 2)
        corner(x + rr, y + rr, PI)
        return pts
    }

    /** Dolu ok ucu: ucu (tx, ty), yönü (dx, dy). */
    fun arrowHead(tx: Float, ty: Float, dx: Float, dy: Float, len: Float = Style.ARROW_LEN, half: Float = Style.ARROW_HALF, color: String? = null) {
        val l = hypot(dx, dy).coerceAtLeast(1e-6f)
        val ux = dx / l; val uy = dy / l
        val nx = -uy; val ny = ux
        val bx = tx - ux * len; val by = ty - uy * len
        add(
            listOf(P(bx + nx * half, by + ny * half), P(tx, ty), P(bx - nx * half, by - ny * half), P(bx + ux * len * 0.18f, by + uy * len * 0.18f)),
            closed = true, fill = color ?: INK_COLOR, stroke = color, width = 0.4f,
        )
    }

    fun measure(s: String, size: Float) = Metrics.width(s, size)

    fun textBox(x: Float, y: Float, s: String, size: Float) = Box(x, y, x + measure(s, size), y + size)

    /** Yazı (sol-üst çapa) ekler ve alanını ayırır. */
    fun text(x: Float, y: Float, s: String, size: Float, color: String? = null, tag: String? = null, reserve: Boolean = true): Box {
        layers[layer] += VPath(listOf(u(x, y)), stroke = color, label = s, labelSize = size, tag = tag)
        val b = textBox(x, y, s, size)
        if (reserve) boxes += b
        return b
    }

    /** Kutu içinde mi ve ayrılmış alanlarla çakışmıyor mu? */
    fun fits(b: Box, margin: Float = 0.004f, slack: Float = 0.012f): Boolean =
        b.l >= -slack && b.t >= -slack && b.r <= W + slack && b.b <= H + slack && boxes.none { it.overlaps(b, margin) }

    /**
     * Çentik yazısı: kullanıcı değiştirdiyse onun metni (zorunlu), değilse varsayılan. "" → null (gizli).
     * Varsayılan metin [defaults]'a yazılır.
     */
    fun tickText(key: String, def: String): Pair<String?, Boolean> {
        defaults[key] = def
        val o = p.tickLabels[key]
        val t = o ?: def
        return Pair(t.ifEmpty { null }, o != null)
    }
}
