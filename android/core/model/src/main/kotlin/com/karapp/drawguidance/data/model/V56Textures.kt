package com.karapp.drawguidance.data.model

import kotlin.math.floor

/**
 * v56 hazır doku paketi (aşama 5, saf ve deterministik; iOS `V56Textures.swift` aynı tamsayı karması ve aynı
 * formüllerle bit bit aynı karoları üretir). Karolar [SIZE]×[SIZE], döşenebilir; değer 0..1 "boya tutma" oranıdır
 * (1: tam boya, 0: kağıt dişindeki boşluk). Gren, kement ve katman doku dolgusu bu değerleri kullanır.
 */
object V56Textures {
    const val PREFIX = "builtin:"
    const val SIZE = 128
    val NAMES = listOf("paper", "canvas", "charcoal", "noise", "dots", "hatch")
    val BUILTIN: List<String> = NAMES.map { PREFIX + it }

    fun isBuiltin(id: String?): Boolean = id != null && id in BUILTIN

    /** Gren ölçeği ve gücü sınırları. */
    const val MIN_SCALE = 0.25f
    const val MAX_SCALE = 8f

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535f
    }

    /** Döşenebilir değer gürültüsü: hücre [cx]×[cy] piksel ([SIZE] bunlara tam bölünür). */
    private fun noise(x: Int, y: Int, cx: Int, cy: Int, seed: Int): Float {
        val nx = SIZE / cx
        val ny = SIZE / cy
        val ix = x / cx
        val iy = y / cy
        val fx = (x % cx).toFloat() / cx
        val fy = (y % cy).toFloat() / cy
        val sx = fx * fx * (3f - 2f * fx)
        val sy = fy * fy * (3f - 2f * fy)
        val a = hash(ix % nx, iy % ny, seed)
        val b = hash((ix + 1) % nx, iy % ny, seed)
        val c = hash(ix % nx, (iy + 1) % ny, seed)
        val d = hash((ix + 1) % nx, (iy + 1) % ny, seed)
        val top = a + (b - a) * sx
        val bottom = c + (d - c) * sx
        return top + (bottom - top) * sy
    }

    private fun ramp(v: Float, lo: Float, hi: Float): Float = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)

    /** Üçgen dalga: periyodun ortasında 0, kenarlarında 1. */
    private fun tri(t: Int, p: Int): Float {
        val f = (t % p).toFloat() / p * 2f - 1f
        return if (f < 0f) -f else f
    }

    /** Karodaki (x, y) noktasının boya tutma değeri (0..1); bilinmeyen dokuda 1. */
    fun value(id: String, x: Int, y: Int): Float {
        val px = Math.floorMod(x, SIZE)
        val py = Math.floorMod(y, SIZE)
        return when (id.removePrefix(PREFIX)) {
            // Kağıt: üç katlı yumuşak gürültü
            "paper" -> ramp(0.5f * noise(px, py, 16, 16, 1) + 0.3f * noise(px, py, 8, 8, 2) + 0.2f * noise(px, py, 4, 4, 3), 0.3f, 0.62f)
            // Tuval: 8 px'lik dama örgü (yatay ve dikey iplik) ve az gürültü
            "canvas" -> {
                val thread = if ((px / 8 + py / 8) % 2 == 0) 1f - tri(py, 8) else 1f - tri(px, 8)
                (0.3f + 0.6f * thread + 0.1f * noise(px, py, 4, 4, 5)).coerceIn(0f, 1f)
            }
            // Kömür: yatayda uzayan kaba taneler
            "charcoal" -> ramp(0.6f * noise(px, py, 32, 4, 7) + 0.4f * noise(px, py, 8, 8, 8), 0.35f, 0.6f)
            // Kum: ince, sık tane
            "noise" -> ramp(0.6f * noise(px, py, 2, 2, 11) + 0.4f * noise(px, py, 4, 4, 12), 0.25f, 0.75f)
            // Noktalar: 16 px aralıklı yuvarlak noktalar
            "dots" -> {
                val dx = (px % 16) - 7.5f
                val dy = (py % 16) - 7.5f
                1f - 0.85f * ramp(dx * dx + dy * dy, 16f, 36f)
            }
            // Tarama: 12 px aralıklı çapraz çizgiler
            "hatch" -> if ((px + py) % 12 < 3) 1f else 0.15f
            else -> 1f
        }
    }

    /** Bütün karo (satır satır, [SIZE]×[SIZE]). */
    fun tile(id: String): FloatArray = FloatArray(SIZE * SIZE) { value(id, it % SIZE, it / SIZE) }

    /** Güçle karışmış saydamlık: güç 0 → doku etkisiz (1), güç 1 → değerin kendisi. */
    fun alpha(value: Float, strength: Float): Float = 1f - strength.coerceIn(0f, 1f) * (1f - value)

    /**
     * Doku başlangıcı (tuval koordinatı): [GrainSpec.moving] true ("Kayar") ise doku tuvale sabittir (0, 0); değilse
     * çizginin ilk noktasına bağlıdır (çizgiyle birlikte taşınır).
     */
    fun anchor(spec: GrainSpec, firstX: Float, firstY: Float): Pair<Float, Float> = if (spec.moving) 0f to 0f else firstX to firstY

    fun scaleOf(spec: GrainSpec): Float = spec.scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /** Tuval noktasındaki gren saydamlığı (çizimde Android `BitmapShader`, iOS desen ile aynı yerleşim). */
    fun sample(spec: GrainSpec, x: Float, y: Float, firstX: Float = 0f, firstY: Float = 0f): Float {
        val (ax, ay) = anchor(spec, firstX, firstY)
        val s = scaleOf(spec)
        val u = floor((x - ax) / s).toInt()
        val v = floor((y - ay) / s).toInt()
        return alpha(value(spec.textureId, u, v), spec.strength)
    }

    /** Katmanı ya da sayfayı dokuyla doldurmak için sayfa dikdörtgeni (kement dolgu noktaları). */
    fun pageRect(width: Float, height: Float): List<StrokePoint> =
        listOf(StrokePoint(0f, 0f), StrokePoint(width, 0f), StrokePoint(width, height), StrokePoint(0f, height))
}
