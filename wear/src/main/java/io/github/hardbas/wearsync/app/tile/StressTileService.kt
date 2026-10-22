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
import io.github.hardbas.wearsync.app.tile.design.TileAccents
import io.github.hardbas.wearsync.app.tile.design.TileDial
import io.github.hardbas.wearsync.app.tile.design.TileInk
import io.github.hardbas.wearsync.app.tile.design.TileResources
import io.github.hardbas.wearsync.app.tile.design.TileShell
import io.github.hardbas.wearsync.app.tile.design.TileStrings
import io.github.hardbas.wearsync.app.tile.design.TileType
import java.util.Locale

/**
 * Stress tile: the five-stop spectrum is the rim *and* the reading.
 *
 * The file only supplies meaning. Every colour, size, radius and animation comes
 * from the warm editorial design system, so the carousel reads as one watch face
 * family instead of five hand-painted gauges.
 */
class StressTileService : TileService() {

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
        const val RESOURCES_VERSION = "warm-editorial-stress-v1"
        private const val DETAIL_ACTIVITY = "io.github.hardbas.wearsync.app.ui.StressDetailActivity"

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
            val hasStress = vitals.stressScore > 0
            val score = vitals.stressScore.coerceIn(0, 100)
            val stale = vitals.isStale

            // Calm -> balanced -> moderate -> high. The level drives ONE token: its
            // ramp stop, which the glow, the label and the sub line all share, so
            // they can never disagree.
            val level = levelOf(score)
            val ramp = if (stale) TileAccents.dim(TileAccents.stress) else TileAccents.stress
            val levelColor = TileAccents.step(ramp, levelStop(level))
            val accent = if (hasStress) levelColor else TileInk.DIM

            val levelWord = when {
                !hasStress -> strings.text(R.string.no_data)
                level == 0 -> strings.text(R.string.stress_relaxed)
                level == 1 -> strings.text(R.string.stress_balanced)
                level == 2 -> strings.text(R.string.stress_moderate)
                else -> strings.text(R.string.stress_high)
            }

            // HRV is the read that explains the score, so it rides the sub line
            // instead of becoming a fourth block.
            val hrvRead = if (vitals.lastHrv > 0) {
                "${strings.text(R.string.hrv_short)} ${vitals.lastHrv} ms"
            } else {
                strings.text(R.string.stress_resting)
            }

            // The pearl's position is the reading itself: 0..100 across the sweep.
            val fraction = score / 100f

            val clickable = TileShell.launchDetail(packageName, DETAIL_ACTIVITY)

            val layers = buildList {
                add(TileShell.glow(TileResources.GLOW, accent))
                add(TileShell.overlay(TileResources.TICKS_STRESS))
                add(
                    if (hasStress) {
                        // The whole dial is the spectrum; the pearl is the score on it.
                        TileDial.spectrum(
                            stops = ramp,
                            pearlColor = TileAccents.ramp(ramp, fraction),
                            pearlAt = fraction
                        )
                    } else {
                        // No reading: rim only — no halo, no pearl, no entrance.
                        TileDial.spectrum(TileAccents.stress, pearlColor = null, halo = false, entrance = false)
                    }
                )
            }

            val stack = TileShell.stack(
                label = TileShell.text(
                    value = strings.text(R.string.tile_stress_title).uppercase(Locale.getDefault()),
                    style = TileType.label(accent)
                ),
                hero = TileShell.text(
                    value = if (hasStress) "$score" else "--",
                    style = TileType.hero(),
                    opacity = if (stale) TileInk.STALE_HERO_ALPHA else null
                ),
                sub = TileShell.pillRow(
                    listOf(
                        TileShell.text(levelWord, TileType.sub(accent)),
                        TileShell.text(" · ", TileType.sub(TileInk.SECONDARY)),
                        TileShell.text(hrvRead, TileType.sub())
                    )
                )
            )

            val pill = TileShell.pill(
                content = TileShell.pillText(strings.text(R.string.details), TileInk.PRIMARY),
                clickable = clickable
            )

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
                .setFreshnessIntervalMillis(60_000L)
                .build()
        }

        /** Calm below 30, balanced below 60, moderate below 80, high from 80. */
        fun levelOf(score: Int): Int = when {
            score < 30 -> 0
            score < 60 -> 1
            score < 80 -> 2
            else -> 3
        }

        /**
         * Ramp stop of a level. The top level sits on the spectrum's carmine end,
         * so "Alto" never reads as the milder clay stop underneath it.
         */
        fun levelStop(level: Int): Int = when (level) {
            0 -> 0
            1 -> 1
            2 -> 2
            else -> TileAccents.stress.lastIndex
        }
    }
}
