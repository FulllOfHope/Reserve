package com.app.reserve.core.datastore

import kotlinx.serialization.Serializable

/**
 * Serializable form of every DataStore preference. Stored inside the backup JSON
 * so "restore" really restores the whole app, not just the ledger tables.
 * ThemeMode is kept as its enum name to avoid a serializer on the enum itself.
 */
@Serializable
data class BackupSettings(
    val biometricLock: Boolean = false,
    val hideAmounts: Boolean = false,
    val themeMode: String = "SYSTEM",
    val primaryColorHex: String = "#00B843",
    val appIconAlias: String = "",
    val lastBackupAt: Long = 0L,
    val lastSmsScanAt: Long = 0L,
    val smsImportedTotal: Int = 0,
    val ledgerRepairedV3: Boolean = false,
    val updateChecksEnabled: Boolean = true,
    val lastUpdateCheckAt: Long = 0L,
    val dismissedUpdateVersion: String = "",
    val snoozedUpdateVersion: String = "",
    val snoozeUpdateReminderUntil: Long = 0L,
    val pendingUpdateDownloadId: Long = -1L,
    val pendingUpdateVersion: String = "",
    val balanceAccountIds: Set<String>? = null
)

fun ReserveSettings.toBackupSettings(): BackupSettings = BackupSettings(
    biometricLock = biometricLock,
    hideAmounts = hideAmounts,
    themeMode = themeMode.name,
    primaryColorHex = primaryColorHex,
    appIconAlias = appIconAlias,
    lastBackupAt = lastBackupAt,
    lastSmsScanAt = lastSmsScanAt,
    smsImportedTotal = smsImportedTotal,
    ledgerRepairedV3 = ledgerRepairedV3,
    updateChecksEnabled = updateChecksEnabled,
    lastUpdateCheckAt = lastUpdateCheckAt,
    dismissedUpdateVersion = dismissedUpdateVersion,
    snoozedUpdateVersion = snoozedUpdateVersion,
    snoozeUpdateReminderUntil = snoozeUpdateReminderUntil,
    pendingUpdateDownloadId = pendingUpdateDownloadId,
    pendingUpdateVersion = pendingUpdateVersion,
    balanceAccountIds = balanceAccountIds
)

fun BackupSettings.toReserveSettings(): ReserveSettings = ReserveSettings(
    biometricLock = biometricLock,
    hideAmounts = hideAmounts,
    themeMode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.SYSTEM),
    primaryColorHex = primaryColorHex,
    appIconAlias = appIconAlias,
    lastBackupAt = lastBackupAt,
    lastSmsScanAt = lastSmsScanAt,
    smsImportedTotal = smsImportedTotal,
    ledgerRepairedV3 = ledgerRepairedV3,
    updateChecksEnabled = updateChecksEnabled,
    lastUpdateCheckAt = lastUpdateCheckAt,
    dismissedUpdateVersion = dismissedUpdateVersion,
    snoozedUpdateVersion = snoozedUpdateVersion,
    snoozeUpdateReminderUntil = snoozeUpdateReminderUntil,
    pendingUpdateDownloadId = pendingUpdateDownloadId,
    pendingUpdateVersion = pendingUpdateVersion,
    balanceAccountIds = balanceAccountIds
)
