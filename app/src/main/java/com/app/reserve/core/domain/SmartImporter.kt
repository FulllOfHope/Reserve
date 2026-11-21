package com.app.reserve.core.domain

import com.app.reserve.core.data.ImportedTransaction
import com.app.reserve.core.database.TransactionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.abs

/** Lightweight, on-device detection for CSV/JSON/TXT/ZIP tracker exports. */
object SmartImporter {

    const val MAX_FILE_BYTES = 20 * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true }
    private val supportedExtension = Regex("\\.(csv|tsv|txt|json)$", RegexOption.IGNORE_CASE)

    private val aliases = mapOf(
        SmartField.DATE to listOf(
            "date", "datetime", "time", "timestamp", "transaction date", "txn date",
            "transactiondatetime", "posted", "posting date", "value date", "occurred at",
            "created at", "createdat"
        ),
        SmartField.AMOUNT to listOf(
            "amount", "amountminor", "value", "sum", "total", "price", "money",
            "amount value", "transaction amount"
        ),
        SmartField.TYPE to listOf(
            "type", "direction", "transaction type", "transactiontype", "kind", "drcr",
            "creditdebit", "flow", "transaction kind"
        ),
        SmartField.CATEGORY to listOf(
            "category", "category name", "categoryname", "tag", "label", "group",
            "expense category", "subcategory"
        ),
        SmartField.ACCOUNT to listOf(
            "account", "account name", "accountname", "wallet", "source", "bank",
            "from", "from account", "payment method"
        ),
        SmartField.TARGET_ACCOUNT to listOf(
            "target account", "targetaccount", "to account", "destination", "transfer to",
            "to", "recipient"
        ),
        SmartField.NOTE to listOf(
            "note", "notes", "description", "desc", "memo", "details", "title",
            "narration", "remark", "reference", "particulars", "message"
        )
    )

    fun analyze(bytes: ByteArray): SmartImportAnalysis {
        if (bytes.isEmpty()) {
            return SmartImportAnalysis(error = "That file is empty.")
        }
        if (bytes.size > MAX_FILE_BYTES) {
            return SmartImportAnalysis(error = "That file is too large to import (20 MB limit).")
        }
        if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return analyzeZip(bytes)
                ?: SmartImportAnalysis(error = "No supported CSV, TXT or JSON file was found in that archive.")
        }
        return runCatching {
            val text = decode(bytes)
            if (text.isBlank()) {
                SmartImportAnalysis(error = "That file has no readable data.")
            } else if (text.length < 8) {
                SmartImportAnalysis(error = "That file has too little data to import.")
            } else {
                parseText(text)
            }
        }.getOrElse { SmartImportAnalysis(error = it.message ?: "That file could not be read.") }
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes.copyOfRange(3, bytes.size), Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16BE)
        }
        return runCatching {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrDefault(String(bytes, StandardCharsets.ISO_8859_1))
    }

    private fun parseText(text: String): SmartImportAnalysis {
        val trimmed = text.trimStart('\uFEFF').trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val parsed = parseJson(trimmed)
            if (parsed != null) return parsed
        }
        if (isZip(text)) return SmartImportAnalysis(error = "Compressed export could not be read.")
        return parseDelimitedExport(text)
    }

    private fun isZip(text: String): Boolean =
        text.length >= 2 && text[0] == 'P' && text[1] == 'K'

    /** ZIP bytes are decoded as text above, so callers use this entry point. */
    fun analyzeZip(bytes: ByteArray): SmartImportAnalysis? {
        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) return null
        var best: SmartImportAnalysis? = null
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && supportedExtension.containsMatchIn(entry.name)) {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var read = zip.read(buffer)
                        var total = 0
                        while (read > 0 && total <= MAX_FILE_BYTES) {
                            output.write(buffer, 0, read)
                            total += read
                            read = zip.read(buffer)
                        }
                        if (total <= MAX_FILE_BYTES) {
                            val candidate = analyze(output.toByteArray())
                            val currentBest = best
                            if (candidate.error == null && candidate.rows.isNotEmpty() &&
                                (currentBest == null || candidate.rows.size > currentBest.rows.size)
                            ) {
                                best = candidate
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return best ?: SmartImportAnalysis(error = "No supported CSV, TXT or JSON file was found in that archive.")
    }

    private fun parseJson(text: String): SmartImportAnalysis? {
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return null
        val objects = when (element) {
            is JsonArray -> element.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                val arrays = element.values.mapNotNull { it as? JsonArray }
                val records = arrays.maxByOrNull { it.size }
                if (records != null && records.size > 0) {
                    records.mapNotNull { it as? JsonObject }
                } else {
                    listOf(element)
                }
            }
            else -> return null
        }
        if (objects.isEmpty()) return SmartImportAnalysis(error = "No transaction records were found in that JSON file.")

        val headers = LinkedHashSet<String>()
        objects.forEach { obj ->
            obj.keys.forEach { key -> headers.add(key) }
        }
        if (headers.isEmpty()) return SmartImportAnalysis(error = "No transaction records were found in that JSON file.")
        val headerList = headers.toList()
        val rows = objects.map { obj ->
            headerList.map { header -> flattenValue(obj[header]) }
        }
        return finish(headerList, rows, "JSON", hasHeader = true)
    }

    private fun flattenValue(value: JsonElement?): String = when (value) {
        null -> ""
        is JsonPrimitive -> value.content
        is JsonArray -> value.joinToString(", ") { flattenValue(it) }
        is JsonObject -> value.entries.joinToString(" · ") { (key, child) ->
            if (child is JsonPrimitive) "$key: ${child.content}" else key
        }
    }

    private fun parseDelimitedExport(text: String): SmartImportAnalysis {
        val lines = text.lineSequence().filter { it.isNotBlank() }.take(40).toList()
        if (lines.isEmpty()) return SmartImportAnalysis(error = "That file is empty.")
        val delimiter = detectDelimiter(lines)
        val rows = parseDelimited(text, delimiter).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return SmartImportAnalysis(error = "No rows were found in that file.")

        val first = rows.first()
        val hasHeader = first.any { cell -> isHeaderCell(cell) }
        val headers = if (hasHeader) {
            first.mapIndexed { index, cell -> cell.trim().ifBlank { "Column ${index + 1}" } }
        } else {
            first.indices.map { "Column ${it + 1}" }
        }
        val dataRows = if (hasHeader) rows.drop(1) else rows
        return finish(headers, dataRows, delimiterName(delimiter), hasHeader)
    }

    private fun detectDelimiter(lines: List<String>): Char {
        val candidates = listOf(',', ';', '\t', '|')
        return candidates.maxByOrNull { delimiter ->
            lines.sumOf { line -> countOutsideQuotes(line, delimiter) }
        } ?: ','
    }

    private fun countOutsideQuotes(line: String, delimiter: Char): Int {
        var count = 0
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            when (line[index]) {
                '"' -> {
                    if (inQuotes && index + 1 < line.length && line[index + 1] == '"') index++
                    else inQuotes = !inQuotes
                }
                delimiter -> if (!inQuotes) count++
            }
            index++
        }
        return count
    }

    /** Quote-aware parser; supports escaped quotes and newlines inside cells. */
    private fun parseDelimited(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                inQuotes && char == '"' -> {
                    if (index + 1 < text.length && text[index + 1] == '"') {
                        cell.append('"')
                        index++
                    } else {
                        inQuotes = false
                    }
                }
                char == '"' && cell.isEmpty() -> inQuotes = true
                char == delimiter && !inQuotes -> {
                    row.add(cell.toString().trim())
                    cell.clear()
                }
                (char == '\n' || char == '\r') && !inQuotes -> {
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    row.add(cell.toString().trim())
                    cell.clear()
                    rows.add(row.toList())
                    row.clear()
                }
                else -> cell.append(char)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString().trim())
            rows.add(row.toList())
        }
        return rows
    }

    private fun delimiterName(delimiter: Char): String = when (delimiter) {
        '\t' -> "Tab-separated"
        ';' -> "Semicolon-separated"
        '|' -> "Pipe-separated"
        else -> "Comma-separated"
    }

    private fun finish(
        headers: List<String>,
        rows: List<List<String>>,
        format: String,
        hasHeader: Boolean
    ): SmartImportAnalysis {
        if (rows.isEmpty()) return SmartImportAnalysis(error = "No transaction rows were found.")
        val mapping = detectMapping(headers, rows)
        return SmartImportAnalysis(
            format = format,
            headers = headers,
            rows = rows,
            mapping = mapping,
            hasHeader = hasHeader
        )
    }

    private fun detectMapping(headers: List<String>, rows: List<List<String>>): SmartImportMapping {
        val mapping = SmartImportMapping()
        val used = mutableSetOf<SmartField>()
        headers.forEachIndexed { index, header ->
            val normalized = normalize(header)
            SmartField.entries.forEach { field ->
                if (field !in used && aliases[field]?.any { normalized == it || normalized.replace(" ", "") == it.replace(" ", "") } == true) {
                    when (field) {
                        SmartField.DATE -> mapping.date = index
                        SmartField.AMOUNT -> {
                            mapping.amount = index
                            if (normalized.contains("minor") || normalized.contains("cents")) {
                                mapping.amountIsMinor = true
                            }
                        }
                        SmartField.TYPE -> mapping.type = index
                        SmartField.CATEGORY -> mapping.category = index
                        SmartField.ACCOUNT -> mapping.account = index
                        SmartField.TARGET_ACCOUNT -> mapping.targetAccount = index
                        SmartField.NOTE -> mapping.note = index
                    }
                    used.add(field)
                }
            }
        }

        // Header names like debit/credit can carry the type even without a type column.
        if (mapping.type < 0) {
            headers.forEachIndexed { index, header ->
                val inferred = inferTypeFromText(header)
                if (inferred != null && mapping.amount >= 0 && index == mapping.amount) {
                    mapping.defaultType = inferred
                }
            }
        }

        // Fall back to value shapes for exports with unusual or missing headers.
        val sample = rows.take(8)
        if (mapping.date < 0) mapping.date = sampleColumn(sample) { parseDate(it) != null }
        if (mapping.amount < 0) mapping.amount = sampleColumn(sample) { parseAmountValue(it) != null }
        if (mapping.type < 0) mapping.type = sampleColumn(sample) { inferTypeFromText(it) != null }
        if (mapping.category < 0) mapping.category = sampleColumn(sample, preferText = true) { it.length in 2..40 }
        if (mapping.account < 0) mapping.account = sampleColumn(sample, preferText = true) { it.length in 2..40 }
        if (mapping.note < 0) mapping.note = sampleColumn(sample, preferText = true) { it.length > 8 }
        return mapping
    }

    private fun sampleColumn(
        rows: List<List<String>>,
        preferText: Boolean = false,
        predicate: (String) -> Boolean
    ): Int {
        val width = rows.maxOfOrNull { it.size } ?: return -1
        var best = -1
        var bestScore = 0
        for (index in 0 until width) {
            val values = rows.mapNotNull { it.getOrNull(index)?.trim() }.filter { it.isNotEmpty() }
            if (values.isEmpty()) continue
            val score = values.count(predicate)
            if (score > bestScore && (!preferText || values.any { it.contains(" ") } || score >= values.size)) {
                best = index
                bestScore = score
            }
        }
        return best
    }

    fun toTransactions(
        analysis: SmartImportAnalysis,
        mapping: SmartImportMapping,
        defaultType: TransactionType = TransactionType.EXPENSE,
        amountIsMinor: Boolean = mapping.amountIsMinor
    ): List<ImportedTransaction> {
        val effective = mapping.copy(defaultType = defaultType, amountIsMinor = amountIsMinor)
        return analysis.rows.mapNotNull { row ->
            val dateText = cell(row, effective.date)
            val amountText = cell(row, effective.amount)
            val timestamp = parseDate(dateText) ?: return@mapNotNull null
            val parsedAmount = parseAmountValue(amountText) ?: return@mapNotNull null
            // parseAmountValue returns minor units from a decimal string. When the
            // source column is already in cents, undo that one extra x100.
            val rawAmount = if (amountIsMinor) parsedAmount / 100 else parsedAmount
            if (rawAmount == 0L) return@mapNotNull null
            val amount = abs(rawAmount)
            val explicitType = inferTypeFromText(cell(row, effective.type))
            val headerType = analysis.headers.getOrNull(effective.amount)?.let(::inferTypeFromText)
            val negative = amountText.trim().startsWith("-") || amountText.trim().startsWith("(")
            val type = explicitType ?: headerType
                ?: if (negative) TransactionType.EXPENSE else effective.defaultType
            val category = cell(row, effective.category)
            val account = cell(row, effective.account)
            val target = cell(row, effective.targetAccount)
            val note = listOf(cell(row, effective.note))
                .firstOrNull { it.isNotBlank() }.orEmpty()
            ImportedTransaction(
                timestamp = timestamp,
                type = type,
                amountMinor = amount,
                categoryName = category,
                accountName = account,
                targetAccountName = target,
                note = note
            )
        }
    }

    private fun cell(row: List<String>, index: Int): String =
        if (index in row.indices) row[index].trim() else ""

    fun parseDate(value: String): Long? {
        val text = value.trim()
        if (text.isBlank()) return null
        val digits = text.filter { it.isDigit() }
        if (text.toLongOrNull() != null && digits.length in 10..13) {
            return when (digits.length) {
                10 -> digits.toLong() * 1000
                13 -> digits.toLong()
                else -> null
            }
        }
        val normalized = text.replace("T", " ").substringBefore("+").trim()
        val formatters = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH)
        )
        for (formatter in formatters) {
            runCatching {
                when {
                    normalized.contains(":") && formatter == DateTimeFormatter.ISO_LOCAL_DATE_TIME ->
                        java.time.LocalDateTime.parse(normalized, formatter).toLocalDate()
                    formatter == DateTimeFormatter.ISO_LOCAL_DATE_TIME -> return@runCatching null
                    normalized.contains(":") -> return@runCatching null
                    else -> LocalDate.parse(normalized.take(30), formatter)
                }
            }.getOrNull()?.let { return Dates.toTimestamp(it) }
        }
        // Numeric dates such as 04/05/2026 10:30 — take the date part.
        val dateOnly = normalized.substringBefore(" ").trim()
        return runCatching {
            val parts = dateOnly.split("/", "-", ".")
            if (parts.size != 3) return@runCatching null
            val first = parts[0].toInt()
            val second = parts[1].toInt()
            val third = parts[2].toInt()
            val year: Int
            val month: Int
            val day: Int
            if (first > 31) {
                year = first
                month = second
                day = third
            } else if (third > 31) {
                year = third
                month = if (first > 12) first else second
                day = if (first > 12) second else first
            } else {
                year = if (third < 100) 2000 + third else third
                month = if (first > 12) first else second
                day = if (first > 12) second else first
            }
            Dates.toTimestamp(LocalDate.of(year, month, day))
        }.getOrNull()
    }

    fun parseAmountValue(value: String): Long? {
        var text = value.trim()
        if (text.isBlank()) return null
        val negative = text.startsWith("(") && text.endsWith(")") || text.startsWith("-")
        text = text.trim('(', ')', '+', '-')
        text = text.replace(Regex("[^0-9.,]"), "")
        if (text.isBlank()) return null
        val hasDot = text.contains('.')
        val hasComma = text.contains(',')
        val decimalSeparator: Char? = when {
            hasDot && hasComma -> if (text.lastIndexOf('.') > text.lastIndexOf(',')) '.' else ','
            hasComma -> {
                val tail = text.substringAfterLast(',')
                if (tail.length in 1..2 && tail.all { it.isDigit() }) ',' else null
            }
            hasDot -> {
                val tail = text.substringAfterLast('.')
                if (tail.length in 1..2 && tail.all { it.isDigit() }) '.' else null
            }
            else -> null
        }
        val normalized = if (decimalSeparator == null) {
            text.replace(".", "").replace(",", "")
        } else {
            val other = if (decimalSeparator == '.') ',' else '.'
            text.replace(other.toString(), "")
                .replace(decimalSeparator.toString(), ".")
        }
        val major = normalized.toDoubleOrNull() ?: return null
        val minor = kotlin.math.round(major * 100).toLong()
        return if (negative) -minor else minor
    }

    fun inferTypeFromText(value: String): TransactionType? {
        val text = normalize(value)
        return when {
            text.contains("transfer") || text == "trf" -> TransactionType.TRANSFER
            text.contains("income") || text.contains("credit") || text == "cr" ||
                text == "in" || text.contains("deposit") || text.contains("refund") ||
                text.contains("salary") -> TransactionType.INCOME
            text.contains("expense") || text.contains("debit") || text == "dr" ||
                text == "out" || text.contains("withdraw") || text.contains("spend") ||
                text.contains("payment") || text.contains("purchase") -> TransactionType.EXPENSE
            else -> null
        }
    }

    private fun isHeaderCell(value: String): Boolean {
        val normalized = normalize(value)
        return aliases.values.flatten().any { alias -> normalized == alias || normalized.replace(" ", "") == alias.replace(" ", "") } ||
            normalized.contains("amount") || normalized.contains("date") ||
            normalized.contains("category") || normalized.contains("account") ||
            normalized.contains("description") || normalized == "type"
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT)
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
}

enum class SmartField(val label: String) {
    DATE("Date"),
    AMOUNT("Amount"),
    TYPE("Type"),
    CATEGORY("Category"),
    ACCOUNT("Account"),
    TARGET_ACCOUNT("Target account"),
    NOTE("Note")
}

data class SmartImportMapping(
    var date: Int = -1,
    var amount: Int = -1,
    var type: Int = -1,
    var category: Int = -1,
    var account: Int = -1,
    var targetAccount: Int = -1,
    var note: Int = -1,
    var defaultType: TransactionType = TransactionType.EXPENSE,
    var amountIsMinor: Boolean = false
)

data class SmartImportAnalysis(
    val format: String = "",
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val mapping: SmartImportMapping = SmartImportMapping(),
    val hasHeader: Boolean = false,
    val error: String? = null
)
