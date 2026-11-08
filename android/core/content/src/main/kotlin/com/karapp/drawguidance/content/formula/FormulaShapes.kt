package com.karapp.drawguidance.content.formula

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Yazı tipine bırakılamayan (esneyen ya da tipten bağımsız olması gereken) işaretlerin geometrisi: esnek ayraçlar,
 * integral, kök işareti, noktalar. Hepsi [DrawOp.Stroke] (dolu çokgen ya da çizgi) üretir.
 */
internal object FormulaShapes {

    /** Değişken kalınlıklı şerit: orta çizgi [pts] (x,y çiftleri) boyunca [widths] genişliğinde dolu çokgen. */
    fun ribbon(pts: FloatArray, widths: FloatArray): DrawOp.Stroke {
        val n = pts.size / 2
        val left = FloatArray(n * 2)
        val right = FloatArray(n * 2)
        for (k in 0 until n) {
            val a = max(0, k - 1)
            val b = min(n - 1, k + 1)
            var tx = pts[b * 2] - pts[a * 2]
            var ty = pts[b * 2 + 1] - pts[a * 2 + 1]
            val len = sqrt(tx * tx + ty * ty).takeIf { it > 1e-6f } ?: 1f
            tx /= len; ty /= len
            val hw = widths[k] / 2f
            left[k * 2] = pts[k * 2] - ty * hw
            left[k * 2 + 1] = pts[k * 2 + 1] + tx * hw
            right[k * 2] = pts[k * 2] + ty * hw
            right[k * 2 + 1] = pts[k * 2 + 1] - tx * hw
        }
        val poly = ArrayList<Float>(n * 4)
        for (k in 0 until n) { poly.add(left[k * 2]); poly.add(left[k * 2 + 1]) }
        for (k in n - 1 downTo 0) { poly.add(right[k * 2]); poly.add(right[k * 2 + 1]) }
        return DrawOp.Stroke(poly, 0f, closed = true, filled = true)
    }

