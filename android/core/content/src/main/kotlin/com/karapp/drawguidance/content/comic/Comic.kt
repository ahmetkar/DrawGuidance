package com.karapp.drawguidance.content.comic

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * v27 çizgi roman yardımcıları (saf Kotlin): sayfa panel düzenleri, hız/odak/patlama çizgileri ve efekt yazısı
 * (ses efekti) hazırları. Çizim katmanı bunları tuval elemanlarına çevirir.
 */

/** Panel düzeni: sayfanın kenar boşlukları içinde oranlarla verilmiş kareler (sol, üst, sağ, alt; 0..1). */
class PanelLayout(val id: String, val name: String, val cells: List<FloatArray>)

object ComicPanels {
    private fun r(l: Float, t: Float, rr: Float, b: Float) = floatArrayOf(l, t, rr, b)

    val layouts: List<PanelLayout> = listOf(
        PanelLayout("single", "Tek kare", listOf(r(0f, 0f, 1f, 1f))),
        PanelLayout("rows2", "İki şerit", listOf(r(0f, 0f, 1f, 0.5f), r(0f, 0.5f, 1f, 1f))),
        PanelLayout("cols2", "İki sütun", listOf(r(0f, 0f, 0.5f, 1f), r(0.5f, 0f, 1f, 1f))),
        PanelLayout("rows3", "Üç şerit", listOf(r(0f, 0f, 1f, 1 / 3f), r(0f, 1 / 3f, 1f, 2 / 3f), r(0f, 2 / 3f, 1f, 1f))),
        PanelLayout("grid4", "Dörtlü", listOf(r(0f, 0f, 0.5f, 0.5f), r(0.5f, 0f, 1f, 0.5f), r(0f, 0.5f, 0.5f, 1f), r(0.5f, 0.5f, 1f, 1f))),
        PanelLayout(
            "grid6", "Altılı",
            List(6) { i -> val c = i % 2; val row = i / 2; r(c / 2f, row / 3f, (c + 1) / 2f, (row + 1) / 3f) },
        ),
        PanelLayout("big_top", "Büyük + iki", listOf(r(0f, 0f, 1f, 0.58f), r(0f, 0.58f, 0.5f, 1f), r(0.5f, 0.58f, 1f, 1f))),
        PanelLayout("big_bottom", "İki + büyük", listOf(r(0f, 0f, 0.5f, 0.42f), r(0.5f, 0f, 1f, 0.42f), r(0f, 0.42f, 1f, 1f))),
        PanelLayout(
            "manga", "Manga",
            listOf(r(0f, 0f, 1f, 0.3f), r(0f, 0.3f, 0.62f, 0.65f), r(0.62f, 0.3f, 1f, 0.65f), r(0f, 0.65f, 0.38f, 1f), r(0.38f, 0.65f, 1f, 1f)),
        ),
        PanelLayout("strip3", "Gazete şeridi", listOf(r(0f, 0f, 1 / 3f, 1f), r(1 / 3f, 0f, 2 / 3f, 1f), r(2 / 3f, 0f, 1f, 1f))),
    )

    fun byId(id: String): PanelLayout? = layouts.firstOrNull { it.id == id }

    /**
     * Düzenin karelerinin tuval dikdörtgenleri (sol, üst, sağ, alt): [w]×[h] sayfada [margin] kenar boşluğu ve kareler
     * arası [gutter] (ikisi de kısa kenarın oranı).
     */
    fun rects(layout: PanelLayout, w: Float, h: Float, margin: Float = 0.045f, gutter: Float = 0.022f): List<FloatArray> {
        val m = min(w, h) * margin
        val g = min(w, h) * gutter / 2f
        val iw = w - 2 * m; val ih = h - 2 * m
        return layout.cells.map { c ->
            val l = m + c[0] * iw + (if (c[0] > 0.001f) g else 0f)
            val t = m + c[1] * ih + (if (c[1] > 0.001f) g else 0f)
            val rr = m + c[2] * iw - (if (c[2] < 0.999f) g else 0f)
            val b = m + c[3] * ih - (if (c[3] < 0.999f) g else 0f)
            floatArrayOf(l, t, max(rr, l + 1f), max(b, t + 1f))
        }
    }
}

/**
 * Çizgi türleri (v27: yatay/dikey hız, odak, patlama; v29 manga/çizgi roman efekt çizgileri, bkz. "line effects" —
 * dousen, shuuchuu-sen, odoro-sen, housha-sen, tare-sen, yure-sen, tere-sen; CLIP STUDIO efekt araçları):
 * çapraz hız, kavisli hareket izi, ürkütücü (pürüzlü) odak, flaş (ince yoğun ışınlar, ortası boş halka), neşeli
 * ışınlar (kümeler), damlayan çizgiler (karamsarlık), titreme yayları, kızarma tarama, girdap ve şok halkaları.
 */
enum class ComicLinesKind {
    SPEED_H, SPEED_V, FOCUS, BURST,
    SPEED_DIAG, MOTION_CURVE, FOCUS_ROUGH, FLASH, RADIATE, DRIP, WAVER, BLUSH, SWIRL, RINGS,
    /** v36 bir noktaya (karaktere) yakınlaştırma, perspektif/derinlik, daralan-genişleyen ışınlar, yoğun manga, yumuşak anime. */
    ZOOM, PERSPECTIVE, BEAMS, MANGA_DENSE, ANIME_SOFT,
    /**
     * v50 Batı çizgi romanı: enerji çıtırtısı (Kirby krackle, kozmik enerji noktaları), darbe yıldızı (tırtıklı patlama
     * kenarı), şaşkınlık çizgileri (emanata: başın çevresinde kısa çizgiler), elektrik (zikzak şimşekler) ve Ben-Day
     * nokta tramı. Manga: kasvet (tate-sen, yukarıdan inen dikey çizgiler), parıltı (kira-kira, dört köşeli yıldızlar),
     * tarama (kake-ami, çapraz tram) ve yoğun hız (ryūsen-beta, ortası boş sık yatay çizgiler).
     */
    KRACKLE, IMPACT, SURPRISE, ELECTRIC, BENDAY,
    GLOOM, SPARKLE, CROSSHATCH, SPEED_DENSE,
}

