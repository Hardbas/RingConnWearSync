package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import androidx.wear.protolayout.expression.DynamicBuilders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The executable contract of the tile design system.
 *
 * These assertions are what "One UI Watch 8 parity" means in code, and they are
 * the net under the five tile files: a tile that stops using the shared gauge,
 * the shared type scale or the shared backdrop breaks here instead of drifting.
 */
class TileDesignContractTest {

    private val pin = TileDesign.Accent.heartRate[4]

    private fun arcLines(
        elements: List<LayoutElementBuilders.LayoutElement>
    ): List<LayoutElementBuilders.ArcLine> =
        elements.filterIsInstance<LayoutElementBuilders.Arc>()
            .flatMap { it.contents }
            .filterIsInstance<LayoutElementBuilders.ArcLine>()

    /**
     * The animation spec only exists on the internal animatable wrapper, so it is
     * read reflectively. Nothing else in the public protocol exposes the animator
     * budget, and the budget is the thing that black-screens tiles when exceeded.
     */
    private fun specOf(dynamic: DynamicBuilders.DynamicFloat?): AnimationParameterBuilders.AnimationSpec? {
        if (dynamic == null) return null
        return runCatching {
            val method = dynamic.javaClass.getMethod("getAnimationSpec")
            method.isAccessible = true
            method.invoke(dynamic) as AnimationParameterBuilders.AnimationSpec
        }.getOrNull()
    }

    private fun durationOf(dynamic: DynamicBuilders.DynamicFloat?): Long =
        specOf(dynamic)?.animationParameters?.durationMillis ?: -1L

    private fun loopsForever(dynamic: DynamicBuilders.DynamicFloat?): Boolean =
        runCatching { specOf(dynamic)?.repeatable?.hasInfiniteIteration() ?: false }
            .getOrDefault(false)

    private fun childrenOf(element: LayoutElementBuilders.LayoutElement) =
        when (element) {
            is LayoutElementBuilders.Box -> element.contents
            is LayoutElementBuilders.Row -> element.contents
            is LayoutElementBuilders.Column -> element.contents
            else -> emptyList()
        }

    private fun flatten(root: LayoutElementBuilders.LayoutElement): List<LayoutElementBuilders.LayoutElement> {
        val out = mutableListOf<LayoutElementBuilders.LayoutElement>()
        fun visit(element: LayoutElementBuilders.LayoutElement) {
            out += element
            childrenOf(element).forEach(::visit)
        }
        visit(root)
        return out
    }

    // ---- S2: typography -------------------------------------------------

    @Test
    fun `hero is a One UI display number, not a secondary read`() {
        val hero = TileDesign.Type.hero()
        assertTrue(
            "hero must be at least 38sp, was ${hero.size?.value}",
            (hero.size?.value ?: 0f) >= 38f
        )
        assertEquals(LayoutElementBuilders.FONT_WEIGHT_BOLD, hero.weight?.value)
        assertTrue("hero must use tabular figures", hero.settings.isNotEmpty())
        assertTrue("hero must be optically tightened", (hero.letterSpacing?.value ?: 0f) < 0f)
    }

    @Test
    fun `labels are tracked out and stay three times under the hero`() {
        val label = TileDesign.Type.label(TileDesign.Text.SECONDARY)
        assertTrue("labels need positive tracking", (label.letterSpacing?.value ?: 0f) > 0f)
        assertTrue(
            "hero must outrank the label at least 3x",
            TileDesign.Type.HERO_SIZE >= TileDesign.Type.LABEL_SIZE * 3f
        )
    }

    // ---- S5/S6: motion --------------------------------------------------

    @Test
    fun `gauge spends exactly the two allowed animators`() {
        val lines = arcLines(TileGauge.elements(pin, 182f))
        assertEquals("two arcs: track and value", 2, lines.size)

        val animators = lines.count { line ->
            specOf(line.length?.dynamicValue) != null ||
                (line.modifiers?.opacity?.dynamicValue?.let { specOf(it) } != null)
        }
        assertTrue(
            "gauge must stay within MAX_ANIMATORS=${TileDesign.Motion.MAX_ANIMATORS}, found $animators",
            animators <= TileDesign.Motion.MAX_ANIMATORS
        )
    }

