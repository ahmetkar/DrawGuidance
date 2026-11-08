package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.comic.CharExpression
import com.karapp.drawguidance.content.comic.CharKind
import com.karapp.drawguidance.content.comic.CharPose
import com.karapp.drawguidance.content.comic.ComicCharacters
import com.karapp.drawguidance.content.comic.ComicEffects
import com.karapp.drawguidance.content.comic.EffectGroup
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VPath
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * v37 kütüphane çizimlerinin (nesne, karakter, efekt) çizgi hali: elle çizen birinin çizeceği dış çizgiler.
 *
 * - Dış çizgisi olan parçalar alınır (gölge, ışık, yer gölgesi gibi yalnız dolgulu ve saydam parçalar alınmaz).
 * - Efektlerde dış çizgisi olmayan renkli parçalar da alınır: ince uzun olanlar (hız çizgisi, yağmur) orta çizgisine,
 *   ötekiler dış hattına çevrilir.
 * - Aynı biçimin tekrarları (kalın + ince çizgi) birleşir; çok küçük ayrıntılar düşer, en önemli [max] parça kalır.
 * - Oran korunur (birim kutuda ortalanır); noktalar seyreltilir.
 */
internal object LibraryLineArt {

    fun of(shape: LibShape, max: Int, fills: Boolean, minExtent: Float = 0.07f): List<List<P>> =
        of(shape.paths(256f), shape.aspect, max, fills, minExtent)

    fun of(paths: List<VPath>, aspect: Float, max: Int, fills: Boolean, minExtent: Float = 0.07f): List<List<P>> {
        fun fit(p: P): P = if (aspect >= 1f) P(p.x, p.y / aspect + (1f - 1f / aspect) / 2f) else P(p.x * aspect + (1f - aspect) / 2f, p.y)
        val cand = ArrayList<List<P>>()
        val weak = ArrayList<List<P>>()
        for (p in paths) {
            if (p.label != null || p.points.size < 2) continue
            val pts = p.points.map(::fit)
            val poly = if (p.closed) pts + pts.first() else pts
            val white = p.stroke.equals("#FFFFFF", true) || p.fill.equals("#FFFFFF", true) && p.width == 0f
            if (p.width > 0f && p.alpha >= 0.45f && !p.stroke.equals("#FFFFFF", true)) { cand += poly; continue }
            if (!fills || white || p.alpha < 0.3f) continue
            val shade = p.fill?.startsWith("@shade") == true
            val line = if (p.closed && thin(pts)) centerline(pts) else poly
            if (shade) weak += line else cand += line
        }
        if (cand.isEmpty()) cand += weak
        val uniq = ArrayList<List<P>>()
        for (c in cand) if (uniq.none { same(it, c) }) uniq += c
        val sized = uniq.map { it to extent(it) }
        var keep = sized.filter { it.second >= minExtent }
        if (keep.isEmpty()) keep = sized.sortedByDescending { it.second }.take(3)
        // En önemli parçalar (büyüklük ve uzunluk), çizim sırası korunur
        val chosen = keep.sortedByDescending { it.second + 0.25f * min(1f, length(it.first)) }.take(max).toHashSet()
        return keep.filter { it in chosen }.map { simplify(it.first) }.filter { it.size >= 2 }
    }

    private fun extent(p: List<P>): Float = max(p.maxOf { it.x } - p.minOf { it.x }, p.maxOf { it.y } - p.minOf { it.y })

