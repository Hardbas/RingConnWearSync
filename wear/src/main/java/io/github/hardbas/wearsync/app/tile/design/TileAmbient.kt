package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.DynamicBuilders

/**
 * The expressive backdrop: the layer that makes the design render feel alive.
 *
 * The design ships two motion modes. "Esencial" is a static face plus one
 * ambient breath, and "Expresivo" adds a moving layer behind the dial — a
 * cardiogram that scrolls, a scan ring, a breathing glow, a drifting halo. This
 * file is that second layer, expressed with the three mechanisms a tile renderer
 * is known to animate on the target watch: image translation, arc length and
 * opacity.
 *
 * Each tile adds exactly one of these, and it spends the tile's second animator:
 * the first one is the bezel breath in [TileDial]. A tile with no data, or one
 * reading stale data, adds none and renders a still frame.
 *
 * What cannot exist here, by construction of ProtoLayout: a conic gradient that
 * rotates, a radial gradient that scales, a blurred layer, a mask, and any text
 * that counts up. Those parts of the render are deliberately not attempted.
 */
object TileAmbient {

    /**
     * A wide strip that scrolls sideways for ever.
     *
     * The strip must repeat every `travelDp`, otherwise the restart shows a seam.
     * It is drawn wider than the face on purpose: the tile clips it, so what the
     * eye sees is a line travelling across the dial.
     */
    fun scrolling(
        resourceId: String,
        accent: Int,
        widthDp: Float,
        heightDp: Float,
        travelDp: Float,
        opacity: Float,
        durationMs: Long = TileMotion.SCROLL_MS
    ): LayoutElementBuilders.Image =
        image(resourceId, accent, widthDp, heightDp, opacity)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setOpacity(TypeBuilders.FloatProp.Builder(opacity).build())
                    .setTransformation(
                        ModifiersBuilders.Transformation.Builder()
                            .setTranslationX(
                                DimensionBuilders.DpProp.Builder(0f)
                                    .setDynamicValue(
                                        DynamicBuilders.DynamicFloat.animate(
                                            0f,
                                            -travelDp,
                                            TileMotion.scroll(durationMs)
                                        )
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

    /**
     * A soft radial body that drifts on one axis and comes back, so nothing snaps.
     * One property, therefore one animator.
     */
    fun drifting(
        resourceId: String,
        accent: Int,
        sizeDp: Float,
        opacity: Float,
        travelDp: Float,
        durationMs: Long = TileMotion.DRIFT_MS
    ): LayoutElementBuilders.Image =
        image(resourceId, accent, sizeDp, sizeDp, opacity)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setOpacity(TypeBuilders.FloatProp.Builder(opacity).build())
                    .setTransformation(
                        ModifiersBuilders.Transformation.Builder()
                            .setTranslationX(
                                DimensionBuilders.DpProp.Builder(0f)
                                    .setDynamicValue(
                                        DynamicBuilders.DynamicFloat.animate(
                                            0f,
                                            travelDp,
                                            TileMotion.drift(durationMs)
                                        )
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

    /**
     * The shooting star: a streak that crosses the face and starts over, the one
     * piece of the design's sleep backdrop that is expressible as a single
     * animator.
     */
    fun meteor(
        resourceId: String,
        accent: Int,
        travelDp: Float,
        opacity: Float,
        widthDp: Float = 64f,
        heightDp: Float = 40f,
        durationMs: Long = TileMotion.METEOR_MS
    ): LayoutElementBuilders.Image =
        image(resourceId, accent, widthDp, heightDp, opacity)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setOpacity(TypeBuilders.FloatProp.Builder(opacity).build())
                    .setTransformation(
                        ModifiersBuilders.Transformation.Builder()
                            .setTranslationX(
                                DimensionBuilders.DpProp.Builder(-travelDp / 2f)
                                    .setDynamicValue(
                                        DynamicBuilders.DynamicFloat.animate(
                                            -travelDp / 2f,
                                            travelDp / 2f,
                                            TileMotion.meteor(durationMs)
                                        )
                                    )
                                    .build()
                            )
                            .setTranslationY(dp(-34f))
                            .build()
                    )
                    .build()
            )
            .build()

    /**
     * A thin arc that scans the whole dial and starts over: the closest a tile can
     * get to the design's rotating conic gradient.
     */
    fun scanning(
        accent: Int,
        radiusDp: Float = TileGeometry.SCAN_RADIUS,
        thicknessDp: Float = TileGeometry.SCAN_THICKNESS,
        opacity: Float = 0.5f,
        durationMs: Long = TileMotion.SCAN_MS
    ): LayoutElementBuilders.Box {
        val line = LayoutElementBuilders.ArcLine.Builder()
            .setLength(
                DimensionBuilders.DegreesProp.Builder(TileGeometry.ARC_SPAN)
                    .setDynamicValue(
                        DynamicBuilders.DynamicFloat.animate(
                            0f,
                            TileGeometry.ARC_SPAN,
                            TileMotion.scan(durationMs)
                        )
                    )
                    .build()
            )
            .setLayoutConstraintsForDynamicLength(
                DimensionBuilders.AngularLayoutConstraint.Builder(TileGeometry.ARC_SPAN).build()
            )
            .setThickness(dp(thicknessDp))
            .setStrokeCap(LayoutElementBuilders.STROKE_CAP_ROUND)
            .setColor(argb(TileInk.withAlpha(accent, opacity)))
            .build()

        val arc = LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(DimensionBuilders.degrees(TileGeometry.ARC_START))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(line)
            .build()

        val size = dp(TileGeometry.containerDp(radiusDp, thicknessDp))
        return LayoutElementBuilders.Box.Builder()
            .setWidth(size)
            .setHeight(size)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(arc)
            .build()
    }

    /** A radial glow that breathes between two opacities: the design's `.bg-2`. */
    fun breathing(
        resourceId: String,
        accent: Int,
        sizeDp: Float,
        min: Float,
        max: Float,
        durationMs: Long = TileMotion.PULSE_MS
    ): LayoutElementBuilders.Image =
        image(resourceId, accent, sizeDp, sizeDp, max)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setOpacity(
                        TypeBuilders.FloatProp.Builder(max)
                            .setDynamicValue(
                                DynamicBuilders.DynamicFloat.animate(
                                    min,
                                    max,
                                    TileMotion.pulse(durationMs)
                                )
                            )
                            .build()
                    )
                    .build()
            )
            .build()

    private fun image(
        resourceId: String,
        accent: Int,
        widthDp: Float,
        heightDp: Float,
        opacity: Float
    ): LayoutElementBuilders.Image.Builder =
        LayoutElementBuilders.Image.Builder()
            .setResourceId(resourceId)
            .setWidth(dp(widthDp))
            .setHeight(dp(heightDp))
            .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FIT)
            .setColorFilter(
                LayoutElementBuilders.ColorFilter.Builder()
                    .setTint(argb(accent))
                    .build()
            )
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setOpacity(TypeBuilders.FloatProp.Builder(opacity).build())
                    .build()
            )
}
