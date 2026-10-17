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

class VitalsTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return Futures.immediateFuture(
            buildTile(this, requestParams.deviceConfiguration)
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(buildResources())
    }

    /**
     * Handled by Wear OS without triggering an expensive IPC re-inflation storm
     * that would block touch events when user taps the tile.
     */
    override fun onTileEnterEvent(tileEnterEvent: EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(tileEnterEvent)
    }

    companion object {
        const val RESOURCES_VERSION = "oneui8-vitals-v3"
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
            val spo2Value = if (vitals.lastSpo2 > 0) "${vitals.lastSpo2}%" else "--%"
            val batteryValue = if (vitals.ringBattery > 0) "${vitals.ringBattery}%" else "--%"

            // Exact Samsung Health One UI Watch 8 Heart Rate Tile Colors (ra7.java / cg7.java)
            val whiteColor = argb(0xFFFFFFFF.toInt())
            val subtitlePinkColor = argb(0xFFF99BD6.toInt())      // heart_rate_tile_subtitle_text_color
            val bpmUnitWarmColor = argb(0xFFFBCEB6.toInt())       // heart_rate_tile_bpm_text_color
            val minMaxBarColor = argb(0xFFFF5D99.toInt())         // heart_rate_tile_min_max_progress_color
            val translucentGlassBlack = argb(0x80000000.toInt())  // #80000000 (50% black glassmorphism)

            // Click 1: Main Stadium Capsule -> VitalsDetailActivity
            val mainCapsuleAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName("com.ringconn.wear.app.ui.VitalsDetailActivity")
                        .build()
                ).build()

            val mainCapsuleClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(mainCapsuleAction)
                .build()

            // Click 2: "Medir Ahora" Pill Button -> HeartRateMeasureActivity alias (instant measurement)
            val measurePillAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName("com.ringconn.wear.app.ui.HeartRateMeasureActivity")
                        .build()
                ).build()

            val measurePillClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(measurePillAction)
                .build()

            // 1. One UI Watch 8 Full-Tile Stadium Capsule Background (195dp x 142dp, radius 71dp)
            val bgImage = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_BG_HR_CAPSULE)
                .setWidth(dp(195f))
                .setHeight(dp(142f))
                .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS)
                .build()

            // 2. Title Text (14sp SemiBold White, top margin 10.25dp in ra7.java)
            val titleText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.heart_rate))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.TITLE))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            // 3. Subtitle / Ring Telemetry Row (11.5sp Regular #FFF99BD6, top margin 30.76dp in ra7.java)
            val subtitleText = LayoutElementBuilders.Text.Builder()
                .setText("SpO2 $spo2Value  ·  Ring $batteryValue")
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.LABEL))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                        .setColor(subtitlePinkColor)
                        .build()
                )
                .build()

            // 4. Hero Heart Rate Row (cg7.c: Icon + 25sp Bold HR + 12.3sp BPM unit)
            val hrIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_HR)
                .setWidth(dp(16f))
                .setHeight(dp(16f))
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
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(5f)).build())
                .addContent(hrNumberText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(3f)).build())
                .addContent(bpmUnitText)
                .build()

            // =========================================================================
            // 5. One UI Watch 8 ProtoLayout 1.2 Dynamic Animated Min-Max Range Bar (ra7.java / xf7.java)
            // =========================================================================
            val barWidthDp = 133f
            val barHeightDp = 12f
            val hasMinMax = vitals.minHeartRate > 0 && vitals.maxHeartRate > 0
            val minHr = if (hasMinMax) vitals.minHeartRate else (if (vitals.lastHeartRate > 0) vitals.lastHeartRate else 0)
            val maxHr = if (hasMinMax) vitals.maxHeartRate else (if (vitals.lastHeartRate > 0) vitals.lastHeartRate else 0)
            val scale = barWidthDp / 110f // 40..150 bpm span
            val rangeStartDp = if (minHr > 0) ((minHr - 40) * scale).coerceIn(0f, 80f) else 0f
            val rangeLengthDp = if (maxHr > minHr) ((maxHr - minHr) * scale).coerceIn(4f, barWidthDp - rangeStartDp) else (if (minHr > 0) 10f else 0f)
            val dotHr = if (vitals.lastHeartRate > 0) vitals.lastHeartRate else minHr
            val dotPosDp = if (dotHr > 0) (((dotHr.coerceIn(40, 150) - 40) * scale) - 4.1f).coerceIn(0f, barWidthDp - 8f) else 0f

            // One UI Watch 8 Animation Parameters (600L ms sweep with CubicBezierEasing 0.22, 0.25, 0.0, 1.0)
            val sweepAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(600L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.22f, 0.25f, 0.0f, 1.0f))
                        .build()
                )
                .build()

            // Dynamic Animated Offset & Widths with static fallbacks
            val dynamicRangeStart = DimensionBuilders.DpProp.Builder(rangeStartDp)
                .setDynamicValue(DynamicBuilders.DynamicFloat.animate(0.1f, rangeStartDp, sweepAnimSpec))
                .build()

            val dynamicRangeLength = DimensionBuilders.DpProp.Builder(rangeLengthDp)
                .setDynamicValue(DynamicBuilders.DynamicFloat.animate(0.1f, rangeLengthDp, sweepAnimSpec))
                .build()

            val dynamicDotPos = DimensionBuilders.DpProp.Builder(dotPosDp)
                .setDynamicValue(DynamicBuilders.DynamicFloat.animate(0.1f, dotPosDp, sweepAnimSpec))
                .build()

            val barTrackBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(translucentGlassBlack)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(6.15f)).build())
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
                                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(6.15f)).build())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
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
                        .setWidth(dp(8.2f))
                        .setHeight(dp(8.2f))
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

            // 6. Glassmorphic "Medir Ahora" Pill Button (cg7.e: 92.27dp x 26.66dp, radius 18.45dp, #80000000)
            val measureButtonText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.measure_now))
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
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(18.45f)).build())
                                .build()
                        )
                        .build()
                )
                .addContent(measureButtonText)
                .build()

            // Assemble inside the 195dp x 142dp One UI Watch 8 Stadium Capsule
            val capsuleContentColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(dp(195f))
                .setHeight(dp(142f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(10f)).build())
                .addContent(titleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).build())
                .addContent(subtitleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build())
                .addContent(heroHrRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(7f)).build())
                .addContent(minMaxChartBox)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(10f)).build())
                .addContent(measurePillBox)
                .build()

            val stadiumCapsuleBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(195f))
                .setHeight(dp(142f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(mainCapsuleClickable)
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

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .setFreshnessIntervalMillis(30_000L)
                .build()
        }
    }
}
