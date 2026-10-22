package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.ColorBuilders.Brush
import androidx.wear.protolayout.ColorBuilders.ColorStop
import androidx.wear.protolayout.ColorBuilders.SweepGradient
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.DynamicBuilders

/**
 * The bezel dial, built from the only three primitives ProtoLayout gives us:
 * `Arc`, `ArcLine` and `Box`.
 *
 * Every function returns a centred box of exactly the size the stroke needs, so
 * different radii can coexist in one 192dp face. Paint order inside a dial box
 * is: halo, fill, pearl — the same order the design render uses.
 *
 * The only motion this file owns is the bezel breath on the track: `ArcLine`
 * opacity, 0.45..0.95 over 3400 ms, the exact band and period measured animating
 * on the target watch. Everything else here is static; the expressive backdrop
 * of the design's "Expresivo" mode lives in [TileBackdrop].
 */
object TileDial {

    /**
     * The quiet rim every tile keeps, even with no data: the metric accent at
     * 16 % over black, full 260 degrees, round caps.
     *
     * @param breathing drives the tile's ambient animator. A tile with no data,
     *   or one reading stale data, passes false and renders a still frame.
     */
    fun track(
        accent: Int,
        radius: Float = TileGeometry.ARC_RADIUS,
        thickness: Float = TileGeometry.ARC_THICKNESS,
        breathing: Boolean = true
    ): LayoutElementBuilders.Box = container(
        radius = radius,
        thickness = thickness,
        arcs = listOf(
            arc(
                anchorDegrees = TileGeometry.ARC_START,
                line(
                    lengthDegrees = TileGeometry.ARC_SPAN,
                    thicknessDp = thickness,
                    color = TileInk.track(accent),
                    opacity = if (breathing) breathingTrack() else null
                )
            )
        )
    )

    /**
     * The value arc: gradient across the swept range, soft static halo behind it,
     * optional pearl at its end.
     *
     * @param accent ramp, calmest stop first. The last stop is the fallback colour
     *   for renderers that ignore the sweep-gradient brush.
     */
    fun value(
        accent: IntArray,
        fraction: Float,
        radius: Float = TileGeometry.ARC_RADIUS,
        thickness: Float = TileGeometry.ARC_THICKNESS,
        pearlColor: Int? = null,
        halo: Boolean = true
    ): LayoutElementBuilders.Box {
        val sweep = TileGeometry.sweepDegrees(fraction)
        val arcs = buildList {
            if (halo) {
                add(arc(TileGeometry.ARC_START, haloLine(sweep, thickness, accent.last())))
            }
            add(
                arc(
                    anchorDegrees = TileGeometry.ARC_START,
                    line(
                        lengthDegrees = sweep,
                        thicknessDp = thickness,
                        color = accent.last(),
                        brush = gradient(accent, sweep / TileGeometry.ARC_SPAN)
                    )
                )
            )
            pearlColor?.let { add(pearl(fraction, it)) }
        }
        return container(radius, thickness, arcs)
    }

    /**
     * The two concentric rings of the vitals and activity tiles.
     *
     * Two rings on two radii need two containers, and they share one static halo:
     * the budget is per tile, not per ring.
     */
    fun rings(
        outer: Ring,
        inner: Ring,
        halo: Boolean = true
    ): LayoutElementBuilders.Box {
        val outerArcs = buildList {
            if (halo) {
                add(
                    arc(
                        anchorDegrees = TileGeometry.ARC_START,
                        haloLine(
                            lengthDegrees = TileGeometry.sweepDegrees(outer.fraction),
                            thicknessDp = outer.thickness,
                            color = outer.accent.last()
                        )
                    )
                )
            }
            add(
                arc(
                    anchorDegrees = TileGeometry.ARC_START,
                    line(
                        lengthDegrees = TileGeometry.sweepDegrees(outer.fraction),
                        thicknessDp = outer.thickness,
                        color = outer.accent.last(),
                        brush = gradient(outer.accent, outer.fraction)
                    )
                )
            )
        }
        val innerArcs = listOf(
            arc(
                anchorDegrees = TileGeometry.ARC_START,
                line(
                    lengthDegrees = TileGeometry.sweepDegrees(inner.fraction),
                    thicknessDp = inner.thickness,
                    color = inner.accent.last(),
                    brush = gradient(inner.accent, inner.fraction)
                )
            )
        )

        val outerBox = container(outer.radius, outer.thickness, outerArcs)
        val innerBox = container(inner.radius, inner.thickness, innerArcs)

        val size = dp(TileGeometry.containerDp(outer.radius, outer.thickness))
        return LayoutElementBuilders.Box.Builder()
            .setWidth(size)
            .setHeight(size)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(outerBox)
            .addContent(innerBox)
            .build()
    }

