package com.app.reserve.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.reserve.core.datastore.ReserveSettings
import com.app.reserve.core.datastore.SettingsDataStore
import com.app.reserve.core.datastore.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsDataStore: SettingsDataStore) : ViewModel() {

    /** Null until DataStore has produced its first value, so the lock is not skipped. */
    val settings: StateFlow<ReserveSettings?> = settingsDataStore.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setBiometricLock(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setPrimaryColorHex(hex: String) {
        viewModelScope.launch { settingsDataStore.setPrimaryColorHex(hex) }
    }

    fun setAppIconAlias(alias: String) {
        viewModelScope.launch { settingsDataStore.setAppIconAlias(alias) }
    }

    fun markBackupNow() {
        viewModelScope.launch { settingsDataStore.setLastBackupAt(System.currentTimeMillis()) }
    }
}
