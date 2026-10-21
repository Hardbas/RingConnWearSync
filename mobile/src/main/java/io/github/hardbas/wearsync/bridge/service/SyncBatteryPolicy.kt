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

    fun watchdogIntervalMs(hourOfDay: Int): Long =
        if (isMorningWindow(hourOfDay)) WATCHDOG_MORNING_INTERVAL_MS else WATCHDOG_INTERVAL_MS

    fun isMorningWindow(hourOfDay: Int): Boolean =
        hourOfDay in MORNING_WINDOW_START_HOUR until MORNING_WINDOW_END_HOUR
}
