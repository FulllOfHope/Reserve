package com.app.reserve.core.domain

import com.app.reserve.core.database.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartImporterTest {

    @Test
    fun detectsCsvColumnsAndConvertsAmounts() {
        val csv = """
            Date,Description,Amount,Type,Category,Account
            2026-01-15,Lunch,-12.50,EXPENSE,Food,Cash
            2026-01-16,Salary,1000,INCOME,Salary,Bank
        """.trimIndent().toByteArray()

        val analysis = SmartImporter.analyze(csv)
        assertTrue(analysis.error == null)
        assertEquals("Comma-separated", analysis.format)
        assertEquals("Date", analysis.headers[analysis.mapping.date])
        assertEquals("Amount", analysis.headers[analysis.mapping.amount])
        assertEquals("Description", analysis.headers[analysis.mapping.note])

        val rows = SmartImporter.toTransactions(analysis, analysis.mapping)
        assertEquals(2, rows.size)
        assertEquals(TransactionType.EXPENSE, rows[0].type)
        assertEquals(1_250L, rows[0].amountMinor)
        assertEquals("Food", rows[0].categoryName)
        assertEquals("Cash", rows[0].accountName)
        assertEquals(TransactionType.INCOME, rows[1].type)
        assertEquals(100_000L, rows[1].amountMinor)
    }

    @Test
    fun detectsJsonMinorUnitsAndDirection() {
        val json = """
            [
              {"transaction_date":"2026-02-01","amount_minor":1250,"direction":"credit","memo":"Refund"},
              {"transaction_date":"2026-02-02","amount_minor":499,"direction":"debit","memo":"Coffee"}
            ]
        """.trimIndent().toByteArray()

        val analysis = SmartImporter.analyze(json)
        assertTrue(analysis.error == null)
        assertTrue(analysis.mapping.amountIsMinor)

        val rows = SmartImporter.toTransactions(analysis, analysis.mapping)
        assertEquals(2, rows.size)
        assertEquals(TransactionType.INCOME, rows[0].type)
        assertEquals(1_250L, rows[0].amountMinor)
        assertEquals("Refund", rows[0].note)
        assertEquals(TransactionType.EXPENSE, rows[1].type)
        assertEquals(499L, rows[1].amountMinor)
    }

    @Test
    fun rejectsTinyOrCorruptFilesWithoutCrashing() {
        val error = SmartImporter.analyze(byteArrayOf(0x50)).error
        assertNotNull(error)
        assertTrue(error!!.isNotBlank())
    }
}
