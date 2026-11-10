package com.app.reserve.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.reserveDataStore: DataStore<Preferences> by preferencesDataStore(name = "reserve_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ReserveSettings(
    val biometricLock: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lastBackupAt: Long = 0L
)

class SettingsDataStore(private val context: Context) {

    private companion object {
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    }

    val settings: Flow<ReserveSettings> = context.reserveDataStore.data.map { preferences ->
        ReserveSettings(
            biometricLock = preferences[BIOMETRIC_LOCK] ?: false,
            themeMode = runCatching {
                ThemeMode.valueOf(preferences[THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            lastBackupAt = preferences[LAST_BACKUP_AT] ?: 0L
        )
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.reserveDataStore.edit { it[BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.reserveDataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setLastBackupAt(timestamp: Long) {
        context.reserveDataStore.edit { it[LAST_BACKUP_AT] = timestamp }
    }
}
