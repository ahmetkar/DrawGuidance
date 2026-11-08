package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.content.shapes.MatchMode
import com.karapp.drawguidance.content.shapes.PartFit
import com.karapp.drawguidance.content.shapes.SuggestRules
import com.karapp.drawguidance.content.shapes.VectorShapeMatcher
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.Plane3D
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.ShapeFit
import com.karapp.drawguidance.ui.drawing.ShapeKind
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import kotlin.math.max

/**
 * v38 şekil önerisi: birbirine yakın 4+ elemanın ([strokeIds]) yerine konabilecek şekiller ([templates]; ilk seçenek en
 * iyi tahmin). Her seçenek dikkate alınan bütün çizgileri tek düğmeyle (tek geri alma adımı) değiştirir.
 */
data class ShapeSuggestion(
    val templates: List<TemplateFix>,
    val strokeIds: Set<String>,
    val bounds: Rect,
) {
    /** Tuvalde kesik çizgiyle önizlenen düzeltme: ilk seçeneğin çizgileri. */
    val preview: List<List<StrokePoint>> get() = templates.firstOrNull()?.strokes.orEmpty()
}

/**
 * v38 şekil önerisinin algoritması (yazı ve çizgi önerisinden ayrı):
 * 1. Gruplama: son çizilenle birbirine yakın ([SuggestRules.SHAPE_PAD]) en az [SuggestRules.SHAPE_MIN] eleman (aynı
 *    katman ve düzlem); daha azında öneri yok.
 * 2. Modun şekil sinir ağı (v38: 4+ elemanlı çizimlerle eğitildi) ve şablon veritabanında grup araması
 *    ([TemplateSuggester]: LSH → kosinüs → kNN → chamfer, $Q, IDM, parça grafiği; ağ aday ekler ve yeniden sıralar).
 * 3. Hazır vektör şekillere $Q benzerliği (dört kenarla çizilen kare → Kare şekli; basit türlerde türün kataloğu).
 */
class ShapeSuggester(private val templates: com.karapp.drawguidance.data.repository.TemplateSource?) {

    /** Hazır vektör şekillerin bulutları (ilk kullanımda bir kez). */
    private val vectors: VectorShapeMatcher by lazy {
        VectorShapeMatcher(VECTOR_KINDS.mapNotNull { kind ->
            val outline = outline(kind.name, Rect(0f, 0f, 100f, 100f))
            // Kutuya esneyen şekiller (dikdörtgen, elips…) en-boy oranından bağımsız karşılaştırılır
            if (outline.isEmpty()) null else VectorShapeMatcher.Item(kind.name, outline.map { s -> s.map { P(it.x, it.y) } }, stretch = kind.fit == ShapeFit.BOX)
        })
    }

    /** Son çizilenle ([last]) birbirine yakın elemanlar (bkz. [TemplateSuggester.group]). */
    fun group(all: List<Stroke>, last: Stroke, isInk: (Stroke) -> Boolean): List<Stroke> = TemplateSuggester.group(all, last, isInk)

    /** [group] (birbirine yakın 4+ eleman) için şekil önerisi; bulunamazsa ya da grup kurala uymuyorsa null. */
    fun suggest(group: List<Stroke>, all: List<Stroke>, isInk: (Stroke) -> Boolean): ShapeSuggestion? {
        if (group.size !in SuggestRules.SHAPE_MIN..SuggestRules.SHAPE_MAX) return null
        val kind = templates?.canvasKind
        val max = SimpleSuggest.maxTemplates(kind)
        val index = templates?.index
        val found = if (index != null) {
            runCatching { TemplateSuggester.search(index, group, templates?.searchScope, max) }.getOrDefault(emptyList())
        } else emptyList()
        val flat = group.none { it.space != null || TemplateSuggester.planeOf(it) != Plane3D.FREE }
        val vector = if (flat) {
            vectorFixes(group, vectors, 2) + (kind?.let { runCatching { SimpleSuggest.vectorFixes(it, group) }.getOrNull() }.orEmpty())
        } else emptyList()
        val fixes = SimpleSuggest.merge(found, vector, max)
        if (fixes.isEmpty()) return null
        return ShapeSuggestion(fixes, group.mapTo(LinkedHashSet()) { it.id }, fixes.first().bounds)
    }

