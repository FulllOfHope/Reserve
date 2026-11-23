package com.app.reserve.feature.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.app.reserve.MainActivity
import com.app.reserve.R
import com.app.reserve.core.domain.Money
import com.app.reserve.core.domain.PendingToBePaidMatch

/**
 * Quiet heads-up when an imported SMS could be a planned one-off movement.
 * The persistent answer lives in DataStore, so the popup survives a tap that
 * only brings Reserve to the foreground.
 */
class ToBePaidNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        // minSdk is 26, so notification channels are always available.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Planned payments",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "An SMS may match a To Be Paid reminder"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun show(match: PendingToBePaidMatch) {
        if (!hasPermission()) return
        val open = PendingIntent.getActivity(
            appContext,
            match.reminderId.hashCode(),
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reserve)
            .setContentTitle("Was this your planned payment?")
            .setContentText(
                "${Money.format(match.amountMinor)} matches “${match.reminderTitle}”. Tap to confirm."
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        runCatching { manager.notify(notificationId(match), notification) }
    }

    fun cancel(match: PendingToBePaidMatch) {
        runCatching { manager.cancel(notificationId(match)) }
    }

    private fun notificationId(match: PendingToBePaidMatch): Int =
        match.reminderId.hashCode()

    private companion object {
        const val CHANNEL_ID = "to_be_paid_matches"
    }
}
