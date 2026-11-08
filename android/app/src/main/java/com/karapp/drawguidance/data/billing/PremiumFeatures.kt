package com.karapp.drawguidance.data.billing

import com.karapp.drawguidance.content.comic.ComicLinesKind
import com.karapp.drawguidance.content.comic.ComicTemplate
import com.karapp.drawguidance.content.comic.EffectGroup
import com.karapp.drawguidance.content.image.ImageLook
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.data.model.CanvasKind

/**
 * v39 erişim hakkı anahtarları (sunucuyla aynı; değiştirilmemeli). v40: şekil ekleme (*.shapes_advanced) ve
 * diyagramlar (notes.diagrams_advanced) uygulamada artık kilitlenmez; anahtarlar sunucu planıyla uyum için durur.
 */
object FeatureKeys {
    const val ART_IMAGE_BG_REMOVAL = "art.image_bg_removal"
    const val ART_IMAGE_EFFECTS_ADVANCED = "art.image_effects_advanced"
    const val ART_SHAPE_SUGGEST_ADVANCED = "art.shape_suggest_advanced"
    const val ART_SHAPES_ADVANCED = "art.shapes_advanced"
    const val NOTES_IMAGE_BG_REMOVAL = "notes.image_bg_removal"
    const val NOTES_IMAGE_EFFECTS_ADVANCED = "notes.image_effects_advanced"
    const val NOTES_DIAGRAMS_ADVANCED = "notes.diagrams_advanced"
    const val NOTES_SHAPE_SUGGEST_ADVANCED = "notes.shape_suggest_advanced"
    const val COMIC_IMAGE_BG_REMOVAL = "comic.image_bg_removal"
    const val COMIC_EFFECTS_ADVANCED = "comic.effects_advanced"
    const val COMIC_CHARACTERS_ADVANCED = "comic.characters_advanced"
    const val COMIC_OBJECTS_SUGGEST_ADVANCED = "comic.objects_suggest_advanced"
    const val COMIC_EFFECTS = "comic.effects"
    const val COMIC_CHARACTERS = "comic.characters"
    const val COMIC_BACKGROUNDS = "comic.backgrounds"
    const val COMIC_TEMPLATES = "comic.templates"
    const val COMIC_SHAPES_ADVANCED = "comic.shapes_advanced"
    const val MODEL3D_SUGGEST = "model3d.suggest"
    /** v44 3B modeli GLB olarak dışa aktarma ve öteki uygulamalarla paylaşma. */
    const val MODEL3D_EXPORT = "model3d.export"
    /** v40 çocuk modu çizim görevinin son 15 seviyesi (36–50). */
    const val KIDS_CHALLENGE_ADVANCED = "kids.challenge_advanced"
    const val ADS_NONE = "ads.none"

    /** Premium'un bütün hakları (sıralı; abonelik sayfasında bu sırayla listelenir). */
    val PREMIUM: List<String> = listOf(
        ART_IMAGE_BG_REMOVAL, ART_IMAGE_EFFECTS_ADVANCED, ART_SHAPE_SUGGEST_ADVANCED, ART_SHAPES_ADVANCED,
        NOTES_IMAGE_BG_REMOVAL, NOTES_IMAGE_EFFECTS_ADVANCED, NOTES_DIAGRAMS_ADVANCED, NOTES_SHAPE_SUGGEST_ADVANCED,
        COMIC_IMAGE_BG_REMOVAL, COMIC_EFFECTS_ADVANCED, COMIC_CHARACTERS_ADVANCED, COMIC_OBJECTS_SUGGEST_ADVANCED,
        COMIC_EFFECTS, COMIC_CHARACTERS, COMIC_BACKGROUNDS, COMIC_TEMPLATES, COMIC_SHAPES_ADVANCED,
        MODEL3D_SUGGEST, MODEL3D_EXPORT, KIDS_CHALLENGE_ADVANCED, ADS_NONE,
    )

    /** Anahtarın modu (önekten). */
    fun modeOf(key: String): String = key.substringBefore('.')
}

/**
 * v39 kilitlenebilen özellik (moddan bağımsız ad); tuval türüne göre hangi erişim hakkını istediği [key] ile bulunur.
 * Basit modlar etkilenmez (null: kilit yok); çocuk modunda yalnız v40 çizim görevinin son seviyeleri kilitlenir.
 */
