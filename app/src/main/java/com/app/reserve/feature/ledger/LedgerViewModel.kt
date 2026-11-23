package com.app.reserve.feature.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.reserve.core.data.ReserveRepository
import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.database.BudgetEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.RecurringEntity
import com.app.reserve.core.database.ToBePaidEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.datastore.SettingsDataStore
import com.app.reserve.core.domain.BackupManager
import com.app.reserve.core.domain.BudgetCalculator
import com.app.reserve.core.domain.CsvManager
import com.app.reserve.core.domain.Dates
import com.app.reserve.core.domain.LedgerReconciliation.PendingBalanceDiscrepancy
import com.app.reserve.core.domain.PendingToBePaidMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LedgerState(
    val loading: Boolean = true,
    val monthYear: String = Dates.currentMonthKey(),
    val transactions: List<TransactionEntity> = emptyList(),
    /** Rows in Trash, ordered by most-recently-deleted first. */
    val trashedTransactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val recurring: List<RecurringEntity> = emptyList(),
    val toBePaid: List<ToBePaidEntity> = emptyList(),
    val pendingDeletes: Set<String> = emptySet(),
    /** Null means all active accounts; an explicit set can be empty. */
    val balanceAccountIds: Set<String>? = null
) {
    val visibleTransactions: List<TransactionEntity>
        get() = transactions.filterNot { it.id in pendingDeletes }

    val monthTransactions: List<TransactionEntity>
        get() = visibleTransactions.filter {
            Dates.monthKey(Dates.toLocalDate(it.timestamp)) == monthYear
        }

    val spent: Long get() = BudgetCalculator.spentTotal(monthTransactions, monthYear)
    val income: Long get() = BudgetCalculator.incomeTotal(monthTransactions, monthYear)
    val net: Long get() = income - spent

    val activeAccounts: List<AccountEntity> get() = accounts.filterNot { it.isArchived }

    /** Accounts whose balances make up the Home hero figure. */
    val balanceAccounts: List<AccountEntity>
        get() = activeAccounts.filter { account ->
            balanceAccountIds == null || account.id in balanceAccountIds
        }

    val monthBudgets: List<BudgetEntity> get() = budgets.filter { it.monthYear == monthYear }

    /** Settled reminders stay in the DB for history but leave the checklist. */
    val outstandingToBePaid: List<ToBePaidEntity>
        get() = toBePaid.filterNot { it.isSettled }

    val monthToBePaid: List<ToBePaidEntity>
        get() = outstandingToBePaid.filter {
            Dates.monthKey(Dates.toLocalDate(it.dueAt)) == monthYear
        }

    fun accountName(id: String?): String =
        accounts.firstOrNull { it.id == id }?.name ?: "Account"

    fun categoryFor(id: String?): CategoryEntity? =
        categories.firstOrNull { it.id == id }

    fun expensesFor(type: com.app.reserve.core.database.TransactionType): List<CategoryEntity> =
        categories.filter { it.type == type }

    val totalBalance: Long get() = balanceAccounts.sumOf { it.balance }
}

