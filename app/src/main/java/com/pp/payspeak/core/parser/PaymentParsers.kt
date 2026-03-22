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
        (credited|received|deposited|got|added|\bpaid\s+to\s+you\b|\bpaid\s+you\b|paid\s+.*\s+to\s+you|paid\s+.*\s+you|sent\s+you|sent\s+to\s+you|sent\s+.*\s+to\s+you|paid\s+.*\bto\b|\bfrom\s+[A-Za-z\u0900-\u097F])
        """.trimIndent(), RegexOption.IGNORE_CASE
    )
    private val debitRegex = Regex("""(debited|deducted|spent|withdrawn)""", RegexOption.IGNORE_CASE)
    private val senderRegex = Regex("""(?:from|by|sender|VPA|payment)\s*:?\s*([A-Za-z0-9\s@._-]{3,})""", RegexOption.IGNORE_CASE)

    override fun canParse(text: String): Boolean {
        val result = amountRegex.containsMatchIn(text)
        if (!result) Log.w(TAG, "canParse=false — no Rs/₹/INR amount found in: $text")
        return result
    }

    override fun parse(text: String, source: PaymentSource): PaymentEvent? {
        return try {
            val amountMatch = amountRegex.find(text)
            if (amountMatch == null) {
                Log.w(TAG, "parse FAIL — amountRegex no match | text: $text")
                return null
            }
            val amountStr = amountMatch.groupValues[1].replace(",", "")
            val amountDouble = amountStr.toDoubleOrNull()
            if (amountDouble == null) {
                Log.w(TAG, "parse FAIL — amount not parseable: '$amountStr' | text: $text")
                return null
            }
            // BUG-05: Math.round avoids float truncation (99.99*100 = 9998.999 → toLong = 9998 wrong; round = 9999 correct)
            val amountPaise = Math.round(amountDouble * 100)
            val isCredit = creditRegex.containsMatchIn(text)
            val isDebit  = debitRegex.containsMatchIn(text)
            Log.d(TAG, "parse → amount=${amountPaise}p, isCredit=$isCredit, isDebit=$isDebit | text: $text")
            if (!isCredit) {
                Log.w(TAG, "parse FAIL — no credit signal found")
                return null
            }
            if (isDebit) {
                Log.w(TAG, "parse FAIL — debit signal overrides credit")
                return null
            }

            val sender = senderRegex.find(text)?.groupValues?.getOrNull(1)?.trim()?.ifBlank { "Unknown" } ?: "Unknown"
            Log.d(TAG, "parse OK → amount=${amountPaise}p, sender=$sender, source=$source")

            PaymentEvent(
                amount = amountPaise,
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
