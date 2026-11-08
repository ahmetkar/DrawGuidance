package com.karapp.drawguidance.data.remote.v1

import kotlinx.serialization.Serializable

/** v43 `PUT /api/v1/devices/me` gövdesi: cihaz bilgisi (sunucuda şifreli saklanır) ve kurulu paketler. */
@Serializable
data class DeviceRegistration(
    val deviceId: String,
    val appVersionCode: Long,
    val appVersionName: String,
    val model: String? = null,
    val manufacturer: String? = null,
    val sdkInt: Int? = null,
    val locale: String? = null,
    val packages: List<InstalledPackageDto> = emptyList(),
)

@Serializable
data class InstalledPackageDto(val id: String, val version: Int)

/** v43 `GET /api/v1/inbox` yanıtı. */
@Serializable
data class InboxResponse(val items: List<InboxItemDto> = emptyList())

@Serializable
data class InboxItemDto(
    val deliveryId: String,
    val kind: String,
    val refId: String,
    val version: Int = 1,
    val stage: String = "",
    val dataReady: Boolean = false,
)

@Serializable
data class AckItemDto(val deliveryId: String, val stage: String, val error: String? = null)

@Serializable
data class AckBody(val items: List<AckItemDto>)

@Serializable
data class AckResponse(val results: Map<String, String> = emptyMap())

/** v43 teslimat verisi (türüne göre alanlar dolu). */
@Serializable
data class DeliveryDataDto(
    val kind: String,
    val id: String? = null,
    val title: String? = null,
    val body: String? = null,
    val imageUrl: String? = null,
    val name: String? = null,
    val mode: String? = null,
    val version: Int? = null,
    val items: List<PackageItemDto> = emptyList(),
    val versionCode: Long? = null,
    val versionName: String? = null,
    val message: String? = null,
    val minSupportedVersionCode: Long? = null,
)

@Serializable
data class PackageItemDto(val id: String, val window: String, val title: String, val category: String? = null, val url: String)
