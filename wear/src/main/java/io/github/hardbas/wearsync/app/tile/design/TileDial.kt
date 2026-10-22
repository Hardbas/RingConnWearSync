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
 * Motion uses only the two mechanisms measured on the target watch
 * (SM-L500): an arc-length sweep and an `ArcLine` opacity loop. Both are
 * one-shot-or-loop animations on the arc itself, so a tile never depends on a
 * container transformation the renderer may not animate.
 */
object TileDial {

    /**
     * The quiet rim every tile keeps, even with no data: the metric accent at
     * 16 % over black, full 260 degrees, round caps. Static by design.
     */
    fun track(
        accent: Int,
        radius: Float = TileGeometry.ARC_RADIUS,
        thickness: Float = TileGeometry.ARC_THICKNESS
    ): LayoutElementBuilders.Box = container(
        radius = radius,
        thickness = thickness,
        arcs = listOf(
            arc(
                anchorDegrees = TileGeometry.ARC_START,
                line(
                    lengthDegrees = TileGeometry.ARC_SPAN,
                    thicknessDp = thickness,
                    color = TileInk.track(accent)
                )
            )
        )
    )

    /**
     * The value arc: gradient across the swept range, breathing halo behind it,
     * optional pearl at its end.
     *
     * @param accent ramp, calmest stop first. The last stop is the fallback colour
     *   for renderers that ignore the sweep-gradient brush.
     * @param sweepIn grows the arc from zero on tile entry. Pass false for the
     *   still frame a stale reading deserves.
     */
    fun value(
        accent: IntArray,
        fraction: Float,
        radius: Float = TileGeometry.ARC_RADIUS,
        thickness: Float = TileGeometry.ARC_THICKNESS,
        pearlColor: Int? = null,
        halo: Boolean = true,
        sweepIn: Boolean = true
    ): LayoutElementBuilders.Box {
        val sweep = TileGeometry.sweepDegrees(fraction)
        val arcs = buildList {
            if (halo) {
                add(arc(TileGeometry.ARC_START, haloLine(sweep, thickness, accent.last())))
            }
            add(
                arc(
                    anchorDegrees = TileGeometry.ARC_START,
                    sweepLine(
                        lengthDegrees = sweep,
                        thicknessDp = thickness,
                        color = accent.last(),
                        brush = gradient(accent, sweep / TileGeometry.ARC_SPAN),
                        sweepIn = sweepIn
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
     * Two rings on two radii need two containers, but they still share ONE halo:
     * the budget is per tile, not per ring. Only the outer ring sweeps in — a
     * second length animation would be a third animator, and the inner ring is
     * hidden behind the outer stroke for most of the sweep anyway.
     */
    fun rings(
        outer: Ring,
        inner: Ring,
        halo: Boolean = true,
        sweepOuter: Boolean = true
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
                    sweepLine(
                        lengthDegrees = TileGeometry.sweepDegrees(outer.fraction),
                        thicknessDp = outer.thickness,
                        color = outer.accent.last(),
                        brush = gradient(outer.accent, outer.fraction),
                        sweepIn = sweepOuter
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
        halo: Boolean = true,
        sweepIn: Boolean = true
    ): LayoutElementBuilders.Box {
        val arcs = buildList {
            if (halo) {
                add(arc(TileGeometry.ARC_START, haloLine(TileGeometry.ARC_SPAN, thickness, stops.last())))
            }
            add(
                arc(
                    anchorDegrees = TileGeometry.ARC_START,
                    sweepLine(
                        lengthDegrees = TileGeometry.ARC_SPAN,
                        thicknessDp = thickness,
                        color = stops.first(),
                        brush = gradient(stops, 1f),
                        sweepIn = sweepIn
                    )
                )
            )
            pearlColor?.let { add(pearl(pearlAt, it)) }
        }
        return container(radius, thickness, arcs)
    }

    /**
     * Sleep's ring: four segments, proportional to the phases, separated by a
     * dark gap. Each segment is solid, so the whole ring cannot share one length
     * animation; its ambient loop comes from [halo] instead.
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
                            line(length * TileGeometry.ARC_SPAN, thickness + TileGeometry.HALO_EXTRA, color)
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

    /**
     * One full-span band behind the dial that breathes for ever. This is the
     * sleep tile's ambient loop: its four phase segments cannot share a length
     * animation, so the life has to live in a single line.
     */
    fun halo(
        accent: Int,
        radius: Float = TileGeometry.ARC_RADIUS,
        thickness: Float = TileGeometry.ARC_THICKNESS
    ): LayoutElementBuilders.Box = container(
        radius = radius,
        thickness = thickness,
        arcs = listOf(arc(TileGeometry.ARC_START, haloLine(TileGeometry.ARC_SPAN, thickness, accent)))
    )

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
     * The fill line. With [sweepIn] its length is a dynamic value growing from
     * zero, which is the one entrance this renderer is known to animate on the
     * target watch; the angular constraint is required or the tile crashes.
     */
    private fun sweepLine(
        lengthDegrees: Float,
        thicknessDp: Float,
        color: Int,
        brush: Brush? = null,
        sweepIn: Boolean
    ): LayoutElementBuilders.ArcLine {
        val builder = LayoutElementBuilders.ArcLine.Builder()
            .setThickness(dp(thicknessDp))
            .setStrokeCap(LayoutElementBuilders.STROKE_CAP_ROUND)
            .setColor(argb(color))
        if (sweepIn) {
            builder
                .setLength(
                    DimensionBuilders.DegreesProp.Builder(lengthDegrees)
                        .setDynamicValue(
                            DynamicBuilders.DynamicFloat.animate(
                                0f,
                                lengthDegrees,
                                TileMotion.entrance()
                            )
                        )
                        .build()
                )
                .setLayoutConstraintsForDynamicLength(
                    DimensionBuilders.AngularLayoutConstraint.Builder(TileGeometry.ARC_SPAN).build()
                )
        } else {
            builder.setLength(DimensionBuilders.degrees(lengthDegrees))
        }
        if (brush != null) builder.setBrush(brush)
        return builder.build()
    }

    /** Same arc, 4dp wider on each side: the soft band the design calls resplandor. */
    private fun haloLine(
        lengthDegrees: Float,
        thicknessDp: Float,
        color: Int
    ): LayoutElementBuilders.ArcLine =
        line(
            lengthDegrees = lengthDegrees,
            thicknessDp = thicknessDp + TileGeometry.HALO_EXTRA,
            color = color,
            opacity = breathingHalo()
        )

    /**
     * The ambient loop. The band is deliberately wider than the design's
     * 0.10-0.32: measured on the target watch, a narrow band on a thin line moves
     * less than one luminance step and reads as a dead tile.
     */
    private fun breathingHalo(): TypeBuilders.FloatProp =
        TypeBuilders.FloatProp.Builder(TileMotion.HALO_MAX)
            .setDynamicValue(
                DynamicBuilders.DynamicFloat.animate(
                    TileMotion.HALO_MIN,
                    TileMotion.HALO_MAX,
                    TileMotion.halo()
                )
            )
            .build()

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
