package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TypeBuilders

/**
 * The shell every tile shares: black face, radial glow, dial layers, centred
 * metric stack and the action pill.
 *
 * The stack is centred by the root box, so no tile has to compute its own
 * vertical rhythm; the pill is centred too and then pushed 63dp down by a
 * static translation, which is how the design pins it without absolute layout.
 */
object TileShell {

    /**
     * The render's pill material: warm white at 9 % over the face, with a 0.75dp
     * hairline at 16 %. Both are opaque ARGB values because ProtoLayout has no
     * backdrop blur; the alphas are the render's own.
     */
    const val PILL_GLASS = 0x17F5F2EB
    const val PILL_GLASS_BORDER = 0x29F5F2EB

    /** Root of a tile: face, click target, content cross-fade and paint order. */
    fun root(
        clickable: ModifiersBuilders.Clickable? = null,
        layers: List<LayoutElementBuilders.LayoutElement>,
        stack: LayoutElementBuilders.LayoutElement,
        pill: LayoutElementBuilders.LayoutElement? = null
    ): LayoutElementBuilders.Box {
        val modifiers = ModifiersBuilders.Modifiers.Builder()
            .setBackground(
                ModifiersBuilders.Background.Builder()
                    .setColor(argb(TileInk.BASE))
                    .build()
            )
            .setContentUpdateAnimation(
                ModifiersBuilders.AnimatedVisibility.Builder()
                    .setEnterTransition(ModifiersBuilders.DefaultContentTransitions.fadeIn())
                    .setExitTransition(ModifiersBuilders.DefaultContentTransitions.fadeOut())
                    .build()
            )
        clickable?.let(modifiers::setClickable)

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(modifiers.build())
        layers.forEach(root::addContent)
        root.addContent(stack)
        pill?.let(root::addContent)
        return root.build()
    }

