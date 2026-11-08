package com.karapp.drawguidance.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** Fırça tipleri — fırça seçim modalında sunulan seçenekler. Her birinin çizim stili ui/drawing/BrushStyle.kt'de. */
@Serializable
enum class BrushType {
    // Sanat kalemleri
    PENCIL,
    INK_PEN,
    FINELINER,
    CALLIGRAPHY,
    CHARCOAL,
    // Geniş uçlu
    MARKER,
    FLAT_BRUSH,
    HIGHLIGHTER,
    // Sert uçlu
    HARD_ROUND,
    PASTEL,
    // Yumuşak uçlu
    SOFT_ROUND,
    AIRBRUSH,
    // Şekil izi
    STAMP_STAR,
    STAMP_HEART,
    STAMP_LEAF,
    // Desen
    PATTERN_DOTS,
    PATTERN_ZIGZAG,
    PATTERN_HATCH,
    // Doku
    TEXTURE_SPONGE,
    TEXTURE_CANVAS,
    TEXTURE_SPLATTER,
    // Yarım ton
    HALFTONE_DOTS,
    HALFTONE_LINES,
    // Çalışma / not kalemleri
    NOTE_PENCIL,
    NOTE_BALLPOINT,
    NOTE_FOUNTAIN,
    NOTE_GEL,
    NOTE_FINELINER,
    NOTE_FELT,
    NOTE_HIGHLIGHTER,
    // v15: nokta izi bırakan kalem ve 3B (gölgeli tüp) fırça
    DOT_PEN,
    BRUSH_3D,
    // v21 izler
    INK_TRAIL,
    DRIP_TRAIL,
    PAINT_TRAIL,
    // v21 desenler (kağıda sabit, fırçayla açığa çıkan motif)
    PATTERN_CARPET,
    PATTERN_WALLPAPER,
    PATTERN_WOODPAINT,
    // v21 canlı ve malzeme dokuları
    TEXTURE_SKIN,
    TEXTURE_FUR,
    TEXTURE_PLANT,
    TEXTURE_WOOD,
    TEXTURE_IRON,
    TEXTURE_METAL,
    // Araçlar
    ERASER,
    /** Vektör şekil: noktalar alt yolların uç uca eklenmiş noktalarıdır; yapısı ve biçimi [Stroke.shape]. */
    SHAPE,
    /** Diyagram: noktalar 4 köşedir (yazı gibi); düğüm ve oklar [Stroke.diagram]. */
    DIAGRAM,
    /** Yazı elemanı: noktalar yazı kutusunun 4 köşesidir (sol-üst, sağ-üst, sağ-alt, sol-alt); içerik [Stroke.text]. */
    TEXT,
    /** Dışarıdan eklenen resim: noktalar resmin 4 köşesidir (sol-üst, sağ-üst, sağ-alt, sol-alt). */
    IMAGE,
    /** Formül: noktalar kutunun 4 köşesidir (yazı gibi); kaynak ve düzenlenebilir alanlar [Stroke.formula]. */
    FORMULA,
}

/** Noktaları 4 köşeli bir kutu olan (fırça izi olmayan) elemanlar: resim, yazı ve diyagram. */
val BrushType.isQuad: Boolean
    get() = this == BrushType.IMAGE || this == BrushType.TEXT || this == BrushType.DIAGRAM || this == BrushType.FORMULA

/**
 * Fırça iziyle çizilmeyen, kendi başına bir nesne olan elemanlar (şekil, yazı, resim, diyagram):
 * her zaman ayrı bir elemandır (başka çizimlerle birleşmez) ve silgi değdiğinde tamamen silinir.
 */
val BrushType.isObject: Boolean
    get() = this == BrushType.IMAGE || this == BrushType.TEXT || this == BrushType.SHAPE || this == BrushType.DIAGRAM ||
        this == BrushType.FORMULA

/**
 * Silginin etkilediği çizimler: yalnızca fırçayla çizilen izler. Nesneler (şekil, diyagram, yazı, resim, formül)
 * silgiyle silinmez ve görüntüde de silgiden etkilenmez.
 */
val BrushType.isErasable: Boolean get() = !isObject && this != BrushType.ERASER

/** Yazı tipi grupları (yazı tipi listesinde başlık olarak). */
enum class FontGroup { SANS, SERIF, HAND, DISPLAY, MONO, COMIC }

/**
 * Yazı elemanının yazı tipi: Android sistem ailesi ([family]) ya da uygulamayla gelen yazı tipi dosyası
 * ([asset], `assets/fonts/` altında). [title] listede gösterilen ad (yazı tipinin kendisiyle yazılır).
 */