enum class PremiumFeature {
    IMAGE_BG_REMOVAL, IMAGE_EFFECTS_ADVANCED, SHAPE_SUGGEST_ADVANCED,
    COMIC_EFFECTS, COMIC_CHARACTERS, COMIC_CHARACTERS_ADVANCED, COMIC_BACKGROUNDS, COMIC_TEMPLATES, MODEL3D_SUGGEST,
    /** v44 3B modeli GLB olarak dışa aktarma ve paylaşma. */
    MODEL3D_EXPORT,
    /** v40 çocuk modu çizim görevinin Premium seviyeleri (tuval türünden bağımsız: Ana sayfadan da sorulur). */
    KIDS_CHALLENGE_ADVANCED;

    fun key(kind: CanvasKind?): String? = when (this) {
        IMAGE_BG_REMOVAL -> when (kind) {
            CanvasKind.ART -> FeatureKeys.ART_IMAGE_BG_REMOVAL
            CanvasKind.NOTES -> FeatureKeys.NOTES_IMAGE_BG_REMOVAL
            CanvasKind.COMIC -> FeatureKeys.COMIC_IMAGE_BG_REMOVAL
            else -> null
        }
        IMAGE_EFFECTS_ADVANCED -> when (kind) {
            CanvasKind.ART -> FeatureKeys.ART_IMAGE_EFFECTS_ADVANCED
            CanvasKind.NOTES -> FeatureKeys.NOTES_IMAGE_EFFECTS_ADVANCED
            CanvasKind.COMIC -> FeatureKeys.COMIC_EFFECTS_ADVANCED
            else -> null
        }
        SHAPE_SUGGEST_ADVANCED -> when (kind) {
            CanvasKind.ART -> FeatureKeys.ART_SHAPE_SUGGEST_ADVANCED
            CanvasKind.NOTES -> FeatureKeys.NOTES_SHAPE_SUGGEST_ADVANCED
            CanvasKind.COMIC -> FeatureKeys.COMIC_OBJECTS_SUGGEST_ADVANCED
            else -> null
        }
        COMIC_EFFECTS -> FeatureKeys.COMIC_EFFECTS.takeIf { kind == CanvasKind.COMIC }
        COMIC_CHARACTERS -> FeatureKeys.COMIC_CHARACTERS.takeIf { kind == CanvasKind.COMIC }
        COMIC_CHARACTERS_ADVANCED -> FeatureKeys.COMIC_CHARACTERS_ADVANCED.takeIf { kind == CanvasKind.COMIC }
        COMIC_BACKGROUNDS -> FeatureKeys.COMIC_BACKGROUNDS.takeIf { kind == CanvasKind.COMIC }
        COMIC_TEMPLATES -> FeatureKeys.COMIC_TEMPLATES.takeIf { kind == CanvasKind.COMIC }
        // 3B düzenleyici ayrı ekran: tuval türünden bağımsız
        MODEL3D_SUGGEST -> FeatureKeys.MODEL3D_SUGGEST
        MODEL3D_EXPORT -> FeatureKeys.MODEL3D_EXPORT
        KIDS_CHALLENGE_ADVANCED -> FeatureKeys.KIDS_CHALLENGE_ADVANCED
    }
}

