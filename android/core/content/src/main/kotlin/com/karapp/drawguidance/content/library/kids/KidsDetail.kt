package com.karapp.drawguidance.content.library.kids

import com.karapp.drawguidance.content.vector.P
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * v47 çocuk çizimleri 6–9 yaşa göre: her çizimin en büyük parçalarının içine boyanacak bir desen eklenir (benek, şerit,
 * dalga, yıldız, baklava). Böylece boyanacak alan sayısı artar, çizim boyama kitabının büyük çocuklara yönelik
 * sayfalarına benzer. Desen parçaları ayrı alanlardır (Alan boya ve boya fırçası her birini ayrı boyar).
 *
 * Kurallar (gizli çizgi kalmaması için, bkz. [KidPen]): desen parçası bağlı olduğu parçanın içine kırpılır; sonradan
 * çizilen parçalar ve koyu ayrıntılar desenden kesilir; içine başka bir parça tamamen düşen ya da çok ince kalan desen
 * parçası atılır. Göz içeren (yüz) parçalara desen konmaz. Desenler "kid_detail" etiketlidir: çizim görevi izlenecek
 * ve boyanacak parçaları sayarken onları atlar (görevlerin parça sıraları değişmez).
 */
internal object KidDetail {
    /** Desen parçalarının etiketi. */
    const val TAG = "kid_detail"

    enum class Pattern { DOTS, STARS, DIAMONDS, FLOWERS }

    /** Bir çizimde desen konan en çok parça. */
    private const val MAX_HOSTS = 2

    /** Bir parçadaki en çok desen öğesi. */
    private const val MAX_ITEMS = 16

    /** Çizimin türüne göre desen (etiketlerinden); türü bilinmeyen çizimlerde adına göre sabit bir seçim. */
    fun pattern(name: String, tags: List<String>): Pattern {
        fun has(vararg t: String) = t.any { it in tags }
        return when {
            // Hayvanlarda benek, denizde kabarcık, meyve ve tatlıda benek
            has("hayvan", "deniz", "sea", "meyve", "tatlı", "fruit", "dessert") -> Pattern.DOTS
            has("uzay", "space", "taşıt", "vehicle") -> Pattern.STARS
            has("oyuncak", "toy", "parti") -> Pattern.DIAMONDS
            has("doğa", "bahçe", "çiçek", "garden") -> Pattern.FLOWERS
            else -> Pattern.entries[abs(name.hashCode()) % Pattern.entries.size]
        }
    }

    /**
     * [polys] çizim işlemlerinin biçimleri (sırayla), [isPart] boyanacak parça mı, [isInk] koyu ayrıntı mı. Dönen harita:
     * işlem sırası → o parçanın hemen üstüne çizilecek desen parçaları.
     */
    fun decorate(
        pen: KidPen, polys: List<Poly>, isPart: List<Boolean>, isInk: List<Boolean>, isLine: List<Boolean>,
        pattern: Pattern, w: Float, h: Float,
    ): Map<Int, List<Poly>> {
        val total = w * h
        val u = sqrt(total) / 100f
        val areas = polys.map { abs(PolyClip.area(it)) }
        val hosts = polys.indices
            .filter { isPart[it] && areas[it] >= total * 0.07f }
            // Yüz (gözü olan parça) desensiz kalır
            .filter { i -> (i + 1 until polys.size).none { j -> isInk[j] && inside(polys[i], centroid(polys[j])) } }
            .sortedByDescending { areas[it] }
            .take(MAX_HOSTS)
        val out = HashMap<Int, List<Poly>>()
        for (i in hosts) {
            val host = polys[i]
            val blockers = (i + 1 until polys.size).filter { isPart[it] || isInk[it] }.map { polys[it] }
            // Sonra çizilen ayrıntı çizgilerine (tüy, bıyık, damar) değmez
            val lines = (i + 1 until polys.size).filter { isLine[it] }.map { polys[it] }
            val items = when (pattern) {
                Pattern.DOTS -> whole(pen, host, blockers, lines, u, spacing = 12.5f * u) { x, y -> pen.circle(x, y, 3.6f * u) }
                Pattern.STARS -> whole(pen, host, blockers, lines, u, spacing = 15f * u) { x, y -> pen.star(x, y, 5.4f * u, 2.5f * u) }
                Pattern.DIAMONDS -> whole(pen, host, blockers, lines, u, spacing = 14f * u) { x, y ->
                    listOf(P(x, y - 4.8f * u), P(x + 4.8f * u, y), P(x, y + 4.8f * u), P(x - 4.8f * u, y))
                }
                Pattern.FLOWERS -> whole(pen, host, blockers, lines, u, spacing = 15f * u) { x, y -> pen.star(x, y, 5.2f * u, 2.8f * u, n = 6, round = 1.6f * u) }
            }
            if (items.isNotEmpty()) out[i] = items
        }
        return out
    }