    companion object {
        const val VECTOR_PREFIX = "vec."

        private fun box(points: List<P>): Rect? = PartFit.box(points)?.let { Rect(it[0], it[1], it[2], it[3]) }

        private fun square(b: Rect): Rect {
            val s = max(b.width, b.height)
            val c = b.center
            return Rect(c.x - s / 2f, c.y - s / 2f, c.x + s / 2f, c.y + s / 2f)
        }

        /** v34 [b] kutusunun ortasında en/boy oranı [aspect] olan, kutuya sığan çerçeve (kütüphane şekilleri). */
        private fun aspectFrame(b: Rect, aspect: Float): Rect {
            val w: Float; val h: Float
            if (b.width / b.height.coerceAtLeast(1e-3f) > aspect) { h = b.height; w = h * aspect } else { w = b.width; h = w / aspect }
            val c = b.center
            return Rect(c.x - w / 2f, c.y - h / 2f, c.x + w / 2f, c.y + h / 2f)
        }

        /** [matcher]'daki şekillerden [ink] çizgilerine benzeyenler (çizimin yerine tek vektör şekil). */
        fun vectorFixes(ink: List<Stroke>, matcher: VectorShapeMatcher, limit: Int, minConfidence: Float = VectorShapeMatcher.MIN_CONFIDENCE): List<TemplateFix> {
            val lines = ink.map { s -> s.points.map { P(it.x, it.y) } }.filter { it.size >= 2 }
            if (lines.isEmpty()) return emptyList()
            val b = box(lines.flatten()) ?: return emptyList()
            if (max(b.width, b.height) < 24f) return emptyList()
            val ids = ink.mapTo(LinkedHashSet()) { it.id }
            val q = ContentCodec::quantize
            return matcher.match(lines, limit = limit, minConfidence = minConfidence).mapNotNull { m ->
                if (!ShapeRegistry.canBuild(m.id)) return@mapNotNull null
                val fit = ShapeRegistry.fit(m.id)
                val frame = when {
                    fit == ShapeFit.SQUARE -> square(b)
                    ShapeRegistry.isLibrary(m.id) -> aspectFrame(b, ShapeRegistry.aspect(m.id))
                    else -> b
                }
                val outline = outline(m.id, frame)
                if (outline.isEmpty()) return@mapNotNull null
                TemplateFix(
                    key = VECTOR_PREFIX + m.id, name = "", category = "", confidence = m.confidence, mode = MatchMode.GROUP,
                    strokeIds = ids, bounds = frame, strokes = outline.map { s -> s.map { StrokePoint(q(it.x), q(it.y)) } },
                    solid = m.id, vector = true,
                )
            }
        }

        /** Çizimlerin benzetileceği hazır şekiller (kutuya oturan, dış hattı belirgin olanlar). */
        val VECTOR_KINDS: List<ShapeKind> = listOf(
            ShapeKind.CIRCLE, ShapeKind.ELLIPSE, ShapeKind.SQUARE, ShapeKind.RECTANGLE, ShapeKind.ROUNDED_RECT,
            ShapeKind.TRIANGLE, ShapeKind.RIGHT_TRIANGLE, ShapeKind.DIAMOND, ShapeKind.PARALLELOGRAM, ShapeKind.TRAPEZOID,
            ShapeKind.PENTAGON, ShapeKind.HEXAGON, ShapeKind.OCTAGON, ShapeKind.STAR, ShapeKind.HEART, ShapeKind.CROSS,
            ShapeKind.ARROW_BLOCK, ShapeKind.KITE, ShapeKind.SEMICIRCLE, ShapeKind.MOON, ShapeKind.CLOUD, ShapeKind.DROP,
            ShapeKind.LEAF, ShapeKind.HOUSE, ShapeKind.SUN, ShapeKind.FLOWER, ShapeKind.APPLE, ShapeKind.FISH,
            ShapeKind.CUBE, ShapeKind.CYLINDER, ShapeKind.PYRAMID,
        )

        /** Şeklin [frame] kutusundaki çizgileri (yazı ve kesik parçalar hariç). */
        fun outline(kind: String, frame: Rect): List<List<Offset>> = runCatching {
            ShapeRegistry.paths(kind, Offset(frame.left, frame.top), Offset(frame.right, frame.bottom), 2f)
                .filter { it.label == null && !it.dashed && it.points.size >= 2 }
                .map { it.points }
        }.getOrDefault(emptyList())
    }
}
