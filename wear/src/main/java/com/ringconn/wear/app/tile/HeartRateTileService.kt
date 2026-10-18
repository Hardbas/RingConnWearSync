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
 * Bespoke Circular One UI Watch 8 Heart Rate Tile for Samsung Galaxy Watch (Wear OS 5 / 6).
 * Features a circular bezel arc sweeping to the active cardiac zone, pulsating heart telemetry,
 * prominent tabular Hero BPM typography, and an ergonomic frosted glass action pill.
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
        const val RESOURCES_VERSION = "oneui8-hr-circular-v1"
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

            // 5 Cardiac Zones color mapping
            val (zoneColor, zoneNameRes) = when {
                hrInt <= 0 -> Pair(0xFF47CDFF.toInt(), R.string.no_data)
                hrInt < 100 -> Pair(0xFF47CDFF.toInt(), R.string.zone_rest)           // Cyan: Resting (<100 bpm)
                hrInt in 100..120 -> Pair(0xFFA0F116.toInt(), R.string.zone_warmup)   // Lime: Warm Up (100-120 bpm)
                hrInt in 121..140 -> Pair(0xFFFFB703.toInt(), R.string.zone_fat_burn) // Amber: Fat Burn (121-140 bpm)
                hrInt in 141..160 -> Pair(0xFFFB8500.toInt(), R.string.zone_cardio)   // Orange: Cardio (141-160 bpm)
                else -> Pair(0xFFFF5D99.toInt(), R.string.zone_peak)                 // Vivid Pink: Peak (>160 bpm)
            }

            val whiteColor = argb(0xFFFFFFFF.toInt())
            val subtitlePinkColor = argb(0xFFF99BD6.toInt())      // One UI pink (#FFF99BD6)
            val bpmUnitWarmColor = argb(0xFFFBCEB6.toInt())       // Warm peach (#FFFBCEB6)
            val secondaryStatsColor = argb(0xFF94A3B8.toInt())    // Slate neutral (#FF94A3B8)
            val frostedGlassPill = argb(0x33FFFFFF.toInt())       // 20% Frosted glass
            val bezelTrackColor = argb(0x22FFFFFF.toInt())        // Subtle dark bezel track

            // Click 1: Full tile tap -> HeartRateDetailActivity
            val mainTileAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName("com.ringconn.wear.app.ui.HeartRateDetailActivity")
                        .build()
                ).build()

            val mainTileClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(mainTileAction)
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

            // Dynamic Sweeping Bezel Arc Gauge (240 degrees total span, 8 o'clock to 4 o'clock)
            val totalArcSpanDegrees = 240f
            val arcStartAngle = 240f

            val targetSweepDegrees = if (hrInt > 0) {
                val clampedHr = hrInt.coerceIn(40, 180)
                val fraction = (clampedHr - 40) / 140f
                (fraction * totalArcSpanDegrees).coerceIn(12f, totalArcSpanDegrees)
            } else {
                12f
            }

            val sweepAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(650L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.22f, 0.25f, 0.0f, 1.0f))
                        .build()
                )
                .build()

            val dynamicSweep = DimensionBuilders.DegreesProp.Builder(targetSweepDegrees)
                .setDynamicValue(
                    DynamicBuilders.DynamicFloat.animate(
                        AppDataKey("hr_arc_sweep"),
                        sweepAnimSpec
                    )
                )
                .build()

            val angularConstraint = DimensionBuilders.AngularLayoutConstraint.Builder(totalArcSpanDegrees)
                .build()

            // 1. Outer Bezel Track (Static subtle track)
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

            // 2. Active Cardiac Zone Bezel Arc (Animated dynamic sweep)
            val activeArcLine = LayoutElementBuilders.ArcLine.Builder()
                .setLength(dynamicSweep)
                .setLayoutConstraintsForDynamicLength(angularConstraint)
                .setThickness(dp(6.5f))
                .setColor(argb(zoneColor))
                .build()

            val activeArc = LayoutElementBuilders.Arc.Builder()
                .setAnchorAngle(DimensionBuilders.degrees(arcStartAngle))
                .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                .addContent(activeArcLine)
                .build()

            // 3. Heartbeat continuous pulse animation (650ms reverse loop, shealth cg7.java:482)
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

            // 4. Center Typography Elements
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

            val hrIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_HR)
                .setWidth(dp(18f))
                .setHeight(dp(18f))
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
                        .setSize(sp(34f))
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
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).setHeight(dp(1f)).build())
                .addContent(hrNumberText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(4f)).setHeight(dp(1f)).build())
                .addContent(bpmUnitText)
                .build()

            // Secondary Stats: Min - Max or Cardiac Zone label
            val hasMinMax = vitals.minHeartRate > 0 && vitals.maxHeartRate > 0
            val secondaryTextStr = if (hasMinMax) {
                "${vitals.minHeartRate} - ${vitals.maxHeartRate} ${context.getString(R.string.bpm_unit).lowercase()}"
            } else if (hrInt > 0) {
                context.getString(zoneNameRes)
            } else {
                ""
            }

            val secondaryStatsText = LayoutElementBuilders.Text.Builder()
                .setText(secondaryTextStr)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        .setColor(secondaryStatsColor)
                        .build()
                )
                .build()

            // 5. Ergonomic Frosted Glass Pill Button ("Medir" / "Sincronizar")
            val measureButtonText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.measure))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(12f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val measurePillBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(94f))
                .setHeight(dp(27f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(measurePillClickable)
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(frostedGlassPill)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(13.5f)).build())
                                .build()
                        )
                        .build()
                )
                .addContent(measureButtonText)
                .build()

            // Center Column Content
            val contentColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(titleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).setWidth(dp(1f)).build())
                .addContent(subtitleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(5f)).setWidth(dp(1f)).build())
                .addContent(heroHrRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).setWidth(dp(1f)).build())
                .addContent(secondaryStatsText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).setWidth(dp(1f)).build())
                .addContent(measurePillBox)
                .build()

            // Root Canvas: Circular Box with Bezel Arcs + Center Content + Carousel Fade-In
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
                                .build()
                        )
                        .build()
                )
                .addContent(bgArc)
                .addContent(activeArc)
                .addContent(contentColumn)
                .build()

            val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(rootBox).build())
                .build()

            val timeline = TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(timelineEntry)
                .build()

            val tileState = StateBuilders.State.Builder()
                .addKeyToValueMapping(AppDataKey("hr_arc_sweep"), DynamicDataBuilders.DynamicDataValue.fromFloat(targetSweepDegrees))
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
