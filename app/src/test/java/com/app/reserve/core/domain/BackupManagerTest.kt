package com.app.reserve.core.domain

import com.app.reserve.core.data.LedgerSnapshot
import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.database.BudgetEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.RecurringEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    private val snapshot = LedgerSnapshot(
        accounts = listOf(AccountEntity("cash", "Cash", 123_456, createdAt = 1L)),
        categories = listOf(
            CategoryEntity("food", "Food", "#5C947F", "food", TransactionType.EXPENSE)
        ),
        transactions = listOf(
            TransactionEntity(
                id = "t1",
                amount = 45_050,
                type = TransactionType.EXPENSE,
                categoryId = "food",
                accountId = "cash",
                timestamp = Dates.toTimestamp(java.time.LocalDate.of(2026, 11, 3)),
                note = "Lunch"
            )
        ),
        budgets = listOf(BudgetEntity("b1", "food", 100_000, "2026-11")),
        recurring = listOf(
            RecurringEntity(
                id = "r1",
                amount = 50_000,
                type = TransactionType.EXPENSE,
                categoryId = "food",
                accountId = "cash",
                note = "Rent",
                dayOfMonth = 1,
                nextDueAt = Dates.toTimestamp(java.time.LocalDate.of(2026, 12, 1))
            )
        )
    )

    @Test
    fun `backup round-trips every entity and keeps money exact`() {
        val text = BackupManager.encode(snapshot, now = 1_000L)
        val decoded = BackupManager.decode(text).getOrThrow()

        assertEquals(1, decoded.accounts.size)
        assertEquals(123_456L, decoded.accounts.first().balance)
        assertEquals(45_050L, decoded.transactions.first().amount)
        assertEquals(TransactionType.EXPENSE, decoded.transactions.first().type)
        assertEquals(100_000L, decoded.budgets.first().amount)
        assertEquals("2026-11", decoded.budgets.first().monthYear)
        assertEquals(50_000L, decoded.recurring.first().amount)
        assertEquals(1, decoded.recurring.first().dayOfMonth)
    }

    @Test
    fun `newer backup versions are rejected`() {
        val text = BackupManager.encode(snapshot)
            .replace("\"version\": 1", "\"version\": 99")
        val result = BackupManager.decode(text)
        assertTrue(result.isFailure)
    }

    @Test
    fun `garbage is rejected without throwing`() {
        assertTrue(BackupManager.decode("not json").isFailure)
    }
}
