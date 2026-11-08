package com.karapp.drawguidance.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.karapp.drawguidance.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v42 hata raporunun düz metni: kopyalanır ya da paylaşılır (e-posta, mesajlaşma). Sunucuya ve girişe gerek yoktur;
 * sunucuya ulaşılamadığında ya da giriş yapılamadığında da rapor geliştiriciye iletilebilir. Çizimler ve kişisel
 * bilgiler girmez: sürüm, cihaz, bellek, hata izi, son işlemler ve son uyarı/hata günlükleri.
 */
object CrashExport {
    const val SUPPORT_EMAIL = "ahmetkar.1258@gmail.com"

    fun text(context: Context, report: ErrorReport, note: String? = null): String = buildString {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(report.time))
        appendLine("DrawGuidance hata raporu")
        appendLine("Tür: ${report.kind} · Özellik: ${report.feature} · Zaman: $time")
        appendLine("Uygulama: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Dil: ${Locale.getDefault().toLanguageTag()} · İmza: ${report.signature}")
        if (!note.isNullOrBlank()) { appendLine(); appendLine("Not: ${note.trim()}") }
        appendLine()
        appendLine("İleti: ${report.message}")
        if (report.thread != null) appendLine("İş parçacığı: ${report.thread}")
        appendLine()
        appendLine(report.stackTrace)
        if (report.breadcrumbs.isNotEmpty()) {
            appendLine()
            appendLine("--- Son işlemler ---")
            report.breadcrumbs.forEach { appendLine(it) }
        }
        appendLine()
        appendLine("--- Şimdiki bellek ---")
        appendLine(SessionWatch.memoryInfo(context))
        val logs = AppLog.entries().filter { it.level >= LogLevel.WARN }.takeLast(40)
        if (logs.isNotEmpty()) {
            appendLine()
            appendLine("--- Bu oturumun uyarı ve hataları ---")
            logs.forEach { appendLine("${it.time} ${it.level.letter}/${it.tag}: ${it.message.take(600)}") }
        }
    }

    /** Panoya kopyalar. */
    fun copy(context: Context, text: String): Boolean = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("DrawGuidance hata raporu", text))
        true
    }.getOrDefault(false)

    /** Paylaşım penceresi (e-posta uygulamasında alıcı ve konu dolu gelir). */
    fun share(context: Context, text: String): Boolean = runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "DrawGuidance hata raporu ${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})")
            // Bazı e-posta uygulamaları çok uzun metni kabul etmez
            putExtra(Intent.EXTRA_TEXT, text.take(90_000))
        }
        val chooser = Intent.createChooser(send, "Hata raporunu gönder").apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    }.getOrDefault(false)
}
