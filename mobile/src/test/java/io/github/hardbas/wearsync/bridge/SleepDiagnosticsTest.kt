package io.github.hardbas.wearsync.bridge

import io.github.hardbas.wearsync.bridge.service.SleepDiagnostics
import io.github.hardbas.wearsync.bridge.service.SleepDiagnostics.Session
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The diagnostic must answer one question unambiguously: is the night excluded by
 * the "ended after today's midnight" rule, or is it simply not there?
 *
 * Everything else in the report is context; this rule is the one that decides
 * whether the watch is falling back to the cache.
 */
class SleepDiagnosticsTest {

    private val hourMs = 60 * 60 * 1000L
    private val todayStart = 20_000L * hourMs

    @Test
    fun aNightThatEndedBeforeMidnightIsExcluded() {
        // Slept 23:00 -> 07:00, but the record is dated the previous day.
        val night = Session(startMs = todayStart - 5 * hourMs, endMs = todayStart - 1 * hourMs, stageCount = 4)

        assertFalse(
            "a night that ended before today's midnight cannot count for today",
            SleepDiagnostics.countedToday(night, todayStart)
        )
    }

    @Test
    fun aNightThatEndedAfterMidnightCounts() {
        val night = Session(startMs = todayStart - 5 * hourMs, endMs = todayStart + 7 * hourMs, stageCount = 4)

        assertTrue(SleepDiagnostics.countedToday(night, todayStart))
    }

    @Test
    fun theReportSaysExcludedInTheTextSoAUserCanPasteIt() {
        val excluded = Session(startMs = todayStart - 5 * hourMs, endMs = todayStart - hourMs, stageCount = 0)
        val counted = Session(startMs = todayStart - 6 * hourMs, endMs = todayStart + 2 * hourMs, stageCount = 4)

        val text = SleepDiagnostics.format(
            SleepDiagnostics.Report(
                nowMs = todayStart + 9 * hourMs,
                todayStartMs = todayStart,
                sessions = listOf(excluded, counted),
                chosenStages = "deep=60 rem=70 light=200 awake=12",
                chosenSource = "cache"
            )
        )

        assertTrue(text.contains("sessionsInWindow=2"))
        assertTrue(text.contains("countedForToday=1"))
        assertTrue(text.contains("EXCLUDED"))
        assertTrue(text.contains("chosenSource=cache"))
    }

    @Test
    fun anEmptyResultPointsAtThePermissionInsteadOfSilence() {
        val text = SleepDiagnostics.format(
            SleepDiagnostics.Report(
                nowMs = todayStart + 9 * hourMs,
                todayStartMs = todayStart,
                sessions = emptyList(),
                chosenStages = "deep=0 rem=0 light=0 awake=0",
                chosenSource = "empty"
            )
        )

        assertTrue(text.contains("no sleep session"))
        assertTrue("an empty result must name the permission as the suspect", text.contains("permission"))
    }

    @Test
    fun theCountedCountMatchesTheVerdictsInTheText() {
        val sessions = listOf(
            Session(todayStart - 5 * hourMs, todayStart - hourMs, 0),
            Session(todayStart - 3 * hourMs, todayStart + 4 * hourMs, 4),
            Session(todayStart + hourMs, todayStart + 3 * hourMs, 2)
        )

        val counted = SleepDiagnostics.countedTodayCount(sessions, todayStart)

        assertTrue("two of the three ended after midnight", counted == 2)
    }

    @Test
    fun theReportPrintsTheStageTypesBecauseTheMergeOnlyMapsFive() {
        // Reported from hardware: every session carried exactly one stage, so the report had
        // to say WHICH stage. A fragment whose type is not one of the five the reader maps is
        // silently dropped, which empties the night while the data is present.
        val umbrella = Session(todayStart, todayStart + 390 * minuteMs, 1, listOf(2))
        val deep = Session(todayStart + 30 * minuteMs, todayStart + 60 * minuteMs, 1, listOf(5))

        val text = SleepDiagnostics.format(
            SleepDiagnostics.Report(
                nowMs = todayStart + 8 * hourMs,
                todayStartMs = todayStart,
                sessions = listOf(umbrella, deep),
                chosenStages = "n/a",
                chosenSource = "n/a"
            )
        )

        assertTrue("the type must be visible per session", text.contains("types=[2]"))
        assertTrue(text.contains("types=[5]"))
        assertTrue("the longest session is the night anchor", text.contains("longestSessionMin=390"))
        assertTrue("distinct types answer 'did anything usable arrive'", text.contains("distinctStageTypes=[2, 5]"))
    }

    private val minuteMs = 60_000L
}
