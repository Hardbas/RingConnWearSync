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
import java.util.Date
import java.util.Locale

/**
 * Dedicated Samsung One UI Watch 8 Heart Rate Tile for Samsung Galaxy Watch (Wear OS 5 / 6).
 * Follows exact Samsung Health architecture (ra7.java & xf7.java):
 * - Deep obsidian wine stadium capsule (190dp x 138dp) with rose glass rim definition
 * - Solitary heart rate telemetry with pulsating heartbeat tint icon
 * - Animated horizontal min-max daily telemetry range bar with glowing indicator dot
 * - Authentic physiological "En reposo" (Resting HR) / "Mín · Máx" metrics (no workout misclassifications)
 * - Glassmorphic "Medir" / "Sincronizar" pill action button
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
        const val RESOURCES_VERSION = "oneui8-hr-official-v1"
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
            val hrValue = if (hrInt > 0) "$hrInt" else "--"

            // Timestamp subtitle
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

            // Authentic One UI Heart Rate Palette (from Samsung Health ra7.java / of8.java)
            val whiteColor = argb(0xFFFFFFFF.toInt())
            val subtitlePinkColor = argb(0xFFF99BD6.toInt())      // One UI rose (#FFF99BD6)
            val bpmUnitWarmColor = argb(0xFFFBCEB6.toInt())       // Warm peach (#FFFBCEB6)
            val minMaxBarColor = argb(0xFFFF5D99.toInt())         // One UI crimson bar (#FFFF5D99)
            val translucentGlassBlack = argb(0x66000000.toInt())  // 40% glassmorphic black
            val restingTextColor = argb(0xFFCBD5E1.toInt())       // Slate 300 neutral (#FFCBD5E1)

            // Click 1: Full capsule -> HeartRateDetailActivity
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

            // Click 2: "Medir" / "Sincronizar" Button -> HeartRateMeasureSolitaryActivity
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

            // 1. One UI Stadium Capsule Background (190dp x 138dp, corner radius 69dp)
            val bgImage = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_BG_HR_CAPSULE)
                .setWidth(dp(190f))
                .setHeight(dp(138f))
                .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS)
                .build()

            // 2. Title: "Frecuencia Cardíaca" (12.5sp bold white)
            val titleText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.tile_heart_rate_title))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(12f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            // 3. Subtitle: Last sync timestamp in One UI Rose
            val subtitleText = LayoutElementBuilders.Text.Builder()
                .setText(subtitleTextStr)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(10.5f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                        .setColor(subtitlePinkColor)
                        .build()
                )
                .build()

            // Continuous heartbeat pulse animation (650ms reverse loop, shealth cg7.java:482)
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

            // 4. Hero row: Heart icon (16dp) + BPM (28sp bold white) + "ppm" (12sp warm peach)
            val hrIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_HR)
                .setWidth(dp(16f))
                .setHeight(dp(16f))
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
                        .setSize(sp(28f))
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
                        .setSize(sp(12f))
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

            // 5. Horizontal Min-Max Telemetry Bar (128dp x 8dp, reverse-engineered from shealth xf7.java)
            val barWidthDp = 128f
            val barHeightDp = 8f
            val hasMinMax = vitals.minHeartRate > 0 && vitals.maxHeartRate > 0
            val minHr = if (hasMinMax) vitals.minHeartRate else (if (hrInt > 0) hrInt else 0)
            val maxHr = if (hasMinMax) vitals.maxHeartRate else (if (hrInt > 0) hrInt else 0)
            val scale = barWidthDp / 110f // 40..150 bpm span
            val rangeStartDp = if (minHr > 0) ((minHr - 40) * scale).coerceIn(0f, 75f) else 0f
            val rangeLengthDp = if (maxHr > minHr) ((maxHr - minHr) * scale).coerceIn(8f, barWidthDp - rangeStartDp) else (if (minHr > 0) 12f else 0f)
            val dotHr = if (hrInt > 0) hrInt else minHr
            val dotPosDp = if (dotHr > 0) (((dotHr.coerceIn(40, 150) - 40) * scale) - 3.75f).coerceIn(0f, barWidthDp - 8f) else 0f

            val sweepAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(700L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.22f, 0.25f, 0.0f, 1.0f))
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

            val barTrackBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(translucentGlassBlack)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(4f)).build())
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
                                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(4f)).build())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()

            val dynamicDotColor = DynamicBuilders.DynamicColor.animate(
                0xFFFFFFFF.toInt(),
                0x44FFFFFF.toInt(),
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

            // 6. Resting Baseline & Range Metric (Authentic physiological resting status)
            val restingTextStr = if (hasMinMax) {
                "${vitals.minHeartRate} - ${vitals.maxHeartRate} ${context.getString(R.string.bpm_unit).lowercase()}"
            } else if (vitals.avgHeartRate > 0) {
                "${context.getString(R.string.stress_resting)}: ${vitals.avgHeartRate} ${context.getString(R.string.bpm_unit).lowercase()}"
            } else {
                context.getString(R.string.stress_resting)
            }

            val restingStatusText = LayoutElementBuilders.Text.Builder()
                .setText(restingTextStr)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(10.5f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        .setColor(restingTextColor)
                        .build()
                )
                .build()

            // 7. Glassmorphic Pill Button ("Medir" / "Sincronizar")
            val measureButtonText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.measure))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11.5f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val measurePillBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(94f))
                .setHeight(dp(25f))
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

            // Assemble into the 190dp x 138dp Stadium Capsule
            val capsuleContentColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(dp(190f))
                .setHeight(dp(138f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).setWidth(dp(1f)).build())
                .addContent(titleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(1.5f)).setWidth(dp(1f)).build())
                .addContent(subtitleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2.5f)).setWidth(dp(1f)).build())
                .addContent(heroHrRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(3.5f)).setWidth(dp(1f)).build())
                .addContent(minMaxChartBox)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(3f)).setWidth(dp(1f)).build())
                .addContent(restingStatusText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4.5f)).setWidth(dp(1f)).build())
                .addContent(measurePillBox)
                .build()

            val stadiumCapsuleBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(190f))
                .setHeight(dp(138f))
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
