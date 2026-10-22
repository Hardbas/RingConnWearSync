package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders

/**
 * The type scale of the editorial dial.
 *
 * One hero per tile, one short label above it, one quiet subtitle below it.
 * The hero is a *medium* weight (500) number with tabular figures and negative
 * tracking: at 50sp a bold face reads as a marketing poster, a medium face with
 * tight tracking reads as a watch dial.
 */
object TileType {

    /** Heart rate, stress and sleep. */
    const val HERO = 50f

    /** Vitals: a second reading shares the face with the pulse. */
    const val HERO_RING = 44f

    /** Steps: the widest number of the five (up to "12.4k"). */
    const val HERO_COMPACT = 38f

    const val LABEL = 10.5f

    /** Sleep labels its quality in sentence case instead of an uppercase word. */
    const val SLEEP_LABEL = 12f

    const val SUB = 12f
    const val PILL = 10f

    /** Optically tightened hero, as the design sets it. */
    const val HERO_TRACKING = -0.03f

    /** Tracked-out uppercase label. */
    const val LABEL_TRACKING = 0.09f
    const val SLEEP_TRACKING = 0.02f

    /**
     * The protocol has no semibold weight (400 / 500 / 700), so the design's 600
     * label lands on bold: at 10.5sp with 0.09em tracking that is what reads as
     * the design's label.
     */
    private const val LABEL_WEIGHT = LayoutElementBuilders.FONT_WEIGHT_BOLD

    private fun style(
        size: Float,
        weight: Int,
        color: Int,
        tracking: Float,
        tabular: Boolean = false
    ): LayoutElementBuilders.FontStyle {
        val builder = LayoutElementBuilders.FontStyle.Builder()
            .setSize(sp(size))
            .setWeight(weight)
            .setColor(argb(color))
            .setLetterSpacing(DimensionBuilders.em(tracking))
        if (tabular) {
            builder.setSettings(LayoutElementBuilders.FontSetting.tabularNum())
        }
        return builder.build()
    }

    /** The one number the tile is about. */
    fun hero(
        size: Float = HERO,
        color: Int = TileInk.PRIMARY
    ): LayoutElementBuilders.FontStyle =
        style(size, LayoutElementBuilders.FONT_WEIGHT_MEDIUM, color, HERO_TRACKING, tabular = true)

    /** Uppercase metric word above the hero. */
    fun label(color: Int): LayoutElementBuilders.FontStyle =
        style(LABEL, LABEL_WEIGHT, color, LABEL_TRACKING)

    /** Sleep's quality word: sentence case, slightly larger, barely tracked. */
    fun sleepLabel(color: Int): LayoutElementBuilders.FontStyle =
        style(SLEEP_LABEL, LayoutElementBuilders.FONT_WEIGHT_MEDIUM, color, SLEEP_TRACKING)

    /** The line under the hero: duration, HRV, level. */
    fun sub(color: Int = TileInk.SECONDARY): LayoutElementBuilders.FontStyle =
        style(SUB, LayoutElementBuilders.FONT_WEIGHT_MEDIUM, color, HERO_TRACKING, tabular = true)

    /** Pill copy: small, tabular, tight. */
    fun pill(color: Int): LayoutElementBuilders.FontStyle =
        style(PILL, LayoutElementBuilders.FONT_WEIGHT_MEDIUM, color, -0.04f, tabular = true)
}
