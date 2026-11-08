package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind

/** Fırçanın izi nasıl ürettiği. */
enum class BrushEngine {
    /** Tek vektör yol (kalem, marker, fosforlu, teknik kalem) */
    PATH,
    /** Hıza göre kalınlaşıp incelen, uçları sivrilen mürekkep yolu */
    TAPERED_PATH,
    /** 45° yassı uç: yöne göre kalınlığı değişen şerit (kaligrafi) */
    RIBBON,
    /** Paralel kıl izleri (yassı fırça) */
    BRISTLE,
    /** Yuvarlak damgalar; isteğe bağlı kağıda sabit doku ile (sert fırça, pastel, kömür, doku fırçaları) */
    DABS,
    /** Kenarı yumuşak (degrade) damgalar (yumuşak fırça, airbrush) */
    SOFT_DABS,
    /** Belirli bir şeklin damgaları (yıldız, kalp, yaprak) */
    STAMP_SHAPE,
    /** Yol boyunca tekrar eden desen (nokta dizisi, zikzak, tarama) */
    PATTERN,
    /** Yolun çevresine saçılan damlacıklar (serpinti) */
    SCATTER,
    /** Kağıda sabit yarım ton ızgarası (nokta/çizgi) */
    HALFTONE,
    /** Işık alan küre damgalarından gölgeli tüp (3B fırça) */
    TUBE,
    /** v21 boya izi: kalın boyada fırça boyunca uzanan açık/koyu şeritler (tüp motoruyla aynı bant yapısı) */
    PAINT,
    /** v21 kağıda sabit, fırçanın rengine göre tonlanan doku ya da motif damgaları (halı, ahşap, ten, metal…) */
    SURFACE,
    /** v21 ıslak izler: mürekkep lekeleri ve damlacıklar, aşağı akan boya damlaları */
    TRAIL,
    IMAGE,
}

enum class TextureKind { PASTEL_GRAIN, CHARCOAL_GRAIN, SPONGE, CANVAS_WEAVE }
/** v21 ıslak iz türleri. */
enum class TrailKind { INK, DRIP }
/** v21 yüzey (doku ve desen) türleri. */
enum class SurfaceKind { CARPET, WALLPAPER, WOOD_PAINT, SKIN, FUR, PLANT, WOOD, IRON, METAL }
enum class StampShape { STAR, HEART, LEAF }
enum class PatternKind { DOTS, ZIGZAG, HATCH, DOT_TRAIL }
enum class HalftoneKind { DOTS, LINES }

/**
 * Bir fırçanın görünümünü belirleyen parametreler. Kullanıcının seçtiği boyut,
 * opaklık, akışkanlık ve çizim gücü bu değerlerle birleşir.
 */
data class BrushStyle(
    /** Çizgi kalınlığı = boyut × widthFactor (× çizim gücü etkisi) */
    val widthFactor: Float,
    /** Opaklık = kullanıcı opaklığı × alphaFactor */
    val alphaFactor: Float,
    val cap: StrokeCap = StrokeCap.Round,
    val join: StrokeJoin = StrokeJoin.Round,
    val blendMode: BlendMode = BlendMode.SrcOver,
    /** İz, fırça genişliğinin dışına taşabilir mi (tanecik, saçılma) — silinme testinde pay */
    val textured: Boolean = false,
    /** Çizgiyi eğrilerle yumuşat */
    val smooth: Boolean = true,
    val engine: BrushEngine = BrushEngine.PATH,
    /** Damga aralığı (genişliğin katı) */
    val spacing: Float = 0.25f,
    val texture: TextureKind? = null,
    val shape: StampShape? = null,
    val pattern: PatternKind? = null,
    val halftone: HalftoneKind? = null,
    /** Damga konumunun rastgele sapması (genişliğin katı) */
    val jitter: Float = 0f,
    /** Damga boyutunun rastgele değişimi (0..1) */
    val sizeJitter: Float = 0f,
    /** Yumuşak damgalarda sert çekirdek oranı (0 = tamamen degrade) */
    val hardness: Float = 1f,
    /** v21 ıslak iz türü ([BrushEngine.TRAIL]) */
    val trail: TrailKind? = null,
    /** v21 doku/desen türü ([BrushEngine.SURFACE]) */
    val surface: SurfaceKind? = null,
) {
    /** İzin fırça yarıçapına göre en fazla ne kadar uzağa ulaşabildiği */
    val reach: Float get() = 1.05f + jitter + sizeJitter * 0.5f + if (textured) 0.15f else 0f
}

