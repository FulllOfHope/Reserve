package com.app.reserve.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyTest {

    @Test
    fun hiddenAmountReplacesAnyValue() {
        assertEquals(HiddenAmount, formatMoney(123_45, hidden = true))
        assertEquals(HiddenAmount, formatMoney(-123_45, hidden = true))
        assertEquals(HiddenAmount, formatMoney(123_45, hidden = true, showPlus = true))
    }

    @Test
    fun visibleAmountUsesNormalMoneyFormatting() {
        assertEquals("₹123.45", formatMoney(123_45, hidden = false))
        assertEquals("+₹123.45", formatMoney(123_45, hidden = false, showPlus = true))
        assertEquals("-₹123.45", formatMoney(-123_45, hidden = false))
    }
}
