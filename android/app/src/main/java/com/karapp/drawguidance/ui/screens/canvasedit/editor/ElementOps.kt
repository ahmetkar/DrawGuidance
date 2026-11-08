package com.karapp.drawguidance.ui.screens.canvasedit.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.isQuad
import com.karapp.drawguidance.data.model.isObject
import com.karapp.drawguidance.ui.drawing.inkWidth
import com.karapp.drawguidance.ui.drawing.style
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bir eleman: birbirine değen (mürekkebi üst üste binen) çizimlerin oluşturduğu şekil.
 * Hiçbir çizime değmeyen çizim tek başına bir elemandır. Silgi çizgileri listede
 * görünmez; sildikleri elemana [eraserIds] olarak bağlanır ve onunla birlikte
 * taşınır/döndürülür/ölçeklenir.
 */
data class Element(
    val id: String,
    val number: Int,
    val strokeIds: List<String>,
    val eraserIds: List<String>,
    val groupId: String?,
    val brushType: BrushType,
    val colorHex: String,
    /** v30 gizlenmiş eleman (çizimlerinin hepsi gizli). */
    val hidden: Boolean = false,
) {
    val allIds: Set<String> by lazy { (strokeIds + eraserIds).toSet() }
}

/** Elemanlar panelindeki bir satır: tek bir eleman ya da bir grup ve üyeleri. */
sealed interface ElementRow {
    data class Single(val element: Element) : ElementRow
    data class Group(val groupId: String, val number: Int, val members: List<Element>) : ElementRow
}

/** Çizim listesinden türetilen eleman dizini (çizimler değiştikçe yeniden hesaplanır). */
class ElementIndex(
    val strokes: List<Stroke>,
    val elements: List<Element>,
    val groupNumbers: Map<String, Int>,
) {
    private val byStroke: Map<String, Element> = buildMap {
        elements.forEach { element -> element.allIds.forEach { put(it, element) } }
    }
    private val byId: Map<String, Element> = elements.associateBy { it.id }

    fun elementOf(strokeId: String): Element? = byStroke[strokeId]
    fun element(id: String): Element? = byId[id]

    /** v37 çizimlerin nokta kutuları (sol, üst, sağ, alt): dokunma sınamasında uzaktakiler elenir; gerektikçe hesaplanır. */
    private val boxes = arrayOfNulls<FloatArray>(strokes.size)

    internal fun pointBox(position: Int): FloatArray {
        boxes[position]?.let { return it }
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (p in strokes[position].points) {
            if (p.x < l) l = p.x
            if (p.x > r) r = p.x
            if (p.y < t) t = p.y
            if (p.y > b) b = p.y
        }
        return floatArrayOf(l, t, r, b).also { boxes[position] = it }
    }

    /** Seçime değen elemanları ve bu elemanların gruplarındaki diğer elemanları döndürür. */
    fun expand(ids: Set<String>): Set<String> {
        val touched = ids.mapNotNull { byStroke[it] }.toSet()
        if (touched.isEmpty()) return emptySet()
        val groups = touched.mapNotNull { it.groupId }.toSet()
        return elements
            .filter { it in touched || (it.groupId != null && it.groupId in groups) }
            .flatMap { it.allIds }
            .toSet()
    }

    /** Tamamen seçili elemanlar (panelde ve tuvaldeki etiketlerde gösterilir). */
    fun selectedElements(selectedIds: Set<String>): List<Element> =
        elements.filter { it.strokeIds.isNotEmpty() && selectedIds.containsAll(it.strokeIds) }

    companion object {
        val Empty = ElementIndex(emptyList(), emptyList(), emptyMap())
    }
}

/**
 * Çizim elemanları üzerindeki saf (Android'den bağımsız, test edilebilir) işlemler.
 * Tüm fonksiyonlar yeni bir liste döndürür; girdi değişmez. Dönüşüm fonksiyonları
 * verilen kimliklere doğrudan uygulanır — seçimi gruplara/elemanlara genişletmek
 * [ElementIndex.expand] ile çağıranın işidir.
 */
object ElementOps {

    // --- Eleman dizini ---

    /** Tam hesap (testler ve tek seferlik kullanım için); düzenleyici [ElementIndexer] ile artımlı çalışır. */
    fun index(strokes: List<Stroke>): ElementIndex = ElementIndexer().update(strokes)

    /**
     * Panel satırları: en son çizilen en üstte. Grup, en yeni üyesinin yerinde
     * listelenir ve üyelerini (en yeni üstte) içerir.
     */
    fun rows(index: ElementIndex): List<ElementRow> {
        val emitted = HashSet<String>()
        val rows = ArrayList<ElementRow>()
        // v37 grup üyeleri bir kez toplanır (her grup için bütün elemanlar yeniden taranmaz)
        val byGroup = index.elements.filter { it.groupId != null }.groupBy { it.groupId!! }
        for (element in index.elements.asReversed()) {
            val groupId = element.groupId
            if (groupId == null) {
                rows += ElementRow.Single(element)
            } else if (emitted.add(groupId)) {
                val members = byGroup.getValue(groupId).asReversed()
                rows += ElementRow.Group(groupId, index.groupNumbers.getValue(groupId), members)
            }
        }
        return rows
    }

    /**
     * [point] noktasındaki (tuval koordinatı) en üstteki elemanı bulur. [tolerance]
     * fırça kalınlığına eklenen dokunma payıdır.
     */
    fun hitTest(index: ElementIndex, point: Offset, tolerance: Float, skip: Set<String> = emptySet()): Element? {
        // v37 üstten aşağı: ilk değen en üsttekidir; kutusu uzaktaki çizimin noktalarına hiç bakılmaz
        val strokes = index.strokes
        for (position in strokes.indices.reversed()) {
            val stroke = strokes[position]
            if (stroke.brushType == BrushType.ERASER || stroke.points.isEmpty() || stroke.id in skip) continue
            val half = if (stroke.brushType.isQuad) 0f else halfWidth(stroke)
            if (stroke.brushType != BrushType.SHAPE && !nearBox(index.pointBox(position), point, half + tolerance)) continue
            if (!insideClip(stroke, point)) continue
            if (distanceToShape(point, stroke) <= half + tolerance) return index.elementOf(stroke.id)
        }
        return null
    }

    /**
     * Nokta, nokta kutusunun [pad] kadar genişletilmişinde mi. Şekil dışı çizimlerde uzaklık noktalara ölçüldüğünden
     * kutunun dışındaki nokta değemez.
     */
    private fun nearBox(b: FloatArray, point: Offset, pad: Float): Boolean =
        point.x >= b[0] - pad && point.x <= b[2] + pad && point.y >= b[1] - pad && point.y <= b[3] + pad

    // --- Gruplar ---

