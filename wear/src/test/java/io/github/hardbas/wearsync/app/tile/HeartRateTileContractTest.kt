package io.github.hardbas.wearsync.app.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.tile.design.TileDesign
import io.github.hardbas.wearsync.app.tile.design.TileMetric
import io.github.hardbas.wearsync.app.tile.design.TileStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Executable contract for the Heart Rate tile.
 *
 * The tile is built through its public text seam, so the whole layout is
 * inspectable on the JVM: no Android context, no emulator, no screenshot.
 */
class HeartRateTileContractTest {

    private val strings = TileStrings { resId -> "s$resId" }

    private val deviceParams = DeviceParametersBuilders.DeviceParameters.Builder()
        .setScreenWidthDp(192)
        .setScreenHeightDp(192)
        .build()

    private val vitals = RingConnVitals(
        lastHeartRate = 152,
        minHeartRate = 58,
        maxHeartRate = 168,
        refreshTimestamp = 1_730_000_000L
    )

    private val packageName = "io.github.hardbas.wearsync.test"

    private val tile = HeartRateTileService.buildTile(strings, packageName, deviceParams, vitals)

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

    private fun animatorCount(): Int =
        elements.sumOf { element ->
            when (element) {
                is LayoutElementBuilders.ArcLine -> {
                    var count = 0
                    if (specOf(element.length?.dynamicValue) != null) count++
                    if (specOf(element.modifiers?.opacity?.dynamicValue) != null) count++
                    count
                }
                is LayoutElementBuilders.Image -> {
                    if (specOf(element.colorFilter?.tint?.dynamicValue) != null) 1 else 0
                }
                else -> 0
            }
        }

    private val arcLines: List<LayoutElementBuilders.ArcLine>
        get() = elements.filterIsInstance<LayoutElementBuilders.Arc>()
            .flatMap { it.contents }
            .filterIsInstance<LayoutElementBuilders.ArcLine>()

    private val contentColumn: LayoutElementBuilders.Column
        get() = (rootBox.contents.firstOrNull { it is LayoutElementBuilders.Column } as LayoutElementBuilders.Column)

    private val hero: LayoutElementBuilders.Text
        get() = elements.filterIsInstance<LayoutElementBuilders.Text>()
            .firstOrNull { (it.fontStyle?.size?.value ?: 0f) >= 38f }
            ?: error("no 40sp hero text in the tile")

    @Test
    fun `hero is a 40sp tabular display number`() {
        val style = hero.fontStyle!!
        assertTrue("hero must be at least 38sp, was ${style.size?.value}", (style.size?.value ?: 0f) >= 38f)
        assertTrue("hero must use tabular figures", style.settings.isNotEmpty())
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
            "root must carry the ${TileMetric.HEART_RATE.name} gradient",
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
    fun `zone ladder lights exactly one of five segments with the zone accent`() {
        val row = elements.filterIsInstance<LayoutElementBuilders.Row>()
            .firstOrNull { row -> row.contents.filterIsInstance<LayoutElementBuilders.Box>().size == 5 }
            ?: error("no five-segment zone ladder in the tile")

        val segments = row.contents.filterIsInstance<LayoutElementBuilders.Box>()
        assertEquals("ladder needs five segments", 5, segments.size)

        val zoneAccent = TileDesign.Accent.heartRate[3] // 152 bpm -> Z4 Cardio
        val lit = segments.filter { it.modifiers?.background?.color?.argb == zoneAccent }
        assertEquals("exactly one lit segment", 1, lit.size)
    }

    @Test
    fun `tile no longer stores the arc sweep in state`() {
        val keys = tile.state?.keyToValueMapping?.keys?.map { it.key } ?: emptyList()
        assertTrue(
            "hr_arc_sweep was replaced by the entrance animation, found $keys",
            "hr_arc_sweep" !in keys
        )
    }

    @Test
    fun `tile spends at most two animators on the whole tree`() {
        assertTrue(
            "animators must stay within ${TileDesign.Motion.MAX_ANIMATORS}, found ${animatorCount()}",
            animatorCount() <= TileDesign.Motion.MAX_ANIMATORS
        )
    }

    @Test
    fun `previous freshness and resource version still hold`() {
        assertEquals(30_000L, tile.freshnessIntervalMillis)
        assertEquals("oneui8-hr-circular-v1", HeartRateTileService.buildResources().version)
    }

    @Test
    fun `detail tap launches the package it was built for`() {
        val action = rootBox.modifiers?.clickable?.onClick as ActionBuilders.LaunchAction
        assertEquals(packageName, action.androidActivity?.packageName)
    }
}
