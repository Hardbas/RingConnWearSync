package io.github.hardbas.wearsync.app.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.tile.design.TileDesign
import io.github.hardbas.wearsync.app.tile.design.TileMetric
import io.github.hardbas.wearsync.app.tile.design.TileStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.NumberFormat
import java.util.Locale

/**
 * Executable contract for the Daily Activity (steps) tile.
 *
 * Same seam, same assertions as the converted tiles: the layout is built with a
 * fake [TileStrings] and inspected directly, so the goal bar, the four-block
 * cap, the type scale and the animator budget are regression-tested on the JVM.
 */
class DailySummaryTileContractTest {

    /**
     * Real label word, `s<resId>` for everything else. The header-width estimate is
     * only meaningful if the label carries its real length.
     */
    private val strings = TileStrings { resId ->
        if (resId == R.string.steps_short) "Steps" else "s$resId"
    }

    private val deviceParams = DeviceParametersBuilders.DeviceParameters.Builder()
        .setScreenWidthDp(192)
        .setScreenHeightDp(192)
        .build()

    private val vitals = RingConnVitals(
        liveSteps = 6500,
        targetSteps = 10000,
        activeMinutes = 32,
        liveCalorie = 210,
        distanceKm = 7.4f,
        refreshTimestamp = 1_730_000_000L
    )

    private val packageName = "io.github.hardbas.wearsync.test"

    private val tile = DailySummaryTileService.buildTile(strings, packageName, deviceParams, vitals)

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

    private val contentBlocks: List<LayoutElementBuilders.LayoutElement>
        get() = contentColumn.contents.filter { it !is LayoutElementBuilders.Spacer }

    private val headerRow: LayoutElementBuilders.Row
        get() = contentBlocks[0] as LayoutElementBuilders.Row

    /** Readable dp out of a container dimension; wrap/expand simply contribute 0. */
    private fun dpOf(dimension: Any?): Float =
        (dimension as? DimensionBuilders.DpProp)?.value ?: 0f

    /**
     * Conservative width estimate for one header child. Text is estimated as
     * `chars * sizeSp * 0.62` (bold uppercase average advance), plus the tracking
     * the style adds, then 10% of slop so the estimate can never flatter the tile.
     */
    private fun widthOf(child: LayoutElementBuilders.LayoutElement): Float =
        when (child) {
            is LayoutElementBuilders.Image -> dpOf(child.width)
            is LayoutElementBuilders.Spacer -> dpOf(child.width)
            is LayoutElementBuilders.Text -> {
                val size = child.fontStyle?.size?.value ?: 0f
                val chars = (child.text?.value ?: "").length
                val tracking = (child.fontStyle?.letterSpacing?.value ?: 0f) * size * chars
                (chars * size * 0.62f + tracking) * 1.1f
            }
            else -> 0f
        }

    private fun estimatedHeaderWidthDp(row: LayoutElementBuilders.Row): Float =
        row.contents.fold(0f) { acc, child -> acc + widthOf(child) }

