package com.app.reserve.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.app.reserve.core.data.ImportSummary
import com.app.reserve.core.datastore.ReserveSettings
import com.app.reserve.core.datastore.ThemeMode
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.ReserveCard
import com.app.reserve.core.designsystem.ReserveDivider
import com.app.reserve.core.designsystem.ScreenTitle
import com.app.reserve.core.designsystem.SectionLabel
import com.app.reserve.core.domain.Dates
import com.app.reserve.feature.ledger.LedgerState
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    state: LedgerState,
    settings: ReserveSettings?,
    onToggleBiometric: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenRecurring: () -> Unit,
    exportCsv: suspend () -> String,
    importCsv: suspend (String) -> ImportSummary,
    createBackup: suspend () -> String,
    restoreBackup: suspend (String) -> Boolean,
    onBackupCreated: () -> Unit
) {
    val colors = LocalReserveColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packageVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun readDocument(uri: android.net.Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = exportCsv()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(csv.toByteArray())
                    } != null
                }.getOrDefault(false)
            }
            toast(if (ok) "CSV exported." else "Could not write that file.")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readDocument(uri) }
            if (text == null) {
                toast("Could not read that file.")
                return@launch
            }
            val summary = importCsv(text)
            toast(
                if (summary.skipped < 0) {
                    "That file is not a Reserve CSV."
                } else {
                    "Imported ${summary.imported} transactions" +
                        if (summary.skipped > 0) ", skipped ${summary.skipped}." else "."
                }
            )
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = createBackup()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray())
                    } != null
                }.getOrDefault(false)
            }
            if (ok) {
                onBackupCreated()
                toast("Backup saved.")
            } else {
                toast("Could not write that backup.")
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readDocument(uri) }
            if (text == null) {
                toast("Could not read that file.")
                return@launch
            }
            val restored = restoreBackup(text)
            toast(if (restored) "Backup restored." else "That backup could not be read.")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle(title = "Settings")
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
        ) {
            item {
                SectionLabel(text = "Appearance")
                ReserveCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textHigh
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Follow the system, or keep Reserve light or dark.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMed
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                ThemeOption(
                                    label = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                    selected = (settings?.themeMode ?: ThemeMode.SYSTEM) == mode,
                                    onClick = { onThemeMode(mode) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionLabel(text = "Privacy")
                ReserveCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Biometric lock",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textHigh
                            )
                            Text(
                                text = "Ask for your fingerprint or device PIN when Reserve opens.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMed
                            )
                        }
                        Switch(
                            checked = settings?.biometricLock == true,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val available = BiometricManager.from(context).canAuthenticate(
                                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                    ) == BiometricManager.BIOMETRIC_SUCCESS
                                    if (!available) {
                                        toast("Set up a screen lock on this device first.")
                                    } else {
                                        onToggleBiometric(true)
                                    }
                                } else {
                                    onToggleBiometric(false)
                                }
                            },
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
            }

            item { SectionLabel(text = "Organise") }

            item {
                ReserveCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(
                        icon = Icons.Rounded.Description,
                        label = "Categories",
                        value = "${state.categories.size}",
                        onClick = onOpenCategories
                    )
                    ReserveDivider()
                    SettingRow(
                        icon = Icons.Rounded.CreditCard,
                        label = "Accounts",
                        value = "${state.activeAccounts.size}",
                        onClick = onOpenAccounts
                    )
                    ReserveDivider()
                    SettingRow(
                        icon = Icons.Rounded.PieChart,
                        label = "Budgets",
                        value = "${state.monthBudgets.size} this month",
                        onClick = onOpenBudgets
                    )
                    ReserveDivider()
                    SettingRow(
                        icon = Icons.Rounded.Repeat,
                        label = "Recurring",
                        value = "${state.recurring.count { !it.paused }}",
                        onClick = onOpenRecurring
                    )
                }
            }

            item { SectionLabel(text = "Data") }

            item {
                ReserveCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(
                        icon = Icons.Rounded.FileUpload,
                        label = "Export CSV",
                        value = "Spreadsheet",
                        onClick = {
                            exportLauncher.launch("reserve-${LocalDate.now()}.csv")
                        }
                    )
                    ReserveDivider()
                    SettingRow(
                        icon = Icons.Rounded.FileDownload,
                        label = "Import CSV",
                        value = "Add transactions",
                        onClick = {
                            importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                        }
                    )
                    ReserveDivider()
                    SettingRow(
                        icon = Icons.Rounded.Backup,
                        label = "Create backup",
                        value = "JSON",
                        onClick = {
                            backupLauncher.launch("reserve-backup-${LocalDate.now()}.json")
                        }
                    )
                    ReserveDivider()
                    SettingRow(
                        icon = Icons.Rounded.Restore,
                        label = "Restore backup",
                        value = "Replace all data",
                        onClick = {
                            restoreLauncher.launch(arrayOf("application/json", "text/plain"))
                        }
                    )
                    val lastBackup = settings?.lastBackupAt ?: 0L
                    if (lastBackup > 0L) {
                        ReserveDivider()
                        Text(
                            text = "Last backup ${Dates.fullDate(lastBackup)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLow,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            item { SectionLabel(text = "About") }

            item {
                ReserveCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(
                                text = "Reserve $packageVersion",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textHigh
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "A quiet, offline expense tracker. Your data stays on this device — there is no account, no cloud, no ads and no telemetry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMed
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalReserveColors.current
    Surface(
        modifier = modifier
            .height(40.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.primary.copy(alpha = 0.12f) else colors.surface,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.border)
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.primary else colors.textMed
            )
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    val colors = LocalReserveColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textMed,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textHigh,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textLow
        )
        Spacer(modifier = Modifier.size(4.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.textLow,
            modifier = Modifier.size(18.dp)
        )
    }
}
