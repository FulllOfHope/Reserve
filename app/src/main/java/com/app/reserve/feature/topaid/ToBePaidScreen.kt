package com.app.reserve.feature.topaid

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.reserve.core.database.ToBePaidEntity
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.designsystem.EmptyState
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.MoneyText
import com.app.reserve.core.designsystem.ReserveDivider
import com.app.reserve.core.designsystem.ScreenTitle
import com.app.reserve.core.designsystem.SectionLabel
import com.app.reserve.core.designsystem.tabular
import com.app.reserve.core.domain.Dates
import com.app.reserve.core.domain.Money
import com.app.reserve.feature.ledger.LedgerState
import java.time.LocalDate
import java.util.UUID

/**
 * One-off future movements. Rows here are only reminders: they appear in the
 * forecast but never post to an account until they are marked paid or linked to
 * an imported SMS.
 */
@Composable
fun ToBePaidScreen(
    state: LedgerState,
    onSave: (ToBePaidEntity) -> Unit,
    onDelete: (String) -> Unit,
    onMarkPaid: (String) -> Unit,
    onBack: () -> Unit,
    startAdding: Boolean = false
) {
    val colors = LocalReserveColors.current
    var showEditor by remember { mutableStateOf(startAdding) }
    var editing by remember { mutableStateOf<ToBePaidEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<ToBePaidEntity?>(null) }
    val plans = state.outstandingToBePaid.sortedBy { it.dueAt }
    val plannedOutgoing = plans.filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amount }

    fun openAdd() {
        editing = null
        showEditor = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle(
            title = "To Be Paid",
            onBack = onBack,
            trailing = {
                IconButton(onClick = { openAdd() }) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Add planned payment",
                        tint = colors.primaryDefault
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 108.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.fillLight,
                    border = BorderStroke(1.dp, colors.strokeFaint)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Planned, not posted",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textBase
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bills and expected receipts stay out of your balance. They reduce Safe to Spend until you tick them off, then exactly one transaction is created.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textLight
                        )
                        if (plannedOutgoing > 0L) {
                            Spacer(modifier = Modifier.height(12.dp))
                            MoneyText(
                                amount = plannedOutgoing,
                                style = MaterialTheme.typography.titleLarge.tabular(),
                                color = colors.warningDefault
                            )
                            Text(
                                text = "planned payments still ahead",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLighter
                            )
                        }
                    }
                }
            }

            item { SectionLabel(text = "Upcoming") }

            if (plans.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Repeat,
                        text = "Nothing planned yet. Add a one-off bill or an expected receipt.",
                        actionLabel = "Add planned payment",
                        onAction = { openAdd() }
                    )
                }
            } else {
                items(plans, key = { it.id }) { item ->
                    ToBePaidRow(
                        item = item,
                        categoryName = item.categoryId?.let { state.categoryFor(it)?.name },
                        accountName = state.accountName(item.accountId),
                        onClick = {
                            editing = item
                            showEditor = true
                        },
                        onMarkPaid = { onMarkPaid(item.id) },
                        onDelete = { pendingDelete = item }
                    )
                    ReserveDivider()
                }
            }
        }
    }

    if (showEditor) {
        ToBePaidEditorDialog(
            state = state,
            existing = editing,
            onDismiss = {
                showEditor = false
                editing = null
            },
            onSave = { saved ->
                onSave(saved)
                showEditor = false
                editing = null
            },
            onDelete = if (editing != null) {
                {
                    editing?.let { onDelete(it.id) }
                    showEditor = false
                    editing = null
                }
            } else null
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = colors.fillLight,
            titleContentColor = colors.textBase,
            textContentColor = colors.textLight,
            title = { Text("Delete ${item.title}?") },
            text = { Text("No transaction has been created for this reminder, so nothing will change in your ledger.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(item.id)
                    pendingDelete = null
                }) {
                    Text("Delete", color = colors.warningDefault)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Keep", color = colors.textLight)
                }
            }
        )
    }
}