    @Test
    fun `value arc enters with an eased sweep and the track breathes forever`() {
        val lines = arcLines(TileGauge.elements(pin, 182f))
        val track = lines.first()
        val value = lines.last()

        assertEquals(
            "value arc entrance duration",
            1100L,
            durationOf(value.length?.dynamicValue)
        )
        assertNull(
            "value arc must never loop: an infinite sweep fights the reading",
            specOf(value.length?.dynamicValue)?.repeatable
        )
        assertTrue(
            "track must breathe infinitely",
            loopsForever(track.modifiers?.opacity?.dynamicValue)
        )
        assertEquals(
            "breathing is a slow ambient gesture",
            3400L,
            durationOf(track.modifiers?.opacity?.dynamicValue)
        )
    }

    @Test
    fun `breathing can be switched off for tiles spending their animator elsewhere`() {
        val lines = arcLines(TileGauge.elements(pin, 182f, breathing = false))
        assertNull(lines.first().modifiers?.opacity?.dynamicValue)
        assertNotNull(lines.last().length?.dynamicValue)
    }

    // ---- S8: arc treatment ----------------------------------------------

    @Test
    fun `arc keeps round caps, a sweep brush and a flat colour fallback`() {
        val value = arcLines(TileGauge.elements(pin, 182f)).last()
        assertEquals(LayoutElementBuilders.STROKE_CAP_ROUND, value.strokeCap?.value)
        assertNotNull("arc must carry the gradient brush", value.brush)
        assertNotNull(
            "arc must keep a flat colour so an ignored brush degrades to today's look",
            value.color
        )
        assertEquals(
            TileDesign.Chrome.ARC_THICKNESS.toDouble(),
            (value.thickness?.value ?: 0f).toDouble(),
            0.001
        )
    }

    @Test
    fun `sweep mapping is clamped to the bezel`() {
        assertEquals(TileGauge.MIN_SWEEP.toDouble(), TileGauge.sweepFor(0f).toDouble(), 0.001)
        assertEquals(TileGauge.MIN_SWEEP.toDouble(), TileGauge.sweepFor(-1f).toDouble(), 0.001)
        assertEquals(
            TileDesign.Chrome.ARC_SPAN.toDouble(),
            TileGauge.sweepFor(1f).toDouble(),
            0.001
        )
        assertEquals(
            TileDesign.Chrome.ARC_SPAN.toDouble(),
            TileGauge.sweepFor(9f).toDouble(),
            0.001
        )
        assertEquals(
            (TileDesign.Chrome.ARC_SPAN / 2f).toDouble(),
            TileGauge.sweepFor(0.5f).toDouble(),
            0.001
        )
    }

    // ---- S3: depth ------------------------------------------------------

    @Test
    fun `every metric paints its own gradient backdrop`() {
        TileMetric.entries.forEach { metric ->
            assertEquals("${metric.name} needs three stops", 3, metric.gradient.size)
            assertNotNull(
                "${metric.name} must have a gradient background",
                TileChrome.rootModifiers(metric).background?.brush
            )
        }
        assertTrue(
            "metrics must not share a backdrop",
            !TileMetric.HEART_RATE.gradient.contentEquals(TileMetric.SLEEP.gradient)
        )
    }

    // ---- S7: meter hygiene ---------------------------------------------

    @Test
    fun `ladder lights exactly one segment`() {
        val row = TileMeter.ladder(count = 5, activeIndex = 2, accent = pin)
        val segments = row.contents.filterIsInstance<LayoutElementBuilders.Box>()
        assertEquals(5, segments.size)
        val lit = segments.count { box -> box.modifiers?.background?.color?.argb == pin }
        assertEquals("exactly one lit segment", 1, lit)
    }

    @Test
    fun `progress fill grows from the left edge, not from the centre`() {
        val track = TileMeter.progress(fraction = 0.25f, accent = pin)
        assertEquals(
            "the parent box positions the fill; aligning the child only centres it",
            LayoutElementBuilders.HORIZONTAL_ALIGN_START,
            track.horizontalAlignment?.value
        )
        val fill = track.contents.filterIsInstance<LayoutElementBuilders.Box>().single()
        assertEquals(
            (TileDesign.Chrome.LADDER_WIDTH * 0.25f).toDouble(),
            ((fill.width as? DimensionBuilders.DpProp)?.value ?: -1f).toDouble(),
            0.001
        )
    }

    @Test
    fun `meter widths are static so the renderer never rejects the layout`() {
        val roots = listOf(
            TileMeter.ladder(count = 5, activeIndex = 2, accent = pin),
            TileMeter.progress(fraction = 0.42f, accent = pin)
        )
        roots.flatMap { flatten(it) }.forEach { element ->
            if (element is LayoutElementBuilders.Box) {
                assertNull(
                    "dynamic width black-screens the tile; keep widths static dp",
                    (element.width as? DimensionBuilders.DpProp)?.dynamicValue
                )
            }
        }
    }
}
