package com.karapp.drawguidance.data.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * v56 aşama 7 (2D-4): katman ve seçim renk ayarları. Ton/Doygunluk/Parlaklık (HSL, "Renklendir" seçeneğiyle), Renk Dengesi
 * (gölgeler, ara tonlar, parlak alanlar; camgöbeği–kırmızı, eflatun–yeşil, sarı–mavi) ve Eğriler (RGB, R, G, B; 5 noktalı).
 * Sıra: HSL → renk dengesi → eğriler. Vektör elemanların renkleri yeni renge çevrilir; resimlerin pikselleri aynı
 * fonksiyonla işlenir. iOS `ColorAdjust` ile aynı hesap (≤ 1/255).
 */
data class ColorAdjust(
    /** Ton kaydırma (−180..180°); Renklendir'de ton (0..360 ile aynı, eksi değer 360'tan sayılır). */
    val hue: Float = 0f,
    /** Doygunluk (−100..100). */
    val saturation: Float = 0f,
    /** Parlaklık (−100..100). */
    val lightness: Float = 0f,
    val colorize: Boolean = false,
    /** Renk dengesi: [aralık 0 gölgeler, 1 ara tonlar, 2 parlak alanlar][kanal 0 C–R, 1 M–G, 2 Y–B], −100..100. */
    val balance: List<List<Float>> = ZERO_BALANCE,
    val preserveLuminosity: Boolean = true,
    /** Eğriler: [0 RGB, 1 R, 2 G, 3 B] için x = 0, .25, .5, .75, 1 noktalarındaki çıkış (0..1). */
    val curves: List<List<Float>> = IDENTITY_CURVES,
) {
    val hslIdentity: Boolean get() = !colorize && hue == 0f && saturation == 0f && lightness == 0f
    val balanceIdentity: Boolean get() = balance.all { r -> r.all { it == 0f } }
    val curvesIdentity: Boolean get() = curves == IDENTITY_CURVES
    val isIdentity: Boolean get() = hslIdentity && balanceIdentity && curvesIdentity

    fun withBalance(range: Int, channel: Int, v: Float): ColorAdjust =
        copy(balance = balance.mapIndexed { i, r -> if (i == range) r.mapIndexed { j, x -> if (j == channel) v else x } else r })

    fun withCurve(channel: Int, index: Int, v: Float): ColorAdjust =
        copy(curves = curves.mapIndexed { i, c -> if (i == channel) c.mapIndexed { j, x -> if (j == index) v.coerceIn(0f, 1f) else x } else c })

    companion object {
        val ZERO_BALANCE: List<List<Float>> = List(3) { List(3) { 0f } }
        val CURVE_X: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val IDENTITY_CURVES: List<List<Float>> = List(4) { CURVE_X }
    }
}

object ColorAdjustOps {

    /** Eğri için 256 girdili arama tablosu (Catmull-Rom, uçlarda doğrusal uzatma; 0..255). */
    fun curveLut(ys: List<Float>): IntArray {
        val xs = ColorAdjust.CURVE_X
        val n = xs.size
        return IntArray(256) { i ->
            val x = i / 255f
            var k = 0
            while (k < n - 2 && x > xs[k + 1]) k++
            val t = ((x - xs[k]) / (xs[k + 1] - xs[k])).coerceIn(0f, 1f)
            val p1 = ys[k]; val p2 = ys[k + 1]
            // Uçlarda doğrusal uzatma (düz eğri düz kalsın)
            val p0 = if (k > 0) ys[k - 1] else 2f * p1 - p2
            val p3 = if (k + 2 < n) ys[k + 2] else 2f * p2 - p1
            val t2 = t * t; val t3 = t2 * t
            val v = 0.5f * (2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3)
            (v.coerceIn(0f, 1f) * 255f).roundToInt()
        }
    }

    /** Ayarın piksel hesaplayıcısı (tablolar bir kez kurulur). */
    class Mapper(val adj: ColorAdjust) {
        private val luts: Array<IntArray>? = if (adj.curvesIdentity) null else Array(4) { curveLut(adj.curves[it]) }
        private val hsl = FloatArray(3)
        private val rgb = FloatArray(3)

