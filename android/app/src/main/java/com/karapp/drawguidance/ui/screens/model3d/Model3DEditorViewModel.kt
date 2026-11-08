package com.karapp.drawguidance.ui.screens.model3d

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karapp.drawguidance.content.model3d.Camera3
import com.karapp.drawguidance.content.model3d.Catalog3D
import com.karapp.drawguidance.content.model3d.DrawPlane
import com.karapp.drawguidance.content.model3d.EdgeRef
import com.karapp.drawguidance.content.model3d.Material
import com.karapp.drawguidance.content.model3d.MaterialKind
import com.karapp.drawguidance.content.model3d.Mesh
import com.karapp.drawguidance.content.model3d.Group3
import com.karapp.drawguidance.content.model3d.MeshOps
import com.karapp.drawguidance.content.model3d.MeshTopo
import com.karapp.drawguidance.content.model3d.Solid3
import com.karapp.drawguidance.content.model3d.TopoResult
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.ModelEdit
import com.karapp.drawguidance.content.model3d.Part
import com.karapp.drawguidance.content.model3d.Pick3
import com.karapp.drawguidance.content.model3d.Primitives
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.content.model3d.PrimitiveSpec
import com.karapp.drawguidance.content.model3d.SceneSettings
import com.karapp.drawguidance.content.model3d.Stroke3
import com.karapp.drawguidance.content.model3d.Transform3
import com.karapp.drawguidance.content.model3d.V3
import com.karapp.drawguidance.content.model3d.View3
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.library.Model3DResults
import com.karapp.drawguidance.data.library.TextureRepository
import com.karapp.drawguidance.data.settings.Model3DPrefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.exp

/** 3B düzenleyicinin araçları (tuvaldeki modlar gibi). */
enum class Tool3 {
    /** Sahneyi döndür: tek parmak çevirir, iki parmak kaydırır/yakınlaştırır. */
    VIEW,
    /** Parçayı seç ve taşı. */
    MOVE,
    /** Parçayı döndür. */
    ROTATE,
    /** Parçayı ölçekle. */
    SCALE,
    /** v25 Nokta: kenara dokununca nokta ekler, noktaya dokununca seçer; sil, birleştir (kenar), halkadan kes. */
    POINT,
    /** v24 Nokta/Kenar: kenar seç (eğ, ger, it, yuvarlat), basılı tutarak nokta ekle, noktayı sürükle. */
    EDGE,
    /** Yüz düzenleme (yükselt, içe al, büyüt/küçült, boya, sil). */
    FACE,
    /** Yüz/parça boyama. */
    PAINT,
    /** 3B düzleme fırçayla çizim. */
    DRAW,
    /** Çizgi sil. */
    ERASE,
}

/**
 * v26 düzenleyici modları (tuvaldeki gibi): alttaki mod çubuğundan seçilir; her modun alt araçları ve araç satırı vardır.
 * Seç: taşı/çevir/ölçekle, grupla, katı cisim işlemleri · Düzenle: nokta, kenar, yüz · Çiz: fırça ve silgi · Boya ·
 * Ekle: cisimler ve hazır şekiller.
 */
enum class Mode3(val tools: List<Tool3>) {
    SELECT(listOf(Tool3.MOVE, Tool3.ROTATE, Tool3.SCALE)),
    EDIT(listOf(Tool3.POINT, Tool3.EDGE, Tool3.FACE)),
    DRAW(listOf(Tool3.DRAW, Tool3.ERASE)),
    PAINT(listOf(Tool3.PAINT)),
    ADD(listOf(Tool3.MOVE)),
    ;

    companion object {
        fun of(t: Tool3): Mode3 = when (t) {
            Tool3.VIEW, Tool3.MOVE, Tool3.ROTATE, Tool3.SCALE -> SELECT
            Tool3.POINT, Tool3.EDGE, Tool3.FACE -> EDIT
            Tool3.DRAW, Tool3.ERASE -> DRAW
            Tool3.PAINT -> PAINT
        }
    }
}

/** Açık yan panel (v24: Sahne yerine düzenli Ayarlar). */
enum class Panel3 { ADD, PARTS, MATERIAL, SHAPE, SETTINGS, BRUSH, OPS }

/** v25 Yüz aracında seçim: tek yüz ya da dokunulan yüzün bütün yüzeyi (silindirin yanı, kürenin tamamı…). */
enum class FaceMode { SINGLE, SURFACE }

/**
 * v27 Boya kapsamı: dokunulan tek yüz, dokunulan yüzün bütün yüzeyi (silindirin yanı, kutunun bir yüzü…), parçanın
 * tamamı ya da modeldeki bütün parçalar.
 */
enum class PaintScope { FACE, SURFACE, PART, ALL }

/** v25 parçalama düzlemi: dünya eksenleri ya da ekrana dik (bakış yönünde). */
enum class SliceAxis { X, Y, Z, VIEW }

/**
 * v25 3B fırça: tür (çizgi ya da cisim), renk, kalınlık (ekran pikseli), duvar yüksekliği (dünya birimi), şişkinlik,
 * uç incelmesi, yumuşatma, kalem basıncı, malzeme türü ve örtücülük.
 */
data class Brush3(
    val kind: com.karapp.drawguidance.content.model3d.Brush3Kind = com.karapp.drawguidance.content.model3d.Brush3Kind.LINE,
    val height: Float = 0.5f,
    val bulge: Float = 0.8f,
    val taper: Float = 0.35f,
    val smoothing: Float = 0.4f,
    val pressure: Boolean = true,
    val material: MaterialKind = MaterialKind.PLASTIC,
    val opacity: Float = 1f,
)

/**
 * v25 resim olarak dışa aktarma ayarları: en uzun kenar, oran (1 kare, 0,75 dikey, 1,333 yatay), arka plan (0 saydam),
 * zemin ızgarası, gölge, kenar çizgileri, açı (şu anki görünüm ya da modele sığdır), biçim ve kenar yumuşatma (1–3).
 */
data class ImageExport(
    val size: Int = 1080,
    val aspect: Float = 1f,
    val background: Int = 0,
    val ground: Boolean = false,
    val shadow: Boolean = true,
    val edges: Boolean = true,
    val fit: Boolean = true,
    val jpeg: Boolean = false,
    val smoothness: Int = 2,
)

/** Seçim: parçalar (ilk seçilen asıl parça), asıl parçanın yüzleri, köşeleri ve (v24) kenarları. */
data class Selection3(
    val parts: List<String> = emptyList(),
    val faces: Set<Int> = emptySet(),
    val vertices: Set<Int> = emptySet(),
    val edges: Set<EdgeRef> = emptySet(),
) {
    val primary: String? get() = parts.firstOrNull()
}

/** v24 kaydırıcıyla yapılan düzenleme: kenar (eğ, ger, it, yuvarlat) ya da yüz (yükselt, içe al, ölçekle) işlemi. */
enum class OpKind(val min: Float, val max: Float, val start: Float, val face: Boolean) {
    BEND(-1f, 1f, 0f, false),
    STRETCH(0.3f, 3f, 1f, false),
    PUSH(-0.5f, 0.5f, 0f, false),
    ROUND(0f, 1f, 0f, false),
    EXTRUDE(-0.6f, 1.2f, 0f, true),
    INSET(0f, 0.45f, 0f, true),
    FACE_SCALE(0.2f, 2.5f, 1f, true),
    /** v25 delik yarıçapı (yüzey bölgesinin yarı genişliğine oran). */
    HOLE(0.05f, 0.95f, 0.4f, true),
}

/** Etkin kaydırıcı işlemi; [failed]: bu kenarda yapılamıyor (ör. yuvarlatma). */
data class SliderOp(val kind: OpKind, val amount: Float, val failed: Boolean = false)

/** Kaydetme ve çıkış olayları. */
sealed interface Event3 {
    data class Saved(val name: String, val toCanvas: Boolean) : Event3
    data object Exit : Event3
    data class Message(val text: String) : Event3
}

/**
 * v23 3B düzenleyici penceresinin ViewModel'i: model, seçim, araç, kamera ve geri al/yinele (modelin anlık
 * görüntüleri). v24: sahne ekran kartıyla (yoksa yazılım çiziciyle) çizilir; dokunuşlar görünümün izdüşümüyle
 * ([currentView], ekran pikseli) ışın atılarak ([Pick3]) parçaya, yüze, kenara ve köşeye çevrilir. Sürükleme süren
 * değişiklikler ([beginLive]…[endLive]) geçmişe tek adım olarak girer, ara karelerde model yalnız değiştirilir.
 * Kaydedilen model GLB olarak "3B Şekillerim"e yazılır; düzenleyici bir tuvalden açıldıysa sonuç o tuvale de gider.
 */
