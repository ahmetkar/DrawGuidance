package com.karapp.drawguidance.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * Backend sağlık kontrolü. v30: sunucu Spring Boot + MongoDB; CRUD uçları [com.karapp.drawguidance.data.remote.v1.DrawGuidanceApi].
 */
interface ApiService {
    @GET("api/health")
    suspend fun health(): HealthDto
}

/** v30 sağlık yanıtı: servis adı, sürüm, veritabanı durumu ve küme türü (eski sunucuda yalnız durum). */
@Serializable
data class HealthDto(
    val status: String,
    val service: String? = null,
    val version: String? = null,
    val database: String? = null,
    val topology: String? = null,
)
