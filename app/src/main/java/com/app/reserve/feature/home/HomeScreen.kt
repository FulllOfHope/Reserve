package com.app.reserve.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.designsystem.BudgetBar
import com.app.reserve.core.designsystem.EmptyState
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.MoneyText
import com.app.reserve.core.designsystem.MonthSwitcher
import com.app.reserve.core.designsystem.ReserveCard
import com.app.reserve.core.designsystem.ReserveDivider
import com.app.reserve.core.designsystem.ScreenTitle
import com.app.reserve.core.designsystem.SectionLabel
import com.app.reserve.core.designsystem.TransactionRow
import com.app.reserve.core.designsystem.tabular
import com.app.reserve.core.domain.BudgetCalculator
import com.app.reserve.core.domain.Dates
import com.app.reserve.feature.ledger.LedgerState

@Composable
fun HomeScreen(
    state: LedgerState,
    onOpenAdd: () -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit,
    onSeeAll: () -> Unit,
    onOpenBudgets: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val colors = LocalReserveColors.current
    val recent = state.monthTransactions.sortedByDescending { it.timestamp }.take(5)
    val budgets = state.monthBudgets

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle(title = "Reserve")
        MonthSwitcher(
            monthYear = state.monthYear,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ReserveCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "SPENT IN ${Dates.monthLabel(state.monthYear).uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textLow
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        MoneyText(
                            amount = state.spent,
                            style = MaterialTheme.typography.displayLarge.tabular(),
                            color = colors.textHigh
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            MiniStat(
                                label = "Income",
                                value = state.income,
                                color = colors.income,
                                modifier = Modifier.weight(1f)
                            )
                            MiniStat(
                                label = "Net",
                                value = state.net,
                                color = if (state.net < 0) colors.expense else colors.textHigh,
                                modifier = Modifier.weight(1f)
                            )
                            MiniStat(
                                label = "Balance",
                                value = state.totalBalance,
                                color = colors.textHigh,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                SectionLabel(
                    text = "Budgets",
                    actionLabel = "Manage",
                    onAction = onOpenBudgets
                )
            }

            if (budgets.isEmpty()) {
                item {
                    ReserveCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onClick = onOpenBudgets
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Set a monthly budget",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textHigh
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Give a category a limit and Reserve will track it here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMed
                            )
                        }
                    }
                }
            } else {
                items(budgets.take(3), key = { it.id }) { budget ->
                    val progress = BudgetCalculator.progress(budget, state.visibleTransactions)
                    val name = budget.categoryId
                        ?.let { state.categoryFor(it)?.name }
                        ?: "All expenses"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMed,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${com.app.reserve.core.domain.Money.format(progress.spent)} of ${com.app.reserve.core.domain.Money.format(budget.amount)}",
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = colors.textLow
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        BudgetBar(spent = progress.spent, limit = budget.amount)
                    }
                }
            }

            item {
                SectionLabel(
                    text = "Recent",
                    actionLabel = if (recent.isEmpty()) null else "See all",
                    onAction = onSeeAll
                )
            }

            if (recent.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.ReceiptLong,
                        text = "Nothing logged this month. Add your first expense to start.",
                        actionLabel = "Add expense",
                        onAction = onOpenAdd
                    )
                }
            } else {
                items(recent, key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        category = state.categoryFor(transaction.categoryId),
                        accountName = state.accountName(transaction.accountId),
                        onClick = { onEditTransaction(transaction) }
                    )
                    if (transaction != recent.last()) {
                        ReserveDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: Long,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalReserveColors.current
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textLow
        )
        Spacer(modifier = Modifier.height(4.dp))
        MoneyText(
            amount = value,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = color,
            maxLines = 1
        )
    }
}