@Serializable
enum class TextFont(val family: String, val title: String, val group: FontGroup, val asset: String? = null) {
    SANS("sans-serif", "Roboto", FontGroup.SANS),
    SERIF("serif", "Noto Serif", FontGroup.SERIF),
    MONO("monospace", "Droid Sans Mono", FontGroup.MONO),
    CONDENSED("sans-serif-condensed", "Roboto Condensed", FontGroup.SANS),
    HANDWRITING("cursive", "El Yazısı", FontGroup.HAND),
    CASUAL("casual", "Gündelik", FontGroup.HAND),
    // v13: sistem çeşitleri
    LIGHT("sans-serif-light", "Roboto İnce", FontGroup.SANS),
    MEDIUM("sans-serif-medium", "Roboto Orta", FontGroup.SANS),
    BLACK("sans-serif-black", "Roboto Kalın", FontGroup.SANS),
    SMALLCAPS("sans-serif-smallcaps", "Küçük Büyük Harf", FontGroup.DISPLAY),
    SERIF_MONO("serif-monospace", "Daktilo", FontGroup.MONO),
    // v13: uygulamayla gelen yazı tipleri (SIL OFL / Apache 2.0)
    MONTSERRAT("sans-serif", "Montserrat", FontGroup.SANS, "fonts/montserrat.ttf"),
    POPPINS("sans-serif", "Poppins", FontGroup.SANS, "fonts/poppins.ttf"),
    NUNITO("sans-serif", "Nunito", FontGroup.SANS, "fonts/nunito.ttf"),
    RALEWAY("sans-serif", "Raleway", FontGroup.SANS, "fonts/raleway.ttf"),
    QUICKSAND("sans-serif", "Quicksand", FontGroup.SANS, "fonts/quicksand.ttf"),
    COMFORTAA("sans-serif", "Comfortaa", FontGroup.SANS, "fonts/comfortaa.ttf"),
    OSWALD("sans-serif-condensed", "Oswald", FontGroup.SANS, "fonts/oswald.ttf"),
    MERRIWEATHER("serif", "Merriweather", FontGroup.SERIF, "fonts/merriweather.ttf"),
    LORA("serif", "Lora", FontGroup.SERIF, "fonts/lora.ttf"),
    PLAYFAIR("serif", "Playfair Display", FontGroup.SERIF, "fonts/playfair_display.ttf"),
    ROBOTO_SLAB("serif", "Roboto Slab", FontGroup.SERIF, "fonts/roboto_slab.ttf"),
    CAVEAT("cursive", "Caveat", FontGroup.HAND, "fonts/caveat.ttf"),
    KALAM("cursive", "Kalam", FontGroup.HAND, "fonts/kalam.ttf"),
    PATRICK_HAND("casual", "Patrick Hand", FontGroup.HAND, "fonts/patrick_hand.ttf"),
    DANCING_SCRIPT("cursive", "Dancing Script", FontGroup.HAND, "fonts/dancing_script.ttf"),
    PACIFICO("cursive", "Pacifico", FontGroup.HAND, "fonts/pacifico.ttf"),
    GREAT_VIBES("cursive", "Great Vibes", FontGroup.HAND, "fonts/great_vibes.ttf"),
    AMATIC("casual", "Amatic SC", FontGroup.HAND, "fonts/amatic_sc.ttf"),
    BEBAS("sans-serif-condensed", "Bebas Neue", FontGroup.DISPLAY, "fonts/bebas_neue.ttf"),
    LOBSTER("cursive", "Lobster", FontGroup.DISPLAY, "fonts/lobster.ttf"),
    ABRIL("serif", "Abril Fatface", FontGroup.DISPLAY, "fonts/abril_fatface.ttf"),
    RIGHTEOUS("sans-serif", "Righteous", FontGroup.DISPLAY, "fonts/righteous.ttf"),
    PIXEL("monospace", "Press Start 2P", FontGroup.DISPLAY, "fonts/press_start_2p.ttf"),
    JETBRAINS_MONO("monospace", "JetBrains Mono", FontGroup.MONO, "fonts/jetbrains_mono.ttf"),
    SOURCE_CODE("monospace", "Source Code Pro", FontGroup.MONO, "fonts/source_code_pro.ttf"),
    TYPEWRITER("serif-monospace", "Special Elite", FontGroup.MONO, "fonts/special_elite.ttf"),
    // v27 çizgi roman yazı tipleri (SIL OFL / Apache 2.0; Türkçe karakterli)
    COMIC_RELIEF("casual", "Comic Relief", FontGroup.COMIC, "fonts/comic_relief.ttf"),
    COMIC_RELIEF_BOLD("casual", "Comic Relief Kalın", FontGroup.COMIC, "fonts/comic_relief_bold.ttf"),
    BANGERS("sans-serif-condensed", "Bangers", FontGroup.COMIC, "fonts/bangers.ttf"),
    LUCKIEST_GUY("sans-serif", "Luckiest Guy", FontGroup.COMIC, "fonts/luckiest_guy.ttf"),
    SHANTELL("casual", "Shantell Sans", FontGroup.COMIC, "fonts/shantell_sans.ttf"),
    BOOGALOO("casual", "Boogaloo", FontGroup.COMIC, "fonts/boogaloo.ttf"),
    GRANDSTANDER("casual", "Grandstander", FontGroup.COMIC, "fonts/grandstander.ttf"),
    BUNGEE("sans-serif", "Bungee", FontGroup.COMIC, "fonts/bungee.ttf"),
    BUNGEE_SHADE("sans-serif", "Bungee Shade", FontGroup.COMIC, "fonts/bungee_shade.ttf"),
    RUBIK_BUBBLES("sans-serif", "Rubik Bubbles", FontGroup.COMIC, "fonts/rubik_bubbles.ttf"),
    MARGARINE("casual", "Margarine", FontGroup.COMIC, "fonts/margarine.ttf"),
    MANSALVA("cursive", "Mansalva", FontGroup.COMIC, "fonts/mansalva.ttf"),
}

/**
 * Not tuvalinde yazının paragraf biçimi: başlık seviyeleri (daha büyük ve kalın), madde işaretli ve numaralı
 * liste. Liste işaretleri (•, 1.) yazının içinde durur; satır başındaki boşluklar liste seviyesidir.
 */
@Serializable
enum class TextParagraph(val sizeFactor: Float, val heading: Boolean = false) {
    NORMAL(1f), H1(2f, true), H2(1.6f, true), H3(1.3f, true), BULLET(1f), NUMBERED(1f), CHECKLIST(1f);

    val isList: Boolean get() = this == BULLET || this == NUMBERED || this == CHECKLIST
}

@Serializable
enum class TextAlignKind { LEFT, CENTER, RIGHT }

/** Yazı elemanının içeriği ve yazı tipi ayarları (yazı boyutu [Stroke.sizePx], rengi [Stroke.colorHex]). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TextSpec(
    val content: String,
    val font: TextFont = TextFont.SANS,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val align: TextAlignKind = TextAlignKind.LEFT,
    /** Paragraf biçimi (başlık/liste). */
    val paragraph: TextParagraph = TextParagraph.NORMAL,
    /** Harflerin çevresindeki kenarlık çizgisinin rengi (yoksa kenarlık çizilmez). Dolgu = [Stroke.colorHex]. */
    val outlineColorHex: String? = null,
    /** Kenarlık çizgisinin kalınlığı (tuval birimi). */
    val outlineWidth: Float = 0f,
    /** v23 yazı kutusu: genişlik/yükseklik (satırlar genişliğe göre kaydırılır) ve arka planı (renk, desen, resim). */
    val box: TextBox? = null,
    /** v42 yol üzerinde yazı: yazı tek satır olarak bu yolu izler (kalem aracıyla çizilen yoldan kopyalanır). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val path: TextPathSpec? = null,
)

/**
 * v42 yol üzerinde yazı (text on path). [points] yolun yazı kutusundaki yeri (kutunun sol-üstü 0,0; kutu [w]×[h]):
 * eleman taşınınca, döndürülünce ya da ölçeklenince yol da kutuyla birlikte dönüşür. [start] yazının yolda
 * başladığı yer (yol uzunluğuna oranla 0..1; ortalı yazıda orta nokta), [below] yazı yolun altında, [reversed] yazı
 * yolun ters yönünde akar.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TextPathSpec(
    val points: List<StrokePoint>,
    val w: Float,
    val h: Float,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val start: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val below: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val reversed: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val closed: Boolean = false,
)

/** v23 resmin yazı kutusuna yerleşimi. */
@Serializable
enum class BoxFit { COVER, STRETCH, NINE_SLICE, TILE }

