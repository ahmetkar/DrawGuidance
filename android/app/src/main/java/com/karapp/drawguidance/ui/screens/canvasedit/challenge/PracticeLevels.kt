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
    // v52 daha sıkı: tam puan ve sıfır puan uzaklıkları daraldı (çizim doğruluğu daha çok önemli)
    TRACE(R.string.v50_practice_kind_trace, R.string.v50_practice_intro_trace, 0.010f, 0.036f),
    FADE(R.string.v50_practice_kind_fade, R.string.v50_practice_intro_fade, 0.016f, 0.052f),
    MIRROR(R.string.v50_practice_kind_mirror, R.string.v50_practice_intro_mirror, 0.014f, 0.048f),
    /** v51 Bakarak çiz: örnek köşede küçük durur, şekil boş alana (çerçeveye) bakarak çizilir; yer ve boy serbest. */
    COPY(R.string.v51_practice_kind_copy, R.string.v51_practice_intro_copy, 0.020f, 0.060f),
    /** v51 Süreli çizim: kılavuzun üstünden süre bitmeden çizilir; hız da puanlanır. */
    TIMED(R.string.v51_practice_kind_timed, R.string.v51_practice_intro_timed, 0.012f, 0.042f),
}

enum class PracticeGroup(@StringRes val title: Int) {
    BASICS(R.string.v50_practice_group_basics),
    PATTERNS(R.string.v50_practice_group_patterns),
    VOLUME(R.string.v50_practice_group_volume),
    MOTIFS(R.string.v50_practice_group_motifs),
    SUBJECTS(R.string.v50_practice_group_subjects),
    /** v51 Gözlem çizimi: hayvanlar, nesneler, doğa ve taşıtlar (eskiz kütüphanesi). */
    OBSERVE(R.string.v51_practice_group_observe),
    /** v51 İnsan ve portre: vücut oranları, yüz açıları, göz, burun, dudak, kulak, el. */
    FIGURES(R.string.v51_practice_group_figures),
}

/**
 * Bir alıştırma: [shape] kütüphane şeklinin kayıt türü ("lib.practice.<ad>" ya da v51 "lib.sketch.<ad>"). [id] en iyi
 * puanın kayıt anahtarıdır. [reward] v51: ödüllü reklam izlenerek açılır (Premium'da açık).
 */
class PracticeLevel(val shape: String, val kind: PracticeKind, val group: PracticeGroup, val size: Float = 0.72f, val reward: Boolean = false) {
    val id: String = "adult." + shape.removePrefix(PREFIX).removePrefix(SKETCH) + "." + kind.name.lowercase()

    /** Basit resimde de sunulur mu. */
    val simple: Boolean get() = shape.removePrefix("lib.") in PracticeShapes.simpleIds

    /** Şeklin Türkçe adı (arayüzde I18n.tr ile çevrilir). */
    val title: String get() = ShapeRegistry.title(shape) ?: shape

    companion object {
        const val PREFIX = "lib.practice."
        const val SKETCH = "lib.sketch."
    }
}

/**
 * Alıştırmanın kâğıttaki hedefi: yeri ([box]), puanlanan çizgiler ([lines], tuval koordinatı), görünen kılavuz
 * ([guide]; simetri görevinde sol yarı) ve simetri ekseni ([axis]; yalnız simetri görevinde).
 */
class PracticeTarget(
    val level: PracticeLevel,
    val box: Rect,
    val lines: List<List<Offset>>,
    val guide: List<List<Offset>>,
    val axis: Float?,
    /** v51 Bakarak çiz: örneğin durduğu küçük çerçeve (kılavuz bu çerçevede çizilir). */
    val reference: Rect? = null,
    /** v51 Süreli çizim: süre (saniye). */
    val seconds: Int = 0,
)

object PracticeLevels {
    private val T = PracticeKind.TRACE
    private val F = PracticeKind.FADE
    private val M = PracticeKind.MIRROR

    private val C = PracticeKind.COPY
    private val S = PracticeKind.TIMED

    private fun l(name: String, kind: PracticeKind, group: PracticeGroup, size: Float = 0.72f, reward: Boolean = false) =
        PracticeLevel(PracticeLevel.PREFIX + name, kind, group, size, reward)

