package io.github.hardbas.wearsync.app.tile

import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.tile.design.TileAccents
import io.github.hardbas.wearsync.app.tile.design.TileGeometry
import io.github.hardbas.wearsync.app.tile.design.TileInk
import io.github.hardbas.wearsync.app.tile.design.TileMotion
import io.github.hardbas.wearsync.app.tile.design.TileResources
import io.github.hardbas.wearsync.app.tile.design.TileStrings
import io.github.hardbas.wearsync.app.tile.design.TileTestKit
import io.github.hardbas.wearsync.app.tile.design.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Executable contract for the vitals tile: the pulse, the two rings, the pill
 * state, the measured expressive layer (a light streak sweeping the rim) and the
 * chord budget. Built through the public text seam, so the whole layout is
 * inspectable on the JVM.
 */
class VitalsTileContractTest {

    private val strings = TileStrings { resId ->
        when (resId) {
            R.string.vitals_short -> "Vitals"
            R.string.bpm_unit -> "BPM"
            R.string.details -> "Details"
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
        VitalsTileService.buildTile(strings, packageName, deviceParams, vitals)
            .tileTimeline!!.timelineEntries[0].layout!!.root!!

    private val live = RingConnVitals(
        lastHeartRate = 72,
        lastSpo2 = 97,
        ringBattery = 84,
        refreshTimestamp = now()
    )

    private val liveRoot by lazy { rootFor(live) }

    // ---- the reading ----------------------------------------------------

    @Test
    fun `hero is the live pulse at the ring hero size`() {
        val hero = TileTestKit.texts(liveRoot).first { it.text?.value == "72" }
        assertEquals(TileType.HERO_RING, hero.fontStyle?.size?.value)
        assertEquals(LayoutElementBuilders.FONT_WEIGHT_MEDIUM, hero.fontStyle?.weight?.value)
    }

    @Test
    fun `label is the short metric word and the sub is the lowercase unit`() {
        val label = TileTestKit.texts(liveRoot).first { it.text?.value?.startsWith("VITALS") == true }
        assertEquals("VITALS", label.text?.value)
        assertTrue(TileTestKit.isUppercase(label.text!!.value))
        assertEquals(TileType.LABEL, label.fontStyle?.size?.value)
        assertEquals(TileAccents.spo2Label, label.fontStyle?.color?.argb)

        val sub = TileTestKit.texts(liveRoot).first { it.text?.value == "bpm" }
        assertEquals(TileType.SUB, sub.fontStyle?.size?.value)
        assertEquals(TileAccents.heartRateLabel, sub.fontStyle?.color?.argb)
    }

    @Test
    fun `pill carries both readings with the tone dot of their ring`() {
        val texts = TileTestKit.texts(liveRoot).mapNotNull { it.text?.value }
        assertTrue("no SpO2 reading in the pill, had $texts", texts.any { it == "97%" })
        assertTrue("no ring charge in the pill, had $texts", texts.any { it == "84%" })
        assertEquals(TileType.PILL, TileTestKit.texts(liveRoot).first { it.text?.value == "97%" }.fontStyle?.size?.value)
    }

    @Test
    fun `the dial is two tracks and two concentric rings`() {
        val lines = TileTestKit.arcLines(liveRoot)
        assertEquals("track, track, halo, outer ring, inner ring", 5, lines.size)
        // Only the two rings carry a gradient brush: pick them out instead of
        // counting positions, which shift whenever a static layer is added.
        val rings = lines.filter { it.brush != null }
        assertEquals("two rings", 2, rings.size)
        assertEquals(TileGeometry.RING_THICKNESS, TileTestKit.dpOf(rings[0].thickness), 0.001f)
        assertEquals(TileAccents.spo2.last(), rings[0].color?.argb)
        assertEquals(TileGeometry.RING_THICKNESS, TileTestKit.dpOf(rings[1].thickness), 0.001f)
        assertEquals(TileAccents.battery.last(), rings[1].color?.argb)
    }

    // ---- motion ---------------------------------------------------------

    @Test
    fun `the expressive layer is a light streak sweeping the rim`() {
        val streak = TileTestKit.imagesOf(liveRoot, TileResources.SWEEP).single()
        assertEquals(TileGeometry.TILE_DP, TileTestKit.dpOf(streak.width), 0.001f)
        assertEquals(0.40f, streak.modifiers?.opacity?.value ?: 0f, 0.001f)
        assertEquals(
            "a renderer that ignores the animation shows the streak at the top",
            360f,
            streak.modifiers?.transformation?.rotation?.value ?: 0f,
            0.001f
        )
        val spin = TileTestKit.specOf(streak.modifiers?.transformation?.rotation?.dynamicValue)
        assertEquals(TileMotion.SWEEP_MS_RINGS, TileTestKit.durationMs(spin))
        assertTrue(TileTestKit.loopsForever(spin))
        assertEquals(
            "drawn first, so it sits behind both rings",
            TileResources.SWEEP,
            TileTestKit.images(liveRoot).first().resourceId?.value
        )
    }

    @Test
    fun `live tile spends two loops on the streak and the bezel breath`() {
        val specs = TileTestKit.animators(liveRoot)
        assertEquals(2, specs.size)
        assertTrue("both animators loop for ever", specs.all(TileTestKit::loopsForever))
        assertEquals(
            "one is the bezel breath at its measured period",
            1,
            specs.count { TileTestKit.durationMs(it) == TileMotion.AMBIENT_MS }
        )
        assertEquals(
            "the other is one turn of the streak",
            1,
            specs.count { TileTestKit.durationMs(it) == TileMotion.SWEEP_MS_RINGS }
        )
    }

    @Test
    fun `no brush carries a dynamic value`() {
        listOf(live, RingConnVitals(refreshTimestamp = 0L), live.copy(refreshTimestamp = now() - 3_600L))
            .map(::rootFor)
            .forEach { root ->
                assertTrue(
                    "the two rings must stay static; a brush is snapshotted when the tile is built",
                    TileTestKit.brushDynamics(root).isEmpty()
                )
            }
    }

    // ---- degraded states ------------------------------------------------

    @Test
    fun `empty tile keeps both tracks and runs no animator`() {
        val root = rootFor(RingConnVitals(refreshTimestamp = 0L))
        val lines = TileTestKit.arcLines(root)
        assertEquals("the tracks survive, the rings do not", 2, lines.size)
        assertTrue(TileTestKit.animators(root).isEmpty())
        assertTrue(TileTestKit.imagesOf(root, TileResources.SWEEP).isEmpty())
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "VITALS" })
        assertEquals(
            "hero and both pill readings fall back to --",
            3,
            TileTestKit.texts(root).count { it.text?.value == "--" }
        )
    }

