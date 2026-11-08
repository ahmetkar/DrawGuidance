package com.karapp.drawguidance.content.paper

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Hazır arka plan resminin yerleşimi: [COVER] sayfayı kaplar (oranı korunarak kırpılır), [TILE] döşenir (dikişsiz),
 * [NINE] dokuz parçalı esnetilir (köşeler aynı boyda kalır; [repeat] ise kenar ve orta parçalar esnetilmek yerine tam
 * sayıda tekrarlanır: spiral delikleri, satır çizgileri bozulmaz). [insets] korunan kenarlar (sol, üst, sağ, alt piksel),
 * [content] yazının girebileceği iç boşluk (piksel).
 */
class ImageLayout(val kind: Int, val insets: IntArray = IntArray(4), val content: IntArray = IntArray(4), val repeat: Boolean = false) {
    /** Veritabanındaki kısa yazımı: "cover", "tile", "9:l,t,r,b|l,t,r,b" ya da "9r:…". */
    fun encode(): String = when (kind) {
        TILE -> "tile"
        NINE -> (if (repeat) "9r:" else "9:") + insets.joinToString(",") + "|" + content.joinToString(",")
        else -> "cover"
    }

    companion object {
        const val COVER = 0
        const val TILE = 1
        const val NINE = 2

        fun decode(s: String): ImageLayout = when {
            s == "tile" -> ImageLayout(TILE)
            s.startsWith("9") -> {
                val repeat = s.startsWith("9r:")
                val body = s.substringAfter(':')
                val parts = body.split('|')
                fun ints(p: String?) = p?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 4 }?.toIntArray() ?: IntArray(4)
                ImageLayout(NINE, ints(parts.getOrNull(0)), ints(parts.getOrNull(1)), repeat)
            }
            else -> ImageLayout(COVER)
        }
    }
}

/** Hazır arka plan resmi: program içinde üretilir ([generate] ARGB, önçarpımsız), cihazda sıkıştırılıp saklanır. */
class PaperImageDef(
    val id: String,
    val name: String,
    val usage: PaperUsage,
    val w: Int,
    val h: Int,
    val layout: ImageLayout,
    /** Saydam pikseli yok (kayıpla sıkıştırılabilir). */
    val opaque: Boolean,
    val generate: () -> IntArray,
)

/**
 * v23 hazır arka plan resimleri: not tuvali (8), resim tuvali (8) ve yazı kutusu (10, dokuz parçalı). İnternetten
 * alınmış bir resim yoktur; hepsi gürültü (periyodik değer gürültüsü, fbm), işaretli uzaklık alanı (yuvarlak dikdörtgen,
 * kapsül, daire) ve basit ışıklandırmayla burada üretilir. Döşenen resimler kenarlarından birbirine uyar; tekrarlanan
 * dokuz parçalı resimlerde orta parçalar kendi boylarında periyodiktir.
 */
object PaperImages {

    const val VERSION = 1

    private val N = PaperUsage.NOTE
    private val A = PaperUsage.ART
    private val B = PaperUsage.BOX

    private fun cover() = ImageLayout(ImageLayout.COVER)
    private fun tile() = ImageLayout(ImageLayout.TILE)
    private fun nine(l: Int, t: Int, r: Int, b: Int, content: IntArray, repeat: Boolean = false) =
        ImageLayout(ImageLayout.NINE, intArrayOf(l, t, r, b), content, repeat)

