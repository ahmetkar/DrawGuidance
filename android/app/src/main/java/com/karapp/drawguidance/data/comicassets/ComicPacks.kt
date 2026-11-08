package com.karapp.drawguidance.data.comicassets

import com.karapp.drawguidance.data.inbox.PackStore

/**
 * v43 sunucudan inmiş çizgi roman paketlerinin ([PackStore]) hazır pencerelere eklenmesi: nesneler, efektler, mekân
 * arka planları ve karakterler kendi pencerelerinde, paketin adını taşıyan kategoride görünür. Dosya yolları mutlaktır
 * ([ComicAssets.opener] dosyadan açar). Diyalog kutuları ve şekiller "Paket içerikleri" penceresindedir.
 */
object ComicPacks {
    const val PREFIX = "pk."
    const val CATEGORY_PREFIX = "pk_"
    private val LANGS = listOf("tr", "en", "zh", "ja", "ru", "ko", "ar")

    /**
     * Paketlerden ek katalog. Taban paketlerin ([com.karapp.drawguidance.data.inbox.BasePacks]) uygulamada zaten olan
     * öğeleri ([known]) atlanır.
     */
    fun extras(packs: List<PackStore.Pack>, known: Set<String> = emptySet()): ComicCatalog {
        val effects = ArrayList<ComicProp>()
        val objects = ArrayList<ComicProp>()
        val effectCats = LinkedHashMap<String, ComicBgCategory>()
        val objectCats = LinkedHashMap<String, ComicBgCategory>()
        val bgCats = LinkedHashMap<String, ComicBgCategory>()
        val backgrounds = ArrayList<ComicBackground>()
        val characters = ArrayList<ComicCharacter>()
        for (p in packs.filter { it.mode.equals("COMIC", ignoreCase = true) }) {
            val base = com.karapp.drawguidance.data.inbox.BasePacks.isBase(p.id)
            for (item in p.manifest.items) {
                if (base && (item.id in known || item.id in REMOVED_ITEMS)) continue
                val path = p.file(item).absolutePath
                val id = PREFIX + p.id + "." + item.id
                val label = item.category?.takeIf { it.isNotBlank() } ?: p.manifest.name
                val cat = CATEGORY_PREFIX + slug(label)
                val names = LANGS.associateWith { label }
                when (item.window) {
                    "effects" -> {
                        effectCats.getOrPut(cat) { ComicBgCategory(cat, names) }
                        effects += ComicProp(id, listOf(ComicStyle.MANGA.id, ComicStyle.COMIC.id), cat, emptyList(), path, path, item.w, item.h)
                    }
                    "objects" -> {
                        objectCats.getOrPut(cat) { ComicBgCategory(cat, names) }
                        objects += ComicProp(id, listOf(ComicStyle.MANGA.id, ComicStyle.COMIC.id), cat, emptyList(), path, path, item.w, item.h)
                    }
                    "backgrounds" -> {
                        bgCats.getOrPut(cat) { ComicBgCategory(cat, names) }
                        val style = if (label.contains("manga", ignoreCase = true)) ComicStyle.MANGA.id else ComicStyle.COMIC.id
                        backgrounds += ComicBackground(id, style, cat, LANGS.associateWith { item.title }, path, path, item.w, item.h)
                    }
                    "characters" -> {
                        val style = if (label.contains("manga", ignoreCase = true)) ComicStyle.MANGA.id else ComicStyle.COMIC.id
                        characters += ComicCharacter(
                            id, style, CATEGORY_PREFIX + slug(p.manifest.name), LANGS.associateWith { item.title },
                            listOf(ComicVariant(id + ".v", VariantGroup.POSE.id, path, path, item.w, item.h)),
                        )
                    }
                }
            }
        }
        return ComicCatalog(
            characters = characters, bgCategories = bgCats.values.toList(), backgrounds = backgrounds,
            effectCategories = effectCats.values.toList(), effects = effects, objectCategories = objectCats.values.toList(), objects = objects,
        )
    }

    /** Uygulamayla gelen katalog + paketler (kategoriler sona eklenir). */
    /**
     * v46 kaldırılan karakterler: eski sunucudan inmiş bir paket getirse de katalogda görünmez (elf büyücü Lunara;
     * yerine gelecek üç elf büyücü Premium, bkz. PremiumRules.UPCOMING_PREMIUM_CHARACTERS).
     */
    val REMOVED_CHARACTERS: Set<String> = setOf("elf_lunara")

    /**
     * v48 taban paketten gelmeyecek öğeler (uygulamadan kaldırıldı; sunucudaki eski paket listesi getirse de eklenmez):
     * beyaz tulumlu astronotun bozuk ilk pozu.
     */
    val REMOVED_ITEMS: Set<String> = setOf("astronot_kaan-poz_01")

    fun merge(base: ComicCatalog, extra: ComicCatalog): ComicCatalog {
        if (extra.effects.isEmpty() && extra.objects.isEmpty() && extra.backgrounds.isEmpty() && extra.characters.isEmpty()) return base
        return base.copy(
            characters = base.characters + extra.characters.filter { it.id !in REMOVED_CHARACTERS },
            bgCategories = base.bgCategories + extra.bgCategories.filter { c -> base.bgCategories.none { it.id == c.id } },
            backgrounds = base.backgrounds + extra.backgrounds,
            effectCategories = base.effectCategories + extra.effectCategories.filter { c -> base.effectCategories.none { it.id == c.id } },
            effects = base.effects + extra.effects,
            objectCategories = base.objectCategories + extra.objectCategories.filter { c -> base.objectCategories.none { it.id == c.id } },
            objects = base.objects + extra.objects,
        )
    }

    fun slug(s: String): String =
        s.lowercase().map { ch -> when (ch) { 'ç' -> 'c'; 'ğ' -> 'g'; 'ı' -> 'i'; 'ö' -> 'o'; 'ş' -> 's'; 'ü' -> 'u'; else -> ch } }
            .joinToString("").replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "paket" }.take(40)
}
