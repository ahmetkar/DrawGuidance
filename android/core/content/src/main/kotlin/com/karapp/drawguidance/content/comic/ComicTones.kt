package com.karapp.drawguidance.content.comic

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v53 çizgi roman ekran tonları (screentone) ve desenleri. Çizgi romanda/mangada kullanılan başlıca türler:
 *  - Ekran tonları: nokta tonu (amiten; ten ve gölge için ince, gece için koyu), nokta gradyanı (gökyüzü, ışık geçişi),
 *    dairesel gradyan (parlama, odak), kum tonu (suname; yumuşak gölge ve duygu), paralel çizgi tonu, çapraz tarama ve
 *    demet tarama (kakeami; arka plan dokusu, gerilim).
 *  - Desenler: duygu arka planları (çiçek: romantizm, parıltı/kirakira: hayranlık, baloncuk: hayal, ip ağı/nawaami:
 *    endişe, dikey çizgiler: moral bozukluğu ve şok, yıldız, kalp, zikzak: enerji) ve giysi/zemin desenleri (puantiye,
 *    çizgili, ekose, dama, tuğla, tahta).
 * Ton bir şeklin dolgusu olarak saklanır ([com.karapp.drawguidance.data.model.ToneFill]); şeklin sınırına kırpılarak
 * çizilir. Bu dosya platformdan bağımsız geometriyi üretir (iOS `ComicTones.swift` aynı hesabı yapar).
 */
object ComicTones {
    enum class Group { TONE, PATTERN }

    /** Hazır ayar: [id] ton türü, [density] koyuluk (0..1), [scale] sıklık çarpanı, [angle] açı (derece). */
    data class Preset(
        val key: String,
        val id: String,
        val group: Group,
        val density: Float,
        val scale: Float = 1f,
        val angle: Float = 45f,
    ) {
        val nameKey: String get() = "v53_tone_$key"
        val usageKey: String get() = "v53_tone_${key}_use"
    }

    /** Ekran tonları (her biri çizgi romandaki tipik kullanımıyla). */
    val TONES = listOf(
        Preset("skin", "tone_dot", Group.TONE, 0.10f, 0.7f),
        Preset("soft_shadow", "tone_dot", Group.TONE, 0.28f),
        Preset("deep_shadow", "tone_dot", Group.TONE, 0.55f),
        Preset("night", "tone_dot", Group.TONE, 0.8f, 0.8f),
        Preset("sky", "tone_dot_grad", Group.TONE, 0.6f),
        Preset("glow", "tone_dot_radial", Group.TONE, 0.55f),
        Preset("sand", "tone_sand", Group.TONE, 0.3f),
        Preset("lines", "tone_lines", Group.TONE, 0.3f, 1f, 45f),
        Preset("hatch", "tone_hatch", Group.TONE, 0.35f, 1f, 45f),
        Preset("kakeami", "tone_kakeami", Group.TONE, 0.45f),
    )

    /** Desenler: duygu arka planları ve giysi/zemin desenleri. */
    val PATTERNS = listOf(
        Preset("flowers", "pat_flowers", Group.PATTERN, 0.6f),
        Preset("sparkle", "pat_sparkle", Group.PATTERN, 0.6f),
        Preset("bubbles", "pat_bubbles", Group.PATTERN, 0.5f),
        Preset("nawaami", "pat_nawaami", Group.PATTERN, 0.5f),
        Preset("gloom", "pat_vlines", Group.PATTERN, 0.6f),
        Preset("stars", "pat_stars", Group.PATTERN, 0.5f),
        Preset("hearts", "pat_hearts", Group.PATTERN, 0.5f),
        Preset("zigzag", "pat_zigzag", Group.PATTERN, 0.5f, 1f, 0f),
        Preset("polka", "pat_polka", Group.PATTERN, 0.5f),
        Preset("stripes", "pat_stripes", Group.PATTERN, 0.5f, 1f, 45f),
        Preset("plaid", "pat_plaid", Group.PATTERN, 0.5f, 1f, 0f),
        Preset("check", "pat_check", Group.PATTERN, 0.5f, 1f, 0f),
        Preset("brick", "pat_brick", Group.PATTERN, 0.5f, 1f, 0f),
        Preset("wood", "pat_wood", Group.PATTERN, 0.5f, 1f, 0f),
    )

