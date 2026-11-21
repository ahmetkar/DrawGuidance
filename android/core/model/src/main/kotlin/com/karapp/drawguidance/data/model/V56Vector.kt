package com.karapp.drawguidance.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * v56 aşama 8A (2E, yalnız Resim): dolgu/kontur/görünüm seçenekleri, bağlı kopya, opaklık maskesi, Tekrarla.
 * iOS `V56Vector.swift` ile aynı kurallar.
 */

@Serializable enum class CapStyle { ROUND, BUTT, SQUARE }
@Serializable enum class JoinStyle { ROUND, MITER, BEVEL }
@Serializable enum class StrokeAlign { CENTER, INSIDE, OUTSIDE }
@Serializable enum class ArrowKind { NONE, TRIANGLE, OPEN, CIRCLE, BAR }

/** Çoklu dolgu ya da kontur katmanı ([width] yalnız konturda; tuval birimi). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PaintLayer(
    val colorHex: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val width: Float = 4f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val opacity: Float = 1f,
)

/** Serbest gradyan noktası: şeklin kutusunda (0..1) konum ve renk. */
@Serializable
data class GradientPoint(val x: Float, val y: Float, val colorHex: String)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class VectorStyle(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val cap: CapStyle = CapStyle.ROUND,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val join: JoinStyle = JoinStyle.ROUND,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val miterLimit: Float = 4f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val align: StrokeAlign = StrokeAlign.CENTER,
    /** Tire ve boşluk uzunlukları (tuval birimi; çift sayıda). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dash: List<Float>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val arrowStart: ArrowKind = ArrowKind.NONE,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val arrowEnd: ArrowKind = ArrowKind.NONE,
    /** Desen dolgu: hazır doku kimliği ([V56Textures.BUILTIN]) ve ölçeği. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val patternId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val patternScale: Float = 1f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val freeGradient: List<GradientPoint>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val blend: LayerBlend = LayerBlend.NORMAL,
    /** Ana dolgunun üstüne sırayla çizilen ek dolgular. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fills: List<PaintLayer> = emptyList(),
    /** Ana konturun altına sırayla çizilen ek konturlar (ilki en altta). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val strokes: List<PaintLayer> = emptyList(),
    /** Opaklık maskesi: maske nesneleri (parlaklıkları görünürlüğü verir; siyah = gizli). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val opacityMask: List<Stroke>? = null,
    /** v56 aşama 9C sonradan değiştirilebilen efektler (sırayla [effectBase] geometrisine uygulanır). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val effects: List<VectorEffect> = emptyList(),
    /** Efektlerden önceki geometri (efekt yokken null). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val effectBase: EffectBase? = null,
) {
    val isDefault: Boolean get() = this == VectorStyle()
    /** Yalnız şekil çiziminde etkili seçenekler var mı. */
    val hasShapeOptions: Boolean get() = copy(blend = LayerBlend.NORMAL, opacityMask = null, effects = emptyList(), effectBase = null) != VectorStyle()
}

/** v56 aşama 9C efekt türleri. */
@Serializable enum class EffectKind { OFFSET, BEND, ROUGHEN, ZIGZAG, WAVE }

