package com.karapp.drawguidance.ui.components

import com.karapp.drawguidance.platform.DeviceCaps

/**
 * v40 açılış (karşılama) ekranında logonun çizilmesi ve başlığın yazılması cihazın hızına göre: hızlı cihazda olabildiğince
 * kısa, orta cihazda biraz kısa, yavaş cihazda hafif (ölçek canlandırması ve imleç yanıp sönmesi yok, harfler küçük
 * gruplarla) ve kısa. Hız anlaşılamazsa (cihaz sınıfı bilinmiyor, ilk kareler ölçülemedi) normal süreler kullanılır.
 */
data class SplashTiming(
    /** Logonun kendini çizme süresi (ms). */
    val logoMs: Int,
    /** Başlık yazılmaya başlamadan önce (ms). */
    val leadMs: Long,
    /** Harf (ya da [chunk] harflik grup) başına (ms). */
    val charMs: Long,
    /** Boşluktan sonra (ms). */
    val spaceMs: Long,
    /** Yazı bitince imlecin yanıp sönmesi (kapanmadan önce, toplam ms; [light]'ta imleç sabit bekler). */
    val holdMs: Long,
    /** Ekranın solarak kapanması (ms). */
    val fadeMs: Int,
    /** Bir adımda yazılan harf sayısı (yavaş cihazda daha az kare). */
    val chunk: Int = 1,
    /** Hafif canlandırma: logo ölçeklenmez, imleç yanıp sönmez. */
    val light: Boolean = false,
) {
    /** [title] için toplam süre (logo ile yazı aynı anda başlar; kapanma dahil). */
    fun totalMs(title: String): Long {
        var typing = 0L
        var i = 0
        while (i < title.length) {
            val end = (i + chunk).coerceAtMost(title.length)
            typing += if (title[end - 1] == ' ') spaceMs else charMs
            i = end
        }
        return maxOf(logoMs.toLong(), leadMs + typing + holdMs) + fadeMs
    }

    companion object {
        /** v31'den beri süren normal canlandırma (hız bilinmiyorken). */
        val Normal = SplashTiming(logoMs = 850, leadMs = 380, charMs = 58, spaceMs = 90, holdMs = 700, fadeMs = 260)
        /** Hızlı cihaz: olabildiğince kısa. */
        val Fast = SplashTiming(logoMs = 450, leadMs = 150, charMs = 22, spaceMs = 30, holdMs = 340, fadeMs = 180)
        /** Orta cihaz. */
        val Quick = SplashTiming(logoMs = 600, leadMs = 240, charMs = 36, spaceMs = 55, holdMs = 510, fadeMs = 220)
        /** Yavaş cihaz: hafif ve kısa (az kare). */
        val Light = SplashTiming(logoMs = 500, leadMs = 150, charMs = 70, spaceMs = 70, holdMs = 250, fadeMs = 160, chunk = 3, light = true)

        /** İlk karelerin ortalaması bundan uzunsa (ms; ~25 fps altı) cihaz yavaş sayılır. */
        const val SLOW_FRAME_MS = 40f

        /** Ölçülen kare aralıkları (ms) bundan uzunsa (ölçüm takıldı, ör. arka plana alındı) geçersiz sayılır. */
        const val MAX_VALID_FRAME_MS = 1000f

        /**
         * Cihaz sınıfına ([tier]; null: bilinmiyor) ve ilk karelerin ortalama süresine ([frameMs]; null: ölçülemedi)
         * göre süreler. Yalnız kare süresi bilinirken cihaz hızlandırılmaz (ilk kareler her cihazda biraz yavaştır),
         * yalnızca çok yavaşsa hafif canlandırmaya geçilir.
         */
        fun select(tier: DeviceCaps.Tier?, frameMs: Float?): SplashTiming {
            val frame = frameMs?.takeIf { it.isFinite() && it > 0f && it <= MAX_VALID_FRAME_MS }
            val slowFrames = frame != null && frame > SLOW_FRAME_MS
            return when (tier) {
                DeviceCaps.Tier.LOW -> Light
                DeviceCaps.Tier.MID -> if (slowFrames) Light else Quick
                // Güçlü cihazda yalnız çok yavaş kareler (ör. ısınma, güç tasarrufu) hafife düşürür
                DeviceCaps.Tier.HIGH -> if (frame != null && frame > SLOW_FRAME_MS * 1.25f) Light else Fast
                null -> if (slowFrames) Light else Normal
            }
        }

        /** Kare zamanlarından (ns, artan) ortalama kare süresi (ms); en az iki zaman yoksa null. */
        fun averageFrameMs(frameNanos: List<Long>): Float? {
            if (frameNanos.size < 2) return null
            val span = frameNanos.last() - frameNanos.first()
            if (span <= 0L) return null
            return span / 1_000_000f / (frameNanos.size - 1)
        }
    }
}
