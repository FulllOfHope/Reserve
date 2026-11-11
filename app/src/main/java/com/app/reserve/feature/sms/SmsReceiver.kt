package com.app.reserve.feature.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.app.reserve.core.database.ReserveDatabase
import com.app.reserve.core.database.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

import com.app.reserve.core.database.TransactionType
import java.util.UUID

class SmsReceiver : BroadcastReceiver() {
    
    // Extractor regex for Indian banking formats
    private val amountPattern = Pattern.compile("(?:Rs\\.?|INR|₹)\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE)
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.displayMessageBody
                val sender = sms.displayOriginatingAddress
                
                parseAndSaveTransaction(context, sender, body)
            }
        }
    }
    
    private fun parseAndSaveTransaction(context: Context, sender: String, body: String) {
        val lowerBody = body.lowercase()
        val matcher = amountPattern.matcher(lowerBody)
        
        if (matcher.find()) {
            val amountStr = matcher.group(1)?.replace(",", "") ?: return
            val amountParsed = amountStr.toDoubleOrNull() ?: return
            val amountPaise = (amountParsed * 100).toLong() // Convert to minor units (paise)
            
            // Check for keywords
            val isDebited = lowerBody.contains("debited") || lowerBody.contains("spent") || lowerBody.contains("sent to")
            val isCredited = lowerBody.contains("credited")
            
            if (isDebited || isCredited) {
                val type = if (isDebited) TransactionType.EXPENSE else TransactionType.INCOME
                
                // We use goAsync to perform database operation in BroadcastReceiver
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = ReserveDatabase.get(context)
                        val transactionDao = db.transactionDao()
                        
                        // Pick a default/first account for SMS transactions if available
                        val accounts = db.accountDao().getAll()
                        val accountId = if (accounts.isNotEmpty()) accounts[0].id else UUID.randomUUID().toString()
                        
                        val transaction = TransactionEntity(
                            id = UUID.randomUUID().toString(),
                            amount = amountPaise,
                            type = type,
                            timestamp = System.currentTimeMillis(),
                            note = "SMS: $sender",
                            categoryId = null,
                            accountId = accountId
                        )
                        transactionDao.insert(transaction)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
