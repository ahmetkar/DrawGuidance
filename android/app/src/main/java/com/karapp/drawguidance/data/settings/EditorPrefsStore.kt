package com.karapp.drawguidance.data.settings

import android.content.Context
import com.karapp.drawguidance.data.model.TextSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Çizim ekranının kalıcı tercihleri: otomatik kayıt, çizim düzeltme önerileri (v20: kapatılabilir), son seçilen renk/fırça/yazı ayarları, Ekle
 * menüsünde son seçilen öğe ve şekil dolgusu. Değerler ilk erişimde bir kez okunur (açılışta arka planda ısıtılır),
 * yazma `apply()` ile arka planda yapılır.
 */
class EditorPrefsStore(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences("editor_prefs", Context.MODE_PRIVATE) }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val _autosave by lazy { MutableStateFlow(prefs.getBoolean(KEY_AUTOSAVE, true)) }
    /** Tuvaldeki değişiklikler otomatik kaydedilsin mi (kapalıysa üst menüde Kaydet düğmesi). */
    val autosave: StateFlow<Boolean> get() = _autosave.asStateFlow()

    private val _handwriting by lazy { MutableStateFlow(prefs.getBoolean(KEY_HANDWRITING, true)) }
    /** Çizim düzeltme önerileri (şekil ve el yazısı); v20: kullanıcı kapatabilir. */
    val handwriting: StateFlow<Boolean> get() = _handwriting.asStateFlow()

    /** v23 yazı önerisi ve şekil önerisi ayrı ayrı açılıp kapatılır (ana ayar [handwriting] ikisini birlikte kapatır). */
    private val _textSuggest by lazy { MutableStateFlow(prefs.getBoolean(KEY_TEXT_SUGGEST, true)) }
    val textSuggest: StateFlow<Boolean> get() = _textSuggest.asStateFlow()
    private val _shapeSuggest by lazy { MutableStateFlow(prefs.getBoolean(KEY_SHAPE_SUGGEST, true)) }
    val shapeSuggest: StateFlow<Boolean> get() = _shapeSuggest.asStateFlow()
    /** v27 seçimde kendiliğinden öneri (çok elemanda şekil, az elemanda parça; tüm modlarda). */
    private val _selectionSuggest by lazy { MutableStateFlow(prefs.getBoolean(KEY_SELECTION_SUGGEST, true)) }
    val selectionSuggest: StateFlow<Boolean> get() = _selectionSuggest.asStateFlow()
    fun setSelectionSuggest(value: Boolean) { _selectionSuggest.value = value; prefs.edit().putBoolean(KEY_SELECTION_SUGGEST, value).apply() }
    fun setTextSuggest(value: Boolean) { _textSuggest.value = value; prefs.edit().putBoolean(KEY_TEXT_SUGGEST, value).apply() }
    fun setShapeSuggest(value: Boolean) { _shapeSuggest.value = value; prefs.edit().putBoolean(KEY_SHAPE_SUGGEST, value).apply() }
    /** v38 çizgi önerisi (yazı ve şekil önerisinden ayrı açılıp kapatılır). */
    private val _lineSuggest by lazy { MutableStateFlow(prefs.getBoolean(KEY_LINE_SUGGEST, true)) }
    val lineSuggest: StateFlow<Boolean> get() = _lineSuggest.asStateFlow()
    fun setLineSuggest(value: Boolean) { _lineSuggest.value = value; prefs.edit().putBoolean(KEY_LINE_SUGGEST, value).apply() }

    fun setAutosave(value: Boolean) { _autosave.value = value; prefs.edit().putBoolean(KEY_AUTOSAVE, value).apply() }
    fun setHandwriting(value: Boolean) { _handwriting.value = value; prefs.edit().putBoolean(KEY_HANDWRITING, value).apply() }

    /**
     * v28 öneri balonlarının kullanıcının sürüklediği yeri (çizim alanına göre oran, 0..1); null: kendiliğinden
     * (önerilen çizimin üstünde).
     */
    var suggestPin: Pair<Float, Float>?
        get() {
            val x = prefs.getFloat(KEY_SUGGEST_PIN_X, -1f); val y = prefs.getFloat(KEY_SUGGEST_PIN_Y, -1f)
            return if (x < 0f || y < 0f) null else x to y
        }
        set(value) { prefs.edit().putFloat(KEY_SUGGEST_PIN_X, value?.first ?: -1f).putFloat(KEY_SUGGEST_PIN_Y, value?.second ?: -1f).apply() }

    /**
     * v29 tuvale özgü son ayarlar: renk, fırça, yazı biçimi, şekil dolgusu, fırça başına ayar/boyut ve Ekle menüsündeki
     * son öğe her tuvalin kendisinde saklanır. Bir tuvalde seçilen ayar başka tuvale geçmez; yeni tuval öntanımlı
     * ayarlarla açılır.
     */
    fun canvas(canvasId: String): CanvasPrefs = CanvasPrefs(canvasPrefs, canvasId, json)

    /** v29 silinen tuvalin ayarları da silinir. */
    fun clearCanvas(canvasId: String) {
        val prefix = "$canvasId/"
        val keys = canvasPrefs.all.keys.filter { it.startsWith(prefix) }
        if (keys.isEmpty()) return
        val e = canvasPrefs.edit()
        keys.forEach { e.remove(it) }
        e.apply()
    }

    private val canvasPrefs by lazy { context.getSharedPreferences("canvas_editor_prefs", Context.MODE_PRIVATE) }

    /** Açılışta arka planda okunur (ana iş parçacığında disk okuması olmasın). */
    fun warmUp() {
        prefs.all
        canvasPrefs.all
        _autosave.value
        _handwriting.value
        _textSuggest.value
        _shapeSuggest.value
        _lineSuggest.value
        _selectionSuggest.value
    }

    private companion object {
        const val KEY_AUTOSAVE = "autosave"
        const val KEY_HANDWRITING = "handwriting_suggest"
        const val KEY_TEXT_SUGGEST = "text_suggest"
        const val KEY_SHAPE_SUGGEST = "shape_suggest"
        const val KEY_SELECTION_SUGGEST = "selection_suggest"
        const val KEY_LINE_SUGGEST = "line_suggest"
        const val KEY_SUGGEST_PIN_X = "suggest_pin_x"
        const val KEY_SUGGEST_PIN_Y = "suggest_pin_y"
    }
}

