package io.github.hardbas.wearsync.bridge.service

/**
 * Merges Health Connect sleep segments onto a fixed timeline of epochs.
 *
 * RingConn writes two kinds of records for the same night: a coarse segment that
 * spans most of it, and short, specific segments for deep, REM, awake and
 * out-of-bed. They overlap, so a rule is needed to decide which one owns an epoch.
 *
 * That rule used to be "the most recently modified segment wins". It is wrong:
 * when RingConn rewrites the coarse segment after syncing, the coarse segment
 * carries the newest timestamp and overwrites every specific one, so the whole
 * night collapses into a single stage. The reader then reported healthy deep and
 * REM stages as zero while Health Connect held the correct data.
 *
 * The rule now is specificity first:
 *
 *  1. A shorter segment beats a longer one that overlaps it.
 *  2. Recency only breaks ties between segments of equal length.
 *  3. Arrival order never changes the result.
 *
 * Kept free of Android types so the rule can be tested on the JVM.
 */
internal object SleepStageMerger {

    /** One segment of a night, in epoch-milliseconds. */
    data class Interval(
        val startMs: Long,
        val endMs: Long,
        val stageType: Int,
        val lastModifiedMs: Long = 0L
    ) {
        val durationMs: Long get() = endMs - startMs
    }

    /**
     * Applies [intervals] to a timeline of [epochCount] epochs of [epochMs]
     * starting at [timelineStartMs]. Epochs no segment covers stay 0.
     */
    fun merge(
        intervals: List<Interval>,
        timelineStartMs: Long,
        epochCount: Int,
        epochMs: Long = 30_000L
    ): IntArray {
        val timeline = IntArray(epochCount) { 0 }
        if (epochCount <= 0 || epochMs <= 0 || intervals.isEmpty()) return timeline

        // Longest first so the most specific segment is written last and wins;
        // among equals, oldest first so a newer correction lands on top.
        val ordered = intervals.sortedWith(
            compareByDescending<Interval> { it.durationMs }
                .thenBy { it.lastModifiedMs }
        )

        for (interval in ordered) {
            val startEpoch = ((interval.startMs - timelineStartMs) / epochMs).toInt()
                .coerceIn(0, epochCount - 1)
            val endEpoch = (((interval.endMs - timelineStartMs) + epochMs / 2) / epochMs).toInt()
                .coerceIn(0, epochCount)

            for (epoch in startEpoch until endEpoch) {
                timeline[epoch] = interval.stageType
            }
        }
        return timeline
    }

    /** Minutes covered by the given [stageType] across the timeline. */
    fun minutesOf(timeline: IntArray, stageType: Int, epochMs: Long = 30_000L): Long =
        (timeline.count { it == stageType } * epochMs / 60_000L)
}
