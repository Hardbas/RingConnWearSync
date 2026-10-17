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
import com.ringconn.wear.app.R
import com.ringconn.wear.app.complication.RingConnComplicationProvider
import com.ringconn.wear.app.data.RingConnVitals
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Solitary One UI Watch 8 Heart Rate Tile for Samsung Galaxy Watch 8 (SM-L500, Wear OS 6).
 * Follows exact Samsung Health stadium capsule geometry (195dp x 142dp, corner radius 71dp),
 * solitary HR telemetry, animated min-max sweep, and "Medir" glassmorphic pill button.
 */
class HeartRateTileService : TileService() {

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
        const val RESOURCES_VERSION = "oneui8-hr-solitary-v3"
        private const val RES_BG_HR_CAPSULE = "oneui_bg_hr_capsule"
        private const val RES_IC_HR = "oneui_hr_icon"
        private const val RES_HR_DOT = "oneui_hr_dot"

        fun buildResources(): ResourceBuilders.Resources {
            return ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    RES_BG_HR_CAPSULE,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.oneui_bg_hr_capsule)
                                .build()
                        ).build()
                )
                .addIdToImageMapping(
                    RES_IC_HR,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.oneui_hr_icon)
                                .build()
                        ).build()
                )
                .addIdToImageMapping(
                    RES_HR_DOT,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.oneui_hr_dot)
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
            val hrInt = vitals.lastHeartRate
            val hrValue = if (vitals.lastHeartRate > 0) "${vitals.lastHeartRate}" else "--"

            // Calculate subtitle timestamp (purely sync time, NO SpO2 or battery clutter!)
            val subtitleTextStr = if (vitals.refreshTimestamp > 0L) {
                val diffSec = (System.currentTimeMillis() / 1000L) - vitals.refreshTimestamp
                if (diffSec in 0..120) {
                    context.getString(R.string.just_now)
                } else {
                    DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(vitals.refreshTimestamp * 1000L))
                }
            } else {
                context.getString(R.string.just_now)
            }

            // Exact Samsung Health One UI Watch 8 Heart Rate Colors
            val whiteColor = argb(0xFFFFFFFF.toInt())
            val subtitlePinkColor = argb(0xFFF99BD6.toInt())      // heart_rate_tile_subtitle_text_color (#FFF99BD6)
            val bpmUnitWarmColor = argb(0xFFFBCEB6.toInt())       // heart_rate_tile_bpm_text_color (#FFFBCEB6)
            val minMaxBarColor = argb(0xFFFF5D99.toInt())         // heart_rate_tile_min_max_progress_color (#FFFF5D99)
            val translucentGlassBlack = argb(0x80000000.toInt())  // 50% glassmorphic black (#80000000)

            // Click 1: Main Stadium Capsule -> HeartRateDetailActivity
            val mainCapsuleAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName("com.ringconn.wear.app.ui.HeartRateDetailActivity")
                        .build()
                ).build()

            val mainCapsuleClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(mainCapsuleAction)
                .build()

            // Click 2: "Medir" Pill Button -> HeartRateMeasureSolitaryActivity (instant measurement trigger)
            val measurePillAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName("com.ringconn.wear.app.ui.HeartRateMeasureSolitaryActivity")
                        .build()
                ).build()

            val measurePillClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(measurePillAction)
                .build()

            // 1. One UI Watch 8 Stadium Capsule Background (184dp x 132dp, radius 66dp - perfectly fits round bezel)
            val bgImage = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_BG_HR_CAPSULE)
                .setWidth(dp(184f))
                .setHeight(dp(132f))
                .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS)
                .build()

            // 2. Title: "Frecuencia Cardíaca" (12.5sp bold white)
            val titleText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.tile_heart_rate_title))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.LABEL))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            // 3. Subtitle: Last sync timestamp in One UI Pink (#FFF99BD6)
            val subtitleText = LayoutElementBuilders.Text.Builder()
                .setText(subtitleTextStr)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.LABEL))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                        .setColor(subtitlePinkColor)
                        .build()
                )
                .build()

            // Continuous heartbeat pulse animation (650ms reverse loop, reverse-engineered from shealth cg7.java:482)
            val heartbeatPulseAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(650L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.4f, 0.0f, 0.2f, 1.0f))
                        .build()
                )
                .setRepeatable(
                    AnimationParameterBuilders.Repeatable.Builder()
                        .setRepeatMode(AnimationParameterBuilders.REPEAT_MODE_REVERSE)
                        .build()
                )
                .build()

            val dynamicHeartColor = DynamicBuilders.DynamicColor.animate(
                0xFFFF5D99.toInt(),
                0xFF8A1E4A.toInt(),
                heartbeatPulseAnimSpec
            )

            val dynamicHeartTint = androidx.wear.protolayout.ColorBuilders.ColorProp.Builder(0xFFFF5D99.toInt())
                .setDynamicValue(dynamicHeartColor)
                .build()

            // 4. Hero row: Heart icon (15dp) + BPM value in large bold font (26sp) + "ppm" / "bpm" unit (11.5sp)
            val hrIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_HR)
                .setWidth(dp(15f))
                .setHeight(dp(15f))
                .setColorFilter(
                    LayoutElementBuilders.ColorFilter.Builder()
                        .setTint(dynamicHeartTint)
                        .build()
                )
                .build()

            val hrNumberText = LayoutElementBuilders.Text.Builder()
                .setText(hrValue)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.HERO))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val bpmUnitText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.bpm_unit).lowercase())
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.LABEL))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        .setColor(bpmUnitWarmColor)
                        .build()
                )
                .build()

            val heroHrRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(hrIcon)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(5f)).setHeight(dp(1f)).build())
                .addContent(hrNumberText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(3f)).setHeight(dp(1f)).build())
                .addContent(bpmUnitText)
                .build()

            // 5. Min-Max Range Bar: Horizontal animated bar (124dp x 10dp) with DynamicFloat.animate & indicator dot
            val barWidthDp = 124f
            val barHeightDp = 10f
            val hasMinMax = vitals.minHeartRate > 0 && vitals.maxHeartRate > 0
            val minHr = if (hasMinMax) vitals.minHeartRate else (if (vitals.lastHeartRate > 0) vitals.lastHeartRate else 0)
            val maxHr = if (hasMinMax) vitals.maxHeartRate else (if (vitals.lastHeartRate > 0) vitals.lastHeartRate else 0)
            val scale = barWidthDp / 110f // 40..150 bpm span
            val rangeStartDp = if (minHr > 0) ((minHr - 40) * scale).coerceIn(0f, 70f) else 0f
            val rangeLengthDp = if (maxHr > minHr) ((maxHr - minHr) * scale).coerceIn(4f, barWidthDp - rangeStartDp) else (if (minHr > 0) 10f else 0f)
            val dotHr = if (vitals.lastHeartRate > 0) vitals.lastHeartRate else minHr
            val dotPosDp = if (dotHr > 0) (((dotHr.coerceIn(40, 150) - 40) * scale) - 3.75f).coerceIn(0f, barWidthDp - 8f) else 0f

            // One UI Watch 8 Animation Parameters (600L ms sweep with CubicBezierEasing 0.22, 0.25, 0.0, 1.0)
            val sweepAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(600L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.22f, 0.25f, 0.0f, 1.0f))
                        .build()
                )
                .build()

            // Continuous pulse animation for indicator dot (800ms reverse loop, reverse-engineered from shealth cg7.java:482)
            val pulseAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(800L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.4f, 0.0f, 0.2f, 1.0f))
                        .build()
                )
                .setRepeatable(
                    AnimationParameterBuilders.Repeatable.Builder()
                        .setRepeatMode(AnimationParameterBuilders.REPEAT_MODE_REVERSE)
                        .build()
                )
                .build()

            val dynamicRangeStart = DimensionBuilders.DpProp.Builder(rangeStartDp)
                .setDynamicValue(DynamicBuilders.DynamicFloat.animate(AppDataKey("hr_range_start"), sweepAnimSpec))
                .build()

            val dynamicRangeLength = DimensionBuilders.DpProp.Builder(rangeLengthDp)
                .setDynamicValue(DynamicBuilders.DynamicFloat.animate(AppDataKey("hr_range_length"), sweepAnimSpec))
                .build()

            val dynamicDotPos = DimensionBuilders.DpProp.Builder(dotPosDp)
                .setDynamicValue(DynamicBuilders.DynamicFloat.animate(AppDataKey("hr_dot_pos"), sweepAnimSpec))
                .build()

            val dynamicDotSize = DimensionBuilders.DpProp.Builder(7.5f)
                .setDynamicValue(DynamicBuilders.DynamicFloat.animate(6f, 9f, pulseAnimSpec))
                .build()

            val barTrackBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(translucentGlassBlack)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(5f)).build())
                                .build()
                        )
                        .build()
                )
                .build()

            val activeRangeRow = LayoutElementBuilders.Row.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(dynamicRangeStart)
                        .setHeight(dp(barHeightDp))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(dynamicRangeLength)
                        .setHeight(dp(barHeightDp))
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setBackground(
                                    ModifiersBuilders.Background.Builder()
                                        .setColor(minMaxBarColor)
                                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(5f)).build())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()

            val dynamicDotColor = DynamicBuilders.DynamicColor.animate(
                0xFFFFFFFF.toInt(),
                0x33FFFFFF.toInt(),
                heartbeatPulseAnimSpec
            )

            val dynamicDotTint = androidx.wear.protolayout.ColorBuilders.ColorProp.Builder(0xFFFFFFFF.toInt())
                .setDynamicValue(dynamicDotColor)
                .build()

            val dotIndicatorRow = LayoutElementBuilders.Row.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(dynamicDotPos)
                        .setHeight(dp(barHeightDp))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Image.Builder()
                        .setResourceId(RES_HR_DOT)
                        .setWidth(dp(7.5f))
                        .setHeight(dp(7.5f))
                        .setColorFilter(
                            LayoutElementBuilders.ColorFilter.Builder()
                                .setTint(dynamicDotTint)
                                .build()
                        )
                        .build()
                )
                .build()

            val minMaxChartBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(barTrackBox)
                .addContent(activeRangeRow)
                .addContent(dotIndicatorRow)
                .build()

            // 6. Bottom button: Glassmorphic "Medir" pill button (82dp x 24dp)
            val measureButtonText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.measure))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.LABEL))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val measurePillBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(92.3f))
                .setHeight(dp(26.7f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(measurePillClickable)
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(translucentGlassBlack)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(12.5f)).build())
                                .build()
                        )
                        .build()
                )
                .addContent(measureButtonText)
                .build()

            // Assemble into the 184dp x 132dp Stadium Capsule
            val capsuleContentColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(dp(184f))
                .setHeight(dp(132f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(7f)).setWidth(dp(1f)).build())
                .addContent(titleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(1.5f)).setWidth(dp(1f)).build())
                .addContent(subtitleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(3f)).setWidth(dp(1f)).build())
                .addContent(heroHrRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(5f)).setWidth(dp(1f)).build())
                .addContent(minMaxChartBox)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).setWidth(dp(1f)).build())
                .addContent(measurePillBox)
                .build()

            val stadiumCapsuleBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(184f))
                .setHeight(dp(132f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(mainCapsuleClickable)
                        .setContentUpdateAnimation(
                            ModifiersBuilders.AnimatedVisibility.Builder()
                                .setEnterTransition(ModifiersBuilders.DefaultContentTransitions.fadeIn())
                                .build()
                        )
                        .build()
                )
                .addContent(bgImage)
                .addContent(capsuleContentColumn)
                .build()

            val rootBox = LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(stadiumCapsuleBox)
                .build()

            val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(rootBox).build())
                .build()

            val timeline = TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(timelineEntry)
                .build()

            val tileState = StateBuilders.State.Builder()
                .addKeyToValueMapping(AppDataKey("hr_range_start"), DynamicDataBuilders.DynamicDataValue.fromFloat(rangeStartDp))
                .addKeyToValueMapping(AppDataKey("hr_range_length"), DynamicDataBuilders.DynamicDataValue.fromFloat(rangeLengthDp))
                .addKeyToValueMapping(AppDataKey("hr_dot_pos"), DynamicDataBuilders.DynamicDataValue.fromFloat(dotPosDp))
                .build()

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .setState(tileState)
                .setFreshnessIntervalMillis(30_000L)
                .build()
        }
    }
}
