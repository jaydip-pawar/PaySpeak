package com.pp.payspeak.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pp.payspeak.utils.DebugLogger
import java.util.concurrent.TimeUnit

private const val TAG = "BootReceiver"
private const val WATCHDOG_WORK_NAME = "payspeak_service_watchdog"

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isHandledAction = intent.action == Intent.ACTION_BOOT_COMPLETED ||
                intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!isHandledAction) return

        try {
            Log.d(TAG, "Boot completed, starting PaymentAnnouncerService")
            DebugLogger.logServiceStart("BootReceiver")

            val serviceIntent = Intent(context, PaymentAnnouncerService::class.java)
            runCatching { context.startForegroundService(serviceIntent) }
                .onFailure {
                    Log.e(TAG, "Foreground start rejected post-boot", it)
                    DebugLogger.logError("BootReceiver", "Failed to start service", it)
                }

            scheduleWatchdog(context)

            Log.d(TAG, "PaymentAnnouncerService start requested")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service on boot", e)
            DebugLogger.logError("BootReceiver", "Failed to start service", e)
        }
    }

    private fun scheduleWatchdog(context: Context) {
        val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
            15, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WATCHDOG_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "Service watchdog scheduled")
    }
}