    val all: List<PaperImageDef> = listOf(
        // ---------------------------------------------------------------- not tuvali (sade, yazıyı bozmayan)
        PaperImageDef("note.img.cream", "Krem kağıt", N, 512, 512, tile(), true) { cream(512) },
        PaperImageDef("note.img.recycled", "Geri dönüşüm", N, 512, 512, tile(), true) { recycled(512) },
        PaperImageDef("note.img.kraft", "Açık kraft", N, 512, 512, tile(), true) { kraft(512) },
        PaperImageDef("note.img.linen", "Keten", N, 256, 256, tile(), true) { weave(256, 64, 0xFFF3EFE6.toInt(), 0.05f, 71) },
        PaperImageDef("note.img.rice", "Pirinç kağıdı", N, 512, 512, tile(), true) { rice(512) },
        PaperImageDef("note.img.parchment", "Parşömen", N, 620, 877, cover(), true) { parchment(620, 877) },
        PaperImageDef("note.img.wash", "Suluboya köşeler", N, 620, 877, cover(), true) { wash(620, 877) },
        PaperImageDef("note.img.sky", "Açık mavi geçiş", N, 400, 566, cover(), true) {
            gradient(400, 566, intArrayOf(0xFFDCEBF9.toInt(), 0xFFF4F9FE.toInt(), 0xFFFFFFFF.toInt()), floatArrayOf(0f, 0.55f, 1f))
        },
        // ---------------------------------------------------------------- resim tuvali
        PaperImageDef("art.img.coldpress", "Suluboya kağıdı", A, 512, 512, tile(), true) { coldPress(512) },
        PaperImageDef("art.img.canvas", "Tuval bezi", A, 256, 256, tile(), true) { weave(256, 48, 0xFFEAE2D0.toInt(), 0.11f, 83) },
        PaperImageDef("art.img.charcoal", "Gri karakalem kağıdı", A, 512, 512, tile(), true) { charcoal(512) },
        PaperImageDef("art.img.sunset", "Gün batımı", A, 720, 720, cover(), true) { sunset(720) },
        PaperImageDef("art.img.night", "Yıldızlı gece", A, 720, 720, cover(), true) { night(720) },
        PaperImageDef("art.img.clouds", "Bulutlu gök", A, 720, 720, cover(), true) { clouds(720) },
        PaperImageDef("art.img.marble", "Mermer", A, 512, 512, tile(), true) { marble(512) },
        PaperImageDef("art.img.wood", "Ahşap masa", A, 512, 512, tile(), true) { woodTable(512) },
        // ---------------------------------------------------------------- yazı kutusu (dokuz parçalı)
        PaperImageDef("box.img.sticky_yellow", "Sarı yapışkan not", B, 200, 200, nine(18, 30, 46, 46, intArrayOf(20, 32, 24, 26)), false) {
            sticky(0xFFFFEE7A.toInt())
        },
        PaperImageDef("box.img.sticky_pink", "Pembe yapışkan not", B, 200, 200, nine(18, 30, 46, 46, intArrayOf(20, 32, 24, 26)), false) {
            sticky(0xFFFFC6DC.toInt())
        },
        PaperImageDef("box.img.tape", "Bantlı kağıt", B, 220, 200, nine(62, 48, 62, 30, intArrayOf(22, 40, 22, 22)), false) { taped() },
        PaperImageDef("box.img.spiral", "Spiral defter", B, 240, 200, nine(36, 48, 36, 24, intArrayOf(20, 50, 20, 18), repeat = true), false) { spiral() },
        PaperImageDef("box.img.torn", "Yırtık kenarlı kağıt", B, 200, 200, nine(40, 34, 40, 34, intArrayOf(18, 30, 18, 30), repeat = true), false) { torn() },
        PaperImageDef("box.img.kraft_label", "Kraft etiket", B, 220, 160, nine(40, 40, 40, 40, intArrayOf(26, 24, 26, 24), repeat = true), false) { kraftLabel() },
        PaperImageDef("box.img.index_card", "Fiş kartı", B, 240, 180, nine(20, 46, 20, 26, intArrayOf(20, 42, 20, 16), repeat = true), false) { indexCard() },
        PaperImageDef("box.img.clip", "Ataçlı not", B, 220, 200, nine(64, 64, 24, 24, intArrayOf(22, 30, 22, 22)), false) { clipped() },
        PaperImageDef("box.img.card_blue", "Mavi çerçeveli kart", B, 200, 200, nine(26, 26, 26, 26, intArrayOf(22, 20, 22, 20)), false) { card() },
        PaperImageDef("box.img.notebook", "Defter sayfası", B, 240, 220, nine(56, 44, 20, 44, intArrayOf(54, 34, 16, 26), repeat = true), false) { notebook() },
    )

    fun byId(id: String): PaperImageDef? = all.firstOrNull { it.id == id }

    fun of(usage: PaperUsage): List<PaperImageDef> = all.filter { it.usage == usage }

