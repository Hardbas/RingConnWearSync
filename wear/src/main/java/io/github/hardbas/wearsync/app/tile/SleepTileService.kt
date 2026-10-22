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
import io.github.hardbas.wearsync.app.tile.design.TileDial
import io.github.hardbas.wearsync.app.tile.design.TileInk
import io.github.hardbas.wearsync.app.tile.design.TileResources
import io.github.hardbas.wearsync.app.tile.design.TileShell
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
        return Futures.immediateFuture(
            buildTile(this, requestParams.deviceConfiguration)
        )
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
        private const val DETAIL_ACTIVITY = "io.github.hardbas.wearsync.app.ui.SleepDetailActivity"

        /** The four phases, in the order the design paints them. */
        private const val PHASES = 4

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
            val asleepMins = vitals.sleepDeepMins + vitals.sleepRemMins + vitals.sleepLightMins
            val durationText = when {
                asleepMins <= 0 -> "--"
                else -> {
                    val h = asleepMins / 60
                    val m = asleepMins % 60
                    if (h > 0 && m > 0) "${h}h ${m}m" else if (h > 0) "${h}h" else "${m}m"
                }
            }

            // Nocturnal skin temperature, as an offset from the wearer's baseline.
            val offset = vitals.skinTempOffset
            val hasTempData = vitals.skinTempBaseline > 0f || offset != 0f
            val tempText = when {
                !hasTempData -> "--"
                offset in -0.05f..0.05f -> "±0.0°"
                else -> String.format(Locale.US, "%+.1f", offset) + "°"
            }

            val phaseFractions = if (hasData) {
                FloatArray(PHASES) { index ->
                    val minutes = when (index) {
                        0 -> vitals.sleepDeepMins
                        1 -> vitals.sleepRemMins
                        2 -> vitals.sleepLightMins
                        else -> vitals.sleepAwakeMins
                    }
                    minutes.toFloat() / totalMins
                }
            } else {
                FloatArray(0)
            }

            val clickable = TileShell.launchDetail(packageName, DETAIL_ACTIVITY)

            val layers = buildList {
                add(TileShell.glow(TileResources.GLOW, ramp.first()))
                add(TileShell.overlay(TileResources.STARS))
                add(TileShell.overlay(TileResources.TICKS_R74))
                if (hasData) {
                    // The four segments are solid colours, so the ring cannot share one
                    // length animation. Its life comes from a single band behind them:
                    // one line, one animator, the night breathing under the phases.
                    if (!stale) {
                        add(TileDial.halo(ramp.first()))
                    }
                    add(TileDial.phases(colors = ramp, fractions = phaseFractions, halo = false))
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

            val stack = TileShell.stack(
                label = label,
                hero = TileShell.text(
                    value = if (hasData) "$score" else "--",
                    style = TileType.hero(),
                    opacity = if (stale) TileInk.STALE_HERO_ALPHA else null
                ),
                sub = sub
            )

            val pill = TileShell.pill(
                content = TileShell.pillText(strings.text(R.string.details), TileInk.PRIMARY),
                clickable = clickable
            )

            val entry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(TileShell.root(clickable, layers, stack, pill)).build())
                .build()

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(entry).build())
                .setFreshnessIntervalMillis(60_000L)
                .build()
        }
    }
}
