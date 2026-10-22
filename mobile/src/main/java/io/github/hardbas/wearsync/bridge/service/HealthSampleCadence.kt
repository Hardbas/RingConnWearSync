package io.github.hardbas.wearsync.bridge.service

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * How much time one Health Connect sample stands for.
 *
 * Health Connect hands back discrete samples, not continuous coverage. Turning
 * a count of samples into minutes — heart-rate zone minutes, for instance —
 * needs the spacing between them, and that spacing belongs to the source, not
 * to us: a ring that writes every minute and one that writes every five
 * produce the same count for very different durations.
 *
 * The median gap is used instead of the mean so a single sync gap (a night
 * without the ring, a paused sensor) cannot drag the whole estimate.
 */
object HealthSampleCadence {

    /** Assumed spacing when the series is too short to measure one. */
    const val DEFAULT_WINDOW_MINUTES = 5

    /** A gap below one minute still counts as one minute of coverage. */
    const val MIN_WINDOW_MINUTES = 1

    /** Beyond this the series is spot checks, not coverage. */
    const val MAX_WINDOW_MINUTES = 15

    /**
     * Minutes each sample represents, from the median gap between samples.
     *
     * @param timestamps the sample times, in any order; duplicates are ignored.
     * @return [DEFAULT_WINDOW_MINUTES] when there are fewer than two distinct
     *   timestamps, otherwise the median gap rounded to the nearest minute and
     *   clamped to [MIN_WINDOW_MINUTES]..[MAX_WINDOW_MINUTES].
     */
    fun windowMinutes(timestamps: List<Instant>): Int {
        val gaps = gapsSeconds(timestamps)
        if (gaps.isEmpty()) return DEFAULT_WINDOW_MINUTES
        val minutes = (medianSeconds(gaps) / 60.0).roundToInt()
        return minutes.coerceIn(MIN_WINDOW_MINUTES, MAX_WINDOW_MINUTES)
    }

    /** Gaps between consecutive distinct timestamps, in seconds. */
    private fun gapsSeconds(timestamps: List<Instant>): List<Long> {
        val sorted = timestamps.distinct().sorted()
        if (sorted.size < 2) return emptyList()
        return sorted.zipWithNext { earlier, later -> Duration.between(earlier, later).seconds }
    }

    /** Median of the gaps; for an even count, the mean of the two middle ones. */
    private fun medianSeconds(gaps: List<Long>): Long {
        val sorted = gaps.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2
        }
    }
}
