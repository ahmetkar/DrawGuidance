package com.karapp.drawguidance.data.notes

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.PreparedImage
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * v51 not almada resim düzenleme: not alma modunda resim düzenleme araçları yok; düzenlenmek istenen resim geçici bir
 * gelişmiş çizim tuvaline ([CanvasKind.ART]; v52, önce basit resimdi) eklenir. O tuvalde otomatik kayıt kapalıdır; Kaydet'e dokununca tuval
 * resme çevrilip nottaki resmin yerine konur ve geçici tuval silinir. Kaydetmeden çıkılırsa geçici tuval silinir.
 *
 * Geçici tuvaller ve nota henüz aktarılmamış sonuçlar uygulama kapansa da kaybolmasın diye burada saklanır; geçici
 * tuvaller tuval listelerinde görünmez ([isTemp]).
 */
object TempCanvases {
    /** Bir geçici düzenleme: geçici tuval, kaynak not, nottaki resim elemanı ve geçici tuvalin ilk boyutu. */
    data class Session(
        val tempId: String,
        val sourceId: String,
        val strokeId: String,
        val width: Int,
        val height: Int,
        val createdAt: Long,
    )

    /** Kaydedilen düzenlemenin sonucu: notun resim elemanının yeni resim verisi. */
    data class Result(
        val tempId: String,
        val sourceId: String,
        val strokeId: String,
        val assetId: String,
        val width: Int,
        val height: Int,
        /** Geçici tuvalin boyutu (oranı) değişti: nottaki resmin çerçevesi yeni orana uydurulur. */
        val reshaped: Boolean,
    )

    private const val PREFS = "v51_temp_canvases"
    private const val KEY_SESSIONS = "sessions"
    private const val KEY_RESULTS = "results"

    @Volatile private var prefs: SharedPreferences? = null
    private val sessions = ConcurrentHashMap<String, Session>()
    private val results = ConcurrentHashMap<String, Result>()
    private val active: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val _results = MutableSharedFlow<Result>(extraBufferCapacity = 8)

    /** Nota aktarılacak yeni sonuçlar (açık not düzenleyicisi dinler). */
    val resultFlow: SharedFlow<Result> = _results.asSharedFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching {
            JSONArray(p.getString(KEY_SESSIONS, "[]")).let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val s = Session(o.getString("t"), o.getString("s"), o.getString("e"), o.optInt("w"), o.optInt("h"), o.optLong("c"))
                    sessions[s.tempId] = s
                }
            }
            JSONArray(p.getString(KEY_RESULTS, "[]")).let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val r = Result(o.getString("t"), o.getString("s"), o.getString("e"), o.getString("a"), o.optInt("w"), o.optInt("h"), o.optBoolean("r"))
                    results[r.tempId] = r
                }
            }
        }
        prefs = p
    }

    fun isEmpty(): Boolean = sessions.isEmpty()
    fun isTemp(localId: String): Boolean = sessions.containsKey(localId)
    fun session(localId: String): Session? = sessions[localId]
    fun allSessions(): List<Session> = sessions.values.toList()

    fun register(session: Session) {
        sessions[session.tempId] = session
        persist()
    }

    fun remove(tempId: String) {
        if (sessions.remove(tempId) != null) persist()
    }

    fun putResult(result: Result) {
        results[result.tempId] = result
        persist()
        _results.tryEmit(result)
    }

    /** [sourceId] notuna henüz aktarılmamış sonuçlar. */
    fun pendingFor(sourceId: String): List<Result> = results.values.filter { it.sourceId == sourceId }

    /** Notun kullanılmayan resim verileri temizlenirken korunacaklar (henüz aktarılmamış sonuçların resimleri). */
    fun pendingAssetIds(sourceId: String): List<String> = pendingFor(sourceId).map { it.assetId }

    /** Sonuç nota aktarıldı (ya da resim artık yok): saklanmaz. */
    fun consumed(result: Result) {
        if (results.remove(result.tempId) != null) persist()
    }

    /** Geçici tuvalin düzenleyicisi açık mı (süreç içinde; açık olanlar artık sayılıp silinmez). */
    fun setActive(tempId: String, on: Boolean) {
        if (on) active += tempId else active -= tempId
    }

    fun isActive(tempId: String): Boolean = tempId in active

    private fun persist() {
        val p = prefs ?: return
        val s = JSONArray()
        sessions.values.forEach {
            s.put(JSONObject().put("t", it.tempId).put("s", it.sourceId).put("e", it.strokeId).put("w", it.width).put("h", it.height).put("c", it.createdAt))
        }
        val r = JSONArray()
        results.values.forEach {
            r.put(JSONObject().put("t", it.tempId).put("s", it.sourceId).put("e", it.strokeId).put("a", it.assetId).put("w", it.width).put("h", it.height).put("r", it.reshaped))
        }
        p.edit().putString(KEY_SESSIONS, s.toString()).putString(KEY_RESULTS, r.toString()).apply()
    }

    /** Testler için. */
    internal fun clearForTest() {
        sessions.clear(); results.clear(); active.clear()
    }
}

