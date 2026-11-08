package com.karapp.drawguidance.data.remote.v1

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * v33 sunucunun uçları (Spring Boot + MongoDB). Sunucuda yalnız kullanıcılar ve hazır katalog (şekil şablonları,
 * vektör şekiller, modların temel ağları) bulunur; tuvaller, 3B şekiller ve resimler **yalnız cihazda** saklanır.
 * Katalog herkese açık (ETag ile önbellek), kullanıcı uçları Firebase kimlik jetonu ister.
 */
interface DrawGuidanceApi {
    // --- Katalog ---
    @GET("api/v1/catalog/manifest") suspend fun catalogManifest(@Header("If-None-Match") ifNoneMatch: String? = null): Response<ResponseBody>
    @GET("api/v1/catalog/templates") suspend fun catalogTemplates(): Response<ResponseBody>
    // v37 Accept ile ikili biçim istenebilir (sunucu desteklemiyorsa JSON döner)
    @GET("api/v1/catalog/vectors") suspend fun catalogVectors(@Header("Accept") accept: String? = null): Response<ResponseBody>
    @GET("api/v1/catalog/mode-nets") suspend fun catalogModeNets(@Header("Accept") accept: String? = null): Response<ResponseBody>

    // --- Kullanıcı (Authorization: Bearer <Firebase ID jetonu>) ---
    @GET("api/v1/users/me") suspend fun me(@Header("Authorization") auth: String): Response<RemoteUser>
    @PUT("api/v1/users/me") suspend fun putMe(@Header("Authorization") auth: String, @Body body: UserBody): Response<RemoteUser>
    @DELETE("api/v1/users/me") suspend fun deleteMe(@Header("Authorization") auth: String): Response<Unit>

    // --- v39 abonelik (planlar herkese açık; abonelik uçları Firebase jetonuyla) ---
    @GET("api/v1/plans") suspend fun plans(): Response<RemotePlans>
    @GET("api/v1/subscriptions/me") suspend fun mySubscription(@Header("Authorization") auth: String): Response<RemoteSubscription>
    @POST("api/v1/subscriptions/verify") suspend fun verifySubscription(@Header("Authorization") auth: String, @Body body: VerifyBody): Response<RemoteSubscription>

    // --- v39 çizim görevinin ilerlemesi (Firebase jetonuyla; sunucu en iyi puanlarla birleştirir) ---
    @GET("api/v1/challenge/progress") suspend fun challengeProgress(@Header("Authorization") auth: String): Response<RemoteChallengeProgress>
    @PUT("api/v1/challenge/progress") suspend fun putChallengeProgress(@Header("Authorization") auth: String, @Body body: ChallengeProgressBody): Response<RemoteChallengeProgress>

    // --- v37 hata raporları (kimlik isteğe bağlı) ---
    @POST("api/v1/reports") suspend fun postReport(
        @Header("Authorization") auth: String?,
        @Body body: com.karapp.drawguidance.data.reports.ReportBody,
    ): Response<com.karapp.drawguidance.data.reports.ReportAccepted>

    // --- v40 geri bildirim (Firebase jetonuyla), sürüm bilgisi ve içerik eşitleme izni (herkese açık) ---
    @POST("api/v1/feedback") suspend fun postFeedback(@Header("Authorization") auth: String, @Body body: FeedbackBody): Response<FeedbackAccepted>
    @GET("api/v1/app/version") suspend fun appVersion(): Response<RemoteAppVersion>
    @GET("api/v1/sync/permit") suspend fun syncPermit(@Header("Authorization") auth: String?, @Query("deviceId") deviceId: String?): Response<RemoteSyncPermit>

    // --- v43 cihaz kaydı ve gelen kutusu (Firebase jetonu + X-Device-Id; Firebase Cloud Messaging yok, cihaz yoklar) ---
    @PUT("api/v1/devices/me") suspend fun registerDevice(@Header("Authorization") auth: String, @Body body: DeviceRegistration): Response<ResponseBody>
    @DELETE("api/v1/devices/me") suspend fun unregisterDevice(@Header("Authorization") auth: String, @Header("X-Device-Id") deviceId: String): Response<Unit>
    @GET("api/v1/inbox") suspend fun inbox(
        @Header("Authorization") auth: String,
        @Header("X-Device-Id") deviceId: String,
        @Header("If-None-Match") etag: String?,
        @Query("wait") wait: Int?,
    ): Response<InboxResponse>
    @POST("api/v1/inbox/ack") suspend fun ackInbox(@Header("Authorization") auth: String, @Header("X-Device-Id") deviceId: String, @Body body: AckBody): Response<AckResponse>
    @GET("api/v1/inbox/{id}/data") suspend fun inboxData(
        @Header("Authorization") auth: String,
        @Header("X-Device-Id") deviceId: String,
        @Path("id") deliveryId: String,
    ): Response<DeliveryDataDto>
}