/** v50 odak çizgilerinin tarzı (pencerede iki bölüm): Batı çizgi romanı ve manga/anime. */
enum class ComicLinesStyle { COMIC, MANGA }

/** Tek çizgi: noktalar (x, y) ve nokta başına basınç (incelme). */
class ComicLine(val xs: FloatArray, val ys: FloatArray, val pressures: FloatArray)

object ComicLines {
    /** Sıklık sınırları (sonradan değiştirmede de). */
    const val MIN_DENSITY = 0.25f
    const val MAX_DENSITY = 3f

    /** v36 uzunluk çarpanı sınırları (odak noktası alanın 0..1 oranı, yön derece). */
    const val MIN_LENGTH = 0.2f
    const val MAX_LENGTH = 1.6f

    /** v50 türün tarzı (pencerede Çizgi roman ya da Manga bölümünde). */
    fun style(kind: ComicLinesKind): ComicLinesStyle = if (kind in MANGA_KINDS) ComicLinesStyle.MANGA else ComicLinesStyle.COMIC

    /** v50 tarzın türleri (pencere sırası). */
    fun kinds(style: ComicLinesStyle): List<ComicLinesKind> = when (style) {
        ComicLinesStyle.COMIC -> listOf(
            ComicLinesKind.SPEED_H, ComicLinesKind.SPEED_V, ComicLinesKind.SPEED_DIAG, ComicLinesKind.MOTION_CURVE, ComicLinesKind.BURST,
            ComicLinesKind.IMPACT, ComicLinesKind.ZOOM, ComicLinesKind.PERSPECTIVE, ComicLinesKind.BEAMS, ComicLinesKind.RINGS,
            ComicLinesKind.SWIRL, ComicLinesKind.KRACKLE, ComicLinesKind.ELECTRIC, ComicLinesKind.SURPRISE, ComicLinesKind.BENDAY,
        )
        ComicLinesStyle.MANGA -> listOf(
            ComicLinesKind.FOCUS, ComicLinesKind.MANGA_DENSE, ComicLinesKind.FOCUS_ROUGH, ComicLinesKind.FLASH, ComicLinesKind.RADIATE,
            ComicLinesKind.SPEED_DENSE, ComicLinesKind.DRIP, ComicLinesKind.GLOOM, ComicLinesKind.WAVER, ComicLinesKind.BLUSH,
            ComicLinesKind.SPARKLE, ComicLinesKind.CROSSHATCH, ComicLinesKind.ANIME_SOFT,
        )
    }

    private val MANGA_KINDS = setOf(
        ComicLinesKind.FOCUS, ComicLinesKind.MANGA_DENSE, ComicLinesKind.FOCUS_ROUGH, ComicLinesKind.FLASH, ComicLinesKind.RADIATE,
        ComicLinesKind.SPEED_DENSE, ComicLinesKind.DRIP, ComicLinesKind.GLOOM, ComicLinesKind.WAVER, ComicLinesKind.BLUSH,
        ComicLinesKind.SPARKLE, ComicLinesKind.CROSSHATCH, ComicLinesKind.ANIME_SOFT,
    )

    /**
     * v50 türün önerilen ayarları (her tür kendi ayarlarıyla başlar): sıklık, uzunluk ve kalınlık çarpanı. Sıra:
     * (density, length, thickness).
     */
    fun defaults(kind: ComicLinesKind): Triple<Float, Float, Float> = when (kind) {
        ComicLinesKind.MANGA_DENSE, ComicLinesKind.SPEED_DENSE -> Triple(1.2f, 1f, 0.8f)
        ComicLinesKind.IMPACT -> Triple(1f, 1f, 1.6f)
        ComicLinesKind.ELECTRIC -> Triple(1f, 1f, 1.4f)
        ComicLinesKind.SPARKLE, ComicLinesKind.KRACKLE -> Triple(1f, 1f, 1.2f)
        ComicLinesKind.CROSSHATCH, ComicLinesKind.BENDAY -> Triple(1f, 1f, 0.7f)
        ComicLinesKind.GLOOM -> Triple(1f, 0.8f, 0.9f)
        else -> Triple(1f, 1f, 1f)
    }

    /** Odak noktası ayarı olan türler (merkezden yayılan, yakınlaştırma, derinlik, ışın). */
    fun usesFocus(kind: ComicLinesKind): Boolean = kind in FOCUSED

    /** Yön ayarı olan türler (paralel hız çizgileri, ışın yelpazesi). */
    fun usesAngle(kind: ComicLinesKind): Boolean = kind == ComicLinesKind.SPEED_H || kind == ComicLinesKind.SPEED_V ||
        kind == ComicLinesKind.SPEED_DIAG || kind == ComicLinesKind.BEAMS || kind == ComicLinesKind.CROSSHATCH || kind == ComicLinesKind.SPEED_DENSE

    /** Türün çizgi kalınlığı çarpanı (ışın ve yoğun manga çizgileri kalın). */
    fun widthFactor(kind: ComicLinesKind): Float = when (kind) {
        ComicLinesKind.BEAMS -> 5f
        ComicLinesKind.MANGA_DENSE -> 1.7f
        ComicLinesKind.ANIME_SOFT -> 3.2f
        else -> 1f
    }

    /** Yumuşak anime çizgilerinin pastel renkleri (renk seçilmezse sırayla). */
    val SOFT_COLORS = listOf("#FF9EC7", "#8FD3FF", "#C7A6FF", "#FFE08A", "#9FF0D0")

    private val FOCUSED = setOf(
        ComicLinesKind.FOCUS, ComicLinesKind.BURST, ComicLinesKind.FOCUS_ROUGH, ComicLinesKind.FLASH, ComicLinesKind.RADIATE,
        ComicLinesKind.SWIRL, ComicLinesKind.RINGS, ComicLinesKind.WAVER,
        ComicLinesKind.ZOOM, ComicLinesKind.PERSPECTIVE, ComicLinesKind.BEAMS, ComicLinesKind.MANGA_DENSE, ComicLinesKind.ANIME_SOFT,
        ComicLinesKind.IMPACT, ComicLinesKind.SURPRISE, ComicLinesKind.ELECTRIC, ComicLinesKind.KRACKLE, ComicLinesKind.SPARKLE,
        ComicLinesKind.SPEED_DENSE,
    )

