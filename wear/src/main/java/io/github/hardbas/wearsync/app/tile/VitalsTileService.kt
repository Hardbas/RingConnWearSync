package io.github.hardbas.wearsync.app.tile

import android.content.Context
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider
import io.github.hardbas.wearsync.app.data.DailyHistoryEntry
import io.github.hardbas.wearsync.app.data.RingConnHistoryStore
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.tile.design.AndroidTileStrings
import io.github.hardbas.wearsync.app.tile.design.Ring
import io.github.hardbas.wearsync.app.tile.design.TileAccents
import io.github.hardbas.wearsync.app.tile.design.TileAmbient
import io.github.hardbas.wearsync.app.tile.design.TileDial
import io.github.hardbas.wearsync.app.tile.design.TileDrawer
import io.github.hardbas.wearsync.app.tile.design.TileDrawerState
import io.github.hardbas.wearsync.app.tile.design.TileGeometry
import io.github.hardbas.wearsync.app.tile.design.TileInk
import io.github.hardbas.wearsync.app.tile.design.TileMotion
import io.github.hardbas.wearsync.app.tile.design.TileResources
import io.github.hardbas.wearsync.app.tile.design.TileShell
import io.github.hardbas.wearsync.app.tile.design.TileStrings
import io.github.hardbas.wearsync.app.tile.design.TileType
import java.util.Locale

/**
 * Vitals tile: two concentric rings, the pulse in the middle, the ring state in
 * the pill.
 *
 * The file only supplies meaning. Every colour, size, radius and animation comes
 * from the warm editorial design system, so the carousel reads as one watch face
 * family instead of five hand-painted gauges.
 */
class VitalsTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        // A LoadAction tap returns here with its clickable id; the builder turns
        // that id into open/closed before it renders either the face or the drawer.
        return Futures.immediateFuture(
            buildTile(
                this,
                requestParams.deviceConfiguration,
                lastClickableId = requestParams.currentState.lastClickableId
            )
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(buildResources())
    }

    override fun onTileEnterEvent(tileEnterEvent: EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(tileEnterEvent)
    }

    companion object {
        const val RESOURCES_VERSION = "warm-editorial-vitals-v1"

        /** Key of this tile in [TileDrawerState]. */
        const val TILE_ID = "vitals"

        private const val DETAIL_ACTIVITY = "io.github.hardbas.wearsync.app.ui.VitalsDetailActivity"

        /** The drawer's bar series is the last seven days, never more. */
        private const val DRAWER_DAYS = 7

        /** The design estimates autonomy as one day per sixteen percent of charge. */
        private const val AUTONOMY_PERCENT_PER_DAY = 16

        fun buildResources(): ResourceBuilders.Resources =
            TileResources.addTo(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)).build()

        fun buildTile(
            context: Context,
            deviceParams: DeviceParameters,
            vitals: RingConnVitals = RingConnComplicationProvider.getLatestVitals(context),
            lastClickableId: String? = null,
            history: List<DailyHistoryEntry> = RingConnHistoryStore.getHistory(context)
        ): TileBuilders.Tile = buildTile(
            AndroidTileStrings(context),
            context.packageName,
            deviceParams,
            vitals,
            lastClickableId,
            history
        )

        /**
         * @param lastClickableId the id the system carried back from a
         *   `LoadAction` tap, or `null` on a plain refresh. Anything but the two
         *   drawer ids leaves the tile in the state it is already in.
         * @param history the seven-day cache the drawer bars come from. Passed in
         *   so the contract tests can build entries directly; production reads it
         *   from [RingConnHistoryStore].
         */
        fun buildTile(
            strings: TileStrings,
            packageName: String,
            deviceParams: DeviceParameters,
            vitals: RingConnVitals,
            lastClickableId: String? = null,
            history: List<DailyHistoryEntry> = emptyList()
        ): TileBuilders.Tile {
            TileDrawerState.apply(TILE_ID, lastClickableId)
            if (TileDrawerState.isOpen(TILE_ID)) {
                return drawerTile(strings, packageName, vitals, history)
            }

            val hasHr = vitals.lastHeartRate > 0
            val hasSpo2 = vitals.lastSpo2 > 0
            val hasBattery = vitals.ringBattery > 0
            // The rings are a second graphic, not a reading of their own: they only
            // appear once there is something to ring.
            val hasData = hasHr || hasSpo2 || hasBattery
            val stale = vitals.isStale

            // Two ramps, one per ring. Warm when the reading is live, the palette's
            // grey when it is not: the design's "Viejo" state desaturates the face.
            val spo2Ramp = if (stale) TileAccents.dim(TileAccents.spo2) else TileAccents.spo2
            val batteryRamp = if (stale) TileAccents.dim(TileAccents.battery) else TileAccents.battery
            val labelColor = if (hasData && !stale) TileAccents.spo2Label else TileInk.DIM

            // Saturation and charge share one scale: percent of full, floored at
            // 6 % so a low reading still shows a ring instead of a dot.
            val spo2Fraction = (vitals.lastSpo2 / 100f).coerceIn(0.06f, 1f)
            val batteryFraction = (vitals.ringBattery / 100f).coerceIn(0.06f, 1f)

            // The pill asks for a reload and comes back with id `pill`, which
            // opens the drawer; the face behind it keeps launching the detail
            // screen, exactly like the pulse tile.
            val clickable = TileShell.launchDetail(packageName, DETAIL_ACTIVITY)

            val layers = buildList {
                // The measured expressive layer: a light streak sweeping the rim,
                // drawn first so it sits behind both rings. Rotation only, pivot on
                // the tile centre; the track keeps its own breath.
                if (hasData && !stale) {
                    add(
                        TileAmbient.sweep(
                            resourceId = TileResources.SWEEP,
                            accent = spo2Ramp.last(),
                            durationMs = TileMotion.SWEEP_MS_RINGS,
                            opacity = 0.40f
                        )
                    )
                }
                add(TileShell.glow(TileResources.GLOW, spo2Ramp.last()))
                add(
                    TileDial.track(
                        spo2Ramp.last(),
                        TileGeometry.RING_RADIUS,
                        TileGeometry.RING_THICKNESS,
                        breathing = hasData && !stale
                    )
                )
                add(
                    TileDial.track(
                        batteryRamp.last(),
                        TileGeometry.RING_INNER_RADIUS,
                        TileGeometry.RING_THICKNESS,
                        breathing = false
                    )
                )
                add(TileShell.overlay(TileResources.TICKS_R78))
                if (hasData) {
                    // One call for both rings: two `value()` calls would cost four
                    // animators, and the budget is per tile, not per ring.
                    add(
                        TileDial.rings(
                            outer = Ring(spo2Ramp, spo2Fraction, TileGeometry.RING_RADIUS, TileGeometry.RING_THICKNESS),
                            inner = Ring(batteryRamp, batteryFraction, TileGeometry.RING_INNER_RADIUS, TileGeometry.RING_THICKNESS)
                        )
                    )
                }
            }

            val stack = TileShell.stack(
                label = TileShell.text(
                    value = strings.text(R.string.vitals_short).uppercase(Locale.getDefault()),
                    style = TileType.label(labelColor)
                ),
                hero = TileShell.text(
                    value = if (hasHr) "${vitals.lastHeartRate}" else "--",
                    style = TileType.hero(TileType.HERO_RING),
                    opacity = if (stale) TileInk.STALE_HERO_ALPHA else null
                ),
                sub = TileShell.text(
                    value = strings.text(R.string.bpm_unit).lowercase(Locale.getDefault()),
                    style = TileType.sub(TileAccents.heartRateLabel)
                )
            )

            // The pill carries the two readings the rings stand for, each tied to
            // its ring by a tone dot: oxygen first, then ring autonomy.
            val pillContent = TileShell.pillRow(
                listOf(
                    TileShell.pillDot(TileAccents.spo2Label),
                    TileShell.hSpacer(4f),
                    TileShell.pillText(if (hasSpo2) "${vitals.lastSpo2}%" else "--", TileInk.PRIMARY),
                    TileShell.pillSeparator(),
                    TileShell.pillDot(TileAccents.batteryLabel),
                    TileShell.hSpacer(4f),
                    TileShell.pillText(if (hasBattery) "${vitals.ringBattery}%" else "--", TileInk.PRIMARY)
                )
            )

            val pill = TileShell.pill(content = pillContent, clickable = TileDrawer.tapToOpen())

            val entry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(
                    LayoutElementBuilders.Layout.Builder()
                        .setRoot(TileShell.root(clickable, layers, stack, pill))
                        .build()
                )
                .build()

            return tile(entry)
        }

        /**
         * The open drawer: the whole face becomes the S12 panel, built from the
         * tile's own readings and the real seven-day SpO2 history.
         */
        private fun drawerTile(
            strings: TileStrings,
            packageName: String,
            vitals: RingConnVitals,
            history: List<DailyHistoryEntry>
        ): TileBuilders.Tile {
            val days = history.sortedBy { it.epochDay }.takeLast(DRAWER_DAYS)
            val spo2 = if (vitals.lastSpo2 > 0) "${vitals.lastSpo2}%" else NO_VALUE
            val battery = if (vitals.ringBattery > 0) "${vitals.ringBattery}%" else NO_VALUE
            // Autonomy is the design's estimate from today's charge, not a stored
            // number: a missing charge stays a dash.
            val autonomy = if (vitals.ringBattery > 0) {
                "${vitals.ringBattery / AUTONOMY_PERCENT_PER_DAY}"
            } else {
                NO_VALUE
            }

            val root = TileDrawer.drawer(
                accent = TileAccents.spo2Label,
                kicker = strings.text(R.string.tile_drawer_vitals_kicker).uppercase(Locale.getDefault()),
                title = String.format(
                    Locale.getDefault(),
                    strings.text(R.string.tile_drawer_vitals_title),
                    spo2,
                    battery
                ),
                meta = String.format(
                    Locale.getDefault(),
                    strings.text(R.string.tile_drawer_vitals_meta),
                    autonomy
                ),
                bars = days.map { it.spo2.toFloat() },
                barColor = TileAccents.spo2.last(),
                closeLabel = strings.text(R.string.tile_drawer_close),
                openLabel = strings.text(R.string.tile_drawer_open),
                packageName = packageName,
                detailActivity = DETAIL_ACTIVITY
            )

            return tile(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
                    .build()
            )
        }

        private fun tile(entry: TimelineBuilders.TimelineEntry): TileBuilders.Tile =
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(entry).build())
                .setFreshnessIntervalMillis(30_000L)
                .build()

        private const val NO_VALUE = "--"
    }
}
