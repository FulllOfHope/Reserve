package com.app.reserve.core.application

import android.app.Application
import com.app.reserve.core.data.ReserveRepository
import com.app.reserve.core.database.ReserveDatabase
import com.app.reserve.core.datastore.SettingsDataStore
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
    }
}

/**
 * A deliberately small hand-rolled container. There is one module and one
 * graph, so a full DI framework would add build time without adding clarity.
 */
class AppContainer(application: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: ReserveDatabase = ReserveDatabase.get(application)
    val repository = ReserveRepository(database)
    val settings = SettingsDataStore(application)
    val smsImporter = SmsImporter(application, repository, settings)
}
