package com.ringconn.wear.app.tile

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
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ringconn.wear.app.R
import com.ringconn.wear.app.complication.RingConnComplicationProvider
import com.ringconn.wear.app.data.RingConnVitals
import java.text.NumberFormat
import java.util.Locale

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
        val now = System.currentTimeMillis()
        if (now - lastEnterTimestamp > 1200L) {
            lastEnterTimestamp = now
            TileService.getUpdater(this).requestUpdate(this.javaClass)
        }
    }

    companion object {
        private var lastEnterTimestamp = 0L
        const val RESOURCES_VERSION = "oneui8-summary-v4"
        private const val RES_BG_ACTIVITY_CAPSULE = "oneui_bg_activity_capsule"
        private const val RES_BG_SLEEP_CAPSULE = "oneui_bg_sleep_capsule"
        private const val RES_IC_ACTIVITY_RINGS = "oneui_ic_activity_rings"
        private const val RES_IC_SLEEP = "oneui_sleep_icon"

        fun buildResources(): ResourceBuilders.Resources {
            return ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    RES_BG_ACTIVITY_CAPSULE,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.oneui_bg_activity_capsule)
                                .build()
                        ).build()
                )
                .addIdToImageMapping(
                    RES_BG_SLEEP_CAPSULE,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.oneui_bg_sleep_capsule)
                                .build()
                        ).build()
                )
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
                                .setResourceId(R.drawable.oneui_sleep_icon)
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
            // Exact Samsung Health One UI Watch 8 Colors (from aapt2 dump & watch_oneui_active.png)
            val limeStepsColor = argb(0xFFA0F116.toInt())       // daily_activity_steps_text_color
            val cyanTimeColor = argb(0xFF47CDFF.toInt())        // daily_activity_active_time_color
            val violetCalorieColor = argb(0xFFC580FF.toInt())   // daily_activity_active_calories_color
            val pipeDividerColor = argb(0xFF686D73.toInt())     // subtle pipe divider
            val labelSilverColor = argb(0xFFB7B7BB.toInt())     // daily_activity_effect_on_today_string_text_color
            val sleepLabelColor = argb(0xFFA5AEDE.toInt())      // vitals_half_tile_subtitle_no_data_color
            val whiteColor = argb(0xFFFFFFFF.toInt())
            val iconCircleBg = argb(0x40000000.toInt())
            val progressTrackBg = argb(0x33000000.toInt())

            val rawSteps = vitals.liveSteps
            val stepsFormatted = if (rawSteps > 0) NumberFormat.getIntegerInstance(Locale.getDefault()).format(rawSteps) else "--"
            val activeMins = if (vitals.activeMinutes > 0) vitals.activeMinutes else if (vitals.liveSteps > 0) 3 else 0
            val activityVal = if (activeMins > 0) "${activeMins}m" else "--"
            val calorieVal = if (vitals.liveCalorie > 0) "${vitals.liveCalorie}" else "--"
            val sleepVal = if (vitals.sleepScore > 0) "${vitals.sleepScore}" else "--"
            val spo2Val = if (vitals.lastSpo2 > 0) "${vitals.lastSpo2}%" else "--"

            // Animation Spec: 600L ms smooth sweep with One UI CubicBezierEasing
            val sweepAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(600L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.22f, 0.25f, 0.0f, 1.0f))
                        .build()
                )
                .build()

            // Click 1: Top Capsule (Actividad Diaria) -> DailySummaryDetailActivity (Activity ONLY)
            val activityAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName("com.ringconn.wear.app.ui.DailySummaryDetailActivity")
                        .build()
                ).build()

            val activityClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(activityAction)
                .build()

            // Click 2: Bottom Capsule (Sueño) -> SleepDetailActivity (Sleep ONLY)
            val sleepAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName("com.ringconn.wear.app.ui.SleepDetailActivity")
                        .build()
                ).build()

            val sleepClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(sleepAction)
                .build()

            // =========================================================================
            // CAPSULE 1: Daily Activity (188dp x 66dp, exact clone of watch_oneui_active.png)
            // =========================================================================
            val activityBg = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_BG_ACTIVITY_CAPSULE)
                .setWidth(dp(188f))
                .setHeight(dp(66f))
                .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS)
                .build()

            val ringsIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_ACTIVITY_RINGS)
                .setWidth(dp(36f))
                .setHeight(dp(36f))
                .build()

            val activityTitle = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.daily_activity_title))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.LABEL))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        .setColor(labelSilverColor)
                        .build()
                )
                .build()

            val stepsText = LayoutElementBuilders.Text.Builder()
                .setText(stepsFormatted)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.VALUE))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(limeStepsColor)
                        .build()
                )
                .build()

            val pipe1 = LayoutElementBuilders.Text.Builder()
                .setText(" | ")
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.TITLE))
                        .setColor(pipeDividerColor)
                        .build()
                )
                .build()

            val activityScoreText = LayoutElementBuilders.Text.Builder()
                .setText(activityVal)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.VALUE))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(cyanTimeColor)
                        .build()
                )
                .build()

            val pipe2 = LayoutElementBuilders.Text.Builder()
                .setText(" | ")
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.TITLE))
                        .setColor(pipeDividerColor)
                        .build()
                )
                .build()

            val caloriesScoreText = LayoutElementBuilders.Text.Builder()
                .setText(calorieVal)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.VALUE))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(violetCalorieColor)
                        .build()
                )
                .build()

            val activityMetricsRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(stepsText)
                .addContent(pipe1)
                .addContent(activityScoreText)
                .addContent(pipe2)
                .addContent(caloriesScoreText)
                .build()

            // Dynamic mini progress bar for activity (target 8,000 steps across 108dp track)
            val maxActivityBarWidth = 108f
            val targetStepsProgress = ((rawSteps.toFloat() / 8000f).coerceIn(0.08f, 1f) * maxActivityBarWidth)
            val dynamicActivityBarWidth = DimensionBuilders.DpProp.Builder(targetStepsProgress)
                .setDynamicValue(DynamicBuilders.DynamicFloat.animate(0.1f, targetStepsProgress, sweepAnimSpec))
                .build()

            val activityMiniProgressBar = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(maxActivityBarWidth))
                .setHeight(dp(3.5f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(progressTrackBg)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(2f)).build())
                                .build()
                        )
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(dynamicActivityBarWidth)
                        .setHeight(dp(3.5f))
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setBackground(
                                    ModifiersBuilders.Background.Builder()
                                        .setColor(limeStepsColor)
                                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(2f)).build())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()

            val activityTextColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                .addContent(activityTitle)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).build())
                .addContent(activityMetricsRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(3f)).build())
                .addContent(activityMiniProgressBar)
                .build()

            val activityRow = LayoutElementBuilders.Row.Builder()
                .setWidth(dp(188f))
                .setHeight(dp(66f))
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(14f)).build())
                .addContent(ringsIcon)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(10f)).build())
                .addContent(activityTextColumn)
                .build()

            val activityCapsuleBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(188f))
                .setHeight(dp(66f))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(activityClickable)
                        .build()
                )
                .addContent(activityBg)
                .addContent(activityRow)
                .build()

            // =========================================================================
            // CAPSULE 2: Sleep & Nocturnal Vitals (188dp x 66dp, exact clone of watch_oneui_scrolled.png)
            // =========================================================================
            val sleepBg = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_BG_SLEEP_CAPSULE)
                .setWidth(dp(188f))
                .setHeight(dp(66f))
                .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS)
                .build()

            val sleepIconBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(36f))
                .setHeight(dp(36f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(iconCircleBg)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(18f)).build())
                                .build()
                        )
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Image.Builder()
                        .setResourceId(RES_IC_SLEEP)
                        .setWidth(dp(22f))
                        .setHeight(dp(22f))
                        .build()
                )
                .build()

            val sleepTitle = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.sleep_score))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.LABEL))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        .setColor(sleepLabelColor)
                        .build()
                )
                .build()

            val sleepValueText = LayoutElementBuilders.Text.Builder()
                .setText(sleepVal)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.VALUE))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val sleepPtsLabel = LayoutElementBuilders.Text.Builder()
                .setText(if (vitals.sleepScore > 0) " ${context.getString(R.string.pts_unit)}  ·  " else "  ·  ")
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.LABEL))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                        .setColor(sleepLabelColor)
                        .build()
                )
                .build()

            val spo2InlineText = LayoutElementBuilders.Text.Builder()
                .setText("SpO2 $spo2Val")
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.TITLE))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(cyanTimeColor)
                        .build()
                )
                .build()

            val sleepMetricsRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(sleepValueText)
                .addContent(sleepPtsLabel)
                .addContent(spo2InlineText)
                .build()

            // Dynamic mini progress bar for sleep score (0..100 pts across 108dp track)
            val hasSleepScore = vitals.sleepScore > 0
            val targetSleepProgress = if (hasSleepScore) {
                ((vitals.sleepScore.coerceIn(0, 100).toFloat() / 100f) * maxActivityBarWidth)
            } else {
                0f
            }
            val dynamicSleepBarWidth = DimensionBuilders.DpProp.Builder(targetSleepProgress)
                .setDynamicValue(DynamicBuilders.DynamicFloat.animate(0.1f, targetSleepProgress, sweepAnimSpec))
                .build()

            val sleepMiniProgressBar = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(maxActivityBarWidth))
                .setHeight(dp(3.5f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(progressTrackBg)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(2f)).build())
                                .build()
                        )
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(dynamicSleepBarWidth)
                        .setHeight(dp(3.5f))
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setBackground(
                                    ModifiersBuilders.Background.Builder()
                                        .setColor(sleepLabelColor)
                                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(2f)).build())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()

            val sleepTextColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                .addContent(sleepTitle)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).build())
                .addContent(sleepMetricsRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(3f)).build())
                .addContent(sleepMiniProgressBar)
                .build()

            val sleepRow = LayoutElementBuilders.Row.Builder()
                .setWidth(dp(188f))
                .setHeight(dp(66f))
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(14f)).build())
                .addContent(sleepIconBox)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(10f)).build())
                .addContent(sleepTextColumn)
                .build()

            val sleepCapsuleBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(188f))
                .setHeight(dp(66f))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(sleepClickable)
                        .build()
                )
                .addContent(sleepBg)
                .addContent(sleepRow)
                .build()

            // Stack the two One UI Watch 8 Stadium Capsules vertically (66 + 6 + 66 = 138dp)
            val stackedCapsulesColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(activityCapsuleBox)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
                .addContent(sleepCapsuleBox)
                .build()

            val rootBox = LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(stackedCapsulesColumn)
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
                .setFreshnessIntervalMillis(30_000L)
                .build()
        }
    }
}
