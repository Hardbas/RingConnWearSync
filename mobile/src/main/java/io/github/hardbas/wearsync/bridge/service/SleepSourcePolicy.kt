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

    /**
     * The stage minutes to persist, given who is asking.
     *
     * Six places persist vitals; only the Health Connect read and the policy-merged write
     * resolve stages properly. The accessibility scrape and the widget reader persist what
     * they parsed from the official app's interface and run last, so without this rule they
     * overwrite a correct night with their own numbers — the reported case was deep 99 and
     * rem 104 replacing 55 and 60.
     *
     * A non-authoritative writer therefore keeps whatever is already resolved, unless nothing
     * is persisted yet: on a device whose Health Connect read has not run, the widget is the
     * only source and dropping it would leave the screen empty.
     */
    fun stagesForWrite(
        incoming: StageMinutes,
        persisted: StageMinutes,
        authoritative: Boolean
    ): StageMinutes {
        if (authoritative) {
            return if (incoming.asleepMinutes > 0) incoming else persisted
        }
        return if (persisted.asleepMinutes > 0) persisted else incoming
    }
}
