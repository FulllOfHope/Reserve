package com.app.reserve.core.domain

import com.app.reserve.core.database.TransactionType
import java.security.MessageDigest

/**
 * A transactional SMS that has been understood well enough to become a ledger
 * row. Everything here is derived on-device; the raw message is never stored
 * or sent anywhere.
 */
data class ParsedSms(
    /** Stable hash of sender + timestamp + body, used to suppress duplicates. */
    val fingerprint: String,
    val type: TransactionType,
    val amountMinor: Long,
    val accountName: String,
    val categoryName: String?,
    val timestamp: Long,
    val note: String
)

/**
 * Parses Indian bank / wallet transaction alerts. Deliberately conservative:
 * a message must contain a directional verb (debited, credited, spent, …) and
 * a currency amount, so OTPs, promos, balances and personal texts are ignored.
 */
object SmsParser {

    private val amountRegex = Regex(
        """(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val bareAmountRegex = Regex(
        """\b(?:debited|credited|spent|paid|received|withdrawn|deposited|transferred)\b""" +
            """[\s\S]{0,24}?([0-9][0-9,]*(?:\.[0-9]{1,2})?)\b""",
        RegexOption.IGNORE_CASE
    )

    private val debitRegex = Regex(
        """\b(?:debited|debit\s+of|auto[\s-]*debit|spent|withdrawn|withdrawal\s+of|paid|payment\s+of|purchase\s+of|transaction\s+of|txn\s+of|used\s+(?:at|for|on)|sent|transferred|dr)\b""",
        RegexOption.IGNORE_CASE
    )

    private val creditRegex = Regex(
        """\b(?:credited|credit\s+of|received|received\s+from|deposited|refund(?:ed)?|reversal)\b""",
        RegexOption.IGNORE_CASE
    )

    private val otpRegex = Regex(
        """\b(?:otp|one[\s-]*time[\s-]*password|verification\s+code|do\s+not\s+share)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Verbs that prove a message is a transaction even if it also says "OTP". */
    private val transactionVerbRegex = Regex(
        """\b(?:debited|credited|spent|withdrawn|deposited|refund(?:ed)?)\b""",
        RegexOption.IGNORE_CASE
    )

    private val balanceBeforeRegex = Regex(
        """(?:avl\.?|available|bal(?:ance)?|limit|total)\s*[^0-9]{0,14}$""",
        RegexOption.IGNORE_CASE
    )

    private val last4Regexes = listOf(
        Regex("""(?:a/c|ac|account|acct|card)\s*(?:no\.?|number)?\s*[xX*.]*(\d{4})\b""", RegexOption.IGNORE_CASE),
        Regex("""ending\s+(?:with\s+)?(?:[xX*.]*)(\d{4})\b""", RegexOption.IGNORE_CASE),
        Regex("""[xX*]{2,}(\d{4})\b"""),
        Regex("""(?:a/c|account)\s*\.{2,}(\d{4})\b""", RegexOption.IGNORE_CASE)
    )

    private val bankSenders: List<Pair<String, String>> = listOf(
        "HDFC" to "HDFC Bank",
        "ICICI" to "ICICI Bank",
        "SBIN" to "State Bank of India",
        "SBI" to "State Bank of India",
        "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Mahindra Bank",
        "YES" to "YES Bank",
        "IDFC" to "IDFC FIRST Bank",
        "PNB" to "Punjab National Bank",
        "BARB" to "Bank of Baroda",
        "BOB" to "Bank of Baroda",
        "CANARA" to "Canara Bank",
        "CANBNK" to "Canara Bank",
        "UNION" to "Union Bank of India",
        "IDBI" to "IDBI Bank",
        "FEDERAL" to "Federal Bank",
        "INDUS" to "IndusInd Bank",
        "RBL" to "RBL Bank",
        "BANDHAN" to "Bandhan Bank",
        "AUBANK" to "AU Small Finance Bank",
        "PAYTM" to "Paytm Payments Bank",
        "AIRTEL" to "Airtel Payments Bank",
        "JIO" to "Jio Payments Bank",
        "AMEX" to "American Express",
        "SCB" to "Standard Chartered",
        "HSBC" to "HSBC",
        "CITI" to "Citibank",
        "ONECARD" to "OneCard",
        "SLICE" to "Slice",
        "CRED" to "CRED",
        "BOI" to "Bank of India",
        "IOB" to "Indian Overseas Bank",
        "CENTRAL" to "Central Bank of India",
        "UCO" to "UCO Bank",
        "PSB" to "Punjab & Sind Bank",
        "TMB" to "Tamilnad Mercantile Bank",
        "KVB" to "Karur Vysya Bank",
        "CUB" to "City Union Bank",
        "SOUTH" to "South Indian Bank",
        "DHAN" to "Dhanlaxmi Bank",
        "JKB" to "Jammu & Kashmir Bank",
        "JUPITER" to "Jupiter",
        "FI" to "Fi Money",
        "NAVI" to "Navi",
        "FAMP" to "Fampay"
    ).sortedByDescending { it.first.length }

    private val bankBodyPatterns: List<Pair<Regex, String>> = listOf(
        Regex("""\bstate\s+bank\s+of\s+india\b""", RegexOption.IGNORE_CASE) to "State Bank of India",
        Regex("""\bhdfc\s+bank\b""", RegexOption.IGNORE_CASE) to "HDFC Bank",
        Regex("""\bicici\s+bank\b""", RegexOption.IGNORE_CASE) to "ICICI Bank",
        Regex("""\baxis\s+bank\b""", RegexOption.IGNORE_CASE) to "Axis Bank",
        Regex("""\bkotak(?:\s+mahindra)?\s+bank\b""", RegexOption.IGNORE_CASE) to "Kotak Mahindra Bank",
        Regex("""\byes\s+bank\b""", RegexOption.IGNORE_CASE) to "YES Bank",
        Regex("""\bidfc\s+(?:first\s+)?bank\b""", RegexOption.IGNORE_CASE) to "IDFC FIRST Bank",
        Regex("""\bpunjab\s+national\s+bank\b""", RegexOption.IGNORE_CASE) to "Punjab National Bank",
        Regex("""\bbank\s+of\s+baroda\b""", RegexOption.IGNORE_CASE) to "Bank of Baroda",
        Regex("""\bcanara\s+bank\b""", RegexOption.IGNORE_CASE) to "Canara Bank",
        Regex("""\bunion\s+bank\s+of\s+india\b""", RegexOption.IGNORE_CASE) to "Union Bank of India",
        Regex("""\bindusind\s+bank\b""", RegexOption.IGNORE_CASE) to "IndusInd Bank",
        Regex("""\bfederal\s+bank\b""", RegexOption.IGNORE_CASE) to "Federal Bank",
        Regex("""\brbl\s+bank\b""", RegexOption.IGNORE_CASE) to "RBL Bank",
        Regex("""\bidbi\s+bank\b""", RegexOption.IGNORE_CASE) to "IDBI Bank",
        Regex("""\bpaytm\s+(?:payments\s+)?bank\b""", RegexOption.IGNORE_CASE) to "Paytm Payments Bank",
        Regex("""\bairtel\s+payments\s+bank\b""", RegexOption.IGNORE_CASE) to "Airtel Payments Bank",
        Regex("""\bjio\s+payments\s+bank\b""", RegexOption.IGNORE_CASE) to "Jio Payments Bank",
        Regex("""\bamerican\s+express\b""", RegexOption.IGNORE_CASE) to "American Express",
        Regex("""\bstandard\s+chartered\b""", RegexOption.IGNORE_CASE) to "Standard Chartered"
    )

    private val categoryRules: List<Pair<Regex, String>> = listOf(
        Regex("""\b(swiggy|zomato|restaurant|cafe|food|eatery|dominos|pizza|kfc|mcdonald)\b""", RegexOption.IGNORE_CASE) to "Food",
        Regex("""\b(uber|ola|rapido|metro|fuel|petrol|diesel|hpcl|iocl|bpcl|parking|toll)\b""", RegexOption.IGNORE_CASE) to "Transport",
        Regex("""\b(amazon|flipkart|myntra|ajio|meesho|nykaa|shopping|mall|store)\b""", RegexOption.IGNORE_CASE) to "Shopping",
        Regex("""\b(electricity|water bill|gas bill|broadband|recharge|dth|postpaid|prepaid|bill pay|insurance)\b""", RegexOption.IGNORE_CASE) to "Bills",
        Regex("""\b(hospital|pharmacy|medical|clinic|apollo|medplus|health)\b""", RegexOption.IGNORE_CASE) to "Health",
        Regex("""\b(netflix|spotify|hotstar|bookmyshow|pvr|cinema|game)\b""", RegexOption.IGNORE_CASE) to "Fun"
    )

    private val salaryRegex = Regex(
        """\b(salary|payroll|stipend|wages)\b""",
        RegexOption.IGNORE_CASE
    )

    fun parse(sender: String?, body: String, timestamp: Long): ParsedSms? {
        val text = body.replace('\u00A0', ' ').trim()
        if (text.length < 8) return null

        // Bank alerts arrive from short codes or DLT sender IDs, never from a
        // personal mobile number. This keeps one-to-one chats out of the ledger.
        if (isPersonalNumber(sender)) return null

        // OTPs are never transactions unless a settled-transaction verb is present.
        if (otpRegex.containsMatchIn(text) && !transactionVerbRegex.containsMatchIn(text)) return null

        val debitMatch = debitRegex.find(text)
        val creditMatch = creditRegex.find(text)
        val type = when {
            debitMatch == null && creditMatch == null -> return null
            debitMatch == null -> TransactionType.INCOME
            creditMatch == null -> TransactionType.EXPENSE
            // Both appear ("debited … transferred to …", "credit card … debited"):
            // the verb that appears first describes the movement.
            debitMatch.range.first <= creditMatch.range.first -> TransactionType.EXPENSE
            else -> TransactionType.INCOME
        }

        val amount = pickAmount(text) ?: return null
        if (amount <= 0L) return null

        val bank = bankFor(sender, text)
        val last4 = last4For(text)
        val accountName = buildString {
            append(bank ?: "Other Bank")
            if (last4 != null) append(" ••").append(last4)
        }

        return ParsedSms(
            fingerprint = fingerprint(sender, timestamp, text),
            type = type,
            amountMinor = amount,
            accountName = accountName,
            categoryName = categoryFor(text, type),
            timestamp = timestamp,
            note = "SMS · $accountName"
        )
    }

    /**
     * Chooses the transaction amount rather than an "Avl Bal" figure. Prefers
     * an amount that sits next to a debit/credit verb; falls back to the first
     * non-balance amount, then to a bare "debited 500" style number.
     */
    private fun pickAmount(text: String): Long? {
        val candidates = amountRegex.findAll(text).mapNotNull { match ->
            val start = match.range.first
            val before = text.substring(maxOf(0, start - 28), start)
            val beforeWindow = text.substring(maxOf(0, start - 48), start)
            val afterWindow = text.substring(
                minOf(text.length, match.range.last + 1),
                minOf(text.length, match.range.last + 25)
            )
            val balanceLabel = balanceBeforeRegex.containsMatchIn(before)
            val verbBefore = debitRegex.containsMatchIn(beforeWindow) ||
                creditRegex.containsMatchIn(beforeWindow)
            val verbAfter = debitRegex.containsMatchIn(afterWindow) ||
                creditRegex.containsMatchIn(afterWindow)
            // "Avl Bal" is a running balance, not the amount that moved.
            if (balanceLabel) return@mapNotNull null
            val value = Money.parse(match.groupValues[1]) ?: return@mapNotNull null
            if (value <= 0L) return@mapNotNull null
            value to (verbBefore || verbAfter)
        }.toList()

        candidates.firstOrNull { it.second }?.let { return it.first }
        candidates.firstOrNull()?.let { return it.first }

        val bare = bareAmountRegex.find(text)?.groupValues?.getOrNull(1)
        return bare?.let { Money.parse(it) }
    }

    private fun bankFor(sender: String?, body: String): String? {
        val normalized = normalizeSender(sender)
        if (normalized.isNotEmpty()) {
            bankSenders.firstOrNull { normalized.startsWith(it.first) }?.let { return it.second }
        }
        return bankBodyPatterns.firstOrNull { it.first.containsMatchIn(body) }?.second
    }

    private fun normalizeSender(sender: String?): String {
        if (sender.isNullOrBlank()) return ""
        val afterOperator = sender.substringAfterLast('-', sender)
        return afterOperator.uppercase().filter { it.isLetterOrDigit() }
    }

    private fun isPersonalNumber(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val digits = sender.filter { it.isDigit() }
        return digits.length >= 10 && sender.none { it.isLetter() }
    }

    private fun last4For(text: String): String? {
        for (regex in last4Regexes) {
            val digits = regex.find(text)?.groupValues?.getOrNull(1)
            if (digits != null && digits.length == 4) return digits
        }
        return null
    }

    private fun categoryFor(text: String, type: TransactionType): String? = when (type) {
        TransactionType.INCOME ->
            if (salaryRegex.containsMatchIn(text)) "Salary" else null
        TransactionType.EXPENSE ->
            categoryRules.firstOrNull { it.first.containsMatchIn(text) }?.second
        TransactionType.TRANSFER -> null
    }

    private fun fingerprint(sender: String?, timestamp: Long, body: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("${sender.orEmpty()}|$timestamp|$body".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
