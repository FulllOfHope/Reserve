package com.app.reserve.core.domain

import com.app.reserve.core.database.BudgetEntity
import com.app.reserve.core.database.RecurringEntity
import com.app.reserve.core.database.ToBePaidEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import java.time.LocalDate

/**
 * Plain-English monthly projection used by the Safe-to-Spend pill and the
 * Month Forecast sheet. Every money value stays a Long in minor units; the UI
 * only converts for display.
 */
object MonthForecast {

    data class Result(
        val safeToSpend: Long,
        val budgetRemaining: Long,
        val upcomingRecurring: Long,
        val recurringCount: Int,
        /** Outstanding one-off reminders in this month. Never posted balances. */
        val plannedOutgoing: Long = 0L,
        /** Informative only: planned income is not spent before it arrives. */
        val plannedIncoming: Long = 0L,
        val plannedCount: Int = 0,
        val projectedSpend: Long,
        val projectedBalance: Long,
        val daysLeft: Int,
        val hasBudgets: Boolean
    )

    fun compute(
        balance: Long,
        monthYear: String,
        budgets: List<BudgetEntity>,
        transactions: List<TransactionEntity>,
        recurring: List<RecurringEntity>,
        toBePaid: List<ToBePaidEntity> = emptyList(),
        today: LocalDate = Dates.today()
    ): Result {
        val isCurrent = Dates.monthKey(today) == monthYear
        val daysInMonth = Dates.monthDate(monthYear).lengthOfMonth()
        val elapsedDays = if (isCurrent) today.dayOfMonth else daysInMonth
        val daysLeft = if (isCurrent) daysInMonth - elapsedDays else 0

        val spent = BudgetCalculator.spentTotal(transactions, monthYear)
        val income = BudgetCalculator.incomeTotal(transactions, monthYear)

        val monthBudgets = budgets.filter { it.monthYear == monthYear && it.amount > 0L }
        // Category budgets are more specific; counting the all-expenses budget
        // as well would double-count the same spends.
        val categoryBudgets = monthBudgets.filter { it.categoryId != null }
        val effectiveBudgets = categoryBudgets.ifEmpty { monthBudgets }
        val budgetRemaining = effectiveBudgets.sumOf { budget ->
            (budget.amount - BudgetCalculator.progress(budget, transactions).spent)
                .coerceAtLeast(0L)
        }

        // Only still-upcoming expense rules reduce spending room. Income and
        // transfers are not obligations, and generated rows are already ledger rows.
        val upcoming = recurring.filter { rule ->
            !rule.paused &&
                rule.type == TransactionType.EXPENSE &&
                rule.amount > 0L &&
                Dates.monthKey(Dates.toLocalDate(rule.nextDueAt)) == monthYear &&
                (!isCurrent || rule.dayOfMonth >= today.dayOfMonth)
        }
        val upcomingRecurring = upcoming.sumOf { it.amount }

        // One-off reminders are real obligations but still only plans. They are
        // shown in the forecast and reduce spending room; they do not touch any
        // account balance until the checklist item is actually settled.
        val plans = toBePaid.filter { item ->
            !item.isSettled &&
                item.type != TransactionType.TRANSFER &&
                Dates.monthKey(Dates.toLocalDate(item.dueAt)) == monthYear
        }
        val plannedOutgoing = plans.filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }
        val plannedIncoming = plans.filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }

        val available = if (effectiveBudgets.isNotEmpty()) {
            budgetRemaining
        } else {
            // With no budgets, income left this month is the natural ceiling.
            (income - spent).coerceAtLeast(0L)
        }
        // Planned income is deliberately not added: it has not arrived yet.
        val safeToSpend = (minOf(balance, available) - upcomingRecurring - plannedOutgoing)
            .coerceAtLeast(0L)

        val projectedSpend = if (isCurrent && elapsedDays > 0) {
            spent * daysInMonth / elapsedDays
        } else {
            spent
        }
        val projectedBalance = balance + income + plannedIncoming -
            projectedSpend - upcomingRecurring - plannedOutgoing

        return Result(
            safeToSpend = safeToSpend,
            budgetRemaining = budgetRemaining,
            upcomingRecurring = upcomingRecurring,
            recurringCount = upcoming.size,
            plannedOutgoing = plannedOutgoing,
            plannedIncoming = plannedIncoming,
            plannedCount = plans.size,
            projectedSpend = projectedSpend,
            projectedBalance = projectedBalance,
            daysLeft = daysLeft,
            hasBudgets = monthBudgets.isNotEmpty()
        )
    }
}
