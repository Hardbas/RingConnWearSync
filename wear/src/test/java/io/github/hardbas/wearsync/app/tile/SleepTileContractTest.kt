package io.github.hardbas.wearsync.app.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.tile.design.TileAccents
import io.github.hardbas.wearsync.app.tile.design.TileDrawer
import io.github.hardbas.wearsync.app.tile.design.TileDrawerState
import io.github.hardbas.wearsync.app.tile.design.TileGeometry
import io.github.hardbas.wearsync.app.tile.design.TileInk
import io.github.hardbas.wearsync.app.tile.design.TileMotion
import io.github.hardbas.wearsync.app.tile.design.TileResources
import io.github.hardbas.wearsync.app.tile.design.TileShell
import io.github.hardbas.wearsync.app.tile.design.TileStrings
import io.github.hardbas.wearsync.app.tile.design.TileTestKit
import io.github.hardbas.wearsync.app.tile.design.TileType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Executable contract for the sleep tile: the quality label with its crescent,
 * the phase ring, the duration/temperature line, the measured expressive layer (a
 * slow light streak sweeping the rim) and the chord budget. Built through the
 * public text seam, so the whole layout is inspectable on the JVM.
 */
class SleepTileContractTest {

    private val strings = TileStrings { resId ->
        when (resId) {
            R.string.excellent -> "Excellent"
            R.string.good -> "Good"
            R.string.fair -> "Fair"
            R.string.no_data -> "No data"
            R.string.details -> "Details"
            R.string.tile_drawer_sleep_kicker -> "Sleep phases"
            R.string.tile_drawer_sleep_title -> "Total %1\$s · %2\$s"
            R.string.tile_drawer_sleep_meta -> "Score %1\$s"
            R.string.tile_drawer_close -> "Close"
            R.string.tile_drawer_open -> "Open"
            else -> "s$resId"
        }
    }

    private val deviceParams = DeviceParameters.Builder()
        .setScreenWidthDp(192)
        .setScreenHeightDp(192)
        .build()

    private val packageName = "io.github.hardbas.wearsync.test"

    private fun now(): Long = System.currentTimeMillis() / 1000L

    private fun rootFor(
        vitals: RingConnVitals,
        lastClickableId: String? = null
    ): LayoutElementBuilders.LayoutElement =
        SleepTileService.buildTile(strings, packageName, deviceParams, vitals, lastClickableId)
            .tileTimeline!!.timelineEntries[0].layout!!.root!!

    /** The drawer state is process-wide, so every test starts and ends closed. */
    @Before
    fun resetDrawer() {
        TileDrawerState.apply(SleepTileService.TILE_ID, TileDrawerState.ID_CLOSE)
    }

    @After
    fun closeDrawer() {
        TileDrawerState.apply(SleepTileService.TILE_ID, TileDrawerState.ID_CLOSE)
    }

    private fun drawerRoot(vitals: RingConnVitals = live): LayoutElementBuilders.LayoutElement =
        rootFor(vitals, TileDrawerState.ID_OPEN)

    private fun clickables(root: LayoutElementBuilders.LayoutElement): List<ModifiersBuilders.Clickable> =
        TileTestKit.flatten(root).mapNotNull { element ->
            when (element) {
                is LayoutElementBuilders.Box -> element.modifiers?.clickable
                is LayoutElementBuilders.Row -> element.modifiers?.clickable
                is LayoutElementBuilders.Column -> element.modifiers?.clickable
                is LayoutElementBuilders.Text -> element.modifiers?.clickable
                is LayoutElementBuilders.Image -> element.modifiers?.clickable
                is LayoutElementBuilders.Arc -> element.modifiers?.clickable
                else -> null
            }
        }

    private val drawerPanelColor = TileInk.withAlpha(TileDrawer.SURFACE, TileDrawer.SURFACE_ALPHA)

    private fun drawerPanels(root: LayoutElementBuilders.LayoutElement): List<LayoutElementBuilders.Box> =
        TileTestKit.boxes(root).filter { it.modifiers?.background?.color?.argb == drawerPanelColor }

