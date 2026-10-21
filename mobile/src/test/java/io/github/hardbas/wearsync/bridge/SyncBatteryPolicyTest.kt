package io.github.hardbas.wearsync.bridge

import io.github.hardbas.wearsync.bridge.service.SyncBatteryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncBatteryPolicyTest {

    @Test
    fun morningHoursKeepTheFastWatchdogSoThePeekStillFires() {
        for (hour in SyncBatteryPolicy.MORNING_WINDOW_START_HOUR until SyncBatteryPolicy.MORNING_WINDOW_END_HOUR) {
            assertEquals(
                "hour $hour should use the morning watchdog",
                SyncBatteryPolicy.WATCHDOG_MORNING_INTERVAL_MS,
                SyncBatteryPolicy.watchdogIntervalMs(hour)
            )
        }
    }

    @Test
    fun restOfTheDayUsesTheSlowWatchdog() {
        val outside = (0 until 24).filterNot { it in SyncBatteryPolicy.MORNING_WINDOW_START_HOUR until SyncBatteryPolicy.MORNING_WINDOW_END_HOUR }
        assertTrue("the slow window must cover most of the day", outside.size > 12)
        for (hour in outside) {
            assertEquals(
                "hour $hour should use the day watchdog",
                SyncBatteryPolicy.WATCHDOG_INTERVAL_MS,
                SyncBatteryPolicy.watchdogIntervalMs(hour)
            )
        }
    }

    @Test
    fun windowBoundariesAreExact() {
        assertFalse(SyncBatteryPolicy.isMorningWindow(SyncBatteryPolicy.MORNING_WINDOW_START_HOUR - 1))
        assertTrue(SyncBatteryPolicy.isMorningWindow(SyncBatteryPolicy.MORNING_WINDOW_START_HOUR))
        assertTrue(SyncBatteryPolicy.isMorningWindow(SyncBatteryPolicy.MORNING_WINDOW_END_HOUR - 1))
        assertFalse(SyncBatteryPolicy.isMorningWindow(SyncBatteryPolicy.MORNING_WINDOW_END_HOUR))
    }

    @Test
    fun morningWindowCoversThePeekTriggerHours() {
        // RingConnForegroundService peeks on ACTION_USER_PRESENT, and the non-accessibility
        // fallback is limited to 5..12. The watchdog must be fast across that whole span.
        for (hour in 5..12) {
            assertTrue("hour $hour must fall inside the fast watchdog window", SyncBatteryPolicy.isMorningWindow(hour))
        }
    }

    @Test
    fun invalidHoursFallBackToTheSlowWatchdogInsteadOfCrashing() {
        assertEquals(SyncBatteryPolicy.WATCHDOG_INTERVAL_MS, SyncBatteryPolicy.watchdogIntervalMs(-1))
        assertEquals(SyncBatteryPolicy.WATCHDOG_INTERVAL_MS, SyncBatteryPolicy.watchdogIntervalMs(24))
        assertEquals(SyncBatteryPolicy.WATCHDOG_INTERVAL_MS, SyncBatteryPolicy.watchdogIntervalMs(99))
    }

    @Test
    fun slowWatchdogIsStillBoundedSoRecoveryDoesNotTakeHours() {
        assertTrue(SyncBatteryPolicy.WATCHDOG_INTERVAL_MS > SyncBatteryPolicy.WATCHDOG_MORNING_INTERVAL_MS)
        assertTrue(
            "Android rate-limits identical setAndAllowWhileIdle alarms, going under 15 min buys nothing",
            SyncBatteryPolicy.WATCHDOG_MORNING_INTERVAL_MS >= 15 * 60 * 1000L
        )
        assertTrue(
            "recovery of a killed bridge service must stay well under an hour",
            SyncBatteryPolicy.WATCHDOG_INTERVAL_MS <= 60 * 60 * 1000L
        )
    }

    @Test
    fun heartbeatWasRelaxedButStillRecoversAMissedPush() {
        assertTrue(
            "heartbeat exists to recover a missed push, so it must stay bounded",
            SyncBatteryPolicy.HEARTBEAT_INTERVAL_MS in 10 * 60 * 1000L..SyncBatteryPolicy.WATCHDOG_INTERVAL_MS
        )
    }
}
