package com.karapp.drawguidance.ui.screens.canvasedit.challenge

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.fill.RegionFill
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.components.MascotFace
import com.karapp.drawguidance.ui.components.MascotGesture
import com.karapp.drawguidance.ui.components.MascotLook
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.drawing.VectorEdit
import kotlin.math.abs
import kotlin.math.min

/*
 * v38 çocuk modunda çizim görevi ("Çizim Görevi"): seviye seviye, kolaydan zora görevler (v38: 15, v40: 50; ilk 15
 * seviyenin sırası ve kimlikleri değişmedi, eski puanlar aynı seviyelerde kalır). Hedef resimler uygulamanın
 * kendi özgün çocuk boyama şekillerinden (content/library/kids) türetilir: şeklin alanı en büyük kapalı parçalarının
 * dış hatları izlenecek çizgiler, seçilen parçaların renkleri boyanacak yerlerdir. İnternetten resim ya da telifli
 * karakter kullanılmaz.
 */

/**
 * Görevin türü: [TRACE] kesikli çizgilerin üstünden fırçayla çizilir; [PAINT] kâğıda konan şekil örnekteki renklerle
 * kovayla boyanır; [BUILD] şekil Şekiller düğmesinden bulunup kesikli çizginin üstüne konur ve boyanır.
 */
enum class ChallengeKind(@StringRes val label: Int, @StringRes val intro: Int) {
    TRACE(R.string.challenge_kind_trace, R.string.challenge_intro_trace),
    PAINT(R.string.challenge_kind_paint, R.string.challenge_intro_paint),
    BUILD(R.string.challenge_kind_build, R.string.challenge_intro_build),
}

/** Maskotun seviyede ipucu verdiği araçlar (ipucu cümlesi ve o cümledeki yüz ifadesi, el hareketi). */
enum class ChallengeTool(@StringRes val tip: Int, val look: MascotLook) {
    // v45 çizim aracının çocuk menüsündeki adı "Kalem"
    BRUSH(R.string.v45_challenge_tip_pen, MascotLook(MascotFace.HAPPY, MascotGesture.BRUSH)),
    ERASER(R.string.challenge_tip_eraser, MascotLook(MascotFace.THINKING, MascotGesture.THINK)),
    UNDO(R.string.challenge_tip_undo, MascotLook(MascotFace.WINKING, MascotGesture.POINT)),
    PAINT(R.string.challenge_tip_paint, MascotLook(MascotFace.EXCITED, MascotGesture.THUMBS_UP)),
    HOLD(R.string.challenge_tip_hold, MascotLook(MascotFace.SURPRISED, MascotGesture.CHEER)),
    SHAPE(R.string.challenge_tip_shape, MascotLook(MascotFace.EXPLAINING, MascotGesture.POINT)),
    MOVE(R.string.challenge_tip_move, MascotLook(MascotFace.THINKING, MascotGesture.WAVE)),
    /** v45 boya fırçası: boya çizgilerin dışına taşmaz. */
    PAINT_BRUSH(R.string.v45_challenge_tip_paint_brush, MascotLook(MascotFace.EXCITED, MascotGesture.BRUSH)),
    ;

    companion object {
        /**
         * v45 maskotun sırayla verdiği ipuçları: seviyenin araçları, kovanın ipucundan hemen sonra boya fırçasınınki
         * (boyama görevleri kovayla da fırçayla da yapılır).
         */
        fun tips(level: ChallengeLevel): List<ChallengeTool> = level.tools.flatMap { if (it == PAINT) listOf(PAINT, PAINT_BRUSH) else listOf(it) }
    }
}

/**
 * Bir seviye: [shape] çocuk şeklinin kimliği ("lib.kid_…"), [ranks] izlenecek parçalar (alanı en büyükten küçüğe
 * sıraları), [paints] boyanacak parçalar (sıra → çocuk paletinin rengi), [tools] maskotun sırayla ipucu verdiği
 * araçlar, [size] hedefin kâğıttaki boyu (kâğıdın kısa kenarına oranı). [id] ilerlemenin kayıt anahtarıdır.
 */
