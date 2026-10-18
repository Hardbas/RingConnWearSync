package io.github.hardbas.wearsync.bridge

import io.github.hardbas.wearsync.bridge.data.RingConnVitals
import io.github.hardbas.wearsync.bridge.service.RingConnTelemetryParser
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
        fun makeSignature(vitals: RingConnVitals): String {
            return "${vitals.lastHeartRate}_${vitals.avgHeartRate}_${vitals.lastSpo2}_${vitals.avgSpo2}_${vitals.lastHrv}_${vitals.avgHrv}_" +
                "${vitals.sleepScore}_${vitals.activityScore}_${vitals.activeMinutes}_${vitals.stressScore}_${vitals.liveSteps}_" +
                "${vitals.targetSteps}_${vitals.liveCalorie}_${vitals.targetCalorie}_${vitals.ringBattery}_${vitals.isCharging}_${vitals.isConnected}_" +
                "${vitals.minHeartRate}_${vitals.maxHeartRate}_${vitals.zone1Mins}_${vitals.zone2Mins}_${vitals.zone3Mins}_${vitals.zone4Mins}_${vitals.zone5Mins}_" +
                "${vitals.sleepDeepMins}_${vitals.sleepRemMins}_${vitals.sleepLightMins}_${vitals.sleepAwakeMins}_" +
                "${vitals.skinTempOffset}_${vitals.skinTempBaseline}_${vitals.refreshTimestamp}"
        }

        val base = RingConnVitals(avgSpo2 = 98, avgHrv = 45, isConnected = true, targetSteps = 10000, targetCalorie = 500, refreshTimestamp = 1000L)
        val sigBase = makeSignature(base)

        assertNotEquals(sigBase, makeSignature(base.copy(avgSpo2 = 97)))
        assertNotEquals(sigBase, makeSignature(base.copy(avgHrv = 50)))
        assertNotEquals(sigBase, makeSignature(base.copy(isConnected = false)))
        assertNotEquals(sigBase, makeSignature(base.copy(targetSteps = 12000)))
        assertNotEquals(sigBase, makeSignature(base.copy(targetCalorie = 600)))
        assertNotEquals(sigBase, makeSignature(base.copy(refreshTimestamp = 2000L)))
    }
}

