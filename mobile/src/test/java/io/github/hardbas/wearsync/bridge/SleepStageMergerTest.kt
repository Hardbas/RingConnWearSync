package io.github.hardbas.wearsync.bridge

import io.github.hardbas.wearsync.bridge.service.SleepStageMerger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract for merging Health Connect sleep segments onto a timeline.
 *
 * RingConn writes a coarse segment that spans the night alongside short, specific
 * segments (deep, REM, awake). The previous merge resolved overlaps by
 * `lastModified`, so when the coarse segment carried the newest timestamp it
 * overwrote the specific ones and every stage collapsed into a single value — the
 * night read as "all wrong" even though Health Connect held the right data.
 *
 * The rule now: the more specific segment wins, and arrival order cannot change
 * the result. Recency only breaks ties between equally specific segments.
 *
 * Stage ids are arbitrary distinct integers here; the reader passes the real
 * Health Connect constants, which are not available on a plain JVM test.
 */
class SleepStageMergerTest {

    private val light = 2
    private val deep = 4
    private val awake = 5

    private val minuteMs = 60_000L
    private fun t(minutes: Long) = minutes * minuteMs

    private val epochMs = 30 * minuteMs

    @Test
    fun aCoarseSegmentNeverOverwritesASpecificOneInsideIt() {
        // The coarse segment is the newest, which is what used to win.
        val coarse = SleepStageMerger.Interval(
            startMs = t(0), endMs = t(240), stageType = light, lastModifiedMs = 9_000L
        )
        val specific = SleepStageMerger.Interval(
            startMs = t(60), endMs = t(90), stageType = deep, lastModifiedMs = 1_000L
        )

        val timeline = SleepStageMerger.merge(
            intervals = listOf(coarse, specific),
            timelineStartMs = t(0),
            epochCount = 8,
            epochMs = epochMs
        )

        assertEquals("epoch 2 (60-90m) must stay deep", deep, timeline[2])
        assertEquals("epoch 1 (30-60m) is the coarse stage", light, timeline[1])
        assertEquals("epoch 3 (90-120m) is the coarse stage", light, timeline[3])
    }

    @Test
    fun arrivalOrderCannotChangeTheResult() {
        val coarse = SleepStageMerger.Interval(t(0), t(240), light, 9_000L)
        val specific = SleepStageMerger.Interval(t(60), t(90), deep, 1_000L)

        val forward = SleepStageMerger.merge(listOf(coarse, specific), t(0), 8, epochMs)
        val reversed = SleepStageMerger.merge(listOf(specific, coarse), t(0), 8, epochMs)

        assertArrayEquals("the same segments must produce the same night", forward, reversed)
    }

    @Test
    fun recencyBreaksTiesBetweenEquallySpecificSegments() {
        val older = SleepStageMerger.Interval(t(0), t(30), light, 1_000L)
        val newer = SleepStageMerger.Interval(t(0), t(30), awake, 5_000L)

        val timeline = SleepStageMerger.merge(listOf(newer, older), t(0), 2, epochMs)

        assertEquals("equal length, so the newer correction wins", awake, timeline[0])
    }

    @Test
    fun epochsNoSegmentCoversStayEmpty() {
        val onlyLater = SleepStageMerger.Interval(t(60), t(90), deep, 1_000L)

        val timeline = SleepStageMerger.merge(listOf(onlyLater), t(0), 8, epochMs)

        assertEquals("before any segment", 0, timeline[0])
        assertEquals("the covered epoch", deep, timeline[2])
        assertEquals("after the segment", 0, timeline[6])
    }

    @Test
    fun aSegmentShorterThanAnEpochStillClaimsItsEpoch() {
        // A ten minute awakening inside an epoch must not disappear.
        val coarse = SleepStageMerger.Interval(t(0), t(120), light, 1_000L)
        val brief = SleepStageMerger.Interval(t(20), t(30), awake, 500L)

        val timeline = SleepStageMerger.merge(listOf(coarse, brief), t(0), 4, epochMs)

        assertEquals("the brief awakening owns epoch 0", awake, timeline[0])
    }
}
