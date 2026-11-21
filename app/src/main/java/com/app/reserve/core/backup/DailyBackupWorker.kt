package com.app.reserve.core.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.reserve.core.application.ReserveApplication

/** Writes one local backup containing all ledger rows and all preferences. */
class DailyBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? ReserveApplication ?: return Result.failure()
        val coordinator = BackupCoordinator(
            applicationContext,
            application.container.repository,
            application.container.settings
        )
        return if (coordinator.createLocalBackup().isSuccess) Result.success() else Result.retry()
    }
}
