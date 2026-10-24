package io.github.hardbas.wearsync.app.ui

import io.github.hardbas.wearsync.app.WearSources

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source audit: the stress detail screen must not draw blocks the ring never
 * measured.
 *
 * The screen used to synthesise a twenty-four bar curve from an hour formula and
 * printed three fixed percentages ("74%", "26%", "64") for every user regardless
 * of their data. Layout tests cannot catch that, because the layout was valid —
 * only the numbers were invented. This audit pins the absence at the source level
 * and fails if the shapes come back.
 */
class NoSyntheticStressDataTest {

    private val source = WearSources.file(
        "src/main/java/io/github/hardbas/wearsync/app/ui/StressDetailActivity.kt"
    )
    private val body: String = run {
        assertTrue("cannot locate $source; the audit must run from the module", source.isFile)
        source.readText()
    }

    @Test
    fun `draws the measured circadian blocks instead of an hourly formula`() {
        assertTrue(
            "the timeline must be driven by the four measured blocks",
            body.contains("StressPhases.readings(")
        )
        assertTrue(
            "a day with no measured block must fall back to the empty state",
            body.contains("StressTimelineEmptyCard(")
        )
    }

    @Test
    fun `contains no synthetic hourly series`() {
        assertAbsent("hour formula building a fake curve", "(12 + (hour * 2)")
        assertAbsent("a hardcoded block count driving the axis", "List(24)")
        assertAbsent("hourly axis labels over data that does not exist", "text = \"0h\"")
    }

    @Test
    fun `contains no hardcoded stress percentages`() {
        assertAbsent("fixed calm percentage", "text = \"74%\"")
        assertAbsent("fixed tension percentage", "text = \"26%\"")
        assertAbsent("fixed peak value", "text = \"64\"")
    }

    private fun assertAbsent(what: String, needle: String) {
        assertTrue(
            "$source still contains $what (`$needle`); report the measured value or show no data instead",
            !body.contains(needle)
        )
    }
}