fun BrushType.style(): BrushStyle = when (this) {
    // --- Sanat kalemleri ---
    // İnce, keskin grafit çizgi
    BrushType.PENCIL -> BrushStyle(widthFactor = 0.5f, alphaFactor = 1f)
    // Uçları sivrilen, hıza göre kalınlığı değişen mürekkep
    BrushType.INK_PEN -> BrushStyle(widthFactor = 0.8f, alphaFactor = 1f, engine = BrushEngine.TAPERED_PATH)
    // Sabit, ince, net teknik kalem
    BrushType.FINELINER -> BrushStyle(widthFactor = 0.35f, alphaFactor = 1f, smooth = false)
    // 45° yassı kaligrafi ucu
    BrushType.CALLIGRAPHY -> BrushStyle(widthFactor = 1.4f, alphaFactor = 1f, engine = BrushEngine.RIBBON, cap = StrokeCap.Butt)
    // Kaba taneli, yumuşak kenarlı kömür
    BrushType.CHARCOAL -> BrushStyle(
        widthFactor = 1.2f, alphaFactor = 0.95f, engine = BrushEngine.DABS, spacing = 0.08f,
        texture = TextureKind.CHARCOAL_GRAIN, jitter = 0.03f, sizeJitter = 0.12f, textured = true,
    )

    // --- Geniş uçlu ---
    // Geniş, düz uçlu, dolgun mürekkep
    BrushType.MARKER -> BrushStyle(widthFactor = 1.6f, alphaFactor = 0.92f, cap = StrokeCap.Square, join = StrokeJoin.Bevel, smooth = false)
    // Kıl izleri görünen yassı fırça
    BrushType.FLAT_BRUSH -> BrushStyle(widthFactor = 2.2f, alphaFactor = 0.95f, engine = BrushEngine.BRISTLE, cap = StrokeCap.Butt, textured = true)
    // Çok geniş, yarı saydam; altındaki çizimi koyulaştırır (çarpma karışımı)
    BrushType.HIGHLIGHTER -> BrushStyle(
        widthFactor = 3f, alphaFactor = 0.38f, cap = StrokeCap.Butt, join = StrokeJoin.Bevel, blendMode = BlendMode.Multiply, smooth = false,
    )

    // --- Sert uçlu ---
    // Net kenarlı yuvarlak fırça (akışkanlıkla birikir)
    BrushType.HARD_ROUND -> BrushStyle(widthFactor = 1f, alphaFactor = 1f, engine = BrushEngine.DABS, spacing = 0.2f)
    // Sert uçlu pastel boya: keskin kenar, kağıdın dişlerinde kalan boşluklar, kenarda kırıntılar
    BrushType.PASTEL -> BrushStyle(
        widthFactor = 1.3f, alphaFactor = 1f, engine = BrushEngine.DABS, spacing = 0.1f,
        texture = TextureKind.PASTEL_GRAIN, jitter = 0.04f, textured = true,
    )

    // --- Yumuşak uçlu ---
    BrushType.SOFT_ROUND -> BrushStyle(widthFactor = 1.4f, alphaFactor = 1f, engine = BrushEngine.SOFT_DABS, spacing = 0.15f, hardness = 0.35f)
    BrushType.AIRBRUSH -> BrushStyle(
        widthFactor = 2.4f, alphaFactor = 0.8f, engine = BrushEngine.SOFT_DABS, spacing = 0.18f, hardness = 0f, textured = true,
    )

    // --- Şekil izi ---
    BrushType.STAMP_STAR -> BrushStyle(widthFactor = 1.6f, alphaFactor = 1f, engine = BrushEngine.STAMP_SHAPE, spacing = 1.25f, shape = StampShape.STAR, jitter = 0.15f, sizeJitter = 0.3f, textured = true)
    BrushType.STAMP_HEART -> BrushStyle(widthFactor = 1.6f, alphaFactor = 1f, engine = BrushEngine.STAMP_SHAPE, spacing = 1.3f, shape = StampShape.HEART, jitter = 0.1f, sizeJitter = 0.25f, textured = true)
    BrushType.STAMP_LEAF -> BrushStyle(widthFactor = 1.8f, alphaFactor = 1f, engine = BrushEngine.STAMP_SHAPE, spacing = 0.9f, shape = StampShape.LEAF, jitter = 0.2f, sizeJitter = 0.3f, textured = true)

    // --- Desen ---
    BrushType.PATTERN_DOTS -> BrushStyle(widthFactor = 1.2f, alphaFactor = 1f, engine = BrushEngine.PATTERN, pattern = PatternKind.DOTS)
    BrushType.PATTERN_ZIGZAG -> BrushStyle(widthFactor = 1.6f, alphaFactor = 1f, engine = BrushEngine.PATTERN, pattern = PatternKind.ZIGZAG, join = StrokeJoin.Miter)
    BrushType.PATTERN_HATCH -> BrushStyle(widthFactor = 2f, alphaFactor = 1f, engine = BrushEngine.PATTERN, pattern = PatternKind.HATCH, cap = StrokeCap.Butt)

    // --- Doku ---
    BrushType.TEXTURE_SPONGE -> BrushStyle(
        widthFactor = 2f, alphaFactor = 0.9f, engine = BrushEngine.DABS, spacing = 0.3f,
        texture = TextureKind.SPONGE, jitter = 0.06f, sizeJitter = 0.2f, textured = true,
    )
    BrushType.TEXTURE_CANVAS -> BrushStyle(
        widthFactor = 1.8f, alphaFactor = 1f, engine = BrushEngine.DABS, spacing = 0.15f, texture = TextureKind.CANVAS_WEAVE, textured = true,
    )
    BrushType.TEXTURE_SPLATTER -> BrushStyle(widthFactor = 2f, alphaFactor = 1f, engine = BrushEngine.SCATTER, jitter = 0.9f, sizeJitter = 1f, textured = true)

    // --- Yarım ton ---
    BrushType.HALFTONE_DOTS -> BrushStyle(widthFactor = 2.4f, alphaFactor = 1f, engine = BrushEngine.HALFTONE, spacing = 0.15f, halftone = HalftoneKind.DOTS)
    BrushType.HALFTONE_LINES -> BrushStyle(widthFactor = 2.4f, alphaFactor = 1f, engine = BrushEngine.HALFTONE, spacing = 0.15f, halftone = HalftoneKind.LINES)

    // --- Çalışma / not kalemleri ---
    // Kurşun kalem: ince, hafif saydam (grimsi) grafit çizgisi
    BrushType.NOTE_PENCIL -> BrushStyle(widthFactor = 0.6f, alphaFactor = 0.78f)
    // Tükenmez kalem: ince, düzgün, koyu çizgi
    BrushType.NOTE_BALLPOINT -> BrushStyle(widthFactor = 0.42f, alphaFactor = 0.96f)
    // Dolma kalem: hıza göre kalınlığı değişen mürekkep
    BrushType.NOTE_FOUNTAIN -> BrushStyle(widthFactor = 0.62f, alphaFactor = 1f, engine = BrushEngine.TAPERED_PATH)
    // Jel kalem: biraz daha kalın, dolgun ve parlak mürekkep
    BrushType.NOTE_GEL -> BrushStyle(widthFactor = 0.62f, alphaFactor = 1f)
    // İnce uçlu (teknik) kalem: sabit, çok ince ve net
    BrushType.NOTE_FINELINER -> BrushStyle(widthFactor = 0.3f, alphaFactor = 1f, smooth = false)
    // Keçeli kalem: yuvarlak uçlu, dolgun, hafif saydam mürekkep
    BrushType.NOTE_FELT -> BrushStyle(widthFactor = 1f, alphaFactor = 0.9f)
    // Fosforlu kalem: yarı saydam, düz uçlu; yazıyı örtmeden vurgular (çarpma karışımı)
    BrushType.NOTE_HIGHLIGHTER -> BrushStyle(
        widthFactor = 2.6f, alphaFactor = 0.4f, cap = StrokeCap.Butt, join = StrokeJoin.Bevel, blendMode = BlendMode.Multiply, smooth = false,
    )

    // Nokta izi: eşit aralıklı, ayrık yuvarlak noktalar (noktalı çizgi)
    BrushType.DOT_PEN -> BrushStyle(widthFactor = 1f, alphaFactor = 1f, engine = BrushEngine.PATTERN, pattern = PatternKind.DOT_TRAIL)
    // 3B fırça: ışık alan, gölgeli ve parlak tüp (v20: kesit boyunca gölgelenen bantlar, kağıda düşen gölge)
    BrushType.BRUSH_3D -> BrushStyle(widthFactor = 1.3f, alphaFactor = 1f, engine = BrushEngine.TUBE, spacing = 0.08f)

    // v21 izler: mürekkep lekeli iz, aşağı akan damlalı döküm izi, şeritli kalın boya izi
    BrushType.INK_TRAIL -> BrushStyle(widthFactor = 1.1f, alphaFactor = 1f, engine = BrushEngine.TRAIL, spacing = 0.12f, trail = TrailKind.INK, jitter = 0.35f, textured = true)
    BrushType.DRIP_TRAIL -> BrushStyle(widthFactor = 1.2f, alphaFactor = 1f, engine = BrushEngine.TRAIL, spacing = 0.12f, trail = TrailKind.DRIP, textured = true)
    BrushType.PAINT_TRAIL -> BrushStyle(widthFactor = 1.6f, alphaFactor = 1f, engine = BrushEngine.PAINT, spacing = 0.08f, textured = true)
    // v21 desenler ve dokular: kağıda sabit motif/doku, fırçanın renginden tonlanır
    BrushType.PATTERN_CARPET -> surface(SurfaceKind.CARPET)
    BrushType.PATTERN_WALLPAPER -> surface(SurfaceKind.WALLPAPER)
    BrushType.PATTERN_WOODPAINT -> surface(SurfaceKind.WOOD_PAINT)
    BrushType.TEXTURE_SKIN -> surface(SurfaceKind.SKIN)
    BrushType.TEXTURE_FUR -> surface(SurfaceKind.FUR)
    BrushType.TEXTURE_PLANT -> surface(SurfaceKind.PLANT)
    BrushType.TEXTURE_WOOD -> surface(SurfaceKind.WOOD)
    BrushType.TEXTURE_IRON -> surface(SurfaceKind.IRON)
    BrushType.TEXTURE_METAL -> surface(SurfaceKind.METAL)

    // Gerçek silme: altındaki çizimleri şeffaflaştırır (kağıt rengi görünür)
    BrushType.ERASER -> BrushStyle(widthFactor = 2f, alphaFactor = 1f, blendMode = BlendMode.Clear)
    // Resim fırça değildir; çizgi kalınlığı yoktur
    BrushType.IMAGE, BrushType.TEXT, BrushType.SHAPE, BrushType.DIAGRAM, BrushType.FORMULA -> BrushStyle(widthFactor = 0f, alphaFactor = 1f, cap = StrokeCap.Butt, join = StrokeJoin.Miter, smooth = false, engine = BrushEngine.IMAGE)
}

