package com.app.reserve.core.domain

import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.database.BudgetEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCalculatorTest {

    private val month = "2026-11"

    private fun transaction(
        id: String,
        amount: Long,
        type: TransactionType,
        categoryId: String?,
        day: Int = 5
    ) = TransactionEntity(
        id = id,
        amount = amount,
        type = type,
        categoryId = categoryId,
        accountId = "cash",
        targetAccountId = if (type == TransactionType.TRANSFER) "bank" else null,
        timestamp = Dates.toTimestamp(LocalDate.of(2026, 11, day))
    )

    private val transactions = listOf(
        transaction("t1", 50_000, TransactionType.EXPENSE, "food"),
        transaction("t2", 20_000, TransactionType.EXPENSE, "food"),
        transaction("t3", 30_000, TransactionType.EXPENSE, "transport"),
        transaction("t4", 200_000, TransactionType.INCOME, "salary"),
        transaction("t5", 75_000, TransactionType.TRANSFER, null),
        transaction("t6", 99_999, TransactionType.EXPENSE, "food", day = 1).copy(
            timestamp = Dates.toTimestamp(LocalDate.of(2026, 10, 31))
        )
    )

    private val categories = listOf(
        CategoryEntity("food", "Food", "#5C947F", "food", TransactionType.EXPENSE),
        CategoryEntity("transport", "Transport", "#6B83A3", "transport", TransactionType.EXPENSE),
        CategoryEntity("salary", "Salary", "#5C947F", "salary", TransactionType.INCOME)
    )

    @Test
    fun `expenses sum only for the selected month`() {
        assertEquals(100_000L, BudgetCalculator.spentTotal(transactions, month))
    }

    @Test
    fun `income and transfers are separate`() {
        assertEquals(200_000L, BudgetCalculator.incomeTotal(transactions, month))
        // The transfer is present in the data but never counted as spending.
        assertEquals(5, BudgetCalculator.transactionsInMonth(transactions, month).size)
    }

    @Test
    fun `category spend ignores other categories`() {
        assertEquals(70_000L, BudgetCalculator.spentForCategory(transactions, month, "food"))
        assertEquals(30_000L, BudgetCalculator.spentForCategory(transactions, month, "transport"))
        assertEquals(0L, BudgetCalculator.spentForCategory(transactions, month, "missing"))
    }

    @Test
    fun `budget progress reports remaining over and warning states`() {
        val allExpenses = BudgetCalculator.progress(
            BudgetEntity("b0", null, 150_000, month),
            transactions
        )
        assertEquals(100_000L, allExpenses.spent)
        assertEquals(50_000L, allExpenses.remaining)

        val healthy = BudgetEntity("b1", "food", 100_000, month)
        val progress = BudgetCalculator.progress(healthy, transactions)
        assertEquals(70_000L, progress.spent)
        assertEquals(30_000L, progress.remaining)
        assertFalse(progress.isOver)
        assertFalse(progress.isWarning)

        val tight = BudgetCalculator.progress(
            BudgetEntity("b2", "food", 75_000, month),
            transactions
        )
        assertTrue(tight.isWarning)

        val over = BudgetCalculator.progress(
            BudgetEntity("b3", "food", 60_000, month),
            transactions
        )
        assertTrue(over.isOver)
        assertEquals(-10_000L, over.remaining)
    }

    @Test
    fun `breakdown sorts categories by spend and skips transfers`() {
        val breakdown = BudgetCalculator.breakdown(transactions, month, categories)
        assertEquals(listOf("food", "transport"), breakdown.map { it.categoryId })
        assertEquals(70_000L, breakdown.first().spent)
    }

    @Test
    fun `monthly series returns the requested window ending on the month`() {
        val series = BudgetCalculator.monthlySeries(transactions, month, 3)
        assertEquals(listOf("2026-09", "2026-10", "2026-11"), series.map { it.monthYear })
        assertEquals(99_999L, series[1].spent)
        assertEquals(100_000L, series[2].spent)
    }

    @Test
    fun `net worth excludes archived accounts`() {
        val accounts = listOf(
            AccountEntity("a", "Cash", 50_000, isArchived = false, createdAt = 1L),
            AccountEntity("b", "Bank", 120_000, isArchived = false, createdAt = 2L),
            AccountEntity("c", "Old", 999_000, isArchived = true, createdAt = 3L)
        )
        assertEquals(170_000L, BudgetCalculator.netWorth(accounts))
    }
}
