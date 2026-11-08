package com.karapp.drawguidance.notify

/** v40 güncelleme denetiminin kararı. */
enum class UpdateDecision {
    /** Bir şey yapılmaz (güncel, bilinmiyor ya da bu sürüm için zaten bildirildi). */
    NONE,
    /** Yeni sürüm var: bildirim (Play Store sayfası). */
    NOTIFY,
    /** Yüklü sürüm desteklenmiyor: açılışta kapatılamayan güncelleme penceresi. */
    FORCE,
}

/**
 * v40 bildirim kararları (saf, birim testli; işçiler ve açılış bunları kullanır).
 *
 * - **Uzun süre kullanılmama**: uygulama [INACTIVITY_DAYS] (3, 7, 14) gün açılmazsa sırayla birer hatırlatma; üçüncüden
 *   sonra durur. Uygulama açılınca sayaç sıfırlanır.
 * - **Güncelleme**: yüklü sürüm desteklenen en eskiden küçükse zorunlu; en yeni sürüm yüklüden büyükse ve bu sürüm için
 *   daha önce bildirilmediyse bildirim.
 */
object EngagementRules {
    val INACTIVITY_DAYS = listOf(3, 7, 14)
    const val DAY_MS = 24 * 3_600_000L

    /**
     * Gönderilecek hatırlatmanın sırası (1, 2, 3) ya da null. [sent]: son açılıştan beri gönderilen hatırlatma sayısı.
     * Bir günde birden çok eşik geçilmişse (cihaz uzun süre kapalı kaldı) yalnız bir hatırlatma gider.
     */
    fun inactivityReminderDue(lastOpenedAt: Long, now: Long, sent: Int): Int? {
        if (lastOpenedAt <= 0 || sent >= INACTIVITY_DAYS.size) return null
        val days = (now - lastOpenedAt) / DAY_MS
        return if (days >= INACTIVITY_DAYS[sent]) sent + 1 else null
    }

    fun update(installed: Long, latest: Long, minSupported: Long, lastNotified: Long): UpdateDecision = when {
        minSupported > 0 && installed < minSupported -> UpdateDecision.FORCE
        latest > installed && latest > lastNotified -> UpdateDecision.NOTIFY
        else -> UpdateDecision.NONE
    }
}