/** v51 nottaki resim elemanının yeni resimle güncellenmesi (saf; birim testli). */
object TempImageGeometry {
    /**
     * [image] elemanının resmini [result]'takiyle değiştirir. Geçici tuvalin boyutu değişmediyse çerçeve, döndürme ve
     * kırpma aynen kalır; değiştiyse çerçevenin eni ve merkezi korunur, boyu yeni orana göre ayarlanır, kırpma kalkar.
     */
    fun replace(image: Stroke, result: TempCanvases.Result): Stroke {
        if (!result.reshaped || image.points.size != 4 || result.width <= 0 || result.height <= 0) return image.copy(assetId = result.assetId)
        val p = image.points
        val ux = p[1].x - p[0].x; val uy = p[1].y - p[0].y
        val vx = p[3].x - p[0].x; val vy = p[3].y - p[0].y
        val uLen = hypot(ux, uy); val vLen = hypot(vx, vy)
        if (uLen <= 0f || vLen <= 0f) return image.copy(assetId = result.assetId)
        val aspect = result.width.toFloat() / result.height
        val k = (uLen / aspect) / vLen
        val nvx = vx * k; val nvy = vy * k
        val cx = (p[0].x + p[1].x + p[2].x + p[3].x) / 4f
        val cy = (p[0].y + p[1].y + p[2].y + p[3].y) / 4f
        val q = ContentCodec::quantize
        fun pt(su: Float, sv: Float) = StrokePoint(q(cx + ux * su + nvx * sv), q(cy + uy * su + nvy * sv))
        return image.copy(
            assetId = result.assetId,
            points = listOf(pt(-.5f, -.5f), pt(.5f, -.5f), pt(.5f, .5f), pt(-.5f, .5f)),
            clip = null,
        )
    }

    /** Geçici tuvalin boyutu: resmin piksel boyutu, uzun kenarı en çok [maxSide]. */
    fun canvasSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val scale = min(1f, maxSide.toFloat() / max(width, height))
        return (width * scale).roundToInt().coerceAtLeast(16) to (height * scale).roundToInt().coerceAtLeast(16)
    }

    /** İki boyutun oranı farklı mı (yuvarlama payıyla). */
    fun reshaped(w0: Int, h0: Int, w1: Int, h1: Int): Boolean {
        if (w0 <= 0 || h0 <= 0 || w1 <= 0 || h1 <= 0) return false
        val a0 = w0.toFloat() / h0
        val a1 = w1.toFloat() / h1
        return abs(a0 - a1) / a0 > 0.01f
    }

    const val MAX_SIDE = 2400
}

/**
 * v51 geçici resim düzenleme tuvallerini oluşturur, kaydedince nota aktarır ve siler. İşler uygulama kapsamında
 * sürer: düzenleyici kapanırken (geri dönülünce) yarıda kalmaz.
 */
