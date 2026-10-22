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
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Executable contract for the activity tile: the two rings, the three-reading
 * pill, the steps abbreviation, the two-animator budget and the chord budget.
 * Built through the public text seam, so the whole layout is inspectable on the
 * JVM.
 */
class DailySummaryTileContractTest {

    private val strings = TileStrings { resId ->
        when (resId) {
            R.string.steps_short -> "Steps"
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
        DailySummaryTileService.buildTile(strings, packageName, deviceParams, vitals)
            .tileTimeline!!.timelineEntries[0].layout!!.root!!

    /** 8 420 steps against the 10 000 default goal, 320 kcal of 500, 6.1 km. */
    private val live = RingConnVitals(
        liveSteps = 8420,
        targetSteps = 10_000,
        liveCalorie = 320,
        targetCalorie = 500,
        activeMinutes = 42,
        distanceKm = 6.1f,
        refreshTimestamp = now()
    )

    private val liveRoot by lazy { rootFor(live) }

    // ---- the reading ----------------------------------------------------

    @Test
    fun `hero is the grouped step count at the compact hero size`() {
        val hero = TileTestKit.texts(liveRoot).first { it.text?.value == "8,420" }
        assertEquals(TileType.HERO_COMPACT, hero.fontStyle?.size?.value)
        assertEquals(LayoutElementBuilders.FONT_WEIGHT_MEDIUM, hero.fontStyle?.weight?.value)
    }

    @Test
    fun `label is the uppercase metric word in the steps accent`() {
        val label = TileTestKit.texts(liveRoot).first { it.text?.value == "STEPS" }
        assertTrue(TileTestKit.isUppercase(label.text!!.value))
        assertEquals(TileType.LABEL, label.fontStyle?.size?.value)
        assertEquals(TileAccents.steps.first(), label.fontStyle?.color?.argb)
    }

    @Test
    fun `steps abbreviate from ten thousand with a dot`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("8,420", DailySummaryTileService.stepsText(8420))
            assertEquals("12.4k", DailySummaryTileService.stepsText(12400))
            assertEquals("--", DailySummaryTileService.stepsText(0))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `pill carries active minutes, calories and distance in their own accents`() {
        val texts = TileTestKit.texts(liveRoot)
        val active = texts.first { it.text?.value == "42m" }
        val kcal = texts.first { it.text?.value == "320" }
        val km = texts.first { it.text?.value == "6.1km" }

        assertEquals(TileType.PILL, active.fontStyle?.size?.value)
        assertEquals(TileAccents.steps.first(), active.fontStyle?.color?.argb)
        assertEquals(TileAccents.kcal.first(), kcal.fontStyle?.color?.argb)
        assertEquals(TileAccents.teal, km.fontStyle?.color?.argb)
    }

    @Test
    fun `the dial is two tracks and two concentric rings`() {
        val lines = TileTestKit.arcLines(liveRoot)
        assertEquals("track, track, halo, outer ring, inner ring", 5, lines.size)
        assertEquals(TileGeometry.RING_THICKNESS_ACTIVITY, TileTestKit.dpOf(lines[3].thickness), 0.001f)
        assertEquals(TileAccents.steps.last(), lines[3].color?.argb)
        assertEquals(TileGeometry.RING_THICKNESS_ACTIVITY, TileTestKit.dpOf(lines[4].thickness), 0.001f)
        assertEquals(TileAccents.kcal.last(), lines[4].color?.argb)
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
    fun `empty tile keeps both tracks and runs no animator`() {
        val root = rootFor(RingConnVitals(refreshTimestamp = 0L))
        val lines = TileTestKit.arcLines(root)
        assertEquals("the tracks survive, the rings do not", 2, lines.size)
        assertTrue(TileTestKit.animators(root).isEmpty())
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "--" })
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "STEPS" })

        val pill = TileTestKit.boxes(root)
            .first { it.modifiers?.background?.color?.argb == TileInk.SURFACE }
        val pillTexts = pill.contents
            .flatMap(TileTestKit::flatten)
            .filterIsInstance<LayoutElementBuilders.Text>()
            .mapNotNull { it.text?.value }
        assertEquals(
            "the pill falls back to dashes but keeps its unit letters",
            listOf("--m", "--", "--km"),
            pillTexts
        )
    }

    @Test
    fun `stale tile desaturates instead of animating`() {
        val root = rootFor(live.copy(refreshTimestamp = now() - 3_600L))
        val hero = TileTestKit.texts(root).first { it.text?.value == "8,420" }
        assertEquals(TileInk.STALE_HERO_ALPHA, hero.modifiers?.opacity?.value ?: 0f, 0.001f)
        val label = TileTestKit.texts(root).first { it.text?.value == "STEPS" }
        assertEquals(TileInk.DIM, label.fontStyle?.color?.argb)

        val rings = TileTestKit.arcLines(root)
            .filter { TileTestKit.dpOf(it.thickness) == TileGeometry.RING_THICKNESS_ACTIVITY }
        assertEquals("both tracks and both rings stay readable", 4, rings.size)
        assertEquals(TileAccents.dim(TileAccents.steps).last(), rings[2].color?.argb)
        assertEquals(TileAccents.dim(TileAccents.kcal).last(), rings[3].color?.argb)

        assertTrue(
            "a stale tile paints no halo and no entrance, so it runs zero animators",
            TileTestKit.animators(root).isEmpty()
        )
    }

    // ---- fit ------------------------------------------------------------

    @Test
    fun `label and pill fit the chord at their own height`() {
        val label = TileTestKit.texts(liveRoot).first { it.text?.value == "STEPS" }
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
    fun `the tile source carries no colour literal and no legacy design import`() {
        val source = java.io.File("src/main/java/io/github/hardbas/wearsync/app/tile/DailySummaryTileService.kt")
        assertTrue("source not found at ${source.absolutePath}", source.isFile)
        val body = source.readText()
        assertTrue(
            "every colour must come from TileInk or TileAccents",
            !Regex("0x[0-9A-Fa-f]{2,}").containsMatchIn(body)
        )
        listOf("TileDesign", "TileGauge", "TileChrome", "TileMeter", "TileMetric", "TileBackdrop")
            .forEach { legacy ->
                assertTrue("legacy $legacy reference left behind", !body.contains(legacy))
            }
    }

    @Test
    fun `previous freshness and resource version still hold`() {
        val tile = DailySummaryTileService.buildTile(strings, packageName, deviceParams, live)
        assertEquals(60_000L, tile.freshnessIntervalMillis)
        assertEquals(DailySummaryTileService.RESOURCES_VERSION, DailySummaryTileService.buildResources().version)
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
