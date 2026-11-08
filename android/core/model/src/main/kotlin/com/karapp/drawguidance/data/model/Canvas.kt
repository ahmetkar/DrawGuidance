package com.karapp.drawguidance.data.model

/** Hazır tuval boyut şablonları (Yeni Tuval modalında sunulur). */
enum class CanvasSizeTemplate(val label: String, val width: Int, val height: Int) {
    SQUARE("Kare", 1080, 1080),
    PORTRAIT("Portre", 1080, 1920),
    LANDSCAPE("Manzara", 1920, 1080),
    /** Çalışma / not alma: A4 dikey (150 dpi) */
    NOTE_A4("Çalışma/Not (A4)", 1240, 1754),
    /** v27 çizgi roman sayfası (2:3). */
    COMIC_PAGE("Çizgi roman sayfası", 1200, 1800),
    CUSTOM("Özel", 1080, 1080),
}

/**
 * Tuval türü: fırça ve şekil kataloğunu belirler.
 * - [ART] resim: tüm sanat fırçaları; manzara/portre için geometrik ve özel şekiller.
 * - [NOTES] çalışma/not alma: yazı kalemleri; matematik ve geometri şekilleri.
 * - v33 basit çizim türleri ([SIMPLE_ART], [SIMPLE_NOTES], [KIDS]): tek üst menü, sınırlı katalog (10'ar şekil,
 *   diyagram, formül ve 3B şekil; 3 fırça kategorisi). Çizim kuralları [base] türününkidir.
 */
enum class CanvasKind {
    ART,
    NOTES,
    /**
     * v27 çizgi roman: resim tuvalinin bütün çizim özellikleri, ayrıca konuşma balonları, panel düzenleri, efekt
     * yazıları, hız/odak çizgileri ve sayfalar.
     */
    COMIC,
    /** v33 basit resim çizimi (basit çizim). */
    SIMPLE_ART,
    /** v33 basit not alma (basit çizim; sayfalı). */
    SIMPLE_NOTES,
    /** v33 çocuklar için çizim (basit çizim): 50 çocuk şekli, kolay boyama, sade ayarlar. */
    KIDS;

    /** Fırça, şekil kataloğu ve kağıt seçeneklerinde kullanılan temel tür (çizgi roman ve basit türler). */
    val base: CanvasKind get() = when (this) {
        COMIC, SIMPLE_ART, KIDS -> ART
        SIMPLE_NOTES -> NOTES
        else -> this
    }

    /** Birden çok sayfası olabilir (not, basit not ve çizgi roman). */
    val paged: Boolean get() = this == NOTES || this == COMIC || this == SIMPLE_NOTES

    /** v33 basit çizim türü (tek üst menü, sınırlı katalog). */
    val simple: Boolean get() = this == SIMPLE_ART || this == SIMPLE_NOTES || this == KIDS

    /** v33 not alma ailesi (not alma ve basit not alma). */
    val isNotes: Boolean get() = base == NOTES

    /**
     * v43 sunucudan gelen içerik paketlerinin modu: çizgi roman (COMIC), not alma ailesi (NOTES), resim ailesi (ART).
     * Basit resim ve basit not kendi ailesinin paketlerini kullanır; çocuk modu resim ailesindedir.
     */
    fun packMode(): String = when {
        this == COMIC -> "COMIC"
        isNotes -> "NOTES"
        else -> "ART"
    }

    /** v33 resim ailesi, çizgi roman hariç (resim, basit resim, çocuklar için çizim). */
    val isPlainArt: Boolean get() = base == ART && this != COMIC

    /** v33 yeni tuvalin öntanımlı boyutu (not ailesi A4, çizgi roman sayfası, öteki türler kare). */
    val defaultSize: CanvasSizeTemplate get() = when {
        isNotes -> CanvasSizeTemplate.NOTE_A4
        this == COMIC -> CanvasSizeTemplate.COMIC_PAGE
        else -> CanvasSizeTemplate.SQUARE
    }

    /** v33 en çok sayfa sayısı: çizgi roman 1000, öteki sayfalı türler (not alma) 200, tek sayfalılar 1. */
    val maxPages: Int get() = when {
        this == COMIC -> 1000
        paged -> 200
        else -> 1
    }

