package com.app.reserve.core.domain

import com.app.reserve.core.data.ImportedTransaction
import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Flat, human-readable CSV. Amounts are written in minor units so an import is
 * exact; category and account names are written so the file survives a fresh
 * install where ids no longer exist.
 */
object CsvManager {

    const val HEADER = "date,type,amount_minor,category,account,target_account,note"

    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE

    fun export(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        accounts: List<AccountEntity>
    ): String {
        val categoryNames = categories.associate { it.id to it.name }
        val accountNames = accounts.associate { it.id to it.name }
        val builder = StringBuilder()
        builder.append(HEADER).append('\n')
        transactions.sortedBy { it.timestamp }.forEach { transaction ->
            val cells = listOf(
                Dates.toLocalDate(transaction.timestamp).format(isoDate),
                transaction.type.name,
                transaction.amount.toString(),
                transaction.categoryId?.let { categoryNames[it] } ?: "",
                accountNames[transaction.accountId] ?: "",
                transaction.targetAccountId?.let { accountNames[it] } ?: "",
                transaction.note
            )
            builder.append(cells.joinToString(",") { escape(it) }).append('\n')
        }
        return builder.toString()
    }

    fun parse(text: String): CsvParseResult {
        val lines = text.removePrefix("\uFEFF")
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return CsvParseResult(emptyList(), 0, "That file is empty.")

        val header = splitLine(lines.first()).map { it.trim().lowercase() }
        val dateIndex = header.indexOf("date")
        val typeIndex = header.indexOf("type")
        val amountIndex = header.indexOf("amount_minor")
        val categoryIndex = header.indexOf("category")
        val accountIndex = header.indexOf("account")
        val targetIndex = header.indexOf("target_account")
        val noteIndex = header.indexOf("note")

        if (dateIndex < 0 || typeIndex < 0 || amountIndex < 0 || accountIndex < 0) {
            return CsvParseResult(
                emptyList(),
                0,
                "Expected columns: $HEADER"
            )
        }

        val rows = mutableListOf<ImportedTransaction>()
        var skipped = 0
        for (line in lines.drop(1)) {
            val cells = splitLine(line)
            fun cell(index: Int): String = if (index in cells.indices) cells[index].trim() else ""
            val date = runCatching { LocalDate.parse(cell(dateIndex)) }.getOrNull()
            val type = runCatching { TransactionType.valueOf(cell(typeIndex).uppercase()) }.getOrNull()
            val amount = cell(amountIndex).toLongOrNull()
            if (date == null || type == null || amount == null || amount <= 0L) {
                skipped++
                continue
            }
            rows.add(
                ImportedTransaction(
                    timestamp = Dates.toTimestamp(date),
                    type = type,
                    amountMinor = amount,
                    categoryName = cell(categoryIndex),
                    accountName = cell(accountIndex),
                    targetAccountName = cell(targetIndex),
                    note = cell(noteIndex)
                )
            )
        }
        return CsvParseResult(rows, skipped, null)
    }

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun splitLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    cells.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        cells.add(current.toString())
        return cells
    }
}

data class CsvParseResult(
    val rows: List<ImportedTransaction>,
    val skipped: Int,
    val error: String?
)
