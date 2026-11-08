package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.ShapeFactory.EditablePath
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Vektör şekil düzenleme: noktaları artırma (kenara nokta ekleme, tüm kenarları bölme, noktayı taşıma),
 * şeklin üstünde görünen parçalama (kopan yerde küçük bir boşluk kalır) ve iki noktayı doğrudan bağlama.
 * Hepsi saf işlemlerdir: şekil tek eleman olarak kalır, girdi değişmez.
 */
object ShapeEditor {

    /** Tüm noktalarını tutamak olarak gösterecek kadar az noktalı yol (çokgenler, bölünmüş kenarlar). */
    private const val SHOW_ALL_VERTICES = 48
    private const val MAX_HANDLES = 160
    /** Kenar bölmede toplam nokta sınırı (çok büyük şekillerde işlem yapılmaz). */
    private const val MAX_POINTS = 4000

    /** Tutamak: konumu ve açık uç (parçalama sonucu kopuk uç) olup olmadığı. */
    data class Handle(val point: Offset, val openEnd: Boolean)

    /** Dokunulan kenara (en yakın parçaya) yeni bir nokta ekler. */
    fun insertPoint(stroke: Stroke, at: Offset): Stroke? {
        val paths = ShapeFactory.subPaths(stroke)
        val hit = nearestSegment(paths, at) ?: return null
        val path = paths[hit.path]
        val pts = path.points.toMutableList()
        pts.add((hit.segment + 1).coerceAtMost(pts.size), hit.point)
        return rebuild(stroke, paths, hit.path, listOf(path.copy(points = pts, nodes = null)))
    }

    /** Tüm kenarların ortasına nokta ekler: şeklin nokta sayısı iki katına çıkar. */
    fun subdivide(stroke: Stroke): Stroke? {
        if (stroke.points.size * 2 > MAX_POINTS) return null
        val paths = ShapeFactory.subPaths(stroke).map { path ->
            if (path.label != null || path.points.size < 2) return@map path
            val src = path.points
            val out = ArrayList<Offset>(src.size * 2)
            for (i in src.indices) {
                out += src[i]
                val next = if (i + 1 < src.size) src[i + 1] else if (path.closed) src[0] else null
                if (next != null) out += Offset((src[i].x + next.x) / 2f, (src[i].y + next.y) / 2f)
            }
            path.copy(points = out, nodes = null)
        }
        return ShapeFactory.fromEditable(stroke, paths)
    }

    /** [from] noktasına [tolerance] içindeki tutamağı [to] konumuna taşır. */
    fun movePoint(stroke: Stroke, from: Offset, to: Offset, tolerance: Float): Stroke? {
        val paths = ShapeFactory.subPaths(stroke)
        val v = nearestVertex(paths, from, tolerance) ?: return null
        val updated = paths.mapIndexed { i, p ->
            if (i != v.path) p else p.copy(points = p.points.mapIndexed { k, q -> if (k == v.index) to else q })
        }
        return ShapeFactory.fromEditable(stroke, updated)
    }

    /**
     * Şekli dokunulan yerden parçalar; şekil tek eleman kalır, kopan yerde [gap] kadar boşluk bırakılır ki
     * parçalanma şeklin üstünde görünsün. Kapalı yol açılır, açık yol iki parçaya ayrılır.
     */
    fun split(stroke: Stroke, at: Offset, gap: Float): Stroke? {
        val paths = ShapeFactory.subPaths(stroke)
        val hit = nearestSegment(paths, at) ?: return null
        val path = paths[hit.path]
        val pts = path.points
        val cut = hit.point
        fun toward(target: Offset): Offset {
            val d = target - cut
            val len = d.getDistance()
            if (len < 1e-3f) return cut
            return cut + d / len * min(gap, len * 0.45f)
        }
        val replacement = ArrayList<EditablePath>()
        if (path.closed) {
            val n = pts.size
            val next = pts[(hit.segment + 1) % n]
            val prev = pts[hit.segment]
            val rotated = (1..n).map { pts[(hit.segment + it) % n] }
            replacement += path.copy(points = listOf(toward(next)) + rotated + toward(prev), closed = false, nodes = null)
        } else {
            val prev = pts[hit.segment]
            val next = pts[hit.segment + 1]
            val a = pts.subList(0, hit.segment + 1) + toward(prev)
            val b = listOf(toward(next)) + pts.subList(hit.segment + 1, pts.size)
            if (a.size >= 2) replacement += path.copy(points = a, nodes = null)
            if (b.size >= 2) replacement += path.copy(points = b, nodes = null)
        }
        return rebuild(stroke, paths, hit.path, replacement)
    }

