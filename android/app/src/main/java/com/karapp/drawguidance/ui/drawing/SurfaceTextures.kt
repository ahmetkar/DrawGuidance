package com.karapp.drawguidance.ui.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v21 doku ve desen fırçalarının karoları: program içinde üretilen, döşenebilir (kenarları birbirine uyan) ve fırçanın
 * renginden tonlanan (açık/koyu) opak resimler. Karo kağıda sabittir (tuval biriminde); fırça geçtiği yerde onu açığa
 * çıkarır. Aynı renk ve tür için bir kez üretilip önbellekte tutulur (128×128, ~64 KB).
 *
 * Her tür, 0..1 arası bir aydınlık haritası (0.5 = fırçanın rengi) ya da doğrudan renk paletiyle çizilir:
 * - Ten (insan): yumuşak benekler, gözenekler, ince çizgiler.
 * - Kürk (hayvan): eğik, kısa, açık ve koyu kıl telleri.
 * - Yaprak (bitki): hücre ağı gibi ince damarlar ve ana damarlar.
 * - Ahşap: dalgalı yıllık halkalar, lifler ve gözenekler.
 * - Demir: pürüzlü yüzey, çukurlar ve pas lekeleri.
 * - Metal: fırçalanmış ince çizgiler, geniş parlama bandı, çizikler.
 * - Halı (kilim): basamaklı baklava madalyon, köşe motifleri, kenar şeritleri; rengin tonları ve ondan türeyen iki
 *   uyumlu renk.
 * - Duvar kağıdı (damask): simetrik süsleme motifi, açık zemin üzerinde koyu ton.
 * - Ahşap boyası: boyalı tahtalar, aralarda derz, yer yer boyası kalkmış yerden görünen ahşap.
 */
object SurfaceTextures {

    private const val SIZE = 128
    private val cache = LruCache<String, Bitmap>(24)

    /** [kind] türünün [color] renginden tonlanmış karosu. */
    fun tile(kind: SurfaceKind, color: Int): Bitmap = synchronized(cache) {
        val key = "$kind-${color or (0xFF shl 24)}"
        cache.get(key) ?: build(kind, color or (0xFF shl 24)).also { cache.put(key, it) }
    }

    /** Karonun tuvaldeki ölçeği: motif ve doku fırça kalınlığıyla birlikte büyür (0.05 adımlarla). */
    fun scaleFor(kind: SurfaceKind, width: Float): Float {
        val raw = when (kind) {
            SurfaceKind.CARPET, SurfaceKind.WALLPAPER -> width / 60f
            SurfaceKind.WOOD_PAINT, SurfaceKind.WOOD -> width / 48f
            else -> width / 40f
        }
        return (raw.coerceIn(0.5f, 4f) * 20f).roundToInt() / 20f
    }

    private fun build(kind: SurfaceKind, base: Int): Bitmap = when (kind) {
        SurfaceKind.SKIN -> fromLight(base) { x, y -> skin(x, y) }
        SurfaceKind.FUR -> fur(base)
        SurfaceKind.PLANT -> fromLight(base) { x, y -> plant(x, y) }
        SurfaceKind.WOOD -> fromLight(base) { x, y -> wood(x, y) }
        SurfaceKind.IRON -> iron(base)
        SurfaceKind.METAL -> fromLight(base) { x, y -> metal(x, y) }
        SurfaceKind.CARPET -> carpet(base)
        SurfaceKind.WALLPAPER -> wallpaper(base)
        SurfaceKind.WOOD_PAINT -> woodPaint(base)
    }

    // --- Renk ---------------------------------------------------------------------------------------

    /** Aydınlık [l] (0..1, 0.5 = temel renk): altı koyulaştırır, üstü beyaza doğru açar. */
    fun tone(base: Int, l: Float): Int {
        val v = l.coerceIn(0f, 1f)
        return if (v <= 0.5f) {
            val f = 0.25f + 1.5f * v
            Color.rgb((Color.red(base) * f).roundToInt(), (Color.green(base) * f).roundToInt(), (Color.blue(base) * f).roundToInt())
        } else mix(base, Color.WHITE, (v - 0.5f) * 1.5f)
    }

    fun mix(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun ch(x: Int, y: Int) = (x + (y - x) * f).roundToInt().coerceIn(0, 255)
        return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }

    /** Rengin tonunu [degrees] kadar döndürür (gri renklerde doygunluk eklenir: uyumlu ikinci renk). */
    fun hueShift(base: Int, degrees: Float, minSat: Float = 0.35f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(base, hsv)
        hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
        hsv[1] = max(hsv[1], minSat)
        hsv[2] = hsv[2].coerceIn(0.3f, 0.9f)
        return Color.HSVToColor(hsv)
    }

