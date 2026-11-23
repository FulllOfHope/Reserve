package com.app.reserve.core.domain

import com.app.reserve.core.database.BudgetEntity
import com.app.reserve.core.database.RecurringEntity
import com.app.reserve.core.database.ToBePaidEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthForecastTest {

    private val month = "2026-11"
    private val midMonth = LocalDate.of(2026, 11, 15)

    private fun transaction(
        id: String,
        amount: Long,
        type: TransactionType,
        categoryId: String? = null,
        day: Int = 5
    ) = TransactionEntity(
        id = id,
        amount = amount,
        type = type,
        categoryId = categoryId,
        accountId = "cash",
        timestamp = Dates.toTimestamp(LocalDate.of(2026, 11, day))
    )

    private fun rule(amount: Long, day: Int, type: TransactionType = TransactionType.EXPENSE) =
        RecurringEntity(
            id = "r$day$amount",
            amount = amount,
            type = type,
            categoryId = null,
            accountId = "cash",
            dayOfMonth = day,
            nextDueAt = Dates.toTimestamp(LocalDate.of(2026, 11, day))
        )

    @Test
    fun `safe to spend is capped by balance and reduced by upcoming recurring`() {
        val result = MonthForecast.compute(
            balance = 50_000L,
            monthYear = month,
            budgets = listOf(BudgetEntity("b", null, 100_000L, month)),
            transactions = listOf(transaction("t", 40_000L, TransactionType.EXPENSE)),
            recurring = listOf(rule(10_000L, 20)),
            today = midMonth
        )
        assertEquals(60_000L, result.budgetRemaining)
        assertEquals(10_000L, result.upcomingRecurring)
        assertEquals(40_000L, result.safeToSpend)
        assertEquals(15, result.daysLeft)
        assertTrue(result.hasBudgets)
        // 40,000 spent by day 15 of a 30-day month projects to 80,000.
        assertEquals(80_000L, result.projectedSpend)
    }

    @Test
    fun `upcoming income and past-due rules do not reduce spending room`() {
        val result = MonthForecast.compute(
            balance = 500_000L,
            monthYear = month,
            budgets = emptyList(),
            transactions = listOf(
                transaction("income", 200_000L, TransactionType.INCOME),
                transaction("expense", 50_000L, TransactionType.EXPENSE)
            ),
            recurring = listOf(rule(90_000L, 1), rule(70_000L, 25, TransactionType.INCOME)),
            today = midMonth
        )
        assertFalse(result.hasBudgets)
        assertEquals(0L, result.upcomingRecurring)
        // Without budgets, income left this month (150,000) is the ceiling.
        assertEquals(150_000L, result.safeToSpend)
    }

    @Test
    fun `category budgets replace the all-expenses budget instead of stacking`() {
        val result = MonthForecast.compute(
            balance = 900_000L,
            monthYear = month,
            budgets = listOf(
                BudgetEntity("all", null, 100_000L, month),
                BudgetEntity("food", "food", 60_000L, month)
            ),
            transactions = listOf(transaction("f", 20_000L, TransactionType.EXPENSE, "food")),
            recurring = emptyList(),
            today = midMonth
        )
        assertEquals(40_000L, result.budgetRemaining)
        assertEquals(40_000L, result.safeToSpend)
    }

    @Test
    fun `a completed month has no days left and does not extrapolate spend`() {
        val result = MonthForecast.compute(
            balance = 100_000L,
            monthYear = month,
            budgets = emptyList(),
            transactions = listOf(transaction("t", 30_000L, TransactionType.EXPENSE)),
            recurring = emptyList(),
            today = LocalDate.of(2026, 12, 3)
        )
        assertEquals(0, result.daysLeft)
        assertEquals(30_000L, result.projectedSpend)
    }

    private fun plan(
        id: String,
        amount: Long,
        type: TransactionType,
        day: Int = 20,
        settled: Boolean = false
    ) = ToBePaidEntity(
        id = id,
        title = "Planned $id",
        amount = amount,
        type = type,
        categoryId = null,
        accountId = "cash",
        dueAt = Dates.toTimestamp(LocalDate.of(2026, 11, day)),
        settledTransactionId = if (settled) "posted-$id" else null
    )

    @Test
    fun `planned payments lower safe to spend while planned income only informs projection`() {
        val result = MonthForecast.compute(
            balance = 500_000L,
            monthYear = month,
            budgets = emptyList(),
            transactions = listOf(
                transaction("income", 100_000L, TransactionType.INCOME),
                transaction("expense", 20_000L, TransactionType.EXPENSE)
            ),
            recurring = emptyList(),
            toBePaid = listOf(
                plan("bill", 30_000L, TransactionType.EXPENSE),
                plan("refund", 25_000L, TransactionType.INCOME, day = 22)
            ),
            today = midMonth
        )
        assertEquals(30_000L, result.plannedOutgoing)
        assertEquals(25_000L, result.plannedIncoming)
        assertEquals(2, result.plannedCount)
        // Income left this month is 80,000; the 30,000 bill is still an obligation.
        assertEquals(50_000L, result.safeToSpend)
        assertEquals(40_000L, result.projectedSpend)
        assertEquals(555_000L, result.projectedBalance)
    }

    @Test
    fun `settled and other-month reminders never affect this forecast`() {
        val result = MonthForecast.compute(
            balance = 500_000L,
            monthYear = month,
            budgets = emptyList(),
            transactions = listOf(
                transaction("income", 100_000L, TransactionType.INCOME),
                transaction("expense", 20_000L, TransactionType.EXPENSE)
            ),
            recurring = emptyList(),
            toBePaid = listOf(
                plan("settled", 90_000L, TransactionType.EXPENSE, settled = true),
                plan("december", 40_000L, TransactionType.EXPENSE).copy(
                    dueAt = Dates.toTimestamp(LocalDate.of(2026, 12, 2))
                )
            ),
            today = midMonth
        )
        assertEquals(0, result.plannedCount)
        assertEquals(0L, result.plannedOutgoing)
        assertEquals(80_000L, result.safeToSpend)
        assertEquals(560_000L, result.projectedBalance)
    }
}
