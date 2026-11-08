package com.karapp.drawguidance.content.shapes

/**
 * v30 karmaşıklığa göre algoritma seçimi. v38: şekil önerisi yalnız birbirine yakın 4+ elemanda yapıldığı için mod
 * ağları 4+ elemanlı çizimlerle yeniden eğitildi ve yalnız 4+ elemanda çalışır ([NET_MIN_ELEMENTS] = 4; tek çizgi ve
 * 2–3 elemanda öneri uygulamada çizgi önerisidir, bkz. [SuggestRules]). Her çizim, gerektirdiği kadar algoritmayla tanınır; pahalı olanlar yalnız
 * gerekince çalışır (giriş düzeyi cihazlarda da akıcı):
 *
 * | Kademe | Çizim | Algoritmalar |
 * |---|---|---|
 * | [Tier.PRIMITIVE] | tek çizgi, düzgün geometri | geometrik uydurma (doğru, çember, elips, çokgen; en küçük kareler ve köşe bulma) |
 * | [Tier.SINGLE] | tek çizgi | yerelliğe duyarlı karma (LSH) → kosinüs kaba tarama → kNN → yön dizisi (DTW benzeri), chamfer, $Q, IDM |
 * | [Tier.FEW] | 2–3 çizgi | yukarıdakiler + parça grafiği (parçaların yeri ve ilişkisi) |
 * | [Tier.MANY] | 4+ çizgi | yukarıdakiler + modun yapay sinir ağı (aday ekler ve olasılığı yeniden sıralamaya katar) |
 *
 * Yapay sinir ağı yalnız çok elemanlı (v38: 4+ çizgili) çizimlerde çalışır ve yalnız bunlarla eğitilir; az elemanlı
 * çizimlerde geri bildirim yalnız modun kabul/ret sayaçlarına (önsel) yazılır.
 */
object ShapeComplexity {
    /** Sinir ağı bu kadar ya da daha çok elemanlı (çizgili) çizimlerde kullanılır (v38: 3 → 4, [SuggestRules.SHAPE_MIN]). */
    const val NET_MIN_ELEMENTS = SuggestRules.SHAPE_MIN

    enum class Tier { PRIMITIVE, SINGLE, FEW, MANY }

    fun tier(elements: Int, primitive: Boolean = false): Tier = when {
        elements <= 1 && primitive -> Tier.PRIMITIVE
        elements <= 1 -> Tier.SINGLE
        elements < NET_MIN_ELEMENTS -> Tier.FEW
        else -> Tier.MANY
    }

    fun usesNet(elements: Int): Boolean = elements >= NET_MIN_ELEMENTS

    /** Kademede çalışan algoritmalar (ayarlar ekranında ve belgede gösterilir). */
    fun algorithms(tier: Tier): List<String> = when (tier) {
        Tier.PRIMITIVE -> listOf("Geometrik uydurma")
        Tier.SINGLE -> listOf("LSH", "Kosinüs", "kNN", "Yön dizisi", "Chamfer", "\$Q", "IDM")
        Tier.FEW -> listOf("LSH", "Kosinüs", "kNN", "Chamfer", "\$Q", "IDM", "Parça grafiği")
        Tier.MANY -> listOf("LSH", "Kosinüs", "kNN", "Chamfer", "\$Q", "IDM", "Parça grafiği", "Sinir ağı")
    }
}
