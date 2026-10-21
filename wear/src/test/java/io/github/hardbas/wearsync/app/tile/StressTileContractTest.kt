package io.github.hardbas.wearsync.app.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.tile.design.TileDesign
import io.github.hardbas.wearsync.app.tile.design.TileMetric
import io.github.hardbas.wearsync.app.tile.design.TileStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Executable contract for the Stress tile.
 *
 * Same seam and same assertions as the Heart Rate and Vitals tiles: the layout
 * is built with a fake [TileStrings] and inspected on the JVM, so the four-level
 * stress ladder, the status word, the hero scale and the animator budget are
 * regression-tested without a device.
 */
class StressTileContractTest {

    private val strings = TileStrings { resId -> "s$resId" }

    private val deviceParams = DeviceParametersBuilders.DeviceParameters.Builder()
        .setScreenWidthDp(192)
        .setScreenHeightDp(192)
        .build()

    private val vitals = RingConnVitals(
        lastHrv = 42,
        stressScore = 24,
        refreshTimestamp = 1_730_000_000L
    )

    private val packageName = "io.github.hardbas.wearsync.test"

    private val tile = StressTileService.buildTile(strings, packageName, deviceParams, vitals)

    private val root: LayoutElementBuilders.LayoutElement =
        tile.tileTimeline!!.timelineEntries[0].layout!!.root!!

    private val rootBox = root as LayoutElementBuilders.Box

    private fun childrenOf(element: LayoutElementBuilders.LayoutElement): List<LayoutElementBuilders.LayoutElement> =
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

    private val elements by lazy { flatten(root) }

    /** The animation spec only exists on the internal animatable wrapper. */
    private fun specOf(dynamic: Any?): AnimationParameterBuilders.AnimationSpec? {
        if (dynamic == null) return null
        return runCatching {
            val method = dynamic.javaClass.getMethod("getAnimationSpec")
            method.isAccessible = true
            method.invoke(dynamic) as AnimationParameterBuilders.AnimationSpec
        }.getOrNull()
    }

    private val arcLines: List<LayoutElementBuilders.ArcLine>
        get() = elements.filterIsInstance<LayoutElementBuilders.Arc>()
            .flatMap { it.contents }
            .filterIsInstance<LayoutElementBuilders.ArcLine>()

    /**
     * Arc lines are the contents of an arc, not layout elements, so they are
     * counted through [arcLines]; image tints are counted through the tree.
     */
    private fun animatorCount(): Int {
        val arcAnimators = arcLines.count { line ->
            specOf(line.length?.dynamicValue) != null ||
                (line.modifiers?.opacity?.dynamicValue?.let { specOf(it) } != null)
        }
        val imageAnimators = elements.filterIsInstance<LayoutElementBuilders.Image>()
            .count { specOf(it.colorFilter?.tint?.dynamicValue) != null }
        return arcAnimators + imageAnimators
    }

    private val contentColumn: LayoutElementBuilders.Column
        get() = (rootBox.contents.firstOrNull { it is LayoutElementBuilders.Column } as LayoutElementBuilders.Column)

    private val hero: LayoutElementBuilders.Text
        get() = elements.filterIsInstance<LayoutElementBuilders.Text>()
            .firstOrNull { (it.fontStyle?.size?.value ?: 0f) >= 38f }
            ?: error("no 40sp hero text in the tile")

    private val texts: List<String>
        get() = elements.filterIsInstance<LayoutElementBuilders.Text>().mapNotNull { it.text?.value }

    @Test
    fun `hero is a 40sp tabular display number`() {
        val style = hero.fontStyle!!
        assertTrue("hero must be at least 38sp, was ${style.size?.value}", (style.size?.value ?: 0f) >= 38f)
        assertTrue("hero must use tabular figures", style.settings.isNotEmpty())
        assertEquals("hero must carry the stress score", "24", hero.text?.value)
    }

    @Test
    fun `value arc is a round-capped gradient that keeps its flat fallback`() {
        val value = arcLines.lastOrNull() ?: error("no value arc in the tile")
        assertEquals(LayoutElementBuilders.STROKE_CAP_ROUND, value.strokeCap?.value)
        assertNotNull("arc must carry the gradient brush", value.brush)
        assertNotNull("arc must keep a flat colour fallback", value.color)
    }

    @Test
    fun `root paints the metric backdrop and stays tappable`() {
        assertNotNull(
            "root must carry the ${TileMetric.STRESS.name} gradient",
            rootBox.modifiers?.background?.brush
        )
        assertNotNull("root must stay tappable", rootBox.modifiers?.clickable)
    }

    @Test
    fun `content stacks at most four blocks`() {
        val blocks = contentColumn.contents.filter { it !is LayoutElementBuilders.Spacer }
        assertEquals("content column must stack exactly four blocks", 4, blocks.size)
    }

    @Test
    fun `stress ladder lights exactly one of four levels with the matching accent`() {
        val row = elements.filterIsInstance<LayoutElementBuilders.Row>()
            .firstOrNull { row -> row.contents.filterIsInstance<LayoutElementBuilders.Box>().size == 4 }
            ?: error("no four-segment stress ladder in the tile")

        val segments = row.contents.filterIsInstance<LayoutElementBuilders.Box>()
        assertEquals("ladder needs four segments", 4, segments.size)

        val relaxedAccent = TileDesign.Accent.stress[0] // 24 points -> Relaxed
        val lit = segments.filter { it.modifiers?.background?.color?.argb == relaxedAccent }
        assertEquals("exactly one lit segment", 1, lit.size)
    }

    @Test
    fun `status word for the reading is still on the tile`() {
        assertTrue(
            "no text carries the Relaxed status word, had $texts",
            texts.any { it == "s${R.string.stress_relaxed}" }
        )
    }

    @Test
    fun `hrv reading is still on the tile`() {
        assertTrue("no text carries the HRV figure, had $texts", texts.any { "42" in it })
    }

    @Test
    fun `tile no longer stores the arc sweep in state`() {
        val keys = tile.state?.keyToValueMapping?.keys?.map { it.key } ?: emptyList()
        assertTrue(
            "stress_arc_sweep was replaced by the entrance animation, found $keys",
            "stress_arc_sweep" !in keys
        )
    }

    @Test
    fun `tile spends at most two animators on the whole tree`() {
        val count = animatorCount()
        assertTrue(
            "animators must stay within ${TileDesign.Motion.MAX_ANIMATORS}, found $count",
            count <= TileDesign.Motion.MAX_ANIMATORS
        )
        assertEquals("gauge entrance plus track breath are the only two", 2, count)
    }

    @Test
    fun `previous freshness and resource version still hold`() {
        assertEquals(60_000L, tile.freshnessIntervalMillis)
        assertEquals("oneui8-stress-circular-v1", StressTileService.buildResources().version)
    }

    @Test
    fun `detail tap launches the package it was built for`() {
        val action = rootBox.modifiers?.clickable?.onClick as ActionBuilders.LaunchAction
        assertEquals(packageName, action.androidActivity?.packageName)
    }
}
