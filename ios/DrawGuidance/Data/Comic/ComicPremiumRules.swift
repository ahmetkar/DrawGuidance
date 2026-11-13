import Foundation

/// v40–v46 çizgi roman hazır resimlerinin Premium kuralları (Android `PremiumRules` içindeki karakter, mekân, efekt ve
/// nesne bölümleri). Önizleme Free planda da açık; kilitli olanı eklemek "Premium gerekli" penceresini açar.
enum ComicPremiumRules {
    /// v40 Premium karakterler (katalog kimlikleri). Ötekilerin hepsi Free.
    static let PREMIUM_CHARACTERS: Set<String> = [
        "yeniceri_osman", "tufekci_yeniceri", "mogol_atli", "ajan_nova", "komando_baris", "samuray_kenji", "samuray_aiko",
        "buz_buyucu_ayaz", "uzayli_buyucu", "siborg_kaya", "ates_yumruk", "gezgin_kilicci", "maskeli_hirsiz",
        "kovboy_riza", "buyucu_efe", "elf_okcu_silvan",
        "elf_buyucu_erkek", "elf_buyucu_kadin", "suikastci_kadin", "suikastci_erkek", "kutsal_sovalye_kadin",
        "kutsal_sovalye_erkek", "orman_okcusu_erkek", "orman_okcusu_kadin", "olu_cagiran_erkek", "olu_cagiran_kadin",
    ]
    /// v46 henüz gelmemiş ama şimdiden Premium olan karakter.
    static let UPCOMING_PREMIUM_CHARACTERS: Set<String> = ["elf_buyucu_erkek_2"]
    /// v40 Free planda açık arka plan alt kategorileri (Şehir, Sokak, Doğa/Park).
    static let FREE_BACKGROUND_CATEGORIES: Set<String> = ["sehir", "sokak", "doga_park"]
    /// v44 kategorisi Premium olsa da Free olan mekânlar.
    static let FREE_BACKGROUNDS: Set<String> = ["fabrika", "fabrika_m", "yikik_merdiven", "kutup_us", "kutup_us_m", "okul_cati"]
    static let FREE_OBJECTS: Set<String> = [
        "it_scifi_01", "it_scifi_02", "it_scifi_03", "it_scifi_04",
        "it_armory_01", "it_armory_02", "it_armory_04", "it_armory_05",
        "it_magic_01", "it_magic_02", "it_magic_03", "it_magic_04",
    ]
    static let FREE_EFFECTS: Set<String> = ["fx_c0_09", "fx_c0_10", "fx_c0_01", "fx_c0_02", "fx_c5_30", "fx_c5_31"]
    static let FREE_EFFECT_CATEGORIES: Set<String> = ["patlama", "duman", "simge", "isik"]
    static let FREE_OBJECT_CATEGORIES: Set<String> = ["belge", "mobilya", "yiyecek", "isik", "teknoloji"]

    static func characterFeature(_ c: ComicCharacter) -> PremiumFeature? {
        PREMIUM_CHARACTERS.contains(c.id) || UPCOMING_PREMIUM_CHARACTERS.contains(c.id) ? .COMIC_CHARACTERS_ADVANCED : nil
    }

    static func backgroundFeature(_ b: ComicBackground) -> PremiumFeature? {
        FREE_BACKGROUND_CATEGORIES.contains(b.category) || FREE_BACKGROUNDS.contains(b.id) ? nil : .COMIC_BACKGROUNDS
    }

    static func propFeature(_ kind: ComicAssetKind, _ p: ComicProp) -> PremiumFeature? {
        let effect = kind == .EFFECT
        let free = effect ? FREE_EFFECT_CATEGORIES : FREE_OBJECT_CATEGORIES
        let freeIds = effect ? FREE_EFFECTS : FREE_OBJECTS
        return free.contains(p.category) || freeIds.contains(p.id) ? nil : .COMIC_EFFECTS
    }
}
