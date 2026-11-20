package com.app.reserve.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    val primaryColorHex: String = "#C98A00",
    val appIconAlias: String = IconSwitcher.DEFAULT_ALIAS,
    val lastBackupAt: Long = 0L,
    val lastSmsScanAt: Long = 0L,
    val smsImportedTotal: Int = 0,
    /** One-time v3 ledger repair has completed successfully. */
    val ledgerRepairedV3: Boolean = false,
    /** Privacy-first GitHub Releases check for new app versions. */
    val updateChecksEnabled: Boolean = true,
    val lastUpdateCheckAt: Long = 0L,
    val dismissedUpdateVersion: String = ""
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
    }

    val settings: Flow<ReserveSettings> = context.reserveDataStore.data.map { preferences ->
        ReserveSettings(
            biometricLock = preferences[BIOMETRIC_LOCK] ?: false,
            hideAmounts = preferences[HIDE_AMOUNTS] ?: false,
            themeMode = runCatching {
                ThemeMode.valueOf(preferences[THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            primaryColorHex = preferences[PRIMARY_COLOR_HEX] ?: "#C98A00",
            appIconAlias = preferences[APP_ICON_ALIAS] ?: IconSwitcher.DEFAULT_ALIAS,
            lastBackupAt = preferences[LAST_BACKUP_AT] ?: 0L,
            lastSmsScanAt = preferences[LAST_SMS_SCAN_AT] ?: 0L,
            smsImportedTotal = preferences[SMS_IMPORTED_TOTAL] ?: 0,
            ledgerRepairedV3 = preferences[LEDGER_REPAIRED_V3] ?: false,
            updateChecksEnabled = preferences[UPDATE_CHECKS_ENABLED] ?: true,
            lastUpdateCheckAt = preferences[LAST_UPDATE_CHECK_AT] ?: 0L,
            dismissedUpdateVersion = preferences[DISMISSED_UPDATE_VERSION] ?: ""
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
        context.reserveDataStore.edit { it[APP_ICON_ALIAS] = alias }
        IconSwitcher.setAppIcon(context, alias)
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

    /** One-shot read used by the background scanner. */
    suspend fun currentSettings(): ReserveSettings = settings.first()
}