    val ALL = TONES + PATTERNS

    fun find(key: String): Preset? = ALL.firstOrNull { it.key == key }

    /** Çizim öğeleri: dolu daire, halka, çizgi parçası, çoklu çizgi/çokgen. [alpha] örtücülük çarpanı. */
    sealed interface Prim { val alpha: Float }
    data class Dot(val x: Float, val y: Float, val r: Float, override val alpha: Float = 1f) : Prim
    data class Ring(val x: Float, val y: Float, val r: Float, val w: Float, override val alpha: Float = 1f) : Prim
    data class Seg(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val w: Float, override val alpha: Float = 1f) : Prim
    data class Poly(val pts: FloatArray, val closed: Boolean, val filled: Boolean, val w: Float, override val alpha: Float = 1f) : Prim

    /** Bir tonda üretilecek en çok öğe (bellek ve hız sınırı; aşılırsa aralık büyür). */
    const val MAX_PRIMS = 12_000

    /** Temel aralık (tuval birimi; ölçek 1'de). */
    const val BASE_SPACING = 9f

    /** Yeniden üretilebilir sözde rastgele (iki platformda aynı dizi). */
    class Rng(seed: Long) {
        private var s = seed and 0x7fffffffL
        fun next(): Float { s = (s * 1103515245L + 12345L) and 0x7fffffffL; return s.toFloat() / 2147483648f }
    }

    /**
     * [id] tonunun [l],[t],[r],[b] kutusundaki öğeleri. Kutu şeklin sınır kutusudur; çizen taraf şeklin yoluna kırpar.
     */
    fun prims(id: String, density: Float, scale: Float, angle: Float, l: Float, t: Float, r: Float, b: Float, seed: Long = 7L): List<Prim> {
        val w = r - l
        val h = b - t
        if (w <= 0f || h <= 0f) return emptyList()
        val d = density.coerceIn(0.02f, 0.95f)
        var s = BASE_SPACING * scale.coerceIn(0.3f, 6f)
        val pattern = id.startsWith("pat_")
        val step0 = if (pattern) s * 5f else s
        // Öğe sayısı sınırı: alan / aralık² çok büyükse aralık büyür
        // Desenlerde hücre başına birkaç öğe (çiçek: 6) çıkar: sınır öğe sayısına göre
        val cells = (w / step0) * (h / step0) * (if (pattern) 7f else 1.6f)
        if (cells > MAX_PRIMS) s *= sqrt(cells / MAX_PRIMS)
        val out = ArrayList<Prim>()
        val rng = Rng(seed)
        when (id) {
            "tone_dot" -> lattice(l, t, r, b, s, angle) { x, y -> out += Dot(x, y, dotR(s, d)) }
            "tone_dot_grad" -> lattice(l, t, r, b, s, angle) { x, y ->
                val k = 1f - ((y - t) / h).coerceIn(0f, 1f)
                val dd = d * k
                if (dd > 0.015f) out += Dot(x, y, dotR(s, dd))
            }
            "tone_dot_radial" -> {
                val cx = (l + r) / 2; val cy = (t + b) / 2
                val maxD = hypot(w / 2, h / 2)
                lattice(l, t, r, b, s, angle) { x, y ->
                    val k = (hypot(x - cx, y - cy) / maxD).coerceIn(0f, 1f)
                    val dd = d * k * k
                    if (dd > 0.015f) out += Dot(x, y, dotR(s, dd))
                }
            }
            "tone_sand" -> {
                val r0 = s * 0.16f
                val n = min((w * h * d / (PI.toFloat() * r0 * r0 * 2.2f)).toInt(), MAX_PRIMS)
                repeat(n) { out += Dot(l + rng.next() * w, t + rng.next() * h, r0 * (0.6f + rng.next() * 0.8f)) }
            }
            "tone_lines" -> parallel(l, t, r, b, s, angle) { x1, y1, x2, y2 -> out += Seg(x1, y1, x2, y2, s * d) }
            "tone_hatch" -> {
                parallel(l, t, r, b, s, angle) { x1, y1, x2, y2 -> out += Seg(x1, y1, x2, y2, s * d * 0.55f) }
                parallel(l, t, r, b, s, angle + 90f) { x1, y1, x2, y2 -> out += Seg(x1, y1, x2, y2, s * d * 0.55f) }
            }
            "tone_kakeami" -> {
                val cell = s * 4f
                val lw = s * 0.11f * (0.6f + d)
                var row = 0
                var y = t
                while (y < b) {
                    var col = 0
                    var x = l
                    while (x < r) {
                        val vertical = (row + col) % 2 == 0
                        val k = (2 + (d * 4).toInt()).coerceIn(2, 6)
                        for (i in 0 until k) {
                            val o = cell * (i + 0.5f) / k
                            val j = (rng.next() - 0.5f) * cell * 0.08f
                            if (vertical) out += Seg(x + o + j, y + cell * 0.06f, x + o - j, y + cell * 0.94f, lw)
                            else out += Seg(x + cell * 0.06f, y + o + j, x + cell * 0.94f, y + o - j, lw)
                        }
                        x += cell; col++
                    }
                    y += cell; row++
                }
            }
            else -> patternPrims(id, d, s * 5f, angle, l, t, r, b, rng, out)
        }
        return out
    }

