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
 * Pulse tile: terracotta dial, pearl marker, zone and variability.
 *
 * The file only supplies meaning. Every colour, size, radius and animation comes
 * from the warm editorial design system, so the carousel reads as one watch face
 * family instead of five hand-painted gauges.
 */
class HeartRateTileService : TileService() {

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
        const val RESOURCES_VERSION = "warm-editorial-hr-v1"

        /** Key of this tile in [TileDrawerState]. */
        const val TILE_ID = "pulse"

        private const val DETAIL_ACTIVITY = "io.github.hardbas.wearsync.app.ui.HeartRateDetailActivity"

        /** The drawer's bar series is the last seven days, never more. */
        private const val DRAWER_DAYS = 7

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
            val stale = vitals.isStale
            val bpm = vitals.lastHeartRate
            val zone = zoneOf(bpm)

            // Warm ramps when the reading is live, the palette's grey when it is
            // not: the design's "Viejo" state desaturates the whole face.
            val ramp = if (stale) TileAccents.dim(TileAccents.heartRate) else TileAccents.heartRate
            val accent = ramp.last()
            val labelColor = if (hasHr && !stale) TileAccents.heartRateLabel else TileInk.DIM

            val title = strings.text(R.string.pulse_short).uppercase(Locale.getDefault())
            val label = if (hasHr) "$title · $zone" else title

            // 42..187 bpm across the dial, floored so a resting reading still
            // shows an arc instead of a dot.
            val fraction = if (hasHr) ((bpm - 42f) / 145f).coerceIn(0.05f, 0.96f) else 0f

            val hrv = if (vitals.lastHrv > 0) {
                "${strings.text(R.string.hrv_short)} ${vitals.lastHrv} ms"
            } else {
                strings.text(R.string.no_data)
            }

            val clickable = TileShell.launchDetail(packageName, DETAIL_ACTIVITY)

            val layers = buildList {
                // The measured expressive layer, drawn first so both pieces sit
                // behind the dial: a light streak spinning the rim (rotation only,
                // pivot on the tile centre) and a bloom on the pearl that beats at
                // the reading's own rate. Nothing travels across the face.
                if (hasHr && !stale) {
                    add(
                        TileAmbient.sweep(
                            resourceId = TileResources.SWEEP,
                            accent = accent,
                            durationMs = TileMotion.SWEEP_MS_PULSE,
                            opacity = 0.50f
                        )
                    )
                }
                add(TileShell.glow(TileResources.GLOW, accent))
                // The bloom is this tile's pulse, so the track stays still: four
                // animated nodes (streak rotation, bloom opacity, bloom scale X
                // and Y) instead of five.
                add(TileDial.track(accent, breathing = false))
                add(TileShell.overlay(TileResources.TICKS_R84))
                if (hasHr) {
                    // One round trip of the bloom is one heartbeat, half a beat per
                    // leg, so the pearl's light follows the reading instead of a
                    // fixed loop.
                    if (!stale) {
                        add(
                            TileAmbient.bloomAt(
                                resourceId = TileResources.GLOW,
                                angleDeg = TileGeometry.angleOf(fraction),
                                radiusDp = TileGeometry.ARC_RADIUS,
                                sizeDp = TileGeometry.BLOOM_SIZE_DP,
                                accent = TileAccents.heartRateLabel,
                                minOpacity = 0.05f,
                                maxOpacity = 0.72f,
                                minScale = 0.55f,
                                maxScale = 1.15f,
                                spec = TileMotion.beat(bpm)
                            )
                        )
                    }
                    // A live dial settles in and breathes; a stale one is a still
                    // frame, exactly like the design's `animation: none`.
                    add(
                        TileDial.value(
                            accent = ramp,
                            fraction = fraction,
                            pearlColor = TileAccents.heartRateLabel
                        )
                    )
                }
            }

            val stack = TileShell.stack(
                label = TileShell.text(label, TileType.label(labelColor)),
                hero = TileShell.text(
                    value = if (hasHr) "$bpm" else "--",
                    style = TileType.hero(),
                    opacity = if (stale) TileInk.STALE_HERO_ALPHA else null
                ),
                sub = TileShell.text(hrv, TileType.sub())
            )

            val pill = TileShell.pill(
                content = TileShell.pillText(strings.text(R.string.details), TileInk.PRIMARY),
                clickable = TileDrawer.tapToOpen()
            )

            return tile(TileShell.root(clickable, layers, stack, pill))
        }

        /**
         * The open drawer: the whole face becomes the S12 panel, built from the
         * tile's own reading and the real seven-day history.
         *
         * The title uses the newest [DailyHistoryEntry.rhr] (there is no resting
         * rate on [RingConnVitals]) and [RingConnVitals.maxHeartRate]; a missing
         * value renders as `--` instead of an invented zero.
         */
        private fun drawerTile(
            strings: TileStrings,
            packageName: String,
            vitals: RingConnVitals,
            history: List<DailyHistoryEntry>
        ): TileBuilders.Tile {
            val days = history.sortedBy { it.epochDay }.takeLast(DRAWER_DAYS)
            val resting = days.lastOrNull()?.rhr ?: 0
            val peak = vitals.maxHeartRate
            val avgHrv = if (vitals.avgHrv > 0) vitals.avgHrv else vitals.lastHrv
            val zone = if (vitals.lastHeartRate > 0) zoneOf(vitals.lastHeartRate) else NO_VALUE

            val root = TileDrawer.drawer(
                accent = TileAccents.heartRateLabel,
                kicker = strings.text(R.string.tile_drawer_trend_7d).uppercase(Locale.getDefault()),
                title = format(
                    strings,
                    R.string.tile_drawer_pulse_title,
                    numberOrDash(resting),
                    numberOrDash(peak)
                ),
                meta = format(strings, R.string.tile_drawer_pulse_meta, numberOrDash(avgHrv), zone),
                bars = days.map { it.rhr.toFloat() },
                barColor = TileAccents.heartRate.last(),
                closeLabel = strings.text(R.string.tile_drawer_close),
                openLabel = strings.text(R.string.tile_drawer_open),
                packageName = packageName,
                detailActivity = DETAIL_ACTIVITY
            )

            return tile(root)
        }

        private fun tile(root: LayoutElementBuilders.LayoutElement): TileBuilders.Tile {
            val entry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
                .build()

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(entry).build())
                .setFreshnessIntervalMillis(30_000L)
                .build()
        }

        /** Drawer copy carries format arguments, so the template is filled here. */
        private fun format(strings: TileStrings, resId: Int, vararg args: String): String =
            String.format(Locale.getDefault(), strings.text(resId), *args)

        private fun numberOrDash(value: Int): String = if (value > 0) "$value" else NO_VALUE

        private const val NO_VALUE = "--"

        /** Z1 rest -> Z5 peak, on the design's bpm cuts. */
        fun zoneOf(bpm: Int): String = when {
            bpm < 62 -> "Z1"
            bpm <= 96 -> "Z2"
            bpm <= 128 -> "Z3"
            bpm <= 154 -> "Z4"
            else -> "Z5"
        }

        /** Kept public for the contract test: the dial never exceeds the face. */
        internal fun dialBudget(): Float = TileGeometry.safeWidthDp(TileGeometry.SUB_Y)
    }
}
