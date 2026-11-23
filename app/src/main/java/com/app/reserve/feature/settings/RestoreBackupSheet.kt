package com.app.reserve.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.app.reserve.core.backup.BackupCoordinator
import com.app.reserve.core.designsystem.EnteButton
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.ReserveDivider
import com.app.reserve.core.designsystem.spacing_lg
import com.app.reserve.core.designsystem.spacing_md
import com.app.reserve.core.domain.BackupPreview
import com.app.reserve.core.domain.Dates

/** Restore picker: newest local backup preselected, with an option to import any file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreBackupSheet(
    backups: List<BackupCoordinator.BackupFile>,
    onDismiss: () -> Unit,
    onRestore: (BackupCoordinator.BackupFile) -> Unit,
    onImportFile: () -> Unit,
    onBackupNow: () -> Unit
) {
    val colors = LocalReserveColors.current
    // Backups arrive newest-first, so the first entry is the default selection.
    var selected: BackupCoordinator.BackupFile? by remember { mutableStateOf(backups.firstOrNull()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = spacing_lg)) {
            Text(
                text = "Restore backup",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textBase
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Settings and all transactions will be replaced. The newest backup is selected by default.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textLight
            )
            Spacer(modifier = Modifier.height(spacing_md))

            if (backups.isEmpty()) {
                Text(
                    text = "No local backups yet. A daily backup is created automatically, or you can import any backup file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textLight,
                    modifier = Modifier.padding(vertical = spacing_lg)
                )
            } else {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(backups, key = { it.name }) { file ->
                        val isSelected = selected?.name == file.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = file }
                                .padding(vertical = spacing_md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Backup,
                                contentDescription = null,
                                tint = if (isSelected) colors.primaryDefault else colors.textLight,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(spacing_md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = Dates.fullDateLong(file.createdAt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textBase,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = "${file.sizeBytes / 1024} KB · ${file.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textLighter
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = colors.primaryDefault,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        ReserveDivider()
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing_md))
            EnteButton(
                text = "Restore selected backup",
                enabled = selected != null,
                onClick = { selected?.let(onRestore) }
            )
            Spacer(modifier = Modifier.height(spacing_md))
            TextButton(onClick = onImportFile) {
                Icon(
                    imageVector = Icons.Rounded.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Import any backup file")
            }
            TextButton(onClick = onBackupNow) {
                Text("Back up now")
            }
            Spacer(modifier = Modifier.height(spacing_lg))
        }
    }
}

/** Last chance to step back before a backup replaces the current ledger. */
@Composable
fun RestoreConfirmationDialog(
    preview: BackupPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalReserveColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.fillLight,
        titleContentColor = colors.textBase,
        textContentColor = colors.textLight,
        title = { Text("Restore this backup?") },
        text = {
            Column {
                Text(
                    text = "Exported ${Dates.fullDateLong(preview.exportedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textBase
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${preview.transactionCount} transactions · ${preview.accountCount} accounts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textBase
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${preview.sizeBytes / 1024} KB",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLighter
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "This replaces your current ledger and clears pending balance checks. This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warningDefault
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Restore", color = colors.warningDefault)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textLight)
            }
        }
    )
}
