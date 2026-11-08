package com.karapp.drawguidance.ui.drawing

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * v20 çizim girdisi süzgeci. Parmak/kalem noktaları fırçaya verilmeden önce iki ayardan geçer:
 *
 * - **Sabitleme** ([stabilize], 0..1): el titremesinden gelen ard arda zikzaklar düzelir. Önce "ip çekme": çizim ucu
 *   parmağı ekranda [MAX_STRING_PX] × güç uzunluğunda bir iple izler; ipin içindeki küçük gidip gelmeler hiç çizilmez.
 *   Ardından son birkaç noktanın ortalaması alınır (köşeler yumuşar). Çizim bitince uç parmağın bıraktığı yere tamamlanır.
 * - **Çizim hızı** ([speed], 0.1..1): mürekkebin kaleme yetişme hızı. 1'de anında; düştükçe uç parmağı zaman sabitiyle
 *   (en çok [MAX_LAG_MS]) gecikerek izler, parmak dursa da yetişene kadar ilerler ([tick]); çizgi akıcı ve yumuşak olur.
 *
 * Mesafeler tuval birimindedir; [unit] ekrandaki 1 pikselin tuval birimi karşılığıdır (yakınlaştırmadan bağımsız his).
 * Çıkan noktalar hem canlı çizgiye hem kaydedilen çizime gider (görülen ile kaydedilen aynıdır).
 */
class InkFilter(stabilize: Float, speed: Float, private val unit: Float) {

    /** Süzgeçten geçen nokta (kalem basıncı, eğimi ve yönüyle). */
    data class Point(val x: Float, val y: Float, val pressure: Float = 1f, val tilt: Float = 0f, val orient: Float = 0f)

    private val radius = stabilize.coerceIn(0f, 1f) * MAX_STRING_PX * unit
    private val window = 1 + (stabilize.coerceIn(0f, 1f) * MAX_WINDOW).roundToInt()
    private val tau = if (speed >= 0.999f) 0f else (1f - speed.coerceIn(0.1f, 1f)) * MAX_LAG_MS

    /** Süzgeç bir şey yapıyor mu (yoksa noktalar aynen geçer). */
    val active: Boolean get() = radius > 0f || window > 1 || tau > 0f

    /** Parmak dururken de mürekkep ilerlesin diye düzenli [tick] gerekir. */
    val needsTicks: Boolean get() = tau > 0f

    private var started = false
    // İp çekme ucu
    private var ax = 0f; private var ay = 0f
    // Kayan ortalama
    private val wx = FloatArray(window); private val wy = FloatArray(window)
    private var wCount = 0; private var wNext = 0
    // Takip eden uç (çizilen) ve hedefi
    private var fx = 0f; private var fy = 0f
    private var tx = 0f; private var ty = 0f
    private var lastTime = 0L
    private var dyn = Point(0f, 0f)
    private var lastOutX = Float.NaN; private var lastOutY = Float.NaN

    fun start(p: Point, time: Long): List<Point> {
        started = true
        ax = p.x; ay = p.y
        wCount = 0; wNext = 0
        pushWindow(p.x, p.y)
        fx = p.x; fy = p.y; tx = p.x; ty = p.y
        lastTime = time
        dyn = p
        return listOf(emit(fx, fy))
    }

    fun move(p: Point, time: Long): List<Point> {
        if (!started) return start(p, time)
        dyn = p
        // 1) İp çekme: uç, parmak ipin boyundan uzaklaşınca ona doğru çekilir
        val d = hypot(p.x - ax, p.y - ay)
        if (radius > 0f) {
            if (d > radius) {
                val k = (d - radius) / d
                ax += (p.x - ax) * k; ay += (p.y - ay) * k
            } else if (tau <= 0f) {
                return emptyList()
            }
        } else {
            ax = p.x; ay = p.y
        }
        // 2) Kayan ortalama
        pushWindow(ax, ay)
        tx = avgX(); ty = avgY()
        // 3) Takip
        return follow(time)
    }

    /** Parmak hareketsizken zamanın ilerlemesi: mürekkep hedefe doğru yol alır. */
    fun tick(time: Long): List<Point> = if (!started || tau <= 0f) emptyList() else follow(time)

    /** Çizim bitti: uç parmağın bıraktığı noktaya yumuşakça tamamlanır. */
    fun finish(end: Point?, time: Long): List<Point> {
        if (!started) return emptyList()
        val out = ArrayList<Point>()
        end?.let { dyn = it; ax = it.x; ay = it.y }
        // Ortalama penceresini son noktayla doldur: köşe yuvarlanarak sona varılır
        repeat(window - 1) {
            pushWindow(ax, ay)
            tx = avgX(); ty = avgY()
            out += catchUp()
        }
        tx = ax; ty = ay
        out += catchUp()
        lastTime = max(lastTime, time)
        return out
    }

    private fun follow(time: Long): List<Point> {
        val dt = (time - lastTime).coerceAtLeast(0L)
        lastTime = max(lastTime, time)
        if (tau <= 0f) {
            fx = tx; fy = ty
        } else {
            val a = 1f - exp(-dt / tau)
            fx += (tx - fx) * a; fy += (ty - fy) * a
        }
        return if (moved(fx, fy)) listOf(emit(fx, fy)) else emptyList()
    }

    /** Takip ucunu hedefe ara noktalarla taşır (bitişte). */
    private fun catchUp(): List<Point> {
        val dist = hypot(tx - fx, ty - fy)
        if (dist < MIN_STEP_PX * unit) return emptyList()
        val n = (dist / (4f * unit)).toInt().coerceIn(1, 24)
        val sx = fx; val sy = fy
        val out = ArrayList<Point>(n)
        for (i in 1..n) {
            val u = i / n.toFloat()
            fx = sx + (tx - sx) * u; fy = sy + (ty - sy) * u
            if (moved(fx, fy)) out += emit(fx, fy)
        }
        return out
    }

    private fun moved(x: Float, y: Float): Boolean = lastOutX.isNaN() || hypot(x - lastOutX, y - lastOutY) >= MIN_STEP_PX * unit

    private fun emit(x: Float, y: Float): Point {
        lastOutX = x; lastOutY = y
        return Point(x, y, dyn.pressure, dyn.tilt, dyn.orient)
    }

    private fun pushWindow(x: Float, y: Float) {
        wx[wNext] = x; wy[wNext] = y
        wNext = (wNext + 1) % window
        if (wCount < window) wCount++
    }

    private fun avgX(): Float { var s = 0f; for (i in 0 until wCount) s += wx[i]; return s / wCount }
    private fun avgY(): Float { var s = 0f; for (i in 0 until wCount) s += wy[i]; return s / wCount }

    companion object {
        /** En güçlü sabitlemede ipin ekrandaki uzunluğu (piksel). */
        const val MAX_STRING_PX = 26f
        /** En güçlü sabitlemede ortalaması alınan nokta sayısı (+1). */
        const val MAX_WINDOW = 8
        /** En yavaş çizim hızında mürekkebin gecikme zaman sabiti (ms). */
        const val MAX_LAG_MS = 240f
        /** Çıkan noktalar arası en küçük ekran mesafesi (piksel). */
        const val MIN_STEP_PX = 0.4f
    }
}