private fun surface(kind: SurfaceKind) =
    BrushStyle(widthFactor = 2.2f, alphaFactor = 1f, engine = BrushEngine.SURFACE, spacing = 0.1f, surface = kind)

/** Çizim gücünün (bastırma) kalınlığa etkisi: hafif dokunuşta iz %45'e kadar incelir. */
fun pressureWidth(strength: Float): Float = 0.45f + 0.55f * strength.coerceIn(0f, 1f)

/** Çizim gücünün koyuluğa etkisi. */
fun pressureAlpha(strength: Float): Float = 0.35f + 0.65f * strength.coerceIn(0f, 1f)

/** Fırçanın tuval birimindeki gerçek iz genişliği. */
fun BrushType.inkWidth(sizePx: Float, strength: Float = 1f): Float =
    kotlin.math.max(1f, sizePx * style().widthFactor * pressureWidth(strength))

/** Her fırçanın kendine özgü varsayılan boyutu (fırça başına değiştirilen boyut ayrıca hatırlanır). */
fun BrushType.defaultSize(): Float = when (this) {
    BrushType.PENCIL -> 6f
    BrushType.INK_PEN -> 8f
    BrushType.FINELINER -> 6f
    BrushType.CALLIGRAPHY -> 14f
    BrushType.CHARCOAL -> 16f
    BrushType.MARKER -> 14f
    BrushType.FLAT_BRUSH -> 18f
    BrushType.HIGHLIGHTER -> 24f
    BrushType.HARD_ROUND -> 14f
    BrushType.PASTEL -> 18f
    BrushType.SOFT_ROUND -> 28f
    BrushType.AIRBRUSH -> 36f
    BrushType.STAMP_STAR, BrushType.STAMP_HEART -> 18f
    BrushType.STAMP_LEAF -> 16f
    BrushType.PATTERN_DOTS -> 12f
    BrushType.PATTERN_ZIGZAG -> 14f
    BrushType.PATTERN_HATCH -> 16f
    BrushType.TEXTURE_SPONGE -> 22f
    BrushType.TEXTURE_CANVAS -> 22f
    BrushType.TEXTURE_SPLATTER -> 16f
    BrushType.HALFTONE_DOTS, BrushType.HALFTONE_LINES -> 20f
    BrushType.NOTE_PENCIL -> 5f
    BrushType.NOTE_BALLPOINT -> 5f
    BrushType.NOTE_FOUNTAIN -> 6f
    BrushType.NOTE_GEL -> 6f
    BrushType.NOTE_FINELINER -> 5f
    BrushType.NOTE_FELT -> 8f
    BrushType.NOTE_HIGHLIGHTER -> 16f
    BrushType.DOT_PEN -> 6f
    BrushType.BRUSH_3D -> 18f
    BrushType.INK_TRAIL -> 14f
    BrushType.DRIP_TRAIL -> 16f
    BrushType.PAINT_TRAIL -> 18f
    BrushType.PATTERN_CARPET, BrushType.PATTERN_WALLPAPER, BrushType.PATTERN_WOODPAINT -> 36f
    BrushType.TEXTURE_SKIN, BrushType.TEXTURE_FUR, BrushType.TEXTURE_PLANT,
    BrushType.TEXTURE_WOOD, BrushType.TEXTURE_IRON, BrushType.TEXTURE_METAL -> 36f
    BrushType.ERASER -> 30f
    BrushType.IMAGE -> 1f
    BrushType.SHAPE, BrushType.DIAGRAM -> 4f
    BrushType.TEXT -> 48f
    BrushType.FORMULA -> 56f
}

