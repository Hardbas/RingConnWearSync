package io.github.hardbas.wearsync.bridge.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Resilient periodic sync worker that survives Android 14+ Doze and
 * ForegroundService restrictions. Acts as a fallback when Samsung One UI
 * kills RingConnForegroundService in deep sleep.
 */
class RingConnSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "WorkManager periodic sync triggered")
        return try {
            // Poke RingConn Flutter background worker
            try {
                val bgIntent = Intent("es.antonborri.home_widget.action.BACKGROUND").apply {
                    setPackage("com.gdjztech.ringconn")
                }
                applicationContext.sendBroadcast(bgIntent)
            } catch (e: Exception) {
                Log.d(TAG, "HomeWidget background broadcast error: ${e.message}")
            }

            val vitals = RingConnTelemetryReader.readBestAvailableVitalsSuspend(applicationContext)
            val synced = WearDataSyncService(applicationContext).syncVitalsToWatch(vitals, force = false)
            Log.i(TAG, "WorkManager sync completed: synced=$synced, HR=${vitals.lastHeartRate}, Sleep=${vitals.sleepScore}")

            // Ensure ForegroundService is alive
            try {
                RingConnForegroundService.start(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "Could not restart ForegroundService from worker", e)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "RingConnSyncWorker"
        const val UNIQUE_WORK_NAME = "ringconn_periodic_sync"
    }
}
