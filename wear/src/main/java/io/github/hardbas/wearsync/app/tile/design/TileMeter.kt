package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders

/**
 * The second graphic every tile owns besides the bezel.
 *
 * Five tiles sharing one 260° arc was the reason the carousel read as the same
 * tile repainted. Each metric now gets one horizontal read of its own — a
 * segmented ladder for discrete states, a progress bar for a continuum — built
 * from the same swatch geometry so the family still holds together.
 *
 * Widths are always static dp: protolayout rejects a dynamic width unless the
 * renderer was given matching constraints, which is what used to black-screen
 * these tiles.
 */
object TileMeter {

    /** Discrete ladder: one lit segment, the rest are track. */
    fun ladder(
        count: Int,
        activeIndex: Int,
        accent: Int,
        widthDp: Float = TileDesign.Chrome.LADDER_WIDTH
    ): LayoutElementBuilders.Row {
        require(count > 0) { "ladder needs at least one segment" }
        val gap = TileDesign.Chrome.LADDER_GAP
        val segmentWidth = (widthDp - gap * (count - 1)) / count

        val row = LayoutElementBuilders.Row.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

        repeat(count) { index ->
            if (index > 0) {
                row.addContent(TileChrome.hSpacer(gap))
            }
            val lit = index == activeIndex
            row.addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(dp(segmentWidth))
                    .setHeight(dp(TileDesign.Chrome.LADDER_TRACK_HEIGHT))
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setBackground(
                                ModifiersBuilders.Background.Builder()
                                    .setColor(argb(if (lit) accent else TileDesign.Ink.TRACK))
                                    .setCorner(
                                        ModifiersBuilders.Corner.Builder()
                                            .setRadius(dp(TileDesign.Chrome.LADDER_TRACK_RADIUS))
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
        }
        return row.build()
    }

    /** Continuous bar: a track with a static-width fill drawn from the left edge. */
    fun progress(
        fraction: Float,
        accent: Int,
        widthDp: Float = TileDesign.Chrome.LADDER_WIDTH
    ): LayoutElementBuilders.Box {
        val safe = fraction.coerceIn(0.0f, 1.0f)
        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(widthDp))
            .setHeight(dp(TileDesign.Chrome.LADDER_TRACK_HEIGHT))
            // The fill's position is decided by the PARENT box: aligning the child
            // only aligns the child's own contents and silently centres the fill.
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(TileDesign.Ink.TRACK))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(TileDesign.Chrome.LADDER_TRACK_RADIUS))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(dp(widthDp * safe))
                    .setHeight(dp(TileDesign.Chrome.LADDER_TRACK_HEIGHT))
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setBackground(
                                ModifiersBuilders.Background.Builder()
                                    .setColor(argb(accent))
                                    .setCorner(
                                        ModifiersBuilders.Corner.Builder()
                                            .setRadius(dp(TileDesign.Chrome.LADDER_TRACK_RADIUS))
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