    private fun dotR(s: Float, d: Float): Float = min(s * sqrt(d / PI.toFloat()), s * 0.72f)

    /** Döndürülmüş kafes noktaları (kutuyu kaplar). */
    private inline fun lattice(l: Float, t: Float, r: Float, b: Float, s: Float, angle: Float, emit: (Float, Float) -> Unit) {
        val a = angle * PI.toFloat() / 180f
        val ca = cos(a); val sa = sin(a)
        val cx = (l + r) / 2; val cy = (t + b) / 2
        val half = hypot(r - l, b - t) / 2 + s
        val n = ceil(half / s).toInt()
        for (i in -n..n) for (j in -n..n) {
            val u = i * s; val v = j * s
            val x = cx + u * ca - v * sa
            val y = cy + u * sa + v * ca
            if (x >= l - s && x <= r + s && y >= t - s && y <= b + s) emit(x, y)
        }
    }

    /** Kutuyu kaplayan, [angle] açılı, [s] aralıklı paralel çizgiler. */
    private inline fun parallel(l: Float, t: Float, r: Float, b: Float, s: Float, angle: Float, emit: (Float, Float, Float, Float) -> Unit) {
        val a = angle * PI.toFloat() / 180f
        val dx = cos(a); val dy = sin(a)
        val nx = -dy; val ny = dx
        val cx = (l + r) / 2; val cy = (t + b) / 2
        val half = hypot(r - l, b - t) / 2 + s
        val n = ceil(half / s).toInt()
        for (i in -n..n) {
            val ox = cx + nx * i * s; val oy = cy + ny * i * s
            emit(ox - dx * half, oy - dy * half, ox + dx * half, oy + dy * half)
        }
    }

    private fun circlePts(cx: Float, cy: Float, rad: Float, n: Int = 14): FloatArray {
        val p = FloatArray(n * 2)
        for (i in 0 until n) {
            val a = 2 * PI.toFloat() * i / n
            p[2 * i] = cx + cos(a) * rad; p[2 * i + 1] = cy + sin(a) * rad
        }
        return p
    }

    private fun starPts(cx: Float, cy: Float, outer: Float, inner: Float, points: Int, rot: Float): FloatArray {
        val p = FloatArray(points * 4)
        for (i in 0 until points * 2) {
            val a = rot + PI.toFloat() * i / points - PI.toFloat() / 2
            val rr = if (i % 2 == 0) outer else inner
            p[2 * i] = cx + cos(a) * rr; p[2 * i + 1] = cy + sin(a) * rr
        }
        return p
    }