/**
 * v39 "gelişmiş / karışık / karmaşık" kuralları. Hepsi var olan üst verilerden (sıra, grup, parça sayısı) hesaplanır;
 * kural dışında kalan her şey temeldir ve Free planda açıktır.
 *
 * - Resim efektleri: tek renk matrisiyle yapılan görünümler (Yok, Siyah-beyaz, Sepya, Ters, Eski, Soğuk, Sıcak) ve
 *   renk ayarları (parlaklık, karşıtlık, doygunluk, sıcaklık, ton) temel; komşu piksellerle çalışan ya da çok
 *   geçişli olanlar (Noir'dan sonrakiler; bulanıklık, keskinlik, vinyet, gren, solukluk) gelişmiş. Çizgi roman
 *   filtrelerinden "Çizgi roman" temel, ötekiler gelişmiş.
 * - Şekiller (Şekil ekle): v40 sahibin isteğiyle hepsi açık (kilit yok).
 * - Şekil önerileri: yerleşik geometrik şekle (daire, kare, yıldız…) dönüştürme temel; çok parçalı şablonlar (en az
 *   [ADVANCED_SUGGEST_PARTS] çizgi), kütüphane çizimleri ve 3B cisim önerileri gelişmiş (gösterilir, uygulanamaz).
 * - Diyagramlar: v40 sahibin isteğiyle hepsi açık (kilit yok).
 * - Çizgi roman efektleri: Aksiyon, Duygu ve Ses grupları temel; Büyü/enerji ve Atmosfer gelişmiş. Odak çizgilerinde
 *   ilk dört tür (yatay/dikey hız, odak, patlama) temel.
 * - Karakterler (v40 hazır resimler): yalnız sahibin seçtiği karakterler ([PREMIUM_CHARACTERS]: yeniçeriler, Moğol
 *   atlı, ajan, komando, samuraylar, buz büyücüsü, uzaylı büyücü, siborg, ateş yumruk, gezgin kılıççı, maskeli
 *   hırsız) gelişmiş; ötekiler temel. Önizleme hep açıktır; yalnız ekleme kilitlenir.
 * - Arka planlar: renk, desen ve hazır resimler temel; v40 hazır çizgi roman/manga arka planlarından Şehir, Sokak ve
 *   Doğa/Park temel, ötekiler (Okul, Gece/Yağmur, Kış, Yıkım, İç Mekan) gelişmiş ([FREE_BACKGROUND_CATEGORIES]).
 * - Şablonlar: Temel gruptaki en çok [BASIC_TEMPLATE_PANELS] panelli düzenler temel; webtoon, manga, türler ve daha
 *   çok panelli düzenler karmaşık.
 * - 3B mod: 3B şekil önerilerinin hepsi Premium (gösterilir, uygulanamaz).
 */
object PremiumRules {
    const val ADVANCED_SUGGEST_PARTS = 4
    const val BASIC_TEMPLATE_PANELS = 4

    /**
     * v44 Premium resim görünümleri: Noir ve Canlı ile Soğuk, Sıcak ve Eski fotoğraf. Parıltı, yağlı boya, karakalem,
     * kabartma, piksel, iki ton ve afiş Free.
     */
    val PREMIUM_LOOKS: Set<ImageLook> = setOf(ImageLook.NOIR, ImageLook.VIVID, ImageLook.COOL, ImageLook.WARM, ImageLook.VINTAGE)

    fun isAdvancedLook(look: ImageLook): Boolean = look in PREMIUM_LOOKS

    /** Gelişmiş ayar sürgüleri (ImageAdjust alan adları). v44 bulanıklık, keskinlik ve solukluk Free. */
    val ADVANCED_ADJUSTMENTS: Set<String> = setOf("vignette", "grain")

    /** v44 Free çizgi roman filtreleri: Çizgi roman ve Manga. */
    fun isAdvancedFilter(filter: com.karapp.drawguidance.content.comic.ComicFilter): Boolean =
        filter != com.karapp.drawguidance.content.comic.ComicFilter.COMIC && filter != com.karapp.drawguidance.content.comic.ComicFilter.MANGA

    /**
     * Şekil önerisi gelişmiş mi: [vector] öneride [solid] yerleşik geometrik şekilse temel; kütüphane çizimi, 3B cisim
     * ya da [parts] ≥ [ADVANCED_SUGGEST_PARTS] parçalı şablon gelişmiş.
     */
    fun isAdvancedSuggestion(vector: Boolean, solid: String?, parts: Int): Boolean {
        if (vector) return solid == null || solid.startsWith(ShapeLibrary.PREFIX)
        if (solid != null) return true
        return parts >= ADVANCED_SUGGEST_PARTS
    }

    fun isAdvancedEffectGroup(group: EffectGroup): Boolean = group == EffectGroup.MAGIC || group == EffectGroup.ATMOSPHERE

    fun isAdvancedLines(kind: ComicLinesKind): Boolean = kind.ordinal > ComicLinesKind.BURST.ordinal

    /** v40 Premium karakterler (katalog kimlikleri; sahibin listesi). Ötekilerin hepsi Free. */
    val PREMIUM_CHARACTERS: Set<String> = setOf(
        "yeniceri_osman", "tufekci_yeniceri", "mogol_atli", "ajan_nova", "komando_baris", "samuray_kenji", "samuray_aiko",
        "buz_buyucu_ayaz", "uzayli_buyucu", "siborg_kaya", "ates_yumruk", "gezgin_kilicci", "maskeli_hirsiz",
        // v44 kovboy, genç büyücü, elf okçu (samuraylar zaten listede)
        "kovboy_riza", "buyucu_efe", "elf_okcu_silvan",
        // v46 sahibin karakterler.zip "premium" klasöründeki anime karakterleri
        "elf_buyucu_erkek", "elf_buyucu_kadin", "suikastci_kadin", "suikastci_erkek", "kutsal_sovalye_kadin",
        "kutsal_sovalye_erkek", "orman_okcusu_erkek", "orman_okcusu_kadin", "olu_cagiran_erkek", "olu_cagiran_kadin",
    )

