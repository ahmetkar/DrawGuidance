package com.karapp.drawguidance.diagnostics

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.StrictMode
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hafif gözlemlenebilirlik: yakalanmamış hatalar uygulama klasörüne yazılır (bir sonraki açılışta logcat'e
 * aktarılır); hata ayıklama derlemesinde ana iş parçacığında disk/ağ erişimi ve kapatılmamış kaynaklar
 * StrictMode ile logcat'e raporlanır. Bulut servisi kullanılmaz.
 */
object Diagnostics {
    private const val TAG = "DrawGuidance"
    private const val DIR = "diagnostics"
    private const val FILE = "last-crash.txt"

    fun install(context: Context) {
        val file = crashFile(context)
        // Önceki çalışmada çökme olduysa logcat'e yazılır (dosya kullanıcının silmesi için kalır)
        if (file.exists()) runCatching { AppLog.w(TAG, "Önceki çalışmada çökme:\n" + file.readText().take(MAX_LOG)) }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { AppLog.add(LogLevel.ASSERT, "AndroidRuntime", "Çökme (${thread.name}): " + error.stackTraceToString()) }
            // v37 sonraki açılışta rapor penceresi için (iz ve imzayla)
            runCatching { AppErrors.writeCrash(context, thread, error) }
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(
                    buildString {
                        appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("İş parçacığı: ${thread.name}")
                        append(error.stackTraceToString())
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable && Build.FINGERPRINT != "robolectric") enableStrictMode()
    }

    /** Son çökmenin kaydı (yoksa null). */
    fun lastCrash(context: Context): String? = crashFile(context).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clearLastCrash(context: Context) {
        crashFile(context).delete()
    }

    private fun crashFile(context: Context) = File(context.filesDir, "$DIR/$FILE")

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().penaltyLog().build(),
        )
    }

    private const val MAX_LOG = 4000
}
