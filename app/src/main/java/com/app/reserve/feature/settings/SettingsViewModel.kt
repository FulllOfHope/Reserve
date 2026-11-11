package com.app.reserve.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.reserve.core.datastore.ReserveSettings
import com.app.reserve.core.datastore.SettingsDataStore
import com.app.reserve.core.datastore.ThemeMode
import com.app.reserve.feature.sms.SmsImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val smsImporter: SmsImporter? = null
) : ViewModel() {

    /** Null until DataStore has produced its first value, so the lock is not skipped. */
    val settings: StateFlow<ReserveSettings?> = settingsDataStore.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    private val _smsScanning = MutableStateFlow(false)
    val smsScanning: StateFlow<Boolean> = _smsScanning.asStateFlow()

    private val _smsScanMessage = MutableStateFlow<String?>(null)
    val smsScanMessage: StateFlow<String?> = _smsScanMessage.asStateFlow()

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setBiometricLock(enabled) }
    }

    fun setHideAmounts(hidden: Boolean) {
        viewModelScope.launch { settingsDataStore.setHideAmounts(hidden) }
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

    /**
     * Scans the inbox now. Safe to call repeatedly: the importer dedupes every
     * message by fingerprint, and the DataStore progress makes later scans
     * incremental instead of re-reading years of texts.
     */
    fun scanSms() {
        val importer = smsImporter ?: return
        if (_smsScanning.value) return
        viewModelScope.launch {
            _smsScanning.value = true
            _smsScanMessage.value = null
            val outcome = runCatching { importer.scanInbox() }.getOrElse { throwable ->
                _smsScanning.value = false
                _smsScanMessage.value = "Could not scan SMS: ${throwable.message ?: "unknown error"}"
                return@launch
            }
            _smsScanning.value = false
            _smsScanMessage.value = when {
                outcome.permissionDenied -> "SMS permission is needed to scan the inbox."
                outcome.error != null -> "Could not read SMS: ${outcome.error}"
                outcome.scanned == 0 -> "No messages found."
                else -> buildString {
                    append("Scanned ${outcome.scanned} messages")
                    append(" · imported ${outcome.imported}")
                    if (outcome.duplicates > 0) append(" · ${outcome.duplicates} already saved")
                    if (outcome.ignored > 0) append(" · ${outcome.ignored} not bank alerts")
                }
            }
        }
    }
}
