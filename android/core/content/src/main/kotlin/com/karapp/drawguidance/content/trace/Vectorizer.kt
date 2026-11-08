package com.karapp.drawguidance.content.trace

import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VPath
import java.util.Locale
import kotlin.math.cos
import kotlin.math.pow

/**
 * Vektörleştirme ayarları.
 *
 * - [colors]: palet boyu (2..24); yakın renkler birleşebildiğinden sonuç paleti daha küçük olabilir.
 * - [detail]: 0 = kaba, yumuşak, az nokta … 1 = ince ayrıntı (sadeleştirme toleransı, benek eşiği, süzgeç gücü).
 * - [smooth]: köşeler dışındaki sınırlar eğriye (kübik Bézier) uydurulur; kapalıysa çokgen (RDP).
 * - [maxSide]: görüntü izlenmeden önce uzun kenarı en çok bu kadar olacak şekilde küçültülür.
 * - [minRegion]: görüntü alanına oranla bundan küçük bölgeler komşusuna katılır ([detail] 0.5'te; ayrıntı arttıkça
 *   eşik yarıya iner, azaldıkça iki katına çıkar).
 * - [ignoreTransparent]: alfa < 128 pikseller arka plandır (izlenmez); false ise görüntü beyaz zemine bindirilir.
 * - [outline]: parçalara dolgunun koyu tonunda ince (0.35) kenar çizgisi; kapalıyken parçalar dolgu renginde çok ince
 *   (0.25) kenarla çizilir (komşular arasındaki kenar yumuşatma çizgilerini kapatır).
 */
data class TraceOptions(
    val colors: Int = 8,
    val detail: Float = 0.5f,
    val smooth: Boolean = true,
    val maxSide: Int = 480,
    val minRegion: Float = 0.0003f,
    val ignoreTransparent: Boolean = true,
    val outline: Boolean = false,
)

/**
 * Vektörleştirme sonucu. [paths] görüntünün birim kutusunda (x: 0..1 genişlik, y: 0..1 yükseklik), arkadan öne
 * sıralı kapalı dolgulu parçalar; [palette] kullanılan renkler ("#RRGGBB", çoktan aza); [width]×[height] izlemenin
 * yapıldığı (küçültülmüş) çözünürlük — en/boy oranı kaynakla aynıdır.
 */
data class TraceResult(val paths: List<VPath>, val palette: List<String>, val width: Int, val height: Int)

/**
 * Bitmap → renkli vektör şekiller (tamamen Kotlin, Android'e bağımsız).
 *
 * Adımlar: alan ortalamalı küçültme → kenar koruyan gürültü süzgeci → Lab uzayında k-means renk nicemleme (küçük ama
 * belirgin renkler için merkez takası) → 3×3 çoğunluk süzgeci (yakın renkli bant sınırlarında düzleştirme) → 4-bağlı
 * bölgeler, benek/ince şerit birleştirme → piksel kenarı takibiyle sınırlar (komşular ortak yay kullanır: aralarında
 * boşluk kalmaz) → köşe korumalı Bézier uydurma ya da RDP → alanı büyükten küçüğe (ressam sırası) kapalı [VPath] listesi.
 * Durumsuzdur; aynı anda birden çok iş parçacığından çağrılabilir. Aynı girdi her zaman aynı sonucu verir.
 */
object Vectorizer {

    /** Sonuçtaki en çok parça sayısı. */
    const val MAX_PARTS = 1500

    /** Sonuçtaki toplam en çok nokta sayısı. */
    const val MAX_POINTS = 60_000

