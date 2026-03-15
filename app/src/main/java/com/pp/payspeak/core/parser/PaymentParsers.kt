package com.pp.payspeak.core.parser

import android.util.Log
import com.pp.payspeak.model.PaymentApp
import com.pp.payspeak.model.PaymentEvent
import com.pp.payspeak.model.PaymentSource

private const val TAG = "PaymentParser"

interface PaymentParser {
    fun canParse(text: String): Boolean
    fun parse(text: String, source: PaymentSource): PaymentEvent?
}

class CommonPaymentParser : PaymentParser {
    private val amountRegex = Regex("""(?:Rs\.?|₹|INR)\s*([0-9][0-9,]*(?:[\u00A0\s]*\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val creditRegex = Regex(
        """
        (credited|received|deposited|got|added|\bpaid\s+to\s+you\b|\bpaid\s+you\b|paid\s+.*\s+to\s+you|paid\s+.*\s+you|sent\s+you|sent\s+to\s+you|sent\s+.*\s+to\s+you|paid\s+.*\bto\b)
        """.trimIndent(), RegexOption.IGNORE_CASE
    )
    private val debitRegex = Regex("""(debited|deducted|spent|withdrawn)""", RegexOption.IGNORE_CASE)
    private val senderRegex = Regex("""(?:from|by|sender|VPA|payment)\s*:?\s*([A-Za-z0-9\s@._-]{3,})""", RegexOption.IGNORE_CASE)

    override fun canParse(text: String): Boolean = amountRegex.containsMatchIn(text)

    override fun parse(text: String, source: PaymentSource): PaymentEvent? {
        return try {
            val amountMatch = amountRegex.find(text) ?: return null
            val amountStr = amountMatch.groupValues[1].replace(",", "")
            val amountPaise = (amountStr.toDoubleOrNull() ?: return null) * 100
            val isCredit = creditRegex.containsMatchIn(text)
            val isDebit = debitRegex.containsMatchIn(text)
            if (!isCredit || isDebit) return null

            val sender = senderRegex.find(text)?.groupValues?.getOrNull(1)?.trim()?.ifBlank { "Unknown" } ?: "Unknown"

            PaymentEvent(
                amount = amountPaise.toLong(),
                sender = sender,
                appName = PaymentApp.UNKNOWN,
                success = true,
                source = source,
                confidenceScore = 0.8f,
                rawText = text
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing payment", e)
            null
        }
    }
}