    /**
     * [l],[t],[r],[b] alanında çizgiler. Sabit tohumla ([seed]) her seferinde aynı düzen; sıklık [density]
     * ([MIN_DENSITY]–[MAX_DENSITY]); sıklık artınca yeni çizgiler eklenir, eskiler yerinde kalır (aynı tohum dizisi).
     * v36: [focusX],[focusY] odak noktası (alanın oranı; merkezden yayılan türlerde), [angle] yön (derece; paralel
     * türlerde ve ışınlarda), [length] çizgi uzunluğu çarpanı. Varsayılanlarla v29 çizimleri birebir aynıdır.
     */
    fun generate(
        kind: ComicLinesKind, l: Float, t: Float, r: Float, b: Float, seed: Int = 27, density: Float = 1f,
        focusX: Float = 0.5f, focusY: Float = 0.5f, angle: Float = 0f, length: Float = 1f,
    ): List<ComicLine> {
        val rnd = Random(seed)
        val w = r - l; val h = b - t
        if (w < 2f || h < 2f) return emptyList()
        val d = density.coerceIn(MIN_DENSITY, MAX_DENSITY)
        fun count(base: Int, lo: Int, hi: Int) = (base * d).toInt().coerceIn(lo, hi)
        val len = length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        val cx = l + w * focusX.coerceIn(0f, 1f); val cy = t + h * focusY.coerceIn(0f, 1f)
        val centered = kotlin.math.abs(focusX - 0.5f) < 1e-4f && kotlin.math.abs(focusY - 0.5f) < 1e-4f
        // İç uç oranı uzunlukla: uzun çizgi merkeze daha çok yaklaşır (1: v29 ile aynı)
        fun inner(base: Float) = if (len == 1f) base else (1f - (1f - base) * len).coerceIn(0.02f, 0.97f)
        // Dış uç: odaktan alan kenarına; iç uç odağın çevresindeki elips üzerinde ama dış ucu geçmez
        fun edge(ca: Float, sa: Float) = if (centered) edgeDistance(ca, sa, w, h) else edgeFrom(cx, cy, ca, sa, l, t, r, b)
        fun innerAt(ca: Float, sa: Float, f: Float, outer: Float): Pair<Float, Float> {
            val ex = ca * (w / 2f) * f; val ey = sa * (h / 2f) * f
            val dist = hypot(ex, ey)
            if (centered || dist <= outer * 0.92f) return (cx + ex) to (cy + ey)
            val k = outer * 0.92f / dist.coerceAtLeast(1e-3f)
            return (cx + ex * k) to (cy + ey * k)
        }
        val out = ArrayList<ComicLine>()
        if (angle != 0f && (kind == ComicLinesKind.SPEED_H || kind == ComicLinesKind.SPEED_V || kind == ComicLinesKind.SPEED_DIAG)) {
            // v36 döndürülmüş paralel hız çizgileri (alanın içine kırpılır)
            val base = when (kind) { ComicLinesKind.SPEED_H -> 0.0; ComicLinesKind.SPEED_V -> 90.0; else -> -28.0 }
            parallel(out, rnd, (base + angle) * PI / 180.0, l, t, r, b, count(if (kind == ComicLinesKind.SPEED_DIAG) 30 else 26, 4, 130), len)
            return out
        }
        when (kind) {
            ComicLinesKind.SPEED_H, ComicLinesKind.SPEED_V -> {
                val horizontal = kind == ComicLinesKind.SPEED_H
                val across = if (horizontal) h else w
                val along = if (horizontal) w else h
                val n = count(26, 4, 120)
                for (i in 0 until n) {
                    val pos = (i + 0.5f + (rnd.nextFloat() - 0.5f) * 0.8f) / n * across
                    val ln = (along * (0.25f + rnd.nextFloat() * 0.6f) * len).coerceAtMost(along)
                    val start = rnd.nextFloat() * (along - ln)
                    out += taper(
                        if (horizontal) l + start else l + pos, if (horizontal) t + pos else t + start,
                        if (horizontal) l + start + ln else l + pos, if (horizontal) t + pos else t + start + ln,
                        startThin = rnd.nextBoolean(),
                    )
                }
            }
            ComicLinesKind.FOCUS -> {
                val n = count(90, 16, 320)
                for (i in 0 until n) {
                    val a = 2 * PI * (i + rnd.nextFloat() * 0.6f) / n
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    // v29 dış uç alanın kenarında (kareden taşmaz); iç uç merkezde boş bir alan bırakır
                    val outer = edge(ca, sa)
                    val (ix, iy) = innerAt(ca, sa, inner(0.36f + rnd.nextFloat() * 0.2f), outer)
                    out += taper(cx + ca * outer, cy + sa * outer, ix, iy, startThin = false)
                }
            }
            ComicLinesKind.BURST -> {
                val n = count(40, 8, 160)
                for (i in 0 until n) {
                    val a = 2 * PI * (i + rnd.nextFloat() * 0.5f) / n
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    val r0 = 0.12f + rnd.nextFloat() * 0.12f
                    val r1 = r0 + (0.6f + rnd.nextFloat() * 0.4f - r0) * len
                    out += taper(cx + ca * w / 2f * r0, cy + sa * h / 2f * r0, cx + ca * w / 2f * r1, cy + sa * h / 2f * r1, startThin = false)
                }
            }
            ComicLinesKind.SPEED_DIAG -> {
                // Çapraz hız: −28° doğrultusunda paralel çizgiler, alanın içine kırpılır
                val ang = -28.0 * PI / 180.0
                val ux = cos(ang).toFloat(); val uy = sin(ang).toFloat()
                val px = -uy; val py = ux
                val span = hypot(w, h)
                val n = count(30, 5, 130)
                for (i in 0 until n) {
                    val off = ((i + 0.5f + (rnd.nextFloat() - 0.5f) * 0.8f) / n - 0.5f) * span
                    val ln = (span * (0.2f + rnd.nextFloat() * 0.45f) * len).coerceAtMost(span)
                    val along = (rnd.nextFloat() - 0.5f) * (span - ln)
                    val sx = cx + px * off + ux * (along - ln / 2f); val sy = cy + py * off + uy * (along - ln / 2f)
                    clip(sx, sy, sx + ux * ln, sy + uy * ln, l, t, r, b)?.let { c -> out += taper(c[0], c[1], c[2], c[3], startThin = rnd.nextBoolean()) }
                }
            }
            ComicLinesKind.MOTION_CURVE -> {
                // Kavisli hareket izi (dousen): soldan sağa yükselen bir yörüngeye paralel, boyları farklı izler
                val n = count(14, 3, 60)
                for (i in 0 until n) {
                    val off = ((i + 0.5f) / n - 0.5f) * h * 0.55f + (rnd.nextFloat() - 0.5f) * h * 0.03f
                    val f0 = rnd.nextFloat() * 0.35f; val f1 = (f0 + 0.35f + rnd.nextFloat() * 0.3f).coerceAtMost(1f)
                    val m = 12
                    val xs = FloatArray(m); val ys = FloatArray(m); val ps = FloatArray(m)
                    for (k in 0 until m) {
                        val f = f0 + (f1 - f0) * k / (m - 1f)
                        val bx = l + w * (0.05f + 0.9f * f)
                        val by = b - h * 0.18f - (h * 0.5f) * (f * f) + off * (1f - f * 0.4f)
                        xs[k] = bx; ys[k] = by.coerceIn(t, b)
                        ps[k] = 0.15f + 0.85f * (k / (m - 1f))
                    }
                    out += ComicLine(xs, ys, ps)
                }
            }
            ComicLinesKind.FOCUS_ROUGH -> {
                // Ürkütücü odak (odoro-sen): pürüzlü, kalın ve seyrek; ortası boş
                val n = count(48, 10, 180)
                for (i in 0 until n) {
                    val a = 2 * PI * (i + rnd.nextFloat() * 0.7f) / n
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    val outer = edge(ca, sa)
                    val (x1, y1) = innerAt(ca, sa, inner(0.42f + rnd.nextFloat() * 0.18f), outer)
                    val x0 = cx + ca * outer; val y0 = cy + sa * outer
                    val m = 10
                    val xs = FloatArray(m); val ys = FloatArray(m); val ps = FloatArray(m)
                    val jit = hypot(x1 - x0, y1 - y0) * 0.025f
                    for (k in 0 until m) {
                        val f = k / (m - 1f)
                        val j = if (k == 0 || k == m - 1) 0f else (rnd.nextFloat() - 0.5f) * 2f * jit
                        xs[k] = x0 + (x1 - x0) * f - sa * j; ys[k] = y0 + (y1 - y0) * f + ca * j
                        ps[k] = 0.25f + 0.75f * (1f - f)
                    }
                    out += ComicLine(xs, ys, ps)
                }
            }
            ComicLinesKind.FLASH -> {
                // Flaş (uni flash): ortada boş bir halka; çok ince ve yoğun ışınlar dışa doğru, iç uçları ince
                val n = count(160, 32, 480)
                for (i in 0 until n) {
                    val a = 2 * PI * (i + rnd.nextFloat() * 0.8f) / n
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    val r0 = 0.3f + rnd.nextFloat() * 0.05f
                    val r1 = r0 + (0.55f + rnd.nextFloat() * 0.45f - r0) * len
                    out += taper(cx + ca * w / 2f * r0, cy + sa * h / 2f * r0, cx + ca * w / 2f * r1, cy + sa * h / 2f * r1, startThin = true, end = 0.05f)
                }
            }
            ComicLinesKind.RADIATE -> {
                // Neşeli ışınlar (housha-sen): üçlü kümeler halinde kenara kadar, aralarında boşluk
                val clusters = count(14, 4, 40)
                for (c in 0 until clusters) {
                    val base = 2 * PI * (c + rnd.nextFloat() * 0.3f) / clusters
                    for (k in 0 until 3) {
                        val a = base + (k - 1) * 0.045
                        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                        val r0 = 0.22f + rnd.nextFloat() * 0.08f
                        // Kenara kadar (alanın dışına taşmadan)
                        val outer = edge(ca, sa)
                        val (ix, iy) = innerAt(ca, sa, r0, outer)
                        val ox = ix + (cx + ca * outer - ix) * len.coerceAtMost(1f); val oy = iy + (cy + sa * outer - iy) * len.coerceAtMost(1f)
                        out += taper(ix, iy, ox, oy, startThin = true)
                    }
                }
            }
            ComicLinesKind.DRIP -> {
                // Damlayan çizgiler (tare-sen): üst kenardan aşağı, boyları farklı, uçları incelir
                val n = count(40, 8, 160)
                for (i in 0 until n) {
                    val x = l + (i + 0.5f + (rnd.nextFloat() - 0.5f) * 0.7f) / n * w
                    val ln = (h * (0.18f + rnd.nextFloat() * 0.6f) * len).coerceAtMost(h)
                    val m = 8
                    val xs = FloatArray(m); val ys = FloatArray(m); val ps = FloatArray(m)
                    val wob = w * 0.004f
                    for (k in 0 until m) {
                        val f = k / (m - 1f)
                        xs[k] = x + sin(f * 6f + i).toFloat() * wob; ys[k] = t + ln * f
                        ps[k] = 0.15f + 0.85f * (1f - f)
                    }
                    out += ComicLine(xs, ys, ps)
                }
            }
            ComicLinesKind.WAVER -> {
                // Titreme (yure-sen): merkezin iki yanında kısa yaylar
                val rings = count(3, 1, 8)
                for (center in listOf(PI, 0.0)) for (k in 0 until rings) {
                    // Halkalar sıklıkla artar ama alanın içinde kalır
                    val rr = if (rings <= 1) 0.72f else 0.62f + 0.34f * k / (rings - 1f)
                    val m = 10
                    val xs = FloatArray(m); val ys = FloatArray(m); val ps = FloatArray(m)
                    for (j in 0 until m) {
                        val a = center + (j / (m - 1.0) - 0.5) * 0.9
                        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                        xs[j] = cx + ca * w / 2f * rr; ys[j] = cy + sa * h / 2f * rr
                        val f = j / (m - 1f)
                        ps[j] = 0.2f + 0.8f * (1f - abs1(f - 0.5f) * 2f)
                    }
                    out += ComicLine(xs, ys, ps)
                }
            }
            ComicLinesKind.BLUSH -> {
                // Kızarma (tere-sen): iki yanak hizasında kısa çapraz taramalar
                val n = count(6, 2, 18)
                for (side in listOf(0.3f, 0.7f)) {
                    val gx = l + w * side; val gy = cy
                    val step = w * 0.22f / n
                    for (i in 0 until n) {
                        val x = gx - w * 0.11f + step * (i + 0.5f)
                        out += taper(x - w * 0.03f, gy + h * 0.06f, x + w * 0.03f, gy - h * 0.06f, startThin = true, end = 0.3f)
                    }
                }
            }
            ComicLinesKind.SWIRL -> {
                // Girdap (hayal kırıklığı): merkezden dışa sarmallar
                val arms = count(3, 1, 8)
                for (k in 0 until arms) {
                    val m = 48
                    val xs = FloatArray(m); val ys = FloatArray(m); val ps = FloatArray(m)
                    val phase = 2 * PI * k / arms
                    for (j in 0 until m) {
                        val f = j / (m - 1f)
                        val a = phase + f * 2.6 * PI
                        val rr = 0.08f + 0.85f * f
                        xs[j] = cx + cos(a).toFloat() * w / 2f * rr; ys[j] = cy + sin(a).toFloat() * h / 2f * rr
                        ps[j] = 0.2f + 0.8f * f
                    }
                    out += ComicLine(xs, ys, ps)
                }
            }
            ComicLinesKind.RINGS -> {
                // Şok halkaları: merkezin çevresinde kesik kesik eş merkezli yaylar
                val rings = count(4, 2, 10)
                for (k in 0 until rings) {
                    val rr = 0.3f + 0.65f * (k + 1) / rings
                    val segs = 5 + k
                    for (sgi in 0 until segs) {
                        val a0 = 2 * PI * (sgi + rnd.nextFloat() * 0.2f) / segs
                        val a1 = a0 + 2 * PI / segs * (0.55 + rnd.nextFloat() * 0.25)
                        val m = 10
                        val xs = FloatArray(m); val ys = FloatArray(m); val ps = FloatArray(m)
                        for (j in 0 until m) {
                            val a = a0 + (a1 - a0) * j / (m - 1.0)
                            xs[j] = cx + cos(a).toFloat() * w / 2f * rr; ys[j] = cy + sin(a).toFloat() * h / 2f * rr
                            val f = j / (m - 1f)
                            ps[j] = 0.2f + 0.8f * (1f - abs1(f - 0.5f) * 2f)
                        }
                        out += ComicLine(xs, ys, ps)
                    }
                }
            }
            ComicLinesKind.ZOOM -> {
                // Bir noktaya yakınlaştırma: kenarlardan odağa doğru, odağın çevresinde dar bir boşluk; uçlar odağa incelir
                val n = count(110, 20, 360)
                for (i in 0 until n) {
                    val a = 2 * PI * (i + rnd.nextFloat() * 0.7f) / n
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    val outer = edgeFrom(cx, cy, ca, sa, l, t, r, b)
                    val f = (0.12f + rnd.nextFloat() * 0.22f)
                    val reach = outer * (1f - (1f - f) * len.coerceAtMost(1f) * 0.98f)
                    out += taper(cx + ca * outer, cy + sa * outer, cx + ca * reach, cy + sa * reach, startThin = false, end = 0.04f)
                }
            }
            ComicLinesKind.PERSPECTIVE -> {
                // Derinlik: kaçış noktasından köşelere ve kenarlara ışınlar, kaçış noktasına doğru küçülen iç içe çerçeveler
                val rays = count(24, 8, 72)
                for (i in 0 until rays) {
                    val a = 2 * PI * (i + 0.5) / rays
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    val outer = edgeFrom(cx, cy, ca, sa, l, t, r, b)
                    val start = outer * (1f - 0.92f * len.coerceAtMost(1f))
                    out += taper(cx + ca * start, cy + sa * start, cx + ca * outer, cy + sa * outer, startThin = true, end = 0.08f)
                }
                val frames = count(5, 2, 12)
                for (k in 1..frames) {
                    val f = (k / (frames + 1f)).let { it * it }
                    val fl = cx + (l - cx) * f; val ft = cy + (t - cy) * f; val fr = cx + (r - cx) * f; val fb = cy + (b - cy) * f
                    val p = 0.25f + 0.75f * f
                    out += ComicLine(floatArrayOf(fl, fr, fr, fl, fl), floatArrayOf(ft, ft, fb, fb, ft), FloatArray(5) { p })
                }
            }
            ComicLinesKind.BEAMS -> {
                // Daralan/genişleyen ışınlar: odaktan [angle] yönünde yelpaze; uçta genişler (odakta incecik)
                val n = count(9, 3, 30)
                val spread = 70.0 * PI / 180.0
                val dir = angle * PI / 180.0
                for (i in 0 until n) {
                    val a = dir + spread * ((i + 0.5 + (rnd.nextFloat() - 0.5f) * 0.4) / n - 0.5)
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    val outer = edgeFrom(cx, cy, ca, sa, l, t, r, b) * len.coerceAtMost(1f)
                    if (outer < 2f) continue
                    out += taper(cx, cy, cx + ca * outer, cy + sa * outer, startThin = true, end = 0.03f)
                }
            }
            ComicLinesKind.MANGA_DENSE -> {
                // Yoğun siyah-beyaz manga (shuuchuu-sen): çok sık, kalın kökler; ortada pürüzlü kenarlı boşluk
                val n = count(240, 40, 720)
                for (i in 0 until n) {
                    val a = 2 * PI * (i + rnd.nextFloat()) / n
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    val outer = edge(ca, sa)
                    val (ix, iy) = innerAt(ca, sa, inner(0.46f + rnd.nextFloat() * 0.22f), outer)
                    out += taper(cx + ca * outer, cy + sa * outer, ix, iy, startThin = false, end = 0.02f)
                }
            }
            ComicLinesKind.ANIME_SOFT -> {
                // Yumuşak renkli anime: seyrek, geniş ve yarı saydam ışınlar; ortası geniş boş
                val n = count(56, 12, 180)
                for (i in 0 until n) {
                    val a = 2 * PI * (i + rnd.nextFloat() * 0.8f) / n
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    val outer = edge(ca, sa)
                    val (ix, iy) = innerAt(ca, sa, inner(0.55f + rnd.nextFloat() * 0.2f), outer)
                    out += taper(cx + ca * outer, cy + sa * outer, ix, iy, startThin = false, end = 0.1f)
                }
            }
            ComicLinesKind.KRACKLE -> {
                // Enerji çıtırtısı (Kirby krackle): odağın çevresinde kümelenen, boyları farklı küçük halkalar
                val n = count(46, 10, 160)
                val rMax = min(w, h) * 0.05f
                for (i in 0 until n) {
                    val a = rnd.nextDouble() * 2 * PI
                    val dist = (0.15f + rnd.nextFloat() * 0.8f) * len.coerceAtMost(1.2f)
                    val px = cx + cos(a).toFloat() * w / 2f * dist; val py = cy + sin(a).toFloat() * h / 2f * dist
                    val rr = rMax * (0.25f + rnd.nextFloat() * 0.75f) * (1.1f - dist * 0.5f).coerceAtLeast(0.3f)
                    circle(px, py, rr, l, t, r, b)?.let { out += it }
                }
            }
            ComicLinesKind.IMPACT -> {
                // Darbe yıldızı: odağın çevresinde sivri uçlu, düzensiz tek bir patlama kenarı ve içinde ikinci kenar
                val spikes = count(14, 7, 40)
                for (ring in 0 until 2) {
                    val m = spikes * 2 + 1
                    val xs = FloatArray(m); val ys = FloatArray(m); val ps = FloatArray(m) { 1f }
                    val k = if (ring == 0) 1f else 0.62f
                    for (j in 0 until m) {
                        val a = 2 * PI * j / (m - 1) + ring * 0.2
                        val tip = j % 2 == 0
                        val rr = (if (tip) 0.72f + rnd.nextFloat() * 0.26f else 0.36f + rnd.nextFloat() * 0.12f) * k * len.coerceAtMost(1.3f)
                        xs[j] = (cx + cos(a).toFloat() * w / 2f * rr).coerceIn(l, r); ys[j] = (cy + sin(a).toFloat() * h / 2f * rr).coerceIn(t, b)
                    }
                    xs[m - 1] = xs[0]; ys[m - 1] = ys[0]
                    out += ComicLine(xs, ys, ps)
                }
            }
            ComicLinesKind.SURPRISE -> {
                // Şaşkınlık çizgileri (emanata): odağın üstünde yelpaze gibi açılan kısa, kalın çizgiler
                val n = count(5, 3, 14)
                for (i in 0 until n) {
                    val a = -PI / 2 + (i / (n - 1.0).coerceAtLeast(1.0) - 0.5) * 1.6
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    val r0 = 0.42f; val r1 = r0 + 0.38f * len
                    out += taper(cx + ca * w / 2f * r0, cy + sa * h / 2f * r0, cx + ca * w / 2f * r1, cy + sa * h / 2f * r1, startThin = true, end = 0.35f)
                }
            }
            ComicLinesKind.ELECTRIC -> {
                // Elektrik: odaktan dışa doğru zikzak şimşekler (her biri 5–8 kırık)
                val n = count(7, 3, 24)
                for (i in 0 until n) {
                    val a = 2 * PI * (i + rnd.nextFloat() * 0.5f) / n
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    val outer = edgeFrom(cx, cy, ca, sa, l, t, r, b) * len.coerceAtMost(1f)
                    val segs = 5 + rnd.nextInt(4)
                    val m = segs + 1
                    val xs = FloatArray(m); val ys = FloatArray(m); val ps = FloatArray(m)
                    val r0 = outer * 0.18f
                    for (j in 0 until m) {
                        val f = j / segs.toFloat()
                        val d = r0 + (outer - r0) * f
                        val j0 = if (j == 0 || j == segs) 0f else (if (j % 2 == 0) 1f else -1f) * outer * (0.04f + rnd.nextFloat() * 0.05f)
                        xs[j] = (cx + ca * d - sa * j0).coerceIn(l, r); ys[j] = (cy + sa * d + ca * j0).coerceIn(t, b)
                        ps[j] = 1f - 0.85f * f
                    }
                    out += ComicLine(xs, ys, ps)
                }
            }
            ComicLinesKind.BENDAY -> {
                // Ben-Day nokta tramı: düzgün ızgarada küçük halkalar (çizgi romanların klasik baskı noktaları)
                val cols = count(12, 5, 32)
                val step = w / cols
                val rows = (h / step).toInt().coerceIn(1, 64)
                val rr = step * 0.3f * len.coerceIn(0.4f, 1.4f)
                for (ry in 0 until rows) for (rx in 0 until cols) {
                    val px = l + step * (rx + 0.5f + if (ry % 2 == 1) 0.5f else 0f)
                    val py = t + step * (ry + 0.5f)
                    if (px + rr > r || py + rr > b) continue
                    circle(px, py, rr, l, t, r, b, segments = 10)?.let { out += it }
                }
            }
            ComicLinesKind.GLOOM -> {
                // Kasvet (tate-sen): üst kenardan inen sık dikey çizgiler; uzunluk alanın yarısı kadar, uçları incelir
                val n = count(56, 12, 220)
                for (i in 0 until n) {
                    val x = l + (i + 0.5f + (rnd.nextFloat() - 0.5f) * 0.5f) / n * w
                    val ln = (h * (0.35f + rnd.nextFloat() * 0.3f) * len).coerceAtMost(h)
                    out += taper(x, t, x, t + ln, startThin = false, end = 0.05f)
                }
            }
            ComicLinesKind.SPARKLE -> {
                // Parıltı (kira-kira): dört köşeli yıldızlar (iki kesişen ince uçlu çizgi), boyları farklı
                val n = count(9, 3, 40)
                val base = min(w, h) * 0.09f * len.coerceIn(0.3f, 1.6f)
                for (i in 0 until n) {
                    val px = l + w * (0.08f + rnd.nextFloat() * 0.84f); val py = t + h * (0.08f + rnd.nextFloat() * 0.84f)
                    val s = base * (0.35f + rnd.nextFloat() * 0.65f)
                    val hx = s.coerceAtMost(minOf(px - l, r - px)); val vy = s.coerceAtMost(minOf(py - t, b - py))
                    out += star(px - hx, py, px + hx, py)
                    out += star(px, py - vy, px, py + vy)
                }
            }
            ComicLinesKind.CROSSHATCH -> {
                // Tarama (kake-ami): iki yönde çapraz paralel çizgiler, alanın içine kırpılır
                val n = count(22, 6, 90)
                val a = (45.0 + angle) * PI / 180.0
                hatch(out, a, l, t, r, b, n)
                hatch(out, a + PI / 2, l, t, r, b, n)
            }
            ComicLinesKind.SPEED_DENSE -> {
                // Yoğun hız (ryūsen-beta): çok sık yatay çizgiler, ortada boş bir şerit (karakterin geçtiği yer)
                val ang = angle * PI / 180.0
                val ux = cos(ang).toFloat(); val uy = sin(ang).toFloat()
                val px = -uy; val py = ux
                val span = hypot(w, h)
                val n = count(110, 20, 360)
                for (i in 0 until n) {
                    val off = ((i + 0.5f + (rnd.nextFloat() - 0.5f) * 0.6f) / n - 0.5f) * span
                    // Ortadaki şerit (odak yüksekliğinde) boş kalır
                    val center = (focusY - 0.5f) * h
                    if (kotlin.math.abs(off - center) < h * 0.12f * (0.6f + rnd.nextFloat() * 0.6f)) continue
                    val ln = (span * (0.35f + rnd.nextFloat() * 0.6f) * len).coerceAtMost(span)
                    val along = (rnd.nextFloat() - 0.5f) * (span - ln)
                    val mx = (l + r) / 2f; val my = (t + b) / 2f
                    val sx = mx + px * off + ux * (along - ln / 2f); val sy = my + py * off + uy * (along - ln / 2f)
                    clip(sx, sy, sx + ux * ln, sy + uy * ln, l, t, r, b)?.let { c -> out += taper(c[0], c[1], c[2], c[3], startThin = rnd.nextBoolean(), end = 0.05f) }
                }
            }
        }
        return out
    }

