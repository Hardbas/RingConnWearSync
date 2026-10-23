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
import io.github.hardbas.wearsync.app.data.RingConnHistoryStore
import io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider
import io.github.hardbas.wearsync.app.data.DailyHistoryEntry
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.tile.design.AndroidTileStrings
import io.github.hardbas.wearsync.app.tile.design.Ring
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
import kotlin.math.roundToInt

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
        return Futures.immediateFuture(buildTile(this, requestParams.deviceConfiguration))
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(buildResources())
    }

    override fun onTileEnterEvent(tileEnterEvent: EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(tileEnterEvent)
    }

    companion object {
        const val RESOURCES_VERSION = "warm-editorial-vitals-v1"

        /** Face refresh period. */
        private const val DEFAULT_FRESHNESS_MS = 900_000L

        private const val DETAIL_ACTIVITY = "io.github.hardbas.wearsync.app.ui.VitalsDetailActivity"

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
            val hasSpo2 = vitals.lastSpo2 > 0
            // The ring's own charge lives in the complications: this face uses the
            // inner ring for the recovery score, which is the reading a glance
            // cannot get anywhere else.
            val readiness = RingConnHistoryStore.computeReadinessScore(vitals)
            val hasReadiness = readiness > 0
            // The rings are a second graphic, not a reading of their own: they only
            // appear once there is something to ring.
            val hasData = hasHr || hasSpo2 || hasReadiness
            val stale = vitals.isStale

            // Two ramps, one per ring. Warm when the reading is live, the palette's
            // grey when it is not: the design's "Viejo" state desaturates the face.
            val spo2Ramp = if (stale) TileAccents.dim(TileAccents.spo2) else TileAccents.spo2
            val readinessRamp = if (stale) TileAccents.dim(TileAccents.battery) else TileAccents.battery
            val labelColor = if (hasData && !stale) TileAccents.spo2Label else TileInk.DIM

            // Both readings are percent of full, floored at 6 % so a low value
            // still shows a ring instead of a dot.
            val spo2Fraction = (vitals.lastSpo2 / 100f).coerceIn(0.06f, 1f)
            val readinessFraction = (readiness / 100f).coerceIn(0.06f, 1f)

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
                        readinessRamp.last(),
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
                            inner = Ring(readinessRamp, readinessFraction, TileGeometry.RING_INNER_RADIUS, TileGeometry.RING_THICKNESS)
                        )
                    )
                }
            }

            val spo2Series = spo2Series(history)
            val hasTrend = spo2Series.isNotEmpty()

            // With a trend row the sub carries the average saturation of the week
            // and the ring charge, and the pill goes back to being the one action
            // of the face. Without a row the face keeps the subtitle it had.
            val sub = if (hasTrend) {
                // Two tone dots instead of two words: at the sub line's height
                // the rings only leave about 110dp, and "SpO2 ..% · Battery ..%"
                // ran into both of them. The dot is the same key the rings use.
                // One reading, one number: the dot pair read as "which is which".
                val average = spo2Series.average().roundToInt()
                TileShell.text(
                    value = String.format(Locale.getDefault(), strings.text(R.string.tile_spo2_only), "$average%"),
                    style = TileType.sub()
                )
            } else {
                TileShell.text(
                    value = strings.text(R.string.bpm_unit).lowercase(Locale.getDefault()),
                    style = TileType.sub(TileAccents.heartRateLabel)
                )
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
                sub = sub,
                trend = if (hasTrend) {
                    TileSparkline.row(
                        values = spo2Series,
                        // A stale face desaturates every accent it owns (S7).
                        accent = spo2Ramp.last(),
                        heightDp = TileGeometry.SPARK_HEIGHT,
                        barWidthDp = TileGeometry.SPARK_BAR_WIDTH,
                        gapDp = TileGeometry.SPARK_BAR_GAP
                    )
                } else {
                    null
                }
            )

            // The pill carries the two readings the rings stand for, each tied to
            // its ring by a tone dot: oxygen first, then ring autonomy. With a
            // trend row they have moved to the subtitle, so the pill is the action.
            // The pill carries the second reading instead of the word "Detail":
            // it is the only slot left on the face, it is still the tap target,
            // and "Energy 78" says which number is which without a legend.
            val pill = TileShell.pill(
                content = TileShell.pillRow(
                    listOf(
                        TileShell.pillDot(if (hasReadiness && !stale) TileAccents.batteryLabel else TileInk.DIM),
                        TileShell.hSpacer(4f),
                        TileShell.pillText(
                            String.format(
                                Locale.getDefault(),
                                strings.text(R.string.tile_vitals_energy),
                                if (hasReadiness) "$readiness" else NO_VALUE
                            ),
                            TileInk.PRIMARY
                        )
                    )
                ),
                clickable = clickable
            )

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
         * Arterial oxygen of the last seven days, oldest first. A day with no
         * reading is left out instead of drawn as a dip.
         */
        private fun spo2Series(history: List<DailyHistoryEntry>): List<Float> =
            history.sortedBy { it.epochDay }
                .takeLast(TileGeometry.SPARK_DAYS)
                .map { it.spo2.toFloat() }
                .filter { it > 0f }

        private fun tile(
            entry: TimelineBuilders.TimelineEntry,
            freshnessMs: Long = DEFAULT_FRESHNESS_MS
        ): TileBuilders.Tile =
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(entry).build())
                .setFreshnessIntervalMillis(freshnessMs)
                .build()

        private const val NO_VALUE = "--"
    }
}
