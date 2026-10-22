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
import io.github.hardbas.wearsync.app.tile.design.TileGeometry
import io.github.hardbas.wearsync.app.tile.design.TileInk
import io.github.hardbas.wearsync.app.tile.design.TileMotion
import io.github.hardbas.wearsync.app.tile.design.TileResources
import io.github.hardbas.wearsync.app.tile.design.TileShell
import io.github.hardbas.wearsync.app.tile.design.TileSparkline
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
        return Futures.immediateFuture(buildTile(this, requestParams.deviceConfiguration))
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(buildResources())
    }

    override fun onTileEnterEvent(tileEnterEvent: EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(tileEnterEvent)
    }

    companion object {
        const val RESOURCES_VERSION = "warm-editorial-hr-v1"

        /** Face refresh period. */
        private const val DEFAULT_FRESHNESS_MS = 30_000L

        private const val DETAIL_ACTIVITY = "io.github.hardbas.wearsync.app.ui.HeartRateDetailActivity"

        fun buildResources(): ResourceBuilders.Resources =
            TileResources.addTo(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)).build()

        fun buildTile(
            context: Context,
            deviceParams: DeviceParameters,
            vitals: RingConnVitals = RingConnComplicationProvider.getLatestVitals(context),
            history: List<DailyHistoryEntry> = RingConnHistoryStore.getHistory(context)
        ): TileBuilders.Tile = buildTile(
            AndroidTileStrings(context),
            context.packageName,
            deviceParams,
            vitals,
            history
        )

        /**
         * @param history the seven-day cache the trend row comes from. Passed in
         *   so the contract tests can build entries directly; production reads it
         *   from [RingConnHistoryStore].
         */
        fun buildTile(
            strings: TileStrings,
            packageName: String,
            deviceParams: DeviceParameters,
            vitals: RingConnVitals,
            history: List<DailyHistoryEntry> = emptyList()
        ): TileBuilders.Tile {
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

            // The sub carries the average variability of the reading, not one
            // beat's: the trend row underneath already shows the shape of the
            // week, so the line under it is the reference level.
            val hrvValue = if (vitals.avgHrv > 0) vitals.avgHrv else vitals.lastHrv
            val hrv = if (hrvValue > 0) {
                "${strings.text(R.string.hrv_short)} $hrvValue ms"
            } else {
                strings.text(R.string.no_data)
            }

            // The trend row: resting rate, oldest first, last seven days. No
            // history is no row — the face then keeps the shape it had.
            val trend = restingSeries(history)
            val sparkline = if (trend.isEmpty()) {
                null
            } else {
                TileSparkline.row(
                    values = trend,
                    // A stale face desaturates every accent it owns (S7).
                    accent = accent,
                    heightDp = TileGeometry.SPARK_HEIGHT,
                    barWidthDp = TileGeometry.SPARK_BAR_WIDTH,
                    gapDp = TileGeometry.SPARK_BAR_GAP
                )
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
                sub = TileShell.text(hrv, TileType.sub()),
                trend = sparkline
            )

            val pill = TileShell.pill(
                content = TileShell.pillText(strings.text(R.string.details), TileInk.PRIMARY),
                clickable = clickable
            )

            return tile(TileShell.root(clickable, layers, stack, pill))
        }

        /**
         * The resting rate of the last seven days, oldest first. A day with no
         * reading is not a zero: it is left out instead of drawn as a dip.
         */
        private fun restingSeries(history: List<DailyHistoryEntry>): List<Float> =
            history.sortedBy { it.epochDay }
                .takeLast(TileGeometry.SPARK_DAYS)
                .map { it.rhr.toFloat() }
                .filter { it > 0f }

        private fun tile(
            root: LayoutElementBuilders.LayoutElement,
            freshnessMs: Long = DEFAULT_FRESHNESS_MS
        ): TileBuilders.Tile {
            val entry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
                .build()

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(entry).build())
                .setFreshnessIntervalMillis(freshnessMs)
                .build()
        }

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