        fun map(argb: Int): Int {
            val a = argb ushr 24
            if (a == 0 || adj.isIdentity) return argb
            var r = ((argb shr 16) and 0xFF) / 255f
            var g = ((argb shr 8) and 0xFF) / 255f
            var b = (argb and 0xFF) / 255f
            if (!adj.hslIdentity) {
                rgbToHsl(r, g, b, hsl)
                val k = adj.saturation / 100f
                val lk = adj.lightness / 100f
                val h: Float
                val s: Float
                if (adj.colorize) {
                    h = adj.hue
                    s = (0.25f + if (k >= 0f) 0.75f * k else 0.25f * k).coerceIn(0f, 1f)
                } else {
                    h = hsl[0] + adj.hue
                    s = (if (k >= 0f) hsl[1] + (1f - hsl[1]) * k else hsl[1] * (1f + k)).coerceIn(0f, 1f)
                }
                val l = (if (lk >= 0f) hsl[2] + (1f - hsl[2]) * lk else hsl[2] * (1f + lk)).coerceIn(0f, 1f)
                hslToRgb(h, s, l, rgb)
                r = rgb[0]; g = rgb[1]; b = rgb[2]
            }
            if (!adj.balanceIdentity) {
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                val ws = ((0.5f - lum) * 2f).coerceIn(0f, 1f)
                val wh = ((lum - 0.5f) * 2f).coerceIn(0f, 1f)
                val wm = (1f - abs(lum - 0.5f) * 2f).coerceIn(0f, 1f)
                val bl = adj.balance
                val k = 0.32f / 100f
                r += (bl[0][0] * ws + bl[1][0] * wm + bl[2][0] * wh) * k
                g += (bl[0][1] * ws + bl[1][1] * wm + bl[2][1] * wh) * k
                b += (bl[0][2] * ws + bl[1][2] * wm + bl[2][2] * wh) * k
                if (adj.preserveLuminosity) {
                    val d = lum - (0.299f * r + 0.587f * g + 0.114f * b)
                    r += d; g += d; b += d
                }
            }
            var ri = to8(r); var gi = to8(g); var bi = to8(b)
            luts?.let { l ->
                ri = l[0][l[1][ri]]; gi = l[0][l[2][gi]]; bi = l[0][l[3][bi]]
            }
            return (a shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        fun mapPixels(px: IntArray): IntArray = IntArray(px.size) { map(px[it]) }
    }

    fun apply(argb: Int, adj: ColorAdjust): Int = Mapper(adj).map(argb)

    private fun to8(v: Float): Int = (v.coerceIn(0f, 1f) * 255f).roundToInt()

    private fun rgbToHsl(r: Float, g: Float, b: Float, out: FloatArray) {
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        if (mx == mn) { out[0] = 0f; out[1] = 0f; out[2] = l; return }
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        out[0] = h * 60f; out[1] = s; out[2] = l
    }

    private fun hue2rgb(p: Float, q: Float, t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 0.5f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    private fun hslToRgb(h0: Float, s: Float, l: Float, out: FloatArray) {
        if (s <= 0f) { out[0] = l; out[1] = l; out[2] = l; return }
        val h = (((h0 % 360f) + 360f) % 360f) / 360f
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        out[0] = hue2rgb(p, q, h + 1f / 3f); out[1] = hue2rgb(p, q, h); out[2] = hue2rgb(p, q, h - 1f / 3f)
    }

    // ------------------------------------------------------------------ renk metni

    /** "#RRGGBB" ya da "#AARRGGBB" → ARGB (okunamazsa null). */
    fun parseHex(hex: String): Int? {
        val h = hex.trim().removePrefix("#")
        return when (h.length) {
            6 -> h.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
            8 -> h.toLongOrNull(16)?.toInt()
            else -> null
        }
    }

    /** ARGB → aynı biçimde metin ([eightDigits] true: "#AARRGGBB"). */
    fun formatHex(argb: Int, eightDigits: Boolean): String =
        if (eightDigits) "#%08X".format(argb) else "#%06X".format(argb and 0xFFFFFF)

    fun mapHex(hex: String, m: Mapper): String {
        val c = parseHex(hex) ?: return hex
        val out = m.map(c)
        if (out == c) return hex
        return formatHex(out, hex.trim().removePrefix("#").length == 8)
    }

    private fun mapGradient(g: GradientSpec?, m: Mapper): GradientSpec? = g?.copy(colors = g.colors.map { mapHex(it, m) })

    /**
     * Vektör elemanın bütün renkleri (çizgi, şekil kenarı ve dolgusu, gradyan, yazı kenarı, kutu, gölge). Resim ve
     * silgi değişmez (resim pikselleri ayrıca işlenir).
     */
    fun mapStroke(s: Stroke, m: Mapper): Stroke {
        if (s.brushType == BrushType.IMAGE || s.brushType == BrushType.ERASER) return s
        var o = s.copy(colorHex = mapHex(s.colorHex, m))
        s.shape?.let { sh ->
            val st = sh.style
            o = o.copy(shape = sh.copy(style = st.copy(
                strokeColorHex = mapHex(st.strokeColorHex, m),
                fillColorHex = st.fillColorHex?.let { mapHex(it, m) },
                fillGradient = mapGradient(st.fillGradient, m),
            )))
        }
        s.text?.let { t ->
            o = o.copy(text = t.copy(
                outlineColorHex = t.outlineColorHex?.let { mapHex(it, m) },
                box = t.box?.let { b ->
                    b.copy(
                        fillHex = b.fillHex?.let { mapHex(it, m) }, borderHex = b.borderHex?.let { mapHex(it, m) }, fillGradient = mapGradient(b.fillGradient, m),
                        effects = b.effects?.let { e -> e.copy(shadowHex = e.shadowHex?.let { mapHex(it, m) }, outerHex = e.outerHex?.let { mapHex(it, m) }, gradient = mapGradient(e.gradient, m)) },
                    )
                },
            ))
        }
        return o
    }

    /** [ids] elemanlarının renkleri; resimler değişmez. */
    fun mapStrokes(strokes: List<Stroke>, ids: Set<String>, adj: ColorAdjust): List<Stroke> {
        if (adj.isIdentity) return strokes
        val m = Mapper(adj)
        return strokes.map { if (it.id in ids) mapStroke(it, m) else it }
    }
}

/**
 * v56 aşama 7 katman işlemleri (saf): katmanı çoğalt, birden çok katmanı birleştir. Katmanlar belgenin bütün
 * sayfalarında geçerli olduğu için elemanlar her sayfada taşınır/çoğaltılır.
 */
object LayerOps {

    /** Birleştirmede görünüm korunsun diye resme çevirmek gerekir mi (opaklık, mod, alfa kilidi ya da kırpma). */
    fun needsRaster(layers: List<Layer>, ids: Set<String>): Boolean = layers.any { it.id in ids && it.composited }

    /**
     * [id] katmanının kopyası hemen üstte: adı [name], özellikleri aynı, bütün elemanlar yeni kimlikle ([newId] ile).
     * Grup kimlikleri de yenilenir (kopyalar kendi aralarında gruplu kalır).
     */
    fun duplicate(layers: List<Layer>, pages: List<List<Stroke>>, id: String, copyId: String, name: String, newId: () -> String): Pair<List<Layer>, List<List<Stroke>>> {
        val i = layers.indexOfFirst { it.id == id }
        if (i < 0) return layers to pages
        val copy = layers[i].copy(id = copyId, name = name, locked = false)
        val outLayers = layers.toMutableList().also { it.add(i + 1, copy) }
        val groups = HashMap<String, String>()
        val outPages = pages.map { page ->
            val out = ArrayList<Stroke>(page.size * 2)
            out.addAll(page)
            page.filter { Layers.of(it, layers) == id }.forEach { s ->
                out.add(s.copy(id = newId(), layerId = copyId, groupId = s.groupId?.let { g -> groups.getOrPut(g) { newId() } }))
            }
            Layers.sort(out, outLayers)
        }
        return outLayers to outPages
    }

    /**
     * [ids] katmanları, sıraları korunarak en üstteki seçili katmanın yerinde tek katman olur (elemanlar vektör olarak
     * taşınır; katman özellikleri en üsttekinin). En az iki katman gerekir.
     */
    fun mergeVector(layers: List<Layer>, pages: List<List<Stroke>>, ids: Set<String>): Pair<List<Layer>, List<List<Stroke>>> {
        val chosen = layers.filter { it.id in ids }
        if (chosen.size < 2) return layers to pages
        val target = chosen.last()
        val outLayers = layers.filter { it.id !in ids || it.id == target.id }
        val outPages = pages.map { page ->
            // Katman sırasına göre (alttan üste) dizilip hedef katmana alınır
            val moved = page.withIndex().sortedWith(compareBy({ Layers.indexOf(it.value, layers) }, { it.index })).map { it.value }
            Layers.sort(moved.map { s ->
                if (Layers.of(s, layers) in ids) s.copy(layerId = if (target.id == outLayers.first().id) null else target.id) else s
            }, outLayers)
        }
        return outLayers to outPages
    }

    /** [id] katmanının hemen altındaki katman (yoksa null). */
    fun below(layers: List<Layer>, id: String): Layer? {
        val i = layers.indexOfFirst { it.id == id }
        return if (i > 0) layers[i - 1] else null
    }
}

/** v56 aşama 7 hazır ayarlar (Ekle menüsü): ışık katmanı, gölge katmanı ve parıltı fırçası. */
object V56Presets {
    enum class Kind { LIGHT, SHADOW }

    data class LayerPreset(val blend: LayerBlend, val opacity: Float, val colorHex: String, val brush: BrushType, val size: Float, val brushOpacity: Float)

    fun layer(kind: Kind): LayerPreset = when (kind) {
        Kind.LIGHT -> LayerPreset(LayerBlend.SCREEN, 0.7f, "#FFE9A8", BrushType.AIRBRUSH, 80f, 0.35f)
        Kind.SHADOW -> LayerPreset(LayerBlend.MULTIPLY, 0.6f, "#5B4B8A", BrushType.AIRBRUSH, 80f, 0.35f)
    }

    /** Parıltı fırçası: yıldız damgası, açık altın rengi (ışık katmanında en iyi görünür). */
    val glitter = LayerPreset(LayerBlend.ADD, 1f, "#FFF3B8", BrushType.STAMP_STAR, 22f, 0.9f)
}