    companion object {
        fun parse(value: String?): CanvasKind = entries.firstOrNull { it.name == value } ?: ART

        /** v33 basit çizim alt modları. */
        val Simple: List<CanvasKind> = listOf(SIMPLE_ART, SIMPLE_NOTES, KIDS)

        /** v33 gelişmiş çizim alt modları (3B şekil oluşturma ayrı: 3B düzenleyicide açılır). */
        val Advanced: List<CanvasKind> = listOf(ART, NOTES, COMIC)
    }
}

/**
 * UI/Repository katmanında kullanılan tuval domain modeli.
 * Room (CanvasEntity) kaydından bu modele eşleme yapılır. Çizimler yalnızca cihazda saklanır.
 */
data class CanvasUi(
    val localId: String,
    val name: String,
    val width: Int,
    val height: Int,
    val backgroundColorHex: String,
    val aiEnabled: Boolean,
    /** Sayfalar (not tuvalinde birden çok olabilir); resim tuvalinde tek sayfa. */
    val pages: List<List<Stroke>>,
    val createdAt: Long,
    val updatedAt: Long,
    val kind: CanvasKind = CanvasKind.ART,
    /** Açık sayfanın sırası. */
    val currentPage: Int = 0,
    val guide: GuideSpec = GuideSpec(),
    /** Katmanlar (aşağıdan yukarı; tüm sayfalarda ortak). */
    val layers: List<Layer> = listOf(Layer.Default),
    /** Klasör (null: kök). */
    val folderId: String? = null,
    /** Kayıtlı boyut (bayt): çizim verisi ve eklenen resimler (tuval listesinde gösterilir). */
    val sizeBytes: Long = 0L,
    /** v23 arka plan resmi ve deseni (rengin üstünde); v24'te tüm sayfaların ortak (varsayılan) arka planı. */
    val background: BackgroundSpec = BackgroundSpec(),
    /** v24 sayfaların kendi arka planları (sayfa sırasıyla; olmayan ya da null sayfa ortak arka planı kullanır). */
    val pageBackgrounds: List<PageBackground?> = emptyList(),
    /** v40 sayfa numarası, üst bilgi ve alt bilgi (not alma ve çizgi roman). */
    val pageSettings: PageSettings = PageSettings(),
) {
    /** Açık sayfanın çizimleri. */
    val strokes: List<Stroke> get() = pages.getOrElse(currentPage) { pages.firstOrNull() ?: emptyList() }
    val pageCount: Int get() = pages.size

    /** v24 [page] sayfasının kağıt rengi. */
    fun paperOf(page: Int): String = pageBackgrounds.getOrNull(page)?.paperHex ?: backgroundColorHex

    /** v24 [page] sayfasının deseni/resmi. */
    fun backgroundOf(page: Int): BackgroundSpec = pageBackgrounds.getOrNull(page)?.spec ?: background

    /** Açık sayfanın kağıt rengi ve deseni/resmi. */
    val pagePaperHex: String get() = paperOf(currentPage)
    val pageBackground: BackgroundSpec get() = backgroundOf(currentPage)

    /** Sayfaların bazılarının kendi arka planı var mı (v45 kapak bayrağı arka plan sayılmaz). */
    val hasPageBackgrounds: Boolean get() = pageBackgrounds.any { it != null && it.hasLook }

    /** v45 ilk sayfa çizgi roman kapağı mı (kapak numaralanmaz, üst/alt bilgi almaz). */
    val hasCover: Boolean get() = pageBackgrounds.firstOrNull()?.cover == true

    /** v45 [page] sayfası kapak mı (yalnız ilk sayfa kapak olabilir). */
    fun isCover(page: Int): Boolean = page == 0 && hasCover
}

/** Tek sayfalı tuval (testler ve tek sayfalık akışlar için). */
fun canvasOf(
    localId: String,
    name: String,
    width: Int,
    height: Int,
    backgroundColorHex: String,
    aiEnabled: Boolean,
    strokes: List<Stroke>,
    createdAt: Long,
    updatedAt: Long,
    kind: CanvasKind = CanvasKind.ART,
): CanvasUi = CanvasUi(
    localId, name, width, height, backgroundColorHex, aiEnabled, listOf(strokes), createdAt, updatedAt, kind,
)
