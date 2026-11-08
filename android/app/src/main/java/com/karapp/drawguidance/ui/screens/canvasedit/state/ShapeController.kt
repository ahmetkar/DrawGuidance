package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.content.fill.RegionFill
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.GraphSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.GraphShapes
import com.karapp.drawguidance.ui.drawing.ShapeEditor
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeRenderer
import com.karapp.drawguidance.ui.drawing.ShapeFit
import com.karapp.drawguidance.ui.drawing.ShapeKind
import com.karapp.drawguidance.ui.drawing.VectorEdit
import com.karapp.drawguidance.ui.drawing.defaultSize
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.ShapeDrag
import com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool
import com.karapp.drawguidance.ui.screens.canvasedit.VectorSelection
import com.karapp.drawguidance.ui.screens.canvasedit.editor.InkFill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/**
 * Vektör şekiller: şekil türü ve biçimi, tuvale ekleme (sürükleyerek ya da dokunarak; eklenince seçili olarak Taşı'ya
 * geçilir) ve şekil düzenleme (v16): vektör noktalarını görme, seçip taşıma, nokta ekleme, noktalar arasına çizgi
 * çizme, çizgiyi seçip eğimini ve kavisini değiştirme, seçili noktaları bağlı çizgileriyle kesme (ayrı eleman) ya da
 * silme; parça özellikleri (renk, dolgu, çizgi biçimi) ve grafiklerde grafik aracı. v17: Alan boya — çizgilerle
 * çevrili her alan ayrı renklenir (boyama kitabı gibi); çizgiler düzenlenince boyalı alanlar yeni çizgilere uyar.
 */
