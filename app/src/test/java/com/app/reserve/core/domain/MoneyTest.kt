package com.app.reserve.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `formats whole rupees without paise`() {
        assertEquals("₹1,200", Money.format(120_000))
        assertEquals("₹0", Money.format(0))
        assertEquals("₹999", Money.format(99_900))
    }

    @Test
    fun `formats paise and always-show mode`() {
        assertEquals("₹1,200.50", Money.format(120_050))
        assertEquals("₹1,200.05", Money.format(120_005))
        assertEquals("₹1,200.00", Money.format(120_000, alwaysShowPaise = true))
    }

    @Test
    fun `formats negatives and plus`() {
        assertEquals("-₹450", Money.format(-45_000))
        assertEquals("+₹1,200", Money.format(120_000, showPlus = true))
        assertEquals("-₹450.25", Money.format(-45_025))
    }

    @Test
    fun `groups large digits in threes`() {
        assertEquals("1,200", Money.groupDigits("1200"))
        assertEquals("12,345,678", Money.groupDigits("12345678"))
        assertEquals("999", Money.groupDigits("999"))
    }

    @Test
    fun `parses plain grouped and symbol amounts exactly`() {
        assertEquals(123_456L, Money.parse("1,234.56"))
        assertEquals(123_450L, Money.parse("₹1,234.5"))
        assertEquals(123_400L, Money.parse("1234"))
        assertEquals(-45_000L, Money.parse("-450"))
        assertEquals(5L, Money.parse("0.05"))
    }

    @Test
    fun `rejects malformed input`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("-"))
        assertNull(Money.parse("."))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1.2.3"))
    }

    @Test
    fun `storage representation is exact minor units`() {
        assertEquals("120050", Money.toStorage(120_050))
        assertEquals("-45000", Money.toStorage(-45_000))
    }
}
