package com.karapp.drawguidance.data.billing

import com.karapp.drawguidance.data.local.PlanFeatureEntity
import com.karapp.drawguidance.data.local.SubscriptionEntity

/** v39 plan kimlikleri. */
object PlanIds {
    const val FREE = "free"
    const val PREMIUM = "premium"
}

/** v39 abonelik durumları (sunucunun SubscriptionStatus'u; abonelik yoksa NONE). */
object SubscriptionStatuses {
    const val ACTIVE = "ACTIVE"
    const val IN_GRACE_PERIOD = "IN_GRACE_PERIOD"
    const val CANCELED = "CANCELED"
    const val ON_HOLD = "ON_HOLD"
    const val PAUSED = "PAUSED"
    const val PENDING = "PENDING"
    const val EXPIRED = "EXPIRED"
    const val NONE = "NONE"

    /**
     * Bitiş zamanı geçmemişse hak veren durumlar: etkin, ek süre ve iptal edilmiş ama ödenen dönemi süren (Play'de
     * iptal yenilemeyi durdurur, dönem sonuna kadar hak sürer).
     */
    val GRANTING = setOf(ACTIVE, IN_GRACE_PERIOD, CANCELED)

    /** "SUBSCRIPTION_STATE_ACTIVE" gibi Play adları da kabul edilir. */
    fun normalize(raw: String?): String = raw?.trim()?.uppercase()?.removePrefix("SUBSCRIPTION_STATE_")?.ifEmpty { null } ?: NONE
}

/**
 * v39 kullanıcının o anki erişim hakları. Kapalı kalacak şekilde hesaplanır ([compute]): Premium hakları yalnız
 * giriş yapılmışken, önbellekte hak veren durumda ([SubscriptionStatuses.GRANTING]), bitiş zamanı bilinen ve
 * geçmemiş bir Premium aboneliği varsa açılır. Bilinmeyen durum, bitiş zamanı olmayan kayıt, çıkış yapılmış
 * kullanıcı ya da yüklenmemiş veri: Free.
 *
 * [loaded]: veritabanı okundu mu (reklam kararı yalnız yüklendikten sonra verilir; Premium kullanıcıya açılışta
 * yanlışlıkla reklam gösterilmesin).
 */
data class Entitlements(
    val planId: String = PlanIds.FREE,
    val status: String = SubscriptionStatuses.NONE,
    val features: Set<String> = emptySet(),
    val expiryTime: Long? = null,
    val autoRenewing: Boolean = false,
    val signedIn: Boolean = false,
    val loaded: Boolean = false,
) {
    val isPremium: Boolean get() = planId != PlanIds.FREE && features.isNotEmpty()

    /** Hak var mı (bitiş zamanı geçtiyse hayır: değer eski kalsa da kapanır). */
    fun has(key: String, now: Long = System.currentTimeMillis()): Boolean {
        if (key !in features) return false
        val end = expiryTime ?: return false
        return now < end
    }

    /** Reklam gösterilmez (Premium). */
    fun adFree(now: Long = System.currentTimeMillis()): Boolean = has(FeatureKeys.ADS_NONE, now)

    companion object {
        val FREE = Entitlements(loaded = true)

        /** v50 son sunucu doğrulamasından sonra çevrimdışı hakkın en uzun süresi (7 gün). */
        const val OFFLINE_GRACE_MS: Long = 7L * 24 * 60 * 60 * 1000

        /** v50 cihaz saati ileri alınmışsa: doğrulama zamanı "gelecekte" görünen kayıt (1 gün tolerans) hak vermez. */
        const val CLOCK_SKEW_MS: Long = 24L * 60 * 60 * 1000

        /** Testler için: bütün Premium hakları (bitiş [until]). */
        fun premium(until: Long = Long.MAX_VALUE, signedIn: Boolean = true) = Entitlements(
            planId = PlanIds.PREMIUM, status = SubscriptionStatuses.ACTIVE, features = FeatureKeys.PREMIUM.toSet(),
            expiryTime = until, autoRenewing = true, signedIn = signedIn, loaded = true,
        )

        /**
         * Önbellekten hesaplama. [signedInUid]: cihazda giriş yapmış kullanıcının Firebase kimliği (yoksa null → Free).
         * [planFeatures]: planların hakları (sunucu hak listesi vermediyse kullanılır).
         */
        fun compute(
            signedInUid: String?,
            subscription: SubscriptionEntity?,
            planFeatures: List<PlanFeatureEntity>,
            now: Long,
        ): Entitlements {
            if (signedInUid == null) return FREE
            val sub = subscription?.takeIf { it.firebaseUid == signedInUid }
                ?: return Entitlements(signedIn = true, loaded = true)
            val status = SubscriptionStatuses.normalize(sub.status)
            val base = Entitlements(
                planId = PlanIds.FREE, status = status, expiryTime = sub.expiryTime, autoRenewing = sub.autoRenewing,
                signedIn = true, loaded = true,
            )
            if (sub.planId == PlanIds.FREE) return base
            // v50 hak yalnız sunucunun doğruladığı kayıtla ve doğrulama yakın zamanda yapıldıysa açılır
            // (çevrimdışı en çok [OFFLINE_GRACE_MS]; yerel veritabanında elle yazılmış bir kayıt hak vermez)
            if (sub.source != "server") return base
            val verifiedAt = sub.verifiedAt ?: return base
            if (verifiedAt > now + CLOCK_SKEW_MS || now - verifiedAt > OFFLINE_GRACE_MS) return base
            if (status !in SubscriptionStatuses.GRANTING) return base
            val end = sub.expiryTime ?: return base
            if (now >= end) return base.copy(status = SubscriptionStatuses.EXPIRED)
            val fromServer = sub.features.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val features = fromServer.ifEmpty { planFeatures.filter { it.planId == sub.planId }.map { it.featureKey }.toSet() }
            if (features.isEmpty()) return base
            return base.copy(planId = sub.planId, features = features)
        }
    }
}
