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
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.tile.design.AndroidTileStrings
import io.github.hardbas.wearsync.app.tile.design.Ring
import io.github.hardbas.wearsync.app.tile.design.TileAccents
import io.github.hardbas.wearsync.app.tile.design.TileAmbient
import io.github.hardbas.wearsync.app.tile.design.TileDial
import io.github.hardbas.wearsync.app.tile.design.TileGeometry
import io.github.hardbas.wearsync.app.tile.design.TileInk
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
        return Futures.immediateFuture(
            buildTile(this, requestParams.deviceConfiguration)
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
        private const val DETAIL_ACTIVITY = "io.github.hardbas.wearsync.app.ui.VitalsDetailActivity"

        fun buildResources(): ResourceBuilders.Resources =
            TileResources.addTo(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)).build()

        fun buildTile(
            context: Context,
            deviceParams: DeviceParameters,
            vitals: RingConnVitals = RingConnComplicationProvider.getLatestVitals(context)
        ): TileBuilders.Tile = buildTile(AndroidTileStrings(context), context.packageName, deviceParams, vitals)

        fun buildTile(
            strings: TileStrings,
            packageName: String,
            deviceParams: DeviceParameters,
            vitals: RingConnVitals
        ): TileBuilders.Tile {
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

            val clickable = TileShell.launchDetail(packageName, DETAIL_ACTIVITY)

            val layers = buildList {
                // "Expresivo" layer: a scan ring travelling the inner face, the
                // tile's second animator. The first is the outer bezel breath.
                if (hasData && !stale) {
                    add(TileAmbient.scanning(TileAccents.spo2Label, opacity = 0.45f))
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

            val pill = TileShell.pill(content = pillContent, clickable = clickable)

            val entry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(
                    LayoutElementBuilders.Layout.Builder()
                        .setRoot(TileShell.root(clickable, layers, stack, pill))
                        .build()
                )
                .build()

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(entry).build())
                .setFreshnessIntervalMillis(30_000L)
                .build()
        }
    }
}
