package io.github.hardbas.wearsync.app.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.tile.design.TileDesign
import io.github.hardbas.wearsync.app.tile.design.TileMetric
import io.github.hardbas.wearsync.app.tile.design.TileStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Executable contract for the Vitals tile.
 *
 * Same seam, same assertions as the Heart Rate tile: the layout is built with a
 * fake [TileStrings] and inspected directly, so the SpO2 bar, the type scale
 * and the animator budget are regression-tested without a device.
 */
class VitalsTileContractTest {

    private val strings = TileStrings { resId -> "s$resId" }

    private val deviceParams = DeviceParametersBuilders.DeviceParameters.Builder()
        .setScreenWidthDp(192)
        .setScreenHeightDp(192)
        .build()

    private val vitals = RingConnVitals(
        lastHeartRate = 72,
        lastSpo2 = 97,
        ringBattery = 84,
        refreshTimestamp = 1_730_000_000L
    )

    private val packageName = "io.github.hardbas.wearsync.test"

    private val tile = VitalsTileService.buildTile(strings, packageName, deviceParams, vitals)

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

    /** Element-level dynamics: tint, width/height, opacity. */
    private fun elementAnimatorCount(): Int =
        elements.sumOf { element ->
            val props: List<Any?> = when (element) {
                is LayoutElementBuilders.Box -> listOf(
                    (element.width as? DimensionBuilders.DpProp)?.dynamicValue,
                    (element.height as? DimensionBuilders.DpProp)?.dynamicValue,
                    element.modifiers?.opacity?.dynamicValue
                )
                is LayoutElementBuilders.Row -> listOf(
                    (element.width as? DimensionBuilders.DpProp)?.dynamicValue,
                    (element.height as? DimensionBuilders.DpProp)?.dynamicValue,
                    element.modifiers?.opacity?.dynamicValue
                )
                is LayoutElementBuilders.Column -> listOf(
                    (element.width as? DimensionBuilders.DpProp)?.dynamicValue,
                    (element.height as? DimensionBuilders.DpProp)?.dynamicValue,
                    element.modifiers?.opacity?.dynamicValue
                )
                is LayoutElementBuilders.Text -> listOf(element.modifiers?.opacity?.dynamicValue)
                is LayoutElementBuilders.Image -> listOf(
                    element.colorFilter?.tint?.dynamicValue,
                    element.modifiers?.opacity?.dynamicValue
                )
                else -> emptyList()
            }
            props.count { specOf(it) != null }
        }

    /**
     * Gauge animators. ArcLine is not a `LayoutElement`, so it never appears in
     * the tree walk above: it is only reachable through `Arc.contents`. Dropping
     * this pass silently counts 0 gauge animators and the budget assertion stops
     * testing anything, so keep this second walk explicit.
     */
    private fun arcAnimatorCount(): Int =
        elements.filterIsInstance<LayoutElementBuilders.Arc>()
            .flatMap { it.contents }
            .filterIsInstance<LayoutElementBuilders.ArcLine>()
            .sumOf { line ->
                listOf(
                    line.length?.dynamicValue,
                    line.thickness?.dynamicValue,
                    line.color?.dynamicValue,
                    line.modifiers?.opacity?.dynamicValue
                ).count { specOf(it) != null }
            }

    private fun animatorCount(): Int = elementAnimatorCount() + arcAnimatorCount()

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
            "root must carry the ${TileMetric.VITALS.name} gradient",
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
    fun `spo2 bar fills at most its static track width and never animates`() {
        val fill = elements.filterIsInstance<LayoutElementBuilders.Box>()
            .firstOrNull { it.modifiers?.background?.color?.argb == TileDesign.Accent.spo2 }
            ?: error("no SpO2 progress fill in the tile")

        val width = fill.width as? DimensionBuilders.DpProp
            ?: error("progress fill must use a static dp width")
        assertTrue(
            "fill must stay inside the ${TileDesign.Chrome.LADDER_WIDTH}dp track, was ${width.value}",
            width.value <= TileDesign.Chrome.LADDER_WIDTH
        )
        assertNull("a dynamic width black-screens the tile", width.dynamicValue)
    }

    @Test
    fun `tile no longer stores the arc sweep in state`() {
        val keys = tile.state?.keyToValueMapping?.keys?.map { it.key } ?: emptyList()
        assertTrue(
            "vitals_arc_sweep was replaced by the entrance animation, found $keys",
            "vitals_arc_sweep" !in keys
        )
    }

    @Test
    fun `tile spends exactly the two gauge animators and nothing else`() {
        val element = elementAnimatorCount()
        val arcs = arcAnimatorCount()
        assertEquals(
            "expected exactly ${TileDesign.Motion.MAX_ANIMATORS} animators (value-arc length + " +
                "track opacity); element props gave $element and ArcLine contents gave $arcs, " +
                "total ${element + arcs}",
            TileDesign.Motion.MAX_ANIMATORS,
            element + arcs
        )
    }

    @Test
    fun `previous freshness and resource version still hold`() {
        assertEquals(30_000L, tile.freshnessIntervalMillis)
        assertEquals("oneui8-vitals-circular-v1", VitalsTileService.buildResources().version)
    }

    @Test
    fun `ring battery reading is still on the tile`() {
        val texts = elements.filterIsInstance<LayoutElementBuilders.Text>().mapNotNull { it.text?.value }
        assertTrue("no text carries the battery percentage, had $texts", texts.any { "84" in it })
    }

    @Test
    fun `detail tap launches the package it was built for`() {
        val action = rootBox.modifiers?.clickable?.onClick as ActionBuilders.LaunchAction
        assertEquals(packageName, action.androidActivity?.packageName)
    }
}
