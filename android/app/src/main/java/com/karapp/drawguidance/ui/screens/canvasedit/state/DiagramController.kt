package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.DiagramBox
import com.karapp.drawguidance.data.model.DiagramEdge
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramNode
import com.karapp.drawguidance.data.model.DiagramSide
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.DiagramCatalog
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import com.karapp.drawguidance.ui.screens.canvasedit.DiagramFocus
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/**
 * Ok ekleme durumu: [fromId] null ise önce okun başlayacağı adım seçilir. [fromSide] okun adımın hangi
 * tarafından çıkacağı (otomatik: karşıya bakan taraf).
 */
data class DiagramLink(val fromId: String?, val fromSide: DiagramSide = DiagramSide.AUTO)

/**
 * Diyagramlar: türü seçip yerleştirme ve tuvalin üstünde düzenleme. Adıma ya da oka dokunulunca odaklanır;
 * odaktaki adımın dört tarafındaki tutamaklardan ok çekilir (sürükleyerek ya da tutamağa ve hedef adıma
 * dokunarak); ok hedef adımın dokunulan tarafına bağlanır. Her ekleme/silme ayrı geri alma adımıdır.
 */
class DiagramController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val shapes: ShapeController,
) {
    private val _kind = MutableStateFlow(DiagramKind.FLOW)
    val kind: StateFlow<DiagramKind> = _kind.asStateFlow()

    private val _isPickerOpen = MutableStateFlow(false)
    val isPickerOpen: StateFlow<Boolean> = _isPickerOpen.asStateFlow()

    /** Seçili tek diyagram elemanı. */
    val selected: StateFlow<Stroke?> = combine(store.canvas, store.selectedIds) { canvas, ids ->
        if (ids.size != 1) null else canvas?.strokes?.firstOrNull { it.id in ids && it.diagram != null }
    }.stateIn(host.scope, SharingStarted.Eagerly, null)

    private val _focus = MutableStateFlow<DiagramFocus?>(null)
    val focus: StateFlow<DiagramFocus?> = _focus.asStateFlow()

    private val _link = MutableStateFlow<DiagramLink?>(null)
    /** Ok ekleme sürüyorsa kaynağı (null: ok eklenmiyor). */
    val link: StateFlow<DiagramLink?> = _link.asStateFlow()
    val linking: StateFlow<Boolean> = _link.map { it != null }.stateIn(host.scope, SharingStarted.Eagerly, false)

    /** Yeni diyagramların renk teması ([com.karapp.drawguidance.ui.drawing.DiagramThemes]; "" = tek renk). */
    private val _theme = MutableStateFlow(com.karapp.drawguidance.ui.drawing.DiagramThemes.EDITORIAL)
    val theme: StateFlow<String> = _theme.asStateFlow()

    fun openPicker() { _isPickerOpen.value = true }
    fun closePicker() { _isPickerOpen.value = false }

    /** Tema: yeni diyagramlarda kullanılır ve seçili diyagrama hemen uygulanır. */
    fun setTheme(value: String) {
        _theme.value = value
        selected.value?.let { d -> update(d.id) { it.copy(theme = value) } }
    }

    /** Diyagram türü seçilince Diyagram moduna geçilir: tuvalde sürükleyerek ya da dokunarak yerleştirilir. */
    fun selectKind(value: DiagramKind) {
        _kind.value = value
        _isPickerOpen.value = false
        host.startAction(EditMode.DIAGRAM)
    }

    fun add(gestureId: String, start: Offset, end: Offset) {
        val current = store.canvas.value ?: return
        val minSide = minOf(current.width, current.height)
        var left = minOf(start.x, end.x); var top = minOf(start.y, end.y)
        var right = maxOf(start.x, end.x); var bottom = maxOf(start.y, end.y)
        val kind = _kind.value
        if (right - left < minSide * 0.2f || bottom - top < minSide * 0.2f) {
            // Dokunarak eklenince türün kendi en-boy oranıyla (geniş türler yatay, grafikler kareye yakın)
            val aspect = DiagramCatalog.aspect(kind)
            val w = if (aspect == null) minSide * 0.6f else minOf(current.width * 0.86f, minSide * 0.62f * kotlin.math.sqrt(aspect))
            val h = if (aspect == null) minSide * 0.7f else w / aspect
            val cx = (left + right) / 2f; val cy = (top + bottom) / 2f
            left = cx - w / 2f; right = cx + w / 2f; top = cy - h / 2f; bottom = cy + h / 2f
        }
        val nodes = DiagramRenderer.layout(kind, DiagramRenderer.defaultNodes(kind))
        // v50 örnek yazılar uygulamanın dilinde eklenir (sayılar ve kodlar korunur)
        val edges = DiagramRenderer.defaultEdges(kind, nodes).map { DiagramCatalog.localize(it) }
        val spec = DiagramSpec(
            kind = kind, nodes = nodes.map { it.copy(text = DiagramCatalog.localizeText(it.text)) }, edges = edges, style = shapes.newShapeStyle(), theme = _theme.value,
            manual = DiagramCatalog.template(kind)?.manual ?: false,
        )
        val id = UUID.nameUUIDFromBytes("$gestureId-diagram".toByteArray()).toString()
        val stroke = Stroke(
            id = id,
            points = listOf(StrokePoint(left, top), StrokePoint(right, top), StrokePoint(right, bottom), StrokePoint(left, bottom)),
            colorHex = spec.style.strokeColorHex,
            brushType = BrushType.DIAGRAM,
            sizePx = spec.style.strokeWidth,
            opacity = 1f,
            order = current.strokes.size,
            diagram = spec,
        )
        store.commit(current.strokes + stroke)
        host.inserted(setOf(id))
    }

    fun addAt(gestureId: String, center: Offset) = add(gestureId, center, center)

    /**
     * Diyagramı değiştirir. Her yapısal değişiklik (adım/ok ekleme, silme, kutu biçimi) ayrı geri alma adımıdır;
     * yazı düzenlemesi gibi art arda gelenler [coalesce] anahtarıyla tek adımda birleşir.
     */
    fun update(strokeId: String, coalesce: String? = null, block: (DiagramSpec) -> DiagramSpec) {
        val current = store.canvas.value ?: return
        var changed = false
        val updated = current.strokes.map { stroke ->
            val spec = stroke.diagram
            if (stroke.id != strokeId || spec == null) return@map stroke
            val next = block(spec)
            if (next == spec) stroke else { changed = true; stroke.copy(diagram = next) }
        }
        if (changed) store.commit(updated, coalesce = coalesce)
    }

    /** Diyagrama yeni adım (düğüm) ekler; yerleşim türe göre yeniden hesaplanır. */
    fun addNode(strokeId: String, text: String) = update(strokeId) { spec ->
        val id = "n${spec.nodes.size + 1}-${UUID.randomUUID().toString().take(4)}"
        val last = spec.nodes.lastOrNull()
        val node = DiagramNode(
            id = id, text = text.ifBlank { DiagramCatalog.newNodeText(spec.kind, spec.nodes.size + 1, last?.text) },
            x = 0.5f, y = 0.5f, w = 0.3f, h = 0.12f,
            box = last?.box ?: DiagramBox.ROUNDED, role = DiagramCatalog.inheritRole(last?.role),
        )
        spec.copy(nodes = DiagramRenderer.layout(spec.kind, spec.nodes + node))
    }

    /** Ok ekler; aynı ok varsa yalnızca bağlandığı tarafları günceller. */
    fun addEdge(
        strokeId: String, from: String, to: String, label: String = "",
        fromSide: DiagramSide = DiagramSide.AUTO, toSide: DiagramSide = DiagramSide.AUTO,
    ) = update(strokeId) { spec ->
        if (from == to || spec.nodes.none { it.id == from } || spec.nodes.none { it.id == to }) return@update spec
        val existing = spec.edges.indexOfFirst { it.from == from && it.to == to }
        if (existing >= 0) {
            spec.copy(edges = spec.edges.mapIndexed { i, e -> if (i == existing) e.copy(fromSide = fromSide, toSide = toSide) else e })
        } else {
            spec.copy(edges = spec.edges + DiagramEdge(from, to, label, fromSide = fromSide, toSide = toSide))
        }
    }

    fun removeNode(strokeId: String, nodeId: String) = update(strokeId) { spec ->
        if (spec.nodes.size <= 1) spec else {
            val remaining = spec.nodes.filterNot { it.id == nodeId }
            val nodes = if (spec.manual) remaining else DiagramRenderer.layout(spec.kind, remaining)
            val incoming = spec.edges.filter { it.to == nodeId && it.from != nodeId }
            val outgoing = spec.edges.filter { it.from == nodeId && it.to != nodeId }
            var edges = spec.edges.filterNot { it.from == nodeId || it.to == nodeId }
            // Zincirin ortasındaki adım silinince zincir kopmaz: önceki adım sonrakine (aynı taraflarla) bağlanır
            if (incoming.size == 1 && outgoing.size == 1) {
                val a = incoming.single()
                val b = outgoing.single()
                if (a.from != b.to && edges.none { it.from == a.from && it.to == b.to }) {
                    edges = edges + a.copy(to = b.to, toSide = b.toSide)
                }
            }
            spec.copy(nodes = nodes, edges = edges)
        }
    }

    fun removeEdge(strokeId: String, index: Int) = update(strokeId) { spec ->
        if (index !in spec.edges.indices) spec else spec.copy(edges = spec.edges.filterIndexed { i, _ -> i != index })
    }

    fun renameNode(strokeId: String, nodeId: String, text: String) = update(strokeId, coalesce = "diagram-text-$strokeId-$nodeId") { spec ->
        val nodes = spec.nodes.map { if (it.id == nodeId) it.copy(text = text) else it }
        // Nicel türlerde değerler yazıda: yazı değişince grafik yeniden yerleşir
        spec.copy(nodes = if (DiagramCatalog.isData(spec.kind) && !spec.manual) DiagramRenderer.layout(spec.kind, nodes) else nodes)
    }

    /** Odaktaki adımı vurgular ya da vurguyu kaldırır (editoryal tarzda tek vurgu rengi). */
    fun toggleFocusedFocal() {
        val stroke = selected.value ?: return
        val focus = _focus.value as? DiagramFocus.Node ?: return
        update(stroke.id) { spec ->
            spec.copy(nodes = spec.nodes.map { n ->
                if (n.id != focus.id || DiagramCatalog.isStructural(n.role)) n
                else n.copy(role = if (n.role == "focal") null else "focal")
            })
        }
    }

    /** Odaktaki adım vurgulu mu (null: vurgulanamaz). */
    fun focusedFocal(): Boolean? {
        val spec = selected.value?.diagram ?: return null
        val focus = _focus.value as? DiagramFocus.Node ?: return null
        val node = spec.nodes.firstOrNull { it.id == focus.id } ?: return null
        return if (DiagramCatalog.isStructural(node.role)) null else node.role == "focal"
    }

    fun setNodeBox(strokeId: String, nodeId: String, box: DiagramBox) = update(strokeId) { spec ->
        spec.copy(nodes = spec.nodes.map { if (it.id == nodeId) it.copy(box = box) else it })
    }

    // --- Tuval üstünde düzenleme ---

    /** Seçili diyagramı tuval üstünde düzenlemeye başlar. */
    fun editSelected() {
        val diagram = store.selectedStroke { it.brushType == com.karapp.drawguidance.data.model.BrushType.DIAGRAM } ?: return
        reset()
        // Düzenlenirken yalnızca bu diyagram seçili; bitince Seç moduna dönülür
        host.startAction(EditMode.DIAGRAM_EDIT, setOf(diagram.id))
    }

    fun onModeChange(to: EditMode) {
        if (to != EditMode.DIAGRAM_EDIT) reset()
    }

    fun reset() {
        _focus.value = null
        _link.value = null
    }

    /** Geri alma sonrası: odak sıfırlanır; düzenlenen diyagram kalmadıysa Seç moduna dönülür. */
    fun onContentReplaced() {
        reset()
        if (host.mode.value == EditMode.DIAGRAM_EDIT) {
            val current = store.canvas.value ?: return
            if (current.strokes.none { it.id in store.selection && it.diagram != null }) host.endAction()
        }
    }

    /** Ok tutamaklarının göründüğü (sürüklemenin başlayabileceği) adım: odaktaki ya da ok kaynağı. */
    private fun handleNodeId(): String? = _link.value?.fromId ?: (_focus.value as? DiagramFocus.Node)?.id

    /** Sürükleme bu noktadan başlarsa ok çekilir mi (odaktaki adımın bir tutamağı). */
    fun hasHandleAt(point: Offset, tolerance: Float): Boolean {
        val stroke = selected.value ?: return false
        val id = handleNodeId() ?: return false
        return DiagramRenderer.portAt(stroke, point, tolerance * PORT_REACH, only = id) != null
    }

    /**
     * Düzenlenen diyagrama dokunuş:
     * - Ok eklerken: kaynak yoksa dokunulan adım (ve tutamağa dokunulduysa tarafı) kaynak olur; kaynağın bir
     *   tutamağına dokunmak çıkış tarafını değiştirir; başka bir adıma dokunmak oku o adımın dokunulan tarafına
     *   bağlar; boşa dokunmak ok eklemeyi bırakır.
     * - Aksi halde odaktaki adımın bir tutamağına dokunmak o taraftan ok eklemeyi başlatır; adıma ya da oka
     *   dokunmak onu odaklar.
     */
    fun tap(point: Offset, tolerance: Float) {
        val stroke = selected.value ?: return
        val reach = tolerance * PORT_REACH
        val link = _link.value
        if (link != null) {
            val sourceId = link.fromId
            if (sourceId == null) {
                val (node, side) = attachAt(stroke, point, reach) ?: run { _link.value = null; return }
                _focus.value = DiagramFocus.Node(node.id)
                _link.value = DiagramLink(node.id, side)
                return
            }
            val target = attachAt(stroke, point, reach)
            when {
                // Diyagramın içinde boşluğa dokunmak orada yeni adım açar ve oku ona bağlar
                target == null && insideDiagram(stroke, point) -> connectToNewNode(stroke, sourceId, link.fromSide, point)
                target == null -> _link.value = null
                // Kaynağa yeniden dokunmak okun çıkacağı tarafı değiştirir
                target.first.id == sourceId -> _link.value = link.copy(fromSide = target.second)
                else -> connect(stroke, sourceId, link.fromSide, target.first, target.second)
            }
            return
        }
        val focusedNode = (_focus.value as? DiagramFocus.Node)?.id
        if (focusedNode != null) {
            DiagramRenderer.portAt(stroke, point, reach, only = focusedNode)?.let { port ->
                _link.value = DiagramLink(focusedNode, port.second)
                return
            }
        }
        val node = DiagramRenderer.nodeAt(stroke, point)
        if (node != null) {
            _focus.value = DiagramFocus.Node(node.id)
            return
        }
        val edge = DiagramRenderer.edgeAt(stroke, point, tolerance * 1.6f)
        _focus.value = edge?.let { DiagramFocus.Edge(it) }
    }

    /**
     * Okun bağlanacağı adım ve taraf: adımın içine dokunulduysa kenarına yakınlığa göre taraf (ortası otomatik),
     * adımın hemen dışındaki bir tutamağa dokunulduysa o tutamağın tarafı.
     */
    private fun attachAt(stroke: Stroke, point: Offset, reach: Float, exclude: String? = null): Pair<DiagramNode, DiagramSide>? {
        DiagramRenderer.nodeAt(stroke, point)?.takeIf { it.id != exclude }?.let { node ->
            return node to DiagramRenderer.sideNear(stroke, node, point)
        }
        return DiagramRenderer.portAt(stroke, point, reach)?.takeIf { it.first.id != exclude }
    }

    /**
     * Odaktaki adımın tutamağından sürükleyerek ok çekme: [from] tutamağın yakını, [to] parmağın kalktığı yer.
     * Hedef adımın parmağın bırakıldığı kenarına bağlanır (ortası otomatik); boşa bırakılırsa ok eklenmez.
     */
    fun dragLink(from: Offset, to: Offset, tolerance: Float) {
        val stroke = selected.value ?: return
        val sourceId = handleNodeId() ?: return
        val port = DiagramRenderer.portAt(stroke, from, tolerance * PORT_REACH, only = sourceId) ?: return
        val target = attachAt(stroke, to, tolerance * PORT_REACH, exclude = sourceId)
        if (target == null) {
            // Boşluğa bırakılan ok: bırakılan yerde yeni adım açılır ve ok ona bağlanır
            if (DiagramRenderer.nodeAt(stroke, to) == null) connectToNewNode(stroke, sourceId, port.second, to)
            else _link.value = null
            return
        }
        connect(stroke, sourceId, port.second, target.first, target.second)
    }

    /** Nokta diyagram elemanının dörtgeninin içinde mi. */
    private fun insideDiagram(stroke: Stroke, point: Offset): Boolean {
        val f = stroke.diagram?.frame ?: return false
        val u = DiagramRenderer.toUnit(stroke, point) ?: return false
        return u.x in f.left..f.right && u.y in f.top..f.bottom
    }

    /**
     * [point] (tuval) noktasına yeni adım ekler ve [sourceId] adımından ona ok çeker (tek geri alma adımı). Adım
     * diyagramın dışına düşerse diyagramın çerçevesi büyür; bundan sonra adımların yerleri elle korunur.
     */
    private fun connectToNewNode(stroke: Stroke, sourceId: String, fromSide: DiagramSide, point: Offset) {
        _link.value = null
        val current = store.canvas.value ?: return
        val spec = stroke.diagram ?: return
        val source = spec.nodes.firstOrNull { it.id == sourceId } ?: return
        val clamped = Offset(point.x.coerceIn(0f, current.width.toFloat()), point.y.coerceIn(0f, current.height.toFloat()))
        val center = DiagramRenderer.toUnit(stroke, clamped) ?: return
        val id = "n${spec.nodes.size + 1}-${UUID.randomUUID().toString().take(4)}"
        val node = DiagramNode(
            id = id, text = com.karapp.drawguidance.i18n.I18n.trf("Adım {0}", spec.nodes.size + 1),
            x = center.x - source.w / 2f, y = center.y - source.h / 2f, w = source.w, h = source.h,
            box = if (source.box == DiagramBox.PILL || source.box == DiagramBox.DIAMOND) DiagramBox.ROUNDED else source.box,
        )
        val next = spec.copy(nodes = spec.nodes + node, edges = spec.edges + DiagramEdge(sourceId, id, fromSide = fromSide), manual = true)
        commitGrown(stroke, next, node)
        _focus.value = DiagramFocus.Node(id)
    }

    /** Diyagramı [next] ile değiştirir; [node] çerçevenin dışındaysa çerçeve (ve eleman) büyür. */
    private fun commitGrown(stroke: Stroke, next: DiagramSpec, node: DiagramNode) {
        val current = store.canvas.value ?: return
        val grown = com.karapp.drawguidance.data.diagram.DiagramFrames.grow(stroke.points, next, node.x, node.y, node.x + node.w, node.y + node.h)
        val q = com.karapp.drawguidance.data.local.ContentCodec::quantize
        val updated = if (grown == null) stroke.copy(diagram = next)
        else stroke.copy(points = grown.first.map { StrokePoint(q(it.x), q(it.y)) }, diagram = grown.second)
        store.commit(current.strokes.map { if (it.id == stroke.id) updated else it })
    }

    private fun connect(stroke: Stroke, sourceId: String, fromSide: DiagramSide, target: DiagramNode, toSide: DiagramSide) {
        _link.value = null
        addEdge(stroke.id, sourceId, target.id, fromSide = fromSide, toSide = toSide)
        // Yeni (ya da güncellenen) ok odaklanır: yazısı ve tarafları hemen verilebilir
        val index = selected.value?.diagram?.edges?.indexOfFirst { it.from == sourceId && it.to == target.id } ?: -1
        _focus.value = if (index >= 0) DiagramFocus.Edge(index) else DiagramFocus.Node(target.id)
    }

    /** Odaktaki adımdan sonra yeni adım ekler (zincirdeyse ya da ok odaktaysa araya girer) ve ona odaklanır. */
    fun addStep() {
        val stroke = selected.value ?: return
        val spec = stroke.diagram ?: return
        val focusedEdge = (_focus.value as? DiagramFocus.Edge)?.let { spec.edges.getOrNull(it.index) }
        val focusId = focusedEdge?.from ?: (_focus.value as? DiagramFocus.Node)?.id ?: spec.nodes.lastOrNull()?.id
        val id = "n${spec.nodes.size + 1}-${UUID.randomUUID().toString().take(4)}"
        _link.value = null
        val current = spec
        val at = current.nodes.indexOfFirst { it.id == focusId }.let { if (it < 0) current.nodes.size else it + 1 }
        val focused = current.nodes.firstOrNull { it.id == focusId }
        val box = focused?.box ?: DiagramBox.ROUNDED
        val node = DiagramNode(
            id = id, text = DiagramCatalog.newNodeText(current.kind, current.nodes.size + 1, focused?.text), x = 0.5f, y = 0.5f, w = 0.3f, h = 0.12f,
            box = when (box) {
                DiagramBox.PILL, DiagramBox.DIAMOND -> DiagramBox.ROUNDED
                // Kapsayıcılardan (bölge, kulvar, sütun) sonra eklenen adım kart olur
                DiagramBox.GROUP, DiagramBox.LANE, DiagramBox.COLUMN -> if (current.kind == DiagramKind.KANBAN || current.kind == DiagramKind.STORY_MAP) DiagramBox.CARD else DiagramBox.ROUNDED
                else -> box
            },
            role = DiagramCatalog.inheritRole(focused?.role),
        )
        // Elle yerleştirilmiş diyagramda yeni adım odaktaki adımın yanına konur (diğerleri yerinde kalır)
        val placed = if (current.manual) placeNear(current, node, current.nodes.firstOrNull { it.id == focusId }) else node
        val nodes = current.nodes.toMutableList().apply { add(at, placed) }
        val edges = if (focusId == null || current.kind in DiagramCatalog.noEdgeKinds || node.box == DiagramBox.CARD && current.kind == DiagramKind.KANBAN) current.edges else {
            val outgoing = if (focusedEdge != null) listOf(focusedEdge) else current.edges.filter { it.from == focusId }
            if (outgoing.size == 1) {
                // Zincirin arasına girer: a→b yerine a→yeni→b (a'nın çıkış tarafı ve b'nin giriş tarafı korunur)
                val next = outgoing.single()
                current.edges.map { if (it === next) next.copy(to = id, toSide = DiagramSide.AUTO) else it } +
                    next.copy(from = id, label = "", fromSide = DiagramSide.AUTO)
            } else current.edges + DiagramEdge(focusId, id)
        }
        if (current.manual) {
            // Tek geri alma adımı: adım, oklar ve (taşarsa) büyüyen çerçeve birlikte
            commitGrown(stroke, current.copy(nodes = nodes, edges = edges), placed)
        } else {
            update(stroke.id) { it.copy(nodes = DiagramRenderer.layout(it.kind, nodes), edges = edges) }
        }
        _focus.value = DiagramFocus.Node(id)
    }

    /** Yeni adımın yeri: [near] adımının altı, doluysa sağı, solu ya da üstü. */
    private fun placeNear(spec: DiagramSpec, node: DiagramNode, near: DiagramNode?): DiagramNode {
        val ref = near ?: spec.nodes.lastOrNull() ?: return node
        if (ref.box == DiagramBox.GROUP || ref.box == DiagramBox.LANE || ref.box == DiagramBox.COLUMN) placeInside(spec, node, ref)?.let { return it }
        val w = ref.w
        val h = ref.h
        val candidates = listOf(
            ref.x to ref.y + h * 1.8f, ref.x + w * 1.4f to ref.y, ref.x - w * 1.4f to ref.y, ref.x to ref.y - h * 1.8f,
            ref.x + w * 1.4f to ref.y + h * 1.8f,
        )
        fun overlaps(x: Float, y: Float) = spec.nodes.any { o ->
            x < o.x + o.w + 0.01f && x + w > o.x - 0.01f && y < o.y + o.h + 0.01f && y + h > o.y - 0.01f
        }
        val (x, y) = candidates.firstOrNull { (cx, cy) -> !overlaps(cx, cy) } ?: candidates.first()
        return node.copy(x = x, y = y, w = w, h = h)
    }

    /** Kapsayıcının (bölge, kulvar, kanban sütunu) içinde ilk boş yer; adımın boyu diğer adımlardan. */
    private fun placeInside(spec: DiagramSpec, node: DiagramNode, box: DiagramNode): DiagramNode? {
        val containers = setOf(DiagramBox.GROUP, DiagramBox.LANE, DiagramBox.COLUMN)
        val others = spec.nodes.filter { it.box !in containers }
        val w = (others.map { it.w }.sorted().getOrNull(others.size / 2) ?: 0.2f).coerceAtMost(box.w - 0.02f).coerceAtLeast(0.08f)
        val h = (others.map { it.h }.sorted().getOrNull(others.size / 2) ?: 0.12f).coerceAtMost(box.h - 0.02f).coerceAtLeast(0.05f)
        val pad = 0.01f
        val startX = if (box.box == DiagramBox.LANE) box.x + box.w * 0.16f else box.x + pad
        val startY = box.y + if (box.box == DiagramBox.LANE) (box.h - h) / 2f else 0.11f * box.h.coerceAtMost(1f)
        fun free(x: Float, y: Float) = others.none { o -> x < o.x + o.w + 0.008f && x + w > o.x - 0.008f && y < o.y + o.h + 0.008f && y + h > o.y - 0.008f }
        var y = startY
        while (y + h <= box.y + box.h + 1e-4f) {
            var x = startX
            while (x + w <= box.x + box.w + 1e-4f) {
                if (free(x, y)) return node.copy(x = x, y = y, w = w, h = h)
                x += w + 0.015f
            }
            y += h + 0.02f
        }
        return null
    }

    /**
     * Odaktaki adımın ya da okun rengi: adımda [part] dolgu/kenar/yazı, okta çizgi rengi (null: diyagramın
     * rengine döner). Kaydırıcı sürüklemesi gibi art arda değişiklikler tek geri alma adımıdır.
     */
    fun setFocusedColor(part: ColorPart, hex: String?) {
        val stroke = selected.value ?: return
        when (val focus = _focus.value) {
            is DiagramFocus.Node -> update(stroke.id, coalesce = "diagram-color-${stroke.id}-${focus.id}-$part") { spec ->
                spec.copy(nodes = spec.nodes.map { n ->
                    if (n.id != focus.id) n else when (part) {
                        ColorPart.FILL -> n.copy(fillColorHex = hex)
                        ColorPart.STROKE -> n.copy(strokeColorHex = hex)
                        ColorPart.TEXT -> n.copy(textColorHex = hex)
                    }
                })
            }
            is DiagramFocus.Edge -> update(stroke.id, coalesce = "diagram-color-${stroke.id}-e${focus.index}") { spec ->
                spec.copy(edges = spec.edges.mapIndexed { i, e -> if (i == focus.index) e.copy(colorHex = hex) else e })
            }
            null -> Unit
        }
    }

    /** Odaktaki öğenin şu anki rengi (renk penceresinin başlangıcı). */
    fun focusedColor(part: ColorPart): String? {
        val stroke = selected.value ?: return null
        val spec = stroke.diagram ?: return null
        return when (val focus = _focus.value) {
            is DiagramFocus.Node -> spec.nodes.firstOrNull { it.id == focus.id }?.let { n ->
                when (part) {
                    ColorPart.FILL -> n.fillColorHex ?: spec.style.fillColorHex ?: "#FFFFFF"
                    ColorPart.STROKE -> n.strokeColorHex ?: spec.style.strokeColorHex
                    ColorPart.TEXT -> n.textColorHex ?: n.strokeColorHex ?: spec.style.strokeColorHex
                }
            }
            is DiagramFocus.Edge -> spec.edges.getOrNull(focus.index)?.let { it.colorHex ?: spec.style.strokeColorHex }
            null -> null
        }
    }

    /** Renk verilen kısım: adımın dolgusu, kenarı ya da yazısı. */
    enum class ColorPart { FILL, STROKE, TEXT }

    /**
     * Ok eklemeyi başlatır (ya da bırakır): odakta bir adım varsa ok ondan başlar; yoksa önce okun başlayacağı
     * adıma, sonra gideceği adıma dokunulur.
     */
    fun startLink() {
        if (_link.value != null) {
            _link.value = null
            return
        }
        val focusedNode = (_focus.value as? DiagramFocus.Node)?.id
        if (focusedNode == null) _focus.value = null
        _link.value = DiagramLink(focusedNode)
    }

    fun renameFocused(text: String) {
        val stroke = selected.value ?: return
        when (val focus = _focus.value) {
            is DiagramFocus.Node -> renameNode(stroke.id, focus.id, text)
            is DiagramFocus.Edge -> update(stroke.id, coalesce = "diagram-edge-${stroke.id}-${focus.index}") { spec ->
                spec.copy(edges = spec.edges.mapIndexed { i, e -> if (i == focus.index) e.copy(label = text) else e })
            }
            null -> Unit
        }
    }

    fun setFocusedBox(box: DiagramBox) {
        val stroke = selected.value ?: return
        val focus = _focus.value as? DiagramFocus.Node ?: return
        setNodeBox(stroke.id, focus.id, box)
    }

    /** Odaktaki okun bağlandığı taraflar (null: değişmez). */
    fun setFocusedEdgeSides(fromSide: DiagramSide? = null, toSide: DiagramSide? = null) {
        val stroke = selected.value ?: return
        val focus = _focus.value as? DiagramFocus.Edge ?: return
        update(stroke.id) { spec ->
            spec.copy(edges = spec.edges.mapIndexed { i, e ->
                if (i != focus.index) e else e.copy(fromSide = fromSide ?: e.fromSide, toSide = toSide ?: e.toSide)
            })
        }
    }

    /** Odaktaki okun yönünü çevirir (taraflar da yer değiştirir). */
    fun flipFocusedEdge() {
        val stroke = selected.value ?: return
        val focus = _focus.value as? DiagramFocus.Edge ?: return
        update(stroke.id) { spec ->
            val edge = spec.edges.getOrNull(focus.index) ?: return@update spec
            if (spec.edges.any { it.from == edge.to && it.to == edge.from }) return@update spec
            spec.copy(edges = spec.edges.mapIndexed { i, e ->
                if (i != focus.index) e else e.copy(from = e.to, to = e.from, fromSide = e.toSide, toSide = e.fromSide)
            })
        }
    }

    fun deleteFocused() {
        val stroke = selected.value ?: return
        when (val focus = _focus.value) {
            is DiagramFocus.Node -> removeNode(stroke.id, focus.id)
            is DiagramFocus.Edge -> removeEdge(stroke.id, focus.index)
            null -> return
        }
        reset()
    }

    private companion object {
        /** Tutamağa dokunma payı (dokunma toleransının katı). */
        const val PORT_REACH = 1.6f
    }
}