/**
 * Bir efekt: [amount] OFFSET'te mesafe (px), BEND'de -1..1 kıvırma; [size] pürüz/zikzak genliği (px); [detail] 100 px'te
 * tepe sayısı; [seed] pürüzün tohumu.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class VectorEffect(
    val kind: EffectKind,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val amount: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val size: Float = 8f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val detail: Float = 6f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val seed: Int = 1,
)

/** Efektlerden önceki noktalar ve (şekilde) alt yol nokta sayıları. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EffectBase(
    val points: List<StrokePoint>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val counts: List<Int>? = null,
)

/**
 * Bağlı kopya: kaynak [source]; kopya = M·kaynak + t (M = [[a, b], [c, d]], t = (tx, ty)). [repeat] true: Tekrarla'nın
 * ürettiği kopya (tekrar değişince silinip yeniden üretilir).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CloneLink(
    val source: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val a: Float = 1f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val b: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val c: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val d: Float = 1f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tx: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val ty: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val repeat: Boolean = false,
) {
    fun map(x: Float, y: Float): Pair<Float, Float> = (a * x + b * y + tx) to (c * x + d * y + ty)
    val scale: Float get() = sqrt(abs(a * d - b * c)).coerceIn(0.05f, 20f)
}

@Serializable enum class RepeatKind { LINEAR, GRID, RADIAL, MIRROR, STEP }

/**
 * Canlı tekrar ayarı (kaynakta saklanır): doğrusal ([count], [dx], [dy]), ızgara ([cols]×[rows], aralık [gapX]/[gapY]),
 * dairesel ([count] kopya, [radius] yarıçap), aynalı ([angle] eksen açısı, [gapX] aralık), adımla çoğalt ([count],
 * [dx]/[dy], [angle] derece, [scale] çarpanı; her adım bir öncekine uygulanır).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RepeatSpec(
    val kind: RepeatKind,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val count: Int = 4,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dx: Float = 40f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dy: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val cols: Int = 3,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val rows: Int = 3,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val gapX: Float = 20f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val gapY: Float = 20f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val radius: Float = 150f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val angle: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val scale: Float = 1f,
)

object V56Vector {
    const val MAX_COPIES = 400

    /** Elemanın noktalarının kutusu (l, t, r, b). */
    fun pointBounds(strokes: List<Stroke>): FloatArray? {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        strokes.forEach { s -> s.points.forEach { p -> l = minOf(l, p.x); t = minOf(t, p.y); r = maxOf(r, p.x); b = maxOf(b, p.y) } }
        return if (l > r) null else floatArrayOf(l, t, r, b)
    }

    private fun rad(deg: Float) = deg * PI.toFloat() / 180f

    /** p' = c + R(deg)·S(scale)·(p − c) + (dx, dy). */
    fun about(cx: Float, cy: Float, deg: Float, scale: Float, dx: Float, dy: Float, flipX: Boolean = false): CloneLink {
        val co = cos(rad(deg)) * scale; val si = sin(rad(deg)) * scale
        val fx = if (flipX) -1f else 1f
        val a = co * fx; val b = -si; val c = si * fx; val d = co
        return CloneLink("", a, b, c, d, cx - a * cx - b * cy + dx, cy - c * cx - d * cy + dy)
    }

    /** İki dönüşümün bileşimi: önce [first], sonra [then]. */
    fun compose(then: CloneLink, first: CloneLink): CloneLink = CloneLink(
        "", then.a * first.a + then.b * first.c, then.a * first.b + then.b * first.d,
        then.c * first.a + then.d * first.c, then.c * first.b + then.d * first.d,
        then.a * first.tx + then.b * first.ty + then.tx, then.c * first.tx + then.d * first.ty + then.ty,
    )

    /** Eksene ([cx], [cy] noktasından geçen, yönü [deg]) göre yansıma. */
    fun reflect(cx: Float, cy: Float, deg: Float): CloneLink {
        val t = 2f * rad(deg)
        val a = cos(t); val b = sin(t); val c = sin(t); val d = -cos(t)
        return CloneLink("", a, b, c, d, cx - a * cx - b * cy, cy - c * cx - d * cy)
    }

    /** Tekrarın kopya dönüşümleri ([box]: kaynakların kutusu). */
    fun transforms(spec: RepeatSpec, box: FloatArray): List<CloneLink> {
        val w = box[2] - box[0]; val h = box[3] - box[1]
        val cx = (box[0] + box[2]) / 2f; val cy = (box[1] + box[3]) / 2f
        val n = spec.count.coerceIn(1, MAX_COPIES)
        return when (spec.kind) {
            RepeatKind.LINEAR -> (1 until n + 1).map { i -> CloneLink("", tx = spec.dx * i, ty = spec.dy * i) }
            RepeatKind.GRID -> {
                val out = ArrayList<CloneLink>()
                for (j in 0 until spec.rows.coerceIn(1, 40)) for (i in 0 until spec.cols.coerceIn(1, 40)) {
                    if (i == 0 && j == 0) continue
                    if (out.size >= MAX_COPIES) break
                    out += CloneLink("", tx = i * (w + spec.gapX), ty = j * (h + spec.gapY))
                }
                out
            }
            RepeatKind.RADIAL -> {
                val m = n.coerceAtLeast(2)
                (1 until m).map { i -> about(cx, cy + spec.radius, 360f * i / m, 1f, 0f, 0f) }
            }
            RepeatKind.MIRROR -> {
                val ax = box[2] + spec.gapX / 2f
                listOf(reflect(ax, cy, 90f + spec.angle))
            }
            RepeatKind.STEP -> {
                val step = about(cx, cy, spec.angle, spec.scale.coerceIn(0.1f, 4f), spec.dx, spec.dy)
                var acc = step
                (1..n).map { i -> if (i == 1) step else { acc = compose(step, acc); acc } }
            }
        }
    }

    fun mapPoint(p: StrokePoint, m: CloneLink): StrokePoint = m.map(p.x, p.y).let { (x, y) -> p.at(x, y) }

    /** Elemanı dönüşümle eşler (noktalar, kırpma, maske, opaklık maskesi; kalınlıklar ölçekle). */
    fun apply(s: Stroke, m: CloneLink): Stroke {
        fun f(p: StrokePoint) = mapPoint(p, m)
        val k = m.scale
        val scaled = abs(k - 1f) > 1e-4f
        return s.copy(
            points = s.points.map(::f), clip = s.clip?.map(::f),
            mask = s.mask?.let { it.copy(points = it.points.map(::f)) },
            sizePx = if (scaled && !s.brushType.isQuad) (s.sizePx * k).coerceIn(0.5f, 2000f) else s.sizePx,
            shape = if (scaled) s.shape?.let { sp -> sp.copy(style = sp.style.copy(strokeWidth = (sp.style.strokeWidth * k).coerceIn(0.2f, 400f), cornerRadius = sp.style.cornerRadius * k)) } else s.shape,
            style2 = mapStyle(s.style2) { p -> f(p) },
        )
    }

    /** Opaklık maskesi nesnelerinin noktalarını eşler (taşıma, döndürme, ölçekleme her yerde bununla). */
    fun mapStyle(style: VectorStyle?, f: (StrokePoint) -> StrokePoint): VectorStyle? {
        if (style == null || (style.opacityMask == null && style.effectBase == null)) return style
        return style.copy(
            opacityMask = style.opacityMask?.map { ms -> ms.copy(points = ms.points.map(f), clip = ms.clip?.map(f)) },
            effectBase = style.effectBase?.let { b -> b.copy(points = b.points.map(f)) },
        )
    }

    // ---------------------------------------------------------------- bağlı kopya

    /** Seçilenlerin bağlı kopyaları ([dx], [dy] kaydırılmış, yeni kimliklerle, listenin sonunda). */
    fun clone(strokes: List<Stroke>, ids: Set<String>, dx: Float, dy: Float, newId: () -> String): List<Stroke> {
        val sel = strokes.filter { it.id in ids && !it.hidden }
        if (sel.isEmpty()) return strokes
        val groups = HashMap<String, String>()
        val copies = sel.mapIndexed { i, s ->
            val link = CloneLink(s.id, tx = dx, ty = dy)
            apply(s, link).copy(id = newId(), order = strokes.size + i, cloneOf = link, repeat = null,
                groupId = s.groupId?.let { g -> groups.getOrPut(g) { newId() } })
        }
        return strokes + copies
    }

    /** Bağı kopar (kopya sıradan elemana döner). */
    fun unlink(strokes: List<Stroke>, ids: Set<String>): List<Stroke> =
        strokes.map { if (it.id in ids && it.cloneOf != null) it.copy(cloneOf = null) else it }

    private fun center(s: Stroke): Pair<Float, Float>? = pointBounds(listOf(s))?.let { ((it[0] + it[2]) / 2f) to ((it[1] + it[3]) / 2f) }

    /**
     * Kaynağı değişen bağlı kopyaları günceller: kopya kendi başına taşındıysa (beklenen yerinden kaydıysa) kayma
     * korunur. Bağlı eleman yoksa liste aynen döner.
     */
    fun sync(old: List<Stroke>, new: List<Stroke>): List<Stroke> {
        if (new.none { it.cloneOf != null }) return new
        val oldById = old.associateBy { it.id }
        val newById = new.associateBy { it.id }
        var changed = false
        val out = new.map { cl ->
            val link = cl.cloneOf ?: return@map cl
            val src = newById[link.source] ?: return@map cl
            val oldSrc = oldById[link.source] ?: return@map cl
            if (src == oldSrc) return@map cl
            val oldCl = oldById[cl.id] ?: return@map cl
            if (oldCl != cl) return@map cl // kopya da aynı adımda değişti (birlikte taşındı)
            var l = link
            val expected = center(apply(oldSrc, link))
            val actual = center(oldCl)
            if (expected != null && actual != null) {
                val ddx = actual.first - expected.first; val ddy = actual.second - expected.second
                if (abs(ddx) > 0.01f || abs(ddy) > 0.01f) l = l.copy(tx = l.tx + ddx, ty = l.ty + ddy)
            }
            changed = true
            apply(src, l).copy(id = cl.id, order = cl.order, layerId = cl.layerId, groupId = cl.groupId, hidden = cl.hidden,
                cloneOf = l, repeat = null)
        }
        return if (changed) out else new
    }

    // ---------------------------------------------------------------- Tekrarla

    /**
     * Seçilenlere tekrar uygular: eski tekrar kopyaları silinir, kaynaklara [spec] yazılır, kopyalar kaynakların
     * grubuna (yoksa yeni gruba) girer. Dönen: yeni liste ve eklenen kopya sayısı.
     */
    fun repeat(strokes: List<Stroke>, ids: Set<String>, spec: RepeatSpec, newId: () -> String): Pair<List<Stroke>, Int> {
        val base = removeRepeat(strokes, ids)
        val sel = base.filter { it.id in ids && !it.hidden && it.cloneOf?.repeat != true }
        val box = pointBounds(sel) ?: return strokes to 0
        val group = sel.firstNotNullOfOrNull { it.groupId } ?: newId()
        val sources = sel.map { it.id }.toSet()
        val withSpec = base.map { if (it.id in sources) it.copy(repeat = spec, groupId = it.groupId ?: group) else it }
        val copies = ArrayList<Stroke>()
        transforms(spec, box).forEach { t ->
            sel.forEach { s ->
                val link = t.copy(source = s.id, repeat = true)
                copies += apply(s, link).copy(id = newId(), order = withSpec.size + copies.size, cloneOf = link, repeat = null, groupId = s.groupId ?: group)
            }
        }
        return (withSpec + copies) to copies.size
    }

    /** Seçili kaynakların tekrarını kaldırır (kopyalar silinir). */
    fun removeRepeat(strokes: List<Stroke>, ids: Set<String>): List<Stroke> {
        val sources = strokes.filter { it.id in ids && it.repeat != null }.map { it.id }.toSet() +
            strokes.filter { it.id in ids }.mapNotNull { it.cloneOf?.takeIf { l -> l.repeat }?.source }
        if (sources.isEmpty()) return strokes
        return strokes.filter { s -> !(s.cloneOf?.repeat == true && s.cloneOf!!.source in sources) }
            .map { if (it.id in sources) it.copy(repeat = null) else it }
    }

    /** Seçimdeki tekrar ayarı (kaynaktan ya da kopyadan). */
    fun repeatOf(strokes: List<Stroke>, ids: Set<String>): RepeatSpec? {
        val byId = strokes.associateBy { it.id }
        return strokes.firstNotNullOfOrNull { s ->
            if (s.id !in ids) null else s.repeat ?: s.cloneOf?.takeIf { it.repeat }?.let { byId[it.source]?.repeat }
        }
    }

    // ---------------------------------------------------------------- opaklık maskesi

    /**
     * Seçimdeki en üstteki eleman maske olur: listeden çıkar, alttaki seçili elemanlara gömülür (her birine kopyası).
     * İki elemandan az seçiliyse liste aynen döner.
     */
    fun makeOpacityMask(strokes: List<Stroke>, ids: Set<String>): List<Stroke> {
        val sel = strokes.filter { it.id in ids && !it.hidden }
        if (sel.size < 2) return strokes
        val mask = sel.maxBy { strokes.indexOf(it) }
        val targets = sel.filter { it.id != mask.id }.map { it.id }.toSet()
        return strokes.filter { it.id != mask.id }.map { s ->
            if (s.id !in targets) s
            else {
                val st = s.style2 ?: VectorStyle()
                s.copy(style2 = st.copy(opacityMask = (st.opacityMask ?: emptyList()) + mask.copy(layerId = s.layerId)))
            }
        }
    }

    /** Opaklık maskesini kaldırır: maske nesneleri (bir kez) elemanın üstüne geri döner. */
    fun releaseOpacityMask(strokes: List<Stroke>, ids: Set<String>): List<Stroke> {
        val seen = HashSet<String>()
        val out = ArrayList<Stroke>()
        strokes.forEach { s ->
            val masks = s.style2?.opacityMask
            if (s.id in ids && !masks.isNullOrEmpty()) {
                val st = s.style2!!.copy(opacityMask = null)
                out += s.copy(style2 = st.takeUnless { it.isDefault })
                masks.forEach { m -> if (seen.add(m.id)) out += m }
            } else out += s
        }
        return out.mapIndexed { i, s -> if (s.order == i) s else s.copy(order = i) }
    }

    // ---------------------------------------------------------------- serbest gradyan

    /** Ters uzaklık ağırlıklı serbest gradyan rengi (u, v: kutuda 0..1); ARGB. */
    fun freeGradientColor(points: List<GradientPoint>, u: Float, v: Float, parse: (String) -> Int): Int {
        if (points.isEmpty()) return 0
        var wr = 0f; var wg = 0f; var wb = 0f; var wa = 0f; var ws = 0f
        for (p in points) {
            val d2 = (p.x - u) * (p.x - u) + (p.y - v) * (p.y - v)
            val c = parse(p.colorHex)
            if (d2 < 1e-6f) return c
            val w = 1f / (d2 * d2)
            wa += ((c ushr 24) and 0xFF) * w; wr += ((c shr 16) and 0xFF) * w; wg += ((c shr 8) and 0xFF) * w; wb += (c and 0xFF) * w; ws += w
        }
        fun ch(v: Float) = (v / ws).toInt().coerceIn(0, 255)
        return (ch(wa) shl 24) or (ch(wr) shl 16) or (ch(wg) shl 8) or ch(wb)
    }

    /** Ok ucu çokgeni (uç noktası [tx], [ty], yön (ux, uy), boy [size]); daire ve çubuk için iki nokta. */
    fun arrowHead(kind: ArrowKind, tx: Float, ty: Float, ux: Float, uy: Float, size: Float): List<Pair<Float, Float>> {
        val px = -uy; val py = ux
        val s = max(size, 2f)
        return when (kind) {
            ArrowKind.NONE -> emptyList()
            ArrowKind.TRIANGLE, ArrowKind.OPEN -> listOf(
                (tx - ux * s + px * s * 0.5f) to (ty - uy * s + py * s * 0.5f), tx to ty,
                (tx - ux * s - px * s * 0.5f) to (ty - uy * s - py * s * 0.5f),
            )
            ArrowKind.CIRCLE -> listOf((tx - ux * s * 0.4f) to (ty - uy * s * 0.4f), (s * 0.4f) to 0f)
            ArrowKind.BAR -> listOf((tx + px * s * 0.5f) to (ty + py * s * 0.5f), (tx - px * s * 0.5f) to (ty - py * s * 0.5f))
        }
    }
}

/** ContentCodec V11'de elemanın 2E ek bilgileri (tek JSON). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class StrokeExtras2(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val style2: VectorStyle? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val cloneOf: CloneLink? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val repeat: RepeatSpec? = null,
) {
    companion object {
        fun of(s: Stroke): StrokeExtras2? = if (s.style2 == null && s.cloneOf == null && s.repeat == null) null else StrokeExtras2(s.style2, s.cloneOf, s.repeat)
    }
}
