package com.app.reserve.feature.manage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.app.reserve.core.data.DEFAULT_EXPENSE_CATEGORY_ID
import com.app.reserve.core.data.DEFAULT_INCOME_CATEGORY_ID
import com.app.reserve.core.data.DEFAULT_TRANSFER_CATEGORY_ID
import com.app.reserve.core.data.defaultCategoryName
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.designsystem.CategoryAvatar
import com.app.reserve.core.designsystem.CategoryIconChoices
import com.app.reserve.core.designsystem.CategoryPalette
import com.app.reserve.core.designsystem.EmptyState
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.ReserveCard
import com.app.reserve.core.designsystem.ScreenTitle
import com.app.reserve.core.designsystem.SectionLabel
import com.app.reserve.core.designsystem.categoryColor
import com.app.reserve.core.designsystem.categoryIcon
import com.app.reserve.feature.ledger.LedgerState
import java.util.UUID

@Composable
fun CategoriesScreen(
    state: LedgerState,
    onSaveCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalReserveColors.current
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    val expense = state.categories.filter { it.type == TransactionType.EXPENSE }
    val income = state.categories.filter { it.type == TransactionType.INCOME }
    val transfer = state.categories.filter { it.type == TransactionType.TRANSFER }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle(
            title = "Categories",
            onBack = onBack,
            trailing = {
                IconButton(onClick = { adding = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add category", tint = colors.primaryDefault)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 108.dp)
        ) {
            item { SectionLabel(text = "Expense") }
            items(expense, key = { it.id }) { category ->
                CategoryRow(category = category, onClick = { editing = category })
            }
            item { SectionLabel(text = "Income") }
            items(income, key = { it.id }) { category ->
                CategoryRow(category = category, onClick = { editing = category })
            }
            item { SectionLabel(text = "Transfer") }
            items(transfer, key = { it.id }) { category ->
                CategoryRow(category = category, onClick = { editing = category })
            }
            if (state.categories.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Category,
                        text = "No categories yet.",
                        actionLabel = "Add category",
                        onAction = { adding = true }
                    )
                }
            }
        }
    }

    if (adding || editing != null) {
        CategoryEditorDialog(
            existing = editing,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { category ->
                onSaveCategory(category)
                adding = false
                editing = null
            },
            onDelete = { category ->
                onDeleteCategory(category)
                adding = false
                editing = null
            }
        )
    }
}

@Composable
private fun CategoryRow(category: CategoryEntity, onClick: () -> Unit) {
    val colors = LocalReserveColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(category = category, size = 40.dp)
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textBase
            )
            Text(
                text = category.iconName.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLighter
            )
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(categoryColor(category.colorHex), CircleShape)
        )
    }
}

@Composable
fun CategoryEditorDialog(
    existing: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    initialType: TransactionType = TransactionType.EXPENSE,
    allowedTypes: List<TransactionType> = listOf(
        TransactionType.EXPENSE,
        TransactionType.INCOME,
        TransactionType.TRANSFER
    )
) {
    val colors = LocalReserveColors.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember {
        mutableStateOf(existing?.type ?: initialType)
    }
    var colorHex by remember { mutableStateOf(existing?.colorHex ?: "#10B981") }
    var iconName by remember { mutableStateOf(existing?.iconName ?: "other") }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.fillLight,
        titleContentColor = colors.textBase,
        textContentColor = colors.textLight,
        title = { Text(if (existing == null) "New category" else "Edit category") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
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
                Spacer(modifier = Modifier.height(12.dp))
                Text("Type", style = MaterialTheme.typography.labelMedium, color = colors.textLighter)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allowedTypes.forEach { option ->
                        val label = when (option) {
                            TransactionType.EXPENSE -> "Expense"
                            TransactionType.INCOME -> "Income"
                            TransactionType.TRANSFER -> "Transfer"
                        }
                        val selected = type == option
                        Surface(
                            onClick = { type = option },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
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
                Spacer(modifier = Modifier.height(14.dp))
                Text("Colour", style = MaterialTheme.typography.labelMedium, color = colors.textLighter)
                Spacer(modifier = Modifier.height(8.dp))
                CategoryPalette.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { color ->
                            val hex = String.format("#%06X", 0xFFFFFF and color.toArgb())
                            val selected = hex.equals(colorHex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(color, CircleShape)
                                    .clickable { colorHex = hex }
                            ) {
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(colors.fillLight, CircleShape)
                                            .align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text("Icon", style = MaterialTheme.typography.labelMedium, color = colors.textLighter)
                Spacer(modifier = Modifier.height(8.dp))
                CategoryIconChoices.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (icon, label) ->
                            val selected = icon == iconName
                            Surface(
                                onClick = { iconName = icon },
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) colors.primaryDefault.copy(alpha = 0.12f) else colors.fillDark,
                                border = if (selected) BorderStroke(1.dp, colors.primaryDefault) else null
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = categoryIcon(icon),
                                        contentDescription = label,
                                        tint = if (selected) colors.primaryDefault else colors.textLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = existing?.id ?: UUID.randomUUID().toString()
                    onSave(CategoryEntity(id, name.trim(), colorHex, iconName, type))
                },
                enabled = name.isNotBlank()
            ) {
                Text(
                    text = "Save",
                    color = if (name.isNotBlank()) colors.primaryDefault else colors.textLighter
                )
            }
        },
        dismissButton = {
            Row {
                val isDefault = existing?.id == DEFAULT_EXPENSE_CATEGORY_ID ||
                    existing?.id == DEFAULT_INCOME_CATEGORY_ID ||
                    existing?.id == DEFAULT_TRANSFER_CATEGORY_ID
                if (existing != null && !isDefault) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = colors.warningDefault)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = colors.textLight)
                }
            }
        }
    )

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.fillLight,
            titleContentColor = colors.textBase,
            textContentColor = colors.textLight,
            title = { Text("Delete ${existing.name}?") },
            text = {
                Text(
                    "Its transactions move to ${defaultCategoryName(existing.type)}, and any budget for this category is removed. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(existing) }) {
                    Text("Delete", color = colors.warningDefault)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Keep", color = colors.textLight)
                }
            }
        )
    }
}
