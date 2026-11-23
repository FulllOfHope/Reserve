package com.app.reserve.core.application

import android.app.Application
import android.content.Context
import com.app.reserve.core.backup.BackupScheduler
import com.app.reserve.core.data.TRASH_RETENTION_MS
import com.app.reserve.core.data.ReserveRepository
import com.app.reserve.core.database.ReserveDatabase
import com.app.reserve.core.datastore.SettingsDataStore
import com.app.reserve.core.update.GitHubUpdateChecker
import com.app.reserve.core.update.UpdateInstaller
import com.app.reserve.core.update.UpdateNotifier
import com.app.reserve.core.update.UpdateRepository
import com.app.reserve.feature.sms.SmsImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReserveApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        container = AppContainer(this)
        container.scope.launch {
            container.repository.ensureSeeded()
            // Trash rows older than 30 days are removed permanently on startup.
            runCatching {
                container.repository.purgeExpiredTrash(System.currentTimeMillis() - TRASH_RETENTION_MS)
            }
            // One-time v3 repair must finish before any new SMS is imported.
            runCatching {
                if (!container.settings.currentSettings().ledgerRepairedV3 &&
                    container.repository.repairLedgerV3()
                ) {
                    container.settings.setLedgerRepairedV3()
                }
            }
            container.repository.generateDueRecurring()
            // Automatic backfill: the first run reads the whole inbox, later
            // runs only the recent overlap. No-op until SMS permission exists.
            runCatching { container.smsImporter.scanInbox() }
        }
        // Daily local backup: first launch runs one immediately, later starts keep
        // the existing 24-hour schedule. WorkManager survives reboot on its own.
        BackupScheduler.schedule(this)
        // Recover a download that finished while the app was in the background.
        container.scope.launch {
            runCatching { container.updateInstaller.checkPendingDownload() }
        }
        // One quiet, time-gated GitHub check per app start. The repository does
        // the request on IO and decides whether a notification is warranted.
        container.scope.launch {
            runCatching { container.updateRepository.checkOnAppStart() }
        }
    }
}

/**
 * A deliberately small hand-rolled container. There is one module and one
 * graph, so a full DI framework would add build time without adding clarity.
 */
class AppContainer(application: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: ReserveDatabase = ReserveDatabase.get(application)
    val settings = SettingsDataStore(application)
    val repository = ReserveRepository(database, settings)
    val smsImporter = SmsImporter(application, repository, settings)
    val updateChecker = GitHubUpdateChecker(currentVersion = installedVersion(application))
    val updateNotifier = UpdateNotifier(application)
    val updateInstaller = UpdateInstaller(application, settings, updateNotifier)
    val updateRepository = UpdateRepository(settings, updateChecker, updateNotifier)
}

private fun installedVersion(context: Context): String =
    runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "0.0.0"
