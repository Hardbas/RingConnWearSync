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
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Circular Bezel Arc Daily Activity Tile for Samsung Galaxy Watch (Wear OS 5 / 6).
 *
 * Focused purely on daytime activity, steps, active minutes, calories and goal
 * progress. Sleep has been extracted to its own dedicated celestial
 * SleepTileService.
 *
 * Drawing comes from the shared design system; its own second graphic (S7) is
 * the goal bar, so progress toward the daily target is readable at a glance.
 */
class DailySummaryTileService : TileService() {

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
        const val RESOURCES_VERSION = "oneui8-summary-circular-v1"
        private const val RES_IC_ACTIVITY_RINGS = "oneui_ic_activity_rings"
        private const val DETAIL_ACTIVITY = "io.github.hardbas.wearsync.app.ui.DailySummaryDetailActivity"

        fun buildResources(): ResourceBuilders.Resources {
            return ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    RES_IC_ACTIVITY_RINGS,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.oneui_ic_activity_rings)
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
            val accent = TileDesign.Accent.activity

            val rawSteps = vitals.liveSteps
            val stepsFormatted = if (rawSteps > 0) {
                NumberFormat.getIntegerInstance(Locale.getDefault()).format(rawSteps)
            } else {
                "--"
            }
            val targetSteps = if (vitals.targetSteps > 0) vitals.targetSteps else 10000
            val targetStepsFormatted = NumberFormat.getIntegerInstance(Locale.getDefault()).format(targetSteps)
            val activeMins = if (vitals.activeMinutes > 0) {
                vitals.activeMinutes
            } else if (vitals.liveSteps > 0) {
                (vitals.liveSteps / 100).coerceAtLeast(1)
            } else {
                0
            }
            val activityVal = if (activeMins > 0) "${activeMins}m" else "--"
            val calorieVal = if (vitals.liveCalorie > 0) "${vitals.liveCalorie}" else "--"
            val goalPct = if (rawSteps > 0) ((rawSteps.toFloat() / targetSteps.toFloat()) * 100).toInt() else 0
            val isGoalAchieved = rawSteps >= targetSteps && rawSteps > 0

            val fraction = if (rawSteps > 0) rawSteps.toFloat() / targetSteps.toFloat() else 0f

            val timeText = if (vitals.refreshTimestamp > 0L) {
                DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                    .format(Date(vitals.refreshTimestamp * 1000L))
            } else {
                strings.text(R.string.just_now)
            }

            val clickable = TileChrome.launchDetail(packageName, DETAIL_ACTIVITY)

            // Block 1 — header: icon + ONE short uppercase metric word. The chord at
            // ~74dp above centre leaves ~105dp, so the timestamp moved into the
            // frosted card instead of running into the bezel arc.
            val headerIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_ACTIVITY_RINGS)
                .setWidth(dp(14f))
                .setHeight(dp(14f))
                .setColorFilter(
                    LayoutElementBuilders.ColorFilter.Builder()
                        .setTint(argb(accent))
                        .build()
                )
                .build()

            val headerLabel = LayoutElementBuilders.Text.Builder()
                .setText(strings.text(R.string.steps_short).uppercase(Locale.getDefault()))
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

            // Hero: the 40sp step count with its unit, alone in its row.
            val heroNumber = LayoutElementBuilders.Text.Builder()
                .setText(stepsFormatted)
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.hero())
                .build()

            val heroUnit = LayoutElementBuilders.Text.Builder()
                .setText(strings.text(R.string.steps_short).lowercase(Locale.getDefault()))
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.unit(accent))
                .build()

            val heroRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(heroNumber)
                .addContent(TileChrome.hSpacer(3f))
                .addContent(heroUnit)
                .build()

            // Block 3 — meter on frosted glass: the goal bar on the first line, then
            // the goal line, the sub-metrics and the sync timestamp. Steps carries
            // four readings, so they need two caption lines instead of one; four
            // readings on one line would be wider than the chord at this height.
            val goalTextStr = when {
                isGoalAchieved -> strings.text(R.string.goal_achieved)
                rawSteps > 0 -> "${strings.text(R.string.goal_label)} $targetStepsFormatted ($goalPct%)"
                else -> "${strings.text(R.string.goal_label)} $targetStepsFormatted"
            }

            val goalRow = LayoutElementBuilders.Text.Builder()
                .setText(goalTextStr)
                .setMaxLines(1)
                .setFontStyle(
                    TileDesign.Type.caption(
                        if (isGoalAchieved) accent else TileDesign.Text.SECONDARY
                    )
                )
                .build()

            val subMetricsRowBuilder = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

            subMetricsRowBuilder
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(activityVal)
                        .setMaxLines(1)
                        .setFontStyle(TileDesign.Type.caption(TileDesign.Accent.spo2))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(" · ")
                        .setMaxLines(1)
                        .setFontStyle(TileDesign.Type.caption(TileDesign.Text.MUTED))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("$calorieVal kcal")
                        .setMaxLines(1)
                        .setFontStyle(TileDesign.Type.caption(TileDesign.Accent.vitals))
                        .build()
                )

            if (vitals.distanceKm > 0f) {
                subMetricsRowBuilder
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(" · ")
                            .setMaxLines(1)
                            .setFontStyle(TileDesign.Type.caption(TileDesign.Text.MUTED))
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(String.format(Locale.US, "\u2248 %.1f km", vitals.distanceKm))
                            .setMaxLines(1)
                            .setFontStyle(TileDesign.Type.caption(TileDesign.Accent.starlight))
                            .build()
                    )
            }

            val cardTimestamp = LayoutElementBuilders.Text.Builder()
                .setText(timeText)
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.caption(TileDesign.Text.MUTED))
                .build()

            val cardContent = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(TileMeter.progress(fraction = fraction, accent = accent))
                .addContent(TileChrome.spacer(3f))
                .addContent(goalRow)
                .addContent(TileChrome.spacer(2f))
                .addContent(subMetricsRowBuilder.build())
                .addContent(TileChrome.spacer(2f))
                .addContent(cardTimestamp)
                .build()

            val meterCard = TileChrome.card(cardContent)

            val actionPill = TileChrome.actionPill(
                LayoutElementBuilders.Text.Builder()
                    .setText(strings.text(R.string.details))
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
                .setModifiers(TileChrome.rootModifiers(TileMetric.ACTIVITY, clickable))
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
