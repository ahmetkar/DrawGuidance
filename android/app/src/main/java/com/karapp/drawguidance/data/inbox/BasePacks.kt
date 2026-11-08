package com.karapp.drawguidance.data.inbox

import com.karapp.drawguidance.data.comicassets.ComicCatalog

/**
 * v43 taban paketler (`base-comic`, `base-notes`, `base-art`): uygulamanın içinde gelen pencere resimlerinin sunucudaki
 * kopyası. Cihaz bunları kurulu sayar (sürüm 1). Sunucuda güncellenip yeni sürüm inerse yalnız **uygulamada olmayan**
 * öğeler pencerelere eklenir (aynı resim iki kez görünmez).
 *
 * Öğe kimlikleri sunucuya yüklenen listeyle aynı kuralla üretilir ([itemId]; bkz. testlerdeki V43BasePackExport).
 */
object BasePacks {
    fun isBase(packId: String): Boolean = PackStore.BASE.any { it.first == packId }

    /** Uygulamadaki kimlikten paket öğe kimliği. */
    fun itemId(raw: String): String = raw.lowercase().replace(Regex("[^a-z0-9_\\-]"), "-").trim('-').take(80)

    /** Uygulamayla gelen çizgi roman kataloğunun paket öğe kimlikleri (nesne, efekt, mekân, karakter). */
    fun comicKnown(c: ComicCatalog): Set<String> = buildSet {
        c.objects.forEach { add(itemId(it.id)) }
        c.effects.forEach { add(itemId(it.id)) }
        c.backgrounds.forEach { add(itemId(it.id)) }
        c.characters.forEach { ch -> ch.variants.forEach { v -> add(itemId(ch.id + "-" + v.id)) } }
    }
}