    /** Seçimdeki elemanları (ve dahil oldukları grupları) tek bir yeni grupta birleştirir. En az iki eleman gerekir. */
    fun group(strokes: List<Stroke>, ids: Set<String>, newGroupId: String, current: ElementIndex? = null): List<Stroke> {
        val index = current?.takeIf { it.strokes === strokes } ?: index(strokes)
        val targets = index.expand(ids)
        val elementCount = index.elements.count { it.strokeIds.any { id -> id in targets } }
        if (elementCount < 2) return strokes
        return strokes.map { if (it.id in targets) it.copy(groupId = newGroupId) else it }
    }

    /** Seçimdeki elemanların dahil olduğu grupları çözer. */
    fun ungroup(strokes: List<Stroke>, ids: Set<String>, current: ElementIndex? = null): List<Stroke> {
        val targets = (current?.takeIf { it.strokes === strokes } ?: index(strokes)).expand(ids)
        return strokes.map { if (it.id in targets && it.groupId != null) it.copy(groupId = null) else it }
    }

    // --- Dönüşümler ---

    fun translate(strokes: List<Stroke>, ids: Set<String>, dx: Float, dy: Float): List<Stroke> {
        if (dx == 0f && dy == 0f) return strokes
        return transformPoints(strokes, ids) { it.moved(it.x + dx, it.y + dy) }
    }

