package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Which tiles have their in-tile drawer open.
 *
 * The platform carries no state between two loads of a tile, so the state lives
 * here, in the process, keyed by tile. A tap on a [ModifiersBuilders.Clickable]
 * that fires an [ActionBuilders.LoadAction] comes back to
 * `TileService.onTileRequest` with its id in
 * `TileRequest.getCurrentState().getLastClickableId()`; [apply] turns that id
 * into the next state. Two ids are meaningful, any other id (and `null`, which
 * is a plain refresh) leaves the tile as it was.
 *
 * The set is concurrent because a tile request can arrive while another one is
 * being answered; the state is intentionally not persisted.
 */
object TileDrawerState {

    /** Opens the drawer. Carried by the face's action pill. */
    const val ID_OPEN = "pill"

    /** Closes the drawer. Carried by the drawer's close pill. */
    const val ID_CLOSE = "close"

    private val open: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun isOpen(tile: String): Boolean = open.contains(tile)

    /** Applies one click, or leaves the tile untouched for any other id. */
    fun apply(tile: String, lastClickableId: String?) {
        when (lastClickableId) {
            ID_OPEN -> open.add(tile)
            ID_CLOSE -> open.remove(tile)
            else -> Unit
        }
    }
}

/**
 * The in-tile drawer of S12: the "micro-drawer" of the design render, built with
 * ProtoLayout primitives.
 *
 * The drawer replaces the whole face: a near-black panel holds a kicker in the
 * metric accent, one title line, an optional row of real 7-day bars, a meta line
 * and the two actions of the panel (close, and open the detail activity so the
 * access the pill had is not lost).
 *
 * Bars are the tile's own series: each one is `value / max(series.max, 1)` of a
 * 34dp track, in the accent at 45 %, with the last bar (today) at full strength.
 * An empty series renders no bar row at all; a value is never invented.
 */
object TileDrawer {

    /** The panel of the design render: `#0E0E0D` at 97 %. */
    const val SURFACE = 0xFF0E0E0D.toInt()
    const val SURFACE_ALPHA = 0.97f

    /** How much of the accent a past day keeps; today keeps all of it. */
    const val BAR_ALPHA = 0.45f

    /** The face's action pill: it no longer launches the app, it opens the drawer. */
    fun tapToOpen(): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId(TileDrawerState.ID_OPEN)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

    /** The drawer's close pill: it asks the service for the face again. */
    fun tapToClose(): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId(TileDrawerState.ID_CLOSE)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

    /**
     * The full-face overlay.
     *
     * @param accent the kicker colour of the metric.
     * @param kicker small uppercase line, already uppercase at the call site.
     * @param title one line of data; ellipsized by the platform when too long.
     * @param meta quiet one-liner under the bars.
     * @param bars the tile's own series, oldest to newest; empty means no bars.
     * @param barColor the metric accent the bars are painted with.
     * @param closeLabel copy of the close pill.
     * @param openLabel copy of the pill that opens the detail activity.
     * @param packageName package of the detail activity the open pill launches.
     * @param detailActivity class of that activity.
     */
    fun drawer(
        accent: Int,
        kicker: String,
        title: String,
        meta: String,
        bars: List<Float>,
        barColor: Int,
        closeLabel: String,
        openLabel: String,
        packageName: String,
        detailActivity: String
    ): LayoutElementBuilders.Box {
        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(TileShell.text(kicker, TileType.label(accent)))
            .addContent(TileShell.spacer(4f))
            .addContent(TileShell.text(title, TileType.sub(TileInk.PRIMARY)))

        if (bars.isNotEmpty()) {
            column.addContent(TileShell.spacer(8f))
            column.addContent(barRow(bars, barColor))
        }

        column.addContent(TileShell.spacer(8f))
        column.addContent(TileShell.text(meta, TileType.pill(TileInk.SECONDARY)))
        column.addContent(TileShell.spacer(10f))
        column.addContent(
            TileShell.pillRow(
                listOf(
                    TileShell.pill(
                        TileShell.pillText(closeLabel, TileInk.PRIMARY),
                        tapToClose(),
                        centerY = null
                    ),
                    TileShell.hSpacer(8f),
                    TileShell.pill(
                        TileShell.pillText(openLabel, TileInk.PRIMARY),
                        TileShell.launchDetail(packageName, detailActivity),
                        centerY = null
                    )
                )
            )
        )

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(TileInk.withAlpha(SURFACE, SURFACE_ALPHA)))
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setAll(dp(TileGeometry.DRAWER_PADDING))
                            .build()
                    )
                    .build()
            )
            .addContent(column.build())
            .build()
    }

    /** Seven bars of real data, oldest first, last one at full strength. */
    private fun barRow(bars: List<Float>, barColor: Int): LayoutElementBuilders.Row {
        // The series' own maximum, floored at 1 so a flat-zero series cannot
        // divide by zero: heights become a proportion of this series, never of
        // an assumed scale.
        val max = bars.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val row = LayoutElementBuilders.Row.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)

        bars.forEachIndexed { index, value ->
            if (index > 0) row.addContent(TileShell.hSpacer(TileGeometry.DRAWER_BAR_GAP))
            val isToday = index == bars.lastIndex
            val height = (TileGeometry.DRAWER_BAR_TRACK * (value / max))
                .coerceAtLeast(TileGeometry.DRAWER_BAR_MIN_HEIGHT)
            row.addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(dp(TileGeometry.DRAWER_BAR_WIDTH))
                    .setHeight(dp(height))
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setBackground(
                                ModifiersBuilders.Background.Builder()
                                    .setColor(
                                        argb(
                                            if (isToday) barColor
                                            else TileInk.withAlpha(barColor, BAR_ALPHA)
                                        )
                                    )
                                    .setCorner(
                                        ModifiersBuilders.Corner.Builder()
                                            .setRadius(dp(TileGeometry.DRAWER_BAR_RADIUS))
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
}