/**
 * v23 yazı kutusu (not defterinden bir parça gibi): [width] satırların kaydırıldığı genişlik, [height] en az yükseklik
 * (yazı uzarsa kutu da uzar; tuval birimi, yazı boyutuyla birlikte ölçeklenmeden önceki boy). Arka plan: [fillHex]
 * renk, [patternId] desen ([patternColorHex] rengiyle, [patternScale] ölçeğiyle), [imageId] hazır resim; [corner]
 * köşe yuvarlaklığı, [padding] iç boşluk, [borderHex] kenar çizgisi.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TextBox(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val width: Float? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val height: Float? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fillHex: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val patternId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val patternColorHex: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val patternScale: Float = 1f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val imageId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val imageFit: BoxFit = BoxFit.NINE_SLICE,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val corner: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val padding: Float? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val borderHex: String? = null,
    /** v27 kutunun gradyan dolgusu (renkten önce; [fillHex] yerine). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fillGradient: GradientSpec? = null,
    /** v27 çizgi roman konuşma balonu: kutu balon biçiminde çizilir, kuyruklar konuşana işaret eder. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val balloon: BalloonSpec? = null,
    /** v27 yazı efektleri (gölge, gradyan dolgu, büyük harf, harf aralığı). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val effects: TextEffects? = null,
) {
    /** Arka planı var mı (renk, desen ya da resim). */
    val hasBackground: Boolean get() = fillHex != null || patternId != null || imageId != null || borderHex != null || fillGradient != null || balloon != null

    /** Kutuda saklanacak bir şey kalmadıysa null. */
    fun orNull(): TextBox? =
        if (width == null && height == null && !hasBackground && corner == 0f && padding == null && effects == null) null else this
}

/**
 * v27 gradyan: 2–4 renk ([colors], "#RRGGBB" ya da "#AARRGGBB"), doğrusal gradyanda [angle] derece (0 soldan sağa,
 * 90 yukarıdan aşağıya) ya da [radial] merkezden dışa.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GradientSpec(
    val colors: List<String>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val angle: Float = 90f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val radial: Boolean = false,
) {
    val valid: Boolean get() = colors.size >= 2
}

/** v27 konuşma balonu biçimleri (çizgi roman uygulamalarındaki yaygın türler). */
@Serializable
enum class BalloonStyle {
    /** Oval konuşma balonu. */
    SPEECH,
    /** Yuvarlak köşeli kutu. */
    ROUNDED,
    /** Düşünce bulutu (kuyruk küçülen kabarcıklar). */
    THOUGHT,
    /** Bağırma: sivri patlama kenarı. */
    SHOUT,
    /** Fısıltı: kesik çizgili kenar. */
    WHISPER,
    /** Anlatı kutusu (köşeli, kuyruksuz başlar). */
    NARRATION,
    /** Telefon/radyo: şimşek kuyruklu kutu. */
    ELECTRIC,
    /** Korku/titreme: dalgalı kenar. */
    WAVY,
    // v29 (çizgi roman harf yazımı gelenekleri: Blambot, Comics Kingdom; manga balonları)
    /** Vurgu: iç ve dış iki kenar çizgisi. */
    DOUBLE,
    /** Buz/soğuk ses: alt kenardan sarkan sivri buz saçakları. */
    ICICLE,
    /** Korku/canavar: alt kenardan damlayan kenar. */
    DRIP,
    /** Sevimli/yumuşak: kenarı küçük tırtıklı (fistolu) kutu. */
    SCALLOP,
    /** Ani fark ediş / manga flaş balonu: çok sayıda ince sivri ışın. */
    FLASH,
    /** Sevgi: kalp biçimli balon. */
    HEART,
    /** Robot/bilgisayar sesi: köşeleri kesik kutu. */
    CUT,
    /** Canavar/bozuk ses: pürüzlü, düzensiz kenar. */
    ROUGH,
    // v36
    /** Telefon mesajı: renkli, çok yuvarlak köşeli baloncuk; kısa kıvrık kuyruk alt köşede. */
    PHONE,
    /** Serbest yazı: balon çizilmez (yazı balon penceresinden düzenlenir), kuyruksuz. */
    FREE,
}

/** v27 balonun kuyruğu: ucun kutuya göre yeri (0..1 kutu içi; dışarı taşar), [curve] eğrilik (−1..1). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BalloonTail(
    val x: Float,
    val y: Float,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val curve: Float = 0.3f,
)

/**
 * v27 konuşma balonu: biçim, kuyruklar (her biri bir konuşana; boş liste kuyruksuz), dolgu ve kenar rengi, kenar
 * kalınlığı (yazı boyutunun katı).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BalloonSpec(
    val style: BalloonStyle = BalloonStyle.SPEECH,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tails: List<BalloonTail> = listOf(BalloonTail(0.25f, 1.6f)),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fillHex: String = "#FFFFFF",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val strokeHex: String = "#111111",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val strokeWidth: Float = 0.09f,
)

/**
 * v27 yazı efektleri: gölge ([shadowHex], uzaklık ve bulanıklık yazı boyutunun katı), gradyan dolgu ([gradient];
 * yazı rengi yerine), [caps] büyük harf, [letterSpacing] harf aralığı (em).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TextEffects(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val shadowHex: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val shadowDx: Float = 0.06f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val shadowDy: Float = 0.06f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val shadowBlur: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val gradient: GradientSpec? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val caps: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val letterSpacing: Float = 0f,
) {
    val isEmpty: Boolean get() = shadowHex == null && gradient?.valid != true && !caps && letterSpacing == 0f
}

/**
 * v23 tuvalin arka planı (rengin üstünde): resim ([imageId] "paper:<kimlik>" hazır resim ya da "asset:<kimlik>" galeriden
 * eklenen resim; [imageFit] yerleşim, [imageOpacity] örtücülük) ve desen ([patternId], [patternColorHex], ölçek ve
 * örtücülük). Resmin üstüne desen çizilir.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackgroundSpec(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val patternId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val patternColorHex: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val patternScale: Float = 1f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val patternOpacity: Float = 1f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val imageId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val imageFit: BoxFit = BoxFit.COVER,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val imageOpacity: Float = 1f,
    /** v27 kağıdın gradyanı (kağıt renginin yerine; resim ve desen üstüne çizilir). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val gradient: GradientSpec? = null,
    /**
     * v40 arka plan resminin kaydırılması: resmin çerçevesinin merkezi kağıdın merkezinden kağıt eninin/boyunun bu oranı
     * kadar uzakta (eski kayıtlarda 0: ortada).
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val imageOffsetX: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val imageOffsetY: Float = 0f,
    /** v40 arka plan resminin ölçeği (çerçeve kağıt boyunun bu katı; eski kayıtlarda 1: kağıdı kaplar). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val imageScale: Float = 1f,
) {
    val isEmpty: Boolean get() = patternId == null && imageId == null && gradient == null

    /** v40 resim kaydırılmış ya da boyutlandırılmış mı. */
    val hasImageTransform: Boolean get() = imageOffsetX != 0f || imageOffsetY != 0f || imageScale != 1f

    /** v40 [from]'un resim kaydırma ve ölçeğiyle (öteki ayarlar bu nesneninki). */
    fun withImageTransformOf(from: BackgroundSpec): BackgroundSpec =
        copy(imageOffsetX = from.imageOffsetX, imageOffsetY = from.imageOffsetY, imageScale = from.imageScale)

    /** Galeriden eklenen resmin veri kimliği (canvas_assets). */
    val assetId: String? get() = imageId?.takeIf { it.startsWith(ASSET) }?.removePrefix(ASSET)

    /** Hazır resmin kimliği (paper_assets). */
    val paperImageId: String? get() = imageId?.takeIf { it.startsWith(PAPER) }?.removePrefix(PAPER)

    companion object {
        const val ASSET = "asset:"
        const val PAPER = "paper:"
    }
}

