package io.github.hardbas.wearsync.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source audit: the vitals screen must never persist a heart rate it did not
 * receive from the ring.
 *
 * The "measure now" entry point used to wait a fixed delay and then save the
 * previous reading plus a random offset, stamped with a fresh timestamp, so a
 * made-up number was indistinguishable from a measured one. A layout test cannot
 * catch that. This audit pins it at the source level: the screen may route a
 * measurement request to the phone, but it may not synthesise the result.
 */
class NoSyntheticHeartRateTest {

    private val source = File("src/main/java/io/github/hardbas/wearsync/app/ui/VitalsDetailActivity.kt")
    private val body = source.readText()

    @Test
    fun `never derives a heart rate from the previous one`() {
        assertAbsent("a random offset added to the last reading", "(-2..3).random()")
        assertAbsent("arithmetic on the previous heart rate", "lastHeartRate +")
    }

    @Test
    fun `asks the phone for a real measurement instead`() {
        assertTrue(
            "a measurement request must be routed to the phone",
            body.contains("/action/sync_ringconn")
        )
        assertTrue(
            "the screen must wait for the real sync signal, not a fixed delay",
            body.contains("lastSyncCompletedMs")
        )
    }

    private fun assertAbsent(what: String, needle: String) {
        assertTrue(
            "$source still contains $what (`$needle`); persist only values that came from the ring",
            !body.contains(needle)
        )
    }
}