/** v29 bir tuvalin kendi son ayarları ([EditorPrefsStore.canvas]); anahtarlar "tuvalKimliği/ad" biçiminde. */
class CanvasPrefs internal constructor(
    private val prefs: android.content.SharedPreferences,
    private val canvasId: String,
    private val json: Json,
) {
    private fun key(name: String) = "$canvasId/$name"

    /** Son seçilen kalem rengi. */
    var lastColor: String?
        get() = prefs.getString(key(KEY_COLOR), null)
        set(value) { prefs.edit().putString(key(KEY_COLOR), value).apply() }

    /** Son seçilen fırça (BrushType adı). */
    var lastBrush: String?
        get() = prefs.getString(key(KEY_BRUSH), null)
        set(value) { prefs.edit().putString(key(KEY_BRUSH), value).apply() }

    /** Kullanıcının son seçtiği yazı biçimi (yazı tipi, kalın/eğik, hizalama, kutu, efektler) ve boyutu. */
    var lastText: Pair<TextSpec, Float>?
        get() {
            val spec = prefs.getString(key(KEY_TEXT), null) ?: return null
            val size = prefs.getFloat(key(KEY_TEXT_SIZE), 0f)
            return runCatching { json.decodeFromString(TextSpec.serializer(), spec) to size }.getOrNull()
        }
        set(value) {
            if (value == null) return
            prefs.edit()
                .putString(key(KEY_TEXT), json.encodeToString(TextSpec.serializer(), value.first.copy(content = "")))
                .putFloat(key(KEY_TEXT_SIZE), value.second)
                .apply()
        }

    /** Ekle (+) menüsünde son seçilen öğe. */
    var lastAddItem: String?
        get() = prefs.getString(key(KEY_ADD), null)
        set(value) { prefs.edit().putString(key(KEY_ADD), value).apply() }

    /** Şekiller içi dolu eklensin mi ve dolgu rengi ("" = dolgusuz, null = hiç seçilmedi). */
    var shapeFill: String?
        get() = prefs.getString(key(KEY_SHAPE_FILL), null)
        set(value) { prefs.edit().putString(key(KEY_SHAPE_FILL), value ?: "").apply() }

    /** Fırça başına ayarlar (BrushType adı → ayar). */
    var brushTunings: Map<String, BrushTuning>
        get() {
            val raw = prefs.getString(key(KEY_TUNINGS), null) ?: return emptyMap()
            return runCatching {
                json.decodeFromString(MapSerializer(String.serializer(), BrushTuning.serializer()), raw)
            }.getOrDefault(emptyMap())
        }
        set(value) {
            val raw = json.encodeToString(MapSerializer(String.serializer(), BrushTuning.serializer()), value)
            prefs.edit().putString(key(KEY_TUNINGS), raw).apply()
        }

    /** Fırça başına boyutlar (BrushType adı → px). */
    var brushSizes: Map<String, Float>
        get() {
            val raw = prefs.getString(key(KEY_SIZES), null) ?: return emptyMap()
            return runCatching {
                json.decodeFromString(MapSerializer(String.serializer(), Float.serializer()), raw)
            }.getOrDefault(emptyMap())
        }
        set(value) {
            val raw = json.encodeToString(MapSerializer(String.serializer(), Float.serializer()), value)
            prefs.edit().putString(key(KEY_SIZES), raw).apply()
        }

    private companion object {
        const val KEY_COLOR = "last_color"
        const val KEY_BRUSH = "last_brush"
        const val KEY_TEXT = "last_text"
        const val KEY_TEXT_SIZE = "last_text_size"
        const val KEY_ADD = "last_add"
        const val KEY_SHAPE_FILL = "shape_fill"
        const val KEY_TUNINGS = "brush_tunings"
        const val KEY_SIZES = "brush_sizes"
    }
}
