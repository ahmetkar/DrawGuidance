package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.content.shapes.MatchMode
import com.karapp.drawguidance.content.shapes.ShapeIndex
import com.karapp.drawguidance.content.shapes.TemplateFit
import com.karapp.drawguidance.content.shapes.SuggestRules
import com.karapp.drawguidance.content.shapes.TemplateMatch
import com.karapp.drawguidance.content.shapes.TemplateSuggest
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.Plane3D
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.Iso3D
import kotlin.math.max
import kotlin.math.min

/**
 * v21 şekil veritabanından öneri: [key] şablonu [strokeIds] izlerinin yerine konabilir. [strokes] yerine konacak izler
 * (tuval koordinatı; 3B çizimde düzleme yatırılmış). [solid] verilirse seçenek gölgeli cisim şeklidir (3B cisim
 * şablonları). [drawn] kullanıcının düzleme açılmış çizimi (kabul edilince örnek olarak öğrenilir).
 */
data class TemplateFix(
    val key: String,
    val name: String,
    val category: String,
    val confidence: Float,
    val mode: MatchMode,
    val strokeIds: Set<String>,
    val bounds: Rect,
    val strokes: List<List<StrokePoint>>,
    val solid: String? = null,
    val drawn: List<List<P>> = emptyList(),
    /** v22 döndürülebilir sahnede yeni izlerin yerleri ([strokes] ile aynı sırada; düz çizimde boş). */
    val spaces: List<com.karapp.drawguidance.data.model.Space3D?> = emptyList(),
    /** v27 hazır vektör şekil önerisi ([solid] şeklin kimliği; çizim tek vektör şekille değiştirilir). */
    val vector: Boolean = false,
)

/**
 * Şablon arama. v38: yalnız birbirine yakın 4+ elemanın oluşturduğu şekil aranır (tek çizgi ve 2–3 çizgi şekil
 * önerisi almaz; onlar çizgi önerisinindir), modun sinir ağıyla birlikte (bkz. [ShapeIndex]). 3B çizimde izler
 * çizildikleri düzlemden geri açılır (her iz kendi başlangıç noktası etrafında izdüşürüldüğünden açma kesindir), şablon
 * düz görünümde aranır ve sonuç aynı düzleme yatırılarak çizimin yerine oturtulur.
 */
internal object TemplateSuggester {

    /** Grup aramasında geriye bakılacak en çok iz. */
    private const val POOL = 40

    /**
     * [group] elemanlarının (en az [SuggestRules.SHAPE_MIN]) yerine konabilecek şablonlar, güvene göre; her öneri
     * grubun bütün çizgilerini değiştirir.
     */
    fun search(
        index: ShapeIndex,
        group: List<Stroke>,
        /** v29 modun kapsamı (modun şablonları ve sinir ağı). */
        scope: com.karapp.drawguidance.content.shapes.ShapeScope? = null,
        /** v34 en çok öneri (basit türlerde 8). */
        max: Int = 4,
    ): List<TemplateFix> {
        if (group.size < SuggestRules.SHAPE_MIN) return emptyList()
        val limit = if (max > 4) 6 else 3
        val plane = planeOf(group.last())
        val flats = group.map { flatten(it) }
        if (extent(flats) < 16f) return emptyList()
        val out = ArrayList<TemplateFix>()
        index.search(flats, MatchMode.GROUP, limit = limit, minConfidence = TemplateSuggest.MIN_GROUP, scope = scope)
            .forEach { m -> out += fixes(m, group, flats, plane) }
        // Güvene göre; aynı şablon bir kez (cisim seçeneği şablonuyla birlikte kalır)
        val seen = HashSet<String>()
        return out.sortedByDescending { it.confidence }.filter { seen.add(it.key + "|" + it.solid) }.take(max)
    }

    /**
     * Son çizilenle bir arada duran çizimler (aynı katman ve düzlemde, kutuları birbirine yakın; çizilme sırasıyla). v38
     * bu grup 4+ elemanlıysa şekil önerisi kendiliğinden gösterilir ([SuggestRules.SHAPE_PAD]).
     */
    fun group(all: List<Stroke>, last: Stroke, eligible: (Stroke) -> Boolean): List<Stroke> {
        val layer = last.layerId.orEmpty()
        val pool = all.asReversed().asSequence()
            .filter { it.id == last.id || (eligible(it) && it.layerId.orEmpty() == layer && samePlane(it, last)) }
            .take(POOL).toList().asReversed()
        val seed = pool.indexOfFirst { it.id == last.id }
        if (seed < 0) return emptyList()
        val boxes = pool.map { s -> box(flatten(s)) }
        return SuggestRules.near(boxes, seed, SuggestRules.SHAPE_PAD, SuggestRules.SHAPE_MIN_PAD, SuggestRules.SHAPE_MAX).map { pool[it] }
    }

