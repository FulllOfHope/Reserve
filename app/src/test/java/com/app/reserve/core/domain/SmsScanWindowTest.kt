package com.app.reserve.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class SmsScanWindowTest {

    private val zone = ZoneOffset.UTC
    private val now = 1_700_000_000_000L // 2023-11-14T22:13:20Z in UTC

    @Test
    fun `default start is the first instant of the current local month`() {
        val expected = 1_698_796_800_000L // 2023-11-01T00:00:00Z
        assertEquals(expected, SmsScanWindow.defaultMonthStart(now, zone))
    }

    @Test
    fun `configured start month is a hard floor for a forced full scan`() {
        val chosenStart = 1_698_000_000_000L // October, earlier than the current month
        assertEquals(
            chosenStart,
            SmsScanWindow.resolveSince(
                now = now,
                scanStartEpochMs = chosenStart,
                lastScanAt = 0L,
                overlapMillis = THREE_DAYS,
                forceFull = true
            )
        )
    }

    @Test
    fun `a future configured start never widens the window`() {
        val monthStart = SmsScanWindow.defaultMonthStart(now, zone)
        val futureStart = now + THREE_DAYS
        assertEquals(
            futureStart,
            SmsScanWindow.resolveSince(
                now = now,
                scanStartEpochMs = futureStart,
                lastScanAt = now,
                overlapMillis = THREE_DAYS,
                forceFull = true
            )
        )
        assertEquals(monthStart, SmsScanWindow.defaultMonthStart(now, zone))
    }

    @Test
    fun `incremental scans use overlap but never cross the start floor`() {
        val oldFloor = 1_698_000_000_000L
        assertEquals(
            now - THREE_DAYS,
            SmsScanWindow.resolveSince(
                now = now,
                scanStartEpochMs = oldFloor,
                lastScanAt = now,
                overlapMillis = THREE_DAYS,
                forceFull = false,
                zone = zone
            )
        )
        val recentFloor = 1_699_999_900_000L
        assertEquals(
            recentFloor,
            SmsScanWindow.resolveSince(
                now = now,
                scanStartEpochMs = recentFloor,
                lastScanAt = recentFloor + 1L,
                overlapMillis = THREE_DAYS,
                forceFull = false,
                zone = zone
            )
        )
    }

    @Test
    fun `unset start falls back to the current month`() {
        assertEquals(
            SmsScanWindow.defaultMonthStart(now, zone),
            SmsScanWindow.resolveSince(
                now = now,
                scanStartEpochMs = 0L,
                lastScanAt = now,
                overlapMillis = THREE_DAYS,
                forceFull = true,
                zone = zone
            )
        )
    }

    private companion object {
        const val THREE_DAYS = 3L * 24L * 60L * 60L * 1000L
    }
}
