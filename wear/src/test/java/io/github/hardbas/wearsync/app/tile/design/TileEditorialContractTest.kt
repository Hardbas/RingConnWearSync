package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.ColorBuilders.SweepGradient
import androidx.wear.protolayout.LayoutElementBuilders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Executable contract of the warm editorial dial.
 *
 * These assertions are what "the Claude Design render" means in code: the exact
 * palette, the type scale, the arc geometry, the measured motion vocabulary and
 * the chord budget. A tile that stops using this vocabulary breaks here instead
 * of drifting silently.
 */
class TileEditorialContractTest {

    private val ramp = TileAccents.heartRate
    private val accent = ramp.last()

    private fun valueArc(): LayoutElementBuilders.Box = TileDial.value(ramp, fraction = 0.5f)

    // ---- S1: palette ----------------------------------------------------

    @Test
    fun `palette is the warm editorial set`() {
        assertEquals(0xFF080807.toInt(), TileInk.BASE)
        assertEquals(0xFFF5F2EB.toInt(), TileInk.PRIMARY)
        assertEquals(0xFFB8B4AA.toInt(), TileInk.SECONDARY)
        assertEquals(0xFF262522.toInt(), TileInk.SURFACE)
        assertEquals(0xFF4A4742.toInt(), TileInk.SURFACE_BORDER)
        assertEquals(0xFFFAF6EE.toInt(), TileInk.PEARL)
        assertEquals(0xFF6E6A62.toInt(), TileInk.DIM)
    }

    @Test
    fun `metric ramps keep the design stops and sizes`() {
        assertEquals(listOf(0xFFF2A68D.toInt(), 0xFFD96B4E.toInt()), TileAccents.heartRate.toList())
        assertEquals(listOf(0xFF93BEDB.toInt(), 0xFF5E97C4.toInt()), TileAccents.spo2.toList())
        assertEquals(listOf(0xFFAED09E.toInt(), 0xFF75A465.toInt()), TileAccents.battery.toList())
        assertEquals(listOf(0xFFA5C896.toInt(), 0xFF73A559.toInt()), TileAccents.steps.toList())
        assertEquals(listOf(0xFFF0C26B.toInt(), 0xFFD97757.toInt()), TileAccents.kcal.toList())
        assertEquals(0xFF86C9BC.toInt(), TileAccents.teal)
        assertEquals(
            listOf(0xFF7B73B8.toInt(), 0xFFB486C9.toInt(), 0xFF82ADC9.toInt(), 0xFFE3B86B.toInt()),
            TileAccents.sleep.toList()
        )
        assertEquals(5, TileAccents.stress.size)
        assertEquals(0xFF75A478.toInt(), TileAccents.stress.first())
        assertEquals(0xFFC95252.toInt(), TileAccents.stress.last())
    }

    @Test
    fun `the bezel track is the accent at sixteen percent`() {
        val track = TileInk.track(accent)
        assertEquals(0x29, (track ushr 24) and 0xFF)
        assertEquals(accent and 0x00FFFFFF, track and 0x00FFFFFF)
    }

    @Test
    fun `ramp interpolates between its stops`() {
        assertEquals(ramp.first(), TileAccents.ramp(ramp, 0f))
        // The design's mix() floors the index, so the last stop is only reached
        // asymptotically: assert every channel lands within 2 of it.
        val near = TileAccents.ramp(ramp, 0.999f)
        listOf(0, 8, 16).forEach { shift ->
            assertEquals(
                ((ramp.last() shr shift) and 0xFF).toDouble(),
                ((near shr shift) and 0xFF).toDouble(),
                2.0
            )
        }
        assertEquals(TileAccents.step(TileAccents.stress, 2), 0xFFE4B860.toInt())
    }

    // ---- S2 / S4: typography and motion ---------------------------------

    @Test
    fun `hero is a medium display number at the design sizes`() {
        val hero = TileType.hero()
        assertEquals(50f, hero.size?.value)
        assertEquals(LayoutElementBuilders.FONT_WEIGHT_MEDIUM, hero.weight?.value)
        assertTrue("hero must be tabular", hero.settings.isNotEmpty())
        assertTrue("hero must be optically tightened", (hero.letterSpacing?.value ?: 0f) < 0f)
        assertEquals(44f, TileType.hero(TileType.HERO_RING).size?.value)
        assertEquals(38f, TileType.hero(TileType.HERO_COMPACT).size?.value)
    }

    @Test
    fun `label is tracked out and stays at least three times under the hero`() {
        val label = TileType.label(accent)
        assertEquals(TileType.LABEL, label.size?.value)
        assertTrue("labels need positive tracking", (label.letterSpacing?.value ?: 0f) > 0f)
        assertTrue(
            "hero must outrank the label at least 3x",
            TileType.HERO >= TileType.LABEL * 3f
        )
    }

