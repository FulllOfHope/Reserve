package com.app.reserve.core.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.app.reserve.MainActivity
import com.app.reserve.R
import com.app.reserve.core.application.ReserveApplication
import kotlinx.coroutines.launch

/** Posts quiet update notifications: available, downloading, and ready to install. */
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
        val openPendingIntent = activityPendingIntent(
            REQUEST_OPEN,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_UPDATE_SHEET, true)
            }
        )

        val startDownload = broadcastPendingIntent(
            REQUEST_DOWNLOAD,
            Intent(appContext, UpdateNotificationReceiver::class.java).apply {
                action = UpdateNotificationReceiver.ACTION_DOWNLOAD_UPDATE
                putExtra(UpdateNotificationReceiver.EXTRA_UPDATE_VERSION, update.versionName)
                putExtra(UpdateNotificationReceiver.EXTRA_APK_URL, update.apkUrl)
            }
        )

        val later = broadcastPendingIntent(
            REQUEST_LATER,
            Intent(appContext, UpdateNotificationReceiver::class.java).apply {
                action = UpdateNotificationReceiver.ACTION_SNOOZE_UPDATE
                putExtra(UpdateNotificationReceiver.EXTRA_UPDATE_VERSION, update.versionName)
            }
        )

        notify(
            title = "Reserve update available",
            text = "Version ${update.versionName} is ready to install",
            contentIntent = openPendingIntent,
            actions = listOf(
                Notification.Action.Builder(null, "Update", startDownload).build(),
                Notification.Action.Builder(null, "Remind me in a week", later).build()
            )
        )
    }

    fun showReadyToInstall(version: String) {
        if (!hasPermission()) return
        val install = activityPendingIntent(
            REQUEST_INSTALL,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_INSTALL_UPDATE, true)
            }
        )
        val later = broadcastPendingIntent(
            REQUEST_READY_LATER,
            Intent(appContext, UpdateNotificationReceiver::class.java).apply {
                action = UpdateNotificationReceiver.ACTION_SNOOZE_UPDATE
                putExtra(UpdateNotificationReceiver.EXTRA_UPDATE_VERSION, version)
            }
        )
        notify(
            title = "Reserve update ready to install",
            text = "Version $version has been downloaded. Tap to install.",
            contentIntent = install,
            actions = listOf(
                Notification.Action.Builder(null, "Install", install).build(),
                Notification.Action.Builder(null, "Remind me in a week", later).build()
            )
        )
    }

    fun cancel() {
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    private fun notify(
        title: String,
        text: String,
        contentIntent: PendingIntent,
        actions: List<Notification.Action>
    ) {
        val builder = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reserve)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        actions.forEach(builder::addAction)
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    private fun activityPendingIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun broadcastPendingIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val CHANNEL_ID = "reserve_updates"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN = 4101
        private const val REQUEST_DOWNLOAD = 4102
        private const val REQUEST_LATER = 4103
        private const val REQUEST_INSTALL = 4104
        private const val REQUEST_READY_LATER = 4105
    }
}

/** Handles update notification actions and DownloadManager completion. */
class UpdateNotificationReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ReserveApplication ?: return
        when (intent.action) {
            ACTION_LATER -> app.container.updateNotifier.cancel()
            ACTION_SNOOZE_UPDATE -> {
                val version = intent.getStringExtra(EXTRA_UPDATE_VERSION).orEmpty()
                if (version.isNotBlank()) {
                    app.container.scope.launch {
                        app.container.updateRepository.snoozeUpdate(version)
                    }
                } else {
                    app.container.updateNotifier.cancel()
                }
            }
            ACTION_DOWNLOAD_UPDATE -> {
                val version = intent.getStringExtra(EXTRA_UPDATE_VERSION).orEmpty()
                val apkUrl = intent.getStringExtra(EXTRA_APK_URL).orEmpty()
                if (version.isNotBlank() && apkUrl.isNotBlank()) {
                    app.container.scope.launch {
                        app.container.updateInstaller.enqueue(
                            UpdateInfo(version, "", apkUrl, "")
                        )
                    }
                }
            }
            android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                val id = intent.getLongExtra(
                    android.app.DownloadManager.EXTRA_DOWNLOAD_ID,
                    -1L
                )
                if (id >= 0) {
                    app.container.scope.launch {
                        app.container.updateInstaller.onDownloadCompleted(id)
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_LATER = "com.app.reserve.UPDATE_LATER"
        const val ACTION_SNOOZE_UPDATE = "com.app.reserve.UPDATE_SNOOZE"
        const val ACTION_DOWNLOAD_UPDATE = "com.app.reserve.UPDATE_DOWNLOAD"
        const val EXTRA_UPDATE_VERSION = "EXTRA_UPDATE_VERSION"
        const val EXTRA_APK_URL = "EXTRA_APK_URL"
    }
}
