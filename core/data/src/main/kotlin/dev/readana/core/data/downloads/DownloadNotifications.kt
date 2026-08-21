package dev.readana.core.data.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.readana.core.data.R
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The ongoing notification that keeps a download alive as a foreground service. */
@Singleton
class DownloadNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.core_data_downloads_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.core_data_downloads_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(workId: UUID, title: String, bytes: Long, total: Long?): Notification {
        ensureChannel()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title.ifBlank { context.getString(R.string.core_data_downloads_notification_title) })
            .setSubText(context.getString(R.string.core_data_downloads_notification_title))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.core_data_downloads_notification_cancel),
                WorkManager.getInstance(context).createCancelPendingIntent(workId)
            )
        launchIntent()?.let(builder::setContentIntent)
        if (total != null && total > 0) {
            builder.setProgress(PROGRESS_MAX, ((bytes * PROGRESS_MAX) / total).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    fun notify(id: Int, notification: Notification) {
        // Silently ignored when the user declined POST_NOTIFICATIONS; the download still runs.
        if (manager.areNotificationsEnabled()) {
            manager.notify(id, notification)
        }
    }

    private fun launchIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val PROGRESS_MAX = 100
    }
}