    @Test
    fun `subtitle sits between label and hero`() {
        val sub = TileType.sub()
        assertEquals(TileType.SUB, sub.size?.value)
        assertEquals(TileInk.SECONDARY, sub.color?.argb)
        val size = sub.size?.value ?: 0f
        assertTrue(size > TileType.LABEL)
        assertTrue(size < TileType.HERO)
    }

    @Test
    fun `the streak spins a full turn around the tile centre`() {
        val streak = TileAmbient.sweep(
            resourceId = TileResources.SWEEP,
            accent = accent,
            durationMs = TileMotion.SWEEP_MS_PULSE,
            opacity = 0.5f
        )
        assertEquals(TileGeometry.TILE_DP, TileTestKit.dpOf(streak.width), 0.001f)
        assertEquals(accent, streak.colorFilter?.tint?.argb)
        assertEquals("static opacity is enough: the streak does not breathe", 0.5f, streak.modifiers?.opacity?.value ?: 0f, 0.001f)
        assertNull(streak.modifiers?.opacity?.dynamicValue)

        val rotation = streak.modifiers?.transformation?.rotation
        assertEquals("the fallback is a full turn, which looks like no turn", 360f, rotation?.value ?: 0f, 0.001f)
        val spin = TileTestKit.specOf(rotation?.dynamicValue)
        assertEquals(TileMotion.SWEEP_MS_PULSE, TileTestKit.durationMs(spin))
        assertTrue(TileTestKit.loopsForever(spin))
        assertTrue("one property, one animator", TileTestKit.animators(streak).size == 1)
    }

    @Test
    fun `the bloom is positioned on the dial and animates opacity and scale`() {
        val spec = TileMotion.beat(72)
        val bloom = TileAmbient.bloomAt(
            resourceId = TileResources.GLOW,
            angleDeg = TileGeometry.angleOf(0f),
            radiusDp = TileGeometry.ARC_RADIUS,
            sizeDp = TileGeometry.BLOOM_SIZE_DP,
            accent = accent,
            minOpacity = 0.05f,
            maxOpacity = 0.72f,
            minScale = 0.55f,
            maxScale = 1.15f,
            spec = spec
        )
        assertEquals(TileGeometry.BLOOM_SIZE_DP, TileTestKit.dpOf(bloom.width), 0.001f)
        assertEquals(accent, bloom.colorFilter?.tint?.argb)

        // The renderer's zero is 12 o'clock and it sweeps clockwise, so the dial
        // start (230 degrees) is 7:40: down and to the left of the centre.
        val angle = Math.toRadians(TileGeometry.ARC_START.toDouble())
        val x = TileGeometry.ARC_RADIUS * kotlin.math.sin(angle).toFloat()
        val y = -TileGeometry.ARC_RADIUS * kotlin.math.cos(angle).toFloat()
        assertEquals(x, bloom.modifiers?.transformation?.translationX?.value ?: 0f, 0.01f)
        assertEquals(y, bloom.modifiers?.transformation?.translationY?.value ?: 0f, 0.01f)

        assertEquals(0.72f, bloom.modifiers?.opacity?.value ?: 0f, 0.001f)
        assertEquals(1.15f, bloom.modifiers?.transformation?.scaleX?.value ?: 0f, 0.001f)
        assertEquals(1.15f, bloom.modifiers?.transformation?.scaleY?.value ?: 0f, 0.001f)
        assertEquals("opacity, scale X, scale Y", 3, TileTestKit.animators(bloom).size)
        TileTestKit.animators(bloom).forEach {
            assertEquals("one gesture, so the same spec on all three", TileTestKit.durationMs(spec), TileTestKit.durationMs(it))
        }
    }

    @Test
    fun `the polar position is the measured centre-angle convention`() {
        fun translationXAt(angleDeg: Float): Float = TileTestKit.images(
            TileAmbient.bloomAt(
                TileResources.GLOW, angleDeg, 84f, 66f, accent,
                0.05f, 0.72f, 0.55f, 1.15f, TileMotion.swell()
            )
        ).single().modifiers?.transformation?.translationX?.value ?: 0f

        fun translationYAt(angleDeg: Float): Float = TileTestKit.images(
            TileAmbient.bloomAt(
                TileResources.GLOW, angleDeg, 84f, 66f, accent,
                0.05f, 0.72f, 0.55f, 1.15f, TileMotion.swell()
            )
        ).single().modifiers?.transformation?.translationY?.value ?: 0f

        assertEquals("0 degrees is 12 o'clock", 0f, translationXAt(0f), 0.01f)
        assertEquals(-84f, translationYAt(0f), 0.01f)
        assertEquals("90 degrees is 3 o'clock, and clockwise is positive X", 84f, translationXAt(90f), 0.01f)
        assertEquals(0f, translationYAt(90f), 0.01f)
        assertEquals("180 degrees is 6 o'clock", 0f, translationXAt(180f), 0.01f)
        assertEquals(84f, translationYAt(180f), 0.01f)
        assertEquals("270 degrees is 9 o'clock", -84f, translationXAt(270f), 0.01f)
    }

