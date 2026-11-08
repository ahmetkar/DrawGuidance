package com.karapp.drawguidance.ui.drawing

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fırçaların kullandığı, program içinde üretilen (deterministik) dokular, yarım ton
 * ızgaraları, yumuşak damga ve şekil yolları. Döşenebilir (tile) bitmap'ler beyaz renk +
 * alfa kanalı olarak üretilir; renk çizim anında verilir. Doku tuval biriminde olduğu
 * için kağıda sabittir: aynı yerden geçen çizgiler aynı kağıt dişine takılır.
 */
object BrushTextures {

    private val bitmaps = HashMap<String, Bitmap>()

    /** Çizim gücü seviyesi (0..4): güç arttıkça kağıt dişindeki boşluklar dolar. */
    @Synchronized
    fun grain(kind: TextureKind, level: Int): Bitmap = bitmaps.getOrPut("grain-$kind-$level") {
        when (kind) {
            // Sert pastel: ince, sık kağıt dişi; boşluklar küçük ve keskin
            TextureKind.PASTEL_GRAIN -> noiseTile(128, octaves = intArrayOf(64, 32), gap = 0.34f - 0.05f * level, softness = 0.03f, seed = 11)
            // Kömür: yatayda uzayan kaba taneler, her güçte belirgin boşluklar
            TextureKind.CHARCOAL_GRAIN -> noiseTile(128, octaves = intArrayOf(32, 16, 64), gap = 0.58f - 0.06f * level, softness = 0.12f, seed = 23, streak = true)
            // Sünger: düzensiz, farklı boyda gözenekler
            TextureKind.SPONGE -> noiseTile(96, octaves = intArrayOf(12, 24), gap = 0.46f - 0.05f * level, softness = 0.04f, seed = 37)
            TextureKind.CANVAS_WEAVE -> weaveTile(level)
        }
    }

    /**
     * v21 akışkanlık: az boyada izin kağıt dişine takılması. İkili (tam dolu ya da boş) gürültü; dolu oranı akışkanlıkla
     * artar (akışkanlık 0 → %38, 1'e yaklaştıkça tamamen dolu). 10 düzey önbellekte.
     */
    @Synchronized
    fun flowGrain(flow: Float): Bitmap {
        val level = (flow.coerceIn(0f, 1f) * 10).roundToInt().coerceIn(0, 9)
        return bitmaps.getOrPut("flow-$level") {
            val coverage = 0.38f + 0.6f * level / 10f
            noiseTile(128, octaves = intArrayOf(64, 32, 128), gap = 1f - coverage, softness = 0f, seed = 71)
        }
    }

