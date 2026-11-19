package com.app.reserve.core.update

import com.app.reserve.core.datastore.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Owns checking state, the in-app sheet payload and notification side effects. */
class UpdateRepository(
    private val settings: SettingsDataStore,
    private val checker: GitHubUpdateChecker,
    private val notifier: UpdateNotifier
) {

    private val _availableUpdate = MutableStateFlow<UpdateInfo?>(null)
    val availableUpdate: StateFlow<UpdateInfo?> = _availableUpdate.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** The Activity observes this and asks for POST_NOTIFICATIONS when needed. */
    private val _needNotificationPermission = MutableStateFlow(false)
    val needNotificationPermission: StateFlow<Boolean> = _needNotificationPermission.asStateFlow()

    private var notifiedVersion: String? = null

    /** Called once from Application.onCreate; respects the 6-hour gate. */
    suspend fun checkOnAppStart() {
        performCheck(force = false)
    }

    /** Manual check from Settings; bypasses the time gate and the enabled flag. */
    suspend fun checkNow(): UpdateInfo? = performCheck(force = true)

    /** Re-reads the release when the notification's content is tapped. */
    suspend fun refreshForSheet(): UpdateInfo? = performCheck(force = true, notify = false)

    private suspend fun performCheck(force: Boolean, notify: Boolean = true): UpdateInfo? {
        if (_checking.value) return null
        _checking.value = true
        try {
            val before = settings.currentSettings()
            if (!force &&
                (!before.updateChecksEnabled ||
                    !UpdatePolicy.isCheckDue(before.lastUpdateCheckAt))
            ) {
                return null
            }

            val now = System.currentTimeMillis()
            val found = withContext(Dispatchers.IO) { checker.checkForUpdate() }
            settings.setLastUpdateCheckAt(now)
            if (found == null) return null

            if (found.versionName.equals(before.dismissedUpdateVersion, ignoreCase = true)) {
                _availableUpdate.value = null
                return null
            }
            _availableUpdate.value = found

            val shouldNotify = UpdatePolicy.shouldNotify(
                update = found,
                dismissedVersion = before.dismissedUpdateVersion,
                notifiedVersion = notifiedVersion,
                lastCheckAt = before.lastUpdateCheckAt,
                now = now,
                ignoreTimeGate = force
            )
            if (notify && shouldNotify) {
                notifiedVersion = found.versionName
                if (!notifier.hasPermission()) {
                    _needNotificationPermission.value = true
                }
                notifier.show(found)
            }
            return found
        } finally {
            _checking.value = false
        }
    }

    suspend fun setUpdateChecksEnabled(enabled: Boolean) {
        settings.setUpdateChecksEnabled(enabled)
    }

    fun onNotificationPermissionRequestHandled() {
        _needNotificationPermission.value = false
    }

    fun onNotificationPermissionGranted() {
        _availableUpdate.value?.let { notifier.show(it) }
    }

    /** "Later" in the sheet: keep the version available, just hide the sheet. */
    fun dismiss() {
        _availableUpdate.value = null
    }

    /** "Skip this version": never surface this version again. */
    suspend fun skipVersion() {
        val version = _availableUpdate.value?.versionName ?: return
        settings.setDismissedUpdateVersion(version)
        notifier.cancel()
        _availableUpdate.value = null
    }
}
