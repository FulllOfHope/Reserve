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
import com.app.reserve.core.domain.LedgerReconciliation
import com.app.reserve.core.domain.ParsedSms
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Items in Trash are purged permanently after this long (30 days). */
const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

/**
 * All writes go through this repository so an account balance and its
 * transaction rows can never disagree. Money stays in Long minor units.
 */
class ReserveRepository(private val database: ReserveDatabase) {

    /** Serialises delete/restore so a fast Undo can never race the soft-delete. */
    private val trashMutex = Mutex()

    private val transactionDao get() = database.transactionDao()
    private val categoryDao get() = database.categoryDao()
    private val accountDao get() = database.accountDao()
    private val budgetDao get() = database.budgetDao()
    private val recurringDao get() = database.recurringDao()
    private val smsImportDao get() = database.smsImportDao()

    val transactions: Flow<List<TransactionEntity>> = transactionDao.observeAll()
    val trashedTransactions: Flow<List<TransactionEntity>> = transactionDao.observeTrashed()
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

    // ---------------------------------------------------------------------
    // Trash. Balance effects are applied once, on the deletedAt transition:
    // soft-delete reverses, restore re-applies, permanent delete does neither.
    // ---------------------------------------------------------------------

    /** Moves an active row to Trash and reverses its balance effect. */
    suspend fun softDeleteTransaction(id: String, now: Long = System.currentTimeMillis()): Boolean =
        trashMutex.withLock {
            var changed = false
            database.withTransaction {
                val row = transactionDao.getByIdIncludingDeleted(id) ?: return@withTransaction
                if (row.deletedAt != null) return@withTransaction
                applyBalance(row, reverse = true)
                transactionDao.softDeleteTransaction(id, now)
                changed = true
            }
            changed
        }

    /** Restores a trashed row to its original timestamp/position and balance. */
    suspend fun restoreTransaction(id: String): Boolean = trashMutex.withLock {
        var changed = false
        database.withTransaction {
            val row = transactionDao.getByIdIncludingDeleted(id) ?: return@withTransaction
            if (row.deletedAt == null) return@withTransaction
            applyBalance(row, reverse = false)
            transactionDao.restoreTransaction(id)
            changed = true
        }
        changed
    }

    /** Removes a row forever. Safe even if it was somehow still active. */
    suspend fun permanentlyDeleteTransaction(id: String) = trashMutex.withLock {
        database.withTransaction {
            val row = transactionDao.getByIdIncludingDeleted(id) ?: return@withTransaction
            if (row.deletedAt == null) applyBalance(row, reverse = true)
            transactionDao.permanentlyDeleteTransaction(id)
        }
    }

    /** Re-categorises a transaction without touching its balance or tombstone. */
    suspend fun updateTransactionCategory(id: String, categoryId: String?) {
        val row = transactionDao.getByIdIncludingDeleted(id) ?: return
        transactionDao.update(row.copy(categoryId = categoryId))
    }

    suspend fun emptyTrash() = trashMutex.withLock {
        database.withTransaction { transactionDao.emptyTrash() }
    }

