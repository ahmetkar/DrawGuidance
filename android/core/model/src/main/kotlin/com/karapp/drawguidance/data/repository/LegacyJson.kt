package com.karapp.drawguidance.data.repository

import com.karapp.drawguidance.data.model.Stroke
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val canvasJson: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Eski (v2) JSON içerik biçimi — yalnızca veritabanı geçişinde ve eski kayıtları okurken kullanılır. */
fun decodeStrokes(contentJson: String): List<Stroke> = try {
    canvasJson.decodeFromString(contentJson)
} catch (_: Exception) {
    emptyList()
}

fun encodeStrokes(strokes: List<Stroke>): String = canvasJson.encodeToString(strokes)
