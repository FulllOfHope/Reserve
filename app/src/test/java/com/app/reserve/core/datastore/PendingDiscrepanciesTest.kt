package com.app.reserve.core.datastore

import com.app.reserve.core.domain.LedgerReconciliation.PendingBalanceDiscrepancy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDiscrepanciesTest {

    private fun discrepancy(
        accountId: String,
        smsBalanceMinor: Long,
        timestamp: Long
    ) = PendingBalanceDiscrepancy(
        accountId = accountId,
        accountName = "Bank $accountId",
        reserveBalanceMinor = smsBalanceMinor + 1_000L,
        smsBalanceMinor = smsBalanceMinor,
        differenceMinor = -1_000L,
        timestamp = timestamp
    )

    @Test
    fun `queue round-trips through DataStore JSON`() {
        val queue = listOf(discrepancy("a", 5_000L, 20L), discrepancy("b", 9_000L, 10L))
        assertEquals(queue, queue.encodePendingDiscrepancies().decodePendingDiscrepancies())
    }

    @Test
    fun `corrupt JSON never crashes app entry`() {
        assertTrue("{not json".decodePendingDiscrepancies().isEmpty())
    }

    @Test
    fun `multiple accounts are kept as a queue`() {
        val merged = mergePendingDiscrepancies(
            existing = listOf(discrepancy("a", 5_000L, 10L)),
            incoming = listOf(discrepancy("b", 9_000L, 20L))
        )
        assertEquals(listOf("b", "a"), merged.map { it.accountId })
    }

    @Test
    fun `a newer alert replaces only its own account`() {
        val merged = mergePendingDiscrepancies(
            existing = listOf(discrepancy("a", 5_000L, 10L), discrepancy("b", 9_000L, 20L)),
            incoming = listOf(discrepancy("a", 4_000L, 30L))
        )
        assertEquals(listOf("a", "b"), merged.map { it.accountId })
        assertEquals(4_000L, merged.first { it.accountId == "a" }.smsBalanceMinor)
        assertEquals(9_000L, merged.first { it.accountId == "b" }.smsBalanceMinor)
    }

    @Test
    fun `an older replayed alert cannot replace the newer one`() {
        val merged = mergePendingDiscrepancies(
            existing = listOf(discrepancy("a", 4_000L, 30L)),
            incoming = listOf(discrepancy("a", 5_000L, 10L))
        )
        assertEquals(1, merged.size)
        assertEquals(4_000L, merged.single().smsBalanceMinor)
    }
}