class ChallengeLevel(
    val number: Int,
    val shape: String,
    val kind: ChallengeKind,
    val ranks: List<Int>,
    val paints: List<Pair<Int, String>> = emptyList(),
    val tools: List<ChallengeTool>,
    val size: Float = 0.6f,
) {
    val id: String = shape.removePrefix("lib.kid_") + "." + kind.name.lowercase()

    /** Şeklin Türkçe adı (arayüzde I18n.tr ile çevrilir). */
    val title: String get() = ShapeRegistry.title(shape) ?: shape
}

/** Boyanacak yer: şeklin çizgi kutusunda oran ([u], [v]) ve beklenen renk. */
class PaintProbe(val u: Float, val v: Float, val color: String)

/**
 * Seviyenin kâğıttaki hedefi: yeri ([box]), izlenecek çizgiler (tuval koordinatı; kapalı parçalar ilk noktayla kapanır),
 * boyanacak yerler ve hedef şeklin çizgi kutusu ([outline]; boyanacak yerler kâğıtta buna göre gösterilir).
 */
class ChallengeTarget(
    val level: ChallengeLevel,
    val box: Rect,
    val lines: List<List<Offset>>,
    val probes: List<PaintProbe>,
    val outline: Rect? = null,
) {
    /** Boyanacak yerlerin hedef şekildeki konumları (şekil henüz konmadıysa gösterilir). */
    fun spots(): List<Pair<Offset, String>> {
        val b = outline ?: return emptyList()
        return probes.map { Offset(b.left + it.u * b.width, b.top + it.v * b.height) to it.color }
    }
}

/** v38 çizim görevinin seviyeleri (kolaydan zora). */
object ChallengeLevels {
    // Çocuk paletinin renkleri (bkz. components.KidsColors)
    private const val RED = "#E53935"
    private const val ORANGE = "#FB8C00"
    private const val YELLOW = "#FDD835"
    private const val LIGHT_GREEN = "#7CB342"
    private const val TURQUOISE = "#00ACC1"
    private const val BLUE = "#1E88E5"
    private const val PURPLE = "#5E35B1"
    private const val PINK = "#EC407A"
    private const val BROWN = "#8D6E63"
    // v40 çocuk paletinin öteki iki rengi
    private const val GREEN = "#2E7D32"
    private const val BLACK = "#212121"

    /** Yerleştir ve boya görevinde hedefin boyu: Şekiller'den eklenen şeklin ilk boyu (kısa kenarın %44'ü). */
    private const val BUILD_SIZE = 0.44f

    private val T = ChallengeKind.TRACE
    private val P = ChallengeKind.PAINT
    private val B = ChallengeKind.BUILD
    private val brush = ChallengeTool.BRUSH
    private val eraser = ChallengeTool.ERASER
    private val undo = ChallengeTool.UNDO
    private val paint = ChallengeTool.PAINT
    private val hold = ChallengeTool.HOLD
    private val shapes = ChallengeTool.SHAPE
    private val move = ChallengeTool.MOVE

