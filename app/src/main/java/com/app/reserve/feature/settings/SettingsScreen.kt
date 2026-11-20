package com.app.reserve.feature.settings

import android.widget.Toast
import com.app.reserve.core.util.IconSwitcher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.reserve.R
import com.app.reserve.core.data.ImportSummary
import com.app.reserve.core.datastore.ReserveSettings
import com.app.reserve.core.datastore.ThemeMode
import com.app.reserve.core.designsystem.AccentSwatches
import com.app.reserve.core.designsystem.EnteButton
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.normalizedAccentHex
import com.app.reserve.core.designsystem.ReserveCard
import com.app.reserve.core.designsystem.ReserveDivider
import com.app.reserve.core.designsystem.radius_lg
import com.app.reserve.core.designsystem.radius_md
import com.app.reserve.core.designsystem.radius_xl
import com.app.reserve.core.designsystem.spacing_lg
import com.app.reserve.core.designsystem.spacing_md
import com.app.reserve.core.designsystem.spacing_sm
import com.app.reserve.core.designsystem.spacing_xs
import com.app.reserve.core.designsystem.spacing_xl
import com.app.reserve.core.designsystem.spacing_xxl
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
    onToggleHideAmounts: (Boolean) -> Unit = {},
    onThemeMode: (ThemeMode) -> Unit,
    onSetPrimaryColorHex: (String) -> Unit = {},
    onSetAppIconAlias: (String) -> Unit = {},
    onOpenCategories: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenRecurring: () -> Unit,
    exportCsv: suspend () -> String,
    importCsv: suspend (String) -> ImportSummary,
    createBackup: suspend () -> String,
    restoreBackup: suspend (String) -> Boolean,
    onBackupCreated: () -> Unit,
    smsScanning: Boolean = false,
    smsScanMessage: String? = null,
    onScanSms: () -> Unit = {},
    updateChecking: Boolean = false,
    updateCheckMessage: String? = null,
    onToggleUpdateChecks: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onDismissUpdateCheckMessage: () -> Unit = {}
) {
    val colors = LocalReserveColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showColorPicker by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }

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
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 108.dp)
        ) {
            // Appearance block
            item {
                SettingsSectionHeader(text = "APPEARANCE")
                ReserveCard(modifier = Modifier.padding(horizontal = spacing_xxl)) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textBase
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Follow the system, or keep Reserve light or dark.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textLight
                    )
                    Spacer(modifier = Modifier.height(spacing_md))
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

                    ReserveDivider(modifier = Modifier.padding(vertical = spacing_lg))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showColorPicker = true }
                            .padding(vertical = spacing_xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colors.primaryDefault)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Accent Color",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textBase
                            )
                            Text(
                                text = "Customise main action & indicator colour",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textLight
                            )
                        }
                        Text(
                            text = settings?.primaryColorHex?.let(::normalizedAccentHex) ?: "#00B843",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLighter
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = colors.textLighter,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    ReserveDivider(modifier = Modifier.padding(vertical = spacing_lg))

                    // App Icon Option
                    val currentAlias = settings?.appIconAlias ?: IconSwitcher.DEFAULT_ALIAS
                    val currentIconLabel = appIconLabel(currentAlias)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showIconPicker = true }
                            .padding(vertical = spacing_xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    when (currentAlias) {
                                        IconSwitcher.GLASSY_ALIAS -> Color(0xFF16181D)
                                        IconSwitcher.MONOCHROME_ALIAS -> Color(0xFF151515)
                                        else -> Color(0xFF00B843)
                                    }
                                )
                                .border(1.dp, colors.strokeFaint, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Icon",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textBase
                            )
                            Text(
                                text = "Choose Default, Glassy or Monochrome launcher icon",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textLight
                            )
                        }
                        Text(
                            text = currentIconLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLighter
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = colors.textLighter,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // SMS automation block
            item {
                SettingsSectionHeader(text = "SMS AUTOMATION")
                ReserveCard(modifier = Modifier.padding(horizontal = spacing_xxl)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sms,
                            contentDescription = null,
                            tint = colors.primaryDefault,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatic capture",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textBase
                            )
                            Text(
                                text = "Bank debit and credit alerts become expenses and income. New banks become accounts automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textLight
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(spacing_md))
                    val lastScan = settings?.lastSmsScanAt ?: 0L
                    val captured = settings?.smsImportedTotal ?: 0
                    Text(
                        text = if (lastScan == 0L) {
                            "Not scanned yet"
                        } else {
                            "Last scan ${Dates.fullDate(lastScan)} · $captured captured from SMS"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textBase
                    )
                    if (smsScanMessage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = smsScanMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLight
                        )
                    }
                    Spacer(modifier = Modifier.height(spacing_md))
                    Button(
                        onClick = onScanSms,
                        enabled = !smsScanning,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(radius_md),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryDefault,
                            contentColor = colors.textReverse,
                            disabledContainerColor = colors.fillDark,
                            disabledContentColor = colors.textLight
                        )
                    ) {
                        Text(if (smsScanning) "Scanning SMS…" else "Scan SMS inbox now")
                    }
                    Spacer(modifier = Modifier.height(spacing_sm))
                    Text(
                        text = "Parsing happens entirely on this device. Nothing is uploaded.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLighter
                    )
                }
            }

            // Privacy block
            item {
                SettingsSectionHeader(text = "PRIVACY")
                ReserveCard(modifier = Modifier.padding(horizontal = spacing_xxl)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            tint = colors.primaryDefault,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hide amounts",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textBase
                            )
                            Text(
                                text = "Mask balances and every transaction amount, for using Reserve in public.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textLight
                            )
                        }
                        Switch(
                            checked = settings?.hideAmounts == true,
                            onCheckedChange = onToggleHideAmounts,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.textReverse,
                                checkedTrackColor = colors.primaryDefault,
                                uncheckedThumbColor = colors.textLighter,
                                uncheckedTrackColor = colors.fillDark,
                                uncheckedBorderColor = colors.strokeFaint
                            )
                        )
                    }

                    ReserveDivider(modifier = Modifier.padding(vertical = spacing_lg))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = colors.primaryDefault,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Biometric lock",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textBase
                            )
                            Text(
                                text = "Ask for your fingerprint or device PIN when Reserve opens.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textLight
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
                                checkedThumbColor = colors.textReverse,
                                checkedTrackColor = colors.primaryDefault,
                                uncheckedThumbColor = colors.textLighter,
                                uncheckedTrackColor = colors.fillDark,
                                uncheckedBorderColor = colors.strokeFaint
                            )
                        )
                    }
                }
            }

            // App updates block
            item {
                SettingsSectionHeader(text = "APP UPDATES")
                ReserveCard(modifier = Modifier.padding(horizontal = spacing_xxl)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = colors.primaryDefault,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatic update checks",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textBase
                            )
                            Text(
                                text = "A quiet check for new versions, about every six hours.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textLight
                            )
                        }
                        Switch(
                            checked = settings?.updateChecksEnabled != false,
                            onCheckedChange = onToggleUpdateChecks,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.textReverse,
                                checkedTrackColor = colors.primaryDefault,
                                uncheckedThumbColor = colors.textLighter,
                                uncheckedTrackColor = colors.fillDark,
                                uncheckedBorderColor = colors.strokeFaint
                            )
                        )
                    }

                    ReserveDivider(modifier = Modifier.padding(vertical = spacing_lg))

                    val lastCheck = settings?.lastUpdateCheckAt ?: 0L
                    Text(
                        text = if (lastCheck == 0L) {
                            "Not checked yet"
                        } else {
                            "Last checked ${Dates.fullDate(lastCheck)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLighter
                    )
                    Spacer(modifier = Modifier.height(spacing_md))
                    EnteButton(
                        text = if (updateChecking) "Checking…" else "Check for updates",
                        onClick = {
                            onDismissUpdateCheckMessage()
                            onCheckForUpdates()
                        },
                        enabled = !updateChecking,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (updateCheckMessage != null) {
                        Spacer(modifier = Modifier.height(spacing_xs))
                        Text(
                            text = updateCheckMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLight
                        )
                    }
                    Spacer(modifier = Modifier.height(spacing_sm))
                    Text(
                        text = "Checked with GitHub. No analytics or tracking.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLighter
                    )
                }
            }

            // Organise block
            item {
                SettingsSectionHeader(text = "ORGANISE")
                ReserveCard(modifier = Modifier.padding(horizontal = spacing_xxl)) {
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
                        icon = Icons.Outlined.Repeat,
                        label = "Recurring",
                        value = "${state.recurring.count { !it.paused }}",
                        onClick = onOpenRecurring
                    )
                }
            }

            // Data block
            item {
                SettingsSectionHeader(text = "DATA")
                ReserveCard(modifier = Modifier.padding(horizontal = spacing_xxl)) {
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
                            color = colors.textLighter,
                            modifier = Modifier.padding(vertical = spacing_lg)
                        )
                    }
                }
            }

            item {
                val uriHandler = LocalUriHandler.current
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing_xxl, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Reserve $packageVersion",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textBase
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Developed by @FulllOfHope",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primaryDefault,
                        modifier = Modifier
                            .clip(RoundedCornerShape(radius_md))
                            .clickable {
                                runCatching { uriHandler.openUri("https://github.com/FulllOfHope") }
                            }
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Designed with 💛 by Ente design team",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLighter,
                        modifier = Modifier
                            .clip(RoundedCornerShape(radius_md))
                            .clickable {
                                runCatching { uriHandler.openUri("https://github.com/Ente/Ente") }
                            }
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerBottomSheet(
            initialHex = settings?.primaryColorHex?.let(::normalizedAccentHex) ?: "#00B843",
            onApplyColor = onSetPrimaryColorHex,
            onDismiss = { showColorPicker = false }
        )
    }

    if (showIconPicker) {
        AppIconPickerBottomSheet(
            currentAlias = settings?.appIconAlias ?: IconSwitcher.DEFAULT_ALIAS,
            onSelectAlias = onSetAppIconAlias,
            onDismiss = { showIconPicker = false }
        )
    }
}