    @Test
    fun `stale tile desaturates instead of animating`() {
        val root = rootFor(live.copy(refreshTimestamp = now() - 3_600L))
        val hero = TileTestKit.texts(root).first { it.text?.value == "72" }
        assertEquals(TileInk.STALE_HERO_ALPHA, hero.modifiers?.opacity?.value ?: 0f, 0.001f)
        val label = TileTestKit.texts(root).first { it.text?.value?.startsWith("VITALS") == true }
        assertEquals(TileInk.DIM, label.fontStyle?.color?.argb)
        val lines = TileTestKit.arcLines(root)
        // Two tracks, the outer halo, then the two rings.
        assertEquals(TileAccents.dim(TileAccents.spo2).last(), lines[3].color?.argb)
        assertEquals(TileAccents.dim(TileAccents.battery).last(), lines[4].color?.argb)
        assertTrue(
            "a stale tile is a still frame: no streak and no animator",
            TileTestKit.animators(root).isEmpty()
        )
        assertTrue(TileTestKit.imagesOf(root, TileResources.SWEEP).isEmpty())
    }

    // ---- fit ------------------------------------------------------------

    @Test
    fun `label and pill fit the chord at their own height`() {
        val label = TileTestKit.texts(liveRoot).first { it.text?.value?.startsWith("VITALS") == true }
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

    // ---- previous behaviour ---------------------------------------------

    @Test
    fun `previous freshness and resource version still hold`() {
        val tile = VitalsTileService.buildTile(strings, packageName, deviceParams, live)
        assertEquals(30_000L, tile.freshnessIntervalMillis)
        assertEquals(VitalsTileService.RESOURCES_VERSION, VitalsTileService.buildResources().version)
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