/**
 * v24 not tuvalinde bir sayfanın kendi arka planı: [paperHex] kağıt rengi, [spec] desen/resim (null olan, tuvalin ortak
 * değerini kullanır). Sayfa eklenince bir önceki sayfanınki kopyalanır.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PageBackground(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val paperHex: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val spec: BackgroundSpec? = null,
    /**
     * v45 çizgi roman kapak sayfası: yalnız ilk sayfada (0) geçerlidir. Kapak sayfa numarasına, üst ve alt bilgiye
     * katılmaz; numaralar kapaktan sonraki sayfada 1'den başlar. Arka plan JSON'unun "pages" dizisinde saklanır (eski
     * sürümler bilinmeyen alanı yok sayar); sayfa listesiyle birlikte geri alınır/yinelenir.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val cover: Boolean = false,
) {
    /** Saklanacak bir şey yok (kendi kağıdı, deseni ve kapak bayrağı yok). */
    val isEmpty: Boolean get() = paperHex == null && spec == null && !cover

    /** v45 sayfanın kendi görünümü var mı (kağıt rengi ya da desen/resim; kapak bayrağı görünüm değildir). */
    val hasLook: Boolean get() = paperHex != null || spec != null

    /** v45 kapak bayrağı olmadan (sayfa çoğaltılınca/eklenince kopya kapak olmaz); boş kalırsa null. */
    fun withoutCover(): PageBackground? = copy(cover = false).takeUnless { it.isEmpty }
}

/**
 * v23 tuvaldeki 3B şekil: 3B modelin o anki görünümü bir resim olarak ([Stroke.assetId]) çizilir; modelin kendisi
 * [dataAssetId] verisinde (canvas_assets) saklanır. [modelId] modelin 3B Şekillerim'deki (models3d) kimliği:
 * "3B düzenle" ile açılır ve kaydedilince güncellenir.
 */
@Serializable
data class Model3DLink(val modelId: String, val dataAssetId: String)

/**
 * Tuval koordinatlarında (tuvalin kendi piksel boyutunda) bir nokta.
 *
 * v18 kalem dinamikleri: [pressure] kalem basıncı (0..1; parmakla ve basınçsız kalemde 1) ve [tilt] kalemin eğimi
 * (0 = dik, 1 = yatık). Fırça motoru kalınlığı ve (karakalem türü fırçalarda) koyuluğu bunlara göre değiştirir.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class StrokePoint(
    val x: Float,
    val y: Float,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pressure: Float = 1f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tilt: Float = 0f,
    /**
     * v20 kalemin yöneldiği yön (azimut, 0..1 → 0..360°; 0: bilinmiyor/kalemsiz). Kaligrafi ucu kalemin yatırıldığı
     * yöne dönebilsin diye.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val orient: Float = 0f,
) {
    /** Basınç, eğim ya da yön taşıyor mu (kalemle çizilmiş). */
    val hasDynamics: Boolean get() = pressure != 1f || tilt != 0f || orient != 0f

    /** Aynı basınç ve eğimle başka bir yer (taşıma, döndürme, ölçekleme). */
    fun at(x: Float, y: Float): StrokePoint = copy(x = x, y = y)
}

/**
 * Tek bir çizim elemanı (parmağın basılıp kalkana kadar çizdiği şekil).
 * Noktalar ve kalınlık tuval koordinatlarındadır; böylece ekran boyutundan
 * bağımsız olarak önizleme, dışa aktarma ve senkron aynı sonucu verir.
 * Aynı [groupId]'yi taşıyan elemanlar Elemanlar panelinde bir grup oluşturur.
 */
@Serializable
data class Stroke(
    val id: String,
    val points: List<StrokePoint>,
    val colorHex: String,
    val brushType: BrushType,
    val sizePx: Float,
    val opacity: Float,
    val order: Int,
    val groupId: String? = null,
    /** [BrushType.IMAGE] için resim verisinin (canvas_assets tablosu) kimliği. */
    val assetId: String? = null,
    /** Akışkanlık (0..1): her fırça damgasının bıraktığı boya miktarı; üst üste geçtikçe birikir. */
    val flow: Float = 1f,
    /** Çizim gücü (0..1): bastırma kuvveti; iz kalınlığı, koyuluğu ve dokunun doluluğu. */
    val strength: Float = 1f,
    /** [BrushType.TEXT] için yazı içeriği ve yazı tipi. */
    val text: TextSpec? = null,
    /** [BrushType.SHAPE] için şeklin yapısı ve biçimi (vektör bilgisi). */
    val shape: ShapeSpec? = null,
    /** [BrushType.DIAGRAM] için diyagramın düğümleri ve okları. */
    val diagram: DiagramSpec? = null,
    /**
     * Elemana uygulanmış kırpma: 4 köşeli dörtgen (tuval koordinatı). Yalnızca içi görünür; eleman taşınınca,
     * döndürülünce ya da ölçeklenince dörtgen de onunla birlikte dönüşür.
     */
    val clip: List<StrokePoint>? = null,
    /**
     * Elemanın katmanı ([Layer.id]); null, "" ya da bilinmeyen kimlik: ilk katman. "" türetilen elemanlarda (parçalama,
     * yeniden kurma) "kaynağın ilk katmanı" anlamındadır: yeni eleman gibi etkin katmana taşınmaz.
     */
    val layerId: String? = null,
    /** [BrushType.FORMULA] için formül kaynağı. */
    val formula: FormulaSpec? = null,
    /** v20 fırça izinin görünümünü değiştiren ayarlar (damga aralığı, eğim, 3B); null: fırçanın varsayılanları. */
    val params: BrushParams? = null,
    /**
     * v22 döndürülebilir 3B sahnedeki yeri (3B izler ve 3B cisimler; düz çizimlerde null). [points] her zaman sahnenin
     * o anki görünümündeki izdüşümdür; sahne dönünce bu bilgiden yeniden hesaplanır. İçerik biçiminde ayrı saklanır.
     */
    @kotlinx.serialization.Transient val space: Space3D? = null,
    /** v23 3B şekil ([BrushType.IMAGE] olarak çizilir; içerik biçiminde ayrı saklanır). */
    @kotlinx.serialization.Transient val model: Model3DLink? = null,
    /**
     * v30 gizlenen eleman: tuvalde, dışa aktarmada ve küçük resimde görünmez, seçilemez; Elemanlar panelinde göz
     * düğmesiyle yeniden gösterilir.
     */
    val hidden: Boolean = false,
    /**
     * v43 "Arka planla uyumla": resmin (nesnenin) özgün dosyası değişmez; gölge, renk/ışık ve kenar ayarları burada
     * saklanır ve çizimde uygulanır (kaldırılınca özgün görünüme döner).
     */
    val harmony: HarmonySpec? = null,
)

