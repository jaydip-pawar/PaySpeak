package com.pp.payspeak.core.extraction

import android.util.Log
import com.pp.payspeak.core.parser.CommonPaymentParser
import com.pp.payspeak.core.parser.PaymentParser
import com.pp.payspeak.core.parser.SmsAmountEngine
import com.pp.payspeak.core.parser.SmsCategory
import com.pp.payspeak.core.parser.TxnType
import com.pp.payspeak.model.PaymentApp
import com.pp.payspeak.model.PaymentEvent
import com.pp.payspeak.model.PaymentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PaymentExtractionEngine"

class PaymentExtractionEngine {
    private val commonParsers: List<PaymentParser> = listOf(CommonPaymentParser())
    private val senderRegex = Regex(
        """(?:from|by|sender|VPA|payment)\s*:?\s*([A-Za-z0-9\s@._-]{3,})""",
        RegexOption.IGNORE_CASE
    )

    suspend fun extractPayment(
        rawText: String,
        source: PaymentSource,
        packageApp: PaymentApp? = null
    ): PaymentEvent? {
        return withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "Extracting payment from source: $source, text: $rawText")

                val bestEvent = if (source == PaymentSource.SMS) {
                    extractSmsPayment(rawText, source)
                } else {
                    Log.d(TAG, "Notification/Accessibility path — running ${commonParsers.size} parser(s)")
                    val candidates = commonParsers.mapNotNull { parser ->
                        val canParse = parser.canParse(rawText)
                        Log.d(TAG, "  ${parser::class.simpleName}.canParse=$canParse")
                        if (canParse) {
                            val result = parser.parse(rawText, source)
                            Log.d(TAG, "  ${parser::class.simpleName}.parse → ${if (result != null) "amount=${result.amount}p, confidence=${result.confidenceScore}" else "null"}")
                            result
                        } else null
                    }
                    Log.d(TAG, "  candidates matched: ${candidates.size}")
                    candidates.maxByOrNull { it.confidenceScore }
                }

                if (bestEvent != null) {
                    val appOverride = when (source) {
                        PaymentSource.NOTIFICATION, PaymentSource.ACCESSIBILITY ->
                            packageApp?.takeIf { it != PaymentApp.UNKNOWN }
                        else -> null
                    }
                    return@withContext if (appOverride != null) bestEvent.copy(appName = appOverride) else bestEvent
                }

                Log.w(TAG, "No parser matched for source: $source")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting payment", e)
                null
            }
        }
    }

    private fun extractSmsPayment(text: String, source: PaymentSource): PaymentEvent? {
        val result = SmsAmountEngine.extract(text)
        Log.d(TAG, "SMS score-based: category=${result.spamResult.category}, txnType=${result.txnType.type}, confidence=${result.spamResult.confidence}")

        if (result.spamResult.category != SmsCategory.GENUINE_TRANSACTION) {
            Log.d(TAG, "SMS skipped: not genuine (category=${result.spamResult.category})")
            return null
        }
        if (result.txnType.type != TxnType.CREDIT) {
            Log.d(TAG, "SMS skipped: not a credit transaction (type=${result.txnType.type})")
            return null
        }

        val winner = result.winner ?: run {
            Log.d(TAG, "SMS skipped: no winning amount candidate")
            return null
        }

        val amountPaise = (winner.value * 100).toLong()
        if (amountPaise <= 0) return null

        val confidence = when (result.spamResult.confidence) {
            "HIGH"   -> 0.9f
            "MEDIUM" -> 0.75f
            else     -> 0.6f
        }

        val sender = senderRegex.find(text)?.groupValues?.getOrNull(1)?.trim()?.ifBlank { "Unknown" } ?: "Unknown"

        return PaymentEvent(
            amount = amountPaise,
            sender = sender,
            appName = PaymentApp.UNKNOWN,
            success = true,
            source = source,
            confidenceScore = confidence,
            rawText = text
        )
    }
}
