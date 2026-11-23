package com.app.reserve.core.datastore

import com.app.reserve.core.domain.PendingToBePaidMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingToBePaidMatchesTest {

    private fun match(
        reminderId: String,
        transactionId: String,
        detectedAt: Long,
        amountMinor: Long = 45_000L
    ) = PendingToBePaidMatch(
        reminderId = reminderId,
        reminderTitle = "Electricity bill",
        transactionId = transactionId,
        transactionNote = "Bank debit",
        amountMinor = amountMinor,
        transactionTimestamp = detectedAt - 1_000L,
        detectedAt = detectedAt
    )

    @Test
    fun `queue round-trips through DataStore JSON`() {
        val queue = listOf(match("a", "t1", 20L), match("b", "t2", 10L))
        assertEquals(queue, queue.encodePendingToBePaidMatches().decodePendingToBePaidMatches())
    }

    @Test
    fun `corrupt JSON never crashes app entry`() {
        assertTrue("{not json".decodePendingToBePaidMatches().isEmpty())
    }

    @Test
    fun `one prompt per reminder keeps the newest SMS first`() {
        val merged = mergePendingToBePaidMatches(
            existing = listOf(match("a", "old", 10L), match("b", "t2", 20L)),
            incoming = listOf(match("a", "new", 30L), match("c", "t3", 5L))
        )
        assertEquals(listOf("a", "b", "c"), merged.map { it.reminderId })
        assertEquals("new", merged.first { it.reminderId == "a" }.transactionId)
    }

    @Test
    fun `an older replayed match cannot replace the newer one`() {
        val merged = mergePendingToBePaidMatches(
            existing = listOf(match("a", "new", 30L)),
            incoming = listOf(match("a", "old", 10L))
        )
        assertEquals(1, merged.size)
        assertEquals("new", merged.single().transactionId)
    }
}