/**
 * v43 nesnenin arka planla uyumu (yıkıcı olmayan). Uzunluklar nesnenin boyuna göre oran (taşıma/ölçekleme/döndürmede
 * korunur). Bütün etkiler [intensity] ile ölçeklenir.
 *
 * - Renk/ışık: kanal kazançları ([gainR], [gainG], [gainB]), parlaklık kayması ([lift], −1…1) ve doygunluk.
 * - Gölge: zemine düşen gölge (nesnenin silueti; ayak çizgisinden [castSkew] ile yatırılıp [castScaleY] ile basılır,
 *   [shadowDx]/[shadowDy] kaydırılır, [shadowBlur] bulanık) ve ayak altındaki temas gölgesi ([contact]).
 * - Kenar: [feather] yumuşatma (en kısa kenara oran) ve [wrap] ışık sarması (kenarlara arka plan rengi, [wrapColorHex]).
 */
@Serializable
data class HarmonySpec(
    val intensity: Float = 1f,
    val color: Boolean = true,
    val gainR: Float = 1f,
    val gainG: Float = 1f,
    val gainB: Float = 1f,
    val lift: Float = 0f,
    val saturation: Float = 1f,
    val shadow: Boolean = true,
    val shadowColorHex: String = "#000000",
    val shadowOpacity: Float = 0.35f,
    val shadowDx: Float = 0f,
    val shadowDy: Float = 0f,
    val shadowBlur: Float = 0.03f,
    val castSkew: Float = 0f,
    val castScaleY: Float = 0.35f,
    val contact: Boolean = true,
    val edge: Boolean = true,
    val feather: Float = 0.006f,
    val wrap: Float = 0.3f,
    val wrapColorHex: String = "#FFFFFF",
    /** Işığın geldiği yön (derece; 0 sağ, 90 aşağı), bilgi için. */
    val lightAngle: Float = -135f,
)

/**
 * v22 3B sahnede bir elemanın yeri. Dünya koordinatı tuval birimindedir; başlangıç tuvalin ortası, X sağa, Y yukarı,
 * Z bakana doğru.
 * - İzde [frame] = başlangıç o, düzlemin iki ekseni u, v (birim) ve kabarma yönü w; [local] düzlemdeki noktalar
 *   (a0, b0, a1, b1…): nokta = o + a·u + b·v.
 * - Cisimde (3B şekil) [frame] = merkez o ve yarı boyutlu eksenler u (genişlik), v (yükseklik, yukarı), w (derinlik);
 *   [local] boş.
 * - [stamp] son izdüşümdeki noktaların özeti: noktalar 2B'de değiştirilince (taşıma, ölçekleme, silme…) sahnedeki yer
 *   yeniden hesaplanır. [view] son izdüşümün kamerası.
 */
class Space3D(
    val frame: FloatArray,
    val local: FloatArray,
    val stamp: Long,
    val view: Int,
) {
    init { require(frame.size == 12) }

    val isSolid: Boolean get() = local.isEmpty()
    val pointCount: Int get() = local.size / 2

    fun with(stamp: Long, view: Int) = Space3D(frame, local, stamp, view)

    override fun equals(other: Any?): Boolean =
        other is Space3D && stamp == other.stamp && view == other.view && frame.contentEquals(other.frame) && local.contentEquals(other.local)

    override fun hashCode(): Int = (frame.contentHashCode() * 31 + local.contentHashCode()) * 31 + stamp.hashCode()
}

/**
 * v20 fırça izinin görünüm ayarları (yalnız varsayılandan farklıysa saklanır). Çizim sırasında kullanılan girdi
 * ayarları (sabitleme, çizim hızı) çizgiye işlendiği için burada tutulmaz.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BrushParams(
    /** Damga aralığı (fırça çapının katı); null: fırçanın varsayılanı. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val spacing: Float? = null,
    /** Kalem eğimiyle genişleme (0..2); null: fırçanın varsayılanı. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tiltWidth: Float? = null,
    /** Kalem eğimiyle açılma (0..1); null: fırçanın varsayılanı. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tiltLight: Float? = null,
    /** Eğimin etki etmeye başladığı açı (derece, 0..80). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tiltThreshold: Float = 0f,
    /** Yassı (kaligrafi) uç kalemin yatırıldığı yöne döner. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val nibFollow: Boolean = false,
    /** 3B fırça ayarları ([BrushType.BRUSH_3D]); null: varsayılan. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tube: TubeParams? = null,
    /** v21 3B çizim modunda çizildi: düzlem ve derinlik (iz kalınlığında katı gibi kabarır). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val depth3d: Depth3D? = null,
    /** v29 çizgi roman hız/odak çizgisi grubunun üretim bilgisi (sıklığı sonradan değiştirilebilsin). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val comicLines: ComicLinesParams? = null,
)

/**
 * v29 çizgi roman çizgi grubunun üretim bilgisi: tür ([kind] adı), sıklık ([density]), tohum ([seed]), üretildiği alan
 * ([area]: sol, üst, sağ, alt) ve üretilen çizgilerin o anki kutusu ([bounds]). Grup taşınır ya da ölçeklenirse kutunun
 * yeni yeri alanı da aynı oranda taşır; sıklık değişince çizgiler aynı türle, aynı tohumla yeniden üretilir.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ComicLinesParams(
    val kind: String,
    val density: Float,
    val seed: Int,
    val area: List<Float>,
    val bounds: List<Float>,
    /** v36 odak noktası (alanın 0..1 oranı), yön (derece) ve uzunluk çarpanı; varsayılanlar v29 çizimi. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val focusX: Float = 0.5f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val focusY: Float = 0.5f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val angle: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val length: Float = 1f,
    /** v36 yumuşak anime çizgileri pastel renklerle (her çizgi sırayla bir renk). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val palette: Boolean = false,
)

/**
 * v21 3B çizim düzlemi (izometrik görünüm): serbest (izdüşüm yok), ön (sağa bakan dikey yüz), yan (sola bakan dikey yüz),
 * üst (yatay zemin). Düzlemde çizilen şekil izometrik olarak düzleme yatırılır (daire elips olur).
 */