    fun planeOf(s: Stroke): Plane3D = s.params?.depth3d?.plane ?: Plane3D.FREE

    /**
     * Birlikte gruplanabilir mi: v22 sahnedeki 3B izler kendi aralarında (düzlemleri farklı olsa da, ekranda görünen
     * resim aranır), düz çizimler kendi aralarında; v21 izlerinde aynı düzlem.
     */
    private fun samePlane(a: Stroke, b: Stroke): Boolean {
        val sa = a.space?.takeIf { !it.isSolid }; val sb = b.space?.takeIf { !it.isSolid }
        if (sa != null || sb != null) return sa != null && sb != null
        return planeOf(a) == planeOf(b)
    }

    /**
     * İzin şablonla karşılaştırılacak noktaları: v22 sahnede ekranda görünen hali (kullanıcı gördüğünü çizer; iz
     * parmağın altında düzleme çizilir); v21 izinde başlangıç noktası etrafında düzlemden geri açılır.
     */
    fun flatten(s: Stroke): List<P> {
        if (s.space != null) return s.points.map { P(it.x, it.y) }
        val plane = planeOf(s)
        if (plane == Plane3D.FREE || s.points.isEmpty()) return s.points.map { P(it.x, it.y) }
        val a = Offset(s.points[0].x, s.points[0].y)
        return s.points.map { val o = Iso3D.unproject(plane, a, it.x, it.y); P(o.x, o.y) }
    }

    fun box(pts: List<P>): FloatArray {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (p in pts) { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        return floatArrayOf(l, t, r, b)
    }

    private fun extent(strokes: List<List<P>>): Float {
        val b = box(strokes.flatten())
        return max(b[2] - b[0], b[3] - b[1])
    }

    /** Eşleşmenin öneri(ler)i: şablon çizgileri (ve 3B cisim şablonunda gölgeli cisim seçeneği). */
    private fun fixes(m: TemplateMatch, group: List<Stroke>, flats: List<List<P>>, plane: Plane3D): List<TemplateFix> {
        val fb = box(flats.flatten())
        var fitted = TemplateFit.fit(m.geometry, fb[0], fb[1], fb[2], fb[3])
        val visual = box(group.flatMap { s -> s.points.map { P(it.x, it.y) } })
        if (plane != Plane3D.FREE && group.none { it.space != null }) {
            // v21 izi: düz görünümde oturtulan şablon düzleme yatırılır ve çizimin göründüğü yere kaydırılır
            val anchor = Offset((fb[0] + fb[2]) / 2f, (fb[1] + fb[3]) / 2f)
            val projected = fitted.map { s -> s.map { val o = Iso3D.project(plane, anchor, it.x, it.y); P(o.x, o.y) } }
            val pb = box(projected.flatten())
            val dx = (visual[0] + visual[2]) / 2f - (pb[0] + pb[2]) / 2f
            val dy = (visual[1] + visual[3]) / 2f - (pb[1] + pb[3]) / 2f
            fitted = projected.map { s -> s.map { P(it.x + dx, it.y + dy) } }
        }
        val size = max(visual[2] - visual[0], visual[3] - visual[1])
        val step = (size / 90f).coerceIn(2f, 6f)
        val q = ContentCodec::quantize
        // v22 sahnede yeni izler ana izin düzlemine konur: yeri, ana izin sahnedeki yeriyle verilir, noktalar ekranda
        // oturtulduğundan eşitlemede düzleme geri açılır (parmağın altına çizilmiş gibi)
        val ref = group.filter { it.space?.isSolid == false }.maxByOrNull { s -> s.points.size }?.space
        val strokes = fitted.map { s -> TemplateFit.densify(s, step).map { StrokePoint(q(it.x), q(it.y)) } }
        val spaces: List<com.karapp.drawguidance.data.model.Space3D?> = if (ref != null) strokes.map { ref } else emptyList()
        val bounds = Rect(visual[0], visual[1], visual[2], visual[3])
        val ids = group.mapTo(LinkedHashSet()) { it.id }
        val base = TemplateFix(m.key, m.name, m.category, m.confidence, m.mode, ids, bounds, strokes, drawn = flats, spaces = spaces)
        if (m.solid == null) return listOf(base)
        // 3B cisim: gölgeli cisim seçeneği (3B çizimde önce)
        val solid = base.copy(solid = m.solid, confidence = m.confidence + if (plane != Plane3D.FREE || group.any { it.params?.depth3d != null }) 0.001f else -0.001f)
        return listOf(base, solid)
    }
}