class TempImageEditor(
    context: Context,
    private val repository: CanvasRepository,
    private val assetStore: CanvasAssetStore,
    private val exporter: ImageExporter,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        TempCanvases.init(appContext)
    }

    /** [image] resmi için geçici basit resim tuvali oluşturur; kimliğini döner (olmazsa null). */
    suspend fun start(sourceId: String, image: Stroke): String? = withContext(Dispatchers.Default) {
        val assetId = image.assetId ?: return@withContext null
        val bytes = runCatching { assetStore.bytes(assetId) }.getOrNull() ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val (w, h) = TempImageGeometry.canvasSize(bounds.outWidth, bounds.outHeight)
        if (w <= 0 || h <= 0) return@withContext null
        val tempId = UUID.randomUUID().toString()
        // Önce kaydedilir: tuval oluşur oluşmaz listelerden gizlenir
        TempCanvases.register(TempCanvases.Session(tempId, sourceId, image.id, w, h, System.currentTimeMillis()))
        try {
            repository.createCanvas(
                name = appContext.getString(R.string.v51_temp_image_name), width = w, height = h,
                backgroundColorHex = "#FFFFFF", aiEnabled = false, kind = CanvasKind.ART, localId = tempId,
            )
            val copied = assetStore.copyTo(assetId, tempId) ?: error("resim kopyalanamadı")
            val stroke = Stroke(
                id = UUID.randomUUID().toString(),
                points = listOf(StrokePoint(0f, 0f), StrokePoint(w.toFloat(), 0f), StrokePoint(w.toFloat(), h.toFloat()), StrokePoint(0f, h.toFloat())),
                colorHex = "#000000", brushType = BrushType.IMAGE, sizePx = 1f, opacity = 1f, order = 0, assetId = copied,
            )
            repository.saveStrokes(tempId, listOf(listOf(stroke)))
            tempId
        } catch (e: Exception) {
            com.karapp.drawguidance.diagnostics.AppLog.e("TempImage", "Geçici resim tuvali oluşturulamadı", e)
            deleteNow(tempId)
            null
        }
    }

    /** Geçici tuvali resme çevirip nota aktarır (sonuç [TempCanvases.resultFlow] ile gelir) ve geçici tuvali siler. */
    fun commit(tempId: String, canvas: CanvasUi) {
        val session = TempCanvases.session(tempId) ?: return
        scope.launch {
            try {
                // Kağıt beyaz ve desensizse saydam kalır (resmin saydam yerleri nota beyaz kutu olarak gelmesin)
                val plain = canvas.backgroundColorHex.equals("#FFFFFF", ignoreCase = true) && canvas.background.isEmpty && !canvas.hasPageBackgrounds
                val bitmap = exporter.render(
                    canvas, 1f, includeGuides = false,
                    background = if (plain) androidx.compose.ui.graphics.Color.Transparent else null,
                )
                val encoded = com.karapp.drawguidance.data.library.BitmapCodec.encode(bitmap, opaque = !plain)
                val prepared = PreparedImage(encoded, if (plain) "image/png" else "image/webp", bitmap.width, bitmap.height, bitmap, encoded.size, 0f)
                val assetId = assetStore.save(session.sourceId, prepared)
                TempCanvases.putResult(
                    TempCanvases.Result(
                        tempId, session.sourceId, session.strokeId, assetId, bitmap.width, bitmap.height,
                        reshaped = TempImageGeometry.reshaped(session.width, session.height, canvas.width, canvas.height),
                    ),
                )
            } catch (e: Throwable) {
                com.karapp.drawguidance.diagnostics.AppLog.e("TempImage", "Düzenlenen resim nota aktarılamadı", e, userVisible = true)
            } finally {
                deleteNow(tempId)
            }
        }
    }

    /** Kaydetmeden çıkıldı: geçici tuval silinir. */
    fun discard(tempId: String) {
        if (!TempCanvases.isTemp(tempId)) return
        scope.launch { deleteNow(tempId) }
    }

    /**
     * Artık açık olmayan geçici tuvalleri siler: [sourceId] verilirse yalnız o notunkileri (not açılınca), verilmezse
     * [olderThanMs]'den eski olanları (uygulama açılışında).
     */
    fun cleanup(sourceId: String? = null, olderThanMs: Long = 0L) {
        val now = System.currentTimeMillis()
        val stale = TempCanvases.allSessions().filter { s ->
            !TempCanvases.isActive(s.tempId) && (sourceId == null || s.sourceId == sourceId) && now - s.createdAt >= olderThanMs
        }
        if (stale.isEmpty()) return
        scope.launch { stale.forEach { deleteNow(it.tempId) } }
    }

    private suspend fun deleteNow(tempId: String) {
        runCatching { repository.deleteCanvas(tempId) }
        TempCanvases.remove(tempId)
        TempCanvases.setActive(tempId, false)
    }
}