    /**
     * The stress dial: the whole 260 degrees *is* the five-stop spectrum, and the
     * reading is a pearl riding on it. The spectrum is therefore also the empty
     * state's rim — call it with [pearlColor] null and [halo] false for no data.
     */
    fun spectrum(
        stops: IntArray,
        radius: Float = TileGeometry.ARC_RADIUS,
        thickness: Float = TileGeometry.ARC_THICKNESS,
        pearlColor: Int? = null,
        pearlAt: Float = 0f,
        halo: Boolean = true
    ): LayoutElementBuilders.Box {
        val arcs = buildList {
            if (halo) {
                add(arc(TileGeometry.ARC_START, haloLine(TileGeometry.ARC_SPAN, thickness, stops.last())))
            }
            add(
                arc(
                    anchorDegrees = TileGeometry.ARC_START,
                    line(
                        lengthDegrees = TileGeometry.ARC_SPAN,
                        thicknessDp = thickness,
                        color = stops.first(),
                        brush = gradient(stops, 1f)
                    )
                )
            )
            pearlColor?.let { add(pearl(pearlAt, it)) }
        }
        return container(radius, thickness, arcs)
    }

    /**
     * Sleep's ring: four segments, proportional to the phases, separated by a
     * dark gap. Each segment is a solid colour, so the ring cannot share one
     * gradient; its life comes from the track behind it.
     */
    fun phases(
        colors: IntArray,
        fractions: FloatArray,
        radius: Float = TileGeometry.ARC_RADIUS,
        thickness: Float = TileGeometry.ARC_THICKNESS,
        halo: Boolean = true
    ): LayoutElementBuilders.Box {
        require(colors.size == fractions.size) { "one colour per phase" }
        val gap = TileGeometry.SEGMENT_GAP_DEGREES / TileGeometry.ARC_SPAN
        val segments = mutableListOf<Triple<Float, Float, Int>>()
        var cursor = 0f
        fractions.forEachIndexed { index, fraction ->
            val start = cursor + gap / 2f
            val length = (fraction - gap).coerceAtLeast(0.002f)
            segments += Triple(start, length, colors[index])
            cursor += fraction
        }

        val arcs = buildList {
            if (halo) {
                segments.forEach { (start, length, color) ->
                    add(
                        arc(
                            anchorDegrees = TileGeometry.angleOf(start),
                            line(
                                lengthDegrees = length * TileGeometry.ARC_SPAN,
                                thicknessDp = thickness + TileGeometry.HALO_EXTRA,
                                color = TileInk.withAlpha(color, TileGeometry.HALO_ALPHA)
                            )
                        )
                    )
                }
            }
            segments.forEach { (start, length, color) ->
                add(
                    arc(
                        anchorDegrees = TileGeometry.angleOf(start),
                        line(length * TileGeometry.ARC_SPAN, thickness, color)
                    )
                )
            }
        }
        return container(radius, thickness, arcs)
    }

    // ---- primitives ------------------------------------------------------

    /**
     * A fixed-size, centred box that makes its arc land on [radius]: the renderer
     * draws an arc along the box edge with the stroke inside, so the box has to
     * be `2 * (radius + thickness / 2)` wide.
     */
    private fun container(
        radius: Float,
        thickness: Float,
        arcs: List<LayoutElementBuilders.Arc>
    ): LayoutElementBuilders.Box {
        val size = dp(TileGeometry.containerDp(radius, thickness))
        val builder = LayoutElementBuilders.Box.Builder()
            .setWidth(size)
            .setHeight(size)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        arcs.forEach(builder::addContent)
        return builder.build()
    }

