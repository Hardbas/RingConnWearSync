package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders

/**
 * The trend row of S14: the series the drawer used to hide behind a tap, drawn
 * on the face under the hero.
 *
 * A row of bars, oldest first, bottom aligned. Each bar is
 * `value / maximum` of the row's height, floored at [TileGeometry.SPARK_MIN_HEIGHT]
 * so a quiet day still reads as a day instead of disappearing. One metric of one
 * series fades its past days (the accent at [TileGeometry.SPARK_PAST_ALPHA]) and
 * leaves today at full strength; a row of distinct pieces (the four sleep
 * phases) passes [colors] and keeps each piece's own colour.
 *
 * The values are always real: a caller with no series passes an empty list and
 * gets an empty row, which the face skips.
 */
object TileSparkline {

    /**
     * @param values the series, oldest first.
     * @param accent the metric accent: the bar colour, and the one today uses.
     * @param heightDp the tallest bar; every other bar is a share of it.
     * @param barWidthDp width of one bar.
     * @param gapDp separation between two bars.
     * @param colors per-bar colours for a series that is not one metric but a
     *   set of pieces (the sleep phases). When given, the "today is brighter"
     *   rule does not apply.
     * @param maximum the value a full-height bar stands for. `null` means the
     *   series' own maximum, so a trend is read against its own scale.
     */
    fun row(
        values: List<Float>,
        accent: Int,
        heightDp: Float,
        barWidthDp: Float,
        gapDp: Float,
        colors: List<Int>? = null,
        maximum: Float? = null
    ): LayoutElementBuilders.Row {
        val builder = LayoutElementBuilders.Row.Builder()
            .setWidth(wrap())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
        if (values.isEmpty()) {
            return builder.setHeight(wrap()).build()
        }
        builder.setHeight(dp(heightDp))

        // The series' own maximum, so heights are a proportion of this series
        // and never of an assumed scale. A flat-zero series has no maximum to
        // divide by: every bar falls to the floor instead of blowing up.
        val scale = (maximum ?: values.maxOrNull() ?: 0f).coerceAtLeast(0f)

        values.forEachIndexed { index, value ->
            if (index > 0) builder.addContent(TileShell.hSpacer(gapDp))
            val height = if (scale > 0f) {
                (heightDp * (value / scale)).coerceIn(TileGeometry.SPARK_MIN_HEIGHT, heightDp)
            } else {
                TileGeometry.SPARK_MIN_HEIGHT
            }
            val color = colors?.let { it.getOrElse(index) { accent } }
                ?: if (index == values.lastIndex) {
                    accent
                } else {
                    TileInk.withAlpha(accent, TileGeometry.SPARK_PAST_ALPHA)
                }
            builder.addContent(bar(height, barWidthDp, color))
        }
        return builder.build()
    }

    private fun bar(
        heightDp: Float,
        widthDp: Float,
        color: Int
    ): LayoutElementBuilders.Box =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(widthDp))
            .setHeight(dp(heightDp))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(color))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(TileGeometry.SPARK_BAR_RADIUS))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
}
