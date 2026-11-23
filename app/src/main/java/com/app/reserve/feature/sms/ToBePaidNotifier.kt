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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Quiet heads-up when an imported SMS could be a planned one-off movement.
 * The persistent answer lives in DataStore, so the popup survives a tap that
 * only brings Reserve to the foreground.
 *
 * Nothing here may throw during `Application.onCreate`: a manufacturer's
 * NotificationManager can reject channel creation, and that must never take
 * the whole app down before the first screen is drawn.
 */
class ToBePaidNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val manager: NotificationManager? = runCatching {
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }.getOrNull()
    private val channelCreated = AtomicBoolean(false)

    private fun ensureChannel() {
        val current = manager ?: return
        if (channelCreated.get()) return
        runCatching {
            // minSdk is 26, so notification channels are always available.
            current.createNotificationChannel(
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
            channelCreated.set(true)
        }
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            runCatching {
                ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)

    fun show(match: PendingToBePaidMatch) {
        if (!hasPermission()) return
        val current = manager ?: return
        runCatching {
            ensureChannel()
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
            current.notify(notificationId(match), notification)
        }
    }

    fun cancel(match: PendingToBePaidMatch) {
        runCatching { manager?.cancel(notificationId(match)) }
    }

    private fun notificationId(match: PendingToBePaidMatch): Int =
        match.reminderId.hashCode()

    private companion object {
        const val CHANNEL_ID = "to_be_paid_matches"
    }
}
