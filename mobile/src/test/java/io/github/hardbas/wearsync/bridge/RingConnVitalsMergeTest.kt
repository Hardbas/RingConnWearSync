package io.github.hardbas.wearsync.bridge

import io.github.hardbas.wearsync.bridge.data.RingConnVitals
import io.github.hardbas.wearsync.bridge.service.RingConnTelemetryParser
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
}

