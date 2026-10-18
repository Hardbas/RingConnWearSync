package io.github.hardbas.wearsync.bridge.service

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    private suspend fun sendSyncStatusToWatch(status: String) {
        try {
            val nodeClient = Wearable.getNodeClient(this)
            val messageClient = Wearable.getMessageClient(this)
            val nodes = nodeClient.connectedNodes.await()
            val data = status.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                messageClient.sendMessage(node.id, PATH_SYNC_STATUS, data).await()
            }
            Log.i(TAG, "Sent sync status '$status' to ${nodes.size} watch node(s)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending sync status '$status' to watch", e)
        }
    }

    private fun triggerRingConnSync() {
        scope.launch {
            sendSyncStatusToWatch("START")
            try {
                if (RingConnAccessibilityService.isRunning()) {
                    val scraped = RingConnAccessibilityService.instance?.wakeAndScrapeRingConnApp()
                    val vitals = scraped ?: RingConnTelemetryReader.readBestAvailableVitalsSuspend(this@PhoneMessageReceiverService)
                    WearDataSyncService(this@PhoneMessageReceiverService).syncVitalsToWatch(vitals, force = true)
                    Log.i(TAG, "Synced RingConn vitals via AccessibilityService: HR=${vitals.lastHeartRate}, SpO2=${vitals.lastSpo2}")
                    sendSyncStatusToWatch("SUCCESS")
                    return@launch
                }

                val intent = packageManager.getLaunchIntentForPackage("com.gdjztech.ringconn")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    startActivity(intent)
                    Log.i(TAG, "Dispatched activation routine to RingConn to sync BLE data")

                    delay(4000L)

                    // Collapse back to home
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)

                    // Brief delay for RingConn widget inflation
                    delay(1000L)

                    // Read best available vitals (prioritizes direct RingConn Glance widgets)
                    val vitals = RingConnTelemetryReader.readBestAvailableVitalsSuspend(this@PhoneMessageReceiverService)

                    // Force immediate sync to Watch (bypassing battery throttle)
                    WearDataSyncService(this@PhoneMessageReceiverService).syncVitalsToWatch(vitals, force = true)
                    Log.i(TAG, "Forced fresh RingConn vitals sync to Watch: HR=${vitals.lastHeartRate}, SpO2=${vitals.lastSpo2}")
                    sendSyncStatusToWatch("SUCCESS")
                } else {
                    sendSyncStatusToWatch("ERROR")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering sync on RingConn", e)
                sendSyncStatusToWatch("ERROR")
            }
        }
    }

    companion object {
        private const val TAG = "PhoneMsgReceiver"
        const val PATH_ACTION_MEASURE_NOW = "/action/measure_now"
        const val PATH_ACTION_SYNC_RINGCONN = "/action/sync_ringconn"
        const val PATH_SYNC_STATUS = "/action/sync_status"
    }
}
