package com.app.reserve.core.domain

import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvManagerTest {

    private val categories = listOf(
        CategoryEntity("food", "Food", "#5C947F", "food", TransactionType.EXPENSE),
        CategoryEntity("salary", "Salary", "#5C947F", "salary", TransactionType.INCOME)
    )

    private val accounts = listOf(
        AccountEntity("cash", "Cash", 0, createdAt = 1L),
        AccountEntity("bank", "Bank", 0, createdAt = 2L)
    )

    private val transactions = listOf(
        TransactionEntity(
            id = "t1",
            amount = 45_050,
            type = TransactionType.EXPENSE,
            categoryId = "food",
            accountId = "cash",
            timestamp = Dates.toTimestamp(LocalDate.of(2026, 11, 3)),
            note = "Lunch, with \"friends\""
        ),
        TransactionEntity(
            id = "t2",
            amount = 200_000,
            type = TransactionType.INCOME,
            categoryId = "salary",
            accountId = "bank",
            timestamp = Dates.toTimestamp(LocalDate.of(2026, 11, 1)),
            note = "November salary"
        ),
        TransactionEntity(
            id = "t3",
            amount = 25_000,
            type = TransactionType.TRANSFER,
            categoryId = null,
            accountId = "bank",
            targetAccountId = "cash",
            timestamp = Dates.toTimestamp(LocalDate.of(2026, 11, 4)),
            note = ""
        )
    )

    @Test
    fun `export then parse round-trips every field`() {
        val csv = CsvManager.export(transactions, categories, accounts)
        val parsed = CsvManager.parse(csv)

        assertNull(parsed.error)
        assertEquals(0, parsed.skipped)
        assertEquals(3, parsed.rows.size)

        val byAmount = parsed.rows.associateBy { it.amountMinor }
        val lunch = byAmount[45_050]!!
        assertEquals(TransactionType.EXPENSE, lunch.type)
        assertEquals("Food", lunch.categoryName)
        assertEquals("Cash", lunch.accountName)
        assertEquals("Lunch, with \"friends\"", lunch.note)
        assertEquals(LocalDate.of(2026, 11, 3), Dates.toLocalDate(lunch.timestamp))

        val salary = byAmount[200_000]!!
        assertEquals(TransactionType.INCOME, salary.type)
        assertEquals("Salary", salary.categoryName)
        assertEquals("Bank", salary.accountName)
        assertEquals(LocalDate.of(2026, 11, 1), Dates.toLocalDate(salary.timestamp))

        val transfer = byAmount[25_000]!!
        assertEquals(TransactionType.TRANSFER, transfer.type)
        assertEquals("", transfer.categoryName)
        assertEquals("Cash", transfer.targetAccountName)
        assertEquals("", transfer.note)
    }

    @Test
    fun `header is stable and amounts are minor units`() {
        val csv = CsvManager.export(transactions, categories, accounts)
        assertTrue(csv.startsWith(CsvManager.HEADER))
        assertTrue(csv.contains(",45", ignoreCase = false) || csv.contains("45050"))
        assertTrue(csv.contains("45050"))
    }

    @Test
    fun `malformed rows are skipped and counted`() {
        val csv = """
            date,type,amount_minor,category,account,target_account,note
            2026-11-01,EXPENSE,1200,Food,Cash,,Good
            nope,EXPENSE,1200,Food,Cash,,Bad date
            2026-11-02,EXPENSE,not-a-number,Food,Cash,,Bad amount
            2026-11-03,WRONG,1200,Food,Cash,,Bad type
            2026-11-04,EXPENSE,0,Food,Cash,,Zero
        """.trimIndent()
        val parsed = CsvManager.parse(csv)
        assertEquals(1, parsed.rows.size)
        assertEquals(4, parsed.skipped)
        assertNull(parsed.error)
    }

    @Test
    fun `wrong header returns a readable error`() {
        val parsed = CsvManager.parse("foo,bar\n1,2")
        assertEquals(0, parsed.rows.size)
        assertTrue(parsed.error != null)
    }

    @Test
    fun `empty file is an error not a crash`() {
        assertEquals(0, CsvManager.parse("").rows.size)
        assertTrue(CsvManager.parse("").error != null)
    }
}
