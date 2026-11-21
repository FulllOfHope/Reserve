package com.app.reserve.core.domain

import com.app.reserve.core.data.LedgerSnapshot
import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.database.BudgetEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.RecurringEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.datastore.ReserveSettings
import com.app.reserve.core.datastore.ThemeMode
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
            .replace("\"version\": ${BackupManager.CURRENT_VERSION}", "\"version\": 99")
        val result = BackupManager.decode(text)
        assertTrue(result.isFailure)
    }

    @Test
    fun `trash tombstones survive a backup round-trip`() {
        val withTrash = snapshot.copy(
            transactions = snapshot.transactions + snapshot.transactions.first().copy(
                id = "t2", deletedAt = 9_999L
            )
        )
        val decoded = BackupManager.decode(BackupManager.encode(withTrash)).getOrThrow()
        assertEquals(null, decoded.transactions.first { it.id == "t1" }.deletedAt)
        assertEquals(9_999L, decoded.transactions.first { it.id == "t2" }.deletedAt)
    }

    @Test
    fun `legacy v1 backup without deletedAt still decodes`() {
        val legacy = """
            {"version": 1, "exportedAt": 0,
             "accounts": [{"id":"cash","name":"Cash","balance":100,"createdAt":0}],
             "categories": [{"id":"food","name":"Food","colorHex":"#000000","iconName":"food","type":"EXPENSE"}],
             "transactions": [{"id":"t1","amount":50,"type":"EXPENSE","accountId":"cash","timestamp":1,"createdAt":0}],
             "budgets": [], "recurring": []}
        """.trimIndent()
        val decoded = BackupManager.decode(legacy).getOrThrow()
        assertEquals(null, decoded.transactions.first().deletedAt)
        assertEquals(50L, decoded.transactions.first().amount)
    }

    @Test
    fun `settings and account opening balance round-trip in v3 backups`() {
        val withOpening = snapshot.copy(
            accounts = snapshot.accounts.map { it.copy(openingBalanceMinor = 12_345L) }
        )
        val settings = ReserveSettings(
            hideAmounts = true,
            themeMode = ThemeMode.DARK,
            primaryColorHex = "#7C3AED",
            balanceAccountIds = setOf("cash"),
            lastBackupAt = 4_000L
        )
        val bundle = BackupManager.decodeBundle(
            BackupManager.encode(withOpening, settings, now = 4_000L)
        ).getOrThrow()

        assertEquals(12_345L, bundle.snapshot.accounts.first().openingBalanceMinor)
        assertEquals(true, bundle.settings?.hideAmounts)
        assertEquals(ThemeMode.DARK.name, bundle.settings?.themeMode)
        assertEquals("#7C3AED", bundle.settings?.primaryColorHex)
        assertEquals(setOf("cash"), bundle.settings?.balanceAccountIds)
        assertEquals(4_000L, bundle.exportedAt)
    }

    @Test
    fun `legacy v2 backup without settings still decodes`() {
        val legacy = BackupManager.encode(snapshot, now = 2_000L)
            .replace("\"version\": ${BackupManager.CURRENT_VERSION}", "\"version\": 2")
            .replace(Regex("\"settings\": \\{[^}]*\\},?\\s*"), "")
        val bundle = BackupManager.decodeBundle(legacy).getOrThrow()

        assertEquals(2_000L, bundle.exportedAt)
        assertEquals(null, bundle.settings)
        assertEquals(1, bundle.snapshot.transactions.size)
    }

    @Test
    fun `garbage is rejected without throwing`() {
        assertTrue(BackupManager.decode("not json").isFailure)
    }
}