    /**
     * İki noktayı doğrudan bağlar. İkisi de kopuk uçsa yollar birleşir (aynı yolun uçlarıysa yol kapanır);
     * değilse iki nokta arasına düz bir kenar eklenir.
     */
    fun connect(stroke: Stroke, a: Offset, b: Offset, tolerance: Float): Stroke? {
        val paths = ShapeFactory.subPaths(stroke)
        val va = nearestVertex(paths, a, tolerance) ?: return null
        val vb = nearestVertex(paths, b, tolerance, exclude = va) ?: return null
        val pa = paths[va.path].points[va.index]
        val pb = paths[vb.path].points[vb.index]
        if (va.openEnd && vb.openEnd) {
            val mutable = paths.toMutableList()
            if (va.path == vb.path) {
                mutable[va.path] = mutable[va.path].copy(closed = true)
                return ShapeFactory.fromEditable(stroke, mutable)
            }
            val first = paths[va.path].let { if (va.index == 0) it.copy(points = it.points.reversed()) else it }
            val second = paths[vb.path].let { if (vb.index != 0) it.copy(points = it.points.reversed()) else it }
            val merged = first.copy(points = first.points + second.points, nodes = null)
            val out = ArrayList<EditablePath>()
            paths.forEachIndexed { i, p -> if (i != va.path && i != vb.path) out += p }
            out.add(min(va.path, vb.path).coerceAtMost(out.size), merged)
            return ShapeFactory.fromEditable(stroke, out)
        }
        return ShapeFactory.fromEditable(stroke, paths + EditablePath(listOf(pa, pb)))
    }

    /** Düzenlemede gösterilecek tutamaklar: köşeler (az noktalı yollarda tüm noktalar) ve kopuk uçlar. */
    fun handles(stroke: Stroke): List<Handle> {
        val out = ArrayList<Handle>()
        for (path in ShapeFactory.subPaths(stroke)) {
            if (path.label != null) continue
            val pts = path.points
            if (pts.size < 2) continue
            if (!path.closed) {
                out += Handle(pts.first(), true)
                out += Handle(pts.last(), true)
            }
            val all = pts.size <= SHOW_ALL_VERTICES
            val range = if (path.closed) pts.indices else 1 until pts.size - 1
            for (i in range) {
                val b = pts[i]
                if (all) { out += Handle(b, false); continue }
                val a = pts[(i - 1 + pts.size) % pts.size]
                val c = pts[(i + 1) % pts.size]
                val v1 = Offset(a.x - b.x, a.y - b.y)
                val v2 = Offset(c.x - b.x, c.y - b.y)
                val l1 = hypot(v1.x, v1.y); val l2 = hypot(v2.x, v2.y)
                if (l1 < 1e-3f || l2 < 1e-3f) continue
                val cos = (v1.x * v2.x + v1.y * v2.y) / (l1 * l2)
                if (cos > -0.9f) out += Handle(b, false)
            }
        }
        return out.take(MAX_HANDLES)
    }

    /** Şeklin noktalarına en yakın mesafe (düzenleme dokunuşlarında kullanılır). */
    fun distanceToPaths(stroke: Stroke, at: Offset): Float =
        nearestSegment(ShapeFactory.subPaths(stroke), at)?.distance ?: Float.MAX_VALUE

