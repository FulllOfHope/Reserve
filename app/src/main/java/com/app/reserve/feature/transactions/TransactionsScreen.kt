package com.app.reserve.feature.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.reserve.core.database.ToBePaidEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.designsystem.EmptyState
import com.app.reserve.core.designsystem.LocalAmountsHidden
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.MoneyText
import com.app.reserve.core.designsystem.MonthSwitcher
import com.app.reserve.core.designsystem.radius_md
import com.app.reserve.core.designsystem.ReserveDivider
import com.app.reserve.core.designsystem.SheetHandle
import com.app.reserve.core.designsystem.TransactionRow
import com.app.reserve.core.designsystem.formatMoney
import com.app.reserve.core.domain.Dates
import com.app.reserve.feature.ledger.LedgerState
import java.time.LocalDate

private enum class TypeFilter(val label: String, val type: TransactionType?) {
    All("All", null),
    Expense("Expenses", TransactionType.EXPENSE),
    Income("Income", TransactionType.INCOME),
    Transfer("Transfers", TransactionType.TRANSFER),
    Planned("To Be Paid", null)
}

/** Null id = all categories, empty id = uncategorised rows. */
private data class CategoryFilter(val id: String?, val label: String)

private enum class TransactionSort(val label: String, val header: String = "") {
    Newest("Newest"),
    Largest("Largest first", "LARGEST AMOUNT FIRST"),
    Smallest("Smallest first", "SMALLEST AMOUNT FIRST")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: LedgerState,
    onEditTransaction: (TransactionEntity) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToToday: () -> Unit = {},
    swipeToDeleteEnabled: Boolean = false,
    onMarkToBePaid: (String) -> Unit = {},
    onOpenToBePaid: () -> Unit = {}
) {
    val colors = LocalReserveColors.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf(TypeFilter.All) }
    // Stored by name so rotation/process death restores the exact sort choice.
    var sortName by rememberSaveable { mutableStateOf(TransactionSort.Newest.name) }
    val sort = runCatching { TransactionSort.valueOf(sortName) }
        .getOrDefault(TransactionSort.Newest)
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<CategoryFilter?>(null) }
    var selectedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showCategorySheet by remember { mutableStateOf(false) }

    val categoryOptions = remember(state.categories) {
        listOf(CategoryFilter(null, "All categories")) +
            state.categories.map { CategoryFilter(it.id, it.name) } +
            CategoryFilter("", "No category")
    }
    val categoryId = selectedCategory?.id
    val showPlannedOnly = filter == TypeFilter.Planned
    val plannedRows = state.monthToBePaid
        .filter { item ->
            if (query.isBlank()) return@filter true
            val needle = query.trim()
            item.title.contains(needle, ignoreCase = true) ||
                item.note.contains(needle, ignoreCase = true)
        }
        .sortedBy { it.dueAt }

    val filtered = state.monthTransactions
        .filter { filter.type == null || it.type == filter.type }
        .filter { transaction ->
            when {
                selectedCategories.isNotEmpty() ->
                    selectedCategories.any { id ->
                        if (id.isEmpty()) transaction.categoryId == null
                        else transaction.categoryId == id
                    }
                categoryId == null -> true
                categoryId.isEmpty() -> transaction.categoryId == null
                else -> transaction.categoryId == categoryId
            }
        }
        .filter { transaction ->
            if (query.isBlank()) return@filter true
            val needle = query.trim()
            val category = state.categoryFor(transaction.categoryId)?.name.orEmpty()
            val account = state.accountName(transaction.accountId)
            listOf(transaction.note, category, account)
                .any { it.contains(needle, ignoreCase = true) }
        }
        .let { rows ->
            when (sort) {
                TransactionSort.Newest -> rows.sortedByDescending { it.timestamp }
                TransactionSort.Largest -> rows.sortedWith(
                    compareByDescending<TransactionEntity> { it.amount }
                        .thenByDescending { it.timestamp }
                )
                TransactionSort.Smallest -> rows.sortedWith(
                    compareBy<TransactionEntity> { it.amount }
                        .thenByDescending { it.timestamp }
                )
            }
        }

    val grouped = filtered.groupBy { Dates.toLocalDate(it.timestamp) }
        .toSortedMap(compareByDescending<LocalDate> { it })

    val categoryChipLabel = when {
        selectedCategories.size > 1 -> "${selectedCategories.size} categories"
        selectedCategories.size == 1 ->
            categoryOptions.firstOrNull { it.id == selectedCategories.first() }?.label
                ?: "1 category"
        else -> selectedCategory?.label ?: "Category"
    }
    val hasCategoryFilter = selectedCategory != null || selectedCategories.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        MonthSwitcher(
            monthYear = state.monthYear,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
            onToday = onGoToToday
        )
        SearchField(query = query, onQueryChange = { query = it })

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = hasCategoryFilter,
                    onClick = { showCategorySheet = true },
                    label = {
                        Text(
                            text = categoryChipLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (hasCategoryFilter) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colors.surface,
                        labelColor = colors.textLight,
                        selectedContainerColor = colors.primaryContainer,
                        selectedLabelColor = colors.primaryDefault
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = hasCategoryFilter,
                        borderColor = colors.strokeFaint,
                        selectedBorderColor = colors.primaryDefault
                    )
                )
            }
            items(TypeFilter.entries) { option ->
                val selected = filter == option
                FilterChip(
                    selected = selected,
                    onClick = { filter = option },
                    label = {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colors.surface,
                        labelColor = colors.textLight,
                        selectedContainerColor = colors.primaryContainer,
                        selectedLabelColor = colors.primaryDefault
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = colors.strokeFaint,
                        selectedBorderColor = colors.primaryDefault
                    )
                )
            }
            item {
                Box {
                    FilterChip(
                        selected = sort != TransactionSort.Newest,
                        onClick = { sortMenuExpanded = true },
                        label = {
                            Text(
                                "Sort: ${sort.label}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (sort != TransactionSort.Newest) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    }
                                )
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.surface,
                            labelColor = colors.textLight,
                            selectedContainerColor = colors.primaryContainer,
                            selectedLabelColor = colors.primaryDefault
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = sort != TransactionSort.Newest,
                            borderColor = colors.strokeFaint,
                            selectedBorderColor = colors.primaryDefault
                        )
                    )
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false },
                        containerColor = colors.fillLight,
                        shape = RoundedCornerShape(radius_md),
                        tonalElevation = 0.dp
                    ) {
                        TransactionSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        option.label,
                                        color = if (option == sort) {
                                            colors.primaryDefault
                                        } else {
                                            colors.textBase
                                        }
                                    )
                                },
                                leadingIcon = if (option == sort) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = colors.primaryDefault,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    sortName = option.name
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if ((showPlannedOnly && plannedRows.isEmpty()) || (!showPlannedOnly && filtered.isEmpty())) {
            val hasFilters = query.isNotBlank() || filter != TypeFilter.All || hasCategoryFilter
            EmptyState(
                icon = if (hasFilters) Icons.Rounded.SearchOff else Icons.Rounded.ReceiptLong,
                text = if (hasFilters) {
                    "No transactions match these filters"
                } else {
                    "No transactions this month."
                },
                actionLabel = when {
                    showPlannedOnly -> "Add planned"
                    hasFilters -> "Clear filters"
                    else -> null
                },
                onAction = when {
                    showPlannedOnly -> onOpenToBePaid
                    hasFilters -> {
                        {
                            query = ""
                            filter = TypeFilter.All
                            selectedCategory = null
                            selectedCategories = emptySet()
                        }
                    }
                    else -> null
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 108.dp)
            ) {
                if (showPlannedOnly) {
                    item(key = "planned-header") {
                        Text(
                            text = "TO BE PAID · ${state.monthToBePaid.size} PLANNED",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textLighter,
                            modifier = Modifier.padding(
                                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp
                            )
                        )
                    }
                    items(items = plannedRows, key = { it.id }) { item ->
                        PlannedTransactionRow(
                            item = item,
                            onMarkPaid = { onMarkToBePaid(item.id) }
                        )
                    }
                } else if (sort == TransactionSort.Newest) {
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
                                swipeToDeleteEnabled = swipeToDeleteEnabled,
                                onEdit = { onEditTransaction(transaction) },
                                onDelete = { onDeleteTransaction(transaction) }
                            )
                        }
                    }
                } else {
                    item(key = "amount-sort-header") {
                        Text(
                            text = sort.header,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textLighter,
                            modifier = Modifier.padding(
                                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp
                            )
                        )
                    }
                    items(items = filtered, key = { it.id }) { transaction ->
                        SwipeRow(
                            transaction = transaction,
                            state = state,
                            swipeToDeleteEnabled = swipeToDeleteEnabled,
                            onEdit = { onEditTransaction(transaction) },
                            onDelete = { onDeleteTransaction(transaction) }
                        )
                    }
                }
            }
        }
    }

    if (showCategorySheet) {
        CategoryFilterSheet(
            options = categoryOptions,
            selected = selectedCategory,
            multiSelected = selectedCategories,
            onSelect = { option ->
                selectedCategories = emptySet()
                selectedCategory = if (option.id == null) null else option
                showCategorySheet = false
            },
            onToggleMulti = { option ->
                val id = option.id
                if (id == null) {
                    selectedCategories = emptySet()
                    selectedCategory = null
                } else {
                    selectedCategories = if (id in selectedCategories) {
                        selectedCategories - id
                    } else {
                        selectedCategories + id
                    }
                    selectedCategory = null
                }
            },
            onDone = { showCategorySheet = false },
            onDismiss = { showCategorySheet = false }
        )
    }
}

