package com.pp.payspeak.core.parser

import android.util.Log
import com.pp.payspeak.model.PaymentApp
import com.pp.payspeak.model.PaymentEvent
import com.pp.payspeak.model.PaymentSource

private const val TAG = "MLKitExtractor"

/**
 * MLKit-based payment text extractor for fallback when regex parsers fail.
 * This is a simplified implementation that uses pattern matching.
 * In a production app, you would integrate actual ML Kit Text Recognition.
 */
class MLKitExtractor {

    // Amount patterns supporting various currency formats
    private val amountPatterns = listOf(
        Regex("""(?:Rs\.?|₹|INR)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:Rs\.?|₹|rupees?|INR)""", RegexOption.IGNORE_CASE),
        Regex("""amount\s*:?\s*(?:Rs\.?|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    // Sender patterns
    private val senderPatterns = listOf(
        Regex("""(?:from|by|sender|VPA|UPI ID)\s*:?\s*([A-Za-z0-9\s@._-]+?)(?:\s+(?:on|at|via|to|$))""", RegexOption.IGNORE_CASE),
        Regex("""([a-zA-Z0-9._-]+@[a-zA-Z0-9._-]+)"""), // UPI ID format
        Regex("""(?:received|credited)\s+from\s+([A-Za-z0-9\s]+)""", RegexOption.IGNORE_CASE)
    )

    suspend fun extractPaymentWithMLKit(text: String, source: PaymentSource): PaymentEvent? {
        return try {
            Log.d(TAG, "Attempting ML Kit extraction on: ${text.take(100)}")

            // Try to extract amount
            var amount: Long? = null
            for (pattern in amountPatterns) {
                val match = pattern.find(text)
                if (match != null) {
                    val amountStr = match.groupValues[1].replace(",", "")
                    val amountDouble = amountStr.toDoubleOrNull()
                    if (amountDouble != null && amountDouble > 0) {
                        amount = (amountDouble * 100).toLong()
                        break
                    }
                }
            }

            if (amount == null) {
                Log.d(TAG, "ML Kit: No amount found")
                return null
            }

            // Try to extract sender
            var sender = "Unknown"
            for (pattern in senderPatterns) {
                val match = pattern.find(text)
                if (match != null) {
                    sender = match.groupValues[1].trim()
                    if (sender.isNotEmpty() && sender.length > 2) {
                        break
                    }
                }
            }

            // Determine success/credit status
            val isSuccess = text.contains(Regex("received|credited|success|deposit", RegexOption.IGNORE_CASE))

            // Determine app
            val appName = detectApp(text)

            val confidence = calculateConfidence(text, amount, sender)

            Log.d(TAG, "ML Kit extraction: amount=$amount, sender=$sender, app=$appName, confidence=$confidence")

            PaymentEvent(
                amount = amount,
                sender = sender,
                appName = appName,
                success = isSuccess,
                source = source,
                confidenceScore = confidence,
                rawText = text,
                usedMLKit = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in ML Kit extraction", e)
            null
        }
    }

    private fun detectApp(text: String): PaymentApp {
        return when {
            text.contains("paytm", ignoreCase = true) -> PaymentApp.PAYTM
            text.contains("google pay", ignoreCase = true) || text.contains("gpay", ignoreCase = true) -> PaymentApp.GOOGLE_PAY
            text.contains("phonepe", ignoreCase = true) -> PaymentApp.PHONEPE
            text.contains("bhim", ignoreCase = true) -> PaymentApp.BHIM
            text.contains("bank", ignoreCase = true) || text.contains("neft", ignoreCase = true) || text.contains("imps", ignoreCase = true) -> PaymentApp.BANK_SMS
            else -> PaymentApp.UNKNOWN
        }
    }

    private fun calculateConfidence(text: String, amount: Long, sender: String): Float {
        var confidence = 0.5f

        // Higher confidence if we have a valid amount
        if (amount > 0) confidence += 0.15f

        // Higher confidence if sender looks valid
        if (sender != "Unknown" && sender.length > 3) confidence += 0.1f

        // Higher confidence if text contains payment keywords
        val paymentKeywords = listOf("received", "credited", "paid", "transferred", "upi", "rupee")
        val keywordCount = paymentKeywords.count { text.contains(it, ignoreCase = true) }
        confidence += (keywordCount * 0.05f).coerceAtMost(0.2f)

        return confidence.coerceAtMost(0.8f)
    }
}

