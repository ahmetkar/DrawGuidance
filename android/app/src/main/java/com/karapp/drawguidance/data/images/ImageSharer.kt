package com.karapp.drawguidance.data.images

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Resmin paylaşılabileceği uygulamalar. Paylaşım Android'in paylaşma niyetiyle yapılır; API anahtarı gerekmez. */
enum class ShareTarget(val packages: List<String>, val displayName: String) {
    WHATSAPP(listOf("com.whatsapp", "com.whatsapp.w4b"), "WhatsApp"),
    INSTAGRAM(listOf("com.instagram.android"), "Instagram"),
    PINTEREST(listOf("com.pinterest"), "Pinterest"),
    OTHER(emptyList(), ""),
}

sealed interface ShareResult {
    data object Started : ShareResult
    data class NotInstalled(val target: ShareTarget) : ShareResult
    data object FileMissing : ShareResult
    /** v42 belgeyi açacak uygulama yok. */
    data object NoViewer : ShareResult
}

/**
 * Kaydedilen PNG'yi seçilen uygulamaya gönderir. Galerideki resimler (content://) doğrudan, uygulama
 * klasöründekiler (file://) FileProvider üzerinden geçici okuma izniyle paylaşılır.
 */
object ImageSharer {

    fun shareUri(context: Context, uri: String): Uri {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != "file") return parsed
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(requireNotNull(parsed.path)))
    }

    fun sendIntent(context: Context, uri: String, packageName: String?, mime: String = "image/png"): Intent {
        val stream = shareUri(context, uri)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, stream)
            clipData = ClipData.newRawUri("", stream)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (packageName != null) setPackage(packageName)
        }
    }

    private fun readable(context: Context, uri: String): Boolean = runCatching {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") File(requireNotNull(parsed.path)).canRead()
        else context.contentResolver.openInputStream(parsed)?.use { true } ?: false
    }.getOrDefault(false)

    /** Diyalog içindeki bağlam sarmalayıcıdır; paylaşım asıl etkinlikten başlatılır (yeni görev açılmaz). */
    private fun Context.activityOrSelf(): Context {
        var c: Context = this
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        return if (c is android.app.Activity) c else this
    }

    fun share(originContext: Context, uri: String, target: ShareTarget, chooserTitle: String, mime: String = "image/png"): ShareResult {
        val context = originContext.activityOrSelf()
        if (!readable(context, uri)) return ShareResult.FileMissing
        if (target == ShareTarget.OTHER) {
            val chooser = Intent.createChooser(sendIntent(context, uri, null, mime), chooserTitle)
            if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            return ShareResult.Started
        }
        for (pkg in target.packages) {
            val intent = sendIntent(context, uri, pkg, mime)
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return ShareResult.Started
            } catch (_: ActivityNotFoundException) {
                // Sıradaki paket (ör. WhatsApp Business) denenir
            }
        }
        return ShareResult.NotInstalled(target)
    }

    /**
     * v42 kaydedilen belgeyi (PDF, CBZ, TIFF) cihazdaki uygulamayla açar: açacak uygulama yoksa [ShareResult.NoViewer],
     * dosya okunamıyorsa [ShareResult.FileMissing].
     */
    fun open(originContext: Context, uri: String, mime: String): ShareResult {
        val context = originContext.activityOrSelf()
        if (!readable(context, uri)) return ShareResult.FileMissing
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareUri(context, uri), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(view)
            ShareResult.Started
        } catch (_: ActivityNotFoundException) {
            ShareResult.NoViewer
        }
    }

    /** Uygulamanın Play Store sayfası (Play Store yoksa tarayıcıda). */
    fun openStore(originContext: Context, target: ShareTarget) {
        val context = originContext.activityOrSelf()
        val pkg = target.packages.firstOrNull() ?: return
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))) }
        }
    }
}
