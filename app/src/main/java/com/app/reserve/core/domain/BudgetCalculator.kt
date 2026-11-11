package com.app.reserve.core.domain

import com.app.reserve.core.database.BudgetEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType

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

data class CategorySpend(
    val category: CategoryEntity?,
    val categoryId: String?,
    val spent: Long
)

data class MonthTotal(val monthYear: String, val label: String, val spent: Long, val income: Long)