    /** Purges tombstones older than [cutoff]. Returns how many rows were removed. */
    suspend fun purgeExpiredTrash(cutoff: Long): Int = trashMutex.withLock {
        val before = transactionDao.getTrashedTransactions().count { it.deletedAt != null && it.deletedAt!! < cutoff }
        if (before > 0) transactionDao.purgeExpiredTrash(cutoff)
        before
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
        // Include Trash: restoring a trashed row must still find its account.
        val used = transactionDao.getAllIncludingDeleted().any {
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
        transactions = transactionDao.getAllIncludingDeleted(),
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
     * Imports parsed SMS rows oldest-first. Each message is wrapped in its own
     * nested transaction: a failure rolls back only that message (including its
     * balance change), while successful rows and fingerprints stay committed.
     */
    suspend fun importSmsMessages(messages: List<ParsedSms>): SmsImportResult {
        if (messages.isEmpty()) return SmsImportResult(0, 0)
        var imported = 0
        var duplicates = 0
        val ordered = messages.sortedBy { it.timestamp }
        database.withTransaction {
            val accountsByName = accountDao.getAll()
                .associateByTo(mutableMapOf()) { it.name.lowercase(Locale.ROOT) }
            val categoriesByKey = categoryDao.getAll()
                .associateByTo(mutableMapOf()) { "${it.type}|${it.name.lowercase(Locale.ROOT)}" }

            for (message in ordered) {
                val accountKey = message.accountName.lowercase(Locale.ROOT)
                val wasImported = runCatching {
                    database.withTransaction {
                        // Fingerprint check first: this also covers a backfill
                        // racing the live receiver inside the same DB lock.
                        if (smsImportDao.getByFingerprint(message.fingerprint) != null) {
                            return@withTransaction false
                        }

                        var account = accountsByName[accountKey]
                        val accountCreatedNow = account == null
                        if (account == null) {
                            val id = UUID.randomUUID().toString()
                            val opening = message.balanceAfterMinor?.let { balance ->
                                LedgerReconciliation.openingForBalance(
                                    balanceAfterMinor = balance,
                                    type = message.type,
                                    amount = message.amountMinor,
                                    accountId = id
                                )
                            } ?: 0L
                            account = AccountEntity(
                                id = id,
                                name = message.accountName,
                                balance = opening,
                                openingBalanceMinor = opening
                            )
                            accountDao.insert(account!!)
                            accountsByName[accountKey] = account!!
                        }

                        // Transaction-level guard: the same movement may arrive
                        // with two fingerprints if a multipart body differs.
                        val duplicate = transactionDao.findDuplicateSmsTransaction(
                            accountId = account!!.id,
                            amount = message.amountMinor,
                            type = message.type,
                            note = message.note,
                            from = message.timestamp - LedgerReconciliation.DUPLICATE_WINDOW_MILLIS,
                            to = message.timestamp + LedgerReconciliation.DUPLICATE_WINDOW_MILLIS
                        )
                        if (duplicate != null) {
                            recordSmsImport(message, account!!.id, duplicate.id)
                            return@withTransaction false
                        }

                        val category = message.categoryName?.let { name ->
                            val key = "${message.type}|${name.lowercase(Locale.ROOT)}"
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

                        val transactionId = UUID.randomUUID().toString()
                        val transaction = TransactionEntity(
                            id = transactionId,
                            amount = message.amountMinor,
                            type = message.type,
                            categoryId = category?.id,
                            accountId = account!!.id,
                            timestamp = message.timestamp,
                            note = message.note
                        )
                        applyBalance(transaction, reverse = false)
                        transactionDao.insert(transaction)

                        if (message.balanceAfterMinor != null) {
                            val newestForAccount = ordered
                                .filter { it.accountName.equals(account!!.name, ignoreCase = true) }
                                .maxOf { it.timestamp }
                            val newestKnown = maxOf(
                                newestForAccount,
                                smsImportDao.latestMessageDate(account!!.id) ?: 0L
                            )
                            // Out-of-order/old SMS must never rewrite newer state.
                            if (message.timestamp >= newestKnown) {
                                reconcileAccountToSms(account!!.id, message)
                            }
                        }
                        recordSmsImport(message, account!!.id, transactionId)
                        if (accountCreatedNow) {
                            accountsByName[accountKey] = accountDao.getById(account!!.id) ?: account!!
                        }
                        true
                    }
                }
                wasImported.onSuccess { importedNow ->
                    if (importedNow) imported++ else duplicates++
                }
            }
        }
        return SmsImportResult(imported, duplicates)
    }

    private suspend fun recordSmsImport(message: ParsedSms, accountId: String, transactionId: String) {
        smsImportDao.insert(
            SmsImportEntity(
                fingerprint = message.fingerprint,
                transactionId = transactionId,
                importedAt = System.currentTimeMillis(),
                messageDate = message.timestamp,
                balanceAfterMinor = message.balanceAfterMinor,
                accountId = accountId
            )
        )
    }

    /** Posts a clearly labeled adjustment so the ledger matches the bank SMS. */
    private suspend fun reconcileAccountToSms(accountId: String, message: ParsedSms) {
        val balance = accountDao.getById(accountId)?.balance ?: return
        val adjustment = LedgerReconciliation.adjustmentFor(balance, message.balanceAfterMinor) ?: return
        val reconciliation = TransactionEntity(
            id = UUID.randomUUID().toString(),
            amount = adjustment.amountMinor,
            type = adjustment.type,
            categoryId = null,
            accountId = accountId,
            timestamp = message.timestamp,
            note = LedgerReconciliation.RECONCILIATION_NOTE
        )
        applyBalance(reconciliation, reverse = false)
        transactionDao.insert(reconciliation)
    }

    /**
     * One-time v3 repair: removes exact duplicate SMS rows, restores the
     * opening-balance invariant, and reconciles accounts to the newest SMS that
     * carried a balance. Returns false if anything failed so the flag is not set.
     */
    suspend fun repairLedgerV3(): Boolean = runCatching {
        database.withTransaction {
            val smsRows = transactionDao.smsTransactionsForRepair()
            val groups = smsRows.groupBy {
                listOf(it.accountId, it.amount, it.type.name, it.note)
            }
            for (rows in groups.values) {
                var anchor: TransactionEntity? = null
                for (row in rows.sortedWith(compareBy<TransactionEntity> { it.createdAt }.thenBy { it.id })) {
                    if (anchor == null ||
                        kotlin.math.abs(row.timestamp - anchor!!.timestamp) >
                            LedgerReconciliation.DUPLICATE_WINDOW_MILLIS
                    ) {
                        anchor = row
                    } else {
                        transactionDao.delete(row)
                        applyBalance(row, reverse = true)
                    }
                }
            }

            val allTransactions = transactionDao.getAll()
            for (stored in accountDao.getAll()) {
                val account = accountDao.getById(stored.id) ?: continue
                val deltas = allTransactions.sumOf { transaction ->
                    LedgerMath.deltas(
                        type = transaction.type,
                        amount = transaction.amount,
                        accountId = transaction.accountId,
                        targetAccountId = transaction.targetAccountId
                    ).filter { it.accountId == account.id }.sumOf { it.delta }
                }
                if (account.openingBalanceMinor == 0L) {
                    val opening = account.balance - deltas
                    accountDao.update(
                        account.copy(
                            openingBalanceMinor = opening,
                            balance = opening + deltas
                        )
                    )
                }
            }

            for (account in accountDao.getAll()) {
                val latest = smsImportDao.latestWithBalance(account.id) ?: continue
                val balanceAfter = latest.balanceAfterMinor ?: continue
                val current = accountDao.getById(account.id)?.balance ?: continue
                val adjustment = LedgerReconciliation.adjustmentFor(current, balanceAfter) ?: continue
                val reconciliation = TransactionEntity(
                    id = UUID.randomUUID().toString(),
                    amount = adjustment.amountMinor,
                    type = adjustment.type,
                    categoryId = null,
                    accountId = account.id,
                    timestamp = latest.messageDate.takeIf { it > 0L } ?: latest.importedAt,
                    note = LedgerReconciliation.RECONCILIATION_NOTE
                )
                applyBalance(reconciliation, reverse = false)
                transactionDao.insert(reconciliation)
            }
        }
    }.isSuccess

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
