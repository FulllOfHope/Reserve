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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Restore
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
import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.designsystem.EmptyState
import com.app.reserve.core.designsystem.HeaderActionButton
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.MoneyText
import com.app.reserve.core.designsystem.ReserveCard
import com.app.reserve.core.designsystem.ReserveDivider
import com.app.reserve.core.designsystem.ScreenTitle
import com.app.reserve.core.designsystem.SectionLabel
import com.app.reserve.core.designsystem.tabular
import com.app.reserve.core.domain.Money
import com.app.reserve.feature.ledger.LedgerState
import java.util.UUID

@Composable
fun AccountsScreen(
    state: LedgerState,
    onSaveAccount: (AccountEntity) -> Unit,
    onDeleteAccount: (AccountEntity, (Boolean) -> Unit) -> Unit,
    onRestoreAccount: (AccountEntity) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalReserveColors.current
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var archiving by remember { mutableStateOf<AccountEntity?>(null) }

    val active = state.accounts.filterNot { it.isArchived }
    val archived = state.accounts.filter { it.isArchived }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle(
            title = "Accounts",
            onBack = onBack,
            trailing = {
                HeaderActionButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Add account",
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
                SectionLabel(text = "Active")
            }
            if (active.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.AccountBalanceWallet,
                        text = "No active accounts. Add the wallet or bank account you use.",
                        actionLabel = "Add account",
                        onAction = { adding = true }
                    )
                }
            } else {
                items(active, key = { it.id }) { account ->
                    AccountRow(
                        account = account,
                        onClick = { editing = account },
                        trailing = {
                            IconButton(onClick = { archiving = account }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Archive ${account.name}",
                                    tint = colors.textLighter,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                    ReserveDivider()
                }
            }

            if (archived.isNotEmpty()) {
                item {
                    SectionLabel(text = "Archived")
                }
                items(archived, key = { it.id }) { account ->
                    AccountRow(
                        account = account,
                        onClick = null,
                        trailing = {
                            TextButton(onClick = { onRestoreAccount(account) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Restore,
                                    contentDescription = null,
                                    tint = colors.primaryDefault,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore", color = colors.primaryDefault)
                            }
                        }
                    )
                    ReserveDivider()
                }
            }
        }
    }

    if (adding) {
        AccountEditorDialog(
            existing = null,
            onDismiss = { adding = false },
            onSave = { account ->
                onSaveAccount(account)
                adding = false
            }
        )
    }

    editing?.let { account ->
        AccountEditorDialog(
            existing = account,
            onDismiss = { editing = null },
            onSave = { updated ->
                onSaveAccount(updated)
                editing = null
            }
        )
    }

    archiving?.let { account ->
        AlertDialog(
            onDismissRequest = { archiving = null },
            containerColor = colors.fillLight,
            titleContentColor = colors.textBase,
            textContentColor = colors.textLight,
            title = { Text("Remove ${account.name}?") },
            text = {
                Text(
                    "Accounts with transactions are archived so old entries stay valid. " +
                        "An unused account is deleted permanently."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAccount(account) { archivedResult ->
                            archiving = null
                            if (!archivedResult) {
                                // Deletion is silent; the list updates through the Flow.
                            }
                        }
                    }
                ) {
                    Text("Remove", color = colors.warningDefault)
                }
            },
            dismissButton = {
                TextButton(onClick = { archiving = null }) {
                    Text("Cancel", color = colors.textLight)
                }
            }
        )
    }
}

@Composable
private fun AccountRow(
    account: AccountEntity,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit
) {
    val colors = LocalReserveColors.current
    val base = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
    Row(
        modifier = base,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(0.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.fillDark,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CreditCard,
                        contentDescription = null,
                        tint = colors.primaryDefault,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (account.isArchived) colors.textLighter else colors.textBase
            )
            Text(
                text = if (account.isArchived) "Archived" else "Balance",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLighter
            )
        }
        MoneyText(
            amount = account.balance,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = if (account.isArchived) colors.textLighter else colors.textBase
        )
        trailing()
    }
}

@Composable
private fun AccountEditorDialog(
    existing: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit
) {
    val colors = LocalReserveColors.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var openingText by remember { mutableStateOf("") }

    val openingValid = existing != null || openingText.isBlank() || Money.parse(openingText) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.fillLight,
        titleContentColor = colors.textBase,
        textContentColor = colors.textLight,
        title = { Text(if (existing == null) "New account" else "Edit account") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
                if (existing == null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = openingText,
                        onValueChange = { openingText = it },
                        label = { Text("Opening balance (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = !openingValid,
                        supportingText = if (!openingValid) {
                            { Text("Enter a valid amount, for example 1200.50") }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors()
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Balances change automatically as you add transactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textLighter
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val account = existing?.copy(name = name.trim())
                        ?: AccountEntity(
                            id = UUID.randomUUID().toString(),
                            name = name.trim().ifBlank { "Account" },
                            balance = Money.parse(openingText) ?: 0L
                        )
                    onSave(account)
                },
                enabled = name.isNotBlank() && openingValid
            ) {
                Text(
                    text = "Save",
                    color = if (name.isNotBlank() && openingValid) colors.primaryDefault else colors.textLighter
                )
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
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalReserveColors.current.primaryDefault,
    unfocusedBorderColor = LocalReserveColors.current.strokeFaint,
    focusedTextColor = LocalReserveColors.current.textBase,
    unfocusedTextColor = LocalReserveColors.current.textBase,
    focusedLabelColor = LocalReserveColors.current.textLight,
    unfocusedLabelColor = LocalReserveColors.current.textLighter,
    cursorColor = LocalReserveColors.current.primaryDefault
)
