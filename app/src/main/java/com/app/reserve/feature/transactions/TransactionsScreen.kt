package com.app.reserve.feature.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.designsystem.EmptyState
import com.app.reserve.core.designsystem.LocalAmountsHidden
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.MonthSwitcher
import com.app.reserve.core.designsystem.ReserveDivider
import com.app.reserve.core.designsystem.TransactionRow
import com.app.reserve.core.designsystem.formatMoney
import com.app.reserve.core.domain.Dates
import com.app.reserve.feature.ledger.LedgerState
import java.time.LocalDate

private enum class TypeFilter(val label: String, val type: TransactionType?) {
    All("All", null),
    Expense("Expenses", TransactionType.EXPENSE),
    Income("Income", TransactionType.INCOME),
    Transfer("Transfers", TransactionType.TRANSFER)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: LedgerState,
    onEditTransaction: (TransactionEntity) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val colors = LocalReserveColors.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TypeFilter.All) }

    val filtered = state.monthTransactions
        .filter { filter.type == null || it.type == filter.type }
        .filter { transaction ->
            if (query.isBlank()) return@filter true
            val needle = query.trim()
            val category = state.categoryFor(transaction.categoryId)?.name.orEmpty()
            val account = state.accountName(transaction.accountId)
            listOf(transaction.note, category, account)
                .any { it.contains(needle, ignoreCase = true) }
        }
        .sortedByDescending { it.timestamp }

    val grouped = filtered.groupBy { Dates.toLocalDate(it.timestamp) }
        .toSortedMap(compareByDescending<LocalDate> { it })

    Column(modifier = Modifier.fillMaxSize()) {
        MonthSwitcher(
            monthYear = state.monthYear,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth
        )
        SearchField(query = query, onQueryChange = { query = it })

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TypeFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colors.fillLight,
                        labelColor = colors.textLight,
                        selectedContainerColor = colors.primaryDefault.copy(alpha = 0.12f),
                        selectedLabelColor = colors.primaryDefault
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filter == option,
                        borderColor = colors.strokeFaint,
                        selectedBorderColor = colors.primaryDefault
                    )
                )
            }
        }

        if (filtered.isEmpty()) {
            EmptyState(
                icon = if (query.isBlank()) Icons.Rounded.ReceiptLong else Icons.Rounded.SearchOff,
                text = if (query.isBlank()) {
                    "No transactions this month."
                } else {
                    "Nothing matches \"$query\"."
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
            ) {
                grouped.forEach { (date, dayTransactions) ->
                    item(key = "header-$date") {
                        DayHeader(
                            date = date,
                            spent = dayTransactions
                                .filter { it.type == TransactionType.EXPENSE }
                                .sumOf { it.amount }
                        )
                    }
                    items(
                        items = dayTransactions,
                        key = { it.id }
                    ) { transaction ->
                        SwipeRow(
                            transaction = transaction,
                            state = state,
                            onEdit = { onEditTransaction(transaction) },
                            onDelete = { onDeleteTransaction(transaction) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = LocalReserveColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.fillLight,
        border = BorderStroke(1.dp, colors.strokeFaint)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.textLighter,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search notes, categories, accounts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textLighter,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textBase),
                    cursorBrush = SolidColor(colors.primaryDefault),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = colors.textLighter,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, spent: Long) {
    val colors = LocalReserveColors.current
    val amountsHidden = LocalAmountsHidden.current
    val label = when (date) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textLighter,
            modifier = Modifier.weight(1f)
        )
        if (spent > 0) {
            Text(
                text = if (amountsHidden) formatMoney(spent, hidden = true) else "-${formatMoney(spent, hidden = false)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLighter
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(
    transaction: TransactionEntity,
    state: LedgerState,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalReserveColors.current
    val haptic = LocalHapticFeedback.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
                true
            } else {
                false
            }
        }
    )

    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.warningDefault.copy(alpha = 0.10f))
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete",
                    tint = colors.warningDefault
                )
            }
        }
    ) {
        Surface(color = colors.fillBase) {
            Column {
                TransactionRow(
                    transaction = transaction,
                    category = state.categoryFor(transaction.categoryId),
                    onClick = onEdit
                )
                ReserveDivider()
            }
        }
    }
}
