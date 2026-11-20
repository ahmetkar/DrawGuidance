package com.karapp.drawguidance.data.model

/**
 * v56 2C-1 canlı yayının fark paketi (saf; Android ve iOS'ta aynı kural): bir sayfanın önceki ve yeni hali arasındaki
 * fark (eklenen ya da değişen çizgiler, silinen kimlikler; sıra değiştiyse bütün kimliklerin sırası) ve farkın
 * uygulanması. `apply(prev, diff(prev, next)) == next`.
 */
object LiveDiff {
    /** Tam pakete dönme sıklığı (kare). */
    const val FULL_EVERY = 20

    data class Diff(
        /** Eklenen ya da değişen çizgiler (yeni sırayla). */
        val upserts: List<Stroke>,
        val removed: List<String>,
        /** Kalan çizgilerin göreli sırası değiştiyse yeni sayfanın bütün kimlikleri; değişmediyse null. */
        val order: List<String>?,
    ) {
        val isEmpty: Boolean get() = upserts.isEmpty() && removed.isEmpty() && order == null
    }

    fun diff(prev: List<Stroke>, next: List<Stroke>): Diff {
        val before = prev.associateBy { it.id }
        val nextIds = next.mapTo(HashSet()) { it.id }
        val upserts = next.filter { s -> before[s.id] != s }
        val removed = prev.filter { it.id !in nextIds }.map { it.id }
        val order = if (apply(prev, Diff(upserts, removed, null)).map { it.id } == next.map { it.id }) null else next.map { it.id }
        return Diff(upserts, removed, order)
    }

    /**
     * Fark uygulanır: silinenler çıkar, değişenler yerinde güncellenir, yeniler (aralarındaki sırayla) yeni sayfadaki
     * komşularına göre yerleşir: [Diff.order] varsa ona göre, yoksa sona eklenir.
     */
    fun apply(page: List<Stroke>, d: Diff): List<Stroke> {
        val removed = d.removed.toHashSet()
        val changed = d.upserts.associateBy { it.id }
        val kept = page.filter { it.id !in removed }.map { changed[it.id] ?: it }
        val known = kept.mapTo(HashSet()) { it.id }
        val added = d.upserts.filter { it.id !in known }
        val merged = kept + added
        val order = d.order ?: return merged
        val byId = merged.associateBy { it.id }
        val out = order.mapNotNull { byId[it] }
        // Sırada adı geçmeyen (beklenmedik) çizgiler kaybolmasın
        val listed = order.toHashSet()
        return out + merged.filter { it.id !in listed }
    }

    /**
     * Fark paketi yeterli mi: sayfa sayısı, açık sayfa, katmanlar, boyut, ad, tür, kılavuz ve arka planlar aynıysa
     * değişen tek sayfanın sırası (-1: değişiklik yok); başka bir şey değiştiyse ya da birden çok sayfa değiştiyse null
     * (tam paket).
     */
    fun changedPage(prev: CanvasUi, next: CanvasUi): Int? {
        if (prev.localId != next.localId || prev.name != next.name || prev.width != next.width || prev.height != next.height ||
            prev.backgroundColorHex != next.backgroundColorHex || prev.kind != next.kind || prev.currentPage != next.currentPage ||
            prev.guide != next.guide || prev.layers != next.layers || prev.background != next.background ||
            prev.pageBackgrounds != next.pageBackgrounds || prev.pageSettings != next.pageSettings || prev.pages.size != next.pages.size
        ) return null
        var changed = -1
        for (i in next.pages.indices) {
            if (prev.pages[i] == next.pages[i]) continue
            if (changed >= 0) return null
            changed = i
        }
        return changed
    }
}