    private fun bars(root: LayoutElementBuilders.LayoutElement): List<LayoutElementBuilders.Box> =
        TileTestKit.boxes(root)
            .filter {
                TileTestKit.dpOf(it.width) == TileGeometry.DRAWER_BAR_WIDTH ||
                    TileTestKit.dpOf(it.width) == TileGeometry.DRAWER_PHASE_BAR_WIDTH
            }
            .filter { it.modifiers?.background?.color != null }

    /** Score 88 "Excellent", deep 92 + REM 82 + light 236 = 6h 50m, +0.3 degree. */
    private val live = RingConnVitals(
        sleepScore = 88,
        sleepDeepMins = 92,
        sleepRemMins = 82,
        sleepLightMins = 236,
        sleepAwakeMins = 22,
        skinTempOffset = 0.3f,
        skinTempBaseline = 36.1f,
        refreshTimestamp = now()
    )

    private val liveRoot by lazy { rootFor(live) }

    /** The four phase segments: the rim track is excluded by its full-span length. */
    private fun phaseSegments(root: LayoutElementBuilders.LayoutElement) =
        TileTestKit.arcLines(root).filter {
            TileTestKit.dpOf(it.thickness) == TileGeometry.ARC_THICKNESS &&
                (it.length?.value ?: 0f) < TileGeometry.ARC_SPAN
        }

    private fun degrees(lines: List<androidx.wear.protolayout.LayoutElementBuilders.ArcLine>) =
        lines.map { it.length?.value ?: 0f }

    @Test
    fun `hero is the sleep score at the display size`() {
        val hero = TileTestKit.texts(liveRoot).first { it.text?.value == "88" }
        assertEquals(TileType.HERO, hero.fontStyle?.size?.value)
        assertEquals(LayoutElementBuilders.FONT_WEIGHT_MEDIUM, hero.fontStyle?.weight?.value)
    }

    @Test
    fun `sub line carries the duration and the skin temperature offset`() {
        val texts = TileTestKit.texts(liveRoot)
        val duration = texts.first { it.text?.value == "6h 50m" }
        val temperature = texts.first { it.text?.value == "+0.3°" }
        assertEquals(TileType.SUB, duration.fontStyle?.size?.value)
        assertEquals(TileType.SUB, temperature.fontStyle?.size?.value)
        assertEquals(TileAccents.teal, temperature.fontStyle?.color?.argb)
    }

    @Test
    fun `the dial is four phase segments proportional to the minutes`() {
        val segments = phaseSegments(liveRoot)
        assertEquals("one segment per phase", 4, segments.size)
        assertEquals(TileAccents.sleep[0], segments[0].color?.argb)
        assertEquals(TileAccents.sleep[3], segments[3].color?.argb)

        val span = degrees(segments)
        val total = 92f + 82f + 236f + 22f
        val expected = floatArrayOf(92f, 82f, 236f, 22f).map { it / total * 1f }

        // The dial's own gap is subtracted from every segment, so the proportions
        // are compared on the segment plus its gap.
        val withGap = span.map { it + TileGeometry.SEGMENT_GAP_DEGREES }
        withGap.forEachIndexed { index, value ->
            val want = expected[index] * TileGeometry.ARC_SPAN
            assertEquals("phase $index length", want, value, 0.05f)
        }
        assertEquals(
            "the four segments and their four gaps must fill the 260 degree dial",
            TileGeometry.ARC_SPAN,
            span.sum() + 4 * TileGeometry.SEGMENT_GAP_DEGREES,
            0.05f
        )
    }

    // ---- motion ---------------------------------------------------------

