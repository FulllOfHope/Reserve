package com.app.reserve.core.backup

import android.content.Context
import com.app.reserve.core.data.ReserveRepository
import com.app.reserve.core.datastore.SettingsDataStore
import com.app.reserve.core.domain.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Owns the local, on-device backup files. Keeps at most [MAX_BACKUPS] Reserve
 * backups and never touches unrelated files. No network, no cloud, no upload.
 */
class BackupCoordinator(
    private val context: Context,
    private val repository: ReserveRepository,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        const val FILE_PREFIX = "reserve-backup-"
        const val FILE_SUFFIX = ".json"
        const val MAX_BACKUPS = 5
        private const val TEMP_SUFFIX = ".tmp"
        private val FILE_NAME = Regex("^reserve-backup-\\d{4}-\\d{2}-\\d{2}-\\d{6}-\\d{3}\\.json$")
        private val NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss-SSS")
    }

    /** One local backup file, newest first when returned by [listLocalBackups]. */
    data class BackupFile(
        val name: String,
        val path: String,
        val createdAt: Long,
        val sizeBytes: Long
    )

    private val backupDir: File get() = File(context.filesDir, "backups")

    /** JSON used by the manual "Create backup" SAF export — includes all settings. */
    suspend fun createBackupJson(): String = BackupManager.encode(
        repository.snapshot(),
        settingsDataStore.currentSettings()
    )

    /** Writes a timestamped daily backup and prunes anything older than the newest five. */
    suspend fun createLocalBackup(): Result<BackupFile> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val json = BackupManager.encode(
                repository.snapshot(),
                settingsDataStore.currentSettings(),
                now
            )
            val directory = backupDir
            if (!directory.exists() && !directory.mkdirs()) {
                error("Could not create the local backup folder.")
            }
            val name = FILE_PREFIX + NAME_FORMAT.format(
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.systemDefault())
            ) + FILE_SUFFIX
            val target = File(directory, name)
            val temp = File(directory, name + TEMP_SUFFIX)
            runCatching {
                temp.writeText(json)
                if (!temp.renameTo(target)) error("Could not finalise the backup file.")
            }.onFailure { temp.delete() }.getOrThrow()

            settingsDataStore.setLastBackupAt(now)
            rotate()
            BackupFile(name, target.absolutePath, now, target.length())
        }
    }

    /**
     * Restores ledger rows and every preference from any compatible backup file.
     * Ledger replacement stays in the repository so Trash/Undo state remains intact.
     */
    suspend fun restoreBackup(text: String): Result<Long> = runCatching {
        val bundle = BackupManager.decodeBundle(text).getOrThrow()
        bundle.settings?.let { settingsDataStore.importBackupSettings(it) }
        repository.replaceAll(bundle.snapshot)
        val exportedAt = if (bundle.exportedAt > 0L) bundle.exportedAt else System.currentTimeMillis()
        settingsDataStore.setLastBackupAt(exportedAt)
        exportedAt
    }

    /**
     * Applies only the preferences from a backup, after the ledger has already
     * been restored through the existing ViewModel path.
     */
    suspend fun restoreSettingsOnly(text: String): Result<Unit> = runCatching {
        val bundle = BackupManager.decodeBundle(text).getOrThrow()
        bundle.settings?.let { settingsDataStore.importBackupSettings(it) }
        val exportedAt = if (bundle.exportedAt > 0L) bundle.exportedAt else System.currentTimeMillis()
        settingsDataStore.setLastBackupAt(exportedAt)
    }

    /** Newest backup first — this is the default selection in the restore picker. */
    fun listLocalBackups(): List<BackupFile> = runCatching {
        (backupDir.listFiles() ?: emptyArray())
            .asSequence()
            .filter { it.isFile && FILE_NAME.matches(it.name) }
            .map { file ->
                BackupFile(
                    name = file.name,
                    path = file.absolutePath,
                    createdAt = file.lastModified(),
                    sizeBytes = file.length()
                )
            }
            .sortedByDescending { it.name }
            .toList()
    }.getOrDefault(emptyList())

    fun readBackup(file: BackupFile): String? = runCatching { File(file.path).readText() }.getOrNull()

    /** Deletes only Reserve-generated backups beyond the newest five, plus stale temp files. */
    private fun rotate() {
        val files = (backupDir.listFiles() ?: emptyArray()).asSequence()
        files.filter { it.isFile && FILE_NAME.matches(it.name) }
            .sortedByDescending { it.name }
            .drop(MAX_BACKUPS)
            .forEach { runCatching { it.delete() } }
        files.filter { it.isFile && it.name.startsWith(FILE_PREFIX) && it.name.endsWith(TEMP_SUFFIX) }
            .forEach { runCatching { it.delete() } }
    }
}
