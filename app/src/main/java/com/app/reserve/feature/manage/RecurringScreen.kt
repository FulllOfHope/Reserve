package com.app.reserve.feature.manage

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.app.reserve.core.database.RecurringEntity
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.designsystem.EmptyState
import com.app.reserve.core.designsystem.HeaderActionButton
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

@Composable
fun RecurringScreen(
    state: LedgerState,
    onSaveRecurring: (RecurringEntity) -> Unit,
    onDeleteRecurring: (String) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalReserveColors.current
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle(
            title = "Recurring",
            onBack = onBack,
            trailing = {
                HeaderActionButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Add recurring entry",
                    onClick = { adding = true },
                    tint = colors.primaryDefault
                )
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
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
                            text = "Monthly entries",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textBase
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reserve adds the next entry automatically when it is due. Editing or deleting " +
                                "one generated entry never changes the rule.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textLight
                        )
                    }
                }
            }

            item { SectionLabel(text = "Rules") }

            if (state.recurring.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Repeat,
                        text = "No recurring entries yet. Add rent, a subscription or your salary.",
                        actionLabel = "Add rule",
                        onAction = { adding = true }
                    )
                }
            } else {
                items(state.recurring, key = { it.id }) { rule ->
                    RecurringRow(
                        rule = rule,
                        categoryName = rule.categoryId?.let { state.categoryFor(it)?.name },
                        accountName = state.accountName(rule.accountId),
                        onClick = { editing = rule },
                        onTogglePaused = {
                            onSaveRecurring(rule.copy(paused = !rule.paused))
                        },
                        onDelete = { onDeleteRecurring(rule.id) }
                    )
                    ReserveDivider()
                }
            }
        }
    }

    if (adding || editing != null) {
        RecurringEditorDialog(
            state = state,
            existing = editing,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { rule ->
                onSaveRecurring(rule)
                adding = false
                editing = null
            },
            onDelete = {
                editing?.let { onDeleteRecurring(it.id) }
                editing = null
            }
        )
    }
}

