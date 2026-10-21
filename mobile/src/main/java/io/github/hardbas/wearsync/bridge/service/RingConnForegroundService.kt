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
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.hardbas.wearsync.bridge.PhoneBridgeMainActivity
import io.github.hardbas.wearsync.bridge.data.RingConnVitals
import io.github.hardbas.wearsync.bridge.receiver.WatchdogReceiver
import java.time.LocalDate
import kotlinx.coroutines.*

class RingConnForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private var lastUserPresentSyncMs: Long = 0L

    private val userPresentReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                val cal = java.util.Calendar.getInstance()
                val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                val todayEpochDay = LocalDate.now().toEpochDay()
                val prefs = getSharedPreferences(RingConnTelemetryReader.PREFS_NAME, Context.MODE_PRIVATE)
                val lastMorningPeekEpochDay = prefs.getLong(RingConnTelemetryReader.KEY_LAST_MORNING_PEEK_EPOCH_DAY, -1L)
                val currentVitals = RingConnTelemetryReader.readBestAvailableVitals(this@RingConnForegroundService)

                // 1. Once-Per-Day Morning Smart Unlock Peek
                if (RingConnAccessibilityService.isRunning()) {
                    if (todayEpochDay != lastMorningPeekEpochDay && (currentVitals.sleepScore == 0 || currentVitals.skinTempBaseline == 0f)) {
                        prefs.edit().putLong(RingConnTelemetryReader.KEY_LAST_MORNING_PEEK_EPOCH_DAY, todayEpochDay).apply()
                        Log.i(TAG, "Morning Smart Unlock Peek triggered on epoch day $todayEpochDay (hour=$hour)")
                        scope.launch {
                            try {
                                val scraped = RingConnAccessibilityService.instance?.wakeAndScrapeRingConnApp()
                                val finalVitals = scraped ?: RingConnTelemetryReader.readBestAvailableVitalsSuspend(this@RingConnForegroundService)
                                WearDataSyncService(this@RingConnForegroundService).syncVitalsToWatch(finalVitals, force = true)
                                Log.i(TAG, "Morning Smart Unlock Peek completed: HR=${finalVitals.lastHeartRate}, Sleep=${finalVitals.sleepScore}, Bat=${finalVitals.ringBattery}%")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in morning smart peek", e)
                            }
                        }
                        return
                    }
                } else if (hour in 5..12 && todayEpochDay != lastMorningPeekEpochDay && (currentVitals.sleepScore == 0 || currentVitals.skinTempBaseline == 0f)) {
                    // Fallback when AccessibilityService is not yet enabled by user
                    prefs.edit().putLong(RingConnTelemetryReader.KEY_LAST_MORNING_PEEK_EPOCH_DAY, todayEpochDay).apply()
                    Log.i(TAG, "Morning user-present detected without Accessibility: waking RingConn fallback")
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
                            Log.e(TAG, "Error in morning auto-sync fallback", e)
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
        scheduleWatchdog(this)
        if (syncJob?.isActive != true) {
            startPeriodicSync()
        }
        val isForce = intent?.action == ACTION_SYNC_NOW
        scope.launch {
            triggerSync(force = isForce)
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
                // The widget this reads refreshes every 15-30 min, so polling faster than 5 min
                // only holds wake locks without producing fresher data.
                delay(SyncBatteryPolicy.IN_SERVICE_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun triggerSync(force: Boolean = false) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RingConnWearSync:SyncWakeLock")
        wakeLock?.acquire(15_000L)
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
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {}
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
        const val ACTION_WATCHDOG_TICK = "io.github.hardbas.wearsync.bridge.WATCHDOG_TICK"
        private const val WATCHDOG_REQUEST_CODE = 9001

        fun start(context: Context, action: String? = null) {
            val intent = Intent(context, RingConnForegroundService::class.java).apply {
                if (action != null) setAction(action)
            }
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
                val hourOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val intervalMs = SyncBatteryPolicy.watchdogIntervalMs(hourOfDay)
                val triggerAtMs = SystemClock.elapsedRealtime() + intervalMs
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMs,
                    pendingIntent
                )
                Log.d(TAG, "Watchdog scheduled in ${intervalMs / 60_000} minutes (hour=$hourOfDay)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule watchdog", e)
            }
        }
    }
}
