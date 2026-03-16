package com.pp.payspeak.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.pp.payspeak.core.deduplication.PaymentEventManager
import com.pp.payspeak.core.extraction.PaymentExtractionEngine
import com.pp.payspeak.database.PaySpeakDatabase
import com.pp.payspeak.model.PaymentSource
import com.pp.payspeak.utils.DebugLogger
import com.pp.payspeak.core.parser.PackageAppMapper
import com.pp.payspeak.model.PaymentApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "NotificationListener"

class NotificationListenerServiceImpl : NotificationListenerService() {
    private lateinit var extractionEngine: PaymentExtractionEngine
    private lateinit var eventManager: PaymentEventManager
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationListenerService created")
        DebugLogger.logServiceStart("NotificationListener")
        extractionEngine = PaymentExtractionEngine()
        eventManager = PaymentEventManager(PaySpeakDatabase.getInstance(this))
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "✓ NotificationListenerService CONNECTED — system has bound the service, notifications will be received")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "✗ NotificationListenerService DISCONNECTED — check if Notification Access is still granted in Settings")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName
            val appHint = PackageAppMapper.mapPackage(packageName)
            if (appHint == PaymentApp.UNKNOWN) {
                Log.d(TAG, "SKIP package not in supported list: $packageName")
                return
            }

            val notification = sbn.notification
            val text = extractNotificationText(notification)
            Log.d(TAG, "── Notification received ──────────────────────────")
            Log.d(TAG, "  package  : $packageName")
            Log.d(TAG, "  appHint  : $appHint")
            Log.d(TAG, "  text     : $text")

            if (text.isEmpty()) {
                Log.w(TAG, "SKIP empty notification text from $packageName")
                return
            }

            if (appHint == PaymentApp.WHATSAPP) {
                val startsWithPayment = text.trimStart().startsWith("payment", ignoreCase = true)
                val hasCurrency = text.contains(Regex("\u20b9|rs|rupee", RegexOption.IGNORE_CASE))
                val isCreditLike = text.contains(Regex("credited|received|got|added|to you|to u", RegexOption.IGNORE_CASE))
                Log.d(TAG, "  WhatsApp filter → startsWithPayment=$startsWithPayment, hasCurrency=$hasCurrency, isCreditLike=$isCreditLike")
                if (!(startsWithPayment && hasCurrency && isCreditLike)) {
                    Log.w(TAG, "SKIP WhatsApp notification did not pass payment filter")
                    return
                }
            }

            DebugLogger.logNotificationDetected(text, "NotificationListener:$packageName")

            if (!isPaymentNotification(packageName, text)) {
                Log.w(TAG, "SKIP isPaymentNotification() returned false for $packageName")
                return
            }

            Log.d(TAG, "  → Launching extraction coroutine")
            coroutineScope.launch {
                try {
                    val event = extractionEngine.extractPayment(text, PaymentSource.NOTIFICATION, appHint)
                    if (event != null) {
                        val adjusted = event.copy(appName = appHint)
                        Log.d(TAG, "  extraction OK → amount=${adjusted.amount}, sender=${adjusted.sender}, app=${adjusted.appName}, confidence=${adjusted.confidenceScore}")
                        DebugLogger.logExtractionResult(adjusted.amount, adjusted.sender, adjusted.appName.name, adjusted.success, adjusted.confidenceScore)
                        val processed = eventManager.procesPaymentEvent(adjusted)
                        if (processed != null) {
                            Log.d(TAG, "  dedup OK → broadcasting payment")
                            PaymentAnnouncementBroadcaster.broadcastPaymentDetected(this@NotificationListenerServiceImpl, processed)
                        } else {
                            Log.w(TAG, "  SKIP dedup suppressed amount=${adjusted.amount}, sender=${adjusted.sender}")
                            DebugLogger.logDuplicateDetected(adjusted.amount, adjusted.sender)
                        }
                    } else {
                        Log.w(TAG, "  SKIP extraction returned null for text: $text")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing notification", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onNotificationPosted", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    private fun extractNotificationText(notification: android.app.Notification): String {
        val textParts = mutableListOf<String>()
        notification.extras.apply {
            getCharSequence("android.title")?.let { textParts.add(it.toString()) }
            getCharSequence("android.text")?.let { textParts.add(it.toString()) }
            getCharSequence("android.subText")?.let { textParts.add(it.toString()) }
        }
        return textParts.joinToString(" ")
    }

    private fun isPaymentNotification(packageName: String, text: String): Boolean {
        return PackageAppMapper.isSupported(packageName)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "NotificationListenerService destroyed")
        DebugLogger.logServiceStop("NotificationListener")
    }
}

object PaymentAnnouncementBroadcaster {
    fun broadcastPaymentDetected(context: android.content.Context, event: com.pp.payspeak.model.PaymentEvent) {
        val intent = android.content.Intent().apply {
            action = "com.pp.payspeak.PAYMENT_DETECTED"
            putExtra("amount", event.amount)
            putExtra("sender", event.sender)
            putExtra("app", event.appName.name)
            putExtra("success", event.success)
            putExtra("source", event.source.name)
            putExtra("confidence", event.confidenceScore)
            putExtra("timestamp", event.timestamp)
        }
        context.sendBroadcast(intent)
    }
}
