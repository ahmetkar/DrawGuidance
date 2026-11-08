package com.karapp.drawguidance.platform

import android.content.Context
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.PI
import kotlin.math.hypot

/**
 * v18 tablet kalemi: kalemin bağlı olup olmadığı, kullanılıp kullanılmadığı ve kalem ayarları.
 *
 * Algılama iki yoldan yapılır:
 * - Cihazın giriş aygıtları: ekran kalem destekliyse (S Pen, USI) ya da Bluetooth kalem bağlıysa [State.attached].
 *   Bluetooth kalemler bağlanıp koptukça güncellenir. Kalem destekli ekranlar kalem kutusundayken de "bağlı" görünür.
 * - İlk kalem dokunuşu: dokunuş türü kalem ya da silgi ucuyla gelince [State.used] olur (kesin bilgi). Pilsiz
 *   (kapasitif) kalemler parmakla aynı görünür; onlar için parmakla çizim açık kalır.
 */
object Stylus {

    data class State(
        /** Kalem destekli giriş aygıtı var (ekran ya da Bluetooth kalem). */
        val attached: Boolean = false,
        /** Aygıtın adı (gösterim için). */
        val name: String? = null,
        /** Bluetooth kalem. */
        val bluetooth: Boolean = false,
        /** Bu oturumda kalemle dokunuldu. */
        val used: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** v21 kalem tanıma: otomatik (algılanınca), her zaman açık ya da kapalı (kalem özellikleri kullanılmaz). */
    enum class Recognition { AUTO, ALWAYS, OFF }

    val recognition = MutableStateFlow(Recognition.AUTO)

    /** v21 uygulama kalemi tanıyor mu: kalem ayarları (fırça panelindeki eğim, basınç…) buna göre görünür ve çalışır. */
    fun isRecognized(state: State = _state.value, mode: Recognition = recognition.value): Boolean = when (mode) {
        Recognition.AUTO -> state.attached || state.used
        Recognition.ALWAYS -> true
        Recognition.OFF -> false
    }

    /** Kalem özellikleri kullanılabilir mi (tanıma kapalıyken kalem parmak gibi çizer: basınçsız, eğimsiz). */
    val penFeatures: Boolean get() = recognition.value != Recognition.OFF

    fun setRecognition(mode: Recognition) {
        recognition.value = mode
        prefs?.edit()?.putString(KEY_RECOGNITION, mode.name)?.apply()
    }

    private const val KEY_RECOGNITION = "recognition"

    // --- Ayarlar ---

    private var prefs: SharedPreferences? = null

    /** Kalem kullanılınca parmak yalnızca kaydırıp yakınlaştırsın (avuç ve parmak çizmez). */
    val fingerPans = MutableStateFlow(true)
    /** Basınçla kalınlık/koyuluk. */
    val pressure = MutableStateFlow(true)
    /** Eğimle gölgeleme (kurşun kalem, kömür, pastel). */
    val tilt = MutableStateFlow(true)
    /** Kalemin yan tuşu basılıyken silgi. */
    val sideButtonErases = MutableStateFlow(true)
    /** Hareket tahmini: çizgi kalem ucunun gerisinde kalmasın. */
    val prediction = MutableStateFlow(true)
    /** Düşük gecikmeli çizim (Android 10+, ön tampon). */
    val lowLatency = MutableStateFlow(true)

    private val keys = mapOf(
        "finger_pans" to fingerPans, "pressure" to pressure, "tilt" to tilt,
        "side_button" to sideButtonErases, "prediction" to prediction, "low_latency" to lowLatency,
    )

    fun set(flow: MutableStateFlow<Boolean>, value: Boolean) {
        flow.value = value
        val key = keys.entries.firstOrNull { it.value === flow }?.key ?: return
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    /** Düşük gecikmeli çizim bu cihazda kullanılabilir mi (Android 10+). */
    val lowLatencySupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Build.FINGERPRINT.startsWith("robolectric")

    // --- Algılama ---

    private var listening = false

    fun init(context: Context) {
        val app = context.applicationContext
        prefs = runCatching { app.getSharedPreferences("stylus", Context.MODE_PRIVATE) }.getOrNull()?.also { p ->
            keys.forEach { (key, flow) -> flow.value = p.getBoolean(key, flow.value) }
            recognition.value = p.getString(KEY_RECOGNITION, null)?.let { n -> Recognition.entries.firstOrNull { it.name == n } } ?: Recognition.AUTO
        }
        val im = app.getSystemService(Context.INPUT_SERVICE) as? InputManager ?: return
        refresh(im)
        if (!listening) {
            listening = true
            runCatching {
                im.registerInputDeviceListener(object : InputManager.InputDeviceListener {
                    override fun onInputDeviceAdded(deviceId: Int) = refresh(im)
                    override fun onInputDeviceRemoved(deviceId: Int) = refresh(im)
                    override fun onInputDeviceChanged(deviceId: Int) = refresh(im)
                }, Handler(Looper.getMainLooper()))
            }
        }
    }

    private fun refresh(im: InputManager) {
        var found: InputDevice? = null
        var bluetooth = false
        runCatching {
            for (id in im.inputDeviceIds) {
                val device = im.getInputDevice(id) ?: continue
                if (device.isVirtual) continue
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !device.isEnabled) continue
                val bt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && device.supportsSource(InputDevice.SOURCE_BLUETOOTH_STYLUS)
                if (bt || device.supportsSource(InputDevice.SOURCE_STYLUS)) {
                    found = device
                    bluetooth = bt
                    if (bt) break
                }
            }
        }
        _state.update { it.copy(attached = found != null, name = found?.name, bluetooth = bluetooth) }
    }

    /** Kalemle dokunuldu (kesin algılama). */
    fun onStylusInput() {
        if (!_state.value.used) _state.update { it.copy(used = true) }
    }

    /** Test ve önizleme için durum sıfırlama. */
    fun resetForTest() {
        _state.value = State()
        keys.values.forEach { it.value = true }
        recognition.value = Recognition.AUTO
    }

    /** Kalem modu: kalem kullanıldıysa ve ayar açıksa parmak çizmez, kaydırır. */
    val fingerShouldPan: Boolean get() = penFeatures && fingerPans.value && _state.value.used

    // --- Ham olaydan kalem verisi ---

    /** Olayda kalem (ya da silgi ucu) olan işaretçinin sırası; yoksa -1. */
    fun stylusIndex(event: MotionEvent): Int {
        for (i in 0 until event.pointerCount) {
            val t = event.getToolType(i)
            if (t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER) return i
        }
        return -1
    }

    /** Eğim 0..1 (0 = dik, 1 = yatık); eğim bildirmeyen kalemde 0. */
    fun tiltOf(event: MotionEvent, index: Int, history: Int = -1): Float {
        if (index < 0) return 0f
        val v = if (history < 0) event.getAxisValue(MotionEvent.AXIS_TILT, index)
        else event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, index, history)
        if (!v.isFinite()) return 0f
        return (v / (PI / 2).toFloat()).coerceIn(0f, 1f)
    }