/** Fırça seçicideki kategoriler ve sıraları; [kind] kategorinin hangi tuval türünde sunulduğu. */
enum class BrushCategory(val brushes: List<BrushType>, val kind: CanvasKind = CanvasKind.ART) {
    FINE_ART(listOf(BrushType.PENCIL, BrushType.INK_PEN, BrushType.FINELINER, BrushType.CALLIGRAPHY, BrushType.CHARCOAL)),
    BROAD(listOf(BrushType.MARKER, BrushType.FLAT_BRUSH, BrushType.HIGHLIGHTER)),
    HARD(listOf(BrushType.HARD_ROUND, BrushType.PASTEL)),
    SOFT(listOf(BrushType.SOFT_ROUND, BrushType.AIRBRUSH)),
    SHAPE(listOf(BrushType.STAMP_STAR, BrushType.STAMP_HEART, BrushType.STAMP_LEAF)),
    /** v21 izler */
    TRAIL(listOf(BrushType.INK_TRAIL, BrushType.DRIP_TRAIL, BrushType.PAINT_TRAIL)),
    PATTERN(
        listOf(
            BrushType.PATTERN_DOTS, BrushType.PATTERN_ZIGZAG, BrushType.PATTERN_HATCH,
            BrushType.PATTERN_CARPET, BrushType.PATTERN_WALLPAPER, BrushType.PATTERN_WOODPAINT,
        ),
    ),
    TEXTURE(listOf(BrushType.TEXTURE_SPONGE, BrushType.TEXTURE_CANVAS, BrushType.TEXTURE_SPLATTER)),
    /** v21 canlı dokuları (insan teni, hayvan kürkü, bitki yaprağı) */
    LIVING(listOf(BrushType.TEXTURE_SKIN, BrushType.TEXTURE_FUR, BrushType.TEXTURE_PLANT)),
    /** v21 malzeme dokuları (ahşap, demir, metal) */
    MATERIAL(listOf(BrushType.TEXTURE_WOOD, BrushType.TEXTURE_IRON, BrushType.TEXTURE_METAL)),
    HALFTONE(listOf(BrushType.HALFTONE_DOTS, BrushType.HALFTONE_LINES)),
    /** v15: nokta izi ve 3B fırça (resim tuvali) */
    EFFECTS(listOf(BrushType.DOT_PEN, BrushType.BRUSH_3D)),
    NOTE_WRITING(
        listOf(BrushType.NOTE_PENCIL, BrushType.NOTE_BALLPOINT, BrushType.NOTE_FOUNTAIN, BrushType.NOTE_GEL, BrushType.NOTE_FINELINER),
        CanvasKind.NOTES,
    ),
    NOTE_MARKING(listOf(BrushType.NOTE_FELT, BrushType.NOTE_HIGHLIGHTER), CanvasKind.NOTES),
    /** v15: not tuvalinde de nokta izi ve 3B fırça */
    NOTE_EFFECTS(listOf(BrushType.DOT_PEN, BrushType.BRUSH_3D), CanvasKind.NOTES),
    ;

