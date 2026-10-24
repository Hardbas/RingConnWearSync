package io.github.hardbas.wearsync.bridge

import io.github.hardbas.wearsync.bridge.service.SleepStageMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reproduction with the exact intervals reported from a real device.
 *
 * Health Connect returned 29 fragments for one night: two DEEP (35 + 20 min), six REM
 * (5+5+5+10+5+30), thirteen LIGHT (249 min total) and seven AWAKE (60 min), all inside an
 * umbrella SLEEPING segment of 390 min. The app persisted deep=99 rem=104 light=247 awake=0
 * on two consecutive syncs, so the numbers come from this code, not from a stale cache.
 *
 * A union of intervals can never exceed their sum, so 99 and 104 are impossible for inputs
 * that add up to 55 and 60. This test asserts what the merge must return.
 */
class SleepMergeReproTest {

    private val deep = 5
    private val rem = 6
    private val light = 4
    private val awake = 1

    // Timeline exactly as the reader computes it: first fragment start, 30 s epochs.
    private val tStart = 1792819877000L
    private val epochCount = 810

    private fun i(start: Long, end: Long, type: Int) =
        SleepStageMerger.Interval(startMs = start, endMs = end, stageType = type)

    private val fragments = listOf(
        // AWAKE, 60 min
        i(1792818677000, 1792819877000, awake),
        i(1792820177000, 1792820477000, awake),
        i(1792825577000, 1792825877000, awake),
        i(1792829477000, 1792829777000, awake),
        i(1792838477000, 1792838777000, awake),
        i(1792841180000, 1792841480000, awake),
        i(1792843277000, 1792844180000, awake),
        // LIGHT, 249 min
        i(1792819877000, 1792820177000, light),
        i(1792820477000, 1792820777000, light),
        i(1792822877000, 1792823177000, light),
        i(1792823477000, 1792824077000, light),
        i(1792825277000, 1792825577000, light),
        i(1792825877000, 1792826777000, light),
        i(1792827077000, 1792827977000, light),
        i(1792828277000, 1792829477000, light),
        i(1792829777000, 1792833677000, light),
        i(1792834277000, 1792835477000, light),
        i(1792835777000, 1792838477000, light),
        i(1792838777000, 1792839377000, light),
        i(1792841480000, 1792843277000, light),
        // DEEP, 55 min
        i(1792820777000, 1792822877000, deep),
        i(1792824077000, 1792825277000, deep),
        // REM, 60 min
        i(1792823177000, 1792823477000, rem),
        i(1792826777000, 1792827077000, rem),
        i(1792827977000, 1792828277000, rem),
        i(1792833677000, 1792834277000, rem),
        i(1792835477000, 1792835777000, rem),
        i(1792839377000, 1792841180000, rem)
    )

    @Test
    fun theMergeCannotExceedTheSumOfItsInputs() {
        val timeline = SleepStageMerger.merge(fragments, tStart, epochCount)

        val deepMin = SleepStageMerger.minutesOf(timeline, deep)
        val remMin = SleepStageMerger.minutesOf(timeline, rem)
        val lightMin = SleepStageMerger.minutesOf(timeline, light)
        val awakeMin = SleepStageMerger.minutesOf(timeline, awake)

        assertEquals("deep comes from 35 + 20 minutes", 55L, deepMin)
        assertEquals("rem comes from 5+5+5+10+5+30 minutes", 60L, remMin)
        // 30-second epochs round each fragment edge, so a minute of slack is expected; the
        // numbers that matter are deep and rem, which double in the reported bug.
        assertTrue(
            "light comes from 249 minutes of fragments, ±1 for epoch rounding",
            lightMin in 248L..250L
        )
        // The reader drops fragments that conclude at or before the umbrella start
        // (`it.end > umbrellaStart`), and the 20 minute awakening at 1792818677 ends exactly at
        // 1792819877 — the umbrella start. So 40 of the 60 awake minutes reach the merge.
        assertEquals("awake loses the fragment that ends at the umbrella start", 40L, awakeMin)
    }
}
