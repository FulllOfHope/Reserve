package com.app.reserve.core.datastore

import com.app.reserve.core.domain.LedgerReconciliation.PendingBalanceDiscrepancy
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * DataStore representation of the balance-verification queue. Prompts are
 * device-local: they are persisted so a mismatch found by the background
 * receiver still appears on the next app entry, but they are deliberately not
 * part of backup JSON (restoring an old backup must not resurrect stale prompts).
 */
@Serializable
internal data class PendingDiscrepancyRecord(
    val accountId: String,
    val accountName: String,
    val reserveBalanceMinor: Long,
    val smsBalanceMinor: Long,
    val differenceMinor: Long,
    val timestamp: Long
)

private val recordJson = Json { ignoreUnknownKeys = true }

private fun PendingBalanceDiscrepancy.toRecord() = PendingDiscrepancyRecord(
    accountId = accountId,
    accountName = accountName,
    reserveBalanceMinor = reserveBalanceMinor,
    smsBalanceMinor = smsBalanceMinor,
    differenceMinor = differenceMinor,
    timestamp = timestamp
)

private fun PendingDiscrepancyRecord.toDomain() = PendingBalanceDiscrepancy(
    accountId = accountId,
    accountName = accountName,
    reserveBalanceMinor = reserveBalanceMinor,
    smsBalanceMinor = smsBalanceMinor,
    differenceMinor = differenceMinor,
    timestamp = timestamp
)

fun List<PendingBalanceDiscrepancy>.encodePendingDiscrepancies(): String =
    recordJson.encodeToString(map { it.toRecord() })

fun String.decodePendingDiscrepancies(): List<PendingBalanceDiscrepancy> =
    runCatching {
        recordJson.decodeFromString<List<PendingDiscrepancyRecord>>(this).map { it.toDomain() }
    }.getOrDefault(emptyList())

/**
 * Merges a new scan's findings with the persisted queue. There is one prompt
 * per account and the newest bank balance wins, so multiple accounts are
 * handled as an ordered list instead of overwriting one another.
 */
fun mergePendingDiscrepancies(
    existing: List<PendingBalanceDiscrepancy>,
    incoming: List<PendingBalanceDiscrepancy>
): List<PendingBalanceDiscrepancy> = (existing + incoming)
    .groupBy { it.accountId }
    .values
    .map { group -> group.maxWith(compareBy { it.timestamp }) }
    .sortedByDescending { it.timestamp }