    @Test
    fun `live tile spends its two loops on the rim breath and the light streak`() {
        val streak = TileTestKit.imagesOf(liveRoot, TileResources.SWEEP).single()
        assertEquals(TileGeometry.TILE_DP, TileTestKit.dpOf(streak.width), 0.001f)
        assertEquals(0.30f, streak.modifiers?.opacity?.value ?: 0f, 0.001f)
        assertEquals(
            "a renderer that ignores the animation shows the streak at the top",
            360f,
            streak.modifiers?.transformation?.rotation?.value ?: 0f,
            0.001f
        )
        val spin = TileTestKit.specOf(streak.modifiers?.transformation?.rotation?.dynamicValue)
        assertEquals("the slowest streak of the carousel", TileMotion.SWEEP_MS_SLEEP, TileTestKit.durationMs(spin))
        assertTrue(TileTestKit.loopsForever(spin))

        val specs = TileTestKit.animators(liveRoot)
        assertEquals(2, specs.size)
        assertTrue("both animators loop for ever", specs.all(TileTestKit::loopsForever))
        assertEquals(
            "the rim breathes at the measured period",
            1,
            specs.count { TileTestKit.durationMs(it) == TileMotion.AMBIENT_MS }
        )
        assertEquals(1, specs.count { TileTestKit.durationMs(it) == TileMotion.SWEEP_MS_SLEEP })
    }

    @Test
    fun `the removed shooting star is gone from tree and resources`() {
        val drawables = SleepTileService.buildResources().idToImageMapping
        assertTrue(
            "tile_meteor was removed",
            drawables.keys.none { it == "tile_meteor" } &&
                TileTestKit.images(liveRoot).none { it.resourceId?.value == "tile_meteor" }
        )
        assertTrue(drawables.keys.any { it == TileResources.SWEEP })
    }

    @Test
    fun `no brush carries a dynamic value`() {
        listOf(live, RingConnVitals(refreshTimestamp = 0L), live.copy(refreshTimestamp = now() - 3_600L))
            .map(::rootFor)
            .forEach { root ->
                assertTrue(
                    "the phase ring must stay static; a brush is snapshotted when the tile is built",
                    TileTestKit.brushDynamics(root).isEmpty()
                )
            }
    }

    // ---- degraded states ------------------------------------------------

