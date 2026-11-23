package com.app.reserve.core.data

import androidx.room.withTransaction
import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.database.BudgetEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.RecurringEntity
import com.app.reserve.core.database.ReserveDatabase
import com.app.reserve.core.database.SmsImportEntity
import com.app.reserve.core.database.ToBePaidEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.datastore.SettingsDataStore
import com.app.reserve.core.domain.Dates
import com.app.reserve.core.domain.LedgerMath
import com.app.reserve.core.domain.LedgerReconciliation
import com.app.reserve.core.domain.ParsedSms
import com.app.reserve.core.domain.PendingToBePaidMatch
import com.app.reserve.core.domain.ToBePaidMatching
import com.app.reserve.core.domain.LedgerReconciliation.PendingBalanceDiscrepancy
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

const val DEFAULT_EXPENSE_CATEGORY_ID = "seed-uncategorized-expense"
const val DEFAULT_INCOME_CATEGORY_ID = "seed-uncategorized-income"
const val DEFAULT_TRANSFER_CATEGORY_ID = "seed-uncategorized-transfer"
const val DEFAULT_EXPENSE_CATEGORY_NAME = "Unrecognized Expense"
const val DEFAULT_INCOME_CATEGORY_NAME = "Unrecognized Income"
const val DEFAULT_TRANSFER_CATEGORY_NAME = "Unrecognized Transfer"

fun defaultCategoryName(type: TransactionType): String = when (type) {
    TransactionType.EXPENSE -> DEFAULT_EXPENSE_CATEGORY_NAME
    TransactionType.INCOME -> DEFAULT_INCOME_CATEGORY_NAME
    TransactionType.TRANSFER -> DEFAULT_TRANSFER_CATEGORY_NAME
}

/**
 * All writes go through this repository so an account balance and its
 * transaction rows can never disagree. Money stays in Long minor units.
 */
