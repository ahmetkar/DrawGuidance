package com.karapp.drawguidance.ui.screens.sketch3d

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karapp.drawguidance.content.model3d.Brush3D
import com.karapp.drawguidance.content.model3d.Camera3
import com.karapp.drawguidance.content.model3d.Layer3
import com.karapp.drawguidance.content.model3d.Material
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.ModelEdit
import com.karapp.drawguidance.content.model3d.Part
import com.karapp.drawguidance.content.model3d.Pick3
import com.karapp.drawguidance.content.model3d.Plane3
import com.karapp.drawguidance.content.model3d.PlaneSnap
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.content.model3d.RenderOptions
import com.karapp.drawguidance.content.model3d.Renderer
import com.karapp.drawguidance.content.model3d.SceneSettings
import com.karapp.drawguidance.content.model3d.Stroke3
import com.karapp.drawguidance.content.model3d.V3
import com.karapp.drawguidance.content.model3d.View3
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.library.TextureRepository
import com.karapp.drawguidance.data.settings.Model3DPrefs
import com.karapp.drawguidance.ui.screens.model3d.Budget3D
import com.karapp.drawguidance.ui.screens.model3d.Image3DExporter
import com.karapp.drawguidance.ui.screens.model3d.ImageExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** v56 3B Çizim modları (alt mod çubuğu). */
enum class SketchMode { DRAW, PLANE, SELECT, ADD, VIEW }

/** Çiz modunun araçları. */
enum class SketchTool { PEN, ERASER }

/** Seç modunda sürüklemenin yaptığı (Dönüştür panelinin kipleri). */
enum class SketchXform { MOVE, ROTATE, SCALE }

/** Kaydetme, çıkış ve bildirim olayları. */
sealed interface SketchEvent {
    data class Saved(val name: String) : SketchEvent
    data object Exit : SketchEvent
    data class Message(val text: String) : SketchEvent
}

/**
 * v56 3B Çizim ekranının ViewModel'i: eskiz ([Model3D]: çizgiler, cisimler, katmanlar, son düzlem), mod ve araç,
 * görünür çizim düzlemi ([Plane3]; hazır yönler, yüze/çizgi ucuna/kesişime yapıştırma, 15° döndürme), basınçlı çizgi,
 * silgi (çizgiyi değdiği yerden böler), seçim ve dönüştürme (taşı, döndür, ölçekle; Bitti tek geri alma adımı),
 * temel cisimler, katmanlar ve geri al/yinele. Kayıt D3 v2 olarak 3B Çizimlerim'e yazılır; çıktı resimdir.
 */