    /**
     * The warm halo behind the dial: a drawable with a radial gradient, tinted
     * with the metric accent. Static: the tile's one infinite animator is the
     * arc band, which is a mechanism the target watch is known to animate.
     */
    fun glow(
        resourceId: String,
        accent: Int
    ): LayoutElementBuilders.Image {
        return image(resourceId, TileGeometry.GLOW_SIZE)
            .setColorFilter(
                LayoutElementBuilders.ColorFilter.Builder()
                    .setTint(argb(accent))
                    .build()
            )
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setOpacity(TypeBuilders.FloatProp.Builder(TileGeometry.GLOW_OPACITY).build())
                    .build()
            )
            .build()
    }

    /** Full-face drawable: dial ticks, stars. One image instead of forty elements. */
    fun overlay(resourceId: String): LayoutElementBuilders.Image =
        image(resourceId, TileGeometry.TILE_DP).build()

    private fun image(
        resourceId: String,
        sizeDp: Float
    ): LayoutElementBuilders.Image.Builder =
        LayoutElementBuilders.Image.Builder()
            .setResourceId(resourceId)
            .setWidth(dp(sizeDp))
            .setHeight(dp(sizeDp))
            .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FIT)

    /**
     * Label, hero, optional trend row and optional subtitle.
     *
     * Without a trend row the three blocks are one centred column and space
     * themselves from the top, which is the face as it was before S14. With a
     * trend row each block is pinned to its own centre of the S14 rhythm
     * ([TileGeometry.TREND_LABEL_Y] and friends), because a column cannot put a
     * row between the hero and the subtitle on centres it does not own.
     */
    fun stack(
        label: LayoutElementBuilders.LayoutElement,
        hero: LayoutElementBuilders.Text,
        sub: LayoutElementBuilders.LayoutElement? = null,
        trend: LayoutElementBuilders.LayoutElement? = null
    ): LayoutElementBuilders.LayoutElement {
        if (trend != null) return trendStack(label, hero, trend, sub)

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(label)
            .addContent(spacer(2f))
            .addContent(hero)
        sub?.let {
            column.addContent(spacer(2f))
            column.addContent(it)
        }
        return column.build()
    }

    /**
     * The five-block face: every block is centred in the tile and then moved to
     * its own centre with a static translation, exactly like the pill. A box
     * the height of the tile gives the stack the tile's centre as its origin.
     */
    private fun trendStack(
        label: LayoutElementBuilders.LayoutElement,
        hero: LayoutElementBuilders.Text,
        trend: LayoutElementBuilders.LayoutElement,
        sub: LayoutElementBuilders.LayoutElement?
    ): LayoutElementBuilders.Box {
        val box = LayoutElementBuilders.Box.Builder()
            .setWidth(wrap())
            .setHeight(dp(TileGeometry.TILE_DP))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(pinned(label, TileGeometry.TREND_LABEL_Y))
            .addContent(pinned(hero, TileGeometry.TREND_HERO_Y))
            .addContent(pinned(trend, TileGeometry.TREND_ROW_Y))
        sub?.let { box.addContent(pinned(it, TileGeometry.TREND_SUB_Y)) }
        return box.build()
    }

    /** A block moved to [centerYDp] from the tile centre by a static translation. */
    private fun pinned(
        content: LayoutElementBuilders.LayoutElement,
        centerYDp: Float
    ): LayoutElementBuilders.Box =
        LayoutElementBuilders.Box.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setTransformation(
                        ModifiersBuilders.Transformation.Builder()
                            .setTranslationY(dp(centerYDp))
                            .build()
                    )
                    .build()
            )
            .addContent(content)
            .build()

    /** Sleep labels its quality with a crescent in front of the word. */
    fun withMoon(
        resourceId: String,
        text: LayoutElementBuilders.Text
    ): LayoutElementBuilders.Row {
        val row = LayoutElementBuilders.Row.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                image(resourceId, MOON_SIZE_DP)
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setTransformation(
                                ModifiersBuilders.Transformation.Builder()
                                    .setTranslationY(dp(0.5f))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(hSpacer(4f))
            .addContent(text)
        return row.build()
    }

    /** A row of pieces inside the pill: values, dots and separators. */
    fun pillRow(children: List<LayoutElementBuilders.LayoutElement>): LayoutElementBuilders.Row {
        val row = LayoutElementBuilders.Row.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        children.forEach(row::addContent)
        return row.build()
    }

    /**
     * The one tappable surface at the bottom, pinned by a static translation.
     *
     * `centerY` carries that translation: the face pins its pill below the dial.
     * `width` is the render's fixed 104dp.
     */
    fun pill(
        content: LayoutElementBuilders.LayoutElement,
        clickable: ModifiersBuilders.Clickable? = null,
        centerY: Float? = TileGeometry.PILL_CENTER_Y,
        width: Float? = TileGeometry.PILL_WIDTH
    ): LayoutElementBuilders.Box {
        val modifiers = ModifiersBuilders.Modifiers.Builder()
            .setBackground(
                ModifiersBuilders.Background.Builder()
                    .setColor(argb(PILL_GLASS))
                    .setCorner(
                        ModifiersBuilders.Corner.Builder()
                            .setRadius(dp(TileGeometry.PILL_RADIUS))
                            .build()
                    )
                    .build()
            )
            .setBorder(
                ModifiersBuilders.Border.Builder()
                    .setWidth(dp(TileGeometry.PILL_BORDER))
                    .setColor(argb(PILL_GLASS_BORDER))
                    .build()
            )
            .setPadding(
                ModifiersBuilders.Padding.Builder()
                    .setStart(dp(TileGeometry.PILL_PADDING))
                    .setEnd(dp(TileGeometry.PILL_PADDING))
                    .build()
            )
        if (centerY != null) {
            modifiers.setTransformation(
                ModifiersBuilders.Transformation.Builder()
                    .setTranslationY(dp(centerY))
                    .build()
            )
        }
        clickable?.let(modifiers::setClickable)

        return LayoutElementBuilders.Box.Builder()
            .setWidth(if (width != null) dp(width) else wrap())
            .setHeight(dp(TileGeometry.PILL_HEIGHT))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(modifiers.build())
            .addContent(content)
            .build()
    }

    fun pillText(value: String, color: Int): LayoutElementBuilders.Text =
        text(value, TileType.pill(color))

    /** 5.5dp tone dot that ties a pill reading to the arc it comes from. */
    fun pillDot(color: Int): LayoutElementBuilders.Box =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(5.5f))
            .setHeight(dp(5.5f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(color))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(2.75f))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

    /**
     * The separator between two pill readings: the character `·` at 32 %, 9sp,
     * which is what the render draws. It replaces the old 1dp hairline; the tone
     * dots stay.
     */
    fun pillSeparator(): LayoutElementBuilders.Text = text("·", TileType.separator())

    fun text(
        value: String,
        style: LayoutElementBuilders.FontStyle,
        opacity: Float? = null
    ): LayoutElementBuilders.Text {
        val builder = LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(1)
            // A one-line run that does not fit is ellipsized instead of clipped:
            // a trend line is data, so it must show where it was cut.
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
            .setFontStyle(style)
        if (opacity != null) {
            builder.setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setOpacity(
                        TypeBuilders.FloatProp.Builder(opacity).build()
                    )
                    .build()
            )
        }
        return builder.build()
    }

    /** The one activity a pill (and the face) can launch. */
    fun launchDetail(packageName: String, className: String): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(className)
                            .build()
                    )
                    .build()
            )
            .build()

    fun spacer(heightDp: Float): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder()
            .setWidth(dp(1f))
            .setHeight(dp(heightDp))
            .build()

    fun hSpacer(widthDp: Float): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder()
            .setWidth(dp(widthDp))
            .setHeight(dp(1f))
            .build()

    /** The tiny crescent in front of the sleep quality word. */
    private const val MOON_SIZE_DP = 12f
}
