package com.app.reserve.feature.add

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.reserve.core.data.defaultCategoryName
import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.RecurringEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.designsystem.AmountKeypad
import com.app.reserve.core.designsystem.CategoryChip
import com.app.reserve.core.designsystem.EnteButton
import com.app.reserve.core.designsystem.EnteRadius
import com.app.reserve.core.designsystem.EnteSpacing
import com.app.reserve.core.designsystem.LocalAmountsHidden
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.formatMoney
import com.app.reserve.core.designsystem.radius_md
import com.app.reserve.core.designsystem.radius_xl
import com.app.reserve.core.designsystem.spacing_lg
import com.app.reserve.core.designsystem.spacing_md
import com.app.reserve.core.designsystem.tabular
import com.app.reserve.core.domain.AmountInput
import com.app.reserve.core.domain.Dates
import com.app.reserve.feature.ledger.LedgerState
import com.app.reserve.feature.manage.CategoryEditorDialog
import java.time.LocalDate
import java.util.UUID

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

@Composable
fun AddTransactionScreen(
    state: LedgerState,
    initialType: TransactionType,
    existing: TransactionEntity?,
    onClose: () -> Unit,
    onSave: (TransactionEntity, TransactionEntity?) -> Unit,
    onCreateRecurring: (RecurringEntity) -> Unit,
    onCreateCategory: (CategoryEntity) -> Unit
) {
    val colors = LocalReserveColors.current
    val focusManager = LocalFocusManager.current

    val initialAccountId = existing?.accountId ?: state.activeAccounts.firstOrNull()?.id
    val initialForm = remember(existing) {
        AddFormSnapshot(
            type = existing?.type ?: initialType,
            amountText = existing?.amount?.let(::minorToInput) ?: "",
            categoryId = existing?.categoryId,
            accountId = initialAccountId,
            targetAccountId = existing?.targetAccountId
                ?: state.activeAccounts.firstOrNull { it.id != initialAccountId }?.id,
            note = existing?.note ?: "",
            date = existing?.let { Dates.toLocalDate(it.timestamp) } ?: LocalDate.now(),
            repeatMonthly = false
        )
    }

    var type by remember(existing) { mutableStateOf(initialForm.type) }
    var amountText by remember(existing) { mutableStateOf(initialForm.amountText) }
    var categoryId by remember(existing) { mutableStateOf(initialForm.categoryId) }
    var accountId by remember(existing) { mutableStateOf(initialForm.accountId) }
    var targetAccountId by remember(existing) { mutableStateOf(initialForm.targetAccountId) }
    var note by remember(existing) { mutableStateOf(initialForm.note) }
    var date by remember(existing) { mutableStateOf(initialForm.date) }
    var repeatMonthly by remember { mutableStateOf(false) }
    var noteFocused by remember { mutableStateOf(false) }
    var amountFocused by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryEditor by remember { mutableStateOf(false) }
    var pendingCategoryId by remember { mutableStateOf<String?>(null) }

    val categories = state.categories.filter { it.type == type }
    // Top four categories by how often they were used for this transaction type.
    val frequentCategories = remember(state.visibleTransactions, type, categories) {
        state.visibleTransactions.asSequence()
            .filter { it.type == type && it.categoryId != null }
            .groupingBy { it.categoryId!! }.eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(4)
            .mapNotNull { entry -> categories.firstOrNull { it.id == entry.key } }
    }
    LaunchedEffect(type, categories) {
        if (categories.none { it.id == categoryId }) {
            categoryId = categories.firstOrNull { it.name != defaultCategoryName(type) }?.id
                ?: categories.firstOrNull()?.id
        }
    }
    LaunchedEffect(categories, pendingCategoryId) {
        val id = pendingCategoryId ?: return@LaunchedEffect
        if (categories.any { it.id == id }) {
            categoryId = id
            pendingCategoryId = null
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
    val amountValid = amount.minor > 0L
    val accountValid = accountId != null
    val targetValid = type != TransactionType.TRANSFER ||
        (targetAccountId != null && targetAccountId != accountId)
    val canSave = amountValid && accountValid && targetValid &&
        (type == TransactionType.TRANSFER || categoryId != null)

    val dirty = type != initialForm.type ||
        amountText != initialForm.amountText ||
        categoryId != initialForm.categoryId ||
        accountId != initialForm.accountId ||
        targetAccountId != initialForm.targetAccountId ||
        note.trim() != initialForm.note.trim() ||
        date != initialForm.date ||
        repeatMonthly != initialForm.repeatMonthly

    fun requestClose() {
        if (dirty) showDiscardConfirm = true else onClose()
    }

    BackHandler(onBack = { requestClose() })

    fun buildTransaction(): TransactionEntity = TransactionEntity(
        id = existing?.id ?: UUID.randomUUID().toString(),
        amount = amount.minor,
        type = type,
        categoryId = categoryId,
        accountId = accountId!!,
        targetAccountId = if (type == TransactionType.TRANSFER) targetAccountId else null,
        timestamp = Dates.toTimestamp(date),
        note = note.trim(),
        isRecurring = existing?.isRecurring ?: false,
        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        deletedAt = existing?.deletedAt
    )

    val haptic = LocalHapticFeedback.current
    // In landscape the fixed keypad leaves no room for the form column, so the
    // whole sheet scrolls instead of clipping its top edge.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun save() {
        if (!canSave) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        focusManager.clearFocus()
        if (repeatMonthly && existing == null) {
            val nextMonthKey = Dates.shiftMonth(Dates.monthKey(date), 1)
            val nextDate = Dates.fromMonthAndDay(nextMonthKey, date.dayOfMonth)
            onCreateRecurring(
                RecurringEntity(
                    id = UUID.randomUUID().toString(),
                    amount = amount.minor,
                    type = type,
                    categoryId = categoryId,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = { requestClose() }
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (landscape) Modifier.fillMaxHeight() else Modifier)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(
                topStart = radius_xl,
                topEnd = radius_xl
            ),
            color = colors.fillBase
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .then(if (landscape) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing_lg, vertical = spacing_lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            existing != null -> "Edit transaction"
                            type == TransactionType.TRANSFER -> "Transfer"
                            type == TransactionType.INCOME -> "Add income"
                            else -> "Add expense"
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.textBase,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { requestClose() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textBase)
                    }
                }

                Column(
                    modifier = Modifier
                        .then(if (landscape) Modifier else Modifier.weight(1f).verticalScroll(rememberScrollState()))
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
                        color = colors.textLighter
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // A real text field so hardware keyboards, paste and IME
                    // dictation all work; the custom keypad stays as an alternative.
                    BasicTextField(
                        value = amountText,
                        onValueChange = { amountText = sanitizeAmount(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { amountFocused = it.isFocused },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            amountFocused = false
                        }),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (amountValid) colors.textBase else colors.textLighter
                        ).tabular(),
                        decorationBox = { inner ->
                            if (amountText.isEmpty()) {
                                Text(
                                    text = "₹0",
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.Medium
                                    ).tabular(),
                                    color = colors.textLighter
                                )
                            }
                            inner()
                        }
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
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    CategorySection(
                        categories = categories,
                        frequentCategories = frequentCategories,
                        selectedId = categoryId,
                        onSelect = { categoryId = it },
                        onCreate = { showCategoryEditor = true }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (type != TransactionType.TRANSFER) {
                        FieldRow(
                            label = "Account",
                            value = state.accountName(accountId),
                            onClick = { showAccountPicker = true }
                        )
                    }

                    FieldRow(
                        label = "Date",
                        value = Dates.fullDateLong(Dates.toTimestamp(date)),
                        onClick = { showDatePicker = true }
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .onFocusChanged { noteFocused = it.isFocused },
                        placeholder = {
                            Text("Note (optional)", color = colors.textLighter)
                        },
                        singleLine = false,
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            noteFocused = false
                        }),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primaryDefault,
                            unfocusedBorderColor = colors.strokeFaint,
                            focusedContainerColor = colors.fillLight,
                            unfocusedContainerColor = colors.fillLight,
                            focusedTextColor = colors.textBase,
                            unfocusedTextColor = colors.textBase,
                            cursorColor = colors.primaryDefault
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
                                    color = colors.textBase
                                )
                                Text(
                                    text = "Adds this same entry on the ${date.dayOfMonth}${ordinalSuffix(date.dayOfMonth)} of each month.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textLighter
                                )
                            }
                            Switch(
                                checked = repeatMonthly,
                                onCheckedChange = { repeatMonthly = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.textReverse,
                                    checkedTrackColor = colors.primaryDefault,
                                    uncheckedThumbColor = colors.textLighter,
                                    uncheckedTrackColor = colors.fillDark,
                                    uncheckedBorderColor = colors.strokeFaint
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Surface(color = colors.fillBase) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (amountFocused || noteFocused) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing_lg, vertical = spacing_md),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (amountFocused) {
                                        "Tap Done to use the keypad again"
                                    } else {
                                        "Tap the amount to use the keypad again"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textLighter
                                )
                            }
                        } else {
                            AmountKeypad(onKey = { key ->
                                amountText = AmountInput(amountText).append(key).text
                            })
                        }
                        EnteButton(
                            text = "Save",
                            onClick = { save() },
                            enabled = canSave,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = EnteSpacing.lg, vertical = EnteSpacing.md)
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        CustomDateTimePickerDialog(
            initialDate = date,
            onDateSelected = { date = it },
            onDismiss = { showDatePicker = false }
        )
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

    if (showCategoryEditor) {
        CategoryEditorDialog(
            existing = null,
            initialType = type,
            allowedTypes = listOf(type),
            onDismiss = { showCategoryEditor = false },
            onSave = { created ->
                onCreateCategory(created)
                pendingCategoryId = created.id
                showCategoryEditor = false
            },
            onDelete = {}
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            containerColor = colors.fillLight,
            titleContentColor = colors.textBase,
            textContentColor = colors.textLight,
            title = { Text("Discard this transaction?") },
            text = { Text("Your changes haven't been saved. Leaving now will lose them.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onClose()
                }) {
                    Text("Discard", color = colors.warningDefault)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Keep editing", color = colors.textLight)
                }
            }
        )
    }
}

