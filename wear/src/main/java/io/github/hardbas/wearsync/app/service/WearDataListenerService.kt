package io.github.hardbas.wearsync.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider
import io.github.hardbas.wearsync.app.data.RingConnVitals
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

class WearDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            val path = event.dataItem.uri.path
            if (event.type == DataEvent.TYPE_CHANGED && (path == PATH_RINGCONN_VITALS || path == PATH_RINGCONN_VITALS_URGENT)) {
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
                val skinTempBaseline = if (dataMap.containsKey("skin_temp_baseline")) dataMap.getFloat("skin_temp_baseline") else 0.0f

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
        when (messageEvent.path) {
            PATH_ACTION_SYNC_STATUS -> {
                val status = String(messageEvent.data, Charsets.UTF_8).trim()
                syncStatusFlow.value = status
                if (status == "SUCCESS" || status == "ERROR") {
                    lastSyncCompletedMs.value = System.currentTimeMillis()
                }
                Log.i(TAG, "Sync status update received: $status")
            }
            PATH_RINGCONN_MESSAGE, PATH_RINGCONN_VITALS, PATH_RINGCONN_VITALS_URGENT -> {
                try {
                    lastSyncCompletedMs.value = System.currentTimeMillis()
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
                        skinTempBaseline = json.optDouble("skin_temp_baseline", 0.0).toFloat()
                    )

                    persistAndBroadcast(vitals, "MessageClient-RPC")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message payload", e)
                }
            }
        }
    }

    private fun persistAndBroadcast(vitals: RingConnVitals, source: String) {
        RingConnComplicationProvider.saveAndBroadcastVitals(this, vitals)
        lastSyncCompletedMs.value = System.currentTimeMillis()
        Log.i(TAG, "[$source] Fresh RingConn vitals persisted: HR=${vitals.lastHeartRate}, Min=${vitals.minHeartRate}, Max=${vitals.maxHeartRate}, SpO2=${vitals.lastSpo2}, Sleep=${vitals.sleepScore}, Bat=${vitals.ringBattery}%")

        // Check for low ring battery (<20%) and trigger wrist haptic alert
        checkLowBatteryHapticAlert(vitals)
    }

    private fun checkLowBatteryHapticAlert(vitals: RingConnVitals) {
        val battery = vitals.ringBattery
        if (battery in 1..19 && !vitals.isCharging) {
            val prefs = getSharedPreferences(PREFS_ALERTS, Context.MODE_PRIVATE)
            val lastAlertedBattery = prefs.getInt(KEY_LAST_LOW_BAT, -1)
            val lastAlertTime = prefs.getLong(KEY_LAST_LOW_BAT_TIME, 0L)
            val now = System.currentTimeMillis()

            if (lastAlertedBattery == -1 || battery < lastAlertedBattery || (now - lastAlertTime > 4 * 3600_000L)) {
                prefs.edit()
                    .putInt(KEY_LAST_LOW_BAT, battery)
                    .putLong(KEY_LAST_LOW_BAT_TIME, now)
                    .apply()

                triggerHapticNotification(battery)
            }
        } else if (battery >= 20 || vitals.isCharging) {
            getSharedPreferences(PREFS_ALERTS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_LOW_BAT)
                .apply()
        }
    }

    private fun triggerHapticNotification(battery: Int) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 150, 300),
                    intArrayOf(0, 200, 0, 255),
                    -1
                )
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 200, 150, 300), -1)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ALERTS_ID,
                    "RingConn Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alertas de batería y telemetría de RingConn"
                    enableVibration(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            val pendingIntent = PendingIntent.getActivity(
                this, 101, launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
                .setContentTitle(getString(R.string.low_battery_alert_title))
                .setContentText(getString(R.string.low_battery_alert_desc, battery))
                .setSmallIcon(R.drawable.ic_ringconn_ring)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(NOTIFICATION_LOW_BAT_ID, notification)
            Log.i(TAG, "Low battery haptic alert triggered: ring battery at $battery%")
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering haptic alert", e)
        }
    }

    companion object {
        private const val TAG = "WearDataListener"
        const val PATH_RINGCONN_VITALS = "/ringconn/vitals"
        const val PATH_RINGCONN_VITALS_URGENT = "/ringconn/vitals_urgent"
        const val PATH_RINGCONN_MESSAGE = "/ringconn/vitals_message"
        const val PATH_ACTION_SYNC_STATUS = "/action/sync_status"
        const val PREFS_ALERTS = "ring_alerts"
        const val KEY_LAST_LOW_BAT = "last_low_bat_alert"
        const val KEY_LAST_LOW_BAT_TIME = "last_low_bat_time"
        const val CHANNEL_ALERTS_ID = "ringconn_wear_alerts"
        const val NOTIFICATION_LOW_BAT_ID = 3001

        val syncStatusFlow = MutableStateFlow("IDLE")
        val lastSyncCompletedMs = MutableStateFlow(0L)
    }
}
