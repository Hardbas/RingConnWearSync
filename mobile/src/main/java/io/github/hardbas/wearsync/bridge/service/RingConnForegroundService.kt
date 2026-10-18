package io.github.hardbas.wearsync.bridge.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.hardbas.wearsync.bridge.PhoneBridgeMainActivity
import io.github.hardbas.wearsync.bridge.data.RingConnVitals
import io.github.hardbas.wearsync.bridge.receiver.WatchdogReceiver
import kotlinx.coroutines.*

class RingConnForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private var lastMorningSyncDay: Int = -1
    private var lastUserPresentSyncMs: Long = 0L

    private val userPresentReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                val cal = java.util.Calendar.getInstance()
                val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                val day = cal.get(java.util.Calendar.DAY_OF_YEAR)

                // 1. Morning Sleep Deep Sync
                if (hour in 6..11 && lastMorningSyncDay != day) {
                    lastMorningSyncDay = day
                    Log.i(TAG, "Morning user-present detected: waking RingConn to sync sleep BLE data")
                    scope.launch {
                        try {
                            val launchIntent = packageManager.getLaunchIntentForPackage("com.gdjztech.ringconn")
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                                startActivity(launchIntent)
                                delay(4000L)
                                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_HOME)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                startActivity(homeIntent)
                                triggerSync(force = true)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in morning auto-sync", e)
                        }
                    }
                    return
                }

                // 2. Opportunistic Unlocked Sync (Throttled to max 1 per 60s)
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastUserPresentSyncMs > 60_000L) {
                    lastUserPresentSyncMs = nowMs
                    Log.i(TAG, "User present (unlock) detected: triggering opportunistic sync")
                    scope.launch {
                        triggerSync(force = false)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildForegroundNotification("Conectado con Galaxy Watch 8")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed startForeground with connectedDevice, falling back to dataSync", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Failed startForeground fallback to dataSync", e2)
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e3: Exception) {
                    Log.e(TAG, "Failed default startForeground", e3)
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(userPresentReceiver, android.content.IntentFilter(Intent.ACTION_USER_PRESENT), Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(userPresentReceiver, android.content.IntentFilter(Intent.ACTION_USER_PRESENT))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register userPresentReceiver", e)
        }

        scheduleWatchdog(this)
        startPeriodicSync()
    }

    private var lastNotificationText: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SYNC_NOW) {
            scope.launch {
                triggerSync(force = true)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            unregisterReceiver(userPresentReceiver)
        } catch (_: Exception) {}
        syncJob?.cancel()
        scope.cancel()
        scheduleWatchdog(this)
        super.onDestroy()
    }

    private fun startPeriodicSync() {
        syncJob = scope.launch {
            while (isActive) {
                triggerSync(force = false)
                delay(120_000L) // 2 minutes background poll; pushes to watch only on metric changes
            }
        }
    }

    private suspend fun triggerSync(force: Boolean = false) {
        try {
            // Poke RingConn Flutter background worker
            try {
                val bgIntent = Intent("es.antonborri.home_widget.action.BACKGROUND").apply {
                    setPackage("com.gdjztech.ringconn")
                }
                sendBroadcast(bgIntent)
            } catch (e: Exception) {
                Log.d(TAG, "HomeWidget background broadcast error: ${e.message}")
            }

            val vitals = RingConnTelemetryReader.readBestAvailableVitalsSuspend(this@RingConnForegroundService)
            val success = WearDataSyncService(this@RingConnForegroundService).syncVitalsToWatch(vitals, force = force)
            val noteText = if (success) {
                "Sincronizado · FC: ${vitals.lastHeartRate} bpm · SpO2: ${vitals.lastSpo2}% · Anillo: ${vitals.ringBattery}%"
            } else {
                "Buscando Galaxy Watch 8..."
            }
            updateNotification(noteText)
            scheduleWatchdog(this@RingConnForegroundService)
            Log.d(TAG, "Periodic sync completed: success=$success")
        } catch (e: Exception) {
            Log.e(TAG, "Error in periodic sync", e)
        }
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val launchIntent = Intent(this, PhoneBridgeMainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RingConn Wear Sync")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun updateNotification(contentText: String) {
        if (contentText == lastNotificationText) return
        lastNotificationText = contentText
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(contentText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RingConn Wear Sync Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la sincronización en tiempo real entre RingConn y Galaxy Watch 8"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "RingConnFgs"
        const val CHANNEL_ID = "ringconn_wear_sync_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_SYNC_NOW = "io.github.hardbas.wearsync.bridge.SYNC_NOW"
        private const val WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
        private const val WATCHDOG_REQUEST_CODE = 9001

        fun start(context: Context) {
            val intent = Intent(context, RingConnForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            scheduleWatchdog(context)
        }

        fun scheduleWatchdog(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, WatchdogReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    WATCHDOG_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerAtMs = SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMs,
                    pendingIntent
                )
                Log.d(TAG, "Watchdog scheduled in 15 minutes ($triggerAtMs)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule watchdog", e)
            }
        }
    }
}
