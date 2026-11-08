package com.karapp.drawguidance.content.graph

import kotlin.math.PI

/** Grafik üreticisinin çizebildiği türler. */
enum class GraphKind { AXES, COORD_PLANE, NUMBER_LINE, UNIT_CIRCLE, AXES_3D, FUNCTION, BAR, LINE, PIE, SCATTER, HISTOGRAM }

/**
 * Bir grafik elemanının tüm ayarları (kayıtta saklanır; çizim her seferinde bundan yeniden üretilir).
 *
 * Türlere göre anlamlar:
 * - [repeat]: FUNCTION'da periyodik ön ayar için periyot sayısı, diğerlerinde x aralığının birim sayısı (v18'de FUNCTION'da
 *   "desen" [freq]'i değiştirir; [repeat] x aralığını belirlemeye devam eder);
 *   AXES/COORD_PLANE'de her aralığın alt bölme sayısı (ara ızgara / ara çentik, 1 = yok); NUMBER_LINE'da her yandaki
 *   birim aralık sayısı; UNIT_CIRCLE'da özel açıları işaretlenen çeyrek sayısı (1..4); AXES_3D'de yüzey aralığı;
 *   grafiklerde (BAR/LINE/PIE/HISTOGRAM) [values] boşken öğe sayısı = repeat + 3 (SCATTER'da nokta sayısı 2×).
 * - [ticks]: yarım eksendeki en fazla numara sayısı (FUNCTION, AXES, COORD_PLANE, AXES_3D; düzlemde kısa eksen);
 *   grafiklerde değer eksenindeki toplam çentik; NUMBER_LINE'da alt bölme (≤5 yalnız tamsayı, 6→½, 7→⅓, 8→¼,
 *   9→⅕, 10→⅙, 11→⅛, 12→1/10); UNIT_CIRCLE'da açı kümesi (≤2 yalnız eksenler, 3 → 45°, 4 → 30°/60°, ≥5 hepsi,
 *   ≥6 dik izdüşümler).
 * - [tickLabels] anahtarları: "x:3", "x:-2", "y:1", "z:2" (0'dan itibaren kaçıncı çentik), NUMBER_LINE'da ara
 *   çentikler kesirle ("x:1/2"), UNIT_CIRCLE'da açı yazıları "a:30" ve koordinat yazıları "c:30", ayrıca
 *   "formula" (formül kutusu). Değer "" ise o yazı gizlenir.
 */
data class GraphParams(
    val kind: GraphKind,
    val preset: String = "",
    val formula: String = "",
    val xLabel: String = "x", val yLabel: String = "y", val zLabel: String = "z",
    val repeat: Int = 2,
    val ticks: Int = 5,
    val tickStep: Float = 0f,
    val showNumbers: Boolean = true,
    val grid: Boolean = true,
    val tickLabels: Map<String, String> = emptyMap(),
    val values: List<Float> = emptyList(),
    val valueLabels: Map<Int, String> = emptyMap(),
    val curveColor: String = "#1E88E5",
    val fillUnder: Boolean = false,
    val keyPoints: Boolean = true,
    val xMin: Float = Float.NaN, val xMax: Float = Float.NaN,
    val yMin: Float = Float.NaN, val yMax: Float = Float.NaN,
    /**
     * v18 FUNCTION "desen": eğrinin yatay sıklığı k — eksenler aynı kalır, y = f(k·x) çizilir ve formül buna göre
     * yazılır (sin(x) → sin(2x), x² → (2x)²).
     */
    val freq: Int = 1,
    /** v18 kağıt (tuval arka planı) rengi: formül kutusu, nokta içleri ve ızgara buna uyar. */
    val paper: String = "#FFFFFF",
    /** v45: trigonometrik fonksiyonlar derece cinsinden (sin(30) = 1/2); varsayılan radyan. */
    val angleDeg: Boolean = false,
)

/** Hazır eğri. [formula] y = f(x) gövdesi ya da kapalı denklem ("x^2 + y^2 = 9"). [id] kayıtta saklanır. */
data class CurvePreset(
    val id: String,
    val title: String,
    val formula: String,
    val periodic: Boolean,
    val period: Double = 0.0,
    val defaultRepeat: Int = 2,
)

/** Ön ayarın x aralığı biçimi. */
internal enum class XStart { SYMMETRIC, FROM_ZERO, ONE_PERIOD, ZERO }

