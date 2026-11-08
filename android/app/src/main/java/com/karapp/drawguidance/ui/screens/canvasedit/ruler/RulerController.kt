package com.karapp.drawguidance.ui.screens.canvasedit.ruler

import android.content.Context
import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementIndex
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** v40 cetvel/kalıp oturumu: kapalı, cetvel, kalıp için şekil seçimi, kalıp. */
enum class GuideSession { NONE, RULER, TEMPLATE_PICK, TEMPLATE }

/** v40 hatırlanan cetvel ayarları (son tür, birim, ölçek 1:N). */
data class RulerSettings(
    val type: RulerType = RulerType.STRAIGHT,
    val unit: RulerUnit = RulerUnit.CM,
    val scale: Int = 1,
)

/** v40 cetvel ayarlarının saklandığı yer (uygulamada SharedPreferences; testte bellekte). */
interface RulerSettingsStore {
    fun load(): RulerSettings
    fun save(settings: RulerSettings)
}

/** v40 cetvel ayarları cihaz tercihlerinde (tuval verisine ve veritabanına yazılmaz). */
class RulerPrefs(context: Context) : RulerSettingsStore {
    private val prefs = context.applicationContext.getSharedPreferences("ruler_prefs", Context.MODE_PRIVATE)

    override fun load(): RulerSettings = RulerSettings(
        type = RulerType.parse(prefs.getString(KEY_TYPE, null)),
        unit = RulerUnit.parse(prefs.getString(KEY_UNIT, null)),
        scale = prefs.getInt(KEY_SCALE, 1).takeIf { it in RulerUnits.Scales } ?: 1,
    )

    override fun save(settings: RulerSettings) {
        prefs.edit().putString(KEY_TYPE, settings.type.name).putString(KEY_UNIT, settings.unit.name).putInt(KEY_SCALE, settings.scale).apply()
    }

    private companion object {
        const val KEY_TYPE = "type"
        const val KEY_UNIT = "unit"
        const val KEY_SCALE = "scale"
    }
}

/**
 * v40 cetvel ve şekil kalıbı (resim, not alma, çizgi roman, basit türler ve çocuklar; 3B düzenleyicide yok).
 * Cetvel/kalıp tuvalin üstünde yarı saydam bir katmandır: belgeye girmez, geri alma geçmişine girmez, dışa
 * aktarılmaz. Çizim aracı kenara yakın başlayan çizgileri [guide] ile kenara yapıştırır; çizgiler son seçili fırçayla
 * çizilen olağan (geri alınabilir) çizimlerdir.
 *
 * [elements] tuvalin eleman dizini (kalıp için dokunulan şekli bulmak).
 */
class RulerController(private val elements: () -> ElementIndex?) {
    private val _session = MutableStateFlow(GuideSession.NONE)
    val session: StateFlow<GuideSession> = _session.asStateFlow()

    private val _settings = MutableStateFlow(RulerSettings())
    val settings: StateFlow<RulerSettings> = _settings.asStateFlow()

    private val _pose = MutableStateFlow<RulerPose?>(null)
    val pose: StateFlow<RulerPose?> = _pose.asStateFlow()

    private val _template = MutableStateFlow<TemplateGhost?>(null)
    val template: StateFlow<TemplateGhost?> = _template.asStateFlow()

    private val _locked = MutableStateFlow(false)
    /** Kalıp kilitli: tutamaklar gizli, kalıp kıpırdamaz. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** Kalıp için kütüphane penceresi açık mı. */
    val libraryOpen = MutableStateFlow(false)

    private var store: RulerSettingsStore? = null

    /** Ayarların saklandığı yer bağlanır ve son ayarlar okunur (bir kez). */
    fun attach(settingsStore: RulerSettingsStore) {
        if (store != null) return
        store = settingsStore
        _settings.value = runCatching { settingsStore.load() }.getOrDefault(RulerSettings())
    }

    val active: Boolean get() = _session.value != GuideSession.NONE

    private var shapeCache: RulerShape? = null
    private var guideCache: Pair<Any, StrokeGuide>? = null

    /** Gösterilen cetvelin geometrisi (cetvel açık değilse null). */
    fun shape(): RulerShape? {
        val p = _pose.value ?: return null
        if (_session.value != GuideSession.RULER) return null
        shapeCache?.takeIf { it.pose == p }?.let { return it }
        return RulerShape(p).also { shapeCache = it }
    }

    /**
     * v43 çizgi şekil kalıbının üzerinde mi (kalıp açıkken, noktalarının en az %60'ı kalıbın kutusunda; kutu kalıbın
     * boyunun %8'i kadar genişletilir). Kalıbın üzerinde çizilenlere yapay zekâ önerisi yapılmaz.
     */
    fun onTemplate(xs: FloatArray, ys: FloatArray): Boolean {
        if (_session.value != GuideSession.TEMPLATE) return false
        val t = _template.value ?: return false
        if (xs.isEmpty()) return false
        val b = t.bounds
        val m = maxOf(b.width, b.height) * 0.08f + 4f
        var inside = 0
        for (i in xs.indices) if (xs[i] >= b.left - m && xs[i] <= b.right + m && ys[i] >= b.top - m && ys[i] <= b.bottom + m) inside++
        return inside >= xs.size * 0.6f
    }

