package com.app.reserve.core.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules the local, on-device daily backup. No network and no cloud sync. */
object BackupScheduler {
    private const val PERIODIC_NAME = "reserve_daily_local_backup"
    private const val IMMEDIATE_NAME = "reserve_local_backup_now"

    /** Safe to call on every app start: KEEP never resets an existing daily schedule. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyBackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(0, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
            .addTag(PERIODIC_NAME)
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    /** Lets the user trigger a daily backup immediately from Settings. */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<DailyBackupWorker>()
            .addTag(IMMEDIATE_NAME)
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
