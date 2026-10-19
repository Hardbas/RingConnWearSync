package io.github.hardbas.wearsync.bridge

import io.github.hardbas.wearsync.bridge.data.RingConnVitals
import io.github.hardbas.wearsync.bridge.service.RingConnTelemetryParser
import io.github.hardbas.wearsync.bridge.service.RingConnTelemetryReader
import io.github.hardbas.wearsync.bridge.service.WearDataSyncService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingConnVitalsMergeTest {

    @Test
    fun defaultVitals_haveExpectedBaseline() {
        val vitals = RingConnVitals()
        assertEquals(0, vitals.ringBattery)
        assertEquals(0, vitals.lastHeartRate)
        assertEquals(0.0f, vitals.skinTempBaseline, 0.01f)
        assertEquals(0.0f, vitals.skinTempOffset, 0.01f)
        assertFalse(vitals.isConnected)
        assertEquals(0.0f, vitals.odiDropsPerHour, 0.01f)
        assertEquals(0, vitals.minSleepingSpo2)
        assertEquals(0.0f, vitals.sleepingHrDipPct, 0.01f)
        assertEquals(0, vitals.napMinutes)
        assertEquals("", vitals.bedtimeWindow)
        assertEquals(0, vitals.standHours)
    }

    @Test
    fun stalenessThreshold_marksOldDataProperly() {
        val nowSec = System.currentTimeMillis() / 1000L
        val fresh = RingConnVitals(refreshTimestamp = nowSec - 300L) // 5 minutes ago
        assertFalse(fresh.isStale)

        val stale = RingConnVitals(refreshTimestamp = nowSec - 1801L) // > 30 minutes
        assertTrue(stale.isStale)
    }

    @Test
    fun batteryLevels_validateChargingAndCriticalFlags() {
        val lowBattery = RingConnVitals(ringBattery = 15, isCharging = false)
        assertTrue("Battery < 20% should trigger low battery warning state", lowBattery.ringBattery < 20)

        val charging = RingConnVitals(ringBattery = 18, isCharging = true)
        assertTrue(charging.isCharging)
    }

    @Test
    fun parser_honestBaselineDefaultsTo36_5WhenOffsetScraped() {
        val tokens = listOf("Skin Temperature", "+0.3°C")
        val parsed = RingConnTelemetryParser.parseTokens(tokens, RingConnVitals())
        assertEquals(0.3f, parsed.skinTempOffset, 0.01f)
        assertEquals(36.5f, parsed.skinTempBaseline, 0.01f)
    }

    @Test
    fun parser_preservesExplicitBaselineWhenPresent() {
        val tokens = listOf("Skin Temperature", "-0.2°C", "Baseline", "36.2°C")
        val parsed = RingConnTelemetryParser.parseTokens(tokens, RingConnVitals())
        assertEquals(-0.2f, parsed.skinTempOffset, 0.01f)
        assertEquals(36.2f, parsed.skinTempBaseline, 0.01f)
    }

    @Test
    fun signatureFields_detectChangesProperly() {
        fun makeSignature(vitals: RingConnVitals): String = WearDataSyncService.computeSignature(vitals)

        val base = RingConnVitals(avgSpo2 = 98, avgHrv = 45, isConnected = true, targetSteps = 10000, targetCalorie = 500, refreshTimestamp = 1000L)
        val sigBase = makeSignature(base)

        assertNotEquals(sigBase, makeSignature(base.copy(avgSpo2 = 97)))
        assertNotEquals(sigBase, makeSignature(base.copy(avgHrv = 50)))
        assertNotEquals(sigBase, makeSignature(base.copy(isConnected = false)))
        assertNotEquals(sigBase, makeSignature(base.copy(targetSteps = 12000)))
        assertNotEquals(sigBase, makeSignature(base.copy(targetCalorie = 600)))
        assertNotEquals(sigBase, makeSignature(base.copy(refreshTimestamp = 2000L)))
        assertNotEquals(sigBase, makeSignature(base.copy(sleepInsight = "Great sleep")))
        assertNotEquals(sigBase, makeSignature(base.copy(stressInsight = "Calm")))
        assertNotEquals(sigBase, makeSignature(base.copy(odiDropsPerHour = 2.1f)))
        assertNotEquals(sigBase, makeSignature(base.copy(minSleepingSpo2 = 91)))
        assertNotEquals(sigBase, makeSignature(base.copy(sleepingHrDipPct = 12.0f)))
        assertNotEquals(sigBase, makeSignature(base.copy(napMinutes = 45)))
        assertNotEquals(sigBase, makeSignature(base.copy(bedtimeWindow = "23:00 - 23:45")))
        assertNotEquals(sigBase, makeSignature(base.copy(standHours = 9)))
    }

    @Test
    fun reverseEngineeredBiomarkers_defaultProperlyAndTriggerSignatureUpdates() {
        val vitals = RingConnVitals()
        assertEquals(0.0f, vitals.odiDropsPerHour, 0.001f)
        assertEquals(0, vitals.minSleepingSpo2)
        assertEquals(0.0f, vitals.sleepingHrDipPct, 0.001f)
        assertEquals(0, vitals.napMinutes)
        assertEquals("", vitals.bedtimeWindow)
        assertEquals(0, vitals.standHours)

        val baseSig = WearDataSyncService.computeSignature(vitals)
        assertNotEquals(baseSig, WearDataSyncService.computeSignature(vitals.copy(odiDropsPerHour = 1.4f)))
        assertNotEquals(baseSig, WearDataSyncService.computeSignature(vitals.copy(minSleepingSpo2 = 93)))
        assertNotEquals(baseSig, WearDataSyncService.computeSignature(vitals.copy(sleepingHrDipPct = 15.5f)))
        assertNotEquals(baseSig, WearDataSyncService.computeSignature(vitals.copy(napMinutes = 25)))
        assertNotEquals(baseSig, WearDataSyncService.computeSignature(vitals.copy(bedtimeWindow = "22:15 - 23:00")))
        assertNotEquals(baseSig, WearDataSyncService.computeSignature(vitals.copy(standHours = 7)))
    }

    @Test
    fun parser_extractsInsightsCorrectly() {
        val tokens = listOf(
            "Sleep", "85",
            "Your sleep was restful with excellent recovery overnight.",
            "Stress", "28",
            "Nivel de estrés relajado durante la mayor parte del día.",
            "Activity", "78",
            "You reached your daily steps and active movement goal!",
            "Skin Temperature", "+0.2°C",
            "Baseline skin temperature remained normal overnight."
        )
        val parsed = RingConnTelemetryParser.parseTokens(tokens, RingConnVitals())
        assertEquals(85, parsed.sleepScore)
        assertEquals(28, parsed.stressScore)
        assertEquals(78, parsed.activityScore)
        assertEquals("Your sleep was restful with excellent recovery overnight.", parsed.sleepInsight)
        assertEquals("Nivel de estrés relajado durante la mayor parte del día.", parsed.stressInsight)
        assertEquals("You reached your daily steps and active movement goal!", parsed.activityInsight)
        assertEquals("Baseline skin temperature remained normal overnight.", parsed.vitalsInsight)
    }

    @Test
    fun midnightRollover_resetsDailyCumulativeCountersAndPreservesInstantVitals() {
        val yesterdayVitals = RingConnVitals(
            lastHeartRate = 68,
            avgHeartRate = 64,
            lastSpo2 = 98,
            avgSpo2 = 97,
            lastHrv = 52,
            avgHrv = 49,
            ringBattery = 75,
            skinTempOffset = 0.2f,
            skinTempBaseline = 36.4f,
            sleepScore = 86,
            activityScore = 91,
            activeMinutes = 74,
            liveSteps = 8450,
            liveCalorie = 420,
            minHeartRate = 51,
            maxHeartRate = 142,
            zone1Mins = 320,
            zone2Mins = 45,
            zone3Mins = 12,
            zone4Mins = 0,
            zone5Mins = 0,
            sleepDeepMins = 85,
            sleepRemMins = 90,
            sleepLightMins = 210,
            sleepAwakeMins = 18,
            sleepEfficiencyPct = 94,
            hrPhaseDeep = 52,
            hrPhaseRem = 58,
            hrPhaseLight = 55,
            odiDropsPerHour = 1.2f,
            minSleepingSpo2 = 93,
            sleepingHrDipPct = 16.5f,
            napMinutes = 35,
            standHours = 11,
            stressDawn = 24,
            stressMorning = 38,
            stressAfternoon = 45,
            stressNight = 32,
            basalCalories = 1750,
            distanceKm = 6.44f,
            sleepInsight = "Yesterday sleep",
            activityInsight = "Yesterday activity",
            stressInsight = "Yesterday stress",
            vitalsInsight = "Yesterday vitals"
        )

        // Simulate crossing midnight at 01:10 AM
        val rolledOver = RingConnTelemetryReader.rolloverVitalsForNewDay(yesterdayVitals, nowHour = 1, nowMinute = 10)

        // Instantaneous vitals and battery MUST be preserved
        assertEquals(68, rolledOver.lastHeartRate)
        assertEquals(64, rolledOver.avgHeartRate)
        assertEquals(98, rolledOver.lastSpo2)
        assertEquals(97, rolledOver.avgSpo2)
        assertEquals(52, rolledOver.lastHrv)
        assertEquals(49, rolledOver.avgHrv)
        assertEquals(75, rolledOver.ringBattery)
        assertEquals(0.2f, rolledOver.skinTempOffset, 0.01f)
        assertEquals(36.4f, rolledOver.skinTempBaseline, 0.01f)

        // Daily-cumulative activity counters MUST be 0 on the new day
        assertEquals(0, rolledOver.liveSteps)
        assertEquals(0, rolledOver.liveCalorie)
        assertEquals(0, rolledOver.activityScore)
        assertEquals(0, rolledOver.activeMinutes)
        assertEquals(0.0f, rolledOver.distanceKm, 0.01f)
        assertEquals(0, rolledOver.standHours)

        // Sleep score and stages MUST be 0 on the new day until sleep concludes today
        assertEquals(0, rolledOver.sleepScore)
        assertEquals(0, rolledOver.sleepDeepMins)
        assertEquals(0, rolledOver.sleepRemMins)
        assertEquals(0, rolledOver.sleepLightMins)
        assertEquals(0, rolledOver.sleepAwakeMins)
        assertEquals(0, rolledOver.sleepEfficiencyPct)
        assertEquals(0, rolledOver.hrPhaseDeep)
        assertEquals(0, rolledOver.hrPhaseRem)
        assertEquals(0, rolledOver.hrPhaseLight)
        assertEquals(0.0f, rolledOver.odiDropsPerHour, 0.01f)
        assertEquals(0, rolledOver.minSleepingSpo2)
        assertEquals(0.0f, rolledOver.sleepingHrDipPct, 0.01f)
        assertEquals(0, rolledOver.napMinutes)

        // Circadian stress blocks and min/max HR MUST be reset
        assertEquals(0, rolledOver.stressDawn)
        assertEquals(0, rolledOver.stressMorning)
        assertEquals(0, rolledOver.stressAfternoon)
        assertEquals(0, rolledOver.stressNight)
        assertEquals(0, rolledOver.minHeartRate)
        assertEquals(0, rolledOver.maxHeartRate)
        assertEquals(0, rolledOver.zone1Mins)
        assertEquals(0, rolledOver.zone2Mins)

        // Basal calories at 01:10 AM should only reflect today's BMR accrued so far (~120 kcal)
        assertTrue("Basal calories at 01:10 AM must be reset to today (~120 kcal), got: ${rolledOver.basalCalories}", rolledOver.basalCalories in 100..200)

        // Insights from yesterday must be empty
        assertEquals("", rolledOver.sleepInsight)
        assertEquals("", rolledOver.activityInsight)
        assertEquals("", rolledOver.stressInsight)
        assertEquals("", rolledOver.vitalsInsight)
    }

    @Test
    fun parser_allowsZeroResetForStepsAndCalories() {
        val base = RingConnVitals(liveSteps = 5000, liveCalorie = 250, sleepScore = 80, activityScore = 75)
        val tokensWithDashes = listOf("Steps", "--", "Calories", "--", "Sleep", "--", "Activity", "--")
        val parsedDashes = RingConnTelemetryParser.parseTokens(tokensWithDashes, base)
        assertEquals(0, parsedDashes.liveSteps)
        assertEquals(0, parsedDashes.liveCalorie)
        assertEquals(0, parsedDashes.sleepScore)
        assertEquals(0, parsedDashes.activityScore)

        val tokensWithZeros = listOf("Steps", "0", "Calories", "0", "Activity", "0")
        val parsedZeros = RingConnTelemetryParser.parseTokens(tokensWithZeros, base)
        assertEquals(0, parsedZeros.liveSteps)
        assertEquals(0, parsedZeros.liveCalorie)
        assertEquals(0, parsedZeros.activityScore)
    }
}