    /** Çizim aracının yapışma kaynağı (cetvel ya da kalıp açıkken; kalıp seçilirken yok). */
    fun guide(): StrokeGuide? {
        val key: Any = when (_session.value) {
            GuideSession.RULER -> _pose.value ?: return null
            GuideSession.TEMPLATE -> _template.value ?: return null
            else -> return null
        }
        guideCache?.takeIf { it.first === key || it.first == key }?.let { return it.second }
        val g = when (key) {
            is RulerPose -> shape()?.let { EdgesGuide(it.edges, captureAll = key.type == RulerType.COMPASS) } ?: return null
            is TemplateGhost -> EdgesGuide(key.edges)
            else -> return null
        }
        guideCache = key to g
        return g
    }

    /**
     * Cetveli açar: ilk açılışta görünen alanın ortasında, görünen genişliğin %60'ı boyunda; [thickness] düz cetvelin
     * eni (tuval birimi; ekranda sabit genişlik için ölçeğe bölünmüş).
     */
    fun openRuler(center: Offset, visibleWidth: Float, thickness: Float) {
        _template.value = null
        val type = _settings.value.type
        val current = _pose.value
        _pose.value = current?.copy(type = type)?.takeIf { _session.value == GuideSession.RULER }
            ?: RulerPose(type, center.x, center.y, 0f, (visibleWidth * 0.6f).coerceIn(RulerPose.MIN_LENGTH, RulerPose.MAX_LENGTH), thickness)
        _session.value = GuideSession.RULER
    }

    /** Şekil kalıbını açar: önce tuvaldeki bir şekle dokunulur ya da kütüphaneden şekil seçilir. */
    fun openTemplate() {
        _template.value = null
        _locked.value = false
        _session.value = GuideSession.TEMPLATE_PICK
    }

    /** Bitir: normal çizime dönülür. */
    fun finish() {
        _session.value = GuideSession.NONE
        _template.value = null
        shapeCache = null
        guideCache = null
    }

    fun setType(type: RulerType) {
        updateSettings(_settings.value.copy(type = type))
        _pose.value = _pose.value?.copy(type = type)
    }

    fun setUnit(unit: RulerUnit) = updateSettings(_settings.value.copy(unit = unit))
    fun setScale(scale: Int) = updateSettings(_settings.value.copy(scale = scale.coerceAtLeast(1)))

    private fun updateSettings(s: RulerSettings) {
        _settings.value = s
        store?.save(s)
    }

    /** Cetvelin yeni duruşu (sürükleme, döndürme, boyutlandırma). */
    fun setPose(pose: RulerPose) {
        if (_session.value == GuideSession.RULER) _pose.value = pose
    }

    fun setTemplate(ghost: TemplateGhost) {
        _template.value = ghost
        _session.value = GuideSession.TEMPLATE
    }

    fun toggleLock() { _locked.value = !_locked.value }

    /** Kalıp seçilirken dokunulan elemanın (grubuyla) hayalet kopyası; eleman yoksa false. */
    fun pickAt(point: Offset, tolerance: Float): Boolean {
        if (_session.value != GuideSession.TEMPLATE_PICK) return false
        val index = elements() ?: return false
        val element = ElementOps.hitTest(index, point, tolerance) ?: return false
        val ids = element.strokeIds.toSet()
        val strokes = index.strokes.filter { it.id in ids && !it.hidden }
        val ghost = TemplateGhost.of(TemplateGhost.contoursOf(strokes), strokes.firstOrNull()?.let { colorOf(it) } ?: "#3B82F6") ?: return false
        setTemplate(ghost)
        return true
    }

    /** Kütüphaneden seçilen şekil [center] çevresinde [size] boyunda kalıp olur (belgeye eklenmez). */
    fun placeLibrary(shapeId: String, center: Offset, size: Float): Boolean {
        val half = size / 2f
        val registry = com.karapp.drawguidance.ui.drawing.ShapeRegistry
        val aspect = runCatching { registry.aspect(shapeId) }.getOrDefault(1f).takeIf { it > 0f } ?: 1f
        val hw = if (aspect >= 1f) half else half * aspect
        val hh = if (aspect >= 1f) half / aspect else half
        val stroke: Stroke = com.karapp.drawguidance.ui.drawing.ShapeFactory.build(
            kind = shapeId, start = center - Offset(hw, hh), end = center + Offset(hw, hh),
            style = ShapeStyle(), id = "ruler-template", order = 0,
        ) ?: return false
        val ghost = TemplateGhost.of(TemplateGhost.contoursOf(listOf(stroke))) ?: return false
        setTemplate(ghost)
        return true
    }

    private fun colorOf(s: Stroke): String = s.shape?.style?.strokeColorHex ?: s.colorHex
}
