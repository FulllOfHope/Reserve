package com.app.reserve.core.domain

import com.app.reserve.core.database.BudgetEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import java.time.LocalDate

/** Budget maths on Long minor units; no floating point is used for money. */
object BudgetCalculator {

    fun transactionsInMonth(
        transactions: List<TransactionEntity>,
        monthYear: String
    ): List<TransactionEntity> = transactions.filter {
        Dates.monthKey(Dates.toLocalDate(it.timestamp)) == monthYear
    }

    fun spentTotal(transactions: List<TransactionEntity>, monthYear: String): Long =
        transactionsInMonth(transactions, monthYear)
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }

    fun incomeTotal(transactions: List<TransactionEntity>, monthYear: String): Long =
        transactionsInMonth(transactions, monthYear)
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }

    fun spentForCategory(
        transactions: List<TransactionEntity>,
        monthYear: String,
        categoryId: String?
    ): Long = transactionsInMonth(transactions, monthYear)
        .filter { it.type == TransactionType.EXPENSE && it.categoryId == categoryId }
        .sumOf { it.amount }

    fun progress(budget: BudgetEntity, transactions: List<TransactionEntity>): BudgetProgress {
        // A null categoryId is an all-expenses budget, not an "uncategorised" one.
        val spent = if (budget.categoryId == null) {
            spentTotal(transactions, budget.monthYear)
        } else {
            spentForCategory(transactions, budget.monthYear, budget.categoryId)
        }
        return BudgetProgress(budget, spent)
    }

    /**
     * Compares actual spend with an even pace through the month. A budget for a
     * past or future month is reported with the full month elapsed so the UI
     * never claims a "today" pace for a month that is not in progress.
     */
    fun pace(
        budget: BudgetEntity,
        transactions: List<TransactionEntity>,
        today: LocalDate = Dates.today()
    ): BudgetPace {
        val first = Dates.monthDate(budget.monthYear)
        val daysInMonth = first.lengthOfMonth()
        val elapsedDays = when {
            today < first -> 0
            Dates.monthKey(today) != budget.monthYear -> daysInMonth
            else -> today.dayOfMonth
        }
        // Exact floor division in minor units: (a * n) / d without overflow.
        val perDay = budget.amount / daysInMonth
        val remainder = budget.amount % daysInMonth
        val expected = perDay * elapsedDays + (remainder * elapsedDays) / daysInMonth
        return BudgetPace(progress(budget, transactions), elapsedDays, daysInMonth, expected)
    }

    /**
     * Top slices plus one "Other" slice, so the donut and its legend always
     * describe the same set of expenses.
     */
    fun groupedBreakdown(
        transactions: List<TransactionEntity>,
        monthYear: String,
        categories: List<CategoryEntity>,
        maxSlices: Int = 7
    ): List<CategorySpend> {
        val rows = breakdown(transactions, monthYear, categories)
        if (rows.size <= maxSlices) return rows
        val head = rows.take(maxSlices - 1)
        val tail = rows.drop(maxSlices - 1).sumOf { it.spent }
        return head + CategorySpend(null, CategorySpend.OTHER_ID, tail)
    }

    /** Categories with the most spending first. */
    fun breakdown(
        transactions: List<TransactionEntity>,
        monthYear: String,
        categories: List<CategoryEntity>
    ): List<CategorySpend> {
        val byCategory = transactionsInMonth(transactions, monthYear)
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.sumOf { it.amount } }
        return byCategory.map { (categoryId, spent) ->
            CategorySpend(
                category = categories.firstOrNull { it.id == categoryId },
                categoryId = categoryId,
                spent = spent
            )
        }.sortedByDescending { it.spent }
    }

    fun monthlySeries(
        transactions: List<TransactionEntity>,
        endMonth: String,
        months: Int = 6
    ): List<MonthTotal> {
        val keys = (months - 1 downTo 0).map { offset ->
            Dates.shiftMonth(endMonth, -offset.toLong())
        }
        return keys.map { key ->
            MonthTotal(
                monthYear = key,
                label = Dates.monthLabel(key).substringBefore(' ').take(3),
                spent = spentTotal(transactions, key),
                income = transactions.filter { it.type == TransactionType.INCOME && Dates.monthKey(Dates.toLocalDate(it.timestamp)) == key }.sumOf { it.amount }
            )
        }
    }

    fun netWorth(accounts: List<com.app.reserve.core.database.AccountEntity>): Long =
        accounts.filterNot { it.isArchived }.sumOf { it.balance }
}

data class BudgetProgress(val budget: BudgetEntity, val spent: Long) {
    /** Ratio used only for drawing the bar, never for money arithmetic. */
    val ratio: Float
        get() = if (budget.amount <= 0L) 0f else spent.toFloat() / budget.amount.toFloat()

    val remaining: Long get() = budget.amount - spent

    val isOver: Boolean get() = spent > budget.amount

    val isWarning: Boolean get() = !isOver && ratio >= 0.9f
}

data class BudgetPace(
    val progress: BudgetProgress,
    val elapsedDays: Int,
    val daysInMonth: Int,
    val expectedSpent: Long
) {
    /** Money spent above an even day-by-day pace; negative means under pace. */
    val delta: Long get() = progress.spent - expectedSpent
    val isAhead: Boolean get() = delta > 0L
    val isCurrentMonth: Boolean get() = progress.budget.monthYear == Dates.currentMonthKey()
}

data class CategorySpend(
    val category: CategoryEntity?,
    val categoryId: String?,
    val spent: Long
) {
    companion object {
        const val OTHER_ID = "__other__"
    }

    val isOther: Boolean get() = categoryId == OTHER_ID
}

data class MonthTotal(val monthYear: String, val label: String, val spent: Long, val income: Long)
