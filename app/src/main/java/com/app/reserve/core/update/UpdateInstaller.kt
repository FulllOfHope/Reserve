package com.app.reserve.core.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.app.reserve.MainActivity
import com.app.reserve.core.datastore.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Downloads the GitHub APK and hands installation to the system installer. */
class UpdateInstaller(
    private val context: Context,
    private val settings: SettingsDataStore,
    private val notifier: UpdateNotifier
) {
    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val _needInstallPermission = MutableStateFlow(false)
    val needInstallPermission: StateFlow<Boolean> = _needInstallPermission.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    suspend fun enqueue(update: UpdateInfo) = withContext(Dispatchers.IO) {
        runCatching {
            if (!update.apkUrl.startsWith("https://", ignoreCase = true)) {
                throw IllegalArgumentException("Update download link is unavailable.")
            }
            val filename = apkFilename(update.versionName)
            val request = DownloadManager.Request(Uri.parse(update.apkUrl))
                .setTitle("Reserve ${update.versionName}")
                .setDescription("Downloading the Reserve update")
                .setDestinationInExternalFilesDir(
                    appContext,
                    Environment.DIRECTORY_DOWNLOADS,
                    filename
                )
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            val downloadId = manager.enqueue(request)
            settings.setPendingUpdateDownload(downloadId, update.versionName)
            _message.value = "Downloading version ${update.versionName}."
        }.onFailure { error ->
            _message.value = error.message ?: "Could not start the update download."
        }
    }

    /** Called when DownloadManager reports completion or on app start for a pending id. */
    suspend fun onDownloadCompleted(downloadId: Long) = withContext(Dispatchers.IO) {
        val pending = settings.currentSettings()
        if (pending.pendingUpdateDownloadId != downloadId) return@withContext
        val status = queryStatus(downloadId)
        when (status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                notifier.showReadyToInstall(pending.pendingUpdateVersion)
                _message.value = "Version ${pending.pendingUpdateVersion} is ready to install."
            }
            DownloadManager.STATUS_FAILED -> {
                settings.clearPendingUpdateDownload()
                notifier.cancel()
                _message.value = "The update download failed. Please try again."
            }
            DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING,
            DownloadManager.STATUS_PAUSED -> Unit
            else -> Unit
        }
    }

    suspend fun checkPendingDownload() {
        val id = settings.currentSettings().pendingUpdateDownloadId
        if (id >= 0) onDownloadCompleted(id)
    }

    suspend fun promptInstall() = withContext(Dispatchers.IO) {
        val pending = settings.currentSettings()
        val id = pending.pendingUpdateDownloadId
        if (id < 0) {
            _message.value = "No downloaded update is waiting to install."
            return@withContext
        }
        if (queryStatus(id) != DownloadManager.STATUS_SUCCESSFUL) {
            _message.value = "The update is still downloading."
            return@withContext
        }
        if (!canRequestInstalls()) {
            _needInstallPermission.value = true
            return@withContext
        }
        runCatching {
            val file = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                apkFilename(pending.pendingUpdateVersion)
            )
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            appContext.startActivity(intent)
            settings.clearPendingUpdateDownload()
            notifier.cancel()
        }.onFailure { error ->
            _message.value = error.message ?: "Could not open the installer."
        }
    }

    fun canRequestInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            appContext.packageManager.canRequestPackageInstalls()

    suspend fun onInstallPermissionResult(granted: Boolean) {
        _needInstallPermission.value = false
        if (granted) promptInstall()
        else _message.value = "Install permission is needed to finish the update."
    }

    fun onInstallPermissionRequestHandled() {
        _needInstallPermission.value = false
    }

    fun installPermissionIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}")
        )

    fun openUpdateSheetIntent(): Intent = Intent(appContext, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(MainActivity.EXTRA_OPEN_INSTALL_UPDATE, true)
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun queryStatus(downloadId: Long): Int {
        val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId)) ?: return -1
        return cursor.use {
            if (!it.moveToFirst()) -1
            else it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
    }

    private fun apkFilename(version: String): String =
        "Reserve-v${version.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk"
}
