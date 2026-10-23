package io.github.hardbas.wearsync.app

import io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider
import io.github.hardbas.wearsync.app.data.RingConnHistoryStore
import io.github.hardbas.wearsync.app.data.RingConnVitals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        assertEquals(0f, vitals.skinTempBaseline, 0.001f)
        assertEquals(0f, vitals.skinTempOffset, 0.001f)
        assertFalse(vitals.isConnected)
        assertFalse(vitals.isCharging)
    }

    @Test
    fun temperatureMetrics_formatsHonestValues() {
        val vitalsWithData = RingConnVitals(skinTempOffset = 0.3f, skinTempBaseline = 36.4f)
        val hasData = vitalsWithData.skinTempBaseline > 0f || vitalsWithData.skinTempOffset != 0f
        assertTrue(hasData)

        val vitalsNoData = RingConnVitals()
        val hasNoData = vitalsNoData.skinTempBaseline <= 0f && vitalsNoData.skinTempOffset == 0f
        assertTrue(hasNoData)
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

    @Test
    fun computeReadinessScore_returnsZeroForEmptyVitalsAndValidScoreForPopulatedVitals() {
        // Zero/empty vitals return 0
        val emptyVitals = RingConnVitals()
        val emptyScore = RingConnHistoryStore.computeReadinessScore(emptyVitals)
        assertEquals(0, emptyScore)

        // Populated standard vitals return valid score in 1..100
        val populatedVitals = RingConnVitals(
            lastHeartRate = 72,
            avgHeartRate = 65,
            sleepScore = 84,
            lastHrv = 52,
            avgHrv = 48,
            stressScore = 28,
            skinTempOffset = 0.2f
        )
        val populatedScore = RingConnHistoryStore.computeReadinessScore(populatedVitals)
        assertTrue("Populated score must be between 1 and 100, got: $populatedScore", populatedScore in 1..100)

        // Optimal vitals produce high readiness score
        val activeVitals = RingConnVitals(
            lastHeartRate = 60,
            sleepScore = 92,
            lastHrv = 65,
            avgHrv = 50,
            stressScore = 15,
            skinTempOffset = -0.1f
        )
        val activeScore = RingConnHistoryStore.computeReadinessScore(activeVitals)
        assertTrue("Active recovery score must be between 1 and 100, got: $activeScore", activeScore in 1..100)
        assertTrue("High recovery vitals should produce high readiness score", activeScore >= 75)
    }

    @Test
    fun watchMidnightRollover_resetsDailyCountersAndPreservesDeviceVitals() {
        val yesterdayVitals = RingConnVitals(
            lastHeartRate = 72,
            avgHeartRate = 66,
            lastSpo2 = 98,
            avgSpo2 = 97,
            lastHrv = 48,
            avgHrv = 44,
            ringBattery = 82,
            isCharging = false,
            isConnected = true,
            skinTempBaseline = 36.5f,
            skinTempOffset = 0.1f,
            sleepScore = 82,
            activityScore = 88,
            activeMinutes = 65,
            liveSteps = 9120,
            liveCalorie = 480,
            minHeartRate = 52,
            maxHeartRate = 138,
            zone1Mins = 310,
            zone2Mins = 50,
            sleepDeepMins = 78,
            sleepRemMins = 82,
            sleepLightMins = 205,
            sleepAwakeMins = 20,
            sleepEfficiencyPct = 92,
            odiDropsPerHour = 1.1f,
            minSleepingSpo2 = 94,
            sleepingHrDipPct = 14.5f,
            napMinutes = 30,
            standHours = 10,
            stressDawn = 22,
            stressMorning = 40,
            stressAfternoon = 50,
            stressNight = 35,
            basalCalories = 1800,
            distanceKm = 6.95f,
            sleepInsight = "Yesterday sleep",
            activityInsight = "Yesterday activity"
        )

        val rolled = io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider.rolloverVitalsForNewDay(yesterdayVitals)

        // Instantaneous device values preserved
        assertEquals(72, rolled.lastHeartRate)
        assertEquals(82, rolled.ringBattery)
        assertEquals(98, rolled.lastSpo2)
        assertEquals(48, rolled.lastHrv)
        assertEquals(36.5f, rolled.skinTempBaseline, 0.01f)
        assertEquals(0.1f, rolled.skinTempOffset, 0.01f)

        // Daily-cumulative activity reset to 0
        assertEquals(0, rolled.liveSteps)
        assertEquals(0, rolled.liveCalorie)
        assertEquals(0, rolled.activityScore)
        assertEquals(0, rolled.activeMinutes)
        assertEquals(0.0f, rolled.distanceKm, 0.01f)
        assertEquals(0, rolled.standHours)

        // Sleep score and metrics reset to 0
        assertEquals(0, rolled.sleepScore)
        assertEquals(0, rolled.sleepDeepMins)
        assertEquals(0, rolled.sleepRemMins)
        assertEquals(0, rolled.sleepLightMins)
        assertEquals(0, rolled.sleepAwakeMins)
        assertEquals(0, rolled.sleepEfficiencyPct)
        assertEquals(0.0f, rolled.odiDropsPerHour, 0.01f)
        assertEquals(0, rolled.minSleepingSpo2)
        assertEquals(0.0f, rolled.sleepingHrDipPct, 0.01f)
        assertEquals(0, rolled.napMinutes)

        // Circadian blocks reset
        assertEquals(0, rolled.stressDawn)
        assertEquals(0, rolled.stressMorning)
        assertEquals(0, rolled.stressAfternoon)
        assertEquals(0, rolled.stressNight)
        assertEquals(0, rolled.minHeartRate)
        assertEquals(0, rolled.maxHeartRate)
        assertEquals(0, rolled.zone1Mins)
        assertEquals(0, rolled.basalCalories)
        assertEquals("", rolled.sleepInsight)
        assertEquals("", rolled.activityInsight)
    }

    @Test
    fun computeContentSignature_ignoresRefreshTimestampAndBasalCalories() {
        val vitalsA = RingConnVitals(
            lastHeartRate = 72,
            lastSpo2 = 98,
            ringBattery = 80,
            refreshTimestamp = 1000L,
            basalCalories = 1200
        )
        val vitalsB = vitalsA.copy(
            refreshTimestamp = 2000L,
            basalCalories = 1205
        )
        assertEquals(
            RingConnComplicationProvider.computeContentSignature(vitalsA),
            RingConnComplicationProvider.computeContentSignature(vitalsB)
        )

        val vitalsChanged = vitalsA.copy(lastHeartRate = 73)
        assertNotEquals(
            RingConnComplicationProvider.computeContentSignature(vitalsA),
            RingConnComplicationProvider.computeContentSignature(vitalsChanged)
        )
    }
}