@Serializable
enum class Plane3D { FREE, FRONT, SIDE, TOP }

/**
 * v21 3B çizim modunun ize kaydedilen bilgisi: çizildiği düzlem (noktalar zaten düzleme izdüşürülmüştür; öneride geri
 * açmak için) ve derinlik (0..1; izin kalınlık yönünde ne kadar kabaracağı).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Depth3D(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val plane: Plane3D = Plane3D.FREE,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val depth: Float = 0.4f,
    /** v22 kabarmanın ekrandaki yönü ve birim uzunluğu (sahnenin o anki görünümünde); null: v21 izometrik yön. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val ex: Float? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val ey: Float? = null,
)

/** 3B fırçanın malzemesi: ışığı nasıl yansıttığı. */
@Serializable
enum class TubeMaterial { MATTE, PLASTIC, METAL, GLASS }

/** v20 3B fırça: ışık yönü, kabartma, parlaklık, malzeme ve kağıda düşen gölge. */
@Serializable
data class TubeParams(
    /** Işığın geldiği yön (derece; 0 = sağ, 90 = aşağı, 225 = sol üst). */
    val lightAngle: Float = 225f,
    /** Kabartma yüksekliği (0..1): 0 düz, 1 tam yuvarlak. */
    val relief: Float = 0.75f,
    /** Parlaklık (0..1): ışık yansımasının gücü ve keskinliği. */
    val gloss: Float = 0.6f,
    val material: TubeMaterial = TubeMaterial.PLASTIC,
    /** Kağıda düşen gölgenin uzaklığı (fırça kalınlığının katı, 0..1; 0: gölge yok). */
    val shadow: Float = 0.3f,
    /** Gölgenin yumuşaklığı (0..1). */
    val shadowSoftness: Float = 0.5f,
)

/**
 * Tuvalin katmanı: katmanlar aşağıdan yukarı sıralıdır (listedeki sıra). Gizli katman çizilmez ve dışa aktarılmaz,
 * kilitli katmandaki elemanlar seçilemez, taşınamaz ve silinemez. Yeni elemanlar etkin katmana eklenir.
 */
@Serializable
data class Layer(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    val locked: Boolean = false,
) {
    companion object {
        const val BASE = "base"
        val Default = Layer(BASE, "Katman 1")
    }
}

/**
 * Formül elemanı: LaTeX alt kümesi kaynak ([source]); `\slot{ad}{değer}` kısımları kullanıcının düzenlediği
 * katsayı/bilinmeyenlerdir. Yazı boyutu [Stroke.sizePx], rengi [Stroke.colorHex].
 */
@Serializable
data class FormulaSpec(val source: String, val templateId: String = "")

/** Katman işlemleri: çizimlerin katmanı, katman sırasına göre dizilim ve görünürlük. */
object Layers {
    /** Çizimin geçerli katman kimliği (katman listesinde yoksa ilk katman). */
    fun of(stroke: Stroke, layers: List<Layer>): String {
        val id = stroke.layerId
        if (id != null && layers.any { it.id == id }) return id
        return layers.firstOrNull()?.id ?: Layer.BASE
    }

    fun indexOf(stroke: Stroke, layers: List<Layer>): Int {
        if (layers.size <= 1) return 0
        val id = stroke.layerId ?: return 0
        val i = layers.indexOfFirst { it.id == id }
        return if (i < 0) 0 else i
    }

    /**
     * Çizimleri katman sırasına göre dizer (katman içinde sıra korunur) ve `order` alanlarını yeniler. Zaten
     * sıralıysa aynı liste döner.
     */
    fun sort(strokes: List<Stroke>, layers: List<Layer>): List<Stroke> {
        if (layers.size <= 1) return strokes
        var sorted = true
        var last = -1
        for (s in strokes) {
            val i = indexOf(s, layers)
            if (i < last) { sorted = false; break }
            last = i
        }
        if (sorted) return strokes
        return strokes.withIndex().sortedWith(compareBy({ indexOf(it.value, layers) }, { it.index }))
            .mapIndexed { i, v -> if (v.value.order == i) v.value else v.value.copy(order = i) }
    }

    /** Görünür katmanlardaki gizlenmemiş çizimler (hepsi görünürse aynı liste). */
    fun visible(strokes: List<Stroke>, layers: List<Layer>): List<Stroke> {
        val allLayers = layers.all { it.visible }
        if (allLayers && strokes.none { it.hidden }) return strokes
        val hiddenLayers = if (allLayers) emptySet() else layers.filterNot { it.visible }.mapTo(HashSet()) { it.id }
        return strokes.filter { !it.hidden && (allLayers || of(it, layers) !in hiddenLayers) }
    }

    /** Çizim düzenlenebilir mi (gizlenmemiş; katmanı görünür ve kilitsiz)? */
    fun editable(stroke: Stroke, layers: List<Layer>): Boolean {
        if (stroke.hidden) return false
        if (layers.size <= 1 && layers.all { it.visible && !it.locked }) return true
        val id = of(stroke, layers)
        val layer = layers.firstOrNull { it.id == id } ?: return true
        return layer.visible && !layer.locked
    }

    /** Düzenlenemeyen (gizlenmiş ya da gizli/kilitli katmandaki) çizimlerin kimlikleri. */
    fun lockedIds(strokes: List<Stroke>, layers: List<Layer>): Set<String> {
        if (layers.all { it.visible && !it.locked } && strokes.none { it.hidden }) return emptySet()
        return strokes.filterNot { editable(it, layers) }.mapTo(HashSet()) { it.id }
    }

    /** Katman listesi boşsa varsayılan katman. */
    fun normalize(layers: List<Layer>): List<Layer> = layers.ifEmpty { listOf(Layer.Default) }
}

