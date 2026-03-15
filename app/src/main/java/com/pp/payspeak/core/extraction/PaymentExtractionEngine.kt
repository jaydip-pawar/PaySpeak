package com.pp.payspeak.core.extraction

import android.util.Log
import com.pp.payspeak.core.parser.*
import com.pp.payspeak.model.PaymentApp
import com.pp.payspeak.model.PaymentEvent
import com.pp.payspeak.model.PaymentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PaymentExtractionEngine"

class PaymentExtractionEngine {
    private val commonParsers: List<PaymentParser> = listOf(CommonPaymentParser())

    private fun parsersFor(@Suppress("UNUSED_PARAMETER") source: PaymentSource): List<PaymentParser> = commonParsers

    suspend fun extractPayment(rawText: String, source: PaymentSource, packageApp: PaymentApp? = null): PaymentEvent? {
        return withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "Extracting payment from source: $source, text: $rawText")

                val candidateEvents = parsersFor(source).mapNotNull { parser ->
                    if (parser.canParse(rawText)) parser.parse(rawText, source) else null
                }

                val bestEvent = candidateEvents.maxByOrNull { it.confidenceScore }
                if (bestEvent != null) {
                    val appOverride = when (source) {
                        PaymentSource.NOTIFICATION, PaymentSource.ACCESSIBILITY -> packageApp?.takeIf { it != PaymentApp.UNKNOWN }
                        PaymentSource.SMS -> PaymentApp.UNKNOWN
                        else -> null
                    }
                    return@withContext if (appOverride != null) bestEvent.copy(appName = appOverride) else bestEvent
                }

                Log.w(TAG, "No regex parser matched, skipping ML fallback")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting payment", e)
                null
            }
        }
    }
}
