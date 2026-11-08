package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.settings.BrushTuning
import com.karapp.drawguidance.data.settings.ColorPaletteStore
import com.karapp.drawguidance.ui.drawing.defaultSize
import com.karapp.drawguidance.ui.drawing.pickerBrushTypes
import com.karapp.drawguidance.ui.screens.canvasedit.BrushSettings
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fırça: seçili fırça, boyutu/rengi/opaklığı/akışı/gücü, fırça başına hatırlanan boyut ve kayıtlı renkler.
 * Kalem ya da silgi seçilince tüm seçimler kalkar ve Çiz moduna dönülür; ekleme/düzenleme modlarında pasiftir.
 */
class BrushController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val paletteStore: ColorPaletteStore,
    /** Fırça kalınlığı değişince (yeni şekillerin kenar kalınlığı bunu izler). */
    private val onDrawingWidth: (Float) -> Unit,
    /** v20 fırça başına ayarların ve boyutların kalıcı kaydı (testlerde null); v29 tuvale özgü. */
    private val prefs: com.karapp.drawguidance.data.settings.CanvasPrefs? = null,
) {
    private val _settings = MutableStateFlow(BrushSettings())
    val settings: StateFlow<BrushSettings> = _settings.asStateFlow()

    /** Silgiden dönülecek fırça (üst menüdeki Fırça Seç düğmesi bunu gösterir). */
    private val _drawingBrush = MutableStateFlow(BrushType.PENCIL)
    val drawingBrush: StateFlow<BrushType> = _drawingBrush.asStateFlow()

    // Her fırçanın kendi boyutu: varsayılanla başlar, kullanıcı değiştirirse o fırça için hatırlanır (v20: kalıcı)
    private val sizes = BrushType.entries.associateWith { it.defaultSize() }.toMutableMap()

    /** v20 fırça başına ayarlar (aralık, çizim hızı, sabitleme, eğim, 3B); uygulama kapansa da kalır. */
    private val tunings = HashMap<BrushType, BrushTuning>()
    private var loaded = false

    private fun load() {
        if (loaded) return
        loaded = true
        val p = prefs ?: return
        runCatching {
            p.brushTunings.forEach { (k, v) -> BrushType.entries.firstOrNull { it.name == k }?.let { tunings[it] = v } }
            p.brushSizes.forEach { (k, v) -> BrushType.entries.firstOrNull { it.name == k }?.let { sizes[it] = v.coerceIn(BrushTuning.MIN_SIZE, BrushTuning.MAX_SIZE) } }
        }
        val s = _settings.value
        _settings.value = s.copy(sizePx = sizes.getValue(s.brushType), tuning = tuningOf(s.brushType))
    }

    private fun tuningOf(type: BrushType): BrushTuning = tunings[type] ?: BrushTuning()

    private fun persist() {
        val p = prefs ?: return
        p.brushTunings = tunings.mapKeys { it.key.name }
        p.brushSizes = sizes.filter { (t, v) -> v != t.defaultSize() }.mapKeys { it.key.name }
    }

    private val _isPickerOpen = MutableStateFlow(false)
    val isPickerOpen: StateFlow<Boolean> = _isPickerOpen.asStateFlow()

    val savedColors: StateFlow<List<String>> = paletteStore.colors

    /** Tuval açılınca: çalışma/not tuvalinde yalnızca not kalemleri kullanılır. */
    fun adaptTo(kind: CanvasKind) {
        load()
        val allowed = pickerBrushTypes(kind)
        if (_settings.value.brushType !in allowed) {
            val type = com.karapp.drawguidance.ui.drawing.defaultBrush(kind)
            _settings.value = _settings.value.copy(brushType = type, sizePx = sizes.getValue(type), tuning = tuningOf(type))
            _drawingBrush.value = type
        }
    }

    /**
     * Son seçilen fırça ve renk (tercihlerden): tuval açılınca geri yüklenir. Fırça tuval türüne uygun değilse
     * yalnızca renk alınır.
     */
    fun restore(kind: CanvasKind, type: BrushType?, colorHex: String?) {
        load()
        var s = _settings.value
        if (type != null && type != BrushType.ERASER && type in pickerBrushTypes(kind)) {
            s = s.copy(brushType = type, sizePx = sizes.getValue(type), tuning = tuningOf(type))
            _drawingBrush.value = type
            onDrawingWidth(sizes.getValue(type))
        }
        if (colorHex != null && colorHex.matches(Regex("#[0-9A-Fa-f]{6}"))) s = s.copy(colorHex = colorHex.uppercase())
        _settings.value = s
    }

    fun openPicker() {
        if (host.mode.value.allowsDrawTools) _isPickerOpen.value = true
    }

    /**
     * v22 üst menüdeki Fırça düğmesi: fırça dışında bir şey (silgi) seçiliyken son seçilen fırçaya döner (fırça
     * hatırlanır ve seçili olur; Çiz moduna geçilir); fırça zaten seçiliyse fırça seçici açılır.
     */
    fun brushButton() {
        if (!host.mode.value.allowsDrawTools) return
        if (_settings.value.brushType == BrushType.ERASER || host.mode.value != EditMode.DRAW) select(_drawingBrush.value)
        else openPicker()
    }

    /** v22 fırça seçicide seçili gösterilen fırça: silgideyken son kullanılan fırça. */
    fun pickerSelection(): BrushType = _settings.value.brushType.takeIf { it != BrushType.ERASER } ?: _drawingBrush.value

    fun closePicker() { _isPickerOpen.value = false }

    fun select(type: BrushType) {
        if (type == BrushType.IMAGE) return
        // Kalem ya da silgi seçilince tüm seçimler kalkar
        store.select(emptySet())
        load()
        val current = _settings.value
        sizes[current.brushType] = current.sizePx
        _settings.value = current.copy(brushType = type, sizePx = sizes.getValue(type), tuning = tuningOf(type))
        // Yeni şekillerin kenar kalınlığı fırça kalınlığını izler (eleman ayarlarından ayrıca değiştirilebilir)
        if (type != BrushType.ERASER) {
            _drawingBrush.value = type
            onDrawingWidth(sizes.getValue(type))
        }
        _isPickerOpen.value = false
        host.setMode(EditMode.DRAW)
    }

    /** v18: kalemin silgi ucunda (ya da yan tuşla) kullanılan silgi boyu: son kullanılan silgi boyu. */
    fun eraserSize(): Float = sizes[BrushType.ERASER] ?: BrushType.ERASER.defaultSize()

    /** Üst menüdeki Silgi düğmesi: silgiye geçer; silgideyken son kullanılan fırçaya döner. */
    fun toggleEraser() {
        if (!host.mode.value.allowsDrawTools) return
        select(if (_settings.value.brushType == BrushType.ERASER) _drawingBrush.value else BrushType.ERASER)
    }

    fun updateSize(sizePx: Float) {
        val size = sizePx.coerceIn(BrushTuning.MIN_SIZE, BrushTuning.MAX_SIZE)
        _settings.value = _settings.value.copy(sizePx = size)
        sizes[_settings.value.brushType] = size
        if (_settings.value.brushType != BrushType.ERASER) onDrawingWidth(size)
        persist()
    }

    /** v20 seçili fırçanın ayarlarını değiştirir (o fırça için hatırlanır). */
    fun updateTuning(change: (BrushTuning) -> BrushTuning) {
        load()
        val s = _settings.value
        val next = change(s.tuning).let {
            it.copy(
                speed = it.speed.coerceIn(0.1f, 1f),
                stabilizerStrength = it.stabilizerStrength.coerceIn(0.05f, 1f),
                spacing = it.spacing?.coerceIn(0.02f, 3f),
                tiltWidth = it.tiltWidth?.coerceIn(0f, 2f),
                tiltLight = it.tiltLight?.coerceIn(0f, 1f),
                tiltThreshold = it.tiltThreshold.coerceIn(0f, 60f),
            )
        }
        tunings[s.brushType] = next
        _settings.value = s.copy(tuning = next)
        persist()
    }

    /** v20 seçili fırçanın ayarlarını varsayılana döndürür. */
    fun resetTuning() = updateTuning { BrushTuning() }

    fun updateColor(colorHex: String) { _settings.value = _settings.value.copy(colorHex = colorHex.uppercase()) }
    fun updateOpacity(opacity: Float) { _settings.value = _settings.value.copy(opacity = opacity) }
    fun updateFlow(flow: Float) { _settings.value = _settings.value.copy(flow = flow.coerceIn(0.05f, 1f)) }
    fun updateStrength(strength: Float) { _settings.value = _settings.value.copy(strength = strength.coerceIn(0.1f, 1f)) }

    fun saveCurrentColor() = paletteStore.add(_settings.value.colorHex)
    fun saveColor(colorHex: String) = paletteStore.add(colorHex)
    fun removeSavedColor(colorHex: String) = paletteStore.remove(colorHex)

    /** Yeni şekil, diyagram ve yazıların rengi: silgi seçiliyse koyu gri. */
    fun inkColor(): String {
        val brush = _settings.value
        return if (brush.brushType == BrushType.ERASER) "#1E1E1E" else brush.colorHex
    }
}
