package com.pp.payspeak.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pp.payspeak.R
import com.pp.payspeak.core.deduplication.PaymentEventManager
import com.pp.payspeak.core.language.LanguageManager
import com.pp.payspeak.core.speech.SpeechEngine
import com.pp.payspeak.database.PaySpeakDatabase
import com.pp.payspeak.model.PaymentSource
import com.pp.payspeak.ui.MainActivity
import com.pp.payspeak.utils.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "PaymentAnnouncerService"
private const val NOTIFICATION_ID = 1
private const val CHANNEL_ID = "com.pp.payspeak.announcement"

class PaymentAnnouncerService : Service() {
    private lateinit var speechEngine: SpeechEngine
    private lateinit var languageManager: LanguageManager
    private lateinit var eventManager: PaymentEventManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var paymentBroadcastReceiver: PaymentBroadcastReceiver
    private lateinit var preferenceManager: com.pp.payspeak.utils.PreferenceManager
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val recentAnnouncements = object : LinkedHashMap<String, Long>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            val expiry = System.currentTimeMillis() - 60_000
            return (eldest?.value ?: 0L) < expiry || size > 50
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        DebugLogger.logServiceStart("PaymentAnnouncerService")

        preferenceManager = com.pp.payspeak.utils.PreferenceManager(this)
        speechEngine = SpeechEngine(this)
        languageManager = LanguageManager().apply { setLanguage(preferenceManager.getLanguage()) }
        eventManager = PaymentEventManager(PaySpeakDatabase.getInstance(this))
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()
        val notification = createServiceNotification()
        runCatching { startForeground(NOTIFICATION_ID, notification) }
            .onFailure {
                Log.e(TAG, "Failed to promote to foreground", it)
                runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
            }

        paymentBroadcastReceiver = PaymentBroadcastReceiver()
        val filter = IntentFilter("com.pp.payspeak.PAYMENT_DETECTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(paymentBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(paymentBroadcastReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        val notification = createServiceNotification()
        runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        DebugLogger.logServiceStop("PaymentAnnouncerService")
        try {
            unregisterReceiver(paymentBroadcastReceiver)
            speechEngine.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up service", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.notification_channel_description)
                enableVibration(true)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_listening))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private inner class PaymentBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != "com.pp.payspeak.PAYMENT_DETECTED") return
            try {
                val amount = intent.getLongExtra("amount", 0L)
                val sender = intent.getStringExtra("sender") ?: "Unknown"
                val appName = intent.getStringExtra("app") ?: ""
                val source = intent.getStringExtra("source") ?: PaymentSource.UNKNOWN.name
                val ts = intent.getLongExtra("timestamp", System.currentTimeMillis())
                val announcementKey = "$amount|$sender|$appName"
                synchronized(recentAnnouncements) {
                    val now = System.currentTimeMillis()
                    if (recentAnnouncements.containsKey(announcementKey)) return
                    recentAnnouncements[announcementKey] = now
                }

                Log.d(TAG, "Payment detected: $amount, $sender, $appName")

                coroutineScope.launch {
                    try {
                        val src = runCatching { PaymentSource.valueOf(source) }.getOrDefault(PaymentSource.UNKNOWN)
                        val displayApp = if (appName.equals("UNKNOWN", true)) "" else languageManager.getAppNameInCurrentLanguage(appName)
                        val announcement = if (src == PaymentSource.SMS) {
                            languageManager.formatSmsAnnouncement(amount)
                        } else {
                            languageManager.formatAnnouncement(amount, displayApp)
                        }
                        Log.d(TAG, "Announcing: $announcement")
                        DebugLogger.logLanguageSelection(languageManager.getCurrentLanguage().code, announcement)

                        if (speechEngine.isReady()) {
                            DebugLogger.logSpeechStart(announcement, languageManager.getCurrentLanguage().code)
                            speechEngine.announcePayment(announcement, languageManager.getCurrentLanguage().code) {
                                DebugLogger.logSpeechComplete()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error announcing payment", e)
                        DebugLogger.logSpeechError("Announcement failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing payment broadcast", e)
            }
        }
    }
}