class Sketch3DViewModel(
    private val repository: Model3DRepository,
    private val textures: TextureRepository,
    private val modelId: String?,
    private val prefs: Model3DPrefs? = null,
    private val images: com.karapp.drawguidance.data.images.ImagesRepository? = null,
    private val initialName: String? = null,
) : ViewModel(), Image3DExporter {

    private val _model = MutableStateFlow<Model3D?>(null)
    val model: StateFlow<Model3D?> = _model.asStateFlow()

    private val _mode = MutableStateFlow(SketchMode.DRAW)
    val mode: StateFlow<SketchMode> = _mode.asStateFlow()

    private val _tool = MutableStateFlow(SketchTool.PEN)
    val tool: StateFlow<SketchTool> = _tool.asStateFlow()

    private val _brush = MutableStateFlow(Brush3D.PEN)
    val brush: StateFlow<Brush3D> = _brush.asStateFlow()

    private val _color = MutableStateFlow(0xFF263238.toInt())
    val color: StateFlow<Int> = _color.asStateFlow()

    /** Fırça kalınlığı (ekran pikseli; dünyaya kameranın ölçeğiyle çevrilir). */
    private val _size = MutableStateFlow(6f)
    val size: StateFlow<Float> = _size.asStateFlow()

    private val _opacity = MutableStateFlow(1f)
    val opacity: StateFlow<Float> = _opacity.asStateFlow()

    /** Silgi yarıçapı (dp). */
    private val _eraserSize = MutableStateFlow(20f)
    val eraserSize: StateFlow<Float> = _eraserSize.asStateFlow()

    private val _plane = MutableStateFlow(PlaneSnap.preset(PlaneSnap.Preset.FRONT, V3(0f, 0.5f, 0f)))
    val plane: StateFlow<Plane3> = _plane.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _xform = MutableStateFlow(SketchXform.MOVE)
    val xform: StateFlow<SketchXform> = _xform.asStateFlow()

    /** Döndürme ekseni (0 X, 1 Y, 2 Z). */
    private val _axis = MutableStateFlow(1)
    val axis: StateFlow<Int> = _axis.asStateFlow()

    private val _activeLayer = MutableStateFlow(DEFAULT_LAYER)
    val activeLayer: StateFlow<String> = _activeLayer.asStateFlow()

    /** Parmakla çiz (kalemsiz cihazlar); kalem görülünce kapanır. Açıkken tek parmak çizer, iki parmak döndürür. */
    private val _fingerDraw = MutableStateFlow(true)
    val fingerDraw: StateFlow<Boolean> = _fingerDraw.asStateFlow()
    private var fingerDrawTouched = false

    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _events = MutableSharedFlow<SketchEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SketchEvent> = _events

    /** Doku yüklenince sahne yeniden çizilsin diye artar. */
    private val _renderTick = MutableStateFlow(0)
    val renderTick: StateFlow<Int> = _renderTick.asStateFlow()

    // ------------------------------------------------------------------------------------------ çizici tercihleri

    val gpu: StateFlow<Boolean> = if (prefs == null) MutableStateFlow(false) else
        combine(prefs.gpu, prefs.available) { on, ok -> on && ok == true }.stateIn(viewModelScope, SharingStarted.Eagerly, prefs.gpu.value && prefs.available.value == true)
    val quality: StateFlow<Int> = prefs?.quality ?: MutableStateFlow(Model3DPrefs.QUALITY_BALANCED)

    fun gpuFailed(reason: String) { prefs?.gpuFailed(reason); message(GPU_FALLBACK) }
    fun gpuName(name: String) = prefs?.gpuInfo(name)

    private val undo = ArrayDeque<Model3D>()
    private val redo = ArrayDeque<Model3D>()
    private var liveBase: Model3D? = null

    init {
        prefs?.probe()
        viewModelScope.launch {
            val loaded = modelId?.let { repository.load(it) }
            val base = loaded ?: Model3D(
                id = modelId ?: UUID.randomUUID().toString(),
                name = initialName ?: DEFAULT_NAME,
                parts = emptyList(),
                camera = Camera3(yaw = 20f, pitch = 16f, target = V3(0f, 0.5f, 0f)),
            )
            val m = withLayers(base)
            textures.preload(m.parts.flatMap { p -> listOfNotNull(p.material.texture) + p.faceMaterials.values.mapNotNull { it.texture } }.toSet())
            _model.value = m
            m.plane?.let { _plane.value = it }
            _activeLayer.value = m.layers.first().id
            if (loaded == null) _dirty.value = true
        }
    }

    /** Katmanı olmayan (eski) çizime ilk katman eklenir. */
    private fun withLayers(m: Model3D): Model3D =
        if (m.layers.isNotEmpty()) m else m.copy(layers = listOf(Layer3(DEFAULT_LAYER, LAYER_NAME.format(1))))

    // ------------------------------------------------------------------------------------------ dokular

    fun texture(id: String) = textures.cached(id) ?: run {
        val first = synchronized(loading) { loading.add(id) }
        if (first) viewModelScope.launch {
            textures.texture(id)?.let { _renderTick.value++ }
            synchronized(loading) { loading.remove(id) }
        }
        null
    }
    private val loading = HashSet<String>()

    // ------------------------------------------------------------------------------------------ geçmiş

    private fun commit(next: Model3D) {
        val current = _model.value ?: return
        if (next === current) return
        if (liveBase == null && next != current) pushUndo(current)
        _model.value = next
        _dirty.value = true
    }

    private fun pushUndo(m: Model3D) {
        undo.addLast(m)
        while (undo.size > com.karapp.drawguidance.platform.DeviceCaps.historyLimit(MAX_HISTORY)) undo.removeFirst()
        redo.clear()
        refreshFlags()
    }

    fun beginLive() { if (liveBase == null) liveBase = _model.value }

    fun endLive() {
        val base = liveBase ?: return
        liveBase = null
        val now = _model.value ?: return
        if (now !== base && now.copy(camera = base.camera) != base) pushUndo(base)
    }

    fun undo() {
        val current = _model.value ?: return
        val prev = undo.removeLastOrNull() ?: return
        redo.addLast(current)
        _model.value = prev.copy(camera = current.camera)
        _dirty.value = true
        cleanSelection()
        refreshFlags()
    }

    fun redo() {
        val current = _model.value ?: return
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(current)
        _model.value = next.copy(camera = current.camera)
        _dirty.value = true
        cleanSelection()
        refreshFlags()
    }

    private fun refreshFlags() { _canUndo.value = undo.isNotEmpty(); _canRedo.value = redo.isNotEmpty() }

    private fun cleanSelection() {
        val m = _model.value ?: return
        val ids = m.strokes.map { it.id }.toSet() + m.parts.map { it.id }
        _selection.value = _selection.value.filter { it in ids }.toSet()
    }

    // ------------------------------------------------------------------------------------------ mod ve araç

    fun setMode(m: SketchMode) {
        if (_mode.value == SketchMode.SELECT && m != SketchMode.SELECT) _selection.value = emptySet()
        _mode.value = m
    }
    fun setTool(t: SketchTool) { _tool.value = t; _mode.value = SketchMode.DRAW }
    fun setBrush(b: Brush3D) { _brush.value = b; _tool.value = SketchTool.PEN; _mode.value = SketchMode.DRAW }
    fun setColor(c: Int) { _color.value = c }
    fun setSize(px: Float) { _size.value = px.coerceIn(1f, 80f) }
    fun setOpacity(v: Float) { _opacity.value = v.coerceIn(0.05f, 1f) }
    fun setEraserSize(dp: Float) { _eraserSize.value = dp.coerceIn(6f, 60f) }
    fun setXform(x: SketchXform) { _xform.value = x }
    fun setAxis(a: Int) { _axis.value = a.coerceIn(0, 2) }

    fun setFingerDraw(v: Boolean) { _fingerDraw.value = v; fingerDrawTouched = true }

    /** Kalem görüldü: kullanıcı elle seçmediyse parmak çizimi kapanır (avuç çizimi bozmaz). */
    fun stylusSeen() { if (!fingerDrawTouched && _fingerDraw.value) _fingerDraw.value = false }

    // ------------------------------------------------------------------------------------------ görünüm

    @Volatile private var viewW = 0
    @Volatile private var viewH = 0

    fun setViewport(w: Int, h: Int) { viewW = w; viewH = h }

    fun currentView(): View3? {
        val m = _model.value ?: return null
        if (viewW <= 0 || viewH <= 0) return null
        return View3.editor(m.camera, viewW, viewH)
    }

    fun camera(): Camera3 = _model.value?.camera ?: Camera3()

    private fun setCamera(c: Camera3) {
        val m = _model.value ?: return
        _model.value = m.copy(camera = c)
        _dirty.value = true
    }

    fun orbit(dYaw: Float, dPitch: Float) {
        val c = camera()
        setCamera(c.copy(yaw = ((c.yaw + dYaw) % 360f + 360f) % 360f, pitch = (c.pitch + dPitch).coerceIn(-89f, 89f)))
    }

    fun zoom(factor: Float) { val c = camera(); setCamera(c.copy(zoom = (c.zoom * factor).coerceIn(0.1f, 30f))) }

    fun pan(dx: Float, dy: Float) {
        val view = currentView() ?: return
        val c = camera()
        setCamera(c.copy(target = c.target + view.screenDelta(-dx, -dy)))
    }

    fun setPerspective(p: Float) = setCamera(camera().copy(persp = p.coerceIn(0f, 1f)))

    /** Hazır açılar: 0 izometrik, 1 ön, 2 yan, 3 üst. */
    fun presetView(kind: Int) {
        val m = _model.value ?: return
        val center = bounds(m)?.center ?: _plane.value.origin
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

    fun fitView() {
        val m = _model.value ?: return
        val b = bounds(m) ?: return
        val r = b.radius.coerceAtLeast(0.2f)
        setCamera(camera().copy(target = b.center, zoom = (View3.REFERENCE_EXTENT / (r * 1.15f)).coerceIn(0.1f, 30f)))
    }

    private fun bounds(m: Model3D): com.karapp.drawguidance.content.model3d.Box3? {
        var box = ModelEdit.modelBounds(m.copy(strokes = emptyList()))
        for (s in m.strokes) for (i in 0 until s.count) {
            val p = V3(s.points[3 * i], s.points[3 * i + 1], s.points[3 * i + 2])
            val b = com.karapp.drawguidance.content.model3d.Box3(p, p)
            box = box?.union(b) ?: b
        }
        return box
    }

    /** Bir ekran pikselinin düzlemin merkezindeki dünya boyu. */
    fun pixelToWorld(): Float {
        val view = currentView() ?: return 0.01f
        val s = view.project(_plane.value.origin)
        val k = if (s[3] > 0f) s[3] else 1f
        return 1f / (view.scale * k)
    }

    fun setScene(scene: SceneSettings) { val m = _model.value ?: return; commit(m.copy(scene = scene)) }

    // ------------------------------------------------------------------------------------------ düzlem

    /** Görünen düzlemin yarı kenarı (çizimin boyuna göre). */
    fun planeHalf(): Float = max(1.2f, (_model.value?.let { bounds(it)?.radius } ?: 0f) * 1.3f)

    fun setPlanePreset(kind: PlaneSnap.Preset) {
        _plane.value = PlaneSnap.preset(kind, _plane.value.origin, currentView())
        persistPlane()
    }

    /** Düzlemi dokunulan yere yapıştırır: çizgi ucu / kesişim (yön korunur) ya da yüz. */
    fun snapPlaneAt(x: Float, y: Float, radiusPx: Float): Boolean {
        val m = _model.value ?: return false
        val view = currentView() ?: return false
        val visible = m.visibleLayers()
        PlaneSnap.snapPoint(visible, view, x, y, radiusPx)?.let { p ->
            _plane.value = _plane.value.copy(origin = p, id = "point")
            persistPlane(); message(MSG_SNAP_POINT); return true
        }
        Pick3.pick(visible, view, x, y)?.let { hit ->
            _plane.value = PlaneSnap.onFace(visible, hit)
            persistPlane(); message(MSG_SNAP_FACE); return true
        }
        message(MSG_SNAP_NONE)
        return false
    }

    /** Düzlemi normali boyunca kaydırır (ekrandaki sürükleme pikseli). */
    fun dragPlane(dyPx: Float) {
        _plane.value = PlaneSnap.offset(_plane.value, -dyPx * pixelToWorld())
    }

    fun endPlaneDrag() = persistPlane()

    fun rotatePlane(axis: Int, deg: Float) {
        _plane.value = PlaneSnap.rotate(_plane.value, axis, deg)
        persistPlane()
    }

    fun planeToCamera() {
        val view = currentView() ?: return
        _plane.value = PlaneSnap.faceCamera(_plane.value, view)
        persistPlane()
    }

    fun planeTilted(): Boolean = currentView()?.let { PlaneSnap.tooTilted(_plane.value, it) } ?: false

    /** Düzlem kayda girer (geri alma geçmişine girmez). */
    private fun persistPlane() {
        val m = _model.value ?: return
        _model.value = m.copy(plane = _plane.value)
        _dirty.value = true
    }

    // ------------------------------------------------------------------------------------------ çizim

    private fun layerWritable(): Boolean {
        val m = _model.value ?: return false
        if (m.layerLocked(_activeLayer.value)) { message(MSG_LAYER_LOCKED); return false }
        if (m.layerHidden(_activeLayer.value)) { message(MSG_LAYER_HIDDEN); return false }
        return true
    }

    /** Ekran noktasının çizim düzlemindeki dünya karşılığı (önizleme ve testler için). */
    fun planePoint(x: Float, y: Float): V3? = currentView()?.let { PlaneSnap.project(_plane.value, it, x, y) }

    /**
     * Ekranda çizilen izi ([xy] çiftler) düzleme izdüşürüp çizgi ekler. [pressures] kalem basıncı (parmakta null);
     * [straight] basılı tutulunca düz çizgi. Fırça türü kalınlık eğrisini ve örtücülüğü belirler.
     */
    fun finishStroke(xy: FloatArray, pressures: FloatArray?, straight: Boolean): String? {
        val m = _model.value ?: return null
        val view = currentView() ?: return null
        if (xy.size < 4 || !layerWritable()) return null
        val src = if (straight) floatArrayOf(xy[0], xy[1], xy[xy.size - 2], xy[xy.size - 1]) else smooth(xy)
        val n = src.size / 2
        val pts = FloatArray(n * 3)
        var k = 0
        for (i in 0 until n) {
            val p = PlaneSnap.project(_plane.value, view, src[2 * i], src[2 * i + 1]) ?: continue
            pts[k++] = p.x; pts[k++] = p.y; pts[k++] = p.z
        }
        if (k < 6) return null
        val world = pts.copyOf(k)
        val count = k / 3
        val b = _brush.value
        val press = pressures?.takeIf { !straight && it.size * 2 == xy.size }?.let { resample(it, count) }
        val widths = widthsFor(b, count, press)
        val opacity = when (b) { Brush3D.MARKER -> min(_opacity.value, 0.6f); else -> _opacity.value }
        val w = _size.value * (if (b == Brush3D.MARKER) 1.8f else 1f) * pixelToWorld()
        val id = UUID.randomUUID().toString()
        val stroke = Stroke3(id, world, _color.value, w, System.currentTimeMillis(), widths, opacity, b.ordinal, _plane.value.id, _activeLayer.value)
        commit(m.copy(strokes = m.strokes + stroke))
        return id
    }

    /** Hafif yumuşatma (3 noktalı ortalama, uçlar korunur). */
    private fun smooth(xy: FloatArray): FloatArray {
        val n = xy.size / 2
        if (n < 3) return xy
        val out = xy.copyOf()
        for (i in 1 until n - 1) for (c in 0..1) out[2 * i + c] = (xy[2 * (i - 1) + c] + 2f * xy[2 * i + c] + xy[2 * (i + 1) + c]) / 4f
        return out
    }

    private fun resample(a: FloatArray, n: Int): FloatArray =
        if (a.size == n) a else FloatArray(n) { i -> a[((i.toFloat() / max(1, n - 1)) * (a.size - 1)).toInt().coerceIn(0, a.size - 1)] }

    /** Fırçanın nokta başına kalınlık çarpanı: basınç (varsa) ve uç incelmesi. */
    private fun widthsFor(b: Brush3D, n: Int, press: FloatArray?): FloatArray? {
        if (b == Brush3D.MARKER || b == Brush3D.WIDE) return null
        val taper = if (b == Brush3D.INK) 0.22f else 0.08f
        if (press == null && b == Brush3D.PEN) return null
        return FloatArray(n) { i ->
            val t = if (n <= 1) 0.5f else i / (n - 1f)
            val edge = min(t, 1f - t)
            val tp = if (edge < taper) 0.35f + 0.65f * (edge / taper) else 1f
            val p = press?.get(i)?.coerceIn(0f, 1f)?.let { if (b == Brush3D.INK) 0.2f + 0.8f * it.pow(1.4f) else 0.3f + 0.7f * it } ?: 1f
            (tp * p).coerceIn(0.08f, 1.6f)
        }
    }

    private var erasing = false

    fun beginErase() { if (!erasing) { erasing = true; beginLive() } }

    /**
     * Silgi: ekranda ([x], [y]) çevresindeki [radiusPx] içinde kalan çizgi noktalarını siler; çizgi değdiği yerden
     * bölünür. Kilitli ve gizli katmanlar silinmez.
     */
    fun eraseAt(x: Float, y: Float, radiusPx: Float) {
        val m = _model.value ?: return
        val view = currentView() ?: return
        val s = FloatArray(4)
        val r2 = radiusPx * radiusPx
        var changed = false
        val out = ArrayList<Stroke3>(m.strokes.size)
        for (st in m.strokes) {
            if (m.layerLocked(st.layer) || m.layerHidden(st.layer)) { out += st; continue }
            val keep = BooleanArray(st.count)
            var any = false
            for (i in 0 until st.count) {
                view.project(st.points[3 * i], st.points[3 * i + 1], st.points[3 * i + 2], s)
                val hit = s[3] > 0f && (s[0] - x) * (s[0] - x) + (s[1] - y) * (s[1] - y) <= r2
                keep[i] = !hit
                if (hit) any = true
            }
            if (!any) { out += st; continue }
            changed = true
            var i = 0
            var piece = 0
            while (i < st.count) {
                if (!keep[i]) { i++; continue }
                var j = i
                while (j < st.count && keep[j]) j++
                if (j - i >= 2) {
                    val pts = st.points.copyOfRange(3 * i, 3 * j)
                    val ws = st.widths?.copyOfRange(i, j)
                    out += st.copy(points = pts, widths = ws, id = if (piece == 0) st.id else "${st.id}-${UUID.randomUUID().toString().take(6)}")
                    piece++
                }
                i = j
            }
        }
        if (changed) commit(m.copy(strokes = out))
    }

    fun endErase() { if (erasing) { erasing = false; endLive() } }

    fun clearDrawings() {
        val m = _model.value ?: return
        commit(m.copy(strokes = m.strokes.filter { m.layerLocked(it.layer) }))
    }

    // ------------------------------------------------------------------------------------------ seçim

    /** Dokunulan çizgi ya da cisim seçilir ([add]: seçime eklenir/çıkarılır). */
    fun tapSelect(x: Float, y: Float, radiusPx: Float, add: Boolean = false) {
        val m = _model.value ?: return
        val view = currentView() ?: return
        val visible = m.visibleLayers()
        val id = hitId(visible, view, x, y, radiusPx)
        val cur = _selection.value
        _selection.value = when {
            id == null -> if (add) cur else emptySet()
            add -> if (id in cur) cur - id else cur + id
            else -> setOf(id)
        }
    }

    private fun hitId(m: Model3D, view: View3, x: Float, y: Float, radiusPx: Float): String? {
        val editable = m.copy(strokes = m.strokes.filterNot { m.layerLocked(it.layer) })
        Pick3.stroke(editable, view, x, y, radiusPx)?.let { return editable.strokes[it].id }
        val hit = Pick3.pick(m, view, x, y) ?: return null
        val part = m.parts[hit.part]
        return part.id.takeUnless { m.layerLocked(part.layer) }
    }

    /** Dokunuş seçili bir öğenin üstünde mi (sürükleme onu dönüştürür). */
    fun onSelection(x: Float, y: Float, radiusPx: Float): Boolean {
        val m = _model.value ?: return false
        val view = currentView() ?: return false
        val id = hitId(m.visibleLayers(), view, x, y, radiusPx) ?: return false
        return id in _selection.value
    }

    fun selectAll() {
        val m = _model.value ?: return
        _selection.value = (m.strokes.filterNot { m.layerLocked(it.layer) || m.layerHidden(it.layer) }.map { it.id } +
            m.parts.filterNot { m.layerLocked(it.layer) || m.layerHidden(it.layer) }.map { it.id }).toSet()
    }

    fun clearSelection() { _selection.value = emptySet() }

    fun deleteSelected() {
        val m = _model.value ?: return
        val sel = _selection.value
        if (sel.isEmpty()) return
        commit(m.copy(strokes = m.strokes.filterNot { it.id in sel }, parts = m.parts.filterNot { it.id in sel }))
        _selection.value = emptySet()
    }

    fun duplicateSelected() {
        val m = _model.value ?: return
        val sel = _selection.value
        if (sel.isEmpty()) return
        val d = _plane.value.u * 0.15f + _plane.value.v * -0.15f
        val newIds = HashSet<String>()
        val strokes = m.strokes.filter { it.id in sel }.map { s ->
            val id = UUID.randomUUID().toString(); newIds += id
            s.copy(points = translate(s.points, d), id = id)
        }
        val parts = m.parts.filter { it.id in sel }.map { p ->
            val id = UUID.randomUUID().toString(); newIds += id
            p.copy(id = id, position = p.position + d)
        }
        commit(m.copy(strokes = m.strokes + strokes, parts = m.parts + parts))
        _selection.value = newIds
    }

    /** Seçimin merkezi (döndürme ve ölçekleme bunun çevresinde). */
    fun selectionCenter(): V3? {
        val m = _model.value ?: return null
        val sel = _selection.value
        var sx = 0f; var sy = 0f; var sz = 0f; var n = 0
        m.strokes.filter { it.id in sel }.forEach { s -> for (i in 0 until s.count) { sx += s.points[3 * i]; sy += s.points[3 * i + 1]; sz += s.points[3 * i + 2]; n++ } }
        m.parts.filter { it.id in sel }.forEach { p -> val c = ModelEdit.worldBounds(p).center; sx += c.x; sy += c.y; sz += c.z; n++ }
        return if (n == 0) null else V3(sx / n, sy / n, sz / n)
    }

    /** Seçimi ekrandaki sürüklemeye göre çizim düzleminde taşır. */
    fun moveSelectedBy(dxPx: Float, dyPx: Float) {
        val view = currentView() ?: return
        val c = selectionCenter() ?: return
        val s = view.project(c)
        val a = PlaneSnap.project(_plane.value.copy(origin = c), view, s[0], s[1]) ?: return
        val b = PlaneSnap.project(_plane.value.copy(origin = c), view, s[0] + dxPx, s[1] + dyPx) ?: return
        moveSelected(b - a)
    }

    fun moveSelected(d: V3) = updateSelected({ translate(it, d) }) { it.copy(position = it.position + d) }

    /** Seçimi [axis] (0 X, 1 Y, 2 Z) dünya ekseni çevresinde döndürür. */
    fun rotateSelected(deg: Float, axis: Int = _axis.value) {
        val c = selectionCenter() ?: return
        val k = when (axis) { 0 -> V3(1f, 0f, 0f); 1 -> V3.UP; else -> V3(0f, 0f, 1f) }
        updateSelected({ pts ->
            val out = pts.copyOf()
            for (i in 0 until pts.size / 3) {
                val p = PlaneSnap.rotateAround(V3(pts[3 * i], pts[3 * i + 1], pts[3 * i + 2]) - c, k, deg) + c
                out[3 * i] = p.x; out[3 * i + 1] = p.y; out[3 * i + 2] = p.z
            }
            out
        }) { p ->
            val pos = PlaneSnap.rotateAround(p.position - c, k, deg) + c
            p.copy(position = pos, rotation = p.rotation.with(axis, p.rotation.get(axis) + deg))
        }
    }

    fun scaleSelected(f: Float) {
        val c = selectionCenter() ?: return
        updateSelected({ pts ->
            val out = pts.copyOf()
            for (i in 0 until pts.size / 3) {
                out[3 * i] = c.x + (pts[3 * i] - c.x) * f; out[3 * i + 1] = c.y + (pts[3 * i + 1] - c.y) * f; out[3 * i + 2] = c.z + (pts[3 * i + 2] - c.z) * f
            }
            out
        }, widthScale = f) { p -> p.copy(position = c + (p.position - c) * f, scale = p.scale * f) }
    }

    /** Çevir: seçim çizim düzleminin u ekseni boyunca aynalanır. */
    fun flipSelected() {
        val c = selectionCenter() ?: return
        val u = _plane.value.u.normalized()
        updateSelected({ pts ->
            val out = pts.copyOf()
            for (i in 0 until pts.size / 3) {
                val p = V3(pts[3 * i], pts[3 * i + 1], pts[3 * i + 2])
                val q = p - u * (2f * ((p - c) dot u))
                out[3 * i] = q.x; out[3 * i + 1] = q.y; out[3 * i + 2] = q.z
            }
            out
        }) { p -> val pos = p.position - u * (2f * ((p.position - c) dot u)); p.copy(position = pos, scale = p.scale.mul(V3(if (abs(u.x) > 0.5f) -1f else 1f, if (abs(u.y) > 0.5f) -1f else 1f, if (abs(u.z) > 0.5f) -1f else 1f))) }
    }

    /** Seçili cisimleri zemine oturtur. */
    fun dropSelectedToGround() = updateSelected({ it }) { ModelEdit.dropToGround(it) }

    private fun updateSelected(strokeOp: (FloatArray) -> FloatArray, widthScale: Float = 1f, partOp: (Part) -> Part) {
        val m = _model.value ?: return
        val sel = _selection.value
        if (sel.isEmpty()) return
        commit(m.copy(
            strokes = m.strokes.map { if (it.id in sel) it.copy(points = strokeOp(it.points), width = it.width * widthScale) else it },
            parts = m.parts.map { if (it.id in sel) partOp(it) else it },
        ))
    }

    private fun translate(pts: FloatArray, d: V3): FloatArray {
        val out = pts.copyOf()
        for (i in 0 until pts.size / 3) { out[3 * i] += d.x; out[3 * i + 1] += d.y; out[3 * i + 2] += d.z }
        return out
    }

    /** Seçimin rengi (çizgi rengi, cisim malzemesi). */
    fun colorSelected(c: Int) {
        val m = _model.value ?: return
        val sel = _selection.value
        if (sel.isEmpty()) return
        commit(m.copy(
            strokes = m.strokes.map { if (it.id in sel) it.copy(color = c) else it },
            parts = m.parts.map { if (it.id in sel) it.copy(material = it.material.copy(color = c, texture = null)) else it },
        ))
    }

    // ------------------------------------------------------------------------------------------ cisimler

    fun addPrimitive(kind: PrimitiveKind) {
        val m = _model.value ?: return
        if (!layerWritable()) return
        val id = UUID.randomUUID().toString()
        val at = _plane.value.origin
        val part = ModelEdit.newPart(kind, id, m.parts.size, V3(at.x, 0f, at.z)).let { p ->
            val placed = ModelEdit.dropToGround(p)
            placed.copy(material = Material(color = 0xFFF5F1E8.toInt(), kind = com.karapp.drawguidance.content.model3d.MaterialKind.MATTE), layer = _activeLayer.value)
        }
        commit(m.copy(parts = m.parts + part))
        _selection.value = setOf(id)
        _mode.value = SketchMode.SELECT
    }

    // ------------------------------------------------------------------------------------------ katmanlar

    fun setActiveLayer(id: String) { _activeLayer.value = id }

    fun addLayer() {
        val m = _model.value ?: return
        val id = UUID.randomUUID().toString().take(8)
        val l = Layer3(id, LAYER_NAME.format(m.layers.size + 1))
        commit(m.copy(layers = m.layers + l))
        _activeLayer.value = id
    }

    fun toggleLayerVisible(id: String) = updateLayer(id) { it.copy(visible = !it.visible) }
    fun toggleLayerLocked(id: String) = updateLayer(id) { it.copy(locked = !it.locked) }
    fun renameLayer(id: String, name: String) = updateLayer(id) { it.copy(name = name.ifBlank { it.name }) }

    private fun updateLayer(id: String, op: (Layer3) -> Layer3) {
        val m = _model.value ?: return
        commit(m.copy(layers = m.layers.map { if (it.id == id) op(it) else it }))
        cleanSelection()
    }

    /** Katmanı içindekilerle siler (son katman silinmez). */
    fun deleteLayer(id: String) {
        val m = _model.value ?: return
        if (m.layers.size <= 1) return
        val first = m.layers.first().id
        fun owns(l: String?) = (l ?: first) == id
        val layers = m.layers.filterNot { it.id == id }
        commit(m.copy(layers = layers, strokes = m.strokes.filterNot { owns(it.layer) }, parts = m.parts.filterNot { owns(it.layer) }))
        if (_activeLayer.value == id) _activeLayer.value = layers.first().id
        cleanSelection()
    }

    /** Katmanı listede yukarı (−1) ya da aşağı (+1) taşır. */
    fun moveLayer(id: String, dir: Int) {
        val m = _model.value ?: return
        val i = m.layers.indexOfFirst { it.id == id }
        val j = i + dir
        if (i < 0 || j !in m.layers.indices) return
        val l = m.layers.toMutableList(); val t = l[i]; l[i] = l[j]; l[j] = t
        commit(m.copy(layers = l))
    }

    /** Seçimi etkin katmana taşır. */
    fun moveSelectionToLayer(id: String) {
        val m = _model.value ?: return
        val sel = _selection.value
        if (sel.isEmpty()) return
        commit(m.copy(strokes = m.strokes.map { if (it.id in sel) it.copy(layer = id) else it }, parts = m.parts.map { if (it.id in sel) it.copy(layer = id) else it }))
    }

    // ------------------------------------------------------------------------------------------ kaydetme ve resim

    fun rename(name: String) {
        val m = _model.value ?: return
        _model.value = m.copy(name = name.ifBlank { m.name })
        _dirty.value = true
    }

    fun save(exitAfter: Boolean = false) {
        val m = _model.value ?: return
        viewModelScope.launch {
            repository.save(m)
            _dirty.value = false
            _events.emit(SketchEvent.Saved(m.name))
            if (exitAfter) _events.emit(SketchEvent.Exit)
        }
    }

    fun saveAs(name: String) {
        val m = _model.value ?: return
        viewModelScope.launch {
            val copy = repository.saveCopy(m, name.ifBlank { m.name })
            _model.value = copy
            _dirty.value = false
            _events.emit(SketchEvent.Saved(copy.name))
        }
    }

    override val imageLocationLabel: String get() = images?.locationLabel ?: "Pictures/DrawGuidance"

    override suspend fun renderImage(e: ImageExport, previewSide: Int?): android.graphics.Bitmap? {
        val m0 = _model.value ?: return null
        val m = m0.visibleLayers()
        val long = (previewSide ?: e.size).coerceIn(64, Budget3D.exportMaxSide)
        val w = if (e.aspect >= 1f) long else (long * e.aspect).toInt().coerceAtLeast(16)
        val h = if (e.aspect >= 1f) (long / e.aspect).toInt().coerceAtLeast(16) else long
        textures.preload(m.parts.flatMap { p -> listOfNotNull(p.material.texture) + p.faceMaterials.values.mapNotNull { it.texture } }.toSet())
        return withContext(Dispatchers.Default) {
            val maxSs = sqrt(Budget3D.exportPixels.toDouble() / (w.toLong() * h)).toInt().coerceIn(1, 3)
            val ss = e.smoothness.coerceIn(1, 3).coerceAtMost(maxSs)
            val bg = if (e.jpeg && e.background == 0) -1 else e.background
            val mm = m.copy(scene = m.scene.copy(edges = e.edges, shadow = e.shadow, ground = e.ground, background = bg))
            val view = if (e.fit) View3.fit(mm, m.camera, w, h, 0.06f) else run {
                val ref = currentView()
                if (ref == null) View3.editor(m.camera, w, h) else {
                    val k = minOf(w / ref.width.toFloat(), h / ref.height.toFloat())
                    View3(m.camera, w, h, ref.scale * k, w / 2f, h / 2f)
                }
            }
            val f = Renderer.render(mm, view, RenderOptions(textures = textures::cached, supersample = ss, ground = e.ground, shadow = e.shadow, background = bg))
            android.graphics.Bitmap.createBitmap(f.color, w, h, android.graphics.Bitmap.Config.ARGB_8888)
        }
    }

    override suspend fun saveImage(e: ImageExport, target: com.karapp.drawguidance.data.images.SaveTarget): String? {
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

    override fun message(text: String) { _events.tryEmit(SketchEvent.Message(text)) }

    /** Ekran noktaları arası uzaklık (testler). */
    internal fun screenDistance(a: V3, b: V3): Float {
        val v = currentView() ?: return 0f
        val p = v.project(a); val q = v.project(b)
        return hypot(p[0] - q[0], p[1] - q[1])
    }

    companion object {
        const val DEFAULT_NAME = "Yeni 3B çizim"
        const val DEFAULT_LAYER = "L1"
        private const val MAX_HISTORY = 60
        // Metinler arayüzde çevrilir (ui.i18n); burada Türkçe kaynak metin
        const val LAYER_NAME = "Katman %d"
        const val MSG_SNAP_POINT = "Düzlem noktaya taşındı"
        const val MSG_SNAP_FACE = "Düzlem yüze yapıştı"
        const val MSG_SNAP_NONE = "Yüze, çizgi ucuna ya da kesişime dokunun"
        const val MSG_LAYER_LOCKED = "Katman kilitli"
        const val MSG_LAYER_HIDDEN = "Katman gizli"
        const val GPU_FALLBACK = "Ekran kartı çizimi açılamadı; yazılımla çiziliyor"
    }
}
