package com.app.reserve.core.domain

import com.app.reserve.core.database.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerMathTest {

    @Test
    fun `expense lowers one balance`() {
        val deltas = LedgerMath.deltas(TransactionType.EXPENSE, 25_000, "cash", null)
        assertEquals(listOf(AccountDelta("cash", -25_000)), deltas)
    }

    @Test
    fun `income raises one balance`() {
        val deltas = LedgerMath.deltas(TransactionType.INCOME, 200_000, "bank", null)
        assertEquals(listOf(AccountDelta("bank", 200_000)), deltas)
    }

    @Test
    fun `transfer between accounts nets to zero`() {
        val deltas = LedgerMath.deltas(TransactionType.TRANSFER, 75_000, "cash", "bank")
        assertEquals(0L, deltas.sumOf { it.delta })
        assertEquals(-75_000L, deltas.first { it.accountId == "cash" }.delta)
        assertEquals(75_000L, deltas.first { it.accountId == "bank" }.delta)
    }

    @Test
    fun `transfer total balance is unchanged`() {
        val before = mapOf("cash" to 100_000L, "bank" to 250_000L)
        val after = LedgerMath.apply(before, TransactionType.TRANSFER, 75_000, "cash", "bank")
        assertEquals(before.values.sum(), after.values.sum())
        assertEquals(25_000L, after["cash"])
        assertEquals(325_000L, after["bank"])
    }

    @Test
    fun `self transfer is a no-op rather than a loss`() {
        val deltas = LedgerMath.deltas(TransactionType.TRANSFER, 50_000, "cash", "cash")
        assertTrue(deltas.isEmpty())

        val before = mapOf("cash" to 100_000L)
        val after = LedgerMath.apply(before, TransactionType.TRANSFER, 50_000, "cash", "cash")
        assertEquals(before, after)
    }

    @Test
    fun `transfer without a target does nothing`() {
        assertTrue(LedgerMath.deltas(TransactionType.TRANSFER, 50_000, "cash", null).isEmpty())
    }

    @Test
    fun `balances stay exact minor units`() {
        val after = LedgerMath.apply(
            mapOf("cash" to 1L),
            TransactionType.INCOME,
            1,
            "cash",
            null
        )
        assertEquals(2L, after["cash"])
    }
}
