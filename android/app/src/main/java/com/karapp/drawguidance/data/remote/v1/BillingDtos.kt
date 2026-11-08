package com.karapp.drawguidance.data.remote.v1

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** v39 `GET /api/v1/plans` yanıtı: etkin planlar ve bilinen bütün erişim hakkı anahtarları. */
@Serializable
data class RemotePlans(val plans: List<RemotePlan> = emptyList(), val featureKeys: List<String> = emptyList())

@Serializable
data class RemotePlan(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val productId: String? = null,
    val basePlanId: String? = null,
    val price: RemotePrice? = null,
    val period: String? = null,
    val sort: Int = 0,
    val features: List<String> = emptyList(),
)

@Serializable
data class RemotePrice(val amountMicros: Long = 0, val currency: String = "TRY", val display: String? = null)

/**
 * v39 `GET /api/v1/subscriptions/me` ve `POST /api/v1/subscriptions/verify` yanıtı. Zamanlar ISO 8601 metin ya da
 * epoch ms olabilir ([time]).
 */
@Serializable
data class RemoteSubscription(
    val planId: String = "free",
    val status: String? = null,
    val active: Boolean = false,
    val productId: String? = null,
    val basePlanId: String? = null,
    val startTime: JsonElement? = null,
    val expiryTime: JsonElement? = null,
    val autoRenewing: Boolean? = null,
    val acknowledged: Boolean? = null,
    val orderId: String? = null,
    val features: List<String> = emptyList(),
    val obfuscatedAccountId: String? = null,
    /** v40 kullanıcının iptal ettiği zaman (Play `canceledStateContext`). */
    val cancelTime: JsonElement? = null,
    /** v40 iptal nedeni: USER, SYSTEM, DEVELOPER, REPLACEMENT. */
    val cancelReason: String? = null,
    /** v40 duraklatılan aboneliğin kendiliğinden devam zamanı (Play `pausedStateContext`). */
    val autoResumeTime: JsonElement? = null,
) {
    companion object {
        /** ISO 8601 metin ("2026-11-20T10:00:00Z") ya da epoch ms (sayı ya da sayı metni) → ms. */
        fun time(e: JsonElement?): Long? {
            val p = e as? JsonPrimitive ?: return null
            p.longOrNull?.let { return it }
            val s = p.content.trim()
            if (s.isEmpty() || s == "null") return null
            s.toLongOrNull()?.let { return it }
            s.toDoubleOrNull()?.let { return (it * 1000).toLong() }
            return runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()
                ?: runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
        }
    }
}

/** v39 `POST /api/v1/subscriptions/verify` gövdesi (Play'in Purchase nesnesinden). */
@Serializable
data class VerifyBody(val packageName: String, val productId: String, val purchaseToken: String)

/** v39 çizim görevinin bir seviyesi (sunucuda uid başına bir belge; seviye sırası 0'dan). */
@Serializable
data class RemoteLevelProgress(val level: Int, val bestScore: Int = 0, val stars: Int = 0, val passed: Boolean = false, val updatedAt: Long = 0)

/** v39 `GET`/`PUT /api/v1/challenge/progress` yanıtı (birleştirilmiş ilerleme). */
@Serializable
data class RemoteChallengeProgress(val levels: List<RemoteLevelProgress> = emptyList())

/** v39 `PUT /api/v1/challenge/progress` gövdesinin bir satırı. */
@Serializable
data class ChallengeLevelBody(val level: Int, val bestScore: Int, val updatedAt: Long)

@Serializable
data class ChallengeProgressBody(val levels: List<ChallengeLevelBody>)

/** v40 `GET /api/v1/sync/permit` yanıtı (zamanlar ISO 8601). */
@Serializable
data class RemoteSyncPermit(
    val allowed: Boolean = true,
    val slotStart: JsonElement? = null,
    val slotEnd: JsonElement? = null,
    val nextSlotStart: JsonElement? = null,
    val contentVersion: Long = 0,
    val slot: Int = 0,
    val group: Long? = null,
    val slotLengthSeconds: Long = 600,
    val serverTime: JsonElement? = null,
) {
    companion object {
        fun time(e: JsonElement?): Long? = RemoteSubscription.time(e)
    }
}

/** v40 `GET /api/v1/app/version` yanıtı. */
@Serializable
data class RemoteAppVersion(val latestVersionCode: Long = 0, val minSupportedVersionCode: Long = 0, val message: String? = null)

/** v40 `POST /api/v1/feedback` gövdesi; [device] yalnız kullanıcı izin verdiyse dolu. */
@Serializable
data class FeedbackBody(
    val mode: String,
    val feature: String? = null,
    val rating: Int,
    val text: String? = null,
    val consent: Boolean = false,
    val device: FeedbackDevice? = null,
)

@Serializable
data class FeedbackDevice(val model: String, val androidVersion: String, val appVersion: String, val versionCode: Long, val locale: String)

@Serializable
data class FeedbackAccepted(val id: String = "")