    /** v51 eskiz kütüphanesinden alıştırma (yalnız gelişmiş resim). */
    private fun k(name: String, kind: PracticeKind, group: PracticeGroup, size: Float = 0.74f, reward: Boolean = false) =
        PracticeLevel(PracticeLevel.SKETCH + name, kind, group, size, reward)

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
        // v51 yeni alıştırmalar (sona eklenir; kimlikler kalıcı). [reward]: ödüllü reklamla açılır.
        l("lines", S, PracticeGroup.BASICS),
        l("ellipses", S, PracticeGroup.BASICS),
        l("radial", F, PracticeGroup.BASICS),
        l("waves", F, PracticeGroup.BASICS),
        l("spiral", S, PracticeGroup.BASICS),
        l("waves", S, PracticeGroup.BASICS),
        l("honeycomb", F, PracticeGroup.PATTERNS),
        l("honeycomb", M, PracticeGroup.PATTERNS),
        l("mandala", F, PracticeGroup.PATTERNS, reward = true),
        l("scales", S, PracticeGroup.PATTERNS),
        l("box_2p", F, PracticeGroup.VOLUME, 0.8f),
        l("sphere", F, PracticeGroup.VOLUME),
        l("cylinder", F, PracticeGroup.VOLUME),
        l("box_2p", C, PracticeGroup.VOLUME, 0.8f, reward = true),
        l("cylinder", C, PracticeGroup.VOLUME),
        l("star12", M, PracticeGroup.MOTIFS),
        l("rumi", M, PracticeGroup.MOTIFS),
        l("star8", F, PracticeGroup.MOTIFS),
        l("rumi", F, PracticeGroup.MOTIFS, reward = true),
        l("star12", S, PracticeGroup.MOTIFS),
        l("leaf", M, PracticeGroup.SUBJECTS, 0.8f),
        l("cup", M, PracticeGroup.SUBJECTS),
        l("face", F, PracticeGroup.SUBJECTS, reward = true),
        l("tulip", C, PracticeGroup.SUBJECTS),
        k("cat", PracticeKind.TRACE, PracticeGroup.OBSERVE),
        k("bird", PracticeKind.TRACE, PracticeGroup.OBSERVE),
        k("apple", C, PracticeGroup.OBSERVE),
        k("pear", C, PracticeGroup.OBSERVE),
        k("butterfly", M, PracticeGroup.OBSERVE, reward = true),
        k("mug", C, PracticeGroup.OBSERVE),
        k("teapot", PracticeKind.TRACE, PracticeGroup.OBSERVE),
        k("vase", M, PracticeGroup.OBSERVE),
        k("tree", PracticeKind.TRACE, PracticeGroup.OBSERVE, reward = true),
        k("mushroom", M, PracticeGroup.OBSERVE),
        k("fish", C, PracticeGroup.OBSERVE),
        k("chair", C, PracticeGroup.OBSERVE, reward = true),
        k("lighthouse", M, PracticeGroup.OBSERVE),
        k("car_side", C, PracticeGroup.OBSERVE),
        k("bicycle", PracticeKind.TRACE, PracticeGroup.OBSERVE, reward = true),
        k("sailboat", C, PracticeGroup.OBSERVE),
        k("owl", PracticeKind.TRACE, PracticeGroup.OBSERVE),
        k("house_perspective", PracticeKind.TRACE, PracticeGroup.OBSERVE, reward = true),
        k("figure_standing", PracticeKind.TRACE, PracticeGroup.FIGURES),
        k("head_construction", PracticeKind.TRACE, PracticeGroup.FIGURES),
        k("face_front", M, PracticeGroup.FIGURES),
        k("eye_detail", C, PracticeGroup.FIGURES),
        k("nose_detail", PracticeKind.TRACE, PracticeGroup.FIGURES, reward = true),
        k("lips_detail", M, PracticeGroup.FIGURES),
        k("ear_detail", C, PracticeGroup.FIGURES),
        k("face_three_quarter", PracticeKind.TRACE, PracticeGroup.FIGURES, reward = true),
        k("face_profile", C, PracticeGroup.FIGURES),
        k("hand_open", PracticeKind.TRACE, PracticeGroup.FIGURES, reward = true),
        k("figure_walking", PracticeKind.TRACE, PracticeGroup.FIGURES),
        k("figure_sitting", C, PracticeGroup.FIGURES, reward = true),
        // v52 ödüllü reklamla açılan yeni alıştırmalar (ilk beşi basit resimde de)
        l("radial", S, PracticeGroup.BASICS, reward = true),
        l("value_scale", F, PracticeGroup.BASICS, 0.86f, reward = true),
        l("mandala", S, PracticeGroup.PATTERNS, reward = true),
        l("honeycomb", S, PracticeGroup.PATTERNS, reward = true),
        l("scales", C, PracticeGroup.PATTERNS, reward = true),
        k("horse", PracticeKind.TRACE, PracticeGroup.OBSERVE, reward = true),
        k("elephant", C, PracticeGroup.OBSERVE, reward = true),
        k("dog", PracticeKind.TRACE, PracticeGroup.OBSERVE, reward = true),
        k("rabbit", PracticeKind.TRACE, PracticeGroup.OBSERVE, reward = true),
        k("turtle", C, PracticeGroup.OBSERVE, reward = true),
        k("castle_tower", M, PracticeGroup.OBSERVE, reward = true),
        k("train", C, PracticeGroup.OBSERVE, reward = true),
        k("sports_car", PracticeKind.TRACE, PracticeGroup.OBSERVE, reward = true),
        k("pineapple", M, PracticeGroup.OBSERVE, reward = true),
        k("strawberry", C, PracticeGroup.OBSERVE, reward = true),
        k("figure_running", PracticeKind.TRACE, PracticeGroup.FIGURES, reward = true),
        k("face_child", M, PracticeGroup.FIGURES, reward = true),
        k("hair_styles", PracticeKind.TRACE, PracticeGroup.FIGURES, reward = true),
        k("figure_child", C, PracticeGroup.FIGURES, reward = true),
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

