package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.content.graph.CurvePresets
import com.karapp.drawguidance.content.graph.GraphKind
import com.karapp.drawguidance.content.graph.GraphParams
import com.karapp.drawguidance.content.graph.Graphs
import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.vector.LineStyle
import com.karapp.drawguidance.content.vector.VPath
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.GraphSpec
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Eklenebilir tüm şekillerin tek kaydı. Kimlik kayıtta (ShapeSpec.kind) saklanır:
 * - yerleşik şekil: [ShapeKind] adı ("CIRCLE", "CUBE"…)
 * - grafik: "graph.<GraphKind>" ("graph.AXES", "graph.BAR"…)
 * - grafik eğrisi: "curve.<hazır eğri>" ("curve.sinus", "curve.parabol"…)
 * - kütüphane (fen şekilleri, hazır çizimler): "lib.<kimlik>"
 * Uygulama şekil çizmek, yerleştirmek, dönüştürmek ve seçicide listelemek için yalnızca bu nesneyi kullanır; yeni
 * şekil eklemek kütüphaneye kayıt eklemektir (burada özel durum yazılmaz).
 */
object ShapeRegistry {

    const val GRAPH_PREFIX = "graph."
    const val CURVE_PREFIX = "curve."

    /** Kalem aracıyla çizilen ve vektörleştirmeden gelen serbest yollar (yeniden kurulmaz). */
    const val FREE_PATH = "PATH"
    const val TRACED = "TRACED"

    // v37 adla arama tablodan (önizlemede her karede ve her seçici kutusunda çağrılır)
    private val builtins = ShapeKind.entries.associateBy { it.name }

    fun builtin(id: String): ShapeKind? = builtins[id]

    private val legacyGraphs = mapOf(
        ShapeKind.AXES to GraphKind.AXES, ShapeKind.FUNCTION_PLOT to GraphKind.FUNCTION, ShapeKind.COORD_GRID to GraphKind.COORD_PLANE,
        ShapeKind.AXES_3D to GraphKind.AXES_3D, ShapeKind.UNIT_CIRCLE to GraphKind.UNIT_CIRCLE, ShapeKind.BAR_CHART to GraphKind.BAR,
        ShapeKind.LINE_CHART to GraphKind.LINE, ShapeKind.PIE_CHART to GraphKind.PIE, ShapeKind.SCATTER to GraphKind.SCATTER,
    )

    /** Eski grafik şekillerinin yeni grafik kimliği (düzenlenince yeni motorla yeniden kurulur). */
    fun upgradeGraphKind(id: String): String {
        val legacy = builtin(id)?.let { legacyGraphs[it] } ?: return id
        return GRAPH_PREFIX + legacy.name
    }

    fun graphKind(id: String): GraphKind? = when {
        id.startsWith(GRAPH_PREFIX) -> runCatching { GraphKind.valueOf(id.removePrefix(GRAPH_PREFIX)) }.getOrNull()
        id.startsWith(CURVE_PREFIX) -> GraphKind.FUNCTION
        else -> builtin(id)?.let { legacyGraphs[it] }
    }

    fun preset(id: String): String? = if (id.startsWith(CURVE_PREFIX)) id.removePrefix(CURVE_PREFIX) else null

    fun isGraph(id: String): Boolean = graphKind(id) != null

    /** Yeni motorla kurulan grafik mi (eski kayıtlar değil)? */
    fun isModernGraph(id: String): Boolean = id.startsWith(GRAPH_PREFIX) || id.startsWith(CURVE_PREFIX)

    fun isLibrary(id: String): Boolean = id.startsWith(ShapeLibrary.PREFIX)

    /** v45 formülle/değerlerle düzenlenen kütüphane şeklinin modeli (yoksa null). */
    fun paramModel(id: String): com.karapp.drawguidance.content.library.science.ParamModel? =
        if (isLibrary(id)) ShapeLibrary.find(id)?.param else null

    /** Kimlikten yeniden kurulabilir mi (serbest yollar ve desenler kendi verileriyle saklanır). */
    fun canBuild(id: String): Boolean = builtin(id) != null || isModernGraph(id) || (isLibrary(id) && ShapeLibrary.find(id) != null)

    /** Parmakla yerleştirme biçimi. */
    fun fit(id: String): ShapeFit {
        builtin(id)?.let { return it.fit }
        if (isModernGraph(id)) return if (graphKind(id) == GraphKind.NUMBER_LINE) ShapeFit.LINE else ShapeFit.BOX
        val lib = ShapeLibrary.find(id) ?: return ShapeFit.BOX
        return when (lib.fit) {
            Fit.LINE -> ShapeFit.LINE
            Fit.SQUARE -> ShapeFit.SQUARE
            Fit.BOX -> ShapeFit.BOX
        }
    }

