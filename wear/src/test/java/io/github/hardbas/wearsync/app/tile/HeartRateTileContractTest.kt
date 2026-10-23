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
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
            else -> "s$resId"
        }
    }

    private val deviceParams = DeviceParameters.Builder()
        .setScreenWidthDp(192)
        .setScreenHeightDp(192)
        .build()

    private val packageName = "io.github.hardbas.wearsync.test"

    private val testZone: ZoneId = ZoneOffset.UTC

    private fun now(): Long = System.currentTimeMillis() / 1000L

    private fun rootFor(
        vitals: RingConnVitals,
        history: List<DailyHistoryEntry> = emptyList(),
        zoneId: ZoneId = testZone
    ): LayoutElementBuilders.LayoutElement =
        HeartRateTileService.buildTile(
            strings,
            packageName,
            deviceParams,
            vitals,
            history,
            zoneId
        ).tileTimeline!!.timelineEntries[0].layout!!.root!!

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

    private fun bars(root: LayoutElementBuilders.LayoutElement): List<LayoutElementBuilders.Box> =
        TileTestKit.boxes(root)
            .filter { TileTestKit.dpOf(it.width) == TileGeometry.SPARK_BAR_WIDTH }
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
        ).map { rootFor(it) }
        roots.forEach { root ->
            assertTrue(
                "a brush is snapshotted when the tile is built; a dynamic value there never animates",
                TileTestKit.brushDynamics(root).isEmpty()
            )
        }
    }

    // ---- the trend row (S14) --------------------------------------------

    @Test
    fun `the trend row draws the last seven resting rates`() {
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
        // 56 is the series maximum, so today's bar is the full 16dp track.
        val row = bars(rootFor(live, sevenDays()))
        assertEquals(TileGeometry.SPARK_HEIGHT, TileTestKit.dpOf(row.last().height), 0.001f)
        listOf(52, 51, 54, 50, 55, 53).forEachIndexed { index, rhr ->
            assertEquals(
                "bar $index is $rhr of 56",
                TileGeometry.SPARK_HEIGHT * (rhr / 56f),
                TileTestKit.dpOf(row[index].height),
                0.01f
            )
        }

        // A quiet day is never invented as a zero-height bar.
        val quiet = listOf(52, 51, 54, 50, 55, 53, 4).mapIndexed { i, rhr ->
            DailyHistoryEntry(epochDay = 30_000L + i, rhr = rhr)
        }
        assertEquals(
            TileGeometry.SPARK_MIN_HEIGHT,
            TileTestKit.dpOf(bars(rootFor(live, quiet)).last().height),
            0.001f
        )
    }

    @Test
    fun `today keeps the full accent and the other days fade to forty five`() {
        val accent = TileAccents.heartRate.last()
        val row = bars(rootFor(live, sevenDays()))
        assertEquals(accent, row.last().modifiers?.background?.color?.argb)
        row.dropLast(1).forEach {
            assertEquals(
                TileInk.withAlpha(accent, TileGeometry.SPARK_PAST_ALPHA),
                it.modifiers?.background?.color?.argb
            )
        }
        row.forEach {
            assertEquals(
                TileGeometry.SPARK_BAR_RADIUS,
                it.modifiers?.background?.corner?.radius?.value ?: 0f,
                0.001f
            )
        }
    }

    @Test
    fun `the sub line carries the average variability and measurement time`() {
        // avgHrv is the average of the ring's reads; without one the last read is
        // the only average there is. With a valid timestamp, HH:mm is appended.
        val expectedTime = HeartRateTileService.formatMeasurementTime(live.refreshTimestamp, testZone)
        assertTrue(TileTestKit.texts(rootFor(live)).any { it.text?.value == "HRV 34 · $expectedTime" })
        assertTrue(
            TileTestKit.texts(rootFor(live.copy(avgHrv = 48))).any { it.text?.value == "HRV 48 · $expectedTime" }
        )
    }

    @Test
    fun `sub falls back to ms without timestamp and time alone without hrv`() {
        val noTime = live.copy(refreshTimestamp = 0L)
        assertTrue(TileTestKit.texts(rootFor(noTime)).any { it.text?.value == "HRV 34 ms" })

        val hrOnly = live.copy(lastHrv = 0, avgHrv = 0)
        val expectedTime = HeartRateTileService.formatMeasurementTime(live.refreshTimestamp, testZone)
        assertTrue(TileTestKit.texts(rootFor(hrOnly)).any { it.text?.value == expectedTime })

        val empty = RingConnVitals(refreshTimestamp = 0L)
        assertTrue(TileTestKit.texts(rootFor(empty)).any { it.text?.value == "No data" })
    }

    @Test
    fun `formatMeasurementTime handles valid, zero, negative and zone offsets`() {
        val fixed = 1_700_000_000L // 2023-11-14T22:13:20Z -> "22:13"
        assertEquals("22:13", HeartRateTileService.formatMeasurementTime(fixed, ZoneOffset.UTC))
        assertEquals("00:13", HeartRateTileService.formatMeasurementTime(fixed, ZoneOffset.ofHours(2)))
        assertEquals(null, HeartRateTileService.formatMeasurementTime(0L, ZoneOffset.UTC))
        assertEquals(null, HeartRateTileService.formatMeasurementTime(-100L, ZoneOffset.UTC))
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
        val root = rootFor(live.copy(refreshTimestamp = now() - 3_600L), sevenDays())
        val hero = TileTestKit.texts(root).first { it.text?.value == "138" }
        assertEquals(TileInk.STALE_HERO_ALPHA, hero.modifiers?.opacity?.value ?: 0f, 0.001f)
        val label = TileTestKit.texts(root).first { it.text?.value?.startsWith("PULSE") == true }
        assertEquals(TileInk.DIM, label.fontStyle?.color?.argb)
        // Track, static halo, then the value arc.
        val value = TileTestKit.arcLines(root)[2]
        assertEquals(TileAccents.dim(TileAccents.heartRate).last(), value.color?.argb)
        // The trend row is part of the face, so it desaturates with it: today
        // keeps the grey accent and the past days keep its forty five percent.
        val row = bars(root)
        assertEquals(7, row.size)
        assertEquals(TileInk.DIM, row.last().modifiers?.background?.color?.argb)
        row.dropLast(1).forEach {
            assertEquals(
                TileInk.withAlpha(TileInk.DIM, TileGeometry.SPARK_PAST_ALPHA),
                it.modifiers?.background?.color?.argb
            )
        }
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
    fun `label, sub and pill fit the chord at their own height`() {
        val label = TileTestKit.texts(liveRoot).first { it.text?.value?.startsWith("PULSE") == true }
        assertTrue(
            "label ${TileTestKit.estimatedWidthDp(label)}dp must stay under " +
                "${TileGeometry.safeWidthDp(TileGeometry.LABEL_Y)}dp",
            TileTestKit.estimatedWidthDp(label) <= TileGeometry.safeWidthDp(TileGeometry.LABEL_Y)
        )

        val sub = TileTestKit.texts(liveRoot).first { it.text?.value?.startsWith("HRV 34") == true }
        assertTrue(
            "sub ${TileTestKit.estimatedWidthDp(sub)}dp must stay under safe width at SUB_Y " +
                "${TileGeometry.safeWidthDp(TileGeometry.SUB_Y)}dp",
            TileTestKit.estimatedWidthDp(sub) <= TileGeometry.safeWidthDp(TileGeometry.SUB_Y)
        )

        val subWithTrend = TileTestKit.texts(rootFor(live, sevenDays())).first { it.text?.value?.startsWith("HRV 34") == true }
        assertTrue(
            "sub with trend ${TileTestKit.estimatedWidthDp(subWithTrend)}dp must stay under safe width at TREND_SUB_Y " +
                "${TileGeometry.safeWidthDp(TileGeometry.TREND_SUB_Y)}dp",
            TileTestKit.estimatedWidthDp(subWithTrend) <= TileGeometry.safeWidthDp(TileGeometry.TREND_SUB_Y)
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
