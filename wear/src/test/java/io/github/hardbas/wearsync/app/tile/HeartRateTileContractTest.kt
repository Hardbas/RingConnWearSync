package io.github.hardbas.wearsync.app.tile

import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.tile.design.TileAccents
import io.github.hardbas.wearsync.app.tile.design.TileGeometry
import io.github.hardbas.wearsync.app.tile.design.TileInk
import io.github.hardbas.wearsync.app.tile.design.TileMotion
import io.github.hardbas.wearsync.app.tile.design.TileStrings
import io.github.hardbas.wearsync.app.tile.design.TileTestKit
import io.github.hardbas.wearsync.app.tile.design.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Executable contract for the pulse tile: the reading, its zone, the two-animator
 * budget and the chord budget. Built through the public text seam, so the whole
 * layout is inspectable on the JVM.
 */
class HeartRateTileContractTest {

    private val strings = TileStrings { resId ->
        when (resId) {
            R.string.pulse_short -> "Pulse"
            R.string.hrv_short -> "HRV"
            R.string.details -> "Details"
            R.string.no_data -> "No data"
            else -> "s$resId"
        }
    }

    private val deviceParams = DeviceParameters.Builder()
        .setScreenWidthDp(192)
        .setScreenHeightDp(192)
        .build()

    private val packageName = "io.github.hardbas.wearsync.test"

    private fun now(): Long = System.currentTimeMillis() / 1000L

    private fun rootFor(vitals: RingConnVitals): LayoutElementBuilders.LayoutElement =
        HeartRateTileService.buildTile(strings, packageName, deviceParams, vitals)
            .tileTimeline!!.timelineEntries[0].layout!!.root!!

    private val live = RingConnVitals(
        lastHeartRate = 138,
        minHeartRate = 58,
        maxHeartRate = 168,
        lastHrv = 34,
        refreshTimestamp = now()
    )

    private val liveRoot by lazy { rootFor(live) }

    // ---- the reading ----------------------------------------------------

    @Test
    fun `hero is the live reading at the design size`() {
        val hero = TileTestKit.texts(liveRoot).first { it.text?.value == "138" }
        assertEquals(TileType.HERO, hero.fontStyle?.size?.value)
        assertEquals(LayoutElementBuilders.FONT_WEIGHT_MEDIUM, hero.fontStyle?.weight?.value)
    }

    @Test
    fun `label carries the zone and stays short`() {
        val label = TileTestKit.texts(liveRoot).first { it.text?.value?.startsWith("PULSE") == true }
        assertEquals("PULSE · Z4", label.text?.value)
        assertTrue(TileTestKit.isUppercase(label.text!!.value))
        assertEquals(TileType.LABEL, label.fontStyle?.size?.value)
    }

    @Test
    fun `zone cuts follow the design`() {
        assertEquals("Z1", HeartRateTileService.zoneOf(55))
        assertEquals("Z2", HeartRateTileService.zoneOf(62))
        assertEquals("Z2", HeartRateTileService.zoneOf(96))
        assertEquals("Z3", HeartRateTileService.zoneOf(97))
        assertEquals("Z3", HeartRateTileService.zoneOf(128))
        assertEquals("Z4", HeartRateTileService.zoneOf(129))
        assertEquals("Z4", HeartRateTileService.zoneOf(154))
        assertEquals("Z5", HeartRateTileService.zoneOf(155))
    }

    @Test
    fun `the dial is a track, a value arc and a pearl`() {
        val lines = TileTestKit.arcLines(liveRoot)
        assertEquals("track, halo, value, ivory pearl, accent pearl", 5, lines.size)
        val value = lines[2]
        assertEquals(TileGeometry.ARC_THICKNESS, TileTestKit.dpOf(value.thickness), 0.001f)
        assertEquals(TileAccents.heartRate.last(), value.color?.argb)
    }

    // ---- motion ---------------------------------------------------------

    @Test
    fun `live tile spends exactly two animators, one of them infinite`() {
        val specs = TileTestKit.animators(liveRoot)
        assertEquals(TileMotion.MAX_ANIMATORS, specs.size)
        assertEquals(1, specs.count(TileTestKit::loopsForever))
        assertTrue(
            "the entrance must settle once",
            specs.any { !TileTestKit.loopsForever(it) && TileTestKit.durationMs(it) == TileMotion.ENTRANCE_MS }
        )
    }

    // ---- degraded states ------------------------------------------------

    @Test
    fun `empty tile shows no value arc and runs no animator`() {
        val root = rootFor(RingConnVitals(refreshTimestamp = 0L))
        val lines = TileTestKit.arcLines(root)
        assertEquals("the track survives, the value arc does not", 1, lines.size)
        assertTrue(TileTestKit.animators(root).isEmpty())
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "--" })
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "PULSE" })
    }

    @Test
    fun `stale tile desaturates instead of animating`() {
        val root = rootFor(live.copy(refreshTimestamp = now() - 3_600L))
        val hero = TileTestKit.texts(root).first { it.text?.value == "138" }
        assertEquals(TileInk.STALE_HERO_ALPHA, hero.modifiers?.opacity?.value ?: 0f, 0.001f)
        val label = TileTestKit.texts(root).first { it.text?.value?.startsWith("PULSE") == true }
        assertEquals(TileInk.DIM, label.fontStyle?.color?.argb)
        val value = TileTestKit.arcLines(root)[2]
        assertEquals(TileAccents.dim(TileAccents.heartRate).last(), value.color?.argb)
    }

    // ---- fit ------------------------------------------------------------

    @Test
    fun `label and pill fit the chord at their own height`() {
        val label = TileTestKit.texts(liveRoot).first { it.text?.value?.startsWith("PULSE") == true }
        assertTrue(
            "label ${TileTestKit.estimatedWidthDp(label)}dp must stay under " +
                "${TileGeometry.safeWidthDp(TileGeometry.LABEL_Y)}dp",
            TileTestKit.estimatedWidthDp(label) <= TileGeometry.safeWidthDp(TileGeometry.LABEL_Y)
        )

        val pill = TileTestKit.boxes(liveRoot)
            .first { it.modifiers?.background?.color?.argb == TileInk.SURFACE }
        val padding = (pill.modifiers?.padding?.start?.value ?: 0f) +
            (pill.modifiers?.padding?.end?.value ?: 0f)
        val width = pill.contents.sumOf { widthOf(it).toDouble() }.toFloat() + padding
        assertTrue(
            "pill ${width}dp must stay under ${TileGeometry.safeWidthDp(TileGeometry.PILL_CENTER_Y)}dp",
            width <= TileGeometry.safeWidthDp(TileGeometry.PILL_CENTER_Y)
        )
    }

    private fun widthOf(element: LayoutElementBuilders.LayoutElement): Float = when (element) {
        is LayoutElementBuilders.Text -> TileTestKit.estimatedWidthDp(element)
        is LayoutElementBuilders.Spacer -> TileTestKit.dpOf(element.width)
        is LayoutElementBuilders.Box -> TileTestKit.dpOf(element.width)
        is LayoutElementBuilders.Row -> element.contents.sumOf { widthOf(it).toDouble() }.toFloat()
        is LayoutElementBuilders.Column -> element.contents.sumOf { widthOf(it).toDouble() }.toFloat()
        else -> 0f
    }
}
