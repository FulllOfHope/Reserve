package com.app.reserve.core.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure helpers for the bounded SMS inbox query. Kept out of the Android-bound
 * importer so the start-month cutoff can be verified in JVM unit tests.
 */
object SmsScanWindow {

    /** First instant of the current local month. */
    fun defaultMonthStart(
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        .withDayOfMonth(1)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    /**
     * Resolves the oldest timestamp the inbox query may use. The configured
     * scan-start month is a hard floor, even when the caller forces a full scan.
     */
    fun resolveSince(
        now: Long,
        scanStartEpochMs: Long?,
        lastScanAt: Long,
        overlapMillis: Long,
        forceFull: Boolean,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val floor = scanStartEpochMs?.takeIf { it > 0L } ?: defaultMonthStart(now, zone)
        val incremental = if (forceFull || lastScanAt <= 0L) {
            0L
        } else {
            (lastScanAt - overlapMillis).coerceAtLeast(0L)
        }
        return maxOf(floor, incremental)
    }
}
