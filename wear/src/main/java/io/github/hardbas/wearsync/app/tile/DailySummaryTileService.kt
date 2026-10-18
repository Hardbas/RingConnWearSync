package io.github.hardbas.wearsync.app.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import androidx.wear.protolayout.expression.AppDataKey
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider
import io.github.hardbas.wearsync.app.data.RingConnVitals
import java.text.NumberFormat
import java.util.Locale

/**
 * Dedicated Circular Bezel Arc Daily Summary Tile for Samsung Galaxy Watch (Wear OS 5 / 6).
 * Ergonomically calibrated for round screens to ensure zero text crowding and maximum glanceability.
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
        private const val RES_IC_SLEEP = "ic_vitals_sleep"

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
                .addIdToImageMapping(
                    RES_IC_SLEEP,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_vitals_sleep)
                                .build()
                        ).build()
                )
                .build()
        }

        fun buildTile(
            context: Context,
            deviceParams: DeviceParameters,
            vitals: RingConnVitals = RingConnComplicationProvider.getLatestVitals(context)
        ): TileBuilders.Tile {
            val limeStepsColor = argb(0xFFA0F116.toInt())       // Neon Lime steps accent
            val cyanTimeColor = argb(0xFF47CDFF.toInt())        // Active time cyan
            val violetCalorieColor = argb(0xFFC580FF.toInt())   // Active calories violet
            val pipeDividerColor = argb(0xFF686D73.toInt())     // Subtle pipe divider
            val labelSilverColor = argb(0xFFB7B7BB.toInt())     // Silver label
            val sleepLabelColor = argb(0xFFA5AEDE.toInt())      // Soft lavender sleep label
            val whiteColor = argb(0xFFFFFFFF.toInt())
            val frostedPodBg = argb(0x2BFFFFFF.toInt())         // 17% frosted glass
            val bezelTrackColor = argb(0x24FFFFFF.toInt())      // Dark frosted bezel track

            val rawSteps = vitals.liveSteps
            val stepsFormatted = if (rawSteps > 0) NumberFormat.getIntegerInstance(Locale.getDefault()).format(rawSteps) else "--"
            val targetSteps = if (vitals.targetSteps > 0) vitals.targetSteps else 10000
            val activeMins = if (vitals.activeMinutes > 0) vitals.activeMinutes else if (vitals.liveSteps > 0) (vitals.liveSteps / 100).coerceAtLeast(1) else 0
            val activityVal = if (activeMins > 0) "${activeMins}m" else "--"
            val calorieVal = if (vitals.liveCalorie > 0) "${vitals.liveCalorie}" else "--"
            val sleepVal = if (vitals.sleepScore > 0) "${vitals.sleepScore}" else "--"
            val spo2Val = if (vitals.lastSpo2 > 0) "${vitals.lastSpo2}%" else "--"

            val sweepAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(650L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.22f, 0.25f, 0.0f, 1.0f))
                        .build()
                )
                .build()

            // Click 1: Activity Section -> DailySummaryDetailActivity
            val activityAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName("io.github.hardbas.wearsync.app.ui.DailySummaryDetailActivity")
                        .build()
                ).build()

            val activityClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(activityAction)
                .build()

            // Click 2: Sleep Section -> SleepDetailActivity
            val sleepAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName("io.github.hardbas.wearsync.app.ui.SleepDetailActivity")
                        .build()
                ).build()

            val sleepClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(sleepAction)
                .build()

            // 1. Perimeter Steps Goal Bezel Arc (260° span, 140° to 40°)
            val totalArcSpanDegrees = 260f
            val arcStartAngle = 140f

            val targetStepsSweep = if (rawSteps > 0) {
                val fraction = (rawSteps.toFloat() / targetSteps.toFloat()).coerceIn(0.04f, 1.0f)
                fraction * totalArcSpanDegrees
            } else {
                12f
            }

            val dynamicStepsSweep = DimensionBuilders.DegreesProp.Builder(targetStepsSweep)
                .setDynamicValue(
                    DynamicBuilders.DynamicFloat.animate(
                        AppDataKey("daily_steps_arc_sweep"),
                        sweepAnimSpec
                    )
                )
                .build()

            val angularConstraint = DimensionBuilders.AngularLayoutConstraint.Builder(totalArcSpanDegrees)
                .build()

            val bgTrackArcLine = LayoutElementBuilders.ArcLine.Builder()
                .setLength(DimensionBuilders.degrees(totalArcSpanDegrees))
                .setThickness(dp(6.5f))
                .setColor(bezelTrackColor)
                .build()

            val bgArc = LayoutElementBuilders.Arc.Builder()
                .setAnchorAngle(DimensionBuilders.degrees(arcStartAngle))
                .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                .addContent(bgTrackArcLine)
                .build()

            val activeArcLine = LayoutElementBuilders.ArcLine.Builder()
                .setLength(dynamicStepsSweep)
                .setLayoutConstraintsForDynamicLength(angularConstraint)
                .setThickness(dp(6.5f))
                .setColor(limeStepsColor)
                .build()

            val activeArc = LayoutElementBuilders.Arc.Builder()
                .setAnchorAngle(DimensionBuilders.degrees(arcStartAngle))
                .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                .addContent(activeArcLine)
                .build()

            // 2. Activity Header & Hero (Upper Half)
            val ringsIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_ACTIVITY_RINGS)
                .setWidth(dp(18f))
                .setHeight(dp(18f))
                .build()

            val stepsNumberText = LayoutElementBuilders.Text.Builder()
                .setText(stepsFormatted)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(28f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val stepsLabelText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.steps_short).lowercase())
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11.5f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        .setColor(limeStepsColor)
                        .build()
                )
                .build()

            val heroStepsRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(ringsIcon)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(5f)).setHeight(dp(1f)).build())
                .addContent(stepsNumberText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(4f)).setHeight(dp(1f)).build())
                .addContent(stepsLabelText)
                .build()

            val activityTimeText = LayoutElementBuilders.Text.Builder()
                .setText(activityVal)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11.5f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(cyanTimeColor)
                        .build()
                )
                .build()

            val pipeText = LayoutElementBuilders.Text.Builder()
                .setText(" · ")
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11.5f))
                        .setColor(pipeDividerColor)
                        .build()
                )
                .build()

            val calorieText = LayoutElementBuilders.Text.Builder()
                .setText("$calorieVal kcal")
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11.5f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(violetCalorieColor)
                        .build()
                )
                .build()

            val subMetricsRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(activityTimeText)
                .addContent(pipeText)
                .addContent(calorieText)
                .build()

            val activitySection = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(activityClickable).build())
                .addContent(heroStepsRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2.5f)).setWidth(dp(1f)).build())
                .addContent(subMetricsRow)
                .build()

            // 3. Sleep Glassmorphic Pod (Lower Half)
            val sleepIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_SLEEP)
                .setWidth(dp(14f))
                .setHeight(dp(14f))
                .build()

            val sleepValueText = LayoutElementBuilders.Text.Builder()
                .setText(sleepVal)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(14f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val sleepPtsLabel = LayoutElementBuilders.Text.Builder()
                .setText(if (vitals.sleepScore > 0) " ${context.getString(R.string.pts_unit)} · " else " · ")
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(10.5f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                        .setColor(sleepLabelColor)
                        .build()
                )
                .build()

            val spo2InlineText = LayoutElementBuilders.Text.Builder()
                .setText("SpO2 $spo2Val")
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(cyanTimeColor)
                        .build()
                )
                .build()

            val sleepInnerRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(sleepIcon)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(5f)).setHeight(dp(1f)).build())
                .addContent(sleepValueText)
                .addContent(sleepPtsLabel)
                .addContent(spo2InlineText)
                .build()

            val sleepPodBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(136f))
                .setHeight(dp(28f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(sleepClickable)
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(frostedPodBg)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(14f)).build())
                                .build()
                        )
                        .build()
                )
                .addContent(sleepInnerRow)
                .build()

            // Center Column Content
            val titleText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.tile_summary_title))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(labelSilverColor)
                        .build()
                )
                .build()

            val centerContentColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(titleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).setWidth(dp(1f)).build())
                .addContent(activitySection)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(7f)).setWidth(dp(1f)).build())
                .addContent(sleepPodBox)
                .build()

            // Root Canvas: Full-bleed Circular Box with Bezel Arcs + Center Content
            val rootBox = LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setContentUpdateAnimation(
                            ModifiersBuilders.AnimatedVisibility.Builder()
                                .setEnterTransition(ModifiersBuilders.DefaultContentTransitions.fadeIn())
                                .build()
                        )
                        .build()
                )
                .addContent(bgArc)
                .addContent(activeArc)
                .addContent(centerContentColumn)
                .build()

            val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(rootBox).build())
                .build()

            val timeline = TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(timelineEntry)
                .build()

            val tileState = StateBuilders.State.Builder()
                .build()

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .setState(tileState)
                .setFreshnessIntervalMillis(60_000L)
                .build()
        }
    }
}
