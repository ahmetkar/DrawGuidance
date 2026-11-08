package com.karapp.drawguidance.data.remote.v1

import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Sunucu hatası (HTTP durumu, sunucunun kodu ve iletisi). [isRetryable]: bağlantı/sunucu sorunu, iş sonra yeniden
 * denenir; öteki 4xx'ler kalıcıdır.
 */
class RemoteException(val status: Int, val code: String, message: String) : Exception(message) {
    val isNotFound: Boolean get() = status == 404
    val isUnauthorized: Boolean get() = status == 401
    val isRetryable: Boolean get() = status == 401 || status == 408 || status == 429 || status >= 500
}

/**
 * v33 sunucu kütüphanesi: hazır katalog (manifest ETag ile; değişmediyse 304 ve gövde inmez) ve kullanıcı
 * (Firebase jetonuyla). Tuval/resim/3B şekil uçları yoktur (bunlar yalnız cihazda).
 */
class RemoteStore(private val api: DrawGuidanceApi, private val json: Json = Json { ignoreUnknownKeys = true }) {

    private fun <T> Response<T>.ok(): T {
        if (isSuccessful) @Suppress("UNCHECKED_CAST") return (body() ?: Unit) as T
        throw error()
    }

    private fun <T> Response<T>.error(): RemoteException {
        val raw = runCatching { errorBody()?.string() }.getOrNull()
        val dto = raw?.let { runCatching { json.decodeFromString(RemoteErrorDto.serializer(), it) }.getOrNull() }
        return RemoteException(code(), dto?.error ?: "http_${code()}", dto?.message ?: message())
    }

    // --- Katalog ---

    /** Manifest ve ETag'i; [etag] eşleşirse (değişmedi) null. */
    suspend fun catalogManifest(etag: String? = null): Pair<CatalogManifest, String?>? {
        val r = api.catalogManifest(etag)
        if (r.code() == 304) return null
        val body = r.ok().string()
        return json.decodeFromString(CatalogManifest.serializer(), body) to r.headers()["ETag"]
    }

    /** Liste uçlarının ham JSON'u (`{"version", "items"}`; çözümleme [com.karapp.drawguidance.content.catalog.CatalogCodec]). */
    suspend fun catalogTemplates(): String = api.catalogTemplates().ok().string()
    suspend fun catalogVectors(): String = api.catalogVectors().ok().string()
    suspend fun catalogModeNets(): String = api.catalogModeNets().ok().string()

    /**
     * v37 vektörlerin ya da temel ağların ham gövdesi; [binary] ise ikili biçim istenir (JSON da kabul edilir, eski
     * sunucu JSON döner). Hangisi geldiği [com.karapp.drawguidance.content.catalog.CatalogBinary.isBinary] ile anlaşılır.
     */
    suspend fun catalogVectorsBytes(binary: Boolean): ByteArray = api.catalogVectors(accept(binary)).ok().bytes()
    suspend fun catalogModeNetsBytes(binary: Boolean): ByteArray = api.catalogModeNets(accept(binary)).ok().bytes()

    private fun accept(binary: Boolean): String? = if (binary) BINARY_ACCEPT else null

    // --- Kullanıcı ---

    private fun bearer(token: String) = "Bearer $token"

    /** Sunucudaki kayıt (yoksa null). */
    suspend fun me(token: String): RemoteUser? {
        val r = api.me(bearer(token))
        if (r.code() == 404) return null
        return r.ok()
    }

    suspend fun putMe(token: String, body: UserBody): RemoteUser = api.putMe(bearer(token), body).ok()

    suspend fun deleteMe(token: String) { api.deleteMe(bearer(token)).ok<Unit>() }

    // --- v39 abonelik ---

    suspend fun plans(): RemotePlans = api.plans().ok()

    /** Kullanıcının aboneliği (sunucuda kayıt yoksa Free). */
    suspend fun mySubscription(token: String): RemoteSubscription {
        val r = api.mySubscription(bearer(token))
        if (r.code() == 404) return RemoteSubscription()
        return r.ok()
    }

    /** Satın almayı sunucu Play'de doğrular (Play yapılandırılmadıysa 503 `billing_not_configured`). */
    suspend fun verifySubscription(token: String, body: VerifyBody): RemoteSubscription = api.verifySubscription(bearer(token), body).ok()

    // --- v39 çizim görevi ---

    suspend fun challengeProgress(token: String): RemoteChallengeProgress {
        val r = api.challengeProgress(bearer(token))
        if (r.code() == 404) return RemoteChallengeProgress()
        return r.ok()
    }

    suspend fun putChallengeProgress(token: String, body: ChallengeProgressBody): RemoteChallengeProgress =
        api.putChallengeProgress(bearer(token), body).ok()

    // --- v37 hata raporları ---

    /** Raporu gönderir; sunucunun verdiği rapor kimliği. Girişliyse [token] eklenir (yoksa adsız). */
    suspend fun postReport(body: com.karapp.drawguidance.data.reports.ReportBody, token: String?): String =
        api.postReport(token?.let { bearer(it) }, body).ok().id

    // --- v40 geri bildirim, sürüm bilgisi, eşitleme izni ---

    suspend fun postFeedback(token: String, body: FeedbackBody): String = api.postFeedback(bearer(token), body).ok().id

    suspend fun appVersion(): RemoteAppVersion = api.appVersion().ok()

    /** İçerik eşitleme izni; eski sunucuda uç yoksa (404) null. */
    suspend fun syncPermit(token: String?, deviceId: String?): RemoteSyncPermit? {
        val r = api.syncPermit(token?.let { bearer(it) }, deviceId)
        if (r.code() == 404) return null
        return r.ok()
    }

    // --- v43 cihaz kaydı ve gelen kutusu ---

    /** Yoklama sonucu: [notModified] ise gelen kutusu değişmedi (304). [pollAfterSec]: sunucunun önerdiği aralık. */
    data class InboxPoll(val notModified: Boolean, val items: List<InboxItemDto>, val etag: String?, val pollAfterSec: Long?)

    suspend fun registerDevice(token: String, body: DeviceRegistration): Long? {
        val r = api.registerDevice(bearer(token), body)
        r.ok()
        return r.headers()["X-Poll-After"]?.toLongOrNull()
    }

    suspend fun unregisterDevice(token: String, deviceId: String) { api.unregisterDevice(bearer(token), deviceId).ok<Unit>() }

    suspend fun inbox(token: String, deviceId: String, etag: String?, waitSeconds: Int?): InboxPoll {
        val r = api.inbox(bearer(token), deviceId, etag, waitSeconds)
        val after = r.headers()["X-Poll-After"]?.toLongOrNull()
        if (r.code() == 304) return InboxPoll(true, emptyList(), r.headers()["ETag"] ?: etag, after)
        val body = r.ok()
        return InboxPoll(false, body.items, r.headers()["ETag"], after)
    }

    suspend fun ackInbox(token: String, deviceId: String, items: List<AckItemDto>): Map<String, String> =
        api.ackInbox(bearer(token), deviceId, AckBody(items)).ok().results

    suspend fun inboxData(token: String, deviceId: String, deliveryId: String): DeliveryDataDto =
        api.inboxData(bearer(token), deviceId, deliveryId).ok()

    companion object {
        /** v37 ikili katalog istenir; sunucu desteklemiyorsa JSON da kabul edilir. */
        val BINARY_ACCEPT = "${com.karapp.drawguidance.content.catalog.CatalogBinary.MEDIA_TYPE}, application/json;q=0.5"
    }
}
