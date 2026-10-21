package io.github.hardbas.wearsync.app.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import io.github.hardbas.wearsync.app.R
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
 * Executable contract for the celestial Sleep tile (spec T6, S1-S5 + S7-S9).
 *
 * Sleep is the only tile with a scene, so besides the shared design-system
 * contract this pins the things that make it Sleep: the starfield/moon/horizon
 * paint order, the untinted three-tone crescent, the sunset geometry constants
 * and the fact that the sunset is the tile's single non-gauge animator.
 *
 * The tile is built through its public text seam, so the whole layout is
 * inspectable on the JVM: no Android context, no emulator, no screenshot.
 */
class SleepTileContractTest {

    /**
     * Real label word, `s<resId>` for everything else. The header-width estimate is
     * only meaningful if the label carries its real length.
     */
    private val strings = TileStrings { resId ->
        if (resId == R.string.sleep_short) "Sleep" else "s$resId"
    }

    private val deviceParams = DeviceParametersBuilders.DeviceParameters.Builder()
        .setScreenWidthDp(192)
        .setScreenHeightDp(192)
        .build()

    /** 88 pts "Excellent", 492 min = 8h 12m, +0.4 °C against a 36.1 °C baseline. */
    private val vitals = RingConnVitals(
        sleepScore = 88,
        sleepDeepMins = 120,
        sleepRemMins = 90,
        sleepLightMins = 282,
        skinTempOffset = 0.4f,
        skinTempBaseline = 36.1f
    )

    private val packageName = "io.github.hardbas.wearsync.test"

    private val tile = SleepTileService.buildTile(strings, packageName, deviceParams, vitals)

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

    /** `Modifiers` is not on the `LayoutElement` interface, so it is read per type. */
    private fun modifiersOf(element: LayoutElementBuilders.LayoutElement): ModifiersBuilders.Modifiers? =
        when (element) {
            is LayoutElementBuilders.Box -> element.modifiers
            is LayoutElementBuilders.Row -> element.modifiers
            is LayoutElementBuilders.Column -> element.modifiers
            is LayoutElementBuilders.Text -> element.modifiers
            is LayoutElementBuilders.Image -> element.modifiers
            is LayoutElementBuilders.Spacer -> element.modifiers
            else -> null
        }

    /** The animation spec only exists on the internal animatable wrapper. */
    private fun specOf(dynamic: Any?): AnimationParameterBuilders.AnimationSpec? {
        if (dynamic == null) return null
        return runCatching {
            val method = dynamic.javaClass.getMethod("getAnimationSpec")
            method.isAccessible = true
            method.invoke(dynamic) as AnimationParameterBuilders.AnimationSpec
        }.getOrNull()
    }

    /**
     * The from/to endpoints only exist on the internal animatable wrapper too.
     * Reading them keeps the sunset geometry pinned to the tile's constants
     * instead of a restated number.
     */
    private fun endpointOf(dynamic: Any?, getter: String): Float? {
        if (dynamic == null) return null
        return runCatching {
            val method = dynamic.javaClass.getMethod(getter)
            method.isAccessible = true
            method.invoke(dynamic) as Float
        }.getOrNull()
    }

    /** Element-level dynamics, including the sun's translation transform. */
    private fun elementAnimatorCount(): Int =
        elements.sumOf { element ->
            val modifiers = modifiersOf(element)
            val props = mutableListOf<Any?>(
                modifiers?.opacity?.dynamicValue,
                modifiers?.transformation?.translationX?.dynamicValue,
                modifiers?.transformation?.translationY?.dynamicValue
            )
            if (element is LayoutElementBuilders.Image) {
                props += element.colorFilter?.tint?.dynamicValue
            }
            props.count { specOf(it) != null }
        }

    /**
     * Gauge animators. `ArcLine` is not a `LayoutElement`, so it never appears in
     * the tree walk above: it is only reachable through `Arc.contents`. Without
     * this second pass the budget assertion would silently count 0 gauge
     * animators and stop testing anything.
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

    private val texts: List<String>
        get() = elements.filterIsInstance<LayoutElementBuilders.Text>().map { it.text?.value ?: "" }

    private val images: List<LayoutElementBuilders.Image>
        get() = elements.filterIsInstance<LayoutElementBuilders.Image>()

    private fun imageById(id: String): LayoutElementBuilders.Image? =
        images.firstOrNull { it.resourceId?.value == id }

    private val sunImage: LayoutElementBuilders.Image
        get() = images.firstOrNull { it.modifiers?.transformation?.translationY?.dynamicValue != null }
            ?: error("no animated sun in the tile")

    /** Private companion constants, read reflectively so the test cannot restate them. */
    private fun geometryConstant(name: String): Float {
        val field = runCatching { SleepTileService::class.java.getDeclaredField(name) }
            .recoverCatching { SleepTileService.Companion::class.java.getDeclaredField(name) }
            .getOrThrow()
        field.isAccessible = true
        return runCatching { field.getFloat(null) }
            .recoverCatching { field.getFloat(SleepTileService.Companion) }
            .getOrThrow()
    }

