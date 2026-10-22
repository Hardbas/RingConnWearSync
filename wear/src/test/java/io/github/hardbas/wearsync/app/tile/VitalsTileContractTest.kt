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
import io.github.hardbas.wearsync.app.tile.design.TileShell
import io.github.hardbas.wearsync.app.tile.design.TileResources
import io.github.hardbas.wearsync.app.tile.design.TileStrings
import io.github.hardbas.wearsync.app.tile.design.TileTestKit
import io.github.hardbas.wearsync.app.tile.design.TileType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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
            R.string.tile_drawer_vitals_kicker -> "Ring status"
            R.string.tile_drawer_vitals_title -> "SpO₂ %1\$s · Battery %2\$s"
            R.string.tile_drawer_vitals_meta -> "Est. autonomy ≈ %1\$s days"
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

    /** The drawer state is process-wide, so every test starts and ends closed. */
    @Before
    fun resetDrawer() {
        TileDrawerState.apply(VitalsTileService.TILE_ID, TileDrawerState.ID_CLOSE)
    }

    @After
    fun closeDrawer() {
        TileDrawerState.apply(VitalsTileService.TILE_ID, TileDrawerState.ID_CLOSE)
    }

    private fun rootFor(
        vitals: RingConnVitals,
        lastClickableId: String? = null,
        history: List<DailyHistoryEntry> = emptyList()
    ): LayoutElementBuilders.LayoutElement =
        VitalsTileService.buildTile(strings, packageName, deviceParams, vitals, lastClickableId, history)
            .tileTimeline!!.timelineEntries[0].layout!!.root!!

    private fun drawerRoot(
        vitals: RingConnVitals = live,
        history: List<DailyHistoryEntry> = sevenDays()
    ): LayoutElementBuilders.LayoutElement = rootFor(vitals, TileDrawerState.ID_OPEN, history)

    /** Seven real-looking days, oldest first; the newest saturation is 97. */
    private fun sevenDays(): List<DailyHistoryEntry> = listOf(96, 97, 95, 98, 97, 96, 97).mapIndexed { i, spo2 ->
        DailyHistoryEntry(epochDay = 20_000L + i, spo2 = spo2)
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
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "VITALS" })
    }

    @Test
    fun `only pill opens the drawer and only close closes it`() {
        TileDrawerState.apply(VitalsTileService.TILE_ID, TileDrawerState.ID_OPEN)
        assertTrue(TileDrawerState.isOpen(VitalsTileService.TILE_ID))

        TileDrawerState.apply(VitalsTileService.TILE_ID, TileDrawerState.ID_CLOSE)
        assertTrue(!TileDrawerState.isOpen(VitalsTileService.TILE_ID))

        // Any other id, and a plain refresh with no id, preserve the state.
        TileDrawerState.apply(VitalsTileService.TILE_ID, TileDrawerState.ID_OPEN)
        TileDrawerState.apply(VitalsTileService.TILE_ID, "something-else")
        assertTrue("an unknown id is not a command", TileDrawerState.isOpen(VitalsTileService.TILE_ID))
        TileDrawerState.apply(VitalsTileService.TILE_ID, null)
        assertTrue("a load without an id leaves the tile as it was", TileDrawerState.isOpen(VitalsTileService.TILE_ID))

        // The state is per tile: another tile's tap cannot open this one.
        TileDrawerState.apply(VitalsTileService.TILE_ID, TileDrawerState.ID_CLOSE)
        TileDrawerState.apply(HeartRateTileService.TILE_ID, TileDrawerState.ID_OPEN)
        assertTrue(!TileDrawerState.isOpen(VitalsTileService.TILE_ID))
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

        // The face is gone: no hero, no dial, no face label.
        assertTrue(TileTestKit.texts(root).none { it.text?.value == "72" })
        assertTrue(TileTestKit.texts(root).none { it.text?.value == "VITALS" })
        assertTrue(TileTestKit.arcLines(root).isEmpty())

        val kicker = TileTestKit.texts(root).first { it.text?.value == "RING STATUS" }
        assertEquals(TileType.LABEL, kicker.fontStyle?.size?.value)
        assertEquals(TileAccents.spo2Label, kicker.fontStyle?.color?.argb)

        val title = TileTestKit.texts(root).first { it.text?.value == "SpO₂ 97% · Battery 84%" }
        assertEquals("the title is one line", 1, title.maxLines?.value)

        TileTestKit.texts(root).first { it.text?.value == "Est. autonomy ≈ 5 days" }
    }

    @Test
    fun `the drawer draws one bar per day of history and none without it`() {
        val seven = bars(drawerRoot())
        assertEquals("seven days, seven bars", 7, seven.size)

        // Proportional to the series' own maximum (98); the last bar is today.
        val accent = TileAccents.spo2.last()
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
        // Without history nothing is invented: both readings keep their units.
        assertTrue(TileTestKit.texts(empty).any { it.text?.value == "SpO₂ 97% · Battery 84%" })
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
    fun `an open drawer survives a refresh and closes on its own id`() {
        rootFor(live, TileDrawerState.ID_OPEN)
        val refreshed = rootFor(live, lastClickableId = null)
        assertEquals(1, drawerPanels(refreshed).size)

        val closed = rootFor(live, TileDrawerState.ID_CLOSE)
        assertTrue(drawerPanels(closed).isEmpty())
        assertTrue(TileTestKit.texts(closed).any { it.text?.value == "VITALS" })
    }

    // ---- previous behaviour ---------------------------------------------

    @Test
    fun `previous freshness and resource version still hold`() {
        val tile = VitalsTileService.buildTile(strings, packageName, deviceParams, live)
        assertEquals(30_000L, tile.freshnessIntervalMillis)
        assertEquals(VitalsTileService.RESOURCES_VERSION, VitalsTileService.buildResources().version)
    }
}
