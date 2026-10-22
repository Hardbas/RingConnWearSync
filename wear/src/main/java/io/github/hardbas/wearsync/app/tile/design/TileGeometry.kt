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

    /** One UI dial silhouette: 260 degrees starting at 140. */
    const val ARC_SPAN = 260f
    const val ARC_START = 140f

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

    /** The action pill, pinned 63dp below the tile centre by the design. */
    const val PILL_HEIGHT = 28f
    const val PILL_RADIUS = 14f
    const val PILL_PADDING = 12f
    const val PILL_CENTER_Y = 63f

    /** Nominal vertical centres of the centred metric stack, from the tile centre. */
    const val LABEL_Y = -38f
    const val HERO_Y = 0f
    const val SUB_Y = 30f

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
