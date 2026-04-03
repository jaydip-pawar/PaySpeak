package com.pp.payspeak.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.pp.payspeak.core.deduplication.PaymentEventManager
import com.pp.payspeak.core.extraction.PaymentExtractionEngine
import com.pp.payspeak.database.PaySpeakDatabase
import com.pp.payspeak.model.PaymentApp
import com.pp.payspeak.model.PaymentSource
import com.pp.payspeak.utils.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "SMSBroadcastReceiver"

class SMSBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val extractionEngine = PaymentExtractionEngine()
                val eventManager = PaymentEventManager(PaySpeakDatabase.getInstance(context))
                val messages = extractSmsMessages(intent)

                for (smsMessage in messages) {
                    val sender = smsMessage.originatingAddress ?: "Unknown"
                    val messageBody = smsMessage.messageBody

                    Log.d(TAG, "SMS received from $sender: $messageBody")
                    DebugLogger.logSmsDetected(sender, messageBody)

                    if (!isBankSMS(sender, messageBody)) continue

                    try {
                        val event = extractionEngine.extractPayment(messageBody, PaymentSource.SMS)
                        if (event != null) {
                            DebugLogger.logExtractionResult(event.amount, event.sender, event.appName.name, event.success, event.confidenceScore)
                            val processed = eventManager.procesPaymentEvent(event)
                            if (processed != null) {
                                val smsEvent = processed.copy(appName = PaymentApp.UNKNOWN)
                                if (PaymentAnnouncerService.isRunning) {
                                    // Service is alive — use the normal broadcast path
                                    PaymentAnnouncementBroadcaster.broadcastPaymentDetected(context, smsEvent)
                                } else {
                                    // Service was killed — start it and pass the payment as extras
                                    // so onStartCommand() can re-broadcast once the receiver is registered
                                    val serviceIntent = Intent(context, PaymentAnnouncerService::class.java).apply {
                                        putExtra("amount", smsEvent.amount)
                                        putExtra("sender", smsEvent.sender)
                                        putExtra("app", smsEvent.appName.name)
                                        putExtra("success", smsEvent.success)
                                        putExtra("source", smsEvent.source.name)
                                        putExtra("confidence", smsEvent.confidenceScore)
                                        putExtra("timestamp", smsEvent.timestamp)
                                    }
                                    runCatching { context.startForegroundService(serviceIntent) }
                                        .onFailure { Log.e(TAG, "Failed to start service for SMS payment", it) }
                                }
                            } else {
                                DebugLogger.logDuplicateDetected(event.amount, event.sender)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing SMS", e)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractSmsMessages(intent: Intent?): List<SmsMessage> {
        intent ?: return emptyList()
        val pdus = intent.extras?.get("pdus") as? Array<*> ?: return emptyList()
        val format = intent.extras?.getString("format") ?: ""
        return pdus.mapNotNull { pdu ->
            if (pdu is ByteArray) SmsMessage.createFromPdu(pdu, format) else null
        }
    }

    private fun isBankSMS(sender: String, text: String): Boolean {
        val paymentKeywords = listOf("rupee", "rupya", "rupe", "₹", "Rs", "credited", "received", "transferred", "paid", "deducted", "amount", "UPI", "NEFT", "IMPS")
        val isBankSender = sender.contains(Regex("bank|BANK|pay|PAY|sbi|hdfc|icici|axis", RegexOption.IGNORE_CASE))
        val hasPaymentKeyword = paymentKeywords.any { keyword -> text.contains(keyword, ignoreCase = true) }
        return isBankSender || hasPaymentKeyword
    }
}
