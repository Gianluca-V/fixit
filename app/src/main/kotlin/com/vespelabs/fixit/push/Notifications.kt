package com.vespelabs.fixit.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vespelabs.fixit.MainActivity
import com.vespelabs.fixit.R
import com.vespelabs.fixit.core.data.push.PushEvent
import com.vespelabs.fixit.core.data.push.PushKeys

/**
 * System notices of push events (design 14b.5). Fixed texts only; `tag = "${type}_${requestId}"` with a fixed id,
 * setOnlyAlertOnce and setAutoCancel: a repeated notice (two simultaneous taps on "Aceptar") replaces the previous one
 * without sounding again. Checked by hand in 4.13.2.
 */
object Notifications {
    const val CHANNEL_ID = "requests"
    private const val NOTICE_ID = 1

    /** Called from FixItApplication.onCreate; creating an existing channel is a no-op. */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_requests),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Posts nothing when notices are not allowed (denied permission or disabled), so no SecurityException. */
    fun show(context: Context, event: PushEvent) {
        val manager = NotificationManagerCompat.from(context)
        if (!canPost(context, manager)) return
        val tag = "${event.type.name}_${event.requestId}"
        val text = noticeTextFor(event.type)
        val notice = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(text.title))
            .setContentText(context.getString(text.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, event, tag))
            .build()
        try {
            manager.notify(tag, NOTICE_ID, notice)
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the post: the notice is simply not shown.
        }
    }

    private fun canPost(context: Context, manager: NotificationManagerCompat): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && manager.areNotificationsEnabled()
    }

    /** Only the (type, requestId) pair travels: MainActivity resolves the destination with the current role. */
    private fun openIntent(context: Context, event: PushEvent, tag: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(PushKeys.TYPE, event.type.name)
            .putExtra(PushKeys.REQUEST_ID, event.requestId)
        return PendingIntent.getActivity(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
