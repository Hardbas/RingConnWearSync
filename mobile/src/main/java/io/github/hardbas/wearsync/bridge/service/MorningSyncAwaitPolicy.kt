package io.github.hardbas.wearsync.bridge.service

import io.github.hardbas.wearsync.bridge.data.RingConnVitals

/**
 * Timing policy for the morning "Smart Unlock Peek".
 *
 * RingConn is a Flutter app: on a cold start it has to resume its Flutter engine,
 * re-attach the BLE central and pull the night's sleep record from the ring
 * before the sleep card is rendered. A single fixed delay is always wrong:
 * too short and the card is still a `--` placeholder, so the peek synced a watch
 * tile with no sleep at all (the bug this policy replaces), too long and the
 * peek holds the phone's foreground for nothing.
 *
 * The policy is a pure function of elapsed time and the last scrape result so it
 * can be unit tested without an AccessibilityService.
 */
internal object MorningSyncAwaitPolicy {

    /** Hard cap on how long the peek may keep RingConn in the foreground. */
    const val MAX_WAIT_MS = 24_000L

    /** Delay between two consecutive scrapes of the semantics tree. */
    const val POLL_INTERVAL_MS = 1_200L

    /** Grace period after launch so Flutter can present its first frame. */
    const val MIN_SETTLE_MS = 2_000L

    /** Window during which RingConn may still be taking over the screen after launch. */
    const val LAUNCH_GRACE_MS = 6_000L

    /** Every Nth poll also performs a gentle scroll, to reveal lower cards. */
    const val SCROLL_EVERY_N_ATTEMPTS = 4

    /** Sleep is what the morning peek exists for: no score means "keep waiting". */
    fun isReady(vitals: RingConnVitals?): Boolean = vitals != null && vitals.sleepScore > 0

    /**
     * Continue while sleep is still missing, the hard cap has not elapsed, and RingConn
     * is still the app the user is looking at (or is still taking the screen over).
     *
     * @param ringConnForeground false as soon as the user navigates away, which aborts
     *   the peek instead of hijacking the phone until the cap expires.
     */
    fun shouldKeepPolling(
        elapsedMs: Long,
        vitals: RingConnVitals?,
        ringConnForeground: Boolean = true
    ): Boolean =
        elapsedMs < MAX_WAIT_MS &&
            !isReady(vitals) &&
            (ringConnForeground || elapsedMs <= LAUNCH_GRACE_MS)

    /** Scroll only after the first scrape, and not on every poll. */
    fun shouldScroll(attempt: Int): Boolean =
        attempt > 0 && attempt % SCROLL_EVERY_N_ATTEMPTS == 0
}
