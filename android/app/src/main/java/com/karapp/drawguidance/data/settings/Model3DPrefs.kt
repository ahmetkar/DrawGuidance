package com.karapp.drawguidance.data.settings

import android.content.Context
import android.os.Build
import com.karapp.drawguidance.diagnostics.AppLog
import com.karapp.drawguidance.ui.screens.model3d.gl.GlProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * v24 3B düzenleyicinin çizim tercihleri: ekran kartıyla (OpenGL ES 2.0) çizim açık mı, görüntü kalitesi ve ekran
 * kartı sınamasının sonucu. Sınama ([GlProbe]) her kurulumda (APK güncellenince) bir kez arka planda yapılır; başarısızsa ya da
 * ekran kartı çizimi sırasında hata olursa düzenleyici yazılım çiziciye geçer (Ayarlar'dan yeniden açılabilir).
 */
class Model3DPrefs(context: Context, private val versionCode: Long = 0L) {
    private val prefs by lazy { context.getSharedPreferences("model3d_prefs", Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _gpu by lazy { MutableStateFlow(prefs.getBoolean(KEY_GPU, true)) }
    /** Kullanıcının tercihi: ekran kartıyla çiz. */
    val gpu: StateFlow<Boolean> get() = _gpu.asStateFlow()

    // v25 seçilmemişse cihaz sınıfına göre (giriş düzeyi cihazda hızlı, üst düzeyde yüksek)
    private val _quality by lazy {
        MutableStateFlow(prefs.getInt(KEY_QUALITY, com.karapp.drawguidance.ui.screens.model3d.Budget3D.defaultQuality).coerceIn(QUALITY_HIGH, QUALITY_FAST))
    }
    /** Görüntü kalitesi: 0 yüksek (4× kenar yumuşatma), 1 dengeli (2×), 2 hızlı (yumuşatmasız, ¾ çözünürlük). */
    val quality: StateFlow<Int> get() = _quality.asStateFlow()

    private val _available by lazy {
        MutableStateFlow(
            when {
                isRobolectric -> false
                prefs.getLong(KEY_PROBE_VERSION, -1L) == versionCode -> prefs.getBoolean(KEY_PROBE_OK, false)
                else -> null
            },
        )
    }
    /** Ekran kartı kullanılabilir mi (null: henüz sınanmadı). */
    val available: StateFlow<Boolean?> get() = _available.asStateFlow()

    private val _info by lazy { MutableStateFlow(prefs.getString(KEY_INFO, null)) }
    /** Ekran kartının adı ya da hata nedeni. */
    val info: StateFlow<String?> get() = _info.asStateFlow()

    // v27 3B çizim önerisi: açık/kapalı ve cihazda öğrenilen kişisel durum (SketchModel.encode)
    private val _suggest by lazy { MutableStateFlow(prefs.getBoolean(KEY_SUGGEST, true)) }
    val suggest: StateFlow<Boolean> get() = _suggest.asStateFlow()

    fun setSuggest(value: Boolean) {
        _suggest.value = value
        prefs.edit().putBoolean(KEY_SUGGEST, value).apply()
    }

    /** Öneri modelinin kişisel durumu (yoksa null). */
    val sketchModel: String? get() = prefs.getString(KEY_SKETCH_MODEL, null)

    fun setSketchModel(value: String) { prefs.edit().putString(KEY_SKETCH_MODEL, value).apply() }

    fun setGpu(value: Boolean) {
        _gpu.value = value
        prefs.edit().putBoolean(KEY_GPU, value).apply()
        // Kullanıcı yeniden açınca sınama tekrarlanır (sürücü güncellenmiş olabilir)
        if (value && _available.value == false && !isRobolectric) {
            _available.value = null
            prefs.edit().remove(KEY_PROBE_VERSION).apply()
            probe()
        }
    }

    fun setQuality(value: Int) {
        _quality.value = value.coerceIn(QUALITY_HIGH, QUALITY_FAST)
        prefs.edit().putInt(KEY_QUALITY, _quality.value).apply()
    }

    /** Gerekirse ekran kartını arka planda sınar. */
    fun probe() {
        if (_available.value != null) return
        scope.launch {
            val r = runCatching { GlProbe.run() }.getOrElse { GlProbe.Result(false, it.message ?: "hata") }
            AppLog.i("Model3D", "Ekran kartı sınaması: ${if (r.ok) "uygun" else "uygun değil"} (${r.info})")
            prefs.edit().putLong(KEY_PROBE_VERSION, versionCode).putBoolean(KEY_PROBE_OK, r.ok).putString(KEY_INFO, r.info).apply()
            _info.value = r.info
            _available.value = r.ok
        }
    }

    /** Ekran kartı çiziminde hata: bu sürümde yazılım çiziciye geçilir. */
    fun gpuFailed(reason: String) {
        AppLog.w("Model3D", "Ekran kartı çizimi başarısız, yazılım çizici kullanılacak: $reason")
        prefs.edit().putLong(KEY_PROBE_VERSION, versionCode).putBoolean(KEY_PROBE_OK, false).putString(KEY_INFO, reason).apply()
        _info.value = reason
        _available.value = false
    }

    /** Ekran kartının adı (çizim başlayınca). */
    fun gpuInfo(name: String) {
        if (name.isBlank() || name == _info.value) return
        _info.value = name
        prefs.edit().putString(KEY_INFO, name).apply()
    }

    companion object {
        const val QUALITY_HIGH = 0
        const val QUALITY_BALANCED = 1
        const val QUALITY_FAST = 2
        private const val KEY_GPU = "gpu"
        private const val KEY_QUALITY = "quality"
        private const val KEY_PROBE_VERSION = "probe_version"
        private const val KEY_PROBE_OK = "probe_ok"
        private const val KEY_INFO = "gpu_info"
        private const val KEY_SUGGEST = "sketch_suggest"
        private const val KEY_SKETCH_MODEL = "sketch_model"

        val isRobolectric: Boolean get() = Build.FINGERPRINT == "robolectric"
    }
}