    private fun arc(
        anchorDegrees: Float,
        vararg lines: LayoutElementBuilders.ArcLine
    ): LayoutElementBuilders.Arc {
        val builder = LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(DimensionBuilders.degrees(anchorDegrees))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
        lines.forEach(builder::addContent)
        return builder.build()
    }

    private fun line(
        lengthDegrees: Float,
        thicknessDp: Float,
        color: Int,
        brush: Brush? = null,
        opacity: TypeBuilders.FloatProp? = null
    ): LayoutElementBuilders.ArcLine {
        val builder = LayoutElementBuilders.ArcLine.Builder()
            .setLength(DimensionBuilders.degrees(lengthDegrees))
            .setThickness(dp(thicknessDp))
            .setStrokeCap(LayoutElementBuilders.STROKE_CAP_ROUND)
            .setColor(argb(color))
        if (brush != null) builder.setBrush(brush)
        if (opacity != null) {
            builder.setModifiers(
                ModifiersBuilders.ArcModifiers.Builder().setOpacity(opacity).build()
            )
        }
        return builder.build()
    }

    /**
     * The bezel breath: the one opacity loop measured working on this watch. The
     * band is wide on purpose — a 0.16-0.34 band on this same line moved 0.6 of a
     * luminance step and the tile read as frozen.
     */
    private fun breathingTrack(): TypeBuilders.FloatProp =
        TypeBuilders.FloatProp.Builder(TileMotion.AMBIENT_MAX)
            .setDynamicValue(
                DynamicBuilders.DynamicFloat.animate(
                    TileMotion.AMBIENT_MIN,
                    TileMotion.AMBIENT_MAX,
                    TileMotion.ambient()
                )
            )
            .build()

    /** Same arc, 4dp wider on each side: the soft band the design calls resplandor. */
    private fun haloLine(
        lengthDegrees: Float,
        thicknessDp: Float,
        color: Int
    ): LayoutElementBuilders.ArcLine =
        line(
            lengthDegrees = lengthDegrees,
            thicknessDp = thicknessDp + TileGeometry.HALO_EXTRA,
            color = TileInk.withAlpha(color, TileGeometry.HALO_ALPHA)
        )

    /** Ivory pearl with the metric colour in its middle, riding the reading's angle. */
    private fun pearl(
        at: Float,
        color: Int
    ): LayoutElementBuilders.Arc {
        val anchor = TileGeometry.angleOf(at) - TileGeometry.PEARL_DEGREES / 2f
        return arc(
            anchorDegrees = anchor,
            line(TileGeometry.PEARL_DEGREES, TileGeometry.PEARL_OUTER, TileInk.PEARL),
            line(TileGeometry.PEARL_DEGREES, TileGeometry.PEARL_INNER, color)
        )
    }

    /**
     * Sweep gradient across the drawn part of the dial. Both `color` and `brush`
     * are set on the line: a renderer that ignores the brush degrades to a flat
     * accent instead of losing the arc.
     */
    private fun gradient(stops: IntArray, endFraction: Float): SweepGradient {
        require(stops.size >= 2) { "a sweep gradient needs at least two stops" }
        val colorStops = Array(stops.size) { index ->
            ColorStop.Builder(
                argb(stops[index]),
                TypeBuilders.FloatProp.Builder(index.toFloat() / (stops.size - 1)).build()
            ).build()
        }
        val end = TileGeometry.ARC_START + TileGeometry.ARC_SPAN * endFraction.coerceIn(0.01f, 1f)
        return SweepGradient.Builder(*colorStops)
            .setStartAngle(DimensionBuilders.degrees(TileGeometry.ARC_START))
            .setEndAngle(DimensionBuilders.degrees(end))
            .build()
    }
}

/** One of the two concentric rings: its ramp, its reading, its radius and stroke. */
class Ring(
    val accent: IntArray,
    val fraction: Float,
    val radius: Float,
    val thickness: Float
)