    /** v50 [x],[y] merkezli [rr] yarıçaplı halka (alanın dışına taşıyorsa null). */
    private fun circle(x: Float, y: Float, rr: Float, l: Float, t: Float, r: Float, b: Float, segments: Int = 14): ComicLine? {
        if (rr < 0.5f || x - rr < l || x + rr > r || y - rr < t || y + rr > b) return null
        val m = segments + 1
        val xs = FloatArray(m); val ys = FloatArray(m)
        for (j in 0 until m) {
            val a = 2 * PI * j / segments
            xs[j] = x + cos(a).toFloat() * rr; ys[j] = y + sin(a).toFloat() * rr
        }
        return ComicLine(xs, ys, FloatArray(m) { 0.8f })
    }

    /** v50 iki ucu ince, ortası kalın çizgi (parıltının kolu). */
    private fun star(x0: Float, y0: Float, x1: Float, y1: Float): ComicLine {
        val n = 9
        val xs = FloatArray(n); val ys = FloatArray(n); val ps = FloatArray(n)
        for (i in 0 until n) {
            val f = i / (n - 1f)
            xs[i] = x0 + (x1 - x0) * f; ys[i] = y0 + (y1 - y0) * f
            ps[i] = 0.05f + 0.95f * (1f - abs1(f - 0.5f) * 2f)
        }
        return ComicLine(xs, ys, ps)
    }

