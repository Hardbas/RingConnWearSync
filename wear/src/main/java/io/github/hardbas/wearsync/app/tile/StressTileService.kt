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
import io.github.hardbas.wearsync.app.tile.design.TileInk
import io.github.hardbas.wearsync.app.tile.design.TileMotion
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
        const val RESOURCES_VERSION = "warm-editorial-stress-v1"

        /** Key of this tile in [TileDrawerState]. */
        const val TILE_ID = "stress"

        private const val DETAIL_ACTIVITY = "io.github.hardbas.wearsync.app.ui.StressDetailActivity"

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

            val levelWord = levelWord(strings, hasStress, level)

            // HRV is the read that explains the score, so it rides the sub line
            // instead of becoming a fourth block. The render names the read with
            // the word HRV and prints the number without a unit.
            val hrvRead = if (vitals.lastHrv > 0) {
                "${strings.text(R.string.hrv_short)} ${vitals.lastHrv}"
            } else {
                strings.text(R.string.stress_resting)
            }

            // The pearl's position is the reading itself: 0..100 across the sweep.
            val fraction = score / 100f

            // The pill asks for a reload and comes back with id `pill`, which
            // opens the drawer; the face behind it keeps launching the detail
            // screen, exactly like the pulse tile.
            val clickable = TileShell.launchDetail(packageName, DETAIL_ACTIVITY)

            val layers = buildList {
                // The measured expressive layer: a light streak sweeping the rim,
                // drawn first so it sits behind the scale. Rotation only, pivot on
                // the tile centre.
                if (hasStress && !stale) {
                    add(
                        TileAmbient.sweep(
                            resourceId = TileResources.SWEEP,
                            accent = accent,
                            durationMs = TileMotion.SWEEP_MS_STRESS,
                            opacity = 0.35f
                        )
                    )
                }
                // The radial body breathing behind the scale: the 4500ms period of
                // the measured glow breath.
                if (hasStress && !stale) {
                    add(
                        TileAmbient.breathing(
                            resourceId = TileResources.GLOW,
                            accent = accent,
                            sizeDp = 110f,
                            min = 0.10f,
                            max = 0.45f,
                            durationMs = 4500L
                        )
                    )
                }
                add(TileShell.glow(TileResources.GLOW, accent))
                add(TileDial.track(accent, breathing = hasStress && !stale))
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
                        TileDial.spectrum(TileAccents.stress, pearlColor = null, halo = false)
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
                clickable = TileDrawer.tapToOpen()
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
         * The open drawer: the whole face becomes the S12 panel, built from the
         * tile's own score and the real seven-day history.
         */
        private fun drawerTile(
            strings: TileStrings,
            packageName: String,
            vitals: RingConnVitals,
            history: List<DailyHistoryEntry>
        ): TileBuilders.Tile {
            val days = history.sortedBy { it.epochDay }.takeLast(DRAWER_DAYS)
            val hasStress = vitals.stressScore > 0
            val score = vitals.stressScore.coerceIn(0, 100)
            val level = levelOf(score)
            // The level drives the drawer's accent too, so the panel and the face
            // can never disagree about how the day reads.
            val accent = if (hasStress) {
                TileAccents.step(TileAccents.stress, levelStop(level))
            } else {
                TileInk.DIM
            }

            val root = TileDrawer.drawer(
                accent = accent,
                kicker = strings.text(R.string.tile_drawer_stress_kicker).uppercase(Locale.getDefault()),
                title = String.format(
                    Locale.getDefault(),
                    strings.text(R.string.tile_drawer_stress_title),
                    if (hasStress) "$score" else NO_VALUE,
                    levelWord(strings, hasStress, level)
                ),
                meta = strings.text(R.string.tile_drawer_stress_meta),
                bars = days.map { it.stressScore.toFloat() },
                barColor = accent,
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
                .setFreshnessIntervalMillis(60_000L)
                .build()

        private fun levelWord(strings: TileStrings, hasStress: Boolean, level: Int): String = when {
            !hasStress -> strings.text(R.string.no_data)
            level == 0 -> strings.text(R.string.stress_relaxed)
            level == 1 -> strings.text(R.string.stress_balanced)
            level == 2 -> strings.text(R.string.stress_moderate)
            else -> strings.text(R.string.stress_high)
        }

        private const val NO_VALUE = "--"

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