    private val timeText: String by lazy {
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Locale.getDefault())
            .format(java.util.Date(1_730_000_000L * 1000L))
    }

    private val frostedCard: LayoutElementBuilders.Box
        get() = elements.filterIsInstance<LayoutElementBuilders.Box>()
            .firstOrNull { it.modifiers?.background?.color?.argb == TileDesign.Ink.GLASS_CARD }
            ?: error("no frosted card in the tile")

    private val hero: LayoutElementBuilders.Text
        get() = elements.filterIsInstance<LayoutElementBuilders.Text>()
            .firstOrNull { (it.fontStyle?.size?.value ?: 0f) >= 38f }
            ?: error("no 40sp hero text in the tile")

    private val texts: List<String>
        get() = elements.filterIsInstance<LayoutElementBuilders.Text>().mapNotNull { it.text?.value }

    @Test
    fun `header is one short metric word that fits inside the bezel`() {
        // Vertical stack (dp): header 14 + 3 + hero 48 + 5 + card 58 + 6 + pill 28
        // = 162, so a centred column starts at -81 and the header band's centre
        // sits 74dp above the middle of the 192dp circle.
        val headerY = 74f
        val safeWidth = TileDesign.Layout.safeWidthDp(headerY)
        val estimated = estimatedHeaderWidthDp(headerRow)
        val headerTexts = flatten(headerRow).filterIsInstance<LayoutElementBuilders.Text>()
            .map { it.text?.value ?: "" }

        assertEquals("the header carries exactly one label: $headerTexts", 1, headerTexts.size)
        assertEquals("the header label is the short metric word", listOf("STEPS"), headerTexts)
        assertFalse("the timestamp must not be back in the header: $headerTexts", headerTexts.any { "·" in it })
        assertTrue(
            "estimated header width ${estimated}dp must fit the ${safeWidth}dp safe width at ${headerY}dp above centre",
            estimated <= safeWidth
        )
    }

    @Test
    fun `meter sits on a frosted card with the timestamp as its caption`() {
        assertNotNull("the card must carry a hairline edge", frostedCard.modifiers?.border)
        assertNotNull(
            "the card must carry the glass fill",
            frostedCard.modifiers?.background?.color
        )

        val cardTexts = flatten(frostedCard).filterIsInstance<LayoutElementBuilders.Text>()
        assertTrue(
            "the sync timestamp moved into the card, had $cardTexts",
            cardTexts.any { it.text?.value == timeText }
        )
        assertEquals(
            "the timestamp caption uses the muted type step",
            TileDesign.Text.MUTED,
            cardTexts.first { it.text?.value == timeText }.fontStyle?.color?.argb
        )
        assertTrue(
            "the goal line is still in the card",
            cardTexts.any { it.text?.value == "${strings.text(R.string.goal_label)} 10,000 (65%)" }
        )
    }

    @Test
    fun `goal prefix comes from the localized goal label`() {
        assertTrue(
            "the goal line must use R.string.goal_label, had $texts",
            texts.any { it.startsWith("${strings.text(R.string.goal_label)} ") }
        )
        assertFalse("the Spanish literal must be gone, had $texts", texts.any { it.startsWith("Meta ") })
    }

    @Test
    fun `hero is a 40sp tabular display number`() {
        val style = hero.fontStyle!!
        assertTrue("hero must be at least 38sp, was ${style.size?.value}", (style.size?.value ?: 0f) >= 38f)
        assertTrue("hero must use tabular figures", style.settings.isNotEmpty())
        assertEquals(
            "hero must carry the step count",
            NumberFormat.getIntegerInstance(Locale.getDefault()).format(6500),
            hero.text?.value
        )
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
            "root must carry the ${TileMetric.ACTIVITY.name} gradient",
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
    fun `goal bar fills at most its static track width and never animates`() {
        val fill = elements.filterIsInstance<LayoutElementBuilders.Box>()
            .firstOrNull { it.modifiers?.background?.color?.argb == TileDesign.Accent.activity }
            ?: error("no goal progress fill in the tile")

        val width = fill.width as? DimensionBuilders.DpProp
            ?: error("progress fill must use a static dp width")
        assertTrue(
            "fill must stay inside the ${TileDesign.Chrome.LADDER_WIDTH}dp track, was ${width.value}",
            width.value <= TileDesign.Chrome.LADDER_WIDTH
        )
        assertNull("a dynamic width black-screens the tile", width.dynamicValue)
    }

    @Test
    fun `calories, active minutes and goal percentage are still on the tile`() {
        assertTrue("no text carries the calorie reading, had $texts", texts.any { "210" in it })
        assertTrue("no text carries the active minutes, had $texts", texts.any { "32m" in it })
        assertTrue("no text carries the goal percentage, had $texts", texts.any { "65%" in it })
    }

    @Test
    fun `distance reading is still on the tile`() {
        assertTrue("no text carries the distance, had $texts", texts.any { "7.4" in it })
    }

    @Test
    fun `tile no longer stores the arc sweep in state`() {
        val keys = tile.state?.keyToValueMapping?.keys?.map { it.key } ?: emptyList()
        assertTrue(
            "daily_steps_arc_sweep was replaced by the entrance animation, found $keys",
            "daily_steps_arc_sweep" !in keys
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
        assertEquals("oneui8-summary-circular-v1", DailySummaryTileService.buildResources().version)
    }

    @Test
    fun `detail tap launches the package it was built for`() {
        val action = rootBox.modifiers?.clickable?.onClick as ActionBuilders.LaunchAction
        assertEquals(packageName, action.androidActivity?.packageName)
    }
}