    // ================================================================================================ gürültü

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) / 2147483647f
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)

    /** Periyodik değer gürültüsü: [u], [v] 0..1 periyotlu, [cx]×[cy] kafes. */
    private fun vnoise(u: Float, v: Float, cx: Int, cy: Int, seed: Int): Float {
        val fx = u * cx; val fy = v * cy
        val ix = floor(fx).toInt(); val iy = floor(fy).toInt()
        val tx = smooth(fx - ix); val ty = smooth(fy - iy)
        fun c(i: Int, j: Int) = hash(Math.floorMod(i, cx), Math.floorMod(j, cy), seed)
        val a = c(ix, iy); val b = c(ix + 1, iy); val cc = c(ix, iy + 1); val d = c(ix + 1, iy + 1)
        val top = a + (b - a) * tx
        return top + ((cc + (d - cc) * tx) - top) * ty
    }

    /** Kesirli Brown hareketi (periyodik), 0..1. */
    private fun fbm(u: Float, v: Float, cells: Int, octaves: Int, seed: Int, gain: Float = 0.5f, cellsY: Int = cells): Float {
        var sum = 0f; var amp = 1f; var norm = 0f; var c = cells; var cy = cellsY
        for (o in 0 until octaves) {
            sum += amp * vnoise(u, v, c, cy, seed + o * 97)
            norm += amp; amp *= gain; c *= 2; cy *= 2
        }
        return sum / norm
    }

    private fun smoothstep(a: Float, b: Float, x: Float): Float = smooth(((x - a) / (b - a)).coerceIn(0f, 1f))

    private fun argb(r: Float, g: Float, b: Float, a: Float = 1f): Int =
        ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or ((r * 255f).roundToInt().coerceIn(0, 255) shl 16) or
            ((g * 255f).roundToInt().coerceIn(0, 255) shl 8) or (b * 255f).roundToInt().coerceIn(0, 255)

    private fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(s: Int) = ((a shr s and 0xFF) + ((b shr s and 0xFF) - (a shr s and 0xFF)) * k).roundToInt().coerceIn(0, 255)
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun shade(c: Int, f: Float): Int {
        fun ch(s: Int) = ((c shr s and 0xFF) * f).roundToInt().coerceIn(0, 255)
        return (c and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Renk geçişi (duraklar [stops] 0..1). */
    private fun ramp(colors: IntArray, stops: FloatArray, t: Float): Int {
        if (t <= stops[0]) return colors[0]
        for (i in 1 until stops.size) if (t <= stops[i]) return mix(colors[i - 1], colors[i], (t - stops[i - 1]) / (stops[i] - stops[i - 1]))
        return colors.last()
    }

    // ================================================================================================ çizim yüzeyi

    /** Önçarpımsız ARGB piksel tablosu; şekiller işaretli uzaklıkla yumuşak kenarlı çizilir. */
    private class Raster(val w: Int, val h: Int) {
        val px = IntArray(w * h)

        inline fun each(f: (x: Int, y: Int) -> Int) {
            var i = 0
            for (y in 0 until h) for (x in 0 until w) { px[i] = f(x, y); i++ }
        }

        /** Kaynak-üstüne karıştırma. */
        fun blend(i: Int, c: Int, alpha: Float) {
            val sa = ((c ushr 24) / 255f) * alpha
            if (sa <= 0.002f) return
            val d = px[i]
            val da = (d ushr 24) / 255f
            val oa = sa + da * (1f - sa)
            if (oa <= 0f) return
            fun ch(s: Int): Int = (((c shr s and 0xFF) * sa + (d shr s and 0xFF) * da * (1f - sa)) / oa).roundToInt().coerceIn(0, 255)
            px[i] = ((oa * 255f).roundToInt().coerceIn(0, 255) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        /** [sdf] < 0 içeride; kenar 1 piksel yumuşak. Renk [color] fonksiyonuyla (yerinde gölgelendirme). */
        inline fun shape(l: Int, t: Int, r: Int, b: Int, sdf: (Float, Float) -> Float, color: (Int, Int) -> Int, alpha: Float = 1f) {
            for (y in max(0, t) until min(h, b)) for (x in max(0, l) until min(w, r)) {
                val d = sdf(x + 0.5f, y + 0.5f)
                val cov = (0.5f - d).coerceIn(0f, 1f)
                if (cov > 0f) blend(y * w + x, color(x, y), cov * alpha)
            }
        }

        /** Yumuşak gölge: [sdf] şeklinin [blur] piksel yayılmış hali. */
        inline fun shadow(sdf: (Float, Float) -> Float, blur: Float, alpha: Float, color: Int = 0xFF000000.toInt()) {
            for (y in 0 until h) for (x in 0 until w) {
                val d = sdf(x + 0.5f, y + 0.5f)
                if (d > blur) continue
                val k = 1f - smoothstepS(-blur * 0.6f, blur, d)
                if (k > 0f) blend(y * w + x, color, k * alpha)
            }
        }

        fun smoothstepS(a: Float, b: Float, x: Float): Float {
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }

    private fun roundRect(x: Float, y: Float, l: Float, t: Float, r: Float, b: Float, rad: Float): Float {
        val cx = (l + r) / 2f; val cy = (t + b) / 2f
        val qx = abs(x - cx) - ((r - l) / 2f - rad); val qy = abs(y - cy) - ((b - t) / 2f - rad)
        val ox = max(qx, 0f); val oy = max(qy, 0f)
        return sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - rad
    }

    private fun segment(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val px = x - ax; val py = y - ay; val dx = bx - ax; val dy = by - ay
        val t = ((px * dx + py * dy) / (dx * dx + dy * dy).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
        val ex = px - dx * t; val ey = py - dy * t
        return sqrt(ex * ex + ey * ey)
    }

    // ================================================================================================ not tuvali

    private fun cream(n: Int): IntArray {
        val base = 0xFFFBF6E9.toInt()
        val r = Raster(n, n)
        r.each { x, y ->
            val u = x / n.toFloat(); val v = y / n.toFloat()
            var f = 1f + (fbm(u, v, 8, 4, 11) - 0.5f) * 0.05f
            f += (vnoise(u, v, 64, 6, 13) - 0.5f) * 0.025f + (vnoise(u, v, 6, 64, 17) - 0.5f) * 0.02f
            if (hash(x, y, 19) > 0.9975f) f -= 0.07f
            shade(base, f)
        }
        return r.px
    }

    private fun recycled(n: Int): IntArray {
        val base = 0xFFECE8DF.toInt()
        val flecks = intArrayOf(0xFF8C8478.toInt(), 0xFF6F7F94.toInt(), 0xFFA28A6A.toInt(), 0xFFB9B2A6.toInt())
        val cells = 48
        val cell = n / cells.toFloat()
        val r = Raster(n, n)
        r.each { x, y ->
            val u = x / n.toFloat(); val v = y / n.toFloat()
            var c = shade(base, 1f + (fbm(u, v, 6, 4, 23) - 0.5f) * 0.07f + (vnoise(u, v, 96, 96, 29) - 0.5f) * 0.03f)
            val ci = (x / cell).toInt().coerceAtMost(cells - 1); val cj = (y / cell).toInt().coerceAtMost(cells - 1)
            if (hash(ci, cj, 31) > 0.55f) {
                val fx = (ci + 0.2f + 0.6f * hash(ci, cj, 37)) * cell; val fy = (cj + 0.2f + 0.6f * hash(ci, cj, 41)) * cell
                val rad = 0.5f + 1.2f * hash(ci, cj, 43)
                val d = sqrt((x + 0.5f - fx) * (x + 0.5f - fx) + (y + 0.5f - fy) * (y + 0.5f - fy)) - rad
                val cov = (0.5f - d).coerceIn(0f, 1f)
                if (cov > 0f) c = mix(c, flecks[(hash(ci, cj, 47) * flecks.size).toInt().coerceAtMost(flecks.size - 1)], cov * 0.55f)
            }
            c
        }
        return r.px
    }

    private fun kraft(n: Int): IntArray {
        val base = 0xFFE2CCA4.toInt()
        val r = Raster(n, n)
        r.each { x, y ->
            val u = x / n.toFloat(); val v = y / n.toFloat()
            var f = 1f + (fbm(u, v, 5, 4, 53) - 0.5f) * 0.08f
            f += (vnoise(u, v, 128, 8, 59) - 0.5f) * 0.05f + (vnoise(u, v, 8, 128, 61) - 0.5f) * 0.035f
            if (hash(x, y, 67) > 0.996f) f -= 0.12f
            shade(base, f)
        }
        return r.px
    }

    /** Dokuma (keten, tuval bezi): [threads] iplik, [depth] gölge derinliği. */
    private fun weave(n: Int, threads: Int, base: Int, depth: Float, seed: Int): IntArray {
        val r = Raster(n, n)
        r.each { x, y ->
            val u = x / n.toFloat(); val v = y / n.toFloat()
            val su = sin(u * threads * 2 * PI).toFloat(); val sv = sin(v * threads * 2 * PI).toFloat()
            // Üstteki iplik dama düzeninde değişir
            val over = ((floor(u * threads).toInt() + floor(v * threads).toInt()) and 1) == 0
            val warp = 0.5f + 0.5f * abs(su); val weft = 0.5f + 0.5f * abs(sv)
            val slubH = vnoise(u, v, 3, threads, seed); val slubV = vnoise(u, v, threads, 3, seed + 5)
            val thread = if (over) weft * (0.8f + 0.4f * slubH) else warp * (0.8f + 0.4f * slubV)
            var f = 1f - depth * (1f - thread)
            f += (fbm(u, v, 4, 3, seed + 9) - 0.5f) * 0.04f
            shade(base, f)
        }
        return r.px
    }

    private fun rice(n: Int): IntArray {
        val base = 0xFFFAF8F2.toInt()
        val r = Raster(n, n)
        r.each { x, y ->
            val u = x / n.toFloat(); val v = y / n.toFloat()
            shade(base, 1f + (fbm(u, v, 6, 4, 73) - 0.5f) * 0.035f)
        }
        // Uzun, ince lifler (kenarlardan taşanlar karşı kenarda devam eder)
        for (k in 0 until 150) {
            var x = hash(k, 1, 79) * n; var y = hash(k, 2, 79) * n
            var a = hash(k, 3, 79) * 2f * PI.toFloat()
            val width = 0.5f + hash(k, 4, 79) * 0.9f
            val dark = hash(k, 5, 79) > 0.55f
            val color = if (dark) 0xFFD9D2C2.toInt() else 0xFFFFFFFF.toInt()
            val alpha = if (dark) 0.35f + 0.3f * hash(k, 6, 79) else 0.8f
            val steps = 8 + (hash(k, 7, 79) * 16).toInt()
            repeat(steps) { s ->
                a += (hash(k, 100 + s, 83) - 0.5f) * 0.7f
                val nx = x + cos(a) * 7f; val ny = y + sin(a) * 7f
                for (ox in -1..1) for (oy in -1..1) {
                    val ax = x + ox * n; val ay = y + oy * n; val bx = nx + ox * n; val by = ny + oy * n
                    val l = (min(ax, bx) - 3).toInt(); val t = (min(ay, by) - 3).toInt()
                    val rr = (max(ax, bx) + 4).toInt(); val bb = (max(ay, by) + 4).toInt()
                    if (rr < 0 || bb < 0 || l >= n || t >= n) continue
                    r.shape(l, t, rr, bb, { px, py -> segment(px, py, ax, ay, bx, by) - width / 2f }, { _, _ -> color }, alpha)
                }
                x = nx; y = ny
            }
        }
        return r.px
    }

    private fun parchment(w: Int, h: Int): IntArray {
        val light = 0xFFF5E7C6.toInt(); val dark = 0xFFD4B47C.toInt()
        val r = Raster(w, h)
        r.each { x, y ->
            val u = x / w.toFloat(); val v = y / h.toFloat()
            val blot = fbm(u, v, 3, 5, 89)
            var c = mix(light, dark, smoothstep(0.45f, 0.85f, blot) * 0.55f)
            c = shade(c, 1f + (vnoise(u, v, 90, 90, 97) - 0.5f) * 0.03f)
            // Kenarlara doğru koyulaşma (yazı alanı açık kalır)
            val ex = min(u, 1f - u) * 2f; val ey = min(v, 1f - v) * 2f
            val edge = 1f - smoothstep(0f, 0.32f, min(ex, ey * 1.2f) + (fbm(u, v, 6, 3, 101) - 0.5f) * 0.12f)
            mix(c, 0xFFA97E45.toInt(), edge * 0.55f)
        }
        return r.px
    }

    private fun wash(w: Int, h: Int): IntArray {
        val blue = 0xFFB4D0EC.toInt(); val peach = 0xFFF6CDB4.toInt()
        val r = Raster(w, h)
        r.each { x, y ->
            val u = x / w.toFloat(); val v = y / h.toFloat()
            var c = shade(0xFFFFFFFF.toInt(), 1f + (fbm(u, v, 10, 3, 103) - 0.5f) * 0.025f)
            fun bloom(cx: Float, cy: Float, rad: Float, color: Int, seed: Int) {
                val dx = (u - cx) * w / h; val dy = v - cy
                val d = sqrt(dx * dx + dy * dy) + (fbm(u, v, 4, 4, seed) - 0.5f) * 0.18f
                val inside = 1f - smoothstep(rad * 0.75f, rad, d)
                // Suluboya kenarı: sınırda biraz daha koyu
                val rim = smoothstep(rad * 0.82f, rad * 0.97f, d) * (1f - smoothstep(rad * 0.97f, rad * 1.02f, d))
                val a = inside * (0.38f + 0.12f * fbm(u, v, 8, 3, seed + 3)) + rim * 0.25f
                c = mix(c, color, a)
            }
            bloom(0f, 0f, 0.42f, blue, 107)
            bloom(1f, 1f, 0.40f, peach, 113)
            c
        }
        return r.px
    }

    private fun gradient(w: Int, h: Int, colors: IntArray, stops: FloatArray): IntArray {
        val r = Raster(w, h)
        r.each { x, y ->
            val c = ramp(colors, stops, y / (h - 1f))
            // Bantlaşmayı önleyen hafif titreşim
            shade(c, 1f + (hash(x, y, 127) - 0.5f) * 0.012f)
        }
        return r.px
    }

    // ================================================================================================ resim tuvali

    private fun coldPress(n: Int): IntArray {
        val base = 0xFFFBFAF6.toInt()
        val hgt = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val u = x / n.toFloat(); val v = y / n.toFloat()
            hgt[y * n + x] = fbm(u, v, 16, 4, 131, 0.55f)
        }
        val r = Raster(n, n)
        r.each { x, y ->
            val dx = hgt[y * n + (x + 1) % n] - hgt[y * n + (x - 1 + n) % n]
            val dy = hgt[((y + 1) % n) * n + x] - hgt[((y - 1 + n) % n) * n + x]
            // Sol üstten ışık
            val light = (-dx - dy) * 2.1f
            shade(base, 0.972f + light.coerceIn(-0.045f, 0.04f))
        }
        return r.px
    }

    private fun charcoal(n: Int): IntArray {
        val base = 0xFFCFCAC1.toInt()
        val r = Raster(n, n)
        r.each { x, y ->
            val u = x / n.toFloat(); val v = y / n.toFloat()
            var f = 1f + (fbm(u, v, 8, 3, 137) - 0.5f) * 0.05f
            f += (vnoise(u, v, 160, 160, 139) - 0.5f) * 0.08f
            // İnce dikey çizgiler (Ingres kağıdı)
            f += sin(u * 64 * 2 * PI).toFloat() * 0.012f
            shade(base, f)
        }
        return r.px
    }

    private fun sunset(n: Int): IntArray {
        val colors = intArrayOf(0xFF2D3A86.toInt(), 0xFF7A4C9C.toInt(), 0xFFD4618A.toInt(), 0xFFF29457.toInt(), 0xFFFAD07A.toInt(), 0xFFFCE6B0.toInt())
        val stops = floatArrayOf(0f, 0.28f, 0.5f, 0.68f, 0.82f, 1f)
        val r = Raster(n, n)
        r.each { x, y ->
            val u = x / n.toFloat(); val v = y / n.toFloat()
            var c = ramp(colors, stops, v + (fbm(u, v, 3, 3, 149, cellsY = 6) - 0.5f) * 0.05f)
            val dx = u - 0.5f; val dy = (v - 0.8f) * 1.3f
            val glow = exp(-(dx * dx + dy * dy) * 14f)
            c = mix(c, 0xFFFFF4D6.toInt(), glow * 0.65f)
            shade(c, 1f + (hash(x, y, 151) - 0.5f) * 0.012f)
        }
        return r.px
    }

    private fun night(n: Int): IntArray {
        val r = Raster(n, n)
        r.each { x, y ->
            val u = x / n.toFloat(); val v = y / n.toFloat()
            var c = mix(0xFF0A1130.toInt(), 0xFF263463.toInt(), v)
            // Samanyolu: köşegen boyunca soluk bulut
            val band = exp(-((u - v) * (u - v)) * 18f)
            c = mix(c, 0xFF5B6AA6.toInt(), band * fbm(u, v, 5, 5, 157) * 0.5f)
            shade(c, 1f + (hash(x, y, 163) - 0.5f) * 0.02f)
        }
        val cells = 80
        val cell = n / cells.toFloat()
        for (j in 0 until cells) for (i in 0 until cells) {
            if (hash(i, j, 167) < 0.78f) continue
            val sx = (i + 0.15f + 0.7f * hash(i, j, 173)) * cell; val sy = (j + 0.15f + 0.7f * hash(i, j, 179)) * cell
            val big = hash(i, j, 181) > 0.95f
            val rad = if (big) 1.4f else 0.45f + 0.6f * hash(i, j, 191)
            val bright = 0.55f + 0.45f * hash(i, j, 193)
            val tint = if (hash(i, j, 197) > 0.7f) 0xFFFFE9C4.toInt() else 0xFFF2F5FF.toInt()
            val reach = if (big) 9 else 4
            r.shape((sx - reach).toInt(), (sy - reach).toInt(), (sx + reach + 1).toInt(), (sy + reach + 1).toInt(),
                { px, py -> sqrt((px - sx) * (px - sx) + (py - sy) * (py - sy)) - rad }, { _, _ -> tint }, bright)
            // Parıltı
            for (py in (sy - reach).toInt()..(sy + reach).toInt()) for (px in (sx - reach).toInt()..(sx + reach).toInt()) {
                if (px !in 0 until n || py !in 0 until n) continue
                val dx = px + 0.5f - sx; val dy = py + 0.5f - sy
                var g = exp(-(dx * dx + dy * dy) / (rad * rad * 6f)) * 0.35f
                if (big) g += (exp(-dy * dy * 3f) * exp(-abs(dx) / 3f) + exp(-dx * dx * 3f) * exp(-abs(dy) / 3f)) * 0.25f
                r.blend(py * n + px, tint, g * bright)
            }
        }
        return r.px
    }

    private fun clouds(n: Int): IntArray {
        val r = Raster(n, n)
        r.each { x, y ->
            val u = x / n.toFloat(); val v = y / n.toFloat()
            var c = mix(0xFF6FA6DA.toInt(), 0xFFD3E8F8.toInt(), v)
            val f = fbm(u, v * 1.6f, 4, 6, 211, 0.52f)
            val cloud = smoothstep(0.5f, 0.72f, f)
            // Bulutun altı biraz gölgeli
            val under = fbm(u, (v + 0.02f) * 1.6f, 4, 6, 211, 0.52f)
            val shadeK = 1f - 0.12f * smoothstep(0.55f, 0.8f, under) * (1f - smoothstep(0.6f, 0.85f, f))
            c = mix(c, shade(0xFFFFFFFF.toInt(), shadeK), cloud * 0.95f)
            shade(c, 1f + (hash(x, y, 223) - 0.5f) * 0.01f)
        }
        return r.px
    }

    private fun marble(n: Int): IntArray {
        val base = 0xFFF5F3EF.toInt(); val vein = 0xFF8E949B.toInt()
        val r = Raster(n, n)
        r.each { x, y ->
            val u = x / n.toFloat(); val v = y / n.toFloat()
            val t = fbm(u, v, 3, 6, 227)
            val s = abs(sin(((u * 2 + v * 1) * 2 * PI + t * 9).toFloat()))
            val main = 1f - smoothstep(0f, 0.09f, s)
            val s2 = abs(sin(((u * 1 - v * 3) * 2 * PI + fbm(u, v, 4, 5, 229) * 11).toFloat()))
            val thin = 1f - smoothstep(0f, 0.04f, s2)
            var c = shade(base, 1f + (fbm(u, v, 6, 3, 233) - 0.5f) * 0.05f)
            c = mix(c, vein, main * 0.55f + thin * 0.3f)
            c
        }
        return r.px
    }

    private fun woodTable(n: Int): IntArray {
        val light = 0xFFCDA06A.toInt(); val dark = 0xFF94663A.toInt()
        val planks = 4
        val r = Raster(n, n)
        r.each { x, y ->
            val u = x / n.toFloat(); val v = y / n.toFloat()
            val pi = floor(v * planks).toInt().coerceAtMost(planks - 1)
            val local = v * planks - pi
            val warp = fbm(u, v, 2, 4, 241 + pi * 7, cellsY = 8) * 1.6f
            // İnce yıllık halkalar: kesirli kısmın keskin kenarı koyu çizgi
            val ring = ((local * 9f + warp) % 1f + 1f) % 1f
            val line = smoothstep(0.82f, 0.97f, ring) * (1f - smoothstep(0.97f, 1f, ring))
            val fine = vnoise(u, v, 3, 220, 251 + pi)
            val tone = fbm(u, v, 4, 3, 259 + pi, cellsY = 16)
            var c = mix(light, dark, tone * 0.35f + line * 0.45f + fine * 0.18f + hash(pi, 0, 257) * 0.12f)
            // Tahta araları ve uç ekleri
            val seam = min(local, 1f - local) * n / planks
            if (seam < 1.6f) c = shade(c, 0.55f + 0.28f * seam)
            val joint = (hash(pi, 1, 263) + 0.5f * (pi and 1)) % 1f
            val dj = abs(u - joint) * n
            if (min(dj, n - dj) < 1.3f) c = shade(c, 0.62f)
            c
        }
        return r.px
    }

    // ================================================================================================ yazı kutusu

    private fun paperBase(color: Int, seed: Int, periodX: Float = 0f, periodY: Float = 0f): (Int, Int) -> Int = { x, y ->
        val u = if (periodX > 0f) x / periodX else x / 97f
        val v = if (periodY > 0f) y / periodY else y / 97f
        shade(color, 1f + (vnoise(u, v, 6, 6, seed) - 0.5f) * 0.03f)
    }

    private fun sticky(color: Int): IntArray {
        val w = 200; val h = 200
        val r = Raster(w, h)
        val l = 8f; val t = 6f; val rr = 190f; val bb = 188f
        r.shadow({ x, y -> roundRect(x, y, l + 2f, t + 5f, rr + 1f, bb + 4f, 2f) }, 9f, 0.22f)
        // Sağ alt köşe kalkık: gölge orada daha belirgin
        r.shadow({ x, y -> roundRect(x, y, rr - 22f, bb - 18f, rr + 2f, bb + 5f, 10f) }, 6f, 0.1f)
        val base = paperBase(color, 271)
        r.shape(0, 0, w, h, { x, y -> roundRect(x, y, l, t, rr, bb, 1.5f) }, { x, y ->
            var c = base(x, y)
            // Yapışkan şerit (üst) biraz koyu
            if (y < t + 24) c = shade(c, 0.975f)
            // Kalkık köşe: köşegen boyunca aydınlık-gölge
            val d = ((rr - x) + (bb - y)) / 56f
            if (d < 1f) c = shade(c, 0.9f + 0.1f * d + if (d < 0.25f) 0.06f else 0f)
            c
        })
        return r.px
    }

    private fun taped(): IntArray {
        val w = 220; val h = 200
        val r = Raster(w, h)
        val l = 10f; val t = 12f; val rr = 208f; val bb = 188f
        r.shadow({ x, y -> roundRect(x, y, l + 1f, t + 4f, rr + 1f, bb + 4f, 2f) }, 8f, 0.2f)
        val base = paperBase(0xFFFFFEFA.toInt(), 277)
        r.shape(0, 0, w, h, { x, y -> roundRect(x, y, l, t, rr, bb, 1f) }, base)
        fun tape(cx: Float, cy: Float, angle: Float, color: Int) {
            val ca = cos(angle); val sa = sin(angle)
            val hl = 28f; val hw = 9.5f
            r.shape((cx - 34).toInt(), (cy - 34).toInt(), (cx + 34).toInt(), (cy + 34).toInt(), { x, y ->
                val dx = x - cx; val dy = y - cy
                val a = dx * ca + dy * sa; val b = -dx * sa + dy * ca
                // Uçları tırtıklı
                val jag = abs(((b * 0.9f) % 3f + 3f) % 3f - 1.5f) * 0.8f
                max(abs(a) - hl + jag, abs(b) - hw)
            }, { x, y ->
                val dx = x - cx; val dy = y - cy
                val b = -dx * sa + dy * ca
                if (((b + 20f) / 4f).toInt() % 2 == 0) shade(color, 0.94f) else color
            }, 0.78f)
        }
        tape(30f, 20f, (-38 * PI / 180).toFloat(), 0xFF9BD3C8.toInt())
        tape(190f, 20f, (38 * PI / 180).toFloat(), 0xFFF5B7C5.toInt())
        return r.px
    }

    private fun spiral(): IntArray {
        val w = 240; val h = 200
        val r = Raster(w, h)
        val l = 6f; val t = 22f; val rr = 234f; val bb = 192f
        // Gölge ve kağıt (yatayda 168 piksel periyotlu; orta parçalar tekrar ederken dikiş olmaz)
        r.shadow({ x, y -> roundRect(x, y, l + 1f, t + 4f, rr + 1f, bb + 4f, 2f) }, 7f, 0.2f)
        val base = paperBase(0xFFFFFFFF.toInt(), 281, periodX = 168f, periodY = 128f)
        r.shape(0, 0, w, h, { x, y -> roundRect(x, y, l, t, rr, bb, 1.5f) }, base)
        // Delikler ve tel
        var hx = 12f
        while (hx < w + 12f) {
            val cx = hx
            r.shape((cx - 7).toInt(), 28, (cx + 8).toInt(), 42, { x, y -> sqrt((x - cx) * (x - cx) + (y - 35f) * (y - 35f)) - 4.5f }, { _, _ -> 0xFF4D5258.toInt() }, 0.85f)
            r.shape((cx - 8).toInt(), 0, (cx + 8).toInt(), 40, { x, y ->
                // Telin kavisi: üstten deliğe
                val k = ((35f - y) / 31f).coerceIn(0f, 1f)
                val wx = cx + 5f * sin(k * PI.toFloat())
                val inside = if (y < 4f || y > 35f) 99f else abs(x - wx) - 2.4f
                inside
            }, { x, _ ->
                val k = ((x - cx) / 6f).coerceIn(-1f, 1f)
                mix(0xFFE9ECEF.toInt(), 0xFF7E8690.toInt(), 0.5f + 0.5f * k)
            })
            hx += 24f
        }
        return r.px
    }

    /** Periyodik yırtık kenar yüksekliği (periyot [period] piksel). */
    private fun tornEdge(x: Float, period: Float, seed: Int): Float {
        val u = x / period
        return (vnoise(u, 0.5f, 6, 1, seed) - 0.5f) * 5f + (vnoise(u, 0.5f, 24, 1, seed + 1) - 0.5f) * 2.5f + (vnoise(u, 0.5f, 60, 1, seed + 2) - 0.5f) * 1.2f
    }

    private fun torn(): IntArray {
        val w = 200; val h = 200
        val r = Raster(w, h)
        val period = 120f
        val top = 18f; val bottom = 182f; val l = 8f; val rr = 192f
        fun sdf(x: Float, y: Float, dy: Float): Float {
            val tt = top + tornEdge(x, period, 293) + dy
            val bt = bottom + tornEdge(x + 37f, period, 307) + dy
            val vertical = max(tt - y, y - bt)
            val periodY = 132f
            val lt = l + tornEdge(y, periodY, 311) * 0.25f
            val rt = rr + tornEdge(y + 11f, periodY, 313) * 0.25f
            return max(vertical, max(lt - x, x - rt))
        }
        r.shadow({ x, y -> sdf(x, y, 3f) }, 6f, 0.2f)
        val base = paperBase(0xFFFDFCF7.toInt(), 317, periodX = period, periodY = 132f)
        r.shape(0, 0, w, h, { x, y -> sdf(x, y, 0f) }, { x, y ->
            // Yırtık kenarda lifli, daha açık şerit
            val d = -sdf(x + 0.5f, y + 0.5f, 0f)
            if (d < 2.2f) 0xFFFFFFFF.toInt() else base(x, y)
        })
        return r.px
    }

    private fun kraftLabel(): IntArray {
        val w = 220; val h = 160
        val r = Raster(w, h)
        val px = 140f; val py = 80f
        r.shadow({ x, y -> roundRect(x, y, 7f, 8f, 214f, 155f, 14f) }, 6f, 0.2f)
        r.shape(0, 0, w, h, { x, y -> roundRect(x, y, 6f, 5f, 214f, 152f, 14f) }, { x, y ->
            val u = x / px; val v = y / py
            var f = 1f + (vnoise(u, v, 4, 2, 331) - 0.5f) * 0.06f + (vnoise(u, v, 28, 2, 337) - 0.5f) * 0.05f
            if (hash(Math.floorMod(x, 140), Math.floorMod(y, 80), 347) > 0.996f) f -= 0.1f
            shade(0xFFD5B588.toInt(), f)
        })
        // Dikiş: 10 piksel periyotlu kesik çizgi
        r.shape(0, 0, w, h, { x, y ->
            val d = abs(roundRect(x, y, 16f, 15f, 204f, 142f, 8f)) - 0.9f
            val along = if (abs(y - 15f) < 3f || abs(y - 142f) < 3f) x else y
            val dash = if (((along % 10f) + 10f) % 10f < 6f) 0f else 9f
            d + dash
        }, { _, _ -> 0xFFFFF6E2.toInt() }, 0.9f)
        return r.px
    }

    private fun indexCard(): IntArray {
        val w = 240; val h = 180
        val r = Raster(w, h)
        r.shadow({ x, y -> roundRect(x, y, 7f, 8f, 233f, 176f, 3f) }, 6f, 0.2f)
        r.shape(0, 0, w, h, { x, y -> roundRect(x, y, 6f, 5f, 234f, 173f, 3f) }, { x, y ->
            var c = 0xFFFFFFFF.toInt()
            val yy = y + 0.5f
            if (abs(yy - 34f) < 0.9f) c = mix(c, 0xFFE57373.toInt(), 0.85f)
            else if (yy > 46f) {
                val k = ((yy - 52f) % 18f + 18f) % 18f
                if (k < 1.1f || k > 17.6f) c = mix(c, 0xFF8FB3D9.toInt(), 0.7f)
            }
            shade(c, 1f + (vnoise(x / 200f, y / 108f, 4, 4, 353) - 0.5f) * 0.02f)
        })
        return r.px
    }

    private fun clipped(): IntArray {
        val w = 220; val h = 200
        val r = Raster(w, h)
        val l = 10f; val t = 16f; val rr = 208f; val bb = 188f
        r.shadow({ x, y -> roundRect(x, y, l + 1f, t + 4f, rr + 1f, bb + 4f, 2f) }, 8f, 0.2f)
        r.shape(0, 0, w, h, { x, y -> roundRect(x, y, l, t, rr, bb, 1f) }, paperBase(0xFFFFFDF8.toInt(), 359))
        // Ataç: iç içe iki uzun halka (kapsül çevresi)
        val ang = (-12 * PI / 180).toFloat()
        val ca = cos(ang); val sa = sin(ang)
        val cx = 40f; val cy = 22f
        fun loop(x: Float, y: Float, half: Float, rad: Float, oy: Float): Float {
            val dx = x - cx; val dy = y - cy
            val a = dx * ca + dy * sa; val b = -dx * sa + dy * ca - oy
            val seg = segment(a, b, 0f, -half, 0f, half)
            return abs(seg - rad)
        }
        val sdf = { x: Float, y: Float -> min(loop(x, y, 18f, 7f, 0f), max(loop(x, y, 12f, 4f, 4f), -1f)) - 1.6f }
        r.shadow({ x, y -> sdf(x - 1.5f, y - 2f) }, 3f, 0.25f)
        r.shape(20, 0, 64, 60, sdf, { x, y -> mix(0xFFEEF1F4.toInt(), 0xFF8A939C.toInt(), ((x + y) % 7) / 7f * 0.4f + 0.2f) })
        return r.px
    }

    private fun card(): IntArray {
        val w = 200; val h = 200
        val r = Raster(w, h)
        r.shadow({ x, y -> roundRect(x, y, 9f, 10f, 192f, 194f, 16f) }, 8f, 0.18f)
        r.shape(0, 0, w, h, { x, y -> roundRect(x, y, 8f, 6f, 192f, 190f, 16f) }, { _, _ -> 0xFF6FA3D8.toInt() })
        r.shape(0, 0, w, h, { x, y -> roundRect(x, y, 11f, 9f, 189f, 187f, 13f) }, { _, _ -> 0xFFF6FAFF.toInt() })
        return r.px
    }

    private fun notebook(): IntArray {
        val w = 240; val h = 220
        val r = Raster(w, h)
        val l = 6f; val t = 6f; val rr = 234f; val bb = 214f
        r.shadow({ x, y -> roundRect(x, y, l + 1f, t + 4f, rr + 1f, bb + 4f, 2f) }, 6f, 0.2f)
        r.shape(0, 0, w, h, { x, y -> roundRect(x, y, l, t, rr, bb, 1.5f) }, { x, y ->
            var c = 0xFFFFFFFF.toInt()
            val yy = y + 0.5f; val xx = x + 0.5f
            val k = ((yy - 55f) % 22f + 22f) % 22f
            if (yy > 40f && yy < 205f && (k < 1f || k > 21.6f)) c = mix(c, 0xFF9DBEE0.toInt(), 0.75f)
            if (abs(xx - 46f) < 0.9f) c = mix(c, 0xFFE57373.toInt(), 0.8f)
            shade(c, 1f + (vnoise(x / 164f, y / 132f, 4, 4, 367) - 0.5f) * 0.02f)
        })
        // Delikler (44 piksel periyot)
        var hy = 22f
        while (hy < h + 22f) {
            val cy = hy
            r.shape(8, (cy - 8).toInt(), 26, (cy + 9).toInt(), { x, y -> sqrt((x - 17f) * (x - 17f) + (y - cy) * (y - cy)) - 5.5f },
                { _, _ -> 0xFFDFE3E8.toInt() })
            hy += 44f
        }
        return r.px
    }
}
