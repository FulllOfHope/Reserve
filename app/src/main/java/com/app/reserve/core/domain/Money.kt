package com.app.reserve.core.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * All money in Reserve is stored as [Long] minor units (paise). These helpers
 * are the only place where minor units become text, and they never introduce
 * floating point rounding.
 */
object Money {

    /** Digits grouped in threes, western style: 1200 -> "1,200". */
    fun groupDigits(digits: String): String {
        if (digits.length <= 3) return digits
        val builder = StringBuilder()
        val firstGroup = digits.length % 3
        if (firstGroup > 0) {
            builder.append(digits, 0, firstGroup)
        }
        var index = firstGroup
        while (index < digits.length) {
            if (builder.isNotEmpty()) builder.append(',')
            builder.append(digits, index, index + 3)
            index += 3
        }
        return builder.toString()
    }

    /**
     * 120000 -> "₹1,200"; 120050 -> "₹1,200.50"; -45000 -> "-₹450".
     * Paise are hidden when they are zero unless [alwaysShowPaise] is set.
     */
    fun format(minor: Long, alwaysShowPaise: Boolean = false, showPlus: Boolean = false): String {
        val negative = minor < 0
        val absolute = if (negative) -minor else minor
        val rupees = absolute / 100
        val paise = (absolute % 100).toInt()
        val builder = StringBuilder()
        if (negative) builder.append('-') else if (showPlus && minor > 0) builder.append('+')
        builder.append('₹').append(groupDigits(rupees.toString()))
        if (alwaysShowPaise || paise != 0) {
            builder.append('.').append(paise.toString().padStart(2, '0'))
        }
        return builder.toString()
    }

    /** Parses "1,234.5", "₹1,234.56" or "1234" into minor units. */
    fun parse(input: String): Long? {
        val cleaned = input.trim()
            .replace("₹", "")
            .replace(",", "")
            .replace(" ", "")
        if (cleaned.isEmpty() || cleaned == "." || cleaned == "-") return null
        val negative = cleaned.startsWith("-")
        val unsigned = if (negative) cleaned.drop(1) else cleaned
        if (unsigned.isEmpty()) return null
        return runCatching {
            val decimal = BigDecimal(unsigned)
            val minor = decimal.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
            if (negative) -minor else minor
        }.getOrNull()
    }

    /** CSV/backup representation: exact minor units, no symbols. */
    fun toStorage(minor: Long): String = minor.toString()
}