    // ---- S1/S2: palette and type scale ----------------------------------

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
    fun `root paints the sleep backdrop and stays tappable`() {
        assertNotNull(
            "root must carry the ${TileMetric.SLEEP.name} gradient",
            rootBox.modifiers?.background?.brush
        )
        assertNotNull("root must stay tappable", rootBox.modifiers?.clickable)
    }

    @Test
    fun `detail tap launches the package it was built for`() {
        val action = rootBox.modifiers?.clickable?.onClick as ActionBuilders.LaunchAction
        assertEquals(packageName, action.androidActivity?.packageName)
    }

    // ---- S4: chrome budget ----------------------------------------------

    @Test
    fun `content stacks exactly four blocks`() {
        val blocks = contentColumn.contents.filter { it !is LayoutElementBuilders.Spacer }
        assertEquals(
            "content column must stack exactly four blocks, found ${blocks.size}",
            4,
            blocks.size
        )
    }

    private val contentBlocks: List<LayoutElementBuilders.LayoutElement>
        get() = contentColumn.contents.filter { it !is LayoutElementBuilders.Spacer }

    private val headerRow: LayoutElementBuilders.Row
        get() = contentBlocks[0] as LayoutElementBuilders.Row

    private val metricsCard: LayoutElementBuilders.Box
        get() = contentBlocks[2] as LayoutElementBuilders.Box

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

    private fun textsIn(element: LayoutElementBuilders.LayoutElement): List<String> =
        flatten(element).filterIsInstance<LayoutElementBuilders.Text>().map { it.text?.value ?: "" }

    private val headerTimeLike: List<String>
        get() = textsIn(headerRow)

    @Test
    fun `header is one short metric word that fits inside the bezel`() {
        // Vertical stack (dp): header 15 + 4 + hero 48 + 6 + card 39 + 8 + pill 28
        // = 148, so a centred column starts at -74 and the header band's centre
        // sits 66.5dp above the middle of the 192dp circle.
        val headerY = 66.5f
        val safeWidth = TileDesign.Layout.safeWidthDp(headerY)
        val estimated = estimatedHeaderWidthDp(headerRow)

        assertEquals(
            "the header carries exactly one label (the temperature left it), got $headerTimeLike",
            listOf("SLEEP"),
            headerTimeLike
        )
        assertTrue(
            "estimated header width ${estimated}dp must fit the ${safeWidth}dp safe width at ${headerY}dp above centre",
            estimated <= safeWidth
        )
    }

    @Test
    fun `recovery card is frosted glass carrying quality, duration and temperature`() {
        assertNotNull("the card must carry a hairline edge", metricsCard.modifiers?.border)
        assertEquals(
            "the card must carry the glass fill",
            TileDesign.Ink.GLASS_CARD,
            metricsCard.modifiers?.background?.color?.argb
        )

        val cardTexts = textsIn(metricsCard)
        assertTrue(
            "quality word missing from the card: $cardTexts",
            cardTexts.any { it.equals(strings.text(R.string.excellent), ignoreCase = true) }
        )
        assertTrue(
            "duration missing from the card: $cardTexts",
            cardTexts.any { it.contains("8h 12m") }
        )
        assertTrue(
            "temperature offset missing from the card: $cardTexts",
            cardTexts.any { it.contains("+0.4°C") }
        )
        assertTrue(
            "temperature baseline missing from the card: $cardTexts",
            cardTexts.any { it.contains("36.1°") }
        )
    }

    // ---- S5/S6/S9: motion budget ----------------------------------------

    @Test
    fun `tile spends exactly two animators, the sunset and the moon aura`() {
        val element = elementAnimatorCount()
        val arcs = arcAnimatorCount()
        assertEquals(
            "expected exactly ${TileDesign.Motion.MAX_ANIMATORS} animators " +
                "(sun translationY + moon aura); element props gave $element and " +
                "ArcLine contents gave $arcs, total ${element + arcs}",
            TileDesign.Motion.MAX_ANIMATORS,
            element + arcs
        )
        assertEquals("the sunset and the moon aura are the only element animators", 2, element)
        assertEquals("the sunset entrance replaces the arc sweep", 0, arcs)
    }

    @Test
    fun `the moon keeps breathing after the sun lands`() {
        val moon = imageById("ic_tile_moon") ?: error("no moon in the tile")
        val aura = moon.modifiers?.opacity?.dynamicValue
            ?: error("the moon must carry the perpetual aura animator")

        assertEquals(
            "aura must start at AURA_MIN",
            TileDesign.Motion.AURA_MIN,
            endpointOf(aura, "getFromValue")!!,
            0.01f
        )
        assertEquals(
            "aura must reach AURA_MAX",
            TileDesign.Motion.AURA_MAX,
            endpointOf(aura, "getToValue")!!,
            0.01f
        )
        val spec = specOf(aura)!!
        assertTrue(
            "the aura must repeat forever or the tile dies after one pass",
            spec.repeatable?.hasInfiniteIteration() == true
        )
        assertNull("the moon stays untinted so its three tones survive", moon.colorFilter?.tint)
    }