    /** v51 Bakarak çiz: örnek çerçevesinin ve çizim çerçevesinin boyu (kâğıdın kısa kenarına oran). */
    const val COPY_REF = 0.30f
    const val COPY_SIZE = 0.58f
    private const val COPY_PAD = 0.04f

    /** v51 Süreli çizim: süre, kılavuzun toplam boyuna göre (kutunun köşegeni başına 2,5 sn), 15–90 sn. */
    fun seconds(lines: List<List<Offset>>, box: Rect): Int {
        val diag = kotlin.math.hypot(box.width, box.height).coerceAtLeast(1f)
        return (10 + ChallengeScore.length(lines) / diag * 2.5f).toInt().coerceIn(15, 90)
    }

    /** [aspect] oranlı şeklin [side] boyundaki kutusunun [cx],[cy] merkezli yeri. */
    private fun fit(aspect: Float, side: Float, cx: Float, cy: Float): Rect {
        val w = if (aspect >= 1f) side else side * aspect
        val h = if (aspect >= 1f) side / aspect else side
        return Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    fun box(level: PracticeLevel, paperW: Int, paperH: Int): Rect {
        val aspect = ShapeRegistry.aspect(level.shape)
        if (level.kind == PracticeKind.COPY) {
            // Çizim çerçevesi sağ altta, örnek sol üstte (çakışmazlar)
            val m = min(paperW, paperH).toFloat()
            val side = m * COPY_SIZE
            return fit(aspect, side, paperW - m * COPY_PAD - side / 2f, paperH - m * COPY_PAD - side / 2f)
        }
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
        if (level.kind == PracticeKind.COPY) {
            val m = min(paperW, paperH).toFloat()
            val side = m * COPY_REF
            val ref = fit(ShapeRegistry.aspect(level.shape), side, m * COPY_PAD + side / 2f, m * COPY_PAD + side / 2f)
            val k = ref.width / box.width.coerceAtLeast(1f)
            val guide = lines.map { line -> line.map { p -> Offset(ref.left + (p.x - box.left) * k, ref.top + (p.y - box.top) * k) } }
            return PracticeTarget(level, box, lines, guide, null, reference = ref)
        }
        if (level.kind == PracticeKind.TIMED) return PracticeTarget(level, box, lines, lines, null, seconds = seconds(lines, box))
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