    fun circle(cx: Float, cy: Float, r: Float, segments: Int = 14): DrawOp.Stroke {
        val pts = ArrayList<Float>(segments * 2)
        for (k in 0 until segments) {
            val a = (2.0 * Math.PI * k / segments)
            pts.add(cx + r * cos(a).toFloat()); pts.add(cy + r * sin(a).toFloat())
        }
        return DrawOp.Stroke(pts, 0f, closed = true, filled = true)
    }

    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, thickness: Float, segments: Int = 24): DrawOp.Stroke {
        val pts = ArrayList<Float>(segments * 2)
        for (k in 0 until segments) {
            val a = (2.0 * Math.PI * k / segments)
            pts.add(cx + rx * cos(a).toFloat()); pts.add(cy + ry * sin(a).toFloat())
        }
        return DrawOp.Stroke(pts, thickness, closed = true, filled = false)
    }

    /** Catmull-Rom eğrisi: kontrol noktalarından geçen yumuşak orta çizgi. */
    private fun spline(ctrl: FloatArray, steps: Int): FloatArray {
        val m = ctrl.size / 2
        val out = ArrayList<Float>()
        fun p(k: Int, c: Int) = ctrl[k.coerceIn(0, m - 1) * 2 + c]
        for (seg in 0 until m - 1) {
            for (s in 0 until steps) {
                val t = s.toFloat() / steps
                val t2 = t * t
                val t3 = t2 * t
                for (c in 0..1) {
                    val p0 = p(seg - 1, c); val p1 = p(seg, c); val p2 = p(seg + 1, c); val p3 = p(seg + 2, c)
                    out.add(
                        0.5f * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3),
                    )
                }
            }
        }
        out.add(ctrl[(m - 1) * 2]); out.add(ctrl[(m - 1) * 2 + 1])
        return out.toFloatArray()
    }

    // Eğik S biçimli integral: (u, v) birim kare içinde, u yatay (yükseklik H ile ölçeklenir), v dikey.
    private val INTEGRAL = floatArrayOf(
        0.425f, 0.078f, 0.405f, 0.018f, 0.362f, 0.0f, 0.318f, 0.03f, 0.292f, 0.12f,
        0.22f, 0.5f,
        0.148f, 0.88f, 0.122f, 0.97f, 0.078f, 1.0f, 0.035f, 0.982f, 0.015f, 0.922f,
    )

    /** Integral işareti; sol üst köşe (x0, top), yükseklik h. */
    fun integral(x0: Float, top: Float, h: Float, maxWidth: Float): List<DrawOp> {
        val ctrl = FloatArray(INTEGRAL.size)
        for (k in INTEGRAL.indices step 2) {
            ctrl[k] = x0 + INTEGRAL[k] * h
            ctrl[k + 1] = top + INTEGRAL[k + 1] * h
        }
        val pts = spline(ctrl, 8)
        val n = pts.size / 2
        val tMin = maxWidth * 0.32f
        val widths = FloatArray(n) { k ->
            val s = k.toFloat() / (n - 1)
            val z = (s - 0.5f) / 0.21f
            tMin + (maxWidth - tMin) * kotlin.math.exp(-z * z)
        }
        val r = max(maxWidth * 0.6f, h * 0.028f)
        return listOf(
            ribbon(pts, widths),
            circle(pts[0] - r * 0.25f, pts[1] + r * 0.15f, r),
            circle(pts[(n - 1) * 2] + r * 0.25f, pts[(n - 1) * 2 + 1] - r * 0.15f, r),
        )
    }

    /** Esnek ayraç çizimi; kutu [x0, x0+width] × [top, bottom]. Genişliği [delimiterWidth] verir. */
    fun delimiterWidth(ch: String, h: Float, em: Float): Float = when (ch) {
        "(", ")" -> 0.2f * em + 0.07f * h.coerceAtMost(6 * em) + 0.12f * em
        "[", "]", "⌊", "⌋", "⌈", "⌉" -> 0.26f * em + 0.03f * h.coerceAtMost(6 * em) + 0.1f * em
        "{", "}" -> 0.32f * em + 0.05f * h.coerceAtMost(6 * em) + 0.1f * em
        "⟨", "⟩" -> 0.24f * em + 0.08f * h.coerceAtMost(6 * em) + 0.1f * em
        "|" -> 0.28f * em
        "‖" -> 0.42f * em
        else -> 0.12f * em
    }

    fun delimiter(ch: String, x0: Float, top: Float, bottom: Float, em: Float, rule: Float): List<DrawOp> {
        val h = bottom - top
        val w = delimiterWidth(ch, h, em)
        val sb = 0.06f * em
        val l = x0 + sb
        val r = x0 + w - sb
        val mid = (top + bottom) / 2
        val stem = max(rule * 1.25f, min(0.085f * em, rule * 1.25f + 0.006f * h))
        return when (ch) {
            "(", ")" -> {
                val left = ch == "("
                val n = 28
                val k = (1.8f + 0.45f * (h / em - 1.2f)).coerceIn(1.8f, 4f)
                val thMid = max(rule * 1.3f, min(0.11f * em, 0.07f * em + 0.012f * h))
                val thEnd = max(rule * 0.55f, 0.03f * em)
                val bulge = (r - l) - thMid / 2
                val pts = FloatArray(n * 2)
                val ws = FloatArray(n)
                for (j in 0 until n) {
                    val s = -1f + 2f * j / (n - 1)
                    val off = bulge * (1f - abs(s).pow(k))
                    val xEdge = if (left) r - thEnd / 2 else l + thEnd / 2
                    pts[j * 2] = if (left) xEdge - off else xEdge + off
                    pts[j * 2 + 1] = mid + s * (h / 2 - thEnd / 2)
                    ws[j] = thEnd + (thMid - thEnd) * (1f - s * s)
                }
                listOf(ribbon(pts, ws))
            }
            "[", "]", "⌊", "⌋", "⌈", "⌉" -> {
                val left = ch == "[" || ch == "⌊" || ch == "⌈"
                val arm = max(rule * 0.95f, 0.045f * em)
                val topArm = ch == "[" || ch == "]" || ch == "⌈" || ch == "⌉"
                val botArm = ch == "[" || ch == "]" || ch == "⌊" || ch == "⌋"
                val sx = if (left) l else r - stem
                val out = ArrayList<DrawOp>()
                out.add(rect(sx, top, sx + stem, bottom))
                val ax0 = if (left) l else l
                val ax1 = if (left) r else r
                if (topArm) out.add(rect(ax0, top, ax1, top + arm))
                if (botArm) out.add(rect(ax0, bottom - arm, ax1, bottom))
                out
            }
            "{", "}" -> {
                val left = ch == "{"
                val thick = max(rule * 1.3f, min(0.09f * em, 0.07f * em + 0.008f * h))
                val thin = max(rule * 0.5f, 0.028f * em)
                val xm = (l + r) / 2
                val tip = if (left) l else r      // ortadaki sivri uç
                val end = if (left) r else l      // üst/alt uçlar
                val rr = min((h / 2) * 0.32f, 0.3f * em + 0.04f * h)
                fun half(sign: Float): DrawOp.Stroke {
                    // sign=-1 üst yarı, +1 alt yarı: uç → dikey gövde → orta uç
                    val y0 = mid + sign * (h / 2 - thin / 2)
                    val ctrl = floatArrayOf(
                        end, y0,
                        xm + (end - xm) * 0.35f, y0 - sign * rr * 0.12f,
                        xm, y0 - sign * rr * 0.75f,
                        xm, mid + sign * rr * 0.75f,
                        xm - (xm - tip) * 0.35f, mid + sign * rr * 0.12f,
                        tip, mid,
                    )
                    val pts = spline(ctrl, 7)
                    val n = pts.size / 2
                    val ws = FloatArray(n) { j ->
                        val s = j.toFloat() / (n - 1)
                        val z = (s - 0.5f) / 0.26f
                        thin + (thick - thin) * kotlin.math.exp(-z * z * z * z)
                    }
                    return ribbon(pts, ws)
                }
                listOf(half(-1f), half(1f))
            }
            "⟨", "⟩" -> {
                val left = ch == "⟨"
                val a = if (left) r else l
                val b = if (left) l else r
                listOf(DrawOp.Stroke(listOf(a, top + rule / 2, b, mid, a, bottom - rule / 2), rule * 1.1f))
            }
            "|" -> {
                val x = (l + r) / 2
                listOf(DrawOp.Stroke(listOf(x, top, x, bottom), stem * 0.9f))
            }
            "‖" -> {
                val gap = 0.14f * em
                val x = (l + r) / 2
                listOf(
                    DrawOp.Stroke(listOf(x - gap / 2, top, x - gap / 2, bottom), stem * 0.85f),
                    DrawOp.Stroke(listOf(x + gap / 2, top, x + gap / 2, bottom), stem * 0.85f),
                )
            }
            else -> emptyList()
        }
    }

    private fun rect(x0: Float, y0: Float, x1: Float, y1: Float) =
        DrawOp.Stroke(listOf(x0, y0, x1, y0, x1, y1, x0, y1), 0f, closed = true, filled = true)

    /**
     * Kök işareti: kısa kanca, kalın iniş, ince çıkış. [yTop] üst çizginin ortası, [yBot] işaretin dibi.
     * Dönüş: çizimler ve işaretin genişliği (üst çizgi bu x'ten başlar).
     */
    fun radical(x0: Float, yTop: Float, yBot: Float, em: Float, rule: Float): Pair<List<DrawOp>, Float> {
        val h = yBot - yTop
        val hScale = (h / em).coerceIn(0.8f, 1.6f)
        val sw = (0.5f + 0.06f * ((h / em) - 1f).coerceIn(0f, 4f)) * em
        val hook = min(0.5f * h, 0.42f * em * hScale)
        val p0x = x0 + 0.02f * em
        val p0y = yBot - hook + 0.07f * em
        val p1x = x0 + 0.13f * em
        val p1y = yBot - hook
        val p2x = x0 + 0.29f * em + 0.02f * (h / em).coerceAtMost(3f) * em
        val p2y = yBot
        val p3x = x0 + sw
        val p3y = yTop
        val thick = rule * 1.9f
        val ops = ArrayList<DrawOp>()
        ops.add(DrawOp.Stroke(listOf(p0x, p0y, p1x, p1y), rule * 0.9f))
        // kalın iniş: p1 → p2 dolu dörtgen
        val dx = p2x - p1x
        val dy = p2y - p1y
        val len = sqrt(dx * dx + dy * dy)
        val nx = -dy / len * thick / 2
        val ny = dx / len * thick / 2
        ops.add(
            DrawOp.Stroke(
                listOf(p1x - nx, p1y - ny, p1x + nx, p1y + ny, p2x + nx * 0.3f, p2y + ny * 0.3f, p2x - nx * 0.3f, p2y - ny * 0.3f),
                0f, closed = true, filled = true,
            ),
        )
        ops.add(DrawOp.Stroke(listOf(p2x, p2y, p3x, p3y), rule))
        return ops to sw
    }
}
