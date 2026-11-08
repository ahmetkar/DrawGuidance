package com.karapp.drawguidance.ui.drawing

import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.i18n.I18n

/**
 * v36 Şekil Ekle penceresinin süzgeçleri (arayüzden bağımsız):
 * - [Index] ada göre arama: Türkçe ad, etkin dildeki adı, kütüphane etiketleri ve kategori adı. Büyük/küçük harf ve
 *   Türkçe harf farkları (ı/i, ş/s …) gözetilmez; birden çok sözcük yazılırsa hepsi aranır.
 * v43 nesne çizimleri (ve stil süzgeci) Şekil ekle penceresinden tamamen kaldırıldı.
 */
object ShapePickerFilter {
    /** Aramada karşılaştırılan biçim: küçük harf, Türkçe ve şapkalı harfler sade (başlık aramasıyla aynı kural). */
    fun fold(s: String): String = com.karapp.drawguidance.ui.components.TitleSearch.key(s)

    /**
     * Arama dizini: her şeklin aranan metni bir kez hazırlanır. [label] şeklin ekrandaki adı (yerleşik şekillerde
     * string kaynağı), [categoryLabel] kategorinin ekrandaki adı.
     */
    class Index(
        categories: List<ShapeRegistry.PickerCategory>,
        label: (String) -> String,
        categoryLabel: (ShapeRegistry.PickerCategory) -> String,
    ) {
        private val categories = categories
        private val text: Map<String, String>

        init {
            val map = HashMap<String, String>(categories.sumOf { it.items.size } * 2)
            for (c in categories) {
                val cat = buildString {
                    append(categoryLabel(c)).append(' ').append(c.title).append(' ').append(I18n.tr(c.title))
                }
                for (id in c.items) {
                    if (id in map) continue
                    val raw = buildString {
                        append(label(id)).append(' ')
                        ShapeRegistry.title(id)?.let { append(it).append(' ').append(I18n.tr(it)).append(' ') }
                        if (ShapeRegistry.isLibrary(id)) ShapeLibrary.find(id)?.tags?.forEach { append(it).append(' ') }
                        append(cat)
                    }
                    map[id] = fold(raw)
                }
            }
            text = map
        }

        /** Aranan bütün sözcükleri içeren şekiller, kategorileriyle (sıra korunur). */
        fun search(query: String): List<ShapeRegistry.PickerCategory> {
            val words = fold(query).split(' ', '\t').filter { it.isNotBlank() }
            if (words.isEmpty()) return categories
            val seen = HashSet<String>()
            return categories.mapNotNull { c ->
                val items = c.items.filter { id -> val t = text[id] ?: ""; words.all { t.contains(it) } && seen.add(id) }
                if (items.isEmpty()) null else ShapeRegistry.PickerCategory(c.id, c.builtin, c.title, c.section, items)
            }
        }
    }
}