    /** [degrees] saat yönünde (ekran koordinatlarında) [pivot] etrafında döndürür. */
    fun rotate(strokes: List<Stroke>, ids: Set<String>, degrees: Float, pivot: Offset): List<Stroke> {
        if (degrees % 360f == 0f) return strokes
        val radians = Math.toRadians(degrees.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        return transformPoints(strokes, ids) {
            val x = it.x - pivot.x
            val y = it.y - pivot.y
            it.moved(pivot.x + x * c - y * s, pivot.y + x * s + y * c)
        }
    }

    /** [pivot] sabit kalacak şekilde yatayda [sx], dikeyde [sy] kat ölçekler; fırça kalınlığı da orantılı değişir. */
    fun scale(strokes: List<Stroke>, ids: Set<String>, sx: Float, sy: Float, pivot: Offset): List<Stroke> {
        val fx = sx.coerceIn(MIN_SCALE, MAX_SCALE)
        val fy = sy.coerceIn(MIN_SCALE, MAX_SCALE)
        if (fx == 1f && fy == 1f) return strokes
        val widthFactor = sqrt(abs(fx * fy))
        return strokes.map { stroke ->
            if (stroke.id !in ids) stroke
            else stroke.copy(
                points = stroke.points.map { it.moved(pivot.x + (it.x - pivot.x) * fx, pivot.y + (it.y - pivot.y) * fy) },
                clip = stroke.clip?.map { point(pivot.x + (it.x - pivot.x) * fx, pivot.y + (it.y - pivot.y) * fy) },
                sizePx = if (stroke.brushType == BrushType.IMAGE) stroke.sizePx else (stroke.sizePx * widthFactor).coerceIn(0.5f, 2000f),
                shape = stroke.shape?.let { spec ->
                    spec.copy(style = spec.style.copy(
                        strokeWidth = (spec.style.strokeWidth * widthFactor).coerceIn(0.2f, 400f),
                        cornerRadius = spec.style.cornerRadius * widthFactor,
                    ))
                },
            )
        }
    }

    /** Renk verir; silgilerin rengi anlamsız olduğu için atlanır. */
    /** v30 verilen çizimleri gizler ya da gösterir; değişen yoksa aynı liste döner. */
    fun setHidden(strokes: List<Stroke>, ids: Set<String>, hidden: Boolean): List<Stroke> {
        if (ids.isEmpty() || strokes.none { it.id in ids && it.hidden != hidden }) return strokes
        return strokes.map { if (it.id in ids && it.hidden != hidden) it.copy(hidden = hidden) else it }
    }

    fun recolor(strokes: List<Stroke>, ids: Set<String>, colorHex: String): List<Stroke> = strokes.map {
        val shape = it.shape
        val diagram = it.diagram
        if (it.id !in ids || it.brushType == BrushType.ERASER || it.brushType == BrushType.IMAGE) it
        else when {
            // Tüm elemana renk: parçalara verilmiş ayrı çizgi renkleri de bu renge döner
            shape != null -> it.copy(
                colorHex = colorHex,
                shape = shape.copy(style = shape.style.copy(strokeColorHex = colorHex), subpaths = shape.subpaths.map { p -> if (p.strokeColorHex == null) p else p.copy(strokeColorHex = null) }),
            )
            diagram != null -> it.copy(
                colorHex = colorHex,
                diagram = diagram.copy(
                    style = diagram.style.copy(strokeColorHex = colorHex),
                    nodes = diagram.nodes.map { n -> if (n.strokeColorHex == null && n.textColorHex == null) n else n.copy(strokeColorHex = null, textColorHex = null) },
                    edges = diagram.edges.map { e -> if (e.colorHex == null) e else e.copy(colorHex = null) },
                ),
            )
            // v27 yazıda düz renk gradyan dolguyu kaldırır
            it.brushType == BrushType.TEXT && it.text?.box?.effects?.gradient != null ->
                it.copy(colorHex = colorHex, text = it.text!!.copy(box = it.text!!.box!!.let { b -> b.copy(effects = b.effects!!.copy(gradient = null).takeUnless { e -> e.isEmpty }) }.orNull()))
            it.colorHex.equals(colorHex, ignoreCase = true) -> it
            else -> it.copy(colorHex = colorHex)
        }
    }

    /**
     * v27 gradyan dolgu: şekilde dolgu (kutusuna göre), yazıda harflerin dolgusu; [gradient] null ise gradyan kaldırılır.
     * Şekilde ortalama renk düz dolgu olarak da yazılır (eski sürümler ve tıklama denetimi için).
     */
    fun fillGradient(strokes: List<Stroke>, ids: Set<String>, gradient: com.karapp.drawguidance.data.model.GradientSpec?): List<Stroke> = strokes.map {
        if (it.id !in ids || !canFill(it)) return@map it
        val shape = it.shape
        when {
            shape != null && com.karapp.drawguidance.ui.drawing.ShapeRenderer.isFillable(it) -> {
                if (shape.style.fillGradient == gradient) return@map it
                val avg = gradient?.let { g -> String.format("#%06X", com.karapp.drawguidance.ui.drawing.Gradients.average(g) and 0xFFFFFF) }
                it.copy(shape = shape.copy(style = shape.style.copy(fillGradient = gradient, fillColorHex = avg ?: shape.style.fillColorHex)))
            }
            it.brushType == BrushType.TEXT && it.text != null -> {
                val text = it.text!!
                val box = text.box ?: com.karapp.drawguidance.data.model.TextBox()
                val fx = (box.effects ?: com.karapp.drawguidance.data.model.TextEffects()).copy(gradient = gradient)
                if (box.effects?.gradient == gradient) return@map it
                it.copy(text = text.copy(box = box.copy(effects = fx.takeUnless { e -> e.isEmpty }).orNull()))
            }
            else -> it
        }
    }

    /** v27 seçili elemanın gradyan dolgusu (yoksa null). */
    fun fillGradientOf(stroke: Stroke): com.karapp.drawguidance.data.model.GradientSpec? = when {
        stroke.shape != null -> stroke.shape?.style?.fillGradient
        stroke.brushType == BrushType.TEXT -> stroke.text?.box?.effects?.gradient
        else -> null
    }

    /** Eleman dolgu alabilir mi (Renk Ver ve boya kutusu): silgi ve resim dışındaki her eleman. */
    fun canFill(stroke: Stroke): Boolean = stroke.brushType != BrushType.ERASER && stroke.brushType != BrushType.IMAGE

    /**
     * Elemanın dolgu rengi: şekilde iç dolgu (dolgu alamayan açık şekilde, ör. doğru, kenar rengi),
     * diyagramda kutuların dolgusu, yazıda harflerin iç rengi, serbest çizimde mürekkep rengi.
     */
    fun fillColorOf(stroke: Stroke): String? {
        val shape = stroke.shape
        val diagram = stroke.diagram
        return when {
            !canFill(stroke) -> null
            shape != null -> if (com.karapp.drawguidance.ui.drawing.ShapeRenderer.isFillable(stroke)) shape.style.fillColorHex
                else shape.style.strokeColorHex
            diagram != null -> diagram.style.fillColorHex
            else -> stroke.colorHex
        }
    }

    /** Seçili elemanların dolgu rengini verir ([fillColorOf] ile aynı anlamda). */
    fun fill(strokes: List<Stroke>, ids: Set<String>, colorHex: String): List<Stroke> = strokes.map {
        if (it.id !in ids || !canFill(it)) return@map it
        val shape = it.shape
        val diagram = it.diagram
        when {
            shape != null && com.karapp.drawguidance.ui.drawing.ShapeRenderer.isFillable(it) ->
                if (shape.style.fillColorHex.equals(colorHex, ignoreCase = true) && shape.style.fillGradient == null && shape.subpaths.none { p -> p.fillColorHex.let { f -> f != null && !keepsFill(f) } }) it
                // Tüm elemanın dolgusu: bölgelere verilmiş ayrı dolgular da bu renge döner (v21: 3B cisimlerin gölge
                // tonları ve dış hattı kalır; yeni renge göre gölgelenir); v27 düz renk gradyanı kaldırır
                else it.copy(shape = shape.copy(style = shape.style.copy(fillColorHex = colorHex, fillGradient = null), subpaths = shape.subpaths.map { p -> val f = p.fillColorHex; if (f == null || keepsFill(f)) p else p.copy(fillColorHex = null) }))
            shape != null ->
                if (shape.style.strokeColorHex.equals(colorHex, ignoreCase = true)) it
                else it.copy(colorHex = colorHex, shape = shape.copy(style = shape.style.copy(strokeColorHex = colorHex)))
            diagram != null ->
                if (diagram.style.fillColorHex.equals(colorHex, ignoreCase = true) && diagram.nodes.none { n -> n.fillColorHex != null }) it
                else it.copy(diagram = diagram.copy(style = diagram.style.copy(fillColorHex = colorHex), nodes = diagram.nodes.map { n -> if (n.fillColorHex == null) n else n.copy(fillColorHex = null) }))
            it.colorHex.equals(colorHex, ignoreCase = true) -> it
            else -> it.copy(colorHex = colorHex)
        }
    }

    /** Silginin değdiği nesne elemanları (v15'ten beri silgi nesneleri silmez; yalnızca bilgi amaçlı). */
    fun objectsHitBy(strokes: List<Stroke>, eraser: Stroke): Set<String> {
        if (eraser.brushType != BrushType.ERASER || eraser.points.isEmpty()) return emptySet()
        val half = halfWidth(eraser)
        val samples = densePoints(eraser.points, max(2f, half * 0.6f))
        return strokes.filter { it.brushType.isObject }
            .filter { target ->
                val box = bounds(target)?.inflate(half + 1f) ?: return@filter false
                samples.any { box.contains(it) && distanceToShape(it, target) <= half + halfWidth(target) }
            }
            .mapTo(HashSet()) { it.id }
    }

    fun delete(strokes: List<Stroke>, ids: Set<String>): List<Stroke> =
        strokes.filterNot { it.id in ids }.mapIndexed { order, stroke -> stroke.copy(order = order) }

    /** Dönüşüm sonuçları 1/100 birime yuvarlanır; böylece veri kompakt ve kayıpsız saklanır. */
    private fun point(x: Float, y: Float) = StrokePoint(ContentCodec.quantize(x), ContentCodec.quantize(y))

    /** v18: kalem basıncı ve eğimi korunarak yeni yer. */
    private fun StrokePoint.moved(x: Float, y: Float) = at(ContentCodec.quantize(x), ContentCodec.quantize(y))

    // --- Tamamen silinenlerin temizliği ---

    /**
     * Sonradan çizilen silgilerin tamamen örttüğü (artık hiç görünmeyen) çizim ve resimleri,
     * ardından artık görünen hiçbir şeye değmeyen silgileri veriden çıkarır. Görüntü değişmez;
     * elemanlar listesinden kaybolurlar ve boşuna yer kaplamazlar.
     * Örtme testi temkinlidir: fırça izinin kenarları, uçları ve pastel tanecik payı dahil
     * örneklenen her nokta bir silginin içinde kalmalıdır.
     */
    fun pruneErased(strokes: List<Stroke>, focus: Rect? = null): List<Stroke> {
        // Yalnızca tam silen (akışkanlığı/opaklığı tam) silgiler bir çizimi tamamen örtebilir
        val erasers = strokes.withIndex().filter { it.value.brushType == BrushType.ERASER && it.value.points.isNotEmpty() }
        if (erasers.isEmpty()) return strokes
        val eraserBounds = erasers.associate { it.value.id to fullBounds(it.value)!! }

        val removed = HashSet<String>()
        strokes.forEachIndexed { position, stroke ->
            if (stroke.brushType == BrushType.ERASER || stroke.points.isEmpty()) return@forEachIndexed
            val box = bounds(stroke) ?: return@forEachIndexed
            if (focus != null && !focus.overlaps(box)) return@forEachIndexed
            val later = erasers.filter {
                it.index > position && it.value.flow >= 0.99f && it.value.opacity >= 0.99f && eraserBounds.getValue(it.value.id).overlaps(box)
            }.map { it.value }
            if (later.isEmpty()) return@forEachIndexed
            val union = later.map { eraserBounds.getValue(it.id) }.reduce { a, b ->
                Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))
            }
            if (union.left > box.left || union.top > box.top || union.right < box.right || union.bottom < box.bottom) return@forEachIndexed
            val covered = coverageSamples(stroke).all { sample ->
                later.any { eraser ->
                    eraserBounds.getValue(eraser.id).contains(sample) &&
                        distanceToPolyline(sample, eraser.points) <= halfWidth(eraser) - COVER_MARGIN
                }
            }
            if (covered) removed += stroke.id
        }

