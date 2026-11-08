package com.karapp.drawguidance.data.remote.dto

import kotlinx.serialization.Serializable

/** Backend'in /api/canvases uçlarından döndürdüğü satır. */
@Serializable
data class CanvasDto(
    val id: String,
    val localId: String,
    val userId: String,
    val name: String,
    val width: Int,
    val height: Int,
    val backgroundColorHex: String,
    val cloudSyncEnabled: Boolean,
    val aiEnabled: Boolean,
    // Backend bunu TEXT kolonunda tutuyor; burada da ham JSON string olarak
    // alınır, ayrıştırma repository katmanında yapılır.
    val contentJson: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class UpsertCanvasRequest(
    val localId: String,
    val name: String,
    val width: Int,
    val height: Int,
    val backgroundColorHex: String,
    val cloudSyncEnabled: Boolean,
    val aiEnabled: Boolean,
    val contentJson: List<StrokeDto>? = null,
)

@Serializable
data class UpdateCanvasRequest(
    val name: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val backgroundColorHex: String? = null,
    val cloudSyncEnabled: Boolean? = null,
    val aiEnabled: Boolean? = null,
)

@Serializable
data class UpdateContentRequest(
    val contentJson: List<StrokeDto>,
)

@Serializable
data class StrokePointDto(val x: Float, val y: Float)

@Serializable
data class StrokeDto(
    val id: String,
    val points: List<StrokePointDto>,
    val colorHex: String,
    val brushType: String,
    val sizePx: Float,
    val opacity: Float,
    val order: Int,
    val groupId: String? = null,
    val assetId: String? = null,
    val flow: Float = 1f,
    val strength: Float = 1f,
)

@Serializable
data class HealthDto(val status: String)
