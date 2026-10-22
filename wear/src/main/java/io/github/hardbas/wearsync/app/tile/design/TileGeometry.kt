package io.github.hardbas.wearsync.app.tile.design

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The measured geometry of one 192dp round tile.
 *
 * Two facts drive everything here:
 *  1. an `ArcLine` has no radius — the arc is drawn along the edge of whatever
 *     box contains it, so a given radius requires a box of
 *     `2 * (radius + thickness / 2)` (the stroke sits inside the bounds);
 *  2. a 192dp tile is a circle of radius 96dp, so a line sitting `y` from the
 *     centre only has `2 * sqrt(96^2 - y^2)` of usable width.
 */
object TileGeometry {

    const val TILE_DP = 192f
    const val RADIUS_DP = 96f

    /** Rim kept free on both sides of any text line. */
    const val BEZEL_INSET_DP = 10f

    /** One UI dial silhouette: 260 degrees, starting at 7:40 and leaving the gap at the bottom. */
    const val ARC_SPAN = 260f

    /**
     * Anchor angle of the dial start, in the renderer's own convention.
     *
     * The renderer measures angles from 12 o'clock and sweeps clockwise, not
     * from 3 o'clock like the design render does. Measured on the SM-L500: an
     * anchor of 140 puts the arc start at 49 degrees from 3 o'clock (4:40 on the
     * face) and the gap on the right flank, 100 degrees away from where the tick
     * drawable puts it. 140 + 90 lands the start at 7:40 with the gap at the
     * bottom, which is what [TileGeometry.angleOf] and the tick overlay assume.
     */
    const val ARC_START = 230f

    /** The single bezel arc of the pulse, stress and sleep tiles. */
    const val ARC_RADIUS = 84f
    const val ARC_THICKNESS = 12f

    /** The outer ring of the two-ring tiles (vitals, activity). */
    const val RING_RADIUS = 85f

    /** The inner ring: 14dp inside the outer one, leaving a 3.5dp gap. */
    const val RING_INNER_RADIUS = 71f

    const val RING_THICKNESS = 10.5f
    const val RING_THICKNESS_ACTIVITY = 11.5f

    /** Pearl marker: ivory body with the metric accent in its middle. */
    const val PEARL_OUTER = 17f
    const val PEARL_INNER = 7f

    /** Angular length of the pearl's arc. With round caps it renders as a disc. */
    const val PEARL_DEGREES = 1f

    /** The arc halo is the same arc, 4dp wider on each side. */
    const val HALO_EXTRA = 8f

    /** How much of the accent the static halo keeps. Static: the track breathes. */
    const val HALO_ALPHA = 0.18f

    /**
     * The bloom on the pulse pearl: a soft body wider than the pearl, so its
     * growth reads as light rather than as a growing dot.
     */
    const val BLOOM_SIZE_DP = 66f

    /** Dark gap between two sleep phases, in degrees (0.008 of the 260 span). */
    const val SEGMENT_GAP_DEGREES = 2.1f

    /** Radial glow behind the dial, inset 18dp from the rim. */
    const val GLOW_SIZE = 156f
    const val GLOW_OPACITY = 0.22f

    /**
     * The action pill of the render: 26dp tall, radius 13, a fixed 104dp wide and
     * centred, with a 0.75dp hairline. The drawer's two pills reuse the material
     * without the fixed width and without the face pin.
     */
    const val PILL_HEIGHT = 26f
    const val PILL_RADIUS = 13f
    const val PILL_WIDTH = 104f
    const val PILL_BORDER = 0.75f
    const val PILL_PADDING = 6f
    const val PILL_CENTER_Y = 63f

    /**
     * Vertical centres of the three-block stack, from the tile centre: the
     * rhythm of a face with no trend row.
     */
    const val LABEL_Y = -38f
    const val HERO_Y = 0f
    const val SUB_Y = 30f

    /**
     * Vertical centres of the five-block face of S14, from the tile centre:
     * label, hero, trend row, subtitle and the pill. The row pushes the label
     * and the hero up and the subtitle down, so the hero keeps the eye and the
     * trend reads as an aside under it.
     */
    const val TREND_LABEL_Y = -48f
    const val TREND_HERO_Y = -10f
    const val TREND_ROW_Y = 22f
    const val TREND_SUB_Y = 42f

    /** Seven days of trend: 7dp bars, 5dp apart, radius 3.5, 16dp at most. */
    const val SPARK_DAYS = 7
    const val SPARK_BAR_WIDTH = 7f
    const val SPARK_BAR_GAP = 5f
    const val SPARK_BAR_RADIUS = 3.5f
    const val SPARK_HEIGHT = 16f

    /** Sleep shows the four phases of the night, not a seven-day trend. */
    const val SPARK_PHASES = 4
    const val SPARK_PHASE_BAR_WIDTH = 16f
    const val SPARK_PHASE_BAR_GAP = 6f

    /** A bar never disappears when its value is zero: it keeps a sliver. */
    const val SPARK_MIN_HEIGHT = 2f

    /** How much of the accent a past day keeps; today keeps all of it. */
    const val SPARK_PAST_ALPHA = 0.45f

    /** Box size an arc of [radius] and [thickness] needs to land on that radius. */
    fun containerDp(radius: Float, thickness: Float): Float = 2f * (radius + thickness / 2f)

    /** Full chord of the face at [yFromCenterDp]. */
    fun chordWidthDp(yFromCenterDp: Float): Float {
        val y = abs(yFromCenterDp).coerceAtMost(RADIUS_DP - 1f)
        return 2f * sqrt(RADIUS_DP * RADIUS_DP - y * y)
    }

    /** Width a text line may occupy at [yFromCenterDp] without entering the rim. */
    fun safeWidthDp(yFromCenterDp: Float): Float =
        (chordWidthDp(yFromCenterDp) - BEZEL_INSET_DP).coerceAtLeast(0f)

    /** Degrees swept by [fraction] of the dial, with the design's visible floor. */
    fun sweepDegrees(fraction: Float, minSweepDegrees: Float = 3f): Float =
        (fraction.coerceIn(0f, 1f) * ARC_SPAN).coerceAtLeast(minSweepDegrees)

    /** Screen angle (0 at 3 o'clock, clockwise) of [fraction] along the dial. */
    fun angleOf(fraction: Float): Float = ARC_START + ARC_SPAN * fraction.coerceIn(0f, 1f)
}
