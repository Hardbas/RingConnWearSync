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

    private fun now(): Long = System.currentTimeMillis() / 1000L

    private fun rootFor(vitals: RingConnVitals): LayoutElementBuilders.LayoutElement =
        HeartRateTileService.buildTile(strings, packageName, deviceParams, vitals)
            .tileTimeline!!.timelineEntries[0].layout!!.root!!

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
        // maths here.
        val pearlAngle = TileGeometry.ARC_START + (TileTestKit.arcLines(liveRoot)[2].length?.value ?: 0f)
        val radians = Math.toRadians(pearlAngle.toDouble())
        assertEquals(
            TileGeometry.ARC_RADIUS * kotlin.math.cos(radians).toFloat(),
            bloom.modifiers?.transformation?.translationX?.value ?: 0f,
            0.01f
        )
        assertEquals(
            TileGeometry.ARC_RADIUS * kotlin.math.sin(radians).toFloat(),
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
            .first { it.modifiers?.background?.color?.argb == TileInk.SURFACE }
        val padding = (pill.modifiers?.padding?.start?.value ?: 0f) +
            (pill.modifiers?.padding?.end?.value ?: 0f)
        val width = pill.contents.sumOf { widthOf(it).toDouble() }.toFloat() + padding
        assertTrue(
            "pill ${width}dp must stay under ${TileGeometry.safeWidthDp(TileGeometry.PILL_CENTER_Y)}dp",
            width <= TileGeometry.safeWidthDp(TileGeometry.PILL_CENTER_Y)
        )
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