    // --- Yardımcılar ---

    private fun rebuild(stroke: Stroke, paths: List<EditablePath>, index: Int, replacement: List<EditablePath>): Stroke? {
        val out = ArrayList<EditablePath>(paths.size + replacement.size)
        paths.forEachIndexed { i, p -> if (i == index) out += replacement else out += p }
        return ShapeFactory.fromEditable(stroke, out)
    }

    private class SegmentHit(val path: Int, val segment: Int, val point: Offset, val distance: Float)

    private fun nearestSegment(paths: List<EditablePath>, at: Offset): SegmentHit? {
        var best: SegmentHit? = null
        paths.forEachIndexed { pi, path ->
            if (path.label != null) return@forEachIndexed
            val pts = if (path.closed) path.points + path.points.first() else path.points
            for (i in 0 until pts.size - 1) {
                val p = project(at, pts[i], pts[i + 1])
                val d = (p - at).getDistance()
                if (best == null || d < best!!.distance) best = SegmentHit(pi, i, p, d)
            }
        }
        return best
    }

    private class VertexHit(val path: Int, val index: Int, val openEnd: Boolean, val distance: Float)

    private fun nearestVertex(paths: List<EditablePath>, at: Offset, tolerance: Float, exclude: VertexHit? = null): VertexHit? {
        var best: VertexHit? = null
        paths.forEachIndexed { pi, path ->
            if (path.label != null || path.points.size < 2) return@forEachIndexed
            path.points.forEachIndexed { k, p ->
                if (exclude != null && exclude.path == pi && exclude.index == k) return@forEachIndexed
                val d = (p - at).getDistance()
                if (d > tolerance) return@forEachIndexed
                val open = !path.closed && (k == 0 || k == path.points.size - 1)
                // Kopuk uçlar, eşit uzaklıkta öncelikli
                val score = d - if (open) tolerance * 0.25f else 0f
                val bestScore = best?.let { it.distance - if (it.openEnd) tolerance * 0.25f else 0f } ?: Float.MAX_VALUE
                if (score < bestScore) best = VertexHit(pi, k, open, d)
            }
        }
        return best
    }

    private fun project(p: Offset, a: Offset, b: Offset): Offset {
        val vx = b.x - a.x
        val vy = b.y - a.y
        val len2 = vx * vx + vy * vy
        if (len2 < 1e-6f) return a
        val t = (((p.x - a.x) * vx + (p.y - a.y) * vy) / len2).coerceIn(0f, 1f)
        return Offset(a.x + vx * t, a.y + vy * t)
    }

    // --- v13: genel kullanıcı için şekil düzenleme (parçalar, çizgi/eğri ekleme, boyama, dönüştürme) ---

    /**
     * Dokunulan parça (alt yol): çizgisine [tolerance] içinde en yakın parça; yoksa içine dokunulan dolu
     * bölge (en küçük alanlı). Yazı etiketleri de parçadır.
     */
    fun partAt(stroke: Stroke, at: Offset, tolerance: Float): Int? {
        val paths = ShapeFactory.subPaths(stroke)
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        paths.forEachIndexed { i, path ->
            val d = if (path.label != null) {
                val p = path.points.firstOrNull() ?: return@forEachIndexed
                val size = max(8f, path.labelSize)
                val box = androidx.compose.ui.geometry.Rect(p.x - 2f, p.y - 2f, p.x + size * max(1, path.label.length) * 0.6f, p.y + size * 1.2f)
                if (box.contains(at)) 0f else Float.MAX_VALUE
            } else distanceToPath(path, at)
            if (d < bestDistance) { bestDistance = d; best = i }
        }
        if (best != null && bestDistance <= tolerance) return best
        // Bölgenin içi
        val style = stroke.shape?.style
        var inside: Int? = null
        var insideArea = Float.MAX_VALUE
        paths.forEachIndexed { i, path ->
            if (path.label != null || path.points.size < 3) return@forEachIndexed
            val closedEnough = path.closed || fillsOpen(path, style?.strokeWidth ?: 4f)
            if (!closedEnough || !contains(path.points, at)) return@forEachIndexed
            val area = abs(area(path.points))
            if (area < insideArea) { insideArea = area; inside = i }
        }
        return inside
    }

