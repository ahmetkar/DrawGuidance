package com.karapp.drawguidance.ui.screens.canvasedit.challenge

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * v38 çizim görevinin puanı (0–100): çizimin hedefe ne kadar yaklaştığı.
 *
 * - **Çizgi puanı** ([outline]): çizim ve hedef çizgileri eşit aralıkla örneklenir (hedef kutusunun köşegeninin
 *   1/160'ı). Her noktanın öteki kümeye uzaklığı köşegenin %4,5'ine kadar tam, %12'sinde sıfır puan alır (arası
 *   doğrusal). İsabet (çizimin hedefe yakın kısmı) ve kapsama (hedefin çizilen kısmı) ortalamalarının harmonik ortası
 *   (F1) puandır: yarım bırakılan çizim de, kâğıdı karalamak da düşük puan alır. Çizim başka yerde ya da başka boyda
 *   yapıldıysa kutusu hedefin kutusuna uydurulup (ortalar çakışır, tek ölçek) yeniden ölçülür ve bunun %95'i ile yerinde
 *   ölçülenin büyüğü alınır (tek düz çizgi uydurulmaz). Çizimin toplam boyu hedefinkinin 2 katını aşarsa (kâğıdı
 *   karalamak) puan aşan oranda düşer.
 * - **Boya puanı** ([paint]): her boyanacak yerde görünen renk beklenenle karşılaştırılır: boyanmamışsa 0, boyanmışsa
 *   0,3 (emek) + 0,7 × renk benzerliği (RGB uzaklığı, en büyük uzaklığın %35'inde sıfır).
 * - Yerleştir ve boya görevinde puan = %40 çizgi + %60 boya.
 *
 * Geçme sınırı [PASS]; yıldızlar 60, 75 ve 90 puanda.
 */
object ChallengeScore {
    /** Seviyeyi geçme sınırı. */
    const val PASS = 60

    /** 1, 2 ve 3 yıldızın puanları. */
    val STARS = intArrayOf(60, 75, 90)

    fun stars(score: Int): Int = STARS.count { score >= it }

    fun passed(score: Int): Boolean = score >= PASS

    /** Örnek sayısı: köşegen / [SAMPLES] aralıkla. */
    private const val SAMPLES = 160f

    /** Tam puan uzaklığı ve puanın sıfırlandığı uzaklık (hedef köşegenine oran). */
    const val NEAR = 0.045f
    const val FAR = 0.12f

    /** Kutusu uydurulan çizimin puan çarpanı (yerinde çizmek biraz daha iyi). */
    const val ALIGNED = 0.95f

    /** Boyanmış yerin emek payı ve renk benzerliğinin sıfırlandığı uzaklık. */
    const val PAINTED = 0.3f
    const val COLOR_FAR = 0.35f

    /** Çizimin toplam boyu hedefinkinin en çok bu katı olabilir (üstünden iki kez geçmek serbest; fazlası karalama). */
    const val INK_MAX = 2f

    /** Yerleştir ve boya görevinde çizginin payı. */
    const val OUTLINE_WEIGHT = 0.4f

    /** Çizimin ([drawn]) hedef çizgilerine ([target]) yakınlığı, 0–100. */
    fun outline(drawn: List<List<Offset>>, target: List<List<Offset>>): Int {
        val tBox = bounds(target) ?: return 0
        val diag = hypot(tBox.width, tBox.height).coerceAtLeast(1f)
        val step = diag / SAMPLES
        val t = resample(target, step)
        val d = resample(drawn, step)
        if (t.isEmpty() || d.size < 2) return 0
        val near = diag * NEAR
        val far = diag * FAR
        val lenT = length(target).coerceAtLeast(1f)
        val lenD = length(drawn)
        val inPlace = f1(d, t, near, far) * excess(lenD / lenT)
        val dBox = bounds(drawn)
        val aligned = dBox?.let { align(d, it, tBox) }?.let { (pts, s) -> f1(pts, t, near, far) * excess(lenD * s / lenT) * ALIGNED } ?: 0f
        return percent(max(inPlace, aligned))
    }

    /** Boyanacak yerlerin puanı ([checks]: beklenen renk → görünen renk; null boyanmamış). */
    fun paint(checks: List<Pair<String, String?>>): Int =
        if (checks.isEmpty()) 0 else percent(checks.map { (want, got) -> paintCredit(want, got) }.average().toFloat())

    /** Bir yerin boya puanı (0..1). */
    fun paintCredit(want: String, got: String?): Float {
        if (got == null || isPaper(got)) return 0f
        val a = rgb(got) ?: return 0f
        val b = rgb(want) ?: return 0f
        val dr = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
        val dg = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        val dist = sqrt((dr * dr + dg * dg + db * db).toFloat()) / MAX_RGB
        val similar = (1f - dist / COLOR_FAR).coerceIn(0f, 1f)
        return PAINTED + (1f - PAINTED) * similar
    }

    /** Yerleştir ve boya: çizgi ve boya puanlarının ağırlıklı ortası. */
    fun combine(outline: Int, paint: Int): Int = (OUTLINE_WEIGHT * outline + (1f - OUTLINE_WEIGHT) * paint).roundToInt()

    /** Boyanmamış (kâğıt beyazı) dolgu. */
    fun isPaper(hex: String): Boolean = rgb(hex)?.let { it == 0xFFFFFF } ?: true

    private const val MAX_RGB = 441.673f

    /** Kısa kenarı uzun kenarının bu oranından küçük kutu "ince" (düz çizgi gibi) sayılır. */
    private const val THIN = 0.05f

    private fun percent(f: Float): Int = (f * 100f).roundToInt().coerceIn(0, 100)

    /** "#RRGGBB" ya da "#AARRGGBB" → 0xRRGGBB (okunamazsa null). */
    fun rgb(hex: String): Int? {
        val h = hex.trim().removePrefix("#")
        val v = h.toLongOrNull(16) ?: return null
        return when (h.length) {
            6 -> v.toInt()
            8 -> (v and 0xFFFFFF).toInt()
            else -> null
        }
    }

    private fun f1(d: List<Offset>, t: List<Offset>, near: Float, far: Float): Float {
        val tGrid = PointGrid(t, far)
        val dGrid = PointGrid(d, far)
        val precision = d.map { credit(tGrid.nearest(it), near, far) }.average().toFloat()
        val recall = t.map { credit(dGrid.nearest(it), near, far) }.average().toFloat()
        return if (precision + recall <= 0f) 0f else 2f * precision * recall / (precision + recall)
    }

    private fun credit(d: Float, near: Float, far: Float): Float = when {
        d <= near -> 1f
        d >= far -> 0f
        else -> (far - d) / (far - near)
    }

    /** Çizgileri [step] aralıkla örnekler (tek noktalı çizgi tek örnek). */
    fun resample(lines: List<List<Offset>>, step: Float): List<Offset> {
        val out = ArrayList<Offset>()
        for (line in lines) {
            if (line.isEmpty()) continue
            out += line[0]
            var carry = 0f
            for (i in 1 until line.size) {
                val a = line[i - 1]
                val b = line[i]
                val len = hypot(b.x - a.x, b.y - a.y)
                if (len <= 0f) continue
                var at = step - carry
                while (at <= len) {
                    val k = at / len
                    out += Offset(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k)
                    at += step
                }
                carry = (carry + len) % step
            }
            if (line.size > 1) out += line.last()
        }
        return out
    }

    fun bounds(lines: List<List<Offset>>): Rect? {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var any = false
        for (line in lines) for (p in line) {
            any = true
            minX = min(minX, p.x); minY = min(minY, p.y); maxX = max(maxX, p.x); maxY = max(maxY, p.y)
        }
        return if (any) Rect(minX, minY, maxX, maxY) else null
    }

    /**
     * Çizimin kutusu hedefin kutusuna uydurulur: ortalar çakışır, ölçek iki kenarın oranlarının geometrik ortası (ikisi
     * de çizgi gibi inceyse uzun kenarların oranı), 0,2–5 arası. Yalnız biri inceyse uydurulmaz (tek düz çizgi hedefin
     * ortasına taşınıp puan toplamasın).
     */
    private fun align(points: List<Offset>, from: Rect, to: Rect): Pair<List<Offset>, Float>? {
        val fw = from.width; val fh = from.height
        val longest = max(fw, fh)
        if (longest < 1e-3f) return null
        val thin = min(fw, fh) < longest * THIN
        if (thin != min(to.width, to.height) < max(to.width, to.height) * THIN) return null
        val s = (if (thin) max(to.width, to.height) / longest else sqrt((to.width / fw) * (to.height / fh))).coerceIn(0.2f, 5f)
        val fc = from.center
        val tc = to.center
        return points.map { p -> Offset(tc.x + (p.x - fc.x) * s, tc.y + (p.y - fc.y) * s) } to s
    }

    /** Çizginin boyu hedefinkinin [INK_MAX] katını aşarsa (karalama) puan aşan oranda düşer. */
    private fun excess(ratio: Float): Float = if (ratio <= INK_MAX) 1f else INK_MAX / ratio

    /** Çizgilerin toplam boyu. */
    fun length(lines: List<List<Offset>>): Float {
        var sum = 0f
        for (line in lines) for (i in 1 until line.size) sum += hypot(line[i].x - line[i - 1].x, line[i].y - line[i - 1].y)
        return sum
    }

    /** Noktanın çizgiye (kapalıysa kapanış kenarı da) uzaklığı. */
    fun distanceToPolyline(p: Offset, line: List<Offset>, closed: Boolean): Float {
        if (line.isEmpty()) return Float.MAX_VALUE
        if (line.size == 1) return hypot(p.x - line[0].x, p.y - line[0].y)
        var best = Float.MAX_VALUE
        val n = if (closed) line.size else line.size - 1
        for (i in 0 until n) best = min(best, segment(p, line[i], line[(i + 1) % line.size]))
        return best
    }

    fun segment(p: Offset, a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val l = dx * dx + dy * dy
        val t = if (l <= 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / l).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /** En yakın nokta araması için ızgara ([cell] boyunda gözler; [cell]'den uzaktakiler "çok uzak" sayılır). */
    private class PointGrid(points: List<Offset>, private val cell: Float) {
        private val map = HashMap<Long, MutableList<Offset>>()

        init {
            for (p in points) map.getOrPut(key(cx(p.x), cx(p.y))) { ArrayList(4) }.add(p)
        }

        private fun cx(v: Float) = floor(v / cell).toInt()
        private fun key(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

        fun nearest(p: Offset): Float {
            val x = cx(p.x)
            val y = cx(p.y)
            var best = Float.MAX_VALUE
            for (i in -1..1) for (j in -1..1) {
                val list = map[key(x + i, y + j)] ?: continue
                for (q in list) {
                    val d = hypot(p.x - q.x, p.y - q.y)
                    if (d < best) best = d
                }
            }
            return best
        }
    }
}
