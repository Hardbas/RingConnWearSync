package io.github.hardbas.wearsync.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for deciding whether a measurement attempt actually refreshed the
 * reading on screen.
 *
 * A sync completion signal is not proof on its own: another sync can land while
 * the watch waits, and crediting that to the button the user just tapped is the
 * same class of lie as inventing the number. A refresh needs both facts — the
 * round trip we asked for completed, and the reading is newer than it was when
 * the request started.
 */
class MeasureOutcomeTest {

    @Test
    fun `reports a refresh only when the signal arrived and the reading got newer`() {
        assertTrue(MeasureOutcome.refreshed(before = 1_000, after = 1_004, syncSignalSeen = true))
    }

    @Test
    fun `rejects a completed signal that did not advance the reading`() {
        assertFalse(
            "a sync we did not cause must not count as our measurement",
            MeasureOutcome.refreshed(before = 1_000, after = 1_000, syncSignalSeen = true)
        )
    }

    @Test
    fun `rejects a newer reading when no signal was observed`() {
        assertFalse(
            "the reading must come from the round trip this attempt started",
            MeasureOutcome.refreshed(before = 1_000, after = 1_004, syncSignalSeen = false)
        )
    }

    @Test
    fun `rejects an older reading`() {
        assertFalse(MeasureOutcome.refreshed(before = 1_004, after = 1_000, syncSignalSeen = true))
    }
}