@Composable
private fun ToBePaidRow(
    item: ToBePaidEntity,
    categoryName: String?,
    accountName: String,
    onClick: () -> Unit,
    onMarkPaid: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalReserveColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
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
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textBase,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(Dates.fullDate(item.dueAt))
                    append(" · ")
                    append(categoryName ?: if (item.type == TransactionType.INCOME) {
                        "Income"
                    } else {
                        "Expense"
                    })
                    append(" · ")
                    append(accountName)
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLighter,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.note.isNotBlank()) {
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        MoneyText(
            amount = if (item.type == TransactionType.EXPENSE) -item.amount else item.amount,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = if (item.type == TransactionType.INCOME) colors.primaryDefault else colors.textBase
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Delete reminder",
                tint = colors.warningDefault,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToBePaidEditorDialog(
    state: LedgerState,
    existing: ToBePaidEntity?,
    onDismiss: () -> Unit,
    onSave: (ToBePaidEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    val colors = LocalReserveColors.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var amountText by remember {
        mutableStateOf(existing?.amount?.let(::plainMinor) ?: "")
    }
    var type by remember {
        mutableStateOf(existing?.type?.takeIf { it != TransactionType.TRANSFER }
            ?: TransactionType.EXPENSE)
    }
    var dueDate by remember {
        mutableStateOf(existing?.dueAt?.let(Dates::toLocalDate) ?: Dates.today().plusDays(1))
    }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var accountId by remember {
        mutableStateOf(existing?.accountId ?: state.activeAccounts.firstOrNull()?.id)
    }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    val categories = state.categories.filter { it.type == type }
    val amount = Money.parse(amountText)
    val futureDue = !dueDate.isBefore(Dates.today())
    val valid = title.isNotBlank() && amount != null && amount > 0L && accountId != null && futureDue

    // Keep the selection valid when the user flips between expense and income.
    val effectiveCategoryId = categoryId?.takeIf { id -> categories.any { it.id == id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.fillLight,
        titleContentColor = colors.textBase,
        textContentColor = colors.textLight,
        title = { Text(if (existing == null) "Add to be paid" else "Edit reminder") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        TransactionType.EXPENSE to "Payment",
                        TransactionType.INCOME to "Receipt"
                    ).forEach { (option, label) ->
                        val selected = type == option
                        Surface(
                            onClick = {
                                type = option
                                categoryId = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) {
                                colors.primaryDefault.copy(alpha = 0.12f)
                            } else {
                                colors.fillLight
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) colors.primaryDefault else colors.strokeFaint
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) colors.primaryDefault else colors.textLight
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                EditorField(
                    label = "Title",
                    value = title,
                    keyboardType = KeyboardType.Text,
                    onValueChange = { title = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                EditorField(
                    label = "Amount",
                    value = amountText,
                    keyboardType = KeyboardType.Decimal,
                    onValueChange = { amountText = sanitizeAmount(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    onClick = { showDatePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.fillLight,
                    border = BorderStroke(1.dp, colors.strokeFaint)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Due date",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLighter
                            )
                            Text(
                                text = Dates.fullDate(Dates.toTimestamp(dueDate)) + ", " + dueDate.year,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textBase
                            )
                        }
                        Text("Change", color = colors.primaryDefault)
                    }
                }
                if (!futureDue) {
                    Text(
                        text = "Choose today or a future date.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.warningDefault
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Category", style = MaterialTheme.typography.labelMedium, color = colors.textLighter)
                Spacer(modifier = Modifier.height(6.dp))
                ChoiceWrap(
                    options = categories.map { it.id to it.name },
                    selectedId = effectiveCategoryId,
                    onSelect = { categoryId = it }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Account", style = MaterialTheme.typography.labelMedium, color = colors.textLighter)
                Spacer(modifier = Modifier.height(6.dp))
                ChoiceWrap(
                    options = state.activeAccounts.map { it.id to it.name },
                    selectedId = accountId,
                    onSelect = { accountId = it }
                )

                Spacer(modifier = Modifier.height(12.dp))
                EditorField(
                    label = "Note (optional)",
                    value = note,
                    keyboardType = KeyboardType.Text,
                    onValueChange = { note = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!valid) return@TextButton
                    val finalAmount = amount ?: return@TextButton
                    val finalAccountId = accountId ?: return@TextButton
                    onSave(
                        ToBePaidEntity(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            amount = finalAmount,
                            type = type,
                            categoryId = effectiveCategoryId,
                            accountId = finalAccountId,
                            dueAt = Dates.toTimestamp(dueDate),
                            note = note.trim(),
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                            settledTransactionId = null,
                            settledAt = null,
                            settlementSource = null
                        )
                    )
                },
                enabled = valid
            ) {
                Text("Save", color = if (valid) colors.primaryDefault else colors.textLighter)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = colors.warningDefault)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = colors.textLight)
                }
            }
        }
    )

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = Dates.toTimestamp(dueDate),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Dates.toLocalDate(utcTimeMillis).isBefore(Dates.today())
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        dueDate = Dates.toLocalDate(millis)
                    }
                    showDatePicker = false
                }) {
                    Text("OK", color = colors.primaryDefault)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = colors.textLight)
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit
) {
    val colors = LocalReserveColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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

@Composable
private fun ChoiceWrap(
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    val colors = LocalReserveColors.current
    Column {
        options.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (id, label) ->
                    val selected = id == selectedId
                    Surface(
                        onClick = { onSelect(id) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) {
                            colors.primaryDefault.copy(alpha = 0.12f)
                        } else {
                            colors.fillLight
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) colors.primaryDefault else colors.strokeFaint
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) colors.primaryDefault else colors.textLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** Keeps pasted/typed amounts valid: digits, one dot, at most two paise digits. */
private fun sanitizeAmount(raw: String): String {
    val cleaned = raw.filter { it.isDigit() || it == '.' }
    val hasDecimal = cleaned.contains('.')
    val parts = cleaned.split('.', limit = 2)
    val whole = parts[0].take(9).trimStart('0')
        .ifEmpty { if (hasDecimal) "0" else "" }
    val fraction = if (hasDecimal && parts.size > 1) parts[1].take(2) else ""
    return if (hasDecimal) "$whole.$fraction" else whole
}

private fun plainMinor(minor: Long): String {
    val rupees = minor / 100
    val paise = (minor % 100).toInt()
    return if (paise == 0) rupees.toString() else "$rupees.${paise.toString().padStart(2, '0')}"
}
