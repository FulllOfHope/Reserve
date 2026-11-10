package com.app.reserve.core.domain

/**
 * Transient keypad state for the add screen. It keeps exactly the digits the
 * user typed, so "12.5" stays visible as typed instead of being normalised to
 * "12.50", while [minor] remains an exact Long.
 */
data class AmountInput(val text: String = "") {

    val minor: Long
        get() = Money.parse(text) ?: 0L

    val isEmpty: Boolean get() = text.isBlank() || minor == 0L

    val display: String
        get() {
            if (text.isEmpty()) return "₹0"
            val parts = text.split('.')
            val whole = Money.groupDigits(parts[0])
            return if (parts.size > 1) "₹$whole.${parts[1]}" else "₹$whole"
        }

    fun append(key: String): AmountInput = when (key) {
        "back" -> backspace()
        "." -> appendDecimal()
        else -> appendDigit(key)
    }

    private fun appendDigit(digit: String): AmountInput {
        if (digit.length != 1 || !digit[0].isDigit()) return this
        val whole = text.substringBefore('.')
        val decimal = text.substringAfter('.', "")
        return if (text.contains('.')) {
            if (decimal.length >= 2) this else copy(text = text + digit)
        } else {
            if (whole.length >= 9) this else {
                val cleaned = if (whole == "0") digit else whole + digit
                copy(text = cleaned)
            }
        }
    }

    private fun appendDecimal(): AmountInput {
        if (text.contains('.')) return this
        return copy(text = if (text.isEmpty()) "0." else "$text.")
    }

    private fun backspace(): AmountInput = copy(text = text.dropLast(1))
}