private fun appIconLabel(alias: String): String = when (alias) {
    IconSwitcher.GLASSY_ALIAS -> "Glassy"
    IconSwitcher.MONOCHROME_ALIAS -> "Monochrome"
    else -> "Default"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppIconPickerBottomSheet(
    currentAlias: String,
    onSelectAlias: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalReserveColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.fillBase,
        contentWindowInsets = { WindowInsets.ime },
        shape = RoundedCornerShape(topStart = radius_xl, topEnd = radius_xl)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing_xxl)
                .navigationBarsPadding()
                .padding(bottom = spacing_lg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing_md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Icon Style",
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.textBase
                    )
                    Text(
                        text = "Choose a launcher look",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textLight
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = colors.textLighter
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing_md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing_md)
            ) {
                AppIconChoiceCard(
                    label = "Default",
                    selected = currentAlias == IconSwitcher.DEFAULT_ALIAS,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSelectAlias(IconSwitcher.DEFAULT_ALIAS)
                        onDismiss()
                    }
                ) {
                    AppIconPreview(IconSwitcher.DEFAULT_ALIAS)
                }
                AppIconChoiceCard(
                    label = "Glassy",
                    selected = currentAlias == IconSwitcher.GLASSY_ALIAS,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSelectAlias(IconSwitcher.GLASSY_ALIAS)
                        onDismiss()
                    }
                ) {
                    AppIconPreview(IconSwitcher.GLASSY_ALIAS)
                }
                AppIconChoiceCard(
                    label = "Monochrome",
                    selected = currentAlias == IconSwitcher.MONOCHROME_ALIAS,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSelectAlias(IconSwitcher.MONOCHROME_ALIAS)
                        onDismiss()
                    }
                ) {
                    AppIconPreview(IconSwitcher.MONOCHROME_ALIAS)
                }
            }

            Spacer(modifier = Modifier.height(spacing_md))
            Text(
                text = "Your transactions and settings stay exactly where they are.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLighter
            )
        }
    }
}

