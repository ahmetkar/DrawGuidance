package com.karapp.drawguidance.content.model3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Doku: [w]×[h] ARGB piksel (kenarları döşenebilir). */
class Texture(val w: Int, val h: Int, val px: IntArray) {
    init { require(px.size == w * h) }
}

/** Hazır dokunun tanımı: kimlik, ad, kategori ve üretici. */
class TextureDef(val id: String, val name: String, val category: String, val generate: (Int) -> Texture)

/**
 * v23 hazır 3B dokuları: program içinde üretilen, kenarları birbirine uyan (döşenebilir) resimler. Uygulama ilk açılışta
 * bunları üretip veritabanına sıkıştırarak yazar; doku seçme ekranı oradan okur. Üretim: döşenebilir değer gürültüsü
 * (periyodik kafes), kesirli Brown hareketi (fbm), alan bükme ve basit geometri (tuğla, karo, altıgen).
 */
object TextureCatalog {

    const val VERSION = 1
    const val SIZE = 256

    private const val WOOD = "Ahşap"
    private const val STONE = "Taş ve yapı"
    private const val METAL = "Metal"
    private const val FABRIC = "Kumaş ve deri"
    private const val NATURE = "Doğa"
    private const val PATTERN = "Desen"

    val all: List<TextureDef> = listOf(
        TextureDef("wood.oak", "Meşe", WOOD) { wood(it, 0xFFB98B55.toInt(), 0xFF8A5C2E.toInt(), 7f, 11) },
        TextureDef("wood.walnut", "Ceviz", WOOD) { wood(it, 0xFF7A4E2D.toInt(), 0xFF4A2C17.toInt(), 9f, 23) },
        TextureDef("wood.pine", "Çam", WOOD) { wood(it, 0xFFE2C08A.toInt(), 0xFFB98A4E.toInt(), 5f, 37) },
        TextureDef("wood.planks", "Tahta döşeme", WOOD) { planks(it) },
        TextureDef("stone.marble", "Mermer", STONE) { marble(it, 0xFFF1EFEA.toInt(), 0xFF9AA0A6.toInt(), 3) },
        TextureDef("stone.marble_dark", "Siyah mermer", STONE) { marble(it, 0xFF2B2D31.toInt(), 0xFFD9D4C7.toInt(), 5) },
        TextureDef("stone.granite", "Granit", STONE) { granite(it) },
        TextureDef("stone.brick", "Tuğla", STONE) { brick(it) },
        TextureDef("stone.cobble", "Parke taşı", STONE) { cobble(it) },
        TextureDef("stone.tiles", "Seramik karo", STONE) { tiles(it) },
        TextureDef("stone.concrete", "Beton", STONE) { noiseColor(it, 0xFFA7A9AC.toInt(), 0xFF8B8E92.toInt(), 6, 0.9f, 41) },
        TextureDef("metal.brushed", "Fırçalanmış metal", METAL) { brushed(it, 0xFFC9CDD2.toInt()) },
        TextureDef("metal.gold", "Altın", METAL) { brushed(it, 0xFFE2B84B.toInt()) },
        TextureDef("metal.rust", "Paslı demir", METAL) { rust(it) },
        TextureDef("metal.plate", "Baklavalı sac", METAL) { diamondPlate(it) },
        TextureDef("fabric.canvas", "Kanvas", FABRIC) { weave(it, 0xFFE9DFC8.toInt(), 0xFFCDBF9F.toInt(), 4) },
        TextureDef("fabric.denim", "Kot", FABRIC) { denim(it) },
        TextureDef("fabric.knit", "Örgü", FABRIC) { knit(it) },
        TextureDef("fabric.leather", "Deri", FABRIC) { leather(it) },
        TextureDef("fabric.plaid", "Ekose", FABRIC) { plaid(it) },
        TextureDef("nature.grass", "Çimen", NATURE) { grass(it) },
        TextureDef("nature.sand", "Kum", NATURE) { noiseColor(it, 0xFFE6CF9A.toInt(), 0xFFC9AC70.toInt(), 5, 0.6f, 53) },
        TextureDef("nature.water", "Su", NATURE) { water(it) },
        TextureDef("nature.lava", "Lav", NATURE) { lava(it) },
        TextureDef("nature.ice", "Buz", NATURE) { ice(it) },
        TextureDef("nature.bark", "Ağaç kabuğu", NATURE) { bark(it) },
        TextureDef("pattern.checker", "Dama", PATTERN) { checker(it) },
        TextureDef("pattern.stripes", "Şerit", PATTERN) { stripes(it) },
        TextureDef("pattern.dots", "Puantiye", PATTERN) { dots(it) },
        TextureDef("pattern.carbon", "Karbon fiber", PATTERN) { carbon(it) },
        TextureDef("pattern.hex", "Petek", PATTERN) { hexes(it) },
    )

