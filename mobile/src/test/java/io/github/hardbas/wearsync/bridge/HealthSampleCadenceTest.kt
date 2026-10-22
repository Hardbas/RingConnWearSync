package io.github.hardbas.wearsync.bridge

import io.github.hardbas.wearsync.bridge.service.HealthSampleCadence
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sample spacing that turns a count of Health Connect samples into minutes.
 *
 * Every case here is a cadence the ring can plausibly produce, because the
 * whole point of the helper is that a count is not a duration.
 */
class HealthSampleCadenceTest {

    private fun series(vararg offsetsSeconds: Long): List<Instant> =
        offsetsSeconds.map { Instant.ofEpochSecond(BASE_EPOCH_SECOND + it) }

    @Test
    fun `five minute cadence reads as five minutes`() {
        // A typical nocturnal SpO2 series: one sample every five minutes.
        assertEquals(5, HealthSampleCadence.windowMinutes(series(0, 300, 600, 900, 1200)))
    }

    @Test
    fun `one minute cadence reads as one minute`() {
        assertEquals(1, HealthSampleCadence.windowMinutes(series(0, 60, 120, 180, 240)))
    }

    @Test
    fun `sub-minute cadence floors at one minute`() {
        assertEquals(1, HealthSampleCadence.windowMinutes(series(0, 30, 60, 90, 120)))
    }

    @Test
    fun `sparse samples cap at fifteen minutes`() {
        assertEquals(15, HealthSampleCadence.windowMinutes(series(0, 3600, 7200, 10_800)))
    }

    @Test
    fun `a single sync gap does not move the median`() {
        // 5 min, 5 min, then an 8 h gap, then 5 min again: still a five minute source.
        assertEquals(5, HealthSampleCadence.windowMinutes(series(0, 300, 600, 600 + 28_800, 600 + 28_800 + 300)))
    }

    @Test
    fun `fewer than two distinct samples falls back to the default`() {
        assertEquals(HealthSampleCadence.DEFAULT_WINDOW_MINUTES, HealthSampleCadence.windowMinutes(emptyList()))
        assertEquals(HealthSampleCadence.DEFAULT_WINDOW_MINUTES, HealthSampleCadence.windowMinutes(series(0)))
        assertEquals(HealthSampleCadence.DEFAULT_WINDOW_MINUTES, HealthSampleCadence.windowMinutes(series(0, 0, 0)))
    }

    @Test
    fun `duplicate timestamps do not create zero gaps`() {
        assertEquals(2, HealthSampleCadence.windowMinutes(series(0, 0, 120, 120, 240)))
    }

    @Test
    fun `ordering the series does not matter`() {
        assertEquals(5, HealthSampleCadence.windowMinutes(series(1200, 0, 600, 300, 900)))
    }

    private companion object {
        /** A fixed epoch so the cases never depend on the clock. */
        const val BASE_EPOCH_SECOND = 1_700_000_000L
    }
}