    /**
     * v20 kalemin yöneldiği yön (0..1 → 0..360°, ekranda 0 yukarı, saat yönünde); aygıt yön bildirmiyorsa 0
     * ("bilinmiyor"). Gerçek 0° yönü en küçük adımla (1/255) ayırt edilir.
     */
    fun orientOf(event: MotionEvent, index: Int, history: Int = -1): Float {
        if (index < 0) return 0f
        val supported = runCatching { event.device?.getMotionRange(MotionEvent.AXIS_ORIENTATION, event.source) != null }.getOrDefault(false)
        if (!supported) return 0f
        val v = if (history < 0) event.getAxisValue(MotionEvent.AXIS_ORIENTATION, index)
        else event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, index, history)
        if (!v.isFinite()) return 0f
        return orientUnit(v)
    }

    /** Android yönü (radyan, -π..π, 0 = yukarı) → 0..1 (0 kullanılmaz). */
    fun orientUnit(radians: Float): Float {
        val twoPi = (2 * PI).toFloat()
        val unit = (((radians % twoPi) + twoPi) % twoPi) / twoPi
        return unit.coerceIn(1f / 255f, 1f)
    }

    /** Basınç 0..1 (bazı sürücüler 1'in üstünü bildirir). */
    fun pressureOf(event: MotionEvent, index: Int, history: Int = -1): Float {
        if (index < 0) return 1f
        val v = if (history < 0) event.getPressure(index) else event.getHistoricalPressure(index, history)
        return if (v.isFinite()) v.coerceIn(0f, 1f) else 1f
    }

    /** Yan (birincil) tuş basılı mı. Android 6.0 öncesinde kalem tuşları ikincil fare tuşu olarak gelir. */
    fun sideButton(event: MotionEvent): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
        else event.buttonState and MotionEvent.BUTTON_SECONDARY != 0

    /**
     * Olay iptal mi (ör. avuç algılandı): ACTION_CANCEL ya da Android 13+'ta FLAG_CANCELED ile gelen işaretçi kalkışı.
     * İptal edilen çizgi kaydedilmez.
     */
    fun isCanceled(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) return true
        if (Build.VERSION.SDK_INT >= 33) {
            val up = event.actionMasked == MotionEvent.ACTION_POINTER_UP || event.actionMasked == MotionEvent.ACTION_UP
            if (up && event.flags and MotionEvent.FLAG_CANCELED != 0) return true
        }
        return false
    }
}

