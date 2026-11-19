package com.app.reserve.feature.sms

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.app.reserve.core.data.ReserveRepository
import com.app.reserve.core.datastore.SettingsDataStore
import com.app.reserve.core.domain.ParsedSms
import com.app.reserve.core.domain.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmsScanOutcome(
    val scanned: Int = 0,
    val imported: Int = 0,
    val duplicates: Int = 0,
    val ignored: Int = 0,
    val permissionDenied: Boolean = false,
    val error: String? = null
)

/**
 * Reads bank SMS on-device and turns them into ledger rows.
 *
 * The first scan covers the whole inbox. Later scans re-read a three-day
 * overlap so nothing is missed when the app was force-stopped, and the
 * fingerprint table makes that overlap free of duplicates.
 */
class SmsImporter(
    context: Context,
    private val repository: ReserveRepository,
    private val settings: SettingsDataStore
) {
    private val appContext = context.applicationContext

    suspend fun scanInbox(forceFull: Boolean = false): SmsScanOutcome = withContext(Dispatchers.IO) {
        if (!canReadSms()) return@withContext SmsScanOutcome(permissionDenied = true)

        val current = runCatching { settings.currentSettings() }.getOrNull()
        val since = when {
            forceFull -> 0L
            (current?.lastSmsScanAt ?: 0L) > 0L ->
                (current!!.lastSmsScanAt - OVERLAP_MILLIS).coerceAtLeast(0L)
            else -> 0L
        }

        var scanned = 0
        var imported = 0
        var duplicates = 0
        var ignored = 0
        val batch = ArrayList<ParsedSms>(BATCH_SIZE)

        suspend fun flush() {
            if (batch.isEmpty()) return
            val result = repository.importSmsMessages(batch.toList())
            imported += result.imported
            duplicates += result.duplicates
            batch.clear()
        }

        val outcome = runCatching {
            val resolver: ContentResolver = appContext.contentResolver
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            val selection = if (since > 0L) "${Telephony.Sms.DATE} > ?" else null
            val selectionArgs = if (since > 0L) arrayOf(since.toString()) else null
            val cursor = resolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} ASC"
            ) ?: return@runCatching SmsScanOutcome(error = "SMS inbox is not available")

            cursor.use { rows ->
                val addressIndex = rows.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = rows.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = rows.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (rows.moveToNext()) {
                    val address = rows.getString(addressIndex)
                    val body = rows.getString(bodyIndex).orEmpty()
                    val date = rows.getLong(dateIndex).takeIf { it > 0L }
                        ?: System.currentTimeMillis()
                    scanned++
                    val parsed = SmsParser.parse(address, body, date)
                    if (parsed == null) {
                        ignored++
                    } else {
                        batch += parsed
                        if (batch.size >= BATCH_SIZE) flush()
                    }
                }
            }
            flush()
            SmsScanOutcome(
                scanned = scanned,
                imported = imported,
                duplicates = duplicates,
                ignored = ignored
            )
        }.getOrElse { throwable ->
            runCatching { flush() }
            SmsScanOutcome(
                scanned = scanned,
                imported = imported,
                duplicates = duplicates,
                ignored = ignored,
                permissionDenied = throwable is SecurityException,
                error = throwable.message ?: throwable.javaClass.simpleName
            )
        }

        if (outcome.error == null || outcome.imported > 0) {
            runCatching {
                settings.setLastSmsScanAt(System.currentTimeMillis())
                settings.addSmsImported(outcome.imported)
            }
        }
        outcome
    }

    /**
     * Used by the live receiver. When READ_SMS is available we prefer the
     * canonical row the provider eventually stored (multipart parts may differ
     * from the in-memory concatenation). Both paths use the same parser and
     * the same timestamp fallback, so their fingerprints stay identical.
     */
    suspend fun importLive(sender: String?, body: String, timestamp: Long): Boolean =
        withContext(Dispatchers.IO) {
            val normalizedTimestamp = timestamp.takeIf { it > 0L } ?: System.currentTimeMillis()
            val canonical = if (canReadSms()) findInboxMatch(sender, body, normalizedTimestamp) else null
            val parsed = if (canonical != null) {
                SmsParser.parse(canonical.address, canonical.body, canonical.date)
            } else {
                SmsParser.parse(sender, body, normalizedTimestamp)
            } ?: return@withContext false

            val result = repository.importSmsMessages(listOf(parsed))
            if (result.imported > 0) {
                runCatching { settings.addSmsImported(result.imported) }
            }
            result.imported > 0
        }

    private data class InboxRow(val address: String?, val body: String, val date: Long)

    /** Looks for the provider row matching a just-delivered SMS, if permitted. */
    private fun findInboxMatch(sender: String?, body: String, timestamp: Long): InboxRow? {
        val expectedAddress = SmsParser.normalizeSenderKey(sender)
        val expectedBody = SmsParser.normalizeBodyKey(body)
        if (expectedBody.isEmpty()) return null

        return runCatching {
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            val selection = "${Telephony.Sms.DATE} BETWEEN ? AND ?"
            val selectionArgs = arrayOf(
                (timestamp - LIVE_INBOX_WINDOW_MILLIS).toString(),
                (timestamp + LIVE_INBOX_WINDOW_MILLIS).toString()
            )
            val cursor = appContext.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} ASC"
            ) ?: return@runCatching null

            cursor.use { rows ->
                val addressIndex = rows.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = rows.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = rows.getColumnIndexOrThrow(Telephony.Sms.DATE)
                var best: InboxRow? = null
                while (rows.moveToNext()) {
                    val address = rows.getString(addressIndex)
                    val rowBody = rows.getString(bodyIndex).orEmpty()
                    val date = rows.getLong(dateIndex).takeIf { it > 0L }
                        ?: System.currentTimeMillis()
                    val rowBodyKey = SmsParser.normalizeBodyKey(rowBody)
                    val addressMatches = expectedAddress.isEmpty() ||
                        SmsParser.normalizeSenderKey(address) == expectedAddress
                    val bodyMatches = rowBodyKey == expectedBody ||
                        rowBodyKey.contains(expectedBody) ||
                        expectedBody.contains(rowBodyKey)
                    if (!addressMatches || !bodyMatches) continue
                    val current = best
                    if (current == null || kotlin.math.abs(date - timestamp) <
                        kotlin.math.abs(current.date - timestamp)
                    ) {
                        best = InboxRow(address, rowBody, date)
                    }
                }
                best
            }
        }.getOrNull()
    }

    fun canReadSms(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val BATCH_SIZE = 200
        const val OVERLAP_MILLIS = 3L * 24L * 60L * 60L * 1000L
        const val LIVE_INBOX_WINDOW_MILLIS = 60_000L
    }
}
