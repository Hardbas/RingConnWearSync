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
 * Executable contract for the stress tile: the spectrum, the level word, the
 * two-animator budget and the chord budget. Built through the public text seam,
 * so the whole layout is inspectable on the JVM.
 */
class StressTileContractTest {

    private val strings = TileStrings { resId ->
        when (resId) {
            R.string.tile_stress_title -> "Stress"
            R.string.stress_relaxed -> "Relaxed"
            R.string.stress_balanced -> "Balanced"
            R.string.stress_moderate -> "Moderate"
            R.string.stress_high -> "High"
            R.string.stress_resting -> "Resting"
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
        StressTileService.buildTile(strings, packageName, deviceParams, vitals)
            .tileTimeline!!.timelineEntries[0].layout!!.root!!

    private val live = RingConnVitals(
        lastHrv = 42,
        stressScore = 24,
        refreshTimestamp = now()
    )

    private val liveRoot by lazy { rootFor(live) }

    // ---- the reading ----------------------------------------------------

    @Test
    fun `hero is the live score at the design size`() {
        val hero = TileTestKit.texts(liveRoot).first { it.text?.value == "24" }
        assertEquals(TileType.HERO, hero.fontStyle?.size?.value)
        assertEquals(LayoutElementBuilders.FONT_WEIGHT_MEDIUM, hero.fontStyle?.weight?.value)
    }

    @Test
    fun `label is the metric word in the level colour`() {
        val label = TileTestKit.texts(liveRoot).first { it.text?.value?.startsWith("STRESS") == true }
        assertEquals("STRESS", label.text?.value)
        assertTrue(TileTestKit.isUppercase(label.text!!.value))
        assertEquals(TileType.LABEL, label.fontStyle?.size?.value)
        assertEquals(TileAccents.stress[0], label.fontStyle?.color?.argb)
    }

    @Test
    fun `sub line carries the level word and the hrv read`() {
        val texts = TileTestKit.texts(liveRoot)
        val level = texts.first { it.text?.value == "Relaxed" }
        assertEquals(TileType.SUB, level.fontStyle?.size?.value)
        assertEquals(TileAccents.stress[0], level.fontStyle?.color?.argb)
        assertTrue(texts.any { it.text?.value == " · " })
        assertTrue(texts.any { it.text?.value == "HRV 42 ms" })
    }

    @Test
    fun `level cuts follow the design and the top level takes the carmine stop`() {
        assertEquals(0, StressTileService.levelOf(29))
        assertEquals(1, StressTileService.levelOf(30))
        assertEquals(1, StressTileService.levelOf(59))
        assertEquals(2, StressTileService.levelOf(60))
        assertEquals(2, StressTileService.levelOf(79))
        assertEquals(3, StressTileService.levelOf(80))
        assertEquals(3, StressTileService.levelOf(100))

        assertEquals(0, StressTileService.levelStop(0))
        assertEquals(1, StressTileService.levelStop(1))
        assertEquals(2, StressTileService.levelStop(2))
        assertEquals(TileAccents.stress.lastIndex, StressTileService.levelStop(3))
        assertEquals(
            TileAccents.stress[4],
            TileAccents.step(TileAccents.stress, StressTileService.levelStop(3))
        )
    }

    @Test
    fun `the spectrum is the rim, the reading and a pearl`() {
        val lines = TileTestKit.arcLines(liveRoot)
        assertEquals("halo, spectrum, ivory pearl, accent pearl", 4, lines.size)
        val spectrum = lines[1]
        assertEquals(TileGeometry.ARC_THICKNESS, TileTestKit.dpOf(spectrum.thickness), 0.001f)
        assertEquals(TileAccents.stress.first(), spectrum.color?.argb)
        assertTrue("the spectrum must keep its brush", spectrum.brush != null)
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
    fun `empty tile keeps the spectrum rim and runs no animator`() {
        val root = rootFor(RingConnVitals(refreshTimestamp = 0L))
        val lines = TileTestKit.arcLines(root)
        assertEquals("rim only: no halo and no pearl", 1, lines.size)
        assertTrue(TileTestKit.animators(root).isEmpty())
        assertEquals(TileAccents.stress.first(), lines[0].color?.argb)

        val label = TileTestKit.texts(root).first { it.text?.value?.startsWith("STRESS") == true }
        assertEquals(TileInk.DIM, label.fontStyle?.color?.argb)
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "No data" })
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "--" })
    }

    @Test
    fun `stale tile desaturates instead of animating`() {
        val root = rootFor(live.copy(refreshTimestamp = now() - 3_600L))
        val hero = TileTestKit.texts(root).first { it.text?.value == "24" }
        assertEquals(TileInk.STALE_HERO_ALPHA, hero.modifiers?.opacity?.value ?: 0f, 0.001f)
        val label = TileTestKit.texts(root).first { it.text?.value?.startsWith("STRESS") == true }
        assertEquals(TileInk.DIM, label.fontStyle?.color?.argb)
        // No halo in the stale frame, so the spectrum is the first arc line.
        assertEquals(TileInk.DIM, TileTestKit.arcLines(root)[0].color?.argb)
        assertTrue(
            "a stale tile is a still frame: no entrance and no halo",
            TileTestKit.animators(root).isEmpty()
        )
    }

    // ---- fit ------------------------------------------------------------

    @Test
    fun `label and pill fit the chord at their own height`() {
        val label = TileTestKit.texts(liveRoot).first { it.text?.value?.startsWith("STRESS") == true }
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
        val tile = StressTileService.buildTile(strings, packageName, deviceParams, live)
        assertEquals(60_000L, tile.freshnessIntervalMillis)
        assertEquals(StressTileService.RESOURCES_VERSION, StressTileService.buildResources().version)
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
