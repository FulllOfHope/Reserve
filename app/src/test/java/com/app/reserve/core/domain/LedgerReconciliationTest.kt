package com.app.reserve.core.domain

import com.app.reserve.core.database.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerReconciliationTest {

    private fun key(
        amount: Long,
        timestamp: Long,
        accountId: String = "canara",
        note: String = "SMS · Canara Bank ••0105",
        type: TransactionType = TransactionType.EXPENSE
    ) = LedgerReconciliation.DuplicateKey(accountId, amount, type, note, timestamp)

    @Test
    fun `opening balance makes a new account land on the printed balance`() {
        val opening = LedgerReconciliation.openingForBalance(
            balanceAfterMinor = 58_218L,
            type = TransactionType.EXPENSE,
            amount = 12_369L,
            accountId = "canara"
        )
        assertEquals(70_587L, opening)

        val after = opening + LedgerReconciliation.deltaFor(
            TransactionType.EXPENSE, 12_369L, "canara"
        )
        assertEquals(58_218L, after)
    }

    @Test
    fun `income opening balance subtracts the credit`() {
        val opening = LedgerReconciliation.openingForBalance(
            balanceAfterMinor = 100_000L,
            type = TransactionType.INCOME,
            amount = 25_000L,
            accountId = "canara"
        )
        assertEquals(75_000L, opening)
        assertEquals(100_000L, opening + LedgerReconciliation.deltaFor(
            TransactionType.INCOME, 25_000L, "canara"
        ))
    }

    @Test
    fun `missed expense reconciles with an expense adjustment`() {
        val adjustment = LedgerReconciliation.adjustmentFor(
            currentBalance = 70_587L,
            balanceAfterMinor = 58_218L
        )!!
        assertEquals(TransactionType.EXPENSE, adjustment.type)
        assertEquals(12_369L, adjustment.amountMinor)
        assertEquals(
            58_218L,
            70_587L + LedgerReconciliation.deltaFor(
                adjustment.type, adjustment.amountMinor, "canara"
            )
        )
    }

    @Test
    fun `missed credit reconciles with an income adjustment`() {
        val adjustment = LedgerReconciliation.adjustmentFor(40_000L, 50_000L)!!
        assertEquals(TransactionType.INCOME, adjustment.type)
        assertEquals(10_000L, adjustment.amountMinor)
    }

    @Test
    fun `matching balance needs no adjustment`() {
        assertNull(LedgerReconciliation.adjustmentFor(58_218L, 58_218L))
        assertNull(LedgerReconciliation.adjustmentFor(58_218L, null))
    }

    @Test
    fun `duplicate guard uses account amount type note and a minute window`() {
        val original = key(amount = 12_369L, timestamp = 1_000_000L)
        assertTrue(LedgerReconciliation.isDuplicate(original, key(12_369L, 1_000_030L)))
        assertFalse(LedgerReconciliation.isDuplicate(original, key(12_369L, 1_061_000L)))
        assertFalse(LedgerReconciliation.isDuplicate(original, key(12_370L, 1_000_030L)))
        assertFalse(LedgerReconciliation.isDuplicate(
            original,
            key(12_369L, 1_000_030L, accountId = "other")
        ))
        assertFalse(LedgerReconciliation.isDuplicate(
            original,
            key(12_369L, 1_000_030L, type = TransactionType.INCOME)
        ))
        assertFalse(LedgerReconciliation.isDuplicate(
            original,
            key(12_369L, 1_000_030L, note = "SMS · Other")
        ))
    }

    @Test
    fun `exact sms duplicate guard ignores manual rows`() {
        assertTrue(LedgerReconciliation.isExactSmsDuplicate(
            key(100L, 1_000L), key(100L, 1_050L)
        ))
        assertFalse(LedgerReconciliation.isExactSmsDuplicate(
            key(100L, 1_000L, note = "Lunch"), key(100L, 1_050L, note = "Lunch")
        ))
    }

    @Test
    fun `full scan anchors opening against the newest bank balance`() {
        // Three imported movements, not just the first one, define the anchor.
        val deltas = listOf(
            LedgerReconciliation.deltaFor(TransactionType.EXPENSE, 12_369L, "canara"),
            LedgerReconciliation.deltaFor(TransactionType.EXPENSE, 25_000L, "canara"),
            LedgerReconciliation.deltaFor(TransactionType.INCOME, 5_000L, "canara")
        ).sum()

        val opening = LedgerReconciliation.anchoredOpening(58_218L, deltas)
        assertEquals(opening + deltas, 58_218L)
    }

    @Test
    fun `small balance differences are ignored but real ones raise verification`() {
        assertNull(
            LedgerReconciliation.discrepancyFor(
                "canara", "Canara Bank", 58_218L, 58_218L - 100L, 1_000L
            )
        )
        val discrepancy = LedgerReconciliation.discrepancyFor(
            "canara", "Canara Bank", 58_218L, 58_218L - 12_369L, 1_000L
        )!!
        assertEquals(-12_369L, discrepancy.differenceMinor)
        assertEquals(58_218L, discrepancy.reserveBalanceMinor)
        assertEquals(45_849L, discrepancy.smsBalanceMinor)
    }

    @Test
    fun `ledger invariant opening plus deltas equals balance`() {
        val transactions = listOf(
            Triple(TransactionType.EXPENSE, 12_369L, "canara"),
            Triple(TransactionType.INCOME, 5_000L, "canara"),
            Triple(TransactionType.EXPENSE, 2_000L, "canara")
        )
        val deltas = transactions.sumOf { (type, amount, accountId) ->
            LedgerReconciliation.deltaFor(type, amount, accountId)
        }
        val opening = 70_587L
        assertEquals(opening + deltas, 61_218L)
        // Repair derives the same opening from the stored balance and deltas.
        assertEquals(61_218L - deltas, opening)
    }
}