    companion object {
        fun of(type: BrushType): BrushCategory? = entries.firstOrNull { type in it.brushes }
        /** v33 basit çizimde yalnız en önemli 3 kategori (çocuklarda kalem, keçeli ve damga). */
        fun forKind(kind: CanvasKind): List<BrushCategory> = when (kind) {
            CanvasKind.SIMPLE_ART -> listOf(FINE_ART, BROAD, SOFT)
            CanvasKind.KIDS -> listOf(FINE_ART, BROAD, SHAPE)
            CanvasKind.SIMPLE_NOTES -> listOf(NOTE_WRITING, NOTE_MARKING, NOTE_EFFECTS)
            else -> entries.filter { it.kind == kind.base }
        }
    }
}

/** Resim tuvalinin fırça seçicisinde listelenen tüm fırçalar (silgi üst menüde ayrı; resim fırça değildir). */
val PickerBrushTypes: List<BrushType> = BrushCategory.forKind(CanvasKind.ART).flatMap { it.brushes }

/** Çalışma/not tuvalinin kalemleri. */
val NoteBrushTypes: List<BrushType> = BrushCategory.forKind(CanvasKind.NOTES).flatMap { it.brushes }

/** Tuval türünün fırça seçicisindeki fırçalar. */
fun pickerBrushTypes(kind: CanvasKind): List<BrushType> = when {
    kind.simple -> BrushCategory.forKind(kind).flatMap { it.brushes }
    kind.isNotes -> NoteBrushTypes
    else -> PickerBrushTypes
}

