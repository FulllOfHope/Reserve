package com.app.reserve.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountInputTest {

    @Test
    fun `typing digits builds an exact minor amount`() {
        var input = AmountInput()
        listOf("1", "2", "5", "0").forEach { input = input.append(it) }
        assertEquals("1250", input.text)
        assertEquals(125_000L, input.minor)
        assertEquals("₹1,250", input.display)
    }

    @Test
    fun `decimal point is allowed once and keeps typed shape`() {
        var input = AmountInput()
        listOf("1", "2", ".", "5").forEach { input = input.append(it) }
        assertEquals("12.5", input.text)
        assertEquals(1_250L, input.minor)
        assertEquals("₹12.5", input.display)

        input = input.append(".")
        assertEquals("12.5", input.text)
    }

    @Test
    fun `paise are capped at two digits`() {
        var input = AmountInput()
        listOf("9", ".", "9", "9", "9").forEach { input = input.append(it) }
        assertEquals("9.99", input.text)
        assertEquals(999L, input.minor)
    }

    @Test
    fun `leading zero is replaced by the next digit`() {
        var input = AmountInput()
        listOf("0", "7").forEach { input = input.append(it) }
        assertEquals("7", input.text)
    }

    @Test
    fun `a lone decimal starts from zero`() {
        val input = AmountInput().append(".")
        assertEquals("0.", input.text)
        assertEquals("₹0.", input.display)
        assertEquals(0L, input.minor)
        assertTrue(input.isEmpty)
    }

    @Test
    fun `backspace removes the last character`() {
        var input = AmountInput()
        listOf("1", "2", "3").forEach { input = input.append(it) }
        input = input.append("back")
        assertEquals("12", input.text)
        assertFalse(input.isEmpty)
    }

    @Test
    fun `whole part is limited to nine digits`() {
        var input = AmountInput()
        repeat(12) { input = input.append("9") }
        assertEquals(9, input.text.length)
    }
}
