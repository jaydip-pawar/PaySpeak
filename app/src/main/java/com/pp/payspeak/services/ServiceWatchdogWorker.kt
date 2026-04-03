package com.pp.payspeak.services

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

private const val TAG = "ServiceWatchdogWorker"

/**
 * Periodic WorkManager task (every 15 minutes) that checks whether
 * [PaymentAnnouncerService] is still alive and restarts it if it was killed.
 *
 * This is a reliable cross-OEM fallback: even on MIUI/Samsung where the OS
 * force-stops foreground services, WorkManager uses JobScheduler which is
 * protected from the same battery-opt restrictions.
 */
class ServiceWatchdogWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return if (!PaymentAnnouncerService.isRunning) {
            Log.d(TAG, "PaymentAnnouncerService not running — restarting via watchdog")
            runCatching {
                appContext.startForegroundService(
                    Intent(appContext, PaymentAnnouncerService::class.java)
                )
            }.fold(
                onSuccess = { Result.success() },
                onFailure = {
                    Log.e(TAG, "Watchdog failed to restart service", it)
                    Result.retry()
                }
            )
        } else {
            Log.d(TAG, "PaymentAnnouncerService is running — no action needed")
            Result.success()
        }
    }
}
