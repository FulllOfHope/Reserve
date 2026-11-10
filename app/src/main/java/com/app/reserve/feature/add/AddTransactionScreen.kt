package com.app.reserve.feature.add

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.RecurringEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.designsystem.AmountKeypad
import com.app.reserve.core.designsystem.CategoryChip
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.MoneyText
import com.app.reserve.core.designsystem.tabular
import com.app.reserve.core.domain.AmountInput
import com.app.reserve.core.domain.Dates
import com.app.reserve.feature.ledger.LedgerState
import java.time.LocalDate
import java.util.UUID

@Composable
fun AddTransactionScreen(
    state: LedgerState,
    initialType: TransactionType,
    existing: TransactionEntity?,
    onClose: () -> Unit,
    onSave: (TransactionEntity, TransactionEntity?) -> Unit,
    onCreateRecurring: (RecurringEntity) -> Unit
) {
    val colors = LocalReserveColors.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var type by remember(existing) { mutableStateOf(existing?.type ?: initialType) }
    var amountText by remember(existing) { mutableStateOf(existing?.amount?.let { minorToInput(it) } ?: "") }
    var categoryId by remember(existing) { mutableStateOf(existing?.categoryId) }
    var accountId by remember(existing) {
        mutableStateOf(existing?.accountId ?: state.activeAccounts.firstOrNull()?.id)
    }
    var targetAccountId by remember(existing) { mutableStateOf(existing?.targetAccountId) }
    var note by remember(existing) { mutableStateOf(existing?.note ?: "") }
    var date by remember(existing) {
        mutableStateOf(existing?.let { Dates.toLocalDate(it.timestamp) } ?: LocalDate.now())
    }
    var repeatMonthly by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }

    val categories = state.categories.filter { it.type == type }
    LaunchedEffect(type, categories) {
        if (type != TransactionType.TRANSFER && categories.none { it.id == categoryId }) {
            categoryId = categories.firstOrNull { it.name != "Uncategorized" }?.id
                ?: categories.firstOrNull()?.id
        }
    }
    LaunchedEffect(Unit) {
        if (type == TransactionType.TRANSFER) {
            if (targetAccountId == null || targetAccountId == accountId) {
                targetAccountId = state.activeAccounts.firstOrNull { it.id != accountId }?.id
            }
        }
    }

    val amount = AmountInput(amountText)
    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val amountValid = amount.minor > 0L
    val accountValid = accountId != null
    val targetValid = type != TransactionType.TRANSFER ||
        (targetAccountId != null && targetAccountId != accountId)
    val canSave = amountValid && accountValid && targetValid &&
        (type == TransactionType.TRANSFER || categoryId != null)

    fun buildTransaction(): TransactionEntity = TransactionEntity(
        id = existing?.id ?: UUID.randomUUID().toString(),
        amount = amount.minor,
        type = type,
        categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
        accountId = accountId!!,
        targetAccountId = if (type == TransactionType.TRANSFER) targetAccountId else null,
        timestamp = Dates.toTimestamp(date),
        note = note.trim(),
        isRecurring = existing?.isRecurring ?: false,
        createdAt = existing?.createdAt ?: System.currentTimeMillis()
    )

    fun save() {
        if (!canSave) return
        focusManager.clearFocus()
        if (repeatMonthly && existing == null) {
            val nextMonthKey = Dates.shiftMonth(Dates.monthKey(date), 1)
            val nextDate = Dates.fromMonthAndDay(nextMonthKey, date.dayOfMonth)
            onCreateRecurring(
                RecurringEntity(
                    id = UUID.randomUUID().toString(),
                    amount = amount.minor,
                    type = type,
                    categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
                    accountId = accountId!!,
                    targetAccountId = if (type == TransactionType.TRANSFER) targetAccountId else null,
                    note = note.trim(),
                    dayOfMonth = date.dayOfMonth,
                    nextDueAt = Dates.toTimestamp(nextDate)
                )
            )
        }
        onSave(buildTransaction(), existing)
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textHigh)
            }
            Text(
                text = when {
                    existing != null -> "Edit transaction"
                    type == TransactionType.TRANSFER -> "Transfer"
                    type == TransactionType.INCOME -> "Add income"
                    else -> "Add expense"
                },
                style = MaterialTheme.typography.titleMedium,
                color = colors.textHigh,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { save() }, enabled = canSave) {
                Text(
                    text = "Save",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canSave) colors.primary else colors.textLow
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (existing == null) {
                TypeSelector(
                    selected = type,
                    onSelect = { newType ->
                        type = newType
                        if (newType == TransactionType.TRANSFER) {
                            if (targetAccountId == null) {
                                targetAccountId = state.activeAccounts
                                    .firstOrNull { it.id != accountId }?.id
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = "Amount",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textLow
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = amount.display,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Medium
                ).tabular(),
                color = if (amountValid) colors.textHigh else colors.textLow
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (type == TransactionType.TRANSFER) {
                TransferAccounts(
                    state = state,
                    accountId = accountId,
                    targetAccountId = targetAccountId,
                    onPickSource = { showAccountPicker = true },
                    onPickTarget = { showTargetPicker = true }
                )
            } else {
                CategorySection(
                    categories = categories,
                    selectedId = categoryId,
                    onSelect = { categoryId = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                FieldRow(
                    label = "Account",
                    value = state.accountName(accountId),
                    onClick = { showAccountPicker = true }
                )
            }

            FieldRow(
                label = "Date",
                value = Dates.fullDateLong(Dates.toTimestamp(date)),
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            date = LocalDate.of(year, month + 1, dayOfMonth)
                        },
                        date.year,
                        date.monthValue - 1,
                        date.dayOfMonth
                    ).show()
                }
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = {
                    Text("Note (optional)", color = colors.textLow)
                },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedTextColor = colors.textHigh,
                    unfocusedTextColor = colors.textHigh,
                    cursorColor = colors.primary
                )
            )

            if (existing == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Repeat monthly",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textHigh
                        )
                        Text(
                            text = "Adds this same entry on the ${date.dayOfMonth}${ordinalSuffix(date.dayOfMonth)} of each month.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textLow
                        )
                    }
                    Switch(
                        checked = repeatMonthly,
                        onCheckedChange = { repeatMonthly = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.textLow,
                            uncheckedTrackColor = colors.surfaceVariant,
                            uncheckedBorderColor = colors.border
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Surface(color = colors.canvas) {
            AmountKeypad(onKey = { key -> amountText = AmountInput(amountText).append(key).text })
        }
    }

    if (showAccountPicker) {
        AccountPickerDialog(
            title = "Account",
            accounts = state.activeAccounts,
            selectedId = accountId,
            onSelect = {
                accountId = it
                if (type == TransactionType.TRANSFER && targetAccountId == it) {
                    targetAccountId = state.activeAccounts.firstOrNull { account -> account.id != it }?.id
                }
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false }
        )
    }

    if (showTargetPicker) {
        AccountPickerDialog(
            title = "Transfer to",
            accounts = state.activeAccounts.filter { it.id != accountId },
            selectedId = targetAccountId,
            onSelect = {
                targetAccountId = it
                showTargetPicker = false
            },
            onDismiss = { showTargetPicker = false }
        )
    }
}

@Composable
private fun TypeSelector(
    selected: TransactionType,
    onSelect: (TransactionType) -> Unit
) {
    val colors = LocalReserveColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            TransactionType.EXPENSE to "Expense",
            TransactionType.INCOME to "Income",
            TransactionType.TRANSFER to "Transfer"
        ).forEach { (type, label) ->
            val isSelected = selected == type
            val accent = when (type) {
                TransactionType.EXPENSE -> colors.expense
                TransactionType.INCOME -> colors.income
                TransactionType.TRANSFER -> colors.transfer
            }
            Surface(
                onClick = { onSelect(type) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) accent.copy(alpha = 0.12f) else colors.surface,
                border = BorderStroke(1.dp, if (isSelected) accent else colors.border)
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) accent else colors.textMed
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySection(
    categories: List<CategoryEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    Column {
        Text(
            text = "Category",
            style = MaterialTheme.typography.labelMedium,
            color = LocalReserveColors.current.textLow
        )
        Spacer(modifier = Modifier.height(8.dp))
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
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TransferAccounts(
    state: LedgerState,
    accountId: String?,
    targetAccountId: String?,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit
) {
    Column {
        FieldRow(
            label = "From",
            value = state.accountName(accountId),
            onClick = onPickSource,
            valueColor = LocalReserveColors.current.transfer
        )
        FieldRow(
            label = "To",
            value = state.accountName(targetAccountId),
            onClick = onPickTarget,
            valueColor = LocalReserveColors.current.transfer
        )
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    valueColor: Color = LocalReserveColors.current.textHigh
) {
    val colors = LocalReserveColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMed,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor
        )
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = colors.textLow,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AccountPickerDialog(
    title: String,
    accounts: List<AccountEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalReserveColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textHigh,
        title = { Text(title) },
        text = {
            Column {
                if (accounts.isEmpty()) {
                    Text(
                        text = "Add an account first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMed
                    )
                }
                accounts.forEach { account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(account.id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (account.id == selectedId) colors.primary else colors.textHigh
                            )
                            Text(
                                text = com.app.reserve.core.domain.Money.format(account.balance),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLow
                            )
                        }
                        if (account.id == selectedId) {
                            Text(
                                text = "Selected",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.textMed)
            }
        }
    )
}

private fun minorToInput(minor: Long): String {
    val rupees = minor / 100
    val paise = (minor % 100).toInt()
    return if (paise == 0) rupees.toString() else "$rupees.${paise.toString().padStart(2, '0')}"
}

private fun ordinalSuffix(day: Int): String = when {
    day in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}