    /** v50 [rad] doğrultusunda eşit aralıklı paralel çizgiler (alanın içine kırpılır). */
    private fun hatch(out: MutableList<ComicLine>, rad: Double, l: Float, t: Float, r: Float, b: Float, n: Int) {
        val ux = cos(rad).toFloat(); val uy = sin(rad).toFloat()
        val px = -uy; val py = ux
        val cx = (l + r) / 2f; val cy = (t + b) / 2f
        val span = hypot(r - l, b - t)
        for (i in 0 until n) {
            val off = ((i + 0.5f) / n - 0.5f) * span
            val sx = cx + px * off - ux * span / 2f; val sy = cy + py * off - uy * span / 2f
            clip(sx, sy, sx + ux * span, sy + uy * span, l, t, r, b)?.let { c ->
                out += ComicLine(floatArrayOf(c[0], (c[0] + c[2]) / 2f, c[2]), floatArrayOf(c[1], (c[1] + c[3]) / 2f, c[3]), floatArrayOf(0.7f, 0.7f, 0.7f))
            }
        }
    }

    /** v36 açıyla ([rad]) paralel hız çizgileri, alanın içine kırpılır. */
    private fun parallel(out: MutableList<ComicLine>, rnd: Random, rad: Double, l: Float, t: Float, r: Float, b: Float, n: Int, len: Float) {
        val ux = cos(rad).toFloat(); val uy = sin(rad).toFloat()
        val px = -uy; val py = ux
        val cx = (l + r) / 2f; val cy = (t + b) / 2f
        val span = hypot(r - l, b - t)
        for (i in 0 until n) {
            val off = ((i + 0.5f + (rnd.nextFloat() - 0.5f) * 0.8f) / n - 0.5f) * span
            val ln = (span * (0.2f + rnd.nextFloat() * 0.45f) * len).coerceAtMost(span)
            val along = (rnd.nextFloat() - 0.5f) * (span - ln)
            val sx = cx + px * off + ux * (along - ln / 2f); val sy = cy + py * off + uy * (along - ln / 2f)
            clip(sx, sy, sx + ux * ln, sy + uy * ln, l, t, r, b)?.let { c -> out += taper(c[0], c[1], c[2], c[3], startThin = rnd.nextBoolean()) }
        }
    }

