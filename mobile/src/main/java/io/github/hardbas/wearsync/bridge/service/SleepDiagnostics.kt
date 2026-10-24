package io.github.hardbas.wearsync.bridge.service

/**
 * Builds the in-app sleep diagnostic report.
 *
 * Reported from hardware: Health Connect holds the night, the watch still shows
 * something else, and Health Connect itself groups that night under "Yesterday".
 * The reader keeps only sessions whose `endTime` falls after today's midnight, so a
 * night recorded with yesterday's date is invisible to it and the stage policy falls
 * back to the cache — which explains a watch showing numbers that no current source
 * holds.
 *
 * Rather than guess again, the app can now say what it sees. The formatting and the
 * window decision are pure so they can be tested; only the query needs Android.
 */
internal object SleepDiagnostics {

    /**
     * One sleep session as Health Connect returned it.
     *
     * [stageTypes] carries the raw stage ids, not just how many there are: the reader only
     * maps five of them, so a fragment whose single stage is an unhandled type is silently
     * dropped by the merge and the night can end up empty while the data is right there.
     */
    data class Session(
        val startMs: Long,
        val endMs: Long,
        val stageCount: Int,
        val stageTypes: List<Int> = emptyList()
    ) {
        fun durationMinutes(): Long = (endMs - startMs) / 60_000L
    }

    data class Report(
        val nowMs: Long,
        val todayStartMs: Long,
        val sessions: List<Session>,
        val chosenStages: String,
        val chosenSource: String
    )

    /** A session counts for "today" only when it ended after today's midnight. */
    fun countedToday(session: Session, todayStartMs: Long): Boolean = session.endMs >= todayStartMs

    fun countedTodayCount(sessions: List<Session>, todayStartMs: Long): Int =
        sessions.count { countedToday(it, todayStartMs) }

    fun format(report: Report): String {
        val countable = report.sessions.filter { countedToday(it, report.todayStartMs) }
        val build = StringBuilder()

        build.appendLine("nowMillis=${report.nowMs}")
        build.appendLine("todayStartMillis=${report.todayStartMs}")
        build.appendLine("sessionsInWindow=${report.sessions.size}")
        build.appendLine("countedForToday=${countable.size}")
        build.appendLine()

        if (report.sessions.isEmpty()) {
            build.appendLine("Health Connect returned no sleep session in the last 36h.")
            build.appendLine("If the official app shows the night, the query permission is the suspect.")
        } else {
            report.sessions.forEachIndexed { index, session ->
                val verdict = if (countedToday(session, report.todayStartMs)) "COUNTED" else "EXCLUDED (ended before today)"
                build.appendLine("session[$index] start=${session.startMs} end=${session.endMs}")
                build.appendLine(
                    "           durationMin=${session.durationMinutes()} stages=${session.stageCount} " +
                        "types=${session.stageTypes} -> $verdict"
                )
            }
        }

        build.appendLine()
        val longest = report.sessions.maxByOrNull { it.durationMinutes() }
        build.appendLine("longestSessionMin=${longest?.durationMinutes() ?: 0} types=${longest?.stageTypes ?: emptyList<Int>()}")
        val distinctTypes = report.sessions.flatMap { it.stageTypes }.distinct().sorted()
        build.appendLine("distinctStageTypes=$distinctTypes")
        build.appendLine("chosenStages=${report.chosenStages}")
        build.appendLine("chosenSource=${report.chosenSource}")
        build.appendLine()
        build.appendLine(
            "If every session says EXCLUDED, the night is dated yesterday and the reader " +
                "cannot see it: that is the bug, not the data."
        )
        return build.toString()
    }
}