    /** Dokunarak eklemede ve kütüphane şekillerinde korunan en/boy oranı. */
    fun aspect(id: String): Float {
        builtin(id)?.let { return if (it.fit == ShapeFit.SQUARE) 1f else 1.3f }
        if (isModernGraph(id)) return when (graphKind(id)) {
            GraphKind.PIE, GraphKind.UNIT_CIRCLE, GraphKind.AXES_3D -> 1f
            GraphKind.NUMBER_LINE -> 5f
            else -> 1.3f
        }
        return ShapeLibrary.find(id)?.aspect?.coerceIn(0.2f, 6f) ?: 1f
    }

    /** Kütüphane şekilleri sürüklenirken oranını korur (ayrıntılı çizimler bozulmasın). */
    private fun keepsAspect(id: String) = isLibrary(id) && fit(id) != ShapeFit.LINE &&
        // v42 genel şekiller (akış şeması, afiş, konuşma kutusu...) sürüklenen kutuya serbestçe yerleşir
        !com.karapp.drawguidance.content.library.general.GeneralShapes.isGeneral(id)

    /** Sürüklenen iki noktadan şeklin kutusu. */
    fun frame(id: String, start: Offset, end: Offset): Rect {
        builtin(id)?.let { return ShapeCatalog.frame(it, start, end) }
        if (!keepsAspect(id) && fit(id) != ShapeFit.SQUARE) {
            return Rect(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
        }
        val aspect = if (fit(id) == ShapeFit.SQUARE) 1f else aspect(id)
        val dx = end.x - start.x
        val dy = end.y - start.y
        val w = max(abs(dx), abs(dy) * aspect)
        val h = w / aspect
        val ex = start.x + if (dx < 0) -w else w
        val ey = start.y + if (dy < 0) -h else h
        return Rect(min(start.x, ex), min(start.y, ey), max(start.x, ex), max(start.y, ey))
    }

    /** Yeni grafiğin varsayılan ayarları (hazır eğrinin tekrar sayısı). */
    fun defaultGraph(id: String): GraphSpec {
        val kind = graphKind(id) ?: return GraphSpec()
        val preset = preset(id)?.let { CurvePresets.find(it) }
        // Hazır eğrisi olmayan fonksiyon grafiği bir örnek formülle başlar (grafikteki formüle dokunarak değiştirilir)
        val formula = if (kind == GraphKind.FUNCTION && preset == null) "x^2 - 2" else ""
        return GraphSpec(kind = kind.name, preset = preset?.id ?: "", repeat = preset?.defaultRepeat ?: 2, formula = formula)
    }

    fun params(id: String, g: GraphSpec): GraphParams = GraphParams(
        kind = graphKind(id) ?: runCatching { GraphKind.valueOf(g.kind) }.getOrDefault(GraphKind.AXES),
        // Kullanıcı formül yazdıysa (hazır eğri temizlendiyse) hazır eğri yerine formül çizilir
        preset = if (g.formula.isNotBlank() && g.preset.isEmpty()) "" else preset(id) ?: g.preset,
        formula = g.formula,
        xLabel = g.xLabel, yLabel = g.yLabel, zLabel = g.zLabel,
        repeat = g.repeat,
        ticks = g.ticks,
        showNumbers = g.showNumbers,
        grid = g.grid,
        tickLabels = g.tickLabels,
        values = g.values,
        valueLabels = g.valueLabels,
        curveColor = g.curveColor,
        fillUnder = g.fillUnder,
        keyPoints = g.keyPoints,
        xMin = if (g.rangeSet) g.xMin else Float.NaN,
        xMax = if (g.rangeSet) g.xMax else Float.NaN,
        freq = g.freq.coerceAtLeast(1),
        paper = g.paper.ifEmpty { "#FFFFFF" },
        angleDeg = g.angleDeg,
    )

    fun spec(p: GraphParams, base: GraphSpec): GraphSpec = base.copy(
        kind = p.kind.name, preset = p.preset, formula = p.formula,
        xLabel = p.xLabel, yLabel = p.yLabel, zLabel = p.zLabel,
        repeat = p.repeat, ticks = p.ticks, showNumbers = p.showNumbers, grid = p.grid,
        tickLabels = p.tickLabels, values = p.values, valueLabels = p.valueLabels,
        curveColor = p.curveColor, fillUnder = p.fillUnder, keyPoints = p.keyPoints,
        freq = p.freq, paper = if (p.paper.equals("#FFFFFF", ignoreCase = true)) "" else p.paper,
        angleDeg = p.angleDeg,
    )

    /**
     * Şeklin çizgileri (tuval koordinatlarında). Kesik çizgiler parça olarak kalır (çizimde kesik gösterilir).
     */
    fun paths(id: String, start: Offset, end: Offset, ink: Float, graph: GraphSpec? = null, params: Map<String, String>? = null): List<ShapePath> {
        builtin(id)?.let { kind ->
            if (legacyGraphs.containsKey(kind) || kind == ShapeKind.NUMBER_LINE) {
                // Eski kayıttaki grafikler (yalnızca yeniden kurulurken): eski görünüm korunur
                return ShapeCatalog.paths(kind, start, end, ink, expandDashes = false, graph = graph)
            }
            return ShapeCatalog.paths(kind, start, end, ink, expandDashes = false, graph = graph)
        }
        if (isModernGraph(id)) {
            val rect = frame(id, start, end)
            if (fit(id) == ShapeFit.LINE) {
                return lineMapped(start, end, aspect(id)) { res ->
                    Graphs.build(params(id, graph ?: defaultGraph(id)), aspect(id), res)
                }
            }
            val aspect = max(0.05f, rect.width) / max(0.05f, rect.height)
            val built = Graphs.build(params(id, graph ?: defaultGraph(id)), aspect, max(rect.width, rect.height))
            return mapped(built, rect)
        }
        val lib = ShapeLibrary.find(id) ?: return emptyList()
        if (lib.fit == Fit.LINE) return lineMapped(start, end, lib.aspect) { res -> lib.paths(res, params) }
        val rect = frame(id, start, end)
        // v45 parametreli şekil (fizik, geometri, molekül): kayıttaki değerlerle çizilir
        return mapped(lib.paths(max(rect.width, rect.height).coerceAtLeast(64f), params), rect)
    }

    /** Birim kutudaki parçaları dikdörtgene eşler. */
    fun mapped(paths: List<VPath>, rect: Rect): List<ShapePath> {
        val w = max(rect.width, 1e-3f)
        val h = max(rect.height, 1e-3f)
        val m = min(w, h)
        return paths.map { v -> v.toShapePath(m) { p -> Offset(rect.left + p.x * w, rect.top + p.y * h) } }
    }

    /** Çizgi gibi yerleşen şekiller: birim kutunun x ekseni başlangıçtan bitişe, y ekseni ona dik (orta çizgi). */
    private fun lineMapped(start: Offset, end: Offset, aspect: Float, build: (Float) -> List<VPath>): List<ShapePath> {
        val length = max(1e-3f, hypot(end.x - start.x, end.y - start.y))
        val ux = (end.x - start.x) / length
        val uy = (end.y - start.y) / length
        val nx = -uy
        val ny = ux
        val height = length / aspect.coerceAtLeast(0.2f)
        return build(length).map { v ->
            v.toShapePath(height) { p ->
                val a = p.x * length
                val b = (p.y - 0.5f) * height
                Offset(start.x + ux * a + nx * b, start.y + uy * a + ny * b)
            }
        }
    }

    private fun VPath.toShapePath(minSide: Float, map: (com.karapp.drawguidance.content.vector.P) -> Offset): ShapePath {
        val pts = points.map(map)
        return ShapePath(
            points = if (closed && label == null && pts.size > 2) pts + pts.first() else pts,
            closed = closed,
            dashed = line == LineStyle.DASHED,
            label = label,
            labelSize = labelSize * minSide,
            strokeColorHex = stroke,
            fillColorHex = fill,
            widthScale = width.takeIf { it != 1f },
            dotted = line == LineStyle.DOTTED,
            alpha = alpha.takeIf { it < 0.999f },
            tag = tag,
        )
    }

    // --- Seçicideki kategoriler ---

    /** Seçicideki bir kategori: başlık kaynağı ya da düz metin ve şekil kimlikleri. */
    class PickerCategory(
        val id: String,
        /** Yerleşik kategorilerin başlığı (string kaynağı); kütüphane/grafik kategorilerinde null. */
        val builtin: ShapeCategory? = null,
        val title: String = "",
        val section: String,
        val items: List<String>,
    )

    /** Grafikler (eksen, düzlem, sayı doğrusu, birim çember, 3B, grafikler). */
    val graphItems: List<String> = listOf(
        GraphKind.AXES, GraphKind.COORD_PLANE, GraphKind.NUMBER_LINE, GraphKind.UNIT_CIRCLE, GraphKind.AXES_3D,
        GraphKind.BAR, GraphKind.LINE, GraphKind.PIE, GraphKind.SCATTER, GraphKind.HISTOGRAM,
    ).map { GRAPH_PREFIX + it.name }

    /** Grafik eğrileri: formülü kullanıcının yazdığı fonksiyon grafiği ve hazır fonksiyonlar. */
    val curveItems: List<String> get() = listOf(GRAPH_PREFIX + GraphKind.FUNCTION.name) + CurvePresets.all.map { CURVE_PREFIX + it.id }

    /** Grafik ve kütüphane şekillerinin adı (yerleşikler için string kaynağı kullanılır). */
    fun title(id: String): String? = when {
        id.startsWith(CURVE_PREFIX) -> CurvePresets.find(id.removePrefix(CURVE_PREFIX))?.title
        id.startsWith(GRAPH_PREFIX) -> when (graphKind(id)) {
            GraphKind.AXES -> "Koordinat ekseni"
            GraphKind.COORD_PLANE -> "Koordinat düzlemi"
            GraphKind.NUMBER_LINE -> "Sayı doğrusu"
            GraphKind.UNIT_CIRCLE -> "Birim çember"
            GraphKind.AXES_3D -> "3B eksen"
            GraphKind.FUNCTION -> "Fonksiyon grafiği"
            GraphKind.BAR -> "Sütun grafiği"
            GraphKind.LINE -> "Çizgi grafiği"
            GraphKind.PIE -> "Pasta grafiği"
            GraphKind.SCATTER -> "Serpilme grafiği"
            GraphKind.HISTOGRAM -> "Histogram"
            null -> null
        }
        isLibrary(id) -> ShapeLibrary.find(id)?.title
        else -> null
    }

    /** Tuval türünün seçici kategorileri (bölümlere ayrılmış). */
    fun categories(kind: CanvasKind): List<PickerCategory> {
        // v45 basit not alma gelişmiş not almanın bütün şekillerini, grafiklerini, eğrilerini ve fen/bilim şekillerini sunar
        // (v33–v44'teki 42 şekillik üç kategori kalktı); kategoriler ve kimlikler birebir aynıdır.
        if (kind == CanvasKind.SIMPLE_NOTES) return categories(CanvasKind.NOTES)
        val out = ArrayList<PickerCategory>()
        val basicSection = "Temel"
        ShapeCategory.forKind(kind).forEach { cat ->
            if (cat == ShapeCategory.NOTE_GRAPHS) return@forEach
            out += PickerCategory(cat.name, builtin = cat, section = basicSection, items = cat.shapes.map { it.name })
        }
        // v42 resim tuvalleri (gelişmiş ve basit): genel şekiller tek bölümde, kategorilere ayrılmış; nesne çizimleri
        // (hayvan, taşıt, eşya, çizim yardımcıları) yok. Basit resimde akış şeması da yok.
        if (kind == CanvasKind.ART || kind == CanvasKind.SIMPLE_ART) {
            com.karapp.drawguidance.content.library.general.GeneralShapes.categories
                .filter { kind == CanvasKind.ART || it.id != com.karapp.drawguidance.content.library.general.GeneralShapes.FLOWCHART }
                .forEach { cat -> out += PickerCategory(cat.id, title = cat.title, section = basicSection, items = cat.shapes.map { ShapeLibrary.PREFIX + it.id }) }
            return out
        }
        // v33 çocuklar için: yalnız 50 çocuk şekli (kategorili)
        if (kind == CanvasKind.KIDS) {
            ShapeLibrary.forAudience(com.karapp.drawguidance.content.library.Audience.KIDS).filter { it.listed }.forEach { cat ->
                out += PickerCategory(cat.id, title = cat.title, section = cat.section.ifBlank { "Çocuklar" }, items = cat.shapes.map { ShapeLibrary.PREFIX + it.id })
            }
            return out
        }
        if (kind == CanvasKind.NOTES) {
            out += PickerCategory("graphs", title = "Grafikler", section = "Grafik", items = graphItems)
            out += PickerCategory("curves", title = "Grafik eğrileri", section = "Grafik", items = curveItems)
        }
        val audience = if (kind.isNotes) com.karapp.drawguidance.content.library.Audience.NOTES else com.karapp.drawguidance.content.library.Audience.ART
        ShapeLibrary.categories.filter { it.listed && (it.audience == audience || it.audience == com.karapp.drawguidance.content.library.Audience.BOTH) }.forEach { cat ->
            out += PickerCategory(cat.id, title = cat.title, section = cat.section.ifBlank { "Diğer" }, items = cat.shapes.map { ShapeLibrary.PREFIX + it.id })
        }
        return out
    }

    /** Dönüştürmede sunulan (aynı yerleşimle çizilebilen) şekiller: yerleşik temel şekiller. */
    fun convertible(kind: CanvasKind): List<PickerCategory> = categories(kind).filter { it.builtin != null }
}