@Composable
private fun CustomDateTimePickerDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalReserveColors.current
    var selectedDate by remember { mutableStateOf(initialDate) }
    var displayingMonthYear by remember { mutableStateOf(initialDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.fillLight,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { displayingMonthYear = displayingMonthYear.minusMonths(1) }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous Month", tint = colors.textBase)
                }
                Text(
                    text = "${displayingMonthYear.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${displayingMonthYear.year}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textBase
                )
                IconButton(onClick = { displayingMonthYear = displayingMonthYear.plusMonths(1) }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next Month", tint = colors.textBase)
                }
            }
        },
        text = {
            Column {
                val daysInMonth = displayingMonthYear.lengthOfMonth()
                val firstDayOfWeek = displayingMonthYear.withDayOfMonth(1).dayOfWeek.value % 7

                val totalGridCells = firstDayOfWeek + daysInMonth
                val rows = (totalGridCells + 6) / 7

                // Seven equal columns keep Sunday centered even on narrow
                // dialogs; fixed day widths plus SpaceBetween crowded it.
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (row in 0 until rows) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 7) {
                                val cellIndex = row * 7 + col
                                val dayNum = cellIndex - firstDayOfWeek + 1
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (dayNum in 1..daysInMonth) {
                                        val isSelected = selectedDate.year == displayingMonthYear.year &&
                                                selectedDate.month == displayingMonthYear.month &&
                                                selectedDate.dayOfMonth == dayNum
                                        Surface(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clickable(onClickLabel = "Select day $dayNum") {
                                                    selectedDate = LocalDate.of(displayingMonthYear.year, displayingMonthYear.month, dayNum)
                                                },
                                            shape = RoundedCornerShape(radius_md),
                                            color = if (isSelected) colors.primaryDefault else Color.Transparent
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "$dayNum",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isSelected) colors.textReverse else colors.textBase
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    onDateSelected(selectedDate)
                    onDismiss()
                },
                shape = RoundedCornerShape(radius_xl),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = colors.primaryDefault,
                    contentColor = colors.textReverse
                )
            ) {
                Text("Select", style = MaterialTheme.typography.titleMedium)
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
                TransactionType.EXPENSE -> colors.warningDefault
                TransactionType.INCOME -> colors.primaryDefault
                TransactionType.TRANSFER -> colors.textBase
            }
            Surface(
                onClick = { onSelect(type) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) accent.copy(alpha = 0.12f) else colors.fillLight,
                border = BorderStroke(1.dp, if (isSelected) accent else colors.strokeFaint)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) accent else colors.textLight
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySection(
    categories: List<CategoryEntity>,
    frequentCategories: List<CategoryEntity> = emptyList(),
    selectedId: String?,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit
) {
    val colors = LocalReserveColors.current
    Column {
        if (frequentCategories.isNotEmpty()) {
            Text(
                text = "OFTEN USED",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLighter
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                frequentCategories.forEach { category ->
                    CategoryChip(
                        category = category,
                        selected = category.id == selectedId,
                        onClick = { onSelect(category.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = "Category",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textLighter
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
        Surface(
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(radius_md),
            color = colors.fillLight,
            border = BorderStroke(1.dp, colors.strokeFaint)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = spacing_md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = colors.primaryDefault,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(spacing_md))
                Text(
                    text = "New category",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primaryDefault
                )
            }
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
            valueColor = LocalReserveColors.current.textBase
        )
        FieldRow(
            label = "To",
            value = state.accountName(targetAccountId),
            onClick = onPickTarget,
            valueColor = LocalReserveColors.current.textBase
        )
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    valueColor: Color = LocalReserveColors.current.textBase
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
            color = colors.textLight,
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
            tint = colors.textLighter,
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
    val amountsHidden = LocalAmountsHidden.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.fillLight,
        titleContentColor = colors.textBase,
        title = { Text(title) },
        text = {
            Column {
                if (accounts.isEmpty()) {
                    Text(
                        text = "Add an account first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textLight
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
                                color = if (account.id == selectedId) colors.primaryDefault else colors.textBase
                            )
                            Text(
                                text = formatMoney(account.balance, amountsHidden),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLighter
                            )
                        }
                        if (account.id == selectedId) {
                            Text(
                                text = "Selected",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primaryDefault
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.textLight)
            }
        }
    )
}

/** Snapshot used by the discard guard, taken before any user edits. */
private data class AddFormSnapshot(
    val type: TransactionType,
    val amountText: String,
    val categoryId: String?,
    val accountId: String?,
    val targetAccountId: String?,
    val note: String,
    val date: LocalDate,
    val repeatMonthly: Boolean
)

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