    /**
     * [argb]: satır satır 0xAARRGGBB pikseller (Android `Bitmap.getPixels` gibi). [cancel] true dönerse
     * [java.util.concurrent.CancellationException] fırlatılır. Arka plan iş parçacığında çağrılmalıdır.
     */
    fun trace(
        argb: IntArray,
        width: Int,
        height: Int,
        options: TraceOptions = TraceOptions(),
        cancel: () -> Boolean = { false },
    ): TraceResult {
        require(width > 0 && height > 0) { "Geçersiz boyut: ${width}x$height" }
        require(argb.size >= width.toLong() * height) { "Piksel dizisi kısa: ${argb.size} < ${width}x$height" }
        checkCancel(cancel)
        val prm = Params(options)
        val img0 = Downscale.run(argb, width, height, prm.maxSide, options.ignoreTransparent, cancel)
        checkCancel(cancel)
        val img = denoise(img0, prm.denoiseThreshold, prm.denoisePasses, cancel)
        val q = Quantizer.run(img, prm.colors, prm.mergeDE, cancel)
        if (q.paletteRgb.isEmpty()) return TraceResult(emptyList(), emptyList(), img.w, img.h)
        checkCancel(cancel)
        var labels = q.labels
        val soft = softPairs(q.paletteRgb, SOFT_DE)
        repeat(prm.modePasses) {
            labels = modeFilter(labels, img.w, img.h, q.paletteRgb.size, prm.modeMaxOwn, prm.modeMinBest, soft)
            checkCancel(cancel)
        }
        val minArea = prm.minRegion * img.w.toDouble() * img.h
        val map = Regions.build(labels, img.w, img.h, q.paletteRgb, minArea, prm.thinRatio, MAX_PARTS, cancel)
        val tracer = Tracer(map)
        tracer.traceAll(cancel)

        // Ressam sırası: dış sınır alanı büyükten küçüğe (bir bölgenin deliğindekiler hep daha küçüktür)
        val order = (0 until map.count).filter { tracer.outer[it] != null }
            .sortedWith(compareByDescending<Int> { tracer.outer[it]!!.area }.thenBy { it })

        var scale = 1f
        var polys: List<Pair<Int, FloatArray>> = emptyList()
        for (attempt in 0 until 8) {
            tracer.fit(FitParams(prm.eps * scale, options.smooth, prm.cornerCos, prm.cornerScale, prm.flatTol * scale), cancel)
            polys = order.mapNotNull { r ->
                val outer = tracer.polygon(tracer.outer[r]!!)
                if (outer.size < 6) return@mapNotNull null
                val holes = tracer.holes[r]?.map { tracer.polygon(it) }
                val poly = if (holes.isNullOrEmpty()) outer else bridgeHoles(outer, holes)
                if (kotlin.math.abs(signedArea(outer)) < 0.25) null else r to poly
            }
            val total = polys.sumOf { it.second.size / 2 }
            if (total <= MAX_POINTS) break
            scale *= 1.6f
        }
        // Son çare: sınır hâlâ aşılıyorsa en küçük parçalar atılır (liste büyükten küçüğe sıralı)
        if (polys.sumOf { it.second.size / 2 } > MAX_POINTS) {
            val kept = ArrayList<Pair<Int, FloatArray>>()
            var sum = 0
            for (pp in polys) { if (sum + pp.second.size / 2 <= MAX_POINTS) { kept += pp; sum += pp.second.size / 2 } }
            polys = kept
        }
        checkCancel(cancel)

        val iw = 1f / img.w; val ih = 1f / img.h
        val hex = q.paletteRgb.map { "#%06X".format(Locale.ROOT, it and 0xFFFFFF) }
        val dark = q.paletteRgb.map { c ->
            val r = ((c shr 16) and 255) * 0.62f; val g = ((c shr 8) and 255) * 0.62f; val b = (c and 255) * 0.62f
            "#%02X%02X%02X".format(Locale.ROOT, r.toInt(), g.toInt(), b.toInt())
        }
        val paths = polys.map { (r, poly) ->
            val lab = map.label[r]
            val pts = List(poly.size / 2) { i ->
                P((poly[i * 2] * iw).coerceIn(0f, 1f), (poly[i * 2 + 1] * ih).coerceIn(0f, 1f))
            }
            VPath(
                points = pts,
                closed = true,
                fill = hex[lab],
                stroke = if (options.outline) dark[lab] else hex[lab],
                width = if (options.outline) 0.35f else 0.25f,
            )
        }
        val used = polys.map { map.label[it.first] }.toSet()
        val palette = hex.indices.filter { it in used }.map { hex[it] }
        return TraceResult(paths, palette, img.w, img.h)
    }

    /** Bu kadar (ΔE) yakın renkli bölgelerin ortak sınırı "yumuşak" sayılır (geçiş bantları) ve düzleştirilir. */
    private const val SOFT_DE = 22f

    /** Ayarlardan türetilen iç parametreler. */
    private class Params(o: TraceOptions) {
        val colors = o.colors.coerceIn(2, 24)
        val detail = if (o.detail.isNaN()) 0.5f else o.detail.coerceIn(0f, 1f)
        val coarse = 1f - detail
        val maxSide = o.maxSide.coerceIn(16, 4096)
        val eps = 0.9f + 1.4f * coarse
        val flatTol = 0.12f + 0.2f * coarse
        val denoiseThreshold = 40
        val denoisePasses = if (detail < 0.75f) 2 else 1
        val cornerCos = cos(Math.toRadians(40.0 + 15.0 * coarse)).toFloat()
        val cornerScale = 2f + coarse
        val minRegion = (if (o.minRegion.isNaN()) 0f else o.minRegion.coerceIn(0f, 0.2f)) * 2.0.pow(1.0 - 2.0 * detail)
        val thinRatio = 0.56f + 0.3f * coarse
        val mergeDE = 2.5f + 5f * coarse
        val modePasses = if (detail < 0.35f) 3 else if (detail < 0.8f) 2 else 1
        val modeMaxOwn = if (detail < 0.35f) 3 else if (detail < 0.8f) 2 else 1
        val modeMinBest = if (detail < 0.35f) 5 else 4
    }
}