/** Tuval türünün varsayılan fırçası. */
fun defaultBrush(kind: CanvasKind): BrushType = when {
    // v33 çocuklar için kalın, canlı keçeli kalem
    kind == CanvasKind.KIDS -> BrushType.MARKER
    kind.isNotes -> BrushType.NOTE_BALLPOINT
    else -> BrushType.PENCIL
}

/**
 * v18 kalem dinamikleri: basınç ve eğimin fırçaya etkisi. Kalınlık, fırça boyutunun (en çok) basınçla ne kadarına
 * inebileceğini [minWidth] belirler (kalemlerde çok, fosforlu kalemde az). Karakalem, pastel ve yumuşak fırçalarda
 * basınç koyuluğu da değiştirir; kurşun kalem, kömür ve pastel yatırılınca genişleyip açılır (gölgeleme).
 */
internal class BrushDynamics(
    private val minWidth: Float,
    private val pressureAlpha: Boolean,
    /** Tam yatık kalemde genişleme (0: yok, 1: iki kat). */
    val tiltWiden: Float,
    /** Tam yatık kalemde açılma (0..1). */
    val tiltLighten: Float,
    /** v20: eğimin etki etmeye başladığı nokta (0..1 → 0..90°). */
    private val threshold: Float = 0f,
) {
    /** Eşikten sonraki eğim (0..1). */
    private fun effectiveTilt(tilt: Float): Float {
        val t = tilt.coerceIn(0f, 1f)
        return if (threshold <= 0f) t else ((t - threshold) / (1f - threshold)).coerceIn(0f, 1f)
    }

    fun width(pressure: Float, tilt: Float): Float {
        val p = pressure.coerceIn(0f, 1f)
        val curve = Math.pow(p.toDouble(), 0.7).toFloat()
        return (minWidth + (1f - minWidth) * curve) * (1f + tiltWiden * effectiveTilt(tilt))
    }

    fun alpha(pressure: Float, tilt: Float): Float {
        val p = pressure.coerceIn(0f, 1f)
        val a = if (pressureAlpha) 0.3f + 0.7f * p else 1f
        return (a * (1f - tiltLighten * effectiveTilt(tilt))).coerceIn(0.05f, 1f)
    }

    /** En geniş kalınlık çarpanı (tam basınç, tam yatık). */
    val maxWidth: Float get() = 1f + tiltWiden

    /** v20: kullanıcının fırça için seçtiği eğim ayarları (verilmeyenler varsayılan kalır). */
    fun with(params: com.karapp.drawguidance.data.model.BrushParams?): BrushDynamics {
        if (params == null || (params.tiltWidth == null && params.tiltLight == null && params.tiltThreshold <= 0f)) return this
        return BrushDynamics(
            minWidth, pressureAlpha,
            params.tiltWidth?.coerceIn(0f, 2f) ?: tiltWiden,
            params.tiltLight?.coerceIn(0f, 1f) ?: tiltLighten,
            (params.tiltThreshold / 90f).coerceIn(0f, 0.95f),
        )
    }

    companion object {
        private val PENCIL = BrushDynamics(minWidth = 0.35f, pressureAlpha = false, tiltWiden = 1.6f, tiltLighten = 0f)
        private val GRAPHITE = BrushDynamics(minWidth = 0.3f, pressureAlpha = true, tiltWiden = 1.8f, tiltLighten = 0.45f)
        private val SOFT = BrushDynamics(minWidth = 0.35f, pressureAlpha = true, tiltWiden = 0f, tiltLighten = 0f)
        private val PEN = BrushDynamics(minWidth = 0.22f, pressureAlpha = false, tiltWiden = 0f, tiltLighten = 0f)
        private val MARKER = BrushDynamics(minWidth = 0.6f, pressureAlpha = false, tiltWiden = 0f, tiltLighten = 0f)
        private val HIGHLIGHT = BrushDynamics(minWidth = 0.8f, pressureAlpha = false, tiltWiden = 0f, tiltLighten = 0f)

        fun of(type: BrushType): BrushDynamics = when (type) {
            BrushType.PENCIL, BrushType.NOTE_PENCIL -> PENCIL
            BrushType.CHARCOAL, BrushType.PASTEL -> GRAPHITE
            BrushType.SOFT_ROUND, BrushType.AIRBRUSH, BrushType.HARD_ROUND, BrushType.TEXTURE_SPONGE, BrushType.TEXTURE_CANVAS,
            BrushType.PATTERN_CARPET, BrushType.PATTERN_WALLPAPER, BrushType.PATTERN_WOODPAINT,
            BrushType.TEXTURE_SKIN, BrushType.TEXTURE_FUR, BrushType.TEXTURE_PLANT,
            BrushType.TEXTURE_WOOD, BrushType.TEXTURE_IRON, BrushType.TEXTURE_METAL -> SOFT
            BrushType.PAINT_TRAIL -> MARKER
            BrushType.MARKER, BrushType.NOTE_FELT, BrushType.FLAT_BRUSH -> MARKER
            BrushType.HIGHLIGHTER, BrushType.NOTE_HIGHLIGHTER -> HIGHLIGHT
            else -> PEN
        }
    }
}

