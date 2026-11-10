package com.app.reserve.feature.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.reserve.core.data.ReserveRepository
import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.database.BudgetEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.RecurringEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.domain.BackupManager
import com.app.reserve.core.domain.BudgetCalculator
import com.app.reserve.core.domain.CsvManager
import com.app.reserve.core.domain.Dates
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
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val recurring: List<RecurringEntity> = emptyList(),
    val pendingDeletes: Set<String> = emptySet()
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

    val monthBudgets: List<BudgetEntity> get() = budgets.filter { it.monthYear == monthYear }

    fun accountName(id: String?): String =
        accounts.firstOrNull { it.id == id }?.name ?: "Account"

    fun categoryFor(id: String?): CategoryEntity? =
        categories.firstOrNull { it.id == id }

    fun expensesFor(type: com.app.reserve.core.database.TransactionType): List<CategoryEntity> =
        categories.filter { it.type == type }

    val totalBalance: Long get() = activeAccounts.sumOf { it.balance }
}

class LedgerViewModel(private val repository: ReserveRepository) : ViewModel() {

    private val selectedMonth = MutableStateFlow(Dates.currentMonthKey())
    private val pendingDeletes = MutableStateFlow<Set<String>>(emptySet())

    private data class Bundle(
        val transactions: List<TransactionEntity>,
        val categories: List<CategoryEntity>,
        val accounts: List<AccountEntity>,
        val budgets: List<BudgetEntity>,
        val recurring: List<RecurringEntity>
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

    val state: StateFlow<LedgerState> = combine(
        bundle,
        selectedMonth,
        pendingDeletes
    ) { data, monthYear, pending ->
        LedgerState(
            loading = false,
            monthYear = monthYear,
            transactions = data.transactions,
            categories = data.categories,
            accounts = data.accounts,
            budgets = data.budgets,
            recurring = data.recurring,
            pendingDeletes = pending
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LedgerState()
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

    /** Removes the row from the list immediately but keeps it in the database. */
    fun requestDelete(transaction: TransactionEntity) {
        pendingDeletes.update { it + transaction.id }
    }

    fun undoDelete(transaction: TransactionEntity) {
        pendingDeletes.update { it - transaction.id }
    }

    fun commitDelete(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            pendingDeletes.update { it - transaction.id }
        }
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
