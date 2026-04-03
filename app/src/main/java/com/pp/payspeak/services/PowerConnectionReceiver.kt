package com.pp.payspeak.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "PowerConnectionReceiver"

/**
 * Manifest-registered receiver for [Intent.ACTION_POWER_CONNECTED].
 *
 * ACTION_POWER_CONNECTED is on the list of implicit broadcasts explicitly exempt from
 * Android 8+ background restrictions (see android.intent.action.ACTION_POWER_CONNECTED),
 * so this CAN be declared in the manifest and will wake the app even when it is killed.
 *
 * When power is connected the system must be unlocked / active, making it a reliable
 * low-cost opportunity to restart the PaymentAnnouncerService if it was killed.
 */
class PowerConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED) return
        Log.d(TAG, "Power connected — ensuring PaymentAnnouncerService is running")
        if (!PaymentAnnouncerService.isRunning) {
            runCatching {
                context.startForegroundService(
                    Intent(context, PaymentAnnouncerService::class.java)
                )
            }.onFailure {
                Log.e(TAG, "Failed to start service on power connect", it)
            }
        }
    }
}
