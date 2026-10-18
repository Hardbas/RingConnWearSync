package io.github.hardbas.wearsync.bridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.hardbas.wearsync.bridge.service.RingConnForegroundService
import io.github.hardbas.wearsync.bridge.service.RingConnTelemetryReader
import io.github.hardbas.wearsync.bridge.service.WearDataSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Watchdog broadcast receiver triggered periodically by AlarmManager
 * to guarantee that RingConnForegroundService remains alive 24/7 across deep Doze mode
 * and aggressive OEM memory reclaims.
 */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "Watchdog alarm received: ensuring RingConnForegroundService is running")
        try {
            RingConnForegroundService.start(context, RingConnForegroundService.ACTION_WATCHDOG_TICK)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot start ForegroundService from Watchdog (${e.javaClass.simpleName}): falling back to goAsync direct sync", e)
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val vitals = RingConnTelemetryReader.readBestAvailableVitalsSuspend(context)
                    WearDataSyncService(context).syncVitalsToWatch(vitals)
                    RingConnForegroundService.scheduleWatchdog(context)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error in goAsync direct watchdog sync", t)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "RingConnWatchdog"
    }
}
