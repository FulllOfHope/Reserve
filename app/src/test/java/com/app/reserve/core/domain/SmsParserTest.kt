package com.app.reserve.core.domain

import com.app.reserve.core.database.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    private val timestamp = 1_700_000_000_000L

    private fun parse(sender: String, body: String) = SmsParser.parse(sender, body, timestamp)

    @Test
    fun `parses hdfc debit and ignores available balance`() {
        val sms = parse(
            "VK-HDFCBK",
            "Rs.1,250.50 debited from a/c XX1234 on 11-11-26. Avl Bal Rs.10,000.00. -HDFC Bank"
        )
        assertNotNull(sms)
        assertEquals(TransactionType.EXPENSE, sms!!.type)
        assertEquals(125_050L, sms.amountMinor)
        assertEquals("HDFC Bank ••1234", sms.accountName)
        assertEquals("SMS · HDFC Bank ••1234", sms.note)
    }

    @Test
    fun `parses salary credit as income`() {
        val sms = parse(
            "AD-HDFCBK",
            "Rs 75,000.00 credited to your account XX9876 towards salary. Avl Bal Rs 1,00,000.00"
        )
        assertNotNull(sms)
        assertEquals(TransactionType.INCOME, sms!!.type)
        assertEquals(7_500_000L, sms.amountMinor)
        assertEquals("HDFC Bank ••9876", sms.accountName)
        assertEquals("Salary", sms.categoryName)
    }

    @Test
    fun `parses sbi debit with indian grouping`() {
        val sms = parse(
            "VM-SBIINB",
            "Your a/c no. XXXXXXXX4321 debited by Rs 1,23,456.78 on 10-11-26. Avl Bal Rs 5,000"
        )
        assertNotNull(sms)
        assertEquals(TransactionType.EXPENSE, sms!!.type)
        assertEquals(12_345_678L, sms.amountMinor)
        assertEquals("State Bank of India ••4321", sms.accountName)
    }

    @Test
    fun `parses icici credit`() {
        val sms = parse("AD-ICICIB", "INR 2,000.00 has been credited to your account ending 2233.")
        assertNotNull(sms)
        assertEquals(TransactionType.INCOME, sms!!.type)
        assertEquals(200_000L, sms.amountMinor)
        assertEquals("ICICI Bank ••2233", sms.accountName)
    }

    @Test
    fun `sent to is treated as an expense`() {
        val sms = parse("VK-PAYTM", "Rs 499.00 sent to Swiggy via UPI from your Paytm account.")
        assertNotNull(sms)
        assertEquals(TransactionType.EXPENSE, sms!!.type)
        assertEquals(49_900L, sms.amountMinor)
        assertEquals("Paytm Payments Bank", sms.accountName)
        assertEquals("Food", sms.categoryName)
    }

    @Test
    fun `rejects otp messages`() {
        assertNull(parse("VK-SBIOTP", "123456 is your OTP for a transaction of Rs 500. Do not share."))
    }

    @Test
    fun `rejects promotional messages`() {
        assertNull(parse("AD-PROMO", "Win Rs 5,000 cashback on your credit card. Apply now!"))
    }

    @Test
    fun `rejects balance-only alerts`() {
        assertNull(parse("VK-HDFCBK", "Your Avl Bal is Rs 12,500.00 as on 11-11-26."))
    }

    @Test
    fun `rejects personal messages that mention money`() {
        assertNull(parse("+919876543210", "Bro I paid Rs 500 for the tickets, send me your share."))
    }

    @Test
    fun `falls back to a bare amount after a transaction verb`() {
        val sms = parse("AD-AXISBK", "Your account debited by 750 for UPI transfer.")
        assertNotNull(sms)
        assertEquals(TransactionType.EXPENSE, sms!!.type)
        assertEquals(75_000L, sms.amountMinor)
        assertEquals("Axis Bank", sms.accountName)
    }

    @Test
    fun `prefers transaction amount when balance appears first`() {
        val sms = parse("VK-KOTAKB", "Available balance is Rs 50,000. Rs 300 debited for recharge.")
        assertNotNull(sms)
        assertEquals(30_000L, sms!!.amountMinor)
    }

    @Test
    fun `debit wins when both verbs appear`() {
        val sms = parse(
            "VK-HDFCBK",
            "Rs 1,000 debited from your credit card ending 4455. Amount credited to merchant."
        )
        assertNotNull(sms)
        assertEquals(TransactionType.EXPENSE, sms!!.type)
        assertEquals(100_000L, sms.amountMinor)
        assertEquals("HDFC Bank ••4455", sms.accountName)
    }

    @Test
    fun `categorises common merchants`() {
        assertEquals("Transport", parse("VK-HDFCBK", "Rs 250 debited for Uber ride")!!.categoryName)
        assertEquals("Shopping", parse("VK-HDFCBK", "Rs 999 debited at Amazon")!!.categoryName)
        assertEquals("Bills", parse("VK-HDFCBK", "Rs 600 debited for electricity bill")!!.categoryName)
    }

    @Test
    fun `detects bank from the message body when sender is unknown`() {
        val sms = parse("AX-UNKNOWN", "Rs 100 debited. Your HDFC Bank account has been updated.")
        assertNotNull(sms)
        assertEquals("HDFC Bank", sms!!.accountName)
    }

    @Test
    fun `parses credit card usage alerts`() {
        val sms = parse(
            "AD-HDFCBK",
            "Your HDFC Bank Credit Card XX7788 was used at AMAZON for INR 2,499.00 on 11-11-26."
        )
        assertNotNull(sms)
        assertEquals(TransactionType.EXPENSE, sms!!.type)
        assertEquals(249_900L, sms.amountMinor)
        assertEquals("HDFC Bank ••7788", sms.accountName)
        assertEquals("Shopping", sms.categoryName)
    }

    @Test
    fun `parses transaction of phrasing`() {
        val sms = parse("VK-ICICIB", "Your card has a transaction of Rs 350.00 at a merchant.")
        assertNotNull(sms)
        assertEquals(TransactionType.EXPENSE, sms!!.type)
        assertEquals(35_000L, sms.amountMinor)
    }

    @Test
    fun `fingerprints are stable within a minute bucket`() {
        val body = "Rs 100 debited from a/c XX1111"
        val first = SmsParser.parse("VK-HDFCBK", body, timestamp)!!
        val again = SmsParser.parse("VK-HDFCBK", body, timestamp + 30_000)!!
        val later = SmsParser.parse("VK-HDFCBK", body, timestamp + 120_000)!!
        assertEquals(first.fingerprint, again.fingerprint)
        assertFalse(first.fingerprint == later.fingerprint)
        assertTrue(first.fingerprint.length == 64)
    }

    @Test
    fun `parses exact canara zomato alert with balance`() {
        val body = "Dear Customer, Acct XXXX0105 Dr. INR 123.69 on 17/11/26 to ZOMATO; " +
            "UPI: xxxx; Bal INR 582.18.Not you?SMS BLOCKUPI to xxxxxxxxx-CanaraBank"
        val sms = SmsParser.parse("CAN-CANBNK", body, timestamp)!!
        assertEquals(TransactionType.EXPENSE, sms.type)
        assertEquals(12_369L, sms.amountMinor)
        assertEquals(58_218L, sms.balanceAfterMinor)
        assertEquals("Canara Bank ••0105", sms.accountName)
        assertEquals("Food", sms.categoryName)
        assertEquals("SMS · Canara Bank ••0105", sms.note)
    }

    @Test
    fun `detects canara bank without a space`() {
        val sms = parse("XX-UNKNOWN", "Rs 500 debited. -CanaraBank")!!
        assertEquals("Canara Bank", sms.accountName)
    }

    @Test
    fun `fingerprint survives whitespace and timestamp variance`() {
        val firstBody = "Dear Customer, Acct XXXX0105 Dr. INR 123.69 on 17/11/26 to ZOMATO; " +
            "UPI: xxxx; Bal INR 582.18.Not you?SMS BLOCKUPI to xxxxxxxxx-CanaraBank"
        val secondBody = "Dear  Customer, Acct XXXX0105 Dr. INR 123.69 on 17/11/26 to ZOMATO; " +
            "UPI: xxxx; Bal INR 582.18. Not you? SMS BLOCKUPI to xxxxxxxxx-CanaraBank"
        // Anchor inside the minute so the 45s variance stays in one bucket.
        val firstTime = timestamp - 10_000L
        val first = SmsParser.parse("CAN-CANBNK", firstBody, firstTime)!!
        val second = SmsParser.parse(" can-canbnk ", secondBody, firstTime + 45_000L)!!
        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(first.amountMinor, second.amountMinor)
        assertEquals(first.balanceAfterMinor, second.balanceAfterMinor)
    }

    @Test
    fun `captures available balance without using it as the amount`() {
        val sms = parse(
            "VK-HDFCBK",
            "Rs.1,250.50 debited from a/c XX1234 on 11-11-26. Avl Bal Rs.10,000.00. -HDFC Bank"
        )!!
        assertEquals(125_050L, sms.amountMinor)
        assertEquals(1_000_000L, sms.balanceAfterMinor)
    }

    @Test
    fun `balance is null when the sms does not print one`() {
        val sms = parse("AD-ICICIB", "INR 2,000.00 has been credited to your account ending 2233.")!!
        assertNull(sms.balanceAfterMinor)
    }

    @Test
    fun `rejects pre approved loan offers even though they say credited`() {
        assertNull(
            parse(
                "AD-HDFCBK",
                "Pre-approved loan of Rs. 23,00,000 credited to your account XX1234. Apply now!"
            )
        )
    }

    @Test
    fun `does not read a credit card limit as a cash balance`() {
        val sms = parse(
            "AD-HDFCBK",
            "Rs. 450.00 spent on Swiggy. Avl Limit Rs. 2,50,000. -HDFC Bank"
        )!!
        assertEquals(45_000L, sms.amountMinor)
        assertNull(sms.balanceAfterMinor)
    }

    @Test
    fun `rejects failed and declined transactions`() {
        assertNull(parse("VK-HDFCBK", "Rs 500 debited from a/c XX1234 on 11-11-26. Transaction failed."))
        assertNull(parse("VK-ICICIB", "Rs 900 payment declined. Try another method. -ICICI Bank"))
    }

    @Test
    fun `rejects upi mandate creation notices`() {
        assertNull(parse("AD-AXISBK", "UPI mandate created for Rs 5,000. -Axis Bank"))
        assertNull(parse("VK-HDFCBK", "AutoPay registered for your Netflix payment. -HDFC Bank"))
    }

    @Test
    fun `rejects reference numbers that follow a debit verb`() {
        assertNull(parse("VK-HDFCBK", "Debited from A/c XX1234 Ref 2300000. -HDFC Bank"))
    }

    @Test
    fun `rejects amounts above the sanity ceiling`() {
        assertNull(parse("VK-HDFCBK", "Rs. 60,00,000 debited from a/c XX1234. -HDFC Bank"))
    }

    @Test
    fun `sanity ceiling applies to the amount only, never to the balance`() {
        val sms = parse(
            "VK-HDFCBK",
            "Rs. 1,250.00 debited from a/c XX1234. Avl Bal Rs. 60,00,000. -HDFC Bank"
        )!!
        assertEquals(125_000L, sms.amountMinor)
        // ₹60 lakh is above the amount ceiling but a perfectly valid balance.
        assertEquals(600_000_000L, sms.balanceAfterMinor)
    }

    @Test
    fun `standalone Dr title is not a debit verb`() {
        assertNull(parse("XX-UNKNOWN", "Dr. Smith will visit the clinic tomorrow at 5 pm."))
    }

    @Test
    fun `account context keeps Dr as a valid debit verb`() {
        val sms = parse(
            "CAN-CANBNK",
            "Dear Customer, Acct XXXX0105 Dr. INR 123.69 on 17/11/26 to ZOMATO; Bal INR 582.18.-CanaraBank"
        )!!
        assertEquals(TransactionType.EXPENSE, sms.type)
        assertEquals(12_369L, sms.amountMinor)
    }
}