    private fun heartPts(cx: Float, cy: Float, size: Float): FloatArray {
        val n = 20
        val p = FloatArray(n * 2)
        for (i in 0 until n) {
            val tt = 2 * PI.toFloat() * i / n
            val x = 16 * sin(tt) * sin(tt) * sin(tt)
            val y = -(13 * cos(tt) - 5 * cos(2 * tt) - 2 * cos(3 * tt) - cos(4 * tt))
            p[2 * i] = cx + x / 17f * size; p[2 * i + 1] = cy + y / 17f * size
        }
        return p
    }

    private fun patternPrims(id: String, d: Float, S: Float, angle: Float, l: Float, t: Float, r: Float, b: Float, rng: Rng, out: MutableList<Prim>) {
        val w = r - l
        val h = b - t
        val lw = S * 0.035f * (0.6f + d)
        // Kaydırılmış (tuğla dizilişli) ızgara, hafif rastgele kayma
        fun scatter(jitter: Float, emit: (Float, Float, Float) -> Unit) {
            var row = 0
            var y = t - S / 2
            while (y < b + S) {
                var x = l - S / 2 + if (row % 2 == 1) S / 2 else 0f
                while (x < r + S) {
                    emit(x + (rng.next() - 0.5f) * S * jitter, y + (rng.next() - 0.5f) * S * jitter, 0.65f + rng.next() * 0.7f)
                    x += S
                }
                y += S * 0.86f; row++
            }
        }
        when (id) {
            "pat_flowers" -> scatter(0.5f) { x, y, k ->
                val pr = S * 0.16f * k
                for (i in 0 until 5) {
                    val a = 2 * PI.toFloat() * i / 5 + k
                    out += Poly(circlePts(x + cos(a) * pr * 1.25f, y + sin(a) * pr * 1.25f, pr), closed = true, filled = false, w = lw)
                }
                out += Dot(x, y, pr * 0.55f)
            }
            "pat_sparkle" -> scatter(0.8f) { x, y, k ->
                val o = S * 0.22f * k * (0.5f + d)
                out += Poly(starPts(x, y, o, o * 0.18f, 4, 0f), closed = true, filled = true, w = 0f)
                if (k > 1.1f) out += Dot(x + o * 1.3f, y - o * 1.1f, o * 0.12f)
            }
            "pat_bubbles" -> scatter(0.9f) { x, y, k ->
                val rr = S * 0.22f * k
                out += Ring(x, y, rr, lw)
                out += Dot(x - rr * 0.42f, y - rr * 0.42f, rr * 0.16f)
            }
            "pat_nawaami" -> {
                val gap = S * 0.45f
                var o = -h
                while (o < w + h) {
                    for (dir in listOf(1f, -1f)) {
                        val pts = ArrayList<Float>()
                        var k = 0f
                        while (k <= h + gap) {
                            val x = if (dir > 0) l + o + k else r - o - k
                            val y = t + k
                            val wob = sin(k / gap * PI.toFloat()) * gap * 0.18f
                            pts += x + wob; pts += y - wob
                            k += gap / 3
                        }
                        out += Poly(pts.toFloatArray(), closed = false, filled = false, w = lw * 1.1f)
                    }
                    o += gap * 2.2f
                }
            }
            "pat_vlines" -> {
                val gap = S * 0.16f
                var x = l
                while (x < r) {
                    val len = h * (0.25f + rng.next() * 0.55f) * (0.6f + d * 0.6f)
                    out += Seg(x, t, x, t + len, gap * (0.18f + rng.next() * 0.25f))
                    x += gap * (0.7f + rng.next() * 0.8f)
                }
            }
            "pat_stars" -> scatter(0.9f) { x, y, k ->
                val o = S * 0.18f * k
                out += Poly(starPts(x, y, o, o * 0.45f, 5, rng.next()), closed = true, filled = true, w = 0f)
            }
            "pat_hearts" -> scatter(0.6f) { x, y, k -> out += Poly(heartPts(x, y, S * 0.2f * k), closed = true, filled = true, w = 0f) }
            "pat_zigzag" -> {
                val amp = S * 0.18f
                var y = t
                while (y < b + S) {
                    val pts = ArrayList<Float>()
                    var x = l - S
                    var up = true
                    while (x < r + S) { pts += x; pts += y + if (up) -amp else amp; x += S * 0.3f; up = !up }
                    out += Poly(pts.toFloatArray(), closed = false, filled = false, w = lw * 1.4f)
                    y += S * 0.6f
                }
            }
            "pat_polka" -> {
                var row = 0
                var y = t
                while (y < b + S) {
                    var x = l + if (row % 2 == 1) S * 0.5f else 0f
                    while (x < r + S) { out += Dot(x, y, S * 0.17f * (0.6f + d)); x += S }
                    y += S * 0.5f; row++
                }
            }
            "pat_stripes" -> parallel(l, t, r, b, S * 0.5f, angle) { x1, y1, x2, y2 -> out += Seg(x1, y1, x2, y2, S * 0.2f * (0.5f + d)) }
            "pat_plaid" -> {
                parallel(l, t, r, b, S * 0.9f, angle) { x1, y1, x2, y2 -> out += Seg(x1, y1, x2, y2, S * 0.28f, alpha = 0.45f) }
                parallel(l, t, r, b, S * 0.9f, angle + 90f) { x1, y1, x2, y2 -> out += Seg(x1, y1, x2, y2, S * 0.28f, alpha = 0.45f) }
                parallel(l, t, r, b, S * 0.3f, angle) { x1, y1, x2, y2 -> out += Seg(x1, y1, x2, y2, lw * 0.7f, alpha = 0.7f) }
            }
            "pat_check" -> {
                val c = S * 0.45f
                var row = 0
                var y = t
                while (y < b) {
                    var col = 0
                    var x = l
                    while (x < r) {
                        if ((row + col) % 2 == 0) out += Poly(floatArrayOf(x, y, x + c, y, x + c, y + c, x, y + c), closed = true, filled = true, w = 0f, alpha = 0.35f + d * 0.5f)
                        x += c; col++
                    }
                    y += c; row++
                }
            }
            "pat_brick" -> {
                val bh = S * 0.3f
                val bw = S * 0.65f
                var row = 0
                var y = t
                while (y < b) {
                    out += Seg(l, y, r, y, lw)
                    var x = l + if (row % 2 == 1) bw / 2 else 0f
                    while (x < r) { out += Seg(x, y, x, min(y + bh, b), lw); x += bw }
                    y += bh; row++
                }
            }
            "pat_wood" -> {
                val gap = S * 0.22f
                var y = t
                var i = 0
                while (y < b + gap) {
                    val pts = ArrayList<Float>()
                    var x = l
                    val ph = rng.next() * 6f
                    while (x <= r + gap) {
                        pts += x; pts += y + sin(x / (S * 0.9f) + ph) * gap * 0.35f + sin(x / (S * 0.23f) + i) * gap * 0.06f
                        x += gap
                    }
                    out += Poly(pts.toFloatArray(), closed = false, filled = false, w = lw * (0.6f + rng.next() * 0.8f))
                    y += gap * (0.8f + rng.next() * 0.5f); i++
                }
                // Budaklar
                repeat(max(1, (w * h / (S * S * 6f)).toInt().coerceAtMost(12))) {
                    val x = l + rng.next() * w; val y = t + rng.next() * h
                    out += Poly(circlePts(x, y, S * 0.12f, 12).let { p -> for (k in p.indices step 2) p[k] = x + (p[k] - x) * 2.2f; p }, closed = true, filled = false, w = lw)
                }
            }
        }
    }

    /** Tonun eşdeğer gri koyuluğu (önizleme rengi ve PDF/CMYK dönüşümü için). */
    fun coverage(id: String, density: Float): Float = when {
        id.startsWith("tone_") -> density.coerceIn(0f, 1f)
        else -> (density * 0.4f).coerceIn(0f, 1f)
    }
}
