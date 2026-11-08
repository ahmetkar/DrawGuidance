package com.karapp.drawguidance.content.ink

/** Kalem noktası: tuval birimleri (y aşağı), [t] milisaniye (0 olabilir). */
data class InkPoint(val x: Float, val y: Float, val t: Long = 0L)

/** Kalemin kalkmadan çizdiği tek çizgi. */
class InkStroke(val points: List<InkPoint>)

/** MIXED = rakam + harf + simge; DIGITS = rakam + matematik simgeleri; LETTERS = harfler (Türkçe dahil) + noktalama. */
enum class InkMode { MIXED, DIGITS, LETTERS }

/** Bir karakter adayı; [score] 0..1 (yüksek = daha iyi). */
data class Candidate(val text: String, val score: Float)

/** Tek karakter: hangi çizgilerden (girişteki sıra numaraları) oluştuğu ve adayları (en iyi önce). */
data class CharResult(val strokeIndices: List<Int>, val candidates: List<Candidate>)

/**
 * Tanıma sonucu. [text] en iyi okuma ("25", "x+1", "Ali"); [confidence] 0..1 genel güven (karakter puanlarının en
 * küçüğü/ortalaması × bölütleme kalitesi); [alternatives] en çok 3 farklı tam okuma ("2S", "Z5"); [chars] karakter
 * başına ayrıntı (metindeki boşluklar karakter sayılmaz).
 */
data class Recognition(
    val text: String,
    val confidence: Float,
    val alternatives: List<String>,
    val chars: List<CharResult>,
)
