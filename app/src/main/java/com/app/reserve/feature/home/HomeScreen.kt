package com.app.reserve.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.designsystem.BudgetBar
import com.app.reserve.core.designsystem.CategoryChip
import com.app.reserve.core.designsystem.EmptyState
import com.app.reserve.core.designsystem.EnteSpacing
import com.app.reserve.core.designsystem.LocalAmountsHidden
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.MoneyText
import com.app.reserve.core.designsystem.MonthSwitcher
import com.app.reserve.core.designsystem.ReserveCard
import com.app.reserve.core.designsystem.ReserveDivider
import com.app.reserve.core.designsystem.SectionLabel
import com.app.reserve.core.designsystem.SheetHandle
import com.app.reserve.core.designsystem.TransactionActionSheet
import com.app.reserve.core.designsystem.TransactionRow
import com.app.reserve.core.designsystem.formatMoney
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onToggleHideAmounts: (Boolean) -> Unit = {},
    onSetCategory: (TransactionEntity, String) -> Unit = { _, _ -> }
) {
    val colors = LocalReserveColors.current
    val amountsHidden = LocalAmountsHidden.current
    val recent = state.monthTransactions.sortedByDescending { it.timestamp }.take(5)
    val budgets = state.monthBudgets
    var activeActionSheetTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var categoryPickerTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        MonthSwitcher(
            monthYear = state.monthYear,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 108.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(EnteSpacing.md))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = EnteSpacing.xxl),
                    shape = RoundedCornerShape(24.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.strokeFaint),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "CURRENT BALANCE",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textLighter
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            MoneyText(
                                amount = state.totalBalance,
                                style = MaterialTheme.typography.displayLarge.tabular().copy(
                                    fontSize = 32.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                                ),
                                color = colors.textBase,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
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
                                color = colors.textLight,
                                textAlign = TextAlign.Center
                            )
                        }
                        IconButton(
                            onClick = { onToggleHideAmounts(!amountsHidden) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
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
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MiniStat(
                            label = "Spent",
                            value = state.spent,
                            color = colors.textBase,
                            modifier = Modifier.weight(1f)
                        )
                        MiniStat(
                            label = "Income",
                            value = state.income,
                            color = colors.primaryDefault,
                            modifier = Modifier.weight(1f)
                        )
                        MiniStat(
                            label = "Net",
                            value = state.net,
                            color = if (state.net < 0) colors.warningDefault else colors.textBase,
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
                        modifier = Modifier.padding(horizontal = EnteSpacing.xxl),
                        onClick = onOpenBudgets
                    ) {
                        Text(
                            text = "Set a monthly budget",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textBase
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Give a category a limit and Reserve will track it here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textLight
                        )
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
                            .padding(horizontal = EnteSpacing.xxl, vertical = EnteSpacing.sm)
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
                        onClick = { activeActionSheetTransaction = transaction }
                    )
                    if (transaction != recent.last()) {
                        ReserveDivider()
                    }
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
            onCategory = { categoryPickerTransaction = transaction },
            onDelete = { onDeleteTransaction(transaction) },
            onDismiss = { activeActionSheetTransaction = null }
        )
    }

    categoryPickerTransaction?.let { transaction ->
        CategoryPickerSheet(
            categories = state.expensesFor(transaction.type).ifEmpty { state.categories },
            selectedId = transaction.categoryId,
            onSelect = { id ->
                onSetCategory(transaction, id)
                categoryPickerTransaction = null
            },
            onDismiss = { categoryPickerTransaction = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPickerSheet(
    categories: List<com.app.reserve.core.database.CategoryEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalReserveColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentWindowInsets = { WindowInsets.ime },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetHandle()
            Spacer(Modifier.height(20.dp))
            Text(
                "Change category",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textBase,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            categories.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { category ->
                        CategoryChip(
                            category = category,
                            selected = category.id == selectedId,
                            onClick = { onSelect(category.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
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
            color = colors.textLighter
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
