package com.karapp.drawguidance.diagnostics

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import java.io.File
import java.util.concurrent.Executors

/**
 * v42 beklenmedik kapanmaların yakalanması. Java/Kotlin çökmeleri [Diagnostics] tarafından diske yazılır; ama yerel
 * (native) çökme, donma (ANR), bellek yetmediği için sistemin uygulamayı öldürmesi gibi durumlarda yakalanacak bir
 * istisna yoktur. Bu nesne uygulama ön plandayken bir "oturum açık" dosyası tutar (son ekran ve son işlemlerle);
 * uygulama arka plana geçince dosya silinir. Açılışta dosya hâlâ duruyorsa önceki çalışma ön plandayken kapanmıştır:
 * kendi günlük kayıtlarımız (logcat, yalnız bu uygulamanınkiler) ve bellek bilgisi rapora eklenir, rapor penceresi
 * açılır. Rapor kopyalanıp ya da paylaşılarak (e-posta) sunucu ve giriş olmadan da gönderilebilir.
 */
object SessionWatch {
    private const val FILE = "diagnostics/session-open.txt"
    private const val CRUMBS = "diagnostics/breadcrumbs.txt"
    private const val MAX_CRUMBS = 40

    /** Testlerde kapalı (Robolectric her testte yeni dosya klasörü verir, yine de varsayılan kapalı). */
    @Volatile var enabled: Boolean = Build.FINGERPRINT != "robolectric"

    @Volatile private var appContext: Context? = null
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "dg-session").apply { isDaemon = true } }
    private val crumbs = ArrayDeque<String>()
    @Volatile private var screen: String = "-"
    @Volatile private var resumed = 0

    fun install(app: Application) {
        appContext = app.applicationContext
        // Önceki çalışmanın son işlemleri (açılış raporunda kullanılır), sonra yeni oturum boş başlar
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { resumed++; if (resumed == 1) open() }
            override fun onActivityPaused(activity: Activity) { resumed = (resumed - 1).coerceAtLeast(0); if (resumed == 0) close() }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** Gezinme ve önemli işlemler ([AppErrors.breadcrumb] de buraya yazar). */
    fun crumb(text: String) {
        val line = "${System.currentTimeMillis() % 100_000_000} $text".take(140)
        synchronized(crumbs) {
            crumbs.addLast(line)
            while (crumbs.size > MAX_CRUMBS) crumbs.removeFirst()
        }
        if (text.startsWith("ekran:")) screen = text.removePrefix("ekran:").trim()
        if (resumed > 0) write()
    }

    private fun open() = write()

    private fun close() {
        val ctx = appContext ?: return
        if (!enabled) return
        io.execute { runCatching { File(ctx.filesDir, FILE).delete() } }
    }

    private fun write() {
        val ctx = appContext ?: return
        if (!enabled) return
        val snapshot = synchronized(crumbs) { crumbs.toList() }
        val s = screen
        io.execute {
            runCatching {
                val f = File(ctx.filesDir, FILE)
                f.parentFile?.mkdirs()
                f.writeText("${System.currentTimeMillis()}\n$s\n")
                File(ctx.filesDir, CRUMBS).writeText(snapshot.joinToString("\n"))
            }
        }
    }

    /**
     * Açılışta (arka plan iş parçacığında) çağrılır: önceki çalışma ön plandayken kapandıysa ve Java çökmesi
     * yazılmamışsa beklenmedik kapanma raporu bekleyen rapor olarak yazılır ([AppErrors.checkPendingCrash] açar).
     */
    fun checkPrevious(context: Context): Boolean {
        if (!enabled) return false
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return false
        val lines = runCatching { f.readLines() }.getOrDefault(emptyList())
        f.delete()
        if (AppErrors.hasPendingCrash(context)) return false
        val lastScreen = lines.getOrNull(1).orEmpty()
        val previousCrumbs = runCatching { File(context.filesDir, CRUMBS).readLines() }.getOrDefault(emptyList())
        val trace = buildString {
            appendLine("Önceki çalışma ön plandayken beklenmedik şekilde kapandı (yerel çökme, donma ya da bellek yetersizliği olabilir).")
            appendLine("Son ekran: ${lastScreen.ifBlank { "-" }}")
            appendLine()
            appendLine("--- Bellek ---")
            appendLine(memoryInfo(context))
            appendLine()
            appendLine("--- Günlük (crash) ---")
            appendLine(logcat(listOf("logcat", "-d", "-b", "crash", "-v", "threadtime", "-t", "200"), 24_000))
            appendLine("--- Günlük (son kayıtlar, uyarı ve hatalar) ---")
            append(logcat(listOf("logcat", "-d", "-v", "threadtime", "-t", "1500", "*:W"), 40_000))
        }
        val report = ErrorReport(
            id = java.util.UUID.randomUUID().toString(),
            kind = ReportKind.CRASH,
            feature = "unexpected_exit" + if (lastScreen.isNotBlank()) ":$lastScreen" else "",
            message = "Beklenmedik kapanma" + if (lastScreen.isNotBlank()) " ($lastScreen)" else "",
            stackTrace = trace.take(60_000),
            thread = null,
            time = lines.firstOrNull()?.toLongOrNull() ?: System.currentTimeMillis(),
            signature = "unexpected_exit:$lastScreen",
            breadcrumbs = previousCrumbs.takeLast(30),
        )
        AppErrors.writePending(context, report)
        return true
    }

    /** Uygulamanın kendi logcat kayıtları (Android 4.1'den beri uygulama yalnız kendi kayıtlarını okuyabilir). */
    fun logcat(cmd: List<String>, maxChars: Int): String = runCatching {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().use { r ->
            val sb = StringBuilder()
            val buf = CharArray(8192)
            while (true) {
                val n = r.read(buf)
                if (n < 0) break
                sb.append(buf, 0, n)
                // en son kayıtlar önemli: baştan kırp
                if (sb.length > maxChars * 2) sb.delete(0, sb.length - maxChars)
            }
            sb.toString()
        }
        runCatching { p.destroy() }
        if (text.length > maxChars) text.takeLast(maxChars) else text
    }.getOrElse { "(günlük okunamadı: ${it.javaClass.simpleName})" }

    /** Cihazın ve uygulamanın bellek durumu (rapor için). */
    fun memoryInfo(context: Context): String = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val rt = Runtime.getRuntime()
        val mb = 1024 * 1024
        "Cihaz RAM: ${mi.totalMem / mb} MB, boş: ${mi.availMem / mb} MB, düşük bellek: ${mi.lowMemory}, eşik: ${mi.threshold / mb} MB\n" +
            "Uygulama sınırı: ${am.memoryClass} MB (büyük: ${am.largeMemoryClass} MB), düşük RAM cihazı: ${am.isLowRamDevice}\n" +
            "JVM: en çok ${rt.maxMemory() / mb} MB, ayrılan ${rt.totalMemory() / mb} MB, boş ${rt.freeMemory() / mb} MB"
    }.getOrElse { "(bellek bilgisi alınamadı)" }
}
