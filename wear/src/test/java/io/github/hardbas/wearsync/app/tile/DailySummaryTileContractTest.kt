package io.github.hardbas.wearsync.app.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.data.DailyHistoryEntry
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
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Executable contract for the activity tile: the two rings, the three-reading
 * pill, the steps abbreviation, the measured expressive layer (a light streak
 * sweeping the rim) and the chord budget. Built through the public text seam, so
 * the whole layout is inspectable on the JVM.
 */
class DailySummaryTileContractTest {

    private val strings = TileStrings { resId ->
        when (resId) {
            R.string.steps_short -> "Steps"
            R.string.details -> "Details"
            R.string.tile_drawer_steps_kicker -> "Daily cadence"
            R.string.tile_drawer_steps_title -> "%1\$s steps · %2\$s kcal"
            R.string.tile_drawer_steps_meta -> "%1\$s active min · %2\$s km"
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
        lastClickableId: String? = null,
        history: List<DailyHistoryEntry> = emptyList()
    ): LayoutElementBuilders.LayoutElement =
        DailySummaryTileService.buildTile(strings, packageName, deviceParams, vitals, lastClickableId, history)
            .tileTimeline!!.timelineEntries[0].layout!!.root!!

    /** The drawer state is process-wide, so every test starts and ends closed. */
    @Before
    fun resetDrawer() {
        TileDrawerState.apply(DailySummaryTileService.TILE_ID, TileDrawerState.ID_CLOSE)
    }

    @After
    fun closeDrawer() {
        TileDrawerState.apply(DailySummaryTileService.TILE_ID, TileDrawerState.ID_CLOSE)
    }

    private fun drawerRoot(
        vitals: RingConnVitals = live,
        history: List<DailyHistoryEntry> = sevenDays()
    ): LayoutElementBuilders.LayoutElement = rootFor(vitals, TileDrawerState.ID_OPEN, history)

    /** Seven real-looking days, oldest first; the newest day is today's 8 420. */
    private fun sevenDays(): List<DailyHistoryEntry> =
        listOf(7200, 8400, 6100, 9800, 7600, 8800, 8420).mapIndexed { i, steps ->
            DailyHistoryEntry(epochDay = 20_000L + i, steps = steps)
        }

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
            .filter { TileTestKit.dpOf(it.width) == TileGeometry.DRAWER_BAR_WIDTH }
            .filter { it.modifiers?.background?.color != null }

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
        val rings = lines.filter { it.brush != null }
        assertEquals("two rings", 2, rings.size)
        assertEquals(TileGeometry.RING_THICKNESS_ACTIVITY, TileTestKit.dpOf(rings[0].thickness), 0.001f)
        assertEquals(TileAccents.steps.last(), rings[0].color?.argb)
        assertEquals(TileGeometry.RING_THICKNESS_ACTIVITY, TileTestKit.dpOf(rings[1].thickness), 0.001f)
        assertEquals(TileAccents.kcal.last(), rings[1].color?.argb)
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
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "--" })
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "STEPS" })

        val pill = TileTestKit.boxes(root)
            .first { it.modifiers?.background?.color?.argb == TileShell.PILL_GLASS }
        val pillTexts = pill.contents
            .flatMap(TileTestKit::flatten)
            .filterIsInstance<LayoutElementBuilders.Text>()
            .mapNotNull { it.text?.value }
        assertEquals(
            "the pill falls back to dashes but keeps its unit letters and its dot separators",
            listOf("--m", "·", "--", "·", "--km"),
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
            "a stale tile is a still frame: no streak and no animator",
            TileTestKit.animators(root).isEmpty()
        )
        assertTrue(TileTestKit.imagesOf(root, TileResources.SWEEP).isEmpty())
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
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "STEPS" })
    }

    @Test
    fun `only pill opens the drawer and only close closes it`() {
        TileDrawerState.apply(DailySummaryTileService.TILE_ID, TileDrawerState.ID_OPEN)
        assertTrue(TileDrawerState.isOpen(DailySummaryTileService.TILE_ID))

        TileDrawerState.apply(DailySummaryTileService.TILE_ID, TileDrawerState.ID_CLOSE)
        assertTrue(!TileDrawerState.isOpen(DailySummaryTileService.TILE_ID))

        TileDrawerState.apply(DailySummaryTileService.TILE_ID, TileDrawerState.ID_OPEN)
        TileDrawerState.apply(DailySummaryTileService.TILE_ID, "something-else")
        assertTrue("an unknown id is not a command", TileDrawerState.isOpen(DailySummaryTileService.TILE_ID))
        TileDrawerState.apply(DailySummaryTileService.TILE_ID, null)
        assertTrue("a plain load is not an invitation: the panel closes", !TileDrawerState.isOpen(DailySummaryTileService.TILE_ID))

        TileDrawerState.apply(DailySummaryTileService.TILE_ID, TileDrawerState.ID_CLOSE)
        TileDrawerState.apply(HeartRateTileService.TILE_ID, TileDrawerState.ID_OPEN)
        assertTrue(!TileDrawerState.isOpen(DailySummaryTileService.TILE_ID))
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
    fun `the drawer replaces the face and prints the real trend copy`() {
        val root = drawerRoot()
        assertEquals(1, drawerPanels(root).size)

        assertTrue(TileTestKit.texts(root).none { it.text?.value == "STEPS" })
        assertTrue(TileTestKit.arcLines(root).isEmpty())

        val kicker = TileTestKit.texts(root).first { it.text?.value == "DAILY CADENCE" }
        assertEquals(TileType.LABEL, kicker.fontStyle?.size?.value)
        assertEquals(TileAccents.steps.first(), kicker.fontStyle?.color?.argb)

        val title = TileTestKit.texts(root)
            .first { it.text?.value == "${DailySummaryTileService.stepsText(8420)} steps · 320 kcal" }
        assertEquals("the title is one line", 1, title.maxLines?.value)

        TileTestKit.texts(root).first { it.text?.value == "42 active min · 6.1 km" }
    }

    @Test
    fun `the drawer draws one bar per day of history and none without it`() {
        val seven = bars(drawerRoot())
        assertEquals("seven days, seven bars", 7, seven.size)

        // Proportional to the series' own maximum (9 800); the last bar is today.
        val accent = TileAccents.steps.last()
        assertEquals(accent, seven.last().modifiers?.background?.color?.argb)
        seven.dropLast(1).forEach {
            assertEquals(
                TileInk.withAlpha(accent, TileDrawer.BAR_ALPHA),
                it.modifiers?.background?.color?.argb
            )
            assertTrue(
                "a past bar can reach the full track only when it is the series maximum",
                TileTestKit.dpOf(it.height) <= TileGeometry.DRAWER_BAR_TRACK
            )
        }

        val empty = drawerRoot(history = emptyList())
        assertTrue("no series, no bars", bars(empty).isEmpty())
        TileTestKit.texts(empty).first { it.text?.value == "${DailySummaryTileService.stepsText(8420)} steps · 320 kcal" }
        assertEquals(1, drawerPanels(empty).size)
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

        val panel = DailySummaryTileService.buildTile(
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
}
