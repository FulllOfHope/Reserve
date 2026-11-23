package com.app.reserve.core.datastore

import com.app.reserve.core.domain.PendingToBePaidMatch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * DataStore form of the "was this SMS your planned payment?" queue. Like the
 * balance-discrepancy queue it is device-local and excluded from backup JSON.
 */
@Serializable
internal data class PendingToBePaidMatchRecord(
    val reminderId: String,
    val reminderTitle: String,
    val transactionId: String,
    val transactionNote: String,
    val amountMinor: Long,
    val transactionTimestamp: Long,
    val detectedAt: Long
)

private val recordJson = Json { ignoreUnknownKeys = true }

private fun PendingToBePaidMatch.toRecord() = PendingToBePaidMatchRecord(
    reminderId = reminderId,
    reminderTitle = reminderTitle,
    transactionId = transactionId,
    transactionNote = transactionNote,
    amountMinor = amountMinor,
    transactionTimestamp = transactionTimestamp,
    detectedAt = detectedAt
)

private fun PendingToBePaidMatchRecord.toDomain() = PendingToBePaidMatch(
    reminderId = reminderId,
    reminderTitle = reminderTitle,
    transactionId = transactionId,
    transactionNote = transactionNote,
    amountMinor = amountMinor,
    transactionTimestamp = transactionTimestamp,
    detectedAt = detectedAt
)

fun List<PendingToBePaidMatch>.encodePendingToBePaidMatches(): String =
    recordJson.encodeToString(map { it.toRecord() })

fun String.decodePendingToBePaidMatches(): List<PendingToBePaidMatch> =
    runCatching {
        recordJson.decodeFromString<List<PendingToBePaidMatchRecord>>(this).map { it.toDomain() }
    }.getOrDefault(emptyList())

/** One prompt per reminder; the newest matching SMS wins. */
fun mergePendingToBePaidMatches(
    existing: List<PendingToBePaidMatch>,
    incoming: List<PendingToBePaidMatch>
): List<PendingToBePaidMatch> = (existing + incoming)
    .groupBy { it.reminderId }
    .values
    .map { group -> group.maxWith(compareBy { it.detectedAt }) }
    .sortedByDescending { it.detectedAt }
