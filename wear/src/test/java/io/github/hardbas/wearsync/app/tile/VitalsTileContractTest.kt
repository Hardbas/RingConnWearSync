package io.github.hardbas.wearsync.app.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.data.DailyHistoryEntry
import io.github.hardbas.wearsync.app.data.RingConnHistoryStore
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.tile.design.TileAccents
import io.github.hardbas.wearsync.app.tile.design.TileGeometry
import io.github.hardbas.wearsync.app.tile.design.TileInk
import io.github.hardbas.wearsync.app.tile.design.TileMotion
import io.github.hardbas.wearsync.app.tile.design.TileShell
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
            R.string.tile_spo2_only -> "SpO₂ %1\$s"
            R.string.tile_vitals_energy -> "Energy %1\$s"
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
        history: List<DailyHistoryEntry> = emptyList()
    ): LayoutElementBuilders.LayoutElement =
        VitalsTileService.buildTile(strings, packageName, deviceParams, vitals, history)
            .tileTimeline!!.timelineEntries[0].layout!!.root!!

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

    private fun bars(root: LayoutElementBuilders.LayoutElement): List<LayoutElementBuilders.Box> =
        TileTestKit.boxes(root)
            .filter { TileTestKit.dpOf(it.width) == TileGeometry.SPARK_BAR_WIDTH }
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
    fun `hero is the live SpO2 percentage at the ring hero size`() {
        val hero = TileTestKit.texts(liveRoot).first { it.text?.value == "97%" }
        assertEquals(TileType.HERO_RING, hero.fontStyle?.size?.value)
        assertEquals(LayoutElementBuilders.FONT_WEIGHT_MEDIUM, hero.fontStyle?.weight?.value)
    }

    @Test
    fun `hero text fits inside inner ring diameter`() {
        val hero = TileTestKit.texts(liveRoot).first { it.text?.value == "97%" }
        val estimatedWidth = TileTestKit.estimatedWidthDp(hero)
        val innerRingDiameter = 2f * (TileGeometry.RING_INNER_RADIUS - TileGeometry.RING_THICKNESS / 2f)
        assertTrue(
            "hero width $estimatedWidth dp must fit comfortably within inner ring diameter $innerRingDiameter dp",
            estimatedWidth < innerRingDiameter
        )
    }

    @Test
    fun `label is the short metric word in the oxygen accent`() {
        val label = TileTestKit.texts(liveRoot).first { it.text?.value?.startsWith("VITALS") == true }
        assertEquals("VITALS", label.text?.value)
        assertTrue(TileTestKit.isUppercase(label.text!!.value))
        assertEquals(TileType.LABEL, label.fontStyle?.size?.value)
        assertEquals(TileAccents.spo2Label, label.fontStyle?.color?.argb)
    }

    @Test
    fun `without history the sub displays live heart rate in heart rate accent`() {
        val sub = TileTestKit.texts(liveRoot).first { it.text?.value == "HR 72 bpm" }
        assertEquals(TileType.SUB, sub.fontStyle?.size?.value)
        assertEquals(TileAccents.heartRateLabel, sub.fontStyle?.color?.argb)
    }

    @Test
    fun `the pill carries the recovery score with its label`() {
        val texts = TileTestKit.texts(liveRoot).mapNotNull { it.text?.value }
        val expected = "Energy ${RingConnHistoryStore.computeReadinessScore(live)}"
        assertTrue("the recovery score is labelled, had $texts", texts.any { it == expected })
        assertEquals(
            TileType.PILL,
            TileTestKit.texts(liveRoot).first { it.text?.value == expected }.fontStyle?.size?.value
        )
        assertTrue("the ring charge is gone from the face", texts.none { it.contains("84%") })
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
            "the hero falls back to --",
            1,
            TileTestKit.texts(root).count { it.text?.value == "--" }
        )
        assertTrue(
            "the recovery score falls back too",
            TileTestKit.texts(root).any { it.text?.value == "Energy --" }
        )
        assertTrue(
            "the subtitle falls back to oxygen percentage unit",
            TileTestKit.texts(root).any { it.text?.value == "SpO₂ %" }
        )
    }

    @Test
    fun `stale tile desaturates instead of animating`() {
        val root = rootFor(live.copy(refreshTimestamp = now() - 3_600L))
        val hero = TileTestKit.texts(root).first { it.text?.value == "97%" }
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

    // ---- the trend row (S14) --------------------------------------------

    @Test
    fun `the trend row draws the last seven saturations`() {
        val row = bars(rootFor(live, sevenDays()))
        assertEquals("one bar per day of history", 7, row.size)
        row.forEach {
            assertEquals(
                "seven bars of 7dp",
                TileGeometry.SPARK_BAR_WIDTH,
                TileTestKit.dpOf(it.width),
                0.001f
            )
        }
    }

    @Test
    fun `the bars are proportional to the series maximum with a two dp floor`() {
        // 98 is the series maximum; today's 97 is a share of it, not the track.
        val row = bars(rootFor(live, sevenDays()))
        assertEquals(TileGeometry.SPARK_HEIGHT * (97f / 98f), TileTestKit.dpOf(row.last().height), 0.01f)
        listOf(96, 97, 95, 98, 97, 96).forEachIndexed { index, spo2 ->
            assertEquals(
                "bar $index is $spo2 of 98",
                TileGeometry.SPARK_HEIGHT * (spo2 / 98f),
                TileTestKit.dpOf(row[index].height),
                0.01f
            )
        }

        val quiet = listOf(96, 97, 95, 98, 97, 96, 1).mapIndexed { i, spo2 ->
            DailyHistoryEntry(epochDay = 30_000L + i, spo2 = spo2)
        }
        assertEquals(
            TileGeometry.SPARK_MIN_HEIGHT,
            TileTestKit.dpOf(bars(rootFor(live, quiet)).last().height),
            0.001f
        )
    }

    @Test
    fun `today keeps the full accent and the other days fade to forty five`() {
        val accent = TileAccents.spo2.last()
        val row = bars(rootFor(live, sevenDays()))
        assertEquals(accent, row.last().modifiers?.background?.color?.argb)
        row.dropLast(1).forEach {
            assertEquals(
                TileInk.withAlpha(accent, TileGeometry.SPARK_PAST_ALPHA),
                it.modifiers?.background?.color?.argb
            )
        }
    }

    @Test
    fun `the sub carries live heart rate and week average saturation`() {
        val texts = TileTestKit.texts(rootFor(live, sevenDays())).mapNotNull { it.text?.value }
        assertTrue("the live HR and average saturation are in the subtitle", texts.any { it == "HR 72 · 97%" })
        assertTrue("the ring charge is not on the face", texts.none { it.contains("84%") })
    }

    @Test
    fun `with history but without heart rate the sub carries 7d avg`() {
        val vitalsWithoutHr = live.copy(lastHeartRate = 0)
        val texts = TileTestKit.texts(rootFor(vitalsWithoutHr, sevenDays())).mapNotNull { it.text?.value }
        assertTrue("average saturation with 7d label is shown", texts.any { it == "7d avg 97%" })
    }

    @Test
    fun `with a trend row the pill still opens the app`() {
        val root = rootFor(live, sevenDays())
        val texts = TileTestKit.texts(root).mapNotNull { it.text?.value }
        assertTrue(
            "the pill carries the recovery score, had $texts",
            texts.any { it.startsWith("Energy ") }
        )
        assertTrue(
            "the pill launches the detail activity",
            clickables(root).any { it.onClick is ActionBuilders.LaunchAction }
        )
        assertTrue(
            "no load action survives the drawer",
            clickables(root).none { it.onClick is ActionBuilders.LoadAction }
        )
    }

    @Test
    fun `the row sits under the hero on the S14 rhythm`() {
        val stack = TileTestKit.flatten(rootFor(live, sevenDays()))
            .filterIsInstance<LayoutElementBuilders.Box>()
            .first { TileTestKit.dpOf(it.height) == TileGeometry.TILE_DP }
        val centres = stack.contents.mapNotNull {
            (it as? LayoutElementBuilders.Box)?.modifiers?.transformation?.translationY?.value
        }
        assertEquals(
            listOf(
                TileGeometry.TREND_LABEL_Y,
                TileGeometry.TREND_HERO_Y,
                TileGeometry.TREND_ROW_Y,
                TileGeometry.TREND_SUB_Y
            ),
            centres
        )
    }

    @Test
    fun `no history means no row and the face it had before`() {
        val root = rootFor(live)
        assertTrue("nothing is invented", bars(root).isEmpty())
        assertTrue(
            "the stack keeps the column of a face with no trend",
            TileTestKit.flatten(root).any { it is LayoutElementBuilders.Column }
        )
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "HR 72 bpm" })
    }

    // ---- previous behaviour ---------------------------------------------

    @Test
    fun `previous freshness and resource version still hold`() {
        val tile = VitalsTileService.buildTile(strings, packageName, deviceParams, live)
        assertEquals(900_000L, tile.freshnessIntervalMillis)
        assertEquals(VitalsTileService.RESOURCES_VERSION, VitalsTileService.buildResources().version)
    }
}