    /** Yarım ton karosu: [pitch] tuval birimi aralık, [level] 0..7 kaplama. */
    @Synchronized
    fun halftone(kind: HalftoneKind, pitch: Int, level: Int): Bitmap = bitmaps.getOrPut("ht-$kind-$pitch-$level") {
        val coverage = 0.12f + 0.1f * level
        val t = max(4, (pitch * 1.41421f).roundToInt())
        val bitmap = Bitmap.createBitmap(t, t, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        when (kind) {
            HalftoneKind.DOTS -> {
                // 45° döndürülmüş kare ızgara: köşeler + merkez
                val r = t * sqrt(coverage / (2f * PI.toFloat()))
                val tf = t.toFloat()
                for (p in arrayOf(0f to 0f, tf to 0f, 0f to tf, tf to tf, tf / 2 to tf / 2)) canvas.drawCircle(p.first, p.second, r, paint)
            }
            HalftoneKind.LINES -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1f, coverage * pitch)
                val tf = t.toFloat()
                canvas.drawLine(-tf, tf, tf, -tf, paint)
                canvas.drawLine(-tf, 2 * tf, 2 * tf, -tf, paint)
                canvas.drawLine(0f, 2 * tf, 2 * tf, 0f, paint)
            }
        }
        bitmap
    }

    /** Tarama deseni karosu: sabit aralıklı ince 45° çizgiler. */
    @Synchronized
    fun hatch(pitch: Int): Bitmap = bitmaps.getOrPut("hatch-$pitch") {
        val t = max(4, (pitch * 1.41421f).roundToInt())
        val bitmap = Bitmap.createBitmap(t, t, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = max(1f, pitch * 0.22f) }
        val tf = t.toFloat()
        canvas.drawLine(-tf, tf, tf, -tf, paint)
        canvas.drawLine(-tf, 2 * tf, 2 * tf, -tf, paint)
        canvas.drawLine(0f, 2 * tf, 2 * tf, 0f, paint)
        bitmap
    }

    private val shaders = java.util.IdentityHashMap<Bitmap, BitmapShader>()

    /** Döşenen doku gölgelendiricisi (bitmap başına bir kez oluşturulur). */
    @Synchronized
    fun shader(bitmap: Bitmap): BitmapShader = shaders.getOrPut(bitmap) { BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }

    /** Ortası [hardness] oranında dolu, kenara doğru yumuşakça sönen yuvarlak damga. */
    @Synchronized
    fun softStamp(hardness: Float): Bitmap {
        val level = (hardness.coerceIn(0f, 1f) * 10).roundToInt()
        return bitmaps.getOrPut("soft-$level") {
            val size = 64
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val h = level / 10f
            val pixels = IntArray(size * size)
            val c = (size - 1) / 2f
            for (y in 0 until size) for (x in 0 until size) {
                val r = sqrt((x - c) * (x - c) + (y - c) * (y - c)) / c
                val a = when {
                    r >= 1f -> 0f
                    r <= h -> 1f
                    else -> {
                        val t = (r - h) / (1f - h)
                        0.5f + 0.5f * cos(t * PI.toFloat())
                    }
                }
                pixels[y * size + x] = Color.argb((a * a * 255).roundToInt(), 255, 255, 255)
            }
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
            bitmap
        }
    }

    private val spheres = android.util.LruCache<Int, Bitmap>(12)

    /**
     * 3B fırçanın damgası: [color] renginde, sol üstten ışık alan küre (koyu kenar, parlak ışık lekesi). Damgalar sık
     * dizilince gölgeli bir tüp görünür; ışık yönü sabit olduğu için çizgi boyunca tutarlıdır.
     */
    fun sphereStamp(color: Int): Bitmap = synchronized(spheres) {
        spheres.get(color) ?: run {
            val size = 64
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(size * size)
            val c = (size - 1) / 2f
            val r0 = Color.red(color) / 255f; val g0 = Color.green(color) / 255f; val b0 = Color.blue(color) / 255f
            // Işık yönü (sol üst, kameraya doğru)
            val lx = -0.45f; val ly = -0.55f; val lz = 0.70f
            for (y in 0 until size) for (x in 0 until size) {
                val nx = (x - c) / c; val ny = (y - c) / c
                val d2 = nx * nx + ny * ny
                if (d2 >= 1f) { pixels[y * size + x] = 0; continue }
                val nz = sqrt(1f - d2)
                val diffuse = max(0f, nx * lx + ny * ly + nz * lz)
                val shade = 0.28f + 0.82f * diffuse
                // Parlak yansıma (Blinn-Phong, yarım vektör ≈ ışık + bakış)
                val hx = lx; val hy = ly; val hz = lz + 1f
                val hl = sqrt(hx * hx + hy * hy + hz * hz)
                val spec = max(0f, (nx * hx + ny * hy + nz * hz) / hl).let { it * it }.let { it * it }.let { it * it * it }
                fun ch(v: Float) = ((v * shade + spec * 0.85f).coerceIn(0f, 1f) * 255).roundToInt()
                // Kenarda yumuşak kenar yumuşatması
                val edge = ((1f - sqrt(d2)) * c).coerceIn(0f, 1f)
                pixels[y * size + x] = Color.argb((edge * 255).roundToInt(), ch(r0), ch(g0), ch(b0))
            }
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
            spheres.put(color, bitmap)
            bitmap
        }
    }

    /** Merkezde, birim boyutta (≈1×1) şekil yolu. */
    fun shapePath(shape: StampShape): Path = when (shape) {
        StampShape.STAR -> Path().apply {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 0.5f else 0.21f
                val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
                val x = cos(a) * r
                val y = sin(a) * r
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        StampShape.HEART -> Path().apply {
            moveTo(0f, 0.45f)
            cubicTo(-0.55f, 0.05f, -0.45f, -0.5f, 0f, -0.2f)
            cubicTo(0.45f, -0.5f, 0.55f, 0.05f, 0f, 0.45f)
            close()
        }
        StampShape.LEAF -> Path().apply {
            // Yöne göre yatay uzanan badem yaprak
            moveTo(-0.5f, 0f)
            quadTo(0f, -0.32f, 0.5f, 0f)
            quadTo(0f, 0.32f, -0.5f, 0f)
            close()
        }
    }

    // --- Üreticiler ---

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 144665
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) / 2147483647f
    }

    /** Döşenebilir değer gürültüsü; [gap] oranındaki en düşük değerler boşluk (şeffaf) olur. */
    private fun noiseTile(size: Int, octaves: IntArray, gap: Float, softness: Float, seed: Int, streak: Boolean = false): Bitmap {
        val values = FloatArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            var v = 0f
            var weight = 0f
            octaves.forEachIndexed { o, cells ->
                val cellsY = if (streak) max(2, cells / 3) else cells
                val fx = x * cells / size.toFloat()
                val fy = y * cellsY / size.toFloat()
                val x0 = floor(fx).toInt()
                val y0 = floor(fy).toInt()
                val tx = smooth(fx - x0)
                val ty = smooth(fy - y0)
                fun h(ix: Int, iy: Int) = hash(Math.floorMod(ix, cells), Math.floorMod(iy, cellsY), seed + o * 7)
                val top = h(x0, y0) + (h(x0 + 1, y0) - h(x0, y0)) * tx
                val bottom = h(x0, y0 + 1) + (h(x0 + 1, y0 + 1) - h(x0, y0 + 1)) * tx
                val w = 1f / (o + 1)
                v += (top + (bottom - top) * ty) * w
                weight += w
            }
            values[y * size + x] = v / weight
        }
        val sorted = values.sortedArray()
        val threshold = sorted[(gap.coerceIn(0f, 0.95f) * (sorted.size - 1)).toInt()]
        val range = (sorted.last() - sorted.first()).coerceAtLeast(1e-3f)
        val pixels = IntArray(size * size) { i ->
            val d = (values[i] - threshold) / range
            val a = when {
                softness <= 0f -> if (d >= 0f) 1f else 0f
                else -> ((d + softness) / (2 * softness)).coerceIn(0f, 1f)
            }
            Color.argb((a * 255).roundToInt(), 255, 255, 255)
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, size, 0, 0, size, size) }
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)

    /** Tuval kumaşı örgüsü: yatay ve dikey iplikler, aralarında seyrek boya. */
    private fun weaveTile(level: Int): Bitmap {
        val size = 24
        val gapAlpha = 20 + 22 * level
        val pixels = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            val threadX = (x % 12) in 2..9
            val threadY = (y % 12) in 2..9
            val over = ((x / 12) + (y / 12)) % 2 == 0
            val a = when {
                threadX && threadY -> if (over) 255 else 200
                threadX || threadY -> 150
                else -> gapAlpha
            }
            pixels[y * size + x] = Color.argb(a.coerceAtMost(255), 255, 255, 255)
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, size, 0, 0, size, size) }
    }
}
