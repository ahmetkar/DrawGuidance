package com.karapp.drawguidance.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.karapp.drawguidance.MainActivity
import com.karapp.drawguidance.R

/**
 * v40 yerel bildirimler (FCM yok: Google kitaplıkları gerekmeden WorkManager + NotificationChannel). Kanallar:
 * güncellemeler, hatırlatmalar ve v43 sunucudan gelen bildirimler (gelen kutusu yoklamasıyla gelir). Android 13+ izni
 * yoksa ya da kullanıcı bildirimleri kapattıysa sessizce atlanır.
 */
object AppNotifications {
    const val CHANNEL_UPDATES = "dg-updates"
    const val CHANNEL_REMINDERS = "dg-reminders"
    const val CHANNEL_MESSAGES = "dg-messages"
    /** v43 sunucu bildirimlerinin kimlik aralığı (olay tablosundaki kimlik + bu değer). */
    const val ID_MESSAGE_BASE = 50_000
    const val ID_UPDATE = 4001
    const val ID_REMINDER = 4002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_UPDATES, context.getString(R.string.v40_notif_channel_updates), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.v40_notif_channel_reminders), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_MESSAGES, context.getString(R.string.v43_notif_channel_messages), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Android 13+ çalışma zamanı izni (öncesinde her zaman var). */
    fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canPost(context: Context): Boolean = permissionGranted(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Play Store'daki sayfa (uygulama yoksa tarayıcıda). */
    fun storeIntent(context: Context): Intent {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (market.resolveActivity(context.packageManager) != null) market
        else Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** v43 güncelleme bildirimine dokununca uygulama açılır ve güncelleme penceresi gösterilir (Play'e oradan gidilir). */
    const val EXTRA_SHOW_UPDATE = "com.karapp.drawguidance.SHOW_UPDATE"

    fun postUpdate(context: Context, message: String?): Boolean {
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SHOW_UPDATE, true)
        val pi = PendingIntent.getActivity(context, ID_UPDATE, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return post(context, CHANNEL_UPDATES, ID_UPDATE, context.getString(R.string.v40_notif_update_title), message ?: context.getString(R.string.v40_notif_update_text), pi)
    }

    /** v43 sunucudan gelen bildirim (dokununca uygulama açılır). */
    fun postMessage(context: Context, eventId: Int, title: String, text: String): Boolean {
        val id = ID_MESSAGE_BASE + (eventId and 0xFFFF)
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return post(context, CHANNEL_MESSAGES, id, title, text, pi)
    }

    fun postReminder(context: Context, stage: Int): Boolean {
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, ID_REMINDER, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = when (stage) {
            1 -> R.string.v40_notif_reminder_1
            2 -> R.string.v40_notif_reminder_2
            else -> R.string.v40_notif_reminder_3
        }
        return post(context, CHANNEL_REMINDERS, ID_REMINDER, context.getString(R.string.v40_notif_reminder_title), context.getString(text), pi)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun post(context: Context, channel: String, id: Int, title: String, text: String, intent: PendingIntent): Boolean {
        if (!canPost(context)) return false
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(id, n) }.isSuccess
    }
}
