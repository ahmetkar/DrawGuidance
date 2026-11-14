package com.karapp.drawguidance.ui.drawing

import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.TipParams
import com.karapp.drawguidance.data.model.TipShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v51 piksel tabanlı fırça motoru: boya fırçaları (akrilik, yağlı boya, bulut/duman, karıştırıcı) ve yardımcı fırçalar
 * (bulanıklaştır, keskinleştir, aç, koyulaştır, sünger, klon, onar).
 *
 * Saf hesaptır (Android'e bağlı değil; iOS `RasterBrush.swift` aynı adımları izler). Girdi, izin altındaki aynı katman
 * içeriğinin ön çarpımlı RGBA görüntüsüdür ([Patch]); çıktı aynı bölge için sonuç rengi ve karışım maskesidir:
 * `ekran = alt × (1 − maske) + sonuç × maske`. Çizici bunu "maskeyle sil (DST_OUT) + sonuç×maskeyi ekle (ADD)" olarak
 * uygular. Rastgelelik tuval koordinatından ve çizim kimliğinden türetilir: aynı çizim her seferinde aynı görünür.
 */
object RasterBrush {

    /** Bir bölgenin pikselleri: sol-üst köşesi (tuval birimi), ölçek (piksel/tuval birimi), boyut ve ön çarpımlı RGBA (0..1). */
    class Patch(val left: Float, val top: Float, val scale: Float, val w: Int, val h: Int) {
        val rgba = FloatArray(w * h * 4)
    }

    /** Sonuç: ön çarpımlı renk (maske uygulanmamış) ve maske (0..1). */
    class Result(val patch: Patch, val color: FloatArray, val mask: FloatArray)

    /** Fırçanın kendi varsayılan uç ve boya ayarları (kullanıcı ayarı null ise bunlar kullanılır). */
    fun defaults(type: BrushType): TipParams = when (type) {
        BrushType.ACRYLIC -> TipParams(
            minSize = 0.55f, maxSize = 1f, hardness = 0.8f, softness = 0.1f, aspect = 0.8f, angle = 0f, rotation = 0f, roundness = 0.6f,
            shape = TipShape.FLAT, edge = 0.6f, density = 0.95f, buildUp = 0f, wetness = 0.1f, paintAmount = 0.85f, thickness = 0.3f,
            colorAmount = 0.9f, smudge = 0.15f, blend = 0.15f,
        )
        BrushType.OIL_PAINT -> TipParams(
            minSize = 0.5f, maxSize = 1f, hardness = 0.7f, softness = 0.15f, aspect = 0.75f, angle = 0f, rotation = 0f, roundness = 0.9f,
            shape = TipShape.BRISTLE, edge = 0.5f, density = 0.9f, buildUp = 0.2f, wetness = 0.45f, paintAmount = 0.75f, thickness = 0.65f,
            colorAmount = 0.75f, smudge = 0.4f, blend = 0.35f,
        )
        BrushType.CLOUD_SMOKE -> TipParams(
            minSize = 0.6f, maxSize = 1f, hardness = 0f, softness = 0.6f, aspect = 1f, angle = 0f, rotation = 1f, roundness = 1f,
            shape = TipShape.CLOUD, edge = 0.2f, density = 0.7f, buildUp = 0.4f, wetness = 0.3f, paintAmount = 1f, thickness = 0f,
            colorAmount = 1f, smudge = 0.1f, blend = 0.2f,
        )
        BrushType.FX_MIXER -> TipParams(
            minSize = 0.6f, maxSize = 1f, hardness = 0.45f, softness = 0.3f, aspect = 1f, angle = 0f, rotation = 0f, roundness = 1f,
            shape = TipShape.ROUND, edge = 0.4f, density = 1f, buildUp = 0f, wetness = 0.7f, paintAmount = 1f, thickness = 0f,
            colorAmount = 0.15f, smudge = 0.75f, blend = 0.55f,
        )
        else -> TipParams(
            minSize = 0.6f, maxSize = 1f, hardness = 0.3f, softness = 0.3f, aspect = 1f, angle = 0f, rotation = 0f, roundness = 1f,
            shape = TipShape.ROUND, edge = 0.4f, density = 1f, buildUp = 0f, wetness = 0f, paintAmount = 1f, thickness = 0f,
            colorAmount = 1f, smudge = 0f, blend = 0f,
        )
    }

    /** Kullanıcı ayarlarıyla birleşmiş etkin uç ayarları (hiç null alan kalmaz). */
    fun effectiveTip(type: BrushType, params: BrushParams?): TipParams {
        val d = defaults(type)
        val u = params?.tip ?: return d
        return TipParams(
            minSize = u.minSize ?: d.minSize, maxSize = u.maxSize ?: d.maxSize, hardness = u.hardness ?: d.hardness,
            softness = u.softness ?: d.softness, aspect = u.aspect ?: d.aspect, angle = u.angle ?: d.angle,
            rotation = u.rotation ?: d.rotation, roundness = u.roundness ?: d.roundness, shape = u.shape ?: d.shape,
            edge = u.edge ?: d.edge, density = u.density ?: d.density, buildUp = u.buildUp ?: d.buildUp,
            wetness = u.wetness ?: d.wetness, paintAmount = u.paintAmount ?: d.paintAmount, thickness = u.thickness ?: d.thickness,
            colorAmount = u.colorAmount ?: d.colorAmount, smudge = u.smudge ?: d.smudge, blend = u.blend ?: d.blend,
        )
    }

    /** Boya bırakan (karışım motoru) fırçalar; diğerleri süzgeç uygular. */
    fun paints(type: BrushType): Boolean =
        type == BrushType.ACRYLIC || type == BrushType.OIL_PAINT || type == BrushType.CLOUD_SMOKE || type == BrushType.FX_MIXER

    /** Klon/onarma kaynağının hedefe göre kayması (tuval birimi). */
    fun sourceOffset(type: BrushType, params: BrushParams?, width: Float): Pair<Float, Float>? {
        if (type != BrushType.FX_CLONE && type != BrushType.FX_HEAL) return null
        val fx = params?.fx
        val dx = fx?.srcDx; val dy = fx?.srcDy
        return if (dx != null && dy != null) dx to dy else (width * 1.2f) to (width * 0.4f)
    }

    /** Süzgeçlerin okuduğu ek pay (bulanıklık yarıçapı; tuval birimi). */
    fun readMargin(type: BrushType, width: Float, strength: Float): Float = when (type) {
        BrushType.FX_BLUR -> blurRadius(width, strength) * 3f
        BrushType.FX_SHARPEN -> width * 0.2f
        BrushType.FX_HEAL -> width * 1.2f
        else -> 2f
    }

    private fun blurRadius(width: Float, strength: Float) = max(1f, width * (0.06f + 0.14f * strength.coerceIn(0f, 1f)))

    /**
     * İzi hesaplar. [base] izin altındaki içerik (null: boş/şeffaf). Noktalar tuval biriminde; kalem dizileri yoksa null.
     * [color] fırça rengi (ARGB).
     */
    fun render(
        base: Patch,
        type: BrushType, color: Int, sizePx: Float, opacity: Float, flow: Float, strength: Float, seed: Int,
        xs: FloatArray, ys: FloatArray, count: Int,
        pp: FloatArray?, pt: FloatArray?, po: FloatArray?, pr: FloatArray?, pb: FloatArray?,
        params: BrushParams?,
    ): Result {
        val w = base.w; val h = base.h; val s = base.scale
        val n = w * h
        val tip = effectiveTip(type, params)
        val style = type.style()
        val width = type.inkWidth(sizePx, strength)
        val pen = PenModel.of(params?.copy(tip = tip))
        val data = PenModel.PointData(xs, ys, count, pp, pt, po, pr, pb, width)
        val dynamics = BrushDynamics.of(type).with(params)
        val hasPen = pp != null || pt != null
        val spacing = (params?.spacing ?: style.spacing).coerceIn(0.02f, 3f)
        val step = max(0.5f / s, width * spacing)
        val paints = paints(type)
        val mask = FloatArray(n)
        val cur = if (paints) base.rgba.copyOf() else base.rgba
        val dep = FloatArray(n)
        val heightMap = if (paints && (tip.thickness ?: 0f) > 0.01f) FloatArray(n) else null
        val opacityCeil = (opacity * style.alphaFactor).coerceIn(0f, 1f)
        val buildUp = tip.buildUp ?: 0f
        val ceiling = opacityCeil + (1f - opacityCeil) * buildUp
        val dabAlpha = StrokeStream.dabFlow(flow.coerceIn(0.02f, 1f), spacing) * (0.35f + 0.65f * strength.coerceIn(0f, 1f))
        val bc = floatArrayOf(
            ((color shr 16) and 0xFF) / 255f, ((color shr 8) and 0xFF) / 255f, (color and 0xFF) / 255f,
        )
        val carried = floatArrayOf(bc[0], bc[1], bc[2], 1f)
        var carriedInit = false
        val shape = tip.shape ?: TipShape.ROUND
        val hardness = (tip.hardness ?: 0.5f).coerceIn(0f, 1f)
        val softness = (tip.softness ?: 0f).coerceIn(0f, 1f)
        val aspect = when (shape) { TipShape.FLAT -> min(tip.aspect ?: 1f, 0.35f); TipShape.CHISEL -> min(tip.aspect ?: 1f, 0.55f); else -> tip.aspect ?: 1f }.coerceIn(0.05f, 1f)
        val roundness = (tip.roundness ?: 1f).coerceIn(0f, 1f)
        val edge = (tip.edge ?: 0.5f).coerceIn(0f, 1f)
        val density = (tip.density ?: 1f).coerceIn(0f, 1f)
        val wet = (tip.wetness ?: 0f).coerceIn(0f, 1f)
        val load0 = (tip.paintAmount ?: 1f).coerceIn(0f, 1f)
        val colorAmount = (tip.colorAmount ?: 1f).coerceIn(0f, 1f)
        val smudge = (tip.smudge ?: 0f).coerceIn(0f, 1f)
        val blend = (tip.blend ?: 0f).coerceIn(0f, 1f)
        val baseAngle = ((tip.angle ?: 0f) * PI / 180.0).toFloat()
        val rotJitter = (tip.rotation ?: 0f).coerceIn(0f, 1f)
        val exponent = if (shape == TipShape.SQUARE || shape == TipShape.FLAT || shape == TipShape.CHISEL) 2f + (1f - roundness) * 14f else 2f
        val outer = 1f + softness * 0.6f
        val inner = (hardness * (1f - softness * 0.5f)).coerceAtMost(outer - 0.02f)

        // Örnekler: ham noktalar boyunca eşit aralıklı
        var acc = 0f
        var next = 0f
        var k = 0
        fun dab(cx: Float, cy: Float, dx: Float, dy: Float, along: Float, raw: Int) {
            val baseW = if (!hasPen) 1f else dynamics.width(if (pen?.overridesPressure == true) 1f else data.pressure(raw), data.tilt(raw))
            val sizeF = pen?.size(baseW, data, raw) ?: baseW
            val alphaF = (if (!hasPen) 1f else dynamics.alpha(data.pressure(raw), data.tilt(raw))) * (pen?.alpha(data, raw) ?: 1f)
            val rx = max(0.5f, width * 0.5f * sizeF * s)
            val ry = max(0.5f, rx * aspect)
            var ang = baseAngle + (pen?.turn(data, raw, dx, dy) ?: 0f)
            if (rotJitter > 0f) ang += (DabRandom.unit(seed, k, 31) - 0.5f) * 2f * PI.toFloat() * rotJitter
            val ca = cos(ang); val sa = sin(ang)
            val load = if (paints && load0 < 0.999f) max(0.06f, exp(-along / max(1f, width * (1.5f + 40f * load0)))) else 1f
            val a0 = dabAlpha * alphaF * load
            if (a0 <= 0.0005f) return
            val px = (cx - base.left) * s; val py = (cy - base.top) * s
            val reach = max(rx, ry) * outer + 1f
            val x0 = max(0, floor(px - reach).toInt()); val x1 = min(w - 1, ceil(px + reach).toInt())
            val y0 = max(0, floor(py - reach).toInt()); val y1 = min(h - 1, ceil(py + reach).toInt())
            if (x1 < x0 || y1 < y0) return
            // Boya: dokunulan yerin rengini al, taşınan rengi güncelle, fırça rengiyle karıştır
            var mr = bc[0]; var mg = bc[1]; var mb = bc[2]
            if (paints) {
                val pick = FloatArray(4)
                sampleAverage(cur, w, h, px, py, rx * 0.5f, pick)
                if (pick[3] > 0.01f) {
                    val ur = pick[0] / pick[3]; val ug = pick[1] / pick[3]; val ub = pick[2] / pick[3]
                    if (!carriedInit) { carried[0] = ur; carried[1] = ug; carried[2] = ub; carriedInit = true }
                    val keep = smudge * 0.95f
                    carried[0] = carried[0] * keep + ur * (1f - keep)
                    carried[1] = carried[1] * keep + ug * (1f - keep)
                    carried[2] = carried[2] * keep + ub * (1f - keep)
                    val pickupW = (1f - colorAmount) * pick[3]
                    mr = bc[0] * (1f - pickupW) + carried[0] * pickupW
                    mg = bc[1] * (1f - pickupW) + carried[1] * pickupW
                    mb = bc[2] * (1f - pickupW) + carried[2] * pickupW
                    val wetMix = wet * 0.45f * pick[3]
                    mr = mr * (1f - wetMix) + ur * wetMix; mg = mg * (1f - wetMix) + ug * wetMix; mb = mb * (1f - wetMix) + ub * wetMix
                }
            }
            val ndx = if (dx == 0f && dy == 0f) 1f else dx
            val ndy = dy
            for (yy in y0..y1) {
                val fy = yy + 0.5f - py
                for (xx in x0..x1) {
                    val fx = xx + 0.5f - px
                    val lx = (fx * ca + fy * sa) / rx
                    val ly = (-fx * sa + fy * ca) / ry
                    val d = when (shape) {
                        TipShape.SQUARE, TipShape.FLAT -> (abs(lx).pow(exponent) + abs(ly).pow(exponent)).pow(1f / exponent)
                        TipShape.CHISEL -> (abs(lx) * 0.7f + abs(ly) * 0.7f + abs(lx - ly) * 0.3f)
                        else -> sqrt(lx * lx + ly * ly)
                    }
                    if (d >= outer) continue
                    var c = if (d <= inner) 1f else {
                        val t = ((d - inner) / (outer - inner)).coerceIn(0f, 1f)
                        val ts = ((t - 0.5f) * (1f + edge * 6f) + 0.5f).coerceIn(0f, 1f)
                        1f - ts * ts * (3f - 2f * ts)
                    }
                    // Tuvale sabit doku ve uç dokusu
                    val gx = base.left + (xx + 0.5f) / s; val gy = base.top + (yy + 0.5f) / s
                    when (shape) {
                        TipShape.BRISTLE -> {
                            val perp = (fx * -ndy + fy * ndx) / s / max(0.8f, width * 0.045f)
                            val bristle = hash1(floor(perp).toInt(), seed)
                            c *= 0.45f + 0.55f * bristle
                        }
                        TipShape.SPONGE -> c *= smooth(0.3f, 0.7f, valueNoise(gx / max(1f, width * 0.12f), gy / max(1f, width * 0.12f), seed))
                        TipShape.SPLATTER -> c *= if (hash2(floor(gx / max(1f, width * 0.08f)).toInt(), floor(gy / max(1f, width * 0.08f)).toInt(), seed + k) > 0.72f) 1f else 0f
                        TipShape.CLOUD -> {
                            val sc = max(2f, width * 0.35f)
                            c *= smooth(0.2f, 0.9f, fbm(gx / sc, gy / sc, seed))
                        }
                        TipShape.FLAT -> if (type == BrushType.ACRYLIC) {
                            val perp = (fx * -ndy + fy * ndx) / s / max(0.8f, width * 0.06f)
                            c *= 0.75f + 0.25f * hash1(floor(perp).toInt(), seed)
                        }
                        else -> Unit
                    }
                    if (density < 0.999f) c *= smooth(1f - density - 0.12f, 1f - density + 0.12f, valueNoise(gx * 0.6f, gy * 0.6f, seed xor 0x5bd1))
                    if (c <= 0.001f) continue
                    val i = yy * w + xx
                    var a = c * a0
                    if (paints) {
                        val room = ceiling - dep[i]
                        if (room <= 0f) continue
                        if (a > room) a = room
                        dep[i] += a
                        val o = i * 4
                        var tr = mr; var tg = mg; var tb = mb
                        val ca0 = cur[o + 3]
                        if (blend > 0f && ca0 > 0.01f) {
                            val bw = blend * 0.6f * ca0
                            tr = tr * (1f - bw) + cur[o] / ca0 * bw; tg = tg * (1f - bw) + cur[o + 1] / ca0 * bw; tb = tb * (1f - bw) + cur[o + 2] / ca0 * bw
                        }
                        cur[o] = tr * a + cur[o] * (1f - a)
                        cur[o + 1] = tg * a + cur[o + 1] * (1f - a)
                        cur[o + 2] = tb * a + cur[o + 2] * (1f - a)
                        cur[o + 3] = a + ca0 * (1f - a)
                        heightMap?.let { it[i] += a * (tip.thickness ?: 0f) }
                        mask[i] = 1f
                    } else {
                        val m = mask[i]
                        mask[i] = min(ceiling, m + a * (1f - m))
                    }
                }
            }
            k++
        }

        if (count == 1) {
            dab(xs[0], ys[0], 1f, 0f, 0f, 0)
        } else {
            for (i in 1 until count) {
                val ax = xs[i - 1]; val ay = ys[i - 1]; val bx = xs[i]; val by = ys[i]
                val len = hypot(bx - ax, by - ay)
                if (len <= 0f) continue
                val ux = (bx - ax) / len; val uy = (by - ay) / len
                while (next <= acc + len) {
                    val t = (next - acc) / len
                    dab(ax + (bx - ax) * t, ay + (by - ay) * t, ux, uy, next, i)
                    next += step
                }
                acc += len
            }
            if (k == 0) dab(xs[0], ys[0], 1f, 0f, 0f, 0)
        }

        val out: FloatArray = if (paints) {
            heightMap?.let { impasto(cur, it, w, h, mask) }
            cur
        } else filter(type, base, params, width, strength, mask)
        return Result(base, out, mask)
    }

    /** Kalın boya: yükseklik haritasından sol-üstten gelen ışıkla kabartma gölgesi. */
    private fun impasto(cur: FloatArray, hm: FloatArray, w: Int, h: Int, mask: FloatArray) {
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (mask[i] <= 0f) continue
            val gx = hm[i + 1] - hm[i - 1]
            val gy = hm[i + w] - hm[i - w]
            val shade = (-(gx + gy) * 0.9f).coerceIn(-0.35f, 0.35f)
            if (abs(shade) < 0.002f) continue
            val o = i * 4
            val a = cur[o + 3]
            if (shade > 0f) {
                for (c in 0 until 3) cur[o + c] = cur[o + c] + (a - cur[o + c]) * shade
            } else {
                for (c in 0 until 3) cur[o + c] = cur[o + c] * (1f + shade)
            }
        }
    }

    /** Yardımcı fırçaların süzgeci: alttaki içerikten (ön çarpımlı) sonuç rengi. */
    private fun filter(type: BrushType, base: Patch, params: BrushParams?, width: Float, strength: Float, mask: FloatArray): FloatArray {
        val w = base.w; val h = base.h; val s = base.scale
        val src = base.rgba
        val n = w * h
        val out = FloatArray(n * 4)
        val fx = params?.fx
        when (type) {
            BrushType.FX_BLUR -> {
                val r = max(1, (blurRadius(width, strength) * s).roundToInt())
                val b = boxBlur3(src, w, h, r)
                System.arraycopy(b, 0, out, 0, out.size)
            }
            BrushType.FX_SHARPEN -> {
                val r = max(1, (width * 0.06f * s).roundToInt())
                val b = boxBlur3(src, w, h, r)
                val amount = 0.8f + 1.2f * strength.coerceIn(0f, 1f)
                for (i in 0 until n) {
                    val o = i * 4
                    val a = src[o + 3]
                    out[o + 3] = a
                    for (c in 0 until 3) out[o + c] = (src[o + c] + amount * (src[o + c] - b[o + c])).coerceIn(0f, a)
                }
            }
            BrushType.FX_DODGE, BrushType.FX_BURN -> {
                val dodge = type == BrushType.FX_DODGE
                val k = 0.25f + 0.45f * strength.coerceIn(0f, 1f)
                val range = fx?.mode ?: 1
                for (i in 0 until n) {
                    val o = i * 4
                    val a = src[o + 3]
                    out[o + 3] = a
                    if (a <= 0.001f) continue
                    val r = src[o] / a; val g = src[o + 1] / a; val b = src[o + 2] / a
                    val lum = 0.299f * r + 0.587f * g + 0.114f * b
                    val wgt = when (range) { 0 -> (1f - lum).pow(2); 2 -> lum * lum; else -> 1f - abs(2f * lum - 1f) }
                    val kk = k * (0.35f + 0.65f * wgt)
                    val nr = if (dodge) r + (1f - r) * kk else r * (1f - kk)
                    val ng = if (dodge) g + (1f - g) * kk else g * (1f - kk)
                    val nb = if (dodge) b + (1f - b) * kk else b * (1f - kk)
                    out[o] = nr * a; out[o + 1] = ng * a; out[o + 2] = nb * a
                }
            }
            BrushType.FX_SPONGE -> {
                val saturate = (fx?.mode ?: 0) == 1
                val k = 0.3f + 0.6f * strength.coerceIn(0f, 1f)
                for (i in 0 until n) {
                    val o = i * 4
                    val a = src[o + 3]
                    out[o + 3] = a
                    if (a <= 0.001f) continue
                    val r = src[o] / a; val g = src[o + 1] / a; val b = src[o + 2] / a
                    val lum = 0.299f * r + 0.587f * g + 0.114f * b
                    val f = if (saturate) 1f + k else 1f - k
                    out[o] = (lum + (r - lum) * f).coerceIn(0f, 1f) * a
                    out[o + 1] = (lum + (g - lum) * f).coerceIn(0f, 1f) * a
                    out[o + 2] = (lum + (b - lum) * f).coerceIn(0f, 1f) * a
                }
            }
            BrushType.FX_CLONE, BrushType.FX_HEAL -> {
                val (sdx, sdy) = sourceOffset(type, params, width) ?: (0f to 0f)
                val ox = (sdx * s).roundToInt(); val oy = (sdy * s).roundToInt()
                val shifted = FloatArray(n * 4)
                for (y in 0 until h) {
                    val sy = y + oy
                    if (sy < 0 || sy >= h) continue
                    for (x in 0 until w) {
                        val sx = x + ox
                        if (sx < 0 || sx >= w) continue
                        System.arraycopy(src, (sy * w + sx) * 4, shifted, (y * w + x) * 4, 4)
                    }
                }
                if (type == BrushType.FX_CLONE) {
                    System.arraycopy(shifted, 0, out, 0, out.size)
                } else {
                    // Onarma: kaynağın ayrıntısı (yüksek frekans) + hedefin ışığı ve rengi (düşük frekans)
                    val r = max(1, (width * 0.3f * s).roundToInt())
                    val lowDst = boxBlur3(src, w, h, r)
                    val lowSrc = boxBlur3(shifted, w, h, r)
                    for (i in 0 until n * 4 step 4) {
                        val a = (lowDst[i + 3] + (shifted[i + 3] - lowSrc[i + 3])).coerceIn(0f, 1f)
                        out[i + 3] = a
                        for (c in 0 until 3) out[i + c] = (lowDst[i + c] + (shifted[i + c] - lowSrc[i + c])).coerceIn(0f, a)
                    }
                }
            }
            else -> System.arraycopy(src, 0, out, 0, out.size)
        }
        return out
    }

    /** Ortalama renk (ön çarpımlı) — damga merkezi ve çevresindeki 4 nokta. */
    private fun sampleAverage(img: FloatArray, w: Int, h: Int, px: Float, py: Float, r: Float, out: FloatArray) {
        out.fill(0f)
        var cnt = 0
        val offs = floatArrayOf(0f, 0f, r, 0f, -r, 0f, 0f, r, 0f, -r)
        for (j in 0 until 5) {
            val x = (px + offs[j * 2]).toInt(); val y = (py + offs[j * 2 + 1]).toInt()
            if (x < 0 || y < 0 || x >= w || y >= h) continue
            val o = (y * w + x) * 4
            for (c in 0 until 4) out[c] += img[o + c]
            cnt++
        }
        if (cnt > 0) for (c in 0 until 4) out[c] /= cnt
    }

    /** Üç geçişli kutu bulanıklığı (Gauss'a yakın), ön çarpımlı RGBA. */
    fun boxBlur3(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        var a = src.copyOf()
        var b = FloatArray(src.size)
        repeat(3) {
            boxPass(a, b, w, h, r, horizontal = true)
            boxPass(b, a, w, h, r, horizontal = false)
        }
        return a
    }

    private fun boxPass(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val stride = if (horizontal) 4 else w * 4
        val inv = 1f / (2 * r + 1)
        val sum = FloatArray(4)
        for (line in 0 until lines) {
            val start = if (horizontal) line * w * 4 else line * 4
            sum.fill(0f)
            for (t in -r..r) {
                val idx = start + t.coerceIn(0, len - 1) * stride
                for (c in 0 until 4) sum[c] += src[idx + c]
            }
            for (t in 0 until len) {
                val o = start + t * stride
                for (c in 0 until 4) dst[o + c] = sum[c] * inv
                val add = start + (t + r + 1).coerceAtMost(len - 1) * stride
                val rem = start + (t - r).coerceAtLeast(0) * stride
                for (c in 0 until 4) sum[c] += src[add + c] - src[rem + c]
            }
        }
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun mix32(v: Int): Int {
        var x = v
        x = x xor (x ushr 16); x *= 0x7feb352d; x = x xor (x ushr 15); x *= -0x7b935975; x = x xor (x ushr 16)
        return x
    }

    fun hash1(i: Int, seed: Int): Float = (mix32(i * 0x27d4eb2d + seed * 0x165667b1) ushr 8) / 16777216f
    fun hash2(x: Int, y: Int, seed: Int): Float = (mix32(x * 0x1f123bb5 + y * 0x5f356495 + seed * 0x2c1b3c6d) ushr 8) / 16777216f

    /** Tuvale sabit değer gürültüsü (0..1). */
    fun valueNoise(x: Float, y: Float, seed: Int): Float {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val fx = x - x0; val fy = y - y0
        val ux = fx * fx * (3f - 2f * fx); val uy = fy * fy * (3f - 2f * fy)
        val a = hash2(x0, y0, seed); val b = hash2(x0 + 1, y0, seed)
        val c = hash2(x0, y0 + 1, seed); val d = hash2(x0 + 1, y0 + 1, seed)
        return (a + (b - a) * ux) * (1f - uy) + (c + (d - c) * ux) * uy
    }

    fun fbm(x: Float, y: Float, seed: Int): Float {
        var sum = 0f; var amp = 0.5f; var f = 1f
        for (o in 0 until 4) { sum += amp * valueNoise(x * f, y * f, seed + o * 101); amp *= 0.5f; f *= 2.03f }
        return sum / 0.9375f
    }
}
