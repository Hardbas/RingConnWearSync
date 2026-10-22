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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Executable contract for the pulse tile: the reading, its zone, the measured
 * expressive layer (a spinning light streak plus a bloom that beats at the
 * reading's rate) and the chord budget. Built through the public text seam, so
 * the whole layout is inspectable on the JVM.
 */
class HeartRateTileContractTest {

    private val strings = TileStrings { resId ->
        when (resId) {
            R.string.pulse_short -> "Pulse"
            R.string.hrv_short -> "HRV"
            R.string.details -> "Details"
            R.string.no_data -> "No data"
            R.string.tile_drawer_trend_7d -> "7-day trend"
            R.string.tile_drawer_pulse_title -> "Resting %1\$s · Peak %2\$s bpm"
            R.string.tile_drawer_pulse_meta -> "Avg HRV %1\$s ms · Zone %2\$s"
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
        TileDrawerState.apply(HeartRateTileService.TILE_ID, TileDrawerState.ID_CLOSE)
    }

    @After
    fun closeDrawer() {
        TileDrawerState.apply(HeartRateTileService.TILE_ID, TileDrawerState.ID_CLOSE)
    }

    private fun rootFor(
        vitals: RingConnVitals,
        lastClickableId: String? = null,
        history: List<DailyHistoryEntry> = emptyList()
    ): LayoutElementBuilders.LayoutElement =
        HeartRateTileService.buildTile(
            strings,
            packageName,
            deviceParams,
            vitals,
            lastClickableId,
            history
        ).tileTimeline!!.timelineEntries[0].layout!!.root!!

    private fun drawerRoot(
        vitals: RingConnVitals = live,
        history: List<DailyHistoryEntry> = sevenDays()
    ): LayoutElementBuilders.LayoutElement = rootFor(vitals, TileDrawerState.ID_OPEN, history)

    /** Seven real-looking days, oldest first; the newest resting rate is 56. */
    private fun sevenDays(): List<DailyHistoryEntry> = listOf(52, 51, 54, 50, 55, 53, 56).mapIndexed { i, rhr ->
        DailyHistoryEntry(epochDay = 20_000L + i, rhr = rhr)
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
    fun `the expressive layer is a spinning streak behind the dial`() {
        val streak = TileTestKit.imagesOf(liveRoot, TileResources.SWEEP).single()
        assertEquals(TileGeometry.TILE_DP, TileTestKit.dpOf(streak.width), 0.001f)
        assertEquals(0.50f, streak.modifiers?.opacity?.value ?: 0f, 0.001f)

        val rotation = streak.modifiers?.transformation?.rotation
        assertEquals(
            "a renderer that ignores the animation shows the streak at the top",
            360f,
            rotation?.value ?: 0f,
            0.001f
        )
        val spin = TileTestKit.specOf(rotation?.dynamicValue)
        assertNotNull("the streak must rotate dynamically", spin)
        assertEquals(TileMotion.SWEEP_MS_PULSE, TileTestKit.durationMs(spin))
        assertTrue("the turn loops for ever", TileTestKit.loopsForever(spin))

        assertEquals(
            "drawn first, so it stays behind the dial, the ticks and the number",
            TileResources.SWEEP,
            TileTestKit.images(liveRoot).first().resourceId?.value
        )
    }

    @Test
    fun `the pearl carries a bloom that brightens and grows`() {
        val bloom = TileTestKit.images(liveRoot)
            .single { TileTestKit.specOf(it.modifiers?.transformation?.scaleX?.dynamicValue) != null }
        assertEquals(TileGeometry.BLOOM_SIZE_DP, TileTestKit.dpOf(bloom.width), 0.001f)
        assertEquals(TileGeometry.BLOOM_SIZE_DP, TileTestKit.dpOf(bloom.height), 0.001f)

        // The bloom sits on the pearl: same radius, same angle as the end of the
        // value arc, derived from the arc itself instead of recomputing the bpm
        // maths here. The renderer's zero is 12 o'clock and it sweeps clockwise,
        // so the screen position is (sin, -cos) of the arc angle.
        val pearlAngle = TileGeometry.ARC_START + (TileTestKit.arcLines(liveRoot)[2].length?.value ?: 0f)
        val radians = Math.toRadians(pearlAngle.toDouble())
        assertEquals(
            TileGeometry.ARC_RADIUS * kotlin.math.sin(radians).toFloat(),
            bloom.modifiers?.transformation?.translationX?.value ?: 0f,
            0.01f
        )
        assertEquals(
            -TileGeometry.ARC_RADIUS * kotlin.math.cos(radians).toFloat(),
            bloom.modifiers?.transformation?.translationY?.value ?: 0f,
            0.01f
        )

        val opacity = bloom.modifiers?.opacity
        assertEquals("static fallback is the brightest state", 0.72f, opacity?.value ?: 0f, 0.001f)
        val fade = TileTestKit.specOf(opacity?.dynamicValue)
        assertNotNull("the bloom must brighten dynamically", fade)
        assertEquals(
            "one leg is half a beat of the live 138 bpm",
            TileMotion.beatLegMs(138),
            TileTestKit.durationMs(fade)
        )

        val scaleX = bloom.modifiers?.transformation?.scaleX
        val scaleY = bloom.modifiers?.transformation?.scaleY
        assertEquals(1.15f, scaleX?.value ?: 0f, 0.001f)
        assertEquals(1.15f, scaleY?.value ?: 0f, 0.001f)
        val grow = TileTestKit.specOf(scaleX?.dynamicValue)
        assertNotNull("the bloom must grow dynamically", grow)
        assertEquals(TileTestKit.durationMs(fade), TileTestKit.durationMs(grow))
        assertNotNull(TileTestKit.specOf(scaleY?.dynamicValue))
        assertTrue(TileTestKit.loopsForever(fade) && TileTestKit.loopsForever(grow))
    }

    @Test
    fun `the beat leg is half a beat and stays inside its measured bounds`() {
        assertEquals("60000 / 60 / 2", 500L, TileMotion.beatLegMs(60))
        assertEquals("138 bpm is faster than the floor", 220L, TileMotion.beatLegMs(138))
        assertEquals("a missing rate gets the slowest leg, never a division by zero", 900L, TileMotion.beatLegMs(0))
        assertEquals(TileMotion.BEAT_MIN_MS, TileMotion.beatLegMs(400))
        assertEquals(TileMotion.BEAT_MAX_MS, TileMotion.beatLegMs(30))
    }

    @Test
    fun `live tile spends four animated nodes and lets the track rest`() {
        val specs = TileTestKit.animators(liveRoot)
        assertEquals("streak spin, bloom opacity, bloom scale X and scale Y", 4, specs.size)
        assertTrue("every one of them loops for ever", specs.all(TileTestKit::loopsForever))
        assertEquals(
            "the bloom is this tile's pulse, so the bezel track does not breathe",
            0,
            specs.count { TileTestKit.durationMs(it) == TileMotion.AMBIENT_MS }
        )
    }

    @Test
    fun `no brush carries a dynamic value`() {
        val roots = listOf(
            live, RingConnVitals(refreshTimestamp = 0L), live.copy(refreshTimestamp = now() - 3_600L)
        ).map(::rootFor)
        roots.forEach { root ->
            assertTrue(
                "a brush is snapshotted when the tile is built; a dynamic value there never animates",
                TileTestKit.brushDynamics(root).isEmpty()
            )
        }
    }

    // ---- the in-tile drawer (S12) ---------------------------------------

    @Test
    fun `the face is what a plain load shows`() {
        val root = rootFor(live)
        assertTrue("no drawer panel is painted", drawerPanels(root).isEmpty())
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "PULSE · Z4" })
    }

    @Test
    fun `only pill opens the drawer and only close closes it`() {
        TileDrawerState.apply("pulse", TileDrawerState.ID_OPEN)
        assertTrue(TileDrawerState.isOpen("pulse"))

        TileDrawerState.apply("pulse", TileDrawerState.ID_CLOSE)
        assertTrue(!TileDrawerState.isOpen("pulse"))

        // Any other id, and a plain refresh with no id, preserve the state.
        TileDrawerState.apply("pulse", TileDrawerState.ID_OPEN)
        TileDrawerState.apply("pulse", "something-else")
        assertTrue("an unknown id is not a command", TileDrawerState.isOpen("pulse"))
        TileDrawerState.apply("pulse", null)
        assertTrue("a plain load is not an invitation: the panel closes", !TileDrawerState.isOpen("pulse"))

        // The state is per tile: another tile's tap cannot open this one.
        TileDrawerState.apply("pulse", TileDrawerState.ID_CLOSE)
        TileDrawerState.apply("stress", TileDrawerState.ID_OPEN)
        assertTrue(!TileDrawerState.isOpen("pulse"))
        TileDrawerState.apply("stress", TileDrawerState.ID_CLOSE)
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
        assertTrue(TileTestKit.texts(root).none { it.text?.value == "138" })
        assertTrue(TileTestKit.texts(root).none { it.text?.value == "PULSE · Z4" })
        assertTrue(TileTestKit.arcLines(root).isEmpty())

        val kicker = TileTestKit.texts(root).first { it.text?.value == "7-DAY TREND" }
        assertEquals(TileType.LABEL, kicker.fontStyle?.size?.value)
        assertEquals(TileAccents.heartRateLabel, kicker.fontStyle?.color?.argb)

        val title = TileTestKit.texts(root).first { it.text?.value == "Resting 56 · Peak 168 bpm" }
        assertEquals("the title is one line", 1, title.maxLines?.value)

        TileTestKit.texts(root).first { it.text?.value == "Avg HRV 34 ms · Zone Z4" }
    }

    @Test
    fun `the drawer draws one bar per day of history and none without it`() {
        val seven = bars(drawerRoot())
        assertEquals("seven days, seven bars", 7, seven.size)
        assertEquals(TileGeometry.DRAWER_BAR_WIDTH, TileTestKit.dpOf(seven[0].width), 0.001f)

        // Proportional to the series' own maximum (56); the last bar is today.
        val accent = TileAccents.heartRate.last()
        assertEquals(TileGeometry.DRAWER_BAR_TRACK, TileTestKit.dpOf(seven.last().height), 0.001f)
        assertEquals(accent, seven.last().modifiers?.background?.color?.argb)
        seven.dropLast(1).forEach {
            assertEquals(
                TileInk.withAlpha(accent, TileDrawer.BAR_ALPHA),
                it.modifiers?.background?.color?.argb
            )
            assertTrue(TileTestKit.dpOf(it.height) < TileGeometry.DRAWER_BAR_TRACK)
        }

        val empty = drawerRoot(history = emptyList())
        assertTrue("no series, no bars", bars(empty).isEmpty())
        // Without history nothing is invented: the resting rate is a dash.
        assertTrue(TileTestKit.texts(empty).any { it.text?.value == "Resting -- · Peak 168 bpm" })
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

        val panel = HeartRateTileService.buildTile(
            strings, packageName, deviceParams, live, TileDrawerState.ID_OPEN
        )
        assertEquals(
            "a refresh would close the panel, so the panel is not refreshed",
            TileDrawerState.DRAWER_FRESHNESS_MS,
            panel.freshnessIntervalMillis
        )
    }

    // ---- degraded states ------------------------------------------------

    @Test
    fun `empty tile shows no value arc and runs no animator`() {
        val root = rootFor(RingConnVitals(refreshTimestamp = 0L))
        val lines = TileTestKit.arcLines(root)
        assertEquals("the track survives, the value arc does not", 1, lines.size)
        assertTrue(TileTestKit.animators(root).isEmpty())
        assertTrue("no streak without a reading", TileTestKit.imagesOf(root, TileResources.SWEEP).isEmpty())
        assertTrue(
            "no bloom without a pearl",
            TileTestKit.images(root).none {
                TileTestKit.specOf(it.modifiers?.transformation?.scaleX?.dynamicValue) != null
            }
        )
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
        // Track, static halo, then the value arc.
        val value = TileTestKit.arcLines(root)[2]
        assertEquals(TileAccents.dim(TileAccents.heartRate).last(), value.color?.argb)
        assertTrue(
            "a stale tile is a still frame: no streak, no bloom, no animator",
            TileTestKit.animators(root).isEmpty()
        )
        assertTrue(TileTestKit.imagesOf(root, TileResources.SWEEP).isEmpty())
    }

    @Test
    fun `the removed layers are gone from tree and resources`() {
        val drawables = HeartRateTileService.buildResources().idToImageMapping
        listOf("tile_ecg", "tile_meteor").forEach { dead ->
            assertTrue(
                "$dead was removed",
                drawables.keys.none { it == dead } &&
                    TileTestKit.images(liveRoot).none { it.resourceId?.value == dead }
            )
        }
        assertTrue(
            "the streak must be shipped in the tile's resources",
            drawables.keys.any { it == TileResources.SWEEP }
        )
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
}
