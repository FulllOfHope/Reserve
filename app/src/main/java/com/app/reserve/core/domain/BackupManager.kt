package com.app.reserve.core.domain

import com.app.reserve.core.data.LedgerSnapshot
import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.database.BudgetEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.RecurringEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A complete, self-contained local backup. Stored as JSON on the device. */
object BackupManager {

    /** v2 adds `deletedAt` tombstones; v1 backups remain importable (field defaults to null). */
    const val CURRENT_VERSION = 2

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: LedgerSnapshot, now: Long = System.currentTimeMillis()): String {
        val dto = BackupDto(
            version = CURRENT_VERSION,
            exportedAt = now,
            accounts = snapshot.accounts.map { it.toDto() },
            categories = snapshot.categories.map { it.toDto() },
            transactions = snapshot.transactions.map { it.toDto() },
            budgets = snapshot.budgets.map { it.toDto() },
            recurring = snapshot.recurring.map { it.toDto() }
        )
        return json.encodeToString(dto)
    }

    fun decode(text: String): Result<LedgerSnapshot> = runCatching {
        val dto = json.decodeFromString<BackupDto>(text)
        require(dto.version <= CURRENT_VERSION) { "This backup was created by a newer version of Reserve." }
        LedgerSnapshot(
            accounts = dto.accounts.map { it.toEntity() },
            categories = dto.categories.map { it.toEntity() },
            transactions = dto.transactions.map { it.toEntity() },
            budgets = dto.budgets.map { it.toEntity() },
            recurring = dto.recurring.map { it.toEntity() }
        )
    }
}

@Serializable
data class BackupDto(
    val version: Int = BackupManager.CURRENT_VERSION,
    val exportedAt: Long = 0L,
    val accounts: List<BackupAccount> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val transactions: List<BackupTransaction> = emptyList(),
    val budgets: List<BackupBudget> = emptyList(),
    val recurring: List<BackupRecurring> = emptyList()
)

@Serializable
data class BackupAccount(
    val id: String,
    val name: String,
    val balance: Long,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L
) {
    fun toEntity() = AccountEntity(id, name, balance, isArchived, createdAt)
}

fun AccountEntity.toDto() = BackupAccount(id, name, balance, isArchived, createdAt)

@Serializable
data class BackupCategory(
    val id: String,
    val name: String,
    val colorHex: String,
    val iconName: String,
    val type: String
) {
    fun toEntity() = CategoryEntity(id, name, colorHex, iconName, TransactionType.valueOf(type))
}

fun CategoryEntity.toDto() = BackupCategory(id, name, colorHex, iconName, type.name)

@Serializable
data class BackupTransaction(
    val id: String,
    val amount: Long,
    val type: String,
    val categoryId: String? = null,
    val accountId: String,
    val targetAccountId: String? = null,
    val timestamp: Long,
    val note: String = "",
    val isRecurring: Boolean = false,
    val createdAt: Long = 0L,
    /** Null for active rows; set for rows in Trash. Absent in legacy v1 backups. */
    val deletedAt: Long? = null
) {
    fun toEntity() = TransactionEntity(
        id = id,
        amount = amount,
        type = TransactionType.valueOf(type),
        categoryId = categoryId,
        accountId = accountId,
        targetAccountId = targetAccountId,
        timestamp = timestamp,
        note = note,
        isRecurring = isRecurring,
        createdAt = createdAt,
        deletedAt = deletedAt
    )
}

fun TransactionEntity.toDto() = BackupTransaction(
    id, amount, type.name, categoryId, accountId, targetAccountId, timestamp, note, isRecurring, createdAt,
    deletedAt
)

@Serializable
data class BackupBudget(
    val id: String,
    val categoryId: String? = null,
    val amount: Long,
    val monthYear: String
) {
    fun toEntity() = BudgetEntity(id, categoryId, amount, monthYear)
}

fun BudgetEntity.toDto() = BackupBudget(id, categoryId, amount, monthYear)

@Serializable
data class BackupRecurring(
    val id: String,
    val amount: Long,
    val type: String,
    val categoryId: String? = null,
    val accountId: String,
    val targetAccountId: String? = null,
    val note: String = "",
    val dayOfMonth: Int,
    val nextDueAt: Long,
    val paused: Boolean = false
) {
    fun toEntity() = RecurringEntity(
        id, amount, TransactionType.valueOf(type), categoryId, accountId,
        targetAccountId, note, dayOfMonth, nextDueAt, paused
    )
}

fun RecurringEntity.toDto() = BackupRecurring(
    id, amount, type.name, categoryId, accountId, targetAccountId, note, dayOfMonth, nextDueAt, paused
)
