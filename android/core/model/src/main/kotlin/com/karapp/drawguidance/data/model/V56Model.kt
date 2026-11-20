package com.karapp.drawguidance.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * v56 katman karışım modları. Kodlar içerik biçiminde sabit ([code]); sıra değişse bile kayıtlı veri bozulmaz.
 * Android'de `BlendMode` (API 29+, öncesinde en yakın `PorterDuff`), iOS'ta `CGBlendMode` karşılığı vardır.
 */
@Serializable
enum class LayerBlend(val code: Int) {
    NORMAL(0), MULTIPLY(1), SCREEN(2), OVERLAY(3), DARKEN(4), LIGHTEN(5), COLOR_DODGE(6), COLOR_BURN(7),
    HARD_LIGHT(8), SOFT_LIGHT(9), DIFFERENCE(10), EXCLUSION(11), HUE(12), SATURATION(13), COLOR(14), LUMINOSITY(15),
    ADD(16);

    companion object {
        fun of(code: Int): LayerBlend = entries.firstOrNull { it.code == code } ?: NORMAL
    }
}

/** v56 seçim maskesi: kapalı çokgen (tuval koordinatı), kenar yumuşatma (px) ve ters seçim. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ClipMask(
    val points: List<StrokePoint>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val feather: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val inverted: Boolean = false,
)

/** v56 gren: doku kimliği (hazır doku ya da varlık), ölçek, güç (0..1); [moving] true: doku tuvale sabit (kayar). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GrainSpec(
    val textureId: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val scale: Float = 1f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val strength: Float = 1f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val moving: Boolean = true,
)

/** v56 yansıma dalgası: genlik ve dalga boyu (px), evre (0..1), [ripple] true: dalgacık (iç içe halkalar). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WaveSpec(
    val amplitude: Float,
    val wavelength: Float,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val phase: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val ripple: Boolean = false,
    /** Yukarıdan aşağı alfa gradyanı (başlangıç ve bitiş opaklığı). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fadeFrom: Float = 1f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fadeTo: Float = 1f,
)

/**
 * v56 resim dönüşümleri (asıl resim değişmez, sonradan düzenlenir): [quad] bozma (4 köşe, sol-üstten saat yönünde),
 * [mesh] çarpıtma (4×4 = 16 kontrol noktası, satır satır), [wave] yansıma dalgası. Noktalar tuval koordinatındadır.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ImageWarp(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val quad: List<StrokePoint>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val mesh: List<StrokePoint>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val wave: WaveSpec? = null,
) {
    val isEmpty: Boolean get() = quad == null && mesh == null && wave == null
}

/** v56 simetri türleri. */
@Serializable
enum class SymmetryKind { VERTICAL, HORIZONTAL, QUAD, RADIAL }

/** v56 belge simetrisi: tür, kol sayısı (radyalde 2..16), eksen merkezi ve açısı (derece). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SymmetrySpec(
    val kind: SymmetryKind,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val arms: Int = 2,
    val cx: Float,
    val cy: Float,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val angle: Float = 0f,
)

/** v56 belge düzeyindeki ek bilgiler (V11 içerikte JSON olarak). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DocExtras(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val symmetry: SymmetrySpec? = null,
) {
    val isEmpty: Boolean get() = symmetry == null
}