    fun byId(id: String): TextureDef? = all.firstOrNull { it.id == id }

    val categories: List<String> get() = all.map { it.category }.distinct()

    // ------------------------------------------------------------------------------------------------ gürültü

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) / 2147483647f
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)

    /** Periyodik değer gürültüsü: [x], [y] 0..1 dokuda, [cells] kafes sayısı (periyot). */
    private fun vnoise(x: Float, y: Float, cells: Int, seed: Int): Float = vnoise2(x, y, cells, cells, seed)

    /** Yatayda [cx], dikeyde [cy] kafesli periyodik değer gürültüsü (lifli/çizgili dokular). */
    private fun vnoise2(x: Float, y: Float, cx: Int, cy: Int, seed: Int): Float {
        val fx = x * cx; val fy = y * cy
        val ix = floor(fx).toInt(); val iy = floor(fy).toInt()
        val tx = smooth(fx - ix); val ty = smooth(fy - iy)
        fun c(i: Int, j: Int) = hash(Math.floorMod(i, cx), Math.floorMod(j, cy), seed)
        val a = c(ix, iy); val b = c(ix + 1, iy); val cc = c(ix, iy + 1); val d = c(ix + 1, iy + 1)
        return (a + (b - a) * tx) + ((cc + (d - cc) * tx) - (a + (b - a) * tx)) * ty
    }

    /** Kesirli Brown hareketi (döşenebilir), 0..1. */
    private fun fbm(x: Float, y: Float, cells: Int, octaves: Int, seed: Int, gain: Float = 0.5f): Float {
        var sum = 0f; var amp = 1f; var norm = 0f; var c = cells
        for (o in 0 until octaves) {
            sum += amp * vnoise(x, y, c, seed + o * 97)
            norm += amp; amp *= gain; c *= 2
        }
        return sum / norm
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val r = ((a shr 16 and 0xFF) + ((b shr 16 and 0xFF) - (a shr 16 and 0xFF)) * k).roundToInt()
        val g = ((a shr 8 and 0xFF) + ((b shr 8 and 0xFF) - (a shr 8 and 0xFF)) * k).roundToInt()
        val bl = ((a and 0xFF) + ((b and 0xFF) - (a and 0xFF)) * k).roundToInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun shade(c: Int, f: Float): Int {
        val r = ((c shr 16 and 0xFF) * f).roundToInt().coerceIn(0, 255)
        val g = ((c shr 8 and 0xFF) * f).roundToInt().coerceIn(0, 255)
        val b = ((c and 0xFF) * f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private inline fun make(n: Int, f: (Float, Float) -> Int): Texture {
        val px = IntArray(n * n)
        for (j in 0 until n) for (i in 0 until n) px[j * n + i] = f((i + 0.5f) / n, (j + 0.5f) / n)
        return Texture(n, n, px)
    }

    private fun fract(v: Float) = v - floor(v)

    // ------------------------------------------------------------------------------------------------ üreticiler

    private fun wood(n: Int, light: Int, dark: Int, rings: Float, seed: Int) = make(n) { x, y ->
        val warp = fbm(x, y, 4, 4, seed) - 0.5f
        val grain = fbm(x, y, 2, 3, seed + 5)
        // Halkalar dikey eksene yayılır (lifler yukarı doğru), döşenebilir olsun diye x periyodik
        val v = fract((x + warp * 0.25f) * rings + grain * 1.5f)
        val ring = smooth(abs(v * 2f - 1f))
        val fiber = vnoise(x * 1f, y, 64, seed + 9) * 0.25f
        lerpColor(light, dark, ring * 0.75f + fiber)
    }

    private fun planks(n: Int) = make(n) { x, y ->
        val rows = 4
        val row = floor(y * rows).toInt()
        val off = (row * 0.37f) % 1f
        val px = fract(x + off)
        val board = floor(px * 2).toInt()
        val base = if ((row + board) % 2 == 0) 0xFFB98B55.toInt() else 0xFFA87A47.toInt()
        val warp = fbm(px, y, 4, 3, 71 + row) - 0.5f
        val ring = smooth(abs(fract((y * rows - row) * 3f + warp * 0.8f + board * 0.3f) * 2f - 1f))
        var c = lerpColor(base, 0xFF6E4524.toInt(), ring * 0.45f)
        val edgeY = abs(fract(y * rows) - 0.5f) * 2f
        val edgeX = abs(fract(px * 2) - 0.5f) * 2f
        if (edgeY > 0.965f || edgeX > 0.985f) c = shade(c, 0.55f)
        c
    }

    private fun marble(n: Int, base: Int, vein: Int, seed: Int) = make(n) { x, y ->
        val t = fbm(x, y, 3, 5, seed)
        val v = sin(((x + y) * 3f + t * 6f) * PI.toFloat())
        val veins = 1f - abs(v).let { sqrt(it) }
        val soft = fbm(x, y, 6, 3, seed + 3) * 0.12f
        lerpColor(base, vein, (veins * veins * 0.9f + soft).coerceIn(0f, 1f))
    }

    private fun granite(n: Int) = make(n) { x, y ->
        val a = vnoise(x, y, 96, 5); val b = vnoise(x, y, 48, 6); val c = fbm(x, y, 8, 3, 7)
        var col = lerpColor(0xFF8D8A86.toInt(), 0xFFB9B5AE.toInt(), c)
        if (a > 0.82f) col = 0xFF2E2C2A.toInt()
        else if (b > 0.86f) col = 0xFFD8C8B8.toInt()
        col
    }

    private fun brick(n: Int) = make(n) { x, y ->
        val rows = 8
        val ry = y * rows
        val row = floor(ry).toInt()
        val bx = x * 4 + if (row % 2 == 0) 0f else 0.5f
        val mortarY = fract(ry) < 0.1f
        val mortarX = fract(bx) < 0.05f
        if (mortarY || mortarX) lerpColor(0xFFD7D2C8.toInt(), 0xFFB3ADA3.toInt(), vnoise(x, y, 64, 3))
        else {
            val id = Math.floorMod(floor(bx).toInt(), 4) + row * 7
            val tone = hash(id, row, 11)
            val base = lerpColor(0xFFB5543A.toInt(), 0xFF8E3B27.toInt(), tone)
            shade(base, 0.85f + 0.3f * fbm(x, y, 16, 3, 13))
        }
    }

    private fun cobble(n: Int) = make(n) { x, y ->
        // Voronoi taşları (periyodik)
        val cells = 6
        val fx = x * cells; val fy = y * cells
        val ix = floor(fx).toInt(); val iy = floor(fy).toInt()
        var d1 = 9f; var d2 = 9f; var id = 0
        for (j in -1..1) for (i in -1..1) {
            val cx = ix + i; val cy = iy + j
            val px = cx + 0.2f + 0.6f * hash(Math.floorMod(cx, cells), Math.floorMod(cy, cells), 21)
            val py = cy + 0.2f + 0.6f * hash(Math.floorMod(cx, cells), Math.floorMod(cy, cells), 22)
            val d = sqrt((fx - px) * (fx - px) + (fy - py) * (fy - py))
            if (d < d1) { d2 = d1; d1 = d; id = Math.floorMod(cx, cells) * 31 + Math.floorMod(cy, cells) } else if (d < d2) d2 = d
        }
        val edge = d2 - d1
        if (edge < 0.08f) 0xFF4A4642.toInt()
        else {
            val tone = hash(id, 3, 23)
            val base = lerpColor(0xFF9C958C.toInt(), 0xFF6F6A64.toInt(), tone)
            shade(base, 0.8f + 0.35f * min(1f, edge * 2.2f) + 0.1f * fbm(x, y, 16, 2, 24))
        }
    }

    private fun tiles(n: Int) = make(n) { x, y ->
        val k = 4
        val gx = fract(x * k); val gy = fract(y * k)
        if (gx < 0.04f || gy < 0.04f) 0xFFCFCAC0.toInt()
        else {
            val tone = hash(floor(x * k).toInt(), floor(y * k).toInt(), 31)
            val base = lerpColor(0xFFF4F6F7.toInt(), 0xFFDCE6EA.toInt(), tone * 0.6f)
            val gloss = 1f + 0.06f * (1f - abs(gx - gy))
            shade(base, gloss)
        }
    }

    private fun noiseColor(n: Int, a: Int, b: Int, cells: Int, contrast: Float, seed: Int) = make(n) { x, y ->
        val t = fbm(x, y, cells, 5, seed)
        val speck = vnoise(x, y, 128, seed + 1)
        var c = lerpColor(a, b, ((t - 0.5f) * 2f * contrast + 0.5f).coerceIn(0f, 1f))
        if (speck > 0.9f) c = shade(c, 0.82f)
        c
    }

    private fun brushed(n: Int, base: Int) = make(n) { x, y ->
        val line = vnoise2(x, y, 2, 128, 61) * 0.6f + vnoise2(x, y, 4, 256, 62) * 0.4f
        val band = 0.85f + 0.25f * sin((y * 2f + 0.1f) * PI.toFloat() * 2f).let { (it + 1f) / 2f }
        shade(base, (0.78f + 0.32f * line) * band)
    }

    private fun rust(n: Int) = make(n) { x, y ->
        val t = fbm(x, y, 4, 5, 81)
        val spots = fbm(x, y, 8, 4, 82)
        val metal = lerpColor(0xFF6E737A.toInt(), 0xFF50555B.toInt(), vnoise(x, y, 64, 83))
        val rustC = lerpColor(0xFFB3561F.toInt(), 0xFF6B2E12.toInt(), spots)
        lerpColor(metal, rustC, ((t - 0.35f) * 3f).coerceIn(0f, 1f))
    }

    private fun diamondPlate(n: Int) = make(n) { x, y ->
        val k = 8
        val gx = fract(x * k); val gy = fract(y * k)
        val odd = (floor(x * k).toInt() + floor(y * k).toInt()) % 2 == 0
        val ux = gx - 0.5f; val uy = gy - 0.5f
        val (a, b) = if (odd) (ux + uy) to (ux - uy) else (ux - uy) to (ux + uy)
        val ridge = abs(a) < 0.32f && abs(b) < 0.08f
        val base = shade(0xFFB0B5BB.toInt(), 0.85f + 0.2f * vnoise(x, y, 128, 91))
        if (ridge) shade(base, if (b > 0f) 1.25f else 0.8f) else base
    }

    private fun weave(n: Int, a: Int, b: Int, cells: Int) = make(n) { x, y ->
        val k = 48
        val gx = fract(x * k); val gy = fract(y * k)
        val over = (floor(x * k).toInt() + floor(y * k).toInt()) % 2 == 0
        val thread = if (over) sin(gx * PI.toFloat()) else sin(gy * PI.toFloat())
        val c = lerpColor(a, b, fbm(x, y, cells, 3, 101) * 0.6f)
        shade(c, 0.82f + 0.22f * thread)
    }

    private fun denim(n: Int) = make(n) { x, y ->
        val k = 64
        val d = fract((x + y) * k)
        val twill = 0.75f + 0.3f * sin(d * PI.toFloat())
        val c = lerpColor(0xFF3A5A8C.toInt(), 0xFF223A63.toInt(), fbm(x, y, 8, 3, 111))
        val fleck = vnoise(x, y, 256, 112)
        shade(if (fleck > 0.88f) lerpColor(c, 0xFFB8C6DB.toInt(), 0.5f) else c, twill)
    }

    private fun knit(n: Int) = make(n) { x, y ->
        val k = 16
        val gx = fract(x * k); val gy = fract(y * k * 1.5f)
        val v = abs(gx - 0.5f) * 2f
        val loop = 1f - abs(gy - v * 0.6f - 0.2f) * 2.2f
        val c = 0xFFC2453F.toInt()
        shade(c, 0.6f + 0.55f * loop.coerceIn(0f, 1f))
    }

    private fun leather(n: Int) = make(n) { x, y ->
        val cells = 24
        val fx = x * cells; val fy = y * cells
        val ix = floor(fx).toInt(); val iy = floor(fy).toInt()
        var d1 = 9f; var d2 = 9f
        for (j in -1..1) for (i in -1..1) {
            val cx = ix + i; val cy = iy + j
            val px = cx + hash(Math.floorMod(cx, cells), Math.floorMod(cy, cells), 121)
            val py = cy + hash(Math.floorMod(cx, cells), Math.floorMod(cy, cells), 122)
            val d = (fx - px) * (fx - px) + (fy - py) * (fy - py)
            if (d < d1) { d2 = d1; d1 = d } else if (d < d2) d2 = d
        }
        val crease = (sqrt(d2) - sqrt(d1)).coerceIn(0f, 0.3f) / 0.3f
        val c = lerpColor(0xFF6B3F22.toInt(), 0xFF8A5530.toInt(), fbm(x, y, 4, 3, 123))
        shade(c, 0.7f + 0.35f * crease)
    }

    private fun plaid(n: Int) = make(n) { x, y ->
        fun band(v: Float): Float {
            val f = fract(v * 4f)
            return when {
                f < 0.18f -> 1f
                f in 0.45f..0.52f -> 0.6f
                else -> 0f
            }
        }
        val h = band(x); val v = band(y)
        var c = 0xFF2F5D3A.toInt()
        c = lerpColor(c, 0xFFB23A3A.toInt(), max(h, v) * 0.8f)
        if (h > 0f && v > 0f) c = shade(c, 0.75f)
        val k = 96
        val tw = fract((x + y) * k) < 0.5f
        shade(c, if (tw) 1.04f else 0.96f)
    }

    private fun grass(n: Int) = make(n) { x, y ->
        val t = fbm(x, y, 8, 4, 131)
        val blades = vnoise2(x, y, 192, 32, 132)
        val c = lerpColor(0xFF3E7D2E.toInt(), 0xFF6FAE3F.toInt(), t)
        shade(c, 0.75f + 0.45f * blades)
    }

    private fun water(n: Int) = make(n) { x, y ->
        val t = fbm(x + 0.1f * sin(y * 6.283f * 2), y, 4, 4, 141)
        val caustic = 1f - abs(sin((t * 8f) * PI.toFloat()))
        val c = lerpColor(0xFF1C6EA4.toInt(), 0xFF3FA7D6.toInt(), t)
        lerpColor(c, 0xFFD8F1FF.toInt(), caustic * caustic * caustic * 0.55f)
    }

    private fun lava(n: Int) = make(n) { x, y ->
        val t = fbm(x, y, 4, 5, 151)
        val cracks = 1f - abs(sin((t * 7f) * PI.toFloat()))
        val rock = lerpColor(0xFF1F1A18.toInt(), 0xFF3A2E2A.toInt(), fbm(x, y, 16, 2, 152))
        lerpColor(rock, lerpColor(0xFFFF7A00.toInt(), 0xFFFFD34D.toInt(), cracks), (cracks * cracks * 1.6f - 0.15f).coerceIn(0f, 1f))
    }

    private fun ice(n: Int) = make(n) { x, y ->
        val t = fbm(x, y, 3, 5, 161)
        val cr = 1f - abs(sin((fbm(x, y, 6, 3, 162) * 9f) * PI.toFloat()))
        val c = lerpColor(0xFFDDEFFA.toInt(), 0xFFA9D4EE.toInt(), t)
        lerpColor(c, 0xFFFFFFFF.toInt(), (cr - 0.85f).coerceAtLeast(0f) * 5f)
    }

    private fun bark(n: Int) = make(n) { x, y ->
        val warp = fbm(x, y, 4, 3, 171) - 0.5f
        val ridges = abs(sin(((x + warp * 0.15f) * 10f) * PI.toFloat()))
        val noise = vnoise2(x, y, 16, 4, 172) * 0.6f + vnoise2(x, y, 32, 8, 173) * 0.4f
        val c = lerpColor(0xFF4A3424.toInt(), 0xFF7A5B40.toInt(), noise)
        shade(c, 0.55f + 0.6f * ridges)
    }

    private fun checker(n: Int) = make(n) { x, y ->
        if ((floor(x * 8).toInt() + floor(y * 8).toInt()) % 2 == 0) 0xFFF5F5F5.toInt() else 0xFF2B2B2B.toInt()
    }

    private fun stripes(n: Int) = make(n) { x, y ->
        when (Math.floorMod(floor((x + y) * 9).toInt(), 3)) {
            0 -> 0xFFF2C14E.toInt()
            1 -> 0xFFF78154.toInt()
            else -> 0xFF4D9078.toInt()
        }
    }

    private fun dots(n: Int) = make(n) { x, y ->
        val k = 8
        val gx = fract(x * k) - 0.5f; val gy = fract(y * k) - 0.5f
        val gx2 = fract(x * k + 0.5f) - 0.5f; val gy2 = fract(y * k + 0.5f) - 0.5f
        val inDot = gx * gx + gy * gy < 0.06f || gx2 * gx2 + gy2 * gy2 < 0.025f
        if (inDot) 0xFFFFFFFF.toInt() else 0xFFE5487A.toInt()
    }

    private fun carbon(n: Int) = make(n) { x, y ->
        val k = 32
        val gx = fract(x * k); val gy = fract(y * k)
        val cell = (floor(x * k).toInt() / 2 + floor(y * k).toInt() / 2) % 2 == 0
        val v = if (cell) sin(gx * PI.toFloat()) else sin(gy * PI.toFloat())
        shade(0xFF2A2D31.toInt(), 0.65f + 0.7f * v)
    }

    private fun hexes(n: Int) = make(n) { x, y ->
        // Altıgen petek: satırları yarım kaydırılmış merkezlerin Voronoi hücreleri (döşenebilir: 6 sütun × 8 satır)
        val cols = 6; val rows = 8
        val fx = x * cols; val fy = y * rows
        var d1 = 9f; var d2 = 9f
        val jy = floor(fy).toInt()
        for (j in jy - 1..jy + 1) {
            val shift = if (Math.floorMod(j, 2) == 0) 0f else 0.5f
            val ix = floor(fx - shift).toInt()
            for (i in ix - 1..ix + 1) {
                val cx = i + shift + 0.5f; val cy = j + 0.5f
                val ddx = (fx - cx); val ddy = (fy - cy) * 0.75f
                val d = sqrt(ddx * ddx + ddy * ddy)
                if (d < d1) { d2 = d1; d1 = d } else if (d < d2) d2 = d
            }
        }
        if (d2 - d1 < 0.07f) 0xFF7A5C12.toInt() else shade(0xFFF3B61F.toInt(), 1.08f - d1 * 0.45f)
    }

    /** Doku piksellerinin ortalama rengi (küçük önizleme ve yedek renk için). */
    fun average(t: Texture): Int {
        var r = 0L; var g = 0L; var b = 0L
        for (c in t.px) { r += c shr 16 and 0xFF; g += c shr 8 and 0xFF; b += c and 0xFF }
        val n = t.px.size.coerceAtLeast(1)
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }
}