class ReserveRepository(
    private val database: ReserveDatabase,
    private val settings: SettingsDataStore
) {

    /** Serialises delete/restore so a fast Undo can never race the soft-delete. */
    private val trashMutex = Mutex()

    private val transactionDao get() = database.transactionDao()
    private val categoryDao get() = database.categoryDao()
    private val accountDao get() = database.accountDao()
    private val budgetDao get() = database.budgetDao()
    private val recurringDao get() = database.recurringDao()
    private val smsImportDao get() = database.smsImportDao()
    private val toBePaidDao get() = database.toBePaidDao()

    val transactions: Flow<List<TransactionEntity>> = transactionDao.observeAll()
    val trashedTransactions: Flow<List<TransactionEntity>> = transactionDao.observeTrashed()
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val accounts: Flow<List<AccountEntity>> = accountDao.observeAll()
    val budgets: Flow<List<BudgetEntity>> = budgetDao.observeAll()
    val recurringRules: Flow<List<RecurringEntity>> = recurringDao.observeAll()
    val toBePaid: Flow<List<ToBePaidEntity>> = toBePaidDao.observeAll()

    /** Bank balances that disagree with the ledger and need user verification. */
    val pendingBalanceDiscrepancies: Flow<List<PendingBalanceDiscrepancy>> =
        settings.pendingBalanceDiscrepancies

    /** Device-local "was this SMS the planned one?" queue. */
    val pendingToBePaidMatches: Flow<List<PendingToBePaidMatch>> =
        settings.pendingToBePaidMatches

    suspend fun ensureSeeded() {
        if (categoryDao.getAll().isEmpty()) seedCategories()
        reconcileDefaultCategories()
        if (accountDao.getAll().isEmpty()) seedAccounts()
    }

    /** Renames only the fixed default ids; user-named categories are left alone. */
    private suspend fun reconcileDefaultCategories() {
        listOf(
            DEFAULT_EXPENSE_CATEGORY_ID to DEFAULT_EXPENSE_CATEGORY_NAME,
            DEFAULT_INCOME_CATEGORY_ID to DEFAULT_INCOME_CATEGORY_NAME,
            DEFAULT_TRANSFER_CATEGORY_ID to DEFAULT_TRANSFER_CATEGORY_NAME
        ).forEach { (id, name) ->
            val category = categoryDao.getById(id)
            if (category == null && id == DEFAULT_TRANSFER_CATEGORY_ID) {
                categoryDao.insert(
                    CategoryEntity(
                        id = id,
                        name = name,
                        colorHex = "#948B7A",
                        iconName = "other",
                        type = TransactionType.TRANSFER
                    )
                )
            } else if (category != null && category.name == "Uncategorized") {
                categoryDao.update(category.copy(name = name))
            }
        }
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
                CategoryEntity(DEFAULT_EXPENSE_CATEGORY_ID, DEFAULT_EXPENSE_CATEGORY_NAME, "#948B7A", "other", TransactionType.EXPENSE),
                CategoryEntity(DEFAULT_INCOME_CATEGORY_ID, DEFAULT_INCOME_CATEGORY_NAME, "#948B7A", "other", TransactionType.INCOME),
                CategoryEntity(DEFAULT_TRANSFER_CATEGORY_ID, DEFAULT_TRANSFER_CATEGORY_NAME, "#948B7A", "other", TransactionType.TRANSFER)
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
        val wantedName = defaultCategoryName(type)
        val fixedId = when (type) {
            TransactionType.EXPENSE -> DEFAULT_EXPENSE_CATEGORY_ID
            TransactionType.INCOME -> DEFAULT_INCOME_CATEGORY_ID
            TransactionType.TRANSFER -> DEFAULT_TRANSFER_CATEGORY_ID
        }
        val existing = categoryDao.getAll().firstOrNull {
            it.type == type && it.name == wantedName && it.id != excluding
        }
        if (existing != null) return existing
        val created = CategoryEntity(
            id = if (fixedId == excluding) UUID.randomUUID().toString() else fixedId,
            name = wantedName,
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

    /**
     * Sets an account's current balance by adjusting its opening balance, so
     * `balance == openingBalanceMinor + sum(transaction deltas)` stays exact
     * and no fake "reconciliation" row is ever inserted.
     */
    suspend fun setAccountBalance(accountId: String, targetBalanceMinor: Long) {
        database.withTransaction {
            val account = accountDao.getById(accountId) ?: return@withTransaction
            val delta = targetBalanceMinor - account.balance
            if (delta == 0L) return@withTransaction
            accountDao.setOpeningBalance(
                accountId,
                account.openingBalanceMinor + delta
            )
            accountDao.setBalance(accountId, targetBalanceMinor)
        }
    }

    suspend fun dismissBalanceDiscrepancy(accountId: String) {
        settings.dismissBalanceDiscrepancy(accountId)
    }

    /**
     * Resolves a verified discrepancy. When [addDifferenceTransaction] is true
     * the delta is posted as a real income/expense row and the opening balance
     * is untouched; otherwise only the opening balance is adjusted.
     */
    suspend fun resolveBalanceDiscrepancy(
        discrepancy: PendingBalanceDiscrepancy,
        addDifferenceTransaction: Boolean
    ) {
        database.withTransaction {
            val account = accountDao.getById(discrepancy.accountId) ?: return@withTransaction
            if (addDifferenceTransaction && discrepancy.differenceMinor != 0L) {
                val type = if (discrepancy.differenceMinor > 0L) {
                    TransactionType.INCOME
                } else {
                    TransactionType.EXPENSE
                }
                val correction = TransactionEntity(
                    id = UUID.randomUUID().toString(),
                    amount = kotlin.math.abs(discrepancy.differenceMinor),
                    type = type,
                    categoryId = null,
                    accountId = account.id,
                    timestamp = discrepancy.timestamp,
                    note = LedgerReconciliation.BALANCE_VERIFICATION_NOTE
                )
                applyBalance(correction, reverse = false)
                transactionDao.insert(correction)
            } else {
                setAccountBalance(account.id, discrepancy.smsBalanceMinor)
            }
        }
        settings.dismissBalanceDiscrepancy(discrepancy.accountId)
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
    // To Be Paid — future one-off reminders. They never move a balance until
    // they are settled, and settling always produces at most one real row.
    // ---------------------------------------------------------------------

    suspend fun saveToBePaid(item: ToBePaidEntity) {
        toBePaidDao.upsert(item)
    }

    suspend fun deleteToBePaid(id: String) {
        toBePaidDao.deleteById(id)
    }

    suspend fun allToBePaid(): List<ToBePaidEntity> = toBePaidDao.getAll()

    /**
     * Checklist completion: creates one real transaction and links it to the
     * reminder in the same DB transaction. Repeated taps are no-ops.
     */
    suspend fun markToBePaidAsPaid(
        id: String,
        now: Long = System.currentTimeMillis()
    ): TransactionEntity? {
        var created: TransactionEntity? = null
        database.withTransaction {
            val reminder = toBePaidDao.getById(id) ?: return@withTransaction
            if (reminder.isSettled) return@withTransaction
            val note = listOf(reminder.title, reminder.note.trim())
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            val transaction = TransactionEntity(
                id = UUID.randomUUID().toString(),
                amount = reminder.amount,
                type = reminder.type,
                categoryId = reminder.categoryId,
                accountId = reminder.accountId,
                timestamp = now,
                note = note
            )
            applyBalance(transaction, reverse = false)
            transactionDao.insert(transaction)
            toBePaidDao.settle(id, transaction.id, now, "MANUAL")
            created = transaction
        }
        if (created != null) settings.dismissToBePaidMatch(id)
        return created
    }

    /**
     * Confirms an SMS row was the planned movement. The transaction already
     * exists and has already changed the balance, so this only links the two
     * rows and clears the prompt — it never creates a second transaction.
     */
    suspend fun confirmToBePaidMatch(match: PendingToBePaidMatch) {
        database.withTransaction {
            val reminder = toBePaidDao.getById(match.reminderId) ?: return@withTransaction
            if (reminder.isSettled) return@withTransaction
            val transaction = transactionDao.getByIdIncludingDeleted(match.transactionId)
                ?: return@withTransaction
            if (transaction.deletedAt != null) return@withTransaction
            toBePaidDao.settle(reminder.id, transaction.id, System.currentTimeMillis(), "SMS_MATCH")
        }
        settings.dismissToBePaidMatch(match.reminderId)
    }

    suspend fun dismissToBePaidMatch(match: PendingToBePaidMatch) {
        settings.dismissToBePaidMatch(match.reminderId)
    }

    /** Best single candidate for a freshly imported SMS, if one is outstanding. */
    private suspend fun findToBePaidMatch(transaction: TransactionEntity): PendingToBePaidMatch? {
        if (transaction.type == TransactionType.TRANSFER) return null
        val window = ToBePaidMatching.MATCH_WINDOW_MILLIS
        val reminder = toBePaidDao.findMatchCandidates(
            type = transaction.type,
            amount = transaction.amount,
            from = transaction.timestamp - window,
            to = transaction.timestamp + window,
            anchor = transaction.timestamp
        ).firstOrNull() ?: return null
        if (!ToBePaidMatching.isCandidate(reminder, transaction)) return null
        return PendingToBePaidMatch(
            reminderId = reminder.id,
            reminderTitle = reminder.title,
            transactionId = transaction.id,
            transactionNote = transaction.note,
            amountMinor = transaction.amount,
            transactionTimestamp = transaction.timestamp
        )
    }

    // ---------------------------------------------------------------------
    // Backup / restore / import helpers
    // ---------------------------------------------------------------------

    suspend fun snapshot(): LedgerSnapshot = LedgerSnapshot(
        accounts = accountDao.getAll(),
        categories = categoryDao.getAll(),
        transactions = transactionDao.getAllIncludingDeleted(),
        budgets = budgetDao.getAll(),
        recurring = recurringDao.getAll(),
        toBePaid = toBePaidDao.getAll()
    )

    suspend fun replaceAll(snapshot: LedgerSnapshot) {
        database.withTransaction {
            transactionDao.clear()
            budgetDao.clear()
            recurringDao.clear()
            toBePaidDao.clear()
            categoryDao.clear()
            accountDao.clear()
            categoryDao.insertAll(snapshot.categories)
            accountDao.insertAll(snapshot.accounts)
            transactionDao.insertAll(snapshot.transactions)
            snapshot.budgets.forEach { budgetDao.upsert(it) }
            snapshot.recurring.forEach { recurringDao.upsert(it) }
            toBePaidDao.insertAll(snapshot.toBePaid)
        }
        // Device-local prompts belong to the ledger that produced them. Never
        // carry them across a restore, including the LedgerViewModel path.
        settings.clearBalanceDiscrepancies()
        settings.clearToBePaidMatches()
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

    /** Smart-import path: dedupes and can attach transfer categories from the file. */
    suspend fun importSmartTransactions(
        rows: List<ImportedTransaction>,
        defaultAccountId: String
    ): ImportSummary {
        var imported = 0
        var skipped = 0
        database.withTransaction {
            val accountsById = accountDao.getAll().associateBy { it.id }
            val categoriesById = categoryDao.getAll().associateBy { it.id }
            val seen = transactionDao.getAll()
                .filter { it.deletedAt == null }
                .mapTo(mutableSetOf()) { transaction ->
                    fingerprint(
                        timestamp = transaction.timestamp,
                        type = transaction.type,
                        amountMinor = transaction.amount,
                        note = transaction.note,
                        accountName = accountsById[transaction.accountId]?.name
                            ?.takeIf { it.isNotBlank() } ?: "Imported",
                        targetName = accountsById[transaction.targetAccountId]?.name
                            ?.takeIf { it.isNotBlank() }
                            ?: if (transaction.type == TransactionType.TRANSFER) "Imported" else "",
                        categoryName = categoriesById[transaction.categoryId.orEmpty()]?.name
                            ?.takeIf { it.isNotBlank() } ?: defaultCategoryName(transaction.type)
                    )
                }
            for (row in rows) {
                try {
                    if (!seen.add(
                            fingerprint(
                                timestamp = row.timestamp,
                                type = row.type,
                                amountMinor = row.amountMinor,
                                note = row.note,
                                accountName = row.accountName.ifBlank { "Imported" },
                                targetName = if (row.type == TransactionType.TRANSFER) {
                                    row.targetAccountName.ifBlank { row.accountName }.ifBlank { "Imported" }
                                } else {
                                    ""
                                },
                                categoryName = row.categoryName.ifBlank { defaultCategoryName(row.type) }
                            )
                        )
                    ) {
                        skipped++
                        continue
                    }
                    val account = findOrCreateAccount(row.accountName, defaultAccountId)
                    val category = findOrCreateCategory(row.categoryName, row.type)
                    val target = if (row.type == TransactionType.TRANSFER) {
                        findOrCreateAccount(row.targetAccountName.ifBlank { row.accountName }, defaultAccountId)
                    } else {
                        null
                    }
                    val transaction = TransactionEntity(
                        id = UUID.randomUUID().toString(),
                        amount = row.amountMinor,
                        type = row.type,
                        categoryId = category.id,
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

    private fun fingerprint(
        timestamp: Long,
        type: TransactionType,
        amountMinor: Long,
        note: String,
        accountName: String,
        targetName: String,
        categoryName: String
    ): String = listOf(
        (timestamp / 60_000L).toString(),
        type.name,
        amountMinor.toString(),
        note.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " "),
        accountName.trim().lowercase(Locale.ROOT),
        targetName.trim().lowercase(Locale.ROOT),
        categoryName.trim().lowercase(Locale.ROOT)
    ).joinToString("|")

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
        val createdAccountIds = linkedSetOf<String>()
        val newestBalances = linkedMapOf<String, SmsBalanceObservation>()
        val matchedReminders = mutableListOf<PendingToBePaidMatch>()
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
                            accountsByName[accountKey]?.let { known ->
                                recordSmsImport(message, known.id, known.id, newestBalances)
                            }
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
                            if (accountCreatedNow) createdAccountIds += id
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
                            recordSmsImport(message, account!!.id, duplicate.id, newestBalances)
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
                        findToBePaidMatch(transaction)?.let { matchedReminders += it }

                        // No per-message balance correction here. New accounts
                        // are anchored and established accounts are verified
                        // once, after every batch of the scan has been imported.
                        recordSmsImport(message, account!!.id, transactionId, newestBalances)
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
        if (matchedReminders.isNotEmpty()) settings.enqueueToBePaidMatches(matchedReminders)
        return SmsImportResult(
            imported = imported,
            duplicates = duplicates,
            createdAccountIds = createdAccountIds.toSet(),
            newestBalances = newestBalances.toMap(),
            pendingToBePaidMatches = matchedReminders
        )
    }

    private suspend fun recordSmsImport(
        message: ParsedSms,
        accountId: String,
        transactionId: String,
        newestBalances: MutableMap<String, SmsBalanceObservation>
    ) {
        message.balanceAfterMinor?.let { balance ->
            val existing = newestBalances[accountId]
            if (existing == null || message.timestamp > existing.messageTimestamp) {
                newestBalances[accountId] = SmsBalanceObservation(
                    accountId = accountId,
                    balanceAfterMinor = balance,
                    messageTimestamp = message.timestamp
                )
            }
        }
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

    /**
     * Called once after every SMS batch has been imported. New accounts are
     * anchored to the newest bank balance without fake transactions; existing
     * accounts only raise a verification prompt when they actually disagree.
     */
    suspend fun finishSmsImport(
        createdAccountIds: Set<String>,
        newestBalances: Map<String, SmsBalanceObservation>
    ) {
        val found = mutableListOf<PendingBalanceDiscrepancy>()
        database.withTransaction {
            for (observation in newestBalances.values) {
                val account = accountDao.getById(observation.accountId) ?: continue
                val newestKnown = maxOf(
                    observation.messageTimestamp,
                    smsImportDao.latestMessageDate(account.id) ?: 0L
                )
                // An out-of-order or replayed alert must never rewrite newer state.
                if (observation.messageTimestamp < newestKnown) continue

                if (observation.accountId in createdAccountIds) {
                    val deltas = transactionDao.getAll()
                        .filter { it.deletedAt == null }
                        .filter {
                            it.accountId == account.id || it.targetAccountId == account.id
                        }
                        .sumOf { transaction ->
                            LedgerMath.deltas(
                                type = transaction.type,
                                amount = transaction.amount,
                                accountId = transaction.accountId,
                                targetAccountId = transaction.targetAccountId
                            ).filter { it.accountId == account.id }.sumOf { it.delta }
                        }
                    val opening = LedgerReconciliation.anchoredOpening(
                        latestSmsBalanceMinor = observation.balanceAfterMinor,
                        sumOfDeltas = deltas
                    )
                    accountDao.setOpeningBalance(account.id, opening)
                    accountDao.setBalance(account.id, observation.balanceAfterMinor)
                } else {
                    LedgerReconciliation.discrepancyFor(
                        accountId = account.id,
                        accountName = account.name,
                        reserveBalanceMinor = account.balance,
                        smsBalanceMinor = observation.balanceAfterMinor,
                        timestamp = observation.messageTimestamp
                    )?.let { found += it }
                }
            }
        }
        // Persist the whole batch in one DataStore transaction. The queue keeps
        // one newest prompt per account, so a multi-account scan never loses one.
        settings.enqueueBalanceDiscrepancies(found)
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
        val wanted = name.trim().ifBlank { defaultCategoryName(type) }
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
    val recurring: List<RecurringEntity>,
    /** Additive field; defaults keep older call sites and backups valid. */
    val toBePaid: List<ToBePaidEntity> = emptyList()
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

/** Newest bank balance seen for an account during one bounded scan. */
data class SmsBalanceObservation(
    val accountId: String,
    val balanceAfterMinor: Long,
    val messageTimestamp: Long
)

data class SmsImportResult(
    val imported: Int,
    val duplicates: Int,
    /** Accounts first created by this import; they need balance anchoring. */
    val createdAccountIds: Set<String> = emptySet(),
    /** Newest SMS balance per account, to be processed once after all batches. */
    val newestBalances: Map<String, SmsBalanceObservation> = emptyMap(),
    /** Planned reminders that look like one of the newly imported SMS rows. */
    val pendingToBePaidMatches: List<PendingToBePaidMatch> = emptyList()
)
