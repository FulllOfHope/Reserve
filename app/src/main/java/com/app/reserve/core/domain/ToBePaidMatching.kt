package com.app.reserve.core.domain

import com.app.reserve.core.database.ToBePaidEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType

/**
 * A device-local prompt created when an imported SMS looks like a reminder the
 * user already wrote down. It is deliberately not part of backup JSON: a
 * restored ledger must never resurrect an old "was this it?" prompt.
 */
data class PendingToBePaidMatch(
    val reminderId: String,
    val reminderTitle: String,
    val transactionId: String,
    val transactionNote: String,
    val amountMinor: Long,
    val transactionTimestamp: Long,
    val detectedAt: Long = System.currentTimeMillis()
)

/** Conservative one-off matching: exact amount, same direction, nearby date. */
object ToBePaidMatching {

    const val MATCH_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun isCandidate(
        reminder: ToBePaidEntity,
        transaction: TransactionEntity,
        windowMillis: Long = MATCH_WINDOW_MILLIS
    ): Boolean {
        if (reminder.isSettled) return false
        if (reminder.amount <= 0L || reminder.amount != transaction.amount) return false
        if (reminder.type != transaction.type || reminder.type == TransactionType.TRANSFER) return false
        if (transaction.deletedAt != null) return false
        if (reminder.accountId.isNotBlank() && reminder.accountId != transaction.accountId) return false
        return kotlin.math.abs(reminder.dueAt - transaction.timestamp) <= windowMillis
    }
}
