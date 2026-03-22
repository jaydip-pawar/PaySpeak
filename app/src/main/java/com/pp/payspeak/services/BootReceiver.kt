package com.pp.payspeak.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pp.payspeak.utils.DebugLogger

private const val TAG = "BootReceiver"

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

            Log.d(TAG, "PaymentAnnouncerService start requested")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service on boot", e)
            DebugLogger.logError("BootReceiver", "Failed to start service", e)
        }
    }
}