    /** ([cx],[cy]) noktasından ([ca],[sa]) yönünde dikdörtgenin kenarına uzaklık. */
    private fun edgeFrom(cx: Float, cy: Float, ca: Float, sa: Float, l: Float, t: Float, r: Float, b: Float): Float {
        val tx = when { ca > 1e-6f -> (r - cx) / ca; ca < -1e-6f -> (l - cx) / ca; else -> Float.POSITIVE_INFINITY }
        val ty = when { sa > 1e-6f -> (b - cy) / sa; sa < -1e-6f -> (t - cy) / sa; else -> Float.POSITIVE_INFINITY }
        return (minOf(tx, ty) * 0.999f).coerceAtLeast(0f)
    }

    private fun abs1(v: Float) = if (v < 0f) -v else v

    /** Doğru parçasını dikdörtgene kırpar (Liang–Barsky); dışarıdaysa null. */
    private fun clip(x0: Float, y0: Float, x1: Float, y1: Float, l: Float, t: Float, r: Float, b: Float): FloatArray? {
        var u0 = 0f; var u1 = 1f
        val dx = x1 - x0; val dy = y1 - y0
        val p = floatArrayOf(-dx, dx, -dy, dy)
        val q = floatArrayOf(x0 - l, r - x0, y0 - t, b - y0)
        for (i in 0 until 4) {
            if (p[i] == 0f) { if (q[i] < 0f) return null; continue }
            val u = q[i] / p[i]
            if (p[i] < 0f) { if (u > u1) return null; if (u > u0) u0 = u } else { if (u < u0) return null; if (u < u1) u1 = u }
        }
        if (u1 - u0 < 0.05f) return null
        return floatArrayOf(x0 + dx * u0, y0 + dy * u0, x0 + dx * u1, y0 + dy * u1)
    }

