package com.ringconn.wear.bridge.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.ringconn.wear.bridge.data.RingConnVitals
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class WearDataSyncService(private val context: Context) {
    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    suspend fun syncVitalsToWatch(vitals: RingConnVitals, force: Boolean = false): Boolean {
        val signature = "${vitals.lastHeartRate}_${vitals.avgHeartRate}_${vitals.lastSpo2}_${vitals.lastHrv}_" +
            "${vitals.sleepScore}_${vitals.activityScore}_${vitals.stressScore}_${vitals.liveSteps}_" +
            "${vitals.liveCalorie}_${vitals.ringBattery}_${vitals.isCharging}_" +
            "${vitals.minHeartRate}_${vitals.maxHeartRate}_${vitals.zone1Mins}_${vitals.zone2Mins}_${vitals.zone3Mins}_${vitals.zone4Mins}_${vitals.zone5Mins}_" +
            "${vitals.sleepDeepMins}_${vitals.sleepRemMins}_${vitals.sleepLightMins}_${vitals.sleepAwakeMins}_" +
            "${vitals.skinTempOffset}_${vitals.skinTempBaseline}"
        val now = System.currentTimeMillis()

        if (!force && signature == lastSyncedSignature && (now - lastSyncedTimeMs) < MIN_HEARTBEAT_INTERVAL_MS) {
            Log.d(TAG, "Metrics unchanged ($signature); skipping Bluetooth wakeup to preserve Watch battery")
            return true
        }

        var dataOk = false
        var messageOk = false

        // 1. Wearable Data Layer (DataClient - Persistent storage sync, without changing timestamp if signature matches)
        try {
            val request = PutDataMapRequest.create(PATH_RINGCONN_VITALS).apply {
                dataMap.putInt(KEY_HR_LAST, vitals.lastHeartRate)
                dataMap.putInt(KEY_HR_AVG, vitals.avgHeartRate)
                dataMap.putInt(KEY_SPO2_LAST, vitals.lastSpo2)
                dataMap.putInt(KEY_SPO2_AVG, vitals.avgSpo2)
                dataMap.putInt(KEY_HRV_LAST, vitals.lastHrv)
                dataMap.putInt(KEY_HRV_AVG, vitals.avgHrv)
                dataMap.putInt(KEY_SLEEP_SCORE, vitals.sleepScore)
                dataMap.putInt(KEY_ACTIVITY_SCORE, vitals.activityScore)
                dataMap.putInt(KEY_STRESS_SCORE, vitals.stressScore)
                dataMap.putInt(KEY_STEPS_LIVE, vitals.liveSteps)
                dataMap.putInt(KEY_STEPS_TARGET, vitals.targetSteps)
                dataMap.putInt(KEY_CAL_LIVE, vitals.liveCalorie)
                dataMap.putInt(KEY_CAL_TARGET, vitals.targetCalorie)
                dataMap.putInt(KEY_RING_BATTERY, vitals.ringBattery)
                dataMap.putBoolean(KEY_IS_CHARGING, vitals.isCharging)
                dataMap.putBoolean(KEY_IS_CONNECTED, vitals.isConnected)
                dataMap.putLong(KEY_TIMESTAMP, vitals.refreshTimestamp)
                dataMap.putLong(KEY_SYNC_TIME, now)
                dataMap.putInt(KEY_MIN_HR, vitals.minHeartRate)
                dataMap.putInt(KEY_MAX_HR, vitals.maxHeartRate)
                dataMap.putInt(KEY_ZONE1_MINS, vitals.zone1Mins)
                dataMap.putInt(KEY_ZONE2_MINS, vitals.zone2Mins)
                dataMap.putInt(KEY_ZONE3_MINS, vitals.zone3Mins)
                dataMap.putInt(KEY_ZONE4_MINS, vitals.zone4Mins)
                dataMap.putInt(KEY_ZONE5_MINS, vitals.zone5Mins)
                dataMap.putInt(KEY_SLEEP_DEEP_MINS, vitals.sleepDeepMins)
                dataMap.putInt(KEY_SLEEP_REM_MINS, vitals.sleepRemMins)
                dataMap.putInt(KEY_SLEEP_LIGHT_MINS, vitals.sleepLightMins)
                dataMap.putInt(KEY_SLEEP_AWAKE_MINS, vitals.sleepAwakeMins)
                dataMap.putFloat(KEY_SKIN_TEMP_OFFSET, vitals.skinTempOffset)
                dataMap.putFloat(KEY_SKIN_TEMP_BASELINE, vitals.skinTempBaseline)
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(request).await()
            dataOk = true
        } catch (e: Exception) {
            Log.e(TAG, "DataClient putDataItem failed", e)
        }

        // 2. Instant Bluetooth RPC (MessageClient - Zero Latency, bypasses all caching)
        try {
            val jsonBytes = JSONObject().apply {
                put(KEY_HR_LAST, vitals.lastHeartRate)
                put(KEY_HR_AVG, vitals.avgHeartRate)
                put(KEY_SPO2_LAST, vitals.lastSpo2)
                put(KEY_SPO2_AVG, vitals.avgSpo2)
                put(KEY_HRV_LAST, vitals.lastHrv)
                put(KEY_HRV_AVG, vitals.avgHrv)
                put(KEY_SLEEP_SCORE, vitals.sleepScore)
                put(KEY_ACTIVITY_SCORE, vitals.activityScore)
                put(KEY_STRESS_SCORE, vitals.stressScore)
                put(KEY_STEPS_LIVE, vitals.liveSteps)
                put(KEY_STEPS_TARGET, vitals.targetSteps)
                put(KEY_CAL_LIVE, vitals.liveCalorie)
                put(KEY_CAL_TARGET, vitals.targetCalorie)
                put(KEY_RING_BATTERY, vitals.ringBattery)
                put(KEY_IS_CHARGING, vitals.isCharging)
                put(KEY_IS_CONNECTED, vitals.isConnected)
                put(KEY_TIMESTAMP, vitals.refreshTimestamp)
                put(KEY_MIN_HR, vitals.minHeartRate)
                put(KEY_MAX_HR, vitals.maxHeartRate)
                put(KEY_ZONE1_MINS, vitals.zone1Mins)
                put(KEY_ZONE2_MINS, vitals.zone2Mins)
                put(KEY_ZONE3_MINS, vitals.zone3Mins)
                put(KEY_ZONE4_MINS, vitals.zone4Mins)
                put(KEY_ZONE5_MINS, vitals.zone5Mins)
                put(KEY_SLEEP_DEEP_MINS, vitals.sleepDeepMins)
                put(KEY_SLEEP_REM_MINS, vitals.sleepRemMins)
                put(KEY_SLEEP_LIGHT_MINS, vitals.sleepLightMins)
                put(KEY_SLEEP_AWAKE_MINS, vitals.sleepAwakeMins)
                put(KEY_SKIN_TEMP_OFFSET, vitals.skinTempOffset.toDouble())
                put(KEY_SKIN_TEMP_BASELINE, vitals.skinTempBaseline.toDouble())
            }.toString().toByteArray(Charsets.UTF_8)

            val nodes = nodeClient.connectedNodes.await()
            for (node in nodes) {
                messageClient.sendMessage(node.id, PATH_RINGCONN_MESSAGE, jsonBytes).await()
                messageOk = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "MessageClient sendMessage failed", e)
        }

        val success = dataOk || messageOk
        if (success) {
            lastSyncedSignature = signature
            lastSyncedTimeMs = now
            Log.i(TAG, "Successfully synced RingConn vitals to Watch (DataClient=$dataOk, MessageRPC=$messageOk)")
        }
        return success
    }

    companion object {
        private const val TAG = "WearDataSyncService"
        private const val MIN_HEARTBEAT_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes if data unchanged
        @Volatile private var lastSyncedSignature: String = ""
        @Volatile private var lastSyncedTimeMs: Long = 0L

        const val PATH_RINGCONN_VITALS = "/ringconn/vitals"
        const val PATH_RINGCONN_MESSAGE = "/ringconn/vitals_message"

        const val KEY_HR_LAST = "hr_last"
        const val KEY_HR_AVG = "hr_avg"
        const val KEY_SPO2_LAST = "spo2_last"
        const val KEY_SPO2_AVG = "spo2_avg"
        const val KEY_HRV_LAST = "hrv_last"
        const val KEY_HRV_AVG = "hrv_avg"
        const val KEY_SLEEP_SCORE = "sleep_score"
        const val KEY_ACTIVITY_SCORE = "activity_score"
        const val KEY_STRESS_SCORE = "stress_score"
        const val KEY_STEPS_LIVE = "steps_live"
        const val KEY_STEPS_TARGET = "steps_target"
        const val KEY_CAL_LIVE = "cal_live"
        const val KEY_CAL_TARGET = "cal_target"
        const val KEY_RING_BATTERY = "ring_battery"
        const val KEY_IS_CHARGING = "is_charging"
        const val KEY_IS_CONNECTED = "is_connected"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_SYNC_TIME = "sync_time"
        const val KEY_MIN_HR = "min_hr"
        const val KEY_MAX_HR = "max_hr"
        const val KEY_ZONE1_MINS = "zone1_mins"
        const val KEY_ZONE2_MINS = "zone2_mins"
        const val KEY_ZONE3_MINS = "zone3_mins"
        const val KEY_ZONE4_MINS = "zone4_mins"
        const val KEY_ZONE5_MINS = "zone5_mins"
        const val KEY_SLEEP_DEEP_MINS = "sleep_deep_mins"
        const val KEY_SLEEP_REM_MINS = "sleep_rem_mins"
        const val KEY_SLEEP_LIGHT_MINS = "sleep_light_mins"
        const val KEY_SLEEP_AWAKE_MINS = "sleep_awake_mins"
        const val KEY_SKIN_TEMP_OFFSET = "skin_temp_offset"
        const val KEY_SKIN_TEMP_BASELINE = "skin_temp_baseline"
    }
}
