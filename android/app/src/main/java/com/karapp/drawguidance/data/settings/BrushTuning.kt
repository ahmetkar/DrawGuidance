package com.karapp.drawguidance.data.settings

import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.TubeParams
import com.karapp.drawguidance.ui.drawing.defaultSpacing
import com.karapp.drawguidance.ui.drawing.defaultTiltLight
import com.karapp.drawguidance.ui.drawing.defaultTiltWidth
import com.karapp.drawguidance.ui.drawing.hasFlatNib
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * v20 fırça başına ayarlar (her fırça kendi ayarlarını hatırlar, uygulama kapansa da kalır): damga aralığı, çizim
 * hızı (mürekkebin kaleme yetişme hızı), sabitleme, kalem eğimi ve 3B fırçanın ışık/malzeme/gölge ayarları.
 * Görünümü değiştirenler çizime [BrushParams] olarak işlenir; girdi ayarları (hız, sabitleme) noktalara işlenir.
 */
@Serializable
data class BrushTuning(
    /** Damga aralığı (fırça genişliğinin katı); null: fırçanın varsayılanı. */
    val spacing: Float? = null,
    /** Çizim hızı 0.1..1 (1: mürekkep kalemi anında izler). */
    val speed: Float = 1f,
    /** Sabitleme açık mı ve gücü (0..1). */
    val stabilizer: Boolean = false,
    val stabilizerStrength: Float = 0.5f,
    /** Kalem eğimi: kalınlık (0..2) ve açıklık (0..1) etkisi; null: fırçanın varsayılanı. */
    val tiltWidth: Float? = null,
    val tiltLight: Float? = null,
    /** Eğimin etki etmeye başladığı açı (derece, 0..60). */
    val tiltThreshold: Float = 0f,
    /** Yassı uç kalemin yönüne dönsün. */
    val nibFollow: Boolean = false,
    /** 3B fırça ayarları. */
    val tube: TubeParams = TubeParams(),
) {
    /** Etkin sabitleme gücü (kapalıysa 0). */
    val stabilizeAmount: Float get() = if (stabilizer) stabilizerStrength.coerceIn(0.05f, 1f) else 0f

    /** Bu fırçayla çizilen izin görünüm ayarları (yalnız varsayılandan farklı olanlar; hepsi varsayılansa null). */
    fun params(type: BrushType): BrushParams? {
        val sp = spacing?.takeIf { d -> type.defaultSpacing()?.let { abs(it - d) > 1e-3f } == true }
        val tw = tiltWidth?.takeIf { abs(it - type.defaultTiltWidth()) > 1e-3f }
        val tl = tiltLight?.takeIf { abs(it - type.defaultTiltLight()) > 1e-3f }
        val th = tiltThreshold.coerceIn(0f, 80f)
        val nib = nibFollow && type.hasFlatNib
        val t = tube.takeIf { type == BrushType.BRUSH_3D && it != TubeParams() }
        if (sp == null && tw == null && tl == null && th <= 0f && !nib && t == null) return null
        return BrushParams(spacing = sp, tiltWidth = tw, tiltLight = tl, tiltThreshold = th, nibFollow = nib, tube = t)
    }

    companion object {
        /** Fırça boyutu sınırları (px). */
        const val MIN_SIZE = 1f
        const val MAX_SIZE = 380f
    }
}
