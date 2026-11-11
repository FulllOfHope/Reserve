package com.app.reserve.core.data

import androidx.room.withTransaction
import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.database.BudgetEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.RecurringEntity
import com.app.reserve.core.database.ReserveDatabase
import com.app.reserve.core.database.SmsImportEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.domain.Dates
import com.app.reserve.core.domain.LedgerMath
import com.app.reserve.core.domain.ParsedSms
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * All writes go through this repository so an account balance and its
 * transaction rows can never disagree. Money stays in Long minor units.
 */
class ReserveRepository(private val database: ReserveDatabase) {

    private val transactionDao get() = database.transactionDao()
    private val categoryDao get() = database.categoryDao()
    private val accountDao get() = database.accountDao()
    private val budgetDao get() = database.budgetDao()
    private val recurringDao get() = database.recurringDao()
    private val smsImportDao get() = database.smsImportDao()

    val transactions: Flow<List<TransactionEntity>> = transactionDao.observeAll()
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val accounts: Flow<List<AccountEntity>> = accountDao.observeAll()
    val budgets: Flow<List<BudgetEntity>> = budgetDao.observeAll()
    val recurringRules: Flow<List<RecurringEntity>> = recurringDao.observeAll()

    suspend fun ensureSeeded() {
        if (categoryDao.getAll().isEmpty()) seedCategories()
        if (accountDao.getAll().isEmpty()) seedAccounts()
    }

    private suspend fun seedCategories() {
        val expense = listOf(
            Triple("Food", "#5C947F", "food"),
            Triple("Transport", "#6B83A3", "transport"),
            Triple("Shopping", "#8C6B7C", "shopping"),
            Triple("Bills", "#A39261", "bills"),
            Triple("Health", "#798C6B", "health"),
            Triple("Fun", "#A36B6B", "fun"),
            Triple("Other", "#948B7A", "other")
        ).mapIndexed { index, (name, color, icon) ->
            CategoryEntity(
                id = "seed-expense-$index",
                name = name,
                colorHex = color,
                iconName = icon,
                type = TransactionType.EXPENSE
            )
        }
        val income = listOf(
            Triple("Salary", "#5C947F", "salary"),
            Triple("Gifts", "#6B83A3", "gift"),
            Triple("Other Income", "#948B7A", "other")
        ).mapIndexed { index, (name, color, icon) ->
            CategoryEntity(
                id = "seed-income-$index",
                name = name,
                colorHex = color,
                iconName = icon,
                type = TransactionType.INCOME
            )
        }
        categoryDao.insertAll(
            expense + income + listOf(
                CategoryEntity("seed-uncategorized-expense", "Uncategorized", "#948B7A", "other", TransactionType.EXPENSE),
                CategoryEntity("seed-uncategorized-income", "Uncategorized", "#948B7A", "other", TransactionType.INCOME)
            )
        )
    }

    private suspend fun seedAccounts() {
        accountDao.insertAll(
            listOf(
                AccountEntity("seed-cash", "Cash", 0L, createdAt = 1L),
                AccountEntity("seed-bank", "Bank", 0L, createdAt = 2L)
            )
        )
    }

    // ---------------------------------------------------------------------
    // Transactions
    // ---------------------------------------------------------------------

    /**
     * Inserts or replaces a transaction. When editing, the old row's effect on
     * balances is reversed before the new one is applied.
     */
    suspend fun saveTransaction(transaction: TransactionEntity, existing: TransactionEntity? = null) {
        database.withTransaction {
            if (existing != null) {
                applyBalance(existing, reverse = true)
                if (existing.id == transaction.id) {
                    transactionDao.delete(existing)
                } else {
                    transactionDao.delete(existing)
                }
            }
            applyBalance(transaction, reverse = false)
            transactionDao.insert(transaction)
        }
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        database.withTransaction {
            applyBalance(transaction, reverse = true)
            transactionDao.delete(transaction)
        }
    }

    private suspend fun applyBalance(transaction: TransactionEntity, reverse: Boolean) {
        val sign = if (reverse) -1L else 1L
        LedgerMath.deltas(
            type = transaction.type,
            amount = transaction.amount,
            accountId = transaction.accountId,
            targetAccountId = transaction.targetAccountId
        ).forEach { delta ->
            move(delta.accountId, delta.delta * sign)
        }
    }

    private suspend fun move(accountId: String, delta: Long) {
        if (delta == 0L) return
        val account = accountDao.getById(accountId) ?: return
        accountDao.setBalance(account.id, account.balance + delta)
    }

    // ---------------------------------------------------------------------
    // Categories
    // ---------------------------------------------------------------------

    suspend fun saveCategory(category: CategoryEntity) {
        categoryDao.insert(category)
    }

