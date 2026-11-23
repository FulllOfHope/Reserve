package com.app.reserve.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

/**
 * A single movement of money. [amount] is always a positive value in minor
 * units; [type] decides the direction.
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val amount: Long,
    val type: TransactionType,
    val categoryId: String?,
    val accountId: String,
    val targetAccountId: String? = null,
    val timestamp: Long,
    val note: String = "",
    val isRecurring: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Non-null when the row is in Trash; null for active ledger rows. */
    val deletedAt: Long? = null
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val iconName: String,
    val type: TransactionType
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val balance: Long,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Opening capital before any recorded transaction; keeps balance derivable. */
    val openingBalanceMinor: Long = 0L
)

/**
 * Remembers which SMS have already produced a ledger row. The fingerprint is a
 * SHA-256 of normalized sender + body + a one-minute timestamp bucket plus the
 * parsed outcome, so the receiver and the inbox backfill can overlap without
 * ever importing the same message twice.
 */
@Entity(tableName = "sms_imports")
data class SmsImportEntity(
    @PrimaryKey val fingerprint: String,
    val transactionId: String,
    val importedAt: Long,
    /** Canonical SMS date used for ordering and reconciliation. */
    val messageDate: Long = 0L,
    /** Running balance from the SMS, when the bank printed one. */
    val balanceAfterMinor: Long? = null,
    /** Account the SMS was posted to. */
    val accountId: String = ""
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,
    /** Null means a budget for all expenses in the month. */
    val categoryId: String?,
    val amount: Long,
    /** "YYYY-MM" */
    val monthYear: String
)

/**
 * A monthly rule. Generated transactions are normal rows, so deleting or
 * editing one never touches the rule.
 */
@Entity(tableName = "recurring_rules")
data class RecurringEntity(
    @PrimaryKey val id: String,
    val amount: Long,
    val type: TransactionType,
    val categoryId: String?,
    val accountId: String,
    val targetAccountId: String? = null,
    val note: String = "",
    val dayOfMonth: Int,
    /** Epoch millis of the next occurrence at local noon. */
    val nextDueAt: Long,
    val paused: Boolean = false
)

/**
 * A one-off future movement the user wants to remember. It never touches an
 * account balance until it is settled; settling either links an existing SMS
 * row or creates exactly one real transaction.
 */
@Entity(tableName = "to_be_paid")
data class ToBePaidEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** Always positive, in minor units. */
    val amount: Long,
    val type: TransactionType,
    val categoryId: String?,
    val accountId: String,
    val dueAt: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Non-null once the reminder has been settled by a real ledger row. */
    val settledTransactionId: String? = null,
    val settledAt: Long? = null,
    /** MANUAL or SMS_MATCH. Kept as text so old backups stay forgiving. */
    val settlementSource: String? = null
) {
    val isSettled: Boolean get() = settledTransactionId != null
}

class Converters {
    @TypeConverter
    fun toType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromType(value: TransactionType): String = value.name
}