@Composable
private fun AppIconChoiceCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit
) {
    val colors = LocalReserveColors.current
    Surface(
        modifier = modifier
            .height(156.dp)
            .clip(RoundedCornerShape(radius_lg))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius_lg),
        color = if (selected) colors.primaryContainer else colors.fillLight,
        border = BorderStroke(
            1.dp,
            if (selected) colors.primaryDefault else colors.strokeFaint
        ),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing_md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    preview()
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(colors.primaryDefault),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = colors.textReverse,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(spacing_sm))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (selected) colors.textBase else colors.textLight
            )
        }
    }
}

@Composable
private fun AppIconPreview(alias: String) {
    val colors = LocalReserveColors.current
    val previewShape = RoundedCornerShape(14.dp)

    when (alias) {
        IconSwitcher.GLASSY_ALIAS -> Image(
            painter = painterResource(R.drawable.ic_preview_glassy),
            contentDescription = "Glassy icon preview",
            modifier = Modifier
                .size(56.dp)
                .clip(previewShape)
        )
        IconSwitcher.MONOCHROME_ALIAS -> {
            val background = if (colors.isDark) Color(0xFFF4F4F5) else Color(0xFF111315)
            val markColor = if (colors.isDark) Color(0xFF111315) else Color.White
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(previewShape)
                    .background(background)
                    .border(1.dp, colors.strokeFaint, previewShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_reserve_mark),
                    contentDescription = "Monochrome icon preview",
                    colorFilter = ColorFilter.tint(markColor),
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        else -> Box(
            modifier = Modifier
                .size(56.dp)
                .clip(previewShape)
                .background(Color(0xFF00B843)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_reserve_mark),
                contentDescription = "Default icon preview",
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerBottomSheet(
    initialHex: String,
    onApplyColor: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalReserveColors.current
    var hexInput by remember { mutableStateOf(initialHex) }

    val presetSwatches = AccentSwatches.map { it.lightHex }

    fun isValidHex(input: String): Boolean {
        val clean = if (input.startsWith("#")) input else "#$input"
        if (clean.length != 7) return false
        return runCatching { Color(android.graphics.Color.parseColor(clean)) }.isSuccess
    }

    val isValid = isValidHex(hexInput)
    val parsedColor = if (isValid) {
        val clean = if (hexInput.startsWith("#")) hexInput else "#$hexInput"
        runCatching { Color(android.graphics.Color.parseColor(clean)) }.getOrDefault(colors.primaryDefault)
    } else {
        colors.primaryDefault
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.fillBase,
        contentWindowInsets = { WindowInsets.ime },
        shape = RoundedCornerShape(topStart = radius_xl, topEnd = radius_xl)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing_xxl)
                .navigationBarsPadding()
                .padding(bottom = spacing_lg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing_md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Choose Accent Color",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textBase,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = colors.textBase
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing_md))

            Text(
                text = "SWATCHES",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textLighter
            )
            Spacer(modifier = Modifier.height(spacing_sm))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                presetSwatches.chunked(5).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { swatchHex ->
                            val swatchColor = runCatching { Color(android.graphics.Color.parseColor(swatchHex)) }.getOrDefault(Color.Green)
                            val isSelected = hexInput.equals(swatchHex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .then(
                                        if (isSelected) Modifier.border(3.dp, colors.textBase, CircleShape)
                                        else Modifier
                                    )
                                    .clickable {
                                        hexInput = swatchHex
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing_xl))

            Text(
                text = "CUSTOM HEX CODE",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textLighter
            )
            Spacer(modifier = Modifier.height(spacing_xs))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(radius_md))
                        .background(parsedColor)
                        .border(1.dp, colors.strokeFaint, RoundedCornerShape(radius_md))
                )
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        if (input.length <= 7) {
                            hexInput = input.uppercase()
                        }
                    },
                    placeholder = { Text("#00B843", color = colors.textLighter) },
                    singleLine = true,
                    isError = !isValid && hexInput.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(radius_md),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primaryDefault,
                        unfocusedBorderColor = colors.strokeFaint,
                        focusedContainerColor = colors.fillLight,
                        unfocusedContainerColor = colors.fillLight,
                        focusedTextColor = colors.textBase,
                        unfocusedTextColor = colors.textBase
                    )
                )
            }

            Spacer(modifier = Modifier.height(spacing_xxl))

            Button(
                onClick = {
                    val formattedHex = if (hexInput.startsWith("#")) hexInput else "#$hexInput"
                    onApplyColor(formattedHex)
                    onDismiss()
                },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(radius_xl),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryDefault,
                    contentColor = colors.textReverse,
                    disabledContainerColor = colors.fillDark,
                    disabledContentColor = colors.textLighter
                )
            ) {
                Text("Apply Color", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    val colors = LocalReserveColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing_xxl)) {
        Spacer(modifier = Modifier.height(spacing_lg))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            color = colors.textLighter
        )
        Spacer(modifier = Modifier.height(spacing_lg))
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
        shape = RoundedCornerShape(radius_md),
        color = if (selected) colors.primaryDefault.copy(alpha = 0.12f) else colors.fillLight,
        border = if (selected) BorderStroke(1.dp, colors.primaryDefault) else null
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
            .padding(vertical = spacing_lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textLight,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textBase,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textLighter
        )
        Spacer(modifier = Modifier.size(4.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.textLighter,
            modifier = Modifier.size(18.dp)
        )
    }
}
