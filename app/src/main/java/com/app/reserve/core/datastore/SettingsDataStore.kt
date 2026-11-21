package com.app.reserve.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.app.reserve.core.util.IconSwitcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.reserveDataStore: DataStore<Preferences> by preferencesDataStore(name = "reserve_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ReserveSettings(
    val biometricLock: Boolean = false,
    val hideAmounts: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColorHex: String = "#00B843",
    val appIconAlias: String = IconSwitcher.DEFAULT_ALIAS,
    val lastBackupAt: Long = 0L,
    val lastSmsScanAt: Long = 0L,
    val smsImportedTotal: Int = 0,
    /** One-time v3 ledger repair has completed successfully. */
    val ledgerRepairedV3: Boolean = false,
    /** Privacy-first GitHub Releases check for new app versions. */
    val updateChecksEnabled: Boolean = true,
    val lastUpdateCheckAt: Long = 0L,
    val dismissedUpdateVersion: String = "",
    /** Reminder snooze is version-specific so a newer release can still notify. */
    val snoozedUpdateVersion: String = "",
    val snoozeUpdateReminderUntil: Long = 0L,
    val pendingUpdateDownloadId: Long = -1L,
    val pendingUpdateVersion: String = "",
    /** Null means combine every non-archived account; an empty set means none. */
    val balanceAccountIds: Set<String>? = null
)

class SettingsDataStore(private val context: Context) {

    private companion object {
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PRIMARY_COLOR_HEX = stringPreferencesKey("primary_color_hex")
        val APP_ICON_ALIAS = stringPreferencesKey("app_icon_alias")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_SMS_SCAN_AT = longPreferencesKey("last_sms_scan_at")
        val SMS_IMPORTED_TOTAL = intPreferencesKey("sms_imported_total")
        val LEDGER_REPAIRED_V3 = booleanPreferencesKey("ledger_repaired_v3")
        val UPDATE_CHECKS_ENABLED = booleanPreferencesKey("update_checks_enabled")
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
        val DISMISSED_UPDATE_VERSION = stringPreferencesKey("dismissed_update_version")
        val SNOOZED_UPDATE_VERSION = stringPreferencesKey("snoozed_update_version")
        val SNOOZE_UPDATE_REMINDER_UNTIL = longPreferencesKey("snooze_update_reminder_until")
        val PENDING_UPDATE_DOWNLOAD_ID = longPreferencesKey("pending_update_download_id")
        val PENDING_UPDATE_VERSION = stringPreferencesKey("pending_update_version")
        val BALANCE_ACCOUNT_IDS = stringSetPreferencesKey("balance_account_ids")
    }