    /** Bütün kalan öğeler (benek, yıldız, baklava): parçanın içinde, kenarından ve üstteki parçalardan uzak olanlar. */
    private fun whole(pen: KidPen, host: Poly, blockers: List<Poly>, lines: List<Poly>, u: Float, spacing: Float, item: (Float, Float) -> Poly): List<Poly> {
        val b = bounds(host)
        val c = centroid(host)
        val margin = 2f * u
        val found = ArrayList<Poly>()
        var row = 0
        var y = b[1] + spacing * 0.6f
        while (y < b[3]) {
            var x = b[0] + spacing * 0.6f + if (row % 2 == 1) spacing / 2f else 0f
            while (x < b[2]) {
                val el = item(x, y)
                val eb = bounds(el)
                // Kenar payı: öğenin kutusu payla büyütülünce de parçanın içinde
                val probe = listOf(P(eb[0] - margin, eb[1] - margin), P(eb[2] + margin, eb[1] - margin), P(eb[2] + margin, eb[3] + margin), P(eb[0] - margin, eb[3] + margin))
                if (probe.all { inside(host, it) } && el.all { inside(host, it) } &&
                    blockers.none { bl -> overlaps(pen, el, bl, margin) } &&
                    lines.none { ln -> boxesTouch(el, ln, margin) && (ln.any { q -> inside(el, q) || edgeDist(q, el) < margin } || el.any { q -> lineDist(q, ln) < margin }) }
                ) found += el
                x += spacing
            }
            y += spacing * 0.87f
            row++
        }
        // Çoksa parçanın ortasına en yakınlar (desen dağınık durmasın)
        return found.sortedBy { hypot(centroid(it).x - c.x, centroid(it).y - c.y) }.take(MAX_ITEMS)
    }

    private fun overlaps(@Suppress("UNUSED_PARAMETER") pen: KidPen, a: Poly, b: Poly, margin: Float): Boolean {
        if (!boxesTouch(a, b, margin)) return false
        // İçerme (kesişme noktası olmasa da) ya da kenarlara çok yakınlık (iki çizgi üst üste binmesin)
        if (a.any { inside(b, it) } || b.any { inside(a, it) }) return true
        return a.any { p -> edgeDist(p, b) < margin } || b.any { p -> edgeDist(p, a) < margin }
    }

    private fun boxesTouch(a: Poly, b: Poly, margin: Float): Boolean {
        val ba = bounds(a); val bb = bounds(b)
        return !(ba[2] + margin < bb[0] || bb[2] + margin < ba[0] || ba[3] + margin < bb[1] || bb[3] + margin < ba[1])
    }

    /** Noktanın açık çizgiye uzaklığı. */
    private fun lineDist(p: P, line: Poly): Float {
        var d = Float.MAX_VALUE
        for (i in 0 until line.size - 1) d = minOf(d, segDist(p, line[i], line[i + 1]))
        return d
    }

    /** Noktanın biçimin kenarlarına uzaklığı. */
    private fun edgeDist(p: P, poly: Poly): Float {
        var d = Float.MAX_VALUE
        for (i in poly.indices) d = minOf(d, segDist(p, poly[i], poly[(i + 1) % poly.size]))
        return d
    }

    private fun segDist(p: P, a: P, b: P): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        val l2 = dx * dx + dy * dy
        val t = if (l2 <= 1e-9f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / l2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    private fun bounds(p: Poly): FloatArray =
        floatArrayOf(p.minOf { it.x }, p.minOf { it.y }, p.maxOf { it.x }, p.maxOf { it.y })

    private fun centroid(p: Poly): P = P(p.sumOf { it.x.toDouble() }.toFloat() / p.size, p.sumOf { it.y.toDouble() }.toFloat() / p.size)

    /** Çift-tek kuralıyla nokta biçimin içinde mi. */
    private fun inside(poly: Poly, q: P): Boolean {
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a.y > q.y) != (b.y > q.y) && q.x < (b.x - a.x) * (q.y - a.y) / (b.y - a.y) + a.x) c = !c
            j = i
        }
        return c
    }

}
