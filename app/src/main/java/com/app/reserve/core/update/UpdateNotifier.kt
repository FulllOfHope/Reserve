package com.app.reserve.core.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.app.reserve.MainActivity
import com.app.reserve.R

/** Posts the single, non-intrusive app-update notification. */
class UpdateNotifier(private val context: Context) {

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        // minSdk 26: channels always exist.
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "New versions of Reserve"
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun show(update: UpdateInfo) {
        if (!hasPermission()) return
        val openApp = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_UPDATE_SHEET, true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_OPEN,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val download = Intent(Intent.ACTION_VIEW, update.apkUrl.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val downloadPendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_DOWNLOAD,
            download,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val later = Intent(appContext, UpdateNotificationReceiver::class.java).apply {
            action = UpdateNotificationReceiver.ACTION_LATER
        }
        val laterPendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_LATER,
            later,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reserve)
            .setContentTitle("Reserve update available")
            .setContentText("Version ${update.versionName} is ready to install")
            .setContentIntent(openPendingIntent)
            .addAction(
                Notification.Action.Builder(null, "Download update", downloadPendingIntent).build()
            )
            .addAction(Notification.Action.Builder(null, "Later", laterPendingIntent).build())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun cancel() {
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    companion object {
        const val CHANNEL_ID = "reserve_updates"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN = 4101
        private const val REQUEST_DOWNLOAD = 4102
        private const val REQUEST_LATER = 4103
    }
}

/** Handles the "Later" notification action without launching the UI. */
class UpdateNotificationReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_LATER) UpdateNotifier(context.applicationContext).cancel()
    }

    companion object {
        const val ACTION_LATER = "com.app.reserve.UPDATE_LATER"
    }
}