    val settings: Flow<ReserveSettings> = context.reserveDataStore.data.map { preferences ->
        ReserveSettings(
            biometricLock = preferences[BIOMETRIC_LOCK] ?: false,
            hideAmounts = preferences[HIDE_AMOUNTS] ?: false,
            themeMode = runCatching {
                ThemeMode.valueOf(preferences[THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            primaryColorHex = preferences[PRIMARY_COLOR_HEX] ?: "#00B843",
            appIconAlias = IconSwitcher.normalizeAlias(preferences[APP_ICON_ALIAS]),
            lastBackupAt = preferences[LAST_BACKUP_AT] ?: 0L,
            lastSmsScanAt = preferences[LAST_SMS_SCAN_AT] ?: 0L,
            smsImportedTotal = preferences[SMS_IMPORTED_TOTAL] ?: 0,
            ledgerRepairedV3 = preferences[LEDGER_REPAIRED_V3] ?: false,
            updateChecksEnabled = preferences[UPDATE_CHECKS_ENABLED] ?: true,
            lastUpdateCheckAt = preferences[LAST_UPDATE_CHECK_AT] ?: 0L,
            dismissedUpdateVersion = preferences[DISMISSED_UPDATE_VERSION] ?: "",
            snoozedUpdateVersion = preferences[SNOOZED_UPDATE_VERSION] ?: "",
            snoozeUpdateReminderUntil = preferences[SNOOZE_UPDATE_REMINDER_UNTIL] ?: 0L,
            pendingUpdateDownloadId = preferences[PENDING_UPDATE_DOWNLOAD_ID] ?: -1L,
            pendingUpdateVersion = preferences[PENDING_UPDATE_VERSION] ?: "",
            balanceAccountIds = preferences[BALANCE_ACCOUNT_IDS]
        )
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.reserveDataStore.edit { it[BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setHideAmounts(hidden: Boolean) {
        context.reserveDataStore.edit { it[HIDE_AMOUNTS] = hidden }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.reserveDataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setPrimaryColorHex(hex: String) {
        context.reserveDataStore.edit { it[PRIMARY_COLOR_HEX] = hex }
    }

    suspend fun setAppIconAlias(alias: String) {
        val normalized = IconSwitcher.normalizeAlias(alias)
        context.reserveDataStore.edit { it[APP_ICON_ALIAS] = normalized }
        IconSwitcher.setAppIcon(context, normalized)
    }

    /**
     * A removed alias (e.g. the old monochrome option) can leave the launcher
     * without an enabled icon after an update. Restore the default in that case.
     */
    suspend fun reconcileAppIconAlias() {
        val stored = context.reserveDataStore.data.first()[APP_ICON_ALIAS]
        if (stored != null && IconSwitcher.isLegacyAlias(stored)) {
            setAppIconAlias(IconSwitcher.DEFAULT_ALIAS)
        }
    }

    suspend fun setLastBackupAt(timestamp: Long) {
        context.reserveDataStore.edit { it[LAST_BACKUP_AT] = timestamp }
    }

    suspend fun setLastSmsScanAt(timestamp: Long) {
        context.reserveDataStore.edit { it[LAST_SMS_SCAN_AT] = timestamp }
    }

    suspend fun addSmsImported(count: Int) {
        if (count <= 0) return
        context.reserveDataStore.edit {
            it[SMS_IMPORTED_TOTAL] = (it[SMS_IMPORTED_TOTAL] ?: 0) + count
        }
    }

    suspend fun setLedgerRepairedV3(repaired: Boolean = true) {
        context.reserveDataStore.edit { it[LEDGER_REPAIRED_V3] = repaired }
    }

    suspend fun setUpdateChecksEnabled(enabled: Boolean) {
        context.reserveDataStore.edit { it[UPDATE_CHECKS_ENABLED] = enabled }
    }

    suspend fun setLastUpdateCheckAt(timestamp: Long) {
        context.reserveDataStore.edit { it[LAST_UPDATE_CHECK_AT] = timestamp }
    }

    suspend fun setDismissedUpdateVersion(version: String) {
        context.reserveDataStore.edit { it[DISMISSED_UPDATE_VERSION] = version }
    }

    suspend fun setUpdateReminderSnooze(version: String, until: Long) {
        context.reserveDataStore.edit { preferences ->
            preferences[SNOOZED_UPDATE_VERSION] = version
            preferences[SNOOZE_UPDATE_REMINDER_UNTIL] = until
        }
    }

    suspend fun clearUpdateReminderSnooze() {
        context.reserveDataStore.edit { preferences ->
            preferences.remove(SNOOZED_UPDATE_VERSION)
            preferences.remove(SNOOZE_UPDATE_REMINDER_UNTIL)
        }
    }

    suspend fun setPendingUpdateDownload(downloadId: Long, version: String) {
        context.reserveDataStore.edit { preferences ->
            preferences[PENDING_UPDATE_DOWNLOAD_ID] = downloadId
            preferences[PENDING_UPDATE_VERSION] = version
        }
    }

    suspend fun clearPendingUpdateDownload() {
        context.reserveDataStore.edit { preferences ->
            preferences.remove(PENDING_UPDATE_DOWNLOAD_ID)
            preferences.remove(PENDING_UPDATE_VERSION)
        }
    }

    /** Null restores the default "all active accounts" behaviour. */
    suspend fun setBalanceAccountIds(ids: Set<String>?) {
        context.reserveDataStore.edit { preferences ->
            if (ids == null) {
                preferences.remove(BALANCE_ACCOUNT_IDS)
            } else {
                preferences[BALANCE_ACCOUNT_IDS] = ids
            }
        }
    }

    /**
     * Restores every preference from a backup in one DataStore transaction.
     * The launcher icon alias is applied afterwards (it is a system-side change).
     */
    suspend fun importBackupSettings(backup: BackupSettings) {
        val restored = backup.toReserveSettings()
        val alias = IconSwitcher.normalizeAlias(restored.appIconAlias)
        context.reserveDataStore.edit { preferences ->
            preferences[BIOMETRIC_LOCK] = restored.biometricLock
            preferences[HIDE_AMOUNTS] = restored.hideAmounts
            preferences[THEME_MODE] = restored.themeMode.name
            preferences[PRIMARY_COLOR_HEX] = restored.primaryColorHex
            preferences[APP_ICON_ALIAS] = alias
            preferences[LAST_BACKUP_AT] = restored.lastBackupAt
            preferences[LAST_SMS_SCAN_AT] = restored.lastSmsScanAt
            preferences[SMS_IMPORTED_TOTAL] = restored.smsImportedTotal
            preferences[LEDGER_REPAIRED_V3] = restored.ledgerRepairedV3
            preferences[UPDATE_CHECKS_ENABLED] = restored.updateChecksEnabled
            preferences[LAST_UPDATE_CHECK_AT] = restored.lastUpdateCheckAt
            preferences[DISMISSED_UPDATE_VERSION] = restored.dismissedUpdateVersion
            preferences[SNOOZED_UPDATE_VERSION] = restored.snoozedUpdateVersion
            preferences[SNOOZE_UPDATE_REMINDER_UNTIL] = restored.snoozeUpdateReminderUntil
            preferences[PENDING_UPDATE_DOWNLOAD_ID] = restored.pendingUpdateDownloadId
            preferences[PENDING_UPDATE_VERSION] = restored.pendingUpdateVersion
            val ids = restored.balanceAccountIds
            if (ids == null) preferences.remove(BALANCE_ACCOUNT_IDS)
            else preferences[BALANCE_ACCOUNT_IDS] = ids
        }
        runCatching { IconSwitcher.setAppIcon(context, alias) }
    }

    /** One-shot read used by the background scanner. */
    suspend fun currentSettings(): ReserveSettings = settings.first()
}