class ShapeController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val brush: () -> BrushController,
    private val style: StyleController,
) {
    /** Eklenecek şeklin kimliği ([com.karapp.drawguidance.ui.drawing.ShapeRegistry]: yerleşik, grafik ya da kütüphane). */
    private val _shape = MutableStateFlow(ShapeKind.CIRCLE.name)
    val shape: StateFlow<String> = _shape.asStateFlow()

    private val _isPickerOpen = MutableStateFlow(false)
    val isPickerOpen: StateFlow<Boolean> = _isPickerOpen.asStateFlow()

    /** Yeni şekillerin biçimi (kenar kalınlığı, dolgu, köşe yuvarlaklığı); kenar rengi seçili renktir. */
    private val _shapeStyle = MutableStateFlow(ShapeStyle(strokeWidth = BrushType.PENCIL.defaultSize()))
    val shapeStyle: StateFlow<ShapeStyle> = _shapeStyle.asStateFlow()

    /** Yeni grafiklerin eksen etiketleri ve formülü. */
    private val _graphSpec = MutableStateFlow(GraphSpec())
    val graphSpec: StateFlow<GraphSpec> = _graphSpec.asStateFlow()

    /** Seçili tek vektör şekil (biçim ve şekil düzenleme bunu kullanır). */
    val selectedShape: StateFlow<Stroke?> = combine(store.canvas, store.selectedIds) { canvas, ids ->
        if (ids.isEmpty()) null else canvas?.strokes?.firstOrNull { it.id in ids && it.shape != null }
    }.stateIn(host.scope, SharingStarted.Eagerly, null)

    /**
     * v23 "Ok çıkar": okun çıktığı elemanın kutusu (tuval birimi). Ok aracı açıkken sürüklenen ya da dokunulan yere
     * giden okun başı bu kutunun kenarına oturur; ok eklenince ya da moddan çıkınca biter.
     */
    private val _arrowFrom = MutableStateFlow<androidx.compose.ui.geometry.Rect?>(null)
    val arrowFrom: StateFlow<androidx.compose.ui.geometry.Rect?> = _arrowFrom.asStateFlow()

    fun startArrowFrom(bounds: androidx.compose.ui.geometry.Rect) {
        _shape.value = ShapeKind.ARROW.name
        _isPickerOpen.value = false
        host.startAction(EditMode.SHAPE)
        _arrowFrom.value = bounds
    }

    fun clearArrowFrom() { _arrowFrom.value = null }

    /** Kutunun merkezinden [target]'a giden doğrunun kutu kenarından biraz dışarıdaki noktası. */
    private fun edgePoint(box: androidx.compose.ui.geometry.Rect, target: Offset): Offset {
        val c = box.center
        val d = target - c
        val len = d.getDistance()
        if (len < 1e-3f || box.contains(target)) return c
        val hw = box.width / 2f; val hh = box.height / 2f
        val t = minOf(if (kotlin.math.abs(d.x) > 1e-4f) hw / kotlin.math.abs(d.x) else Float.MAX_VALUE, if (kotlin.math.abs(d.y) > 1e-4f) hh / kotlin.math.abs(d.y) else Float.MAX_VALUE)
        val margin = (minOf(box.width, box.height) * 0.06f).coerceIn(4f, 24f)
        return c + d * t + d / len * margin
    }

    fun openPicker() { _isPickerOpen.value = true }
    fun closePicker() { _isPickerOpen.value = false }

    /** Şekil seçilince Şekil moduna geçilir: tuvalde sürükleyerek çizilir. */
    fun selectShape(kind: ShapeKind) = selectShape(kind.name)

    /** Şekil seçilince Şekil moduna geçilir (eski seçim kalkar): tuvalde sürükleyerek ya da dokunarak eklenir. */
    fun selectShape(id: String) {
        _shape.value = id
        _isPickerOpen.value = false
        host.startAction(EditMode.SHAPE)
    }

    /** Şekiller içi dolu eklensin mi: dolgu rengi (null: dolgusuz). */
    fun setNewShapeFill(hex: String?) {
        _shapeStyle.value = _shapeStyle.value.copy(fillColorHex = hex)
    }

    /** Fırça kalınlığı değişince yeni şekillerin kenar kalınlığı onu izler. */
    fun setDrawingWidth(width: Float) {
        _shapeStyle.value = _shapeStyle.value.copy(strokeWidth = width)
    }

    fun updateShapeStyle(value: ShapeStyle) {
        _shapeStyle.value = value
        style.applyStyleToSelection(value)
    }

    fun updateGraphSpec(graph: GraphSpec) {
        _graphSpec.value = graph
        style.applyGraphToSelection(graph)
    }

    /** Yeni şekil ve diyagramların biçimi: kenar rengi seçili renk. */
    fun newShapeStyle(): ShapeStyle = _shapeStyle.value.copy(strokeColorHex = brush().inkColor())

    /**
     * Şeklin çizgileri ([gestureId] aynı kaldıkça kimlik ve görünüm aynıdır: önizlemede görünen ile eklenen
     * birebir aynı). Şekil tek bir vektör elemandır: alt yolları, dolgusu ve kenar biçimi veriyle saklanır.
     */
    fun shapeStrokes(gestureId: String, start0: Offset, end: Offset): List<Stroke> {
        val current = store.canvas.value ?: return emptyList()
        val id = _shape.value
        // v23 ok bir elemandan çıkıyorsa başı o elemanın kenarında
        val start = _arrowFrom.value?.takeIf { id == ShapeKind.ARROW.name }?.let { edgePoint(it, end) } ?: start0
        // v22: 3B çizim modunda gölgeli cisimler sahnenin gerçek 3B nesnesi olur (sahneyle döner)
        if (current.guide.draw3d && com.karapp.drawguidance.ui.drawing.Scene3D.isSolidKind(id)) {
            val cam = com.karapp.drawguidance.ui.drawing.Scene3D.camera(current.guide, current.width, current.height)
            val solid = com.karapp.drawguidance.ui.drawing.Scene3D.solidStroke(
                id, start, end, newShapeStyle(), cam, UUID.nameUUIDFromBytes("$gestureId-shape".toByteArray()).toString(), current.strokes.size,
            ) ?: return emptyList()
            return listOf(solid)
        }
        val stroke = ShapeFactory.build(
            kind = id,
            start = start,
            end = end,
            style = newShapeStyle(),
            // v18: grafik kağıt rengine uyar (formül kutusu, nokta içleri, ızgara)
            graph = if (com.karapp.drawguidance.ui.drawing.ShapeRegistry.isGraph(id)) {
                com.karapp.drawguidance.ui.drawing.ShapeRegistry.defaultGraph(id).copy(paper = com.karapp.drawguidance.ui.drawing.GraphPaper.normalize(current.pagePaperHex))
            } else null,
            id = UUID.nameUUIDFromBytes("$gestureId-shape".toByteArray()).toString(),
            order = current.strokes.size,
        ) ?: return emptyList()
        return listOf(stroke)
    }

    fun addShape(gestureId: String, start: Offset, end: Offset) {
        if (_arrowFrom.value != null && _shape.value == ShapeKind.ARROW.name) {
            // Sürüklemenin bittiği (ya da dokunulan) yere ok
            val current = store.canvas.value ?: return
            val strokes = shapeStrokes(gestureId, start, end)
            _arrowFrom.value = null
            if (strokes.isNotEmpty()) {
                store.commit(current.strokes + strokes)
                host.inserted(strokes.mapTo(HashSet()) { it.id })
            }
            return
        }
        if ((end - start).getDistance() < 2f) return addShapeAt(gestureId, start)
        val current = store.canvas.value ?: return
        val strokes = shapeStrokes(gestureId, start, end)
        if (strokes.isNotEmpty()) {
            store.commit(current.strokes + strokes)
            // Eklenen şekil seçili olarak Taşı moduna geçilir (eski seçim eylem başında kalktı)
            host.inserted(strokes.mapTo(HashSet()) { it.id })
        }
    }

    /** Dokunulan noktaya, tuvalin küçük kenarının %30'u boyutunda şekil ekler. */
    fun addShapeAt(gestureId: String, center: Offset) {
        if (_arrowFrom.value != null && _shape.value == ShapeKind.ARROW.name) return addShape(gestureId, center, center)
        val current = store.canvas.value ?: return
        val id = _shape.value
        val registry = com.karapp.drawguidance.ui.drawing.ShapeRegistry
        val line = registry.fit(id) == ShapeFit.LINE
        // Kütüphane çizimleri ve grafikler biraz daha büyük, kendi oranlarında eklenir
        val big = registry.isLibrary(id) || registry.isModernGraph(id)
        val aspect = if (registry.fit(id) == ShapeFit.SQUARE) 1f else registry.aspect(id)
        val half = minOf(current.width, current.height) * (if (big) 0.22f else 0.15f)
        val hw = if (line) half else if (aspect >= 1f) half else half * aspect
        val hh = if (line) half else if (aspect >= 1f) half / aspect else half
        val start = if (line) Offset(center.x - half, center.y) else center - Offset(hw, hh)
        val end = if (line) Offset(center.x + half, center.y) else center + Offset(hw, hh)
        val strokes = shapeStrokes(gestureId, start, end)
        if (strokes.isNotEmpty()) {
            store.commit(current.strokes + strokes)
            host.inserted(strokes.mapTo(HashSet()) { it.id })
        }
    }

    // --- Şekil düzenleme modu (v16: vektör noktaları ve çizgiler) ---

    private val _tool = MutableStateFlow(ShapeTool.NODES)
    val tool: StateFlow<ShapeTool> = _tool.asStateFlow()

    private val _vector = MutableStateFlow(VectorSelection())
    /** Vektör düzenlemede seçili noktalar, seçili çizgi ve çizgi çizmenin sürdüğü nokta (konumlarıyla). */
    val vector: StateFlow<VectorSelection> = _vector.asStateFlow()

    private val _parts = MutableStateFlow<Set<Int>>(emptySet())
    /** Seçime dahil parçalar (alt yol sıraları): Özellik penceresi bunlara uygulanır (boşsa tüm şekle). */
    val parts: StateFlow<Set<Int>> = _parts.asStateFlow()

    /** Eski API: bağla aracı kalktı (her zaman null). */
    val connectFrom: StateFlow<Offset?> = MutableStateFlow<Offset?>(null).asStateFlow()

    fun selectTool(value: ShapeTool) {
        _tool.value = value
        // Çizgi çizme her araç değişiminde baştan başlar; çizgi seçimi yalnız Çizgiler aracında, nokta seçimi nokta araçlarında
        val v = _vector.value
        setSelection(
            VectorSelection(
                nodes = if (value == ShapeTool.NODES || value == ShapeTool.ADD_NODE) v.nodes else emptyList(),
                edge = if (value == ShapeTool.LINES) v.edge else null,
            ),
        )
    }

    /** v45 parametreli şekiller (değerler, formül, tutamaklar). */
    val paramShapes = ParamShapeController(store) { selectedShape.value }

    /**
     * Şekil düzenleme moduna girer (seçili şekil üzerinde çalışılır); grafikler grafik aracıyla, v45 parametreli şekiller
     * değer/formül düzenleyicisiyle açılır (vektör noktaları oradaki "Vektör düzenle" ile).
     */
    fun editSelected() {
        val shape = store.selectedStroke { it.shape != null } ?: return
        paramShapes.clearError()
        _tool.value = when {
            shape.shape?.let { com.karapp.drawguidance.ui.drawing.ShapeRegistry.isGraph(it.kind) && it.graph != null } == true -> ShapeTool.GRAPH
            paramShapes.model(shape) != null -> ShapeTool.PARAMS
            else -> ShapeTool.NODES
        }
        setSelection(VectorSelection(), shape)
        // Düzenlenirken yalnızca bu şekil seçili (önceki seçim kalkar); bitince İncele moduna geçilir
        host.startAction(EditMode.SHAPE_EDIT, setOf(shape.id))
    }

    fun onModeChange(to: EditMode) {
        if (to != EditMode.SHAPE) _arrowFrom.value = null
        if (to != EditMode.SHAPE_EDIT) reset()
    }

    fun reset() {
        _vector.value = VectorSelection()
        _parts.value = emptySet()
    }

    /** Geri alma sonrası: vektör seçimi sıfırlanır; düzenlenen şekil kalmadıysa eylem biter (İncele). */
    fun onContentReplaced() {
        reset()
        if (host.mode.value == EditMode.SHAPE_EDIT) {
            val current = store.canvas.value ?: return
            if (current.strokes.none { it.id in store.selection && it.shape != null }) host.endAction()
        }
    }

    /** v36 basit çizim türlerinde şekil düzenleme sade: parça başına az vektör noktası ([VectorEdit.SIMPLE_NODES]). */
    val coarse: Boolean get() = store.canvas.value?.kind?.simple == true

    private fun graphOf(stroke: Stroke): VectorEdit.Graph = VectorEdit.graph(stroke, coarse)

    /** Sürükleme başındaki alt yollardan çizge (basit modda kaba görünüm, öteki modlarda eski davranış). */
    private fun graphOf(paths: List<ShapeFactory.EditablePath>): VectorEdit.Graph =
        if (coarse) VectorEdit.graph(paths, VectorEdit.diagonal(paths), coarse = true) else VectorEdit.graph(paths)

    /** v36 Noktaları azalt: yazılı vektör noktası çok olan parçalar en belirgin noktalarına iner (görünüm değişmez). */
    fun canSimplify(): Boolean {
        val stroke = selectedShape.value ?: return false
        return VectorEdit.canSimplify(graphOf(stroke))
    }

    fun simplifySelected() {
        val stroke = selectedShape.value ?: return
        val g = graphOf(stroke)
        commitPaths(stroke, g, VectorEdit.simplifyNodes(g, VectorEdit.diagonal(stroke)), VectorSelection())
    }

    private fun setSelection(sel: VectorSelection, stroke: Stroke? = selectedShape.value) {
        _vector.value = sel
        _parts.value = if (stroke == null || (sel.nodes.isEmpty() && sel.edge == null)) emptySet() else partsOf(graphOf(stroke), sel)
    }

    private fun partsOf(g: VectorEdit.Graph, sel: VectorSelection): Set<Int> {
        sel.edge?.let { (a, b) -> g.edgeBetween(a, b)?.let { return setOf(it.path) } }
        val ids = selectedNodeIds(g, sel)
        return g.edges.filter { it.a in ids || it.b in ids }.mapTo(HashSet()) { it.path }
    }

    private fun selectedNodeIds(g: VectorEdit.Graph, sel: VectorSelection = _vector.value): Set<Int> =
        sel.nodes.mapNotNullTo(HashSet()) { g.nodeNear(it)?.id }

    private fun selectedEdge(g: VectorEdit.Graph, sel: VectorSelection = _vector.value): VectorEdit.Edge? =
        sel.edge?.let { (a, b) -> g.edgeBetween(a, b) }

    private fun toggle(list: List<Offset>, p: Offset): List<Offset> =
        if (list.any { VectorEdit.near(it, p) }) list.filterNot { VectorEdit.near(it, p) } else list + p

    /**
     * Dokunuş: Noktalar'da nokta seçilir/bırakılır (boşa dokunmak seçimi kaldırır); Nokta ekle'de çizgiye dokunmak
     * oraya nokta, boş yere dokunmak serbest nokta ekler; Çizgi çiz'de dokunulan noktalar arasına çizgi çizilir;
     * Çizgiler'de dokunulan çizgi seçilir; Grafik'te sayılar/değerler düzenlenir.
     */
    fun applyTool(point: Offset, tolerance: Float = 12f) {
        val stroke = selectedShape.value ?: return
        if (_tool.value == ShapeTool.GRAPH) return graphTap(stroke, point, tolerance)
        if (_tool.value == ShapeTool.PARAMS) return
        if (_tool.value == ShapeTool.FILL) return regionTap(stroke, point)
        val g = graphOf(stroke)
        val sel = _vector.value
        val node = g.nodeAt(point, tolerance * NODE_HIT)
        when (_tool.value) {
            ShapeTool.NODES -> setSelection(
                if (node != null) VectorSelection(nodes = toggle(sel.nodes, node.point)) else VectorSelection(), stroke,
            )
            ShapeTool.ADD_NODE -> {
                if (node != null) return setSelection(VectorSelection(nodes = toggle(sel.nodes, node.point)), stroke)
                val hit = g.edgeAt(point, tolerance * EDGE_HIT)
                if (hit != null) {
                    val (paths, at) = VectorEdit.insertNode(g, hit)
                    commitPaths(stroke, g, paths, VectorSelection(nodes = listOf(at)))
                } else {
                    commitPaths(stroke, g, VectorEdit.addFreeNode(g, point), VectorSelection(nodes = listOf(point)))
                }
            }
            ShapeTool.CONNECT -> connectTap(stroke, g, point, tolerance)
            ShapeTool.LINES -> {
                val hit = g.edgeAt(point, tolerance * EDGE_HIT)
                val current = selectedEdge(g, sel)
                setSelection(
                    if (hit == null || hit.edge.id == current?.id) VectorSelection() else VectorSelection(edge = hit.edge.start to hit.edge.end),
                    stroke,
                )
            }
            ShapeTool.FILL, ShapeTool.GRAPH, ShapeTool.PARAMS -> Unit
        }
    }

    /** Çizgi çiz: ilk dokunulan nokta başlangıçtır; sonraki her dokunuş oraya çizgi çizer ve oradan devam eder. */
    private fun connectTap(stroke: Stroke, g: VectorEdit.Graph, point: Offset, tolerance: Float) {
        val from = _vector.value.linkFrom
        val (paths, at, free) = anchor(g, point, tolerance, exclude = from)
        when {
            from == null -> {
                // Başlangıç boş bir yerse oraya serbest nokta konur
                val withNode = if (free) VectorEdit.addFreeNode(VectorEdit.graph(paths), at) else paths
                commitPaths(stroke, g, withNode, VectorSelection(nodes = listOf(at), linkFrom = at))
            }
            // Başlangıç noktasına yeniden dokunmak çizmeyi bitirir
            VectorEdit.near(from, at) -> setSelection(VectorSelection(), stroke)
            else -> {
                val connected = VectorEdit.connect(VectorEdit.graph(paths), from, at, ::withLineStyle) ?: paths
                commitPaths(stroke, g, connected, VectorSelection(nodes = listOf(at), linkFrom = at))
            }
        }
    }

    /** Dokunulan yerdeki vektör noktası: var olan nokta, çizgiye eklenen nokta ya da (boşsa) serbest konum. */
    private fun anchor(g: VectorEdit.Graph, point: Offset, tolerance: Float, exclude: Offset? = null): Triple<List<ShapeFactory.EditablePath>, Offset, Boolean> {
        val excludeId = exclude?.let { g.nodeNear(it)?.id }
        g.nodeAt(point, tolerance * NODE_HIT, excludeId)?.let { return Triple(g.paths, it.point, false) }
        g.edgeAt(point, tolerance * EDGE_HIT)?.let { hit ->
            val (paths, at) = VectorEdit.insertNode(g, hit)
            return Triple(paths, at, false)
        }
        return Triple(g.paths, point, true)
    }

    /**
     * Düzenlenen parçaları kaydeder (tek geri alma adımı) ve vektör seçimini günceller. Parçalar değişmediyse yalnızca
     * seçim değişir; çizilecek bir şey kalmadıysa şekil silinir.
     */
    private fun commitPaths(stroke: Stroke, g: VectorEdit.Graph, paths: List<ShapeFactory.EditablePath>, sel: VectorSelection) {
        if (paths === g.paths) return setSelection(sel, stroke)
        val current = store.canvas.value ?: return
        if (!VectorEdit.drawable(paths)) return removeShape(current, stroke)
        val edited = ShapeFactory.fromEditable(stroke, refreshRegions(stroke, paths)) ?: return
        store.commit(current.strokes.map { if (it.id == stroke.id) edited.copy(order = it.order) else it })
        store.select(setOf(stroke.id))
        setSelection(sel, edited)
    }

    private fun removeShape(current: CanvasUi, stroke: Stroke) {
        store.commit(current.strokes.filterNot { it.id == stroke.id }.mapIndexed { i, s -> if (s.order == i) s else s.copy(order = i) })
        store.select(emptySet())
        reset()
        host.endAction()
    }

    // --- v17: Alan boya ---

    private val _regionColor = MutableStateFlow<String?>(null)
    /** Alan boya rengi: null seçili kalem rengi, "" temizle (dokunulan alanın rengi kalkar). */
    val regionColor: StateFlow<String?> = _regionColor.asStateFlow()

    fun setRegionColor(hex: String?) { _regionColor.value = hex }

    /** Alan boyarken kullanılacak renk (null: kalem rengi). */
    fun regionInk(): String = brush().inkColor()

    /**
     * Dokunulan alanı boyar: çizgilerle çevrili alan bulunur (önce tam, bulunamazsa küçük boşluklar kapatılarak) ve
     * kenarsız bir dolgu parçası olarak şeklin sonuna eklenir (çizgilerin iç kenarında durur, çizgiyi örtmez). Aynı alan
     * daha önce boyandıysa rengi değişir; Temizle'de dokunulan alanın rengi kalkar. Alan kapalı değilse bildirim.
     */
    private fun regionTap(stroke: Stroke, point: Offset) {
        fillRegion(stroke, point, select = true, reportOpen = true)
    }

    /**
     * v33 Şekil boyama (basit çizim): dokunulan yerdeki en üstteki şeklin, dokunulan noktayı çevreleyen alanı seçili
     * renkle boyanır (düzenlemeye girilmez). Kilitli/gizli şekiller atlanır. Boyandıysa true.
     * v39 boya kutusu önce seçer sonra boyar: boyanan şekil seçilir (hangi şeklin boyandığı kutusuyla görünür; seçim
     * geri alma adımı değildir, boyama tek adımdır). Boyanacak şekil yoksa seçim kalkar.
     */
    fun paintAt(point: Offset, locked: Set<String>): Boolean {
        val strokes = store.canvas.value?.strokes.orEmpty()
        // v44 kalemle çizilmiş kapalı şeklin içi (dolgu şekli çizginin altına eklenir); üstteki şekil önceliklidir
        val ink = InkFill.closedAt(strokes, point, locked)
        val candidates = paintCandidates(point, locked).filterNot { it.id.startsWith(InkFill.PREFIX) }
        fun rank(s: Stroke?) = if (s == null) -1L else s.order.toLong() * 1_000_000L + strokes.indexOfFirst { it.id == s.id }
        if (ink != null && rank(ink) > rank(candidates.firstOrNull()) && paintInk(ink)) return true
        for (stroke in candidates) {
            if (fillRegion(stroke, point, select = true, reportOpen = false)) return true
        }
        if (ink != null && paintInk(ink)) return true
        store.select(emptySet())
        return false
    }

    /** v44 Kapalı fırça izinin içini seçili renkle boyar (boş renk: dolgu kalkar). */
    private fun paintInk(ink: Stroke): Boolean {
        val current = store.canvas.value ?: return false
        val color = _regionColor.value ?: brush().inkColor()
        val next = if (color.isEmpty()) current.strokes.filterNot { it.id == InkFill.PREFIX + ink.id }
        else InkFill.apply(current.strokes, setOf(ink.id), color)
        if (next == current.strokes) return false
        store.commit(next)
        store.select(setOf(ink.id))
        return true
    }

    /**
     * v39 Düzenle'de bekleyen Alan boya ile dokunulan şekil: şekil seçilip Alan boya ile açıldıktan sonra aynı dokunuşla
     * dokunulan kapalı alan boyanır (önce seçer sonra boyar). Alan kapalı değilse yalnız açılır (bildirim yok).
     */
    fun paintSelectedAt(point: Offset): Boolean {
        val stroke = store.selectedStroke { it.shape != null } ?: return false
        return fillRegion(stroke, point, select = true, reportOpen = false)
    }

    /** Dokunulan yerdeki boyanabilir şekiller: en üstteki önce; kilitli/gizli olanlar ve kutusu dışında kalanlar yok. */
    private fun paintCandidates(point: Offset, locked: Set<String>): List<Stroke> {
        val current = store.canvas.value ?: return emptyList()
        return current.strokes.withIndex()
            // v45 kalem şeklindeki fırça boyası ayrı bir şekil değil, boyadır: boyanacak şekil sayılmaz
            .filter { (_, s) -> s.shape != null && !s.hidden && s.id !in locked && !s.id.startsWith(com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush.PREFIX) }
            .sortedWith(compareByDescending<IndexedValue<Stroke>> { it.value.order }.thenByDescending { it.index })
            .map { it.value }
            .filter { stroke ->
                val pts = ShapeFactory.subPaths(stroke).flatMap { it.points }
                val margin = 2f
                pts.isNotEmpty() && point.x >= pts.minOf { it.x } - margin && point.x <= pts.maxOf { it.x } + margin &&
                    point.y >= pts.minOf { it.y } - margin && point.y <= pts.maxOf { it.y } + margin
            }
    }

    /**
     * v37 çocuk modunda kova imlecinin altındaki şekil ve boyanmaya hazır alan (kaydedilmez, yalnız gösterilir):
     * [paintAt] ile aynı seçim — boyanacak alanı bulunan en üstteki şekil; hiçbirinde kapalı alan yoksa kutusu içinde
     * kalınan en üstteki şekil (alan null).
     */
    fun paintTarget(point: Offset, locked: Set<String>): PaintTarget? {
        val candidates = paintCandidates(point, locked)
        val tap = P(point.x, point.y)
        for (stroke in candidates) {
            val spec = stroke.shape ?: continue
            val region = regionAt(spec.style, ShapeFactory.subPaths(stroke), tap) ?: continue
            return PaintTarget(stroke.id, region.map { Offset(it.x, it.y) })
        }
        return candidates.firstOrNull()?.let { PaintTarget(it.id, null) }
    }

    /**
     * v37 çocuk modunda 2 sn basılı tutunca bütün şekil seçili renge boyanır (tek geri alma adımı): şeklin bütün kapalı
     * parçaları ve boyalı alanları; kenar rengiyle dolu küçük ayrıntılar (göz bebeği, düğme) korunur. Şeklin boyanacak
     * kapalı parçası yoksa yalnız dokunulan alan boyanır. Boyandıysa true.
     */
    fun paintWhole(point: Offset, locked: Set<String>): Boolean {
        val stroke = paintTarget(point, locked)?.let { t -> store.canvas.value?.strokes?.firstOrNull { it.id == t.strokeId } }
            ?: run { store.select(emptySet()); return false }
        val current = store.canvas.value ?: return false
        // v39 önce seçer sonra boyar (seçim geri alma adımı değil)
        store.select(setOf(stroke.id))
        val spec = stroke.shape ?: return false
        val color = _regionColor.value ?: brush().inkColor()
        val paths = ShapeFactory.subPaths(stroke)
        val ink = spec.style.strokeColorHex
        val all = paths.flatMap { it.points }
        val boxArea = ((all.maxOfOrNull { it.x } ?: 0f) - (all.minOfOrNull { it.x } ?: 0f)) * ((all.maxOfOrNull { it.y } ?: 0f) - (all.minOfOrNull { it.y } ?: 0f))
        fun fillable(p: ShapeFactory.EditablePath): Boolean {
            if (!p.closed || p.points.size < 3 || p.label != null || p.tag == VectorEdit.VNODE || p.tag == "guide") return false
            val fill = p.fillColorHex ?: spec.style.fillColorHex ?: return true
            if (fill.equals(p.strokeColorHex ?: ink, ignoreCase = true)) return false
            // Koyu dolgulu küçük ayrıntı (göz bebeği, kapı tokmağı) olduğu gibi kalır
            val small = RegionFill.areaOf(p.points.map { P(it.x, it.y) }) < boxArea * 0.03f
            return !(small && darkHex(fill))
        }
        // v45 fırça boyası da bütün şekille birlikte yeniden boyanır
        val targets = paths.indices.filter { com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush.isPaintLayer(paths[it].tag) || fillable(paths[it]) }
        if (targets.isEmpty()) return fillRegion(stroke, point, select = false, reportOpen = false)
        val next = if (color.isEmpty()) {
            // Temizle: boyalı alanlar kalkar
            paths.filter { !com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush.isPaintLayer(it.tag) }
        } else paths.mapIndexed { i, p -> if (i in targets) p.copy(fillColorHex = color) else p }
        val edited = ShapeFactory.fromEditable(stroke, next) ?: return false
        store.commit(current.strokes.map { if (it.id == stroke.id) edited.copy(order = it.order) else it })
        return true
    }

    /** Dokunulan alanı boyar (bkz. [regionTap]); boyandıysa true. */
    private fun fillRegion(stroke: Stroke, point: Offset, select: Boolean, reportOpen: Boolean): Boolean {
        val current = store.canvas.value ?: return false
        val spec = stroke.shape ?: return false
        val paths = ShapeFactory.subPaths(stroke)
        val color = _regionColor.value ?: brush().inkColor()
        val tap = P(point.x, point.y)
        val found = regionAt(spec.style, paths, tap)
        val regions = paths.indices.filter { paths[it].tag == VectorEdit.REGION }
        fun poly(i: Int) = paths[i].points.map { P(it.x, it.y) }
        val containing = regions.filter { RegionFill.contains(poly(it), tap) }
        // Aynı alan: dokunulan yeri içeren ve alanı bulunanla neredeyse aynı olan boyalı alan
        val same = if (found == null) containing.lastOrNull() else {
            val a = RegionFill.areaOf(found)
            containing.lastOrNull { kotlin.math.abs(RegionFill.areaOf(poly(it)) - a) <= kotlin.math.max(a * 0.08f, 4f) }
        }
        val next: List<ShapeFactory.EditablePath> = when {
            color.isEmpty() -> {
                val hit = same ?: containing.lastOrNull() ?: return false
                paths.filterIndexed { i, _ -> i != hit }
            }
            // v45 alanın üstünde fırça boyası varsa yeniden boyanan alan en üste alınır (kova boyayı örter)
            same != null && paths.any { it.tag == com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush.TAG } ->
                paths.filterIndexed { i, _ -> i != same } + paths[same].copy(fillColorHex = color)
            same != null -> paths.mapIndexed { i, p -> if (i == same) p.copy(fillColorHex = color) else p }
            found == null -> {
                if (reportOpen) host.emit(com.karapp.drawguidance.ui.screens.canvasedit.EditEvent.RegionOpen)
                return false
            }
            else -> paths + ShapeFactory.EditablePath(
                found.map { Offset(it.x, it.y) }, closed = true, fillColorHex = color, widthScale = 0f, tag = VectorEdit.REGION,
            )
        }
        val edited = ShapeFactory.fromEditable(stroke, next) ?: return false
        // v39 önce seçilir sonra boyanır (seçim geri alma adımı değil; boyama tek adım)
        if (select) store.select(setOf(stroke.id))
        store.commit(current.strokes.map { if (it.id == stroke.id) edited.copy(order = it.order) else it })
        return true
    }

    // --- v45 çocuk modunda boya fırçası ---

    /**
     * Fırçanın başladığı yerin alanı (bkz. [com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush]): kova ile
     * aynı seçim — kalemle çizilmiş kapalı şekil üstteyse onun içi, değilse alanı bulunan en üstteki şekil (önce tam,
     * bulunamazsa küçük boşluklar kapatılarak). Boş kâğıtta ya da kapalı olmayan yerde null (serbest boyanır).
     */
    fun brushRegion(point: Offset, locked: Set<String>): com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush.Region? {
        val strokes = store.canvas.value?.strokes.orEmpty()
        val ink = InkFill.closedAt(strokes, point, locked)
        val candidates = paintCandidates(point, locked).filterNot { it.id.startsWith(InkFill.PREFIX) }
        fun rank(s: Stroke?) = if (s == null) -1L else s.order.toLong() * 1_000_000L + strokes.indexOfFirst { it.id == s.id }
        val tap = P(point.x, point.y)
        fun inkRegion(s: Stroke): com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush.Region? {
            val walls = listOf(RegionFill.Line(s.points.map { P(it.x, it.y) }, closed = true, width = kotlin.math.max(0.3f, s.sizePx)))
            val outline = RegionFill.region(walls, tap) ?: return null
            return com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush.Region(s.id, true, walls, 0f, point, outline.map { Offset(it.x, it.y) })
        }
        if (ink != null && rank(ink) > rank(candidates.firstOrNull())) inkRegion(ink)?.let { return it }
        for (stroke in candidates) {
            val spec = stroke.shape ?: continue
            val walls = wallsOf(spec.style, ShapeFactory.subPaths(stroke))
            if (walls.isEmpty()) continue
            var gap = 0f
            val outline = RegionFill.region(walls, tap) ?: run {
                gap = (spec.style.strokeWidth * 1.5f).coerceIn(1.5f, 8f)
                RegionFill.region(walls, tap, gap = gap)
            } ?: continue
            return com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush.Region(stroke.id, false, walls, gap, point, outline.map { Offset(it.x, it.y) })
        }
        return ink?.let(::inkRegion)
    }

    /**
     * Fırça darbesini kaydeder (tek geri alma adımı): [region] varsa darbe alanın içinde kalacak biçimde kırpılıp şekle
     * (kalem şeklinde ayrı dolgu olarak) eklenir; yoksa boş kâğıda düz fırça izi olur. Bir şey boyandıysa true.
     */
    fun commitBrush(
        region: com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush.Region?,
        points: List<Offset>,
        sizePx: Float,
        colorHex: String,
    ): Boolean {
        val current = store.canvas.value ?: return false
        if (points.isEmpty() || colorHex.isEmpty()) return false
        val brush = com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush
        if (region == null) {
            store.commit(current.strokes + brush.freeStroke(points, colorHex, sizePx, current.strokes.size, null))
            return true
        }
        val target = current.strokes.firstOrNull { it.id == region.strokeId } ?: return false
        val polys = brush.clip(region, points, brush.radius(sizePx))
        if (polys.isEmpty()) return false
        if (region.ink) {
            val paint = brush.inkPaint(target, polys, colorHex) ?: return false
            store.commit(brush.insertInkPaint(current.strokes, target.id, paint))
            return true
        }
        val edited = ShapeFactory.fromEditable(target, ShapeFactory.subPaths(target) + brush.parts(polys, colorHex)) ?: return false
        store.commit(current.strokes.map { if (it.id == target.id) edited.copy(order = it.order) else it })
        return true
    }

    /** Alanı sınırlayan görünür çizgiler: kenarı çizilen parçalar ve dolgusu görünen parçaların sınırı. */
    private fun wallsOf(style: ShapeStyle, paths: List<ShapeFactory.EditablePath>): List<RegionFill.Line> = paths.mapNotNull { p ->
        // Kılavuz çizgileri (çizim yardımcılarındaki oran çizgileri) sınır sayılmaz
        // v45 fırça boyası da (kova dolgusu gibi) sınır değildir
        if (p.label != null || p.tag == VectorEdit.VNODE || com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush.isPaintLayer(p.tag) || p.tag == "guide" || p.points.size < 2) return@mapNotNull null
        val scale = p.widthScale ?: 1f
        val width = when {
            scale > 0f -> kotlin.math.max(0.3f, style.strokeWidth * scale)
            // Kenarsız ama dolgulu parça (ör. göz bebeği): sınırı ince bir çizgi sayılır
            p.closed && (p.fillColorHex ?: style.fillColorHex) != null -> 0.3f
            else -> return@mapNotNull null
        }
        RegionFill.Line(p.points.map { P(it.x, it.y) }, p.closed, width)
    }

    private fun regionAt(style: ShapeStyle, paths: List<ShapeFactory.EditablePath>, tap: P): List<P>? {
        val walls = wallsOf(style, paths)
        if (walls.isEmpty()) return null
        return RegionFill.region(walls, tap)
            ?: RegionFill.region(walls, tap, gap = (style.strokeWidth * 1.5f).coerceIn(1.5f, 8f))
    }

    /** Çizgiler değişince boyalı alanlar yeni çizgilere göre yeniden bulunur (bulunamazsa eski hali kalır). */
    private fun refreshRegions(stroke: Stroke, paths: List<ShapeFactory.EditablePath>): List<ShapeFactory.EditablePath> {
        if (paths.none { it.tag == VectorEdit.REGION }) return paths
        val style = stroke.shape?.style ?: return paths
        return paths.map { p ->
            if (p.tag != VectorEdit.REGION) return@map p
            val seed = RegionFill.interiorPoint(p.points.map { P(it.x, it.y) }) ?: return@map p
            val poly = regionAt(style, paths, seed) ?: return@map p
            p.copy(points = poly.map { Offset(it.x, it.y) })
        }
    }

    // --- Parça özellikleri ---

    /** Çizgi çiz aracıyla eklenen parçaların biçimi (son uygulanan parça biçimi). */
    private var lineStyle = com.karapp.drawguidance.ui.screens.canvasedit.PartStyle()

    private fun withLineStyle(p: ShapeFactory.EditablePath): ShapeFactory.EditablePath {
        val style = lineStyle
        if (style == com.karapp.drawguidance.ui.screens.canvasedit.PartStyle()) return p
        return applyStyle(p, style.copy(fillColorHex = null))
    }

    private fun applyStyle(p: ShapeFactory.EditablePath, style: com.karapp.drawguidance.ui.screens.canvasedit.PartStyle): ShapeFactory.EditablePath {
        var out = p
        style.strokeColorHex?.let { out = out.copy(strokeColorHex = it) }
        style.fillColorHex?.let { out = out.copy(fillColorHex = it.ifEmpty { null }) }
        style.widthScale?.let { out = out.copy(widthScale = if (it == 1f) null else it) }
        style.alpha?.let { out = out.copy(alpha = if (it >= 0.999f) null else it) }
        when (style.line) {
            "solid" -> out = out.copy(dashed = false, dotted = false, widthScale = if (out.widthScale == 0f) null else out.widthScale)
            "dashed" -> out = out.copy(dashed = true, dotted = false, widthScale = if (out.widthScale == 0f) null else out.widthScale)
            "dotted" -> out = out.copy(dashed = false, dotted = true, widthScale = if (out.widthScale == 0f) null else out.widthScale)
            "none" -> out = out.copy(widthScale = 0f)
        }
        return out
    }

    /**
     * Seçili parçalara (seçim yoksa tüm şekle) biçim verir: çizgi/dolgu rengi, kalınlık, çizgi biçimi, saydamlık.
     * Verilen biçim sonraki Çizgi/Eğri eklemelerinde de kullanılır. Kaydırıcı sürüklemesi tek geri alma adımıdır.
     */
    fun applyPartStyle(style: com.karapp.drawguidance.ui.screens.canvasedit.PartStyle) {
        val stroke = selectedShape.value ?: return
        val current = store.canvas.value ?: return
        val parts = _parts.value
        lineStyle = lineStyle.merge(style)
        val paths = ShapeFactory.subPaths(stroke)
        // Tüm şekle verilen biçim boyalı alanlara (v17) uygulanmaz: alanlar kenarsız kalır
        val edited = ShapeFactory.fromEditable(
            stroke, paths.mapIndexed { i, p -> if ((parts.isEmpty() && p.tag != VectorEdit.REGION) || i in parts) applyStyle(p, style) else p },
        ) ?: return
        val updated = current.strokes.map { if (it.id == stroke.id) edited.copy(order = it.order) else it }
        store.commit(updated, coalesce = "part-style-${stroke.id}-${parts.sorted()}")
        store.select(setOf(stroke.id))
    }

    private fun com.karapp.drawguidance.ui.screens.canvasedit.PartStyle.merge(o: com.karapp.drawguidance.ui.screens.canvasedit.PartStyle) =
        com.karapp.drawguidance.ui.screens.canvasedit.PartStyle(
            o.strokeColorHex ?: strokeColorHex, null, o.widthScale ?: widthScale, o.line ?: line, o.alpha ?: alpha,
        )

    /** Parça özellikleri penceresinin başlangıç değerleri (ilk seçili parçanınki). */
    fun partStyleOf(): com.karapp.drawguidance.ui.screens.canvasedit.PartStyle {
        val stroke = selectedShape.value ?: return com.karapp.drawguidance.ui.screens.canvasedit.PartStyle()
        val spec = stroke.shape ?: return com.karapp.drawguidance.ui.screens.canvasedit.PartStyle()
        val paths = ShapeFactory.subPaths(stroke)
        val p = paths.getOrNull(_parts.value.minOrNull() ?: -1) ?: paths.firstOrNull { it.label == null }
            ?: return com.karapp.drawguidance.ui.screens.canvasedit.PartStyle()
        return com.karapp.drawguidance.ui.screens.canvasedit.PartStyle(
            strokeColorHex = p.strokeColorHex ?: spec.style.strokeColorHex,
            fillColorHex = ShapeRenderer.resolveFill(p.fillColorHex, spec.style) ?: "",
            widthScale = p.widthScale ?: 1f,
            line = when { (p.widthScale ?: 1f) <= 0f -> "none"; p.dotted -> "dotted"; p.dashed -> "dashed"; else -> "solid" },
            alpha = p.alpha ?: 1f,
        )
    }

    // --- Grafik düzenleme (şekil üstünde sayı girme, desen ve nokta sayısı) ---

    /** Şekil üstünde dokunulan grafik öğesi: sayı/değer/eksen adı/formül düzenleme isteği. */
    data class GraphEditRequest(val strokeId: String, val tag: String, val initial: String)

    private val _graphEdit = MutableStateFlow<GraphEditRequest?>(null)
    val graphEdit: StateFlow<GraphEditRequest?> = _graphEdit.asStateFlow()

    fun dismissGraphEdit() { _graphEdit.value = null }

    private fun graphTap(stroke: Stroke, point: Offset, tolerance: Float) {
        val spec = stroke.shape ?: return
        val graph = spec.graph ?: return
        val tag = graphTagAt(stroke, point, tolerance * 1.3f) ?: return
        val params = com.karapp.drawguidance.ui.drawing.ShapeRegistry.params(spec.kind, graph)
        val initial = when {
            tag.startsWith("tick:") -> com.karapp.drawguidance.content.graph.Graphs.tickValue(params, tag.removePrefix("tick:"), aspectOf(stroke))
            tag.startsWith("value:") -> tag.removePrefix("value:").toIntOrNull()?.let { i ->
                com.karapp.drawguidance.content.graph.Graphs.values(params).getOrNull(i)?.let { v -> formatNumber(v) }
            } ?: ""
            tag.startsWith("vlabel:") -> tag.removePrefix("vlabel:").toIntOrNull()?.let { params.valueLabels[it] } ?: ""
            tag == "axis:x" -> params.xLabel
            tag == "axis:y" -> params.yLabel
            tag == "axis:z" -> params.zLabel
            // v18: desen varsa formül x yerine k·x ile açılır (ör. sin(2*x))
            tag == "formula" -> if (params.freq > 1) com.karapp.drawguidance.content.graph.Graphs.editableFormula(params)
            else params.formula.ifBlank { com.karapp.drawguidance.content.graph.Graphs.formulaLabel(params) }
            else -> return
        }
        _graphEdit.value = GraphEditRequest(stroke.id, tag, initial)
    }

    private fun formatNumber(v: Float): String =
        if (v == kotlin.math.round(v)) v.toInt().toString() else String.format(java.util.Locale.forLanguageTag("tr"), "%.2f", v).trimEnd('0').trimEnd(',')

    private fun aspectOf(stroke: Stroke): Float {
        val b = ShapeEditor.partsBounds(stroke, emptySet()) ?: com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps.bounds(stroke) ?: return 1f
        return (b.width / b.height.coerceAtLeast(1e-3f)).coerceIn(0.05f, 20f)
    }

    /** Dokunulan noktadaki etiketli parçanın etiketi (yazılar önce: yazının kutusu hesaba katılır). */
    fun graphTagAt(stroke: Stroke, point: Offset, tolerance: Float): String? {
        val paths = ShapeFactory.subPaths(stroke)
        var best: String? = null
        var bestD = Float.MAX_VALUE
        paths.forEach { p ->
            val tag = p.tag ?: return@forEach
            val d = if (p.label != null) {
                val a = p.points.firstOrNull() ?: return@forEach
                val w = p.label.length * p.labelSize * 0.6f
                val r = androidx.compose.ui.geometry.Rect(a.x - tolerance, a.y - tolerance, a.x + w + tolerance, a.y + p.labelSize + tolerance)
                if (r.contains(point)) 0f else Float.MAX_VALUE
            } else {
                val pts = if (p.closed) p.points + p.points.first() else p.points
                val poly = com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps.distanceToPolyline(
                    point, pts.map { com.karapp.drawguidance.data.model.StrokePoint(it.x, it.y) },
                )
                if (p.closed && insideFilled(point, p.points)) 0f else poly
            }
            // Yazılar ve değerler eğriye göre öncelikli
            val weight = if (tag == "curve" || tag == "fill" || tag == "asymptote") d + tolerance * 0.8f else d
            if (weight <= tolerance && weight < bestD) { bestD = weight; best = tag }
        }
        return best?.takeIf { it != "curve" && it != "fill" && it != "asymptote" && !it.startsWith("key") && !it.startsWith("angle") }
    }

    private fun insideFilled(p: Offset, pts: List<Offset>): Boolean {
        var inside = false
        var j = pts.size - 1
        for (i in pts.indices) {
            val a = pts[i]; val b = pts[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y + 1e-6f) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    /** Girilen sayı/ad uygulanır (grafik yeniden kurulur). */
    fun applyGraphEdit(text: String) {
        val req = _graphEdit.value ?: return
        _graphEdit.value = null
        val graphs = com.karapp.drawguidance.content.graph.Graphs
        updateGraph(req.strokeId) { p ->
            when {
                req.tag.startsWith("tick:") -> if (text.isBlank()) graphs.withoutTickLabel(p, req.tag.removePrefix("tick:")) else graphs.withTickLabel(p, req.tag, text.trim())
                req.tag.startsWith("value:") -> text.replace(',', '.').trim().toFloatOrNull()?.let { v ->
                    graphs.withValue(p, req.tag.removePrefix("value:").toInt(), v)
                } ?: p
                req.tag.startsWith("vlabel:") -> p.copy(valueLabels = p.valueLabels + (req.tag.removePrefix("vlabel:").toInt() to text.trim()))
                req.tag == "axis:x" -> p.copy(xLabel = text.trim())
                req.tag == "axis:y" -> p.copy(yLabel = text.trim())
                req.tag == "axis:z" -> p.copy(zLabel = text.trim())
                // Yazılan formül çizilir; desen (sıklık) formüle katıldığı için 1'e döner
                req.tag == "formula" -> if (text.isBlank()) p.copy(formula = "", freq = 1) else p.copy(formula = text.trim(), preset = "", freq = 1)
                else -> p
            }
        }
    }

    /** Seçili grafiğe desen/nokta sayısı gibi değişiklik uygular. */
    fun updateSelectedGraph(block: (com.karapp.drawguidance.content.graph.GraphParams) -> com.karapp.drawguidance.content.graph.GraphParams) {
        val stroke = selectedShape.value ?: return
        updateGraph(stroke.id, block)
    }

    /** Seçili şekil yeni grafik motoruyla kurulan (düzenlenebilir) bir grafik mi. */
    fun selectedIsGraph(): Boolean =
        selectedShape.value?.shape?.let { com.karapp.drawguidance.ui.drawing.ShapeRegistry.isGraph(it.kind) && it.graph != null } == true

    fun selectedGraphParams(): com.karapp.drawguidance.content.graph.GraphParams? {
        val spec = selectedShape.value?.shape ?: return null
        val g = spec.graph ?: return null
        val kind = com.karapp.drawguidance.ui.drawing.ShapeRegistry.upgradeGraphKind(spec.kind)
        if (!com.karapp.drawguidance.ui.drawing.ShapeRegistry.isGraph(kind)) return null
        return com.karapp.drawguidance.ui.drawing.ShapeRegistry.params(kind, g)
    }

    private fun updateGraph(strokeId: String, block: (com.karapp.drawguidance.content.graph.GraphParams) -> com.karapp.drawguidance.content.graph.GraphParams) {
        val current = store.canvas.value ?: return
        val stroke = current.strokes.firstOrNull { it.id == strokeId } ?: return
        val spec = stroke.shape ?: return
        val g = spec.graph ?: return
        val kind = com.karapp.drawguidance.ui.drawing.ShapeRegistry.upgradeGraphKind(spec.kind)
        val stored = com.karapp.drawguidance.ui.drawing.ShapeRegistry.params(kind, g)
        // Kağıt rengi her kurulumda güncel arka plandan
        val params = stored.copy(paper = com.karapp.drawguidance.ui.drawing.GraphPaper.normalize(current.pagePaperHex).ifEmpty { "#FFFFFF" })
        val next = block(params)
        if (next == stored) return
        val newSpec = com.karapp.drawguidance.ui.drawing.ShapeRegistry.spec(next, g)
        val rebuilt = ShapeFactory.rebuild(stroke.copy(shape = spec.copy(kind = kind)), spec.style, newSpec) ?: return
        store.commit(current.strokes.map { if (it.id == strokeId) rebuilt.copy(order = it.order) else it }, coalesce = "graph-$strokeId")
        store.select(setOf(strokeId))
    }

    // --- Sürükleme: nokta taşıma, çizgi çizme, çizgiyi taşıma / bükme / döndürme ---

    private var dragGraph: VectorEdit.Graph? = null
    private var dragNodes: Set<Int> = emptySet()
    private var dragEdge: VectorEdit.Edge? = null
    private var dragBase: List<ShapeFactory.EditablePath>? = null
    private var dragFrom: Offset? = null

    /** Sürükleme [point] noktasında başlıyor: ne yapılacak (NONE: görünüm kaydırılır). */
    fun beginDrag(point: Offset, tolerance: Float, handleDistance: Float): ShapeDrag {
        dragGraph = null; dragNodes = emptySet(); dragEdge = null; dragBase = null; dragFrom = null
        val stroke = selectedShape.value ?: return ShapeDrag.NONE
        if (_tool.value == ShapeTool.PARAMS) return if (paramShapes.beginDrag(point, tolerance)) ShapeDrag.HANDLE else ShapeDrag.NONE
        if (_tool.value == ShapeTool.GRAPH || _tool.value == ShapeTool.FILL) return ShapeDrag.NONE
        val g = graphOf(stroke)
        dragGraph = g
        val sel = _vector.value
        return when (_tool.value) {
            ShapeTool.NODES, ShapeTool.ADD_NODE -> {
                val node = g.nodeAt(point, tolerance * NODE_HIT) ?: return ShapeDrag.NONE
                val selected = selectedNodeIds(g, sel)
                // Seçili bir nokta sürüklenirse seçili noktaların hepsi birlikte taşınır
                if (node.id in selected) dragNodes = selected
                else {
                    dragNodes = setOf(node.id)
                    setSelection(VectorSelection(nodes = listOf(node.point)), stroke)
                }
                ShapeDrag.NODES
            }
            ShapeTool.CONNECT -> {
                // Bir noktadan (ya da çizginin üstünden) sürüklenirse çizgi çekilir; boş yerden sürükleme kaydırır
                val from = sel.linkFrom
                if (g.nodeAt(point, tolerance * NODE_HIT) == null && g.edgeAt(point, tolerance * EDGE_HIT) == null &&
                    (from == null || (from - point).getDistance() > tolerance * NODE_HIT)
                ) return ShapeDrag.NONE
                val (paths, at, _) = anchor(g, point, tolerance)
                dragBase = paths
                dragFrom = at
                ShapeDrag.CONNECT
            }
            ShapeTool.LINES -> {
                val edge = selectedEdge(g, sel)
                if (edge != null) {
                    if ((VectorEdit.turnHandle(edge, handleDistance) - point).getDistance() <= tolerance * 2.2f) {
                        dragEdge = edge
                        return ShapeDrag.TURN
                    }
                    if ((VectorEdit.bendHandle(edge) - point).getDistance() <= tolerance * 2f) {
                        dragEdge = edge
                        return ShapeDrag.BEND
                    }
                }
                val hit = g.edgeAt(point, tolerance * EDGE_HIT) ?: return ShapeDrag.NONE
                dragEdge = hit.edge
                if (hit.edge.id != edge?.id) setSelection(VectorSelection(edge = hit.edge.start to hit.edge.end), stroke)
                ShapeDrag.EDGE
            }
            ShapeTool.FILL, ShapeTool.GRAPH, ShapeTool.PARAMS -> ShapeDrag.NONE
        }
    }

    /** Döndürme tutamağıyla verilen açı (15°'nin katlarına yakınsa yapışır). */
    private fun turnDegrees(edge: VectorEdit.Edge, from: Offset, to: Offset): Float {
        val pivot = (edge.start + edge.end) / 2f
        val a0 = kotlin.math.atan2(from.y - pivot.y, from.x - pivot.x)
        val a1 = kotlin.math.atan2(to.y - pivot.y, to.x - pivot.x)
        var degrees = Math.toDegrees((a1 - a0).toDouble()).toFloat()
        if (degrees > 180f) degrees -= 360f
        if (degrees < -180f) degrees += 360f
        val target = edge.angle() + degrees
        val snapped = kotlin.math.round(target / 15f) * 15f
        return if (kotlin.math.abs(target - snapped) < 4f) snapped - edge.angle() else degrees
    }

    private class DragOutcome(val paths: List<ShapeFactory.EditablePath>, val selection: VectorSelection)

    private fun dragOutcome(kind: ShapeDrag, from: Offset, to: Offset, tolerance: Float): DragOutcome? {
        val g = dragGraph ?: return null
        val d = to - from
        return when (kind) {
            ShapeDrag.NODES -> {
                if (dragNodes.isEmpty()) return null
                DragOutcome(VectorEdit.moveNodes(g, dragNodes.associateWith { d }), VectorSelection(nodes = dragNodes.map { g.nodes[it].point + d }))
            }
            ShapeDrag.EDGE -> dragEdge?.let { e ->
                DragOutcome(VectorEdit.moveNodes(g, mapOf(e.a to d, e.b to d)), VectorSelection(edge = (e.start + d) to (e.end + d)))
            }
            ShapeDrag.BEND -> dragEdge?.let { e ->
                DragOutcome(VectorEdit.bendEdge(g, e, VectorEdit.bendHandle(e) + d), VectorSelection(edge = e.start to e.end))
            }
            ShapeDrag.TURN -> dragEdge?.let { e ->
                val degrees = turnDegrees(e, from, to)
                val paths = VectorEdit.rotateEdge(g, e, degrees)
                DragOutcome(paths, VectorSelection(edge = rotated(e, degrees)))
            }
            ShapeDrag.CONNECT -> {
                val base = dragBase ?: return null
                val start = dragFrom ?: return null
                if ((to - start).getDistance() <= tolerance) return null
                val bg = graphOf(base)
                val (paths, at, _) = anchor(bg, to, tolerance, exclude = start)
                val connected = VectorEdit.connect(VectorEdit.graph(paths), start, at, ::withLineStyle) ?: return null
                DragOutcome(connected, VectorSelection(nodes = listOf(at), linkFrom = at))
            }
            ShapeDrag.NONE, ShapeDrag.HANDLE -> null
        }
    }

    private fun rotated(e: VectorEdit.Edge, degrees: Float): Pair<Offset, Offset> {
        val pivot = (e.start + e.end) / 2f
        val rad = Math.toRadians(degrees.toDouble())
        val c = kotlin.math.cos(rad).toFloat()
        val s = kotlin.math.sin(rad).toFloat()
        fun rot(p: Offset) = Offset(pivot.x + (p.x - pivot.x) * c - (p.y - pivot.y) * s, pivot.y + (p.x - pivot.x) * s + (p.y - pivot.y) * c)
        return rot(e.start) to rot(e.end)
    }

    /** Sürükleme sürerken gösterilecek şekil (kaydedilmez). */
    fun dragPreview(kind: ShapeDrag, from: Offset, to: Offset, @Suppress("UNUSED_PARAMETER") path: List<Offset>, tolerance: Float): Stroke? {
        if (kind == ShapeDrag.HANDLE) return paramShapes.dragPreview(to)
        val stroke = selectedShape.value ?: return null
        val outcome = dragOutcome(kind, from, to, tolerance) ?: return null
        return ShapeFactory.fromEditable(stroke, outcome.paths)
    }

    /** Sürükleme bitti: sonuç tek geri alma adımı olarak kaydedilir. */
    fun endDrag(kind: ShapeDrag, from: Offset, to: Offset, @Suppress("UNUSED_PARAMETER") path: List<Offset>, tolerance: Float) {
        if (kind == ShapeDrag.HANDLE) return paramShapes.endDrag(to)
        val stroke = selectedShape.value ?: return
        val g = dragGraph ?: return
        val outcome = dragOutcome(kind, from, to, tolerance)
        dragGraph = null
        if (outcome == null) return
        commitPaths(stroke, g, outcome.paths, outcome.selection)
    }

    // --- Kes, sil ve çizgi ayarları ---

    /** Seçili noktalar bağlı çizgileriyle kesilebilir mi (kesilen ve kalan parçada çizgi olmalı). */
    fun canCut(): Boolean {
        val stroke = selectedShape.value ?: return false
        val g = graphOf(stroke)
        val ids = selectedNodeIds(g)
        if (ids.isEmpty()) return false
        val (rest, piece) = VectorEdit.cutNodes(g, ids)
        return VectorEdit.drawable(rest) && VectorEdit.drawable(piece)
    }

    /**
     * Kes: seçili noktalar ve onlara bağlı çizgiler şekilden çıkar, ayrı bir eleman olur; kesilen parça seçili olarak
     * Taşı moduna geçilir (Bitti → İncele).
     */
    fun cutSelected() {
        val stroke = selectedShape.value ?: return
        val spec = stroke.shape ?: return
        val current = store.canvas.value ?: return
        val g = graphOf(stroke)
        val ids = selectedNodeIds(g)
        if (ids.isEmpty()) return
        val (rest, piece) = VectorEdit.cutNodes(g, ids)
        if (!VectorEdit.drawable(rest) || !VectorEdit.drawable(piece)) return
        val kept = ShapeFactory.fromEditable(stroke, rest) ?: return
        // Kesilen parça serbest vektör yoludur (grafikse yeniden kurulmaz)
        val free = stroke.copy(shape = spec.copy(kind = com.karapp.drawguidance.ui.drawing.ShapeRegistry.FREE_PATH, graph = null))
        val cut = ShapeFactory.fromEditable(free, piece, id = UUID.randomUUID().toString()) ?: return
        val updated = ArrayList<Stroke>(current.strokes.size + 1)
        current.strokes.forEach { s -> if (s.id == stroke.id) { updated += kept; updated += cut } else updated += s }
        store.commit(updated.mapIndexed { i, s -> if (s.order == i) s else s.copy(order = i) })
        reset()
        host.inserted(setOf(cut.id))
    }

    /** Silinecek bir şey seçili mi (nokta ya da çizgi). */
    fun canDelete(): Boolean = _vector.value.nodes.isNotEmpty() || _vector.value.edge != null

    /** Seçili çizgiyi ya da seçili noktaları (bağlı çizgileriyle) siler; hiçbir şey kalmazsa şekil silinir. */
    fun deleteSelected() {
        val stroke = selectedShape.value ?: return
        val g = graphOf(stroke)
        val edge = selectedEdge(g)
        val paths = when {
            edge != null -> VectorEdit.deleteEdge(g, edge)
            else -> {
                val ids = selectedNodeIds(g)
                if (ids.isEmpty()) return
                VectorEdit.cutNodes(g, ids).first
            }
        }
        commitPaths(stroke, g, paths, VectorSelection())
    }

    /** Seçili çizginin eğimi (derece; seçili çizgi yoksa null). */
    fun selectedEdgeAngle(): Float? {
        val stroke = selectedShape.value ?: return null
        return selectedEdge(graphOf(stroke))?.angle()
    }

    /** Seçili çizgi kavisli mi. */
    fun selectedEdgeCurved(): Boolean {
        val stroke = selectedShape.value ?: return false
        return selectedEdge(graphOf(stroke))?.curved == true
    }

    /** Seçili çizgiyi [degrees] kadar döndürür (eğimi değişir). */
    fun turnSelectedEdge(degrees: Float) = editSelectedEdge { g, e -> VectorEdit.rotateEdge(g, e, degrees) to rotated(e, degrees) }

    /** Seçili çizginin eğimini [target] dereceye getirir (0 yatay, 90 dikey). */
    fun setSelectedEdgeAngle(target: Float) = editSelectedEdge { g, e ->
        var diff = (target - e.angle()) % 180f
        if (diff > 90f) diff -= 180f
        if (diff <= -90f) diff += 180f
        VectorEdit.rotateEdge(g, e, diff) to rotated(e, diff)
    }

    /** Seçili çizginin kavisini kaldırır. */
    fun straightenSelectedEdge() = editSelectedEdge { g, e -> VectorEdit.straighten(g, e) to (e.start to e.end) }

    private fun editSelectedEdge(block: (VectorEdit.Graph, VectorEdit.Edge) -> Pair<List<ShapeFactory.EditablePath>, Pair<Offset, Offset>>) {
        val stroke = selectedShape.value ?: return
        val g = graphOf(stroke)
        val edge = selectedEdge(g) ?: return
        val (paths, ends) = block(g, edge)
        commitPaths(stroke, g, paths, VectorSelection(edge = ends))
    }

    private companion object {
        /** Noktaya ve çizgiye dokunma payı (dokunma payının katı). */
        const val NODE_HIT = 1.8f
        const val EDGE_HIT = 1.3f
    }
}

/** v37 kova imlecinin altındaki şekil ([strokeId]) ve boyanmaya hazır alanı ([region], tuval koordinatı; kapalı değilse null). */
data class PaintTarget(val strokeId: String, val region: List<Offset>?)

/** Renk çok koyu mu (göreli parlaklık). Okunamayan renk koyu sayılmaz. */
internal fun darkHex(hex: String): Boolean {
    val v = hex.removePrefix("#").takeIf { it.length >= 6 }?.substring(0, 6)?.toLongOrNull(16) ?: return false
    val r = (v shr 16 and 0xFF) / 255f; val g = (v shr 8 and 0xFF) / 255f; val b = (v and 0xFF) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b < 0.2f
}
