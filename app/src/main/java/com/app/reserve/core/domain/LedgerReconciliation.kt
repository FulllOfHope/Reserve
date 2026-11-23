package com.app.reserve.core.domain

import com.app.reserve.core.database.TransactionType

/**
 * Pure helpers for SMS-driven ledger corrections. They live outside the
 * repository so the balance/dedupe rules can be verified in JVM unit tests.
 */
object LedgerReconciliation {

    /** Transactions this close together, on the same account, are one SMS. */
    const val DUPLICATE_WINDOW_MILLIS = 60_000L

    const val RECONCILIATION_NOTE = "Balance reconciliation · SMS"

    /** Differences of ₹1 or less are noise; anything above needs user verification. */
    const val BALANCE_DISCREPANCY_THRESHOLD_MINOR = 100L

    const val BALANCE_VERIFICATION_NOTE = "Balance verified · SMS"

    /**
     * A bank-reported balance that disagrees with the ledger. This is a prompt
     * for the user to verify, never permission to post a silent correction.
     */
    data class PendingBalanceDiscrepancy(
        val accountId: String,
        val accountName: String,
        val reserveBalanceMinor: Long,
        val smsBalanceMinor: Long,
        val differenceMinor: Long,
        val timestamp: Long
    )

    data class DuplicateKey(
        val accountId: String,
        val amount: Long,
        val type: TransactionType,
        val note: String,
        val timestamp: Long
    )

    data class Adjustment(val type: TransactionType, val amountMinor: Long)

    /** Signed effect of a movement on its source account. */
    fun deltaFor(
        type: TransactionType,
        amount: Long,
        accountId: String,
        targetAccountId: String? = null
    ): Long = LedgerMath.deltas(type, amount, accountId, targetAccountId)
        .firstOrNull { it.accountId == accountId }?.delta ?: 0L

    /**
     * For a brand-new account: the balance the account must start with so that
     * posting this SMS leaves exactly the balance printed by the bank.
     */
    fun openingForBalance(
        balanceAfterMinor: Long,
        type: TransactionType,
        amount: Long,
        accountId: String,
        targetAccountId: String? = null
    ): Long = balanceAfterMinor - deltaFor(type, amount, accountId, targetAccountId)

    /**
     * Opening balance for a newly discovered account after a whole bounded scan:
     * opening + every imported delta must equal the newest SMS balance.
     */
    fun anchoredOpening(latestSmsBalanceMinor: Long, sumOfDeltas: Long): Long =
        latestSmsBalanceMinor - sumOfDeltas

    /** Difference between the ledger balance and the bank's running balance. */
    fun adjustmentFor(currentBalance: Long, balanceAfterMinor: Long?): Adjustment? {
        if (balanceAfterMinor == null) return null
        val difference = balanceAfterMinor - currentBalance
        return when {
            difference > 0L -> Adjustment(TransactionType.INCOME, difference)
            difference < 0L -> Adjustment(TransactionType.EXPENSE, -difference)
            else -> null
        }
    }

    /**
     * Builds a verification prompt only for meaningful disagreements. A small
     * difference is ignored so rounding noise never nags the user.
     */
    fun discrepancyFor(
        accountId: String,
        accountName: String,
        reserveBalanceMinor: Long,
        smsBalanceMinor: Long,
        timestamp: Long
    ): PendingBalanceDiscrepancy? {
        val difference = smsBalanceMinor - reserveBalanceMinor
        if (kotlin.math.abs(difference) <= BALANCE_DISCREPANCY_THRESHOLD_MINOR) return null
        return PendingBalanceDiscrepancy(
            accountId = accountId,
            accountName = accountName,
            reserveBalanceMinor = reserveBalanceMinor,
            smsBalanceMinor = smsBalanceMinor,
            differenceMinor = difference,
            timestamp = timestamp
        )
    }

    /** True when two rows describe the same SMS movement within one minute. */
    fun isDuplicate(first: DuplicateKey, second: DuplicateKey): Boolean =
        first.accountId == second.accountId &&
            first.amount == second.amount &&
            first.type == second.type &&
            first.note == second.note &&
            kotlin.math.abs(first.timestamp - second.timestamp) <= DUPLICATE_WINDOW_MILLIS

    /** Exact duplicate SMS rows as produced by two ingest paths. */
    fun isExactSmsDuplicate(first: DuplicateKey, second: DuplicateKey): Boolean =
        first.note.startsWith("SMS ·") && isDuplicate(first, second)
}
