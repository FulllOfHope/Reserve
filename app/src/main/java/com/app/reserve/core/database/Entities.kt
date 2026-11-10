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
    val createdAt: Long = System.currentTimeMillis()
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
    val createdAt: Long = System.currentTimeMillis()
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

class Converters {
    @TypeConverter
    fun toType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromType(value: TransactionType): String = value.name
}
