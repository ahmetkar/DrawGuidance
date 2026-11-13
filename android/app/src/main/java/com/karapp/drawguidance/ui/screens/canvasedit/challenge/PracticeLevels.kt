package com.karapp.drawguidance.ui.screens.canvasedit.challenge

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.library.practice.PracticeShapes
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import kotlin.math.min

/*
 * v50 yetişkinler için çizim alıştırmaları ("Alıştırmalar"): basit resim ve gelişmiş resim tuvallerinde, çocuk
 * görevinden ayrı. Şekiller content.library.practice.PracticeShapes'tedir. Üç görev türü:
 *  - Üzerinden çiz ([PracticeKind.TRACE]): kesikli kılavuzun üstünden, dar toleransla.
 *  - Kaybolan kılavuz ([PracticeKind.FADE]): kılavuz birkaç saniye görünür, sonra kaybolur; ezberden çizilir (her
 *    ipucu puandan düşer).
 *  - Simetri tamamla ([PracticeKind.MIRROR]): şeklin yalnız sol yarısı ve simetri ekseni görünür; bütün şekil çizilir.
 * Basit resimde yalnız temel alıştırmalar ve kolay desenler ([PracticeShapes.simpleIds]), gelişmiş resimde hepsi.
 */

enum class PracticeKind(@StringRes val label: Int, @StringRes val intro: Int, val near: Float, val far: Float) {
    TRACE(R.string.v50_practice_kind_trace, R.string.v50_practice_intro_trace, 0.022f, 0.07f),
    FADE(R.string.v50_practice_kind_fade, R.string.v50_practice_intro_fade, 0.035f, 0.10f),
    MIRROR(R.string.v50_practice_kind_mirror, R.string.v50_practice_intro_mirror, 0.03f, 0.09f),
}

enum class PracticeGroup(@StringRes val title: Int) {
    BASICS(R.string.v50_practice_group_basics),
    PATTERNS(R.string.v50_practice_group_patterns),
    VOLUME(R.string.v50_practice_group_volume),
    MOTIFS(R.string.v50_practice_group_motifs),
    SUBJECTS(R.string.v50_practice_group_subjects),
}

/** Bir alıştırma: [shape] kütüphane şeklinin kayıt türü ("lib.practice.<ad>"). [id] en iyi puanın kayıt anahtarıdır. */
class PracticeLevel(val shape: String, val kind: PracticeKind, val group: PracticeGroup, val size: Float = 0.72f) {
    val id: String = "adult." + shape.removePrefix(PREFIX) + "." + kind.name.lowercase()

    /** Basit resimde de sunulur mu. */
    val simple: Boolean get() = shape.removePrefix("lib.") in PracticeShapes.simpleIds

    /** Şeklin Türkçe adı (arayüzde I18n.tr ile çevrilir). */
    val title: String get() = ShapeRegistry.title(shape) ?: shape

    companion object {
        const val PREFIX = "lib.practice."
    }
}

/**
 * Alıştırmanın kâğıttaki hedefi: yeri ([box]), puanlanan çizgiler ([lines], tuval koordinatı), görünen kılavuz
 * ([guide]; simetri görevinde sol yarı) ve simetri ekseni ([axis]; yalnız simetri görevinde).
 */
class PracticeTarget(val level: PracticeLevel, val box: Rect, val lines: List<List<Offset>>, val guide: List<List<Offset>>, val axis: Float?)

object PracticeLevels {
    private val T = PracticeKind.TRACE
    private val F = PracticeKind.FADE
    private val M = PracticeKind.MIRROR

    private fun l(name: String, kind: PracticeKind, group: PracticeGroup, size: Float = 0.72f) =
        PracticeLevel(PracticeLevel.PREFIX + name, kind, group, size)