    private fun length(p: List<P>): Float { var l = 0f; for (i in 1 until p.size) l += hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y); return l }

    /** İki çizgi aynı biçimde mi (aynı kutu ve noktaları birbirine çok yakın). */
    private fun same(a: List<P>, b: List<P>): Boolean {
        if (abs(a.minOf { it.x } - b.minOf { it.x }) > 0.01f || abs(a.maxOf { it.x } - b.maxOf { it.x }) > 0.01f) return false
        if (abs(a.minOf { it.y } - b.minOf { it.y }) > 0.01f || abs(a.maxOf { it.y } - b.maxOf { it.y }) > 0.01f) return false
        val ra = resample(a, 24); val rb = resample(b, 24)
        return ra.all { p -> rb.minOf { hypot(it.x - p.x, it.y - p.y) } < 0.012f }
    }

    /** Kapalı biçim ince uzun mu (ana eksen boyunun en az 4 katı). */
    private fun thin(pts: List<P>): Boolean {
        val r = resample(pts + pts.first(), 48)
        val mx = r.map { it.x }.average().toFloat(); val my = r.map { it.y }.average().toFloat()
        var sxx = 0f; var syy = 0f; var sxy = 0f
        for (p in r) { sxx += (p.x - mx) * (p.x - mx); syy += (p.y - my) * (p.y - my); sxy += (p.x - mx) * (p.y - my) }
        val tr = sxx + syy; val det = sxx * syy - sxy * sxy
        val l1 = tr / 2 + sqrt(max(0f, tr * tr / 4 - det)); val l2 = tr / 2 - sqrt(max(0f, tr * tr / 4 - det))
        return l2 <= 1e-9f || sqrt(l1 / l2) > 4f
    }

    /** İnce kapalı biçimin orta çizgisi: ana eksen boyunca dilimlerin ortalaması. */
    private fun centerline(pts: List<P>): List<P> {
        val r = resample(pts + pts.first(), 160)
        val mx = r.map { it.x }.average().toFloat(); val my = r.map { it.y }.average().toFloat()
        var sxx = 0f; var syy = 0f; var sxy = 0f
        for (p in r) { sxx += (p.x - mx) * (p.x - mx); syy += (p.y - my) * (p.y - my); sxy += (p.x - mx) * (p.y - my) }
        val ang = 0.5f * kotlin.math.atan2(2 * sxy, sxx - syy)
        val ux = kotlin.math.cos(ang); val uy = kotlin.math.sin(ang)
        val t = r.map { (it.x - mx) * ux + (it.y - my) * uy }
        val lo = t.min(); val hi = t.max()
        val bins = 12
        val out = ArrayList<P>()
        for (b in 0 until bins) {
            val a = lo + (hi - lo) * b / bins; val c = lo + (hi - lo) * (b + 1) / bins
            val inBin = r.indices.filter { t[it] in a..c }
            if (inBin.isEmpty()) continue
            out += P(inBin.map { r[it].x }.average().toFloat(), inBin.map { r[it].y }.average().toFloat())
        }
        return out
    }

    fun resample(p: List<P>, n: Int): List<P> {
        val total = length(p)
        if (total <= 0f || p.size < 2) return List(n) { p.first() }
        val step = total / (n - 1)
        val out = ArrayList<P>(n)
        out += p.first()
        var acc = 0f
        var i = 1
        var prev = p.first()
        while (i < p.size && out.size < n) {
            val cur = p[i]
            val d = hypot(cur.x - prev.x, cur.y - prev.y)
            if (acc + d >= step && d > 0f) {
                val t = (step - acc) / d
                val q = P(prev.x + (cur.x - prev.x) * t, prev.y + (cur.y - prev.y) * t)
                out += q; prev = q; acc = 0f
            } else { acc += d; prev = cur; i++ }
        }
        while (out.size < n) out += p.last()
        return out
    }

    /** Seyreltme: sık örnekleme + Ramer–Douglas–Peucker. */
    private fun simplify(p: List<P>): List<P> {
        val n = (length(p) / 0.008f).toInt().coerceIn(2, 400)
        val r = resample(p, n)
        val keep = BooleanArray(r.size); keep[0] = true; keep[r.size - 1] = true
        fun rdp(a: Int, b: Int) {
            if (b <= a + 1) return
            var best = -1f; var at = -1
            val ax = r[a].x; val ay = r[a].y; val bx = r[b].x; val by = r[b].y
            val len = hypot(bx - ax, by - ay)
            for (i in a + 1 until b) {
                val d = if (len < 1e-6f) hypot(r[i].x - ax, r[i].y - ay) else abs((bx - ax) * (ay - r[i].y) - (ax - r[i].x) * (by - ay)) / len
                if (d > best) { best = d; at = i }
            }
            if (best > 0.003f) { keep[at] = true; rdp(a, at); rdp(at, b) }
        }
        rdp(0, r.size - 1)
        return r.filterIndexed { i, _ -> keep[i] }
    }

    // ---------------------------------------------------------------- kütüphaneden şablonlar

    /** Nesne kategorisinin şablon kategorisi (moda kapsam bunlara göre; bkz. [ModeCatalog]). */
    private val objectCategory = mapOf(
        "obj_daily" to "Nesne", "obj_home" to "Mobilya", "obj_food" to "Yiyecek ve mutfak", "obj_transport" to "Taşıt",
        "obj_nature" to "Gökyüzü ve doğa", "obj_wear" to "Giyim", "obj_tools" to "Alet", "obj_sports" to "Spor ve oyun",
        "obj_fantasy" to "Hayal", "obj_detective" to "Nesne", "obj_scifi" to "Hayal", "obj_city" to "Yapı", "obj_music" to "Müzik",
    )

    const val OBJECT_STROKES = 10
    const val CHARACTER_STROKES = 12
    const val EFFECT_STROKES = 10

    /** v43 nesne kütüphanesi kaldırıldı: eğitim verisinde nesne çizimi yok. */
    fun objects(): List<LibraryTemplates.Entry> = emptyList()

    /** Karakterin bir pozdaki çizgi hali (eğitimde bütün pozlar kullanılır). */
    fun character(kind: CharKind, pose: CharPose, expression: CharExpression = CharExpression.CALM): List<List<P>> {
        val d = ComicCharacters.def(kind, expression, pose)
        return of(d.shape(), CHARACTER_STROKES, fills = false, minExtent = 0.08f)
    }

    /** Karakterler: tür başına bir şablon (ayakta, sakin). */
    fun characters(): List<LibraryTemplates.Entry> = CharKind.entries.map { k ->
        LibraryTemplates.Entry(
            ShapeTemplate(LibraryTemplates.CHARACTER_PREFIX + k.id, k.title, "Çizgi roman", "Karakter", character(k, CharPose.STAND),
                "Çizgi roman karakteri (${k.title.lowercase()}): baş, gövde, kollar, bacaklar ve belirgin aksesuarları"),
            ComicCharacters.def(k, CharExpression.CALM, CharPose.STAND).id,
        )
    }

    /**
     * Elle çizilmeyen doku/ışık efektleri: çizgi halleri yalnız bir daire ya da birkaç nokta kalıyor (her daire çizimini
     * çalardı); şablon olmazlar.
     */
    val EXCLUDED_EFFECTS = setOf("dots", "glow", "portal_glow")

    /** Efektler: ses efektleri (yazı) ve [EXCLUDED_EFFECTS] dışındakiler. */
    fun effects(): List<LibraryTemplates.Entry> = ComicEffects.all.filter { it.group != EffectGroup.SOUND && it.key !in EXCLUDED_EFFECTS }.map { e ->
        LibraryTemplates.Entry(
            ShapeTemplate(LibraryTemplates.EFFECT_PREFIX + e.key, e.title, "Çizgi roman", "Efekt: ${e.group.title}",
                of(e.shape(), EFFECT_STROKES, fills = true), "Çizgi roman efekti (${e.title.lowercase()})"),
            e.id,
        )
    }

    fun all(): List<LibraryTemplates.Entry> = objects() + characters() + effects()
}
