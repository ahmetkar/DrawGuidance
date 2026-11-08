package com.karapp.drawguidance.diagnostics

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** v37 hata raporunun türü (sunucudaki `kind`): çökme, yakalanan hata, kullanıcının bildirdiği sorun. */
enum class ReportKind { CRASH, ERROR, USER }

/** v37 rapor gönderme tercihi: her seferinde sor, sormadan gönder, hiç gönderme. */
enum class ReportPolicy { ASK, AUTO, NEVER }

/**
 * v37 bir hata kaydı: rapor penceresinde ayrıntısı gösterilir, kullanıcı isterse (not ekleyerek) sunucuya gönderilir.
 * Çizimler ve kişisel bilgiler rapora girmez; yalnız hata, özelliğin adı ve son birkaç işlem (iz).
 */
@Serializable
data class ErrorReport(
    val id: String,
    val kind: ReportKind,
    /** Hatanın olduğu özellik/ekran (ör. "canvas.export", "thumbs"). */
    val feature: String,
    val message: String,
    val stackTrace: String,
    val thread: String? = null,
    /** Cihaz saati (ms). */
    val time: Long,
    /** Aynı hatanın tekrarlarını birleştiren imza (hata türü ve ilk satırlar). */
    val signature: String,
    val breadcrumbs: List<String> = emptyList(),
)

/** v37 raporun gideceği yer (uygulama açılınca [com.karapp.drawguidance.data.reports.ErrorReporter] bağlanır). */
interface ErrorSink {
    val policy: ReportPolicy
    fun send(report: ErrorReport, note: String?)

    /** v40 giriş yapılmış mı: sorun bildirmek giriş ister (girişsizken pencere "önce giriş yapın" uyarısı gösterir). */
    val signedIn: Boolean get() = true
}

/**
 * v37 merkezi hata yönetimi. Bütün özellikler hatalarını buraya bildirir:
 * - [guard] bir işi korur: hata olursa kaydedilir, uygulama çökmez, yedek değer döner;
 * - [report] yakalanmış bir hatayı bildirir ([AppLog.e] hatayla çağrılınca da buraya gelir);
 * - yakalanmamış hatalar (çökme) [Diagnostics] tarafından diske yazılır, sonraki açılışta rapor penceresi açılır.
 *
 * Kullanıcının fark ettiği hatalarda (`userVisible`) ve çökmelerde, tercihe göre ([ReportPolicy]) rapor penceresi
 * açılır ([prompt]) ya da rapor doğrudan gönderilir. Aynı hata bir oturumda bir kez sorulur.
 */
object AppErrors {
    private const val MAX_BREADCRUMBS = 30
    private const val MAX_TRACE = 12_000
    private const val PENDING = "diagnostics/pending-crash.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val crumbs = ArrayDeque<String>()
    /** Bu oturumda sorulan/gönderilen imzalar. */
    private val handled = HashSet<String>()
    private val _prompt = MutableStateFlow<ErrorReport?>(null)

    /** Rapor penceresinde gösterilecek hata (yoksa null). */
    val prompt: StateFlow<ErrorReport?> = _prompt.asStateFlow()

    @Volatile var sink: ErrorSink? = null

    /**
     * v40 pencere sağanağı önlemi: kendiliğinden açılan iki rapor penceresi arasında en az bu kadar süre (ms). Aynı
     * hata (imza) zaten oturumda bir kez sorulur; bu süre farklı hataların arka arkaya pencere açmasını önler.
     */
    @Volatile var minPromptIntervalMs: Long = 30_000L
    @Volatile var clock: () -> Long = System::currentTimeMillis
    @Volatile private var lastPromptAt = Long.MIN_VALUE / 2
    /** v40 bu oturumda aynı hatanın kaç kez olduğu (pencerede "n kez oldu" gösterilir). */
    private val repeats = HashMap<String, Int>()

    /** Bu oturumda [signature] imzalı hatanın sayısı. */
    fun repeatCount(signature: String): Int = synchronized(repeats) { repeats[signature] ?: 0 }

    /** Son işlemlerin kısa izi (rapora eklenir; ör. "tuval açıldı: COMIC"). */
    fun breadcrumb(text: String) {
        val line = text.take(120)
        synchronized(crumbs) {
            crumbs.addLast(line)
            while (crumbs.size > MAX_BREADCRUMBS) crumbs.removeFirst()
        }
        // v42 diske de (beklenmedik kapanmada son işlemler bilinsin)
        runCatching { SessionWatch.crumb(line) }
    }

    fun breadcrumbs(): List<String> = synchronized(crumbs) { crumbs.toList() }