/**
 * v20 fırçanın varsayılan damga aralığı (genişliğin katı); aralığı olmayan (tek yol ya da şerit çizen) fırçalarda
 * null. Kullanıcı bunu [com.karapp.drawguidance.data.model.BrushParams.spacing] ile değiştirebilir.
 */
fun BrushType.defaultSpacing(): Float? {
    val st = style()
    return when (st.engine) {
        BrushEngine.DABS, BrushEngine.SOFT_DABS, BrushEngine.STAMP_SHAPE, BrushEngine.HALFTONE,
        BrushEngine.SURFACE, BrushEngine.TRAIL -> st.spacing
        BrushEngine.SCATTER -> 0.45f
        BrushEngine.PATTERN -> when (st.pattern) {
            PatternKind.DOTS -> 1.6f
            PatternKind.DOT_TRAIL -> 2.2f
            PatternKind.HATCH -> 0.15f
            PatternKind.ZIGZAG -> 0.55f
            null -> null
        }
        else -> null
    }
}

/** v20 kaligrafi gibi yassı uçlu fırçalar: uç kalemin yönüne dönebilir. */
val BrushType.hasFlatNib: Boolean get() = style().engine == BrushEngine.RIBBON

/** v20 eğim ayarlarının varsayılanları (kalınlık 0..2, açıklık 0..1). */
fun BrushType.defaultTiltWidth(): Float = BrushDynamics.of(this).tiltWiden
fun BrushType.defaultTiltLight(): Float = BrushDynamics.of(this).tiltLighten