    /** v40 Free planda açık arka plan alt kategorileri (Şehir, Sokak, Doğa/Park). */
    val FREE_BACKGROUND_CATEGORIES: Set<String> = setOf("sehir", "sokak", "doga_park")

    /** v40 karakteri eklemeyi kilitleyen özellik (yoksa null: temel karakter). */
    fun characterFeature(c: com.karapp.drawguidance.data.comicassets.ComicCharacter): PremiumFeature? =
        if (c.id in PREMIUM_CHARACTERS || c.id in UPCOMING_PREMIUM_CHARACTERS) PremiumFeature.COMIC_CHARACTERS_ADVANCED else null

    /**
     * v46 elf büyücü Lunara kaldırıldı; yerine erkek ve kapalı giysili kadın elf büyücü geldi (Premium listesinde).
     * İkinci erkek elf büyücünün sayfası henüz gelmedi: kimliği şimdiden Premium (gelince `tools/comic_assets` ile eklenir).
     */
    val UPCOMING_PREMIUM_CHARACTERS: Set<String> = setOf("elf_buyucu_erkek_2")

    /** v40 hazır arka planı kilitleyen özellik (yoksa null). */
    fun backgroundFeature(b: com.karapp.drawguidance.data.comicassets.ComicBackground): PremiumFeature? =
        if (b.category in FREE_BACKGROUND_CATEGORIES || b.id in FREE_BACKGROUNDS) null else PremiumFeature.COMIC_BACKGROUNDS

    /** v44 kategorisi Premium olsa da Free olan mekânlar: terk edilmiş fabrika, yıkık bina merdiveni, kutup araştırma üssü, okul çatısı. */
    val FREE_BACKGROUNDS: Set<String> = setOf("fabrika", "fabrika_m", "yikik_merdiven", "kutup_us", "kutup_us_m", "okul_cati")

    /** v44 kategorisi Premium olsa da Free olan nesneler (bilim kurgu, askeri ve fantastik temalarından 4'er). */
    val FREE_OBJECTS: Set<String> = setOf(
        "it_scifi_01", "it_scifi_02", "it_scifi_03", "it_scifi_04",
        "it_armory_01", "it_armory_02", "it_armory_04", "it_armory_05",
        "it_magic_01", "it_magic_02", "it_magic_03", "it_magic_04",
    )

    /** v44 kategorisi Premium olsa da Free olan efektler (portal ve büyü, enerji ve aura, atış: 2'şer). */
    val FREE_EFFECTS: Set<String> = setOf("fx_c0_09", "fx_c0_10", "fx_c0_01", "fx_c0_02", "fx_c5_30", "fx_c5_31")

    /** v42 Free planda açık hazır efekt resmi kategorileri (ötekiler Premium "Gelişmiş efektler"). */
    val FREE_EFFECT_CATEGORIES: Set<String> = setOf("patlama", "duman", "simge", "isik")

    /** v42 Free planda açık hazır nesne kategorileri. */
    val FREE_OBJECT_CATEGORIES: Set<String> = setOf("belge", "mobilya", "yiyecek", "isik", "teknoloji")

    /** v42 hazır efekt/nesne resmini kilitleyen özellik (yoksa null). */
    fun propFeature(kind: com.karapp.drawguidance.data.comicassets.ComicAssetKind, p: com.karapp.drawguidance.data.comicassets.ComicProp): PremiumFeature? {
        val effect = kind == com.karapp.drawguidance.data.comicassets.ComicAssetKind.EFFECT
        val free = if (effect) FREE_EFFECT_CATEGORIES else FREE_OBJECT_CATEGORIES
        val freeIds = if (effect) FREE_EFFECTS else FREE_OBJECTS
        return if (p.category in free || p.id in freeIds) null else PremiumFeature.COMIC_EFFECTS
    }

    /** v44 çizgi roman şablonlarının hepsi Free. */
    @Suppress("UNUSED_PARAMETER")
    fun isAdvancedTemplate(t: ComicTemplate): Boolean = false
}