/**
 * Vektör şeklin bir alt yolu: [count] nokta, kapalı mı, kesik çizgi mi. [label] verilmişse bu alt yol tek
 * noktalık bir yazı çapasıdır (eksen etiketi, formül) ve o noktada [labelSize] boyutunda yazılır.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SubPath(
    val count: Int,
    val closed: Boolean = false,
    val dashed: Boolean = false,
    val label: String? = null,
    val labelSize: Float = 0f,
    /** Bu parçanın kendi çizgi rengi (yoksa şeklin kenar rengi): şekli bölmeden parçalara ayrı renk. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val strokeColorHex: String? = null,
    /** Bu bölgenin (kapalı parça) kendi dolgu rengi (yoksa şeklin dolgusu). "@ink": kenar rengiyle dolu. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fillColorHex: String? = null,
    /** Parçanın kenar kalınlığı çarpanı (şeklin kenar kalınlığına göre; 0 = kenar yok). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val widthScale: Float? = null,
    /** Noktalı çizgi. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dotted: Boolean = false,
    /** Parçanın saydamlığı (0..1). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val alpha: Float? = null,
    /** Parçanın tanımı (ör. grafikte "tick:x:3"): dokunulan parçanın ne olduğunu bulmak için. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tag: String? = null,
    /**
     * v16: parçanın vektör noktaları (parçadaki nokta sıraları, artan). Yoksa belirgin noktalar (köşeler, eğrinin
     * dönüm yerleri) kullanılır; şekil vektör olarak düzenlenince yazılır. Aradaki noktalar çizginin kavisidir.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val nodes: List<Int>? = null,
)

/** Şeklin görünümü: kenar rengi/kalınlığı, dolgu rengi ve köşe yuvarlaklığı (tuval birimi). */
@Serializable
data class ShapeStyle(
    val strokeColorHex: String = "#1E1E1E",
    val strokeWidth: Float = 4f,
    val fillColorHex: String? = null,
    val fillOpacity: Float = 1f,
    val cornerRadius: Float = 0f,
    /** v27 gradyan dolgu (şeklin kutusuna göre; [fillColorHex] yerine). */
    val fillGradient: GradientSpec? = null,
)

/** Grafiklerde eksen etiketleri ve çizilecek formül (ör. y = x^2 / z = sin(x)+cos(y)). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GraphSpec(
    val xLabel: String = "x",
    val yLabel: String = "y",
    val zLabel: String = "z",
    val formula: String = "",
    val xMin: Float = -5f,
    val xMax: Float = 5f,
    /** v15: grafik türü (content.graph.GraphKind adı; boşsa şekil türünden). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val kind: String = "",
    /** Hazır eğri (sinüs, parabol…) kimliği. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val preset: String = "",
    /** Desen tekrarı: periyot sayısı / aralık / sütun sayısı. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val repeat: Int = 2,
    /** Yarım eksen başına koordinat noktası. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val ticks: Int = 5,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val showNumbers: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val grid: Boolean = true,
    /** Kullanıcının şekil üstünde girdiği sayılar ("x:3" → "7"). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tickLabels: Map<String, String> = emptyMap(),
    /** Grafik verisi (sütun yükseklikleri, noktalar…). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val values: List<Float> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val valueLabels: Map<Int, String> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val curveColor: String = "#1E88E5",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fillUnder: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val keyPoints: Boolean = true,
    /** Açık aralık (NaN yerine null: kayıtta NaN yazılamaz). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val rangeSet: Boolean = false,
    /** v18 fonksiyon grafiğinde desen: eğrinin yatay sıklığı (y = f(k·x)). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val freq: Int = 1,
    /** v18 grafiğin kurulduğu kağıt rengi (formül kutusu, nokta içleri, ızgara buna uyar); boş = beyaz. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val paper: String = "",
    /** v45 formüldeki trigonometri derece cinsinden (yoksa yazılmaz; eski sürümler radyan sayar). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val angleDeg: Boolean = false,
)

/**
 * Vektör şekil: noktaların hangi alt yollara ait olduğu ([subpaths] sırayla [SubPath.count] nokta),
 * biçimi ve (grafikse) eksen/formül bilgisi. Noktalar tuval koordinatındadır; taşıma, döndürme ve
 * ölçekleme diğer çizimlerle aynı şekilde uygulanır.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ShapeSpec(
    val kind: String,
    val subpaths: List<SubPath>,
    val style: ShapeStyle = ShapeStyle(),
    val graph: GraphSpec? = null,
    /** v36 çizgi roman şablonunun paneli (yoksa yazılmaz; eski sürümler yok sayar). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val panel: PanelRef? = null,
    /**
     * v45 parametreli kütüphane şeklinin değerleri (anahtar → metin; ör. "v" → "20", "formula" → "CH3COOH"). Şekil
     * bu değerlerden yeniden çizilir; yoksa yazılmaz, eski sürümler yok sayar (Room şeması değişmez).
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val params: Map<String, String>? = null,
)

/**
 * v36 çizgi roman şablonu paneli: aynı [set] kimliğini taşıyan paneller bir sayfa düzenidir. [leaf] panelin düzendeki
 * kimliği; [layout] düzenin kısa metin biçimi (content.comic.PanelPage), setin bütün panellerinde aynıdır. Düzen
 * değişince (boyut, sıra, aralık, ekleme, silme) panellerin köşeleri bu bilgiden yeniden hesaplanır.
 */
@Serializable
data class PanelRef(val set: String, val leaf: Int, val layout: String)

@Serializable
enum class DiagramKind {
    FLOW, PROCESS, ARCHITECTURE, ORG, TIMELINE, STATE, RELATION, COMPARISON,
    // v15: ders ve sunum için
    CYCLE, MIND_MAP, PYRAMID, VENN, MATRIX, FUNNEL, FISHBONE, CONCEPT_MAP, TREE, KWL, STEPS, HUB,
    // v28 editoryal diyagram türleri (yapı)
    SEQUENCE, ER, UML_CLASS, DB_SCHEMA, SWIMLANE, KANBAN, STORY_MAP, DEPLOYMENT, NESTED, LAYERS, LOOP, QUADRANT,
    WARDLEY, JOURNEY, DEPENDENCY, IT_STATE, HIGH_LEVEL, MEDALLION, DATA_FLOW, DP_INTEGRATION, ARCH_DELTA,
    // v28 nicel (değerler adımların yazısından okunur)
    BAR, WATERFALL, LINE, SCATTER, RADAR, POLAR, TREEMAP, HEATMAP, DP_SECURITY, SANKEY, GANTT,
    // v28 aksonometrik
    EXPLODED, AXONOMETRIC,
}

