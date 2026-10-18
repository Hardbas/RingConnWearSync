package io.github.hardbas.wearsync.bridge

import io.github.hardbas.wearsync.bridge.data.RingConnVitals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingConnVitalsMergeTest {

    @Test
    fun defaultVitals_haveExpectedBaseline() {
        val vitals = RingConnVitals()
        assertEquals(0, vitals.ringBattery)
        assertEquals(0, vitals.lastHeartRate)
        assertEquals(36.1f, vitals.skinTempBaseline, 0.01f)
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
}
