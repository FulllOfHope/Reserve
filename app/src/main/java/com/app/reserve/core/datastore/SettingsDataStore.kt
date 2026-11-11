package com.app.reserve.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.app.reserve.core.util.IconSwitcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.reserveDataStore: DataStore<Preferences> by preferencesDataStore(name = "reserve_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ReserveSettings(
    val biometricLock: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColorHex: String = "#0FB70F",
    val appIconAlias: String = IconSwitcher.DEFAULT_ALIAS,
    val lastBackupAt: Long = 0L
)

class SettingsDataStore(private val context: Context) {

    private companion object {
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PRIMARY_COLOR_HEX = stringPreferencesKey("primary_color_hex")
        val APP_ICON_ALIAS = stringPreferencesKey("app_icon_alias")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    }

    val settings: Flow<ReserveSettings> = context.reserveDataStore.data.map { preferences ->
        ReserveSettings(
            biometricLock = preferences[BIOMETRIC_LOCK] ?: false,
            themeMode = runCatching {
                ThemeMode.valueOf(preferences[THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            primaryColorHex = preferences[PRIMARY_COLOR_HEX] ?: "#0FB70F",
            appIconAlias = preferences[APP_ICON_ALIAS] ?: IconSwitcher.DEFAULT_ALIAS,
            lastBackupAt = preferences[LAST_BACKUP_AT] ?: 0L
        )
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.reserveDataStore.edit { it[BIOMETRIC_LOCK] = enabled }
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
}