    /** İnce uçlu çizgi (basınç bir uçta 1, diğer uçta [end]). */
    private fun taper(x0: Float, y0: Float, x1: Float, y1: Float, startThin: Boolean, end: Float = 0.15f): ComicLine {
        val n = 8
        val xs = FloatArray(n); val ys = FloatArray(n); val ps = FloatArray(n)
        for (i in 0 until n) {
            val f = i / (n - 1f)
            xs[i] = x0 + (x1 - x0) * f; ys[i] = y0 + (y1 - y0) * f
            val k = if (startThin) f else 1f - f
            ps[i] = end + (1f - end) * k
        }
        return ComicLine(xs, ys, ps)
    }

    /** Çizgilerin kutusu (sol, üst, sağ, alt); boşsa null. */
    fun bounds(lines: List<ComicLine>): FloatArray? {
        if (lines.isEmpty()) return null
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        lines.forEach { ln -> for (i in ln.xs.indices) { l = min(l, ln.xs[i]); t = min(t, ln.ys[i]); r = max(r, ln.xs[i]); b = max(b, ln.ys[i]) } }
        return floatArrayOf(l, t, r, b)
    }

    /**
     * Grup taşındı/ölçeklendiyse üretim alanının yeni yeri: [bounds] üretildiği andaki çizgilerin kutusu, [now] şimdiki
     * kutusu; alan aynı ölçek ve ötelemeyle taşınır.
     */
    /** Merkezden ([ca], [sa]) yönünde [w]×[h] alanın kenarına uzaklık. */
    private fun edgeDistance(ca: Float, sa: Float, w: Float, h: Float): Float {
        val tx = if (kotlin.math.abs(ca) < 1e-6f) Float.POSITIVE_INFINITY else (w / 2f) / kotlin.math.abs(ca)
        val ty = if (kotlin.math.abs(sa) < 1e-6f) Float.POSITIVE_INFINITY else (h / 2f) / kotlin.math.abs(sa)
        return minOf(tx, ty) * 0.999f
    }