    @Test
    fun `gauge neither breathes nor sweeps in the sleep tile`() {
        val track = arcLines.firstOrNull() ?: error("no gauge track in the tile")
        assertNull(
            "S9 forbids a second ambient animator: the track must not breathe",
            track.modifiers?.opacity?.dynamicValue
        )
        assertNull(
            "the sunset is the entrance, so the arc must not spend an animator sweeping",
            arcLines.last().length?.dynamicValue
        )
    }

    // ---- No information loss --------------------------------------------

    @Test
    fun `score, quality, duration and temperature all stay readable`() {
        assertTrue("score missing from ${texts}", texts.any { it == "88" })
        assertTrue(
            "quality word missing from $texts",
            texts.any { it.equals(strings.text(R.string.excellent), ignoreCase = true) }
        )
        assertTrue("duration missing from $texts", texts.any { it.contains("8h 12m") })
        assertTrue("temperature offset missing from $texts", texts.any { it.contains("+0.4°C") })
        assertTrue("temperature baseline missing from $texts", texts.any { it.contains("36.1°") })
    }

    @Test
    fun `missing temperature falls back to the no-data state`() {
        val noDataTile = SleepTileService.buildTile(
            strings,
            packageName,
            deviceParams,
            RingConnVitals(sleepScore = 0)
        )
        val noDataTexts = flatten(noDataTile.tileTimeline!!.timelineEntries[0].layout!!.root!!)
            .filterIsInstance<LayoutElementBuilders.Text>()
            .map { it.text?.value ?: "" }

        assertTrue("hero must show placeholder, got $noDataTexts", noDataTexts.any { it == "--" })
        assertTrue(
            "temperature badge must fall back to the no-data word, got $noDataTexts",
            noDataTexts.any { it.contains(strings.text(R.string.no_data)) }
        )
    }

    // ---- Scene preservation (S7) ----------------------------------------

    @Test
    fun `scene is painted starfield, moon, sun, horizon before the content`() {
        val sceneIds = rootBox.contents.filterIsInstance<LayoutElementBuilders.Image>()
            .map { it.resourceId?.value }
        assertEquals(
            "the celestial scene must survive in paint order",
            listOf("ic_tile_starfield", "ic_tile_moon", "sun_disc", "horizon_limb"),
            sceneIds.take(4)
        )
        assertNotNull("sleep icon resource must stay mapped", imageById("ic_vitals_sleep"))
        // The temperature icon left the layout when its capsule became the third
        // badge (the badge carries the status colour in its text instead), so it is
        // only pinned by the resource-registration test below.
    }

    @Test
    fun `crescent moon stays untinted so its three tones survive`() {
        val moon = imageById("ic_tile_moon") ?: error("no moon in the tile")
        assertNull("the moon must not be tinted", moon.colorFilter?.tint)
    }

    @Test
    fun `sunset still runs on the shared geometry constants`() {
        val offsetX = geometryConstant("SUN_OFFSET_X_DP")
        val startY = geometryConstant("SUN_START_Y_DP")
        val endY = geometryConstant("SUN_END_Y_DP")

        assertTrue("the sun must start above the horizon line", startY < 0f)
        assertTrue("the sun must sink well past its start", endY - startY >= 100f)

        val transformation = sunImage.modifiers!!.transformation!!
        assertEquals("sun X offset must come from SUN_OFFSET_X_DP", offsetX, transformation.translationX!!.value, 0.01f)

        val sink = transformation.translationY!!.dynamicValue
        assertEquals("sun must start at SUN_START_Y_DP", startY, endpointOf(sink, "getFromValue")!!, 0.01f)
        assertEquals("sun must end at SUN_END_Y_DP", endY, endpointOf(sink, "getToValue")!!, 0.01f)
        assertEquals(
            "sunset duration",
            1700L,
            specOf(sink)?.animationParameters?.durationMillis
        )
    }

    // ---- Preservation ---------------------------------------------------

    @Test
    fun `previous freshness and resource version still hold`() {
        assertEquals(60_000L, tile.freshnessIntervalMillis)
        assertEquals("oneui8-sleep-celestial-v16", SleepTileService.buildResources().version)
    }

    @Test
    fun `all six scene resources stay registered`() {
        val ids = SleepTileService.buildResources().idToImageMapping.keys
        assertEquals(
            "every celestial image must stay registered: $ids",
            setOf(
                "ic_tile_starfield",
                "ic_tile_moon",
                "ic_vitals_sleep",
                "ic_comp_temp",
                "sun_disc",
                "horizon_limb"
            ),
            ids
        )
    }

}
