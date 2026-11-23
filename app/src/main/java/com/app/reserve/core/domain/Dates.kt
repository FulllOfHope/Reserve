package com.app.reserve.core.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val monthKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.US)
    // Keys stay ASCII-stable; anything shown to the user follows device locale.
    private val monthLabelFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val dayHeaderFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val fullFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    fun monthKey(date: LocalDate): String = date.format(monthKeyFormatter)

    fun currentMonthKey(): String = monthKey(LocalDate.now())

    fun today(): LocalDate = LocalDate.now()

    fun monthLabel(monthYear: String): String =
        runCatching { LocalDate.parse("$monthYear-01").format(monthLabelFormatter) }
            .getOrDefault(monthYear)

    fun monthDate(monthYear: String): LocalDate =
        runCatching { LocalDate.parse("$monthYear-01") }.getOrDefault(LocalDate.now().withDayOfMonth(1))

    fun shiftMonth(monthYear: String, delta: Long): String {
        val base = monthDate(monthYear)
        return monthKey(if (delta >= 0) base.plusMonths(delta) else base.minusMonths(-delta))
    }

    fun isCurrentMonth(monthYear: String): Boolean = monthYear == currentMonthKey()

    fun toLocalDate(timestamp: Long): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    fun toTimestamp(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, 0))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    fun fromMonthAndDay(monthYear: String, dayOfMonth: Int): LocalDate {
        val first = monthDate(monthYear)
        val lastDay = first.lengthOfMonth()
        return first.withDayOfMonth(dayOfMonth.coerceIn(1, lastDay))
    }

    fun dayHeader(timestamp: Long): String {
        val date = toLocalDate(timestamp)
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(dayHeaderFormatter)
        }
    }

    fun fullDate(timestamp: Long): String = toLocalDate(timestamp).format(dayHeaderFormatter)

    fun fullDateLong(timestamp: Long): String = toLocalDate(timestamp).format(fullFormatter)
}