@Composable
private fun RecurringRow(
    rule: RecurringEntity,
    categoryName: String?,
    accountName: String,
    onClick: () -> Unit,
    onTogglePaused: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalReserveColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.note.ifBlank {
                        categoryName ?: if (rule.type == TransactionType.TRANSFER) "Transfer" else "Recurring"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (rule.paused) colors.textLighter else colors.textBase
                )
                Text(
                    text = buildString {
                        append("Day ${rule.dayOfMonth}")
                        append(" · ")
                        append(accountName)
                        if (rule.paused) append(" · Paused")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLighter
                )
            }
            MoneyText(
                amount = if (rule.type == TransactionType.EXPENSE) -rule.amount else rule.amount,
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = when (rule.type) {
                    TransactionType.INCOME -> colors.primaryDefault
                    TransactionType.TRANSFER -> colors.textBase
                    TransactionType.EXPENSE -> colors.textBase
                }
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Switch(
            checked = !rule.paused,
            onCheckedChange = { onTogglePaused() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.textReverse,
                checkedTrackColor = colors.primaryDefault,
                uncheckedThumbColor = colors.textLighter,
                uncheckedTrackColor = colors.fillDark,
                uncheckedBorderColor = colors.strokeFaint
            )
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete rule",
                tint = colors.warningDefault,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RecurringEditorDialog(
    state: LedgerState,
    existing: RecurringEntity?,
    onDismiss: () -> Unit,
    onSave: (RecurringEntity) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val colors = LocalReserveColors.current
    var type by remember { mutableStateOf(existing?.type ?: TransactionType.EXPENSE) }
    var amountText by remember {
        mutableStateOf(existing?.amount?.let { plainMinor(it) } ?: "")
    }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var accountId by remember {
        mutableStateOf(existing?.accountId ?: state.activeAccounts.firstOrNull()?.id)
    }
    var targetAccountId by remember { mutableStateOf(existing?.targetAccountId) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var dayText by remember {
        mutableStateOf((existing?.dayOfMonth ?: LocalDate.now().dayOfMonth).toString())
    }

    val categories = state.categories.filter { it.type == type }
    val amount = Money.parse(amountText)
    val day = dayText.toIntOrNull()?.takeIf { it in 1..31 }
    val valid = amount != null && amount > 0L && day != null && accountId != null &&
        (type == TransactionType.TRANSFER || categoryId != null) &&
        (type != TransactionType.TRANSFER || (targetAccountId != null && targetAccountId != accountId))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.fillLight,
        titleContentColor = colors.textBase,
        textContentColor = colors.textLight,
        title = { Text(if (existing == null) "New recurring entry" else "Edit recurring entry") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        TransactionType.EXPENSE to "Expense",
                        TransactionType.INCOME to "Income",
                        TransactionType.TRANSFER to "Transfer"
                    ).forEach { (option, label) ->
                        val selected = type == option
                        Surface(
                            onClick = {
                                type = option
                                if (option == TransactionType.TRANSFER) {
                                    categoryId = null
                                    if (targetAccountId == null || targetAccountId == accountId) {
                                        targetAccountId = state.activeAccounts
                                            .firstOrNull { it.id != accountId }?.id
                                    }
                                } else {
                                    val available = state.categories.filter { it.type == option }
                                    if (available.none { it.id == categoryId }) {
                                        categoryId = available.firstOrNull()?.id
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) colors.primaryDefault.copy(alpha = 0.12f) else colors.fillLight,
                            border = BorderStroke(1.dp, if (selected) colors.primaryDefault else colors.strokeFaint)
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
                PickerField(
                    label = "Amount",
                    value = amountText,
                    keyboardType = KeyboardType.Decimal,
                    onValueChange = { amountText = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                PickerField(
                    label = "Day of month (1–31)",
                    value = dayText,
                    keyboardType = KeyboardType.Number,
                    onValueChange = { input -> dayText = input.filter { it.isDigit() }.take(2) }
                )

                if (type != TransactionType.TRANSFER) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Category", style = MaterialTheme.typography.labelMedium, color = colors.textLighter)
                    Spacer(modifier = Modifier.height(6.dp))
                    ChoiceWrap(
                        options = categories.map { it.id to it.name },
                        selectedId = categoryId,
                        onSelect = { categoryId = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (type == TransactionType.TRANSFER) "From account" else "Account",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textLighter
                )
                Spacer(modifier = Modifier.height(6.dp))
                ChoiceWrap(
                    options = state.activeAccounts.map { it.id to it.name },
                    selectedId = accountId,
                    onSelect = { picked ->
                        accountId = picked
                        if (type == TransactionType.TRANSFER && targetAccountId == picked) {
                            targetAccountId = state.activeAccounts.firstOrNull { it.id != picked }?.id
                        }
                    }
                )

                if (type == TransactionType.TRANSFER) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("To account", style = MaterialTheme.typography.labelMedium, color = colors.textLighter)
                    Spacer(modifier = Modifier.height(6.dp))
                    ChoiceWrap(
                        options = state.activeAccounts
                            .filter { it.id != accountId }
                            .map { it.id to it.name },
                        selectedId = targetAccountId,
                        onSelect = { targetAccountId = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                PickerField(
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
                    val rule = buildRule(
                        existing = existing,
                        type = type,
                        amount = amount!!,
                        categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
                        accountId = accountId!!,
                        targetAccountId = if (type == TransactionType.TRANSFER) targetAccountId else null,
                        note = note.trim(),
                        day = day!!
                    )
                    onSave(rule)
                },
                enabled = valid
            ) {
                Text("Save", color = if (valid) colors.primaryDefault else colors.textLighter)
            }
        },
        dismissButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (existing != null && onDelete != null) {
                    TextButton(onClick = {
                        onDelete()
                        onDismiss()
                    }) {
                        Text("Delete", color = colors.warningDefault)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = colors.textLight)
                }
            }
        }
    )
}

@Composable
private fun PickerField(
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
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) colors.primaryDefault.copy(alpha = 0.12f) else colors.fillLight,
                        border = BorderStroke(1.dp, if (selected) colors.primaryDefault else colors.strokeFaint)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) colors.primaryDefault else colors.textLight,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun buildRule(
    existing: RecurringEntity?,
    type: TransactionType,
    amount: Long,
    categoryId: String?,
    accountId: String,
    targetAccountId: String?,
    note: String,
    day: Int
): RecurringEntity {
    val id = existing?.id ?: UUID.randomUUID().toString()
    val dayChanged = existing == null || existing.dayOfMonth != day
    val nextDue = when {
        existing != null && !dayChanged -> existing.nextDueAt
        else -> nextOccurrence(day)
    }
    return RecurringEntity(
        id = id,
        amount = amount,
        type = type,
        categoryId = categoryId,
        accountId = accountId,
        targetAccountId = targetAccountId,
        note = note,
        dayOfMonth = day,
        nextDueAt = nextDue,
        paused = existing?.paused ?: false
    )
}

/** The next date on or after today for [dayOfMonth], at local noon. */
private fun nextOccurrence(dayOfMonth: Int): Long {
    val today = LocalDate.now()
    val thisMonth = today.withDayOfMonth(dayOfMonth.coerceAtMost(today.lengthOfMonth()))
    val date = if (thisMonth.isBefore(today)) {
        val nextMonth = today.plusMonths(1).withDayOfMonth(1)
        nextMonth.withDayOfMonth(dayOfMonth.coerceAtMost(nextMonth.lengthOfMonth()))
    } else {
        thisMonth
    }
    return Dates.toTimestamp(date)
}

private fun plainMinor(minor: Long): String {
    val rupees = minor / 100
    val paise = (minor % 100).toInt()
    return if (paise == 0) rupees.toString() else "$rupees.${paise.toString().padStart(2, '0')}"
}