    private inline fun fromLight(base: Int, light: (Int, Int) -> Float): Bitmap {
        val px = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) for (x in 0 until SIZE) px[y * SIZE + x] = tone(base, light(x, y))
        return bitmapOf(px)
    }

    private fun bitmapOf(px: IntArray): Bitmap =
        Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE) }

    // --- Döşenebilir gürültü --------------------------------------------------------------------------

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) / 2147483647f
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)

    /** Karo boyunca [cx]×[cy] hücreli, kenarları uyan değer gürültüsü (0..1). */
    private fun noise(x: Float, y: Float, cx: Int, cy: Int, seed: Int): Float {
        val fx = x * cx / SIZE; val fy = y * cy / SIZE
        val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt()
        val tx = smooth(fx - x0); val ty = smooth(fy - y0)
        fun h(ix: Int, iy: Int) = hash(Math.floorMod(ix, cx), Math.floorMod(iy, cy), seed)
        val top = h(x0, y0) + (h(x0 + 1, y0) - h(x0, y0)) * tx
        val bottom = h(x0, y0 + 1) + (h(x0 + 1, y0 + 1) - h(x0, y0 + 1)) * tx
        return top + (bottom - top) * ty
    }

    private fun fbm(x: Float, y: Float, seed: Int, vararg cells: Int): Float {
        var v = 0f; var w = 0f
        cells.forEachIndexed { o, c ->
            val weight = 1f / (o + 1)
            v += noise(x, y, c, c, seed + o * 17) * weight
            w += weight
        }
        return v / w
    }

    /** Karo boyunca uyan hücresel (Worley) gürültü: en yakın ve ikinci en yakın özellik noktasına uzaklık (piksel). */
    private fun worley(x: Float, y: Float, cells: Int, seed: Int): FloatArray {
        val cs = SIZE.toFloat() / cells
        val gx = floor(x / cs).toInt(); val gy = floor(y / cs).toInt()
        var f1 = Float.MAX_VALUE; var f2 = Float.MAX_VALUE
        for (dy in -1..1) for (dx in -1..1) {
            val cx = gx + dx; val cy = gy + dy
            val mx = Math.floorMod(cx, cells); val my = Math.floorMod(cy, cells)
            val px = (cx + hash(mx, my, seed)) * cs
            val py = (cy + hash(mx, my, seed + 1)) * cs
            val d = sqrt((px - x) * (px - x) + (py - y) * (py - y))
            if (d < f1) { f2 = f1; f1 = d } else if (d < f2) f2 = d
        }
        return floatArrayOf(f1, f2)
    }

    /** Karoya uyan eğik çizgi deseni: [a], [b] tam sayı; sonuç -1..1. */
    private fun wave(x: Int, y: Int, a: Int, b: Int, phase: Float = 0f) =
        sin((x * a + y * b) * 2.0 * PI / SIZE + phase).toFloat()

    // --- Dokular ---------------------------------------------------------------------------------------

    private fun skin(x: Int, y: Int): Float {
        val fx = x.toFloat(); val fy = y.toFloat()
        var l = 0.5f + (fbm(fx, fy, 3, 4, 8, 16) - 0.5f) * 0.18f
        // Gözenekler: hücre merkezine yakın küçük koyu noktalar (bir kısmı yok)
        val w = worley(fx, fy, 22, 5)
        val cell = hash((fx / (SIZE / 22f)).toInt(), (fy / (SIZE / 22f)).toInt(), 6)
        if (cell > 0.35f) l -= 0.13f * (1f - (w[0] / 1.3f)).coerceIn(0f, 1f)
        // Çok ince kırışık çizgileri (iki yönde)
        val a = abs(wave(x, y, 5, 3, noise(fx, fy, 8, 8, 7) * 4f))
        val b = abs(wave(x, y, -3, 5, noise(fx, fy, 8, 8, 8) * 4f))
        // Çizgiler yer yer (gürültüyle) görünür: ağ gibi değil, kesik kesik
        val show = noise(fx, fy, 6, 6, 9)
        if (a > 0.993f && show > 0.45f) l -= 0.035f
        if (b > 0.995f && show < 0.55f) l -= 0.03f
        return l
    }

    private fun plant(x: Int, y: Int): Float {
        val fx = x.toFloat(); val fy = y.toFloat()
        var l = 0.47f + (fbm(fx, fy, 11, 4, 8) - 0.5f) * 0.12f
        // İnce damar ağı (hücre kenarları)
        val w = worley(fx, fy, 9, 12)
        val edge = w[1] - w[0]
        if (edge < 1.4f) l += 0.16f * (1f - edge / 1.4f)
        // Ana ve yan damarlar: iki eğik çizgi ailesi
        val main = abs(wave(x, y, 2, 2, noise(fx, fy, 4, 4, 13) * 1.5f))
        if (main > 0.965f) l += 0.2f * (main - 0.965f) / 0.035f
        val side = abs(wave(x, y, 4, -4, noise(fx, fy, 4, 4, 14)))
        if (side > 0.985f) l += 0.08f
        return l
    }

    private fun wood(x: Int, y: Int): Float {
        val fx = x.toFloat(); val fy = y.toFloat()
        // Liflere dik dalgalanan halkalar: y boyunca 6 halka, gürültüyle bükülmüş
        val t = fy / SIZE * 10f + (fbm(fx, fy, 21, 2, 4) - 0.5f) * 2.6f + 0.15f * sin(fx / SIZE * 2 * PI).toFloat()
        val r = t - floor(t)
        // Geç odun (koyu, dar, kenarları yumuşak) ve erken odun (açık, geniş)
        var l = if (r > 0.84f) 0.4f - 0.06f * sin((r - 0.84f) / 0.16f * PI).toFloat() else 0.5f + 0.08f * r
        // Uzun lifler (x yönünde uzayan ince çizgiler)
        l += (noise(fx, fy, 3, 48, 22) - 0.5f) * 0.12f
        // Gözenekler: kısa koyu çizgicikler
        if (hash(x / 6, y, 23) > 0.985f) l -= 0.12f
        return l
    }

    private fun metal(x: Int, y: Int): Float {
        val fx = x.toFloat(); val fy = y.toFloat()
        // Fırçalanmış yüzey: yatayda uzun, düşeyde sık çizgiler
        var l = 0.56f + (noise(fx, fy, 2, 96, 31) - 0.5f) * 0.14f + (noise(fx, fy, 4, 64, 32) - 0.5f) * 0.08f
        // Geniş parlama bandı
        l += 0.16f * sin(fy / SIZE * 2 * PI + 0.6).toFloat()
        // Seyrek, kısa, açık çizikler (rastgele yerlerde)
        if (hash(x / 10, (y + x / 3) / 1, 33) > 0.9975f) l += 0.16f
        return l
    }

    /** Demir: koyu, pürüzlü; çukurlar ve pas lekeleri (pasın rengi sabit, gövdenin tonu fırçanın renginden). */
    private fun iron(base: Int): Bitmap {
        val rust = Color.rgb(142, 72, 30)
        val rustLight = Color.rgb(186, 104, 46)
        val px = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val fx = x.toFloat(); val fy = y.toFloat()
            var l = 0.42f + (fbm(fx, fy, 41, 16, 32, 64) - 0.5f) * 0.22f
            val w = worley(fx, fy, 14, 42)
            if (w[0] < 1.6f && hash((fx / (SIZE / 14f)).toInt(), (fy / (SIZE / 14f)).toInt(), 43) > 0.55f) l -= 0.16f
            var c = tone(base, l)
            val patch = fbm(fx, fy, 44, 3, 6, 12)
            val m = ((patch - 0.56f) / 0.12f).coerceIn(0f, 1f)
            if (m > 0f) {
                val speck = hash(x, y, 45)
                c = mix(c, if (speck > 0.6f) rustLight else rust, m * (0.7f + 0.3f * speck))
            }
            px[y * SIZE + x] = c
        }
        return bitmapOf(px)
    }

    /** Kürk: eğik, kısa, açık ve koyu tellerin üst üste çizimi (karo kenarından taşanlar karşı kenardan devam eder). */
    private fun fur(base: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(tone(base, 0.36f))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        for (i in 0 until 1100) {
            val x = hash(i, 0, 51) * SIZE
            val y = hash(i, 1, 51) * SIZE
            val ang = (72f + (hash(i, 2, 51) - 0.5f) * 30f + (noise(x, y, 4, 4, 52) - 0.5f) * 40f) * PI.toFloat() / 180f
            val len = 6f + 9f * hash(i, 3, 51)
            val ex = x + kotlin.math.cos(ang) * len
            val ey = y + sin(ang) * len
            val l = 0.3f + 0.55f * hash(i, 4, 51) * (0.6f + 0.4f * noise(x, y, 6, 6, 53))
            paint.color = tone(base, l)
            paint.strokeWidth = 0.8f + 0.7f * hash(i, 5, 51)
            for (ox in -1..1) for (oy in -1..1) {
                val dx = ox * SIZE.toFloat(); val dy = oy * SIZE.toFloat()
                canvas.drawLine(x + dx, y + dy, ex + dx, ey + dy, paint)
            }
        }
        return bitmap
    }

    // --- Desenler --------------------------------------------------------------------------------------

    /** Kilim: basamaklı baklava madalyon, köşelerde küçük baklavalar, üst ve altta şeritler. */
    private fun carpet(base: Int): Bitmap {
        val bg = tone(base, 0.5f)
        val dark = tone(base, 0.16f)
        val light = tone(base, 0.86f)
        val accent = hueShift(base, 160f)
        val accent2 = mix(hueShift(base, -38f), Color.WHITE, 0.2f)
        val px = IntArray(SIZE * SIZE)
        val c = SIZE / 2
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            // Basamaklı görünüm için 4 piksellik adımlarla Manhattan uzaklığı
            val d = ((abs(x - c) / 4) + (abs(y - c) / 4)) * 4
            val cx = min(x, SIZE - 1 - x); val cy = min(y, SIZE - 1 - y)
            val corner = ((cx / 4) + (cy / 4)) * 4
            val color = when {
                y < 4 || y >= SIZE - 4 -> dark
                y < 8 || y >= SIZE - 8 -> if (((x / 4) + (y / 4)) % 2 == 0) accent2 else light
                d < 12 -> accent2
                d < 16 -> dark
                d < 28 -> accent
                d < 32 -> dark
                d < 40 -> light
                d < 44 -> dark
                corner < 12 -> accent
                corner < 16 -> dark
                // Zeminde seyrek küçük haçlar
                (x % 32 == 16 && abs(y % 32 - 16) < 3) || (y % 32 == 16 && abs(x % 32 - 16) < 3) -> if (d > 50) light else bg
                else -> bg
            }
            px[y * SIZE + x] = color
        }
        return bitmapOf(px)
    }

    /** Damask: açık zeminde simetrik süsleme (ortada ve köşelerde), ince açık iç çizgiler. */
    private fun wallpaper(base: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(tone(base, 0.62f))
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tone(base, 0.4f); style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tone(base, 0.8f); style = Paint.Style.STROKE; strokeWidth = 1.2f }
        val motif = damask()
        for ((ox, oy) in listOf(64f to 64f, 0f to 0f, 128f to 0f, 0f to 128f, 128f to 128f)) {
            canvas.save()
            canvas.translate(ox, oy)
            canvas.scale(0.62f, 0.62f)
            canvas.drawPath(motif, fill)
            canvas.drawPath(damaskInner(), line)
            canvas.restore()
        }
        return bitmap
    }

    /** Merkezi (0,0) olan, yatayda simetrik süsleme: gövde, iki kıvrık yaprak, tepe ve kök. */
    private fun damask(): Path {
        val half = Path().apply {
            moveTo(0f, -52f)
            cubicTo(8f, -40f, 18f, -30f, 12f, -16f)
            cubicTo(30f, -30f, 46f, -20f, 40f, -2f)
            cubicTo(36f, 10f, 22f, 8f, 20f, 0f)
            cubicTo(28f, 16f, 22f, 32f, 8f, 36f)
            cubicTo(12f, 44f, 8f, 50f, 0f, 56f)
            lineTo(0f, -52f)
            close()
        }
        val full = Path(half)
        val m = android.graphics.Matrix().apply { setScale(-1f, 1f) }
        val mirror = Path(half).apply { transform(m) }
        full.op(mirror, Path.Op.UNION)
        return full
    }

    private fun damaskInner(): Path = Path().apply {
        moveTo(0f, -34f); cubicTo(6f, -20f, 6f, 10f, 0f, 30f)
        moveTo(0f, -34f); cubicTo(-6f, -20f, -6f, 10f, 0f, 30f)
        addCircle(0f, -8f, 4f, Path.Direction.CW)
    }

    /** Boyalı tahtalar: dört sıra tahta, derzler, çivi başları, yer yer kalkmış boyadan görünen ahşap. */
    private fun woodPaint(base: Int): Bitmap {
        val woodBase = Color.rgb(156, 107, 63)
        val px = IntArray(SIZE * SIZE)
        val plank = SIZE / 4
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val row = y / plank
            val inRow = y % plank
            val joint = (hash(row, 0, 61) * SIZE).toInt()
            val fx = x.toFloat(); val fy = y.toFloat()
            val gap = inRow < 2 || abs(x - joint) < 1
            val color = if (gap) tone(base, 0.12f) else {
                val wear = fbm(fx, fy, 62, 4, 8, 16)
                if (wear > 0.66f) {
                    val t = fy / SIZE * 12f + (noise(fx, fy, 2, 4, 63) - 0.5f) * 3f
                    val r = t - floor(t)
                    tone(woodBase, if (r > 0.8f) 0.35f else 0.5f + 0.05f * r)
                } else {
                    // Boya: yatay fırça izleri, kenarda derze doğru hafif gölge
                    var l = 0.5f + (noise(fx, fy, 3, 40, 64) - 0.5f) * 0.16f
                    if (inRow < 5) l -= 0.06f
                    val nail = abs(x - (joint + 5) % SIZE) < 2 && abs(inRow - plank / 2) < 2
                    tone(base, if (nail) 0.2f else l)
                }
            }
            px[y * SIZE + x] = color
        }
        return bitmapOf(px)
    }
}
