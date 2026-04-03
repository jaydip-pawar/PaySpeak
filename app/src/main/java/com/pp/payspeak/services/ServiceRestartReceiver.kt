package com.pp.payspeak.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "ServiceRestartReceiver"

/**
 * Manifest-registered receiver that is triggered by the AlarmManager restart alarm
 * scheduled in [PaymentAnnouncerService.onTaskRemoved].
 *
 * When the user clears all recent apps on OEM devices (MIUI, Samsung, etc.), the system
 * may kill our foreground service before it can restart itself. The alarm fires 1 second
 * after task removal and starts the service again so payment detection resumes quickly.
 */
class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Restart alarm fired — starting PaymentAnnouncerService")
        if (!PaymentAnnouncerService.isRunning) {
            runCatching {
                context.startForegroundService(
                    Intent(context, PaymentAnnouncerService::class.java)
                )
            }.onFailure {
                Log.e(TAG, "Failed to start service from restart alarm", it)
            }
        } else {
            Log.d(TAG, "Service already running — no restart needed")
        }
    }
}
