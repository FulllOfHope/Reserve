package com.app.reserve.core.domain

import com.app.reserve.core.database.TransactionType

/** A signed change to a single account, in minor units. */
data class AccountDelta(val accountId: String, val delta: Long)

/**
 * Pure balance maths for a transaction. Kept outside the repository so the
 * "transfers net to zero" rule can be tested without a database.
 */
object LedgerMath {

    fun deltas(
        type: TransactionType,
        amount: Long,
        accountId: String,
        targetAccountId: String?
    ): List<AccountDelta> = when (type) {
        TransactionType.INCOME -> listOf(AccountDelta(accountId, amount))
        TransactionType.EXPENSE -> listOf(AccountDelta(accountId, -amount))
        TransactionType.TRANSFER -> {
            if (targetAccountId == null || targetAccountId == accountId) {
                // Moving money to the same account is a no-op, never a loss.
                emptyList()
            } else {
                listOf(
                    AccountDelta(accountId, -amount),
                    AccountDelta(targetAccountId, amount)
                )
            }
        }
    }

    fun apply(
        balances: Map<String, Long>,
        type: TransactionType,
        amount: Long,
        accountId: String,
        targetAccountId: String?
    ): Map<String, Long> {
        val updated = balances.toMutableMap()
        for (delta in deltas(type, amount, accountId, targetAccountId)) {
            updated[delta.accountId] = (updated[delta.accountId] ?: 0L) + delta.delta
        }
        return updated
    }
}