    /** Re-parents transactions and removes category budgets, then deletes. */
    suspend fun deleteCategory(category: CategoryEntity) {
        database.withTransaction {
            val fallback = uncategorizedFor(category.type, excluding = category.id)
            transactionDao.reparentCategory(category.id, fallback.id)
            budgetDao.deleteForCategory(category.id)
            categoryDao.delete(category)
        }
    }

    private suspend fun uncategorizedFor(type: TransactionType, excluding: String? = null): CategoryEntity {
        val existing = categoryDao.getAll().firstOrNull {
            it.type == type && it.name == "Uncategorized" && it.id != excluding
        }
        if (existing != null) return existing
        val created = CategoryEntity(
            id = UUID.randomUUID().toString(),
            name = "Uncategorized",
            colorHex = "#948B7A",
            iconName = "other",
            type = type
        )
        categoryDao.insert(created)
        return created
    }

    // ---------------------------------------------------------------------
    // Accounts
    // ---------------------------------------------------------------------

    suspend fun saveAccount(account: AccountEntity) {
        accountDao.insert(account)
    }

    /** Accounts with history are archived, never deleted, so old rows stay valid. */
    suspend fun deleteOrArchiveAccount(account: AccountEntity): Boolean {
        val used = transactionDao.getAll().any {
            it.accountId == account.id || it.targetAccountId == account.id
        }
        return if (used) {
            accountDao.update(account.copy(isArchived = true))
            true
        } else {
            accountDao.delete(account)
            false
        }
    }

    suspend fun restoreAccount(account: AccountEntity) {
        accountDao.update(account.copy(isArchived = false))
    }

    // ---------------------------------------------------------------------
    // Budgets
    // ---------------------------------------------------------------------

    suspend fun saveBudget(budget: BudgetEntity) {
        budgetDao.upsert(budget)
    }

    suspend fun deleteBudget(id: String) {
        budgetDao.deleteById(id)
    }

    // ---------------------------------------------------------------------
    // Recurring rules
    // ---------------------------------------------------------------------

    suspend fun saveRecurring(rule: RecurringEntity) {
        recurringDao.upsert(rule)
    }

    suspend fun deleteRecurring(id: String) {
        recurringDao.deleteById(id)
    }

    /**
     * Generates every monthly occurrence that is due and moves the rule to its
     * next date. Runs inside one transaction so a crash never half-posts a rule.
     */
    suspend fun generateDueRecurring(now: Long = System.currentTimeMillis()): Int {
        var generated = 0
        database.withTransaction {
            val due = recurringDao.getAll().filter { !it.paused && it.nextDueAt <= now }
            for (rule in due) {
                var next = rule.nextDueAt
                var guard = 0
                while (next <= now && guard < 36) {
                    val transaction = TransactionEntity(
                        id = UUID.randomUUID().toString(),
                        amount = rule.amount,
                        type = rule.type,
                        categoryId = rule.categoryId,
                        accountId = rule.accountId,
                        targetAccountId = rule.targetAccountId,
                        timestamp = next,
                        note = rule.note,
                        isRecurring = true
                    )
                    applyBalance(transaction, reverse = false)
                    transactionDao.insert(transaction)
                    generated++
                    next = nextOccurrence(next, rule.dayOfMonth)
                    guard++
                }
                if (next != rule.nextDueAt) recurringDao.updateNextDue(rule.id, next)
            }
        }
        return generated
    }

    private fun nextOccurrence(current: Long, dayOfMonth: Int): Long {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(current).atZone(zone).toLocalDate()
        val nextMonth = date.plusMonths(1)
        val day = dayOfMonth.coerceAtMost(nextMonth.lengthOfMonth())
        return Dates.toTimestamp(nextMonth.withDayOfMonth(day))
    }

    // ---------------------------------------------------------------------
    // Backup / restore / import helpers
    // ---------------------------------------------------------------------

    suspend fun snapshot(): LedgerSnapshot = LedgerSnapshot(
        accounts = accountDao.getAll(),
        categories = categoryDao.getAll(),
        transactions = transactionDao.getAll(),
        budgets = budgetDao.getAll(),
        recurring = recurringDao.getAll()
    )

    suspend fun replaceAll(snapshot: LedgerSnapshot) {
        database.withTransaction {
            transactionDao.clear()
            budgetDao.clear()
            recurringDao.clear()
            categoryDao.clear()
            accountDao.clear()
            categoryDao.insertAll(snapshot.categories)
            accountDao.insertAll(snapshot.accounts)
            transactionDao.insertAll(snapshot.transactions)
            snapshot.budgets.forEach { budgetDao.upsert(it) }
            snapshot.recurring.forEach { recurringDao.upsert(it) }
        }
    }