/**
 * İzin fırça boyutuna göre en fazla ne kadar uzağa ulaşabileceği (görünür alan ve önbellek sınırları için): kalınlık,
 * doku/saçılma, eğimle genişleme ve 3B fırçanın kağıda düşen gölgesi.
 */
fun BrushType.reachFactor(params: com.karapp.drawguidance.data.model.BrushParams?): Float {
    val st = style()
    val widen = BrushDynamics.of(this).with(params).maxWidth
    var r = st.widthFactor * 0.5f * st.reach * widen * 1.15f
    // v21 döküm izinde damlalar izden aşağı uzar
    if (this == BrushType.DRIP_TRAIL) r += st.widthFactor * DRIP_MAX_LENGTH * widen
    if (this == BrushType.BRUSH_3D) {
        val tube = params?.tube ?: com.karapp.drawguidance.data.model.TubeParams()
        r += st.widthFactor * (tube.shadow.coerceIn(0f, 1f) * TUBE_SHADOW_MAX + tube.shadowSoftness.coerceIn(0f, 1f) * TUBE_BLUR_MAX + 0.1f) * widen
    }
    return maxOf(1.6f, r)
}

/** v21 döküm izindeki damlaların iz genişliğine göre en uzun boyu. */
internal const val DRIP_MAX_LENGTH = 3.2f

/**
 * v21 çizim önerisi politikası: öneri fırçanın görünümünü bozmamalı.
 * - [WRITING]: yazı kalemleri; el yazısı (yazıya çevirme), şekil düzeltmesi ve şekil veritabanı önerileri.
 * - [GEOMETRY]: sanat, iz, desen ve damga fırçaları; yalnız çizginin geometrisi düzeltilir (şekil ya da şablon), fırça,
 *   renk, boyut ve damga aralığı aynı kalır; yazıya çevrilmez (dokulu iz düz yazıya dönüşmesin).
 * - [NONE]: alan dolduran doku, yarım ton, tarama, yumuşak fırça ve serpintide öneri yok (düzgün geometri dokuyu bozar).
 */
enum class SuggestPolicy { WRITING, GEOMETRY, NONE }

fun BrushType.suggestPolicy(): SuggestPolicy = when (this) {
    BrushType.PENCIL, BrushType.INK_PEN, BrushType.FINELINER, BrushType.CALLIGRAPHY, BrushType.MARKER, BrushType.HIGHLIGHTER,
    BrushType.NOTE_PENCIL, BrushType.NOTE_BALLPOINT, BrushType.NOTE_FOUNTAIN, BrushType.NOTE_GEL, BrushType.NOTE_FINELINER,
    BrushType.NOTE_FELT, BrushType.NOTE_HIGHLIGHTER -> SuggestPolicy.WRITING
    BrushType.CHARCOAL, BrushType.FLAT_BRUSH, BrushType.HARD_ROUND, BrushType.PASTEL,
    BrushType.STAMP_STAR, BrushType.STAMP_HEART, BrushType.STAMP_LEAF, BrushType.PATTERN_DOTS, BrushType.PATTERN_ZIGZAG,
    BrushType.DOT_PEN, BrushType.BRUSH_3D, BrushType.INK_TRAIL, BrushType.DRIP_TRAIL, BrushType.PAINT_TRAIL -> SuggestPolicy.GEOMETRY
    else -> SuggestPolicy.NONE
}

/** 3B fırça: gölge uzaklığı ve yumuşaklığının (bulanıklık yarıçapı) iz genişliğine göre üst sınırı. */
internal const val TUBE_SHADOW_MAX = 0.6f
internal const val TUBE_BLUR_MAX = 0.35f
