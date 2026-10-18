package io.github.hardbas.wearsync.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyIntentTelemetryIfPresent(intent)
        setContent {
            WearApp(context = this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyIntentTelemetryIfPresent(intent)
    }

    private fun applyIntentTelemetryIfPresent(intent: Intent?) {
        if (intent == null) return
        val current = RingConnComplicationProvider.getLatestVitals(this)
        val hasOverride = intent.hasExtra("hr_last") ||
            intent.hasExtra("spo2_last") ||
            intent.hasExtra("steps_live") ||
            intent.hasExtra("sleep_score") ||
            intent.hasExtra("ring_battery")

        if (hasOverride) {
            val updated = current.copy(
                lastHeartRate = intent.getIntExtra("hr_last", current.lastHeartRate),
                lastSpo2 = intent.getIntExtra("spo2_last", current.lastSpo2),
                lastHrv = intent.getIntExtra("hrv_last", current.lastHrv),
                sleepScore = intent.getIntExtra("sleep_score", current.sleepScore),
                activityScore = intent.getIntExtra("activity_score", current.activityScore),
                stressScore = intent.getIntExtra("stress_score", current.stressScore),
                liveSteps = intent.getIntExtra("steps_live", current.liveSteps),
                liveCalorie = intent.getIntExtra("cal_live", current.liveCalorie),
                ringBattery = intent.getIntExtra("ring_battery", current.ringBattery),
                refreshTimestamp = System.currentTimeMillis() / 1000L
            )
            RingConnComplicationProvider.saveAndBroadcastVitals(this, updated)
        } else {
            // Ensure Tiles and Complications are refreshed on launch
            RingConnComplicationProvider.saveAndBroadcastVitals(this, current)
        }
    }
}
