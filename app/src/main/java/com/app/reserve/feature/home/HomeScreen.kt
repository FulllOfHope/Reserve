package com.app.reserve.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.database.ToBePaidEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.designsystem.BudgetBar
import com.app.reserve.core.designsystem.CategoryChip
import com.app.reserve.core.designsystem.EmptyState
import com.app.reserve.core.designsystem.EnteSpacing
import com.app.reserve.core.designsystem.HiddenAmount
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.app.reserve.core.designsystem.EnteButton
import com.app.reserve.core.designsystem.radius_md
import com.app.reserve.core.designsystem.spacing_md
import com.app.reserve.core.designsystem.spacing_sm
import com.app.reserve.core.designsystem.spacing_xxl
import com.app.reserve.core.designsystem.tabular
import com.app.reserve.core.domain.BudgetCalculator
import com.app.reserve.core.domain.Dates
import com.app.reserve.core.domain.MonthForecast
import com.app.reserve.core.domain.Money
import com.app.reserve.feature.ledger.LedgerState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: LedgerState,
    onOpenAdd: () -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit = {},
    onSeeAll: () -> Unit,
    onOpenBudgets: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToToday: () -> Unit = {},
    onOpenMenu: () -> Unit,
    onToggleHideAmounts: (Boolean) -> Unit = {},
    onChooseBalanceAccounts: (Set<String>?) -> Unit = {},
    onSetBalance: (String, Long) -> Unit = { _, _ -> },
    onSetCategory: (TransactionEntity, String) -> Unit = { _, _ -> },
    showSafeToSpend: Boolean = false,
    onOpenToBePaid: () -> Unit = {},
    onAddToBePaid: () -> Unit = {},
    onMarkToBePaid: (String) -> Unit = {}
) {
    val colors = LocalReserveColors.current
    val amountsHidden = LocalAmountsHidden.current
    val haptic = LocalHapticFeedback.current
    val recent = state.monthTransactions.sortedByDescending { it.timestamp }.take(3)
    val budgets = state.monthBudgets
    val upcomingPlans = state.outstandingToBePaid.sortedBy { it.dueAt }.take(3)
    var activeActionSheetTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var categoryPickerTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var showBalanceAccounts by remember { mutableStateOf(false) }
    var showBalanceEditor by remember { mutableStateOf(false) }
    var showForecast by remember { mutableStateOf(false) }
    val forecast = MonthForecast.compute(
        balance = state.totalBalance,
        monthYear = state.monthYear,
        budgets = state.budgets,
        transactions = state.visibleTransactions,
        recurring = state.recurring,
        toBePaid = state.outstandingToBePaid
    )

    Column(modifier = Modifier.fillMaxSize()) {
        MonthSwitcher(
            monthYear = state.monthYear,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
            onToday = onGoToToday
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
                    Column(modifier = Modifier.padding(22.dp)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (state.balanceAccounts.isEmpty()) {
                                                showBalanceAccounts = true
                                            } else {
                                                showBalanceEditor = true
                                            }
                                        }
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "CURRENT BALANCE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textLighter
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                MoneyText(
                                    amount = state.totalBalance,
                                    style = MaterialTheme.typography.displayLarge.tabular().copy(
                                        fontSize = 36.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                                    ),
                                    color = colors.textBase,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = buildString {
                                        append(Dates.monthLabel(state.monthYear))
                                        val accounts = state.balanceAccounts.size
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
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (state.balanceAccounts.isEmpty()) {
                                        "Choose accounts to edit balances"
                                    } else {
                                        "Long press to correct balance"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textLighter,
                                    textAlign = TextAlign.Center
                                )
                            }
                            IconButton(
                                onClick = { showBalanceAccounts = true },
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Choose balance accounts",
                                    tint = colors.textLight
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.strokeFaint)
                        )
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
                        Spacer(modifier = Modifier.height(16.dp))
                        if (showSafeToSpend) {
                            Surface(
                                onClick = { showForecast = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(radius_md),
                                color = colors.fillLight,
                                border = BorderStroke(1.dp, colors.strokeFaint)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = spacing_md, vertical = spacing_sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "SAFE TO SPEND",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textLighter
                                        )
                                        MoneyText(
                                            amount = forecast.safeToSpend,
                                            style = MaterialTheme.typography.titleMedium.tabular(),
                                            color = colors.primaryDefault
                                        )
                                    }
                                    Text(
                                        text = if (Dates.isCurrentMonth(state.monthYear)) {
                                            "${forecast.daysLeft} days left"
                                        } else {
                                            "View forecast"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textLighter
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textLighter,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
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
                        val pace = BudgetCalculator.pace(budget, state.visibleTransactions)
                        if (pace.isCurrentMonth) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when {
                                    pace.isAhead ->
                                        "${formatMoney(pace.delta, amountsHidden)} ahead of pace"
                                    pace.delta < 0L ->
                                        "${formatMoney(-pace.delta, amountsHidden)} under pace"
                                    else -> "Right on pace"
                                },
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = if (pace.isAhead) colors.warningDefault else colors.primaryDefault
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                SectionLabel(
                    text = "To Be Paid",
                    actionLabel = if (upcomingPlans.isEmpty()) "Add" else "See all",
                    onAction = if (upcomingPlans.isEmpty()) onAddToBePaid else onOpenToBePaid
                )
            }

            item {
                ReserveCard(
                    modifier = Modifier.padding(horizontal = EnteSpacing.xxl),
                    onClick = onOpenToBePaid
                ) {
                    if (upcomingPlans.isEmpty()) {
                        Text(
                            text = "No upcoming one-off payments. Add a bill or a planned receipt here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textLight
                        )
                    } else {
                        upcomingPlans.forEachIndexed { index, item ->
                            ToBePaidSummaryRow(
                                item = item,
                                onMarkPaid = { onMarkToBePaid(item.id) }
                            )
                            if (index != upcomingPlans.lastIndex) ReserveDivider()
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
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
                    Column {
                        TransactionRow(
                            transaction = transaction,
                            category = state.categoryFor(transaction.categoryId),
                            accountName = state.accountName(transaction.accountId),
                            onClick = { activeActionSheetTransaction = transaction }
                        )
                        if (transaction != recent.last()) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
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

    if (showBalanceAccounts) {
        BalanceAccountsSheet(
            accounts = state.activeAccounts,
            selectedIds = state.balanceAccountIds,
            onApply = { ids ->
                onChooseBalanceAccounts(ids)
                showBalanceAccounts = false
            },
            onDismiss = { showBalanceAccounts = false }
        )
    }

    if (showBalanceEditor) {
        CurrentBalanceEditorSheet(
            accounts = state.balanceAccounts,
            onSave = { changes ->
                changes.forEach { (accountId, amount) -> onSetBalance(accountId, amount) }
                showBalanceEditor = false
            },
            onDismiss = { showBalanceEditor = false }
        )
    }

    if (showSafeToSpend && showForecast) {
        MonthForecastSheet(
            forecast = forecast,
            hasBudgets = forecast.hasBudgets,
            monthYear = state.monthYear,
            onDismiss = { showForecast = false }
        )
    }
}

@Composable
private fun ToBePaidSummaryRow(
    item: ToBePaidEntity,
    onMarkPaid: () -> Unit
) {
    val colors = LocalReserveColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing_sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = false,
            onCheckedChange = { checked -> if (checked) onMarkPaid() },
            colors = CheckboxDefaults.colors(
                checkedColor = colors.primaryDefault,
                uncheckedColor = colors.textLighter
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textBase,
                maxLines = 1
            )
            Text(
                text = buildString {
                    append(Dates.fullDate(item.dueAt))
                    append(" · ")
                    append(if (item.type == com.app.reserve.core.database.TransactionType.INCOME) {
                        "Expected income"
                    } else {
                        "Planned payment"
                    })
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLighter
            )
        }
        MoneyText(
            amount = item.amount,
            style = MaterialTheme.typography.titleSmall.tabular(),
            color = if (item.type == com.app.reserve.core.database.TransactionType.INCOME) {
                colors.primaryDefault
            } else {
                colors.textBase
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthForecastSheet(
    forecast: MonthForecast.Result,
    hasBudgets: Boolean,
    monthYear: String,
    onDismiss: () -> Unit
) {
    val colors = LocalReserveColors.current
    val amountsHidden = LocalAmountsHidden.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentWindowInsets = { WindowInsets.ime },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing_xxl)
                .navigationBarsPadding()
        ) {
            SheetHandle()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Month forecast · ${Dates.monthLabel(monthYear)}",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textBase
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "A guide to what you can safely spend. It stays on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textLight
            )
            Spacer(modifier = Modifier.height(16.dp))
            ReserveCard {
                Column(modifier = Modifier.padding(spacing_md)) {
                    ForecastRow("Safe to spend", forecast.safeToSpend, colors.primaryDefault)
                    ReserveDivider()
                    ForecastRow("Left in budgets", forecast.budgetRemaining, colors.textBase)
                    ReserveDivider()
                    ForecastRow(
                        if (forecast.recurringCount == 1) {
                            "Upcoming recurring rule"
                        } else {
                            "Upcoming recurring rules (${forecast.recurringCount})"
                        },
                        forecast.upcomingRecurring,
                        colors.textBase
                    )
                    if (forecast.plannedCount > 0) {
                        ReserveDivider()
                        ForecastRow(
                            if (forecast.plannedCount == 1) {
                                "To Be Paid"
                            } else {
                                "To Be Paid (${forecast.plannedCount})"
                            },
                            forecast.plannedOutgoing,
                            colors.warningDefault
                        )
                        if (forecast.plannedIncoming > 0L) {
                            ReserveDivider()
                            ForecastRow(
                                "Planned income (not yet spent)",
                                forecast.plannedIncoming,
                                colors.primaryDefault
                            )
                        }
                    }
                    ReserveDivider()
                    ForecastRow("Spend at this pace", forecast.projectedSpend, colors.textBase)
                    ReserveDivider()
                    ForecastRow("Projected balance", forecast.projectedBalance, colors.textBase)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = buildString {
                    append("Safe to spend starts with the cash you have and the budget you have left, then subtracts recurring expenses and To Be Paid items still due this month. ")
                    if (!hasBudgets) {
                        append("With no budgets set, leftover income is used instead. ")
                    }
                    append("Planned reminders never change your balance until you mark them paid. Planned income is not spent before it arrives.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textLighter
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ForecastRow(label: String, value: Long, color: androidx.compose.ui.graphics.Color) {
    val colors = LocalReserveColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = spacing_sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textLight,
            modifier = Modifier.weight(1f)
        )
        MoneyText(amount = value, style = MaterialTheme.typography.titleMedium.tabular(), color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BalanceAccountsSheet(
    accounts: List<AccountEntity>,
    selectedIds: Set<String>?,
    onApply: (Set<String>?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalReserveColors.current
    var useAll by remember(selectedIds) { mutableStateOf(selectedIds == null) }
    var chosen by remember(selectedIds) {
        mutableStateOf(selectedIds ?: accounts.map { it.id }.toSet())
    }

    fun toggleAccount(id: String) {
        if (useAll) {
            useAll = false
            chosen = setOf(id)
        } else {
            chosen = if (id in chosen) chosen - id else chosen + id
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentWindowInsets = { WindowInsets.ime },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetHandle()
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Accounts in balance",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textBase,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Choose which accounts are combined into Current balance.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textLight,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Surface(
                onClick = { useAll = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (useAll) colors.primaryContainer else colors.fillLight,
                border = BorderStroke(1.dp, if (useAll) colors.primaryDefault else colors.strokeFaint)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useAll,
                        onCheckedChange = { useAll = true },
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.primaryDefault,
                            uncheckedColor = colors.textLighter,
                            checkmarkColor = colors.textReverse
                        )
                    )
                    Text(
                        text = "All accounts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textBase
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            if (accounts.isEmpty()) {
                Text(
                    text = "No active accounts yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textLighter,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            accounts.forEach { account ->
                val accountSelected = !useAll && account.id in chosen
                Surface(
                    onClick = { toggleAccount(account.id) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.fillLight
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = accountSelected,
                            onCheckedChange = { toggleAccount(account.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.primaryDefault,
                                uncheckedColor = colors.textLighter,
                                checkmarkColor = colors.textReverse
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textBase,
                            modifier = Modifier.weight(1f)
                        )
                        MoneyText(
                            amount = account.balance,
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = colors.textLight
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        val validIds = accounts.map { it.id }.toSet()
                        onApply(if (useAll) null else chosen.intersect(validIds))
                    }
                ) {
                    Text("Done", color = colors.primaryDefault)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrentBalanceEditorSheet(
    accounts: List<AccountEntity>,
    onSave: (List<Pair<String, Long>>) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalReserveColors.current
    var values by remember(accounts) {
        mutableStateOf(accounts.associate { it.id to Money.format(it.balance) })
    }
    val changes = accounts.mapNotNull { account ->
        val parsed = Money.parse(values[account.id].orEmpty())
        if (parsed != null && parsed != account.balance) account.id to parsed else null
    }
    val allValid = values.values.all { Money.parse(it) != null }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentWindowInsets = { WindowInsets.ime },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            SheetHandle()
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Correct current balance",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.textBase
            )
            Spacer(Modifier.height(spacing_sm))
            Text(
                text = "Adjusts each selected account's opening balance. No transaction is added and past rows stay intact.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textLighter
            )
            Spacer(Modifier.height(spacing_md))

            accounts.forEach { account ->
                val raw = values[account.id].orEmpty()
                OutlinedTextField(
                    value = raw,
                    onValueChange = { values = values + (account.id to it) },
                    label = { Text(account.name) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = Money.parse(raw) == null,
                    shape = RoundedCornerShape(radius_md),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primaryDefault,
                        unfocusedBorderColor = colors.strokeFaint,
                        focusedTextColor = colors.textBase,
                        unfocusedTextColor = colors.textBase,
                        focusedLabelColor = colors.textLight,
                        unfocusedLabelColor = colors.textLighter,
                        cursorColor = colors.primaryDefault
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(spacing_sm))
            }

            EnteButton(
                text = "Save balances",
                enabled = allValid && changes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSave(changes) }
            )
            Spacer(Modifier.height(spacing_sm))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Cancel", color = colors.textLight)
            }
        }
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

/** Opens from the top bar. Month navigation here is local to the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingCalendarSheet(
    state: LedgerState,
    onDismiss: () -> Unit
) {
    val colors = LocalReserveColors.current
    val amountsHidden = LocalAmountsHidden.current
    var sheetMonth by remember { mutableStateOf(state.monthYear) }

    val daySpend = remember(state.visibleTransactions, sheetMonth) {
        BudgetCalculator.transactionsInMonth(state.visibleTransactions, sheetMonth)
            .filter { it.type == com.app.reserve.core.database.TransactionType.EXPENSE }
            .groupBy { Dates.toLocalDate(it.timestamp) }
            .mapValues { (_, rows) -> rows.sumOf { it.amount } }
    }
    val monthTotal = daySpend.values.sum()
    val firstDay = Dates.monthDate(sheetMonth)
    val leadingBlanks = firstDay.dayOfWeek.value % 7 // Sunday-first grid
    val daysInMonth = firstDay.lengthOfMonth()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentWindowInsets = { WindowInsets.ime },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing_xxl)
                .navigationBarsPadding()
        ) {
            SheetHandle()
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { sheetMonth = Dates.shiftMonth(sheetMonth, -1) }) {
                    Icon(
                        Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous month",
                        tint = colors.textBase
                    )
                }
                if (!Dates.isCurrentMonth(sheetMonth)) {
                    TextButton(
                        onClick = { sheetMonth = Dates.currentMonthKey() },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryDefault),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            "${Dates.monthLabel(sheetMonth)} · Today",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.primaryDefault
                        )
                    }
                } else {
                    Text(
                        text = Dates.monthLabel(sheetMonth),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textBase,
                        modifier = Modifier.weight(1f)
                    )
                }
                IconButton(onClick = { sheetMonth = Dates.shiftMonth(sheetMonth, 1) }) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = "Next month",
                        tint = colors.textBase
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLighter,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val cells = leadingBlanks + daysInMonth
            val rows = (cells + 6) / 7
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val day = row * 7 + col - leadingBlanks + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day in 1..daysInMonth) {
                                val date = Dates.fromMonthAndDay(sheetMonth, day)
                                val spend = daySpend[date] ?: 0L
                                val isToday = date == Dates.today()
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(radius_md))
                                        .background(
                                            if (isToday) colors.primaryContainer else colors.fillLight
                                        )
                                ) {
                                    Text(
                                        text = "$day",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isToday) colors.primaryDefault else colors.textBase
                                    )
                                    if (spend > 0L) {
                                        Text(
                                            text = if (amountsHidden) HiddenAmount else compactDaySpend(spend),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = colors.textLighter,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Total spent",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textLight,
                    modifier = Modifier.weight(1f)
                )
                MoneyText(
                    amount = monthTotal,
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    color = colors.textBase
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Expenses only. Transfers move money between your own accounts and are never counted.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textLighter
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun compactDaySpend(minor: Long): String {
    val rupees = minor / 100L
    return when {
        rupees >= 10_000_000L -> "${rupees / 10_000_000L}C"
        rupees >= 100_000L -> "${rupees / 100_000L}L"
        rupees >= 1_000L -> "${rupees / 1_000L}k"
        else -> "$rupees"
    }
}