/** Ön ayarın görünüm ipuçları (API'de değil). */
internal data class PresetHint(
    /** Formül kutusunda gösterilen güzel yazım. */
    val label: String = "",
    val center: Double = 0.0,
    /** Desen başına x birimi (periyodik olmayanlar). */
    val unit: Double = 1.0,
    val start: XStart = XStart.SYMMETRIC,
    /** y görünümü ±yLimit (NaN = otomatik). */
    val yLimit: Double = Double.NaN,
    /** Eşit ölçek zorunlu. */
    val equal: Boolean = false,
    /** Ek süsleme: "circle:3" (merkez + yarıçap). */
    val decorate: String = "",
)

object CurvePresets {

    private class Entry(val preset: CurvePreset, val hint: PresetHint)

    private val entries: List<Entry> = listOf(
        Entry(CurvePreset("dogrusal", "Doğrusal", "2x + 1", false, defaultRepeat = 3), PresetHint("y = 2x + 1")),
        Entry(CurvePreset("parabol", "Parabol", "x^2", false, defaultRepeat = 3), PresetHint("y = x²")),
        Entry(
            CurvePreset("parabol-tepe", "Parabol (tepe noktası)", "x^2 - 4x + 3", false, defaultRepeat = 3),
            PresetHint("y = x² − 4x + 3", center = 2.0),
        ),
        Entry(CurvePreset("kubik", "Kübik", "x^3 - 3x", false, defaultRepeat = 3), PresetHint("y = x³ − 3x", unit = 0.8)),
        Entry(
            CurvePreset("sinus", "Sinüs", "sin(x)", true, 2 * PI, 2),
            PresetHint("y = sin(x)", start = XStart.ONE_PERIOD),
        ),
        Entry(
            CurvePreset("kosinus", "Kosinüs", "cos(x)", true, 2 * PI, 2),
            PresetHint("y = cos(x)", start = XStart.ONE_PERIOD),
        ),
        Entry(CurvePreset("tanjant", "Tanjant", "tan(x)", true, PI, 3), PresetHint("y = tan(x)", yLimit = 4.0)),
        Entry(CurvePreset("ustel", "Üstel", "2^x", false, defaultRepeat = 3), PresetHint("y = 2ˣ")),
        Entry(
            CurvePreset("logaritma", "Logaritma", "ln(x)", false, defaultRepeat = 3),
            PresetHint("y = ln(x)", unit = 2.0, start = XStart.FROM_ZERO),
        ),
        Entry(
            CurvePreset("karekok", "Karekök", "sqrt(x)", false, defaultRepeat = 3),
            PresetHint("y = √x", unit = 2.0, start = XStart.FROM_ZERO),
        ),
        Entry(CurvePreset("kupkok", "Küpkök", "cbrt(x)", false, defaultRepeat = 4), PresetHint("y = ∛x", unit = 2.0)),
        Entry(CurvePreset("mutlak", "Mutlak değer", "abs(x)", false, defaultRepeat = 3), PresetHint("y = |x|")),
        Entry(CurvePreset("hiperbol", "Hiperbol", "1/x", false, defaultRepeat = 4), PresetHint("y = 1/x")),
        Entry(
            CurvePreset("kaydirilmis-hiperbol", "Kaydırılmış hiperbol", "1/(x - 2) + 1", false, defaultRepeat = 4),
            PresetHint("y = 1/(x − 2) + 1", center = 2.0),
        ),
        Entry(
            CurvePreset("cember", "Çember", "x^2 + y^2 = 9", false, defaultRepeat = 4),
            PresetHint("x² + y² = 9", equal = true, decorate = "circle:3"),
        ),
        Entry(
            CurvePreset("elips", "Elips", "x^2/9 + y^2/4 = 1", false, defaultRepeat = 4),
            PresetHint("x²/9 + y²/4 = 1", equal = true),
        ),
        Entry(
            CurvePreset("normal", "Normal dağılım (çan eğrisi)", "exp(-x^2/2)/sqrt(2pi)", false, defaultRepeat = 4),
            PresetHint("y = e^(−x²/2) / √(2π)"),
        ),
        Entry(CurvePreset("basamak", "Tam değer (basamak)", "floor(x)", false, defaultRepeat = 3), PresetHint("y = ⟦x⟧")),
        Entry(
            CurvePreset("sonumlu", "Sönümlü salınım", "e^(-x/4)*sin(2x)", true, PI, 4),
            PresetHint("y = e^(−x/4)·sin(2x)", start = XStart.ZERO),
        ),
        Entry(CurvePreset("arktanjant", "Arktanjant", "atan(x)", false, defaultRepeat = 4), PresetHint("y = arctan(x)", unit = 2.0)),
    )

    private val byId: Map<String, Entry> = entries.associateBy { it.preset.id }

    val all: List<CurvePreset> = entries.map { it.preset }

    fun find(id: String): CurvePreset? = byId[id]?.preset

    internal fun hint(id: String): PresetHint? = byId[id]?.hint
}