    @Test
    fun `empty tile shows no phase ring and runs no animator`() {
        val root = rootFor(RingConnVitals(refreshTimestamp = 0L))
        assertTrue("no phase box without data", phaseSegments(root).isEmpty())
        assertTrue(TileTestKit.animators(root).isEmpty())
        assertTrue(TileTestKit.imagesOf(root, TileResources.SWEEP).isEmpty())
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "--" })
        val label = TileTestKit.texts(root).first { it.text?.value == "No data" }
        assertEquals(TileInk.DIM, label.fontStyle?.color?.argb)
        assertEquals(
            "hero, duration and temperature all fall back",
            3,
            TileTestKit.texts(root).count { it.text?.value == "--" }
        )
    }

    @Test
    fun `stale tile desaturates instead of animating`() {
        val root = rootFor(live.copy(refreshTimestamp = now() - 3_600L))
        val hero = TileTestKit.texts(root).first { it.text?.value == "88" }
        assertEquals(TileInk.STALE_HERO_ALPHA, hero.modifiers?.opacity?.value ?: 0f, 0.001f)
        val label = TileTestKit.texts(root).first { it.text?.value == "Excellent" }
        assertEquals(TileInk.DIM, label.fontStyle?.color?.argb)

        val segments = phaseSegments(root)
        assertEquals(4, segments.size)
        segments.forEach { assertEquals(TileInk.DIM, it.color?.argb) }
        assertTrue(
            "a stale tile paints no streak and no breath, so it runs zero animators",
            TileTestKit.animators(root).isEmpty()
        )
        assertTrue(TileTestKit.imagesOf(root, TileResources.SWEEP).isEmpty())
    }

    // ---- fit ------------------------------------------------------------

    @Test
    fun `label, sub and pill fit the chord at their own height`() {
        val stack = TileTestKit.flatten(liveRoot)
            .filterIsInstance<LayoutElementBuilders.Column>()
            .single()
        val labelRow = stack.contents.first()
        val labelWidth = widthOf(labelRow)
        assertTrue(
            "label $labelWidth dp must stay under ${TileGeometry.safeWidthDp(TileGeometry.LABEL_Y)}dp",
            labelWidth <= TileGeometry.safeWidthDp(TileGeometry.LABEL_Y)
        )

        val sub = stack.contents.last()
        val subWidth = widthOf(sub)
        assertTrue(
            "sub $subWidth dp must stay under ${TileGeometry.safeWidthDp(TileGeometry.SUB_Y)}dp",
            subWidth <= TileGeometry.safeWidthDp(TileGeometry.SUB_Y)
        )

        val pill = TileTestKit.boxes(liveRoot)
            .first { it.modifiers?.background?.color?.argb == TileShell.PILL_GLASS }
        assertEquals(
            "the face pill is the render's fixed width",
            TileGeometry.PILL_WIDTH,
            TileTestKit.dpOf(pill.width),
            0.001f
        )
        assertTrue(
            "pill ${TileGeometry.PILL_WIDTH}dp must stay under " +
                "${TileGeometry.safeWidthDp(TileGeometry.PILL_CENTER_Y)}dp",
            TileGeometry.PILL_WIDTH <= TileGeometry.safeWidthDp(TileGeometry.PILL_CENTER_Y)
        )
    }

    // ---- the in-tile drawer (S12) ---------------------------------------

    @Test
    fun `the face is what a plain load shows`() {
        val root = rootFor(live)
        assertTrue("no drawer panel is painted", drawerPanels(root).isEmpty())
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "Excellent" })
    }

    @Test
    fun `only pill opens the drawer and only close closes it`() {
        TileDrawerState.apply(SleepTileService.TILE_ID, TileDrawerState.ID_OPEN)
        assertTrue(TileDrawerState.isOpen(SleepTileService.TILE_ID))

        TileDrawerState.apply(SleepTileService.TILE_ID, TileDrawerState.ID_CLOSE)
        assertTrue(!TileDrawerState.isOpen(SleepTileService.TILE_ID))

        TileDrawerState.apply(SleepTileService.TILE_ID, TileDrawerState.ID_OPEN)
        TileDrawerState.apply(SleepTileService.TILE_ID, "something-else")
        assertTrue("an unknown id is not a command", TileDrawerState.isOpen(SleepTileService.TILE_ID))
        TileDrawerState.apply(SleepTileService.TILE_ID, null)
        assertTrue("a plain load is not an invitation: the panel closes", !TileDrawerState.isOpen(SleepTileService.TILE_ID))

        TileDrawerState.apply(SleepTileService.TILE_ID, TileDrawerState.ID_CLOSE)
        TileDrawerState.apply(HeartRateTileService.TILE_ID, TileDrawerState.ID_OPEN)
        assertTrue(!TileDrawerState.isOpen(SleepTileService.TILE_ID))
        TileDrawerState.apply(HeartRateTileService.TILE_ID, TileDrawerState.ID_CLOSE)
    }

    @Test
    fun `the face pill opens the drawer instead of launching the app`() {
        val root = rootFor(live)
        val pill = clickables(root).single { it.id == TileDrawerState.ID_OPEN }
        assertTrue(
            "the pill must ask for a reload, not start the activity",
            pill.onClick is ActionBuilders.LoadAction
        )
    }

    @Test
    fun `the drawer replaces the face and prints the phase copy`() {
        val root = drawerRoot()
        assertEquals(1, drawerPanels(root).size)

        assertTrue(TileTestKit.texts(root).none { it.text?.value == "88" })
        assertTrue(TileTestKit.texts(root).none { it.text?.value == "Excellent" })
        assertTrue(TileTestKit.arcLines(root).isEmpty())

        val kicker = TileTestKit.texts(root).first { it.text?.value == "SLEEP PHASES" }
        assertEquals(TileType.LABEL, kicker.fontStyle?.size?.value)
        assertEquals(TileAccents.sleepLabel, kicker.fontStyle?.color?.argb)

        val title = TileTestKit.texts(root).first { it.text?.value == "Total 6h 50m · +0.3°" }
        assertEquals("the title is one line", 1, title.maxLines?.value)

        TileTestKit.texts(root).first { it.text?.value == "Score 88" }
    }

    @Test
    fun `the drawer draws the four phases with their own colours and no history is needed`() {
        // Sleep needs no stored series: the night of the reading is the series.
        val four = bars(drawerRoot())
        assertEquals("four phases, four bars", 4, four.size)
        four.forEach {
            assertEquals(
                "four bars read better than a seven-day trend, so they are wider",
                TileGeometry.DRAWER_PHASE_BAR_WIDTH,
                TileTestKit.dpOf(it.width),
                0.01f
            )
        }
        four.forEachIndexed { index, bar ->
            assertEquals(
                "each bar keeps the colour of its phase",
                TileAccents.sleep[index],
                bar.modifiers?.background?.color?.argb
            )
        }

        // Height is the phase's own fraction of the night, not a share of the
        // tallest bar: the fractions sum to one.
        val total = 92f + 82f + 236f + 22f
        fun expected(index: Int): Float =
            (TileGeometry.DRAWER_BAR_TRACK * (listOf(92f, 82f, 236f, 22f)[index] / total))
                .coerceAtLeast(TileGeometry.DRAWER_BAR_MIN_HEIGHT)
        four.forEachIndexed { index, bar ->
            assertEquals(expected(index), TileTestKit.dpOf(bar.height), 0.01f)
        }
    }

    @Test
    fun `the drawer close pill closes and its open pill keeps the app reachable`() {
        val root = drawerRoot()
        val close = clickables(root).single { it.id == TileDrawerState.ID_CLOSE }
        assertTrue(close.onClick is ActionBuilders.LoadAction)

        val open = clickables(root).single { it.onClick is ActionBuilders.LaunchAction }
        assertTrue("only the drawer ids steer the state", open.id.isNullOrEmpty())

        TileTestKit.texts(root).first { it.text?.value == "Close" }
        TileTestKit.texts(root).first { it.text?.value == "Open" }
    }

    @Test
    fun `a plain load shows the face, and the open panel asks not to be refreshed`() {
        rootFor(live, TileDrawerState.ID_OPEN)
        assertTrue(
            "entering the tile shows the face, not the panel the user left open",
            drawerPanels(rootFor(live, lastClickableId = null)).isEmpty()
        )

        val panel = SleepTileService.buildTile(
            strings, packageName, deviceParams, live, TileDrawerState.ID_OPEN
        )
        assertEquals(
            "a refresh would close the panel, so the panel is not refreshed",
            TileDrawerState.DRAWER_FRESHNESS_MS,
            panel.freshnessIntervalMillis
        )
    }

    // ---- previous behaviour ---------------------------------------------

    @Test
    fun `the tile source carries no colour literal and no legacy design import`() {
        val source = java.io.File("src/main/java/io/github/hardbas/wearsync/app/tile/SleepTileService.kt")
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
        val tile = SleepTileService.buildTile(strings, packageName, deviceParams, live)
        assertEquals(60_000L, tile.freshnessIntervalMillis)
        assertEquals(SleepTileService.RESOURCES_VERSION, SleepTileService.buildResources().version)
        assertTrue(
            "the old sunset scene drawables are gone from the resources",
            SleepTileService.buildResources().idToImageMapping.keys.none {
                it in listOf("ic_tile_starfield", "ic_tile_moon", "sun_disc", "horizon_limb")
            }
        )
    }

    private fun widthOf(element: LayoutElementBuilders.LayoutElement): Float = when (element) {
        is LayoutElementBuilders.Text -> TileTestKit.estimatedWidthDp(element)
        is LayoutElementBuilders.Image -> TileTestKit.dpOf(element.width)
        is LayoutElementBuilders.Spacer -> TileTestKit.dpOf(element.width)
        is LayoutElementBuilders.Box -> TileTestKit.dpOf(element.width)
        is LayoutElementBuilders.Row -> element.contents.sumOf { widthOf(it).toDouble() }.toFloat()
        is LayoutElementBuilders.Column -> element.contents.sumOf { widthOf(it).toDouble() }.toFloat()
        else -> 0f
    }
}
