package io.github.hardbas.wearsync.app.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.data.DailyHistoryEntry
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.tile.design.TileAccents
import io.github.hardbas.wearsync.app.tile.design.TileGeometry
import io.github.hardbas.wearsync.app.tile.design.TileInk
import io.github.hardbas.wearsync.app.tile.design.TileMotion
import io.github.hardbas.wearsync.app.tile.design.TileResources
import io.github.hardbas.wearsync.app.tile.design.TileShell
import io.github.hardbas.wearsync.app.tile.design.TileStrings
import io.github.hardbas.wearsync.app.tile.design.TileTestKit
import io.github.hardbas.wearsync.app.tile.design.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Executable contract for the stress tile: the spectrum, the level word, the
 * measured expressive layer (a light streak plus the halo's own breath) and the
 * chord budget. Built through the public text seam, so the whole layout is
 * inspectable on the JVM.
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

    private fun rootFor(
        vitals: RingConnVitals,
        history: List<DailyHistoryEntry> = emptyList()
    ): LayoutElementBuilders.LayoutElement =
        StressTileService.buildTile(strings, packageName, deviceParams, vitals, history)
            .tileTimeline!!.timelineEntries[0].layout!!.root!!

    /** Seven real-looking days, oldest first; the newest score is 24. */
    private fun sevenDays(): List<DailyHistoryEntry> = listOf(28, 24, 31, 42, 35, 26, 24).mapIndexed { i, score ->
        DailyHistoryEntry(epochDay = 20_000L + i, stressScore = score)
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
        assertTrue(
            "the subtitle names the read: the word HRV, not a unit",
            texts.any { it.text?.value == "HRV 42" }
        )
        assertTrue("the read is not printed in ms", texts.none { it.text?.value == "HRV 42 ms" })
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
        assertEquals("track, halo, spectrum, ivory pearl, accent pearl", 5, lines.size)
        val spectrum = lines.first { it.brush != null }
        assertEquals(TileGeometry.ARC_THICKNESS, TileTestKit.dpOf(spectrum.thickness), 0.001f)
        assertEquals(TileAccents.stress.first(), spectrum.color?.argb)
        assertTrue("the spectrum must keep its brush", spectrum.brush != null)
    }

    // ---- motion ---------------------------------------------------------

    @Test
    fun `the expressive layers are a slow streak and the measured glow breath`() {
        val streak = TileTestKit.imagesOf(liveRoot, TileResources.SWEEP).single()
        assertEquals(TileGeometry.TILE_DP, TileTestKit.dpOf(streak.width), 0.001f)
        assertEquals(0.35f, streak.modifiers?.opacity?.value ?: 0f, 0.001f)
        assertEquals(
            "the streak carries the reading's accent",
            TileAccents.stress.first(),
            streak.colorFilter?.tint?.argb
        )
        assertEquals(
            "a renderer that ignores the animation shows the streak at the top",
            360f,
            streak.modifiers?.transformation?.rotation?.value ?: 0f,
            0.001f
        )
        val spin = TileTestKit.specOf(streak.modifiers?.transformation?.rotation?.dynamicValue)
        assertEquals(TileMotion.SWEEP_MS_STRESS, TileTestKit.durationMs(spin))
        assertTrue(TileTestKit.loopsForever(spin))

        val halo = TileTestKit.images(liveRoot).first { it.modifiers?.opacity?.dynamicValue != null }
        val breath = TileTestKit.specOf(halo.modifiers?.opacity?.dynamicValue)
        assertEquals("the measured glow breath is 4500ms", 4500L, TileTestKit.durationMs(breath))
        assertEquals(0.45f, halo.modifiers?.opacity?.value ?: 0f, 0.001f)
        assertTrue(TileTestKit.loopsForever(breath))
    }

    @Test
    fun `live tile spends three loops on the streak, the glow and the rim breath`() {
        val specs = TileTestKit.animators(liveRoot)
        assertEquals(3, specs.size)
        assertTrue("all three loop for ever", specs.all(TileTestKit::loopsForever))
        assertEquals(
            "the rim keeps its measured period",
            1,
            specs.count { TileTestKit.durationMs(it) == TileMotion.AMBIENT_MS }
        )
        assertEquals(1, specs.count { TileTestKit.durationMs(it) == TileMotion.SWEEP_MS_STRESS })
        assertEquals(1, specs.count { TileTestKit.durationMs(it) == 4500L })
    }

    @Test
    fun `no brush carries a dynamic value`() {
        listOf(live, RingConnVitals(refreshTimestamp = 0L), live.copy(refreshTimestamp = now() - 3_600L))
            .map(::rootFor)
            .forEach { root ->
                assertTrue(
                    "the five-stop spectrum is snapshotted when the tile is built",
                    TileTestKit.brushDynamics(root).isEmpty()
                )
            }
    }

    // ---- degraded states ------------------------------------------------

    @Test
    fun `empty tile keeps the spectrum rim and runs no animator`() {
        val root = rootFor(RingConnVitals(refreshTimestamp = 0L))
        val lines = TileTestKit.arcLines(root)
        assertEquals("bare rim only: the track and the spectrum, no halo, no pearl", 2, lines.size)
        assertTrue(TileTestKit.animators(root).isEmpty())
        assertTrue(TileTestKit.imagesOf(root, TileResources.SWEEP).isEmpty())
        assertEquals(TileAccents.stress.first(), lines.first { it.brush != null }.color?.argb)

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
        // Track, halo, then the spectrum.
        assertEquals(TileInk.DIM, TileTestKit.arcLines(root)[2].color?.argb)
        assertTrue(
            "a stale tile is a still frame: no streak, no glow, no animator",
            TileTestKit.animators(root).isEmpty()
        )
        assertTrue(TileTestKit.imagesOf(root, TileResources.SWEEP).isEmpty())
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
    fun `the trend row draws the last seven scores`() {
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
        // 42 is the series maximum; today's 24 is a share of it, not the track.
        val row = bars(rootFor(live, sevenDays()))
        assertEquals(TileGeometry.SPARK_HEIGHT * (24f / 42f), TileTestKit.dpOf(row.last().height), 0.01f)
        listOf(28, 24, 31, 42, 35, 26).forEachIndexed { index, score ->
            assertEquals(
                "bar $index is $score of 42",
                TileGeometry.SPARK_HEIGHT * (score / 42f),
                TileTestKit.dpOf(row[index].height),
                0.01f
            )
        }

        val quiet = listOf(28, 24, 31, 42, 35, 26, 1).mapIndexed { i, score ->
            DailyHistoryEntry(epochDay = 30_000L + i, stressScore = score)
        }
        assertEquals(
            TileGeometry.SPARK_MIN_HEIGHT,
            TileTestKit.dpOf(bars(rootFor(live, quiet)).last().height),
            0.001f
        )
    }

    @Test
    fun `today keeps the full accent and the other days fade to forty five`() {
        val accent = TileAccents.stress[0]
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
        assertTrue(TileTestKit.texts(root).any { it.text?.value == "HRV 42" })
    }

    @Test
    fun `the pill launches the detail activity again`() {
        val root = rootFor(live, sevenDays())
        assertTrue(
            "the pill is the face's one action",
            clickables(root).any { it.onClick is ActionBuilders.LaunchAction }
        )
        assertTrue(
            "no load action survives the drawer",
            clickables(root).none { it.onClick is ActionBuilders.LoadAction }
        )
    }

    // ---- previous behaviour ---------------------------------------------

    @Test
    fun `previous freshness and resource version still hold`() {
        val tile = StressTileService.buildTile(strings, packageName, deviceParams, live)
        assertEquals(60_000L, tile.freshnessIntervalMillis)
        assertEquals(StressTileService.RESOURCES_VERSION, StressTileService.buildResources().version)
    }
}
