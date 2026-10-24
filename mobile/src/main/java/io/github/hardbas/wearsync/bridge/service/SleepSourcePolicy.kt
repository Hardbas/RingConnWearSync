package io.github.hardbas.wearsync.bridge.service

/**
 * Decides which source owns the sleep stage minutes of a night.
 *
 * The reader used to take the source that declared the most sleep — the widget when
 * it had the largest total, then the cache, then Health Connect. That rule is
 * self-reinforcing: a cache left inflated by an earlier parsing bug outbids the
 * corrected Health Connect record every time, so the watch keeps showing numbers
 * that no source currently holds.
 *
 * The rule is about capability instead of size. Health Connect is the only source
 * with per-stage records, so it owns the night whenever it has one; the cache covers
 * a night Health Connect has not received yet; and no field is ever filled from a
 * different source, because a night assembled from two sources matches neither.
 */
internal object SleepSourcePolicy {

    data class StageMinutes(
        val deep: Int,
        val rem: Int,
        val light: Int,
        val awake: Int
    ) {
        val asleepMinutes: Int get() = deep + rem + light
    }

    private val EMPTY_NIGHT = StageMinutes(deep = 0, rem = 0, light = 0, awake = 0)

    /**
     * Health Connect when it holds a night for this date, the cache otherwise, and an
     * empty night when neither does. A source with no asleep minutes is not a night.
     */
    fun stages(hc: StageMinutes?, cached: StageMinutes?): StageMinutes {
        if (hc != null && hc.asleepMinutes > 0) return hc
        if (cached != null && cached.asleepMinutes > 0) return cached
        return EMPTY_NIGHT
    }
}