        val remaining = strokes.filterNot { it.id in removed }
        val visibleBefore = ArrayList<Stroke>()
        val kept = ArrayList<Stroke>(remaining.size)
        val gcArea = focus?.let { f ->
            removed.mapNotNull { id -> strokes.firstOrNull { it.id == id }?.let { bounds(it) } }
                .fold(f) { a, b -> Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom)) }
        }
        for (stroke in remaining) {
            if (stroke.brushType != BrushType.ERASER) {
                visibleBefore += stroke
                kept += stroke
            } else if (gcArea != null && bounds(stroke)?.overlaps(gcArea) != true) {
                kept += stroke
            } else if (stroke.points.isNotEmpty() && visibleBefore.any { eraserTouches(stroke, it) }) {
                kept += stroke
            }
        }
        if (kept.size == strokes.size) return strokes
        return kept.mapIndexed { order, stroke -> if (stroke.order == order) stroke else stroke.copy(order = order) }
    }

    private fun eraserTouches(eraser: Stroke, target: Stroke): Boolean {
        val eb = bounds(eraser) ?: return false
        val tb = bounds(target) ?: return false
        if (!eb.overlaps(tb)) return false
        if (target.brushType.isQuad) {
            val half = halfWidth(eraser)
            return densePoints(eraser.points, half).any { distanceToShape(it, target) <= half }
        }
        return StrokeShape(eraser, 0).touches(StrokeShape(target, 0))
    }

    private fun coverageSamples(stroke: Stroke): Sequence<Offset> = sequence {
        val pts = stroke.points
        if (stroke.brushType.isQuad) {
            if (pts.size < 4) return@sequence
            val n = 16
            for (i in 0..n) for (j in 0..n) {
                val u = i / n.toFloat()
                val v = j / n.toFloat()
                val top = lerp(pts[0], pts[1], u)
                val bottom = lerp(pts[3], pts[2], u)
                yield(Offset(top.x + (bottom.x - top.x) * v, top.y + (bottom.y - top.y) * v))
            }
            return@sequence
        }
        val half = halfWidth(stroke)
        val r = half * stroke.brushType.style().reach
        if (pts.size == 1) {
            val c = Offset(pts[0].x, pts[0].y)
            yield(c)
            for (k in 0 until 16) {
                val a = (k / 16f) * 2f * Math.PI.toFloat()
                yield(Offset(c.x + cos(a) * r, c.y + sin(a) * r))
                yield(Offset(c.x + cos(a) * r * 0.5f, c.y + sin(a) * r * 0.5f))
            }
            return@sequence
        }
        val step = (half * 0.5f).coerceIn(0.5f, 4f)
        val offsets = floatArrayOf(0f, -1f, 1f, -0.5f, 0.5f)
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val length = hypot(b.x - a.x, b.y - a.y)
            val dx = if (length == 0f) 1f else (b.x - a.x) / length
            val dy = if (length == 0f) 0f else (b.y - a.y) / length
            var t = 0f
            while (true) {
                val px = a.x + dx * t
                val py = a.y + dy * t
                for (k in offsets) yield(Offset(px - dy * k * r, py + dx * k * r))
                if (t >= length) break
                t = min(length, t + step)
            }
            // Köşe ve uç payları (yuvarlak/kare uçlar, birleşimler)
            for (k in 0 until 12) {
                val ang = (k / 12f) * 2f * Math.PI.toFloat()
                yield(Offset(a.x + cos(ang) * r, a.y + sin(ang) * r))
            }
            if (i == 1) for (k in floatArrayOf(-1f, 0f, 1f)) yield(Offset(a.x - dx * r - dy * k * r, a.y - dy * r + dx * k * r))
            if (i == pts.size - 1) {
                for (k in 0 until 12) {
                    val ang = (k / 12f) * 2f * Math.PI.toFloat()
                    yield(Offset(b.x + cos(ang) * r, b.y + sin(ang) * r))
                }
                for (k in floatArrayOf(-1f, 0f, 1f)) yield(Offset(b.x + dx * r - dy * k * r, b.y + dy * r + dx * k * r))
            }
        }
    }

    private fun lerp(a: StrokePoint, b: StrokePoint, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    private fun densePoints(points: List<StrokePoint>, spacing: Float): List<Offset> {
        if (points.size == 1) return listOf(Offset(points[0].x, points[0].y))
        val out = ArrayList<Offset>()
        val step = spacing.coerceAtLeast(1f)
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val length = hypot(b.x - a.x, b.y - a.y)
            var t = 0f
            while (t <= length) {
                val f = if (length == 0f) 0f else t / length
                out += Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
                t += step
            }
        }
        out += Offset(points.last().x, points.last().y)
        return out
    }

    /** Noktanın çizime (resimde dörtgenin içi 0) uzaklığı. */
    fun distanceToShape(p: Offset, stroke: Stroke): Float {
        if (stroke.brushType == BrushType.SHAPE) return com.karapp.drawguidance.ui.drawing.ShapeRenderer.distanceTo(stroke, p)
        if (!stroke.brushType.isQuad || stroke.points.size < 4) return distanceToPolyline(p, stroke.points)
        val q = stroke.points
        if (insideQuad(p, q)) return 0f
        var best = Float.MAX_VALUE
        for (i in 0 until 4) {
            val a = q[i]
            val b = q[(i + 1) % 4]
            best = min(best, pointSegmentDistance(p.x, p.y, a.x, a.y, b.x, b.y))
        }
        return best
    }

    private fun insideQuad(p: Offset, q: List<StrokePoint>): Boolean {
        var positive = false
        var negative = false
        for (i in 0 until 4) {
            val a = q[i]
            val b = q[(i + 1) % 4]
            val c = cross(a.x, a.y, b.x, b.y, p.x, p.y)
            if (c > 0) positive = true
            if (c < 0) negative = true
        }
        return !(positive && negative)
    }

    /** Noktalara (ve varsa kırpma dörtgenine) aynı dönüşümü uygular. */
    private inline fun transformPoints(strokes: List<Stroke>, ids: Set<String>, crossinline f: (StrokePoint) -> StrokePoint): List<Stroke> =
        strokes.map { stroke ->
            if (stroke.id !in ids) stroke
            else stroke.copy(points = stroke.points.map { f(it) }, clip = stroke.clip?.map { f(it) })
        }

    // --- Geometri ---

    fun halfWidth(stroke: Stroke): Float =
        if (stroke.brushType == BrushType.SHAPE) com.karapp.drawguidance.ui.drawing.ShapeRenderer.halfWidth(stroke)
        else stroke.brushType.inkWidth(stroke.sizePx, stroke.strength) / 2f

    /** Elemanın tuval koordinatlarındaki sınırları (fırça kalınlığı dahil). */
    fun bounds(stroke: Stroke): Rect? {
        val full = fullBounds(stroke) ?: return null
        // Kırpılmış elemanda yalnızca görünen kısım
        val clip = stroke.clip?.takeIf { it.size == 4 } ?: return full
        val c = Rect(clip.minOf { it.x }, clip.minOf { it.y }, clip.maxOf { it.x }, clip.maxOf { it.y })
        if (!full.overlaps(c)) return null
        return full.intersect(c)
    }

    /** Kırpma hesaba katılmadan elemanın tüm alanı (değme testleri ve iç hesaplar için; boş değilse hiç null olmaz). */
    fun fullBounds(stroke: Stroke): Rect? {
        if (stroke.points.isEmpty()) return null
        val half = if (stroke.brushType.isQuad) 0f else halfWidth(stroke)
        var left = Float.MAX_VALUE; var top = Float.MAX_VALUE; var right = -Float.MAX_VALUE; var bottom = -Float.MAX_VALUE
        for (p in stroke.points) {
            left = min(left, p.x); top = min(top, p.y); right = max(right, p.x); bottom = max(bottom, p.y)
        }
        return Rect(left - half, top - half, right + half, bottom + half)
    }

    /** Nokta elemanın kırpma dörtgeninin içinde mi (kırpma yoksa her zaman evet). */
    fun insideClip(stroke: Stroke, p: Offset): Boolean {
        val clip = stroke.clip?.takeIf { it.size == 4 } ?: return true
        return insideQuad(p, clip)
    }

    fun bounds(strokes: List<Stroke>): Rect? = strokes.mapNotNull { bounds(it) }.reduceOrNull { a, b ->
        Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))
    }

    /** Seçimin görünen (silgi hariç) sınırları. */
    fun selectionBounds(strokes: List<Stroke>, ids: Set<String>): Rect? =
        bounds(strokes.filter { it.id in ids && it.brushType != BrushType.ERASER })

    fun distanceToPolyline(p: Offset, points: List<StrokePoint>): Float {
        if (points.size == 1) return hypot(p.x - points[0].x, p.y - points[0].y)
        var best = Float.MAX_VALUE
        for (i in 1 until points.size) {
            best = min(best, pointSegmentDistance(p.x, p.y, points[i - 1].x, points[i - 1].y, points[i].x, points[i].y))
        }
        return best
    }

    private fun pointSegmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    private fun segmentDistance(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Float {
        if (segmentsIntersect(ax, ay, bx, by, cx, cy, dx, dy)) return 0f
        return min(
            min(pointSegmentDistance(ax, ay, cx, cy, dx, dy), pointSegmentDistance(bx, by, cx, cy, dx, dy)),
            min(pointSegmentDistance(cx, cy, ax, ay, bx, by), pointSegmentDistance(dx, dy, ax, ay, bx, by)),
        )
    }

    private fun segmentsIntersect(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
        val d1 = cross(cx, cy, dx, dy, ax, ay)
        val d2 = cross(cx, cy, dx, dy, bx, by)
        val d3 = cross(ax, ay, bx, by, cx, cy)
        val d4 = cross(ax, ay, bx, by, dx, dy)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    private fun cross(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float): Float =
        (bx - ax) * (py - ay) - (by - ay) * (px - ax)

    /** Değme testini hızlandırmak için çizimi küçük parçalara (sınır kutularıyla) böler. */
    internal class StrokeShape(val stroke: Stroke, val position: Int) {
        val isImage = stroke.brushType.isObject
        /**
         * Katman: farklı katmandaki çizimler değse de tek eleman olmaz; silgi yalnızca kendi katmanını etkiler. v30
         * gizlenen çizimler ayrı bir katman gibi sayılır: görünen çizimlerle birleşmez, görünen silgiler onlara bağlanmaz.
         */
        val layer: String = (stroke.layerId ?: "") + if (stroke.hidden) "\u0000gizli" else ""
        val half = if (isImage) 0f else halfWidth(stroke)
        private val xs = FloatArray(stroke.points.size) { stroke.points[it].x }
        private val ys = FloatArray(stroke.points.size) { stroke.points[it].y }
        val expandedBounds: Rect = (fullBounds(stroke) ?: Rect.Zero).inflate(TOUCH_EPSILON)
        private val chunks: List<IntRange>
        val chunkBounds: List<Rect>

        init {
            val n = xs.size
            val ranges = ArrayList<IntRange>()
            if (n == 1) ranges += 0..0
            else {
                var start = 0
                while (start < n - 1) {
                    val end = min(start + CHUNK, n - 1)
                    ranges += start..end
                    start = end
                }
            }
            chunks = ranges
            chunkBounds = ranges.map { r ->
                var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var rr = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
                for (i in r) { l = min(l, xs[i]); t = min(t, ys[i]); rr = max(rr, xs[i]); b = max(b, ys[i]) }
                Rect(l - half, t - half, rr + half, b + half)
            }
        }

        fun touches(other: StrokeShape): Boolean {
            if (!expandedBounds.overlaps(other.expandedBounds)) return false
            val limit = half + other.half + TOUCH_EPSILON
            for (a in chunks.indices) {
                val boxA = chunkBounds[a].inflate(TOUCH_EPSILON)
                for (b in other.chunks.indices) {
                    if (!boxA.overlaps(other.chunkBounds[b])) continue
                    if (chunkDistance(chunks[a], other, other.chunks[b]) <= limit) return true
                }
            }
            return false
        }

        private fun chunkDistance(range: IntRange, other: StrokeShape, otherRange: IntRange): Float {
            var best = Float.MAX_VALUE
            val singleA = range.first == range.last
            val singleB = otherRange.first == otherRange.last
            if (singleA && singleB) return hypot(xs[range.first] - other.xs[otherRange.first], ys[range.first] - other.ys[otherRange.first])
            if (singleA) {
                for (j in otherRange.first until otherRange.last) {
                    best = min(best, pointSegmentDistance(xs[range.first], ys[range.first], other.xs[j], other.ys[j], other.xs[j + 1], other.ys[j + 1]))
                }
                return best
            }
            if (singleB) {
                for (i in range.first until range.last) {
                    best = min(best, pointSegmentDistance(other.xs[otherRange.first], other.ys[otherRange.first], xs[i], ys[i], xs[i + 1], ys[i + 1]))
                }
                return best
            }
            for (i in range.first until range.last) for (j in otherRange.first until otherRange.last) {
                best = min(best, segmentDistance(xs[i], ys[i], xs[i + 1], ys[i + 1], other.xs[j], other.ys[j], other.xs[j + 1], other.ys[j + 1]))
                if (best == 0f) return 0f
            }
            return best
        }

        /**
         * İki çizimin mürekkeplerinin değdiği yerler: (x, y, r) üçlüleri; r, değme bölgesinin yaklaşık yarıçapı.
         * Boşsa değmiyorlar. Sonradan bir silgi bu noktaların hepsini silerse çizimler ayrı elemanlara bölünür.
         */
        fun contacts(other: StrokeShape, maxContacts: Int = 128): FloatArray {
            if (!expandedBounds.overlaps(other.expandedBounds)) return EMPTY_CONTACTS
            val limit = half + other.half + TOUCH_EPSILON
            val step = max(0.5f, min(half, other.half).coerceAtLeast(0.5f))
            val out = ContactList(step)
            var nearestX = 0f; var nearestY = 0f; var nearest = Float.MAX_VALUE
            for (a in chunks.indices) {
                val boxA = chunkBounds[a].inflate(TOUCH_EPSILON)
                for (b in other.chunks.indices) {
                    if (!boxA.overlaps(other.chunkBounds[b])) continue
                    if (chunkDistance(chunks[a], other, other.chunks[b]) > limit) continue
                    val best = collect(chunks[a], other, other.chunks[b], limit, step, out)
                    if (best[2] < nearest) { nearest = best[2]; nearestX = best[0]; nearestY = best[1] }
                    if (out.size >= maxContacts * 4) break
                }
            }
            // Örnekleme kaçırsa bile değen çizimlerin en az bir değme noktası olsun
            if (out.size == 0 && nearest <= limit + step) out.add(nearestX, nearestY, 0.5f)
            return out.toArray(maxContacts)
        }

        /** Değme noktalarını toplar; en yakın örneği (x, y, mesafe) [nearest] içine yazar. */
        private fun collect(range: IntRange, other: StrokeShape, otherRange: IntRange, limit: Float, step: Float, out: ContactList): FloatArray {
            val maxR = min(half, other.half).coerceAtLeast(0.5f)
            val nearest = floatArrayOf(0f, 0f, Float.MAX_VALUE)
            // Tek noktalı parçalar sıfır uzunluklu doğru parçası sayılır
            val aFirst = range.first; val aLast = if (range.first == range.last) range.first + 1 else range.last
            val bFirst = otherRange.first; val bLast = if (otherRange.first == otherRange.last) otherRange.first + 1 else otherRange.last
            fun ax(k: Int) = xs[min(k, xs.size - 1)]
            fun ay(k: Int) = ys[min(k, ys.size - 1)]
            fun bx(k: Int) = other.xs[min(k, other.xs.size - 1)]
            fun by(k: Int) = other.ys[min(k, other.ys.size - 1)]

            fun probe(px: Float, py: Float, againstOther: Boolean) {
                var best = Float.MAX_VALUE; var qx = 0f; var qy = 0f
                val first = if (againstOther) bFirst else aFirst
                val last = if (againstOther) bLast else aLast
                for (k in first until last) {
                    val x0 = if (againstOther) bx(k) else ax(k); val y0 = if (againstOther) by(k) else ay(k)
                    val x1 = if (againstOther) bx(k + 1) else ax(k + 1); val y1 = if (againstOther) by(k + 1) else ay(k + 1)
                    val dx = x1 - x0; val dy = y1 - y0
                    val l2 = dx * dx + dy * dy
                    val t = if (l2 == 0f) 0f else (((px - x0) * dx + (py - y0) * dy) / l2).coerceIn(0f, 1f)
                    val cx = x0 + t * dx; val cy = y0 + t * dy
                    val ddx = px - cx; val ddy = py - cy
                    val d = sqrt(ddx * ddx + ddy * ddy)
                    if (d < best) { best = d; qx = cx; qy = cy }
                }
                val mx = (px + qx) / 2f; val my = (py + qy) / 2f
                if (best < nearest[2]) { nearest[0] = mx; nearest[1] = my; nearest[2] = best }
                if (best <= limit) out.add(mx, my, ((limit - best) / 2f).coerceIn(0.5f, maxR))
            }
            // Bu çizim boyunca örnekler ve karşı çizimin köşeleri
            for (k in aFirst until aLast) {
                val x0 = ax(k); val y0 = ay(k); val x1 = ax(k + 1); val y1 = ay(k + 1)
                val length = hypot(x1 - x0, y1 - y0)
                val n = max(1, kotlin.math.ceil(length / step).toInt())
                for (q in 0..n) {
                    val f = q / n.toFloat()
                    probe(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, againstOther = true)
                }
            }
            for (k in bFirst..bLast) probe(bx(k), by(k), againstOther = false)
            return nearest
        }

        /** Bu çizimin (silginin) kaç noktası [target] çiziminin mürekkebine değiyor. */
        fun pointsTouching(target: StrokeShape): Int {
            val limit = half + target.half
            var count = 0
            for (i in xs.indices) {
                if (distanceToShape(Offset(xs[i], ys[i]), target.stroke) <= limit) count++
            }
            return count
        }
    }

    private val EMPTY_CONTACTS = FloatArray(0)

    /** Değme noktaları; aynı küçük hücreye düşenler tekilleştirilir. */
    private class ContactList(private val cell: Float) {
        private var data = FloatArray(48)
        var size = 0
            private set
        private val seen = HashSet<Long>()
        fun add(x: Float, y: Float, r: Float) {
            val key = (kotlin.math.floor(x / cell).toLong() shl 32) or (kotlin.math.floor(y / cell).toLong() and 0xFFFFFFFFL)
            if (!seen.add(key)) return
            if (size + 3 > data.size) data = data.copyOf(data.size * 2)
            data[size++] = x; data[size++] = y; data[size++] = r
        }
        /** En fazla [max] nokta; fazlası eşit aralıkla seyreltilir. */
        fun toArray(max: Int): FloatArray {
            val count = size / 3
            if (count <= max) return data.copyOf(size)
            val out = FloatArray(max * 3)
            for (i in 0 until max) {
                val src = (i.toLong() * (count - 1) / (max - 1).coerceAtLeast(1)).toInt()
                out[i * 3] = data[src * 3]; out[i * 3 + 1] = data[src * 3 + 1]; out[i * 3 + 2] = data[src * 3 + 2]
            }
            return out
        }
    }

    private const val CHUNK = 12
    private const val COVER_MARGIN = 0.5f
    private const val TOUCH_EPSILON = 0.5f
    const val MIN_SCALE = 0.05f
    const val MAX_SCALE = 20f
}

/**
 * Eleman dizinini artımlı hesaplar. Çizimler yalnızca sona eklendiyse (yeni çizim) sadece
 * yeni çizimler, uzamsal ızgarada komşu olan çizimlerle karşılaştırılır; aksi halde (silme,
 * taşıma, geri alma) aynı algoritmayla baştan hesaplanır. İş parçacığı güvenlidir; düzenleyici
 * bunu arka planda çalıştırır.
 */
class ElementIndexer {
    private var strokes: List<Stroke> = emptyList()
    private var index: ElementIndex = ElementIndex.Empty
    private val shapes = ArrayList<ElementOps.StrokeShape>()
    private var parent = IntArray(64)
    private val grid = HashMap<Long, ArrayList<Int>>()
    /** Silgi kimliği → bağlandığı çizim şeklinin sırası */
    private val eraserOwner = HashMap<String, Int>()
    private val eraserIds = ArrayList<String>()

    /**
     * Tam (akışkanlığı ve opaklığı tam) silgiler. Bir silgi iki çizimin değdiği yerlerin hepsini silerse o
     * iki çizim görünüşte artık birleşik değildir; bileşenler silgiler hesaba katılarak yeniden kurulur.
     */
    private val fullErasers = ArrayList<ElementOps.StrokeShape>()
    private val eraserGrid = HashMap<Long, ArrayList<Int>>()
    /** Çift başına "silgilerden sonra hâlâ değiyor mu" sonucu (çizimler değişmediği sürece geçerli). */
    private val pairConnected = HashMap<Long, Boolean>()
    /** Silgi kesmesi sonrası yeniden kurulacak bileşenlerden birer üye çizim (kökler sonradan değişebilir). */
    private val rebuildMembers = HashSet<Int>()
    /** Baştan hesaplamada silgi kesmeleri tek tek değil, sonda tüm bileşenler için bir kez değerlendirilir. */
    private var replaying = false
    private var rebuildAll = false
    private var needsRebuild = false

    @Synchronized
    fun update(newStrokes: List<Stroke>): ElementIndex {
        if (newStrokes === strokes) return index
        val appendOnly = newStrokes.size >= strokes.size && strokes.indices.all { newStrokes[it] === strokes[it] }
        if (!appendOnly) reset()
        replaying = !appendOnly || strokes.isEmpty()
        for (position in strokes.size until newStrokes.size) add(newStrokes[position], position)
        replaying = false
        if (needsRebuild) {
            val roots = if (rebuildAll) shapes.indices.mapTo(HashSet()) { find(it) } else rebuildMembers.mapTo(HashSet()) { find(it) }
            rebuildWithErasers(roots)
            needsRebuild = false
            rebuildAll = false
            rebuildMembers.clear()
        }
        strokes = newStrokes
        index = build()
        return index
    }

    private fun reset() {
        strokes = emptyList()
        shapes.clear()
        grid.clear()
        eraserOwner.clear()
        eraserIds.clear()
        fullErasers.clear()
        eraserGrid.clear()
        pairConnected.clear()
        needsRebuild = false
        rebuildAll = false
        rebuildMembers.clear()
    }

    private fun find(i: Int): Int {
        var x = i
        while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }
        return x
    }

    private fun cellsOf(rect: Rect, out: HashSet<Long>) {
        val x0 = kotlin.math.floor(rect.left / CELL).toInt()
        val x1 = kotlin.math.floor(rect.right / CELL).toInt()
        val y0 = kotlin.math.floor(rect.top / CELL).toInt()
        val y1 = kotlin.math.floor(rect.bottom / CELL).toInt()
        // Çok büyük tek bir parça ızgarayı şişirmesin
        if ((x1 - x0 + 1).toLong() * (y1 - y0 + 1) > 4096) { out += Long.MIN_VALUE; return }
        for (cx in x0..x1) for (cy in y0..y1) out += (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
    }

    private fun candidates(shape: ElementOps.StrokeShape): Set<Int> {
        val cells = HashSet<Long>()
        shape.chunkBounds.forEach { cellsOf(it.inflate(1f), cells) }
        if (Long.MIN_VALUE in cells) return shapes.indices.toSet()
        val result = HashSet<Int>()
        cells.forEach { grid[it]?.let(result::addAll) }
        grid[Long.MIN_VALUE]?.let(result::addAll)
        return result
    }

    private fun add(stroke: Stroke, position: Int) {
        if (stroke.points.isEmpty()) return
        val shape = ElementOps.StrokeShape(stroke, position)
        if (stroke.brushType == BrushType.ERASER) {
            // En çok üzerinden geçtiği, kendisinden önce çizilmiş elemana bağlanır
            val tally = HashMap<Int, Int>()
            candidates(shape).forEach { s ->
                val other = shapes[s]
                // Silgi yalnızca kendi katmanındaki fırça izlerine bağlanır (nesneleri silmez)
                if (!other.isImage && other.layer == shape.layer && other.expandedBounds.overlaps(shape.expandedBounds)) {
                    val touching = shape.pointsTouching(other)
                    if (touching > 0) find(s).let { k -> tally[k] = (tally[k] ?: 0) + touching }
                }
            }
            tally.maxByOrNull { it.value }?.let { eraserOwner[stroke.id] = it.key }
            eraserIds += stroke.id
            if (stroke.flow >= 0.99f && stroke.opacity >= 0.99f) {
                val e = fullErasers.size
                fullErasers += shape
                val cells = HashSet<Long>()
                shape.chunkBounds.forEach { cellsOf(it.inflate(1f), cells) }
                cells.forEach { eraserGrid.getOrPut(it) { ArrayList() } += e }
                if (replaying) {
                    needsRebuild = true
                    rebuildAll = true
                } else {
                    // Yalnızca silginin çevresine bakılır; bir bağ koptuysa o bileşen sonda yeniden kurulur
                    val cut = cutMembers(shape)
                    if (cut.isNotEmpty()) {
                        needsRebuild = true
                        rebuildMembers.addAll(cut)
                    }
                }
            }
            return
        }
        val i = shapes.size
        if (i >= parent.size) parent = parent.copyOf(parent.size * 2)
        parent[i] = i
        shapes += shape
        if (!shape.isImage) {
            // Kendisinden önceki silgiler yeni çizimi etkilemez: değen her çizimle birleşir
            candidates(shape).sorted().forEach { j ->
                val other = shapes[j]
                if (j != i && !other.isImage && other.layer == shape.layer && find(i) != find(j) && shape.touches(other)) parent[find(j)] = find(i)
            }
        }
        val cells = HashSet<Long>()
        shape.chunkBounds.forEach { cellsOf(it, cells) }
        cells.forEach { grid.getOrPut(it) { ArrayList() } += i }
    }

    /** Değme noktalarından en az biri [erasers] tarafından silinmeden kalıyor mu? */
    private fun survive(contacts: FloatArray, erasers: List<ElementOps.StrokeShape>): Boolean {
        var k = 0
        while (k < contacts.size) {
            val p = Offset(contacts[k], contacts[k + 1])
            val r = contacts[k + 2]
            if (erasers.none { e -> ElementOps.distanceToPolyline(p, e.stroke.points) + r <= e.half }) return true
            k += 3
        }
        return false
    }

    /** İki çizimin ortak bölgesine değen, ikisinden de sonra çizilmiş tam silgiler ([upTo] konumuna kadar). */
    private fun blockers(a: ElementOps.StrokeShape, b: ElementOps.StrokeShape, upTo: Int): List<ElementOps.StrokeShape> {
        if (fullErasers.isEmpty()) return emptyList()
        val after = max(a.position, b.position)
        val region = a.expandedBounds.intersect(b.expandedBounds)
        if (region.width < 0f || region.height < 0f) return emptyList()
        val cells = HashSet<Long>()
        cellsOf(region.inflate(1f), cells)
        val found = HashSet<Int>()
        if (Long.MIN_VALUE in cells) fullErasers.indices.forEach { found += it } else cells.forEach { eraserGrid[it]?.let(found::addAll) }
        eraserGrid[Long.MIN_VALUE]?.let(found::addAll)
        return found.sorted().map { fullErasers[it] }.filter { it.position in (after + 1)..upTo && it.expandedBounds.overlaps(region) }
    }

    /** [a] ile [b] arasında, kendilerinden sonra çizilmiş tam silgilerce silinmemiş bir değme var mı? */
    private fun pairKey(i: Int, j: Int): Long = (min(i, j).toLong() shl 32) or max(i, j).toLong()

    /** Değme noktaları hep küçük sıralı çizim boyunca örneklenir (sonuç çağrı sırasına bağlı olmasın). */
    private fun connected(i: Int, j: Int): Boolean =
        pairConnected.getOrPut(pairKey(i, j)) { computeConnected(shapes[min(i, j)], shapes[max(i, j)]) }

    private fun computeConnected(a: ElementOps.StrokeShape, b: ElementOps.StrokeShape): Boolean {
        val blockers = blockers(a, b, Int.MAX_VALUE)
        if (blockers.isEmpty()) return a.touches(b)
        if (!a.touches(b)) return false
        val contacts = a.contacts(b)
        return contacts.isNotEmpty() && survive(contacts, blockers)
    }

    /**
     * Yeni tam silgi, altında kalan iki çizimin son değme yerini de siliyorsa bu çizimlerin bileşen kökleri
     * (yalnızca silginin çevresine bakılır).
     */
    private fun cutMembers(eraser: ElementOps.StrokeShape): Set<Int> {
        val under = candidates(eraser).filter {
            !shapes[it].isImage && shapes[it].layer == eraser.layer && shapes[it].expandedBounds.overlaps(eraser.expandedBounds)
        }.sorted()
        val roots = HashSet<Int>()
        for (x in under.indices) for (y in x + 1 until under.size) {
            val i = under[x]; val j = under[y]
            // Farklı bileşendeki çiftler zaten bağlı değil (bağlı olsalardı aynı bileşende olurlardı)
            if (find(i) != find(j)) continue
            val a = shapes[i]; val b = shapes[j]
            if (!a.expandedBounds.overlaps(b.expandedBounds) || !a.touches(b)) continue
            val key = pairKey(i, j)
            if (pairConnected[key] == false) continue
            val all = blockers(a, b, eraser.position)
            if (all.none { it === eraser }) continue // silgi bu çiftin ortak bölgesine uzanmıyor
            val contacts = a.contacts(b)
            if (contacts.isEmpty()) { pairConnected[key] = false; roots += i; continue }
            val earlier = all.filter { it !== eraser }
            if (!survive(contacts, earlier)) { pairConnected[key] = false; continue } // bağ zaten kopmuştu
            val still = survive(contacts, all)
            pairConnected[key] = still
            if (!still) roots += i
        }
        return roots
    }

    /** Verilen köklerin bileşenlerini tam silgileri hesaba katarak yeniden kurar. */
    private fun rebuildWithErasers(roots: Set<Int>) {
        val members = shapes.indices.filter { find(it) in roots }
        val memberSet: Set<Int>? = members.toHashSet()
        for (i in members) parent[i] = i
        for (i in members) {
            val shape = shapes[i]
            if (shape.isImage) continue
            candidates(shape).sorted().forEach { j ->
                if (j >= i || (memberSet != null && j !in memberSet)) return@forEach
                val other = shapes[j]
                if (other.isImage || other.layer != shape.layer || find(i) == find(j)) return@forEach
                if (connected(i, j)) parent[find(j)] = find(i)
            }
        }
    }

    private fun build(): ElementIndex {
        if (shapes.isEmpty()) return ElementIndex(strokes, emptyList(), emptyMap())
        val members = LinkedHashMap<Int, ArrayList<Int>>()
        for (i in shapes.indices) members.getOrPut(find(i)) { ArrayList() } += i
        val components = members.values.sortedBy { shapes[it.first()].position }
        val componentOfRoot = HashMap<Int, Int>()
        components.forEachIndexed { c, list -> componentOfRoot[find(list.first())] = c }
        val erasersOf = Array(components.size) { ArrayList<String>() }
        eraserIds.forEach { id -> eraserOwner[id]?.let { owner -> componentOfRoot[find(owner)]?.let { erasersOf[it] += id } } }
        val elements = components.mapIndexed { c, list ->
            val memberStrokes = list.map { shapes[it].stroke }
            val first = memberStrokes.first()
            Element(
                id = first.id,
                number = c + 1,
                strokeIds = memberStrokes.map { it.id },
                eraserIds = erasersOf[c],
                groupId = memberStrokes.firstNotNullOfOrNull { it.groupId },
                brushType = first.brushType,
                colorHex = first.colorHex,
                hidden = first.hidden,
            )
        }
        val groupNumbers = elements.mapNotNull { it.groupId }.distinct().withIndex().associate { it.value to it.index + 1 }
        return ElementIndex(strokes, elements, groupNumbers)
    }

    private companion object {
        const val CELL = 96f
    }
}

/** v21 renge göre değişen özel dolgular (3B cisim tonları, dolgusuz dış hat) boyamayla silinmez. */
private fun keepsFill(hex: String): Boolean =
    com.karapp.drawguidance.ui.drawing.ShapeRenderer.isShade(hex) || hex == com.karapp.drawguidance.ui.drawing.ShapeRenderer.NO_FILL