    fun movedArea(area: FloatArray, bounds: FloatArray, now: FloatArray): FloatArray {
        val bw = (bounds[2] - bounds[0]).coerceAtLeast(1e-3f); val bh = (bounds[3] - bounds[1]).coerceAtLeast(1e-3f)
        val sx = (now[2] - now[0]) / bw; val sy = (now[3] - now[1]) / bh
        fun mx(x: Float) = now[0] + (x - bounds[0]) * sx
        fun my(y: Float) = now[1] + (y - bounds[1]) * sy
        return floatArrayOf(mx(area[0]), my(area[1]), mx(area[2]), my(area[3]))
    }
}

/** Efekt yazısı (ses efekti) hazırı: yazı, yazı tipi adı, gradyan renkleri, kenarlık rengi ve eğim (derece). */
class SfxPreset(val text: String, val font: String, val colors: List<String>, val outline: String, val shadow: String, val tilt: Float)

object ComicSfx {
    val presets: List<SfxPreset> = listOf(
        SfxPreset("BAM!", "BANGERS", listOf("#FFEB3B", "#FF5722"), "#1A1A1A", "#000000", -8f),
        SfxPreset("POW!", "LUCKIEST_GUY", listOf("#FFFFFF", "#29B6F6"), "#0D47A1", "#000000", 6f),
        SfxPreset("BOOM!", "BUNGEE", listOf("#FFD54F", "#E53935"), "#3E2723", "#000000", -5f),
        SfxPreset("ZAP!", "BANGERS", listOf("#B2FF59", "#00C853"), "#1B5E20", "#000000", 10f),
        SfxPreset("ŞAK!", "LUCKIEST_GUY", listOf("#FFF176", "#FB8C00"), "#4E342E", "#000000", -10f),
        SfxPreset("VUUUŞ", "BANGERS", listOf("#E1F5FE", "#4FC3F7"), "#01579B", "#00000000", 0f),
        SfxPreset("KRAK!", "BUNGEE_SHADE", listOf("#FFFFFF", "#BDBDBD"), "#212121", "#000000", 4f),
        SfxPreset("HAHAHA", "RUBIK_BUBBLES", listOf("#F8BBD0", "#EC407A"), "#880E4F", "#000000", -4f),
        SfxPreset("AAAA!", "BANGERS", listOf("#FFFFFF", "#FF1744"), "#B71C1C", "#000000", 8f),
        SfxPreset("ZZZ…", "GRANDSTANDER", listOf("#E8EAF6", "#7986CB"), "#1A237E", "#00000000", -6f),
        SfxPreset("GÜM!", "BUNGEE", listOf("#FFCC80", "#EF6C00"), "#3E2723", "#000000", 7f),
        SfxPreset("TIK TAK", "BOOGALOO", listOf("#FFFFFF", "#90A4AE"), "#263238", "#00000000", 0f),
        // v36 Ses efektleri sekmesindeki eksikler
        SfxPreset("CRASH!", "BUNGEE", listOf("#FFF59D", "#FF7043"), "#3E2723", "#000000", -7f),
        SfxPreset("WHOOSH!", "BANGERS", listOf("#E0F7FA", "#26C6DA"), "#006064", "#00000000", 5f),
    )

    /** v36 Efektler penceresinin Ses bölümünde ilk sırada gösterilen yazılar (geri kalanlar ardından). */
    val featured: List<String> = listOf("POW!", "BAM!", "BOOM!", "CRASH!", "WHOOSH!", "ZAP!")
}
