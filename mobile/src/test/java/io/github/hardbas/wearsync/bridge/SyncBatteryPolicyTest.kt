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

    @Test
    fun inServicePollingWasRelaxedToFiveMinutes() {
        assertTrue(
            "the poll loop re-reads the RingConn widget, which refreshes every 15-30 min",
            SyncBatteryPolicy.IN_SERVICE_POLL_INTERVAL_MS >= 5 * 60 * 1000L
        )
        assertTrue(
            "polling slower than the heartbeat would delay the only push that carries changes",
            SyncBatteryPolicy.IN_SERVICE_POLL_INTERVAL_MS <= SyncBatteryPolicy.HEARTBEAT_INTERVAL_MS
        )
    }

    @Test
    fun overnightHoursUseTheSlowPollInsteadOfTheDayOne() {
        // The ring consolidates sleep; it does not produce readings overnight that a
        // five-minute poll could catch. Polling that fast while the phone is asleep on a
        // bedside table buys nothing and costs wake locks.
        for (hour in listOf(23, 0, 2, 5)) {
            assertEquals(
                "hour $hour is overnight and must use the night poll",
                SyncBatteryPolicy.IN_SERVICE_POLL_NIGHT_INTERVAL_MS,
                SyncBatteryPolicy.pollIntervalMs(hour)
            )
            assertTrue("hour $hour must be inside the night window", SyncBatteryPolicy.isNightWindow(hour))
        }
    }

    @Test
    fun daytimeKeepsTheFastPoll() {
        for (hour in listOf(6, 9, 13, 18, 22)) {
            assertEquals(
                "hour $hour is daytime and must keep the fast poll",
                SyncBatteryPolicy.IN_SERVICE_POLL_INTERVAL_MS,
                SyncBatteryPolicy.pollIntervalMs(hour)
            )
            assertFalse("hour $hour must be outside the night window", SyncBatteryPolicy.isNightWindow(hour))
        }
    }

    @Test
    fun theWholeNightCostsAtMostThreePolls() {
        val nightMillis = (SyncBatteryPolicy.NIGHT_WINDOW_HOURS * 60 * 60 * 1000L)
        val polls = nightMillis / SyncBatteryPolicy.IN_SERVICE_POLL_NIGHT_INTERVAL_MS
        assertTrue("a night of polling must cost 2 to 3 wake cycles, got $polls", polls in 2..3)
    }

    @Test
    fun nightPollIsStrictlySlowerThanTheDayPoll() {
        assertTrue(
            "the night interval exists to spend less, so it must be longer",
            SyncBatteryPolicy.IN_SERVICE_POLL_NIGHT_INTERVAL_MS > SyncBatteryPolicy.IN_SERVICE_POLL_INTERVAL_MS
        )
    }

    @Test
    fun invalidHoursFallBackToTheDayPoll() {
        assertEquals(SyncBatteryPolicy.IN_SERVICE_POLL_INTERVAL_MS, SyncBatteryPolicy.pollIntervalMs(-1))
        assertEquals(SyncBatteryPolicy.IN_SERVICE_POLL_INTERVAL_MS, SyncBatteryPolicy.pollIntervalMs(24))
    }
}