class LedgerViewModel(
    private val repository: ReserveRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(Dates.currentMonthKey())
    private val pendingDeletes = MutableStateFlow<Set<String>>(emptySet())

    private data class Bundle(
        val transactions: List<TransactionEntity>,
        val categories: List<CategoryEntity>,
        val accounts: List<AccountEntity>,
        val budgets: List<BudgetEntity>,
        val recurring: List<RecurringEntity>,
        val toBePaid: List<ToBePaidEntity> = emptyList()
    )

    private val bundle = combine(
        repository.transactions,
        repository.categories,
        repository.accounts,
        repository.budgets,
        repository.recurringRules
    ) { transactions, categories, accounts, budgets, recurring ->
        Bundle(transactions, categories, accounts, budgets, recurring)
    }

    private val ledgerBundle = combine(bundle, repository.toBePaid) { data, toBePaid ->
        data.copy(toBePaid = toBePaid)
    }

    val state: StateFlow<LedgerState> = combine(
        ledgerBundle,
        selectedMonth,
        pendingDeletes,
        repository.trashedTransactions,
        settingsDataStore.settings
    ) { data, monthYear, pending, trashed, settings ->
        LedgerState(
            loading = false,
            monthYear = monthYear,
            transactions = data.transactions,
            trashedTransactions = trashed,
            categories = data.categories,
            accounts = data.accounts,
            budgets = data.budgets,
            recurring = data.recurring,
            toBePaid = data.toBePaid,
            pendingDeletes = pending,
            balanceAccountIds = settings.balanceAccountIds
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LedgerState()
    )

    /** Persisted queue from background SMS checks; survives process death. */
    val pendingBalanceDiscrepancies: StateFlow<List<PendingBalanceDiscrepancy>> =
        repository.pendingBalanceDiscrepancies.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    /** Persisted SMS/reminder prompts; the app host shows them one at a time. */
    val pendingToBePaidMatches: StateFlow<List<PendingToBePaidMatch>> =
        repository.pendingToBePaidMatches.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    fun setMonth(monthYear: String) {
        selectedMonth.value = monthYear
    }

    fun shiftMonth(delta: Long) {
        selectedMonth.update { Dates.shiftMonth(it, delta) }
    }

    fun goToCurrentMonth() {
        selectedMonth.value = Dates.currentMonthKey()
    }

    // -- Transactions -------------------------------------------------------

    fun saveTransaction(transaction: TransactionEntity, existing: TransactionEntity?) {
        viewModelScope.launch {
            repository.saveTransaction(transaction, existing)
            selectedMonth.value = Dates.monthKey(Dates.toLocalDate(transaction.timestamp))
        }
    }

    /**
     * Moves the row to Trash immediately. `deletedAt` is persisted, the balance
     * reverses at once, and the active flow drops the row — so no optimistic
     * in-memory hiding is needed. The Undo snackbar calls [undoDelete].
     */
    fun requestDelete(transaction: TransactionEntity) {
        viewModelScope.launch { repository.softDeleteTransaction(transaction.id) }
    }

    /** Restores a just-deleted row to its exact position and balance. */
    fun undoDelete(transaction: TransactionEntity) {
        viewModelScope.launch { repository.restoreTransaction(transaction.id) }
    }

    // -- Trash ---------------------------------------------------------------

    fun restoreFromTrash(transaction: TransactionEntity) {
        viewModelScope.launch { repository.restoreTransaction(transaction.id) }
    }

    fun setTransactionCategory(transaction: TransactionEntity, categoryId: String) {
        viewModelScope.launch { repository.updateTransactionCategory(transaction.id, categoryId) }
    }

    fun deleteForever(transaction: TransactionEntity) {
        viewModelScope.launch { repository.permanentlyDeleteTransaction(transaction.id) }
    }

    fun emptyTrash() {
        viewModelScope.launch { repository.emptyTrash() }
    }

    // -- Categories ---------------------------------------------------------

    fun saveCategory(category: CategoryEntity) {
        viewModelScope.launch { repository.saveCategory(category) }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }

    // -- Accounts -----------------------------------------------------------

    fun saveAccount(account: AccountEntity) {
        viewModelScope.launch { repository.saveAccount(account) }
    }

    fun deleteAccount(account: AccountEntity, onResult: (archived: Boolean) -> Unit) {
        viewModelScope.launch {
            val archived = repository.deleteOrArchiveAccount(account)
            onResult(archived)
        }
    }

    fun restoreAccount(account: AccountEntity) {
        viewModelScope.launch { repository.restoreAccount(account) }
    }

    /** Manual correction: adjusts opening balance, never inserts a fake row. */
    fun setAccountBalance(accountId: String, balanceMinor: Long) {
        viewModelScope.launch { repository.setAccountBalance(accountId, balanceMinor) }
    }

    fun resolveBalanceDiscrepancy(
        discrepancy: PendingBalanceDiscrepancy,
        addDifferenceTransaction: Boolean
    ) {
        viewModelScope.launch {
            repository.resolveBalanceDiscrepancy(discrepancy, addDifferenceTransaction)
        }
    }

    fun dismissBalanceDiscrepancy(accountId: String) {
        viewModelScope.launch { repository.dismissBalanceDiscrepancy(accountId) }
    }

    /** Null = all active accounts; an explicit (possibly empty) set is respected. */
    fun setBalanceAccountIds(ids: Set<String>?, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            settingsDataStore.setBalanceAccountIds(ids)
            onSaved()
        }
    }

    // -- Budgets ------------------------------------------------------------

    fun saveBudget(budget: BudgetEntity) {
        viewModelScope.launch { repository.saveBudget(budget) }
    }

    fun deleteBudget(id: String) {
        viewModelScope.launch { repository.deleteBudget(id) }
    }

    // -- Recurring ----------------------------------------------------------

    fun saveRecurring(rule: RecurringEntity) {
        viewModelScope.launch { repository.saveRecurring(rule) }
    }

    fun deleteRecurring(id: String) {
        viewModelScope.launch { repository.deleteRecurring(id) }
    }

    fun generateRecurring() {
        viewModelScope.launch { repository.generateDueRecurring() }
    }

    // -- To Be Paid ---------------------------------------------------------

    fun saveToBePaid(item: ToBePaidEntity) {
        viewModelScope.launch { repository.saveToBePaid(item) }
    }

    fun deleteToBePaid(id: String) {
        viewModelScope.launch { repository.deleteToBePaid(id) }
    }

    fun markToBePaidAsPaid(id: String) {
        viewModelScope.launch { repository.markToBePaidAsPaid(id) }
    }

    fun confirmToBePaidMatch(match: PendingToBePaidMatch) {
        viewModelScope.launch { repository.confirmToBePaidMatch(match) }
    }

    fun dismissToBePaidMatch(match: PendingToBePaidMatch) {
        viewModelScope.launch { repository.dismissToBePaidMatch(match) }
    }

    // -- Files --------------------------------------------------------------

    suspend fun exportCsv(): String {
        val transactions = repository.allTransactions()
        return CsvManager.export(transactions, repository.allCategories(), repository.allAccounts())
    }

    suspend fun importCsv(text: String): com.app.reserve.core.data.ImportSummary {
        val parsed = CsvManager.parse(text)
        if (parsed.error != null) {
            return com.app.reserve.core.data.ImportSummary(0, -1)
        }
        val accounts = repository.allAccounts()
        val default = accounts.firstOrNull { !it.isArchived }?.id ?: accounts.first()?.id ?: ""
        if (default.isEmpty()) {
            return com.app.reserve.core.data.ImportSummary(0, -1)
        }
        return repository.importTransactions(parsed.rows, default)
    }

    suspend fun smartImport(
        rows: List<com.app.reserve.core.data.ImportedTransaction>
    ): com.app.reserve.core.data.ImportSummary {
        if (rows.isEmpty()) return com.app.reserve.core.data.ImportSummary(0, -1)
        val accounts = repository.allAccounts()
        val default = accounts.firstOrNull { !it.isArchived }?.id ?: accounts.firstOrNull()?.id
        if (default.isNullOrBlank()) {
            return com.app.reserve.core.data.ImportSummary(0, -1)
        }
        return repository.importSmartTransactions(rows, default)
    }

    suspend fun createBackup(): String = BackupManager.encode(repository.snapshot())

    suspend fun restoreBackup(text: String): Boolean {
        val decoded = BackupManager.decode(text)
        decoded.getOrNull()?.let { snapshot ->
            repository.replaceAll(snapshot)
            pendingDeletes.value = emptySet()
            return true
        }
        return false
    }
}