/** Diyagram düğümü: 0..1 alanında sol-üst köşe ([x], [y]), genişlik/yükseklik ve kutu biçimi. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DiagramNode(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val box: DiagramBox = DiagramBox.ROUNDED,
    /** Adımın kendi dolgu / kenar / yazı rengi (yoksa diyagramın biçimi). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fillColorHex: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val strokeColorHex: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val textColorHex: String? = null,
    /**
     * v28 editoryal rol: "focal" (vurgu, en çok 1–2), "store", "external", "input", "optional", "security", "hub",
     * "boundary" (kesik bölge), "done", "blocked", "waiting", "cold", "added", "removed", "changed", "moving" ve
     * türe özgü roller ("row", "col", "stage", "mood", "action", "pain", "c0".."c2"). Yoksa olağan kutu.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val role: String? = null,
)

@Serializable
enum class DiagramBox {
    ROUNDED, RECT, DIAMOND, CIRCLE, CYLINDER, PILL, NOTE,
    // v15
    HEXAGON, PARALLELOGRAM, DOCUMENT, CLOUD, TRAPEZOID, ELLIPSE, TRIANGLE, CHEVRON,
    // v28 editoryal: tablo (başlık + satırlar), bölge, kulvar, sütun, kart, nokta, yalnız yazı, çubuk, hücre, levha
    TABLE, GROUP, LANE, COLUMN, CARD, DOT, LABEL, BAR, CELL, SLAB,
}

/** Okun adıma bağlandığı taraf; [AUTO]: iki adımın merkezlerini birleştiren düz ok. */
@Serializable
enum class DiagramSide { AUTO, TOP, RIGHT, BOTTOM, LEFT }

/**
 * Diyagram oku. [fromSide]/[toSide] seçilmişse ok adımın o tarafından çıkar/o tarafına girer ve dik açılarla
 * (kutuların içinden geçmeden) çizilir.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DiagramEdge(
    val from: String,
    val to: String,
    val label: String = "",
    val dashed: Boolean = false,
    val arrow: Boolean = true,
    val fromSide: DiagramSide = DiagramSide.AUTO,
    val toSide: DiagramSide = DiagramSide.AUTO,
    /** Okun kendi rengi (yoksa diyagramın kenar rengi). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val colorHex: String? = null,
    /**
     * v28 okun rolü: "accent" (vurgu), "link" (bağlantı mavisi), "inherit", "realize", "compose", "aggregate",
     * "depend" (UML), "spoke" (göbeğe), "arc" (üstten yay), "cycle" (geri ok).
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val role: String? = null,
    /** v28 uçlardaki küçük yazılar (ör. ilişki sayısı 1 / N). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fromLabel: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val toLabel: String? = null,
)

/**
 * Diyagramın tasarım alanında elemanın dörtgenine eşlenen bölge. Başlangıçta 0..1; adım çerçevenin dışına
 * konunca çerçeve büyür (eleman da aynı oranda büyür, adımların tuvaldeki yeri ve boyutu değişmez).
 */
@Serializable
data class DiagramFrame(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isDefault: Boolean get() = left == 0f && top == 0f && right == 1f && bottom == 1f
}

/** Diyagram: düğümler ve oklar; 0..1 alanında tanımlanır, elemanın 4 köşesine eşlenir. */
@Serializable
data class DiagramSpec(
    val kind: DiagramKind,
    val nodes: List<DiagramNode>,
    val edges: List<DiagramEdge> = emptyList(),
    val style: ShapeStyle = ShapeStyle(),
    val fontSize: Float = 0.045f,
    /** Tasarım alanının dörtgene eşlenen bölgesi. */
    val frame: DiagramFrame = DiagramFrame(),
    /** Adımlar elle yerleştirildi: yeni adım eklenince otomatik yerleşim yapılmaz (yerler korunur). */
    val manual: Boolean = false,
    /** v15: renk teması (boş: klasik; kalem rengi ve düz kutular). */
    val theme: String = "",
)

/** Kılavuz türleri: çizim yardımcı çizgileri (kağıdın üstünde, çizimlerin altında). */
@Serializable
enum class GuideKind { NONE, GRID, RULED, DOTS, PERSPECTIVE_1, PERSPECTIVE_2, PERSPECTIVE_3, ISOMETRIC }

/**
 * Kılavuz ve perspektif ayarları (tuval başına), ikisi ayrı ayrı açılıp kapanır.
 * [kind] kılavuz çizgileri (kareli/çizgili/noktalı/izometrik), [perspective] kaçış noktası sayısı (0 = kapalı).
 * [spacing] tuval birimi; ufuk ve kaçış noktaları kağıdın 0..1 oranındadır.
 */
@Serializable
data class GuideSpec(
    val kind: GuideKind = GuideKind.NONE,
    val spacing: Float = 80f,
    val colorHex: String = "#7C89A6",
    val opacity: Float = 0.35f,
    val horizon: Float = 0.5f,
    val vanishA: Float = 0.2f,
    val vanishB: Float = 0.8f,
    val vanishC: Float = 0.9f,
    val rays: Int = 24,
    val includeInExport: Boolean = false,
    val perspective: Int = 0,
    val perspectiveOpacity: Float = 0.35f,
    /** v21 3B çizim modu: izometrik kılavuz, düzleme izdüşüm ve tüm fırçalarda derinlik. */
    val draw3d: Boolean = false,
    val plane3d: Plane3D = Plane3D.FREE,
    val depth3d: Float = 0.4f,
    /**
     * v22 döndürülebilir 3B sahnenin kamerası: yatay dönüş (derece), dikey eğim (derece, −90 alttan … 90 üstten) ve
     * perspektif (0 paralel izdüşüm … 1 güçlü perspektif). Varsayılan izometrik görünüm.
     */
    val yaw3d: Float = DEFAULT_YAW,
    val pitch3d: Float = DEFAULT_PITCH,
    val persp3d: Float = 0f,
) {
    /** v9 kayıtlarında perspektif de [kind] içindeydi: ayrı alana taşınır. */
    fun normalized(): GuideSpec = when (kind) {
        GuideKind.PERSPECTIVE_1 -> copy(kind = GuideKind.NONE, perspective = 1, perspectiveOpacity = opacity)
        GuideKind.PERSPECTIVE_2 -> copy(kind = GuideKind.NONE, perspective = 2, perspectiveOpacity = opacity)
        GuideKind.PERSPECTIVE_3 -> copy(kind = GuideKind.NONE, perspective = 3, perspectiveOpacity = opacity)
        else -> this
    }

    val isEmpty: Boolean get() = kind == GuideKind.NONE && perspective == 0 && !draw3d

    /** v21 3B çizim modu açıksa yeni izlere yazılacak düzlem ve derinlik. */
    val draw3dSpec: Depth3D? get() = if (draw3d) Depth3D(plane3d, depth3d.coerceIn(0f, 1f)) else null

    companion object {
        /** v22 izometrik varsayılan görünüm: eksenler yatayla 30° yapar. */
        const val DEFAULT_YAW = 45f
        const val DEFAULT_PITCH = 35.26439f

        /** Kılavuz çizgisi türleri (perspektif ayrı eklenir). */
        val GridKinds = listOf(GuideKind.GRID, GuideKind.RULED, GuideKind.DOTS, GuideKind.ISOMETRIC)
    }
}
