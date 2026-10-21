package io.github.hardbas.wearsync.bridge

import io.github.hardbas.wearsync.bridge.data.RingConnVitals
import io.github.hardbas.wearsync.bridge.service.MorningSyncAwaitPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningSyncAwaitPolicyTest {

    private fun vitals(sleepScore: Int) = RingConnVitals(sleepScore = sleepScore)

    @Test
    fun notReadyWhenNothingScrapedYet() {
        assertFalse(MorningSyncAwaitPolicy.isReady(null))
    }

    @Test
    fun notReadyWhileSleepCardStillShowsPlaceholder() {
        assertFalse(MorningSyncAwaitPolicy.isReady(vitals(0)))
    }

    @Test
    fun readyAsSoonAsSleepScoreIsPresent() {
        assertTrue(MorningSyncAwaitPolicy.isReady(vitals(72)))
    }

    @Test
    fun keepsPollingWhileSleepIsMissing() {
        assertTrue(MorningSyncAwaitPolicy.shouldKeepPolling(elapsedMs = 0L, vitals = null))
        assertTrue(MorningSyncAwaitPolicy.shouldKeepPolling(elapsedMs = 5_000L, vitals = vitals(0)))
    }

    @Test
    fun stopsPollingImmediatelyOnceSleepIsRead() {
        assertFalse(MorningSyncAwaitPolicy.shouldKeepPolling(elapsedMs = 5_000L, vitals = vitals(72)))
    }

    @Test
    fun keepsPollingWhileRingConnIsStillStartingUp() {
        assertTrue(
            "the peek must not abort during the launch grace window",
            MorningSyncAwaitPolicy.shouldKeepPolling(
                elapsedMs = MorningSyncAwaitPolicy.LAUNCH_GRACE_MS,
                vitals = null,
                ringConnForeground = false
            )
        )
    }

    @Test
    fun abortsOnceTheUserLeavesRingConnAfterTheGraceWindow() {
        assertFalse(
            MorningSyncAwaitPolicy.shouldKeepPolling(
                elapsedMs = MorningSyncAwaitPolicy.LAUNCH_GRACE_MS + 1_000L,
                vitals = null,
                ringConnForeground = false
            )
        )
    }

    @Test
    fun keepsPollingWhileRingConnStaysInFrontEvenWithNoSleepYet() {
        assertTrue(
            MorningSyncAwaitPolicy.shouldKeepPolling(
                elapsedMs = MorningSyncAwaitPolicy.LAUNCH_GRACE_MS + 1_000L,
                vitals = vitals(0),
                ringConnForeground = true
            )
        )
    }

    @Test
    fun launchGraceIsShorterThanTheHardCap() {
        assertTrue(MorningSyncAwaitPolicy.LAUNCH_GRACE_MS in 1_000L..MorningSyncAwaitPolicy.MAX_WAIT_MS)
    }

    @Test
    fun stopsPollingAtTheHardCapSoThePhoneIsNeverHijacked() {
        assertFalse(MorningSyncAwaitPolicy.shouldKeepPolling(MorningSyncAwaitPolicy.MAX_WAIT_MS, null))
        assertFalse(MorningSyncAwaitPolicy.shouldKeepPolling(MorningSyncAwaitPolicy.MAX_WAIT_MS + 1_000L, vitals(0)))
    }

    @Test
    fun capIsLongEnoughForRingConnToLoadAndStillPollsSeveralTimes() {
        assertTrue(
            "MAX_WAIT_MS must cover a cold RingConn start plus BLE sleep sync",
            MorningSyncAwaitPolicy.MAX_WAIT_MS >= 15_000L
        )
        assertTrue(
            "MAX_WAIT_MS must stay bounded so the peek cannot hijack the phone",
            MorningSyncAwaitPolicy.MAX_WAIT_MS <= 45_000L
        )
        assertTrue(
            "POLL_INTERVAL_MS must be a real poll interval",
            MorningSyncAwaitPolicy.POLL_INTERVAL_MS in 500L..3_000L
        )
        assertTrue(
            "the cap must allow several polls",
            MorningSyncAwaitPolicy.MAX_WAIT_MS / MorningSyncAwaitPolicy.POLL_INTERVAL_MS >= 5L
        )
    }

    @Test
    fun doesNotScrollBeforeTheFirstScrape() {
        assertFalse(MorningSyncAwaitPolicy.shouldScroll(0))
    }

    @Test
    fun scrollsEveryNthAttemptToRevealLowerCards() {
        val n = MorningSyncAwaitPolicy.SCROLL_EVERY_N_ATTEMPTS
        assertTrue("n must be positive", n > 0)
        assertFalse(MorningSyncAwaitPolicy.shouldScroll(1))
        assertTrue(MorningSyncAwaitPolicy.shouldScroll(n))
        assertFalse(MorningSyncAwaitPolicy.shouldScroll(n + 1))
        assertTrue(MorningSyncAwaitPolicy.shouldScroll(n * 2))
    }

    @Test
    fun settleTimeGivesTheFlutterSemanticsTreeAFrame() {
        assertTrue(MorningSyncAwaitPolicy.MIN_SETTLE_MS >= 500L)
        assertTrue(MorningSyncAwaitPolicy.MIN_SETTLE_MS < MorningSyncAwaitPolicy.MAX_WAIT_MS)
    }
}
