package com.app.reserve.feature.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.glance.appwidget.updateAll
import com.app.reserve.core.application.ReserveApplication
import com.app.reserve.feature.widget.ReserveWidget
import kotlinx.coroutines.launch

/**
 * Captures new bank alerts. Parsing and balance updates happen on a background
 * coroutine through [SmsImporter], so the broadcast thread is never blocked.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages.firstOrNull()?.displayOriginatingAddress
        val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        if (body.isBlank()) return
        val timestamp = messages.maxOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()

        val application = context.applicationContext as? ReserveApplication ?: return
        val pending = goAsync()
        application.container.scope.launch {
            try {
                val imported = application.container.smsImporter.importLive(sender, body, timestamp)
                if (imported) {
                    runCatching { ReserveWidget().updateAll(context.applicationContext) }
                }
            } catch (_: Exception) {
                // A bank SMS is never worth crashing the receiver over.
            } finally {
                pending.finish()
            }
        }
    }
}