    /** İçine dokunulan dolu (kapalı) bölge; yoksa null. */
    fun regionAt(stroke: Stroke, at: Offset): Int? {
        val paths = ShapeFactory.subPaths(stroke)
        val width = stroke.shape?.style?.strokeWidth ?: 4f
        var inside: Int? = null
        var insideArea = Float.MAX_VALUE
        paths.forEachIndexed { i, path ->
            if (path.label != null || path.points.size < 3) return@forEachIndexed
            if (!(path.closed || fillsOpen(path, width)) || !contains(path.points, at)) return@forEachIndexed
            val area = abs(area(path.points))
            if (area < insideArea) { insideArea = area; inside = i }
        }
        return inside
    }

    /** Çizgisine [tolerance] içinde en yakın parça. */
    fun lineAt(stroke: Stroke, at: Offset, tolerance: Float): Int? {
        val paths = ShapeFactory.subPaths(stroke)
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        paths.forEachIndexed { i, path ->
            if (path.label != null) return@forEachIndexed
            val d = distanceToPath(path, at)
            if (d < bestDistance) { bestDistance = d; best = i }
        }
        return best?.takeIf { bestDistance <= tolerance }
    }

    private fun fillsOpen(path: EditablePath, width: Float): Boolean {
        val pts = path.points
        if (pts.size < 3 || path.dashed) return false
        val gap = (pts.first() - pts.last()).getDistance()
        return gap <= max(14f, width * 6f)
    }

    private fun distanceToPath(path: EditablePath, at: Offset): Float {
        val pts = if (path.closed && path.points.size > 2) path.points + path.points.first() else path.points
        if (pts.size == 1) return (pts[0] - at).getDistance()
        var best = Float.MAX_VALUE
        for (i in 0 until pts.size - 1) best = min(best, (project(at, pts[i], pts[i + 1]) - at).getDistance())
        return best
    }