    suspend fun importTransactions(
        rows: List<ImportedTransaction>,
        defaultAccountId: String
    ): ImportSummary {
        var imported = 0
        var skipped = 0
        database.withTransaction {
            for (row in rows) {
                try {
                    val account = findOrCreateAccount(row.accountName, defaultAccountId)
                    val category = if (row.type == TransactionType.TRANSFER) {
                        null
                    } else {
                        findOrCreateCategory(row.categoryName, row.type)
                    }
                    val target = if (row.type == TransactionType.TRANSFER) {
                        findOrCreateAccount(row.targetAccountName.ifBlank { row.accountName }, defaultAccountId)
                    } else {
                        null
                    }
                    val transaction = TransactionEntity(
                        id = UUID.randomUUID().toString(),
                        amount = row.amountMinor,
                        type = row.type,
                        categoryId = category?.id,
                        accountId = account.id,
                        targetAccountId = target?.id,
                        timestamp = row.timestamp,
                        note = row.note
                    )
                    applyBalance(transaction, reverse = false)
                    transactionDao.insert(transaction)
                    imported++
                } catch (_: Exception) {
                    skipped++
                }
            }
        }
        return ImportSummary(imported, skipped)
    }

    /**
     * Imports parsed SMS rows in one transaction. A fingerprint is written
     * first, so a message can never create two transactions even if the live
     * receiver and the inbox backfill run at the same time.
     */
    suspend fun importSmsMessages(messages: List<ParsedSms>): SmsImportResult {
        if (messages.isEmpty()) return SmsImportResult(0, 0)
        var imported = 0
        var duplicates = 0
        database.withTransaction {
            val accountsByName = accountDao.getAll()
                .associateByTo(mutableMapOf()) { it.name.lowercase() }
            val categoriesByKey = categoryDao.getAll()
                .associateByTo(mutableMapOf()) { "${it.type}|${it.name.lowercase()}" }

            for (message in messages) {
                val transactionId = UUID.randomUUID().toString()
                val inserted = smsImportDao.insert(
                    SmsImportEntity(
                        fingerprint = message.fingerprint,
                        transactionId = transactionId,
                        importedAt = System.currentTimeMillis()
                    )
                )
                if (inserted == -1L) {
                    duplicates++
                    continue
                }
                try {
                    val accountKey = message.accountName.lowercase()
                    val account = accountsByName[accountKey] ?: AccountEntity(
                        UUID.randomUUID().toString(),
                        message.accountName,
                        0L
                    ).also {
                        accountDao.insert(it)
                        accountsByName[accountKey] = it
                    }

                    val category = message.categoryName?.let { name ->
                        val key = "${message.type}|${name.lowercase()}"
                        categoriesByKey[key] ?: CategoryEntity(
                            UUID.randomUUID().toString(),
                            name,
                            "#948B7A",
                            "other",
                            message.type
                        ).also {
                            categoryDao.insert(it)
                            categoriesByKey[key] = it
                        }
                    }

                    val transaction = TransactionEntity(
                        id = transactionId,
                        amount = message.amountMinor,
                        type = message.type,
                        categoryId = category?.id,
                        accountId = account.id,
                        timestamp = message.timestamp,
                        note = message.note
                    )
                    applyBalance(transaction, reverse = false)
                    transactionDao.insert(transaction)
                    imported++
                } catch (_: Exception) {
                    // Let a later scan retry this message instead of losing it.
                    smsImportDao.deleteByFingerprint(message.fingerprint)
                }
            }
        }
        return SmsImportResult(imported, duplicates)
    }

    private suspend fun findOrCreateAccount(name: String, defaultAccountId: String): AccountEntity {
        val wanted = name.trim().ifBlank { "Imported" }
        accountDao.getAll().firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.let { return it }
        val created = AccountEntity(UUID.randomUUID().toString(), wanted, 0L)
        accountDao.insert(created)
        return created
    }

    private suspend fun findOrCreateCategory(name: String, type: TransactionType): CategoryEntity {
        val wanted = name.trim().ifBlank { "Uncategorized" }
        categoryDao.getAll().firstOrNull {
            it.type == type && it.name.equals(wanted, ignoreCase = true)
        }?.let { return it }
        val created = CategoryEntity(
            UUID.randomUUID().toString(),
            wanted,
            "#948B7A",
            "other",
            type
        )
        categoryDao.insert(created)
        return created
    }

    suspend fun monthTransactions(monthYear: String): List<TransactionEntity> =
        transactionDao.getAll().filter { Dates.monthKey(Dates.toLocalDate(it.timestamp)) == monthYear }

    /** Used by tests and CSV export. */
    suspend fun allTransactions(): List<TransactionEntity> = transactionDao.getAll()

    suspend fun allAccounts(): List<AccountEntity> = accountDao.getAll()

    suspend fun allCategories(): List<CategoryEntity> = categoryDao.getAll()
}

data class LedgerSnapshot(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val recurring: List<RecurringEntity>
)

data class ImportedTransaction(
    val timestamp: Long,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryName: String,
    val accountName: String,
    val targetAccountName: String,
    val note: String
)

data class ImportSummary(val imported: Int, val skipped: Int)

data class SmsImportResult(val imported: Int, val duplicates: Int)
