package io.github.hardbas.wearsync.bridge

import io.github.hardbas.wearsync.bridge.service.SleepSourcePolicy
import io.github.hardbas.wearsync.bridge.service.SleepSourcePolicy.StageMinutes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Who is allowed to overwrite the persisted stage minutes.
 *
 * Six places persist vitals, and only the Health Connect read and the policy-merged
 * write resolve stages properly. The accessibility scrape and the widget reader persist
 * whatever they parsed from the official app's interface — numbers that never go through
 * `SleepSourcePolicy` — and they run last, so the watch ends up showing those.
 *
 * That is why a reported night displayed deep 99 / rem 104 / awake 0 while Health Connect
 * held 55 / 60 / 60 and the merge demonstrably produces the latter.
 */
class SleepWritePolicyTest {

    private val fromHealthConnect = StageMinutes(deep = 55, rem = 60, light = 249, awake = 40)
    private val fromWidget = StageMinutes(deep = 99, rem = 104, light = 247, awake = 0)

    @Test
    fun aScrapeOrWidgetWriteKeepsTheStagesAnotherSourceAlreadyResolved() {
        val written = SleepSourcePolicy.stagesForWrite(
            incoming = fromWidget,
            persisted = fromHealthConnect,
            authoritative = false
        )

        assertEquals("a UI scrape must not overwrite resolved stages", fromHealthConnect, written)
    }

    @Test
    fun theHealthConnectWriteOwnsTheStages() {
        val written = SleepSourcePolicy.stagesForWrite(
            incoming = fromHealthConnect,
            persisted = fromWidget,
            authoritative = true
        )

        assertEquals(fromHealthConnect, written)
    }

    @Test
    fun theFirstWriteFromAScrapeStillContributesWhenNothingIsPersistedYet() {
        // Otherwise a device whose Health Connect read has not run yet would show no stages at
        // all, and the widget is the only source available at that moment.
        val written = SleepSourcePolicy.stagesForWrite(
            incoming = fromWidget,
            persisted = StageMinutes(0, 0, 0, 0),
            authoritative = false
        )

        assertEquals(fromWidget, written)
    }

    @Test
    fun anAuthoritativeWriteWithNoNightDoesNotBlankWhatIsThere() {
        val written = SleepSourcePolicy.stagesForWrite(
            incoming = StageMinutes(0, 0, 0, 0),
            persisted = fromHealthConnect,
            authoritative = true
        )

        assertEquals(
            "an empty Health Connect read is not a night; keep the last real one",
            fromHealthConnect,
            written
        )
    }
}
