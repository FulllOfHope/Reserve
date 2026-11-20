package com.app.reserve.feature.trash

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.designsystem.EmptyState
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.ReserveDivider
import com.app.reserve.core.designsystem.ScreenTitle
import com.app.reserve.core.designsystem.SheetHandle
import com.app.reserve.core.designsystem.TransactionRow
import com.app.reserve.core.domain.Dates
import com.app.reserve.feature.ledger.LedgerState

/**
 * The Recycle Bin. Rows here are still fully recoverable until they are
 * permanently deleted or auto-purged 30 days after they were moved here.
 */
@Composable
fun TrashScreen(
    state: LedgerState,
    onBack: () -> Unit,
    onRestore: (TransactionEntity) -> Unit,
    onDeleteForever: (TransactionEntity) -> Unit,
    onEmptyTrash: () -> Unit
) {
    val colors = LocalReserveColors.current
    val trashed = state.trashedTransactions
    var selected by remember { mutableStateOf<TransactionEntity?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle(
            title = "Trash",
            onBack = onBack,
            trailing = {
                if (trashed.isNotEmpty()) {
                    Text(
                        text = "Empty",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.warningDefault,
                        modifier = Modifier
                            .clickable { confirmEmpty = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
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
                    shape = RoundedCornerShape(12.dp),
                    color = colors.fillLight,
                    border = BorderStroke(1.dp, colors.strokeFaint)
                ) {
                    Text(
                        text = "Items in Trash are permanently deleted after 30 days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textLight,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            if (trashed.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Restore,
                        text = "Nothing in Trash. Deleted transactions will appear here for 30 days."
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${trashed.size} ${if (trashed.size == 1) "ITEM" else "ITEMS"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textLighter
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "MOST RECENT FIRST",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLighter
                        )
                    }
                }
                items(items = trashed, key = { it.id }) { transaction ->
                    Column {
                        TransactionRow(
                            transaction = transaction,
                            category = state.categoryFor(transaction.categoryId),
                            onClick = { selected = transaction }
                        )
                        TransactionRowCaption(deletedAt = transaction.deletedAt)
                        ReserveDivider()
                    }
                }
            }
        }
    }

    selected?.let { transaction ->
        TrashActionSheet(
            onRestore = {
                onRestore(transaction)
                selected = null
            },
            onDeleteForever = {
                onDeleteForever(transaction)
                selected = null
            },
            onDismiss = { selected = null }
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty Trash?", color = colors.textBase) },
            text = {
                Text(
                    "All ${trashed.size} items will be permanently deleted. This cannot be undone.",
                    color = colors.textLight
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onEmptyTrash()
                    confirmEmpty = false
                }) {
                    Text("Empty", color = colors.warningDefault)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) {
                    Text("Cancel", color = colors.textLight)
                }
            },
            containerColor = colors.surface
        )
    }
}

@Composable
private fun TransactionRowCaption(deletedAt: Long?) {
    val colors = LocalReserveColors.current
    val label = deletedAt?.let { Dates.fullDate(it) }?.let { "Moved to Trash $it" }
        ?: "Moved to Trash"
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = colors.textLighter,
        modifier = Modifier.padding(start = 72.dp, bottom = 12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashActionSheet(
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalReserveColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentWindowInsets = { WindowInsets.ime },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetHandle()
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetAction(
                    icon = Icons.Rounded.Restore,
                    label = "Restore",
                    tint = colors.primaryDefault,
                    onClick = onRestore
                )
                SheetAction(
                    icon = Icons.Rounded.DeleteForever,
                    label = "Delete forever",
                    tint = colors.warningDefault,
                    onClick = onDeleteForever
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val colors = LocalReserveColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textBase)
    }
}