    /**
     * [block]'u korur: hata olursa [feature] adıyla bildirilir ve [fallback] döner (iptal hataları geçer). Askıya
     * alınan işlerde de kullanılır (satır içi).
     */
    inline fun <T> guard(feature: String, fallback: T, userVisible: Boolean = false, block: () -> T): T =
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            report(feature, t, userVisible)
            fallback
        }

    /**
     * Yakalanmış bir hatayı bildirir. [userVisible]: kullanıcı işinin yapılamadığını fark etti (pencere açılabilir).
     * [log]: günlüğe de yazılır ([AppLog.e] zaten yazdığı için kendisi false verir).
     */
    fun report(feature: String, error: Throwable, userVisible: Boolean = false, log: Boolean = true) {
        if (error is CancellationException) return
        val r = build(ReportKind.ERROR, feature, error, Thread.currentThread().name)
        if (log) AppLog.add(LogLevel.ERROR, feature, "Hata: " + error.stackTraceToString().take(2000))
        dispatch(r, ask = userVisible)
    }

    /** Kullanıcının kendi bildirdiği sorun (Ayarlar → Sorun bildir): pencere her zaman açılır. */
    fun userReport(feature: String = "user") {
        _prompt.value = ErrorReport(
            UUID.randomUUID().toString(), ReportKind.USER, feature, "", "", null, System.currentTimeMillis(), "user", breadcrumbs(),
        )
    }

    /** Pencere kapandı: gönderilmeden. */
    fun dismiss(context: Context? = null) {
        val r = _prompt.value ?: return
        _prompt.value = null
        if (r.kind == ReportKind.CRASH) context?.let { clearPendingCrash(it) }
    }

    /** Pencerede "Gönder": [note] kullanıcının açıklaması. */
    fun send(note: String?, context: Context? = null) {
        val r = _prompt.value ?: return
        _prompt.value = null
        runCatching { sink?.send(r, note?.trim()?.takeIf { it.isNotEmpty() }?.take(1000)) }
        if (r.kind == ReportKind.CRASH) context?.let { clearPendingCrash(it) }
    }

    private fun dispatch(r: ErrorReport, ask: Boolean) {
        synchronized(repeats) { repeats[r.signature] = (repeats[r.signature] ?: 0) + 1 }
        val first = synchronized(handled) { handled.add(r.signature) }
        if (!first) return
        when (sink?.policy ?: ReportPolicy.ASK) {
            ReportPolicy.NEVER -> Unit
            ReportPolicy.AUTO -> runCatching { sink?.send(r, null) }
            // Sorulacaksa pencere yalnız kullanıcının fark ettiği hatalarda açılır; açık pencere varsa ya da son
            // pencereden beri [minPromptIntervalMs] geçmediyse beklemez (v40 sağanak önlemi)
            ReportPolicy.ASK -> if (ask && _prompt.value == null && promptAllowed()) show(r) else synchronized(handled) { handled.remove(r.signature) }
        }
    }

    private fun promptAllowed(): Boolean = clock() - lastPromptAt >= minPromptIntervalMs

    private fun show(r: ErrorReport) {
        lastPromptAt = clock()
        _prompt.value = r
    }

    fun build(kind: ReportKind, feature: String, error: Throwable, thread: String?): ErrorReport = ErrorReport(
        id = UUID.randomUUID().toString(),
        kind = kind,
        feature = feature.take(80),
        message = (error.message ?: error.javaClass.simpleName).take(500),
        stackTrace = error.stackTraceToString().take(MAX_TRACE),
        thread = thread,
        time = System.currentTimeMillis(),
        signature = signature(error),
        breadcrumbs = breadcrumbs(),
    )

    /** Hata türü ve ilk beş satırının özeti (satır numaraları dahil; ileti hariç, çünkü değişken olabilir). */
    fun signature(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()
        val key = buildString {
            append(root.javaClass.name)
            root.stackTrace.take(5).forEach { append('|').append(it.className).append('.').append(it.methodName).append(':').append(it.lineNumber) }
        }
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    // --- Çökme (yakalanmamış hata) ---

    /** Çökme anında (iş parçacığı ölmeden) rapor diske yazılır; uygulama yeniden açılınca sorulur. */
    fun writeCrash(context: Context, thread: Thread, error: Throwable) {
        val base = build(ReportKind.CRASH, "crash", error, thread.name)
        // v42 bellek durumu da (bellek yetersizliği çökmelerini anlamak için)
        val memory = runCatching { SessionWatch.memoryInfo(context) }.getOrDefault("")
        writePending(context, base.copy(stackTrace = (base.stackTrace + "\n\n--- Bellek ---\n" + memory).take(MAX_TRACE + 1_000)))
    }

    /** v42 bekleyen raporu (sonraki açılışta sorulur) yazar. */
    fun writePending(context: Context, report: ErrorReport) {
        val f = File(context.filesDir, PENDING)
        f.parentFile?.mkdirs()
        f.writeText(json.encodeToString(ErrorReport.serializer(), report))
    }

    /** v42 bekleyen çökme raporu var mı. */
    fun hasPendingCrash(context: Context): Boolean = File(context.filesDir, PENDING).exists()

    /** Açılışta: önceki çalışmada çökme olduysa tercihe göre pencere açılır ya da rapor gönderilir. */
    fun checkPendingCrash(context: Context) {
        val f = File(context.filesDir, PENDING)
        if (!f.exists()) return
        val r = runCatching { json.decodeFromString(ErrorReport.serializer(), f.readText()) }.getOrNull()
        if (r == null) { f.delete(); return }
        when (sink?.policy ?: ReportPolicy.ASK) {
            ReportPolicy.NEVER -> f.delete()
            ReportPolicy.AUTO -> { runCatching { sink?.send(r, null) }; f.delete() }
            ReportPolicy.ASK -> { synchronized(handled) { handled.add(r.signature) }; _prompt.value = r }
        }
    }

    fun clearPendingCrash(context: Context) { File(context.filesDir, PENDING).delete() }

    /** Testler için: oturum kaydı sıfırlanır. */
    fun resetForTest() {
        synchronized(handled) { handled.clear() }
        synchronized(crumbs) { crumbs.clear() }
        synchronized(repeats) { repeats.clear() }
        lastPromptAt = Long.MIN_VALUE / 2
        minPromptIntervalMs = 30_000L
        clock = System::currentTimeMillis
        _prompt.value = null
        sink = null
    }
}
