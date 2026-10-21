package io.github.hardbas.wearsync.app.tile

import android.content.Context
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
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
import io.github.hardbas.wearsync.app.tile.design.TileChrome
import io.github.hardbas.wearsync.app.tile.design.TileDesign
import io.github.hardbas.wearsync.app.tile.design.TileGauge
import io.github.hardbas.wearsync.app.tile.design.TileMeter
import io.github.hardbas.wearsync.app.tile.design.TileMetric
import io.github.hardbas.wearsync.app.tile.design.TileStrings
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Circular Bezel Arc Stress Tile for Samsung Galaxy Watch (Wear OS 5 / 6).
 *
 * Drawing comes entirely from the shared design system: the same bezel gauge,
 * backdrop, type scale and chrome as the other four tiles. The tile only
 * supplies meaning — the score, the level and the HRV read.
 *
 * Its own second graphic (S7) is a four-level ladder: Relaxed, Balanced,
 * Moderate, High. One segment lit, so the state is readable without the number.
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
        const val RESOURCES_VERSION = "oneui8-stress-circular-v1"
        private const val RES_IC_STRESS = "ic_comp_stress"
        private const val DETAIL_ACTIVITY = "io.github.hardbas.wearsync.app.ui.StressDetailActivity"

        fun buildResources(): ResourceBuilders.Resources {
            return ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    RES_IC_STRESS,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_comp_stress)
                                .build()
                        ).build()
                )
                .build()
        }

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
            val scoreStr = if (hasStress) "$score" else "--"

            // Relaxed -> Balanced -> Moderate -> High. The index drives both the
            // ladder segment and the accent ramp, so they can never disagree.
            val levelIndex = when {
                !hasStress -> 0
                score < 30 -> 0
                score < 60 -> 1
                score < 80 -> 2
                else -> 3
            }
            val accent = if (hasStress) TileDesign.Accent.stress[levelIndex] else TileDesign.Text.MUTED

            val statusText = when (levelIndex) {
                0 -> if (hasStress) strings.text(R.string.stress_relaxed) else strings.text(R.string.no_data)
                1 -> strings.text(R.string.stress_balanced)
                2 -> strings.text(R.string.stress_moderate)
                else -> strings.text(R.string.stress_high)
            }

            // HRV is the read that explains the score, so it folds into the
            // header instead of eating one of the four content blocks.
            val hrvSubtitle = if (vitals.lastHrv > 0) {
                "${strings.text(R.string.hrv_short)} ${vitals.lastHrv} ms"
            } else {
                strings.text(R.string.stress_resting)
            }

            val fraction = if (hasStress) score / 100f else 0f

            val timeText = if (vitals.refreshTimestamp > 0L) {
                DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                    .format(Date(vitals.refreshTimestamp * 1000L))
            } else {
                strings.text(R.string.just_now)
            }

            val clickable = TileChrome.launchDetail(packageName, DETAIL_ACTIVITY)

            // Block 1 — header: icon + ONE short uppercase metric word. The chord at
            // ~67dp above centre leaves ~119dp, so the HRV read and the timestamp
            // moved into the frosted card rather than crowding the bezel arc.
            val headerIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_STRESS)
                .setWidth(dp(14f))
                .setHeight(dp(14f))
                .setColorFilter(
                    LayoutElementBuilders.ColorFilter.Builder()
                        .setTint(argb(accent))
                        .build()
                )
                .build()

            val headerLabel = LayoutElementBuilders.Text.Builder()
                .setText(strings.text(R.string.stress_title).uppercase(Locale.getDefault()))
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.label(accent))
                .build()

            val headerRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(headerIcon)
                .addContent(TileChrome.hSpacer(4f))
                .addContent(headerLabel)
                .build()

            // Hero: the 40sp score with a scale unit so the metric accent has a
            // saturated element next to the number too, not only in the arc.
            val heroRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(scoreStr)
                        .setMaxLines(1)
                        .setFontStyle(TileDesign.Type.hero())
                        .build()
                )
                .addContent(TileChrome.hSpacer(3f))
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        // Locale-neutral scale marker; stress is always scored 0-100.
                        .setText("/100")
                        .setMaxLines(1)
                        .setFontStyle(TileDesign.Type.unit(accent))
                        .build()
                )
                .build()

            // Block 3 — meter on frosted glass: the four-level ladder on the first
            // line, the status word and HRV read under it, then the sync timestamp.
            val statusTextNode = LayoutElementBuilders.Text.Builder()
                .setText(statusText)
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.caption(accent))
                .build()

            val cardCaption = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(statusTextNode)
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(" · ")
                        .setMaxLines(1)
                        .setFontStyle(TileDesign.Type.caption(TileDesign.Text.MUTED))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(hrvSubtitle)
                        .setMaxLines(1)
                        .setFontStyle(TileDesign.Type.caption(TileDesign.Text.SECONDARY))
                        .build()
                )
                .build()

            val cardTimestamp = LayoutElementBuilders.Text.Builder()
                .setText(timeText)
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.caption(TileDesign.Text.MUTED))
                .build()

            val cardContent = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    TileMeter.ladder(
                        count = 4,
                        activeIndex = levelIndex,
                        accent = accent
                    )
                )
                .addContent(TileChrome.spacer(4f))
                .addContent(cardCaption)
                .addContent(TileChrome.spacer(2f))
                .addContent(cardTimestamp)
                .build()

            val meterCard = TileChrome.card(cardContent)

            val actionPill = TileChrome.actionPill(
                LayoutElementBuilders.Text.Builder()
                    .setText(strings.text(R.string.breathe))
                    .setMaxLines(1)
                    .setFontStyle(TileDesign.Type.caption(TileDesign.Text.PRIMARY))
                    .build(),
                clickable
            )

            val contentColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(headerRow)
                .addContent(TileChrome.spacer(3f))
                .addContent(heroRow)
                .addContent(TileChrome.spacer(5f))
                .addContent(meterCard)
                .addContent(TileChrome.spacer(6f))
                .addContent(actionPill)
                .build()

            val gauge = TileGauge.elements(
                accent = accent,
                targetSweep = TileGauge.sweepFor(fraction)
            )

            val rootBox = LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(TileChrome.rootModifiers(TileMetric.STRESS, clickable))
                .addContent(gauge[0])
                .addContent(gauge[1])
                .addContent(contentColumn)
                .build()

            val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(rootBox).build())
                .build()

            val timeline = TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(timelineEntry)
                .build()

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .setFreshnessIntervalMillis(60_000L)
                .build()
        }
    }
}