    @Test
    fun `no brush inside the dial builders carries a dynamic value`() {
        val dials = listOf(
            TileDial.track(accent),
            valueArc(),
            TileDial.spectrum(TileAccents.stress, pearlColor = accent, pearlAt = 0.62f),
            TileDial.phases(TileAccents.sleep, floatArrayOf(0.3f, 0.2f, 0.45f, 0.05f)),
            TileDial.rings(
                outer = Ring(TileAccents.spo2, 0.98f, TileGeometry.RING_RADIUS, TileGeometry.RING_THICKNESS),
                inner = Ring(TileAccents.battery, 0.82f, TileGeometry.RING_INNER_RADIUS, TileGeometry.RING_THICKNESS)
            )
        )
        dials.forEach { dial ->
            assertTrue(
                "a brush is snapshotted when the tile is built: a dynamic stop or angle never animates",
                TileTestKit.brushDynamics(dial).isEmpty()
            )
        }
    }

    @Test
    fun `the bezel breathes at the band measured on the watch`() {
        val line = TileTestKit.arcLines(TileDial.track(accent)).single()
        val spec = TileTestKit.specOf(line.modifiers?.opacity?.dynamicValue)
        assertEquals(TileMotion.AMBIENT_MS, TileTestKit.durationMs(spec))
        assertTrue(TileTestKit.loopsForever(spec))
        assertEquals(TileMotion.AMBIENT_MAX, line.modifiers?.opacity?.value ?: 0f, 0.001f)
        assertTrue(TileMotion.AMBIENT_MIN == 0.45f && TileMotion.AMBIENT_MAX == 0.95f)
    }

    @Test
    fun `the halo is a static band, the track carries the motion`() {
        val halos = TileTestKit.arcLines(valueArc()).filter { TileTestKit.dpOf(it.thickness) > TileGeometry.ARC_THICKNESS }
        assertEquals(1, halos.size)
        assertEquals("a static band costs no animator", null, halos.first().modifiers?.opacity)
        assertEquals(
            TileInk.withAlpha(TileAccents.heartRate.last(), TileGeometry.HALO_ALPHA),
            halos.first().color?.argb
        )
    }

    // ---- S3 / S6: geometry ----------------------------------------------

    @Test
    fun `an arc lands on its radius through the container size`() {
        assertEquals(180f, TileGeometry.containerDp(84f, 12f), 0.001f)
        assertEquals(152.5f, TileGeometry.containerDp(71f, 10.5f), 0.001f)
        assertEquals(180f, TileGeometry.containerDp(85f, 10f), 0.001f)
        assertEquals(180f, TileTestKit.dpOf(valueArc().width), 0.001f)
    }

    @Test
    fun `the safe width follows the chord of the round face`() {
        assertEquals(182f, TileGeometry.safeWidthDp(0f), 0.5f)
        assertEquals(134.8f, TileGeometry.safeWidthDp(63f), 0.5f)
        assertTrue(
            "a line further from the centre has less room",
            TileGeometry.safeWidthDp(63f) < TileGeometry.safeWidthDp(-38f)
        )
    }

    @Test
    fun `the value arc is a round-capped gradient with a flat fallback`() {
        val lines = TileTestKit.arcLines(valueArc())
        val value = lines[1]
        assertEquals(LayoutElementBuilders.STROKE_CAP_ROUND, value.strokeCap?.value)
        assertEquals(TileGeometry.ARC_THICKNESS, TileTestKit.dpOf(value.thickness), 0.001f)
        assertEquals(TileGeometry.sweepDegrees(0.5f), value.length?.value ?: 0f, 0.01f)
        assertEquals(accent, value.color?.argb)
        val brush = value.brush
        assertTrue("the arc must carry a sweep gradient", brush is SweepGradient)
        assertEquals(ramp.size, (brush as SweepGradient).colorStops.size)
    }

    @Test
    fun `the pearl is ivory with the metric colour inside`() {
        val lines = TileTestKit.arcLines(TileDial.value(ramp, 0.5f, pearlColor = accent))
        assertEquals(4, lines.size)
        assertEquals(TileGeometry.PEARL_OUTER, TileTestKit.dpOf(lines[2].thickness), 0.001f)
        assertEquals(TileInk.PEARL, lines[2].color?.argb)
        assertEquals(TileGeometry.PEARL_INNER, TileTestKit.dpOf(lines[3].thickness), 0.001f)
        assertEquals(accent, lines[3].color?.argb)
    }

