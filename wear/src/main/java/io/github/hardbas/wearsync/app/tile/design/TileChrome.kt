package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.ColorStop
import androidx.wear.protolayout.ColorBuilders.LinearGradient
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TypeBuilders

/**
 * The pieces every tile repeats: backdrop, badge, action pill.
 *
 * Geometry lives in [TileDesign.Chrome], so changing the pill height once
 * changes it in all five tiles.
 */
object TileChrome {

    /**
     * Root modifiers: the per-metric backdrop gradient, the tile tap target and
     * the content cross-fade.
     *
     * protolayout 1.3.0 accepts a [androidx.wear.protolayout.ColorBuilders.Brush]
     * on a background, so the two-layer depth One UI Watch 8 uses (gradient under
     * translucent glass) costs no drawable and no duplicated palette: the stops
     * come from [TileBackdrop]. If a renderer ignores the brush the tile simply
     * falls back to the system dark background.
     */
    fun rootModifiers(
        metric: TileMetric,
        clickable: ModifiersBuilders.Clickable? = null
    ): ModifiersBuilders.Modifiers {
        val stops = metric.gradient
        val brush = LinearGradient.Builder(
            ColorStop.Builder(argb(stops[0]), TypeBuilders.FloatProp.Builder(0.0f).build()).build(),
            ColorStop.Builder(argb(stops[1]), TypeBuilders.FloatProp.Builder(0.55f).build()).build(),
            ColorStop.Builder(argb(stops[2]), TypeBuilders.FloatProp.Builder(1.0f).build()).build()
        )
            .setStartX(dp(24f))
            .setStartY(dp(8f))
            .setEndX(dp(176f))
            .setEndY(dp(192f))
            .build()

        return ModifiersBuilders.Modifiers.Builder()
            .setBackground(ModifiersBuilders.Background.Builder().setBrush(brush).build())
            .apply { clickable?.let { setClickable(it) } }
            .setContentUpdateAnimation(
                ModifiersBuilders.AnimatedVisibility.Builder()
                    .setEnterTransition(ModifiersBuilders.DefaultContentTransitions.fadeIn())
                    .setExitTransition(ModifiersBuilders.DefaultContentTransitions.fadeOut())
                    .build()
            )
            .build()
    }

    /** Quiet capsule for a state word or a small metric. */
    fun badge(text: LayoutElementBuilders.Text): LayoutElementBuilders.Box =
        LayoutElementBuilders.Box.Builder()
            .setWidth(wrap())
            .setHeight(dp(TileDesign.Chrome.BADGE_HEIGHT))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(TileDesign.Ink.GLASS_SUBTLE))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(TileDesign.Chrome.BADGE_RADIUS))
                                    .build()
                            )
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(TileDesign.Chrome.BADGE_PADDING))
                            .setEnd(dp(TileDesign.Chrome.BADGE_PADDING))
                            .build()
                    )
                    .build()
            )
            .addContent(text)
            .build()

    /** The single tappable surface at the bottom of every tile. */
    fun actionPill(
        text: LayoutElementBuilders.Text,
        clickable: ModifiersBuilders.Clickable
    ): LayoutElementBuilders.Box =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(TileDesign.Chrome.ACTION_WIDTH))
            .setHeight(dp(TileDesign.Chrome.ACTION_HEIGHT))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(TileDesign.Ink.GLASS))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(TileDesign.Chrome.ACTION_RADIUS))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(text)
            .build()

    /** Click wiring for "open the detail screen". */
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
}
