package com.app.reserve.feature.home

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.designsystem.BudgetBar
import com.app.reserve.core.designsystem.EmptyState
import com.app.reserve.core.designsystem.EnteSpacing
import com.app.reserve.core.designsystem.LocalAmountsHidden
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.MoneyText
import com.app.reserve.core.designsystem.MonthSwitcher
import com.app.reserve.core.designsystem.SectionLabel
import com.app.reserve.core.designsystem.TransactionActionSheet
import com.app.reserve.core.designsystem.TransactionRow
import com.app.reserve.core.designsystem.formatMoney
import com.app.reserve.core.designsystem.tabular
import com.app.reserve.core.domain.BudgetCalculator
import com.app.reserve.core.domain.Dates
import com.app.reserve.feature.ledger.LedgerState

@Composable
fun HomeScreen(
    state: LedgerState,
    onOpenAdd: () -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit = {},
    onSeeAll: () -> Unit,
    onOpenBudgets: () -> Unit,
    onPreviousMonth: () -> Unit,
    onOpenMenu: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleHideAmounts: (Boolean) -> Unit = {}
) {
    val colors = LocalReserveColors.current
    val amountsHidden = LocalAmountsHidden.current
    val recent = state.monthTransactions.sortedByDescending { it.timestamp }.take(4)
    val budgets = state.monthBudgets
    var activeActionSheetTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
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
                // Borderless, spacious hero. Privacy mode masks the amount.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = EnteSpacing.lg, vertical = 32.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CURRENT BALANCE",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textLighter,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onToggleHideAmounts(!amountsHidden) }) {
                            Icon(
                                imageVector = if (amountsHidden) {
                                    Icons.Rounded.Visibility
                                } else {
                                    Icons.Rounded.VisibilityOff
                                },
                                contentDescription = if (amountsHidden) "Show amounts" else "Hide amounts",
                                tint = colors.textLight
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MoneyText(
                        amount = state.totalBalance,
                        style = MaterialTheme.typography.displayLarge.tabular(),
                        color = colors.textBase,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = buildString {
                            append(Dates.monthLabel(state.monthYear))
                            val accounts = state.activeAccounts.size
                            if (accounts > 0) {
                                append(" · ")
                                append(accounts)
                                append(if (accounts == 1) " account" else " accounts")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textLight
                    )
                }

                // One airy text row instead of three separate stat cards.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = EnteSpacing.lg, vertical = EnteSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(EnteSpacing.lg)
                ) {
                    HeroStat(
                        label = "SPENT",
                        value = state.spent,
                        color = colors.textBase,
                        modifier = Modifier.weight(1f)
                    )
                    HeroStat(
                        label = "INCOME",
                        value = state.income,
                        color = colors.primaryDefault,
                        modifier = Modifier.weight(1f)
                    )
                    HeroStat(
                        label = "NET",
                        value = state.net,
                        color = if (state.net < 0) colors.warningDefault else colors.textBase,
                        modifier = Modifier.weight(1f)
                    )
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
                    Text(
                        text = "Set a monthly budget",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textLight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenBudgets)
                            .padding(horizontal = EnteSpacing.lg, vertical = EnteSpacing.md)
                    )
                }
            } else {
                items(budgets.take(2), key = { it.id }) { budget ->
                    val progress = BudgetCalculator.progress(budget, state.visibleTransactions)
                    val name = budget.categoryId
                        ?.let { state.categoryFor(it)?.name }
                        ?: "All expenses"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EnteSpacing.lg, vertical = EnteSpacing.sm)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textLight,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${formatMoney(progress.spent, amountsHidden)} of ${formatMoney(budget.amount, amountsHidden)}",
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = colors.textLighter
                            )
                        }
                        Spacer(modifier = Modifier.height(EnteSpacing.sm))
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
                        onClick = { activeActionSheetTransaction = transaction }
                    )
                }
            }
        }
    }

    activeActionSheetTransaction?.let { transaction ->
        TransactionActionSheet(
            transaction = transaction,
            onEdit = { onEditTransaction(transaction) },
            onDuplicate = {
                onEditTransaction(
                    transaction.copy(id = java.util.UUID.randomUUID().toString())
                )
            },
            onDelete = { onDeleteTransaction(transaction) },
            onDismiss = { activeActionSheetTransaction = null }
        )
    }
}

@Composable
private fun HeroStat(
    label: String,
    value: Long,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalReserveColors.current
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textLighter
        )
        Spacer(modifier = Modifier.height(EnteSpacing.sm))
        MoneyText(
            amount = value,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = color,
            maxLines = 1
        )
    }
}
