package io.github.hardbas.wearsync.bridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.hardbas.wearsync.bridge.service.RingConnForegroundService

/**
 * Watchdog broadcast receiver triggered periodically by AlarmManager
 * to guarantee that RingConnForegroundService remains alive 24/7 across deep Doze mode
 * and aggressive OEM memory reclaims.
 */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "Watchdog alarm received: ensuring RingConnForegroundService is running")
        RingConnForegroundService.start(context)
    }

    companion object {
        private const val TAG = "RingConnWatchdog"
    }
}
