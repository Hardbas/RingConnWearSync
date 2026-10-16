package com.ringconn.wear.app.service

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.ringconn.wear.app.complication.RingConnComplicationProvider
import com.ringconn.wear.app.data.RingConnVitals
import com.ringconn.wear.app.tile.DailySummaryTileService
import com.ringconn.wear.app.tile.HeartRateTileService
import com.ringconn.wear.app.tile.StressTileService
import com.ringconn.wear.app.tile.TemperatureTileService
import com.ringconn.wear.app.tile.VitalsTileService
import org.json.JSONObject

class WearDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == PATH_RINGCONN_VITALS) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

                val hrLast = dataMap.getInt("hr_last")
                val hrAvg = dataMap.getInt("hr_avg")
                val spo2Last = dataMap.getInt("spo2_last")
                val spo2Avg = dataMap.getInt("spo2_avg")
                val hrvLast = dataMap.getInt("hrv_last")
                val hrvAvg = dataMap.getInt("hrv_avg")
                val sleepScore = dataMap.getInt("sleep_score")
                val activityScore = dataMap.getInt("activity_score")
                val activeMins = dataMap.getInt("active_mins")
                val stressScore = dataMap.getInt("stress_score")
                val stepsLive = dataMap.getInt("steps_live")
                val stepsTarget = dataMap.getInt("steps_target")
                val calLive = dataMap.getInt("cal_live")
                val calTarget = dataMap.getInt("cal_target")
                val ringBattery = dataMap.getInt("ring_battery")
                val isCharging = dataMap.getBoolean("is_charging")
                val isConnected = dataMap.getBoolean("is_connected")
                val timestamp = dataMap.getLong("timestamp")
                val minHr = dataMap.getInt("min_hr")
                val maxHr = dataMap.getInt("max_hr")
                val zone1Mins = dataMap.getInt("zone1_mins")
                val zone2Mins = dataMap.getInt("zone2_mins")
                val zone3Mins = dataMap.getInt("zone3_mins")
                val zone4Mins = dataMap.getInt("zone4_mins")
                val zone5Mins = dataMap.getInt("zone5_mins")
                val sleepDeepMins = dataMap.getInt("sleep_deep_mins")
                val sleepRemMins = dataMap.getInt("sleep_rem_mins")
                val sleepLightMins = dataMap.getInt("sleep_light_mins")
                val sleepAwakeMins = dataMap.getInt("sleep_awake_mins")
                val skinTempOffset = dataMap.getFloat("skin_temp_offset")
                val skinTempBaseline = if (dataMap.containsKey("skin_temp_baseline")) dataMap.getFloat("skin_temp_baseline") else 36.1f

                val vitals = RingConnVitals(
                    lastHeartRate = hrLast,
                    avgHeartRate = hrAvg,
                    lastSpo2 = spo2Last,
                    avgSpo2 = spo2Avg,
                    lastHrv = hrvLast,
                    avgHrv = hrvAvg,
                    sleepScore = sleepScore,
                    activityScore = activityScore,
                    activeMinutes = activeMins,
                    stressScore = stressScore,
                    liveSteps = stepsLive,
                    targetSteps = stepsTarget,
                    liveCalorie = calLive,
                    targetCalorie = calTarget,
                    ringBattery = ringBattery,
                    isCharging = isCharging,
                    isConnected = isConnected,
                    refreshTimestamp = timestamp,
                    minHeartRate = minHr,
                    maxHeartRate = maxHr,
                    zone1Mins = zone1Mins,
                    zone2Mins = zone2Mins,
                    zone3Mins = zone3Mins,
                    zone4Mins = zone4Mins,
                    zone5Mins = zone5Mins,
                    sleepDeepMins = sleepDeepMins,
                    sleepRemMins = sleepRemMins,
                    sleepLightMins = sleepLightMins,
                    sleepAwakeMins = sleepAwakeMins,
                    skinTempOffset = skinTempOffset,
                    skinTempBaseline = skinTempBaseline
                )

                persistAndBroadcast(vitals, "DataClient")
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == PATH_RINGCONN_MESSAGE) {
            try {
                val json = JSONObject(String(messageEvent.data, Charsets.UTF_8))
                val vitals = RingConnVitals(
                    lastHeartRate = json.optInt("hr_last", 0),
                    avgHeartRate = json.optInt("hr_avg", 0),
                    lastSpo2 = json.optInt("spo2_last", 0),
                    avgSpo2 = json.optInt("spo2_avg", 0),
                    lastHrv = json.optInt("hrv_last", 0),
                    avgHrv = json.optInt("hrv_avg", 0),
                    sleepScore = json.optInt("sleep_score", 0),
                    activityScore = json.optInt("activity_score", 0),
                    activeMinutes = json.optInt("active_mins", 0),
                    stressScore = json.optInt("stress_score", 0),
                    liveSteps = json.optInt("steps_live", 0),
                    targetSteps = json.optInt("steps_target", 10000),
                    liveCalorie = json.optInt("cal_live", 0),
                    targetCalorie = json.optInt("cal_target", 500),
                    ringBattery = json.optInt("ring_battery", 0),
                    isCharging = json.optBoolean("is_charging", false),
                    isConnected = json.optBoolean("is_connected", true),
                    refreshTimestamp = json.optLong("timestamp", System.currentTimeMillis() / 1000L),
                    minHeartRate = json.optInt("min_hr", 0),
                    maxHeartRate = json.optInt("max_hr", 0),
                    zone1Mins = json.optInt("zone1_mins", 0),
                    zone2Mins = json.optInt("zone2_mins", 0),
                    zone3Mins = json.optInt("zone3_mins", 0),
                    zone4Mins = json.optInt("zone4_mins", 0),
                    zone5Mins = json.optInt("zone5_mins", 0),
                    sleepDeepMins = json.optInt("sleep_deep_mins", 0),
                    sleepRemMins = json.optInt("sleep_rem_mins", 0),
                    sleepLightMins = json.optInt("sleep_light_mins", 0),
                    sleepAwakeMins = json.optInt("sleep_awake_mins", 0),
                    skinTempOffset = json.optDouble("skin_temp_offset", 0.0).toFloat(),
                    skinTempBaseline = json.optDouble("skin_temp_baseline", 36.1).toFloat()
                )

                persistAndBroadcast(vitals, "MessageClient-RPC")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message payload", e)
            }
        }
    }

    private fun persistAndBroadcast(vitals: RingConnVitals, source: String) {
        RingConnComplicationProvider.saveAndBroadcastVitals(this, vitals)
        Log.i(TAG, "[$source] Fresh RingConn vitals persisted: HR=${vitals.lastHeartRate}, Min=${vitals.minHeartRate}, Max=${vitals.maxHeartRate}, SpO2=${vitals.lastSpo2}, Sleep=${vitals.sleepScore}, Bat=${vitals.ringBattery}%")

        // Request immediate refresh of Complications
        val complicationRequester = ComplicationDataSourceUpdateRequester.create(
            this,
            ComponentName(this, RingConnComplicationProvider::class.java)
        )
        complicationRequester.requestUpdateAll()

        // Request immediate refresh of Tiles
        TileService.getUpdater(this).requestUpdate(VitalsTileService::class.java)
        TileService.getUpdater(this).requestUpdate(DailySummaryTileService::class.java)
        TileService.getUpdater(this).requestUpdate(HeartRateTileService::class.java)
        TileService.getUpdater(this).requestUpdate(StressTileService::class.java)
        TileService.getUpdater(this).requestUpdate(TemperatureTileService::class.java)
    }

    companion object {
        private const val TAG = "WearDataListener"
        const val PATH_RINGCONN_VITALS = "/ringconn/vitals"
        const val PATH_RINGCONN_MESSAGE = "/ringconn/vitals_message"
    }
}
