package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.ColorBuilders.ColorProp
import androidx.wear.protolayout.ColorBuilders.ColorStop
import androidx.wear.protolayout.ColorBuilders.SweepGradient
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import androidx.wear.protolayout.expression.DynamicBuilders

/**
 * The One UI bezel gauge shared by every tile.
 *
 * Two pieces, two animators and no more:
 *  1. the track, which breathes slowly in opacity so the rim has life without moving;
 *  2. the value arc, which sweeps in from zero on entrance and carries a
 *     sweep-gradient plus a flat colour fallback.
 *
 * Round caps are part of the One UI read: a chopped arc end looks like a
 * progress bar, a round one looks like a dial.
 */
object TileGauge {

    /**
     * Bezel elements, in paint order: track first, value arc on top.
     *
     * @param accent flat colour for the value arc, used as-is if the renderer
     *   does not honour [SweepGradient] brushes.
     * @param targetSweep final sweep in degrees, already clamped by the caller.
     * @param breathing ambient opacity band on the track. Tiles that spend
     *   their second animator elsewhere pass false.
     */
    fun elements(
        accent: Int,
        targetSweep: Float,
        breathing: Boolean = true
    ): List<LayoutElementBuilders.LayoutElement> {
        val trackLine = LayoutElementBuilders.ArcLine.Builder()
            .setLength(DimensionBuilders.degrees(TileDesign.Chrome.ARC_SPAN))
            .setThickness(dp(TileDesign.Chrome.ARC_THICKNESS))
            .setStrokeCap(LayoutElementBuilders.STROKE_CAP_ROUND)
            .setColor(argb(TileDesign.Ink.TRACK))
            .apply {
                if (breathing) {
                    setModifiers(
                        ModifiersBuilders.ArcModifiers.Builder()
                            .setOpacity(
                                TypeBuilders.FloatProp.Builder(TileDesign.Motion.BREATHE_MIN)
                                    .setDynamicValue(
                                        DynamicBuilders.DynamicFloat.animate(
                                            TileDesign.Motion.BREATHE_MIN,
                                            TileDesign.Motion.BREATHE_MAX,
                                            TileDesign.Motion.breathe()
                                        )
                                    )
                                    .build()
                            )
                            .build()
                    )
                }
            }
            .build()

        val entrance = DynamicBuilders.DynamicFloat.animate(
            0f,
            targetSweep,
            TileDesign.Motion.entrance()
        )

        val valueSweep = DimensionBuilders.DegreesProp.Builder(targetSweep)
            .setDynamicValue(entrance)
            .build()

        val valueLine = LayoutElementBuilders.ArcLine.Builder()
            .setLength(valueSweep)
            .setLayoutConstraintsForDynamicLength(
                DimensionBuilders.AngularLayoutConstraint.Builder(TileDesign.Chrome.ARC_SPAN).build()
            )
            .setThickness(dp(TileDesign.Chrome.ARC_THICKNESS))
            .setStrokeCap(LayoutElementBuilders.STROKE_CAP_ROUND)
            .setColor(argb(accent))
            .setBrush(sweepBrush(accent))
            .build()

        return listOf(
            arc(trackLine),
            arc(valueLine)
        )
    }

    /**
     * Accent fades out over the last third of the sweep. `color` and `brush` are
     * separate fields in the protocol, so the flat colour above still stands in
     * if a renderer ignores the brush.
     */
    private fun sweepBrush(accent: Int): SweepGradient =
        SweepGradient.Builder(
            ColorStop.Builder(ColorProp.Builder(accent).build(), TypeBuilders.FloatProp.Builder(0.0f).build()).build(),
            ColorStop.Builder(ColorProp.Builder(accent).build(), TypeBuilders.FloatProp.Builder(0.62f).build()).build(),
            ColorStop.Builder(ColorProp.Builder(transparent(accent)).build(), TypeBuilders.FloatProp.Builder(1.0f).build()).build()
        )
            .setStartAngle(DimensionBuilders.degrees(TileDesign.Chrome.ARC_START))
            .setEndAngle(DimensionBuilders.degrees(TileDesign.Chrome.ARC_START + TileDesign.Chrome.ARC_SPAN))
            .build()

    private fun transparent(color: Int): Int = color and 0x00FFFFFF

    private fun arc(line: LayoutElementBuilders.ArcLine): LayoutElementBuilders.Arc =
        LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(DimensionBuilders.degrees(TileDesign.Chrome.ARC_START))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(line)
            .build()

    /**
     * Maps a raw reading onto the bezel. Callers pass their own domain; this only
     * guarantees the swept fraction and the visible floor agree everywhere.
     */
    fun sweepFor(fraction: Float): Float =
        (fraction.coerceIn(0.0f, 1.0f) * TileDesign.Chrome.ARC_SPAN).coerceAtLeast(MIN_SWEEP)

    /** A gauge that reads "no data" still shows a hint of rim. */
    const val MIN_SWEEP = 14f

    /** Unused animation parameter import guard: keeps the spec type visible to callers. */
    internal fun entranceSpec(): AnimationParameterBuilders.AnimationSpec = TileDesign.Motion.entrance()
}
