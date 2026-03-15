package com.pp.payspeak.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.pp.payspeak.core.deduplication.PaymentEventManager
import com.pp.payspeak.core.extraction.PaymentExtractionEngine
import com.pp.payspeak.database.PaySpeakDatabase
import com.pp.payspeak.model.PaymentSource
import com.pp.payspeak.utils.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "AccessibilityService"

class AccessibilityServiceImpl : AccessibilityService() {
    private lateinit var extractionEngine: PaymentExtractionEngine
    private lateinit var eventManager: PaymentEventManager
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val paymentPackages = listOf(
        "com.paytm",
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "in.org.npci.upiapp",
        "com.ybl",
        "com.icicibank.imobile",
        "net.one97.paytm"
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AccessibilityService created")
        DebugLogger.logServiceStart("AccessibilityService")
        extractionEngine = PaymentExtractionEngine()
        eventManager = PaymentEventManager(PaySpeakDatabase.getInstance(this))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (paymentPackages.none { pkg.startsWith(it) }) return
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val text = extractAccessibilityEventText(event)
                    if (text.isNotEmpty()) {
                        Log.d(TAG, "Accessibility event: ${event.contentDescription} - $text")
                        DebugLogger.logAccessibilityDetected(text, event.className?.toString() ?: "Unknown")

                        coroutineScope.launch {
                            try {
                                val extractedEvent = extractionEngine.extractPayment(text, PaymentSource.ACCESSIBILITY)
                                if (extractedEvent != null) {
                                    DebugLogger.logExtractionResult(extractedEvent.amount, extractedEvent.sender, extractedEvent.appName.name, extractedEvent.success, extractedEvent.confidenceScore)
                                    val processed = eventManager.procesPaymentEvent(extractedEvent)
                                    if (processed != null) {
                                        PaymentAnnouncementBroadcaster.broadcastPaymentDetected(this@AccessibilityServiceImpl, extractedEvent)
                                    } else {
                                        DebugLogger.logDuplicateDetected(extractedEvent.amount, extractedEvent.sender)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing accessibility event", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onAccessibilityEvent", e)
        }
    }

    override fun onServiceConnected() {
        Log.d(TAG, "AccessibilityService connected")
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AccessibilityService destroyed")
        DebugLogger.logServiceStop("AccessibilityService")
    }

    private fun extractAccessibilityEventText(event: AccessibilityEvent): String {
        val textParts = mutableListOf<String>()
        event.text.forEach { text -> textParts.add(text.toString()) }
        event.contentDescription?.let { textParts.add(it.toString()) }
        return textParts.joinToString(" ")
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val textParts = mutableListOf<String>()
        node.text?.let { textParts.add(it.toString()) }
        node.contentDescription?.let { textParts.add(it.toString()) }
        for (i in 0 until (node.childCount ?: 0)) {
            node.getChild(i)?.let { child -> textParts.add(extractTextFromNode(child)) }
        }
        return textParts.filter { it.isNotEmpty() }.joinToString(" ")
    }
}
