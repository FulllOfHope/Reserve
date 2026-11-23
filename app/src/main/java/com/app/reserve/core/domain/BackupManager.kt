package com.app.reserve.core.domain

import com.app.reserve.core.data.LedgerSnapshot
import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.datastore.BackupSettings
import com.app.reserve.core.datastore.ReserveSettings
import com.app.reserve.core.datastore.toBackupSettings
import com.app.reserve.core.database.BudgetEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.RecurringEntity
import com.app.reserve.core.database.ToBePaidEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A complete, self-contained local backup. Stored as JSON on the device. */
object BackupManager {

    /**
     * v2 added `deletedAt` tombstones, v3 added DataStore settings and the
     * account opening balance. Later additive fields (planned reminders, the
     * Safe-to-Spend preference) stay on v3 so older backups remain importable
     * purely through serializer defaults.
     */
    const val CURRENT_VERSION = 3

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Ledger-only encoding, kept for backwards compatibility and existing tests. */
    fun encode(snapshot: LedgerSnapshot, now: Long = System.currentTimeMillis()): String =
        encode(snapshot, null, now)

    /** Full backup: every ledger row plus every DataStore preference. */
    fun encode(
        snapshot: LedgerSnapshot,
        settings: ReserveSettings?,
        now: Long = System.currentTimeMillis()
    ): String {
        val dto = BackupDto(
            version = CURRENT_VERSION,
            exportedAt = now,
            accounts = snapshot.accounts.map { it.toDto() },
            categories = snapshot.categories.map { it.toDto() },
            transactions = snapshot.transactions.map { it.toDto() },
            budgets = snapshot.budgets.map { it.toDto() },
            recurring = snapshot.recurring.map { it.toDto() },
            toBePaid = snapshot.toBePaid.map { it.toDto() },
            settings = settings?.toBackupSettings()
        )
        return json.encodeToString(dto)
    }

    fun decode(text: String): Result<LedgerSnapshot> = runCatching {
        decodeBundle(text).getOrThrow().snapshot
    }

    /**
     * Lightweight inspection for the restore confirmation. Counts come straight
     * from the DTO, so even a very old backup can be previewed without mapping
     * every row into entities.
     */
    fun preview(text: String, sizeBytes: Long = 0L): Result<BackupPreview> = runCatching {
        val dto = json.decodeFromString<BackupDto>(text)
        require(dto.version <= CURRENT_VERSION) { "This backup was created by a newer version of Reserve." }
        BackupPreview(
            exportedAt = dto.exportedAt,
            transactionCount = dto.transactions.size,
            accountCount = dto.accounts.size,
            sizeBytes = sizeBytes.coerceAtLeast(0L)
        )
    }

    /** Decodes both the ledger and the settings (null for legacy backups). */
    fun decodeBundle(text: String): Result<BackupBundle> = runCatching {
        val dto = json.decodeFromString<BackupDto>(text)
        require(dto.version <= CURRENT_VERSION) { "This backup was created by a newer version of Reserve." }
        BackupBundle(
            snapshot = LedgerSnapshot(
                accounts = dto.accounts.map { it.toEntity() },
                categories = dto.categories.map { it.toEntity() },
                transactions = dto.transactions.map { it.toEntity() },
                budgets = dto.budgets.map { it.toEntity() },
                recurring = dto.recurring.map { it.toEntity() },
                toBePaid = dto.toBePaid.map { it.toEntity() }
            ),
            settings = dto.settings,
            exportedAt = dto.exportedAt
        )
    }
}

/** What the user sees before a restore is allowed to replace the ledger. */
data class BackupPreview(
    val exportedAt: Long,
    val transactionCount: Int,
    val accountCount: Int,
    val sizeBytes: Long
)

/** The complete decoded content of a backup file. */
data class BackupBundle(
    val snapshot: LedgerSnapshot,
    val settings: BackupSettings?,
    val exportedAt: Long
)

@Serializable
data class BackupDto(
    val version: Int = BackupManager.CURRENT_VERSION,
    val exportedAt: Long = 0L,
    val accounts: List<BackupAccount> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val transactions: List<BackupTransaction> = emptyList(),
    val budgets: List<BackupBudget> = emptyList(),
    val recurring: List<BackupRecurring> = emptyList(),
    /** Additive in v3; absent in older backups and in older v3 files. */
    val toBePaid: List<BackupToBePaid> = emptyList(),
    /** Absent in v1/v2 backups. */
    val settings: BackupSettings? = null
)

@Serializable
data class BackupAccount(
    val id: String,
    val name: String,
    val balance: Long,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
    /** Absent in legacy backups, where opening capital was not preserved. */
    val openingBalanceMinor: Long = 0L
) {
    fun toEntity() = AccountEntity(id, name, balance, isArchived, createdAt, openingBalanceMinor)
}

fun AccountEntity.toDto() = BackupAccount(id, name, balance, isArchived, createdAt, openingBalanceMinor)

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

@Serializable
data class BackupToBePaid(
    val id: String,
    val title: String,
    val amount: Long,
    val type: String,
    val categoryId: String? = null,
    val accountId: String,
    val dueAt: Long,
    val note: String = "",
    val createdAt: Long = 0L,
    val settledTransactionId: String? = null,
    val settledAt: Long? = null,
    val settlementSource: String? = null
) {
    fun toEntity() = ToBePaidEntity(
        id = id,
        title = title,
        amount = amount,
        type = runCatching { TransactionType.valueOf(type) }
            .getOrDefault(TransactionType.EXPENSE),
        categoryId = categoryId,
        accountId = accountId,
        dueAt = dueAt,
        note = note,
        createdAt = createdAt,
        settledTransactionId = settledTransactionId,
        settledAt = settledAt,
        settlementSource = settlementSource
    )
}

fun ToBePaidEntity.toDto() = BackupToBePaid(
    id = id,
    title = title,
    amount = amount,
    type = type.name,
    categoryId = categoryId,
    accountId = accountId,
    dueAt = dueAt,
    note = note,
    createdAt = createdAt,
    settledTransactionId = settledTransactionId,
    settledAt = settledAt,
    settlementSource = settlementSource
)
