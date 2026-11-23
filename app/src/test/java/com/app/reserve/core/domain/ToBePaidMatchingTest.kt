package com.app.reserve.core.domain

import com.app.reserve.core.database.ToBePaidEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToBePaidMatchingTest {

    private val dueDate = LocalDate.of(2026, 11, 20)
    private val dueAt = Dates.toTimestamp(dueDate)

    private fun reminder(
        amount: Long = 45_000L,
        type: TransactionType = TransactionType.EXPENSE,
        accountId: String = "bank",
        settled: Boolean = false
    ) = ToBePaidEntity(
        id = "plan-1",
        title = "Electricity bill",
        amount = amount,
        type = type,
        categoryId = "bills",
        accountId = accountId,
        dueAt = dueAt,
        settledTransactionId = if (settled) "already-posted" else null
    )

    private fun smsRow(
        amount: Long = 45_000L,
        type: TransactionType = TransactionType.EXPENSE,
        accountId: String = "bank",
        day: Int = 21,
        deleted: Boolean = false
    ) = TransactionEntity(
        id = "sms-1",
        amount = amount,
        type = type,
        categoryId = "bills",
        accountId = accountId,
        timestamp = Dates.toTimestamp(LocalDate.of(2026, 11, day)),
        note = "Electricity bill",
        deletedAt = if (deleted) 9_999L else null
    )

    @Test
    fun `exact amount matching direction and nearby date is a candidate`() {
        assertTrue(ToBePaidMatching.isCandidate(reminder(), smsRow()))
    }

    @Test
    fun `settled reminders never prompt again`() {
        assertFalse(ToBePaidMatching.isCandidate(reminder(settled = true), smsRow()))
    }

    @Test
    fun `wrong amount direction account or trashed row is rejected`() {
        assertFalse(ToBePaidMatching.isCandidate(reminder(amount = 45_001L), smsRow()))
        assertFalse(ToBePaidMatching.isCandidate(reminder(type = TransactionType.INCOME), smsRow()))
        assertFalse(ToBePaidMatching.isCandidate(reminder(accountId = "cash"), smsRow()))
        assertFalse(ToBePaidMatching.isCandidate(reminder(), smsRow(deleted = true)))
        assertFalse(ToBePaidMatching.isCandidate(reminder(type = TransactionType.TRANSFER), smsRow(type = TransactionType.TRANSFER)))
    }

    @Test
    fun `a message outside the conservative window is rejected`() {
        assertFalse(ToBePaidMatching.isCandidate(reminder(), smsRow(day = 28)))
    }
}
