package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.DynamicBuilders

/**
 * The shell every tile shares: black face, radial glow, dial layers, centred
 * metric stack and the action pill.
 *
 * The stack is centred by the root box, so no tile has to compute its own
 * vertical rhythm; the pill is centred too and then pushed 63dp down by a
 * static translation, which is how the design pins it without absolute layout.
 */
object TileShell {

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
     * with the metric accent. Only sleep breathes here (5500ms); the other four
     * spend their second animator on the arc halo.
     */
    fun glow(
        resourceId: String,
        accent: Int,
        breathing: Boolean = false
    ): LayoutElementBuilders.Image {
        val opacity = if (breathing) {
            TypeBuilders.FloatProp.Builder(TileMotion.SLEEP_MAX)
                .setDynamicValue(
                    DynamicBuilders.DynamicFloat.animate(
                        TileMotion.SLEEP_MIN,
                        TileMotion.SLEEP_MAX,
                        TileMotion.sleepGlow()
                    )
                )
                .build()
        } else {
            TypeBuilders.FloatProp.Builder(TileGeometry.GLOW_OPACITY).build()
        }
        return image(resourceId, TileGeometry.GLOW_SIZE)
            .setColorFilter(
                LayoutElementBuilders.ColorFilter.Builder()
                    .setTint(argb(accent))
                    .build()
            )
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setOpacity(opacity)
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

    /** Label, hero and optional subtitle, centred as one block. */
    fun stack(
        label: LayoutElementBuilders.LayoutElement,
        hero: LayoutElementBuilders.Text,
        sub: LayoutElementBuilders.LayoutElement? = null
    ): LayoutElementBuilders.Column {
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

    /** The one tappable surface at the bottom, pinned by a static translation. */
    fun pill(
        content: LayoutElementBuilders.LayoutElement,
        clickable: ModifiersBuilders.Clickable? = null
    ): LayoutElementBuilders.Box {
        val modifiers = ModifiersBuilders.Modifiers.Builder()
            .setBackground(
                ModifiersBuilders.Background.Builder()
                    .setColor(argb(TileInk.SURFACE))
                    .setCorner(
                        ModifiersBuilders.Corner.Builder()
                            .setRadius(dp(TileGeometry.PILL_RADIUS))
                            .build()
                    )
                    .build()
            )
            .setBorder(
                ModifiersBuilders.Border.Builder()
                    .setWidth(dp(1f))
                    .setColor(argb(TileInk.SURFACE_BORDER))
                    .build()
            )
            .setPadding(
                ModifiersBuilders.Padding.Builder()
                    .setStart(dp(TileGeometry.PILL_PADDING))
                    .setEnd(dp(TileGeometry.PILL_PADDING))
                    .build()
            )
            .setTransformation(
                ModifiersBuilders.Transformation.Builder()
                    .setTranslationY(dp(TileGeometry.PILL_CENTER_Y))
                    .build()
            )
        clickable?.let(modifiers::setClickable)

        return LayoutElementBuilders.Box.Builder()
            .setWidth(wrap())
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

    /** 1x11 hairline between two pill readings. */
    fun pillSeparator(): LayoutElementBuilders.Box =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(1f))
            .setHeight(dp(11f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(TileInk.HAIRLINE))
                            .build()
                    )
                    .build()
            )
            .build()

    fun text(value: String, style: LayoutElementBuilders.FontStyle): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(1)
            .setFontStyle(style)
            .build()

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
