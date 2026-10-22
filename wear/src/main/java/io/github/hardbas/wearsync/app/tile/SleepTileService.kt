package io.github.hardbas.wearsync.app.tile

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Sleep tile: the quality word behind a crescent, the score, and the night split
 * into four proportional phases around the dial.
 *
 * The file only supplies meaning. Every colour, size, radius and animation comes
 * from the warm editorial design system, so the carousel reads as one watch face
 * family instead of five hand-painted gauges.
 *
 * Motion budget: the entrance of the phase ring plus the breathing radial glow,
 * which is why the phase halo is static — exactly the two animators the design
 * allows, and the glow is the infinite one.
 */
class SleepTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return Futures.immediateFuture(buildTile(this, requestParams.deviceConfiguration))
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(buildResources())
    }

    override fun onTileEnterEvent(tileEnterEvent: EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(tileEnterEvent)
        val vitals = RingConnComplicationProvider.getLatestVitals(this)
        if (vitals.sleepScore == 0) {
            // No sleep data after midnight rollover — request phone to wake RingConn and sync
            requestPhoneSync()
        }
        TileService.getUpdater(this).requestUpdate(this.javaClass)
    }

    private fun requestPhoneSync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = com.google.android.gms.wearable.Wearable.getNodeClient(this@SleepTileService)
                    .connectedNodes
                    .await()
                for (node in nodes) {
                    com.google.android.gms.wearable.Wearable.getMessageClient(this@SleepTileService)
                        .sendMessage(node.id, "/action/sync_ringconn", byteArrayOf())
                        .await()
                    Log.d("SleepTile", "Requested phone sync from node ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.w("SleepTile", "Failed to request phone sync", e)
            }
        }
    }

    companion object {
        const val RESOURCES_VERSION = "warm-editorial-sleep-v1"

        /** Face refresh period. */
        private const val DEFAULT_FRESHNESS_MS = 60_000L

        private const val DETAIL_ACTIVITY = "io.github.hardbas.wearsync.app.ui.SleepDetailActivity"

        /** The four phases, in the order the design paints them. */
        private const val PHASES = TileGeometry.SPARK_PHASES

        fun buildResources(): ResourceBuilders.Resources =
            TileResources.addTo(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)).build()

        fun buildTile(
            context: Context,
            deviceParams: DeviceParameters,
            vitals: RingConnVitals = RingConnComplicationProvider.getLatestVitals(context)
        ): TileBuilders.Tile = buildTile(
            AndroidTileStrings(context),
            context.packageName,
            deviceParams,
            vitals
        )

        fun buildTile(
            strings: TileStrings,
            packageName: String,
            deviceParams: DeviceParameters,
            vitals: RingConnVitals
        ): TileBuilders.Tile {
            val score = vitals.sleepScore
            val stale = vitals.isStale

            // Awake counts towards the night: the four segments have to describe the
            // whole time in bed, so their fractions sum to one.
            val totalMins = vitals.sleepDeepMins + vitals.sleepRemMins +
                vitals.sleepLightMins + vitals.sleepAwakeMins
            val hasData = score > 0 && totalMins > 0

            val ramp = if (stale) TileAccents.dim(TileAccents.sleep) else TileAccents.sleep
            val labelColor = if (hasData && !stale) TileAccents.sleepLabel else TileInk.DIM

            val quality = when {
                !hasData -> strings.text(R.string.no_data)
                score >= 85 -> strings.text(R.string.excellent)
                score >= 70 -> strings.text(R.string.good)
                else -> strings.text(R.string.fair)
            }

            // Asleep time excludes the awake minutes; the ring includes them.
            val durationText = durationText(vitals)

            // Nocturnal skin temperature, as an offset from the wearer's baseline.
            val tempText = tempText(vitals)

            val phaseFractions = if (hasData) phaseFractions(vitals) else FloatArray(0)

            val clickable = TileShell.launchDetail(packageName, DETAIL_ACTIVITY)

            val layers = buildList {
                add(TileShell.glow(TileResources.GLOW, ramp.first()))
                add(TileShell.overlay(TileResources.STARS))
                add(TileShell.overlay(TileResources.TICKS_R74))
                if (hasData) {
                    // The measured expressive layer: a slow light streak sweeping
                    // the rim, drawn behind the phase ring. Rotation only, pivot on
                    // the tile centre.
                    if (!stale) {
                        add(
                            TileAmbient.sweep(
                                resourceId = TileResources.SWEEP,
                                accent = TileAccents.sleepLabel,
                                durationMs = TileMotion.SWEEP_MS_SLEEP,
                                opacity = 0.30f
                            )
                        )
                    }
                    add(TileDial.track(ramp.first(), breathing = !stale))
                    add(TileDial.phases(colors = ramp, fractions = phaseFractions))
                }
            }

            val label = TileShell.withMoon(
                TileResources.MOON,
                TileShell.text(quality, TileType.sleepLabel(labelColor))
            )

            val sub = TileShell.pillRow(
                listOf(
                    TileShell.text(durationText, TileType.sub()),
                    TileShell.text(" · ", TileType.sub()),
                    TileShell.text(tempText, TileType.sub(TileAccents.teal))
                )
            )

            // Sleep has no seven-day series: the trend row is the night itself —
            // one bar per phase, in its own colour, each a share of the whole.
            val trend = phaseFractions.toList()
            val sparkline = if (trend.isEmpty()) {
                null
            } else {
                TileSparkline.row(
                    values = trend,
                    accent = TileAccents.sleepLabel,
                    heightDp = TileGeometry.SPARK_HEIGHT,
                    barWidthDp = TileGeometry.SPARK_PHASE_BAR_WIDTH,
                    gapDp = TileGeometry.SPARK_PHASE_BAR_GAP,
                    // A stale face desaturates every accent it owns (S7).
                    colors = ramp.toList(),
                    maximum = trend.maxOrNull() ?: 0f
                )
            }

            val stack = TileShell.stack(
                label = label,
                hero = TileShell.text(
                    value = if (hasData) "$score" else "--",
                    style = TileType.hero(),
                    opacity = if (stale) TileInk.STALE_HERO_ALPHA else null
                ),
                sub = sub,
                trend = sparkline
            )

            val pill = TileShell.pill(
                content = TileShell.pillText(strings.text(R.string.details), TileInk.PRIMARY),
                clickable = clickable
            )

            val entry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(TileShell.root(clickable, layers, stack, pill)).build())
                .build()

            return tile(entry)
        }

        private fun tile(
            entry: TimelineBuilders.TimelineEntry,
            freshnessMs: Long = DEFAULT_FRESHNESS_MS
        ): TileBuilders.Tile =
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(entry).build())
                .setFreshnessIntervalMillis(freshnessMs)
                .build()

        /** Asleep time, without the awake minutes, in the design's h/m wording. */
        private fun durationText(vitals: RingConnVitals): String {
            val asleepMins = vitals.sleepDeepMins + vitals.sleepRemMins + vitals.sleepLightMins
            return when {
                asleepMins <= 0 -> NO_VALUE
                else -> {
                    val h = asleepMins / 60
                    val m = asleepMins % 60
                    if (h > 0 && m > 0) "${h}h ${m}m" else if (h > 0) "${h}h" else "${m}m"
                }
            }
        }

        /** Nocturnal skin temperature, as an offset from the wearer's baseline. */
        private fun tempText(vitals: RingConnVitals): String {
            val offset = vitals.skinTempOffset
            val hasTempData = vitals.skinTempBaseline > 0f || offset != 0f
            return when {
                !hasTempData -> NO_VALUE
                offset in -0.05f..0.05f -> "±0.0°"
                else -> String.format(Locale.US, "%+.1f", offset) + "°"
            }
        }

        /** Deep, REM, light and awake, as shares of the whole time in bed. */
        private fun phaseFractions(vitals: RingConnVitals): FloatArray {
            val totalMins = vitals.sleepDeepMins + vitals.sleepRemMins +
                vitals.sleepLightMins + vitals.sleepAwakeMins
            return FloatArray(PHASES) { index ->
                val minutes = when (index) {
                    0 -> vitals.sleepDeepMins
                    1 -> vitals.sleepRemMins
                    2 -> vitals.sleepLightMins
                    else -> vitals.sleepAwakeMins
                }
                minutes.toFloat() / totalMins
            }
        }

        private const val NO_VALUE = "--"
    }
}
