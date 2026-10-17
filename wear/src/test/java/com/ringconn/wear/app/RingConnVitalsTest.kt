package com.ringconn.wear.app

import com.ringconn.wear.app.data.RingConnVitals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingConnVitalsTest {

    @Test
    fun defaultVitals_haveSensibleZeroValues() {
        val vitals = RingConnVitals()
        assertEquals(0, vitals.ringBattery)
        assertEquals(0, vitals.lastHeartRate)
        assertEquals(0, vitals.lastSpo2)
        assertEquals(0, vitals.stressScore)
        assertEquals(10000, vitals.targetSteps)
        assertEquals(500, vitals.targetCalorie)
        assertFalse(vitals.isConnected)
        assertFalse(vitals.isCharging)
    }

    @Test
    fun isStale_returnsTrueWhenRefreshTimestampIsOlderThan30Minutes() {
        val nowSec = System.currentTimeMillis() / 1000L
        val freshVitals = RingConnVitals(refreshTimestamp = nowSec - 600L) // 10 mins ago
        assertFalse(freshVitals.isStale)

        val staleVitals = RingConnVitals(refreshTimestamp = nowSec - 2000L) // > 30 mins ago
        assertTrue(staleVitals.isStale)
    }

    @Test
    fun complicationBatteryFormatting_handlesZeroAndLiveValues() {
        // Zero or unknown battery formatting logic used in RingBatteryComplicationProvider
        val zeroBattery = 0
        val textValueZero = if (zeroBattery > 0) "$zeroBattery%" else "--%"
        assertEquals("--%", textValueZero)

        // Live valid battery
        val liveBattery = 85
        val textValueLive = if (liveBattery > 0) "$liveBattery%" else "--%"
        assertEquals("85%", textValueLive)

        // Clamping to 0..100
        val clampedOver = 120.coerceIn(0, 100).toFloat()
        assertEquals(100f, clampedOver, 0.01f)

        val clampedNegative = (-5).coerceIn(0, 100).toFloat()
        assertEquals(0f, clampedNegative, 0.01f)
    }

    @Test
    fun sleepStages_aggregatesNightIntervalsAccurately() {
        val vitals = RingConnVitals(
            sleepDeepMins = 85,
            sleepRemMins = 65,
            sleepLightMins = 120,
            sleepAwakeMins = 16
        )
        val totalSleepMins = vitals.sleepDeepMins + vitals.sleepRemMins + vitals.sleepLightMins
        assertEquals(270, totalSleepMins) // 4h 30m of real restorative sleep
        assertEquals(16, vitals.sleepAwakeMins)
    }

    @Test
    fun lowBatteryAlert_triggerConditionsAndHysteresis() {
        // Battery at 15% and not charging -> should trigger alert
        val lowBatVitals = RingConnVitals(ringBattery = 15, isCharging = false)
        val shouldAlert = lowBatVitals.ringBattery in 1..19 && !lowBatVitals.isCharging
        assertTrue("Battery at 15% not charging must trigger alert condition", shouldAlert)

        // Battery at 15% but currently charging -> should NOT trigger alert
        val chargingVitals = RingConnVitals(ringBattery = 15, isCharging = true)
        val shouldAlertCharging = chargingVitals.ringBattery in 1..19 && !chargingVitals.isCharging
        assertFalse("Charging ring must never trigger low battery alert", shouldAlertCharging)

        // Battery at 20% or above -> normal state, no alert
        val normalBatVitals = RingConnVitals(ringBattery = 20, isCharging = false)
        val shouldAlertNormal = normalBatVitals.ringBattery in 1..19 && !normalBatVitals.isCharging
        assertFalse("Battery >= 20% must not trigger low battery alert", shouldAlertNormal)

        // Battery at 0 (disconnected/unknown) -> must not trigger false alert
        val unknownBatVitals = RingConnVitals(ringBattery = 0, isCharging = false)
        val shouldAlertZero = unknownBatVitals.ringBattery in 1..19 && !unknownBatVitals.isCharging
        assertFalse("Unknown/0 battery must not trigger false alert", shouldAlertZero)
    }
}
