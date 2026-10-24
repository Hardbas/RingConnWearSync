package io.github.hardbas.wearsync.bridge

import io.github.hardbas.wearsync.bridge.service.SleepSourcePolicy
import io.github.hardbas.wearsync.bridge.service.SleepSourcePolicy.StageMinutes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which source owns the sleep stage minutes for a night.
 *
 * The reader used to pick the source that claimed the most sleep: widget if it had
 * the largest total, then the cache, then Health Connect. That rule is
 * self-reinforcing — a cache left inflated by an earlier bug outbids the corrected
 * Health Connect record forever, which is exactly what happened after the stage
 * merge was fixed: Health Connect was right and the watch still showed the old
 * numbers.
 *
 * The policy is now about what a source can actually provide, not about who claims
 * more: Health Connect is the only source with per-stage records, so it owns the
 * night whenever it has one. The cache is a fallback for a night Health Connect has
 * not received yet, and the widget is never consulted for stages because it does
 * not carry them.
 */
class SleepSourcePolicyTest {

    private fun stages(deep: Int, rem: Int, light: Int, awake: Int) =
        StageMinutes(deep = deep, rem = rem, light = light, awake = awake)

    @Test
    fun healthConnectOwnsTheStagesEvenWhenTheCacheClaimsMoreSleep() {
        val hc = stages(deep = 60, rem = 70, light = 200, awake = 12)
        val inflatedCache = stages(deep = 200, rem = 180, light = 300, awake = 0)

        val chosen = SleepSourcePolicy.stages(hc = hc, cached = inflatedCache)

        assertEquals("Health Connect is the record of the night", hc, chosen)
    }

    @Test
    fun theCacheCoversOnlyANightHealthConnectDoesNotHaveYet() {
        val cached = stages(deep = 40, rem = 50, light = 180, awake = 15)

        val chosen = SleepSourcePolicy.stages(hc = null, cached = cached)

        assertEquals(cached, chosen)
    }

    @Test
    fun anEmptyHealthConnectNightIsTreatedAsMissing() {
        val emptyHc = stages(0, 0, 0, 0)
        val cached = stages(deep = 40, rem = 50, light = 180, awake = 15)

        val chosen = SleepSourcePolicy.stages(hc = emptyHc, cached = cached)

        assertEquals("no asleep minutes means no night", cached, chosen)
    }

    @Test
    fun nothingAnywhereYieldsAnEmptyNight() {
        val chosen = SleepSourcePolicy.stages(hc = null, cached = null)

        assertEquals(0, chosen.asleepMinutes)
        assertEquals(StageMinutes(0, 0, 0, 0), chosen)
    }

    @Test
    fun theChosenNightIsNeverAMixtureOfTwoSources() {
        // Score comes from the widget and stages from Health Connect; mixing them per
        // field produced nights that matched neither source.
        val hc = stages(deep = 60, rem = 70, light = 200, awake = 12)
        val cached = stages(deep = 90, rem = 0, light = 250, awake = 0)

        val chosen = SleepSourcePolicy.stages(hc = hc, cached = cached)

        assertEquals(60, chosen.deep)
        assertEquals(70, chosen.rem)
        assertEquals(200, chosen.light)
        assertEquals("awake must come from the same source, zeros included", 12, chosen.awake)
    }
}