@Composable
private fun PlannedTransactionRow(
    item: ToBePaidEntity,
    onMarkPaid: () -> Unit
) {
    val colors = LocalReserveColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(Dates.fullDate(item.dueAt))
                    if (item.note.isNotBlank()) {
                        append(" · ")
                        append(item.note)
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLighter,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        MoneyText(
            amount = item.amount,
            style = MaterialTheme.typography.titleMedium,
            color = if (item.type == TransactionType.INCOME) {
                colors.primaryDefault
            } else {
                colors.textBase
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CategoryFilterSheet(
    options: List<CategoryFilter>,
    selected: CategoryFilter?,
    multiSelected: Set<String>,
    onSelect: (CategoryFilter) -> Unit,
    onToggleMulti: (CategoryFilter) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalReserveColors.current
    val haptic = LocalHapticFeedback.current
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetHandle()
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Filter by category",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textBase,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap to filter one category. Long press to combine several.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLighter,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            options.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { option ->
                        val isSelected = selected?.id == option.id ||
                            (option.id != null && option.id in multiSelected)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .combinedClickable(
                                    onClick = { onSelect(option) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleMulti(option)
                                    }
                                ),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) colors.primaryContainer else colors.fillLight,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) colors.primaryDefault else colors.strokeFaint
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isSelected) "✓ ${option.label}" else option.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) colors.primaryDefault else colors.textBase,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
            if (multiSelected.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDone) {
                    Text("Show ${multiSelected.size} selected", color = colors.primaryDefault)
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
    // One localised source for Today / Yesterday / date labels.
    val label = Dates.dayHeader(Dates.toTimestamp(date))
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
    swipeToDeleteEnabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalReserveColors.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var confirmDelete by remember { mutableStateOf(false) }

    // A tab swipe should not delete a row. Require a long deliberate drag, and
    // still ask before moving anything to Trash.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && !confirmDelete) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                confirmDelete = true
            }
            false
        },
        positionalThreshold = { totalDistance ->
            (totalDistance * 0.7f).coerceAtLeast(with(density) { 120.dp.toPx() })
        }
    )

    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val rowContent: @Composable () -> Unit = {
        Surface(color = colors.fillBase) {
            Column {
                TransactionRow(
                    transaction = transaction,
                    category = state.categoryFor(transaction.categoryId),
                    accountName = state.accountName(transaction.accountId),
                    onClick = onEdit
                )
                ReserveDivider()
            }
        }
    }

    if (swipeToDeleteEnabled) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .background(colors.warningDefault, RoundedCornerShape(16.dp))
                        .padding(end = 24.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color.White
                    )
                }
            }
        ) {
            rowContent()
        }
    } else {
        rowContent()
    }

    if (swipeToDeleteEnabled && confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.fillLight,
            titleContentColor = colors.textBase,
            textContentColor = colors.textLight,
            title = { Text("Move to Trash?") },
            text = { Text("You can restore this transaction from Trash until it is permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) {
                    Text("Move to Trash", color = colors.warningDefault)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = colors.textLight)
                }
            }
        )
    }
}