class Model3DEditorViewModel(
    private val repository: Model3DRepository,
    private val textures: TextureRepository,
    private val results: Model3DResults,
    private val modelId: String?,
    private val canvasId: String?,
    private val strokeId: String?,
    private val prefs: Model3DPrefs? = null,
    private val images: com.karapp.drawguidance.data.images.ImagesRepository? = null,
    /** v29 3B şekli tuvale ya da çizgi romana aktarma (resim ya da vektör). */
    private val transfer: com.karapp.drawguidance.data.transfer.CanvasTransfer? = null,
    /** v30 tuvalden açılınca o tuvalin yapay zekâ ayarı (kapalıysa 3B öneri yapılmaz). */
    private val canvasAi: (suspend (String) -> Boolean)? = null,
    /** v33 yeni 3B şeklin adı (ana sayfada 3B tuval oluşturulurken verilir). */
    private val initialName: String? = null,
) : ViewModel() {

    private val _model = MutableStateFlow<Model3D?>(null)
    val model: StateFlow<Model3D?> = _model.asStateFlow()

    private val _selection = MutableStateFlow(Selection3())
    val selection: StateFlow<Selection3> = _selection.asStateFlow()

    private val _tool = MutableStateFlow(Tool3.MOVE)
    val tool: StateFlow<Tool3> = _tool.asStateFlow()

    /** v26 etkin mod (alttaki mod çubuğu). */
    private val _mode = MutableStateFlow(Mode3.SELECT)
    val mode: StateFlow<Mode3> = _mode.asStateFlow()

    /** Her modda en son kullanılan alt araç (moda dönünce o açılır). */
    private val lastTool = HashMap<Mode3, Tool3>()

    /** Eksen kilidi: −1 serbest, 0 X, 1 Y, 2 Z. */
    private val _axis = MutableStateFlow(-1)
    val axis: StateFlow<Int> = _axis.asStateFlow()

    private val _panel = MutableStateFlow<Panel3?>(null)
    val panel: StateFlow<Panel3?> = _panel.asStateFlow()

    private val _plane = MutableStateFlow(DrawPlane.FRONT)
    val plane: StateFlow<DrawPlane> = _plane.asStateFlow()

    private val _brushColor = MutableStateFlow(0xFF263238.toInt())
    val brushColor: StateFlow<Int> = _brushColor.asStateFlow()

    /** Fırça kalınlığı (ekran pikseli; dünyaya kameranın ölçeğiyle çevrilir). */
    private val _brushWidth = MutableStateFlow(6f)
    val brushWidth: StateFlow<Float> = _brushWidth.asStateFlow()

    /** Boya aracının ve yeni malzemelerin rengi/dokusu. */
    private val _paint = MutableStateFlow(Material(color = 0xFFE07A5F.toInt()))
    val paint: StateFlow<Material> = _paint.asStateFlow()

    /** Boya aracı yalnız yüzü mü, tüm parçayı mı boyar. */
    private val _paintScope = MutableStateFlow(PaintScope.FACE)
    val paintScope: StateFlow<PaintScope> = _paintScope.asStateFlow()

    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /** v24 etkin kaydırıcı işlemi (kenar/yüz). */
    private val _op = MutableStateFlow<SliderOp?>(null)
    val op: StateFlow<SliderOp?> = _op.asStateFlow()

    private val _events = MutableSharedFlow<Event3>(extraBufferCapacity = 4)
    val events: SharedFlow<Event3> = _events

    /** Tuvalden açıldı mı (kaydedince tuvale de uygulanır). */
    val fromCanvas: Boolean get() = canvasId != null

    // ------------------------------------------------------------------------------------------ çizim tercihleri

    /** Ekran kartıyla çizilsin mi (tercih açık ve kart uygun). */
    val gpu: StateFlow<Boolean> = if (prefs == null) MutableStateFlow(false) else
        combine(prefs.gpu, prefs.available) { on, ok -> on && ok == true }.stateIn(viewModelScope, SharingStarted.Eagerly, prefs.gpu.value && prefs.available.value == true)
    val gpuPreference: StateFlow<Boolean> = prefs?.gpu ?: MutableStateFlow(false)
    val gpuAvailable: StateFlow<Boolean?> = prefs?.available ?: MutableStateFlow(false)
    val gpuInfo: StateFlow<String?> = prefs?.info ?: MutableStateFlow(null)
    val quality: StateFlow<Int> = prefs?.quality ?: MutableStateFlow(Model3DPrefs.QUALITY_BALANCED)

    fun setGpu(v: Boolean) = prefs?.setGpu(v)
    fun setQuality(q: Int) = prefs?.setQuality(q)
    fun gpuFailed(reason: String) {
        prefs?.gpuFailed(reason)
        _events.tryEmit(Event3.Message(GPU_FALLBACK))
    }
    fun gpuName(name: String) = prefs?.gpuInfo(name)

    // ------------------------------------------------------------------------------------------ görünüm

    @Volatile private var viewW = 0
    @Volatile private var viewH = 0

    /** Sahne alanının boyu (ekran pikseli; dokunuşlar bu görünüme göre çevrilir). */
    fun setViewport(w: Int, h: Int) { viewW = w; viewH = h }

    /** Ekrandaki görünüm (kamera ve sahne alanı boyu). */
    fun currentView(): View3? {
        val m = _model.value ?: return null
        if (viewW <= 0 || viewH <= 0) return null
        return View3.editor(m.camera, viewW, viewH)
    }

    private val undo = ArrayDeque<Model3D>()
    private val redo = ArrayDeque<Model3D>()
    private var lastCoalesce: String? = null
    private var lastCoalesceTime = 0L

    init {
        prefs?.probe()
        viewModelScope.launch {
            val catalog = modelId?.takeIf { it.startsWith(Catalog3D.PREFIX) }
            val loaded = if (catalog != null) repository.fromCatalog(catalog) else modelId?.let { repository.load(it) }
            val m = loaded ?: Model3D(
                id = modelId?.takeIf { catalog == null } ?: UUID.randomUUID().toString(),
                name = initialName ?: DEFAULT_NAME,
                parts = listOf(ModelEdit.newPart(PrimitiveKind.CUBE, UUID.randomUUID().toString(), 0)),
            )
            preloadTextures(m)
            _model.value = m
            if (loaded == null || catalog != null) _dirty.value = true
        }
    }

    private suspend fun preloadTextures(m: Model3D) {
        textures.preload(m.parts.flatMap { p -> listOfNotNull(p.material.texture) + p.faceMaterials.values.mapNotNull { it.texture } }.toSet())
    }

    /** Doku yüklenince sahne yeniden çizilsin diye artar. */
    private val _renderTick = MutableStateFlow(0)
    val renderTick: StateFlow<Int> = _renderTick.asStateFlow()

    /** Çizim için doku (bellekte değilse null; yükleme başlar). Her iş parçacığından çağrılabilir. */
    fun texture(id: String) = textures.cached(id) ?: run {
        val first = synchronized(loading) { loading.add(id) }
        if (first) viewModelScope.launch {
            textures.texture(id)?.let { _renderTick.value++ }
            synchronized(loading) { loading.remove(id) }
        }
        null
    }
    private val loading = HashSet<String>()

    val textureItems get() = textures.items

    // ------------------------------------------------------------------------------------------ geçmiş

    /** Sürüklemenin başındaki model (sürerken geçmişe yazılmaz). */
    private var liveBase: Model3D? = null

    /** Modeli değiştirir (geri alınabilir). Aynı [coalesce] anahtarlı ardışık değişiklikler tek adım olur. */
    private fun commit(next0: Model3D, coalesce: String? = null) {
        val current = _model.value ?: return
        if (next0 === current) return
        // v26 tek üyesi kalan gruplar çözülür
        val next = normalizeGroups(next0)
        if (liveBase != null) {
            // Sürükleme sürüyor: geçmiş sonunda tek adım
            _model.value = next
            _dirty.value = true
            return
        }
        if (next == current) return
        val now = System.currentTimeMillis()
        val merge = coalesce != null && coalesce == lastCoalesce && now - lastCoalesceTime < COALESCE_MS
        if (!merge) pushUndo(current)
        lastCoalesce = coalesce
        lastCoalesceTime = now
        _model.value = next
        _dirty.value = true
    }

    private fun pushUndo(m: Model3D) {
        undo.addLast(m)
        if (undo.size > MAX_HISTORY) undo.removeFirst()
        redo.clear()
        refreshFlags()
    }

    /** Sürükleme başladı: ara değişiklikler geçmişe girmez. */
    fun beginLive() {
        if (liveBase == null) liveBase = _model.value
    }

    /** Sürükleme bitti: değiştiyse tek geri alma adımı. */
    fun endLive() {
        val base = liveBase ?: return
        liveBase = null
        lastCoalesce = null
        val now = _model.value ?: return
        if (now !== base && now.copy(camera = base.camera) != base) pushUndo(base)
    }

    fun endCoalesce() { lastCoalesce = null }

    fun undo() {
        cancelOp()
        dropSuggestion()
        val current = _model.value ?: return
        val prev = undo.removeLastOrNull() ?: return
        redo.addLast(current)
        _model.value = prev.copy(camera = current.camera)
        lastCoalesce = null
        _dirty.value = true
        cleanSelection()
        refreshFlags()
    }

    fun redo() {
        cancelOp()
        dropSuggestion()
        val current = _model.value ?: return
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(current)
        _model.value = next.copy(camera = current.camera)
        lastCoalesce = null
        _dirty.value = true
        cleanSelection()
        refreshFlags()
    }

    private fun refreshFlags() {
        _canUndo.value = undo.isNotEmpty()
        _canRedo.value = redo.isNotEmpty()
    }

    private fun cleanSelection() {
        val m = _model.value ?: return
        val s = _selection.value
        val parts = s.parts.filter { id -> m.parts.any { it.id == id } }
        val primary = parts.firstOrNull()?.let { m.part(it) }
        val n = primary?.mesh?.vertexCount ?: 0
        _selection.value = Selection3(
            parts,
            s.faces.filter { primary != null && it < primary.mesh.faceCount }.toSet(),
            s.vertices.filter { it < n }.toSet(),
            s.edges.filter { it.a < n && it.b < n && primary != null && MeshOps.facesOfEdge(primary.mesh, it).isNotEmpty() }.toSet(),
        )
    }

    // ------------------------------------------------------------------------------------------ araçlar ve paneller

    fun setTool(t: Tool3) {
        applyOp()
        val mode = _mode.value
        val target = if (t in mode.tools) mode else Mode3.of(t)
        if (target != mode) enterMode(target)
        lastTool[target] = t
        _tool.value = t
        // Köşe/kenar/yüz seçimi araca özgü
        val s = _selection.value
        _selection.value = s.copy(
            faces = if (t == Tool3.FACE || t == Tool3.PAINT) s.faces else emptySet(),
            vertices = if (t == Tool3.EDGE || t == Tool3.POINT) s.vertices else emptySet(),
            edges = if (t == Tool3.EDGE) s.edges else emptySet(),
        )
        // v26 fırça paneli yalnız fırçada (silgiye geçince kapanır)
        if (t != Tool3.DRAW && _panel.value == Panel3.BRUSH) _panel.value = null
        if (t == Tool3.DRAW && _panel.value == null) _panel.value = Panel3.BRUSH
    }

    /**
     * v26 mod değişir: modun son alt aracı açılır; önceki moda ait panel kapanır (Parçalar ve Ayarlar her modda kalır),
     * Ekle modunda ekleme paneli açılır.
     */
    fun setMode(m: Mode3) {
        applyOp()
        if (m == _mode.value) {
            // Aynı moda yeniden dokunmak modun panelini açar (Ekle'de ekleme paneli)
            if (m == Mode3.ADD && _panel.value == null) _panel.value = Panel3.ADD
            return
        }
        enterMode(m)
        setTool(lastTool[m] ?: m.tools.first())
        if (m == Mode3.ADD) _panel.value = Panel3.ADD
    }

    private fun enterMode(m: Mode3) {
        // v27 mod değişince açık öneri kalkar; Çiz'e girerken öneri modeli hazırlanır
        if (m != _mode.value) dropSuggestion()
        if (m == Mode3.DRAW) prewarmSuggest()
        _mode.value = m
        val p = _panel.value
        if (p != null && p !in GLOBAL_PANELS) _panel.value = null
    }

    fun setAxis(a: Int) { _axis.value = a }
    fun togglePanel(p: Panel3) { _panel.value = if (_panel.value == p) null else p }
    fun closePanel() { _panel.value = null }
    fun setPlane(p: DrawPlane) { _plane.value = p }
    fun setBrushColor(c: Int) { _brushColor.value = c }
    fun setBrushWidth(w: Float) { _brushWidth.value = w.coerceIn(1f, 60f) }
    fun setPaint(m: Material) { _paint.value = m }
    fun setPaintScope(v: PaintScope) { _paintScope.value = v; paintedRegion = null }

    // ------------------------------------------------------------------------------------------ kamera

    fun camera(): Camera3 = _model.value?.camera ?: Camera3()

    /** Kamera görünüm ayarıdır: geri alma geçmişine girmez, kaydedilir (küçük resim bu açıdan). */
    private fun setCamera(c: Camera3) {
        val m = _model.value ?: return
        _model.value = m.copy(camera = c)
        _dirty.value = true
    }

    fun orbit(dYaw: Float, dPitch: Float) {
        val c = camera()
        setCamera(c.copy(yaw = ((c.yaw + dYaw) % 360f + 360f) % 360f, pitch = (c.pitch + dPitch).coerceIn(-89f, 89f)))
    }

    fun zoom(factor: Float) {
        val c = camera()
        setCamera(c.copy(zoom = (c.zoom * factor).coerceIn(0.1f, 30f)))
    }

    /** Hedefi ekran düzleminde kaydırır ([dx], [dy] ekran pikseli). */
    fun pan(dx: Float, dy: Float) {
        val view = currentView() ?: return
        val c = camera()
        val d = view.screenDelta(-dx, -dy)
        setCamera(c.copy(target = c.target + d))
    }

    fun setPerspective(p: Float) = setCamera(camera().copy(persp = p.coerceIn(0f, 1f)))

    /** Hazır açılar: 0 izometrik, 1 ön, 2 yan, 3 üst. Hedef modelin ortasına alınır. */
    fun presetView(kind: Int) {
        val m = _model.value ?: return
        val center = ModelEdit.modelBounds(m)?.center ?: V3(0f, 0.5f, 0f)
        val c = camera()
        setCamera(
            when (kind) {
                1 -> c.copy(yaw = 0f, pitch = 0f, target = center)
                2 -> c.copy(yaw = 90f, pitch = 0f, target = center)
                3 -> c.copy(yaw = 0f, pitch = 89f, target = center)
                else -> c.copy(yaw = 35f, pitch = 24f, target = center)
            },
        )
    }

    /** Modeli ekrana sığdırır (yakınlık ve hedef). */
    fun fitView() {
        val m = _model.value ?: return
        val b = ModelEdit.modelBounds(m) ?: return
        val r = b.radius.coerceAtLeast(0.2f)
        setCamera(camera().copy(target = b.center, zoom = (View3.REFERENCE_EXTENT / (r * 1.15f)).coerceIn(0.1f, 30f)))
    }

    // ------------------------------------------------------------------------------------------ parçalar

    private fun nextIndex(m: Model3D) = m.parts.size

    /** Yeni parçanın yeri: mevcut parçaların sağı (üst üste binmesin). */
    private fun besideX(m: Model3D, width: Float = 1f): Float {
        val b = ModelEdit.modelBounds(m) ?: return 0f
        return b.max.x + 0.2f + width / 2f
    }

    fun addPart(kind: PrimitiveKind) {
        applyOp()
        val m = _model.value ?: return
        val part = ModelEdit.newPart(kind, UUID.randomUUID().toString(), nextIndex(m), V3(if (m.parts.isEmpty()) 0f else besideX(m), 0f, 0f))
        commit(m.copy(parts = m.parts + part))
        _selection.value = Selection3(listOf(part.id))
        if (_tool.value == Tool3.VIEW || _tool.value == Tool3.DRAW || _tool.value == Tool3.ERASE) { if (_mode.value == Mode3.ADD) _tool.value = Tool3.MOVE else setTool(Tool3.MOVE) }
        _panel.value = null
    }

    /** v24 hazır şeklin parçalarını (ve çizgilerini) modele ekler: yeni kimliklerle, mevcutların yanına. */
    fun addCatalog(catalogId: String) {
        applyOp()
        val m = _model.value ?: return
        val item = Catalog3D.byId(catalogId)?.build() ?: return
        val b = ModelEdit.modelBounds(item)
        val dx = if (m.parts.isEmpty() && m.strokes.isEmpty()) 0f else besideX(m, b?.size?.x ?: 1f) - (b?.center?.x ?: 0f)
        val shift = V3(dx, 0f, 0f)
        val parts = item.parts.map { it.copy(id = UUID.randomUUID().toString(), position = it.position + shift) }
        val strokes = item.strokes.map { s ->
            val p = s.points.copyOf()
            for (i in 0 until s.count) p[3 * i] += dx
            Stroke3(UUID.randomUUID().toString(), p, s.color, s.width)
        }
        commit(m.copy(parts = m.parts + parts, strokes = m.strokes + strokes))
        _selection.value = Selection3(parts.map { it.id })
        if (_tool.value == Tool3.VIEW || _tool.value == Tool3.DRAW || _tool.value == Tool3.ERASE) { if (_mode.value == Mode3.ADD) _tool.value = Tool3.MOVE else setTool(Tool3.MOVE) }
        _panel.value = null
        _events.tryEmit(Event3.Message(ADDED.format(item.name)))
    }

    /**
     * v27 GLB/glTF dosyasının parçaları bu modele eklenir (yanına, tek grup olarak, seçili); [saveSeparately] ise model
     * ayrıca 3B Şekillerim'e ayrı şekil olarak kaydedilir. Tek geri alma adımı.
     */
    fun importGlbParts(bytes: ByteArray, name: String, maxTriangles: Int, saveSeparately: Boolean) {
        applyOp()
        runHeavy(MSG_WORKING_IMPORT, MSG_IMPORT_FAIL) {
            val r = repository.importGlb(bytes, name, maxTriangles)
            if (saveSeparately) repository.save(r.model.copy(id = UUID.randomUUID().toString()))
            val item = r.model
            val change: (Model3D) -> Model3D = { mm ->
                val b = ModelEdit.modelBounds(item)
                val dx = if (mm.parts.isEmpty() && mm.strokes.isEmpty()) 0f else besideX(mm, b?.size?.x ?: 1f) - (b?.center?.x ?: 0f)
                val gid = if (item.parts.size >= 2) UUID.randomUUID().toString() else null
                val parts = item.parts.map { it.copy(id = UUID.randomUUID().toString(), position = it.position + V3(dx, 0f, 0f), group = gid) }
                _selection.value = Selection3(parts.map { it.id })
                message((if (saveSeparately) MSG_IMPORTED_SAVED else MSG_IMPORTED).format(item.name, r.triangles))
                mm.copy(parts = mm.parts + parts, groups = if (gid != null) mm.groups + Group3(gid, item.name) else mm.groups)
            }
            change
        }
        _panel.value = null
    }

    fun selectPart(id: String, add: Boolean = false) {
        applyOp()
        val s = _selection.value
        _selection.value = if (add) {
            Selection3(if (id in s.parts) s.parts - id else s.parts + id)
        } else Selection3(listOf(id))
    }

    fun clearSelection() { applyOp(); _selection.value = Selection3() }

    fun deleteSelected() {
        applyOp()
        val m = _model.value ?: return
        val ids = _selection.value.parts.toSet()
        if (ids.isEmpty()) return
        commit(m.copy(parts = m.parts.filterNot { it.id in ids }))
        _selection.value = Selection3()
    }

    fun duplicateSelected() {
        applyOp()
        val m = _model.value ?: return
        val ids = _selection.value.parts
        if (ids.isEmpty()) return
        // v26 kopyalanan gruplar yeni grup olur
        val newGroups = HashMap<String, Group3>()
        val copies = ids.mapNotNull { m.part(it) }.map { p ->
            val g = p.group?.let { old -> newGroups.getOrPut(old) { Group3(UUID.randomUUID().toString(), (m.groups.firstOrNull { it.id == old }?.name ?: GROUP) + " (2)") } }
            ModelEdit.duplicate(p, UUID.randomUUID().toString()).let { it.copy(position = it.position + V3(0.3f, 0f, 0.3f), group = g?.id) }
        }
        commit(m.copy(parts = m.parts + copies, groups = m.groups + newGroups.values))
        _selection.value = Selection3(copies.map { it.id })
    }

    /** Seçili parçaları tek parçada birleştirir. */
    fun mergeSelected() {
        applyOp()
        val m = _model.value ?: return
        val parts = _selection.value.parts.mapNotNull { m.part(it) }
        if (parts.size < 2) return
        val merged = ModelEdit.merge(parts, UUID.randomUUID().toString(), parts.first().name) ?: return
        val ids = parts.map { it.id }.toSet()
        val at = m.parts.indexOfFirst { it.id in ids }
        val rest = m.parts.filterNot { it.id in ids }.toMutableList()
        rest.add(at.coerceIn(0, rest.size), merged)
        commit(m.copy(parts = rest))
        _selection.value = Selection3(listOf(merged.id))
    }

    fun renamePart(id: String, name: String) {
        val m = _model.value ?: return
        val p = m.part(id) ?: return
        commit(m.replacePart(p.copy(name = name.ifBlank { p.name })))
    }

    fun togglePartVisible(id: String) {
        val m = _model.value ?: return
        val p = m.part(id) ?: return
        commit(m.replacePart(p.copy(visible = !p.visible)))
    }

    fun dropSelectedToGround() = updateSelectedParts("ground") { ModelEdit.dropToGround(it) }

    /** Parçaların listedeki sırası (yalnız düzen; çizimde derinlik belirler). */
    fun movePartInList(id: String, direction: Int) {
        val m = _model.value ?: return
        val i = m.parts.indexOfFirst { it.id == id }
        val j = i + direction
        if (i < 0 || j !in m.parts.indices) return
        val list = m.parts.toMutableList()
        list.add(j, list.removeAt(i))
        commit(m.copy(parts = list))
    }

    private fun updateSelectedParts(coalesce: String?, block: (Part) -> Part) {
        val m = _model.value ?: return
        val ids = _selection.value.parts.toSet()
        if (ids.isEmpty()) return
        commit(m.copy(parts = m.parts.map { if (it.id in ids) block(it) else it }), coalesce)
    }

    // ------------------------------------------------------------------------------------------ dönüşüm

    fun moveSelected(d: V3, coalesce: String? = "move") = updateSelectedParts(coalesce) { it.copy(position = it.position + d) }

    /** Seçili parçaları [axis] (−1: Y) çevresinde [deg] derece döndürür. */
    fun rotateSelected(deg: Float, axis: Int = _axis.value, coalesce: String? = "rotate") {
        val a = if (axis in 0..2) axis else 1
        val pivot = selectionPivot()
        if (pivot == null) {
            updateSelectedParts(coalesce) { p -> p.copy(rotation = p.rotation.with(a, ((p.rotation.get(a) + deg) % 360f + 540f) % 360f - 180f)) }
            return
        }
        // v26 birden çok parça (grup): ortak orta çevresinde dünya ekseninde birlikte döner
        val rw = Transform3.rotation(V3.ZERO.with(a, deg))
        updateSelectedParts(coalesce) { p ->
            val r = Transform3.rotation(p.rotation)
            p.copy(position = pivot + mul(rw, p.position - pivot), rotation = euler(mat(rw, r)))
        }
    }

    /** Birden çok parça seçiliyse ortak döndürme/ölçekleme merkezi (parça ortalarının ortalaması); tek parçada null. */
    private fun selectionPivot(): V3? {
        val m = _model.value ?: return null
        val parts = _selection.value.parts.mapNotNull { m.part(it) }
        if (parts.size < 2) return null
        var c = V3.ZERO
        parts.forEach { c += partCenter(it) }
        return c * (1f / parts.size)
    }

    /** Ölçek: [axis] seçiliyse yalnız o eksende, değilse eşit. */
    fun scaleSelected(factor: Float, axis: Int = _axis.value, coalesce: String? = "scale") {
        val f = factor.coerceIn(0.2f, 5f)
        val pivot = selectionPivot()
        updateSelectedParts(coalesce) { p ->
            val s = if (axis in 0..2) p.scale.with(axis, (p.scale.get(axis) * f).coerceIn(0.01f, 100f))
            else V3((p.scale.x * f).coerceIn(0.01f, 100f), (p.scale.y * f).coerceIn(0.01f, 100f), (p.scale.z * f).coerceIn(0.01f, 100f))
            // v26 grupta konumlar da ortak merkeze göre ölçeklenir
            val pos = if (pivot == null) p.position else if (axis in 0..2) p.position.with(axis, pivot.get(axis) + (p.position.get(axis) - pivot.get(axis)) * f)
            else pivot + (p.position - pivot) * f
            p.copy(scale = s, position = pos)
        }
    }

    /** Sayısal değerler (panelden). */
    fun setTransform(id: String, position: V3? = null, rotation: V3? = null, scale: V3? = null) {
        val m = _model.value ?: return
        val p = m.part(id) ?: return
        commit(m.replacePart(p.copy(position = position ?: p.position, rotation = rotation ?: p.rotation, scale = scale ?: p.scale)), "transform-$id")
    }

    /** Ekran sürüklemesinden döndürme ve ölçek. */
    fun rotateByDrag(dx: Float, dy: Float) {
        val deg = if (abs(dx) >= abs(dy)) dx * 0.5f else dy * 0.5f
        rotateSelected(deg)
    }

    fun scaleByDrag(dx: Float, dy: Float) = scaleSelected(exp((dx - dy) / 260f))

    // ------------------------------------------------------------------------------------------ nokta ve kenar (v24)

    private fun primaryIndex(m: Model3D): Int = _selection.value.primary?.let { id -> m.parts.indexOfFirst { it.id == id } } ?: -1

    /**
     * Nokta/Kenar aracında dokunuş: asıl parçanın kenarına yakınsa kenar seçilir/bırakılır (birden çok kenar seçilebilir);
     * başka parçaya dokunulursa o parça seçilir; boşa dokunulursa kenar seçimi temizlenir.
     */
    private fun edgeTap(x: Float, y: Float, radius: Float) {
        val m = _model.value ?: return
        val view = currentView() ?: return
        val s = _selection.value
        val hit = Pick3.pick(m, view, x, y)
        var pi = primaryIndex(m)
        if (pi < 0) {
            if (hit == null) return
            pi = hit.part
            _selection.value = Selection3(listOf(m.parts[pi].id))
        }
        val e = Pick3.edge(m, view, pi, x, y, radius)
        val cur = _selection.value
        when {
            e != null && (e.second.visible || hit == null || hit.part == pi) ->
                _selection.value = cur.copy(edges = if (e.first in cur.edges) cur.edges - e.first else cur.edges + e.first, vertices = emptySet())
            hit != null && hit.part != pi -> _selection.value = Selection3(listOf(m.parts[hit.part].id))
            else -> _selection.value = s.copy(edges = emptySet(), vertices = emptySet())
        }
    }

    /** Basılı tutulan kenara yeni nokta ekler (yüzlerin şekli değişmez; nokta sonra sürüklenebilir). */
    fun addPointAt(x: Float, y: Float, radius: Float): Boolean {
        applyOp()
        val m = _model.value ?: return false
        val view = currentView() ?: return false
        var pi = primaryIndex(m)
        if (pi < 0) pi = Pick3.pick(m, view, x, y)?.part ?: return false
        val (edge, _) = Pick3.edge(m, view, pi, x, y, radius) ?: return false
        val part = m.parts[pi]
        val t = Pick3.edgeParam(m, view, pi, edge, x, y)
        val (mesh, v) = MeshOps.splitEdge(part.mesh, edge, t)
        commit(m.replacePart(part.copy(mesh = mesh, primitive = null)))
        _selection.value = Selection3(listOf(part.id), vertices = setOf(v))
        _events.tryEmit(Event3.Message(POINT_ADDED))
        return true
    }

    /** Sürüklenen noktalar ve derinlikleri (sürükleme başında belirlenir). */
    private var dragVertices: Set<Int> = emptySet()
    private var dragDepth = 0f

    /**
     * Nokta/Kenar aracında sürükleme başlangıcı: parmağın altındaki nokta (yoksa kenarın iki ucu) tutulur. Bir şey
     * tutulduysa true (sürükleme sahneyi döndürmez).
     */
    fun beginVertexDrag(x: Float, y: Float, radius: Float): Boolean {
        val m = _model.value ?: return false
        val view = currentView() ?: return false
        val pi = primaryIndex(m).takeIf { it >= 0 } ?: Pick3.pick(m, view, x, y)?.part ?: return false
        val part = m.parts[pi]
        val v = Pick3.vertex(m, view, pi, x, y, radius * 0.8f)
        val grabbed: Set<Int>
        val s = _selection.value
        if (v != null) {
            grabbed = setOf(v.index)
            _selection.value = Selection3(listOf(part.id), vertices = grabbed, edges = if (s.primary == part.id) s.edges else emptySet())
        } else {
            val e = Pick3.edge(m, view, pi, x, y, radius) ?: return false
            grabbed = setOf(e.first.a, e.first.b)
            _selection.value = Selection3(listOf(part.id), vertices = grabbed, edges = setOf(e.first))
        }
        val t = Transform3.of(part)
        val w = t.apply(part.mesh.vertex(grabbed.first()))
        dragVertices = grabbed
        dragDepth = view.depth(w.x, w.y, w.z)
        beginLive()
        return true
    }

    fun dragVerticesBy(dx: Float, dy: Float) {
        val view = currentView() ?: return
        val m = _model.value ?: return
        val p = _selection.value.primary?.let { m.part(it) } ?: return
        if (dragVertices.isEmpty()) return
        val d = ModelEdit.dragDelta(view, dx, dy, _axis.value, dragDepth)
        commit(m.replacePart(ModelEdit.moveVertices(p, dragVertices, d)))
    }

    fun endVertexDrag() {
        dragVertices = emptySet()
        endLive()
    }

    fun setVertexSelection(v: Set<Int>) { _selection.value = _selection.value.copy(vertices = v) }
    fun clearEdgeSelection() { applyOp(); _selection.value = _selection.value.copy(edges = emptySet(), vertices = emptySet()) }

    /** v27 Düzenle → Yüz'de seçili yüzleri bırakır (parça seçili kalır). */
    fun clearFaceSelection() { applyOp(); _selection.value = _selection.value.copy(faces = emptySet()) }

    /** Seçili kenarların ortasına nokta ekler (düğmeyle). */
    fun splitSelectedEdges() {
        applyOp()
        val m = _model.value ?: return
        val s = _selection.value
        val part = s.primary?.let { m.part(it) } ?: return
        if (s.edges.isEmpty()) return
        var mesh = part.mesh
        val added = HashSet<Int>()
        for (e in s.edges) {
            if (MeshOps.facesOfEdge(mesh, e).isEmpty()) continue
            val (nm, v) = MeshOps.splitEdge(mesh, e, 0.5f)
            mesh = nm; added += v
        }
        commit(m.replacePart(part.copy(mesh = mesh, primitive = null)))
        _selection.value = Selection3(listOf(part.id), vertices = added)
    }

    // ------------------------------------------------------------------------------------------ kaydırıcı işlemleri (v24)

    private var opBase: Model3D? = null
    private var opPart: String? = null

    /** Kenar ya da yüz işlemini başlatır: kaydırıcı değiştikçe sonuç önizlenir, Tamam ile tek adımda uygulanır. */
    fun startOp(kind: OpKind) {
        applyOp()
        val m = _model.value ?: return
        val s = _selection.value
        val part = s.primary?.let { m.part(it) } ?: return
        if (kind.face && s.faces.isEmpty()) return
        if (!kind.face && s.edges.isEmpty()) return
        opBase = m
        opPart = part.id
        _op.value = SliderOp(kind, kind.start)
    }

    fun setOpAmount(v: Float) {
        val cur = _op.value ?: return
        val base = opBase ?: return
        val part = opPart?.let { base.part(it) } ?: return
        val amount = v.coerceIn(cur.kind.min, cur.kind.max)
        // Delik: kaydırıcı yalnız yarıçapı değiştirir (önizleme çember), Tamam'da açılır
        if (cur.kind == OpKind.HOLE) { _op.value = cur.copy(amount = amount); return }
        val next = runCatching { compute(cur.kind, part, amount) }.getOrNull()
        val camera = _model.value?.camera ?: base.camera
        if (next == null) {
            _model.value = base.copy(camera = camera)
            _op.value = cur.copy(amount = amount, failed = true)
            return
        }
        _model.value = base.replacePart(next).copy(camera = camera)
        _dirty.value = true
        _op.value = cur.copy(amount = amount, failed = false)
    }

    private fun compute(kind: OpKind, part: Part, a: Float): Part? {
        val s = _selection.value
        val k = ((abs(part.scale.x) + abs(part.scale.y) + abs(part.scale.z)) / 3f).coerceAtLeast(1e-3f)
        return when (kind) {
            OpKind.BEND -> part.copy(mesh = MeshOps.bend(part.mesh, s.edges, a), primitive = null)
            OpKind.STRETCH -> part.copy(mesh = MeshOps.stretch(part.mesh, s.edges, a), primitive = null)
            OpKind.PUSH -> part.copy(mesh = MeshOps.push(part.mesh, s.edges, a / k), primitive = null)
            OpKind.ROUND -> {
                if (a <= 0.001f) return part
                val e = s.edges.first()
                MeshOps.round(part.mesh, e, a * MeshOps.extent(part.mesh) * 0.5f, ROUND_SEGMENTS)?.let { part.copy(mesh = it, primitive = null, faceMaterials = emptyMap()) }
            }
            // v25 seçili yüzler birlikte (bölge) yükseltilir: iç kenarlarda duvar oluşmaz
            OpKind.EXTRUDE -> if (abs(a) < 1e-3f) part else Solid3.apply(part, MeshTopo.extrudeRegion(part.mesh, s.faces, a / k))
            OpKind.INSET -> if (a < 1e-3f) part else faces(part) { p, f -> ModelEdit.insetFace(p, f, a) }
            OpKind.FACE_SCALE -> if (s.faces.size == 1) faces(part) { p, f -> ModelEdit.scaleFace(p, f, a) }
                else part.copy(mesh = MeshTopo.scaleRegion(part.mesh, s.faces, a), primitive = null)
            OpKind.HOLE -> null
        }
    }

    private fun faces(part: Part, op: (Part, Int) -> Part): Part {
        var p = part
        // Yüz sayısı değişen işlemlerde sıralar kaymasın diye büyükten küçüğe
        _selection.value.faces.sortedDescending().forEach { f -> if (f < p.mesh.faceCount) p = op(p, f) }
        return p
    }

    /** Etkin işlemi uygular (geçmişe tek adım). */
    fun applyOp() {
        val cur = _op.value ?: return
        val base = opBase ?: return
        opBase = null; opPart = null
        _op.value = null
        if (cur.kind == OpKind.HOLE) { holeThroughFaces(cur.amount); return }
        val now = _model.value ?: return
        if (now.copy(camera = base.camera) != base) {
            pushUndo(base)
            _dirty.value = true
        }
        // Ağın yapısı değiştiyse kenar seçimi geçersiz
        if (!cur.kind.face && (cur.kind == OpKind.BEND || cur.kind == OpKind.ROUND)) _selection.value = _selection.value.copy(edges = emptySet(), vertices = emptySet())
        cleanSelection()
    }

    /** İşlemden vazgeçer (model başlangıcına döner). */
    fun cancelOp() {
        val base = opBase ?: run { _op.value = null; return }
        opBase = null; opPart = null
        _op.value = null
        _model.value = base.copy(camera = _model.value?.camera ?: base.camera)
    }

    // ------------------------------------------------------------------------------------------ yüz

    fun setFaceSelection(f: Set<Int>) { _selection.value = _selection.value.copy(faces = f) }

    private fun updateFaces(coalesce: String?, op: (Part, Int) -> Part) {
        val m = _model.value ?: return
        val s = _selection.value
        val p0 = s.primary?.let { m.part(it) } ?: return
        if (s.faces.isEmpty()) return
        var p = p0
        s.faces.sortedDescending().forEach { f -> if (f < p.mesh.faceCount) p = op(p, f) }
        commit(m.replacePart(p), coalesce)
    }

    fun extrudeFaces(distance: Float) = updateFaces(null) { p, f -> ModelEdit.extrudeFace(p, f, distance) }

    fun insetFaces(amount: Float) = updateFaces(null) { p, f -> ModelEdit.insetFace(p, f, amount) }

    fun scaleFaces(factor: Float) = updateFaces("face-scale") { p, f -> ModelEdit.scaleFace(p, f, factor) }

    fun moveFaces(d: V3) = updateFaces("face-move") { p, f -> ModelEdit.moveFace(p, f, d) }

    fun deleteFaces() {
        applyOp()
        updateFaces(null) { p, f -> ModelEdit.deleteFace(p, f) }
        _selection.value = _selection.value.copy(faces = emptySet())
    }

    /** Seçili yüzleri (yüz yoksa seçili parçaları) boyar. */
    fun paintSelection(material: Material = _paint.value) {
        applyOp()
        val s = _selection.value
        if (s.faces.isNotEmpty()) updateFaces("paint") { p, f -> ModelEdit.paintFace(p, f, material) }
        else updateSelectedParts("paint") { it.copy(material = material, faceMaterials = emptyMap()) }
    }

    /** v27 son boyanan bölge (sürüklerken aynı yüzey yeniden hesaplanmasın): parça kimliği, ağ ve yüzler. */
    private var paintedRegion: Triple<String, com.karapp.drawguidance.content.model3d.Mesh, Set<Int>>? = null

    /** v27 boya kapsamına göre dokunulan yüzden boyanacak yüzler (parça/tümü kapsamında null: bütün parça). */
    private fun paintTargets(p: Part, face: Int): Set<Int>? = when (_paintScope.value) {
        PaintScope.FACE -> setOf(face)
        PaintScope.SURFACE -> paintedRegion?.takeIf { it.first == p.id && it.second === p.mesh && face in it.third }?.third
            ?: MeshTopo.surfaceRegion(p.mesh, face).also { paintedRegion = Triple(p.id, p.mesh, it) }
        PaintScope.PART, PaintScope.ALL -> null
    }

    /**
     * v27 Boya'da parmak inince neyin boyanacağı önceden gösterilir: tek yüz ya da yüzey seçili yüz olarak, parça ya da
     * bütün model seçili parça olarak vurgulanır.
     */
    fun previewPaint(x: Float, y: Float) {
        val m = _model.value ?: return
        val hit = pick(x, y) ?: return
        val p = m.parts.getOrNull(hit.first) ?: return
        _selection.value = when (_paintScope.value) {
            PaintScope.ALL -> Selection3(m.parts.filter { it.visible }.map { it.id })
            PaintScope.PART -> Selection3(listOf(p.id))
            else -> Selection3(listOf(p.id), paintTargets(p, hit.second) ?: emptySet())
        }
    }

    /** Boya aracıyla dokunulan yüz (v27 kapsama göre: tek yüz, yüzey, parça ya da bütün model). */
    fun paintAt(partIndex: Int, face: Int) {
        val m = _model.value ?: return
        val p = m.parts.getOrNull(partIndex) ?: return
        val mat = _paint.value
        viewModelScope.launch { mat.texture?.let { textures.texture(it) } }
        when (_paintScope.value) {
            PaintScope.ALL -> {
                // Gizli parçalar boyanmaz
                if (m.parts.all { !it.visible || (it.material == mat && it.faceMaterials.isEmpty()) }) return
                commit(m.copy(parts = m.parts.map { if (it.visible) it.copy(material = mat, faceMaterials = emptyMap()) else it }), "paint-drag")
                _selection.value = Selection3()
                return
            }
            PaintScope.PART -> {
                if (p.material == mat && p.faceMaterials.isEmpty()) return
                commit(m.replacePart(p.copy(material = mat, faceMaterials = emptyMap())), "paint-drag")
            }
            else -> {
                val faces = paintTargets(p, face) ?: return
                // Zaten bu renkteyse yeni geri alma adımı ya da kayıt yok (sürüklerken aynı yüzün üstünde)
                if (faces.all { p.materialOf(it) == mat }) { _selection.value = Selection3(listOf(p.id)); return }
                commit(m.replacePart(ModelEdit.paintFaces(p, faces, mat)), "paint-drag")
            }
        }
        _selection.value = Selection3(listOf(p.id))
    }

    // ------------------------------------------------------------------------------------------ malzeme ve cisim

    /** Seçili parçaların malzemesi (yüz seçiliyse o yüzlerin). */
    fun selectedMaterial(): Material? {
        val m = _model.value ?: return null
        val s = _selection.value
        val p = s.primary?.let { m.part(it) } ?: return null
        return s.faces.firstOrNull()?.let { p.materialOf(it) } ?: p.material
    }

    fun setMaterial(mat: Material) {
        viewModelScope.launch {
            mat.texture?.let { textures.texture(it) }
            // v36 Boya aracında malzeme seçimi yalnız fırçanındır (seçili parçanın bütün yüzleri boyanmaz)
            if (_tool.value == Tool3.PAINT) { _paint.value = mat; return@launch }
            val s = _selection.value
            if (s.faces.isNotEmpty() && (_tool.value == Tool3.FACE || _tool.value == Tool3.PAINT)) {
                updateFaces("material") { p, f -> ModelEdit.paintFace(p, f, mat) }
            } else {
                updateSelectedParts("material") { it.copy(material = mat) }
            }
            _paint.value = mat
        }
    }

    fun setSmooth(v: Boolean) = updateSelectedParts(null) { it.copy(smooth = v) }

    fun setUv(mode: com.karapp.drawguidance.content.model3d.UvMode) = updateSelectedParts(null) { it.copy(uv = mode) }

    fun setPrimitiveSpec(spec: PrimitiveSpec) = updateSelectedParts("primitive") {
        ModelEdit.setPrimitive(it, spec.copy(segments = spec.segments.coerceIn(3, Primitives.MAX_SEGMENTS), rings = spec.rings.coerceIn(2, Primitives.MAX_RINGS)))
    }

    // ------------------------------------------------------------------------------------------ sahne

    fun setScene(scene: SceneSettings, coalesce: String? = "scene") {
        val m = _model.value ?: return
        commit(m.copy(scene = scene), coalesce)
    }

    // ------------------------------------------------------------------------------------------ 3B çizgiler

    /** Çizim düzlemi: nokta ve normal (yüz düzleminde seçili yüz; yoksa seçili parçanın/hedefin merkezi). */
    fun drawPlane(): Pair<V3, V3>? {
        val view = currentView() ?: return null
        val m = _model.value ?: return null
        val s = _selection.value
        val p = s.primary?.let { m.part(it) }
        if (_plane.value == DrawPlane.FACE) {
            val f = s.faces.firstOrNull() ?: return null
            return ModelEdit.faceFrame(p ?: return null, f)
        }
        val origin = p?.let { ModelEdit.worldBounds(it).center } ?: m.camera.target
        return ModelEdit.plane(_plane.value, view, origin)
    }

    /** Ekran noktasının çizim düzlemindeki karşılığı. */
    fun planePoint(plane: Pair<V3, V3>, x: Float, y: Float): V3? {
        val view = currentView() ?: return null
        return ModelEdit.hitPlane(view, x, y, plane.first, plane.second)
    }

    fun addStroke(points: FloatArray, widthWorld: Float): String? {
        val m = _model.value ?: return null
        if (points.size < 6) return null
        val id = UUID.randomUUID().toString()
        commit(m.copy(strokes = m.strokes + Stroke3(id, points, _brushColor.value, widthWorld, System.currentTimeMillis())))
        return id
    }

    fun eraseStroke(index: Int) {
        val m = _model.value ?: return
        if (index !in m.strokes.indices) return
        commit(m.copy(strokes = m.strokes.filterIndexed { i, _ -> i != index }))
    }

    fun clearStrokes() {
        val m = _model.value ?: return
        if (m.strokes.isEmpty()) return
        commit(m.copy(strokes = emptyList()))
    }

    // ------------------------------------------------------------------------------------------ dokunuş

    /** Ekran noktasındaki parça sırası ve yüz (yoksa null). */
    fun pick(x: Float, y: Float): Pair<Int, Int>? {
        val m = _model.value ?: return null
        val view = currentView() ?: return null
        return Pick3.pick(m, view, x, y)?.let { it.part to it.face }
    }

    /** Ekrandaki noktaya dokunuldu (araca göre seçim, boyama, silme). [radius] parmak payı (piksel). */
    fun tapAt(x: Float, y: Float, radius: Float = 48f) {
        val m = _model.value ?: return
        when (_tool.value) {
            Tool3.EDGE -> { applyOp(); edgeTap(x, y, radius) }
            Tool3.POINT -> { applyOp(); pointTap(x, y, radius) }
            Tool3.ERASE -> {
                val view = currentView() ?: return
                Pick3.stroke(m, view, x, y, radius)?.let { eraseStroke(it) }
            }
            Tool3.PAINT -> pick(x, y)?.let { paintAt(it.first, it.second) }
            Tool3.FACE -> {
                applyOp()
                val hit = pick(x, y)
                if (hit == null) { _selection.value = _selection.value.copy(faces = emptySet()); return }
                val part = m.parts.getOrNull(hit.first) ?: return
                val s = _selection.value
                // v25 Yüzey kipinde dokunulan yüzün bütün yüzeyi seçilir/bırakılır
                val region = if (_faceMode.value == FaceMode.SURFACE) MeshTopo.surfaceRegion(part.mesh, hit.second) else setOf(hit.second)
                _selection.value = if (s.primary == part.id) {
                    s.copy(faces = if (hit.second in s.faces) s.faces - region else s.faces + region)
                } else Selection3(listOf(part.id), region)
            }
            else -> {
                val hit = pick(x, y)
                if (hit == null) { clearSelection(); return }
                m.parts.getOrNull(hit.first)?.let { selectInScene(it.id) }
            }
        }
    }

    /** Taşı/Döndür/Ölçekle'de sürüklenen parça seçili değilse önce seçilir. */
    /** Sürükleme başında parmağın altındaki parçayı (grubuyla) seçer; parça yoksa false (sahne döner). */
    fun grabAt(x: Float, y: Float): Boolean {
        val hit = pick(x, y) ?: return false
        val m = _model.value ?: return false
        val id = m.parts.getOrNull(hit.first)?.id ?: return false
        if (id !in _selection.value.parts) selectInScene(id)
        return true
    }

    /** Seçili yüzün üstünde mi (Yüz aracında sürükleme yüzü taşır). */
    fun onSelectedFace(x: Float, y: Float): Boolean {
        val hit = pick(x, y) ?: return false
        val s = _selection.value
        val m = _model.value ?: return false
        return m.parts.getOrNull(hit.first)?.id == s.primary && hit.second in s.faces
    }

    /** Sürüklemenin dünyadaki karşılığı (eksen kilidiyle; derinlik seçili parçanınki). */
    fun dragDelta(dxScreen: Float, dyScreen: Float): V3? {
        val view = currentView() ?: return null
        val m = _model.value ?: return null
        val p = _selection.value.primary?.let { m.part(it) }
        val depth = p?.let { view.depth(it.position.x, it.position.y, it.position.z) } ?: 0f
        return ModelEdit.dragDelta(view, dxScreen, dyScreen, _axis.value, depth)
    }

    /** Dünya noktasının ekran konumu (görünmüyorsa null). */
    fun toScreen(v: V3): Pair<Float, Float>? {
        val view = currentView() ?: return null
        val s = view.project(v)
        if (s[3] <= 0f) return null
        return s[0] to s[1]
    }

    /** Ekran pikselinin dünya birimindeki boyu (kalınlık için). */
    fun pixelToWorld(): Float = currentView()?.let { 1f / it.scale } ?: 0.01f


    // ------------------------------------------------------------------------------------------ v25 Nokta aracı

    /**
     * Nokta aracında dokunuş: noktaya yakınsa seçer/bırakır (seçim sırası korunur: birleştirme ve kesme bu sırayla);
     * kenara yakınsa oraya yeni nokta ekler ve seçime katar; başka parçaya dokunulursa o parça seçilir; boşa dokunulursa
     * nokta seçimi temizlenir.
     */
    private fun pointTap(x: Float, y: Float, radius: Float) {
        val m = _model.value ?: return
        val view = currentView() ?: return
        val hit = Pick3.pick(m, view, x, y)
        var pi = primaryIndex(m)
        if (pi < 0) {
            if (hit == null) return
            pi = hit.part
            _selection.value = Selection3(listOf(m.parts[pi].id))
        }
        val part = m.parts[pi]
        val s = _selection.value
        val v = Pick3.vertex(m, view, pi, x, y, radius * 0.8f)
        if (v != null && (v.visible || hit == null || hit.part == pi)) {
            _selection.value = s.copy(vertices = if (v.index in s.vertices) s.vertices - v.index else s.vertices + v.index, edges = emptySet())
            return
        }
        val e = Pick3.edge(m, view, pi, x, y, radius)
        if (e != null && (e.second.visible || hit == null || hit.part == pi)) {
            val t = Pick3.edgeParam(m, view, pi, e.first, x, y)
            val (mesh, nv) = MeshOps.splitEdge(part.mesh, e.first, t)
            commit(m.replacePart(part.copy(mesh = mesh, primitive = null)))
            _selection.value = Selection3(listOf(part.id), vertices = s.vertices + nv)
            return
        }
        if (hit != null && hit.part != pi) { _selection.value = Selection3(listOf(m.parts[hit.part].id)); return }
        _selection.value = s.copy(vertices = emptySet(), edges = emptySet())
    }

    /** Nokta aracında noktayı sürükleme (seçim ve sırası korunur); dokunuş noktada değilse false. */
    fun beginPointDrag(x: Float, y: Float, radius: Float): Boolean {
        val m = _model.value ?: return false
        val view = currentView() ?: return false
        val pi = primaryIndex(m).takeIf { it >= 0 } ?: return false
        val part = m.parts[pi]
        val v = Pick3.vertex(m, view, pi, x, y, radius * 0.8f) ?: return false
        val w = Transform3.of(part).apply(part.mesh.vertex(v.index))
        dragVertices = setOf(v.index)
        dragDepth = view.depth(w.x, w.y, w.z)
        beginLive()
        return true
    }

    /** Seçili noktalar (seçim sırasıyla). */
    fun selectedPoints(): List<Int> = _selection.value.vertices.toList()

    /** Seçili noktaları siler: kenar üstündekiler iz bırakmaz, köşeler kesilmiş gibi kapanır. */
    fun deleteSelectedPoints() {
        applyOp()
        val m = _model.value ?: return
        val s = _selection.value
        var part = s.primary?.let { m.part(it) } ?: return
        if (s.vertices.isEmpty()) return
        // Silindikçe noktalar yeniden numaralanır: konumlarıyla bulunur
        val targets = s.vertices.filter { it < part.mesh.vertexCount }.map { part.mesh.vertex(it) }
        var removed = 0
        for (pt in targets) {
            val idx = (0 until part.mesh.vertexCount).firstOrNull { part.mesh.vertex(it) == pt } ?: continue
            val r = MeshTopo.deleteVertex(part.mesh, idx) ?: continue
            if (r.mesh.faceCount < 2) continue
            part = Solid3.apply(part, r)
            removed++
        }
        if (removed == 0) { message(MSG_POINT_DELETE_FAIL); return }
        commit(m.replacePart(part))
        _selection.value = Selection3(listOf(part.id))
        message(MSG_POINTS_DELETED.format(removed))
    }

    /**
     * Noktaları sırayla kenarlarla birleştirilmiş parça (art arda gelen her iki nokta aynı yüzdeyse yüz ikiye bölünür;
     * [close] ile son nokta ilkine de bağlanır). Bir çift aynı yüzde değilse ileti ve null.
     */
    private fun connected(part: Part, pts: List<Int>, close: Boolean): Part? {
        var mesh = part.mesh
        var origin = IntArray(mesh.faceCount) { it }
        val pairs = (0 until pts.size - 1).map { pts[it] to pts[it + 1] } + if (close && pts.size >= 3) listOf(pts.last() to pts.first()) else emptyList()
        for ((i, pair) in pairs.withIndex()) {
            val (a, b) = pair
            if (MeshTopo.isEdge(mesh, a, b)) continue
            val r = MeshTopo.connect(mesh, a, b)
            if (r == null) { message(MSG_NOT_SAME_FACE.format(i + 1, if (i + 2 > pts.size) 1 else i + 2)); return null }
            origin = IntArray(r.origin.size) { f -> origin.getOrElse(r.origin[f]) { -1 } }
            mesh = r.mesh
        }
        return if (mesh === part.mesh) part else Solid3.apply(part, TopoResult(mesh, origin))
    }

    /** Seçili noktaları seçim sırasıyla kenarlarla birleştirir (tek geri alma adımı). */
    fun connectSelectedPoints(close: Boolean = false): Boolean {
        applyOp()
        val m = _model.value ?: return false
        val part = _selection.value.primary?.let { m.part(it) } ?: return false
        val pts = selectedPoints()
        if (pts.size < 2) { message(MSG_SELECT_TWO); return false }
        val joined = connected(part, pts, close) ?: return false
        if (joined !== part) commit(m.replacePart(joined))
        return true
    }

    /**
     * Seçili noktalardan (en az üç, seçim sırasıyla) oluşan halka boyunca keser: eksik kenarlar önce birleştirilir;
     * halkanın içi ayrı parça olur, iki taraf da kapatılır. Geçmişe tek adım girer.
     */
    fun cutSelectedLoop() {
        applyOp()
        val pts = selectedPoints()
        if (pts.size < 3) { message(MSG_SELECT_THREE); return }
        val m = _model.value ?: return
        val part = _selection.value.primary?.let { m.part(it) } ?: return
        val joined = connected(part, pts, close = true) ?: return
        val result = Solid3.cut(joined, pts, UUID.randomUUID().toString())
        if (result == null) { message(MSG_CUT_FAIL); return }
        val (rest, piece) = result
        val at = m.parts.indexOfFirst { it.id == part.id }
        val list = m.parts.toMutableList()
        list[at] = rest
        list.add(at + 1, piece)
        commit(m.copy(parts = list))
        _selection.value = Selection3(listOf(piece.id))
        message(MSG_CUT_DONE)
    }

    // ------------------------------------------------------------------------------------------ v25 yüz kipi ve yüzey

    private val _faceMode = MutableStateFlow(FaceMode.SINGLE)
    val faceMode: StateFlow<FaceMode> = _faceMode.asStateFlow()
    fun setFaceMode(mode: FaceMode) { _faceMode.value = mode }

    /** Seçili yüzleri ayrı parça yapar (yüzey olarak; kalan şekil yerinde kalır). */
    fun detachFaces() {
        applyOp()
        val m = _model.value ?: return
        val s = _selection.value
        val part = s.primary?.let { m.part(it) } ?: return
        val faces = s.faces.filter { it < part.mesh.faceCount }
        if (faces.isEmpty() || faces.size == part.mesh.faceCount) return
        val sub = MeshTopo.subMesh(part.mesh, faces)
        val rest = MeshTopo.deleteFaces(part.mesh, faces.toSet()) ?: return
        val piece = part.copy(id = UUID.randomUUID().toString(), name = com.karapp.drawguidance.i18n.I18n.trf("{0} (yüzey)", part.name), mesh = sub.mesh, primitive = null, faceMaterials = Solid3.remap(part, sub.origin))
        val at = m.parts.indexOfFirst { it.id == part.id }
        val list = m.parts.toMutableList()
        list[at] = Solid3.apply(part, rest)
        list.add(at + 1, piece)
        commit(m.copy(parts = list))
        _selection.value = Selection3(listOf(piece.id))
    }

    /** Seçili yüz bölgesinin dünya merkezi, ortalama normali ve eş alanlı yarıçapı. */
    fun faceRegionFrame(): Triple<V3, V3, Float>? {
        val m = _model.value ?: return null
        val s = _selection.value
        val part = s.primary?.let { m.part(it) } ?: return null
        val faces = s.faces.filter { it < part.mesh.faceCount }
        if (faces.isEmpty()) return null
        val t = Transform3.of(part)
        var area = 0.0; var cx = 0.0; var cy = 0.0; var cz = 0.0; var nx = 0f; var ny = 0f; var nz = 0f
        val out = FloatArray(3)
        for (f in faces) {
            val world = Mesh(part.mesh.pos, arrayOf(part.mesh.faces[f])).mapPositions { t.apply(it) }
            val a = MeshTopo.faceArea(world, 0)
            val c = world.faceCenter(0)
            area += a; cx += c.x * a; cy += c.y * a; cz += c.z * a
            val fn = part.mesh.faceNormal(f)
            t.normal(fn.x, fn.y, fn.z, out, 0)
            nx += out[0] * a.toFloat(); ny += out[1] * a.toFloat(); nz += out[2] * a.toFloat()
        }
        if (area <= 1e-12) return null
        val center = V3((cx / area).toFloat(), (cy / area).toFloat(), (cz / area).toFloat())
        return Triple(center, V3(nx, ny, nz).normalized(), kotlin.math.sqrt(area / Math.PI).toFloat())
    }

    /** Delik önizlemesi (Delik işlemi açıkken): merkez, eksen, yarıçap. */
    fun holePreview(): Triple<V3, V3, Float>? {
        val op = _op.value ?: return null
        if (op.kind != OpKind.HOLE) return null
        val (c, n, r) = faceRegionFrame() ?: return null
        return Triple(c, n, r * op.amount)
    }

    private fun holeThroughFaces(amount: Float) {
        val m = _model.value ?: return
        val part = _selection.value.primary?.let { m.part(it) } ?: return
        val (c, n, r) = faceRegionFrame() ?: return
        runHeavy(MSG_WORKING_HOLE) {
            Solid3.hole(part, c, n, r * amount, Budget3D.holeSegments, Budget3D.csgTriangles)?.let { holed ->
                { mm: Model3D -> mm.replacePart(holed) }
            }
        }
        _selection.value = _selection.value.copy(faces = emptySet())
    }

    // ------------------------------------------------------------------------------------------ v25 katı cisim işlemleri

    private val _busy = MutableStateFlow<String?>(null)
    /** Uzun süren işlem sürüyor (ileti); null boşta. */
    val busy: StateFlow<String?> = _busy.asStateFlow()
    private var heavyJob: kotlinx.coroutines.Job? = null

    /**
     * Ağır işlemi (katı cisim, fırça cismi) arka planda yapar; sonuç modele uygulanacak bir dönüşüm olarak gelir ve tek
     * geri alma adımıyla işlenir. Sonuç null ise başarısız iletisi.
     */
    private fun runHeavy(label: String, failMessage: String = MSG_OP_FAIL, work: suspend () -> ((Model3D) -> Model3D)?) {
        heavyJob?.cancel()
        heavyJob = viewModelScope.launch {
            _busy.value = label
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { work() } }
            _busy.value = null
            val f = r.getOrNull()
            if (f == null) { message(if (r.isFailure) MSG_OP_FAIL else failMessage); return@launch }
            val m = _model.value ?: return@launch
            commit(f(m))
        }
    }

    /** Seçili parçalar üzerinde birleştir/çıkar/kesişim (çıkarmada asıl parçadan diğerleri çıkarılır). */
    fun booleanSelected(op: Solid3.Op) {
        applyOp()
        val m = _model.value ?: return
        val parts = _selection.value.parts.mapNotNull { m.part(it) }
        if (parts.size < 2) { message(MSG_SELECT_TWO_PARTS); return }
        val ids = parts.map { it.id }.toSet()
        val newId = UUID.randomUUID().toString()
        val label = when (op) { Solid3.Op.UNION -> MSG_WORKING_UNION; Solid3.Op.SUBTRACT -> MSG_WORKING_SUBTRACT; Solid3.Op.INTERSECT -> MSG_WORKING_INTERSECT }
        runHeavy(label, if (op == Solid3.Op.INTERSECT) MSG_NO_INTERSECTION else MSG_OP_FAIL) {
            val result = Solid3.boolean(parts, op, newId, parts.first().name, Budget3D.csgTriangles) ?: return@runHeavy null
            val change: (Model3D) -> Model3D = { mm ->
                val at = mm.parts.indexOfFirst { it.id in ids }.coerceAtLeast(0)
                val rest = mm.parts.filterNot { it.id in ids }.toMutableList()
                rest.add(at.coerceIn(0, rest.size), result)
                _selection.value = Selection3(listOf(result.id))
                mm.copy(parts = rest)
            }
            change
        }
    }

    /** Kopuk parçaları ayrı parçalara ayırır. */
    fun separateSelected() {
        applyOp()
        val m = _model.value ?: return
        val part = _selection.value.primary?.let { m.part(it) } ?: return
        val pieces = Solid3.separate(part) { UUID.randomUUID().toString() }
        if (pieces.isEmpty()) { message(MSG_NOTHING_TO_SEPARATE); return }
        val at = m.parts.indexOfFirst { it.id == part.id }
        val list = m.parts.toMutableList()
        list.removeAt(at)
        list.addAll(at, pieces)
        commit(m.copy(parts = list))
        _selection.value = Selection3(pieces.map { it.id })
        message(MSG_SEPARATED.format(pieces.size))
    }

    private val _slice = MutableStateFlow(SliceAxis.Y to 0f)
    /** Parçalama düzlemi: eksen ve konum (−0,5..0,5, parçanın boyuna oran). */
    val slice: StateFlow<Pair<SliceAxis, Float>> = _slice.asStateFlow()
    fun setSlice(axis: SliceAxis = _slice.value.first, offset: Float = _slice.value.second) { _slice.value = axis to offset.coerceIn(-0.49f, 0.49f) }

    /** Parçalama düzleminin dünya noktası ve normali (asıl parçaya göre). */
    fun slicePlane(): Pair<V3, V3>? {
        val m = _model.value ?: return null
        val part = _selection.value.primary?.let { m.part(it) } ?: return null
        val b = ModelEdit.worldBounds(part)
        val (axis, off) = _slice.value
        val n = when (axis) {
            SliceAxis.X -> V3(1f, 0f, 0f)
            SliceAxis.Y -> V3(0f, 1f, 0f)
            SliceAxis.Z -> V3(0f, 0f, 1f)
            SliceAxis.VIEW -> currentView()?.right ?: V3(1f, 0f, 0f)
        }
        val extent = abs(b.size.x * n.x) + abs(b.size.y * n.y) + abs(b.size.z * n.z)
        return (b.center + n * (off * extent)) to n
    }

    /** Asıl parçayı düzlemle ikiye parçalar (iki taraf kapalı, biraz aralanır). */
    fun sliceSelected() {
        applyOp()
        val m = _model.value ?: return
        val part = _selection.value.primary?.let { m.part(it) } ?: return
        val (point, normal) = slicePlane() ?: return
        val newId = UUID.randomUUID().toString()
        runHeavy(MSG_WORKING_SLICE, MSG_SLICE_MISS) {
            val (front, back) = Solid3.slice(part, point, normal, Budget3D.csgTriangles) ?: return@runHeavy null
            val gap = ModelEdit.worldBounds(part).radius * 0.06f
            val a = front.copy(position = front.position + normal * gap)
            val b = back.copy(id = newId, name = "${part.name} (2)", position = back.position - normal * gap)
            val change: (Model3D) -> Model3D = { mm ->
                val at = mm.parts.indexOfFirst { it.id == part.id }
                if (at < 0) mm else {
                    val list = mm.parts.toMutableList()
                    list[at] = a
                    list.add(at + 1, b)
                    _selection.value = Selection3(listOf(a.id, b.id))
                    mm.copy(parts = list)
                }
            }
            change
        }
    }

    private val _holeRatio = MutableStateFlow(0.4f)
    /** İşlem panelindeki delik genişliği (0,05–0,95). */
    val holeRatio: StateFlow<Float> = _holeRatio.asStateFlow()
    fun setHoleRatio(r: Float) { _holeRatio.value = r.coerceIn(0.05f, 0.95f) }

    /** İşlem panelindeki deliğin merkezi, ekseni ve yarıçapı: yüz seçiliyse oradan, değilse parçanın ortasından ekrana dik. */
    fun panelHoleFrame(ratio: Float = _holeRatio.value): Triple<V3, V3, Float>? {
        val m = _model.value ?: return null
        val part = _selection.value.primary?.let { m.part(it) } ?: return null
        val (center, axis, base) = faceRegionFrame() ?: run {
            val b = ModelEdit.worldBounds(part)
            Triple(b.center, currentView()?.toViewer ?: V3(0f, 0f, 1f), minOf(b.size.x, minOf(b.size.y, b.size.z)) * 0.5f)
        }
        return Triple(center, axis, base * ratio.coerceIn(0.05f, 0.95f))
    }

    /** Delik (İşlem panelinden): yüz seçiliyse oradan, değilse parçanın ortasından bakış yönünde. */
    fun holeSelected(ratio: Float = _holeRatio.value) {
        applyOp()
        val m = _model.value ?: return
        val part = _selection.value.primary?.let { m.part(it) } ?: return
        val (center, axis, radius) = panelHoleFrame(ratio) ?: return
        runHeavy(MSG_WORKING_HOLE) {
            val holed = Solid3.hole(part, center, axis, radius, Budget3D.holeSegments, Budget3D.csgTriangles) ?: return@runHeavy null
            val change: (Model3D) -> Model3D = { mm -> mm.replacePart(holed) }
            change
        }
    }

    // ------------------------------------------------------------------------------------------ v25 3B fırça

    private val _brush = MutableStateFlow(Brush3())
    val brush: StateFlow<Brush3> = _brush.asStateFlow()
    fun setBrush(b: Brush3) {
        val line = com.karapp.drawguidance.content.model3d.Brush3Kind.LINE
        // Çizgiden cisim türüne geçerken ince çizgi kalınlığı cisim için yetersiz: makul kalınlığa çıkarılır
        if (_brush.value.kind == line && b.kind != line && _brushWidth.value < SOLID_MIN_WIDTH) _brushWidth.value = SOLID_DEFAULT_WIDTH
        _brush.value = b.copy(height = b.height.coerceIn(0.02f, 3f), opacity = b.opacity.coerceIn(0.1f, 1f))
    }

    /** Düzlemdeki yukarı yön (fırça cisimleri için): dünya yukarısı; düzlem yataysa ekranın yukarısı. */
    private fun planeUp(normal: V3): V3 {
        val n = normal.normalized()
        return if (abs(n.y) < 0.9f) V3.UP else (currentView()?.down?.let { -it } ?: V3(0f, 0f, -1f))
    }

    /**
     * Fırça izi bitti: Çizgi türünde 3B çizgi eklenir; diğer türlerde iz arka planda cisme dönüştürülüp yeni parça olarak
     * eklenir ve seçilir. [points] dünya noktaları (x, y, z…), [pressures] kalem basınçları (yoksa null).
     */
    fun finishBrush(points: FloatArray, pressures: FloatArray?, normal: V3) {
        val b = _brush.value
        val widthWorld = _brushWidth.value * pixelToWorld()
        if (b.kind == com.karapp.drawguidance.content.model3d.Brush3Kind.LINE) {
            addStroke(points, widthWorld)?.let { suggestFor(it, points, normal, planeUp(normal), b.kind, null) }
            return
        }
        if (points.size < 6) return
        val partId = UUID.randomUUID().toString()
        val pts = List(points.size / 3) { V3(points[3 * it], points[3 * it + 1], points[3 * it + 2]) }
        val spec = com.karapp.drawguidance.content.model3d.BrushShapeSpec(
            kind = b.kind, width = widthWorld, height = b.height, bulge = b.bulge, taper = b.taper, smoothing = b.smoothing,
            pressure = b.pressure, sides = Budget3D.brushSides, maxSamples = Budget3D.brushSamples,
        )
        val input = com.karapp.drawguidance.content.model3d.BrushInput(pts, pressures, normal, planeUp(normal))
        val material = Material(color = _brushColor.value, kind = b.material, opacity = b.opacity, shine = 0.5f)
        val name = brushName(b.kind)
        // v26 kaynak (silgi bununla yeniden kurar; "Son çizimi sil" için çizim anı)
        val source = com.karapp.drawguidance.content.model3d.BrushSource(spec, points.copyOf(), pressures?.copyOf(), normal, input.up, time = System.currentTimeMillis())
        runHeavy(MSG_WORKING_BRUSH, MSG_BRUSH_FAIL) {
            val mesh = com.karapp.drawguidance.content.model3d.BrushShapes.build(input, spec) ?: return@runHeavy null
            val part = com.karapp.drawguidance.content.model3d.BrushShapes.toPart(mesh, partId, name, material, source)
            val change: (Model3D) -> Model3D = { mm ->
                _selection.value = Selection3(listOf(part.id))
                mm.copy(parts = mm.parts + part)
            }
            change
        }
        // Öneri cisim oluştuktan sonra görünür (kaynağı yerine geçebilsin)
        suggestFor(partId, points, normal, input.up, b.kind, heavyJob)
    }

    // ------------------------------------------------------------------------------------------ v27 çizim önerisi

    /** Açık öneri: kaynağı (çizgi ya da fırça cismi), çözümleme ve iz. */
    class SuggestUi(
        val sourceId: String,
        val sketch: com.karapp.drawguidance.content.model3d.Sketch2,
        val result: com.karapp.drawguidance.content.model3d.Sketch3Result,
        val points: List<V3>,
    ) {
        val kinds: List<com.karapp.drawguidance.content.model3d.Suggest3Kind> get() = result.suggestions.map { it.kind }
    }

    private val _suggestion = MutableStateFlow<SuggestUi?>(null)
    /** v27 çizimden sonra gösterilen öneri (yoksa null). */
    val suggestion: StateFlow<SuggestUi?> = _suggestion.asStateFlow()

    private val _suggestEnabled = MutableStateFlow(prefs?.suggest?.value ?: true)
    val suggestEnabled: StateFlow<Boolean> = _suggestEnabled.asStateFlow()

    /** v30 açıldığı tuvalde yapay zekâ kapalıysa false (tuvalsiz açılışta true). */
    private val _canvasAiOn = MutableStateFlow(true)
    val canvasAiOn: StateFlow<Boolean> = _canvasAiOn.asStateFlow()

    init {
        val id = canvasId
        val read = canvasAi
        if (id != null && read != null) viewModelScope.launch {
            _canvasAiOn.value = runCatching { read(id) }.getOrDefault(true)
            if (!_canvasAiOn.value) _suggestion.value = null
        }
    }

    fun setSuggestEnabled(v: Boolean) {
        _suggestEnabled.value = v
        prefs?.setSuggest(v)
        if (!v) _suggestion.value = null
        message(if (v) MSG_SUGGEST_ON else MSG_SUGGEST_OFF)
    }

    /** Öneri modeli: ilk kullanımda arka planda eğitilir (sentetik), kişisel durum tercihlerden yüklenir. */
    private val sketchModel: com.karapp.drawguidance.content.model3d.SketchModel by lazy {
        com.karapp.drawguidance.content.model3d.SketchModel.create().also { it.decode(prefs?.sketchModel) }
    }
    private var suggestJob: kotlinx.coroutines.Job? = null

    /** Çizim modunda model önceden hazırlanır (ilk öneri beklemesin). */
    private fun prewarmSuggest() {
        if (!_suggestEnabled.value || !_canvasAiOn.value) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) { runCatching { sketchModel } }
    }

    private fun suggestFor(
        sourceId: String, points: FloatArray, normal: V3, up: V3, drawn: com.karapp.drawguidance.content.model3d.Brush3Kind, after: kotlinx.coroutines.Job?,
    ) {
        _suggestion.value = null
        suggestJob?.cancel()
        if (!_suggestEnabled.value || !_canvasAiOn.value || points.size < 9) return
        val pts = List(points.size / 3) { V3(points[3 * it], points[3 * it + 1], points[3 * it + 2]) }
        val minLength = SUGGEST_MIN_PX * pixelToWorld()
        suggestJob = viewModelScope.launch {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { com.karapp.drawguidance.content.model3d.Sketch3Suggest.analyze(pts, normal, up, drawn, sketchModel, minLength) }.getOrNull()
            } ?: return@launch
            after?.join()
            _suggestion.value = SuggestUi(sourceId, r.first, r.second, pts)
        }
    }

    /** Öneri kapatıldı (✕): gösterilen öneriler hafifçe geri plana düşer. */
    fun dismissSuggestion() {
        val s = _suggestion.value ?: return
        _suggestion.value = null
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            sketchModel.learnDismiss(s.result.features, s.kinds)
            prefs?.setSketchModel(sketchModel.encode())
        }
    }

    /** Öneri süresi doldu (baloncuk kalkar; öğrenme yok). Bu arada yeni öneri geldiyse dokunulmaz. */
    fun expireSuggestion(s: SuggestUi) { if (_suggestion.value === s) _suggestion.value = null }

    /** Öneri kendiliğinden kalktı (yeni işlem, mod değişimi): öğrenme yok. */
    private fun dropSuggestion() {
        suggestJob?.cancel()
        _suggestion.value = null
    }

    /**
     * v27 öneri uygulanır: çizim (çizgi ya da fırça cismi) önerilen cisimle değişir — tek geri alma adımı. Kabul cihazda
     * öğrenilir (bu çizim tarzı o sınıfa eklenir, öneri öne çıkar).
     */
    fun applySuggestion(kind: com.karapp.drawguidance.content.model3d.Suggest3Kind) {
        applyOp()
        val s = _suggestion.value ?: return
        _suggestion.value = null
        val m = _model.value ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            sketchModel.learnAccept(s.result.features, kind)
            prefs?.setSketchModel(sketchModel.encode())
        }
        val b = _brush.value
        val material = Material(color = _brushColor.value, kind = b.material, opacity = b.opacity, shine = 0.5f)
        val label = suggestName(kind)
        val name = "$label ${m.parts.size + 1}"
        val src = s.sourceId
        if (kind.primitive != null) {
            val part = com.karapp.drawguidance.content.model3d.Sketch3Fit.solid(kind, s.sketch, UUID.randomUUID().toString(), m.parts.size, material, 2f * pixelToWorld())
                ?.copy(name = name)
            if (part == null) { message(MSG_SUGGEST_FAIL); return }
            commit(m.copy(strokes = m.strokes.filterNot { it.id == src }, parts = m.parts.filterNot { it.id == src } + part))
            _selection.value = Selection3(listOf(part.id))
            message(MSG_SUGGEST_APPLIED.format(label))
            return
        }
        val fit = com.karapp.drawguidance.content.model3d.Sketch3Fit.brush(kind, s.sketch, s.points) ?: run { message(MSG_SUGGEST_FAIL); return }
        val px = pixelToWorld()
        val spec = com.karapp.drawguidance.content.model3d.BrushShapeSpec(
            kind = fit.kind, width = fit.width ?: (maxOf(_brushWidth.value, SOLID_DEFAULT_WIDTH) * px), height = b.height, bulge = b.bulge,
            taper = b.taper, smoothing = fit.smoothing ?: b.smoothing, pressure = false, sides = Budget3D.brushSides, maxSamples = Budget3D.brushSamples,
        )
        val flat = FloatArray(fit.input.points.size * 3).also { a -> fit.input.points.forEachIndexed { i, p -> a[3 * i] = p.x; a[3 * i + 1] = p.y; a[3 * i + 2] = p.z } }
        val source = com.karapp.drawguidance.content.model3d.BrushSource(spec, flat, null, fit.input.normal, fit.input.up, time = System.currentTimeMillis())
        runHeavy(MSG_WORKING_BRUSH, MSG_SUGGEST_FAIL) {
            val mesh = com.karapp.drawguidance.content.model3d.BrushShapes.build(fit.input, spec) ?: return@runHeavy null
            val part = com.karapp.drawguidance.content.model3d.BrushShapes.toPart(mesh, UUID.randomUUID().toString(), name, material, source)
            val change: (Model3D) -> Model3D = { mm ->
                _selection.value = Selection3(listOf(part.id))
                mm.copy(strokes = mm.strokes.filterNot { it.id == src }, parts = mm.parts.filterNot { it.id == src } + part)
            }
            message(MSG_SUGGEST_APPLIED.format(label))
            change
        }
    }

    // ------------------------------------------------------------------------------------------ v25 resim olarak dışa aktarma

    /**
     * Modelin resmi (ayarlarla): yazılım çizicisiyle, cihazın bellek bütçesine göre örnekleme; açı şu anki görünüm ya da
     * modele sığdırılmış.
     */
    suspend fun renderImage(e: ImageExport, previewSide: Int? = null): android.graphics.Bitmap? {
        val m = _model.value ?: return null
        val long = (previewSide ?: e.size).coerceIn(64, Budget3D.exportMaxSide)
        val w = if (e.aspect >= 1f) long else (long * e.aspect).toInt().coerceAtLeast(16)
        val h = if (e.aspect >= 1f) (long / e.aspect).toInt().coerceAtLeast(16) else long
        textures.preload(m.parts.flatMap { p -> listOfNotNull(p.material.texture) + p.faceMaterials.values.mapNotNull { it.texture } }.toSet())
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val maxSs = kotlin.math.sqrt(Budget3D.exportPixels.toDouble() / (w.toLong() * h)).toInt().coerceIn(1, 3)
            val ss = e.smoothness.coerceIn(1, 3).coerceAtMost(maxSs)
            val bg = if (e.jpeg && e.background == 0) -1 else e.background
            val scene = m.scene.copy(edges = e.edges, shadow = e.shadow, ground = e.ground, background = bg)
            val mm = m.copy(scene = scene)
            val view = if (e.fit) View3.fit(mm, m.camera, w, h, 0.06f) else run {
                // Ekrandaki görünüm, resmin boyuna ölçeklenir
                val ref = currentView()
                if (ref == null) View3.editor(m.camera, w, h) else {
                    val k = minOf(w / ref.width.toFloat(), h / ref.height.toFloat())
                    View3(m.camera, w, h, ref.scale * k, w / 2f, h / 2f)
                }
            }
            val f = com.karapp.drawguidance.content.model3d.Renderer.render(
                mm, view, com.karapp.drawguidance.content.model3d.RenderOptions(textures = textures::cached, supersample = ss, ground = e.ground, shadow = e.shadow, background = bg),
            )
            android.graphics.Bitmap.createBitmap(f.color, w, h, android.graphics.Bitmap.Config.ARGB_8888)
        }
    }

    /** Resmi Resimlerim'e (ve kayıt yerine) yazar; kaydedilen adresi döner. */
    /** v40 resmin varsayılan kayıt yeri (Ayarlar'daki klasör ya da Galeri albümü). */
    val imageLocationLabel: String get() = images?.locationLabel ?: "Pictures/DrawGuidance"

    suspend fun saveImage(e: ImageExport, target: com.karapp.drawguidance.data.images.SaveTarget = com.karapp.drawguidance.data.images.SaveTarget.Default): String? {
        val repo = images ?: return null
        val m = _model.value ?: return null
        val bmp = renderImage(e) ?: return null
        return try {
            val format = if (e.jpeg) com.karapp.drawguidance.data.settings.ImageFormat.JPEG else com.karapp.drawguidance.data.settings.ImageFormat.PNG
            repo.saveBitmap(bmp, m.name, "model3d:${m.id}", format, target = target).uri
        } finally {
            bmp.recycle()
        }
    }

    // ------------------------------------------------------------------------------------------ v29 tuvale aktarma

    /** Aktarılabilecek tuvaller: resim (tuval) ve çizgi roman tuvalleri (geldiği tuval hariç). */
    val transferTargets: kotlinx.coroutines.flow.Flow<List<com.karapp.drawguidance.data.model.CanvasUi>> =
        transfer?.targets(setOf(com.karapp.drawguidance.data.model.CanvasKind.ART, com.karapp.drawguidance.data.model.CanvasKind.COMIC), canvasId)
            ?: kotlinx.coroutines.flow.flowOf(emptyList())

    /**
     * v29 3B şekli bir tuvale aktarır: [vector] ise görünen kenarları (dış hat, kıvrımlar) düzenlenebilir mürekkep
     * çizgileri olarak (tek grup), değilse saydam zeminli resim olarak. Açı modele sığdırılmış şu anki kamera.
     */
    suspend fun transferTo(target: com.karapp.drawguidance.data.transfer.TransferTarget, vector: Boolean): com.karapp.drawguidance.data.transfer.TransferResult? {
        val t = transfer ?: return null
        val m = _model.value ?: return null
        val name = m.name.ifBlank { "3B şekil" }
        return if (vector) {
            val strokes = outlineStrokes(m)
            if (strokes.isEmpty()) null else t.sendStrokes(target, name, strokes, group = true)
        } else {
            val bmp = renderImage(ImageExport(size = minOf(1200, Budget3D.exportMaxSide), background = 0, ground = false, shadow = false, edges = true, fit = true)) ?: return null
            val cropped = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { cropTransparent(bmp) }
            t.sendImage(target, name, cropped)
        }
    }

    /** Modelin görünen çizgileri mürekkep kalemi izleri olarak (1200 piksellik görünümde). */
    suspend fun outlineStrokes(model: Model3D? = _model.value): List<com.karapp.drawguidance.data.model.Stroke> {
        val m = model ?: return emptyList()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val side = 1200
            val view = View3.fit(m, m.camera, side, side, 0.06f)
            com.karapp.drawguidance.content.model3d.Renderer.outlines(m, view)
                .filter { pl -> pl.size >= 4 && run { var len = 0f; for (i in 2 until pl.size step 2) len += kotlin.math.hypot(pl[i] - pl[i - 2], pl[i + 1] - pl[i - 1]); len >= 3f } }
                .map { pl ->
                    com.karapp.drawguidance.data.model.Stroke(
                        id = java.util.UUID.randomUUID().toString(),
                        points = (0 until pl.size / 2).map { i -> com.karapp.drawguidance.data.model.StrokePoint(pl[2 * i], pl[2 * i + 1]) },
                        colorHex = "#1E1E1E", brushType = com.karapp.drawguidance.data.model.BrushType.INK_PEN, sizePx = 3f, opacity = 1f, order = 0,
                    )
                }
        }
    }

    /** Resmin saydam kenarlarını kırpar (boşsa aynısı). */
    private fun cropTransparent(bmp: android.graphics.Bitmap): android.graphics.Bitmap {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 8) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y }
        if (r < l || b < t) return bmp
        val pad = 6
        l = maxOf(0, l - pad); t = maxOf(0, t - pad); r = minOf(w - 1, r + pad); b = minOf(h - 1, b + pad)
        return android.graphics.Bitmap.createBitmap(bmp, l, t, r - l + 1, b - t + 1)
    }

    // ------------------------------------------------------------------------------------------ v26 gruplar

    /** Sahnede dokunulan parçayı seçer: grubu varsa bütün grup (dokunulan asıl parça). */
    fun selectInScene(id: String) {
        applyOp()
        val m = _model.value ?: return
        val g = m.part(id)?.group
        _selection.value = if (g == null) Selection3(listOf(id)) else Selection3(listOf(id) + m.parts.filter { it.group == g && it.id != id }.map { it.id })
    }

    /** Grubun bütün parçalarını seçer. */
    fun selectGroup(groupId: String) {
        applyOp()
        val m = _model.value ?: return
        val ids = m.parts.filter { it.group == groupId }.map { it.id }
        if (ids.isNotEmpty()) _selection.value = Selection3(ids)
    }

    /** Seçili parçaları (en az iki) yeni grupta toplar; önceki gruplarından çıkarlar. */
    fun groupSelected() {
        applyOp()
        val m = _model.value ?: return
        val ids = _selection.value.parts.toSet()
        if (ids.size < 2) { message(MSG_GROUP_TWO); return }
        val n = (1..999).first { k -> m.groups.none { it.name == "$GROUP $k" } }
        val g = Group3(UUID.randomUUID().toString(), "$GROUP $n")
        commit(m.copy(parts = m.parts.map { if (it.id in ids) it.copy(group = g.id) else it }, groups = m.groups + g))
        message(MSG_GROUPED.format(g.name, ids.size))
    }

    /** Seçili parçaların gruplarını çözer. */
    fun ungroupSelected() {
        applyOp()
        val m = _model.value ?: return
        val groups = _selection.value.parts.mapNotNull { m.part(it)?.group }.toSet()
        if (groups.isEmpty()) return
        commit(m.copy(parts = m.parts.map { if (it.group in groups) it.copy(group = null) else it }, groups = m.groups.filterNot { it.id in groups }))
    }

    fun ungroup(groupId: String) {
        val m = _model.value ?: return
        commit(m.copy(parts = m.parts.map { if (it.group == groupId) it.copy(group = null) else it }, groups = m.groups.filterNot { it.id == groupId }))
    }

    fun renameGroup(groupId: String, name: String) {
        val m = _model.value ?: return
        if (name.isBlank()) return
        commit(m.copy(groups = m.groups.map { if (it.id == groupId) it.copy(name = name.trim()) else it }))
    }

    /** Grubun tüm parçalarını gizler/gösterir (biri görünüyorsa hepsi gizlenir). */
    fun toggleGroupVisible(groupId: String) {
        val m = _model.value ?: return
        val members = m.parts.filter { it.group == groupId }
        val show = members.none { it.visible }
        commit(m.copy(parts = m.parts.map { if (it.group == groupId) it.copy(visible = show) else it }))
    }

    /** Seçimde gruplu parça var mı (Grubu çöz düğmesi). */
    fun selectionHasGroup(): Boolean {
        val m = _model.value ?: return false
        return _selection.value.parts.any { m.part(it)?.group != null }
    }

    /** Tek üyesi kalan ya da kaydı olmayan gruplar çözülür (aynı model değişmediyse aynı nesne döner). */
    private fun normalizeGroups(m: Model3D): Model3D {
        if (m.groups.isEmpty() && m.parts.none { it.group != null }) return m
        val counts = HashMap<String, Int>()
        for (p in m.parts) p.group?.let { counts[it] = (counts[it] ?: 0) + 1 }
        val valid = m.groups.filter { (counts[it.id] ?: 0) >= 2 }.map { it.id }.toSet()
        if (valid.size == m.groups.size && m.parts.all { it.group == null || it.group in valid }) return m
        return m.copy(
            parts = m.parts.map { if (it.group != null && it.group !in valid) it.copy(group = null) else it },
            groups = m.groups.filter { it.id in valid },
        )
    }

    // ------------------------------------------------------------------------------------------ v26 silgi ve temizleme

    private val _eraserSize = MutableStateFlow(24f)
    /** Silgi yarıçapı (dp). */
    val eraserSize: StateFlow<Float> = _eraserSize.asStateFlow()
    fun setEraserSize(dp: Float) { _eraserSize.value = dp.coerceIn(8f, 60f) }

    /** Silgi sürmeye başladı: süren silme geçmişe tek adım girer. */
    fun beginErase() { applyOp(); beginLive() }

    /**
     * Silgi izinin son parçası ([xs], [ys] ekran pikseli, [radius] piksel): 3B çizgilerin ve tüp/şerit/duvar/torna
     * çizimlerinin değen kısmı hemen silinir (hızlı). Şişir ve prizma çizimleri parmak kalkınca oyulur.
     */
    fun eraseAlong(xs: FloatArray, ys: FloatArray, radius: Float) {
        val m = _model.value ?: return
        val view = currentView() ?: return
        if (xs.isEmpty()) return
        val e = com.karapp.drawguidance.content.model3d.BrushErase.Eraser(xs, ys, radius)
        var changed = false
        val strokes = m.strokes.flatMap { st ->
            com.karapp.drawguidance.content.model3d.BrushErase.eraseStroke(st, view, e) { UUID.randomUUID().toString() }?.also { changed = true } ?: listOf(st)
        }
        val parts = m.parts.flatMap { p ->
            com.karapp.drawguidance.content.model3d.BrushErase.erasePart(p, view, e) { UUID.randomUUID().toString() }?.also { changed = true } ?: listOf(p)
        }
        if (!changed) return
        commit(m.copy(parts = parts, strokes = strokes))
        cleanSelection()
    }

    /** Silgi kalktı: süren silme tek adım; şişir/prizma çizimleri bütün iz boyunca arka planda oyulur (ayrı adım). */
    fun endErase(xs: FloatArray, ys: FloatArray, radius: Float) {
        endLive()
        val m = _model.value ?: return
        val view = currentView() ?: return
        if (xs.isEmpty()) return
        val e = com.karapp.drawguidance.content.model3d.BrushErase.Eraser(xs, ys, radius)
        val areas = m.parts.filter { com.karapp.drawguidance.content.model3d.BrushErase.isArea(it) && com.karapp.drawguidance.content.model3d.BrushErase.onScreen(it, view, e) }
        if (areas.isEmpty()) return
        runHeavy(MSG_WORKING_ERASE, MSG_NOTHING_ERASED) {
            val results = HashMap<String, List<Part>>()
            for (p in areas) com.karapp.drawguidance.content.model3d.BrushErase.carvePart(p, view, e) { UUID.randomUUID().toString() }?.let { results[p.id] = it }
            if (results.isEmpty()) return@runHeavy null
            val change: (Model3D) -> Model3D = { mm -> mm.copy(parts = mm.parts.flatMap { results[it.id] ?: listOf(it) }) }
            change
        }
    }

    /** En son çizileni (3B çizgi ya da fırça cismi; silgiyle bölünmüş parçaları birlikte) siler. */
    fun deleteLastDrawing() {
        applyOp()
        dropSuggestion()
        val m = _model.value ?: return
        val latest = (m.strokes.map { it.time } + m.parts.mapNotNull { it.brush?.time }).maxOrNull()
        if (latest == null) { message(MSG_NO_DRAWINGS); return }
        commit(m.copy(strokes = m.strokes.filterNot { it.time == latest }, parts = m.parts.filterNot { it.brush?.time == latest }))
        cleanSelection()
    }

    /** Bütün çizimleri (3B çizgiler ve fırça cisimleri) siler. */
    fun clearDrawings() {
        applyOp()
        dropSuggestion()
        val m = _model.value ?: return
        if (!hasDrawings(m)) { message(MSG_NO_DRAWINGS); return }
        commit(m.copy(strokes = emptyList(), parts = m.parts.filter { it.brush == null }))
        cleanSelection()
    }

    // ------------------------------------------------------------------------------------------ kaydetme ve dışa aktarma

    fun rename(name: String) {
        val m = _model.value ?: return
        _model.value = m.copy(name = name.ifBlank { m.name })
        _dirty.value = true
    }

    /** Kaydeder: "3B Şekillerim"e yazılır; tuvalden açıldıysa tuvaldeki şekil de güncellenir/eklenir. */
    fun save(exitAfter: Boolean = false) {
        applyOp()
        val m = _model.value ?: return
        viewModelScope.launch {
            repository.save(m)
            if (canvasId != null) results.post(Model3DResults.Result(canvasId, strokeId, m))
            _dirty.value = false
            _events.emit(Event3.Saved(m.name, canvasId != null))
            if (exitAfter) _events.emit(Event3.Exit)
        }
    }

    /** Farklı kaydet: yeni bir 3B şekil olarak (düzenleyici yeni kopyayla devam eder). */
    fun saveAs(name: String) {
        applyOp()
        val m = _model.value ?: return
        viewModelScope.launch {
            val copy = repository.saveCopy(m, name.ifBlank { m.name })
            _model.value = copy
            _dirty.value = false
            _events.emit(Event3.Saved(copy.name, false))
        }
    }

    /** v24 GLB olarak dışa aktarma verisi (dokular gömülü). */
    suspend fun exportGlb(): ByteArray? {
        applyOp()
        val m = _model.value ?: return null
        return repository.exportGlb(m)
    }

    fun message(text: String) { _events.tryEmit(Event3.Message(text)) }

    companion object {
        const val DEFAULT_NAME = "Yeni 3B şekil"
        private const val MAX_HISTORY = 60
        private const val COALESCE_MS = 1500L
        private const val ROUND_SEGMENTS = 3
        private const val GPU_FALLBACK = "Ekran kartıyla çizilemedi; yazılım çizici kullanılıyor"
        private const val POINT_ADDED = "Nokta eklendi: sürükleyerek taşıyın"
        private const val ADDED = "%s eklendi"
        /** Her modda açık kalan paneller (diğerleri mod değişince kapanır). */
        private val GLOBAL_PANELS = setOf(Panel3.PARTS, Panel3.SETTINGS)
        const val GROUP = "Grup"
        private const val MSG_GROUP_TWO = "Gruplamak için en az iki parça seçin"
        private const val MSG_GROUPED = "%s: %d parça birlikte hareket eder"
        private const val MSG_WORKING_ERASE = "Siliniyor…"
        private const val MSG_NOTHING_ERASED = "Silgi bir çizime değmedi"
        private const val MSG_NO_DRAWINGS = "Silinecek çizim yok"

        /** Modelde çizim (3B çizgi ya da fırça cismi) var mı. */
        fun hasDrawings(m: Model3D): Boolean = m.strokes.isNotEmpty() || m.parts.any { it.brush != null }

        /** 3×3 döndürme matrisi (satır düzeni) ile yön. */
        private fun mul(r: FloatArray, v: V3) = V3(r[0] * v.x + r[1] * v.y + r[2] * v.z, r[3] * v.x + r[4] * v.y + r[5] * v.z, r[6] * v.x + r[7] * v.y + r[8] * v.z)

        private fun mat(a: FloatArray, b: FloatArray) = FloatArray(9) { k ->
            val i = k / 3; val j = k % 3
            a[3 * i] * b[j] + a[3 * i + 1] * b[3 + j] + a[3 * i + 2] * b[6 + j]
        }

        /** Rz·Ry·Rx matrisinden derece cinsinden açılar ([Transform3.rotation]'ın tersi). */
        internal fun euler(r: FloatArray): V3 {
            val sy = (-r[6]).coerceIn(-1f, 1f)
            val y = Math.toDegrees(kotlin.math.asin(sy.toDouble())).toFloat()
            return if (abs(sy) < 0.9999f) {
                V3(Math.toDegrees(kotlin.math.atan2(r[7].toDouble(), r[8].toDouble())).toFloat(), y, Math.toDegrees(kotlin.math.atan2(r[3].toDouble(), r[0].toDouble())).toFloat())
            } else {
                V3(Math.toDegrees(kotlin.math.atan2(-r[5].toDouble(), r[4].toDouble())).toFloat(), y, 0f)
            }
        }
        private const val MSG_WORKING_IMPORT = "İçe aktarılıyor…"
        private const val MSG_IMPORT_FAIL = "GLB dosyası içe aktarılamadı"
        private const val MSG_IMPORTED = "\"%s\" eklendi (%d üçgen)"
        private const val MSG_IMPORTED_SAVED = "\"%s\" eklendi ve 3B Şekillerim'e ayrıca kaydedildi (%d üçgen)"
        private const val MSG_SUGGEST_APPLIED = "%s yapıldı · geri al ile çizime dönebilirsiniz"
        private const val MSG_SUGGEST_FAIL = "Öneri bu çizime uygulanamadı"
        private const val MSG_SUGGEST_ON = "Çizim önerileri açık"
        private const val MSG_SUGGEST_OFF = "Çizim önerileri kapalı"
        /** Öneri için en kısa iz (ekran pikseli). */
        private const val SUGGEST_MIN_PX = 48f

        /** v27 önerilerin adları (parça adı ve ileti). */
        fun suggestName(k: com.karapp.drawguidance.content.model3d.Suggest3Kind): String = when (k) {
            com.karapp.drawguidance.content.model3d.Suggest3Kind.SPHERE -> "Küre"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.ELLIPSOID -> "Elipsoit"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.CYLINDER -> "Silindir"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.TORUS -> "Simit"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.DOME -> "Kubbe"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.CAPSULE -> "Kapsül"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.CUBE -> "Küp"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.BOX -> "Kutu"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.SLAB -> "Levha"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.PYRAMID -> "Piramit"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.CONE -> "Koni"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.PRISM3 -> "Üçgen prizma"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.PRISM6 -> "Altıgen prizma"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.STAR -> "Yıldız"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.ROD -> "Çubuk"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.TUBE -> "Tüp"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.RIBBON -> "Şerit"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.WALL -> "Duvar"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.LATHE -> "Torna"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.INFLATE -> "Şişirme"
            com.karapp.drawguidance.content.model3d.Suggest3Kind.SPRING -> "Yay"
        }
        private const val SOLID_MIN_WIDTH = 12f
        private const val SOLID_DEFAULT_WIDTH = 18f
        private const val MSG_POINTS_DELETED = "%d nokta silindi"
        private const val MSG_POINT_DELETE_FAIL = "Bu nokta silinemiyor"
        private const val MSG_SELECT_TWO = "Birleştirmek için en az iki nokta seçin"
        private const val MSG_SELECT_THREE = "Kesmek için halka oluşturan en az üç nokta seçin"
        private const val MSG_NOT_SAME_FACE = "%d. ve %d. noktalar aynı yüzde değil: araya nokta ekleyip adım adım birleştirin"
        private const val MSG_CUT_FAIL = "Seçili noktalar şekli ikiye ayıran kapalı bir halka oluşturmuyor"
        private const val MSG_CUT_DONE = "Kesildi: kesilen parça seçili"
        private const val MSG_SELECT_TWO_PARTS = "En az iki parça seçin (Parçalar panelinde kutucuklarla)"
        private const val MSG_OP_FAIL = "İşlem yapılamadı"
        private const val MSG_NO_INTERSECTION = "Parçalar kesişmiyor"
        private const val MSG_NOTHING_TO_SEPARATE = "Parça zaten tek parça"
        private const val MSG_SEPARATED = "%d parçaya ayrıldı"
        private const val MSG_SLICE_MISS = "Düzlem parçayı kesmiyor"
        private const val MSG_BRUSH_FAIL = "Çizgiden cisim oluşmadı: biraz daha uzun çizin"
        private const val MSG_WORKING_UNION = "Birleştiriliyor…"
        private const val MSG_WORKING_SUBTRACT = "Çıkarılıyor…"
        private const val MSG_WORKING_INTERSECT = "Kesişim alınıyor…"
        private const val MSG_WORKING_SLICE = "Parçalanıyor…"
        private const val MSG_WORKING_HOLE = "Delik açılıyor…"
        private const val MSG_WORKING_BRUSH = "Cisim oluşturuluyor…"

        /** 3B fırça türlerinin adları. */
        fun brushName(k: com.karapp.drawguidance.content.model3d.Brush3Kind): String = when (k) {
            com.karapp.drawguidance.content.model3d.Brush3Kind.LINE -> "Çizgi"
            com.karapp.drawguidance.content.model3d.Brush3Kind.TUBE -> "Tüp"
            com.karapp.drawguidance.content.model3d.Brush3Kind.RIBBON -> "Şerit"
            com.karapp.drawguidance.content.model3d.Brush3Kind.WALL -> "Duvar"
            com.karapp.drawguidance.content.model3d.Brush3Kind.LATHE -> "Torna"
            com.karapp.drawguidance.content.model3d.Brush3Kind.INFLATE -> "Şişir"
        }

        /** Malzeme türlerinin adları. */
        fun kindLabel(k: MaterialKind): String = when (k) {
            MaterialKind.MATTE -> "Mat"
            MaterialKind.PLASTIC -> "Plastik"
            MaterialKind.METAL -> "Metal"
            MaterialKind.GLASS -> "Cam"
            MaterialKind.GLOW -> "Işıklı"
        }

        /** Parçanın dünya kutusu (sınama). */
        internal fun partCenter(p: Part): V3 = Transform3.of(p).apply(p.mesh.bounds.center)
    }
}