    val all: List<ChallengeLevel> = listOf(
        // v47 6–9 yaş: her çizme görevinde iki parça daha (güneşte yüz, yanak ve ışın), yerleştirmede bir parça daha
        ChallengeLevel(1, "lib.kid_sun", T, listOf(0, 1, 2), tools = listOf(brush, undo), size = 0.5f),
        ChallengeLevel(2, "lib.kid_apple", T, listOf(0, 1, 3, 2), tools = listOf(brush, eraser)),
        // Duvar sarı, çatı kırmızı, kapı kahverengi
        ChallengeLevel(3, "lib.kid_house", P, emptyList(), listOf(0 to YELLOW, 1 to RED, 2 to BROWN), listOf(paint, undo)),
        ChallengeLevel(4, "lib.kid_kite", T, listOf(0, 1, 2, 3, 4, 5), tools = listOf(brush, undo)),
        // Ortadaki kırmızı, sağdaki mavi, soldaki sarı balon
        ChallengeLevel(5, "lib.kid_balloons", P, emptyList(), listOf(0 to RED, 1 to BLUE, 2 to YELLOW), listOf(paint, undo)),
        ChallengeLevel(6, "lib.kid_ice_cream", T, listOf(0, 1, 2, 3), tools = listOf(brush, eraser, undo)),
        // Ortası sarı, yapraklar yeşil, taç yaprakları pembe
        ChallengeLevel(
            7, "lib.kid_flower", B, listOf(0, 1, 2, 3, 4, 5, 6, 7, 8),
            listOf(0 to YELLOW, 1 to LIGHT_GREEN, 2 to LIGHT_GREEN, 3 to PINK, 4 to PINK, 5 to PINK, 6 to PINK, 7 to PINK),
            listOf(shapes, move, hold, paint), size = BUILD_SIZE,
        ),
        ChallengeLevel(8, "lib.kid_fish", T, listOf(0, 1, 2, 3, 4, 5), tools = listOf(brush, eraser, undo)),
        // Şapka kırmızı, çimen yeşil, küçük mantar turuncu
        ChallengeLevel(9, "lib.kid_mushroom", P, emptyList(), listOf(0 to RED, 1 to LIGHT_GREEN, 3 to ORANGE), listOf(paint, undo)),
        ChallengeLevel(10, "lib.kid_umbrella", T, listOf(0, 1, 2, 3, 4, 5, 6), tools = listOf(brush, eraser, undo)),
        // Gövde kırmızı, üst kısım turkuaz
        ChallengeLevel(11, "lib.kid_car", B, listOf(0, 1, 2, 3, 4), listOf(0 to RED, 1 to TURQUOISE), listOf(shapes, move, paint), BUILD_SIZE),
        ChallengeLevel(12, "lib.kid_cat", T, listOf(0, 1, 2, 3, 4, 5, 6, 7), tools = listOf(brush, eraser, undo), size = 0.64f),
        // Gövde kırmızı, burun mavi, kanatlar turuncu
        ChallengeLevel(13, "lib.kid_rocket", P, emptyList(), listOf(0 to RED, 1 to BLUE, 2 to ORANGE, 3 to ORANGE), listOf(paint, hold, undo)),
        ChallengeLevel(14, "lib.kid_butterfly", T, listOf(0, 1, 2, 3, 4, 5, 6), tools = listOf(brush, eraser, undo), size = 0.64f),
        // Duvar sarı, kuleler mor, kule çatıları kırmızı
        ChallengeLevel(
            15, "lib.kid_castle", B, listOf(0, 1, 2, 3, 4, 5, 6),
            listOf(0 to YELLOW, 1 to PURPLE, 2 to PURPLE, 4 to RED, 5 to RED), listOf(shapes, move, hold, paint), BUILD_SIZE,
        ),
        // v40 16–35: çizgi sayısı 3'ten 7'ye, boyanacak parça 3'ten 7'ye, renk 3'ten 6'ya çıkar (Free)
        ChallengeLevel(16, "lib.kid_banana", T, listOf(0, 1, 2), tools = listOf(brush, eraser, undo)),
        // Çilek kırmızı, yapraklar açık yeşil, sap kahverengi
        ChallengeLevel(17, "lib.kid_strawberry", P, emptyList(), listOf(0 to RED, 1 to LIGHT_GREEN, 2 to BROWN), listOf(paint, undo)),
        ChallengeLevel(18, "lib.kid_igloo", T, listOf(0, 1, 2, 3, 4), tools = listOf(brush, eraser, undo)),
        // Meyve kırmızı, kabuk yeşil, iç kabuk açık yeşil
        ChallengeLevel(19, "lib.kid_watermelon", P, emptyList(), listOf(0 to RED, 1 to GREEN, 2 to LIGHT_GREEN), listOf(paint, hold, undo)),
        // Dört dilim dört renk
        ChallengeLevel(
            20, "lib.kid_ball", B, listOf(0, 1, 2, 3, 4),
            listOf(0 to RED, 1 to BLUE, 2 to YELLOW, 3 to LIGHT_GREEN), listOf(shapes, move, paint), BUILD_SIZE,
        ),
        ChallengeLevel(21, "lib.kid_whale", T, listOf(0, 1, 2, 3, 4, 5), tools = listOf(brush, eraser, undo), size = 0.64f),
        // Kâğıt kalıp turkuaz, krema pembe-sarı-pembe, kiraz kırmızı
        ChallengeLevel(
            22, "lib.kid_cupcake", P, emptyList(),
            listOf(0 to TURQUOISE, 1 to PINK, 2 to YELLOW, 3 to PINK, 4 to RED), listOf(paint, hold, undo),
        ),
        ChallengeLevel(23, "lib.kid_ufo", T, listOf(0, 1, 2, 3, 4, 5, 6), tools = listOf(brush, eraser, undo), size = 0.64f),
        // Kanatlar kırmızı, baş ve benekler siyah
        ChallengeLevel(
            24, "lib.kid_ladybug", P, emptyList(),
            listOf(0 to RED, 1 to RED, 2 to BLACK, 3 to BLACK, 4 to BLACK), listOf(paint, undo),
        ),
        // Deniz mavi, kıtalar yeşil ve açık yeşil
        ChallengeLevel(
            25, "lib.kid_earth", B, listOf(0, 1, 2, 3, 4),
            listOf(0 to BLUE, 1 to GREEN, 2 to GREEN, 3 to LIGHT_GREEN), listOf(shapes, move, hold, paint), BUILD_SIZE,
        ),
        ChallengeLevel(26, "lib.kid_jellyfish", T, listOf(0, 1, 2, 3, 4, 5, 6, 7), tools = listOf(brush, eraser, undo), size = 0.64f),
        // Taç sarı, şerit turuncu, taşlar kırmızı ve mavi
        ChallengeLevel(
            27, "lib.kid_crown", P, emptyList(),
            listOf(0 to YELLOW, 1 to ORANGE, 2 to RED, 3 to BLUE, 4 to BLUE), listOf(paint, hold, undo),
        ),
        ChallengeLevel(28, "lib.kid_owl", T, listOf(0, 1, 2, 3, 4, 5, 6, 7), tools = listOf(brush, eraser, undo), size = 0.64f),
        // Deniz mavi, gövde kahverengi, yelkenler sarı ve kırmızı
        ChallengeLevel(
            29, "lib.kid_sailboat", B, listOf(0, 1, 2, 3, 4, 5),
            listOf(0 to BLUE, 1 to BROWN, 2 to YELLOW, 3 to RED), listOf(shapes, move, hold, paint), BUILD_SIZE,
        ),
        ChallengeLevel(30, "lib.kid_frog_prince", T, listOf(0, 1, 2, 3, 4, 5, 6, 7), tools = listOf(brush, eraser, undo), size = 0.66f),
        // Yele turuncu, yüz sarı, burun kahverengi, kulak içleri turuncu
        ChallengeLevel(
            31, "lib.kid_lion", P, emptyList(),
            listOf(0 to ORANGE, 1 to YELLOW, 2 to BROWN, 3 to ORANGE, 4 to ORANGE), listOf(paint, hold, undo),
        ),
        // Yapraklar yeşil, çimen açık yeşil, gövde kahverengi, elmalar kırmızı
        ChallengeLevel(
            32, "lib.kid_tree", P, emptyList(),
            listOf(0 to GREEN, 1 to LIGHT_GREEN, 2 to BROWN, 3 to RED, 4 to RED, 5 to RED, 6 to RED), listOf(paint, hold, undo),
        ),
        ChallengeLevel(33, "lib.kid_teddy", T, listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), tools = listOf(brush, eraser, undo), size = 0.66f),
        // Gezegen turuncu, halka mor, küçük ay turkuaz, yıldız sarı
        ChallengeLevel(
            34, "lib.kid_planet", P, emptyList(),
            listOf(0 to ORANGE, 1 to PURPLE, 2 to ORANGE, 3 to TURQUOISE, 4 to PURPLE, 5 to YELLOW), listOf(paint, hold, undo),
        ),
        // Gökkuşağının beş bandı ve iki bulut
        ChallengeLevel(
            35, "lib.kid_rainbow", B, listOf(0, 1, 2, 3, 4, 5, 6),
            listOf(0 to RED, 1 to ORANGE, 4 to YELLOW, 5 to LIGHT_GREEN, 6 to BLUE, 2 to TURQUOISE, 3 to TURQUOISE),
            listOf(shapes, move, hold, paint), BUILD_SIZE,
        ),
        // v40 36–50 (Premium): çizgi sayısı 7'den 11'e, boyanacak parça 5'ten 11'e, renk 7'ye kadar
        ChallengeLevel(36, "lib.kid_elephant", T, listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), tools = listOf(brush, eraser, undo), size = 0.66f),
        // Baş mor, kollar pembe, turuncu ve turkuaz
        ChallengeLevel(
            37, "lib.kid_octopus", P, emptyList(),
            listOf(0 to PURPLE, 1 to PINK, 2 to PINK, 3 to ORANGE, 4 to ORANGE, 5 to TURQUOISE, 6 to TURQUOISE), listOf(paint, hold, undo),
        ),
        ChallengeLevel(38, "lib.kid_train", T, listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), tools = listOf(brush, eraser, undo), size = 0.68f),
        // Sandık kahverengi, kapak turuncu, kuşaklar sarı, kilit kırmızı
        ChallengeLevel(
            39, "lib.kid_treasure_chest", B, listOf(0, 1, 2, 3, 4, 5, 6, 7),
            listOf(0 to BROWN, 1 to ORANGE, 2 to YELLOW, 3 to YELLOW, 4 to RED), listOf(shapes, move, hold, paint), BUILD_SIZE,
        ),
        // Tavşan kahverengi, çimen açık yeşil, kulak içleri pembe, burun kırmızı
        ChallengeLevel(
            40, "lib.kid_rabbit", P, emptyList(),
            listOf(0 to BROWN, 1 to LIGHT_GREEN, 2 to BROWN, 3 to BROWN, 4 to PINK, 5 to PINK, 8 to RED), listOf(paint, hold, undo),
        ),
        ChallengeLevel(41, "lib.kid_robot", T, listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), tools = listOf(brush, eraser, undo), size = 0.68f),
        // Gövde ve kuyruk kırmızı, cam turkuaz, pervane ve ayaklar siyah, kuyruk kanadı turuncu
        ChallengeLevel(
            42, "lib.kid_helicopter", P, emptyList(),
            listOf(0 to RED, 1 to TURQUOISE, 2 to BLACK, 3 to RED, 4 to BLACK, 5 to ORANGE, 6 to ORANGE), listOf(paint, hold, undo),
        ),
        ChallengeLevel(43, "lib.kid_astronaut", T, listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), tools = listOf(brush, eraser, undo), size = 0.68f),
        // Gövde kahverengi, yelken sarı, deniz mavi, işaret siyah, bayrak kırmızı
        ChallengeLevel(
            44, "lib.kid_pirate_ship", B, listOf(0, 1, 2, 3, 4, 5, 6),
            listOf(0 to BROWN, 1 to YELLOW, 2 to BLUE, 3 to BLACK, 4 to RED, 5 to BROWN), listOf(shapes, move, hold, paint), BUILD_SIZE,
        ),
        ChallengeLevel(45, "lib.kid_bus", T, listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), tools = listOf(brush, eraser, undo), size = 0.7f),
        // Gövde pembe, yele ve kuyruk mor, mavi ve sarı
        ChallengeLevel(
            46, "lib.kid_unicorn", P, emptyList(),
            listOf(0 to PINK, 1 to PINK, 2 to PURPLE, 3 to BLUE, 4 to YELLOW, 5 to PINK, 6 to PINK, 7 to PINK), listOf(paint, hold, undo),
        ),
        ChallengeLevel(47, "lib.kid_dino", T, listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), tools = listOf(brush, eraser, undo), size = 0.7f),
        // Duvar kahverengi, çatı pembe, kapı kırmızı, şekerler sarı ve turuncu, pencereler turkuaz
        ChallengeLevel(
            48, "lib.kid_candy_house", B, listOf(0, 1, 2, 3, 4, 5, 6, 7),
            listOf(0 to BROWN, 1 to PINK, 2 to RED, 3 to YELLOW, 4 to TURQUOISE, 5 to TURQUOISE, 6 to ORANGE),
            listOf(shapes, move, hold, paint), BUILD_SIZE,
        ),
        ChallengeLevel(49, "lib.kid_knight", T, listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), tools = listOf(brush, eraser, undo), size = 0.7f),
        // Son seviye: şatonun bütün parçaları yedi renkle
        ChallengeLevel(
            50, "lib.kid_castle", P, emptyList(),
            listOf(
                0 to YELLOW, 1 to PURPLE, 2 to PURPLE, 3 to BROWN, 4 to RED, 5 to RED, 6 to BLUE, 7 to BLUE, 8 to TURQUOISE,
                9 to PINK, 10 to PINK,
            ),
            listOf(paint, hold, undo),
        ),
    )

    val count: Int get() = all.size

    /** v40 Free planda açık seviyeler (1–35); sonrakiler (36–50) Premium ([com.karapp.drawguidance.data.billing.FeatureKeys.KIDS_CHALLENGE_ADVANCED]). */
    const val FREE_LEVELS = 35

    /** v40 seviye (sıra 0'dan) Premium mu. */
    fun isPremium(index: Int): Boolean = index >= FREE_LEVELS

    /** Hedefin ve kâğıda konan şeklin kenar biçimi (çocuk eğitimiyle aynı). */
    val inkStyle = ShapeStyle(strokeColorHex = "#2A2A33", strokeWidth = 4f)

    /** Seviyenin kâğıttaki yeri: kâğıdın ortasında, şeklin en/boy oranıyla. */
    fun box(level: ChallengeLevel, paperW: Int, paperH: Int): Rect {
        val aspect = ShapeRegistry.aspect(level.shape)
        val side = min(paperW, paperH) * level.size
        val w = if (aspect >= 1f) side else side * aspect
        val h = if (aspect >= 1f) side / aspect else side
        val cx = paperW / 2f
        val cy = paperH / 2f
        return Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    /** Şekil [box] kutusuna kurulur ([fills]: alt yol sırası → dolgu rengi). */
    fun build(level: ChallengeLevel, box: Rect, id: String, order: Int = 0, style: ShapeStyle = inkStyle, fills: Map<Int, String> = emptyMap()): Stroke? {
        val stroke = runCatching { ShapeFactory.build(level.shape, box.topLeft, box.bottomRight, style, id = id, order = order) }.getOrNull() ?: return null
        if (fills.isEmpty()) return stroke
        val paths = ShapeFactory.subPaths(stroke).mapIndexed { i, p -> fills[i]?.let { p.copy(fillColorHex = it) } ?: p }
        return ShapeFactory.fromEditable(stroke, paths)?.copy(layerId = null)
    }

    /** Seviyenin kâğıttaki hedefi. */
    fun target(level: ChallengeLevel, paperW: Int, paperH: Int): ChallengeTarget {
        val box = box(level, paperW, paperH)
        val info = info(level)
        val stroke = build(level, box, "challenge-target") ?: return ChallengeTarget(level, box, emptyList(), info.probes)
        val paths = ShapeFactory.subPaths(stroke)
        val lines = info.lineIndices.mapNotNull { paths.getOrNull(it) }.map { p -> if (p.closed) p.points + p.points.first() else p.points }
        return ChallengeTarget(level, box, lines, info.probes, outlineBounds(paths))
    }

    /** Örnek resimde boyanacak parçaların renkleri (alt yol sırası → renk). */
    fun fills(level: ChallengeLevel): Map<Int, String> = info(level).fills

    /**
     * Şeklin bir kez hesaplanan bilgisi (sabit bir kutuda kurulur; alt yolların sırası boyuttan bağımsızdır): izlenecek
     * alt yollar, boyanacak alt yolların renkleri ve boyanacak yerler.
     */
    private class Info(val lineIndices: List<Int>, val fills: Map<Int, String>, val probes: List<PaintProbe>)

    private val infos = HashMap<String, Info>()

    private fun info(level: ChallengeLevel): Info = synchronized(infos) {
        infos.getOrPut(level.id) {
            val box = box(level, CANON, CANON)
            val stroke = build(level, box, "challenge-canon")
            val paths = stroke?.let { ShapeFactory.subPaths(it) }.orEmpty()
            val regions = regions(paths)
            val bounds = outlineBounds(paths)
            val lineIndices = (if (level.kind == ChallengeKind.PAINT) emptyList() else level.ranks).mapNotNull { regions.getOrNull(it) }
            val fills = LinkedHashMap<Int, String>()
            val probes = ArrayList<PaintProbe>()
            for ((rank, color) in level.paints) {
                val index = regions.getOrNull(rank) ?: continue
                fills[index] = color
                val at = probe(paths, index) ?: continue
                if (bounds != null) probes += PaintProbe((at.x - bounds.left) / bounds.width, (at.y - bounds.top) / bounds.height, color)
            }
            Info(lineIndices, fills, probes)
        }
    }

    /** v47 çocuk çizimlerindeki desen parçalarının etiketi (content.library.kids.KidDetail.TAG). */
    const val KID_DETAIL_TAG = "kid_detail"

    /** Hesaplamaların sabit kâğıt boyu. */
    private const val CANON = 1000

    /** Boyanabilir kapalı parçaların alt yol sıraları, alanı en büyükten küçüğe. */
    fun regions(paths: List<ShapeFactory.EditablePath>): List<Int> =
        paths.indices.filter { i ->
            val p = paths[i]
            // v47 çizimlerin desen parçaları ("kid_detail") sayılmaz: görevlerin parça sıraları değişmez
            p.closed && p.points.size >= 3 && p.label == null && !com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush.isPaintLayer(p.tag) &&
                p.tag != KID_DETAIL_TAG && (p.widthScale ?: 1f) > 0f
        }.sortedByDescending { area(paths[it].points) }

    /** Şeklin çizgilerinin kutusu (boyanmış alanlar ve yazılar hariç): boyanacak yerler buna göre bulunur. */
    fun outlineBounds(paths: List<ShapeFactory.EditablePath>): Rect? {
        val pts = paths.filter { it.label == null && !com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush.isPaintLayer(it.tag) && it.points.size >= 2 }.flatMap { it.points }
        if (pts.isEmpty()) return null
        val r = Rect(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
        return if (r.width < 1e-3f || r.height < 1e-3f) null else r
    }

    /**
     * Parçanın içinde, üstündeki parçaların dışında ve bütün çizgilerden en uzak nokta (kova o yere dokununca bu parça
     * boyanır): parçanın kutusunda bir ızgara taranır.
     */
    private fun probe(paths: List<ShapeFactory.EditablePath>, index: Int): Offset? {
        val region = paths[index].points.map { P(it.x, it.y) }
        val above = (index + 1 until paths.size).map { paths[it] }
            .filter { it.closed && it.points.size >= 3 && it.label == null }
            .map { p -> p.points.map { P(it.x, it.y) } }
        val edges = paths.filter { it.label == null && it.points.size >= 2 }
        val minX = region.minOf { it.x }; val maxX = region.maxOf { it.x }
        val minY = region.minOf { it.y }; val maxY = region.maxOf { it.y }
        var best: Offset? = null
        var bestD = 0f
        for (i in 1 until GRID) for (j in 1 until GRID) {
            val q = P(minX + (maxX - minX) * i / GRID, minY + (maxY - minY) * j / GRID)
            if (!RegionFill.contains(region, q) || above.any { RegionFill.contains(it, q) }) continue
            val o = Offset(q.x, q.y)
            var d = Float.MAX_VALUE
            for (e in edges) {
                d = min(d, ChallengeScore.distanceToPolyline(o, e.points, e.closed))
                if (d <= bestD) break
            }
            if (d > bestD) { bestD = d; best = o }
        }
        return best
    }

    private const val GRID = 40

    private fun area(p: List<Offset>): Float {
        var a = 0f
        for (i in p.indices) { val q = p[(i + 1) % p.size]; a += p[i].x * q.y - q.x * p[i].y }
        return abs(a) / 2f
    }
}