/**
 * v18 hareket tahmini: kalemin birkaç milisaniye sonraki yeri. Yalnızca ekranda geçici olarak çizilir (kaydedilmez).
 * Android 14+ sistemin tahmin edicisi (cihaz destekliyorsa), diğerlerinde son noktaların hızından doğrusal tahmin.
 */
internal class StrokePredictor(context: Context?) {
    private val system: Any? = if (Build.VERSION.SDK_INT >= 34 && context != null) {
        runCatching { android.view.MotionPredictor(context) }.getOrNull()
    } else null
    private var systemUsable = false

    private val xs = FloatArray(HISTORY)
    private val ys = FloatArray(HISTORY)
    private val ts = LongArray(HISTORY)
    private var n = 0

    /** Yeni ham olay (sistem tahmin edicisi için). */
    fun record(event: MotionEvent?) {
        if (event == null || Build.VERSION.SDK_INT < 34) return
        val p = system as? android.view.MotionPredictor ?: return
        runCatching {
            systemUsable = p.isPredictionAvailable(event.deviceId, event.source)
            if (systemUsable) p.record(event)
        }
    }

    /** Yeni nokta (yerel koordinat) ve zamanı (ms). */
    fun add(x: Float, y: Float, timeMs: Long) {
        if (n == HISTORY) {
            System.arraycopy(xs, 1, xs, 0, HISTORY - 1); System.arraycopy(ys, 1, ys, 0, HISTORY - 1); System.arraycopy(ts, 1, ts, 0, HISTORY - 1)
            n--
        }
        xs[n] = x; ys[n] = y; ts[n] = timeMs
        n++
    }

    fun reset() { n = 0 }

    /**
     * Tahmin edilen yerler (yerel koordinat). [rawToLocalX]/[rawToLocalY]: sistem tahmini ham olay koordinatındadır,
     * yerel koordinata bu farkla çevrilir. [maxDistance] tahminin son noktadan en çok uzaklığı.
     */
    fun predict(rawToLocalX: Float, rawToLocalY: Float, maxDistance: Float): List<Pair<Float, Float>> {
        if (n < 2) return emptyList()
        val lx = xs[n - 1]; val ly = ys[n - 1]
        if (systemUsable && Build.VERSION.SDK_INT >= 34) {
            val p = system as? android.view.MotionPredictor
            val predicted = runCatching { p?.predict(System.nanoTime() + LOOKAHEAD_MS * 1_000_000L) }.getOrNull()
            if (predicted != null) {
                val out = ArrayList<Pair<Float, Float>>()
                val idx = Stylus.stylusIndex(predicted).coerceAtLeast(0)
                for (h in 0 until predicted.historySize) out += clamp(lx, ly, predicted.getHistoricalX(idx, h) + rawToLocalX, predicted.getHistoricalY(idx, h) + rawToLocalY, maxDistance)
                out += clamp(lx, ly, predicted.getX(idx) + rawToLocalX, predicted.getY(idx) + rawToLocalY, maxDistance)
                predicted.recycle()
                return out
            }
        }
        // Son ~30 ms'deki hızla doğrusal tahmin
        var k = n - 2
        while (k > 0 && ts[n - 1] - ts[k] < WINDOW_MS) k--
        val dt = (ts[n - 1] - ts[k]).coerceAtLeast(1L).toFloat()
        val vx = (lx - xs[k]) / dt
        val vy = (ly - ys[k]) / dt
        if (hypot(vx, vy) < 0.02f) return emptyList()
        return listOf(
            clamp(lx, ly, lx + vx * LOOKAHEAD_MS * 0.5f, ly + vy * LOOKAHEAD_MS * 0.5f, maxDistance),
            clamp(lx, ly, lx + vx * LOOKAHEAD_MS, ly + vy * LOOKAHEAD_MS, maxDistance),
        )
    }

    private fun clamp(ax: Float, ay: Float, bx: Float, by: Float, maxDistance: Float): Pair<Float, Float> {
        val d = hypot(bx - ax, by - ay)
        if (d <= maxDistance || d == 0f) return bx to by
        val f = maxDistance / d
        return (ax + (bx - ax) * f) to (ay + (by - ay) * f)
    }

    private companion object {
        const val HISTORY = 8
        const val WINDOW_MS = 30L
        const val LOOKAHEAD_MS = 18L
    }
}