    private fun contains(pts: List<Offset>, p: Offset): Boolean {
        var inside = false
        var j = pts.size - 1
        for (i in pts.indices) {
            val a = pts[i]; val b = pts[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y + 1e-6f) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    private fun area(pts: List<Offset>): Float {
        var a = 0f
        for (i in pts.indices) {
            val p = pts[i]; val q = pts[(i + 1) % pts.size]
            a += p.x * q.y - q.x * p.y
        }
        return a / 2f
    }

    /** Seçili parçaların sınır kutusu. */
    fun partsBounds(stroke: Stroke, parts: Set<Int>): androidx.compose.ui.geometry.Rect? {
        val paths = ShapeFactory.subPaths(stroke)
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        var any = false
        parts.forEach { i ->
            paths.getOrNull(i)?.points?.forEach { p ->
                any = true
                l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y)
            }
        }
        return if (any) androidx.compose.ui.geometry.Rect(l, t, r, b) else null
    }

    /** Seçili parçaların noktalarını dönüştürür (şekil tek eleman kalır). */
    fun transformParts(stroke: Stroke, parts: Set<Int>, transform: (Offset) -> Offset): Stroke? {
        if (parts.isEmpty()) return null
        val paths = ShapeFactory.subPaths(stroke).mapIndexed { i, p ->
            if (i in parts) p.copy(points = p.points.map(transform)) else p
        }
        return ShapeFactory.fromEditable(stroke, paths)
    }

    /** Seçili parçaları [dx],[dy] kadar taşır. */
    fun moveParts(stroke: Stroke, parts: Set<Int>, dx: Float, dy: Float): Stroke? =
        transformParts(stroke, parts) { Offset(it.x + dx, it.y + dy) }

    /** Seçili parçaları [pivot] çevresinde [degrees] kadar döndürür. */
    fun rotateParts(stroke: Stroke, parts: Set<Int>, degrees: Float, pivot: Offset): Stroke? {
        val rad = Math.toRadians(degrees.toDouble())
        val c = kotlin.math.cos(rad).toFloat(); val sn = kotlin.math.sin(rad).toFloat()
        return transformParts(stroke, parts) { p ->
            val x = p.x - pivot.x; val y = p.y - pivot.y
            Offset(pivot.x + x * c - y * sn, pivot.y + x * sn + y * c)
        }
    }

    /** Şekle yeni bir çizgi (düz ya da eğri) parçası ekler. */
    fun addPath(stroke: Stroke, points: List<Offset>): Stroke? {
        if (points.size < 2) return null
        return ShapeFactory.fromEditable(stroke, ShapeFactory.subPaths(stroke) + EditablePath(points))
    }

    /** Seçili parçaları siler; hiçbir parça kalmazsa null. */
    fun removeParts(stroke: Stroke, parts: Set<Int>): Stroke? {
        val remaining = ShapeFactory.subPaths(stroke).filterIndexed { i, _ -> i !in parts }
        if (remaining.none { it.label == null }) return null
        return ShapeFactory.fromEditable(stroke, remaining)
    }

    /**
     * Şekli seçilen parçalara göre ayrı elemanlara ayırır. [each] false (Ayır): seçilen parçalar birlikte tek yeni
     * eleman olur. [each] true (Parçala): seçilen her parça ayrı bir eleman olur. Kalan parçalar asıl elemanda
     * (kimliği aynı) kalır; hiç parça kalmazsa ilk yeni parça asıl kimliği alır. Yazı etiketleri kendi grubundaki
     * en yakın çizgi parçasıyla gider. Sonuç: önce kalan (varsa), sonra ayrılanlar; ayrılacak bir şey yoksa null.
     */
    fun separate(stroke: Stroke, parts: Set<Int>, each: Boolean, ids: () -> String): List<Stroke>? {
        val paths = ShapeFactory.subPaths(stroke)
        val chosen = parts.filter { it in paths.indices }.toSortedSet()
        val chosenLines = chosen.filter { paths[it].label == null }
        if (chosenLines.isEmpty()) return null
        val rest = paths.indices.filterNot { it in chosen }
        val restLines = rest.filter { paths[it].label == null }
        // Ayır'da tümü seçiliyse ayrılacak bir şey yok; Parçala'da en az iki parça çıkmalı
        if (!each && restLines.isEmpty()) return null
        if (each && restLines.isEmpty() && chosenLines.size < 2) return null
        fun nearest(label: Int, among: List<Int>): Int {
            val p = paths[label].points.firstOrNull() ?: return among.first()
            return among.minByOrNull { distanceToPath(paths[it], p) } ?: among.first()
        }
        val groups = ArrayList<List<Int>>()
        if (restLines.isNotEmpty()) groups += rest
        if (each) {
            val byLine = chosenLines.associateWith { mutableListOf(it) }
            chosen.filter { paths[it].label != null }.forEach { byLine.getValue(nearest(it, chosenLines)) += it }
            groups += byLine.values.map { it.sorted() }
        } else {
            groups += chosen.toList()
        }
        return groups.mapIndexedNotNull { g, indices ->
            val id = if (g == 0) stroke.id else ids()
            ShapeFactory.fromEditable(stroke, indices.map { paths[it] }, id = id)
        }
    }

    /** Parçanın çizgi rengini ([fill] ise bölgenin dolgusunu) verir; null varsayılana döndürür. */
    fun paintPart(stroke: Stroke, index: Int, colorHex: String?, fill: Boolean): Stroke? {
        val paths = ShapeFactory.subPaths(stroke)
        if (index !in paths.indices) return null
        val updated = paths.mapIndexed { i, p ->
            if (i != index) p else if (fill) p.copy(fillColorHex = colorHex) else p.copy(strokeColorHex = colorHex)
        }
        return ShapeFactory.fromEditable(stroke, updated)
    }

    /** Parmakla çizilen yolu yumuşak bir eğriye çevirir (gereksiz noktalar atılır, köşeler yuvarlanır). */
    fun smoothCurve(points: List<Offset>, tolerance: Float): List<Offset> {
        if (points.size < 3) return points
        val simplified = simplify(points, max(0.5f, tolerance * 0.35f))
        var pts = simplified
        repeat(3) {
            if (pts.size < 3) return@repeat
            val out = ArrayList<Offset>(pts.size * 2)
            out += pts.first()
            for (i in 0 until pts.size - 1) {
                val a = pts[i]; val b = pts[i + 1]
                out += Offset(a.x * 0.75f + b.x * 0.25f, a.y * 0.75f + b.y * 0.25f)
                out += Offset(a.x * 0.25f + b.x * 0.75f, a.y * 0.25f + b.y * 0.75f)
            }
            out += pts.last()
            pts = out
        }
        return if (pts.size > MAX_CURVE_POINTS) simplify(pts, tolerance * 0.08f) else pts
    }

    private const val MAX_CURVE_POINTS = 160

    /** Ramer–Douglas–Peucker sadeleştirme. */
    private fun simplify(points: List<Offset>, epsilon: Float): List<Offset> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true; keep[points.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.size - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var index = -1
            var maxD = 0f
            for (i in a + 1 until b) {
                val d = (project(points[i], points[a], points[b]) - points[i]).getDistance()
                if (d > maxD) { maxD = d; index = i }
            }
            if (index >= 0 && maxD > epsilon) {
                keep[index] = true
                stack.addLast(a to index); stack.addLast(index to b)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    /**
     * Şekli başka bir şekle dönüştürür: yeni şekil eskisinin kapladığı kutuya yerleşir, biçimi (renk,
     * kalınlık, dolgu) ve kimliği korunur.
     */
    fun convert(stroke: Stroke, kind: ShapeKind): Stroke? {
        val spec = stroke.shape ?: return null
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        stroke.points.forEach { l = min(l, it.x); t = min(t, it.y); r = max(r, it.x); b = max(b, it.y) }
        if (r - l < 1f && b - t < 1f) return null
        // Çok ince (doğru gibi) şekillerden kutu şekline geçerken kutu kareye yakın açılır
        val minSide = max(r - l, b - t) * 0.35f
        if (kind.fit != ShapeFit.LINE) {
            if (r - l < minSide) { val c = (l + r) / 2f; l = c - minSide / 2f; r = c + minSide / 2f }
            if (b - t < minSide) { val c = (t + b) / 2f; t = c - minSide / 2f; b = c + minSide / 2f }
        }
        val start = if (kind.fit == ShapeFit.LINE) Offset(l, (t + b) / 2f) else Offset(l, t)
        val end = if (kind.fit == ShapeFit.LINE) Offset(r, (t + b) / 2f) else Offset(r, b)
        val graph = if (kind in GraphShapes.kinds) spec.graph ?: com.karapp.drawguidance.data.model.GraphSpec() else null
        return ShapeFactory.build(kind, start, end, spec.style, graph, stroke.id, stroke.order, stroke.opacity)
            ?.copy(groupId = stroke.groupId, clip = stroke.clip)
    }

    /** Parçalamada bırakılan boşluk: kenar kalınlığına göre, görünür ama küçük. */
    fun splitGap(stroke: Stroke): Float = max(4f, (stroke.shape?.style?.strokeWidth ?: 4f) * 2.2f)
}
