package com.app.reserve.feature.budgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.app.reserve.core.database.BudgetEntity
import com.app.reserve.core.designsystem.BudgetBar
import com.app.reserve.core.designsystem.EmptyState
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.MoneyText
import com.app.reserve.core.designsystem.MonthSwitcher
import com.app.reserve.core.designsystem.ReserveCard
import com.app.reserve.core.designsystem.ScreenTitle
import com.app.reserve.core.designsystem.SectionLabel
import com.app.reserve.core.designsystem.tabular
import com.app.reserve.core.domain.BudgetCalculator
import com.app.reserve.core.domain.Dates
import com.app.reserve.core.domain.Money
import com.app.reserve.feature.ledger.LedgerState
import java.util.UUID

@Composable
fun BudgetsScreen(
    state: LedgerState,
    onSaveBudget: (BudgetEntity) -> Unit,
    onDeleteBudget: (String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalReserveColors.current
    var editing by remember { mutableStateOf<BudgetEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    val budgets = state.monthBudgets
    val expenseCategories = state.categories.filter {
        it.type == com.app.reserve.core.database.TransactionType.EXPENSE
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle(
            title = "Budgets",
            onBack = onBack,
            trailing = {
                IconButton(onClick = { adding = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add budget", tint = colors.primaryDefault)
                }
            }
        )
        MonthSwitcher(
            monthYear = state.monthYear,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp, top = 8.dp)
        ) {
            item {
                ReserveCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Strict monthly budgets",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textBase
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Limits reset every month and never roll over. Transfers are excluded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textLight
                        )
                    }
                }
            }

            item { SectionLabel(text = "This month") }

            if (budgets.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.PieChart,
                        text = "No budgets for ${Dates.monthLabel(state.monthYear)}. Set a limit to track your spending.",
                        actionLabel = "Set a budget",
                        onAction = { adding = true }
                    )
                }
            } else {
                items(budgets, key = { it.id }) { budget ->
                    val progress = BudgetCalculator.progress(budget, state.visibleTransactions)
                    val name = budget.categoryId
                        ?.let { state.categoryFor(it)?.name }
                        ?: "All expenses"
                    BudgetRow(
                        name = name,
                        spent = progress.spent,
                        limit = budget.amount,
                        isOver = progress.isOver,
                        onClick = { editing = budget },
                        onDelete = { onDeleteBudget(budget.id) }
                    )
                }
            }
        }
    }

    if (adding || editing != null) {
        BudgetEditorDialog(
            existing = editing,
            categories = expenseCategories.map { it.id to it.name },
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { categoryId, amount ->
                val existingId = state.budgets
                    .firstOrNull { it.monthYear == state.monthYear && it.categoryId == categoryId }
                    ?.id
                onSaveBudget(
                    BudgetEntity(
                        id = editing?.id ?: existingId ?: UUID.randomUUID().toString(),
                        categoryId = categoryId,
                        amount = amount,
                        monthYear = state.monthYear
                    )
                )
                adding = false
                editing = null
            }
        )
    }
}

@Composable
private fun BudgetRow(
    name: String,
    spent: Long,
    limit: Long,
    isOver: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalReserveColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textBase,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isOver) {
                    "${Money.format(spent - limit)} over"
                } else {
                    "${Money.format(limit - spent)} left"
                },
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = if (isOver) colors.warningDefault else colors.textLighter
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete budget",
                    tint = colors.textLighter,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        BudgetBar(spent = spent, limit = limit)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${Money.format(spent)} of ${Money.format(limit)}",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = colors.textLight
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${((if (limit <= 0L) 0f else spent.toFloat() / limit.toFloat()) * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = if (isOver) colors.warningDefault else colors.textLighter
            )
        }
    }
}

@Composable
private fun BudgetEditorDialog(
    existing: BudgetEntity?,
    categories: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (categoryId: String?, amount: Long) -> Unit
) {
    val colors = LocalReserveColors.current
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var amountText by remember {
        mutableStateOf(existing?.amount?.let { if (it % 100L == 0L) (it / 100L).toString() else Money.format(it).removePrefix("₹").replace(",", "") } ?: "")
    }
    val parsed = Money.parse(amountText)
    val valid = parsed != null && parsed > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.fillLight,
        titleContentColor = colors.textBase,
        textContentColor = colors.textLight,
        title = { Text(if (existing == null) "New budget" else "Edit budget") },
        text = {
            Column {
                Text(
                    text = "Applies to",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textLighter
                )
                Spacer(modifier = Modifier.height(8.dp))
                CategoryChoice(
                    label = "All expenses",
                    selected = categoryId == null,
                    onClick = { categoryId = null }
                )
                categories.forEach { (id, name) ->
                    CategoryChoice(
                        label = name,
                        selected = categoryId == id,
                        onClick = { categoryId = id }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monthly limit") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountText.isNotBlank() && !valid,
                    supportingText = if (amountText.isNotBlank() && !valid) {
                        { Text("Enter an amount greater than zero") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primaryDefault,
                        unfocusedBorderColor = colors.strokeFaint,
                        focusedTextColor = colors.textBase,
                        unfocusedTextColor = colors.textBase,
                        focusedLabelColor = colors.textLight,
                        unfocusedLabelColor = colors.textLighter,
                        cursorColor = colors.primaryDefault
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onSave(categoryId, parsed!!) },
                enabled = valid
            ) {
                Text("Save", color = if (valid) colors.primaryDefault else colors.textLighter)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textLight)
            }
        }
    )
}

@Composable
private fun CategoryChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalReserveColors.current
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.primaryDefault.copy(alpha = 0.12f) else colors.fillLight,
        border = BorderStroke(1.dp, if (selected) colors.primaryDefault else colors.strokeFaint)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) colors.primaryDefault else colors.textBase,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
