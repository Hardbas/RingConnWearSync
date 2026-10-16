package com.ringconn.wear.bridge.service

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhoneMessageReceiverService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PATH_ACTION_MEASURE_NOW, PATH_ACTION_SYNC_RINGCONN -> {
                Log.i(TAG, "Received trigger from Galaxy Watch: ${messageEvent.path}")
                triggerRingConnSync()
            }
        }
    }

    private fun triggerRingConnSync() {
        scope.launch {
            try {
                val intent = packageManager.getLaunchIntentForPackage("com.gdjztech.ringconn")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    startActivity(intent)
                    Log.i(TAG, "Dispatched activation routine to RingConn to sync BLE data")

                    // Wait 4 seconds for RingConn to connect to BLE ring and sync metrics
                    kotlinx.coroutines.delay(4000L)

                    // Collapse back to home
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)

                    // Immediately trigger fresh sync to Galaxy Watch
                    val serviceIntent = Intent(this@PhoneMessageReceiverService, RingConnForegroundService::class.java).apply {
                        action = RingConnForegroundService.ACTION_SYNC_NOW
                    }
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering sync on RingConn", e)
            }
        }
    }

    companion object {
        private const val TAG = "PhoneMsgReceiver"
        const val PATH_ACTION_MEASURE_NOW = "/action/measure_now"
        const val PATH_ACTION_SYNC_RINGCONN = "/action/sync_ringconn"
    }
}
