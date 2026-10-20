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
import androidx.wear.protolayout.expression.DynamicDataBuilders
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
 * Dedicated Circular Bezel Arc Daily Activity Tile for Samsung Galaxy Watch (Wear OS 5 / 6).
 * Focused purely on daytime activity, steps, active minutes, calories and goal progress.
 * Sleep has been extracted to its own dedicated celestial SleepTileService.
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
        ): TileBuilders.Tile {
            val limeStepsColor = argb(0xFFA0F116.toInt())       // Neon Lime steps accent
            val cyanTimeColor = argb(0xFF47CDFF.toInt())        // Active time cyan
            val violetCalorieColor = argb(0xFFC580FF.toInt())   // Active calories violet
            val amberDistanceColor = argb(0xFFFFB703.toInt())   // Distance amber
            val pipeDividerColor = argb(0xFF686D73.toInt())     // Subtle pipe divider
            val whiteColor = argb(0xFFFFFFFF.toInt())
            val frostedGlassPill = argb(0x2BFFFFFF.toInt())     // 17% frosted glass
            val frostedPodBg = argb(0x22FFFFFF.toInt())         // Subtle frosted glass
            val bezelTrackColor = argb(0x24FFFFFF.toInt())      // Dark frosted bezel track

            val rawSteps = vitals.liveSteps
            val stepsFormatted = if (rawSteps > 0) NumberFormat.getIntegerInstance(Locale.getDefault()).format(rawSteps) else "--"
            val targetSteps = if (vitals.targetSteps > 0) vitals.targetSteps else 10000
            val targetStepsFormatted = NumberFormat.getIntegerInstance(Locale.getDefault()).format(targetSteps)
            val activeMins = if (vitals.activeMinutes > 0) vitals.activeMinutes else if (vitals.liveSteps > 0) (vitals.liveSteps / 100).coerceAtLeast(1) else 0
            val activityVal = if (activeMins > 0) "${activeMins}m" else "--"
            val calorieVal = if (vitals.liveCalorie > 0) "${vitals.liveCalorie}" else "--"
            val goalPct = if (targetSteps > 0 && rawSteps > 0) ((rawSteps.toFloat() / targetSteps.toFloat()) * 100).toInt() else 0
            val isGoalAchieved = rawSteps >= targetSteps && rawSteps > 0

            val sweepAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(650L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.22f, 0.25f, 0.0f, 1.0f))
                        .build()
                )
                .build()

            // Click -> DailySummaryDetailActivity
            val activityAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName("io.github.hardbas.wearsync.app.ui.DailySummaryDetailActivity")
                        .build()
                ).build()

            val mainTileClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(activityAction)
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
                        AppDataKey<DynamicBuilders.DynamicFloat>("daily_steps_arc_sweep"),
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

            // 2. Title Row: Activity Rings Icon + "ACTIVIDAD"
            val ringsIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_ACTIVITY_RINGS)
                .setWidth(dp(15f))
                .setHeight(dp(15f))
                .build()

            val titleText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.steps_short).uppercase())
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(limeStepsColor)
                        .build()
                )
                .build()

            val headerRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(ringsIcon)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(4f)).setHeight(dp(1f)).build())
                .addContent(titleText)
                .build()

            // 3. Hero Row: Steps Big Number (30sp) + "pasos" (11sp)
            val stepsNumberText = LayoutElementBuilders.Text.Builder()
                .setText(stepsFormatted)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(30f))
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
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
                .addContent(stepsNumberText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(3f)).setHeight(dp(1f)).build())
                .addContent(stepsLabelText)
                .build()

            // 4. Sub-metrics Row (Middle): Active Time + Calories
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

            val subMetricsRowBuilder = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(activityTimeText)
                .addContent(pipeText)
                .addContent(calorieText)

            if (vitals.distanceKm > 0f) {
                val pipe2 = LayoutElementBuilders.Text.Builder()
                    .setText(" · ")
                    .setMaxLines(1)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(11.5f))
                            .setColor(pipeDividerColor)
                            .build()
                    )
                    .build()

                val distanceText = LayoutElementBuilders.Text.Builder()
                    .setText(String.format(Locale.US, "%.1f km", vitals.distanceKm))
                    .setMaxLines(1)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(11.5f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .setColor(amberDistanceColor)
                            .build()
                    )
                    .build()

                subMetricsRowBuilder.addContent(pipe2).addContent(distanceText)
            }

            val subMetricsRow = subMetricsRowBuilder.build()

            // 5. Goal Progress Capsule (Replacing old Sleep pod)
            val goalTextStr = if (isGoalAchieved) {
                context.getString(R.string.goal_achieved)
            } else if (rawSteps > 0) {
                "Meta $targetStepsFormatted ($goalPct%)"
            } else {
                "Meta $targetStepsFormatted"
            }

            val goalTextColor = if (isGoalAchieved) limeStepsColor else whiteColor

            val goalText = LayoutElementBuilders.Text.Builder()
                .setText(goalTextStr)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(10.5f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(goalTextColor)
                        .build()
                )
                .build()

            val goalPillBox = LayoutElementBuilders.Box.Builder()
                .setWidth(wrap())
                .setHeight(dp(20f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(frostedPodBg)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(10f)).build())
                                .build()
                        )
                        .setPadding(
                            ModifiersBuilders.Padding.Builder()
                                .setStart(dp(8f))
                                .setEnd(dp(8f))
                                .build()
                        )
                        .build()
                )
                .addContent(goalText)
                .build()

            // 6. Action Button ("Detalles")
            val actionButtonText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.details))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val actionPillBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(88f))
                .setHeight(dp(26f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(mainTileClickable)
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(frostedGlassPill)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(13f)).build())
                                .build()
                        )
                        .build()
                )
                .addContent(actionButtonText)
                .build()

            // Center Column Content
            val centerContentColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(headerRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).setWidth(dp(1f)).build())
                .addContent(heroStepsRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(3f)).setWidth(dp(1f)).build())
                .addContent(subMetricsRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(5f)).setWidth(dp(1f)).build())
                .addContent(goalPillBox)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).setWidth(dp(1f)).build())
                .addContent(actionPillBox)
                .build()

            // Root Canvas: Full-bleed Circular Box with Bezel Arcs + Center Content
            val rootBox = LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(mainTileClickable)
                        .setContentUpdateAnimation(
                            ModifiersBuilders.AnimatedVisibility.Builder()
                                .setEnterTransition(ModifiersBuilders.DefaultContentTransitions.fadeIn())
                                .setExitTransition(ModifiersBuilders.DefaultContentTransitions.fadeOut())
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
                .addKeyToValueMapping(
                    AppDataKey<DynamicBuilders.DynamicFloat>("daily_steps_arc_sweep"),
                    DynamicDataBuilders.DynamicDataValue.fromFloat(targetStepsSweep)
                )
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