    @Test
    fun `the stress spectrum sweeps five stops across the whole dial`() {
        val spectrum = TileDial.spectrum(TileAccents.stress, pearlColor = TileAccents.stress.first(), pearlAt = 0.62f)
        val lines = TileTestKit.arcLines(spectrum)
        val value = lines.first { (it.brush as? SweepGradient)?.colorStops?.size == 5 }
        assertEquals(TileGeometry.ARC_SPAN, value.length?.value ?: 0f, 0.01f)
        assertEquals(TileAccents.stress.first(), value.color?.argb)
    }

    @Test
    fun `sleep splits its ring into four proportional segments with gaps`() {
        val fractions = floatArrayOf(0.214f, 0.19f, 0.548f, 0.048f)
        val lines = TileTestKit.arcLines(TileDial.phases(TileAccents.sleep, fractions))
        assertEquals("four segments plus four wider halos", 8, lines.size)
        val segments = lines.filter { TileTestKit.dpOf(it.thickness) == TileGeometry.ARC_THICKNESS }
        assertEquals(4, segments.size)
        val total = segments.sumOf { (it.length?.value ?: 0f).toDouble() }
        val expected = TileGeometry.ARC_SPAN - fractions.size * TileGeometry.SEGMENT_GAP_DEGREES
        assertEquals(expected.toDouble(), total, 0.05)
    }

    @Test
    fun `two rings share one rotation and one halo`() {
        val rings = TileDial.rings(
            outer = Ring(TileAccents.spo2, 0.98f, TileGeometry.RING_RADIUS, TileGeometry.RING_THICKNESS),
            inner = Ring(TileAccents.battery, 0.82f, TileGeometry.RING_INNER_RADIUS, TileGeometry.RING_THICKNESS)
        )
        assertTrue(
            "the rings are static: the bezel track carries the breath",
            TileTestKit.animators(rings).isEmpty()
        )

        val boxes = TileTestKit.boxes(rings)
        assertEquals(
            TileGeometry.containerDp(TileGeometry.RING_RADIUS, TileGeometry.RING_THICKNESS),
            TileTestKit.dpOf(boxes.first { it.contents.any { c -> c is LayoutElementBuilders.Arc } }.width),
            0.001f
        )
        assertEquals(3, TileTestKit.arcLines(rings).size)
    }

    @Test
    fun `the empty dial is a bare track with no animators`() {
        val track = TileDial.track(accent, breathing = false)
        assertEquals(1, TileTestKit.arcLines(track).size)
        assertTrue(TileTestKit.animators(track).isEmpty())
    }

    // ---- S3 / S7: chrome ------------------------------------------------

    @Test
    fun `the pill is a solid capsule pinned below the centre`() {
        val pill = TileShell.pill(TileShell.pillText("Detalle", TileInk.PRIMARY))
        assertEquals(TileGeometry.PILL_HEIGHT, TileTestKit.dpOf(pill.height), 0.001f)
        assertEquals(
            TileGeometry.PILL_RADIUS,
            pill.modifiers?.background?.corner?.radius?.value ?: 0f,
            0.001f
        )
        assertEquals(
            TileGeometry.PILL_CENTER_Y,
            pill.modifiers?.transformation?.translationY?.value ?: 0f,
            0.001f
        )
        assertEquals(TileInk.SURFACE, pill.modifiers?.background?.color?.argb)
    }

    @Test
    fun `the glow is a static tinted radial drawable`() {
        val glow = TileShell.glow(TileResources.GLOW, accent)
        assertEquals(TileGeometry.GLOW_SIZE, TileTestKit.dpOf(glow.width), 0.001f)
        assertEquals(accent, glow.colorFilter?.tint?.argb)
        assertEquals(TileGeometry.GLOW_OPACITY, glow.modifiers?.opacity?.value ?: 0f, 0.001f)
        assertTrue(
            "the one infinite animator is the arc band, not the glow drawable",
            TileTestKit.animators(glow).isEmpty()
        )
    }

    @Test
    fun `the stack is label, hero and optional subtitle in that order`() {
        val stack = TileShell.stack(
            label = TileShell.text("PULSO · Z2", TileType.label(accent)),
            hero = TileShell.text("72", TileType.hero()),
            sub = TileShell.text("HRV 48 ms", TileType.sub())
        )
        val content = stack.contents.filter { it !is LayoutElementBuilders.Spacer }
        assertEquals(3, content.size)
        assertTrue(content[0] is LayoutElementBuilders.Text)
        assertTrue(content[1] is LayoutElementBuilders.Text)
        assertTrue(content[2] is LayoutElementBuilders.Text)
    }
}