    /** Bütün alıştırmalar (kolaydan zora; sıra ve kimlikler kalıcı). */
    val all: List<PracticeLevel> = listOf(
        l("lines", T, PracticeGroup.BASICS),
        l("radial", T, PracticeGroup.BASICS),
        l("ellipses", T, PracticeGroup.BASICS),
        l("spiral", T, PracticeGroup.BASICS),
        l("waves", T, PracticeGroup.BASICS),
        l("value_scale", T, PracticeGroup.BASICS, 0.86f),
        l("lines", F, PracticeGroup.BASICS),
        l("ellipses", F, PracticeGroup.BASICS),
        l("spiral", F, PracticeGroup.BASICS),
        l("honeycomb", T, PracticeGroup.PATTERNS),
        l("scales", T, PracticeGroup.PATTERNS),
        l("mandala", T, PracticeGroup.PATTERNS),
        l("mandala", M, PracticeGroup.PATTERNS),
        l("scales", F, PracticeGroup.PATTERNS),
        // Yalnız gelişmiş resim
        l("cube_1p", T, PracticeGroup.VOLUME, 0.8f),
        l("box_2p", T, PracticeGroup.VOLUME, 0.8f),
        l("sphere", T, PracticeGroup.VOLUME),
        l("cylinder", T, PracticeGroup.VOLUME),
        l("cube_1p", F, PracticeGroup.VOLUME, 0.8f),
        l("star8", T, PracticeGroup.MOTIFS),
        l("star8", M, PracticeGroup.MOTIFS),
        l("star12", T, PracticeGroup.MOTIFS),
        l("tulip", T, PracticeGroup.MOTIFS),
        l("tulip", M, PracticeGroup.MOTIFS),
        l("rumi", T, PracticeGroup.MOTIFS),
        l("eye", T, PracticeGroup.SUBJECTS, 0.8f),
        l("face", T, PracticeGroup.SUBJECTS),
        l("face", M, PracticeGroup.SUBJECTS),
        l("leaf", T, PracticeGroup.SUBJECTS, 0.8f),
        l("rose", T, PracticeGroup.SUBJECTS),
        l("dome", T, PracticeGroup.SUBJECTS, 0.8f),
        l("dome", M, PracticeGroup.SUBJECTS, 0.8f),
        l("cup", T, PracticeGroup.SUBJECTS),
        l("eye", F, PracticeGroup.SUBJECTS, 0.8f),
        l("rose", F, PracticeGroup.SUBJECTS),
        l("cup", F, PracticeGroup.SUBJECTS),
    )

    /** Tuval türünde sunulan alıştırmalar: basit resimde temel olanlar, gelişmiş resimde hepsi; ötekilerde yok. */
    fun forCanvas(kind: CanvasKind?): List<PracticeLevel> = when (kind) {
        CanvasKind.SIMPLE_ART -> all.filter { it.simple }
        CanvasKind.ART -> all
        else -> emptyList()
    }

    fun available(kind: CanvasKind?): Boolean = kind == CanvasKind.SIMPLE_ART || kind == CanvasKind.ART

    /** Geçme sınırı ve yıldızlar (çocuk görevinden yüksek). */
    const val PASS = 70
    val STARS = intArrayOf(70, 82, 92)
    fun stars(score: Int): Int = STARS.count { score >= it }

    /** Kaybolan kılavuzda her ipucunun puan cezası. */
    const val HINT_PENALTY = 5

    /** Kılavuzun kenar biçimi (hedef şekil kurulurken; görünmez, yalnız çizgiler alınır). */
    private val style = ShapeStyle(strokeColorHex = "#2A2A33", strokeWidth = 3f)

    fun box(level: PracticeLevel, paperW: Int, paperH: Int): Rect {
        val aspect = ShapeRegistry.aspect(level.shape)
        val side = min(paperW, paperH) * level.size
        val w = if (aspect >= 1f) side else side * aspect
        val h = if (aspect >= 1f) side / aspect else side
        val cx = paperW / 2f
        val cy = paperH / 2f
        return Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    fun target(level: PracticeLevel, paperW: Int, paperH: Int): PracticeTarget {
        val box = box(level, paperW, paperH)
        val stroke = runCatching { ShapeFactory.build(level.shape, box.topLeft, box.bottomRight, style, id = "practice-target", order = 0) }.getOrNull()
        val lines = stroke?.let { ShapeFactory.subPaths(it) }.orEmpty()
            .filter { it.label == null && it.points.size >= 2 && (it.widthScale ?: 1f) > 0f }
            .map { p -> if (p.closed) p.points + p.points.first() else p.points }
        if (level.kind != PracticeKind.MIRROR) return PracticeTarget(level, box, lines, lines, null)
        val axis = box.center.x
        return PracticeTarget(level, box, lines, leftHalf(lines, axis), axis)
    }

    /** Çizgilerin [axis] ekseninin solunda (ve üstünde) kalan parçaları; eksenden geçen kenar kesişim noktasında bölünür. */
    fun leftHalf(lines: List<List<Offset>>, axis: Float): List<List<Offset>> {
        val eps = 0.5f
        val out = ArrayList<List<Offset>>()
        for (line in lines) {
            var piece = ArrayList<Offset>()
            for (i in line.indices) {
                val p = line[i]
                val inside = p.x <= axis + eps
                if (i > 0) {
                    val q = line[i - 1]
                    val wasInside = q.x <= axis + eps
                    if (inside != wasInside && p.x != q.x) {
                        val t = (axis - q.x) / (p.x - q.x)
                        val cut = Offset(axis, q.y + (p.y - q.y) * t)
                        piece += cut
                        if (!inside) {
                            if (piece.size >= 2) out += piece
                            piece = ArrayList()
                        }
                    }
                }
                if (inside) piece += p
            }
            if (piece.size >= 2) out += piece
        }
        return out
    }
}
