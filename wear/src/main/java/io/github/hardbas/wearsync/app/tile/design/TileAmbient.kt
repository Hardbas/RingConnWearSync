package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import androidx.wear.protolayout.expression.DynamicBuilders
import kotlin.math.cos
import kotlin.math.sin

/**
 * The two expressive layers the measured motion engine is allowed to build.
 *
 * A probe tile on the target watch showed which properties actually animate:
 * `Transformation` (rotation, scale, translation), `Modifiers.opacity`,
 * `ArcLine` length/opacity and `Arc.anchorAngle`. It also showed which do not: a
 * rotation on a container and anything dynamic inside a `Brush`. The previous
 * engine ignored that and produced shapes sliding across the face, which read as
 * a 2D animation pasted onto a watch face.
 *
 * So there are exactly two builders here:
 *
 *  - [sweep], a light streak baked into a 192dp drawable (the arc and its fade
 *    live in the vector, [TileResources.SWEEP]) that rotates a full turn around
 *    the tile centre. Nothing crosses the face: the pivot is the centre, so the
 *    light travels the rim.
 *  - [bloomAt], a soft radial body pinned on the dial by translation that
 *    brightens and grows. The caller passes the animation spec, so the pulse
 *    tile owns the bpm maths and this builder stays free of it.
 *
 * [breathing] stays because the stress tile's halo is its own gesture, measured
 * in production. All of them are still images with one or two animated
 * properties, which is what the renderer honours.
 */
object TileAmbient {

    /**
     * The light streak: the baked arc spun a full turn around the tile centre.
     *
     * Linear and restarting, so the seam of the loop is invisible. The static
     * rotation is 360 degrees — the same visual state as 0 — so a renderer that
     * ignores the animation shows the streak at the top of the rim instead of
     * somewhere random.
     */
    fun sweep(
        resourceId: String,
        accent: Int,
        durationMs: Long,
        opacity: Float
    ): LayoutElementBuilders.Image =
        image(resourceId, accent, TileGeometry.TILE_DP, TileGeometry.TILE_DP)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setOpacity(TypeBuilders.FloatProp.Builder(opacity).build())
                    .setTransformation(
                        ModifiersBuilders.Transformation.Builder()
                            .setRotation(
                                DimensionBuilders.DegreesProp.Builder(360f)
                                    .setDynamicValue(
                                        DynamicBuilders.DynamicFloat.animate(
                                            0f,
                                            360f,
                                            TileMotion.sweep(durationMs)
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
     * A bloom pinned on the dial: it breathes between [minOpacity] and
     * [maxOpacity] while it grows from [minScale] to [maxScale].
     *
     * Positioned by translation from the tile centre, computed with [offsetAt]
     * from [angleDeg] on [radiusDp] — the same centre-angle convention as
     * [TileGeometry.angleOf], 0 at 3 o'clock and clockwise. The spec decides the
     * rhythm; nothing here knows what a heartbeat is.
     *
     * @param spec animation of both properties, so one heartbeat moves the bloom
     *   as one gesture instead of two that drift apart.
     */
    fun bloomAt(
        resourceId: String,
        angleDeg: Float,
        radiusDp: Float,
        sizeDp: Float,
        accent: Int,
        minOpacity: Float,
        maxOpacity: Float,
        minScale: Float,
        maxScale: Float,
        spec: AnimationParameterBuilders.AnimationSpec
    ): LayoutElementBuilders.Image {
        val (x, y) = offsetAt(angleDeg, radiusDp)
        return image(resourceId, accent, sizeDp, sizeDp)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setOpacity(
                        TypeBuilders.FloatProp.Builder(maxOpacity)
                            .setDynamicValue(
                                DynamicBuilders.DynamicFloat.animate(minOpacity, maxOpacity, spec)
                            )
                            .build()
                    )
                    .setTransformation(
                        ModifiersBuilders.Transformation.Builder()
                            .setTranslationX(dp(x))
                            .setTranslationY(dp(y))
                            .setScaleX(animatedScale(maxScale, minScale, maxScale, spec))
                            .setScaleY(animatedScale(maxScale, minScale, maxScale, spec))
                            .build()
                    )
                    .build()
            )
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
        image(resourceId, accent, sizeDp, sizeDp)
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

    /**
     * Where [angleDeg] on [radiusDp] lands, as the translation that moves an
     * image from the tile centre to that point: the dial position measured from
     * the tile's top-left corner minus half the tile.
     */
    private fun offsetAt(angleDeg: Float, radiusDp: Float): Pair<Float, Float> {
        val radians = Math.toRadians(angleDeg.toDouble())
        val x = TileGeometry.RADIUS_DP + radiusDp * cos(radians).toFloat()
        val y = TileGeometry.RADIUS_DP + radiusDp * sin(radians).toFloat()
        return (x - TileGeometry.TILE_DP / 2f) to (y - TileGeometry.TILE_DP / 2f)
    }

    /** A scale prop whose static value is [resting] and whose animation starts at [from]. */
    private fun animatedScale(
        resting: Float,
        from: Float,
        to: Float,
        spec: AnimationParameterBuilders.AnimationSpec
    ): TypeBuilders.FloatProp =
        TypeBuilders.FloatProp.Builder(resting)
            .setDynamicValue(DynamicBuilders.DynamicFloat.animate(from, to, spec))
            .build()

    private fun image(
        resourceId: String,
        accent: Int,
        widthDp: Float,
        heightDp: Float
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
}
