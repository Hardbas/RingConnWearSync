package io.github.hardbas.wearsync.bridge.service

/**
 * Battery budget for the phone bridge.
 *
 * Two knobs used to be fixed constants chosen for latency alone:
 *
 *  - the `AlarmManager` watchdog that resurrects the bridge service after One UI kills it, and
 *  - the heartbeat that re-sends vitals to the watch even when nothing changed.
 *
 * Both are recovery mechanisms, not data sources: the wake lock they take exists to re-read the
 * RingConn widget, which the official app itself only refreshes every 15-30 minutes. Shortening
 * them below that cadence cannot make data fresher, it only burns battery.
 *
 * The morning window is deliberately kept fast: the "Smart Unlock Peek" needs the service alive at
 * the first unlock of the day, so the recovery interval must not be relaxed there.
 */
internal object SyncBatteryPolicy {

    /** Watchdog interval for most of the day, once the morning peek is no longer relevant. */
    const val WATCHDOG_INTERVAL_MS = 30 * 60 * 1000L

    /** Watchdog interval while the morning peek can still be triggered. */
    const val WATCHDOG_MORNING_INTERVAL_MS = 15 * 60 * 1000L

    /** First hour (inclusive) covered by the fast watchdog. */
    const val MORNING_WINDOW_START_HOUR = 4

    /** Last hour (exclusive) covered by the fast watchdog. */
    const val MORNING_WINDOW_END_HOUR = 13

    /** Re-send vitals to the watch even if the metric signature did not change. */
    const val HEARTBEAT_INTERVAL_MS = 15 * 60 * 1000L

    /**
     * In-service poll loop: re-reads the RingConn widget and pushes on signature change.
     * The widget itself only refreshes every 15-30 minutes, so a 2-minute poll spent a wake lock
     * on data that had not changed; 5 minutes keeps the same freshness for a third of the cost.
     */
    const val IN_SERVICE_POLL_INTERVAL_MS = 5 * 60 * 1000L

    /**
     * In-service poll interval while the wearer is expected to be asleep.
     *
     * Sleep is consolidated by the ring and read once the night ends; a five-minute poll between
     * 23:00 and 06:00 cannot observe anything new, it only holds a wake lock on a phone that is
     * otherwise idle. At 150 minutes the whole night costs three wake cycles.
     */
    const val IN_SERVICE_POLL_NIGHT_INTERVAL_MS = 150 * 60 * 1000L

    /** First hour (inclusive) of the quiet window, which wraps past midnight. */
    const val NIGHT_WINDOW_START_HOUR = 23

    /** First hour (exclusive) after the quiet window. */
    const val NIGHT_WINDOW_END_HOUR = 6

    /** Hours covered by the quiet window, crossing midnight. */
    const val NIGHT_WINDOW_HOURS = (24 - NIGHT_WINDOW_START_HOUR) + NIGHT_WINDOW_END_HOUR

    fun watchdogIntervalMs(hourOfDay: Int): Long =
        if (isMorningWindow(hourOfDay)) WATCHDOG_MORNING_INTERVAL_MS else WATCHDOG_INTERVAL_MS

    /**
     * Poll interval for the given hour: the quiet interval overnight, the day interval otherwise.
     * An hour outside 0..23 is a programming error, not a night, so it falls back to the day one.
     */
    fun pollIntervalMs(hourOfDay: Int): Long =
        if (isNightWindow(hourOfDay)) IN_SERVICE_POLL_NIGHT_INTERVAL_MS else IN_SERVICE_POLL_INTERVAL_MS

    /** True between [NIGHT_WINDOW_START_HOUR] and [NIGHT_WINDOW_END_HOUR], wrapping midnight. */
    fun isNightWindow(hourOfDay: Int): Boolean {
        if (hourOfDay !in 0..23) return false
        return hourOfDay >= NIGHT_WINDOW_START_HOUR || hourOfDay < NIGHT_WINDOW_END_HOUR
    }

    fun isMorningWindow(hourOfDay: Int): Boolean =
        hourOfDay in MORNING_WINDOW_START_HOUR until MORNING_WINDOW_END_HOUR
}
